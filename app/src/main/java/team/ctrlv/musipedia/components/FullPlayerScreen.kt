package team.ctrlv.musipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import team.ctrlv.musipedia.innertube.Lyrics
import team.ctrlv.musipedia.innertube.hdArtwork
import team.ctrlv.musipedia.innertube.hdProfileArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.foundation.interaction.MutableInteractionSource

private val Cyan = Color(0xFF42E4CE)
private val Black: Color @Composable get() = MaterialTheme.colorScheme.background
private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground

@Composable
internal fun FullPlayerScreen(onBack: () -> Unit) {
    val player = LocalPlayerConnection.current
    val router = LocalMusicRouter.current
    val scope = rememberCoroutineScope()
    val song = player?.current
    if (player == null || song == null) {
        Column(Modifier.fillMaxSize().background(Black).safeDrawingPadding().padding(28.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.KeyboardArrowDown, "Back", tint = Ink) }
            Text("Nothing is playing", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var lyrics by remember(song.id) { mutableStateOf<Lyrics?>(null) }
    var lyricsOn by remember(song.id) { mutableStateOf(false) }
    var showLyricSync by remember(song.id) { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showRingtone by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var openingArtist by remember { mutableStateOf(false) }
    var confirmRemoveDownload by remember { mutableStateOf(false) }
    var artistPhoto by remember(song.id) { mutableStateOf<String?>(null) }
    var creatorName by remember(song.id) { mutableStateOf(song.artist) }
    var creatorChannelId by remember(song.id) { mutableStateOf(song.artistId?.takeIf { it.startsWith("UC") }) }
    val app = LocalContext.current.applicationContext as? MusiumApplication
    val favoriteStore = app?.favoriteStore
    val catalogStore = app?.tasteCatalogStore
    val lyricsOffsetStore = app?.lyricsOffsetStore
    var lyricOffsetMs by remember(song.id) {
        mutableLongStateOf(lyricsOffsetStore?.get(song.id) ?: 0L)
    }
    var favorite by remember(song.id) { mutableStateOf(favoriteStore?.contains(song.id) == true) }
    val isVerifiedArtist = catalogStore?.isVerified(creatorChannelId) == true ||
        catalogStore?.matching(creatorName)?.any {
            it.name.equals(creatorName, ignoreCase = true)
        } == true
    val canOpenCreator = !song.isLocal && song.id.length == 11
    fun openCreatorProfile() {
        if (!canOpenCreator || openingArtist) return
        showActions = false
        openingArtist = true
        scope.launch {
            val uploader = runCatching {
                withContext(Dispatchers.IO) { MusicRepository.videoUploader(song.id) }
            }.getOrNull()
            // Prefer the video's real channelId; never guess via name search.
            val channelId = uploader?.channelId
                ?: song.artistId?.takeIf { it.startsWith("UC") }
            val name = uploader?.name?.takeIf { it.isNotBlank() } ?: creatorName
            if (uploader?.name != null) creatorName = uploader.name
            if (channelId != null) creatorChannelId = channelId
            val photo = when {
                channelId != null ->
                    artistPhoto
                        ?: runCatching { MusicRepository.artistAvatar(channelId, name) }.getOrNull()
                        ?: song.thumbnailUrl?.hdArtwork()
                else -> song.thumbnailUrl?.hdArtwork()
            }
            openingArtist = false
            if (channelId != null) {
                router(MusicRoute.Artist(channelId, name, photo))
            }
        }
    }
    LaunchedEffect(song.id, player.playing, lyricsOn) {
        while (true) {
            if (!dragging) position = player.currentPosition()
            delay(if (lyricsOn) 200 else 400)
        }
    }
    LaunchedEffect(song.id) {
        lyricsOn = false
        showLyricSync = false
        lyrics = runCatching { LyricsResolver.load(song, player.duration) }.getOrNull()
        lyricOffsetMs = lyricsOffsetStore?.get(song.id) ?: 0L
    }
    LaunchedEffect(song.id, canOpenCreator) {
        artistPhoto = null
        creatorName = song.artist
        creatorChannelId = song.artistId?.takeIf { it.startsWith("UC") }
        if (!canOpenCreator) return@LaunchedEffect
        val uploader = runCatching { MusicRepository.videoUploader(song.id) }.getOrNull()
        val channelId = uploader?.channelId ?: song.artistId?.takeIf { it.startsWith("UC") }
        if (uploader?.name != null) creatorName = uploader.name
        if (channelId != null) creatorChannelId = channelId
        artistPhoto = runCatching {
            MusicRepository.artistAvatar(channelId, creatorName)
        }.getOrNull()
    }
    val duration = player.duration.coerceAtLeast(1L)
    val sheetOpen = showQueue || showSleep || showEqualizer || showRingtone
    Box(
        Modifier
            .fillMaxSize()
            .background(Black)
            // Block taps from falling through to Home/charts underneath this overlay.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Upper content can scroll/shrink; transport stays pinned below.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val artHeight = maxWidth.coerceIn(180.dp, 300.dp)
            Box(Modifier.fillMaxWidth().height(artHeight)) {
            if (lyricsOn && lyrics != null) {
                LyricsPanel(
                    lyrics = lyrics!!,
                    positionMs = position,
                    offsetMs = lyricOffsetMs,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                ) { time ->
                    player.seekTo(time)
                    position = time
                }
            } else {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = song.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .92f), CircleShape).size(38.dp),
            ) {
                Icon(Icons.Outlined.KeyboardArrowDown, "Back", tint = Ink, modifier = Modifier.size(28.dp))
            }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (player.sleepActive) {
                    Text(
                        if (player.sleepEndOfTrack) "Sleep" else formatTime(player.sleepRemaining),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (lyricsOn && lyrics?.synced == true) {
                    IconButton(
                        onClick = { showLyricSync = !showLyricSync },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = .92f), CircleShape)
                            .size(38.dp),
                    ) {
                        Icon(Icons.Outlined.Edit, "Adjust lyrics sync", tint = Ink, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (lyricsOn && lyrics?.synced == true && showLyricSync) {
                LyricsSyncBar(
                    offsetMs = lyricOffsetMs,
                    onOffsetChange = { next ->
                        lyricOffsetMs = next
                        lyricsOffsetStore?.set(song.id, next)
                    },
                    onDismiss = { showLyricSync = false },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 58.dp, start = 16.dp, end = 16.dp),
                )
            }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
            Text(
                song.title,
                Modifier.weight(1f),
                color = Ink,
                fontSize = 25.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                maxLines = 2,
            )
            Box {
                val downloading = song.id in OfflineDownloads.progressing
                val lyricsAvailable = lyrics != null
                IconButton(
                    onClick = {
                        if (downloading) return@IconButton
                        favorite = favoriteStore?.contains(song.id) == true
                        showActions = true
                    },
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            progress = { OfflineDownloads.progress[song.id] ?: 0f },
                            color = Cyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.MoreHoriz, "More", tint = Ink)
                            if (lyricsAvailable) {
                                RedDot(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-1).dp),
                                )
                            }
                        }
                    }
                }
                DropdownMenu(
                    expanded = showActions && !downloading,
                    onDismissRequest = { showActions = false },
                    modifier = Modifier.width(214.dp),
                    offset = DpOffset(x = (-174).dp, y = (-4).dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 7.dp,
                ) {
                    if (song.canDownload) {
                        DropdownMenuItem(
                            modifier = Modifier.height(44.dp),
                            text = {
                                Text(
                                    if (OfflineDownloads.has(song.id)) "Remove download" else "Download",
                                    color = Ink,
                                    fontSize = 12.sp,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (OfflineDownloads.has(song.id)) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
                                    null,
                                    tint = Color(0xFF8B9490),
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 13.dp),
                            onClick = {
                                if (OfflineDownloads.has(song.id)) {
                                    confirmRemoveDownload = true
                                    showActions = false
                                } else {
                                    OfflineDownloads.download(song)
                                    showActions = false
                                }
                            },
                        )
                        HorizontalDivider(color = Color(0xFFE7E9E8), thickness = 0.7.dp)
                    }
                    DropdownMenuItem(
                        modifier = Modifier.height(44.dp),
                        text = {
                            Text(
                                if (favorite) "Remove from Favorites" else "Add to Favorites",
                                color = Ink,
                                fontSize = 12.sp,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                null,
                                tint = Color(0xFF8B9490),
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 13.dp),
                        onClick = {
                            favorite = favoriteStore?.toggle(song) ?: !favorite
                            showActions = false
                        },
                    )
                    if (canOpenCreator) {
                        HorizontalDivider(color = Color(0xFFE7E9E8), thickness = 0.7.dp)
                        DropdownMenuItem(
                            modifier = Modifier.height(44.dp),
                            text = { Text("Artist profile", color = Ink, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Person,
                                    null,
                                    tint = Color(0xFF8B9490),
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 13.dp),
                            onClick = { openCreatorProfile() },
                        )
                    }
                    HorizontalDivider(color = Color(0xFFE7E9E8), thickness = 0.7.dp)
                    if (lyrics != null) {
                        DropdownMenuItem(
                            modifier = Modifier.height(44.dp),
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (lyricsOn) "Hide lyrics" else "Show lyrics",
                                        color = Ink,
                                        fontSize = 12.sp,
                                    )
                                    if (!lyricsOn) {
                                        RedDot(Modifier.padding(start = 6.dp))
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Lyrics,
                                    null,
                                    tint = Color(0xFF8B9490),
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 13.dp),
                            onClick = {
                                lyricsOn = !lyricsOn
                                if (!lyricsOn) showLyricSync = false
                                showActions = false
                            },
                        )
                        HorizontalDivider(color = Color(0xFFE7E9E8), thickness = 0.7.dp)
                    }
                    DropdownMenuItem(
                        modifier = Modifier.height(44.dp),
                        text = { Text("Sleep timer", color = Ink, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Timer,
                                null,
                                tint = Color(0xFF8B9490),
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 13.dp),
                        onClick = { showActions = false; showSleep = true },
                    )
                    HorizontalDivider(color = Color(0xFFE7E9E8), thickness = 0.7.dp)
                    DropdownMenuItem(
                        modifier = Modifier.height(44.dp),
                        text = { Text("Equalizer", color = Ink, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Equalizer,
                                null,
                                tint = Color(0xFF8B9490),
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 13.dp),
                        onClick = { showActions = false; showEqualizer = true },
                    )
                    HorizontalDivider(color = Color(0xFFE7E9E8), thickness = 0.7.dp)
                    DropdownMenuItem(
                        modifier = Modifier.height(44.dp),
                        text = { Text("Set ringtone", color = Ink, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.NotificationsActive,
                                null,
                                tint = Color(0xFF8B9490),
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 13.dp),
                        onClick = { showActions = false; showRingtone = true },
                    )
                }
            }
        }

        if (canOpenCreator) {
            ArtistProfileCard(
                artistName = creatorName,
                photoUrl = artistPhoto,
                verified = isVerifiedArtist,
                loading = openingArtist,
                onClick = ::openCreatorProfile,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }
        PlayingNextStack(
            player = player,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            interactionsEnabled = !sheetOpen,
            onOpenQueue = { showQueue = true },
        )
        player.lastError?.let { error ->
            Text(error, Modifier.padding(top = 8.dp), color = Color(0xFFFF8A80), fontSize = 12.sp, maxLines = 3)
        }
        OfflineDownloads.lastError?.let { error ->
            Text(error, Modifier.padding(top = 8.dp), color = Color(0xFFFF8A80), fontSize = 12.sp, maxLines = 3)
        }
        Spacer(Modifier.height(8.dp))
        }

        CircleProgressBar(
            positionMs = position,
            durationMs = duration,
            onPositionChange = {
                dragging = true
                position = it
            },
            onPositionChangeFinished = {
                player.seekTo(position)
                dragging = false
            },
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Color(0xFF8C9690), fontSize = 12.sp)
            Text(formatTime(duration), color = Color(0xFF8C9690), fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.SkipPrevious, "Previous", tint = Cyan, modifier = Modifier.size(42.dp))
                }
                FloatingActionButton(
                    onClick = player::togglePlay,
                    containerColor = Cyan,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        if (player.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        "Play",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = player::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.SkipNext, "Next", tint = Cyan, modifier = Modifier.size(42.dp))
                }
            }
            IconButton(onClick = player::toggleRepeatOne, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    Icons.Outlined.RepeatOne,
                    if (player.repeatOne) "Turn off song loop" else "Loop current song",
                    tint = if (player.repeatOne) Cyan else Color(0xFF8C9690),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
    if (showQueue) QueueSheet(player) { showQueue = false }
    if (showSleep) SleepTimerSheet(player) { showSleep = false }
    if (showEqualizer) EqualizerSheet(player) { showEqualizer = false }
    if (showRingtone) {
        RingtoneSheet(
            song = song,
            player = player,
            durationMs = duration,
            onDismiss = { showRingtone = false },
        )
    }
    }
    if (confirmRemoveDownload) {
        DeleteDownloadConfirmDialog(
            songTitle = song.title,
            onConfirm = {
                OfflineDownloads.delete(song.id)
                confirmRemoveDownload = false
            },
            onDismiss = { confirmRemoveDownload = false },
        )
    }
}

@Composable
private fun CircleProgressBar(
    positionMs: Long,
    durationMs: Long,
    onPositionChange: (Long) -> Unit,
    onPositionChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val thumbRadiusPx = with(LocalDensity.current) { 9.dp.toPx() }
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)

    fun positionFor(x: Float): Long {
        if (widthPx <= 0) return 0L
        val trackWidth = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)
        val fraction = ((x - thumbRadiusPx) / trackWidth).coerceIn(0f, 1f)
        return (fraction * safeDuration).toLong()
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(safeDuration, widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onPositionChange(positionFor(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        onPositionChange(positionFor(change.position.x))
                    }
                    onPositionChangeFinished()
                }
            },
    ) {
        val trackHeight = 6.dp.toPx()
        val thumbRadius = 9.dp.toPx()
        val trackStart = thumbRadius
        val trackWidth = (size.width - thumbRadius * 2).coerceAtLeast(0f)
        val trackTop = (size.height - trackHeight) / 2
        val progressWidth = trackWidth * fraction
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(trackStart, trackTop),
            size = Size(trackWidth, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2),
        )
        if (progressWidth > 0f) {
            drawRoundRect(
                color = Cyan,
                topLeft = Offset(trackStart, trackTop),
                size = Size(progressWidth, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2),
            )
        }
        drawCircle(
            color = Cyan,
            radius = thumbRadius,
            center = Offset(trackStart + progressWidth, size.height / 2),
        )
    }
}

@Composable
private fun ArtistProfileCard(
    artistName: String,
    photoUrl: String?,
    verified: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val verifiedBlue = Color(0xFF1D9BF0)
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl.isNullOrBlank()) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Color(0xFF9FA8A3),
                    modifier = Modifier.size(26.dp),
                )
            } else {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(photoUrl) {
                        ImageRequest.Builder(context)
                            .data(photoUrl)
                            .size(512)
                            .build()
                    },
                    contentDescription = artistName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    artistName,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (verified) {
                    Icon(
                        Icons.Outlined.Verified,
                        contentDescription = "Verified artist",
                        tint = verifiedBlue,
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                    )
                }
            }
            Text(
                if (loading) "Opening artist…" else "Listen more from this artist",
                color = Color(0xFF8B9490),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        if (loading) {
            CircularProgressIndicator(
                color = Cyan,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF9FA8A3),
            )
        }
    }
}

@Composable
private fun PlayingNextStack(
    player: PlayerConnection,
    modifier: Modifier = Modifier,
    interactionsEnabled: Boolean = true,
    onOpenQueue: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("musium_appearance", android.content.Context.MODE_PRIVATE)
    }
    var visible by remember {
        mutableStateOf(prefs.getBoolean("playing_next_visible", true))
    }
    val next = player.queue.getOrNull(player.currentIndex + 1)
    val after = player.queue.getOrNull(player.currentIndex + 2) ?: next
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = if (visible) 10.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Playing next",
                Modifier.weight(1f),
                color = Color(0xFF9AA49F),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = {
                    visible = !visible
                    prefs.edit().putBoolean("playing_next_visible", visible).apply()
                },
                enabled = interactionsEnabled,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (visible) "Hide playing next" else "Show playing next",
                    tint = Color(0xFF8B9490),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onOpenQueue,
                enabled = interactionsEnabled,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(34.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.QueueMusic,
                    "Queue",
                    tint = Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (visible) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clickable(enabled = interactionsEnabled, onClick = onOpenQueue),
            ) {
                if (after != null) {
                    NextTrackCard(
                        song = after,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .offset(y = 10.dp)
                            .alpha(0.42f),
                        muted = true,
                    )
                }
                NextTrackCard(
                    song = next,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    muted = false,
                )
            }
        }
    }
}

@Composable
private fun NextTrackCard(
    song: PlayableSong?,
    modifier: Modifier = Modifier,
    muted: Boolean,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (song == null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic, null, tint = Color(0xFFB0B8B4), modifier = Modifier.size(22.dp))
            }
            Text(
                "Queue is empty",
                Modifier.padding(start = 12.dp).weight(1f),
                color = Color(0xFF9AA49F),
                fontSize = 13.sp,
            )
        } else {
            AsyncImage(
                song.thumbnailUrl,
                song.title,
                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    song.title,
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    color = Color(0xFF9AA49F),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun LyricsSyncBar(
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = LyricsOffsetStore.STEP_MS
    val max = LyricsOffsetStore.MAX_OFFSET_MS
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Lyrics sync", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            "Earlier if lines lag · Later if lines lead",
            color = Color(0xFF9AA49F),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { onOffsetChange((offsetMs - step).coerceIn(-max, max)) },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Icon(Icons.Outlined.Remove, "Earlier", tint = Ink)
            }
            Text(
                formatLyricsOffset(offsetMs),
                Modifier.width(72.dp),
                color = Cyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = { onOffsetChange((offsetMs + step).coerceIn(-max, max)) },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Icon(Icons.Outlined.Add, "Later", tint = Ink)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Reset",
                color = if (offsetMs != 0L) Cyan else Color(0xFF9AA49F),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(enabled = offsetMs != 0L) { onOffsetChange(0L) }
                    .padding(8.dp),
            )
            Text(
                "Done",
                color = Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun RedDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(Color(0xFFE53935)),
    )
}

internal fun formatTime(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    val totalSeconds = safe / 1_000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
