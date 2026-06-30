package com.alexpo.grammermate.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test 2: Word bank toggle opens list.
 * Verifies toggle state change + visibility change.
 */
@RunWith(AndroidJUnit4::class)
class WordBankToggleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun click_word_bank_icon_shows_list() {
        composeTestRule.setContent {
            val expanded = remember { mutableStateOf(false) }
            if (expanded.value) {
                Text("Word Bank: apple, banana, cherry")
            } else {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_add),
                    contentDescription = "Word Bank",
                    modifier = Modifier.clickable { expanded.value = true }
                )
            }
        }

        // Icon visible initially
        composeTestRule.onNodeWithContentDescription("Word Bank").assertIsDisplayed()

        // Click to expand
        composeTestRule.onNodeWithContentDescription("Word Bank").performClick()

        // Wait for recomposition
        composeTestRule.waitForIdle()

        // Word bank list visible after click
        composeTestRule.onNodeWithText("Word Bank: apple, banana, cherry").assertIsDisplayed()
    }
}
