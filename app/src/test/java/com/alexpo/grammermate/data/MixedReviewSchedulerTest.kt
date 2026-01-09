package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedReviewSchedulerTest {
    @Test
    fun build_firstLessonHasNoMixedBlocks() {
        val scheduler = MixedReviewScheduler(warmupSize = 1, subLessonSize = 4)
        val lesson = lesson("L1", 7)
        val schedule = scheduler.build(listOf(lesson)).getValue("L1")
        val types = schedule.subLessons.map { it.type }
        assertTrue(types.contains(SubLessonType.WARMUP))
        assertTrue(types.contains(SubLessonType.NEW_ONLY))
        assertTrue(types.none { it == SubLessonType.MIXED })
    }

    @Test
    fun build_appliesIntervalsAcrossMixedBlocks() {
        val scheduler = MixedReviewScheduler(warmupSize = 1, subLessonSize = 4)
        val lessons = listOf(
            lesson("L1", 7),
            lesson("L2", 13),
            lesson("L3", 13)
        )
        val schedules = scheduler.build(lessons)
        val mixedL2 = schedules.getValue("L2").subLessons.filter { it.type == SubLessonType.MIXED }
        assertEquals(2, mixedL2.size)
        assertEquals(2, mixedL2[0].cards.count { it.id.startsWith("L1-") })
        assertEquals(2, mixedL2[0].cards.count { it.id.startsWith("L2-") })
        assertEquals(2, mixedL2[1].cards.count { it.id.startsWith("L1-") })

        val mixedL3 = schedules.getValue("L3").subLessons.filter { it.type == SubLessonType.MIXED }
        assertEquals(2, mixedL3.size)
        assertEquals(0, mixedL3[0].cards.count { it.id.startsWith("L1-") })
        val secondMixedL3 = mixedL3[1].cards
        assertEquals(1, secondMixedL3.count { it.id.startsWith("L1-") })
        assertEquals(1, secondMixedL3.count { it.id.startsWith("L2-") })
        assertTrue(secondMixedL3.any { it.id.startsWith("L3-") })
    }

    @Test
    fun build_mixedAppearsWithDefaultSizes() {
        val scheduler = MixedReviewScheduler(warmupSize = 3, subLessonSize = 10)
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

    private fun lesson(id: String, count: Int): Lesson {
        val cards = (1..count).map { index ->
            SentenceCard("$id-$index", "ru$index", listOf("en$index"))
        }
        return Lesson(id = id, languageId = "en", title = id, cards = cards)
    }
}
