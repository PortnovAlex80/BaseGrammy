package com.alexpo.grammermate.v2.feature.packcontent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.domain.model.Chapter
import com.alexpo.grammermate.domain.model.ChapterId
import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.Lesson
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.Pack
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.v2.ui.theme.GrammarMateTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Clickable-путь Pack → Chapter → Lesson (Фаза 3 плана: путь golden journey
 * после Home; клик по уроку обязан нести РЕАЛЬНЫЙ lessonId — регрессия P0
 * «Home передаёт packId как lessonId»).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PackContentScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val packId = PackId("ITALIAN_SHORT")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        pack: Pack? = Pack(packId, LanguageId("it"), "Итальянский экспресс", "1", 0L),
        chapters: List<Chapter> = listOf(
            Chapter(
                id = ChapterId("ch1"),
                packId = packId,
                order = 0,
                title = "Глава 1",
                subtitle = null,
                storyFile = null,
                lessonIds = listOf(LessonId("lesson_01"), LessonId("lesson_02")),
            ),
        ),
        lessons: List<Lesson> = listOf(
            Lesson(LessonId("lesson_01"), packId, ChapterId("ch1"), order = 0, title = "Приветствие", cefrLevel = "A1", grammarChipKey = null, cards = emptyList()),
            Lesson(LessonId("lesson_02"), packId, ChapterId("ch1"), order = 1, title = "Знакомство", cefrLevel = "A1", grammarChipKey = null, cards = emptyList()),
        ),
    ): PackContentViewModel {
        val repo = mockk<ContentRepository> {
            coEvery { getPack(any()) } returns pack
            coEvery { getChapters(any()) } returns chapters
            coEvery { getLessons(any()) } returns lessons
        }
        return PackContentViewModel(
            savedStateHandle = SavedStateHandle(mapOf("packId" to packId.value)),
            contentRepository = repo,
        )
    }

    @Test
    fun lessonClick_carriesRealLessonId_notPackId() {
        val vm = viewModel()
        var openedLessonId: String? = null

        compose.setContent {
            GrammarMateTheme {
                PackContentScreen(
                    packId = packId.value,
                    onNavigateBack = {},
                    onOpenLesson = { openedLessonId = it },
                    viewModel = vm,
                )
            }
        }

        compose.onNodeWithTag(PackContentTestTags.CHAPTER_HEADER).assertIsDisplayed()
        compose.onNodeWithText("Приветствие").assertIsDisplayed()
        assertThat(
            compose.onAllNodesWithTag(PackContentTestTags.LESSON_ITEM).fetchSemanticsNodes().size,
        ).isEqualTo(2)

        compose.onNodeWithText("Приветствие").performClick()

        compose.runOnIdle {
            // ★ Реальный lessonId урока, а НЕ packId (P0-регрессия Фазы 1).
            assertThat(openedLessonId).isEqualTo("lesson_01")
            assertThat(openedLessonId).isNotEqualTo(packId.value)
        }
    }

    @Test
    fun packNotFound_showsExplicitError() {
        val vm = viewModel(pack = null)

        compose.setContent {
            GrammarMateTheme {
                PackContentScreen(
                    packId = packId.value,
                    onNavigateBack = {},
                    onOpenLesson = {},
                    viewModel = vm,
                )
            }
        }

        compose.onNodeWithText("Пак не найден", substring = true).assertIsDisplayed()
    }

    /** D3: кнопки режимов кликабельны и несут маршруты. */
    @Test
    fun modeButtons_navigateToDrillVocabDaily() {
        val vm = viewModel()
        var verb = false; var vocab = false; var daily = false
        compose.setContent {
            GrammarMateTheme {
                PackContentScreen(
                    packId = "TEST_PACK",
                    onNavigateBack = {},
                    onOpenLesson = {},
                    onOpenVerbDrill = { verb = true },
                    onOpenVocabDrill = { vocab = true },
                    onOpenDailyPractice = { daily = true },
                    viewModel = vm,
                )
            }
        }
        compose.onNodeWithTag(PACK_VERB_DRILL_TAG).performClick()
        compose.onNodeWithTag(PACK_VOCAB_DRILL_TAG).performClick()
        compose.onNodeWithTag(PACK_DAILY_TAG).performClick()
        compose.runOnIdle {
            assertThat(verb).isTrue(); assertThat(vocab).isTrue(); assertThat(daily).isTrue()
        }
    }
}
