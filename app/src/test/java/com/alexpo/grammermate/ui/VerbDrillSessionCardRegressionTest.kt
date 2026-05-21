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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SubmitResult
import com.alexpo.grammermate.data.TrainingMode
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

        renderHarness(verbVm, trainingVm, route)
        composeRule.waitForIdle()

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

        renderHarness(verbVm, trainingVm, route)
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

        renderHarness(verbVm, trainingVm, route)
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
        return VerbDrillViewModel(application, store).apply {
            injectTestCards(cards)
            reloadForPack(packId)
            setSessionSize(sessionSize)
            selectTense(tense)
            selectGroup(group)
            toggleSortByFrequency()
        }
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
                        state = trainingVm.uiState.collectAsState().value,
                        onInputChange = trainingVm.training::onInputChanged,
                        onSubmit = {
                            val beforeCard = trainingVm.uiState.value.cardSession.currentCard
                            val result = trainingVm.submitAnswer()
                            if (beforeCard is VerbDrillCard && result.accepted) {
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

        // Wait for button to exist in semantics tree
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithTag("verb_start_button", useUnmergedTree = true)
                    .assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // Click the Start button (TRUE UI click, not ViewModel bypass)
        composeRule.onNodeWithTag("verb_start_button", useUnmergedTree = true)
            .performClick()

        composeRule.waitForIdle()

        // Wait for session creation via UI callback
        composeRule.waitUntil(timeoutMillis = 5_000) {
            verbVm.uiState.value.session?.cards?.isNotEmpty() == true
        }

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
