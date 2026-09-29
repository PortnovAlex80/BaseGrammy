package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.PackLessonProgressStoreImpl
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.StoreFactory
import com.alexpo.grammermate.ui.screens.TrainingScreenContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** B1 click regression: production TrainingScreen callbacks and real session/persistence owners. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RegularLessonClickUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val packId = "UI_REGULAR_LESSON"
    private val lessonId = "ui_regular_lesson"
    private lateinit var vm: TrainingViewModel

    @Before
    fun setUp() {
        val stores = StoreFactory.getInstance(application())
        stores.getMasteryStore().clearPack(packId)
        PackLessonProgressStoreImpl(application()).deletePackProgress(packId)
        importLessonPack()

        vm = TrainingViewModel(application())
        rule.setContent {
            GrammarMateTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                TrainingScreenContent(
                    state = state,
                    vm = vm,
                    onShowExitDialog = {},
                    onShowSettings = {},
                    onTtsSpeak = {}
                )
            }
        }
        rule.waitUntil(240_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            !vm.uiState.value.isLoading
        }
        // Establish a fresh, paused regular lesson. All transitions asserted
        // below use the actual TrainingScreen controls and production callbacks.
        rule.runOnUiThread {
            vm.selectLanguage("it")
            vm.selectPack(packId)
            vm.selectSubLesson(0)
            vm.training.setInputMode(InputMode.KEYBOARD)
        }
        rule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.cardSession.currentCard?.promptRu == "Prompt one" &&
                vm.uiState.value.cardSession.sessionState == SessionState.PAUSED
        }
        rule.onNodeWithTag("card_prompt_text").assertIsDisplayed()
            .assertTextContains("Prompt one")
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) rule.runOnUiThread { vm.selectLanguage("en") }
        val stores = StoreFactory.getInstance(application())
        stores.getLessonStore().removeInstalledPackData(packId)
        stores.getMasteryStore().clearPack(packId)
        PackLessonProgressStoreImpl(application()).deletePackProgress(packId)
    }

    @Test
    fun answerPauseHintAndNavigationHaveDistinctEffectsAndNoStaleInput() {
        // Pause/resume preserves the partial answer on the same card.
        rule.onNodeWithTag("pause_button").performScrollTo().performClick()
        awaitState { it.cardSession.sessionState == SessionState.ACTIVE }
        rule.onNodeWithTag("input_field").performTextInput("partial")
        rule.onNodeWithTag("pause_button").performScrollTo().performClick()
        awaitState { it.cardSession.sessionState == SessionState.PAUSED }
        assertEquals("partial", vm.inputText.value)
        assertEquals(0, vm.uiState.value.cardSession.correctCount)
        assertEquals(0, vm.uiState.value.cardSession.incorrectCount)
        assertEquals("Prompt one", vm.uiState.value.cardSession.currentCard?.promptRu)

        rule.onNodeWithTag("pause_button").performScrollTo().performClick()
        awaitState { it.cardSession.sessionState == SessionState.ACTIVE }
        assertEquals("partial", vm.inputText.value)

        // Revealing the answer is a hint, not a graded submission.
        rule.onNodeWithTag("show_answer_button").performScrollTo().performClick()
        awaitState { it.cardSession.sessionState == SessionState.HINT_SHOWN }
        assertEquals("answer one", vm.uiState.value.cardSession.answerText)
        assertEquals(1, vm.uiState.value.cardSession.hintCount)
        assertEquals(0, vm.uiState.value.cardSession.correctCount)
        assertEquals(0, vm.uiState.value.cardSession.incorrectCount)
        rule.onNodeWithText(rule.activity.getString(R.string.hint_answer_prefix) + "answer one")
            .performScrollTo().assertIsDisplayed()

        // The Next arrow visits card two without grading card one and clears
        // its partial text, hint and result before the new card is shown.
        rule.onNodeWithTag("next_button").performScrollTo().performClick()
        awaitState { it.cardSession.currentCard?.promptRu == "Prompt two" }
        assertEquals(1, vm.uiState.value.cardSession.currentIndex)
        assertEquals(SessionState.PAUSED, vm.uiState.value.cardSession.sessionState)
        assertEquals(0, vm.uiState.value.cardSession.correctCount)
        assertEquals(0, vm.uiState.value.cardSession.incorrectCount)
        assertNull(vm.uiState.value.cardSession.answerText)
        assertNull(vm.uiState.value.cardSession.lastResult)
        assertEquals("", vm.inputText.value)
        rule.onNodeWithTag("card_prompt_text").assertIsDisplayed()
            .assertTextContains("Prompt two")
        rule.onNodeWithTag("check_button").assertIsNotEnabled()
        assertEquals(0L, masteryLastReview())

        // A wrong answer stays on card two and shows feedback.
        rule.onNodeWithTag("pause_button").performScrollTo().performClick()
        awaitState { it.cardSession.sessionState == SessionState.ACTIVE }
        rule.onNodeWithTag("input_field").performTextInput("wrong")
        rule.onNodeWithTag("check_button").performScrollTo().performClick()
        awaitState { it.cardSession.incorrectCount == 1 }
        assertEquals("Prompt two", vm.uiState.value.cardSession.currentCard?.promptRu)
        assertEquals(0, vm.uiState.value.cardSession.correctCount)
        assertEquals(false, vm.uiState.value.cardSession.lastResult)
        rule.onNodeWithText(rule.activity.getString(R.string.result_incorrect))
            .performScrollTo().assertIsDisplayed()
        assertEquals(0L, masteryLastReview())

        // A correct keyboard answer advances once, records learning and leaves
        // card three with an empty input and no stale wrong-answer feedback.
        rule.onNodeWithTag("input_field").performTextClearance()
        rule.onNodeWithTag("input_field").performTextInput("answer two")
        rule.onNodeWithTag("check_button").performScrollTo().performClick()
        awaitState { it.cardSession.currentCard?.promptRu == "Prompt three" }
        assertEquals(1, vm.uiState.value.cardSession.correctCount)
        assertEquals(1, vm.uiState.value.cardSession.incorrectCount)
        assertEquals(2, vm.uiState.value.cardSession.currentIndex)
        assertNull(vm.uiState.value.cardSession.lastResult)
        assertNull(vm.uiState.value.cardSession.answerText)
        assertEquals("", vm.inputText.value)
        rule.onNodeWithTag("card_prompt_text").assertIsDisplayed()
            .assertTextContains("Prompt three")
        rule.onNodeWithTag("check_button").assertIsNotEnabled()
        assertTrue(masteryLastReview() > 0L)
    }

    private fun awaitState(predicate: (com.alexpo.grammermate.data.TrainingUiState) -> Boolean) {
        rule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            predicate(vm.uiState.value)
        }
    }

    private fun masteryLastReview(): Long = StoreFactory.getInstance(application())
        .getMasteryStore().getForPack(packId, lessonId)?.lastReviewMs ?: 0L

    private fun importLessonPack() {
        val archive = File(application().cacheDir, "$packId.zip")
        val manifest = """{"schemaVersion":2,"packId":"$packId","packVersion":"test-1","language":"it","displayName":"Click regular lesson","chapters":[{"chapterId":"chapter_1","order":1,"title":"Click chapter","lessons":["$lessonId"]}]}"""
        ZipOutputStream(archive.outputStream()).use { zip ->
            fun entry(path: String, contents: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(contents.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("manifest.json", manifest)
            entry(
                "$lessonId.csv",
                "$lessonId title\nPrompt one;answer one\nPrompt two;answer two\nPrompt three;answer three\n"
            )
        }
        StoreFactory.getInstance(application()).getLessonStore()
            .importPackFromUri(Uri.fromFile(archive), application().contentResolver)
    }

    private fun application(): Application = RuntimeEnvironment.getApplication() as Application
}
