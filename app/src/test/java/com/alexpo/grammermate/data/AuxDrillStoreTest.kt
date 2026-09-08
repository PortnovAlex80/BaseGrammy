package com.alexpo.grammermate.data

import com.alexpo.grammermate.testharness.FakeAuxDrillStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxDrillStoreTest {

    @Test
    fun upsertComboProgress_storesByKey() {
        val store = FakeAuxDrillStore()
        val key = "aux|avere|Presente"
        val progress = AuxDrillComboProgress(
            verb = "avere",
            tense = "Presente",
            totalCards = 35,
            everShownCardIds = setOf("c1", "c2"),
            lastDate = "2026-06-24"
        )

        store.upsertComboProgress(key, progress)

        assertEquals(progress, store.loadProgress()[key])
    }

    @Test
    fun upsertComboProgress_overwritesExisting() {
        val store = FakeAuxDrillStore()
        val key = "aux|essere|Imperfetto"
        val first = AuxDrillComboProgress("essere", "Imperfetto", 35, setOf("a"))
        val second = AuxDrillComboProgress("essere", "Imperfetto", 35, setOf("a", "b", "c"))

        store.upsertComboProgress(key, first)
        store.upsertComboProgress(key, second)

        assertEquals(3, store.loadProgress()[key]?.everShownCardIds?.size)
    }

    @Test
    fun saveProgress_replacesAll() {
        val store = FakeAuxDrillStore()
        store.upsertComboProgress("old", AuxDrillComboProgress("avere", "Presente", 35))
        val newMap = mapOf(
            "aux|stare|Presente" to AuxDrillComboProgress("stare", "Presente", 35)
        )

        store.saveProgress(newMap)

        assertEquals(1, store.loadProgress().size)
        assertTrue(store.loadProgress().containsKey("aux|stare|Presente"))
    }

    @Test
    fun loadProgress_emptyWhenNothingSaved() {
        val store = FakeAuxDrillStore()
        assertTrue(store.loadProgress().isEmpty())
    }
}
