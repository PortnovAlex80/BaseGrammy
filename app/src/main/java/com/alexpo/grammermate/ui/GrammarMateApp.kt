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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AppScreen
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.TrainingUiState
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

// ── Route constants ──────────────────────────────────────────────────────────

private object Routes {
    const val HOME = "home"
    const val LESSON = "lesson"
    const val ELITE = "elite"        // backward compat redirect
    const val VOCAB = "vocab"        // backward compat redirect
    const val DAILY_PRACTICE = "daily_practice"
    const val MIX_CHALLENGE = "mix_challenge"
    const val STORY = "story"
    const val TRAINING = "training"
    const val LADDER = "ladder"
    const val VERB_DRILL = "verb_drill"
    const val VOCAB_DRILL = "vocab_drill"
}

// ── Dialog state holder ──────────────────────────────────────────────────────

private data class DialogState(
    val showSettings: Boolean = false,
    val showExitDialog: Boolean = false,
    val showWelcomeDialog: Boolean = false,
    val showDailyResumeDialog: Boolean = false,
    val showTtsDownloadDialog: Boolean = false,
    val pendingDailyLevel: Int = 0,
    val isLoadingDaily: Boolean = false
)

// ── Main composable ──────────────────────────────────────────────────────────

@Composable
fun GrammarMateApp() {
    Surface(modifier = Modifier.fillMaxSize()) {
        val vm: TrainingViewModel = viewModel()
        val state by vm.uiState.collectAsState()
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntry?.destination?.route ?: Routes.HOME

        // Track previous screen for LADDER back navigation
        var previousRoute by remember { mutableStateOf(Routes.HOME) }
        var dialogs by remember { mutableStateOf(DialogState()) }
        val dailyScope = rememberCoroutineScope()
        val lastFinishedToken = remember { mutableStateOf(state.cardSession.subLessonFinishedToken) }
        val lastBossFinishedToken = remember { mutableStateOf(state.boss.bossFinishedToken) }

        // Map current nav route to AppScreen for legacy tracking
        val currentScreen = routeToScreen(currentRoute)

        LaunchedEffect(currentRoute) {
            vm.settings.onScreenChanged(currentScreen.name)
        }

        val onTtsSpeak: () -> Unit = {
            if (state.audio.ttsState == TtsState.SPEAKING) {
                vm.audio.stopTts()
            } else if (!state.audio.ttsModelReady) {
                val bgState = state.audio.bgTtsDownloadStates[state.navigation.selectedLanguageId.value]
                if (bgState != null && bgState !is DownloadState.Idle) {
                    vm.audio.setTtsDownloadStateFromBackground(bgState)
                }
                dialogs = dialogs.copy(showTtsDownloadDialog = true)
            } else {
                val text = state.cardSession.answerText
                    ?: state.cardSession.currentCard?.acceptedAnswers?.firstOrNull()
                if (text != null) {
                    vm.audio.onTtsSpeak(text, speed = 0.67f)
                }
            }
        }

        // Navigation helper — replaces direct onScreenChange calls
        val onNavigate: (String) -> Unit = { route ->
            if (route != currentRoute) {
                previousRoute = currentRoute
                navController.navigate(route) {
                    // Pop up to start destination to avoid building a large back stack
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Persistent TTS download progress bar
            AnimatedVisibility(visible = state.audio.bgTtsDownloading) {
                LinearProgressIndicator(
                    progress = { calcBgDownloadProgress(state.audio.bgTtsDownloadStates) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                NavBackHandlers(
                    currentRoute = currentRoute,
                    showSettings = dialogs.showSettings,
                    previousRoute = previousRoute,
                    state = state,
                    vm = vm,
                    navController = navController,
                    onShowExitDialog = { dialogs = dialogs.copy(showExitDialog = true) }
                )

                SettingsSheet(
                    show = dialogs.showSettings,
                    state = state,
                    onDismiss = {
                        dialogs = dialogs.copy(showSettings = false)
                        if (currentRoute == Routes.TRAINING && state.cardSession.currentCard != null) {
                            vm.resumeFromSettings()
                        }
                    },
                    onOpenLadder = {
                        dialogs = dialogs.copy(showSettings = false)
                        previousRoute = currentRoute
                        navController.navigate(Routes.LADDER) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
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
                    onStartAsrDownload = { vm.audio.startAsrDownload() },
                    onResetAllProgress = vm::resetLanguageProgress,
                    onSetHintLevel = vm.settings::setHintLevel,
                    onSetVoiceAutoStart = vm.audio::setVoiceAutoStart,
                    languageDisplayName = state.navigation.languages.firstOrNull { it.id == state.navigation.selectedLanguageId }?.displayName ?: state.navigation.selectedLanguageId.value
                )

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            state = state,
                            onSelectLanguage = vm::selectLanguage,
                            onOpenSettings = {
                                previousRoute = Routes.HOME
                                vm.pauseSession()
                                dialogs = dialogs.copy(showSettings = true)
                            },
                            onPrimaryAction = { onNavigate(Routes.LESSON) },
                            onSelectLesson = { lessonId ->
                                vm.selectLesson(lessonId)
                                onNavigate(Routes.LESSON)
                            },
                            onOpenElite = {
                                val level = vm.getProgressLessonLevel()
                                if (vm.daily.hasResumableDailySession()) {
                                    dialogs = dialogs.copy(showDailyResumeDialog = true, pendingDailyLevel = level)
                                } else {
                                    dialogs = dialogs.copy(isLoadingDaily = true)
                                    dailyScope.launch {
                                        val started = withContext(Dispatchers.IO) {
                                            vm.startDailyPractice(level)
                                        }
                                        dialogs = dialogs.copy(isLoadingDaily = false)
                                        if (started) onNavigate(Routes.DAILY_PRACTICE)
                                    }
                                }
                            },
                            hasVerbDrill = state.navigation.hasVerbDrill,
                            hasVocabDrill = state.navigation.hasVocabDrill,
                            onOpenVerbDrill = { onNavigate(Routes.VERB_DRILL) },
                            onOpenVocabDrill = { onNavigate(Routes.VOCAB_DRILL) }
                        )
                    }

                    composable(Routes.LESSON) {
                        LessonRoadmapScreen(
                            state = state,
                            onBack = { onNavigate(Routes.HOME) },
                            onStartSubLesson = { index ->
                                vm.selectSubLesson(index)
                                onNavigate(Routes.TRAINING)
                            },
                            onStartBossLesson = {
                                vm.startBossLesson()
                                onNavigate(Routes.TRAINING)
                            },
                            onStartBossMega = {
                                vm.startBossMega()
                                onNavigate(Routes.TRAINING)
                            },
                            onDrillStart = {
                                state.navigation.selectedLessonId?.let { vm.training.showDrillStartDialog(it.value) }
                            }
                        )
                    }

                    // Backward compat: ELITE redirects to HOME
                    composable(Routes.ELITE) {
                        LaunchedEffect(Unit) { navController.navigate(Routes.HOME) { popUpTo(Routes.ELITE) { inclusive = true } } }
                    }

                    // Backward compat: VOCAB redirects to HOME
                    composable(Routes.VOCAB) {
                        LaunchedEffect(Unit) { navController.navigate(Routes.HOME) { popUpTo(Routes.VOCAB) { inclusive = true } } }
                    }

                    composable(Routes.DAILY_PRACTICE) {
                        DailyPracticeScreenContent(state, vm) { route ->
                            onNavigate(route)
                        }
                    }

                    composable(Routes.MIX_CHALLENGE) {
                        TrainingScreenContent(state, vm, { dialogs = dialogs.copy(showExitDialog = true) }, { previousRoute = Routes.MIX_CHALLENGE; vm.pauseSession(); dialogs = dialogs.copy(showSettings = true) }, onTtsSpeak, hintLevel = state.cardSession.hintLevel)
                    }

                    composable(Routes.STORY) {
                        StoryQuizScreen(
                            story = state.story.activeStory,
                            testMode = state.cardSession.testMode,
                            onClose = {
                                state.story.activeStory?.phase?.let { phase ->
                                    vm.completeStory(phase, false)
                                }
                                onNavigate(Routes.LESSON)
                            },
                            onComplete = { allCorrect ->
                                state.story.activeStory?.phase?.let { phase ->
                                    vm.completeStory(phase, allCorrect)
                                }
                                onNavigate(Routes.LESSON)
                            }
                        )
                    }

                    composable(Routes.LADDER) {
                        LadderScreen(
                            state = state,
                            onBack = {
                                onNavigate(previousRoute)
                                if (previousRoute == Routes.TRAINING && state.cardSession.currentCard != null) {
                                    vm.resumeFromSettings()
                                }
                            }
                        )
                    }

                    composable(Routes.TRAINING) {
                        TrainingScreenContent(state, vm, { dialogs = dialogs.copy(showExitDialog = true) }, { previousRoute = Routes.TRAINING; vm.pauseSession(); dialogs = dialogs.copy(showSettings = true) }, onTtsSpeak)
                    }

                    composable(Routes.VERB_DRILL) {
                        val verbDrillVm = viewModel<VerbDrillViewModel>()
                        val activePackId = state.navigation.activePackId
                        if (activePackId != null) {
                            verbDrillVm.reloadForPack(activePackId.value)
                        } else {
                            verbDrillVm.reloadForLanguage(state.navigation.selectedLanguageId.value)
                        }
                        VerbDrillScreen(
                            viewModel = verbDrillVm,
                            onBack = { onNavigate(Routes.HOME) },
                            hintLevel = state.cardSession.hintLevel,
                            textScale = state.audio.ruTextScale,
                            voiceAutoStart = state.audio.voiceAutoStart
                        )
                    }

                    composable(Routes.VOCAB_DRILL) {
                        val vocabDrillVm = viewModel<VocabDrillViewModel>()
                        val packId = state.navigation.activePackId
                        if (packId != null) {
                            vocabDrillVm.reloadForPack(packId.value, state.navigation.selectedLanguageId.value)
                        } else {
                            vocabDrillVm.reloadForLanguage(state.navigation.selectedLanguageId.value)
                        }
                        VocabDrillScreen(
                            viewModel = vocabDrillVm,
                            onBack = {
                                vm.refreshVocabMasteryCount()
                                onNavigate(Routes.HOME)
                            },
                            hintLevel = state.cardSession.hintLevel,
                            textScale = state.audio.ruTextScale,
                            voiceAutoStart = state.audio.voiceAutoStart
                        )
                    }
                }

                NavDialogs(
                    dialogs = dialogs,
                    state = state,
                    currentRoute = currentRoute,
                    vm = vm,
                    dailyScope = dailyScope,
                    lastFinishedToken = lastFinishedToken,
                    lastBossFinishedToken = lastBossFinishedToken,
                    onDialogsChange = { dialogs = it },
                    onNavigate = onNavigate
                )
            } // Box
        } // Column
    } // Surface
} // GrammarMateApp

// ── Route-to-AppScreen mapping ───────────────────────────────────────────────

private fun routeToScreen(route: String?): AppScreen = when (route) {
    Routes.HOME -> AppScreen.HOME
    Routes.LESSON -> AppScreen.LESSON
    Routes.ELITE -> AppScreen.ELITE
    Routes.VOCAB -> AppScreen.VOCAB
    Routes.DAILY_PRACTICE -> AppScreen.DAILY_PRACTICE
    Routes.MIX_CHALLENGE -> AppScreen.MIX_CHALLENGE
    Routes.STORY -> AppScreen.STORY
    Routes.TRAINING -> AppScreen.TRAINING
    Routes.LADDER -> AppScreen.LADDER
    Routes.VERB_DRILL -> AppScreen.VERB_DRILL
    Routes.VOCAB_DRILL -> AppScreen.VOCAB_DRILL
    else -> AppScreen.HOME
}

// ── Back handlers ────────────────────────────────────────────────────────────

@Composable
private fun NavBackHandlers(
    currentRoute: String?,
    showSettings: Boolean,
    previousRoute: String,
    state: TrainingUiState,
    vm: TrainingViewModel,
    navController: androidx.navigation.NavHostController,
    onShowExitDialog: () -> Unit
) {
    BackHandler(enabled = currentRoute == Routes.TRAINING && !showSettings) {
        onShowExitDialog()
    }
    BackHandler(enabled = currentRoute == Routes.LESSON && !showSettings) {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
    BackHandler(enabled = currentRoute == Routes.DAILY_PRACTICE && !showSettings) {
        onShowExitDialog()
    }
    BackHandler(enabled = currentRoute == Routes.MIX_CHALLENGE && !showSettings) {
        onShowExitDialog()
    }
    BackHandler(enabled = currentRoute == Routes.STORY && !showSettings) {
        navController.navigate(Routes.LESSON) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
    BackHandler(enabled = currentRoute == Routes.LADDER && !showSettings) {
        navController.navigate(previousRoute) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
        if (previousRoute == Routes.TRAINING && state.cardSession.currentCard != null) {
            vm.resumeFromSettings()
        }
    }
    BackHandler(enabled = currentRoute == Routes.VERB_DRILL && !showSettings) {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
    BackHandler(enabled = currentRoute == Routes.VOCAB_DRILL && !showSettings) {
        vm.refreshVocabMasteryCount()
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
}

// ── Shared TrainingScreen helper ─────────────────────────────────────────────
// TRAINING and MIX_CHALLENGE share the same 15+ callback parameters.
// This helper avoids duplicating them.

@Composable
private fun TrainingScreenContent(
    state: TrainingUiState,
    vm: TrainingViewModel,
    onShowExitDialog: () -> Unit,
    onShowSettings: () -> Unit,
    onTtsSpeak: () -> Unit,
    hintLevel: HintLevel = HintLevel.EASY
) {
    TrainingScreen(
        state = state,
        onInputChange = vm.training::onInputChanged,
        onSubmit = vm::submitAnswer,
        onPrev = vm::prevCard,
        onNext = vm::nextCard,
        onTogglePause = vm::togglePause,
        onRequestExit = onShowExitDialog,
        onOpenSettings = { vm.pauseSession() },
        onShowSettings = onShowSettings,
        onSelectLesson = vm::selectLesson,
        onSelectMode = vm::selectMode,
        onSetInputMode = vm.training::setInputMode,
        onShowAnswer = vm::showAnswer,
        onVoicePromptStarted = vm.training::onVoicePromptStarted,
        onSelectWordFromBank = vm.training::selectWordFromBank,
        onRemoveLastWord = vm.training::removeLastSelectedWord,
        onTtsSpeak = onTtsSpeak,
        onFlagBadSentence = vm::flagBadSentence,
        onUnflagBadSentence = vm.reports::unflagBadSentence,
        onHideCard = vm::hideCurrentCard,
        onExportBadSentences = vm.reports::exportBadSentences,
        isBadSentence = vm.reports::isBadSentence,
        onStartOfflineRecognition = vm::startOfflineRecognition,
        hintLevel = hintLevel
    )
}

// ── Daily Practice screen content ────────────────────────────────────────────

@Composable
private fun DailyPracticeScreenContent(
    state: TrainingUiState,
    vm: TrainingViewModel,
    onNavigate: (String) -> Unit
) {
    val dailyState = state.daily.dailySession
    val dailyTask = vm.daily.getDailyCurrentTask()
    val dailyProgress = vm.daily.getDailyBlockProgress()
    DailyPracticeScreen(
        state = dailyState,
        blockProgress = dailyProgress,
        currentTask = dailyTask,
        onSubmitSentence = vm::submitDailySentenceAnswer,
        onSubmitVerb = vm::submitDailyVerbAnswer,
        languageId = state.navigation.selectedLanguageId.value,
        onShowSentenceAnswer = vm.daily::getDailySentenceAnswer,
        onShowVerbAnswer = vm.daily::getDailyVerbAnswer,
        onFlipVocabCard = { /* no-op: flip tracked locally */ },
        onRateVocabCard = { rating -> vm.daily.rateVocabCard(rating) },
        onPersistVerbProgress = { card -> vm.daily.persistDailyVerbProgress(card) },
        onCardPracticed = { blockType -> vm.recordDailyCardPracticed(blockType) },
        onAdvance = vm::advanceDailyTask,
        onAdvanceBlock = { vm.daily.advanceDailyBlock() },
        onRepeatBlock = { vm.repeatDailyBlock() },
        onSpeak = { text ->
            if (state.audio.ttsModelReady) {
                vm.audio.onTtsSpeak(text, speed = 0.67f)
            }
        },
        onStopTts = { vm.audio.stopTts() },
        ttsState = state.audio.ttsState,
        onExit = {
            vm.cancelDailySession()
            onNavigate(Routes.HOME)
        },
        onComplete = {
            vm.cancelDailySession()
        },
        onFlagDailyBadSentence = { cardId, langId, sentence, translation, mode ->
            vm.reports.flagDailyBadSentence(cardId, langId, sentence, translation, mode)
        },
        onUnflagDailyBadSentence = { cardId ->
            vm.reports.unflagDailyBadSentence(cardId)
        },
        isDailyBadSentence = { cardId ->
            vm.reports.isDailyBadSentence(cardId)
        },
        onExportDailyBadSentences = {
            vm.reports.exportDailyBadSentences()
        },
        hintLevel = state.cardSession.hintLevel,
        textScale = state.audio.ruTextScale,
        voiceAutoStart = state.audio.voiceAutoStart
    )
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun NavDialogs(
    dialogs: DialogState,
    state: TrainingUiState,
    currentRoute: String?,
    vm: TrainingViewModel,
    dailyScope: kotlinx.coroutines.CoroutineScope,
    lastFinishedToken: androidx.compose.runtime.MutableState<Int>,
    lastBossFinishedToken: androidx.compose.runtime.MutableState<Int>,
    onDialogsChange: (DialogState) -> Unit,
    onNavigate: (String) -> Unit
) {
    // Welcome dialog trigger
    LaunchedEffect(currentRoute, state.navigation.userName) {
        if (state.navigation.userName == "GrammarMateUser") {
            onDialogsChange(dialogs.copy(showWelcomeDialog = true))
        }
    }

    // Welcome dialog
    if (dialogs.showWelcomeDialog) {
        WelcomeDialog(
            onNameSet = { name ->
                vm.settings.updateUserName(name)
                onDialogsChange(dialogs.copy(showWelcomeDialog = false))
            }
        )
    }

    // TTS download dialog
    if (dialogs.showTtsDownloadDialog) {
        if (state.audio.ttsDownloadState is DownloadState.Done) {
            onDialogsChange(dialogs.copy(showTtsDownloadDialog = false))
            vm.audio.dismissTtsDownloadDialog()
            val text = state.cardSession.answerText ?: state.cardSession.currentCard?.acceptedAnswers?.firstOrNull()
            if (text != null) vm.audio.onTtsSpeak(text, speed = 0.67f)
        }
        if (dialogs.showTtsDownloadDialog) {
            TtsDownloadDialog(
                downloadState = state.audio.ttsDownloadState,
                languageId = state.navigation.selectedLanguageId.value,
                onConfirm = { vm.audio.startTtsDownload() },
                onDismiss = {
                    vm.audio.dismissTtsDownloadDialog()
                    onDialogsChange(dialogs.copy(showTtsDownloadDialog = false))
                }
            )
        }
    }

    // Metered network warnings
    if (state.audio.ttsMeteredNetwork) {
        MeteredNetworkDialog(
            onConfirm = { vm.audio.confirmTtsDownloadOnMetered() },
            onDismiss = {
                vm.audio.dismissMeteredWarning()
                vm.audio.dismissTtsDownloadDialog()
                onDialogsChange(dialogs.copy(showTtsDownloadDialog = false))
            }
        )
    }
    if (state.audio.asrMeteredNetwork) {
        AsrMeteredNetworkDialog(
            onConfirm = { vm.audio.confirmAsrDownloadOnMetered() },
            onDismiss = { vm.audio.dismissAsrMeteredWarning(); vm.audio.dismissAsrDownloadDialog() }
        )
    }

    // Token-based navigation: sub-lesson finished
    if (currentRoute == Routes.TRAINING && state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
        lastFinishedToken.value = state.cardSession.subLessonFinishedToken
        onNavigate(Routes.LESSON)
    }
    if (currentRoute == Routes.MIX_CHALLENGE && state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
        lastFinishedToken.value = state.cardSession.subLessonFinishedToken
        onNavigate(Routes.HOME)
    }

    // Token-based navigation: boss finished
    if (currentRoute == Routes.TRAINING && state.boss.bossFinishedToken != lastBossFinishedToken.value) {
        lastBossFinishedToken.value = state.boss.bossFinishedToken
        onNavigate(Routes.LESSON)
    }

    // Daily practice loading overlay
    if (dialogs.isLoadingDaily) {
        DailyLoadingOverlay()
    }

    // Exit confirmation dialog
    if (dialogs.showExitDialog) {
        ExitConfirmDialog(
            currentRoute = currentRoute,
            state = state,
            vm = vm,
            onDismiss = { onDialogsChange(dialogs.copy(showExitDialog = false)) },
            onNavigate = onNavigate
        )
    }

    // Daily resume dialog
    if (dialogs.showDailyResumeDialog) {
        DailyResumeDialog(
            pendingDailyLevel = dialogs.pendingDailyLevel,
            vm = vm,
            dailyScope = dailyScope,
            onDialogsChange = { onDialogsChange(dialogs.copy(showDailyResumeDialog = false, isLoadingDaily = it.isLoadingDaily)) },
            onNavigate = onNavigate
        )
    }

    // Story error dialog
    if (state.story.storyErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { vm.story.clearStoryError() },
            confirmButton = {
                TextButton(onClick = { vm.story.clearStoryError() }) {
                    Text(text = stringResource(R.string.dialog_ok))
                }
            },
            title = { Text(text = stringResource(R.string.dialog_story_title)) },
            text = { Text(text = state.story.storyErrorMessage ?: "") }
        )
    }

    // Boss error dialog
    if (state.boss.bossErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { vm.boss.clearBossError() },
            confirmButton = {
                TextButton(onClick = { vm.boss.clearBossError() }) {
                    Text(text = stringResource(R.string.dialog_ok))
                }
            },
            title = { Text(text = stringResource(R.string.dialog_boss_title)) },
            text = { Text(text = state.boss.bossErrorMessage ?: "") }
        )
    }

    // Boss reward dialog
    if (state.boss.bossRewardMessage != null && state.boss.bossReward != null) {
        AlertDialog(
            onDismissRequest = { vm.clearBossRewardMessage() },
            confirmButton = {
                TextButton(onClick = { vm.clearBossRewardMessage() }) {
                    Text(text = stringResource(R.string.dialog_ok))
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
            title = { Text(text = stringResource(R.string.dialog_boss_reward_title)) },
            text = { Text(text = state.boss.bossRewardMessage ?: "") }
        )
    }

    // Streak celebration dialog
    if (state.cardSession.streakMessage != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissStreakMessage() },
            confirmButton = {
                TextButton(onClick = { vm.dismissStreakMessage() }) {
                    Text(text = stringResource(R.string.dialog_streak_continue))
                }
            },
            icon = {
                Text(text = "??", fontSize = 48.sp)
            },
            title = { Text(text = stringResource(R.string.dialog_streak_title)) },
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
                            text = stringResource(R.string.dialog_streak_longest, state.cardSession.longestStreak),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        )
    }

    // Drill start dialog
    if (state.drill.drillShowStartDialog) {
        DrillStartDialog(
            hasProgress = state.drill.drillHasProgress,
            onStartFresh = {
                vm.startDrill(resume = false)
                onNavigate(Routes.TRAINING)
            },
            onResume = {
                vm.startDrill(resume = true)
                onNavigate(Routes.TRAINING)
            },
            onDismiss = { vm.training.dismissDrillDialog() }
        )
    }
}

// ── Individual dialog composables ────────────────────────────────────────────

@Composable
private fun DailyLoadingOverlay() {
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
                    text = stringResource(R.string.dialog_daily_loading),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ExitConfirmDialog(
    currentRoute: String?,
    state: TrainingUiState,
    vm: TrainingViewModel,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                if (currentRoute == Routes.DAILY_PRACTICE) {
                    vm.cancelDailySession()
                    onNavigate(Routes.HOME)
                    return@TextButton
                }
                if (state.boss.bossActive) {
                    vm.finishBoss()
                    onNavigate(Routes.LESSON)
                    return@TextButton
                }
                if (state.drill.isDrillMode) {
                    vm.exitDrillMode()
                    onNavigate(Routes.LESSON)
                    return@TextButton
                }
                vm.finishSession()
                onNavigate(Routes.LESSON)
            }) {
                Text(text = stringResource(R.string.dialog_exit_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_cancel))
            }
        },
        title = { Text(text = if (currentRoute == Routes.DAILY_PRACTICE) stringResource(R.string.dialog_exit_title_daily) else stringResource(R.string.dialog_exit_title_training)) },
        text = { Text(text = if (currentRoute == Routes.DAILY_PRACTICE) stringResource(R.string.dialog_exit_text_daily) else stringResource(R.string.dialog_exit_text_training)) }
    )
}

@Composable
private fun DailyResumeDialog(
    pendingDailyLevel: Int,
    vm: TrainingViewModel,
    dailyScope: kotlinx.coroutines.CoroutineScope,
    onDialogsChange: (DialogState) -> Unit,
    onNavigate: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDialogsChange(DialogState()) },
        confirmButton = {
            TextButton(onClick = {
                onDialogsChange(DialogState(isLoadingDaily = true))
                dailyScope.launch {
                    val started = withContext(Dispatchers.IO) {
                        vm.startDailyPractice(pendingDailyLevel)
                    }
                    onDialogsChange(DialogState())
                    if (started) onNavigate(Routes.DAILY_PRACTICE)
                }
            }) {
                Text(text = stringResource(R.string.dialog_daily_resume_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDialogsChange(DialogState(isLoadingDaily = true))
                dailyScope.launch {
                    val started = withContext(Dispatchers.IO) {
                        vm.repeatDailyPractice(pendingDailyLevel)
                    }
                    onDialogsChange(DialogState())
                    if (started) onNavigate(Routes.DAILY_PRACTICE)
                }
            }) {
                Text(text = stringResource(R.string.dialog_daily_resume_repeat))
            }
        },
        title = { Text(text = stringResource(R.string.dialog_daily_resume_title)) },
        text = { Text(text = stringResource(R.string.dialog_daily_resume_text)) }
    )
}

// ── Enum & helpers ───────────────────────────────────────────────────────────

@Composable
private fun WelcomeDialog(
    onNameSet: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = stringResource(R.string.dialog_welcome_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_welcome_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(50) },
                    label = { Text(stringResource(R.string.dialog_welcome_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_welcome_placeholder)) },
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
                Text(stringResource(R.string.dialog_welcome_continue))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onNameSet("GrammarMateUser")
                }
            ) {
                Text(stringResource(R.string.dialog_welcome_skip))
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
        title = { Text(stringResource(R.string.dialog_drill_title)) },
        text = { Text(if (hasProgress) stringResource(R.string.dialog_drill_resume_prompt) else stringResource(R.string.dialog_drill_start_prompt)) },
        confirmButton = {
            TextButton(onClick = if (hasProgress) onResume else onStartFresh) {
                Text(if (hasProgress) stringResource(R.string.dialog_drill_continue) else stringResource(R.string.dialog_drill_start))
            }
        },
        dismissButton = {
            if (hasProgress) {
                TextButton(onClick = onStartFresh) { Text(stringResource(R.string.dialog_drill_start_fresh)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
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
