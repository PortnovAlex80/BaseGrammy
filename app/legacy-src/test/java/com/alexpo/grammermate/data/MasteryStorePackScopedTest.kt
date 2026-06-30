package com.alexpo.grammermate.data

import com.alexpo.grammermate.testharness.FakeMasteryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MasteryStorePackScopedTest {
    private val store = FakeMasteryStore()

    @Before
    fun setup() {
        store.resetAll()
    }

    @Test
    fun getForPack_returnsNull_forUntouchedPack() {
        assertNull(store.getForPack("PACK_A", "lesson_01"))
    }

    @Test
    fun saveForPack_thenGetForPack_returnsState() {
        val state = LessonMasteryState(
            lessonId = LessonId("lesson_01"),
            languageId = LanguageId("it"),
            uniqueCardShows = 5
        )
        store.saveForPack(state, "PACK_A")
        val result = store.getForPack("PACK_A", "lesson_01")
        assertNotNull(result)
        assertEquals(5, result!!.uniqueCardShows)
    }

    @Test
    fun packsAreIsolated_saveInPackA_notVisibleInPackB() {
        val state = LessonMasteryState(
            lessonId = LessonId("lesson_01"),
            languageId = LanguageId("it"),
            uniqueCardShows = 10
        )
        store.saveForPack(state, "PACK_A")
        assertNull(store.getForPack("PACK_B", "lesson_01"))
    }

    @Test
    fun sameLessonId_twoPacks_independentData() {
        val stateA = LessonMasteryState(
            lessonId = LessonId("lesson_01"),
            languageId = LanguageId("it"),
            uniqueCardShows = 10
        )
        val stateB = LessonMasteryState(
            lessonId = LessonId("lesson_01"),
            languageId = LanguageId("it"),
            uniqueCardShows = 20
        )
        store.saveForPack(stateA, "PACK_A")
        store.saveForPack(stateB, "PACK_B")
        assertEquals(10, store.getForPack("PACK_A", "lesson_01")!!.uniqueCardShows)
        assertEquals(20, store.getForPack("PACK_B", "lesson_01")!!.uniqueCardShows)
    }

    @Test
    fun recordCardShowForPack_incrementsUniqueCardShows() {
        store.recordCardShowForPack("PACK_A", "lesson_01", "card_1")
        store.recordCardShowForPack("PACK_A", "lesson_01", "card_1") // duplicate
        store.recordCardShowForPack("PACK_A", "lesson_01", "card_2")
        val state = store.getForPack("PACK_A", "lesson_01")!!
        assertEquals(2, state.uniqueCardShows) // card_1 + card_2
        assertEquals(3, state.totalCardShows) // 3 total calls
    }

    @Test
    fun recordCardShowForPack_doesNotLeakToOtherPack() {
        store.recordCardShowForPack("PACK_A", "lesson_01", "card_1")
        assertNull(store.getForPack("PACK_B", "lesson_01"))
    }

    @Test
    fun markCardsShownForProgressForPack_addsToShownCardIds() {
        store.markCardsShownForProgressForPack("PACK_A", "lesson_01", listOf("card_1", "card_2", "card_3"))
        val state = store.getForPack("PACK_A", "lesson_01")!!
        assertTrue(state.shownCardIds.containsAll(listOf("card_1", "card_2", "card_3")))
    }

    @Test
    fun markLessonCompletedForPack_setsCompletedAtMs() {
        store.saveForPack(
            LessonMasteryState(
                lessonId = LessonId("lesson_01"),
                languageId = LanguageId("it")
            ),
            "PACK_A"
        )
        store.markLessonCompletedForPack("PACK_A", "lesson_01")
        assertNotNull(store.getForPack("PACK_A", "lesson_01")!!.completedAtMs)
    }

    @Test
    fun clearPack_removesOnlyTargetPack() {
        store.saveForPack(
            LessonMasteryState(
                lessonId = LessonId("lesson_01"),
                languageId = LanguageId("it"),
                uniqueCardShows = 10
            ),
            "PACK_A"
        )
        store.saveForPack(
            LessonMasteryState(
                lessonId = LessonId("lesson_01"),
                languageId = LanguageId("it"),
                uniqueCardShows = 20
            ),
            "PACK_B"
        )
        store.clearPack("PACK_A")
        assertNull(store.getForPack("PACK_A", "lesson_01"))
        assertEquals(20, store.getForPack("PACK_B", "lesson_01")!!.uniqueCardShows)
    }

    @Test
    fun recordCardEncounterForPack_returnsIncrementingCount() {
        val count1 = store.recordCardEncounterForPack("PACK_A", "lesson_01", "card_1")
        val count2 = store.recordCardEncounterForPack("PACK_A", "lesson_01", "card_1")
        assertEquals(1, count1)
        assertEquals(2, count2)
    }

    @Test
    fun encounterCounts_arePackIsolated() {
        store.recordCardEncounterForPack("PACK_A", "lesson_01", "card_1")
        assertEquals(0, store.getCardEncounterCountForPack("PACK_B", "lesson_01", "card_1"))
    }

    @Test
    fun getOrCreateForPack_createsNewStateIfMissing() {
        val state = store.getOrCreateForPack("PACK_A", "lesson_01")
        assertEquals("lesson_01", state.lessonId.value)
        assertEquals(0, state.uniqueCardShows)
    }

    @Test
    fun simulatePackLessonCompletion_marksCompleted() {
        store.simulatePackLessonCompletion("PACK_A", "lesson_01", cardsToShow = 10)
        val state = store.getForPack("PACK_A", "lesson_01")!!
        assertEquals(10, state.uniqueCardShows)
        assertNotNull(state.completedAtMs)
    }
}
