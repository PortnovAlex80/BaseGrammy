package com.alexpo.grammermate.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeScreenJourneyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var clickedPrimary = false
    private var clickedDaily = false

    @Before
    fun reset() {
        clickedPrimary = false
        clickedDaily = false
    }

    @Test
    fun `home screen shows key elements`() {
        composeTestRule.setContent {
            GrammarMateTheme {
                HomeScreen(
                    state = TrainingUiState(),
                    onSelectLanguage = {},
                    onOpenSettings = {},
                    onPrimaryAction = { clickedPrimary = true },
                    onSelectLesson = {},
                    onOpenElite = { clickedDaily = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Grammar Roadmap").assertIsDisplayed()
    }

    @Test
    fun `clicking primary action triggers callback`() {
        composeTestRule.setContent {
            GrammarMateTheme {
                HomeScreen(
                    state = TrainingUiState(),
                    onSelectLanguage = {},
                    onOpenSettings = {},
                    onPrimaryAction = { clickedPrimary = true },
                    onSelectLesson = {},
                    onOpenElite = { clickedDaily = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Continue Learning").performClick()
        assertTrue("Primary action should have been clicked", clickedPrimary)
    }

    @Test
    fun `clicking daily practice triggers callback`() {
        composeTestRule.setContent {
            GrammarMateTheme {
                HomeScreen(
                    state = TrainingUiState(),
                    onSelectLanguage = {},
                    onOpenSettings = {},
                    onPrimaryAction = { clickedPrimary = true },
                    onSelectLesson = {},
                    onOpenElite = { clickedDaily = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Daily Practice").performClick()
        assertTrue("Daily practice should have been clicked", clickedDaily)
    }
}
