package team.ctrlv.musipedia

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

object RingtoneSetter {
    const val MIN_CLIP_MS = 5_000L
    const val MAX_CLIP_MS = 40_000L
    const val DEFAULT_CLIP_MS = 30_000L

    fun canWriteSettings(context: Context): Boolean =
        Settings.System.canWrite(context.applicationContext)

    fun openWriteSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun localFile(song: PlayableSong): File? {
        val path = OfflineDownloads.attach(song).localUri?.takeIf { it.isNotBlank() } ?: return null
        val file = when {
            path.startsWith("file:") -> File(Uri.parse(path).path ?: return null)
            path.startsWith("content:") -> return null
            else -> File(path)
        }
        return file.takeIf { it.exists() && it.length() >= 1024 }
    }

    fun durationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun setAsRingtone(
        context: Context,
        song: PlayableSong,
        startMs: Long,
        endMs: Long,
    ): Result<Uri> = runCatching {
        val app = context.applicationContext
        if (!canWriteSettings(app)) error("Allow MusiPedia to change system settings")
        val source = localFile(song) ?: error("Download this song first")
        val clipStart = startMs.coerceAtLeast(0L)
        val clipEnd = endMs.coerceAtLeast(clipStart + 1L)
        val length = clipEnd - clipStart
        if (length < MIN_CLIP_MS) error("Clip must be at least ${MIN_CLIP_MS / 1000}s")
        if (length > MAX_CLIP_MS) error("Clip must be at most ${MAX_CLIP_MS / 1000}s")

        val cacheDir = File(app.cacheDir, "ringtones").apply { mkdirs() }
        val clipped = clipAudio(source, cacheDir, song.id, clipStart, clipEnd)
        val uri = publishRingtone(app, song, clipped)
        RingtoneManager.setActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE, uri)
        clipped.delete()
        uri
    }

    private data class ClipResult(val file: File, val mime: String)

    private fun clipAudio(
        source: File,
        cacheDir: File,
        songId: String,
        startMs: Long,
        endMs: Long,
    ): File {
        val extractor = MediaExtractor()
        extractor.setDataSource(source.absolutePath)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            error("No audio track found")
        }
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val webm = mime.contains("webm", ignoreCase = true) || mime.contains("opus", ignoreCase = true)
        val dest = File(
            cacheDir,
            "clip_${DownloadStore.safeId(songId)}.${if (webm) "webm" else "m4a"}",
        )
        dest.delete()
        val muxer = MediaMuxer(
            dest.absolutePath,
            if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )
        try {
            copyRange(extractor, muxer, format, startMs, endMs)
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            runCatching { extractor.release() }
        }
        if (!dest.exists() || dest.length() < 256) error("Could not trim this track")
        return dest
    }

    private fun publishRingtone(context: Context, song: PlayableSong, file: File): Uri {
        val webm = file.extension.equals("webm", ignoreCase = true)
        val mime = if (webm) "audio/webm" else "audio/mp4"
        val displayName =
            "${DownloadStore.safeId(song.title.ifBlank { song.id })}_ringtone.${file.extension}"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.TITLE, song.title.ifBlank { "MusiPedia ringtone" })
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
            put(MediaStore.Audio.Media.IS_ALARM, false)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES + "/MusiPedia")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: error("Could not save ringtone")
        resolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Could not write ringtone")
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun copyRange(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        format: MediaFormat,
        startMs: Long,
        endMs: Long,
    ) {
        val startUs = TimeUnit.MILLISECONDS.toMicros(startMs)
        val endUs = TimeUnit.MILLISECONDS.toMicros(endMs)
        val muxerTrack = muxer.addTrack(format)
        muxer.start()
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val buffer = ByteBuffer.allocate(256 * 1024)
        val info = MediaCodec.BufferInfo()
        var firstPts = -1L
        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val sampleTime = extractor.sampleTime
            if (sampleTime < 0) break
            if (sampleTime < startUs) {
                extractor.advance()
                continue
            }
            if (sampleTime > endUs) break
            if (firstPts < 0L) firstPts = sampleTime
            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = (sampleTime - firstPts).coerceAtLeast(0L)
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrack, buffer, info)
            extractor.advance()
        }
    }
}
