package com.musium.app

import androidx.compose.ui.graphics.Color

data class QuickPick(val title: String, val image: Int)
data class Mix(val title: String, val image: Int, val accent: Color)
data class BrowseCategory(val title: String, val image: Int, val color: Color)
data class LibraryItem(val title: String, val subtitle: String?, val image: Int, val round: Boolean = false)

