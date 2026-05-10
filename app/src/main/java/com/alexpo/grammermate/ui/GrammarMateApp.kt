package com.alexpo.grammermate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.SubLessonType
import com.alexpo.grammermate.data.FlowerVisual
import com.alexpo.grammermate.data.FlowerState
import com.alexpo.grammermate.data.FlowerCalculator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.alexpo.grammermate.data.AsrState
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.TtsModelRegistry
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent

@Composable
fun GrammarMateApp() {
    Surface(modifier = Modifier.fillMaxSize()) {
        val vm: TrainingViewModel = viewModel()
        val state by vm.uiState.collectAsState()
        var screen by remember { mutableStateOf(parseScreen(state.initialScreen)) }
        var previousScreen by remember { mutableStateOf(AppScreen.HOME) }
        var showSettings by remember { mutableStateOf(false) }
        var showExitDialog by remember { mutableStateOf(false) }
        var showWelcomeDialog by remember { mutableStateOf(false) }
        val lastFinishedToken = remember { mutableStateOf(state.subLessonFinishedToken) }
        val lastVocabFinishedToken = remember { mutableStateOf(state.vocabFinishedToken) }
        val lastBossFinishedToken = remember { mutableStateOf(state.bossFinishedToken) }
        val lastEliteFinishedToken = remember { mutableStateOf(state.eliteFinishedToken) }
        var showTtsDownloadDialog by remember { mutableStateOf(false) }
    
        LaunchedEffect(screen) {
            vm.onScreenChanged(screen.name)
        }

        val audioPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* permission result handled by system */ }
        var showVocabStartDialog by remember { mutableStateOf(false) }

        val context = androidx.compose.ui.platform.LocalContext.current
        val lessonStore = remember { com.alexpo.grammermate.data.LessonStore(context) }
        val hasVerbDrill = remember(state.activePackId, state.selectedLanguageId) {
            state.activePackId?.let { lessonStore.hasVerbDrill(it, state.selectedLanguageId) } ?: false
        }
        val hasVocabDrill = remember(state.activePackId, state.selectedLanguageId) {
            state.activePackId?.let { lessonStore.hasVocabDrill(it, state.selectedLanguageId) } ?: false
        }

        val onTtsSpeak: () -> Unit = {
            if (state.ttsState == TtsState.SPEAKING) {
                vm.stopTts()
            } else if (!state.ttsModelReady) {
                val bgState = state.bgTtsDownloadStates[state.selectedLanguageId]
                if (bgState != null && bgState !is DownloadState.Idle) {
                    vm.setTtsDownloadStateFromBackground(bgState)
                }
                showTtsDownloadDialog = true
            } else {
                val text = state.answerText
                    ?: state.currentCard?.acceptedAnswers?.firstOrNull()
                if (text != null) {
                    vm.onTtsSpeak(text, speed = 0.67f)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Persistent TTS download progress bar — always visible on all screens
            AnimatedVisibility(visible = state.bgTtsDownloading) {
                LinearProgressIndicator(
                    progress = { calcBgDownloadProgress(state.bgTtsDownloadStates) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                BackHandler(enabled = screen == AppScreen.TRAINING && !showSettings) {
                    showExitDialog = true
                }
                BackHandler(enabled = screen == AppScreen.LESSON && !showSettings) {
                    screen = AppScreen.HOME
                }
                BackHandler(enabled = screen == AppScreen.ELITE && !showSettings) {
                    screen = AppScreen.HOME
                }
                BackHandler(enabled = screen == AppScreen.STORY && !showSettings) {
                    screen = AppScreen.LESSON
                }
                BackHandler(enabled = screen == AppScreen.VOCAB && !showSettings) {
                    screen = AppScreen.LESSON
                }
                BackHandler(enabled = screen == AppScreen.LADDER && !showSettings) {
                    screen = previousScreen
                    if (previousScreen == AppScreen.TRAINING && state.currentCard != null) {
                        vm.resumeFromSettings()
                    }
                }
                BackHandler(enabled = screen == AppScreen.VERB_DRILL && !showSettings) {
                    screen = AppScreen.HOME
                }
                BackHandler(enabled = screen == AppScreen.VOCAB_DRILL && !showSettings) {
                    screen = AppScreen.HOME
                }

                SettingsSheet(
                    show = showSettings,
                    state = state,
                    onDismiss = {
                        showSettings = false
                        if (screen == AppScreen.TRAINING && state.currentCard != null) {
                            vm.resumeFromSettings()
                        }
                    },
                    onOpenLadder = {
                        showSettings = false
                        screen = AppScreen.LADDER
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
                    onUpdateVocabLimit = vm::updateVocabSprintLimit,
                    onUpdateUserName = vm::updateUserName,
                    onSaveProgress = vm::saveProgressNow,
                    onRestoreBackup = vm::restoreBackup,
                    onSetTtsSpeed = vm::setTtsSpeed,
                    onSetRuTextScale = vm::setRuTextScale,
                    onSetUseOfflineAsr = vm::setUseOfflineAsr,
                    onStartAsrDownload = { vm.startAsrDownload() }
                )

            if (showWelcomeDialog) {
                WelcomeDialog(
                    onNameSet = { name ->
                        vm.updateUserName(name)
                        showWelcomeDialog = false
                    }
                )
            }
            if (showTtsDownloadDialog) {
                // Auto-close and play when download completes
                if (state.ttsDownloadState is DownloadState.Done) {
                    showTtsDownloadDialog = false
                    vm.dismissTtsDownloadDialog()
                    // Auto-play TTS after download
                    val text = state.answerText ?: state.currentCard?.acceptedAnswers?.firstOrNull()
                    if (text != null) vm.onTtsSpeak(text, speed = 0.67f)
                }
                if (showTtsDownloadDialog) {
                    TtsDownloadDialog(
                        downloadState = state.ttsDownloadState,
                        languageId = state.selectedLanguageId,
                        onConfirm = { vm.startTtsDownload() },
                        onDismiss = { vm.dismissTtsDownloadDialog(); showTtsDownloadDialog = false }
                    )
                }
            }
            // M6: Metered network warning
            if (state.ttsMeteredNetwork) {
                MeteredNetworkDialog(
                    onConfirm = { vm.confirmTtsDownloadOnMetered() },
                    onDismiss = { vm.dismissMeteredWarning(); vm.dismissTtsDownloadDialog(); showTtsDownloadDialog = false }
                )
            }
            // ASR metered network warning
            if (state.asrMeteredNetwork) {
                AsrMeteredNetworkDialog(
                    onConfirm = { vm.confirmAsrDownloadOnMetered() },
                    onDismiss = { vm.dismissAsrMeteredWarning(); vm.dismissAsrDownloadDialog() }
                )
            }
            androidx.compose.runtime.LaunchedEffect(screen, state.userName) {
                if (screen != AppScreen.HOME && state.userName == "GrammarMateUser") {
                    showWelcomeDialog = true
                }
            }

            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    state = state,
                    onSelectLanguage = vm::selectLanguage,
                    onOpenSettings = {
                        previousScreen = screen
                        vm.pauseSession()
                        showSettings = true
                    },
                    onPrimaryAction = { screen = AppScreen.LESSON },
                    onSelectLesson = { lessonId ->
                        vm.selectLesson(lessonId)
                        screen = AppScreen.LESSON
                    },
                    onOpenElite = { if (state.eliteUnlocked) screen = AppScreen.ELITE },
                    hasVerbDrill = hasVerbDrill,
                    hasVocabDrill = hasVocabDrill,
                    onOpenVerbDrill = { screen = AppScreen.VERB_DRILL },
                    onOpenVocabDrill = { screen = AppScreen.VOCAB_DRILL }
                )
                AppScreen.LESSON -> LessonRoadmapScreen(
                    state = state,
                    onBack = { screen = AppScreen.HOME },
                    onStartSubLesson = { index ->
                        vm.selectSubLesson(index)
                        screen = AppScreen.TRAINING
                    },
                    onOpenVocab = {
                        if (vm.hasVocabProgress()) {
                            showVocabStartDialog = true
                        } else {
                            vm.openVocabSprint(resume = false)
                            screen = AppScreen.VOCAB
                        }
                    },
                    onStartBossLesson = {
                        vm.startBossLesson()
                        screen = AppScreen.TRAINING
                    },
                    onStartBossMega = {
                        vm.startBossMega()
                        screen = AppScreen.TRAINING
                    },
                    onDrillStart = {
                        state.selectedLessonId?.let { vm.showDrillStartDialog(it) }
                    }
                )
                AppScreen.ELITE -> EliteRoadmapScreen(
                    state = state,
                    onBack = { screen = AppScreen.HOME },
                    onStartStep = { index ->
                        vm.openEliteStep(index)
                        screen = AppScreen.TRAINING
                    },
                    onStartBoss = {
                        vm.startBossElite()
                        screen = AppScreen.TRAINING
                    }
                )
                AppScreen.STORY -> StoryQuizScreen(
                    story = state.activeStory,
                    testMode = state.testMode,
                    onClose = {
                        state.activeStory?.phase?.let { phase ->
                            vm.completeStory(phase, false)
                        }
                        screen = AppScreen.LESSON
                    },
                    onComplete = { allCorrect ->
                        state.activeStory?.phase?.let { phase ->
                            vm.completeStory(phase, allCorrect)
                        }
                        screen = AppScreen.LESSON
                    }
                )
                AppScreen.VOCAB -> VocabSprintScreen(
                    state = state,
                    onInputChange = vm::onVocabInputChanged,
                    onSubmit = { input -> vm.submitVocabAnswer(input) },
                    onSetInputMode = vm::setVocabInputMode,
                    onRequestVoice = vm::requestVocabVoice,
                    onShowAnswer = vm::showVocabAnswer,
                    onClose = { screen = AppScreen.LESSON },
                    onSpeak = { text ->
                        if (state.ttsState == TtsState.SPEAKING) {
                            vm.stopTts()
                        } else if (!state.ttsModelReady) {
                            val bgState = state.bgTtsDownloadStates[state.selectedLanguageId]
                            if (bgState != null && bgState !is DownloadState.Idle) {
                                vm.setTtsDownloadStateFromBackground(bgState)
                            }
                            showTtsDownloadDialog = true
                        } else {
                            vm.onTtsSpeak(text, speed = 0.67f)
                        }
                    }
                )
                AppScreen.LADDER -> LadderScreen(
                    state = state,
                    onBack = {
                        screen = previousScreen
                        if (previousScreen == AppScreen.TRAINING && state.currentCard != null) {
                            vm.resumeFromSettings()
                        }
                    }
                )
                AppScreen.TRAINING -> TrainingScreen(
                    state = state,
                    onInputChange = vm::onInputChanged,
                    onSubmit = vm::submitAnswer,
                    onPrev = vm::prevCard,
                    onNext = vm::nextCard,
                    onTogglePause = vm::togglePause,
                    onRequestExit = { showExitDialog = true },
                    onOpenSettings = {
                        previousScreen = screen
                        vm.pauseSession()
                    },
                    onShowSettings = { showSettings = true },
                    onSelectLesson = vm::selectLesson,
                    onSelectMode = vm::selectMode,
                    onSetInputMode = vm::setInputMode,
                    onShowAnswer = vm::showAnswer,
                    onVoicePromptStarted = vm::onVoicePromptStarted,
                    onSelectWordFromBank = vm::selectWordFromBank,
                    onRemoveLastWord = vm::removeLastSelectedWord,
                    onTtsSpeak = onTtsSpeak,
                    onFlagBadSentence = vm::flagBadSentence,
                    onUnflagBadSentence = vm::unflagBadSentence,
                    onHideCard = vm::hideCurrentCard,
                    onExportBadSentences = vm::exportBadSentences,
                    isBadSentence = vm::isBadSentence,
                    onStartOfflineRecognition = vm::startOfflineRecognition
                )
                AppScreen.VERB_DRILL -> {
                    val verbDrillVm = viewModel<VerbDrillViewModel>()
                    val activePackId = state.activePackId
                    if (activePackId != null) {
                        verbDrillVm.reloadForPack(activePackId)
                    } else {
                        verbDrillVm.reloadForLanguage(state.selectedLanguageId)
                    }
                    VerbDrillScreen(
                        viewModel = verbDrillVm,
                        onBack = { screen = AppScreen.HOME }
                    )
                }
                AppScreen.VOCAB_DRILL -> {
                    val vocabDrillVm = viewModel<VocabDrillViewModel>()
                    val packId = state.activePackId
                    if (packId != null) {
                        vocabDrillVm.reloadForPack(packId, state.selectedLanguageId)
                    } else {
                        vocabDrillVm.reloadForLanguage(state.selectedLanguageId)
                    }
                    VocabDrillScreen(
                        viewModel = vocabDrillVm,
                        onBack = { screen = AppScreen.HOME }
                    )
                }
            }

            if (screen == AppScreen.TRAINING && state.subLessonFinishedToken != lastFinishedToken.value) {
                lastFinishedToken.value = state.subLessonFinishedToken
                screen = AppScreen.LESSON
            }
            if (screen == AppScreen.VOCAB && state.vocabFinishedToken != lastVocabFinishedToken.value) {
                lastVocabFinishedToken.value = state.vocabFinishedToken
                screen = AppScreen.LESSON
            }
            if (screen == AppScreen.TRAINING && state.bossFinishedToken != lastBossFinishedToken.value) {
                lastBossFinishedToken.value = state.bossFinishedToken
                screen = if (state.bossLastType == com.alexpo.grammermate.data.BossType.ELITE) {
                    AppScreen.ELITE
                } else {
                    AppScreen.LESSON
                }
            }
            if (screen == AppScreen.TRAINING && state.eliteFinishedToken != lastEliteFinishedToken.value) {
                lastEliteFinishedToken.value = state.eliteFinishedToken
                screen = AppScreen.ELITE
            }

            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showExitDialog = false
                            if (state.bossActive) {
                                vm.finishBoss()
                                screen = if (state.bossType == com.alexpo.grammermate.data.BossType.ELITE) {
                                    AppScreen.ELITE
                                } else {
                                    AppScreen.LESSON
                                }
                                return@TextButton
                            }
                            if (state.eliteActive) {
                                vm.cancelEliteSession()
                                screen = AppScreen.ELITE
                                return@TextButton
                            }
                            if (state.isDrillMode) {
                                vm.exitDrillMode()
                                screen = AppScreen.LESSON
                                return@TextButton
                            }
                            vm.finishSession()
                            screen = AppScreen.LESSON
                        }) {
                            Text(text = "Exit")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitDialog = false }) {
                            Text(text = "Cancel")
                        }
                    },
                    title = { Text(text = "End session?") },
                    text = { Text(text = "Current session will be completed.") }
                )
            }

            if (state.storyErrorMessage != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearStoryError() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearStoryError() }) {
                            Text(text = "OK")
                        }
                    },
                    title = { Text(text = "Story") },
                    text = { Text(text = state.storyErrorMessage ?: "") }
                )
            }
            if (state.vocabErrorMessage != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearVocabError() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearVocabError() }) {
                            Text(text = "OK")
                        }
                    },
                    title = { Text(text = "Vocabulary") },
                    text = { Text(text = state.vocabErrorMessage ?: "") }
                )
            }
            if (showVocabStartDialog) {
                AlertDialog(
                    onDismissRequest = { showVocabStartDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showVocabStartDialog = false
                            vm.openVocabSprint(resume = true)
                            screen = AppScreen.VOCAB
                        }) {
                            Text(text = "Continue")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showVocabStartDialog = false
                            vm.openVocabSprint(resume = false)
                            screen = AppScreen.VOCAB
                        }) {
                            Text(text = "Start fresh")
                        }
                    },
                    title = { Text(text = "Vocabulary Sprint") },
                    text = { Text(text = "You have previous progress. Continue where you left off or start fresh?") }
                )
            }
            if (state.bossErrorMessage != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearBossError() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearBossError() }) {
                            Text(text = "OK")
                        }
                    },
                    title = { Text(text = "Boss") },
                    text = { Text(text = state.bossErrorMessage ?: "") }
                )
            }
            if (state.bossRewardMessage != null && state.bossReward != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearBossRewardMessage() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearBossRewardMessage() }) {
                            Text(text = "OK")
                        }
                    },
                    icon = {
                        val tint = when (state.bossReward) {
                            com.alexpo.grammermate.data.BossReward.BRONZE -> Color(0xFFCD7F32)
                            com.alexpo.grammermate.data.BossReward.SILVER -> Color(0xFFC0C0C0)
                            com.alexpo.grammermate.data.BossReward.GOLD -> Color(0xFFFFD700)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = tint
                        )
                    },
                    title = { Text(text = "Boss Reward") },
                    text = { Text(text = state.bossRewardMessage ?: "") }
                )
            }
            if (state.streakMessage != null) {
                AlertDialog(
                    onDismissRequest = { vm.dismissStreakMessage() },
                    confirmButton = {
                        TextButton(onClick = { vm.dismissStreakMessage() }) {
                            Text(text = "Continue")
                        }
                    },
                    icon = {
                        Text(text = "??", fontSize = 48.sp)
                    },
                    title = { Text(text = "Streak!") },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = state.streakMessage ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            if (state.longestStreak > state.currentStreak) {
                                Text(
                                    text = "Longest streak: ${state.longestStreak} days",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                )
            }
            if (state.drillShowStartDialog) {
                DrillStartDialog(
                    hasProgress = state.drillHasProgress,
                    onStartFresh = {
                        vm.startDrill(resume = false)
                        screen = AppScreen.TRAINING
                    },
                    onResume = {
                        vm.startDrill(resume = true)
                        screen = AppScreen.TRAINING
                    },
                    onDismiss = { vm.dismissDrillDialog() }
                )
            }
            } // Box
        } // Column
    } // Surface
} // GrammarMateApp

private enum class AppScreen {
    HOME,
    LESSON,
    ELITE,
    VOCAB,
    STORY,
    TRAINING,
    LADDER,
    VERB_DRILL,
    VOCAB_DRILL
}

private fun parseScreen(name: String): AppScreen {
    return try { AppScreen.valueOf(name) } catch (_: IllegalArgumentException) { AppScreen.HOME }
}

private enum class LessonTileState {
    SEED,
    SPROUT,
    FLOWER,
    LOCKED,
    UNLOCKED,  // Available but not started yet (open lock)
    EMPTY,     // No lesson in this slot (pack has fewer than 12 lessons)
    VERB_DRILL

}

private data class LessonTileUi(
    val index: Int,
    val lessonId: String?,
    val state: LessonTileState
)

/**
 * Generate initials from user name (first letter of first two words, max 2 chars)
 */
private fun getUserInitials(name: String): String {
    return name.trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercase() }
        .joinToString("")
        .ifEmpty { "GM" } // Fallback: GrammarMate
}

@Composable
private fun WelcomeDialog(
    onNameSet: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = "Welcome to GrammarMate!",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "What's your name?",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(50) },
                    label = { Text("Enter your name") },
                    placeholder = { Text("e.g., John Smith") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onNameSet(if (nameInput.isBlank()) "GrammarMateUser" else nameInput.trim())
                        }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onNameSet(if (nameInput.isBlank()) "GrammarMateUser" else nameInput.trim())
                }
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onNameSet("GrammarMateUser")
                }
            ) {
                Text("Skip")
            }
        }
    )
}

@Composable
private fun HomeScreen(
    state: TrainingUiState,
    onSelectLanguage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSelectLesson: (String) -> Unit,
    onOpenElite: () -> Unit,
    hasVerbDrill: Boolean = false,
    hasVocabDrill: Boolean = false,
    onOpenVerbDrill: () -> Unit = {},
    onOpenVocabDrill: () -> Unit = {}
) {
    val tiles = remember(state.selectedLanguageId, state.lessons, state.testMode, state.lessonFlowers, state.selectedLessonId, state.activePackId, state.activePackLessonIds) {
        buildLessonTiles(state.lessons, state.testMode, state.lessonFlowers, state.selectedLessonId, state.activePackLessonIds)
    }
    var showMethod by remember { mutableStateOf(false) }
    var showRefreshHint by remember { mutableStateOf(false) }
    var showLockedLessonHint by remember { mutableStateOf(false) }
    var earlyStartLessonId by remember { mutableStateOf<String?>(null) }
    val languageCode = state.languages
        .firstOrNull { it.id == state.selectedLanguageId }
        ?.id
        ?.uppercase()
        ?: "--"
    val isFirstLaunch = state.correctCount == 0 &&
        state.incorrectCount == 0 &&
        state.activeTimeMs == 0L
    val activePackDisplayName = state.installedPacks
        .firstOrNull { it.packId == state.activePackId }
        ?.displayName
    val primaryLabel = when {
        state.sessionState == SessionState.ACTIVE -> activePackDisplayName ?: "Continue Learning"
        isFirstLaunch -> activePackDisplayName ?: "Start learning"
        else -> activePackDisplayName ?: "Start learning"
    }
    // Calculate the actual current lesson (first incomplete or first with most recent activity)
    val currentLessonIndex = state.lessons.indexOfFirst { it.id == state.selectedLessonId }
        .takeIf { it >= 0 }
        ?: 0

    // Get actual sub-lesson progress for the current lesson
    val currentLessonProgress = if (state.lessons.isNotEmpty() && state.selectedLessonId != null) {
        val currentLesson = state.lessons.getOrNull(currentLessonIndex)
        if (currentLesson != null && currentLesson.id == state.selectedLessonId) {
            "${state.completedSubLessonCount}/${state.subLessonCount}"
        } else {
            "1/10"  // Default if lesson not loaded yet
        }
    } else {
        "1/10"
    }

    val nextHint = if (state.lessons.isNotEmpty()) {
        "Lesson ${currentLessonIndex + 1}. Exercise $currentLessonProgress"
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = getUserInitials(state.userName),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = state.userName, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguageSelector(
                    label = languageCode,
                    languages = state.languages,
                    selectedLanguageId = state.selectedLanguageId,
                    onSelect = onSelectLanguage
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPrimaryAction)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = primaryLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                if (nextHint != null) {
                    Text(
                        text = nextHint,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Grammar Roadmap", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(tiles) { _, tile ->
                val flower = tile.lessonId?.let { state.lessonFlowers[it] }
                LessonTile(
                    tile = tile,
                    flower = flower,
                    onSelect = {
                        val lessonId = tile.lessonId ?: return@LessonTile
                        onSelectLesson(lessonId)
                    },
                    onLockedClick = {
                        // For locked tiles with a lesson, offer early start
                        if (tile.lessonId != null) {
                            earlyStartLessonId = tile.lessonId
                        } else {
                            showLockedLessonHint = true
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (hasVerbDrill || hasVocabDrill) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasVerbDrill) {
                    VerbDrillEntryTile(
                        modifier = Modifier.weight(1f),
                        onClick = onOpenVerbDrill
                    )
                }
                if (hasVocabDrill) {
                    VocabDrillEntryTile(
                        modifier = if (hasVerbDrill) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                        onClick = onOpenVocabDrill,
                        masteredCount = state.vocabMasteredCount
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        EliteEntryTile(
            enabled = state.eliteUnlocked,
            onClick = onOpenElite,
            onLockedClick = { showRefreshHint = true }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Legend:", fontWeight = FontWeight.SemiBold)
        Text(text = "🌱 seed • 🌿 growing • 🌸 bloom")
        Text(text = "🥀 wilting • 🍂 wilted • ⚫ forgotten")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showMethod = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "How This Training Works")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onPrimaryAction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Continue Learning")
        }
    }

    if (showMethod) {
        AlertDialog(
            onDismissRequest = { showMethod = false },
            confirmButton = {
                TextButton(onClick = { showMethod = false }) {
                    Text(text = "OK")
                }
            },
            title = { Text(text = "How This Training Works") },
            text = {
                Text(
                    text = "GrammarMate builds automatic grammar patterns with repeated retrieval. " +
                        "States show how stable each pattern is and when it needs refresh."
                )
            }
        )
    }
    if (showRefreshHint) {
        AlertDialog(
            onDismissRequest = { showRefreshHint = false },
            confirmButton = {
                TextButton(onClick = { showRefreshHint = false }) {
                    Text(text = "OK")
                }
            },
            title = { Text(text = "Refresh") },
            text = {
                Text(
                    text = "This mode is designed to keep all learned material active in the background. " +
                        "Refresh becomes available after completing the full course."
                )
            }
        )
    }
    if (showLockedLessonHint) {
        AlertDialog(
            onDismissRequest = { showLockedLessonHint = false },
            confirmButton = {
                TextButton(onClick = { showLockedLessonHint = false }) {
                    Text(text = "OK")
                }
            },
            title = { Text(text = "Lesson locked") },
            text = { Text(text = "Please complete the previous lesson first.") }
        )
    }
    if (earlyStartLessonId != null) {
        AlertDialog(
            onDismissRequest = { earlyStartLessonId = null },
            confirmButton = {
                TextButton(onClick = {
                    val lessonId = earlyStartLessonId
                    earlyStartLessonId = null
                    if (lessonId != null) {
                        onSelectLesson(lessonId)
                    }
                }) {
                    Text(text = "Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { earlyStartLessonId = null }) {
                    Text(text = "No")
                }
            },
            title = { Text(text = "Start early?") },
            text = { Text(text = "Start this lesson early? You can always come back to review previous lessons.") }
        )
    }
}

@Composable
private fun VerbDrillEntryTile(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(64.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Verb Drill", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun VocabDrillEntryTile(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    masteredCount: Int = 0
) {
    Card(
        modifier = modifier
            .height(64.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Vocab Drill", fontWeight = FontWeight.SemiBold)
            }
            if (masteredCount > 0) {
                Text(
                    text = "$masteredCount mastered",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
private fun EliteEntryTile(
    enabled: Boolean,
    onClick: () -> Unit,
    onLockedClick: () -> Unit
) {
    val labelColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { if (enabled) onClick() else onLockedClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Refresh", fontWeight = FontWeight.SemiBold, color = labelColor)
            if (!enabled) {
                Icon(Icons.Default.Lock, contentDescription = "Refresh locked")
            }
        }
    }
}

@Composable
private fun LessonRoadmapScreen(
    state: TrainingUiState,
    onBack: () -> Unit,
    onStartSubLesson: (Int) -> Unit,
    onOpenVocab: () -> Unit,
    onStartBossLesson: () -> Unit,
    onStartBossMega: () -> Unit,
    onDrillStart: () -> Unit = {}
) {
    val lessonTitle = state.lessons
        .firstOrNull { it.id == state.selectedLessonId }
        ?.title
        ?: "Lesson"
    val fallbackTotal = state.subLessonCount.coerceAtLeast(1)
    val fallbackNewOnlyCount = fallbackTotal.coerceAtMost(3)
    val trainingTypes = if (state.subLessonTypes.isNotEmpty()) {
        state.subLessonTypes
    } else {
        List(fallbackTotal) { index ->
            if (index < fallbackNewOnlyCount) SubLessonType.NEW_ONLY else SubLessonType.MIXED
        }
    }
    val total = trainingTypes.size.coerceAtLeast(1)
    val completed = state.completedSubLessonCount.coerceIn(0, total)
    val currentIndex = completed.coerceIn(0, total - 1)
    var earlyStartSubLessonIndex by remember { mutableStateOf<Int?>(null) }

    // Calculate current cycle (block of 15)
    val currentCycle = completed / 15
    val cycleStart = currentCycle * 15
    val cycleEnd = minOf(cycleStart + 15, total)

    // Show only current cycle's sublessons (max 15 at a time)
    // If all sublessons are completed, show empty list
    val visibleTrainingTypes = if (cycleStart < total) {
        trainingTypes.subList(cycleStart, cycleEnd)
    } else {
        emptyList()
    }

    val lessonIndex = state.lessons.indexOfFirst { it.id == state.selectedLessonId }
    val hasMegaBoss = lessonIndex > 0
    val currentLesson = state.lessons.firstOrNull { it.id == state.selectedLessonId }
    val totalCards = currentLesson?.allCards?.size ?: 0
    val shownCards = state.currentLessonShownCount.coerceAtMost(totalCards)
    val bossLessonReward = state.selectedLessonId?.let { state.bossLessonRewards[it] }
    val bossMegaReward = state.selectedLessonId?.let { state.bossMegaRewards[it] }
    val bossUnlocked = state.completedSubLessonCount >= 15 || state.testMode
    var bossLockedMessage by remember { mutableStateOf<String?>(null) }
    val hasDrill = currentLesson?.drillCards?.isNotEmpty() == true
    val entries = buildRoadmapEntries(visibleTrainingTypes, hasMegaBoss, cycleStart, hasDrill)
    var showVocabStartDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(text = lessonTitle, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(40.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = completed.toFloat() / total.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Show progress within current block of 15
            val displayIndex = (completed % 15) + 1
            val displayTotal = minOf(15, total - (completed / 15) * 15)
            Text(text = "Exercise $displayIndex of $displayTotal", textAlign = TextAlign.Center)
            Text(
                text = "Cards: $shownCards of $totalCards",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(entries) { _, entry ->
                when (entry) {
                    is RoadmapEntry.Training -> {
                        val index = entry.index
                        val isCompleted = index < completed
                        val isActive = index == currentIndex
                        val canEnter = state.testMode || isCompleted || isActive
                        val kindLabel = when (entry.type) {
                            SubLessonType.NEW_ONLY -> "NEW"
                            SubLessonType.MIXED -> "MIX"
                        }
                        // Use lesson flower for exercise tiles (they copy lesson state)
                        val flower = state.currentLessonFlower
                        val (emoji, scale) = when {
                            !isCompleted && !state.testMode -> {
                                (if (isActive) "\uD83D\uDD13" else "\uD83D\uDD12") to 1.0f
                            }
                            flower == null -> "\uD83C\uDF38" to 1.0f  // 🌸
                            else -> FlowerCalculator.getEmoji(flower.state) to flower.scaleMultiplier
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clickable {
                                    if (canEnter) {
                                        onStartSubLesson(index)
                                    } else {
                                        earlyStartSubLessonIndex = index
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "${index + 1}", fontWeight = FontWeight.SemiBold)
                                Text(text = emoji, fontSize = (18 * scale).sp)
                                Text(text = kindLabel, fontSize = 10.sp)
                            }
                        }
                    }
                    is RoadmapEntry.Vocab -> {
                        VocabTile(label = "Vocab", onClick = onOpenVocab)
                    }
                    is RoadmapEntry.Drill -> {
                        DrillTile(onClick = onDrillStart, enabled = true)
                    }
                    is RoadmapEntry.BossLesson -> {
                        BossTile(
                            label = "Review",
                            enabled = bossUnlocked,
                            reward = if (bossUnlocked) bossLessonReward else null,
                            locked = !bossUnlocked,
                            onClick = if (bossUnlocked) onStartBossLesson else {
                                { bossLockedMessage = "Complete at least 15 exercises first" }
                            }
                        )
                    }
                    is RoadmapEntry.BossMega -> {
                        BossTile(
                            label = "Mega",
                            enabled = bossUnlocked,
                            reward = if (bossUnlocked) bossMegaReward else null,
                            locked = !bossUnlocked,
                            onClick = if (bossUnlocked) onStartBossMega else {
                                { bossLockedMessage = "Complete at least 15 exercises first" }
                            }
                        )
                    }
                    // StoryCheckIn/StoryCheckOut kept for backward compat but no longer rendered
                    is RoadmapEntry.StoryCheckIn -> { }
                    is RoadmapEntry.StoryCheckOut -> { }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onStartSubLesson(currentIndex) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (completed == 0) "Start Lesson" else "Continue Lesson")
        }
    }

    if (earlyStartSubLessonIndex != null) {
        val idx = earlyStartSubLessonIndex!!
        AlertDialog(
            onDismissRequest = { earlyStartSubLessonIndex = null },
            confirmButton = {
                TextButton(onClick = {
                    earlyStartSubLessonIndex = null
                    onStartSubLesson(idx)
                }) {
                    Text(text = "Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { earlyStartSubLessonIndex = null }) {
                    Text(text = "No")
                }
            },
            title = { Text(text = "Start early?") },
            text = { Text(text = "Start exercise ${idx + 1} early? You can always come back to review.") }
        )
    }

    if (bossLockedMessage != null) {
        AlertDialog(
            onDismissRequest = { bossLockedMessage = null },
            confirmButton = {
                TextButton(onClick = { bossLockedMessage = null }) {
                    Text(text = "OK")
                }
            },
            title = { Text(text = "Locked") },
            text = { Text(text = "Complete at least 15 exercises first.") }
        )
    }
}

@Composable
private fun EliteRoadmapScreen(
    state: TrainingUiState,
    onBack: () -> Unit,
    onStartStep: (Int) -> Unit,
    onStartBoss: () -> Unit
) {
    val stepCount = com.alexpo.grammermate.data.TrainingConfig.ELITE_STEP_COUNT
    val currentIndex = state.eliteStepIndex.coerceIn(0, stepCount - 1)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Refresh", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(40.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(List(stepCount) { it }) { _, index ->
                EliteStepTile(
                    index = index,
                    isActive = index == currentIndex,
                    onClick = { onStartStep(index) }
                )
            }
            item {
                BossTile(label = "Review", enabled = true, reward = null, onClick = onStartBoss)
            }
        }
    }
}

@Composable
private fun EliteStepTile(
    index: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val icon = if (isActive) Icons.Default.PlayArrow else Icons.Default.Lock
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = isActive, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Step ${index + 1}", fontWeight = FontWeight.SemiBold)
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
private sealed class RoadmapEntry {
    data class Training(val index: Int, val type: SubLessonType) : RoadmapEntry()
    object Drill : RoadmapEntry()
    object Vocab : RoadmapEntry()
    object StoryCheckIn : RoadmapEntry()
    object StoryCheckOut : RoadmapEntry()
    object BossLesson : RoadmapEntry()
    object BossMega : RoadmapEntry()
}

private fun buildRoadmapEntries(
    trainingTypes: List<SubLessonType>,
    hasMegaBoss: Boolean,
    cycleStart: Int = 0,
    hasDrill: Boolean = false
): List<RoadmapEntry> {
    val entries = mutableListOf<RoadmapEntry>()
    entries.add(RoadmapEntry.Vocab)
    if (hasDrill) {
        entries.add(RoadmapEntry.Drill)
    }
    trainingTypes.forEachIndexed { index, type ->
        // Use absolute index for proper tracking
        entries.add(RoadmapEntry.Training(cycleStart + index, type))
    }
    entries.add(RoadmapEntry.BossLesson)
    if (hasMegaBoss) {
        entries.add(RoadmapEntry.BossMega)
    }
    return entries
}

@Composable
private fun VocabTile(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = Icons.Default.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun BossTile(label: String, enabled: Boolean, reward: BossReward?, locked: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        val tint = when (reward) {
            BossReward.BRONZE -> Color(0xFFCD7F32)
            BossReward.SILVER -> Color(0xFFC0C0C0)
            BossReward.GOLD -> Color(0xFFFFD700)
            null -> MaterialTheme.colorScheme.onSurface
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
            if (locked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = tint
                )
            }
        }
    }
}

@Composable
private fun DrillTile(
    onClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth().height(72.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = "Drill",
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(4.dp))
            Text("Drill", fontSize = 12.sp)
        }
    }
}

@Composable
private fun DrillStartDialog(
    hasProgress: Boolean,
    onStartFresh: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drill Mode") },
        text = { Text(if (hasProgress) "Continue where you left off or start over?" else "Start drill training?") },
        confirmButton = {
            TextButton(onClick = if (hasProgress) onResume else onStartFresh) {
                Text(if (hasProgress) "Continue" else "Start")
            }
        },
        dismissButton = {
            if (hasProgress) {
                TextButton(onClick = onStartFresh) { Text("Start Fresh") }
            }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DrillProgressRow(current: Int, total: Int, speed: Long, wordCount: Int) {
    val progress = if (total > 0) current.toFloat() / total else 0f
    val barColor = Color(0xFF4CAF50)
    val trackColor = Color(0xFFC8E6C9)
    val speedVal = if (speed > 0) (wordCount / (speed / 60000.0)).toInt() else 0
    val speedColor = when {
        speedVal <= 20 -> Color(0xFFE53935)
        speedVal <= 40 -> Color(0xFFFDD835)
        else -> Color(0xFF43A047)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress bar — 70% width, fill grows left to right
        Box(
            modifier = Modifier
                .weight(0.7f)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(24.dp)
                    .fillMaxWidth(progress)
                    .background(barColor, RoundedCornerShape(12.dp))
            )
            Text(
                text = "$current / $total",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (progress < 0.12f) Color(0xFF2E7D32) else Color.White,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Speedometer circle — 30% width, constrained to square
        Box(
            modifier = Modifier
                .weight(0.3f)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            val sizeModifier = Modifier.size(44.dp)
            Canvas(modifier = sizeModifier) {
                val strokeWidth = 4.dp.toPx()
                drawArc(
                    color = Color(0xFFE0E0E0),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )
                val sweep = 360f * (speedVal.coerceAtMost(100) / 100f)
                drawArc(
                    color = speedColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(
                text = "$speedVal",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = speedColor
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VocabSprintScreen(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: (String?) -> Unit,
    onSetInputMode: (InputMode) -> Unit,
    onRequestVoice: () -> Unit,
    onSpeak: (String) -> Unit,
    onShowAnswer: () -> Unit,
    onClose: () -> Unit
) {
    val vocab = state.currentVocab
    val latestState by rememberUpdatedState(state)
    val canLaunchVoice = vocab != null
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank() && latestState.currentVocab != null) {
                onInputChange(spoken)
                onSubmit(spoken)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(
        state.vocabVoiceTriggerToken,
        state.vocabInputMode,
        vocab?.id
    ) {
        if (state.vocabInputMode == InputMode.VOICE && vocab != null) {
            kotlinx.coroutines.delay(200)
            launchVoiceRecognition(state.selectedLanguageId, vocab.nativeText, speechLauncher)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Vocabulary Sprint", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        val progressText = if (state.vocabTotal > 0) {
            "${state.vocabIndex + 1} / ${state.vocabTotal}"
        } else {
            "0 / 0"
        }
        Text(text = progressText)
        Spacer(modifier = Modifier.height(16.dp))
        if (vocab != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = vocab.nativeText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Show text input only when not using Word Bank.
            if (state.vocabInputMode != InputMode.WORD_BANK) {
                OutlinedTextField(
                    value = state.vocabInputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Answer") }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Word Bank mode: show options when there are at least 2 choices.
            if (state.vocabInputMode == InputMode.WORD_BANK && state.vocabWordBankWords.size >= 2) {
                Text(
                    text = "Choose the correct translation:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.vocabWordBankWords.forEach { option ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                onInputChange(option)
                                onSubmit(option)
                            },
                            label = { Text(text = option) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (state.vocabInputMode == InputMode.WORD_BANK && state.vocabWordBankWords.size < 2) {
                // If options are insufficient, fall back to text input.
                OutlinedTextField(
                    value = state.vocabInputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Answer (not enough words for word bank)") }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            state.vocabAnswerText?.let { answer ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Answer: $answer", color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    TtsSpeakerButton(
                        ttsState = state.ttsState,
                        enabled = true,
                        onClick = { onSpeak(answer) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Action row with Show Answer and Check buttons.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(text = "Show answer") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = onShowAnswer,
                        enabled = state.vocabAnswerText == null
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Show answer")
                    }
                }
                Button(
                    onClick = { onSubmit(state.vocabInputText) },
                    modifier = Modifier.weight(1f),
                    enabled = state.vocabInputText.isNotBlank() || state.vocabInputMode == InputMode.WORD_BANK
                ) {
                    Text(text = "Check")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = vocab.nativeText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    }
                }
                Card(
                    modifier = Modifier
                        .width(96.dp)
                        .height(72.dp)
                ) {
                    val isVoice = state.vocabInputMode == InputMode.VOICE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val micClick = { onRequestVoice() }
                        if (isVoice) {
                            FilledTonalIconButton(onClick = micClick, enabled = canLaunchVoice) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice")
                            }
                        } else {
                            IconButton(onClick = micClick, enabled = canLaunchVoice) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (isVoice) {
                            IconButton(onClick = { onSetInputMode(InputMode.KEYBOARD) }) {
                                Icon(Icons.Default.Keyboard, contentDescription = "Keyboard")
                            }
                        } else {
                            FilledTonalIconButton(onClick = { onSetInputMode(InputMode.KEYBOARD) }) {
                                Icon(Icons.Default.Keyboard, contentDescription = "Keyboard")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (state.vocabInputMode == InputMode.WORD_BANK) {
                            FilledTonalIconButton(onClick = { onSetInputMode(InputMode.WORD_BANK) }) {
                                Icon(Icons.Default.LibraryBooks, contentDescription = "Word bank")
                            }
                        } else {
                            IconButton(onClick = { onSetInputMode(InputMode.WORD_BANK) }) {
                                Icon(Icons.Default.LibraryBooks, contentDescription = "Word bank")
                            }
                        }
                    }
                }
            }
        } else {
            Text(text = "No words")
        }
    }
}

@Composable
private fun StoryQuizScreen(
    story: com.alexpo.grammermate.data.StoryQuiz?,
    testMode: Boolean,
    onClose: () -> Unit,
    onComplete: (Boolean) -> Unit
) {
    if (story == null) {
        onClose()
        return
    }
    val selections = remember(story.storyId) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val results = remember(story.storyId) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var questionIndex by remember(story.storyId) { mutableStateOf(0) }
    var showResult by remember(story.storyId) { mutableStateOf(false) }
    var errorMessage by remember(story.storyId) { mutableStateOf<String?>(null) }
    if (story.questions.isEmpty()) {
        onComplete(true)
        return
    }
    val question = story.questions.getOrNull(questionIndex) ?: run {
        val allCorrect = results.value.size == story.questions.size &&
            results.value.values.all { it }
        onComplete(allCorrect)
        return
    }
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = if (story.phase == com.alexpo.grammermate.data.StoryPhase.CHECK_IN) {
                "Story Check-in"
            } else {
                "Story Check-out"
            },
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = story.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Question ${questionIndex + 1} / ${story.questions.size}")
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = question.prompt, fontWeight = FontWeight.SemiBold)
        question.options.forEachIndexed { index, option ->
            val selected = selections.value[question.qId] == index
            val isCorrectOption = index == question.correctIndex
            val optionSuffix = when {
                showResult && isCorrectOption -> " (correct)"
                showResult && selected && !isCorrectOption -> " (your choice)"
                else -> ""
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selections.value = selections.value + (question.qId to index)
                        errorMessage = null
                        if (showResult) {
                            results.value = results.value - question.qId
                            showResult = false
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (selected) ">" else " ")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = option + optionSuffix)
            }
        }
        if (showResult) {
            Spacer(modifier = Modifier.height(8.dp))
            val correctText = question.options.getOrNull(question.correctIndex).orEmpty()
            Text(text = "Correct: $correctText", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val prevIndex = (questionIndex - 1).coerceAtLeast(0)
                    if (prevIndex != questionIndex) {
                        questionIndex = prevIndex
                        val prevId = story.questions[questionIndex].qId
                        showResult = results.value.containsKey(prevId)
                        errorMessage = null
                    }
                },
                enabled = questionIndex > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Prev")
            }
            Button(
                onClick = {
                    val selected = selections.value[question.qId]
                    if (selected == null) {
                        errorMessage = "Select an answer"
                        return@Button
                    }
                    val correct = selected == question.correctIndex
                    results.value = results.value + (question.qId to correct)
                    showResult = true
                    errorMessage = if (correct) null else "Incorrect"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Check")
            }
            Button(
                onClick = {
                    val selected = selections.value[question.qId]
                    if (selected == null) {
                        errorMessage = "Select an answer"
                        return@Button
                    }
                    val correct = selected == question.correctIndex
                    results.value = results.value + (question.qId to correct)
                    val isLast = questionIndex >= story.questions.lastIndex
                    if (isLast) {
                        val allCorrect = results.value.size == story.questions.size &&
                            results.value.values.all { it }
                        onComplete(testMode || allCorrect)
                        return@Button
                    }
                    questionIndex += 1
                    val nextId = story.questions[questionIndex].qId
                    showResult = results.value.containsKey(nextId)
                    errorMessage = null
                },
                modifier = Modifier.weight(1f)
            ) {
                val isLast = questionIndex >= story.questions.lastIndex
                Text(text = if (isLast) "Finish" else "Next")
            }
        }
        if (scrollState.maxValue > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scroll to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
@Composable
private fun LessonTile(
    tile: LessonTileUi,
    flower: FlowerVisual?,
    onSelect: () -> Unit,
    onLockedClick: (() -> Unit)? = null
) {
    val isEmpty = tile.state == LessonTileState.EMPTY

    if (tile.state == LessonTileState.VERB_DRILL) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable(onClick = onSelect)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "Verb", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "Verb Drill",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        return
    }

    // Determine emoji and scale based on flower state
    val (emoji, scale) = when {
        isEmpty -> "●" to 1.0f  // gray dot for empty slots
        tile.state == LessonTileState.LOCKED -> "🔒" to 1.0f  // 🔒 closed lock
        tile.state == LessonTileState.UNLOCKED -> "🔓" to 1.0f  // 🔓 open lock
        flower == null -> "🌱" to 1.0f  // 🌱 seed default
        else -> FlowerCalculator.getEmoji(flower.state) to flower.scaleMultiplier
    }

    val masteryPercent = flower?.masteryPercent ?: 0f

    // Empty tiles use a faded container
    val containerColor = if (isEmpty) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isEmpty) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(
                if (isEmpty) Modifier
                else Modifier.clickable {
                    if (tile.state == LessonTileState.LOCKED) {
                        onLockedClick?.invoke()
                    } else {
                        onSelect()
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "${tile.index + 1}", fontWeight = FontWeight.SemiBold)
            Text(
                text = emoji,
                fontSize = (18 * scale).sp
            )
            // Show mastery percentage if > 0 (but not for locked/unlocked/empty states)
            if (masteryPercent > 0f && tile.state != LessonTileState.LOCKED && tile.state != LessonTileState.UNLOCKED && !isEmpty) {
                Text(
                    text = "${(masteryPercent * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    label: String,
    languages: List<com.alexpo.grammermate.data.Language>,
    selectedLanguageId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Text(text = label)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        languages.forEach { language ->
            DropdownMenuItem(
                text = { Text(text = language.displayName) },
                onClick = {
                    expanded = false
                    if (language.id != selectedLanguageId) onSelect(language.id)
                }
            )
        }
    }
}

private fun buildLessonTiles(
    lessons: List<Lesson>,
    testMode: Boolean,
    lessonFlowers: Map<String, FlowerVisual>,
    selectedLessonId: String?,
    activePackLessonIds: List<String>?
): List<LessonTileUi> {
    // Filter lessons to only those in the active pack
    val packLessons = if (activePackLessonIds != null) {
        // Preserve the order from the pack's lesson list
        activePackLessonIds.mapNotNull { id -> lessons.firstOrNull { it.id == id } }
    } else {
        lessons
    }
    val total = 12
    val tiles = mutableListOf<LessonTileUi>()

    // Find the highest lesson with any progress (masteryPercent > 0)
    var lastLessonWithProgress = -1
    for (i in packLessons.indices) {
        val flower = lessonFlowers[packLessons[i].id]
        android.util.Log.d("GrammarMate", "buildLessonTiles: lesson $i (${packLessons[i].id}) -> masteryPercent=${flower?.masteryPercent}")
        if (flower != null && flower.masteryPercent > 0f) {
            lastLessonWithProgress = i
        }
    }
    android.util.Log.d("GrammarMate", "buildLessonTiles: lastLessonWithProgress=$lastLessonWithProgress, next UNLOCKED will be at index ${lastLessonWithProgress + 1}")

    for (i in 0 until total) {
        val lesson = packLessons.getOrNull(i)
        val state = when {
            // No lesson exists at this index in the pack - show empty slot
            lesson == null -> LessonTileState.EMPTY
            testMode -> LessonTileState.SEED
            i == 0 -> LessonTileState.SPROUT
            else -> {
                val currentFlower = lessonFlowers[lesson.id]

                when {
                    // Current lesson has progress - show flower
                    currentFlower != null && currentFlower.masteryPercent > 0f -> {
                        LessonTileState.SEED
                    }
                    // This is the lesson right after the last one with progress - UNLOCKED (open lock)
                    i == lastLessonWithProgress + 1 -> {
                        LessonTileState.UNLOCKED
                    }
                    // This lesson is before the last with progress - check if previous has progress
                    i < lastLessonWithProgress + 1 -> {
                        val prevLesson = packLessons.getOrNull(i - 1)
                        val prevFlower = prevLesson?.let { lessonFlowers[it.id] }
                        if (prevFlower != null && prevFlower.masteryPercent > 0f) {
                            LessonTileState.UNLOCKED
                        } else {
                            LessonTileState.LOCKED
                        }
                    }
                    // All other lessons are locked
                    else -> {
                        LessonTileState.LOCKED
                    }
                }
            }
        }
        tiles.add(LessonTileUi(i, lesson?.id, state))
    }
    return tiles
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
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
    onStartAsrDownload: () -> Unit
) {
    if (!show) return
    val sheetState = rememberModalBottomSheetState()
    var newLessonTitle by remember { mutableStateOf("") }
    var newLanguageName by remember { mutableStateOf("") }
    var vocabLimitText by remember(state.vocabSprintLimit) { mutableStateOf(state.vocabSprintLimit.toString()) }
    var userNameInput by remember(state.userName) { mutableStateOf(state.userName) }
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
                text = "Service Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Test Mode", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.testMode,
                    onCheckedChange = { onToggleTestMode() }
                )
            }
            Text(
                text = "Enables all lessons, accepts all answers, unlocks Elite mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            OutlinedButton(
                onClick = onOpenLadder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Insights, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Показать лестницу")
            }
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
                label = { Text(text = "Vocabulary Sprint limit") }
            )
            Text(
                text = "Set how many words to show (0 = all words)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // TTS speed control
            Text(
                text = "Pronunciation speed",
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
                    value = state.ttsSpeed,
                    onValueChange = onSetTtsSpeed,
                    valueRange = 0.5f..1.5f,
                    steps = 3,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "1.5x", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = String.format("%.2fx", state.ttsSpeed),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Offline ASR toggle
            Text(
                text = "Voice recognition",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Offline speech recognition", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = state.useOfflineAsr,
                    onCheckedChange = { enabled ->
                        onSetUseOfflineAsr(enabled)
                        if (enabled && !state.asrModelReady) {
                            onStartAsrDownload()
                        }
                    }
                )
            }
            when (val dlState = state.asrDownloadState) {
                is DownloadState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { dlState.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Downloading model... ${dlState.percent}%",
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
                        text = "Extracting model... ${dlState.percent}%",
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
                        text = if (state.useOfflineAsr) {
                            if (state.asrModelReady) "Using on-device recognition (no internet required)" else "Model not downloaded yet"
                        } else {
                            "Using Google speech recognition (requires internet)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Russian text size control
            Text(
                text = "Размер текста перевода",
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
                    value = state.ruTextScale,
                    onValueChange = onSetRuTextScale,
                    valueRange = 1.0f..2.0f,
                    steps = 3,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "2x", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = String.format("%.1fx", state.ruTextScale),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            LanguageLessonColumn(state, onSelectLanguage, onSelectPack)
            OutlinedTextField(
                value = newLanguageName,
                onValueChange = { newLanguageName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "New language") }
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
                Text(text = "Add language")
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
                Text(text = "Import lesson pack (ZIP)")
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("text/*", "text/csv")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Import lesson (CSV)")
            }
            OutlinedButton(
                onClick = { resetLauncher.launch(arrayOf("text/*", "text/csv")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Reset/Reload (clear + import)")
            }
            OutlinedTextField(
                value = newLessonTitle,
                onValueChange = { newLessonTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Empty lesson (title)") }
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
                Text(text = "Create empty lesson")
            }
            OutlinedButton(
                onClick = { onDeleteAllLessons() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB00020))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Delete all lessons")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "CSV format", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "UTF-8, delimiter ';'.\n" +
                    "Column 1: Native sentence.\n" +
                    "Column 2: Translation(s), variants via '+'.\n" +
                    "Example: He doesn't work from home;Он не работает из дома",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Instructions", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "Play - start/continue, Pause - pause, Stop - reset progress without updating rating.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Packs", style = MaterialTheme.typography.labelLarge)
            if (state.installedPacks.isEmpty()) {
                Text(
                    text = "No installed packs",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                state.installedPacks.forEach { pack ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pack.displayName ?: "${pack.packId} (${pack.packVersion})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onDeletePack(pack.packId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete pack")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = userNameInput,
                onValueChange = { userNameInput = it.take(50) },
                label = { Text("Your Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val trimmed = userNameInput.trim()
                    if (trimmed.isNotEmpty() && trimmed != state.userName) {
                        onUpdateUserName(trimmed)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = userNameInput.trim().isNotEmpty() && userNameInput.trim() != state.userName
            ) {
                Text("Save Name")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Restore progress from backup folder (Downloads/BaseGrammy)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            OutlinedButton(
                onClick = onSaveProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save progress now")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { restoreBackupLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Restore from backup")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LadderScreen(
    state: TrainingUiState,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "Лестница интервалов",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Все уроки текущего пакета",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.ladderRows.isEmpty()) {
            Text(
                text = "Нет данных по урокам.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            return
        }

        LadderHeaderRow()
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.ladderRows) { row ->
                LadderRowCard(row)
            }
        }
    }
}

@Composable
private fun LadderHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(28.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "Урок",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "Карты",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "Дней",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "Интервал",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(92.dp),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun LadderRowCard(row: LessonLadderRow) {
    val isOverdue = row.intervalLabel?.startsWith("Просрочка") == true
    val containerColor = if (isOverdue) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isOverdue) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val cardsText = row.uniqueCardShows?.toString() ?: "-"
    val daysText = row.daysSinceLastShow?.toString() ?: "-"
    val intervalText = row.intervalLabel ?: "-"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.index.toString(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(28.dp)
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = cardsText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(56.dp),
                textAlign = TextAlign.End
            )
            Text(
                text = daysText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End
            )
            Text(
                text = intervalText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(92.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingScreen(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> SubmitResult,
    onPrev: () -> Unit,
    onNext: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onRequestExit: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowSettings: () -> Unit,
    onSelectLesson: (String) -> Unit,
    onSelectMode: (TrainingMode) -> Unit,
    onSetInputMode: (InputMode) -> Unit,
    onShowAnswer: () -> Unit,
    onVoicePromptStarted: () -> Unit,
    onSelectWordFromBank: (String) -> Unit,
    onRemoveLastWord: () -> Unit,
    onTtsSpeak: () -> Unit,
    onFlagBadSentence: () -> Unit = {},
    onUnflagBadSentence: () -> Unit = {},
    onHideCard: () -> Unit = {},
    onExportBadSentences: () -> String? = { null },
    isBadSentence: () -> Boolean = { false },
    onStartOfflineRecognition: () -> Unit = {}
) {
    val hasCards = state.currentCard != null
    val scrollState = rememberScrollState()
    val drillGreen = Color(0xFFE8F5E9)

    Scaffold(
        containerColor = if (state.isDrillMode) drillGreen else MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "GrammarMate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    onOpenSettings()
                    onShowSettings()
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.bossActive) {
                Text(text = "Review Session", fontWeight = FontWeight.SemiBold)
            } else if (state.eliteActive) {
                Text(text = "Refresh Session", fontWeight = FontWeight.SemiBold)
            } else if (state.isDrillMode) {
                // Drill: prompt without hints + progress bar + speedometer
                val cardTense = state.currentCard?.tense
                if (!cardTense.isNullOrBlank()) {
                    Text(
                        text = cardTense,
                        fontSize = 13.sp,
                        color = Color(0xFF388E3C),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val rawPrompt = state.currentCard?.promptRu ?: ""
                val cleanPrompt = rawPrompt.replace(Regex("\\s*\\([^)]+\\)"), "")
                if (cleanPrompt.isNotBlank()) {
                    Text(
                        text = cleanPrompt,
                        fontSize = (18f * state.ruTextScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DrillProgressRow(
                    current = state.drillCardIndex + 1,
                    total = state.drillTotalCards,
                    speed = state.voiceActiveMs,
                    wordCount = state.voiceWordCount
                )
            } else {
                val cardTense = state.currentCard?.tense
                if (!cardTense.isNullOrBlank()) {
                    Text(
                        text = cardTense,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val rawPrompt = state.currentCard?.promptRu ?: ""
                val cleanPrompt = rawPrompt.replace(Regex("\\s*\\([^)]+\\)"), "")
                if (cleanPrompt.isNotBlank()) {
                    Text(
                        text = cleanPrompt,
                        fontSize = (18f * state.ruTextScale).sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val total = if (state.bossActive) state.bossTotal else state.subLessonTotal
                val current = if (state.bossActive) state.bossProgress else state.currentIndex
                DrillProgressRow(
                    current = (current + 1).coerceAtMost(total.coerceAtLeast(1)),
                    total = total.coerceAtLeast(1),
                    speed = state.voiceActiveMs,
                    wordCount = state.voiceWordCount
                )
            }
            CardPrompt(state, onSpeak = onTtsSpeak)
            AnswerBox(
                state,
                onInputChange,
                onSubmit,
                onSetInputMode,
                onShowAnswer,
                onVoicePromptStarted,
                onSelectWordFromBank,
                onRemoveLastWord,
                hasCards,
                onFlagBadSentence,
                onUnflagBadSentence,
                onHideCard,
                onExportBadSentences,
                isBadSentence,
                onStartOfflineRecognition
            )
            ResultBlock(state, onSpeak = onTtsSpeak)
            NavigationRow(onPrev, onNext, onTogglePause, onRequestExit, state.sessionState, hasCards)
        }
    }
}

@Composable
private fun HeaderStats(state: TrainingUiState, isDrillMode: Boolean = false) {
    val total = if (state.bossActive) state.bossTotal else state.subLessonTotal
    val progressIndex = if (total > 0) {
        if (state.bossActive) {
            state.bossProgress.coerceIn(0, total)
        } else {
            state.currentIndex.coerceIn(0, total)
        }
    } else {
        0
    }
    val progressPercent = if (total > 0) {
        ((progressIndex.toDouble() / total.toDouble()) * 100).toInt()
    } else {
        0
    }
    val speed = speedPerMinute(state.voiceActiveMs, state.voiceWordCount)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isDrillMode) {
            Column {
                Text(text = if (state.bossActive) "Review" else "Progress")
                val progressText = when {
                    state.bossActive -> "${progressPercent}% (${progressIndex}/${total})"
                    state.mode == TrainingMode.ALL_MIXED -> "${progressPercent}% (${progressIndex}/${total})"
                    else -> "${progressPercent}%"
                }
                Text(
                    text = progressText,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (!isDrillMode) {
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Time")
                Text(text = formatTime(state.activeTimeMs), fontWeight = FontWeight.SemiBold)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "Speed")
            Text(text = speed, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ModeSelector(
    state: TrainingUiState,
    onSelectMode: (TrainingMode) -> Unit,
    onSelectLesson: (String) -> Unit
) {
    var lessonExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ModeIconButton(
                selected = state.mode == TrainingMode.LESSON,
                icon = Icons.Default.MenuBook,
                contentDescription = "Lesson"
            ) {
                onSelectMode(TrainingMode.LESSON)
                lessonExpanded = true
            }
            DropdownMenu(
                expanded = lessonExpanded,
                onDismissRequest = { lessonExpanded = false }
            ) {
                if (state.lessons.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(text = "No lessons") },
                        onClick = { lessonExpanded = false }
                    )
                } else {
                    state.lessons.forEach { lesson ->
                        DropdownMenuItem(
                            text = { Text(text = lesson.title) },
                            onClick = {
                                lessonExpanded = false
                                onSelectLesson(lesson.id)
                            }
                        )
                    }
                }
            }
        }
        ModeIconButton(
            selected = state.mode == TrainingMode.ALL_SEQUENTIAL,
            icon = Icons.Default.LibraryBooks,
            contentDescription = "All lessons"
        ) { onSelectMode(TrainingMode.ALL_SEQUENTIAL) }
        ModeIconButton(
            selected = state.mode == TrainingMode.ALL_MIXED,
            icon = Icons.Default.SwapHoriz,
            contentDescription = "Mixed"
        ) { onSelectMode(TrainingMode.ALL_MIXED) }
    }
}

@Composable
private fun ModeIconButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun LanguageLessonColumn(
    state: TrainingUiState,
    onSelectLanguage: (String) -> Unit,
    onSelectPack: (String) -> Unit
) {
    val languagePacks = state.installedPacks.filter { it.languageId == state.selectedLanguageId }
    val selectedPackLabel = state.activePackId?.let { activeId ->
        languagePacks.firstOrNull { it.packId == activeId }?.let { pack ->
            pack.displayName ?: "${pack.packId} (${pack.packVersion})"
        }
    } ?: "-"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownSelector(
            title = "Language",
            selected = state.languages.firstOrNull { it.id == state.selectedLanguageId }?.displayName
                ?: "-",
            items = state.languages.map { it.displayName to it.id },
            onSelect = onSelectLanguage
        )
        DropdownSelector(
            title = "Pack",
            selected = selectedPackLabel,
            items = languagePacks.map { pack ->
                (pack.displayName ?: "${pack.packId} (${pack.packVersion})") to pack.packId
            },
            onSelect = onSelectPack
        )
    }
}

@Composable
private fun DropdownSelector(
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

@Composable
private fun CardPrompt(state: TrainingUiState, onSpeak: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "RU", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.currentCard?.promptRu ?: "No cards",
                    fontSize = (20f * state.ruTextScale).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TtsSpeakerButton(
                ttsState = state.ttsState,
                enabled = state.currentCard != null,
                onClick = onSpeak
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun AnswerBox(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> SubmitResult,
    onSetInputMode: (InputMode) -> Unit,
    onShowAnswer: () -> Unit,
    onVoicePromptStarted: () -> Unit,
    onSelectWordFromBank: (String) -> Unit,
    onRemoveLastWord: () -> Unit,
    hasCards: Boolean,
    onFlagBadSentence: () -> Unit = {},
    onUnflagBadSentence: () -> Unit = {},
    onHideCard: () -> Unit = {},
    onExportBadSentences: () -> String? = { null },
    isBadSentence: () -> Boolean = { false },
    onStartOfflineRecognition: () -> Unit = {}
) {
    val latestState by rememberUpdatedState(state)
    val canLaunchVoice = hasCards && state.sessionState == SessionState.ACTIVE
    val clipboardManager = LocalClipboardManager.current
    var showReportSheet by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val reportCard = state.currentCard
    val reportText = if (reportCard != null) {
        val targetText = reportCard.acceptedAnswers.joinToString(" / ")
        "ID: ${reportCard.id}\nSource: ${reportCard.promptRu}\nTarget: $targetText"
    } else {
        ""
    }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (latestState.sessionState == SessionState.PAUSED) return@rememberLauncherForActivityResult
                onInputChange(spoken)
                onSubmit()
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(
        state.currentCard?.id,
        state.inputMode,
        state.sessionState,
        state.voiceTriggerToken
    ) {
        if (state.inputMode == InputMode.VOICE &&
            state.sessionState == SessionState.ACTIVE &&
            state.currentCard != null
        ) {
            kotlinx.coroutines.delay(200)
            onVoicePromptStarted()
            if (state.useOfflineAsr && state.asrModelReady) {
                onStartOfflineRecognition()
            } else {
                launchVoiceRecognition(state.selectedLanguageId, state.currentCard?.promptRu, speechLauncher)
            }
        }
    }
    if (showReportSheet) {
        val cardIsBad = isBadSentence()
        ModalBottomSheet(
            onDismissRequest = { showReportSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Card options",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (reportCard != null) {
                    Text(
                        text = reportCard.promptRu,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (cardIsBad) {
                    TextButton(
                        onClick = {
                            onUnflagBadSentence()
                            showReportSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove from bad sentences list")
                    }
                } else {
                    TextButton(
                        onClick = {
                            onFlagBadSentence()
                            showReportSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to bad sentences list")
                    }
                }
                TextButton(
                    onClick = {
                        onHideCard()
                        showReportSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hide this card from lessons")
                }
                TextButton(
                    onClick = {
                        val path = onExportBadSentences()
                        exportMessage = if (path != null) "Exported to $path" else "No bad sentences to export"
                        showReportSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export bad sentences to file")
                }
                TextButton(
                    onClick = {
                        if (reportText.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(reportText))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy text")
                }
            }
        }
    }
    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("Export") },
            text = { Text(exportMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.inputText,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Your translation") },
            enabled = hasCards,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (canLaunchVoice) {
                            onSetInputMode(InputMode.VOICE)
                        }
                    },
                    enabled = canLaunchVoice
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input")
                }
            }
        )
        if (!hasCards) {
            Text(
                text = "No cards",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.inputMode == InputMode.VOICE && state.sessionState == SessionState.ACTIVE) {
            Text(
                text = state.currentCard?.promptRu?.let { "Say translation: $it" } ?: "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.useOfflineAsr) {
            AsrStatusIndicator(state.asrState)
        }

        // Word Bank UI
        if (state.inputMode == InputMode.WORD_BANK && state.wordBankWords.isNotEmpty()) {
            Text(
                text = "Tap words in correct order:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                state.wordBankWords.forEach { word ->
                    // Count how many times this word appears in the word bank
                    val availableCount = state.wordBankWords.count { it == word }
                    // Count how many times this word has been selected
                    val usedCount = state.selectedWords.count { it == word }
                    // Word is fully used only when all instances are selected
                    val isFullyUsed = usedCount >= availableCount

                    FilterChip(
                        selected = usedCount > 0,
                        onClick = {
                            if (!isFullyUsed) {
                                onSelectWordFromBank(word)
                            }
                        },
                        label = { Text(text = word) },
                        enabled = !isFullyUsed
                    )
                }
            }
            if (state.selectedWords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected: ${state.selectedWords.size} / ${state.wordBankWords.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onRemoveLastWord) {
                        Text(text = "Undo")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canSelectInputMode = hasCards && state.sessionState == SessionState.ACTIVE
                FilledTonalIconButton(
                    onClick = {
                        if (canLaunchVoice) {
                            onSetInputMode(InputMode.VOICE)
                        }
                    },
                    enabled = canLaunchVoice
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice mode")
                }
                FilledTonalIconButton(
                    onClick = { onSetInputMode(InputMode.KEYBOARD) },
                    enabled = canSelectInputMode
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard mode")
                }
                FilledTonalIconButton(
                    onClick = { onSetInputMode(InputMode.WORD_BANK) },
                    enabled = canSelectInputMode
                ) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = "Word bank mode")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(text = "Show answer") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { if (hasCards) onShowAnswer() },
                        enabled = hasCards
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Show answer")
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(text = "Report sentence") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { if (hasCards) showReportSheet = true },
                        enabled = hasCards
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = "Report sentence")
                    }
                }
                Text(
                    text = when (state.inputMode) {
                        InputMode.VOICE -> "Voice"
                        InputMode.KEYBOARD -> "Keyboard"
                        InputMode.WORD_BANK -> "Word Bank"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Button(
            onClick = { onSubmit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasCards &&
                state.inputText.isNotBlank() &&
                state.sessionState == SessionState.ACTIVE &&
                state.currentCard != null
        ) {
            Text(text = "Check")
        }
    }
}

@Composable
private fun ResultBlock(state: TrainingUiState, onSpeak: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state.lastResult) {
                true -> Text(text = "Correct", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                false -> Text(text = "Incorrect", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                null -> Text(text = "")
            }
            if (!state.answerText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                TtsSpeakerButton(
                    ttsState = state.ttsState,
                    enabled = true,
                    onClick = onSpeak
                )
            }
        }
        if (!state.answerText.isNullOrBlank()) {
            Text(text = "Answer: ${state.answerText}")
        }
    }
}

@Composable
private fun NavigationRow(
    onPrev: () -> Unit,
    onNext: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onRequestExit: () -> Unit,
    state: SessionState,
    hasCards: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavIconButton(onClick = onPrev, enabled = hasCards) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Prev")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NavIconButton(onClick = onTogglePause, enabled = hasCards) {
                if (state == SessionState.ACTIVE) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
            NavIconButton(onClick = onRequestExit, enabled = hasCards) {
                Icon(Icons.Default.StopCircle, contentDescription = "Exit session")
            }
            NavIconButton(onClick = { onNext(false) }, enabled = hasCards) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
            }
        }
    }
}

@Composable
private fun NavIconButton(
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

private fun formatTime(activeMs: Long): String {
    val totalSeconds = activeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun speedPerMinute(activeMs: Long, correct: Int): String {
    val minutes = activeMs / 60000.0
    if (minutes <= 0.0) return "-"
    return String.format("%.1f", correct / minutes)
}

private fun launchVoiceRecognition(
    languageId: String,
    prompt: String?,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val languageTag = when (languageId) {
        "it" -> "it-IT"
        else -> "en-US"
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt ?: "Say the translation")
    }
    launcher.launch(intent)
}

@Composable
private fun TtsSpeakerButton(
    ttsState: TtsState,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = {
            onClick()
        },
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

@Composable
private fun TtsDownloadDialog(
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

@Composable
private fun MeteredNetworkDialog(
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

@Composable
private fun AsrMeteredNetworkDialog(
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
private fun AsrStatusIndicator(asrState: AsrState) {
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
        else -> { /* IDLE, INITIALIZING, READY — no indicator */ }
    }
}

private fun calcBgDownloadProgress(states: Map<String, DownloadState>): Float {
    if (states.isEmpty()) return 0f
    var total = 0f
    for (s in states.values) {
        total += when (s) {
            is DownloadState.Downloading -> s.percent / 100f * 0.9f
            is DownloadState.Extracting -> 0.9f + s.percent / 100f * 0.1f
            is DownloadState.Done -> 1f
            else -> 0f
        }
    }
    return (total / states.size).coerceIn(0f, 1f)
}
