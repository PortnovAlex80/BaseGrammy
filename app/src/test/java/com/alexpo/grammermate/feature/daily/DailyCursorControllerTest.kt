package com.alexpo.grammermate.feature.daily

import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.PackDailyCursorState
import com.alexpo.grammermate.data.PackDailyCursorStore
import com.alexpo.grammermate.data.SentenceCard
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DailyCursorControllerTest {
    @get:Rule val temp = TemporaryFolder()
    private val store = mockk<PackDailyCursorStore>(relaxed = true)
    private val lessons = mockk<LessonStore>(relaxed = true)
    private val controller = DailyCursorController(store, lessons)

    @Test
    fun migration_only_seeds_missing_selected_pack_and_stored_cursor_wins() {
        val legacy = DailyCursorState(sentenceOffset = 4, firstSessionLessonId = "lesson-1")
        val saved = PackDailyCursorState.forPack("pack-b").copy(sentenceOffset = 7)
        every { store.loadPackCursor("pack-a") } returns null
        every { store.loadPackCursor("pack-b") } returns saved

        assertEquals(legacy, controller.initialize("pack-a", legacy))
        assertEquals(7, controller.initialize("pack-b", legacy)?.sentenceOffset)
        verify(exactly = 1) { store.savePackCursor(match { it.packId == "pack-a" && it.sentenceOffset == 4 }) }
        verify(exactly = 0) { store.savePackCursor(match { it.packId == "pack-b" }) }
    }

    @Test
    fun missing_pack_never_writes_or_deletes_a_cursor() {
        val cursor = DailyCursorState(sentenceOffset = 3)

        assertNull(controller.initialize(null, cursor))
        assertFalse(controller.save(null, cursor))
        controller.delete(null)
        assertEquals(cursor, controller.advance(cursor, null, "it", 2, 5))
        verify(exactly = 0) { store.savePackCursor(any()) }
        verify(exactly = 0) { store.deletePackCursor(any()) }
    }

    @Test
    fun advance_wraps_last_lesson_and_verb_pool_without_persisting() {
        val csv = temp.newFile("it_lesson_2.csv").apply {
            writeText("Verb Drill\nRU;IT;Verb;Tense;Group;Rank\nprompt;Io parlo;parlare;Presente;are;1\nprompt;Tu parli;parlare;Presente;are;2")
        }
        every { lessons.getLessonCount("pack-a", "it") } returns 2
        every { lessons.getLessonIdAtIndex("pack-a", "it", 1) } returns "lesson-2"
        every { lessons.getCardsForLesson("pack-a", "it", "lesson-2") } returns
            listOf(mockk<SentenceCard>(), mockk<SentenceCard>())
        every { lessons.getCumulativeTenses("pack-a", 1) } returns listOf("Presente")
        every { lessons.getVerbDrillFiles("pack-a", "it") } returns listOf(csv)
        val cursor = DailyCursorState(currentLessonIndex = 1, sentenceOffset = 1, verbOffset = 1)

        val advanced = controller.advance(cursor, "pack-a", "it", sentenceCount = 1, sessionSize = 2)

        assertEquals(0, advanced.currentLessonIndex)
        assertEquals(0, advanced.sentenceOffset)
        assertEquals(0, advanced.verbOffset)
        verify(exactly = 0) { store.savePackCursor(any()) }
    }

    @Test
    fun save_preserves_first_session_lesson_identity() {
        val cursor = DailyCursorState(firstSessionLessonId = "cursor-lesson", verbOffset = 5)

        assertTrue(controller.save("pack-a", cursor))
        verify { store.savePackCursor(match {
            it.packId == "pack-a" && it.firstSessionLessonId == "cursor-lesson" && it.verbOffset == 5
        }) }
    }
}
