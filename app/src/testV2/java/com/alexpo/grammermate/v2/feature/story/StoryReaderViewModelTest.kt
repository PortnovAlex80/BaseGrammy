package com.alexpo.grammermate.v2.feature.story

import androidx.lifecycle.SavedStateHandle
import com.alexpo.grammermate.domain.model.Chapter
import com.alexpo.grammermate.domain.model.ChapterId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * StoryReaderViewModel (срез 6 Фазы 4, шаг 3b): резолв storyFile по chapterId,
 * чтение портом, снятие маркеров/пауз доменным парсером, абзацы.
 */
class StoryReaderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val packId = PackId("TEST_PACK")

    private fun chapter(storyFile: String?) = Chapter(
        id = ChapterId("chapter_1"),
        packId = packId,
        order = 1,
        title = "Глава 1",
        subtitle = "Настоящее",
        storyFile = storyFile,
        lessonIds = emptyList(),
    )

    private fun viewModel(storyText: String?): StoryReaderViewModel {
        val repo = mockk<ContentRepository> {
            coEvery { getChapters(packId) } returns listOf(chapter(storyFile = "stories/it/chapter_1.md"))
            coEvery { getStoryText(packId, "stories/it/chapter_1.md") } returns storyText
        }
        return StoryReaderViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("packId" to packId.value, "chapterId" to "chapter_1")
            ),
            contentRepository = repo,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `story text loads with markers stripped and split to paragraphs`() {
        val vm = viewModel(
            "# Заголовок\n\n{it}Ciao,{/it} disse Maria. {pause:250} " +
                "{ru}Привет,{/ru} сказала Мария.\n\nВторая глава пути."
        )

        val state = vm.state.value as StoryReaderViewState.Content
        assertThat(state.title).isEqualTo("Глава 1")
        // Заголовок Markdown остаётся абзацем, но без '#'.
        assertThat(state.paragraphs).containsExactly(
            "Заголовок",
            "Ciao, disse Maria.  Привет, сказала Мария.",
            "Вторая глава пути.",
        ).inOrder()
        assertThat(state.paragraphs.none { it.contains("#") }).isTrue()
    }

    @Test
    fun `missing story file is explicit Error not silent content`() {
        val vm = viewModel(storyText = null)

        val state = vm.state.value as StoryReaderViewState.Error
        assertThat(state.message).contains("нет истории")
    }

    @Test
    fun `unknown chapter is Error`() {
        val repo = mockk<ContentRepository> {
            coEvery { getChapters(packId) } returns emptyList()
        }
        val vm = StoryReaderViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("packId" to packId.value, "chapterId" to "ghost")
            ),
            contentRepository = repo,
        )

        assertThat(vm.state.value).isInstanceOf(StoryReaderViewState.Error::class.java)
    }

    @Test
    fun `blank route args is Error`() {
        val vm = StoryReaderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("packId" to "", "chapterId" to "")),
            contentRepository = mockk(relaxed = true),
        )

        assertThat(vm.state.value).isInstanceOf(StoryReaderViewState.Error::class.java)
    }
}
