package com.alexpo.grammermate.feature.training

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.CardSessionStateModel
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.PracticeType
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionProgress
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingScreenMode
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
 * timer, word bank interaction, and elite sub-mode.
 *
 * Query-style operations (getMastery, getSchedule, calculateCompletedSubLessons)
 * are injected as constructor function parameters.
 * Command-style results are returned as [List]<[SessionEvent]> for the ViewModel to execute.
 * Timer-triggered saveProgress is injected as a constructor function parameter.
 *
 * Uses [TrainingStateAccess] for state reads/writes.
 *
 * Implements [CardSessionStateModel] for unified state queries across all
 * card session types (training, verb drill, daily practice).
 */
class SessionRunner(
    private val stateAccess: TrainingStateAccess,
    private val appContext: Application,
    private val coroutineScope: CoroutineScope,
    private val answerValidator: AnswerValidator,
    private val wordBankGenerator: WordBankGenerator,
    private val cardProvider: CardProvider,
    private val streakManager: StreakManager,
    private val getMastery: (String, String) -> LessonMasteryState?,
    private val getSchedule: (String) -> LessonSchedule?,
    private val calculateCompletedSubLessons: (List<ScheduledSubLesson>, LessonMasteryState?, String?) -> Int,
    private val onTimerSaveProgress: () -> Unit,
    private val sessionTimerMsSink: ((Long) -> Unit)? = null
) : CardSessionStateModel {
    private val logTag = "SessionRunner"

    // ── Retry/hint state machine (shared with VerbDrill, DailyPractice) ──

    private val stateMachine = CardSessionStateMachine(maxAttempts = AnswerValidator.HINT_THRESHOLD) { card ->
        card.acceptedAnswers.joinToString(" / ")
    }

    // ── Private mutable state ───────────────────────────────────────────

    private var sessionCards: List<SessionCard> = emptyList()
    private var bossCards: List<SessionCard> = emptyList()
    private var eliteCards: List<SessionCard> = emptyList()
    private var timerJob: Job? = null
    private var activeStartMs: Long? = null
    private var timerTickCounter = 0
    private var lastSaveActiveTimeMs: Long = 0L

    private var subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
    private val eliteStepCount = TrainingConfig.ELITE_STEP_COUNT
    private var eliteSizeMultiplier: Double = TrainingConfig.ELITE_SIZE_MULTIPLIER

    // ── Session classification properties ──────────────────────────────────

    /**
     * Linear sessions have no sub-lessons, boss battles, or elite rounds.
     * They share a single answer-handling and completion path.
     */
    private val isLinearSession: Boolean
        get() = stateAccess.uiState.value.cardSession.screenMode in listOf(
            TrainingScreenMode.VERB_DRILL,
            TrainingScreenMode.DAILY_TRANSLATE,
            TrainingScreenMode.DAILY_VERBS
        )

    /**
     * Verb-style word banks use all session card answers as distractors.
     * Sentence-style word banks use lesson cards as distractors.
     * This is a session configuration, not business logic branching.
     */
    private val usesVerbWordBank: Boolean
        get() = stateAccess.uiState.value.cardSession.screenMode in listOf(
            TrainingScreenMode.VERB_DRILL,
            TrainingScreenMode.DAILY_VERBS
        )

    // ── CardSessionStateModel implementation ─────────────────────────────
    // Maps internal SessionState enum + stateMachine to the unified state model.

    override val isActive: Boolean
        get() = stateAccess.uiState.value.cardSession.sessionState == SessionState.ACTIVE
            && stateMachine.hintAnswer == null

    override val isPaused: Boolean
        get() = stateAccess.uiState.value.cardSession.sessionState == SessionState.PAUSED
            || stateMachine.isPaused

    override val isHintShown: Boolean
        get() = stateMachine.hintAnswer != null

    override val canSubmit: Boolean
        get() = stateAccess.uiState.value.cardSession.canSubmit

    override val hasCurrentCard: Boolean
        get() = currentCard() != null

    override val isComplete: Boolean
        get() = sessionCards.isEmpty() ||
            (stateAccess.uiState.value.cardSession.sessionState == SessionState.PAUSED &&
             (currentCard() == null ||
              // All cards in the session have been answered correctly
              stateAccess.uiState.value.cardSession.correctCount >= sessionCards.size))

    override val progress: SessionProgress
        get() {
            val state = stateAccess.uiState.value
            val total = sessionCards.size.coerceAtLeast(state.cardSession.subLessonTotal)
            val current = (state.cardSession.currentIndex + 1).coerceAtMost(total)
            return SessionProgress(current = current, total = total)
        }

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
        if (!state.boss.bossActive && !state.elite.eliteActive && !isLinearSession) {
            events.add(SessionEvent.BuildSessionCards)
        }
        // Auto-set currentCard from sessionCards if not set (for test compatibility and direct session starts)
        if (state.cardSession.currentCard == null && sessionCards.isNotEmpty()) {
            val firstCard = sessionCards.firstOrNull()
            stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(currentCard = firstCard, subLessonTotal = sessionCards.size)) }
        }
        if (sessionCards.isEmpty() || stateAccess.uiState.value.cardSession.currentCard == null) {
            pauseTimer()
            stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED)) }
            events.add(SessionEvent.SaveProgress)
            return events
        }
        pauseTimer()
        resumeTimer()
        stateMachine.reset()
        if (stateAccess.uiState.value.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.ACTIVE, inputText = it.cardSession.inputText, answerText = null, incorrectAttemptsForCard = 0, voiceTriggerToken = stateMachine.voiceTriggerToken, voicePromptStartMs = null))
        }
        // Populate word bank so the UI toggle is visible from the start.
        // Without this, regular lesson sessions start with wordBankWords=empty,
        // hiding the word bank toggle button (supportsWordBank checks isNotEmpty).
        updateWordBank()
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
        // Re-read state after pauseTimer() flush to get accurate activeTimeMs
        val flushedState = stateAccess.uiState.value
        val minutes = flushedState.cardSession.activeTimeMs / 60000.0
        val rating = if (minutes <= 0.0) 0.0 else flushedState.cardSession.correctCount / minutes
        val firstCard = sessionCards.firstOrNull()
        stateMachine.reset()
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
    fun currentCard(): SessionCard? {
        if (sessionCards.isEmpty()) return null
        val index = stateAccess.uiState.value.cardSession.currentIndex.coerceIn(0, sessionCards.lastIndex)
        return sessionCards.getOrNull(index)
    }

    // ── Input handling ──────────────────────────────────────────────────

    fun onInputChanged(text: String) {
        stateMachine.onInputChanged(text)
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(inputText = text, incorrectAttemptsForCard = stateMachine.incorrectAttempts, answerText = stateMachine.hintAnswer))
        }
    }

    fun onVoicePromptStarted() {
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(voicePromptStartMs = SystemClock.elapsedRealtime()))
        }
    }

    fun setInputMode(mode: InputMode) {
        // Pomodoro hint level guard: auto-switch to VOICE when Word Bank is unavailable
        if (mode == InputMode.WORD_BANK && stateAccess.uiState.value.cardSession.hintLevel != HintLevel.EASY) {
            stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(inputMode = InputMode.VOICE)) }
            return
        }
        // If hint was shown, typing/clearing should reset hint state via stateMachine
        if (stateMachine.hintAnswer != null) {
            stateMachine.onInputChanged("x") // force clear hint
            stateMachine.reset()
        }
        val shouldTriggerVoice = mode == InputMode.VOICE &&
            stateAccess.uiState.value.cardSession.sessionState == SessionState.ACTIVE
        if (shouldTriggerVoice) {
            stateMachine.triggerVoice()
        }
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(inputMode = mode, incorrectAttemptsForCard = stateMachine.incorrectAttempts, answerText = stateMachine.hintAnswer, voiceTriggerToken = stateMachine.voiceTriggerToken, voicePromptStartMs = if (mode == InputMode.VOICE) it.cardSession.voicePromptStartMs else null))
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
    /** Dedup guard: prevents double-submit within short time window (race from two speech launchers). */
    private var lastSubmitInputText = ""
    private var lastSubmitTimeMs = 0L

    fun submitAnswer(): Pair<SubmitResult, List<SessionEvent>> {
        val state = stateAccess.uiState.value
        val inputText = state.cardSession.inputText
        // Dedup: skip if same text submitted within 300ms (two speech launchers race)
        val now = SystemClock.elapsedRealtime()
        if (inputText == lastSubmitInputText && now - lastSubmitTimeMs < 300) {
            Log.w(logTag, "submitAnswer() DEDUP skipped: same text='$inputText' within ${now - lastSubmitTimeMs}ms")
            return SubmitResult(false, false, needsSaveProgress = false) to emptyList()
        }
        lastSubmitInputText = inputText
        lastSubmitTimeMs = now
        if (inputText.isBlank() && !state.cardSession.testMode) return SubmitResult(false, false, needsSaveProgress = false) to emptyList()
        val card = currentCard() ?: return SubmitResult(false, false, needsSaveProgress = false) to emptyList()
        val validationResult = answerValidator.validate(inputText, card.acceptedAnswers, state.cardSession.testMode, state.cardSession.inputMode)
        val accepted = validationResult.isCorrect
        val normalizedAnswers = card.acceptedAnswers.flatMap { it.split("+") }.map {
            if (state.cardSession.inputMode == InputMode.VOICE) Normalizer.normalizeForVoice(it) else Normalizer.normalize(it)
        }
        Log.d(logTag, "Answer check: input='$inputText', normalized='${validationResult.normalizedInput}', normalizedAnswers=$normalizedAnswers, mode=${state.cardSession.inputMode}, accepted=$accepted")
        val voiceStartMs = if (state.cardSession.inputMode == InputMode.VOICE) state.cardSession.voicePromptStartMs else null
        val voiceDurationMs = voiceStartMs?.let { SystemClock.elapsedRealtime() - it }
        val voiceWords = if (voiceStartMs != null) countMetricWords(state.cardSession.inputText) else 0
        val shouldAddVoiceMetrics = accepted && voiceDurationMs != null && voiceWords > 0
        var hintShown = false

        if (accepted) {
            val events = mutableListOf<SessionEvent>(SessionEvent.PlaySuccess)

            // Correct answer while not ACTIVE: resume to ACTIVE so the normal advance logic runs.
            // Both PAUSED and HINT_SHOWN states allow submission via canSubmit, and the user
            // expects correct answers to advance to the next card regardless of session state.
            if (state.cardSession.sessionState != SessionState.ACTIVE) {
                stateMachine.reset()
                resumeTimer()
                stateAccess.updateState {
                    it.copy(cardSession = it.cardSession.copy(
                        sessionState = SessionState.ACTIVE,
                        incorrectAttemptsForCard = 0,
                        answerText = null,
                        voicePromptStartMs = null
                    ))
                }
            }

            events.add(SessionEvent.RecordCardShow(card))
            val isLastCard = state.cardSession.currentIndex >= sessionCards.lastIndex

            val result = when {
                isLinearSession -> {
                    submitLinearSessionAnswer(shouldAddVoiceMetrics, voiceDurationMs, voiceWords, isLastCard)
                }
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
            val smResult = stateMachine.onSubmit(
                isCorrect = false,
                card = card,
                inputMode = state.cardSession.inputMode
            )
            when (smResult) {
                is CardSessionStateMachine.OnSubmitResult.HintShown -> {
                    hintShown = true
                    stateAccess.updateState {
                        it.copy(cardSession = it.cardSession.copy(
                            incorrectCount = it.cardSession.incorrectCount + 1,
                            incorrectAttemptsForCard = 0,
                            lastResult = false,
                            answerText = smResult.answer,
                            inputText = it.cardSession.inputText,
                            sessionState = SessionState.HINT_SHOWN,
                            voicePromptStartMs = null
                        ))
                    }
                    pauseTimer()
                }
                is CardSessionStateMachine.OnSubmitResult.Wrong -> {
                    stateAccess.updateState {
                        it.copy(cardSession = it.cardSession.copy(
                            incorrectCount = it.cardSession.incorrectCount + 1,
                            incorrectAttemptsForCard = stateMachine.incorrectAttempts,
                            lastResult = false,
                            inputText = it.cardSession.inputText,
                            voiceTriggerToken = stateMachine.voiceTriggerToken,
                            voicePromptStartMs = null
                        ))
                    }
                }
                is CardSessionStateMachine.OnSubmitResult.Correct -> {
                    // Should not happen when accepted=false, but handle defensively
                    stateAccess.updateState {
                        it.copy(cardSession = it.cardSession.copy(
                            incorrectCount = it.cardSession.incorrectCount + 1,
                            lastResult = false
                        ))
                    }
                }
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
        stateMachine.reset()
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
        stateMachine.reset()
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
        stateMachine.reset()
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
     * Normal mode: last card of sub-lesson. Pause timer, mark completion,
     * signal ViewModel to orchestrate cross-module updates.
     */
    private fun submitNormalLastCard(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int
    ): Pair<SubmitResult, List<SessionEvent>> {
        pauseTimer()
        stateMachine.reset()
        stateAccess.updateState {
            val nextCompleted = (it.cardSession.completedSubLessonCount + 1).coerceAtMost(it.cardSession.subLessonCount)
            val lessonId = it.navigation.selectedLessonId
            val mastery = lessonId?.let { id -> it.navigation.selectedLanguageId?.let { langId -> getMastery(id.value, langId.value) } }
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
        stateMachine.reset()
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(correctCount = it.cardSession.correctCount + 1, lastResult = true, incorrectAttemptsForCard = 0, answerText = null, voiceActiveMs = if (shouldAddVoiceMetrics) it.cardSession.voiceActiveMs + (voiceDurationMs ?: 0L) else it.cardSession.voiceActiveMs, voiceWordCount = if (shouldAddVoiceMetrics) it.cardSession.voiceWordCount + voiceWords else it.cardSession.voiceWordCount, voicePromptStartMs = null))
        }
        // nextCard handles voice trigger, word bank, save
        nextCardInternal(triggerVoice = state.cardSession.inputMode == InputMode.VOICE)
        return SubmitResult(accepted = true, hintShown = false, needsSaveProgress = false, needsFlowerRefresh = true)
    }

    /**
     * Linear session (VERB_DRILL / DAILY_TRANSLATE / DAILY_VERBS): correct answer.
     * Advance to next card or signal completion without lesson-level mechanics.
     */
    private fun submitLinearSessionAnswer(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int,
        isLastCard: Boolean
    ): SubmitResult {
        stateMachine.reset()
        if (isLastCard) {
            pauseTimer()
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(
                    currentCard = null,
                    correctCount = it.cardSession.correctCount + 1,
                    lastResult = null,
                    incorrectAttemptsForCard = 0,
                    answerText = null,
                    voiceActiveMs = if (shouldAddVoiceMetrics) it.cardSession.voiceActiveMs + (voiceDurationMs ?: 0L) else it.cardSession.voiceActiveMs,
                    voiceWordCount = if (shouldAddVoiceMetrics) it.cardSession.voiceWordCount + voiceWords else it.cardSession.voiceWordCount,
                    voicePromptStartMs = null,
                    sessionState = SessionState.PAUSED,
                    subLessonFinishedToken = it.cardSession.subLessonFinishedToken + 1
                ))
            }
            return SubmitResult(accepted = true, hintShown = false, needsSubLessonComplete = true, needsSaveProgress = true)
        } else {
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(
                    correctCount = it.cardSession.correctCount + 1,
                    lastResult = null,
                    incorrectAttemptsForCard = 0,
                    answerText = null,
                    inputText = "",
                    voiceActiveMs = if (shouldAddVoiceMetrics) it.cardSession.voiceActiveMs + (voiceDurationMs ?: 0L) else it.cardSession.voiceActiveMs,
                    voiceWordCount = if (shouldAddVoiceMetrics) it.cardSession.voiceWordCount + voiceWords else it.cardSession.voiceWordCount,
                    voicePromptStartMs = null,
                    sessionState = SessionState.ACTIVE
                ))
            }
            advanceLinearCard()
            return SubmitResult(accepted = true, hintShown = false, needsSaveProgress = false)
        }
    }

    /**
     * Advance to the next card in a linear session (VERB_DRILL / DAILY_TRANSLATE / DAILY_VERBS).
     * Similar to drill card advance but does not use lesson-based drill progress store.
     */
    private fun advanceLinearCard() {
        val state = stateAccess.uiState.value
        val nextIndex = state.cardSession.currentIndex + 1
        val nextCard = sessionCards.getOrNull(nextIndex)
        stateMachine.reset()
        if (state.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(
                currentIndex = nextIndex,
                currentCard = nextCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = stateMachine.incorrectAttempts,
                sessionState = if (nextCard != null) SessionState.ACTIVE else SessionState.PAUSED,
                voiceTriggerToken = stateMachine.voiceTriggerToken,
                voicePromptStartMs = null
            ))
        }
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
        val wasHintShown = state.cardSession.sessionState == SessionState.HINT_SHOWN || stateMachine.hintAnswer != null
        stateMachine.reset()
        val lastIndex = sessionCards.lastIndex
        val isOnLastCard = state.cardSession.currentIndex >= lastIndex && lastIndex >= 0

        // Linear sessions: on last card, signal completion without lesson-level mechanics
        if (isOnLastCard && isLinearSession) {
            pauseTimer()
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(
                    currentCard = null,
                    lastResult = null,
                    incorrectAttemptsForCard = 0,
                    answerText = null,
                    voicePromptStartMs = null,
                    sessionState = SessionState.PAUSED,
                    subLessonFinishedToken = it.cardSession.subLessonFinishedToken + 1
                ))
            }
            Log.d(logTag, "LinearSession: Next pressed on last card — signaling completion")
            return listOf(SessionEvent.SaveProgress)
        }

        // Fix 3: If already on the last card, trigger sub-lesson completion instead of staying put
        if (isOnLastCard && !state.boss.bossActive && !state.elite.eliteActive) {
            pauseTimer()
            stateAccess.updateState {
                val nextCompleted = (it.cardSession.completedSubLessonCount + 1).coerceAtMost(it.cardSession.subLessonCount)
                val lessonId = it.navigation.selectedLessonId
                val mastery = lessonId?.let { id -> it.navigation.selectedLanguageId?.let { langId -> getMastery(id.value, langId.value) } }
                val schedule = lessonId?.let { id -> getSchedule(id.value) }
                val subLessons = schedule?.subLessons.orEmpty()
                val actualCompletedCount = calculateCompletedSubLessons(subLessons, mastery, lessonId?.value)
                val preservedActiveIndex = maxOf(it.cardSession.activeSubLessonIndex, actualCompletedCount)
                val finalActiveIndex = preservedActiveIndex.coerceAtMost((it.cardSession.subLessonCount - 1).coerceAtLeast(0))

                it.copy(cardSession = it.cardSession.copy(
                    lastResult = null,
                    incorrectAttemptsForCard = 0,
                    answerText = null,
                    voicePromptStartMs = null,
                    sessionState = SessionState.PAUSED,
                    currentIndex = 0,
                    activeSubLessonIndex = finalActiveIndex,
                    completedSubLessonCount = maxOf(nextCompleted, actualCompletedCount),
                    subLessonFinishedToken = it.cardSession.subLessonFinishedToken + 1
                ))
            }
            Log.d(logTag, "Next pressed on last card — triggering sub-lesson completion")
            return listOf(
                SessionEvent.MarkSubLessonCardsShown(sessionCards),
                SessionEvent.BuildSessionCards,
                SessionEvent.CheckAndMarkLessonCompleted,
                SessionEvent.RefreshFlowerStates,
                SessionEvent.UpdateStreak,
                SessionEvent.SaveProgress
            )
        }

        val nextIndex = (state.cardSession.currentIndex + 1).coerceAtMost(lastIndex)
        val nextCard = sessionCards.getOrNull(nextIndex)

        if (triggerVoice && stateAccess.uiState.value.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(currentIndex = nextIndex, currentCard = nextCard, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = stateMachine.incorrectAttempts, sessionState = SessionState.ACTIVE, voiceTriggerToken = stateMachine.voiceTriggerToken, voicePromptStartMs = null))
        }
        val events = mutableListOf<SessionEvent>()
        // Boss progress: trigger update in orchestrator
        if (state.boss.bossActive) {
            events.add(SessionEvent.AdvanceBossProgress(nextIndex, sessionCards.size))
        }
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
        stateMachine.reset()
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(currentIndex = prevIndex, currentCard = prevCard, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0, voicePromptStartMs = null))
        }
        val events = mutableListOf<SessionEvent>()
        events.add(SessionEvent.SaveProgress)
        return events
    }

    /**
     * Navigate to the next card, pausing first if the session is ACTIVE.
     * Used by UI navigation arrows — always leaves the session in PAUSED state
     * so the user can browse cards without timer pressure.
     * Pressing Play resumes the session.
     */
    fun navigateNext(): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        // Pause first if ACTIVE
        if (stateAccess.uiState.value.cardSession.sessionState == SessionState.ACTIVE) {
            events.addAll(pauseSession())
        }
        // Advance card but leave PAUSED
        stateMachine.reset()
        val state = stateAccess.uiState.value
        val nextIndex = (state.cardSession.currentIndex + 1).coerceAtMost(sessionCards.lastIndex)
        val nextCard = sessionCards.getOrNull(nextIndex)
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(currentIndex = nextIndex, currentCard = nextCard, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0, sessionState = SessionState.PAUSED, voicePromptStartMs = null))
        }
        // Update word bank if in WORD_BANK mode
        if (stateAccess.uiState.value.cardSession.inputMode == InputMode.WORD_BANK) {
            updateWordBank()
        }

        events.add(SessionEvent.SaveProgress)
        return events
    }

    /**
     * Navigate to the previous card, pausing first if the session is ACTIVE.
     * Used by UI navigation arrows — always leaves the session in PAUSED state.
     */
    fun navigatePrev(): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        // Pause first if ACTIVE
        if (stateAccess.uiState.value.cardSession.sessionState == SessionState.ACTIVE) {
            events.addAll(pauseSession())
        }
        // Go back but leave PAUSED
        stateMachine.reset()
        val prevIndex = (stateAccess.uiState.value.cardSession.currentIndex - 1).coerceAtLeast(0)
        val prevCard = sessionCards.getOrNull(prevIndex)
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(currentIndex = prevIndex, currentCard = prevCard, inputText = "", lastResult = null, answerText = null, incorrectAttemptsForCard = 0, sessionState = SessionState.PAUSED, voicePromptStartMs = null))
        }
        events.add(SessionEvent.SaveProgress)
        return events
    }

    fun selectSubLesson(index: Int): List<SessionEvent> {
        pauseTimer()
        stateMachine.reset()
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(activeSubLessonIndex = index.coerceAtLeast(0), currentIndex = 0, inputText = "", lastResult = null, answerText = null, sessionState = SessionState.PAUSED))
        }
        return listOf(SessionEvent.BuildSessionCards, SessionEvent.SaveProgress)
    }

    // ── Pause/resume ────────────────────────────────────────────────────

    fun togglePause(): List<SessionEvent> {
        val state = stateAccess.uiState.value
        if (state.cardSession.sessionState == SessionState.ACTIVE) {
            // Active → pause
            stateMachine.pause()
            pauseTimer()
            stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, voicePromptStartMs = null)) }
            return listOf(SessionEvent.SaveProgress)
        }
        // Paused/hint → resume or advance
        if (stateMachine.hintAnswer != null) {
            // Hint was shown → play button clears hint, resumes ACTIVE on same card.
            // User must press explicit Next button (ArrowForward) to advance.
            // This prevents auto-advance after 3 incorrect retries.
            stateMachine.reset()
            if (state.cardSession.inputMode == InputMode.VOICE) {
                stateMachine.triggerVoice()
            }
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(
                    sessionState = SessionState.ACTIVE,
                    incorrectAttemptsForCard = 0,
                    answerText = null,
                    inputText = it.cardSession.inputText,
                    voiceTriggerToken = stateMachine.voiceTriggerToken,
                    voicePromptStartMs = null
                ))
            }
            resumeTimer()
            return listOf(SessionEvent.SaveProgress)
        }
        // Manual pause → resume
        stateMachine.resume()
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
        val answer = stateMachine.showAnswer(card)
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(answerText = answer, sessionState = SessionState.HINT_SHOWN, inputText = it.cardSession.inputText, hintCount = it.cardSession.hintCount + 1, incorrectAttemptsForCard = stateMachine.incorrectAttempts, voicePromptStartMs = null))
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
            stateMachine.reset()
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
        stateMachine.reset()
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
        stateMachine.reset()
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

    // ── Unified card session start/exit ────────────────────────────────────

    /**
     * Start a card session with the given cards and screen mode.
     * Unified implementation for verb drill, daily translate, and daily verbs sessions.
     * Loads cards into [sessionCards], resets all session state, activates the session,
     * and sets [screenMode] to the given mode.
     *
     * @param cards The cards to practice.
     * @param mode  The [TrainingScreenMode] to set (VERB_DRILL, DAILY_TRANSLATE, DAILY_VERBS).
     * @return List of [SessionEvent] to process.
     */
    fun startCardSession(cards: List<SessionCard>, mode: TrainingScreenMode): List<SessionEvent> {
        pauseTimer()
        stateMachine.reset()
        sessionCards = cards
        val firstCard = cards.firstOrNull()
        if (stateAccess.uiState.value.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        stateAccess.updateState {
            it.copy(
                cardSession = it.cardSession.copy(
                    sessionState = if (firstCard != null) SessionState.ACTIVE else SessionState.PAUSED,
                    currentCard = firstCard,
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
                    subLessonTotal = cards.size,
                    subLessonCount = 1,
                    activeSubLessonIndex = 0,
                    completedSubLessonCount = 0,
                    subLessonFinishedToken = 0,
                    wordBankWords = emptyList(),
                    selectedWords = emptyList(),
                    screenMode = mode,
                    voiceTriggerToken = stateMachine.voiceTriggerToken,
                    verbConjugationCards = if (mode == TrainingScreenMode.VERB_DRILL || mode == TrainingScreenMode.DAILY_VERBS) {
                        cards.filterIsInstance<VerbDrillCard>()
                    } else emptyList()
                ),
                boss = it.boss.copy(bossActive = false),
                elite = it.elite.copy(eliteActive = false)
            )
        }
        if (firstCard != null) {
            resumeTimer()
            // Populate word bank unconditionally.
            // Without this, daily translate sessions carry over WORD_BANK input mode
            // from a previous session but never call updateWordBank() (which is only
            // triggered via setInputMode/nextCard/navigateNext).
            updateWordBank()
        }
        return listOf(SessionEvent.SaveProgress)
    }

    /**
     * Exit a card session: reset session state, clear cards, and reset screenMode to NORMAL.
     * Unified implementation for verb drill and daily practice exit.
     *
     * @return List of [SessionEvent] to process.
     */
    fun exitCardSession(): List<SessionEvent> {
        pauseTimer()
        stateMachine.reset()
        sessionCards = emptyList()
        stateAccess.updateState {
            it.copy(
                cardSession = it.cardSession.copy(
                    sessionState = SessionState.PAUSED,
                    currentCard = null,
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
                    subLessonTotal = 0,
                    screenMode = TrainingScreenMode.NORMAL
                )
            )
        }
        return listOf(SessionEvent.SaveProgress)
    }

    // ── Card session wrappers (delegate to unified methods) ──────────────

    /** Start a verb drill session. Delegates to [startCardSession]. */
    fun startVerbDrillSession(cards: List<SessionCard>) = startCardSession(cards, TrainingScreenMode.VERB_DRILL)

    /** Start a daily practice translation session (block 1). Delegates to [startCardSession]. */
    fun startDailyTranslateSession(cards: List<SessionCard>) = startCardSession(cards, TrainingScreenMode.DAILY_TRANSLATE)

    /** Start a daily practice verb conjugation session (block 3). Delegates to [startCardSession]. */
    fun startDailyVerbsSession(cards: List<SessionCard>) = startCardSession(cards, TrainingScreenMode.DAILY_VERBS)

    /**
     * Start a lesson review session with the given cards and difficulty level.
     * Resets all session state, sets [hintLevel], and loads cards for review.
     * Mastery tracking works normally (VOICE/KEYBOARD count).
     *
     * @param cards     All lesson cards (shuffled, filtered by hidden).
     * @param hintLevel The difficulty level for this review session.
     * @return List of [SessionEvent] to process.
     */
    fun startReview(cards: List<SessionCard>, hintLevel: HintLevel): List<SessionEvent> {
        pauseTimer()
        stateMachine.reset()
        sessionCards = cards
        val firstCard = cards.firstOrNull()
        val defaultInputMode = when (hintLevel) {
            HintLevel.EASY -> InputMode.WORD_BANK
            HintLevel.MEDIUM -> InputMode.KEYBOARD
            HintLevel.HARD -> InputMode.VOICE
        }
        if (defaultInputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        stateAccess.updateState {
            it.copy(
                cardSession = it.cardSession.copy(
                    sessionState = if (firstCard != null) SessionState.ACTIVE else SessionState.PAUSED,
                    currentCard = firstCard,
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
                    subLessonTotal = cards.size,
                    subLessonCount = 1,
                    activeSubLessonIndex = 0,
                    completedSubLessonCount = 0,
                    subLessonFinishedToken = 0,
                    wordBankWords = emptyList(),
                    selectedWords = emptyList(),
                    screenMode = TrainingScreenMode.NORMAL,
                    isReviewMode = true,
                    hintLevel = hintLevel,
                    inputMode = defaultInputMode,
                    voiceTriggerToken = stateMachine.voiceTriggerToken,
                    returnTo = ""
                ),
                boss = it.boss.copy(bossActive = false),
                elite = it.elite.copy(eliteActive = false)
            )
        }
        if (firstCard != null) {
            resumeTimer()
            updateWordBank()
            return listOf(SessionEvent.RecordCardShow(firstCard), SessionEvent.SaveProgress)
        }
        return listOf(SessionEvent.SaveProgress)
    }

    /**
     * Replace cards in the active card session without exiting.
     * Used by "Ещё" button to load the next batch of verb drill cards
     * without leaving TrainingScreen.
     *
     * Resets card navigation state (index, counts, input) but preserves
     * screenMode and other session context.
     *
     * @param cards The new cards to load.
     * @return List of [SessionEvent] to process.
     */
    fun replaceCards(cards: List<SessionCard>): List<SessionEvent> {
        pauseTimer()
        stateMachine.reset()
        sessionCards = cards
        val firstCard = cards.firstOrNull()
        if (stateAccess.uiState.value.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        stateAccess.updateState {
            it.copy(
                cardSession = it.cardSession.copy(
                    sessionState = if (firstCard != null) SessionState.ACTIVE else SessionState.PAUSED,
                    currentCard = firstCard,
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
                    subLessonTotal = cards.size,
                    subLessonFinishedToken = 0,
                    wordBankWords = emptyList(),
                    selectedWords = emptyList(),
                    voiceTriggerToken = stateMachine.voiceTriggerToken,
                    verbConjugationCards = if (it.cardSession.screenMode == TrainingScreenMode.VERB_DRILL || it.cardSession.screenMode == TrainingScreenMode.DAILY_VERBS) {
                        cards.filterIsInstance<VerbDrillCard>()
                    } else emptyList()
                )
            )
        }
        if (firstCard != null) {
            resumeTimer()
            updateWordBank()
        }
        return listOf(SessionEvent.SaveProgress)
    }

    /** Exit verb drill mode. Delegates to [exitCardSession]. */
    fun exitVerbDrillSession() = exitCardSession()

    /** Exit daily practice session. Delegates to [exitCardSession]. */
    fun exitDailySession() = exitCardSession()

    // ── Card list management (called by ViewModel) ─────────────────────

    /**
     * Set the session cards list. Called by ViewModel after CardProvider builds them.
     */
    fun setSessionCards(cards: List<SentenceCard>) {
        sessionCards = cards
    }

    fun setSessionCardsGeneric(cards: List<SessionCard>) {
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

    fun getSessionCards(): List<SessionCard> = sessionCards

    fun clearAllCards() {
        sessionCards = emptyList()
        bossCards = emptyList()
        eliteCards = emptyList()
    }

    fun setEliteSizeMultiplier(multiplier: Double) {
        eliteSizeMultiplier = multiplier
    }

    fun setSubLessonSize(size: Int) {
        subLessonSize = size
    }

    // ── Timer (private) ─────────────────────────────────────────────────

    fun resumeTimer() {
        if (timerJob?.isActive == true) return
        activeStartMs = SystemClock.elapsedRealtime()
        timerTickCounter = 0
        lastSaveActiveTimeMs = stateAccess.uiState.value.cardSession.activeTimeMs
        timerJob = coroutineScope.launch {
            while (true) {
                delay(500)
                val start = activeStartMs ?: continue
                val elapsed = SystemClock.elapsedRealtime() - start
                activeStartMs = SystemClock.elapsedRealtime()

                // Accumulate total active time internally
                lastSaveActiveTimeMs += elapsed

                // Push high-frequency timer value to separate flow (for UI display)
                sessionTimerMsSink?.invoke(lastSaveActiveTimeMs)

                // Push activeTimeMs to main state and save progress only every ~10s (20 ticks)
                timerTickCounter++
                if (timerTickCounter >= 20) {
                    timerTickCounter = 0
                    stateAccess.updateState {
                        it.copy(cardSession = it.cardSession.copy(activeTimeMs = lastSaveActiveTimeMs))
                    }
                    onTimerSaveProgress()
                }
            }
        }
    }

    fun pauseTimer() {
        // Flush accumulated time to main state before stopping
        if (lastSaveActiveTimeMs > 0 && timerJob?.isActive == true) {
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(activeTimeMs = lastSaveActiveTimeMs))
            }
        }
        timerJob?.cancel()
        timerJob = null
        activeStartMs = null
        timerTickCounter = 0
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private fun buildEliteCards(): List<SessionCard> {
        val cards = stateAccess.uiState.value.navigation.lessons.flatMap { it.cards }
        if (cards.isEmpty()) return emptyList()
        val eliteSize = kotlin.math.ceil(subLessonSize * eliteSizeMultiplier).toInt()
        return cards.shuffled().take(eliteSize)
    }

    fun updateWordBank() {
        val state = stateAccess.uiState.value
        // Pomodoro hint level guard: Word Bank only available at EASY level
        if (state.cardSession.hintLevel != HintLevel.EASY) return
        val card = state.cardSession.currentCard
        if (card == null) {
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(wordBankWords = emptyList(), selectedWords = emptyList()))
            }
            return
        }

        val correctAnswer = card.acceptedAnswers.firstOrNull() ?: ""
        val wordBank = if (usesVerbWordBank) {
            // Verb-style word bank: use all session card answers as distractor pool
            val allAnswers = sessionCards.mapNotNull { it.acceptedAnswers.firstOrNull() }
            wordBankGenerator.generateForVerb(correctAnswer, allAnswers)
        } else {
            val allCards = state.navigation.lessons.flatMap { it.cards }
            wordBankGenerator.generateForSentence(correctAnswer, allCards)
        }

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
