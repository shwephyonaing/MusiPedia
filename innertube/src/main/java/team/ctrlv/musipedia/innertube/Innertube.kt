package team.ctrlv.musipedia.innertube

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
    private val youtubeGl = youtubeRegion(gl)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .dispatcher(
            okhttp3.Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 12
            },
        )
        .build()
    @Volatile private var youtubeMusicOk = true

    init {
        // Never blocks: baked/cached values are used immediately; refresh runs off-thread.
        InnertubeSession.ensureBackgroundSync()
    }

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

    fun ytmCharts(): List<SongItem> {
        val country = youtubeGl
        val withCountry = runCatching {
            post(
                "browse",
                mapOf(
                    "browseId" to "FEmusic_charts",
                    "formData" to mapOf("selectedValues" to listOf(country)),
                ),
            )
        }.getOrNull()
        songsFromCharts(withCountry).takeIf { it.isNotEmpty() }?.let { return it }
        val fallback = runCatching { post("browse", mapOf("browseId" to "FEmusic_charts")) }.getOrNull()
        return songsFromCharts(fallback).ifEmpty { songsFromCharts(withCountry) }
    }

    fun appleMostPlayed(limit: Int = 10, chartCountry: String? = null): List<ChartTrack> {
        val preferred = chartCountry?.lowercase()?.takeIf { it.isNotBlank() }
        val countries = if (preferred != null) {
            listOf(preferred, "us").distinct()
        } else {
            listOfNotNull(gl.lowercase().takeIf { it.isNotBlank() }, "mm", "us").distinct()
        }
        for (country in countries) {
            val tracks = runCatching { appleMarketingCharts(country, limit) }.getOrDefault(emptyList())
                .ifEmpty { runCatching { itunesTopSongs(country, limit) }.getOrDefault(emptyList()) }
            if (tracks.isNotEmpty()) return tracks.take(limit)
        }
        return emptyList()
    }

    fun findSong(query: String, youtubeOnly: Boolean = false): SongItem? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return null
        if (!youtubeOnly && youtubeMusicOk) {
            val music = runCatching {
                parseSearch(post("search", mapOf("query" to trimmed, "params" to FILTER_SONG)))
            }.getOrDefault(SearchPage.Empty).songs
            MusicCatalog.preferOfficial(music, trimmed).firstOrNull()?.let { return it }
            music.firstOrNull()?.let { return it }
        }
        val videos = runCatching { searchYoutubeVideos(trimmed) }.getOrDefault(emptyList())
        return MusicCatalog.preferOfficial(videos, trimmed).firstOrNull() ?: videos.firstOrNull()
    }

    fun relatedSongs(videoId: String, youtubeOnly: Boolean = false): List<SongItem> {
        if (!videoId.isYoutubeVideoId()) return emptyList()
        if (!youtubeOnly && youtubeMusicOk) {
            val fromMusic = runCatching {
                parseNextSongs(
                    post(
                        "next",
                        mapOf(
                            "videoId" to videoId,
                            "playlistId" to "RDAMVM$videoId",
                            "isAudioOnly" to true,
                        ),
                    ),
                )
            }.getOrDefault(emptyList())
            if (fromMusic.size > 1) {
                return fromMusic.filter { it.id != videoId && !MusicCatalog.isNonMusic(it.title, it.subtitle) }
            }
        }
        return youtubeRadio(videoId)
            .filter { it.id != videoId && !MusicCatalog.isNonMusic(it.title, it.subtitle) }
    }

    fun mixAround(seed: SongItem?, youtubeOnly: Boolean = false): List<SongItem> {
        if (seed == null) return emptyList()
        val resolved = when {
            seed.id.isYoutubeVideoId() -> seed
            else -> runCatching {
                findSong(listOfNotNull(seed.title, seed.subtitle).joinToString(" "), youtubeOnly)
            }.getOrNull()
        } ?: return emptyList()
        val related = runCatching { relatedSongs(resolved.id, youtubeOnly) }.getOrDefault(emptyList())
        return (listOf(resolved) + related).distinctBy { it.id }
    }

    private fun youtubeRadio(videoId: String): List<SongItem> {
        // One good radio call is enough; avoid stacking 3 round-trips on the home path.
        val songs = runCatching {
            parseNextSongs(
                youtubeWebPost(
                    "next",
                    mapOf("videoId" to videoId, "playlistId" to "RDAMVM$videoId", "params" to "wAEB"),
                ),
            )
        }.getOrDefault(emptyList())
        if (songs.size > 1) return songs
        return runCatching {
            parseNextSongs(youtubeWebPost("next", mapOf("videoId" to videoId)))
        }.getOrDefault(emptyList())
    }

    private fun songsFromCharts(root: JSONObject?): List<SongItem> {
        if (root == null) return emptyList()
        val songs = LinkedHashMap<String, SongItem>()
        val playlists = mutableListOf<PlaylistItem>()
        parseSections(root).forEach { section ->
            section.items.forEach { item ->
                when (item) {
                    is SongItem -> songs.putIfAbsent(item.id, item)
                    is PlaylistItem -> playlists += item
                    else -> Unit
                }
            }
        }
        if (songs.isNotEmpty()) return songs.values.toList()
        return playlists.firstOrNull()?.let { chart ->
            runCatching { playlist(chart.id).songs }.getOrDefault(emptyList())
        }.orEmpty()
    }

    fun artist(browseId: String): BrowsePage {
        val root = post("browse", mapOf("browseId" to browseId))
        return enrichBrowse(parseBrowsePage(root, "Artist"), root)
    }

    /**
     * YouTube channel videos sorted by popularity (view count) — matches the
     * channel "Popular videos" shelf better than YTM artist "songs".
     */
    fun channelPopularVideos(channelId: String, limit: Int = 12): List<SongItem> {
        if (!channelId.startsWith("UC")) return emptyList()
        val root = runCatching {
            youtubeWebPost(
                "browse",
                mapOf(
                    "browseId" to channelId,
                    // Channel Videos tab (lockups include view counts for sorting).
                    "params" to CHANNEL_VIDEOS_PARAMS,
                ),
            )
        }.getOrNull() ?: return emptyList()
        return parseChannelVideoLockups(root).take(limit)
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

    /** Real uploader channel for [videoId] from player videoDetails (not a name guess). */
    fun videoUploader(videoId: String): VideoUploader? {
        if (!videoId.isYoutubeVideoId()) return null
        for (client in PLAYER_CLIENTS) {
            val json = runCatching { player(videoId, client) }.getOrNull() ?: continue
            parseVideoUploader(json)?.let { return it }
        }
        return null
    }

    fun lyrics(videoId: String): Lyrics? {
        val next = post("next", mapOf("videoId" to videoId, "isAudioOnly" to true))
        val browseId = parseLyricsBrowseId(next) ?: return null
        val web = parseLyrics(post("browse", mapOf("browseId" to browseId)))
        if (web?.synced == true) return web
        val timed = runCatching { parseLyrics(browseAndroid(browseId)) }.getOrNull()
        return if (timed?.synced == true) timed else web ?: timed
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
        return page.copy(songs = extra)
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
        return parseYoutubeVideos(youtubeWebPost("search", mapOf("query" to query, "params" to FILTER_WEB_VIDEO)))
    }

    private fun youtubeWebPost(endpoint: String, extra: Map<String, Any?>): JSONObject {
        val payload = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB")
                        .put("clientVersion", WEB_VERSION)
                        .put("hl", hl)
                        .put("gl", youtubeGl),
                ),
            )
        extra.forEach { (key, value) -> jsonValue(value)?.let { payload.put(key, it) } }
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false&key=${InnertubeSession.apiKey()}")
            .post(payload.toString().toRequestBody(JSON))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("YouTube $endpoint failed (${response.code}): ${text.take(180)}")
            return JSONObject(text)
        }
    }

    private fun player(videoId: String, client: PlayerClient): JSONObject {
        val clientJson = JSONObject()
            .put("clientName", client.name)
            .put("clientVersion", client.version)
            .put("hl", hl)
            .put("gl", youtubeGl)
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
            .put("gl", youtubeGl)
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
        check(youtubeMusicOk) { "YouTube Music unavailable" }
        val payload = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", CLIENT_NAME)
                        .put("clientVersion", InnertubeSession.clientVersion())
                        .put("hl", hl)
                        .put("gl", youtubeGl)
                        .put("visitorData", InnertubeSession.visitorData()),
                ),
            )
        extra.forEach { (key, value) ->
            jsonValue(value)?.let { payload.put(key, it) }
        }
        val url = "$BASE$endpoint?prettyPrint=false&key=${InnertubeSession.apiKey()}"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", CLIENT_NAME)
            .header("X-YouTube-Client-Version", InnertubeSession.clientVersion())
            .header("Origin", ORIGIN)
            .header("Referer", ORIGIN)
            .header("x-origin", ORIGIN)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 400) youtubeMusicOk = false
                error("YouTube Music $endpoint failed (${response.code}): ${text.take(180)}")
            }
            val json = JSONObject(text)
            if (text.contains("isn't available in your country", ignoreCase = true)) {
                youtubeMusicOk = false
            }
            return json
        }
    }

    private fun appleMarketingCharts(country: String, limit: Int): List<ChartTrack> {
        val url = "https://rss.marketingtools.apple.com/api/v2/$country/music/most-played/$limit/songs.json"
        val results = getJson(url).optJSONObject("feed")?.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val title = item.optString("name").trim()
                val artist = item.optString("artistName").trim()
                if (title.isBlank() || artist.isBlank()) continue
                add(ChartTrack(title, artist, item.optString("artworkUrl100").upgradeArtwork()))
            }
        }
    }

    private fun itunesTopSongs(country: String, limit: Int): List<ChartTrack> {
        val url = "https://itunes.apple.com/$country/rss/topsongs/limit=$limit/json"
        val entry = getJson(url).optJSONObject("feed")?.opt("entry") ?: return emptyList()
        val items = when (entry) {
            is JSONArray -> entry
            is JSONObject -> JSONArray().put(entry)
            else -> return emptyList()
        }
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val title = item.optJSONObject("im:name")?.optString("label").orEmpty().trim()
                val artist = item.optJSONObject("im:artist")?.optString("label").orEmpty().trim()
                if (title.isBlank() || artist.isBlank()) continue
                val images = item.optJSONArray("im:image")
                val artwork = images?.optJSONObject(images.length() - 1)?.optString("label")?.upgradeArtwork()
                add(ChartTrack(title, artist, artwork))
            }
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("GET $url failed (${response.code})")
            return JSONObject(text)
        }
    }

    private fun jsonValue(value: Any?): Any? = when (value) {
        null -> null
        is JSONObject, is JSONArray, is Number, is Boolean, is String -> value
        is Map<*, *> -> JSONObject().also { obj ->
            value.forEach { (key, nested) ->
                jsonValue(nested)?.let { obj.put(key.toString(), it) }
            }
        }
        is List<*> -> JSONArray().also { array ->
            value.forEach { nested -> jsonValue(nested)?.let(array::put) }
        }
        else -> value.toString()
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val BASE = "https://music.youtube.com/youtubei/v1/"
        private const val ORIGIN = "https://music.youtube.com/"
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val FILTER_SONG = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        private const val FILTER_VIDEO = "EgWKAQIQAWoKEAkQBRAKEAMQBA%3D%3D"
        private const val FILTER_WEB_VIDEO = "EgIQAQ%3D%3D"
        private const val WEB_VERSION = "2.20250317.01.00"
        /** Channel → Videos tab (WEB lockup grid with view counts). */
        private const val CHANNEL_VIDEOS_PARAMS = "EgZ2aWRlb3MYAyAAMAE="
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

private fun youtubeRegion(gl: String): String {
    val country = gl.uppercase()
    return if (country in YOUTUBE_UNAVAILABLE) "US" else country.ifBlank { "US" }
}

private val YOUTUBE_UNAVAILABLE = setOf("MM", "CN", "IR", "KP", "SY", "CU", "BY", "TM", "AF")

private fun String.isYoutubeVideoId(): Boolean =
    matches(Regex("""^[A-Za-z0-9_-]{11}$"""))

private fun String.upgradeArtwork(): String? {
    if (isBlank()) return null
    return hdArtwork()
}
