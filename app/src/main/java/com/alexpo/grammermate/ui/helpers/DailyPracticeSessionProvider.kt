package com.alexpo.grammermate.ui.helpers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.InputModeConfig
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionProgress
import com.alexpo.grammermate.data.TtsState

/**
 * CardSessionContract adapter for Daily Practice Blocks 1 (Translation) and 3 (Verb Drill).
 * Block 2 (Vocab Flashcard) does NOT use this — the UI switches composables at block boundaries.
 *
 * Create a fresh instance when the session enters a TRANSLATE or VERBS block.
 */
class DailyPracticeSessionProvider(
    private val tasks: List<DailyTask>,
    private val startOffset: Int,
    private val onBlockComplete: () -> Unit,
    override val languageId: String = "en"
) : CardSessionContract {

    private val blockCards: List<DailyTask> = tasks
        .filter { it is DailyTask.TranslateSentence || it is DailyTask.ConjugateVerb }
        .let { block ->
            val start = block.indexOfFirst { it == tasks[startOffset] }.coerceAtLeast(0)
            val firstType = block.getOrNull(start)?.blockType ?: return@let emptyList()
            block.drop(start).takeWhile { it.blockType == firstType }
        }

    private var currentIndex: Int by mutableStateOf(0)
    private var _pendingCard: SessionCard? by mutableStateOf(null)
    private var _pendingResult: AnswerResult? by mutableStateOf(null)
    private var _isPaused: Boolean by mutableStateOf(false)
    private var _inputMode: InputMode by mutableStateOf(InputMode.VOICE)
    private var _selectedWords: List<String> by mutableStateOf(emptyList())

    private var cachedWordBankCardId: String? = null
    private var cachedWordBank: List<String> = emptyList()
    private var pendingInput: String = ""

    // ── Capabilities ─────────────────────────────────────────────────────

    override val supportsTts: Boolean get() = true
    override val supportsVoiceInput: Boolean get() = true
    override val supportsWordBank: Boolean get() = true
    override val supportsNavigation: Boolean get() = true
    override val supportsPause: Boolean get() = true

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
        get() = !_isPaused && currentIndex < blockCards.size

    override val currentInputMode: InputMode
        get() = _inputMode

    // ── Actions ──────────────────────────────────────────────────────────

    override fun onInputChanged(text: String) {
        pendingInput = text
    }

    override fun submitAnswer(): AnswerResult? {
        if (currentIndex >= blockCards.size) return null
        val task = blockCards[currentIndex]
        val card = taskToSessionCard(task) ?: return null

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
        _isPaused = true
        return card.acceptedAnswers.first()
    }

    override fun nextCard() {
        val hadPending = _pendingCard != null || _isPaused

        _pendingCard = null
        _pendingResult = null
        _isPaused = false
        _selectedWords = emptyList()
        cachedWordBankCardId = null
        cachedWordBank = emptyList()
        pendingInput = ""

        if (hadPending) {
            currentIndex++
            if (currentIndex >= blockCards.size) {
                onBlockComplete()
            }
        }
    }

    override fun prevCard() {
        if (currentIndex > 0) {
            currentIndex--
            _pendingCard = null
            _pendingResult = null
            _isPaused = false
            _selectedWords = emptyList()
            cachedWordBankCardId = null
            cachedWordBank = emptyList()
        }
    }

    override fun togglePause() {
        if (_isPaused) {
            nextCard()
        } else {
            _isPaused = true
        }
    }

    override fun setInputMode(mode: InputMode) {
        _inputMode = mode
        _selectedWords = emptyList()
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

    override fun requestExit() {
        onBlockComplete()
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
