package com.musium.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private val PlayerCyan = Color(0xFF18CDE0)
private val PlayerBlack = Color(0xFF101010)

@Composable
internal fun LocalMusicLibrary() {
    val context = LocalContext.current
    val player = LocalPlayerConnection.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    var songs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var recents by remember { mutableStateOf<List<PlayableSong>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PlayableSong?>(null) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val recentsStore = (context.applicationContext as? MusiumApplication)?.recentStore

    LaunchedEffect(player?.current?.id, player?.playing) {
        recents = recentsStore?.songs().orEmpty()
    }
    LaunchedEffect(allowed) {
        if (allowed) {
            loading = true
            songs = LocalMusicRepository(context.applicationContext).songs()
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().background(PlayerBlack)) {
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(top = 54.dp, bottom = 170.dp),
    ) {
        item {
            Row(Modifier.padding(start = 72.dp, end = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.height(28.dp).width(32.dp), PlayerCyan)
                Text("Your Music", Modifier.padding(start = 12.dp), color = PlayerCyan, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        OfflineDownloads.lastError?.let { error ->
            item {
                Text(error, Modifier.padding(28.dp, 16.dp, 28.dp, 0.dp), color = Color(0xFFFF8A80), fontSize = 13.sp)
            }
        }
        val downloads = (OfflineDownloads.songs + OfflineDownloads.inFlight.values)
            .distinctBy { it.id }
        if (downloads.isNotEmpty()) {
            item { Text("Downloads", Modifier.padding(28.dp, 24.dp, 28.dp, 10.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            items(downloads, key = { "offline-${it.id}" }) { song ->
                PlayableRow(
                    song = song,
                    onClick = {
                        val queue = OfflineDownloads.songs
                        player?.play(queue, queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                    },
                    trailing = {
                        if (song.id in OfflineDownloads.progressing) {
                            CircularProgressIndicator(
                                progress = { OfflineDownloads.progress[song.id] ?: 0f },
                                color = PlayerCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = { pendingDelete = song }) {
                            Icon(Icons.Outlined.Close, "Remove download", tint = Color(0xFF9FAEB1))
                        }
                    },
                )
            }
        }
        if (recents.isNotEmpty()) {
            item { Text("Recently played", Modifier.padding(28.dp, 24.dp, 28.dp, 10.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            items(recents, key = { "recent-${it.id}" }) { song ->
                PlayableRow(song, onClick = { player?.play(recents, recents.indexOfFirst { it.id == song.id }.coerceAtLeast(0)) })
            }
        }
        when {
            !allowed -> item {
                Button(onClick = { requestPermission.launch(permission) }, Modifier.padding(28.dp)) { Text("Allow") }
            }
            loading -> item { CircularProgressIndicator(Modifier.padding(28.dp), color = PlayerCyan) }
            songs.isEmpty() -> { }
            else -> {
                item { Text("On this phone", Modifier.padding(28.dp, 24.dp, 28.dp, 10.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                items(songs, key = { "local-${it.id}" }) { song ->
                    LocalSongRow(song) {
                        val queue = songs.map { it.toPlayable() }
                        player?.play(queue, songs.indexOf(song).coerceAtLeast(0))
                    }
                }
            }
        }
    }
    pendingDelete?.let { song ->
        DeleteDownloadConfirmDialog(
            songTitle = song.title,
            onConfirm = {
                OfflineDownloads.delete(song.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
    }
}

@Composable
private fun LocalSongRow(song: LocalSong, onClick: () -> Unit) {
    PlayableRow(song.toPlayable(), onClick = onClick)
}

@Composable
private fun PlayableRow(
    song: PlayableSong,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(song.thumbnailUrl, null, Modifier.size(58.dp).clip(RoundedCornerShape(6.dp)).background(Color.DarkGray), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(song.artist, color = Color.LightGray, fontSize = 13.sp, maxLines = 1)
        }
        if (trailing != null) trailing() else Icon(Icons.Outlined.PlayArrow, "Play", tint = PlayerCyan)
    }
}
