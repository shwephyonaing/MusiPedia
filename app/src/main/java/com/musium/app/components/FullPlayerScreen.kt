package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.musium.innertube.Lyrics
import kotlinx.coroutines.delay

private val Cyan = Color(0xFF18CDE0)
private val Black = Color(0xFF101010)

@Composable
internal fun FullPlayerScreen(onBack: () -> Unit) {
    val player = LocalPlayerConnection.current
    val song = player?.current
    if (player == null || song == null) {
        Column(Modifier.fillMaxSize().background(Black).padding(28.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.KeyboardArrowDown, "Back", tint = Color.White) }
            Text("Nothing is playing", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var lyrics by remember(song.id) { mutableStateOf<Lyrics?>(null) }
    var lyricsOn by remember(song.id) { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    LaunchedEffect(song.id, player.playing, lyricsOn) {
        while (true) {
            if (!dragging) position = player.currentPosition()
            delay(if (lyricsOn) 200 else 400)
        }
    }
    LaunchedEffect(song.id) {
        lyricsOn = false
        lyrics = runCatching { LyricsResolver.load(song, player.duration) }.getOrNull()
    }
    val duration = player.duration.coerceAtLeast(1L)
    Column(Modifier.fillMaxSize().background(Black).padding(horizontal = 28.dp, vertical = 28.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.KeyboardArrowDown, "Back", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            if (player.sleepActive) {
                Text(
                    if (player.sleepEndOfTrack) "Sleep" else formatTime(player.sleepRemaining),
                    color = Cyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Spacer(Modifier.width(48.dp))
            }
            IconButton(onClick = { player.playRadio(song) }, enabled = !song.isLocal) {
                Icon(Icons.Outlined.Radio, "Radio", tint = if (song.isLocal) Color.Gray else Cyan)
            }
        }
        if (lyricsOn && lyrics != null) {
            LyricsPanel(
                lyrics = lyrics!!,
                positionMs = position,
                durationMs = duration,
                modifier = Modifier.fillMaxWidth().height(286.dp).padding(top = 28.dp),
            ) { time ->
                player.seekTo(time)
                position = time
            }
        } else {
            AsyncImage(
                song.thumbnailUrl,
                song.title,
                Modifier.fillMaxWidth().height(286.dp).padding(top = 28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF253238)),
                contentScale = ContentScale.Crop,
            )
        }
        Text(song.title, Modifier.padding(top = 28.dp), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(song.artist, Modifier.padding(top = 6.dp), color = Color(0xFF9FAEB1), fontSize = 16.sp, maxLines = 1)
        player.lastError?.let { error ->
            Text(error, Modifier.padding(top = 8.dp), color = Color(0xFFFF8A80), fontSize = 12.sp, maxLines = 3)
        }
        OfflineDownloads.lastError?.let { error ->
            Text(error, Modifier.padding(top = 8.dp), color = Color(0xFFFF8A80), fontSize = 12.sp, maxLines = 3)
        }
        Slider(
            value = position.toFloat().coerceAtMost(duration.toFloat()),
            onValueChange = {
                dragging = true
                position = it.toLong()
            },
            onValueChangeFinished = {
                player.seekTo(position)
                dragging = false
            },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan),
            modifier = Modifier.padding(top = 24.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Color.LightGray, fontSize = 12.sp)
            Text(formatTime(duration), color = Color.LightGray, fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = player::previous) {
                Icon(Icons.Outlined.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            FloatingActionButton(onClick = player::togglePlay, containerColor = Cyan) {
                Icon(
                    if (player.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    "Play",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
            IconButton(onClick = player::next) {
                Icon(Icons.Outlined.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lyrics != null) {
                IconButton(onClick = { lyricsOn = !lyricsOn }) {
                    Icon(Icons.Outlined.Lyrics, "Lyrics", tint = if (lyricsOn) Cyan else Color.White)
                }
            }
            IconButton(onClick = { showQueue = true }) {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic, "Queue", tint = Color.White)
            }
            IconButton(onClick = { showSleep = true }) {
                Icon(Icons.Outlined.Bedtime, "Sleep timer", tint = if (player.sleepActive) Cyan else Color.White)
            }
            if (song.canDownload) {
                val downloading = song.id in OfflineDownloads.progressing
                val downloaded = OfflineDownloads.has(song.id)
                IconButton(onClick = { OfflineDownloads.toggle(song) }) {
                    when {
                        downloading -> CircularProgressIndicator(
                            progress = { OfflineDownloads.progress[song.id] ?: 0f },
                            color = Cyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                        )
                        downloaded -> Icon(Icons.Outlined.DownloadDone, "Remove download", tint = Cyan)
                        else -> Icon(Icons.Outlined.Download, "Download", tint = Color.White)
                    }
                }
            }
        }
    }
    if (showQueue) QueueSheet(player) { showQueue = false }
    if (showSleep) SleepTimerSheet(player) { showSleep = false }
}

internal fun formatTime(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    val totalSeconds = safe / 1_000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
