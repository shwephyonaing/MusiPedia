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
}

fun YtItem.open(player: PlayerConnection?, router: (MusicRoute) -> Unit, queue: List<PlayableSong>? = null) {
    when (this) {
        is SongItem -> {
            val song = toPlayable()
            val songs = queue?.takeIf { it.isNotEmpty() } ?: listOf(song)
            val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            player?.play(songs, index)
        }
        is ArtistItem -> router(MusicRoute.Artist(id, title, thumbnail))
        is AlbumItem -> router(MusicRoute.Album(id, title))
        is PlaylistItem -> router(MusicRoute.Playlist(id, title))
    }
}
