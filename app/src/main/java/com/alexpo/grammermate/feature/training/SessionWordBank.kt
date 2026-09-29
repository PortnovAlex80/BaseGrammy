package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
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
        val index = stateAccess.currentState.cardSession.currentIndex.coerceIn(0, cards.lastIndex)
        return cards.getOrNull(index)?.id ?: ""
    }

    fun select(word: String) {
        val cardSession = stateAccess.currentState.cardSession
        val currentSelected = cardSession.selectedWords
        val availableCount = cardSession.wordBankWords.count { it == word }
        if (availableCount == 0 || currentSelected.count { it == word } >= availableCount) return
        val newSelected = currentSelected + word
        audit?.wordBankSelect(word, newSelected.size, currentCardId())
        inputTextFlow.value = newSelected.joinToString(" ")
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(selectedWords = newSelected))
        }
    }

    fun removeLast() {
        val currentSelected = stateAccess.currentState.cardSession.selectedWords
        if (currentSelected.isEmpty()) return
        val newSelected = currentSelected.dropLast(1)
        audit?.wordBankRemove(newSelected.size, currentCardId())
        inputTextFlow.value = newSelected.joinToString(" ")
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(selectedWords = newSelected))
        }
    }

    fun update() {
        val state = stateAccess.currentState
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

        // Rebuilding bank choices on resume must not erase a typed answer in
        // keyboard/voice mode. Card navigation clears input in its own transition.
        if (state.cardSession.inputMode == InputMode.WORD_BANK) inputTextFlow.value = ""
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(wordBankWords = words, selectedWords = emptyList()))
        }
    }
}
