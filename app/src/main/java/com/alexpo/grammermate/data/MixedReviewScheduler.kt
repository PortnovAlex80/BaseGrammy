package com.alexpo.grammermate.data

import java.util.ArrayDeque

enum class SubLessonType {
    NEW_ONLY,
    MIXED
}

/**
 * Один блок под-урока.
 *
 * Для [SubLessonType.MIXED] расписание хранит только **число слотов** под
 * повторение, а не конкретные карточки: чем их заполнить, решается в момент
 * показа по актуальному mastery (см. [com.alexpo.grammermate.feature.training.ReviewSelector]).
 * Благодаря этому статический план остаётся функцией контента, а всё
 * зависящее от времени считается на лету.
 */
data class ScheduledSubLesson(
    val type: SubLessonType,
    val cards: List<SentenceCard>,
    val reviewSlots: Int = 0
)

data class LessonSchedule(
    val lessonId: LessonId,
    val subLessons: List<ScheduledSubLesson>
)

/**
 * Строит **форму** курса: сколько блоков у каждого урока, какие новые карточки
 * в них попадают и сколько в смешанных блоках слотов под повторение.
 *
 * Конкретные карточки повторения здесь НЕ выбираются — это делает
 * [com.alexpo.grammermate.feature.training.ReviewSelector] в момент показа, по
 * актуальному mastery. Раньше они запекались сюда вместе с позиционным
 * счётчиком «дней», из-за чего план не мог реагировать на то, когда человек
 * реально занимался, а «день» растягивался пропорционально размеру урока.
 *
 * См. `docs/specification/forgetting-curve-review-scheduling.md`.
 */
class MixedReviewScheduler(
    private val subLessonSize: Int
) {

    fun build(lessons: List<Lesson>): Map<LessonId, LessonSchedule> {
        if (lessons.isEmpty()) return emptyMap()
        val schedules = linkedMapOf<LessonId, LessonSchedule>()

        lessons.forEachIndexed { lessonIndex, lesson ->
            // Use all available cards for sub-lessons (not just first 150)
            val currentCards = lesson.allCards
            // Первому уроку повторять нечего — у него нет предшественников.
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
                    val currentHalf = takeUpTo(mixedCurrentQueue, currentSlotsInMixed)
                    val slotsNeeded = (subLessonSize - currentHalf.size).coerceAtLeast(0)
                    if (currentHalf.isNotEmpty()) {
                        mixedSubLessons.add(
                            ScheduledSubLesson(
                                type = SubLessonType.MIXED,
                                cards = currentHalf,
                                reviewSlots = slotsNeeded
                            )
                        )
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
