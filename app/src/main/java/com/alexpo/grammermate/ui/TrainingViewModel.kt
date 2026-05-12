package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import java.io.File
import com.alexpo.grammermate.data.AsrEngine
import com.alexpo.grammermate.data.AsrModelManager
import com.alexpo.grammermate.data.AsrState
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.alexpo.grammermate.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.MixedReviewScheduler
import com.alexpo.grammermate.data.AppConfigStore
import com.alexpo.grammermate.data.AppConfig
import com.alexpo.grammermate.data.Normalizer
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
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.VocabEntry
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.FlowerVisual
import com.alexpo.grammermate.data.FlowerState
import com.alexpo.grammermate.data.LessonLadderCalculator
import com.alexpo.grammermate.data.StreakStore
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.DrillProgressStore
import com.alexpo.grammermate.data.HiddenCardStore
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.data.ProfileStore
import com.alexpo.grammermate.data.UserProfile
import com.alexpo.grammermate.data.VocabProgressStore
import com.alexpo.grammermate.data.WordMasteryStore
import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import com.alexpo.grammermate.data.TtsEngine
import com.alexpo.grammermate.data.TtsModelManager
import com.alexpo.grammermate.data.TtsModelRegistry
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.DownloadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.alexpo.grammermate.ui.helpers.DailySessionComposer
import com.alexpo.grammermate.ui.helpers.DailySessionHelper
import com.alexpo.grammermate.ui.helpers.TrainingStateAccess

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()
    private val successSoundId = soundPool.load(application, R.raw.voicy_correct_answer, 1)
    private val errorSoundId = soundPool.load(application, R.raw.voicy_bad_answer, 1)
    private val loadedSounds = mutableSetOf<Int>()
    private val logTag = "GrammarMate"
    private val lessonStore = LessonStore(application)
    private val progressStore = ProgressStore(application)
    private val configStore = AppConfigStore(application)
    private val masteryStore = MasteryStore(application)
    private val streakStore = StreakStore(application)
    private val badSentenceStore = BadSentenceStore(application)
    private val hiddenCardStore = HiddenCardStore(application)
    private val drillProgressStore = DrillProgressStore(application)
    private val vocabProgressStore = VocabProgressStore(application)
    private val wordMasteryStore = WordMasteryStore(application)
    private val backupManager = BackupManager(application)
    private val profileStore = ProfileStore(application)
    private val ttsModelManager = TtsModelManager(application)
    private val ttsEngine = TtsEngine(application)
    private val asrModelManager = AsrModelManager(application)
    private val asrEngine: AsrEngine? = try {
        AsrEngine(application)
    } catch (e: Exception) {
        Log.e(logTag, "ASR engine creation failed — sherpa-onnx API may not support ASR", e)
        null
    }
    private var sessionCards: List<SentenceCard> = emptyList()
    private var bossCards: List<SentenceCard> = emptyList()
    private var eliteCards: List<SentenceCard> = emptyList()
    private var vocabSession: List<VocabEntry> = emptyList()
    private var subLessonTotal: Int = 0
    private var subLessonCount: Int = 0
    private var lessonSchedules: Map<String, LessonSchedule> = emptyMap()
    private var scheduleKey: String = ""
    private var timerJob: Job? = null
    private var activeStartMs: Long? = null
    private var forceBackupOnSave: Boolean = false
    private var prebuiltDailySession: List<DailyTask>? = null
    private var lastDailyTasks: List<DailyTask>? = null
    private val subLessonSizeMin = TrainingConfig.SUB_LESSON_SIZE_MIN
    private val subLessonSizeMax = TrainingConfig.SUB_LESSON_SIZE_MAX
    private val subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
    private val eliteStepCount = TrainingConfig.ELITE_STEP_COUNT
    private var eliteSizeMultiplier: Double = 1.25

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState

    private val dailySessionHelper = DailySessionHelper(object : TrainingStateAccess {
        override val uiState: StateFlow<TrainingUiState> = _uiState
        override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
            _uiState.update(transform)
        }
        override fun saveProgress() = this@TrainingViewModel.saveProgress()
    })


    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds.add(sampleId)
            }
        }
        Log.d(logTag, "Update: duolingo sfx, prompt in speech UI, voice loop rules, stop resets progress")
        lessonStore.ensureSeedData()
        badSentenceStore.migrateIfNeeded(lessonStore)
        val progress = progressStore.load()
        val config = configStore.load()
        val profile = profileStore.load()
        eliteSizeMultiplier = config.eliteSizeMultiplier
        val bossLessonRewards = progress.bossLessonRewards.mapNotNull { (lessonId, reward) ->
            val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
            lessonId to parsed
        }.toMap()
        val bossMegaRewards = progress.bossMegaRewards.mapNotNull { (lessonId, reward) ->
            val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
            lessonId to parsed
        }.toMap()
        val languages = lessonStore.getLanguages()
        val packs = lessonStore.getInstalledPacks()
        val selectedLanguageId = languages.firstOrNull { it.id == progress.languageId }?.id ?: "en"
        val lessons = lessonStore.getLessons(selectedLanguageId)
        val selectedLessonId = progress.lessonId?.let { id ->
            lessons.firstOrNull { it.id == id }?.id
        } ?: lessons.firstOrNull()?.id
        val normalizedEliteSpeeds = normalizeEliteSpeeds(progress.eliteBestSpeeds)
        val lessonIdWasValid = progress.lessonId != null &&
            lessons.any { it.id == progress.lessonId }
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
            it.copy(
                languages = languages,
                installedPacks = packs,
                selectedLanguageId = selectedLanguageId,
                activePackId = initialActivePackId,
                activePackLessonIds = initialPackLessonIds,
                lessons = lessons,
                selectedLessonId = selectedLessonId,
                mode = progress.mode,
                sessionState = progress.state,
                currentIndex = progress.currentIndex,
                correctCount = progress.correctCount,
                incorrectCount = progress.incorrectCount,
                incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                activeTimeMs = progress.activeTimeMs,
                voiceActiveMs = progress.voiceActiveMs,
                voiceWordCount = progress.voiceWordCount,
                hintCount = progress.hintCount,
                voicePromptStartMs = null,
                subLessonTotal = 0,
                subLessonCount = 0,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                bossLessonRewards = bossLessonRewards,
                bossMegaRewards = bossMegaRewards,
                testMode = config.testMode,
                eliteActive = false,
                eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1),
                eliteBestSpeeds = normalizedEliteSpeeds,
                eliteFinishedToken = 0,
                eliteUnlocked = resolveEliteUnlocked(lessons, config.testMode),
                eliteSizeMultiplier = config.eliteSizeMultiplier,
                vocabSprintLimit = config.vocabSprintLimit,
                currentStreak = streakData.currentStreak,
                longestStreak = streakData.longestStreak,
                userName = profile.userName,
                badSentenceCount = initialActivePackId?.let { badSentenceStore.getBadSentenceCount(it) } ?: 0,
                useOfflineAsr = config.useOfflineAsr,
                asrModelReady = asrModelManager.isReady(),
                initialScreen = restoredScreen,
                vocabMasteredCount = wordMasteryStore.getMasteredCount()
            )
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        refreshFlowerStates()
        if (_uiState.value.sessionState == SessionState.ACTIVE && _uiState.value.currentCard != null) {
            resumeTimer()
            _uiState.value.currentCard?.let { recordCardShowForMastery(it) }
            if (_uiState.value.inputMode == InputMode.VOICE) {
                _uiState.update { it.copy(voiceTriggerToken = it.voiceTriggerToken + 1) }
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
                    val currentLang = current.selectedLanguageId
                    val selectedLang = languages.firstOrNull { it.id == currentLang }?.id
                        ?: languages.firstOrNull()?.id
                        ?: "en"
                    val lessons = lessonStore.getLessons(selectedLang)
                    val currentLessonId = current.selectedLessonId
                    val selectedLessonId = lessons.firstOrNull { it.id == currentLessonId }?.id
                        ?: lessons.firstOrNull()?.id
                    val reloadedPackIds = packs.map { it.packId }.toSet()
                    // Keep current activePackId if it still exists after reload,
                    // otherwise derive from lessonId, otherwise fall back to first pack.
                    val updatedPackId = if (current.activePackId != null && current.activePackId in reloadedPackIds) {
                        current.activePackId
                    } else {
                        selectedLessonId?.let { lessonStore.getPackIdForLesson(it) }
                            ?: packs.firstOrNull { it.languageId == selectedLang }?.packId
                    }
                    val updatedPackLessonIds = updatedPackId?.let { lessonStore.getLessonIdsForPack(it) }
                    current.copy(
                        languages = languages,
                        installedPacks = packs,
                        selectedLanguageId = selectedLang,
                        activePackId = updatedPackId,
                        activePackLessonIds = updatedPackLessonIds,
                        lessons = lessons,
                        selectedLessonId = selectedLessonId,
                        eliteUnlocked = resolveEliteUnlocked(lessons, current.testMode)
                    )
                }
                val updatedLessons = lessonStore.getLessons(_uiState.value.selectedLanguageId)
                rebuildSchedules(updatedLessons)
                buildSessionCards()
                refreshFlowerStates()
            }
        }

        // TTS state collection
        checkTtsModel()
        checkAllTtsModels()
        checkAsrModel()
        startBackgroundTtsDownload()
        viewModelScope.launch {
            ttsEngine.state.collect { ttsState ->
                _uiState.update { it.copy(ttsState = ttsState) }
            }
        }

        // Pre-build daily practice session in background for faster start
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val packId = state.activePackId
            val langId = state.selectedLanguageId
            val lessonId = state.selectedLessonId
            if (packId != null && lessonId != null) {
                val lessonLevel = (state.lessons.indexOfFirst { it.id == lessonId } + 1).coerceIn(1, 12)
                val verbDrillStore = VerbDrillStore(getApplication(), packId = packId)
                val packWordMasteryStore = WordMasteryStore(getApplication(), packId = packId)
                val cumulativeTenses = lessonStore.getCumulativeTenses(packId, lessonLevel)
                val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore)
                val tasks = composer.buildSession(lessonLevel, packId, langId, lessonId, cumulativeTenses)
                if (tasks.isNotEmpty()) {
                    prebuiltDailySession = tasks
                }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update {
            val resetAttempts = it.answerText != null || it.incorrectAttemptsForCard >= 3
            it.copy(
                inputText = text,
                incorrectAttemptsForCard = if (resetAttempts) 0 else it.incorrectAttemptsForCard,
                answerText = if (resetAttempts) null else it.answerText
            )
        }
    }

    fun onVoicePromptStarted() {
        _uiState.update {
            it.copy(voicePromptStartMs = SystemClock.elapsedRealtime())
        }
    }

    fun setInputMode(mode: InputMode) {
        _uiState.update {
            val resetAttempts = it.answerText != null || it.incorrectAttemptsForCard >= 3
            val shouldTriggerVoice = mode == InputMode.VOICE &&
                resetAttempts &&
                it.sessionState == SessionState.ACTIVE
            it.copy(
                inputMode = mode,
                incorrectAttemptsForCard = if (resetAttempts) 0 else it.incorrectAttemptsForCard,
                answerText = if (resetAttempts) null else it.answerText,
                voiceTriggerToken = if (shouldTriggerVoice) it.voiceTriggerToken + 1 else it.voiceTriggerToken,
                voicePromptStartMs = if (mode == InputMode.VOICE) it.voicePromptStartMs else null
            )
        }

        // Update word bank when switching to WORD_BANK mode
        if (mode == InputMode.WORD_BANK) {
            updateWordBank()
        }

        Log.d(logTag, "Input mode changed: $mode")
    }

    fun selectLanguage(languageId: String) {
        pauseTimer()
        vocabSession = emptyList()
        sessionCards = emptyList()
        bossCards = emptyList()
        eliteCards = emptyList()
        scheduleKey = "" // Force rebuild schedules
        val lessons = lessonStore.getLessons(languageId)
        val selectedLessonId = lessons.firstOrNull()?.id
        // Derive activePackId for the new language from the selected lesson.
        val newPackId = selectedLessonId?.let { lessonStore.getPackIdForLesson(it) }
            ?: lessonStore.getInstalledPacks().firstOrNull { it.languageId == languageId }?.packId
        val newPackLessonIds = newPackId?.let { lessonStore.getLessonIdsForPack(it) }
        _uiState.update {
            it.copy(
                selectedLanguageId = languageId,
                lessons = lessons,
                selectedLessonId = selectedLessonId,
                activePackId = newPackId,
                activePackLessonIds = newPackLessonIds,
                eliteActive = false,
                eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode),
                currentIndex = 0,
                correctCount = 0,
                incorrectCount = 0,
                incorrectAttemptsForCard = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                inputMode = InputMode.VOICE,
                sessionState = SessionState.PAUSED,
                voicePromptStartMs = null,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                storyErrorMessage = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabWordBankWords = emptyList(),
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                bossLessonRewards = emptyMap(),
                bossMegaRewards = emptyMap(),
                lessonFlowers = emptyMap(),
                currentLessonFlower = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList(),
                vocabMasteredCount = wordMasteryStore.getMasteredCount()
            )
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        refreshFlowerStates()
        saveProgress()
        ttsModelManager.currentLanguageId = languageId
        checkTtsModel()
        // Only switch ASR language if engine is already initialized and ready.
        // Calling setLanguage() when ASR is not ready crashes the native layer.
        if (asrEngine?.isReady == true) {
            asrEngine.setLanguage(languageId)
        }
    }

    fun selectLesson(lessonId: String) {
        pauseTimer()
        vocabSession = emptyList()
        sessionCards = emptyList()
        bossCards = emptyList()
        eliteCards = emptyList()

        // Resolve the pack for this lesson and set as active
        val packId = lessonStore.getPackIdForLesson(lessonId)
        val packLessonIds = packId?.let { lessonStore.getLessonIdsForPack(it) }

        // Rebuild schedules BEFORE reading them
        rebuildSchedules(_uiState.value.lessons)

        // Calculate active sub-lesson index based on completed count
        val schedule = lessonSchedules[lessonId]
        val subLessons = schedule?.subLessons.orEmpty()
        val mastery = masteryStore.get(lessonId, _uiState.value.selectedLanguageId)
        val completedCount = calculateCompletedSubLessons(subLessons, mastery, lessonId)
        val nextActiveIndex = completedCount.coerceAtMost((subLessons.size - 1).coerceAtLeast(0))

        _uiState.update {
            it.copy(
                selectedLessonId = lessonId,
                activePackId = packId,
                activePackLessonIds = packLessonIds,
                mode = TrainingMode.LESSON,
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.PAUSED,
                voicePromptStartMs = null,
                inputMode = InputMode.VOICE,
                activeSubLessonIndex = nextActiveIndex,
                completedSubLessonCount = completedCount,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                storyErrorMessage = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabWordBankWords = emptyList(),
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList(),
                isDrillMode = false,
                drillCardIndex = 0,
                drillTotalCards = 0,
                drillShowStartDialog = false,
                drillHasProgress = false,
                currentCard = null
            )
        }
        buildSessionCards()
        refreshFlowerStates()
        saveProgress()
    }

    fun selectPack(packId: String) {
        val packLessonIds = lessonStore.getLessonIdsForPack(packId)
        if (packLessonIds.isNotEmpty()) {
            val currentLessonId = _uiState.value.selectedLessonId
            val lessonId = if (currentLessonId != null && currentLessonId in packLessonIds) currentLessonId else packLessonIds.first()
            selectLesson(lessonId)
        } else {
            // Drill-only pack — set activePackId without selecting a lesson
            _uiState.update {
                it.copy(
                    activePackId = packId,
                    activePackLessonIds = emptyList(),
                    selectedLessonId = null
                )
            }
            saveProgress()
        }
    }

    fun selectMode(mode: TrainingMode) {
        pauseTimer()
        vocabSession = emptyList()
        _uiState.update {
            it.copy(
                mode = mode,
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.PAUSED,
                voicePromptStartMs = null,
                inputMode = InputMode.VOICE,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                storyErrorMessage = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabWordBankWords = emptyList(),
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList(),
                isDrillMode = false,
                drillCardIndex = 0,
                drillTotalCards = 0,
                drillShowStartDialog = false,
                drillHasProgress = false
            )
        }
        buildSessionCards()
        saveProgress()
    }

    fun submitAnswer(): SubmitResult {
        val state = _uiState.value
        if (state.sessionState != SessionState.ACTIVE) return SubmitResult(false, false)
        if (state.inputText.isBlank() && !state.testMode) return SubmitResult(false, false)
        val card = currentCard() ?: return SubmitResult(false, false)
        val normalizedInput = Normalizer.normalize(state.inputText)
        val accepted = state.testMode || card.acceptedAnswers.any { Normalizer.normalize(it) == normalizedInput }
        val voiceStartMs = if (state.inputMode == InputMode.VOICE) state.voicePromptStartMs else null
        val voiceDurationMs = voiceStartMs?.let { SystemClock.elapsedRealtime() - it }
        val voiceWords = if (voiceStartMs != null) countMetricWords(state.inputText) else 0
        val shouldAddVoiceMetrics = accepted && voiceDurationMs != null && voiceWords > 0
        var hintShown = false
        if (accepted) {
            playSuccessTone()
            // Record card show for mastery tracking
            recordCardShowForMastery(card)
            val isLastCard = state.currentIndex >= sessionCards.lastIndex
            if (state.bossActive) {
                if (isLastCard) {
                    _uiState.update {
                        it.copy(
                            correctCount = it.correctCount + 1,
                            lastResult = null,
                            incorrectAttemptsForCard = 0,
                            answerText = null,
                            voiceActiveMs = if (shouldAddVoiceMetrics) {
                                it.voiceActiveMs + (voiceDurationMs ?: 0L)
                            } else {
                                it.voiceActiveMs
                            },
                            voiceWordCount = if (shouldAddVoiceMetrics) {
                                it.voiceWordCount + voiceWords
                            } else {
                                it.voiceWordCount
                            },
                            voicePromptStartMs = null
                        )
                    }
                    updateBossProgress(state.bossTotal)
                    finishBoss()
                } else {
                    _uiState.update {
                        it.copy(
                            correctCount = it.correctCount + 1,
                            lastResult = true,
                            incorrectAttemptsForCard = 0,
                            answerText = null,
                            voiceActiveMs = if (shouldAddVoiceMetrics) {
                                it.voiceActiveMs + (voiceDurationMs ?: 0L)
                            } else {
                                it.voiceActiveMs
                            },
                            voiceWordCount = if (shouldAddVoiceMetrics) {
                                it.voiceWordCount + voiceWords
                            } else {
                                it.voiceWordCount
                            },
                            voicePromptStartMs = null
                        )
                    }
                    nextCard(triggerVoice = state.inputMode == InputMode.VOICE)
                }
            } else if (state.eliteActive && isLastCard) {
                pauseTimer()
                val speed = calculateSpeedPerMinute(state.voiceActiveMs, state.voiceWordCount)
                val bestSpeeds = normalizeEliteSpeeds(state.eliteBestSpeeds)
                val stepIndex = state.eliteStepIndex.coerceIn(0, eliteStepCount - 1)
                val currentBest = bestSpeeds.getOrNull(stepIndex) ?: 0.0
                val nextSpeeds = bestSpeeds.toMutableList().apply {
                    if (speed > currentBest) {
                        this[stepIndex] = speed
                    }
                }
                val nextStep = (stepIndex + 1) % eliteStepCount
                _uiState.update {
                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastResult = null,
                        incorrectAttemptsForCard = 0,
                        answerText = null,
                        voiceActiveMs = if (shouldAddVoiceMetrics) {
                            it.voiceActiveMs + (voiceDurationMs ?: 0L)
                        } else {
                            it.voiceActiveMs
                        },
                        voiceWordCount = if (shouldAddVoiceMetrics) {
                            it.voiceWordCount + voiceWords
                        } else {
                            it.voiceWordCount
                        },
                        voicePromptStartMs = null,
                        sessionState = SessionState.PAUSED,
                        currentIndex = 0,
                        eliteActive = false,
                        eliteStepIndex = nextStep,
                        eliteBestSpeeds = nextSpeeds,
                        eliteFinishedToken = it.eliteFinishedToken + 1
                    )
                }
                saveProgress()
            } else if (state.isDrillMode) {
                // Drill: seamless advance — same flow as regular nextCard()
                _uiState.update {
                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastResult = null,
                        incorrectAttemptsForCard = 0,
                        answerText = null,
                        inputText = "",
                        voiceActiveMs = if (shouldAddVoiceMetrics) {
                            it.voiceActiveMs + (voiceDurationMs ?: 0L)
                        } else {
                            it.voiceActiveMs
                        },
                        voiceWordCount = if (shouldAddVoiceMetrics) {
                            it.voiceWordCount + voiceWords
                        } else {
                            it.voiceWordCount
                        },
                        voicePromptStartMs = null,
                        sessionState = SessionState.ACTIVE
                    )
                }
                advanceDrillCard()
            } else if (isLastCard) {
                pauseTimer()

                // Check if current sub-lesson is WARMUP before updating state
                val currentState = _uiState.value
                _uiState.update {
                    val nextCompleted = (it.completedSubLessonCount + 1).coerceAtMost(it.subLessonCount)
                    // Рассчитываем реальный прогресс на основе сохранённых карточек
                    val lessonId = it.selectedLessonId
                    val mastery = lessonId?.let { id -> masteryStore.get(id, it.selectedLanguageId) }
                    val schedule = lessonId?.let { id -> lessonSchedules[id] }
                    val subLessons = schedule?.subLessons.orEmpty()
                    val actualCompletedCount = calculateCompletedSubLessons(subLessons, mastery, lessonId)

                    // Не двигаем индекс назад, если пользователь повторяет старый подурок
                    // nextCompleted - это индекс только что завершённого подурока + 1
                    // actualCompletedCount - это реальный прогресс из сохранённых данных
                    val preservedActiveIndex = maxOf(it.activeSubLessonIndex, actualCompletedCount)
                    val finalActiveIndex = preservedActiveIndex.coerceAtMost((it.subLessonCount - 1).coerceAtLeast(0))

                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastResult = null,
                        incorrectAttemptsForCard = 0,
                        answerText = null,
                        voiceActiveMs = if (shouldAddVoiceMetrics) {
                            it.voiceActiveMs + (voiceDurationMs ?: 0L)
                        } else {
                            it.voiceActiveMs
                        },
                        voiceWordCount = if (shouldAddVoiceMetrics) {
                            it.voiceWordCount + voiceWords
                        } else {
                            it.voiceWordCount
                        },
                        voicePromptStartMs = null,
                        sessionState = SessionState.PAUSED,
                        currentIndex = 0,
                        activeSubLessonIndex = finalActiveIndex,
                        completedSubLessonCount = maxOf(nextCompleted, actualCompletedCount),
                        subLessonFinishedToken = it.subLessonFinishedToken + 1
                    )
                }
                markSubLessonCardsShown(sessionCards)
                buildSessionCards()
                // Check if lesson is completed and update flower states
                checkAndMarkLessonCompleted()
                refreshFlowerStates()
                updateStreak()
                forceBackupOnSave = true
            } else {
                _uiState.update {
                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastResult = true,
                        incorrectAttemptsForCard = 0,
                        answerText = null,
                        voiceActiveMs = if (shouldAddVoiceMetrics) {
                            it.voiceActiveMs + (voiceDurationMs ?: 0L)
                        } else {
                            it.voiceActiveMs
                        },
                        voiceWordCount = if (shouldAddVoiceMetrics) {
                            it.voiceWordCount + voiceWords
                        } else {
                            it.voiceWordCount
                        },
                        voicePromptStartMs = null
                    )
                }
                nextCard(triggerVoice = state.inputMode == InputMode.VOICE)
            }
        } else {
            playErrorTone()
            val nextIncorrect = state.incorrectAttemptsForCard + 1
            val hint = if (nextIncorrect >= 3) card.acceptedAnswers.joinToString(" / ") else null
            hintShown = hint != null
            val shouldTriggerVoice = !hintShown && state.inputMode == InputMode.VOICE
            _uiState.update {
                it.copy(
                    incorrectCount = it.incorrectCount + 1,
                    incorrectAttemptsForCard = if (hintShown) 0 else nextIncorrect,
                    lastResult = false,
                    answerText = hint,
                    inputText = if (state.inputMode == InputMode.VOICE) "" else it.inputText,
                    sessionState = if (hint != null) SessionState.HINT_SHOWN else it.sessionState,
                    voiceTriggerToken = if (shouldTriggerVoice) it.voiceTriggerToken + 1 else it.voiceTriggerToken,
                    voicePromptStartMs = null
                )
            }
            if (hintShown) {
                pauseTimer()
            }
        }
        saveProgress()
        // Refresh flower states after card show is recorded
        if (accepted) {
            refreshFlowerStates()
        }
        Log.d(logTag, "Answer submitted: accepted=$accepted")
        return SubmitResult(accepted, hintShown)
    }

    fun nextCard(triggerVoice: Boolean = false) {
        val state = _uiState.value
        val wasHintShown = state.sessionState == SessionState.HINT_SHOWN
        val nextIndex = (state.currentIndex + 1).coerceAtMost(sessionCards.lastIndex)
        val nextCard = sessionCards.getOrNull(nextIndex)
        val nextProgress = if (state.bossActive) {
            (state.bossProgress.coerceAtLeast(nextIndex)).coerceAtMost(state.bossTotal)
        } else {
            state.bossProgress
        }
        val nextReward = if (state.bossActive) {
            resolveBossReward(nextProgress, state.bossTotal)
        } else {
            state.bossReward
        }
        val isNewReward = state.bossActive && nextReward != null && nextReward != state.bossReward
        val rewardMessage = if (isNewReward) {
            bossRewardMessage(nextReward!!)
        } else {
            state.bossRewardMessage
        }
        val pauseForReward = isNewReward
        if (pauseForReward) {
            pauseTimer()
        }
        _uiState.update {
            val shouldTrigger = triggerVoice && it.inputMode == InputMode.VOICE && !pauseForReward
            it.copy(
                currentIndex = nextIndex,
                currentCard = nextCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = if (pauseForReward) SessionState.PAUSED else SessionState.ACTIVE,
                voiceTriggerToken = if (shouldTrigger) it.voiceTriggerToken + 1 else it.voiceTriggerToken,
                voicePromptStartMs = null,
                bossProgress = nextProgress,
                bossReward = nextReward ?: it.bossReward,
                bossRewardMessage = rewardMessage
            )
        }
        nextCard?.let { recordCardShowForMastery(it) }

        // Update word bank if in WORD_BANK mode
        if (_uiState.value.inputMode == InputMode.WORD_BANK) {
            updateWordBank()
        }

        if (wasHintShown && !pauseForReward) {
            resumeTimer()
        }
        saveProgress()
    }

    fun prevCard() {
        val prevIndex = (_uiState.value.currentIndex - 1).coerceAtLeast(0)
        val prevCard = sessionCards.getOrNull(prevIndex)
        _uiState.update {
            it.copy(
                currentIndex = prevIndex,
                currentCard = prevCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                voicePromptStartMs = null
            )
        }
        prevCard?.let { recordCardShowForMastery(it) }
        saveProgress()
    }

    fun togglePause() {
        if (_uiState.value.sessionState == SessionState.ACTIVE) {
            pauseTimer()
            _uiState.update { it.copy(sessionState = SessionState.PAUSED, voicePromptStartMs = null) }
            saveProgress()
            return
        }
        startSession()
    }

    fun pauseSession() {
        pauseTimer()
        _uiState.update { it.copy(sessionState = SessionState.PAUSED, voicePromptStartMs = null) }
        saveProgress()
    }

    fun finishSession() {
        if (sessionCards.isEmpty()) return
        if (_uiState.value.eliteActive) {
            cancelEliteSession()
            return
        }
        if (_uiState.value.bossActive) {
            finishBoss()
            return
        }
        pauseTimer()
        val state = _uiState.value
        val minutes = state.activeTimeMs / 60000.0
        val rating = if (minutes <= 0.0) 0.0 else state.correctCount / minutes
        val firstCard = sessionCards.firstOrNull()
        _uiState.update {
            it.copy(
                sessionState = SessionState.PAUSED,
                lastRating = rating,
                incorrectAttemptsForCard = 0,
                lastResult = null,
                answerText = null,
                currentIndex = 0,
                currentCard = firstCard,
                inputText = "",
                voicePromptStartMs = null
            )
        }
        saveProgress()
        refreshFlowerStates()
        Log.d(logTag, "Session finished. Rating=$rating")
    }

    fun showAnswer() {
        val card = currentCard() ?: return
        pauseTimer()
        _uiState.update {
            it.copy(
                answerText = card.acceptedAnswers.joinToString(" / "),
                sessionState = SessionState.HINT_SHOWN,
                inputText = if (it.inputMode == InputMode.VOICE) "" else it.inputText,
                hintCount = it.hintCount + 1,
                voicePromptStartMs = null
            )
        }
        saveProgress()
    }

    fun importLesson(uri: Uri) {
        val languageId = _uiState.value.selectedLanguageId
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
                it.copy(
                    languages = lessonStore.getLanguages(),
                    installedPacks = lessonStore.getInstalledPacks(),
                    selectedLanguageId = pack.languageId,
                    lessons = lessons,
                    selectedLessonId = selectedLessonId,
                    eliteActive = false,
                    eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode),
                    mode = TrainingMode.LESSON,
                    sessionState = SessionState.PAUSED,
                    currentIndex = 0,
                    correctCount = 0,
                    incorrectCount = 0,
                    incorrectAttemptsForCard = 0,
                    inputMode = InputMode.VOICE,
                    activeTimeMs = 0L,
                    voiceActiveMs = 0L,
                    voiceWordCount = 0,
                    hintCount = 0,
                    voicePromptStartMs = null,
                    inputText = "",
                    lastResult = null,
                    answerText = null,
                    activeSubLessonIndex = 0,
                    completedSubLessonCount = 0,
                    subLessonFinishedToken = 0,
                    storyCheckInDone = false,
                    storyCheckOutDone = false,
                    activeStory = null,
                    storyErrorMessage = null,
                    currentVocab = null,
                    vocabInputText = "",
                    vocabAttempts = 0,
                    vocabAnswerText = null,
                    vocabIndex = 0,
                    vocabTotal = 0,
                    vocabWordBankWords = emptyList(),
                    vocabFinishedToken = 0,
                    vocabErrorMessage = null,
                    vocabInputMode = InputMode.VOICE,
                    vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                    bossReward = null,
                    bossRewardMessage = null,
                    bossFinishedToken = 0,
                    bossErrorMessage = null,
                    wordBankWords = emptyList(),
                    selectedWords = emptyList()
                )
            }
            rebuildSchedules(lessons)
            buildSessionCards()
            saveProgress()
        } catch (e: Exception) {
            Log.e(logTag, "Lesson pack import failed", e)
        }
    }
    fun resetAndImportLesson(uri: Uri) {
        val languageId = _uiState.value.selectedLanguageId
        lessonStore.deleteAllLessons(languageId)
        val lesson = lessonStore.importFromUri(languageId, uri, getApplication<Application>().contentResolver)
        refreshLessons(lesson.id)
    }

    fun deleteLesson(lessonId: String) {
        val languageId = _uiState.value.selectedLanguageId
        lessonStore.deleteLesson(languageId, lessonId)
        val selected = if (_uiState.value.selectedLessonId == lessonId) null else _uiState.value.selectedLessonId
        refreshLessons(selected)
    }

    fun createEmptyLesson(title: String) {
        val languageId = _uiState.value.selectedLanguageId
        val lesson = lessonStore.createEmptyLesson(languageId, title)
        refreshLessons(lesson.id)
    }

    fun addLanguage(name: String) {
        val language = lessonStore.addLanguage(name)
        vocabSession = emptyList()
        val lessons = lessonStore.getLessons(language.id)
        val selectedLessonId = lessons.firstOrNull()?.id
        _uiState.update {
            it.copy(
                languages = lessonStore.getLanguages(),
                installedPacks = lessonStore.getInstalledPacks(),
                selectedLanguageId = language.id,
                lessons = lessons,
                selectedLessonId = selectedLessonId,
                eliteActive = false,
                eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode),
                mode = TrainingMode.LESSON,
                sessionState = SessionState.PAUSED,
                currentIndex = 0,
                correctCount = 0,
                incorrectCount = 0,
                incorrectAttemptsForCard = 0,
                inputMode = InputMode.VOICE,
                activeTimeMs = 0L,
                voiceActiveMs = 0L,
                voiceWordCount = 0,
                hintCount = 0,
                voicePromptStartMs = null,
                inputText = "",
                lastResult = null,
                answerText = null,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                storyErrorMessage = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabWordBankWords = emptyList(),
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList()
            )
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        saveProgress()
    }

    fun deleteAllLessons() {
        val languageId = _uiState.value.selectedLanguageId
        lessonStore.deleteAllLessons(languageId)
        refreshLessons(null)
        _uiState.update { it.copy(installedPacks = lessonStore.getInstalledPacks()) }
    }

    /**
     * Reset ALL progress: mastery, daily practice, verb drill, vocab mastery, training progress.
     */
    fun resetAllProgress() {
        // Clear training progress file
        progressStore.clear()

        // Clear mastery (card show tracking)
        masteryStore.clear()

        // Clear verb drill and word mastery for every installed pack
        val packs = lessonStore.getInstalledPacks()
        val baseDir = File(getApplication<Application>().filesDir, "grammarmate")
        for (pack in packs) {
            val verbDrillFile = File(baseDir, "drills/${pack.packId}/verb_drill_progress.yaml")
            if (verbDrillFile.exists()) verbDrillFile.delete()

            val wordMasteryFile = File(baseDir, "drills/${pack.packId}/word_mastery.yaml")
            if (wordMasteryFile.exists()) wordMasteryFile.delete()
        }

        // Clear legacy (pack-less) stores if they exist
        wordMasteryStore.saveAll(emptyMap())

        // Reset UI state
        _uiState.update {
            it.copy(
                dailySession = DailySessionState(),
                currentIndex = 0,
                correctCount = 0,
                incorrectCount = 0,
                sessionState = SessionState.PAUSED,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0
            )
        }

        // Refresh lessons to reflect reset mastery (flower states)
        refreshLessons(null)

        Log.d(logTag, "All progress reset: mastery, daily, verb drill, vocab mastery, training progress")
    }

    fun deletePack(packId: String) {
        val pack = lessonStore.getInstalledPacks().firstOrNull { it.packId == packId } ?: return
        val languageId = pack.languageId
        lessonStore.removeInstalledPackData(packId)
        if (_uiState.value.selectedLanguageId == languageId) {
            refreshLessons(null)
        }
        _uiState.update { it.copy(installedPacks = lessonStore.getInstalledPacks()) }
    }

    fun toggleTestMode() {
        val newTestMode = !_uiState.value.testMode
        _uiState.update {
            it.copy(
                testMode = newTestMode,
                eliteUnlocked = resolveEliteUnlocked(_uiState.value.lessons, newTestMode)
            )
        }
        configStore.save(
            configStore.load().copy(testMode = newTestMode)
        )
        Log.d(logTag, "Test mode toggled: $newTestMode")
    }

    fun updateVocabSprintLimit(limit: Int) {
        val nextLimit = limit.coerceAtLeast(0)
        _uiState.update { it.copy(vocabSprintLimit = nextLimit) }
        configStore.save(
            configStore.load().copy(vocabSprintLimit = nextLimit)
        )
    }

    private fun refreshLessons(selectedLessonId: String?) {
        pauseTimer()
        vocabSession = emptyList()
        val languageId = _uiState.value.selectedLanguageId
        val lessons = lessonStore.getLessons(languageId)
        val selected = selectedLessonId ?: lessons.firstOrNull()?.id
        _uiState.update {
            it.copy(
                lessons = lessons,
                selectedLessonId = selected,
                eliteActive = false,
                eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode),
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.PAUSED,
                voicePromptStartMs = null,
                inputMode = InputMode.VOICE,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                storyErrorMessage = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabWordBankWords = emptyList(),
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList()
            )
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        saveProgress()
    }

    fun resumeFromSettings() {
        if (_uiState.value.sessionState == SessionState.ACTIVE) return
        startSession()
    }

    private fun rebuildSchedules(lessons: List<Lesson>) {
        val lessonKey = lessons.joinToString("|") { "${it.id}:${it.cards.size}" }
        val blockSize = subLessonSize.coerceIn(subLessonSizeMin, subLessonSizeMax)
        val key = "${lessonKey}|${blockSize}"
        if (key == scheduleKey) return
        scheduleKey = key
        lessonSchedules = MixedReviewScheduler(blockSize).build(lessons)
    }

    private fun buildSessionCards() {
        if (_uiState.value.bossActive || _uiState.value.eliteActive || _uiState.value.isDrillMode) return
        val state = _uiState.value
        val hiddenIds = hiddenCardStore.getHiddenCardIds()
        val lessons = state.lessons
        if (state.mode == TrainingMode.LESSON) {
            val schedule = lessonSchedules[state.selectedLessonId]
            val subLessons = schedule?.subLessons.orEmpty()
            subLessonCount = subLessons.size

            // Calculate completed sub-lessons based on shown cards
            val mastery = state.selectedLessonId?.let {
                masteryStore.get(it, state.selectedLanguageId)
            }
            val completedCount = calculateCompletedSubLessons(subLessons, mastery, state.selectedLessonId)

            val activeIndex = state.activeSubLessonIndex.coerceIn(0, (subLessonCount - 1).coerceAtLeast(0))
            val subLesson = subLessons.getOrNull(activeIndex)
            sessionCards = (subLesson?.cards ?: emptyList()).filter { it.id !in hiddenIds }
            subLessonTotal = sessionCards.size
            val safeIndex = _uiState.value.currentIndex.coerceIn(0, (sessionCards.size - 1).coerceAtLeast(0))
            val card = sessionCards.getOrNull(safeIndex)
            if (card == null && state.sessionState == SessionState.ACTIVE) {
                pauseTimer()
            }
            _uiState.update {
                it.copy(
                    currentIndex = safeIndex,
                    currentCard = card,
                    sessionState = if (card == null) SessionState.PAUSED else state.sessionState,
                    subLessonTotal = subLessonTotal,
                    subLessonCount = subLessonCount,
                    activeSubLessonIndex = activeIndex,
                    completedSubLessonCount = completedCount,
                    subLessonTypes = subLessons.map { item -> item.type }
                )
            }
            return
        }

        val lessonCards = when (state.mode) {
            TrainingMode.ALL_SEQUENTIAL -> lessons.flatMap { it.cards }.filter { it.id !in hiddenIds }
            // ALL_MIXED (Review) uses a random subset across all cards
            TrainingMode.ALL_MIXED -> {
                val reviewLimit = 300
                lessons.flatMap { it.allCards }.filter { it.id !in hiddenIds }.shuffled().take(reviewLimit)
            }
            else -> emptyList()
        }
        val blockSize = subLessonSize.coerceIn(subLessonSizeMin, subLessonSizeMax)
        subLessonCount = if (lessonCards.isEmpty()) 0 else (lessonCards.size + blockSize - 1) / blockSize
        val activeIndex = state.activeSubLessonIndex.coerceIn(0, (subLessonCount - 1).coerceAtLeast(0))
        val blockStart = activeIndex * blockSize
        val block = lessonCards.drop(blockStart).take(blockSize)
        sessionCards = block
        subLessonTotal = block.size
        val safeIndex = _uiState.value.currentIndex.coerceIn(0, (sessionCards.size - 1).coerceAtLeast(0))
        val card = sessionCards.getOrNull(safeIndex)
        if (card == null && state.sessionState == SessionState.ACTIVE) {
            pauseTimer()
        }
        _uiState.update {
            it.copy(
                currentIndex = safeIndex,
                currentCard = card,
                sessionState = if (card == null) SessionState.PAUSED else state.sessionState,
                subLessonTotal = subLessonTotal,
                subLessonCount = subLessonCount,
                activeSubLessonIndex = activeIndex,
                subLessonTypes = emptyList()
            )
        }
    }

    fun selectSubLesson(index: Int) {
        pauseTimer()
        _uiState.update {
            it.copy(
                activeSubLessonIndex = index.coerceAtLeast(0),
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                sessionState = SessionState.PAUSED
            )
        }
        buildSessionCards()
        saveProgress()
    }

    fun openEliteStep(index: Int) {
        pauseTimer()
        val stepIndex = index.coerceIn(0, eliteStepCount - 1)
        val cards = buildEliteCards()
        eliteCards = cards
        sessionCards = cards
        val firstCard = cards.firstOrNull()
        _uiState.update {
            it.copy(
                eliteActive = true,
                eliteStepIndex = stepIndex,
                currentIndex = 0,
                currentCard = firstCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                correctCount = 0,
                incorrectCount = 0,
                activeTimeMs = 0L,
                voiceActiveMs = 0L,
                voiceWordCount = 0,
                hintCount = 0,
                voicePromptStartMs = null,
                sessionState = SessionState.PAUSED,
                subLessonTotal = cards.size,
                subLessonCount = eliteStepCount,
                activeSubLessonIndex = stepIndex,
                completedSubLessonCount = 0
            )
        }
        saveProgress()
    }

    fun cancelEliteSession() {
        if (!_uiState.value.eliteActive) return
        pauseTimer()
        _uiState.update {
            it.copy(
                eliteActive = false,
                sessionState = SessionState.PAUSED,
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                voicePromptStartMs = null
            )
        }
        saveProgress()
        refreshFlowerStates()
    }

    // ── Daily Practice ────────────────────────────────────────────────────

    fun hasResumableDailySession(): Boolean {
        val progress = progressStore.load()
        return progress.dailyLevel > 0
    }

    fun startDailyPractice(lessonLevel: Int): Boolean {
        // Try pre-built session first
        val cached = prebuiltDailySession
        if (cached != null && cached.isNotEmpty()) {
            lastDailyTasks = cached
            dailySessionHelper.startDailySession(cached, lessonLevel)
            prebuiltDailySession = null
            return true
        }
        // Fallback to synchronous build
        val state = _uiState.value
        val packId = state.activePackId ?: return false
        val langId = state.selectedLanguageId
        val lessonId = state.selectedLessonId ?: return false

        val verbDrillStore = VerbDrillStore(getApplication(), packId = packId)
        val packWordMasteryStore = WordMasteryStore(getApplication(), packId = packId)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId, lessonLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore)
        val tasks = composer.buildSession(lessonLevel, packId, langId, lessonId, cumulativeTenses)
        Log.d(logTag, "DailyPractice fallback: built ${tasks.size} tasks, per-block=${tasks.groupBy { it.blockType }.mapValues { it.value.size }}")
        if (tasks.isEmpty()) return false

        lastDailyTasks = tasks
        dailySessionHelper.startDailySession(tasks, lessonLevel)
        return true
    }

    fun repeatDailyPractice(lessonLevel: Int): Boolean {
        val cached = lastDailyTasks
        if (cached != null && cached.isNotEmpty()) {
            dailySessionHelper.startDailySession(cached, lessonLevel)
            return true
        }
        // No cache — build fresh (same as start)
        return startDailyPractice(lessonLevel)
    }

    fun resumeDailyPractice(): Boolean {
        val progress = progressStore.load()
        val lessonLevel = progress.dailyLevel
        val savedTaskIndex = progress.dailyTaskIndex
        if (lessonLevel <= 0) return false

        val state = _uiState.value
        val packId = state.activePackId ?: return false
        val langId = state.selectedLanguageId
        val lessonId = state.selectedLessonId ?: return false

        val verbDrillStore = VerbDrillStore(getApplication(), packId = packId)
        val packWordMasteryStore = WordMasteryStore(getApplication(), packId = packId)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId, lessonLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore)
        val tasks = composer.buildSession(lessonLevel, packId, langId, lessonId, cumulativeTenses)
        if (tasks.isEmpty()) return false

        dailySessionHelper.startDailySession(tasks, lessonLevel)
        if (savedTaskIndex > 0 && savedTaskIndex < tasks.size) {
            dailySessionHelper.fastForwardTo(savedTaskIndex)
        }
        return true
    }

    fun advanceDailyTask(): Boolean {
        // Persist verb drill progress before advancing
        val task = dailySessionHelper.getCurrentTask()
        if (task is DailyTask.ConjugateVerb) {
            persistDailyVerbProgress(task.card)
        }
        return dailySessionHelper.nextTask()
    }

    fun advanceDailyBlock(): Boolean {
        return dailySessionHelper.advanceToNextBlock()
    }

    private fun persistDailyVerbProgress(card: VerbDrillCard) {
        val packId = _uiState.value.activePackId ?: return
        val store = VerbDrillStore(getApplication(), packId = packId)
        val comboKey = "|${card.tense ?: ""}"
        val existing = store.loadProgress()[comboKey]
        val everShown = (existing?.everShownCardIds ?: emptySet()) + card.id
        val todayShown = (existing?.todayShownCardIds ?: emptySet()) + card.id
        val updated = VerbDrillComboProgress(
            group = "",
            tense = card.tense ?: "",
            totalCards = existing?.totalCards ?: 0,
            everShownCardIds = everShown,
            todayShownCardIds = todayShown,
            lastDate = java.time.LocalDate.now().toString()
        )
        store.upsertComboProgress(comboKey, updated)
    }

    fun repeatDailyBlock(): Boolean {
        val state = _uiState.value
        val ds = state.dailySession
        if (!ds.active) return false
        val blockType = dailySessionHelper.getCurrentBlockType() ?: return false
        val packId = state.activePackId ?: return false
        val langId = state.selectedLanguageId
        val lessonId = state.selectedLessonId ?: return false
        val lessonLevel = ds.level

        val verbDrillStore = VerbDrillStore(getApplication(), packId = packId)
        val packWordMasteryStore = WordMasteryStore(getApplication(), packId = packId)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId, lessonLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore)
        val newTasks = composer.rebuildBlock(blockType, lessonLevel, packId, langId, lessonId, cumulativeTenses)
        if (newTasks.isEmpty()) return false

        dailySessionHelper.replaceCurrentBlock(newTasks)
        return true
    }

    fun cancelDailySession() {
        dailySessionHelper.endSession()
    }

    /**
     * Record an SRS rating for the current vocab flashcard in Daily Practice.
     * Rating: 0=Again, 1=Hard, 2=Good, 3=Easy — mirrors VocabDrillViewModel.answerRating logic.
     */
    fun rateVocabCard(rating: Int) {
        val task = dailySessionHelper.getCurrentTask() as? DailyTask.VocabFlashcard ?: return
        val wordId = task.word.id
        val state = _uiState.value
        val packId = state.activePackId ?: return
        val store = WordMasteryStore(getApplication(), packId = packId)
        val current = store.getMastery(wordId) ?: WordMasteryState.new(wordId)
        val now = System.currentTimeMillis()
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val newStepIndex = when (rating) {
            0 -> 0  // Again — reset
            1 -> current.intervalStepIndex  // Hard — stay
            2 -> (current.intervalStepIndex + 1).coerceIn(0, maxStep)  // Good — +1
            else -> (current.intervalStepIndex + 2).coerceIn(0, maxStep)  // Easy — +2
        }
        val newNextReview = WordMasteryState.computeNextReview(now, newStepIndex)
        val LEARNED_THRESHOLD = 3
        val isLearned = newStepIndex >= LEARNED_THRESHOLD
        val updated = current.copy(
            intervalStepIndex = newStepIndex,
            correctCount = current.correctCount + (if (rating != 0) 1 else 0),
            incorrectCount = current.incorrectCount + (if (rating == 0) 1 else 0),
            lastReviewDateMs = now,
            nextReviewDateMs = newNextReview,
            isLearned = isLearned
        )
        store.upsertMastery(updated)
    }

    fun getDailyCurrentTask(): DailyTask? {
        return dailySessionHelper.getCurrentTask()
    }

    fun getDailyBlockProgress(): com.alexpo.grammermate.ui.helpers.BlockProgress {
        return dailySessionHelper.getBlockProgress()
    }

    fun submitDailySentenceAnswer(input: String): Boolean {
        val task = dailySessionHelper.getCurrentTask() as? DailyTask.TranslateSentence ?: return false
        val card = task.card
        val normalized = Normalizer.normalize(input)
        val correct = card.acceptedAnswers.any { Normalizer.normalize(it) == normalized }
        if (correct) {
            playSuccessSound()
        } else {
            playErrorSound()
        }
        return correct
    }

    fun submitDailyVerbAnswer(input: String): Boolean {
        val task = dailySessionHelper.getCurrentTask() as? DailyTask.ConjugateVerb ?: return false
        val card = task.card
        val normalized = Normalizer.normalize(input)
        val correct = card.acceptedAnswers.any { Normalizer.normalize(it) == normalized }
        if (correct) {
            playSuccessSound()
        } else {
            playErrorSound()
        }
        return correct
    }

    fun getDailySentenceAnswer(): String? {
        val task = dailySessionHelper.getCurrentTask() as? DailyTask.TranslateSentence ?: return null
        return task.card.acceptedAnswers.firstOrNull()
    }

    fun getDailyVerbAnswer(): String? {
        val task = dailySessionHelper.getCurrentTask() as? DailyTask.ConjugateVerb ?: return null
        return task.card.answer
    }

    private fun playSuccessSound() {
        if (successSoundId in loadedSounds) {
            soundPool.play(successSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    private fun playErrorSound() {
        if (errorSoundId in loadedSounds) {
            soundPool.play(errorSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    // ── Drill Mode ───────────────────────────────────────────────────────
    // Seamless card training, no mastery/flower progress.
    // All drill cards in one continuous stream. Save position on exit.

    fun showDrillStartDialog(lessonId: String) {
        val lesson = _uiState.value.lessons.firstOrNull { it.id == lessonId } ?: return
        if (lesson.drillCards.isEmpty()) return
        val hasProgress = drillProgressStore.hasProgress(lessonId)
        _uiState.update {
            it.copy(
                drillShowStartDialog = true,
                drillHasProgress = hasProgress
            )
        }
    }

    fun startDrill(resume: Boolean) {
        val lessonId = _uiState.value.selectedLessonId ?: return
        val lesson = _uiState.value.lessons.firstOrNull { it.id == lessonId } ?: return
        val drillCards = lesson.drillCards
        if (drillCards.isEmpty()) return

        pauseTimer()
        vocabSession = emptyList()

        val startCardIndex = if (resume) {
            drillProgressStore.getDrillProgress(lessonId).coerceIn(0, drillCards.size - 1)
        } else {
            0
        }

        _uiState.update {
            it.copy(
                isDrillMode = true,
                drillCardIndex = startCardIndex,
                drillTotalCards = drillCards.size,
                drillShowStartDialog = false,
                drillHasProgress = false,
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                correctCount = 0,
                incorrectCount = 0,
                activeTimeMs = 0L,
                voiceActiveMs = 0L,
                voiceWordCount = 0,
                hintCount = 0,
                voicePromptStartMs = null,
                sessionState = SessionState.PAUSED,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                eliteActive = false,
                wordBankWords = emptyList(),
                selectedWords = emptyList(),
                badSentenceCount = _uiState.value.activePackId?.let { badSentenceStore.getBadSentenceCount(it) } ?: 0
            )
        }
        loadDrillCard(startCardIndex)
        saveProgress()
    }

    fun dismissDrillDialog() {
        _uiState.update { it.copy(drillShowStartDialog = false) }
    }

    private fun loadDrillCard(cardIndex: Int, activate: Boolean = false) {
        val lessonId = _uiState.value.selectedLessonId ?: return
        val lesson = _uiState.value.lessons.firstOrNull { it.id == lessonId } ?: return
        val drillCards = lesson.drillCards
        if (cardIndex >= drillCards.size) {
            finishDrill(lessonId)
            return
        }

        val card = drillCards[cardIndex]
        sessionCards = listOf(card)
        subLessonTotal = 1
        _uiState.update {
            it.copy(
                currentIndex = 0,
                currentCard = card,
                subLessonTotal = 1,
                drillCardIndex = cardIndex,
                sessionState = if (activate) SessionState.ACTIVE else SessionState.PAUSED,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0
            )
        }
        if (_uiState.value.inputMode == InputMode.VOICE) {
            _uiState.update { it.copy(voiceTriggerToken = it.voiceTriggerToken + 1) }
        }
    }

    fun advanceDrillCard() {
        val state = _uiState.value
        if (!state.isDrillMode) return
        val lessonId = state.selectedLessonId ?: return

        val nextIndex = state.drillCardIndex + 1
        drillProgressStore.saveDrillProgress(lessonId, nextIndex)

        if (nextIndex >= state.drillTotalCards) {
            finishDrill(lessonId)
        } else {
            loadDrillCard(nextIndex, activate = true)
        }
    }

    private fun finishDrill(lessonId: String) {
        drillProgressStore.clearDrillProgress(lessonId)
        _uiState.update {
            it.copy(
                isDrillMode = false,
                drillCardIndex = 0,
                drillTotalCards = 0,
                sessionState = SessionState.PAUSED,
                currentIndex = 0,
                currentCard = null,
                subLessonFinishedToken = it.subLessonFinishedToken + 1
            )
        }
        buildSessionCards()
        refreshFlowerStates()
        saveProgress()
    }

    fun exitDrillMode() {
        val state = _uiState.value
        if (!state.isDrillMode) return
        pauseTimer()
        val lessonId = state.selectedLessonId
        if (lessonId != null && state.drillCardIndex > 0) {
            drillProgressStore.saveDrillProgress(lessonId, state.drillCardIndex)
        }
        _uiState.update {
            it.copy(
                isDrillMode = false,
                drillCardIndex = 0,
                drillTotalCards = 0,
                sessionState = SessionState.PAUSED,
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                voicePromptStartMs = null,
                badSentenceCount = _uiState.value.activePackId?.let { badSentenceStore.getBadSentenceCount(it) } ?: 0
            )
        }
        buildSessionCards()
        refreshFlowerStates()
        saveProgress()
    }

    // ── End Drill Mode ───────────────────────────────────────────────────

    fun openStory(phase: StoryPhase) {
        val lessonId = _uiState.value.selectedLessonId ?: return
        val languageId = _uiState.value.selectedLanguageId
        val story = lessonStore.getStoryQuizzes(lessonId, phase, languageId).firstOrNull()
        if (story == null) {
            _uiState.update { it.copy(storyErrorMessage = "Story not found. Please import the pack again.") }
            return
        }
        _uiState.update { it.copy(activeStory = story, storyErrorMessage = null) }
    }

    fun hasVocabProgress(): Boolean {
        val lessonId = _uiState.value.selectedLessonId ?: return false
        val languageId = _uiState.value.selectedLanguageId
        val progress = vocabProgressStore.get(lessonId, languageId)
        return progress.completedIndices.isNotEmpty()
    }

    fun openVocabSprint(resume: Boolean = false) {
        val lessonId = _uiState.value.selectedLessonId ?: return
        val languageId = _uiState.value.selectedLanguageId
        val allEntries = lessonStore.getVocabEntries(lessonId, languageId)

        // Sort using SRS prioritization: overdue first, then new, then not due
        val sorted = vocabProgressStore.sortEntriesForSprint(allEntries, lessonId, languageId)

        val limit = _uiState.value.vocabSprintLimit
        val limited = if (limit <= 0 || limit >= sorted.size) sorted else sorted.take(limit)

        if (limited.isEmpty()) {
            vocabSession = emptyList()
            _uiState.update { it.copy(vocabErrorMessage = "Vocabulary not found. Please import the pack again.") }
            return
        }

        val startIndex: Int
        val sessionEntries: List<VocabEntry>

        if (resume) {
            val progress = vocabProgressStore.get(lessonId, languageId)
            // Filter out already-completed entries
            val remaining = limited.filterIndexed { index, _ -> index !in progress.completedIndices }
            if (remaining.isEmpty()) {
                // All completed - start fresh
                vocabProgressStore.clearSprintProgress(lessonId, languageId)
                sessionEntries = limited
                startIndex = 0
            } else {
                sessionEntries = remaining
                startIndex = 0
            }
        } else {
            vocabProgressStore.clearSprintProgress(lessonId, languageId)
            sessionEntries = limited
            startIndex = 0
        }

        vocabSession = sessionEntries
        val firstEntry = sessionEntries.firstOrNull()
        val vocabWordBank = firstEntry?.let { buildVocabWordBank(it, sessionEntries) }.orEmpty()
        Log.d(logTag, "openVocabSprint: allEntries=${allEntries.size}, limited=${limited.size}, session=${sessionEntries.size}, resume=$resume, wordBank=${vocabWordBank.size}")
        _uiState.update {
            it.copy(
                currentVocab = firstEntry,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = startIndex,
                vocabTotal = sessionEntries.size,
                vocabWordBankWords = vocabWordBank,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null
            )
        }
    }

    fun completeStory(phase: StoryPhase, allCorrect: Boolean) {
        val shouldPersist = allCorrect || _uiState.value.testMode
        _uiState.update {
            if (!allCorrect && !it.testMode) {
                return@update it.copy(activeStory = null)
            }
            when (phase) {
                StoryPhase.CHECK_IN -> it.copy(storyCheckInDone = true, activeStory = null)
                StoryPhase.CHECK_OUT -> it.copy(storyCheckOutDone = true, activeStory = null)
            }
        }
        if (shouldPersist) {
            forceBackupOnSave = true
            saveProgress()
        }
    }

    fun clearStoryError() {
        _uiState.update { it.copy(storyErrorMessage = null) }
    }

    fun clearVocabError() {
        _uiState.update { it.copy(vocabErrorMessage = null) }
    }

    fun onVocabInputChanged(text: String) {
        _uiState.update {
            val resetAttempts = it.vocabAnswerText != null || it.vocabAttempts >= 3
            it.copy(
                vocabInputText = text,
                vocabAttempts = if (resetAttempts) 0 else it.vocabAttempts,
                vocabAnswerText = if (resetAttempts) null else it.vocabAnswerText
            )
        }
    }

    fun setVocabInputMode(mode: InputMode) {
        Log.d(logTag, "setVocabInputMode: $mode")
        _uiState.update { it.copy(vocabInputMode = mode) }
        if (mode == InputMode.WORD_BANK) {
            updateVocabWordBank()
            Log.d(logTag, "Word bank updated. Words: ${_uiState.value.vocabWordBankWords.size}")
        }
    }

    fun requestVocabVoice() {
        _uiState.update {
            it.copy(
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = it.vocabVoiceTriggerToken + 1
            )
        }
    }

    fun submitVocabAnswer(inputOverride: String? = null) {
        val state = _uiState.value
        val entry = state.currentVocab ?: return
        val input = inputOverride ?: state.vocabInputText
        if (input.isBlank() && !state.testMode) return
        val normalizedInput = Normalizer.normalize(input)
        val accepted = state.testMode || entry.targetText.split("+")
            .map { Normalizer.normalize(it) }
            .any { it == normalizedInput }
        if (accepted) {
            playSuccessTone()
            // Save progress: record correct answer and completed index
            val lessonId = state.selectedLessonId ?: return
            vocabProgressStore.recordCorrect(entry.id, lessonId, state.selectedLanguageId)
            vocabProgressStore.addCompletedIndex(lessonId, state.selectedLanguageId, state.vocabIndex)
            moveToNextVocab()
            return
        }
        playErrorTone()
        // Record incorrect answer for SRS tracking
        val lessonId = state.selectedLessonId ?: return
        vocabProgressStore.recordIncorrect(entry.id, lessonId, state.selectedLanguageId)
        val nextAttempts = state.vocabAttempts + 1
        if (nextAttempts >= 3) {
            _uiState.update {
                it.copy(
                    vocabAttempts = nextAttempts,
                    vocabAnswerText = entry.targetText,
                    vocabInputText = ""
                )
            }
        } else {
            _uiState.update {
                val nextToken = if (state.vocabInputMode == InputMode.VOICE) {
                    it.vocabVoiceTriggerToken + 1
                } else {
                    it.vocabVoiceTriggerToken
                }
                it.copy(
                    vocabAttempts = nextAttempts,
                    vocabInputText = "",
                    vocabVoiceTriggerToken = nextToken
                )
            }
        }
    }

    fun showVocabAnswer() {
        val entry = _uiState.value.currentVocab ?: return
        _uiState.update {
            it.copy(
                vocabAnswerText = entry.targetText,
                vocabInputText = "",
                vocabAttempts = 3
            )
        }
    }

    private fun moveToNextVocab() {
        val state = _uiState.value
        val nextIndex = state.vocabIndex + 1
        if (nextIndex >= vocabSession.size) {
            // All vocab entries completed - clear sprint progress
            val lessonId = state.selectedLessonId
            if (lessonId != null) {
                vocabProgressStore.clearSprintProgress(lessonId, state.selectedLanguageId)
            }
            _uiState.update {
                it.copy(
                    currentVocab = null,
                    vocabInputText = "",
                    vocabAttempts = 0,
                    vocabAnswerText = null,
                    vocabIndex = nextIndex,
                    vocabTotal = vocabSession.size,
                    vocabWordBankWords = emptyList(),
                    vocabFinishedToken = it.vocabFinishedToken + 1
                )
            }
            forceBackupOnSave = true
            saveProgress()
            return
        }
        val next = vocabSession[nextIndex]
        val vocabWordBank = buildVocabWordBank(next, vocabSession)
        _uiState.update {
            it.copy(
                currentVocab = next,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = nextIndex,
                vocabTotal = vocabSession.size,
                vocabWordBankWords = vocabWordBank
            )
        }
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
        pauseTimer()
        val state = _uiState.value
        // Boss unlock guard: require at least 15 completed sub-lessons (unless test mode or elite)
        if (type != BossType.ELITE && state.completedSubLessonCount < 15 && !state.testMode) {
            _uiState.update { it.copy(bossErrorMessage = "Complete at least 15 exercises first") }
            return
        }
        val lessons = state.lessons
        val selectedId = state.selectedLessonId
        if (type != BossType.ELITE && selectedId == null) {
            _uiState.update { it.copy(bossErrorMessage = "Lesson not selected") }
            return
        }
        val selectedIndex = lessons.indexOfFirst { it.id == selectedId }
        val maxBossCards = 300
        val cards = when (type) {
            BossType.LESSON -> {
                val lessonCards = lessons.firstOrNull { it.id == selectedId }?.cards ?: emptyList()
                lessonCards.shuffled().take(maxBossCards)
            }
            BossType.MEGA -> {
                if (selectedIndex <= 0) emptyList()
                else lessons.take(selectedIndex).flatMap { it.cards }.shuffled().take(maxBossCards)
            }
            BossType.ELITE -> {
                val eliteSize = eliteSubLessonSize() * eliteStepCount
                lessons.flatMap { it.cards }.shuffled().take(eliteSize)
            }
        }
        if (cards.isEmpty()) {
            val message = if (type == BossType.MEGA) {
                "Mega boss is available after the first lesson"
            } else if (type == BossType.ELITE) {
                "Elite boss has no cards"
            } else {
                "Boss has no cards"
            }
            _uiState.update { it.copy(bossErrorMessage = message) }
            return
        }
        bossCards = cards
        sessionCards = bossCards
        subLessonTotal = bossCards.size
        subLessonCount = 1
        val firstCard = bossCards.firstOrNull()
        _uiState.update {
            it.copy(
                bossActive = true,
                bossType = type,
                bossTotal = bossCards.size,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossErrorMessage = null,
                currentIndex = 0,
                currentCard = firstCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                correctCount = 0,
                incorrectCount = 0,
                activeTimeMs = 0L,
                voiceActiveMs = 0L,
                voiceWordCount = 0,
                hintCount = 0,
                voicePromptStartMs = null,
                sessionState = SessionState.PAUSED,
                subLessonTotal = bossCards.size,
                subLessonCount = 1,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0
            )
        }
    }

    fun finishBoss() {
        pauseTimer()
        val state = _uiState.value
        val reward = resolveBossReward(state.bossProgress, state.bossTotal)
        val progress = progressStore.load()
        val restoredLessonId = progress.lessonId ?: state.selectedLessonId
        val updatedLessonRewards = if (state.bossType == BossType.LESSON) {
            val lessonId = state.selectedLessonId
            if (lessonId != null && reward != null) {
                state.bossLessonRewards + (lessonId to reward)
            } else {
                state.bossLessonRewards
            }
        } else {
            state.bossLessonRewards
        }
        val updatedMegaRewards = if (state.bossType == BossType.MEGA && reward != null) {
            val lessonId = state.selectedLessonId
            if (lessonId != null) {
                state.bossMegaRewards + (lessonId to reward)
            } else {
                state.bossMegaRewards
            }
        } else {
            state.bossMegaRewards
        }
        bossCards = emptyList()
        _uiState.update {
            it.copy(
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = reward ?: it.bossReward,
                bossRewardMessage = it.bossRewardMessage,
                bossFinishedToken = it.bossFinishedToken + 1,
                bossLastType = state.bossType,
                bossErrorMessage = null,
                bossLessonRewards = updatedLessonRewards,
                bossMegaRewards = updatedMegaRewards,
                selectedLessonId = restoredLessonId,
                mode = progress.mode,
                currentIndex = progress.currentIndex,
                correctCount = progress.correctCount,
                incorrectCount = progress.incorrectCount,
                incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                activeTimeMs = progress.activeTimeMs,
                voiceActiveMs = progress.voiceActiveMs,
                voiceWordCount = progress.voiceWordCount,
                hintCount = progress.hintCount,
                voicePromptStartMs = null,
                inputText = "",
                lastResult = null,
                answerText = null,
                sessionState = SessionState.PAUSED
            )
        }
        buildSessionCards()
        saveProgress()
        refreshFlowerStates()
    }

    fun clearBossRewardMessage() {
        val state = _uiState.value
        val shouldResume = state.bossActive &&
            state.sessionState == SessionState.PAUSED &&
            state.currentCard != null
        if (shouldResume) {
            resumeTimer()
        }
        _uiState.update {
            val trigger = if (shouldResume && it.inputMode == InputMode.VOICE) {
                it.voiceTriggerToken + 1
            } else {
                it.voiceTriggerToken
            }
            it.copy(
                bossRewardMessage = null,
                sessionState = if (shouldResume) SessionState.ACTIVE else it.sessionState,
                voiceTriggerToken = trigger,
                inputText = if (shouldResume) "" else it.inputText
            )
        }
    }

    fun clearBossError() {
        _uiState.update { it.copy(bossErrorMessage = null) }
    }

    private fun updateBossProgress(progress: Int) {
        val state = _uiState.value
        val nextProgress = progress.coerceAtMost(state.bossTotal)
        val nextReward = resolveBossReward(nextProgress, state.bossTotal)
        val isNewReward = nextReward != null && nextReward != state.bossReward
        val message = if (isNewReward) {
            bossRewardMessage(nextReward!!)
        } else {
            state.bossRewardMessage
        }
        if (isNewReward) {
            pauseTimer()
        }
        _uiState.update {
            it.copy(
                bossProgress = nextProgress,
                bossReward = nextReward ?: it.bossReward,
                bossRewardMessage = message,
                sessionState = if (isNewReward) SessionState.PAUSED else it.sessionState
            )
        }
    }

    private fun resolveBossReward(progress: Int, total: Int): BossReward? {
        if (total <= 0) return null
        val percent = (progress.toDouble() / total.toDouble()) * 100.0
        return when {
            percent >= 100.0 -> BossReward.GOLD
            percent > 75.0 -> BossReward.SILVER
            percent > 50.0 -> BossReward.BRONZE
            else -> null
        }
    }

    private fun bossRewardMessage(reward: BossReward): String {
        return when (reward) {
            BossReward.BRONZE -> "Bronze reached"
            BossReward.SILVER -> "Silver reached"
            BossReward.GOLD -> "Gold reached"
        }
    }

    private fun startSession() {
        if (!_uiState.value.bossActive && !_uiState.value.eliteActive) {
            buildSessionCards()
        }
        if (sessionCards.isEmpty() || _uiState.value.currentCard == null) {
            pauseTimer()
            _uiState.update { it.copy(sessionState = SessionState.PAUSED) }
            saveProgress()
            return
        }
        pauseTimer()
        resumeTimer()
        _uiState.update {
            val trigger = if (it.inputMode == InputMode.VOICE) it.voiceTriggerToken + 1 else it.voiceTriggerToken
            it.copy(
                sessionState = SessionState.ACTIVE,
                inputText = "",
                voiceTriggerToken = trigger,
                voicePromptStartMs = null
            )
        }
        currentCard()?.let { recordCardShowForMastery(it) }
        saveProgress()
    }

    private fun currentCard(): SentenceCard? {
        if (sessionCards.isEmpty()) return null
        val index = _uiState.value.currentIndex.coerceIn(0, sessionCards.lastIndex)
        return sessionCards.getOrNull(index)
    }

    private fun resumeTimer() {
        if (timerJob?.isActive == true) return
        activeStartMs = SystemClock.elapsedRealtime()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val start = activeStartMs ?: continue
                val elapsed = SystemClock.elapsedRealtime() - start
                _uiState.update { it.copy(activeTimeMs = it.activeTimeMs + elapsed) }
                activeStartMs = SystemClock.elapsedRealtime()
                saveProgress()
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        activeStartMs = null
    }

    private fun saveProgress() {
        val state = _uiState.value
        if (state.bossActive && state.bossType != BossType.ELITE) return
        progressStore.save(
            TrainingProgress(
                languageId = state.selectedLanguageId,
                mode = state.mode,
                lessonId = state.selectedLessonId,
                currentIndex = state.currentIndex,
                correctCount = state.correctCount,
                incorrectCount = state.incorrectCount,
                incorrectAttemptsForCard = state.incorrectAttemptsForCard,
                activeTimeMs = state.activeTimeMs,
                state = state.sessionState,
                bossLessonRewards = state.bossLessonRewards.mapValues { it.value.name },
                bossMegaReward = null,
                bossMegaRewards = state.bossMegaRewards.mapValues { it.value.name },
                voiceActiveMs = state.voiceActiveMs,
                voiceWordCount = state.voiceWordCount,
                hintCount = state.hintCount,
                eliteStepIndex = state.eliteStepIndex,
                eliteBestSpeeds = normalizeEliteSpeeds(state.eliteBestSpeeds),
                currentScreen = state.currentScreen,
                activePackId = state.activePackId,
                dailyLevel = state.dailySession.level,
                dailyTaskIndex = state.dailySession.taskIndex
            )
        )

        if (forceBackupOnSave) {
            forceBackupOnSave = false
            createProgressBackup()
        }
    }

    private fun resolveEliteUnlocked(lessons: List<Lesson>, testMode: Boolean): Boolean {
        return testMode || lessons.size >= 12
    }

    private fun normalizeEliteSpeeds(speeds: List<Double>): List<Double> {
        return if (speeds.size >= eliteStepCount) {
            speeds.take(eliteStepCount)
        } else {
            speeds + List(eliteStepCount - speeds.size) { 0.0 }
        }
    }

    private fun eliteSubLessonSize(): Int {
        return kotlin.math.ceil(subLessonSize * eliteSizeMultiplier).toInt()
    }

    private fun calculateSpeedPerMinute(activeMs: Long, words: Int): Double {
        val minutes = activeMs / 60000.0
        if (minutes <= 0.0) return 0.0
        return words / minutes
    }

    private fun buildEliteCards(): List<SentenceCard> {
        val cards = _uiState.value.lessons.flatMap { it.cards }
        if (cards.isEmpty()) return emptyList()
        return cards.shuffled().take(eliteSubLessonSize())
    }

    private fun countMetricWords(text: String): Int {
        val normalized = Normalizer.normalize(text)
        if (normalized.isBlank()) return 0
        return normalized.split(" ").count { it.length >= 3 }
    }

    fun onTtsSpeak(text: String, speed: Float? = null) {
        if (text.isBlank()) return
        val langId = _uiState.value.selectedLanguageId
        val effectiveSpeed = speed ?: _uiState.value.ttsSpeed
        viewModelScope.launch {
            if (ttsEngine.state.value != TtsState.READY
                || ttsEngine.activeLanguageId != langId) {
                ttsEngine.initialize(langId)
            }
            if (ttsEngine.state.value == TtsState.READY) {
                ttsEngine.speak(text, languageId = langId, speed = effectiveSpeed)
            } else {
                Log.w(logTag, "TTS not ready after initialize, state=${ttsEngine.state.value}")
            }
        }
    }

    fun setTtsSpeed(speed: Float) {
        _uiState.update { it.copy(ttsSpeed = speed.coerceIn(0.5f, 1.5f)) }
    }

    fun setRuTextScale(scale: Float) {
        _uiState.update { it.copy(ruTextScale = scale.coerceIn(1.0f, 2.0f)) }
    }

    fun startTtsDownload() {
        // M6: Check metered network before starting download
        if (ttsModelManager.isNetworkMetered()) {
            _uiState.update { it.copy(ttsMeteredNetwork = true) }
            return
        }
        beginTtsDownload()
    }

    fun confirmTtsDownloadOnMetered() {
        _uiState.update { it.copy(ttsMeteredNetwork = false) }
        beginTtsDownload()
    }

    fun dismissMeteredWarning() {
        _uiState.update { it.copy(ttsMeteredNetwork = false) }
    }

    /**
     * Dismiss the TTS download dialog and reset download state to Idle.
     * Called when the user closes the dialog after Done or Error states.
     */
    fun dismissTtsDownloadDialog() {
        val state = _uiState.value.ttsDownloadState
        // Only reset to Idle if in a terminal state (Done or Error)
        if (state is DownloadState.Done || state is DownloadState.Error) {
            _uiState.update { it.copy(ttsDownloadState = DownloadState.Idle) }
        }
    }

    private var ttsDownloadJob: Job? = null

    private fun beginTtsDownload() {
        if (ttsDownloadJob?.isActive == true) {
            Log.d(logTag, "TTS download already in progress, ignoring duplicate request")
            return
        }
        val langId = _uiState.value.selectedLanguageId
        if (ttsModelManager.isModelReady(langId)) {
            _uiState.update { it.copy(ttsModelReady = true, ttsDownloadState = DownloadState.Done) }
            return
        }
        ttsDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            ttsModelManager.download(langId).collect { downloadState ->
                _uiState.update { it.copy(ttsDownloadState = downloadState) }
                if (downloadState is DownloadState.Done) {
                    _uiState.update { it.copy(ttsModelReady = true) }
                }
            }
        }
    }

    private fun checkTtsModel() {
        val langId = _uiState.value.selectedLanguageId
        val ready = ttsModelManager.isModelReady(langId)
        _uiState.update { it.copy(ttsModelReady = ready) }
    }

    private fun checkAllTtsModels() {
        val readyMap = TtsModelRegistry.models.keys.associateWith { langId ->
            ttsModelManager.isModelReady(langId)
        }
        _uiState.update { it.copy(ttsModelsReady = readyMap) }
    }

    fun startTtsDownloadForLanguage(languageId: String) {
        if (ttsModelManager.isModelReady(languageId)) {
            _uiState.update {
                it.copy(
                    ttsModelsReady = it.ttsModelsReady + (languageId to true),
                    bgTtsDownloadStates = it.bgTtsDownloadStates + (languageId to DownloadState.Done)
                )
            }
            return
        }
        if (ttsDownloadJob?.isActive == true) {
            Log.d(logTag, "TTS download already in progress, ignoring request for $languageId")
            return
        }
        ttsDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            ttsModelManager.download(languageId).collect { downloadState ->
                _uiState.update { current ->
                    val updatedBgStates = current.bgTtsDownloadStates + (languageId to downloadState)
                    val updatedReady = current.ttsModelsReady + (languageId to (downloadState is DownloadState.Done))
                    val downloadStateOverride = if (languageId == current.selectedLanguageId
                        && downloadState !is DownloadState.Idle
                        && current.ttsDownloadState !is DownloadState.Done) {
                        downloadState
                    } else {
                        current.ttsDownloadState
                    }
                    current.copy(
                        bgTtsDownloadStates = updatedBgStates,
                        ttsModelsReady = updatedReady,
                        ttsDownloadState = downloadStateOverride,
                        ttsModelReady = if (languageId == current.selectedLanguageId && downloadState is DownloadState.Done) true else current.ttsModelReady
                    )
                }
            }
        }
    }

    fun stopTts() {
        ttsEngine.stop()
    }

    // ── ASR (offline speech recognition) ────────────────────────────────

    private var asrDownloadJob: Job? = null

    fun checkAsrModel() {
        val ready = asrModelManager.isReady()
        _uiState.update { it.copy(asrModelReady = ready) }
    }

    fun dismissAsrDownloadDialog() {
        _uiState.update { it.copy(asrDownloadState = DownloadState.Idle) }
    }

    fun startOfflineRecognition() {
        viewModelScope.launch {
            try {
                val result = transcribeWithOfflineAsr()
                if (result.isNotBlank()) {
                    onInputChanged(result)
                    // Don't auto-submit — let user review recognized text and submit manually
                }
            } catch (e: Exception) {
                Log.e(logTag, "Offline recognition failed", e)
            }
        }
    }

    fun stopAsr() {
        asrEngine?.stopRecording()
    }

    fun setUseOfflineAsr(enabled: Boolean) {
        _uiState.update { it.copy(useOfflineAsr = enabled) }
        val config = configStore.load()
        configStore.save(config.copy(useOfflineAsr = enabled))
        if (enabled) {
            checkAsrModel()
        } else {
            asrEngine?.release()
            _uiState.update { it.copy(asrState = AsrState.IDLE, asrModelReady = false, asrErrorMessage = null) }
        }
    }

    fun startAsrDownload() {
        if (asrModelManager.isNetworkMetered()) {
            _uiState.update { it.copy(asrMeteredNetwork = true) }
            return
        }
        beginAsrDownload()
    }

    fun confirmAsrDownloadOnMetered() {
        _uiState.update { it.copy(asrMeteredNetwork = false) }
        beginAsrDownload()
    }

    fun dismissAsrMeteredWarning() {
        _uiState.update { it.copy(asrMeteredNetwork = false) }
    }

    private fun beginAsrDownload() {
        if (asrDownloadJob?.isActive == true) return
        asrDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            // Download VAD first (small ~2MB)
            if (!asrModelManager.isVadReady()) {
                asrModelManager.downloadVad().collect { state ->
                    _uiState.update { it.copy(asrDownloadState = state) }
                }
            }
            // Then ASR model
            if (!asrModelManager.isAsrReady()) {
                asrModelManager.downloadAsr().collect { state ->
                    _uiState.update { it.copy(asrDownloadState = state) }
                    if (state is DownloadState.Done) {
                        _uiState.update { it.copy(asrModelReady = true) }
                    }
                }
            }
        }
    }

    /**
     * Initialize ASR engine and record + transcribe audio.
     * Returns the recognized text.
     */
    private suspend fun transcribeWithOfflineAsr(): String {
        val engine = asrEngine
        if (engine == null) {
            _uiState.update {
                it.copy(
                    asrState = AsrState.ERROR,
                    asrErrorMessage = "ASR engine unavailable on this device"
                )
            }
            return ""
        }
        if (!engine.isReady) {
            engine.initialize(_uiState.value.selectedLanguageId)
        }

        // Check if initialization failed
        if (engine.state.value == AsrState.ERROR) {
            _uiState.update {
                it.copy(
                    asrState = AsrState.ERROR,
                    asrErrorMessage = engine.errorMessage ?: "ASR initialization failed"
                )
            }
            return ""
        }

        _uiState.update { it.copy(asrState = engine.state.value, asrErrorMessage = null) }

        // Collect state updates from ASR engine
        val stateJob = viewModelScope.launch {
            engine.state.collect { asrState ->
                _uiState.update { it.copy(asrState = asrState) }
            }
        }

        val result = engine.recordAndTranscribe()
        stateJob.cancel()

        // Check for errors after recording/transcription
        val finalState = engine.state.value
        val errorMsg = engine.errorMessage
        _uiState.update {
            it.copy(
                asrState = finalState,
                asrErrorMessage = if (result.isBlank() && errorMsg != null) errorMsg
                    else if (result.isBlank() && finalState == AsrState.ERROR) "ASR recognition failed"
                    else null
            )
        }
        return result
    }

    // ── End ASR ─────────────────────────────────────────────────────────

    private var bgDownloadJob: Job? = null

    private fun startBackgroundTtsDownload() {
        val languages = _uiState.value.languages
        if (languages.isEmpty()) return

        val missingLanguages = languages.map { it.id }
            .filter { !ttsModelManager.isModelReady(it) }

        if (missingLanguages.isEmpty()) return
        if (bgDownloadJob?.isActive == true) return

        bgDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            ttsModelManager.downloadMultiple(missingLanguages).collect { stateMap ->
                val allDone = stateMap.values.all { it is DownloadState.Done }
                val anyActive = stateMap.values.any {
                    it is DownloadState.Downloading || it is DownloadState.Extracting
                }

                _uiState.update { current ->
                    val selectedBgState = stateMap[current.selectedLanguageId]
                    // Mirror bg download state to ttsDownloadState so the dialog
                    // shows live progress if the user opened it while bg download runs
                    val downloadStateOverride = if (selectedBgState != null
                        && selectedBgState !is DownloadState.Idle
                        && current.ttsDownloadState !is DownloadState.Done) {
                        selectedBgState
                    } else {
                        current.ttsDownloadState
                    }
                    val updatedReady = current.ttsModelsReady.toMutableMap().apply {
                        stateMap.forEach { (langId, dlState) ->
                            if (dlState is DownloadState.Done) {
                                this[langId] = true
                            }
                        }
                    }
                    current.copy(
                        bgTtsDownloadStates = stateMap,
                        bgTtsDownloading = anyActive,
                        ttsModelReady = ttsModelManager.isModelReady(current.selectedLanguageId),
                        ttsDownloadState = downloadStateOverride,
                        ttsModelsReady = updatedReady
                    )
                }

                if (allDone) {
                    _uiState.update { it.copy(bgTtsDownloading = false) }
                }
            }
        }
    }

    fun setTtsDownloadStateFromBackground(bgState: DownloadState) {
        _uiState.update { it.copy(ttsDownloadState = bgState) }
    }

    override fun onCleared() {
        saveProgress()
        bgDownloadJob?.cancel()
        ttsEngine.release()
        asrEngine?.release()
        soundPool.release()
        super.onCleared()
    }

    private fun playSuccessTone() {
        if (loadedSounds.contains(successSoundId)) {
            soundPool.play(successSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    private fun playErrorTone() {
        if (loadedSounds.contains(errorSoundId)) {
            soundPool.play(errorSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    /**
     * Определить к какому уроку принадлежит карточка.
     * Важно для Mixed-режима где карточки могут быть из разных уроков.
     */
    private fun resolveCardLessonId(card: SentenceCard): String {
        val selectedId = _uiState.value.selectedLessonId
        // Prefer the currently selected lesson if it contains this card
        if (selectedId != null) {
            val selectedLesson = _uiState.value.lessons.find { it.id == selectedId }
            if (selectedLesson != null && selectedLesson.cards.any { it.id == card.id }) {
                return selectedId
            }
        }
        return _uiState.value.lessons
            .find { lesson -> lesson.cards.any { it.id == card.id } }
            ?.id
            ?: selectedId
            ?: "unknown"
    }

    /**
     * Записать показ карточки для отслеживания прогресса освоения.
     * Word Bank НЕ учитывается для формирования навыка (роста цветка).
     * Учитывается только голосовой ввод и клавиатура.
     */
    private fun recordCardShowForMastery(card: SentenceCard) {
        // Drill mode: pure card training, no mastery/flower progress
        if (_uiState.value.isDrillMode) return

        val lessonId = resolveCardLessonId(card)
        val languageId = _uiState.value.selectedLanguageId

        // Word Bank mode: does NOT count for mastery (flower growth)
        // Only voice and keyboard input count for skill formation
        val isWordBankMode = _uiState.value.inputMode == InputMode.WORD_BANK
        if (isWordBankMode) {
            Log.d(logTag, "Skipping card show record for Word Bank mode - does not count for mastery")
            return
        }

        Log.d(logTag, "Recording card show: lessonId=$lessonId, cardId=${card.id}, mode=${_uiState.value.inputMode}")
        masteryStore.recordCardShow(lessonId, languageId, card.id)
        val mastery = masteryStore.get(lessonId, languageId)
        Log.d(logTag, "After record: uniqueCardShows=${mastery?.uniqueCardShows}, totalShows=${mastery?.totalCardShows}")
    }

    private fun markSubLessonCardsShown(cards: List<SentenceCard>) {
        if (_uiState.value.inputMode != InputMode.WORD_BANK || cards.isEmpty()) return
        val lessonId = _uiState.value.selectedLessonId ?: return
        val lessonCardIds = _uiState.value.lessons
            .firstOrNull { it.id == lessonId }
            ?.cards
            ?.map { it.id }
            ?.toSet()
            ?: return
        val cardIds = cards.map { it.id }.filter { lessonCardIds.contains(it) }
        masteryStore.markCardsShownForProgress(lessonId, _uiState.value.selectedLanguageId, cardIds)
    }

    /**
     * Проверить и отметить урок как завершённый если все под-уроки пройдены.
     */
    private fun checkAndMarkLessonCompleted() {
        val state = _uiState.value
        // Mark lesson as completed after first 15 sublessons (but allow continuing)
        val completedFirstCycle = state.completedSubLessonCount >= 15
        if (completedFirstCycle && state.selectedLessonId != null) {
            masteryStore.markLessonCompleted(state.selectedLessonId, state.selectedLanguageId)
        }
    }

    /**
     * Вычислить количество завершённых под-уроков на основе показанных карточек.
     */
    private fun calculateCompletedSubLessons(
        subLessons: List<ScheduledSubLesson>,
        mastery: LessonMasteryState?,
        lessonId: String?
    ): Int {
        if (lessonId == null || mastery == null || mastery.shownCardIds.isEmpty()) return 0

        val lessonCardIds = _uiState.value.lessons
            .firstOrNull { it.id == lessonId }
            ?.cards
            ?.map { it.id }
            ?.toSet()
            ?: return 0

        var completed = 0
        for (subLesson in subLessons) {
            val allCardsShown = subLesson.cards.all { card ->
                !lessonCardIds.contains(card.id) || mastery.shownCardIds.contains(card.id)
            }
            if (allCardsShown) {
                completed++
            } else {
                // Stop at first incomplete sub-lesson.
                break
            }
        }
        return completed
    }
    /**
     * Генерирует word bank из правильного ответа
     */
    private fun generateWordBank(correctAnswer: String, extraWords: List<String> = emptyList()): List<String> {
        val words = correctAnswer.split(" ").filter { it.isNotBlank() }
        val extras = extraWords.filter { it.isNotBlank() && !words.contains(it) }
        return (words + extras).shuffled()
    }

    /**
     * Обновляет word bank для текущей карточки
     */
    private fun updateWordBank() {
        val card = _uiState.value.currentCard
        if (card == null) {
            _uiState.update {
                it.copy(
                    wordBankWords = emptyList(),
                    selectedWords = emptyList()
                )
            }
            return
        }

        val correctAnswer = card.acceptedAnswers.firstOrNull() ?: ""
        val correctWords = correctAnswer.split(" ").map { it.trim() }.filter { it.isNotBlank() }
        val normalizedCorrect = correctWords.map { Normalizer.normalize(it) }.toSet()
        val distractorPool = _uiState.value.lessons
            .flatMap { it.cards }
            .filter { it.id != card.id }
            .flatMap { it.acceptedAnswers }
            .flatMap { it.split(" ") }
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filter { Normalizer.normalize(it) !in normalizedCorrect }
            .distinct()
        val extraWords = distractorPool.shuffled().take(3)
        val wordBank = generateWordBank(correctAnswer, extraWords)

        _uiState.update {
            it.copy(
                wordBankWords = wordBank,
                selectedWords = emptyList(),
                inputText = ""
            )
        }
    }

    private fun buildVocabWordBank(entry: VocabEntry, pool: List<VocabEntry>): List<String> {
        val correctOption = entry.targetText.split("+")
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: entry.targetText
        val normalizedCorrect = Normalizer.normalize(correctOption)

        // Собираем все возможные варианты из пула словаря
        val poolOptions = pool
            .flatMap { it.targetText.split("+") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Убираем правильный ответ из списка вариантов словаря
        val poolDistractors = poolOptions
            .filter { Normalizer.normalize(it) != normalizedCorrect }
            .shuffled()

        // Начинаем с дистракторов из словаря
        val distractors = poolDistractors.take(4).toMutableList()

        // Если дистракторов недостаточно, добираем из всех уроков
        if (distractors.size < 4) {
            val languageId = _uiState.value.selectedLanguageId
            val allVocabFromLessons = _uiState.value.lessons
                .flatMap { lesson ->
                    lessonStore.getVocabEntries(lesson.id, languageId)
                }
                .flatMap { it.targetText.split("+") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .filter { Normalizer.normalize(it) != normalizedCorrect }
                .filter { !distractors.contains(it) }
                .shuffled()

            val additionalNeeded = 4 - distractors.size
            distractors.addAll(allVocabFromLessons.take(additionalNeeded))
        }

        // Берём до 4 дистракторов + правильный ответ = до 5 вариантов
        val selectedDistractors = distractors.take(4)
        val result = (listOf(correctOption) + selectedDistractors).shuffled()

        Log.d(logTag, "buildVocabWordBank: entry=${entry.nativeText}, correct=$correctOption, pool=${pool.size}, poolDistractors=${poolDistractors.size}, finalDistractors=${selectedDistractors.size}, result=${result.size}, words=$result")
        return result
    }

    private fun updateVocabWordBank() {
        val entry = _uiState.value.currentVocab ?: return
        val options = buildVocabWordBank(entry, vocabSession)
        _uiState.update { it.copy(vocabWordBankWords = options) }
    }

    /**
     * Добавляет слово в выбранные для word bank режима
     */
    fun selectWordFromBank(word: String) {
        val currentSelected = _uiState.value.selectedWords
        val newSelected = currentSelected + word
        val inputText = newSelected.joinToString(" ")

        _uiState.update {
            it.copy(
                selectedWords = newSelected,
                inputText = inputText
            )
        }
    }

    /**
     * Удаляет последнее выбранное слово
     */
    fun removeLastSelectedWord() {
        val currentSelected = _uiState.value.selectedWords
        if (currentSelected.isEmpty()) return

        val newSelected = currentSelected.dropLast(1)
        val inputText = newSelected.joinToString(" ")

        _uiState.update {
            it.copy(
                selectedWords = newSelected,
                inputText = inputText
            )
        }
    }

    /**
     * Обновить состояния цветков для всех уроков.
     */
    private fun refreshFlowerStates() {
        val languageId = _uiState.value.selectedLanguageId
        val lessons = _uiState.value.lessons
        val nowMs = System.currentTimeMillis()

        val flowerStates = lessons.associate { lesson ->
            val mastery = masteryStore.get(lesson.id, languageId)
            val flower = FlowerCalculator.calculate(mastery, lesson.cards.size)
            Log.d(logTag, "Flower for lesson ${lesson.id}: mastery=${mastery?.uniqueCardShows ?: 0}, state=${flower.state}, scale=${flower.scaleMultiplier}")
            lesson.id to flower
        }

        val currentLessonId = _uiState.value.selectedLessonId
        val currentFlower = currentLessonId?.let { flowerStates[it] }
        val currentShownCount = currentLessonId?.let { lessonId ->
            masteryStore.get(lessonId, languageId)?.shownCardIds?.size ?: 0
        } ?: 0
        val ladderRows = lessons.mapIndexed { index, lesson ->
            val mastery = masteryStore.get(lesson.id, languageId)
            val metrics = LessonLadderCalculator.calculate(mastery, nowMs)
            LessonLadderRow(
                index = index + 1,
                lessonId = lesson.id,
                title = lesson.title,
                uniqueCardShows = metrics.uniqueCardShows,
                daysSinceLastShow = metrics.daysSinceLastShow,
                intervalLabel = metrics.intervalLabel
            )
        }

        _uiState.update {
            it.copy(
                lessonFlowers = flowerStates,
                currentLessonFlower = currentFlower,
                currentLessonShownCount = currentShownCount,
                ladderRows = ladderRows
            )
        }
    }

    /**
     * Refresh the vocab mastered count from the store.
     * Called when returning from VocabDrill to reflect updated mastery.
     */
    fun refreshVocabMasteryCount() {
        val count = wordMasteryStore.getMasteredCount()
        _uiState.update { it.copy(vocabMasteredCount = count) }
    }

    /**
     * Обновляет streak после завершения подурока
     */
    private fun updateStreak() {
        val languageId = _uiState.value.selectedLanguageId
        val (updatedStreak, isNewStreak) = streakStore.recordSubLessonCompletion(languageId)

        if (isNewStreak && updatedStreak.currentStreak > 0) {
            // Генерируем сообщение о streak
            val message = when {
                updatedStreak.currentStreak == 1 -> "\uD83D\uDD25 Great start! Day 1 streak!"
                updatedStreak.currentStreak == 3 -> "\uD83D\uDD25 3 days streak! You're on fire!"
                updatedStreak.currentStreak == 7 -> "\uD83D\uDD25 7 days streak! One week! Amazing!"
                updatedStreak.currentStreak == 14 -> "\uD83D\uDD25 14 days streak! Two weeks! Incredible!"
                updatedStreak.currentStreak == 30 -> "\uD83D\uDD25 30 days streak! One month! Outstanding!"
                updatedStreak.currentStreak == 100 -> "\uD83D\uDD25 100 days streak! You're a legend!"
                updatedStreak.currentStreak % 10 == 0 -> "\uD83D\uDD25 ${updatedStreak.currentStreak} days streak! Keep it up!"
                else -> "\uD83D\uDD25 ${updatedStreak.currentStreak} days streak!"
            }

            _uiState.update {
                it.copy(
                    currentStreak = updatedStreak.currentStreak,
                    longestStreak = updatedStreak.longestStreak,
                    streakMessage = message,
                    streakCelebrationToken = it.streakCelebrationToken + 1
                )
            }
        } else {
            // Просто обновляем streak без сообщения
            _uiState.update {
                it.copy(
                    currentStreak = updatedStreak.currentStreak,
                    longestStreak = updatedStreak.longestStreak
                )
            }
        }
    }

    /**
     * Закрывает сообщение о streak
     */
    fun dismissStreakMessage() {
        _uiState.update {
            it.copy(streakMessage = null)
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
            it.copy(userName = trimmed)
        }
    }

    fun saveProgressNow() {
        Log.d(logTag, "Manual progress save requested from settings")
        forceBackupOnSave = true
        saveProgress()
    }

    fun onScreenChanged(screenName: String) {
        _uiState.update { it.copy(currentScreen = screenName) }
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
            // Reload all data after restore
            Log.d(logTag, "--- Loading Progress Data ---")
            val progress = progressStore.load()
            Log.d(logTag, "Progress: languageId=${progress.languageId}, lessonId=${progress.lessonId}, mode=${progress.mode}")
            Log.d(logTag, "Progress: currentIndex=${progress.currentIndex}, correctCount=${progress.correctCount}, incorrectCount=${progress.incorrectCount}")
            Log.d(logTag, "Progress: state=${progress.state}, bossRewards=${progress.bossLessonRewards.size}")

            Log.d(logTag, "--- Loading Profile Data ---")
            val profile = profileStore.load()
            Log.d(logTag, "Profile: userName=${profile.userName}")

            val selectedLanguageId = progress.languageId ?: "en"
            Log.d(logTag, "Selected language: $selectedLanguageId")

            Log.d(logTag, "--- Loading Lessons ---")
            val lessons = lessonStore.getLessons(selectedLanguageId)
            Log.d(logTag, "Loaded ${lessons.size} lessons for language $selectedLanguageId")

            val selectedLessonId = progress.lessonId?.let { id ->
            lessons.firstOrNull { it.id == id }?.id
        } ?: lessons.firstOrNull()?.id
            Log.d(logTag, "Selected lesson: $selectedLessonId")

            Log.d(logTag, "--- Loading Streak Data ---")
            val streak = streakStore.getCurrentStreak(selectedLanguageId)
            Log.d(logTag, "Streak: current=${streak.currentStreak}, longest=${streak.longestStreak}, totalCompleted=${streak.totalSubLessonsCompleted}")

            // Parse boss rewards from strings to BossReward enum
            Log.d(logTag, "--- Parsing Boss Rewards ---")
            val bossLessonRewards = progress.bossLessonRewards.mapNotNull { (lessonId, reward) ->
                val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
                Log.d(logTag, "Boss reward: $lessonId -> $parsed")
                lessonId to parsed
            }.toMap()
            val bossMegaRewards = progress.bossMegaRewards.mapNotNull { (lessonId, reward) ->
                val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
                lessonId to parsed
            }.toMap()
            Log.d(logTag, "Boss Mega rewards: $bossMegaRewards")

            Log.d(logTag, "--- Updating UI State ---")
            _uiState.update {
                it.copy(
                    selectedLanguageId = selectedLanguageId,
                    lessons = lessons,
                    selectedLessonId = selectedLessonId,
                    mode = progress.mode,
                    sessionState = progress.state,
                    currentIndex = progress.currentIndex,
                    correctCount = progress.correctCount,
                    incorrectCount = progress.incorrectCount,
                    incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                    activeTimeMs = progress.activeTimeMs,
                    voiceActiveMs = progress.voiceActiveMs,
                    voiceWordCount = progress.voiceWordCount,
                    hintCount = progress.hintCount,
                    currentStreak = streak.currentStreak,
                    longestStreak = streak.longestStreak,
                    bossLessonRewards = bossLessonRewards,
                    bossMegaRewards = bossMegaRewards,
                    userName = profile.userName,
                    eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1),
                    eliteBestSpeeds = normalizeEliteSpeeds(progress.eliteBestSpeeds)
                )
            }
            Log.d(logTag, "UI state updated")

            // Rebuild session and schedules
            Log.d(logTag, "--- Rebuilding Schedules ---")
            rebuildSchedules(lessons)
            Log.d(logTag, "--- Building Session Cards ---")
            buildSessionCards()
            Log.d(logTag, "--- Refreshing Flower States ---")
            refreshFlowerStates()

            Log.d(logTag, "=== Backup Restore Complete ===")
            Log.d(logTag, "Summary: userName=${profile.userName}, lessons=${lessons.size}, lessonId=${selectedLessonId}, streak=${streak.currentStreak}")
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

            val bossLessonRewards = progress.bossLessonRewards.mapNotNull { (lessonId, reward) ->
                val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
                lessonId to parsed
            }.toMap()
            val bossMegaRewards = progress.bossMegaRewards.mapNotNull { (lessonId, reward) ->
                val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
                lessonId to parsed
            }.toMap()
            val normalizedEliteSpeeds = normalizeEliteSpeeds(progress.eliteBestSpeeds)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        languages = languages,
                        installedPacks = packs,
                        selectedLanguageId = selectedLanguageId,
                        lessons = lessons,
                        selectedLessonId = selectedLessonId,
                        mode = progress.mode,
                        sessionState = progress.state,
                        currentIndex = progress.currentIndex,
                        correctCount = progress.correctCount,
                        incorrectCount = progress.incorrectCount,
                        incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                        activeTimeMs = progress.activeTimeMs,
                        voiceActiveMs = progress.voiceActiveMs,
                        voiceWordCount = progress.voiceWordCount,
                        hintCount = progress.hintCount,
                        currentStreak = streak.currentStreak,
                        longestStreak = streak.longestStreak,
                        bossLessonRewards = bossLessonRewards,
                        bossMegaRewards = bossMegaRewards,
                        userName = profile.userName,
                        eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1),
                        eliteBestSpeeds = normalizedEliteSpeeds,
                        eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode)
                    )
                }
                rebuildSchedules(lessons)
                buildSessionCards()
                refreshFlowerStates()
            }
        }
    }

    fun flagBadSentence() {
        val card = _uiState.value.currentCard ?: return
        val state = _uiState.value
        val packId = state.activePackId ?: return
        badSentenceStore.addBadSentence(
            packId = packId,
            cardId = card.id,
            languageId = state.selectedLanguageId,
            sentence = card.promptRu,
            translation = card.acceptedAnswers.joinToString(" / ")
        )
        _uiState.update { it.copy(badSentenceCount = badSentenceStore.getBadSentenceCount(packId)) }
        if (state.isDrillMode) {
            advanceDrillCard()
        }
    }

    fun unflagBadSentence() {
        val card = _uiState.value.currentCard ?: return
        val packId = _uiState.value.activePackId ?: return
        badSentenceStore.removeBadSentence(packId, card.id)
        _uiState.update { it.copy(badSentenceCount = badSentenceStore.getBadSentenceCount(packId)) }
    }

    fun isBadSentence(): Boolean {
        val card = _uiState.value.currentCard ?: return false
        val packId = _uiState.value.activePackId ?: return false
        return badSentenceStore.isBadSentence(packId, card.id)
    }

    fun exportBadSentences(): String? {
        val packId = _uiState.value.activePackId ?: return null
        val entries = badSentenceStore.getBadSentences(packId)
        if (entries.isEmpty()) return null
        val file = badSentenceStore.exportToTextFile(packId)
        return file.absolutePath
    }

    fun hideCurrentCard() {
        val card = _uiState.value.currentCard ?: return
        hiddenCardStore.hideCard(card.id)
        skipToNextCard()
    }

    fun unhideCurrentCard() {
        val card = _uiState.value.currentCard ?: return
        hiddenCardStore.unhideCard(card.id)
    }

    fun isCurrentCardHidden(): Boolean {
        val card = _uiState.value.currentCard ?: return false
        return hiddenCardStore.isHidden(card.id)
    }

    private fun skipToNextCard() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex < sessionCards.size) {
            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    currentCard = sessionCards[nextIndex],
                    inputText = "",
                    lastResult = null,
                    answerText = null,
                    incorrectAttemptsForCard = 0
                )
            }
        } else {
            pauseTimer()
            _uiState.update {
                it.copy(
                    sessionState = SessionState.PAUSED,
                    inputText = "",
                    lastResult = null,
                    answerText = null
                )
            }
        }
    }
}

data class SubmitResult(
    val accepted: Boolean,
    val hintShown: Boolean
)

data class TrainingUiState(
    val languages: List<com.alexpo.grammermate.data.Language> = emptyList(),
    val installedPacks: List<com.alexpo.grammermate.data.LessonPack> = emptyList(),
    val selectedLanguageId: String = "en",
    val activePackId: String? = null,
    val activePackLessonIds: List<String>? = null,
    val lessons: List<Lesson> = emptyList(),
    val selectedLessonId: String? = null,
    val mode: TrainingMode = TrainingMode.LESSON,
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
    val storyCheckInDone: Boolean = false,
    val storyCheckOutDone: Boolean = false,
    val activeStory: StoryQuiz? = null,
    val storyErrorMessage: String? = null,
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
    val bossMegaRewards: Map<String, BossReward> = emptyMap(),
    val testMode: Boolean = false,
    val eliteActive: Boolean = false,
    val eliteStepIndex: Int = 0,
    val eliteBestSpeeds: List<Double> = emptyList(),
    val eliteFinishedToken: Int = 0,
    val eliteUnlocked: Boolean = false,
    val eliteSizeMultiplier: Double = 1.25,
    val vocabSprintLimit: Int = 20,
    // Flower mastery states
    val lessonFlowers: Map<String, FlowerVisual> = emptyMap(),
    val currentLessonFlower: FlowerVisual? = null,
    val currentLessonShownCount: Int = 0,
    // Word bank mode
    val wordBankWords: List<String> = emptyList(),
    val selectedWords: List<String> = emptyList(),
    // Streak tracking
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakMessage: String? = null,
    val streakCelebrationToken: Int = 0,
    // User profile
    val userName: String = "GrammarMateUser",
    val ladderRows: List<LessonLadderRow> = emptyList(),
    // TTS
    val ttsState: TtsState = TtsState.IDLE,
    val ttsDownloadState: DownloadState = DownloadState.Idle,
    val ttsModelReady: Boolean = false,
    val ttsMeteredNetwork: Boolean = false,
    val bgTtsDownloading: Boolean = false,
    val bgTtsDownloadStates: Map<String, DownloadState> = emptyMap(),
    val ttsModelsReady: Map<String, Boolean> = emptyMap(),
    val ttsSpeed: Float = 1.0f,
    val ruTextScale: Float = 1.0f,
    // Bad sentences
    val badSentenceCount: Int = 0,
    // Drill mode
    val isDrillMode: Boolean = false,
    val drillCardIndex: Int = 0,
    val drillTotalCards: Int = 0,
    val drillShowStartDialog: Boolean = false,
    val drillHasProgress: Boolean = false,
    // ASR (offline speech recognition)
    val useOfflineAsr: Boolean = false,
    val asrState: AsrState = AsrState.IDLE,
    val asrModelReady: Boolean = false,
    val asrDownloadState: DownloadState = DownloadState.Idle,
    val asrMeteredNetwork: Boolean = false,
    val asrErrorMessage: String? = null,
    val audioPermissionDenied: Boolean = false,
    // Persisted screen for state restoration
    val initialScreen: String = "HOME",
    val currentScreen: String = "HOME",
    // Vocab drill mastered count (global, across all POS)
    val vocabMasteredCount: Int = 0,
    // Daily practice session
    val dailySession: DailySessionState = DailySessionState()
)

data class LessonLadderRow(
    val index: Int,
    val lessonId: String,
    val title: String,
    val uniqueCardShows: Int?,
    val daysSinceLastShow: Int?,
    val intervalLabel: String?
)
