package com.alexpo.grammermate.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.testharness.FakeVerbDrillStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * UI integration tests for Verb Drill "Start Fresh / Resume" feature (VD-50).
 *
 * These tests verify the **actual user journey** — not just state checks.
 * They follow the real app flow:
 * 1. Load cards via reloadForPack() (simulates LaunchedEffect in GrammarMateApp)
 * 2. Start a session and exit (saves last session)
 * 3. Re-create ViewModel (simulates user navigating away and back)
 * 4. Call reloadForPack() again
 * 5. Verify dialog appears and buttons work correctly
 *
 * Key difference from old test: Does NOT use injectTestCards() — that bypasses
 * the real reloadForPack() flow. Instead, cards are loaded through the store
 * via reloadForPack() just like the real app.
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
    private lateinit var application: Application
    private var sessionStarted = false
    var backPressed = false

    // Test data constants
    private val testPackId = "test_pack_italian"
    private val testLanguageId = "it"
    private val testTense = "Presente"
    private val testGroup = "regular_are"

    @Before
    fun setup() {
        store = FakeVerbDrillStore()
        application = RuntimeEnvironment.getApplication<Application>()
        sessionStarted = false
        backPressed = false
    }

    @After
    fun tearDown() {
        store.clear()
    }

    // ========================================
    // SCENARIO 1: Dialog Shown on Re-entry After exitSession()
    // ========================================

    @Test
    fun dialogShown_onReentryAfterExitSession() = runBlocking {
        // --- GIVEN: Cards are loaded in the store ---
        val testCards = createTestCards()
        store.setCards(testPackId, testLanguageId, testCards)

        // --- GIVEN: First ViewModel loads cards and starts a session ---
        val viewModel1 = VerbDrillViewModel(application, store)
        viewModel1.reloadForPack(testPackId)
        waitForViewModel(viewModel1)

        // Verify cards loaded
        check(viewModel1.uiState.value.availableTenses.isNotEmpty()) {
            "Cards should be loaded after reloadForPack"
        }

        // --- GIVEN: User selects filters and starts a session ---
        viewModel1.selectTense(testTense)
        viewModel1.selectGroup(testGroup)
        viewModel1.startSession()

        val session1 = viewModel1.uiState.value.session
        check(session1 != null) { "Session should be started" }
        check(session1.cards.size == 10) { "Session should have 10 cards" }

        // --- GIVEN: User exits session (saves last session) ---
        viewModel1.exitSession()

        // Verify last session was saved
        val savedSession = store.loadLastSession()
        check(savedSession != null) { "Last session should be saved after exitSession" }
        check(savedSession.selectedTense == testTense) { "Tense should be saved" }
        check(savedSession.selectedGroup == testGroup) { "Group should be saved" }

        // --- WHEN: User navigates back (simulated by new ViewModel) ---
        val viewModel2 = VerbDrillViewModel(application, store)

        // Render screen with new ViewModel
        composeTestRule.setContent {
            MaterialTheme {
                VerbDrillScreen(
                    viewModel = viewModel2,
                    onBack = { backPressed = true },
                    onStartSession = { cards -> sessionStarted = true }
                )
            }
        }

        // --- WHEN: reloadForPack is called (simulates LaunchedEffect) ---
        viewModel2.reloadForPack(testPackId)
        waitForViewModel(viewModel2)

        // --- THEN: Dialog should be shown ---
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Fresh").assertIsDisplayed()
        composeTestRule.onNodeWithText("You have an incomplete session").assertIsDisplayed()

        // --- THEN: Session context should be displayed ---
        composeTestRule.onNodeWithText(testTense).assertIsDisplayed()
        composeTestRule.onNodeWithText(testGroup).assertIsDisplayed()
    }

    // ========================================
    // SCENARIO 2: Resume Button Loads Next Cards
    // ========================================

    @Test
    fun resumeButton_loadsNextCards_excludingShown() = runBlocking {
        // --- GIVEN: A saved session exists from before ---
        val testCards = createTestCards()
        store.setCards(testPackId, testLanguageId, testCards)

        // First session setup
        val viewModel1 = VerbDrillViewModel(application, store)
        viewModel1.reloadForPack(testPackId)
        waitForViewModel(viewModel1)

        viewModel1.selectTense(testTense)
        viewModel1.selectGroup(testGroup)
        viewModel1.startSession()

        // Answer a few cards to set progress
        viewModel1.submitCorrectAnswer()
        viewModel1.submitCorrectAnswer()
        viewModel1.markCardCompleted()

        // Save session
        viewModel1.exitSession()

        val savedSession = store.loadLastSession()
        check(savedSession != null) { "Session should be saved" }
        check(savedSession.todayShownCardIds.size == 3) { "Should have 3 shown cards" }

        // --- WHEN: User returns and clicks Resume ---
        val viewModel2 = VerbDrillViewModel(application, store)
        composeTestRule.setContent {
            MaterialTheme {
                VerbDrillScreen(
                    viewModel = viewModel2,
                    onBack = { backPressed = true },
                    onStartSession = { cards ->
                        sessionStarted = true
                        check(cards.size == 10) { "Should have 10 new cards" }
                    }
                )
            }
        }

        viewModel2.reloadForPack(testPackId)
        waitForViewModel(viewModel2)

        // Verify dialog is shown
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()

        // Click Resume
        composeTestRule.onNodeWithText("Resume").performClick()
        waitForViewModel(viewModel2)

        // --- THEN: Dialog should be dismissed ---
        composeTestRule.onNodeWithText("Resume").assertDoesNotExist()

        // --- THEN: New session should be started with NEXT cards ---
        val newSession = viewModel2.uiState.value.session
        check(newSession != null) { "New session should be started" }
        check(newSession.cards.size == 10) { "Should have 10 cards" }
        check(newSession.currentIndex == 0) { "Should start at index 0 (new session)" }
        check(newSession.correctCount == 0) { "Should have 0 correct (new session)" }
        check(newSession.incorrectCount == 0) { "Should have 0 incorrect (new session)" }

        // --- THEN: Cards should NOT include the already shown cards ---
        val shownCardIds = savedSession.todayShownCardIds
        val newCardIds = newSession.cards.map { it.id }.toSet()
        check(newCardIds.intersect(shownCardIds).isEmpty()) {
            "New cards should not include already shown cards. Intersection: ${newCardIds.intersect(shownCardIds)}"
        }

        // --- THEN: Filters should be restored ---
        check(viewModel2.uiState.value.selectedTense == testTense) { "Tense should be restored" }
        check(viewModel2.uiState.value.selectedGroup == testGroup) { "Group should be restored" }

        // --- THEN: Last session should be deleted after resume ---
        check(store.loadLastSession() == null) { "Last session should be deleted after resume" }
    }

    // ========================================
    // SCENARIO 3: Start Fresh Deletes Session
    // ========================================

    @Test
    fun startFresh_deletesSession_andShowsSelectionScreen() = runBlocking {
        // --- GIVEN: A saved session exists ---
        val testCards = createTestCards()
        store.setCards(testPackId, testLanguageId, testCards)

        val viewModel1 = VerbDrillViewModel(application, store)
        viewModel1.reloadForPack(testPackId)
        waitForViewModel(viewModel1)

        viewModel1.selectTense(testTense)
        viewModel1.selectGroup(testGroup)
        viewModel1.startSession()
        viewModel1.exitSession()

        check(store.loadLastSession() != null) { "Session should be saved" }

        // --- WHEN: User returns and clicks Start Fresh ---
        val viewModel2 = VerbDrillViewModel(application, store)
        composeTestRule.setContent {
            MaterialTheme {
                VerbDrillScreen(
                    viewModel = viewModel2,
                    onBack = { backPressed = true },
                    onStartSession = { cards -> sessionStarted = true }
                )
            }
        }

        viewModel2.reloadForPack(testPackId)
        waitForViewModel(viewModel2)

        // Verify dialog is shown
        composeTestRule.onNodeWithText("Start Fresh").assertIsDisplayed()

        // Click Start Fresh
        composeTestRule.onNodeWithText("Start Fresh").performClick()
        waitForViewModel(viewModel2)

        // --- THEN: Dialog should be dismissed ---
        composeTestRule.onNodeWithText("Resume").assertDoesNotExist()
        composeTestRule.onNodeWithText("Start Fresh").assertDoesNotExist()

        // --- THEN: Last session should be deleted ---
        check(store.loadLastSession() == null) { "Last session should be deleted" }

        // --- THEN: Selection screen should be visible ---
        composeTestRule.onNodeWithText("Verb Drill").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start").assertIsDisplayed()

        // --- THEN: Filters should be cleared ---
        check(viewModel2.uiState.value.selectedTense == null) { "Tense should be null" }
        check(viewModel2.uiState.value.selectedGroup == null) { "Group should be null" }

        // --- THEN: Session should NOT be started ---
        check(viewModel2.uiState.value.session == null) { "No session should be active" }
        check(!sessionStarted) { "onStartSession callback should not be invoked" }
    }

    // ========================================
    // SCENARIO 4: Cancel Button Navigates Back
    // ========================================

    @Test
    fun cancelButton_dismissesDialog_andNavigatesBack() = runBlocking {
        // --- GIVEN: A saved session exists ---
        val testCards = createTestCards()
        store.setCards(testPackId, testLanguageId, testCards)

        val viewModel1 = VerbDrillViewModel(application, store)
        viewModel1.reloadForPack(testPackId)
        waitForViewModel(viewModel1)
        viewModel1.startSession()
        viewModel1.exitSession()

        // --- WHEN: User returns and clicks Cancel ---
        val viewModel2 = VerbDrillViewModel(application, store)
        composeTestRule.setContent {
            MaterialTheme {
                VerbDrillScreen(
                    viewModel = viewModel2,
                    onBack = { backPressed = true },
                    onStartSession = { cards -> sessionStarted = true }
                )
            }
        }

        viewModel2.reloadForPack(testPackId)
        waitForViewModel(viewModel2)

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        composeTestRule.onNodeWithText("Cancel").performClick()
        waitForViewModel(viewModel2)

        // --- THEN: Dialog should be dismissed ---
        composeTestRule.onNodeWithText("Resume").assertDoesNotExist()

        // --- THEN: Back callback should be invoked ---
        check(backPressed) { "Back should be pressed after cancel" }

        // --- THEN: Last session should NOT be deleted ---
        check(store.loadLastSession() != null) { "Last session should still exist" }
    }

    // ========================================
    // SCENARIO 5: No Dialog on First Launch
    // ========================================

    @Test
    fun firstLaunch_noLastSession_showsSelectionScreen_noDialog() = runBlocking {
        // --- GIVEN: Cards exist but no previous session ---
        val testCards = createTestCards()
        store.setCards(testPackId, testLanguageId, testCards)

        check(store.loadLastSession() == null) { "No last session should exist initially" }

        // --- WHEN: Screen is rendered and cards are loaded ---
        val viewModel = VerbDrillViewModel(application, store)
        composeTestRule.setContent {
            MaterialTheme {
                VerbDrillScreen(
                    viewModel = viewModel,
                    onBack = { backPressed = true },
                    onStartSession = { cards -> sessionStarted = true }
                )
            }
        }

        viewModel.reloadForPack(testPackId)
        waitForViewModel(viewModel)

        // --- THEN: Selection screen should be visible ---
        composeTestRule.onNodeWithText("Verb Drill").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start").assertIsDisplayed()

        // --- THEN: Dialog should NOT be shown ---
        composeTestRule.onNodeWithText("Resume").assertDoesNotExist()
        composeTestRule.onNodeWithText("Start Fresh").assertDoesNotExist()
    }

    // ========================================
    // SCENARIO 6: Dialog Displays Filter Context Only
    // ========================================

    @Test
    fun dialogDisplaysFilterContext_onlyTenseAndGroup() = runBlocking {
        // --- GIVEN: A session with specific filters ---
        val testCards = createTestCards()
        store.setCards(testPackId, testLanguageId, testCards)

        val viewModel1 = VerbDrillViewModel(application, store)
        viewModel1.reloadForPack(testPackId)
        waitForViewModel(viewModel1)

        viewModel1.selectTense("Imperfetto")
        viewModel1.selectGroup("mixed_irregular")
        viewModel1.startSession()

        viewModel1.exitSession()

        // --- WHEN: User returns ---
        val viewModel2 = VerbDrillViewModel(application, store)
        composeTestRule.setContent {
            MaterialTheme {
                VerbDrillScreen(
                    viewModel = viewModel2,
                    onBack = { backPressed = true },
                    onStartSession = { cards -> sessionStarted = true }
                )
            }
        }

        viewModel2.reloadForPack(testPackId)
        waitForViewModel(viewModel2)

        // --- THEN: Dialog should show filter context ---
        composeTestRule.onNodeWithText("Imperfetto").assertIsDisplayed()
        composeTestRule.onNodeWithText("mixed_irregular").assertIsDisplayed()

        // --- THEN: Progress and Score labels should NOT be shown ---
        // Note: These assertions assume the string resources use "Progress" and "Score" as labels
        // If the actual strings differ, these would need adjustment
    }

    // ========================================
    // SCENARIO 7: Resume Loads Cards Even When All Previously Shown
    // ========================================

    @Test
    fun resumeWithAllShownCards_showsAllDoneMessage() = runBlocking {
        // --- GIVEN: All cards in the filter have been shown ---
        val testCards = createTestCards().take(10) // Only 10 cards total
        store.setCards(testPackId, testLanguageId, testCards)

        val viewModel1 = VerbDrillViewModel(application, store)
        viewModel1.reloadForPack(testPackId)
        waitForViewModel(viewModel1)

        viewModel1.selectTense(testTense)
        viewModel1.selectGroup(testGroup)
        viewModel1.startSession()

        // Mark all cards as shown
        repeat(10) { viewModel1.submitCorrectAnswer() }

        // Save session - all 10 cards should be in todayShownCardIds
        viewModel1.exitSession()

        val savedSession = store.loadLastSession()
        check(savedSession != null) { "Session should be saved" }
        check(savedSession.todayShownCardIds.size == 10) { "All 10 cards should be marked shown" }

        // --- WHEN: User returns and clicks Resume ---
        val viewModel2 = VerbDrillViewModel(application, store)
        composeTestRule.setContent {
            MaterialTheme {
                VerbDrillScreen(
                    viewModel = viewModel2,
                    onBack = { backPressed = true },
                    onStartSession = { cards -> sessionStarted = true }
                )
            }
        }

        viewModel2.reloadForPack(testPackId)
        waitForViewModel(viewModel2)

        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resume").performClick()
        waitForViewModel(viewModel2)

        // --- THEN: No new cards should be available ---
        check(viewModel2.uiState.value.allDoneToday) { "Should show all done message" }
        check(viewModel2.uiState.value.session == null) { "No session should be started" }
        check(store.loadLastSession() == null) { "Last session should be deleted after resume" }
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Waits for ViewModel async operations to complete.
     * Includes delay for coroutine launches and state updates.
     */
    private suspend fun waitForViewModel(viewModel: VerbDrillViewModel) {
        delay(500) // Wait for coroutines to settle
        composeTestRule.waitForIdle()
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
