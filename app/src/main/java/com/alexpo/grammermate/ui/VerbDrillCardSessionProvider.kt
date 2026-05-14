package com.alexpo.grammermate.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.InputModeConfig
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionProgress
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillSessionState
import com.alexpo.grammermate.feature.training.CardSessionStateMachine
import com.alexpo.grammermate.feature.training.WordBankGenerator

/**
 * Adapter that wraps [VerbDrillViewModel] to implement [CardSessionContract].
 *
 * The contract expects submitAnswer() to return a result while keeping the card
 * visible for feedback, then nextCard() to advance to the next card.
 * This adapter tracks the submitted card locally in Compose state so the
 * UI can show the result. The ViewModel index is advanced in nextCard(),
 * matching the normal training flow (submit -> result -> next -> advance).
 *
 * Retry/hint logic is delegated to [CardSessionStateMachine].
 *
 * Usage: create a fresh instance each time the session starts via `remember`.
 */
class VerbDrillCardSessionProvider(
    private val viewModel: VerbDrillViewModel
) : CardSessionContract {

    /** The card that was submitted and is currently showing feedback. */
    var pendingCard: VerbDrillCard? by mutableStateOf(null)
        private set

    /** The answer result from the last submission. */
    var pendingAnswerResult: AnswerResult? by mutableStateOf(null)
        private set

    /** Retry/hint state machine. Answer provider returns [VerbDrillCard.answer]. */
    private val sm = CardSessionStateMachine(
        maxAttempts = 3,
        answerProvider = { card -> (card as VerbDrillCard).answer }
    )

    // Delegated state from CardSessionStateMachine
    val hintAnswer: String? get() = sm.hintAnswer
    val showIncorrectFeedback: Boolean get() = sm.showIncorrectFeedback
    val remainingAttempts: Int get() = sm.remainingAttempts
    val voiceTriggerToken: Int get() = sm.voiceTriggerToken

    /** Current input mode. */
    private var _inputMode: InputMode by mutableStateOf(InputMode.VOICE)

    /** Words currently selected in word bank mode. */
    private var _selectedWords: List<String> by mutableStateOf(emptyList())

    /** Cached word bank for the current card. */
    private var cachedWordBankCardId: String? = null
    private var cachedWordBank: List<String> = emptyList()

    private val session: VerbDrillSessionState?
        get() = viewModel.uiState.value.session

    // ── Capabilities ─────────────────────────────────────────────────────

    override val supportsTts: Boolean get() = true
    override val supportsVoiceInput: Boolean get() = true
    override val supportsWordBank: Boolean get() = true
    override val supportsFlagging: Boolean get() = true
    override val supportsNavigation: Boolean get() = true
    override val supportsPause: Boolean get() = true

    // ── Contract state ───────────────────────────────────────────────────

    override val currentCard: SessionCard?
        get() {
            val s = session ?: return null
            if (pendingCard != null) return pendingCard
            if (s.isComplete) return null
            return s.cards.getOrElse(s.currentIndex) { null }
        }

    override val progress: SessionProgress
        get() {
            val s = session ?: return SessionProgress(0, 0)
            val displayIndex = s.currentIndex + 1
            return SessionProgress(current = displayIndex, total = s.cards.size)
        }

    override val isComplete: Boolean
        get() = session?.isComplete == true && pendingCard == null

    override val inputText: String
        get() = "" // Input is managed by the composable

    override val inputModeConfig: InputModeConfig
        get() = InputModeConfig(
            availableModes = setOf(InputMode.KEYBOARD, InputMode.VOICE, InputMode.WORD_BANK),
            defaultMode = InputMode.VOICE,
            showInputModeButtons = false
        )

    override val lastResult: AnswerResult?
        get() = pendingAnswerResult

    override val sessionActive: Boolean
        get() {
            if (sm.isPaused) return false
            if (sm.hintAnswer != null) return false
            val s = session ?: return false
            return !s.isComplete || pendingCard != null
        }

    override val ttsState: TtsState
        get() = viewModel.ttsState.value

    override val currentInputMode: InputMode
        get() = _inputMode

    override val languageId: String
        get() = viewModel.uiState.value.loadedLanguageId ?: "it"

    override val currentSpeedWpm: Int
        get() = viewModel.currentSpeedWpm

    // ── Actions ──────────────────────────────────────────────────────────

    /** Clear incorrect feedback when the user starts typing a new attempt. */
    fun clearIncorrectFeedback() {
        sm.clearIncorrectFeedback()
    }

    override fun togglePause() {
        if (sm.isPaused) {
            if (sm.hintAnswer != null) {
                // Hint was shown (3 wrong attempts or manual eye button) — advance to next card
                nextCard()
            } else {
                // Manual pause — just resume the current card without advancing
                sm.resume()
            }
        } else {
            sm.pause()
        }
    }

    override fun onInputChanged(text: String) {
        sm.onInputChanged(text)
    }

    override fun submitAnswer(): AnswerResult? {
        // VerbDrill uses submitAnswerWithInput instead
        return null
    }

    /**
     * Submit an answer with the given input text. This is the primary method
     * called by the VerbDrill integration.
     *
     * Retry/hint flow is delegated to [CardSessionStateMachine].
     */
    fun submitAnswerWithInput(input: String): AnswerResult? {
        val s = session ?: return null
        if (s.isComplete) return null
        if (s.currentIndex >= s.cards.size) return null

        val card = s.cards[s.currentIndex]
        val isCorrect = Normalizer.normalize(input) == Normalizer.normalize(card.answer)

        val result = sm.onSubmit(
            isCorrect = isCorrect,
            card = card,
            inputMode = _inputMode,
            onCorrect = {
                pendingCard = card
                pendingAnswerResult = AnswerResult(correct = true, displayAnswer = card.answer)
                viewModel.recordAnswerTime()
            },
            onWrong = {}
        )

        when (result) {
            is CardSessionStateMachine.OnSubmitResult.HintShown -> {
                pendingCard = card
            }
            is CardSessionStateMachine.OnSubmitResult.Correct -> {
                pendingAnswerResult = result.result
            }
            is CardSessionStateMachine.OnSubmitResult.Wrong -> {
                // State already updated by state machine
            }
        }

        return pendingAnswerResult
    }

    override fun showAnswer(): String? {
        val s = session ?: return null
        val card = s.cards.getOrElse(s.currentIndex) { return null }
        pendingCard = card
        return sm.showAnswer(card)
    }

    override fun nextCard() {
        val hadPending = pendingCard != null
        val hadHint = sm.hintAnswer != null

        pendingCard = null
        pendingAnswerResult = null
        sm.reset()
        _selectedWords = emptyList()
        cachedWordBankCardId = null
        cachedWordBank = emptyList()

        // Auto-trigger voice recognition when advancing to next card in VOICE mode
        if (_inputMode == InputMode.VOICE) {
            sm.triggerVoice()
        }

        if (hadHint) {
            // Hint was shown (3 wrong attempts or manual eye button) — persist and advance
            viewModel.markCardCompleted()
        } else if (hadPending) {
            // Correct answer was shown — now advance the ViewModel index
            viewModel.submitCorrectAnswer()
        } else {
            // No pending result (user pressed Next without answering)
            viewModel.nextCardManual()
        }
    }

    override fun prevCard() {
        viewModel.prevCard()
        pendingCard = null
        pendingAnswerResult = null
        sm.reset()
        _selectedWords = emptyList()
        cachedWordBankCardId = null
        cachedWordBank = emptyList()
    }

    // ── Voice Input ───────────────────────────────────────────────────────

    override fun onVoiceInputResult(text: String) {
        // Voice input result is handled by the caller who sets input text
        // and triggers submit. The composable manages this flow.
    }

    override fun setInputMode(mode: InputMode) {
        _inputMode = mode
        _selectedWords = emptyList()
        if (mode == InputMode.VOICE) {
            sm.triggerVoice()
        }
    }

    // ── Word Bank ─────────────────────────────────────────────────────────

    override fun getWordBankWords(): List<String> {
        val s = session ?: return emptyList()
        val card = pendingCard ?: s.cards.getOrElse(s.currentIndex) { return emptyList() }

        // Cache word bank per card to avoid re-generating on every recomposition
        if (cachedWordBankCardId == card.id) return cachedWordBank

        val allAnswers = s.cards.map { it.answer }
        val bank = WordBankGenerator.generateForVerb(
            answer = card.answer,
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
        val s = session
        val card = pendingCard ?: s?.cards?.getOrElse(s.currentIndex) { null } ?: return
        val text = card.answer
        viewModel.speakTts(text)
    }

    override fun stopTts() {
        viewModel.stopTts()
    }

    // ── Flagging ─────────────────────────────────────────────────────────

    override fun flagCurrentCard() {
        viewModel.flagBadSentence()
    }

    override fun unflagCurrentCard() {
        viewModel.unflagBadSentence()
    }

    override fun isCurrentCardFlagged(): Boolean {
        return viewModel.isBadSentence()
    }

    override fun hideCurrentCard() {
        // Not yet supported for VerbDrill
    }

    override fun exportFlaggedCards(): String? {
        return viewModel.exportBadSentences()
    }

    // ── Session lifecycle ────────────────────────────────────────────────

    override fun requestExit() {
        viewModel.exitSession()
    }

    override fun requestNextBatch() {
        viewModel.nextBatch()
    }
}
