package team.ctrlv.musipedia

import android.util.LruCache
import team.ctrlv.musipedia.innertube.Innertube
import team.ctrlv.musipedia.innertube.ResolvedAudio
import team.ctrlv.musipedia.innertube.StreamKind
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.TimeUnit

object StreamResolver {
    const val ANDROID_USER_AGENT =
        "com.google.android.youtube/20.10.4 (Linux; U; Android 14) gzip"
    private const val FIREFOX_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    private const val FALLBACK_CACHE_MS = 3 * 60 * 1000L
    private const val MID_FILE_OFFSET = 2L * 1024 * 1024

    val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val probeHttp = http.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    private val cache = LruCache<String, CachedAudio>(24)
    private val lock = Any()
    private val innertube = Innertube()

    fun initialize() {
        synchronized(lock) {
            if (initialized) return
            NewPipe.init(ExtractorDownloader(http), Localization("en", "US"), ContentCountry("US"))
            initialized = true
        }
    }

    @Volatile
    private var initialized = false

    fun resolve(videoId: String, preferNewPipe: Boolean = false): ResolvedAudio {
        initialize()
        PlayLog.d("resolve start videoId=$videoId preferNewPipe=$preferNewPipe")
        cached(videoId)?.let { return it }

        if (preferNewPipe) {
            pickFrom(videoId, runCatching { newPipeStreams(videoId) }.onFailure {
                PlayLog.e("NewPipe failed videoId=$videoId", it)
            }.getOrDefault(emptyList()))?.let { return it }
        }

        for (group in innertube.playerStreamGroups(videoId)) {
            pickFrom(videoId, group)?.let { return it }
        }

        if (!preferNewPipe) {
            pickFrom(videoId, runCatching { newPipeStreams(videoId) }.onFailure {
                PlayLog.e("NewPipe failed videoId=$videoId", it)
            }.getOrDefault(emptyList()))?.let { return it }
        }

        throw IOException("No audio stream for $videoId")
    }

    fun resolveForDownload(videoId: String): ResolvedAudio {
        initialize()
        val collected = buildList {
            innertube.playerStreamGroups(videoId).forEach(::addAll)
            addAll(runCatching { newPipeStreams(videoId) }.getOrDefault(emptyList()))
        }.distinctBy { it.url }
        val progressive = collected.filterNot { it.kind == StreamKind.HLS || it.url.contains(".m3u8") }
        val preferred = progressive.filter { it.itag == 18 || it.kind == StreamKind.SEEKABLE }
        pickFrom(videoId, preferred.ifEmpty { progressive })?.let { chosen ->
            if (chosen.kind == StreamKind.HLS || chosen.url.contains(".m3u8") || chosen.kind == StreamKind.THROTTLED) {
                throw IOException("No offline file stream for $videoId")
            }
            return chosen
        }
        throw IOException("No audio stream for $videoId")
    }

    fun invalidate(videoId: String) {
        PlayLog.d("invalidate cache videoId=$videoId")
        synchronized(lock) { cache.remove(videoId) }
    }

    private fun cached(videoId: String): ResolvedAudio? {
        synchronized(lock) {
            return cache.get(videoId)?.takeIf { it.expiresAt > System.currentTimeMillis() }?.audio?.also {
                PlayLog.d("resolve cache hit videoId=$videoId client=${it.client} kind=${it.kind}")
            }
        }
    }

    private fun pickFrom(videoId: String, candidates: List<ResolvedAudio>): ResolvedAudio? {
        val audio = pickWorking(videoId, candidates) ?: return null
        PlayLog.d(
            "resolve ok videoId=$videoId client=${audio.client} kind=${audio.kind} itag=${audio.itag} " +
                "url=${summarizeUrl(audio.url)}",
        )
        synchronized(lock) {
            cache.put(videoId, CachedAudio(audio, cacheUntilMs(audio.url)))
        }
        return audio
    }

    private fun pickWorking(videoId: String, candidates: List<ResolvedAudio>): ResolvedAudio? {
        val ordered = candidates.distinctBy { it.url }.sortedBy { stream ->
            when {
                stream.kind == StreamKind.HLS -> 0
                stream.itag == 18 || stream.kind == StreamKind.SEEKABLE -> 1
                stream.itag == 140 -> 3
                else -> 2
            }
        }
        PlayLog.d("resolve candidates videoId=$videoId count=${ordered.size}")
        var startOnly: ResolvedAudio? = null
        for (stream in ordered.take(6)) {
            val start = probe(stream, 0L)
            if (!start) continue
            if (stream.kind == StreamKind.HLS) {
                PlayLog.d("picked HLS client=${stream.client}")
                return stream.copy(kind = StreamKind.HLS)
            }
            val mid = probe(stream, MID_FILE_OFFSET)
            if (mid) {
                PlayLog.d("picked SEEKABLE client=${stream.client} itag=${stream.itag}")
                return stream.copy(kind = StreamKind.SEEKABLE)
            }
            PlayLog.w("mid-file blocked client=${stream.client} itag=${stream.itag}")
            if (startOnly == null) startOnly = stream.copy(kind = StreamKind.THROTTLED)
        }
        return startOnly
    }

    private fun probe(audio: ResolvedAudio, start: Long): Boolean {
        val request = Request.Builder()
            .url(audio.url)
            .header("User-Agent", audio.userAgent)
            .apply {
                if (audio.kind != StreamKind.HLS && !audio.url.contains(".m3u8")) {
                    header("Range", "bytes=$start-${start + 1023}")
                }
            }
            .build()
        return runCatching {
            probeHttp.newCall(request).execute().use { response ->
                val ok = response.code in 200..299
                PlayLog.d(
                    "probe client=${audio.client} itag=${audio.itag} start=$start http=${response.code} " +
                        "type=${response.header("Content-Type")}",
                )
                ok
            }
        }.onFailure { PlayLog.e("probe crashed client=${audio.client} start=$start", it) }.getOrDefault(false)
    }

    private fun cacheUntilMs(url: String): Long {
        val expireSec = url.substringAfter("expire=", missingDelimiterValue = "")
            .substringBefore('&')
            .toLongOrNull()
        val fromUrl = expireSec?.times(1000L)?.minus(45_000L)
        val fallback = System.currentTimeMillis() + FALLBACK_CACHE_MS
        return (fromUrl ?: fallback).coerceAtLeast(System.currentTimeMillis() + 20_000L)
    }

    private fun summarizeUrl(url: String): String {
        val noQuery = url.substringBefore('?')
        val host = noQuery.substringAfter("://").substringBefore('/')
        return "$host ...${noQuery.takeLast(24)} q=${url.length}"
    }

    private fun newPipeStreams(videoId: String): List<ResolvedAudio> {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val streams = mutableListOf<ResolvedAudio>()
        runCatching { info.hlsUrl }.getOrNull()?.takeIf { !it.isNullOrBlank() }?.let { url ->
            streams += ResolvedAudio(url, FIREFOX_USER_AGENT, "NEWPIPE:hls", StreamKind.HLS)
        }
        info.videoStreams.orEmpty().forEach { video ->
            val url = runCatching { video.content }.getOrNull() ?: return@forEach
            if (url.isBlank()) return@forEach
            val itag = runCatching { video.itag }.getOrDefault(0)
            streams += ResolvedAudio(
                url = url,
                userAgent = FIREFOX_USER_AGENT,
                client = "NEWPIPE:$itag",
                kind = if (itag == 18) StreamKind.SEEKABLE else StreamKind.THROTTLED,
                itag = itag,
            )
        }
        pickAudio(info.audioStreams)?.let { audio ->
            val url = streamUrl(audio) ?: return@let
            streams += ResolvedAudio(url, FIREFOX_USER_AGENT, "NEWPIPE:audio", StreamKind.THROTTLED)
        }
        PlayLog.d("NewPipe streams videoId=$videoId count=${streams.size}")
        return streams
    }

    private fun pickAudio(streams: List<AudioStream>): AudioStream? {
        return streams
            .filter { !streamUrl(it).isNullOrBlank() }
            .maxByOrNull { it.averageBitrate }
            ?: streams.firstOrNull { !streamUrl(it).isNullOrBlank() }
    }

    private fun streamUrl(stream: AudioStream): String? {
        val content = runCatching { stream.content }.getOrNull()
        if (!content.isNullOrBlank()) return content
        @Suppress("DEPRECATION")
        return runCatching { stream.url }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private data class CachedAudio(val audio: ResolvedAudio, val expiresAt: Long)

    private class ExtractorDownloader(
        private val client: OkHttpClient,
    ) : Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
            val builder = Request.Builder()
                .url(request.url())
                .method(
                    request.httpMethod(),
                    request.dataToSend()?.toRequestBody(null),
                )
                .header("User-Agent", FIREFOX_USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) {
                    throw ReCaptchaException("reCaptcha Challenge requested", request.url())
                }
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string(),
                    response.request.url.toString(),
                )
            }
        }
    }
}
