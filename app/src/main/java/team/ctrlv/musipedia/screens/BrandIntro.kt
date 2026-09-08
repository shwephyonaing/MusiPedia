package team.ctrlv.musipedia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private val SplashDeep = Color(0xFF2BB8B8)
private val SplashMid = Color(0xFF42E4CE)
private val SplashLight = Color(0xFF7AEFE0)
private val SplashFog = Color(0x66FFFFFF)

/** Enough for the brand lockup; Home finishes For You after splash. */
private const val MinSplashMs = 1_800L
/** Cap wait on slow networks — Home can finish loading after splash. */
private const val MaxSplashWaitMs = 7_000L

private sealed interface SplashState {
    data object Loading : SplashState
    data class Error(val message: String) : SplashState
}

@Composable
internal fun BrandIntro(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = remember {
        context.applicationContext as? MusiumApplication
    }
    val recentStore = remember { app?.recentStore }
    var state by remember { mutableStateOf<SplashState>(SplashState.Loading) }
    var request by remember { mutableIntStateOf(0) }
    var showTagline by remember { mutableStateOf(false) }

    val breath = rememberInfiniteTransition(label = "splashBreath")
    val breathScale by breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashBreathScale",
    )
    val shimmer by breath.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashShimmer",
    )
    val barSweep by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashBarSweep",
    )

    LaunchedEffect(Unit) {
        // After Musi lands + Pedia finishes sliding out (~420+640+760).
        delay(1950)
        showTagline = true
    }

    LaunchedEffect(request) {
        state = SplashState.Loading
        val result = runCatching {
            coroutineScope {
                val minimumSplash = async { delay(MinSplashMs) }
                val home = async {
                    withTimeoutOrNull(MaxSplashWaitMs) {
                        MusicRepository.homeReady(
                            recentStore?.songs().orEmpty().take(5),
                            app?.tasteStore?.artists().orEmpty(),
                            force = false,
                        )
                    }
                }
                minimumSplash.await()
                home.await()
            }
        }
        val sections = result.getOrNull().orEmpty()
        if (sections.any { it.items.isNotEmpty() }) {
            onDone()
        } else {
            state = SplashState.Error(
                result.exceptionOrNull()?.message ?: "Couldn't load music. Check your connection.",
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SplashLight, SplashMid, SplashDeep),
                ),
            ),
    ) {
        // Soft light bloom behind the mark — atmosphere without clutter.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(320.dp)
                .graphicsLayer { alpha = shimmer }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset.Unspecified,
                        radius = 480f,
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 12.dp)
                .size(180.dp)
                .graphicsLayer { alpha = 0.22f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    scaleX = breathScale
                    scaleY = breathScale
                },
            ) {
                AnimatedBrandLockup(
                    markColor = Color.White,
                    textColor = Color.White,
                    textSize = 46.sp,
                )
                AnimatedVisibility(
                    visible = showTagline,
                    enter = fadeIn(tween(520, easing = FastOutSlowInEasing)),
                    exit = fadeOut(),
                ) {
                    Text(
                        "Discover music you love",
                        modifier = Modifier.padding(top = 18.dp),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 0.2.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            val error = state as? SplashState.Error
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(tween(280)),
                exit = fadeOut(tween(180)),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Text(
                        error?.message.orEmpty(),
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                    Button(
                        onClick = { request += 1 },
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = SplashDeep,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        Text("Try again", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onDone) {
                        Text("Continue offline", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    }
                }
            }

            if (error == null) {
                // Quiet progress cue — thin bar, not a loud CTA.
                Box(
                    Modifier
                        .padding(bottom = 36.dp)
                        .width(42.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SplashFog),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.45f)
                            .height(3.dp)
                            .graphicsLayer {
                                translationX = (42.dp.toPx() - size.width) * barSweep
                            }
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White),
                    )
                }
            }
        }
    }
}
