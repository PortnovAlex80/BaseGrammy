package com.alexpo.grammermate.v2.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.Pack
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.MasteryRepository
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
 * Clickable-путь экрана Home (Фаза 3 плана: «каждый видимый route имеет хотя
 * бы один clickable happy path и один failure/retry path»; Compose/Robolectric).
 *
 * Home → клик по паку → колбэк с реальным packId (начало golden journey:
 * Home → PackContent → Training).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun pack(id: String, name: String) = Pack(
        id = PackId(id),
        languageId = LanguageId("it"),
        displayName = name,
        version = "1",
        importedAtMs = 0L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Прогресс-канал пуст: пак без mastery-строк = 0% (ADR-002 слой 1). */
    private fun emptyProgress(): MasteryRepository = mockk {
        coEvery { observePackProgress() } returns kotlinx.coroutines.flow.flowOf(emptyList())
    }

    private fun viewModel(packs: List<Pack>, error: Boolean = false): HomeViewModel {
        val repo = mockk<ContentRepository> {
            coEvery { getPacks() } returns packs
            coEvery { observePacks() } returns kotlinx.coroutines.flow.flowOf(packs)
            if (error) {
                coEvery { getPacks() } throws IllegalStateException("network down")
                coEvery { observePacks() } throws IllegalStateException("network down")
            }
        }
        return HomeViewModel(repo, emptyProgress())
    }

    @Test
    fun packClick_opensPackContentWithRealPackId() {
        val vm = viewModel(listOf(pack("ITALIAN_SHORT", "Итальянский экспресс")))
        var clickedPackId: String? = null

        compose.setContent {
            GrammarMateTheme {
                HomeScreen(
                    onPackClick = { clickedPackId = it },
                    onNavigateSettings = {},
                    viewModel = vm,
                )
            }
        }

        compose.onNodeWithTag(HOME_PACK_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithText("Итальянский экспресс").assertIsDisplayed()

        compose.onNodeWithTag(HOME_PACK_CARD_TAG).performClick()

        compose.runOnIdle { assertThat(clickedPackId).isEqualTo("ITALIAN_SHORT") }
    }

    @Test
    fun emptyPacks_showsEmptyState_notFakeContent() {
        val vm = viewModel(emptyList())

        compose.setContent {
            GrammarMateTheme {
                HomeScreen(onPackClick = {}, onNavigateSettings = {}, viewModel = vm)
            }
        }

        // Явный empty-state (после bundled-seed он заполнится реактивно).
        compose.onNodeWithText("Пока нет установленных паков", substring = true).assertIsDisplayed()
    }

    @Test
    fun loadError_showsRetry_andRetryRefetches() {
        var failing = true
        val repo = mockk<ContentRepository> {
            coEvery { getPacks() } answers {
                if (failing) throw IllegalStateException("network down") else listOf(pack("P1", "Пак 1"))
            }
            // Канал наблюдения падает ПРИ КОЛЛЕКЦИИ (mockk `throws` кидает при
            // самом вызове и роняет конструктор VM); retry-путь идёт через
            // одноразовый getPacks — от observePacks не зависит.
            coEvery { observePacks() } returns kotlinx.coroutines.flow.flow {
                throw IllegalStateException("network down")
            }
        }
        val vm = HomeViewModel(repo, emptyProgress())

        compose.setContent {
            GrammarMateTheme {
                HomeScreen(onPackClick = {}, onNavigateSettings = {}, viewModel = vm)
            }
        }

        compose.onNodeWithText("Упс!").assertIsDisplayed()

        // Retry повторяет операцию загрузки (Фаза 3: retry ≠ back).
        failing = false
        compose.onNodeWithTag(HOME_RETRY_BUTTON_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Пак 1").assertIsDisplayed()
    }
}
