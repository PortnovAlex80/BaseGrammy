package com.alexpo.grammermate

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal smoke test proving Compose UI testing works with Robolectric.
 * This is the foundation for incremental UI test growth.
 */
@RunWith(AndroidJUnit4::class)
class MinimalComposeRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun app_launches_and_renders_text() {
        composeTestRule.setContent {
            Text("Hello, Test!")
        }
        composeTestRule.onNodeWithText("Hello, Test!").assertIsDisplayed()
    }
}
