package com.alexpo.grammermate.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import com.alexpo.grammermate.AppContainer
import com.alexpo.grammermate.BuildConfig
import com.alexpo.grammermate.ui.navigation.Routes
import com.alexpo.grammermate.GrammarMateApplication
import com.alexpo.grammermate.data.SubmitResult
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.ParseError
import com.alexpo.grammermate.data.SoundPackManager
import com.alexpo.grammermate.feature.backgroundvocab.SoundPackManagerSource
import com.alexpo.grammermate.feature.backgroundvocab.SoundPackTransferController
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.countsTowardMastery
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.StoryPhase
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VocabEntry
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.data.CompletionNextAction
import com.alexpo.grammermate.data.PracticeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

import com.alexpo.grammermate.feature.boss.BossBattleRunner
import com.alexpo.grammermate.feature.boss.BossCommand
import com.alexpo.grammermate.feature.boss.BossOrchestrator
import com.alexpo.grammermate.feature.daily.DailyPracticeCoordinator
import com.alexpo.grammermate.shared.TrainingStateAccess
import com.alexpo.grammermate.feature.progress.BadSentenceHelper
import com.alexpo.grammermate.feature.progress.BadSentenceResult
import com.alexpo.grammermate.feature.progress.ChapterRoadmapController
import com.alexpo.grammermate.feature.progress.FlowerRefresher
import com.alexpo.grammermate.feature.progress.ProgressResult
import com.alexpo.grammermate.feature.progress.ProgressRestorer
import com.alexpo.grammermate.feature.progress.ProgressTracker
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.feature.training.CardProvider
import com.alexpo.grammermate.feature.training.ProfileStats
import com.alexpo.grammermate.feature.training.ProfileStatsSource
import com.alexpo.grammermate.feature.training.SessionEvent
import com.alexpo.grammermate.feature.training.SessionRunner
import com.alexpo.grammermate.feature.training.StoryResult
import com.alexpo.grammermate.feature.training.StoryRunner
import com.alexpo.grammermate.feature.story.StoryReaderController
import com.alexpo.grammermate.feature.training.WordBankGenerator
import com.alexpo.grammermate.feature.vocab.VocabResult
import com.alexpo.grammermate.feature.vocab.VocabSoundResult
import com.alexpo.grammermate.feature.vocab.VocabSprintRunner
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.SettingsActionHandler
import com.alexpo.grammermate.shared.SettingsResult
import com.alexpo.grammermate.shared.audio.AudioCoordinator
import com.alexpo.grammermate.feature.pomodoro.PomodoroHelper
import com.alexpo.grammermate.data.PomodoroHistoryStore
import com.alexpo.grammermate.data.PomodoroSettingsStore
import com.alexpo.grammermate.feature.pomodoro.PomodoroRecords
import com.alexpo.grammermate.data.CardDifficultyRating
import com.alexpo.grammermate.data.PackLessonProgressStore
import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.ChapterCardUi


class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val logTag = "GrammarMate"
    private val container: AppContainer = when (application) {
        is GrammarMateApplication -> application.container
        else -> AppContainer(application)
    }
    private val lessonStore = container.lessonStore
    private val progressStore = container.progressStore
    private val configStore = container.configStore
    private val masteryStore = container.masteryStore
    private val streakStore = container.streakStore
    private val badSentenceStore = container.badSentenceStore
    private val hiddenCardStore = container.hiddenCardStore
    private var vocabProgressStore = container.vocabProgressStore(null)
    private var wordMasteryStore = container.wordMasteryStore(null)
    private val packLessonProgressStore = container.packLessonProgressStore
    private val packDailyCursorStore = container.packDailyCursorStore()
    private val backupManager = container.backupManager
    private val profileStore = container.profileStore
    private val _coreState = MutableStateFlow(TrainingUiState(isLoading = true))
    @Volatile private var runtimeOwnersReady = false

    // ── One-shot navigation events (Phase 2, item 2.2) ────────────────────
    // Consumed by a single LaunchedEffect collector in GrammarMateApp. The
    // channel is BUFFERED so events raised before the collector attaches
    // (e.g. during startup) are not dropped.
    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<NavigationEvent> = _navigationEvents.receiveAsFlow()

    /**
     * Edge handler for "a sub-lesson finished" (the UI's token-keyed effect
     * calls this exactly once per completion). Owns the pomodoro bookkeeping
     * and decides the navigation outcome; the UI only renders what the
     * emitted [NavigationEvent] says.
     */
    fun handleSubLessonFinished() {
        onTrainingSessionCompleted()
        val s = _coreState.value
        val hasCards = s.cardSession.currentCard != null
        if (s.pomodoro.isComplete || !hasCards) return
        val returnTo = s.cardSession.returnTo
        viewModelScope.launch {
            when {
                returnTo == Routes.DAILY_PRACTICE -> {
                    dailyPracticeCoordinator.onBlockComplete()
                    _navigationEvents.send(NavigationEvent.Navigate(Routes.DAILY_PRACTICE))
                }
                returnTo == Routes.VERB_DRILL || returnTo == Routes.AUX_DRILL -> {
                    _navigationEvents.send(NavigationEvent.Navigate(requireNotNull(returnTo)))
                }
                else -> _navigationEvents.send(
                    NavigationEvent.CompletionDialog(computeCompletionNextAction())
                )
            }
        }
    }

    // ── High-frequency flows (separate from main state for performance) ──
    private val _sessionTimerMs = MutableStateFlow(0L)
    val sessionTimerMs: StateFlow<Long> = _sessionTimerMs.asStateFlow()

    /**
     * Typed answer text (TASK-091 item 4). Every keystroke used to flow
     * through the combined uiState (7-flow combine + two full TrainingUiState
     * copies), recomposing the whole NavHost. Like [sessionTimerMs], this
     * high-frequency value lives in its own flow; SessionRunner shares the
     * same instance.
     */
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _pomodoroRemainingSeconds = MutableStateFlow(0)
    val pomodoroRemainingSeconds: StateFlow<Int> = _pomodoroRemainingSeconds.asStateFlow()

    // ── Shared stateAccess — single instance for all helpers ──────────────
    private val stateAccess: TrainingStateAccess = object : TrainingStateAccess {
        override val currentState: TrainingUiState
            get() {
                val core = _coreState.value
                // Owners are initialized after this bridge. A constructor-time
                // read sees the core; command-time reads see every owner now.
                if (!runtimeOwnersReady) return core
                return core.copy(
                    audio = audioCoordinator.audioState.value,
                    story = storyRunner.stateFlow.value,
                    vocabSprint = vocabSprintRunner.vocabState.value,
                    daily = dailyPracticeCoordinator.dailyState.value,
                    flowerDisplay = flowerRefresher.stateFlow.value,
                    boss = bossOrchestrator.stateFlow.value
                )
            }
        override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
            _coreState.update(transform)
            AuditLogger.getInstanceOrNull()?.updateContext(_coreState.value)
        }
        override fun saveProgress() = this@TrainingViewModel.saveProgress()
    }

    // ── Pack tile data for home screen ────────────────────────────────────

    private val packTileSource = PackTileSource(lessonStore, masteryStore)
    private val contextResolver = TrainingContextResolver(lessonStore)

    private fun getPackTiles(): List<PackTileUi> = packTileSource.getTiles(_coreState.value.navigation)

    // ── Derived data as state (Phase 1: no disk I/O during composition) ───
    // These were imperative per-composition calls; they are now StateFlows
    // refreshed on the events that actually change them, with equality-skip
    // so unchanged refreshes emit nothing.

    private val _packTiles = MutableStateFlow<List<PackTileUi>>(emptyList())
    val packTiles: StateFlow<List<PackTileUi>> = _packTiles.asStateFlow()

    private val chapterRoadmap = ChapterRoadmapController(
        lessonStore,
        com.alexpo.grammermate.feature.progress.ChapterRepository(lessonStore, masteryStore),
        _coreState,
        logTag
    )
    val chapterCards: StateFlow<List<ChapterCardUi>> = chapterRoadmap.chapterCards

    private val profileStatsSource = ProfileStatsSource(
        scope = viewModelScope,
        lessonStore = lessonStore,
        masteryStore = masteryStore,
        wordMasteryStoreProvider = { wordMasteryStore }
    )
    val profileStats: StateFlow<ProfileStats> get() = profileStatsSource.profileStats

    val pomodoroHistory get() = pomodoroRecords.history
    val pomodoroLastDuration get() = pomodoroRecords.lastDuration

    private fun refreshPackTiles() {
        val tiles = getPackTiles()
        if (_packTiles.value != tiles) _packTiles.value = tiles
    }

    private fun refreshProfileStats() {
        val navigation = _coreState.value.navigation
        profileStatsSource.refresh(
            languageId = navigation.selectedLanguageId?.value,
            packId = navigation.activePackId?.value
        )
    }

    private fun refreshPomodoroHistory() {
        pomodoroRecords.refreshHistory(_coreState.value.navigation.selectedLanguageId?.value)
    }

    /** Session resets zero cardSession.badSentenceCount — re-seed it for the
     *  active pack (before Phase 1 the TRAINING screen recomputed it per
     *  composition; the count also feeds the updateStreak threshold). */
    private fun refreshBadSentenceCount() {
        val packId = _coreState.value.navigation.activePackId?.value
        val count = if (packId != null) badSentenceStore.getBadSentenceCount(packId) else 0
        if (_coreState.value.cardSession.badSentenceCount != count) {
            _coreState.update { it.copy(cardSession = it.cardSession.copy(badSentenceCount = count)) }
        }
    }

    // ── Feature instances (declared before uiState combine chain) ──────────

    private val answerValidator = AnswerValidator()
    private var vocabSprintRunner = VocabSprintRunner(
        stateAccess = stateAccess,
        lessonStore = lessonStore,
        vocabProgressStoreProvider = { vocabProgressStore },
        answerValidator = answerValidator
    )

    private var vocabSession: List<VocabEntry>
        get() = vocabSprintRunner.vocabSession
        set(value) { vocabSprintRunner.vocabSession = value }
    private var subLessonTotal: Int = 0
    private var subLessonCount: Int = 0
    private var lessonSchedules: Map<com.alexpo.grammermate.data.LessonId, LessonSchedule> = emptyMap()

    /** Saved lesson ID before daily practice started, for restoration on exit. */
    private var preDailySelectedLessonId: com.alexpo.grammermate.data.LessonId? = null
    private val subLessonSizeMin = TrainingConfig.SUB_LESSON_SIZE_MIN
    private val subLessonSizeMax = TrainingConfig.SUB_LESSON_SIZE_MAX
    private var sessionSize: Int = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
    private val eliteStepCount = TrainingConfig.ELITE_STEP_COUNT
    private var eliteSizeMultiplier: Double = 1.25

    private val streakManager = StreakManager(streakStore)
    private val bossBattleRunner = BossBattleRunner()

    private val progressTracker = ProgressTracker(
        stateAccess = stateAccess,
        masteryStore = masteryStore,
        progressStore = progressStore,
        lessonStore = lessonStore,
        packDailyCursorStore = packDailyCursorStore,
        packLessonProgressStore = packLessonProgressStore
    )

    private val cardProvider = CardProvider(
        subLessonSize = sessionSize,
        subLessonSizeMin = subLessonSizeMin,
        subLessonSizeMax = subLessonSizeMax,
        eliteSizeMultiplier = eliteSizeMultiplier,
        eliteStepCount = eliteStepCount,
        progressTracker = progressTracker
    )

    private val sessionRunner = SessionRunner(
        stateAccess = stateAccess,
        appContext = application,
        coroutineScope = viewModelScope,
        answerValidator = answerValidator,
        wordBankGenerator = WordBankGenerator,
        cardProvider = cardProvider,
        streakManager = streakManager,
        getMastery = { lessonId, _ ->
            _coreState.value.navigation.activePackId?.let { pid ->
                masteryStore.getForPack(pid.value, lessonId)
            }
        },
        getSchedule = { lessonId -> lessonSchedules[com.alexpo.grammermate.data.LessonId(lessonId)] },
        getHiddenCardIds = { hiddenCardStore.getHiddenCardIds() },
        calculateCompletedSubLessons = { subLessons, mastery, lessonId, hiddenIds ->
            progressTracker.calculateCompletedSubLessons(
                subLessons = subLessons,
                mastery = mastery,
                lessonId = lessonId?.let { com.alexpo.grammermate.data.LessonId(it) },
                lessons = _coreState.value.navigation.lessons,
                hiddenCardIds = hiddenIds
            )
        },
        onTimerSaveProgress = { saveProgressFromTimer() },
        sessionTimerMsSink = { ms -> _sessionTimerMs.value = ms },
        inputTextFlow = _inputText
    )

    private val flowerRefresher = FlowerRefresher(
        stateAccess = stateAccess,
        masteryStore = masteryStore
    )

    private val dailyPracticeCoordinator = DailyPracticeCoordinator(
        stateAccess = stateAccess,
        appContext = application,
        answerValidator = answerValidator,
        lessonStore = lessonStore,
        masteryStore = masteryStore,
        verbDrillStoreFactory = { packId -> container.verbDrillStore(packId) },
        wordMasteryStoreFactory = { packId -> container.wordMasteryStore(packId) },
        streakStore = streakStore,
        streakManager = streakManager,
        packDailyCursorStore = packDailyCursorStore
    )

    private val storyRunner = StoryRunner(
        stateAccess = stateAccess,
        lessonStore = lessonStore,
    )

    private val bossOrchestrator = BossOrchestrator(
        stateAccess = stateAccess,
        bossBattleRunner = bossBattleRunner,
        cardProvider = cardProvider,
        sessionRunner = sessionRunner,
        progressStore = progressStore,
        masteryStore = masteryStore
    )

    private val progressRestorer = ProgressRestorer(
        stateAccess = stateAccess,
        progressStore = progressStore,
        profileStore = profileStore,
        streakStore = streakStore,
        lessonStore = lessonStore,
        backupManager = backupManager,
        eliteStepCount = eliteStepCount,
        normalizeEliteSpeeds = { speeds -> sessionRunner.normalizeEliteSpeeds(speeds) },
        resolveEliteUnlocked = { lessons, testMode -> sessionRunner.resolveEliteUnlocked(lessons, testMode) },
        parseBossRewards = { rewardMap -> bossOrchestrator.parseBossRewards(rewardMap) },
        packLessonProgressStore = packLessonProgressStore
    )

    private val badSentenceHelper = BadSentenceHelper(
        stateAccess = stateAccess,
        badSentenceStore = badSentenceStore,
        hiddenCardStore = hiddenCardStore
    )

    private val audioCoordinator = AudioCoordinator(
        stateAccess = stateAccess,
        appContext = application,
        coroutineScope = viewModelScope,
        configStore = configStore,
        storyAudioResolver = container.storyAudioResolver
    )
    private val storyReader = StoryReaderController(lessonStore, audioCoordinator, _coreState, logTag)

    /** Lifecycle owner is lazy because most sessions never open sound-pack settings. */
    private val soundPackTransfers: SoundPackTransferController by lazy {
        SoundPackTransferController(
            source = SoundPackManagerSource(SoundPackManager(getApplication(), container.baseDir)),
            scope = viewModelScope,
            publishState = audioCoordinator::updateSoundPackDownloadState,
            publishCount = audioCoordinator::updateSoundPackInstalledCount
        )
    }

    private val pomodoroRecords = PomodoroRecords(
        PomodoroSettingsStore(getApplication<Application>()),
        PomodoroHistoryStore(getApplication<Application>())
    )
    private val startupLoader = TrainingStartupLoader(
        application, container, StartupContentResolver(lessonStore), pomodoroRecords
    )
    private val pomodoroHelper = PomodoroHelper(
        stateProvider = { _coreState.value },
        onUpdateState = { newState -> _coreState.update { newState } },
        onUpdatePomodoroRemaining = { seconds -> _pomodoroRemainingSeconds.value = seconds },
        scope = viewModelScope,
        onPauseTraining = { handleSessionEvents(sessionRunner.pauseSession()) },
        onResumeTraining = { handleSessionEvents(sessionRunner.resumeFromSettings()) },
        onPlayCompletionSound = {
            try {
                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = android.media.RingtoneManager.getRingtone(getApplication<Application>(), uri)
                ringtone?.play()
            } catch (_: Exception) {}
        },
        onPomodoroCompleted = { stats, remainingSeconds, totalSeconds ->
            val navigation = _coreState.value.navigation
            pomodoroRecords.appendCompleted(
                stats, remainingSeconds, totalSeconds,
                navigation.selectedLanguageId?.value,
                navigation.activePackId?.value,
                navigation.selectedLessonId?.value
            )
        }
    )

    // ── Combined state flow (all feature flows merged with core) ──────────
    val uiState: StateFlow<TrainingUiState> = combine(
        _coreState,
        audioCoordinator.audioState,
        storyRunner.stateFlow,
        vocabSprintRunner.vocabState,
        dailyPracticeCoordinator.dailyState
    ) { core, audio, story, vocabSprint, daily ->
        core.copy(
            audio = audio,
            story = story,
            vocabSprint = vocabSprint,
            daily = daily
        )
    }.let { partial ->
        combine(
            partial,
            flowerRefresher.stateFlow,
            bossOrchestrator.stateFlow
        ) { state, flower, boss ->
            state.copy(flowerDisplay = flower, boss = boss)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _coreState.value)

    private val settingsActionHandler = SettingsActionHandler(
        stateAccess = stateAccess,
        configStore = configStore,
        profileStore = profileStore,
        backupManager = backupManager,
        coroutineScope = viewModelScope,
        resolveEliteUnlocked = { lessons, testMode -> sessionRunner.resolveEliteUnlocked(lessons, testMode) }
    )

    private val progressWriter: TrainingProgressWriter = TrainingProgressWriter(
        snapshots = TrainingProgressSnapshotSource(
            core = _coreState,
            audio = audioCoordinator.audioState,
            story = storyRunner.stateFlow,
            vocab = vocabSprintRunner.vocabState,
            daily = dailyPracticeCoordinator.dailyState,
            flower = flowerRefresher.stateFlow,
            boss = bossOrchestrator.stateFlow,
            currentCursor = dailyPracticeCoordinator::getCursor
        ),
        progressTracker = progressTracker,
        masteryStore = masteryStore,
        normalizeEliteSpeeds = sessionRunner::normalizeEliteSpeeds,
        createBackup = settingsActionHandler::createProgressBackup,
        scope = viewModelScope
    )

    // ── Public helper accessors (UI uses vm.audio.X() instead of vm.X()) ─────
    /** Public accessor for audio operations. UI should use vm.audio.X() instead of vm.X() */
    val audio: AudioCoordinator get() = audioCoordinator
    /** Public accessor for training session operations. */
    val training: SessionRunner get() = sessionRunner
    /** Public accessor for boss battle operations. */
    val boss: BossOrchestrator get() = bossOrchestrator
    /** Public accessor for daily practice operations. */
    val daily: DailyPracticeCoordinator get() = dailyPracticeCoordinator
    /** Public accessor for vocab sprint operations. */
    val vocab: VocabSprintRunner get() = vocabSprintRunner
    /** Public accessor for story operations. */
    val story: StoryRunner get() = storyRunner
    /** Public accessor for bad sentence reporting. */
    val reports: BadSentenceHelper get() = badSentenceHelper
    /** Public accessor for settings operations. */
    val settings: SettingsActionHandler get() = settingsActionHandler
    /** Current session size for the settings screen. */
    val currentSessionSize: Int get() = sessionSize

    // ── Pomodoro operations ─────────────────────────────────────────────────

    fun startPomodoro(durationMinutes: Int) {
        pomodoroRecords.saveDuration(durationMinutes)
        _pomodoroRemainingSeconds.value = durationMinutes * 60
        pomodoroHelper.startPomodoro(durationMinutes)
        AuditLogger.getInstanceOrNull()?.pomodoroStart(
            durationMin = durationMinutes,
            lessonId = _coreState.value.navigation.selectedLessonId?.value ?: ""
        )
    }

    fun pausePomodoro() {
        pomodoroHelper.pausePomodoro()
        val pomo = _coreState.value.pomodoro
        val elapsedMs = (pomo.totalSeconds - _pomodoroRemainingSeconds.value).toLong() * 1000
        AuditLogger.getInstanceOrNull()?.pomodoroPause(elapsedMs)
    }

    fun resumePomodoro() {
        pomodoroHelper.resumePomodoro()
        val pomo = _coreState.value.pomodoro
        val elapsedMs = (pomo.totalSeconds - _pomodoroRemainingSeconds.value).toLong() * 1000
        AuditLogger.getInstanceOrNull()?.pomodoroResume(elapsedMs)
    }

    fun cancelPomodoro() {
        pomodoroHelper.cancelPomodoro()
        _pomodoroRemainingSeconds.value = 0
    }

    private fun onTrainingSessionCompleted() {
        pomodoroHelper.onTrainingSessionCompleted()
        val pomo = _coreState.value.pomodoro
        val cs = _coreState.value.cardSession
        if (pomo.isComplete) {
            val shown = (cs.correctCount - pomo.baselineCorrect).coerceAtLeast(0) +
                (cs.incorrectCount - pomo.baselineIncorrect).coerceAtLeast(0)
            val correct = (cs.correctCount - pomo.baselineCorrect).coerceAtLeast(0)
            val incorrect = (cs.incorrectCount - pomo.baselineIncorrect).coerceAtLeast(0)
            AuditLogger.getInstanceOrNull()?.pomodoroComplete(
                durationMin = pomo.selectedDurationMinutes,
                cardsShown = shown,
                correct = correct,
                incorrect = incorrect
            )
        }
    }

    fun rateCardDifficulty(rating: CardDifficultyRating) {
        pomodoroHelper.recordDifficultyRating(rating)
    }

    fun confirmPomodoroExit() {
        pomodoroHelper.cancelPomodoro()
    }

    fun dismissPomodoroExit() {
        pomodoroHelper.setShowExitConfirm(false)
    }

    fun onAppBackgrounded() {
        pomodoroHelper.onLifecycleStop()
        masteryStore.flush()
    }

    fun onAppForegrounded() {
        pomodoroHelper.onLifecycleStart()
    }

    // ── TTS playback for story narration ───────────────────────────────────────

    /**
     * Manually download TTS voice models for the background-vocab languages
     * (Italian + Russian) from the Settings screen, with visible progress.
     * Delegates to [AudioCoordinator.downloadTtsModels], which updates
     * `audioState.ttsDownloadState` and `audioState.ttsModelsReady`.
     */
    fun startTtsDownload() {
        audioCoordinator.downloadTtsModels(listOf("it", "ru"))
    }

    // ── Sound-pack (pre-rendered background-vocab audio) ──────────────────

    /** Refresh the selected pack's installed clip count on Settings entry. */
    fun refreshSoundPackInstalledCount() {
        soundPackTransfers.refreshInstalledCount(_coreState.value.navigation.activePackId?.value)
    }

    /** Import a sound-pack ZIP picked through the document picker. */
    fun importSoundPack(uri: Uri) {
        soundPackTransfers.import(
            uri, getApplication<Application>().contentResolver,
            _coreState.value.navigation.activePackId?.value
        )
    }

    /** Download the registered sound pack for the selected pack. */
    fun downloadSoundPack() {
        soundPackTransfers.download(_coreState.value.navigation.activePackId?.value)
    }

    /** Cancel the active transfer and reset displayed progress. */
    fun cancelSoundPackDownload() {
        soundPackTransfers.cancel()
    }

    /**
     * Play multilingual story with automatic language switching.
     * Use this for stories with Italian insertions marked with {it}...{/it}
     *
     * When [storyFile] and [packId] are provided, plays the pre-rendered Opus narration
     * clip for the chapter if one exists on disk (real-voice narration), otherwise
     * synthesizes the [content] via multilingual TTS.
     *
     * @param content Story content with language markers
     * @param defaultLanguageId Default language (e.g., "en" for English stories, "ru" for Russian)
     * @param storyFile Optional chapter story filename (e.g. "chapter_03.md") for narration lookup
     * @param packId Optional active pack id scoping the narration lookup
     */
    fun speakMultilingualStory(
        content: String,
        defaultLanguageId: String = "en",
        storyFile: String? = null,
        packId: String? = null
    ) {
        storyReader.speakMultilingual(content, defaultLanguageId, storyFile, packId)
    }

    fun stopStoryNarration() {
        storyReader.stop()
    }

    fun pauseStoryPlayback() {
        storyReader.pause()
    }

    fun resumeStoryPlayback() {
        storyReader.resume()
    }

    fun setStoryReader(chapterTitle: String, content: String, storyFile: String? = null) {
        storyReader.open(chapterTitle, content, storyFile)
    }

    fun clearStoryReader() {
        storyReader.clear()
    }

    init {
        runtimeOwnersReady = true
        AuditLogger.getInstanceOrNull()?.startWriter(viewModelScope)
        Log.d(logTag, "Update: duolingo sfx, prompt in speech UI, voice loop rules, stop resets progress")

        // All file I/O moved to a background thread to avoid blocking the main thread
        // on app startup (was 500ms–2s on weak tablets). UI shows a loading spinner
        // via TrainingUiState.isLoading until this coroutine completes.
        viewModelScope.launch(Dispatchers.IO) {
            val startup = startupLoader.load()
            val progress = startup.progress
            val config = startup.config
            eliteSizeMultiplier = config.eliteSizeMultiplier
            sessionSize = config.sessionSize
            cardProvider.setSubLessonSize(sessionSize)
            sessionRunner.setEliteSizeMultiplier(eliteSizeMultiplier)
            sessionRunner.setSubLessonSize(sessionSize)
            dailyPracticeCoordinator.setSessionSize(sessionSize)
            val bossLessonRewards = bossOrchestrator.parseBossRewards(progress.bossLessonRewards)
            val bossMegaRewards = bossOrchestrator.parseBossRewards(progress.bossMegaRewards)
            val restoredContent = startup.content
            val selectedLanguageId = restoredContent.languageId
            val lessons = restoredContent.lessons
            val normalizedEliteSpeeds = sessionRunner.normalizeEliteSpeeds(progress.eliteBestSpeeds)
            val initialActivePackId = restoredContent.packId

            // Phase 3 (3.5): stamp retroactive completions on the restore path
            // too — only selectPack recalculated before, so a cold start showed
            // 0 completed lessons for any pack whose last-card event never fired
            // (the quarantined BUG REPRO). Runs OFF-main: the hidden-card read
            // and the mastery writes stay off the UI thread (review blocker),
            // and the freshly stamped completions are already visible to the
            // flower/chapter refreshes below.
            if (initialActivePackId != null && selectedLanguageId != null && lessons.isNotEmpty()) {
                progressTracker.recalculateCompletionsExcludingHidden(
                    lessons = lessons,
                    languageId = selectedLanguageId,
                    hiddenCardIds = hiddenCardStore.getHiddenCardIds(),
                    packId = initialActivePackId.value
                )
            }

            val startupState = TrainingStartupState(
                content = restoredContent,
                languages = startup.languages,
                packs = startup.packs,
                progress = progress,
                config = config,
                profile = startup.profile,
                streak = startup.streak,
                lessonProgress = startup.lessonProgress,
                normalizedEliteSpeeds = normalizedEliteSpeeds,
                eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, config.testMode),
                eliteStepCount = eliteStepCount,
                badSentenceCount = startup.badSentenceCount,
                hintSessionOffset = Random.nextInt(0, 100)
            )

            withContext(Dispatchers.Main) {
                _coreState.update(startupState::apply)
                // Initialize feature-owned state from persisted progress
                bossOrchestrator.initRewards(bossLessonRewards, bossMegaRewards)
                rebindWordMasteryStore(initialActivePackId?.value)
                rebindVocabProgressStore(initialActivePackId?.value)
                vocabSprintRunner.updateMasteredCount(wordMasteryStore.getMasteredCount())
                dailyPracticeCoordinator.initializeCursor(progress.dailyCursor)
                refreshDrillVisibility()
                rebuildSchedules(filterLessonsForActivePack(lessons))
                restoreSelectedLessonCheckpointPosition()
                buildSessionCards()
                refreshFlowerStates()
                loadChapters()
                refreshProfileStats()
                refreshPomodoroHistory()
                if (_coreState.value.cardSession.sessionState == SessionState.ACTIVE && _coreState.value.cardSession.currentCard != null) {
                    sessionRunner.resumeTimer()
                    // Hydration resumes the checkpoint; it is not a new practiced answer.
                    if (_coreState.value.cardSession.inputMode == InputMode.VOICE) {
                        _coreState.update { it.copy(cardSession = it.cardSession.copy(voiceTriggerToken = it.cardSession.voiceTriggerToken + 1)) }
                    }
                }

                // TTS state collection (must run on main thread — checks model state)
                audioCoordinator.checkTtsModel()
                audioCoordinator.checkAllTtsModels()
                audioCoordinator.checkAsrModel()
                audioCoordinator.startBackgroundTtsDownload()
                // TEMP: Download Russian TTS model for multilingual story testing
                audioCoordinator.startTtsDownloadForLanguage("ru")
                audioCoordinator.startTtsStateCollection()
            }

            // Pre-build daily practice after the validated startup context is published.
            // Uses progress-based lesson, NOT selectedLessonId, so that browsing
            // locked lessons does not affect the daily practice session.
            val state = _coreState.value
            val packId = state.navigation.activePackId
            val langId = state.navigation.selectedLanguageId
            val progressInfo = resolveProgressLessonInfo()
            if (packId != null && langId != null && progressInfo != null) {
                val lessonId = progressInfo.first
                val lessonLevel = progressInfo.second
                dailyPracticeCoordinator.prebuildSession(
                    packId.value, langId.value, lessonId, lessonLevel, dailyPracticeCoordinator.getCursor()
                )
            }
        }
    }

    /**
     * Compute drill visibility for the current active pack.
     * Called whenever activePackId or selectedLanguageId changes.
     */
    private fun computeDrillVisibility(): Pair<Boolean, Boolean> {
        val packId = _coreState.value.navigation.activePackId
        val langId = _coreState.value.navigation.selectedLanguageId
        return if (packId != null && langId != null) {
            lessonStore.hasVerbDrill(packId.value, langId.value) to lessonStore.hasVocabDrill(packId.value, langId.value)
        } else {
            false to false
        }
    }

    /**
     * Apply drill visibility fields to the current navigation state.
     * Must be called after every navigation update that may change activePackId.
     */
    private fun refreshDrillVisibility() {
        val (verbDrill, vocabDrill) = computeDrillVisibility()
        val hasChapters = _coreState.value.navigation.activePackId
            ?.let { lessonStore.hasChapters(it.value) } ?: false
        _coreState.update {
            it.copy(navigation = it.navigation.copy(
                hasVerbDrill = verbDrill,
                hasVocabDrill = vocabDrill,
                activePackHasChapters = hasChapters
            ))
        }
    }

    private fun syncAudioLanguage(languageId: String) {
        audioCoordinator.ttsModelManager.currentLanguageId = languageId
        audioCoordinator.checkTtsModel()
        // Native ASR rejects a language change before its engine is ready.
        if (audioCoordinator.asrEngine?.isReady == true) {
            audioCoordinator.asrEngine.setLanguage(languageId)
        }
    }

    fun selectLanguage(languageId: String) {
        val target = contextResolver.forLanguage(LanguageId(languageId))
        sessionRunner.pauseTimer()
        vocabSession = emptyList()
        sessionRunner.clearAllCards()
        val lessons = target.lessons
        // No pack selected yet — user must pick a pack before lessons load.
        // Previously auto-selected first pack — caused pack confusion.
        rebindWordMasteryStore(null)
        rebindVocabProgressStore(null)
        _coreState.update {
            it.resetAllSessionState().copy(
                navigation = target.navigationFrom(it.navigation),
                elite = it.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, it.cardSession.testMode))
            )
        }
        // Replace the previous language's streak display with this language's data.
        val streakData = streakStore.getCurrentStreak(languageId)
        _coreState.update {
            it.copy(cardSession = it.cardSession.copy(
                currentStreak = streakData.currentStreak,
                longestStreak = streakData.longestStreak,
                todayFireCount = streakData.todayFireCount,
                hintSessionOffset = Random.nextInt(0, 100)
            ))
        }
        // Reset feature-owned state
        bossOrchestrator.resetStateKeepRewards()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        vocabSprintRunner.updateMasteredCount(wordMasteryStore.getMasteredCount())
        dailyPracticeCoordinator.resetState()
        dailyPracticeCoordinator.initializeCursor()
        refreshDrillVisibility()
        rebuildSchedules(filterLessonsForActivePack(lessons))
        buildSessionCards()
        refreshFlowerStates()
        loadChapters()
        refreshProfileStats()
        refreshPomodoroHistory()
        saveProgress()
        syncAudioLanguage(languageId)
    }

    fun selectLesson(lessonId: String, packId: String? = null) {
        val target = contextResolver.forLesson(lessonId, packId, _coreState.value.navigation) ?: return
        sessionRunner.pauseTimer()
        vocabSession = emptyList()
        sessionRunner.clearAllCards()

        val resolvedPackId = target.packId?.value
        rebindWordMasteryStore(resolvedPackId)
        rebindVocabProgressStore(resolvedPackId)
        rebuildSchedules(target.scheduleLessons())

        // Calculate active sub-lesson index based on completed count
        val typedLessonId = com.alexpo.grammermate.data.LessonId(lessonId)
        val schedule = lessonSchedules[typedLessonId]
        val subLessons = schedule?.subLessons.orEmpty()
        val mastery = resolvedPackId?.let { masteryStore.getForPack(it, lessonId) }
        val hiddenIds = hiddenCardStore.getHiddenCardIds()
        val completedCount = progressTracker.calculateCompletedSubLessons(
            subLessons = subLessons,
            mastery = mastery,
            lessonId = typedLessonId,
            lessons = target.lessons,
            hiddenCardIds = hiddenIds
        )
        // If lesson is mastery-completed (completedAtMs set), clamp completedCount to total
        // so LessonRoadmapScreen shows CompletionCard instead of sub-lesson grid with locks.
        // This handles the case where uniqueCardShows meets the threshold but some
        // sub-lessons have cards not yet shown (sequential check stops early).
        val isMasteryCompleted = mastery?.completedAtMs != null
        val effectiveCompletedCount = if (isMasteryCompleted) {
            subLessons.size
        } else {
            completedCount
        }
        val resume = progressTracker.resolveLessonCheckpointResume(
            packId = resolvedPackId,
            lessonId = typedLessonId,
            subLessons = subLessons,
            completedSubLessons = effectiveCompletedCount
        )
        val checkpoint = resume.checkpoint
        val nextActiveIndex = resume.activeSubLessonIndex

        _coreState.update {
            val reset = it.resetSessionState()
            reset.copy(
                navigation = target.navigationFrom(it.navigation).copy(mode = TrainingMode.LESSON),
                cardSession = reset.cardSession.copy(
                    activeSubLessonIndex = nextActiveIndex,
                    completedSubLessonCount = effectiveCompletedCount,
                    currentIndex = checkpoint?.currentIndex ?: 0,
                    correctCount = checkpoint?.correctCount ?: 0,
                    incorrectCount = checkpoint?.incorrectCount ?: 0,
                    activeTimeMs = checkpoint?.activeTimeMs ?: 0L,
                    currentCard = null,
                    hintSessionOffset = Random.nextInt(0, 100)
                )
            )
        }
        _sessionTimerMs.value = checkpoint?.activeTimeMs ?: 0L
        // Reset feature-owned state for session change
        bossOrchestrator.resetStateKeepRewards()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        vocabSprintRunner.updateMasteredCount(wordMasteryStore.getMasteredCount())
        dailyPracticeCoordinator.resetState()
        dailyPracticeCoordinator.initializeCursor()
        refreshDrillVisibility()
        buildSessionCards()
        refreshFlowerStates()
        loadChapters()
        refreshBadSentenceCount()
        saveProgress()
    }

    fun selectChapter(chapter: Chapter) {
        _coreState.update {
            it.copy(navigation = it.navigation.copy(selectedChapter = chapter))
        }
    }

    /**
     * Find the chapter that contains a given lesson ID.
     * Used when navigating back from LESSON to CHAPTER_LESSONS to restore selectedChapter.
     */
    fun findChapterForLesson(lessonId: String): Chapter? {
        return _coreState.value.chapters.firstOrNull { chapter ->
            lessonId in chapter.lessons
        }
    }

    fun getLessonsForChapter(chapter: Chapter): List<Lesson> {
        val packId = _coreState.value.navigation.activePackId?.value ?: return emptyList()
        val allNavLessons = _coreState.value.navigation.lessons
        if (allNavLessons.isEmpty()) {
            Log.w(logTag, "getLessonsForChapter: navLessons is empty for pack $packId — lessons not loaded yet?")
        }
        return chapter.lessons.mapNotNull { lessonId ->
            allNavLessons.firstOrNull { it.id.value == lessonId }
        }
    }

    fun selectPack(packId: String) {
        val target = contextResolver.forPack(packId, _coreState.value.navigation.selectedLessonId) ?: return
        val packLanguageId = target.languageId?.value ?: return
        val previousLanguageId = _coreState.value.navigation.selectedLanguageId?.value
        // Cancel any active daily session before switching packs
        if (_coreState.value.daily.dailySession.active) {
            cancelDailySession()
        }

        // Sync language to match the pack's language (fixes voice input language)
        if (packLanguageId != previousLanguageId) {
            syncAudioLanguage(packLanguageId)
        }

        // Load pack-scoped lessons and update state
        // Migration: recalculate lesson completions excluding hidden/bad cards
        if (target.lessons.isNotEmpty()) {
            val hiddenIds = hiddenCardStore.getHiddenCardIds()
            progressTracker.recalculateCompletionsExcludingHidden(
                lessons = target.lessons,
                languageId = requireNotNull(target.languageId),
                hiddenCardIds = hiddenIds,
                packId = packId
            )
        }

        // Publish one coherent language/pack/content snapshot before schedules rebuild.
        _coreState.update { it.copy(navigation = target.navigationFrom(it.navigation)) }
        if (target.selectedLessonId != null) {
            selectLesson(target.selectedLessonId.value, packId)
        } else {
            sessionRunner.pauseTimer()
            sessionRunner.clearAllCards()
            _coreState.update { it.resetSessionState() }
            // Drill-only pack — set activePackId without selecting a lesson
            rebindWordMasteryStore(packId)
            rebindVocabProgressStore(packId)
            vocabSprintRunner.updateMasteredCount(wordMasteryStore.getMasteredCount())
            dailyPracticeCoordinator.resetState()
            dailyPracticeCoordinator.initializeCursor()
            refreshDrillVisibility()
            loadChapters()
            refreshPackTiles()
            refreshBadSentenceCount()
            saveProgress()
        }
    }

    /**
     * Clear the active pack to return to pack selection screen.
     */
    fun clearActivePack() {
        // Cancel any active daily session before clearing pack
        if (_coreState.value.daily.dailySession.active) {
            cancelDailySession()
        }

        sessionRunner.pauseTimer()
        sessionRunner.clearAllCards()
        rebindWordMasteryStore(null)
        rebindVocabProgressStore(null)
        vocabSprintRunner.updateMasteredCount(wordMasteryStore.getMasteredCount())
        val target = contextResolver.withoutPack(_coreState.value.navigation)
        _coreState.update {
            it.resetSessionState().copy(navigation = target.navigationFrom(it.navigation))
        }
        loadChapters()
        refreshPackTiles()
        dailyPracticeCoordinator.resetState()
        dailyPracticeCoordinator.initializeCursor()
        refreshDrillVisibility()
        saveProgress()
    }

    fun selectMode(mode: TrainingMode) {
        sessionRunner.pauseTimer()
        sessionRunner.clearAllCards()
        vocabSession = emptyList()
        _coreState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(mode = mode))
        }
        // Reset feature-owned state for session change
        bossOrchestrator.resetStateKeepRewards()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        buildSessionCards()
        refreshBadSentenceCount()
        saveProgress()
    }

    fun submitAnswer(): SubmitResult {
        val beforeState = _coreState.value.cardSession
        val beforeCard = beforeState.currentCard
        val beforeInputMode = beforeState.inputMode
        val beforeScreenMode = beforeState.screenMode
        val (result, events) = sessionRunner.submitAnswer()
        handleSessionEvents(events)

        // Orchestrator: handle cross-module actions based on result
        if (result.needsBossFinish) {
            val bossState = bossOrchestrator.stateFlow.value
            updateBossProgress(bossState.bossTotal)
            finishBoss()
        }
        if (result.needsSubLessonComplete) {
            progressWriter.requestBackup()
        }

        // The self-produced ladder rule lives in ProgressTracker; the ViewModel
        // gathers only the cross-feature facts (boss flow, daily session flag)
        // and the session-local reveal fact.
        progressTracker.recordSelfProducedIfEarned(
            accepted = result.accepted,
            card = beforeCard,
            inputMode = beforeInputMode,
            answerWasRevealed = beforeState.sessionState == SessionState.HINT_SHOWN ||
                beforeState.answerText != null,
            bossActive = bossOrchestrator.stateFlow.value.bossActive,
            dailySessionActive = isDailySession()
        )

        // Track daily card practice for cursor advancement.
        // Only VOICE and KEYBOARD answers count — WORD_BANK does NOT
        // (single rule owner: InputMode.countsTowardMastery).
        // This hook is the sole counting mechanism for DAILY_TRANSLATE and
        // DAILY_VERBS sessions that run through SessionRunner.
        if (result.accepted && isDailySession()) {
            if (beforeInputMode.countsTowardMastery) {
                val blockType = when (beforeScreenMode) {
                    com.alexpo.grammermate.data.TrainingScreenMode.DAILY_TRANSLATE -> DailyBlockType.TRANSLATE
                    com.alexpo.grammermate.data.TrainingScreenMode.DAILY_VERBS -> DailyBlockType.VERBS
                    else -> return SubmitResult(result.accepted, result.hintShown)
                }
                recordDailyCardPracticed(blockType, beforeCard as? SentenceCard)
                // Persist verb card progress so buildVerbBlock doesn't repeat the same cards
                if (blockType == DailyBlockType.VERBS) {
                    if (beforeCard is com.alexpo.grammermate.data.VerbDrillCard) {
                        dailyPracticeCoordinator.persistDailyVerbProgress(beforeCard)
                    }
                }
            }
        }

        // A linear completion must persist after its mastery/daily hooks and
        // after the backup request. Other answer paths save via events.
        if (result.needsSaveProgress) saveProgress()

        Log.d(logTag, "Answer submitted: accepted=${result.accepted}")
        return SubmitResult(result.accepted, result.hintShown)
    }

    fun nextCard(triggerVoice: Boolean = false) {
        val events = sessionRunner.nextCard(triggerVoice)
        handleSessionEvents(events)
    }

    /** Navigate forward via arrow button: pause-first, then advance. Leaves PAUSED. */
    fun navigateNext() = handleSessionEvents(sessionRunner.navigateNext())

    /** Navigate backward via arrow button: pause-first, then go back. Leaves PAUSED. */
    fun navigatePrev() = handleSessionEvents(sessionRunner.navigatePrev())

    fun togglePause() = handleSessionEvents(sessionRunner.togglePause())

    fun pauseSession() = handleSessionEvents(sessionRunner.pauseSession())

    fun finishSession() {
        audioCoordinator.stopAsr()
        if (bossOrchestrator.stateFlow.value.bossActive) {
            finishBoss()
            return
        }
        val (_, events) = sessionRunner.finishSession()
        handleSessionEvents(events)
    }

    fun showAnswer() = handleSessionEvents(sessionRunner.showAnswer())

    /**
     * Start a verb drill session with cards from [VerbDrillViewModel].
     * Loads cards into SessionRunner, sets screenMode to VERB_DRILL,
     * and activates the session. Navigating to TRAINING after calling
     * this will render the cards in TrainingScreen with verb drill styling.
     */
    fun startVerbDrillSession(cards: List<com.alexpo.grammermate.data.VerbDrillCard>) {
        val events = sessionRunner.startCardSession(cards, com.alexpo.grammermate.data.TrainingScreenMode.VERB_DRILL)
        handleSessionEvents(events)
        AuditLogger.getInstanceOrNull()?.verbDrillStart(cardCount = cards.size, tense = "")
    }

    /**
     * Exit the verb drill session and reset to normal mode.
     * Call when the user exits the verb drill from TrainingScreen.
     */
    fun exitVerbDrillSession() {
        audioCoordinator.stopAsr()
        val events = sessionRunner.exitCardSession()
        handleSessionEvents(events)
    }

    /**
     * Replace cards in the active Verb Drill card session.
     * Used by "Ещё" button to load next batch without leaving TrainingScreen.
     */
    fun replaceVerbDrillCards(cards: List<com.alexpo.grammermate.data.VerbDrillCard>) {
        val events = sessionRunner.replaceCards(cards)
        handleSessionEvents(events)
        AuditLogger.getInstanceOrNull()?.verbDrillMore(newCardCount = cards.size)
    }

    /** Whether the current session is in VERB_DRILL screen mode. */
    fun isVerbDrillSession(): Boolean {
        return _coreState.value.cardSession.screenMode == com.alexpo.grammermate.data.TrainingScreenMode.VERB_DRILL
    }

    /**
     * Start a daily translation session with [SessionCard] cards.
     * Sets screenMode to DAILY_TRANSLATE and activates the session.
     */
    fun startDailyTranslateSession(cards: List<com.alexpo.grammermate.data.SessionCard>) {
        val events = sessionRunner.startCardSession(cards, com.alexpo.grammermate.data.TrainingScreenMode.DAILY_TRANSLATE)
        handleSessionEvents(events)
        AuditLogger.getInstanceOrNull()?.dailyBlockStart("translate")
    }

    /**
     * Start a daily verbs session with [SessionCard] cards.
     * Sets screenMode to DAILY_VERBS and activates the session.
     */
    fun startDailyVerbsSession(cards: List<com.alexpo.grammermate.data.SessionCard>) {
        val events = sessionRunner.startCardSession(cards, com.alexpo.grammermate.data.TrainingScreenMode.DAILY_VERBS)
        handleSessionEvents(events)
        AuditLogger.getInstanceOrNull()?.dailyBlockStart("verbs")
    }

    /**
     * Exit the daily session and reset to normal mode.
     * Call when the user finishes or exits a daily practice block.
     */
    fun exitDailySession() {
        audioCoordinator.stopAsr()
        val events = sessionRunner.exitCardSession()
        handleSessionEvents(events)
    }

    /** Whether the current session is in a daily practice screen mode. */
    fun isDailySession(): Boolean {
        val mode = _coreState.value.cardSession.screenMode
        return mode == com.alexpo.grammermate.data.TrainingScreenMode.DAILY_TRANSLATE ||
               mode == com.alexpo.grammermate.data.TrainingScreenMode.DAILY_VERBS
    }

    /** Set the route to navigate back to when the training session ends. */
    fun setReturnTo(route: String) {
        _coreState.update { it.copy(cardSession = it.cardSession.copy(returnTo = route)) }
    }

    /** Set return route to lesson screen (sub-lesson list) after session completion. */
    fun setReturnToForLesson() {
        // Always return to the lesson/sub-lesson screen — Back on that screen
        // already navigates correctly to CHAPTER_LESSONS for chapter packs.
        setReturnTo("lesson")
    }

    fun importLesson(uri: Uri) {
        val languageId = _coreState.value.navigation.selectedLanguageId ?: return
        val (lesson, errors) = lessonStore.importFromUriWithErrors(languageId.value, uri, getApplication<Application>().contentResolver)

        if (errors.isNotEmpty()) {
            _coreState.update {
                it.copy(
                    parseErrors = errors,
                    showParseWarning = true,
                    parseUserMessage = getUserMessageForParseErrors(errors)
                )
            }
        }

        refreshLessons(lesson.id.value)
    }

    fun importLessonPack(uri: Uri) {
        vocabSession = emptyList()
        try {
            val pack = lessonStore.importPackFromUri(uri, getApplication<Application>().contentResolver)
            val target = requireNotNull(contextResolver.forPack(pack.packId.value)) {
                "Imported pack ${pack.packId.value} is unavailable"
            }
            sessionRunner.pauseTimer()
            sessionRunner.clearAllCards()
            rebindWordMasteryStore(target.packId?.value)
            rebindVocabProgressStore(target.packId?.value)
            _coreState.update {
                it.resetAllSessionState().copy(
                    navigation = target.navigationFrom(it.navigation).copy(
                        languages = lessonStore.getLanguagesWithPacks(),
                        installedPacks = lessonStore.getInstalledPacks(),
                        mode = TrainingMode.LESSON
                    ),
                    elite = it.elite.copy(
                        eliteUnlocked = sessionRunner.resolveEliteUnlocked(target.lessons, it.cardSession.testMode)
                    )
                )
            }
            // Reset feature-owned state
            bossOrchestrator.resetStateKeepRewards()
            storyRunner.resetState()
            vocabSprintRunner.resetState()
            dailyPracticeCoordinator.resetState()
            dailyPracticeCoordinator.initializeCursor()
            refreshDrillVisibility()
            rebuildSchedules(target.scheduleLessons())
            buildSessionCards()
            loadChapters()
            refreshPackTiles()
            refreshFlowerStates()
            refreshProfileStats()
            refreshBadSentenceCount()
            saveProgress()
            if (audioCoordinator.ttsModelManager.currentLanguageId != pack.languageId.value) {
                syncAudioLanguage(pack.languageId.value)
            }
        } catch (e: Exception) {
            Log.e(logTag, "Lesson pack import failed", e)
        }
    }

    fun dismissParseWarning() {
        _coreState.update {
            it.copy(
                parseErrors = emptyList(),
                showParseWarning = false,
                parseUserMessage = null
            )
        }
    }

    fun confirmPartialImport() {
        // Parse errors are already logged by LessonStore
        // User has chosen to proceed with partial import
        _coreState.update {
            it.copy(
                parseErrors = emptyList(),
                showParseWarning = false,
                parseUserMessage = null
            )
        }
    }

    private fun getUserMessageForParseErrors(errors: List<ParseError>): String {
        return when {
            errors.isEmpty() -> "Import completed successfully."
            else -> {
                val errorSummary = errors.joinToString("\n") { it.toUserMessage() }
                "Import completed with ${errors.size} error(s):\n$errorSummary"
            }
        }
    }

    fun resetAndImportLesson(uri: Uri) {
        val languageId = _coreState.value.navigation.selectedLanguageId ?: return
        lessonStore.deleteAllLessons(languageId.value)
        val lesson = lessonStore.importFromUri(languageId.value, uri, getApplication<Application>().contentResolver)
        refreshLessons(lesson.id.value)
    }

    fun deleteLesson(lessonId: String) {
        val languageId = _coreState.value.navigation.selectedLanguageId ?: return
        lessonStore.deleteLesson(languageId.value, lessonId)
        val selected = if (_coreState.value.navigation.selectedLessonId?.value == lessonId) null else _coreState.value.navigation.selectedLessonId
        refreshLessons(selected?.value)
    }

    fun createEmptyLesson(title: String) {
        val languageId = _coreState.value.navigation.selectedLanguageId ?: return
        val lesson = lessonStore.createEmptyLesson(languageId.value, title)
        refreshLessons(lesson.id.value)
    }

    fun addLanguage(name: String) {
        val language = lessonStore.addLanguage(name)
        val target = contextResolver.forLanguage(language.id)
        vocabSession = emptyList()
        sessionRunner.pauseTimer()
        sessionRunner.clearAllCards()
        rebindWordMasteryStore(null)
        rebindVocabProgressStore(null)
        _coreState.update {
            it.resetAllSessionState().copy(
                navigation = target.navigationFrom(it.navigation).copy(
                    languages = lessonStore.getLanguagesWithPacks(),
                    installedPacks = lessonStore.getInstalledPacks(),
                    mode = TrainingMode.LESSON
                ),
                elite = it.elite.copy(
                    eliteUnlocked = sessionRunner.resolveEliteUnlocked(target.lessons, it.cardSession.testMode)
                )
            )
        }
        // Reset feature-owned state
        bossOrchestrator.resetStateKeepRewards()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        dailyPracticeCoordinator.resetState()
        dailyPracticeCoordinator.initializeCursor()
        refreshDrillVisibility()
        rebuildSchedules(target.scheduleLessons())
        buildSessionCards()
        loadChapters()
        refreshPackTiles()
        refreshProfileStats()
        refreshPomodoroHistory()
        saveProgress()
        syncAudioLanguage(language.id.value)
    }

    fun deleteAllLessons() {
        val languageId = _coreState.value.navigation.selectedLanguageId ?: return
        lessonStore.deleteAllLessons(languageId.value)
        refreshLessons(null)
        _coreState.update { it.copy(navigation = it.navigation.copy(installedPacks = lessonStore.getInstalledPacks())) }
    }

    /**
     * Reset progress for the current language/pack only.
     * Other language packs are NOT affected.
     */
    fun resetLanguageProgress() {
        val state = _coreState.value
        val languageId = state.navigation.selectedLanguageId?.value ?: return
        val packId = state.navigation.activePackId?.value
        handleSettingsResults(settingsActionHandler.resetLanguageProgress(getApplication(), languageId, packId))
    }

    fun deletePack(packId: String) {
        val pack = lessonStore.getInstalledPacks().firstOrNull { it.packId.value == packId } ?: return
        val languageId = pack.languageId
        lessonStore.removeInstalledPackData(packId)
        if (_coreState.value.navigation.activePackId?.value == packId ||
            _coreState.value.navigation.selectedLanguageId == languageId
        ) {
            refreshLessons(null)
        }
        _coreState.update { it.copy(navigation = it.navigation.copy(installedPacks = lessonStore.getInstalledPacks())) }
        refreshPackTiles()
    }

    fun toggleTestMode() = handleSettingsResults(settingsActionHandler.toggleTestMode())

    fun resumeFromSettings() {
        val state = _coreState.value
        if (state.cardSession.isReviewMode) {
            // Rebuild review cards with updated hidden cards after settings change
            val hintLevel = state.cardSession.hintLevel
            val hiddenIds = hiddenCardStore.getHiddenCardIds()
            val cards = cardProvider.buildReviewCards(
                lessons = state.navigation.lessons,
                selectedLessonId = state.navigation.selectedLessonId,
                hiddenCardIds = hiddenIds
            )
            if (cards.isEmpty()) return
            handleSessionEvents(sessionRunner.startReview(cards, hintLevel))
        } else {
            handleSessionEvents(sessionRunner.resumeFromSettings())
        }
    }

    fun selectSubLesson(index: Int) = handleSessionEvents(sessionRunner.selectSubLesson(index))

    /**
     * Compute what "next" action is available after a sub-lesson completes.
     * Called from GrammarMateApp when subLessonFinishedToken changes.
     *
     * Logic:
     * 1. If returnTo is DAILY_PRACTICE or VERB_DRILL → NONE (special flows handle their own nav)
     * 2. If completedSubLessonCount < subLessonCount → NEXT_SUB_LESSON
     * 3. If pack has chapters, find next lesson in current chapter → NEXT_LESSON
     * 4. If no chapters, find next lesson in pack lesson list → NEXT_LESSON
     * 5. Otherwise → NONE
     */
    private fun computeCompletionNextAction(): CompletionNextAction {
        val state = _coreState.value
        val returnTo = state.cardSession.returnTo

        // Daily practice and verb drill have their own completion flows
        if (returnTo == "daily_practice" || returnTo == "verb_drill") {
            return CompletionNextAction.NONE
        }

        // Check if more sub-lessons remain in current lesson
        val completed = state.cardSession.completedSubLessonCount
        val total = state.cardSession.subLessonCount
        if (completed < total) {
            return CompletionNextAction.NEXT_SUB_LESSON
        }

        // Check if more lessons remain
        val packId = state.navigation.activePackId?.value ?: return CompletionNextAction.NONE
        val hasChapters = lessonStore.hasChapters(packId)

        if (hasChapters) {
            val chapter = state.navigation.selectedChapter ?: return CompletionNextAction.NONE
            val currentLessonId = state.navigation.selectedLessonId?.value ?: return CompletionNextAction.NONE
            val lessonsInChapter = chapter.lessons
            val currentIdx = lessonsInChapter.indexOf(currentLessonId)
            if (currentIdx >= 0 && currentIdx < lessonsInChapter.lastIndex) {
                return CompletionNextAction.NEXT_LESSON
            }
        } else {
            val lessons = state.navigation.lessons
            val currentLessonId = state.navigation.selectedLessonId
            val currentIdx = lessons.indexOfFirst { it.id == currentLessonId }
            if (currentIdx >= 0 && currentIdx < lessons.lastIndex) {
                return CompletionNextAction.NEXT_LESSON
            }
        }

        return CompletionNextAction.NONE
    }

    /**
     * Start a lesson review session with the given difficulty level.
     * Loads all lesson cards (shuffled, filtered by hidden), applies [hintLevel],
     * and starts an ACTIVE training session. Mastery tracking works normally.
     */
    fun startReview(hintLevel: HintLevel) {
        val state = _coreState.value
        val hiddenIds = hiddenCardStore.getHiddenCardIds()
        val cards = cardProvider.buildReviewCards(
            lessons = state.navigation.lessons,
            selectedLessonId = state.navigation.selectedLessonId,
            hiddenCardIds = hiddenIds
        )
        if (cards.isEmpty()) return
        val events = sessionRunner.startReview(cards, hintLevel)
        handleSessionEvents(events)
    }

    fun openEliteStep(index: Int) = handleSessionEvents(sessionRunner.openEliteStep(index))

    fun cancelEliteSession() = handleSessionEvents(sessionRunner.cancelEliteSession())

    // ── Daily Practice ────────────────────────────────────────────────────

    @Suppress("UNUSED_PARAMETER")
    suspend fun startDailyPractice(lessonLevel: Int): Boolean {
        Log.d(logTag, "DailyPractice: VM.startDailyPractice level=$lessonLevel")
        // Save lesson selection before daily practice so we can restore on exit
        preDailySelectedLessonId = _coreState.value.navigation.selectedLessonId
        val progressInfo = resolveProgressLessonInfo()
        AuditLogger.getInstanceOrNull()?.dailyStart(
            lessonId = progressInfo?.first ?: _coreState.value.navigation.selectedLessonId?.value ?: "",
            level = lessonLevel
        )
        val started = dailyPracticeCoordinator.startDailyPractice(
            resolveProgressLessonInfo = { resolveProgressLessonInfo() },
            onStoreFirstSessionCardIds = { lessonId, sentenceIds, verbIds ->
                storeFirstSessionCardIds(lessonId, sentenceIds, verbIds)
            }
        )
        Log.d(logTag, "DailyPractice: VM.startDailyPractice result=$started")
        return started
    }

    /**
     * Store the first session of the day's card IDs in the cursor state.
     * This allows Repeat to reconstruct the exact same cards even after restart.
     */
    private fun storeFirstSessionCardIds(lessonId: String, sentenceIds: List<String>, verbIds: List<String>) {
        val updatedCursor = progressTracker.storeFirstSessionCardIds(
            currentCursor = dailyPracticeCoordinator.getCursor(),
            sentenceIds = sentenceIds,
            verbIds = verbIds,
            lessonId = lessonId
        )
        dailyPracticeCoordinator.updateCursor(updatedCursor)
        saveProgress()
    }

    /**
     * Advance the daily cursor offsets after a session is built.
     * Uses dailyPracticeCoordinator.advanceDailyCursor() which correctly updates
     * BOTH sentenceOffset AND verbOffset (unlike progressTracker.advanceCursor()
     * which only updates sentenceOffset).
     */
    private fun advanceCursor(sentenceCount: Int) {
        val s = _coreState.value
        val langId = s.navigation.selectedLanguageId ?: return
        val advanced = dailyPracticeCoordinator.advanceDailyCursor(
            sentenceCount = sentenceCount,
            languageId = langId.value
        )
        dailyPracticeCoordinator.updateCursor(advanced)
    }

    suspend fun repeatDailyPractice(lessonLevel: Int): Boolean {
        Log.d(logTag, "DailyPractice: VM.repeatDailyPractice level=$lessonLevel")
        // Save lesson selection before daily practice so we can restore on exit
        if (preDailySelectedLessonId == null) {
            preDailySelectedLessonId = _coreState.value.navigation.selectedLessonId
        }
        val progressInfo = resolveProgressLessonInfo()
        AuditLogger.getInstanceOrNull()?.dailyStart(
            lessonId = progressInfo?.first ?: _coreState.value.navigation.selectedLessonId?.value ?: "",
            level = lessonLevel
        )
        val started = dailyPracticeCoordinator.repeatDailyPractice(
            lessonLevel = lessonLevel,
            resolveProgressLessonInfo = { resolveProgressLessonInfo() }
        )
        Log.d(logTag, "DailyPractice: VM.repeatDailyPractice result=$started")
        return started
    }

    /**
     * Record that a card in the current daily practice block was practiced via VOICE or KEYBOARD.
     * Called from submitAnswer's daily hook (which only runs for non-WORD_BANK modes).
     * Used to track per-block completion for cursor advancement.
     */
    private fun recordDailyCardPracticed(blockType: DailyBlockType, sentenceCard: SentenceCard? = null) {
        // Caller already guards on result.accepted, so accepted=true here.
        dailyPracticeCoordinator.recordDailyCardPracticed(
            blockType = blockType,
            sentenceCard = sentenceCard,
            resolveCardLessonId = { card -> resolveCardLessonId(card) },
            accepted = true
        )
    }

    fun cancelDailySession() {
        Log.d(logTag, "DailyPractice: VM.cancelDailySession")
        val sentenceCount = dailyPracticeCoordinator.cancelDailySession()
        AuditLogger.getInstanceOrNull()?.dailyExit(sentenceCount ?: 0)
        // Orchestrator: if coordinator signals cursor advancement, delegate to ProgressTracker
        if (sentenceCount != null) {
            advanceCursor(sentenceCount)
        }
        // Restore lesson selection that was active before daily practice
        preDailySelectedLessonId?.let { savedLessonId ->
            _coreState.update { it.copy(navigation = it.navigation.copy(selectedLessonId = savedLessonId)) }
            preDailySelectedLessonId = null
        }
    }

    fun openVocabSprint(resume: Boolean = false) {
        when (val result = vocabSprintRunner.openSprint(resume)) {
            is VocabResult.ResetBoss -> bossOrchestrator.resetStateKeepRewards()
            is VocabResult.SaveAndBackup -> { /* handled in submitVocabAnswer */ }
            is VocabResult.None -> {}
        }
    }

    fun completeStory(phase: StoryPhase, allCorrect: Boolean) {
        when (val result = storyRunner.completeStory(phase, allCorrect)) {
            is StoryResult.SaveAndBackup -> {
                progressWriter.requestBackup()
                saveProgress()
            }
            is StoryResult.None -> {}
        }
    }

    fun submitVocabAnswer(inputOverride: String? = null) {
        val result = vocabSprintRunner.submitAnswer(inputOverride)
        when (result.sound) {
            is VocabSoundResult.PlaySuccess -> audioCoordinator.playSuccessSound()
            is VocabSoundResult.PlayError -> audioCoordinator.playErrorSound()
            is VocabSoundResult.None -> {}
        }
        when (result.action) {
            is VocabResult.SaveAndBackup -> {
                progressWriter.requestBackup()
                saveProgress()
            }
            is VocabResult.ResetBoss -> bossOrchestrator.resetStateKeepRewards()
            is VocabResult.None -> {}
        }
    }


    fun startBossLesson() {
        AuditLogger.getInstanceOrNull()?.bossStart(
            type = "LESSON",
            lessonId = _coreState.value.navigation.selectedLessonId?.value ?: ""
        )
        handleBossCommands(bossOrchestrator.startBossLesson())
    }

    fun startBossMega() {
        AuditLogger.getInstanceOrNull()?.bossStart(
            type = "MEGA",
            lessonId = _coreState.value.navigation.selectedLessonId?.value ?: ""
        )
        handleBossCommands(bossOrchestrator.startBossMega())
    }

    fun finishBoss() {
        val bossState = bossOrchestrator.stateFlow.value
        val bossType = bossState.bossType?.name ?: bossState.bossLastType?.name ?: ""
        val reward = bossState.bossReward?.name ?: "NONE"
        AuditLogger.getInstanceOrNull()?.bossFinish(
            type = bossType,
            result = reward,
            lessonId = _coreState.value.navigation.selectedLessonId?.value ?: ""
        )
        handleBossCommands(bossOrchestrator.finishBoss())
        viewModelScope.launch { _navigationEvents.send(NavigationEvent.BossSessionFinished) }
    }

    fun clearBossRewardMessage() = handleBossCommands(bossOrchestrator.clearBossRewardMessage())

    private fun updateBossProgress(progress: Int) = handleBossCommands(bossOrchestrator.updateBossProgress(progress))

    /**
     * Resolve the user's actual progress lesson based on mastery data.
     * This is independent of [selectedLessonId] — it uses the flower/mastery
     * state to find the highest lesson with any progress, then returns the
     * NEXT lesson (the one the user should be practicing).
     *
     * Falls back to the first lesson if no progress exists.
     * Returns (lessonId, lessonLevel) where lessonLevel is 1-based.
     */
    private fun resolveProgressLessonInfo(): Pair<String, Int>? {
        val s = _coreState.value
        val langId = s.navigation.selectedLanguageId ?: return null
        return progressTracker.resolveProgressLessonInfo(
            activePackId = s.navigation.activePackId,
            selectedLanguageId = langId,
            activePackLessonIds = s.navigation.activePackLessonIds,
            lessons = s.navigation.lessons,
            dailyCursor = dailyPracticeCoordinator.getCursor()
        )
    }

    /**
     * Public helper for UI layer to get the progress-based lesson level
     * without depending on [selectedLessonId].
     */
    fun getProgressLessonLevel(): Int {
        val s = _coreState.value
        val langId = s.navigation.selectedLanguageId ?: return 1
        return progressTracker.getProgressLessonLevel(
            activePackId = s.navigation.activePackId,
            selectedLanguageId = langId,
            activePackLessonIds = s.navigation.activePackLessonIds,
            lessons = s.navigation.lessons,
            dailyCursor = dailyPracticeCoordinator.getCursor()
        )
    }

    // ── Audio delegations to AudioCoordinator ─────────────────────────────

    fun setRuTextScale(scale: Float) {
        audioCoordinator.setRuTextScale(scale)
        configStore.save(configStore.load().copy(ruTextScale = scale.coerceIn(1.0f, 2.0f)))
    }

    fun setSessionSize(size: Int) {
        val safe = size.coerceIn(3, 1000)
        sessionSize = safe
        cardProvider.setSubLessonSize(safe)
        sessionRunner.setSubLessonSize(safe)
        dailyPracticeCoordinator.setSessionSize(safe)
        configStore.save(configStore.load().copy(sessionSize = safe))
    }

    fun startOfflineRecognition() {
        audioCoordinator.startOfflineRecognition { result ->
            sessionRunner.onInputChanged(result)
            // Don't auto-submit — let user review recognized text and submit manually
        }
    }

    override fun onCleared() {
        AuditLogger.getInstanceOrNull()?.stopWriter()
        saveProgress()
        masteryStore.flush()
        audioCoordinator.release()
        super.onCleared()
    }

    /**
     * Определить к какому уроку принадлежит карточка.
     * Важно для Mixed-режима где карточки могут быть из разных уроков.
     */
    private fun resolveCardLessonId(card: SentenceCard): String? {
        val s = _coreState.value
        return progressTracker.resolveCardLessonId(
            card = card,
            selectedLessonId = s.navigation.selectedLessonId,
            lessons = s.navigation.lessons
        )?.value
    }

    /**
     * Записать показ карточки для отслеживания прогресса освоения.
     * Word Bank НЕ учитывается для формирования навыка (роста цветка).
     * Учитывается только голосовой ввод и клавиатура.
     */
    private fun recordCardShowForMastery(card: com.alexpo.grammermate.data.SessionCard) {
        // VerbDrillCards have their own progress tracking in VerbDrillViewModel
        if (card is com.alexpo.grammermate.data.VerbDrillCard) return
        val s = _coreState.value
        val langId = s.navigation.selectedLanguageId ?: return
        progressTracker.recordCardShowForMastery(
            card = card,
            bossActive = bossOrchestrator.stateFlow.value.bossActive,
            inputMode = s.cardSession.inputMode,
            selectedLanguageId = langId,
            lessons = s.navigation.lessons,
            selectedLessonId = s.navigation.selectedLessonId
        )
    }

    /**
     * Record a card encounter in MasteryStore and update encounterCount in state.
     * Called each time a card is shown to the user (navigated to or session started).
     */
    private fun recordCardEncounter(card: SentenceCard) {
        val s = _coreState.value
        val packId = s.navigation.activePackId?.value ?: return
        val lessonId = resolveCardLessonId(card) ?: return
        val count = masteryStore.recordCardEncounterForPack(packId, lessonId, card.id)
        _coreState.update {
            it.copy(cardSession = it.cardSession.copy(encounterCount = count))
        }
    }


    /**
     * Re-scope wordMasteryStore to the given packId.
     * Must be called whenever activePackId changes so that mastery reads/writes
     * go to the pack-scoped file instead of the legacy global file.
     */
    private fun rebindWordMasteryStore(packId: String?) {
        wordMasteryStore = container.wordMasteryStore(packId)
    }

    /**
     * Re-scope vocabProgressStore to the given packId.
     * Must be called whenever activePackId changes so that vocab progress reads/writes
     * go to the pack-scoped file. VocabSprintRunner uses a lambda provider so it
     * automatically picks up the new store reference.
     */
    private fun rebindVocabProgressStore(packId: String?) {
        vocabProgressStore = container.vocabProgressStore(packId)
    }

    /**
     * Refresh the vocab mastered count from the store.
     * Called when returning from VocabDrill to reflect updated mastery.
     */
    fun refreshVocabMasteryCount() {
        val count = wordMasteryStore.getMasteredCount()
        vocabSprintRunner.updateMasteredCount(count)
    }

    /**
     * Закрывает сообщение о streak
     */
    fun dismissStreakMessage() {
        _coreState.update {
            it.copy(cardSession = it.cardSession.copy(streakMessage = null))
        }
    }

    fun saveProgressNow() = handleSettingsResults(settingsActionHandler.saveProgressNow())

    /**
     * Restore user progress from backup folder.
     */
    fun restoreBackup(backupUri: android.net.Uri) {
        val wasActive = _coreState.value.cardSession.sessionState == SessionState.ACTIVE
        sessionRunner.pauseTimer()
        progressWriter.drainTimerWrites()
        val results = progressRestorer.restoreBackup(backupUri)
        if (results.isEmpty()) {
            if (wasActive) sessionRunner.resumeTimer()
            return
        }
        finishProgressRestore(results)
    }

    private fun finishProgressRestore(results: List<ProgressResult>) {
        sessionRunner.clearTransientAfterRestore()

        // BackupManager invalidates factory-owned stores. These two references
        // live in this ViewModel and need the same file-replacement barrier.
        packLessonProgressStore.invalidateCache()
        packDailyCursorStore.invalidateCache()
        val restored = progressStore.load()
        val selectedLanguage = restored.languageId.takeIf { it.value.isNotEmpty() }
            ?: LanguageId("en")
        val target = restored.activePackId?.let { contextResolver.forPack(it.value, restored.lessonId?.let(::LessonId)) }
            ?: contextResolver.forLanguage(selectedLanguage)
        _coreState.update { it.copy(navigation = target.navigationFrom(it.navigation)) }
        rebindWordMasteryStore(target.packId?.value)
        rebindVocabProgressStore(target.packId?.value)
        dailyPracticeCoordinator.resetState()
        dailyPracticeCoordinator.initializeCursor()
        handleProgressResults(results)
        refreshDrillVisibility()
        loadChapters()
        refreshPackTiles()
        refreshBadSentenceCount()
        refreshProfileStats()
        refreshVocabMasteryCount()
        if (_coreState.value.cardSession.sessionState == SessionState.ACTIVE) {
            sessionRunner.resumeTimer()
        }
    }

    fun reloadFromDisk() {
        sessionRunner.pauseTimer()
        progressWriter.drainTimerWrites()
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { progressRestorer.reloadFromDisk() }
            finishProgressRestore(results)
        }
    }

    fun flagBadSentence() {
        val card = _coreState.value.cardSession.currentCard
        if (card != null) {
            AuditLogger.getInstanceOrNull()?.badSentenceFlag(
                cardId = card.id,
                ru = card.promptRu,
                it = card.acceptedAnswers.joinToString(" / ")
            )
        }
        when (val result = badSentenceHelper.flagBadSentence()) {
            is BadSentenceResult.SkipToNextCard -> sessionRunner.skipToNextCard()
            is BadSentenceResult.None -> {}
        }
    }

    fun clearPackBadSentences() {
        val packId = _coreState.value.navigation.activePackId?.value ?: return
        badSentenceStore.clearPack(packId)
        hiddenCardStore.clearPack(packId)
        _coreState.update {
            it.copy(cardSession = it.cardSession.copy(badSentenceCount = 0))
        }
    }

    fun hideCurrentCard() {
        val card = _coreState.value.cardSession.currentCard
        if (card != null) {
            AuditLogger.getInstanceOrNull()?.cardHidden(cardId = card.id)
        }
        when (val result = badSentenceHelper.hideCurrentCard()) {
            is BadSentenceResult.SkipToNextCard -> sessionRunner.skipToNextCard()
            is BadSentenceResult.None -> {}
        }
    }

    // ── Session event handling (private methods) ────────────────────────────

    private fun handleSessionEvents(events: List<SessionEvent>) {
        for (event in events) {
            when (event) {
                is SessionEvent.SaveProgress -> saveProgress()
                is SessionEvent.RefreshFlowerStates -> refreshFlowerStates()
                is SessionEvent.UpdateStreak -> updateStreak()
                is SessionEvent.UpdateStreakForType -> updateStreak(event.type)
                is SessionEvent.BuildSessionCards -> buildSessionCards()
                is SessionEvent.PlaySuccess -> audioCoordinator.playSuccessSound()
                is SessionEvent.PlayError -> audioCoordinator.playErrorSound()
                is SessionEvent.RecordCardShow -> {
                    recordCardShowForMastery(event.card)
                    (event.card as? SentenceCard)?.let { recordCardEncounter(it) }
                }
                is SessionEvent.MarkSubLessonCardsShown -> markSubLessonCardsShown(event.cards)
                is SessionEvent.CheckAndMarkLessonCompleted -> checkAndMarkLessonCompleted()
                is SessionEvent.CalculateCompletedSubLessons -> {
                    val hiddenIds = hiddenCardStore.getHiddenCardIds()
                    val count = progressTracker.calculateCompletedSubLessons(
                        subLessons = event.subLessons,
                        mastery = event.mastery,
                        lessonId = event.lessonId?.let { com.alexpo.grammermate.data.LessonId(it) },
                        lessons = _coreState.value.navigation.lessons,
                        hiddenCardIds = hiddenIds
                    )
                    event.callback(count)
                }
                is SessionEvent.GetMastery -> event.callback(
                    _coreState.value.navigation.activePackId?.let { pid ->
                        masteryStore.getForPack(pid.value, event.lessonId)
                    }
                )
                is SessionEvent.GetSchedule -> event.callback(lessonSchedules[com.alexpo.grammermate.data.LessonId(event.lessonId)])
                is SessionEvent.RebuildSchedules -> rebuildSchedules(filterLessonsForActivePack(event.lessons))
                is SessionEvent.AdvanceBossProgress -> {
                    val (advanceResult, bossCommands) = bossOrchestrator.advanceBossProgressOnNextCard(event.nextIndex, event.totalCards)
                    handleBossCommands(bossCommands)

                    // Apply boss pause if reward threshold was crossed
                    if (advanceResult.rewardMessageChanged) {
                        _coreState.update { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED)) }
                    }
                }
                is SessionEvent.Composite -> handleSessionEvents(event.events)
            }
        }
    }

    private fun markSubLessonCardsShown(cards: List<com.alexpo.grammermate.data.SessionCard>) {
        val s = _coreState.value
        val langId = s.navigation.selectedLanguageId ?: return
        progressTracker.markSubLessonCardsShown(
            cards = cards,
            inputMode = s.cardSession.inputMode,
            selectedLessonId = s.navigation.selectedLessonId,
            selectedLanguageId = langId,
            lessons = s.navigation.lessons,
            packId = s.navigation.activePackId?.value
        )

        // Update chapter progress when cards are shown (mastery changes)
        s.navigation.selectedLessonId?.let { lessonId ->
            updateChapterProgress(lessonId.value)
        }
    }

    private fun checkAndMarkLessonCompleted() {
        val s = _coreState.value
        val langId = s.navigation.selectedLanguageId ?: return
        val lessonId = s.navigation.selectedLessonId
        val packId = s.navigation.activePackId?.value
        val mastery = lessonId?.let { lid ->
            packId?.let { pid -> masteryStore.getForPack(pid, lid.value) }
        }
        val uniqueCardShows = mastery?.uniqueCardShows ?: 0
        val lessonCards = lessonId?.let { lid ->
            s.navigation.lessons.find { it.id == lid }?.cards
        }.orEmpty()
        val hiddenIds = hiddenCardStore.getHiddenCardIds()
        val hiddenCardCount = progressTracker.hiddenCardCountInLesson(lessonCards, lessonId?.value, hiddenIds, packId)
        val totalCardsInLesson = lessonCards.size
        progressTracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = s.cardSession.completedSubLessonCount,
            selectedLessonId = s.navigation.selectedLessonId,
            selectedLanguageId = langId,
            uniqueCardShows = uniqueCardShows,
            totalCardsInLesson = totalCardsInLesson,
            hiddenCardCount = hiddenCardCount,
            packId = packId
        )
        AuditLogger.getInstanceOrNull()?.lessonComplete(
            lessonId = lessonId?.value ?: "",
            uniqueShows = uniqueCardShows,
            totalCards = totalCardsInLesson
        )

        // Update chapter progress when lesson completion status changes
        s.navigation.selectedLessonId?.let { lessonId ->
            updateChapterProgress(lessonId.value)
        }
    }
    private fun refreshFlowerStates() {
        flowerRefresher.refreshFlowerStates()
        // Pack tiles derive from the same mastery data as the flower display;
        // mastery change is the shared trigger. Equality-skip inside keeps
        // this cheap when nothing visible changed.
        refreshPackTiles()
    }
    private fun updateStreak(forcedPracticeType: PracticeType? = null) {
        // TASK-051: Streak only counts when session is "completed" (засчитанная УЕ).
        // Must have enough correct answers to cover non-bad cards.
        if (!isSessionCompleted()) return

        val languageId = _coreState.value.navigation.selectedLanguageId ?: return
        val practiceType = forcedPracticeType ?: PracticeType.TRANSLATION
        val (updatedStreak, isNewFire) = streakManager.recordPracticeTypeCompletion(languageId.value, practiceType)
        val fireCount = updatedStreak.todayFireCount
        if (isNewFire && updatedStreak.currentStreak > 0) {
            val message = streakManager.getCelebrationMessage(updatedStreak.currentStreak, fireCount)
            _coreState.update {
                it.copy(cardSession = it.cardSession.copy(currentStreak = updatedStreak.currentStreak, longestStreak = updatedStreak.longestStreak, streakMessage = message, streakCelebrationToken = it.cardSession.streakCelebrationToken + 1, todayFireCount = fireCount))
            }
        } else {
            _coreState.update {
                it.copy(cardSession = it.cardSession.copy(currentStreak = updatedStreak.currentStreak, longestStreak = updatedStreak.longestStreak, todayFireCount = fireCount))
            }
        }
    }

    /**
     * Reload streak data from the store into _coreState.
     * Called when returning to HomeScreen from standalone drill ViewModels
     * (VocabDrill, VerbDrill) that record streak to disk but don't share _coreState.
     * Without this, the streak display on HomeScreen is stale until app restart.
     */
    fun refreshStreakFromStore() {
        val languageId = _coreState.value.navigation.selectedLanguageId?.value ?: return
        val streakData = streakStore.getCurrentStreak(languageId)
        _coreState.update {
            it.copy(cardSession = it.cardSession.copy(
                currentStreak = streakData.currentStreak,
                longestStreak = streakData.longestStreak,
                todayFireCount = streakData.todayFireCount
            ))
        }
    }

    /**
     * Check if the current session is "completed" (засчитанная УЕ).
     * A session counts when correctCount >= (sessionSize - badSentenceCount),
     * i.e. all non-bad cards were answered correctly.
     */
    private fun isSessionCompleted(): Boolean {
        val state = _coreState.value.cardSession
        val badCount = _coreState.value.cardSession.badSentenceCount
        val sessionTotal = state.subLessonTotal.takeIf { it > 0 } ?: sessionSize
        val totalRequired = (sessionTotal - badCount).coerceAtLeast(1)
        return state.correctCount >= totalRequired
    }

    private fun saveProgress(): Unit = progressWriter.save()

    private fun saveProgressFromTimer(): Unit = progressWriter.saveFromTimer()

    /** Restore a saved position after schedules exist; loading is never a card encounter. */
    private fun restoreSelectedLessonCheckpointPosition() {
        val state = _coreState.value
        val lessonId = state.navigation.selectedLessonId ?: return
        val packId = state.navigation.activePackId?.value ?: return
        val subLessons = lessonSchedules[lessonId]?.subLessons.orEmpty()
        val mastery = masteryStore.getForPack(packId, lessonId.value)
        val completed = progressTracker.calculateCompletedSubLessons(
            subLessons, mastery, lessonId, state.navigation.lessons, hiddenCardStore.getHiddenCardIds()
        )
        val index = progressTracker.resolveLessonCheckpointResume(packId, lessonId, subLessons, completed)
            .activeSubLessonIndex
        _coreState.update { it.copy(cardSession = it.cardSession.copy(activeSubLessonIndex = index)) }
        _sessionTimerMs.value = state.cardSession.activeTimeMs
    }

    private fun buildSessionCards() {
        if (bossOrchestrator.stateFlow.value.bossActive || _coreState.value.elite.eliteActive) return
        val state = _coreState.value
        val hiddenIds = hiddenCardStore.getHiddenCardIds()
        val lessons = state.navigation.lessons
        val mastery = state.navigation.selectedLessonId?.let { lid ->
            state.navigation.activePackId?.let { pid ->
                masteryStore.getForPack(pid.value, lid.value)
            }
        }
        val activePackId = state.navigation.activePackId?.value
        val result = cardProvider.buildSessionCards(
            lessons = lessons,
            mode = state.navigation.mode,
            selectedLessonId = state.navigation.selectedLessonId,
            schedules = lessonSchedules,
            activeSubLessonIndex = state.cardSession.activeSubLessonIndex,
            hiddenCardIds = hiddenIds,
            mastery = mastery,
            // Слоты повторения в смешанных блоках заполняются по актуальному
            // mastery — обе шкалы кривой забывания считаются здесь, на лету.
            masteryOf = { lessonId ->
                activePackId?.let { masteryStore.getForPack(it, lessonId) }
            },
            totalEffortCards = activePackId?.let { masteryStore.totalEffortCardsForPack(it) } ?: 0
        )
        sessionRunner.setSessionCards(result.cards)
        subLessonTotal = result.subLessonTotal
        subLessonCount = result.subLessonCount
        val safeIndex = _coreState.value.cardSession.currentIndex.coerceIn(0, (result.cards.size - 1).coerceAtLeast(0))
        val card = result.cards.getOrNull(safeIndex)
        if (card == null && state.cardSession.sessionState == SessionState.ACTIVE) {
            sessionRunner.pauseTimer()
        }
        _coreState.update {
            it.copy(cardSession = it.cardSession.copy(currentIndex = safeIndex, currentCard = card, sessionState = if (card == null) SessionState.PAUSED else state.cardSession.sessionState, subLessonTotal = result.subLessonTotal, subLessonCount = result.subLessonCount, activeSubLessonIndex = result.activeSubLessonIndex, completedSubLessonCount = result.completedSubLessonCount, subLessonTypes = result.subLessonTypes))
        }
        // Sync word bank with the newly built card so the toggle button appears immediately.
        // Without this, sessions started via selectLesson() are ACTIVE with currentCard set
        // but wordBankWords stays empty — the user never sees the word bank toggle until
        // they manually pause and resume (which triggers updateWordBank via startSession).
        sessionRunner.updateWordBank()
    }

    private fun rebuildSchedules(lessons: List<Lesson>) {
        lessonSchedules = cardProvider.buildSchedules(lessons)
    }

    /**
     * Filter lessons to only include those belonging to the active pack.
     * Prevents MixedReviewScheduler from pulling review cards across packs.
     */
    private fun filterLessonsForActivePack(lessons: List<Lesson>): List<Lesson> {
        val packLessonIds = _coreState.value.navigation.activePackLessonIds
        return if (packLessonIds != null && packLessonIds.isNotEmpty()) {
            lessons.filter { it.id.value in packLessonIds }
        } else {
            lessons
        }
    }

    // -- BossCommand handler --
    private fun handleBossCommands(commands: List<BossCommand>) {
        for (command in commands) {
            when (command) {
                is BossCommand.PauseTimer -> sessionRunner.pauseTimer()
                is BossCommand.ResumeTimer -> sessionRunner.resumeTimer()
                is BossCommand.SaveProgress -> saveProgress()
                is BossCommand.BuildSessionCards -> buildSessionCards()
                is BossCommand.RefreshFlowerStates -> refreshFlowerStates()
                is BossCommand.ResetBoss -> bossOrchestrator.resetStateKeepRewards()
                is BossCommand.ResetDailySession -> {
                    dailyPracticeCoordinator.resetState()
                }
                is BossCommand.ResetStory -> storyRunner.resetState()
                is BossCommand.ResetVocabSprint -> vocabSprintRunner.resetState()
                is BossCommand.Composite -> handleBossCommands(command.commands)
            }
        }
    }

    // -- SettingsCallbacks (now private methods) --
    private fun refreshLessons(selectedLessonId: String?) {
        sessionRunner.pauseTimer()
        sessionRunner.clearAllCards()
        vocabSession = emptyList()
        val nav = _coreState.value.navigation
        val target = selectedLessonId?.let {
            contextResolver.forLesson(it, null, nav)
        } ?: nav.activePackId?.let {
            contextResolver.forPack(it.value, selectedLessonId?.let(::LessonId))
        } ?: contextResolver.withoutPack(nav)
        rebindWordMasteryStore(target.packId?.value)
        rebindVocabProgressStore(target.packId?.value)
        val languages = lessonStore.getLanguagesWithPacks()
        val packs = lessonStore.getInstalledPacks()
        _coreState.update {
            it.resetSessionState().copy(
                navigation = target.navigationFrom(it.navigation).copy(
                    languages = languages,
                    installedPacks = packs
                ),
                elite = it.elite.copy(
                    eliteUnlocked = sessionRunner.resolveEliteUnlocked(target.lessons, it.cardSession.testMode)
                )
            )
        }
        // Reset feature-owned state for session change
        bossOrchestrator.resetStateKeepRewards()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        dailyPracticeCoordinator.resetState()
        dailyPracticeCoordinator.initializeCursor()
        refreshDrillVisibility()
        rebuildSchedules(target.scheduleLessons())
        buildSessionCards()
        loadChapters() // reload chapter progress from disk (cleared after reset)
        // Progress resets clear mastery on disk — derived data must follow
        // (flowers/tiles/profile show pre-reset values otherwise).
        refreshFlowerStates()
        refreshProfileStats()
        refreshBadSentenceCount()
        saveProgress()
    }
    private fun resetStores(app: Application) {
        progressTracker.resetStores(app)
        container.clearCache() // invalidate all cached stores including vocabProgressStore
        packDailyCursorStore.invalidateCache() // prevent stale cursor reads after disk deletion
        // Chapter progress is derived live from mastery (Phase 3, 3.3) —
        // resetting mastery removes the underlying data; nothing extra to clear.
    }
    private fun resetStoresForLanguage(app: Application, languageId: String) {
        progressTracker.resetStoresForLanguage(app, languageId)
        container.clearCache() // invalidate all cached stores including vocabProgressStore
        packDailyCursorStore.invalidateCache() // prevent stale cursor reads after disk deletion
        // Chapter progress is derived live from mastery (Phase 3, 3.3).
    }
    private fun resetDrillFiles(app: Application) {
        progressTracker.resetDrillFiles(app)
        container.clearCache()
        rebindWordMasteryStore(_coreState.value.navigation.activePackId?.value)
        rebindVocabProgressStore(_coreState.value.navigation.activePackId?.value)
    }
    private fun resetDrillFilesForPack(app: Application, packId: String) {
        progressTracker.resetDrillFilesForPack(app, packId)
        container.clearCache()
        rebindWordMasteryStore(_coreState.value.navigation.activePackId?.value)
        rebindVocabProgressStore(_coreState.value.navigation.activePackId?.value)
    }
    private fun clearWordMastery() {
        wordMasteryStore.saveAll(emptyMap())
        vocabSprintRunner.updateMasteredCount(0)
    }
    private fun resetDailyState() = dailyPracticeCoordinator.resetAllDailyState()

    private fun resetStreak() {
        streakStore.resetAll()
        _coreState.update {
            it.copy(cardSession = it.cardSession.copy(
                currentStreak = 0,
                longestStreak = 0,
                streakMessage = null,
                streakCelebrationToken = 0,
                todayFireCount = 0
            ))
        }
    }

    private fun resetStreakForLanguage(languageId: String) {
        streakStore.resetForLanguage(languageId)
        val currentLang = _coreState.value.navigation.selectedLanguageId
        if (currentLang?.value == languageId) {
            _coreState.update {
                it.copy(cardSession = it.cardSession.copy(
                    currentStreak = 0,
                    longestStreak = 0,
                    streakMessage = null,
                    streakCelebrationToken = 0,
                    todayFireCount = 0
                ))
            }
        }
    }

    // -- SettingsResult handler --
    private fun handleSettingsResults(results: List<SettingsResult>) {
        for (result in results) {
            when (result) {
                is SettingsResult.RefreshLessons -> refreshLessons(result.selectedLessonId)
                is SettingsResult.ResetStores -> {
                    // The settings handler's state resets cleared the typed
                    // answer together with the session; the input flow needs
                    // the same wipe.
                    _inputText.value = ""
                    bossOrchestrator.resetState()
                    resetStores(result.app)
                }
                is SettingsResult.ResetStoresForLanguage -> {
                    _inputText.value = ""
                    bossOrchestrator.resetState()
                    resetStoresForLanguage(result.app, result.languageId)
                }
                is SettingsResult.ResetDrillFiles -> resetDrillFiles(result.app)
                is SettingsResult.ResetDrillFilesForPack -> resetDrillFilesForPack(result.app, result.packId)
                is SettingsResult.ClearWordMastery -> clearWordMastery()
                is SettingsResult.ResetDailyState -> {
                    _inputText.value = ""
                    resetDailyState()
                }
                is SettingsResult.SetForceBackup -> { progressWriter.requestBackup() }
                is SettingsResult.SaveProgress -> saveProgress()
                is SettingsResult.ResetStreak -> resetStreak()
                is SettingsResult.ResetStreakForLanguage -> resetStreakForLanguage(result.languageId)
                is SettingsResult.None -> {}
            }
        }
    }

    // -- ProgressResult handler --
    private fun handleProgressResults(results: List<ProgressResult>) {
        for (result in results) {
            when (result) {
                is ProgressResult.HydrateBossRewards -> bossOrchestrator.initRewards(
                    result.lessonRewards, result.megaRewards
                )
                // Restore may resolve a saved pack to its manifest language after
                // ProgressRestorer loaded the legacy language field. Build from
                // the published context, which is also what the deck reads.
                is ProgressResult.RebuildSchedules -> rebuildSchedules(
                    filterLessonsForActivePack(_coreState.value.navigation.lessons)
                )
                is ProgressResult.BuildSessionCards -> {
                    restoreSelectedLessonCheckpointPosition()
                    buildSessionCards()
                }
                is ProgressResult.RefreshFlowerStates -> refreshFlowerStates()
                is ProgressResult.NormalizeEliteSpeeds -> {
                    val normalized = sessionRunner.normalizeEliteSpeeds(result.speeds)
                    result.callback(normalized)
                }
                is ProgressResult.ResolveEliteUnlocked -> {
                    val unlocked = sessionRunner.resolveEliteUnlocked(result.lessons, result.testMode)
                    result.callback(unlocked)
                }
                is ProgressResult.ParseBossRewards -> {
                    val parsed = bossOrchestrator.parseBossRewards(result.rewardMap)
                    result.callback(parsed)
                }
                is ProgressResult.None -> {}
            }
        }
    }

    // ── Grammar Story Roadmap Methods ───────────────────────────────────────────

    /**
     * Get chapter cards for the current pack with progress and status.
     * Returns an empty list if the pack has no chapters.
     */
    private fun getChapterCards(): List<ChapterCardUi> {
        return chapterRoadmap.getChapterCards()
    }

    /**
     * Load story content from a story file with automatic language detection.
     * Returns null if the file doesn't exist or cannot be read.
     *
     * This method uses the chapter ID to automatically detect the appropriate
     * story file based on the UI language setting (Russian/English).
     *
     * Language detection logic:
     * - If UI language is "ru", prefer Russian versions (*_original.md)
     * - If UI language is "en" or "system", prefer English versions
     * - Falls back to available version if preferred doesn't exist
     *
     * @param storyFile The story filename from chapter manifest (can be null)
     * @return Story content in the appropriate language, or null if not found
     */
    fun loadStoryContent(storyFile: String?): String? {
        return storyReader.loadContent(storyFile)
    }

    /**
     * Get the first incomplete lesson ID in a chapter.
     * Returns null if all lessons are completed or chapter is empty.
     */
    private fun getFirstIncompleteLesson(chapter: com.alexpo.grammermate.data.Chapter): String? {
        return chapterRoadmap.getFirstIncompleteLesson(chapter)
    }

    /**
     * Load chapters for the current pack into TrainingUiState.
     * Call this when the app starts or when switching packs.
     */
    fun loadChapters() {
        chapterRoadmap.loadChapters()
    }

    /**
     * Update chapter progress for a specific lesson.
     * Call this when lesson mastery changes (card practiced, lesson completed).
     *
     * @param lessonId The lesson ID that was updated
     */
    private fun updateChapterProgress(lessonId: String) {
        chapterRoadmap.updateChapterProgress(lessonId)
    }

    fun getCompletedLessonIds(chapter: Chapter): Set<String> {
        return chapterRoadmap.getCompletedLessonIds(chapter)
    }
}


