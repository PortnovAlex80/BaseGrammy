package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.InputMode

/** Suppresses a repeated submit on the same card and races between voice launchers. */
internal class SubmissionDedupGuard(private val nowMs: () -> Long) {
    private data class Submission(
        val text: String,
        val timeMs: Long,
        val mode: InputMode,
        val deckEpoch: Long,
        val cardIndex: Int,
        val cardId: String?
    )

    private var deckEpoch = 0L
    private var last: Submission? = null

    /** A fresh deck may reuse card IDs and indices; voice races still span the switch. */
    fun onDeckReplaced() {
        deckEpoch++
    }

    fun shouldSuppress(text: String, mode: InputMode, cardIndex: Int, cardId: String?): Boolean {
        val now = nowMs()
        val previous = last
        val withinWindow = previous != null && now - previous.timeMs in 0 until 300
        val sameTarget = previous != null &&
            previous.deckEpoch == deckEpoch &&
            previous.cardIndex == cardIndex &&
            previous.cardId == cardId
        val voiceRace = mode == InputMode.VOICE || previous?.mode == InputMode.VOICE
        if (withinWindow && previous?.text == text && (voiceRace || sameTarget)) return true

        // Keep the old ordering: even blank or missing-card submits update the guard.
        last = Submission(text, now, mode, deckEpoch, cardIndex, cardId)
        return false
    }
}
