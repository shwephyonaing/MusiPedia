package team.ctrlv.musipedia

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsResolverTest {
    @Test
    fun usesSongNameNotArtistFromOfficialVideoTitle() {
        val title = "ချစ်သူ့အိမ် - G Fatt (Official Lyrics Video)"
        assertEquals("ချစ်သူ့အိမ်", LyricsResolver.displayTitle(title, "G Fatt"))
        assertFalse(LyricsResolver.queriesFor("ချစ်သူ့အိမ်", "G Fatt", title).any { it.equals("G Fatt", ignoreCase = true) })
    }

    @Test
    fun rejectsUnrelatedLrclibHit() {
        val moneybagg = JSONObject()
            .put("trackName", "Break On A Bitch")
            .put("artistName", "Moneybagg Yo")
            .put("duration", 163)
        assertFalse(LyricsResolver.matches(moneybagg, "ချစ်သူ့အိမ်", "G Fatt", 360_000))
        val same = JSONObject()
            .put("trackName", "ချစ်သူ့အိမ်")
            .put("artistName", "G Fatt")
            .put("duration", 358)
        assertTrue(LyricsResolver.matches(same, "ချစ်သူ့အိမ်", "G Fatt", 360_000))
    }

    @Test
    fun motherTeresaUsesTitleAfterDashNotArtist() {
        val raw = "Lil Kee Boi ft NAY - Mother Teresa ( official music )"
        assertEquals("Mother Teresa", LyricsResolver.displayTitle(raw, "Lil Kee Boi"))
        assertEquals("Mother Teresa", LyricsResolver.displayTitle(raw, "Lil Kee Boi ft NAY"))
        val timed = JSONObject()
            .put("trackName", "Mother Teresa")
            .put("artistName", "Lil Kee Boi")
        assertTrue(LyricsResolver.matches(timed, "Mother Teresa", "Lil Kee Boi"))
    }

    @Test
    fun collapsesSpacedUnicodeMyanmarNotZawgyi() {
        val broken = "She from Asana, သ ူ ့ ဇ ာ ပ င ် တ င ် ပ ါ န ဖ ျ ာ း ခ ျ"
        val fixed = LyricsResolver.normalizeLyricText(broken)
        assertEquals("She from Asana, သူ့ဇာပင်တင်ပါနဖျားချ", fixed)
        assertFalse(" သ " in fixed)
        assertEquals("love me or not", LyricsResolver.normalizeLyricText("love me or not"))
    }
}
