package com.alexpo.grammermate.feature.daily

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.CardSessionStateModel
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.InputModeConfig
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionProgress
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.feature.training.WordBankGenerator
import com.alexpo.grammermate.feature.training.CardSessionStateMachine

/**
 * CardSessionContract adapter for Daily Practice Blocks 1 (Translation) and 3 (Verb Drill).
 * Block 2 (Vocab Flashcard) does NOT use this — the UI switches composables at block boundaries.
 *
 * Retry/hint logic is delegated to [CardSessionStateMachine].
 *
 * Create a fresh instance when the session enters a TRANSLATE or VERBS block.
 */
class DailyPracticeSessionProvider(
    private val tasks: List<DailyTask>,
    blockType: DailyBlockType,
    private val onBlockComplete: () -> Unit,
    override val languageId: String = "en",
    private val onAnswerChecked: (input: String, correct: Boolean) -> Unit = { _, _ -> },
    private val onSpeakTts: (String) -> Unit = {},
    private val onStopTts: () -> Unit = {},
    private val ttsStateProvider: () -> TtsState = { TtsState.Idle },
    private val onExit: () -> Unit = {},
    private val onCardAdvanced: (DailyTask) -> Unit = {},
    private val onFlagCard: ((SessionCard, DailyBlockType) -> Unit)? = null,
    private val onUnflagCard: ((SessionCard, DailyBlockType) -> Unit)? = null,
    private val isCardFlagged: ((SessionCard) -> Boolean)? = null,
    private val onExportFlagged: (() -> String?)? = null
) : CardSessionContract {

    /** All tasks matching the requested block type, capped by the global per-block limit. */
    private val blockType: DailyBlockType = blockType
    private val blockCards: List<DailyTask> = tasks
        .filter { it.blockType == blockType }
        .take(DailySessionComposer.CARDS_PER_BLOCK)

    private var currentIndex: Int by mutableStateOf(0)
    private var _pendingCard: SessionCard? by mutableStateOf(null)
    var pendingAnswerResult: AnswerResult? by mutableStateOf(null)
        private set
    private var _pendingResult: AnswerResult? by mutableStateOf(null)
    private var _inputMode: InputMode by mutableStateOf(InputMode.VOICE)
    private var _selectedWords: List<String> by mutableStateOf(emptyList())

    private var cachedWordBankCardId: String? = null
    private var cachedWordBank: List<String> = emptyList()
    private var pendingInput: String by mutableStateOf("")

    /** Retry/hint state machine. Answer provider returns first accepted answer. */
    private val sm = CardSessionStateMachine(
        maxAttempts = 3,
        answerProvider = { card -> card.acceptedAnswers.first() }
    )

    // Delegated state from CardSessionStateMachine
    val hintAnswer: String? get() = sm.hintAnswer
    val showIncorrectFeedback: Boolean get() = sm.showIncorrectFeedback
    val remainingAttempts: Int get() = sm.remainingAttempts
    val voiceTriggerToken: Int get() = sm.voiceTriggerToken

    // ── Capabilities ─────────────────────────────────────────────────────

    override val supportsTts: Boolean get() = true
    override val supportsVoiceInput: Boolean get() = true
    override val supportsWordBank: Boolean get() = true
    override val supportsFlagging: Boolean get() = true
    override val supportsNavigation: Boolean get() = true
    override val supportsPause: Boolean get() = true

    // ── TTS state ────────────────────────────────────────────────────────

    override val ttsState: TtsState
        get() = ttsStateProvider()

    // ── Contract state ───────────────────────────────────────────────────

    override val currentCard: SessionCard?
        get() {
            if (_pendingCard != null) return _pendingCard
            if (currentIndex >= blockCards.size) return null
            return taskToSessionCard(blockCards[currentIndex])
        }

    override val progress: SessionProgress
        get() = SessionProgress(
            current = (currentIndex + 1).coerceAtMost(blockCards.size),
            total = blockCards.size
        )

    override val isComplete: Boolean
        get() = currentIndex >= blockCards.size && _pendingCard == null

    override val inputText: String get() = ""

    override val inputModeConfig: InputModeConfig
        get() {
            val task = blockCards.getOrNull(currentIndex)
            val mode = when (task) {
                is DailyTask.TranslateSentence -> task.inputMode
                is DailyTask.ConjugateVerb -> task.inputMode
                else -> InputMode.KEYBOARD
            }
            return InputModeConfig(
                availableModes = setOf(InputMode.KEYBOARD, InputMode.VOICE, InputMode.WORD_BANK),
                defaultMode = mode,
                showInputModeButtons = true
            )
        }

    override val lastResult: AnswerResult? get() = _pendingResult

    override val sessionActive: Boolean
        get() {
            if (sm.isPaused) return false
            if (sm.hintAnswer != null) return false
            return currentIndex < blockCards.size
        }

    // ── CardSessionStateModel overrides ──────────────────────────────────
    // Explicit mappings from internal CardSessionStateMachine state.
    // These override the default implementations in CardSessionContract
    // which don't account for the state machine's isPaused/hintAnswer.

    override val isActive: Boolean
        get() = sessionActive

    override val isPaused: Boolean
        get() = sm.isPaused && sm.hintAnswer == null && !isComplete

    override val isHintShown: Boolean
        get() = sm.hintAnswer != null

    override val canSubmit: Boolean
        get() = sessionActive && currentCard != null

    override val hasCurrentCard: Boolean
        get() = currentCard != null

    override val currentInputMode: InputMode
        get() = _inputMode

    // ── Actions ──────────────────────────────────────────────────────────

    /** Clear incorrect feedback when the user starts typing a new attempt. */
    fun clearIncorrectFeedback() {
        sm.clearIncorrectFeedback()
    }

    override fun onInputChanged(text: String) {
        pendingInput = text
        sm.onInputChanged(text)
    }

    override fun submitAnswer(): AnswerResult? {
        if (currentIndex >= blockCards.size) return null
        val task = blockCards[currentIndex]
        val card = taskToSessionCard(task) ?: return null

        // Already showing hint — ignore further submissions until nextCard()
        if (sm.hintAnswer != null) return null

        val input = if (currentInputMode == InputMode.WORD_BANK && _selectedWords.isNotEmpty()) {
            _selectedWords.joinToString(" ")
        } else {
            pendingInput
        }

        val isCorrect = card.acceptedAnswers.any { ans ->
            Normalizer.normalize(input) == Normalizer.normalize(ans)
        }

        val result = sm.onSubmit(
            isCorrect = isCorrect,
            card = card,
            inputMode = _inputMode,
            onCorrect = {
                _pendingCard = card
                _pendingResult = AnswerResult(correct = true, displayAnswer = card.acceptedAnswers.first())
                pendingAnswerResult = _pendingResult
                onAnswerChecked(input, true)
            },
            onWrong = {
                onAnswerChecked(input, false)
            }
        )

        when (result) {
            is CardSessionStateMachine.OnSubmitResult.HintShown -> {
                _pendingCard = card
            }
            is CardSessionStateMachine.OnSubmitResult.Correct -> {
                pendingAnswerResult = result.result
            }
            is CardSessionStateMachine.OnSubmitResult.Wrong -> {
                // State already updated by state machine
            }
        }

        return _pendingResult
    }

    fun submitAnswerWithInput(input: String): AnswerResult? {
        pendingInput = input
        return submitAnswer()
    }

    override fun showAnswer(): String? {
        if (currentIndex >= blockCards.size) return null
        val card = taskToSessionCard(blockCards[currentIndex]) ?: return null
        _pendingCard = card
        return sm.showAnswer(card)
    }

    override fun nextCard() {
        _pendingCard = null
        _pendingResult = null
        pendingAnswerResult = null
        sm.reset()
        _selectedWords = emptyList()
        cachedWordBankCardId = null
        cachedWordBank = emptyList()
        pendingInput = ""

        // Notify caller about the card being advanced (for progress persistence)
        // Only VOICE and KEYBOARD count as "practiced"; WORD_BANK does not.
        if (currentIndex < blockCards.size && _inputMode != InputMode.WORD_BANK) {
            onCardAdvanced(blockCards[currentIndex])
        }

        // Always advance to next card
        currentIndex++

        // Auto-trigger voice recognition when advancing to next card in VOICE mode
        if (_inputMode == InputMode.VOICE) {
            sm.triggerVoice()
        }

        if (currentIndex >= blockCards.size) {
            onBlockComplete()
        }
    }

    override fun prevCard() {
        if (currentIndex > 0) {
            currentIndex--
            _pendingCard = null
            _pendingResult = null
            pendingAnswerResult = null
            sm.reset()
            _selectedWords = emptyList()
            cachedWordBankCardId = null
            cachedWordBank = emptyList()
        }
    }

    /**
     * Navigate to the next card, pausing first if the session is ACTIVE.
     * Used by UI navigation arrows — always leaves the session in PAUSED state.
     */
    fun navigateNext() {
        // Pause first if active
        if (!sm.isPaused && sm.hintAnswer == null) {
            sm.pause()
        }
        // Reset card state and advance
        _pendingCard = null
        _pendingResult = null
        pendingAnswerResult = null
        _selectedWords = emptyList()
        cachedWordBankCardId = null
        cachedWordBank = emptyList()
        pendingInput = ""

        // Notify caller about the card being advanced
        if (currentIndex < blockCards.size && _inputMode != InputMode.WORD_BANK) {
            onCardAdvanced(blockCards[currentIndex])
        }

        currentIndex++
        // Force paused state after navigation
        sm.reset()
        sm.pause()

        if (currentIndex >= blockCards.size) {
            onBlockComplete()
        }
    }

    /**
     * Navigate to the previous card, pausing first if the session is ACTIVE.
     * Used by UI navigation arrows — always leaves the session in PAUSED state.
     */
    fun navigatePrev() {
        if (currentIndex <= 0) return
        // Pause first if active
        if (!sm.isPaused && sm.hintAnswer == null) {
            sm.pause()
        }
        currentIndex--
        _pendingCard = null
        _pendingResult = null
        pendingAnswerResult = null
        _selectedWords = emptyList()
        cachedWordBankCardId = null
        cachedWordBank = emptyList()
        pendingInput = ""
        // Force paused state after navigation
        sm.reset()
        sm.pause()
    }

    override fun togglePause() {
        if (sm.isPaused) {
            // Play pressed — resume session, clearing hint if shown
            // Matches SessionRunner behavior: Play from HINT_SHOWN clears hint and resumes ACTIVE
            if (sm.hintAnswer != null) {
                // Hint was shown — clear hint, reset attempts, resume ACTIVE
                sm.reset()
                _pendingCard = null
                _pendingResult = null
                pendingAnswerResult = null
                _selectedWords = emptyList()
                cachedWordBankCardId = null
                cachedWordBank = emptyList()
                pendingInput = ""
                // Trigger voice recognition if in VOICE mode (matches SessionRunner startSession)
                if (_inputMode == InputMode.VOICE) {
                    sm.triggerVoice()
                }
            } else {
                // Simple pause resume — just unpause without resetting card state
                sm.resume()
            }
        } else {
            sm.pause()
        }
    }

    override fun setInputMode(mode: InputMode) {
        _inputMode = mode
        _selectedWords = emptyList()
        if (mode == InputMode.VOICE) {
            sm.triggerVoice()
        }
    }

    // ── Word Bank ────────────────────────────────────────────────────────

    override fun getWordBankWords(): List<String> {
        val card = _pendingCard ?: taskToSessionCard(blockCards.getOrNull(currentIndex) ?: return emptyList())
            ?: return emptyList()

        if (cachedWordBankCardId == card.id) return cachedWordBank

        val allAnswers = blockCards
            .mapNotNull { taskToSessionCard(it) }
            .flatMap { it.acceptedAnswers }

        val bank = WordBankGenerator.generateForVerb(
            answer = card.acceptedAnswers.first(),
            allAnswers = allAnswers,
            maxDistractors = 8
        )
        cachedWordBankCardId = card.id
        cachedWordBank = bank
        return bank
    }

    override fun getSelectedWords(): List<String> = _selectedWords

    override fun selectWordFromBank(word: String) {
        val bank = getWordBankWords()
        val availableCount = bank.count { it == word }
        val usedCount = _selectedWords.count { it == word }
        if (usedCount < availableCount) {
            _selectedWords = _selectedWords + word
        }
    }

    override fun removeLastSelectedWord() {
        if (_selectedWords.isNotEmpty()) {
            _selectedWords = _selectedWords.dropLast(1)
        }
    }

    // ── TTS ──────────────────────────────────────────────────────────────

    override fun speakTts() {
        val card = _pendingCard ?: taskToSessionCard(blockCards.getOrNull(currentIndex) ?: return)
            ?: return
        val text = card.acceptedAnswers.firstOrNull() ?: return
        onSpeakTts(text)
    }

    override fun stopTts() {
        onStopTts()
    }

    // ── Verb/Tense info (for Block 3 chips) ──────────────────────────────

    /** Returns the VerbDrillCard for the current task if this is a verb block. */
    fun currentVerbDrillCard(): VerbDrillCard? {
        val task = blockCards.getOrNull(currentIndex) ?: return null
        return (task as? DailyTask.ConjugateVerb)?.card
    }

    /**
     * Returns all VerbDrillCards from the current block that match the given verb+tense.
     * Used to populate the conjugation table in VerbReferenceBottomSheet.
     */
    fun getConjugationCards(verb: String, tense: String): List<VerbDrillCard> {
        return blockCards.mapNotNull { (it as? DailyTask.ConjugateVerb)?.card }
            .filter { it.verb == verb && it.tense == tense }
    }

    // ── Flagging ──────────────────────────────────────────────────────────

    override fun flagCurrentCard() {
        val card = currentCard ?: return
        onFlagCard?.invoke(card, blockType)
    }

    override fun unflagCurrentCard() {
        val card = currentCard ?: return
        onUnflagCard?.invoke(card, blockType)
    }

    override fun isCurrentCardFlagged(): Boolean {
        val card = currentCard ?: return false
        return isCardFlagged?.invoke(card) ?: false
    }

    override fun hideCurrentCard() {
        // No-op for daily practice — flagging is enough, no card hiding
    }

    override fun exportFlaggedCards(): String? {
        return onExportFlagged?.invoke()
    }

    override fun requestExit() {
        onExit()
    }

    override fun requestNextBatch() {
        onBlockComplete()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun taskToSessionCard(task: DailyTask): SessionCard? = when (task) {
        is DailyTask.TranslateSentence -> task.card
        is DailyTask.ConjugateVerb -> task.card
        is DailyTask.VocabFlashcard -> null
    }
}
