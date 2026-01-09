package com.alexpo.grammermate.data

import java.util.ArrayDeque

enum class SubLessonType {
    WARMUP,
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
    private val warmupSize: Int,
    private val subLessonSize: Int,
    private val intervals: List<Int> = listOf(1, 2, 4, 7, 10, 14, 20, 28, 42, 56)
) {
    fun build(lessons: List<Lesson>): Map<String, LessonSchedule> {
        if (lessons.isEmpty()) return emptyMap()
        val schedules = linkedMapOf<String, LessonSchedule>()
        val reviewQueues = mutableMapOf<String, ArrayDeque<SentenceCard>>()
        val reviewStartMixedIndex = mutableMapOf<String, Int>()
        val lessonIndexById = lessons.mapIndexed { index, lesson -> lesson.id to index }.toMap()
        var globalMixedIndex = 0

        lessons.forEachIndexed { lessonIndex, lesson ->
            if (lessonIndex > 0) {
                val previousLessonId = lessons[lessonIndex - 1].id
                reviewStartMixedIndex.putIfAbsent(previousLessonId, globalMixedIndex)
            }

            reviewQueues[lesson.id] = ArrayDeque(lesson.cards)

            val warmupCards = lesson.cards.take(warmupSize)
            val currentCards = lesson.cards.drop(warmupSize)
            val allowMixed = lessonIndex > 0
            val reviewSlots = subLessonSize / 2
            val currentSlotsInMixed = subLessonSize - reviewSlots
            val pairSize = subLessonSize + currentSlotsInMixed
            val pairCount = if (currentCards.isEmpty()) {
                0
            } else if (allowMixed) {
                ceilDiv(currentCards.size, pairSize)
            } else {
                ceilDiv(currentCards.size, subLessonSize)
            }
            val mixedCurrentTarget = if (!allowMixed || currentCards.isEmpty()) {
                0
            } else {
                ceilDiv(currentCards.size * currentSlotsInMixed, pairSize)
            }.coerceAtMost(currentCards.size)
            val newOnlyTarget = currentCards.size - mixedCurrentTarget
            val newOnlyQueue = ArrayDeque(currentCards.take(newOnlyTarget))
            val mixedCurrentQueue = ArrayDeque(currentCards.drop(newOnlyTarget))

            val subLessons = mutableListOf<ScheduledSubLesson>()
            if (warmupCards.isNotEmpty()) {
                subLessons.add(ScheduledSubLesson(SubLessonType.WARMUP, warmupCards))
            }

            val mixedSubLessons = mutableListOf<ScheduledSubLesson>()
            if (allowMixed) {
                repeat(pairCount) {
                    globalMixedIndex += 1
                    val currentHalf = takeUpTo(mixedCurrentQueue, currentSlotsInMixed)
                    val reviewHalfSize = currentHalf.size
                    val reviewCards = if (reviewHalfSize == 0) {
                        emptyList()
                    } else {
                        val dueLessons = dueLessonIds(
                            reviewStartMixedIndex,
                            lessonIndexById,
                            globalMixedIndex
                        )
                        fillReviewSlots(
                            dueLessons,
                            reviewQueues,
                            reviewHalfSize,
                            newOnlyQueue
                        )
                    }
                    val mixedCards = currentHalf + reviewCards
                    if (mixedCards.isNotEmpty()) {
                        mixedSubLessons.add(ScheduledSubLesson(SubLessonType.MIXED, mixedCards))
                    }
                }
            }

            repeat(pairCount) {
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
        slots: Int,
        fallbackQueue: ArrayDeque<SentenceCard>
    ): List<SentenceCard> {
        if (slots <= 0) return emptyList()
        val availableLessons = dueLessons.filter { reviewQueues[it]?.isNotEmpty() == true }
        if (availableLessons.isEmpty()) {
            return takeUpTo(fallbackQueue, slots)
        }
        val result = mutableListOf<SentenceCard>()
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
