package com.musium.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DownloadStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("musium_offline", Context.MODE_PRIVATE)
    val folder: File = File(context.applicationContext.filesDir, "offline").apply {
        mkdirs()
        File(this, ".nomedia").apply { if (!exists()) writeText("") }
    }

    fun fileFor(id: String): File = File(folder, "${safeId(id)}.bin")

    @Synchronized
    fun songs(): List<PlayableSong> {
        val kept = mutableListOf<PlayableSong>()
        read().forEach { song ->
            val file = fileFor(song.id)
            if (file.exists() && file.length() >= 1024) {
                kept += song.copy(localUri = file.absolutePath)
            } else {
                runCatching { file.delete() }
            }
        }
        save(kept)
        return kept
    }

    @Synchronized
    fun get(id: String): PlayableSong? = songs().firstOrNull { it.id == id }

    fun has(id: String): Boolean = get(id) != null

    @Synchronized
    fun add(song: PlayableSong, file: File) {
        val next = PlayableSong(
            id = song.id,
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnailUrl,
            localUri = file.absolutePath,
            playlistId = song.playlistId,
            artistId = song.artistId,
            albumId = song.albumId,
        )
        save((listOf(next) + read().filterNot { it.id == song.id }))
    }

    @Synchronized
    fun remove(id: String) {
        runCatching { fileFor(id).delete() }
        save(read().filterNot { it.id == id })
    }

    fun attach(song: PlayableSong): PlayableSong = get(song.id) ?: song

    private fun read(): List<PlayableSong> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optString("id")
                if (id.isBlank()) continue
                add(
                    PlayableSong(
                        id = id,
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
        }
    }

    private fun save(items: List<PlayableSong>) {
        val array = JSONArray()
        items.forEach { item ->
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

    companion object {
        private const val KEY = "songs"
        fun safeId(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
    }
}
