package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.TrainingMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-тесты политики порядка урока (Фаза 4 срез 1: mixed review).
 *
 * Ключевое свойство — детерминизм: тот же вход даёт тот же порядок (без
 * random/seed), поэтому resume-инварианты SessionEngine не зависят от
 * политики (пул персистится в снимке целиком).
 */
class LessonOrderPolicyTest {

    private fun card(id: String): Card = Card(
        id = CardId(id),
        packId = PackId("pack"),
        lessonId = LessonId("lesson"),
        ord = 0,
        type = CardType.SENTENCE,
        promptRu = id,
        acceptedAnswers = listOf(id),
        tense = null, verb = null, verbGroup = null, person = null,
        frequencyRank = null,
    )

    @Test
    fun `sequential modes keep original order`() {
        val cards = listOf("a", "b", "c", "d").map(::card)

        assertThat(LessonOrderPolicy.apply(cards, TrainingMode.LESSON))
            .containsExactlyElementsIn(cards).inOrder()
        assertThat(LessonOrderPolicy.apply(cards, TrainingMode.ALL_SEQUENTIAL))
            .containsExactlyElementsIn(cards).inOrder()
    }

    @Test
    fun `mixed interleaves halves deterministically`() {
        val cards = listOf("a1", "a2", "a3", "b1", "b2").map(::card)

        val mixed = LessonOrderPolicy.apply(cards, TrainingMode.ALL_MIXED)

        assertThat(mixed.map { it.id.value })
            .containsExactly("a1", "b1", "a2", "b2", "a3")
            .inOrder()
        // Тот же вход — тот же результат (никакого рантайм-рандома).
        assertThat(LessonOrderPolicy.apply(cards, TrainingMode.ALL_MIXED))
            .containsExactlyElementsIn(mixed).inOrder()
    }

    @Test
    fun `short and empty inputs are not interleaved`() {
        assertThat(LessonOrderPolicy.interleave(emptyList())).isEmpty()
        assertThat(LessonOrderPolicy.interleave(listOf("a").map(::card)))
            .hasSize(1)
        // 2 карты: чередование половин бессмысленно — порядок сохранён.
        assertThat(LessonOrderPolicy.interleave(listOf("a", "b").map(::card)).map { it.id.value })
            .containsExactly("a", "b").inOrder()
    }

    @Test
    fun `interleave preserves the full set of cards`() {
        val cards = (0 until 9).map { "c$it" }.map(::card)

        val mixed = LessonOrderPolicy.interleave(cards)

        assertThat(mixed).hasSize(cards.size)
        assertThat(mixed.toSet()).isEqualTo(cards.toSet())
    }
}
