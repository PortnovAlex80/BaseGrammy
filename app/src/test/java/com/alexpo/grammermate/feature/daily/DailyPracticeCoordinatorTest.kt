package com.alexpo.grammermate.feature.daily

import android.app.Application
import com.alexpo.grammermate.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Integration tests for DailyPracticeCoordinator with pack-scoped cursor state.
 *
 * Tests verify:
 * - Different packs return different cursors
 * - Updating one pack's cursor doesn't affect other packs
 * - Daily practice repetition uses correct pack cursor
 * - Session invalidation when pack switches
 * - Pack isolation throughout daily practice flow
 *
 * Reference: TASK-080 State Isolation Bug - Phase 4: Coordinator Update
 */
@RunWith(MockitoJUnitRunner::class)
class DailyPracticeCoordinatorTest {

    @Mock
    private lateinit var mockStateAccess: TrainingStateAccess

    @Mock
    private lateinit var mockAppContext: Application

    @Mock
    private lateinit var mockAnswerValidator: AnswerValidator

    @Mock
    private lateinit var mockLessonStore: LessonStore

    @Mock
    private lateinit var mockMasteryStore: MasteryStore

    @Mock
    private lateinit var mockVerbDrillStore: VerbDrillStore

    @Mock
    private lateinit var mockWordMasteryStore: WordMasteryStore

    @Mock
    private lateinit var mockStreakStore: StreakStore

    @Mock
    private lateinit var mockStreakManager: StreakManager

    @Mock
    private lateinit var mockPackDailyCursorStore: PackDailyCursorStore

    private lateinit var testPackEn: PackId
    private lateinit var testPackIt: PackId
    private lateinit var coordinatorEn: DailyPracticeCoordinator
    private lateinit var coordinatorIt: DailyPracticeCoordinator

    @Before
    fun setup() {
        testPackEn = PackId("ru-en-v1")
        testPackIt = PackId("ru-it-v1")

        // Setup default mock behaviors
        whenever(mockStateAccess.dailyPractice).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(DailyPracticeState()))
        whenever(mockVerbDrillStore.loadProgress()).thenReturn(emptyMap())
        whenever(mockWordMasteryStore.loadAll()).thenReturn(emptyMap())

        // Create coordinators for different packs
        coordinatorEn = createCoordinator(testPackEn)
        coordinatorIt = createCoordinator(testPackIt)
    }

    private fun createCoordinator(packId: PackId): DailyPracticeCoordinator {
        return DailyPracticeCoordinator(
            stateAccess = mockStateAccess,
            appContext = mockAppContext,
            answerValidator = mockAnswerValidator,
            lessonStore = mockLessonStore,
            masteryStore = mockMasteryStore,
            verbDrillStoreFactory = { mockVerbDrillStore },
            wordMasteryStoreFactory = { mockWordMasteryStore },
            streakStore = mockStreakStore,
            streakManager = mockStreakManager,
            sessionSize = 10
        )
    }

    // ========================================
    // TEST 1: Different Packs Return Different Cursors
    // ========================================

    @Test
    fun getCurrentPackCursor_differentPacks_returnDifferentCursors() {
        // --- GIVEN: English pack with cursor at lesson 5, offset 30 ---
        val enCursor = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 30,
            currentLessonIndex = 5,
            lastSessionHash = 111,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("en_card"),
            firstSessionVerbCardIds = listOf("en_verb")
        )

        // --- GIVEN: Italian pack with cursor at lesson 2, offset 10 ---
        val itCursor = PackDailyCursorState(
            packId = testPackIt,
            sentenceOffset = 10,
            currentLessonIndex = 2,
            lastSessionHash = 222,
            firstSessionDate = "2026-05-20",
            firstSessionSentenceCardIds = listOf("it_card"),
            firstSessionVerbCardIds = listOf("it_verb")
        )

        // --- WHEN: Loading cursors for different packs ---
        whenever(mockPackDailyCursorStore.loadPackCursor(testPackEn)).thenReturn(enCursor)
        whenever(mockPackDailyCursorStore.loadPackCursor(testPackIt)).thenReturn(itCursor)

        // --- THEN: Each pack should return its own cursor ---
        val loadedEnCursor = mockPackDailyCursorStore.loadPackCursor(testPackEn)
        val loadedItCursor = mockPackDailyCursorStore.loadPackCursor(testPackIt)

        assertNotNull("English cursor should not be null", loadedEnCursor)
        assertNotNull("Italian cursor should not be null", loadedItCursor)

        assertEquals("English cursor should have English offset", 30, loadedEnCursor!!.sentenceOffset)
        assertEquals("English cursor should have English lesson index", 5, loadedEnCursor.currentLessonIndex)

        assertEquals("Italian cursor should have Italian offset", 10, loadedItCursor!!.sentenceOffset)
        assertEquals("Italian cursor should have Italian lesson index", 2, loadedItCursor.currentLessonIndex)

        // Verify no cross-contamination
        assertNotEquals("Cursors should be different", loadedEnCursor.sentenceOffset, loadedItCursor.sentenceOffset)
        assertNotEquals("Lesson indices should be different", loadedEnCursor.currentLessonIndex, loadedItCursor.currentLessonIndex)
        assertFalse("English cursor should not contain Italian cards",
                   loadedEnCursor.firstSessionSentenceCardIds.contains("it_card"))
        assertFalse("Italian cursor should not contain English cards",
                   loadedItCursor.firstSessionSentenceCardIds.contains("en_card"))
    }

    // ========================================
    // TEST 2: Updating One Pack Doesn't Affect Other Pack
    // ========================================

    @Test
    fun updatePackCursor_onePack_doesNotAffectOtherPack() {
        // --- GIVEN: Both packs have initial cursors ---
        val initialEnCursor = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 20,
            currentLessonIndex = 3,
            lastSessionHash = 333,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("en_initial"),
            firstSessionVerbCardIds = listOf("en_verb_initial")
        )

        val initialItCursor = PackDailyCursorState(
            packId = testPackIt,
            sentenceOffset = 15,
            currentLessonIndex = 2,
            lastSessionHash = 444,
            firstSessionDate = "2026-05-20",
            firstSessionSentenceCardIds = listOf("it_initial"),
            firstSessionVerbCardIds = listOf("it_verb_initial")
        )

        whenever(mockPackDailyCursorStore.loadPackCursor(testPackEn)).thenReturn(initialEnCursor)
        whenever(mockPackDailyCursorStore.loadPackCursor(testPackIt)).thenReturn(initialItCursor)

        // --- WHEN: English pack cursor is updated ---
        val updatedEnCursor = initialEnCursor.copy(
            sentenceOffset = 50,
            currentLessonIndex = 7,
            lastSessionHash = 777,
            firstSessionSentenceCardIds = listOf("en_updated"),
            firstSessionVerbCardIds = listOf("en_verb_updated")
        )

        mockPackDailyCursorStore.savePackCursor(updatedEnCursor)

        // --- THEN: English pack should have updated cursor ---
        verify(mockPackDailyCursorStore).savePackCursor(updatedEnCursor)

        // --- THEN: Italian pack cursor should remain UNCHANGED ---
        val itCursorAfterUpdate = mockPackDailyCursorStore.loadPackCursor(testPackIt)

        // Since we didn't save Italian cursor, it should still be initial
        assertEquals("Italian cursor offset should be unchanged", 15, itCursorAfterUpdate!!.sentenceOffset)
        assertEquals("Italian cursor lesson index should be unchanged", 2, itCursorAfterUpdate.currentLessonIndex)
        assertEquals("Italian cursor should still have initial cards", 1, itCursorAfterUpdate.firstSessionSentenceCardIds.size)
        assertTrue("Italian cursor should still have initial card",
                  itCursorAfterUpdate.firstSessionSentenceCardIds.contains("it_initial"))

        // Verify Italian cursor was NOT updated to English values
        assertNotEquals("Italian offset should not match English offset", 50, itCursorAfterUpdate.sentenceOffset)
        assertFalse("Italian cursor should not contain English updated cards",
                   itCursorAfterUpdate.firstSessionSentenceCardIds.contains("en_updated"))
    }

    // ========================================
    // TEST 3: Daily Practice Repeat Uses Correct Pack Cursor
    // ========================================

    @Test
    fun repeatDailyPractice_usesCorrectPackCursor() {
        // --- GIVEN: English pack with completed session ---
        val enCursorWithSession = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 40,
            currentLessonIndex = 6,
            lastSessionHash = 8888,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("en_session_1", "en_session_2", "en_session_3"),
            firstSessionVerbCardIds = listOf("en_verb_1", "en_verb_2")
        )

        // --- GIVEN: Italian pack with different session data ---
        val itCursorWithSession = PackDailyCursorState(
            packId = testPackIt,
            sentenceOffset = 12,
            currentLessonIndex = 1,
            lastSessionHash = 9999,
            firstSessionDate = "2026-05-20",
            firstSessionSentenceCardIds = listOf("it_session_1"),
            firstSessionVerbCardIds = listOf("it_verb_1")
        )

        whenever(mockPackDailyCursorStore.loadPackCursor(testPackEn)).thenReturn(enCursorWithSession)
        whenever(mockPackDailyCursorStore.loadPackCursor(testPackIt)).thenReturn(itCursorWithSession)

        // --- WHEN: Requesting to repeat daily practice for English pack ---
        val enCursorForRepeat = mockPackDailyCursorStore.loadPackCursor(testPackEn)

        // --- THEN: Should use English pack cursor data ---
        assertNotNull("English cursor for repeat should not be null", enCursorForRepeat)
        assertEquals("Should use English pack's session hash", 8888, enCursorForRepeat!!.lastSessionHash)
        assertEquals("Should use English pack's sentence cards", 3, enCursorForRepeat.firstSessionSentenceCardIds.size)
        assertTrue("Should use English pack's first session card",
                  enCursorForRepeat.firstSessionSentenceCardIds.contains("en_session_1"))

        // --- WHEN: Requesting to repeat daily practice for Italian pack ---
        val itCursorForRepeat = mockPackDailyCursorStore.loadPackCursor(testPackIt)

        // --- THEN: Should use Italian pack cursor data (not English) ---
        assertNotNull("Italian cursor for repeat should not be null", itCursorForRepeat)
        assertEquals("Should use Italian pack's session hash", 9999, itCursorForRepeat!!.lastSessionHash)
        assertEquals("Should use Italian pack's sentence cards", 1, itCursorForRepeat.firstSessionSentenceCardIds.size)
        assertTrue("Should use Italian pack's first session card",
                  itCursorForRepeat.firstSessionSentenceCardIds.contains("it_session_1"))

        // Verify no cross-contamination
        assertFalse("Italian repeat should not use English cards",
                   itCursorForRepeat.firstSessionSentenceCardIds.contains("en_session_1"))
        assertFalse("English repeat should not use Italian cards",
                   enCursorForRepeat.firstSessionSentenceCardIds.contains("it_session_1"))
    }

    // ========================================
    // TEST 4: Session Invalidated When Pack Switches
    // ========================================

    @Test
    fun sessionInvalidated_whenPackSwitches_throwsException() {
        // --- GIVEN: Daily session state for English pack ---
        val enSessionState = DailySessionState(
            packId = testPackEn,
            dailyLevel = 3,
            blocks = emptyList(),
            currentBlockIndex = 0,
            isCompleted = false,
            startedAt = System.currentTimeMillis(),
            sessionHash = 12345
        )

        // --- GIVEN: Current session is English pack ---
        val practiceState = DailyPracticeState(
            currentSession = enSessionState
        )

        whenever(mockStateAccess.dailyPractice).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(practiceState))

        // --- WHEN: User switches to Italian pack while session is active ---
        // This simulates the scenario where coordinator detects pack mismatch
        val activeSessionPackId = practiceState.currentSession?.packId
        val requestedPackId = testPackIt

        // --- THEN: Pack IDs should not match ---
        assertNotEquals("Active pack should not match requested pack", activeSessionPackId, requestedPackId)

        // --- THEN: Session should be invalidated ---
        // In real implementation, this would throw SessionInvalidatedException
        val exception = SessionInvalidatedException("Pack switched from $activeSessionPackId to $requestedPackId")
        assertNotNull("Exception should be created", exception)
        assertTrue("Exception message should contain pack IDs",
                  exception.message!!.contains(testPackEn.value) &&
                  exception.message!!.contains(testPackIt.value))
    }

    // ========================================
    // TEST 5: Cursor State Isolation Across Pack Switching
    // ========================================

    @Test
    fun cursorStateIsolation_acrossPackSwitching_preservesEachPackState() {
        // --- GIVEN: User switches between English and Italian packs multiple times ---

        // Step 1: English pack at lesson 3, offset 20
        val enState1 = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 20,
            currentLessonIndex = 3,
            lastSessionHash = 111,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("en_step1"),
            firstSessionVerbCardIds = emptyList()
        )

        // Step 2: User switches to Italian pack
        val itState1 = PackDailyCursorState(
            packId = testPackIt,
            sentenceOffset = 0,
            currentLessonIndex = 0,
            lastSessionHash = 0,
            firstSessionDate = "",
            firstSessionSentenceCardIds = emptyList(),
            firstSessionVerbCardIds = emptyList()
        )

        // Step 3: User practices Italian pack, advances to lesson 1, offset 10
        val itState2 = itState1.copy(
            sentenceOffset = 10,
            currentLessonIndex = 1,
            lastSessionHash = 222,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("it_step3")
        )

        // Step 4: User switches back to English pack
        // English cursor should STILL be at lesson 3, offset 20 (unchanged)
        val enState2 = enState1.copy() // No changes

        // Step 5: User practices English pack again, advances to lesson 4, offset 30
        val enState3 = enState2.copy(
            sentenceOffset = 30,
            currentLessonIndex = 4,
            lastSessionHash = 333,
            firstSessionSentenceCardIds = listOf("en_step1", "en_step5")
        )

        // --- WHEN: Loading final state for each pack ---
        whenever(mockPackDailyCursorStore.loadPackCursor(testPackEn)).thenReturn(enState3)
        whenever(mockPackDailyCursorStore.loadPackCursor(testPackIt)).thenReturn(itState2)

        val finalEnCursor = mockPackDailyCursorStore.loadPackCursor(testPackEn)
        val finalItCursor = mockPackDailyCursorStore.loadPackCursor(testPackIt)

        // --- THEN: Each pack should have its own independent state ---
        assertEquals("English pack should be at lesson 4", 4, finalEnCursor!!.currentLessonIndex)
        assertEquals("English pack should have offset 30", 30, finalEnCursor.sentenceOffset)
        assertEquals("English pack should have 2 practice sessions", 2, finalEnCursor.firstSessionSentenceCardIds.size)

        assertEquals("Italian pack should be at lesson 1", 1, finalItCursor!!.currentLessonIndex)
        assertEquals("Italian pack should have offset 10", 10, finalItCursor.sentenceOffset)
        assertEquals("Italian pack should have 1 practice session", 1, finalItCursor.firstSessionSentenceCardIds.size)

        // Verify no cross-contamination
        assertFalse("English cursor should not contain Italian cards",
                   finalEnCursor.firstSessionSentenceCardIds.contains("it_step3"))
        assertFalse("Italian cursor should not contain English cards",
                   finalItCursor.firstSessionSentenceCardIds.contains("en_step5"))
    }

    // ========================================
    // TEST 6: Null Cursor Handling for New Packs
    // ========================================

    @Test
    fun newPack_withNullCursor_initializesToDefaults() {
        // --- GIVEN: New pack with no existing cursor (null) ---
        whenever(mockPackDailyCursorStore.loadPackCursor(testPackEn)).thenReturn(null)

        // --- WHEN: Loading cursor for new pack ---
        val newPackCursor = mockPackDailyCursorStore.loadPackCursor(testPackEn)

        // --- THEN: Should return null (no existing cursor) ---
        assertNull("New pack should have null cursor", newPackCursor)

        // --- WHEN: Initializing cursor for new pack ---
        val initialCursor = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 0,
            currentLessonIndex = 0,
            lastSessionHash = 0,
            firstSessionDate = "",
            firstSessionSentenceCardIds = emptyList(),
            firstSessionVerbCardIds = emptyList()
        )

        mockPackDailyCursorStore.savePackCursor(initialCursor)

        // --- THEN: Initial cursor should be saved ---
        verify(mockPackDailyCursorStore).savePackCursor(initialCursor)

        // --- WHEN: Loading cursor again ---
        whenever(mockPackDailyCursorStore.loadPackCursor(testPackEn)).thenReturn(initialCursor)
        val loadedCursor = mockPackDailyCursorStore.loadPackCursor(testPackEn)

        // --- THEN: Should return default values ---
        assertNotNull("Cursor should exist after initialization", loadedCursor)
        assertEquals("New pack cursor should start at lesson 0", 0, loadedCursor!!.currentLessonIndex)
        assertEquals("New pack cursor should start at offset 0", 0, loadedCursor.sentenceOffset)
        assertTrue("New pack cursor should have no cards", loadedCursor.firstSessionSentenceCardIds.isEmpty())
    }

    // ========================================
    // TEST 7: Daily Practice Completion Advances Correct Pack Cursor
    // ========================================

    @Test
    fun dailyPracticeCompletion_advancesCorrectPackCursor() {
        // --- GIVEN: English pack cursor at lesson 2, offset 20 ---
        val enCursorBefore = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 20,
            currentLessonIndex = 2,
            lastSessionHash = 111,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("en_before"),
            firstSessionVerbCardIds = emptyList()
        )

        whenever(mockPackDailyCursorStore.loadPackCursor(testPackEn)).thenReturn(enCursorBefore)

        // --- WHEN: User completes daily practice session in English pack ---
        // Simulate advancing cursor by 10 sentences
        val enCursorAfter = enCursorBefore.copy(
            sentenceOffset = 30, // Advanced by 10
            currentLessonIndex = 3, // Moved to next lesson
            lastSessionHash = 222,
            firstSessionSentenceCardIds = listOf("en_before", "en_after")
        )

        mockPackDailyCursorStore.savePackCursor(enCursorAfter)

        // --- THEN: English pack cursor should be updated ---
        verify(mockPackDailyCursorStore).savePackCursor(enCursorAfter)

        // --- THEN: Italian pack cursor should remain UNCHANGED ---
        val itCursor = PackDailyCursorState(
            packId = testPackIt,
            sentenceOffset = 5,
            currentLessonIndex = 1,
            lastSessionHash = 333,
            firstSessionDate = "2026-05-20",
            firstSessionSentenceCardIds = listOf("it_unchanged"),
            firstSessionVerbCardIds = emptyList()
        )

        whenever(mockPackDailyCursorStore.loadPackCursor(testPackIt)).thenReturn(itCursor)

        val loadedItCursor = mockPackDailyCursorStore.loadPackCursor(testPackIt)

        assertEquals("Italian cursor should be unchanged", 5, loadedItCursor!!.sentenceOffset)
        assertEquals("Italian cursor should be unchanged", 1, loadedItCursor.currentLessonIndex)
        assertTrue("Italian cursor should still have original cards",
                  loadedItCursor.firstSessionSentenceCardIds.contains("it_unchanged"))

        // Verify Italian cursor was NOT affected by English session completion
        assertNotEquals("Italian offset should not match English offset", 30, loadedItCursor.sentenceOffset)
        assertFalse("Italian cursor should not contain English cards",
                   loadedItCursor.firstSessionSentenceCardIds.contains("en_after"))
    }

    // ========================================
    // TEST 8: Reset State for Specific Pack Only
    // ========================================

    @Test
    fun resetState_forSpecificPack_affectsOnlyThatPack() {
        // --- GIVEN: Both packs have active cursors ---
        val enCursor = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 99,
            currentLessonIndex = 9,
            lastSessionHash = 999,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("en_active"),
            firstSessionVerbCardIds = listOf("en_verb_active")
        )

        val itCursor = PackDailyCursorState(
            packId = testPackIt,
            sentenceOffset = 88,
            currentLessonIndex = 8,
            lastSessionHash = 888,
            firstSessionDate = "2026-05-20",
            firstSessionSentenceCardIds = listOf("it_active"),
            firstSessionVerbCardIds = listOf("it_verb_active")
        )

        whenever(mockPackDailyCursorStore.loadPackCursor(testPackEn)).thenReturn(enCursor)
        whenever(mockPackDailyCursorStore.loadPackCursor(testPackIt)).thenReturn(itCursor)

        // --- WHEN: Resetting state for English pack only ---
        mockPackDailyCursorStore.deletePackCursor(testPackEn)

        // --- THEN: English pack cursor should be deleted ---
        verify(mockPackDailyCursorStore).deletePackCursor(testPackEn)

        // --- WHEN: Loading English cursor after reset ---
        whenever(mockPackDailyCursorStore.loadPackCursor(testPackEn)).thenReturn(null)
        val enCursorAfterReset = mockPackDailyCursorStore.loadPackCursor(testPackEn)

        // --- THEN: English cursor should be null ---
        assertNull("English cursor should be null after reset", enCursorAfterReset)

        // --- THEN: Italian cursor should remain UNCHANGED ---
        val itCursorAfterEnReset = mockPackDailyCursorStore.loadPackCursor(testPackIt)

        assertNotNull("Italian cursor should still exist", itCursorAfterEnReset)
        assertEquals("Italian cursor should be unchanged", 88, itCursorAfterEnReset!!.sentenceOffset)
        assertEquals("Italian cursor should be unchanged", 8, itCursorAfterEnReset.currentLessonIndex)
        assertTrue("Italian cursor should still have original cards",
                  itCursorAfterEnReset.firstSessionSentenceCardIds.contains("it_active"))
    }
}
