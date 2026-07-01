package com.alexpo.grammermate.v2.core.domain.training

import com.alexpo.grammermate.v2.core.domain.TrainingConfig
import com.alexpo.grammermate.v2.core.domain.model.CardId
import com.alexpo.grammermate.v2.core.domain.model.LessonId
import com.alexpo.grammermate.v2.core.domain.model.SubLessonType
import com.alexpo.grammermate.v2.core.domain.srs.SrsConstants
import java.util.ArrayDeque

/**
 * Запланированный под-урок.
 *
 * Перенесено из v1 `data/MixedReviewScheduler.kt:10` (`ScheduledSubLesson`),
 * адаптировано: `cards: List<CardId>` вместо v1 `List<SentenceCard>`
 * (v2 оперирует только ID — mixed review не нуждается в полном контенте карточек).
 *
 * [SubLessonType] НЕ определяется здесь — он живёт в
 * [com.alexpo.grammermate.v2.core.domain.model.Enums] (создан другим агентом)
 * и импортируется, чтобы избежать дублирования.
 *
 * @property type    тип под-урока.
 * @property cardIds упорядоченный список ID карточек под-урока.
 */
data class ScheduledSubLesson(
    val type: SubLessonType,
    val cardIds: List<CardId>,
)

/**
 * Расписание одного урока: упорядоченный список под-уроков.
 *
 * Перенесено из v1 `data/MixedReviewScheduler.kt:15` (`LessonSchedule`).
 *
 * @property lessonId  урок.
 * @property subLessons под-уроки в порядке прохождения.
 */
data class LessonSchedule(
    val lessonId: LessonId,
    val subLessons: List<ScheduledSubLesson>,
)

/**
 * Входной элемент расписания: урок + его карточки + ревью-пулы.
 *
 * В v1 `MixedReviewScheduler.build` работал с `List<Lesson>` (полные объекты с
 * mainPool/reserve/allCards). В v2 планировщик оперирует ТОЛЬКО ID (mixed review
 * не нуждается в контенте), поэтому сигнатура адаптирована под id-only модель:
 * вызывающая сторона поставляет [cardIds] (текущие карты урока) и опциональные
 * пулы ревью [reviewCardIds]/[reserveCardIds]. Это сознательная адаптация, явно
 * требуемая v2-clean-architecture.
 *
 * @property lessonId       урок.
 * @property cardIds        все карточки урока в порядке (для нарезки NEW_ONLY/MIXED).
 * @property reviewCardIds  основной пул ревью (main), либо пусто.
 * @property reserveCardIds резервный пул ревью, либо пусто.
 */
data class LessonScheduleInput(
    val lessonId: LessonId,
    val cardIds: List<CardId>,
    val reviewCardIds: List<CardId> = emptyList(),
    val reserveCardIds: List<CardId> = emptyList(),
)

/**
 * Чистый планировщик смешанных сессий (new + review из предыдущих уроков).
 *
 * Перенесено из v1 `data/MixedReviewScheduler.kt` (`class MixedReviewScheduler`),
 * строки 20–211. В v2 оперирует ID ([CardId]) вместо `SentenceCard`.
 *
 * Стратегия (перенесена 1:1 из v1 кода):
 *  - allowMixed = lessonIndex > 0 (первый урок — только NEW);
 *  - newOnlyTarget = allowMixed ? ceilDiv(total, 2) : total;
 *  - reviewSlots = subLessonSize / 2, currentSlotsInMixed = subLessonSize − reviewSlots;
 *  - NEW_ONLY под-уроки идут ПЕРВЫМИ, MIXED — после;
 *  - fillReviewSlots: приоритет reserve → main → fallback (текущий mixed-курсор);
 *  - ревью-карточки берутся из «due» предыдущих уроков по [intervals]-лестнице.
 *
 * Ноль Android-зависимостей (использует `java.util.ArrayDeque` — JVM stdlib).
 *
 * @param subLessonSize размер под-урока (карточек).
 * @param intervals     лестница интервалов ревью (по умолчанию [SrsConstants.INTERVAL_LADDER_DAYS]).
 */
class MixedReviewScheduler(
    private val subLessonSize: Int = TrainingConfig.SUB_LESSON_SIZE_DEFAULT,
    private val intervals: List<Int> = SrsConstants.INTERVAL_LADDER_DAYS,
) {

    /**
     * Построить расписание для списка уроков.
     *
     * Перенесено 1:1 из v1 `MixedReviewScheduler.build`, строки 25–108
     * (адаптировано под [LessonScheduleInput] / [CardId]; ревью-пулы берутся из
     * [LessonScheduleInput.reviewCardIds]/[reserveCardIds] вместо v1 Lesson.mainPoolCards/
     * reservePoolCards; `REVIEW_LIMIT` берётся из [TrainingConfig]).
     *
     * Возвращает список [LessonSchedule] (по одному на входной урок, в порядке входа),
     * НЕ Map — v2 предпочитает упорядоченный список (см. [SubLessonScheduler]).
     *
     * @param lessonSchedules входные элементы (урок + карты + ревью-пулы).
     * @return расписание для каждого урока.
     */
    fun build(lessonSchedules: List<LessonScheduleInput>): List<LessonSchedule> {
        if (lessonSchedules.isEmpty()) return emptyList()
        val lessons = lessonSchedules
        val schedules = mutableListOf<LessonSchedule>()
        val reviewQueues = mutableMapOf<LessonId, ArrayDeque<CardId>>()
        val reserveQueues = mutableMapOf<LessonId, ArrayDeque<CardId>>()
        val reviewStartMixedIndex = mutableMapOf<LessonId, Int>()
        val lessonIndexById = lessons.mapIndexed { index, lesson -> lesson.lessonId to index }.toMap()
        var globalMixedIndex = 0

        lessons.forEachIndexed { lessonIndex, lesson ->
            if (lessonIndex > 0) {
                val previousLessonId = lessons[lessonIndex - 1].lessonId
                reviewStartMixedIndex.putIfAbsent(previousLessonId, globalMixedIndex)
            }

            // Лимит ревью-карточек = REVIEW_LIMIT, сплит 50/50 (main/reserve).
            val allReviewCards = (lesson.reviewCardIds + lesson.reserveCardIds)
                .shuffled()
                .take(TrainingConfig.REVIEW_LIMIT)

            val mainCount = (allReviewCards.size / 2)
                .coerceAtLeast(lesson.reviewCardIds.size.coerceAtMost(150))
            reviewQueues[lesson.lessonId] = ArrayDeque(allReviewCards.take(mainCount))
            reserveQueues[lesson.lessonId] = ArrayDeque(allReviewCards.drop(mainCount))

            val currentCards = lesson.cardIds
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
                        globalMixedIndex,
                    ).take(2)
                    val reviewCards = if (reviewSlotsNeeded <= 0) {
                        emptyList()
                    } else {
                        fillReviewSlots(
                            dueLessons,
                            reviewQueues,
                            reserveQueues,
                            reviewSlotsNeeded,
                            mixedCurrentQueue,
                        )
                    }
                    val mixedCards = currentHalf + reviewCards
                    if (mixedCards.isNotEmpty()) {
                        mixedSubLessons.add(ScheduledSubLesson(SubLessonType.MIXED, mixedCards))
                    }
                }
            }

            // NEW_ONLY идут ПЕРВЫМИ, MIXED — после.
            repeat(newOnlyCount) {
                val cards = takeUpTo(newOnlyQueue, subLessonSize)
                if (cards.isNotEmpty()) {
                    subLessons.add(ScheduledSubLesson(SubLessonType.NEW_ONLY, cards))
                }
            }
            subLessons.addAll(mixedSubLessons)

            schedules.add(LessonSchedule(lesson.lessonId, subLessons))
        }

        return schedules
    }

    /**
     * Список уроков, «должных» ревью на данном [globalMixedIndex].
     *
     * Перенесено 1:1 из v1 `MixedReviewScheduler.dueLessonIds`, строки 110–124.
     * Урок due, если `(globalMixedIndex − startIndex)` входит в [intervals].
     * Сортировка по убыванию индекса урока (более поздние — первыми).
     */
    private fun dueLessonIds(
        reviewStartMixedIndex: Map<LessonId, Int>,
        lessonIndexById: Map<LessonId, Int>,
        globalMixedIndex: Int,
    ): List<LessonId> {
        if (globalMixedIndex <= 0) return emptyList()
        val due = mutableListOf<LessonId>()
        for ((lessonId, startIndex) in reviewStartMixedIndex) {
            val step = globalMixedIndex - startIndex
            if (intervals.contains(step)) {
                due.add(lessonId)
            }
        }
        return due.sortedByDescending { lessonIndexById[it] ?: -1 }
    }

    /**
     * Заполнить review-слоты с приоритетом: reserve → main → fallback.
     *
     * Перенесено 1:1 из v1 `MixedReviewScheduler.fillReviewSlots`, строки 126–196.
     * Карточки раздаются round-robin по due-урокам каждого пула; если не хватает —
     * берётся fallback из [fallbackQueue] (текущий mixed-курсор).
     */
    private fun fillReviewSlots(
        dueLessons: List<LessonId>,
        reviewQueues: Map<LessonId, ArrayDeque<CardId>>,
        reserveQueues: Map<LessonId, ArrayDeque<CardId>>,
        slots: Int,
        fallbackQueue: ArrayDeque<CardId>,
    ): List<CardId> {
        if (slots <= 0) return emptyList()

        // Приоритет 1: reserve-пулы.
        val result = mutableListOf<CardId>()
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

        // Приоритет 2: main-пулы.
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

        // Приоритет 3: fallback.
        if (result.size < slots) {
            result.addAll(takeUpTo(fallbackQueue, slots - result.size))
        }
        return result
    }

    /** Взять до [count] элементов из начала очереди. Перенесено из v1 строки 198–205. */
    private fun takeUpTo(queue: ArrayDeque<CardId>, count: Int): List<CardId> {
        if (count <= 0 || queue.isEmpty()) return emptyList()
        val result = ArrayList<CardId>(count)
        repeat(count.coerceAtMost(queue.size)) {
            result.add(queue.removeFirst())
        }
        return result
    }

    /** ceil-деление: `(numerator + denominator - 1) / denominator`, при numerator<=0 → 0. */
    private fun ceilDiv(numerator: Int, denominator: Int): Int {
        if (numerator <= 0) return 0
        return (numerator + denominator - 1) / denominator
    }
}
