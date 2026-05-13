package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.HiddenCardStore
import com.alexpo.grammermate.data.TrainingUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages bad-sentence flagging and card hiding for both training and daily-practice sessions.
 *
 * Delegated from [com.alexpo.grammermate.ui.TrainingViewModel] to keep the ViewModel thin.
 * All state reads/writes go through [TrainingStateAccess].
 */
class BadSentenceHelper(
    private val stateAccess: TrainingStateAccess,
    private val badSentenceStore: BadSentenceStore,
    private val hiddenCardStore: HiddenCardStore,
) {
    /** In-memory set of card IDs flagged as bad during the current daily session. */
    private val dailyBadCardIds = mutableSetOf<String>()

    /** Called by flagBadSentence when in drill mode — ViewModel wires to advanceDrillCard(). */
    var onAdvanceDrillCard: () -> Unit = {}

    /** Called by hideCurrentCard after hiding — ViewModel wires to skipToNextCard(). */
    var onSkipToNextCard: () -> Unit = {}

    // ── Training-session bad sentences ─────────────────────────────────────

    fun flagBadSentence() {
        val card = stateAccess.uiState.value.cardSession.currentCard ?: return
        val state = stateAccess.uiState.value
        val packId = state.navigation.activePackId ?: return
        badSentenceStore.addBadSentence(
            packId = packId,
            cardId = card.id,
            languageId = state.navigation.selectedLanguageId,
            sentence = card.promptRu,
            translation = card.acceptedAnswers.joinToString(" / "),
            mode = "training"
        )
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(badSentenceCount = badSentenceStore.getBadSentenceCount(packId)))
        }
        if (state.drill.isDrillMode) {
            onAdvanceDrillCard()
        }
    }

    fun unflagBadSentence() {
        val card = stateAccess.uiState.value.cardSession.currentCard ?: return
        val packId = stateAccess.uiState.value.navigation.activePackId ?: return
        badSentenceStore.removeBadSentence(packId, card.id)
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(badSentenceCount = badSentenceStore.getBadSentenceCount(packId)))
        }
    }

    fun isBadSentence(): Boolean {
        val card = stateAccess.uiState.value.cardSession.currentCard ?: return false
        val packId = stateAccess.uiState.value.navigation.activePackId ?: return false
        return badSentenceStore.isBadSentence(packId, card.id)
    }

    fun exportBadSentences(): String? {
        val packId = stateAccess.uiState.value.navigation.activePackId ?: return null
        val entries = badSentenceStore.getBadSentences(packId)
        if (entries.isEmpty()) return null
        val file = badSentenceStore.exportUnified()
        return file.absolutePath
    }

    // ── Daily Practice bad sentences ───────────────────────────────────────

    fun flagDailyBadSentence(cardId: String, languageId: String, sentence: String, translation: String, mode: String) {
        val packId = stateAccess.uiState.value.navigation.activePackId ?: return
        badSentenceStore.addBadSentence(
            packId = packId,
            cardId = cardId,
            languageId = languageId,
            sentence = sentence,
            translation = translation,
            mode = mode
        )
        dailyBadCardIds.add(cardId)
    }

    fun unflagDailyBadSentence(cardId: String) {
        val packId = stateAccess.uiState.value.navigation.activePackId ?: return
        badSentenceStore.removeBadSentence(packId, cardId)
        dailyBadCardIds.remove(cardId)
    }

    fun isDailyBadSentence(cardId: String): Boolean {
        return dailyBadCardIds.contains(cardId) ||
            stateAccess.uiState.value.navigation.activePackId?.let { badSentenceStore.isBadSentence(it, cardId) } == true
    }

    fun exportDailyBadSentences(): String? {
        val packId = stateAccess.uiState.value.navigation.activePackId ?: return null
        val entries = badSentenceStore.getBadSentences(packId)
        if (entries.isEmpty()) return null
        val file = badSentenceStore.exportUnified()
        return file.absolutePath
    }

    // ── Card hiding ────────────────────────────────────────────────────────

    fun hideCurrentCard() {
        val card = stateAccess.uiState.value.cardSession.currentCard ?: return
        hiddenCardStore.hideCard(card.id)
        onSkipToNextCard()
    }

    fun unhideCurrentCard() {
        val card = stateAccess.uiState.value.cardSession.currentCard ?: return
        hiddenCardStore.unhideCard(card.id)
    }

    fun isCurrentCardHidden(): Boolean {
        val card = stateAccess.uiState.value.cardSession.currentCard ?: return false
        return hiddenCardStore.isHidden(card.id)
    }

    // ── Utility ────────────────────────────────────────────────────────────

    /** Compute the bad-sentence count for the active pack (used during drill transitions). */
    fun getBadSentenceCount(): Int {
        val packId = stateAccess.uiState.value.navigation.activePackId ?: return 0
        return badSentenceStore.getBadSentenceCount(packId)
    }
}
