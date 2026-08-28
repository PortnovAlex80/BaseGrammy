package com.alexpo.grammermate.v2.feature.training

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.AppConfig
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.SettingsRepository
import com.alexpo.grammermate.domain.repository.UserContentRepository
import com.alexpo.grammermate.domain.session.SessionEngine
import com.alexpo.grammermate.domain.validation.AnswerValidator
import com.alexpo.grammermate.v2.core.data.local.TrainingDbFixture
import com.alexpo.grammermate.v2.core.data.repository.FakeSessionRepository
import com.alexpo.grammermate.v2.ui.theme.GrammarMateTheme
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
 * UI-варианты тренировки (Фаза 3 slice 3 плана: regression-matrix «UI variants —
 * font 200%, dark/light» без эмулятора + clickable Recovered-session экран).
 *
 * Robolectric-покрытие ограничено by-design: полная матрица (RU/EN, tablet,
 * реальные плотности) — instrumented/nightly контур CI (план §5.1).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrainingUiVariantsTest {

    @get:Rule
    val compose = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val packId = PackId(TrainingDbFixture.PACK_ID)
    private val lessonId = LessonId(TrainingDbFixture.LESSON_ID)
    private val sessionId = SessionId.forLesson(packId, lessonId)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Recovered-session экран: реальные клики через UI ─────────────────────

    @Test
    fun resumeGate_resumeButton_continuesLesson() {
        val vm = midLessonViewModel()

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

        compose.onNodeWithText("Урок начат").assertIsDisplayed()
        compose.onNodeWithText("Пройдено 1 из 3", substring = true).assertIsDisplayed()

        compose.onNodeWithTag(TrainingTestTags.RESUME_BUTTON)
            .assertIsDisplayed()
            .performClick()

        // Продолжение с сохранённой карточки (после ответа current не двигался).
        compose.onNodeWithText("Промпт 0").assertIsDisplayed()
    }

    @Test
    fun resumeGate_restartButton_startsFreshLesson() {
        val vm = midLessonViewModel()

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
        compose.onNodeWithText("Урок начат").assertIsDisplayed()

        compose.onNodeWithTag(TrainingTestTags.RESTART_BUTTON)
            .assertIsDisplayed()
            .performClick()

        // Свежий проход: первая карточка, гейт исчез.
        compose.onNodeWithText("Промпт 0").assertIsDisplayed()
        compose.onNodeWithText("Урок начат").assertDoesNotExist()
    }

    // ── UI variants: font scale 200% и тёмная тема ───────────────────────────

    @Test
    fun activePhase_fontScale200_keyElementsVisible() {
        val vm = freshViewModel()

        compose.setContent {
            GrammarMateTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = 2f),
                ) {
                    TrainingScreen(
                        packId = packId.value,
                        lessonId = lessonId.value,
                        onNavigateBack = {},
                        viewModel = vm,
                    )
                }
            }
        }

        compose.onNodeWithTag(TrainingTestTags.PROMPT_CARD).assertIsDisplayed()
        compose.onNodeWithTag(TrainingTestTags.INPUT_FIELD).assertIsDisplayed()
        compose.onNodeWithTag(TrainingTestTags.CHECK_BUTTON).assertIsDisplayed()
    }

    @Test
    fun activePhase_darkTheme_rendersWithoutCrash() {
        val vm = freshViewModel()

        compose.setContent {
            GrammarMateTheme(darkTheme = true) {
                TrainingScreen(
                    packId = packId.value,
                    lessonId = lessonId.value,
                    onNavigateBack = {},
                    viewModel = vm,
                )
            }
        }

        compose.onNodeWithTag(TrainingTestTags.PROMPT_CARD).assertIsDisplayed()
        compose.onNodeWithTag(TrainingTestTags.INPUT_FIELD).assertIsDisplayed()
    }

    @Test
    fun resumeGate_darkTheme_keyButtonsVisible() {
        val vm = midLessonViewModel()

        compose.setContent {
            GrammarMateTheme(darkTheme = true) {
                TrainingScreen(
                    packId = packId.value,
                    lessonId = lessonId.value,
                    onNavigateBack = {},
                    viewModel = vm,
                )
            }
        }

        compose.onNodeWithTag(TrainingTestTags.RESUME_BUTTON).assertIsDisplayed()
        compose.onNodeWithTag(TrainingTestTags.RESTART_BUTTON).assertIsDisplayed()
    }

    // ── Хелперы ──────────────────────────────────────────────────────────────

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

    private fun freshViewModel(): TrainingViewModel {
        val parts = harness()
        return TrainingViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("packId" to packId.value, "lessonId" to lessonId.value),
            ),
            sessionEngine = SessionEngine(parts.first, parts.second, parts.third, clock = { 1L }),
            contentRepository = parts.second,
            userContentRepository = parts.third,
            vocabDrillRepository = io.mockk.mockk(relaxed = true),
            settingsRepository = mockk<SettingsRepository>(relaxed = true) {
                coEvery { getAppConfig() } returns AppConfig()
            },
            answerValidator = AnswerValidator(),
        )
    }

    /** VM над сессией с одной отвеченной карточкой → вход показывает ResumeGate. */
    private fun midLessonViewModel(): TrainingViewModel {
        val (sessionRepository, contentRepository, userContentRepository) = harness()
        val engine = SessionEngine(sessionRepository, contentRepository, userContentRepository, clock = { 1L })
        runBlocking {
            engine.startLessonSession(packId, lessonId, sessionSize = 10)
            engine.submitAnswer(
                sessionId, CardId(TrainingDbFixture.CARD_IDS.first()),
                isCorrect = true, inputMode = InputMode.KEYBOARD,
            )
        }
        return TrainingViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("packId" to packId.value, "lessonId" to lessonId.value),
            ),
            sessionEngine = engine,
            contentRepository = contentRepository,
            userContentRepository = userContentRepository,
            vocabDrillRepository = io.mockk.mockk(relaxed = true),
            settingsRepository = mockk<SettingsRepository>(relaxed = true) {
                coEvery { getAppConfig() } returns AppConfig()
            },
            answerValidator = AnswerValidator(),
        )
    }

    private fun harness(): Triple<
        FakeSessionRepository,
        ContentRepository,
        UserContentRepository,
        > {
        val sessionRepository = FakeSessionRepository(clock = { 1L })
        val contentRepository = mockk<ContentRepository>(relaxed = true) {
            coEvery { getCards(packId, lessonId) } returns lessonCards()
        }
        val userContentRepository = mockk<UserContentRepository>(relaxed = true) {
            coEvery { getHiddenCardIds(any()) } returns emptySet()
        }
        return Triple(sessionRepository, contentRepository, userContentRepository)
    }
}
