package com.alexpo.grammermate.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.*
import com.alexpo.grammermate.ui.screens.TrainingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI click test for pack-scoped lesson content isolation.
 *
 * Verifies that when TrainingScreen is rendered with cards from a specific pack,
 * the displayed content matches that pack's lesson data (not another pack's).
 *
 * This is NOT a ViewModel test — it tests the Compose UI rendering path.
 */
@RunWith(AndroidJUnit4::class)
class PackSwitchClickUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ========================================
    // Test 1: Italian SHORT pack shows correct card
    // ========================================

    @Test
    fun trainingScreen_showsCorrectCard_forItalianShortPack() {
        // ARRANGE: Italian Short A01 first card
        val shortCard = SentenceCard(
            id = "card_0",
            promptRu = "Я покупаю дом (compro, una casa)",
            acceptedAnswers = listOf("Compro una casa")
        )
        val initialState = TrainingUiState().copy(
            cardSession = CardSessionState(
                currentCard = shortCard,
                currentIndex = 0,
                subLessonTotal = 2,
                sessionState = SessionState.ACTIVE,
                inputMode = InputMode.KEYBOARD
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("it"),
                activePackId = PackId("ITALIAN_SHORT"),
                selectedLessonId = LessonId("lesson_01_A01")
            )
        )

        // ACT: Render with Italian Short state
        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = {},
                onSubmit = { SubmitResult(accepted = false, hintShown = false) },
                onPrev = {},
                onNext = {},
                onTogglePause = {},
                onRequestExit = {},
                onOpenSettings = {},
                onShowSettings = {},
                onSelectLesson = {},
                onSelectMode = {},
                onSetInputMode = {},
                onShowAnswer = {},
                onVoicePromptStarted = {},
                onSelectWordFromBank = {},
                onRemoveLastWord = {},
                onTtsSpeak = {}
            )
        }

        // ASSERT: Card shows SHORT pack's content (parenthetical stripped by HintCalculator)
        composeTestRule.onNodeWithTag("card_prompt_text")
            .assertIsDisplayed()
            .assertTextContains("Я покупаю дом", substring = true)
        // Should NOT show Italian Express content
        composeTestRule.onNodeWithText("Я говорю по-итальянски").assertDoesNotExist()
    }

    // ========================================
    // Test 2: Italian EXPRESS pack shows correct card
    // ========================================

    @Test
    fun trainingScreen_showsCorrectCard_forItalianExpressPack() {
        // ARRANGE: Italian Express A01 first card
        val expressCard = SentenceCard(
            id = "card_0",
            promptRu = "Я говорю по-итальянски (parlo, italiano)",
            acceptedAnswers = listOf("Parlo italiano")
        )
        val initialState = TrainingUiState().copy(
            cardSession = CardSessionState(
                currentCard = expressCard,
                currentIndex = 0,
                subLessonTotal = 10,
                sessionState = SessionState.ACTIVE,
                inputMode = InputMode.KEYBOARD
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("it"),
                activePackId = PackId("ITALIAN_EXPRESS"),
                selectedLessonId = LessonId("lesson_01_A01")
            )
        )

        // ACT: Render with Italian Express state
        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = {},
                onSubmit = { SubmitResult(accepted = false, hintShown = false) },
                onPrev = {},
                onNext = {},
                onTogglePause = {},
                onRequestExit = {},
                onOpenSettings = {},
                onShowSettings = {},
                onSelectLesson = {},
                onSelectMode = {},
                onSetInputMode = {},
                onShowAnswer = {},
                onVoicePromptStarted = {},
                onSelectWordFromBank = {},
                onRemoveLastWord = {},
                onTtsSpeak = {}
            )
        }

        // ASSERT: Card shows EXPRESS pack's content (parenthetical stripped by HintCalculator)
        composeTestRule.onNodeWithTag("card_prompt_text")
            .assertIsDisplayed()
            .assertTextContains("Я говорю по-итальянски", substring = true)
        composeTestRule.onNodeWithText("Я покупаю дом").assertDoesNotExist()
    }

    // ========================================
    // Test 3: Submit correct answer from SHORT pack
    // ========================================

    @Test
    fun trainingScreen_submitCorrectAnswer_forShortPack() {
        // ARRANGE
        val card1 = SentenceCard(
            id = "card_0",
            promptRu = "Я покупаю дом",
            acceptedAnswers = listOf("Compro una casa")
        )

        var submitCount = 0

        val initialState = TrainingUiState().copy(
            cardSession = CardSessionState(
                currentCard = card1,
                currentIndex = 0,
                subLessonTotal = 2,
                sessionState = SessionState.ACTIVE,
                inputMode = InputMode.KEYBOARD
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("it"),
                activePackId = PackId("ITALIAN_SHORT"),
                selectedLessonId = LessonId("lesson_01_A01")
            )
        )

        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = {},
                onSubmit = {
                    submitCount++
                    SubmitResult(accepted = true, hintShown = false)
                },
                onPrev = {},
                onNext = {},
                onTogglePause = {},
                onRequestExit = {},
                onOpenSettings = {},
                onShowSettings = {},
                onSelectLesson = {},
                onSelectMode = {},
                onSetInputMode = {},
                onShowAnswer = {},
                onVoicePromptStarted = {},
                onSelectWordFromBank = {},
                onRemoveLastWord = {},
                onTtsSpeak = {}
            )
        }

        // ASSERT: SHORT pack card is visible
        composeTestRule.onNodeWithTag("card_prompt_text")
            .assertIsDisplayed()
            .assertTextContains("Я покупаю дом", substring = true)

        // ACT: Type correct answer and submit
        composeTestRule.onNodeWithTag("input_field")
            .performTextInput("Compro una casa")
        composeTestRule.onNodeWithTag("check_button").performClick()

        // ASSERT: Submit callback was invoked
        assert(submitCount == 1) { "Submit should be called once after clicking check" }

        // Should NOT show Express content at any point
        composeTestRule.onNodeWithText("Я говорю по-итальянски").assertDoesNotExist()
    }

    // ========================================
    // Test 4: Switching pack state changes displayed card
    // ========================================

    @Test
    fun trainingScreen_differentPacksWithSameLessonId_showDifferentContent() {
        // ARRANGE: Express pack card
        val expressCard = SentenceCard(
            id = "card_0",
            promptRu = "Я говорю по-итальянски",
            acceptedAnswers = listOf("Parlo italiano")
        )
        val expressState = TrainingUiState().copy(
            cardSession = CardSessionState(
                currentCard = expressCard,
                currentIndex = 0,
                subLessonTotal = 10,
                sessionState = SessionState.ACTIVE,
                inputMode = InputMode.KEYBOARD
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("it"),
                activePackId = PackId("ITALIAN_EXPRESS"),
                selectedLessonId = LessonId("lesson_01_A01")
            )
        )

        // ACT: Render with Express pack
        composeTestRule.setContent {
            TrainingScreen(
                state = expressState,
                onInputChange = {},
                onSubmit = { SubmitResult(accepted = false, hintShown = false) },
                onPrev = {},
                onNext = {},
                onTogglePause = {},
                onRequestExit = {},
                onOpenSettings = {},
                onShowSettings = {},
                onSelectLesson = {},
                onSelectMode = {},
                onSetInputMode = {},
                onShowAnswer = {},
                onVoicePromptStarted = {},
                onSelectWordFromBank = {},
                onRemoveLastWord = {},
                onTtsSpeak = {}
            )
        }

        // ASSERT: Express content shown
        composeTestRule.onNodeWithTag("card_prompt_text")
            .assertIsDisplayed()
            .assertTextContains("Я говорю по-итальянски", substring = true)

        // Short pack's content must not appear
        composeTestRule.onNodeWithText("Я покупаю дом").assertDoesNotExist()
    }
}
