package team.ctrlv.musipedia

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val FavoriteAqua = Color(0xFF42E4CE)
private val FavoriteInk = Color(0xFF414944)

@Composable
internal fun LibraryScreen(active: Boolean = true, onBack: () -> Unit = {}, onExplore: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { (context.applicationContext as? MusiumApplication)?.favoriteStore }
    var favorites by remember { mutableStateOf(store?.songs().orEmpty()) }
    val player = LocalPlayerConnection.current

    LaunchedEffect(active) {
        if (active) favorites = store?.songs().orEmpty()
    }

    Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
        if (favorites.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.FavoriteBorder, null, tint = FavoriteAqua, modifier = Modifier.size(104.dp))
                Spacer(Modifier.height(34.dp))
                Text("No Favorites", color = FavoriteInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Start adding songs to your favorites\nby clicking the heart icon",
                    Modifier.padding(top = 10.dp),
                    color = Color(0xFF909994),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onExplore, modifier = Modifier.padding(top = 34.dp)) {
                    Text("Explore music", color = FavoriteAqua, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 86.dp, bottom = 118.dp)) {
                item {
                    Text("Favorites", Modifier.padding(horizontal = 28.dp, vertical = 14.dp), color = FavoriteInk, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                itemsIndexed(favorites, key = { _, song -> song.id }) { index, song ->
                    Row(
                        Modifier.fillMaxWidth().clickable { player?.play(favorites, index) }
                            .padding(horizontal = 24.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = song.title,
                            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF0F1EF)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = FavoriteInk, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(song.artist, color = Color(0xFF909994), fontSize = 12.sp, maxLines = 1)
                        }
                        IconButton(onClick = {
                            store?.remove(song.id)
                            favorites = store?.songs().orEmpty()
                        }) {
                            Icon(Icons.Outlined.Favorite, "Remove favorite", tint = FavoriteAqua)
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 24.dp)
                .size(38.dp).background(Color(0xFFE1E5E2), CircleShape),
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, "Back to Home", tint = Color(0xFF727A76))
        }
    }
}
