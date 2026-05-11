package com.alexpo.grammermate.ui.helpers

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
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TtsState

class DailyPracticeSessionProvider(
    private val cards: List<SentenceCard>,
    private val onBlockComplete: () -> Unit,
    override val languageId: String = "en",
    private val onAnswerChecked: (input: String, correct: Boolean, inputMode: InputMode) -> Unit = { _, _, _ -> },
    private val onSpeakTts: (String) -> Unit = {},
    private val onStopTts: () -> Unit = {},
    private val ttsStateProvider: () -> TtsState = { TtsState.IDLE },
    private val onExit: () -> Unit = {}
) : CardSessionContract {

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
    private var incorrectAttempts: Int by mutableStateOf(0)
    var hintAnswer: String? by mutableStateOf(null)
        private set
    var voiceTriggerToken: Int by mutableStateOf(0)
        private set
    var showIncorrectFeedback: Boolean by mutableStateOf(false)
        private set
    var remainingAttempts: Int by mutableStateOf(3)
        private set

    override val supportsTts: Boolean get() = true
    override val supportsVoiceInput: Boolean get() = true
    override val supportsWordBank: Boolean get() = true
    override val supportsFlagging: Boolean get() = false
    override val supportsNavigation: Boolean get() = true
    override val supportsPause: Boolean get() = true
    override val ttsState: TtsState get() = ttsStateProvider()

    override val currentCard: SessionCard?
        get() {
            if (_pendingCard != null) return _pendingCard
            if (currentIndex >= cards.size) return null
            return cards[currentIndex]
        }

    override val progress: SessionProgress
        get() = SessionProgress(
            current = (currentIndex + 1).coerceAtMost(cards.size),
            total = cards.size
        )

    override val isComplete: Boolean
        get() = currentIndex >= cards.size && _pendingCard == null

    override val inputText: String get() = ""

    override val inputModeConfig: InputModeConfig
        get() = InputModeConfig(
            availableModes = setOf(InputMode.KEYBOARD, InputMode.VOICE, InputMode.WORD_BANK),
            defaultMode = _inputMode,
            showInputModeButtons = true
        )

    override val lastResult: AnswerResult? get() = _pendingResult

    override val sessionActive: Boolean
        get() {
            if (_isPaused) return false
            if (hintAnswer != null) return false
            return currentIndex < cards.size
        }

    override val currentInputMode: InputMode get() = _inputMode

    fun clearIncorrectFeedback() {
        showIncorrectFeedback = false
    }

    override fun onInputChanged(text: String) {
        pendingInput = text
        if (text.isNotBlank() && hintAnswer != null) {
            hintAnswer = null
            _isPaused = false
            incorrectAttempts = 0
            remainingAttempts = 3
        }
    }

    override fun submitAnswer(): AnswerResult? {
        if (currentIndex >= cards.size) return null
        val card = cards[currentIndex]
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
            onAnswerChecked(input, true, _inputMode)
        } else {
            onAnswerChecked(input, false, _inputMode)
            incorrectAttempts++
            remainingAttempts = 3 - incorrectAttempts
            if (incorrectAttempts >= 3) {
                hintAnswer = card.acceptedAnswers.first()
                _pendingCard = card
                showIncorrectFeedback = false
                _isPaused = true
            } else {
                showIncorrectFeedback = true
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
        if (currentIndex >= cards.size) return null
        val card = cards[currentIndex]
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
        currentIndex++
        if (_inputMode == InputMode.VOICE) {
            voiceTriggerToken++
        }
        if (currentIndex >= cards.size) {
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

    override fun getWordBankWords(): List<String> {
        val card = _pendingCard ?: cards.getOrNull(currentIndex) ?: return emptyList()
        if (cachedWordBankCardId == card.id) return cachedWordBank
        val answerWords = card.acceptedAnswers.first().split(Regex("\\s+")).filter { it.isNotBlank() }
        val distractorWords = cards
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

    override fun speakTts() {
        val card = _pendingCard ?: cards.getOrNull(currentIndex) ?: return
        val text = card.acceptedAnswers.firstOrNull() ?: return
        onSpeakTts(text)
    }
    override fun stopTts() { onStopTts() }

    override fun requestExit() { onExit() }
    override fun requestNextBatch() { onBlockComplete() }
}
