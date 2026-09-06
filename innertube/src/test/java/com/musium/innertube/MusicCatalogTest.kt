package com.musium.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicCatalogTest {
    @Test
    fun dropsFootballHighlightsAndVoiceOvers() {
        assertTrue(MusicCatalog.isNonMusic("Chelsea vs Arsenal | Extended Highlights | Voice Over"))
        assertTrue(MusicCatalog.isNonMusic("Chelsea 2-1 Arsenal", "Sky Sports full match commentary"))
        assertTrue(MusicCatalog.isNonMusicQuery("chelsea vs arsenal highlights"))
        assertTrue(MusicCatalog.isNonMusicQuery("Chelsea vs Arsenal"))
        assertTrue(MusicCatalog.isNonMusicQuery("japan vs brazil"))
        assertTrue(MusicCatalog.isNonMusic("Japan vs Brazil 2-1", "FIFA World Cup"))
        assertFalse(MusicCatalog.allowYoutubeVideoFallback("japan vs brazil"))
        assertFalse(MusicCatalog.isNonMusicQuery("drake vs kendrick diss"))
        assertFalse(MusicCatalog.isNonMusic("အမေ့အိမ်", "Htoo Ein Thin"))
        assertFalse(MusicCatalog.isNonMusic("Anti-Hero", "Taylor Swift · Official Audio"))
    }

    @Test
    fun prefersOfficialAudioOverCovers() {
        val ranked = MusicCatalog.preferOfficial(
            listOf(
                SongItem("1", "Anti-Hero (cover)", "Some Channel"),
                SongItem("2", "Anti-Hero", "Taylor Swift - Topic", videoType = "MUSIC_VIDEO_TYPE_ATV"),
                SongItem("3", "Chelsea vs Arsenal Highlights Voice Over", "WatchAlong"),
            ),
            "Anti-Hero",
        )
        assertEquals(listOf("2", "1"), ranked.map { it.id })
        assertTrue(ranked.none { it.title.contains("Highlights", ignoreCase = true) })
    }

    @Test
    fun dropsLessonVideosAndPrefersExactSongTitle() {
        assertTrue(MusicCatalog.isNonMusic("What is 'Paragraph'", "Learn English"))
        assertTrue(MusicCatalog.isNonMusic("What is a paragraph?", "English grammar"))
        assertFalse(MusicCatalog.isNonMusic("Paragraph", "Ed Sheeran"))
        assertFalse(MusicCatalog.allowYoutubeVideoFallback("Paragraph"))
        val ranked = MusicCatalog.preferOfficial(
            listOf(
                SongItem("lesson", "What is Love", "Haddaway"),
                SongItem("song", "Paragraph", "Ed Sheeran · Official Audio", videoType = "MUSIC_VIDEO_TYPE_ATV"),
                SongItem("class", "What is 'Paragraph'", "English Lessons"),
            ),
            "Paragraph",
        )
        assertEquals("song", ranked.first().id)
        assertTrue(ranked.none { it.title.contains("'Paragraph'") })
        assertTrue(MusicCatalog.isMusicSearchHint("Paragraph", "paragraph ed sheeran"))
        assertFalse(MusicCatalog.isMusicSearchHint("Paragraph", "paragraph writing in english"))
        assertFalse(MusicCatalog.isNonMusic("Despacito (English Version)", "Luis Fonsi"))
        assertFalse(MusicCatalog.isNonMusic("Lullaby for kids", "Nursery Rhymes"))
        assertTrue(
            MusicCatalog.hasMusicHits(
                listOf(SongItem("1", "Hello", "Adele · Official Audio", videoType = "MUSIC_VIDEO_TYPE_ATV")),
                "Hello",
            ),
        )
    }
}
