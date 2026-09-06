package com.musium.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

private val Cyan = Color(0xFF00C2CB)
private val PageBlack = Color(0xFF0B0B0B)
private val Tile = Color(0xFF172023)

@Composable
internal fun LibraryScreen() {
    LocalMusicLibrary()
}

@Composable
internal fun FilterChip(label: String) {
    Text(label, Modifier.border(1.dp, Color.White, RoundedCornerShape(23.dp)).padding(horizontal = 17.dp, vertical = 6.dp), Color.White, 12.sp)
}

@Composable
internal fun LibraryAction(symbol: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 29.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF72E5F1), Cyan))), contentAlignment = Alignment.Center) {
            Text(symbol, color = Color(0xFF071A1C), fontSize = 32.sp, fontWeight = FontWeight.Light)
        }
        Text(label, Modifier.padding(start = 25.dp), Color.White, 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
    }
}

@Composable
internal fun LibraryRow(item: LibraryItem) {
    Row(Modifier.padding(horizontal = 28.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(item.image), null,
            Modifier.size(84.dp).clip(if (item.round) CircleShape else RoundedCornerShape(5.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(start = 21.dp)) {
            Text(item.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            item.subtitle?.let { Text(it, Modifier.padding(top = 2.dp), Color(0xFF8A9A9D), 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

