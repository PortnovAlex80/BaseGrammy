package com.alexpo.grammermate.ui

import android.app.Application
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.testharness.FakeVerbDrillStore
import com.alexpo.grammermate.ui.helpers.createTestVerbCards
import com.alexpo.grammermate.ui.screens.TrainingScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class VerbDrillSessionCardRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var store: FakeVerbDrillStore

    private val packId = "test_pack_session"

    @Before
    fun setup() {
        application = RuntimeEnvironment.getApplication()
        store = FakeVerbDrillStore()
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun final_answer_creates_backup_after_linear_completion() {
        val harness = renderPreparedHarness()
        startVerbSessionThroughUi(harness)
        repeat(4) { answerCurrentCardCorrectly(harness.trainingVm) }
        val backupMetadata = File(application.filesDir, "grammarmate/backups/backup_latest/metadata.txt")
        backupMetadata.delete()
        answerCurrentCardCorrectly(harness.trainingVm)

        assertEquals(SessionState.PAUSED, harness.trainingVm.uiState.value.cardSession.sessionState)
        composeRule.waitUntil(timeoutMillis = 5_000) { backupMetadata.exists() }
        assertTrue(backupMetadata.length() > 0L)
    }

    @Test
    fun repeat_replays_saved_batch_in_same_order() {
        val harness = renderPreparedHarness()

        startVerbSessionThroughUi(harness)
        val firstBatchIds = verbSessionIds(harness.verbVm)

        repeat(3) {
            answerCurrentCardCorrectly(harness.trainingVm)
        }
        assertEquals(3, store.loadLastSession()!!.todayShownCardIds.size)

        exitTrainingThroughUi()
        composeRule.onNodeWithTag("session_card").assertIsDisplayed()
        composeRule.onNodeWithTag("repeat_button").performClick()
        waitForTrainingRoute(harness)

        assertEquals(
            "Repeat must replay the persisted batch order exactly",
            firstBatchIds,
            verbSessionIds(harness.verbVm)
        )
    }

    @Test
    fun continue_random_mode_excludes_checked_cards_but_not_navigation_only_cards() {
        val harness = renderPreparedHarness()

        startVerbSessionThroughUi(harness)
        val batch = verbSessionIds(harness.verbVm)
        repeat(2) {
            answerCurrentCardCorrectly(harness.trainingVm)
        }

        val navigationOnlyCardId = currentTrainingCardId(harness.trainingVm)
        clickNext()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            currentTrainingCardId(harness.trainingVm) == batch[3]
        }

        val shownAfterNavigation = store.loadLastSession()!!.todayShownCardIds
        assertEquals("Only checked cards should be counted as shown", 2, shownAfterNavigation.size)
        assertFalse(
            "Navigation-only card must not be counted as shown",
            navigationOnlyCardId in shownAfterNavigation
        )

        exitTrainingThroughUi()
        composeRule.onNodeWithTag("session_card").assertIsDisplayed()
        composeRule.onNodeWithTag("continue_button").performClick()
        waitForTrainingRoute(harness)

        val continueBatchIds = verbSessionIds(harness.verbVm).toSet()
        assertTrue(
            "Continue must exclude checked cards",
            continueBatchIds.intersect(shownAfterNavigation).isEmpty()
        )
        assertFalse(
            "Navigation-only card should remain eligible because it was not checked",
            navigationOnlyCardId in store.loadLastSession()!!.todayShownCardIds
        )
    }

    @Test
    fun continue_frequency_mode_resumes_with_next_ranked_cards_in_order() {
        val harness = renderPreparedHarness()
        enableSortByFrequencyThroughUi(harness.verbVm)

        startVerbSessionThroughUi(harness)
        assertEquals(
            listOf("test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"),
            verbSessionIds(harness.verbVm)
        )

        repeat(2) {
            answerCurrentCardCorrectly(harness.trainingVm)
        }
        val navigationOnlyCardId = currentTrainingCardId(harness.trainingVm)
        assertEquals("test_verb_3", navigationOnlyCardId)

        clickNext()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            currentTrainingCardId(harness.trainingVm) == "test_verb_4"
        }

        exitTrainingThroughUi()
        composeRule.onNodeWithTag("session_card").assertIsDisplayed()
        composeRule.onNodeWithTag("continue_button").performClick()
        waitForTrainingRoute(harness)

        assertEquals(
            "Frequency Continue should exclude only checked cards and keep ranked order",
            listOf("test_verb_3", "test_verb_4", "test_verb_5", "test_verb_6", "test_verb_7"),
            verbSessionIds(harness.verbVm)
        )
    }

    @Test
    fun continue_excludes_hint_and_wrong_then_next_but_keeps_navigation_only_card() {
        val harness = renderPreparedHarness()
        enableSortByFrequencyThroughUi(harness.verbVm)
        startVerbSessionThroughUi(harness)
        val batch = verbSessionIds(harness.verbVm)
        assertEquals(listOf("test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"), batch)

        // Showing an answer and then choosing Next explicitly completes card 1.
        composeRule.onNodeWithTag("show_answer_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertNotNull(harness.trainingVm.uiState.value.cardSession.answerText)
        assertEquals(5, harness.trainingVm.training.getSessionCards().size)
        clickNext()
        composeRule.waitForIdle()
        assertEquals("Next click must reach the production callback", 1, harness.nextClickCount.get())
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            currentTrainingCardId(harness.trainingVm) == batch[1]
        }
        assertEquals(batch[1], currentTrainingCardId(harness.trainingVm))
        assertEquals(setOf(batch[0]), store.loadLastSession()!!.todayShownCardIds)

        // A wrong Check followed by Next is an explicit skip, not mere browsing.
        clickPause()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("input_field").performTextInput("definitely wrong")
        composeRule.onNodeWithTag("check_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(false, harness.trainingVm.uiState.value.cardSession.lastResult)
        clickNext()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            currentTrainingCardId(harness.trainingVm) == batch[2]
        }
        assertEquals(batch[2], currentTrainingCardId(harness.trainingVm))
        assertEquals(setOf(batch[0], batch[1]), store.loadLastSession()!!.todayShownCardIds)
        assertEquals(2, harness.verbVm.uiState.value.session!!.incorrectCount)

        // Browsing past card 3 without Check or hint must leave it eligible.
        clickNext()
        composeRule.waitForIdle()
        assertEquals(setOf(batch[0], batch[1]), store.loadLastSession()!!.todayShownCardIds)

        exitTrainingThroughUi()
        composeRule.onNodeWithTag("session_card").assertIsDisplayed()
        composeRule.onNodeWithTag("continue_button").performClick()
        waitForTrainingRoute(harness)

        assertEquals(
            "Continue excludes completed cards while retaining navigation-only visits",
            listOf(batch[2], batch[3], batch[4], "test_verb_6", "test_verb_7"),
            verbSessionIds(harness.verbVm)
        )
    }

    @Test
    fun reset_clears_saved_session_keeps_progress_and_allows_ranked_restart_from_beginning() {
        val harness = renderPreparedHarness()
        enableSortByFrequencyThroughUi(harness.verbVm)

        startVerbSessionThroughUi(harness)
        assertEquals(
            listOf("test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"),
            verbSessionIds(harness.verbVm)
        )

        repeat(2) {
            answerCurrentCardCorrectly(harness.trainingVm)
        }
        val shownBeforeReset = allTodayShownIds()
        assertEquals(setOf("test_verb_1", "test_verb_2"), shownBeforeReset)

        exitTrainingThroughUi()
        composeRule.onNodeWithTag("session_card").assertIsDisplayed()
        composeRule.onNodeWithTag("reset_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("session_card").assertIsNotDisplayed()
        assertNull("Reset should delete only the saved session", store.loadLastSession())
        assertEquals(
            "Reset should not delete verb drill shown-card progress",
            shownBeforeReset,
            allTodayShownIds()
        )

        enableSortByFrequencyThroughUi(harness.verbVm)
        startVerbSessionThroughUi(harness)
        assertEquals(
            "After reset, a fresh ranked start should begin from the top of the deck again",
            listOf("test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"),
            verbSessionIds(harness.verbVm)
        )
    }

    private fun renderPreparedHarness(): Harness {
        // The session uses the real CSV-style alternatives supported by the validator.
        // Entering one alternative keeps the explicit Check action available: keyboard
        // auto-submit compares against the unsplit source answer.
        val cards = createTestVerbCards(12).map { card ->
            card.copy(answer = "${card.answer}+${card.answer} alternate")
        }
        store.setCards(packId, "it", cards)

        val verbVm = VerbDrillViewModel(application, store)
        verbVm.injectTestCards(cards)
        verbVm.reloadForPack(packId)
        verbVm.setSessionSize(5)

        val trainingVm = TrainingViewModel(application)
        val route = mutableStateOf(TestRoute.VERB)
        val nextClickCount = AtomicInteger(0)
        val harness = Harness(verbVm, trainingVm, route, nextClickCount)

        composeRule.setContent {
            MaterialTheme {
                when (route.value) {
                    TestRoute.VERB -> VerbDrillScreen(
                        viewModel = verbVm,
                        onBack = {},
                        onStartSession = { sessionCards ->
                            trainingVm.startVerbDrillSession(sessionCards)
                            trainingVm.setReturnTo(VERB_DRILL_ROUTE)
                            trainingVm.training.setInputMode(InputMode.KEYBOARD)
                            route.value = TestRoute.TRAINING
                        }
                    )

                    TestRoute.TRAINING -> TrainingScreen(
                        state = trainingVm.uiState.collectAsState().value,
                        inputText = trainingVm.inputText.collectAsState().value,
                        onInputChange = trainingVm.training::onInputChanged,
                        onSubmit = {
                            val result = trainingVm.submitAnswer()
                            val isVerbDrillMode =
                                trainingVm.uiState.value.cardSession.screenMode == TrainingScreenMode.VERB_DRILL
                            if (isVerbDrillMode && result.accepted) {
                                verbVm.submitCorrectAnswer()
                            }
                            result
                        },
                        onPrev = {
                            verbVm.prevCard()
                            trainingVm.navigatePrev()
                        },
                        onNext = {
                            nextClickCount.incrementAndGet()
                            val state = trainingVm.uiState.value.cardSession
                            if (
                                state.currentCard is VerbDrillCard &&
                                (state.lastResult == false || state.answerText != null)
                            ) {
                                verbVm.markCardCompleted()
                            }
                            trainingVm.navigateNext()
                        },
                        onTogglePause = trainingVm::togglePause,
                        onRequestExit = {
                            verbVm.persistSessionState()
                            verbVm.refreshLastSessionContext()
                            trainingVm.exitVerbDrillSession()
                            route.value = TestRoute.VERB
                        },
                        onOpenSettings = {},
                        onShowSettings = {},
                        onSelectLesson = {},
                        onSelectMode = { mode: TrainingMode -> trainingVm.selectMode(mode) },
                        onSetInputMode = trainingVm.training::setInputMode,
                        onShowAnswer = trainingVm::showAnswer,
                        onVoicePromptStarted = {},
                        onSelectWordFromBank = trainingVm.training::selectWordFromBank,
                        onRemoveLastWord = trainingVm.training::removeLastSelectedWord,
                        onTtsSpeak = {},
                        onSessionDone = {
                            verbVm.persistSessionState()
                            verbVm.refreshLastSessionContext()
                            trainingVm.exitVerbDrillSession()
                            route.value = TestRoute.VERB
                        },
                        lessonTitle = null
                    )
                }
            }
        }

        waitForSelectionReady(verbVm)
        return harness
    }

    private fun waitForSelectionReady(verbVm: VerbDrillViewModel) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val state = verbVm.uiState.value
            !state.isLoading && state.totalCards > 0 && state.lastSessionContext == null
        }
        composeRule.onNodeWithTag("verb_start_button").performScrollTo().assertIsDisplayed()
    }

    private fun enableSortByFrequencyThroughUi(verbVm: VerbDrillViewModel) {
        waitForSelectionReady(verbVm)
        if (!verbVm.uiState.value.sortByFrequency) {
            composeRule.onNodeWithTag("sort_by_frequency_checkbox").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                verbVm.uiState.value.sortByFrequency
            }
        }
    }

    private fun startVerbSessionThroughUi(harness: Harness) {
        waitForSelectionReady(harness.verbVm)
        composeRule.onNodeWithTag("verb_start_button").performScrollTo().performClick()
        waitForTrainingRoute(harness)
    }

    private fun waitForTrainingRoute(harness: Harness) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            harness.route.value == TestRoute.TRAINING &&
                harness.trainingVm.uiState.value.cardSession.screenMode == TrainingScreenMode.VERB_DRILL &&
                harness.trainingVm.uiState.value.cardSession.currentCard != null &&
                harness.verbVm.uiState.value.session?.cards?.isNotEmpty() == true
        }
    }

    private fun answerCurrentCardCorrectly(trainingVm: TrainingViewModel) {
        val card = trainingVm.uiState.value.cardSession.currentCard
        val previousIndex = trainingVm.uiState.value.cardSession.currentIndex
        assertNotNull("No current card on TrainingScreen", card)
        composeRule.onNodeWithTag("input_field").performTextClearance()
        composeRule.onNodeWithTag("input_field").performTextInput(card!!.acceptedAnswers.first().substringBefore('+'))
        composeRule.onNodeWithTag("check_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val session = trainingVm.uiState.value.cardSession
            session.currentIndex > previousIndex || session.sessionState == SessionState.PAUSED
        }
    }

    private fun clickNext() {
        composeRule.onNodeWithTag("next_button").performScrollTo()
        composeRule.onNodeWithContentDescription(application.getString(R.string.content_desc_next))
            .assertIsEnabled().performClick()
    }

    private fun clickPause() {
        composeRule.onNodeWithTag("pause_button").performScrollTo()
        composeRule.onNodeWithContentDescription(application.getString(R.string.content_desc_play))
            .assertIsEnabled().performClick()
    }

    private fun exitTrainingThroughUi() {
        composeRule.onNodeWithTag("exit_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("exit_confirm_button", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("exit_confirm_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun verbSessionIds(verbVm: VerbDrillViewModel): List<String> {
        return verbVm.uiState.value.session!!.cards.map { it.id }
    }

    private fun currentTrainingCardId(trainingVm: TrainingViewModel): String {
        return trainingVm.uiState.value.cardSession.currentCard!!.id
    }

    private fun allTodayShownIds(): Set<String> {
        return store.loadProgress().values.flatMap { it.todayShownCardIds }.toSet()
    }

    private data class Harness(
        val verbVm: VerbDrillViewModel,
        val trainingVm: TrainingViewModel,
        val route: MutableState<TestRoute>,
        val nextClickCount: AtomicInteger
    )

    private enum class TestRoute {
        VERB,
        TRAINING
    }

    private companion object {
        const val VERB_DRILL_ROUTE = "verb_drill"
    }
}
