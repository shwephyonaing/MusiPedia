package com.musium.app

import com.musium.innertube.LyricLine
import com.musium.innertube.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

object LyricsResolver {
    private val lrcLine = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?\](.*)$""")
    private val titleJunk = Regex(
        """(?i)\s*[\(\[][^\)\]]*(official|lyrics?|audio|visualizer|\bmv\b|video|hd|4k|music)[^\)\]]*[\)\]]""",
    )
    private val trailingJunk = Regex(
        """(?i)\s*[-–|]\s*(official\s+)?(lyrics?\s+)?(audio|video|visualizer|mv).*$""",
    )
    private val junkChars = Regex("""[^\p{L}\p{N}\s]+""")

    suspend fun load(song: PlayableSong, durationMs: Long = 0L): Lyrics? = withContext(Dispatchers.IO) {
        coroutineScope {
            val remote = async {
                if (song.isLocal) null
                else runCatching { MusicRepository.lyrics(song.id) }.getOrNull()?.takeUnless { it.isEmpty }
            }
            val library = async { runCatching { fromLrclib(song, durationMs) }.getOrNull()?.takeUnless { it.isEmpty } }
            pick(library.await(), remote.await())
        }
    }

    private fun pick(library: Lyrics?, remote: Lyrics?): Lyrics? {
        return listOf(library, remote).firstOrNull { it?.synced == true } ?: remote ?: library
    }

    private fun fromLrclib(song: PlayableSong, durationMs: Long): Lyrics? {
        val artist = cleanArtist(song.artist)
        val track = displayTitle(song.title, artist)
        val found = mutableListOf<Pair<JSONObject, Lyrics>>()
        lyricsFromGet(track, artist)?.let { (json, lyrics) -> found += json to lyrics }
        queriesFor(track, artist, song.title).forEach { query ->
            val results = searchLrclib(query) ?: return@forEach
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val lyrics = parseLrclibObject(item, track, artist) ?: continue
                found += item to lyrics
            }
        }
        val unique = found.distinctBy { it.second.lines.joinToString { line -> line.text } }
        val synced = unique.filter { it.second.synced }
        val pool = synced.ifEmpty { unique }
        return pool.minByOrNull { (json, lyrics) ->
            val remoteSec = json.optDouble("duration", 0.0)
            if (durationMs >= 30_000L && remoteSec >= 20.0) abs(durationMs / 1000.0 - remoteSec)
            else if (lyrics.synced) 0.0 else 1.0
        }?.second
    }

    private fun lyricsFromGet(track: String, artist: String): Pair<JSONObject, Lyrics>? {
        val json = request(
            "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track)
                .addQueryParameter("artist_name", artist)
                .build()
                .toString(),
        )?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val lyrics = parseLrclibObject(json, track, artist) ?: return null
        return json to lyrics
    }

    private fun searchLrclib(query: String): JSONArray? {
        val body = request(
            "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
                .toString(),
        ) ?: return null
        return runCatching { JSONArray(body) }.getOrNull()
    }

    private fun parseLrclibObject(
        json: JSONObject?,
        track: String,
        artist: String,
    ): Lyrics? {
        if (json == null || !matches(json, track, artist)) return null
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
        if (synced != null) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return Lyrics(lines, synced = true)
        }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() } ?: return null
        val lines = plain.lines().map { it.trim() }.filter { it.isNotBlank() }.map { LyricLine(0, it) }
        return lines.takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = false) }
    }

    internal fun parseLrc(lrc: String): List<LyricLine> = buildList {
        lrc.lineSequence().forEach { raw ->
            var rest = raw.trim()
            val stamps = mutableListOf<Long>()
            while (true) {
                val match = lrcLine.find(rest) ?: break
                stamps += stampMs(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                rest = match.groupValues[4].trim()
                if (!rest.startsWith("[")) break
            }
            val text = rest.replace(Regex("""<\d+:\d+(?:[.:]\d+)?>"""), "").trim()
            if (text.isBlank()) return@forEach
            stamps.forEach { add(LyricLine(it, text)) }
        }
    }.sortedBy { it.timeMs }

    internal fun displayTitle(raw: String, artist: String): String {
        val title = raw.replace(titleJunk, "").replace(trailingJunk, "").trim().ifBlank { raw }
        if (!title.contains(" - ")) return title
        val after = title.substringAfterLast(" - ").trim()
        val before = title.substringBeforeLast(" - ").trim()
        val artistName = cleanArtist(artist)
        if (after.equals(artistName, ignoreCase = true) || similar(after, artistName)) return before
        if (before.equals(artistName, ignoreCase = true) ||
            before.startsWith(artistName, ignoreCase = true)
        ) {
            return after
        }
        return after
    }

    internal fun matches(json: JSONObject, track: String, artist: String, durationMs: Long = 0L): Boolean {
        val gotTitle = json.optString("trackName")
        val gotArtist = cleanArtist(json.optString("artistName"))
        if (!similar(gotTitle, track)) return false
        if (similar(gotArtist, artist)) return true
        return normalize(gotTitle) == normalize(track) && !clearlyDifferentArtist(gotArtist, artist)
    }

    internal fun cleanArtist(artist: String): String {
        return artist.split("•", "·", "|").first().trim()
            .replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""(?i)\s*vevo$"""), "")
            .trim()
    }

    internal fun queriesFor(track: String, artist: String, rawTitle: String): List<String> {
        return listOf("$artist $track", track, "$track $artist", rawTitle)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(artist, ignoreCase = true) && it.length >= 2 }
            .distinct()
    }

    private fun clearlyDifferentArtist(left: String, right: String): Boolean {
        val ta = normalize(left).split(' ').filter { it.length > 1 }.toSet()
        val tb = normalize(right).split(' ').filter { it.length > 1 }.toSet()
        return ta.isNotEmpty() && tb.isNotEmpty() && ta.intersect(tb).isEmpty()
    }

    private fun similar(left: String, right: String): Boolean {
        val a = normalize(left)
        val b = normalize(right)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b || a.contains(b) || b.contains(a)) return true
        val ta = a.split(' ').filter { it.length > 1 }.toSet()
        val tb = b.split(' ').filter { it.length > 1 }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return false
        return ta.intersect(tb).size * 2 >= minOf(ta.size, tb.size)
    }

    private fun normalize(value: String): String {
        return value.lowercase().replace(junkChars, " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun stampMs(minutes: String, seconds: String, fraction: String): Long {
        val millis = when {
            fraction.isEmpty() -> 0L
            fraction.length <= 2 -> fraction.padEnd(2, '0').toLong() * 10
            else -> fraction.take(3).padEnd(3, '0').toLong()
        }
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + millis
    }

    private fun request(url: String): String? {
        repeat(3) { attempt ->
            val text = runCatching {
                val call = Request.Builder().url(url).header("User-Agent", "MusiPedia").build()
                StreamResolver.http.newCall(call).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) body.takeIf { it.isNotBlank() } else null
                }
            }.getOrNull()
            if (text != null) return text
            if (attempt < 2) Thread.sleep(400)
        }
        return null
    }
}
