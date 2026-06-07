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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AppScreen
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.CompletionNextAction
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.GrammarChipStore
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.alexpo.grammermate.ui.screens.ChapterLessonsScreen
import com.alexpo.grammermate.ui.screens.StoryReaderScreen
import com.alexpo.grammermate.ui.TenseInfo
import com.alexpo.grammermate.ui.VerbDrillViewModel
import com.alexpo.grammermate.ui.screens.SettingsSheet
import com.alexpo.grammermate.ui.screens.LadderScreen
import com.alexpo.grammermate.ui.components.TtsDownloadDialog
import com.alexpo.grammermate.ui.components.MeteredNetworkDialog
import com.alexpo.grammermate.ui.components.AsrMeteredNetworkDialog
import com.alexpo.grammermate.ui.components.ProfileStatsPopup
import android.util.Log
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.ScreenLogger

// ── Route constants ──────────────────────────────────────────────────────────

private object Routes {
    const val HOME = "home"
    const val LESSON = "lesson"
    const val CHAPTER_LESSONS = "chapter_lessons"
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
    val isLoadingDaily: Boolean = false
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
        // Use currentBackStackEntryAsState() for proper Compose state observation.
        // Direct property access does NOT trigger recomposition on navigate/popBackStack.
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: Routes.HOME
        val context = LocalContext.current

        // Track previous screen for LADDER back navigation
        var previousRoute by remember { mutableStateOf(Routes.HOME) }

        // Story reader state managed in ViewModel (state.storyReaderChapterTitle, state.storyReaderContent)
        var dialogs by remember { mutableStateOf(DialogState()) }
        val dailyScope = rememberCoroutineScope()
        val lastFinishedToken = remember { mutableStateOf(state.cardSession.subLessonFinishedToken) }
        val lastBossFinishedToken = remember { mutableStateOf(state.boss.bossFinishedToken) }
        val completionNextAction = remember { mutableStateOf(CompletionNextAction.NONE) }

        // VerbDrillViewModel shared between TRAINING and VERB_DRILL routes for session persistence.
        // Hoisted to outer scope so TRAINING composable can call persistSessionState() on exit.
        val verbDrillVm = viewModel<VerbDrillViewModel>()
        val verbDrillActivePackId = state.navigation.activePackId
        LaunchedEffect(verbDrillActivePackId, state.navigation.selectedLanguageId) {
            if (verbDrillActivePackId != null) {
                verbDrillVm.reloadForPack(verbDrillActivePackId.value)
            } else {
                state.navigation.selectedLanguageId?.let { verbDrillVm.reloadForLanguage(it.value) }
            }
        }

        // Map current nav route to AppScreen for legacy tracking
        val currentScreen = routeToScreen(currentRoute)

        LaunchedEffect(currentRoute) {
            vm.settings.onScreenChanged(currentScreen.name)
        }

        LaunchedEffect(currentRoute) {
            Log.d("NavDebug", "ROUTE_CHANGED: currentRoute=$currentRoute, activePackId=${state.navigation.activePackId?.value}")
            val backStackEntry = navController.currentBackStackEntry
            ScreenLogger.nav(backStackEntry?.destination?.route ?: "?", currentRoute, trigger = "route_changed")
            ScreenLogger.screenShown(currentRoute.uppercase())
            AuditLogger.getInstanceOrNull()?.screenOpen(
                from = backStackEntry?.destination?.route ?: "?",
                to = currentRoute,
                trigger = "route_changed"
            )
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
                    val bgState = state.navigation.selectedLanguageId?.let { state.audio.bgTtsDownloadStates[it.value] }
                    if (bgState != null && bgState !is DownloadState.Idle) {
                        vm.audio.setTtsDownloadStateFromBackground(bgState)
                    }
                    dialogs = dialogs.copy(showTtsDownloadDialog = true)
                } else {
                    val text = state.cardSession.answerText
                        ?: state.cardSession.currentCard?.acceptedAnswers?.firstOrNull()
                    if (text != null) {
                        vm.audio.onTtsSpeak(text)
                    }
                }
            }
        }

        // Navigation helper — replaces direct onScreenChange calls
        val onNavigate: (String) -> Unit = remember(navController) {
            { route: String ->
                Log.d("NavDebug", "NAVIGATE: from=${navController.currentBackStackEntry?.destination?.route} to=$route")
                val actual = navController.currentBackStackEntry?.destination?.route
                ScreenLogger.nav(actual ?: "?", route, trigger = "navigate")
                AuditLogger.getInstanceOrNull()?.screenOpen(
                    from = actual ?: "?",
                    to = route,
                    trigger = "navigate"
                )
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
            val selectedTtsDownloadState = state.navigation.selectedLanguageId?.let { state.audio.bgTtsDownloadStates[it.value] }
                ?: state.audio.ttsDownloadState
            // Persistent TTS download progress bar — hidden during story playback
            AnimatedVisibility(visible = state.audio.bgTtsDownloading && !state.audio.isStoryPlaybackActive) {
                LinearProgressIndicator(
                    progress = { calcBgDownloadProgress(state.audio.bgTtsDownloadStates) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
            if (!state.audio.isStoryPlaybackActive) {
                TtsDownloadStatusBanner(downloadState = selectedTtsDownloadState)
            }

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
                    show = dialogs.showSettings,
                    state = state,
                    onDismiss = remember(dialogs, currentRoute, state.cardSession.currentCard) {
                        {
                            ScreenLogger.overlay("settings", shown = false)
                            dialogs = dialogs.copy(showSettings = false)
                            if (currentRoute == Routes.TRAINING && state.cardSession.currentCard != null) {
                                vm.resumeFromSettings()
                            }
                        }
                    },
                    onOpenLadder = remember(dialogs, currentRoute) {
                        {
                            ScreenLogger.overlay("settings", shown = false)
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
                    languageDisplayName = state.navigation.languages.firstOrNull { it.id == state.navigation.selectedLanguageId }?.displayName ?: state.navigation.selectedLanguageId?.value ?: ""
                )

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        LaunchedEffect(Unit) {
                            ScreenLogger.screenShown("HOME")
                        }
                        val activePackId = state.navigation.activePackId?.value
                        val hasChapters = activePackId != null && vm.hasPackChapters(activePackId)

                        if (hasChapters) {
                            // Rebuild chapter cards when pack changes OR chapter progress updates.
                            // Lightweight string key — only changes when actual progress values change,
                            // not on every _coreState.update (timer, etc).
                            val progressKey = state.chapterProgresses.values
                                .sortedBy { it.chapterId }
                                .joinToString(",") { "${it.chapterId}:${it.lessonsCompleted}/${it.lessonsStarted}" }
                            val chapterCards = remember(activePackId, progressKey) { vm.getChapterCards() }
                            GrammarStoryRoadmapScreen(
                                chapters = chapterCards,
                                onBack = remember {
                                    {
                                        // Clear active pack to show pack selection.
                                        // State change triggers recomposition - no navigation needed
                                        // since we are already on the HOME route.
                                        vm.clearActivePack()
                                    }
                                },
                                onReadStory = remember { { chapter ->
                                    val storyContent = vm.loadStoryContent(chapter.storyFile)
                                    if (storyContent != null) {
                                        vm.setStoryReader(chapter.title, storyContent)
                                        onNavigate(Routes.STORY_READER)
                                    } else {
                                        Toast.makeText(context, "Story not found: ${chapter.storyFile}", Toast.LENGTH_SHORT).show()
                                    }
                                } },
                                onPlayChapterStory = remember { { chapter ->
                                    // Quick play from roadmap - load story and play immediately
                                    val storyContent = vm.loadStoryContent(chapter.storyFile)
                                    if (storyContent != null && storyContent.isNotBlank()) {
                                        // Use multilingual TTS with Italian markers {it}...{/it}
                                        // ALLEGORY_PACK stories are in Russian with Italian insertions
                                        vm.speakMultilingualStory(storyContent, defaultLanguageId = "ru")
                                    } else {
                                        Toast.makeText(context, "Story not found: ${chapter.storyFile}", Toast.LENGTH_SHORT).show()
                                    }
                                } },
                                onOpenSettings = remember(dialogs) {
                                    {
                                        previousRoute = Routes.HOME
                                        vm.pauseSession()
                                        ScreenLogger.overlay("settings", shown = true)
                                        dialogs = dialogs.copy(showSettings = true)
                                    }
                                },
                                onContinue = remember { { chapter ->
                                    // Navigate to chapter lessons screen
                                    vm.selectChapter(chapter)
                                    onNavigate(Routes.CHAPTER_LESSONS)
                                } },
                                onVerbPractice = remember { { onNavigate(Routes.VERB_DRILL) } },
                                onFlashcards = remember { { onNavigate(Routes.VOCAB_DRILL) } },
                                onDailyPractice = remember(dialogs) {
                                    {
                                        val level = vm.getProgressLessonLevel()
                                        Log.d("GrammarMate", "DailyPractice: user clicked daily practice, level=$level")
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
                                showBackButton = true,  // Show back button to return to pack selection
                                isStoryPlaying = state.audio.isStoryPlaybackActive || state.audio.ttsState == TtsState.Speaking,
                                isStoryPaused = state.audio.isStoryPlaybackPaused,
                                onStopStory = remember { { vm.stopStoryNarration() } },
                                onPauseStory = remember { { vm.pauseStoryPlayback() } },
                                onResumeStory = remember { { vm.resumeStoryPlayback() } }
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
                                        ScreenLogger.overlay("settings", shown = true)
                                        dialogs = dialogs.copy(showSettings = true)
                                    }
                                },
                                onPrimaryAction = remember { { onNavigate(Routes.LESSON) } },
                                onSelectLesson = remember { { lessonId: String ->
                                    val currentPackId = state.navigation.activePackId?.value
                                    vm.selectLesson(lessonId, currentPackId)
                                    onNavigate(Routes.LESSON)
                                } },
                                onOpenElite = remember(dialogs) {
                                    {
                                        val level = vm.getProgressLessonLevel()
                                        Log.d("GrammarMate", "DailyPractice: user clicked daily practice, level=$level")
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
                                    ScreenLogger.overlay("pomodoro", shown = true, details = "duration=${duration}m")
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
                            onBack = remember(state.navigation.activePackId) {
                                {
                                    val activePackId = state.navigation.activePackId?.value
                                    val hasChapters = activePackId != null && vm.hasPackChapters(activePackId)
                                    Log.d("NavDebug", "LESSON onBack: hasChapters=$hasChapters")
                                    if (hasChapters) {
                                        // Restore selectedChapter so CHAPTER_LESSONS doesn't show spinner
                                        val currentLessonId = state.navigation.selectedLessonId?.value
                                        if (currentLessonId != null) {
                                            val chapter = vm.findChapterForLesson(currentLessonId)
                                            if (chapter != null) vm.selectChapter(chapter)
                                        }
                                        // Refresh chapter progress from disk (mastery may have changed)
                                        vm.loadChapters()
                                        Log.d("NavDebug", "LESSON onBack → CHAPTER_LESSONS")
                                        onNavigate(Routes.CHAPTER_LESSONS)
                                    } else {
                                        Log.d("NavDebug", "LESSON onBack → HOME")
                                        navController.popBackStack(Routes.HOME, inclusive = false)
                                    }
                                }
                            },
                            onStartSubLesson = remember { { index: Int ->
                                vm.selectSubLesson(index)
                                vm.setReturnToForLesson()
                                onNavigate(Routes.TRAINING)
                            } },
                            onStartBossLesson = remember { {
                                vm.startBossLesson()
                                vm.setReturnToForLesson()
                                onNavigate(Routes.TRAINING)
                            } },
                            onStartBossMega = remember { {
                                vm.startBossMega()
                                vm.setReturnToForLesson()
                                onNavigate(Routes.TRAINING)
                            } },
                            onReview = remember { { hintLevel: HintLevel ->
                                vm.startReview(hintLevel)
                                vm.setReturnToForLesson()
                                onNavigate(Routes.TRAINING)
                            } },
                            onNextLesson = remember(state.navigation.lessons, state.navigation.selectedLessonId) {
                                {
                                    val currentIdx = state.navigation.lessons.indexOfFirst { it.id == state.navigation.selectedLessonId }
                                    val nextLessonId = state.navigation.lessons.getOrNull(currentIdx + 1)?.id
                                    if (nextLessonId != null) {
                                        val currentPackId = state.navigation.activePackId?.value
                                        vm.selectLesson(nextLessonId.value, currentPackId)
                                        onNavigate(Routes.LESSON)
                                    }
                                }
                            }
                        )
                        // Inner BackHandler — has higher priority than NavController's internal
                        // handler, so system back gesture works correctly on Android 14+.
                        // Uses the same logic as the UI back button (onNavigate).
                        val lessonActivePackId = state.navigation.activePackId?.value
                        val lessonHasChapters = lessonActivePackId != null && vm.hasPackChapters(lessonActivePackId)
                        BackHandler(enabled = !dialogs.showSettings) {
                            Log.d("NavDebug", "BACK: LESSON inner handler, hasChapters=$lessonHasChapters")
                            ScreenLogger.nav("lesson", "BACK", trigger = "back_press")
                            AuditLogger.getInstanceOrNull()?.backPress("lesson", "back_to_${if (lessonHasChapters) "chapter_lessons" else "home"}")
                            if (lessonHasChapters) {
                                val currentLessonId = state.navigation.selectedLessonId?.value
                                if (currentLessonId != null) {
                                    val chapter = vm.findChapterForLesson(currentLessonId)
                                    if (chapter != null) vm.selectChapter(chapter)
                                }
                                onNavigate(Routes.CHAPTER_LESSONS)
                            } else {
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            }
                        }
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
                        LaunchedEffect(Unit) { Log.d("GrammarMate", "Screen: DAILY_PRACTICE shown") }
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
                                verbTenseInfoVm.reloadForLanguage(state.navigation.selectedLanguageId?.value ?: "en")
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
                            onShowSettings = remember(dialogs) { { previousRoute = Routes.TRAINING; vm.pauseSession(); ScreenLogger.overlay("settings", shown = true); dialogs = dialogs.copy(showSettings = true) } },
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
                                    Log.d("NavDebug", "SESSION_DONE: returnTo=$returnTo")
                                    when {
                                        returnTo == Routes.DAILY_PRACTICE -> {
                                            Log.d("NavDebug", "SESSION_DONE: → DAILY_PRACTICE")
                                            Log.d("GrammarMate", "DailyPractice: navigating after block, returnTo=DAILY_PRACTICE")
                                            vm.daily.onBlockComplete()
                                            onNavigate(Routes.DAILY_PRACTICE)
                                        }
                                        returnTo == Routes.CHAPTER_LESSONS -> {
                                            Log.d("NavDebug", "SESSION_DONE: → CHAPTER_LESSONS")
                                            onNavigate(Routes.CHAPTER_LESSONS)
                                        }
                                        returnTo == Routes.LESSON -> {
                                            val activePackId = state.navigation.activePackId?.value
                                            val hasChapters = activePackId != null && vm.hasPackChapters(activePackId)
                                            Log.d("NavDebug", "SESSION_DONE: returnTo=LESSON, hasChapters=$hasChapters")
                                            if (hasChapters) {
                                                Log.d("NavDebug", "SESSION_DONE: → CHAPTER_LESSONS (chapter pack)")
                                                onNavigate(Routes.CHAPTER_LESSONS)
                                            } else {
                                                Log.d("NavDebug", "SESSION_DONE: → LESSON (classic pack)")
                                                onNavigate(Routes.LESSON)
                                            }
                                        }
                                        else -> {
                                            Log.d("NavDebug", "SESSION_DONE: → HOME (default)")
                                            onNavigate(Routes.HOME)
                                        }
                                    }
                                }
                            },
                            getTenseInfo = remember { { tenseName: String -> verbTenseInfoVm.getTenseInfo(tenseName) } }
                        )

                        // Local back handler for VERB_DRILL return path
                        BackHandler(enabled = state.cardSession.returnTo == Routes.VERB_DRILL && !dialogs.showSettings) {
                            Log.d("NavDebug", "BACK: TRAINING VERB_DRILL return path")
                            ScreenLogger.nav(currentRoute, "BACK", trigger = "back_press")
                            AuditLogger.getInstanceOrNull()?.backPress("training", "verb_drill_return")
                            verbDrillVm.persistSessionState()
                            vm.exitVerbDrillSession()
                            onNavigate(Routes.VERB_DRILL)
                        }
                        // Local back handler for TRAINING — staircase navigation (only for lesson-based training)
                        BackHandler(enabled = state.cardSession.returnTo != Routes.VERB_DRILL && state.cardSession.returnTo != Routes.DAILY_PRACTICE && !dialogs.showSettings) {
                            Log.d("NavDebug", "BACK: TRAINING staircase → LESSON")
                            ScreenLogger.nav(currentRoute, "BACK", trigger = "back_press")
                            AuditLogger.getInstanceOrNull()?.backPress("training", "staircase_to_lesson")
                            vm.finishSession()
                            onNavigate(Routes.LESSON)
                        }
                    }

                    composable(Routes.VERB_DRILL) {
                        // Force reload when entering verb drill screen — ensures cards are loaded
                        // even if the outer LaunchedEffect didn't fire (e.g. packId didn't change)
                        val vdPackId = state.navigation.activePackId
                        LaunchedEffect(vdPackId, state.navigation.selectedLanguageId) {
                            if (vdPackId != null) {
                                verbDrillVm.reloadForPack(vdPackId.value)
                            } else {
                                verbDrillVm.reloadForLanguage(state.navigation.selectedLanguageId?.value ?: "en")
                            }
                        }
                        val verbDrillExit = remember(verbDrillVm) {
                            {
                                verbDrillVm.exitSession()
                                vm.refreshStreakFromStore()
                                onNavigate(Routes.HOME)
                            }
                        }
                        BackHandler {
                            ScreenLogger.nav(currentRoute, "BACK", trigger = "back_press")
                            AuditLogger.getInstanceOrNull()?.backPress("verb_drill", "exit_to_home")
                            verbDrillExit()
                        }
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
                                vocabDrillVm.reloadForPack(packId.value, state.navigation.selectedLanguageId?.value ?: "en")
                            } else {
                                vocabDrillVm.reloadForLanguage(state.navigation.selectedLanguageId?.value ?: "en")
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
                        BackHandler {
                            ScreenLogger.nav(currentRoute, "BACK", trigger = "back_press")
                            AuditLogger.getInstanceOrNull()?.backPress("vocab_drill", "exit_to_home")
                            vocabExit()
                        }
                        VocabDrillScreen(
                            viewModel = vocabDrillVm,
                            onBack = vocabExit,
                            hintLevel = state.cardSession.hintLevel,
                            textScale = state.audio.ruTextScale,
                            voiceAutoStart = state.audio.voiceAutoStart,
                            onBluetoothSetup = vm.audio::startBluetoothMicIfNeeded,
                            onBluetoothCleanup = vm.audio::stopBluetoothMicIfNeeded,
                            onClearBadSentences = vocabDrillVm::clearBadSentences,
                            badSentenceCount = vocabDrillVm.getBadSentenceCount()
                        )
                    }

                    composable(Routes.GRAMMAR_STORY_ROADMAP) {
                        val progressKey = state.chapterProgresses.values
                            .sortedBy { it.chapterId }
                            .joinToString(",") { "${it.chapterId}:${it.lessonsCompleted}/${it.lessonsStarted}" }
                        GrammarStoryRoadmapScreen(
                            chapters = remember(state.navigation.activePackId?.value, progressKey) { vm.getChapterCards() },
                            onBack = remember { { vm.clearActivePack(); navController.popBackStack(Routes.HOME, inclusive = false) } },
                            showBackButton = true,  // Show back button when accessed via direct route
                            onReadStory = remember { { chapter ->
                                val storyContent = vm.loadStoryContent(chapter.storyFile)
                                if (storyContent != null) {
                                    vm.setStoryReader(chapter.title, storyContent)
                                    onNavigate(Routes.STORY_READER)
                                } else {
                                    Toast.makeText(context, "Story not found: ${chapter.storyFile}", Toast.LENGTH_SHORT).show()
                                }
                            } },
                            onOpenSettings = remember(dialogs) {
                                {
                                    previousRoute = Routes.GRAMMAR_STORY_ROADMAP
                                    vm.pauseSession()
                                    ScreenLogger.overlay("settings", shown = true)
                                    dialogs = dialogs.copy(showSettings = true)
                                }
                            },
                            onContinue = remember { { chapter ->
                                // Navigate to chapter lessons screen
                                vm.selectChapter(chapter)
                                onNavigate(Routes.CHAPTER_LESSONS)
                            } },
                            onPlayChapterStory = remember { { chapter ->
                                // Quick play from roadmap - use multilingual TTS
                                val storyContent = vm.loadStoryContent(chapter.storyFile)
                                if (storyContent != null) {
                                    vm.speakMultilingualStory(storyContent, defaultLanguageId = "ru")
                                } else {
                                    Toast.makeText(context, "Story not found: ${chapter.storyFile}", Toast.LENGTH_SHORT).show()
                                }
                            } },
                            onVerbPractice = remember { { onNavigate(Routes.VERB_DRILL) } },
                            onFlashcards = remember { { onNavigate(Routes.VOCAB_DRILL) } },
                            onDailyPractice = remember(dialogs) {
                                {
                                    val level = vm.getProgressLessonLevel()
                                    Log.d("GrammarMate", "DailyPractice: user clicked daily practice, level=$level")
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
                            isStoryPlaying = state.audio.isStoryPlaybackActive || state.audio.ttsState == TtsState.Speaking,
                            isStoryPaused = state.audio.isStoryPlaybackPaused,
                            onStopStory = remember { { vm.stopStoryNarration() } },
                            onPauseStory = remember { { vm.pauseStoryPlayback() } },
                            onResumeStory = remember { { vm.resumeStoryPlayback() } }
                        )
                        // Back handler removed — handled by outer NavBackHandlers to avoid double-fire
                    }

                    composable(Routes.STORY_READER) {
                        val chapterTitle = state.storyReaderChapterTitle ?: "Unknown Chapter"
                        val content = state.storyReaderContent
                        val isCurrentlyPlaying = state.audio.isStoryPlaybackActive || state.audio.ttsState == TtsState.Speaking
                        val isStoryPaused = state.audio.isStoryPlaybackPaused

                        StoryReaderScreen(
                            chapterTitle = chapterTitle,
                            markdownContent = content,
                            textScale = state.audio.ruTextScale,
                            onBack = remember {
                                {
                                    // Stop TTS if playing or paused
                                    if (isCurrentlyPlaying || isStoryPaused) {
                                        vm.stopStoryNarration()
                                    }
                                    vm.clearStoryReader()
                                    // Pop back to whatever launched STORY_READER
                                    // (HOME showing roadmap, or GRAMMAR_STORY_ROADMAP route)
                                    navController.popBackStack()
                                }
                            },
                            onPlayStory = remember {
                                {
                                    // Use multilingual TTS for story narration
                                    if (content.isNotEmpty()) {
                                        vm.speakMultilingualStory(content, defaultLanguageId = "ru")
                                    }
                                }
                            },
                            onStopStory = remember {
                                {
                                    vm.stopStoryNarration()
                                }
                            },
                            onPauseStory = remember {
                                {
                                    vm.pauseStoryPlayback()
                                }
                            },
                            onResumeStory = remember {
                                {
                                    vm.resumeStoryPlayback()
                                }
                            },
                            isPlaying = isCurrentlyPlaying,
                            isStoryPaused = isStoryPaused
                        )
                    }

                    composable(Routes.CHAPTER_LESSONS) {
                        val selectedChapter = state.navigation.selectedChapter
                        val packId = state.navigation.activePackId

                        if (selectedChapter != null && packId != null) {
                            val chapterProgress = vm.getChapterProgress(packId.value, selectedChapter.chapterId)
                            val lessons = vm.getLessonsForChapter(selectedChapter)
                            val completedLessonIds = vm.getCompletedLessonIds(selectedChapter)

                            ChapterLessonsScreen(
                                chapterTitle = selectedChapter.title,
                                chapterSubtitle = selectedChapter.subtitle ?: "",
                                chapterId = selectedChapter.chapterId,
                                lessons = lessons,
                                chapterProgress = chapterProgress,
                                completedLessonIds = completedLessonIds,
                                onLessonClick = remember { { lesson ->
                                    vm.selectLesson(lesson.id.value, packId.value)
                                    onNavigate(Routes.LESSON)
                                } },
                                onBack = remember { { navController.popBackStack(Routes.HOME, inclusive = false) } }
                            )
                        } else {
                            // Fallback if no chapter selected
                            Box(modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                        }
                        // Inner BackHandler — higher priority than NavController, so system back
                        // works correctly on Android 14+ predictive back.
                        BackHandler(enabled = !dialogs.showSettings) {
                            Log.d("NavDebug", "BACK: CHAPTER_LESSONS inner handler → HOME")
                            ScreenLogger.nav("chapter_lessons", "BACK", trigger = "back_press")
                            AuditLogger.getInstanceOrNull()?.backPress("chapter_lessons", "back_to_home")
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        }
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
                    completionNextAction = completionNextAction,
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
//
// Architecture note:
//   GrammarStoryRoadmapScreen is rendered INSIDE the HOME composable when
//   `hasChapters == true`.  The separate GRAMMAR_STORY_ROADMAP route exists
//   only for direct entry from STORY_READER.  This means the "grammar roadmap"
//   that appears after selecting a chapters pack IS the HOME screen.
//
//   Therefore, back from CHAPTER_LESSONS must go to HOME (which shows the
//   roadmap), NOT to the GRAMMAR_STORY_ROADMAP route.  Using popBackStack()
//   is correct here because the stack naturally contains HOME at the base.
//
//   BackHandler priority in Compose: the LAST registered (innermost) handler
//   with enabled=true intercepts the back press first.  Inner handlers
//   (inside NavHost composables) take priority over outer handlers (here).

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
    // NOTE: CHAPTER_LESSONS and LESSON back are handled by INNER BackHandlers
    // inside their composable() blocks — they have higher priority than NavController's
    // internal handler and thus work correctly with Android 14+ predictive back.

    // ── HOME with chapters pack back → clear pack, stay on HOME ──
    // When HOME renders GrammarStoryRoadmapScreen (active chapters pack),
    // back should clear the pack and show pack selection (also on HOME).
    BackHandler(enabled = currentRoute == Routes.HOME && state.navigation.activePackId != null && vm.hasPackChapters(state.navigation.activePackId.value) && !showSettings) {
        Log.d("NavDebug", "BACK: HOME+chapters → clearActivePack")
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("home", "clear_active_pack")
        vm.clearActivePack()
        // No navigation needed — HOME recomposes with pack selection when activePackId becomes null
    }

    // ── TRAINING back (lesson-based) → show exit dialog ──
    // Note: inner TRAINING BackHandlers (verb_drill return, staircase) have
    // higher priority and will intercept first when applicable.
    BackHandler(enabled = currentRoute == Routes.TRAINING && !showSettings) {
        Log.d("NavDebug", "BACK: TRAINING BackHandler fired! showSettings=$showSettings, currentRoute=$currentRoute")
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("training", "show_exit_dialog")
        onShowExitDialog()
    }

    // ── DAILY_PRACTICE back → exit dialog ──
    BackHandler(enabled = currentRoute == Routes.DAILY_PRACTICE && !showSettings) {
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("daily_practice", "show_exit_dialog")
        onShowExitDialog()
    }

    // ── STORY back → LESSON ──
    BackHandler(enabled = currentRoute == Routes.STORY && !showSettings) {
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("story", "back_to_lesson")
        navController.navigate(Routes.LESSON) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }

    // ── LADDER back → previous route ──
    BackHandler(enabled = currentRoute == Routes.LADDER && !showSettings) {
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("ladder", "back_to_$previousRoute")
        navController.navigate(previousRoute) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
        if (previousRoute == Routes.TRAINING && state.cardSession.currentCard != null) {
            vm.resumeFromSettings()
        }
    }

    // ── TRAINING with daily-practice return → cancel daily, go HOME ──
    BackHandler(enabled = currentRoute == Routes.TRAINING && state.cardSession.returnTo == Routes.DAILY_PRACTICE && !showSettings) {
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("training", "daily_cancel_to_home")
        vm.cancelDailySession()
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = true }
            launchSingleTop = true
        }
    }

    // ── GRAMMAR_STORY_ROADMAP route back → HOME (clearActivePack) ──
    // This route is only reached from STORY_READER.  Clear pack and go to HOME.
    BackHandler(enabled = currentRoute == Routes.GRAMMAR_STORY_ROADMAP && !showSettings) {
        Log.d("NavDebug", "BACK: GRAMMAR_STORY_ROADMAP → HOME (clearActivePack)")
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("grammar_story_roadmap", "clear_active_pack_to_home")
        vm.clearActivePack()
        navController.popBackStack(Routes.HOME, inclusive = false)
    }

    // ── STORY_READER back → GRAMMAR_STORY_ROADMAP or HOME ──
    // STORY_READER is entered from either the HOME roadmap or the
    // GRAMMAR_STORY_ROADMAP route.  Pop back to whichever is underneath.
    BackHandler(enabled = currentRoute == Routes.STORY_READER && !showSettings) {
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("story_reader", "pop_back")
        // Pop back one step — returns to whatever launched STORY_READER
        // (HOME showing roadmap, or GRAMMAR_STORY_ROADMAP direct route).
        // Also stop TTS if playing.
        vm.stopStoryNarration()
        vm.clearStoryReader()
        navController.popBackStack()
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

    // Get lesson title for header — suppress during daily practice and verb drill modes
    val lessonTitle = if (state.cardSession.returnTo == Routes.DAILY_PRACTICE) {
        null  // Daily practice: don't show lesson title
    } else if (state.cardSession.returnTo == Routes.VERB_DRILL) {
        null  // Verb drill: don't show lesson title (drill CSV titles are technical)
    } else {
        state.navigation.lessons
            .firstOrNull { it.id == state.navigation.selectedLessonId }?.title
    }

    // Load grammar chip for current lesson (pack-scoped to avoid wrong-pack data)
    val activePackId = state.navigation.activePackId?.value
    val grammarChip = state.navigation.selectedLessonId?.let { lessonId ->
        GrammarChipStore.getChipForLesson(lessonId.value, packId = activePackId)
    }

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
        onClearBadSentences = vm::clearPackBadSentences,
        badSentenceCount = vm.reports.getBadSentenceCount(),
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
        grammarChip = grammarChip,
        lessonTitle = lessonTitle,
        onBluetoothSetup = vm.audio::startBluetoothMicIfNeeded,
        onBluetoothCleanup = vm.audio::stopBluetoothMicIfNeeded
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
    Log.d("GrammarMate", "DailyPractice: DailyPracticeScreenContent active=${dailyState.active}, finishedToken=${dailyState.finishedToken}, blockType=${currentBlock?.type}, blockIndex=${dailyState.blockIndex}")
    DailyPracticeScreen(
        state = dailyState,
        blockProgress = dailyProgress,
        currentBlock = currentBlock,
        currentTask = dailyTask,
        languageId = state.navigation.selectedLanguageId?.value ?: "en",
        onShowSentenceAnswer = vm.daily::getDailySentenceAnswer,
        onShowVerbAnswer = vm.daily::getDailyVerbAnswer,
        onRateVocabCard = remember { { rating: com.alexpo.grammermate.data.SrsRating -> vm.daily.rateVocabCard(rating) } },
        onStartCardBlock = remember(onNavigate) { { blockType: DailyBlockType, cards: List<com.alexpo.grammermate.data.SessionCard> ->
            Log.d("GrammarMate", "DailyPractice: startCardBlock type=$blockType, cards=${cards.size}, returnTo=DAILY_PRACTICE")
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
                vm.audio.onTtsSpeak(text)
            }
        } },
        onStopTts = remember { { vm.audio.stopTts() } },
        ttsState = state.audio.ttsState,
        onExit = remember(onNavigate) { {
            Log.d("GrammarMate", "DailyPractice: session exit, navigating HOME")
            vm.cancelDailySession()
            onNavigate(Routes.HOME)
        } },
        onComplete = remember { {
            // VOCAB block completed — signal coordinator to advance to next block
            val nextBlock = vm.daily.onBlockComplete()
            if (nextBlock == null) {
                // All blocks done — coordinator already called endSession()
                // The finishedToken will be set and DailyPracticeScreen shows completion
                Log.d("GrammarMate", "DailyPractice: session finished, all blocks done")
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
        voiceAutoStart = state.audio.voiceAutoStart,
        onBluetoothSetup = vm.audio::startBluetoothMicIfNeeded,
        onBluetoothCleanup = vm.audio::stopBluetoothMicIfNeeded,
        onClearBadSentences = vm::clearPackBadSentences,
        badSentenceCount = state.cardSession.badSentenceCount
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
    completionNextAction: androidx.compose.runtime.MutableState<CompletionNextAction>,
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
            if (text != null) vm.audio.onTtsSpeak(text)
        }
        if (dialogs.showTtsDownloadDialog) {
            TtsDownloadDialog(
                downloadState = state.audio.ttsDownloadState,
                languageId = state.navigation.selectedLanguageId?.value ?: "en",
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

    // Token-based navigation: sub-lesson finished — show completion dialog or auto-navigate
    if (currentRoute == Routes.TRAINING && state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
        lastFinishedToken.value = state.cardSession.subLessonFinishedToken
        Log.d("NavDebug", "TOKEN_NAV: subLessonFinished, pomodoroComplete=${state.pomodoro.isComplete}, hasCards=${state.cardSession.currentCard != null}, returnTo=${state.cardSession.returnTo}")
        // Pomodoro: track session completion for timer stats
        vm.onTrainingSessionCompleted()
        val hasCards = state.cardSession.currentCard != null

        if (!state.pomodoro.isComplete && hasCards) {
            val returnTo = state.cardSession.returnTo

            // Special flows (daily practice, verb drill) — keep original auto-navigate behavior
            if (returnTo == Routes.DAILY_PRACTICE) {
                Log.d("GrammarMate", "DailyPractice: navigating after block, returnTo=DAILY_PRACTICE")
                vm.daily.onBlockComplete()
                Log.d("NavDebug", "TOKEN_NAV: → DAILY_PRACTICE")
                onNavigate(returnTo)
            } else if (returnTo == Routes.VERB_DRILL) {
                Log.d("NavDebug", "TOKEN_NAV: → VERB_DRILL")
                onNavigate(returnTo)
            } else {
                // Lesson-based training — compute what's next and show dialog
                val nextAction = vm.computeCompletionNextAction()
                Log.d("NavDebug", "TOKEN_NAV: completion dialog, nextAction=$nextAction")
                completionNextAction.value = nextAction
            }
        }
        // If !hasCards: SessionCompletionContent will render via TrainingScreen early-return
        // User presses OK → onSessionDone → navigate HOME or DAILY_PRACTICE
    }

    // Sub-lesson completion dialog
    if (completionNextAction.value != CompletionNextAction.NONE) {
        val correctCount = state.cardSession.correctCount
        val incorrectCount = state.cardSession.incorrectCount
        val total = correctCount + incorrectCount
        val rate = if (total > 0) correctCount * 100 / total else 0

        val continueLabel = when (completionNextAction.value) {
            CompletionNextAction.NEXT_SUB_LESSON -> "Следующее упражнение"
            CompletionNextAction.NEXT_LESSON -> "Следующий урок"
            CompletionNextAction.NONE -> ""
        }

        val returnTo = state.cardSession.returnTo

        AlertDialog(
            onDismissRequest = {
                completionNextAction.value = CompletionNextAction.NONE
                // Exit: navigate back to lesson list
                val activePackId = state.navigation.activePackId?.value
                val hasChapters = activePackId != null && vm.hasPackChapters(activePackId)
                if (returnTo == Routes.CHAPTER_LESSONS || hasChapters) {
                    onNavigate(Routes.CHAPTER_LESSONS)
                } else {
                    onNavigate(Routes.LESSON)
                }
            },
            title = {
                Text(
                    text = "Упражнение завершено! 🎉",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "$correctCount правильно / $incorrectCount ошибок ($rate%)",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    if (completionNextAction.value == CompletionNextAction.NEXT_LESSON) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Урок пройден! Доступен следующий.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val action = completionNextAction.value
                    completionNextAction.value = CompletionNextAction.NONE
                    ScreenLogger.tap("completion_continue", "action=$action")
                    AuditLogger.getInstanceOrNull()?.dialogClose("completion", "continue_$action")
                    when (action) {
                        CompletionNextAction.NEXT_SUB_LESSON -> {
                            // Start next sub-lesson in current lesson
                            val nextIdx = state.cardSession.activeSubLessonIndex + 1
                            vm.selectSubLesson(nextIdx)
                            // Stay on TRAINING — cards will be rebuilt by ViewModel
                        }
                        CompletionNextAction.NEXT_LESSON -> {
                            // Find and start next lesson in chapter/pack
                            val activePackId = state.navigation.activePackId?.value
                            val hasChapters = activePackId != null && vm.hasPackChapters(activePackId)
                            val currentLessonId = state.navigation.selectedLessonId

                            if (hasChapters) {
                                val chapter = state.navigation.selectedChapter
                                if (chapter != null) {
                                    val currentIdx = chapter.lessons.indexOf(currentLessonId?.value)
                                    val nextLessonId = chapter.lessons.getOrNull(currentIdx + 1)
                                    if (nextLessonId != null) {
                                        vm.selectLesson(nextLessonId, activePackId)
                                        // Rebuild session for new lesson and stay on TRAINING
                                        vm.selectSubLesson(0)
                                    }
                                }
                            } else {
                                val lessons = state.navigation.lessons
                                val currentIdx = lessons.indexOfFirst { it.id == currentLessonId }
                                val nextLesson = lessons.getOrNull(currentIdx + 1)
                                if (nextLesson != null) {
                                    vm.selectLesson(nextLesson.id.value, activePackId)
                                    vm.selectSubLesson(0)
                                }
                            }
                            // Stay on TRAINING — cards will be rebuilt
                        }
                        CompletionNextAction.NONE -> { /* no-op */ }
                    }
                }) {
                    Text(continueLabel)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    completionNextAction.value = CompletionNextAction.NONE
                    ScreenLogger.tap("completion_exit")
                    AuditLogger.getInstanceOrNull()?.dialogClose("completion", "exit")
                    // Navigate back to lesson list
                    val activePackId = state.navigation.activePackId?.value
                    val hasChapters = activePackId != null && vm.hasPackChapters(activePackId)
                    if (returnTo == Routes.CHAPTER_LESSONS || hasChapters) {
                        onNavigate(Routes.CHAPTER_LESSONS)
                    } else {
                        onNavigate(Routes.LESSON)
                    }
                }) {
                    Text("Выход")
                }
            }
        )
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
                AuditLogger.getInstanceOrNull()?.dialogAction("exit_confirm", "yes")
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
                Log.d("NavDebug", "EXIT_DIALOG: confirm exit → LESSON")
                onNavigate(Routes.LESSON)
            }) {
                Text(text = stringResource(R.string.dialog_exit_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                AuditLogger.getInstanceOrNull()?.dialogAction("exit_confirm", "no")
                onDismiss()
            }) {
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
