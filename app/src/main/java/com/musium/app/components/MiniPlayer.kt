package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Cyan = Color(0xFF42E4CE)
private val MiniInk = Color(0xFF414944)

@Composable
internal fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier, protectBottom: Boolean = false) {
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
    var safeModifier = modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    if (protectBottom) safeModifier = safeModifier.navigationBarsPadding()
    Column(
        safeModifier
            .background(Color.White)
            .clickable(onClick = onOpen),
    ) {
        LinearProgressIndicator(
            progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Cyan,
            trackColor = Color(0xFFE7EAE8),
        )
        Row(
            Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CdDisc(
                artworkUrl = song.thumbnailUrl,
                contentDescription = song.title,
                playing = player.playing,
                modifier = Modifier.size(48.dp),
                hole = 10.dp,
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(song.title, color = MiniInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    player.lastError ?: song.artist,
                    color = if (player.lastError != null) Color(0xFFFF8A80) else Color(0xFFA1AAA5),
                    fontSize = 11.sp,
                    maxLines = 2,
                )
            }
            Icon(Icons.Outlined.KeyboardArrowUp, "Open player", tint = MiniInk, modifier = Modifier.size(26.dp))
        }
    }
}
