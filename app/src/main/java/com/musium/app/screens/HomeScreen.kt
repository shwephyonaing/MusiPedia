package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musium.innertube.HomeSection
import com.musium.innertube.SongItem
import com.musium.innertube.YtItem

private val Cyan = Color(0xFF42E4CE)
private val PageBlack = Color(0xFFFAFAF8)
private val Tile = Color(0xFFF0F0EC)
private val Ink = Color(0xFF3F4944)

private data class DemoMood(val image: Int, val title: String, val duration: String)

private val forYouMoods = listOf(
    DemoMood(R.drawable.anything_goes, "Funky Vibes", "2 hours"),
    DemoMood(R.drawable.pop_mix, "Emotional Eaters", "35 min"),
    DemoMood(R.drawable.chill_mix, "Soft Sundays", "3 hours"),
)

private val popularMoods = listOf(
    DemoMood(R.drawable.recent_harry, "Feeling Artsy", "42 min"),
    DemoMood(R.drawable.library_vibes, "Late Night Vibes", "1 hour"),
    DemoMood(R.drawable.released, "Fresh Sounds", "55 min"),
    DemoMood(R.drawable.coffee_jazz, "Coffee & Jazz", "2 hours"),
)

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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 106.dp),
        ) {
            item { FeaturedHero() }
            item { SectionTitle("For you", Modifier.padding(top = 12.dp)) }
            item { DemoMoodRow(forYouMoods) }
            item { SectionTitle("Popular", Modifier.padding(top = 18.dp)) }
            item { DemoMoodRow(popularMoods) }
            when (val current = state) {
                HomeUi.Loading -> Unit
                is HomeUi.Error -> item {
                    TextButton(onClick = { reload += 1 }, Modifier.padding(28.dp)) { Text("Retry", color = Cyan, fontWeight = FontWeight.Bold) }
                }
                is HomeUi.Ready -> {
                    current.sections.drop(2).forEach { section ->
                        item { SectionTitle(section.title, Modifier.padding(top = 16.dp)) }
                        item { HomeSectionRow(section) { item -> item.open(player, router, section.items.filterIsInstance<SongItem>().map { it.toPlayable() }) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoMoodRow(moods: List<DemoMood>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(moods) { mood ->
            Column(Modifier.width(158.dp)) {
                Image(
                    painter = painterResource(mood.image),
                    contentDescription = mood.title,
                    modifier = Modifier.size(158.dp).clip(RoundedCornerShape(10.dp)).background(Tile),
                    contentScale = ContentScale.Crop,
                )
                Text(mood.title, Modifier.padding(top = 8.dp), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(mood.duration, Modifier.padding(top = 2.dp), color = Color(0xFFA3ACA7), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FeaturedHero() {
    Image(
        painter = painterResource(R.drawable.featured_all_about_summer),
        contentDescription = "Featured album: All About Summer",
        modifier = Modifier.fillMaxWidth().aspectRatio(414f / 463f),
        contentScale = ContentScale.FillWidth,
        alignment = Alignment.TopCenter,
    )
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
        Text(song.title, Modifier.padding(horizontal = 10.dp), Ink, 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
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
        Text(item.title, Modifier.padding(top = 8.dp), Ink, 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        item.subtitle?.let { Text(it, color = Color(0xFF8C9690), fontSize = 11.sp, maxLines = 1) }
    }
}

@Composable
internal fun Header() {
    BrandLockup(Modifier.fillMaxWidth().padding(horizontal = 28.dp), textSize = 24.sp)
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 26.dp, vertical = 8.dp), Ink, 23.sp, fontWeight = FontWeight.Bold)
}
