package com.alexpo.grammermate.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
                inputMode = InputMode.KEYBOARD,
                // The boss header renders only in BOSS screen modes
                screenMode = com.alexpo.grammermate.data.TrainingScreenMode.BOSS
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

        // ACT: Render TrainingScreen in Boss mode. Boss progress must live in
        // a remembered state so each accepted submission recomposes the
        // screen with the updated boss counter.
        composeTestRule.setContent {
            var liveState by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(initialState)
            }
            TrainingScreen(
                state = liveState,
                onInputChange = { text -> currentInput = text },
                onSubmit = {
                    submitCount++

                    // Симуляция: правильный ответ → обновляем bossProgress
                    if (currentCardIndex < testCards.size - 1) {
                        currentCardIndex++
                        bossProgress++
                    }
                    liveState = liveState.copy(
                        cardSession = liveState.cardSession.copy(
                            // advance the card so the next typed answer exact-matches
                            currentCard = testCards[currentCardIndex.coerceIn(testCards.indices)]
                        ),
                        boss = (liveState.boss ?: initialState.boss).copy(bossProgress = bossProgress)
                    )

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

        // ASSERT: initial progress renders "current+1 / total"
        composeTestRule
            .onNodeWithText("1 / 10")
            .performScrollTo()
            .assertIsDisplayed()

        // ACT 1: Submit first correct answer (exact match auto-submits on type)
        composeTestRule
            .onNodeWithTag("input_field")
            .performTextInput("english word 1")
        composeTestRule.waitForIdle()

        // ASSERT 1: progress advanced to card 2 of 10
        if (submitCount != 1) {
            throw AssertionError("expected 1 submit, got $submitCount (boss=$bossProgress)")
        }
        composeTestRule
            .onNodeWithText("2 / 10")
            .performScrollTo()
            .assertIsDisplayed()

        // ACT 2: Submit second correct answer
        composeTestRule
            .onNodeWithTag("input_field")
            .performTextInput("english word 2")
        composeTestRule.waitForIdle()

        // ASSERT 2: progress advanced to card 3 of 10
        composeTestRule
            .onNodeWithText("3 / 10")
            .performScrollTo()
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
                inputMode = InputMode.KEYBOARD,
                // The boss header renders only in BOSS screen modes
                screenMode = com.alexpo.grammermate.data.TrainingScreenMode.BOSS
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

        // ASSERT: progress renders "current / total" (boss branch)
        composeTestRule
            .onNodeWithText("1 / 10")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
