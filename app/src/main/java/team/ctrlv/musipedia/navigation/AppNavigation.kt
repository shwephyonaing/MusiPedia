package team.ctrlv.musipedia

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private enum class LaunchGate { Splash, Taste, Home }

@Composable
fun MusiumApp() {
    val context = LocalContext.current
    val tasteStore = remember {
        (context.applicationContext as MusiumApplication).tasteStore
    }
    var gate by rememberSaveable {
        mutableStateOf(LaunchGate.Splash)
    }

    AnimatedContent(
        targetState = gate,
        transitionSpec = {
            fadeIn(tween(280, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(180)) using SizeTransform(clip = false)
        },
        label = "launchGate",
    ) { step ->
        when (step) {
            LaunchGate.Splash -> BrandIntro(
                onDone = {
                    gate = if (tasteStore.hasOnboarded()) LaunchGate.Home else LaunchGate.Taste
                },
            )
            LaunchGate.Taste -> TastePickerScreen(
                store = tasteStore,
                title = "Personalize",
                subtitle = "Please choose at least 3 artists to personalize your interface",
                confirmLabel = "Continue",
                onDone = { gate = LaunchGate.Home },
            )
            LaunchGate.Home -> MusiumHomeScreen()
        }
    }
}
