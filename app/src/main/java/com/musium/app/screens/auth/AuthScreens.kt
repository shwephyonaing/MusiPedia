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
internal fun SignUpScreen(onBack: () -> Unit, onLogin: () -> Unit) {
    AuthPage(onBack) {
        LogoBlock()
        Text("Let’s get you in", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(38.dp))
        SocialButton("G", "Continue with Google")
        SocialButton("f", "Continue with Facebook")
        SocialButton("●", "Continue with Apple")
        OrDivider()
        PrimaryButton("Log in with a password", onLogin)
        Spacer(Modifier.height(20.dp))
        Text("Don’t have an account? Sign Up", Modifier.clickable(onClick = onLogin), color = Color.White, fontSize = 15.sp)
    }
}

@Composable
internal fun LoginScreen(onSignUp: () -> Unit, onLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AuthPage(onSignUp) {
        LogoBlock()
        Text("Login to your account", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(34.dp))
        AuthField(email, { email = it }, "Email")
        Spacer(Modifier.height(22.dp))
        AuthField(password, { password = it }, "Password", true)
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).border(2.dp, AppCyan, RoundedCornerShape(5.dp)))
            Text("Remember me", Modifier.padding(start = 12.dp), Color.White, 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(38.dp))
        PrimaryButton("Log in", onLogin)
        Text("Forgot the password?", Modifier.padding(20.dp), AppCyan, 14.sp, fontWeight = FontWeight.Bold)
        OrDivider("or continue with")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("G", "f", "●").forEach { Text(it, Modifier.size(50.dp).clip(CircleShape).border(1.dp, Color.Gray, CircleShape).wrapContentSize(), Color.White, 24.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(20.dp))
        Text("Don’t have an account? Sign Up", Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onSignUp), Color.White, 15.sp)
    }
}

@Composable
internal fun AuthPage(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(AppBlack), contentPadding = PaddingValues(20.dp, 44.dp, 20.dp, 38.dp)) {
        item { Column { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(32.dp).clickable(onClick = onBack)); Spacer(Modifier.height(8.dp)); content() } }
    }
}

@Composable internal fun LogoBlock() = Box(Modifier.fillMaxWidth().height(255.dp), contentAlignment = Alignment.Center) { Text("♪", color = AppCyan, fontSize = 145.sp, fontWeight = FontWeight.Bold) }

@Composable
internal fun SocialButton(mark: String, label: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).height(60.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF555555), RoundedCornerShape(12.dp)).padding(horizontal = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(mark, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, Modifier.padding(start = 20.dp), Color.White, 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun AuthField(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), placeholder = { Text(label) }, leadingIcon = { Icon(if (password) Icons.Outlined.Lock else Icons.Outlined.Email, null) }, visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, singleLine = true, shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AppCyan, unfocusedBorderColor = Color(0xFF555555), focusedContainerColor = Color(0xFF1D1D1D), unfocusedContainerColor = Color(0xFF1D1D1D)))
}

@Composable
internal fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(60.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(ButtonCyan)) { Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun OrDivider(label: String = "or") {
    Row(Modifier.fillMaxWidth().padding(vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f), color = Color.White); Text(label, Modifier.padding(horizontal = 18.dp), Color.White, 15.sp, fontWeight = FontWeight.Bold); HorizontalDivider(Modifier.weight(1f), color = Color.White) }
}


