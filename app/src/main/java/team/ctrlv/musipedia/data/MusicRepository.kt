package team.ctrlv.musipedia

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
        client.search(query)
    }

    suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        client.searchSuggestions(query)
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
            // Returning users only: one radio call from the latest play (not 5 mixes + taste seeds).
            val radioJob = async {
                if (firstTime) return@async emptyList()
                val seed = seeds.firstOrNull() ?: return@async emptyList()
                runCatching { client.mixAround(seed, youtubeOnly = true) }
                    .getOrDefault(emptyList())
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
                // First launch after personalize: same shelf as From Your Fav Artists (zero extra calls).
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
                fromFav.takeIf { it.isNotEmpty() }?.let { HomeSection("From Your Fav Artists", it) },
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

    private suspend fun songsFromFavArtists(artists: List<TasteArtist>): List<SongItem> {
        if (artists.isEmpty()) return emptyList()
        return coroutineScope {
            interleave(
                // Cap artists to keep home light; browse only (search fallback only if empty).
                artists.take(5).map { artist ->
                    async {
                        val pageSongs = runCatching { client.artist(artist.id).songs }.getOrDefault(emptyList())
                        val songs = pageSongs.ifEmpty {
                            runCatching {
                                client.search(artist.name).songs
                            }.getOrDefault(emptyList())
                        }
                        songs.take(3).map {
                            it.copy(
                                artistId = it.artistId ?: artist.id,
                                subtitle = it.subtitle ?: artist.name,
                            )
                        }
                    }
                }.awaitAll(),
            ).take(12)
        }
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
