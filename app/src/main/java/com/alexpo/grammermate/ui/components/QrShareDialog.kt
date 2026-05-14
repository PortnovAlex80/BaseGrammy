package com.alexpo.grammermate.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.alexzhirkevich.qrose.options.QrOptions
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

@Composable
fun QrShareDialog(
    promptRu: String,
    answerText: String,
    targetLanguage: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val langLabel = targetLanguage.uppercase()
    val qrText = "RU: $promptRu\n$langLabel: $answerText"

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            TextButton(onClick = {
                val url = "https://translate.google.com/?sl=ru&tl=$targetLanguage&text=${Uri.encode(promptRu)}"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) {
                Text("Open in Google Translate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "RU: $promptRu",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "$langLabel: $answerText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Image(
                    painter = rememberQrCodePainter(qrText) {
                        colors {
                            dark = io.github.alexzhirkevich.qrose.options.QrBrush.solid(androidx.compose.ui.graphics.Color.Black)
                            light = io.github.alexzhirkevich.qrose.options.QrBrush.solid(androidx.compose.ui.graphics.Color.White)
                        }
                        shapes {
                            ball = io.github.alexzhirkevich.qrose.options.QrBallShape.roundCorners(radius = .25f)
                            darkPixel = io.github.alexzhirkevich.qrose.options.QrPixelShape.roundCorners(radius = .25f)
                        }
                    },
                    contentDescription = "QR code with translation pair",
                    modifier = Modifier.size(240.dp)
                )
            }
        }
    )
}
