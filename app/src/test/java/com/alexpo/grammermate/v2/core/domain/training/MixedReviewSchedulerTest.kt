package com.alexpo.grammermate.v2.core.domain.training

import com.alexpo.grammermate.v2.core.domain.model.CardId
import com.alexpo.grammermate.v2.core.domain.model.LessonId
import com.alexpo.grammermate.v2.core.domain.model.SubLessonType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты для [MixedReviewScheduler] — планировщика смешанных сессий.
 *
 * Ключевые инварианты (перенесённые из v1):
 *  - первый урок (lessonIndex==0) → только NEW_ONLY;
 *  - allowMixed (lessonIndex>0) → NEW_ONLY под-уроки идут ПЕРВЫМИ, MIXED — после;
 *  - каждый под-урок размером ≤ subLessonSize;
 *  - все карточки урока покрыты (как множество) в первом уроке.
 *
 * Т.к. внутри есть shuffle (дистракторы/ревью), проверяем структурные,
 * а не побайтовые инварианты.
 */
class MixedReviewSchedulerTest {

    private val lessonId = LessonId("lesson_0")

    /** Список CardId вида card_0..card_(n-1). */
    private fun cardIds(n: Int, prefix: String = "card"): List<CardId> =
        (0 until n).map { CardId("$prefix$it") }

    /** Вход для одного урока без ревью-пулов. */
    private fun input(id: String, cardCount: Int): LessonScheduleInput =
        LessonScheduleInput(
            lessonId = LessonId(id),
            cardIds = cardIds(cardCount, prefix = "${id}_c"),
        )

    // ── build: пустой вход ─────────────────────────────────────────────────

    @Test
    fun `build empty input returns empty list`() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        assertThat(scheduler.build(emptyList())).isEmpty()
    }

    // ── Первый урок (lessonIndex==0) → только NEW_ONLY ─────────────────────

    @Test
    fun `build first lesson produces only NEW_ONLY sublessons`() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        val result = scheduler.build(listOf(input("L0", 25)))
        assertThat(result).hasSize(1)
        val types = result[0].subLessons.map { it.type }
        // Все под-уроки первого урока — NEW_ONLY (нет MIXED).
        assertThat(types).doesNotContain(SubLessonType.MIXED)
        assertThat(types).contains(SubLessonType.NEW_ONLY)
        types.forEach { assertThat(it).isEqualTo(SubLessonType.NEW_ONLY) }
    }

    @Test
    fun `build first lesson all sublessons are NEW_ONLY`() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        val result = scheduler.build(listOf(input("L0", 35)))
        result[0].subLessons.forEach { sub ->
            assertThat(sub.type).isEqualTo(SubLessonType.NEW_ONLY)
        }
    }

    @Test
    fun `build first lesson covers all cards of the lesson`() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        val allCards = cardIds(22, prefix = "L0_c")
        val result = scheduler.build(
            listOf(LessonScheduleInput(lessonId = LessonId("L0"), cardIds = allCards)),
        )
        val produced = result[0].subLessons.flatMap { it.cardIds }.toSet()
        assertThat(produced).containsExactlyElementsIn(allCards)
    }

    @Test
    fun `build each NEW_ONLY sublesson size does not exceed subLessonSize`() {
        val scheduler = MixedReviewScheduler(subLessonSize = 6)
        val result = scheduler.build(listOf(input("L0", 20)))
        result[0].subLessons.forEach { sub ->
            assertThat(sub.cardIds.size).isAtMost(6)
        }
    }

    @Test
    fun `build splits first lesson into ceilDiv count sublessons`() {
        // 20 карт / subLessonSize 6 → ceilDiv(20,6)=4 NEW_ONLY под-урока (только первый урок).
        val scheduler = MixedReviewScheduler(subLessonSize = 6)
        val result = scheduler.build(listOf(input("L0", 20)))
        val newOnly = result[0].subLessons.filter { it.type == SubLessonType.NEW_ONLY }
        assertThat(newOnly).hasSize(4)
    }

    // ── Несколько уроков: NEW_ONLY идут ПЕРВЫМИ, MIXED после ───────────────

    @Test
    fun `build preserves lesson order in output`() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        val inputs = listOf(input("L0", 10), input("L1", 10), input("L2", 10))
        val result = scheduler.build(inputs)
        assertThat(result.map { it.lessonId })
            .containsExactly(LessonId("L0"), LessonId("L1"), LessonId("L2")).inOrder()
    }

    @Test
    fun `build NEW_ONLY sublessons always precede MIXED sublessons`() {
        // Инвариант v1: NEW_ONLY идут первыми, MIXED — после, в любом уроке.
        val scheduler = MixedReviewScheduler(subLessonSize = 6)
        val inputs = listOf(
            input("L0", 12),
            LessonScheduleInput(
                lessonId = LessonId("L1"),
                cardIds = cardIds(12, prefix = "L1_c"),
                reviewCardIds = cardIds(6, prefix = "L0_c"), // ревью из L0
            ),
        )
        val result = scheduler.build(inputs)
        for (schedule in result) {
            val types = schedule.subLessons.map { it.type }
            val firstMixed = types.indexOf(SubLessonType.MIXED)
            val lastNewOnly = types.lastIndexOf(SubLessonType.NEW_ONLY)
            if (firstMixed >= 0 && lastNewOnly >= 0) {
                assertThat(lastNewOnly).isLessThan(firstMixed)
            }
        }
    }

    @Test
    fun `build second lesson may contain MIXED sublessons`() {
        // Второй урок (lessonIndex>0) с ревью-пулом → могут появиться MIXED.
        val scheduler = MixedReviewScheduler(subLessonSize = 6)
        val inputs = listOf(
            input("L0", 12),
            LessonScheduleInput(
                lessonId = LessonId("L1"),
                cardIds = cardIds(20, prefix = "L1_c"),
                reviewCardIds = cardIds(12, prefix = "L0_c"),
            ),
        )
        val result = scheduler.build(inputs)
        // Хоть один MIXED должен появиться (allowMixed=true, mixedCurrent не пуст).
        val hasMixed = result.any { it.subLessons.any { s -> s.type == SubLessonType.MIXED } }
        assertThat(hasMixed).isTrue()
    }

    // ── subLessonSize по умолчанию и интервалы ─────────────────────────────

    @Test
    fun `build default subLessonSize is ten`() {
        // 10 карт → 1 под-урок; 11 карт → 2 под-урока (ceilDiv(11,10)=2).
        val scheduler = MixedReviewScheduler() // default SUB_LESSON_SIZE_DEFAULT=10
        val r1 = scheduler.build(listOf(input("L0", 10)))
        assertThat(r1[0].subLessons).hasSize(1)
        val r2 = scheduler.build(listOf(input("L0", 11)))
        assertThat(r2[0].subLessons).hasSize(2)
    }

    @Test
    fun `build MIXED sublesson size does not exceed subLessonSize`() {
        val scheduler = MixedReviewScheduler(subLessonSize = 6)
        val inputs = listOf(
            input("L0", 12),
            LessonScheduleInput(
                lessonId = LessonId("L1"),
                cardIds = cardIds(20, prefix = "L1_c"),
                reviewCardIds = cardIds(12, prefix = "L0_c"),
            ),
        )
        val result = scheduler.build(inputs)
        result.forEach { schedule ->
            schedule.subLessons
                .filter { it.type == SubLessonType.MIXED }
                .forEach { sub -> assertThat(sub.cardIds.size).isAtMost(6) }
        }
    }

    @Test
    fun `build all sublessons carry card ids not blank`() {
        val scheduler = MixedReviewScheduler(subLessonSize = 6)
        val result = scheduler.build(listOf(input("L0", 18)))
        result[0].subLessons.forEach { sub ->
            sub.cardIds.forEach { id -> assertThat(id.value).isNotEmpty() }
        }
    }

    @Test
    fun `build returns one schedule per input lesson`() {
        val scheduler = MixedReviewScheduler(subLessonSize = 10)
        val inputs = listOf(input("A", 5), input("B", 5), input("C", 5))
        val result = scheduler.build(inputs)
        assertThat(result).hasSize(3)
    }
}
