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

/**
 * Adapter that wraps [VerbDrillViewModel] to implement [CardSessionContract].
 *
 * VerbDrillViewModel advances the card index inside submitAnswer(), but the
 * contract expects submitAnswer() to return a result while keeping the card
 * visible for feedback. This adapter tracks the submitted card locally so the
 * UI can show the result before calling nextCard().
 *
 * State is stored in Compose-observable [mutableStateOf] fields so the
 * composable recomposes when the result changes.
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

    /** Current input mode. */
    private var _inputMode: InputMode by mutableStateOf(InputMode.KEYBOARD)

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
    override val supportsPause: Boolean get() = false

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
            return SessionProgress(
                current = s.currentIndex + 1,
                total = s.cards.size
            )
        }

    override val isComplete: Boolean
        get() = session?.isComplete == true && pendingCard == null

    override val inputText: String
        get() = "" // Input is managed by the composable

    override val inputModeConfig: InputModeConfig
        get() = InputModeConfig(
            availableModes = setOf(InputMode.KEYBOARD, InputMode.VOICE, InputMode.WORD_BANK),
            defaultMode = InputMode.KEYBOARD,
            showInputModeButtons = false
        )

    override val lastResult: AnswerResult?
        get() = pendingAnswerResult

    override val sessionActive: Boolean
        get() {
            val s = session ?: return false
            return !s.isComplete
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

    override fun onInputChanged(text: String) {
        // Input is managed locally in the composable; no-op
    }

    override fun submitAnswer(): AnswerResult? {
        // VerbDrill uses submitAnswerWithInput instead
        return null
    }

    /**
     * Submit an answer with the given input text. This is the primary method
     * called by the VerbDrill integration.
     */
    fun submitAnswerWithInput(input: String): AnswerResult? {
        val s = session ?: return null
        if (s.isComplete) return null
        if (s.currentIndex >= s.cards.size) return null

        val card = s.cards[s.currentIndex]
        val isCorrect = Normalizer.normalize(input) == Normalizer.normalize(card.answer)

        pendingCard = card
        pendingAnswerResult = AnswerResult(
            correct = isCorrect,
            displayAnswer = card.answer
        )

        // Forward to ViewModel (this advances the index internally)
        viewModel.submitAnswer(input)
        viewModel.recordAnswerTime()

        return pendingAnswerResult
    }

    override fun showAnswer(): String? {
        val s = session ?: return null
        val card = s.cards.getOrElse(s.currentIndex) { return null }
        return card.answer
    }

    override fun nextCard() {
        val hadPending = pendingCard != null
        pendingCard = null
        pendingAnswerResult = null
        _selectedWords = emptyList()
        cachedWordBankCardId = null
        cachedWordBank = emptyList()
        if (!hadPending) {
            viewModel.nextCardManual()
        }
    }

    override fun prevCard() {
        viewModel.prevCard()
        pendingCard = null
        pendingAnswerResult = null
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
    }

    // ── Word Bank ─────────────────────────────────────────────────────────

    override fun getWordBankWords(): List<String> {
        val s = session ?: return emptyList()
        val card = pendingCard ?: s.cards.getOrElse(s.currentIndex) { return emptyList() }

        // Cache word bank per card to avoid re-generating on every recomposition
        if (cachedWordBankCardId == card.id) return cachedWordBank

        val answerWords = card.answer.split(Regex("\\s+")).filter { it.isNotBlank() }
        val distractorWords = s.cards
            .filter { it.id != card.id }
            .flatMap { it.answer.split(Regex("\\s+")).filter { w -> w.isNotBlank() && w !in answerWords } }
            .distinct()
            .shuffled()
            .take(maxOf(0, 8 - answerWords.size))

        val bank = (answerWords + distractorWords).shuffled()
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
