package com.alexpo.grammermate.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PersistenceSafetyRegressionTest {
    @Test
    fun secondDebounceWindowAlsoPersistsWithoutExplicitFlush() {
        val app = RuntimeEnvironment.getApplication()
        val store = MasteryStoreImpl(app, debounceMs = 100L)
        store.clear()
        val file = File(app.filesDir, "grammarmate/mastery.yaml")
        try {
            store.recordCardShowForPack("review-pack", "lesson", "review-first-card")
            val deadline = System.nanoTime() + 5_000_000_000L
            while ((!file.exists() || !file.readText().contains("review-first-card")) &&
                System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue("First automatic persistence must work", file.exists() && file.readText().contains("review-first-card"))
            Thread.sleep(200)
            store.recordCardShowForPack("review-pack", "lesson", "review-second-card")
            Thread.sleep(700)
            assertTrue("A second debounce window must persist new progress without explicit flush", file.readText().contains("review-second-card"))
        } finally {
            store.flush()
        }
    }

    @Test
    fun restoreRejectsWellFormedYamlWithWrongMasterySchemaBeforeDeletingProgress() {
        val app = RuntimeEnvironment.getApplication()
        val dataDir = File(app.filesDir, "grammarmate").apply { mkdirs() }
        val hidden = File(dataDir, "hidden_cards.yaml")
        hidden.writeText("schemaVersion: 1\nhiddenCardIds: [keep]\n")
        val backup = File(app.filesDir, "review-wrong-schema-backup").apply { mkdirs() }
        File(backup, "mastery.yaml").writeText("unrelated: value\n")
        val success = BackupManagerImpl(app).restoreFromBackup(backup.absolutePath)
        assertFalse("Wrong-schema restore must be rejected; actual success=$success, prior hidden file still exists=${hidden.exists()}", success)
        assertTrue("Rejected restore must retain unrelated progress", hidden.exists())
    }

    @Test
    fun progressBackupIncludesPomodoroHistoryAndBackgroundVocabLearningMarks() {
        val app = RuntimeEnvironment.getApplication()
        val dataDir = File(app.filesDir, "grammarmate").apply { mkdirs() }
        File(dataDir, "pomodoro_history.yaml").writeText("sessions: []\n")
        BgVocabMarkStore(app).setMark("review-word", BgVocabMark.GREEN)
        assertTrue(BackupManagerImpl(app).createBackup())
        val backup = File(dataDir, "backups/backup_latest")
        assertTrue("Learning/session history must be included in progress backup", File(backup, "pomodoro_history.yaml").exists())
        assertTrue("User word-learning marks must be included in progress backup", backup.walkTopDown().any { it.name == "bg_vocab_marks.json" })
    }
}
