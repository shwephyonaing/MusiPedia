package com.musium.app

import androidx.compose.runtime.staticCompositionLocalOf
import com.musium.innertube.AlbumItem
import com.musium.innertube.ArtistItem
import com.musium.innertube.PlaylistItem
import com.musium.innertube.SongItem
import com.musium.innertube.YtItem

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { null }
val LocalMusicRouter = staticCompositionLocalOf<(MusicRoute) -> Unit> { {} }

sealed class MusicRoute {
    data class Artist(val id: String, val name: String, val profileImage: String? = null) : MusicRoute()
    data class Album(val id: String, val name: String) : MusicRoute()
    data class Playlist(val id: String, val name: String) : MusicRoute()
    data object Downloads : MusicRoute()
    data object Favorites : MusicRoute()
}

fun YtItem.open(player: PlayerConnection?, router: (MusicRoute) -> Unit, queue: List<PlayableSong>? = null) {
    when (this) {
        is SongItem -> {
            val song = toPlayable()
            // Explicit collections (album/playlist/downloads/favorites) pass a queue.
            // Search / charts / loose lists play the song + radio so you don't get
            // 10 near-duplicates of the same track.
            if (queue != null && queue.size > 1) {
                val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                player?.play(queue, index)
            } else {
                player?.playSong(song)
            }
        }
        is ArtistItem -> router(MusicRoute.Artist(id, title, thumbnail))
        is AlbumItem -> router(MusicRoute.Album(id, title))
        is PlaylistItem -> router(MusicRoute.Playlist(id, title))
    }
}
