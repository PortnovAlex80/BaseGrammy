package com.alexpo.grammermate

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alexpo.grammermate.data.StoreFactory
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises reachable routes from MainActivity through the bundled Italian
 * chapter pack. No NavController injection or direct destination composition.
 *
 * The app configuration is restored after the journey; installed packs and
 * user progress are left in place so the test is safe on a reused emulator.
 */
@RunWith(AndroidJUnit4::class)
class ProductionRouteJourneySmokeTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Test
    fun productionEntriesRenderAndReturnThroughBack() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as GrammarMateApplication
        val configFile = File(app.filesDir, "grammarmate/config.yaml")
        val originalConfig = configFile.takeIf(File::exists)?.readBytes()
        val configStore = StoreFactory.getInstance(app).getAppConfigStore()
        val previousConfig = configStore.load()
        configStore.save(previousConfig.copy(uiLanguage = "en", voiceAutoStart = false))

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                enterItalianRoadmap()
                awaitText("Grammar Story Roadmap") // HOME, chapter-pack variant

                clickChapterAction() // First chapter with lessons
                awaitText("lessons completed", substring = true) // CHAPTER_LESSONS
                clickText("A01", substring = true)
                awaitAnyText("Start Lesson", "Continue Lesson", "All exercises completed!") // LESSON
                if (hasText("All exercises completed!")) {
                    clickText("Repeat")
                    awaitText("Choose difficulty")
                    clickText("Easy")
                } else {
                    clickText(if (hasText("Start Lesson")) "Start Lesson" else "Continue Lesson")
                }
                awaitTag("show_answer_button") // TRAINING
                Espresso.pressBack()
                awaitText("End session?")
                clickText("Exit")
                awaitAnyText("Start Lesson", "Continue Lesson", "All exercises completed!") // LESSON again
                clickDescription("Back")
                awaitText("lessons completed", substring = true)
                clickDescription("Back")
                awaitText("Grammar Story Roadmap")

                clickRoadmapText("Read Story")
                awaitDescription("Copy story to clipboard") // STORY_READER
                clickDescription("Back to Roadmap")
                awaitText("Grammar Story Roadmap")

                clickDescription("Settings")
                awaitText("Show ladder")
                composeRule.onAllNodesWithText("Show ladder")[0].performScrollTo().performClick()
                awaitText("Interval Ladder") // LADDER
                Espresso.pressBack()
                awaitText("Grammar Story Roadmap")

                clickRoadmapText("Фоновое слушание")
                awaitDescription("Next word") // BACKGROUND_VOCAB
                clickDescription("Back")
                awaitText("Grammar Story Roadmap")

                clickRoadmapText("Verb Practice")
                awaitAnyTag("sort_by_frequency_checkbox", "session_card") // VERB_DRILL
                clickDescription("Back")
                awaitText("Grammar Story Roadmap")

                clickRoadmapText("Flashcards")
                composeRule.waitUntil(timeoutMillis = 90_000) {
                    hasText("Due:", substring = true) || hasText("No words loaded")
                } // VOCAB_DRILL
                clickDescription("Back")
                awaitText("Grammar Story Roadmap")

                clickRoadmapText("Lead-in")
                awaitText("Select an auxiliary verb to drill") // AUX_DRILL
                clickDescription("Back")
                awaitText("Grammar Story Roadmap")
            }
        } finally {
            // Restore the caller's language and microphone preference without
            // deleting any saved learning state.
            configStore.save(previousConfig)
            if (originalConfig == null) configFile.delete()
            else if (!configFile.readBytes().contentEquals(originalConfig)) configFile.writeBytes(originalConfig)
        }
    }

    private fun enterItalianRoadmap() {
        composeRule.waitUntil(timeoutMillis = 180_000) {
            hasText("Welcome to GrammarMate!") ||
                hasText("Choose your language") ||
                hasText("Grammar Story Roadmap") ||
                hasText("English Full Course") ||
                hasText("Full Course Italian", substring = true)
        }
        if (hasText("Welcome to GrammarMate!")) {
            clickText("Skip")
            composeRule.waitUntil(timeoutMillis = 90_000) {
                hasText("Choose your language") ||
                    hasText("English Full Course") ||
                    hasText("Full Course Italian", substring = true) ||
                    hasText("Grammar Story Roadmap")
            }
        }
        if (hasText("Choose your language")) {
            clickText("Italian")
        } else {
            if (hasText("Grammar Story Roadmap")) clickDescription("Back")
            if (hasDescription("EN")) {
                clickDescription("EN")
                clickText("Italian")
            }
        }
        awaitText("Full Course Italian", substring = true)
        composeRule.onAllNodesWithText("Full Course Italian", substring = true)[0]
            .performScrollTo().performClick()
        awaitText("Grammar Story Roadmap")
    }

    private fun hasText(value: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(value, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun hasDescription(value: String): Boolean =
        composeRule.onAllNodesWithContentDescription(value).fetchSemanticsNodes().isNotEmpty()

    private fun awaitText(value: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 90_000) {
            composeRule.onAllNodesWithText(value, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitAnyText(vararg values: String) {
        composeRule.waitUntil(timeoutMillis = 90_000) { values.any { hasText(it) } }
    }

    private fun awaitDescription(value: String) {
        composeRule.waitUntil(timeoutMillis = 90_000) { hasDescription(value) }
    }

    private fun awaitTag(value: String) {
        composeRule.waitUntil(timeoutMillis = 90_000) {
            composeRule.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitAnyTag(vararg values: String) {
        composeRule.waitUntil(timeoutMillis = 90_000) {
            values.any { composeRule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private fun clickChapterAction() {
        val action = hasAnyDescendant(hasTextExactly("Continue")) or
            hasAnyDescendant(hasTextExactly("Review"))
        composeRule.onNode(hasScrollAction()).performScrollToNode(action)
        clickText(if (hasText("Continue")) "Continue" else "Review")
    }

    private fun clickRoadmapText(value: String) {
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasAnyDescendant(hasTextExactly(value)))
        clickText(value)
    }

    private fun clickText(value: String, substring: Boolean = false) {
        awaitText(value, substring)
        composeRule.onAllNodesWithText(value, substring = substring)[0].performClick()
    }

    private fun clickDescription(value: String) {
        awaitDescription(value)
        composeRule.onAllNodesWithContentDescription(value)[0].performClick()
    }
}
