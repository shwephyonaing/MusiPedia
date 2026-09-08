package team.ctrlv.musipedia

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RecentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("musium_recent", Context.MODE_PRIVATE)
    @Volatile private var songCache: List<PlayableSong>? = null
    @Volatile private var queryCache: List<String>? = null

    @Synchronized
    fun songs(): List<PlayableSong> {
        songCache?.let { return it }
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                add(
                    PlayableSong(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        localUri = obj.optString("localUri").takeIf { it.isNotBlank() },
                        playlistId = obj.optString("playlistId").takeIf { it.isNotBlank() },
                        artistId = obj.optString("artistId").takeIf { it.isNotBlank() },
                        albumId = obj.optString("albumId").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }.filter { it.id.isNotBlank() }.also { songCache = it }
    }

    @Synchronized
    fun add(song: PlayableSong) {
        val next = (listOf(song) + songs().filterNot { it.id == song.id }).take(40)
        songCache = next
        val array = JSONArray()
        next.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("thumbnailUrl", item.thumbnailUrl)
                    .put("localUri", item.localUri)
                    .put("playlistId", item.playlistId)
                    .put("artistId", item.artistId)
                    .put("albumId", item.albumId),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    @Synchronized
    fun queries(): List<String> {
        queryCache?.let { return it }
        val raw = prefs.getString(QUERIES, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }.also { queryCache = it }
    }

    @Synchronized
    fun addQuery(query: String) {
        val next = query.trim().takeIf { it.isNotBlank() } ?: return
        saveQueries((listOf(next) + queries().filterNot { it.equals(next, ignoreCase = true) }).take(25))
    }

    @Synchronized
    fun removeQuery(query: String) {
        saveQueries(queries().filterNot { it.equals(query, ignoreCase = true) })
    }

    private fun saveQueries(items: List<String>) {
        queryCache = items
        val array = JSONArray()
        items.forEach { array.put(it) }
        prefs.edit().putString(QUERIES, array.toString()).apply()
    }

    companion object {
        private const val KEY = "songs"
        private const val QUERIES = "queries"
    }
}
