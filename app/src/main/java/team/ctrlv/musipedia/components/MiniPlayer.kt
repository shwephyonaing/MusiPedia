package team.ctrlv.musipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val Cyan = Color(0xFF42E4CE)
private val MiniInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface

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
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniPlayerArtworkControl(
                artworkUrl = song.thumbnailUrl,
                title = song.title,
                playing = player.playing,
                progress = (position.toFloat() / duration).coerceIn(0f, 1f),
                onTogglePlay = player::togglePlay,
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(song.title, color = MiniInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                player.lastError?.let { error ->
                    Text(error, color = Color(0xFFFF8A80), fontSize = 11.sp, maxLines = 2)
                }
            }
            Icon(Icons.Outlined.KeyboardArrowUp, "Open player", tint = MiniInk, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun MiniPlayerArtworkControl(
    artworkUrl: String?,
    title: String,
    playing: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(50.dp)) {
            val stroke = 3.dp.toPx()
            drawCircle(
                color = trackColor,
                radius = (size.minDimension - stroke) / 2,
                style = Stroke(stroke),
            )
            drawArc(
                color = Cyan,
                startAngle = -90f,
                sweepAngle = progress.coerceIn(0f, 1f) * 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        AsyncImage(
            model = artworkUrl,
            contentDescription = title,
            modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)))
        IconButton(onClick = onTogglePlay, modifier = Modifier.size(42.dp)) {
            Icon(
                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                if (playing) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(25.dp),
            )
        }
    }
}
