package team.ctrlv.musipedia

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun MusiumApp() {
    var showIntro by rememberSaveable { mutableStateOf(true) }
    AnimatedContent(
        targetState = showIntro,
        transitionSpec = {
            if (!targetState) {
                (fadeIn(tween(520, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        initialScale = 0.985f,
                        animationSpec = tween(520, easing = FastOutSlowInEasing),
                    )) togetherWith fadeOut(tween(360))
            } else {
                fadeIn(tween(420)) togetherWith fadeOut(tween(300))
            }.using(SizeTransform(clip = false))
        },
        label = "splashToHome",
    ) { splashVisible ->
        if (splashVisible) {
            BrandIntro(onDone = { showIntro = false })
        } else {
            MusiumHomeScreen()
        }
    }
}
