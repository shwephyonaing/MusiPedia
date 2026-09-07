package team.ctrlv.musipedia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val style = TextStyle(
        color = textColor,
        fontSize = textSize,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.6).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeight = textSize,
    )
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
