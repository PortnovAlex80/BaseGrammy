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
 * Owns the training session lifecycle: card navigation, answer submission,
 * timer entrypoints, word bank interaction, and elite sub-mode.
 * [SessionActiveTimer] owns the running clock and checkpoint cadence.
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

    private val submitGuard = SubmissionDedupGuard(submitClockMs)
    private var sessionCards: List<SessionCard> = emptyList()
    private fun assignSessionCards(cards: List<SessionCard>) {
        sessionCards = cards
        submitGuard.onDeckReplaced()
    }
    private var bossCards: List<SessionCard> = emptyList()
    private var eliteCards: List<SessionCard> = emptyList()
    private val activeTimer = SessionActiveTimer(
        scope = coroutineScope,
        readPersistedMs = { stateAccess.uiState.value.cardSession.activeTimeMs },
        writePersistedMs = { activeTimeMs ->
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(activeTimeMs = activeTimeMs))
            }
        },
        onDisplayTick = sessionTimerMsSink,
        onCheckpoint = onTimerSaveProgress
    )

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
            it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.ACTIVE, answerText = null, incorrectAttemptsForCard = 0, voiceTriggerToken = stateMachine.voiceTriggerToken, voicePromptStartMs = null))
        }
        // Populate word bank so the UI toggle is visible from the start.
        // Without this, regular lesson sessions start with wordBankWords=empty,
        // hiding the word bank toggle button (supportsWordBank checks isNotEmpty).
        updateWordBank()
        // Audit: session started
        val startedState = stateAccess.uiState.value
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
        val state = stateAccess.uiState.value
        if (state.cardSession.sessionState == SessionState.ACTIVE) return emptyList()
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

    /** Clear the typed answer (boss/elite transitions outside the runner). */
    fun clearTypedInput() {
        inputTextFlow.value = ""
    }

    fun onInputChanged(text: String) {
        stateMachine.onInputChanged(text)
        inputTextFlow.value = text
        // Only the state-machine sibling fields go through the combined
        // uiState, and only when they actually changed - the typed text
        // itself stays in [inputTextFlow].
        val attempts = stateMachine.incorrectAttempts
        val hintAnswer = stateMachine.hintAnswer
        stateAccess.updateState {
            val cs = it.cardSession
            if (cs.incorrectAttemptsForCard == attempts && cs.answerText == hintAnswer) {
                it
            } else {
                it.copy(cardSession = cs.copy(incorrectAttemptsForCard = attempts, answerText = hintAnswer))
            }
        }
    }

    fun onVoicePromptStarted() {
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(voicePromptStartMs = SystemClock.elapsedRealtime()))
        }
    }

    fun setInputMode(mode: InputMode) {
        val previousMode = stateAccess.uiState.value.cardSession.inputMode
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
        // Audit: input mode change
        audit?.inputModeChange(
            previousMode.name, mode.name,
            currentCard()?.id ?: ""
        )
    }

    // ── Answer submission ───────────────────────────────────────────────

    /**
     * Submit the current answer. Orchestrates validation, state updates,
     * and signals the ViewModel for cross-module actions via [SubmitResult].
     */
    fun submitAnswer(): Pair<SubmitResult, List<SessionEvent>> {
        val state = stateAccess.uiState.value
        val inputText = inputTextFlow.value
        // VOICE keeps cross-card duplicate suppression for the two speech launchers.
        if (submitGuard.shouldSuppress(
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
            val isLastCard = state.cardSession.currentIndex >= sessionCards.lastIndex

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
     * Returns the events emitted by [nextCardInternal] (notably
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
        val events = nextCardInternal(triggerVoice = state.cardSession.inputMode == InputMode.VOICE)
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
        val completion = SessionAnswerTransitions.eliteLast(state, voice, eliteStepCount)
        stateAccess.updateState {
            SessionAnswerTransitions.eliteLast(it, voice, eliteStepCount).state
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
        val cs = stateAccess.uiState.value.cardSession
        audit?.subLessonComplete(cs.activeSubLessonIndex, cs.completedSubLessonCount, cs.subLessonCount)
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
        val events = nextCardInternal(triggerVoice = state.cardSession.inputMode == InputMode.VOICE)
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
        updateStateAndClearInput {
            it.copy(cardSession = LinearSessionTransitions.advance(
                it.cardSession, nextIndex, nextCard,
                stateMachine.incorrectAttempts, stateMachine.voiceTriggerToken
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
        val fromIdx = state.cardSession.currentIndex
        val wasHintShown = state.cardSession.sessionState == SessionState.HINT_SHOWN || stateMachine.hintAnswer != null
        stateMachine.reset()
        val lastIndex = sessionCards.lastIndex
        val isOnLastCard = state.cardSession.currentIndex >= lastIndex && lastIndex >= 0

        // Linear sessions: on last card, signal completion without lesson-level mechanics
        if (isOnLastCard && isLinearSession) {
            pauseTimer()
            stateAccess.updateState {
                it.copy(cardSession = LinearSessionTransitions.completeViaNext(it.cardSession))
            }
            Log.d(logTag, "LinearSession: Next pressed on last card — signaling completion")
            return listOf(SessionEvent.SaveProgress)
        }

        // Fix 3: If already on the last card, trigger sub-lesson completion instead of staying put
        if (isOnLastCard && !state.boss.bossActive && !state.elite.eliteActive) {
            pauseTimer()
            stateAccess.updateState {
                SessionAnswerTransitions.normalCompleteViaNext(it, actualCompletedSubLessonCount(it))
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
        updateStateAndClearInput {
            it.copy(cardSession = it.cardSession.copy(currentIndex = nextIndex, currentCard = nextCard, lastResult = null, answerText = null, incorrectAttemptsForCard = stateMachine.incorrectAttempts, sessionState = SessionState.ACTIVE, voiceTriggerToken = stateMachine.voiceTriggerToken, voicePromptStartMs = null))
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
        // Audit: card navigation next
        val toIdx = stateAccess.uiState.value.cardSession.currentIndex
        audit?.cardNav("next", fromIdx, toIdx, "submit")
        events.add(SessionEvent.SaveProgress)
        return events
    }

    fun prevCard(): List<SessionEvent> {
        val state = stateAccess.uiState.value
        val fromIdx = state.cardSession.currentIndex
        val prevIndex = (state.cardSession.currentIndex - 1).coerceAtLeast(0)
        val prevCard = sessionCards.getOrNull(prevIndex)
        stateMachine.reset()
        updateStateAndClearInput {
            it.copy(cardSession = it.cardSession.copy(currentIndex = prevIndex, currentCard = prevCard, lastResult = null, answerText = null, incorrectAttemptsForCard = 0, voicePromptStartMs = null))
        }
        // Audit: card navigation prev
        audit?.cardNav("prev", fromIdx, prevIndex, "manual")
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
        val fromIdx = state.cardSession.currentIndex
        val nextIndex = (state.cardSession.currentIndex + 1).coerceAtMost(sessionCards.lastIndex)
        val nextCard = sessionCards.getOrNull(nextIndex)
        updateStateAndClearInput {
            it.copy(cardSession = it.cardSession.copy(currentIndex = nextIndex, currentCard = nextCard, lastResult = null, answerText = null, incorrectAttemptsForCard = 0, sessionState = SessionState.PAUSED, voicePromptStartMs = null))
        }
        // Audit: card navigation next (manual)
        audit?.cardNav("next", fromIdx, nextIndex, "manual")
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
        val state = stateAccess.uiState.value
        val fromIdx = state.cardSession.currentIndex
        val prevIndex = (state.cardSession.currentIndex - 1).coerceAtLeast(0)
        val prevCard = sessionCards.getOrNull(prevIndex)
        updateStateAndClearInput {
            it.copy(cardSession = it.cardSession.copy(currentIndex = prevIndex, currentCard = prevCard, lastResult = null, answerText = null, incorrectAttemptsForCard = 0, sessionState = SessionState.PAUSED, voicePromptStartMs = null))
        }
        // Audit: card navigation prev (manual)
        audit?.cardNav("prev", fromIdx, prevIndex, "manual")
        events.add(SessionEvent.SaveProgress)
        return events
    }

    fun selectSubLesson(index: Int): List<SessionEvent> {
        pauseTimer()
        stateMachine.reset()
        val prevState = stateAccess.uiState.value
        val fromSub = prevState.cardSession.activeSubLessonIndex
        val fromIdx = prevState.cardSession.currentIndex
        updateStateAndClearInput {
            it.copy(cardSession = it.cardSession.copy(activeSubLessonIndex = index.coerceAtLeast(0), currentIndex = 0, lastResult = null, answerText = null, sessionState = SessionState.PAUSED))
        }
        // Audit: sub-lesson transition
        audit?.subLessonTransition(fromSub, index, prevState.cardSession.subLessonCount, fromIdx, 0)
        return listOf(SessionEvent.BuildSessionCards, SessionEvent.SaveProgress)
    }

    // ── Pause/resume ────────────────────────────────────────────────────

    fun togglePause(): List<SessionEvent> {
        val state = stateAccess.uiState.value
        if (state.cardSession.sessionState == SessionState.ACTIVE) {
            // Active → pause
            stateMachine.pause()
            pauseTimer()
            // Audit: session paused
            audit?.sessionPause(stateAccess.uiState.value.cardSession.activeTimeMs)
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
            audit?.sessionResume(stateAccess.uiState.value.cardSession.activeTimeMs)
            return listOf(SessionEvent.SaveProgress)
        }
        // Manual pause → resume
        stateMachine.resume()
        // Audit: session resumed (from manual pause)
        audit?.sessionResume(stateAccess.uiState.value.cardSession.activeTimeMs)
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
        val newHintCount = stateAccess.uiState.value.cardSession.hintCount + 1
        // Audit: manual hint
        audit?.hintManual(card.id, card.promptRu, card.acceptedAnswers.firstOrNull() ?: "", newHintCount)
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(answerText = answer, sessionState = SessionState.HINT_SHOWN, hintCount = newHintCount, incorrectAttemptsForCard = stateMachine.incorrectAttempts, voicePromptStartMs = null))
        }
        return listOf(SessionEvent.SaveProgress)
    }

    // ── Word bank interaction ───────────────────────────────────────────

    fun selectWordFromBank(word: String) {
        val currentSelected = stateAccess.uiState.value.cardSession.selectedWords
        val newSelected = currentSelected + word
        val inputText = newSelected.joinToString(" ")
        // Audit: word bank select
        audit?.wordBankSelect(word, newSelected.size, currentCard()?.id ?: "")
        inputTextFlow.value = inputText
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(selectedWords = newSelected))
        }
    }

    fun removeLastSelectedWord() {
        val currentSelected = stateAccess.uiState.value.cardSession.selectedWords
        if (currentSelected.isEmpty()) return

        val newSelected = currentSelected.dropLast(1)
        val inputText = newSelected.joinToString(" ")
        // Audit: word bank remove
        audit?.wordBankRemove(newSelected.size, currentCard()?.id ?: "")
        inputTextFlow.value = inputText
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(selectedWords = newSelected))
        }
    }

    // ── Skip ────────────────────────────────────────────────────────────

    fun skipToNextCard() {
        val state = stateAccess.uiState.value
        val fromIdx = state.cardSession.currentIndex
        val nextIndex = state.cardSession.currentIndex + 1
        if (nextIndex < sessionCards.size) {
            stateMachine.reset()
            updateStateAndClearInput {
                it.copy(cardSession = it.cardSession.copy(currentIndex = nextIndex, currentCard = sessionCards[nextIndex], lastResult = null, answerText = null, incorrectAttemptsForCard = 0))
            }
            // Audit: skip to next card
            audit?.cardNav("skip", fromIdx, nextIndex, "manual")
        } else {
            pauseTimer()
            updateStateAndClearInput {
                it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, lastResult = null, answerText = null))
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
        assignSessionCards(cards)
        val firstCard = cards.firstOrNull()
        updateStateAndClearInput {
            it.copy(elite = it.elite.copy(eliteActive = true, eliteStepIndex = stepIndex), cardSession = it.cardSession.copy(currentIndex = 0, currentCard = firstCard, lastResult = null, answerText = null, incorrectAttemptsForCard = 0, correctCount = 0, incorrectCount = 0, activeTimeMs = 0L, voiceActiveMs = 0L, voiceWordCount = 0, hintCount = 0, voicePromptStartMs = null, sessionState = SessionState.PAUSED, subLessonTotal = cards.size, subLessonCount = eliteStepCount, activeSubLessonIndex = stepIndex, completedSubLessonCount = 0))
        }
        // Audit: elite step start
        audit?.eliteStepStart(stepIndex, eliteStepCount, cards.size)
        return listOf(SessionEvent.SaveProgress)
    }

    fun cancelEliteSession(): List<SessionEvent> {
        if (!stateAccess.uiState.value.elite.eliteActive) return emptyList()
        pauseTimer()
        stateMachine.reset()
        updateStateAndClearInput {
            it.copy(elite = it.elite.copy(eliteActive = false), cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, currentIndex = 0, lastResult = null, answerText = null, incorrectAttemptsForCard = 0, voicePromptStartMs = null))
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
        assignSessionCards(cards)
        val firstCard = cards.firstOrNull()
        if (stateAccess.uiState.value.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        updateStateAndClearInput {
            LinearSessionTransitions.start(it, cards, mode, stateMachine.voiceTriggerToken)
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
        assignSessionCards(emptyList())
        updateStateAndClearInput {
            LinearSessionTransitions.exit(it)
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
        assignSessionCards(cards)
        val firstCard = cards.firstOrNull()
        // Audit: review started
        audit?.reviewStart(
            stateAccess.uiState.value.navigation.selectedLessonId?.value ?: "",
            hintLevel.name
        )
        val defaultInputMode = ReviewSessionTransitions.defaultInputMode(hintLevel)
        if (defaultInputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        updateStateAndClearInput { ReviewSessionTransitions.start(it, cards, hintLevel, stateMachine.voiceTriggerToken) }
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
        assignSessionCards(cards)
        val firstCard = cards.firstOrNull()
        if (stateAccess.uiState.value.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        updateStateAndClearInput {
            LinearSessionTransitions.replace(it, cards, stateMachine.voiceTriggerToken)
        }
        if (firstCard != null) {
            resumeTimer()
            updateWordBank()
        }
        // Audit: cards replaced
        audit?.cardsReplaced(cards.size, "more")
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
        assignSessionCards(cards)
    }

    fun setSessionCardsGeneric(cards: List<SessionCard>) {
        assignSessionCards(cards)
    }

    fun setBossCards(cards: List<SentenceCard>) {
        bossCards = cards
        assignSessionCards(cards)
    }

    fun setEliteCards(cards: List<SentenceCard>) {
        eliteCards = cards
        assignSessionCards(cards)
    }

    fun getSessionCards(): List<SessionCard> = sessionCards

    fun clearAllCards() {
        assignSessionCards(emptyList())
        bossCards = emptyList()
        eliteCards = emptyList()
    }

    fun setEliteSizeMultiplier(multiplier: Double) {
        eliteSizeMultiplier = multiplier
    }

    fun setSubLessonSize(size: Int) {
        subLessonSize = size
    }

    // ── Timer entrypoints ─────────────────────────────────────────────────

    fun resumeTimer() {
        activeTimer.resume()
    }

    fun pauseTimer() {
        activeTimer.pause()
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

        updateStateAndClearInput {
            it.copy(cardSession = it.cardSession.copy(wordBankWords = wordBank, selectedWords = emptyList()))
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
