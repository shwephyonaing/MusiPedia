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

@Composable
internal fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { delay(1400); onFinished() }
    Box(Modifier.fillMaxSize().background(AppBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("♪", color = AppCyan, fontSize = 150.sp, fontWeight = FontWeight.Bold)
            Image(painterResource(R.drawable.musium_wordmark), "Musium", Modifier.width(170.dp).height(50.dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
internal fun WelcomeScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF41C3D6))) {
        Box(Modifier.fillMaxWidth().height(550.dp)) {
            Image(painterResource(R.drawable.welcome_girl), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.offset(25.dp, 28.dp).size(149.dp).clip(CircleShape).background(Color(0x22006C79)))
            Box(Modifier.align(Alignment.TopEnd).offset((-50).dp, 74.dp).size(78.dp).clip(CircleShape).background(Color(0x22006C79)))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(400.dp).clip(RoundedCornerShape(topStart = 54.dp, topEnd = 54.dp))
                .background(Color(0xEF061012)).padding(horizontal = 25.dp, vertical = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("From the latest to the\ngreatest hits, play your\nfavorite tracks on musium\nnow!", color = Color.White, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Row { Box(Modifier.width(58.dp).height(8.dp).clip(CircleShape).background(AppCyan)); Box(Modifier.width(50.dp).height(8.dp).clip(CircleShape).background(Color(0xFFDBE7E8))) }
            Spacer(Modifier.height(38.dp))
            PrimaryButton("Get Started", onStart)
        }
    }
}


