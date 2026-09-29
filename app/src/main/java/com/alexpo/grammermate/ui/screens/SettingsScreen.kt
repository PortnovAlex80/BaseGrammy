package com.alexpo.grammermate.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.AuditLogger

@Composable
private fun ColumnScope.SoundPackSection(
    installedCount: Int,
    downloadState: DownloadState,
    onImport: (android.net.Uri) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    val soundPackImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImport(uri) }

    Text(
        text = "Звуковой пакет фоновой озвучки",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = "Установлено клипов: $installedCount",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    val soundPackBusy = downloadState is DownloadState.Downloading ||
        downloadState is DownloadState.Extracting ||
        downloadState is DownloadState.Initializing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                AuditLogger.getInstanceOrNull()?.settingsChange("soundPackImport", "manual")
                soundPackImportLauncher.launch(
                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                )
            },
            modifier = Modifier.weight(1f),
            enabled = !soundPackBusy
        ) {
            Icon(Icons.Default.Upload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Импортировать из файла")
        }
        Button(
            onClick = {
                AuditLogger.getInstanceOrNull()?.settingsChange("soundPackDownload", "manual")
                onDownload()
            },
            modifier = Modifier.weight(1f),
            enabled = !soundPackBusy
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Скачать с сервера")
        }
    }
    if (soundPackBusy) {
        OutlinedButton(
            onClick = {
                AuditLogger.getInstanceOrNull()?.settingsChange("soundPackCancel", "manual")
                onCancel()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Отмена")
        }
    }
    when (val spState = downloadState) {
        is DownloadState.Downloading -> {
            LinearProgressIndicator(progress = { spState.percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text(text = "Загрузка пакета… ${spState.percent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        is DownloadState.Extracting -> {
            LinearProgressIndicator(progress = { spState.percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text(text = "Распаковка ${spState.percent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        is DownloadState.Initializing -> {
            LinearProgressIndicator(progress = { spState.percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text(text = "Подготовка… ${spState.percent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        is DownloadState.Error -> {
            Text(text = spState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(text = "Проверьте подключение к сети и повторите попытку.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        is DownloadState.Done -> {
            Text(text = "Звуковой пакет готов к использованию.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        DownloadState.Idle -> {
            Text(text = "Импортируйте или скачайте звуковой пакет, чтобы фоновая озвучка использовала готовые записи.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

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
    onSetUseBluetoothMic: (Boolean) -> Unit = {},
    onStartAsrDownload: () -> Unit,
    onStartTtsDownload: () -> Unit = {},
    onImportSoundPack: (android.net.Uri) -> Unit = {},
    onDownloadSoundPack: () -> Unit = {},
    onCancelSoundPackDownload: () -> Unit = {},
    onRefreshSoundPackCount: () -> Unit = {},
    onResetAllProgress: () -> Unit,
    onSetHintLevel: (HintLevel) -> Unit,
    onSetThemeMode: (com.alexpo.grammermate.data.ThemeMode) -> Unit = {},
    onSetVoiceAutoStart: (Boolean) -> Unit = {},
    onSetUiLanguage: (String) -> Unit = {},
    onSetSessionSize: (Int) -> Unit = {},
    onSetClickableWordHints: (Boolean) -> Unit = {},
    onDismissParseWarning: () -> Unit = {},
    onConfirmPartialImport: () -> Unit = {},
    uiLanguage: String = "system",
    sessionSize: Int = 10,
    clickableWordHints: Boolean = true,
    languageDisplayName: String = ""
) {
    if (!show) return
    val sheetState = rememberModalBottomSheetState()
    var newLessonTitle by remember { mutableStateOf("") }
    var newLanguageName by remember { mutableStateOf("") }
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
    // Refresh the installed-clips count whenever the sheet is opened so the
    // "Установлено клипов: N" line reflects the real on-disk state.
    LaunchedEffect(Unit) { onRefreshSoundPackCount() }

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
            SettingsPreferencesSection(
                values = SettingsPreferencesValues(
                    uiLanguage = uiLanguage,
                    testMode = state.cardSession.testMode,
                    themeMode = state.navigation.themeMode,
                    hintLevel = state.cardSession.hintLevel,
                    clickableWordHints = clickableWordHints,
                    vocabSprintLimit = state.cardSession.vocabSprintLimit,
                    sessionSize = sessionSize,
                    ttsSpeed = state.audio.ttsSpeed
                ),
                onSetUiLanguage = onSetUiLanguage,
                onToggleTestMode = onToggleTestMode,
                onOpenLadder = onOpenLadder,
                onSetThemeMode = onSetThemeMode,
                onSetHintLevel = onSetHintLevel,
                onSetClickableWordHints = onSetClickableWordHints,
                onUpdateVocabLimit = onUpdateVocabLimit,
                onSetSessionSize = onSetSessionSize,
                onSetTtsSpeed = onSetTtsSpeed
            )

            // Background vocab — reset "easy" (green-marked) words
            Text(
                text = "Фоновое слушание",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            // User-tunable pause after each example sentence (it/ru) in background-vocab
            // playback. Read/written directly to AppConfigStore (mirrors the bgVocabMarkStore
            // access pattern below). Persisted on lift (onValueChangeFinished) to avoid
            // hammering disk on every drag tick; takes effect on the next spoken word.
            val configStore = (LocalContext.current.applicationContext
                as com.alexpo.grammermate.GrammarMateApplication).container.configStore
            var sentencePauseSec by remember {
                mutableStateOf(configStore.load().bgVocabSentencePauseMs / 1000f)
            }
            Text(
                text = "Пауза между примерами",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "0.5с", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = sentencePauseSec,
                    onValueChange = { sentencePauseSec = it },
                    onValueChangeFinished = {
                        val ms = (sentencePauseSec * 1000).toLong().coerceIn(500L, 6000L)
                        configStore.save(configStore.load().copy(bgVocabSentencePauseMs = ms))
                        AuditLogger.getInstanceOrNull()?.settingsChange("bgVocabSentencePauseMs", ms.toString())
                    },
                    valueRange = 0.5f..6.0f,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "6с", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = String.format("%.1f с", sentencePauseSec),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            val bgMarkStore = (LocalContext.current.applicationContext
                as com.alexpo.grammermate.GrammarMateApplication).container.bgVocabMarkStore
            var resetGreenCount by remember { mutableStateOf(bgMarkStore.greenCount()) }
            var showResetGreenDialog by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = {
                    resetGreenCount = bgMarkStore.greenCount()
                    showResetGreenDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = resetGreenCount > 0
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Сбросить лёгкие слова ($resetGreenCount)")
            }
            if (showResetGreenDialog) {
                AlertDialog(
                    onDismissRequest = { showResetGreenDialog = false },
                    title = { Text("Сбросить лёгкие слова?") },
                    text = { Text("Все слова, отмеченные зелёным ($resetGreenCount), снова будут включены в фоновую озвучку.") },
                    confirmButton = {
                        TextButton(onClick = {
                            AuditLogger.getInstanceOrNull()?.settingsChange("bgVocabResetGreen", "reset")
                            bgMarkStore.clearGreen()
                            resetGreenCount = 0
                            showResetGreenDialog = false
                        }) { Text("Сбросить") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetGreenDialog = false }) { Text("Отмена") }
                    }
                )
            }

            // Sound pack — pre-rendered background-vocab audio (import ZIP / download).
            SoundPackSection(
                installedCount = state.audio.soundPackInstalledCount,
                downloadState = state.audio.soundPackDownloadState,
                onImport = onImportSoundPack,
                onDownload = onDownloadSoundPack,
                onCancel = onCancelSoundPackDownload
            )

            TtsModelsSection(state.audio, onStartTtsDownload)
            SpeechSettingsSection(
                audio = state.audio,
                onSetUseOfflineAsr = onSetUseOfflineAsr,
                onStartAsrDownload = onStartAsrDownload,
                onSetUseBluetoothMic = onSetUseBluetoothMic,
                onSetVoiceAutoStart = onSetVoiceAutoStart
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
                    onValueChange = {
                        AuditLogger.getInstanceOrNull()?.settingsChange("ruTextScale", it.toString())
                        onSetRuTextScale(it)
                    },
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
                        AuditLogger.getInstanceOrNull()?.settingsChange("addLanguage", name)
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
                    AuditLogger.getInstanceOrNull()?.settingsChange("importPack", "")
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
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("importCsv", "")
                    importLauncher.launch(arrayOf("text/*", "text/csv"))
                },
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
                        AuditLogger.getInstanceOrNull()?.settingsChange("createEmptyLesson", "")
                        onCreateEmptyLesson(title)
                        newLessonTitle = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_create_empty_lesson))
            }
            OutlinedButton(
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("deleteAllLessons", "confirmed")
                    onDeleteAllLessons()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_delete_all_lessons))
            }
            OutlinedButton(
                onClick = { showResetConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
                                AuditLogger.getInstanceOrNull()?.settingsChange("resetProgress", "confirmed")
                                onResetAllProgress()
                            }
                        ) {
                            Text(text = stringResource(R.string.settings_reset), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirmDialog = false }) {
                            Text(text = stringResource(R.string.settings_cancel))
                        }
                    }
                )
            }

            if (state.showParseWarning) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = onDismissParseWarning,
                    title = { Text(text = "Import Warning") },
                    text = {
                        Column {
                            Text(text = "${state.parseErrors.size} card(s) failed to parse. Import anyway?")
                            Spacer(modifier = Modifier.height(8.dp))
                            state.parseErrors.take(3).forEach { error ->
                                Text(
                                    text = error.toUserMessage(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (state.parseErrors.size > 3) {
                                Text(
                                    text = "... and ${state.parseErrors.size - 3} more errors",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDismissParseWarning()
                                onConfirmPartialImport()
                            }
                        ) {
                            Text(text = "Import Partial")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissParseWarning) {
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
                        IconButton(onClick = {
                            AuditLogger.getInstanceOrNull()?.settingsChange("deletePack", pack.packId.value)
                            onDeletePack(pack.packId.value)
                        }) {
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
                        AuditLogger.getInstanceOrNull()?.settingsChange("userName", trimmed)
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
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("saveProgress", "")
                    onSaveProgress()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_save_progress_now))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("restoreBackup", "")
                    restoreBackupLauncher.launch(null)
                },
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
            onSelect = { langId ->
                AuditLogger.getInstanceOrNull()?.settingsChange("language", langId)
                onSelectLanguage(langId)
            }
        )
        DropdownSelector(
            title = stringResource(R.string.settings_pack),
            selected = selectedPackLabel,
            items = languagePacks.map { pack ->
                (pack.displayName ?: "${pack.packId.value} (${pack.packVersion})") to pack.packId.value
            },
            onSelect = { packId ->
                AuditLogger.getInstanceOrNull()?.settingsChange("pack", packId)
                onSelectPack(packId)
            }
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
