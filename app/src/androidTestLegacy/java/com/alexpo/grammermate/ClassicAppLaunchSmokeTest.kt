package com.alexpo.grammermate

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClassicAppLaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun productionEntryPointReachesInteractiveContent() {
        composeRule.waitUntil(timeoutMillis = 180_000) {
            composeRule.onAllNodesWithText("Welcome to GrammarMate!")
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("English")
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
