package com.alexpo.grammermate.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.ui.screens.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w320dp-h470dp")
class HomeCompactViewportTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun lowerPackActionsCanBeScrolledIntoViewAndClicked() {
        val state = TrainingUiState().copy(
            navigation = NavigationState(
                selectedLanguageId = LanguageId("it"),
                activePackId = PackId("TEST_PACK")
            )
        )
        var verbClicks = 0
        var auxClicks = 0
        var vocabClicks = 0
        var backgroundClicks = 0

        rule.setContent {
            GrammarMateTheme {
                HomeScreen(
                    state = state,
                    onSelectLanguage = {},
                    onOpenSettings = {},
                    onPrimaryAction = {},
                    onSelectLesson = {},
                    onDailyPractice = {},
                    hasVerbDrill = true,
                    hasVocabDrill = true,
                    onOpenVerbDrill = { verbClicks++ },
                    onOpenAuxDrill = { auxClicks++ },
                    onOpenVocabDrill = { vocabClicks++ },
                    onBackgroundVocab = { backgroundClicks++ }
                )
            }
        }

        rule.onNodeWithTag("verb_drill_entry_tile").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("aux_drill_entry_tile").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("vocab_drill_entry_tile").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("Фоновое слушание").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(1, verbClicks)
        assertEquals(1, auxClicks)
        assertEquals(1, vocabClicks)
        assertEquals(1, backgroundClicks)
    }
}
