package team.ctrlv.musipedia

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import team.ctrlv.musipedia.innertube.Lyrics

private val Cyan = Color(0xFF42E4CE)
private val Sheet: Color @Composable get() = MaterialTheme.colorScheme.surface
private val SheetInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Scrim = Color.Black.copy(alpha = 0.45f)

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

/** In-hierarchy sheet — avoids ModalBottomSheet Dialog tap-through bugs. */
@Composable
internal fun PlayerOverlaySheet(
    onDismiss: () -> Unit,
    heightFraction: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().zIndex(20f)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Sheet)
                // Absorb empty-area taps so they never reach the player/home underneath.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFD0D5D2)),
            )
            content()
        }
    }
}

@Composable
internal fun QueueSheet(player: PlayerConnection, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    var rows by remember {
        mutableStateOf(player.queue.mapIndexed { index, song -> QueueRow(uid = "q-$index-${song.id}", song = song) })
    }
    var draggingUid by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player.queue, draggingUid) {
        if (draggingUid != null) return@LaunchedEffect
        rows = player.queue.mapIndexed { index, song ->
            val existing = rows.getOrNull(index)
            if (existing != null && existing.song.id == song.id) {
                existing.copy(song = song)
            } else {
                QueueRow(uid = "q-$index-${song.id}-${song.title.hashCode()}", song = song)
            }
        }
        dragOffsetY = 0f
    }

    PlayerOverlaySheet(onDismiss = onDismiss, heightFraction = 0.88f) {
        Text(
            "Playing next",
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            SheetInk,
            22.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 12.dp),
        ) {
            itemsIndexed(rows, key = { _, row -> row.uid }) { index, row ->
                val current = index == player.currentIndex
                val dragging = row.uid == draggingUid
                Row(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 2f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffsetY else 0f
                            shadowElevation = if (dragging) 10f else 0f
                        }
                        .background(if (current) Cyan.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.DragHandle,
                        "Drag to reorder",
                        tint = Color(0xFF9FA8A3),
                        modifier = Modifier
                            .size(32.dp)
                            .pointerInput(row.uid) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingUid = row.uid
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingUid = null
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        draggingUid = null
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        val from = rows.indexOfFirst { it.uid == row.uid }
                                        if (from < 0) return@detectDragGestures
                                        val visible = listState.layoutInfo.visibleItemsInfo
                                        val self = visible.find { it.key == row.uid }
                                            ?: visible.find { it.index == from }
                                            ?: return@detectDragGestures
                                        // Only step one slot at a time, and only after crossing
                                        // most of the neighbor — avoids twitchy midpoint jumps.
                                        val step = self.size.toFloat().coerceAtLeast(1f)
                                        val commit = step * 0.62f
                                        val target = when {
                                            dragOffsetY > commit && from < rows.lastIndex -> from + 1
                                            dragOffsetY < -commit && from > 0 -> from - 1
                                            else -> from
                                        }
                                        if (target != from) {
                                            player.moveInQueue(from, target)
                                            rows = rows.toMutableList().also { list ->
                                                val moved = list.removeAt(from)
                                                list.add(target, moved)
                                            }
                                            // Keep the floating row under the finger after the swap.
                                            dragOffsetY -= (target - from) * step
                                        }
                                    },
                                )
                            },
                    )
                    AsyncImage(
                        row.song.thumbnailUrl,
                        row.song.title,
                        Modifier
                            .padding(start = 4.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .clickable {
                                val playIndex = rows.indexOfFirst { it.uid == row.uid }
                                if (playIndex >= 0) player.playAt(playIndex)
                            },
                    ) {
                        Text(
                            row.song.title,
                            color = if (current) Cyan else SheetInk,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(row.song.artist, color = Color(0xFF9FA8A3), fontSize = 12.sp, maxLines = 1)
                    }
                    IconButton(
                        onClick = {
                            val from = rows.indexOfFirst { it.uid == row.uid }
                            if (from > 0) player.moveInQueue(from, from - 1)
                        },
                        enabled = index > 0 && draggingUid == null,
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
                            val from = rows.indexOfFirst { it.uid == row.uid }
                            if (from >= 0 && from < rows.lastIndex) player.moveInQueue(from, from + 1)
                        },
                        enabled = index < rows.lastIndex && draggingUid == null,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            "Move down",
                            tint = if (index < rows.lastIndex) SheetInk else Color(0xFFD0D5D2),
                        )
                    }
                    if (!current) {
                        IconButton(
                            onClick = {
                                val removeIndex = rows.indexOfFirst { it.uid == row.uid }
                                if (removeIndex >= 0) player.removeFromQueue(removeIndex)
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Outlined.Close, "Remove", tint = Color(0xFF9FA8A3))
                        }
                    }
                }
            }
        }
    }
}

private data class QueueRow(val uid: String, val song: PlayableSong)

@Composable
internal fun SleepTimerSheet(player: PlayerConnection, onDismiss: () -> Unit) {
    val options = listOf(5, 15, 30, 45, 60)
    PlayerOverlaySheet(onDismiss = onDismiss, heightFraction = 0.48f) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
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

@Composable
internal fun EqualizerSheet(player: PlayerConnection, onDismiss: () -> Unit) {
    val state = player.equalizerState
    val presets = remember {
        EqPreset.entries.filter { it != EqPreset.CUSTOM }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val chipIdle = MaterialTheme.colorScheme.surfaceVariant
    val trackIdle = MaterialTheme.colorScheme.outlineVariant
    val trackDisabled = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    PlayerOverlaySheet(onDismiss = onDismiss, heightFraction = 0.72f) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Equalizer", color = SheetInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = state.enabled,
                    onCheckedChange = player::setEqualizerEnabled,
                    enabled = state.available,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Cyan,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = trackIdle,
                        disabledUncheckedTrackColor = trackDisabled,
                    ),
                )
            }
            if (!state.available) {
                Text(
                    "Equalizer unavailable on this device right now. Start playback and try again.",
                    Modifier.padding(top = 8.dp, bottom = 12.dp),
                    muted,
                    13.sp,
                )
                return@Column
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    val active = state.presetId == preset.id
                    Text(
                        preset.label,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) Cyan.copy(alpha = 0.18f) else chipIdle)
                            .clickable(enabled = state.enabled) {
                                player.applyEqualizerPreset(preset)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (active) Cyan else SheetInk,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    )
                }
                if (state.presetId == EqPreset.CUSTOM.id) {
                    Text(
                        "Custom",
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Cyan.copy(alpha = 0.18f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Cyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                state.bands.forEach { band ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            Modifier
                                .height(150.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Slider(
                                value = band.level,
                                onValueChange = { player.setEqualizerBand(band.index, it) },
                                valueRange = -1f..1f,
                                enabled = state.enabled,
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = -90f }
                                    .width(150.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Cyan,
                                    activeTrackColor = Cyan,
                                    inactiveTrackColor = trackIdle,
                                    disabledThumbColor = muted,
                                    disabledActiveTrackColor = muted,
                                    disabledInactiveTrackColor = trackDisabled,
                                ),
                            )
                        }
                        Text(
                            formatEqHz(band.centerHz),
                            color = muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            if (state.bassAvailable) {
                Text(
                    "Bass boost",
                    Modifier.padding(top = 16.dp),
                    SheetInk,
                    14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Slider(
                    value = state.bassStrength.toFloat(),
                    onValueChange = { player.setBassBoost(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = state.enabled,
                    colors = SliderDefaults.colors(
                        thumbColor = Cyan,
                        activeTrackColor = Cyan,
                        inactiveTrackColor = trackIdle,
                        disabledThumbColor = muted,
                        disabledActiveTrackColor = muted,
                        disabledInactiveTrackColor = trackDisabled,
                    ),
                )
            }
        }
    }
}

private fun formatEqHz(hz: Int): String = when {
    hz >= 1000 -> "${hz / 1000}k"
    else -> "$hz"
}
