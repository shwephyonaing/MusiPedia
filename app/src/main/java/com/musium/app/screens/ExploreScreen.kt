package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musium.innertube.SearchPage
import com.musium.innertube.YtItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Cyan = Color(0xFF00C2CB)
private val Black = Color(0xFF0B0B0B)

private sealed interface SearchUi {
    data object Idle : SearchUi
    data object Loading : SearchUi
    data class Results(val page: SearchPage) : SearchUi
    data class Error(val message: String) : SearchUi
}

private enum class SearchTab { Songs, Artists, Albums, Playlists }

@Composable
internal fun ExploreScreen() {
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

    Box(Modifier.fillMaxSize().background(Black)) {
        Box(
            Modifier.fillMaxWidth().height(220.dp).background(
                Brush.verticalGradient(listOf(Color(0xFF09383B), Color.Transparent)),
            ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 54.dp, bottom = 170.dp),
        ) {
            item {
                Row(Modifier.padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(Modifier.height(28.dp).width(32.dp), Cyan)
                    Text("Search", Modifier.padding(start = 12.dp), color = Cyan, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 26.dp)
                        .testTag("searchField")
                        .onFocusChanged { focused = it.isFocused },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                lastSearched = ""
                                state = SearchUi.Idle
                                focused = true
                            }) {
                                Icon(Icons.Outlined.Close, null)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFD9D9D9),
                        unfocusedContainerColor = Color(0xFFD9D9D9),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
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
                    SearchUi.Loading -> item { CircularProgressIndicator(Modifier.padding(28.dp), color = Cyan) }
                    is SearchUi.Error -> item {
                        TextButton(onClick = { search() }, enabled = query.isNotBlank(), modifier = Modifier.padding(28.dp)) {
                            Text("Retry", color = Cyan, fontWeight = FontWeight.Bold)
                        }
                    }
                    is SearchUi.Results -> {
                        val page = current.page
                        if (page.isEmpty) {
                            item { Text("No results", Modifier.padding(28.dp), color = Color.White, fontSize = 18.sp) }
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
                                                labelColor = Color.White,
                                                containerColor = Color(0xFF172023),
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
                            items(visible, key = { it::class.simpleName + it.id }) { item ->
                                MusicItemRow(item, round = tab == SearchTab.Artists) {
                                    item.open(player, router, page.songs.map { it.toPlayable() })
                                }
                            }
                        }
                    }
                }
            }
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
        Text(text, Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp), Color.White, 16.sp, maxLines = 1)
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, null, tint = Color(0xFFB6B6B6))
            }
        }
    }
}

@Composable
internal fun MusicItemRow(item: YtItem, round: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            item.thumbnail,
            item.title,
            Modifier.size(58.dp).clip(if (round) CircleShape else RoundedCornerShape(6.dp)).background(Color.DarkGray),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(item.subtitle ?: item::class.simpleName.orEmpty().removeSuffix("Item"), color = Color(0xFFB6B6B6), fontSize = 13.sp, maxLines = 1)
        }
    }
}
