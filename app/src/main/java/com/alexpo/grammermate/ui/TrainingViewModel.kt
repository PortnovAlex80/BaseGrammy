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
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.TrainingProgress
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
    private var warmupCount: Int = 0
    private var subLessonTotal: Int = 0
    private var subLessonCount: Int = 0
    private var timerJob: Job? = null
    private var activeStartMs: Long? = null
    private val warmupSize = 3
    private val subLessonSizeMin = 6
    private val subLessonSizeMax = 12
    private val subLessonSize = 10

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
                subLessonFinishedToken = 0
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
                subLessonFinishedToken = 0
            )
        }
        buildSessionCards()
        saveProgress()
    }

    fun selectLesson(lessonId: String) {
        pauseTimer()
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
                subLessonFinishedToken = 0
            )
        }
        buildSessionCards()
        saveProgress()
    }

    fun selectMode(mode: TrainingMode) {
        pauseTimer()
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
                subLessonFinishedToken = 0
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
            if (isLastCard) {
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
        val wasHintShown = _uiState.value.sessionState == SessionState.HINT_SHOWN
        val nextIndex = (_uiState.value.currentIndex + 1).coerceAtMost(sessionCards.lastIndex)
        val nextCard = sessionCards.getOrNull(nextIndex)
        _uiState.update {
            val shouldTrigger = triggerVoice && it.inputMode == InputMode.VOICE
            it.copy(
                currentIndex = nextIndex,
                currentCard = nextCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.ACTIVE,
                voiceTriggerToken = if (shouldTrigger) it.voiceTriggerToken + 1 else it.voiceTriggerToken
            )
        }
        if (wasHintShown) {
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
                    subLessonFinishedToken = 0
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
                subLessonFinishedToken = 0
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
                subLessonFinishedToken = 0
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
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.PAUSED
            )
        }
        buildSessionCards()
        saveProgress()
    }

    private fun startSession() {
        buildSessionCards()
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
                state = state.sessionState
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
    val subLessonFinishedToken: Int = 0
)
