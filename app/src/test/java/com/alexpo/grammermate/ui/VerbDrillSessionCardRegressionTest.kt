package com.alexpo.grammermate.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SubmitResult
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
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class VerbDrillSessionCardRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var store: FakeVerbDrillStore

    private val packId = "test_pack_session"
    private val tense = "Presente"
    private val group = "regular_are"

    @Before
    fun setup() {
        application = RuntimeEnvironment.getApplication()
        store = FakeVerbDrillStore()
        // Ensure the store is completely clean before each test
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun repeat_replays_last_batch_after_checked_cards() {
        val cards = createTestVerbCards(12)
        val verbVm = preparedVerbVm(cards, sessionSize = 5)
        val trainingVm = TrainingViewModel(application)
        val route = mutableStateOf(TestRoute.VERB)

        // Configure ViewModel BEFORE rendering UI to avoid LaunchedEffect interference
        verbVm.setSessionSize(5)

        renderHarness(verbVm, trainingVm, route)
        composeRule.waitForIdle()

        // Wait for ViewModel async operations to complete
        // AND lastSessionContext to be null (so start button is displayed)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val state = verbVm.uiState.value
            !state.isLoading && state.totalCards > 0 && state.lastSessionContext == null
        }

        startVerbSessionThroughUi(verbVm, trainingVm, route)
        val firstBatchIds = verbVm.uiState.value.session!!.cards.map { it.id }

        repeat(3) {
            answerCurrentCardCorrectly(trainingVm)
        }
        assertEquals(3, store.loadLastSession()!!.todayShownCardIds.size)

        exitTrainingThroughUi(verbVm, trainingVm, route)
        composeRule.onNodeWithTag("session_card").assertIsDisplayed()
        composeRule.onNodeWithTag("repeat_button").performClick()
        composeRule.waitForIdle()

        val repeatedBatchIds = verbVm.uiState.value.session!!.cards.map { it.id }
        assertEquals(
            "Repeat should replay the full saved batch in the same order",
            firstBatchIds,
            repeatedBatchIds
        )
    }

    @Test
    fun continue_excludes_checked_cards_but_not_navigation_only_cards() {
        val cards = createTestVerbCards(12)
        val verbVm = preparedVerbVm(cards, sessionSize = 5)
        val trainingVm = TrainingViewModel(application)
        val route = mutableStateOf(TestRoute.VERB)

        // Configure ViewModel BEFORE rendering UI
        verbVm.setSessionSize(5)

        renderHarness(verbVm, trainingVm, route)
        composeRule.waitForIdle()

        // Wait for ViewModel async operations to complete
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val state = verbVm.uiState.value
            !state.isLoading && state.totalCards > 0
        }

        composeRule.waitForIdle()

        startVerbSessionThroughUi(verbVm, trainingVm, route)

        repeat(2) {
            answerCurrentCardCorrectly(trainingVm)
        }
        val navigationOnlyCardId = trainingVm.uiState.value.cardSession.currentCard!!.id
        composeRule.onNodeWithTag("next_button").performClick()
        composeRule.waitForIdle()

        val shownAfterNavigation = store.loadLastSession()!!.todayShownCardIds
        assertEquals("Only checked cards should be counted as shown", 2, shownAfterNavigation.size)
        assertFalse(
            "Navigation-only card must not be counted as shown",
            navigationOnlyCardId in shownAfterNavigation
        )

        exitTrainingThroughUi(verbVm, trainingVm, route)
        composeRule.onNodeWithTag("session_card").assertIsDisplayed()
        composeRule.onNodeWithTag("continue_button").performClick()
        composeRule.waitForIdle()

        val continueBatchIds = verbVm.uiState.value.session!!.cards.map { it.id }.toSet()
        assertTrue(
            "Continue must not include already checked cards",
            continueBatchIds.intersect(shownAfterNavigation).isEmpty()
        )
        assertFalse(
            "Navigation-only card should remain eligible for future batches",
            navigationOnlyCardId in store.loadLastSession()!!.todayShownCardIds
        )
    }

    @Test
    fun reset_hides_session_card_but_keeps_progress() {
        val cards = createTestVerbCards(12)
        val verbVm = preparedVerbVm(cards, sessionSize = 5)
        val trainingVm = TrainingViewModel(application)
        val route = mutableStateOf(TestRoute.VERB)

        // Configure ViewModel BEFORE rendering UI
        verbVm.setSessionSize(5)

        renderHarness(verbVm, trainingVm, route)
        composeRule.waitForIdle()

        // Wait for ViewModel async operations to complete
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val state = verbVm.uiState.value
            !state.isLoading && state.totalCards > 0
        }

        composeRule.waitForIdle()

        startVerbSessionThroughUi(verbVm, trainingVm, route)
        repeat(2) {
            answerCurrentCardCorrectly(trainingVm)
        }
        val shownBeforeReset = allTodayShownIds()
        assertEquals(2, shownBeforeReset.size)

        exitTrainingThroughUi(verbVm, trainingVm, route)
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
    }

    private fun preparedVerbVm(cards: List<VerbDrillCard>, sessionSize: Int): VerbDrillViewModel {
        store.setCards(packId, "it", cards)
        val vm = VerbDrillViewModel(application, store)
        vm.injectTestCards(cards)
        vm.reloadForPack(packId) // Triggers async loadCards()
        // Note: ViewModel configuration will be done after UI rendering to ensure async operations complete
        return vm
    }

    private fun renderHarness(
        verbVm: VerbDrillViewModel,
        trainingVm: TrainingViewModel,
        route: MutableState<TestRoute>
    ) {
        composeRule.setContent {
            MaterialTheme {
                when (route.value) {
                    TestRoute.VERB -> VerbDrillScreen(
                        viewModel = verbVm,
                        onBack = {},
                        onStartSession = { cards ->
                            trainingVm.startVerbDrillSession(cards)
                            trainingVm.setReturnTo(VERB_DRILL_ROUTE)
                            trainingVm.training.setInputMode(InputMode.KEYBOARD)
                            route.value = TestRoute.TRAINING
                        }
                    )

                    TestRoute.TRAINING -> TrainingScreen(
                        state = trainingVm.uiState.collectAsState().value,  // Note: using .value directly for test harness
                        onInputChange = trainingVm.training::onInputChanged,
                        onSubmit = {
                            val result = trainingVm.submitAnswer()
                            // Check for VerbDrillSession mode instead of card type
                            val isVerbDrillMode = trainingVm.uiState.value.cardSession.screenMode == TrainingScreenMode.VERB_DRILL
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
                        onRequestExit = { exitTrainingThroughUi(verbVm, trainingVm, route) },
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
                        onSessionDone = { exitTrainingThroughUi(verbVm, trainingVm, route) }
                    )
                }
            }
        }
    }

    private fun startVerbSessionThroughUi(
        verbVm: VerbDrillViewModel,
        trainingVm: TrainingViewModel,
        route: MutableState<TestRoute>? = null
    ) {
        composeRule.waitForIdle()

        // Wait for ViewModel to be fully ready with totalCards calculated
        // AND lastSessionContext to be null (so start button is displayed)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val state = verbVm.uiState.value
            !state.isLoading && state.totalCards > 0 && state.lastSessionContext == null
        }

        // Force multiple recomposition cycles with waitForIdle
        repeat(3) {
            composeRule.waitForIdle()
        }

        // TRUE UI click only - no fallback to direct ViewModel calls
        // Verify start button is shown (not SessionCard)
        val state = verbVm.uiState.value

        // Debug: Check all conditions for button display
        println("DEBUG: allDoneToday=${state.allDoneToday}, lastSessionContext=${state.lastSessionContext}, totalCards=${state.totalCards}, availableTenses=${state.availableTenses.size}, availableGroups=${state.availableGroups.size}")

        if (state.lastSessionContext != null) {
            throw AssertionError("Expected lastSessionContext to be null so start button is shown, but was: ${state.lastSessionContext}")
        }

        // Wait until the start button exists (try BOTH merged and unmerged trees)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val mergedNodes = composeRule.onAllNodesWithTag("verb_start_button", useUnmergedTree = false)
                .fetchSemanticsNodes()
            val unmergedNodes = composeRule.onAllNodesWithTag("verb_start_button", useUnmergedTree = true)
                .fetchSemanticsNodes()
            val found = mergedNodes.isNotEmpty() || unmergedNodes.isNotEmpty()
            if (!found) {
                val s = verbVm.uiState.value
                println("DEBUG: Button not found yet. allDoneToday=${s.allDoneToday}, lastSessionContext=${s.lastSessionContext}, totalCards=${s.totalCards}, merged=${mergedNodes.size}, unmerged=${unmergedNodes.size}")
            }
            found
        }

        // Try clicking with unmerged tree first, fall back to merged tree
        try {
            composeRule.onNodeWithTag("verb_start_button", useUnmergedTree = true)
                .performClick()
        } catch (e: Exception) {
            println("DEBUG: Failed to click with unmerged tree, trying merged tree")
            composeRule.onNodeWithTag("verb_start_button", useUnmergedTree = false)
                .performClick()
        }

        composeRule.waitForIdle()

        // Wait for route change if applicable
        route?.let {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                it.value == TestRoute.TRAINING
            }
        }
    }

    private fun answerCurrentCardCorrectly(trainingVm: TrainingViewModel) {
        val card = trainingVm.uiState.value.cardSession.currentCard
        assertNotNull("No current card on TrainingScreen", card)
        composeRule.onNodeWithTag("input_field").performTextClearance()
        composeRule.onNodeWithTag("input_field").performTextInput(card!!.acceptedAnswers.first())
        composeRule.onNodeWithTag("check_button").performClick()
        composeRule.waitForIdle()
    }

    private fun exitTrainingThroughUi(
        verbVm: VerbDrillViewModel,
        trainingVm: TrainingViewModel,
        route: MutableState<TestRoute>
    ) {
        verbVm.persistSessionState()
        verbVm.refreshLastSessionContext()
        route.value = TestRoute.VERB
        trainingVm.exitVerbDrillSession()
        composeRule.waitForIdle()
    }

    private fun allTodayShownIds(): Set<String> {
        return store.loadProgress().values.flatMap { it.todayShownCardIds }.toSet()
    }

    private enum class TestRoute {
        VERB,
        TRAINING
    }

    private companion object {
        const val VERB_DRILL_ROUTE = "verb_drill"
    }
}
