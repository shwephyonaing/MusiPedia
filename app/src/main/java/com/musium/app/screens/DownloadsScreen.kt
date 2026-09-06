package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val DownloadAqua = Color(0xFF42E4CE)
private val DownloadInk = Color(0xFF414944)

@Composable
internal fun DownloadsScreen(darkMode: Boolean = false, onBack: () -> Unit) {
    val player = LocalPlayerConnection.current
    val downloads = (OfflineDownloads.songs + OfflineDownloads.inFlight.values).distinctBy { it.id }
    var pendingDelete by remember { mutableStateOf<PlayableSong?>(null) }
    val surface = if (darkMode) Color(0xFF121413) else Color.White
    val textColor = if (darkMode) Color(0xFFF2F4F3) else DownloadInk
    val mutedColor = if (darkMode) Color(0xFF8E9993) else Color(0xFF909994)
    val tileColor = if (darkMode) Color(0xFF292D2B) else Color(0xFFF0F1EF)
    val backColor = if (darkMode) Color(0xFF303532) else Color(0xFFE1E5E2)

    Box(Modifier.fillMaxSize().background(surface).safeDrawingPadding()) {
        if (downloads.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Download, null, tint = DownloadAqua, modifier = Modifier.size(88.dp))
                Spacer(Modifier.height(28.dp))
                Text("No Downloads", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Downloaded songs will appear here",
                    Modifier.padding(top = 9.dp),
                    color = mutedColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 82.dp, bottom = 118.dp)) {
                item {
                    Text(
                        "Downloads",
                        Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                        color = textColor,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                    )
                }
                OfflineDownloads.lastError?.let { error ->
                    item {
                        Text(error, Modifier.padding(horizontal = 28.dp, vertical = 8.dp), color = Color(0xFFFF6464), fontSize = 12.sp)
                    }
                }
                items(downloads, key = { it.id }) { song ->
                    val downloading = song.id in OfflineDownloads.progressing
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !downloading) {
                                val queue = OfflineDownloads.songs
                                val index = queue.indexOfFirst { it.id == song.id }
                                if (index >= 0) player?.play(queue, index)
                            }
                            .padding(horizontal = 24.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = song.title,
                            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(tileColor),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = textColor, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                if (downloading) "Downloading…" else song.artist,
                                color = mutedColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                        if (downloading) {
                            CircularProgressIndicator(
                                progress = { OfflineDownloads.progress[song.id] ?: 0f },
                                color = DownloadAqua,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        IconButton(onClick = { pendingDelete = song }) {
                            Icon(Icons.Outlined.Close, "Remove download", tint = mutedColor)
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 24.dp)
                .size(38.dp).background(backColor, CircleShape),
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, "Back to Settings", tint = if (darkMode) Color.White else Color(0xFF727A76))
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
