package team.ctrlv.musipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import team.ctrlv.musipedia.innertube.AlbumItem
import team.ctrlv.musipedia.innertube.HomeSection
import team.ctrlv.musipedia.innertube.PlaylistItem
import team.ctrlv.musipedia.innertube.SongItem
import team.ctrlv.musipedia.innertube.YtItem
import team.ctrlv.musipedia.innertube.hdArtwork
import team.ctrlv.musipedia.innertube.youtubeThumb

private val Cyan = Color(0xFF42E4CE)
private val Page: Color @Composable get() = MaterialTheme.colorScheme.background
private val Tile: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val Mute: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

private sealed interface HomeUi {
    data object Loading : HomeUi
    data class Ready(val sections: List<HomeSection>) : HomeUi
    data class Error(val message: String) : HomeUi
}

@Composable
internal fun HomeContent() {
    val player = LocalPlayerConnection.current
    val router = LocalMusicRouter.current
    val context = LocalContext.current
    val recentsStore = (context.applicationContext as? MusiumApplication)?.recentStore
    val favoriteStore = (context.applicationContext as? MusiumApplication)?.favoriteStore
    val tasteStore = (context.applicationContext as? MusiumApplication)?.tasteStore
    var recents by remember { mutableStateOf(recentsStore?.songs().orEmpty()) }
    var favorites by remember { mutableStateOf(favoriteStore?.songs().orEmpty()) }
    var state by remember { mutableStateOf<HomeUi>(HomeUi.Loading) }
    var reload by remember { mutableStateOf(0) }
    var awaitingOnlineSync by remember {
        mutableStateOf(!context.isNetworkAvailable())
    }
    val scope = rememberCoroutineScope()
    val downloads = (OfflineDownloads.inFlight.values + OfflineDownloads.songs).distinctBy { it.id }
    val lastListened = recents.firstOrNull()
    val app = context.applicationContext as? MusiumApplication

    val tasteSignature = tasteStore?.artists()?.joinToString(",") { it.id }.orEmpty()

    LaunchedEffect(player?.current?.id, player?.playing) {
        recents = recentsStore?.songs().orEmpty()
        favorites = favoriteStore?.songs().orEmpty()
    }
    LaunchedEffect(tasteSignature, reload) {
        if (!context.isNetworkAvailable()) {
            // Offline shell — downloads / favorites / recents still work; sync when back online.
            if (state !is HomeUi.Ready) state = HomeUi.Ready(emptyList())
            awaitingOnlineSync = true
            return@LaunchedEffect
        }
        if (reload > 0) state = HomeUi.Loading
        val seeds = recentsStore?.songs().orEmpty().take(5)
        val tastes = tasteStore?.artists().orEmpty()
        val force = reload > 0
        runCatching {
            MusicRepository.homeFeed(seeds, tastes, force = force).collect { sections ->
                if (sections.any { it.items.isNotEmpty() }) {
                    state = HomeUi.Ready(sections)
                    awaitingOnlineSync = false
                }
            }
        }.onFailure {
            if (state !is HomeUi.Ready) {
                state = HomeUi.Error(it.message ?: "Unable to load charts")
            }
        }
        if (state !is HomeUi.Ready) {
            state = HomeUi.Error("Couldn't load home. Check your connection and retry.")
        }
    }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            return@DisposableEffect onDispose { }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = manager.getNetworkCapabilities(network) ?: return
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val current = state
                    val needsRefresh = awaitingOnlineSync ||
                        current is HomeUi.Error ||
                        (current is HomeUi.Ready && current.sections.none { it.items.isNotEmpty() })
                    if (!needsRefresh) return@post
                    awaitingOnlineSync = false
                    MusicRepository.clearHomeCache()
                    reload += 1
                    // Refresh verified artists + home once connectivity returns.
                    scope.launch {
                        app?.tasteCatalogStore?.refresh(force = true)
                    }
                }
            }

            override fun onLost(network: Network) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    awaitingOnlineSync = true
                }
            }
        }
        runCatching {
            manager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }

    val ready = state as? HomeUi.Ready
    val forYou = ready?.named("For You")
    val trending = ready?.named("Trending")
    val tasteArtists = remember(tasteSignature) { tasteStore?.artists().orEmpty() }
    val nowPlaying = player?.current?.takeIf { player.playing }
    val returning = lastListened != null
    val hero = when {
        nowPlaying != null -> nowPlaying.toFeaturedItem()
        returning -> lastListened.toFeaturedItem()
        else -> listOfNotNull(trending?.firstOrNull(), forYou?.firstOrNull())
            .firstOrNull { !it.thumbnail.isNullOrBlank() }
            ?: trending?.firstOrNull()
            ?: forYou?.firstOrNull()
    }
    val heroMode = when {
        nowPlaying != null -> HeroMode.PlayingNow
        returning -> HeroMode.LastPlayed
        else -> HeroMode.Featured
    }
    val heroMeta = hero?.subtitle?.prettyTitle()?.takeIf { it.isNotBlank() }
        ?: when (heroMode) {
            HeroMode.PlayingNow -> "Now on MusiPedia"
            HeroMode.LastPlayed -> "Continue listening"
            HeroMode.Featured -> "Trending now"
        }

    Box(Modifier.fillMaxSize().background(Page)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 106.dp),
        ) {
            item {
                FeaturedHero(
                    item = hero,
                    mode = heroMode,
                    meta = heroMeta,
                    enabled = heroMode != HeroMode.PlayingNow,
                ) {
                    when {
                        nowPlaying != null -> Unit
                        returning -> player?.play(recents, 0)
                        else -> hero?.open(player, router)
                    }
                }
            }
            if (state is HomeUi.Error) {
                item {
                    TextButton(onClick = { reload += 1 }, Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Text("Retry", color = Cyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { SectionTitle("For You", Modifier.padding(top = 18.dp)) }
            item { PosterRow(forYou, placeholders = 8) { item -> item.open(player, router) } }
            if (favorites.size > 1) {
                item {
                    SectionTitleRow(
                        title = "Favorites",
                        action = "Show All",
                        onAction = { router(MusicRoute.Favorites) },
                        modifier = Modifier.padding(top = 22.dp),
                    )
                }
                item { FavoritesRow(preview = favorites.take(5), queue = favorites, player = player) }
            }
            if (downloads.isNotEmpty()) {
                item {
                    SectionTitleRow(
                        title = "Downloads",
                        action = "Show All",
                        onAction = { router(MusicRoute.Downloads) },
                        modifier = Modifier.padding(top = 22.dp),
                    )
                }
                item { DownloadsRow(downloads.take(5), player) }
            }
            item { SectionTitle("Trending", Modifier.padding(top = 22.dp)) }
            item { PosterRow(trending, placeholders = 10) { item -> item.open(player, router) } }
            if (tasteArtists.isNotEmpty()) {
                item {
                    SectionTitle("More from Your Fav Artists", Modifier.padding(top = 22.dp))
                }
                item {
                    FavArtistProfilesRow(
                        artists = tasteArtists,
                        onArtist = { artist ->
                            router(
                                MusicRoute.Artist(
                                    id = artist.id,
                                    name = artist.name,
                                    profileImage = artist.thumbnailUrl,
                                ),
                            )
                        },
                        onMoreArtists = { router(MusicRoute.Personalize) },
                    )
                }
            }
        }
    }
}

private fun HomeUi.Ready.named(title: String): List<YtItem> =
    sections.firstOrNull { it.title.equals(title, ignoreCase = true) }?.items.orEmpty()

private fun PlayableSong.toFeaturedItem() = SongItem(
    id = id,
    title = title,
    subtitle = artist,
    thumbnail = thumbnailUrl,
    playlistId = playlistId,
    artistId = artistId,
    albumId = albumId,
)

private enum class HeroMode { Featured, LastPlayed, PlayingNow }

@Composable
private fun FeaturedHero(
    item: YtItem?,
    mode: HeroMode,
    meta: String,
    enabled: Boolean = true,
    onOpen: () -> Unit,
) {
    val badge = when (mode) {
        HeroMode.Featured -> "Featured"
        HeroMode.LastPlayed -> "Last Played"
        HeroMode.PlayingNow -> "Now Playing"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(414f / 463f)
            .background(Color(0xFF0B0B0B))
            .clickable(enabled = enabled && item != null, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        if (item?.thumbnail.isNullOrBlank() && item !is SongItem) {
            BrandMark(Modifier.height(92.dp).width(104.dp), Cyan)
        } else {
            val primary = item?.thumbnail?.hdArtwork()
                ?: (item as? SongItem)?.id?.takeIf { it.length == 11 }?.let { youtubeThumb(it) }
            val fallback = (item as? SongItem)?.id?.takeIf { it.length == 11 }?.let { youtubeThumb(it, hd = false) }
                ?: item?.thumbnail
            var model by remember(primary, fallback) { mutableStateOf(primary ?: fallback) }
            if (model.isNullOrBlank()) {
                BrandMark(Modifier.height(92.dp).width(104.dp), Cyan)
            } else {
                val heroReq = rememberArtworkRequest(
                    data = model,
                    size = ArtworkSizes.HeroMaxWidth,
                    crossfade = false,
                )
                AsyncImage(
                    model = heroReq,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = {
                        if (fallback != null && model != fallback) model = fallback
                    },
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.72f to Color(0xCC000000),
                        1f to Color(0xF2000000),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 28.dp),
        ) {
            Text(
                badge,
                Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
            )
            Text(
                item?.title?.heroTitle() ?: "MusiPedia",
                Modifier.padding(top = 12.dp),
                color = Color.White,
                fontSize = 30.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                Modifier.padding(top = 6.dp),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FavArtistProfilesRow(
    artists: List<TasteArtist>,
    onArtist: (TasteArtist) -> Unit,
    onMoreArtists: () -> Unit,
) {
    val catalog = (LocalContext.current.applicationContext as? MusiumApplication)?.tasteCatalogStore
    LazyRow(
        contentPadding = PaddingValues(horizontal = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(artists, key = { it.id }) { artist ->
            FavArtistProfileCell(
                name = artist.name,
                imageUrl = artist.thumbnailUrl,
                verified = catalog?.isVerified(artist.id) == true,
                onClick = { onArtist(artist) },
            )
        }
        item(key = "more-artists") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(88.dp)
                    .clickable(onClick = onMoreArtists),
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Tile)
                        .border(1.dp, Ink.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PersonAdd,
                        contentDescription = "More Artists",
                        tint = Cyan,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    "More Artists",
                    Modifier.padding(top = 8.dp),
                    color = Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun FavArtistProfileCell(
    name: String,
    imageUrl: String?,
    verified: Boolean,
    onClick: () -> Unit,
) {
    val art = rememberArtworkRequest(imageUrl, ArtworkSizes.Profile)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Tile),
            contentAlignment = Alignment.Center,
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = art,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    name.take(1).uppercase(),
                    color = Ink.copy(alpha = 0.55f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                )
            }
        }
        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                name,
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (verified) {
                Icon(
                    Icons.Outlined.Verified,
                    contentDescription = "Verified artist",
                    tint = Color(0xFF1D9BF0),
                    modifier = Modifier.padding(start = 2.dp).size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PosterRow(items: List<YtItem>?, placeholders: Int = 5, onOpen: (YtItem) -> Unit) {
    val cards = items.orEmpty()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (cards.isEmpty()) {
            items(placeholders) {
                LogoCard()
            }
        } else {
            items(cards, key = { it::class.simpleName + it.id }) { item ->
                PosterCard(
                    image = item.thumbnail,
                    title = item.title.prettyTitle(),
                    subtitle = item.cardMeta(),
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun LogoCard() {
    Column(Modifier.width(158.dp)) {
        Box(
            Modifier.size(158.dp).clip(RoundedCornerShape(14.dp)).background(Tile),
            contentAlignment = Alignment.Center,
        ) {
            BrandMark(Modifier.height(48.dp).width(54.dp), Cyan.copy(alpha = 0.7f))
        }
        Text(" ", Modifier.padding(top = 8.dp), fontSize = 15.sp)
        Text(" ", Modifier.padding(top = 2.dp), fontSize = 12.sp)
    }
}

@Composable
private fun FavoritesRow(
    preview: List<PlayableSong>,
    queue: List<PlayableSong>,
    player: PlayerConnection?,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(preview, key = { "fav-${it.id}" }) { song ->
            PosterCard(
                image = song.thumbnailUrl,
                title = song.title.prettyTitle(),
                subtitle = song.artist.prettyTitle().ifBlank { "Favorite" },
                onClick = {
                    player?.play(queue, queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                },
            )
        }
    }
}

@Composable
private fun DownloadsRow(songs: List<PlayableSong>, player: PlayerConnection?) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(songs, key = { it.id }) { song ->
            DownloadPosterCard(song = song, player = player)
        }
    }
}

@Composable
private fun DownloadPosterCard(song: PlayableSong, player: PlayerConnection?) {
    // Progress state stays local so ticks don't rebuild the whole Home LazyColumn.
    val inProgress = song.id in OfflineDownloads.progressing
    val progress = if (inProgress) OfflineDownloads.progress[song.id] ?: 0f else 0f
    Box {
        PosterCard(
            image = song.thumbnailUrl,
            title = song.title.prettyTitle(),
            subtitle = song.artist.prettyTitle().ifBlank { "Downloaded" },
            onClick = {
                val queue = OfflineDownloads.songs
                player?.play(queue, queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
            },
        )
        if (inProgress) {
            CircularProgressIndicator(
                progress = { progress },
                color = Cyan,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).padding(bottom = 36.dp).size(28.dp),
            )
        }
    }
}

@Composable
private fun PosterCard(
    image: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val art = rememberArtworkRequest(image, ArtworkSizes.Poster)
    Column(Modifier.width(158.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = art,
            contentDescription = title,
            modifier = Modifier.size(158.dp).clip(RoundedCornerShape(14.dp)).background(Tile),
            contentScale = ContentScale.Crop,
        )
        Text(
            title,
            Modifier.padding(top = 8.dp),
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(subtitle, Modifier.padding(top = 2.dp), color = Mute, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(horizontal = 26.dp, vertical = 8.dp),
        Ink,
        23.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Serif,
    )
}

@Composable
private fun SectionTitleRow(
    title: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), Ink, 23.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        Text(action, Modifier.clickable(onClick = onAction), Cyan, 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private val PrettyDashTail = Regex("\\s+-\\s*$")
private val HeroPipe = Regex("""(?i)\s*\|\s*.+$""")
private val HeroParen = Regex(
    """(?i)\s*[\(\[]\s*(official\s+)?(audio|music\s+video|mv|lyric[s]?(?:\s+video)?|visualizer)\s*[\)\]]""",
)
private val HeroDashOfficial = Regex("""(?i)\s*[-–—]\s*(official|lyric).+$""")
private val MultiSpace = Regex("""\s{2,}""")
private val TrailingDash = Regex("""\s+[-–—]\s*$""")

private fun String.prettyTitle(): String = trim().replace(PrettyDashTail, "")

private fun String.heroTitle(): String {
    var title = trim()
    title = title.replace(HeroPipe, "")
    title = title.replace(HeroParen, "")
    title = title.replace(HeroDashOfficial, "")
    title = title.replace(MultiSpace, " ").trim()
    title = title.replace(TrailingDash, "")
    return title.ifBlank { trim() }.prettyTitle()
}

private fun YtItem.cardMeta(): String = subtitle?.prettyTitle()?.takeIf { it.isNotBlank() } ?: when (this) {
    is PlaylistItem -> "Playlist"
    is AlbumItem -> "Album"
    is SongItem -> "Song"
    else -> "Music"
}
