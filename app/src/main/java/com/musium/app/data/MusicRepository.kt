package com.musium.app

import com.musium.innertube.BrowsePage
import com.musium.innertube.HomeSection
import com.musium.innertube.Innertube
import com.musium.innertube.Lyrics
import com.musium.innertube.SearchPage
import com.musium.innertube.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicRepository {
    private val client = Innertube(hl = "en", gl = "US")

    suspend fun search(query: String): SearchPage = withContext(Dispatchers.IO) {
        client.search(query)
    }

    suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        client.searchSuggestions(query)
    }

    suspend fun home(): List<HomeSection> = withContext(Dispatchers.IO) {
        client.home()
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

    suspend fun radio(videoId: String): List<SongItem> = withContext(Dispatchers.IO) {
        client.nextRadio(videoId)
    }

    suspend fun lyrics(videoId: String): Lyrics? = withContext(Dispatchers.IO) {
        client.lyrics(videoId)
    }
}
