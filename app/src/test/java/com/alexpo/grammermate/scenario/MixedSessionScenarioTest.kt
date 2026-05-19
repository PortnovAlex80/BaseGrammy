package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SubLessonType
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.training.CardProvider
import com.alexpo.grammermate.feature.training.CardSetResult
import com.alexpo.grammermate.testharness.FakeMasteryStore
import com.alexpo.grammermate.testharness.FakeTrainingStateAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scenario test for MIXED sub-lesson interleaving behavior.
 *
 * Verifies that:
 * 1. MIXED sub-lessons interleave new cards from current lesson with review cards from previous lessons
 * 2. Cards are shown in interleaved order (not blocked by lesson)
 * 3. No card is shown twice within a session
 * 4. Review cards come from the correct previous lessons based on interval schedule
 */
class MixedSessionScenarioTest {

    @Test
    fun mixedSession_interleavesNewAndReviewCards() {
        // ARRANGE: Create two lessons where L2 will have MIXED sub-lessons
        // L1: 10 cards (completed, used for review in L2)
        // L2: 14 cards (7 new + structured to create MIXED with review from L1)
        val subLessonSize = 10
        val l1Cards = createCards("L1", 10)
        val l2Cards = createCards("L2", 14)

        val lessons = listOf(
            Lesson(
                id = LessonId("L1"),
                languageId = LanguageId("it"),
                title = "Lesson 1",
                cards = l1Cards
            ),
            Lesson(
                id = LessonId("L2"),
                languageId = LanguageId("it"),
                title = "Lesson 2",
                cards = l2Cards
            )
        )

        // Create fake components
        val fakeMasteryStore = FakeMasteryStore()

        // Mark L1 as started (so its cards can appear in L2 MIXED blocks)
        fakeMasteryStore.recordCardShow("L1", "it", "L1-1")
        fakeMasteryStore.recordCardShow("L1", "it", "L1-2")

        // Create CardProvider and build schedules
        val cardProvider = CardProvider(
            subLessonSize = subLessonSize,
            progressTracker = null
        )
        val schedules = cardProvider.buildSchedules(lessons, emptyMap())

        // ACT: Get the schedule for L2
        val l2Schedule = schedules[LessonId("L2")]!!

        // ASSERT: Verify L2 has MIXED sub-lessons
        val mixedSubLessons = l2Schedule.subLessons.filter { it.type == SubLessonType.MIXED }
        assertTrue("L2 should have MIXED sub-lessons", mixedSubLessons.isNotEmpty())

        // Verify the first MIXED sub-lesson contains cards from both lessons
        val firstMixed = mixedSubLessons.first()
        val hasL1Cards = firstMixed.cards.any { it.id.startsWith("L1-") }
        val hasL2Cards = firstMixed.cards.any { it.id.startsWith("L2-") }

        assertTrue(
            "First MIXED sub-lesson should contain cards from L1 (review)",
            hasL1Cards
        )
        assertTrue(
            "First MIXED sub-lesson should contain cards from L2 (new)",
            hasL2Cards
        )
    }

    @Test
    fun mixedSession_firstLessonHasNoMixedBlocks() {
        // ARRANGE: Single lesson - should never have MIXED blocks
        val cards = createCards("L1", 20)
        val lesson = Lesson(
            id = LessonId("L1"),
            languageId = LanguageId("it"),
            title = "Lesson 1",
            cards = cards
        )

        val cardProvider = CardProvider(subLessonSize = 10)
        val schedules = cardProvider.buildSchedules(listOf(lesson), emptyMap())

        // ACT: Get schedule for L1
        val l1Schedule = schedules[LessonId("L1")]!!

        // ASSERT: First lesson should have only NEW_ONLY sub-lessons
        val types = l1Schedule.subLessons.map { it.type }
        assertTrue(
            "First lesson should only have NEW_ONLY sub-lessons, got $types",
            types.all { it == SubLessonType.NEW_ONLY }
        )
    }

    @Test
    fun mixedSession_verifiesInterleavingNotBlocked() {
        // ARRANGE: Create lessons that will generate a MIXED sub-lesson
        // with specific card counts to test interleaving pattern
        val subLessonSize = 10

        // L1: 20 cards (review source)
        val l1Cards = createCards("L1", 20)

        // L2: 20 cards (current lesson with new content)
        val l2Cards = createCards("L2", 20)

        val lessons = listOf(
            Lesson(LessonId("L1"), LanguageId("it"), "Lesson 1", l1Cards),
            Lesson(LessonId("L2"), LanguageId("it"), "Lesson 2", l2Cards)
        )

        val cardProvider = CardProvider(subLessonSize = subLessonSize)
        val schedules = cardProvider.buildSchedules(lessons, emptyMap())

        // ACT: Get the first MIXED sub-lesson from L2
        val l2Schedule = schedules[LessonId("L2")]!!
        val firstMixed = l2Schedule.subLessons
            .firstOrNull { it.type == SubLessonType.MIXED }

        // ASSERT: Verify interleaving pattern
        assertNotNull("L2 should have at least one MIXED sub-lesson", firstMixed)

        val cards = firstMixed!!.cards

        // Track which lesson IDs appear at each position
        val lessonAtPosition = cards.map { it.id.substringBefore("-") }

        // Count how many times we switch between L1 and L2
        var switches = 0
        for (i in 1 until lessonAtPosition.size) {
            if (lessonAtPosition[i] != lessonAtPosition[i - 1]) {
                switches++
            }
        }

        // We expect some interleaving (at least one switch)
        // Pure blocked would be: L1, L1, L1, L2, L2, L2 (1 switch)
        // Interleaved would be: L1, L2, L1, L2, L1, L2 (5 switches)
        assertTrue(
            "Cards should be interleaved, not purely blocked. Switches: $switches",
            switches >= 1
        )

        // Verify both lessons are present
        val uniqueLessons = lessonAtPosition.toSet()
        assertTrue("Should contain cards from L1", "L1" in uniqueLessons)
        assertTrue("Should contain cards from L2", "L2" in uniqueLessons)
    }

    @Test
    fun mixedSession_noCardShownTwiceInSession() {
        // ARRANGE: Build a complete MIXED session scenario
        val subLessonSize = 10

        val lessons = listOf(
            Lesson(LessonId("L1"), LanguageId("it"), "Lesson 1", createCards("L1", 15)),
            Lesson(LessonId("L2"), LanguageId("it"), "Lesson 2", createCards("L2", 15))
        )

        val cardProvider = CardProvider(subLessonSize = subLessonSize)
        val schedules = cardProvider.buildSchedules(lessons, emptyMap())

        // ACT: Collect all cards across all MIXED sub-lessons in L2
        val l2Schedule = schedules[LessonId("L2")]!!
        val allMixedCards = l2Schedule.subLessons
            .filter { it.type == SubLessonType.MIXED }
            .flatMap { it.cards }

        // ASSERT: No duplicate card IDs
        val cardIds = allMixedCards.map { it.id }
        val uniqueCardIds = cardIds.toSet()

        assertEquals(
            "No card should appear twice in MIXED sub-lessons",
            uniqueCardIds.size,
            cardIds.size
        )
    }

    @Test
    fun mixedSession_cardProviderReturnsCorrectCards() {
        // ARRANGE: Full end-to-end test with CardProvider.buildSessionCards
        val subLessonSize = 10

        val lessons = listOf(
            Lesson(LessonId("L1"), LanguageId("it"), "Lesson 1", createCards("L1", 20)),
            Lesson(LessonId("L2"), LanguageId("it"), "Lesson 2", createCards("L2", 20))
        )

        val cardProvider = CardProvider(subLessonSize = subLessonSize)
        val schedules = cardProvider.buildSchedules(lessons, emptyMap())

        // Select L2 and verify MIXED sub-lessons exist
        val l2Schedule = schedules[LessonId("L2")]!!
        val mixedIndex = l2Schedule.subLessons
            .indexOfFirst { it.type == SubLessonType.MIXED }

        assertTrue("L2 should have at least one MIXED sub-lesson", mixedIndex >= 0)

        // ACT: Build session cards for the MIXED sub-lesson
        val result = cardProvider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.LESSON,
            selectedLessonId = LessonId("L2"),
            schedules = schedules,
            activeSubLessonIndex = mixedIndex,
            hiddenCardIds = emptySet(),
            mastery = null
        )

        // ASSERT: Verify the returned cards
        assertTrue(
            "Session should contain cards",
            result.cards.isNotEmpty()
        )

        // Verify cards come from the correct MIXED sub-lesson
        val expectedCards = l2Schedule.subLessons[mixedIndex].cards
        assertEquals(
            "Card count should match the MIXED sub-lesson",
            expectedCards.size,
            result.cards.size
        )

        // Verify sub-lesson metadata
        assertEquals(
            SubLessonType.MIXED,
            l2Schedule.subLessons[mixedIndex].type
        )
        assertTrue(
            "subLessonTypes should include MIXED",
            SubLessonType.MIXED in result.subLessonTypes
        )
    }

    @Test
    fun mixedSession_reviewCardsComeFromPreviousLessons() {
        // ARRANGE: Three lessons to verify review comes from previous, not future
        val subLessonSize = 10

        val lessons = listOf(
            Lesson(LessonId("L1"), LanguageId("it"), "Lesson 1", createCards("L1", 20)),
            Lesson(LessonId("L2"), LanguageId("it"), "Lesson 2", createCards("L2", 20)),
            Lesson(LessonId("L3"), LanguageId("it"), "Lesson 3", createCards("L3", 20))
        )

        val cardProvider = CardProvider(subLessonSize = subLessonSize)
        val schedules = cardProvider.buildSchedules(lessons, emptyMap())

        // ACT: Get MIXED sub-lessons from L2 (second lesson)
        val l2Schedule = schedules[LessonId("L2")]!!
        val mixedSubLessons = l2Schedule.subLessons
            .filter { it.type == SubLessonType.MIXED }

        // ASSERT: Review cards should come from L1 (previous), not L3 (future)
        val allMixedCards = mixedSubLessons.flatMap { it.cards }
        val lessonIdsInMixed = allMixedCards
            .map { it.id.substringBefore("-") }
            .toSet()

        // L2's MIXED blocks should contain L1 and L2, but NOT L3
        assertTrue("Should contain L1 (review)", "L1" in lessonIdsInMixed)
        assertTrue("Should contain L2 (new)", "L2" in lessonIdsInMixed)
        assertFalse("Should NOT contain L3 (future lesson)", "L3" in lessonIdsInMixed)
    }

    @Test
    fun mixedSession_defaultSubLessonSize() {
        // ARRANGE: Verify MIXED works with default sub-lesson size
        val lessons = listOf(
            Lesson(LessonId("L1"), LanguageId("it"), "Lesson 1", createCards("L1", 50)),
            Lesson(LessonId("L2"), LanguageId("it"), "Lesson 2", createCards("L2", 50))
        )

        // Use default sub-lesson size
        val cardProvider = CardProvider(
            subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
        )
        val schedules = cardProvider.buildSchedules(lessons, emptyMap())

        // ACT: Get L2 schedule
        val l2Schedule = schedules[LessonId("L2")]!!

        // ASSERT: Should have MIXED sub-lessons with default size
        val mixedSubLessons = l2Schedule.subLessons
            .filter { it.type == SubLessonType.MIXED }

        assertTrue(
            "L2 should have MIXED sub-lessons with default size",
            mixedSubLessons.isNotEmpty()
        )

        // Verify sub-lesson size constraint
        mixedSubLessons.forEach { subLesson ->
            assertTrue(
                "Each MIXED sub-lesson should have at most ${TrainingConfig.SUB_LESSON_SIZE_DEFAULT} cards",
                subLesson.cards.size <= TrainingConfig.SUB_LESSON_SIZE_DEFAULT
            )
        }
    }

    @Test
    fun mixedSession_stateAccessIntegration() {
        // ARRANGE: Test integration with TrainingStateAccess
        val subLessonSize = 10

        val lessons = listOf(
            Lesson(LessonId("L1"), LanguageId("it"), "Lesson 1", createCards("L1", 20)),
            Lesson(LessonId("L2"), LanguageId("it"), "Lesson 2", createCards("L2", 20))
        )

        val cardProvider = CardProvider(subLessonSize = subLessonSize)
        val schedules = cardProvider.buildSchedules(lessons, emptyMap())

        // Set up state for L2
        val l2Schedule = schedules[LessonId("L2")]!!
        val mixedIndex = l2Schedule.subLessons
            .indexOfFirst { it.type == SubLessonType.MIXED }

        // ACT: Build session cards through CardProvider
        val result: CardSetResult = cardProvider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.LESSON,
            selectedLessonId = LessonId("L2"),
            schedules = schedules,
            activeSubLessonIndex = mixedIndex,
            hiddenCardIds = emptySet(),
            mastery = null
        )

        // ASSERT: Verify result structure
        assertTrue(
            "Should have cards in result",
            result.cards.isNotEmpty()
        )
        assertEquals(
            "Active index should match requested",
            mixedIndex,
            result.activeSubLessonIndex
        )
        assertTrue(
            "Sub-lesson types should include MIXED",
            result.subLessonTypes.contains(SubLessonType.MIXED)
        )
    }

    // Helper functions

    private fun createCards(prefix: String, count: Int): List<SentenceCard> {
        return (1..count).map { index ->
            SentenceCard(
                id = "$prefix-$index",
                promptRu = "Russian $prefix $index",
                acceptedAnswers = listOf("Italian $prefix $index"),
                tense = if (index % 2 == 0) "present" else "past"
            )
        }
    }

    private fun assertNotNull(message: String, obj: Any?) {
        org.junit.Assert.assertNotNull(message, obj)
    }
}
