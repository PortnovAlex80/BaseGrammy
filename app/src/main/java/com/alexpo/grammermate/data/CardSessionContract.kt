package com.alexpo.grammermate.data

/**
 * Unified state model for card session lifecycle.
 *
 * All card-based drill modes (training via SessionRunner,
 * daily practice via SessionRunner)
 * implement this interface so the UI layer can query session state uniformly.
 *
 * This is a read-only state interface. Actions (submit, nextCard, etc.) remain
 * on [CardSessionContract] because they have mode-specific signatures.
 *
 * The three core state dimensions:
 * - **isActive**: session is running, timer is ticking, input is accepted
 * - **isPaused**: session is paused (user-initiated or between cards)
 * - **isHintShown**: answer is revealed, session paused awaiting advance
 *
 * These three are mutually exclusive in normal operation:
 *   isActive = true  => isPaused = false, isHintShown = false
 *   isPaused  = true  => isActive = false, isHintShown = false
 *   isHintShown = true => isActive = false, isPaused = true (hint implies pause)
 */
interface CardSessionStateModel {
    /** Session is active: timer running, input accepted, card displayed. */
    val isActive: Boolean

    /** Session is paused: timer stopped, awaiting user action to resume. */
    val isPaused: Boolean

    /** Answer hint is shown: correct answer visible, session paused until advance. */
    val isHintShown: Boolean

    /** Session can accept an answer submission right now. */
    val canSubmit: Boolean

    /** Current card is available for display. */
    val hasCurrentCard: Boolean

    /** All cards have been processed. */
    val isComplete: Boolean

    /** Current position within the card deck. */
    val progress: SessionProgress
}

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
 *
 * Extends [CardSessionStateModel] for unified state queries across all session types.
 */
interface CardSessionContract : CardSessionCapabilities, CardSessionStateModel {
    val currentCard: SessionCard?
    val inputText: String
    val inputModeConfig: InputModeConfig
    val lastResult: AnswerResult?
    val sessionActive: Boolean

    // ── CardSessionStateModel defaults ──────────────────────────────────
    // Mapped from existing CardSessionContract properties so that existing
    // implementations
    // work without changes. SessionRunnerAdapter overrides these explicitly.

    override val isActive: Boolean get() = sessionActive && !isHintShown
    override val isPaused: Boolean get() = !sessionActive && !isComplete
    override val isHintShown: Boolean get() = lastResult?.hintShown == true
    override val canSubmit: Boolean get() = currentCard != null
    override val hasCurrentCard: Boolean get() = currentCard != null

    /** Current TTS state for speaker button rendering. */
    val ttsState: TtsState get() = TtsState.Idle

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

    /** Whether voice auto-start is enabled. When true, mic button only switches mode (LaunchedEffect handles launch). */
    val voiceAutoStart: Boolean get() = false

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
