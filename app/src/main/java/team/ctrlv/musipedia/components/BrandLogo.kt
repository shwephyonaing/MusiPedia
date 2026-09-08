package team.ctrlv.musipedia

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BrandCyan = Color(0xFF00C2CB)
private val EqHeights = floatArrayOf(1.00f, 0.56f, 0.30f, 0.62f, 1.00f)

@Composable
internal fun BrandMark(
    modifier: Modifier = Modifier,
    color: Color = BrandCyan,
) {
    Canvas(modifier) {
        val count = EqHeights.size
        val gap = size.width * 0.085f
        val barW = (size.width - gap * (count - 1)) / count
        val radius = CornerRadius(barW / 2f, barW / 2f)
        EqHeights.forEachIndexed { i, ratio ->
            val barH = size.height * ratio
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), size.height - barH),
                size = Size(barW, barH),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
internal fun BrandLockup(
    modifier: Modifier = Modifier,
    markColor: Color = BrandCyan,
    textColor: Color = Color.White,
    textSize: TextUnit = 22.sp,
) {
    val style = brandTextStyle(textColor, textSize)
    val density = LocalDensity.current
    val markH = with(density) { (textSize.toPx() * 0.75f).toDp() }
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        BrandMark(
            Modifier
                .height(markH)
                .width(markH * 1.14f),
            markColor,
        )
        Spacer(Modifier.width(9.dp))
        Text("usiPedia", style = style)
    }
}

/**
 * Splash lockup: M-mark + "usi" holds, then "Pedia" wipes open to the right
 * (clipped — no stacking under transparent letters). Full width is reserved and
 * optical centering eases Musi → full wordmark so the mark never jumps.
 */
@Composable
internal fun AnimatedBrandLockup(
    modifier: Modifier = Modifier,
    markColor: Color = BrandCyan,
    textColor: Color = Color.White,
    textSize: TextUnit = 46.sp,
) {
    val style = brandTextStyle(textColor, textSize)
    val density = LocalDensity.current
    val markH = with(density) { (textSize.toPx() * 0.75f).toDp() }
    val markW = markH * 1.14f
    val gap = 9.dp
    val measurer = rememberTextMeasurer()
    val usiWidth = remember(style, textSize) {
        with(density) { measurer.measure("usi", style).size.width.toDp() }
    }
    val pediaWidth = remember(style, textSize) {
        with(density) { measurer.measure("Pedia", style).size.width.toDp() }
    }
    val pediaWidthPx = with(density) { pediaWidth.toPx() }
    val lockupWidth = markW + gap + usiWidth + pediaWidth

    val musiAlpha = remember { Animatable(0f) }
    val pediaReveal = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        musiAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        delay(700)
        pediaReveal.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
    }

    val reveal = pediaReveal.value.coerceIn(0f, 1f)

    Box(
        modifier
            .width(lockupWidth)
            .graphicsLayer {
                alpha = musiAlpha.value
                // Optically center on Musi first, then settle on the full lockup.
                translationX = (1f - reveal) * (pediaWidthPx * 0.5f)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(
                Modifier
                    .height(markH)
                    .width(markW),
                markColor,
            )
            Spacer(Modifier.width(gap))
            Text("usi", style = style)
            // Width wipe — glyphs stay in final place so letters never fragment.
            Box(
                Modifier
                    .width(pediaWidth * reveal)
                    .clip(RectangleShape),
            ) {
                Text(
                    "Pedia",
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(pediaWidth),
                )
            }
        }
    }
}

private fun brandTextStyle(textColor: Color, textSize: TextUnit) = TextStyle(
    color = textColor,
    fontSize = textSize,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.6).sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeight = textSize,
)
