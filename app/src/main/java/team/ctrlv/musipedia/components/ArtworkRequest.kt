package team.ctrlv.musipedia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest

/**
 * Decode art only as large as the on-screen tile — full-res YouTube thumbs cause scroll jank.
 */
object ArtworkSizes {
    val Poster = 158.dp
    val Mini = 48.dp
    val HeroMaxWidth = 480.dp
    val Profile = 96.dp
    val ListThumb = 56.dp
}

@Composable
fun rememberArtworkRequest(
    data: Any?,
    size: Dp,
    crossfade: Boolean = false,
): ImageRequest {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    return remember(data, px, crossfade) {
        ImageRequest.Builder(context)
            .data(data)
            .size(px)
            .crossfade(crossfade)
            .build()
    }
}

@Composable
fun rememberArtworkRequestPx(
    data: Any?,
    sizePx: Int,
    crossfade: Boolean = false,
): ImageRequest {
    val context = LocalContext.current
    val px = sizePx.coerceAtLeast(1)
    return remember(data, px, crossfade) {
        ImageRequest.Builder(context)
            .data(data)
            .size(px)
            .crossfade(crossfade)
            .build()
    }
}
