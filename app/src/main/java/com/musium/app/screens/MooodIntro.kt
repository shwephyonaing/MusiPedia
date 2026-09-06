package com.musium.app

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MooodAqua = Color(0xFF42E4CE)

@Composable
internal fun MooodIntro(onDone: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF62D9DD), MooodAqua)),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text("moood", color = Color.White, fontSize = 70.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(116.dp))
            Text("Discover Alternative Music", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Find music that fits your mood and make every listen your own.",
                Modifier.padding(top = 14.dp),
                color = Color.White.copy(alpha = .88f),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
            )
            Text("●  ○  ○", Modifier.padding(top = 14.dp), color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(.7f).height(50.dp).clip(RoundedCornerShape(28.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MooodAqua),
            ) { Text("Next", fontSize = 16.sp) }
            Text("Skip", Modifier.padding(top = 20.dp), color = Color.White, fontSize = 15.sp)
        }
    }
}
