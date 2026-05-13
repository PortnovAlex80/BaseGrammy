package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import com.alexpo.grammermate.data.SubmitResult
import com.alexpo.grammermate.data.TrainingUiState
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.LessonStoreImpl
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.AppConfigStore
import com.alexpo.grammermate.data.AppConfigStoreImpl
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.ProgressStoreImpl
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.StoryPhase
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VocabEntry
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.MasteryStoreImpl
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.StreakStore
import com.alexpo.grammermate.data.StreakStoreImpl
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.StoreFactory
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.HiddenCardStore
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.data.BackupManagerImpl
import com.alexpo.grammermate.data.ProfileStore
import com.alexpo.grammermate.data.VocabProgressStore
import com.alexpo.grammermate.data.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.alexpo.grammermate.feature.boss.BossBattleRunner
import com.alexpo.grammermate.feature.boss.BossCommand
import com.alexpo.grammermate.feature.boss.BossOrchestrator
import com.alexpo.grammermate.feature.daily.BlockProgress
import com.alexpo.grammermate.feature.daily.DailyPracticeCoordinator
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import com.alexpo.grammermate.feature.progress.BadSentenceHelper
import com.alexpo.grammermate.feature.progress.BadSentenceResult
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
import com.alexpo.grammermate.feature.vocab.VocabSubmitResult
import com.alexpo.grammermate.shared.SettingsActionHandler
import com.alexpo.grammermate.shared.SettingsResult
import com.alexpo.grammermate.shared.audio.AudioCoordinator

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val logTag = "GrammarMate"
    private val lessonStore = LessonStoreImpl(application)
    private val progressStore = ProgressStoreImpl(application)
    private val configStore = AppConfigStoreImpl(application)
    private val masteryStore = MasteryStoreImpl(application)
    private val streakStore = StreakStoreImpl(application)
    private val storeFactory = StoreFactory.getInstance(application)
    private val badSentenceStore = storeFactory.getBadSentenceStore()
    private val hiddenCardStore = HiddenCardStore(application)
    private val vocabProgressStore = VocabProgressStore(application)
    private var wordMasteryStore = storeFactory.getWordMasteryStore(null)
    private val backupManager = BackupManagerImpl(application)
    private val profileStore = ProfileStore(application)
    private val _coreState = MutableStateFlow(TrainingUiState())

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
    private val subLessonSizeMin = TrainingConfig.SUB_LESSON_SIZE_MIN
    private val subLessonSizeMax = TrainingConfig.SUB_LESSON_SIZE_MAX
    private val subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
    private val eliteStepCount = TrainingConfig.ELITE_STEP_COUNT
    private var eliteSizeMultiplier: Double = 1.25

    private val streakManager = StreakManager(streakStore)
    private val bossBattleRunner = BossBattleRunner()

    private val progressTracker = ProgressTracker(
        stateAccess = stateAccess,
        masteryStore = masteryStore,
        progressStore = progressStore,
        lessonStore = lessonStore
    )

    private val cardProvider = CardProvider(
        subLessonSize = subLessonSize,
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
        onTimerSaveProgress = { saveProgress() }
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
        storeFactory = storeFactory
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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _coreState.value)

    private val settingsActionHandler = SettingsActionHandler(
        stateAccess = stateAccess,
        configStore = configStore,
        profileStore = profileStore,
        backupManager = backupManager,
        coroutineScope = viewModelScope,
        resolveEliteUnlocked = { lessons, testMode -> sessionRunner.resolveEliteUnlocked(lessons, testMode) }
    )

    init {
        Log.d(logTag, "Update: duolingo sfx, prompt in speech UI, voice loop rules, stop resets progress")
        lessonStore.ensureSeedData()
        badSentenceStore.migrateIfNeeded(lessonStore)
        val progress = progressStore.load()
        val config = configStore.load()
        val profile = profileStore.load()
        eliteSizeMultiplier = config.eliteSizeMultiplier
        val bossLessonRewards = bossOrchestrator.parseBossRewards(progress.bossLessonRewards)
        val bossMegaRewards = bossOrchestrator.parseBossRewards(progress.bossMegaRewards)
        val languages = lessonStore.getLanguages()
        val packs = lessonStore.getInstalledPacks()
        val selectedLanguageId = languages.firstOrNull { it.id == progress.languageId }?.id ?: com.alexpo.grammermate.data.LanguageId("en")
        val lessons = lessonStore.getLessons(selectedLanguageId.value)
        val selectedLessonId = progress.lessonId?.let { id ->
            lessons.firstOrNull { it.id.value == id }?.id
        } ?: lessons.firstOrNull()?.id
        val normalizedEliteSpeeds = sessionRunner.normalizeEliteSpeeds(progress.eliteBestSpeeds)
        val restoredScreen = "HOME"
        val streakData = streakStore.getCurrentStreak(selectedLanguageId.value)
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
        _coreState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(languages = languages, installedPacks = packs, selectedLanguageId = selectedLanguageId, activePackId = initialActivePackId, activePackLessonIds = initialPackLessonIds, lessons = lessons, selectedLessonId = selectedLessonId, mode = progress.mode, userName = profile.userName, initialScreen = restoredScreen), cardSession = it.cardSession.copy(sessionState = progress.state, currentIndex = progress.currentIndex, correctCount = progress.correctCount, incorrectCount = progress.incorrectCount, incorrectAttemptsForCard = progress.incorrectAttemptsForCard, activeTimeMs = progress.activeTimeMs, voiceActiveMs = progress.voiceActiveMs, voiceWordCount = progress.voiceWordCount, hintCount = progress.hintCount, testMode = config.testMode, vocabSprintLimit = config.vocabSprintLimit, currentStreak = streakData.currentStreak, longestStreak = streakData.longestStreak, badSentenceCount = initialActivePackId?.let { pid -> badSentenceStore.getBadSentenceCount(pid.value) } ?: 0, hintLevel = config.hintLevel), elite = it.elite.copy(eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1), eliteBestSpeeds = normalizedEliteSpeeds, eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, config.testMode), eliteSizeMultiplier = config.eliteSizeMultiplier))
        }
        // Initialize feature-owned state from persisted progress
        bossOrchestrator.initRewards(bossLessonRewards, bossMegaRewards)
        vocabSprintRunner.updateMasteredCount(wordMasteryStore.getMasteredCount())
        dailyPracticeCoordinator.updateCursor(progress.dailyCursor)
        refreshDrillVisibility()
        rebindWordMasteryStore(initialActivePackId?.value)
        rebuildSchedules(lessons)
        buildSessionCards()
        refreshFlowerStates()
        if (_coreState.value.cardSession.sessionState == SessionState.ACTIVE && _coreState.value.cardSession.currentCard != null) {
            sessionRunner.resumeTimer()
            _coreState.value.cardSession.currentCard?.let { recordCardShowForMastery(it) }
            if (_coreState.value.cardSession.inputMode == InputMode.VOICE) {
                _coreState.update { it.copy(cardSession = it.cardSession.copy(voiceTriggerToken = it.cardSession.voiceTriggerToken + 1)) }
            }
        }
        // Force reload default packs on every app start to ensure latest lesson content.
        // NOTE: We read _coreState.value INSIDE the update lambda to avoid a TOCTOU race
        // where the user changes language/lesson on the main thread while we captured
        // stale values on the IO thread.
        viewModelScope.launch(Dispatchers.IO) {
            val reloaded = lessonStore.forceReloadDefaultPacks()
            if (!reloaded) return@launch
            val languages = lessonStore.getLanguages()
            val packs = lessonStore.getInstalledPacks()
            withContext(Dispatchers.Main) {
                _coreState.update { current ->
                    val currentLang = current.navigation.selectedLanguageId
                    val selectedLang = languages.firstOrNull { it.id == currentLang }?.id
                        ?: languages.firstOrNull()?.id
                        ?: com.alexpo.grammermate.data.LanguageId("en")
                    val lessons = lessonStore.getLessons(selectedLang.value)
                    val currentLessonId = current.navigation.selectedLessonId
                    val selectedLessonId = lessons.firstOrNull { it.id == currentLessonId }?.id
                        ?: lessons.firstOrNull()?.id
                    val reloadedPackIds = packs.map { it.packId }.toSet()
                    // Keep current activePackId if it still exists after reload,
                    // otherwise derive from lessonId, otherwise fall back to first pack.
                    val updatedPackId = if (current.navigation.activePackId != null && current.navigation.activePackId in reloadedPackIds) {
                        current.navigation.activePackId
                    } else {
                        selectedLessonId?.let { com.alexpo.grammermate.data.PackId(lessonStore.getPackIdForLesson(it.value) ?: return@let null) }
                            ?: packs.firstOrNull { it.languageId == selectedLang }?.packId
                    }
                    val updatedPackLessonIds = updatedPackId?.let { lessonStore.getLessonIdsForPack(it.value) }
                    current.copy(navigation = current.navigation.copy(languages = languages, installedPacks = packs, selectedLanguageId = selectedLang, activePackId = updatedPackId, activePackLessonIds = updatedPackLessonIds, lessons = lessons, selectedLessonId = selectedLessonId), elite = current.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, current.cardSession.testMode)))
                }
                refreshDrillVisibility()
                val updatedLessons = lessonStore.getLessons(_coreState.value.navigation.selectedLanguageId.value)
                rebuildSchedules(updatedLessons)
                buildSessionCards()
                refreshFlowerStates()
            }
        }

        // TTS state collection
        audioCoordinator.checkTtsModel()
        audioCoordinator.checkAllTtsModels()
        audioCoordinator.checkAsrModel()
        audioCoordinator.startBackgroundTtsDownload()
        audioCoordinator.startTtsStateCollection()

        // Pre-build daily practice session in background for faster start.
        // Uses progress-based lesson, NOT selectedLessonId, so that browsing
        // locked lessons does not affect the daily practice session.
        viewModelScope.launch(Dispatchers.IO) {
            val state = _coreState.value
            val packId = state.navigation.activePackId
            val langId = state.navigation.selectedLanguageId
            val progressInfo = resolveProgressLessonInfo()
            if (packId != null && progressInfo != null) {
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
        return if (packId != null) {
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

    fun onInputChanged(text: String) = sessionRunner.onInputChanged(text)

    fun onVoicePromptStarted() = sessionRunner.onVoicePromptStarted()

    fun setInputMode(mode: InputMode) = sessionRunner.setInputMode(mode)

    fun selectLanguage(languageId: String) {
        sessionRunner.pauseTimer()
        vocabSession = emptyList()
        sessionRunner.clearAllCards()
        val lessons = lessonStore.getLessons(languageId)
        val selectedLessonId = lessons.firstOrNull()?.id
        // Derive activePackId for the new language from the selected lesson.
        val newPackId = selectedLessonId?.let { lessonStore.getPackIdForLesson(it.value) }
            ?: lessonStore.getInstalledPacks().firstOrNull { it.languageId.value == languageId }?.packId?.value
        val newPackLessonIds = newPackId?.let { lessonStore.getLessonIdsForPack(it) }
        rebindWordMasteryStore(newPackId)
        _coreState.update {
            it.resetAllSessionState().copy(navigation = it.navigation.copy(selectedLanguageId = com.alexpo.grammermate.data.LanguageId(languageId), lessons = lessons, selectedLessonId = selectedLessonId, activePackId = newPackId?.let { pid -> com.alexpo.grammermate.data.PackId(pid) }, activePackLessonIds = newPackLessonIds), elite = it.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, it.cardSession.testMode)))
        }
        // Reset feature-owned state
        bossOrchestrator.resetStateKeepRewards()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        vocabSprintRunner.updateMasteredCount(wordMasteryStore.getMasteredCount())
        dailyPracticeCoordinator.resetState()
        refreshDrillVisibility()
        rebuildSchedules(lessons)
        buildSessionCards()
        refreshFlowerStates()
        saveProgress()
        audioCoordinator.ttsModelManager.currentLanguageId = languageId
        audioCoordinator.checkTtsModel()
        // Only switch ASR language if engine is already initialized and ready.
        // Calling setLanguage() when ASR is not ready crashes the native layer.
        if (audioCoordinator.asrEngine?.isReady == true) {
            audioCoordinator.asrEngine.setLanguage(languageId)
        }
    }

    fun selectLesson(lessonId: String) {
        sessionRunner.pauseTimer()
        vocabSession = emptyList()
        sessionRunner.clearAllCards()

        // Resolve the pack for this lesson and set as active
        val packId = lessonStore.getPackIdForLesson(lessonId)
        val packLessonIds = packId?.let { lessonStore.getLessonIdsForPack(it) }
        rebindWordMasteryStore(packId)

        // Rebuild schedules BEFORE reading them
        rebuildSchedules(_coreState.value.navigation.lessons)

        // Calculate active sub-lesson index based on completed count
        val typedLessonId = com.alexpo.grammermate.data.LessonId(lessonId)
        val schedule = lessonSchedules[typedLessonId]
        val subLessons = schedule?.subLessons.orEmpty()
        val mastery = masteryStore.get(lessonId, _coreState.value.navigation.selectedLanguageId.value)
        val completedCount = progressTracker.calculateCompletedSubLessons(
            subLessons = subLessons,
            mastery = mastery,
            lessonId = typedLessonId,
            lessons = _coreState.value.navigation.lessons
        )
        val nextActiveIndex = completedCount.coerceAtMost((subLessons.size - 1).coerceAtLeast(0))

        _coreState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(selectedLessonId = typedLessonId, activePackId = packId?.let { pid -> com.alexpo.grammermate.data.PackId(pid) }, activePackLessonIds = packLessonIds, mode = TrainingMode.LESSON), cardSession = it.cardSession.copy(activeSubLessonIndex = nextActiveIndex, completedSubLessonCount = completedCount, currentCard = null))
        }
        // Reset feature-owned state for session change
        bossOrchestrator.resetState()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        dailyPracticeCoordinator.resetState()
        refreshDrillVisibility()
        buildSessionCards()
        refreshFlowerStates()
        saveProgress()
    }

    fun selectPack(packId: String) {
        val packLessonIds = lessonStore.getLessonIdsForPack(packId)
        if (packLessonIds.isNotEmpty()) {
            val currentLessonId = _coreState.value.navigation.selectedLessonId
            val lessonId = if (currentLessonId != null && currentLessonId.value in packLessonIds) currentLessonId.value else packLessonIds.first()
            selectLesson(lessonId)
        } else {
            // Drill-only pack — set activePackId without selecting a lesson
            rebindWordMasteryStore(packId)
            _coreState.update {
                it.copy(navigation = it.navigation.copy(activePackId = com.alexpo.grammermate.data.PackId(packId), activePackLessonIds = emptyList(), selectedLessonId = null))
            }
            refreshDrillVisibility()
            saveProgress()
        }
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

        Log.d(logTag, "Answer submitted: accepted=${result.accepted}")
        return SubmitResult(result.accepted, result.hintShown)
    }

    fun nextCard(triggerVoice: Boolean = false) {
        val state = _coreState.value
        val bossState = bossOrchestrator.stateFlow.value

        // Boss-specific progress handling
        if (bossState.bossActive) {
            val nextIndex = (state.cardSession.currentIndex + 1).coerceAtMost(sessionRunner.getSessionCards().lastIndex)
            val (advanceResult, bossCommands) = bossOrchestrator.advanceBossProgressOnNextCard(nextIndex, sessionRunner.getSessionCards().size)

            val events = sessionRunner.nextCard(triggerVoice)
            handleSessionEvents(events)
            handleBossCommands(bossCommands)

            // Apply boss pause if reward threshold was crossed
            if (advanceResult.rewardMessageChanged) {
                _coreState.update { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED)) }
            }
        } else {
            val events = sessionRunner.nextCard(triggerVoice)
            handleSessionEvents(events)
        }
    }

    fun prevCard() = handleSessionEvents(sessionRunner.prevCard())

    fun togglePause() = handleSessionEvents(sessionRunner.togglePause())

    fun pauseSession() = handleSessionEvents(sessionRunner.pauseSession())

    fun finishSession() {
        if (bossOrchestrator.stateFlow.value.bossActive) {
            finishBoss()
            return
        }
        val (_, events) = sessionRunner.finishSession()
        handleSessionEvents(events)
    }

    fun showAnswer() = handleSessionEvents(sessionRunner.showAnswer())

    fun importLesson(uri: Uri) {
        val languageId = _coreState.value.navigation.selectedLanguageId
        val lesson = lessonStore.importFromUri(languageId.value, uri, getApplication<Application>().contentResolver)
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
            rebuildSchedules(lessons)
            buildSessionCards()
            saveProgress()
        } catch (e: Exception) {
            Log.e(logTag, "Lesson pack import failed", e)
        }
    }
    fun resetAndImportLesson(uri: Uri) {
        val languageId = _coreState.value.navigation.selectedLanguageId
        lessonStore.deleteAllLessons(languageId.value)
        val lesson = lessonStore.importFromUri(languageId.value, uri, getApplication<Application>().contentResolver)
        refreshLessons(lesson.id.value)
    }

    fun deleteLesson(lessonId: String) {
        val languageId = _coreState.value.navigation.selectedLanguageId
        lessonStore.deleteLesson(languageId.value, lessonId)
        val selected = if (_coreState.value.navigation.selectedLessonId?.value == lessonId) null else _coreState.value.navigation.selectedLessonId
        refreshLessons(selected?.value)
    }

    fun createEmptyLesson(title: String) {
        val languageId = _coreState.value.navigation.selectedLanguageId
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
        rebuildSchedules(lessons)
        buildSessionCards()
        saveProgress()
    }

    fun deleteAllLessons() {
        val languageId = _coreState.value.navigation.selectedLanguageId
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
        val languageId = state.navigation.selectedLanguageId.value
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

    fun updateVocabSprintLimit(limit: Int) = settingsActionHandler.updateVocabSprintLimit(limit)

    fun setHintLevel(level: HintLevel) = settingsActionHandler.setHintLevel(level)

    fun resumeFromSettings() = handleSessionEvents(sessionRunner.resumeFromSettings())

    fun selectSubLesson(index: Int) = handleSessionEvents(sessionRunner.selectSubLesson(index))

    fun openEliteStep(index: Int) = handleSessionEvents(sessionRunner.openEliteStep(index))

    fun cancelEliteSession() = handleSessionEvents(sessionRunner.cancelEliteSession())

    // ── Daily Practice ────────────────────────────────────────────────────

    fun hasResumableDailySession(): Boolean {
        return dailyPracticeCoordinator.hasResumableDailySession()
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun startDailyPractice(lessonLevel: Int): Boolean {
        return dailyPracticeCoordinator.startDailyPractice(
            resolveProgressLessonInfo = { resolveProgressLessonInfo() },
            onStoreFirstSessionCardIds = { sentenceIds, verbIds ->
                storeFirstSessionCardIds(sentenceIds, verbIds)
            }
        )
    }

    /**
     * Store the first session of the day's card IDs in the cursor state.
     * This allows Repeat to reconstruct the exact same cards even after restart.
     */
    private fun storeFirstSessionCardIds(sentenceIds: List<String>, verbIds: List<String>) {
        val updatedCursor = progressTracker.storeFirstSessionCardIds(
            currentCursor = dailyPracticeCoordinator.getCursor(),
            sentenceIds = sentenceIds,
            verbIds = verbIds
        )
        dailyPracticeCoordinator.updateCursor(updatedCursor)
        saveProgress()
    }

    /**
     * Advance the daily cursor offsets after a session is built.
     * If the sentence offset exceeds the current lesson's card count,
     * advances currentLessonIndex and resets sentenceOffset.
     */
    private fun advanceCursor(sentenceCount: Int) {
        val s = _coreState.value
        val advanced = progressTracker.advanceCursor(
            currentCursor = dailyPracticeCoordinator.getCursor(),
            sentenceCount = sentenceCount,
            selectedLanguageId = s.navigation.selectedLanguageId
        )
        dailyPracticeCoordinator.updateCursor(advanced)
    }

    suspend fun repeatDailyPractice(lessonLevel: Int): Boolean {
        return dailyPracticeCoordinator.repeatDailyPractice(
            lessonLevel = lessonLevel,
            resolveProgressLessonInfo = { resolveProgressLessonInfo() }
        )
    }

    fun advanceDailyTask(): Boolean {
        return dailyPracticeCoordinator.advanceDailyTask(
            onPersistVerbProgress = { card -> dailyPracticeCoordinator.persistDailyVerbProgress(card) }
        )
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

    fun advanceDailyBlock(): Boolean {
        // Move to the next block but do NOT advance the daily cursor yet.
        // Cursor advancement happens only when the full session completes successfully,
        // and only for blocks where all cards were answered via VOICE or KEYBOARD.
        return dailyPracticeCoordinator.advanceDailyBlock()
    }

    fun persistDailyVerbProgress(card: VerbDrillCard) {
        dailyPracticeCoordinator.persistDailyVerbProgress(card)
    }

    fun repeatDailyBlock(): Boolean {
        return dailyPracticeCoordinator.repeatDailyBlock(
            resolveProgressLessonInfo = { resolveProgressLessonInfo() }
        )
    }

    fun cancelDailySession() {
        val sentenceCount = dailyPracticeCoordinator.cancelDailySession()
        // Orchestrator: if coordinator signals cursor advancement, delegate to ProgressTracker
        if (sentenceCount != null) {
            advanceCursor(sentenceCount)
        }
    }

    /**
     * Record an SRS rating for the current vocab flashcard in Daily Practice.
     * Rating: 0=Again, 1=Hard, 2=Good, 3=Easy — mirrors VocabDrillViewModel.answerRating logic.
     */
    fun rateVocabCard(rating: com.alexpo.grammermate.data.SrsRating) {
        dailyPracticeCoordinator.rateVocabCard(rating)
    }

    fun getDailyCurrentTask(): DailyTask? {
        return dailyPracticeCoordinator.getDailyCurrentTask()
    }

    fun getDailyBlockProgress(): BlockProgress {
        return dailyPracticeCoordinator.getDailyBlockProgress()
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

    fun getDailySentenceAnswer(): String? {
        return dailyPracticeCoordinator.getDailySentenceAnswer()
    }

    fun getDailyVerbAnswer(): String? {
        return dailyPracticeCoordinator.getDailyVerbAnswer()
    }

    // ── Drill Mode ───────────────────────────────────────────────────────
    // Seamless card training, no mastery/flower progress.
    // All drill cards in one continuous stream. Save position on exit.

    fun showDrillStartDialog(lessonId: String) = sessionRunner.showDrillStartDialog(lessonId)

    fun startDrill(resume: Boolean) {
        vocabSession = emptyList()
        handleSessionEvents(sessionRunner.startDrill(resume))
        _coreState.update {
            it.copy(cardSession = it.cardSession.copy(badSentenceCount = badSentenceHelper.getBadSentenceCount()))
        }
    }

    fun dismissDrillDialog() = sessionRunner.dismissDrillDialog()

    fun exitDrillMode() {
        handleSessionEvents(sessionRunner.exitDrillMode())
        _coreState.update {
            it.copy(cardSession = it.cardSession.copy(badSentenceCount = badSentenceHelper.getBadSentenceCount()))
        }
    }

    // ── End Drill Mode ───────────────────────────────────────────────────

    fun openStory(phase: StoryPhase) = storyRunner.openStory(phase)

    fun hasVocabProgress(): Boolean {
        val lessonId = _coreState.value.navigation.selectedLessonId ?: return false
        val languageId = _coreState.value.navigation.selectedLanguageId
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

    fun clearStoryError() = storyRunner.clearStoryError()

    fun clearVocabError() = vocabSprintRunner.clearError()

    fun onVocabInputChanged(text: String) = vocabSprintRunner.onInputChanged(text)

    fun setVocabInputMode(mode: InputMode) = vocabSprintRunner.setInputMode(mode)

    fun requestVocabVoice() = vocabSprintRunner.requestVoice()

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

    fun showVocabAnswer() = vocabSprintRunner.showAnswer()


    /**
     * Start a Mix Challenge (interleaved practice) session.
     * Delegates to [BossOrchestrator].
     */
    fun startMixChallenge(): Boolean {
        val (success, commands) = bossOrchestrator.startMixChallenge()
        handleBossCommands(commands)
        return success
    }

    fun startBossLesson() = handleBossCommands(bossOrchestrator.startBossLesson())

    fun startBossMega() = handleBossCommands(bossOrchestrator.startBossMega())

    fun startBossElite() = handleBossCommands(bossOrchestrator.startBossElite())

    fun finishBoss() = handleBossCommands(bossOrchestrator.finishBoss())

    fun clearBossRewardMessage() = handleBossCommands(bossOrchestrator.clearBossRewardMessage())

    fun clearBossError() = bossOrchestrator.clearBossError()

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
        return progressTracker.resolveProgressLessonInfo(
            activePackId = s.navigation.activePackId,
            selectedLanguageId = s.navigation.selectedLanguageId,
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
        return progressTracker.getProgressLessonLevel(
            activePackId = s.navigation.activePackId,
            selectedLanguageId = s.navigation.selectedLanguageId,
            activePackLessonIds = s.navigation.activePackLessonIds,
            lessons = s.navigation.lessons,
            dailyCursor = dailyPracticeCoordinator.getCursor()
        )
    }

    // ── Audio delegations to AudioCoordinator ─────────────────────────────

    fun onTtsSpeak(text: String, speed: Float? = null) = audioCoordinator.onTtsSpeak(text, speed)

    fun setTtsSpeed(speed: Float) = audioCoordinator.setTtsSpeed(speed)

    fun setRuTextScale(scale: Float) {
        audioCoordinator.setRuTextScale(scale)
        configStore.save(configStore.load().copy(ruTextScale = scale.coerceIn(1.0f, 2.0f)))
    }

    fun startTtsDownload() = audioCoordinator.startTtsDownload()

    fun confirmTtsDownloadOnMetered() = audioCoordinator.confirmTtsDownloadOnMetered()

    fun dismissMeteredWarning() = audioCoordinator.dismissMeteredWarning()

    fun dismissTtsDownloadDialog() = audioCoordinator.dismissTtsDownloadDialog()

    fun startTtsDownloadForLanguage(languageId: String) = audioCoordinator.startTtsDownloadForLanguage(languageId)

    fun stopTts() = audioCoordinator.stopTts()

    fun checkAsrModel() = audioCoordinator.checkAsrModel()

    fun dismissAsrDownloadDialog() = audioCoordinator.dismissAsrDownloadDialog()

    fun startOfflineRecognition() {
        audioCoordinator.startOfflineRecognition { result ->
            onInputChanged(result)
            // Don't auto-submit — let user review recognized text and submit manually
        }
    }

    fun stopAsr() = audioCoordinator.stopAsr()

    fun setUseOfflineAsr(enabled: Boolean) = audioCoordinator.setUseOfflineAsr(enabled)

    fun setVoiceAutoStart(enabled: Boolean) = audioCoordinator.setVoiceAutoStart(enabled)

    fun startAsrDownload() = audioCoordinator.startAsrDownload()

    fun confirmAsrDownloadOnMetered() = audioCoordinator.confirmAsrDownloadOnMetered()

    fun dismissAsrMeteredWarning() = audioCoordinator.dismissAsrMeteredWarning()

    fun setTtsDownloadStateFromBackground(bgState: DownloadState) = audioCoordinator.setTtsDownloadStateFromBackground(bgState)

    override fun onCleared() {
        saveProgress()
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
    private fun recordCardShowForMastery(card: SentenceCard) {
        val s = _coreState.value
        progressTracker.recordCardShowForMastery(
            card = card,
            bossActive = bossOrchestrator.stateFlow.value.bossActive,
            isDrillMode = s.drill.isDrillMode,
            inputMode = s.cardSession.inputMode,
            selectedLanguageId = s.navigation.selectedLanguageId,
            lessons = s.navigation.lessons,
            selectedLessonId = s.navigation.selectedLessonId
        )
    }


    /**
     * Добавляет слово в выбранные для word bank режима
     */
    fun selectWordFromBank(word: String) = sessionRunner.selectWordFromBank(word)

    /**
     * Удаляет последнее выбранное слово
     */
    fun removeLastSelectedWord() = sessionRunner.removeLastSelectedWord()

    /**
     * Re-scope wordMasteryStore to the given packId.
     * Must be called whenever activePackId changes so that mastery reads/writes
     * go to the pack-scoped file instead of the legacy global file.
     */
    private fun rebindWordMasteryStore(packId: String?) {
        wordMasteryStore = storeFactory.getWordMasteryStore(packId)
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

    fun createProgressBackup() = settingsActionHandler.createProgressBackup()

    fun updateUserName(newName: String) = settingsActionHandler.updateUserName(newName)

    fun saveProgressNow() = handleSettingsResults(settingsActionHandler.saveProgressNow())

    fun onScreenChanged(screenName: String) = settingsActionHandler.onScreenChanged(screenName)

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
            is BadSentenceResult.AdvanceDrillCard -> sessionRunner.advanceDrillCard()
            is BadSentenceResult.SkipToNextCard -> sessionRunner.skipToNextCard()
            is BadSentenceResult.None -> {}
        }
    }

    fun unflagBadSentence() = badSentenceHelper.unflagBadSentence()

    fun isBadSentence(): Boolean = badSentenceHelper.isBadSentence()

    fun exportBadSentences(): String? = badSentenceHelper.exportBadSentences()

    // ── Daily Practice Bad Sentence Support ────────────────────────────────

    fun flagDailyBadSentence(cardId: String, languageId: String, sentence: String, translation: String, mode: String) =
        badSentenceHelper.flagDailyBadSentence(cardId, languageId, sentence, translation, mode)

    fun unflagDailyBadSentence(cardId: String) = badSentenceHelper.unflagDailyBadSentence(cardId)

    fun isDailyBadSentence(cardId: String): Boolean = badSentenceHelper.isDailyBadSentence(cardId)

    fun exportDailyBadSentences(): String? = badSentenceHelper.exportDailyBadSentences()

    fun hideCurrentCard() {
        when (val result = badSentenceHelper.hideCurrentCard()) {
            is BadSentenceResult.AdvanceDrillCard -> sessionRunner.advanceDrillCard()
            is BadSentenceResult.SkipToNextCard -> sessionRunner.skipToNextCard()
            is BadSentenceResult.None -> {}
        }
    }

    fun unhideCurrentCard() = badSentenceHelper.unhideCurrentCard()

    fun isCurrentCardHidden(): Boolean = badSentenceHelper.isCurrentCardHidden()

    // ── Session event handling (private methods) ────────────────────────────

    private fun handleSessionEvents(events: List<SessionEvent>) {
        for (event in events) {
            when (event) {
                is SessionEvent.SaveProgress -> saveProgress()
                is SessionEvent.RefreshFlowerStates -> refreshFlowerStates()
                is SessionEvent.UpdateStreak -> updateStreak()
                is SessionEvent.BuildSessionCards -> buildSessionCards()
                is SessionEvent.PlaySuccess -> audioCoordinator.playSuccessSound()
                is SessionEvent.PlayError -> audioCoordinator.playErrorSound()
                is SessionEvent.RecordCardShow -> recordCardShowForMastery(event.card)
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
                is SessionEvent.RebuildSchedules -> rebuildSchedules(event.lessons)
                is SessionEvent.Composite -> handleSessionEvents(event.events)
            }
        }
    }

    private fun markSubLessonCardsShown(cards: List<SentenceCard>) {
        val s = _coreState.value
        progressTracker.markSubLessonCardsShown(
            cards = cards,
            inputMode = s.cardSession.inputMode,
            selectedLessonId = s.navigation.selectedLessonId,
            selectedLanguageId = s.navigation.selectedLanguageId,
            lessons = s.navigation.lessons
        )
    }
    private fun checkAndMarkLessonCompleted() {
        val s = _coreState.value
        progressTracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = s.cardSession.completedSubLessonCount,
            selectedLessonId = s.navigation.selectedLessonId,
            selectedLanguageId = s.navigation.selectedLanguageId
        )
    }
    private fun refreshFlowerStates() = flowerRefresher.refreshFlowerStates()
    private fun updateStreak() {
        val languageId = _coreState.value.navigation.selectedLanguageId
        val (updatedStreak, isNewStreak) = streakManager.recordSubLessonCompletion(languageId.value)
        if (isNewStreak && updatedStreak.currentStreak > 0) {
            val message = streakManager.getCelebrationMessage(updatedStreak.currentStreak)
            _coreState.update {
                it.copy(cardSession = it.cardSession.copy(currentStreak = updatedStreak.currentStreak, longestStreak = updatedStreak.longestStreak, streakMessage = message, streakCelebrationToken = it.cardSession.streakCelebrationToken + 1))
            }
        } else {
            _coreState.update {
                it.copy(cardSession = it.cardSession.copy(currentStreak = updatedStreak.currentStreak, longestStreak = updatedStreak.longestStreak))
            }
        }
    }
    private fun saveProgress() {
        val state = uiState.value
        val shouldBackup = progressTracker.saveProgress(
            state = state,
            forceBackup = forceBackupOnSave,
            normalizedEliteSpeeds = sessionRunner.normalizeEliteSpeeds(state.elite.eliteBestSpeeds)
        )
        if (shouldBackup) {
            forceBackupOnSave = false
            createProgressBackup()
        }
    }
    private fun buildSessionCards() {
        if (bossOrchestrator.stateFlow.value.bossActive || _coreState.value.elite.eliteActive || _coreState.value.drill.isDrillMode) return
        val state = _coreState.value
        val hiddenIds = hiddenCardStore.getHiddenCardIds()
        val lessons = state.navigation.lessons
        val mastery = state.navigation.selectedLessonId?.let {
            masteryStore.get(it.value, state.navigation.selectedLanguageId.value)
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
    }
    private fun rebuildSchedules(lessons: List<Lesson>) {
        lessonSchedules = cardProvider.buildSchedules(lessons, lessonSchedules)
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
        val lessons = lessonStore.getLessons(languageId.value)
        val selected = selectedLessonId?.let { com.alexpo.grammermate.data.LessonId(it) } ?: lessons.firstOrNull()?.id
        _coreState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(lessons = lessons, selectedLessonId = selected), elite = it.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, it.cardSession.testMode)))
        }
        // Reset feature-owned state for session change
        bossOrchestrator.resetState()
        storyRunner.resetState()
        vocabSprintRunner.resetState()
        dailyPracticeCoordinator.resetState()
        rebuildSchedules(lessons)
        buildSessionCards()
        saveProgress()
    }
    private fun resetStores(app: Application) = progressTracker.resetStores(app)
    private fun resetStoresForLanguage(app: Application, languageId: String) = progressTracker.resetStoresForLanguage(app, languageId)
    private fun resetDrillFiles(app: Application) = progressTracker.resetDrillFiles(app)
    private fun resetDrillFilesForPack(app: Application, packId: String) = progressTracker.resetDrillFilesForPack(app, packId)
    private fun clearWordMastery() = wordMasteryStore.saveAll(emptyMap())
    private fun resetDailyState() = dailyPracticeCoordinator.resetState()

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
                is SettingsResult.None -> {}
            }
        }
    }

    // -- ProgressResult handler --
    private fun handleProgressResults(results: List<ProgressResult>) {
        for (result in results) {
            when (result) {
                is ProgressResult.RebuildSchedules -> rebuildSchedules(result.lessons)
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
}
