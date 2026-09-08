package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.ChapterProgress
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.feature.progress.ChapterProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test

/**
 * Integration test for chapter progress in TrainingViewModel.
 * Tests the Fix 9 implementation: chapter progress updates on lesson mastery changes.
 */
class TrainingViewModelChapterIntegrationTest {

    @Test
    fun `test chapter progress calculation with lesson mastery states`() {
        // Given: A chapter with 3 lessons
        val chapter = Chapter(
            chapterId = "chapter_1",
            order = 1,
            title = "Basics",
            lessons = listOf("lesson_1", "lesson_2", "lesson_3")
        )

        // And: Mastery states where lesson_1 is completed, lesson_2 is started, lesson_3 is not started
        val masteryStates = mapOf(
            "lesson_1" to LessonMasteryState(
                lessonId = LessonId("lesson_1"),
                languageId = LanguageId("it"),
                uniqueCardShows = 10,
                intervalStepIndex = 5,
                lastShowDateMs = 1000L,
                completedAtMs = 1000L  // Phase 3 rule: completion = completedAtMs
            ),
            "lesson_2" to LessonMasteryState(
                lessonId = LessonId("lesson_2"),
                languageId = LanguageId("it"),
                uniqueCardShows = 5,
                intervalStepIndex = 2,
                lastShowDateMs = 2000L
            )
            // lesson_3 has no mastery state (not started)
        )

        // When: Calculate chapter progress
        val progress = ChapterProgressCalculator.calculateChapterProgress(chapter, masteryStates)

        // Then: Progress should reflect 2 started lessons and 1 completed lesson
        assertEquals("chapter_1", progress.chapterId)
        assertEquals(2, progress.lessonsStarted) // lesson_1 and lesson_2
        assertEquals(1, progress.lessonsCompleted) // only lesson_1
        assertEquals(2000L, progress.lastAccessedMs) // most recent activity
    }

    @Test
    fun `test chapter progress with empty chapter`() {
        // Given: An empty chapter
        val chapter = Chapter(
            chapterId = "empty_chapter",
            order = 1,
            title = "Empty",
            lessons = emptyList()
        )

        // When: Calculate progress
        val progress = ChapterProgressCalculator.calculateChapterProgress(
            chapter,
            emptyMap()
        )

        // Then: All values should be zero
        assertEquals("empty_chapter", progress.chapterId)
        assertEquals(0, progress.lessonsStarted)
        assertEquals(0, progress.lessonsCompleted)
        assertEquals(0L, progress.lastAccessedMs)
    }

    @Test
    fun `test chapter progress with all lessons completed`() {
        // Given: A chapter where all lessons are completed
        val chapter = Chapter(
            chapterId = "completed_chapter",
            order = 1,
            title = "Completed",
            lessons = listOf("lesson_1", "lesson_2")
        )

        val masteryStates = mapOf(
            "lesson_1" to LessonMasteryState(
                lessonId = LessonId("lesson_1"),
                languageId = LanguageId("it"),
                uniqueCardShows = 10,
                intervalStepIndex = 5,
                lastShowDateMs = 1000L,
                completedAtMs = 1000L  // Phase 3 rule: completion = completedAtMs
            ),
            "lesson_2" to LessonMasteryState(
                lessonId = LessonId("lesson_2"),
                languageId = LanguageId("it"),
                uniqueCardShows = 15,
                intervalStepIndex = 8,
                lastShowDateMs = 2000L,
                completedAtMs = 2000L  // Phase 3 rule: completion = completedAtMs
            )
        )

        // When: Calculate progress
        val progress = ChapterProgressCalculator.calculateChapterProgress(chapter, masteryStates)

        // Then: All lessons should be marked as completed
        assertEquals(2, progress.lessonsStarted)
        assertEquals(2, progress.lessonsCompleted)
        assertEquals(2000L, progress.lastAccessedMs)
    }

    @Test
    fun `test chapter progress with no mastery data`() {
        // Given: A chapter with lessons but no mastery data
        val chapter = Chapter(
            chapterId = "new_chapter",
            order = 1,
            title = "New",
            lessons = listOf("lesson_1", "lesson_2")
        )

        // When: Calculate progress with empty mastery states
        val progress = ChapterProgressCalculator.calculateChapterProgress(
            chapter,
            emptyMap()
        )

        // Then: No lessons should be started or completed
        assertEquals(0, progress.lessonsStarted)
        assertEquals(0, progress.lessonsCompleted)
        assertEquals(0L, progress.lastAccessedMs)
    }

    @Test
    fun `test chapter progress companion object`() {
        // Given: A chapter ID
        val chapterId = "test_chapter"

        // When: Create progress using companion object
        val progress = ChapterProgress.forChapter(chapterId)

        // Then: Should return progress with zero values
        assertEquals(chapterId, progress.chapterId)
        assertEquals(0, progress.lessonsStarted)
        assertEquals(0, progress.lessonsCompleted)
        assertEquals(0L, progress.lastAccessedMs)
    }

    @Test
    fun `test chapter progress validation allows valid state`() {
        // Given: Valid chapter progress
        val progress = ChapterProgress(
            chapterId = "valid",
            lessonsStarted = 5,
            lessonsCompleted = 3,
            lastAccessedMs = 1000L
        )

        // Then: Should not throw exception (started >= completed)
        assertEquals(5, progress.lessonsStarted)
        assertEquals(3, progress.lessonsCompleted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test chapter progress validation rejects negative started`() {
        // Given: Invalid progress with negative started count
        ChapterProgress(
            chapterId = "invalid",
            lessonsStarted = -1,
            lessonsCompleted = 0,
            lastAccessedMs = 0L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test chapter progress validation rejects completed greater than started`() {
        // Given: Invalid progress where completed > started
        ChapterProgress(
            chapterId = "invalid",
            lessonsStarted = 2,
            lessonsCompleted = 5, // More completed than started - invalid!
            lastAccessedMs = 0L
        )
    }
}
