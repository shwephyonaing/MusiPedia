package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musium.innertube.BrowsePage
import com.musium.innertube.SongItem
import com.musium.innertube.YtItem

private val Cyan = Color(0xFF00C2CB)
private val Black = Color(0xFF0B0B0B)

private sealed interface BrowseUi {
    data object Loading : BrowseUi
    data class Ready(val page: BrowsePage) : BrowseUi
    data class Error(val message: String) : BrowseUi
}

@Composable
internal fun ArtistScreen(browseId: String, name: String, onBack: () -> Unit) {
    BrowseScaffold(pageId = browseId, titleHint = name, onBack = onBack) { MusicRepository.artist(browseId) }
}

@Composable
internal fun AlbumScreen(browseId: String, name: String, onBack: () -> Unit) {
    BrowseScaffold(pageId = browseId, titleHint = name, onBack = onBack) { MusicRepository.album(browseId) }
}

@Composable
internal fun PlaylistScreen(playlistId: String, name: String, onBack: () -> Unit) {
    BrowseScaffold(pageId = playlistId, titleHint = name, onBack = onBack) { MusicRepository.playlist(playlistId) }
}

@Composable
private fun BrowseScaffold(pageId: String, titleHint: String, onBack: () -> Unit, loader: suspend () -> BrowsePage) {
    val player = LocalPlayerConnection.current
    val router = LocalMusicRouter.current
    var state by remember { mutableStateOf<BrowseUi>(BrowseUi.Loading) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(pageId, reload) {
        state = BrowseUi.Loading
        state = runCatching { loader() }.fold(
            onSuccess = { BrowseUi.Ready(it) },
            onFailure = { BrowseUi.Error(it.message ?: "Unable to open this page") },
        )
    }
    Box(Modifier.fillMaxSize().background(Black)) {
        when (val current = state) {
            BrowseUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Cyan)
            is BrowseUi.Error -> Column(Modifier.padding(28.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White) }
                Text(titleHint, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { reload += 1 }) { Text("Retry", color = Cyan, fontWeight = FontWeight.Bold) }
            }
            is BrowseUi.Ready -> {
                val page = current.page
                val queue = page.songs.map { it.toPlayable() }
                LazyColumn(contentPadding = PaddingValues(bottom = 170.dp)) {
                    item {
                        Row(Modifier.padding(8.dp, 40.dp, 8.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White) }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                page.thumbnail,
                                page.title,
                                Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray),
                                contentScale = ContentScale.Crop,
                            )
                            Text(page.title, Modifier.padding(top = 16.dp), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            page.subtitle?.let { Text(it, Modifier.padding(top = 6.dp), color = Color(0xFF8A9A9D), fontSize = 14.sp) }
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { if (queue.isNotEmpty()) player?.play(queue, 0) },
                                    enabled = queue.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black),
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, null)
                                    Text("Play", Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val seed = queue.firstOrNull() ?: page.radioVideoId?.let { PlayableSong(it, page.title, page.subtitle ?: "") }
                                        seed?.let { player?.playRadio(it) }
                                    },
                                    enabled = queue.isNotEmpty() || page.radioVideoId != null,
                                ) {
                                    Icon(Icons.Outlined.Radio, null, tint = Cyan)
                                    Text("Radio", Modifier.padding(start = 6.dp), color = Cyan, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (page.songs.isNotEmpty()) {
                        item { SectionTitle("Songs", Modifier.padding(top = 24.dp)) }
                        items(page.songs, key = { it.id }) { song ->
                            MusicItemRow(song) { song.open(player, router, queue) }
                        }
                    }
                    page.sections.forEach { section ->
                        item { SectionTitle(section.title, Modifier.padding(top = 18.dp)) }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 26.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(section.items, key = { it::class.simpleName + it.id }) { item ->
                                    BrowseTile(item) {
                                        item.open(player, router, section.items.filterIsInstance<SongItem>().map { it.toPlayable() })
                                    }
                                }
                            }
                        }
                    }
                    item { Box(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BrowseTile(item: YtItem, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        AsyncImage(
            item.thumbnail,
            item.title,
            Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray),
            contentScale = ContentScale.Crop,
        )
        Text(item.title, Modifier.padding(top = 8.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        item.subtitle?.let { Text(it, color = Color(0xFF8A9A9D), fontSize = 11.sp, maxLines = 1) }
    }
}
