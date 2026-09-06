package com.musium.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val PlayerCyan = Color(0xFF18CDE0)
private val PlayerBlack = Color(0xFF101010)

@Composable
internal fun LocalMusicLibrary() {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    var songs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<LocalSong?>(null) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }

    LaunchedEffect(allowed) {
        if (allowed) {
            loading = true
            songs = LocalMusicRepository(context.applicationContext).songs()
            loading = false
        }
    }

    selectedSong?.let { song -> LocalAudioPlayerScreen(song) { selectedSong = null } } ?: LazyColumn(
        Modifier.fillMaxSize().background(PlayerBlack), contentPadding = PaddingValues(top = 54.dp, bottom = 105.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LibraryMusic, null, tint = PlayerCyan, modifier = Modifier.size(30.dp))
                Text("Your Music", Modifier.padding(start = 12.dp), color = PlayerCyan, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        when {
            !allowed -> item {
                Column(Modifier.padding(28.dp)) {
                    Text("Allow music access", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Musium needs access to audio files stored on this phone.", Modifier.padding(top = 8.dp), color = Color.LightGray)
                    Button(onClick = { requestPermission.launch(permission) }, Modifier.padding(top = 18.dp)) { Text("Allow access") }
                }
            }
            loading -> item { CircularProgressIndicator(Modifier.padding(28.dp), color = PlayerCyan) }
            songs.isEmpty() -> item { Text("No music files found. Add MP3 or M4A music files to your phone, then reopen this screen.", Modifier.padding(28.dp), color = Color.LightGray) }
            else -> {
                item { Text("Songs on this phone", Modifier.padding(28.dp, 24.dp, 28.dp, 10.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                items(songs, key = { it.id }) { song -> LocalSongRow(song) { selectedSong = song } }
            }
        }
    }
}

@Composable
private fun LocalSongRow(song: LocalSong, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(song.artworkUri, null, Modifier.size(58.dp).clip(RoundedCornerShape(6.dp)).background(Color.DarkGray), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(song.artist, color = Color.LightGray, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.Outlined.PlayArrow, "Play", tint = PlayerCyan)
    }
}

@Composable
private fun LocalAudioPlayerScreen(song: LocalSong, onBack: () -> Unit) {
    val context = LocalContext.current
    var player by remember(song.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    val duration = song.durationMs.coerceAtLeast(1L)

    DisposableEffect(song.id) {
        val mediaPlayer = MediaPlayer.create(context, song.audioUri)
        player = mediaPlayer
        mediaPlayer?.setOnCompletionListener { playing = false; position = duration }
        onDispose { mediaPlayer?.release() }
    }
    LaunchedEffect(playing) {
        while (playing) { position = player?.currentPosition?.toLong() ?: position; delay(400) }
    }

    Column(Modifier.fillMaxSize().background(PlayerBlack).padding(horizontal = 28.dp, vertical = 28.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.KeyboardArrowDown, "Back", tint = Color.White, modifier = Modifier.size(32.dp)) }
            Icon(Icons.Outlined.MoreVert, "More options", tint = Color.White)
        }
        Text("PLAYING FROM YOUR PHONE", color = Color(0xFF8A9799), fontSize = 10.sp, letterSpacing = 1.sp)
        Text("Local music", Modifier.padding(top = 4.dp), color = PlayerCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        AsyncImage(song.artworkUri, null, Modifier.fillMaxWidth().height(286.dp).padding(top = 28.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF253238)), contentScale = ContentScale.Crop)
        Row(Modifier.fillMaxWidth().padding(top = 25.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(song.artist, Modifier.padding(top = 3.dp), color = Color(0xFF9FAEB1), fontSize = 16.sp)
            }
            Icon(Icons.Outlined.Share, "Share", tint = Color.White, modifier = Modifier.padding(end = 18.dp))
            IconButton(onClick = { liked = !liked }) {
                Icon(if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Like", tint = PlayerCyan)
            }
        }
        Slider(value = position.toFloat(), onValueChange = { position = it.toLong(); player?.seekTo(position.toInt()) }, valueRange = 0f..duration.toFloat(), colors = SliderDefaults.colors(thumbColor = PlayerCyan, activeTrackColor = PlayerCyan), modifier = Modifier.padding(top = 24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(time(position), color = Color.LightGray, fontSize = 12.sp); Text(time(duration), color = Color.LightGray, fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shuffle, "Shuffle", tint = Color.White)
            Icon(Icons.Outlined.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
            FloatingActionButton(onClick = { player?.let { if (it.isPlaying) it.pause() else it.start(); playing = it.isPlaying } }, containerColor = PlayerCyan) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "Play or pause", tint = Color.White, modifier = Modifier.size(34.dp)) }
            Icon(Icons.Outlined.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
            Icon(Icons.Outlined.QueueMusic, "Queue", tint = Color.White)
        }
        Text("LYRICS", Modifier.padding(top = 42.dp, start = 4.dp), color = Color(0xFF9FAEB1), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF267685)).padding(22.dp)) {
            Text("Lyrics are available for tracks you add to Musium.", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Add lyrics later from your own licensed source.", Modifier.padding(top = 9.dp), color = Color(0xFFB6E5EA), fontSize = 14.sp)
        }
    }
}

private fun time(milliseconds: Long): String = "%d:%02d".format(milliseconds / 60_000, (milliseconds / 1_000) % 60)
