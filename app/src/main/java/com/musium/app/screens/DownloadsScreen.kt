package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Cyan = Color(0xFF42E4CE)
private val Page = Color(0xFFFAFAF8)
private val Tile = Color(0xFFF0F0EC)
private val Ink = Color(0xFF3F4944)

@Composable
internal fun DownloadsScreen(onBack: () -> Unit, onOpenPlayer: () -> Unit) {
    val player = LocalPlayerConnection.current
    val downloads = (OfflineDownloads.inFlight.values + OfflineDownloads.songs).distinctBy { it.id }
    Box(Modifier.fillMaxSize().background(Page)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Row(
                    Modifier.statusBarsPadding().padding(start = 8.dp, end = 26.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Ink)
                    }
                    Text("Downloads", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
            OfflineDownloads.lastError?.let { error ->
                item {
                    Text(error, Modifier.padding(26.dp, 8.dp), color = Color(0xFFB42318), fontSize = 13.sp)
                }
            }
            if (downloads.isEmpty()) {
                item {
                    Text(
                        "Nothing downloaded yet",
                        Modifier.padding(26.dp, 16.dp),
                        color = Ink,
                        fontSize = 16.sp,
                    )
                }
            } else {
                items(downloads, key = { it.id }) { song ->
                    DownloadRow(song) {
                        val queue = OfflineDownloads.songs
                        player?.play(queue, queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                        onOpenPlayer()
                    }
                }
            }
        }
        MiniPlayer(onOpen = onOpenPlayer, modifier = Modifier.align(Alignment.BottomCenter), protectBottom = true)
    }
}

@Composable
private fun DownloadRow(song: PlayableSong, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 26.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            song.thumbnailUrl,
            song.title,
            Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Tile),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(song.title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(song.artist, color = Color(0xFF8C9690), fontSize = 13.sp, maxLines = 1)
        }
        if (song.id in OfflineDownloads.progressing) {
            CircularProgressIndicator(
                progress = { OfflineDownloads.progress[song.id] ?: 0f },
                color = Cyan,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            IconButton(onClick = { OfflineDownloads.delete(song.id) }) {
                Icon(Icons.Outlined.Close, "Remove download", tint = Color(0xFF8C9690))
            }
        }
    }
}
