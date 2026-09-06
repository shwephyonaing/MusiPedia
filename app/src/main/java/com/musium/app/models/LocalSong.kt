package com.musium.app

import android.net.Uri

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val audioUri: Uri,
    val artworkUri: Uri?,
)
