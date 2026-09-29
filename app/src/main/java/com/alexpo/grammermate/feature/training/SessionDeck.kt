package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionCard

/** One active card list and its submit-dedup epoch; no copy of TrainingUiState. */
internal class SessionDeck(submitClockMs: () -> Long) {
    private val submitGuard = SubmissionDedupGuard(submitClockMs)

    var cards: List<SessionCard> = emptyList()
        private set

    fun replace(next: List<SessionCard>) {
        cards = next
        submitGuard.onDeckReplaced()
    }

    fun current(index: Int): SessionCard? {
        if (cards.isEmpty()) return null
        return cards.getOrNull(index.coerceIn(0, cards.lastIndex))
    }

    fun shouldSuppress(text: String, mode: InputMode, index: Int, cardId: String?): Boolean =
        submitGuard.shouldSuppress(text, mode, index, cardId)
}
