package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musium.innertube.HomeSection
import com.musium.innertube.SongItem
import com.musium.innertube.YtItem

private val Cyan = Color(0xFF00C2CB)
private val PageBlack = Color(0xFF0B0B0B)
private val Tile = Color(0xFF172023)

private sealed interface HomeUi {
    data object Loading : HomeUi
    data class Ready(val sections: List<HomeSection>) : HomeUi
    data class Error(val message: String) : HomeUi
}

@Composable
internal fun HomeContent() {
    val player = LocalPlayerConnection.current
    val router = LocalMusicRouter.current
    var state by remember { mutableStateOf<HomeUi>(HomeUi.Loading) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        state = HomeUi.Loading
        state = runCatching { MusicRepository.home() }.fold(
            onSuccess = { HomeUi.Ready(it) },
            onFailure = { HomeUi.Error(it.message ?: "Unable to load YouTube Music") },
        )
    }

    Box(Modifier.fillMaxSize().background(PageBlack)) {
        Box(
            Modifier.fillMaxWidth().height(235.dp).background(
                Brush.verticalGradient(listOf(Color(0xFF09383B), Color.Transparent)),
            ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 46.dp, bottom = 170.dp),
        ) {
            item { Header() }
            when (val current = state) {
                HomeUi.Loading -> item { CircularProgressIndicator(Modifier.padding(28.dp), color = Cyan) }
                is HomeUi.Error -> item {
                    TextButton(onClick = { reload += 1 }, Modifier.padding(28.dp)) { Text("Retry", color = Cyan, fontWeight = FontWeight.Bold) }
                }
                is HomeUi.Ready -> {
                    if (current.sections.isEmpty()) {
                        item { Text("Nothing here", Modifier.padding(28.dp), Color.White, 16.sp) }
                    }
                    current.sections.forEach { section ->
                        item { SectionTitle(section.title, Modifier.padding(top = 22.dp)) }
                        item { HomeSectionRow(section) { item -> item.open(player, router, section.items.filterIsInstance<SongItem>().map { it.toPlayable() }) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionRow(section: HomeSection, onOpen: (YtItem) -> Unit) {
    val songs = section.items.filterIsInstance<SongItem>()
    if (songs.size >= 4 && songs.size == section.items.size) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            songs.take(6).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { song ->
                        QuickSongCard(song, Modifier.weight(1f)) { onOpen(song) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(section.items, key = { it::class.simpleName + it.id }) { item ->
                HomeTile(item) { onOpen(item) }
            }
        }
    }
}

@Composable
private fun QuickSongCard(song: SongItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(55.dp).clip(RoundedCornerShape(10.dp)).background(Tile).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(song.thumbnail, song.title, Modifier.size(55.dp), contentScale = ContentScale.Crop)
        Text(song.title, Modifier.padding(horizontal = 10.dp), Color.White, 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

@Composable
private fun HomeTile(item: YtItem, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        AsyncImage(
            item.thumbnail,
            item.title,
            Modifier.size(150.dp).clip(RoundedCornerShape(4.dp)).background(Tile),
            contentScale = ContentScale.Crop,
        )
        Text(item.title, Modifier.padding(top = 8.dp), Color.White, 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        item.subtitle?.let { Text(it, color = Color(0xFF8A9A9D), fontSize = 11.sp, maxLines = 1) }
    }
}

@Composable
internal fun Header() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(Cyan), contentAlignment = Alignment.Center) {
            Text("M", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text("MusiPedia", Modifier.padding(start = 15.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 26.dp, vertical = 8.dp), Color.White, 20.sp, fontWeight = FontWeight.Bold)
}
