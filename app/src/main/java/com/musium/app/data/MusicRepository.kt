package com.musium.app

import com.musium.innertube.BrowsePage
import com.musium.innertube.HomeSection
import com.musium.innertube.Innertube
import com.musium.innertube.Lyrics
import com.musium.innertube.SearchPage
import com.musium.innertube.SongItem
import com.musium.innertube.hdArtwork
import com.musium.innertube.youtubeThumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
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

    fun clearHomeCache() {
        cachedHome = null
    }

    suspend fun search(query: String): SearchPage = withContext(Dispatchers.IO) {
        client.search(query)
    }

    suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        client.searchSuggestions(query)
    }

    fun homeFeed(recent: List<PlayableSong> = emptyList(), force: Boolean = false): Flow<List<HomeSection>> = flow {
            val hit = cachedHome?.takeIf { !force && it.hasContent() }
            if (hit != null) {
                emit(hit)
                return@flow
            }
            cachedHome = null

            coroutineScope {
            // Apple chart is cheap; resolve YouTube IDs in parallel with For You mixes.
            val chartHits = async {
                runCatching { client.appleMostPlayed(10, chartCountry = "mm") }.getOrDefault(emptyList())
            }
            val seeds = recent.take(5).map { it.toSongItem() }
            val mixJob = async {
                val mixSeeds = seeds.ifEmpty {
                    val first = chartHits.await().firstOrNull() ?: return@async emptyList()
                    listOfNotNull(
                        runCatching {
                            client.findSong("${first.title} ${first.artist}", youtubeOnly = true)
                        }.getOrNull()?.copy(
                            title = first.title,
                            subtitle = first.artist,
                            thumbnail = first.artwork?.hdArtwork() ?: first.artwork,
                        ),
                    )
                }
                buildForYouMix(mixSeeds)
            }

            val trending = resolveChartHits(chartHits.await())
            emit(
                listOfNotNull(
                    HomeSection("For You", emptyList()),
                    trending.takeIf { it.isNotEmpty() }?.let { HomeSection("Trending", it) },
                ),
            )

            val heard = seeds.map { it.id }.toSet()
            val forYou = (mixJob.await().filter { it.id !in heard } + trending)
                .distinctBy { it.id }
                .take(10)
            val sections = listOfNotNull(
                forYou.takeIf { it.isNotEmpty() }?.let { HomeSection("For You", it) },
                trending.takeIf { it.isNotEmpty() }?.let { HomeSection("Trending", it) },
            )
            // Never cache empty home — offline failures would stick forever in-process.
            if (sections.hasContent()) cachedHome = sections
            emit(sections)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun home(recent: List<PlayableSong> = emptyList(), force: Boolean = false): List<HomeSection> {
        var latest = cachedHome.orEmpty()
        homeFeed(recent, force).collect { latest = it }
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

    private suspend fun resolveChartHits(hits: List<com.musium.innertube.ChartTrack>): List<SongItem> {
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

    private suspend fun buildForYouMix(seeds: List<SongItem>): List<SongItem> {
        if (seeds.isEmpty()) return emptyList()
        return coroutineScope {
            interleave(
                seeds.map { seed ->
                    async {
                        runCatching { client.mixAround(seed, youtubeOnly = true) }
                            .getOrDefault(emptyList())
                            .take(8)
                    }
                }.awaitAll(),
            )
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
