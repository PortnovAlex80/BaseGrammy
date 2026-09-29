package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.testharness.FakeTrainingStateAccess
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionWordBankTest {
    private val card = SentenceCard("bank-1", "prompt", listOf("one two"))

    @Test
    fun selectionAndUndoKeepTypedInputInSync() {
        val state = FakeTrainingStateAccess(TrainingUiState(cardSession = CardSessionState(
            currentCard = card, hintLevel = HintLevel.EASY
        )))
        val input = MutableStateFlow("")
        val bank = SessionWordBank(state, input, WordBankGenerator) { listOf(card) }

        bank.select("one")
        bank.select("two")
        assertEquals(listOf("one", "two"), state.uiState.value.cardSession.selectedWords)
        assertEquals("one two", input.value)

        bank.removeLast()
        assertEquals(listOf("one"), state.uiState.value.cardSession.selectedWords)
        assertEquals("one", input.value)
    }

    @Test
    fun regenerationClearsInputButNoCardBranchPreservesExistingText() {
        val state = FakeTrainingStateAccess(TrainingUiState(cardSession = CardSessionState(
            currentCard = card, hintLevel = HintLevel.EASY,
            selectedWords = listOf("old")
        )))
        val input = MutableStateFlow("old")
        val bank = SessionWordBank(state, input, WordBankGenerator) { listOf(card) }

        bank.update()
        assertEquals("", input.value)
        assertTrue(state.uiState.value.cardSession.selectedWords.isEmpty())

        input.value = "pending"
        state.updateState { it.copy(cardSession = it.cardSession.copy(currentCard = null, selectedWords = listOf("old"))) }
        bank.update()
        assertEquals("pending", input.value)
        assertTrue(state.uiState.value.cardSession.selectedWords.isEmpty())
        assertTrue(state.uiState.value.cardSession.wordBankWords.isEmpty())
    }
}
