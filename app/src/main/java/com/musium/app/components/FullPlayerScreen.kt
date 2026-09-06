package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musium.innertube.Lyrics
import kotlinx.coroutines.delay

private val Cyan = Color(0xFF42E4CE)
private val Black = Color(0xFFFAFAF8)
private val Ink = Color(0xFF3F4944)

@Composable
internal fun FullPlayerScreen(onBack: () -> Unit) {
    val player = LocalPlayerConnection.current
    val song = player?.current
    if (player == null || song == null) {
        Column(Modifier.fillMaxSize().background(Black).safeDrawingPadding().padding(28.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.KeyboardArrowDown, "Back", tint = Ink) }
            Text("Nothing is playing", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    Column(Modifier.fillMaxSize().background(Black).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color(0xFFE1E5E2), CircleShape).size(38.dp)) {
                Icon(Icons.Outlined.KeyboardArrowDown, "Back", tint = Ink, modifier = Modifier.size(32.dp))
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
            Spacer(Modifier.width(48.dp))
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
                model = song.thumbnailUrl,
                contentDescription = song.title,
                modifier = Modifier.fillMaxWidth().height(326.dp).padding(top = 14.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFFF0F1EF)),
                contentScale = ContentScale.Crop,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.Top) {
            Text(
                song.title,
                Modifier.weight(1f),
                color = Ink,
                fontSize = 27.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                maxLines = 2,
            )
            IconButton(onClick = { showQueue = true }) {
                Icon(Icons.Outlined.MoreHoriz, "More", tint = Ink)
            }
        }
        Text("${formatTime(duration)}  ·  ${player.queue.size} Tracks", color = Color(0xFFA0A9A4), fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Playing next", color = Color(0xFFB1B8B4), fontSize = 11.sp)
            Text(song.artist, Modifier.padding(start = 18.dp), color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
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
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Color(0xFF8C9690), fontSize = 12.sp)
            Text(formatTime(duration), color = Color(0xFF8C9690), fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = player::previous) {
                Icon(Icons.Outlined.SkipPrevious, "Previous", tint = Cyan, modifier = Modifier.size(36.dp))
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
                Icon(Icons.Outlined.SkipNext, "Next", tint = Cyan, modifier = Modifier.size(36.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lyrics != null) {
                IconButton(onClick = { lyricsOn = !lyricsOn }) {
                    Icon(Icons.Outlined.Lyrics, "Lyrics", tint = if (lyricsOn) Cyan else Ink)
                }
            }
            IconButton(onClick = { showQueue = true }) {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic, "Queue", tint = Ink)
            }
            IconButton(onClick = { showSleep = true }) {
                Icon(Icons.Outlined.Bedtime, "Sleep timer", tint = if (player.sleepActive) Cyan else Ink)
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
                        else -> Icon(Icons.Outlined.Download, "Download", tint = Ink)
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
