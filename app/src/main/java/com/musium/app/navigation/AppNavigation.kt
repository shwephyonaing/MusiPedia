package com.musium.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

private val AppBlack = Color(0xFF121111)
private val AppCyan = Color(0xFF00C2CB)
private val ButtonCyan = Color(0xFF06A0B5)

internal enum class AppRoute { Splash, Welcome, SignUp, Login, Main, Screens }

@Composable
fun MusiumApp() {
    var route by remember { mutableStateOf(AppRoute.Splash) }
    when (route) {
        AppRoute.Splash -> SplashScreen { route = AppRoute.Welcome }
        AppRoute.Welcome -> WelcomeScreen { route = AppRoute.SignUp }
        AppRoute.SignUp -> SignUpScreen({ route = AppRoute.Login }, { route = AppRoute.Login })
        AppRoute.Login -> LoginScreen({ route = AppRoute.SignUp }) { route = AppRoute.Main }
        AppRoute.Main -> MusiumHomeScreen { route = AppRoute.Screens }
        AppRoute.Screens -> AllScreensPage(onBack = { route = AppRoute.Main })
    }
}


