package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import com.alexpo.grammermate.data.AsrState
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.AppConfigStore
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.StoryPhase
import com.alexpo.grammermate.data.StoryQuiz
import com.alexpo.grammermate.data.SubLessonType
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VocabEntry
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.FlowerVisual
import com.alexpo.grammermate.data.StreakStore
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.HiddenCardStore
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.data.ProfileStore
import com.alexpo.grammermate.data.UserProfile
import com.alexpo.grammermate.data.VocabProgressStore
import com.alexpo.grammermate.data.WordMasteryStore
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.alexpo.grammermate.ui.helpers.AnswerValidator
import com.alexpo.grammermate.ui.helpers.BossBattleRunner
import com.alexpo.grammermate.ui.helpers.WordBankGenerator
import com.alexpo.grammermate.ui.helpers.CardProvider
import com.alexpo.grammermate.ui.helpers.CardSetResult
import com.alexpo.grammermate.ui.helpers.StreakManager
import com.alexpo.grammermate.ui.helpers.DailyPracticeCoordinator
import com.alexpo.grammermate.ui.helpers.AudioCoordinator
import com.alexpo.grammermate.ui.helpers.FlowerRefresher
import com.alexpo.grammermate.ui.helpers.ProgressTracker
import com.alexpo.grammermate.ui.helpers.SessionRunner
import com.alexpo.grammermate.ui.helpers.TrainingStateAccess
import com.alexpo.grammermate.ui.helpers.StoryRunner
import com.alexpo.grammermate.ui.helpers.VocabSprintRunner

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val logTag = "GrammarMate"
    private val lessonStore = LessonStore(application)
    private val progressStore = ProgressStore(application)
    private val configStore = AppConfigStore(application)
    private val masteryStore = MasteryStore(application)
    private val streakStore = StreakStore(application)
    private val badSentenceStore = BadSentenceStore(application)
    private val hiddenCardStore = HiddenCardStore(application)
    private val vocabProgressStore = VocabProgressStore(application)
    private var wordMasteryStore = WordMasteryStore(application, packId = null)
    private val backupManager = BackupManager(application)
    private val profileStore = ProfileStore(application)
    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState
    private val audioCoordinator = AudioCoordinator(
        stateAccess = object : TrainingStateAccess {
            override val uiState: StateFlow<TrainingUiState> = _uiState
            override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
                _uiState.update(transform)
            }
            override fun saveProgress() = this@TrainingViewModel.saveProgress()
        },
        appContext = application,
        coroutineScope = viewModelScope,
        configStore = configStore
    )
    private val answerValidator = AnswerValidator()
    private val vocabSprintRunner = VocabSprintRunner(
        stateAccess = object : TrainingStateAccess {
            override val uiState: StateFlow<TrainingUiState> = _uiState
            override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
                _uiState.update(transform)
            }
            override fun saveProgress() = this@TrainingViewModel.saveProgress()
        },
        lessonStore = lessonStore,
        vocabProgressStore = vocabProgressStore,
        answerValidator = answerValidator
    ).also { runner ->
        runner.onPlaySuccess = { audioCoordinator.playSuccessSound() }
        runner.onPlayError = { audioCoordinator.playErrorSound() }
        runner.onSaveProgress = { saveProgress() }
        runner.onForceBackup = { forceBackupOnSave = true }
    }

    private var vocabSession: List<VocabEntry>
        get() = vocabSprintRunner.vocabSession
        set(value) { vocabSprintRunner.vocabSession = value }
    private var subLessonTotal: Int = 0
    private var subLessonCount: Int = 0
    private var lessonSchedules: Map<String, LessonSchedule> = emptyMap()
    private var forceBackupOnSave: Boolean = false
    private val subLessonSizeMin = TrainingConfig.SUB_LESSON_SIZE_MIN
    private val subLessonSizeMax = TrainingConfig.SUB_LESSON_SIZE_MAX
    private val subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
    private val eliteStepCount = TrainingConfig.ELITE_STEP_COUNT
    private var eliteSizeMultiplier: Double = 1.25

    private val streakManager = StreakManager(streakStore)
    private val bossBattleRunner = BossBattleRunner()
    private val cardProvider = CardProvider(
        subLessonSize = subLessonSize,
        subLessonSizeMin = subLessonSizeMin,
        subLessonSizeMax = subLessonSizeMax,
        eliteSizeMultiplier = eliteSizeMultiplier,
        eliteStepCount = eliteStepCount
    )

    private val sessionRunner = SessionRunner(
        stateAccess = object : TrainingStateAccess {
            override val uiState: StateFlow<TrainingUiState> = _uiState
            override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
                _uiState.update(transform)
            }
            override fun saveProgress() = this@TrainingViewModel.saveProgress()
        },
        appContext = application,
        coroutineScope = viewModelScope,
        answerValidator = answerValidator,
        wordBankGenerator = WordBankGenerator,
        cardProvider = cardProvider,
        streakManager = streakManager
    ).also { runner ->
        runner.onRecordCardShow = { card -> recordCardShowForMastery(card) }
        runner.onMarkSubLessonCardsShown = { cards -> markSubLessonCardsShown(cards) }
        runner.onCheckAndMarkLessonCompleted = { checkAndMarkLessonCompleted() }
        runner.onCalculateCompletedSubLessons = { subs, mastery, lessonId ->
            calculateCompletedSubLessons(subs, mastery, lessonId)
        }
        runner.onRefreshFlowerStates = { refreshFlowerStates() }
        runner.onUpdateStreak = { updateStreak() }
        runner.onSaveProgress = { saveProgress() }
        runner.onPlaySuccess = { audioCoordinator.playSuccessSound() }
        runner.onPlayError = { audioCoordinator.playErrorSound() }
        runner.onBuildSessionCards = { buildSessionCards() }
        runner.onGetMastery = { lessonId, langId -> masteryStore.get(lessonId, langId) }
        runner.onGetSchedule = { lessonId -> lessonSchedules[lessonId] }
    }

    private val progressTracker = ProgressTracker(
        stateAccess = object : TrainingStateAccess {
            override val uiState: StateFlow<TrainingUiState> = _uiState
            override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
                _uiState.update(transform)
            }
            override fun saveProgress() = this@TrainingViewModel.saveProgress()
        },
        masteryStore = masteryStore,
        progressStore = progressStore,
        lessonStore = lessonStore
    )

    private val flowerRefresher = FlowerRefresher(
        stateAccess = object : TrainingStateAccess {
            override val uiState: StateFlow<TrainingUiState> = _uiState
            override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
                _uiState.update(transform)
            }
            override fun saveProgress() = this@TrainingViewModel.saveProgress()
        },
        masteryStore = masteryStore
    )

    private val dailyPracticeCoordinator = DailyPracticeCoordinator(
        stateAccess = object : TrainingStateAccess {
            override val uiState: StateFlow<TrainingUiState> = _uiState
            override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
                _uiState.update(transform)
            }
            override fun saveProgress() = this@TrainingViewModel.saveProgress()
        },
        appContext = application,
        answerValidator = answerValidator,
        lessonStore = lessonStore,
        masteryStore = masteryStore
    )

    private val storyRunner = StoryRunner(
        stateAccess = object : TrainingStateAccess {
            override val uiState: StateFlow<TrainingUiState> = _uiState
            override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
                _uiState.update(transform)
            }
            override fun saveProgress() = this@TrainingViewModel.saveProgress()
        },
        lessonStore = lessonStore,
    ).also { runner ->
        runner.onForceBackup = { forceBackupOnSave = true }
        runner.onSaveProgress = { saveProgress() }
    }

    init {
        Log.d(logTag, "Update: duolingo sfx, prompt in speech UI, voice loop rules, stop resets progress")
        lessonStore.ensureSeedData()
        badSentenceStore.migrateIfNeeded(lessonStore)
        val progress = progressStore.load()
        val config = configStore.load()
        val profile = profileStore.load()
        eliteSizeMultiplier = config.eliteSizeMultiplier
        val bossLessonRewards = parseBossRewards(progress.bossLessonRewards)
        val bossMegaRewards = parseBossRewards(progress.bossMegaRewards)
        val languages = lessonStore.getLanguages()
        val packs = lessonStore.getInstalledPacks()
        val selectedLanguageId = languages.firstOrNull { it.id == progress.languageId }?.id ?: "en"
        val lessons = lessonStore.getLessons(selectedLanguageId)
        val selectedLessonId = progress.lessonId?.let { id ->
            lessons.firstOrNull { it.id == id }?.id
        } ?: lessons.firstOrNull()?.id
        val normalizedEliteSpeeds = normalizeEliteSpeeds(progress.eliteBestSpeeds)
        val restoredScreen = "HOME"
        val streakData = streakStore.getCurrentStreak(selectedLanguageId)
        // Resolve activePackId: prefer saved value if pack still exists,
        // then derive from lessonId, then fall back to first pack for language.
        val savedPackId = progress.activePackId
        val allPackIds = packs.map { it.packId }.toSet()
        val initialActivePackId = if (savedPackId != null && savedPackId in allPackIds) {
            savedPackId
        } else {
            selectedLessonId?.let { lessonStore.getPackIdForLesson(it) }
        }
        val initialPackLessonIds = initialActivePackId?.let { lessonStore.getLessonIdsForPack(it) }
        _uiState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(languages = languages, installedPacks = packs, selectedLanguageId = selectedLanguageId, activePackId = initialActivePackId, activePackLessonIds = initialPackLessonIds, lessons = lessons, selectedLessonId = selectedLessonId, mode = progress.mode, userName = profile.userName, initialScreen = restoredScreen), cardSession = it.cardSession.copy(sessionState = progress.state, currentIndex = progress.currentIndex, correctCount = progress.correctCount, incorrectCount = progress.incorrectCount, incorrectAttemptsForCard = progress.incorrectAttemptsForCard, activeTimeMs = progress.activeTimeMs, voiceActiveMs = progress.voiceActiveMs, voiceWordCount = progress.voiceWordCount, hintCount = progress.hintCount, testMode = config.testMode, vocabSprintLimit = config.vocabSprintLimit, currentStreak = streakData.currentStreak, longestStreak = streakData.longestStreak, badSentenceCount = initialActivePackId?.let { badSentenceStore.getBadSentenceCount(it) } ?: 0, hintLevel = config.hintLevel), boss = it.boss.copy(bossLessonRewards = bossLessonRewards, bossMegaRewards = bossMegaRewards), elite = it.elite.copy(eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1), eliteBestSpeeds = normalizedEliteSpeeds, eliteUnlocked = resolveEliteUnlocked(lessons, config.testMode), eliteSizeMultiplier = config.eliteSizeMultiplier), audio = it.audio.copy(useOfflineAsr = config.useOfflineAsr, asrModelReady = audioCoordinator.asrModelManager.isReady()), vocabSprint = it.vocabSprint.copy(vocabMasteredCount = wordMasteryStore.getMasteredCount()), daily = it.daily.copy(dailyCursor = progress.dailyCursor))
        }
        rebindWordMasteryStore(initialActivePackId)
        rebuildSchedules(lessons)
        buildSessionCards()
        refreshFlowerStates()
        if (_uiState.value.cardSession.sessionState == SessionState.ACTIVE && _uiState.value.cardSession.currentCard != null) {
            resumeTimer()
            _uiState.value.cardSession.currentCard?.let { recordCardShowForMastery(it) }
            if (_uiState.value.cardSession.inputMode == InputMode.VOICE) {
                _uiState.update { it.copy(cardSession = it.cardSession.copy(voiceTriggerToken = it.cardSession.voiceTriggerToken + 1)) }
            }
        }
        // Force reload default packs on every app start to ensure latest lesson content.
        // NOTE: We read _uiState.value INSIDE the update lambda to avoid a TOCTOU race
        // where the user changes language/lesson on the main thread while we captured
        // stale values on the IO thread.
        viewModelScope.launch(Dispatchers.IO) {
            val reloaded = lessonStore.forceReloadDefaultPacks()
            if (!reloaded) return@launch
            val languages = lessonStore.getLanguages()
            val packs = lessonStore.getInstalledPacks()
            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    val currentLang = current.navigation.selectedLanguageId
                    val selectedLang = languages.firstOrNull { it.id == currentLang }?.id
                        ?: languages.firstOrNull()?.id
                        ?: "en"
                    val lessons = lessonStore.getLessons(selectedLang)
                    val currentLessonId = current.navigation.selectedLessonId
                    val selectedLessonId = lessons.firstOrNull { it.id == currentLessonId }?.id
                        ?: lessons.firstOrNull()?.id
                    val reloadedPackIds = packs.map { it.packId }.toSet()
                    // Keep current activePackId if it still exists after reload,
                    // otherwise derive from lessonId, otherwise fall back to first pack.
                    val updatedPackId = if (current.navigation.activePackId != null && current.navigation.activePackId in reloadedPackIds) {
                        current.navigation.activePackId
                    } else {
                        selectedLessonId?.let { lessonStore.getPackIdForLesson(it) }
                            ?: packs.firstOrNull { it.languageId == selectedLang }?.packId
                    }
                    val updatedPackLessonIds = updatedPackId?.let { lessonStore.getLessonIdsForPack(it) }
                    current.copy(navigation = current.navigation.copy(languages = languages, installedPacks = packs, selectedLanguageId = selectedLang, activePackId = updatedPackId, activePackLessonIds = updatedPackLessonIds, lessons = lessons, selectedLessonId = selectedLessonId), elite = current.elite.copy(eliteUnlocked = resolveEliteUnlocked(lessons, current.cardSession.testMode)))
                }
                val updatedLessons = lessonStore.getLessons(_uiState.value.navigation.selectedLanguageId)
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
            val state = _uiState.value
            val packId = state.navigation.activePackId
            val langId = state.navigation.selectedLanguageId
            val progressInfo = resolveProgressLessonInfo()
            if (packId != null && progressInfo != null) {
                val lessonId = progressInfo.first
                val lessonLevel = progressInfo.second
                dailyPracticeCoordinator.prebuildSession(
                    packId, langId, lessonId, lessonLevel, state.daily.dailyCursor
                )
            }
        }
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
        val newPackId = selectedLessonId?.let { lessonStore.getPackIdForLesson(it) }
            ?: lessonStore.getInstalledPacks().firstOrNull { it.languageId == languageId }?.packId
        val newPackLessonIds = newPackId?.let { lessonStore.getLessonIdsForPack(it) }
        rebindWordMasteryStore(newPackId)
        _uiState.update {
            it.resetAllSessionState().copy(navigation = it.navigation.copy(selectedLanguageId = languageId, lessons = lessons, selectedLessonId = selectedLessonId, activePackId = newPackId, activePackLessonIds = newPackLessonIds), elite = it.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, it.cardSession.testMode)), vocabSprint = it.vocabSprint.copy(vocabMasteredCount = wordMasteryStore.getMasteredCount()))
        }
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
        rebuildSchedules(_uiState.value.navigation.lessons)

        // Calculate active sub-lesson index based on completed count
        val schedule = lessonSchedules[lessonId]
        val subLessons = schedule?.subLessons.orEmpty()
        val mastery = masteryStore.get(lessonId, _uiState.value.navigation.selectedLanguageId)
        val completedCount = calculateCompletedSubLessons(subLessons, mastery, lessonId)
        val nextActiveIndex = completedCount.coerceAtMost((subLessons.size - 1).coerceAtLeast(0))

        _uiState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(selectedLessonId = lessonId, activePackId = packId, activePackLessonIds = packLessonIds, mode = TrainingMode.LESSON), cardSession = it.cardSession.copy(activeSubLessonIndex = nextActiveIndex, completedSubLessonCount = completedCount, currentCard = null))
        }
        buildSessionCards()
        refreshFlowerStates()
        saveProgress()
    }

    fun selectPack(packId: String) {
        val packLessonIds = lessonStore.getLessonIdsForPack(packId)
        if (packLessonIds.isNotEmpty()) {
            val currentLessonId = _uiState.value.navigation.selectedLessonId
            val lessonId = if (currentLessonId != null && currentLessonId in packLessonIds) currentLessonId else packLessonIds.first()
            selectLesson(lessonId)
        } else {
            // Drill-only pack — set activePackId without selecting a lesson
            rebindWordMasteryStore(packId)
            _uiState.update {
                it.copy(navigation = it.navigation.copy(activePackId = packId, activePackLessonIds = emptyList(), selectedLessonId = null))
            }
            saveProgress()
        }
    }

    fun selectMode(mode: TrainingMode) {
        sessionRunner.pauseTimer()
        vocabSession = emptyList()
        _uiState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(mode = mode))
        }
        buildSessionCards()
        saveProgress()
    }

    fun submitAnswer(): SubmitResult {
        val result = sessionRunner.submitAnswer()

        // Orchestrator: handle cross-module actions based on result
        if (result.needsBossFinish) {
            val state = _uiState.value
            updateBossProgress(state.boss.bossTotal)
            finishBoss()
        }
        if (result.needsSubLessonComplete) {
            forceBackupOnSave = true
        }

        Log.d(logTag, "Answer submitted: accepted=${result.accepted}")
        return SubmitResult(result.accepted, result.hintShown)
    }

    fun nextCard(triggerVoice: Boolean = false) {
        val state = _uiState.value

        // Boss-specific progress handling (stays in ViewModel since it reads boss state)
        val nextIndex = (state.cardSession.currentIndex + 1).coerceAtMost(sessionRunner.getSessionCards().lastIndex)
        if (state.boss.bossActive) {
            val nextProgress = (state.boss.bossProgress.coerceAtLeast(nextIndex)).coerceAtMost(state.boss.bossTotal)
            val nextReward = resolveBossReward(nextProgress, state.boss.bossTotal)
            val isNewReward = nextReward != null && nextReward != state.boss.bossReward
            val rewardMessage = if (isNewReward) {
                bossBattleRunner.bossRewardMessage(nextReward!!)
            } else {
                state.boss.bossRewardMessage
            }
            if (isNewReward) {
                sessionRunner.pauseTimer()
            }
            _uiState.update {
                it.copy(boss = it.boss.copy(bossProgress = nextProgress, bossReward = nextReward ?: it.boss.bossReward, bossRewardMessage = rewardMessage))
            }
        }

        sessionRunner.nextCard(triggerVoice)

        // Apply boss pause if reward threshold was crossed
        if (state.boss.bossActive && _uiState.value.boss.bossRewardMessage != state.boss.bossRewardMessage) {
            _uiState.update { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED)) }
        }
    }

    fun prevCard() = sessionRunner.prevCard()

    fun togglePause() = sessionRunner.togglePause()

    fun pauseSession() = sessionRunner.pauseSession()

    fun finishSession() {
        if (_uiState.value.boss.bossActive) {
            finishBoss()
            return
        }
        sessionRunner.finishSession()
    }

    fun showAnswer() = sessionRunner.showAnswer()

    fun importLesson(uri: Uri) {
        val languageId = _uiState.value.navigation.selectedLanguageId
        val lesson = lessonStore.importFromUri(languageId, uri, getApplication<Application>().contentResolver)
        refreshLessons(lesson.id)
    }

    fun importLessonPack(uri: Uri) {
        vocabSession = emptyList()
        try {
            val pack = lessonStore.importPackFromUri(uri, getApplication<Application>().contentResolver)
            val lessons = lessonStore.getLessons(pack.languageId)
            val selectedLessonId = lessons.firstOrNull()?.id
            _uiState.update {
                it.resetAllSessionState().copy(navigation = it.navigation.copy(languages = lessonStore.getLanguages(), installedPacks = lessonStore.getInstalledPacks(), selectedLanguageId = pack.languageId, lessons = lessons, selectedLessonId = selectedLessonId, mode = TrainingMode.LESSON), elite = it.elite.copy(eliteUnlocked = resolveEliteUnlocked(lessons, it.cardSession.testMode)))
            }
            rebuildSchedules(lessons)
            buildSessionCards()
            saveProgress()
        } catch (e: Exception) {
            Log.e(logTag, "Lesson pack import failed", e)
        }
    }
    fun resetAndImportLesson(uri: Uri) {
        val languageId = _uiState.value.navigation.selectedLanguageId
        lessonStore.deleteAllLessons(languageId)
        val lesson = lessonStore.importFromUri(languageId, uri, getApplication<Application>().contentResolver)
        refreshLessons(lesson.id)
    }

    fun deleteLesson(lessonId: String) {
        val languageId = _uiState.value.navigation.selectedLanguageId
        lessonStore.deleteLesson(languageId, lessonId)
        val selected = if (_uiState.value.navigation.selectedLessonId == lessonId) null else _uiState.value.navigation.selectedLessonId
        refreshLessons(selected)
    }

    fun createEmptyLesson(title: String) {
        val languageId = _uiState.value.navigation.selectedLanguageId
        val lesson = lessonStore.createEmptyLesson(languageId, title)
        refreshLessons(lesson.id)
    }

    fun addLanguage(name: String) {
        val language = lessonStore.addLanguage(name)
        vocabSession = emptyList()
        val lessons = lessonStore.getLessons(language.id)
        val selectedLessonId = lessons.firstOrNull()?.id
        _uiState.update {
            it.resetAllSessionState().copy(navigation = it.navigation.copy(languages = lessonStore.getLanguages(), installedPacks = lessonStore.getInstalledPacks(), selectedLanguageId = language.id, lessons = lessons, selectedLessonId = selectedLessonId, mode = TrainingMode.LESSON), elite = it.elite.copy(eliteUnlocked = resolveEliteUnlocked(lessons, it.cardSession.testMode)))
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        saveProgress()
    }

    fun deleteAllLessons() {
        val languageId = _uiState.value.navigation.selectedLanguageId
        lessonStore.deleteAllLessons(languageId)
        refreshLessons(null)
        _uiState.update { it.copy(navigation = it.navigation.copy(installedPacks = lessonStore.getInstalledPacks())) }
    }

    /**
     * Reset ALL progress: mastery, daily practice, verb drill, vocab mastery, training progress.
     */
    fun resetAllProgress() {
        // Clear training progress file and mastery store
        progressTracker.resetStores(getApplication())

        // Clear verb drill and word mastery for every installed pack
        progressTracker.resetDrillFiles(getApplication())

        // Clear legacy (pack-less) stores if they exist
        wordMasteryStore.saveAll(emptyMap())

        // Clear cached daily sessions
        dailyPracticeCoordinator.resetState()

        // Reset UI state
        _uiState.update {
            it.copy(daily = it.daily.copy(dailySession = DailySessionState(), dailyCursor = DailyCursorState()), cardSession = it.cardSession.copy(currentIndex = 0, correctCount = 0, incorrectCount = 0, sessionState = SessionState.PAUSED, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0))
        }

        // Refresh lessons to reflect reset mastery (flower states)
        refreshLessons(null)

        Log.d(logTag, "All progress reset: mastery, daily, verb drill, vocab mastery, training progress")
    }

    fun deletePack(packId: String) {
        val pack = lessonStore.getInstalledPacks().firstOrNull { it.packId == packId } ?: return
        val languageId = pack.languageId
        lessonStore.removeInstalledPackData(packId)
        if (_uiState.value.navigation.selectedLanguageId == languageId) {
            refreshLessons(null)
        }
        _uiState.update { it.copy(navigation = it.navigation.copy(installedPacks = lessonStore.getInstalledPacks())) }
    }

    fun toggleTestMode() {
        val newTestMode = !_uiState.value.cardSession.testMode
        _uiState.update {
            it.copy(cardSession = it.cardSession.copy(testMode = newTestMode), elite = it.elite.copy(eliteUnlocked = resolveEliteUnlocked(_uiState.value.navigation.lessons, newTestMode)))
        }
        configStore.save(
            configStore.load().copy(testMode = newTestMode)
        )
        Log.d(logTag, "Test mode toggled: $newTestMode")
    }

    fun updateVocabSprintLimit(limit: Int) {
        val nextLimit = limit.coerceAtLeast(0)
        _uiState.update { it.copy(cardSession = it.cardSession.copy(vocabSprintLimit = nextLimit)) }
        configStore.save(
            configStore.load().copy(vocabSprintLimit = nextLimit)
        )
    }

    fun setHintLevel(level: com.alexpo.grammermate.data.HintLevel) {
        _uiState.update { it.copy(cardSession = it.cardSession.copy(hintLevel = level)) }
        configStore.save(
            configStore.load().copy(hintLevel = level)
        )
    }

    private fun refreshLessons(selectedLessonId: String?) {
        sessionRunner.pauseTimer()
        vocabSession = emptyList()
        val languageId = _uiState.value.navigation.selectedLanguageId
        val lessons = lessonStore.getLessons(languageId)
        val selected = selectedLessonId ?: lessons.firstOrNull()?.id
        _uiState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(lessons = lessons, selectedLessonId = selected), elite = it.elite.copy(eliteUnlocked = sessionRunner.resolveEliteUnlocked(lessons, it.cardSession.testMode)))
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        saveProgress()
    }

    fun resumeFromSettings() = sessionRunner.resumeFromSettings()

    private fun rebuildSchedules(lessons: List<Lesson>) {
        lessonSchedules = cardProvider.buildSchedules(lessons, lessonSchedules)
    }

    private fun buildSessionCards() {
        if (_uiState.value.boss.bossActive || _uiState.value.elite.eliteActive || _uiState.value.drill.isDrillMode) return
        val state = _uiState.value
        val hiddenIds = hiddenCardStore.getHiddenCardIds()
        val lessons = state.navigation.lessons

        val mastery = state.navigation.selectedLessonId?.let {
            masteryStore.get(it, state.navigation.selectedLanguageId)
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
        val safeIndex = _uiState.value.cardSession.currentIndex.coerceIn(0, (result.cards.size - 1).coerceAtLeast(0))
        val card = result.cards.getOrNull(safeIndex)
        if (card == null && state.cardSession.sessionState == SessionState.ACTIVE) {
            sessionRunner.pauseTimer()
        }
        _uiState.update {
            it.copy(cardSession = it.cardSession.copy(currentIndex = safeIndex, currentCard = card, sessionState = if (card == null) SessionState.PAUSED else state.cardSession.sessionState, subLessonTotal = result.subLessonTotal, subLessonCount = result.subLessonCount, activeSubLessonIndex = result.activeSubLessonIndex, completedSubLessonCount = result.completedSubLessonCount, subLessonTypes = result.subLessonTypes))
        }
    }

    fun selectSubLesson(index: Int) = sessionRunner.selectSubLesson(index)

    fun openEliteStep(index: Int) = sessionRunner.openEliteStep(index)

    fun cancelEliteSession() = sessionRunner.cancelEliteSession()

    // ── Daily Practice ────────────────────────────────────────────────────

    fun hasResumableDailySession(): Boolean {
        return dailyPracticeCoordinator.hasResumableDailySession()
    }

    @Suppress("UNUSED_PARAMETER")
    fun startDailyPractice(lessonLevel: Int): Boolean {
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
            currentCursor = _uiState.value.daily.dailyCursor,
            sentenceIds = sentenceIds,
            verbIds = verbIds
        )
        _uiState.update { it.copy(daily = it.daily.copy(dailyCursor = updatedCursor)) }
        saveProgress()
    }

    /**
     * Advance the daily cursor offsets after a session is built.
     * If the sentence offset exceeds the current lesson's card count,
     * advances currentLessonIndex and resets sentenceOffset.
     */
    private fun advanceCursor(sentenceCount: Int) {
        val s = _uiState.value
        val advanced = progressTracker.advanceCursor(
            currentCursor = s.daily.dailyCursor,
            sentenceCount = sentenceCount,
            selectedLanguageId = s.navigation.selectedLanguageId
        )
        _uiState.update { it.copy(daily = it.daily.copy(dailyCursor = advanced)) }
    }

    fun repeatDailyPractice(lessonLevel: Int): Boolean {
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
    fun rateVocabCard(rating: Int) {
        dailyPracticeCoordinator.rateVocabCard(rating)
    }

    fun getDailyCurrentTask(): DailyTask? {
        return dailyPracticeCoordinator.getDailyCurrentTask()
    }

    fun getDailyBlockProgress(): com.alexpo.grammermate.ui.helpers.BlockProgress {
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
        sessionRunner.startDrill(resume)
        _uiState.update {
            it.copy(cardSession = it.cardSession.copy(badSentenceCount = _uiState.value.navigation.activePackId?.let { pid -> badSentenceStore.getBadSentenceCount(pid) } ?: 0))
        }
    }

    fun dismissDrillDialog() = sessionRunner.dismissDrillDialog()

    fun advanceDrillCard() = sessionRunner.advanceDrillCard()

    fun exitDrillMode() {
        sessionRunner.exitDrillMode()
        _uiState.update {
            it.copy(cardSession = it.cardSession.copy(badSentenceCount = _uiState.value.navigation.activePackId?.let { pid -> badSentenceStore.getBadSentenceCount(pid) } ?: 0))
        }
    }

    // ── End Drill Mode ───────────────────────────────────────────────────

    fun openStory(phase: StoryPhase) = storyRunner.openStory(phase)

    fun hasVocabProgress(): Boolean {
        val lessonId = _uiState.value.navigation.selectedLessonId ?: return false
        val languageId = _uiState.value.navigation.selectedLanguageId
        val progress = vocabProgressStore.get(lessonId, languageId)
        return progress.completedIndices.isNotEmpty()
    }

    fun openVocabSprint(resume: Boolean = false) = vocabSprintRunner.openSprint(resume)

    fun completeStory(phase: StoryPhase, allCorrect: Boolean) = storyRunner.completeStory(phase, allCorrect)

    fun clearStoryError() = storyRunner.clearStoryError()

    fun clearVocabError() = vocabSprintRunner.clearError()

    fun onVocabInputChanged(text: String) = vocabSprintRunner.onInputChanged(text)

    fun setVocabInputMode(mode: InputMode) = vocabSprintRunner.setInputMode(mode)

    fun requestVocabVoice() = vocabSprintRunner.requestVoice()

    fun submitVocabAnswer(inputOverride: String? = null) = vocabSprintRunner.submitAnswer(inputOverride)

    fun showVocabAnswer() = vocabSprintRunner.showAnswer()


    /**
     * Start a Mix Challenge (interleaved practice) session.
     *
     * Selects 10 cards from different lessons/tenses with maximum alternation,
     * then sets up a regular training session with those cards. Mastery tracking
     * and SRS intervals work identically to regular training.
     *
     * @return true if the session was started, false if not enough started lessons
     */
    fun startMixChallenge(): Boolean {
        val state = _uiState.value
        val languageId = state.navigation.selectedLanguageId
        val lessons = state.navigation.lessons

        // Determine which lessons the user has started
        val startedIds = mutableSetOf<String>()
        for (lesson in lessons) {
            val mastery = masteryStore.get(lesson.id, languageId)
            if (mastery != null && mastery.uniqueCardShows > 0) {
                startedIds.add(lesson.id)
            }
        }
        // Also include the first lesson even if not started yet
        if (startedIds.isEmpty() && lessons.isNotEmpty()) {
            startedIds.add(lessons.first().id)
        }

        val cards = cardProvider.buildMixChallengeCards(lessons, startedIds, count = 10)
        if (cards.isEmpty()) return false

        sessionRunner.pauseTimer()
        sessionRunner.clearAllCards()
        vocabSession = emptyList()

        sessionRunner.setSessionCards(cards)
        subLessonTotal = cards.size
        subLessonCount = 1

        val firstCard = cards.firstOrNull()
        _uiState.update {
            it.resetSessionState().copy(navigation = it.navigation.copy(mode = TrainingMode.MIX_CHALLENGE, selectedLessonId = null), cardSession = it.cardSession.copy(currentCard = firstCard, subLessonTotal = cards.size, subLessonCount = 1), daily = it.daily.copy(dailySession = DailySessionState()))
        }
        saveProgress()
        return true
    }

    fun startBossLesson() {
        startBoss(BossType.LESSON)
    }

    fun startBossMega() {
        startBoss(BossType.MEGA)
    }

    fun startBossElite() {
        startBoss(BossType.ELITE)
    }

    private fun startBoss(type: BossType) {
        sessionRunner.pauseTimer()
        val state = _uiState.value
        val lessons = state.navigation.lessons
        val selectedId = state.navigation.selectedLessonId
        val selectedIndex = lessons.indexOfFirst { it.id == selectedId }
        val cards = cardProvider.buildBossCards(lessons, type, selectedId, selectedIndex)
        val result = bossBattleRunner.startBoss(
            type = type,
            cards = cards,
            selectedLessonId = selectedId,
            completedSubLessonCount = state.cardSession.completedSubLessonCount,
            testMode = state.cardSession.testMode
        )
        if (!result.success) {
            _uiState.update { it.copy(boss = it.boss.copy(bossErrorMessage = result.errorMessage)) }
            return
        }
        sessionRunner.setBossCards(result.cards)
        subLessonTotal = result.subLessonTotal
        subLessonCount = result.subLessonCount
        val firstCard = result.cards.firstOrNull()
        _uiState.update {
            it.copy(boss = it.boss.copy(bossActive = true, bossType = type, bossTotal = result.subLessonTotal, bossProgress = 0, bossReward = null, bossRewardMessage = null, bossErrorMessage = null), cardSession = it.cardSession.copy(currentIndex = 0, currentCard = firstCard, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0, correctCount = 0, incorrectCount = 0, activeTimeMs = 0L, voiceActiveMs = 0L, voiceWordCount = 0, hintCount = 0, voicePromptStartMs = null, sessionState = SessionState.PAUSED, subLessonTotal = result.subLessonTotal, subLessonCount = 1, activeSubLessonIndex = 0, completedSubLessonCount = 0))
        }
    }

    fun finishBoss() {
        sessionRunner.pauseTimer()
        val state = _uiState.value
        val result = bossBattleRunner.finishBoss(
            bossType = state.boss.bossType,
            bossProgress = state.boss.bossProgress,
            bossTotal = state.boss.bossTotal,
            selectedLessonId = state.navigation.selectedLessonId,
            currentLessonRewards = state.boss.bossLessonRewards,
            currentMegaRewards = state.boss.bossMegaRewards
        )
        val progress = progressStore.load()
        val restoredLessonId = progress.lessonId ?: state.navigation.selectedLessonId
        sessionRunner.clearAllCards()
        _uiState.update {
            it.copy(boss = it.boss.copy(bossActive = false, bossType = null, bossTotal = 0, bossProgress = 0, bossReward = result.reward ?: it.boss.bossReward, bossRewardMessage = it.boss.bossRewardMessage, bossFinishedToken = it.boss.bossFinishedToken + 1, bossLastType = state.boss.bossType, bossErrorMessage = null, bossLessonRewards = result.updatedLessonRewards, bossMegaRewards = result.updatedMegaRewards), navigation = it.navigation.copy(selectedLessonId = restoredLessonId, mode = progress.mode), cardSession = it.cardSession.copy(currentIndex = progress.currentIndex, correctCount = progress.correctCount, incorrectCount = progress.incorrectCount, incorrectAttemptsForCard = progress.incorrectAttemptsForCard, activeTimeMs = progress.activeTimeMs, voiceActiveMs = progress.voiceActiveMs, voiceWordCount = progress.voiceWordCount, hintCount = progress.hintCount, voicePromptStartMs = null, inputText = "", lastResult = null, answerText = null, sessionState = SessionState.PAUSED))
        }
        buildSessionCards()
        saveProgress()
        refreshFlowerStates()
    }

    fun clearBossRewardMessage() {
        val state = _uiState.value
        val clearResult = bossBattleRunner.clearBossRewardMessage(
            bossActive = state.boss.bossActive,
            sessionState = state.cardSession.sessionState,
            currentCard = state.cardSession.currentCard,
            inputMode = state.cardSession.inputMode
        )
        if (clearResult.shouldResumeTimer) {
            sessionRunner.resumeTimer()
        }
        _uiState.update {
            val trigger = if (clearResult.shouldTriggerVoice) {
                it.cardSession.voiceTriggerToken + 1
            } else {
                it.cardSession.voiceTriggerToken
            }
            it.copy(boss = it.boss.copy(bossRewardMessage = null), cardSession = it.cardSession.copy(sessionState = if (clearResult.shouldResumeTimer) SessionState.ACTIVE else it.cardSession.sessionState, voiceTriggerToken = trigger, inputText = if (clearResult.shouldResumeTimer) "" else it.cardSession.inputText))
        }
    }

    fun clearBossError() {
        _uiState.update { it.copy(boss = it.boss.copy(bossErrorMessage = null)) }
    }

    private fun updateBossProgress(progress: Int) {
        val state = _uiState.value
        val result = bossBattleRunner.updateBossProgress(
            progress = progress,
            currentTotal = state.boss.bossTotal,
            currentReward = state.boss.bossReward
        )
        if (result.shouldPause) {
            sessionRunner.pauseTimer()
        }
        _uiState.update {
            it.copy(boss = it.boss.copy(bossProgress = result.nextProgress, bossReward = result.nextReward ?: it.boss.bossReward, bossRewardMessage = result.rewardMessage ?: state.boss.bossRewardMessage), cardSession = it.cardSession.copy(sessionState = if (result.shouldPause) SessionState.PAUSED else it.cardSession.sessionState))
        }
    }

    private fun resolveBossReward(progress: Int, total: Int): BossReward? {
        return bossBattleRunner.resolveBossReward(progress, total)
    }

    private fun bossRewardMessage(reward: BossReward): String {
        return bossBattleRunner.bossRewardMessage(reward)
    }

    private fun startSession() = sessionRunner.startSession()

    private fun resumeTimer() = sessionRunner.resumeTimer()

    private fun pauseTimer() = sessionRunner.pauseTimer()

    private fun saveProgress() {
        val state = _uiState.value
        val shouldBackup = progressTracker.saveProgress(
            state = state,
            forceBackup = forceBackupOnSave,
            normalizedEliteSpeeds = normalizeEliteSpeeds(state.elite.eliteBestSpeeds)
        )
        if (shouldBackup) {
            forceBackupOnSave = false
            createProgressBackup()
        }
    }

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
        val s = _uiState.value
        return progressTracker.resolveProgressLessonInfo(
            activePackId = s.navigation.activePackId,
            selectedLanguageId = s.navigation.selectedLanguageId,
            activePackLessonIds = s.navigation.activePackLessonIds,
            lessons = s.navigation.lessons,
            dailyCursor = s.daily.dailyCursor
        )
    }

    /**
     * Public helper for UI layer to get the progress-based lesson level
     * without depending on [selectedLessonId].
     */
    fun getProgressLessonLevel(): Int {
        val s = _uiState.value
        return progressTracker.getProgressLessonLevel(
            activePackId = s.navigation.activePackId,
            selectedLanguageId = s.navigation.selectedLanguageId,
            activePackLessonIds = s.navigation.activePackLessonIds,
            lessons = s.navigation.lessons,
            dailyCursor = s.daily.dailyCursor
        )
    }

    private fun resolveEliteUnlocked(lessons: List<Lesson>, testMode: Boolean): Boolean =
        sessionRunner.resolveEliteUnlocked(lessons, testMode)

    /**
     * Parse boss reward map from string-keyed progress store to typed BossReward map.
     */
    private fun parseBossRewards(rewardMap: Map<String, String>): Map<String, BossReward> {
        return rewardMap.mapNotNull { (lessonId, reward) ->
            val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
            lessonId to parsed
        }.toMap()
    }

    /**
     * Apply progress restoration to UI state and rebuild derived data.
     * Shared by restoreBackup and reloadFromDisk.
     */
    private fun applyRestoredProgress(
        progress: com.alexpo.grammermate.data.TrainingProgress,
        languages: List<com.alexpo.grammermate.data.Language>,
        packs: List<com.alexpo.grammermate.data.LessonPack>,
        lessons: List<Lesson>,
        selectedLanguageId: String,
        selectedLessonId: String?,
        streak: StreakData,
        profile: UserProfile,
        bossLessonRewards: Map<String, BossReward>,
        bossMegaRewards: Map<String, BossReward>,
        includeLanguageData: Boolean = false
    ) {
        val normalizedEliteSpeeds = normalizeEliteSpeeds(progress.eliteBestSpeeds)
        _uiState.update {
            val base = it.copy(navigation = it.navigation.copy(selectedLanguageId = selectedLanguageId, lessons = lessons, selectedLessonId = selectedLessonId, mode = progress.mode, userName = profile.userName), cardSession = it.cardSession.copy(sessionState = progress.state, currentIndex = progress.currentIndex, correctCount = progress.correctCount, incorrectCount = progress.incorrectCount, incorrectAttemptsForCard = progress.incorrectAttemptsForCard, activeTimeMs = progress.activeTimeMs, voiceActiveMs = progress.voiceActiveMs, voiceWordCount = progress.voiceWordCount, hintCount = progress.hintCount, currentStreak = streak.currentStreak, longestStreak = streak.longestStreak), boss = it.boss.copy(bossLessonRewards = bossLessonRewards, bossMegaRewards = bossMegaRewards), elite = it.elite.copy(eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1), eliteBestSpeeds = normalizedEliteSpeeds))
            if (includeLanguageData) {
                base.copy(navigation = base.navigation.copy(languages = languages, installedPacks = packs), elite = base.elite.copy(eliteUnlocked = resolveEliteUnlocked(lessons, base.cardSession.testMode)))
            } else {
                base
            }
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        refreshFlowerStates()
    }

    private fun normalizeEliteSpeeds(speeds: List<Double>): List<Double> =
        sessionRunner.normalizeEliteSpeeds(speeds)

    // ── Audio delegations to AudioCoordinator ─────────────────────────────

    fun onTtsSpeak(text: String, speed: Float? = null) = audioCoordinator.onTtsSpeak(text, speed)

    fun setTtsSpeed(speed: Float) = audioCoordinator.setTtsSpeed(speed)

    fun setRuTextScale(scale: Float) = audioCoordinator.setRuTextScale(scale)

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
        val s = _uiState.value
        return progressTracker.resolveCardLessonId(
            card = card,
            selectedLessonId = s.navigation.selectedLessonId,
            lessons = s.navigation.lessons
        )
    }

    /**
     * Записать показ карточки для отслеживания прогресса освоения.
     * Word Bank НЕ учитывается для формирования навыка (роста цветка).
     * Учитывается только голосовой ввод и клавиатура.
     */
    private fun recordCardShowForMastery(card: SentenceCard) {
        val s = _uiState.value
        progressTracker.recordCardShowForMastery(
            card = card,
            bossActive = s.boss.bossActive,
            isDrillMode = s.drill.isDrillMode,
            inputMode = s.cardSession.inputMode,
            selectedLanguageId = s.navigation.selectedLanguageId,
            lessons = s.navigation.lessons,
            selectedLessonId = s.navigation.selectedLessonId
        )
    }

    private fun markSubLessonCardsShown(cards: List<SentenceCard>) {
        val s = _uiState.value
        progressTracker.markSubLessonCardsShown(
            cards = cards,
            inputMode = s.cardSession.inputMode,
            selectedLessonId = s.navigation.selectedLessonId,
            selectedLanguageId = s.navigation.selectedLanguageId,
            lessons = s.navigation.lessons
        )
    }

    /**
     * Проверить и отметить урок как завершённый если все под-уроки пройдены.
     */
    private fun checkAndMarkLessonCompleted() {
        val s = _uiState.value
        progressTracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = s.cardSession.completedSubLessonCount,
            selectedLessonId = s.navigation.selectedLessonId,
            selectedLanguageId = s.navigation.selectedLanguageId
        )
    }

    /**
     * Вычислить количество завершённых под-уроков на основе показанных карточек.
     */
    private fun calculateCompletedSubLessons(
        subLessons: List<ScheduledSubLesson>,
        mastery: LessonMasteryState?,
        lessonId: String?
    ): Int {
        return progressTracker.calculateCompletedSubLessons(
            subLessons = subLessons,
            mastery = mastery,
            lessonId = lessonId,
            lessons = _uiState.value.navigation.lessons
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
     * Обновить состояния цветков для всех уроков.
     */
    private fun refreshFlowerStates() = flowerRefresher.refreshFlowerStates()

    /**
     * Re-scope wordMasteryStore to the given packId.
     * Must be called whenever activePackId changes so that mastery reads/writes
     * go to the pack-scoped file instead of the legacy global file.
     */
    private fun rebindWordMasteryStore(packId: String?) {
        wordMasteryStore = WordMasteryStore(getApplication(), packId = packId)
    }

    /**
     * Refresh the vocab mastered count from the store.
     * Called when returning from VocabDrill to reflect updated mastery.
     */
    fun refreshVocabMasteryCount() {
        val count = wordMasteryStore.getMasteredCount()
        _uiState.update { it.copy(vocabSprint = it.vocabSprint.copy(vocabMasteredCount = count)) }
    }

    /**
     * Обновляет streak после завершения подурока
     */
    private fun updateStreak() {
        val languageId = _uiState.value.navigation.selectedLanguageId
        val (updatedStreak, isNewStreak) = streakManager.recordSubLessonCompletion(languageId)

        if (isNewStreak && updatedStreak.currentStreak > 0) {
            val message = streakManager.getCelebrationMessage(updatedStreak.currentStreak)

            _uiState.update {
                it.copy(cardSession = it.cardSession.copy(currentStreak = updatedStreak.currentStreak, longestStreak = updatedStreak.longestStreak, streakMessage = message, streakCelebrationToken = it.cardSession.streakCelebrationToken + 1))
            }
        } else {
            // Просто обновляем streak без сообщения
            _uiState.update {
                it.copy(cardSession = it.cardSession.copy(currentStreak = updatedStreak.currentStreak, longestStreak = updatedStreak.longestStreak))
            }
        }
    }

    /**
     * Закрывает сообщение о streak
     */
    fun dismissStreakMessage() {
        _uiState.update {
            it.copy(cardSession = it.cardSession.copy(streakMessage = null))
        }
    }

    /**
     * Create a backup of current progress data.
     * Should be called periodically to ensure data is not lost on uninstall.
     */
    fun createProgressBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = backupManager.createBackup()
                Log.d(logTag, "Progress backup created: success=$success")
            } catch (e: Exception) {
                Log.e(logTag, "Failed to create progress backup", e)
            }
        }
    }

    /**
     * Update user name in profile and save to storage.
     */
    fun updateUserName(newName: String) {
        val trimmed = newName.trim().take(50)
        if (trimmed.isEmpty()) return

        val profile = UserProfile(userName = trimmed)
        profileStore.save(profile)

        _uiState.update {
            it.copy(navigation = it.navigation.copy(userName = trimmed))
        }
    }

    fun saveProgressNow() {
        Log.d(logTag, "Manual progress save requested from settings")
        forceBackupOnSave = true
        saveProgress()
    }

    fun onScreenChanged(screenName: String) {
        _uiState.update { it.copy(navigation = it.navigation.copy(currentScreen = screenName)) }
    }

    /**
     * Restore user progress from backup folder.
     */
    fun restoreBackup(backupUri: android.net.Uri) {
        Log.d(logTag, "=== Starting Backup Restore ===")
        Log.d(logTag, "Backup URI: $backupUri")

        val success = backupManager.restoreFromBackupUri(backupUri)
        Log.d(logTag, "Backup restore result: $success")

        if (success) {
            val progress = progressStore.load()
            val profile = profileStore.load()
            val selectedLanguageId = progress.languageId ?: "en"
            val lessons = lessonStore.getLessons(selectedLanguageId)
            val selectedLessonId = progress.lessonId?.let { id ->
                lessons.firstOrNull { it.id == id }?.id
            } ?: lessons.firstOrNull()?.id
            val streak = streakStore.getCurrentStreak(selectedLanguageId)
            val bossLessonRewards = parseBossRewards(progress.bossLessonRewards)
            val bossMegaRewards = parseBossRewards(progress.bossMegaRewards)

            applyRestoredProgress(
                progress = progress,
                languages = emptyList(),
                packs = emptyList(),
                lessons = lessons,
                selectedLanguageId = selectedLanguageId,
                selectedLessonId = selectedLessonId,
                streak = streak,
                profile = profile,
                bossLessonRewards = bossLessonRewards,
                bossMegaRewards = bossMegaRewards,
                includeLanguageData = false
            )

            Log.d(logTag, "=== Backup Restore Complete ===")
        } else {
            Log.e(logTag, "Failed to restore backup - check restore_log.txt in backup folder")
        }
    }

    fun reloadFromDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            val progress = progressStore.load()
            val profile = profileStore.load()
            val languages = lessonStore.getLanguages()
            val packs = lessonStore.getInstalledPacks()
            val selectedLanguageId = languages.firstOrNull { it.id == progress.languageId }?.id
                ?: languages.firstOrNull()?.id
                ?: "en"
            val lessons = lessonStore.getLessons(selectedLanguageId)
            val selectedLessonId = progress.lessonId?.let { id ->
                lessons.firstOrNull { it.id == id }?.id
            } ?: lessons.firstOrNull()?.id
            val streak = streakStore.getCurrentStreak(selectedLanguageId)
            val bossLessonRewards = parseBossRewards(progress.bossLessonRewards)
            val bossMegaRewards = parseBossRewards(progress.bossMegaRewards)

            withContext(Dispatchers.Main) {
                applyRestoredProgress(
                    progress = progress,
                    languages = languages,
                    packs = packs,
                    lessons = lessons,
                    selectedLanguageId = selectedLanguageId,
                    selectedLessonId = selectedLessonId,
                    streak = streak,
                    profile = profile,
                    bossLessonRewards = bossLessonRewards,
                    bossMegaRewards = bossMegaRewards,
                    includeLanguageData = true
                )
            }
        }
    }

    fun flagBadSentence() {
        val card = _uiState.value.cardSession.currentCard ?: return
        val state = _uiState.value
        val packId = state.navigation.activePackId ?: return
        badSentenceStore.addBadSentence(
            packId = packId,
            cardId = card.id,
            languageId = state.navigation.selectedLanguageId,
            sentence = card.promptRu,
            translation = card.acceptedAnswers.joinToString(" / "),
            mode = "training"
        )
        _uiState.update { it.copy(cardSession = it.cardSession.copy(badSentenceCount = badSentenceStore.getBadSentenceCount(packId))) }
        if (state.drill.isDrillMode) {
            advanceDrillCard()
        }
    }

    fun unflagBadSentence() {
        val card = _uiState.value.cardSession.currentCard ?: return
        val packId = _uiState.value.navigation.activePackId ?: return
        badSentenceStore.removeBadSentence(packId, card.id)
        _uiState.update { it.copy(cardSession = it.cardSession.copy(badSentenceCount = badSentenceStore.getBadSentenceCount(packId))) }
    }

    fun isBadSentence(): Boolean {
        val card = _uiState.value.cardSession.currentCard ?: return false
        val packId = _uiState.value.navigation.activePackId ?: return false
        return badSentenceStore.isBadSentence(packId, card.id)
    }

    fun exportBadSentences(): String? {
        val packId = _uiState.value.navigation.activePackId ?: return null
        val entries = badSentenceStore.getBadSentences(packId)
        if (entries.isEmpty()) return null
        val file = badSentenceStore.exportUnified()
        return file.absolutePath
    }

    // ── Daily Practice Bad Sentence Support ────────────────────────────────

    private val dailyBadCardIds = mutableSetOf<String>()

    fun flagDailyBadSentence(cardId: String, languageId: String, sentence: String, translation: String, mode: String) {
        val packId = _uiState.value.navigation.activePackId ?: return
        badSentenceStore.addBadSentence(
            packId = packId,
            cardId = cardId,
            languageId = languageId,
            sentence = sentence,
            translation = translation,
            mode = mode
        )
        dailyBadCardIds.add(cardId)
    }

    fun unflagDailyBadSentence(cardId: String) {
        val packId = _uiState.value.navigation.activePackId ?: return
        badSentenceStore.removeBadSentence(packId, cardId)
        dailyBadCardIds.remove(cardId)
    }

    fun isDailyBadSentence(cardId: String): Boolean {
        return dailyBadCardIds.contains(cardId) ||
            _uiState.value.navigation.activePackId?.let { badSentenceStore.isBadSentence(it, cardId) } == true
    }

    fun exportDailyBadSentences(): String? {
        val packId = _uiState.value.navigation.activePackId ?: return null
        val entries = badSentenceStore.getBadSentences(packId)
        if (entries.isEmpty()) return null
        val file = badSentenceStore.exportUnified()
        return file.absolutePath
    }

    fun hideCurrentCard() {
        val card = _uiState.value.cardSession.currentCard ?: return
        hiddenCardStore.hideCard(card.id)
        skipToNextCard()
    }

    fun unhideCurrentCard() {
        val card = _uiState.value.cardSession.currentCard ?: return
        hiddenCardStore.unhideCard(card.id)
    }

    fun isCurrentCardHidden(): Boolean {
        val card = _uiState.value.cardSession.currentCard ?: return false
        return hiddenCardStore.isHidden(card.id)
    }

    private fun skipToNextCard() = sessionRunner.skipToNextCard()
}

data class SubmitResult(
    val accepted: Boolean,
    val hintShown: Boolean
)

data class NavigationState(
    val languages: List<com.alexpo.grammermate.data.Language> = emptyList(),
    val installedPacks: List<com.alexpo.grammermate.data.LessonPack> = emptyList(),
    val selectedLanguageId: String = "en",
    val activePackId: String? = null,
    val activePackLessonIds: List<String>? = null,
    val lessons: List<Lesson> = emptyList(),
    val selectedLessonId: String? = null,
    val mode: TrainingMode = TrainingMode.LESSON,
    val userName: String = "GrammarMateUser",
    val ladderRows: List<LessonLadderRow> = emptyList(),
    val initialScreen: String = "HOME",
    val currentScreen: String = "HOME",
    val appVersion: String = "1.5"
)

data class CardSessionState(
    val sessionState: SessionState = SessionState.ACTIVE,
    val currentIndex: Int = 0,
    val currentCard: SentenceCard? = null,
    val inputText: String = "",
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val incorrectAttemptsForCard: Int = 0,
    val activeTimeMs: Long = 0L,
    val voiceActiveMs: Long = 0L,
    val voiceWordCount: Int = 0,
    val hintCount: Int = 0,
    val voicePromptStartMs: Long? = null,
    val answerText: String? = null,
    val lastResult: Boolean? = null,
    val lastRating: Double? = null,
    val inputMode: InputMode = InputMode.VOICE,
    val voiceTriggerToken: Int = 0,
    val subLessonTotal: Int = 0,
    val subLessonCount: Int = 0,
    val subLessonTypes: List<SubLessonType> = emptyList(),
    val activeSubLessonIndex: Int = 0,
    val completedSubLessonCount: Int = 0,
    val subLessonFinishedToken: Int = 0,
    val wordBankWords: List<String> = emptyList(),
    val selectedWords: List<String> = emptyList(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakMessage: String? = null,
    val streakCelebrationToken: Int = 0,
    val hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY,
    val badSentenceCount: Int = 0,
    val testMode: Boolean = false,
    val vocabSprintLimit: Int = 20
)

data class BossState(
    val bossActive: Boolean = false,
    val bossType: BossType? = null,
    val bossTotal: Int = 0,
    val bossProgress: Int = 0,
    val bossReward: BossReward? = null,
    val bossRewardMessage: String? = null,
    val bossFinishedToken: Int = 0,
    val bossLastType: BossType? = null,
    val bossErrorMessage: String? = null,
    val bossLessonRewards: Map<String, BossReward> = emptyMap(),
    val bossMegaRewards: Map<String, BossReward> = emptyMap()
)

data class StoryState(
    val storyCheckInDone: Boolean = false,
    val storyCheckOutDone: Boolean = false,
    val activeStory: StoryQuiz? = null,
    val storyErrorMessage: String? = null
)

data class VocabSprintState(
    val currentVocab: VocabEntry? = null,
    val vocabInputText: String = "",
    val vocabAttempts: Int = 0,
    val vocabAnswerText: String? = null,
    val vocabIndex: Int = 0,
    val vocabTotal: Int = 0,
    val vocabWordBankWords: List<String> = emptyList(),
    val vocabFinishedToken: Int = 0,
    val vocabErrorMessage: String? = null,
    val vocabInputMode: InputMode = InputMode.VOICE,
    val vocabVoiceTriggerToken: Int = 0,
    val vocabMasteredCount: Int = 0
)

data class EliteState(
    val eliteActive: Boolean = false,
    val eliteStepIndex: Int = 0,
    val eliteBestSpeeds: List<Double> = emptyList(),
    val eliteFinishedToken: Int = 0,
    val eliteUnlocked: Boolean = false,
    val eliteSizeMultiplier: Double = 1.25
)

data class DrillState(
    val isDrillMode: Boolean = false,
    val drillCardIndex: Int = 0,
    val drillTotalCards: Int = 0,
    val drillShowStartDialog: Boolean = false,
    val drillHasProgress: Boolean = false
)

data class FlowerDisplayState(
    val lessonFlowers: Map<String, FlowerVisual> = emptyMap(),
    val currentLessonFlower: FlowerVisual? = null,
    val currentLessonShownCount: Int = 0
)

data class AudioState(
    val ttsState: TtsState = TtsState.IDLE,
    val ttsDownloadState: DownloadState = DownloadState.Idle,
    val ttsModelReady: Boolean = false,
    val ttsMeteredNetwork: Boolean = false,
    val bgTtsDownloading: Boolean = false,
    val bgTtsDownloadStates: Map<String, DownloadState> = emptyMap(),
    val ttsModelsReady: Map<String, Boolean> = emptyMap(),
    val ttsSpeed: Float = 1.0f,
    val ruTextScale: Float = 1.0f,
    val useOfflineAsr: Boolean = false,
    val asrState: AsrState = AsrState.IDLE,
    val asrModelReady: Boolean = false,
    val asrDownloadState: DownloadState = DownloadState.Idle,
    val asrMeteredNetwork: Boolean = false,
    val asrErrorMessage: String? = null,
    val audioPermissionDenied: Boolean = false
)

data class DailyPracticeState(
    val dailySession: DailySessionState = DailySessionState(),
    val dailyCursor: DailyCursorState = DailyCursorState()
)

data class TrainingUiState(
    val navigation: NavigationState = NavigationState(),
    val cardSession: CardSessionState = CardSessionState(),
    val boss: BossState = BossState(),
    val story: StoryState = StoryState(),
    val vocabSprint: VocabSprintState = VocabSprintState(),
    val elite: EliteState = EliteState(),
    val drill: DrillState = DrillState(),
    val flowerDisplay: FlowerDisplayState = FlowerDisplayState(),
    val audio: AudioState = AudioState(),
    val daily: DailyPracticeState = DailyPracticeState()
) {
    /**
     * Reset all session-related state to defaults.
     * Used by selectLanguage, selectLesson, selectMode, importLessonPack,
     * addLanguage, and refreshLessons to clear stale training state.
     */
    fun resetSessionState(): TrainingUiState = copy(
        cardSession = CardSessionState(sessionState = SessionState.PAUSED),
        boss = BossState(),
        story = StoryState(),
        vocabSprint = VocabSprintState(),
        drill = DrillState()
    )

    /**
     * Full session reset including counters and timers.
     * Used when changing language or importing packs where all progress resets.
     */
    fun resetAllSessionState(): TrainingUiState = resetSessionState().copy(
        cardSession = CardSessionState(correctCount = 0, incorrectCount = 0, activeTimeMs = 0L, voiceActiveMs = 0L, voiceWordCount = 0, hintCount = 0, currentCard = null),
        elite = EliteState(eliteActive = false),
        boss = BossState(bossLessonRewards = emptyMap(), bossMegaRewards = emptyMap()),
        flowerDisplay = FlowerDisplayState(lessonFlowers = emptyMap(), currentLessonFlower = null)
    )
}


data class LessonLadderRow(
    val index: Int,
    val lessonId: String,
    val title: String,
    val uniqueCardShows: Int?,
    val daysSinceLastShow: Int?,
    val intervalLabel: String?
)
