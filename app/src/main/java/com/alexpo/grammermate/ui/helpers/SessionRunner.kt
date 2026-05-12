package com.alexpo.grammermate.ui.helpers

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.alexpo.grammermate.data.BossReward
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
 * All cross-module orchestration (mastery tracking, flower refresh,
 * streak updates, boss battles) is communicated through callback lambdas
 * so that SessionRunner never holds references to other helpers.
 *
 * Uses [TrainingStateAccess] for state reads/writes. The ViewModel
 * wires callbacks to delegate to ProgressTracker, BossBattleRunner,
 * AudioCoordinator, etc.
 */
class SessionRunner(
    private val stateAccess: TrainingStateAccess,
    private val appContext: Application,
    private val coroutineScope: CoroutineScope,
    private val answerValidator: AnswerValidator,
    private val wordBankGenerator: WordBankGenerator,
    private val cardProvider: CardProvider,
    private val streakManager: StreakManager
) {
    private val logTag = "SessionRunner"

    // ── Callbacks for cross-module orchestration ────────────────────────

    /** Record a card show for mastery tracking (delegates to ProgressTracker). */
    var onRecordCardShow: ((SentenceCard) -> Unit)? = null

    /** Mark all cards in a completed sub-lesson as shown (delegates to ProgressTracker). */
    var onMarkSubLessonCardsShown: ((List<SentenceCard>) -> Unit)? = null

    /** Check and mark a lesson as completed (delegates to ProgressTracker). */
    var onCheckAndMarkLessonCompleted: (() -> Unit)? = null

    /** Calculate completed sub-lessons count (delegates to ProgressTracker). */
    var onCalculateCompletedSubLessons: ((List<ScheduledSubLesson>, LessonMasteryState?, String?) -> Int)? = null

    /** Refresh flower states after mastery changes (ViewModel orchestration). */
    var onRefreshFlowerStates: (() -> Unit)? = null

    /** Update streak after sub-lesson completion (ViewModel orchestration). */
    var onUpdateStreak: (() -> Unit)? = null

    /** Save progress to persistent storage (ViewModel orchestration). */
    var onSaveProgress: (() -> Unit)? = null

    /** Play success sound (delegates to AudioCoordinator). */
    var onPlaySuccess: (() -> Unit)? = null

    /** Play error sound (delegates to AudioCoordinator). */
    var onPlayError: (() -> Unit)? = null

    /** Build session cards (delegates to CardProvider through ViewModel). */
    var onBuildSessionCards: (() -> Unit)? = null

    /** Rebuild schedules (delegates to CardProvider through ViewModel). */
    var onRebuildSchedules: ((List<Lesson>) -> Unit)? = null

    /** Calculate completed sub-lessons from mastery store (delegates to ProgressTracker). */
    var onGetMastery: ((String, String) -> LessonMasteryState?)? = null

    /** Get lesson schedule (reads from CardProvider's cached schedules). */
    var onGetSchedule: ((String) -> LessonSchedule?)? = null

    // ── Private mutable state ───────────────────────────────────────────

    private var sessionCards: List<SentenceCard> = emptyList()
    private var bossCards: List<SentenceCard> = emptyList()
    private var eliteCards: List<SentenceCard> = emptyList()
    private var timerJob: Job? = null
    private var activeStartMs: Long? = null

    private val subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
    private val eliteStepCount = TrainingConfig.ELITE_STEP_COUNT
    private var eliteSizeMultiplier: Double = 1.25

    private val drillProgressStore = DrillProgressStore(appContext)

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
    fun startSession() {
        val state = stateAccess.uiState.value
        if (!state.bossActive && !state.eliteActive && !state.isDrillMode) {
            onBuildSessionCards?.invoke()
        }
        if (sessionCards.isEmpty() || state.currentCard == null) {
            pauseTimer()
            stateAccess.updateState { it.copy(sessionState = SessionState.PAUSED) }
            onSaveProgress?.invoke()
            return
        }
        pauseTimer()
        resumeTimer()
        stateAccess.updateState {
            val trigger = if (it.inputMode == InputMode.VOICE) it.voiceTriggerToken + 1 else it.voiceTriggerToken
            it.copy(
                sessionState = SessionState.ACTIVE,
                inputText = "",
                voiceTriggerToken = trigger,
                voicePromptStartMs = null
            )
        }
        currentCard()?.let { onRecordCardShow?.invoke(it) }
        onSaveProgress?.invoke()
    }

    /**
     * Finish the current training session.
     * Returns a [SessionFinishResult] for the ViewModel to apply.
     */
    fun finishSession(): SessionFinishResult {
        if (sessionCards.isEmpty()) return SessionFinishResult.Empty
        val state = stateAccess.uiState.value
        if (state.eliteActive) {
            cancelEliteSession()
            return SessionFinishResult.EliteCancelled
        }
        pauseTimer()
        val minutes = state.activeTimeMs / 60000.0
        val rating = if (minutes <= 0.0) 0.0 else state.correctCount / minutes
        val firstCard = sessionCards.firstOrNull()
        stateAccess.updateState {
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
        onSaveProgress?.invoke()
        onRefreshFlowerStates?.invoke()
        Log.d(logTag, "Session finished. Rating=$rating")
        return SessionFinishResult.Completed(rating)
    }

    fun resumeFromSettings() {
        if (stateAccess.uiState.value.sessionState == SessionState.ACTIVE) return
        startSession()
    }

    /**
     * Get the current card based on index and session cards.
     */
    fun currentCard(): SentenceCard? {
        if (sessionCards.isEmpty()) return null
        val index = stateAccess.uiState.value.currentIndex.coerceIn(0, sessionCards.lastIndex)
        return sessionCards.getOrNull(index)
    }

    // ── Input handling ──────────────────────────────────────────────────

    fun onInputChanged(text: String) {
        stateAccess.updateState {
            val resetAttempts = it.answerText != null || it.incorrectAttemptsForCard >= 3
            it.copy(
                inputText = text,
                incorrectAttemptsForCard = if (resetAttempts) 0 else it.incorrectAttemptsForCard,
                answerText = if (resetAttempts) null else it.answerText
            )
        }
    }

    fun onVoicePromptStarted() {
        stateAccess.updateState {
            it.copy(voicePromptStartMs = SystemClock.elapsedRealtime())
        }
    }

    fun setInputMode(mode: InputMode) {
        stateAccess.updateState {
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

    // ── Answer submission ───────────────────────────────────────────────

    /**
     * Submit the current answer. Orchestrates validation, state updates,
     * and signals the ViewModel for cross-module actions via [SubmitResult].
     */
    fun submitAnswer(): SubmitResult {
        val state = stateAccess.uiState.value
        if (state.sessionState != SessionState.ACTIVE) return SubmitResult(false, false, needsSaveProgress = false)
        if (state.inputText.isBlank() && !state.testMode) return SubmitResult(false, false, needsSaveProgress = false)
        val card = currentCard() ?: return SubmitResult(false, false, needsSaveProgress = false)
        val validationResult = answerValidator.validate(state.inputText, card.acceptedAnswers, state.testMode)
        val accepted = validationResult.isCorrect
        val voiceStartMs = if (state.inputMode == InputMode.VOICE) state.voicePromptStartMs else null
        val voiceDurationMs = voiceStartMs?.let { SystemClock.elapsedRealtime() - it }
        val voiceWords = if (voiceStartMs != null) countMetricWords(state.inputText) else 0
        val shouldAddVoiceMetrics = accepted && voiceDurationMs != null && voiceWords > 0
        var hintShown = false

        if (accepted) {
            onPlaySuccess?.invoke()
            onRecordCardShow?.invoke(card)
            val isLastCard = state.currentIndex >= sessionCards.lastIndex

            return when {
                state.bossActive && isLastCard -> {
                    submitBossLastCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
                }
                state.bossActive -> {
                    submitBossMidCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords, state)
                }
                state.eliteActive && isLastCard -> {
                    submitEliteFinish(shouldAddVoiceMetrics, voiceDurationMs, voiceWords, state)
                }
                state.isDrillMode -> {
                    submitDrillAnswer(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
                }
                isLastCard -> {
                    submitNormalLastCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
                }
                else -> {
                    submitNormalMidCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords, state)
                }
            }
        } else {
            onPlayError?.invoke()
            val nextIncorrect = state.incorrectAttemptsForCard + 1
            val hint = if (nextIncorrect >= 3) card.acceptedAnswers.joinToString(" / ") else null
            hintShown = hint != null
            val shouldTriggerVoice = !hintShown && state.inputMode == InputMode.VOICE
            stateAccess.updateState {
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
            onSaveProgress?.invoke()
            Log.d(logTag, "Answer submitted: accepted=false")
            return SubmitResult(accepted = false, hintShown = hintShown, needsFlowerRefresh = false)
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
            it.copy(
                correctCount = it.correctCount + 1,
                lastResult = null,
                incorrectAttemptsForCard = 0,
                answerText = null,
                voiceActiveMs = if (shouldAddVoiceMetrics) it.voiceActiveMs + (voiceDurationMs ?: 0L) else it.voiceActiveMs,
                voiceWordCount = if (shouldAddVoiceMetrics) it.voiceWordCount + voiceWords else it.voiceWordCount,
                voicePromptStartMs = null
            )
        }
        onSaveProgress?.invoke()
        return SubmitResult(
            accepted = true,
            hintShown = false,
            needsBossFinish = true,
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
        state: com.alexpo.grammermate.ui.TrainingUiState
    ): SubmitResult {
        stateAccess.updateState {
            it.copy(
                correctCount = it.correctCount + 1,
                lastResult = true,
                incorrectAttemptsForCard = 0,
                answerText = null,
                voiceActiveMs = if (shouldAddVoiceMetrics) it.voiceActiveMs + (voiceDurationMs ?: 0L) else it.voiceActiveMs,
                voiceWordCount = if (shouldAddVoiceMetrics) it.voiceWordCount + voiceWords else it.voiceWordCount,
                voicePromptStartMs = null
            )
        }
        // nextCard handles voice trigger, word bank, save
        nextCard(triggerVoice = state.inputMode == InputMode.VOICE)
        return SubmitResult(accepted = true, hintShown = false, needsSaveProgress = false, needsFlowerRefresh = true)
    }

    /**
     * Elite mode: last card of step. Pause timer, calculate speed, advance step.
     */
    private fun submitEliteFinish(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int,
        state: com.alexpo.grammermate.ui.TrainingUiState
    ): SubmitResult {
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
        stateAccess.updateState {
            it.copy(
                correctCount = it.correctCount + 1,
                lastResult = null,
                incorrectAttemptsForCard = 0,
                answerText = null,
                voiceActiveMs = if (shouldAddVoiceMetrics) it.voiceActiveMs + (voiceDurationMs ?: 0L) else it.voiceActiveMs,
                voiceWordCount = if (shouldAddVoiceMetrics) it.voiceWordCount + voiceWords else it.voiceWordCount,
                voicePromptStartMs = null,
                sessionState = SessionState.PAUSED,
                currentIndex = 0,
                eliteActive = false,
                eliteStepIndex = nextStep,
                eliteBestSpeeds = nextSpeeds,
                eliteFinishedToken = it.eliteFinishedToken + 1
            )
        }
        onSaveProgress?.invoke()
        return SubmitResult(
            accepted = true,
            hintShown = false,
            needsEliteFinish = true
        )
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
            it.copy(
                correctCount = it.correctCount + 1,
                lastResult = null,
                incorrectAttemptsForCard = 0,
                answerText = null,
                inputText = "",
                voiceActiveMs = if (shouldAddVoiceMetrics) it.voiceActiveMs + (voiceDurationMs ?: 0L) else it.voiceActiveMs,
                voiceWordCount = if (shouldAddVoiceMetrics) it.voiceWordCount + voiceWords else it.voiceWordCount,
                voicePromptStartMs = null,
                sessionState = SessionState.ACTIVE
            )
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
    ): SubmitResult {
        pauseTimer()
        stateAccess.updateState {
            val nextCompleted = (it.completedSubLessonCount + 1).coerceAtMost(it.subLessonCount)
            val lessonId = it.selectedLessonId
            val mastery = lessonId?.let { id -> onGetMastery?.invoke(id, it.selectedLanguageId) }
            val schedule = lessonId?.let { id -> onGetSchedule?.invoke(id) }
            val subLessons = schedule?.subLessons.orEmpty()
            val actualCompletedCount = onCalculateCompletedSubLessons?.invoke(subLessons, mastery, lessonId) ?: 0

            val preservedActiveIndex = maxOf(it.activeSubLessonIndex, actualCompletedCount)
            val finalActiveIndex = preservedActiveIndex.coerceAtMost((it.subLessonCount - 1).coerceAtLeast(0))

            it.copy(
                correctCount = it.correctCount + 1,
                lastResult = null,
                incorrectAttemptsForCard = 0,
                answerText = null,
                voiceActiveMs = if (shouldAddVoiceMetrics) it.voiceActiveMs + (voiceDurationMs ?: 0L) else it.voiceActiveMs,
                voiceWordCount = if (shouldAddVoiceMetrics) it.voiceWordCount + voiceWords else it.voiceWordCount,
                voicePromptStartMs = null,
                sessionState = SessionState.PAUSED,
                currentIndex = 0,
                activeSubLessonIndex = finalActiveIndex,
                completedSubLessonCount = maxOf(nextCompleted, actualCompletedCount),
                subLessonFinishedToken = it.subLessonFinishedToken + 1
            )
        }
        onMarkSubLessonCardsShown?.invoke(sessionCards)
        onBuildSessionCards?.invoke()
        onCheckAndMarkLessonCompleted?.invoke()
        onRefreshFlowerStates?.invoke()
        onUpdateStreak?.invoke()
        onSaveProgress?.invoke()
        Log.d(logTag, "Answer submitted: accepted=true (last card)")
        return SubmitResult(
            accepted = true,
            hintShown = false,
            needsSubLessonComplete = true,
            needsFlowerRefresh = true
        )
    }

    /**
     * Normal mode: mid-sub-lesson correct answer. Advance to next card.
     */
    private fun submitNormalMidCard(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int,
        state: com.alexpo.grammermate.ui.TrainingUiState
    ): SubmitResult {
        stateAccess.updateState {
            it.copy(
                correctCount = it.correctCount + 1,
                lastResult = true,
                incorrectAttemptsForCard = 0,
                answerText = null,
                voiceActiveMs = if (shouldAddVoiceMetrics) it.voiceActiveMs + (voiceDurationMs ?: 0L) else it.voiceActiveMs,
                voiceWordCount = if (shouldAddVoiceMetrics) it.voiceWordCount + voiceWords else it.voiceWordCount,
                voicePromptStartMs = null
            )
        }
        // nextCard handles voice trigger, word bank, save
        nextCard(triggerVoice = state.inputMode == InputMode.VOICE)
        return SubmitResult(accepted = true, hintShown = false, needsSaveProgress = false, needsFlowerRefresh = true)
    }

    // ── Card navigation ─────────────────────────────────────────────────

    /**
     * Advance to the next card. Handles boss progress tracking and reward thresholds.
     *
     * @param triggerVoice Whether to trigger voice input on the next card.
     */
    fun nextCard(triggerVoice: Boolean = false) {
        val state = stateAccess.uiState.value
        val wasHintShown = state.sessionState == SessionState.HINT_SHOWN
        val nextIndex = (state.currentIndex + 1).coerceAtMost(sessionCards.lastIndex)
        val nextCard = sessionCards.getOrNull(nextIndex)

        stateAccess.updateState {
            val shouldTrigger = triggerVoice && it.inputMode == InputMode.VOICE
            it.copy(
                currentIndex = nextIndex,
                currentCard = nextCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.ACTIVE,
                voiceTriggerToken = if (shouldTrigger) it.voiceTriggerToken + 1 else it.voiceTriggerToken,
                voicePromptStartMs = null
            )
        }
        nextCard?.let { onRecordCardShow?.invoke(it) }

        // Update word bank if in WORD_BANK mode
        if (stateAccess.uiState.value.inputMode == InputMode.WORD_BANK) {
            updateWordBank()
        }

        if (wasHintShown) {
            resumeTimer()
        }
        onSaveProgress?.invoke()
    }

    fun prevCard() {
        val prevIndex = (stateAccess.uiState.value.currentIndex - 1).coerceAtLeast(0)
        val prevCard = sessionCards.getOrNull(prevIndex)
        stateAccess.updateState {
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
        prevCard?.let { onRecordCardShow?.invoke(it) }
        onSaveProgress?.invoke()
    }

    fun selectSubLesson(index: Int) {
        pauseTimer()
        stateAccess.updateState {
            it.copy(
                activeSubLessonIndex = index.coerceAtLeast(0),
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                sessionState = SessionState.PAUSED
            )
        }
        onBuildSessionCards?.invoke()
        onSaveProgress?.invoke()
    }

    // ── Pause/resume ────────────────────────────────────────────────────

    fun togglePause() {
        if (stateAccess.uiState.value.sessionState == SessionState.ACTIVE) {
            pauseTimer()
            stateAccess.updateState { it.copy(sessionState = SessionState.PAUSED, voicePromptStartMs = null) }
            onSaveProgress?.invoke()
            return
        }
        startSession()
    }

    fun pauseSession() {
        pauseTimer()
        stateAccess.updateState { it.copy(sessionState = SessionState.PAUSED, voicePromptStartMs = null) }
        onSaveProgress?.invoke()
    }

    // ── Hint ────────────────────────────────────────────────────────────

    fun showAnswer() {
        val card = currentCard() ?: return
        pauseTimer()
        stateAccess.updateState {
            it.copy(
                answerText = card.acceptedAnswers.joinToString(" / "),
                sessionState = SessionState.HINT_SHOWN,
                inputText = if (it.inputMode == InputMode.VOICE) "" else it.inputText,
                hintCount = it.hintCount + 1,
                voicePromptStartMs = null
            )
        }
        onSaveProgress?.invoke()
    }

    // ── Word bank interaction ───────────────────────────────────────────

    fun selectWordFromBank(word: String) {
        val currentSelected = stateAccess.uiState.value.selectedWords
        val newSelected = currentSelected + word
        val inputText = newSelected.joinToString(" ")

        stateAccess.updateState {
            it.copy(
                selectedWords = newSelected,
                inputText = inputText
            )
        }
    }

    fun removeLastSelectedWord() {
        val currentSelected = stateAccess.uiState.value.selectedWords
        if (currentSelected.isEmpty()) return

        val newSelected = currentSelected.dropLast(1)
        val inputText = newSelected.joinToString(" ")

        stateAccess.updateState {
            it.copy(
                selectedWords = newSelected,
                inputText = inputText
            )
        }
    }

    // ── Skip ────────────────────────────────────────────────────────────

    fun skipToNextCard() {
        val state = stateAccess.uiState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex < sessionCards.size) {
            stateAccess.updateState {
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
            stateAccess.updateState {
                it.copy(
                    sessionState = SessionState.PAUSED,
                    inputText = "",
                    lastResult = null,
                    answerText = null
                )
            }
        }
    }

    // ── Elite sub-mode ──────────────────────────────────────────────────

    fun openEliteStep(index: Int) {
        pauseTimer()
        val stepIndex = index.coerceIn(0, eliteStepCount - 1)
        val cards = buildEliteCards()
        eliteCards = cards
        sessionCards = cards
        val firstCard = cards.firstOrNull()
        stateAccess.updateState {
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
        onSaveProgress?.invoke()
    }

    fun cancelEliteSession() {
        if (!stateAccess.uiState.value.eliteActive) return
        pauseTimer()
        stateAccess.updateState {
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
        onSaveProgress?.invoke()
        onRefreshFlowerStates?.invoke()
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
        val lesson = stateAccess.uiState.value.lessons.firstOrNull { it.id == lessonId } ?: return
        if (lesson.drillCards.isEmpty()) return
        val hasProgress = drillProgressStore.hasProgress(lessonId)
        stateAccess.updateState {
            it.copy(
                drillShowStartDialog = true,
                drillHasProgress = hasProgress
            )
        }
    }

    fun startDrill(resume: Boolean) {
        val lessonId = stateAccess.uiState.value.selectedLessonId ?: return
        val lesson = stateAccess.uiState.value.lessons.firstOrNull { it.id == lessonId } ?: return
        val drillCards = lesson.drillCards
        if (drillCards.isEmpty()) return

        pauseTimer()

        val startCardIndex = if (resume) {
            drillProgressStore.getDrillProgress(lessonId).coerceIn(0, drillCards.size - 1)
        } else {
            0
        }

        stateAccess.updateState {
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
                selectedWords = emptyList()
            )
        }
        loadDrillCard(startCardIndex)
        onSaveProgress?.invoke()
    }

    fun dismissDrillDialog() {
        stateAccess.updateState { it.copy(drillShowStartDialog = false) }
    }

    fun loadDrillCard(cardIndex: Int, activate: Boolean = false) {
        val lessonId = stateAccess.uiState.value.selectedLessonId ?: return
        val lesson = stateAccess.uiState.value.lessons.firstOrNull { it.id == lessonId } ?: return
        val drillCards = lesson.drillCards
        if (cardIndex >= drillCards.size) {
            finishDrill(lessonId)
            return
        }

        val card = drillCards[cardIndex]
        sessionCards = listOf(card)
        stateAccess.updateState {
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
        if (stateAccess.uiState.value.inputMode == InputMode.VOICE) {
            stateAccess.updateState { it.copy(voiceTriggerToken = it.voiceTriggerToken + 1) }
        }
    }

    fun advanceDrillCard() {
        val state = stateAccess.uiState.value
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

    fun finishDrill(lessonId: String) {
        drillProgressStore.clearDrillProgress(lessonId)
        stateAccess.updateState {
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
        onBuildSessionCards?.invoke()
        onRefreshFlowerStates?.invoke()
        onSaveProgress?.invoke()
    }

    fun exitDrillMode() {
        val state = stateAccess.uiState.value
        if (!state.isDrillMode) return
        pauseTimer()
        val lessonId = state.selectedLessonId
        if (lessonId != null && state.drillCardIndex > 0) {
            drillProgressStore.saveDrillProgress(lessonId, state.drillCardIndex)
        }
        stateAccess.updateState {
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
                voicePromptStartMs = null
            )
        }
        onBuildSessionCards?.invoke()
        onRefreshFlowerStates?.invoke()
        onSaveProgress?.invoke()
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
                stateAccess.updateState { it.copy(activeTimeMs = it.activeTimeMs + elapsed) }
                activeStartMs = SystemClock.elapsedRealtime()
                onSaveProgress?.invoke()
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
        val cards = stateAccess.uiState.value.lessons.flatMap { it.cards }
        if (cards.isEmpty()) return emptyList()
        val eliteSize = kotlin.math.ceil(subLessonSize * eliteSizeMultiplier).toInt()
        return cards.shuffled().take(eliteSize)
    }

    private fun updateWordBank() {
        val card = stateAccess.uiState.value.currentCard
        if (card == null) {
            stateAccess.updateState {
                it.copy(
                    wordBankWords = emptyList(),
                    selectedWords = emptyList()
                )
            }
            return
        }

        val correctAnswer = card.acceptedAnswers.firstOrNull() ?: ""
        val allCards = stateAccess.uiState.value.lessons.flatMap { it.cards }
        val wordBank = wordBankGenerator.generateForSentence(correctAnswer, allCards)

        stateAccess.updateState {
            it.copy(
                wordBankWords = wordBank,
                selectedWords = emptyList(),
                inputText = ""
            )
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
