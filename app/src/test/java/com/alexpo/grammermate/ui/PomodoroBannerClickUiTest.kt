package com.alexpo.grammermate.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.*
import com.alexpo.grammermate.ui.screens.TrainingScreen
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TRUE UI-click test for Pomodoro timer feature.
 *
 * Tests the complete Pomodoro flow through actual UI:
 * 1. Active Pomodoro → Timer banner shows remaining time
 * 2. Pause/Resume button in banner
 * 3. Complete early → Summary screen shows stats
 * 4. Summary persists until OK clicked
 *
 * Rules:
 * - NO direct calls to PomodoroHelper or state modification
 * - All actions via Compose UI API: onNodeWithTag, performClick
 * - Verify only what's visible on screen
 */
@RunWith(AndroidJUnit4::class)
class PomodoroBannerClickUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ========================================
    // Test 1: Active Pomodoro → Banner Shows
    // ========================================

    @Test
    fun pomodoroActive_bannerShowsTimeAndStats() {
        // ARRANGE: Active Pomodoro state
        val pomodoroState = PomodoroState(
            isActive = true,
            isPaused = false,
            isComplete = false,
            totalSeconds = 1500, // 25 minutes
            remainingSeconds = 1200, // 20 minutes remaining
            baselineCorrect = 5,
            baselineIncorrect = 1
        )

        val initialState = TrainingUiState().copy(
            pomodoro = pomodoroState,
            cardSession = CardSessionState(
                currentCard = SentenceCard(
                    id = "card-1",
                    promptRu = "тест",
                    acceptedAnswers = listOf("test")
                ),
                currentIndex = 0,
                subLessonTotal = 10,
                sessionState = SessionState.ACTIVE,
                inputMode = InputMode.KEYBOARD,
                correctCount = 10, // 5 baseline + 5 current = 10 session correct
                incorrectCount = 2  // 1 baseline + 1 current = 2 session incorrect
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("en"),
                selectedLessonId = LessonId("lesson-01")
            )
        )

        // ACT: Render TrainingScreen with active Pomodoro
        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = { /* no-op */ },
                onSubmit = { SubmitResult(accepted = true, hintShown = false) },
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
                pomodoroRemainingSeconds = 1200,
                lessonTitle = null
            )
        }

        // ASSERT: Pomodoro banner should be displayed
        composeTestRule.onNodeWithTag("pomodoro_banner").assertIsDisplayed()

        // ASSERT: Pause/Resume button should be displayed
        composeTestRule.onNodeWithTag("pomodoro_pause_resume").assertIsDisplayed()

        // ASSERT: Time text should be visible (20:00 format)
        composeTestRule.onNodeWithText("20:00").assertIsDisplayed()

        // ASSERT: Session stats should be shown
        // Session correct = 10 - 5 = 5, Session incorrect = 2 - 1 = 1
        // Total cards = 6, Success rate = 5/6 = 83%
        composeTestRule.onNodeWithText("6 cards").assertIsDisplayed()
    }

    // ========================================
    // Test 2: Pause/Resume Button Click
    // ========================================

    @Test
    fun pomodoroPauseResume_buttonTogglesState() {
        // ARRANGE: Active Pomodoro
        var isPaused = false
        var pauseResumeClicked = false

        val pomodoroState = PomodoroState(
            isActive = true,
            isPaused = false,
            isComplete = false,
            totalSeconds = 1500,
            remainingSeconds = 1200,
            baselineCorrect = 5,
            baselineIncorrect = 1
        )

        val initialState = TrainingUiState().copy(
            pomodoro = pomodoroState,
            cardSession = CardSessionState(
                currentCard = SentenceCard(
                    id = "card-1",
                    promptRu = "тест",
                    acceptedAnswers = listOf("test")
                ),
                currentIndex = 0,
                subLessonTotal = 10,
                sessionState = SessionState.ACTIVE,
                inputMode = InputMode.KEYBOARD,
                correctCount = 10,
                incorrectCount = 2
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("en"),
                selectedLessonId = LessonId("lesson-01")
            )
        )

        // ACT: Render TrainingScreen
        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = { /* no-op */ },
                onSubmit = { SubmitResult(accepted = true, hintShown = false) },
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
                onPausePomodoro = {
                    isPaused = true
                    pauseResumeClicked = true
                },
                onResumePomodoro = {
                    isPaused = false
                    pauseResumeClicked = true
                },
                pomodoroRemainingSeconds = 1200
            )
        }

        // ASSERT: Initial state - not paused
        assertFalse("Should not be paused initially", isPaused)

        // ACT: Click pause/resume button
        composeTestRule.onNodeWithTag("pomodoro_pause_resume").performClick()

        // ASSERT: Pause callback should be invoked
        assertTrue("Pause callback should be invoked", pauseResumeClicked)
        assertTrue("Should be paused after click", isPaused)
    }

    // ========================================
    // Test 3: Complete Early → Summary Shows
    // ========================================

    @Test
    @Ignore("Phase 0 quarantine — summary component not displayed, reopened in Phase 2 (legacy-test-quarantine.md)")
    fun pomodoroCompleteEarly_summaryShowsStats() {
        // ARRANGE: Pomodoro completed early (e.g., after 15 minutes)
        val pomodoroState = PomodoroState(
            isActive = false,
            isPaused = false,
            isComplete = true,
            totalSeconds = 1500,
            remainingSeconds = 600, // 10 minutes remaining (completed early)
            baselineCorrect = 12,
            baselineIncorrect = 3
        )

        val initialState = TrainingUiState().copy(
            pomodoro = pomodoroState,
            cardSession = CardSessionState(
                currentCard = null, // No cards (complete)
                currentIndex = 20,
                subLessonTotal = 20,
                sessionState = SessionState.PAUSED,
                correctCount = 20,
                incorrectCount = 5,
                currentStreak = 5,
                todayFireCount = 2
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("en"),
                selectedLessonId = LessonId("lesson-01")
            )
        )

        var onDoneClicked = false

        // ACT: Render TrainingScreen with completed Pomodoro
        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = { /* no-op */ },
                onSubmit = { SubmitResult(accepted = true, hintShown = false) },
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
                onCancelPomodoro = { onDoneClicked = true }
            )
        }

        // ASSERT: Summary screen should be displayed
        composeTestRule.onNodeWithTag("pomodoro_summary").assertIsDisplayed()

        // ASSERT: Completion message should be shown
        composeTestRule.onNodeWithText("Session Complete!").assertIsDisplayed()

        // ASSERT: Early completion message should be shown
        composeTestRule.onNodeWithText("Early completion!").assertIsDisplayed()

        // ASSERT: Stats should be displayed
        // Session correct = 20 - 12 = 8, Total = 20, Success rate = 40%
        composeTestRule.onNodeWithText("20 cards").assertIsDisplayed()
        composeTestRule.onNodeWithText("40%").assertIsDisplayed()

        // ASSERT: Streak info should be shown
        composeTestRule.onNodeWithText("5 day streak!").assertIsDisplayed()

        // ACT: Click Done button
        composeTestRule.onNodeWithTag("pomodoro_done_button").performClick()

        // ASSERT: Callback invoked
        assertTrue("Done callback should be invoked", onDoneClicked)
    }

    // ========================================
    // Test 4: Summary Persists Until OK Clicked
    // ========================================

    @Test
    @Ignore("Phase 0 quarantine — summary component not displayed, reopened in Phase 2 (legacy-test-quarantine.md)")
    fun pomodoroSummary_persistsUntilOkClicked() {
        // ARRANGE: Completed Pomodoro with summary
        val pomodoroState = PomodoroState(
            isActive = false,
            isPaused = false,
            isComplete = true,
            totalSeconds = 1500,
            remainingSeconds = 300, // 5 minutes remaining
            baselineCorrect = 8,
            baselineIncorrect = 2
        )

        val initialState = TrainingUiState().copy(
            pomodoro = pomodoroState,
            cardSession = CardSessionState(
                currentCard = null,
                currentIndex = 15,
                subLessonTotal = 15,
                sessionState = SessionState.PAUSED,
                correctCount = 15,
                incorrectCount = 4
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("en"),
                selectedLessonId = LessonId("lesson-01")
            )
        )

        var onDoneClicked = false

        // ACT: Render TrainingScreen
        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = { /* no-op */ },
                onSubmit = { SubmitResult(accepted = true, hintShown = false) },
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
                onCancelPomodoro = { onDoneClicked = true }
            )
        }

        // ASSERT: Summary should be visible
        composeTestRule.onNodeWithTag("pomodoro_summary").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pomodoro_done_button").assertIsDisplayed()

        // ASSERT: Done button NOT clicked yet
        assertFalse("Done should not be clicked yet", onDoneClicked)

        // ACT: Click Done button
        composeTestRule.onNodeWithTag("pomodoro_done_button").performClick()

        // ASSERT: Done callback invoked
        assertTrue("Done callback should be invoked", onDoneClicked)
    }

    // ========================================
    // Test 5: No Banner When Pomodoro Inactive
    // ========================================

    @Test
    fun pomodoroInactive_noBannerShown() {
        // ARRANGE: No active Pomodoro
        val initialState = TrainingUiState().copy(
            pomodoro = PomodoroState(
                isActive = false,
                isPaused = false,
                isComplete = false,
                totalSeconds = 1500,
                remainingSeconds = 1500,
                baselineCorrect = 0,
                baselineIncorrect = 0
            ),
            cardSession = CardSessionState(
                currentCard = SentenceCard(
                    id = "card-1",
                    promptRu = "тест",
                    acceptedAnswers = listOf("test")
                ),
                currentIndex = 0,
                subLessonTotal = 10,
                sessionState = SessionState.ACTIVE,
                inputMode = InputMode.KEYBOARD
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("en"),
                selectedLessonId = LessonId("lesson-01")
            )
        )

        // ACT: Render TrainingScreen without Pomodoro
        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = { /* no-op */ },
                onSubmit = { SubmitResult(accepted = true, hintShown = false) },
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
                pomodoroRemainingSeconds = 1500
            )
        }

        // ASSERT: Pomodoro banner should NOT be displayed
        composeTestRule.onNodeWithTag("pomodoro_banner").assertDoesNotExist()
    }

    // ========================================
    // Test 6: Full Session Completion (No Time Remaining)
    // ========================================

    @Test
    fun pomodoroFullCompletion_noEarlyCompletionMessage() {
        // ARRANGE: Pomodoro completed with no time remaining
        val pomodoroState = PomodoroState(
            isActive = false,
            isPaused = false,
            isComplete = true,
            totalSeconds = 1500,
            remainingSeconds = 0, // No time remaining (full completion)
            baselineCorrect = 20,
            baselineIncorrect = 5
        )

        val initialState = TrainingUiState().copy(
            pomodoro = pomodoroState,
            cardSession = CardSessionState(
                currentCard = null,
                currentIndex = 25,
                subLessonTotal = 25,
                sessionState = SessionState.PAUSED,
                correctCount = 25,
                incorrectCount = 5,
                currentStreak = 10
            ),
            navigation = NavigationState(
                selectedLanguageId = LanguageId("en"),
                selectedLessonId = LessonId("lesson-01")
            )
        )

        // ACT: Render TrainingScreen
        composeTestRule.setContent {
            TrainingScreen(
                state = initialState,
                onInputChange = { /* no-op */ },
                onSubmit = { SubmitResult(accepted = true, hintShown = false) },
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
                onCancelPomodoro = { /* no-op */ }
            )
        }

        // ASSERT: Summary should be displayed
        composeTestRule.onNodeWithTag("pomodoro_summary").assertIsDisplayed()

        // ASSERT: "Early completion!" should NOT be shown (no early completion)
        composeTestRule.onNodeWithText("Early completion!").assertDoesNotExist()

        // ASSERT: Session Complete message should still be shown
        composeTestRule.onNodeWithText("Session Complete!").assertIsDisplayed()
    }
}
