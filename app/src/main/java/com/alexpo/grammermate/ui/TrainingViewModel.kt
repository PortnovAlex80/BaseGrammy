package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.alexpo.grammermate.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.StoryPhase
import com.alexpo.grammermate.data.StoryQuiz
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.VocabEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private var sessionCards: List<SentenceCard> = emptyList()
    private var bossCards: List<SentenceCard> = emptyList()
    private var vocabSession: List<VocabEntry> = emptyList()
    private var warmupCount: Int = 0
    private var subLessonTotal: Int = 0
    private var subLessonCount: Int = 0
    private var timerJob: Job? = null
    private var activeStartMs: Long? = null
    private val warmupSize = TrainingConfig.WARMUP_SIZE
    private val subLessonSizeMin = TrainingConfig.SUB_LESSON_SIZE_MIN
    private val subLessonSizeMax = TrainingConfig.SUB_LESSON_SIZE_MAX
    private val subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds.add(sampleId)
            }
        }
        Log.d(logTag, "Update: duolingo sfx, prompt in speech UI, voice loop rules, stop resets progress")
        lessonStore.ensureSeedData()
        val progress = progressStore.load()
        val bossLessonRewards = progress.bossLessonRewards.mapNotNull { (lessonId, reward) ->
            val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
            lessonId to parsed
        }.toMap()
        val bossMegaReward = progress.bossMegaReward?.let { reward ->
            runCatching { BossReward.valueOf(reward) }.getOrNull()
        }
        val languages = lessonStore.getLanguages()
        val packs = lessonStore.getInstalledPacks()
        val selectedLanguageId = languages.firstOrNull { it.id == progress.languageId }?.id ?: "en"
        val lessons = lessonStore.getLessons(selectedLanguageId)
        val selectedLessonId = progress.lessonId ?: lessons.firstOrNull()?.id
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
                warmupCount = 0,
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
                bossMegaReward = bossMegaReward
            )
        }
        buildSessionCards()
        if (_uiState.value.sessionState == SessionState.ACTIVE && _uiState.value.currentCard != null) {
            resumeTimer()
            if (_uiState.value.inputMode == InputMode.VOICE) {
                _uiState.update { it.copy(voiceTriggerToken = it.voiceTriggerToken + 1) }
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
                voiceTriggerToken = if (shouldTriggerVoice) it.voiceTriggerToken + 1 else it.voiceTriggerToken
            )
        }
        Log.d(logTag, "Input mode changed: $mode")
    }

    fun selectLanguage(languageId: String) {
        pauseTimer()
        vocabSession = emptyList()
        val lessons = lessonStore.getLessons(languageId)
        val selectedLessonId = lessons.firstOrNull()?.id
        _uiState.update {
            it.copy(
                selectedLanguageId = languageId,
                lessons = lessons,
                selectedLessonId = selectedLessonId,
                currentIndex = 0,
                correctCount = 0,
                incorrectCount = 0,
                incorrectAttemptsForCard = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                sessionState = SessionState.PAUSED,
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
                bossMegaReward = null
            )
        }
        buildSessionCards()
        saveProgress()
    }

    fun selectLesson(lessonId: String) {
        pauseTimer()
        vocabSession = emptyList()
        _uiState.update {
            it.copy(
                selectedLessonId = lessonId,
                mode = TrainingMode.LESSON,
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.PAUSED,
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
                bossErrorMessage = null
            )
        }
        buildSessionCards()
        saveProgress()
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
                bossErrorMessage = null
            )
        }
        buildSessionCards()
        saveProgress()
    }

    fun submitAnswer(): SubmitResult {
        val state = _uiState.value
        if (state.sessionState != SessionState.ACTIVE) return SubmitResult(false, false)
        if (state.inputText.isBlank()) return SubmitResult(false, false)
        val card = currentCard() ?: return SubmitResult(false, false)
        val normalizedInput = Normalizer.normalize(state.inputText)
        val accepted = card.acceptedAnswers.any { Normalizer.normalize(it) == normalizedInput }
        var hintShown = false
        if (accepted) {
            playSuccessTone()
            val isLastCard = state.currentIndex >= sessionCards.lastIndex
            if (state.bossActive) {
                if (isLastCard) {
                    _uiState.update {
                        it.copy(
                            correctCount = it.correctCount + 1,
                            lastResult = null,
                            incorrectAttemptsForCard = 0,
                            answerText = null
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
                            answerText = null
                        )
                    }
                    nextCard(triggerVoice = state.inputMode == InputMode.VOICE)
                }
            } else if (isLastCard) {
                pauseTimer()
                _uiState.update {
                    val nextCompleted = (it.completedSubLessonCount + 1).coerceAtMost(it.subLessonCount)
                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastResult = null,
                        incorrectAttemptsForCard = 0,
                        answerText = null,
                        sessionState = SessionState.PAUSED,
                        currentIndex = 0,
                        activeSubLessonIndex = nextCompleted.coerceAtMost((it.subLessonCount - 1).coerceAtLeast(0)),
                        completedSubLessonCount = nextCompleted,
                        subLessonFinishedToken = it.subLessonFinishedToken + 1
                    )
                }
                buildSessionCards()
            } else {
                _uiState.update {
                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastResult = true,
                        incorrectAttemptsForCard = 0,
                        answerText = null
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
                    voiceTriggerToken = if (shouldTriggerVoice) it.voiceTriggerToken + 1 else it.voiceTriggerToken
                )
            }
            if (hintShown) {
                pauseTimer()
            }
        }
        saveProgress()
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
                bossProgress = nextProgress,
                bossReward = nextReward ?: it.bossReward,
                bossRewardMessage = rewardMessage
            )
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
                incorrectAttemptsForCard = 0
            )
        }
        saveProgress()
    }

    fun togglePause() {
        if (_uiState.value.sessionState == SessionState.ACTIVE) {
            pauseTimer()
            _uiState.update { it.copy(sessionState = SessionState.PAUSED) }
            saveProgress()
            return
        }
        startSession()
    }

    fun pauseSession() {
        pauseTimer()
        _uiState.update { it.copy(sessionState = SessionState.PAUSED) }
        saveProgress()
    }

    fun finishSession() {
        if (sessionCards.isEmpty()) return
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
                inputText = ""
            )
        }
        saveProgress()
        Log.d(logTag, "Session finished. Rating=$rating")
    }

    fun showAnswer() {
        val card = currentCard() ?: return
        pauseTimer()
        _uiState.update {
            it.copy(
                answerText = card.acceptedAnswers.joinToString(" / "),
                sessionState = SessionState.HINT_SHOWN,
                inputText = if (it.inputMode == InputMode.VOICE) "" else it.inputText
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
                    mode = TrainingMode.LESSON,
                    sessionState = SessionState.PAUSED,
                    currentIndex = 0,
                    correctCount = 0,
                    incorrectCount = 0,
                    incorrectAttemptsForCard = 0,
                    activeTimeMs = 0L,
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
                bossErrorMessage = null
                )
            }
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
                mode = TrainingMode.LESSON,
                sessionState = SessionState.PAUSED,
                currentIndex = 0,
                correctCount = 0,
                incorrectCount = 0,
                incorrectAttemptsForCard = 0,
                activeTimeMs = 0L,
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
                bossErrorMessage = null
            )
        }
        buildSessionCards()
        saveProgress()
    }

    fun deleteAllLessons() {
        val languageId = _uiState.value.selectedLanguageId
        lessonStore.deleteAllLessons(languageId)
        refreshLessons(null)
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
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.PAUSED,
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
                bossErrorMessage = null
            )
        }
        buildSessionCards()
        saveProgress()
    }

    fun resumeFromSettings() {
        if (_uiState.value.sessionState == SessionState.ACTIVE) return
        startSession()
    }

    private fun buildSessionCards() {
        if (_uiState.value.bossActive) return
        val state = _uiState.value
        val lessons = state.lessons
        val lessonCards = when (state.mode) {
            TrainingMode.LESSON -> {
                val lesson = lessons.firstOrNull { it.id == state.selectedLessonId }
                lesson?.cards ?: emptyList()
            }
            TrainingMode.ALL_SEQUENTIAL -> lessons.flatMap { it.cards }
            TrainingMode.ALL_MIXED -> lessons.flatMap { it.cards }.shuffled()
        }
        val warmup = lessonCards.take(warmupSize)
        val mainCards = lessonCards.drop(warmup.size)
        val blockSize = subLessonSize.coerceIn(subLessonSizeMin, subLessonSizeMax)
        subLessonCount = if (mainCards.isEmpty()) 0 else (mainCards.size + blockSize - 1) / blockSize
        val activeIndex = state.activeSubLessonIndex.coerceIn(0, (subLessonCount - 1).coerceAtLeast(0))
        val blockStart = activeIndex * blockSize
        val block = mainCards.drop(blockStart).take(blockSize)
        sessionCards = warmup + block
        warmupCount = warmup.size
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
                warmupCount = warmupCount,
                subLessonTotal = subLessonTotal,
                subLessonCount = subLessonCount,
                activeSubLessonIndex = activeIndex
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

    fun openStory(phase: StoryPhase) {
        val lessonId = _uiState.value.selectedLessonId ?: return
        val languageId = _uiState.value.selectedLanguageId
        val story = lessonStore.getStoryQuizzes(lessonId, phase, languageId).firstOrNull()
        if (story == null) {
            _uiState.update { it.copy(storyErrorMessage = "История не найдена. Импортируйте пакет заново.") }
            return
        }
        _uiState.update { it.copy(activeStory = story, storyErrorMessage = null) }
    }

    fun openVocabSprint() {
        val lessonId = _uiState.value.selectedLessonId ?: return
        val languageId = _uiState.value.selectedLanguageId
        val entries = lessonStore.getVocabEntries(lessonId, languageId).take(3)
        if (entries.isEmpty()) {
            vocabSession = emptyList()
            _uiState.update { it.copy(vocabErrorMessage = "Словарь не найден. Импортируйте пакет заново.") }
            return
        }
        vocabSession = entries
        _uiState.update {
            it.copy(
                currentVocab = entries.firstOrNull(),
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = entries.size,
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
        _uiState.update {
            if (!allCorrect) {
                return@update it.copy(activeStory = null)
            }
            when (phase) {
                StoryPhase.CHECK_IN -> it.copy(storyCheckInDone = true, activeStory = null)
                StoryPhase.CHECK_OUT -> it.copy(storyCheckOutDone = true, activeStory = null)
            }
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
        _uiState.update { it.copy(vocabInputMode = mode) }
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
        if (input.isBlank()) return
        val normalizedInput = Normalizer.normalize(input)
        val accepted = entry.targetText.split("+")
            .map { Normalizer.normalize(it) }
            .any { it == normalizedInput }
        if (accepted) {
            playSuccessTone()
            moveToNextVocab()
            return
        }
        playErrorTone()
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

    private fun moveToNextVocab() {
        val state = _uiState.value
        val nextIndex = state.vocabIndex + 1
        if (nextIndex >= vocabSession.size) {
            _uiState.update {
                it.copy(
                    currentVocab = null,
                    vocabInputText = "",
                    vocabAttempts = 0,
                    vocabAnswerText = null,
                    vocabIndex = nextIndex,
                    vocabTotal = vocabSession.size,
                    vocabFinishedToken = it.vocabFinishedToken + 1
                )
            }
            return
        }
        val next = vocabSession[nextIndex]
        _uiState.update {
            it.copy(
                currentVocab = next,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = nextIndex,
                vocabTotal = vocabSession.size
            )
        }
    }

    fun startBossLesson() {
        startBoss(BossType.LESSON)
    }

    fun startBossMega() {
        startBoss(BossType.MEGA)
    }

    private fun startBoss(type: BossType) {
        pauseTimer()
        val lessons = _uiState.value.lessons
        val selectedId = _uiState.value.selectedLessonId
        if (selectedId == null) {
            _uiState.update { it.copy(bossErrorMessage = "Lesson not selected") }
            return
        }
        val selectedIndex = lessons.indexOfFirst { it.id == selectedId }
        val cards = when (type) {
            BossType.LESSON -> lessons.firstOrNull { it.id == selectedId }?.cards ?: emptyList()
            BossType.MEGA -> {
                if (selectedIndex <= 0) emptyList() else lessons.take(selectedIndex).flatMap { it.cards }
            }
        }
        if (cards.isEmpty()) {
            val message = if (type == BossType.MEGA) {
                "Mega boss is available after the first lesson"
            } else {
                "Boss has no cards"
            }
            _uiState.update { it.copy(bossErrorMessage = message) }
            return
        }
        bossCards = cards
        sessionCards = bossCards
        warmupCount = 0
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
                sessionState = SessionState.PAUSED,
                warmupCount = 0,
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
        val updatedMegaReward = if (state.bossType == BossType.MEGA && reward != null) {
            reward
        } else {
            state.bossMegaReward
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
                bossErrorMessage = null,
                bossLessonRewards = updatedLessonRewards,
                bossMegaReward = updatedMegaReward,
                selectedLessonId = restoredLessonId,
                mode = progress.mode,
                currentIndex = progress.currentIndex,
                correctCount = progress.correctCount,
                incorrectCount = progress.incorrectCount,
                incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                activeTimeMs = progress.activeTimeMs,
                inputText = "",
                lastResult = null,
                answerText = null,
                sessionState = SessionState.PAUSED
            )
        }
        buildSessionCards()
        saveProgress()
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
        if (!_uiState.value.bossActive) {
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
            it.copy(sessionState = SessionState.ACTIVE, inputText = "", voiceTriggerToken = trigger)
        }
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
        if (state.bossActive) return
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
                bossMegaReward = state.bossMegaReward?.name
            )
        )
    }

    override fun onCleared() {
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
}

data class SubmitResult(
    val accepted: Boolean,
    val hintShown: Boolean
)

data class TrainingUiState(
    val languages: List<com.alexpo.grammermate.data.Language> = emptyList(),
    val installedPacks: List<com.alexpo.grammermate.data.LessonPack> = emptyList(),
    val selectedLanguageId: String = "en",
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
    val answerText: String? = null,
    val lastResult: Boolean? = null,
    val lastRating: Double? = null,
    val inputMode: InputMode = InputMode.VOICE,
    val voiceTriggerToken: Int = 0,
    val warmupCount: Int = 0,
    val subLessonTotal: Int = 0,
    val subLessonCount: Int = 0,
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
    val bossErrorMessage: String? = null,
    val bossLessonRewards: Map<String, BossReward> = emptyMap(),
    val bossMegaReward: BossReward? = null
)
