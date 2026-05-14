package com.alexpo.grammermate.feature.training

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.DrillProgressStore
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import com.alexpo.grammermate.feature.progress.StreakManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Session management module extracted from TrainingViewModel.
 *
 * Owns the training session lifecycle: card navigation, answer submission,
 * timer, word bank interaction, drill mode, and elite sub-mode.
 *
 * Query-style operations (getMastery, getSchedule, calculateCompletedSubLessons)
 * are injected as constructor function parameters.
 * Command-style results are returned as [List]<[SessionEvent]> for the ViewModel to execute.
 * Timer-triggered saveProgress is injected as a constructor function parameter.
 *
 * Uses [TrainingStateAccess] for state reads/writes.
 */
class SessionRunner(
    private val stateAccess: TrainingStateAccess,
    private val appContext: Application,
    private val coroutineScope: CoroutineScope,
    private val answerValidator: AnswerValidator,
    private val wordBankGenerator: WordBankGenerator,
    private val cardProvider: CardProvider,
    private val streakManager: StreakManager,
    private val drillProgressStore: DrillProgressStore,
    private val getMastery: (String, String) -> LessonMasteryState?,
    private val getSchedule: (String) -> LessonSchedule?,
    private val calculateCompletedSubLessons: (List<ScheduledSubLesson>, LessonMasteryState?, String?) -> Int,
    private val onTimerSaveProgress: () -> Unit
) {
    private val logTag = "SessionRunner"

    // ── Private mutable state ───────────────────────────────────────────

    private var sessionCards: List<SentenceCard> = emptyList()
    private var bossCards: List<SentenceCard> = emptyList()
    private var eliteCards: List<SentenceCard> = emptyList()
    private var timerJob: Job? = null
    private var activeStartMs: Long? = null

    private val subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
    private val eliteStepCount = TrainingConfig.ELITE_STEP_COUNT
    private var eliteSizeMultiplier: Double = TrainingConfig.ELITE_SIZE_MULTIPLIER

    // ── Submit result type ──────────────────────────────────────────────

    /**
     * Result of answer submission returned to ViewModel for orchestration.
     *
     * @property accepted      Whether the answer was correct.
     * @property hintShown     Whether a hint was displayed after 3 failures.
     * @property needsBossFinish  Boss mode: last card reached, ViewModel should call finishBoss.
     * @property needsSubLessonComplete  Normal mode: last card, sub-lesson completed.
     * @property needsEliteFinish  Elite mode: last card, step completed.
     * @property needsSaveProgress  Whether saveProgress should be called after this result.
     * @property needsFlowerRefresh  Whether flower states should be refreshed.
     */
    data class SubmitResult(
        val accepted: Boolean,
        val hintShown: Boolean,
        val needsBossFinish: Boolean = false,
        val needsSubLessonComplete: Boolean = false,
        val needsEliteFinish: Boolean = false,
        val needsSaveProgress: Boolean = true,
        val needsFlowerRefresh: Boolean = false
    )

    // ── Session lifecycle ───────────────────────────────────────────────

    /**
     * Start or resume an active training session.
     * Activates the timer and records the first card for mastery.
     */
    fun startSession(): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        val state = stateAccess.uiState.value
        if (!state.boss.bossActive && !state.elite.eliteActive && !state.drill.isDrillMode) {
            events.add(SessionEvent.BuildSessionCards)
        }
        if (sessionCards.isEmpty() || state.cardSession.currentCard == null) {
            pauseTimer()
            stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED)) }
            events.add(SessionEvent.SaveProgress)
            return events
        }
        pauseTimer()
        resumeTimer()
        stateAccess.updateState {
            val trigger = if (it.cardSession.inputMode == InputMode.VOICE) it.cardSession.voiceTriggerToken + 1 else it.cardSession.voiceTriggerToken
            it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.ACTIVE, inputText = "", voiceTriggerToken = trigger, voicePromptStartMs = null))
        }
        currentCard()?.let { events.add(SessionEvent.RecordCardShow(it)) }
        events.add(SessionEvent.SaveProgress)
        return events
    }

    /**
     * Finish the current training session.
     * Returns a [SessionFinishResult] for the ViewModel to apply.
     */
    fun finishSession(): Pair<SessionFinishResult, List<SessionEvent>> {
        if (sessionCards.isEmpty()) return SessionFinishResult.Empty to emptyList()
        val state = stateAccess.uiState.value
        if (state.elite.eliteActive) {
            cancelEliteSession()
            return SessionFinishResult.EliteCancelled to emptyList()
        }
        pauseTimer()
        val minutes = state.cardSession.activeTimeMs / 60000.0
        val rating = if (minutes <= 0.0) 0.0 else state.cardSession.correctCount / minutes
        val firstCard = sessionCards.firstOrNull()
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, lastRating = rating, incorrectAttemptsForCard = 0, lastResult = null, answerText = null, currentIndex = 0, currentCard = firstCard, inputText = "", voicePromptStartMs = null))
        }
        Log.d(logTag, "Session finished. Rating=$rating")
        return SessionFinishResult.Completed(rating) to listOf(SessionEvent.SaveProgress, SessionEvent.RefreshFlowerStates)
    }

    fun resumeFromSettings(): List<SessionEvent> {
        if (stateAccess.uiState.value.cardSession.sessionState == SessionState.ACTIVE) return emptyList()
        return startSession()
    }

    /**
     * Get the current card based on index and session cards.
     */
    fun currentCard(): SentenceCard? {
        if (sessionCards.isEmpty()) return null
        val index = stateAccess.uiState.value.cardSession.currentIndex.coerceIn(0, sessionCards.lastIndex)
        return sessionCards.getOrNull(index)
    }

    // ── Input handling ──────────────────────────────────────────────────

    fun onInputChanged(text: String) {
        stateAccess.updateState {
            val resetAttempts = it.cardSession.answerText != null || it.cardSession.incorrectAttemptsForCard >= AnswerValidator.HINT_THRESHOLD
            it.copy(cardSession = it.cardSession.copy(inputText = text, incorrectAttemptsForCard = if (resetAttempts) 0 else it.cardSession.incorrectAttemptsForCard, answerText = if (resetAttempts) null else it.cardSession.answerText))
        }
    }

    fun onVoicePromptStarted() {
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(voicePromptStartMs = SystemClock.elapsedRealtime()))
        }
    }

    fun setInputMode(mode: InputMode) {
        stateAccess.updateState {
            val resetAttempts = it.cardSession.answerText != null || it.cardSession.incorrectAttemptsForCard >= AnswerValidator.HINT_THRESHOLD
            val shouldTriggerVoice = mode == InputMode.VOICE &&
                resetAttempts &&
                it.cardSession.sessionState == SessionState.ACTIVE
            it.copy(cardSession = it.cardSession.copy(inputMode = mode, incorrectAttemptsForCard = if (resetAttempts) 0 else it.cardSession.incorrectAttemptsForCard, answerText = if (resetAttempts) null else it.cardSession.answerText, voiceTriggerToken = if (shouldTriggerVoice) it.cardSession.voiceTriggerToken + 1 else it.cardSession.voiceTriggerToken, voicePromptStartMs = if (mode == InputMode.VOICE) it.cardSession.voicePromptStartMs else null))
        }

        // Update word bank when switching to WORD_BANK mode
        if (mode == InputMode.WORD_BANK) {
            updateWordBank()
        }

        Log.d(logTag, "Input mode changed: $mode")
    }

    // ── Answer submission ───────────────────────────────────────────────

    /**
     * Submit the current answer. Orchestrates validation, state updates,
     * and signals the ViewModel for cross-module actions via [SubmitResult].
     */
    fun submitAnswer(): Pair<SubmitResult, List<SessionEvent>> {
        val state = stateAccess.uiState.value
        if (state.cardSession.sessionState != SessionState.ACTIVE) return SubmitResult(false, false, needsSaveProgress = false) to emptyList()
        if (state.cardSession.inputText.isBlank() && !state.cardSession.testMode) return SubmitResult(false, false, needsSaveProgress = false) to emptyList()
        val card = currentCard() ?: return SubmitResult(false, false, needsSaveProgress = false) to emptyList()
        val validationResult = answerValidator.validate(state.cardSession.inputText, card.acceptedAnswers, state.cardSession.testMode)
        val accepted = validationResult.isCorrect
        val voiceStartMs = if (state.cardSession.inputMode == InputMode.VOICE) state.cardSession.voicePromptStartMs else null
        val voiceDurationMs = voiceStartMs?.let { SystemClock.elapsedRealtime() - it }
        val voiceWords = if (voiceStartMs != null) countMetricWords(state.cardSession.inputText) else 0
        val shouldAddVoiceMetrics = accepted && voiceDurationMs != null && voiceWords > 0
        var hintShown = false

        if (accepted) {
            val events = mutableListOf<SessionEvent>(SessionEvent.PlaySuccess, SessionEvent.RecordCardShow(card))
            val isLastCard = state.cardSession.currentIndex >= sessionCards.lastIndex

            val result = when {
                state.boss.bossActive && isLastCard -> {
                    submitBossLastCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
                }
                state.boss.bossActive -> {
                    submitBossMidCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords, state)
                }
                state.elite.eliteActive && isLastCard -> {
                    val (r, e) = submitEliteFinish(shouldAddVoiceMetrics, voiceDurationMs, voiceWords, state)
                    events.addAll(e)
                    r
                }
                state.drill.isDrillMode -> {
                    submitDrillAnswer(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
                }
                isLastCard -> {
                    val (r, e) = submitNormalLastCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
                    events.addAll(e)
                    r
                }
                else -> {
                    submitNormalMidCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords, state)
                }
            }
            return result to events
        } else {
            val events = mutableListOf<SessionEvent>(SessionEvent.PlayError, SessionEvent.SaveProgress)
            val nextIncorrect = state.cardSession.incorrectAttemptsForCard + 1
            val hint = if (nextIncorrect >= AnswerValidator.HINT_THRESHOLD) card.acceptedAnswers.joinToString(" / ") else null
            hintShown = hint != null
            val shouldTriggerVoice = !hintShown && state.cardSession.inputMode == InputMode.VOICE
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(incorrectCount = it.cardSession.incorrectCount + 1, incorrectAttemptsForCard = if (hintShown) 0 else nextIncorrect, lastResult = false, answerText = hint, inputText = if (state.cardSession.inputMode == InputMode.VOICE) "" else it.cardSession.inputText, sessionState = if (hint != null) SessionState.HINT_SHOWN else it.cardSession.sessionState, voiceTriggerToken = if (shouldTriggerVoice) it.cardSession.voiceTriggerToken + 1 else it.cardSession.voiceTriggerToken, voicePromptStartMs = null))
            }
            if (hintShown) {
                pauseTimer()
            }
            Log.d(logTag, "Answer submitted: accepted=false")
            return SubmitResult(accepted = false, hintShown = hintShown, needsFlowerRefresh = false) to events
        }
    }

    /**
     * Boss mode: last card reached. ViewModel should finish boss after this.
     */
    private fun submitBossLastCard(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int,
    ): SubmitResult {
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(correctCount = it.cardSession.correctCount + 1, lastResult = null, incorrectAttemptsForCard = 0, answerText = null, voiceActiveMs = if (shouldAddVoiceMetrics) it.cardSession.voiceActiveMs + (voiceDurationMs ?: 0L) else it.cardSession.voiceActiveMs, voiceWordCount = if (shouldAddVoiceMetrics) it.cardSession.voiceWordCount + voiceWords else it.cardSession.voiceWordCount, voicePromptStartMs = null))
        }
        return SubmitResult(
            accepted = true,
            hintShown = false,
            needsBossFinish = true,
            needsSaveProgress = true,
            needsFlowerRefresh = true
        )
    }

    /**
     * Boss mode: mid-session correct answer. Advance to next card.
     */
    private fun submitBossMidCard(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int,
        state: com.alexpo.grammermate.data.TrainingUiState
    ): SubmitResult {
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(correctCount = it.cardSession.correctCount + 1, lastResult = true, incorrectAttemptsForCard = 0, answerText = null, voiceActiveMs = if (shouldAddVoiceMetrics) it.cardSession.voiceActiveMs + (voiceDurationMs ?: 0L) else it.cardSession.voiceActiveMs, voiceWordCount = if (shouldAddVoiceMetrics) it.cardSession.voiceWordCount + voiceWords else it.cardSession.voiceWordCount, voicePromptStartMs = null))
        }
        // nextCard handles voice trigger, word bank, save
        nextCardInternal(triggerVoice = state.cardSession.inputMode == InputMode.VOICE)
        return SubmitResult(accepted = true, hintShown = false, needsSaveProgress = false, needsFlowerRefresh = true)
    }

    /**
     * Elite mode: last card of step. Pause timer, calculate speed, advance step.
     */
    private fun submitEliteFinish(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int,
        state: com.alexpo.grammermate.data.TrainingUiState
    ): Pair<SubmitResult, List<SessionEvent>> {
        pauseTimer()
        val speed = calculateSpeedPerMinute(state.cardSession.voiceActiveMs, state.cardSession.voiceWordCount)
        val bestSpeeds = normalizeEliteSpeeds(state.elite.eliteBestSpeeds)
        val stepIndex = state.elite.eliteStepIndex.coerceIn(0, eliteStepCount - 1)
        val currentBest = bestSpeeds.getOrNull(stepIndex) ?: 0.0
        val nextSpeeds = bestSpeeds.toMutableList().apply {
            if (speed > currentBest) {
                this[stepIndex] = speed
            }
        }
        val nextStep = (stepIndex + 1) % eliteStepCount
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(correctCount = it.cardSession.correctCount + 1, lastResult = null, incorrectAttemptsForCard = 0, answerText = null, voiceActiveMs = if (shouldAddVoiceMetrics) it.cardSession.voiceActiveMs + (voiceDurationMs ?: 0L) else it.cardSession.voiceActiveMs, voiceWordCount = if (shouldAddVoiceMetrics) it.cardSession.voiceWordCount + voiceWords else it.cardSession.voiceWordCount, voicePromptStartMs = null, sessionState = SessionState.PAUSED, currentIndex = 0), elite = it.elite.copy(eliteActive = false, eliteStepIndex = nextStep, eliteBestSpeeds = nextSpeeds, eliteFinishedToken = it.elite.eliteFinishedToken + 1))
        }
        return SubmitResult(
            accepted = true,
            hintShown = false,
            needsEliteFinish = true
        ) to listOf(SessionEvent.SaveProgress)
    }

    /**
     * Drill mode: correct answer. Seamless advance to next drill card.
     */
    private fun submitDrillAnswer(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int
    ): SubmitResult {
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(correctCount = it.cardSession.correctCount + 1, lastResult = null, incorrectAttemptsForCard = 0, answerText = null, inputText = "", voiceActiveMs = if (shouldAddVoiceMetrics) it.cardSession.voiceActiveMs + (voiceDurationMs ?: 0L) else it.cardSession.voiceActiveMs, voiceWordCount = if (shouldAddVoiceMetrics) it.cardSession.voiceWordCount + voiceWords else it.cardSession.voiceWordCount, voicePromptStartMs = null, sessionState = SessionState.ACTIVE))
        }
        advanceDrillCard()
        return SubmitResult(accepted = true, hintShown = false, needsSaveProgress = false)
    }

    /**
     * Normal mode: last card of sub-lesson. Pause timer, mark completion,
     * signal ViewModel to orchestrate cross-module updates.
     */
    private fun submitNormalLastCard(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int
    ): Pair<SubmitResult, List<SessionEvent>> {
        pauseTimer()
        stateAccess.updateState {
            val nextCompleted = (it.cardSession.completedSubLessonCount + 1).coerceAtMost(it.cardSession.subLessonCount)
            val lessonId = it.navigation.selectedLessonId
            val mastery = lessonId?.let { id -> getMastery(id.value, it.navigation.selectedLanguageId.value) }
            val schedule = lessonId?.let { id -> getSchedule(id.value) }
            val subLessons = schedule?.subLessons.orEmpty()
            val actualCompletedCount = calculateCompletedSubLessons(subLessons, mastery, lessonId?.value)

            val preservedActiveIndex = maxOf(it.cardSession.activeSubLessonIndex, actualCompletedCount)
            val finalActiveIndex = preservedActiveIndex.coerceAtMost((it.cardSession.subLessonCount - 1).coerceAtLeast(0))

            it.copy(cardSession = it.cardSession.copy(correctCount = it.cardSession.correctCount + 1, lastResult = null, incorrectAttemptsForCard = 0, answerText = null, voiceActiveMs = if (shouldAddVoiceMetrics) it.cardSession.voiceActiveMs + (voiceDurationMs ?: 0L) else it.cardSession.voiceActiveMs, voiceWordCount = if (shouldAddVoiceMetrics) it.cardSession.voiceWordCount + voiceWords else it.cardSession.voiceWordCount, voicePromptStartMs = null, sessionState = SessionState.PAUSED, currentIndex = 0, activeSubLessonIndex = finalActiveIndex, completedSubLessonCount = maxOf(nextCompleted, actualCompletedCount), subLessonFinishedToken = it.cardSession.subLessonFinishedToken + 1))
        }
        Log.d(logTag, "Answer submitted: accepted=true (last card)")
        return SubmitResult(
            accepted = true,
            hintShown = false,
            needsSubLessonComplete = true,
            needsFlowerRefresh = true
        ) to listOf(
            SessionEvent.MarkSubLessonCardsShown(sessionCards),
            SessionEvent.BuildSessionCards,
            SessionEvent.CheckAndMarkLessonCompleted,
            SessionEvent.RefreshFlowerStates,
            SessionEvent.UpdateStreak,
            SessionEvent.SaveProgress
        )
    }

    /**
     * Normal mode: mid-sub-lesson correct answer. Advance to next card.
     */
    private fun submitNormalMidCard(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int,
        state: com.alexpo.grammermate.data.TrainingUiState
    ): SubmitResult {
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(correctCount = it.cardSession.correctCount + 1, lastResult = true, incorrectAttemptsForCard = 0, answerText = null, voiceActiveMs = if (shouldAddVoiceMetrics) it.cardSession.voiceActiveMs + (voiceDurationMs ?: 0L) else it.cardSession.voiceActiveMs, voiceWordCount = if (shouldAddVoiceMetrics) it.cardSession.voiceWordCount + voiceWords else it.cardSession.voiceWordCount, voicePromptStartMs = null))
        }
        // nextCard handles voice trigger, word bank, save
        nextCardInternal(triggerVoice = state.cardSession.inputMode == InputMode.VOICE)
        return SubmitResult(accepted = true, hintShown = false, needsSaveProgress = false, needsFlowerRefresh = true)
    }

    // ── Card navigation ─────────────────────────────────────────────────

    /**
     * Advance to the next card. Handles boss progress tracking and reward thresholds.
     *
     * @param triggerVoice Whether to trigger voice input on the next card.
     */
    fun nextCard(triggerVoice: Boolean = false): List<SessionEvent> {
        val events = nextCardInternal(triggerVoice)
        return events
    }

    private fun nextCardInternal(triggerVoice: Boolean): List<SessionEvent> {
        val state = stateAccess.uiState.value
        val wasHintShown = state.cardSession.sessionState == SessionState.HINT_SHOWN
        val nextIndex = (state.cardSession.currentIndex + 1).coerceAtMost(sessionCards.lastIndex)
        val nextCard = sessionCards.getOrNull(nextIndex)

        stateAccess.updateState {
            val shouldTrigger = triggerVoice && it.cardSession.inputMode == InputMode.VOICE
            it.copy(cardSession = it.cardSession.copy(currentIndex = nextIndex, currentCard = nextCard, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0, sessionState = SessionState.ACTIVE, voiceTriggerToken = if (shouldTrigger) it.cardSession.voiceTriggerToken + 1 else it.cardSession.voiceTriggerToken, voicePromptStartMs = null))
        }
        val events = mutableListOf<SessionEvent>()
        nextCard?.let { events.add(SessionEvent.RecordCardShow(it)) }

        // Update word bank if in WORD_BANK mode
        if (stateAccess.uiState.value.cardSession.inputMode == InputMode.WORD_BANK) {
            updateWordBank()
        }

        if (wasHintShown) {
            resumeTimer()
        }
        events.add(SessionEvent.SaveProgress)
        return events
    }

    fun prevCard(): List<SessionEvent> {
        val prevIndex = (stateAccess.uiState.value.cardSession.currentIndex - 1).coerceAtLeast(0)
        val prevCard = sessionCards.getOrNull(prevIndex)
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(currentIndex = prevIndex, currentCard = prevCard, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0, voicePromptStartMs = null))
        }
        val events = mutableListOf<SessionEvent>()
        prevCard?.let { events.add(SessionEvent.RecordCardShow(it)) }
        events.add(SessionEvent.SaveProgress)
        return events
    }

    fun selectSubLesson(index: Int): List<SessionEvent> {
        pauseTimer()
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(activeSubLessonIndex = index.coerceAtLeast(0), currentIndex = 0, inputText = "", lastResult = null, answerText = null, sessionState = SessionState.PAUSED))
        }
        return listOf(SessionEvent.BuildSessionCards, SessionEvent.SaveProgress)
    }

    // ── Pause/resume ────────────────────────────────────────────────────

    fun togglePause(): List<SessionEvent> {
        if (stateAccess.uiState.value.cardSession.sessionState == SessionState.ACTIVE) {
            pauseTimer()
            stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, voicePromptStartMs = null)) }
            return listOf(SessionEvent.SaveProgress)
        }
        return startSession()
    }

    fun pauseSession(): List<SessionEvent> {
        pauseTimer()
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, voicePromptStartMs = null)) }
        return listOf(SessionEvent.SaveProgress)
    }

    // ── Hint ────────────────────────────────────────────────────────────

    fun showAnswer(): List<SessionEvent> {
        val card = currentCard() ?: return emptyList()
        pauseTimer()
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(answerText = card.acceptedAnswers.joinToString(" / "), sessionState = SessionState.HINT_SHOWN, inputText = if (it.cardSession.inputMode == InputMode.VOICE) "" else it.cardSession.inputText, hintCount = it.cardSession.hintCount + 1, voicePromptStartMs = null))
        }
        return listOf(SessionEvent.SaveProgress)
    }

    // ── Word bank interaction ───────────────────────────────────────────

    fun selectWordFromBank(word: String) {
        val currentSelected = stateAccess.uiState.value.cardSession.selectedWords
        val newSelected = currentSelected + word
        val inputText = newSelected.joinToString(" ")

        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(selectedWords = newSelected, inputText = inputText))
        }
    }

    fun removeLastSelectedWord() {
        val currentSelected = stateAccess.uiState.value.cardSession.selectedWords
        if (currentSelected.isEmpty()) return

        val newSelected = currentSelected.dropLast(1)
        val inputText = newSelected.joinToString(" ")

        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(selectedWords = newSelected, inputText = inputText))
        }
    }

    // ── Skip ────────────────────────────────────────────────────────────

    fun skipToNextCard() {
        val state = stateAccess.uiState.value
        val nextIndex = state.cardSession.currentIndex + 1
        if (nextIndex < sessionCards.size) {
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(currentIndex = nextIndex, currentCard = sessionCards[nextIndex], inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0))
            }
        } else {
            pauseTimer()
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, inputText = "", lastResult = null, answerText = null))
            }
        }
    }

    // ── Elite sub-mode ──────────────────────────────────────────────────

    fun openEliteStep(index: Int): List<SessionEvent> {
        pauseTimer()
        val stepIndex = index.coerceIn(0, eliteStepCount - 1)
        val cards = buildEliteCards()
        eliteCards = cards
        sessionCards = cards
        val firstCard = cards.firstOrNull()
        stateAccess.updateState {
            it.copy(elite = it.elite.copy(eliteActive = true, eliteStepIndex = stepIndex), cardSession = it.cardSession.copy(currentIndex = 0, currentCard = firstCard, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0, correctCount = 0, incorrectCount = 0, activeTimeMs = 0L, voiceActiveMs = 0L, voiceWordCount = 0, hintCount = 0, voicePromptStartMs = null, sessionState = SessionState.PAUSED, subLessonTotal = cards.size, subLessonCount = eliteStepCount, activeSubLessonIndex = stepIndex, completedSubLessonCount = 0))
        }
        return listOf(SessionEvent.SaveProgress)
    }

    fun cancelEliteSession(): List<SessionEvent> {
        if (!stateAccess.uiState.value.elite.eliteActive) return emptyList()
        pauseTimer()
        stateAccess.updateState {
            it.copy(elite = it.elite.copy(eliteActive = false), cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, currentIndex = 0, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0, voicePromptStartMs = null))
        }
        return listOf(SessionEvent.SaveProgress, SessionEvent.RefreshFlowerStates)
    }

    fun resolveEliteUnlocked(lessons: List<Lesson>, testMode: Boolean): Boolean {
        return testMode || lessons.size >= 12
    }

    fun normalizeEliteSpeeds(speeds: List<Double>): List<Double> {
        return if (speeds.size >= eliteStepCount) {
            speeds.take(eliteStepCount)
        } else {
            speeds + List(eliteStepCount - speeds.size) { 0.0 }
        }
    }

    fun eliteSubLessonSize(): Int {
        return kotlin.math.ceil(subLessonSize * eliteSizeMultiplier).toInt()
    }

    fun calculateSpeedPerMinute(activeMs: Long, words: Int): Double {
        val minutes = activeMs / 60000.0
        if (minutes <= 0.0) return 0.0
        return words / minutes
    }

    // ── Drill sub-mode ──────────────────────────────────────────────────

    fun showDrillStartDialog(lessonId: String) {
        val lesson = stateAccess.uiState.value.navigation.lessons.firstOrNull { it.id.value == lessonId } ?: return
        if (lesson.drillCards.isEmpty()) return
        val hasProgress = drillProgressStore.hasProgress(lessonId)
        stateAccess.updateState {
            it.copy(drill = it.drill.copy(drillShowStartDialog = true, drillHasProgress = hasProgress))
        }
    }

    fun startDrill(resume: Boolean): List<SessionEvent> {
        val lessonId = stateAccess.uiState.value.navigation.selectedLessonId ?: return emptyList()
        val lesson = stateAccess.uiState.value.navigation.lessons.firstOrNull { it.id == lessonId } ?: return emptyList()
        val drillCards = lesson.drillCards
        if (drillCards.isEmpty()) return emptyList()

        pauseTimer()

        val startCardIndex = if (resume) {
            drillProgressStore.getDrillProgress(lessonId.value).coerceIn(0, drillCards.size - 1)
        } else {
            0
        }

        stateAccess.updateState {
            it.copy(drill = it.drill.copy(isDrillMode = true, drillCardIndex = startCardIndex, drillTotalCards = drillCards.size, drillShowStartDialog = false, drillHasProgress = false), cardSession = it.cardSession.copy(currentIndex = 0, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0, correctCount = 0, incorrectCount = 0, activeTimeMs = 0L, voiceActiveMs = 0L, voiceWordCount = 0, hintCount = 0, voicePromptStartMs = null, sessionState = SessionState.PAUSED, wordBankWords = emptyList(), selectedWords = emptyList()), boss = it.boss.copy(bossActive = false, bossType = null, bossTotal = 0, bossProgress = 0, bossReward = null, bossRewardMessage = null, bossFinishedToken = 0, bossErrorMessage = null), elite = it.elite.copy(eliteActive = false))
        }
        loadDrillCard(startCardIndex)
        return listOf(SessionEvent.SaveProgress)
    }

    fun dismissDrillDialog() {
        stateAccess.updateState { it.copy(drill = it.drill.copy(drillShowStartDialog = false)) }
    }

    fun loadDrillCard(cardIndex: Int, activate: Boolean = false) {
        val lessonId = stateAccess.uiState.value.navigation.selectedLessonId ?: return
        val lesson = stateAccess.uiState.value.navigation.lessons.firstOrNull { it.id == lessonId } ?: return
        val drillCards = lesson.drillCards
        if (cardIndex >= drillCards.size) {
            finishDrill(lessonId.value)
            return
        }

        val card = drillCards[cardIndex]
        sessionCards = listOf(card)
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(currentIndex = 0, currentCard = card, subLessonTotal = 1, sessionState = if (activate) SessionState.ACTIVE else SessionState.PAUSED, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0), drill = it.drill.copy(drillCardIndex = cardIndex))
        }
        if (stateAccess.uiState.value.cardSession.inputMode == InputMode.VOICE) {
            stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(voiceTriggerToken = it.cardSession.voiceTriggerToken + 1)) }
        }
    }

    fun advanceDrillCard() {
        val state = stateAccess.uiState.value
        if (!state.drill.isDrillMode) return
        val lessonId = state.navigation.selectedLessonId ?: return

        val nextIndex = state.drill.drillCardIndex + 1
        drillProgressStore.saveDrillProgress(lessonId.value, nextIndex)

        if (nextIndex >= state.drill.drillTotalCards) {
            finishDrill(lessonId.value)
        } else {
            loadDrillCard(nextIndex, activate = true)
        }
    }

    fun finishDrill(lessonId: String): List<SessionEvent> {
        drillProgressStore.clearDrillProgress(lessonId)
        stateAccess.updateState {
            it.copy(drill = it.drill.copy(isDrillMode = false, drillCardIndex = 0, drillTotalCards = 0), cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, currentIndex = 0, currentCard = null, subLessonFinishedToken = it.cardSession.subLessonFinishedToken + 1))
        }
        return listOf(SessionEvent.BuildSessionCards, SessionEvent.RefreshFlowerStates, SessionEvent.SaveProgress)
    }

    fun exitDrillMode(): List<SessionEvent> {
        val state = stateAccess.uiState.value
        if (!state.drill.isDrillMode) return emptyList()
        pauseTimer()
        val lessonId = state.navigation.selectedLessonId
        if (lessonId != null && state.drill.drillCardIndex > 0) {
            drillProgressStore.saveDrillProgress(lessonId.value, state.drill.drillCardIndex)
        }
        stateAccess.updateState {
            it.copy(drill = it.drill.copy(isDrillMode = false, drillCardIndex = 0, drillTotalCards = 0), cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, currentIndex = 0, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0, voicePromptStartMs = null))
        }
        return listOf(SessionEvent.BuildSessionCards, SessionEvent.RefreshFlowerStates, SessionEvent.SaveProgress)
    }

    // ── Card list management (called by ViewModel) ─────────────────────

    /**
     * Set the session cards list. Called by ViewModel after CardProvider builds them.
     */
    fun setSessionCards(cards: List<SentenceCard>) {
        sessionCards = cards
    }

    fun setBossCards(cards: List<SentenceCard>) {
        bossCards = cards
        sessionCards = cards
    }

    fun setEliteCards(cards: List<SentenceCard>) {
        eliteCards = cards
        sessionCards = cards
    }

    fun getSessionCards(): List<SentenceCard> = sessionCards

    fun clearAllCards() {
        sessionCards = emptyList()
        bossCards = emptyList()
        eliteCards = emptyList()
    }

    fun setEliteSizeMultiplier(multiplier: Double) {
        eliteSizeMultiplier = multiplier
    }

    // ── Timer (private) ─────────────────────────────────────────────────

    fun resumeTimer() {
        if (timerJob?.isActive == true) return
        activeStartMs = SystemClock.elapsedRealtime()
        timerJob = coroutineScope.launch {
            while (true) {
                delay(500)
                val start = activeStartMs ?: continue
                val elapsed = SystemClock.elapsedRealtime() - start
                stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(activeTimeMs = it.cardSession.activeTimeMs + elapsed)) }
                activeStartMs = SystemClock.elapsedRealtime()
                onTimerSaveProgress()
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        activeStartMs = null
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private fun buildEliteCards(): List<SentenceCard> {
        val cards = stateAccess.uiState.value.navigation.lessons.flatMap { it.cards }
        if (cards.isEmpty()) return emptyList()
        val eliteSize = kotlin.math.ceil(subLessonSize * eliteSizeMultiplier).toInt()
        return cards.shuffled().take(eliteSize)
    }

    private fun updateWordBank() {
        val card = stateAccess.uiState.value.cardSession.currentCard
        if (card == null) {
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(wordBankWords = emptyList(), selectedWords = emptyList()))
            }
            return
        }

        val correctAnswer = card.acceptedAnswers.firstOrNull() ?: ""
        val allCards = stateAccess.uiState.value.navigation.lessons.flatMap { it.cards }
        val wordBank = wordBankGenerator.generateForSentence(correctAnswer, allCards)

        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(wordBankWords = wordBank, selectedWords = emptyList(), inputText = ""))
        }
    }

    private fun countMetricWords(text: String): Int {
        val normalized = Normalizer.normalize(text)
        if (normalized.isBlank()) return 0
        return normalized.split(" ").count { it.length >= 3 }
    }

    // ── Result types ────────────────────────────────────────────────────

    sealed class SessionFinishResult {
        object Empty : SessionFinishResult()
        object EliteCancelled : SessionFinishResult()
        data class Completed(val rating: Double) : SessionFinishResult()
    }
}
