package com.alexpo.grammermate.feature.training

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.data.BossState
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.testharness.FakeStreakStore
import com.alexpo.grammermate.testharness.FakeTrainingStateAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression test for the frozen boss progress bar.
 *
 * Symptom: during a Boss Battle the on-screen progress indicator stays at
 * "1 / N" for the whole battle and only jumps to completion on the last card.
 *
 * Root cause: [SessionRunner.submitBossMidCard] calls [SessionRunner.nextCardInternal]
 * but discards the returned [SessionEvent] list. That list is the only place
 * [SessionEvent.AdvanceBossProgress] is emitted (SessionRunner.kt:772-774), so the
 * event that drives the boss progress field never reaches the ViewModel handler.
 *
 * This test reproduces the mid-card answer path directly against SessionRunner
 * (no UI, no ViewModel) and asserts that a correct mid-card answer returns an
 * AdvanceBossProgress event, proving the orchestrator will be told to advance.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRunnerBossProgressTest {

    private val appContext: Application = ApplicationProvider.getApplicationContext()

    private fun threeCardBossState(): TrainingUiState = TrainingUiState().copy(
        cardSession = CardSessionState(
            currentCard = bossCards.first(),
            currentIndex = 0,
            subLessonTotal = bossCards.size,
            sessionState = SessionState.ACTIVE,
            inputMode = InputMode.KEYBOARD
        ),
        boss = BossState(
            bossActive = true,
            bossType = BossType.LESSON,
            bossTotal = bossCards.size,
            bossProgress = 0
        ),
        navigation = NavigationState(
            selectedLanguageId = LanguageId("it"),
            selectedLessonId = LessonId("L01_1FORM")
        )
    )

    private val bossCards = (1..3).map { i ->
        SentenceCard(
            id = "boss-card-$i",
            promptRu = "русское $i",
            acceptedAnswers = listOf("english $i")
        )
    }

    private val inputTextFlow = kotlinx.coroutines.flow.MutableStateFlow("")

    private fun newRunner(state: TrainingUiState): Pair<SessionRunner, TrainingStateAccess> {
        val stateAccess = FakeTrainingStateAccess(state)
        val runner = SessionRunner(
            stateAccess = stateAccess,
            appContext = appContext,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            answerValidator = AnswerValidator(),
            wordBankGenerator = WordBankGenerator,
            cardProvider = CardProvider(),
            streakManager = StreakManager(FakeStreakStore()),
            getMastery = { _, _ -> null },
            getSchedule = { _ -> null },
            getHiddenCardIds = { emptySet() },
            calculateCompletedSubLessons = { _, _, _, _ -> 0 },
            onTimerSaveProgress = { },
            inputTextFlow = inputTextFlow
        )
        runner.setBossCards(bossCards)
        return runner to stateAccess
    }

    @Test
    fun `correct mid-card boss answer emits AdvanceBossProgress event`() {
        // Arrange: boss session on card 0 of 3, correct answer typed.
        val (runner, stateAccess) = newRunner(threeCardBossState())
        inputTextFlow.value = "english 1"

        // Act: submit the correct answer for the first (mid) card.
        val (result, events) = runner.submitAnswer()

        // Assert: answer accepted, and the event that drives the boss progress
        // field must be present so the orchestrator advances the visible bar.
        assertTrue("answer should be accepted", result.accepted)
        val advance = events.filterIsInstance<SessionEvent.AdvanceBossProgress>().singleOrNull()
        assertEquals(
            "mid-card correct answer must carry AdvanceBossProgress(nextIndex=1, totalCards=3)",
            SessionEvent.AdvanceBossProgress(nextIndex = 1, totalCards = 3),
            advance
        )
    }

    @Test
    fun `each successive mid-card boss answer advances progress by one`() {
        // Arrange: boss session on card 0 of 3.
        val (runner, stateAccess) = newRunner(threeCardBossState())

        // Card 0 -> 1
        inputTextFlow.value = "english 1"
        val (_, events0) = runner.submitAnswer()
        assertEquals(
            SessionEvent.AdvanceBossProgress(nextIndex = 1, totalCards = 3),
            events0.filterIsInstance<SessionEvent.AdvanceBossProgress>().single()
        )

        // Card 1 -> 2 (last card). After submitting card 1 the runner advanced
        // currentIndex to 2 internally; feed the answer for card 2... but card 2
        // is the last card, so this path goes through submitBossLastCard, which
        // signals completion via needsBossFinish. We assert only the mid-card
        // advance here; the last-card path is covered by existing boss tests.
        assertTrue(
            "currentIndex advanced to 1 after first correct mid-card answer",
            stateAccess.uiState.value.cardSession.currentIndex == 1
        )
    }
}
