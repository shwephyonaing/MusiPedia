package com.musium.innertube

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSearchTest {
    @Test
    fun searchSuggestionsForAme() {
        val suggestions = Innertube().searchSuggestions("အမေ့")
        println("suggestions=${suggestions.size} $suggestions")
        assertTrue("Suggestions should appear for အမေ့", suggestions.isNotEmpty())
    }

    @Test
    fun searchAmeAinAndResolveAudio() {
        val youtube = Innertube()
        val page = youtube.search("အမေ့အိမ်")
        println("songs=${page.songs.size} artists=${page.artists.size} albums=${page.albums.size} playlists=${page.playlists.size}")
        page.songs.take(8).forEach { song ->
            println("SONG ${song.id} | ${song.title} | ${song.subtitle}")
        }
        assertTrue("Songs tab should not be empty for အမေ့အိမ်", page.songs.isNotEmpty())

        val first = page.songs.first()
        val streams = youtube.playerStreams(first.id)
        println("streams=${streams.size}")
        streams.take(12).forEach { stream ->
            println("STREAM client=${stream.client} kind=${stream.kind} itag=${stream.itag}")
        }
        val chosen = youtube.playerAudio(first.id)
        println("chosen client=${chosen?.client} kind=${chosen?.kind} itag=${chosen?.itag}")
        assertTrue("player should return a stream for ${first.id}", chosen != null && chosen.url.isNotBlank())

        val http = OkHttpClient()
        fun probe(start: Long): Int {
            val request = Request.Builder()
                .url(chosen!!.url)
                .header("User-Agent", chosen.userAgent)
                .header("Range", "bytes=$start-${start + 1023}")
                .build()
            return http.newCall(request).execute().use { it.code }
        }
        val startCode = probe(0)
        val midCode = probe(2L * 1024 * 1024)
        println("probe start=$startCode mid2mb=$midCode kind=${chosen!!.kind}")
        assertTrue("start range should work, was $startCode", startCode == 200 || startCode == 206)
    }
}
