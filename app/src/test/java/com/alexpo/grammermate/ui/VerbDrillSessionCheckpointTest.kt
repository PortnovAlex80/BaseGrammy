package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillLastSessionState
import com.alexpo.grammermate.data.VerbDrillSessionState
import com.alexpo.grammermate.data.VerbDrillUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class VerbDrillSessionCheckpointTest {
    private val card = VerbDrillCard("card-1", "prompt", "answer")
    private val saved = VerbDrillLastSessionState(
        selectedTense = "Presente", selectedGroup = "are", sortByFrequency = true,
        sessionCardIds = listOf(card.id), packId = "pack-1"
    )

    @Test
    fun rejects_foreign_pack_and_missing_cards_only_after_catalog_load() {
        assertEquals(VerbDrillSessionCheckpoint.Validity.PACK_MISMATCH,
            VerbDrillSessionCheckpoint.validity(saved, "pack-2", emptyList()))
        assertEquals(VerbDrillSessionCheckpoint.Validity.VALID,
            VerbDrillSessionCheckpoint.validity(saved, "pack-1", emptyList()))
        assertEquals(VerbDrillSessionCheckpoint.Validity.MISSING_CARDS,
            VerbDrillSessionCheckpoint.validity(saved, "pack-1", listOf(card.copy(id = "other"))))
        assertEquals(VerbDrillSessionCheckpoint.Validity.VALID,
            VerbDrillSessionCheckpoint.validity(saved, "pack-1", listOf(card)))
    }

    @Test
    fun snapshot_keeps_filters_and_clamps_index_without_marking_new_cards_shown() {
        val state = VerbDrillUiState(
            selectedTense = "Presente", selectedGroup = "are", selectedPerson = "Io",
            sortByFrequency = true
        )
        val snapshot = VerbDrillSessionCheckpoint.snapshot(
            state, VerbDrillSessionState(listOf(card), currentIndex = 9),
            todayShownCardIds = setOf("answered-before"), packId = "pack-1"
        )

        assertEquals(listOf(card.id), snapshot.sessionCardIds)
        assertEquals(setOf("answered-before"), snapshot.todayShownCardIds)
        assertEquals(1, snapshot.currentIndex)
        assertEquals("Io", snapshot.selectedPerson)
        assertEquals("pack-1", snapshot.packId)
    }
}
