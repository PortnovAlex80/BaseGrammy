package com.alexpo.grammermate.feature.pomodoro

import com.alexpo.grammermate.data.TrainingUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroHelperTest {

    @Test
    fun onTrainingSessionCompleted_preservesRemainingSecondsForEarlySummary() = runBlocking {
        var state = TrainingUiState()
        var remainingFlowValue = -1
        var pauseCalls = 0
        var completionSoundCalls = 0
        val helper = PomodoroHelper(
            stateProvider = { state },
            onUpdateState = { state = it },
            scope = CoroutineScope(Dispatchers.Unconfined),
            onPauseTraining = { pauseCalls += 1 },
            onPlayCompletionSound = { completionSoundCalls += 1 },
            onUpdatePomodoroRemaining = { remainingFlowValue = it }
        )

        helper.startPomodoro(durationMinutes = 5)
        state = state.copy(
            cardSession = state.cardSession.copy(
                correctCount = state.cardSession.correctCount + 3,
                incorrectCount = state.cardSession.incorrectCount + 1
            )
        )

        helper.onTrainingSessionCompleted()

        assertTrue("Pomodoro should be complete", state.pomodoro.isComplete)
        assertEquals("Early completion should keep remaining time for summary", 300, state.pomodoro.remainingSeconds)
        assertEquals("Timer display flow should keep remaining time until OK/cancel", 300, remainingFlowValue)
        assertEquals(4, state.pomodoro.stats.cardsShown)
        assertEquals(3, state.pomodoro.stats.cardsCorrect)
        assertEquals(1, state.pomodoro.stats.cardsIncorrect)
        assertEquals(1, pauseCalls)
        assertEquals(1, completionSoundCalls)
    }
}
