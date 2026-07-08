package com.alexpo.grammermate.domain.progress

import com.alexpo.grammermate.domain.TrainingConfig
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.SubLessonType
import com.alexpo.grammermate.domain.training.ScheduledSubLesson
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты для [LessonCompletionCalculator].
 *
 * isLessonComplete:
 *  - effectiveCardCount = totalCards − hidden;
 *  - threshold = min(effective, LESSON_COMPLETION_CARD_THRESHOLD=150);
 *  - uniqueCardShows >= threshold.
 *
 * calculateCompletedSubLessons:
 *  - последовательный подсчёт с break на первом незавершённом;
 *  - все карты скрыты → авто-complete.
 */
class LessonCompletionCalculatorTest {

    // ── isLessonComplete ───────────────────────────────────────────────────

    @Test
    fun `isLessonComplete true when unique shows reaches effective threshold`() {
        // effective = 10-0 = 10; threshold = min(10,150)=10; shows=10 → complete.
        assertThat(LessonCompletionCalculator.isLessonComplete(10, 10, 0)).isTrue()
    }

    @Test
    fun `isLessonComplete false when unique shows below threshold`() {
        // threshold=10; shows=9 → false.
        assertThat(LessonCompletionCalculator.isLessonComplete(9, 10, 0)).isFalse()
    }

    @Test
    fun `isLessonComplete subtracts hidden cards from total`() {
        // total=10, hidden=3 → effective=7; threshold=7; shows=7 → true.
        assertThat(LessonCompletionCalculator.isLessonComplete(7, 10, 3)).isTrue()
        assertThat(LessonCompletionCalculator.isLessonComplete(6, 10, 3)).isFalse()
    }

    @Test
    fun `isLessonComplete threshold capped at 150`() {
        // effective=300; threshold=min(300,150)=150; shows=150 → true; 149 → false.
        assertThat(LessonCompletionCalculator.isLessonComplete(150, 300, 0)).isTrue()
        assertThat(LessonCompletionCalculator.isLessonComplete(149, 300, 0)).isFalse()
    }

    @Test
    fun `isLessonComplete threshold exactly 150 boundary`() {
        assertThat(LessonCompletionCalculator.isLessonComplete(150, 150, 0)).isTrue()
    }

    @Test
    fun `isLessonComplete false when effective count is zero`() {
        // total=5, hidden=5 → effective=0 → threshold=min(0,150)=0 → false.
        assertThat(LessonCompletionCalculator.isLessonComplete(5, 5, 5)).isFalse()
    }

    @Test
    fun `isLessonComplete false when more hidden than total`() {
        // total=3, hidden=5 → effective=-2 → coerceAtLeast(0)=0 → threshold=0 → false.
        assertThat(LessonCompletionCalculator.isLessonComplete(100, 3, 5)).isFalse()
    }

    @Test
    fun `isLessonComplete threshold equals training config constant`() {
        assertThat(TrainingConfig.LESSON_COMPLETION_CARD_THRESHOLD).isEqualTo(150)
    }

    // ── calculateCompletedSubLessons ───────────────────────────────────────

    private fun subLesson(vararg ids: String): ScheduledSubLesson =
        ScheduledSubLesson(SubLessonType.NEW_ONLY, ids.map { CardId(it) })

    @Test
    fun `calculateCompletedSubLessons empty shown set returns zero`() {
        val subLessons = listOf(subLesson("c1", "c2"))
        val result = LessonCompletionCalculator.calculateCompletedSubLessons(
            subLessons, shownCardIds = emptySet(),
            lessonCardIds = setOf(CardId("c1"), CardId("c2")),
            hiddenCardIds = emptySet(),
        )
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `calculateCompletedSubLessons counts completed sequential sublessons`() {
        val subLessons = listOf(
            subLesson("c1", "c2"),
            subLesson("c3", "c4"),
            subLesson("c5", "c6"),
        )
        val shown = setOf(CardId("c1"), CardId("c2"), CardId("c3"), CardId("c4"))
        val all = (1..6).map { CardId("c$it") }.toSet()
        val result = LessonCompletionCalculator.calculateCompletedSubLessons(
            subLessons, shown, all, hiddenCardIds = emptySet(),
        )
        // Первые два завершены, третий нет → 2.
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `calculateCompletedSubLessons breaks on first incomplete`() {
        val subLessons = listOf(
            subLesson("c1", "c2"),     // завершён
            subLesson("c3", "c4"),     // НЕ завершён (c4 не показан)
            subLesson("c5", "c6"),     // завершён (но не считается — break)
        )
        val shown = setOf(CardId("c1"), CardId("c2"), CardId("c3"), CardId("c5"), CardId("c6"))
        val all = (1..6).map { CardId("c$it") }.toSet()
        val result = LessonCompletionCalculator.calculateCompletedSubLessons(
            subLessons, shown, all, hiddenCardIds = emptySet(),
        )
        // Считаем последовательно: первый да, второй нет → break → 1.
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `calculateCompletedSubLessons all complete returns count`() {
        val subLessons = listOf(subLesson("c1"), subLesson("c2"), subLesson("c3"))
        val shown = setOf(CardId("c1"), CardId("c2"), CardId("c3"))
        val all = shown
        val result = LessonCompletionCalculator.calculateCompletedSubLessons(
            subLessons, shown, all, hiddenCardIds = emptySet(),
        )
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `calculateCompletedSubLessons skips hidden cards when checking completion`() {
        // c2 скрыта → под-урок завершён, если c1 показана (c2 игнор).
        val subLessons = listOf(subLesson("c1", "c2"))
        val shown = setOf(CardId("c1"))
        val all = setOf(CardId("c1"), CardId("c2"))
        val hidden = setOf(CardId("c2"))
        val result = LessonCompletionCalculator.calculateCompletedSubLessons(
            subLessons, shown, all, hidden,
        )
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `calculateCompletedSubLessons auto-completes sublesson with all hidden cards`() {
        // Все карты под-урока скрыты → авто-complete (continue).
        val subLessons = listOf(
            subLesson("h1", "h2"), // все скрыты → авто-завершён
            subLesson("c1", "c2"), // завершён
        )
        val shown = setOf(CardId("c1"), CardId("c2"))
        val all = setOf(CardId("h1"), CardId("h2"), CardId("c1"), CardId("c2"))
        val hidden = setOf(CardId("h1"), CardId("h2"))
        val result = LessonCompletionCalculator.calculateCompletedSubLessons(
            subLessons, shown, all, hidden,
        )
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `calculateCompletedSubLessons treats foreign card as shown`() {
        // Карточка не в lessonCardIds («чужая») считается показанной.
        val subLessons = listOf(subLesson("c1", "foreign_x"))
        val shown = setOf(CardId("c1")) // foreign_x не показана и не в уроке
        val all = setOf(CardId("c1")) // foreign_x НЕ в lessonCardIds
        val result = LessonCompletionCalculator.calculateCompletedSubLessons(
            subLessons, shown, all, hiddenCardIds = emptySet(),
        )
        // c1 показана, foreign_x чужая (не в lessonCardIds) → считается показанной → 1.
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `calculateCompletedSubLessons empty sublesson list returns zero`() {
        val result = LessonCompletionCalculator.calculateCompletedSubLessons(
            emptyList(), setOf(CardId("c1")), setOf(CardId("c1")), emptySet(),
        )
        assertThat(result).isEqualTo(0)
    }

    // ── advanceActiveSubLessonIndex (AC-14 Then.3: monotonic non-decreasing) ──

    @Test
    fun `advanceActiveSubLessonIndex advances when actual ahead of current`() {
        // current=1, actual=3, size=5 → maxOf(1,3)=3.
        assertThat(
            LessonCompletionCalculator.advanceActiveSubLessonIndex(1, 3, 5)
        ).isEqualTo(3)
    }

    @Test
    fun `advanceActiveSubLessonIndex never moves backward when actual below current`() {
        // AC-14 Then.3 regression anchor: actual dropped (e.g. after hiding cards)
        // but current index is preserved — maxOf(current, actual).
        // current=4, actual=2, size=5 → maxOf(4,2)=4 (never moves back to 2).
        assertThat(
            LessonCompletionCalculator.advanceActiveSubLessonIndex(4, 2, 5)
        ).isEqualTo(4)
    }

    @Test
    fun `advanceActiveSubLessonIndex equals when current equals actual`() {
        assertThat(
            LessonCompletionCalculator.advanceActiveSubLessonIndex(2, 2, 5)
        ).isEqualTo(2)
    }

    @Test
    fun `advanceActiveSubLessonIndex clamps to sublesson count`() {
        // Defensive: actual (or current) above size → clamped to size (valid index bound).
        // size=5 → index 5 = «за последним под-уроком» (сигнал «урок пройден»),
        // никогда 6 или больше.
        assertThat(
            LessonCompletionCalculator.advanceActiveSubLessonIndex(2, 7, 5)
        ).isEqualTo(5)
        assertThat(
            LessonCompletionCalculator.advanceActiveSubLessonIndex(9, 1, 5)
        ).isEqualTo(5)
    }

    @Test
    fun `advanceActiveSubLessonIndex handles negative inputs as zero`() {
        // Defensive: отрицательные входы coerceAtLeast(0).
        assertThat(
            LessonCompletionCalculator.advanceActiveSubLessonIndex(-2, -1, 5)
        ).isEqualTo(0)
    }

    @Test
    fun `advanceActiveSubLessonIndex returns zero when no sublessons`() {
        assertThat(
            LessonCompletionCalculator.advanceActiveSubLessonIndex(3, 2, 0)
        ).isEqualTo(0)
    }

    @Test
    fun `advanceActiveSubLessonIndex overload computes actual from sublessons and never regresses`() {
        // AC-14 Then.3 end-to-end: текущий индекс = 3 (пользователь дошёл до 3-го
        // под-урока), но после скрытия карт actualCompleted пересчитался в 1.
        // Индекс НЕ должен откатиться назад.
        val subLessons = listOf(
            subLesson("c1", "c2"),     // завершён
            subLesson("c3", "h1"),     // НЕ завершён (c3 не показан, h1 скрыта)
            subLesson("c4", "c5"),     // не считается (break)
            subLesson("c6", "c7"),     // не считается
        )
        val shown = setOf(CardId("c1"), CardId("c2"))
        val all = setOf(CardId("c1"), CardId("c2"), CardId("c3"), CardId("c4"), CardId("c5"), CardId("c6"), CardId("c7"))
        val hidden = setOf(CardId("h1"))
        val result = LessonCompletionCalculator.advanceActiveSubLessonIndex(
            currentIndex = 3,
            subLessons = subLessons,
            shownCardIds = shown,
            lessonCardIds = all,
            hiddenCardIds = hidden,
        )
        // actual = 1, current = 3 → maxOf(3,1) = 3 (не регрессирует).
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `advanceActiveSubLessonIndex overload advances forward normally`() {
        // Все первые 2 под-урока завершены, current=0 → advancing to 2.
        val subLessons = listOf(
            subLesson("c1", "c2"),
            subLesson("c3", "c4"),
            subLesson("c5", "c6"),
        )
        val shown = setOf(CardId("c1"), CardId("c2"), CardId("c3"), CardId("c4"))
        val all = (1..6).map { CardId("c$it") }.toSet()
        val result = LessonCompletionCalculator.advanceActiveSubLessonIndex(
            currentIndex = 0,
            subLessons = subLessons,
            shownCardIds = shown,
            lessonCardIds = all,
            hiddenCardIds = emptySet(),
        )
        assertThat(result).isEqualTo(2)
    }
}
