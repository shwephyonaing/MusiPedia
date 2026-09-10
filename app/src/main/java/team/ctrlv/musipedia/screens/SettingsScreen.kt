package team.ctrlv.musipedia

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val SettingsAqua = Color(0xFF42E4CE)

@Composable
internal fun SettingsScreen(
    darkMode: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    onDownloads: () -> Unit = {},
    onPersonalize: () -> Unit = {},
) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    val surface = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val backColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier.fillMaxSize().background(surface).safeDrawingPadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 38.dp).size(38.dp).background(backColor, CircleShape),
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsOption(
            "Personalize",
            "Artists for your For You page",
            textColor,
            mutedColor,
            onPersonalize,
        )
        SettingsOption("Downloads", "Songs available offline", textColor, mutedColor, onDownloads)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dark Mode", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(if (darkMode) "Currently on" else "Currently off", color = mutedColor, fontSize = 11.sp)
            }
            Switch(
                checked = darkMode,
                onCheckedChange = onDarkModeChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = SettingsAqua,
                    checkedThumbColor = Color.White,
                ),
            )
        }
        SettingsOption("About", "About MusiPedia", textColor, mutedColor) { showAbout = true }
        SettingsOption("Feedback", "To report a bug or request a feature", textColor, mutedColor) {
            val email = Intent(
                Intent.ACTION_SENDTO,
                Uri.parse("mailto:team.ctrl.v@gmail.com?subject=MusiPedia%20Feedback"),
            )
            runCatching { context.startActivity(email) }.onFailure {
                Toast.makeText(context, "No email app is available", Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.weight(1f))
        Text("App version 1.0.1", Modifier.padding(bottom = 92.dp), color = mutedColor, fontSize = 10.sp)
    }
    if (showAbout) AboutMusiPediaDialog(onDismiss = { showAbout = false })
}

@Composable
private fun AboutMusiPediaDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(280.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLockup(
                markColor = MaterialTheme.colorScheme.primary,
                textColor = MaterialTheme.colorScheme.onSurface,
                textSize = 24.sp,
            )
            Text(
                "Discover music made for you",
                modifier = Modifier.padding(top = 14.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
            )
            Text(
                "MusiPedia helps you discover trending music and personalized recommendations, save favorites, and enjoy downloaded songs offline.",
                modifier = Modifier.padding(top = 9.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "Made by the Ctrl V team",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 6.dp)) {
                Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsOption(
    title: String,
    subtitle: String,
    textColor: Color,
    mutedColor: Color,
    onClick: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ).padding(vertical = 2.dp),
    ) {
        Text(title, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = mutedColor, fontSize = 11.sp)
    }
}
