package team.ctrlv.musipedia

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val MissingFavoriteArtistMessage =
    "Cannot find your Favorite Artist? Please let us know. We will added this very soom."

internal fun Context.openFavoriteArtistRequestEmail(artistQuery: String = "") {
    val subject = Uri.encode("MusiPedia Artist Request")
    val body = Uri.encode(
        buildString {
            append("Hi MusiPedia team,\n\n")
            append("I cannot find my favorite artist in the app. Please add:\n\n")
            if (artistQuery.isNotBlank()) {
                append(artistQuery.trim())
                append("\n")
            }
        },
    )
    val email = Intent(
        Intent.ACTION_SENDTO,
        Uri.parse("mailto:team.ctrl.v@gmail.com?subject=$subject&body=$body"),
    )
    runCatching { startActivity(email) }.onFailure {
        Toast.makeText(this, "No email app is available", Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun MissingFavoriteArtistPrompt(
    artistQuery: String = "",
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
) {
    val context = LocalContext.current
    Text(
        text = MissingFavoriteArtistMessage,
        modifier = modifier
            .fillMaxWidth()
            .clickable { context.openFavoriteArtistRequestEmail(artistQuery) }
            .padding(vertical = 8.dp),
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        textDecoration = TextDecoration.Underline,
        lineHeight = (fontSize.value + 6).sp,
    )
}
