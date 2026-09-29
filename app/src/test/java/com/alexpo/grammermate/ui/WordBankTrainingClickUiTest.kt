package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.AppContainer
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.StoreFactory
import com.alexpo.grammermate.ui.screens.TrainingScreenContent
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class WordBankTrainingClickUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun bankClicksRespectCopiesAndUndoWithoutAwardingMastery() {
        val application = RuntimeEnvironment.getApplication() as Application
        val packId = "BANK_CLICK_${java.lang.Long.toHexString(System.nanoTime())}"
        val lessonId = "bank_lesson"
        val vm = TrainingViewModel(application)
        rule.setContent {
            GrammarMateTheme {
                TrainingScreenContent(
                    state = vm.uiState.collectAsState().value,
                    vm = vm,
                    onShowExitDialog = {},
                    onShowSettings = {},
                    onTtsSpeak = {}
                )
            }
        }
        rule.waitUntil(timeoutMillis = 240_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            !vm.uiState.value.isLoading
        }

        val zip = File(application.cacheDir, "$packId.zip")
        val manifest = """{"schemaVersion":2,"packId":"$packId","packVersion":"test-1","language":"it","displayName":"Bank test","chapters":[{"chapterId":"chapter_1","order":1,"title":"Test","subtitle":"Test","lessons":["$lessonId"]}]}"""
        ZipOutputStream(zip.outputStream()).use { output ->
            fun entry(name: String, contents: String) {
                output.putNextEntry(ZipEntry(name))
                output.write(contents.toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
            entry("manifest.json", manifest)
            entry("$lessonId.csv", "$lessonId title\nPrompt one;one one two\nPrompt two;one one two\nPrompt three;one one two\n")
        }
        StoreFactory.getInstance(application).getLessonStore()
            .importPackFromUri(Uri.fromFile(zip), application.contentResolver)

        try {
            rule.runOnUiThread {
                vm.selectLanguage("it")
                vm.selectPack(packId)
                vm.selectSubLesson(0)
            }
            rule.waitUntil(timeoutMillis = 10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.uiState.value.navigation.activePackId?.value == packId &&
                    vm.uiState.value.cardSession.currentCard?.acceptedAnswers?.firstOrNull() == "one one two"
            }
            val masteryStore = AppContainer(application).masteryStore
            assertEquals(0L, masteryStore.getForPack(packId, lessonId)?.lastReviewMs ?: 0L)

            if (vm.uiState.value.cardSession.sessionState != SessionState.ACTIVE) {
                rule.onNodeWithTag("pause_button").performScrollTo().performClick()
            }
            rule.waitUntil(timeoutMillis = 10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.uiState.value.cardSession.sessionState == SessionState.ACTIVE &&
                    vm.uiState.value.cardSession.wordBankWords.isNotEmpty()
            }
            rule.onNodeWithContentDescription(application.getString(R.string.content_desc_word_bank_mode)).performClick()
            rule.onAllNodesWithTag("word_bank_chip_one")[0].performClick()
            assertEquals(listOf("one"), vm.uiState.value.cardSession.selectedWords)
            rule.onAllNodesWithTag("word_bank_chip_one")[1].performClick()
            assertEquals(listOf("one", "one"), vm.uiState.value.cardSession.selectedWords)
            rule.onAllNodesWithTag("word_bank_chip_one")[0].assertIsNotEnabled()
            rule.onAllNodesWithTag("word_bank_chip_one")[1].assertIsNotEnabled()

            rule.onNodeWithTag("word_bank_undo").performClick()
            assertEquals(listOf("one"), vm.uiState.value.cardSession.selectedWords)
            rule.onAllNodesWithTag("word_bank_chip_one")[0].assertIsEnabled()
            rule.onAllNodesWithTag("word_bank_chip_one")[0].performClick()
            rule.onNodeWithTag("word_bank_chip_two").performClick()
            assertEquals(listOf("one", "one", "two"), vm.uiState.value.cardSession.selectedWords)
            assertEquals("one one two", vm.inputText.value)
            rule.onNodeWithTag("check_button").performScrollTo().performClick()
            rule.waitForIdle()

            assertTrue(vm.uiState.value.cardSession.correctCount > 0)
            val bankMastery = masteryStore.getForPack(packId, lessonId)
            assertEquals(0L, bankMastery?.lastReviewMs ?: 0L)
            assertEquals(0, bankMastery?.uniqueCardShows ?: 0)

            // A correctly typed answer is the control: it must stamp mastery.
            rule.runOnUiThread {
                vm.selectLesson(lessonId, packId)
                vm.selectSubLesson(0)
            }
            rule.waitUntil(timeoutMillis = 10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.uiState.value.cardSession.currentCard?.acceptedAnswers?.firstOrNull() == "one one two"
            }
            rule.onNodeWithContentDescription(application.getString(R.string.content_desc_keyboard_mode))
                .performScrollTo().performClick()
            rule.waitUntil(timeoutMillis = 10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.uiState.value.cardSession.inputMode == InputMode.KEYBOARD
            }
            if (vm.uiState.value.cardSession.sessionState != SessionState.ACTIVE) {
                rule.onNodeWithTag("pause_button").performScrollTo()
                rule.onNodeWithContentDescription(application.getString(R.string.content_desc_play)).performClick()
            }
            rule.waitUntil(timeoutMillis = 10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.uiState.value.cardSession.sessionState == SessionState.ACTIVE
            }
            val correctBeforeTyping = vm.uiState.value.cardSession.correctCount
            rule.onNodeWithTag("input_field").performTextInput("ONE, one two!")
            assertEquals(correctBeforeTyping, vm.uiState.value.cardSession.correctCount)
            assertEquals("ONE, one two!", vm.inputText.value)
            rule.onNodeWithTag("check_button").performScrollTo().assertIsEnabled().performClick()
            try {
                rule.waitUntil(timeoutMillis = 10_000) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    vm.uiState.value.cardSession.correctCount == correctBeforeTyping + 1
                }
            } catch (cause: Throwable) {
                val state = vm.uiState.value.cardSession
                throw AssertionError(
                    "Keyboard Check did not advance count once: before=$correctBeforeTyping, " +
                        "nowCorrect=${state.correctCount}, nowIncorrect=${state.incorrectCount}, " +
                        "lastResult=${state.lastResult}, card=${state.currentCard?.id}, " +
                        "index=${state.currentIndex}, mode=${state.inputMode}, " +
                        "session=${state.sessionState}, inputNow='${vm.inputText.value}'"
                ).apply { initCause(cause) }
            }
            assertEquals(correctBeforeTyping + 1, vm.uiState.value.cardSession.correctCount)
            assertTrue((masteryStore.getForPack(packId, lessonId)?.lastReviewMs ?: 0L) > 0L)
        } finally {
            rule.runOnUiThread { vm.selectLanguage("en") }
        }
    }
}
