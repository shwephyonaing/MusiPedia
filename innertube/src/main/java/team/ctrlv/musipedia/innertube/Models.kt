package team.ctrlv.musipedia.innertube

sealed class YtItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String?
    abstract val thumbnail: String?
}

data class SongItem(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val thumbnail: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    val playlistId: String? = null,
    val videoType: String? = null,
) : YtItem()

data class ArtistItem(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val thumbnail: String? = null,
) : YtItem()

data class AlbumItem(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val thumbnail: String? = null,
) : YtItem()

data class PlaylistItem(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val thumbnail: String? = null,
) : YtItem()

/** Real YouTube uploader for a video (from player videoDetails), not a name search. */
data class VideoUploader(
    val channelId: String,
    val name: String,
)

data class HomeSection(
    val title: String,
    val items: List<YtItem>,
)

data class ChartTrack(
    val title: String,
    val artist: String,
    val artwork: String? = null,
)

data class SearchPage(
    val songs: List<SongItem>,
    val artists: List<ArtistItem>,
    val albums: List<AlbumItem>,
    val playlists: List<PlaylistItem>,
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()

    fun merge(other: SearchPage) = SearchPage(
        songs = (songs + other.songs).distinctBy { it.id },
        artists = (artists + other.artists).distinctBy { it.id },
        albums = (albums + other.albums).distinctBy { it.id },
        playlists = (playlists + other.playlists).distinctBy { it.id },
    )

    companion object {
        val Empty = SearchPage(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

enum class StreamKind { HLS, SEEKABLE, THROTTLED }

data class ResolvedAudio(
    val url: String,
    val userAgent: String,
    val client: String = "unknown",
    val kind: StreamKind = StreamKind.THROTTLED,
    val itag: Int = 0,
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
) {
    val isEmpty: Boolean get() = lines.isEmpty() || lines.all { it.text.isBlank() }
}

data class BrowsePage(
    val title: String,
    val subtitle: String? = null,
    val thumbnail: String? = null,
    val songs: List<SongItem> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
)
