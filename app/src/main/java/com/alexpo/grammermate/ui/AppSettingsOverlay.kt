package com.alexpo.grammermate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.ScreenLogger
import com.alexpo.grammermate.ui.dialogs.DialogState
import com.alexpo.grammermate.ui.navigation.Routes
import com.alexpo.grammermate.ui.screens.SettingsSheet

/** Owns settings presentation and its Bluetooth permission request lifecycle. */
@Composable
internal fun AppSettingsOverlay(
    state: TrainingUiState,
    vm: TrainingViewModel,
    verbDrillVm: VerbDrillViewModel,
    navController: NavHostController,
    currentRoute: String,
    dialogState: MutableState<DialogState>
) {
    var dialogs by dialogState
    val context = LocalContext.current
    // Bluetooth mic permission launcher — handles BLUETOOTH_CONNECT on API 31+
    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.audio.setUseBluetoothMic(true)
        } else {
            vm.audio.setUseBluetoothMic(false)
        }
    }

    SettingsSheet(
        show = dialogs == DialogState.Settings,
        state = state,
        onDismiss = remember(dialogs, currentRoute, state.cardSession.currentCard) {
            {
                ScreenLogger.overlay("settings", shown = false)
                dialogs = DialogState.None
                if (currentRoute == Routes.TRAINING && state.cardSession.currentCard != null) {
                    vm.resumeFromSettings()
                }
            }
        },
        onOpenLadder = remember(navController) {
            {
                ScreenLogger.overlay("settings", shown = false)
                dialogs = DialogState.None
                // Plain push: LADDER back pops to the screen that opened it.
                navController.navigate(Routes.LADDER) {
                    launchSingleTop = true
                }
            }
        },
        onSelectLanguage = vm::selectLanguage,
        onSelectPack = vm::selectPack,
        onAddLanguage = vm::addLanguage,
        onImportLessonPack = vm::importLessonPack,
        onImportLesson = vm::importLesson,
        onResetReload = vm::resetAndImportLesson,
        onCreateEmptyLesson = vm::createEmptyLesson,
        onDeleteAllLessons = vm::deleteAllLessons,
        onDeletePack = vm::deletePack,
        onToggleTestMode = vm::toggleTestMode,
        onUpdateVocabLimit = vm.settings::updateVocabSprintLimit,
        onUpdateUserName = vm.settings::updateUserName,
        onSaveProgress = vm::saveProgressNow,
        onRestoreBackup = vm::restoreBackup,
        onSetTtsSpeed = vm.audio::setTtsSpeed,
        onSetRuTextScale = vm::setRuTextScale,
        onSetUseOfflineAsr = vm.audio::setUseOfflineAsr,
        onSetUseBluetoothMic = { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasPermission = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    vm.audio.setUseBluetoothMic(true)
                } else {
                    btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            } else {
                vm.audio.setUseBluetoothMic(enabled)
            }
        },
        onStartAsrDownload = remember { { vm.audio.startAsrDownload() } },
        onStartTtsDownload = remember { { vm.startTtsDownload() } },
        onImportSoundPack = remember { { uri: android.net.Uri -> vm.importSoundPack(uri) } },
        onDownloadSoundPack = remember { { vm.downloadSoundPack() } },
        onCancelSoundPackDownload = remember { { vm.cancelSoundPackDownload() } },
        onRefreshSoundPackCount = remember { { vm.refreshSoundPackInstalledCount() } },
        onResetAllProgress = vm::resetLanguageProgress,
        onSetHintLevel = vm.settings::setHintLevel,
        onSetThemeMode = vm.settings::setThemeMode,
        onSetVoiceAutoStart = vm.audio::setVoiceAutoStart,
        onSetUiLanguage = vm.settings::setUiLanguage,
        onSetSessionSize = { size ->
            vm.setSessionSize(size)
            verbDrillVm.setSessionSize(size)
        },
        onSetClickableWordHints = vm.settings::setClickableWordHints,
        onDismissParseWarning = vm::dismissParseWarning,
        onConfirmPartialImport = vm::confirmPartialImport,
        sessionSize = vm.currentSessionSize,
        clickableWordHints = state.navigation.clickableWordHints,
        uiLanguage = state.navigation.uiLanguage,
        languageDisplayName = state.navigation.languages.firstOrNull { it.id == state.navigation.selectedLanguageId }?.displayName ?: state.navigation.selectedLanguageId?.value ?: ""
    )
}
