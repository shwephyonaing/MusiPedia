package com.musium.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

private val Cyan = Color(0xFF00C2CB)
private val PageBlack = Color(0xFF0B0B0B)
private val Tile = Color(0xFF172023)

@Composable
internal fun HomeContent(onOpenAllScreens: () -> Unit) {
    val quickPicks = listOf(
        QuickPick("Coffee & Jazz", R.drawable.coffee_jazz),
        QuickPick("RELEASED", R.drawable.released),
        QuickPick("Anything Goes", R.drawable.anything_goes),
        QuickPick("Anime OSTs", R.drawable.anime_ost),
        QuickPick("Harry’s House", R.drawable.harrys_house),
        QuickPick("Lo-Fi Beats", R.drawable.lofi_beats),
    )
    val mixes = listOf(
        Mix("Pop Mix", R.drawable.pop_mix, Color(0xFFFF7777)),
        Mix("Chill Mix", R.drawable.chill_mix, Color(0xFFFFFA77)),
        Mix("Kpop", R.drawable.kpop_mix, Color(0xFF77FF95)),
    )

    Box(Modifier.fillMaxSize().background(PageBlack)) {
        Box(
            Modifier.fillMaxWidth().height(235.dp).background(
                Brush.verticalGradient(listOf(Color(0xFF09383B), Color.Transparent))
            )
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 46.dp, bottom = 104.dp),
        ) {
            item { Header(onOpenAllScreens) }
            item { SectionTitle("Continue Listening", Modifier.padding(top = 28.dp)) }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    quickPicks.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { QuickPickCard(it, Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item { SectionTitle("Your Top Mixes", Modifier.padding(top = 18.dp)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(31.dp),
                ) { items(mixes) { MixCard(it) } }
            }
            item { SectionTitle("Based on your recent listening", Modifier.padding(top = 42.dp)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                ) {
                    item { AlbumArt(R.drawable.recent_harry) }
                    item { AlbumArt(R.drawable.recent_cassette) }
                }
            }
        }
    }
}

@Composable
internal fun Header(onOpenAllScreens: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.avatar), null,
            Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(Cyan).padding(2.dp).clip(RoundedCornerShape(50)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(start = 15.dp).weight(1f)) {
            Text("Welcome back !", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("chandrama", color = Color.White.copy(.58f), fontSize = 12.sp)
        }
        Icon(Icons.Outlined.Equalizer, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Icon(Icons.Outlined.Notifications, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Icon(Icons.Outlined.Settings, "All screens", tint = Color.White, modifier = Modifier.size(34.dp).clickable(onClick = onOpenAllScreens).padding(6.dp))
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 26.dp, vertical = 8.dp), Color.White, 20.sp, fontWeight = FontWeight.Bold)
}

@Composable
internal fun QuickPickCard(pick: QuickPick, modifier: Modifier = Modifier) {
    Row(modifier.height(55.dp).clip(RoundedCornerShape(10.dp)).background(Tile), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(pick.image), null, Modifier.size(55.dp), contentScale = ContentScale.Crop)
        Text(pick.title, Modifier.padding(horizontal = 13.dp), Color.White, 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun MixCard(mix: Mix) {
    Box(Modifier.width(150.dp).height(150.dp).clip(RoundedCornerShape(2.dp))) {
        Image(painterResource(mix.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text(mix.title, Modifier.padding(17.dp, 9.dp), Color.White, 15.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(7.dp).background(mix.accent))
    }
}

@Composable
internal fun AlbumArt(image: Int) {
    Image(painterResource(image), null, Modifier.size(182.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
}


