package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit-тесты для [SubLessonScheduler] — чистая функция нарезки пула карточек
 * на под-уроки (замена рантайм-пересборки v1, которая теряла card_15).
 */
class SubLessonSchedulerTest {

    private val packId = PackId("pack")
    private val lessonId = LessonId("lesson")

    /** N карт от card_0 до card_(n-1). */
    private fun cards(n: Int): List<Card> = (0 until n).map { idx ->
        Card(
            id = CardId("card_$idx"),
            packId = packId,
            lessonId = lessonId,
            ord = idx,
            type = CardType.SENTENCE,
            promptRu = "p$idx",
            acceptedAnswers = listOf("a$idx"),
            tense = null,
            verb = null,
            verbGroup = null,
            person = null,
            frequencyRank = idx,
        )
    }

    @Test
    fun `buildSubLessons splits cards evenly by sessionSize`() {
        val subLessons = SubLessonScheduler.buildSubLessons(cards(20), sessionSize = 5)

        assertThat(subLessons).hasSize(4)
        assertThat(subLessons[0]).containsExactly(
            CardId("card_0"), CardId("card_1"), CardId("card_2"),
            CardId("card_3"), CardId("card_4"),
        ).inOrder()
        assertThat(subLessons.last()).containsExactly(
            CardId("card_15"), CardId("card_16"), CardId("card_17"),
            CardId("card_18"), CardId("card_19"),
        ).inOrder()
        // Все карты распределены ровно по одному разу, без потерь.
        assertThat(subLessons.flatten().toSet()).hasSize(20)
    }

    @Test
    fun `buildSubLessons last chunk is smaller when not evenly divisible`() {
        val subLessons = SubLessonScheduler.buildSubLessons(cards(13), sessionSize = 5)

        assertThat(subLessons).hasSize(3)
        assertThat(subLessons[0]).hasSize(5)
        assertThat(subLessons[1]).hasSize(5)
        assertThat(subLessons[2]).hasSize(3)
        assertThat(subLessons.flatten().toSet()).hasSize(13)
    }

    @Test
    fun `buildSubLessons sessionSize larger than total yields single chunk`() {
        val subLessons = SubLessonScheduler.buildSubLessons(cards(3), sessionSize = 100)

        assertThat(subLessons).hasSize(1)
        assertThat(subLessons[0]).hasSize(3)
    }

    @Test
    fun `buildSubLessons empty input yields empty list`() {
        assertThat(SubLessonScheduler.buildSubLessons(emptyList(), sessionSize = 5)).isEmpty()
    }

    @Test
    fun `activeSubLesson returns chunk at activeIndex`() {
        val subLessons = SubLessonScheduler.buildSubLessons(cards(15), sessionSize = 5)

        assertThat(SubLessonScheduler.activeSubLesson(subLessons, activeIndex = 2))
            .containsExactly(
                CardId("card_10"), CardId("card_11"), CardId("card_12"),
                CardId("card_13"), CardId("card_14"),
            ).inOrder()
    }

    @Test
    fun `activeSubLesson clamps negative index to first chunk`() {
        val subLessons = SubLessonScheduler.buildSubLessons(cards(10), sessionSize = 5)

        assertThat(SubLessonScheduler.activeSubLesson(subLessons, activeIndex = -3))
            .isEqualTo(subLessons[0])
    }

    @Test
    fun `activeSubLesson clamps out-of-range index to last chunk`() {
        val subLessons = SubLessonScheduler.buildSubLessons(cards(10), sessionSize = 5)

        assertThat(SubLessonScheduler.activeSubLesson(subLessons, activeIndex = 99))
            .isEqualTo(subLessons.last())
    }

    @Test
    fun `activeSubLesson empty input returns empty pool`() {
        val result: List<CardId> = SubLessonScheduler.activeSubLesson(emptyList(), activeIndex = 0)
        assertThat(result).isEmpty()
    }
}
