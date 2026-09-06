package com.musium.innertube

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Innertube(
    private val hl: String = "en",
    private val gl: String = "US",
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun search(query: String): SearchPage {
        var page = searchMusic(query)
        if (!MusicCatalog.hasMusicHits(page.songs, query) && !query.trim().endsWith("song", ignoreCase = true)) {
            page = MusicCatalog.refine(page.merge(searchMusic("$query song")), query)
        }
        if (!MusicCatalog.hasMusicHits(page.songs, query)) {
            val hint = searchSuggestions(query).firstOrNull { MusicCatalog.isMusicSearchHint(query, it) }
            if (hint != null && !hint.equals(query, ignoreCase = true)) {
                page = MusicCatalog.refine(page.merge(searchMusic(hint)), query)
                if (!MusicCatalog.hasMusicHits(page.songs, query)) {
                    val videos = runCatching { searchYoutubeVideos(hint) }.getOrDefault(emptyList())
                    page = page.copy(songs = MusicCatalog.preferOfficial(page.songs + videos, query))
                }
            }
        }
        if (!MusicCatalog.hasMusicHits(page.songs, query) && MusicCatalog.allowYoutubeVideoFallback(query)) {
            val videos = runCatching { searchYoutubeVideos(query) }.getOrDefault(emptyList())
            page = page.copy(songs = MusicCatalog.preferOfficial(page.songs + videos, query))
        }
        return page
    }

    fun searchSuggestions(input: String): List<String> {
        val query = input.trim()
        if (query.isEmpty()) return emptyList()
        val fromMusic = runCatching {
            parseSearchSuggestions(post("music/get_search_suggestions", mapOf("input" to query)))
        }.getOrDefault(emptyList()).filterNot(MusicCatalog::isNonMusicQuery)
        if (fromMusic.isNotEmpty()) return fromMusic
        val fromYoutube = runCatching {
            parseSearchSuggestions(post("get_search_suggestions", mapOf("input" to query)))
        }.getOrDefault(emptyList()).filterNot(MusicCatalog::isNonMusicQuery)
        if (fromYoutube.isNotEmpty()) return fromYoutube
        return runCatching { googleSuggest(query) }.getOrDefault(emptyList())
            .filterNot(MusicCatalog::isNonMusicQuery)
    }

    fun home(): List<HomeSection> {
        val sections = LinkedHashMap<String, HomeSection>()
        listOf("FEmusic_home", "FEmusic_explore", "FEmusic_charts").forEach { browseId ->
            runCatching { parseSections(post("browse", mapOf("browseId" to browseId))) }
                .getOrDefault(emptyList())
                .forEach { section -> sections.putIfAbsent(section.title, section) }
        }
        return sections.values.filter { it.items.isNotEmpty() }
    }

    fun artist(browseId: String): BrowsePage {
        val root = post("browse", mapOf("browseId" to browseId))
        return enrichBrowse(parseBrowsePage(root, "Artist"), root)
    }

    fun album(browseId: String): BrowsePage {
        val root = post("browse", mapOf("browseId" to browseId))
        return enrichBrowse(parseBrowsePage(root, "Album"), root)
    }

    fun playlist(playlistId: String): BrowsePage {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val root = post("browse", mapOf("browseId" to browseId))
        return enrichBrowse(parseBrowsePage(root, "Playlist"), root)
    }

    fun playerStreamGroups(videoId: String): Sequence<List<ResolvedAudio>> = sequence {
        for (client in PLAYER_CLIENTS) {
            Log.d(PLAY_TAG, "playerStreams try client=${client.name} videoId=$videoId")
            val json = runCatching { player(videoId, client) }
                .onFailure { Log.w(PLAY_TAG, "player ${client.name} $videoId failed: ${it.message}", it) }
                .getOrNull()
                ?: continue
            val streams = collectStreams(json)
            if (streams.isEmpty()) {
                Log.w(PLAY_TAG, "playerStreams no formats client=${client.name} videoId=$videoId")
                continue
            }
            yield(
                streams.map { stream ->
                    Log.d(
                        PLAY_TAG,
                        "candidate client=${client.name} itag=${stream.itag} kind=${stream.kindHint} " +
                            "mime=${stream.mime.take(40)} len=${stream.contentLength} host=${stream.url.toHttpUrlOrNull()?.host}",
                    )
                    ResolvedAudio(
                        url = stream.url,
                        userAgent = client.userAgent,
                        client = "${client.name}:${stream.itag}",
                        kind = stream.kindHint,
                        itag = stream.itag,
                    )
                },
            )
        }
    }

    fun playerStreams(videoId: String): List<ResolvedAudio> {
        val found = playerStreamGroups(videoId).flatten().toList()
        if (found.isEmpty()) Log.e(PLAY_TAG, "playerStreams all clients failed videoId=$videoId")
        return found
    }

    fun playerAudio(videoId: String): ResolvedAudio? {
        val streams = playerStreams(videoId)
        return streams.firstOrNull { it.kind == StreamKind.HLS || it.kind == StreamKind.SEEKABLE }
            ?: streams.firstOrNull { it.itag == 140 || it.url.contains("mime=audio") }
            ?: streams.firstOrNull()
    }

    fun playerAudioUrl(videoId: String): String? = playerAudio(videoId)?.url

    fun lyrics(videoId: String): Lyrics? {
        val next = post("next", mapOf("videoId" to videoId, "isAudioOnly" to true))
        val browseId = parseLyricsBrowseId(next) ?: return null
        val web = parseLyrics(post("browse", mapOf("browseId" to browseId)))
        if (web?.synced == true) return web
        val timed = runCatching { parseLyrics(browseAndroid(browseId)) }.getOrNull()
        return if (timed?.synced == true) timed else web ?: timed
    }

    fun nextRadio(videoId: String): List<SongItem> {
        val radioPlaylist = "RDAMVM$videoId"
        val first = post(
            "next",
            mapOf(
                "videoId" to videoId,
                "playlistId" to radioPlaylist,
                "isAudioOnly" to true,
            ),
        )
        var songs = parseNextSongs(first)
        if (songs.size < 2) {
            val (mixVideo, mixPlaylist) = parseAutomixPlaylistId(first)
            if (mixPlaylist != null) {
                val second = post(
                    "next",
                    buildMap {
                        put("playlistId", mixPlaylist)
                        put("isAudioOnly", true)
                        mixVideo?.let { put("videoId", it) }
                    },
                )
                songs = (songs + parseNextSongs(second)).distinctBy { it.id }
            }
        }
        if (songs.isEmpty()) {
            val queue = post(
                "music/get_queue",
                mapOf("playlistId" to radioPlaylist, "videoIds" to listOf(videoId)),
            )
            songs = parseNextSongs(queue)
        }
        return songs
    }

    fun nextPlaylist(playlistId: String, videoId: String? = null): List<SongItem> {
        val body = buildMap<String, Any> {
            put("playlistId", playlistId)
            put("isAudioOnly", true)
            videoId?.let { put("videoId", it) }
        }
        return parseNextSongs(post("next", body))
    }

    private fun enrichBrowse(page: BrowsePage, root: JSONObject): BrowsePage {
        if (page.songs.isNotEmpty()) return page
        val extra = playlistIdsFrom(root).take(3).flatMap { playlistId ->
            runCatching {
                val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
                parseBrowsePage(post("browse", mapOf("browseId" to browseId)), "Playlist").songs
            }.getOrDefault(emptyList())
        }.distinctBy { it.id }
        return page.copy(
            songs = extra,
            radioVideoId = page.radioVideoId ?: extra.firstOrNull()?.id,
        )
    }

    private fun googleSuggest(query: String): List<String> {
        val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("https://suggestqueries.google.com/complete/search?client=firefox&hl=$hl&q=$encoded")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) return emptyList()
            val items = JSONArray(text).optJSONArray(1) ?: return emptyList()
            return buildList {
                for (index in 0 until items.length()) {
                    items.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }

    private fun searchMusic(query: String): SearchPage {
        val music = runCatching { parseSearch(post("search", mapOf("query" to query))) }
            .getOrDefault(SearchPage.Empty)
        val songs = runCatching { parseSearch(post("search", mapOf("query" to query, "params" to FILTER_SONG))) }
            .getOrDefault(SearchPage.Empty)
        val videos = runCatching { parseSearch(post("search", mapOf("query" to query, "params" to FILTER_VIDEO))) }
            .getOrDefault(SearchPage.Empty)
        return MusicCatalog.refine(songs.merge(music).merge(videos), query)
    }

    private fun searchYoutubeVideos(query: String): List<SongItem> {
        val payload = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB")
                        .put("clientVersion", WEB_VERSION)
                        .put("hl", hl)
                        .put("gl", gl),
                ),
            )
            .put("query", query)
            .put("params", FILTER_WEB_VIDEO)
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false&key=$API_KEY")
            .post(payload.toString().toRequestBody(JSON))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("YouTube search failed (${response.code})")
            return parseYoutubeVideos(JSONObject(text))
        }
    }

    private fun player(videoId: String, client: PlayerClient): JSONObject {
        val clientJson = JSONObject()
            .put("clientName", client.name)
            .put("clientVersion", client.version)
            .put("hl", hl)
            .put("gl", gl)
        client.extra.forEach { (key, value) -> clientJson.put(key, value) }
        val context = JSONObject().put("client", clientJson)
        client.embedUrl?.let { context.put("thirdParty", JSONObject().put("embedUrl", it)) }
        val payload = JSONObject()
            .put("context", context)
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(payload.toString().toRequestBody(JSON))
            .header("User-Agent", client.userAgent)
            .header("Content-Type", "application/json")
            .header("Origin", "https://www.youtube.com")
            .header("X-YouTube-Client-Name", client.name)
            .header("X-YouTube-Client-Version", client.version)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            Log.d(PLAY_TAG, "player HTTP client=${client.name} videoId=$videoId code=${response.code} bodyLen=${text.length}")
            if (!response.isSuccessful) error("Player ${client.name} failed (${response.code}): ${text.take(180)}")
            return JSONObject(text)
        }
    }

    private fun browseAndroid(browseId: String): JSONObject {
        val client = PLAYER_CLIENTS.first { it.name == "ANDROID" }
        val clientJson = JSONObject()
            .put("clientName", client.name)
            .put("clientVersion", client.version)
            .put("hl", hl)
            .put("gl", gl)
        client.extra.forEach { (key, value) -> clientJson.put(key, value) }
        val payload = JSONObject()
            .put("context", JSONObject().put("client", clientJson))
            .put("browseId", browseId)
        val request = Request.Builder()
            .url("${BASE}browse?prettyPrint=false")
            .post(payload.toString().toRequestBody(JSON))
            .header("User-Agent", client.userAgent)
            .header("Content-Type", "application/json")
            .header("X-YouTube-Client-Name", client.name)
            .header("X-YouTube-Client-Version", client.version)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Android lyrics browse failed (${response.code})")
            return JSONObject(text)
        }
    }

    private fun post(endpoint: String, extra: Map<String, Any?>): JSONObject {
        val payload = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", CLIENT_NAME)
                        .put("clientVersion", CLIENT_VERSION)
                        .put("hl", hl)
                        .put("gl", gl)
                        .put("visitorData", VISITOR_DATA),
                ),
            )
        extra.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is List<*> -> payload.put(key, JSONArray(value))
                else -> payload.put(key, value)
            }
        }
        val url = "$BASE$endpoint?prettyPrint=false&key=$API_KEY"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", CLIENT_NAME)
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .header("Origin", ORIGIN)
            .header("Referer", ORIGIN)
            .header("x-origin", ORIGIN)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("YouTube Music $endpoint failed (${response.code}): ${text.take(180)}")
            }
            return JSONObject(text)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val BASE = "https://music.youtube.com/youtubei/v1/"
        private const val ORIGIN = "https://music.youtube.com/"
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20250317.01.00"
        private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val FILTER_SONG = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        private const val FILTER_VIDEO = "EgWKAQIQAWoKEAkQBRAKEAMQBA%3D%3D"
        private const val FILTER_WEB_VIDEO = "EgIQAQ%3D%3D"
        private const val WEB_VERSION = "2.20250317.01.00"
        private const val SAFARI_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
        private const val PLAY_TAG = "MusiPediaPlay"
        private val PLAYER_CLIENTS = listOf(
            PlayerClient(
                name = "WEB_EMBEDDED_PLAYER",
                version = "1.20250317.01.00",
                userAgent = USER_AGENT,
                embedUrl = "https://www.youtube.com/",
            ),
            PlayerClient(
                name = "WEB",
                version = WEB_VERSION,
                userAgent = SAFARI_USER_AGENT,
            ),
            PlayerClient(
                name = "ANDROID_VR",
                version = "1.61.09",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.09 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                extra = mapOf(
                    "androidSdkVersion" to 32,
                    "deviceMake" to "Oculus",
                    "deviceModel" to "Quest 3",
                    "osName" to "Android",
                    "osVersion" to "12L",
                ),
            ),
            PlayerClient(
                name = "ANDROID",
                version = "20.10.4",
                userAgent = "com.google.android.youtube/20.10.4 (Linux; U; Android 14) gzip",
                extra = mapOf(
                    "androidSdkVersion" to 34,
                    "osName" to "Android",
                    "osVersion" to "14",
                ),
            ),
            PlayerClient(
                name = "IOS",
                version = "20.10.4",
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 17_7 like Mac OS X;)",
                extra = mapOf(
                    "osName" to "iPhone",
                    "osVersion" to "17.7.2.21H221",
                    "deviceMake" to "Apple",
                    "deviceModel" to "iPhone16,2",
                ),
            ),
        )
    }

    private data class PlayerClient(
        val name: String,
        val version: String,
        val userAgent: String,
        val extra: Map<String, Any> = emptyMap(),
        val embedUrl: String? = null,
    )
}
