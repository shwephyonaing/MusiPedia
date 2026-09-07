package team.ctrlv.musipedia.innertube

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

    @Test
    fun searchJapanVsBrazilIsNotAMatchReplay() {
        val page = Innertube().search("japan vs brazil")
        println("japan vs brazil songs=${page.songs.size}")
        page.songs.take(8).forEach { println("SONG ${it.title} | ${it.subtitle}") }
        assertTrue(page.songs.none { it.title.contains("highlight", ignoreCase = true) })
        assertTrue(page.songs.none { it.title.contains("voice over", ignoreCase = true) })
        assertTrue(page.songs.none { Regex("""(?i)japan.+(vs|v).+brazil""").containsMatchIn(it.title) && it.title.contains("goal", ignoreCase = true) })
    }

    @Test
    fun searchOtherSongsStayMusic() {
        val youtube = Innertube()
        listOf("Hello", "Shape of You", "အမေ့အိမ်").forEach { query ->
            val page = youtube.search(query)
            println("$query songs=${page.songs.size}")
            page.songs.take(3).forEach { println("  ${it.title} | ${it.subtitle}") }
            assertTrue("$query should return songs", page.songs.isNotEmpty())
            assertTrue(
                "$query should not return lessons",
                page.songs.none { it.title.contains("What is", ignoreCase = true) },
            )
        }
    }

    @Test
    fun searchParagraphPrefersMusic() {
        val youtube = Innertube()
        val suggestions = youtube.searchSuggestions("Paragraph")
        val page = youtube.search("Paragraph")
        println("paragraph suggestions=$suggestions")
        println("paragraph songs=${page.songs.size} artists=${page.artists.size} albums=${page.albums.size} playlists=${page.playlists.size}")
        page.songs.take(8).forEach { song ->
            println("SONG ${song.id} | ${song.title} | ${song.subtitle}")
        }
        page.artists.take(5).forEach { println("ARTIST ${it.title} | ${it.subtitle}") }
        assertTrue(page.songs.isNotEmpty())
        assertTrue(page.songs.none { it.title.contains("What is", ignoreCase = true) })
        assertTrue(page.songs.any { it.subtitle.orEmpty().contains("Sheeran", ignoreCase = true) || it.title.contains("Paragraph", ignoreCase = true) })
    }
}
