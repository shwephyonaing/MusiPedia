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
fun MusiumHomeScreen(onOpenAllScreens: () -> Unit = {}) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(PageBlack)) {
        when (selectedTab) {
            0 -> HomeContent(onOpenAllScreens)
            1 -> ExploreScreen()
            else -> LibraryScreen()
        }
        BottomNavigation(selectedTab, { selectedTab = it }, Modifier.align(Alignment.BottomCenter))
    }
}


