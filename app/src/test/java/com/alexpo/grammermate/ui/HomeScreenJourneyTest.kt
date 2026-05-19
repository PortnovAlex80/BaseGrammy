package com.alexpo.grammermate.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.ui.screens.HomeScreen
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeScreenJourneyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var clickedPrimary = false

    @Before
    fun reset() {
        clickedPrimary = false
    }

    private fun setHomeScreenContent() {
        composeTestRule.setContent {
            GrammarMateTheme {
                HomeScreen(
                    state = TrainingUiState(),
                    onSelectLanguage = {},
                    onOpenSettings = {},
                    onPrimaryAction = { clickedPrimary = true },
                    onSelectLesson = {},
                    onOpenElite = {},
                )
            }
        }
    }

    @Test
    fun `home screen renders Grammar Roadmap`() {
        setHomeScreenContent()
        composeTestRule.onAllNodesWithText("Grammar Roadmap").onFirst().assertIsDisplayed()
    }

    @Test
    fun `clicking Continue Learning triggers navigation callback`() {
        setHomeScreenContent()
        composeTestRule.onAllNodesWithText("Continue Learning").onFirst().performClick()
        assertTrue("onPrimaryAction callback should fire", clickedPrimary)
    }
}
