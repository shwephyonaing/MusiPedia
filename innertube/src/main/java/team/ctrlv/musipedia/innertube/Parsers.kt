package team.ctrlv.musipedia.innertube

import org.json.JSONObject

internal fun parseLyricsBrowseId(root: JSONObject): String? {
    var found: String? = null
    root.walkObjects { key, obj ->
        if (key != "tabRenderer" || obj.optBoolean("unselectable")) return@walkObjects
        val pageType = obj.child("endpoint", "browseEndpoint")?.pageType()
        val title = obj.runsText("title") ?: obj.str("title")
        val browseId = obj.child("endpoint", "browseEndpoint")?.str("browseId")
        if (pageType == "MUSIC_PAGE_TYPE_TRACK_LYRICS" ||
            title.equals("Lyrics", ignoreCase = true) ||
            browseId.orEmpty().startsWith("MPLYt")
        ) {
            found = browseId ?: found
        }
    }
    return found
}

internal fun parseLyrics(root: JSONObject): Lyrics? {
    val timed = linkedMapOf<Pair<Long, String>, LyricLine>()
    root.walkObjects { _, obj ->
        obj.arr("timedLyricsData")?.objects()?.forEach { line ->
            parseTimedLine(line)?.let { timed.putIfAbsent(it.timeMs to it.text, it) }
        }
        parseTimedLine(obj)?.let { timed.putIfAbsent(it.timeMs to it.text, it) }
    }
    if (timed.isNotEmpty()) return Lyrics(timed.values.sortedBy { it.timeMs }, synced = true)
    var plain: String? = null
    root.walkObjects { key, obj ->
        if (key == "musicDescriptionShelfRenderer") {
            plain = obj.runsText("description") ?: plain
        }
    }
    val lines = plain?.lines().orEmpty()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { LyricLine(0, it) }
    return lines.takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = false) }
}

private fun parseTimedLine(line: JSONObject): LyricLine? {
    val text = line.str("lyricLine") ?: line.runsText("lyricLine") ?: return null
    val range = line.child("cueRange") ?: return null
    val start = range.optString("startTimeMilliseconds").toLongOrNull()
        ?: range.optLong("startTimeMilliseconds")
    if (start < 0) return null
    return LyricLine(start, text)
}

internal fun parseSearchSuggestions(root: JSONObject): List<String> {
    val out = linkedSetOf<String>()
    root.walkObjects { key, obj ->
        if (key != "searchSuggestionRenderer") return@walkObjects
        obj.child("navigationEndpoint", "searchEndpoint")?.str("query")?.let { out += it }
        obj.runsText("suggestion")?.let { out += it }
    }
    return out.toList()
}

internal fun parseSearch(root: JSONObject): SearchPage {
    val songs = linkedMapOf<String, SongItem>()
    val artists = linkedMapOf<String, ArtistItem>()
    val albums = linkedMapOf<String, AlbumItem>()
    val playlists = linkedMapOf<String, PlaylistItem>()
    collectItems(root).forEach { item ->
        when (item) {
            is SongItem -> songs.putIfAbsent(item.id, item)
            is ArtistItem -> artists.putIfAbsent(item.id, item)
            is AlbumItem -> albums.putIfAbsent(item.id, item)
            is PlaylistItem -> playlists.putIfAbsent(item.id, item)
        }
    }
    return SearchPage(
        songs = songs.values.toList(),
        artists = artists.values.toList(),
        albums = albums.values.toList(),
        playlists = playlists.values.toList(),
    )
}

internal fun parseSections(root: JSONObject): List<HomeSection> {
    val sections = mutableListOf<HomeSection>()
    root.walkObjects { key, obj ->
        if (key != "musicCarouselShelfRenderer" && key != "musicShelfRenderer") return@walkObjects
        val title = obj.runsText("title")
            ?: obj.child("header", "musicCarouselShelfBasicHeaderRenderer")?.runsText("title")
            ?: obj.child("header", "musicShelfHeaderRenderer")?.runsText("title")
            ?: return@walkObjects
        val items = collectItems(obj)
        if (items.isNotEmpty()) sections += HomeSection(title, items.distinctBy { it.id + it::class.simpleName })
    }
    return sections.distinctBy { it.title }
}

internal fun parseBrowsePage(root: JSONObject, fallbackTitle: String): BrowsePage {
    var title = fallbackTitle
    var subtitle: String? = null
    var thumbnail: String? = null
    var avatar: String? = null
    root.walkObjects { key, obj ->
        if (key == "musicImmersiveHeaderRenderer" || key == "musicVisualHeaderRenderer" ||
            key == "musicResponsiveHeaderRenderer" || key == "musicDetailHeaderRenderer"
        ) {
            obj.runsText("title")?.let { title = it }
            subtitle = obj.runsText("subtitle") ?: obj.runsText("straplineTextOne") ?: subtitle
            // Immersive artist pages: foregroundThumbnail is the circular avatar;
            // thumbnail is often a wide banner (looks wrong when cropped to a circle).
            if (key == "musicImmersiveHeaderRenderer") {
                avatar = obj.obj("foregroundThumbnail")?.bestSquareThumbnail()
                    ?: obj.child("foregroundThumbnail", "musicThumbnailRenderer", "thumbnail")?.bestSquareThumbnail()
                    ?: avatar
            }
            avatar = avatar
                ?: obj.obj("thumbnail")?.bestSquareThumbnail()
                ?: obj.bestSquareThumbnail()
            thumbnail = obj.bestThumbnail() ?: thumbnail
        }
    }
    val songs = collectItems(root).filterIsInstance<SongItem>().distinctBy { it.id }
    val sections = parseSections(root).map { section ->
        section.copy(items = section.items.filterNot { it is SongItem && songs.any { song -> song.id == it.id } })
    }.filter { it.items.isNotEmpty() }
    val profile = avatar ?: thumbnail
    return BrowsePage(
        title = title,
        subtitle = subtitle,
        // Artist heroes must be avatars, not a random song cover.
        thumbnail = when {
            fallbackTitle.equals("Artist", ignoreCase = true) -> profile
            else -> profile ?: songs.firstOrNull()?.thumbnail
        },
        songs = songs,
        sections = sections,
    )
}

internal fun parseVideoUploader(player: JSONObject): VideoUploader? {
    val details = player.obj("videoDetails")
    val channelId = details?.str("channelId")
        ?: player.child("microformat", "playerMicroformatRenderer")?.str("externalChannelId")
        ?: return null
    if (!channelId.startsWith("UC")) return null
    val name = details?.str("author")
        ?: player.child("microformat", "playerMicroformatRenderer")?.str("ownerChannelName")
        ?: return VideoUploader(channelId, channelId)
    return VideoUploader(channelId, name)
}

internal fun parseNextSongs(root: JSONObject): List<SongItem> {
    val songs = linkedMapOf<String, SongItem>()
    root.walkObjects { key, obj ->
        if (key != "playlistPanelVideoRenderer") return@walkObjects
        parseSongFromPanel(obj)?.let { songs.putIfAbsent(it.id, it) }
    }
    if (songs.isEmpty()) {
        collectItems(root).filterIsInstance<SongItem>().forEach { songs.putIfAbsent(it.id, it) }
    }
    return songs.values.toList()
}

internal fun parseYoutubeVideos(root: JSONObject): List<SongItem> {
    val songs = linkedMapOf<String, SongItem>()
    root.walkObjects { key, obj ->
        if (key != "videoRenderer") return@walkObjects
        val videoId = obj.str("videoId") ?: return@walkObjects
        val title = obj.runsText("title") ?: return@walkObjects
        val subtitle = obj.runsText("ownerText") ?: obj.runsText("shortBylineText") ?: obj.runsText("longBylineText")
        val seconds = obj.clockSeconds()
        if (MusicCatalog.isNonMusic(title, subtitle)) return@walkObjects
        if (seconds != null && seconds > 15 * 60 && !MusicCatalog.looksOfficial(title, subtitle)) return@walkObjects
        songs.putIfAbsent(
            videoId,
            SongItem(
                id = videoId,
                title = title,
                subtitle = subtitle,
                thumbnail = obj.bestThumbnail(),
                artistId = obj.firstChannelId(),
                videoType = obj.musicVideoType(),
            ),
        )
    }
    return songs.values.toList()
}

internal fun playlistIdsFrom(root: JSONObject): List<String> {
    val ids = linkedSetOf<String>()
    root.walkObjects { key, obj ->
        when (key) {
            "watchPlaylistEndpoint", "watchEndpoint" ->
                obj.str("playlistId")?.let { ids += it.removePrefix("VL") }
            "browseEndpoint" -> {
                val browseId = obj.str("browseId") ?: return@walkObjects
                if (browseId.startsWith("VL") || browseId.startsWith("PL") ||
                    browseId.startsWith("OL") || browseId.startsWith("RDCLAK")
                ) {
                    ids += browseId.removePrefix("VL")
                }
            }
        }
    }
    return ids.toList()
}

internal data class StreamCandidate(
    val url: String,
    val itag: Int,
    val mime: String,
    val kindHint: StreamKind,
    val contentLength: Long,
)

internal fun collectStreams(player: JSONObject): List<StreamCandidate> {
    val playability = player.child("playabilityStatus")
    val status = playability?.str("status")
    if (status != null && status != "OK") {
        android.util.Log.w("MusiPediaPlay", "collectStreams skip status=$status reason=${playability.str("reason")}")
        return emptyList()
    }
    val streaming = player.obj("streamingData") ?: return emptyList()
    val out = mutableListOf<StreamCandidate>()
    streaming.str("hlsManifestUrl")?.let {
        out += StreamCandidate(it, 0, "application/x-mpegURL", StreamKind.HLS, 0)
    }
    val formats = buildList {
        streaming.arr("formats")?.objects()?.forEach(::add)
        streaming.arr("adaptiveFormats")?.objects()?.forEach(::add)
    }
    formats.forEach { format ->
        val url = format.str("url") ?: return@forEach
        val mime = format.str("mimeType").orEmpty()
        val itag = format.optInt("itag")
        val length = format.optString("contentLength").toLongOrNull() ?: format.optLong("contentLength")
        val hint = when {
            itag == 18 || (mime.contains("video/") && mime.contains("avc1") && mime.contains("mp4a")) -> StreamKind.SEEKABLE
            else -> StreamKind.THROTTLED
        }
        out += StreamCandidate(url, itag, mime, hint, length)
    }
    android.util.Log.d(
        "MusiPediaPlay",
        "collectStreams status=$status hls=${out.count { it.kindHint == StreamKind.HLS }} " +
            "progressive=${out.count { it.kindHint != StreamKind.HLS }}",
    )
    return out
}

internal fun pickAudioUrl(player: JSONObject): String? {
    val playability = player.child("playabilityStatus")
    val status = playability?.str("status")
    val reason = playability?.str("reason")
    if (status != null && status != "OK") {
        android.util.Log.w(
            "MusiPediaPlay",
            "pickAudioUrl skip status=$status reason=$reason",
        )
        return null
    }
    val streaming = player.obj("streamingData")
    if (streaming == null) {
        android.util.Log.w("MusiPediaPlay", "pickAudioUrl no streamingData status=$status")
        return null
    }
    val formats = buildList {
        streaming.arr("adaptiveFormats")?.objects()?.forEach(::add)
        streaming.arr("formats")?.objects()?.forEach(::add)
    }
    val audio = formats.filter { format ->
        !format.str("url").isNullOrBlank() &&
            format.str("mimeType").orEmpty().contains("audio")
    }
    val ciphered = formats.count { !it.str("signatureCipher").isNullOrBlank() || !it.str("cipher").isNullOrBlank() }
    val mp4 = audio.filter { it.str("mimeType").orEmpty().contains("mp4") }
    val chosen = (mp4.ifEmpty { audio }).maxByOrNull { it.optInt("bitrate") }
        ?: formats.firstOrNull { !it.str("url").isNullOrBlank() }
    android.util.Log.d(
        "MusiPediaPlay",
        "pickAudioUrl status=$status reason=$reason formats=${formats.size} audio=${audio.size} " +
            "mp4=${mp4.size} ciphered=$ciphered chosenMime=${chosen?.str("mimeType")} " +
            "itag=${chosen?.optInt("itag")} bitrate=${chosen?.optInt("bitrate")}",
    )
    return chosen?.str("url")
}

private fun collectItems(root: JSONObject): List<YtItem> {
    val items = mutableListOf<YtItem>()
    root.walkObjects { key, obj ->
        when (key) {
            "musicResponsiveListItemRenderer" -> parseListItem(obj)?.let(items::add)
            "musicTwoRowItemRenderer" -> parseTwoRow(obj)?.let(items::add)
            "musicCardShelfRenderer" -> parseCard(obj)?.let(items::add)
        }
    }
    return items.distinctBy { it.id + it::class.simpleName }
}

private fun parseListItem(obj: JSONObject): YtItem? {
    val videoId = obj.child("playlistItemData")?.str("videoId") ?: obj.firstVideoId()
    val title = flexText(obj, 0) ?: obj.runsText("title") ?: "Song"
    val subtitle = flexText(obj, 1)
    val thumbnail = obj.bestThumbnail()
    if (!videoId.isNullOrBlank()) {
        return SongItem(
            id = videoId,
            title = title,
            subtitle = subtitle,
            thumbnail = thumbnail,
            artistId = obj.firstChannelId() ?: firstBrowseId(obj, "MUSIC_PAGE_TYPE_ARTIST"),
            albumId = firstBrowseId(obj, "MUSIC_PAGE_TYPE_ALBUM"),
            playlistId = obj.child("menu", "menuRenderer")?.firstPlaylistId(),
            videoType = obj.musicVideoType(),
        )
    }
    return browseItem(obj, title, subtitle, thumbnail)
}

private fun parseCard(obj: JSONObject): YtItem? {
    val title = obj.runsText("title") ?: return null
    val subtitle = obj.runsText("subtitle")
    val thumbnail = obj.bestThumbnail()
    val videoId = obj.firstVideoId()
    if (!videoId.isNullOrBlank()) {
        return SongItem(id = videoId, title = title, subtitle = subtitle, thumbnail = thumbnail, videoType = obj.musicVideoType())
    }
    return browseItem(obj, title, subtitle, thumbnail)
}

private fun parseTwoRow(obj: JSONObject): YtItem? {
    val title = obj.runsText("title") ?: return null
    val subtitle = obj.runsText("subtitle")
    val thumbnail = obj.bestThumbnail()
    val videoId = obj.firstVideoId()
    val pageType = obj.browseEndpoint()?.pageType()
    if (pageType == null && !videoId.isNullOrBlank()) {
        return SongItem(id = videoId, title = title, subtitle = subtitle, thumbnail = thumbnail, videoType = obj.musicVideoType())
    }
    return browseItem(obj, title, subtitle, thumbnail) ?: videoId?.let {
        SongItem(id = it, title = title, subtitle = subtitle, thumbnail = thumbnail, videoType = obj.musicVideoType())
    }
}

private fun parseSongFromPanel(obj: JSONObject): SongItem? {
    val videoId = obj.firstVideoId() ?: return null
    return SongItem(
        id = videoId,
        title = obj.runsText("title") ?: return null,
        subtitle = obj.runsText("shortBylineText") ?: obj.runsText("longBylineText"),
        thumbnail = obj.bestThumbnail(),
        playlistId = obj.child("navigationEndpoint", "watchEndpoint")?.str("playlistId"),
        artistId = obj.firstChannelId() ?: firstBrowseId(obj, "MUSIC_PAGE_TYPE_ARTIST"),
        videoType = obj.musicVideoType(),
    )
}

private fun browseItem(obj: JSONObject, title: String, subtitle: String?, thumbnail: String?): YtItem? {
    val browse = obj.browseEndpoint() ?: return null
    val browseId = browse.str("browseId") ?: return null
    return when (browse.pageType()) {
        "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" ->
            ArtistItem(
                browseId,
                title,
                subtitle,
                obj.bestSquareThumbnail() ?: thumbnail?.hdProfileArtwork(),
            )
        "MUSIC_PAGE_TYPE_ALBUM" -> AlbumItem(browseId, title, subtitle, thumbnail)
        "MUSIC_PAGE_TYPE_PLAYLIST", "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" ->
            PlaylistItem(browseId.removePrefix("VL"), title, subtitle, thumbnail)
        else -> when {
            browseId.startsWith("UC") -> ArtistItem(browseId, title, subtitle, thumbnail)
            browseId.startsWith("MPRE") || browseId.startsWith("MPBE") ->
                AlbumItem(browseId, title, subtitle, thumbnail)
            browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("OL") ||
                browseId.startsWith("RD") ->
                PlaylistItem(browseId.removePrefix("VL"), title, subtitle, thumbnail)
            else -> null
        }
    }
}

private fun flexText(obj: JSONObject, index: Int): String? {
    val columns = obj.arr("flexColumns") ?: return null
    val column = columns.optJSONObject(index) ?: return null
    return column.obj("musicResponsiveListItemFlexColumnRenderer")?.runsText("text")
}

private fun firstBrowseId(obj: JSONObject, pageType: String): String? {
    var found: String? = null
    obj.walkObjects { key, child ->
        if (found == null && key == "browseEndpoint" && child.pageType() == pageType) {
            found = child.str("browseId")
        }
    }
    return found
}

/** Prefer a real YouTube channel id (UC…) from byline/owner endpoints. */
private fun JSONObject.firstChannelId(): String? {
    var found: String? = null
    walkObjects { key, child ->
        if (found != null || key != "browseEndpoint") return@walkObjects
        val browseId = child.str("browseId") ?: return@walkObjects
        if (browseId.startsWith("UC")) found = browseId
    }
    return found
}

private fun JSONObject.firstPlaylistId(): String? {
    var found: String? = null
    walkObjects { _, obj ->
        if (found == null) found = obj.str("playlistId")
    }
    return found
}
