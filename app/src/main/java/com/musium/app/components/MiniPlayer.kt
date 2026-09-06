package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay

private val Cyan = Color(0xFF00C2CB)

@Composable
internal fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val player = LocalPlayerConnection.current ?: return
    val song = player.current ?: return
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(song.id, player.playing) {
        while (true) {
            position = player.currentPosition()
            delay(400)
        }
    }
    val duration = player.duration.coerceAtLeast(1L)
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF172023))
            .clickable(onClick = onOpen),
    ) {
        LinearProgressIndicator(
            progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Cyan,
            trackColor = Color(0xFF2A3A3D),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                song.thumbnailUrl,
                song.title,
                Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Color.DarkGray),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    player.lastError ?: song.artist,
                    color = if (player.lastError != null) Color(0xFFFF8A80) else Color(0xFFB6B6B6),
                    fontSize = 11.sp,
                    maxLines = 2,
                )
            }
            IconButton(onClick = player::togglePlay) {
                Icon(if (player.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "Play", tint = Cyan)
            }
            IconButton(onClick = player::next) {
                Icon(Icons.Outlined.SkipNext, "Next", tint = Color.White)
            }
        }
    }
}
