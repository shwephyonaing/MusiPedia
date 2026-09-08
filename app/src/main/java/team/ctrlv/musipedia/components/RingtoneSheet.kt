package team.ctrlv.musipedia

import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
private val Cyan = Color(0xFF42E4CE)
private val SheetInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Muted = Color(0xFF9FA8A3)

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
    var playheadMs by remember { mutableLongStateOf(0L) }
    var previewing by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mediaPlayer.stop()
                mediaPlayer.release()
            }
        }
    }

    fun stopPreview() {
        previewing = false
        runCatching { if (mediaPlayer.isPlaying) mediaPlayer.pause() }
    }

    fun startPreview(file: File) {
        if (player.playing) player.togglePlay()
        runCatching {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.seekTo(startMs.toInt().coerceAtLeast(0))
            mediaPlayer.start()
            playheadMs = startMs
            previewing = true
        }.onFailure {
            previewing = false
            status = "Could not preview this clip"
        }
    }

    LaunchedEffect(previewing, startMs, endMs) {
        if (!previewing) return@LaunchedEffect
        while (previewing) {
            val pos = runCatching { mediaPlayer.currentPosition.toLong() }.getOrDefault(startMs)
            if (pos >= endMs - 40L || !mediaPlayer.isPlaying) {
                runCatching {
                    mediaPlayer.seekTo(startMs.toInt().coerceAtLeast(0))
                    mediaPlayer.start()
                }
                playheadMs = startMs
            } else {
                playheadMs = pos.coerceIn(startMs, endMs)
            }
            delay(80)
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (RingtoneSetter.canWriteSettings(context)) {
            status = "Permission granted — tap Set ringtone"
        } else {
            status = "Write settings permission is required"
        }
    }
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        status = if (granted) "Storage allowed — tap Set ringtone"
        else "Storage permission is required on this Android version"
    }

    fun applyRange(nextStart: Long, nextEnd: Long) {
        val maxLen = RingtoneSetter.MAX_CLIP_MS
        val minLen = minOf(RingtoneSetter.MIN_CLIP_MS, safeDuration)
        var start = nextStart.coerceIn(0L, safeDuration)
        var end = nextEnd.coerceIn(0L, safeDuration)
        if (end < start) {
            val t = start
            start = end
            end = t
        }
        if (end - start < minLen) {
            if (start + minLen <= safeDuration) end = start + minLen
            else start = (end - minLen).coerceAtLeast(0L)
        }
        if (end - start > maxLen) {
            // Keep the handle the user last moved by shrinking the opposite side later in UI.
            end = start + maxLen
            if (end > safeDuration) {
                end = safeDuration
                start = (end - maxLen).coerceAtLeast(0L)
            }
        }
        startMs = start
        endMs = end
    }

    PlayerOverlaySheet(onDismiss = {
        stopPreview()
        onDismiss()
    }, heightFraction = 0.62f) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = Cyan, modifier = Modifier.size(22.dp))
                Text(
                    "Set ringtone",
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
                Text(
                    "Drag the line handles to choose start and end.",
                    Modifier.padding(top = 14.dp),
                    Muted,
                    13.sp,
                )
                RingtoneRangeLine(
                    startMs = startMs,
                    endMs = endMs,
                    durationMs = safeDuration,
                    playheadMs = if (previewing) playheadMs else null,
                    onRangeChange = { s, e ->
                        stopPreview()
                        applyRange(s, e)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatTime(startMs), color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        formatTime(endMs - startMs),
                        color = SheetInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(formatTime(endMs), color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Clip ${RingtoneSetter.MIN_CLIP_MS / 1000}–${RingtoneSetter.MAX_CLIP_MS / 1000}s",
                    Modifier.padding(top = 2.dp),
                    Muted,
                    11.sp,
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (previewing) stopPreview() else startPreview(localFile)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Cyan.copy(alpha = 0.14f), CircleShape),
                    ) {
                        Icon(
                            if (previewing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            if (previewing) "Pause preview" else "Preview clip",
                            tint = Cyan,
                        )
                    }
                    Button(
                        onClick = {
                            if (applying) return@Button
                            stopPreview()
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
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    RingtoneSetter.setAsRingtone(context, song, startMs, endMs)
                                }
                                applying = false
                                result.onSuccess {
                                    Toast.makeText(context, "Ringtone set", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }.onFailure { error ->
                                    status = error.message ?: "Could not set ringtone"
                                }
                            }
                        },
                        enabled = !applying && safeDuration >= RingtoneSetter.MIN_CLIP_MS,
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
                            Text("Set ringtone", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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

@Composable
private fun RingtoneRangeLine(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    playheadMs: Long?,
    onRangeChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val thumbRadiusPx = with(LocalDensity.current) { 10.dp.toPx() }
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val safeDuration = durationMs.coerceAtLeast(1L)

    fun timeFor(x: Float): Long {
        if (widthPx <= 0) return 0L
        val trackWidth = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)
        val fraction = ((x - thumbRadiusPx) / trackWidth).coerceIn(0f, 1f)
        return (fraction * safeDuration).toLong()
    }

    fun xFor(timeMs: Long): Float {
        val trackWidth = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)
        return thumbRadiusPx + (timeMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) * trackWidth
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(safeDuration, widthPx, startMs, endMs) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startX = xFor(startMs)
                    val endX = xFor(endMs)
                    val touchingStart = abs(down.position.x - startX) <= abs(down.position.x - endX)
                    var s = startMs
                    var e = endMs
                    fun update(x: Float) {
                        val t = timeFor(x)
                        if (touchingStart) {
                            s = t.coerceIn(0L, (e - RingtoneSetter.MIN_CLIP_MS).coerceAtLeast(0L))
                            if (e - s > RingtoneSetter.MAX_CLIP_MS) e = s + RingtoneSetter.MAX_CLIP_MS
                        } else {
                            e = t.coerceIn(
                                (s + RingtoneSetter.MIN_CLIP_MS).coerceAtMost(safeDuration),
                                safeDuration,
                            )
                            if (e - s > RingtoneSetter.MAX_CLIP_MS) s = (e - RingtoneSetter.MAX_CLIP_MS).coerceAtLeast(0L)
                        }
                        onRangeChange(s, e)
                    }
                    update(down.position.x)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position.x)
                    }
                }
            },
    ) {
        val trackHeight = 8.dp.toPx()
        val thumbRadius = 10.dp.toPx()
        val trackStart = thumbRadius
        val trackWidth = (size.width - thumbRadius * 2).coerceAtLeast(0f)
        val trackTop = (size.height - trackHeight) / 2
        val startX = trackStart + (startMs.toFloat() / safeDuration) * trackWidth
        val endX = trackStart + (endMs.toFloat() / safeDuration) * trackWidth

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(trackStart, trackTop),
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2),
        )
        drawRoundRect(
            color = Cyan.copy(alpha = 0.28f),
            topLeft = Offset(startX, trackTop),
            size = Size((endX - startX).coerceAtLeast(0f), trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2),
        )
        drawRoundRect(
            color = Cyan,
            topLeft = Offset(startX, trackTop),
            size = Size((endX - startX).coerceAtLeast(0f), trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2),
        )
        playheadMs?.let { head ->
            val hx = trackStart + (head.toFloat() / safeDuration) * trackWidth
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(hx, size.height / 2))
            drawCircle(color = Cyan, radius = 3.dp.toPx(), center = Offset(hx, size.height / 2))
        }
        drawCircle(color = Color.White, radius = thumbRadius + 1.5f, center = Offset(startX, size.height / 2))
        drawCircle(color = Cyan, radius = thumbRadius, center = Offset(startX, size.height / 2))
        drawCircle(color = Color.White, radius = thumbRadius + 1.5f, center = Offset(endX, size.height / 2))
        drawCircle(color = Cyan, radius = thumbRadius, center = Offset(endX, size.height / 2))
    }
}
