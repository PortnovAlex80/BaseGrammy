package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.BadSentenceEntry
import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.VerbDrillCard
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VerbDrillBadSentencesTest {
    private val store = mockk<BadSentenceStore>(relaxed = true)
    private val badSentences = VerbDrillBadSentences(store)
    private val card = VerbDrillCard("card-1", "prompt", "answer")

    @Test
    fun scopes_flags_and_counts_to_current_catalog() {
        assertFalse(badSentences.flag(card, "it"))
        badSentences.useCatalog(mapOf(card.id to "pack-1"), setOf("pack-1"))
        every { store.getBadSentenceCount("pack-1") } returns 2
        every { store.isBadSentence("pack-1", card.id) } returns true

        assertTrue(badSentences.flag(card, "it"))
        assertTrue(badSentences.isBad(card))
        assertEquals(2, badSentences.count())
        assertTrue(badSentences.unflag(card))
        verify { store.addBadSentence("pack-1", card.id, "it", "prompt", "answer", "verb_drill") }
        verify { store.removeBadSentence("pack-1", card.id) }

        badSentences.useCatalog(emptyMap(), emptySet())
        assertFalse(badSentences.isBad(card))
        assertFalse(badSentences.unflag(card))
        assertEquals(0, badSentences.count())
    }

    @Test
    fun exports_once_when_any_active_pack_has_entries() {
        badSentences.useCatalog(emptyMap(), linkedSetOf("empty", "pack-1"))
        every { store.getBadSentences("empty") } returns emptyList()
        every { store.getBadSentences("pack-1") } returns listOf(BadSentenceEntry(card.id, "it", "prompt", "answer"))
        every { store.exportUnified() } returns File("report.txt")

        assertEquals(File("report.txt").absolutePath, badSentences.export())
        verify(exactly = 1) { store.exportUnified() }
    }
}
