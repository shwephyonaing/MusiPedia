package team.ctrlv.musipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val TasteAqua = Color(0xFF42E4CE)
private const val PersonalizeTimeoutMs = 14_000L
private const val PersonalizeMinShowMs = 700L

/**
 * First-run and Settings personalization: pick ≥3 artists.
 * Search filters the curated catalog only (Myanmar-safe — no live YTM).
 */
@Composable
internal fun TastePickerScreen(
    store: TasteStore,
    title: String = "Personalize",
    subtitle: String = "Please choose at least 3 artists to personalize your interface",
    confirmLabel: String = "Continue",
    onDone: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val mute = MaterialTheme.colorScheme.onSurfaceVariant
    val page = MaterialTheme.colorScheme.background
    val tile = MaterialTheme.colorScheme.surfaceVariant
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as? MusiumApplication

    val catalog = app?.tasteCatalogStore
    val emptyCatalog = remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<TasteArtist>()) }
    val catalogArtists by (catalog?.artists ?: emptyCatalog).collectAsState()
    val initialArtists = remember { store.artists().associateBy { it.id } }
    var selectedArtistIds by remember {
        mutableStateOf(initialArtists.keys.toSet())
    }
    var query by remember { mutableStateOf("") }
    var personalizing by remember { mutableStateOf(false) }
    val thumbnails = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) {
        // Always try GitHub when opening the picker so newly pushed artists appear
        // without waiting for the background TTL (still falls back instantly offline).
        catalog?.refresh(force = true)
    }

    LaunchedEffect(catalogArtists) {
        catalogArtists.forEach { artist ->
            artist.thumbnailUrl?.let { thumbnails.putIfAbsent(artist.id, it) }
        }
        initialArtists.values.forEach { artist ->
            artist.thumbnailUrl?.let { thumbnails.putIfAbsent(artist.id, it) }
        }
        coroutineScope {
            catalogArtists
                .filter { thumbnails[it.id].isNullOrBlank() }
                .chunked(8)
                .forEach { chunk ->
                    chunk.map { artist ->
                        async {
                            val url = runCatching {
                                MusicRepository.artistAvatar(artist.id, artist.name)
                            }.getOrNull()
                            if (!url.isNullOrBlank()) thumbnails[artist.id] = url
                        }
                    }.awaitAll()
                }
        }
    }

    val canContinue = selectedArtistIds.size >= TasteStore.MIN_ARTISTS && !personalizing
    val filteredArtists = remember(query, catalogArtists) {
        val q = query.trim()
        if (q.isEmpty()) {
            catalogArtists
        } else {
            catalogArtists.filter { it.name.contains(q, ignoreCase = true) }
        }
    }

    fun startPersonalizing() {
        if (personalizing) return
        val artists = catalogArtists
            .filter { it.id in selectedArtistIds }
            .map { it.copy(thumbnailUrl = thumbnails[it.id] ?: it.thumbnailUrl) }
        personalizing = true
        focusManager.clearFocus()
        scope.launch {
            val started = System.currentTimeMillis()
            store.save(artists)
            MusicRepository.clearHomeCache()
            // Warm For You / fav-artist rails so home isn't empty on slow networks.
            withTimeoutOrNull(PersonalizeTimeoutMs) {
                val recent = app?.recentStore?.songs().orEmpty().take(5)
                MusicRepository.home(recent, artists, force = true)
            }
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < PersonalizeMinShowMs) {
                delay(PersonalizeMinShowMs - elapsed)
            }
            personalizing = false
            onDone()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(page)
            .safeDrawingPadding(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(3) }) {
                Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                    Text(
                        title,
                        color = ink,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        subtitle,
                        modifier = Modifier.padding(top = 8.dp),
                        color = mute,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Text(
                        "${selectedArtistIds.size} selected · pick ${TasteStore.MIN_ARTISTS}+",
                        color = if (selectedArtistIds.size >= TasteStore.MIN_ARTISTS) TasteAqua else mute,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !personalizing,
                        singleLine = true,
                        placeholder = {
                            Text("Search artists", color = mute, fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = mute)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }, enabled = !personalizing) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = mute)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { focusManager.clearFocus() },
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TasteAqua,
                            unfocusedBorderColor = ink.copy(alpha = 0.12f),
                            focusedTextColor = ink,
                            unfocusedTextColor = ink,
                            cursorColor = TasteAqua,
                            focusedContainerColor = tile.copy(alpha = 0.45f),
                            unfocusedContainerColor = tile.copy(alpha = 0.35f),
                        ),
                    )
                }
            }
            if (filteredArtists.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Text(
                        "No artists match “$query”",
                        modifier = Modifier.padding(vertical = 28.dp),
                        color = mute,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                items(filteredArtists, key = { it.id }) { artist ->
                    val selected = artist.id in selectedArtistIds
                    ArtistPickCell(
                        name = artist.name,
                        imageUrl = thumbnails[artist.id],
                        selected = selected,
                        tile = tile,
                        ink = ink,
                        onClick = {
                            if (personalizing) return@ArtistPickCell
                            selectedArtistIds = if (selected) {
                                selectedArtistIds - artist.id
                            } else {
                                selectedArtistIds + artist.id
                            }
                        },
                    )
                }
            }
        }

        Button(
            onClick = { startPersonalizing() },
            enabled = canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TasteAqua,
                contentColor = Color(0xFF083F3C),
                disabledContainerColor = mute.copy(alpha = 0.22f),
                disabledContentColor = mute,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(confirmLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (personalizing) {
        PersonalizingDialog()
    }
}

@Composable
private fun PersonalizingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            Modifier
                .width(280.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = TasteAqua,
                strokeWidth = 3.dp,
            )
            Text(
                "Personalizing your interface",
                modifier = Modifier.padding(top = 18.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
            )
            Text(
                "Building your For You feed. This may take a moment on a slow connection.",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ArtistPickCell(
    name: String,
    imageUrl: String?,
    selected: Boolean,
    tile: Color,
    ink: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) TasteAqua else ink.copy(alpha = 0.12f),
                        shape = CircleShape,
                    )
                    .padding(if (selected) 3.dp else 0.dp)
                    .clip(CircleShape)
                    .background(tile),
                contentAlignment = Alignment.Center,
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        name.take(1).uppercase(),
                        color = ink.copy(alpha = 0.55f),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(TasteAqua),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color(0xFF083F3C),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            name,
            modifier = Modifier.padding(top = 8.dp),
            color = ink,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
        )
    }
}
