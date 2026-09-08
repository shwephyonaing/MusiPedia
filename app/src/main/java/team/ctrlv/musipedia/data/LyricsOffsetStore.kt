package team.ctrlv.musipedia

import android.content.Context
import org.json.JSONObject

/** Per-track lyrics timing offset in milliseconds. Positive = delay lyrics. */
class LyricsOffsetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("musium_lyrics_offset", Context.MODE_PRIVATE)
    @Volatile private var cache: MutableMap<String, Long>? = null

    @Synchronized
    fun get(songId: String): Long {
        if (songId.isBlank()) return 0L
        return map()[songId] ?: 0L
    }

    @Synchronized
    fun set(songId: String, offsetMs: Long) {
        if (songId.isBlank()) return
        val next = map().toMutableMap()
        val clamped = offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        if (clamped == 0L) next.remove(songId) else next[songId] = clamped
        cache = next
        persist(next)
    }

    @Synchronized
    fun clear(songId: String) = set(songId, 0L)

    private fun map(): MutableMap<String, Long> {
        cache?.let { return it }
        val raw = prefs.getString(KEY, "{}") ?: "{}"
        val parsed = runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val value = obj.optLong(key, 0L)
                    if (value != 0L) put(key, value)
                }
            }.toMutableMap()
        }.getOrElse { mutableMapOf() }
        return parsed.also { cache = it }
    }

    private fun persist(map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (id, offset) -> obj.put(id, offset) }
        prefs.edit().putString(KEY, obj.toString()).apply()
    }

    companion object {
        const val MAX_OFFSET_MS = 10_000L
        const val STEP_MS = 250L
        private const val KEY = "offsets"
    }
}
