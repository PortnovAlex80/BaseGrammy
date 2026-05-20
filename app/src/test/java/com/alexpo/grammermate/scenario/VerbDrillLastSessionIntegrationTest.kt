package com.alexpo.grammermate.scenario

import android.app.Application
import com.alexpo.grammermate.AppContainer
import com.alexpo.grammermate.GrammarMateApplication
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillLastSessionState
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.VerbDrillUiState
import com.alexpo.grammermate.testharness.FakeVerbDrillStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration test that verifies the REAL user flow for VerbDrill last session persistence.
 *
 * Test scenario:
 * 1. User opens VerbDrill screen (ViewModel created, reloadForPack called)
 * 2. User selects tense/group and clicks "Start" (startSession() saves to YAML/store)
 * 3. User exits session (exitSession() updates last session state)
 * 4. User navigates away (simulated by ViewModel clearing)
 * 5. User opens VerbDrill screen again (NEW ViewModel created)
 * 6. Should see SessionCard with "Repeat/Continue" buttons (lastSessionContext loaded)
 *
 * This test DIFFERS from VerbDrillClickTest because:
 * - It uses the REAL VerbDrillViewModel (not just mocking the store)
 * - It verifies that startSession() saves to the store
 * - It verifies that reloadForPack() loads from the store
 * - It simulates the navigation-away-and-back flow with a new ViewModel instance
 *
 * References:
 * - Spec: docs/specification/10-verb-drill.md (VD-50, VD-51)
 * - Scenario: docs/specification/scenarios/scenario-07-verb-drill.md
 */
@RunWith(RobolectricTestRunner::class)
class VerbDrillLastSessionIntegrationTest {

    private lateinit var store: FakeVerbDrillStore
    private lateinit var testCards: List<VerbDrillCard>
    private lateinit var mockApplication: Application

    // Test constants
    private val testPackId = "test-pack-italian"
    private val testLanguageId = "it"
    private val testTense = "Presente"
    private val testGroup = "regular_are"

    @Before
    fun setup() {
        store = FakeVerbDrillStore()
        testCards = createTestCards()
        mockApplication = setupMockApplication()

        // Load cards into the store for the test pack
        store.setCards(testPackId, testLanguageId, testCards)
    }

    // ========================================
    // Integration Test: Full User Flow
    // ========================================

    @Test
    fun testLastSessionPersistence_FullUserFlow() {
        // ========================================
        // STEP 1: User opens VerbDrill screen
        // ========================================
        val viewModel1 = createVerbDrillViewModel(store, mockApplication)

        // Simulate: User navigates to VerbDrill, reloadForPack() is called
        // First inject cards into the ViewModel for testing
        viewModel1.injectTestCards(testCards)
        viewModel1.reloadForPack(testPackId)

        // Wait for async card loading to complete
        waitFor(viewModel1, { state -> !state.isLoading })

        // ASSERT: Cards should be loaded, no session yet
        val initialState1 = viewModel1.uiState.value
        assertFalse("Initial state should not be loading", initialState1.isLoading)
        assertTrue("Should have test cards loaded", initialState1.totalCards > 0)
        assertNull("No last session context initially", initialState1.lastSessionContext)
        assertNull("No active session initially", initialState1.session)

        // ========================================
        // STEP 2: User selects tense/group and clicks "Start"
        // ========================================
        viewModel1.selectTense(testTense)
        viewModel1.selectGroup(testGroup)

        // Verify selections
        val afterSelection = viewModel1.uiState.value
        assertEquals("Selected tense should be $testTense", testTense, afterSelection.selectedTense)
        assertEquals("Selected group should be $testGroup", testGroup, afterSelection.selectedGroup)

        // Start the session
        viewModel1.startSession()

        // ASSERT: Session should be active
        val afterStart = viewModel1.uiState.value
        assertNotNull("Session should be active after startSession()", afterStart.session)
        assertTrue("Session should have cards", afterStart.session!!.cards.isNotEmpty())

        // ASSERT: Last session should be saved to store
        val savedSession = store.loadLastSession()
        assertNotNull("Last session should be saved to store", savedSession)
        assertEquals("Saved session tense should match", testTense, savedSession!!.selectedTense)
        assertEquals("Saved session group should match", testGroup, savedSession.selectedGroup)

        // ========================================
        // STEP 3: User exits session (simulates navigation away)
        // ========================================
        viewModel1.exitSession()

        // ASSERT: Session should be cleared, but last session should still be in store
        val afterExit = viewModel1.uiState.value
        assertNull("Session should be null after exit", afterExit.session)
        assertNotNull("Last session should still be in store", store.loadLastSession())

        // ========================================
        // STEP 4: Simulate navigation away (ViewModel is cleared/destroyed)
        // ========================================
        // In real app, ViewModel would be cleared on navigation away
        // We simulate this by creating a NEW ViewModel instance

        // ========================================
        // STEP 5: User opens VerbDrill screen again (NEW ViewModel)
        // ========================================
        val viewModel2 = createVerbDrillViewModel(store, mockApplication)

        // Simulate: User navigates to VerbDrill again, reloadForPack() is called
        // Inject cards for the new ViewModel instance
        viewModel2.injectTestCards(testCards)
        viewModel2.reloadForPack(testPackId)

        // Wait for async card loading
        waitFor(viewModel2, { state -> !state.isLoading })

        // ASSERT: Cards should be loaded again
        val initialState2 = viewModel2.uiState.value
        assertFalse("Second ViewModel should not be loading", initialState2.isLoading)
        assertTrue("Second ViewModel should have cards loaded", initialState2.totalCards > 0)

        // ========================================
        // STEP 6: Verify lastSessionContext is loaded
        // ========================================
        // ASSERT: lastSessionContext should be loaded from store
        val lastSessionContext = initialState2.lastSessionContext
        assertNotNull(
            "lastSessionContext should be loaded after reloadForPack()",
            lastSessionContext
        )

        // ASSERT: lastSessionContext should match what was saved
        assertEquals(
            "lastSessionContext.selectedTense should match saved value",
            testTense,
            lastSessionContext!!.selectedTense
        )
        assertEquals(
            "lastSessionContext.selectedGroup should match saved value",
            testGroup,
            lastSessionContext.selectedGroup
        )

        // ASSERT: No active session (user needs to click "Repeat" or "Continue")
        assertNull("No active session until user clicks button", initialState2.session)

        // ========================================
        // STEP 7: Verify Repeat button behavior
        // ========================================
        viewModel2.onRepeatSession()

        val afterRepeat = viewModel2.uiState.value
        assertNotNull("Session should be active after onRepeatSession()", afterRepeat.session)
        assertTrue("Session should have cards", afterRepeat.session!!.cards.isNotEmpty())

        // Verify filters are restored
        assertEquals("Tense should be restored", testTense, afterRepeat.selectedTense)
        assertEquals("Group should be restored", testGroup, afterRepeat.selectedGroup)
    }

    @Test
    fun testLastSessionPersistence_MultipleSessionsOverwrite() {
        // ========================================
        // STEP 1: Start first session
        // ========================================
        val viewModel1 = createVerbDrillViewModel(store, mockApplication)
        viewModel1.injectTestCards(testCards)
        viewModel1.reloadForPack(testPackId)
        waitFor(viewModel1, { state -> !state.isLoading })

        viewModel1.selectTense("Presente")
        viewModel1.selectGroup("regular_are")
        viewModel1.startSession()

        // ASSERT: First session saved
        var savedSession = store.loadLastSession()
        assertEquals("First session tense should be Presente", "Presente", savedSession?.selectedTense)

        viewModel1.exitSession()

        // ========================================
        // STEP 2: Start second session with different filters
        // ========================================
        val viewModel2 = createVerbDrillViewModel(store, mockApplication)
        viewModel2.injectTestCards(testCards)
        viewModel2.reloadForPack(testPackId)
        waitFor(viewModel2, { state -> !state.isLoading })

        viewModel2.selectTense("Presente")
        viewModel2.selectGroup("regular_ere")  // Use Presente with regular_ere (both exist in test data)
        viewModel2.startSession()

        // ASSERT: Second session should overwrite first
        savedSession = store.loadLastSession()
        assertEquals("Second session tense should be Presente", "Presente", savedSession?.selectedTense)
        assertEquals("Second session group should be regular_ere", "regular_ere", savedSession?.selectedGroup)

        viewModel2.exitSession()

        // ========================================
        // STEP 3: Verify new ViewModel loads latest session
        // ========================================
        val viewModel3 = createVerbDrillViewModel(store, mockApplication)
        viewModel3.injectTestCards(testCards)
        viewModel3.reloadForPack(testPackId)
        waitFor(viewModel3, { state -> !state.isLoading })

        val lastSessionContext = viewModel3.uiState.value.lastSessionContext
        assertNotNull("Last session should be loaded", lastSessionContext)
        assertEquals("Should load latest session (Presente with regular_ere)", "Presente", lastSessionContext?.selectedTense)
        assertEquals("Should load latest session group (regular_ere)", "regular_ere", lastSessionContext?.selectedGroup)
    }

    @Test
    fun testLastSessionPersistence_StartFreshClearsSession() {
        // ========================================
        // STEP 1: Start and exit a session
        // ========================================
        val viewModel1 = createVerbDrillViewModel(store, mockApplication)
        viewModel1.injectTestCards(testCards)
        viewModel1.reloadForPack(testPackId)
        waitFor(viewModel1, { state -> !state.isLoading })

        viewModel1.selectTense(testTense)
        viewModel1.selectGroup(testGroup)
        viewModel1.startSession()
        viewModel1.exitSession()

        // ASSERT: Session should be saved
        assertNotNull("Session should be saved", store.loadLastSession())

        // ========================================
        // STEP 2: User clicks "Start Fresh"
        // ========================================
        val viewModel2 = createVerbDrillViewModel(store, mockApplication)
        viewModel2.injectTestCards(testCards)
        viewModel2.reloadForPack(testPackId)
        waitFor(viewModel2, { state -> !state.isLoading })

        // User clicks "Start Fresh"
        viewModel2.onStartFresh()

        // ASSERT: Session should be deleted from store
        assertNull("Session should be deleted after onStartFresh()", store.loadLastSession())

        // ASSERT: UI state should be cleared
        val afterStartFresh = viewModel2.uiState.value
        assertNull("Selected tense should be null", afterStartFresh.selectedTense)
        assertNull("Selected group should be null", afterStartFresh.selectedGroup)
        assertNull("Last session context should be null", afterStartFresh.lastSessionContext)

        // ========================================
        // STEP 3: Verify new ViewModel doesn't show last session
        // ========================================
        val viewModel3 = createVerbDrillViewModel(store, mockApplication)
        viewModel3.injectTestCards(testCards)
        viewModel3.reloadForPack(testPackId)
        waitFor(viewModel3, { state -> !state.isLoading })

        assertNull("No last session should be loaded", viewModel3.uiState.value.lastSessionContext)
    }

    @Test
    fun testLastSessionPersistence_CompletedSessionSavedForContinue() {
        // ========================================
        // STEP 1: Start and complete a session
        // ========================================
        val viewModel = createVerbDrillViewModel(store, mockApplication)
        viewModel.injectTestCards(testCards)
        viewModel.reloadForPack(testPackId)
        waitFor(viewModel, { state -> !state.isLoading })

        viewModel.selectTense(testTense)
        viewModel.selectGroup(testGroup)
        viewModel.startSession()

        // Complete all cards in the session
        val session = viewModel.uiState.value.session!!
        for (i in 0 until session.cards.size) {
            viewModel.submitCorrectAnswer()
        }

        // ASSERT: Session should be complete
        val completeState = viewModel.uiState.value
        assertTrue("Session should be complete", completeState.session?.isComplete == true)

        // Wait for async persistence
        Thread.sleep(100)

        // ASSERT: Last session should remain so the completion screen can offer More/Continue
        val savedSession = store.loadLastSession()
        assertNotNull("Last session should remain after completion", savedSession)
        assertEquals("Saved session tense should match", testTense, savedSession?.selectedTense)
        assertEquals("Saved session group should match", testGroup, savedSession?.selectedGroup)
        assertEquals(
            "Completed card IDs should be saved for Continue exclusion",
            session.cards.map { it.id }.toSet(),
            savedSession?.todayShownCardIds
        )
        assertEquals(
            "Completed batch order should be saved for Repeat",
            session.cards.map { it.id },
            savedSession?.sessionCardIds
        )
        assertEquals(
            "Completed batch should save index at the end for Continue",
            session.cards.size,
            savedSession?.currentIndex
        )
    }

    // ========================================
    // Helper methods
    // ========================================

    /**
     * Creates a VerbDrillViewModel with a fake store for testing.
     */
    private fun createVerbDrillViewModel(
        testStore: VerbDrillStore,
        application: Application
    ): com.alexpo.grammermate.ui.VerbDrillViewModel {
        // Use the test constructor that accepts a fake store
        return com.alexpo.grammermate.ui.VerbDrillViewModel(application, testStore)
    }

    /**
     * Creates a mock Application for testing.
     * In Robolectric, we can use a real Application instance.
     */
    private fun setupMockApplication(): Application {
        return org.robolectric.RuntimeEnvironment.getApplication()
    }

    /**
     * Wait for a condition on the ViewModel's uiState to become true.
     * Uses simple polling with a timeout.
     */
    private fun waitFor(
        viewModel: com.alexpo.grammermate.ui.VerbDrillViewModel,
        condition: (VerbDrillUiState) -> Boolean,
        timeoutMs: Long = 5000
    ) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition(viewModel.uiState.value)) {
                return
            }
            Thread.sleep(50)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms")
    }

    /**
     * Creates a set of test verb drill cards.
     */
    private fun createTestCards(): List<VerbDrillCard> {
        val cards = mutableListOf<VerbDrillCard>()

        // Create cards for Presente tense
        for (i in 1..15) {
            cards.add(
                VerbDrillCard(
                    id = "presente_card_$i",
                    promptRu = "я говорю $i",
                    answer = "io parlo $i",
                    verb = "parlare",
                    tense = "Presente",
                    group = "regular_are",
                    rank = i
                )
            )
        }

        // Create cards for Imperfetto tense
        for (i in 1..10) {
            cards.add(
                VerbDrillCard(
                    id = "imperfetto_card_$i",
                    promptRu = "я говорил $i",
                    answer = "io parlavo $i",
                    verb = "parlare",
                    tense = "Imperfetto",
                    group = "regular_are",
                    rank = i + 100
                )
            )
        }

        // Create cards for different group
        for (i in 1..8) {
            cards.add(
                VerbDrillCard(
                    id = "ere_card_$i",
                    promptRu = "я читаю $i",
                    answer = "io leggo $i",
                    verb = "leggere",
                    tense = "Presente",
                    group = "regular_ere",
                    rank = i + 200
                )
            )
        }

        return cards
    }
}
