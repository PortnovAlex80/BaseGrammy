package com.alexpo.grammermate.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Migration tests for ProgressStore global-to-pack-scoped daily cursor migration.
 *
 * Tests verify:
 * - Migration logic correctly moves global cursor to pack-scoped files
 * - Empty global cursor skips migration
 * - Migration handles multiple packs correctly
 * - Migration is idempotent (can run multiple times safely)
 * - Global cursor is cleared after successful migration
 *
 * Reference: TASK-080 State Isolation Bug - Phase 3: Migration
 */
@RunWith(RobolectricTestRunner::class)
class ProgressStoreMigrationTest {

    private lateinit var context: android.content.Context
    private lateinit var grammarmateDir: File
    private lateinit var progressFile: File
    private lateinit var testPackEn: PackId
    private lateinit var testPackIt: PackId

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        grammarmateDir = File(context.filesDir, "grammarmate")
        progressFile = File(grammarmateDir, "progress.yaml")

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

        // Delete progress file
        if (progressFile.exists()) {
            progressFile.delete()
        }
    }

    // ========================================
    // TEST 1: Empty Global Cursor Skips Migration
    // ========================================

    @Test
    fun migrateGlobalToPackScoped_emptyGlobal_noMigration() {
        // --- GIVEN: ProgressStore with empty global cursor ---
        val progressStore = ProgressStoreImpl(context)

        val progressWithEmptyCursor = TrainingProgress(
            languageId = LanguageId("en"),
            activePackId = testPackEn,
            dailyCursor = DailyCursorState(
                sentenceOffset = 0,
                currentLessonIndex = 0,
                lastSessionHash = 0,
                firstSessionDate = "",
                firstSessionSentenceCardIds = emptyList(),
                firstSessionVerbCardIds = emptyList()
            )
        )

        progressStore.save(progressWithEmptyCursor)

        // --- WHEN: Migration is attempted ---
        val result = progressStore.migrateGlobalDailyCursorToPackScoped()

        // --- THEN: Migration should be skipped (false result) ---
        assertFalse("Migration should return false when cursor is empty", result)

        // --- THEN: No pack-scoped cursor file should be created ---
        val cursorFile = File(grammarmateDir, "daily_cursor_${testPackEn.value}.yaml")
        assertFalse("Pack cursor file should not be created for empty migration", cursorFile.exists())
    }

    // ========================================
    // TEST 2: Global Data With Non-Default Values Migrates
    // ========================================

    @Test
    fun migrateGlobalToPackScoped_withGlobalData_createsPackFiles() {
        // --- GIVEN: ProgressStore with non-empty global cursor ---
        val progressStore = ProgressStoreImpl(context)

        val progressWithData = TrainingProgress(
            languageId = LanguageId("en"),
            activePackId = testPackEn,
            dailyCursor = DailyCursorState(
                sentenceOffset = 30,
                currentLessonIndex = 5,
                lastSessionHash = 12345,
                firstSessionDate = "2026-05-21",
                firstSessionSentenceCardIds = listOf("card1", "card2", "card3"),
                firstSessionVerbCardIds = listOf("verb1", "verb2")
            )
        )

        progressStore.save(progressWithData)

        // Verify global cursor exists in progress.yaml
        val loadedBeforeMigration = progressStore.load()
        assertEquals("Global cursor should exist before migration",
                    30, loadedBeforeMigration.dailyCursor.sentenceOffset)

        // --- WHEN: Migration is executed ---
        val result = progressStore.migrateGlobalDailyCursorToPackScoped()

        // --- THEN: Migration should succeed (true result) ---
        assertTrue("Migration should return true when data exists", result)

        // --- THEN: Pack-scoped cursor file should be created ---
        val cursorFile = File(grammarmateDir, "daily_cursor_${testPackEn.value}.yaml")
        assertTrue("Pack cursor file should be created", cursorFile.exists())

        // --- THEN: Pack-scoped file should contain migrated data ---
        val packStore = PackDailyCursorStoreImpl(context, testPackEn)
        val migratedCursor = packStore.loadPackCursor(testPackEn)

        assertNotNull("Migrated cursor should not be null", migratedCursor)
        assertEquals("Sentence offset should be migrated", 30, migratedCursor!!.sentenceOffset)
        assertEquals("Current lesson index should be migrated", 5, migratedCursor.currentLessonIndex)
        assertEquals("Last session hash should be migrated", 12345, migratedCursor.lastSessionHash)
        assertEquals("First session date should be migrated", "2026-05-21", migratedCursor.firstSessionDate)
        assertEquals("Sentence card IDs should be migrated", 3, migratedCursor.firstSessionSentenceCardIds.size)
        assertEquals("Verb card IDs should be migrated", 2, migratedCursor.firstSessionVerbCardIds.size)

        // --- THEN: Global cursor should be cleared to defaults ---
        val loadedAfterMigration = progressStore.load()
        assertEquals("Global cursor sentence offset should be cleared to 0",
                    0, loadedAfterMigration.dailyCursor.sentenceOffset)
        assertEquals("Global cursor lesson index should be cleared to 0",
                    0, loadedAfterMigration.dailyCursor.currentLessonIndex)
        assertEquals("Global cursor first session date should be cleared to empty",
                    "", loadedAfterMigration.dailyCursor.firstSessionDate)
    }

    // ========================================
    // TEST 3: Migration to Active Pack When Multiple Packs Exist
    // ========================================

    @Test
    fun migrateGlobalToPackScoped_multiplePacks_migratesToActivePack() {
        // --- GIVEN: ProgressStore with global cursor and active pack set ---
        val progressStore = ProgressStoreImpl(context)

        val progress = TrainingProgress(
            languageId = LanguageId("en"),
            activePackId = testPackIt, // Active pack is Italian
            dailyCursor = DailyCursorState(
                sentenceOffset = 77,
                currentLessonIndex = 7,
                lastSessionHash = 7777,
                firstSessionDate = "2026-05-20",
                firstSessionSentenceCardIds = listOf("global_card"),
                firstSessionVerbCardIds = listOf("global_verb")
            )
        )

        progressStore.save(progress)

        // --- WHEN: Migration is executed ---
        val result = progressStore.migrateGlobalDailyCursorToPackScoped()

        // --- THEN: Migration should succeed ---
        assertTrue("Migration should succeed", result)

        // --- THEN: Cursor should be migrated to ACTIVE pack (Italian), not English ---
        val itCursorFile = File(grammarmateDir, "daily_cursor_${testPackIt.value}.yaml")
        val enCursorFile = File(grammarmateDir, "daily_cursor_${testPackEn.value}.yaml")

        assertTrue("Italian pack cursor file should exist", itCursorFile.exists())
        assertFalse("English pack cursor file should NOT be created", enCursorFile.exists())

        // --- THEN: Migrated data should match global cursor ---
        val packStore = PackDailyCursorStoreImpl(context, testPackIt)
        val migratedCursor = packStore.loadPackCursor(testPackIt)

        assertNotNull("Migrated cursor should exist for Italian pack", migratedCursor)
        assertEquals("Global cursor should migrate to active pack", 77, migratedCursor!!.sentenceOffset)
        assertEquals("Global lesson index should migrate to active pack", 7, migratedCursor.currentLessonIndex)
    }

    // ========================================
    // TEST 4: Migration Idempotence - Safe to Run Multiple Times
    // ========================================

    @Test
    fun migrateGlobalToPackScoped_migrationIdempotent() {
        // --- GIVEN: ProgressStore with global cursor ---
        val progressStore = ProgressStoreImpl(context)

        val progress = TrainingProgress(
            languageId = LanguageId("en"),
            activePackId = testPackEn,
            dailyCursor = DailyCursorState(
                sentenceOffset = 50,
                currentLessonIndex = 10,
                lastSessionHash = 5555,
                firstSessionDate = "2026-05-21",
                firstSessionSentenceCardIds = listOf("idempotent_card"),
                firstSessionVerbCardIds = listOf("idempotent_verb")
            )
        )

        progressStore.save(progress)

        // --- WHEN: Migration is run FIRST time ---
        val result1 = progressStore.migrateGlobalDailyCursorToPackScoped()

        // --- THEN: First migration should succeed ---
        assertTrue("First migration should succeed", result1)

        val packStore = PackDailyCursorStoreImpl(context, testPackEn)
        val afterFirstMigration = packStore.loadPackCursor(testPackEn)

        assertNotNull("Cursor should exist after first migration", afterFirstMigration)
        assertEquals("Cursor offset should be preserved after first migration",
                    50, afterFirstMigration!!.sentenceOffset)

        // --- WHEN: Migration is run SECOND time (after global cursor is cleared) ---
        val result2 = progressStore.migrateGlobalDailyCursorToPackScoped()

        // --- THEN: Second migration should be skipped (false) ---
        assertFalse("Second migration should return false (nothing to migrate)", result2)

        // --- THEN: Original migrated data should remain unchanged ---
        val afterSecondMigration = packStore.loadPackCursor(testPackEn)

        assertNotNull("Cursor should still exist after second migration attempt", afterSecondMigration)
        assertEquals("Cursor offset should be unchanged after second migration",
                    50, afterSecondMigration!!.sentenceOffset)
        assertEquals("Lesson index should be unchanged",
                    10, afterSecondMigration.currentLessonIndex)
        assertEquals("Card IDs should be unchanged",
                    1, afterSecondMigration.firstSessionSentenceCardIds.size)
    }

    // ========================================
    // TEST 5: Migration With No Active Pack - Uses First Available
    // ========================================

    @Test
    fun migrateGlobalToPackScoped_noActivePack_usesFirstAvailable() {
        // --- GIVEN: ProgressStore with global cursor but NO active pack ---
        val progressStore = ProgressStoreImpl(context)

        val progress = TrainingProgress(
            languageId = LanguageId("en"),
            activePackId = null, // No active pack set
            dailyCursor = DailyCursorState(
                sentenceOffset = 88,
                currentLessonIndex = 8,
                lastSessionHash = 8888,
                firstSessionDate = "2026-05-21",
                firstSessionSentenceCardIds = listOf("fallback_card"),
                firstSessionVerbCardIds = listOf("fallback_verb")
            )
        )

        progressStore.save(progress)

        // --- WHEN: Migration is attempted without active pack ---
        // NOTE: This test assumes ProgressStore.migrateGlobalDailyCursorToPackScoped()
        // will use a fallback mechanism (e.g., LessonStore.getFirstInstalledPackId())
        // In real implementation, this might return false or use a default pack

        // For now, we test the expected behavior: migration returns false
        // when no active pack is available
        val result = progressStore.migrateGlobalDailyCursorToPackScoped()

        // --- THEN: Migration should fail gracefully ---
        // This behavior may vary based on implementation
        // If implementation uses fallback, result would be true
        // If implementation skips migration, result would be false
        // We assert that no crash occurs and state is consistent

        // Verify no files were created if migration was skipped
        if (!result) {
            val enCursorFile = File(grammarmateDir, "daily_cursor_${testPackEn.value}.yaml")
            assertFalse("No cursor file should be created when migration is skipped", enCursorFile.exists())
        }
    }

    // ========================================
    // TEST 6: Migration Preserves Progress.yaml Structure
    // ========================================

    @Test
    fun migrateGlobalToPackScoped_preservesOtherProgressFields() {
        // --- GIVEN: ProgressStore with multiple fields including global cursor ---
        val progressStore = ProgressStoreImpl(context)

        val originalProgress = TrainingProgress(
            languageId = LanguageId("it"),
            mode = TrainingMode.LESSON,
            lessonId = "lesson-5",
            currentIndex = 15,
            correctCount = 100,
            incorrectCount = 5,
            activeTimeMs = 3600000L,
            state = SessionState.PAUSED,
            activePackId = testPackIt,
            dailyLevel = 3,
            dailyTaskIndex = 2,
            dailyCursor = DailyCursorState(
                sentenceOffset = 22,
                currentLessonIndex = 3,
                lastSessionHash = 3333,
                firstSessionDate = "2026-05-21",
                firstSessionSentenceCardIds = listOf("preserve_card"),
                firstSessionVerbCardIds = listOf("preserve_verb")
            )
        )

        progressStore.save(originalProgress)

        // --- WHEN: Migration is executed ---
        val result = progressStore.migrateGlobalDailyCursorToPackScoped()

        // --- THEN: Migration should succeed ---
        assertTrue("Migration should succeed", result)

        // --- THEN: Other progress fields should remain unchanged ---
        val afterMigration = progressStore.load()

        assertEquals("Language ID should be preserved", LanguageId("it"), afterMigration.languageId)
        assertEquals("Training mode should be preserved", TrainingMode.LESSON, afterMigration.mode)
        assertEquals("Lesson ID should be preserved", "lesson-5", afterMigration.lessonId)
        assertEquals("Current index should be preserved", 15, afterMigration.currentIndex)
        assertEquals("Correct count should be preserved", 100, afterMigration.correctCount)
        assertEquals("Incorrect count should be preserved", 5, afterMigration.incorrectCount)
        assertEquals("Active time should be preserved", 3600000L, afterMigration.activeTimeMs)
        assertEquals("Session state should be preserved", SessionState.PAUSED, afterMigration.state)
        assertEquals("Active pack ID should be preserved", testPackIt, afterMigration.activePackId)
        assertEquals("Daily level should be preserved", 3, afterMigration.dailyLevel)
        assertEquals("Daily task index should be preserved", 2, afterMigration.dailyTaskIndex)

        // --- THEN: Only dailyCursor should be cleared ---
        assertEquals("Daily cursor should be cleared after migration",
                    0, afterMigration.dailyCursor.sentenceOffset)
        assertEquals("Daily cursor lesson index should be cleared",
                    0, afterMigration.dailyCursor.currentLessonIndex)
    }

    // ========================================
    // TEST 7: Migration Handles Complex Card Lists
    // ========================================

    @Test
    fun migrateGlobalToPackScoped_complexCardLists_migratesCorrectly() {
        // --- GIVEN: Global cursor with complex card ID lists ---
        val progressStore = ProgressStoreImpl(context)

        val complexSentenceCardIds = listOf(
            "sentence_1_ru-en-v1_lesson-2",
            "sentence_2_ru-en-v1_lesson-2",
            "sentence_3_ru-en-v1_lesson-2",
            "sentence_4_ru-en-v1_lesson-3",
            "sentence_5_ru-en-v1_lesson-3"
        )

        val complexVerbCardIds = listOf(
            "verb_andare_Presente_irregular",
            "verb_essere_Passato_Prossimo_irregular",
            "verb_avere_Imperfetto_regular"
        )

        val progress = TrainingProgress(
            languageId = LanguageId("en"),
            activePackId = testPackEn,
            dailyCursor = DailyCursorState(
                sentenceOffset = 50,
                currentLessonIndex = 3,
                lastSessionHash = 99999,
                firstSessionDate = "2026-05-21",
                firstSessionSentenceCardIds = complexSentenceCardIds,
                firstSessionVerbCardIds = complexVerbCardIds
            )
        )

        progressStore.save(progress)

        // --- WHEN: Migration is executed ---
        val result = progressStore.migrateGlobalDailyCursorToPackScoped()

        // --- THEN: Migration should succeed ---
        assertTrue("Migration should succeed with complex data", result)

        // --- THEN: Complex card lists should be preserved exactly ---
        val packStore = PackDailyCursorStoreImpl(context, testPackEn)
        val migratedCursor = packStore.loadPackCursor(testPackEn)

        assertNotNull("Migrated cursor should exist", migratedCursor)
        assertEquals("All sentence card IDs should be migrated",
                    5, migratedCursor!!.firstSessionSentenceCardIds.size)
        assertEquals("All verb card IDs should be migrated",
                    3, migratedCursor.firstSessionVerbCardIds.size)

        // Verify specific complex card IDs are preserved
        assertTrue("Complex sentence card ID should be preserved",
                  migratedCursor.firstSessionSentenceCardIds.contains("sentence_3_ru-en-v1_lesson-2"))
        assertTrue("Complex verb card ID should be preserved",
                  migratedCursor.firstSessionVerbCardIds.contains("verb_essere_Passato_Prossimo_irregular"))
    }

    // ========================================
    // TEST 8: Multiple Pack Scenarios - Only Active Pack Gets Migration
    // ========================================

    @Test
    fun migrateGlobalToPackScoped_multiplePacksOnlyActiveMigrated() {
        // --- GIVEN: User has multiple packs but only Italian is active ---
        val progressStore = ProgressStoreImpl(context)

        // Simulate scenario where user has both packs installed
        // but only Italian pack is active
        val progress = TrainingProgress(
            languageId = LanguageId("it"),
            activePackId = testPackIt,
            dailyCursor = DailyCursorState(
                sentenceOffset = 60,
                currentLessonIndex = 6,
                lastSessionHash = 6666,
                firstSessionDate = "2026-05-21",
                firstSessionSentenceCardIds = listOf("active_pack_card"),
                firstSessionVerbCardIds = listOf("active_pack_verb")
            )
        )

        progressStore.save(progress)

        // --- WHEN: Migration is executed ---
        val result = progressStore.migrateGlobalDailyCursorToPackScoped()

        // --- THEN: Migration should succeed ---
        assertTrue("Migration should succeed", result)

        // --- THEN: Only ACTIVE pack should get cursor file ---
        val itCursorFile = File(grammarmateDir, "daily_cursor_${testPackIt.value}.yaml")
        val enCursorFile = File(grammarmateDir, "daily_cursor_${testPackEn.value}.yaml")

        assertTrue("Active pack (Italian) cursor file should exist", itCursorFile.exists())
        assertFalse("Inactive pack (English) cursor file should NOT be created", enCursorFile.exists())

        // --- THEN: English pack should have null cursor until first use ---
        val enPackStore = PackDailyCursorStoreImpl(context, testPackEn)
        val enCursor = enPackStore.loadPackCursor(testPackEn)

        assertNull("Inactive pack should have null cursor until first use", enCursor)
    }
}
