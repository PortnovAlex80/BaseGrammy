package com.alexpo.grammermate.ui.helpers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.InputModeConfig
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionProgress
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VerbDrillCard

/**
 * CardSessionContract adapter for Daily Practice Blocks 1 (Translation) and 3 (Verb Drill).
 * Block 2 (Vocab Flashcard) does NOT use this — the UI switches composables at block boundaries.
 *
 * Mirrors the retry/hint flow from VerbDrillCardSessionProvider:
 * - Correct answer -> set result (pendingCard/pendingResult)
 * - Wrong answer -> show inline error, increment attempt counter, stay on card
 * - 3 wrong attempts -> auto-show answer as hint (input controls stay visible)
 * - Manual "Show Answer" -> show answer as hint (input controls stay visible)
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
    private val ttsStateProvider: () -> TtsState = { TtsState.IDLE },
    private val onExit: () -> Unit = {},
    private val onCardAdvanced: (DailyTask) -> Unit = {}
) : CardSessionContract {

    /** All tasks matching the requested block type, capped by the global per-block limit. */
    private val blockCards: List<DailyTask> = tasks
        .filter { it.blockType == blockType }
        .take(DailySessionComposer.CARDS_PER_BLOCK)

    private var currentIndex: Int by mutableStateOf(0)
    private var _pendingCard: SessionCard? by mutableStateOf(null)
    var pendingAnswerResult: AnswerResult? by mutableStateOf(null)
        private set
    private var _pendingResult: AnswerResult? by mutableStateOf(null)
    private var _isPaused: Boolean by mutableStateOf(false)
    private var _inputMode: InputMode by mutableStateOf(InputMode.VOICE)
    private var _selectedWords: List<String> by mutableStateOf(emptyList())

    private var cachedWordBankCardId: String? = null
    private var cachedWordBank: List<String> = emptyList()
    private var pendingInput: String by mutableStateOf("")

    /** Consecutive incorrect attempts for the current card. */
    private var incorrectAttempts: Int by mutableStateOf(0)

    /** When non-null, the answer is being shown as a hint (auto or manual). */
    var hintAnswer: String? by mutableStateOf(null)
        private set

    /** Token incremented to trigger automatic voice recognition (mirrors VerbDrillCardSessionProvider). */
    var voiceTriggerToken: Int by mutableStateOf(0)
        private set

    /** When true, shows "Incorrect" feedback inline in input controls (wrong attempt < 3). */
    var showIncorrectFeedback: Boolean by mutableStateOf(false)
        private set

    /** Remaining attempts before hint auto-shows. */
    var remainingAttempts: Int by mutableStateOf(3)
        private set

    // ── Capabilities ─────────────────────────────────────────────────────

    override val supportsTts: Boolean get() = true
    override val supportsVoiceInput: Boolean get() = true
    override val supportsWordBank: Boolean get() = true
    override val supportsFlagging: Boolean get() = false
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
            if (_isPaused) return false
            if (hintAnswer != null) return false
            return currentIndex < blockCards.size
        }

    override val currentInputMode: InputMode
        get() = _inputMode

    // ── Actions ──────────────────────────────────────────────────────────

    /** Clear incorrect feedback when the user starts typing a new attempt. */
    fun clearIncorrectFeedback() {
        showIncorrectFeedback = false
    }

    override fun onInputChanged(text: String) {
        pendingInput = text
        // Clear hint when user starts typing after seeing the answer
        if (text.isNotBlank() && hintAnswer != null) {
            hintAnswer = null
            _isPaused = false
            incorrectAttempts = 0
            remainingAttempts = 3
        }
    }

    override fun submitAnswer(): AnswerResult? {
        if (currentIndex >= blockCards.size) return null
        val task = blockCards[currentIndex]
        val card = taskToSessionCard(task) ?: return null

        // Already showing hint — ignore further submissions until nextCard()
        if (hintAnswer != null) return null

        val input = if (currentInputMode == InputMode.WORD_BANK && _selectedWords.isNotEmpty()) {
            _selectedWords.joinToString(" ")
        } else {
            pendingInput
        }

        val isCorrect = card.acceptedAnswers.any { ans ->
            Normalizer.normalize(input) == Normalizer.normalize(ans)
        }

        if (isCorrect) {
            _pendingCard = card
            _pendingResult = AnswerResult(correct = true, displayAnswer = card.acceptedAnswers.first())
            pendingAnswerResult = _pendingResult
            incorrectAttempts = 0
            showIncorrectFeedback = false
            remainingAttempts = 3
            onAnswerChecked(input, true)
        } else {
            onAnswerChecked(input, false)
            incorrectAttempts++
            remainingAttempts = 3 - incorrectAttempts
            if (incorrectAttempts >= 3) {
                // Auto-show answer after 3 wrong attempts
                hintAnswer = card.acceptedAnswers.first()
                _pendingCard = card
                showIncorrectFeedback = false
                _isPaused = true
            } else {
                // Show inline "Incorrect" feedback for attempts < 3
                showIncorrectFeedback = true
                // Auto-trigger voice recognition in VOICE mode for retry
                if (_inputMode == InputMode.VOICE) {
                    voiceTriggerToken++
                }
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
        // Manual eye button — show answer as hint, keep input controls visible
        hintAnswer = card.acceptedAnswers.first()
        _pendingCard = card
        incorrectAttempts = 3
        showIncorrectFeedback = false
        remainingAttempts = 0
        _isPaused = true
        return card.acceptedAnswers.first()
    }

    override fun nextCard() {
        _pendingCard = null
        _pendingResult = null
        pendingAnswerResult = null
        _isPaused = false
        _selectedWords = emptyList()
        cachedWordBankCardId = null
        cachedWordBank = emptyList()
        pendingInput = ""
        hintAnswer = null
        incorrectAttempts = 0
        showIncorrectFeedback = false
        remainingAttempts = 3

        // Notify caller about the card being advanced (for progress persistence)
        // Only VOICE and KEYBOARD count as "practiced"; WORD_BANK does not.
        if (currentIndex < blockCards.size && _inputMode != InputMode.WORD_BANK) {
            onCardAdvanced(blockCards[currentIndex])
        }

        // Always advance to next card
        currentIndex++

        // Auto-trigger voice recognition when advancing to next card in VOICE mode
        if (_inputMode == InputMode.VOICE) {
            voiceTriggerToken++
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
            _isPaused = false
            _selectedWords = emptyList()
            cachedWordBankCardId = null
            cachedWordBank = emptyList()
            hintAnswer = null
            incorrectAttempts = 0
            showIncorrectFeedback = false
            remainingAttempts = 3
        }
    }

    override fun togglePause() {
        if (_isPaused) {
            // Play pressed — unpause and restart current card (fresh attempt)
            _isPaused = false
            _pendingCard = null
            _pendingResult = null
            pendingAnswerResult = null
            hintAnswer = null
            incorrectAttempts = 0
            showIncorrectFeedback = false
            remainingAttempts = 3
            _selectedWords = emptyList()
            cachedWordBankCardId = null
            cachedWordBank = emptyList()
            pendingInput = ""
        } else {
            _isPaused = true
        }
    }

    override fun setInputMode(mode: InputMode) {
        _inputMode = mode
        _selectedWords = emptyList()
        if (mode == InputMode.VOICE) {
            voiceTriggerToken++
        }
    }

    // ── Word Bank ────────────────────────────────────────────────────────

    override fun getWordBankWords(): List<String> {
        val card = _pendingCard ?: taskToSessionCard(blockCards.getOrNull(currentIndex) ?: return emptyList())
            ?: return emptyList()

        if (cachedWordBankCardId == card.id) return cachedWordBank

        val answerWords = card.acceptedAnswers.first().split(Regex("\\s+")).filter { it.isNotBlank() }
        val distractorWords = blockCards
            .mapNotNull { taskToSessionCard(it) }
            .filter { it.id != card.id }
            .flatMap { it.acceptedAnswers.first().split(Regex("\\s+")).filter { w -> w.isNotBlank() && w !in answerWords } }
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
