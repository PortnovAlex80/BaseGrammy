package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Пины правил кривой забывания.
 * Спека: docs/specification/forgetting-curve-review-scheduling.md
 */
class ReviewSelectorTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun lesson(id: String, cards: Int): Lesson = Lesson(
        id = LessonId(id),
        languageId = LanguageId("it"),
        title = id,
        cards = (1..cards).map {
            SentenceCard(
                id = "${id}_c$it",
                promptRu = "ru$it",
                acceptedAnswers = listOf("it$it")
            )
        }
    )

    private fun mastery(
        id: String,
        lastReviewMs: Long,
        step: Int = 0,
        effortAtLastReview: Int = 0
    ) = LessonMasteryState(
        lessonId = LessonId(id),
        languageId = LanguageId("it"),
        intervalStepIndex = step,
        lastReviewMs = lastReviewMs,
        effortAtLastReview = effortAtLastReview
    )

    // ── Две шкалы ────────────────────────────────────────────────────────

    @Test
    fun effectiveDays_takesMaxOfCalendarAndEffort() {
        // Календарь: 2 дня. Усилия: 0 карточек.
        val calendarDriven = SpacedRepetitionConfig.effectiveDays(
            nowMs = now,
            lastReviewMs = now - 2 * day,
            totalEffortCards = 100,
            effortAtLastReview = 100
        )
        assertThat(calendarDriven).isWithin(0.01).of(2.0)

        // Календарь: 0 дней (тот же день). Усилия: 180 карточек = 3 дня.
        val effortDriven = SpacedRepetitionConfig.effectiveDays(
            nowMs = now,
            lastReviewMs = now,
            totalEffortCards = 280,
            effortAtLastReview = 100
        )
        assertThat(effortDriven).isWithin(0.01).of(3.0)
    }

    @Test
    fun effectiveDays_sixtyCardShowsIsOneEffortDay() {
        val days = SpacedRepetitionConfig.effectiveDays(
            nowMs = now,
            lastReviewMs = now,
            totalEffortCards = 60,
            effortAtLastReview = 0
        )
        assertThat(days).isWithin(0.001).of(1.0)
    }

    @Test
    fun effectiveDays_neverReviewedIsZero() {
        val days = SpacedRepetitionConfig.effectiveDays(
            nowMs = now,
            lastReviewMs = 0L,
            totalEffortCards = 1000,
            effortAtLastReview = 0
        )
        assertThat(days).isEqualTo(0.0)
    }

    // ── Просроченность как ранг ──────────────────────────────────────────

    @Test
    fun moreOverdueLessonWins() {
        val l1 = lesson("l1", 20)
        val l2 = lesson("l2", 20)
        // step 0 → ожидаемый интервал 1 день.
        // l1 просрочен втрое, l2 ровно созрел.
        val masteries = mapOf(
            "l1" to mastery("l1", lastReviewMs = now - 3 * day, step = 0),
            "l2" to mastery("l2", lastReviewMs = now - 1 * day, step = 0)
        )

        val picked = ReviewSelector.selectReviewCards(
            candidates = listOf(l1, l2),
            masteryOf = { masteries[it] },
            totalEffortCards = 0,
            nowMs = now,
            slots = 5,
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertThat(picked).hasSize(5)
        assertThat(picked.map { it.id }).containsNoneIn(l2.cards.map { it.id })
        assertThat(picked.first().id).startsWith("l1_")
    }

    @Test
    fun nothingDue_stillFillsBlockWithClosestToDue() {
        val l1 = lesson("l1", 20)
        // Пересмотрен только что, шаг 3 (интервал 7 дней) — не созрел.
        val masteries = mapOf("l1" to mastery("l1", lastReviewMs = now, step = 3))

        val picked = ReviewSelector.selectReviewCards(
            candidates = listOf(l1),
            masteryOf = { masteries[it] },
            totalEffortCards = 0,
            nowMs = now,
            slots = 5,
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // Размер блока обязан сохраниться — от него зависит подсчёт прогресса.
        assertThat(picked).hasSize(5)
    }

    @Test
    fun lessonNeverSelfProduced_isNeverSelected() {
        val l1 = lesson("l1", 20)
        val masteries = mapOf("l1" to mastery("l1", lastReviewMs = 0L))

        val picked = ReviewSelector.selectReviewCards(
            candidates = listOf(l1),
            masteryOf = { masteries[it] },
            totalEffortCards = 0,
            nowMs = now,
            slots = 5,
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertThat(picked).isEmpty()
    }

    @Test
    fun lessonWithoutMastery_isNeverSelected() {
        val l1 = lesson("l1", 20)

        val picked = ReviewSelector.selectReviewCards(
            candidates = listOf(l1),
            masteryOf = { null },
            totalEffortCards = 0,
            nowMs = now,
            slots = 5,
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertThat(picked).isEmpty()
    }

    @Test
    fun hiddenCardsAreExcluded() {
        val l1 = lesson("l1", 6)
        val masteries = mapOf("l1" to mastery("l1", lastReviewMs = now - 5 * day))
        val hidden = setOf("l1_c1", "l1_c2", "l1_c3")

        val picked = ReviewSelector.selectReviewCards(
            candidates = listOf(l1),
            masteryOf = { masteries[it] },
            totalEffortCards = 0,
            nowMs = now,
            slots = 3,
            activeSubLessonIndex = 0,
            hiddenCardIds = hidden
        )

        assertThat(picked.map { it.id }).containsNoneIn(hidden)
    }

    // ── Непрерывный отрезок ──────────────────────────────────────────────

    @Test
    fun drawIsContiguous_preservingAuthoredPairs() {
        val cards = lesson("l1", 20).cards
        val run = ReviewSelector.drawRun(cards, stepIndex = 0, activeSubLessonIndex = 0, slots = 5)

        assertThat(run).hasSize(5)
        val startIndex = cards.indexOfFirst { it.id == run.first().id }
        run.forEachIndexed { offset, card ->
            assertThat(card.id).isEqualTo(cards[(startIndex + offset) % cards.size].id)
        }
    }

    @Test
    fun drawIsDeterministic_sameBlockRebuiltTwiceIsIdentical() {
        val cards = lesson("l1", 20).cards
        val first = ReviewSelector.drawRun(cards, stepIndex = 2, activeSubLessonIndex = 3, slots = 5)
        val second = ReviewSelector.drawRun(cards, stepIndex = 2, activeSubLessonIndex = 3, slots = 5)

        assertThat(first.map { it.id }).isEqualTo(second.map { it.id })
    }

    @Test
    fun drawRotatesBetweenBlocks() {
        val cards = lesson("l1", 40).cards
        val block0 = ReviewSelector.drawRun(cards, stepIndex = 0, activeSubLessonIndex = 0, slots = 5)
        val block1 = ReviewSelector.drawRun(cards, stepIndex = 0, activeSubLessonIndex = 1, slots = 5)

        assertThat(block0.map { it.id }).isNotEqualTo(block1.map { it.id })
    }

    @Test
    fun drawWrapsAroundEndOfLesson() {
        val cards = lesson("l1", 6).cards
        // offset = (0*7 + 5) * 4 = 20; 20 % 6 = 2 → старт с индекса 2, заворот.
        val run = ReviewSelector.drawRun(cards, stepIndex = 0, activeSubLessonIndex = 5, slots = 4)

        assertThat(run).hasSize(4)
        assertThat(run.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun drawNeverExceedsLessonSize() {
        val cards = lesson("l1", 3).cards
        val run = ReviewSelector.drawRun(cards, stepIndex = 0, activeSubLessonIndex = 0, slots = 10)

        assertThat(run).hasSize(3)
    }
}
