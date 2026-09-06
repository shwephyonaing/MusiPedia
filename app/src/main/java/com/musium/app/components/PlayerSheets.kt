package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musium.innertube.Lyrics

private val Cyan = Color(0xFF42E4CE)
private val Sheet = Color.White
private val SheetInk = Color(0xFF414944)

@Composable
internal fun LyricsPanel(
    lyrics: Lyrics,
    positionMs: Long,
    durationMs: Long = 0L,
    modifier: Modifier = Modifier,
    onSeek: ((Long) -> Unit)? = null,
) {
    val last = lyrics.lines.lastIndex
    val active = when {
        lyrics.synced -> lyrics.lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        durationMs > 0L && last > 0 -> {
            val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            (progress * last).toInt().coerceIn(0, last)
        }
        else -> -1
    }
    val listState = rememberLazyListState()
    LaunchedEffect(active, lyrics.synced) {
        if (active >= 0) {
            val offset = -(listState.layoutInfo.viewportSize.height / 3)
            runCatching { listState.animateScrollToItem(active, offset) }
        }
    }
    LazyColumn(
        modifier,
        state = listState,
        contentPadding = PaddingValues(vertical = 72.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lyrics.lines, key = { index, line -> "${line.timeMs}-$index" }) { index, line ->
            val current = index == active
            Text(
                line.text,
                modifier = if (lyrics.synced && onSeek != null) {
                    Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }
                } else {
                    Modifier.fillMaxWidth()
                },
                color = when {
                    current -> Cyan
                    active >= 0 -> Color(0xFF8A9A9D)
                    else -> SheetInk
                },
                fontSize = if (current) 22.sp else 16.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(player: PlayerConnection, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sheet) {
        Text(
            "Playing next",
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            SheetInk,
            22.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyColumn(Modifier.padding(bottom = 28.dp)) {
            itemsIndexed(player.queue, key = { index, item -> "${item.id}-$index" }) { index, song ->
                val current = index == player.currentIndex
                var dragAccum by remember(song.id) { mutableFloatStateOf(0f) }
                var dragIndex by remember(song.id, index) { mutableIntStateOf(index) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) Color(0xFFE8FBF8) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.DragHandle,
                        "Drag to reorder",
                        tint = Color(0xFF9FA8A3),
                        modifier = Modifier
                            .size(28.dp)
                            .pointerInput(song.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragAccum = 0f
                                        dragIndex = player.queue.indexOfFirst { it.id == song.id }
                                            .takeIf { it >= 0 } ?: index
                                    },
                                    onDragCancel = { dragAccum = 0f },
                                    onDragEnd = { dragAccum = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragAccum += dragAmount.y
                                        val step = 56f
                                        val last = player.queue.lastIndex
                                        while (dragAccum <= -step && dragIndex > 0) {
                                            player.moveInQueue(dragIndex, dragIndex - 1)
                                            dragIndex--
                                            dragAccum += step
                                        }
                                        while (dragAccum >= step && dragIndex < last) {
                                            player.moveInQueue(dragIndex, dragIndex + 1)
                                            dragIndex++
                                            dragAccum -= step
                                        }
                                    },
                                )
                            },
                    )
                    AsyncImage(
                        song.thumbnailUrl,
                        song.title,
                        Modifier
                            .padding(start = 4.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.DarkGray)
                            .clickable { player.playAt(index) },
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .clickable { player.playAt(index) },
                    ) {
                        Text(
                            song.title,
                            color = if (current) Cyan else SheetInk,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(song.artist, color = Color(0xFF9FA8A3), fontSize = 12.sp, maxLines = 1)
                    }
                    IconButton(
                        onClick = { if (index > 0) player.moveInQueue(index, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowUp,
                            "Move up",
                            tint = if (index > 0) SheetInk else Color(0xFFD0D5D2),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (index < player.queue.lastIndex) player.moveInQueue(index, index + 1)
                        },
                        enabled = index < player.queue.lastIndex,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            "Move down",
                            tint = if (index < player.queue.lastIndex) SheetInk else Color(0xFFD0D5D2),
                        )
                    }
                    if (!current) {
                        IconButton(onClick = { player.removeFromQueue(index) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Close, "Remove", tint = Color(0xFF9FA8A3))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(player: PlayerConnection, onDismiss: () -> Unit) {
    val options = listOf(5, 15, 30, 45, 60)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sheet) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text("Sleep timer", color = SheetInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (player.sleepActive) {
                Text(
                    if (player.sleepEndOfTrack) "Ends with this song" else formatTime(player.sleepRemaining),
                    Modifier.padding(top = 6.dp),
                    Cyan,
                    14.sp,
                )
            }
            TextButton(onClick = { player.setSleepEndOfSong(); onDismiss() }) {
                Text("End of song", color = SheetInk, fontSize = 16.sp)
            }
            options.forEach { minutes ->
                TextButton(onClick = { player.setSleepMinutes(minutes); onDismiss() }) {
                    Text("$minutes min", color = SheetInk, fontSize = 16.sp)
                }
            }
            if (player.sleepActive) {
                TextButton(onClick = { player.clearSleep(); onDismiss() }) {
                    Text("Off", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
