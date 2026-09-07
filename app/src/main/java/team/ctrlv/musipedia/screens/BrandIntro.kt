package team.ctrlv.musipedia

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private val SplashAqua = Color(0xFF42E4CE)

private sealed interface SplashState {
    data object Loading : SplashState
    data class Error(val message: String) : SplashState
}

@Composable
internal fun BrandIntro(onDone: () -> Unit) {
    val context = LocalContext.current
    val recentStore = remember {
        (context.applicationContext as? MusiumApplication)?.recentStore
    }
    var state by remember { mutableStateOf<SplashState>(SplashState.Loading) }
    var request by remember { mutableIntStateOf(0) }
    val animation = rememberInfiniteTransition(label = "splashLogo")
    val logoScale by animation.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashLogoScale",
    )
    val logoAlpha by animation.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashLogoAlpha",
    )

    LaunchedEffect(request) {
        state = SplashState.Loading
        val result = runCatching {
            coroutineScope {
                val minimumSplash = async { delay(900) }
                val home = async {
                    MusicRepository.home(recentStore?.songs().orEmpty().take(5), force = false)
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
            .background(Brush.verticalGradient(listOf(Color(0xFF65D7D8), SplashAqua))),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 54.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))
            BrandLockup(
                modifier = Modifier.graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    alpha = logoAlpha
                },
                markColor = Color.White,
                textColor = Color.White,
                textSize = 42.sp,
            )
            Text(
                "Discover music you love",
                modifier = Modifier.padding(top = 18.dp),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))

            val error = state as? SplashState.Error
            if (error != null) {
                Text(
                    error.message,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            Button(
                onClick = {
                    if (state is SplashState.Error) request += 1
                },
                enabled = state is SplashState.Error,
                modifier = Modifier.fillMaxWidth(0.72f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = SplashAqua,
                    disabledContainerColor = Color.White,
                    disabledContentColor = SplashAqua,
                ),
            ) {
                if (state is SplashState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(21.dp),
                        color = SplashAqua,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("Loading music…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Retry", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (error != null) {
                TextButton(onClick = onDone) {
                    Text("Continue offline", color = Color.White, fontSize = 13.sp)
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
