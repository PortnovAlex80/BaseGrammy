package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.countsTowardMastery
import com.alexpo.grammermate.testharness.FakeMasteryStore
import com.alexpo.grammermate.testharness.FakeTrainingStateAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 (item 3.8): pins each business rule to exactly one definition.
 *
 * - progress = lessonsCompleted / chapter total (NOT / lessonsStarted)
 * - completion = completedAtMs stamped at uniqueCardShows >= min(effectiveCards, 150)
 * - intervalStepIndex alone does NOT complete a lesson
 * - WORD_BANK never counts toward mastery (single predicate)
 * - chapter progress is pack-scoped
 */
class ChapterProgressRulesTest {

    private val calculator = ChapterProgressCalculator

    private fun chapter(vararg lessons: String) =
        Chapter(chapterId = "chapter_x", order = 0, title = "X", lessons = lessons.toList())

    private fun mastery(id: String, shows: Int, step: Int = 0, completedAtMs: Long? = null) =
        LessonMasteryState(
            lessonId = LessonId(id),
            languageId = LanguageId("it"),
            uniqueCardShows = shows,
            totalCardShows = shows,
            lastShowDateMs = 100L,
            intervalStepIndex = step,
            completedAtMs = completedAtMs
        )

    @Test
    fun `progress divides by the chapter total, not by lessons started`() {
        val ch = chapter("l1", "l2", "l3")
        val states = mapOf(
            "l1" to mastery("l1", shows = 10, completedAtMs = 100L),
            "l2" to mastery("l2", shows = 5),
            "l3" to mastery("l3", shows = 0)
        )
        val progress = calculator.calculateChapterProgress(ch, states)

        assertEquals(3, progress.totalLessons)
        assertEquals(2, progress.lessonsStarted)
        assertEquals(1, progress.lessonsCompleted)
        assertEquals(1f / 3f, progress.progress)
    }

    @Test
    fun `story-only chapter has zero total and zero progress without throwing`() {
        val ch = chapter()
        val progress = calculator.calculateChapterProgress(ch, emptyMap())

        assertEquals(0, progress.totalLessons)
        assertEquals(0f, progress.progress)
    }

    @Test
    fun `intervalStepIndex alone does not complete a lesson`() {
        val ch = chapter("l1", "l2")
        val states = mapOf(
            // Step 9 (full SRS maturity) but never stamped → NOT completed.
            "l1" to mastery("l1", shows = 10, step = 9),
            // Stamped at step 0 → completed: the stamp is the only signal.
            "l2" to mastery("l2", shows = 10, step = 0, completedAtMs = 50L)
        )
        val progress = calculator.calculateChapterProgress(ch, states)

        assertEquals(1, progress.lessonsCompleted)
    }

    @Test
    fun `completion threshold is min of effective cards and 150`() {
        val fakeMastery = FakeMasteryStore()
        val packId = "THRESH_PACK"

        fun cards(n: Int) = (1..n).map { SentenceCard("c$it", "p$it", listOf("a$it")) }
        val smallLesson = Lesson(LessonId("small"), LanguageId("it"), "S", cards(10))
        val bigLesson = Lesson(LessonId("big"), LanguageId("it"), "B", cards(300))

        // Small lesson: threshold = 10 cards → 9 shows is NOT enough.
        fakeMastery.saveForPack(mastery("small", shows = 9), packId)
        // Big lesson: threshold capped at 150 → 150 shows IS enough.
        fakeMastery.saveForPack(mastery("big", shows = 150), packId)

        tracker(fakeMastery).recalculateCompletionsExcludingHidden(
            lessons = listOf(smallLesson, bigLesson),
            languageId = LanguageId("it"),
            hiddenCardIds = emptySet(),
            packId = packId
        )

        assertEquals(null, fakeMastery.getForPack(packId, "small")!!.completedAtMs)
        assertEquals(true, fakeMastery.getForPack(packId, "big")!!.completedAtMs != null)
    }

    @Test
    fun `hidden cards lower the effective threshold`() {
        val fakeMastery = FakeMasteryStore()
        val packId = "HIDDEN_PACK"
        val cards = (1..10).map { SentenceCard("c$it", "p$it", listOf("a$it")) }
        val lesson = Lesson(LessonId("l"), LanguageId("it"), "L", cards)
        // 2 of 10 cards hidden → threshold 8; 8 shows is enough.
        fakeMastery.saveForPack(mastery("l", shows = 8), packId)

        tracker(fakeMastery).recalculateCompletionsExcludingHidden(
            lessons = listOf(lesson),
            languageId = LanguageId("it"),
            hiddenCardIds = setOf("c1", "c2"),
            packId = packId
        )

        assertEquals(true, fakeMastery.getForPack(packId, "l")!!.completedAtMs != null)
    }

    @Test
    fun `chapter progress is pack-scoped for the same chapterId`() {
        val ch = chapter("l1")
        val storeA = FakeMasteryStore()
        val storeB = FakeMasteryStore()
        storeA.saveForPack(mastery("l1", shows = 10, completedAtMs = 10L), "PACK_A")
        storeB.saveForPack(mastery("l1", shows = 10), "PACK_B")

        val progressA = calculator.calculateChapterProgress(ch, mapOf("l1" to storeA.getForPack("PACK_A", "l1")!!))
        val progressB = calculator.calculateChapterProgress(ch, mapOf("l1" to storeB.getForPack("PACK_B", "l1")!!))

        assertEquals(1, progressA.lessonsCompleted)
        assertEquals(0, progressB.lessonsCompleted)
    }

    @Test
    fun `WORD_BANK exclusion has a single predicate`() {
        assertTrue(InputMode.VOICE.countsTowardMastery)
        assertTrue(InputMode.KEYBOARD.countsTowardMastery)
        assertFalse(InputMode.WORD_BANK.countsTowardMastery)
    }

    private fun tracker(store: FakeMasteryStore) = ProgressTracker(
        stateAccess = FakeTrainingStateAccess(),
        masteryStore = store,
        progressStore = io.mockk.mockk(relaxed = true),
        lessonStore = io.mockk.mockk(relaxed = true),
        packDailyCursorStore = io.mockk.mockk(relaxed = true),
        packLessonProgressStore = io.mockk.mockk(relaxed = true)
    )
}
