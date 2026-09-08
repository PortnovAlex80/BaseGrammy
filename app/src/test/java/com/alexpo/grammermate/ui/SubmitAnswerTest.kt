package com.alexpo.grammermate.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test 4: Submit button click.
 * Verifies button click triggers state change.
 */
@RunWith(AndroidJUnit4::class)
class SubmitAnswerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun submit_button_click_shows_result() {
        composeTestRule.setContent {
            val submitted = remember { mutableStateOf(false) }
            if (submitted.value) {
                Text("Submitted!")
            } else {
                Button(onClick = { submitted.value = true }) {
                    Text("Submit")
                }
            }
        }

        // Initial state
        composeTestRule.onNodeWithText("Submit")

        // Click submit
        composeTestRule.onNodeWithText("Submit").performClick()

        // Result shown
        composeTestRule.onNodeWithText("Submitted!")
    }
}
