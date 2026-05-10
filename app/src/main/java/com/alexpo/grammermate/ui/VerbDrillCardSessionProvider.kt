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

    private val session: VerbDrillSessionState?
        get() = viewModel.uiState.value.session

    override val currentCard: SessionCard?
        get() {
            val s = session ?: return null
            if (s.isComplete) return null
            // If we have a pending result, show the card that was answered
            if (pendingCard != null) return pendingCard
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
            availableModes = setOf(InputMode.KEYBOARD),
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

        return pendingAnswerResult
    }

    override fun showAnswer(): String? {
        val s = session ?: return null
        val card = s.cards.getOrElse(s.currentIndex) { return null }
        return card.answer
    }

    override fun nextCard() {
        pendingCard = null
        pendingAnswerResult = null
        // The ViewModel already advanced the index in submitAnswer.
    }

    override fun prevCard() {
        // VerbDrill does not support backward navigation
    }

    override fun requestExit() {
        viewModel.exitSession()
    }

    override fun requestNextBatch() {
        viewModel.nextBatch()
    }
}
