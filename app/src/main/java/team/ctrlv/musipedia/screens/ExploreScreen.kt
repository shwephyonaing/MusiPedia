package team.ctrlv.musipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import team.ctrlv.musipedia.innertube.ArtistItem
import team.ctrlv.musipedia.innertube.SearchPage
import team.ctrlv.musipedia.innertube.SongItem
import team.ctrlv.musipedia.innertube.YtItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Cyan = Color(0xFF42E4CE)
private val Black: Color @Composable get() = MaterialTheme.colorScheme.background
private val SearchInk: Color @Composable get() = MaterialTheme.colorScheme.onBackground

private sealed interface SearchUi {
    data object Idle : SearchUi
    data object Loading : SearchUi
    data class Results(val page: SearchPage) : SearchUi
    data class Error(val message: String) : SearchUi
}

private enum class SearchTab { Songs, Artists, Albums, Playlists }

@Composable
internal fun ExploreScreen(onBack: () -> Unit = {}) {
    val player = LocalPlayerConnection.current
    val router = LocalMusicRouter.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val store = remember { (context.applicationContext as? MusiumApplication)?.recentStore }
    var query by remember { mutableStateOf("") }
    var lastSearched by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<SearchUi>(SearchUi.Idle) }
    var tab by remember { mutableStateOf(SearchTab.Songs) }
    var history by remember { mutableStateOf(store?.queries().orEmpty()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    fun search(text: String = query) {
        val next = text.trim()
        if (next.isBlank()) return
        query = next
        lastSearched = next
        focused = false
        focusManager.clearFocus()
        keyboard?.hide()
        store?.addQuery(next)
        history = store?.queries().orEmpty()
        scope.launch {
            state = SearchUi.Loading
            state = runCatching { MusicRepository.search(next) }.fold(
                onSuccess = { SearchUi.Results(it) },
                onFailure = { SearchUi.Error(it.message ?: "Search failed") },
            )
        }
    }

    LaunchedEffect(query) {
        val typed = query.trim()
        if (typed.isEmpty()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(180)
        suggestions = runCatching { MusicRepository.suggest(typed) }
            .getOrDefault(emptyList())
            .filterNot { it.equals(typed, ignoreCase = true) }
    }

    val typed = query.trim()
    val showSuggest = focused || typed != lastSearched || state is SearchUi.Idle
    val historyHits = if (typed.isEmpty()) history else history.filter { it.contains(typed, ignoreCase = true) }
    val remoteHits = suggestions.filter { suggestion ->
        historyHits.none { it.equals(suggestion, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize().background(Black).safeDrawingPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 72.dp, bottom = 100.dp),
        ) {
            item {
                Row(Modifier.padding(horizontal = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Search", color = SearchInk, fontSize = 25.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 34.dp, vertical = 14.dp)
                        .testTag("searchField")
                        .onFocusChanged { focused = it.isFocused },
                    placeholder = {
                        Text(
                            "Search for artists and songs",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                lastSearched = ""
                                state = SearchUi.Idle
                                focused = true
                            }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = SearchInk,
                        unfocusedTextColor = SearchInk,
                        cursorColor = Cyan,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
            }
            if (showSuggest) {
                items(historyHits, key = { "h:$it" }) { item ->
                    SuggestRow(item, history = true, onClick = { search(item) }) {
                        store?.removeQuery(item)
                        history = store?.queries().orEmpty()
                    }
                }
                items(remoteHits, key = { "s:$it" }) { item ->
                    SuggestRow(item, history = false, onClick = { search(item) })
                }
            } else {
                when (val current = state) {
                    SearchUi.Idle -> Unit
                    SearchUi.Loading -> item {
                        BrandLoadingIndicator(
                            Modifier.fillMaxWidth().height(170.dp),
                        )
                    }
                    is SearchUi.Error -> item {
                        TextButton(onClick = { search() }, enabled = query.isNotBlank(), modifier = Modifier.padding(28.dp)) {
                            Text("Retry", color = Cyan, fontWeight = FontWeight.Bold)
                        }
                    }
                    is SearchUi.Results -> {
                        val page = current.page
                        if (page.isEmpty) {
                            item {
                                Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                                    Text("No results", color = SearchInk, fontSize = 18.sp)
                                    MissingFavoriteArtistPrompt(
                                        artistQuery = lastSearched.ifBlank { query },
                                        color = Cyan,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                            }
                        } else {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 28.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(SearchTab.entries) { option ->
                                        FilterChip(
                                            selected = tab == option,
                                            onClick = { tab = option },
                                            label = { Text(option.name) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Cyan,
                                                selectedLabelColor = Color.Black,
                                                labelColor = SearchInk,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            ),
                                        )
                                    }
                                }
                            }
                            val visible: List<YtItem> = when (tab) {
                                SearchTab.Songs -> page.songs
                                SearchTab.Artists -> page.artists
                                SearchTab.Albums -> page.albums
                                SearchTab.Playlists -> page.playlists
                            }
                            if (visible.isEmpty()) {
                                item {
                                    Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                                        Text(
                                            "No ${tab.name.lowercase()} found",
                                            color = SearchInk,
                                            fontSize = 16.sp,
                                        )
                                        if (tab == SearchTab.Artists) {
                                            MissingFavoriteArtistPrompt(
                                                artistQuery = lastSearched.ifBlank { query },
                                                color = Cyan,
                                                modifier = Modifier.padding(top = 12.dp),
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(visible, key = { it::class.simpleName + it.id }) { item ->
                                    MusicItemRow(
                                        item = item,
                                        round = tab == SearchTab.Artists,
                                        light = true,
                                        trailing = if (item is SongItem) {
                                            {
                                                SearchSongMenu(
                                                    song = item.toPlayable(),
                                                    player = player,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    ) {
                                        item.open(player, router)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 24.dp)
                .size(38.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, "Back to Home", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SuggestRow(text: String, history: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 28.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (history) Icons.Outlined.History else Icons.Outlined.Search,
            null,
            tint = Color(0xFFB6B6B6),
            modifier = Modifier.size(22.dp),
        )
        Text(text, Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp), SearchInk, 16.sp, maxLines = 1)
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, null, tint = Color(0xFFB6B6B6))
            }
        }
    }
}

@Composable
internal fun MusicItemRow(
    item: YtItem,
    round: Boolean = false,
    light: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val catalog = (LocalContext.current.applicationContext as? MusiumApplication)?.tasteCatalogStore
    val verified = item is ArtistItem && catalog?.isVerified(item.id) == true
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = rememberArtworkRequest(item.thumbnail, ArtworkSizes.ListThumb),
            contentDescription = item.title,
            modifier = Modifier.size(58.dp).clip(if (round) CircleShape else RoundedCornerShape(6.dp)).background(Color.DarkGray),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    color = if (light) SearchInk else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (verified) {
                    Icon(
                        Icons.Outlined.Verified,
                        contentDescription = "Verified artist",
                        tint = Color(0xFF1D9BF0),
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                item.subtitle ?: item::class.simpleName.orEmpty().removeSuffix("Item"),
                color = if (light) Color(0xFF909994) else Color(0xFFB6B6B6),
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        trailing?.invoke(this)
    }
}

@Composable
private fun SearchSongMenu(song: PlayableSong, player: PlayerConnection?) {
    var open by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreHoriz, "More", tint = Color(0xFF909994))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(200.dp),
            offset = DpOffset(x = (-120).dp, y = 0.dp),
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
                            color = SearchInk,
                            fontSize = 13.sp,
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
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    onClick = {
                        if (OfflineDownloads.has(song.id)) {
                            confirmRemove = true
                            open = false
                        } else {
                            OfflineDownloads.download(song)
                            open = false
                        }
                    },
                )
                HorizontalDivider(color = Color(0xFFE7E9E8), thickness = 0.7.dp)
            }
            DropdownMenuItem(
                modifier = Modifier.height(44.dp),
                text = { Text("Add to queue", color = SearchInk, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.PlaylistAdd,
                        null,
                        tint = Color(0xFF8B9490),
                        modifier = Modifier.size(20.dp),
                    )
                },
                contentPadding = PaddingValues(horizontal = 12.dp),
                onClick = {
                    player?.addToQueue(song)
                    open = false
                },
            )
        }
        if (confirmRemove) {
            DeleteDownloadConfirmDialog(
                songTitle = song.title,
                onConfirm = {
                    OfflineDownloads.delete(song.id)
                    confirmRemove = false
                },
                onDismiss = { confirmRemove = false },
            )
        }
    }
}
