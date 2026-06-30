package com.alexpo.grammermate.v2.core.domain.session

import com.alexpo.grammermate.v2.core.domain.model.Card
import com.alexpo.grammermate.v2.core.domain.model.CardId

/**
 * Pure function для нарезки карточек урока на под-уроки.
 *
 * Это упрощённая замена v1-го `MixedReviewScheduler`, который пересобирал
 * пул в рантайме и терял идентичность текущей карты (баг `card_15`).
 *
 * Здесь нарезка детерминирована и иммутабельна: входной список карт
 * однозначно отображается в упорядоченный список пулов по [CardId]. Никакого
 * скрытого состояния, никакого рантайм-пересчёта — пул фиксируется на старте
 * сессии и хранится целиком в [com.alexpo.grammermate.v2.core.domain.model.SessionSnapshot.poolCardIds].
 *
 * Стратегия v2 (упрощённая, задокументированная):
 * - карты урока берутся в порядке [Card.ord] / исходного порядка;
 * - режутся подряд на чанки по [sessionSize] (последний чанк может быть короче);
 * - внутри чанка порядок сохраняется.
 *
 * Сложная v1-логика «первая половина NEW_ONLY, вторая MIXED» сознательно не
 * воспроизводится — для устойчивости resume важна именно детерминированная
 * нарезка по PK, а не перемешивание. При необходимости смешивание вводится
 * отдельным, тестируемым шагом перед нарезкой.
 */
object SubLessonScheduler {

    /**
     * Разбить [cards] на под-уроки по [sessionSize] карт.
     *
     * @param cards       карты урока (порядок сохраняется).
     * @param sessionSize размер одного под-урока (>0; ≤0 трактуется как «всё в один чанк»).
     * @return список пулов, каждый — упорядоченный список [CardId]. Пустой вход → пустой список.
     */
    fun buildSubLessons(cards: List<Card>, sessionSize: Int): List<List<CardId>> {
        if (cards.isEmpty()) return emptyList()
        val size = if (sessionSize <= 0) cards.size else sessionSize
        return cards.chunked(size) { chunk -> chunk.map { it.id } }
    }

    /**
     * Выбрать активный под-урок по индексу с clamping.
     *
     * @param subLessons   результат [buildSubLessons].
     * @param activeIndex  желаемый индекс под-урока.
     * @return выбранный пул; отрицательный индекс → первый, выход за пределы → последний.
     *        Пустой [subLessons] → пустой список.
     */
    fun activeSubLesson(subLessons: List<List<CardId>>, activeIndex: Int): List<CardId> {
        if (subLessons.isEmpty()) return emptyList()
        val clamped = activeIndex.coerceIn(0, subLessons.lastIndex)
        return subLessons[clamped]
    }
}
