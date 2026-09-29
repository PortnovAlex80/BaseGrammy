package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.VerbDrillCard

/** State rules shared by verb drill and daily linear card sessions. No effects or stored state. */
internal object LinearSessionTransitions {
    fun handles(mode: TrainingScreenMode): Boolean = mode in listOf(
        TrainingScreenMode.VERB_DRILL,
        TrainingScreenMode.DAILY_TRANSLATE,
        TrainingScreenMode.DAILY_VERBS
    )

    data class VoiceContribution(val activeMs: Long, val words: Int) {
        companion object {
            fun from(shouldRecord: Boolean, durationMs: Long?, words: Int): VoiceContribution =
                if (shouldRecord) VoiceContribution(durationMs ?: 0L, words) else VoiceContribution(0L, 0)
        }
    }

    private fun acceptedCard(current: CardSessionState, voice: VoiceContribution): CardSessionState =
        current.copy(
            correctCount = current.correctCount + 1,
            lastResult = null,
            incorrectAttemptsForCard = 0,
            answerText = null,
            voiceActiveMs = current.voiceActiveMs + voice.activeMs,
            voiceWordCount = current.voiceWordCount + voice.words,
            voicePromptStartMs = null
        )

    fun correctMid(current: CardSessionState, voice: VoiceContribution): CardSessionState =
        acceptedCard(current, voice).copy(sessionState = SessionState.ACTIVE)

    fun correctLast(current: CardSessionState, voice: VoiceContribution): CardSessionState =
        acceptedCard(current, voice).copy(
            currentCard = null,
            sessionState = SessionState.PAUSED,
            subLessonFinishedToken = current.subLessonFinishedToken + 1
        )

    fun advance(
        current: CardSessionState,
        nextIndex: Int,
        nextCard: SessionCard?,
        incorrectAttempts: Int,
        voiceTriggerToken: Int
    ): CardSessionState = current.copy(
        currentIndex = nextIndex,
        currentCard = nextCard,
        lastResult = null,
        answerText = null,
        incorrectAttemptsForCard = incorrectAttempts,
        sessionState = if (nextCard != null) SessionState.ACTIVE else SessionState.PAUSED,
        voiceTriggerToken = voiceTriggerToken,
        voicePromptStartMs = null
    )

    /** Explicit Next on the final card completes without claiming a correct answer. */
    fun completeViaNext(current: CardSessionState): CardSessionState = current.copy(
        currentCard = null,
        lastResult = null,
        incorrectAttemptsForCard = 0,
        answerText = null,
        voicePromptStartMs = null,
        sessionState = SessionState.PAUSED,
        subLessonFinishedToken = current.subLessonFinishedToken + 1
    )

    private fun resetDeck(
        current: CardSessionState,
        cards: List<SessionCard>,
        mode: TrainingScreenMode,
        voiceTriggerToken: Int
    ): CardSessionState {
        val firstCard = cards.firstOrNull()
        return current.copy(
            sessionState = if (firstCard != null) SessionState.ACTIVE else SessionState.PAUSED,
            currentCard = firstCard,
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
            subLessonFinishedToken = 0,
            wordBankWords = emptyList(),
            selectedWords = emptyList(),
            voiceTriggerToken = voiceTriggerToken,
            verbConjugationCards = if (mode == TrainingScreenMode.VERB_DRILL || mode == TrainingScreenMode.DAILY_VERBS) {
                cards.filterIsInstance<VerbDrillCard>()
            } else emptyList()
        )
    }

    fun start(
        state: TrainingUiState,
        cards: List<SessionCard>,
        mode: TrainingScreenMode,
        voiceTriggerToken: Int
    ): TrainingUiState = state.copy(
        cardSession = resetDeck(state.cardSession, cards, mode, voiceTriggerToken).copy(
            subLessonCount = 1,
            activeSubLessonIndex = 0,
            completedSubLessonCount = 0,
            screenMode = mode
        ),
        boss = state.boss.copy(bossActive = false),
        elite = state.elite.copy(eliteActive = false)
    )

    fun replace(
        state: TrainingUiState,
        cards: List<SessionCard>,
        voiceTriggerToken: Int
    ): TrainingUiState = state.copy(
        cardSession = resetDeck(state.cardSession, cards, state.cardSession.screenMode, voiceTriggerToken)
    )

    fun exit(state: TrainingUiState): TrainingUiState = state.copy(
        cardSession = state.cardSession.copy(
            sessionState = SessionState.PAUSED,
            currentCard = null,
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
            subLessonTotal = 0,
            screenMode = TrainingScreenMode.NORMAL
        )
    )
}
