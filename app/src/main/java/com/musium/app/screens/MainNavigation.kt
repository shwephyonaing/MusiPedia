package com.musium.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PageBlack = Color(0xFF0B0B0B)

@Composable
fun MusiumHomeScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var destination by remember { mutableStateOf<MusicRoute?>(null) }
    var stack by remember { mutableStateOf<List<MusicRoute>>(emptyList()) }
    var fullPlayer by remember { mutableStateOf(false) }
    val showTabs = !fullPlayer && destination == null

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
            else -> return false
        }
        return true
    }

    BackHandler(enabled = fullPlayer || destination != null) { goBack() }

    CompositionLocalProvider(LocalMusicRouter provides router) {
        Box(Modifier.fillMaxSize().background(PageBlack)) {
            if (selectedTab == 0 && showTabs) HomeContent()
            KeepAlive(visible = selectedTab == 1 && showTabs) {
                ExploreScreen()
            }
            if (selectedTab == 2 && showTabs) LibraryScreen()
            if (!fullPlayer) {
                when (val route = destination) {
                    is MusicRoute.Artist -> ArtistScreen(route.id, route.name, onBack = { goBack() })
                    is MusicRoute.Album -> AlbumScreen(route.id, route.name, onBack = { goBack() })
                    is MusicRoute.Playlist -> PlaylistScreen(route.id, route.name, onBack = { goBack() })
                    null -> Unit
                }
            }
            if (fullPlayer) FullPlayerScreen(onBack = { fullPlayer = false })
            if (!fullPlayer) {
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
        }
    }
}

@Composable
private fun KeepAlive(visible: Boolean, content: @Composable () -> Unit) {
    Box(if (visible) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
        content()
    }
}
