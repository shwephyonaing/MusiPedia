package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SettingsAqua = Color(0xFF42E4CE)
private val SettingsText = Color(0xFF4D5651)

@Composable
internal fun SettingsScreen(onBack: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 38.dp).size(38.dp).background(Color(0xFFE1E5E2), CircleShape),
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, "Settings", tint = Color(0xFF727A76))
        }
        SettingsOption("Push Notifications", "Currently on")
        SettingsOption("Account & Privacy", "Manage your account")
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dark Mode", color = SettingsText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Currently off", color = Color(0xFFB0B8B4), fontSize = 11.sp)
            }
            Switch(checked = false, onCheckedChange = {}, enabled = false)
        }
        Text("About", color = SettingsText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Feedback", color = SettingsText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text("Delete Account", color = Color(0xFFFF6464), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Sign Out", color = SettingsText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("App version 1.0", color = Color(0xFFB0B8B4), fontSize = 10.sp)
        Text("moood", Modifier.align(Alignment.End).padding(bottom = 92.dp), color = SettingsAqua, fontSize = 26.sp)
    }
}

@Composable
private fun SettingsOption(title: String, subtitle: String) {
    Column {
        Text(title, color = SettingsText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Color(0xFFB0B8B4), fontSize = 11.sp)
    }
}
