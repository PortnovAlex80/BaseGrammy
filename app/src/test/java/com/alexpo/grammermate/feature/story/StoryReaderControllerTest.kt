package com.alexpo.grammermate.feature.story

import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.audio.AudioCoordinator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoryReaderControllerTest {
    private val lessons = mockk<LessonStore>()
    private val audio = mockk<AudioCoordinator>(relaxed = true)
    private val state = MutableStateFlow(TrainingUiState(navigation = NavigationState(
        activePackId = PackId("A"), uiLanguage = "ru"
    )))
    private val controller = StoryReaderController(lessons, audio, state, "StoryReaderTest")

    @Test
    fun readerStateAndNarrationUseOneController() {
        controller.open("Chapter", "Content", "chapter_03.md")
        assertEquals("Chapter", state.value.storyReaderChapterTitle)
        assertEquals("chapter_03.md", state.value.storyReaderStoryFile)
        controller.speakMultilingual("Content", "ru", "chapter_03.md", "A")
        verify(exactly = 1) { audio.playMultilingualStory("Content", "ru", "chapter_03.md", "A") }

        controller.clear()
        assertNull(state.value.storyReaderChapterTitle)
        assertNull(state.value.storyReaderStoryFile)
        assertEquals("", state.value.storyReaderContent)
    }

    @Test
    fun storyLookupUsesActivePackAndUiLanguage() {
        every { lessons.detectStoryLanguage("A", "chapter_03", "ru") } returns "chapter_03_original.md"
        every { lessons.getChapterStory("A", "chapter_03_original.md") } returns "Русский текст"

        assertEquals("Русский текст", controller.loadContent("chapter_03.md"))
        assertNull(controller.loadContent(null))
        verify(exactly = 1) { lessons.getChapterStory("A", "chapter_03_original.md") }
    }
}
