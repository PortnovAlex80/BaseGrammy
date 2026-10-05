package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.HiddenCardStore
import com.alexpo.grammermate.data.HiddenCardKeys
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.TrainingStateAccess
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

    // ── Training-session bad sentences ─────────────────────────────────────

    fun flagBadSentence(): BadSentenceResult {
        val card = stateAccess.currentState.cardSession.currentCard ?: return BadSentenceResult.None
        val state = stateAccess.currentState
        val packId = state.navigation.activePackId ?: return BadSentenceResult.None
        val reportId = reportKey(card) ?: return BadSentenceResult.None
        badSentenceStore.addBadSentence(
            packId = packId.value,
            cardId = reportId,
            languageId = state.navigation.selectedLanguageId?.value ?: return BadSentenceResult.None,
            sentence = card.promptRu,
            translation = card.acceptedAnswers.joinToString(" / "),
            mode = "training"
        )
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(badSentenceCount = badSentenceStore.getBadSentenceCount(packId.value)))
        }
        return BadSentenceResult.None
    }

    fun unflagBadSentence() {
        val card = stateAccess.currentState.cardSession.currentCard ?: return
        val packId = stateAccess.currentState.navigation.activePackId ?: return
        val reportId = reportKey(card) ?: return
        badSentenceStore.removeBadSentence(packId.value, reportId)
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(badSentenceCount = badSentenceStore.getBadSentenceCount(packId.value)))
        }
    }

    fun isBadSentence(): Boolean {
        val card = stateAccess.currentState.cardSession.currentCard ?: return false
        val packId = stateAccess.currentState.navigation.activePackId ?: return false
        val reportId = reportKey(card) ?: return false
        return badSentenceStore.isBadSentence(packId.value, reportId)
    }

    fun exportBadSentences(): String? {
        val packId = stateAccess.currentState.navigation.activePackId ?: return null
        val entries = badSentenceStore.getBadSentences(packId.value)
        if (entries.isEmpty()) return null
        val file = badSentenceStore.exportUnified()
        return file.absolutePath
    }

    // ── Daily Practice bad sentences ───────────────────────────────────────

    fun flagDailyBadSentence(cardId: String, languageId: String, sentence: String, translation: String, mode: String) {
        val packId = stateAccess.currentState.navigation.activePackId ?: return
        badSentenceStore.addBadSentence(
            packId = packId.value,
            cardId = cardId,
            languageId = languageId,
            sentence = sentence,
            translation = translation,
            mode = mode
        )
        dailyBadCardIds.add(cardId)
    }

    fun unflagDailyBadSentence(cardId: String) {
        val packId = stateAccess.currentState.navigation.activePackId ?: return
        badSentenceStore.removeBadSentence(packId.value, cardId)
        dailyBadCardIds.remove(cardId)
    }

    fun isDailyBadSentence(cardId: String): Boolean {
        return dailyBadCardIds.contains(cardId) ||
            stateAccess.currentState.navigation.activePackId?.let { badSentenceStore.isBadSentence(it.value, cardId) } == true
    }

    fun exportDailyBadSentences(): String? {
        val packId = stateAccess.currentState.navigation.activePackId ?: return null
        val entries = badSentenceStore.getBadSentences(packId.value)
        if (entries.isEmpty()) return null
        val file = badSentenceStore.exportUnified()
        return file.absolutePath
    }

    // ── Card hiding ────────────────────────────────────────────────────────

    fun hideCurrentCard(): BadSentenceResult {
        val card = stateAccess.currentState.cardSession.currentCard ?: return BadSentenceResult.None
        val key = hiddenKey(card) ?: return BadSentenceResult.None
        hiddenCardStore.hideCard(key)
        return BadSentenceResult.SkipToNextCard
    }

    fun unhideCurrentCard() {
        val card = stateAccess.currentState.cardSession.currentCard ?: return
        hiddenKey(card)?.let(hiddenCardStore::unhideCard)
    }

    fun isCurrentCardHidden(): Boolean {
        val card = stateAccess.currentState.cardSession.currentCard ?: return false
        return hiddenKey(card)?.let(hiddenCardStore::isHidden) ?: false
    }

    // ── Utility ────────────────────────────────────────────────────────────

    /** Sentence feedback follows its source lesson, including mixed review slots. */
    private fun reportKey(card: SessionCard): String? =
        if (card is SentenceCard) hiddenKey(card) else card.id

    private fun hiddenKey(card: SessionCard): String? {
        val state = stateAccess.currentState
        val packId = state.navigation.activePackId?.value ?: return null
        val lessonScope = if (card is SentenceCard) state.navigation.selectedLessonId?.value
            else "@${state.cardSession.returnTo ?: state.cardSession.screenMode.name}"
        return HiddenCardKeys.forCard(card, packId, lessonScope)
    }

    /** Compute the bad-sentence count for the active pack. */
    fun getBadSentenceCount(): Int {
        val packId = stateAccess.currentState.navigation.activePackId ?: return 0
        return badSentenceStore.getBadSentenceCount(packId.value)
    }
}
