package com.alexpo.grammermate.ui.helpers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionCard

/**
 * Reusable Compose-state holder for the retry/hint state machine used by
 * card session providers (VerbDrill, DailyPractice, etc.).
 *
 * Encapsulates the common pattern:
 * - Correct answer -> reset state, return result
 * - Wrong answer -> increment attempts, show inline feedback or auto-show hint
 * - 3 wrong attempts -> auto-show answer as hint (input controls stay visible)
 * - Manual "Show Answer" -> force show hint
 * - User types after hint -> clear hint, reset attempts
 *
 * Each provider creates its own instance and delegates retry/hint fields to it.
 */
class CardSessionStateMachine(
    val maxAttempts: Int = 3,
    private val answerProvider: (SessionCard) -> String
) {

    /** Consecutive incorrect attempts for the current card. */
    var incorrectAttempts: Int by mutableStateOf(0)
        private set

    /** When non-null, the answer is being shown as a hint (auto or manual). */
    var hintAnswer: String? by mutableStateOf(null)
        private set

    /** When true, shows "Incorrect" feedback inline in input controls (wrong attempt < max). */
    var showIncorrectFeedback: Boolean by mutableStateOf(false)
        private set

    /** Remaining attempts before hint auto-shows. */
    var remainingAttempts: Int by mutableStateOf(maxAttempts)
        private set

    /** Whether the session is paused (answer shown, waiting for user to continue). */
    var isPaused: Boolean by mutableStateOf(false)
        private set

    /** Token incremented to trigger automatic voice recognition. */
    var voiceTriggerToken: Int by mutableStateOf(0)
        private set

    // ── Result of onSubmit ────────────────────────────────────────────────

    /**
     * Sealed result from [onSubmit], so callers can distinguish correct vs wrong
     * without re-checking boolean parameters.
     */
    sealed class OnSubmitResult {
        /** Answer was correct. [result] contains the display answer. */
        data class Correct(val result: AnswerResult) : OnSubmitResult()

        /** Answer was wrong but attempts remain. */
        data class Wrong(val attemptNumber: Int, val remaining: Int) : OnSubmitResult()

        /** Max attempts reached — hint is now shown. */
        data class HintShown(val answer: String) : OnSubmitResult()
    }

    // ── Actions ──────────────────────────────────────────────────────────

    /**
     * Handle a submitted answer. Returns the appropriate [OnSubmitResult].
     *
     * @param isCorrect Whether the answer was normalized-correct.
     * @param card The card being answered (used to extract the hint answer).
     * @param inputMode Current input mode (VOICE triggers auto-retry token increment).
     * @param onCorrect Callback for correct-answer side effects (e.g., recording time).
     * @param onWrong Callback for wrong-answer side effects (e.g., tracking).
     */
    fun onSubmit(
        isCorrect: Boolean,
        card: SessionCard,
        inputMode: InputMode = InputMode.KEYBOARD,
        onCorrect: () -> Unit = {},
        onWrong: () -> Unit = {}
    ): OnSubmitResult {
        // Already showing hint — ignore further submissions
        if (hintAnswer != null) return OnSubmitResult.Wrong(incorrectAttempts, remainingAttempts)

        if (isCorrect) {
            val answer = answerProvider(card)
            onCorrect()
            incorrectAttempts = 0
            showIncorrectFeedback = false
            remainingAttempts = maxAttempts
            return OnSubmitResult.Correct(AnswerResult(correct = true, displayAnswer = answer))
        } else {
            onWrong()
            incorrectAttempts++
            remainingAttempts = maxAttempts - incorrectAttempts
            if (incorrectAttempts >= maxAttempts) {
                // Auto-show answer after max wrong attempts
                val answer = answerProvider(card)
                hintAnswer = answer
                showIncorrectFeedback = false
                isPaused = true
                return OnSubmitResult.HintShown(answer)
            } else {
                // Show inline "Incorrect" feedback for attempts < max
                showIncorrectFeedback = true
                // Auto-trigger voice recognition in VOICE mode for retry
                if (inputMode == InputMode.VOICE) {
                    voiceTriggerToken++
                }
                return OnSubmitResult.Wrong(incorrectAttempts, remainingAttempts)
            }
        }
    }

    /**
     * Handle input text changes. Clears hint and resets attempts when the user
     * starts typing after seeing the answer.
     */
    fun onInputChanged(text: String) {
        if (text.isNotBlank() && hintAnswer != null) {
            hintAnswer = null
            isPaused = false
            incorrectAttempts = 0
            remainingAttempts = maxAttempts
        }
    }

    /**
     * Force-show the answer as a hint (manual eye button).
     * Returns the hint answer string.
     */
    fun showAnswer(card: SessionCard): String {
        val answer = answerProvider(card)
        hintAnswer = answer
        incorrectAttempts = maxAttempts
        showIncorrectFeedback = false
        remainingAttempts = 0
        isPaused = true
        return answer
    }

    /**
     * Clear incorrect feedback when the user starts typing a new attempt.
     */
    fun clearIncorrectFeedback() {
        showIncorrectFeedback = false
    }

    /**
     * Reset all retry/hint state to initial values.
     * Called when advancing to the next card, going back, or restarting.
     */
    fun reset() {
        incorrectAttempts = 0
        hintAnswer = null
        showIncorrectFeedback = false
        remainingAttempts = maxAttempts
        isPaused = false
    }

    /**
     * Increment the voice trigger token (e.g., when switching to VOICE mode or advancing cards).
     */
    fun triggerVoice() {
        voiceTriggerToken++
    }

    /**
     * Set pause state without showing a hint.
     * Used by togglePause when the user explicitly pauses without having a hint shown.
     */
    fun pause() {
        isPaused = true
    }
}
