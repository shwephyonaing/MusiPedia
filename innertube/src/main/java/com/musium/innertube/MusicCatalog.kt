package com.musium.innertube

internal object MusicCatalog {
    private val nonMusic = Regex(
        """(?i)(\bhighlights?\b|\bextended highlights\b|\bfull match\b|\bwatchalong\b|\bmatchday\b|""" +
            """\bpost[- ]?match\b|\bpress conference\b|\bplay[- ]?by[- ]?play\b|\bvoice[- ]?overs?\b|""" +
            """\bcommentary\b|\ball goals\b|\bgoal compilation\b|\bmatch recap\b|\bsportscenter\b|""" +
            """\bsky sports\b|\btnt sports\b|\bespn\b|\bpremier league\b|\bchampions league\b|""" +
            """\bla liga\b|\bserie a\b|\bbundesliga\b|\bworld cup\b|\bcopa (del rey|america|libertadores)\b|""" +
            """\bfootball match\b|\bsoccer match\b|\bfantasy football\b|\bpredicted (xi|line[- ]?ups?)\b|""" +
            """\bwhat is (a|an|the)\b|\bwhat is ['\"]|\bhow to write\b|\bdefinition of\b|""" +
            """\b(english lesson|learn english|english grammar|english class|vocabulary words?)\b|""" +
            """\bwriting (a |an )?(paragraph|essay|sentence)\b|""" +
            """\bcrash course\b|\bted-ed\b|\bkhan academy\b|\bbbc learning\b)""",
    )
    private val official = Regex(
        """(?i)(official (audio|music|video|mv|lyric)|provided to youtube|\btopic\b|\bvevo\b)""",
    )
    private val unofficial = Regex(
        """(?i)(\bcover\b|\bkaraoke\b|\bspeed ?up\b|\bnightcore\b|\b8d audio\b|\blive session\b)""",
    )
    private val sportsClub = Regex(
        """(?i)\b(arsenal|chelsea|liverpool|manchester (united|city)|man utd|man city|tottenham|spurs|""" +
            """barcelona|real madrid|bayern|juventus|psg|paris saint[- ]germain|ac milan|inter milan|""" +
            """dortmund|newcastle|west ham|aston villa|brighton|everton|crystal palace|leeds|leicester|""" +
            """celtic|rangers|ajax|napoli|as roma|atletico|sevilla|lyon|marseille)\b""",
    )
    private val sportsNation = Regex(
        """(?i)\b(japan|brazil|argentina|france|germany|spain|italy|england|portugal|netherlands|""" +
            """belgium|croatia|mexico|usa|united states|korea|south korea|australia|canada|uruguay|""" +
            """colombia|senegal|morocco|ghana|nigeria|cameroon|egypt|tunisia|iran|saudi arabia|qatar|""" +
            """china|india|thailand|vietnam|indonesia|philippines|sweden|denmark|norway|poland|""" +
            """switzerland|austria|ukraine|turkey|greece|scotland|wales|ireland|chile|peru|ecuador|""" +
            """paraguay|bolivia|venezuela|algeria|ivory coast|cote d'ivoire)\b""",
    )
    private val versus = Regex("""(?i)(\bvs\.?\b|\bv\b|\b\d+\s*[-–]\s*\d+\b)""")
    private val versusPair = Regex("""(?i)\S.+\s+vs\.?\s+\S.+""")
    private val musicIntent = Regex(
        """(?i)(\bsong\b|\bofficial\b|\blyrics?\b|\bremix\b|\bfeat\.?\b|\bft\.?\b|\baudio\b|\bdiss\b|\brap\b|\bmashup\b|\bcover\b|\bvevo\b)""",
    )
    private val junk = Regex("""[^\p{L}\p{N}\s]+""")
    private val parens = Regex("""\([^)]*\)|\[[^\]]*\]""")

    fun refine(page: SearchPage, query: String = ""): SearchPage = page.copy(
        songs = preferOfficial(page.songs, query),
        artists = page.artists.filterNot { isNonMusic(it.title, it.subtitle) },
        albums = page.albums.filterNot { isNonMusic(it.title, it.subtitle) },
        playlists = page.playlists.filterNot { isNonMusic(it.title, it.subtitle) },
    )

    fun hasMusicHits(songs: List<SongItem>, query: String = ""): Boolean {
        if (songs.isEmpty()) return false
        return songs.any { song ->
            song.videoType == "MUSIC_VIDEO_TYPE_ATV" ||
                song.videoType == "MUSIC_VIDEO_TYPE_OMV" ||
                looksOfficial(song.title, song.subtitle) ||
                titleDistance(song.title, query) <= -18
        }
    }

    fun preferOfficial(songs: List<SongItem>, query: String = ""): List<SongItem> {
        return songs.filterNot { isNonMusic(it.title, it.subtitle) }
            .sortedBy { rank(it, query) }
    }

    fun isNonMusic(title: String, subtitle: String? = null): Boolean {
        val hay = haystack(title, subtitle)
        if (nonMusic.containsMatchIn(hay)) return true
        return isSportsVersus(hay)
    }

    fun isNonMusicQuery(query: String): Boolean {
        if (isNonMusic(query) || isSportsVersus(query)) return true
        if (versusPair.containsMatchIn(query) && !musicIntent.containsMatchIn(query)) return true
        return Regex("""(?i)(\bwriting\b|\bstructure\b|\bgrammar\b|\blearn english\b|\bon importance\b|ရေး)""")
            .containsMatchIn(query)
    }

    fun allowYoutubeVideoFallback(query: String): Boolean {
        if (isNonMusicQuery(query)) return false
        return query.any { it.code > 127 } || query.trim().split(Regex("\\s+")).size >= 2
    }

    fun isMusicSearchHint(query: String, suggestion: String): Boolean {
        if (suggestion.equals(query, ignoreCase = true) || isNonMusicQuery(suggestion)) return false
        val extra = suggestion.trim().removePrefix(query.trim()).trim()
            .ifEmpty { suggestion.lowercase().removePrefix(query.lowercase()).trim() }
        if (extra.isEmpty()) return false
        return !Regex("""(?i)(\bwriting\b|\bstructure\b|\bgrammar\b|\blearn english\b|\breading\b|\bhow to\b|ရေး)""")
            .containsMatchIn(extra)
    }

    private fun isSportsVersus(text: String): Boolean {
        if (!versus.containsMatchIn(text)) return false
        if (musicIntent.containsMatchIn(text)) return false
        return sportsClub.containsMatchIn(text) || sportsNation.containsMatchIn(text)
    }

    fun looksOfficial(title: String, subtitle: String? = null): Boolean {
        val hay = haystack(title, subtitle)
        return official.containsMatchIn(hay) || hay.contains(" - Topic", ignoreCase = true)
    }

    private fun rank(song: SongItem, query: String): Int {
        val hay = haystack(song.title, song.subtitle)
        var score = 40
        when (song.videoType) {
            "MUSIC_VIDEO_TYPE_ATV" -> score -= 30
            "MUSIC_VIDEO_TYPE_OMV" -> score -= 20
        }
        if (looksOfficial(song.title, song.subtitle)) score -= 15
        if (hay.contains("official", ignoreCase = true)) score -= 8
        if (unofficial.containsMatchIn(hay)) score += 20
        score += titleDistance(song.title, query)
        return score
    }

    private fun titleDistance(title: String, query: String): Int {
        val needle = normalize(query)
        val hay = normalize(title)
        if (needle.isEmpty() || hay.isEmpty()) return 0
        if (hay == needle) return -40
        if (hay.startsWith("$needle ") || needle.startsWith("$hay ")) return -28
        if (hay.split(' ').any { it == needle }) return -18
        return 0
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace(parens, " ")
            .replace(junk, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun haystack(title: String, subtitle: String?): String {
        return "$title ${subtitle.orEmpty()}"
    }
}
