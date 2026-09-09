package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SpacedRepetitionConfig

/**
 * Выбирает карточки повторения из **старых** уроков по кривой забывания.
 *
 * Заполняет слоты, которые [com.alexpo.grammermate.data.MixedReviewScheduler]
 * оставил в смешанном блоке. Чистая функция: ничего не мутирует и не пишет в
 * хранилище, поэтому один и тот же блок при пересборке сессии (навигация,
 * восстановление прогресса) всегда даёт один и тот же набор карточек.
 *
 * Правила — см. `docs/specification/forgetting-curve-review-scheduling.md`:
 *  - «созрел» это **ранг**, а не событие: уроки сортируются по степени
 *    просроченности, поэтому потерять повторение структурно невозможно;
 *  - берётся **непрерывный отрезок** карточек одного урока, потому что внутри
 *    урока соседние карточки образуют контрастные пары (ед./мн. число, м./ж. род),
 *    и разрывать их нельзя;
 *  - один урок на блок — дробление слотов между уроками снова ломало бы отрезки.
 */
object ReviewSelector {

    /** Шаг ротации отрезка между повторениями одного урока. */
    private const val ROTATION_STRIDE = 7

    /**
     * @param candidates уроки-предшественники в порядке курса (текущий не входит)
     * @param masteryOf  доступ к mastery по lessonId
     * @param totalEffortCards суммарные показы карточек по паку (шкала усилий)
     * @param nowMs текущее время
     * @param slots сколько карточек нужно
     * @param activeSubLessonIndex индекс текущего блока — часть детерминированной ротации
     * @param hiddenCardIds скрытые карточки, которые нельзя показывать
     */
    fun selectReviewCards(
        candidates: List<Lesson>,
        masteryOf: (String) -> LessonMasteryState?,
        totalEffortCards: Int,
        nowMs: Long,
        slots: Int,
        activeSubLessonIndex: Int,
        hiddenCardIds: Set<String>
    ): List<SentenceCard> {
        if (slots <= 0 || candidates.isEmpty()) return emptyList()

        val ranked = candidates
            .mapNotNull { lesson ->
                val mastery = masteryOf(lesson.id.value) ?: return@mapNotNull null
                // Урок, который ни разу не воспроизводили самостоятельно,
                // в повторение не идёт — повторять нечего.
                if (mastery.lastReviewMs <= 0L) return@mapNotNull null
                val visible = lesson.allCards.filter { it.id !in hiddenCardIds }
                if (visible.isEmpty()) return@mapNotNull null

                val effectiveDays = SpacedRepetitionConfig.effectiveDays(
                    nowMs = nowMs,
                    lastReviewMs = mastery.lastReviewMs,
                    totalEffortCards = totalEffortCards,
                    effortAtLastReview = mastery.effortAtLastReview
                )
                val ratio = SpacedRepetitionConfig.overdueRatio(effectiveDays, mastery.intervalStepIndex)
                Ranked(lesson, visible, mastery, ratio)
            }
            // Самый просроченный — первым. Если ничего не созрело, берём
            // ближайший к созреванию: блок обязан сохранить свой размер,
            // от него зависит subLessonTotal и подсчёт прогресса.
            .sortedByDescending { it.overdueRatio }

        val winner = ranked.firstOrNull() ?: return emptyList()
        return drawRun(
            cards = winner.visibleCards,
            stepIndex = winner.mastery.intervalStepIndex,
            activeSubLessonIndex = activeSubLessonIndex,
            slots = slots
        )
    }

    /**
     * Непрерывный отрезок длиной [slots], начиная с детерминированной позиции,
     * с заворотом через конец урока.
     *
     * Начало зависит только от того, что не меняется внутри блока: шага лестницы
     * урока и индекса блока. Поэтому пересборка сессии не перетасовывает карточки
     * под пользователем, а между блоками и между повторениями отрезок сдвигается.
     */
    internal fun drawRun(
        cards: List<SentenceCard>,
        stepIndex: Int,
        activeSubLessonIndex: Int,
        slots: Int
    ): List<SentenceCard> {
        if (cards.isEmpty() || slots <= 0) return emptyList()
        val size = cards.size
        val take = slots.coerceAtMost(size)
        val offset = (stepIndex * ROTATION_STRIDE + activeSubLessonIndex) * slots
        val start = ((offset % size) + size) % size
        return List(take) { cards[(start + it) % size] }
    }

    private data class Ranked(
        val lesson: Lesson,
        val visibleCards: List<SentenceCard>,
        val mastery: LessonMasteryState,
        val overdueRatio: Double
    )
}
