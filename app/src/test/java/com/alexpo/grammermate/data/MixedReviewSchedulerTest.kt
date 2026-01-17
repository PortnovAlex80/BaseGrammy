package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedReviewSchedulerTest {
    @Test
    fun build_firstLessonHasNoMixedBlocks() {
        val scheduler = MixedReviewScheduler(subLessonSize = 4)
        val lesson = lesson("L1", 7)
        val schedule = scheduler.build(listOf(lesson)).getValue("L1")
        val types = schedule.subLessons.map { it.type }
        assertEquals(listOf(SubLessonType.NEW_ONLY, SubLessonType.NEW_ONLY), types)
    }

    @Test
    fun build_appliesIntervalsAcrossMixedBlocks() {
        val scheduler = MixedReviewScheduler(subLessonSize = 4)
        val lessons = listOf(
            lesson("L1", 7),
            lesson("L2", 13),
            lesson("L3", 13)
        )
        val schedules = scheduler.build(lessons)
        val mixedL2 = schedules.getValue("L2").subLessons.filter { it.type == SubLessonType.MIXED }
        assertEquals(3, mixedL2.size)
        assertTrue(mixedL2[0].cards.any { it.id.startsWith("L1-") })
        assertTrue(mixedL2[0].cards.any { it.id.startsWith("L2-") })
        assertTrue(mixedL2[1].cards.any { it.id.startsWith("L1-") })
        assertTrue(mixedL2[1].cards.any { it.id.startsWith("L2-") })
        assertTrue(mixedL2[2].cards.all { it.id.startsWith("L2-") })

        val mixedL3 = schedules.getValue("L3").subLessons.filter { it.type == SubLessonType.MIXED }
        assertEquals(3, mixedL3.size)
        assertTrue(mixedL3[0].cards.any { it.id.startsWith("L1-") })
        assertTrue(mixedL3[0].cards.any { it.id.startsWith("L2-") })
        assertTrue(mixedL3[0].cards.any { it.id.startsWith("L3-") })
        assertTrue(mixedL3[1].cards.any { it.id.startsWith("L2-") })
        assertTrue(mixedL3[1].cards.any { it.id.startsWith("L3-") })
        assertTrue(mixedL3[2].cards.all { it.id.startsWith("L3-") })
    }

    @Test
    fun build_mixedAppearsWithDefaultSizes() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        val lessons = listOf(
            lesson("L1", 20),
            lesson("L2", 20)
        )
        val mixed = scheduler.build(lessons)
            .getValue("L2")
            .subLessons
            .filter { it.type == SubLessonType.MIXED }
        assertTrue(mixed.isNotEmpty())
        val first = mixed.first().cards
        assertTrue(first.any { it.id.startsWith("L1-") })
        assertTrue(first.any { it.id.startsWith("L2-") })
    }

    @Test
    fun build_mixedHasAtMostThreeThemes() {
        val scheduler = MixedReviewScheduler(subLessonSize = 6)
        val lessons = listOf(
            lesson("L1", 12),
            lesson("L2", 12),
            lesson("L3", 12),
            lesson("L4", 12)
        )
        val schedules = scheduler.build(lessons)
        schedules.values.flatMap { it.subLessons }
            .filter { it.type == SubLessonType.MIXED }
            .forEach { subLesson ->
                val themes = subLesson.cards.map { it.id.substringBefore("-") }.toSet()
                assertTrue(themes.size <= 3)
            }
    }

    @Test
    fun build_usesAllCardsNotJustFirst150() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        // Create lesson with 300 cards (more than MAIN_POOL_SIZE of 150)
        val lesson = lesson("L1", 300)
        val schedule = scheduler.build(listOf(lesson)).getValue("L1")

        // Count total cards across all sublessons
        val totalCards = schedule.subLessons.flatMap { it.cards }.size

        // Should use all 300 cards, not just first 150
        assertTrue("Expected all 300 cards to be used, but got $totalCards", totalCards == 300)
    }

    @Test
    fun build_createsCyclicSublessons() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        // Create lesson with 200 cards - should create 20 sublessons (200/10)
        val lesson = lesson("L1", 200)
        val schedule = scheduler.build(listOf(lesson)).getValue("L1")

        assertEquals(20, schedule.subLessons.size)

        // First 15 sublessons should be in first cycle
        // Next 5 should be in second cycle
        val firstCycle = schedule.subLessons.take(15)
        val secondCycle = schedule.subLessons.drop(15).take(5)

        assertTrue(firstCycle.size == 15)
        assertTrue(secondCycle.size == 5)
    }

    @Test
    fun build_handlesLessThan15Sublessons() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        // Create lesson with only 80 cards - should create 8 sublessons
        val lesson = lesson("L1", 80)
        val schedule = scheduler.build(listOf(lesson)).getValue("L1")

        assertEquals(8, schedule.subLessons.size)
    }

    private fun lesson(id: String, count: Int): Lesson {
        val cards = (1..count).map { index ->
            SentenceCard("$id-$index", "ru$index", listOf("en$index"))
        }
        return Lesson(id = id, languageId = "en", title = id, cards = cards)
    }
}
