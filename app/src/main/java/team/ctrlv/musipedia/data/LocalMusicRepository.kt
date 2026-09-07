package team.ctrlv.musipedia

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicRepository(private val context: Context) {
    suspend fun songs(): List<LocalSong> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(
            collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val albumId = cursor.getLong(albumColumn)
                    add(
                        LocalSong(
                            id = id,
                            title = cursor.getString(titleColumn).orEmpty().ifBlank { "Unknown title" },
                            artist = cursor.getString(artistColumn).orEmpty().ifBlank { "Unknown artist" },
                            durationMs = cursor.getLong(durationColumn),
                            audioUri = ContentUris.withAppendedId(collection, id),
                            artworkUri = ContentUris.withAppendedId(
                                android.net.Uri.parse("content://media/external/audio/albumart"), albumId,
                            ),
                        ),
                    )
                }
            }
        }.orEmpty()
    }
}
