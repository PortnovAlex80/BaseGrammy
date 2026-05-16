package com.alexpo.grammermate.feature.pomodoro

import com.alexpo.grammermate.data.CardDifficultyRating
import com.alexpo.grammermate.data.PomodoroSessionStats
import com.alexpo.grammermate.data.PomodoroState
import com.alexpo.grammermate.data.TrainingUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PomodoroHelper(
    private val stateProvider: () -> TrainingUiState,
    private val onUpdateState: (TrainingUiState) -> Unit,
    private val scope: CoroutineScope
) {
    private var timerJob: Job? = null

    fun startPomodoro(durationMinutes: Int) {
        val totalSeconds = durationMinutes * 60
        val state = PomodoroState(
            isActive = true,
            isPaused = false,
            isComplete = false,
            selectedDurationMinutes = durationMinutes,
            remainingSeconds = totalSeconds,
            totalSeconds = totalSeconds
        )
        updatePomodoroState(state)
        startTimer()
    }

    fun pausePomodoro() {
        timerJob?.cancel()
        timerJob = null
        updatePomodoro { it.copy(isPaused = true) }
    }

    fun resumePomodoro() {
        updatePomodoro { it.copy(isPaused = false) }
        startTimer()
    }

    fun cancelPomodoro() {
        timerJob?.cancel()
        timerJob = null
        updatePomodoroState(PomodoroState())
    }

    fun completePomodoro() {
        timerJob?.cancel()
        timerJob = null
        val current = stateProvider()
        val cardSession = current.cardSession

        val stats = PomodoroSessionStats(
            cardsShown = cardSession.correctCount + cardSession.incorrectCount,
            cardsCorrect = cardSession.correctCount,
            cardsIncorrect = cardSession.incorrectCount,
            difficultyRatings = current.pomodoro.stats.difficultyRatings,
            wordsPerMinute = if (cardSession.voiceActiveMs > 0)
                cardSession.voiceWordCount / (cardSession.voiceActiveMs / 60000.0) else 0.0,
            durationMinutes = current.pomodoro.selectedDurationMinutes,
            completedAtMs = System.currentTimeMillis()
        )

        updatePomodoro {
            it.copy(
                isComplete = true,
                isActive = false,
                remainingSeconds = 0,
                stats = stats
            )
        }
    }

    fun recordDifficultyRating(rating: CardDifficultyRating) {
        updatePomodoro { state ->
            val ratings = state.stats.difficultyRatings.toMutableMap()
            ratings[rating] = (ratings[rating] ?: 0) + 1
            state.copy(
                stats = state.stats.copy(difficultyRatings = ratings),
                showRatingPrompt = false
            )
        }
    }

    fun setShowRatingPrompt(show: Boolean) {
        updatePomodoro { it.copy(showRatingPrompt = show) }
    }

    fun setShowExitConfirm(show: Boolean) {
        updatePomodoro { it.copy(showExitConfirm = show) }
    }

    fun onLifecycleStop() {
        if (stateProvider().pomodoro.isActive && !stateProvider().pomodoro.isPaused) {
            pausePomodoro()
        }
    }

    fun onLifecycleStart() {
        val pomodoro = stateProvider().pomodoro
        if (pomodoro.isActive && pomodoro.isPaused && !pomodoro.isComplete) {
            resumePomodoro()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    private fun tick() {
        val current = stateProvider().pomodoro
        if (!current.isActive || current.isPaused || current.isComplete) return

        val newRemaining = current.remainingSeconds - 1
        if (newRemaining <= 0) {
            completePomodoro()
            return
        }

        updatePomodoro { it.copy(remainingSeconds = newRemaining) }
    }

    private fun updatePomodoro(transform: (PomodoroState) -> PomodoroState) {
        val current = stateProvider()
        onUpdateState(current.copy(pomodoro = transform(current.pomodoro)))
    }

    private fun updatePomodoroState(newState: PomodoroState) {
        val current = stateProvider()
        onUpdateState(current.copy(pomodoro = newState))
    }
}
