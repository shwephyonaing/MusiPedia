package com.musium.app

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

@UnstableApi
class YoutubeChunkDataSource(
    private val client: OkHttpClient,
) : BaseDataSource(/* isNetwork = */ true) {
    private var dataSpec: DataSpec? = null
    private var response: Response? = null
    private var input: InputStream? = null
    private var streamUrl: String? = null
    private var videoId: String? = null
    private var userAgent: String = StreamResolver.ANDROID_USER_AGENT
    private var position = 0L
    private var chunkEnd = 0L
    private var totalLength = C.LENGTH_UNSET.toLong()
    private var refreshed = false
    private var lastChunkStart = -1L

    override fun open(spec: DataSpec): Long {
        transferInitializing(spec)
        dataSpec = spec
        streamUrl = spec.uri.toString()
        userAgent = spec.httpRequestHeaders["User-Agent"] ?: StreamResolver.ANDROID_USER_AGENT
        videoId = spec.httpRequestHeaders["X-Video-Id"]
        position = spec.position
        refreshed = false
        lastChunkStart = -1L
        openChunk(position)
        transferStarted(spec)
        return if (totalLength == C.LENGTH_UNSET.toLong()) {
            C.LENGTH_UNSET.toLong()
        } else {
            (totalLength - spec.position).coerceAtLeast(0L)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (finished()) return C.RESULT_END_OF_INPUT
        if (input == null || position >= chunkEnd) {
            if (finished()) return C.RESULT_END_OF_INPUT
            openChunk(position)
        }
        val startedAt = position
        val read = input?.read(buffer, offset, length) ?: -1
        if (read == -1) {
            closeChunk()
            if (position == startedAt) {
                if (totalLength == C.LENGTH_UNSET.toLong()) totalLength = position
                return C.RESULT_END_OF_INPUT
            }
            if (finished()) return C.RESULT_END_OF_INPUT
            openChunk(position)
            val again = input?.read(buffer, offset, length) ?: -1
            if (again == -1) {
                if (totalLength == C.LENGTH_UNSET.toLong()) totalLength = position
                return C.RESULT_END_OF_INPUT
            }
            position += again
            bytesTransferred(again)
            return again
        }
        position += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = streamUrl?.let(Uri::parse) ?: dataSpec?.uri

    override fun close() {
        closeChunk()
        dataSpec = null
        streamUrl = null
        transferEnded()
    }

    private fun finished(): Boolean {
        return totalLength != C.LENGTH_UNSET.toLong() && position >= totalLength
    }

    private fun openChunk(start: Long) {
        if (lastChunkStart == start) {
            throw IOException("YouTube chunk made no progress at byte=$start")
        }
        closeChunk()
        val url = streamUrl ?: throw IOException("DataSource not opened")
        val end = start + CHUNK_BYTES - 1
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Range", "bytes=$start-$end")
            .build()
        PlayLog.d("chunk GET ${Uri.parse(url).host} bytes=$start-$end")
        val next = client.newCall(request).execute()
        if (next.code != 200 && next.code != 206) {
            val snippet = next.body?.string()?.take(120).orEmpty()
            PlayLog.e("chunk FAIL http=${next.code} bytes=$start-$end body=$snippet")
            next.close()
            if ((next.code == 403 || next.code == 401) && refreshStream(start)) {
                openChunk(start)
                return
            }
            throw IOException("YouTube chunk HTTP ${next.code} for bytes=$start-$end")
        }
        val contentLength = next.body?.contentLength() ?: -1L
        if (contentLength == 0L) {
            next.close()
            totalLength = start
            return
        }
        response = next
        parseTotal(next.header("Content-Range"), contentLength)
        chunkEnd = start + if (contentLength > 0) contentLength else CHUNK_BYTES
        input = next.body?.byteStream()
        lastChunkStart = start
        PlayLog.d("chunk OK http=${next.code} contentRange=${next.header("Content-Range")} total=$totalLength")
    }

    private fun refreshStream(start: Long): Boolean {
        val id = videoId ?: return false
        if (refreshed) return false
        refreshed = true
        PlayLog.w("refresh stream url videoId=$id at byte=$start preferNewPipe=${start > 0}")
        StreamResolver.invalidate(id)
        val audio = runCatching { StreamResolver.resolve(id, preferNewPipe = start > 0) }
            .onFailure { PlayLog.e("refresh resolve failed videoId=$id", it) }
            .getOrNull()
            ?: return false
        streamUrl = audio.url
        userAgent = audio.userAgent
        lastChunkStart = -1L
        PlayLog.d("refresh ok client=${audio.client} host=${Uri.parse(audio.url).host}")
        return true
    }

    private fun parseTotal(contentRange: String?, contentLength: Long) {
        val slash = contentRange?.substringAfter('/', missingDelimiterValue = "")
        val parsed = slash?.toLongOrNull()
        if (parsed != null && parsed > 0) {
            totalLength = parsed
        } else if (totalLength == C.LENGTH_UNSET.toLong() && contentLength > 0 && contentRange == null) {
            totalLength = position + contentLength
        }
    }

    private fun closeChunk() {
        runCatching { input?.close() }
        runCatching { response?.close() }
        input = null
        response = null
    }

    class Factory(
        private val client: OkHttpClient,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = YoutubeChunkDataSource(client)
    }

    companion object {
        private const val CHUNK_BYTES = 256L * 1024L
    }
}
