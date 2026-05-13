package com.alexpo.grammermate.data

/**
 * A card that can be presented in a training session.
 * Both [SentenceCard] and [VerbDrillCard] implement this interface.
 */
interface SessionCard {
    val id: String
    val promptRu: String
    val acceptedAnswers: List<String>
}

/** Current position within a card session. */
data class SessionProgress(
    val current: Int,
    val total: Int
)

/** Result of checking a user's answer. */
data class AnswerResult(
    val correct: Boolean,
    val displayAnswer: String,
    val hintShown: Boolean = false
)

/** Configuration for input modes available in a session. */
data class InputModeConfig(
    val availableModes: Set<InputMode>,
    val defaultMode: InputMode,
    val showInputModeButtons: Boolean
)

/** Optional capabilities a card session may support. All default to false. */
interface CardSessionCapabilities {
    val supportsTts: Boolean get() = false
    val supportsVoiceInput: Boolean get() = false
    val supportsWordBank: Boolean get() = false
    val supportsFlagging: Boolean get() = false
    val supportsNavigation: Boolean get() = false
    val supportsPause: Boolean get() = false
}

/**
 * Contract that a card session provider must implement.
 * Adapters wrap existing ViewModels to satisfy this interface.
 */
interface CardSessionContract : CardSessionCapabilities {
    val currentCard: SessionCard?
    val progress: SessionProgress
    val isComplete: Boolean
    val inputText: String
    val inputModeConfig: InputModeConfig
    val lastResult: AnswerResult?
    val sessionActive: Boolean

    /** Current TTS state for speaker button rendering. */
    val ttsState: TtsState get() = TtsState.IDLE

    /** Current input mode (VOICE, KEYBOARD, WORD_BANK). */
    val currentInputMode: InputMode get() = inputModeConfig.defaultMode

    /** Language ID for voice recognition locale resolution. */
    val languageId: String get() = "en"

    /** Current typing speed in words per minute. */
    val currentSpeedWpm: Int get() = 0

    /** Font size multiplier for prompt text. Range [1.0, 2.0]. */
    val textScale: Float get() = 1.0f

    fun onInputChanged(text: String)
    fun submitAnswer(): AnswerResult?
    fun showAnswer(): String?
    fun nextCard()
    fun prevCard()

    /** Called when voice recognition returns a result. */
    fun onVoiceInputResult(text: String) { onInputChanged(text) }

    /** Set the current input mode. */
    fun setInputMode(mode: InputMode) {}

    /** Get the currently selected word bank words (in order). */
    fun getSelectedWords(): List<String> = emptyList()

    // Optional capabilities with default no-op implementations
    fun getWordBankWords(): List<String> = emptyList()
    fun selectWordFromBank(word: String) {}
    fun removeLastSelectedWord() {}
    fun speakTts() {}
    fun stopTts() {}
    fun flagCurrentCard() {}
    fun unflagCurrentCard() {}
    fun isCurrentCardFlagged(): Boolean = false
    fun hideCurrentCard() {}
    fun exportFlaggedCards(): String? = null
    fun togglePause() {}
    fun requestExit() {}
    fun requestNextBatch() {}
}
