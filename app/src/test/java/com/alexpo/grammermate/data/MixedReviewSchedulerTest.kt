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
        val scheduler = MixedReviewScheduler(warmupSize = 1, subLessonSize = 6)
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

    private fun lesson(id: String, count: Int): Lesson {
        val cards = (1..count).map { index ->
            SentenceCard("$id-$index", "ru$index", listOf("en$index"))
        }
        return Lesson(id = id, languageId = "en", title = id, cards = cards)
    }
}
