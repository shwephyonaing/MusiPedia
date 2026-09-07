package team.ctrlv.musipedia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun MusiumApp() {
    var showIntro by remember { mutableStateOf(true) }
    if (showIntro) BrandIntro(onDone = { showIntro = false }) else MusiumHomeScreen()
}
