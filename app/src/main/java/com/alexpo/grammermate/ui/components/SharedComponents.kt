package com.alexpo.grammermate.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.data.AsrState
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.TtsModelRegistry
import com.alexpo.grammermate.data.TtsState

/**
 * Navigation icon button with surfaceVariant background and primary accent bottom bar.
 * Used by GrammarMateApp and TrainingCardSession.
 */
@Composable
fun NavIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) surface else surface.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(if (enabled) accent else accent.copy(alpha = 0.3f))
        )
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}

/**
 * TTS speaker button with 4 states: speaking (stop icon, red), initializing (spinner),
 * error (warning, red), idle (VolumeUp icon).
 * Used by GrammarMateApp and TrainingCardSession.
 */
@Composable
fun TtsSpeakerButton(
    ttsState: TtsState,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled
    ) {
        when (ttsState) {
            TtsState.SPEAKING -> Icon(
                Icons.Default.StopCircle,
                contentDescription = "Stop",
                tint = MaterialTheme.colorScheme.error
            )
            TtsState.INITIALIZING -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            TtsState.ERROR -> Icon(
                Icons.Default.ReportProblem,
                contentDescription = "TTS error",
                tint = MaterialTheme.colorScheme.error
            )
            else -> Icon(
                Icons.Default.VolumeUp,
                contentDescription = "Listen",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Dialog for downloading TTS pronunciation models.
 * Shows download/extract progress and handles all download states.
 */
@Composable
fun TtsDownloadDialog(
    downloadState: DownloadState,
    languageId: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val spec = TtsModelRegistry.specFor(languageId)
    val langName = spec?.displayName ?: languageId
    val sizeText = spec?.let { "${it.fallbackDownloadSize / (1024 * 1024)} MB" } ?: "model"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download pronunciation model?") },
        text = {
            when (downloadState) {
                is DownloadState.Idle -> {
                    Text("This will download ~$sizeText ($langName pronunciation). Uses internal storage.")
                }
                is DownloadState.Downloading -> {
                    Column {
                        Text("Downloading... ${downloadState.percent}%")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = downloadState.percent / 100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is DownloadState.Extracting -> {
                    Column {
                        Text("Extracting... ${downloadState.percent}%")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = downloadState.percent / 100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is DownloadState.Done -> {
                    Text("Pronunciation model ready!")
                }
                is DownloadState.Error -> {
                    Text("Download failed: ${downloadState.message}")
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is DownloadState.Idle -> TextButton(onClick = onConfirm) { Text("Download") }
                is DownloadState.Done, is DownloadState.Error -> TextButton(onClick = onDismiss) { Text("OK") }
                is DownloadState.Downloading, is DownloadState.Extracting -> {
                    TextButton(onClick = onDismiss) { Text("Continue in background") }
                }
            }
        },
        dismissButton = {
            if (downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Extracting) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/**
 * Warning dialog shown when downloading TTS model on a metered (cellular) network.
 */
@Composable
fun MeteredNetworkDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metered network detected") },
        text = { Text("You appear to be on a cellular or metered connection. The pronunciation model is ~346 MB. Continue downloading?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Download anyway") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Warning dialog shown when downloading ASR model on a metered (cellular) network.
 */
@Composable
fun AsrMeteredNetworkDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metered network detected") },
        text = { Text("You appear to be on a cellular or metered connection. The speech recognition model is ~375 MB. Continue downloading?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Download anyway") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Visual indicator for offline ASR state near the microphone button.
 * Shows a pulsing red dot when recording, a spinner when recognizing,
 * and an error message on failure. Auto-dismisses when state returns to IDLE.
 */
@Composable
fun AsrStatusIndicator(asrState: AsrState) {
    when (asrState) {
        AsrState.RECORDING -> {
            val infiniteTransition = rememberInfiniteTransition(label = "asrPulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "asrPulseAlpha"
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.Red.copy(alpha = pulseAlpha), CircleShape)
                )
                Text(
                    text = "Listening...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        AsrState.RECOGNIZING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Processing...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        AsrState.ERROR -> {
            Text(
                text = "Recognition error",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        else -> { /* IDLE, INITIALIZING, READY -- no indicator */ }
    }
}
