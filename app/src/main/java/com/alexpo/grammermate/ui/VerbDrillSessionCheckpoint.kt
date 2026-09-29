package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillLastSessionState
import com.alexpo.grammermate.data.VerbDrillSessionState
import com.alexpo.grammermate.data.VerbDrillUiState

/** Compatibility and snapshot rules for the persisted verb-drill session. */
internal object VerbDrillSessionCheckpoint {
    enum class Validity { VALID, PACK_MISMATCH, MISSING_CARDS }

    fun validity(
        saved: VerbDrillLastSessionState,
        currentPackId: String?,
        loadedCards: List<VerbDrillCard>
    ): Validity {
        if (currentPackId != null && saved.packId != null && saved.packId != currentPackId) {
            return Validity.PACK_MISMATCH
        }
        // A new ViewModel cannot reject IDs before the catalog has loaded.
        if (saved.sessionCardIds.isNotEmpty() && loadedCards.isNotEmpty()) {
            val availableIds = loadedCards.asSequence().map { it.id }.toSet()
            if (saved.sessionCardIds.any { it !in availableIds }) return Validity.MISSING_CARDS
        }
        return Validity.VALID
    }

    fun snapshot(
        state: VerbDrillUiState,
        session: VerbDrillSessionState,
        todayShownCardIds: Set<String>,
        packId: String?
    ) = VerbDrillLastSessionState(
        selectedTense = state.selectedTense,
        selectedGroup = state.selectedGroup,
        selectedPerson = state.selectedPerson,
        sortByFrequency = state.sortByFrequency,
        todayShownCardIds = todayShownCardIds,
        sessionCardIds = session.cards.map { it.id },
        currentIndex = session.currentIndex.coerceIn(0, session.cards.size),
        packId = packId
    )
}
