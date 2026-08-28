package com.alexpo.grammermate.v2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.v2.feature.home.HOME_PACK_CARD_TAG
import com.alexpo.grammermate.v2.feature.packcontent.PACK_VERB_DRILL_TAG
import com.alexpo.grammermate.v2.feature.packcontent.PACK_VOCAB_DRILL_TAG
import com.alexpo.grammermate.v2.feature.packcontent.PACK_DAILY_TAG
import com.alexpo.grammermate.v2.feature.packcontent.PACK_POMODORO_TAG
import com.alexpo.grammermate.v2.feature.packcontent.PACK_CHAPTER_STORY_TAG
import com.alexpo.grammermate.v2.feature.packcontent.PackContentTestTags
import com.alexpo.grammermate.v2.feature.daily.DailyTestTags
import com.alexpo.grammermate.v2.feature.pomodoro.PomodoroTestTags
import com.alexpo.grammermate.v2.feature.story.StoryReaderTestTags
import com.alexpo.grammermate.v2.feature.settings.SettingsTestTags
import com.alexpo.grammermate.v2.feature.training.TrainingTestTags
import com.alexpo.grammermate.v2.feature.verbdrill.VerbDrillTestTags
import com.alexpo.grammermate.v2.feature.vocabdrill.VocabDrillTestTags
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

    private fun openBundledPack() {
        compose.waitUntil(timeoutMillis = 180_000) {
            compose.onAllNodesWithTag(HOME_PACK_CARD_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag(HOME_PACK_CARD_TAG)[0].performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(PACK_VERB_DRILL_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag(tag)[0].assertIsDisplayed()
    }

    @Test
    fun launch_showsBundledPack_onPackContent_showsModeButtons() {
        // Bundled-seed идемпотентен и реактивен: ждём появления карточки пака
        // (может занять время на первый распаковку — waitUntil).
        compose.waitUntil(timeoutMillis = 180_000) {
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

    @Test
    fun packContent_opensFirstLessonWithTrainingCard() {
        openBundledPack()
        compose.onAllNodesWithTag(PackContentTestTags.LESSON_ITEM)[0]
            .performScrollTo()
            .performClick()
        waitForTag(TrainingTestTags.INPUT_FIELD)
    }

    @Test
    fun packContent_opensChapterStoryWithImportedMarkdown() {
        openBundledPack()
        compose.onAllNodesWithTag(PACK_CHAPTER_STORY_TAG)[0]
            .performScrollTo()
            .performClick()
        waitForTag(StoryReaderTestTags.TEXT)
    }

    @Test
    fun packContent_opensVerbDrill() {
        openBundledPack()
        compose.onNodeWithTag(PACK_VERB_DRILL_TAG).performClick()
        waitForTag(VerbDrillTestTags.INPUT_FIELD)
    }

    @Test
    fun packContent_opensVocabDrill() {
        openBundledPack()
        compose.onNodeWithTag(PACK_VOCAB_DRILL_TAG).performClick()
        waitForTag(VocabDrillTestTags.REVEAL_BUTTON)
    }

    @Test
    fun packContent_opensDailyPractice() {
        openBundledPack()
        compose.onNodeWithTag(PACK_DAILY_TAG).performClick()
        waitForTag(DailyTestTags.INPUT_FIELD)
    }

    @Test
    fun packContent_opensPomodoro() {
        openBundledPack()
        compose.onNodeWithTag(PACK_POMODORO_TAG).performClick()
        waitForTag(PomodoroTestTags.PRESET_QUICK)
    }

    @Test
    fun bottomNavigation_opensSessionSizeSettings() {
        compose.onNodeWithText("Настройки").performClick()

        waitForTag(SettingsTestTags.SESSION_SIZE_VALUE)
        compose.onNodeWithTag(SettingsTestTags.SESSION_SIZE_DECREASE).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTestTags.SESSION_SIZE_INCREASE).assertIsDisplayed()
    }
}
