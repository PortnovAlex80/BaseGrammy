package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.ChapterStatus
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.testharness.FakeMasteryStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterRepositoryTest {
    private val lessons = mockk<LessonStore>()
    private val mastery = FakeMasteryStore()
    private val repository = ChapterRepository(lessons, mastery)
    private val first = Chapter("first", 0, "First", lessons = listOf("l1", "l2"))
    private val second = Chapter("second", 1, "Second", lessons = listOf("l3"))

    @Test
    fun sameLessonIdInDifferentPacksKeepsCompletionAndCursorSeparate() {
        every { lessons.getLessonIdsForPack("A") } returns listOf("l1", "l2", "l3")
        every { lessons.getLessonIdsForPack("B") } returns listOf("l1", "l2", "l3")
        mastery.saveForPack(LessonMasteryState(
            LessonId("l1"), LanguageId("it"), uniqueCardShows = 10, completedAtMs = 100L
        ), "A")
        mastery.saveForPack(LessonMasteryState(
            LessonId("l1"), LanguageId("it"), uniqueCardShows = 10, intervalStepIndex = 9
        ), "B")

        assertEquals(setOf("l1"), repository.getCompletedLessonIds(first, "A"))
        assertEquals(emptySet<String>(), repository.getCompletedLessonIds(first, "B"))
        assertEquals("l2", repository.getFirstIncompleteLesson(first, "A", "it"))
        assertEquals("l1", repository.getFirstIncompleteLesson(first, "B", "it"))
        assertEquals(1, repository.computeAllChapterProgress(listOf(first), "A", "it")["first"]?.lessonsCompleted)
        assertEquals(0, repository.computeAllChapterProgress(listOf(first), "B", "it")["first"]?.lessonsCompleted)
        assertEquals(listOf("first"), repository.getAffectedChapterProgresses(
            listOf(first, second), "l1", "A", "it"
        ).map { it.chapterId })
    }

    @Test
    fun activeChapterUsesFirstIncompleteOrLastWhenAllComplete() {
        val chapters = listOf(first, second)
        val none = emptyMap<String, com.alexpo.grammermate.data.ChapterProgress>()
        assertEquals("first", repository.determineActiveChapter(chapters, none))
        val completedFirst = com.alexpo.grammermate.data.ChapterProgress("first", 2, 2, totalLessons = 2, lastAccessedMs = 1L)
        assertEquals("second", repository.determineActiveChapter(chapters, mapOf("first" to completedFirst)))
        val completedSecond = com.alexpo.grammermate.data.ChapterProgress("second", 1, 1, totalLessons = 1, lastAccessedMs = 1L)
        assertEquals("second", repository.determineActiveChapter(
            chapters, mapOf("first" to completedFirst, "second" to completedSecond)
        ))
        assertEquals(null, repository.determineActiveChapter(emptyList(), none))
    }

    @Test
    fun storyOnlyChapterRemainsAccessible() {
        val story = Chapter("story", 0, "Story", lessons = emptyList())
        val progress = com.alexpo.grammermate.data.ChapterProgress.forChapter("story")
        assertEquals(ChapterStatus.ACTIVE, repository.calculateChapterStatus(story, progress, 0))
    }
}
