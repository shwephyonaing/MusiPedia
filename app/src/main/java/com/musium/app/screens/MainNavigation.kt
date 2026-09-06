package com.musium.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val PageBlack = Color(0xFF0B0B0B)

@Composable
fun MusiumHomeScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var destination by remember { mutableStateOf<MusicRoute?>(null) }
    var stack by remember { mutableStateOf<List<MusicRoute>>(emptyList()) }
    var fullPlayer by remember { mutableStateOf(false) }
    val showTabs = destination == null

    val router: (MusicRoute) -> Unit = { route ->
        destination?.let { stack = stack + it }
        destination = route
    }

    fun clearBrowse() {
        destination = null
        stack = emptyList()
    }

    fun goBack(): Boolean {
        when {
            fullPlayer -> fullPlayer = false
            destination != null -> {
                destination = stack.lastOrNull()
                stack = stack.dropLast(1)
            }
            selectedTab != 0 -> selectedTab = 0
            else -> return false
        }
        return true
    }

    BackHandler(enabled = fullPlayer || destination != null || selectedTab != 0) { goBack() }

    CompositionLocalProvider(LocalMusicRouter provides router) {
        Box(Modifier.fillMaxSize().background(PageBlack)) {
            if ((selectedTab == 0 || selectedTab == 1) && showTabs) HomeContent()
            KeepAlive(visible = selectedTab == 1 && showTabs, animateFromBottom = true) {
                Box(Modifier.fillMaxSize()) {
                    ExploreScreen(onBack = { selectedTab = 0 })
                    MiniPlayer(
                        onOpen = { fullPlayer = true },
                        modifier = Modifier.align(Alignment.BottomCenter),
                        protectBottom = true,
                    )
                }
            }
            if (selectedTab == 2 && showTabs) LibraryScreen()
            if (selectedTab == 3 && showTabs) SettingsScreen()
            if (!fullPlayer) {
                when (val route = destination) {
                    is MusicRoute.Artist -> ArtistScreen(route.id, route.name, onBack = { goBack() })
                    is MusicRoute.Album -> AlbumScreen(route.id, route.name, onBack = { goBack() })
                    is MusicRoute.Playlist -> PlaylistScreen(route.id, route.name, onBack = { goBack() })
                    null -> Unit
                }
            }
            KeepAlive(visible = fullPlayer, animateFromBottom = true) {
                FullPlayerScreen(onBack = { fullPlayer = false })
            }
            if (!fullPlayer && selectedTab == 0 && destination == null) {
                Column(Modifier.align(Alignment.BottomCenter)) {
                    MiniPlayer(onOpen = { fullPlayer = true })
                    BottomNavigation(selectedTab, onSelected = { tab ->
                        if (tab != selectedTab) {
                            clearBrowse()
                            fullPlayer = false
                        }
                        selectedTab = tab
                    })
                }
            }
            if (!fullPlayer && selectedTab != 0 && selectedTab != 1 && destination == null) {
                IconButton(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 28.dp, top = 24.dp)
                        .size(38.dp).background(Color(0xFFE1E5E2), CircleShape),
                ) {
                    Icon(Icons.Outlined.KeyboardArrowDown, "Back to Home", tint = Color(0xFF727A76))
                }
            }
        }
    }
}

@Composable
private fun KeepAlive(visible: Boolean, animateFromBottom: Boolean = false, content: @Composable () -> Unit) {
    var keepFullSize by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) keepFullSize = true
    }
    val slideProgress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "secondaryScreenSlide",
        finishedListener = {
            if (!visible) keepFullSize = false
        },
    )
    val modifier = when {
        (visible || keepFullSize) && animateFromBottom -> Modifier.fillMaxSize().graphicsLayer {
            translationY = size.height * slideProgress
            alpha = 1f - slideProgress
        }
        visible || keepFullSize -> Modifier.fillMaxSize()
        else -> Modifier.size(0.dp)
    }
    Box(modifier.clipToBounds()) {
        content()
    }
}
