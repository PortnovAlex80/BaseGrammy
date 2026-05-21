package com.alexpo.grammermate.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.LocalDate

/**
 * **REAL** integration test for VerbDrill progress persistence across app restarts and APK updates.
 *
 * This test uses the **REAL** VerbDrillStoreImpl (not FakeVerbDrillStore) and actually
 * verifies that:
 * 1. Progress is written to disk
 * 2. Progress survives store recreation (simulates app restart)
 * 3. Language separation works (EN progress doesn't overwrite IT progress)
 * 4. Legacy migration works correctly
 *
 * Unlike other tests that use FakeVerbDrillStore, this test uses real file I/O
 * to verify the actual persistence behavior users will experience.
 *
 * **Test Strategy:**
 * - Use VerbDrillStoreImpl with real file operations
 * - Create temporary test files in grammarmate/ directory
 * - Verify file contents after operations
 * - Test language separation by using different packIds
 * - Test migration from legacy format to new language-specific format
 *
 * References:
 * - TASK-077: Verb Drill Progress Lost on APK Update
 * - Wave 1: LanguageId separation implementation
 */
@RunWith(RobolectricTestRunner::class)
class VerbDrillProgressPersistenceTest {

    private lateinit var context: android.content.Context
    private lateinit var grammarmateDir: File
    private lateinit var testPackEn: String
    private lateinit var testPackIt: String

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        grammarmateDir = File(context.filesDir, "grammarmate")

        // Clean up any test files from previous runs
        cleanupTestFiles()

        // Test pack IDs following the pattern: "ru-{languageId}-v1"
        testPackEn = "ru-en-v1"
        testPackIt = "ru-it-v1"
    }

    private fun cleanupTestFiles() {
        // Delete all test verb drill files
        grammarmateDir.listFiles { file ->
            file.name.startsWith("verb_drill_progress_") ||
            file.name.startsWith("verb_drill_last_session_") ||
            file.name == "verb_drill_progress.yaml" ||
            file.name == "verb_drill_last_session.yaml"
        }?.forEach { it.delete() }
    }

    // ========================================
    // TEST 1: Basic Progress Persistence
    // ========================================

    @Test
    fun testProgress_persistsAcrossStoreRecreation() {
        // --- GIVEN: Create store for English pack and save progress ---
        val store1 = VerbDrillStoreImpl(context, testPackEn)

        val testProgress = mapOf(
            "${testPackEn}:Presente:regular_are" to VerbDrillComboProgress(
                group = "regular_are",
                tense = "Presente",
                totalCards = 20,
                everShownCardIds = setOf("card1", "card2", "card3"),
                todayShownCardIds = setOf("card1", "card2"),
                lastDate = LocalDate.now().toString()
            )
        )

        // --- WHEN: Progress is saved ---
        store1.saveProgress(testProgress)
        store1.flush()

        // --- THEN: File should exist on disk ---
        val progressFile = File(grammarmateDir, "verb_drill_progress_en.yaml")
        assertTrue("Progress file should exist for English pack", progressFile.exists())
        assertTrue("Progress file should not be empty", progressFile.length() > 0)

        // --- WHEN: Create NEW store instance (simulates app restart) ---
        val store2 = VerbDrillStoreImpl(context, testPackEn)

        // --- THEN: Progress should be loaded correctly ---
        val loadedProgress = store2.loadProgress()
        assertEquals("Progress should persist across store recreation", 1, loadedProgress.size)

        val loadedCombo = loadedProgress["${testPackEn}:Presente:regular_are"]
        assertNotNull("Combo progress should exist", loadedCombo)
        assertEquals("Group should match", "regular_are", loadedCombo!!.group)
        assertEquals("Tense should match", "Presente", loadedCombo.tense)
        assertEquals("Total cards should match", 20, loadedCombo.totalCards)
        assertEquals("Ever shown cards should persist", 3, loadedCombo.everShownCardIds.size)
        assertTrue("Card1 should be in ever shown", loadedCombo.everShownCardIds.contains("card1"))
    }

    // ========================================
    // TEST 2: Language Separation
    // ========================================

    @Test
    fun testProgress_languageSeparation_preventsOverwrite() {
        // --- GIVEN: Create stores for English and Italian packs ---
        val storeEn = VerbDrillStoreImpl(context, testPackEn)
        val storeIt = VerbDrillStoreImpl(context, testPackIt)

        // --- WHEN: Save progress for English pack ---
        val englishProgress = mapOf(
            "${testPackEn}:Presente:regular_are" to VerbDrillComboProgress(
                group = "regular_are",
                tense = "Presente",
                totalCards = 20,
                everShownCardIds = setOf("en_card1", "en_card2"),
                todayShownCardIds = setOf("en_card1"),
                lastDate = LocalDate.now().toString()
            )
        )
        storeEn.saveProgress(englishProgress)
        storeEn.flush()

        // --- WHEN: Save progress for Italian pack ---
        val italianProgress = mapOf(
            "${testPackIt}:Passato_Prossimo:regular_ere" to VerbDrillComboProgress(
                group = "regular_ere",
                tense = "Passato Prossimo",
                totalCards = 15,
                everShownCardIds = setOf("it_card1", "it_card2", "it_card3"),
                todayShownCardIds = setOf("it_card1"),
                lastDate = LocalDate.now().toString()
            )
        )
        storeIt.saveProgress(italianProgress)
        storeIt.flush()

        // --- THEN: Two separate files should exist ---
        val enFile = File(grammarmateDir, "verb_drill_progress_en.yaml")
        val itFile = File(grammarmateDir, "verb_drill_progress_it.yaml")

        assertTrue("English progress file should exist", enFile.exists())
        assertTrue("Italian progress file should exist", itFile.exists())

        // --- WHEN: Create new stores and load progress ---
        val newStoreEn = VerbDrillStoreImpl(context, testPackEn)
        val newStoreIt = VerbDrillStoreImpl(context, testPackIt)

        val loadedEn = newStoreEn.loadProgress()
        val loadedIt = newStoreIt.loadProgress()

        // --- THEN: Progress should be completely separate ---
        assertEquals("English store should have 1 combo", 1, loadedEn.size)
        assertEquals("Italian store should have 1 combo", 1, loadedIt.size)

        val enCombo = loadedEn.values.first()
        val itCombo = loadedIt.values.first()

        assertEquals("English combo should have English cards", 2, enCombo.everShownCardIds.size)
        assertEquals("Italian combo should have Italian cards", 3, itCombo.everShownCardIds.size)

        // Verify no cross-contamination
        assertFalse("English progress should not contain Italian cards",
                   enCombo.everShownCardIds.contains("it_card1"))
        assertFalse("Italian progress should not contain English cards",
                   itCombo.everShownCardIds.contains("en_card1"))
    }

    // ========================================
    // TEST 3: Last Session Persistence
    // ========================================

    @Test
    fun testLastSession_persistsAcrossStoreRecreation() {
        // --- GIVEN: Create store and save last session ---
        val store1 = VerbDrillStoreImpl(context, testPackIt)

        val lastSession = VerbDrillLastSessionState(
            selectedTense = "Imperfetto",
            selectedGroup = "irregular",
            cardsShown = setOf("card1", "card2", "card3"),
            timestamp = System.currentTimeMillis(),
            todayShownCardIds = setOf("card1", "card2", "card3")
        )

        // --- WHEN: Session is saved ---
        store1.saveLastSession(lastSession)

        // --- THEN: Session file should exist ---
        val sessionFile = File(grammarmateDir, "verb_drill_last_session_it.yaml")
        assertTrue("Session file should exist for Italian pack", sessionFile.exists())

        // --- WHEN: Create NEW store instance ---
        val store2 = VerbDrillStoreImpl(context, testPackIt)

        // --- THEN: Session should be loaded correctly ---
        val loadedSession = store2.loadLastSession()
        assertNotNull("Session should persist across store recreation", loadedSession)
        assertEquals("Tense should match", "Imperfetto", loadedSession!!.selectedTense)
        assertEquals("Group should match", "irregular", loadedSession.selectedGroup)
        assertEquals("Cards shown should match", 3, loadedSession.cardsShown.size)
        assertTrue("Card1 should be in session", loadedSession.cardsShown.contains("card1"))
    }

    // ========================================
    // TEST 4: Legacy File Migration
    // ========================================

    @Test
    fun testLegacyFiles_migrateToLanguageSpecificFormat() {
        // --- GIVEN: Create legacy flat files (old format) ---
        val legacyProgressFile = File(grammarmateDir, "verb_drill_progress.yaml")
        val legacySessionFile = File(grammarmateDir, "verb_drill_last_session.yaml")

        // Create legacy progress file with some data
        val legacyProgressContent = """
            schemaVersion: 1
            data:
              ru-it-v1:Presente:regular_are:
                group: regular_are
                tense: Presente
                totalCards: 20
                everShownCardIds:
                  - legacy_card1
                  - legacy_card2
                todayShownCardIds:
                  - legacy_card1
                lastDate: ${LocalDate.now().toString()}
        """.trimIndent()

        val legacySessionContent = """
            selectedTense: Imperfetto
            selectedGroup: irregular
            cardsShown:
              - legacy_session_card1
              - legacy_session_card2
            timestamp: ${System.currentTimeMillis()}
            todayShownCardIds:
              - legacy_session_card1
              - legacy_session_card2
        """.trimIndent()

        legacyProgressFile.writeText(legacyProgressContent)
        legacySessionFile.writeText(legacySessionContent)

        assertTrue("Legacy progress file should be created", legacyProgressFile.exists())
        assertTrue("Legacy session file should be created", legacySessionFile.exists())

        // --- WHEN: Create store (should trigger migration) ---
        val store = VerbDrillStoreImpl(context, testPackIt)

        // --- THEN: Legacy files should be deleted ---
        assertFalse("Legacy progress file should be deleted after migration",
                   legacyProgressFile.exists())
        assertFalse("Legacy session file should be deleted after migration",
                   legacySessionFile.exists())

        // --- THEN: New language-specific files should exist ---
        val newProgressFile = File(grammarmateDir, "verb_drill_progress_it.yaml")
        val newSessionFile = File(grammarmateDir, "verb_drill_last_session_it.yaml")

        assertTrue("New language-specific progress file should exist", newProgressFile.exists())
        assertTrue("New language-specific session file should exist", newSessionFile.exists())

        // --- THEN: Progress should be loadable from new files ---
        val loadedProgress = store.loadProgress()
        assertTrue("Progress should be loaded from migrated file", loadedProgress.isNotEmpty())

        val loadedSession = store.loadLastSession()
        assertNotNull("Session should be loaded from migrated file", loadedSession)
        assertEquals("Session data should migrate correctly", "Imperfetto", loadedSession!!.selectedTense)
    }

    // ========================================
    // TEST 5: Combo Progress Upsert
    // ========================================

    @Test
    fun testComboProgress_upsertUpdatesCorrectly() {
        // --- GIVEN: Create store and add initial progress ---
        val store = VerbDrillStoreImpl(context, testPackEn)

        val initialProgress = VerbDrillComboProgress(
            group = "regular_are",
            tense = "Presente",
            totalCards = 20,
            everShownCardIds = setOf("card1", "card2"),
            todayShownCardIds = setOf("card1"),
            lastDate = LocalDate.now().toString()
        )

        val comboKey = "${testPackEn}:Presente:regular_are"

        // --- WHEN: Upsert initial progress ---
        store.upsertComboProgress(comboKey, initialProgress)
        store.flush()

        // --- THEN: Progress should be retrievable ---
        val loaded1 = store.getComboProgress(comboKey)
        assertNotNull("Progress should exist after upsert", loaded1)
        assertEquals("Initial progress should have 2 ever shown cards", 2, loaded1!!.everShownCardIds.size)

        // --- WHEN: Update progress (simulate showing more cards) ---
        val updatedProgress = initialProgress.copy(
            everShownCardIds = setOf("card1", "card2", "card3", "card4"),
            todayShownCardIds = setOf("card3", "card4"),
            lastDate = LocalDate.now().toString()
        )

        store.upsertComboProgress(comboKey, updatedProgress)
        store.flush()

        // --- THEN: Updated progress should persist ---
        val loaded2 = store.getComboProgress(comboKey)
        assertNotNull("Progress should still exist", loaded2)
        assertEquals("Updated progress should have 4 ever shown cards", 4, loaded2!!.everShownCardIds.size)
        assertTrue("New cards should be in ever shown", loaded2.everShownCardIds.contains("card3"))
    }

    // ========================================
    // TEST 6: Delete Last Session
    // ========================================

    @Test
    fun testDeleteLastSession_removesFile() {
        // --- GIVEN: Create store and save session ---
        val store = VerbDrillStoreImpl(context, testPackEn)

        val session = VerbDrillLastSessionState(
            selectedTense = "Presente",
            selectedGroup = "regular_are",
            cardsShown = setOf("card1"),
            timestamp = System.currentTimeMillis(),
            todayShownCardIds = setOf("card1")
        )

        store.saveLastSession(session)

        val sessionFile = File(grammarmateDir, "verb_drill_last_session_en.yaml")
        assertTrue("Session file should exist", sessionFile.exists())

        // --- WHEN: Delete session ---
        store.deleteLastSession()

        // --- THEN: File should be deleted ---
        assertFalse("Session file should be deleted", sessionFile.exists())

        // --- THEN: Load should return null ---
        val loaded = store.loadLastSession()
        assertNull("No session should be loaded after deletion", loaded)
    }

    // ========================================
    // TEST 7: APK Update Simulation
    // ========================================

    @Test
    fun testAPKUpdate_preservesAllProgress() {
        // --- GIVEN: User has been using app with English pack ---
        val oldStore = VerbDrillStoreImpl(context, testPackEn)

        // User completes some cards
        val progress = mapOf(
            "${testPackEn}:Presente:regular_are" to VerbDrillComboProgress(
                group = "regular_are",
                tense = "Presente",
                totalCards = 20,
                everShownCardIds = setOf("card1", "card2", "card3", "card4", "card5"),
                todayShownCardIds = setOf("card5"),
                lastDate = LocalDate.now().toString()
            )
        )

        val session = VerbDrillLastSessionState(
            selectedTense = "Presente",
            selectedGroup = "regular_are",
            cardsShown = setOf("card1", "card2", "card3", "card4", "card5"),
            timestamp = System.currentTimeMillis(),
            todayShownCardIds = setOf("card5")
        )

        oldStore.saveProgress(progress)
        oldStore.saveLastSession(session)
        oldStore.flush()

        // Verify files exist before "APK update"
        val progressFile = File(grammarmateDir, "verb_drill_progress_en.yaml")
        val sessionFile = File(grammarmateDir, "verb_drill_last_session_en.yaml")
        assertTrue("Progress file should exist", progressFile.exists())
        assertTrue("Session file should exist", sessionFile.exists())

        // --- WHEN: APK is updated (simulated by creating new store instance) ---
        val newStore = VerbDrillStoreImpl(context, testPackEn)

        // --- THEN: All progress should be preserved ---
        val newProgress = newStore.loadProgress()
        val newSession = newStore.loadLastSession()

        assertNotNull("Progress should be preserved", newProgress)
        assertNotNull("Session should be preserved", newSession)

        assertEquals("Progress should have all ever shown cards", 1, newProgress.size)
        val preservedCombo = newProgress.values.first()
        assertEquals("Ever shown cards should be preserved", 5, preservedCombo.everShownCardIds.size)
        assertTrue("All specific cards should be preserved",
                  preservedCombo.everShownCardIds.containsAll(listOf("card1", "card2", "card3", "card4", "card5")))

        assertEquals("Session should be preserved", 5, newSession!!.cardsShown.size)
        assertEquals("Session tense should be preserved", "Presente", newSession.selectedTense)
    }
}
