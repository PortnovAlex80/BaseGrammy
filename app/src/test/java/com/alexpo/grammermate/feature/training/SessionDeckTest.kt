package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SentenceCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDeckTest {
    private val card = SentenceCard("same", "prompt", listOf("answer"))

    @Test
    fun replacementChangesKeyboardDedupEpochButPreservesVoiceRaceGuard() {
        val deck = SessionDeck { 1_000L }
        deck.replace(listOf(card))
        assertFalse(deck.shouldSuppress("answer", InputMode.KEYBOARD, 0, card.id))
        assertTrue(deck.shouldSuppress("answer", InputMode.KEYBOARD, 0, card.id))

        deck.replace(listOf(card))
        assertFalse(deck.shouldSuppress("answer", InputMode.KEYBOARD, 0, card.id))
        deck.replace(listOf(card))
        assertTrue(deck.shouldSuppress("answer", InputMode.VOICE, 0, card.id))
    }

    @Test
    fun currentCardClampsIndexAndEmptyDeckReturnsNull() {
        val deck = SessionDeck { 1_000L }
        assertEquals(null, deck.current(3))
        deck.replace(listOf(card))
        assertEquals(card, deck.current(-10))
        assertEquals(card, deck.current(10))
    }
}
