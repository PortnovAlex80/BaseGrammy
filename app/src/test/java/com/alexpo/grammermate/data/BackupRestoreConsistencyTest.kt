package com.alexpo.grammermate.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupRestoreConsistencyTest {
    private lateinit var app: Application
    private lateinit var dataDir: File
    private lateinit var manager: BackupManagerImpl
    private val pack = "ru-en-restore-test"

    @Before
    fun setup() {
        app = RuntimeEnvironment.getApplication()
        dataDir = File(app.filesDir, "grammarmate")
        dataDir.deleteRecursively()
        dataDir.mkdirs()
        manager = BackupManagerImpl(app)
    }

    @Test
    fun samePackRestoreRefreshesLiveStoresAndDoesNotResurrectPendingMasteryWrite() {
        val mastery = MasteryStoreImpl(app, debounceMs = 180L)
        val factory = StoreFactory.getInstance(app)
        val hidden = factory.getHiddenCardStore()
        val vocab = factory.getVocabProgressStore(pack)
        val words = factory.getWordMasteryStore(pack)
        val verbs = factory.getVerbDrillStore(pack)
        val aux = factory.getAuxDrillStore(pack)
        val bad = factory.getBadSentenceStore()
        hidden.clearAll()
        vocab.clear()
        bad.clearAll()

        // B is the saved snapshot.
        mastery.recordCardShowForPack(pack, "lesson", "card-B")
        mastery.flush()
        hidden.hideCard("hidden-B")
        vocab.addCompletedIndex("lesson", "en", 8)
        words.upsertMastery(WordMasteryState("word", correctCount = 8))
        verbs.upsertComboProgress("combo", VerbDrillComboProgress("group", "tense", totalCards = 8))
        aux.upsertComboProgress("combo", AuxDrillComboProgress("verb", "tense", totalCards = 8))
        bad.addBadSentence(pack, "bad-B", "en", "sentence", "translation")
        assertTrue(manager.createBackup())
        val backupDir = File(dataDir, "backups/backup_latest")

        // A is the live in-memory state, including a pending debounced write.
        mastery.clear()
        mastery.recordCardShowForPack(pack, "lesson", "card-A")
        hidden.clearAll()
        hidden.hideCard("hidden-A")
        vocab.clear()
        vocab.addCompletedIndex("lesson", "en", 2)
        words.upsertMastery(WordMasteryState("word", correctCount = 2))
        verbs.upsertComboProgress("combo", VerbDrillComboProgress("group", "tense", totalCards = 2))
        aux.upsertComboProgress("combo", AuxDrillComboProgress("verb", "tense", totalCards = 2))
        bad.clearAll()
        bad.addBadSentence(pack, "bad-A", "en", "sentence", "translation")
        assertTrue(mastery.getForPack(pack, "lesson")!!.shownCardIds.contains("card-A"))
        assertTrue(hidden.isHidden("hidden-A"))
        assertEquals(setOf(2), vocab.get("lesson", "en").completedIndices)

        assertTrue(manager.restoreFromBackup(backupDir.absolutePath))
        assertEquals(setOf("card-B"), mastery.getForPack(pack, "lesson")!!.shownCardIds)
        assertTrue(hidden.isHidden("hidden-B"))
        assertFalse(hidden.isHidden("hidden-A"))
        assertEquals(setOf(8), vocab.get("lesson", "en").completedIndices)
        assertEquals(8, words.getMastery("word")!!.correctCount)
        assertEquals(8, verbs.getComboProgress("combo")!!.totalCards)
        assertEquals(8, aux.loadProgress()["combo"]!!.totalCards)
        assertTrue(bad.isBadSentence(pack, "bad-B"))
        assertFalse(bad.isBadSentence(pack, "bad-A"))

        Thread.sleep(500L) // Past the old pending writer's debounce window.
        val reopened = MasteryStoreImpl(app, debounceMs = 180L)
        assertEquals(setOf("card-B"), reopened.getForPack(pack, "lesson")!!.shownCardIds)
    }

    @Test
    fun backupAndRestoreReplaceEntireManagedSnapshotIncludingPackDrills() {
        val packDir = File(dataDir, "drills/$pack")
        packDir.mkdirs()
        val aux = File(packDir, "aux_drill_progress.yaml")
        val vocab = File(packDir, "vocab_progress.yaml")
        val cursor = File(dataDir, "daily_cursor_$pack.yaml")
        val lessonProgress = File(dataDir, "lesson_progress_$pack.yaml")
        AuxDrillStoreImpl(app, pack).saveProgress(emptyMap())
        VocabProgressStoreImpl(app, pack).addCompletedIndex("lesson", "en", 2)
        PackDailyCursorStoreImpl(app).savePackCursor(PackDailyCursorState(packId = pack))
        PackLessonProgressStoreImpl(app).savePackProgress(PackLessonProgressState(packId = pack))
        assertTrue(manager.createBackup())

        val latest = File(dataDir, "backups/backup_latest")
        listOf("drills/$pack/aux_drill_progress.yaml", "drills/$pack/vocab_progress.yaml",
            "daily_cursor_$pack.yaml", "lesson_progress_$pack.yaml").forEach { path ->
            assertTrue("Missing $path from snapshot", File(latest, path).exists())
        }

        // Reusing backup_latest must not leave files removed since the previous backup.
        aux.delete()
        assertTrue(manager.createBackup())
        assertFalse(File(latest, "drills/$pack/aux_drill_progress.yaml").exists())

        // A file created after the backup must be removed on restore, not merged in.
        aux.writeText("schemaVersion: 1\ndata: {stale: true}\n")
        assertTrue(manager.restoreFromBackup(latest.absolutePath))
        assertFalse(aux.exists())
        assertTrue(vocab.exists())
        assertTrue(cursor.exists())
        assertTrue(lessonProgress.exists())
    }

    @Test
    fun legacyFlatNamesRestoreIntoCurrentLanguageAndPackPaths() {
        val source = File(app.filesDir, "legacy-restore").apply { mkdirs() }
        val streakContent = """
            languageId: it
            currentStreak: 3
            longestStreak: 5
            lastCompletionDateMs: 0
            totalSubLessonsCompleted: 7
        """.trimIndent() + "\n"
        val verbContent = "languageId: it\nschemaVersion: 1\ndata: {}\n"
        val sessionContent = "languageId: it\nschemaVersion: 1\nsessionCardIds: []\n"
        val packContent = "schemaVersion: 1\ndata: {}\n"
        File(source, "streak.yaml").writeText(streakContent)
        File(source, "verb_drill_progress.yaml").writeText(verbContent)
        File(source, "verb_drill_last_session.yaml").writeText(sessionContent)
        File(source, "drills_${pack}_vocab_progress.yaml").writeText(packContent)

        File(dataDir, "streak_it.yaml").writeText("languageId: it\ncurrentStreak: 1\n")
        assertTrue(manager.restoreFromBackup(source.absolutePath))

        assertEquals(3, StreakStoreImpl(app).load("it").currentStreak)
        assertEquals(streakContent, File(dataDir, "streak_it.yaml").readText())
        assertEquals(verbContent, File(dataDir, "verb_drill_progress_it.yaml").readText())
        assertEquals(sessionContent, File(dataDir, "verb_drill_last_session_it.yaml").readText())
        assertEquals(packContent, File(dataDir, "drills/$pack/vocab_progress.yaml").readText())
        assertFalse(File(dataDir, "streak.yaml").exists())
        assertFalse(File(dataDir, "drills_${pack}_vocab_progress.yaml").exists())
        source.deleteRecursively()
    }

    @Test
    fun invalidSnapshotDoesNotReplaceAnyExistingProgressFile() {
        val hidden = File(dataDir, "hidden_cards.yaml")
        hidden.writeText("schemaVersion: 1\nhiddenCardIds: [keep]\n")
        val source = File(app.filesDir, "invalid-restore").apply { mkdirs() }
        File(source, "progress.yaml").writeText("schemaVersion: 1\ndata: {}\n")
        File(source, "hidden_cards.yaml").writeText("not: [valid")

        assertFalse(manager.restoreFromBackup(source.absolutePath))
        assertEquals("schemaVersion: 1\nhiddenCardIds: [keep]\n", hidden.readText())
        assertFalse(File(dataDir, "progress.yaml").exists())
        source.deleteRecursively()
    }
}
