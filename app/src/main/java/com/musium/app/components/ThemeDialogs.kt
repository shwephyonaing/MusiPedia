package com.musium.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val DialogCyan = Color(0xFF42E4CE)
private val DialogInk = Color(0xFF414944)

@Composable
internal fun ThemedConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector = Icons.Outlined.DeleteOutline,
    confirmDestructive: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAFAF8), RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(
                        if (confirmDestructive) Color(0xFFFFE8E6) else DialogCyan.copy(alpha = 0.16f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (confirmDestructive) Color(0xFFE05B52) else DialogCyan,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                color = DialogInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = Color(0xFF7A8480),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (confirmDestructive) Color(0xFFE05B52) else DialogCyan,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8B9490), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun DeleteDownloadConfirmDialog(
    songTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ThemedConfirmDialog(
        title = "Remove download?",
        message = "“$songTitle” will be deleted from this device. You can download it again later.",
        confirmLabel = "Remove",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
