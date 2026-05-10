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

    fun onInputChanged(text: String)
    fun submitAnswer(): AnswerResult?
    fun showAnswer(): String?
    fun nextCard()
    fun prevCard()

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
