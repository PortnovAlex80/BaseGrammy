package com.alexpo.grammermate.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Unit tests for PackDailyCursorStore implementation.
 *
 * Tests verify:
 * - File-based persistence per pack
 * - CRUD operations for pack-scoped cursor state
 * - File isolation between different packs
 * - Proper YAML serialization/deserialization
 *
 * Reference: TASK-080 State Isolation Bug
 */
@RunWith(RobolectricTestRunner::class)
class PackDailyCursorStoreTest {

    private lateinit var context: android.content.Context
    private lateinit var grammarmateDir: File
    private lateinit var testPackEn: PackId
    private lateinit var testPackIt: PackId

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        grammarmateDir = File(context.filesDir, "grammarmate")

        // Clean up any test files from previous runs
        cleanupTestFiles()

        // Test pack IDs
        testPackEn = PackId("ru-en-v1")
        testPackIt = PackId("ru-it-v1")
    }

    private fun cleanupTestFiles() {
        // Delete all test daily cursor files
        grammarmateDir.listFiles { file ->
            file.name.startsWith("daily_cursor_") && file.name.endsWith(".yaml")
        }?.forEach { it.delete() }
    }

    // ========================================
    // TEST 1: Load Non-Existent Pack Returns Null
    // ========================================

    @Test
    fun loadPackCursor_nonExistentPack_returnsNull() {
        // --- GIVEN: Store for pack with no existing cursor file ---
        val store = PackDailyCursorStoreImpl(context, testPackEn)

        // --- WHEN: Attempting to load cursor for non-existent pack ---
        val result = store.loadPackCursor(testPackEn)

        // --- THEN: Should return null (no file exists) ---
        assertNull("Loading cursor for non-existent pack should return null", result)

        // --- THEN: No file should be created ---
        val expectedFile = File(grammarmateDir, "daily_cursor_${testPackEn.value}.yaml")
        assertFalse("No file should be created for non-existent pack", expectedFile.exists())
    }

    // ========================================
    // TEST 2: Save and Load Returns Same Data
    // ========================================

    @Test
    fun savePackCursor_thenLoad_returnsSame() {
        // --- GIVEN: Store for English pack ---
        val store = PackDailyCursorStoreImpl(context, testPackEn)

        val originalCursor = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 30,
            currentLessonIndex = 5,
            lastSessionHash = 12345,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("card1", "card2", "card3"),
            firstSessionVerbCardIds = listOf("verb1", "verb2")
        )

        // --- WHEN: Cursor is saved ---
        store.savePackCursor(originalCursor)

        // --- THEN: File should exist ---
        val expectedFile = File(grammarmateDir, "daily_cursor_${testPackEn.value}.yaml")
        assertTrue("Cursor file should be created", expectedFile.exists())
        assertTrue("Cursor file should not be empty", expectedFile.length() > 0)

        // --- WHEN: Cursor is loaded ---
        val loadedCursor = store.loadPackCursor(testPackEn)

        // --- THEN: Loaded data should match original ---
        assertNotNull("Loaded cursor should not be null", loadedCursor)
        assertEquals("Pack ID should match", testPackEn, loadedCursor!!.packId)
        assertEquals("Sentence offset should match", 30, loadedCursor.sentenceOffset)
        assertEquals("Current lesson index should match", 5, loadedCursor.currentLessonIndex)
        assertEquals("Last session hash should match", 12345, loadedCursor.lastSessionHash)
        assertEquals("First session date should match", "2026-05-21", loadedCursor.firstSessionDate)
        assertEquals("First session sentence card IDs should match",
                    listOf("card1", "card2", "card3"), loadedCursor.firstSessionSentenceCardIds)
        assertEquals("First session verb card IDs should match",
                    listOf("verb1", "verb2"), loadedCursor.firstSessionVerbCardIds)
    }

    // ========================================
    // TEST 3: Save Overwrites Existing Data
    // ========================================

    @Test
    fun savePackCursor_overwritesExisting() {
        // --- GIVEN: Store with existing cursor data ---
        val store = PackDailyCursorStoreImpl(context, testPackEn)

        val originalCursor = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 10,
            currentLessonIndex = 2,
            lastSessionHash = 111,
            firstSessionDate = "2026-05-20",
            firstSessionSentenceCardIds = listOf("old1"),
            firstSessionVerbCardIds = listOf("old_verb1")
        )

        store.savePackCursor(originalCursor)

        // --- WHEN: New cursor data is saved for same pack ---
        val updatedCursor = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 50,
            currentLessonIndex = 8,
            lastSessionHash = 99999,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("new1", "new2", "new3", "new4"),
            firstSessionVerbCardIds = listOf("new_verb1", "new_verb2", "new_verb3")
        )

        store.savePackCursor(updatedCursor)

        // --- THEN: Loaded data should reflect the update (not the original) ---
        val loadedCursor = store.loadPackCursor(testPackEn)

        assertNotNull("Loaded cursor should not be null", loadedCursor)
        assertEquals("Sentence offset should be updated", 50, loadedCursor!!.sentenceOffset)
        assertEquals("Current lesson index should be updated", 8, loadedCursor.currentLessonIndex)
        assertEquals("Last session hash should be updated", 99999, loadedCursor.lastSessionHash)
        assertEquals("First session date should be updated", "2026-05-21", loadedCursor.firstSessionDate)
        assertEquals("Sentence card IDs should be updated", 4, loadedCursor.firstSessionSentenceCardIds.size)
        assertEquals("Verb card IDs should be updated", 3, loadedCursor.firstSessionVerbCardIds.size)

        // Verify old data is gone
        assertFalse("Old sentence card ID should not exist",
                   loadedCursor.firstSessionSentenceCardIds.contains("old1"))
        assertFalse("Old verb card ID should not exist",
                   loadedCursor.firstSessionVerbCardIds.contains("old_verb1"))
    }

    // ========================================
    // TEST 4: Delete Cursor Removes File
    // ========================================

    @Test
    fun deletePackCursor_fileRemoved() {
        // --- GIVEN: Store with existing cursor file ---
        val store = PackDailyCursorStoreImpl(context, testPackEn)

        val cursor = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 20,
            currentLessonIndex = 3,
            lastSessionHash = 555,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("delete_me"),
            firstSessionVerbCardIds = listOf("verb_delete")
        )

        store.savePackCursor(cursor)

        val expectedFile = File(grammarmateDir, "daily_cursor_${testPackEn.value}.yaml")
        assertTrue("Cursor file should exist before deletion", expectedFile.exists())

        // --- WHEN: Cursor is deleted ---
        store.deletePackCursor(testPackEn)

        // --- THEN: File should be removed ---
        assertFalse("Cursor file should be deleted", expectedFile.exists())

        // --- THEN: Loading should return null ---
        val loadedCursor = store.loadPackCursor(testPackEn)
        assertNull("Loading deleted cursor should return null", loadedCursor)
    }

    // ========================================
    // TEST 5: Load All Cursors Returns Map
    // ========================================

    @Test
    fun loadAllPackCursors_returnsMap() {
        // --- GIVEN: Multiple packs with cursor data ---
        val storeEn = PackDailyCursorStoreImpl(context, testPackEn)
        val storeIt = PackDailyCursorStoreImpl(context, testPackIt)

        val cursorEn = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 100,
            currentLessonIndex = 10,
            lastSessionHash = 1111,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("en1", "en2"),
            firstSessionVerbCardIds = listOf("en_verb1")
        )

        val cursorIt = PackDailyCursorState(
            packId = testPackIt,
            sentenceOffset = 25,
            currentLessonIndex = 4,
            lastSessionHash = 2222,
            firstSessionDate = "2026-05-20",
            firstSessionSentenceCardIds = listOf("it1"),
            firstSessionVerbCardIds = listOf("it_verb1", "it_verb2")
        )

        storeEn.savePackCursor(cursorEn)
        storeIt.savePackCursor(cursorIt)

        // --- WHEN: Loading all cursors ---
        val allCursors = storeEn.loadAllPackCursors()

        // --- THEN: Should return map with both pack cursors ---
        assertNotNull("All cursors map should not be null", allCursors)
        assertEquals("Should have 2 pack cursors", 2, allCursors.size)

        // --- THEN: English pack cursor should be present ---
        assertTrue("English pack cursor should exist", allCursors.containsKey(testPackEn))
        val loadedEn = allCursors[testPackEn]
        assertNotNull("English cursor should not be null", loadedEn)
        assertEquals("English sentence offset should match", 100, loadedEn!!.sentenceOffset)
        assertEquals("English lesson index should match", 10, loadedEn.currentLessonIndex)

        // --- THEN: Italian pack cursor should be present ---
        assertTrue("Italian pack cursor should exist", allCursors.containsKey(testPackIt))
        val loadedIt = allCursors[testPackIt]
        assertNotNull("Italian cursor should not be null", loadedIt)
        assertEquals("Italian sentence offset should match", 25, loadedIt!!.sentenceOffset)
        assertEquals("Italian lesson index should match", 4, loadedIt.currentLessonIndex)
    }

    // ========================================
    // TEST 6: Pack Isolation - Different Packs Don't Interfere
    // ========================================

    @Test
    fun differentPacks_maintainSeparateCursors() {
        // --- GIVEN: Two different packs ---
        val storeEn = PackDailyCursorStoreImpl(context, testPackEn)
        val storeIt = PackDailyCursorStoreImpl(context, testPackIt)

        // --- WHEN: English pack cursor is saved ---
        val cursorEn = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 999,
            currentLessonIndex = 99,
            lastSessionHash = 888,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = listOf("en_only"),
            firstSessionVerbCardIds = listOf("en_verb_only")
        )
        storeEn.savePackCursor(cursorEn)

        // --- WHEN: Italian pack cursor is saved ---
        val cursorIt = PackDailyCursorState(
            packId = testPackIt,
            sentenceOffset = 1,
            currentLessonIndex = 0,
            lastSessionHash = 1,
            firstSessionDate = "2026-01-01",
            firstSessionSentenceCardIds = listOf("it_only"),
            firstSessionVerbCardIds = listOf("it_verb_only")
        )
        storeIt.savePackCursor(cursorIt)

        // --- THEN: Each pack should have its own file ---
        val enFile = File(grammarmateDir, "daily_cursor_${testPackEn.value}.yaml")
        val itFile = File(grammarmateDir, "daily_cursor_${testPackIt.value}.yaml")
        assertTrue("English cursor file should exist", enFile.exists())
        assertTrue("Italian cursor file should exist", itFile.exists())

        // --- THEN: Loading English cursor should not return Italian data ---
        val loadedEn = storeEn.loadPackCursor(testPackEn)
        assertEquals("English cursor should have English offset", 999, loadedEn!!.sentenceOffset)
        assertEquals("English cursor should have English cards", 1, loadedEn.firstSessionSentenceCardIds.size)
        assertFalse("English cursor should not contain Italian cards",
                   loadedEn.firstSessionSentenceCardIds.contains("it_only"))

        // --- THEN: Loading Italian cursor should not return English data ---
        val loadedIt = storeIt.loadPackCursor(testPackIt)
        assertEquals("Italian cursor should have Italian offset", 1, loadedIt!!.sentenceOffset)
        assertEquals("Italian cursor should have Italian cards", 1, loadedIt.firstSessionSentenceCardIds.size)
        assertFalse("Italian cursor should not contain English cards",
                   loadedIt.firstSessionSentenceCardIds.contains("en_only"))
    }

    // ========================================
    // TEST 7: Default Values Are Preserved
    // ========================================

    @Test
    fun savePackCursor_defaultValues_preservedCorrectly() {
        // --- GIVEN: Cursor with all default values ---
        val store = PackDailyCursorStoreImpl(context, testPackEn)

        val defaultCursor = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 0,
            currentLessonIndex = 0,
            lastSessionHash = 0,
            firstSessionDate = "",
            firstSessionSentenceCardIds = emptyList(),
            firstSessionVerbCardIds = emptyList()
        )

        // --- WHEN: Default cursor is saved ---
        store.savePackCursor(defaultCursor)

        // --- WHEN: Cursor is loaded ---
        val loadedCursor = store.loadPackCursor(testPackEn)

        // --- THEN: All defaults should be preserved ---
        assertNotNull("Loaded cursor should not be null", loadedCursor)
        assertEquals("Default sentence offset should be 0", 0, loadedCursor!!.sentenceOffset)
        assertEquals("Default lesson index should be 0", 0, loadedCursor.currentLessonIndex)
        assertEquals("Default session hash should be 0", 0, loadedCursor.lastSessionHash)
        assertEquals("Default session date should be empty", "", loadedCursor.firstSessionDate)
        assertEquals("Default sentence cards should be empty", 0, loadedCursor.firstSessionSentenceCardIds.size)
        assertEquals("Default verb cards should be empty", 0, loadedCursor.firstSessionVerbCardIds.size)
    }

    // ========================================
    // TEST 8: Large Card Lists Are Handled Correctly
    // ========================================

    @Test
    fun savePackCursor_largeCardLists_serializesCorrectly() {
        // --- GIVEN: Cursor with large card lists ---
        val store = PackDailyCursorStoreImpl(context, testPackEn)

        val largeSentenceCardList = (1..100).map { "sentence_card_$it" }
        val largeVerbCardList = (1..50).map { "verb_card_$it" }

        val cursorWithLargeLists = PackDailyCursorState(
            packId = testPackEn,
            sentenceOffset = 1000,
            currentLessonIndex = 100,
            lastSessionHash = 99999,
            firstSessionDate = "2026-05-21",
            firstSessionSentenceCardIds = largeSentenceCardList,
            firstSessionVerbCardIds = largeVerbCardList
        )

        // --- WHEN: Cursor with large lists is saved ---
        store.savePackCursor(cursorWithLargeLists)

        // --- WHEN: Cursor is loaded ---
        val loadedCursor = store.loadPackCursor(testPackEn)

        // --- THEN: All cards should be preserved ---
        assertNotNull("Loaded cursor should not be null", loadedCursor)
        assertEquals("All sentence cards should be preserved", 100, loadedCursor!!.firstSessionSentenceCardIds.size)
        assertEquals("All verb cards should be preserved", 50, loadedCursor.firstSessionVerbCardIds.size)
        assertTrue("First sentence card should match",
                  loadedCursor.firstSessionSentenceCardIds.contains("sentence_card_1"))
        assertTrue("Last sentence card should match",
                  loadedCursor.firstSessionSentenceCardIds.contains("sentence_card_100"))
        assertTrue("First verb card should match",
                  loadedCursor.firstSessionVerbCardIds.contains("verb_card_1"))
        assertTrue("Last verb card should match",
                  loadedCursor.firstSessionVerbCardIds.contains("verb_card_50"))
    }
}
