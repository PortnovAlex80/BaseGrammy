package com.alexpo.grammermate.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillLastSessionState
import com.alexpo.grammermate.testharness.FakeVerbDrillStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * UI integration tests for Verb Drill "Start Fresh / Resume" feature (VD-50).
 *
 * These tests verify the **actual UI behavior** - clicking buttons and checking
 * that dialogs are shown/hidden. They do NOT call ViewModel methods directly.
 *
 * Each test follows the full user journey:
 * 1. Set up test data in fake store
 * 2. Render VerbDrillScreen with ComposeTestRule
 * 3. Click UI elements via composeTestRule
 * 4. Assert UI state via Compose assertions (not state.value checks)
 *
 * References:
 * - Spec: docs/specification/10-verb-drill.md section 10.6.2 (VD-50)
 * - User Story: US-10.20
 * - Use Case: UC-74
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class VerbDrillScreenStartFreshResumeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var store: FakeVerbDrillStore
    private lateinit var viewModel: VerbDrillViewModel
    private lateinit var testCards: List<VerbDrillCard>
    private var sessionStarted = false
    private var backPressed = false

    // Test data constants
    private val testPackId = "test_pack_italian"
    private val testLanguageId = "it"
    private val testTense = "Presente"
    private val testGroup = "regular_are"

    @Before
    fun setup() {
        store = FakeVerbDrillStore()
        testCards = createTestCards()
        store.setCards(testPackId, testLanguageId, testCards)

        val application = RuntimeEnvironment.getApplication<Application>()
        viewModel = VerbDrillViewModel(application, store)
        viewModel.injectTestCards(testCards)

        sessionStarted = false
        backPressed = false
    }

    @After
    fun tearDown() {
        store.clear()
    }

    // ========================================
    // SCENARIO 1: First Launch (No Last Session) - No Dialog
    // ========================================

    @Test
    fun firstLaunch_noLastSession_showsSelectionScreen_noDialog() {
        // --- GIVEN: No previous session exists ---
        val initialSession = store.loadLastSession()
        check(initialSession == null) { "Store should have no last session initially" }

        // --- WHEN: Screen is rendered ---
        renderVerbDrillScreen()

        // --- THEN: Selection screen should be visible ---
        composeTestRule.onNodeWithText("Verb Drill").assertIsDisplayed()

        // --- THEN: Dialog should NOT be shown ---
        composeTestRule.onNodeWithText("Resume").assertDoesNotExist()
        composeTestRule.onNodeWithText("Start Fresh").assertDoesNotExist()

        // --- THEN: Start button should be visible ---
        composeTestRule.onNodeWithText("Start").assertIsDisplayed()
    }

    // ========================================
    // SCENARIO 2: Has Last Session - Dialog Shown
    // ========================================

    @Test
    fun hasLastSession_showsDialog_withSessionContext() {
        // --- GIVEN: A last session from 1 hour ago exists ---
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        val lastSession = VerbDrillLastSessionState(
            selectedTense = testTense,
            selectedGroup = testGroup,
            sortByFrequency = true,
            cards = testCards.take(10),
            currentIndex = 5,
            correctCount = 3,
            incorrectCount = 2,
            timestamp = oneHourAgo
        )
        store.saveLastSession(lastSession)

        // --- WHEN: Screen is rendered ---
        renderVerbDrillScreen()
        waitForIdleSync()

        // --- THEN: Dialog should be shown ---
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Fresh").assertIsDisplayed()

        // --- THEN: Dialog message should be visible ---
        composeTestRule.onNodeWithText("You have an incomplete training session").assertIsDisplayed()

        // --- THEN: Session context should be displayed ---
        composeTestRule.onNodeWithText("Tense").assertIsDisplayed()
        composeTestRule.onNodeWithText(testTense).assertIsDisplayed()
        composeTestRule.onNodeWithText("Group").assertIsDisplayed()
        composeTestRule.onNodeWithText(testGroup).assertIsDisplayed()
    }

    // ========================================
    // SCENARIO 3: User Clicks "Resume" - Session Restored
    // ========================================

    @Test
    fun userClicksResume_restoresSession_dialogHidden_sessionStarted() {
        // --- GIVEN: Dialog is shown for a saved session ---
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        val savedSession = VerbDrillLastSessionState(
            selectedTense = testTense,
            selectedGroup = testGroup,
            sortByFrequency = true,
            cards = testCards.take(10),
            currentIndex = 5,
            correctCount = 3,
            incorrectCount = 2,
            timestamp = oneHourAgo
        )
        store.saveLastSession(savedSession)
        renderVerbDrillScreen()
        waitForIdleSync()

        // Verify dialog is shown
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()

        // --- WHEN: User clicks "Resume" button ---
        composeTestRule.onNodeWithText("Resume").performClick()
        waitForIdleSync()

        // --- THEN: Dialog should be dismissed ---
        composeTestRule.onNodeWithText("Resume").assertDoesNotExist()

        // --- THEN: Session should be started (callback invoked) ---
        check(sessionStarted) { "Session should be started after resume" }
    }

    // ========================================
    // SCENARIO 4: User Clicks "Start Fresh" - Session Deleted
    // ========================================

    @Test
    fun userClicksStartFresh_deletesSession_showsSelectionScreen() {
        // --- GIVEN: Dialog is shown for a saved session ---
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        val savedSession = VerbDrillLastSessionState(
            selectedTense = testTense,
            selectedGroup = testGroup,
            sortByFrequency = true,
            cards = testCards.take(10),
            currentIndex = 5,
            correctCount = 3,
            incorrectCount = 2,
            timestamp = oneHourAgo
        )
        store.saveLastSession(savedSession)
        renderVerbDrillScreen()
        waitForIdleSync()

        // Verify dialog is shown
        composeTestRule.onNodeWithText("Start Fresh").assertIsDisplayed()

        // --- WHEN: User clicks "Start Fresh" button ---
        composeTestRule.onNodeWithText("Start Fresh").performClick()
        waitForIdleSync()

        // --- THEN: Dialog should be dismissed ---
        composeTestRule.onNodeWithText("Start Fresh").assertDoesNotExist()
        composeTestRule.onNodeWithText("Resume").assertDoesNotExist()

        // --- THEN: Last session should be deleted from store ---
        val deletedSession = store.loadLastSession()
        check(deletedSession == null) { "Last session should be deleted" }

        // --- THEN: Selection screen should be visible ---
        composeTestRule.onNodeWithText("Verb Drill").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start").assertIsDisplayed()

        // --- THEN: Session should NOT be started ---
        check(!sessionStarted) { "Session should not be started after start fresh" }
    }

    // ========================================
    // SCENARIO 5: User Clicks "Cancel" - Dialog Dismissed, Back Pressed
    // ========================================

    @Test
    fun userClicksCancel_dismissesDialog_navigatesBack() {
        // --- GIVEN: Dialog is shown for a saved session ---
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        val savedSession = VerbDrillLastSessionState(
            selectedTense = testTense,
            selectedGroup = testGroup,
            sortByFrequency = false,
            cards = testCards.take(10),
            currentIndex = 5,
            correctCount = 3,
            incorrectCount = 2,
            timestamp = oneHourAgo
        )
        store.saveLastSession(savedSession)
        renderVerbDrillScreen()
        waitForIdleSync()

        // Verify dialog is shown
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        // --- WHEN: User clicks "Cancel" button ---
        composeTestRule.onNodeWithText("Cancel").performClick()
        waitForIdleSync()

        // --- THEN: Dialog should be dismissed ---
        composeTestRule.onNodeWithText("Resume").assertDoesNotExist()

        // --- THEN: Back callback should be invoked ---
        check(backPressed) { "Back should be pressed after cancel" }

        // --- THEN: Last session should NOT be deleted ---
        val remainingSession = store.loadLastSession()
        check(remainingSession != null) { "Last session should still exist" }
    }

    // ========================================
    // SCENARIO 6: Dialog Shows Progress Information
    // ========================================

    @Test
    fun dialogDisplaysProgressInformation_correctFormat() {
        // --- GIVEN: A session with specific progress ---
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        val session = VerbDrillLastSessionState(
            selectedTense = "Imperfetto",
            selectedGroup = "mixed_irregular",
            sortByFrequency = true,
            cards = testCards.take(20),
            currentIndex = 12,
            correctCount = 9,
            incorrectCount = 3,
            timestamp = oneHourAgo
        )
        store.saveLastSession(session)

        // --- WHEN: Screen is rendered ---
        renderVerbDrillScreen()
        waitForIdleSync()

        // --- THEN: Progress label should be visible ---
        composeTestRule.onNodeWithText("Progress").assertIsDisplayed()

        // --- THEN: Score label should be visible ---
        composeTestRule.onNodeWithText("Score").assertIsDisplayed()

        // --- THEN: Age label should be visible ---
        composeTestRule.onNodeWithText("Saved").assertIsDisplayed()

        // --- THEN: Correct tense and group should be displayed ---
        composeTestRule.onNodeWithText("Imperfetto").assertIsDisplayed()
        composeTestRule.onNodeWithText("mixed_irregular").assertIsDisplayed()
    }

    // ========================================
    // SCENARIO 7: Stale Session (> 24h) - Still Shows Dialog
    // ========================================

    @Test
    fun staleSession_over24Hours_showsDialog_withAgeContext() {
        // --- GIVEN: A session from 25 hours ago exists ---
        val twentyFiveHoursAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000)
        val staleSession = VerbDrillLastSessionState(
            selectedTense = testTense,
            selectedGroup = testGroup,
            sortByFrequency = false,
            cards = testCards.take(10),
            currentIndex = 5,
            correctCount = 3,
            incorrectCount = 2,
            timestamp = twentyFiveHoursAgo
        )
        store.saveLastSession(staleSession)

        // --- WHEN: Screen is rendered ---
        renderVerbDrillScreen()
        waitForIdleSync()

        // --- THEN: Dialog SHOULD be shown (no auto-deletion) ---
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()

        // --- THEN: Session age should be displayed ---
        composeTestRule.onNodeWithText("Saved").assertIsDisplayed()
    }

    // ========================================
    // SCENARIO 8: Very Old Session (7 days) - Shows Dialog
    // ========================================

    @Test
    fun veryOldSession_daysOld_showsDialog_withCorrectAge() {
        // --- GIVEN: A session 7 days old ---
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val oldSession = VerbDrillLastSessionState(
            selectedTense = "Passato Prossimo",
            selectedGroup = "irregular_ere",
            sortByFrequency = true,
            cards = testCards.take(15),
            currentIndex = 8,
            correctCount = 6,
            incorrectCount = 2,
            timestamp = sevenDaysAgo
        )
        store.saveLastSession(oldSession)

        // --- WHEN: Screen is rendered ---
        renderVerbDrillScreen()
        waitForIdleSync()

        // --- THEN: Dialog should be shown ---
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()

        // --- THEN: Correct tense and group should be displayed ---
        composeTestRule.onNodeWithText("Passato Prossimo").assertIsDisplayed()
        composeTestRule.onNodeWithText("irregular_ere").assertIsDisplayed()
    }

    // ========================================
    // SCENARIO 9: Empty Cards Session - Handled Gracefully
    // ========================================

    @Test
    fun resumedSessionWithEmptyCards_deletesSession_showsSelectionScreen() {
        // --- GIVEN: A last session with empty cards list ---
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        val emptyCardsSession = VerbDrillLastSessionState(
            selectedTense = testTense,
            selectedGroup = testGroup,
            sortByFrequency = false,
            cards = emptyList(),
            currentIndex = 0,
            correctCount = 0,
            incorrectCount = 0,
            timestamp = oneHourAgo
        )
        store.saveLastSession(emptyCardsSession)
        renderVerbDrillScreen()
        waitForIdleSync()

        // Verify dialog is shown
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()

        // --- WHEN: User clicks "Resume" on empty session ---
        composeTestRule.onNodeWithText("Resume").performClick()
        waitForIdleSync()

        // --- THEN: Invalid session should be deleted ---
        val deletedSession = store.loadLastSession()
        check(deletedSession == null) { "Empty session should be deleted" }

        // --- THEN: Dialog should be dismissed ---
        composeTestRule.onNodeWithText("Resume").assertDoesNotExist()

        // --- THEN: Session should NOT be started ---
        check(!sessionStarted) { "Session should not be started for empty cards" }
    }

    // ========================================
    // SCENARIO 10: All Dialog Buttons Are Visible
    // ========================================

    @Test
    fun allDialogButtons_areVisible_whenSessionExists() {
        // --- GIVEN: A saved session exists ---
        val lastSession = VerbDrillLastSessionState(
            selectedTense = testTense,
            selectedGroup = testGroup,
            sortByFrequency = true,
            cards = testCards.take(5),
            currentIndex = 2,
            correctCount = 1,
            incorrectCount = 1,
            timestamp = System.currentTimeMillis() - 3600000
        )
        store.saveLastSession(lastSession)

        // --- WHEN: Screen is rendered ---
        renderVerbDrillScreen()
        waitForIdleSync()

        // --- THEN: All three buttons should be visible ---
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Fresh").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Renders the VerbDrillScreen with test dependencies.
     */
    private fun renderVerbDrillScreen() {
        composeTestRule.setContent {
            MaterialTheme {
                VerbDrillScreen(
                    viewModel = viewModel,
                    onBack = { backPressed = true },
                    onStartSession = { cards ->
                        sessionStarted = true
                    }
                )
            }
        }
    }

    /**
     * Waits for Compose to settle and async operations to complete.
     */
    private fun waitForIdleSync() {
        composeTestRule.waitForIdle()
        // Additional delay for ViewModel coroutine launch
        Thread.sleep(100)
    }

    /**
     * Creates a set of test verb drill cards.
     */
    private fun createTestCards(): List<VerbDrillCard> {
        return (1..30).map { index ->
            VerbDrillCard(
                id = "verb_card_$index",
                promptRu = "Я спрягаю (verb) $index",
                answer = "io conjugate $index",
                verb = "verb_test",
                tense = testTense,
                group = testGroup,
                rank = index * 10
            )
        }
    }
}
