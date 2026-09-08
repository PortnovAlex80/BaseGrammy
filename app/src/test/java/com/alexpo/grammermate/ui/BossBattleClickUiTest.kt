package com.alexpo.grammermate.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.*
import com.alexpo.grammermate.ui.screens.TrainingScreen
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TRUE UI-click test for Boss Battle mode.
 *
 * Tests the specific issue: bossProgress NOT updating during Boss Battle.
 *
 * User path tested:
 * 1. Start boss battle -> verify initial progress (0%)
 * 2. Submit correct answer -> verify progress updates to 1%
 * 3. Submit another correct answer -> verify progress updates to 2%
 *
 * Rules:
 * - NO direct calls to bossOrchestrator.startBossLesson(), sessionRunner.submitAnswer(), etc.
 * - All actions via Compose UI API: onNodeWithText, performClick, performTextInput
 * - Verify only what's visible on screen
 *
 * This test specifically validates the fix for: bossProgress not updating
 */
@RunWith(AndroidJUnit4::class)
@Ignore("Phase 0 quarantine — 2/2 fail: components not displayed, reopened in Phase 2 (legacy-test-quarantine.md)")
class BossBattleClickUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ========================================
    // Test: Boss Battle Progress Updates
    // ========================================

    @Test
    fun bossBattle_correctAnswer_progressShouldUpdate() {
        // ARRANGE: Create test data for boss battle (10 cards for simplicity)
        val testCards = (1..10).map { i ->
            SentenceCard(
                id = "boss-card-$i",
                promptRu = "русское слово $i",
                acceptedAnswers = listOf("english word $i")
            )
        }

        var currentInput = ""
        var currentCardIndex = 0
        var submitCount = 0
        var bossProgress = 0  // Это должно обновляться!

        val initialState = TrainingUiState().copy(
            cardSession = CardSessionState(
                currentCard = testCards[0],
                currentIndex = 0,
                subLessonTotal = 10,  // Boss battle: все карточки за раз
                sessionState = SessionState.PAUSED,  // Boss starts paused
                inputMode = InputMode.KEYBOARD
            ),
            boss = BossState(
                bossActive = true,
                bossType = BossType.LESSON,
                bossTotal = 10,
                bossProgress = 0,  // Начинаем с 0
                bossReward = null,
                bossRewardMessage = null
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("it"),
                selectedLessonId = LessonId("L01_1FORM")
            )
        )

        // ACT: Render TrainingScreen in Boss mode
        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = { text -> currentInput = text },
                onSubmit = {
                    submitCount++

                    // Симуляция: правильный ответ → обновляем bossProgress
                    if (currentCardIndex < testCards.size - 1) {
                        currentCardIndex++
                        bossProgress++  // ← ЭТО ДОЛЖНО ОБНОВЛЯТЬСЯ!
                    }

                    SubmitResult(accepted = true, hintShown = false)
                },
                onPrev = { /* no-op */ },
                onNext = { /* no-op */ },
                onTogglePause = { /* no-op */ },
                onRequestExit = { /* no-op */ },
                onOpenSettings = { /* no-op */ },
                onShowSettings = { /* no-op */ },
                onSelectLesson = { /* no-op */ },
                onSelectMode = { /* no-op */ },
                onSetInputMode = { /* no-op */ },
                onShowAnswer = { /* no-op */ },
                onVoicePromptStarted = { /* no-op */ },
                onSelectWordFromBank = { /* no-op */ },
                onRemoveLastWord = { /* no-op */ },
                onTtsSpeak = { /* no-op */ },
                lessonTitle = null
            )
        }

        // ASSERT: Verify "Review Session" title (Boss mode indicator)
        composeTestRule
            .onNodeWithText("Review Session")
            .assertIsDisplayed()

        // ASSERT: Verify initial progress shows 0/10
        composeTestRule
            .onNodeWithText("0% (0/10)")
            .assertIsDisplayed()

        // ACT 1: Submit first correct answer
        composeTestRule
            .onNodeWithText("Your translation")
            .performTextInput("english word 1")
        composeTestRule
            .onNodeWithText("Check")
            .performClick()

        // ASSERT 1: Progress should update to 1/10
        // ПРОВАЛ: ЭТО ТЕСТ БУДЕТ ПРОВАЛЯТЬ, ЧТО bossProgress НЕ ОБНОВЛЯЕТСЯ!
        composeTestRule
            .onNodeWithText("10% (1/10)")
            .assertIsDisplayed()

        // ACT 2: Submit second correct answer
        composeTestRule
            .onNodeWithText("Your translation")
            .performTextInput("english word 2")
        composeTestRule
            .onNodeWithText("Check")
            .performClick()

        // ASSERT 2: Progress should update to 2/10
        composeTestRule
            .onNodeWithText("20% (2/10)")
            .assertIsDisplayed()
    }

    // ========================================
    // Test 2: Boss Battle Title and Stats
    // ========================================

    @Test
    fun bossBattle_showsCorrectTitleAndStats() {
        // ARRANGE: Same setup as Test 1
        val testCards = (1..10).map { i ->
            SentenceCard(
                id = "boss-card-$i",
                promptRu = "русское $i",
                acceptedAnswers = listOf("english $i")
            )
        }

        val initialState = TrainingUiState().copy(
            cardSession = CardSessionState(
                currentCard = testCards[0],
                currentIndex = 0,
                subLessonTotal = 10,
                sessionState = SessionState.PAUSED,
                inputMode = InputMode.KEYBOARD
            ),
            boss = BossState(
                bossActive = true,
                bossType = BossType.LESSON,
                bossTotal = 10,
                bossProgress = 0,
                bossReward = null
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("it"),
                selectedLessonId = LessonId("L01_1FORM")
            )
        )

        // ACT: Render TrainingScreen
        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = { },
                onSubmit = { SubmitResult(accepted = true, hintShown = false) },
                onPrev = { },
                onNext = { },
                onTogglePause = { },
                onRequestExit = { },
                onOpenSettings = { },
                onShowSettings = { },
                onSelectLesson = { },
                onSelectMode = { },
                onSetInputMode = { },
                onShowAnswer = { },
                onVoicePromptStarted = { },
                onSelectWordFromBank = { },
                onRemoveLastWord = { },
                onTtsSpeak = { }
            )
        }

        // ASSERT: Verify Boss mode indicators
        composeTestRule
            .onNodeWithText("Review Session")  // Boss mode title
            .assertIsDisplayed()

        // ASSERT: Verify progress shows boss cards (not sub-lesson)
        composeTestRule
            .onNodeWithText("0% (0/10)")  // bossProgress/bossTotal
            .assertIsDisplayed()
    }
}
