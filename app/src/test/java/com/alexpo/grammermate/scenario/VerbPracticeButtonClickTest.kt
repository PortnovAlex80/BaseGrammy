package com.alexpo.grammermate.scenario

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.ui.screens.VerbDrillEntryTile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test for Verb Practice button on HomeScreen.
 * Verifies:
 * 1. Button with text "Verb Practice" exists and is clickable
 * 2. Button has correct testTag for UI testing
 * 3. onClick callback is invoked when clicked
 *
 * Context: Button name changed from "Verb Drill" to "Verb Practice"
 * String resource: R.string.home_verb_drill = "Verb Practice"
 *
 * References:
 * - Spec: docs/specification/10-verb-drill.md
 * - Screen: HomeScreen.kt (VerbDrillEntryTile composable)
 */
@RunWith(AndroidJUnit4::class)
class VerbPracticeButtonClickTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verbPracticeButton_isDisplayedAndClickable() {
        var clickCount = 0

        // --- SETUP: Render VerbDrillEntryTile with test onClick ---
        composeTestRule.setContent {
            MaterialTheme {
                VerbDrillEntryTile(
                    onClick = { clickCount++ }
                )
            }
        }

        // --- ASSERT: Button text "Verb Practice" is displayed ---
        composeTestRule
            .onNodeWithText("Verb Practice")
            .assertExists()

        // --- ASSERT: Button has correct testTag ---
        composeTestRule
            .onNodeWithTag("verb_drill_entry_tile")
            .assertExists()

        // --- ASSERT: Button is enabled/clickable ---
        composeTestRule
            .onNodeWithTag("verb_drill_entry_tile")
            .assertIsEnabled()

        // --- ACTION: Click the button ---
        composeTestRule
            .onNodeWithTag("verb_drill_entry_tile")
            .performClick()

        // --- ASSERT: onClick callback was invoked ---
        composeTestRule.waitForIdle()
        assert(clickCount == 1) { "onClick should be called exactly once, but was called $clickCount times" }
    }

    @Test
    fun verbPracticeButton_clickTwice_invokesCallbackTwice() {
        var clickCount = 0

        // --- SETUP: Render VerbDrillEntryTile ---
        composeTestRule.setContent {
            MaterialTheme {
                VerbDrillEntryTile(
                    onClick = { clickCount++ }
                )
            }
        }

        // --- ACTION: Click twice ---
        composeTestRule.onNodeWithTag("verb_drill_entry_tile").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("verb_drill_entry_tile").performClick()
        composeTestRule.waitForIdle()

        // --- ASSERT: onClick was called twice ---
        assert(clickCount == 2) { "onClick should be called twice, but was called $clickCount times" }
    }
}
