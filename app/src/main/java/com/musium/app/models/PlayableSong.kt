package com.musium.app

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musium.innertube.SongItem
import java.io.File

data class PlayableSong(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val localUri: String? = null,
    val playlistId: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
) {
    val isLocal: Boolean get() = id.startsWith("local:")
    val canDownload: Boolean get() = !isLocal

    fun toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_ID, id)
            putString(EXTRA_TITLE, title)
            putString(EXTRA_ARTIST, artist)
            putString(EXTRA_THUMB, thumbnailUrl)
            putString(EXTRA_LOCAL, localUri)
            putString(EXTRA_PLAYLIST, playlistId)
            putString(EXTRA_ARTIST_ID, artistId)
            putString(EXTRA_ALBUM_ID, albumId)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(playbackUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(thumbnailUrl?.toUri())
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    private fun playbackUri() = when {
        localUri.isNullOrBlank() -> "$STREAM_SCHEME://watch/$id".toUri()
        localUri.startsWith("content:") || localUri.startsWith("file:") -> localUri.toUri()
        else -> File(localUri).toUri()
    }

    companion object {
        const val STREAM_SCHEME = "musium"
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_THUMB = "thumbnailUrl"
        private const val EXTRA_LOCAL = "localUri"
        private const val EXTRA_PLAYLIST = "playlistId"
        private const val EXTRA_ARTIST_ID = "artistId"
        private const val EXTRA_ALBUM_ID = "albumId"

        fun from(item: MediaItem): PlayableSong? {
            val extras = item.mediaMetadata.extras ?: return null
            val id = extras.getString(EXTRA_ID) ?: item.mediaId
            return PlayableSong(
                id = id,
                title = extras.getString(EXTRA_TITLE) ?: item.mediaMetadata.title?.toString().orEmpty(),
                artist = extras.getString(EXTRA_ARTIST) ?: item.mediaMetadata.artist?.toString().orEmpty(),
                thumbnailUrl = extras.getString(EXTRA_THUMB),
                localUri = extras.getString(EXTRA_LOCAL),
                playlistId = extras.getString(EXTRA_PLAYLIST),
                artistId = extras.getString(EXTRA_ARTIST_ID),
                albumId = extras.getString(EXTRA_ALBUM_ID),
            )
        }
    }
}

fun SongItem.toPlayable(): PlayableSong = PlayableSong(
    id = id,
    title = title,
    artist = subtitle ?: "YouTube Music",
    thumbnailUrl = thumbnail,
    playlistId = playlistId,
    artistId = artistId,
    albumId = albumId,
)

fun LocalSong.toPlayable(): PlayableSong = PlayableSong(
    id = "local:$id",
    title = title,
    artist = artist,
    thumbnailUrl = artworkUri?.toString(),
    localUri = audioUri.toString(),
)
