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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val ExploreCyan = Color(0xFF00C2CB)
private val ExploreBlack = Color(0xFF0B0B0B)

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val videos: List<YouTubeVideo>) : SearchState
    data class Error(val message: String) : SearchState
}

@Composable
internal fun ExploreScreen() {
    val repository = remember { YouTubeRepository() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var selectedVideo by remember { mutableStateOf<YouTubeVideo?>(null) }

    fun search() {
        if (query.isBlank()) return
        scope.launch {
            state = SearchState.Loading
            state = repository.searchMusic(query.trim()).fold(
                onSuccess = { SearchState.Results(it) },
                onFailure = { SearchState.Error(it.message ?: "Unable to load YouTube results.") },
            )
        }
    }

    Box(Modifier.fillMaxSize().background(ExploreBlack)) {
        Box(
            Modifier.fillMaxWidth().height(220.dp).background(
                Brush.verticalGradient(listOf(Color(0xFF09383B), Color.Transparent))
            )
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 54.dp, bottom = 110.dp),
        ) {
            item {
                Row(Modifier.padding(horizontal = 38.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("♩", color = ExploreCyan, fontSize = 34.sp)
                    Text("Search", Modifier.padding(start = 12.dp), color = ExploreCyan, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 28.dp, vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Songs, Artists, Podcasts & More") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
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
                    TextButton(onClick = ::search, enabled = state !is SearchState.Loading) {
                        Text("Go", color = ExploreCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
            when (val currentState = state) {
                SearchState.Idle -> item {
                    Text("Search YouTube Music", Modifier.padding(horizontal = 28.dp, vertical = 16.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Add a YouTube API key to local.properties, then search for a song.", Modifier.padding(horizontal = 28.dp), color = Color(0xFFB6B6B6), fontSize = 14.sp)
                }
                SearchState.Loading -> item { CircularProgressIndicator(Modifier.padding(28.dp), color = ExploreCyan) }
                is SearchState.Error -> item {
                    Text("Search failed", Modifier.padding(horizontal = 28.dp, vertical = 16.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(currentState.message, Modifier.padding(horizontal = 28.dp), color = Color(0xFFFFB4AB), fontSize = 14.sp)
                }
                is SearchState.Results -> {
                    item { Text("YouTube results", Modifier.padding(horizontal = 28.dp, vertical = 10.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    items(currentState.videos, key = { it.videoId }) { video ->
                        SearchResultRow(video, onClick = { selectedVideo = video })
                    }
                }
            }
        }
    }

    selectedVideo?.let { video ->
        YouTubePlayerDialog(video = video, onDismiss = { selectedVideo = null })
    }
}

@Composable
private fun SearchResultRow(video: YouTubeVideo, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier.size(90.dp, 58.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(video.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(video.channelName, color = Color(0xFFB6B6B6), fontSize = 13.sp, maxLines = 1)
        }
    }
}
