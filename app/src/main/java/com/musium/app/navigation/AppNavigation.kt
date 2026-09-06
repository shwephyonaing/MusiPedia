package com.musium.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun MusiumApp() {
    var showIntro by remember { mutableStateOf(true) }
    if (showIntro) MooodIntro(onDone = { showIntro = false }) else MusiumHomeScreen()
}
