package com.alexpo.grammermate.data

import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.yaml.snakeyaml.Yaml
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupSchemaContractTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun context() = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getFilesDir(): File = temporary.root
    }

    @Test fun currentSerializersRoundTripEveryManagedStoreAndRefreshLiveBackgroundMarks() {
        val app = context()
        val mastery = MasteryStoreImpl(app)
        mastery.recordCardShowForPack("pack", "lesson", "card")
        mastery.flush()
        ProgressStoreImpl(app).save(TrainingProgress(languageId = LanguageId("en"), lessonId = "lesson", activePackId = PackId("pack")))
        ProfileStoreImpl(app).save(UserProfile(userName = "Tester"))
        StreakStoreImpl(app).save(StreakData(LanguageId("en"), currentStreak = 2, longestStreak = 2))
        HiddenCardStoreImpl(app).hideCard("hidden")
        BadSentenceStoreImpl(app).addBadSentence("pack", "bad", "en", "prompt", "answer")
        for (pack in listOf(null, "pack")) {
            WordMasteryStoreImpl(app, pack).upsertMastery(WordMasteryState("word", correctCount = 2))
            val verbs = VerbDrillStoreImpl(app, pack)
            verbs.upsertComboProgress("combo", VerbDrillComboProgress("group", "tense", totalCards = 2))
            verbs.saveLastSession(VerbDrillLastSessionState(null, null, sortByFrequency = false, sessionCardIds = listOf("card"), packId = pack))
            AuxDrillStoreImpl(app, pack).upsertComboProgress("combo", AuxDrillComboProgress("verb", "tense", totalCards = 2))
            VocabProgressStoreImpl(app, pack).addCompletedIndex("lesson", "en", 1)
        }
        PackDailyCursorStoreImpl(app).savePackCursor(PackDailyCursorState(packId = "pack", sentenceOffset = 3))
        PackLessonProgressStoreImpl(app).savePackProgress(PackLessonProgressState("pack", mapOf("lesson" to PackLessonProgressState.LessonProgress(currentIndex = 2, activeSubLessonIndex = 1))))
        PomodoroHistoryStore(app).append(PomodoroHistoryEntry("history", "en", completedAtMs = 10L, durationMinutes = 5, totalSeconds = 300, remainingSeconds = 0, cardsShown = 3, cardsCorrect = 2, cardsIncorrect = 1, wordsPerMinute = 2.0))
        val marks = BgVocabMarkStore(app)
        marks.setMark("Word", BgVocabMark.GREEN)
        BgVocabPositionStore(app).setLastWord("word")
        val initial = BackupSnapshotFiles.current(app).mapValues { it.value.readText() }
        assertTrue(initial.keys.containsAll(listOf("mastery.yaml", "progress.yaml", "profile.yaml", "hidden_cards.yaml", "bad_sentences.yaml", "pomodoro_history.yaml", "bg_vocab_marks.json", "bg_vocab_position.json")))
        initial.forEach { (path, content) -> assertTrue("Current serializer rejected: $path\n$content", BackupContentValidator.isValid(path, content)) }
        val manager = BackupManagerImpl(app)
        assertTrue(manager.createBackup())
        BackupSnapshotFiles.current(app).values.forEach { it.delete() }
        marks.setMark("changed", BgVocabMark.RED)
        assertTrue(manager.restoreFromBackup(File(app.filesDir, "grammarmate/backups/backup_latest").path))
        assertEquals(initial, BackupSnapshotFiles.current(app).mapValues { it.value.readText() })
        assertEquals(BgVocabMark.GREEN, marks.getMark("WORD"))
        assertEquals(BgVocabMark.NONE, marks.getMark("changed"))
        assertEquals("lesson", ProgressStoreImpl(app).load().lessonId)
        assertEquals(1, PackLessonProgressStoreImpl(app).loadPackProgress("pack")!!.lessonProgress["lesson"]!!.activeSubLessonIndex)
    }

    @Test fun completeSnapshotsClearOmittedFilesButLegacyPartialSnapshotsPreserveThem() {
        val app = context()
        val marks = BgVocabMarkStore(app)
        val hidden = HiddenCardStoreImpl(app)
        hidden.hideCard("keep")
        val manager = BackupManagerImpl(app)
        assertTrue(manager.createBackup())
        marks.setMark("new", BgVocabMark.GREEN)
        assertTrue(manager.restoreFromBackup(File(app.filesDir, "grammarmate/backups/backup_latest").path))
        assertEquals(BgVocabMark.NONE, marks.getMark("new"))
        val oldBackup = temporary.newFolder("legacy")
        File(oldBackup, "progress.yaml").writeText("languageId: en\nlessonId: old\nmode: LESSON\n")
        assertTrue(manager.restoreFromBackup(oldBackup.path))
        assertTrue(File(app.filesDir, "grammarmate/hidden_cards.yaml").exists())
        assertEquals("old", ProgressStoreImpl(app).load().lessonId)
    }

    @Test fun incompleteOrAlteredModernSnapshotDoesNotMutateLiveFiles() {
        val app = context()
        ProgressStoreImpl(app).save(TrainingProgress(lessonId = "saved"))
        HiddenCardStoreImpl(app).hideCard("saved")
        val manager = BackupManagerImpl(app)
        assertTrue(manager.createBackup())
        val backup = File(app.filesDir, "grammarmate/backups/backup_latest")
        File(backup, "progress.yaml").delete()
        val before = BackupSnapshotFiles.current(app).mapValues { it.value.readText() }
        assertFalse(manager.restoreFromBackup(backup.path))
        assertEquals(before, BackupSnapshotFiles.current(app).mapValues { it.value.readText() })
        assertTrue(manager.createBackup())
        File(backup, "progress.yaml").appendText("# modified\n")
        assertFalse(manager.restoreFromBackup(backup.path))
        assertEquals(before, BackupSnapshotFiles.current(app).mapValues { it.value.readText() })
    }

    @Test fun legacySerializerPayloadsRemainValidAndWrongNestedShapesAreRejected() {
        val app = context()
        val mastery = MasteryStoreImpl(app)
        mastery.recordCardShowForPack("pack", "lesson", "card")
        mastery.flush()
        ProgressStoreImpl(app).save(TrainingProgress(lessonId = "lesson"))
        for (name in listOf("mastery.yaml", "progress.yaml")) {
            val root = Yaml().load<Map<String, Any>>(File(app.filesDir, "grammarmate/$name").readText())
            assertTrue("Flat historical $name", BackupContentValidator.isValid(name, Yaml().dump(root["data"])))
        }
        assertTrue(BackupContentValidator.isValid("mastery.yaml", "schemaVersion: 1\ndata: {}\n"))
        assertTrue(BackupContentValidator.isValid("drills/pack/vocab_progress.yaml", "schemaVersion: 1\ndata: {}\n"))
        assertFalse(BackupContentValidator.isValid("progress.yaml", "schemaVersion: 1\ndata: {mode: training}\n"))
        assertFalse(BackupContentValidator.isValid("progress.yaml", "schemaVersion: 1\ndata: {mode: INVALID}\n"))
        val invalid = mapOf(
            "mastery.yaml" to "schemaVersion: 2\ndata: {pack: {lesson: {shownCardIds: wrong}}}\n",
            "progress.yaml" to "schemaVersion: 1\ndata: {languageId: en, dailyCursor: []}\n",
            "lesson_progress_pack.yaml" to "schemaVersion: 1\npackId: pack\nlessonProgress: {lesson: {state: INVALID}}\n",
            "daily_cursor_pack.yaml" to "schemaVersion: 1\npackId: other\nsentenceOffset: 2\n",
            "pomodoro_history.yaml" to "sessions: [{languageId: en, completedAtMs: wrong}]\n",
            "bg_vocab_marks.json" to "{\"word\":\"INVALID\"}",
            "drills/pack/vocab_progress.yaml" to "schemaVersion: 1\ndata: {en: {lesson: {entries: []}}}\n"
        )
        invalid.forEach { (path, content) -> assertFalse("Must reject $path", BackupContentValidator.isValid(path, content)) }
    }

    @Test fun optionalSelectionMetadataAndPartialCacheReadsRetainAllPacks() {
        val app = context()
        val progress = PackLessonProgressStoreImpl(app)
        val cursors = PackDailyCursorStoreImpl(app)
        for (pack in listOf("a", "b")) {
            progress.savePackProgress(PackLessonProgressState(pack))
            cursors.savePackCursor(PackDailyCursorState(packId = pack))
        }
        val reader = PackLessonProgressStoreImpl(app)
        val cursorReader = PackDailyCursorStoreImpl(app)
        reader.loadPackProgress("a")
        cursorReader.loadPackCursor("a")
        assertEquals(setOf("a", "b"), reader.loadAllPackProgress().keys)
        assertEquals(setOf("a", "b"), cursorReader.loadAllPackCursors().keys)
        File(app.filesDir, "grammarmate/lesson_progress_a.yaml").writeText("schemaVersion: 1\npackId: a\nlessonProgress: {lesson: {currentIndex: 1, state: PAUSED}}\n")
        assertNull(PackLessonProgressStoreImpl(app).loadPackProgress("a")!!.lessonProgress["lesson"]!!.activeSubLessonIndex)
        File(app.filesDir, "grammarmate/progress.yaml").writeText("schemaVersion: 1\ndata: {languageId: en}\n")
        assertNull(ProgressStoreImpl(app).load().lessonId)
    }
}
