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
internal fun BottomNavigation(selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(94.dp).background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.92f), Color.Black))
        ).padding(horizontal = 34.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        NavItem("Home", Icons.Outlined.Home, selected == 0) { onSelected(0) }
        NavItem("Explore", Icons.Outlined.Search, selected == 1) { onSelected(1) }
        NavItem("Library", Icons.Outlined.VideoLibrary, selected == 2) { onSelected(2) }
    }
}

@Composable
internal fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Cyan else Color.White
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}


