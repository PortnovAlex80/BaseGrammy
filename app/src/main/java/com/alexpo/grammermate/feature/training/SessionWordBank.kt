package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.TrainingStateAccess
import kotlinx.coroutines.flow.MutableStateFlow

/** Keeps the existing word-bank compatibility mode's selection and generation effects together. */
internal class SessionWordBank(
    private val stateAccess: TrainingStateAccess,
    private val inputTextFlow: MutableStateFlow<String>,
    private val generator: WordBankGenerator,
    private val sessionCards: () -> List<SessionCard>
) {
    private val audit get() = AuditLogger.getInstanceOrNull()

    private fun currentCardId(): String {
        val cards = sessionCards()
        if (cards.isEmpty()) return ""
        val index = stateAccess.uiState.value.cardSession.currentIndex.coerceIn(0, cards.lastIndex)
        return cards.getOrNull(index)?.id ?: ""
    }

    fun select(word: String) {
        val currentSelected = stateAccess.uiState.value.cardSession.selectedWords
        val newSelected = currentSelected + word
        audit?.wordBankSelect(word, newSelected.size, currentCardId())
        inputTextFlow.value = newSelected.joinToString(" ")
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(selectedWords = newSelected))
        }
    }

    fun removeLast() {
        val currentSelected = stateAccess.uiState.value.cardSession.selectedWords
        if (currentSelected.isEmpty()) return
        val newSelected = currentSelected.dropLast(1)
        audit?.wordBankRemove(newSelected.size, currentCardId())
        inputTextFlow.value = newSelected.joinToString(" ")
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(selectedWords = newSelected))
        }
    }

    fun update() {
        val state = stateAccess.uiState.value
        if (state.cardSession.hintLevel != HintLevel.EASY) return
        val card = state.cardSession.currentCard
        if (card == null) {
            // Preserve the existing no-card behavior: selection clears without changing typed input.
            stateAccess.updateState {
                it.copy(cardSession = it.cardSession.copy(wordBankWords = emptyList(), selectedWords = emptyList()))
            }
            return
        }

        val correctAnswer = card.acceptedAnswers.firstOrNull() ?: ""
        val verbStyle = state.cardSession.screenMode in listOf(
            TrainingScreenMode.VERB_DRILL, TrainingScreenMode.DAILY_VERBS
        )
        val words = if (verbStyle) {
            val allAnswers = sessionCards().mapNotNull { it.acceptedAnswers.firstOrNull() }
            generator.generateForVerb(correctAnswer, allAnswers)
        } else {
            val allCards = state.navigation.lessons.flatMap { it.cards }
            generator.generateForSentence(correctAnswer, allCards)
        }

        inputTextFlow.value = ""
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(wordBankWords = words, selectedWords = emptyList()))
        }
    }
}
