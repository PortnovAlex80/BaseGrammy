package com.alexpo.grammermate.v2.feature.training

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.domain.TrainingConfig
import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.UserContentRepository
import com.alexpo.grammermate.domain.session.SessionEngine
import com.alexpo.grammermate.domain.validation.AnswerValidator
import com.alexpo.grammermate.v2.core.data.local.TrainingDbFixture
import com.alexpo.grammermate.v2.core.data.repository.FakeSessionRepository
import com.alexpo.grammermate.v2.ui.theme.GrammarMateTheme
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
 * Clickable-путь экрана тренировки (Фаза 3 плана: Compose journey для
 * изменённого feature — «реальные клики, enabled states»).
 *
 * Active (ввод + submit) → Feedback (✓/✗ + Next) — золотой отрезок
 * golden journey без эмулятора: реальный TrainingViewModel на fake-портах.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrainingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val packId = PackId(TrainingDbFixture.PACK_ID)
    private val lessonId = LessonId(TrainingDbFixture.LESSON_ID)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun lessonCards(): List<Card> =
        TrainingDbFixture.CARD_IDS.mapIndexed { index, cardId ->
            Card(
                id = CardId(cardId),
                packId = packId,
                lessonId = lessonId,
                ord = index,
                type = CardType.SENTENCE,
                promptRu = "Промпт $index",
                acceptedAnswers = listOf("answer $index"),
                tense = null, verb = null, verbGroup = null, person = null,
                frequencyRank = null,
            )
        }

    private fun viewModel(): TrainingViewModel {
        val sessionRepository = FakeSessionRepository(clock = { 1L })
        val contentRepository = mockk<ContentRepository>(relaxed = true) {
            coEvery { getCards(lessonId) } returns lessonCards()
        }
        val userContentRepository = mockk<UserContentRepository>(relaxed = true) {
            coEvery { getHiddenCardIds() } returns emptySet()
        }
        return TrainingViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("packId" to packId.value, "lessonId" to lessonId.value),
            ),
            sessionEngine = SessionEngine(sessionRepository, contentRepository, userContentRepository, clock = { 1L }),
            contentRepository = contentRepository,
            userContentRepository = userContentRepository,
            answerValidator = AnswerValidator(),
        )
    }

    @Test
    fun activePhase_showsFirstCard_draftInputAndSubmit() {
        val vm = viewModel()

        compose.setContent {
            GrammarMateTheme {
                TrainingScreen(
                    packId = packId.value,
                    lessonId = lessonId.value,
                    onNavigateBack = {},
                    viewModel = vm,
                )
            }
        }

        compose.onNodeWithText("Промпт 0").assertIsDisplayed()

        compose.onNodeWithTag(TrainingTestTags.INPUT_FIELD)
            .performTextInput("answer 0")
        compose.onNodeWithTag(TrainingTestTags.CHECK_BUTTON)
            .assertIsDisplayed()
            .performClick()

        // После успешного commit — Feedback с результатом (правило §3.1.5).
        compose.onNodeWithText("Верно!").assertIsDisplayed()
    }

    @Test
    fun feedback_nextAdvancesToSecondCard() {
        val vm = viewModel()

        compose.setContent {
            GrammarMateTheme {
                TrainingScreen(
                    packId = packId.value,
                    lessonId = lessonId.value,
                    onNavigateBack = {},
                    viewModel = vm,
                )
            }
        }

        compose.onNodeWithTag(TrainingTestTags.INPUT_FIELD).performTextInput("nope")
        compose.onNodeWithTag(TrainingTestTags.CHECK_BUTTON).performClick()
        compose.onNodeWithText("Неверно").assertIsDisplayed()

        compose.onNodeWithTag(TrainingTestTags.NEXT_BUTTON).performClick()

        compose.onNodeWithText("Промпт 1").assertIsDisplayed()
    }

    @Test
    fun topBarBack_invokesNavigationCallback() {
        val vm = viewModel()
        var navigatedBack = false

        compose.setContent {
            GrammarMateTheme {
                TrainingScreen(
                    packId = packId.value,
                    lessonId = lessonId.value,
                    onNavigateBack = { navigatedBack = true },
                    viewModel = vm,
                )
            }
        }

        compose.onNodeWithContentDescription("Назад").performClick()

        compose.runOnIdle { assert(navigatedBack) }
    }
}
