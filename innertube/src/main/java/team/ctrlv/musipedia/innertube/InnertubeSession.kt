package team.ctrlv.musipedia.innertube

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Innertube client config: baked values are always the primary fallback.
 * Fresh visitorData / clientVersion / apiKey are fetched in the background and cached.
 */
object InnertubeSession {
    private const val TAG = "InnertubeSession"
    private const val PREFS = "innertube_session"
    private const val KEY_API = "api_key"
    private const val KEY_VISITOR = "visitor_data"
    private const val KEY_VERSION = "client_version"
    private const val KEY_SYNCED_AT = "synced_at"
    private const val TTL_MS = 12L * 60L * 60L * 1000L

    const val BAKED_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    const val BAKED_VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"
    const val BAKED_CLIENT_VERSION = "1.20250317.01.00"

    @Volatile private var apiKey: String = BAKED_API_KEY
    @Volatile private var visitorData: String = BAKED_VISITOR_DATA
    @Volatile private var clientVersion: String = BAKED_CLIENT_VERSION

    @Volatile private var prefs: android.content.SharedPreferences? = null
    private val syncing = AtomicBoolean(false)

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun apiKey(): String = apiKey
    fun visitorData(): String = visitorData
    fun clientVersion(): String = clientVersion

    /** Load disk cache (if any) then kick a background refresh when stale. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY_API, null)?.takeIf(::isApiKey)?.let { apiKey = it }
        p.getString(KEY_VISITOR, null)?.takeIf(::isVisitorData)?.let { visitorData = it }
        p.getString(KEY_VERSION, null)?.takeIf(::isMusicClientVersion)?.let { clientVersion = it }
        ensureBackgroundSync(force = false)
    }

    /** Non-blocking. Safe to call often; throttled by TTL unless [force]. */
    fun ensureBackgroundSync(force: Boolean = false) {
        val syncedAt = prefs?.getLong(KEY_SYNCED_AT, 0L) ?: 0L
        val stale = syncedAt == 0L || System.currentTimeMillis() - syncedAt >= TTL_MS
        if (!force && !stale) return
        if (!syncing.compareAndSet(false, true)) return
        Thread(
            {
                try {
                    refreshFromNetwork()
                } catch (t: Throwable) {
                    Log.w(TAG, "background sync failed: ${t.message}")
                } finally {
                    syncing.set(false)
                }
            },
            "innertube-session-sync",
        ).apply { isDaemon = true }.start()
    }

    private fun refreshFromNetwork() {
        var nextVisitor: String? = null
        var nextVersion: String? = null
        var nextApiKey: String? = null

        runCatching { fetchSwJsData() }.onSuccess { (visitor, version) ->
            nextVisitor = visitor
            nextVersion = version
        }.onFailure {
            Log.w(TAG, "sw.js_data failed: ${it.message}")
        }

        runCatching { fetchApiKeyFromHtml("https://music.youtube.com/") }.onSuccess {
            nextApiKey = it
        }.onFailure {
            // Geo-blocks are common; WEB_REMIX baked key remains primary.
            Log.d(TAG, "music.youtube.com key scrape skipped: ${it.message}")
        }

        if (nextVisitor == null && nextVersion == null && nextApiKey == null) return

        nextVisitor?.let { visitorData = it }
        nextVersion?.let { clientVersion = it }
        nextApiKey?.let { apiKey = it }

        prefs?.edit()?.apply {
            nextVisitor?.let { putString(KEY_VISITOR, it) }
            nextVersion?.let { putString(KEY_VERSION, it) }
            nextApiKey?.let { putString(KEY_API, it) }
            putLong(KEY_SYNCED_AT, System.currentTimeMillis())
            apply()
        }
        Log.d(
            TAG,
            "synced visitor=${nextVisitor != null} version=${nextVersion ?: "-"} apiKey=${nextApiKey != null}",
        )
    }

    private fun fetchSwJsData(): Pair<String?, String?> {
        val request = Request.Builder()
            .url("https://music.youtube.com/sw.js_data")
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("sw.js_data HTTP ${response.code}")
            val jsonText = text.removePrefix(")]}'").trim()
            val root = JSONArray(jsonText)
            val visitors = ArrayList<String>()
            val versions = ArrayList<String>()
            walkJson(root) { value ->
                when {
                    isVisitorData(value) -> visitors += value
                    isMusicClientVersion(value) -> versions += value
                }
            }
            return visitors.firstOrNull() to versions.firstOrNull()
        }
    }

    private fun fetchApiKeyFromHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        http.newCall(request).execute().use { response ->
            val html = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("html HTTP ${response.code}")
            val match = Regex("\"INNERTUBE_API_KEY\"\\s*:\\s*\"([^\"]+)\"").find(html)
                ?: error("INNERTUBE_API_KEY not in HTML")
            val key = match.groupValues[1]
            check(isApiKey(key)) { "bad api key" }
            return key
        }
    }

    private fun walkJson(node: Any?, onString: (String) -> Unit) {
        when (node) {
            is String -> onString(node)
            is JSONArray -> {
                for (i in 0 until node.length()) walkJson(node.opt(i), onString)
            }
            is org.json.JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) walkJson(node.opt(keys.next()), onString)
            }
        }
    }

    private fun isApiKey(value: String) = value.startsWith("AIza") && value.length >= 35
    private fun isVisitorData(value: String) =
        (value.startsWith("Cgt") || value.startsWith("Cgs")) && value.length >= 20
    private fun isMusicClientVersion(value: String) =
        Regex("^1\\.\\d{8}\\.\\d+\\.\\d+$").matches(value)

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
}
