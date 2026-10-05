package com.alexpo.grammermate.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.GrammarMateApplication
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.*
import com.alexpo.grammermate.ui.dialogs.DialogState
import com.alexpo.grammermate.ui.navigation.Routes
import com.alexpo.grammermate.ui.screens.TrainingScreenContent
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native Dialog/Popup pointer delivery; MainActivity routes have a separate smoke journey. */
@RunWith(AndroidJUnit4::class)
class ModalTouchRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val suffix = UUID.randomUUID().toString().take(8)
    private val packA = "TOUCH_A_$suffix"
    private val packB = "TOUCH_B_$suffix"
    private val lessonId = "touch_lesson_$suffix"
    private lateinit var app: GrammarMateApplication
    private lateinit var vm: TrainingViewModel
    private lateinit var originalConfig: AppConfig
    private var originalConfigBytes: ByteArray? = null
    private var progressSnapshot: Map<String, ByteArray>? = null
    private fun stores() = StoreFactory.getInstance(app)
    private fun text(id: Int) = rule.activity.getString(id)

    @Before fun setup() {
        app = rule.activity.application as GrammarMateApplication
        MasteryStoreImpl.drainBeforeRestore(app)
        progressSnapshot = BackupSnapshotFiles.current(app).mapValues { it.value.readBytes() }
        val configFile = File(app.filesDir, "grammarmate/config.yaml")
        originalConfigBytes = configFile.takeIf(File::isFile)?.readBytes()
        originalConfig = stores().getAppConfigStore().load()
        stores().getAppConfigStore().save(originalConfig.copy(voiceAutoStart = false))
        install(packA)
        install(packB)
        lateinit var verbVm: VerbDrillViewModel
        rule.runOnUiThread {
            vm = ViewModelProvider(rule.activity)[TrainingViewModel::class.java]
            verbVm = ViewModelProvider(rule.activity)[VerbDrillViewModel::class.java]
        }
        rule.setContent {
            val state by vm.uiState.collectAsState()
            val dialogs = remember { mutableStateOf<DialogState>(DialogState.None) }
            val navController = rememberNavController()
            GrammarMateTheme {
                TrainingScreenContent(
                    state, vm, onShowExitDialog = {}, onTtsSpeak = {},
                    onShowSettings = { vm.pauseSession(); dialogs.value = DialogState.Settings }
                )
                AppSettingsOverlay(state, vm, verbVm, navController, Routes.TRAINING, dialogs)
            }
        }
        rule.waitUntil(180_000) { !vm.uiState.value.isLoading }
        select(packA)
    }

    @After fun cleanup() {
        if (!::app.isInitialized) return
        rule.runOnUiThread {
            if (::vm.isInitialized) vm.pauseSession()
            rule.activity.setContent {}
            rule.activity.viewModelStore.clear()
        }
        MasteryStoreImpl.drainBeforeRestore(app)
        listOf(packA, packB).forEach {
            stores().getLessonStore().removeInstalledPackData(it)
            File(app.cacheDir, "$it.zip").delete()
        }
        progressSnapshot?.let { original ->
            BackupSnapshotFiles.current(app).filterKeys { it !in original }.values.forEach(File::delete)
            original.forEach { (path, bytes) ->
                BackupSnapshotFiles.destination(app, path).also { it.parentFile?.mkdirs() }.writeBytes(bytes)
            }
            MasteryStoreImpl.invalidateAfterRestore(app)
            stores().invalidateAfterRestore()
            app.container.packLessonProgressStore.invalidateCache()
        }
        if (::originalConfig.isInitialized) {
            stores().getAppConfigStore().save(originalConfig)
            val configFile = File(app.filesDir, "grammarmate/config.yaml")
            originalConfigBytes?.let(configFile::writeBytes) ?: configFile.delete()
        }
    }

    @Test fun reportDialogTouchPersistsFlagAndScopedHide() {
        val first = vm.training.currentCard() as SentenceCard
        val key = HiddenCardKeys.forCard(first)!!
        reportAction(R.string.report_flag)
        rule.waitUntil(5_000) { stores().getBadSentenceStore().isBadSentence(packA, key) }
        reportAction(R.string.report_unflag)
        rule.waitUntil(5_000) { !stores().getBadSentenceStore().isBadSentence(packA, key) }
        reportAction(R.string.report_hide_card)
        rule.waitUntil(5_000) { stores().getHiddenCardStore().isHidden(key) }
        assertNotEquals(first.id, vm.training.currentCard()!!.id)
        select(packB)
        val other = vm.training.currentCard() as SentenceCard
        assertEquals(first.id, other.id)
        assertEquals("$packB prompt 1", other.promptRu)
        assertFalse(HiddenCardKeys.contains(stores().getHiddenCardStore().getHiddenCardIds(), other))
    }

    @Test fun settingsPopupTouchChangesPackClearsDraftAndKeepsDifficulty() {
        rule.onNodeWithContentDescription(text(R.string.content_desc_keyboard_mode)).performScrollTo().performClick()
        rule.onNodeWithTag("input_field").performTextInput("draft from the first pack")
        assertEquals("draft from the first pack", vm.inputText.value)
        Espresso.closeSoftKeyboard()
        rule.onNodeWithContentDescription(text(R.string.training_settings)).performClick()
        rule.onNodeWithText(text(R.string.settings_hard)).performScrollTo().assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { vm.uiState.value.cardSession.hintLevel == HintLevel.HARD }
        rule.onNode(hasClickAction() and hasTextExactly(packA)).performScrollTo().performClick()
        rule.onNode(hasAnyAncestor(isPopup()) and hasClickAction() and hasAnyDescendant(hasTextExactly(packB)), useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        rule.waitUntil(10_000) {
            vm.uiState.value.navigation.activePackId?.value == packB &&
                vm.training.currentCard()?.promptRu == "$packB prompt 1"
        }
        assertEquals("", vm.inputText.value)
        assertEquals(HintLevel.HARD, vm.uiState.value.cardSession.hintLevel)
        assertEquals(0, vm.uiState.value.cardSession.correctCount)
        Espresso.pressBack()
        rule.onNodeWithTag("input_field").assertIsDisplayed()
    }

    private fun reportAction(action: Int) {
        rule.onNodeWithContentDescription(text(R.string.tooltip_report_sentence)).performScrollTo().performClick()
        rule.onNodeWithText(text(action)).assertIsDisplayed().assertIsEnabled().performClick()
        rule.waitForIdle()
    }

    private fun select(packId: String) {
        rule.runOnUiThread {
            vm.selectLanguage("it")
            vm.selectPack(packId)
            vm.selectLesson(lessonId, packId)
            vm.selectSubLesson(0)
        }
        rule.waitUntil(10_000) {
            vm.uiState.value.navigation.activePackId?.value == packId &&
                vm.training.currentCard()?.promptRu == "$packId prompt 1"
        }
    }

    private fun install(packId: String) {
        val archive = File(app.cacheDir, "$packId.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            fun entry(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            entry("manifest.json", """{"schemaVersion":1,"packId":"$packId","packVersion":"1","language":"it","displayName":"$packId","lessons":[{"lessonId":"$lessonId","order":1,"file":"lesson.csv"}]}""")
            entry("lesson.csv", "Touch lesson\n" + (1..4).joinToString("\n") { "$packId prompt $it;answer $it" })
        }
        stores().getLessonStore().importPackFromUri(Uri.fromFile(archive), app.contentResolver)
    }
}
