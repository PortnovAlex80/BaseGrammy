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
    private val scope: CoroutineScope,
    private val onPauseTraining: () -> Unit = {},
    private val onResumeTraining: () -> Unit = {},
    private val onPlayCompletionSound: () -> Unit = {},
    private val onUpdatePomodoroRemaining: ((Int) -> Unit)? = null
) {
    private var timerJob: Job? = null
    /** Tracks the true remaining seconds independently from main state (which is updated periodically). */
    private var trueRemainingSeconds: Int = 0

    fun startPomodoro(durationMinutes: Int) {
        val current = stateProvider()
        val totalSeconds = durationMinutes * 60
        val state = PomodoroState(
            isActive = true,
            isPaused = false,
            isComplete = false,
            selectedDurationMinutes = durationMinutes,
            remainingSeconds = totalSeconds,
            totalSeconds = totalSeconds,
            baselineCorrect = current.cardSession.correctCount,
            baselineIncorrect = current.cardSession.incorrectCount
        )
        updatePomodoroState(state)
        trueRemainingSeconds = totalSeconds
        onUpdatePomodoroRemaining?.invoke(totalSeconds)
        startTimer()
    }

    fun pausePomodoro() {
        timerJob?.cancel()
        timerJob = null
        onPauseTraining()
        updatePomodoro { it.copy(isPaused = true, remainingSeconds = trueRemainingSeconds) }
        onUpdatePomodoroRemaining?.invoke(trueRemainingSeconds)
    }

    fun resumePomodoro() {
        onResumeTraining()
        updatePomodoro { it.copy(isPaused = false, remainingSeconds = trueRemainingSeconds) }
        onUpdatePomodoroRemaining?.invoke(trueRemainingSeconds)
        startTimer()
    }

    fun cancelPomodoro() {
        timerJob?.cancel()
        timerJob = null
        trueRemainingSeconds = 0
        updatePomodoroState(PomodoroState())
        onUpdatePomodoroRemaining?.invoke(0)
    }

    fun completePomodoro() {
        timerJob?.cancel()
        timerJob = null
        onPauseTraining()
        val current = stateProvider()
        val cardSession = current.cardSession
        val pomodoro = current.pomodoro

        val sessionCorrect = (cardSession.correctCount - pomodoro.baselineCorrect).coerceAtLeast(0)
        val sessionIncorrect = (cardSession.incorrectCount - pomodoro.baselineIncorrect).coerceAtLeast(0)
        val remainingAtCompletion = trueRemainingSeconds.coerceAtLeast(0)

        val stats = PomodoroSessionStats(
            cardsShown = sessionCorrect + sessionIncorrect,
            cardsCorrect = sessionCorrect,
            cardsIncorrect = sessionIncorrect,
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
                remainingSeconds = remainingAtCompletion,
                stats = stats
            )
        }
        trueRemainingSeconds = remainingAtCompletion
        onUpdatePomodoroRemaining?.invoke(remainingAtCompletion)
        onPlayCompletionSound()
    }

    /**
     * Called when the training session completes (cards finished before timer).
     * Triggers Pomodoro completion to show the summary with remaining time.
     */
    fun onTrainingSessionCompleted() {
        if (stateProvider().pomodoro.isActive && !stateProvider().pomodoro.isComplete) {
            completePomodoro()
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
            // Sync trueRemainingSeconds from main state on lifecycle resume
            trueRemainingSeconds = pomodoro.remainingSeconds
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

        trueRemainingSeconds -= 1
        if (trueRemainingSeconds <= 0) {
            trueRemainingSeconds = 0
            completePomodoro()
            return
        }

        // Push high-frequency remaining seconds to separate flow (for UI countdown display)
        onUpdatePomodoroRemaining?.invoke(trueRemainingSeconds)

        // Update main state only on significant boundaries (every 10 seconds) for atomicity
        if (trueRemainingSeconds % 10 == 0) {
            updatePomodoro { it.copy(remainingSeconds = trueRemainingSeconds) }
        }
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
