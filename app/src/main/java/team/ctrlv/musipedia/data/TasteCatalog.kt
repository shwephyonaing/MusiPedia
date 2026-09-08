package team.ctrlv.musipedia

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Verified taste-picker artists.
 *
 * Source of truth on GitHub: `catalog/verified-artists.json` (fetched at runtime).
 * Bundled asset + on-disk cache keep the picker usable offline / on slow networks.
 */
class TasteCatalogStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheFile = File(appContext.filesDir, CACHE_FILE)
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _artists = MutableStateFlow(loadInitial())
    val artists: StateFlow<List<TasteArtist>> = _artists.asStateFlow()

    fun snapshot(): List<TasteArtist> = _artists.value

    fun isVerified(channelId: String?): Boolean {
        val id = channelId?.takeIf { it.startsWith("UC") } ?: return false
        return _artists.value.any { it.id == id }
    }

    fun matching(query: String): List<TasteArtist> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return _artists.value.filter { artist ->
            artist.name.lowercase().contains(q) ||
                q.contains(artist.name.lowercase()) ||
                artist.id.equals(query.trim(), ignoreCase = true)
        }
    }

    /**
     * Refresh from GitHub when stale (or [force]). Never clears the current list on failure.
     */
    suspend fun refresh(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val last = prefs.getLong(KEY_FETCHED_AT, 0L)
            if (!force && last > 0L && System.currentTimeMillis() - last < MIN_REFRESH_MS) {
                return@withLock false
            }
            val body = fetchRemote() ?: return@withLock false
            val parsed = parseCatalog(body) ?: return@withLock false
            if (parsed.isEmpty()) return@withLock false
            runCatching { cacheFile.writeText(body) }
            prefs.edit().putLong(KEY_FETCHED_AT, System.currentTimeMillis()).apply()
            _artists.value = parsed
            true
        }
    }

    private fun loadInitial(): List<TasteArtist> {
        readText(cacheFile)?.let { parseCatalog(it) }?.takeIf { it.isNotEmpty() }?.let { return it }
        readAsset()?.let { text ->
            runCatching { cacheFile.writeText(text) }
            parseCatalog(text)?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return emptyList()
    }

    private fun fetchRemote(): String? {
        for (url in REMOTE_URLS) {
            val text = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()?.takeIf { it.isNotBlank() }
                }
            }.getOrNull()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    private fun readAsset(): String? = runCatching {
        appContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun readText(file: File): String? =
        runCatching { if (file.isFile) file.readText() else null }.getOrNull()

    companion object {
        private const val PREFS = "musium_taste_catalog"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val CACHE_FILE = "verified-artists.json"
        private const val ASSET_PATH = "catalog/verified-artists.json"
        private const val MIN_REFRESH_MS = 6L * 60L * 60L * 1000L

        /** Prefer GitHub raw; jsDelivr as CDN fallback for flaky networks. */
        val REMOTE_URLS = listOf(
            "https://raw.githubusercontent.com/shwephyonaing/MusiPedia/main/catalog/verified-artists.json",
            "https://cdn.jsdelivr.net/gh/shwephyonaing/MusiPedia@main/catalog/verified-artists.json",
        )

        fun parseCatalog(raw: String): List<TasteArtist>? {
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val array = when {
                root.has("artists") -> root.optJSONArray("artists")
                else -> runCatching { JSONArray(raw) }.getOrNull()
            } ?: return null
            return buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("name").trim()
                    if (id.isBlank() || name.isBlank()) continue
                    add(
                        TasteArtist(
                            id = id,
                            name = name,
                            thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
    }
}
