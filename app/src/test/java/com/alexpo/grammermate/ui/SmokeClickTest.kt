package com.alexpo.grammermate.ui

import androidx.compose.material3.Button
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
 * Test 1: Basic Compose click works.
 * Verifies that we can click on a button and see state change.
 */
@RunWith(AndroidJUnit4::class)
class SmokeClickTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun click_on_button_changes_text() {
        composeTestRule.setContent {
            val clicked = remember { mutableStateOf(false) }
            if (clicked.value) {
                Text("Clicked!")
            } else {
                Button(onClick = { clicked.value = true }) {
                    Text("Click Me")
                }
            }
        }

        // Initial state
        composeTestRule.onNodeWithText("Click Me").assertIsDisplayed()

        // Click
        composeTestRule.onNodeWithText("Click Me").performClick()

        // Wait for recomposition
        composeTestRule.waitForIdle()

        // After click
        composeTestRule.onNodeWithText("Clicked!").assertIsDisplayed()
    }
}
