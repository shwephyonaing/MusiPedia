package team.ctrlv.musipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import team.ctrlv.musipedia.innertube.BrowsePage
import team.ctrlv.musipedia.innertube.SongItem
import team.ctrlv.musipedia.innertube.YtItem
import team.ctrlv.musipedia.innertube.hdArtwork
import team.ctrlv.musipedia.innertube.hdProfileArtwork

private val Cyan = Color(0xFF42E4CE)
private val VerifiedBlue = Color(0xFF1D9BF0)
private val PageBackground: Color @Composable get() = MaterialTheme.colorScheme.background
private val PageInk: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val MutedInk: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

private sealed interface BrowseUi {
    data object Loading : BrowseUi
    data class Ready(val page: BrowsePage) : BrowseUi
    data class Error(val message: String) : BrowseUi
}

@Composable
internal fun ArtistScreen(browseId: String, name: String, profileImage: String?, onBack: () -> Unit) {
    var resolvedPhoto by remember(browseId, profileImage) { mutableStateOf(profileImage) }
    LaunchedEffect(browseId, name, profileImage) {
        if (!profileImage.isNullOrBlank()) {
            resolvedPhoto = profileImage.hdProfileArtwork()
            return@LaunchedEffect
        }
        resolvedPhoto = runCatching {
            MusicRepository.artistAvatar(browseId, name)
        }.getOrNull()
    }
    BrowseScaffold(
        pageId = browseId,
        titleHint = name,
        preferredArtwork = resolvedPhoto,
        roundArtwork = true,
        onBack = onBack,
    ) { MusicRepository.artist(browseId) }
}

@Composable
internal fun AlbumScreen(browseId: String, name: String, onBack: () -> Unit) {
    BrowseScaffold(pageId = browseId, titleHint = name, onBack = onBack) { MusicRepository.album(browseId) }
}

@Composable
internal fun PlaylistScreen(playlistId: String, name: String, onBack: () -> Unit) {
    BrowseScaffold(pageId = playlistId, titleHint = name, onBack = onBack) { MusicRepository.playlist(playlistId) }
}

@Composable
private fun BrowseScaffold(
    pageId: String,
    titleHint: String,
    preferredArtwork: String? = null,
    roundArtwork: Boolean = false,
    onBack: () -> Unit,
    loader: suspend () -> BrowsePage,
) {
    val player = LocalPlayerConnection.current
    val router = LocalMusicRouter.current
    var state by remember { mutableStateOf<BrowseUi>(BrowseUi.Loading) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(pageId, reload) {
        state = BrowseUi.Loading
        state = runCatching { loader() }.fold(
            onSuccess = { BrowseUi.Ready(it) },
            onFailure = { BrowseUi.Error(it.message ?: "Unable to open this page") },
        )
    }
    Box(Modifier.fillMaxSize().background(PageBackground).safeDrawingPadding()) {
        when (val current = state) {
            BrowseUi.Loading -> BrandLoadingIndicator(Modifier.align(Alignment.Center).size(120.dp))
            is BrowseUi.Error -> Column(Modifier.padding(28.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                ) {
                    Icon(Icons.Outlined.KeyboardArrowDown, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    titleHint,
                    Modifier.padding(top = 20.dp),
                    color = PageInk,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                )
                TextButton(onClick = { reload += 1 }) { Text("Retry", color = Cyan, fontWeight = FontWeight.Bold) }
            }
            is BrowseUi.Ready -> {
                val page = current.page
                val queue = page.songs.map { it.toPlayable() }
                // Artist: prefer the resolved portrait passed in / fetched for the player card.
                // Browse headers often return banners or abstract channel icons.
                val rawArtwork = when {
                    roundArtwork -> preferredArtwork
                        ?: page.thumbnail
                        ?: page.sections.asSequence()
                            .flatMap { it.items.asSequence() }
                            .mapNotNull { it.thumbnail }
                            .firstOrNull()
                    else -> preferredArtwork
                        ?: page.thumbnail
                        ?: page.sections.asSequence()
                            .flatMap { it.items.asSequence() }
                            .mapNotNull { it.thumbnail }
                            .firstOrNull()
                        ?: page.songs.firstOrNull()?.thumbnail
                }
                val heroArtwork = if (roundArtwork) {
                    rawArtwork?.hdProfileArtwork()
                } else {
                    rawArtwork?.hdArtwork()
                }
                val context = LocalContext.current
                val heroModel = remember(heroArtwork, roundArtwork) {
                    heroArtwork?.let { url ->
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(if (roundArtwork) 560 else 480)
                            .crossfade(false)
                            .build()
                    }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 170.dp)) {
                    item {
                        Row(Modifier.padding(20.dp, 20.dp, 20.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowDown, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(
                                model = heroModel,
                                contentDescription = page.title,
                                modifier = Modifier
                                    .size(if (roundArtwork) 248.dp else 210.dp)
                                    .clip(if (roundArtwork) CircleShape else RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.Center,
                            )
                            if (roundArtwork) {
                                val catalog = (context.applicationContext as? MusiumApplication)?.tasteCatalogStore
                                val verified = catalog?.isVerified(pageId) == true
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        page.title,
                                        color = PageInk,
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (verified) {
                                        Icon(
                                            Icons.Outlined.Verified,
                                            contentDescription = "Verified artist",
                                            tint = VerifiedBlue,
                                            modifier = Modifier.padding(start = 8.dp).size(22.dp),
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    page.title,
                                    Modifier.padding(top = 16.dp),
                                    color = PageInk,
                                    fontSize = 27.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                )
                            }
                            page.subtitle?.let { Text(it, Modifier.padding(top = 6.dp), color = MutedInk, fontSize = 14.sp) }
                            Row(
                                Modifier.padding(top = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { if (queue.isNotEmpty()) player?.play(queue, 0) },
                                    enabled = queue.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.White),
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, null)
                                    Text("Play", Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold)
                                }
                                if (roundArtwork) {
                                    val tasteStore = (context.applicationContext as? MusiumApplication)?.tasteStore
                                    var favorited by remember(pageId) {
                                        mutableStateOf(tasteStore?.contains(pageId) == true)
                                    }
                                    IconButton(
                                        onClick = {
                                            val store = tasteStore ?: return@IconButton
                                            favorited = store.toggle(
                                                TasteArtist(
                                                    id = pageId,
                                                    name = page.title.ifBlank { titleHint },
                                                    thumbnailUrl = heroArtwork,
                                                ),
                                            )
                                        },
                                        modifier = Modifier.background(
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape,
                                        ),
                                    ) {
                                        Icon(
                                            if (favorited) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                            if (favorited) "Remove favorite artist" else "Add favorite artist",
                                            tint = if (favorited) Cyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (page.songs.isNotEmpty()) {
                        item { SectionTitle("Songs", Modifier.padding(top = 24.dp)) }
                        items(page.songs, key = { it.id }) { song ->
                            MusicItemRow(song, light = true) { song.open(player, router, queue) }
                        }
                    }
                    page.sections.forEach { section ->
                        item { SectionTitle(section.title, Modifier.padding(top = 18.dp)) }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 26.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(section.items, key = { it::class.simpleName + it.id }) { item ->
                                    BrowseTile(item) {
                                        item.open(player, router, section.items.filterIsInstance<SongItem>().map { it.toPlayable() })
                                    }
                                }
                            }
                        }
                    }
                    item { Box(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BrowseTile(item: YtItem, onClick: () -> Unit) {
    val art = rememberArtworkRequest(item.thumbnail, 140.dp)
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = art,
            contentDescription = item.title,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray),
            contentScale = ContentScale.Crop,
        )
        Text(item.title, Modifier.padding(top = 8.dp), color = PageInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        item.subtitle?.let { Text(it, color = MutedInk, fontSize = 11.sp, maxLines = 1) }
    }
}
