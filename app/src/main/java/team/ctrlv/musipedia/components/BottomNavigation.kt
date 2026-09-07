package team.ctrlv.musipedia

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

private val Cyan = Color(0xFF42E4CE)

@Composable
internal fun BottomNavigation(selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(62.dp).background(Color.White)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem("Search", Icons.Outlined.Search, selected == 1) { onSelected(1) }
        NavItem("Favorites", Icons.Outlined.FavoriteBorder, selected == 2) { onSelected(2) }
        NavItem("Settings", Icons.Outlined.Menu, selected == 3) { onSelected(3) }
    }
}

@Composable
internal fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Cyan else Color(0xFF6F7672)
    Column(
        Modifier.fillMaxHeight().width(72.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(28.dp))
    }
}
