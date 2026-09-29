package com.alexpo.grammermate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AudioState
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.InitPhase
import com.alexpo.grammermate.shared.AuditLogger

@Composable
internal fun ColumnScope.TtsModelsSection(
    audio: AudioState,
    onStartTtsDownload: () -> Unit
) {
    // TTS voice models (explicit download section)
    Text(
        text = "Голосовые модели (TTS)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    // Per-language status list. TtsModelRegistry.displayNames are plain
    // ("Italian", "Russian"); append the model dir name to match the task's
    // "Italian — paola-medium" expectation.
    val ttsTargetLanguages = listOf("it", "ru")
    ttsTargetLanguages.forEach { langId ->
        val spec = com.alexpo.grammermate.data.TtsModelRegistry.specFor(langId)
        val displayName = spec?.let { "${it.displayName} — ${it.modelDirName}" } ?: langId
        val present = audio.ttsModelsReady[langId] == true
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (present) "✓ Загружено" else "Не загружено",
                style = MaterialTheme.typography.bodySmall,
                color = if (present) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
    Button(
        onClick = {
            AuditLogger.getInstanceOrNull()?.settingsChange("ttsDownload", "manual")
            onStartTtsDownload()
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = ttsTargetLanguages.any { audio.ttsModelsReady[it] != true } &&
            audio.ttsDownloadState !is DownloadState.Downloading &&
            audio.ttsDownloadState !is DownloadState.Extracting &&
            audio.ttsDownloadState !is DownloadState.Initializing
    ) {
        Icon(Icons.Default.Download, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Загрузить модели")
    }
    // Progress block mirroring the ASR section's when(DownloadState).
    when (val ttsState = audio.ttsDownloadState) {
        is DownloadState.Downloading -> {
            LinearProgressIndicator(
                progress = { ttsState.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Загрузка модели… ${ttsState.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        is DownloadState.Extracting -> {
            LinearProgressIndicator(
                progress = { ttsState.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Распаковка модели… ${ttsState.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        is DownloadState.Initializing -> {
            LinearProgressIndicator(
                progress = { ttsState.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = when (ttsState.phase) {
                    InitPhase.CHECKING_FILES -> "Проверка файлов…"
                    InitPhase.LOADING_MODEL -> "Загрузка движка…"
                    InitPhase.PREPARING_ENGINE -> "Подготовка…"
                } + " ${ttsState.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        is DownloadState.Error -> {
            Text(
                text = ttsState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        is DownloadState.Done -> {
            Text(
                text = "Модели готовы к использованию.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        DownloadState.Idle -> {
            Text(
                text = "Скачайте модели для озвучки Italian и Russian заранее.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
internal fun ColumnScope.SpeechSettingsSection(
    audio: AudioState,
    onSetUseOfflineAsr: (Boolean) -> Unit,
    onStartAsrDownload: () -> Unit,
    onSetUseBluetoothMic: (Boolean) -> Unit,
    onSetVoiceAutoStart: (Boolean) -> Unit
) {
    // Offline ASR toggle
    Text(
        text = stringResource(R.string.settings_voice_recognition),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.settings_offline_speech_recognition), style = MaterialTheme.typography.bodyLarge)
        }
        Switch(
            checked = audio.useOfflineAsr,
            onCheckedChange = { enabled ->
                AuditLogger.getInstanceOrNull()?.settingsChange("offlineAsr", enabled.toString())
                onSetUseOfflineAsr(enabled)
                if (enabled && !audio.asrModelReady) {
                    onStartAsrDownload()
                }
            }
        )
    }
    when (val dlState = audio.asrDownloadState) {
        is DownloadState.Downloading -> {
            LinearProgressIndicator(
                progress = { dlState.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.settings_downloading_model, dlState.percent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        is DownloadState.Extracting -> {
            LinearProgressIndicator(
                progress = { dlState.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.settings_extracting_model, dlState.percent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        is DownloadState.Initializing -> {
            LinearProgressIndicator(
                progress = { dlState.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = when (dlState.phase) {
                    InitPhase.CHECKING_FILES -> "Checking model files..."
                    InitPhase.LOADING_MODEL -> "Loading engine..."
                    InitPhase.PREPARING_ENGINE -> "Preparing..."
                } + " ${dlState.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        is DownloadState.Error -> {
            Text(
                text = dlState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        else -> {
            Text(
                text = if (audio.useOfflineAsr) {
                    if (audio.asrModelReady) stringResource(R.string.settings_using_on_device) else stringResource(R.string.settings_model_not_downloaded)
                } else {
                    stringResource(R.string.settings_using_google_recognition)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    // Bluetooth microphone toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_bluetooth_microphone),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Switch(
            checked = audio.useBluetoothMic,
            onCheckedChange = { enabled ->
                AuditLogger.getInstanceOrNull()?.settingsChange("bluetoothMic", enabled.toString())
                onSetUseBluetoothMic(enabled)
            }
        )
    }
    Text(
        text = if (audio.useBluetoothMic) {
            stringResource(R.string.settings_bluetooth_mic_on)
        } else {
            stringResource(R.string.settings_bluetooth_mic_off)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )

    // Voice auto-start toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.settings_auto_start_voice), style = MaterialTheme.typography.bodyLarge)
        }
        Switch(
            checked = audio.voiceAutoStart,
            onCheckedChange = {
                AuditLogger.getInstanceOrNull()?.settingsChange("voiceAutoStart", it.toString())
                onSetVoiceAutoStart(it)
            }
        )
    }
    Text(
        text = if (audio.voiceAutoStart) {
            stringResource(R.string.settings_voice_auto_start_on)
        } else {
            stringResource(R.string.settings_voice_auto_start_off)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}
