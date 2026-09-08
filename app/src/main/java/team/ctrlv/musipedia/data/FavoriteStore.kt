package team.ctrlv.musipedia

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class FavoriteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("musium_favorites", Context.MODE_PRIVATE)
    @Volatile private var cache: List<PlayableSong>? = null
    @Volatile private var idSet: Set<String>? = null

    @Synchronized
    fun songs(): List<PlayableSong> {
        cache?.let { return it }
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    PlayableSong(
                        id = id,
                        title = item.optString("title"),
                        artist = item.optString("artist"),
                        thumbnailUrl = item.optString("thumbnailUrl").takeIf(String::isNotBlank),
                        localUri = item.optString("localUri").takeIf(String::isNotBlank),
                        playlistId = item.optString("playlistId").takeIf(String::isNotBlank),
                        artistId = item.optString("artistId").takeIf(String::isNotBlank),
                        albumId = item.optString("albumId").takeIf(String::isNotBlank),
                    ),
                )
            }
        }.also {
            cache = it
            idSet = it.mapTo(HashSet()) { song -> song.id }
        }
    }

    @Synchronized
    fun contains(id: String): Boolean {
        idSet?.let { return id in it }
        return songs().any { it.id == id }
    }

    @Synchronized
    fun toggle(song: PlayableSong): Boolean {
        val existing = songs()
        val adding = existing.none { it.id == song.id }
        save(if (adding) listOf(song) + existing else existing.filterNot { it.id == song.id })
        return adding
    }

    @Synchronized
    fun remove(id: String) = save(songs().filterNot { it.id == id })

    private fun save(songs: List<PlayableSong>) {
        cache = songs
        idSet = songs.mapTo(HashSet()) { it.id }
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("thumbnailUrl", song.thumbnailUrl)
                    .put("localUri", song.localUri)
                    .put("playlistId", song.playlistId)
                    .put("artistId", song.artistId)
                    .put("albumId", song.albumId),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "songs"
    }
}
