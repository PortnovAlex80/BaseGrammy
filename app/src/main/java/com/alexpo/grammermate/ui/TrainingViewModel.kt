package com.alexpo.grammermate.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import java.io.File
import com.alexpo.grammermate.AppContainer
import com.alexpo.grammermate.GrammarMateApplication
import com.alexpo.grammermate.data.GrammarChipStore
import com.alexpo.grammermate.data.SubmitResult
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.ParseError
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.StoryPhase
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VocabEntry
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.data.CefrCalculator
import com.alexpo.grammermate.data.CompletionNextAction
import com.alexpo.grammermate.data.PracticeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

import com.alexpo.grammermate.feature.boss.BossBattleRunner
import com.alexpo.grammermate.feature.boss.BossCommand
import com.alexpo.grammermate.feature.boss.BossOrchestrator
import com.alexpo.grammermate.feature.daily.DailyPracticeCoordinator
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import com.alexpo.grammermate.feature.progress.BadSentenceHelper
import com.alexpo.grammermate.feature.progress.BadSentenceResult
import com.alexpo.grammermate.feature.progress.ChapterProgressCalculator
import com.alexpo.grammermate.feature.progress.FlowerRefresher
import com.alexpo.grammermate.feature.progress.ProgressResult
import com.alexpo.grammermate.feature.progress.ProgressRestorer
import com.alexpo.grammermate.feature.progress.ProgressTracker
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.feature.training.CardProvider
import com.alexpo.grammermate.feature.training.SessionEvent
import com.alexpo.grammermate.feature.training.SessionRunner
import com.alexpo.grammermate.feature.training.StoryResult
import com.alexpo.grammermate.feature.training.StoryRunner
import com.alexpo.grammermate.feature.training.WordBankGenerator
import com.alexpo.grammermate.feature.vocab.VocabResult
import com.alexpo.grammermate.feature.vocab.VocabSoundResult
import com.alexpo.grammermate.feature.vocab.VocabSprintRunner
import com.alexpo.grammermate.shared.SettingsActionHandler
import com.alexpo.grammermate.shared.SettingsResult
import com.alexpo.grammermate.shared.audio.AudioCoordinator
import com.alexpo.grammermate.feature.pomodoro.PomodoroHelper
import com.alexpo.grammermate.data.PomodoroHistoryEntry
import com.alexpo.grammermate.data.PomodoroHistoryStore
import com.alexpo.grammermate.data.PomodoroSessionStats
import com.alexpo.grammermate.data.PomodoroSettingsStore
import com.alexpo.grammermate.data.CardDifficultyRating
import com.alexpo.grammermate.data.PackLessonProgressStore
import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.ChapterProgress

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
    private val vocabProgressStore = container.vocabProgressStore
    private var wordMasteryStore = container.wordMasteryStore(null)
    private val packLessonProgressStore = container.packLessonProgressStore
    private val packDailyCursorStore = container.packDailyCursorStore()
    private val backupManager = container.backupManager
    private val profileStore = container.profileStore
    private val _coreState = MutableStateFlow(TrainingUiState(isLoading = true))

    // ── High-frequency timer flows (separate from main state for performance) ──
    private val _sessionTimerMs = MutableStateFlow(0L)
    val sessionTimerMs: StateFlow<Long> = _sessionTimerMs.asStateFlow()

    private val _pomodoroRemainingSeconds = MutableStateFlow(0)
    val pomodoroRemainingSeconds: StateFlow<Int> = _pomodoroRemainingSeconds.asStateFlow()

    // ── Shared stateAccess — single instance for all helpers ──────────────
    private val stateAccess = object : TrainingStateAccess {
        override val uiState: StateFlow<TrainingUiState> get() = this@TrainingViewModel.uiState
        override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
            _coreState.update(transform)
        }
        override fun saveProgress() = this@TrainingViewModel.saveProgress()
    }

    // ── Feature instances (declared before uiState combine chain) ──────────

    private val answerValidator = AnswerValidator()
    private val vocabSprintRunner = VocabSprintRunner(
        stateAccess = stateAccess,
        lessonStore = lessonStore,
        vocabProgressStore = vocabProgressStore,
        answerValidator = answerValidator
    )

    private var vocabSession: List<VocabEntry>
        get() = vocabSprintRunner.vocabSession
        set(value) { vocabSprintRunner.vocabSession = value }
    private var subLessonTotal: Int = 0
    private var subLessonCount: Int = 0
    private var lessonSchedules: Map<com.alexpo.grammermate.data.LessonId, LessonSchedule> = emptyMap()
    private var forceBackupOnSave: Boolean = false

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
        getMastery = { lessonId, langId -> masteryStore.get(lessonId, langId) },
        getSchedule = { lessonId -> lessonSchedules[com.alexpo.grammermate.data.LessonId(lessonId)] },
        calculateCompletedSubLessons = { subLessons, mastery, lessonId ->
            progressTracker.calculateCompletedSubLessons(
                subLessons = subLessons,
                mastery = mastery,
                lessonId = lessonId?.let { com.alexpo.grammermate.data.LessonId(it) },
                lessons = _coreState.value.navigation.lessons
            )
        },
        onTimerSaveProgress = { saveProgress() },
        sessionTimerMsSink = { ms -> _sessionTimerMs.value = ms }
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
        parseBossRewards = { rewardMap -> bossOrchestrator.parseBossRewards(rewardMap) }
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
        configStore = configStore
    )

    private val pomodoroSettingsStore = PomodoroSettingsStore(getApplication<Application>())
    private val pomodoroHistoryStore = PomodoroHistoryStore(getApplication<Application>())
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
            savePomodoroHistory(stats, remainingSeconds, totalSeconds)
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
    /** Current UI language setting for the settings screen selector. */
    val currentUiLanguage: String get() = configStore.load().uiLanguage
    /** Current session size for the settings screen. */
    val currentSessionSize: Int get() = sessionSize

    // ── Pomodoro operations ─────────────────────────────────────────────────

    fun startPomodoro(durationMinutes: Int) {
        pomodoroSettingsStore.save(durationMinutes)
        _pomodoroRemainingSeconds.value = durationMinutes * 60
        pomodoroHelper.startPomodoro(durationMinutes)
    }

    fun pausePomodoro() {
        pomodoroHelper.pausePomodoro()
    }

    fun resumePomodoro() {
        pomodoroHelper.resumePomodoro()
    }

    fun cancelPomodoro() {
        pomodoroHelper.cancelPomodoro()
        _pomodoroRemainingSeconds.value = 0
    }

    fun onTrainingSessionCompleted() {
        pomodoroHelper.onTrainingSessionCompleted()
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

    fun getPomodoroLastDuration(): Int {
        return pomodoroSettingsStore.load()
    }

    fun getPomodoroHistoryForSelectedLanguage() =
        _coreState.value.navigation.selectedLanguageId?.let { pomodoroHistoryStore.loadAll(it.value) } ?: emptyList()

    // ── TTS playback for story narration ───────────────────────────────────────

    fun speakStoryText(text: String, languageId: String = "en") {
        audioCoordinator.onTtsSpeak(text, languageId = languageId)
    }

    /**
     * Play multilingual story with automatic language switching.
     * Use this for stories with Italian insertions marked with {it}...{/it}
     *
     * @param content Story content with language markers
     * @param defaultLanguageId Default language (e.g., "en" for English stories, "ru" for Russian)
     */
    fun speakMultilingualStory(content: String, defaultLanguageId: String = "en") {
        audioCoordinator.playMultilingualStory(content, defaultLanguageId)
    }

    fun stopStoryNarration() {
        audioCoordinator.stopTts()
    }

    fun pauseStoryPlayback() {
        audioCoordinator.pauseStoryPlayback()
    }

    fun resumeStoryPlayback() {
        audioCoordinator.resumeStoryPlayback()
    }

    fun setStoryReader(chapterTitle: String, content: String) {
        _coreState.update {
            it.copy(
                storyReaderChapterTitle = chapterTitle,
                storyReaderContent = content
            )
        }
    }

    fun clearStoryReader() {
        _coreState.update {
            it.copy(
                storyReaderChapterTitle = null,
                storyReaderContent = ""
            )
        }
    }

    private fun savePomodoroHistory(
        stats: PomodoroSessionStats,
        remainingSeconds: Int,
        totalSeconds: Int
    ) {
        val state = _coreState.value
        val languageId = state.navigation.selectedLanguageId?.value ?: return
        pomodoroHistoryStore.append(
            PomodoroHistoryEntry(
                id = "${languageId}_${stats.completedAtMs}",
                languageId = languageId,
                packId = state.navigation.activePackId?.value,
                lessonId = state.navigation.selectedLessonId?.value,
                completedAtMs = stats.completedAtMs,
                durationMinutes = stats.durationMinutes,
                totalSeconds = totalSeconds,
                remainingSeconds = remainingSeconds,
                cardsShown = stats.cardsShown,
                cardsCorrect = stats.cardsCorrect,
                cardsIncorrect = stats.cardsIncorrect,
                wordsPerMinute = stats.wordsPerMinute
            )
        )
    }

    init {
        Log.d(logTag, "Update: duolingo sfx, prompt in speech UI, voice loop rules, stop resets progress")

        // All file I/O moved to a background thread to avoid blocking the main thread
        // on app startup (was 500ms–2s on weak tablets). UI shows a loading spinner
        // via TrainingUiState.isLoading until this coroutine completes.
        viewModelScope.launch(Dispatchers.IO) {
            lessonStore.ensureSeedData()
            // Automatically seed/update default packs if needed
            lessonStore.updateDefaultPacksIfNeeded()

            // Initialize GrammarChipStore after packs are installed
            val packsDir = File(getApplication<Application>().filesDir, "grammarmate/packs")
            GrammarChipStore.initialize(getApplication(), packsDir)

            badSentenceStore.migrateIfNeeded(lessonStore)
            val progress = progressStore.load()
            val config = configStore.load()
            val profile = profileStore.load()
            eliteSizeMultiplier = config.eliteSizeMultiplier
            sessionSize = config.sessionSize
            cardProvider.setSubLessonSize(sessionSize)
            sessionRunner.setEliteSizeMultiplier(eliteSizeMultiplier)
            sessionRunner.setSubLessonSize(sessionSize)
            dailyPracticeCoordinator.setSessionSize(sessionSize)
            val bossLessonRewards = bossOrchestrator.parseBossRewards(progress.bossLessonRewards)
            val bossMegaRewards = bossOrchestrator.parseBossRewards(progress.bossMegaRewards)
            val languages = lessonStore.getLanguages()
            val packs = lessonStore.getInstalledPacks()
            val hasExistingProgress = progressStore.exists()
            val selectedLanguageId = if (hasExistingProgress) {
                languages.firstOrNull { it.id == progress.languageId }?.id
            } else {
                null // Zero state: no progress.yaml → show language cards
            }
            val lessons = selectedLanguageId?.let { lessonStore.getLessons(it.value) } ?: emptyList()
            val selectedLessonId = lessons.firstOrNull()?.id
            val normalizedEliteSpeeds = sessionRunner.normalizeEliteSpeeds(progress.eliteBestSpeeds)
            val restoredScreen = "HOME"
            val streakData = selectedLanguageId?.let { streakStore.getCurrentStreak(it.value) }
                ?: com.alexpo.grammermate.data.StreakData(languageId = com.alexpo.grammermate.data.LanguageId(""))
            // Resolve activePackId: prefer saved value if pack still exists,
            // then derive from lessonId, then fall back to first pack for language.
            val savedPackId = progress.activePackId
            val allPackIds = packs.map { it.packId }.toSet()
            val initialActivePackId = if (savedPackId != null && savedPackId in allPackIds) {
                savedPackId
            } else {
                selectedLessonId?.let { com.alexpo.grammermate.data.PackId(lessonStore.getPackIdForLesson(it.value) ?: return@let null) }
            }
            val initialPackLessonIds = initialActivePackId?.let { lessonStore.getLessonIdsForPack(it.value) }

            // Load lesson progress from pack-scoped store (Phase 5, Wave 3.1)
            val packProgress = initialActivePackId?.let { packLessonProgressStore.loadPackProgress(it.value) }
            val lessonProgress = selectedLessonId?.let { lessonId ->
                packProgress?.lessonProgress?.get(lessonId.value)
            }

            withContext(Dispatchers.Main) {
                _coreState.update {
                    it.resetSessionState().copy(isLoading = false, navigation = it.navigation.copy(languages = languages, installedPacks = packs, selectedLanguageId = selectedLanguageId, activePackId = initialActivePackId, activePackLessonIds = initialPackLessonIds, lessons = lessons, selectedLessonId = selectedLessonId, mode = progress.mode, userName = profile.userName, initialScreen = restoredScreen, welcomeDialogAttempts = profile.welcomeDialogAttempts, themeMode = config.themeMode), cardSession = it.cardSession.copy(sessionState = lessonProgress?.state ?: SessionState.PAUSED, currentIndex = lessonProgress?.currentIndex ?: 0, correctCount = lessonProgress?.correctCount ?: 0, incorrectCount = lessonProgress?.incorrectCount ?: 0, incorrectAttemptsForCard = lessonProgress?.incorrectAttemptsForCard ?: 0, activeTimeMs = lessonProgress?.activeTimeMs ?: 0L, voiceActiveMs = progress.voiceActiveMs, voiceWordCount = progress.voiceWordCount, hintCount = progress.hintCount, testMode = config.testMode, vocabSprintLimit = config.vocabSprintLimit, currentStreak = streakData.currentStreak, longestStreak = streakData.longestStreak, todayFireCount = streakData.todayFireCount, badSentenceCount = initialActivePackId?.let { pid -> badSentenceStore.getBadSentenceCount(pid.value) } ?: 0, hintLevel = config.hintLevel, hintSessionOffset = Random.nextInt(0, 100)), elite = it.elite.copy(eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1), eliteBestSpeeds = normalizedEliteSpeeds, eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, config.testMode), eliteSizeMultiplier = config.eliteSizeMultiplier))
                }
                // Initialize feature-owned state from persisted progress
                bossOrchestrator.initRewards(bossLessonRewards, bossMegaRewards)
                rebindWordMasteryStore(initialActivePackId?.value)
                vocabSprintRunner.updateMasteredCount(wordMasteryStore.getMasteredCount())
                dailyPracticeCoordinator.initializeCursor(progress.dailyCursor)
                refreshDrillVisibility()
                rebuildSchedules(filterLessonsForActivePack(lessons))
                buildSessionCards()
                refreshFlowerStates()
                loadChapters()
                if (_coreState.value.cardSession.sessionState == SessionState.ACTIVE && _coreState.value.cardSession.currentCard != null) {
                    sessionRunner.resumeTimer()
                    (_coreState.value.cardSession.currentCard as? SentenceCard)?.let {
                        recordCardShowForMastery(it)
                        recordCardEncounter(it)
                    }
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

            // Force reload default packs on every app start to ensure latest lesson content.
            // NOTE: We read _coreState.value INSIDE the update lambda to avoid a TOCTOU race
            // where the user changes language/lesson on the main thread while we captured
            // stale values on the IO thread.
            val reloaded = lessonStore.forceReloadDefaultPacks()
            if (reloaded) {
                val reloadLanguages = lessonStore.getLanguages()
                val reloadPacks = lessonStore.getInstalledPacks()
                withContext(Dispatchers.Main) {
                    _coreState.update { current ->
                        val currentLang = current.navigation.selectedLanguageId
                        val selectedLang = if (currentLang != null) {
                            reloadLanguages.firstOrNull { it.id == currentLang }?.id
                                ?: reloadLanguages.firstOrNull()?.id
                        } else {
                            null // Preserve zero state: no language selected yet
                        }
                        val reloadLessons = selectedLang?.let { lessonStore.getLessons(it.value) } ?: emptyList()
                        val currentLessonId = current.navigation.selectedLessonId
                        val selectedLessonId = reloadLessons.firstOrNull { it.id == currentLessonId }?.id
                            ?: reloadLessons.firstOrNull()?.id
                        val reloadedPackIds = reloadPacks.map { it.packId }.toSet()
                        // Keep current activePackId if it still exists after reload,
                        // otherwise derive from lessonId, otherwise fall back to first pack.
                        val updatedPackId = if (current.navigation.activePackId != null && current.navigation.activePackId in reloadedPackIds) {
                            current.navigation.activePackId
                        } else {
                            selectedLessonId?.let { com.alexpo.grammermate.data.PackId(lessonStore.getPackIdForLesson(it.value) ?: return@let null) }
                                ?: reloadPacks.firstOrNull { it.languageId == selectedLang }?.packId
                        }
                        val updatedPackLessonIds = updatedPackId?.let { lessonStore.getLessonIdsForPack(it.value) }
                        current.copy(navigation = current.navigation.copy(languages = reloadLanguages, installedPacks = reloadPacks, selectedLanguageId = selectedLang, activePackId = updatedPackId, activePackLessonIds = updatedPackLessonIds, lessons = reloadLessons, selectedLessonId = selectedLessonId), elite = current.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(reloadLessons, current.cardSession.testMode)))
                    }
                    refreshDrillVisibility()
                    rebindWordMasteryStore(_coreState.value.navigation.activePackId?.value)
                    vocabSprintRunner.updateMasteredCount(wordMasteryStore.getMasteredCount())
                    dailyPracticeCoordinator.resetState()
                    dailyPracticeCoordinator.initializeCursor()
                    val updatedLessons = _coreState.value.navigation.selectedLanguageId?.let { lessonStore.getLessons(it.value) } ?: emptyList()
                    rebuildSchedules(filterLessonsForActivePack(updatedLessons))
                    buildSessionCards()
                    refreshFlowerStates()
                    loadChapters()
                }
            }

            // Pre-build daily practice session AFTER forceReload completes.
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
        _coreState.update { it.copy(navigation = it.navigation.copy(hasVerbDrill = verbDrill, hasVocabDrill = vocabDrill)) }
    }

    fun selectLanguage(languageId: String) {
        sessionRunner.pauseTimer()
        vocabSession = emptyList()
        sessionRunner.clearAllCards()
        val lessons = lessonStore.getLessons(languageId)
        // Clear activePackId so user sees pack selection for the new language.
        // Previously auto-selected first pack — caused pack confusion.
        rebindWordMasteryStore(null)
        _coreState.update {
            it.resetAllSessionState().copy(navigation = it.navigation.copy(selectedLanguageId = com.alexpo.grammermate.data.LanguageId(languageId), lessons = lessons, selectedLessonId = null, activePackId = null, activePackLessonIds = emptyList()), elite = it.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, it.cardSession.testMode)))
        }
        // Load streak for the new language (resetAllSessionState zeroes streak data)
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
        saveProgress()
        audioCoordinator.ttsModelManager.currentLanguageId = languageId
        audioCoordinator.checkTtsModel()
        // Only switch ASR language if engine is already initialized and ready.
        // Calling setLanguage() when ASR is not ready crashes the native layer.
        if (audioCoordinator.asrEngine?.isReady == true) {
            audioCoordinator.asrEngine.setLanguage(languageId)
        }
    }

    fun selectLesson(lessonId: String, packId: String? = null) {
        sessionRunner.pauseTimer()
        vocabSession = emptyList()
        sessionRunner.clearAllCards()

        // Use provided packId, or resolve from lesson if not specified
        val resolvedPackId = packId ?: lessonStore.getPackIdForLesson(lessonId)
        val packLessonIds = resolvedPackId?.let { lessonStore.getLessonIdsForPack(it) }
        rebindWordMasteryStore(resolvedPackId)

        // Rebuild schedules BEFORE reading them (filtered to active pack)
        rebuildSchedules(filterLessonsForActivePack(_coreState.value.navigation.lessons))

        // Calculate active sub-lesson index based on completed count
        val typedLessonId = com.alexpo.grammermate.data.LessonId(lessonId)
        val schedule = lessonSchedules[typedLessonId]
        val subLessons = schedule?.subLessons.orEmpty()
        val mastery = _coreState.value.navigation.selectedLanguageId?.let { masteryStore.get(lessonId, it.value) }
        val completedCount = progressTracker.calculateCompletedSubLessons(
            subLessons = subLessons,
            mastery = mastery,
            lessonId = typedLessonId,
            lessons = _coreState.value.navigation.lessons
        )
        val nextActiveIndex = completedCount.coerceAtMost((subLessons.size - 1).coerceAtLeast(0))

        _coreState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(selectedLessonId = typedLessonId, activePackId = resolvedPackId?.let { pid -> com.alexpo.grammermate.data.PackId(pid) }, activePackLessonIds = packLessonIds, mode = TrainingMode.LESSON), cardSession = it.cardSession.copy(activeSubLessonIndex = nextActiveIndex, completedSubLessonCount = completedCount, currentCard = null, hintSessionOffset = Random.nextInt(0, 100)))
        }
        // Reset feature-owned state for session change
        bossOrchestrator.resetState()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        dailyPracticeCoordinator.resetState()
        dailyPracticeCoordinator.initializeCursor()
        refreshDrillVisibility()
        buildSessionCards()
        refreshFlowerStates()
        loadChapters()
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
        val packId = _coreState.value.navigation.activePackId?.value
            ?: return emptyList()

        return chapter.lessons.mapNotNull { lessonId ->
            _coreState.value.navigation.lessons.firstOrNull { it.id.value == lessonId }
        }
    }

    fun getChapterProgress(packId: String, chapterId: String): ChapterProgress? {
        return _coreState.value.chapterProgresses[chapterId]
    }

    fun selectPack(packId: String) {
        // Cancel any active daily session before switching packs
        if (_coreState.value.daily.dailySession.active) {
            cancelDailySession()
        }

        // Sync language to match the pack's language (fixes voice input language)
        val packLanguageId = lessonStore.getInstalledPacks()
            .firstOrNull { it.packId.value == packId }?.languageId?.value
        if (packLanguageId != null && packLanguageId != _coreState.value.navigation.selectedLanguageId?.value) {
            // Update language-dependent audio before continuing
            audioCoordinator.ttsModelManager.currentLanguageId = packLanguageId
            audioCoordinator.checkTtsModel()
            if (audioCoordinator.asrEngine?.isReady == true) {
                audioCoordinator.asrEngine.setLanguage(packLanguageId)
            }
            _coreState.update {
                it.copy(navigation = it.navigation.copy(selectedLanguageId = com.alexpo.grammermate.data.LanguageId(packLanguageId)))
            }
        }

        val packLessonIds = lessonStore.getLessonIdsForPack(packId)
        if (packLessonIds.isNotEmpty()) {
            val currentLessonId = _coreState.value.navigation.selectedLessonId
            val lessonId = if (currentLessonId != null && currentLessonId.value in packLessonIds) currentLessonId.value else packLessonIds.first()
            // Pass packId explicitly to avoid relying on getPackIdForLesson()
            selectLesson(lessonId, packId)
        } else {
            // Drill-only pack — set activePackId without selecting a lesson
            rebindWordMasteryStore(packId)
            _coreState.update {
                it.copy(navigation = it.navigation.copy(activePackId = com.alexpo.grammermate.data.PackId(packId), activePackLessonIds = emptyList(), selectedLessonId = null))
            }
            dailyPracticeCoordinator.resetState()
            dailyPracticeCoordinator.initializeCursor()
            refreshDrillVisibility()
            loadChapters()
            saveProgress()
        }
    }

    /**
     * Check if a pack has chapters (Grammar Story Roadmap support).
     */
    fun hasPackChapters(packId: String): Boolean {
        return lessonStore.hasChapters(packId)
    }

    /**
     * Clear the active pack to return to pack selection screen.
     */
    fun clearActivePack() {
        // Cancel any active daily session before clearing pack
        if (_coreState.value.daily.dailySession.active) {
            cancelDailySession()
        }

        _coreState.update {
            it.copy(navigation = it.navigation.copy(
                activePackId = null,
                activePackLessonIds = emptyList(),
                selectedLessonId = null
            ))
        }
        dailyPracticeCoordinator.resetState()
        dailyPracticeCoordinator.initializeCursor()
        refreshDrillVisibility()
        saveProgress()
    }

    fun selectMode(mode: TrainingMode) {
        sessionRunner.pauseTimer()
        vocabSession = emptyList()
        _coreState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(mode = mode))
        }
        // Reset feature-owned state for session change
        bossOrchestrator.resetState()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        buildSessionCards()
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
            forceBackupOnSave = true
        }

        // Track daily card practice for cursor advancement.
        // Only VOICE and KEYBOARD answers count — WORD_BANK does NOT (Level B rule).
        // This hook is the sole counting mechanism for DAILY_TRANSLATE and DAILY_VERBS
        // sessions that run through SessionRunner (the DailyPracticeSessionProvider
        // path with its onCardAdvanced callback is dead code, never instantiated).
        if (result.accepted && isDailySession()) {
            if (beforeInputMode != InputMode.WORD_BANK) {
                val blockType = when (beforeScreenMode) {
                    com.alexpo.grammermate.data.TrainingScreenMode.DAILY_TRANSLATE -> DailyBlockType.TRANSLATE
                    com.alexpo.grammermate.data.TrainingScreenMode.DAILY_VERBS -> DailyBlockType.VERBS
                    else -> return SubmitResult(result.accepted, result.hintShown)
                }
                recordDailyCardPracticed(blockType)
                // Persist verb card progress so buildVerbBlock doesn't repeat the same cards
                if (blockType == DailyBlockType.VERBS) {
                    if (beforeCard is com.alexpo.grammermate.data.VerbDrillCard) {
                        dailyPracticeCoordinator.persistDailyVerbProgress(beforeCard)
                    }
                }
            }
        }

        Log.d(logTag, "Answer submitted: accepted=${result.accepted}")
        return SubmitResult(result.accepted, result.hintShown)
    }

    fun nextCard(triggerVoice: Boolean = false) {
        val events = sessionRunner.nextCard(triggerVoice)
        handleSessionEvents(events)
    }

    fun prevCard() = handleSessionEvents(sessionRunner.prevCard())

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
    }

    /**
     * Start a daily verbs session with [SessionCard] cards.
     * Sets screenMode to DAILY_VERBS and activates the session.
     */
    fun startDailyVerbsSession(cards: List<com.alexpo.grammermate.data.SessionCard>) {
        val events = sessionRunner.startCardSession(cards, com.alexpo.grammermate.data.TrainingScreenMode.DAILY_VERBS)
        handleSessionEvents(events)
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
            val lessons = lessonStore.getLessons(pack.languageId.value)
            val selectedLessonId = lessons.firstOrNull()?.id
            _coreState.update {
                it.resetAllSessionState().copy(navigation = it.navigation.copy(languages = lessonStore.getLanguages(), installedPacks = lessonStore.getInstalledPacks(), selectedLanguageId = pack.languageId, lessons = lessons, selectedLessonId = selectedLessonId, mode = TrainingMode.LESSON), elite = it.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, it.cardSession.testMode)))
            }
            // Reset feature-owned state
            bossOrchestrator.resetStateKeepRewards()
            storyRunner.resetState()
            vocabSprintRunner.resetState()
            dailyPracticeCoordinator.resetState()
            refreshDrillVisibility()
            rebuildSchedules(filterLessonsForActivePack(lessons))
            buildSessionCards()
            loadChapters()
            saveProgress()
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
        vocabSession = emptyList()
        val lessons = lessonStore.getLessons(language.id.value)
        val selectedLessonId = lessons.firstOrNull()?.id
        _coreState.update {
            it.resetAllSessionState().copy(navigation = it.navigation.copy(languages = lessonStore.getLanguages(), installedPacks = lessonStore.getInstalledPacks(), selectedLanguageId = language.id, lessons = lessons, selectedLessonId = selectedLessonId, mode = TrainingMode.LESSON), elite = it.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, it.cardSession.testMode)))
        }
        // Reset feature-owned state
        bossOrchestrator.resetStateKeepRewards()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        dailyPracticeCoordinator.resetState()
        refreshDrillVisibility()
        rebuildSchedules(filterLessonsForActivePack(lessons))
        buildSessionCards()
        loadChapters()
        saveProgress()
    }

    fun deleteAllLessons() {
        val languageId = _coreState.value.navigation.selectedLanguageId ?: return
        lessonStore.deleteAllLessons(languageId.value)
        refreshLessons(null)
        _coreState.update { it.copy(navigation = it.navigation.copy(installedPacks = lessonStore.getInstalledPacks())) }
    }

    /**
     * Reset ALL progress: mastery, daily practice, verb drill, vocab mastery, training progress.
     */
    fun resetAllProgress() = handleSettingsResults(settingsActionHandler.resetAllProgress(getApplication()))

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
        if (_coreState.value.navigation.selectedLanguageId == languageId) {
            refreshLessons(null)
        }
        _coreState.update { it.copy(navigation = it.navigation.copy(installedPacks = lessonStore.getInstalledPacks())) }
    }

    fun toggleTestMode() = handleSettingsResults(settingsActionHandler.toggleTestMode())

    fun resumeFromSettings() = handleSessionEvents(sessionRunner.resumeFromSettings())

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
    fun computeCompletionNextAction(): CompletionNextAction {
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
        val started = dailyPracticeCoordinator.startDailyPractice(
            resolveProgressLessonInfo = { resolveProgressLessonInfo() },
            onStoreFirstSessionCardIds = { sentenceIds, verbIds ->
                storeFirstSessionCardIds(sentenceIds, verbIds)
            }
        )
        Log.d(logTag, "DailyPractice: VM.startDailyPractice result=$started")
        return started
    }

    /**
     * Store the first session of the day's card IDs in the cursor state.
     * This allows Repeat to reconstruct the exact same cards even after restart.
     */
    private fun storeFirstSessionCardIds(sentenceIds: List<String>, verbIds: List<String>) {
        val lessonId = _coreState.value.navigation.selectedLessonId?.value ?: ""
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
        val started = dailyPracticeCoordinator.repeatDailyPractice(
            lessonLevel = lessonLevel,
            resolveProgressLessonInfo = { resolveProgressLessonInfo() }
        )
        Log.d(logTag, "DailyPractice: VM.repeatDailyPractice result=$started")
        return started
    }

    /**
     * Advance to the next block in the daily practice session.
     * Called when TRANSLATE/VERBS block finishes in TrainingScreen (via GrammarMateApp token detection)
     * or when VOCAB block completes in DailyPracticeScreen.
     *
     * @return the next block to render, or null if all blocks are done.
     */
    fun onDailyBlockComplete(): com.alexpo.grammermate.data.DailyBlock? {
        return dailyPracticeCoordinator.onBlockComplete()
    }

    /**
     * Record that a card in the current daily practice block was practiced via VOICE or KEYBOARD.
     * Called from DailyPracticeScreen's onCardAdvanced callback (which the provider only fires
     * for non-WORD_BANK modes). Used to track per-block completion for cursor advancement.
     */
    fun recordDailyCardPracticed(blockType: DailyBlockType) {
        dailyPracticeCoordinator.recordDailyCardPracticed(
            blockType = blockType,
            resolveCardLessonId = { card -> resolveCardLessonId(card) }
        )
    }

    fun repeatDailyBlock(): Boolean {
        return dailyPracticeCoordinator.repeatDailyBlock(
            resolveProgressLessonInfo = { resolveProgressLessonInfo() }
        )
    }

    fun cancelDailySession() {
        Log.d(logTag, "DailyPractice: VM.cancelDailySession")
        val sentenceCount = dailyPracticeCoordinator.cancelDailySession()
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

    fun submitDailySentenceAnswer(input: String): Boolean {
        val correct = dailyPracticeCoordinator.submitDailySentenceAnswer(input)
        // Orchestrator: play sound via AudioCoordinator (cross-module call)
        if (correct) {
            audioCoordinator.playSuccessSound()
        } else {
            audioCoordinator.playErrorSound()
        }
        return correct
    }

    fun submitDailyVerbAnswer(input: String): Boolean {
        val correct = dailyPracticeCoordinator.submitDailyVerbAnswer(input)
        // Orchestrator: play sound via AudioCoordinator (cross-module call)
        if (correct) {
            audioCoordinator.playSuccessSound()
        } else {
            audioCoordinator.playErrorSound()
        }
        return correct
    }

    fun hasVocabProgress(): Boolean {
        val lessonId = _coreState.value.navigation.selectedLessonId ?: return false
        val languageId = _coreState.value.navigation.selectedLanguageId ?: return false
        val progress = vocabProgressStore.get(lessonId.value, languageId.value)
        return progress.completedIndices.isNotEmpty()
    }

    fun openVocabSprint(resume: Boolean = false) {
        when (val result = vocabSprintRunner.openSprint(resume)) {
            is VocabResult.ResetBoss -> bossOrchestrator.resetState()
            is VocabResult.SaveAndBackup -> { /* handled in submitVocabAnswer */ }
            is VocabResult.None -> {}
        }
    }

    fun completeStory(phase: StoryPhase, allCorrect: Boolean) {
        when (val result = storyRunner.completeStory(phase, allCorrect)) {
            is StoryResult.SaveAndBackup -> {
                forceBackupOnSave = true
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
                forceBackupOnSave = true
                saveProgress()
            }
            is VocabResult.ResetBoss -> bossOrchestrator.resetState()
            is VocabResult.None -> {}
        }
    }


    fun startBossLesson() = handleBossCommands(bossOrchestrator.startBossLesson())

    fun startBossMega() = handleBossCommands(bossOrchestrator.startBossMega())

    fun startBossElite() = handleBossCommands(bossOrchestrator.startBossElite())

    fun finishBoss() = handleBossCommands(bossOrchestrator.finishBoss())

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
        saveProgress()
        masteryStore.flush()
        audioCoordinator.release()
        super.onCleared()
    }

    /**
     * Определить к какому уроку принадлежит карточка.
     * Важно для Mixed-режима где карточки могут быть из разных уроков.
     */
    private fun resolveCardLessonId(card: SentenceCard): String {
        val s = _coreState.value
        return progressTracker.resolveCardLessonId(
            card = card,
            selectedLessonId = s.navigation.selectedLessonId,
            lessons = s.navigation.lessons
        ).value
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
        val lessonId = resolveCardLessonId(card)
        val languageId = s.navigation.selectedLanguageId?.value ?: return
        val count = masteryStore.recordCardEncounter(lessonId, languageId, card.id)
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
        val results = progressRestorer.restoreBackup(backupUri)
        handleProgressResults(results)
    }

    fun reloadFromDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            val results = progressRestorer.reloadFromDisk()
            handleProgressResults(results)
        }
    }

    fun flagBadSentence() {
        when (val result = badSentenceHelper.flagBadSentence()) {
            is BadSentenceResult.SkipToNextCard -> sessionRunner.skipToNextCard()
            is BadSentenceResult.None -> {}
        }
    }

    fun hideCurrentCard() {
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
                    val count = progressTracker.calculateCompletedSubLessons(
                        subLessons = event.subLessons,
                        mastery = event.mastery,
                        lessonId = event.lessonId?.let { com.alexpo.grammermate.data.LessonId(it) },
                        lessons = _coreState.value.navigation.lessons
                    )
                    event.callback(count)
                }
                is SessionEvent.GetMastery -> event.callback(masteryStore.get(event.lessonId, event.langId))
                is SessionEvent.GetSchedule -> event.callback(lessonSchedules[com.alexpo.grammermate.data.LessonId(event.lessonId)])
                is SessionEvent.RebuildSchedules -> rebuildSchedules(filterLessonsForActivePack(event.lessons))
                is SessionEvent.AdvanceBossProgress -> {
                    val (advanceResult, bossCommands) = bossOrchestrator.advanceBossProgressOnNextCard(event.nextIndex, event.totalCards)
                    handleBossCommands(bossCommands)

                    // Sync bossState from BossOrchestrator to _coreState.boss
                    // This ensures UI sees updated bossProgress, bossReward, bossRewardMessage
                    val bossState = bossOrchestrator.stateFlow.value
                    _coreState.update { it.copy(boss = bossState) }

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
            lessons = s.navigation.lessons
        )

        // Update chapter progress when cards are shown (mastery changes)
        s.navigation.selectedLessonId?.let { lessonId ->
            updateChapterProgress(lessonId.value)
        }
    }

    private fun checkAndMarkLessonCompleted() {
        val s = _coreState.value
        val langId = s.navigation.selectedLanguageId ?: return
        progressTracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = s.cardSession.completedSubLessonCount,
            selectedLessonId = s.navigation.selectedLessonId,
            selectedLanguageId = langId
        )

        // Update chapter progress when lesson completion status changes
        s.navigation.selectedLessonId?.let { lessonId ->
            updateChapterProgress(lessonId.value)
        }
    }
    private fun refreshFlowerStates() = flowerRefresher.refreshFlowerStates()
    private fun updateStreak(forcedPracticeType: PracticeType? = null) {
        // TASK-051: Streak only counts when session is "completed" (засчитанная УЕ).
        // Must have enough correct answers to cover non-bad cards.
        if (!isSessionCompleted()) return

        val languageId = _coreState.value.navigation.selectedLanguageId ?: return
        val practiceType = forcedPracticeType ?: determinePracticeType()
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

    /**
     * Determine the PracticeType for the current session mode.
     */
    private fun determinePracticeType(): PracticeType {
        val state = _coreState.value
        return when {
            state.boss.bossActive -> PracticeType.TRANSLATION
            false -> PracticeType.TRANSLATION
            else -> PracticeType.TRANSLATION
        }
    }
    private fun saveProgress() {
        val state = uiState.value
        // Read daily cursor directly from coordinator to avoid combine flow staleness.
        // The combine() flow that produces uiState merges _coreState with coordinator's
        // dailyState; after updateCursor() the MutableStateFlow is updated immediately
        // but the downstream combine may not have propagated yet, so uiState.value can
        // contain a stale cursor. This caused storeFirstSessionCardIds() to write a
        // cursor missing firstSessionDate/cardIds to disk, breaking Repeat after restart.
        val actualCursor = dailyPracticeCoordinator.getCursor()
        val stateToSave = if (state.daily.dailyCursor != actualCursor) {
            state.copy(daily = state.daily.copy(dailyCursor = actualCursor))
        } else {
            state
        }
        val shouldBackup = progressTracker.saveProgress(
            state = stateToSave,
            forceBackup = forceBackupOnSave,
            normalizedEliteSpeeds = sessionRunner.normalizeEliteSpeeds(stateToSave.elite.eliteBestSpeeds)
        )
        masteryStore.flush()
        if (shouldBackup) {
            forceBackupOnSave = false
            settingsActionHandler.createProgressBackup()
        }
    }
    private fun buildSessionCards() {
        if (bossOrchestrator.stateFlow.value.bossActive || _coreState.value.elite.eliteActive) return
        val state = _coreState.value
        val hiddenIds = hiddenCardStore.getHiddenCardIds()
        val lessons = state.navigation.lessons
        val mastery = state.navigation.selectedLessonId?.let { lid ->
            state.navigation.selectedLanguageId?.let { langId ->
                masteryStore.get(lid.value, langId.value)
            }
        }
        val result = cardProvider.buildSessionCards(
            lessons = lessons,
            mode = state.navigation.mode,
            selectedLessonId = state.navigation.selectedLessonId,
            schedules = lessonSchedules,
            activeSubLessonIndex = state.cardSession.activeSubLessonIndex,
            hiddenCardIds = hiddenIds,
            mastery = mastery
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
        lessonSchedules = cardProvider.buildSchedules(lessons, lessonSchedules)
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
                is BossCommand.ResetBoss -> bossOrchestrator.resetState()
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
        vocabSession = emptyList()
        val languageId = _coreState.value.navigation.selectedLanguageId
        val lessons = if (languageId != null) lessonStore.getLessons(languageId.value) else emptyList()
        val selected = selectedLessonId?.let { com.alexpo.grammermate.data.LessonId(it) } ?: lessons.firstOrNull()?.id
        _coreState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(lessons = lessons, selectedLessonId = selected), elite = it.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, it.cardSession.testMode)))
        }
        // Reset feature-owned state for session change
        bossOrchestrator.resetState()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        dailyPracticeCoordinator.resetState()
        rebuildSchedules(filterLessonsForActivePack(lessons))
        buildSessionCards()
        loadChapters() // reload chapter progress from disk (cleared after reset)
        saveProgress()
    }
    private fun resetStores(app: Application) {
        progressTracker.resetStores(app)
        vocabProgressStore.clear()
        packDailyCursorStore.invalidateCache() // prevent stale cursor reads after disk deletion
        // Clear chapter progress for all packs
        lessonStore.getInstalledPacks().forEach { pack ->
            container.chapterProgressStore(pack.packId.value).clear()
        }
    }
    private fun resetStoresForLanguage(app: Application, languageId: String) {
        progressTracker.resetStoresForLanguage(app, languageId)
        vocabProgressStore.clearLanguage(languageId)
        packDailyCursorStore.invalidateCache() // prevent stale cursor reads after disk deletion
        // Clear chapter progress for packs matching this language
        lessonStore.getInstalledPacks()
            .filter { it.languageId.value == languageId }
            .forEach { pack ->
                container.chapterProgressStore(pack.packId.value).clear()
            }
    }
    private fun resetDrillFiles(app: Application) {
        progressTracker.resetDrillFiles(app)
        container.clearCache()
        rebindWordMasteryStore(_coreState.value.navigation.activePackId?.value)
    }
    private fun resetDrillFilesForPack(app: Application, packId: String) {
        progressTracker.resetDrillFilesForPack(app, packId)
        container.clearCache()
        rebindWordMasteryStore(_coreState.value.navigation.activePackId?.value)
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
                is SettingsResult.ResetStores -> resetStores(result.app)
                is SettingsResult.ResetStoresForLanguage -> resetStoresForLanguage(result.app, result.languageId)
                is SettingsResult.ResetDrillFiles -> resetDrillFiles(result.app)
                is SettingsResult.ResetDrillFilesForPack -> resetDrillFilesForPack(result.app, result.packId)
                is SettingsResult.ClearWordMastery -> clearWordMastery()
                is SettingsResult.ResetDailyState -> resetDailyState()
                is SettingsResult.SetForceBackup -> { forceBackupOnSave = true }
                is SettingsResult.SaveProgress -> saveProgress()
                is SettingsResult.ResetStreak -> resetStreak()
                is SettingsResult.ResetStreakForLanguage -> resetStreakForLanguage(result.languageId)
                is SettingsResult.None -> {}
            }
        }
    }

    // -- Profile stats --
    data class ProfileStats(
        val cardsCompleted: Int,
        val wordsLearned: Int,
        val cefrLevel: String
    )

    fun getProfileStats(): ProfileStats {
        val languageId = _coreState.value.navigation.selectedLanguageId?.value ?: return ProfileStats(0, 0, "")
        val packId = _coreState.value.navigation.activePackId?.value

        // Cards completed: sum uniqueCardShows across all lessons for current language
        val cardsCompleted = masteryStore.loadAll()[languageId]
            ?.values
            ?.sumOf { it.uniqueCardShows }
            ?: 0

        // Words learned: count of mastered words in current pack
        val wordsLearned = wordMasteryStore.getMasteredCount()

        // CEFR level: based on rank of learned vocab words
        val cefrLevel = if (packId != null) {
            val allWords = lessonStore.getVocabWordsByRankRange(packId, languageId, 0, Int.MAX_VALUE)
            val learnedIds = wordMasteryStore.loadAll().filter { (_, state) -> state.isLearned }.keys
            val learnedRanks = allWords.filter { it.id in learnedIds }.map { it.rank }
            CefrCalculator.calculate(learnedRanks)
        } else {
            CefrCalculator.calculate(emptyList())
        }

        return ProfileStats(cardsCompleted, wordsLearned, cefrLevel)
    }

    // -- ProgressResult handler --
    private fun handleProgressResults(results: List<ProgressResult>) {
        for (result in results) {
            when (result) {
                is ProgressResult.RebuildSchedules -> rebuildSchedules(filterLessonsForActivePack(result.lessons))
                is ProgressResult.BuildSessionCards -> buildSessionCards()
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
    fun getChapterCards(): List<ChapterCardUi> {
        val activePackId = _coreState.value.navigation.activePackId?.value ?: return emptyList()
        val selectedLanguageId = _coreState.value.navigation.selectedLanguageId?.value ?: return emptyList()

        Log.d(logTag, "getChapterCards: activePackId=$activePackId, selectedLanguageId=$selectedLanguageId")

        if (!lessonStore.hasChapters(activePackId)) {
            return emptyList()
        }

        val chapters = lessonStore.getChapters(activePackId)

        // Get mastery states for all lessons in the pack
        val allLessonMasteryStates = mutableMapOf<String, LessonMasteryState>()
        for (lessonId in lessonStore.getLessonIdsForPack(activePackId)) {
            val masteryState = masteryStore.get(lessonId, selectedLanguageId) ?: LessonMasteryState(LessonId(lessonId), LanguageId(selectedLanguageId))
            allLessonMasteryStates[lessonId] = masteryState
            if (masteryState.uniqueCardShows > 0 || masteryState.intervalStepIndex > 0) {
                Log.d(logTag, "getChapterCards: $lessonId -> uniqueShows=${masteryState.uniqueCardShows}, stepIndex=${masteryState.intervalStepIndex}")
            }
        }

        return chapters.mapIndexed { index, chapter ->
            // Calculate progress LIVE from mastery data instead of relying on
            // chapterProgressStore which may be stale/empty on app start.
            val progress = ChapterProgressCalculator.calculateChapterProgress(chapter, allLessonMasteryStates)
            Log.d(logTag, "getChapterCards: chapter=${chapter.chapterId} started=${progress.lessonsStarted} completed=${progress.lessonsCompleted}/${chapter.lessons.size}")
            val status = calculateChapterStatus(chapter, progress, allLessonMasteryStates, index)

            ChapterCardUi(
                chapter = chapter,
                progress = progress,
                status = status
            )
        }
    }

    /**
     * Calculate the status of a chapter based on progress.
     * All chapters are accessible - no locks. Users can learn at their own pace.
     */
    private fun calculateChapterStatus(
        chapter: com.alexpo.grammermate.data.Chapter,
        progress: com.alexpo.grammermate.data.ChapterProgress,
        masteryStates: Map<String, LessonMasteryState>,
        chapterIndex: Int
    ): ChapterStatus {
        return when {
            chapter.lessons.isEmpty() -> ChapterStatus.ACTIVE // Empty chapter is never DONE
            progress.lessonsCompleted >= chapter.lessons.size -> ChapterStatus.DONE
            else -> ChapterStatus.ACTIVE // All chapters are accessible
        }
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
        if (storyFile == null) return null

        return try {
            val activePackId = _coreState.value.navigation.activePackId?.value ?: return null
            val uiLanguage = currentUiLanguage // Get from configStore

            // Extract chapter ID from storyFile for language detection
            // storyFile format: "chapter_XX_original.md" or "chapter_XX_name.md"
            val chapterBase = storyFile.removePrefix("chapter_")
                .removeSuffix(".md")
                .split("_")[0] // Get "XX" part
            val chapterId = "chapter_$chapterBase"

            Log.d(logTag, "Loading story: storyFile=$storyFile, chapterId=$chapterId, uiLanguage=$uiLanguage")

            // Use language detection to get the right story file
            val detectedStoryFile = lessonStore.detectStoryLanguage(activePackId, chapterId, uiLanguage)

            if (detectedStoryFile != null) {
                Log.d(logTag, "Detected story file: $detectedStoryFile (original request: $storyFile)")
                val content = lessonStore.getChapterStory(activePackId, detectedStoryFile)

                if (content != null) {
                    Log.d(logTag, "Successfully loaded story: $detectedStoryFile")
                    content
                } else {
                    Log.w(logTag, "Story file not found: $detectedStoryFile")
                    null
                }
            } else {
                Log.w(logTag, "No story file detected for chapter: $chapterId")
                null
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load story content: $storyFile", e)
            null
        }
    }

    /**
     * Get the first incomplete lesson ID in a chapter.
     * Returns null if all lessons are completed or chapter is empty.
     */
    fun getFirstIncompleteLesson(chapter: com.alexpo.grammermate.data.Chapter): String? {
        val selectedLanguageId = _coreState.value.navigation.selectedLanguageId?.value ?: return null

        for (lessonId in chapter.lessons) {
            val masteryState = masteryStore.get(lessonId, selectedLanguageId) ?: LessonMasteryState(LessonId(lessonId), LanguageId(selectedLanguageId))
            // Consider lesson incomplete if intervalStepIndex < 3 (learned threshold)
            if (masteryState.intervalStepIndex < 3) {
                return lessonId
            }
        }

        return null
    }

    /**
     * Load chapters for the current pack into TrainingUiState.
     * Call this when the app starts or when switching packs.
     */
    private fun loadChapters() {
        val activePackId = _coreState.value.navigation.activePackId?.value ?: run {
            // Clear chapter state if no pack is active
            _coreState.update {
                it.copy(
                    chapters = emptyList(),
                    chapterProgresses = emptyMap(),
                    activeChapterId = null
                )
            }
            Log.d(logTag, "loadChapters: no active pack, clearing chapters")
            return
        }

        if (!lessonStore.hasChapters(activePackId)) {
            // Pack has no chapters - clear chapter state
            _coreState.update {
                it.copy(
                    chapters = emptyList(),
                    chapterProgresses = emptyMap(),
                    activeChapterId = null
                )
            }
            Log.d(logTag, "loadChapters: pack $activePackId has no chapters, clearing")
            return
        }

        Log.d(logTag, "loadChapters: loading chapters for pack $activePackId")
        val chapters = lessonStore.getChapters(activePackId)
        val chapterProgressStore = container.chapterProgressStore(activePackId)
        val chapterProgresses = chapterProgressStore.loadAll()

        // Determine active chapter ID (first incomplete or first chapter)
        val activeChapterId = determineActiveChapter(chapters, chapterProgresses)

        Log.d(logTag, "loadChapters: updating state with ${chapters.size} chapters, activeChapterId=$activeChapterId")

        _coreState.update {
            it.copy(
                chapters = chapters,
                chapterProgresses = chapterProgresses,
                activeChapterId = activeChapterId
            )
        }
    }

    /**
     * Determine the active chapter ID based on progress.
     * Returns the first incomplete chapter, or the last chapter if all are complete.
     */
    private fun determineActiveChapter(
        chapters: List<Chapter>,
        chapterProgresses: Map<String, ChapterProgress>
    ): String? {
        if (chapters.isEmpty()) return null

        // Find first incomplete chapter
        for (chapter in chapters) {
            val progress = chapterProgresses[chapter.chapterId]
            if (progress == null || progress.lessonsCompleted < chapter.lessons.size) {
                return chapter.chapterId
            }
        }

        // All chapters complete - return the last one
        return chapters.lastOrNull()?.chapterId
    }

    /**
     * Update chapter progress for a specific lesson.
     * Call this when lesson mastery changes (card practiced, lesson completed).
     *
     * @param lessonId The lesson ID that was updated
     */
    private fun updateChapterProgress(lessonId: String) {
        val activePackId = _coreState.value.navigation.activePackId?.value ?: return
        val selectedLanguageId = _coreState.value.navigation.selectedLanguageId?.value ?: return

        if (!lessonStore.hasChapters(activePackId)) {
            return
        }

        val chapters = lessonStore.getChapters(activePackId)
        val chapterProgressStore = container.chapterProgressStore(activePackId)

        // Find which chapter(s) contain this lesson
        val affectedChapters = chapters.filter { chapter ->
            lessonId in chapter.lessons
        }

        if (affectedChapters.isEmpty()) {
            return
        }

        // Get mastery states for all lessons in the pack
        val allLessonMasteryStates = mutableMapOf<String, LessonMasteryState>()
        for (lid in lessonStore.getLessonIdsForPack(activePackId)) {
            val masteryState = masteryStore.get(lid, selectedLanguageId) ?: LessonMasteryState(LessonId(lid), LanguageId(selectedLanguageId))
            allLessonMasteryStates[lid] = masteryState
        }

        // Update progress for each affected chapter
        for (chapter in affectedChapters) {
            val newProgress = ChapterProgressCalculator.calculateChapterProgress(
                chapter = chapter,
                masteryStates = allLessonMasteryStates
            )

            // Persist to store atomically
            chapterProgressStore.upsertProgress(newProgress)

            // Update in-memory state
            _coreState.update {
                it.copy(
                    chapterProgresses = it.chapterProgresses.toMutableMap().apply {
                        put(newProgress.chapterId, newProgress)
                    }
                )
            }
        }
    }

    /**
     * Get chapter progress for a specific chapter.
     * Returns null if chapter has no progress data.
     */
    fun getChapterProgress(chapterId: String): ChapterProgress? {
        return _coreState.value.chapterProgresses[chapterId]
    }

    fun getCompletedLessonIds(chapter: Chapter): Set<String> {
        val languageId = _coreState.value.navigation.selectedLanguageId?.value ?: return emptySet()
        return chapter.lessons.filter { lessonId ->
            val mastery = masteryStore.get(lessonId, languageId)
            mastery?.completedAtMs != null // Lesson explicitly completed
        }.toSet()
    }
}

/**
 * UI model for a chapter card in the roadmap.
 */
data class ChapterCardUi(
    val chapter: com.alexpo.grammermate.data.Chapter,
    val progress: com.alexpo.grammermate.data.ChapterProgress,
    val status: ChapterStatus
)

/**
 * Status of a chapter in the roadmap.
 */
enum class ChapterStatus {
    ACTIVE,     // Started but not completed
    DONE        // All lessons completed
}
