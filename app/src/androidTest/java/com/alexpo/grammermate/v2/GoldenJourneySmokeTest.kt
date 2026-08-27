package com.alexpo.grammermate.v2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.v2.feature.home.HOME_PACK_CARD_TAG
import com.alexpo.grammermate.v2.feature.packcontent.PACK_VERB_DRILL_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D6 аудита 2026-08-26 (TASK-073): первый instrumented-тест — golden journey
 * smoke на реальном приложении (Hilt инициализируется через Application):
 * запуск → Home с bundled-паком → PackContent с кнопками режимов.
 *
 * Запуск: эмулятор/CI (локально недоступно); компиляция доказана
 * assembleDebugAndroidTest. Полная матрица journeys — TASK-073.
 */
@RunWith(AndroidJUnit4::class)
class GoldenJourneySmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivityV2>()

    @Test
    fun launch_showsBundledPack_onPackContent_showsModeButtons() {
        // Bundled-seed идемпотентен и реактивен: ждём появления карточки пака
        // (может занять время на первый распаковку — waitUntil).
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithTag(HOME_PACK_CARD_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag(HOME_PACK_CARD_TAG)[0].assertIsDisplayed()

        compose.onAllNodesWithTag(HOME_PACK_CARD_TAG)[0].performClick()

        // PackContent: кнопка режима видна (маршрут Pack→Content работает).
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(PACK_VERB_DRILL_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(PACK_VERB_DRILL_TAG).assertIsDisplayed()
    }
}
