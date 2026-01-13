package com.alexpo.grammermate.data

import java.util.ArrayDeque

enum class SubLessonType {
    NEW_ONLY,
    MIXED
}

data class ScheduledSubLesson(
    val type: SubLessonType,
    val cards: List<SentenceCard>
)

data class LessonSchedule(
    val lessonId: String,
    val subLessons: List<ScheduledSubLesson>
)

class MixedReviewScheduler(
    private val subLessonSize: Int,
    private val intervals: List<Int> = listOf(1, 2, 4, 7, 10, 14, 20, 28, 42, 56)
) {
    fun build(lessons: List<Lesson>): Map<String, LessonSchedule> {
        if (lessons.isEmpty()) return emptyMap()
        val schedules = linkedMapOf<String, LessonSchedule>()
        val reviewQueues = mutableMapOf<String, ArrayDeque<SentenceCard>>()
        val reserveQueues = mutableMapOf<String, ArrayDeque<SentenceCard>>()
        val reviewStartMixedIndex = mutableMapOf<String, Int>()
        val lessonIndexById = lessons.mapIndexed { index, lesson -> lesson.id to index }.toMap()
        var globalMixedIndex = 0

        lessons.forEachIndexed { lessonIndex, lesson ->
            if (lessonIndex > 0) {
                val previousLessonId = lessons[lessonIndex - 1].id
                reviewStartMixedIndex.putIfAbsent(previousLessonId, globalMixedIndex)
            }

            // Use main pool for review queue, reserve pool for additional mixing
            reviewQueues[lesson.id] = ArrayDeque(lesson.mainPoolCards)
            reserveQueues[lesson.id] = ArrayDeque(lesson.reservePoolCards)

            // Use only main pool cards (first 150) for sub-lessons
            val mainCards = lesson.mainPoolCards
            val currentCards = mainCards
            val allowMixed = lessonIndex > 0
            val reviewSlots = subLessonSize / 2
            val currentSlotsInMixed = subLessonSize - reviewSlots
            val totalCards = currentCards.size
            val newOnlyTarget = if (allowMixed) {
                ceilDiv(totalCards, 2)
            } else {
                totalCards
            }
            val newOnlyQueue = ArrayDeque(currentCards.take(newOnlyTarget))
            val mixedCurrentQueue = ArrayDeque(currentCards.drop(newOnlyTarget))
            val newOnlyCount = if (newOnlyQueue.isEmpty()) 0 else ceilDiv(newOnlyQueue.size, subLessonSize)

            val subLessons = mutableListOf<ScheduledSubLesson>()

            val mixedSubLessons = mutableListOf<ScheduledSubLesson>()
            if (allowMixed) {
                while (mixedCurrentQueue.isNotEmpty()) {
                    globalMixedIndex += 1
                    val currentHalf = takeUpTo(mixedCurrentQueue, currentSlotsInMixed)
                    val reviewSlotsNeeded = subLessonSize - currentHalf.size
                    val dueLessons = dueLessonIds(
                        reviewStartMixedIndex,
                        lessonIndexById,
                        globalMixedIndex
                    ).take(2)
                    val reviewCards = if (reviewSlotsNeeded <= 0) {
                        emptyList()
                    } else {
                        fillReviewSlots(
                            dueLessons,
                            reviewQueues,
                            reserveQueues,
                            reviewSlotsNeeded,
                            mixedCurrentQueue
                        )
                    }
                    val mixedCards = currentHalf + reviewCards
                    if (mixedCards.isNotEmpty()) {
                        mixedSubLessons.add(ScheduledSubLesson(SubLessonType.MIXED, mixedCards))
                    }
                }
            }

            repeat(newOnlyCount) {
                val cards = takeUpTo(newOnlyQueue, subLessonSize)
                if (cards.isNotEmpty()) {
                    subLessons.add(ScheduledSubLesson(SubLessonType.NEW_ONLY, cards))
                }
            }
            subLessons.addAll(mixedSubLessons)

            schedules[lesson.id] = LessonSchedule(lesson.id, subLessons)
        }

        return schedules
    }

    private fun dueLessonIds(
        reviewStartMixedIndex: Map<String, Int>,
        lessonIndexById: Map<String, Int>,
        globalMixedIndex: Int
    ): List<String> {
        if (globalMixedIndex <= 0) return emptyList()
        val due = mutableListOf<String>()
        for ((lessonId, startIndex) in reviewStartMixedIndex) {
            val step = globalMixedIndex - startIndex
            if (intervals.contains(step)) {
                due.add(lessonId)
            }
        }
        return due.sortedByDescending { lessonIndexById[it] ?: -1 }
    }

    private fun fillReviewSlots(
        dueLessons: List<String>,
        reviewQueues: Map<String, ArrayDeque<SentenceCard>>,
        reserveQueues: Map<String, ArrayDeque<SentenceCard>>,
        slots: Int,
        fallbackQueue: ArrayDeque<SentenceCard>
    ): List<SentenceCard> {
        if (slots <= 0) return emptyList()

        // Priority 1: Try to get cards from reserve pools first
        val result = mutableListOf<SentenceCard>()
        val availableReserveLessons = dueLessons.filter { reserveQueues[it]?.isNotEmpty() == true }

        if (availableReserveLessons.isNotEmpty()) {
            val reserveLessonQueues = availableReserveLessons
                .mapNotNull { lessonId ->
                    val queue = reserveQueues[lessonId] ?: return@mapNotNull null
                    lessonId to queue
                }
                .toMutableList()
            var index = 0
            while (result.size < slots && reserveLessonQueues.isNotEmpty()) {
                val (_, queue) = reserveLessonQueues[index]
                if (queue.isNotEmpty()) {
                    result.add(queue.removeFirst())
                }
                if (queue.isEmpty()) {
                    reserveLessonQueues.removeAt(index)
                } else {
                    index++
                }
                if (index >= reserveLessonQueues.size) {
                    index = 0
                }
            }
        }

        // Priority 2: If reserve not enough, use main pool cards
        if (result.size < slots) {
            val availableLessons = dueLessons.filter { reviewQueues[it]?.isNotEmpty() == true }
            if (availableLessons.isNotEmpty()) {
                val lessonQueues = availableLessons
                    .mapNotNull { lessonId ->
                        val queue = reviewQueues[lessonId] ?: return@mapNotNull null
                        lessonId to queue
                    }
                    .toMutableList()
                var index = 0
                while (result.size < slots && lessonQueues.isNotEmpty()) {
                    val (_, queue) = lessonQueues[index]
                    if (queue.isNotEmpty()) {
                        result.add(queue.removeFirst())
                    }
                    if (queue.isEmpty()) {
                        lessonQueues.removeAt(index)
                    } else {
                        index++
                    }
                    if (index >= lessonQueues.size) {
                        index = 0
                    }
                }
            }
        }

        // Priority 3: Use fallback queue if still not enough
        if (result.size < slots) {
            result.addAll(takeUpTo(fallbackQueue, slots - result.size))
        }
        return result
    }

    private fun takeUpTo(queue: ArrayDeque<SentenceCard>, count: Int): List<SentenceCard> {
        if (count <= 0 || queue.isEmpty()) return emptyList()
        val result = ArrayList<SentenceCard>(count)
        repeat(count.coerceAtMost(queue.size)) {
            result.add(queue.removeFirst())
        }
        return result
    }

    private fun ceilDiv(numerator: Int, denominator: Int): Int {
        if (numerator <= 0) return 0
        return (numerator + denominator - 1) / denominator
    }
}
