package com.musium.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private val DiscInk = Color(0xFF0B0B0B)
private val DiscRing = Color(0x66FFFFFF)
private val DiscSheen = Color(0x33FFFFFF)
private val SpindleCyan = Color(0xFF00C2CB)

@Composable
internal fun CdDisc(
    artworkUrl: String?,
    contentDescription: String?,
    playing: Boolean,
    modifier: Modifier = Modifier,
    hole: Dp = 36.dp,
) {
    val spin by rememberInfiniteTransition(label = "cd").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_200, easing = LinearEasing),
        ),
        label = "spin",
    )
    var held by remember(artworkUrl) { mutableFloatStateOf(0f) }
    var lastPlaying by remember { mutableStateOf(false) }
    if (playing != lastPlaying) {
        if (!playing) held = spin
        lastPlaying = playing
    }
    val angle = if (playing) spin else held

    Box(
        modifier
            .shadow(12.dp, CircleShape)
            .rotate(angle)
            .clip(CircleShape)
            .background(Color(0xFF1A2224)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            artworkUrl,
            contentDescription,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    0.42f to Color.Transparent,
                    0.78f to Color(0x22000000),
                    1f to Color(0x66000000),
                    center = center,
                    radius = r,
                ),
            )
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        DiscSheen,
                        Color(0x2280E8FF),
                        Color.Transparent,
                        DiscSheen,
                        Color.Transparent,
                    ),
                    center = center,
                ),
                radius = r,
            )
            listOf(0.62f, 0.74f, 0.86f).forEach { t ->
                drawCircle(
                    color = DiscRing,
                    radius = r * t,
                    center = center,
                    style = Stroke(width = 1.2f),
                )
            }
        }
        Box(
            Modifier
                .size(hole)
                .clip(CircleShape)
                .background(DiscInk),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(hole * 0.34f)
                    .clip(CircleShape)
                    .background(SpindleCyan),
            )
        }
    }
}
