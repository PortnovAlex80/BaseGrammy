package com.alexpo.grammermate.scenario

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.VerbDrillLastSessionState
import com.alexpo.grammermate.ui.SessionCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for VerbDrill SessionCard component.
 *
 * Verifies the SessionCard that displays when lastSessionContext is provided,
 * showing Repeat and Continue buttons for resuming a previous session.
 *
 * References:
 * - Spec: docs/specification/10-verb-drill.md (VD-51)
 * - Component: VerbDrillScreen.kt (SessionCard composable)
 * - Strings: values/strings-app.xml (verb_session_card_*)
 */
@RunWith(AndroidJUnit4::class)
class VerbDrillSessionCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testSessionContext = VerbDrillLastSessionState(
        selectedTense = "Presente",
        selectedGroup = "regular_are",
        sortByFrequency = false,
        todayShownCardIds = setOf("card-1", "card-2", "card-3")
    )

    @Test
    fun sessionCard_displaysSessionContext() {
        // --- SETUP: Render SessionCard with test context ---
        composeTestRule.setContent {
            MaterialTheme {
                SessionCard(
                    lastSessionContext = testSessionContext,
                    onRepeat = {},
                    onContinue = {}
                )
            }
        }

        // --- ASSERT: Title is displayed ---
        composeTestRule
            .onNodeWithText("Previous session")
            .assertExists()

        // --- ASSERT: Tense is displayed ---
        composeTestRule
            .onNodeWithText("Presente")
            .assertExists()

        // --- ASSERT: Group is displayed ---
        composeTestRule
            .onNodeWithText("regular_are")
            .assertExists()

        // --- ASSERT: Cards shown count is displayed ---
        composeTestRule
            .onNodeWithText("3 cards shown")
            .assertExists()
    }

    @Test
    fun sessionCard_buttonsAreDisplayedAndEnabled() {
        // --- SETUP: Render SessionCard ---
        composeTestRule.setContent {
            MaterialTheme {
                SessionCard(
                    lastSessionContext = testSessionContext,
                    onRepeat = {},
                    onContinue = {}
                )
            }
        }

        // --- ASSERT: "Repeat" button is displayed ---
        composeTestRule
            .onNodeWithText("Repeat")
            .assertExists()

        // --- ASSERT: "Repeat" button is enabled ---
        composeTestRule
            .onNodeWithText("Repeat")
            .assertIsEnabled()

        // --- ASSERT: "Continue" button is displayed ---
        composeTestRule
            .onNodeWithText("Continue")
            .assertExists()

        // --- ASSERT: "Continue" button is enabled ---
        composeTestRule
            .onNodeWithText("Continue")
            .assertIsEnabled()
    }

    @Test
    fun sessionCard_repeatButton_invokesCallback() {
        var repeatClickCount = 0
        var continueClickCount = 0

        // --- SETUP: Render SessionCard with callbacks ---
        composeTestRule.setContent {
            MaterialTheme {
                SessionCard(
                    lastSessionContext = testSessionContext,
                    onRepeat = { repeatClickCount++ },
                    onContinue = { continueClickCount++ }
                )
            }
        }

        // --- ACTION: Click Repeat button ---
        composeTestRule
            .onNodeWithText("Repeat")
            .performClick()

        composeTestRule.waitForIdle()

        // --- ASSERT: onRepeat callback was invoked exactly once ---
        assert(repeatClickCount == 1) {
            "onRepeat should be called exactly once, but was called $repeatClickCount times"
        }

        // --- ASSERT: onContinue callback was NOT invoked ---
        assert(continueClickCount == 0) {
            "onContinue should not be called when Repeat is clicked"
        }
    }

    @Test
    fun sessionCard_continueButton_invokesCallback() {
        var repeatClickCount = 0
        var continueClickCount = 0

        // --- SETUP: Render SessionCard with callbacks ---
        composeTestRule.setContent {
            MaterialTheme {
                SessionCard(
                    lastSessionContext = testSessionContext,
                    onRepeat = { repeatClickCount++ },
                    onContinue = { continueClickCount++ }
                )
            }
        }

        // --- ACTION: Click Continue button ---
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()

        composeTestRule.waitForIdle()

        // --- ASSERT: onContinue callback was invoked exactly once ---
        assert(continueClickCount == 1) {
            "onContinue should be called exactly once, but was called $continueClickCount times"
        }

        // --- ASSERT: onRepeat callback was NOT invoked ---
        assert(repeatClickCount == 0) {
            "onRepeat should not be called when Continue is clicked"
        }
    }

    @Test
    fun sessionCard_withEmptyShownCards_displaysZeroCount() {
        val emptySessionContext = VerbDrillLastSessionState(
            selectedTense = "Passato Prossimo",
            selectedGroup = null,
            sortByFrequency = true,
            todayShownCardIds = emptySet()
        )

        // --- SETUP: Render SessionCard with empty shown cards ---
        composeTestRule.setContent {
            MaterialTheme {
                SessionCard(
                    lastSessionContext = emptySessionContext,
                    onRepeat = {},
                    onContinue = {}
                )
            }
        }

        // --- ASSERT: Tense is displayed ---
        composeTestRule
            .onNodeWithText("Passato Prossimo")
            .assertExists()

        // --- ASSERT: Zero cards shown is displayed ---
        composeTestRule
            .onNodeWithText("0 cards shown")
            .assertExists()
    }
}
