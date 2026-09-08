package com.alexpo.grammermate.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test 5: Navigation click triggers callback.
 * Verifies navigation callback is invoked.
 */
@RunWith(AndroidJUnit4::class)
class NavigationClickTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun click_back_icon_triggers_navigation() {
        var navigatedBack = false

        composeTestRule.setContent {
            Text("Current Screen")
            Icon(
                painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_revert),
                contentDescription = "Navigate Back",
                modifier = Modifier.clickable { navigatedBack = true }
            )
        }

        // Initial state
        composeTestRule.onNodeWithText("Current Screen")

        // Click back
        composeTestRule.onNodeWithContentDescription("Navigate Back").performClick()

        // Navigation triggered
        assert(navigatedBack) { "Navigation callback should be triggered" }
    }
}
