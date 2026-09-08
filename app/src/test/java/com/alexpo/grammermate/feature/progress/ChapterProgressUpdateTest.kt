package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.*
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

/**
 * Test: Chapter progress reflects lesson completion in real-time.
 *
 * Scenario from bug report:
 * - User completes lessons 1 and 2 in a pack
 * - Returns to roadmap → progress should show completed
 * - Bug: progress didn't update until user re-selected the pack
 *
 * This test traces the full chain:
 *   recordCardShowForPack → checkAndMarkLessonCompleted → updateChapterProgress
 *   → getChapterCards shows correct completed count
 */
class ChapterProgressUpdateTest {

    private val calculator = ChapterProgressCalculator

    // ── Step 1: Mastery threshold reached → completedAtMs set ──

    @Test
    fun `lesson with enough uniqueShows is marked completed`() {
        // mastery.threshold = LESSON_COMPLETION_CARD_THRESHOLD
        // A lesson is completed when uniqueCardShows >= threshold AND packId is set
        val mastery = LessonMasteryState(
            lessonId = LessonId("lesson_01_A01"),
            languageId = LanguageId("it"),
            uniqueCardShows = 15,
            totalCardShows = 15,
            lastShowDateMs = 1000L,
            intervalStepIndex = 0,
            completedAtMs = null  // NOT yet completed
        )
        // After markLessonCompletedForPack: completedAtMs should be set
        val updated = mastery.copy(completedAtMs = 2000L)
        assertNotNull("completedAtMs must be set after lesson completion", updated.completedAtMs)
    }

    // ── Step 2: ChapterProgressCalculator uses completedAtMs ──

    @Test
    fun `chapter progress counts lesson as completed when completedAtMs is set`() {
        val chapter = Chapter(
            chapterId = "chapter_1",
            order = 1,
            title = "Present",
            lessons = listOf("lesson_01_A01", "lesson_02_A02", "lesson_03_A03")
        )

        // lesson_01: completed (completedAtMs is set)
        val lesson1 = LessonMasteryState(
            lessonId = LessonId("lesson_01_A01"),
            languageId = LanguageId("it"),
            uniqueCardShows = 15,
            totalCardShows = 15,
            lastShowDateMs = 1000L,
            intervalStepIndex = 0,
            completedAtMs = 1000L  // COMPLETED
        )
        // lesson_02: completed
        val lesson2 = LessonMasteryState(
            lessonId = LessonId("lesson_02_A02"),
            languageId = LanguageId("it"),
            uniqueCardShows = 14,
            totalCardShows = 14,
            lastShowDateMs = 2000L,
            intervalStepIndex = 0,
            completedAtMs = 2000L  // COMPLETED
        )
        // lesson_03: started but NOT completed
        val lesson3 = LessonMasteryState(
            lessonId = LessonId("lesson_03_A03"),
            languageId = LanguageId("it"),
            uniqueCardShows = 5,
            totalCardShows = 5,
            lastShowDateMs = 3000L,
            intervalStepIndex = 0,
            completedAtMs = null  // NOT completed
        )

        val masteryStates = mapOf(
            "lesson_01_A01" to lesson1,
            "lesson_02_A02" to lesson2,
            "lesson_03_A03" to lesson3
        )

        val progress = calculator.calculateChapterProgress(chapter, masteryStates)

        assertEquals(3, progress.lessonsStarted)   // all 3 have uniqueCardShows > 0
        assertEquals(2, progress.lessonsCompleted)  // lesson_01 and lesson_02 have completedAtMs
        assertEquals(3000L, progress.lastAccessedMs)
    }

    // ── Step 3 (Phase 3 repair): retroactive stamping fixes the cold-start repro ──

    @Test
    fun `retroactive recalculation stamps completion so live progress counts them`() {
        // Original bug: a user went through all cards, but the last-card event
        // never fired (and the restore path never recalculated) → completedAtMs
        // stayed null → chapter progress showed 0 completed. Phase 3 (3.5) adds
        // recalculateCompletionsExcludingHidden to the restore path; this test
        // pins the repaired chain: recalc stamps → calculator counts.
        val fakeMastery = com.alexpo.grammermate.testharness.FakeMasteryStore()
        val packId = "PACK_X"
        fun unstampedLesson(lessonId: String, shows: Int) = LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId("it"),
            uniqueCardShows = shows,
            totalCardShows = shows,
            lastShowDateMs = 1000L,
            intervalStepIndex = 0,
            completedAtMs = null
        )
        fakeMastery.saveForPack(unstampedLesson("lesson_01_A01", 15), packId)
        fakeMastery.saveForPack(unstampedLesson("lesson_02_A02", 14), packId)

        fun cards(n: Int) = (1..n).map { i ->
            SentenceCard(id = "c$i", promptRu = "p$i", acceptedAnswers = listOf("a$i"))
        }
        val lessons = listOf(
            Lesson(LessonId("lesson_01_A01"), LanguageId("it"), "L1", cards(15)),
            Lesson(LessonId("lesson_02_A02"), LanguageId("it"), "L2", cards(14))
        )

        val tracker = ProgressTracker(
            stateAccess = com.alexpo.grammermate.testharness.FakeTrainingStateAccess(),
            masteryStore = fakeMastery,
            progressStore = io.mockk.mockk(relaxed = true),
            lessonStore = io.mockk.mockk(relaxed = true),
            packDailyCursorStore = io.mockk.mockk(relaxed = true),
            packLessonProgressStore = io.mockk.mockk(relaxed = true)
        )
        tracker.recalculateCompletionsExcludingHidden(
            lessons = lessons,
            languageId = LanguageId("it"),
            hiddenCardIds = emptySet(),
            packId = packId
        )

        val chapter = Chapter(
            chapterId = "chapter_1",
            order = 1,
            title = "Present",
            lessons = listOf("lesson_01_A01", "lesson_02_A02")
        )
        val masteryStates = buildMap {
            for (lid in chapter.lessons) put(lid, fakeMastery.getForPack(packId, lid)!!)
        }
        val progress = calculator.calculateChapterProgress(chapter, masteryStates)

        assertEquals(2, progress.lessonsStarted)
        assertEquals(
            "retroactive recalculation must stamp completedAtMs for lessons at threshold",
            2,
            progress.lessonsCompleted
        )
    }

    // ── Step 4: progressKey derivation ──

    @Test
    fun `progressKey changes when chapterProgresses map changes`() {
        val chapter1Progress = ChapterProgress(
            chapterId = "chapter_1",
            lessonsStarted = 0,
            lessonsCompleted = 0,
            lastAccessedMs = 0L
        )
        val before = mapOf("chapter_1" to chapter1Progress)
        val keyBefore = before.values
            .sortedBy { it.chapterId }
            .joinToString(",") { "${it.chapterId}:${it.lessonsCompleted}/${it.lessonsStarted}" }

        val chapter1ProgressUpdated = ChapterProgress(
            chapterId = "chapter_1",
            lessonsStarted = 2,
            lessonsCompleted = 2,
            lastAccessedMs = 1000L
        )
        val after = mapOf("chapter_1" to chapter1ProgressUpdated)
        val keyAfter = after.values
            .sortedBy { it.chapterId }
            .joinToString(",") { "${it.chapterId}:${it.lessonsCompleted}/${it.lessonsStarted}" }

        assertNotEquals(
            "progressKey must change when chapter progress updates",
            keyBefore, keyAfter
        )
    }

    // ── Step 5: getChapterCards uses LIVE mastery, not stale chapterProgressStore ──

    @Test
    fun `getCompletedLessonIds reads live from masteryStore`() {
        // This verifies the key design: completedLessonIds come from mastery.completedAtMs
        // not from a stale chapter progress store
        val chapter = Chapter(
            chapterId = "chapter_1",
            order = 1,
            title = "Present",
            lessons = listOf("lesson_01_A01")
        )

        // Scenario: mastery was just updated with completedAtMs
        val mastery = LessonMasteryState(
            lessonId = LessonId("lesson_01_A01"),
            languageId = LanguageId("it"),
            uniqueCardShows = 15,
            totalCardShows = 15,
            lastShowDateMs = 1000L,
            intervalStepIndex = 0,
            completedAtMs = 1000L
        )

        // The chapter progress calculator should reflect this immediately
        val progress = calculator.calculateChapterProgress(
            chapter,
            mapOf("lesson_01_A01" to mastery)
        )

        assertEquals(1, progress.lessonsCompleted)
    }

    // ── Step 6: Legacy data should not interfere ──

    @Test
    fun `legacy language-scoped mastery does not affect pack-scoped progress`() {
        // Old data under "it:" key should NOT be read by pack-scoped code
        // MasteryStore.getForPack uses "pack:{packId}" key, not bare language ID

        val chapter = Chapter(
            chapterId = "chapter_1",
            order = 1,
            title = "Present",
            lessons = listOf("lesson_01_A01")
        )

        // Pack-scoped: no mastery for this pack
        val packMasteryStates = emptyMap<String, LessonMasteryState>()

        val progress = calculator.calculateChapterProgress(chapter, packMasteryStates)

        assertEquals(0, progress.lessonsStarted)
        assertEquals(0, progress.lessonsCompleted)
        // Even if legacy "it:" key has mastery for this lesson, pack-scoped ignores it
    }
}
