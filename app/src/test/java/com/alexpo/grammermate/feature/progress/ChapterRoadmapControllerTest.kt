package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.testharness.FakeMasteryStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChapterRoadmapControllerTest {
    private val lessons = mockk<LessonStore>()
    private val mastery = FakeMasteryStore()
    private val chapter = Chapter("first", 0, "First", lessons = listOf("l1"))
    private val state = MutableStateFlow(TrainingUiState(navigation = NavigationState(
        selectedLanguageId = LanguageId("it"), activePackId = PackId("A")
    )))
    private val controller = ChapterRoadmapController(
        lessons, ChapterRepository(lessons, mastery), state, "ChapterRoadmapTest"
    )

    @Test
    fun packRemovalClearsCoreAndPublishedRoadmap() {
        every { lessons.hasChapters("A") } returns true
        every { lessons.getChapters("A") } returns listOf(chapter)
        every { lessons.getLessonIdsForPack("A") } returns listOf("l1")

        controller.loadChapters()
        assertEquals("first", state.value.activeChapterId)
        assertEquals(listOf("first"), controller.chapterCards.value.map { it.chapter.chapterId })

        state.update { it.copy(navigation = it.navigation.copy(activePackId = null)) }
        controller.loadChapters()
        assertEquals(null, state.value.activeChapterId)
        assertTrue(state.value.chapters.isEmpty())
        assertTrue(state.value.chapterProgresses.isEmpty())
        assertTrue(controller.chapterCards.value.isEmpty())
    }

    @Test
    fun masteryChangeRefreshesAffectedProgressAndCards() {
        every { lessons.hasChapters("A") } returns true
        every { lessons.getChapters("A") } returns listOf(chapter)
        every { lessons.getLessonIdsForPack("A") } returns listOf("l1")

        controller.loadChapters()
        assertEquals(0, state.value.chapterProgresses["first"]?.lessonsCompleted)
        mastery.saveForPack(LessonMasteryState(
            LessonId("l1"), LanguageId("it"), uniqueCardShows = 1, completedAtMs = 10L
        ), "A")

        controller.updateChapterProgress("l1")
        assertEquals(1, state.value.chapterProgresses["first"]?.lessonsCompleted)
        assertEquals(1, controller.chapterCards.value.single().progress.lessonsCompleted)
    }
}
