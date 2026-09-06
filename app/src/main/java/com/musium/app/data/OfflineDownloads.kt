package com.musium.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.musium.innertube.StreamKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object OfflineDownloads {
    var songs by mutableStateOf<List<PlayableSong>>(emptyList())
        private set
    var progressing by mutableStateOf<Set<String>>(emptySet())
        private set
    var progress by mutableStateOf<Map<String, Float>>(emptyMap())
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    var inFlight by mutableStateOf<Map<String, PlayableSong>>(emptyMap())
        private set

    private lateinit var store: DownloadStore
    private lateinit var scope: CoroutineScope
    private val jobs = mutableMapOf<String, Job>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    fun initialize(store: DownloadStore, scope: CoroutineScope) {
        this.store = store
        this.scope = scope
        refresh()
    }

    fun has(id: String): Boolean = songs.any { it.id == id }

    fun attach(song: PlayableSong): PlayableSong = if (::store.isInitialized) store.attach(song) else song

    fun attachAll(items: List<PlayableSong>): List<PlayableSong> = items.map(::attach)

    fun toggle(song: PlayableSong) {
        if (!song.canDownload) return
        if (has(song.id) || song.id in progressing) delete(song.id) else download(song)
    }

    fun download(song: PlayableSong) {
        if (!::store.isInitialized || !song.canDownload || has(song.id) || song.id in progressing) return
        lastError = null
        inFlight = inFlight + (song.id to song)
        progressing = progressing + song.id
        progress = progress + (song.id to 0f)
        jobs[song.id] = scope.launch(Dispatchers.IO) {
            try {
                writeFile(song)
                withContext(Dispatchers.Main) {
                    inFlight = inFlight - song.id
                    refresh()
                    progressing = progressing - song.id
                    progress = progress - song.id
                }
            } catch (_: CancellationException) {
                store.remove(song.id)
                withContext(Dispatchers.Main) {
                    inFlight = inFlight - song.id
                    progressing = progressing - song.id
                    progress = progress - song.id
                }
            } catch (error: Throwable) {
                store.remove(song.id)
                withContext(Dispatchers.Main) {
                    inFlight = inFlight - song.id
                    lastError = error.message ?: "Download failed"
                    progressing = progressing - song.id
                    progress = progress - song.id
                }
            }
        }
    }

    fun delete(id: String) {
        jobs.remove(id)?.cancel()
        store.remove(id)
        inFlight = inFlight - id
        progressing = progressing - id
        progress = progress - id
        refresh()
    }

    private fun refresh() {
        if (::store.isInitialized) songs = store.songs()
    }

    private suspend fun writeFile(song: PlayableSong) {
        val audio = StreamResolver.resolveForDownload(song.id)
        if (audio.kind == StreamKind.HLS || audio.url.contains(".m3u8")) {
            error("This track cannot be saved offline")
        }
        val target = store.fileFor(song.id)
        val temp = File(target.parentFile, "${target.name}.part")
        temp.delete()
        val request = Request.Builder()
            .url(audio.url)
            .header("User-Agent", audio.userAgent)
            .header("Accept-Encoding", "identity")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed (${response.code})")
            val body = response.body ?: error("Empty download")
            val total = body.contentLength()
            var read = 0L
            var lastUi = 0L
            temp.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        read += n
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastUi >= 200) {
                            lastUi = now
                            val value = (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) {
                                progress = progress + (song.id to value)
                            }
                        }
                    }
                }
            }
            if (total > 0 && temp.length() < (total * 9 / 10)) {
                temp.delete()
                error("Download incomplete")
            }
        }
        if (temp.length() < 1024) {
            temp.delete()
            error("Download was empty")
        }
        target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        store.add(song, target)
        PlayLog.d("offline saved id=${song.id} bytes=${target.length()} client=${audio.client}")
    }
}
