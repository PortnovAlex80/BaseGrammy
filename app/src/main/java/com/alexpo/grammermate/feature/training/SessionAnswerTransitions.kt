package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingUiState

/** Pure correct-answer rules for normal, boss and elite sessions. The runner owns all writes and effects. */
internal object SessionAnswerTransitions {
    data class VoiceContribution(val activeMs: Long, val words: Int) {
        companion object {
            fun from(shouldRecord: Boolean, durationMs: Long?, words: Int): VoiceContribution =
                if (shouldRecord) VoiceContribution(durationMs ?: 0L, words) else VoiceContribution(0L, 0)
        }
    }

    private fun acceptedCard(
        current: CardSessionState,
        voice: VoiceContribution,
        lastResult: Boolean?
    ): CardSessionState = current.copy(
        correctCount = current.correctCount + 1,
        lastResult = lastResult,
        incorrectAttemptsForCard = 0,
        answerText = null,
        voiceActiveMs = current.voiceActiveMs + voice.activeMs,
        voiceWordCount = current.voiceWordCount + voice.words,
        voicePromptStartMs = null
    )

    fun bossLast(current: CardSessionState, voice: VoiceContribution): CardSessionState =
        acceptedCard(current, voice, lastResult = null)

    fun bossMid(current: CardSessionState, voice: VoiceContribution): CardSessionState =
        acceptedCard(current, voice, lastResult = true)

    fun normalMid(current: CardSessionState, voice: VoiceContribution): CardSessionState =
        acceptedCard(current, voice, lastResult = true)

    fun normalLast(
        state: TrainingUiState,
        voice: VoiceContribution,
        actualCompletedCount: Int
    ): TrainingUiState {
        val card = state.cardSession
        return state.copy(cardSession = completedSubLessonCard(
            acceptedCard(card, voice, lastResult = null), actualCompletedCount
        ))
    }

    /** Explicit Next completes the sub-lesson without crediting a correct answer. */
    fun normalCompleteViaNext(state: TrainingUiState, actualCompletedCount: Int): TrainingUiState {
        val card = state.cardSession
        return state.copy(cardSession = completedSubLessonCard(card.copy(
            lastResult = null,
            incorrectAttemptsForCard = 0,
            answerText = null,
            voicePromptStartMs = null
        ), actualCompletedCount))
    }

    private fun completedSubLessonCard(
        card: CardSessionState,
        actualCompletedCount: Int
    ): CardSessionState {
        val nextCompleted = (card.completedSubLessonCount + 1).coerceAtMost(card.subLessonCount)
        val preservedActiveIndex = maxOf(card.activeSubLessonIndex, actualCompletedCount)
        val finalActiveIndex = preservedActiveIndex.coerceAtMost((card.subLessonCount - 1).coerceAtLeast(0))
        return card.copy(
            sessionState = SessionState.PAUSED,
            currentIndex = 0,
            activeSubLessonIndex = finalActiveIndex,
            completedSubLessonCount = maxOf(nextCompleted, actualCompletedCount),
            subLessonFinishedToken = card.subLessonFinishedToken + 1
        )
    }

    data class EliteCompletion(
        val state: TrainingUiState,
        val stepIndex: Int,
        val speed: Double,
        val previousBest: Double
    )

    fun eliteLast(
        state: TrainingUiState,
        voice: VoiceContribution,
        stepCount: Int
    ): EliteCompletion {
        val card = state.cardSession
        // Existing behavior scores the step before adding the final answer's voice metrics.
        val minutes = card.voiceActiveMs / 60_000.0
        val speed = if (minutes <= 0.0) 0.0 else card.voiceWordCount / minutes
        val bestSpeeds = if (state.elite.eliteBestSpeeds.size >= stepCount) {
            state.elite.eliteBestSpeeds.take(stepCount)
        } else {
            state.elite.eliteBestSpeeds + List(stepCount - state.elite.eliteBestSpeeds.size) { 0.0 }
        }
        val stepIndex = state.elite.eliteStepIndex.coerceIn(0, stepCount - 1)
        val previousBest = bestSpeeds[stepIndex]
        val nextSpeeds = bestSpeeds.toMutableList().apply {
            if (speed > previousBest) this[stepIndex] = speed
        }
        val nextState = state.copy(
            cardSession = acceptedCard(card, voice, lastResult = null).copy(
                sessionState = SessionState.PAUSED,
                currentIndex = 0
            ),
            elite = state.elite.copy(
                eliteActive = false,
                eliteStepIndex = (stepIndex + 1) % stepCount,
                eliteBestSpeeds = nextSpeeds,
                eliteFinishedToken = state.elite.eliteFinishedToken + 1
            )
        )
        return EliteCompletion(nextState, stepIndex, speed, previousBest)
    }
}
