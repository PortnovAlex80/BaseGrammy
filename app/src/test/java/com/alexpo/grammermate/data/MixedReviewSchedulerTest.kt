package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedReviewSchedulerTest {
    @Test
    fun build_firstLessonHasNoMixedBlocks() {
        val scheduler = MixedReviewScheduler(subLessonSize = 4)
        val lesson = lesson("L1", 7)
        val schedule = scheduler.build(listOf(lesson)).getValue(LessonId("L1"))
        val types = schedule.subLessons.map { it.type }
        assertEquals(listOf(SubLessonType.NEW_ONLY, SubLessonType.NEW_ONLY), types)
    }

    /**
     * Расписание больше не запекает карточки повторения — оно объявляет число
     * слотов, которые заполняет ReviewSelector по актуальному mastery.
     * Здесь пиннится именно форма: сколько смешанных блоков и сколько в них
     * места под повторение.
     */
    @Test
    fun build_mixedBlocksDeclareReviewSlotsInsteadOfBakingCards() {
        val scheduler = MixedReviewScheduler(subLessonSize = 4)
        val lessons = listOf(
            lesson("L1", 7),
            lesson("L2", 13),
            lesson("L3", 13)
        )
        val schedules = scheduler.build(lessons)

        val mixedL2 = schedules.getValue(LessonId("L2")).subLessons.filter { it.type == SubLessonType.MIXED }
        assertEquals(3, mixedL2.size)
        mixedL2.forEach { block ->
            // Никаких чужих карточек в плане: только текущий урок.
            assertTrue(block.cards.all { it.id.startsWith("L2-") })
            // Текущие карточки + слоты повторения = размер блока.
            assertEquals(4, block.cards.size + block.reviewSlots)
            assertTrue(block.reviewSlots > 0)
        }

        val mixedL3 = schedules.getValue(LessonId("L3")).subLessons.filter { it.type == SubLessonType.MIXED }
        assertEquals(3, mixedL3.size)
        mixedL3.forEach { block ->
            assertTrue(block.cards.all { it.id.startsWith("L3-") })
            assertEquals(4, block.cards.size + block.reviewSlots)
        }
    }

    @Test
    fun build_mixedAppearsWithDefaultSizes() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        val lessons = listOf(
            lesson("L1", 20),
            lesson("L2", 20)
        )
        val mixed = scheduler.build(lessons)
            .getValue(LessonId("L2"))
            .subLessons
            .filter { it.type == SubLessonType.MIXED }
        assertTrue(mixed.isNotEmpty())
        val first = mixed.first()
        assertTrue(first.cards.all { it.id.startsWith("L2-") })
        assertEquals(5, first.cards.size)
        assertEquals(5, first.reviewSlots)
    }

    @Test
    fun build_firstLessonDeclaresNoReviewSlots() {
        val scheduler = MixedReviewScheduler(subLessonSize = 6)
        val schedule = scheduler.build(listOf(lesson("L1", 12))).getValue(LessonId("L1"))
        assertTrue(schedule.subLessons.all { it.reviewSlots == 0 })
    }

    @Test
    fun build_usesAllCardsNotJustFirst150() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        // Create lesson with 300 cards (more than MAIN_POOL_SIZE of 150)
        val lesson = lesson("L1", 300)
        val schedule = scheduler.build(listOf(lesson)).getValue(LessonId("L1"))

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
        val schedule = scheduler.build(listOf(lesson)).getValue(LessonId("L1"))

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
        val schedule = scheduler.build(listOf(lesson)).getValue(LessonId("L1"))

        assertEquals(8, schedule.subLessons.size)
    }

    private fun lesson(id: String, count: Int): Lesson {
        val cards = (1..count).map { index ->
            SentenceCard("$id-$index", "ru$index", listOf("en$index"))
        }
        return Lesson(id = LessonId(id), languageId = LanguageId("en"), title = id, cards = cards)
    }
}
