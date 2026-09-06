package com.musium.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

private val AppBlack = Color(0xFF121111)
private val AppCyan = Color(0xFF00C2CB)
private val ButtonCyan = Color(0xFF06A0B5)

internal data class ScreenEntry(val title: String, val subtitle: String)

@Composable
internal fun AllScreensPage(onBack: () -> Unit) {
    var opened by remember { mutableStateOf<ScreenEntry?>(null) }
    val screens = listOf(
        ScreenEntry("Folders", "Your Library"), ScreenEntry("Playlists", "Your Library"), ScreenEntry("Artists", "Recently played"),
        ScreenEntry("Albums", "Recently added"), ScreenEntry("Podcasts & Shows", "Your Library"), ScreenEntry("Search", "Browse Library"),
        ScreenEntry("Playlist", "Lofi Loft"), ScreenEntry("Song", "grainy days • moody."), ScreenEntry("Queue", "Playing from Lofi Loft"),
        ScreenEntry("Equalizer", "Built-in speakers"), ScreenEntry("Sleep timer", "Stop audio automatically"), ScreenEntry("Player menu", "Song actions"),
        ScreenEntry("Folder", "Saved collections"), ScreenEntry("Stats", "Listening statistics"), ScreenEntry("Add to playlist", "Choose a playlist"),
        ScreenEntry("Create New", "New Playlist"),
    )
    opened?.let { FullScreenTemplate(it, onBack = { opened = null }); return }
    LazyColumn(Modifier.fillMaxSize().background(AppBlack), contentPadding = PaddingValues(top = 50.dp, bottom = 30.dp)) {
        item { Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White, modifier = Modifier.clickable(onClick = onBack)); Text("All Figma Screens", Modifier.padding(start = 20.dp), AppCyan, 26.sp, fontWeight = FontWeight.Bold) } }
        items(screens) { screen -> Row(Modifier.fillMaxWidth().clickable { opened = screen }.padding(horizontal = 26.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF183236)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MusicNote, null, tint = AppCyan) }; Column(Modifier.padding(start = 18.dp).weight(1f)) { Text(screen.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(screen.subtitle, color = Color(0xFF8A9A9D), fontSize = 14.sp) }; Icon(Icons.Outlined.ChevronRight, null, tint = Color.White) } }
    }
}

@Composable
internal fun FullScreenTemplate(entry: ScreenEntry, onBack: () -> Unit) {
    val songs = listOf("grainy days" to "moody.", "Coffee" to "Kainbeats", "raindrops" to "rainyyxx", "Tokyo" to "SmYang", "Lullaby" to "iamfinenow", "Hazel Eyes" to "moody.")
    Column(Modifier.fillMaxSize().background(AppBlack).padding(top = 48.dp)) {
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White, modifier = Modifier.clickable(onClick = onBack)); Column(Modifier.padding(start = 18.dp)) { Text(entry.title, color = AppCyan, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text(entry.subtitle, color = Color(0xFF8A9A9D), fontSize = 13.sp) } }
        if (entry.title == "Song") PlayerBody() else if (entry.title == "Equalizer") EqualizerBody() else LazyColumn(Modifier.padding(top = 28.dp), contentPadding = PaddingValues(bottom = 30.dp)) { items(songs) { (title, artist) -> Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Color(0xFF136A75), Color(0xFF26214F)))), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MusicNote, null, tint = Color.White) }; Column(Modifier.padding(start = 16.dp)) { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(artist, color = Color(0xFF8A9A9D), fontSize = 14.sp) } } } }
    }
}

@Composable internal fun PlayerBody() { Column(Modifier.fillMaxWidth().padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Color(0xFF263A44), Color(0xFF0E8D9D)))), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MusicNote, null, tint = Color.White, modifier = Modifier.size(100.dp)) }; Text("grainy days", Modifier.fillMaxWidth().padding(top = 22.dp), Color.White, 22.sp, fontWeight = FontWeight.Bold); Text("moody.", Modifier.fillMaxWidth(), Color(0xFF8A9A9D), 16.sp); Slider(.55f, {}, Modifier.padding(top = 20.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.SkipPrevious, null, tint = Color.White); Icon(Icons.Outlined.PlayCircle, null, tint = AppCyan, modifier = Modifier.size(64.dp)); Icon(Icons.Outlined.SkipNext, null, tint = Color.White) } } }

@Composable internal fun EqualizerBody() { Column(Modifier.fillMaxWidth().padding(28.dp)) { Text("PRESETS", color = Color.White, fontSize = 14.sp); LazyColumn(Modifier.height(220.dp)) { items(listOf("Custom", "Normal", "Pop", "Classic", "Heavy M")) { Text(it, Modifier.fillMaxWidth().padding(14.dp), color = if (it == "Custom") AppCyan else Color.White, fontSize = 17.sp) } }; Text("Bass Boost : 23%", color = Color.White, fontSize = 17.sp); Slider(.23f, {}); Text("3D Effect : 69%", color = Color.White, fontSize = 17.sp); Slider(.69f, {}) } }


