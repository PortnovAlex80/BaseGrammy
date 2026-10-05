package com.alexpo.grammermate.feature.training

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.CompletionNextAction
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
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.TrainingStateAccess
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.shared.AuditLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Session management module extracted from TrainingViewModel.
 *
 * Public facade and answer owner. [SessionCardSessions]/[SessionEliteController]
 * own entry/exit; [SessionInputController] input; [SessionDeck] cards;
 * [SessionNavigator] movement; [SessionWordBank] compatibility; [SessionActiveTimer] clock.
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
    wordBankGenerator: WordBankGenerator,
    private val cardProvider: CardProvider,
    private val streakManager: StreakManager,
    private val getMastery: (String, String) -> LessonMasteryState?,
    private val getSchedule: (String) -> LessonSchedule?,
    private val getHiddenCardIds: () -> Set<String>,
    private val calculateCompletedSubLessons: (List<ScheduledSubLesson>, LessonMasteryState?, String?, Set<String>) -> Int,
    private val onTimerSaveProgress: () -> Unit,
    private val sessionTimerMsSink: ((Long) -> Unit)? = null,
    /**
     * Single source of truth for the typed answer (TASK-091 item 4). A
     * dedicated flow, like [sessionTimerMsSink]: per-keystroke input must not
     * flow through the combined uiState (it recomposed the whole NavHost on
     * every character). The ViewModel passes its own flow so UI and runner
     * share one value; the default keeps tests standalone.
     */
    private val inputTextFlow: MutableStateFlow<String> = MutableStateFlow(""),
    submitClockMs: () -> Long = SystemClock::elapsedRealtime
) : CardSessionStateModel {
    private val logTag = "SessionRunner"
    private val audit get() = AuditLogger.getInstanceOrNull()

    // ── Retry/hint state machine (shared with VerbDrill, DailyPractice) ──

    private val stateMachine = CardSessionStateMachine(maxAttempts = AnswerValidator.HINT_THRESHOLD) { card ->
        card.acceptedAnswers.firstOrNull() ?: ""
    }

    /** Clear the typed answer together with a card-transition state update
     *  (mirrors the old `inputText = ""` argument of those copies). */
    private fun updateStateAndClearInput(transform: (com.alexpo.grammermate.data.TrainingUiState) -> com.alexpo.grammermate.data.TrainingUiState) {
        inputTextFlow.value = ""
        stateAccess.updateState(transform)
    }

    // ── Private mutable state ───────────────────────────────────────────
    private val deck = SessionDeck(submitClockMs)
    private val activeCards: List<SessionCard> get() = deck.cards
    private fun assignSessionCards(cards: List<SessionCard>) = deck.replace(cards)
    private val activeTimer = SessionActiveTimer(
        scope = coroutineScope,
        readPersistedMs = { stateAccess.currentState.cardSession.activeTimeMs },
        writePersistedMs = { activeTimeMs ->
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(activeTimeMs = activeTimeMs))
            }
        },
        onDisplayTick = sessionTimerMsSink,
        onCheckpoint = onTimerSaveProgress
    )
    private val wordBank = SessionWordBank(stateAccess, inputTextFlow, wordBankGenerator) { activeCards }
    private val inputController = SessionInputController(stateAccess, stateMachine, inputTextFlow, wordBank, ::currentCard)
    private val navigator = SessionNavigator(
        stateAccess, stateMachine, { activeCards }, activeTimer, inputTextFlow, wordBank,
        ::actualCompletedSubLessonCount
    )
    private val cardSessions = SessionCardSessions(stateAccess, stateMachine, deck, activeTimer, inputTextFlow, wordBank)
    private val eliteController = SessionEliteController(stateAccess, stateMachine, deck, activeTimer, inputTextFlow)


    // ── Session classification properties ──────────────────────────────────

    /**
     * Linear sessions have no sub-lessons, boss battles, or elite rounds.
     * They share a single answer-handling and completion path.
     */
    private val isLinearSession: Boolean
        get() = LinearSessionTransitions.handles(stateAccess.currentState.cardSession.screenMode)

    // ── CardSessionStateModel implementation ─────────────────────────────
    // Maps internal SessionState enum + stateMachine to the unified state model.

    override val isActive: Boolean
        get() = stateAccess.currentState.cardSession.sessionState == SessionState.ACTIVE
            && stateMachine.hintAnswer == null

    override val isPaused: Boolean
        get() = stateAccess.currentState.cardSession.sessionState == SessionState.PAUSED
            || stateMachine.isPaused

    override val isHintShown: Boolean
        get() = stateMachine.hintAnswer != null

    override val canSubmit: Boolean
        get() = stateAccess.currentState.cardSession.canSubmit

    override val hasCurrentCard: Boolean
        get() = currentCard() != null

    override val isComplete: Boolean
        get() = activeCards.isEmpty() ||
            (stateAccess.currentState.cardSession.sessionState == SessionState.PAUSED &&
             (currentCard() == null ||
              // All cards in the session have been answered correctly
              stateAccess.currentState.cardSession.correctCount >= activeCards.size))

    override val progress: SessionProgress
        get() {
            val state = stateAccess.currentState
            val total = activeCards.size.coerceAtLeast(state.cardSession.subLessonTotal)
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
        val needsSaveProgress: Boolean = false,
        val needsFlowerRefresh: Boolean = false
    )

    // ── Session lifecycle ───────────────────────────────────────────────

    /**
     * Start or resume an active training session.
     * Activates the timer and records the first card for mastery.
     */
    fun startSession(): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        val state = stateAccess.currentState
        if (!state.boss.bossActive && !state.elite.eliteActive && !isLinearSession) {
            events.add(SessionEvent.BuildSessionCards)
        }
        // Auto-set currentCard from activeCards if not set (for test compatibility and direct session starts)
        if (state.cardSession.currentCard == null && activeCards.isNotEmpty()) {
            val firstCard = activeCards.firstOrNull()
            stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(currentCard = firstCard, subLessonTotal = activeCards.size)) }
        }
        if (activeCards.isEmpty() || stateAccess.currentState.cardSession.currentCard == null) {
            pauseTimer()
            stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED)) }
            events.add(SessionEvent.SaveProgress)
            return events
        }
        pauseTimer()
        resumeTimer()
        stateMachine.reset()
        if (stateAccess.currentState.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.ACTIVE, answerText = null, incorrectAttemptsForCard = 0, voiceTriggerToken = stateMachine.voiceTriggerToken, voicePromptStartMs = null))
        }
        // Populate word bank so the UI toggle is visible from the start.
        // Without this, regular lesson sessions start with wordBankWords=empty,
        // hiding the word bank toggle button (supportsWordBank checks isNotEmpty).
        updateWordBank()
        // Audit: session started
        val startedState = stateAccess.currentState
        audit?.sessionStart(
            startedState.navigation.selectedLessonId?.value ?: "",
            startedState.cardSession.inputMode.name,
            startedState.cardSession.subLessonCount,
            startedState.cardSession.subLessonTotal,
            startedState.cardSession.screenMode.name
        )
        events.add(SessionEvent.SaveProgress)
        return events
    }

    /**
     * Finish the current training session.
     * Returns a [SessionFinishResult] for the ViewModel to apply.
     */
    fun finishSession(): Pair<SessionFinishResult, List<SessionEvent>> {
        if (activeCards.isEmpty()) return SessionFinishResult.Empty to emptyList()
        val state = stateAccess.currentState
        if (state.elite.eliteActive) {
            cancelEliteSession()
            return SessionFinishResult.EliteCancelled to emptyList()
        }
        pauseTimer()
        // Re-read state after pauseTimer() flush to get accurate activeTimeMs
        val flushedState = stateAccess.currentState
        val minutes = flushedState.cardSession.activeTimeMs / 60000.0
        val rating = if (minutes <= 0.0) 0.0 else flushedState.cardSession.correctCount / minutes
        val firstCard = activeCards.firstOrNull()
        stateMachine.reset()
        // Recalculate completedSubLessonCount for the lesson (not review/boss mode)
        // so LessonRoadmapScreen shows correct state after staircase navigation
        val lessonId = flushedState.navigation.selectedLessonId
        val langId = flushedState.navigation.selectedLanguageId
        val mastery = lessonId?.let { lid -> langId?.let { getMastery(lid.value, it.value) } }
        val schedule = lessonId?.let { getSchedule(it.value) }
        val subLessons = schedule?.subLessons.orEmpty()
        val hiddenIds = getHiddenCardIds()
        val rawCompleted = calculateCompletedSubLessons(subLessons, mastery, lessonId?.value, hiddenIds)
        val isMasteryCompleted = mastery?.completedAtMs != null
        val lessonCompletedCount = if (isMasteryCompleted) subLessons.size else rawCompleted
        updateStateAndClearInput {
            it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, lastRating = rating, incorrectAttemptsForCard = 0, lastResult = null, answerText = null, currentIndex = 0, currentCard = firstCard, voicePromptStartMs = null, completedSubLessonCount = lessonCompletedCount, activeSubLessonIndex = lessonCompletedCount.coerceAtMost((subLessons.size - 1).coerceAtLeast(0)), subLessonCount = subLessons.size, subLessonTypes = subLessons.map { sl -> sl.type }))
        }
        Log.d(logTag, "Session finished. Rating=$rating")
        // Audit: session ended
        audit?.sessionEnd(
            rating,
            flushedState.cardSession.correctCount,
            flushedState.cardSession.incorrectCount,
            flushedState.cardSession.activeTimeMs,
            flushedState.cardSession.hintCount
        )
        return SessionFinishResult.Completed(rating) to listOf(SessionEvent.SaveProgress, SessionEvent.RefreshFlowerStates)
    }

    fun resumeFromSettings(): List<SessionEvent> {
        val state = stateAccess.currentState
        if (state.cardSession.sessionState == SessionState.ACTIVE) return emptyList()
        return startSession()
    }

    /**
     * Get the current card based on index and session cards.
     */
    fun currentCard(): SessionCard? {
        return deck.current(stateAccess.currentState.cardSession.currentIndex)
    }

    // ── Input handling ──────────────────────────────────────────────────

    fun clearTypedInput() = inputController.clearTypedInput()

    fun onInputChanged(text: String) = inputController.onInputChanged(text)

    fun onVoicePromptStarted() = inputController.onVoicePromptStarted()

    fun setInputMode(mode: InputMode) = inputController.setInputMode(mode)

    // ── Answer submission ───────────────────────────────────────────────

    /**
     * Submit the current answer. Orchestrates validation, state updates,
     * and signals the ViewModel for cross-module actions via [SubmitResult].
     */
    fun submitAnswer(): Pair<SubmitResult, List<SessionEvent>> {
        val state = stateAccess.currentState
        val inputText = inputTextFlow.value
        // VOICE keeps cross-card duplicate suppression for the two speech launchers.
        if (deck.shouldSuppress(
                inputText, state.cardSession.inputMode,
                state.cardSession.currentIndex, currentCard()?.id
            )) {
            Log.w(logTag, "submitAnswer() DEDUP skipped: same text='$inputText' within 300ms")
            // Audit: dedup guard triggered
            currentCard()?.let { audit?.answerDedup(it.id) }
            return SubmitResult(false, false, needsSaveProgress = false) to emptyList()
        }
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
        val voiceWords = if (voiceStartMs != null) countMetricWords(inputText) else 0
        val shouldAddVoiceMetrics = accepted && voiceDurationMs != null && voiceWords > 0
        var hintShown = false

        if (accepted) {
            val events = mutableListOf<SessionEvent>(SessionEvent.PlaySuccess)

            // Audit: correct answer
            audit?.answerCorrect(
                card.id, inputText, validationResult.normalizedInput,
                card.promptRu, card.acceptedAnswers.firstOrNull() ?: "",
                state.cardSession.inputMode.name, stateMachine.incorrectAttempts
            )

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
            val isLastCard = state.cardSession.currentIndex >= activeCards.lastIndex

            val result = when {
                isLinearSession -> {
                    submitLinearSessionAnswer(shouldAddVoiceMetrics, voiceDurationMs, voiceWords, isLastCard)
                }
                state.boss.bossActive && isLastCard -> {
                    submitBossLastCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
                }
                state.boss.bossActive -> {
                    val (r, e) = submitBossMidCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords, state)
                    events.addAll(e)
                    r
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
                    val (r, e) = submitNormalMidCard(shouldAddVoiceMetrics, voiceDurationMs, voiceWords, state)
                    events.addAll(e)
                    r
                }
            }
            return result to events
        } else {
            val events = mutableListOf<SessionEvent>(SessionEvent.PlayError, SessionEvent.SaveProgress)
            // Audit: wrong answer
            audit?.answerWrong(
                card.id, inputText, validationResult.normalizedInput,
                card.promptRu, card.acceptedAnswers.firstOrNull() ?: "",
                state.cardSession.inputMode.name, stateMachine.incorrectAttempts + 1
            )
            val smResult = stateMachine.onSubmit(
                isCorrect = false,
                card = card,
                inputMode = state.cardSession.inputMode
            )
            if (smResult is CardSessionStateMachine.OnSubmitResult.HintShown) {
                hintShown = true
                audit?.hintAuto(
                    card.id, card.promptRu, card.acceptedAnswers.firstOrNull() ?: "",
                    stateMachine.incorrectAttempts, state.cardSession.hintCount + 1
                )
            }
            stateAccess.updateState {
                it.copy(cardSession = SessionAnswerTransitions.incorrect(
                    it.cardSession, smResult, stateMachine.incorrectAttempts, stateMachine.voiceTriggerToken
                ))
            }
            if (hintShown) pauseTimer()
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
        val voice = SessionAnswerTransitions.VoiceContribution.from(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
        stateAccess.updateState {
            it.copy(cardSession = SessionAnswerTransitions.bossLast(it.cardSession, voice))
        }
        return SubmitResult(
            accepted = true,
            hintShown = false,
            needsBossFinish = true,
            needsFlowerRefresh = true
        )
    }

    /**
     * Boss mode: mid-session correct answer. Advance to next card.
     *
     * Returns the events emitted by [nextCard] (notably
     * [SessionEvent.AdvanceBossProgress]) so the ViewModel can advance the
     * boss progress field the progress bar reads. Dropping these events
     * leaves the boss progress bar frozen at "1 / N".
     */
    private fun submitBossMidCard(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int,
        state: com.alexpo.grammermate.data.TrainingUiState
    ): Pair<SubmitResult, List<SessionEvent>> {
        stateMachine.reset()
        val voice = SessionAnswerTransitions.VoiceContribution.from(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
        stateAccess.updateState {
            it.copy(cardSession = SessionAnswerTransitions.bossMid(it.cardSession, voice))
        }
        // nextCard handles voice trigger, word bank, save, and boss progress event.
        val events = nextCard(triggerVoice = state.cardSession.inputMode == InputMode.VOICE)
        return SubmitResult(accepted = true, hintShown = false, needsSaveProgress = false, needsFlowerRefresh = true) to events
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
        val voice = SessionAnswerTransitions.VoiceContribution.from(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
        // Score from the pre-answer snapshot; pauseTimer may update only active time.
        val completion = SessionAnswerTransitions.eliteLast(state, voice, eliteController.stepCount)
        stateAccess.updateState {
            SessionAnswerTransitions.eliteLast(it, voice, eliteController.stepCount).state
        }
        // Audit: elite step finish
        audit?.eliteStepFinish(completion.stepIndex, completion.speed, completion.previousBest, state.elite.eliteFinishedToken + 1)
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
        val voice = SessionAnswerTransitions.VoiceContribution.from(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
        stateAccess.updateState {
            SessionAnswerTransitions.normalLast(it, voice, actualCompletedSubLessonCount(it))
        }
        Log.d(logTag, "Answer submitted: accepted=true (last card)")
        // Audit: sub-lesson completed
        val cs = stateAccess.currentState.cardSession
        audit?.subLessonComplete(cs.activeSubLessonIndex, cs.completedSubLessonCount, cs.subLessonCount)
        return SubmitResult(
            accepted = true,
            hintShown = false,
            needsSubLessonComplete = true,
            needsFlowerRefresh = true
        ) to listOf(
            SessionEvent.MarkSubLessonCardsShown(activeCards),
            SessionEvent.BuildSessionCards,
            SessionEvent.CheckAndMarkLessonCompleted,
            SessionEvent.RefreshFlowerStates,
            SessionEvent.UpdateStreak,
            SessionEvent.SaveProgress
        )
    }

    private fun actualCompletedSubLessonCount(state: TrainingUiState): Int {
        val lessonId = state.navigation.selectedLessonId
        val mastery = lessonId?.let { id ->
            state.navigation.selectedLanguageId?.let { languageId -> getMastery(id.value, languageId.value) }
        }
        val subLessons = lessonId?.let { getSchedule(it.value)?.subLessons }.orEmpty()
        return calculateCompletedSubLessons(subLessons, mastery, lessonId?.value, getHiddenCardIds())
    }

    /**
     * Normal mode: mid-sub-lesson correct answer. Advance to next card.
     */
    private fun submitNormalMidCard(
        shouldAddVoiceMetrics: Boolean,
        voiceDurationMs: Long?,
        voiceWords: Int,
        state: com.alexpo.grammermate.data.TrainingUiState
    ): Pair<SubmitResult, List<SessionEvent>> {
        stateMachine.reset()
        val voice = SessionAnswerTransitions.VoiceContribution.from(shouldAddVoiceMetrics, voiceDurationMs, voiceWords)
        stateAccess.updateState {
            it.copy(cardSession = SessionAnswerTransitions.normalMid(it.cardSession, voice))
        }
        // nextCard handles voice trigger, word bank, save.
        val events = nextCard(triggerVoice = state.cardSession.inputMode == InputMode.VOICE)
        return SubmitResult(accepted = true, hintShown = false, needsSaveProgress = false, needsFlowerRefresh = true) to events
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
        val voice = LinearSessionTransitions.VoiceContribution.from(
            shouldAddVoiceMetrics, voiceDurationMs, voiceWords
        )
        if (isLastCard) {
            pauseTimer()
            stateAccess.updateState {
                it.copy(cardSession = LinearSessionTransitions.correctLast(it.cardSession, voice))
            }
            return SubmitResult(accepted = true, hintShown = false, needsSubLessonComplete = true, needsSaveProgress = true)
        } else {
            updateStateAndClearInput {
                it.copy(cardSession = LinearSessionTransitions.correctMid(it.cardSession, voice))
            }
            cardSessions.advanceLinearCard()
            return SubmitResult(accepted = true, hintShown = false, needsSaveProgress = false)
        }
    }

    // ── Card navigation ─────────────────────────────────────────────────

    /**
     * Advance to the next card. Handles boss progress tracking and reward thresholds.
     *
     * @param triggerVoice Whether to trigger voice input on the next card.
     */
    fun nextCard(triggerVoice: Boolean = false): List<SessionEvent> = navigator.nextCard(triggerVoice)
    fun prevCard(): List<SessionEvent> = navigator.prevCard()
    fun navigateNext(): List<SessionEvent> = navigator.navigateNext()
    fun navigatePrev(): List<SessionEvent> = navigator.navigatePrev()
    fun selectSubLesson(index: Int): List<SessionEvent> = navigator.selectSubLesson(index)

    fun togglePause(): List<SessionEvent> {
        val state = stateAccess.currentState
        if (state.cardSession.sessionState == SessionState.ACTIVE) {
            // Active → pause
            stateMachine.pause()
            pauseTimer()
            // Audit: session paused
            audit?.sessionPause(stateAccess.currentState.cardSession.activeTimeMs)
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
                    voiceTriggerToken = stateMachine.voiceTriggerToken,
                    voicePromptStartMs = null
                ))
            }
            resumeTimer()
            // Audit: session resumed (from hint)
            audit?.sessionResume(stateAccess.currentState.cardSession.activeTimeMs)
            return listOf(SessionEvent.SaveProgress)
        }
        // Manual pause → resume
        stateMachine.resume()
        // Audit: session resumed (from manual pause)
        audit?.sessionResume(stateAccess.currentState.cardSession.activeTimeMs)
        return startSession()
    }

    fun pauseSession(): List<SessionEvent> = navigator.pauseSession()

    fun showAnswer(): List<SessionEvent> {
        val card = currentCard() ?: return emptyList()
        pauseTimer()
        val answer = stateMachine.showAnswer(card)
        val newHintCount = stateAccess.currentState.cardSession.hintCount + 1
        // Audit: manual hint
        audit?.hintManual(card.id, card.promptRu, card.acceptedAnswers.firstOrNull() ?: "", newHintCount)
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(answerText = answer, sessionState = SessionState.HINT_SHOWN, hintCount = newHintCount, incorrectAttemptsForCard = stateMachine.incorrectAttempts, voicePromptStartMs = null))
        }
        return listOf(SessionEvent.SaveProgress)
    }

    // ── Word bank interaction ───────────────────────────────────────────

    fun selectWordFromBank(word: String) {
        wordBank.select(word)
    }

    fun removeLastSelectedWord() {
        wordBank.removeLast()
    }

    // ── Skip ────────────────────────────────────────────────────────────

    fun skipToNextCard() = navigator.skipToNextCard()

    fun openEliteStep(index: Int): List<SessionEvent> = eliteController.openStep(index)

    fun cancelEliteSession(): List<SessionEvent> = eliteController.cancel()

    fun resolveEliteUnlocked(lessons: List<Lesson>, testMode: Boolean): Boolean =
        eliteController.resolveUnlocked(lessons, testMode)

    fun normalizeEliteSpeeds(speeds: List<Double>): List<Double> = eliteController.normalizeSpeeds(speeds)

    fun eliteSubLessonSize(): Int = eliteController.eliteSubLessonSize()

    fun calculateSpeedPerMinute(activeMs: Long, words: Int): Double =
        eliteController.calculateSpeedPerMinute(activeMs, words)

    // ── Unified card session start/exit ────────────────────────────────────

    /**
     * Start a card session with the given cards and screen mode.
     * Unified implementation for verb drill, daily translate, and daily verbs sessions.
     * Loads cards into [activeCards], resets all session state, activates the session,
     * and sets [screenMode] to the given mode.
     *
     * @param cards The cards to practice.
     * @param mode  The [TrainingScreenMode] to set (VERB_DRILL, DAILY_TRANSLATE, DAILY_VERBS).
     * @return List of [SessionEvent] to process.
     */
    fun startCardSession(cards: List<SessionCard>, mode: TrainingScreenMode): List<SessionEvent> = cardSessions.start(cards, mode)

    /**
     * Exit a card session: reset session state, clear cards, and reset screenMode to NORMAL.
     * Unified implementation for verb drill and daily practice exit.
     *
     * @return List of [SessionEvent] to process.
     */
    fun exitCardSession(): List<SessionEvent> = cardSessions.exit()

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
    fun startReview(cards: List<SessionCard>, hintLevel: HintLevel): List<SessionEvent> = cardSessions.startReview(cards, hintLevel)

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
    fun replaceCards(cards: List<SessionCard>): List<SessionEvent> = cardSessions.replace(cards)

    /** Exit verb drill mode. Delegates to [exitCardSession]. */
    fun exitVerbDrillSession() = exitCardSession()

    /** Exit daily practice session. Delegates to [exitCardSession]. */
    fun exitDailySession() = exitCardSession()

    // ── Card list management (called by ViewModel) ─────────────────────

    /**
     * Set the session cards list. Called by ViewModel after CardProvider builds them.
     */
    fun setSessionCards(cards: List<SentenceCard>) {
        assignSessionCards(cards)
    }

    fun setSessionCardsGeneric(cards: List<SessionCard>) {
        assignSessionCards(cards)
    }

    fun setBossCards(cards: List<SentenceCard>) {
        assignSessionCards(cards)
    }

    fun setEliteCards(cards: List<SentenceCard>) {
        assignSessionCards(cards)
    }

    fun getSessionCards(): List<SessionCard> = activeCards

    fun clearAllCards() {
        assignSessionCards(emptyList())
        stateMachine.reset()
        inputTextFlow.value = ""
        sessionTimerMsSink?.invoke(0L)
    }

    /** Drop live input and retry state after files are replaced by a backup. */
    fun clearTransientAfterRestore() {
        assignSessionCards(emptyList())
        stateMachine.reset()
        inputTextFlow.value = ""
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(
                currentCard = null,
                answerText = null,
                lastResult = null,
                lastRating = null,
                incorrectAttemptsForCard = 0,
                voicePromptStartMs = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList(),
                isReviewMode = false,
                screenMode = TrainingScreenMode.NORMAL,
                returnTo = "",
                completionNextAction = CompletionNextAction.NONE
            ))
        }
    }

    fun setEliteSizeMultiplier(multiplier: Double) = eliteController.setSizeMultiplier(multiplier)

    fun setSubLessonSize(size: Int) = eliteController.setSubLessonSize(size)

    // ── Timer entrypoints ─────────────────────────────────────────────────

    fun resumeTimer() {
        activeTimer.resume()
    }

    fun pauseTimer() {
        activeTimer.pause()
    }

    // ── Internal helpers ────────────────────────────────────────────────

    fun updateWordBank() {
        wordBank.update()
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
