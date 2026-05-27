package com.alexpo.grammermate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AppScreen
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.TtsState
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Imported from extracted screen files
import com.alexpo.grammermate.ui.screens.HomeScreen
import com.alexpo.grammermate.ui.screens.LessonRoadmapScreen
import com.alexpo.grammermate.ui.screens.StoryQuizScreen
import com.alexpo.grammermate.ui.screens.TrainingScreen
import com.alexpo.grammermate.ui.screens.GrammarStoryRoadmapScreen
import com.alexpo.grammermate.ui.screens.StoryReaderScreen
import com.alexpo.grammermate.ui.TenseInfo
import com.alexpo.grammermate.ui.VerbDrillViewModel
import com.alexpo.grammermate.ui.screens.SettingsSheet
import com.alexpo.grammermate.ui.screens.LadderScreen
import com.alexpo.grammermate.ui.components.TtsDownloadDialog
import com.alexpo.grammermate.ui.components.MeteredNetworkDialog
import com.alexpo.grammermate.ui.components.AsrMeteredNetworkDialog
import com.alexpo.grammermate.ui.components.ProfileStatsPopup

// ── Route constants ──────────────────────────────────────────────────────────

private object Routes {
    const val HOME = "home"
    const val LESSON = "lesson"
    const val ELITE = "elite"        // backward compat redirect
    const val VOCAB = "vocab"        // backward compat redirect
    const val DAILY_PRACTICE = "daily_practice"
    const val STORY = "story"
    const val TRAINING = "training"
    const val LADDER = "ladder"
    const val VERB_DRILL = "verb_drill"
    const val VOCAB_DRILL = "vocab_drill"
    const val GRAMMAR_STORY_ROADMAP = "grammar_story_roadmap"
    const val STORY_READER = "story_reader"
}

// ── Dialog state holder ──────────────────────────────────────────────────────

private data class DialogState(
    val showSettings: Boolean = false,
    val showExitDialog: Boolean = false,
    val showWelcomeDialog: Boolean = false,
    val showDailyResumeDialog: Boolean = false,
    val showTtsDownloadDialog: Boolean = false,
    val showProfileStats: Boolean = false,
    val pendingDailyLevel: Int = 0,
    val isLoadingDaily: Boolean = false,
    val storyReaderChapterTitle: String? = null,
    val storyReaderContent: String? = null
)

// ── Main composable ──────────────────────────────────────────────────────────

@Composable
fun GrammarMateApp(vm: TrainingViewModel = viewModel()) {
    Surface(modifier = Modifier.fillMaxSize()) {
        val state by vm.uiState.collectAsStateWithLifecycle()

        // Show loading spinner while background init (file I/O) is running
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {

        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntry?.destination?.route ?: Routes.HOME
        val context = LocalContext.current

        // Track previous screen for LADDER back navigation
        var previousRoute by remember { mutableStateOf(Routes.HOME) }
        var dialogs by remember { mutableStateOf(DialogState()) }
        val dailyScope = rememberCoroutineScope()
        val lastFinishedToken = remember { mutableStateOf(state.cardSession.subLessonFinishedToken) }
        val lastBossFinishedToken = remember { mutableStateOf(state.boss.bossFinishedToken) }

        // VerbDrillViewModel shared between TRAINING and VERB_DRILL routes for session persistence.
        // Hoisted to outer scope so TRAINING composable can call persistSessionState() on exit.
        val verbDrillVm = viewModel<VerbDrillViewModel>()
        val verbDrillActivePackId = state.navigation.activePackId
        LaunchedEffect(verbDrillActivePackId, state.navigation.selectedLanguageId) {
            if (verbDrillActivePackId != null) {
                verbDrillVm.reloadForPack(verbDrillActivePackId.value)
            } else {
                verbDrillVm.reloadForLanguage(state.navigation.selectedLanguageId.value)
            }
        }

        // Map current nav route to AppScreen for legacy tracking
        val currentScreen = routeToScreen(currentRoute)

        LaunchedEffect(currentRoute) {
            vm.settings.onScreenChanged(currentScreen.name)
        }

        // Observe Activity lifecycle to pause/resume Pomodoro timer
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> vm.onAppBackgrounded()
                    Lifecycle.Event.ON_START -> vm.onAppForegrounded()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val onTtsSpeak: () -> Unit = remember(state.audio.ttsState, state.audio.ttsModelReady, state.audio.bgTtsDownloadStates, state.navigation.selectedLanguageId, state.cardSession.answerText, state.cardSession.currentCard, dialogs) {
            {
                if (state.audio.ttsState == TtsState.Speaking) {
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
        }

        // Navigation helper — replaces direct onScreenChange calls
        val onNavigate: (String) -> Unit = remember(navController) {
            { route: String ->
                val actual = navController.currentBackStackEntry?.destination?.route
                if (route != actual) {
                    previousRoute = actual ?: Routes.HOME
                    navController.navigate(route) {
                        // Pop up to start destination to avoid building a large back stack
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            val selectedTtsDownloadState = state.audio.bgTtsDownloadStates[state.navigation.selectedLanguageId.value]
                ?: state.audio.ttsDownloadState
            // Persistent TTS download progress bar
            AnimatedVisibility(visible = state.audio.bgTtsDownloading) {
                LinearProgressIndicator(
                    progress = { calcBgDownloadProgress(state.audio.bgTtsDownloadStates) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
            TtsDownloadStatusBanner(downloadState = selectedTtsDownloadState)

            Box(modifier = Modifier.weight(1f)) {
                NavBackHandlers(
                    currentRoute = currentRoute,
                    showSettings = dialogs.showSettings,
                    previousRoute = previousRoute,
                    state = state,
                    vm = vm,
                    navController = navController,
                    onShowExitDialog = remember(dialogs) { { dialogs = dialogs.copy(showExitDialog = true) } }
                )

                SettingsSheet(
                    show = dialogs.showSettings,
                    state = state,
                    onDismiss = remember(dialogs, currentRoute, state.cardSession.currentCard) {
                        {
                            dialogs = dialogs.copy(showSettings = false)
                            if (currentRoute == Routes.TRAINING && state.cardSession.currentCard != null) {
                                vm.resumeFromSettings()
                            }
                        }
                    },
                    onOpenLadder = remember(dialogs, currentRoute) {
                        {
                            dialogs = dialogs.copy(showSettings = false)
                            previousRoute = currentRoute
                            navController.navigate(Routes.LADDER) {
                                popUpTo(Routes.HOME) { inclusive = false }
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
                    onStartAsrDownload = remember { { vm.audio.startAsrDownload() } },
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
                    clickableWordHints = vm.settings.getClickableWordHints(),
                    uiLanguage = vm.currentUiLanguage,
                    languageDisplayName = state.navigation.languages.firstOrNull { it.id == state.navigation.selectedLanguageId }?.displayName ?: state.navigation.selectedLanguageId.value
                )

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        val activePackId = state.navigation.activePackId?.value
                        val hasChapters = activePackId != null && vm.hasPackChapters(activePackId)

                        if (hasChapters) {
                            // Show Grammar Story Roadmap for packs with chapters
                            GrammarStoryRoadmapScreen(
                                chapters = vm.getChapterCards(),
                                onBack = remember {
                                    {
                                        // Clear active pack to show pack selection
                                        vm.clearActivePack()
                                        // Force recomposition by navigating to HOME
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(Routes.HOME) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onReadStory = remember { { chapter ->
                                    val storyContent = vm.loadStoryContent(chapter.storyFile)
                                    if (storyContent != null) {
                                        dialogs = dialogs.copy(
                                            storyReaderChapterTitle = chapter.title,
                                            storyReaderContent = storyContent
                                        )
                                        onNavigate(Routes.STORY_READER)
                                    } else {
                                        Toast.makeText(context, "Story not found: ${chapter.storyFile}", Toast.LENGTH_SHORT).show()
                                    }
                                } },
                                onOpenSettings = remember(dialogs) {
                                    {
                                        previousRoute = Routes.HOME
                                        vm.pauseSession()
                                        dialogs = dialogs.copy(showSettings = true)
                                    }
                                },
                                onContinue = remember { { chapter ->
                                    // Navigate to first incomplete lesson in chapter
                                    val firstIncompleteLesson = vm.getFirstIncompleteLesson(chapter)
                                    if (firstIncompleteLesson != null) {
                                        vm.selectLesson(firstIncompleteLesson)
                                        onNavigate(Routes.LESSON)
                                    }
                                } },
                                onVerbPractice = remember { { onNavigate(Routes.VERB_DRILL) } },
                                onFlashcards = remember { { onNavigate(Routes.VOCAB_DRILL) } },
                                onDailyPractice = remember(dialogs) {
                                    {
                                        val level = vm.getProgressLessonLevel()
                                        if (vm.daily.hasResumableDailySession()) {
                                            dialogs = dialogs.copy(showDailyResumeDialog = true, pendingDailyLevel = level)
                                        } else {
                                            dialogs = dialogs.copy(isLoadingDaily = true)
                                            dailyScope.launch {
                                                try {
                                                    val started = withContext(Dispatchers.IO) {
                                                        vm.startDailyPractice(level)
                                                    }
                                                    dialogs = dialogs.copy(isLoadingDaily = false)
                                                    if (started) {
                                                        onNavigate(Routes.DAILY_PRACTICE)
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    dialogs = dialogs.copy(isLoadingDaily = false)
                                                    Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                hasVerbDrill = state.navigation.hasVerbDrill,
                                hasVocabDrill = state.navigation.hasVocabDrill,
                                showBackButton = true  // Show back button to return to pack selection
                            )
                        } else {
                            // Show Classic Home Screen for v1 packs without chapters
                            HomeScreen(
                                state = state,
                                onSelectLanguage = vm::selectLanguage,
                                onSelectPack = remember { { packId: String -> vm.selectPack(packId) } },
                                onOpenSettings = remember(dialogs) {
                                    {
                                        previousRoute = Routes.HOME
                                        vm.pauseSession()
                                        dialogs = dialogs.copy(showSettings = true)
                                    }
                                },
                                onPrimaryAction = remember { { onNavigate(Routes.LESSON) } },
                                onSelectLesson = remember { { lessonId: String ->
                                    vm.selectLesson(lessonId)
                                    onNavigate(Routes.LESSON)
                                } },
                                onOpenElite = remember(dialogs) {
                                    {
                                        val level = vm.getProgressLessonLevel()
                                        if (vm.daily.hasResumableDailySession()) {
                                            dialogs = dialogs.copy(showDailyResumeDialog = true, pendingDailyLevel = level)
                                        } else {
                                            dialogs = dialogs.copy(isLoadingDaily = true)
                                            dailyScope.launch {
                                                try {
                                                    val started = withContext(Dispatchers.IO) {
                                                        vm.startDailyPractice(level)
                                                    }
                                                    dialogs = dialogs.copy(isLoadingDaily = false)
                                                    if (started) {
                                                        onNavigate(Routes.DAILY_PRACTICE)
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    dialogs = dialogs.copy(isLoadingDaily = false)
                                                    Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                hasVerbDrill = state.navigation.hasVerbDrill,
                                hasVocabDrill = state.navigation.hasVocabDrill,
                                onOpenVerbDrill = remember { { onNavigate(Routes.VERB_DRILL) } },
                                onOpenVocabDrill = remember { { onNavigate(Routes.VOCAB_DRILL) } },
                                onProfileClick = remember(dialogs) { { dialogs = dialogs.copy(showProfileStats = true) } },
                                onStartPomodoro = remember { { duration: Int ->
                                    vm.startPomodoro(duration)
                                    onNavigate(Routes.LESSON)
                                } },
                                pomodoroLastDuration = vm.getPomodoroLastDuration(),
                                pomodoroHistory = vm.getPomodoroHistoryForSelectedLanguage()
                            )
                        }
                    }

                    composable(Routes.LESSON) {
                        LessonRoadmapScreen(
                            state = state,
                            onBack = remember { { onNavigate(Routes.HOME) } },
                            onStartSubLesson = remember { { index: Int ->
                                vm.selectSubLesson(index)
                                vm.setReturnTo(Routes.LESSON)
                                onNavigate(Routes.TRAINING)
                            } },
                            onStartBossLesson = remember { {
                                vm.startBossLesson()
                                vm.setReturnTo(Routes.LESSON)
                                onNavigate(Routes.TRAINING)
                            } },
                            onStartBossMega = remember { {
                                vm.startBossMega()
                                vm.setReturnTo(Routes.LESSON)
                                onNavigate(Routes.TRAINING)
                            } },
                            onReview = remember { { hintLevel: HintLevel ->
                                vm.startReview(hintLevel)
                                vm.setReturnTo(Routes.LESSON)
                                onNavigate(Routes.TRAINING)
                            } },
                            onNextLesson = remember(state.navigation.lessons, state.navigation.selectedLessonId) {
                                {
                                    val currentIdx = state.navigation.lessons.indexOfFirst { it.id == state.navigation.selectedLessonId }
                                    val nextLessonId = state.navigation.lessons.getOrNull(currentIdx + 1)?.id
                                    if (nextLessonId != null) {
                                        vm.selectLesson(nextLessonId.value)
                                        onNavigate(Routes.LESSON)
                                    }
                                }
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
                        DailyPracticeScreenContent(state, vm, remember { { route: String -> onNavigate(route) } })
                    }

                    composable(Routes.STORY) {
                        StoryQuizScreen(
                            story = state.story.activeStory,
                            testMode = state.cardSession.testMode,
                            onClose = remember(state.story.activeStory?.phase) {
                                {
                                    state.story.activeStory?.phase?.let { phase ->
                                        vm.completeStory(phase, false)
                                    }
                                    onNavigate(Routes.LESSON)
                                }
                            },
                            onComplete = remember(state.story.activeStory?.phase) {
                                { allCorrect: Boolean ->
                                    state.story.activeStory?.phase?.let { phase ->
                                        vm.completeStory(phase, allCorrect)
                                    }
                                    onNavigate(Routes.LESSON)
                                }
                            }
                        )
                    }

                    composable(Routes.LADDER) {
                        LadderScreen(
                            state = state,
                            onBack = remember(previousRoute, state.cardSession.currentCard) {
                                {
                                    onNavigate(previousRoute)
                                    if (previousRoute == Routes.TRAINING && state.cardSession.currentCard != null) {
                                        vm.resumeFromSettings()
                                    }
                                }
                            }
                        )
                    }

                    composable(Routes.TRAINING) {
                        // Create VerbDrillViewModel scoped to this route for tense info bottom sheet
                        val verbTenseInfoVm = viewModel<VerbDrillViewModel>()
                        val activePackIdForTenses = state.navigation.activePackId
                        LaunchedEffect(activePackIdForTenses, state.navigation.selectedLanguageId) {
                            if (activePackIdForTenses != null) {
                                verbTenseInfoVm.reloadForPack(activePackIdForTenses.value)
                            } else {
                                verbTenseInfoVm.reloadForLanguage(state.navigation.selectedLanguageId.value)
                            }
                        }
                        TrainingScreenContent(
                            state, vm,
                            onSubmit = {
                                val beforeCard = state.cardSession.currentCard
                                val result = vm.submitAnswer()
                                if (
                                    state.cardSession.returnTo == Routes.VERB_DRILL &&
                                    beforeCard is VerbDrillCard &&
                                    result.accepted
                                ) {
                                    verbDrillVm.submitCorrectAnswer()
                                }
                                result
                            },
                            onNext = {
                                if (
                                    state.cardSession.returnTo == Routes.VERB_DRILL &&
                                    state.cardSession.currentCard is VerbDrillCard &&
                                    (state.cardSession.lastResult == false || state.cardSession.answerText != null)
                                ) {
                                    verbDrillVm.markCardCompleted()
                                }
                                vm.navigateNext()
                            },
                            onPrev = {
                                if (
                                    state.cardSession.returnTo == Routes.VERB_DRILL &&
                                    state.cardSession.currentIndex > 0
                                ) {
                                    verbDrillVm.prevCard()
                                }
                                vm.navigatePrev()
                            },
                            onShowExitDialog = remember(dialogs, state.cardSession.returnTo, state.cardSession.currentCard) {
                                {
                                    val returnTo = state.cardSession.returnTo
                                    val hasActiveCard = state.cardSession.currentCard != null
                                    when {
                                        // VERB_DRILL with active card (exiting mid-session) → save state, return to selection screen
                                        returnTo == Routes.VERB_DRILL && hasActiveCard -> {
                                            verbDrillVm.persistSessionState()
                                            vm.exitVerbDrillSession()
                                            onNavigate(Routes.VERB_DRILL)
                                        }
                                        // VERB_DRILL with NO active card (block completed) → save state, go to HOME
                                        returnTo == Routes.VERB_DRILL && !hasActiveCard -> {
                                            verbDrillVm.persistSessionState()
                                            vm.exitVerbDrillSession()
                                            onNavigate(Routes.HOME)
                                        }
                                        returnTo == Routes.DAILY_PRACTICE -> {
                                            vm.cancelDailySession()
                                            onNavigate(Routes.HOME)
                                        }
                                        else -> {
                                            dialogs = dialogs.copy(showExitDialog = true)
                                        }
                                    }
                                }
                            },
                            onShowSettings = remember(dialogs) { { previousRoute = Routes.TRAINING; vm.pauseSession(); dialogs = dialogs.copy(showSettings = true) } },
                            onTtsSpeak = onTtsSpeak,
                            onVerbDrillMore = remember { {
                                verbDrillVm.persistSessionState()
                                verbDrillVm.nextBatch()
                                val nextCards = verbDrillVm.uiState.value.session?.cards ?: emptyList()
                                if (nextCards.isNotEmpty()) {
                                    vm.replaceVerbDrillCards(nextCards)
                                } else {
                                    // No more cards — go to selection screen
                                    vm.exitVerbDrillSession()
                                    onNavigate(Routes.VERB_DRILL)
                                }
                            } },
                            onNavigate = onNavigate,
                            onSessionDone = remember(state.cardSession.returnTo) {
                                {
                                    val returnTo = state.cardSession.returnTo
                                    when {
                                        returnTo == Routes.DAILY_PRACTICE -> {
                                            vm.daily.onBlockComplete()
                                            onNavigate(Routes.DAILY_PRACTICE)
                                        }
                                        else -> onNavigate(Routes.HOME)
                                    }
                                }
                            },
                            getTenseInfo = remember { { tenseName: String -> verbTenseInfoVm.getTenseInfo(tenseName) } }
                        )

                        // Local back handler for VERB_DRILL return path
                        BackHandler(enabled = state.cardSession.returnTo == Routes.VERB_DRILL && !dialogs.showSettings) {
                            verbDrillVm.persistSessionState()
                            vm.exitVerbDrillSession()
                            onNavigate(Routes.VERB_DRILL)
                        }
                    }

                    composable(Routes.VERB_DRILL) {
                        val verbDrillExit = remember(verbDrillVm) {
                            {
                                verbDrillVm.exitSession()
                                vm.refreshStreakFromStore()
                                onNavigate(Routes.HOME)
                            }
                        }
                        BackHandler { verbDrillExit() }
                        VerbDrillScreen(
                            viewModel = verbDrillVm,
                            onBack = verbDrillExit,
                            onStartSession = remember { { cards: List<VerbDrillCard> ->
                                vm.startVerbDrillSession(cards)
                                vm.setReturnTo(Routes.VERB_DRILL)
                                onNavigate(Routes.TRAINING)
                            } }
                        )
                    }

                    composable(Routes.VOCAB_DRILL) {
                        val vocabDrillVm = viewModel<VocabDrillViewModel>()
                        val packId = state.navigation.activePackId
                        LaunchedEffect(packId, state.navigation.selectedLanguageId) {
                            if (packId != null) {
                                vocabDrillVm.reloadForPack(packId.value, state.navigation.selectedLanguageId.value)
                            } else {
                                vocabDrillVm.reloadForLanguage(state.navigation.selectedLanguageId.value)
                            }
                        }
                        val vocabExit = remember(vocabDrillVm) {
                            {
                                if (vocabDrillVm.hasRatedCards) {
                                    vm.refreshVocabMasteryCount()
                                }
                                vm.refreshStreakFromStore()
                                onNavigate(Routes.HOME)
                            }
                        }
                        BackHandler { vocabExit() }
                        VocabDrillScreen(
                            viewModel = vocabDrillVm,
                            onBack = vocabExit,
                            hintLevel = state.cardSession.hintLevel,
                            textScale = state.audio.ruTextScale,
                            voiceAutoStart = state.audio.voiceAutoStart
                        )
                    }

                    composable(Routes.GRAMMAR_STORY_ROADMAP) {
                        GrammarStoryRoadmapScreen(
                            chapters = vm.getChapterCards(),
                            onBack = remember { { onNavigate(Routes.HOME) } },
                            showBackButton = true,  // Show back button when accessed via direct route
                            onReadStory = remember { { chapter ->
                                val storyContent = vm.loadStoryContent(chapter.storyFile)
                                if (storyContent != null) {
                                    dialogs = dialogs.copy(
                                        storyReaderChapterTitle = chapter.title,
                                        storyReaderContent = storyContent
                                    )
                                    onNavigate(Routes.STORY_READER)
                                } else {
                                    Toast.makeText(context, "Story not found: ${chapter.storyFile}", Toast.LENGTH_SHORT).show()
                                }
                            } },
                            onOpenSettings = remember(dialogs) {
                                {
                                    previousRoute = Routes.GRAMMAR_STORY_ROADMAP
                                    vm.pauseSession()
                                    dialogs = dialogs.copy(showSettings = true)
                                }
                            },
                            onContinue = remember { { chapter ->
                                val firstIncompleteLesson = vm.getFirstIncompleteLesson(chapter)
                                if (firstIncompleteLesson != null) {
                                    vm.selectLesson(firstIncompleteLesson)
                                    onNavigate(Routes.LESSON)
                                }
                            } },
                            onVerbPractice = remember { { onNavigate(Routes.VERB_DRILL) } },
                            onFlashcards = remember { { onNavigate(Routes.VOCAB_DRILL) } },
                            onDailyPractice = remember(dialogs) {
                                {
                                    val level = vm.getProgressLessonLevel()
                                    if (vm.daily.hasResumableDailySession()) {
                                        dialogs = dialogs.copy(showDailyResumeDialog = true, pendingDailyLevel = level)
                                    } else {
                                        dialogs = dialogs.copy(isLoadingDaily = true)
                                        dailyScope.launch {
                                            try {
                                                val started = withContext(Dispatchers.IO) {
                                                    vm.startDailyPractice(level)
                                                }
                                                dialogs = dialogs.copy(isLoadingDaily = false)
                                                if (started) {
                                                    onNavigate(Routes.DAILY_PRACTICE)
                                                } else {
                                                    Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                dialogs = dialogs.copy(isLoadingDaily = false)
                                                Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            },
                            hasVerbDrill = state.navigation.hasVerbDrill,
                            hasVocabDrill = state.navigation.hasVocabDrill
                        )
                    }

                    composable(Routes.STORY_READER) {
                        val chapterTitle = dialogs.storyReaderChapterTitle ?: "Unknown Chapter"
                        val content = dialogs.storyReaderContent ?: ""

                        StoryReaderScreen(
                            chapterTitle = chapterTitle,
                            markdownContent = content,
                            onBack = remember {
                                {
                                    dialogs = dialogs.copy(
                                        storyReaderChapterTitle = null,
                                        storyReaderContent = null
                                    )
                                    onNavigate(Routes.GRAMMAR_STORY_ROADMAP)
                                }
                            }
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
                    onDialogsChange = remember { { dialogs = it } },
                    onNavigate = onNavigate
                )
            } // Box
        } // Column
        } // else (not loading)
    } // Surface
} // GrammarMateApp

// ── Route-to-AppScreen mapping ───────────────────────────────────────────────

private fun routeToScreen(route: String?): AppScreen = when (route) {
    Routes.HOME -> AppScreen.HOME
    Routes.LESSON -> AppScreen.LESSON
    Routes.ELITE -> AppScreen.ELITE
    Routes.VOCAB -> AppScreen.VOCAB
    Routes.DAILY_PRACTICE -> AppScreen.DAILY_PRACTICE
    Routes.STORY -> AppScreen.STORY
    Routes.TRAINING -> AppScreen.TRAINING
    Routes.LADDER -> AppScreen.LADDER
    Routes.VERB_DRILL -> AppScreen.VERB_DRILL
    Routes.VOCAB_DRILL -> AppScreen.VOCAB_DRILL
    Routes.GRAMMAR_STORY_ROADMAP -> AppScreen.HOME // Treat as home for tracking
    Routes.STORY_READER -> AppScreen.STORY // Treat as story for tracking
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
    BackHandler(enabled = currentRoute == Routes.TRAINING && state.cardSession.returnTo == Routes.DAILY_PRACTICE && !showSettings) {
        vm.cancelDailySession()
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
    // GRAMMAR_STORY_ROADMAP is shown on HOME route when pack has chapters
    // Back handler clears the active pack to return to pack selection
    BackHandler(enabled = currentRoute == Routes.HOME && state.navigation.activePackId != null && vm.hasPackChapters(state.navigation.activePackId.value) && !showSettings) {
        // Clear active pack to show pack selection
        vm.clearActivePack()
        // Force recomposition by navigating to HOME
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = true }
            launchSingleTop = true
        }
    }
    BackHandler(enabled = currentRoute == Routes.STORY_READER && !showSettings) {
        navController.navigate(Routes.GRAMMAR_STORY_ROADMAP) {
            popUpTo(Routes.GRAMMAR_STORY_ROADMAP) { inclusive = false }
            launchSingleTop = true
        }
    }
}

// ── Shared TrainingScreen helper ─────────────────────────────────────────────
// This helper avoids duplicating callback parameters.

@Composable
private fun TrainingScreenContent(
    state: TrainingUiState,
    vm: TrainingViewModel,
    onSubmit: () -> com.alexpo.grammermate.data.SubmitResult = vm::submitAnswer,
    onNext: () -> Unit = vm::navigateNext,
    onPrev: () -> Unit = vm::navigatePrev,
    onShowExitDialog: () -> Unit,
    onShowSettings: () -> Unit,
    onTtsSpeak: () -> Unit,
    hintLevel: HintLevel = HintLevel.EASY,
    onVerbDrillMore: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onSessionDone: () -> Unit = {},
    getTenseInfo: (String) -> TenseInfo? = { null }
) {
    val pomodoroRemainingSeconds by vm.pomodoroRemainingSeconds.collectAsStateWithLifecycle()
    TrainingScreen(
        state = state,
        onInputChange = vm.training::onInputChanged,
        onSubmit = onSubmit,
        onPrev = onPrev,
        onNext = onNext,
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
        hintLevel = hintLevel,
        onPausePomodoro = vm::pausePomodoro,
        onResumePomodoro = vm::resumePomodoro,
        onCancelPomodoro = remember(onNavigate) {
            vm::cancelPomodoro
        },
        onRateCardDifficulty = vm::rateCardDifficulty,
        onVerbDrillMore = onVerbDrillMore,
        onSessionDone = onSessionDone,
        getTenseInfo = getTenseInfo,
        pomodoroRemainingSeconds = pomodoroRemainingSeconds,
        clickableWordHints = vm.settings.getClickableWordHints(),
        baseDir = LocalContext.current.filesDir,
        grammarChip = null // TODO: Load from GrammarChipStore based on lesson ID
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
    val currentBlock = vm.daily.getCurrentBlock()
    val dailyTask = vm.daily.getDailyCurrentTask()
    val dailyProgress = vm.daily.getDailyBlockProgress()
    DailyPracticeScreen(
        state = dailyState,
        blockProgress = dailyProgress,
        currentBlock = currentBlock,
        currentTask = dailyTask,
        languageId = state.navigation.selectedLanguageId.value,
        onShowSentenceAnswer = vm.daily::getDailySentenceAnswer,
        onShowVerbAnswer = vm.daily::getDailyVerbAnswer,
        onRateVocabCard = remember { { rating: com.alexpo.grammermate.data.SrsRating -> vm.daily.rateVocabCard(rating) } },
        onStartCardBlock = remember(onNavigate) { { blockType: DailyBlockType, cards: List<com.alexpo.grammermate.data.SessionCard> ->
            when (blockType) {
                DailyBlockType.TRANSLATE -> vm.startDailyTranslateSession(cards)
                DailyBlockType.VERBS -> vm.startDailyVerbsSession(cards)
                else -> {}
            }
            vm.setReturnTo(Routes.DAILY_PRACTICE)
            onNavigate(Routes.TRAINING)
        } },
        onSpeak = remember(state.audio.ttsModelReady) { { text: String ->
            if (state.audio.ttsModelReady) {
                vm.audio.onTtsSpeak(text, speed = 0.67f)
            }
        } },
        onStopTts = remember { { vm.audio.stopTts() } },
        ttsState = state.audio.ttsState,
        onExit = remember(onNavigate) { {
            vm.cancelDailySession()
            onNavigate(Routes.HOME)
        } },
        onComplete = remember { {
            // VOCAB block completed — signal coordinator to advance to next block
            val nextBlock = vm.daily.onBlockComplete()
            if (nextBlock == null) {
                // All blocks done — coordinator already called endSession()
                // The finishedToken will be set and DailyPracticeScreen shows completion
            }
            // If nextBlock is non-null, the UI will re-render with the new block
        } },
        onFlagDailyBadSentence = remember { { cardId: String, langId: String, sentence: String, translation: String, mode: String ->
            vm.reports.flagDailyBadSentence(cardId, langId, sentence, translation, mode)
        } },
        onUnflagDailyBadSentence = remember { { cardId: String ->
            vm.reports.unflagDailyBadSentence(cardId)
        } },
        isDailyBadSentence = remember { { cardId: String ->
            vm.reports.isDailyBadSentence(cardId)
        } },
        onExportDailyBadSentences = remember { {
            vm.reports.exportDailyBadSentences()
        } },
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
    // Welcome dialog trigger — only on HOME screen, only if attempts < 3.
    // Guard: languages must be non-empty to ensure init block has loaded the real
    // profile from disk. Without this, the combined uiState flow may still carry
    // the default TrainingUiState (userName="GrammarMateUser", attempts=0) on the
    // first composition frame, causing a false-positive dialog trigger that
    // overwrites the name the user previously saved.
    LaunchedEffect(currentRoute, state.navigation.userName, state.navigation.languages.size) {
        if (currentRoute == Routes.HOME
            && state.navigation.userName == "GrammarMateUser"
            && state.navigation.welcomeDialogAttempts < 3
            && state.navigation.languages.isNotEmpty()
        ) {
            onDialogsChange(dialogs.copy(showWelcomeDialog = true))
        }
    }

    // Welcome dialog
    if (dialogs.showWelcomeDialog) {
        WelcomeDialog(
            onNameSet = { name ->
                if (name == "GrammarMateUser") {
                    vm.settings.incrementWelcomeDialogAttempts()
                } else {
                    vm.settings.updateUserName(name)
                }
                onDialogsChange(dialogs.copy(showWelcomeDialog = false))
            }
        )
    }

    // Profile stats popup
    if (dialogs.showProfileStats) {
        val profileStats = remember { vm.getProfileStats() }
        ProfileStatsPopup(
            userName = state.navigation.userName,
            cardsCompleted = profileStats.cardsCompleted,
            wordsLearned = profileStats.wordsLearned,
            cefrLevel = profileStats.cefrLevel,
            onDismiss = { onDialogsChange(dialogs.copy(showProfileStats = false)) }
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

    // Sync lastFinishedToken when token is reset (new session started)
    if (state.cardSession.subLessonFinishedToken < lastFinishedToken.value) {
        lastFinishedToken.value = state.cardSession.subLessonFinishedToken
    }

    // Token-based navigation: sub-lesson finished — unified via returnTo
    if (currentRoute == Routes.TRAINING && state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
        lastFinishedToken.value = state.cardSession.subLessonFinishedToken
        // Pomodoro: track session completion for timer stats
        vm.onTrainingSessionCompleted()
        val hasCards = state.cardSession.currentCard != null
        // If pomodoro completed OR no cards left (session finished), stay on TrainingScreen for summary/completion
        if (!state.pomodoro.isComplete && hasCards) {
            val returnTo = state.cardSession.returnTo
            if (returnTo.isNotEmpty()) {
                if (returnTo == Routes.DAILY_PRACTICE) {
                    vm.daily.onBlockComplete()
                }
                onNavigate(returnTo)
            } else {
                // Default: sub-lesson from LESSON screen — more sub-lessons available
                onNavigate(Routes.LESSON)
            }
        }
        // If !hasCards: SessionCompletionContent will render via TrainingScreen early-return
        // User presses OK → onSessionDone → navigate HOME or DAILY_PRACTICE
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
    val isPomodoroActive = state.pomodoro.isActive
    val dialogTitle = when {
        isPomodoroActive -> stringResource(R.string.dialog_exit_title_pomodoro)
        currentRoute == Routes.DAILY_PRACTICE -> stringResource(R.string.dialog_exit_title_daily)
        else -> stringResource(R.string.dialog_exit_title_training)
    }
    val dialogText = when {
        isPomodoroActive -> stringResource(R.string.dialog_exit_text_pomodoro)
        currentRoute == Routes.DAILY_PRACTICE -> stringResource(R.string.dialog_exit_text_daily)
        else -> stringResource(R.string.dialog_exit_text_training)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                if (isPomodoroActive) {
                    vm.confirmPomodoroExit()
                    onNavigate(Routes.HOME)
                    return@TextButton
                }
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
        title = { Text(text = dialogTitle) },
        text = { Text(text = dialogText) }
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
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { onDialogsChange(DialogState()) },
        confirmButton = {
            TextButton(onClick = {
                onDialogsChange(DialogState(isLoadingDaily = true))
                dailyScope.launch {
                    try {
                        val started = withContext(Dispatchers.IO) {
                            vm.startDailyPractice(pendingDailyLevel)
                        }
                        onDialogsChange(DialogState())
                        if (started) onNavigate(Routes.DAILY_PRACTICE)
                    } catch (e: Exception) {
                        onDialogsChange(DialogState())
                        Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text(text = stringResource(R.string.dialog_daily_resume_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDialogsChange(DialogState(isLoadingDaily = true))
                dailyScope.launch {
                    try {
                        val started = withContext(Dispatchers.IO) {
                            vm.repeatDailyPractice(pendingDailyLevel)
                        }
                        onDialogsChange(DialogState())
                        if (started) onNavigate(Routes.DAILY_PRACTICE)
                    } catch (e: Exception) {
                        onDialogsChange(DialogState())
                        Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
                    }
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


private fun calcBgDownloadProgress(states: Map<String, DownloadState>): Float {
    if (states.isEmpty()) return 0f
    var total = 0f
    for (s in states.values) {
        total += when (s) {
            is DownloadState.Downloading -> s.percent / 100f * 0.3f
            is DownloadState.Extracting -> 0.3f + s.percent / 100f * 0.4f
            is DownloadState.Initializing -> 0.7f + s.percent / 100f * 0.3f
            is DownloadState.Done -> 1f
            else -> 0f
        }
    }
    return (total / states.size).coerceIn(0f, 1f)
}

@Composable
private fun TtsDownloadStatusBanner(downloadState: DownloadState) {
    val visible = downloadState is DownloadState.Downloading ||
        downloadState is DownloadState.Extracting ||
        downloadState is DownloadState.Initializing ||
        downloadState is DownloadState.Error
    AnimatedVisibility(visible = visible) {
        val progress = when (downloadState) {
            is DownloadState.Downloading -> downloadState.percent / 100f
            is DownloadState.Extracting -> downloadState.percent / 100f
            is DownloadState.Initializing -> downloadState.percent / 100f
            else -> 0f
        }.coerceIn(0f, 1f)
        val text = when (downloadState) {
            is DownloadState.Downloading -> "Voice model downloading ${downloadState.percent}%"
            is DownloadState.Extracting -> "Voice model extracting ${downloadState.percent}%"
            is DownloadState.Initializing -> "Voice engine starting ${downloadState.percent}%"
            is DownloadState.Error -> "Voice model error: ${downloadState.message}"
            else -> ""
        }
        Surface(
            color = if (downloadState is DownloadState.Error) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (downloadState is DownloadState.Error) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (downloadState !is DownloadState.Error) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.width(18.dp).height(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = text, style = MaterialTheme.typography.bodySmall)
                }
                if (downloadState !is DownloadState.Error) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
