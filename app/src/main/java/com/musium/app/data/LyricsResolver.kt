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

object LyricsResolver {
    private val lrcLine = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?\](.*)$""")
    private val titleJunk = Regex(
        """(?i)\s*[\(\[][^\)\]]*(official|lyrics?|audio|visualizer|\bmv\b|video|hd|4k)[^\)\]]*[\)\]]""",
    )

    suspend fun load(song: PlayableSong): Lyrics? = withContext(Dispatchers.IO) {
        coroutineScope {
            val remote = async {
                if (song.isLocal) null
                else runCatching { MusicRepository.lyrics(song.id) }.getOrNull()?.takeUnless { it.isEmpty }
            }
            val library = async { runCatching { fromLrclib(song) }.getOrNull()?.takeUnless { it.isEmpty } }
            pick(library.await(), remote.await())
        }
    }

    private fun pick(library: Lyrics?, remote: Lyrics?): Lyrics? {
        return listOf(library, remote).firstOrNull { it?.synced == true } ?: library ?: remote
    }

    private fun fromLrclib(song: PlayableSong): Lyrics? {
        val title = cleanTitle(song.title)
        val track = title.substringAfterLast(" - ").trim().ifBlank { title }
        val artist = song.artist.split("•", "·", "|").first().trim()
        val found = mutableListOf<Lyrics>()
        lyricsFromGet(track, artist)?.let { if (it.synced) return it else found += it }
        listOf("$artist $track", track, "$track $artist", title).distinct().forEach { query ->
            val results = searchLrclib(query) ?: return@forEach
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val lyrics = parseLrclibObject(item) ?: continue
                if (lyrics.synced) return lyrics
                found += lyrics
            }
        }
        return found.firstOrNull()
    }

    private fun lyricsFromGet(track: String, artist: String): Lyrics? = parseLrclibObject(
        request(
            "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track)
                .addQueryParameter("artist_name", artist)
                .build()
                .toString(),
        )?.let { runCatching { JSONObject(it) }.getOrNull() },
    )

    private fun searchLrclib(query: String): JSONArray? {
        val body = request(
            "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
                .toString(),
        ) ?: return null
        return runCatching { JSONArray(body) }.getOrNull()
    }

    private fun parseLrclibObject(json: JSONObject?): Lyrics? {
        if (json == null) return null
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

    private fun stampMs(minutes: String, seconds: String, fraction: String): Long {
        val millis = when {
            fraction.isEmpty() -> 0L
            fraction.length <= 2 -> fraction.padEnd(2, '0').toLong() * 10
            else -> fraction.take(3).padEnd(3, '0').toLong()
        }
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + millis
    }

    private fun cleanTitle(title: String): String = title.replace(titleJunk, "").trim().ifBlank { title }

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
