package team.ctrlv.musipedia

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val Cyan = Color(0xFF42E4CE)

@Composable
fun MusiumHomeScreen() {
    val context = LocalContext.current
    val appearancePreferences = remember {
        context.applicationContext.getSharedPreferences("musium_appearance", android.content.Context.MODE_PRIVATE)
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var destination by remember { mutableStateOf<MusicRoute?>(null) }
    var stack by remember { mutableStateOf<List<MusicRoute>>(emptyList()) }
    var fullPlayer by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(appearancePreferences.getBoolean("dark_mode", false)) }
    val showTabs = destination == null

    LaunchedEffect(Unit) {
        if (!context.isNetworkAvailable()) {
            showOfflineDialog = true
        }
    }

    fun clearBrowse() {
        destination = null
        stack = emptyList()
    }

    val router: (MusicRoute) -> Unit = { route ->
        when (route) {
            MusicRoute.Favorites -> {
                clearBrowse()
                fullPlayer = false
                showDownloads = false
                selectedTab = 2
            }
            MusicRoute.Downloads -> {
                clearBrowse()
                fullPlayer = false
                showDownloads = true
            }
            else -> {
                fullPlayer = false
                showDownloads = false
                destination?.let { stack = stack + it }
                destination = route
            }
        }
    }

    fun goBack(): Boolean {
        when {
            fullPlayer -> fullPlayer = false
            showDownloads -> showDownloads = false
            destination != null -> {
                destination = stack.lastOrNull()
                stack = stack.dropLast(1)
            }
            selectedTab != 0 -> selectedTab = 0
            else -> return false
        }
        return true
    }

    BackHandler(enabled = fullPlayer || showDownloads || destination != null || selectedTab != 0) { goBack() }

    MusiumTheme(darkMode = darkMode) {
    CompositionLocalProvider(LocalMusicRouter provides router) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (selectedTab in 0..3 && showTabs) HomeContent()
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
            KeepAlive(visible = selectedTab == 2 && showTabs, animateFromBottom = true) {
                Box(Modifier.fillMaxSize()) {
                    LibraryScreen(
                        active = selectedTab == 2 && showTabs,
                        onBack = { selectedTab = 0 },
                        onExplore = { selectedTab = 0 },
                    )
                    MiniPlayer(
                        onOpen = { fullPlayer = true },
                        modifier = Modifier.align(Alignment.BottomCenter),
                        protectBottom = true,
                    )
                }
            }
            KeepAlive(visible = selectedTab == 3 && showTabs, animateFromBottom = true) {
                SettingsScreen(
                    darkMode = darkMode,
                    onDarkModeChange = { enabled ->
                        darkMode = enabled
                        appearancePreferences.edit().putBoolean("dark_mode", enabled).apply()
                    },
                    onBack = { selectedTab = 0 },
                    onDownloads = { showDownloads = true },
                )
            }
            KeepAlive(visible = showDownloads, animateFromBottom = true) {
                Box(Modifier.fillMaxSize()) {
                    DownloadsScreen(darkMode = darkMode, onBack = { showDownloads = false })
                    MiniPlayer(
                        onOpen = { fullPlayer = true },
                        modifier = Modifier.align(Alignment.BottomCenter),
                        protectBottom = true,
                    )
                }
            }
            if (!fullPlayer && destination != null) {
                Box(Modifier.fillMaxSize()) {
                    when (val route = destination) {
                        is MusicRoute.Artist -> ArtistScreen(
                            route.id,
                            route.name,
                            route.profileImage,
                            onBack = { goBack() },
                        )
                        is MusicRoute.Album -> AlbumScreen(route.id, route.name, onBack = { goBack() })
                        is MusicRoute.Playlist -> PlaylistScreen(route.id, route.name, onBack = { goBack() })
                        MusicRoute.Downloads -> DownloadsScreen(darkMode = darkMode, onBack = { goBack() })
                        MusicRoute.Favorites -> Unit
                        null -> Unit
                    }
                    MiniPlayer(
                        onOpen = { fullPlayer = true },
                        modifier = Modifier.align(Alignment.BottomCenter),
                        protectBottom = true,
                    )
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
            if (showOfflineDialog) {
                OfflineConnectionDialog(
                    onDismiss = { showOfflineDialog = false },
                    onOpenDownloads = {
                        showOfflineDialog = false
                        clearBrowse()
                        fullPlayer = false
                        showDownloads = true
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun OfflineConnectionDialog(
    onDismiss: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(Cyan.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "No internet connection",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Enjoy listening to your downloaded songs.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onOpenDownloads,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Go to Downloads", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            TextButton(onClick = onDismiss) {
                Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun Context.isNetworkAvailable(): Boolean {
    val manager = getSystemService(ConnectivityManager::class.java) ?: return true
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
    Box(
        modifier
            .clipToBounds()
            .then(
                if (visible || keepFullSize) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}
