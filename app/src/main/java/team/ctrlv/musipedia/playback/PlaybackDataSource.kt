package team.ctrlv.musipedia

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import team.ctrlv.musipedia.innertube.StreamKind

@UnstableApi
class PlaybackDataSource(
    private val seekable: DataSource,
    private val chunked: DataSource,
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        seekable.addTransferListener(transferListener)
        chunked.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val kind = dataSpec.httpRequestHeaders["X-Stream-Kind"]
        val useChunked = kind == StreamKind.THROTTLED.name
        PlayLog.d("open dataSource kind=${kind ?: "SEEKABLE"} uriHost=${dataSpec.uri.host} pos=${dataSpec.position}")
        val next = if (useChunked) chunked else seekable
        active = next
        return next.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return active?.read(buffer, offset, length) ?: -1
    }

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> {
        return active?.responseHeaders ?: emptyMap()
    }

    override fun close() {
        active?.close()
        active = null
    }

    class Factory(
        private val seekable: DataSource.Factory,
        private val chunked: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return PlaybackDataSource(seekable.createDataSource(), chunked.createDataSource())
        }
    }
}
