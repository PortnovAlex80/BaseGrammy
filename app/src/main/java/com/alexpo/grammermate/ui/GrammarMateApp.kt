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
import com.alexpo.grammermate.ui.components.TtsDownloadStatusBanner
import com.alexpo.grammermate.ui.components.calcBgDownloadProgress
import com.alexpo.grammermate.ui.navigation.Routes
import com.alexpo.grammermate.ui.navigation.isVerbDrillLikeReturn
import com.alexpo.grammermate.ui.navigation.rememberOnDailyPractice
import com.alexpo.grammermate.ui.navigation.NavBackHandlers
import com.alexpo.grammermate.ui.navigation.routeToScreen
import com.alexpo.grammermate.ui.dialogs.DialogState
import com.alexpo.grammermate.ui.dialogs.NavDialogs
import com.alexpo.grammermate.ui.screens.TrainingScreenContent
import com.alexpo.grammermate.ui.screens.DailyPracticeScreenContent
import com.alexpo.grammermate.ui.screens.GrammarStoryRoadmapScreen
import com.alexpo.grammermate.ui.screens.ChapterLessonsScreen
import com.alexpo.grammermate.ui.screens.StoryReaderScreen
import com.alexpo.grammermate.ui.screens.BackgroundVocabScreen
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


// ── Dialog state holder ──────────────────────────────────────────────────────


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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.app_preparing),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {

        val navController = rememberNavController()
        // Use currentBackStackEntryAsState() for proper Compose state observation.
        // Direct property access does NOT trigger recomposition on navigate/popBackStack.
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: Routes.HOME
        val context = LocalContext.current

        // Story reader state managed in ViewModel (state.storyReaderChapterTitle, state.storyReaderContent)
        var dialogs by remember { mutableStateOf(DialogState()) }
        val dailyScope = rememberCoroutineScope()
        val lastFinishedToken = remember { mutableStateOf(state.cardSession.subLessonFinishedToken) }
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
                    // Phase 2, item 2.4: plain push. The stack is no longer
                    // flattened to [HOME, X] — back is meaningful again. Flows
                    // that must reset use onNavigateResetStack; "training done,
                    // return to origin" flows use onNavigatePopTo.
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            }
        }

        /** Abandon-flow navigation: reset to a clean single-entry stack. */
        val onNavigateResetStack: (String) -> Unit = remember(navController) {
            { route: String ->
                navController.navigate(route) {
                    popUpTo(Routes.HOME) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        /**
         * Training-done navigation: pop back to the existing origin entry
         * (LESSON / CHAPTER_LESSONS / drill screen) instead of pushing a
         * duplicate on top of TRAINING.
         */
        val onNavigatePopTo: (String) -> Unit = remember(navController) {
            { route: String ->
                if (!navController.popBackStack(route, inclusive = false)) {
                    navController.navigate(route) { launchSingleTop = true }
                }
            }
        }

        // Single consumer of the ViewModel's one-shot navigation events
        // (Phase 2, item 2.2): sub-lesson completion outcomes and boss exits
        // navigate exclusively through this stream.
        LaunchedEffect(Unit) {
            vm.navigationEvents.collect { event ->
                when (event) {
                    is NavigationEvent.Navigate -> onNavigatePopTo(event.route)
                    is NavigationEvent.CompletionDialog -> completionNextAction.value = event.action
                    NavigationEvent.BossSessionFinished -> onNavigatePopTo(Routes.LESSON)
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
                    onOpenLadder = remember(navController) {
                        {
                            ScreenLogger.overlay("settings", shown = false)
                            dialogs = dialogs.copy(showSettings = false)
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

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        LaunchedEffect(Unit) {
                            ScreenLogger.screenShown("HOME")
                        }
                        // VM-state slices (Phase 1): refreshed on their own events,
                        // not recomputed during composition.
                        val pomodoroHistory by vm.pomodoroHistory.collectAsStateWithLifecycle()
                        val packTiles by vm.packTiles.collectAsStateWithLifecycle()
                        val pomodoroLastDuration by vm.pomodoroLastDuration.collectAsStateWithLifecycle()
                        val activePackId = state.navigation.activePackId?.value
                        val hasChapters = state.navigation.activePackHasChapters

                        if (hasChapters) {
                            // Chapter cards are VM state refreshed on pack switch
                            // and chapter-progress updates (was: imperative call +
                            // remember keyed on a progress string — disk read on
                            // every key change, per-composition before Phase 1).
                            val chapterCards by vm.chapterCards.collectAsStateWithLifecycle()
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
                                        vm.setStoryReader(chapter.title, storyContent, chapter.storyFile)
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
                                        // ALLEGORY_PACK stories are in Russian with Italian insertions.
                                        // When a pre-rendered Opus narration clip exists for this chapter
                                        // (real-voice narration), it is played instead of TTS; otherwise
                                        // the multilingual TTS path synthesizes the story text.
                                        vm.speakMultilingualStory(
                                            storyContent,
                                            defaultLanguageId = "ru",
                                            storyFile = chapter.storyFile,
                                            packId = activePackId
                                        )
                                    } else {
                                        Toast.makeText(context, "Story not found: ${chapter.storyFile}", Toast.LENGTH_SHORT).show()
                                    }
                                } },
                                onOpenSettings = remember {
                                    {
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
                                onAuxDrill = remember { { onNavigate(Routes.AUX_DRILL) } },
                                onFlashcards = remember { { onNavigate(Routes.VOCAB_DRILL) } },
                                onDailyPractice = rememberOnDailyPractice(
                                    vm = vm,
                                    getDialogs = { dialogs },
                                    setDialogs = { dialogs = it },
                                    onNavigate = onNavigate
                                ),
                                onBackgroundVocab = remember { { onNavigate(Routes.BACKGROUND_VOCAB) } },
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
                                onOpenSettings = remember {
                                    {
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
                                onDailyPractice = rememberOnDailyPractice(
                                    vm = vm,
                                    getDialogs = { dialogs },
                                    setDialogs = { dialogs = it },
                                    onNavigate = onNavigate
                                ),
                                hasVerbDrill = state.navigation.hasVerbDrill,
                                hasVocabDrill = state.navigation.hasVocabDrill,
                                onOpenVerbDrill = remember { { onNavigate(Routes.VERB_DRILL) } },
                                onOpenAuxDrill = remember { { onNavigate(Routes.AUX_DRILL) } },
                                onOpenVocabDrill = remember { { onNavigate(Routes.VOCAB_DRILL) } },
                                onProfileClick = remember { { dialogs = dialogs.copy(showProfileStats = true) } },
                                onStartPomodoro = remember { { duration: Int ->
                                    ScreenLogger.overlay("pomodoro", shown = true, details = "duration=${duration}m")
                                    vm.startPomodoro(duration)
                                    onNavigate(Routes.LESSON)
                                } },
                                pomodoroLastDuration = pomodoroLastDuration,
                                pomodoroHistory = pomodoroHistory,
                                packTiles = packTiles,
                                onBackgroundVocab = remember { { onNavigate(Routes.BACKGROUND_VOCAB) } }
                            )
                        }
                    }

                    composable(Routes.LESSON) {
                        LessonRoadmapScreen(
                            state = state,
                            onBack = remember(state.navigation.activePackId) {
                                {
                                    val hasChapters = state.navigation.activePackHasChapters
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
                                        onNavigatePopTo(Routes.CHAPTER_LESSONS)
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
                        val lessonHasChapters = state.navigation.activePackHasChapters
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
                                onNavigatePopTo(Routes.CHAPTER_LESSONS)
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
                        DailyPracticeScreenContent(state, vm, remember { { route: String -> onNavigate(route) } }, onNavigateResetStack)
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
                            onBack = remember(state.cardSession.currentCard) {
                                {
                                    // LADDER was pushed on top of its opener —
                                    // resume a paused session only if that opener
                                    // was TRAINING with a live card.
                                    val opener = navController.previousBackStackEntry?.destination?.route
                                    if (opener == Routes.TRAINING && state.cardSession.currentCard != null) {
                                        vm.resumeFromSettings()
                                    }
                                    navController.popBackStack()
                                }
                            }
                        )
                    }

                    composable(Routes.TRAINING) {
                        // Phase 4 (4.6): the tense-info bottom sheet uses the SAME
                        // Activity-scoped verbDrillVm — one instance, one load
                        // (the former route-scoped duplicate ran a parallel
                        // reloadForPack and held divergent drill state).
                        TrainingScreenContent(
                            state, vm,
                            onSubmit = {
                                val beforeCard = state.cardSession.currentCard
                                val result = vm.submitAnswer()
                                if (
                                    isVerbDrillLikeReturn(state.cardSession.returnTo) &&
                                    beforeCard is VerbDrillCard &&
                                    result.accepted
                                ) {
                                    verbDrillVm.submitCorrectAnswer()
                                }
                                result
                            },
                            onNext = {
                                if (
                                    isVerbDrillLikeReturn(state.cardSession.returnTo) &&
                                    state.cardSession.currentCard is VerbDrillCard &&
                                    (state.cardSession.lastResult == false || state.cardSession.answerText != null)
                                ) {
                                    verbDrillVm.markCardCompleted()
                                }
                                vm.navigateNext()
                            },
                            onPrev = {
                                if (
                                    isVerbDrillLikeReturn(state.cardSession.returnTo) &&
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
                                        // Verb/Aux drill with active card (exiting mid-session) → return to drill screen
                                        isVerbDrillLikeReturn(returnTo) && hasActiveCard -> {
                                            verbDrillVm.persistSessionState()
                                            vm.exitVerbDrillSession()
                                            onNavigatePopTo(returnTo)
                                        }
                                        // Verb/Aux drill with NO active card (block completed) → save state, go to HOME
                                        isVerbDrillLikeReturn(returnTo) && !hasActiveCard -> {
                                            verbDrillVm.persistSessionState()
                                            vm.exitVerbDrillSession()
                                            onNavigateResetStack(Routes.HOME)
                                        }
                                        returnTo == Routes.DAILY_PRACTICE -> {
                                            vm.cancelDailySession()
                                            onNavigateResetStack(Routes.HOME)
                                        }
                                        else -> {
                                            dialogs = dialogs.copy(showExitDialog = true)
                                        }
                                    }
                                }
                            },
                            onShowSettings = remember { { vm.pauseSession(); ScreenLogger.overlay("settings", shown = true); dialogs = dialogs.copy(showSettings = true) } },
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
                                    onNavigatePopTo(Routes.VERB_DRILL)
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
                                            onNavigatePopTo(Routes.DAILY_PRACTICE)
                                        }
                                        returnTo == Routes.CHAPTER_LESSONS -> {
                                            Log.d("NavDebug", "SESSION_DONE: → CHAPTER_LESSONS")
                                            onNavigatePopTo(Routes.CHAPTER_LESSONS)
                                        }
                                        returnTo == Routes.LESSON -> {
                                            val hasChapters = state.navigation.activePackHasChapters
                                            Log.d("NavDebug", "SESSION_DONE: returnTo=LESSON, hasChapters=$hasChapters")
                                            if (hasChapters) {
                                                Log.d("NavDebug", "SESSION_DONE: → CHAPTER_LESSONS (chapter pack)")
                                                onNavigatePopTo(Routes.CHAPTER_LESSONS)
                                            } else {
                                                Log.d("NavDebug", "SESSION_DONE: → LESSON (classic pack)")
                                                onNavigatePopTo(Routes.LESSON)
                                            }
                                        }
                                        isVerbDrillLikeReturn(returnTo) -> {
                                            // VERB_DRILL / AUX_DRILL: return to the drill screen we came from
                                            Log.d("NavDebug", "SESSION_DONE: → drill return $returnTo")
                                            onNavigatePopTo(returnTo)
                                        }
                                        else -> {
                                            Log.d("NavDebug", "SESSION_DONE: → HOME (default)")
                                            onNavigateResetStack(Routes.HOME)
                                        }
                                    }
                                }
                            },
                            getTenseInfo = remember { { tenseName: String -> verbDrillVm.getTenseInfo(tenseName) } }
                        )

                        // Local back handler for VERB_DRILL / AUX_DRILL return path
                        BackHandler(enabled = isVerbDrillLikeReturn(state.cardSession.returnTo) && !dialogs.showSettings) {
                            Log.d("NavDebug", "BACK: TRAINING drill return path -> ${state.cardSession.returnTo}")
                            ScreenLogger.nav(currentRoute, "BACK", trigger = "back_press")
                            AuditLogger.getInstanceOrNull()?.backPress("training", "drill_return")
                            verbDrillVm.persistSessionState()
                            vm.exitVerbDrillSession()
                            onNavigatePopTo(state.cardSession.returnTo)
                        }
                        // Local back handler: daily-practice training → cancel + HOME
                        BackHandler(enabled = state.cardSession.returnTo == Routes.DAILY_PRACTICE && !dialogs.showSettings) {
                            ScreenLogger.nav(currentRoute, "BACK", trigger = "back_press")
                            AuditLogger.getInstanceOrNull()?.backPress("training", "daily_cancel_to_home")
                            vm.cancelDailySession()
                            onNavigateResetStack(Routes.HOME)
                        }
                        // Local back handler: lesson training → exit confirmation
                        // dialog — the SAME behavior as the on-screen Stop button
                        // (Phase 2, item 2.5). These handlers live INSIDE the
                        // TRAINING destination so they always outrank both
                        // NavBackHandlers and NavController's own pop.
                        BackHandler(enabled = !isVerbDrillLikeReturn(state.cardSession.returnTo) && state.cardSession.returnTo != Routes.DAILY_PRACTICE && !dialogs.showSettings) {
                            Log.d("NavDebug", "BACK: TRAINING lesson → exit dialog")
                            ScreenLogger.nav(currentRoute, "BACK", trigger = "back_press")
                            AuditLogger.getInstanceOrNull()?.backPress("training", "show_exit_dialog")
                            dialogs = dialogs.copy(showExitDialog = true)
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
                                onNavigateResetStack(Routes.HOME)
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
                                onNavigateResetStack(Routes.HOME)
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

                    composable(Routes.AUX_DRILL) {
                        val auxVm = viewModel<AuxDrillViewModel>()
                        val packId = state.navigation.activePackId
                        LaunchedEffect(packId, state.navigation.selectedLanguageId) {
                            if (packId != null) {
                                auxVm.reloadForPack(packId.value, state.navigation.selectedLanguageId?.value ?: "it")
                            }
                        }
                        val auxExit = remember(auxVm) {
                            {
                                onNavigateResetStack(Routes.HOME)
                            }
                        }
                        BackHandler {
                            auxExit()
                        }
                        AuxDrillScreen(
                            viewModel = auxVm,
                            onBack = auxExit,
                            onStartTraining = remember(auxVm) { { pair ->
                                val deck = auxVm.sessionCardsFor(pair)
                                if (!deck.isNullOrEmpty()) {
                                    // Record shown cards for aux-scoped progress, then hand the
                                    // deck to the shared training session (same path as Verb Drill).
                                    auxVm.recordShown(pair, deck.map { it.id }.toSet())
                                    vm.startVerbDrillSession(deck)
                                    vm.setReturnTo(Routes.AUX_DRILL)
                                    onNavigate(Routes.TRAINING)
                                }
                            } }
                        )
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
                                    // (HOME showing the roadmap).
                                    navController.popBackStack()
                                }
                            },
                            onPlayStory = remember {
                                {
                                    // Play story narration: prefer the pre-rendered Opus clip for this
                                    // chapter (real-voice narration) when present; otherwise fall back to
                                    // multilingual TTS synthesis of the on-screen content.
                                    if (content.isNotEmpty()) {
                                        vm.speakMultilingualStory(
                                            content,
                                            defaultLanguageId = "ru",
                                            storyFile = state.storyReaderStoryFile,
                                            packId = state.navigation.activePackId?.value
                                        )
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
                        // Live progress (Phase 3, item 3.3): the SAME
                        // computation the HOME roadmap consumes — the roadmap
                        // and this screen can no longer diverge.
                        val chapterCards by vm.chapterCards.collectAsStateWithLifecycle()

                        if (selectedChapter != null && packId != null) {
                            val chapterProgress = chapterCards.firstOrNull {
                                it.chapter.chapterId == selectedChapter.chapterId
                            }?.progress
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

                    composable(Routes.BACKGROUND_VOCAB) {
                        LaunchedEffect(Unit) {
                            ScreenLogger.screenShown("BACKGROUND_VOCAB")
                        }
                        BackgroundVocabScreen(
                            onBack = remember { {
                                ScreenLogger.nav("background_vocab", "BACK", trigger = "back_press")
                                AuditLogger.getInstanceOrNull()?.backPress("background_vocab", "back_to_home")
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            } }
                        )
                        BackHandler(enabled = !dialogs.showSettings) {
                            ScreenLogger.nav("background_vocab", "BACK", trigger = "back_press")
                            AuditLogger.getInstanceOrNull()?.backPress("background_vocab", "back_to_home")
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        }
                    }
                }

                // Route-level back handlers, registered AFTER NavHost on purpose
                // (Phase 2, items 2.5/2.6). Back-callback priority is LIFO — the
                // LAST registered enabled callback wins. Screen-internal handlers
                // (inside composable(...) destinations) register whenever their
                // destination composes, i.e. above these; NavController's own pop
                // registers with the dispatcher at rememberNavController() time,
                // i.e. BELOW these. Effective order: screen-internal > these route
                // handlers > NavController pop. Before the move, NavController
                // silently outranked these wherever the back stack exceeded [HOME].
                NavBackHandlers(
                    currentRoute = currentRoute,
                    showSettings = dialogs.showSettings,
                    state = state,
                    vm = vm,
                    navController = navController,
                    onShowExitDialog = remember { { dialogs = dialogs.copy(showExitDialog = true) } }
                )

                NavDialogs(
                    dialogs = dialogs,
                    state = state,
                    currentRoute = currentRoute,
                    vm = vm,
                    dailyScope = dailyScope,
                    lastFinishedToken = lastFinishedToken,
                    completionNextAction = completionNextAction,
                    onDialogsChange = remember { { dialogs = it } },
                    onNavigate = onNavigate,
                    onNavigateResetStack = onNavigateResetStack,
                    onNavigatePopTo = onNavigatePopTo
                )
            } // Box
        } // Column
        } // else (not loading)
    } // Surface
} // GrammarMateApp

/**
 * True when the training session's [returnTo] route is a verb-drill-style
 * screen (regular Verb Drill or the aux lead-in drill). Aux drill reuses the
 * VERB_DRILL training mode, so all verb-drill-specific UI logic (chip cards,
 * header suppression, back/return navigation, session-done routing) applies to
 * both. Centralizing the check here keeps the call sites from listing both
 * routes explicitly.
 */
