package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.AppConfig
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.StoreFactory
import com.alexpo.grammermate.AppContainer
import com.alexpo.grammermate.data.BackupManagerImpl
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.PackLessonProgressState
import com.alexpo.grammermate.data.PackLessonProgressStoreImpl
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Context changes preserve settings and isolate pending answers and progress. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TrainingContextStateRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val packId = "TRAINING_REVIEW_PROBE"
    private val otherPackId = "TRAINING_REVIEW_OTHER"
    private lateinit var vm: TrainingViewModel
    private lateinit var originalConfig: AppConfig
    private lateinit var originalProgress: TrainingProgress
    private var observedVm by mutableStateOf<TrainingViewModel?>(null)
    private val extraPackIds = mutableSetOf<String>()

    private fun app(): Application = rule.activity.application

    @Before fun setup() {
        originalConfig = StoreFactory.getInstance(app()).getAppConfigStore().load()
        originalProgress = StoreFactory.getInstance(app()).getProgressStore().load()
        installPack(packId)
        installPack(otherPackId)
        vm = TrainingViewModel(app())
        observedVm = vm
        rule.setContent { Text(observedVm!!.uiState.collectAsState().value.navigation.activePackId?.value.orEmpty()) }
        settle { !vm.uiState.value.isLoading }
        rule.runOnUiThread {
            vm.selectLanguage("it")
            vm.selectPack(packId)
            vm.selectLesson("review_a", packId)
            vm.selectSubLesson(0)
        }
        settle { vm.uiState.value.navigation.selectedLessonId?.value == "review_a" }
    }

    private fun installPack(id: String) {
        val archive = File(app().cacheDir, "$id.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            fun entry(name: String, text: String) {
                output.putNextEntry(ZipEntry(name))
                output.write(text.toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
            entry("manifest.json", """{"schemaVersion":2,"packId":"$id","packVersion":"1","language":"it","displayName":"Review $id","chapters":[{"chapterId":"chapter_1","order":1,"title":"Review","lessons":["review_a","review_b"]}]}""")
            entry("review_a.csv", "Review A\nPrompt A1;alpha\nPrompt A2;bravo\nPrompt A3;charlie\n")
            entry("review_b.csv", "Review B\n" + (1..24).joinToString("\n") { "Prompt B$it;answer $it" } + "\n")
        }
        StoreFactory.getInstance(app()).getLessonStore().importPackFromUri(Uri.fromFile(archive), app().contentResolver)
    }

    @After fun cleanup() {
        if (::vm.isInitialized) rule.runOnUiThread { vm.selectLanguage("en") }
        StoreFactory.getInstance(app()).getLessonStore().removeInstalledPackData(packId)
        StoreFactory.getInstance(app()).getLessonStore().removeInstalledPackData(otherPackId)
        extraPackIds.forEach { StoreFactory.getInstance(app()).getLessonStore().removeInstalledPackData(it) }
        if (::originalConfig.isInitialized) StoreFactory.getInstance(app()).getAppConfigStore().save(originalConfig)
        if (::originalProgress.isInitialized) StoreFactory.getInstance(app()).getProgressStore().save(originalProgress)
    }

    @Test fun switchingPackFromSettingsClearsThePreviousTypedAnswer() {
        rule.runOnUiThread {
            vm.training.setInputMode(InputMode.KEYBOARD)
            vm.training.onInputChanged("draft belonging to lesson A")
            vm.pauseSession()
            vm.selectPack(otherPackId)
            vm.resumeFromSettings()
        }
        settle { vm.uiState.value.navigation.activePackId?.value == otherPackId }
        assertEquals("A newly selected lesson must not carry the old answer", "", vm.inputText.value)
    }

    @Test fun returningToPackSelectionPreservesConfiguredHintLevel() {
        rule.runOnUiThread {
            vm.settings.setHintLevel(HintLevel.HARD)
            vm.clearActivePack()
            vm.selectPack(packId)
        }
        rule.waitForIdle()
        assertEquals("Leaving and reopening a pack must preserve HARD hints", HintLevel.HARD, vm.uiState.value.cardSession.hintLevel)
    }

    @Test fun changingLessonResetsCountersBeforeStartingTheNextLesson() {
        rule.runOnUiThread {
            vm.training.setInputMode(InputMode.KEYBOARD)
            vm.training.onInputChanged(vm.training.currentCard()!!.acceptedAnswers.first())
            vm.submitAnswer()
            vm.navigateNext()
            vm.selectLesson("review_b", packId)
            // Mirrors the actual LessonRoadmap -> training entry path.
            vm.selectSubLesson(0)
        }
        settle { vm.uiState.value.navigation.selectedLessonId?.value == "review_b" }
        assertEquals("An unanswered new lesson must not inherit lesson A's correct answer", 0, vm.uiState.value.cardSession.correctCount)
    }

    @Test fun openingSettingsWithoutChangingContextPreservesDraft() {
        rule.runOnUiThread {
            vm.training.setInputMode(InputMode.KEYBOARD)
            vm.training.onInputChanged("unfinished answer")
            vm.pauseSession()
            vm.resumeFromSettings()
        }
        rule.waitForIdle()
        assertEquals("unfinished answer", vm.inputText.value)
    }

    @Test fun checkpointRestoreAndRestartKeepSelectedLessonPositionWithoutCreditingAnAnswer() {
        val checkpointStore = PackLessonProgressStoreImpl(app())
        val progressStore = StoreFactory.getInstance(app()).getProgressStore()
        val masteryStore = AppContainer(app()).masteryStore
        val checkpoint = PackLessonProgressState.LessonProgress(
            currentIndex = 1, activeSubLessonIndex = 1,
            correctCount = 2, incorrectCount = 1, activeTimeMs = 4200L,
            state = SessionState.PAUSED
        )
        checkpointStore.savePackProgress(PackLessonProgressState(packId, mapOf("review_b" to checkpoint)))
        val otherCheckpoint = checkpoint.copy(currentIndex = 2, activeSubLessonIndex = 0, correctCount = 7, incorrectCount = 3, activeTimeMs = 8700L)
        checkpointStore.savePackProgress(PackLessonProgressState(otherPackId, mapOf("review_b" to otherCheckpoint)))
        progressStore.save(TrainingProgress(
            languageId = LanguageId("it"), activePackId = PackId(packId), lessonId = "review_b"
        ))
        val masteryBefore = masteryStore.getForPack(packId, "review_b")
        val backup = BackupManagerImpl(app())
        assertTrue(backup.createBackup())

        // Divergent live pack with a draft must be replaced by the restored snapshot.
        rule.runOnUiThread {
            vm.selectPack(otherPackId)
            vm.training.setInputMode(InputMode.KEYBOARD)
            vm.training.onInputChanged("discard on restore")
            vm.pauseSession()
        }
        assertTrue(backup.restoreFromBackup(File(app().filesDir, "grammarmate/backups/backup_latest").absolutePath))
        rule.runOnUiThread { vm.reloadFromDisk() }
        settle { vm.uiState.value.navigation.activePackId?.value == packId && vm.uiState.value.navigation.selectedLessonId?.value == "review_b" }
        rule.waitForIdle()
        assertCheckpoint(checkpoint)
        val restoredCard = vm.uiState.value.cardSession.currentCard!!.id
        assertEquals("", vm.inputText.value)
        assertEquals(masteryBefore, masteryStore.getForPack(packId, "review_b"))

        rule.runOnUiThread { vm.selectPack(otherPackId) }
        rule.waitForIdle()
        assertCheckpoint(otherCheckpoint)
        rule.runOnUiThread { vm.selectPack(packId) }
        rule.waitForIdle()
        assertCheckpoint(checkpoint)

        // A new process projection uses the same selected lesson and pack checkpoint.
        rule.runOnUiThread { vm = TrainingViewModel(app()); observedVm = vm }
        settle { !vm.uiState.value.isLoading }
        assertCheckpoint(checkpoint)
        assertEquals(restoredCard, vm.uiState.value.cardSession.currentCard!!.id)
        assertEquals(masteryBefore, masteryStore.getForPack(packId, "review_b"))

        // An explicit roadmap click is new intent, not another checkpoint resume.
        rule.runOnUiThread { vm.selectSubLesson(0) }
        rule.waitForIdle()
        assertEquals(0, vm.uiState.value.cardSession.activeSubLessonIndex)
        assertEquals(0, vm.uiState.value.cardSession.currentIndex)
    }

    @Test fun startupDropsTransientHintAndClampsCheckpointToAvailableCards() {
        // Recreating a VM retains AppContainer; write through its checkpoint owner.
        val checkpoints = (app() as com.alexpo.grammermate.GrammarMateApplication).container.packLessonProgressStore
        checkpoints.savePackProgress(PackLessonProgressState(packId, mapOf(
            "review_b" to PackLessonProgressState.LessonProgress(
                currentIndex = Int.MAX_VALUE, activeSubLessonIndex = Int.MAX_VALUE,
                state = SessionState.HINT_SHOWN, incorrectAttemptsForCard = 3
            )
        )))
        StoreFactory.getInstance(app()).getProgressStore().save(TrainingProgress(
            languageId = LanguageId("it"), activePackId = PackId(packId), lessonId = "review_b"
        ))
        rule.runOnUiThread { vm = TrainingViewModel(app()); observedVm = vm }
        settle { !vm.uiState.value.isLoading }
        val restored = vm.uiState.value.cardSession
        assertEquals(SessionState.PAUSED, restored.sessionState)
        assertEquals(0, restored.incorrectAttemptsForCard)
        assertEquals(restored.subLessonCount - 1, restored.activeSubLessonIndex)
        assertEquals(restored.subLessonTotal - 1, restored.currentIndex)
        assertTrue(restored.canSubmit)
        assertEquals(null, restored.answerText)
    }

    private fun assertCheckpoint(checkpoint: PackLessonProgressState.LessonProgress) {
        val session = vm.uiState.value.cardSession
        assertEquals("review_b", vm.uiState.value.navigation.selectedLessonId?.value)
        assertEquals(checkpoint.activeSubLessonIndex, session.activeSubLessonIndex)
        assertEquals(checkpoint.currentIndex, session.currentIndex)
        assertEquals(checkpoint.correctCount, session.correctCount)
        assertEquals(checkpoint.incorrectCount, session.incorrectCount)
        assertEquals(checkpoint.activeTimeMs, session.activeTimeMs)
        assertNotNull(session.currentCard)
    }

    @Test fun importedCsvAndEmptyLessonRemainSelectableAfterContextReload() {
        val csv = File(app().cacheDir, "standalone_review.csv")
        csv.writeText("Standalone review\nA unique prompt;unica risposta\n")
        rule.runOnUiThread { vm.importLesson(Uri.fromFile(csv)) }
        rule.waitForIdle()
        val imported = vm.uiState.value.navigation
        val importedPack = imported.activePackId!!.value.also(extraPackIds::add)
        val importedLesson = imported.selectedLessonId!!.value
        assertTrue(importedPack.startsWith("USER_"))
        assertTrue(imported.installedPacks.any { it.packId.value == importedPack })
        assertEquals("unica risposta", vm.training.currentCard()!!.acceptedAnswers.first())

        rule.runOnUiThread {
            vm.clearActivePack()
            vm.selectPack(importedPack)
        }
        rule.waitForIdle()
        assertEquals(importedLesson, vm.uiState.value.navigation.selectedLessonId?.value)

        rule.runOnUiThread { vm.createEmptyLesson("Draft review lesson") }
        rule.waitForIdle()
        val created = vm.uiState.value.navigation
        val createdPack = created.activePackId!!.value.also(extraPackIds::add)
        assertTrue(createdPack.startsWith("USER_"))
        assertEquals("Draft review lesson", created.lessons.single().title)
        assertTrue(created.lessons.single().cards.isEmpty())
        rule.runOnUiThread { vm.deleteLesson(created.selectedLessonId!!.value) }
        rule.waitForIdle()
        assertTrue(vm.uiState.value.navigation.installedPacks.none { it.packId.value == createdPack })
    }

    private fun settle(condition: () -> Boolean) {
        rule.waitUntil(240_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
