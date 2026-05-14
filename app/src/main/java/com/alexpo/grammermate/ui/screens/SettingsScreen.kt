package com.alexpo.grammermate.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.TrainingUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    show: Boolean,
    state: TrainingUiState,
    onDismiss: () -> Unit,
    onOpenLadder: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectPack: (String) -> Unit,
    onAddLanguage: (String) -> Unit,
    onImportLessonPack: (android.net.Uri) -> Unit,
    onImportLesson: (android.net.Uri) -> Unit,
    onResetReload: (android.net.Uri) -> Unit,
    onCreateEmptyLesson: (String) -> Unit,
    onDeleteAllLessons: () -> Unit,
    onDeletePack: (String) -> Unit,
    onToggleTestMode: () -> Unit,
    onUpdateVocabLimit: (Int) -> Unit,
    onUpdateUserName: (String) -> Unit,
    onSaveProgress: () -> Unit,
    onRestoreBackup: (android.net.Uri) -> Unit,
    onSetTtsSpeed: (Float) -> Unit,
    onSetRuTextScale: (Float) -> Unit,
    onSetUseOfflineAsr: (Boolean) -> Unit,
    onStartAsrDownload: () -> Unit,
    onResetAllProgress: () -> Unit,
    onSetHintLevel: (HintLevel) -> Unit,
    onSetVoiceAutoStart: (Boolean) -> Unit = {},
    languageDisplayName: String = ""
) {
    if (!show) return
    val sheetState = rememberModalBottomSheetState()
    var newLessonTitle by remember { mutableStateOf("") }
    var newLanguageName by remember { mutableStateOf("") }
    var vocabLimitText by remember(state.cardSession.vocabSprintLimit) { mutableStateOf(state.cardSession.vocabSprintLimit.toString()) }
    var userNameInput by remember(state.navigation.userName) { mutableStateOf(state.navigation.userName) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLesson(uri)
    }
    val packImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLessonPack(uri)
    }
    val resetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onResetReload(uri)
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onRestoreBackup(uri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_service_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.settings_test_mode), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.cardSession.testMode,
                    onCheckedChange = { onToggleTestMode() }
                )
            }
            Text(
                text = stringResource(R.string.settings_test_mode_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            OutlinedButton(
                onClick = onOpenLadder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Insights, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_show_ladder))
            }

            // Difficulty / Hint level selector
            Text(
                text = stringResource(R.string.settings_difficulty),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HintLevel.entries.forEach { level ->
                    val isSelected = state.cardSession.hintLevel == level
                    val label = when (level) {
                        HintLevel.EASY -> stringResource(R.string.settings_easy)
                        HintLevel.MEDIUM -> stringResource(R.string.settings_medium)
                        HintLevel.HARD -> stringResource(R.string.settings_hard)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSetHintLevel(level) },
                        label = { Text(text = label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                text = when (state.cardSession.hintLevel) {
                    HintLevel.EASY ->
                        stringResource(R.string.settings_easy_description)
                    HintLevel.MEDIUM ->
                        stringResource(R.string.settings_medium_description)
                    HintLevel.HARD ->
                        stringResource(R.string.settings_hard_description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            OutlinedTextField(
                value = vocabLimitText,
                onValueChange = { next ->
                    val cleaned = next.filter { it.isDigit() }
                    vocabLimitText = cleaned
                    val parsed = cleaned.toIntOrNull()
                    if (parsed != null) {
                        onUpdateVocabLimit(parsed)
                    } else if (cleaned.isEmpty()) {
                        onUpdateVocabLimit(0)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.settings_vocab_sprint_limit)) }
            )
            Text(
                text = stringResource(R.string.settings_vocab_limit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // TTS speed control
            Text(
                text = stringResource(R.string.settings_pronunciation_speed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "0.5x", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.audio.ttsSpeed,
                    onValueChange = onSetTtsSpeed,
                    valueRange = 0.5f..1.5f,
                    steps = 3,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "1.5x", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = String.format("%.2fx", state.audio.ttsSpeed),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

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
                    checked = state.audio.useOfflineAsr,
                    onCheckedChange = { enabled ->
                        onSetUseOfflineAsr(enabled)
                        if (enabled && !state.audio.asrModelReady) {
                            onStartAsrDownload()
                        }
                    }
                )
            }
            when (val dlState = state.audio.asrDownloadState) {
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
                is DownloadState.Error -> {
                    Text(
                        text = dlState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    Text(
                        text = if (state.audio.useOfflineAsr) {
                            if (state.audio.asrModelReady) stringResource(R.string.settings_using_on_device) else stringResource(R.string.settings_model_not_downloaded)
                        } else {
                            stringResource(R.string.settings_using_google_recognition)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

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
                    checked = state.audio.voiceAutoStart,
                    onCheckedChange = onSetVoiceAutoStart
                )
            }
            Text(
                text = if (state.audio.voiceAutoStart) {
                    stringResource(R.string.settings_voice_auto_start_on)
                } else {
                    stringResource(R.string.settings_voice_auto_start_off)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // Russian text size control
            Text(
                text = stringResource(R.string.settings_translation_text_size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "1x", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.audio.ruTextScale,
                    onValueChange = onSetRuTextScale,
                    valueRange = 1.0f..2.0f,
                    steps = 3,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "2x", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = String.format("%.1fx", state.audio.ruTextScale),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            LanguageLessonColumn(state, onSelectLanguage, onSelectPack)
            OutlinedTextField(
                value = newLanguageName,
                onValueChange = { newLanguageName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.settings_new_language)) }
            )
            OutlinedButton(
                onClick = {
                    val name = newLanguageName.trim()
                    if (name.isNotEmpty()) {
                        onAddLanguage(name)
                        newLanguageName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_add_language))
            }
            OutlinedButton(
                onClick = {
                    packImportLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_import_lesson_pack))
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("text/*", "text/csv")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_import_lesson_csv))
            }
            OutlinedButton(
                onClick = { resetLauncher.launch(arrayOf("text/*", "text/csv")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_reset_reload))
            }
            OutlinedTextField(
                value = newLessonTitle,
                onValueChange = { newLessonTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.settings_empty_lesson_title)) }
            )
            OutlinedButton(
                onClick = {
                    val title = newLessonTitle.trim()
                    if (title.isNotEmpty()) {
                        onCreateEmptyLesson(title)
                        newLessonTitle = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_create_empty_lesson))
            }
            OutlinedButton(
                onClick = { onDeleteAllLessons() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB00020))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_delete_all_lessons))
            }
            OutlinedButton(
                onClick = { showResetConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB00020))
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_reset_progress, languageDisplayName))
            }
            if (showResetConfirmDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResetConfirmDialog = false },
                    title = { Text(text = stringResource(R.string.settings_reset_progress_title)) },
                    text = {
                        Text(text = stringResource(R.string.settings_reset_progress_confirm, languageDisplayName))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showResetConfirmDialog = false
                                onResetAllProgress()
                            }
                        ) {
                            Text(text = stringResource(R.string.settings_reset), color = Color(0xFFB00020))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirmDialog = false }) {
                            Text(text = stringResource(R.string.settings_cancel))
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.settings_csv_format), style = MaterialTheme.typography.labelLarge)
            Text(
                text = stringResource(R.string.settings_csv_format_description),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.settings_instructions), style = MaterialTheme.typography.labelLarge)
            Text(
                text = stringResource(R.string.settings_instructions_description),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.settings_packs), style = MaterialTheme.typography.labelLarge)
            if (state.navigation.installedPacks.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_installed_packs),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                state.navigation.installedPacks.forEach { pack ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pack.displayName ?: "${pack.packId} (${pack.packVersion})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onDeletePack(pack.packId.value) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_delete_pack))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_profile),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = userNameInput,
                onValueChange = { userNameInput = it.take(50) },
                label = { Text(stringResource(R.string.settings_your_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val trimmed = userNameInput.trim()
                    if (trimmed.isNotEmpty() && trimmed != state.navigation.userName) {
                        onUpdateUserName(trimmed)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = userNameInput.trim().isNotEmpty() && userNameInput.trim() != state.navigation.userName
            ) {
                Text(stringResource(R.string.settings_save_name))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_backup_restore),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = stringResource(R.string.settings_restore_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            OutlinedButton(
                onClick = onSaveProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_save_progress_now))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { restoreBackupLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_restore_from_backup))
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.settings_grammarmate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "v${state.navigation.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_description),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun LanguageLessonColumn(
    state: TrainingUiState,
    onSelectLanguage: (String) -> Unit,
    onSelectPack: (String) -> Unit
) {
    val languagePacks = state.navigation.installedPacks.filter { it.languageId == state.navigation.selectedLanguageId }
    val selectedPackLabel = state.navigation.activePackId?.let { activeId ->
        languagePacks.firstOrNull { it.packId == activeId }?.let { pack ->
            pack.displayName ?: "${pack.packId} (${pack.packVersion})"
        }
    } ?: "-"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownSelector(
            title = stringResource(R.string.settings_language),
            selected = state.navigation.languages.firstOrNull { it.id == state.navigation.selectedLanguageId }?.displayName
                ?: "-",
            items = state.navigation.languages.map { it.displayName to it.id.value },
            onSelect = onSelectLanguage
        )
        DropdownSelector(
            title = stringResource(R.string.settings_pack),
            selected = selectedPackLabel,
            items = languagePacks.map { pack ->
                (pack.displayName ?: "${pack.packId.value} (${pack.packVersion})") to pack.packId.value
            },
            onSelect = onSelectPack
        )
    }
}

@Composable
fun DropdownSelector(
    title: String,
    selected: String,
    items: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(text = selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, id) ->
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    }
                )
            }
        }
    }
}
