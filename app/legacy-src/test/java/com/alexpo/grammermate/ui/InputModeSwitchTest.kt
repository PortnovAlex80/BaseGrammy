package com.alexpo.grammermate.ui

import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test 3: Input mode switch changes state.
 * Verifies state update + UI reaction.
 */
@RunWith(AndroidJUnit4::class)
class InputModeSwitchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun click_input_mode_button_changes_display() {
        composeTestRule.setContent {
            val mode = remember { mutableStateOf("KEYBOARD") }
            Text("Current: ${mode.value}")
            IconButton(onClick = { mode.value = "VOICE" }) {
                Text("🎤")
            }
        }

        // Initial state
        composeTestRule.onNodeWithText("Current: KEYBOARD").assertIsDisplayed()

        // Click to switch mode
        composeTestRule.onNodeWithText("🎤").performClick()

        // Wait for recomposition
        composeTestRule.waitForIdle()

        // Mode changed
        composeTestRule.onNodeWithText("Current: VOICE").assertIsDisplayed()
    }
}
