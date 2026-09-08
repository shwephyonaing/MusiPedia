package team.ctrlv.musipedia

import team.ctrlv.musipedia.innertube.ArtistItem
import team.ctrlv.musipedia.innertube.BrowsePage
import team.ctrlv.musipedia.innertube.HomeSection
import team.ctrlv.musipedia.innertube.Innertube
import team.ctrlv.musipedia.innertube.Lyrics
import team.ctrlv.musipedia.innertube.SearchPage
import team.ctrlv.musipedia.innertube.SongItem
import team.ctrlv.musipedia.innertube.hdArtwork
import team.ctrlv.musipedia.innertube.hdProfileArtwork
import team.ctrlv.musipedia.innertube.youtubeThumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Locale

object MusicRepository {
    private val client = Innertube(
        hl = Locale.getDefault().language.ifBlank { "en" },
        gl = Locale.getDefault().country.ifBlank { "US" },
    )
    @Volatile private var cachedHome: List<HomeSection>? = null
    /** False while For You / fav rails are still resolving after a partial Trending emit. */
    @Volatile private var homeFullyLoaded: Boolean = false

    fun clearHomeCache() {
        cachedHome = null
        homeFullyLoaded = false
    }

    suspend fun search(query: String): SearchPage = withContext(Dispatchers.IO) {
        preferVerified(client.search(query), query)
    }

    /**
     * Surface MusiPedia verified / official artists and their songs first in search.
     */
    fun preferVerified(page: SearchPage, query: String = ""): SearchPage {
        val catalog = verifiedCatalog()
        if (catalog.isEmpty()) return page
        val verifiedIds = catalog.map { it.id }.toHashSet()
        val matched = catalogMatching(catalog, query)
            .map {
                ArtistItem(
                    id = it.id,
                    title = it.name,
                    subtitle = "Verified artist",
                    thumbnail = it.thumbnailUrl,
                )
            }
        val existingIds = page.artists.map { it.id }.toHashSet()
        val extras = matched.filter { it.id !in existingIds }
        val artists = (extras + page.artists).distinctBy { it.id }
            .sortedBy { artist -> if (artist.id in verifiedIds) 0 else 1 }
        val songs = page.songs.sortedBy { song ->
            when {
                song.artistId != null && song.artistId in verifiedIds -> 0
                else -> 1
            }
        }
        return page.copy(artists = artists, songs = songs)
    }

    private fun verifiedCatalog(): List<TasteArtist> =
        runCatching {
            // Set from MusiumApplication; empty until then.
            verifiedCatalogProvider?.invoke().orEmpty()
        }.getOrDefault(emptyList())

    private fun catalogMatching(catalog: List<TasteArtist>, query: String): List<TasteArtist> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return catalog.filter { artist ->
            val name = artist.name.lowercase()
            name.contains(q) || q.contains(name)
        }
    }

    @Volatile
    private var verifiedCatalogProvider: (() -> List<TasteArtist>)? = null

    fun bindVerifiedCatalog(provider: () -> List<TasteArtist>) {
        verifiedCatalogProvider = provider
    }

    suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        val remote = client.searchSuggestions(query)
        val verifiedNames = catalogMatching(verifiedCatalog(), query).map { it.name }
        (verifiedNames + remote).distinctBy { it.lowercase() }
    }

    fun homeFeed(
        recent: List<PlayableSong> = emptyList(),
        tasteArtists: List<TasteArtist> = emptyList(),
        force: Boolean = false,
    ): Flow<List<HomeSection>> = flow {
            val hit = cachedHome?.takeIf { !force && it.hasContent() }
            if (hit != null) {
                emit(hit)
                // Partial cache (Trending only) still needs For You / fav rails.
                if (homeFullyLoaded) return@flow
            }
            if (force) {
                cachedHome = null
                homeFullyLoaded = false
            }

            coroutineScope {
            val chartHits = async {
                runCatching { client.appleMostPlayed(10, chartCountry = "mm") }.getOrDefault(emptyList())
            }
            val seeds = recent.take(5).map { it.toSongItem() }
            val firstTime = seeds.isEmpty()
            // One shared artist fetch for both rails — skip when there are no tastes.
            val favArtistsJob = async {
                if (tasteArtists.isEmpty()) emptyList() else songsFromFavArtists(tasteArtists)
            }
            // Returning: up to 2 radio mixes from recent plays (latest + one more).
            val radioJob = async {
                if (firstTime) return@async emptyList()
                val radioSeeds = listOfNotNull(seeds.getOrNull(0), seeds.getOrNull(1)).distinctBy { it.id }
                if (radioSeeds.isEmpty()) return@async emptyList()
                coroutineScope {
                    radioSeeds.map { seed ->
                        async {
                            runCatching { client.mixAround(seed, youtubeOnly = true) }
                                .getOrDefault(emptyList())
                                .take(6)
                        }
                    }.awaitAll().flatten().distinctBy { it.id }
                }
            }

            val trending = resolveChartHits(chartHits.await().take(6))
            val partial = listOfNotNull(
                HomeSection("For You", emptyList()),
                trending.takeIf { it.isNotEmpty() }?.let { HomeSection("Trending", it) },
            )
            if (partial.hasContent()) {
                cachedHome = partial
                homeFullyLoaded = false
            }
            emit(partial)

            val fromFav = favArtistsJob.await()
            val forYou = if (firstTime) {
                // First launch after personalize: seed For You from fav-artist popular hits.
                fromFav
            } else {
                val heard = seeds.map { it.id }.toSet()
                val radio = radioJob.await().filter { it.id !in heard }
                // Last plays → radio, then fav artists (reuse), trending as last-resort filler.
                (radio + fromFav + trending)
                    .distinctBy { it.id }
                    .filter { it.id !in heard }
                    .take(10)
            }
            val sections = listOfNotNull(
                forYou.takeIf { it.isNotEmpty() }?.let { HomeSection("For You", it) },
                trending.takeIf { it.isNotEmpty() }?.let { HomeSection("Trending", it) },
            )
            // Never cache empty home — offline failures would stick forever in-process.
            if (sections.hasContent()) {
                cachedHome = sections
                homeFullyLoaded = true
            }
            emit(sections)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Warm home and return as soon as any section has items (usually Trending).
     * Cancelling after this leaves a partial cache; Home continues For You via homeFeed.
     */
    suspend fun homeReady(
        recent: List<PlayableSong> = emptyList(),
        tasteArtists: List<TasteArtist> = emptyList(),
        force: Boolean = false,
    ): List<HomeSection> {
        cachedHome?.takeIf { !force && it.hasContent() }?.let { return it }
        return homeFeed(recent, tasteArtists, force).first { it.hasContent() }
    }

    suspend fun home(
        recent: List<PlayableSong> = emptyList(),
        tasteArtists: List<TasteArtist> = emptyList(),
        force: Boolean = false,
    ): List<HomeSection> {
        var latest = cachedHome.orEmpty()
        homeFeed(recent, tasteArtists, force).collect { latest = it }
        return latest
    }

    suspend fun artist(browseId: String): BrowsePage = withContext(Dispatchers.IO) {
        client.artist(browseId)
    }

    suspend fun album(browseId: String): BrowsePage = withContext(Dispatchers.IO) {
        client.album(browseId)
    }

    suspend fun playlist(playlistId: String): BrowsePage = withContext(Dispatchers.IO) {
        client.playlist(playlistId)
    }

    suspend fun lyrics(videoId: String): Lyrics? = withContext(Dispatchers.IO) {
        client.lyrics(videoId)
    }

    suspend fun related(videoId: String): List<PlayableSong> = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || videoId.startsWith("local:")) return@withContext emptyList()
        client.relatedSongs(videoId, youtubeOnly = true)
            .map { it.toPlayable() }
            .filter { it.id != videoId }
    }

    /** Resolve the real YouTube uploader channel for a playing video (not a name search). */
    suspend fun videoUploader(videoId: String): team.ctrlv.musipedia.innertube.VideoUploader? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank() || videoId.startsWith("local:")) return@withContext null
            client.videoUploader(videoId)
        }

    /** Resolve a high-res creator photo for profile UI. Prefer the channel itself when we have UC… */
    suspend fun artistAvatar(artistId: String?, artistName: String): String? = withContext(Dispatchers.IO) {
        val channelId = artistId?.takeIf { it.startsWith("UC") }
        if (channelId != null) {
            val fromBrowse = runCatching { client.artist(channelId).thumbnail }.getOrNull()
            if (!fromBrowse.isNullOrBlank()) return@withContext fromBrowse.hdProfileArtwork()
        }
        val name = artistName.trim()
        val fromSearch = if (name.isNotBlank()) {
            runCatching { client.search(name).artists.firstOrNull()?.thumbnail }.getOrNull()
        } else {
            null
        }
        if (!fromSearch.isNullOrBlank()) return@withContext fromSearch.hdProfileArtwork()

        val fromBrowse = artistId?.takeIf { it.isNotBlank() && !it.startsWith("UC") }?.let { id ->
            runCatching { client.artist(id).thumbnail }.getOrNull()
        }
        if (!fromBrowse.isNullOrBlank()) return@withContext fromBrowse.hdProfileArtwork()
        null
    }

    private suspend fun resolveChartHits(hits: List<team.ctrlv.musipedia.innertube.ChartTrack>): List<SongItem> {
        if (hits.isEmpty()) {
            return runCatching { client.ytmCharts() }.getOrDefault(emptyList()).take(10)
        }
        return coroutineScope {
            hits.map { hit ->
                async {
                    runCatching { client.findSong("${hit.title} ${hit.artist}", youtubeOnly = true) }
                        .getOrNull()
                        ?.let { song ->
                            song.copy(
                                title = hit.title,
                                subtitle = hit.artist,
                                thumbnail = hit.artwork?.hdArtwork()
                                    ?: song.thumbnail?.hdArtwork()
                                    ?: youtubeThumb(song.id),
                            )
                        }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * Per artist: YouTube **Popular videos** (by view count) — 2 biggest hits + 1 newer
     * among the popular set. Falls back to YTM artist songs / search if needed.
     */
    private suspend fun songsFromFavArtists(artists: List<TasteArtist>): List<SongItem> {
        if (artists.isEmpty()) return emptyList()
        return coroutineScope {
            interleave(
                artists.take(5).map { artist ->
                    async { picksForArtist(artist) }
                }.awaitAll(),
            ).take(12)
        }
    }

    private suspend fun picksForArtist(artist: TasteArtist): List<SongItem> {
        val popular = runCatching { client.channelPopularVideos(artist.id, limit = 24) }
            .getOrDefault(emptyList())
            .filter(::isTasteTrack)

        val pool = when {
            popular.size >= 3 -> popular
            else -> {
                val pageSongs = runCatching { client.artist(artist.id).songs }
                    .getOrDefault(emptyList())
                    .filter(::isTasteTrack)
                val search = if (pageSongs.size + popular.size < 3) {
                    runCatching { client.search(artist.name).songs }.getOrDefault(emptyList())
                        .filter(::isTasteTrack)
                } else {
                    emptyList()
                }
                (popular + pageSongs + search).distinctBy { it.id }
            }
        }
        if (pool.isEmpty()) return emptyList()

        // Already view-sorted from channelPopularVideos; keep that order as hit rank.
        val byViews = pool
        val picked = LinkedHashMap<String, SongItem>()
        // 2 most-viewed hits
        byViews.forEach { song ->
            if (picked.size >= 2) return@forEach
            picked.putIfAbsent(song.id, song)
        }
        // 1 newer among the popular set (prefer months/weeks over years)
        val newer = byViews
            .asSequence()
            .filter { it.id !in picked }
            .take(12)
            .minByOrNull { tasteAgeScore(it.subtitle) }
        newer?.let { picked[it.id] = it }
        byViews.forEach { song ->
            if (picked.size >= 3) return@forEach
            picked.putIfAbsent(song.id, song)
        }

        return picked.values.map {
            it.copy(
                artistId = it.artistId ?: artist.id,
                subtitle = artist.name,
            )
        }
    }

    /** Reject drama/episode / album-dump shelves; keep real music titles. */
    private fun isTasteTrack(song: SongItem): Boolean {
        val title = song.title
        if (EpisodeJunk.containsMatchIn(title)) return false
        if (NonMusicExtra.containsMatchIn(title)) return false
        if (AlbumDump.containsMatchIn(title)) return false
        return true
    }

    private fun interleave(lists: List<List<SongItem>>): List<SongItem> {
        val out = LinkedHashMap<String, SongItem>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (index in 0 until longest) {
            lists.forEach { list ->
                list.getOrNull(index)?.let { out.putIfAbsent(it.id, it) }
            }
        }
        return out.values.toList()
    }
}

private val EpisodeJunk = Regex(
    """(?i)(\bepisode\b|\bep\.?\s*\d+\b|\bep\s*\d+\b|\bpart\s*\d+\b|\bseason\s*\d+\b|""" +
        """\btrailer\b|\bteaser\b|\breaction\b|\binterview\b|\bpodcast\b|\bvlog\b)""",
)
private val NonMusicExtra = Regex("""(?i)(\bbehind the scenes\b|\bmaking of\b|\bpress conference\b|\bmemo\b|\bshouting out\b)""")
private val AlbumDump = Regex("""(?i)(album compilation|full album|\bplaylist\b)""")

private fun tasteAgeScore(subtitle: String?): Int {
    if (subtitle.isNullOrBlank()) return 500_000
    val m = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)""", RegexOption.IGNORE_CASE)
        .find(subtitle) ?: return 500_000
    val n = m.groupValues[1].toIntOrNull() ?: return 500_000
    val unit = when (m.groupValues[2].lowercase()) {
        "second" -> 1
        "minute" -> 60
        "hour" -> 3_600
        "day" -> 86_400
        "week" -> 604_800
        "month" -> 2_592_000
        "year" -> 31_536_000
        else -> 86_400
    }
    return n * unit
}

private fun List<HomeSection>.hasContent(): Boolean = any { it.items.isNotEmpty() }

private fun PlayableSong.toSongItem() = SongItem(
    id = id,
    title = title,
    subtitle = artist,
    thumbnail = thumbnailUrl,
    playlistId = playlistId,
    artistId = artistId,
    albumId = albumId,
)
