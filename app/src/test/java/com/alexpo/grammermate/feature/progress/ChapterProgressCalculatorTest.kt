package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.ChapterProgress
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verification tests for ChapterProgressCalculator.
 *
 * Tests the calculation logic for chapter progress metrics:
 * - Empty chapter handling
 * - Single lesson chapters
 * - Multiple lesson chapters
 * - Independent progress across chapters
 * - Mastery state transitions
 */
class ChapterProgressCalculatorTest {

    @Test
    fun `calculateChapterProgress returns zero progress for empty chapter`() {
        // Arrange: Chapter with no lessons
        val chapter = Chapter(
            chapterId = "chapter_0",
            order = 0,
            title = "Empty Chapter",
            lessons = emptyList()
        )
        val masteryStates = emptyMap<String, LessonMasteryState>()

        // Act
        val result = ChapterProgressCalculator.calculateChapterProgress(chapter, masteryStates)

        // Assert
        assertEquals("chapter_0", result.chapterId)
        assertEquals(0, result.lessonsStarted)
        assertEquals(0, result.lessonsCompleted)
        assertEquals(0L, result.lastAccessedMs)
    }

    @Test
    fun `calculateChapterProgress works correctly for single lesson chapter`() {
        // Arrange: Chapter with one lesson that has progress
        val chapter = Chapter(
            chapterId = "chapter_1",
            order = 1,
            title = "Single Lesson Chapter",
            lessons = listOf("lesson_01")
        )
        val masteryState = LessonMasteryState(
            lessonId = LessonId("lesson_01"),
            languageId = LanguageId("en"),
            uniqueCardShows = 50,
            totalCardShows = 60,
            lastShowDateMs = 1000L,
            intervalStepIndex = 2
        )
        val masteryStates = mapOf("lesson_01" to masteryState)

        // Act
        val result = ChapterProgressCalculator.calculateChapterProgress(chapter, masteryStates)

        // Assert
        assertEquals("chapter_1", result.chapterId)
        assertEquals(1, result.lessonsStarted)  // uniqueCardShows > 0
        assertEquals(0, result.lessonsCompleted)  // intervalStepIndex < 3
        assertEquals(1000L, result.lastAccessedMs)
    }

    @Test
    fun `calculateChapterProgress counts completed lessons correctly`() {
        // Arrange: Chapter with mixed lesson states
        val chapter = Chapter(
            chapterId = "chapter_2",
            order = 2,
            title = "Mixed Progress Chapter",
            lessons = listOf("lesson_01", "lesson_02", "lesson_03")
        )
        val lesson1 = LessonMasteryState(
            lessonId = LessonId("lesson_01"),
            languageId = LanguageId("en"),
            uniqueCardShows = 10,
            totalCardShows = 15,
            lastShowDateMs = 1000L,
            intervalStepIndex = 1  // Not completed
        )
        val lesson2 = LessonMasteryState(
            lessonId = LessonId("lesson_02"),
            languageId = LanguageId("en"),
            uniqueCardShows = 80,
            totalCardShows = 100,
            lastShowDateMs = 2000L,
            intervalStepIndex = 4  // Completed (>= 3)
        )
        val lesson3 = LessonMasteryState(
            lessonId = LessonId("lesson_03"),
            languageId = LanguageId("en"),
            uniqueCardShows = 0,  // Not started
            totalCardShows = 0,
            lastShowDateMs = 0L,
            intervalStepIndex = 0
        )
        val masteryStates = mapOf(
            "lesson_01" to lesson1,
            "lesson_02" to lesson2,
            "lesson_03" to lesson3
        )

        // Act
        val result = ChapterProgressCalculator.calculateChapterProgress(chapter, masteryStates)

        // Assert
        assertEquals("chapter_2", result.chapterId)
        assertEquals(2, result.lessonsStarted)  // lesson_01 and lesson_02 started
        assertEquals(1, result.lessonsCompleted)  // Only lesson_02 completed
        assertEquals(2000L, result.lastAccessedMs)  // Max of all lastShowDateMs
    }

    @Test
    fun `calculateChapterProgress is independent across chapters`() {
        // Arrange: Two different chapters with different lessons
        val chapter1 = Chapter(
            chapterId = "chapter_1",
            order = 1,
            title = "Chapter One",
            lessons = listOf("lesson_01", "lesson_02")
        )
        val chapter2 = Chapter(
            chapterId = "chapter_2",
            order = 2,
            title = "Chapter Two",
            lessons = listOf("lesson_03", "lesson_04")
        )

        // Lesson in Chapter 1 has progress
        val lesson1 = LessonMasteryState(
            lessonId = LessonId("lesson_01"),
            languageId = LanguageId("en"),
            uniqueCardShows = 50,
            totalCardShows = 60,
            lastShowDateMs = 1000L,
            intervalStepIndex = 3  // Completed
        )
        val lesson2 = LessonMasteryState(
            lessonId = LessonId("lesson_02"),
            languageId = LanguageId("en"),
            uniqueCardShows = 0,
            totalCardShows = 0,
            lastShowDateMs = 0L,
            intervalStepIndex = 0
        )
        // Lessons in Chapter 2 have no progress
        val lesson3 = LessonMasteryState(
            lessonId = LessonId("lesson_03"),
            languageId = LanguageId("en"),
            uniqueCardShows = 0,
            totalCardShows = 0,
            lastShowDateMs = 0L,
            intervalStepIndex = 0
        )
        val lesson4 = LessonMasteryState(
            lessonId = LessonId("lesson_04"),
            languageId = LanguageId("en"),
            uniqueCardShows = 0,
            totalCardShows = 0,
            lastShowDateMs = 0L,
            intervalStepIndex = 0
        )

        val masteryStates = mapOf(
            "lesson_01" to lesson1,
            "lesson_02" to lesson2,
            "lesson_03" to lesson3,
            "lesson_04" to lesson4
        )

        // Act
        val result1 = ChapterProgressCalculator.calculateChapterProgress(chapter1, masteryStates)
        val result2 = ChapterProgressCalculator.calculateChapterProgress(chapter2, masteryStates)

        // Assert: Chapter 1 has progress, Chapter 2 doesn't
        assertEquals(1, result1.lessonsStarted)
        assertEquals(1, result1.lessonsCompleted)
        assertEquals(1000L, result1.lastAccessedMs)

        assertEquals(0, result2.lessonsStarted)
        assertEquals(0, result2.lessonsCompleted)
        assertEquals(0L, result2.lastAccessedMs)
    }

    @Test
    fun `calculateChapterProgress handles missing mastery states gracefully`() {
        // Arrange: Chapter with lessons, some mastery states missing
        val chapter = Chapter(
            chapterId = "chapter_1",
            order = 1,
            title = "Chapter with Missing Data",
            lessons = listOf("lesson_01", "lesson_02", "lesson_03")
        )
        // Only lesson_01 has mastery data
        val lesson1 = LessonMasteryState(
            lessonId = LessonId("lesson_01"),
            languageId = LanguageId("en"),
            uniqueCardShows = 30,
            totalCardShows = 40,
            lastShowDateMs = 1000L,
            intervalStepIndex = 2
        )
        val masteryStates = mapOf("lesson_01" to lesson1)

        // Act
        val result = ChapterProgressCalculator.calculateChapterProgress(chapter, masteryStates)

        // Assert: Missing lessons treated as not started
        assertEquals(1, result.lessonsStarted)  // Only lesson_01
        assertEquals(0, result.lessonsCompleted)  // lesson_01 not at step 3 yet
        assertEquals(1000L, result.lastAccessedMs)
    }

    @Test
    fun `calculateChapterProgress uses max lastShowDateMs for lastAccessedMs`() {
        // Arrange: Chapter with multiple lessons accessed at different times
        val chapter = Chapter(
            chapterId = "chapter_1",
            order = 1,
            title = "Chapter with Multiple Access Times",
            lessons = listOf("lesson_01", "lesson_02", "lesson_03")
        )
        val lesson1 = LessonMasteryState(
            lessonId = LessonId("lesson_01"),
            languageId = LanguageId("en"),
            uniqueCardShows = 10,
            totalCardShows = 15,
            lastShowDateMs = 1000L,
            intervalStepIndex = 1
        )
        val lesson2 = LessonMasteryState(
            lessonId = LessonId("lesson_02"),
            languageId = LanguageId("en"),
            uniqueCardShows = 20,
            totalCardShows = 25,
            lastShowDateMs = 3000L,  // Most recent
            intervalStepIndex = 2
        )
        val lesson3 = LessonMasteryState(
            lessonId = LessonId("lesson_03"),
            languageId = LanguageId("en"),
            uniqueCardShows = 5,
            totalCardShows = 10,
            lastShowDateMs = 2000L,
            intervalStepIndex = 0
        )
        val masteryStates = mapOf(
            "lesson_01" to lesson1,
            "lesson_02" to lesson2,
            "lesson_03" to lesson3
        )

        // Act
        val result = ChapterProgressCalculator.calculateChapterProgress(chapter, masteryStates)

        // Assert: lastAccessedMs is the maximum
        assertEquals(3000L, result.lastAccessedMs)
        assertEquals(3, result.lessonsStarted)
        assertEquals(0, result.lessonsCompleted)
    }

    @Test
    fun calculateChapterProgress_maintainsStartedGeqCompleted_invariant() {
        // Arrange: Chapter with various lesson states
        val chapter = Chapter(
            chapterId = "chapter_1",
            order = 1,
            title = "Invariant Test Chapter",
            lessons = listOf("lesson_01", "lesson_02", "lesson_03", "lesson_04", "lesson_05")
        )
        val masteryStates = mapOf(
            "lesson_01" to LessonMasteryState(
                lessonId = LessonId("lesson_01"),
                languageId = LanguageId("en"),
                uniqueCardShows = 0,
                totalCardShows = 0,
                lastShowDateMs = 0L,
                intervalStepIndex = 0
            ),
            "lesson_02" to LessonMasteryState(
                lessonId = LessonId("lesson_02"),
                languageId = LanguageId("en"),
                uniqueCardShows = 10,
                totalCardShows = 15,
                lastShowDateMs = 1000L,
                intervalStepIndex = 1
            ),
            "lesson_03" to LessonMasteryState(
                lessonId = LessonId("lesson_03"),
                languageId = LanguageId("en"),
                uniqueCardShows = 80,
                totalCardShows = 100,
                lastShowDateMs = 2000L,
                intervalStepIndex = 3  // Completed
            ),
            "lesson_04" to LessonMasteryState(
                lessonId = LessonId("lesson_04"),
                languageId = LanguageId("en"),
                uniqueCardShows = 100,
                totalCardShows = 120,
                lastShowDateMs = 3000L,
                intervalStepIndex = 5  // Completed
            ),
            "lesson_05" to LessonMasteryState(
                lessonId = LessonId("lesson_05"),
                languageId = LanguageId("en"),
                uniqueCardShows = 50,
                totalCardShows = 60,
                lastShowDateMs = 4000L,
                intervalStepIndex = 2
            )
        )

        // Act
        val result = ChapterProgressCalculator.calculateChapterProgress(chapter, masteryStates)

        // Assert: Verify invariant holds
        assertTrue(
            "lessonsStarted ($result.lessonsStarted) must be >= lessonsCompleted (${result.lessonsCompleted})",
            result.lessonsStarted >= result.lessonsCompleted
        )
        assertEquals(4, result.lessonsStarted)  // All except lesson_01
        assertEquals(2, result.lessonsCompleted)  // lesson_03 and lesson_04
    }
}
