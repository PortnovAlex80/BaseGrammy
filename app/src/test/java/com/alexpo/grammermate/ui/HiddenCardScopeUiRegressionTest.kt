package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.*
import com.alexpo.grammermate.ui.screens.TrainingScreenContent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class HiddenCardScopeUiRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val firstPack = "HIDDEN_SCOPE_A"
    private val secondPack = "HIDDEN_SCOPE_B"
    private lateinit var vm: TrainingViewModel
    private lateinit var originalConfig: AppConfig
    private lateinit var originalProgress: TrainingProgress
    private lateinit var hiddenFile: File
    private var originalHidden: ByteArray? = null
    private fun app(): Application = rule.activity.application
    private fun stores() = StoreFactory.getInstance(app())

    @Before fun setup() {
        originalConfig = stores().getAppConfigStore().load()
        originalProgress = stores().getProgressStore().load()
        hiddenFile = File(app().filesDir, "grammarmate/hidden_cards.yaml")
        originalHidden = hiddenFile.takeIf(File::isFile)?.readBytes()
        // This fixture exercises report actions, not cold-start asset extraction.
        // Prepare real bundled content before starting the asynchronous ViewModel.
        stores().getLessonStore().ensureSeedData()
        stores().getLessonStore().updateDefaultPacksIfNeeded()
        install(firstPack)
        install(secondPack)
        rule.runOnUiThread {
            // AndroidViewModelFactory.getInstance retains the first Application,
            // but Robolectric creates a new Application for each test method.
            vm = ViewModelProvider(rule.activity, ViewModelProvider.AndroidViewModelFactory(app()))[TrainingViewModel::class.java]
        }
        assertSame(app(), vm.getApplication<Application>())
        rule.setContent {
            val state by vm.uiState.collectAsState()
            GrammarMateTheme {
                TrainingScreenContent(state, vm, onShowExitDialog = {}, onShowSettings = {}, onTtsSpeak = {})
            }
        }
        await { !vm.uiState.value.isLoading }
        select(firstPack, "first")
    }

    @After fun cleanup() {
        rule.runOnUiThread {
            if (::vm.isInitialized) vm.selectLanguage("en")
            rule.activity.setContent {}
            rule.activity.viewModelStore.clear()
        }
        MasteryStoreImpl.drainBeforeRestore(app())
        stores().getLessonStore().removeInstalledPackData(firstPack)
        stores().getLessonStore().removeInstalledPackData(secondPack)
        stores().getBadSentenceStore().clearPack(firstPack)
        stores().getBadSentenceStore().clearPack(secondPack)
        if (::originalConfig.isInitialized) stores().getAppConfigStore().save(originalConfig)
        if (::originalProgress.isInitialized) stores().getProgressStore().save(originalProgress)
        if (::hiddenFile.isInitialized) {
            originalHidden?.let(hiddenFile::writeBytes) ?: hiddenFile.delete()
            (stores().getHiddenCardStore() as HiddenCardStoreImpl).invalidateCache()
        }
    }

    @Test fun hidingThroughReportSheetDoesNotHideTheSameRowOfAnotherLessonOrPackAndClearIsScoped() {
        val first = vm.training.currentCard() as SentenceCard
        reportAction(R.string.report_hide_card)
        select(firstPack, "second")
        val second = vm.training.currentCard() as SentenceCard
        assertEquals(first.id, second.id)
        assertEquals("$firstPack second 1", second.promptRu)
        select(secondPack, "first")
        val otherPackCard = vm.training.currentCard() as SentenceCard
        assertEquals(first.id, otherPackCard.id)
        assertEquals("$secondPack first 1", otherPackCard.promptRu)
        reportAction(R.string.report_hide_card)
        select(firstPack, "first")
        assertEquals("$firstPack first 2", vm.training.currentCard()!!.promptRu)
        rule.runOnUiThread { vm.clearPackBadSentences() }
        select(firstPack, "first")
        assertEquals("$firstPack first 1", vm.training.currentCard()!!.promptRu)
        select(secondPack, "first")
        assertEquals("$secondPack first 2", vm.training.currentCard()!!.promptRu)
    }

    @Test fun reportFlagAndUnflagUseTheDisplayedLessonEvenWhenRowsHaveTheSameId() {
        val first = vm.training.currentCard() as SentenceCard
        reportAction(R.string.report_flag)
        select(firstPack, "second")
        val second = vm.training.currentCard() as SentenceCard
        assertEquals(first.id, second.id)
        assertFalse(vm.reports.isBadSentence())
        reportAction(R.string.report_flag)
        assertEquals(2, stores().getBadSentenceStore().getBadSentenceCount(firstPack))
        reportAction(R.string.report_unflag)
        assertFalse(vm.reports.isBadSentence())
        assertEquals(1, stores().getBadSentenceStore().getBadSentenceCount(firstPack))
        select(firstPack, "first")
        assertTrue(vm.reports.isBadSentence())
    }

    private fun reportAction(action: Int) {
        val before = vm.training.currentCard()!!
        val key = HiddenCardKeys.forCard(before)!!
        rule.onNodeWithContentDescription(app().getString(R.string.tooltip_report_sentence))
            .performScrollTo().performClick()
        settleModal()
        // Robolectric does not deliver pointer events across the Dialog root here.
        // Keep the production UI action; native touch is covered by ModalTouchRegressionTest.
        rule.onNodeWithText(app().getString(action)).assertIsDisplayed().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        settleModal()
        try {
            rule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                when (action) {
                    R.string.report_hide_card -> stores().getHiddenCardStore().isHidden(key)
                    R.string.report_flag -> vm.reports.isBadSentence()
                    R.string.report_unflag -> !vm.reports.isBadSentence()
                    else -> error("Unsupported report action")
                }
            }
        } catch (failure: Throwable) {
            println("Report callback did not complete: action=$action source=$key hidden=${stores().getHiddenCardStore().getHiddenCardIds()}")
            val roots = rule.onAllNodes(isRoot(), useUnmergedTree = true)
            roots.fetchSemanticsNodes().indices.forEach { println(roots[it].printToString()) }
            throw failure
        }
    }

    private fun settleModal() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250))
        rule.waitForIdle()
    }

    private fun select(pack: String, lesson: String) {
        rule.runOnUiThread {
            vm.selectLanguage("it")
            vm.selectPack(pack)
            vm.selectLesson(lesson, pack)
            vm.selectSubLesson(0)
        }
        try {
            await { vm.uiState.value.navigation.activePackId?.value == pack && vm.uiState.value.navigation.selectedLessonId?.value == lesson }
        } catch (failure: Throwable) {
            val state = vm.uiState.value
            println("Context selection failed: requested=$pack/$lesson actual=${state.navigation.activePackId}/${state.navigation.selectedLessonId} loading=${state.isLoading}")
            println("Registered packs=${stores().getLessonStore().getInstalledPacks().map { it.packId.value }} target lessons=${stores().getLessonStore().getLessons(pack, "it").map { it.id.value }} sameApplication=${app() === vm.getApplication<Application>()}")
            throw failure
        }
    }

    private fun install(pack: String) {
        val archive = File(app().cacheDir, "$pack.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            fun entry(path: String, text: String) {
                output.putNextEntry(ZipEntry(path)); output.write(text.toByteArray()); output.closeEntry()
            }
            entry("manifest.json", """{"schemaVersion":1,"packId":"$pack","packVersion":"1","language":"it","lessons":[{"lessonId":"first","order":1,"file":"first.csv"},{"lessonId":"second","order":2,"file":"second.csv"}]}""")
            listOf("first", "second").forEach { lesson ->
                entry("$lesson.csv", "$lesson\n" + (1..4).joinToString("\n") { "$pack $lesson $it;answer $it" })
            }
        }
        stores().getLessonStore().importPackFromUri(Uri.fromFile(archive), app().contentResolver)
    }

    private fun await(condition: () -> Boolean) {
        rule.waitUntil(60_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); condition() }
    }
}
