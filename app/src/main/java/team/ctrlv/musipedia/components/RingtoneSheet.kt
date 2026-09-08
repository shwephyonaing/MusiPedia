package team.ctrlv.musipedia

import android.media.MediaPlayer
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

private val Cyan = Color(0xFF42E4CE)
private val SheetInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Muted = Color(0xFF9FA8A3)

/** Snap trim/scrub positions to whole seconds for predictable, tickable dragging. */
private fun snapToSecond(ms: Long, durationMs: Long): Long {
    val safe = durationMs.coerceAtLeast(0L)
    if (safe <= 0L) return 0L
    val snapped = ((ms.toDouble() / 1_000.0).roundToLong() * 1_000L)
    return snapped.coerceIn(0L, safe)
}

/**
 * Ringtone trim UX follows the Ringdroid / MP3-cutter pattern:
 * one timeline, start+end handles, free playhead scrub to listen anywhere,
 * then Mark Start / Mark End from the listened position.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RingtoneSheet(
    song: PlayableSong,
    player: PlayerConnection,
    durationMs: Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localFile = remember(song.id, OfflineDownloads.songs) { RingtoneSetter.localFile(song) }
    var fileDuration by remember(localFile?.absolutePath) {
        mutableLongStateOf(
            localFile?.let { RingtoneSetter.durationMs(it) }?.takeIf { it > 0L } ?: durationMs,
        )
    }
    LaunchedEffect(localFile?.absolutePath) {
        if (localFile != null) {
            fileDuration = withContext(Dispatchers.IO) {
                RingtoneSetter.durationMs(localFile).takeIf { it > 0L } ?: durationMs
            }
        }
    }
    val safeDuration = fileDuration.coerceAtLeast(1L)
    val defaultEnd = minOf(RingtoneSetter.DEFAULT_CLIP_MS, safeDuration)
        .coerceAtLeast(minOf(RingtoneSetter.MIN_CLIP_MS, safeDuration))
    var startMs by remember(song.id) { mutableLongStateOf(0L) }
    var endMs by remember(song.id) { mutableLongStateOf(defaultEnd) }
    LaunchedEffect(safeDuration) {
        if (endMs > safeDuration || endMs - startMs > RingtoneSetter.MAX_CLIP_MS) {
            endMs = minOf(startMs + RingtoneSetter.DEFAULT_CLIP_MS, safeDuration)
                .coerceAtLeast(minOf(startMs + RingtoneSetter.MIN_CLIP_MS, safeDuration))
        }
    }
    var playheadMs by remember(song.id) { mutableLongStateOf(0L) }
    var previewing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var loopingClip by remember { mutableStateOf(true) }
    var applying by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var selectedTargets by remember {
        mutableStateOf(setOf(RingtoneSoundTarget.PhoneSim1))
    }
    val simCount = remember { RingtoneSetter.activeSimCount(context) }
    val mediaPlayer = remember { MediaPlayer() }
    val waveformSeed = remember(song.id) { song.id.hashCode() }

    LaunchedEffect(localFile?.absolutePath) {
        prepared = false
        previewing = false
        playheadMs = 0L
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mediaPlayer.stop()
                mediaPlayer.release()
            }
        }
    }

    fun ensurePrepared(file: File): Boolean {
        if (prepared) return true
        return runCatching {
            if (player.playing) player.togglePlay()
            mediaPlayer.reset()
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            prepared = true
            true
        }.getOrElse {
            status = "Could not open this file for preview"
            false
        }
    }

    fun stopPreview() {
        previewing = false
        runCatching { if (mediaPlayer.isPlaying) mediaPlayer.pause() }
    }

    fun seekTo(timeMs: Long, autoPlay: Boolean) {
        val file = localFile ?: return
        if (!ensurePrepared(file)) return
        val t = snapToSecond(timeMs, safeDuration)
        playheadMs = t
        runCatching {
            mediaPlayer.seekTo(t.toInt())
            if (autoPlay) {
                if (!mediaPlayer.isPlaying) mediaPlayer.start()
                previewing = true
            } else {
                if (mediaPlayer.isPlaying) mediaPlayer.pause()
                previewing = false
            }
        }
    }

    fun pauseForDrag() {
        previewing = false
        runCatching { if (mediaPlayer.isPlaying) mediaPlayer.pause() }
    }

    fun playFromStart() {
        loopingClip = true
        seekTo(startMs, autoPlay = true)
    }

    fun togglePlay(file: File) {
        if (previewing) {
            stopPreview()
            return
        }
        if (!ensurePrepared(file)) return
        loopingClip = true
        seekTo(startMs, autoPlay = true)
    }

    LaunchedEffect(previewing, startMs, endMs, loopingClip) {
        if (!previewing) return@LaunchedEffect
        while (previewing) {
            val pos = runCatching { mediaPlayer.currentPosition.toLong() }.getOrDefault(playheadMs)
            playheadMs = pos.coerceIn(0L, safeDuration)
            when {
                loopingClip && pos >= endMs - 40L -> {
                    runCatching {
                        mediaPlayer.seekTo(startMs.toInt().coerceAtLeast(0))
                        mediaPlayer.start()
                    }
                    playheadMs = startMs
                }
                !mediaPlayer.isPlaying -> previewing = false
                pos >= safeDuration - 40L -> {
                    stopPreview()
                    playheadMs = safeDuration
                }
            }
            delay(50)
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (RingtoneSetter.canWriteSettings(context)) {
            status = "Permission granted — tap Apply"
        } else {
            status = "Write settings permission is required"
        }
    }
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        status = if (granted) "Storage allowed — tap Apply"
        else "Storage permission is required on this Android version"
    }

    fun applyRange(nextStart: Long, nextEnd: Long, movingEnd: Boolean = false): Boolean {
        val maxLen = RingtoneSetter.MAX_CLIP_MS
        val minLen = minOf(RingtoneSetter.MIN_CLIP_MS, safeDuration)
        val previousStart = startMs
        var start = snapToSecond(nextStart, safeDuration)
        var end = snapToSecond(nextEnd, safeDuration)

        if (end < start) {
            if (movingEnd) end = start
            else start = end
        }

        if (movingEnd) {
            // Dragging End forward: keep End where the user put it, pull Start along
            // so the clip never exceeds max length (e.g. End 0:40 → Start 0:10).
            if (end - start > maxLen) {
                start = snapToSecond((end - maxLen).coerceAtLeast(0L), safeDuration)
            }
            if (end - start < minLen) {
                end = snapToSecond((start + minLen).coerceAtMost(safeDuration), safeDuration)
                if (end - start < minLen) {
                    start = snapToSecond((end - minLen).coerceAtLeast(0L), safeDuration)
                }
            }
        } else {
            // Dragging Start: keep Start, push End if needed.
            if (end - start > maxLen) {
                end = snapToSecond((start + maxLen).coerceAtMost(safeDuration), safeDuration)
            }
            if (end - start < minLen) {
                start = snapToSecond((end - minLen).coerceAtLeast(0L), safeDuration)
                if (end - start < minLen) {
                    end = snapToSecond((start + minLen).coerceAtMost(safeDuration), safeDuration)
                }
            }
        }

        startMs = start
        endMs = end
        return start != previousStart
    }

    PlayerOverlaySheet(onDismiss = {
        stopPreview()
        onDismiss()
    }, heightFraction = 0.98f) {
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = Cyan, modifier = Modifier.size(22.dp))
                Text(
                    "Set sound",
                    Modifier.padding(start = 10.dp),
                    SheetInk,
                    20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                song.title,
                Modifier.padding(top = 6.dp),
                SheetInk,
                15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(song.artist, color = Muted, fontSize = 12.sp, maxLines = 1)

            if (localFile == null) {
                Text(
                    "Download this song first so you can trim a clean clip.",
                    Modifier.padding(top = 18.dp),
                    Muted,
                    14.sp,
                )
                Button(
                    onClick = {
                        OfflineDownloads.download(song)
                        status = "Downloading…"
                    },
                    enabled = song.canDownload && song.id !in OfflineDownloads.progressing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (song.id in OfflineDownloads.progressing) {
                        CircularProgressIndicator(
                            progress = { OfflineDownloads.progress[song.id] ?: 0f },
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("Downloading…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Download", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
              
                var endDragMovedStart by remember { mutableStateOf(false) }
                RingtoneTimeline(
                    startMs = startMs,
                    endMs = endMs,
                    durationMs = safeDuration,
                    waveformSeed = waveformSeed,
                    onDragBegin = {
                        pauseForDrag()
                        endDragMovedStart = false
                    },
                    onStartChange = { t ->
                        pauseForDrag()
                        applyRange(t, endMs, movingEnd = false)
                        playheadMs = startMs
                    },
                    onStartCommitted = {
                        playFromStart()
                    },
                    onEndChange = { t ->
                        pauseForDrag()
                        val startMoved = applyRange(startMs, t, movingEnd = true)
                        if (startMoved) endDragMovedStart = true
                    },
                    onEndCommitted = {
                        if (endDragMovedStart) {
                            endDragMovedStart = false
                            playFromStart()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Start ${formatTime(startMs)}", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Clip ${formatTime(endMs - startMs)}",
                        color = SheetInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("End ${formatTime(endMs)}", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Clip ${RingtoneSetter.MIN_CLIP_MS / 1000}–${RingtoneSetter.MAX_CLIP_MS / 1000}s",
                    Modifier.padding(top = 4.dp),
                    Muted,
                    11.sp,
                )

                Text(
                    "Apply to",
                    Modifier.padding(top = 16.dp),
                    SheetInk,
                    14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (simCount >= 2) "Dual SIM detected — choose phone and/or alarm."
                    else "Choose phone ringtone and/or alarm. SIM 2 applies on dual-SIM devices.",
                    Modifier.padding(top = 4.dp),
                    Muted,
                    12.sp,
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RingtoneSoundTarget.entries.forEach { target ->
                        val selected = target in selectedTargets
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedTargets = if (selected) {
                                    selectedTargets - target
                                } else {
                                    selectedTargets + target
                                }
                            },
                            label = {
                                Text(
                                    when (target) {
                                        RingtoneSoundTarget.PhoneSim1 -> "Phone · SIM 1"
                                        RingtoneSoundTarget.PhoneSim2 -> "Phone · SIM 2"
                                        RingtoneSoundTarget.Alarm -> "Alarm"
                                    },
                                    fontSize = 13.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan,
                                selectedLabelColor = Color.Black,
                                labelColor = SheetInk,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }
                selectedTargets.sortedBy { it.ordinal }.forEach { target ->
                    Text("• ${target.detail}", Modifier.padding(top = 4.dp), Muted, 11.sp)
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(
                        onClick = { togglePlay(localFile) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Cyan.copy(alpha = 0.14f), CircleShape),
                    ) {
                        Icon(
                            if (previewing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            if (previewing) "Pause" else "Play",
                            tint = Cyan,
                        )
                    }
                    Button(
                        onClick = {
                            if (applying) return@Button
                            stopPreview()
                            if (selectedTargets.isEmpty()) {
                                status = "Choose at least one: SIM 1, SIM 2, or Alarm"
                                return@Button
                            }
                            if (!RingtoneSetter.canWriteSettings(context)) {
                                status = "Allow modifying system settings, then try again"
                                settingsLauncher.launch(
                                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    },
                                )
                                return@Button
                            }
                            if (android.os.Build.VERSION.SDK_INT <= 28) {
                                val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    permission,
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    status = "Allow storage access, then try again"
                                    storageLauncher.launch(permission)
                                    return@Button
                                }
                            }
                            applying = true
                            status = null
                            val targets = selectedTargets
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    RingtoneSetter.setAsRingtone(
                                        context = context,
                                        song = song,
                                        startMs = startMs,
                                        endMs = endMs,
                                        targets = targets,
                                    )
                                }
                                applying = false
                                result.onSuccess { applied ->
                                    val message = buildString {
                                        append("Set: ${applied.applied.joinToString(", ")}")
                                        if (applied.skipped.isNotEmpty()) {
                                            append("\nSkipped: ${applied.skipped.joinToString("; ")}")
                                        }
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    if (applied.skipped.isEmpty()) onDismiss()
                                    else status = applied.skipped.joinToString("\n")
                                }.onFailure { error ->
                                    status = error.message ?: "Could not set sound"
                                }
                            }
                        },
                        enabled = !applying &&
                            safeDuration >= RingtoneSetter.MIN_CLIP_MS &&
                            selectedTargets.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (applying) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text("Apply", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            status?.let {
                Text(it, Modifier.padding(top = 10.dp), color = Color(0xFFFF8A80), fontSize = 12.sp)
            }
            TextButton(
                onClick = {
                    stopPreview()
                    onDismiss()
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Cancel", color = Muted)
            }
        }
    }
}

private enum class TimelineDrag { Start, End }

/** Visible waveform window — zoomed detail instead of the full track. */
private const val ViewportWindowMs = 30_000L

private fun View.tickHaptic() {
    val type = if (Build.VERSION.SDK_INT >= 34) {
        HapticFeedbackConstants.SEGMENT_TICK
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(type)
}

@Composable
private fun RingtoneTimeline(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    waveformSeed: Int,
    onDragBegin: () -> Unit,
    onStartChange: (Long) -> Unit,
    onStartCommitted: () -> Unit,
    onEndChange: (Long) -> Unit,
    onEndCommitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val view = LocalView.current
    val padPx = with(density) { 18.dp.toPx() }
    val safeDuration = durationMs.coerceAtLeast(1L)
    val viewportDur = minOf(ViewportWindowMs, safeDuration)
    var viewportStartMs by remember(safeDuration) { mutableLongStateOf(0L) }

    val startLatest = rememberUpdatedState(startMs)
    val endLatest = rememberUpdatedState(endMs)
    val viewportLatest = rememberUpdatedState(viewportStartMs)
    val onDragBeginLatest = rememberUpdatedState(onDragBegin)
    val onStartLatest = rememberUpdatedState(onStartChange)
    val onStartCommittedLatest = rememberUpdatedState(onStartCommitted)
    val onEndLatest = rememberUpdatedState(onEndChange)
    val onEndCommittedLatest = rememberUpdatedState(onEndCommitted)

    val bars = remember(waveformSeed, widthPx, viewportStartMs, viewportDur) {
        val count = (widthPx / 4).coerceIn(64, 200)
        val rnd = Random(waveformSeed xor (viewportStartMs / 1_000L).toInt())
        FloatArray(count) { i ->
            val t = viewportStartMs + (i.toLong() * viewportDur / count.coerceAtLeast(1))
            val phase = (t / 180.0) + waveformSeed
            0.18f + abs(sin(phase)).toFloat() * 0.55f + rnd.nextFloat() * 0.28f
        }
    }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val ink = MaterialTheme.colorScheme.onSurface

    fun maxViewportStart(): Long = (safeDuration - viewportDur).coerceAtLeast(0L)

    fun panToKeepVisible(start: Long, end: Long, movingEnd: Boolean) {
        val maxVs = maxViewportStart()
        var vs = viewportStartMs
        if (movingEnd) {
            // Keep End pinned near the right of the zoom window so it can leave 0:30.
            vs = if (end >= viewportDur) {
                (end - viewportDur).coerceIn(0L, maxVs)
            } else {
                0L
            }
            // If Start was pulled forward, keep it in frame when the clip fits.
            if (end - start <= viewportDur && start < vs) {
                vs = start.coerceIn(0L, maxVs)
            }
        } else {
            if (start < vs) vs = start
            if (end > vs + viewportDur) vs = end - viewportDur
            if (end - start <= viewportDur) {
                if (start < vs) vs = start
                if (end > vs + viewportDur) vs = end - viewportDur
            }
            vs = vs.coerceIn(0L, maxVs)
        }
        viewportStartMs = vs.coerceIn(0L, maxVs)
    }

    fun timeFor(x: Float, viewStart: Long = viewportLatest.value): Long {
        if (widthPx <= 0) return viewStart
        val track = (widthPx - padPx * 2).coerceAtLeast(1f)
        val frac = ((x - padPx) / track).coerceIn(0f, 1f)
        return snapToSecond(viewStart + (frac * viewportDur).toLong(), safeDuration)
    }

    fun xFor(timeMs: Long, viewStart: Long = viewportStartMs): Float {
        val track = (widthPx - padPx * 2).coerceAtLeast(1f)
        val frac = ((timeMs - viewStart).toFloat() / viewportDur.toFloat()).coerceIn(0f, 1f)
        return padPx + frac * track
    }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(168.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(16.dp),
                )
                .onSizeChanged { widthPx = it.width }
                .pointerInput(safeDuration, widthPx, viewportDur) {
                    if (widthPx <= 0) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val vs0 = viewportLatest.value
                        val sx = xFor(startLatest.value, vs0)
                        val ex = xFor(endLatest.value, vs0)
                        val mode =
                            if (abs(down.position.x - ex) <= abs(down.position.x - sx)) {
                                TimelineDrag.End
                            } else {
                                TimelineDrag.Start
                            }

                        onDragBeginLatest.value()

                        var lastSec = -1L
                        fun emit(x: Float) {
                            val track = (widthPx - padPx * 2).coerceAtLeast(1f)
                            val edge = track * 0.1f
                            var viewStart = viewportLatest.value
                            val maxVs = (safeDuration - viewportDur).coerceAtLeast(0L)
                            // Edge auto-pan so parked finger at the rim keeps exploring.
                            if (mode == TimelineDrag.End && x >= padPx + track - edge) {
                                viewStart = (viewStart + 1_000L).coerceIn(0L, maxVs)
                                viewportStartMs = viewStart
                            } else if (mode == TimelineDrag.Start && x <= padPx + edge) {
                                viewStart = (viewStart - 1_000L).coerceIn(0L, maxVs)
                                viewportStartMs = viewStart
                            }
                            val t = timeFor(x, viewStart)
                            val sec = t / 1_000L
                            if (sec == lastSec) return
                            lastSec = sec
                            view.tickHaptic()
                            when (mode) {
                                TimelineDrag.Start -> {
                                    onStartLatest.value(t)
                                    panToKeepVisible(t, endLatest.value.coerceAtLeast(t), movingEnd = false)
                                }
                                TimelineDrag.End -> {
                                    onEndLatest.value(t)
                                    // Use requested end + start that applyRange will pull
                                    // (end - maxClip), so the window follows even before recomposition.
                                    val pulledStart = (t - RingtoneSetter.MAX_CLIP_MS).coerceAtLeast(0L)
                                    val visibleStart = minOf(startLatest.value, pulledStart).coerceAtMost(t)
                                    panToKeepVisible(visibleStart, t, movingEnd = true)
                                }
                            }
                        }
                        emit(down.position.x)

                        val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                            if (abs(over.x) >= abs(over.y)) change.consume()
                        }
                        if (slop != null) {
                            emit(slop.position.x)
                            horizontalDrag(slop.id) { change ->
                                change.consume()
                                emit(change.position.x)
                            }
                        }

                        when (mode) {
                            TimelineDrag.Start -> onStartCommittedLatest.value()
                            TimelineDrag.End -> onEndCommittedLatest.value()
                        }
                    }
                },
        ) {
            val trackTop = 16.dp.toPx()
            val trackBottom = size.height - 16.dp.toPx()
            val midY = (trackTop + trackBottom) / 2f
            val waveH = trackBottom - trackTop
            val trackW = (size.width - padPx * 2).coerceAtLeast(1f)
            val viewStart = viewportStartMs
            val startX = xFor(startMs, viewStart)
            val endX = xFor(endMs, viewStart)

            // Second grid inside the zoomed window (one mark per second).
            val firstSec = (viewStart / 1_000L).toInt()
            val lastSec = ((viewStart + viewportDur) / 1_000L).toInt()
            for (sec in firstSec..lastSec) {
                val tx = xFor(sec * 1_000L, viewStart)
                val major = sec % 5 == 0
                drawLine(
                    color = if (major) Cyan.copy(alpha = 0.35f) else dim.copy(alpha = 0.55f),
                    start = Offset(tx, trackBottom - if (major) 10.dp.toPx() else 6.dp.toPx()),
                    end = Offset(tx, trackBottom),
                    strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx(),
                )
            }

            val barW = trackW / bars.size.coerceAtLeast(1)
            bars.forEachIndexed { i, amp ->
                val x = padPx + i * barW + barW / 2f
                val h = waveH * amp.coerceIn(0.12f, 1f) * 0.5f
                val selected = x in startX..endX
                drawLine(
                    color = if (selected) Cyan.copy(alpha = 0.95f) else dim,
                    start = Offset(x, midY - h),
                    end = Offset(x, midY + h),
                    strokeWidth = (barW * 0.55f).coerceAtLeast(1.5f),
                    cap = StrokeCap.Round,
                )
            }

            drawRoundRect(
                color = Color.Black.copy(alpha = 0.28f),
                topLeft = Offset(padPx, trackTop),
                size = Size((startX - padPx).coerceAtLeast(0f), waveH),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.28f),
                topLeft = Offset(endX, trackTop),
                size = Size((padPx + trackW - endX).coerceAtLeast(0f), waveH),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawRoundRect(
                color = Cyan.copy(alpha = 0.32f),
                topLeft = Offset(startX, trackTop),
                size = Size((endX - startX).coerceAtLeast(0f), waveH),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )

            fun drawHandle(x: Float) {
                drawLine(
                    color = Cyan,
                    start = Offset(x, trackTop - 2.dp.toPx()),
                    end = Offset(x, trackBottom + 2.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawRoundRect(
                    color = Cyan,
                    topLeft = Offset(x - 11.dp.toPx(), midY - 24.dp.toPx()),
                    size = Size(22.dp.toPx(), 48.dp.toPx()),
                    cornerRadius = CornerRadius(11.dp.toPx()),
                )
                drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(x, midY - 8.dp.toPx()))
                drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(x, midY + 8.dp.toPx()))
            }
            drawHandle(startX)
            drawHandle(endX)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(viewportStartMs), color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Song ${formatTime(safeDuration)}",
                color = Muted,
                fontSize = 11.sp,
            )
            Text(
                formatTime(viewportStartMs + viewportDur),
                color = ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
