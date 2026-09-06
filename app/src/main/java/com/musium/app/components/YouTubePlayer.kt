package com.musium.app

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerDialog(video: YouTubeVideo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(video.title) },
        text = {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        val safeId = video.videoId.replace(Regex("[^A-Za-z0-9_-]"), "")
                        val referrer = "https://${context.packageName}"
                        val playerUrl = "https://www.youtube.com/embed/$safeId" +
                            "?playsinline=1&rel=0&origin=${Uri.encode(referrer)}"

                        // YouTube requires a client identity for WebView embeds.
                        loadUrl(playerUrl, mapOf("Referer" to referrer))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(230.dp),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
