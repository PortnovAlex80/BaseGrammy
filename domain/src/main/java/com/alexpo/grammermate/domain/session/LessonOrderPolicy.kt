package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.TrainingMode

/**
 * Политика порядка карт урока (Фаза 4 срез 1: Sequential/Mixed review).
 *
 * v1 `MixedReviewScheduler` пересобирал пул в рантайме и терял идентичность
 * текущей карты (баг `card_15`). v2: порядок — ЧИСТАЯ детерминированная
 * функция от входа, пул фиксируется на старте сессии и хранится в снимке
 * целиком (resume-инварианты не зависят от политики).
 *
 * `ALL_MIXED` — чередование первой и второй половины урока
 * (`a₁b₁a₂b₂…`): карты из разных частей урока идут вперемешку, ломая
 * механическое запоминание последовательности. НЕ SRS-driven повторение:
 * интервалы повторов — слой 2 ADR-002 (после гейта FSRS).
 */
object LessonOrderPolicy {

    /**
     * Упорядочить карты урока по режиму.
     *
     * [TrainingMode.LESSON]/[TrainingMode.ALL_SEQUENTIAL] — как есть
     * (порядок `ord`); [TrainingMode.ALL_MIXED] — [interleave].
     */
    fun apply(cards: List<Card>, mode: TrainingMode): List<Card> = when (mode) {
        TrainingMode.LESSON, TrainingMode.ALL_SEQUENTIAL,
        TrainingMode.VERB_DRILL, // порядок drill-пула задаётся при сборке (rank)
            -> cards
        TrainingMode.ALL_MIXED -> interleave(cards)
    }

    /**
     * Чередование половин: `[a1, b1, a2, b2, a3]` для входа
     * `[a1, a2, a3, b1, b2]`. Детерминировано (без random/seed) —
     * повторный вызов на том же входе даёт тот же результат.
     *
     * Входы короче 3 карт не перемешиваются (чередование бессмысленно).
     */
    fun interleave(cards: List<Card>): List<Card> {
        if (cards.size < 3) return cards
        val half = (cards.size + 1) / 2
        val first = cards.subList(0, half)
        val second = cards.subList(half, cards.size)
        return buildList {
            first.forEachIndexed { i, card ->
                add(card)
                second.getOrNull(i)?.let(::add)
            }
        }
    }
}
