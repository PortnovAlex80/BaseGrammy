package com.alexpo.grammermate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.TtsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Imported from extracted screen files
import com.alexpo.grammermate.ui.screens.HomeScreen
import com.alexpo.grammermate.ui.screens.LessonRoadmapScreen
import com.alexpo.grammermate.ui.screens.StoryQuizScreen
import com.alexpo.grammermate.ui.screens.TrainingScreen
import com.alexpo.grammermate.ui.screens.SettingsSheet
import com.alexpo.grammermate.ui.screens.LadderScreen
import com.alexpo.grammermate.ui.components.TtsDownloadDialog
import com.alexpo.grammermate.ui.components.MeteredNetworkDialog
import com.alexpo.grammermate.ui.components.AsrMeteredNetworkDialog

@Composable
fun GrammarMateApp() {
    Surface(modifier = Modifier.fillMaxSize()) {
        val vm: TrainingViewModel = viewModel()
        val state by vm.uiState.collectAsState()
        var screen by remember { mutableStateOf(parseScreen(state.navigation.initialScreen)) }
        var previousScreen by remember { mutableStateOf(AppScreen.HOME) }
        var showSettings by remember { mutableStateOf(false) }
        var showExitDialog by remember { mutableStateOf(false) }
        var showWelcomeDialog by remember { mutableStateOf(false) }
        var showDailyResumeDialog by remember { mutableStateOf(false) }
        var pendingDailyLevel by remember { mutableStateOf(0) }
        var isLoadingDaily by remember { mutableStateOf(false) }
        val dailyScope = rememberCoroutineScope()
        val lastFinishedToken = remember { mutableStateOf(state.cardSession.subLessonFinishedToken) }
        val lastBossFinishedToken = remember { mutableStateOf(state.boss.bossFinishedToken) }
        var showTtsDownloadDialog by remember { mutableStateOf(false) }

        LaunchedEffect(screen) {
            vm.onScreenChanged(screen.name)
        }

        val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { /* permission result handled by system */ }

        val context = LocalContext.current
        val lessonStore = remember { com.alexpo.grammermate.data.LessonStoreImpl(context) }
        val hasVerbDrill = remember(state.navigation.activePackId, state.navigation.selectedLanguageId) {
            state.navigation.activePackId?.let { lessonStore.hasVerbDrill(it, state.navigation.selectedLanguageId) } ?: false
        }
        val hasVocabDrill = remember(state.navigation.activePackId, state.navigation.selectedLanguageId) {
            state.navigation.activePackId?.let { lessonStore.hasVocabDrill(it, state.navigation.selectedLanguageId) } ?: false
        }

        val onTtsSpeak: () -> Unit = {
            if (state.audio.ttsState == TtsState.SPEAKING) {
                vm.stopTts()
            } else if (!state.audio.ttsModelReady) {
                val bgState = state.audio.bgTtsDownloadStates[state.navigation.selectedLanguageId]
                if (bgState != null && bgState !is DownloadState.Idle) {
                    vm.setTtsDownloadStateFromBackground(bgState)
                }
                showTtsDownloadDialog = true
            } else {
                val text = state.cardSession.answerText
                    ?: state.cardSession.currentCard?.acceptedAnswers?.firstOrNull()
                if (text != null) {
                    vm.onTtsSpeak(text, speed = 0.67f)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Persistent TTS download progress bar — always visible on all screens
            AnimatedVisibility(visible = state.audio.bgTtsDownloading) {
                LinearProgressIndicator(
                    progress = { calcBgDownloadProgress(state.audio.bgTtsDownloadStates) },
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
                BackHandler(enabled = screen == AppScreen.DAILY_PRACTICE && !showSettings) {
                    showExitDialog = true
                }
                BackHandler(enabled = screen == AppScreen.MIX_CHALLENGE && !showSettings) {
                    showExitDialog = true
                }
                BackHandler(enabled = screen == AppScreen.STORY && !showSettings) {
                    screen = AppScreen.LESSON
                }
                BackHandler(enabled = screen == AppScreen.LADDER && !showSettings) {
                    screen = previousScreen
                    if (previousScreen == AppScreen.TRAINING && state.cardSession.currentCard != null) {
                        vm.resumeFromSettings()
                    }
                }
                BackHandler(enabled = screen == AppScreen.VERB_DRILL && !showSettings) {
                    screen = AppScreen.HOME
                }
                BackHandler(enabled = screen == AppScreen.VOCAB_DRILL && !showSettings) {
                    vm.refreshVocabMasteryCount()
                    screen = AppScreen.HOME
                }

                SettingsSheet(
                    show = showSettings,
                    state = state,
                    onDismiss = {
                        showSettings = false
                        if (screen == AppScreen.TRAINING && state.cardSession.currentCard != null) {
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
                    onStartAsrDownload = { vm.startAsrDownload() },
                    onResetAllProgress = vm::resetAllProgress,
                    onSetHintLevel = vm::setHintLevel
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
                if (state.audio.ttsDownloadState is DownloadState.Done) {
                    showTtsDownloadDialog = false
                    vm.dismissTtsDownloadDialog()
                    // Auto-play TTS after download
                    val text = state.cardSession.answerText ?: state.cardSession.currentCard?.acceptedAnswers?.firstOrNull()
                    if (text != null) vm.onTtsSpeak(text, speed = 0.67f)
                }
                if (showTtsDownloadDialog) {
                    TtsDownloadDialog(
                        downloadState = state.audio.ttsDownloadState,
                        languageId = state.navigation.selectedLanguageId,
                        onConfirm = { vm.startTtsDownload() },
                        onDismiss = { vm.dismissTtsDownloadDialog(); showTtsDownloadDialog = false }
                    )
                }
            }
            // M6: Metered network warning
            if (state.audio.ttsMeteredNetwork) {
                MeteredNetworkDialog(
                    onConfirm = { vm.confirmTtsDownloadOnMetered() },
                    onDismiss = { vm.dismissMeteredWarning(); vm.dismissTtsDownloadDialog(); showTtsDownloadDialog = false }
                )
            }
            // ASR metered network warning
            if (state.audio.asrMeteredNetwork) {
                AsrMeteredNetworkDialog(
                    onConfirm = { vm.confirmAsrDownloadOnMetered() },
                    onDismiss = { vm.dismissAsrMeteredWarning(); vm.dismissAsrDownloadDialog() }
                )
            }
            LaunchedEffect(screen, state.navigation.userName) {
                if (state.navigation.userName == "GrammarMateUser") {
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
                    onOpenElite = {
                        // Use progress-based lesson level, NOT derived from selectedLessonId.
                        // selectedLessonId changes when the user previews locked lessons,
                        // but daily practice must follow the actual learning path.
                        val level = vm.getProgressLessonLevel()
                        if (vm.hasResumableDailySession()) {
                            pendingDailyLevel = level
                            showDailyResumeDialog = true
                        } else {
                            isLoadingDaily = true
                            dailyScope.launch {
                                val started = withContext(Dispatchers.IO) {
                                    vm.startDailyPractice(level)
                                }
                                isLoadingDaily = false
                                if (started) screen = AppScreen.DAILY_PRACTICE
                            }
                        }
                    },
                    hasVerbDrill = hasVerbDrill,
                    hasVocabDrill = hasVocabDrill,
                    onOpenVerbDrill = { screen = AppScreen.VERB_DRILL },
                    onOpenVocabDrill = { screen = AppScreen.VOCAB_DRILL },
                    onOpenMixChallenge = {
                        isLoadingDaily = true
                        dailyScope.launch {
                            val started = withContext(Dispatchers.IO) {
                                vm.startMixChallenge()
                            }
                            isLoadingDaily = false
                            if (started) screen = AppScreen.MIX_CHALLENGE
                        }
                    }
                )
                AppScreen.LESSON -> LessonRoadmapScreen(
                    state = state,
                    onBack = { screen = AppScreen.HOME },
                    onStartSubLesson = { index ->
                        vm.selectSubLesson(index)
                        screen = AppScreen.TRAINING
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
                        state.navigation.selectedLessonId?.let { vm.showDrillStartDialog(it) }
                    }
                )
                AppScreen.ELITE -> { screen = AppScreen.HOME }
                AppScreen.VOCAB -> { screen = AppScreen.HOME }
                AppScreen.DAILY_PRACTICE -> {
                    val dailyState = state.daily.dailySession
                    val dailyTask = vm.getDailyCurrentTask()
                    val dailyProgress = vm.getDailyBlockProgress()
                    DailyPracticeScreen(
                        state = dailyState,
                        blockProgress = dailyProgress,
                        currentTask = dailyTask,
                        onSubmitSentence = vm::submitDailySentenceAnswer,
                        onSubmitVerb = vm::submitDailyVerbAnswer,
                        languageId = state.navigation.selectedLanguageId,
                        onShowSentenceAnswer = vm::getDailySentenceAnswer,
                        onShowVerbAnswer = vm::getDailyVerbAnswer,
                        onFlipVocabCard = { /* no-op: flip tracked locally */ },
                        onRateVocabCard = { rating -> vm.rateVocabCard(rating) },
                        onPersistVerbProgress = { card -> vm.persistDailyVerbProgress(card) },
                        onCardPracticed = { blockType -> vm.recordDailyCardPracticed(blockType) },
                        onAdvance = vm::advanceDailyTask,
                        onAdvanceBlock = { vm.advanceDailyBlock() },
                        onRepeatBlock = { vm.repeatDailyBlock() },
                        onSpeak = { text ->
                            if (state.audio.ttsModelReady) {
                                vm.onTtsSpeak(text, speed = 0.67f)
                            }
                        },
                        onStopTts = { vm.stopTts() },
                        ttsState = state.audio.ttsState,
                        onExit = {
                            vm.cancelDailySession()
                            screen = AppScreen.HOME
                        },
                        onComplete = {
                            // Only end the session (sets finishedToken=true, active=false).
                            // Do NOT navigate to HOME here — the completion screen needs to render.
                            // The completion screen's "Back to Home" button calls onExit which does the navigation.
                            vm.cancelDailySession()
                        },
                        onFlagDailyBadSentence = { cardId, langId, sentence, translation, mode ->
                            vm.flagDailyBadSentence(cardId, langId, sentence, translation, mode)
                        },
                        onUnflagDailyBadSentence = { cardId ->
                            vm.unflagDailyBadSentence(cardId)
                        },
                        isDailyBadSentence = { cardId ->
                            vm.isDailyBadSentence(cardId)
                        },
                        onExportDailyBadSentences = {
                            vm.exportDailyBadSentences()
                        },
                        hintLevel = state.cardSession.hintLevel
                    )
                }
                AppScreen.MIX_CHALLENGE -> TrainingScreen(
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
                    onStartOfflineRecognition = vm::startOfflineRecognition,
                    hintLevel = state.cardSession.hintLevel
                )
                AppScreen.STORY -> StoryQuizScreen(
                    story = state.story.activeStory,
                    testMode = state.cardSession.testMode,
                    onClose = {
                        state.story.activeStory?.phase?.let { phase ->
                            vm.completeStory(phase, false)
                        }
                        screen = AppScreen.LESSON
                    },
                    onComplete = { allCorrect ->
                        state.story.activeStory?.phase?.let { phase ->
                            vm.completeStory(phase, allCorrect)
                        }
                        screen = AppScreen.LESSON
                    }
                )
                AppScreen.LADDER -> LadderScreen(
                    state = state,
                    onBack = {
                        screen = previousScreen
                        if (previousScreen == AppScreen.TRAINING && state.cardSession.currentCard != null) {
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
                    val activePackId = state.navigation.activePackId
                    if (activePackId != null) {
                        verbDrillVm.reloadForPack(activePackId)
                    } else {
                        verbDrillVm.reloadForLanguage(state.navigation.selectedLanguageId)
                    }
                    VerbDrillScreen(
                        viewModel = verbDrillVm,
                        onBack = { screen = AppScreen.HOME },
                        hintLevel = state.cardSession.hintLevel
                    )
                }
                AppScreen.VOCAB_DRILL -> {
                    val vocabDrillVm = viewModel<VocabDrillViewModel>()
                    val packId = state.navigation.activePackId
                    if (packId != null) {
                        vocabDrillVm.reloadForPack(packId, state.navigation.selectedLanguageId)
                    } else {
                        vocabDrillVm.reloadForLanguage(state.navigation.selectedLanguageId)
                    }
                    VocabDrillScreen(
                        viewModel = vocabDrillVm,
                        onBack = {
                            vm.refreshVocabMasteryCount()
                            screen = AppScreen.HOME
                        },
                        hintLevel = state.cardSession.hintLevel
                    )
                }
            }

            // Daily practice loading overlay
            if (isLoadingDaily) {
                androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading session...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            if (screen == AppScreen.TRAINING && state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
                lastFinishedToken.value = state.cardSession.subLessonFinishedToken
                screen = AppScreen.LESSON
            }
            if (screen == AppScreen.MIX_CHALLENGE && state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
                lastFinishedToken.value = state.cardSession.subLessonFinishedToken
                screen = AppScreen.HOME
            }
            if (screen == AppScreen.TRAINING && state.boss.bossFinishedToken != lastBossFinishedToken.value) {
                lastBossFinishedToken.value = state.boss.bossFinishedToken
                screen = AppScreen.LESSON
            }

            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showExitDialog = false
                            if (screen == AppScreen.DAILY_PRACTICE) {
                                vm.cancelDailySession()
                                screen = AppScreen.HOME
                                return@TextButton
                            }
                            if (state.boss.bossActive) {
                                vm.finishBoss()
                                screen = AppScreen.LESSON
                                return@TextButton
                            }
                            if (state.drill.isDrillMode) {
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
                    title = { Text(text = if (screen == AppScreen.DAILY_PRACTICE) "Exit practice?" else "End session?") },
                    text = { Text(text = if (screen == AppScreen.DAILY_PRACTICE) "Your progress in this session will be lost." else "Current session will be completed.") }
                )
            }

            if (showDailyResumeDialog) {
                AlertDialog(
                    onDismissRequest = { showDailyResumeDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showDailyResumeDialog = false
                            isLoadingDaily = true
                            dailyScope.launch {
                                val started = withContext(Dispatchers.IO) {
                                    vm.startDailyPractice(pendingDailyLevel)
                                }
                                isLoadingDaily = false
                                if (started) screen = AppScreen.DAILY_PRACTICE
                            }
                        }) {
                            Text(text = "Продолжить")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDailyResumeDialog = false
                            isLoadingDaily = true
                            dailyScope.launch {
                                val started = withContext(Dispatchers.IO) {
                                    vm.repeatDailyPractice(pendingDailyLevel)
                                }
                                isLoadingDaily = false
                                if (started) screen = AppScreen.DAILY_PRACTICE
                            }
                        }) {
                            Text(text = "Повторить")
                        }
                    },
                    title = { Text(text = "Ежедневная практика") },
                    text = { Text(text = "Повторить — те же карточки сначала\nПродолжить — новый набор карточек") }
                )
            }

            if (state.story.storyErrorMessage != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearStoryError() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearStoryError() }) {
                            Text(text = "OK")
                        }
                    },
                    title = { Text(text = "Story") },
                    text = { Text(text = state.story.storyErrorMessage ?: "") }
                )
            }
            if (state.boss.bossErrorMessage != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearBossError() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearBossError() }) {
                            Text(text = "OK")
                        }
                    },
                    title = { Text(text = "Boss") },
                    text = { Text(text = state.boss.bossErrorMessage ?: "") }
                )
            }
            if (state.boss.bossRewardMessage != null && state.boss.bossReward != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearBossRewardMessage() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearBossRewardMessage() }) {
                            Text(text = "OK")
                        }
                    },
                    icon = {
                        val tint = when (state.boss.bossReward) {
                            BossReward.BRONZE -> Color(0xFFCD7F32)
                            BossReward.SILVER -> Color(0xFFC0C0C0)
                            BossReward.GOLD -> Color(0xFFFFD700)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = tint
                        )
                    },
                    title = { Text(text = "Boss Reward") },
                    text = { Text(text = state.boss.bossRewardMessage ?: "") }
                )
            }
            if (state.cardSession.streakMessage != null) {
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
                                text = state.cardSession.streakMessage ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            if (state.cardSession.longestStreak > state.cardSession.currentStreak) {
                                Text(
                                    text = "Longest streak: ${state.cardSession.longestStreak} days",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                )
            }
            if (state.drill.drillShowStartDialog) {
                DrillStartDialog(
                    hasProgress = state.drill.drillHasProgress,
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
    DAILY_PRACTICE,
    MIX_CHALLENGE,
    STORY,
    TRAINING,
    LADDER,
    VERB_DRILL,
    VOCAB_DRILL
}

private fun parseScreen(name: String): AppScreen {
    return try { AppScreen.valueOf(name) } catch (_: IllegalArgumentException) { AppScreen.HOME }
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
