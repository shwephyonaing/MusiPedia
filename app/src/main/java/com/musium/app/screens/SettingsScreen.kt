package com.musium.app

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SettingsAqua = Color(0xFF42E4CE)

@Composable
internal fun SettingsScreen(
    darkMode: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    onDownloads: () -> Unit = {},
) {
    val surface = if (darkMode) Color(0xFF121413) else Color.White
    val textColor = if (darkMode) Color(0xFFF2F4F3) else Color(0xFF4D5651)
    val mutedColor = if (darkMode) Color(0xFF8E9993) else Color(0xFFB0B8B4)
    val backColor = if (darkMode) Color(0xFF303532) else Color(0xFFE1E5E2)
    Column(
        Modifier.fillMaxSize().background(surface).safeDrawingPadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 38.dp).size(38.dp).background(backColor, CircleShape),
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, "Settings", tint = if (darkMode) Color.White else Color(0xFF727A76))
        }
        SettingsOption("Downloads", "Songs available offline", textColor, mutedColor, onDownloads)
        SettingsOption("Account & Privacy", "Manage your account", textColor, mutedColor)
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
        Text("About", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Feedback", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text("App version 1.0", Modifier.padding(bottom = 92.dp), color = mutedColor, fontSize = 10.sp)
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
