package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState

/** Pure reset rule for a lesson review. The runner owns the card queue, timer, audit and writes. */
internal object ReviewSessionTransitions {
    fun defaultInputMode(hintLevel: HintLevel): InputMode = when (hintLevel) {
        HintLevel.EASY, HintLevel.MEDIUM -> InputMode.KEYBOARD
        HintLevel.HARD -> InputMode.VOICE
    }

    fun start(
        state: TrainingUiState,
        cards: List<SessionCard>,
        hintLevel: HintLevel,
        voiceTriggerToken: Int
    ): TrainingUiState = state.copy(
        cardSession = state.cardSession.copy(
            sessionState = if (cards.isNotEmpty()) SessionState.ACTIVE else SessionState.PAUSED,
            currentCard = cards.firstOrNull(),
            currentIndex = 0,
            lastResult = null,
            answerText = null,
            incorrectAttemptsForCard = 0,
            correctCount = 0,
            incorrectCount = 0,
            activeTimeMs = 0L,
            voiceActiveMs = 0L,
            voiceWordCount = 0,
            hintCount = 0,
            voicePromptStartMs = null,
            subLessonTotal = cards.size,
            subLessonCount = 1,
            activeSubLessonIndex = 0,
            completedSubLessonCount = 0,
            subLessonFinishedToken = 0,
            wordBankWords = emptyList(),
            selectedWords = emptyList(),
            screenMode = TrainingScreenMode.NORMAL,
            isReviewMode = true,
            hintLevel = hintLevel,
            inputMode = defaultInputMode(hintLevel),
            voiceTriggerToken = voiceTriggerToken,
            returnTo = ""
        ),
        boss = state.boss.copy(bossActive = false),
        elite = state.elite.copy(eliteActive = false)
    )
}
