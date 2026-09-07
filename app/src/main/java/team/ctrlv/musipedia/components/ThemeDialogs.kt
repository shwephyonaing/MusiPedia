package team.ctrlv.musipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

private val DialogInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface

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
                .width(280.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(
                        if (confirmDestructive) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (confirmDestructive) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                color = DialogInk,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (confirmDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (confirmDestructive) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp),
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.height(38.dp)) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
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
