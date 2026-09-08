package com.alexpo.grammermate.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.alexpo.grammermate.R
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_flag_ru),
                        contentDescription = "RU",
                        modifier = Modifier.size(14.dp, 10.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = promptRu,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(
                            when (targetLanguage.lowercase()) {
                                "it", "italian" -> R.drawable.ic_flag_it
                                "en", "english" -> R.drawable.ic_flag_en
                                "el", "greek" -> R.drawable.ic_flag_el
                                else -> R.drawable.ic_flag_en
                            }
                        ),
                        contentDescription = langLabel,
                        modifier = Modifier.size(14.dp, 10.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = answerText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Image(
                    painter = rememberQrCodePainter(qrText),
                    contentDescription = "QR code with translation pair",
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp)
                )
            }
        }
    )
}
