package team.ctrlv.musipedia

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TasteArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
)

class TasteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("musium_taste", Context.MODE_PRIVATE)
    @Volatile private var artistCache: List<TasteArtist>? = null

    fun isComplete(): Boolean = artists().size >= MIN_ARTISTS

    @Synchronized
    fun artists(): List<TasteArtist> {
        artistCache?.let { return it }
        val array = runCatching { JSONArray(prefs.getString(KEY_ARTISTS, "[]")) }.getOrNull()
            ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val name = obj.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    TasteArtist(
                        id = id,
                        name = name,
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }.also { artistCache = it }
    }

    @Synchronized
    fun save(artists: List<TasteArtist>) {
        artistCache = artists
        val artistArray = JSONArray()
        artists.forEach { artist ->
            artistArray.put(
                JSONObject()
                    .put("id", artist.id)
                    .put("name", artist.name)
                    .put("thumbnailUrl", artist.thumbnailUrl),
            )
        }
        prefs.edit()
            .putString(KEY_ARTISTS, artistArray.toString())
            .remove(KEY_GENRES)
            .putBoolean(KEY_ONBOARDED, true)
            .apply()
    }

    /** True once the user has finished the first taste screen. */
    fun hasOnboarded(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false) || isComplete()

    companion object {
        const val MIN_ARTISTS = 3
        private const val KEY_ARTISTS = "artists"
        private const val KEY_GENRES = "genres"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
