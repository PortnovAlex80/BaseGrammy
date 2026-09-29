package com.alexpo.grammermate.feature.training

import android.os.SystemClock
import android.util.Log
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.TrainingStateAccess
import kotlinx.coroutines.flow.MutableStateFlow

/** Owns input-mode and text commands; the retry machine and typed-text flow remain shared. */
internal class SessionInputController(
    private val stateAccess: TrainingStateAccess,
    private val stateMachine: CardSessionStateMachine,
    private val inputTextFlow: MutableStateFlow<String>,
    private val wordBank: SessionWordBank,
    private val currentCard: () -> SessionCard?
) {
    private val audit get() = AuditLogger.getInstanceOrNull()

    fun clearTypedInput() {
        inputTextFlow.value = ""
    }

    fun onInputChanged(text: String) {
        stateMachine.onInputChanged(text)
        inputTextFlow.value = text
        // Keystrokes stay in the dedicated flow; only changed retry fields update the combined state.
        val attempts = stateMachine.incorrectAttempts
        val hintAnswer = stateMachine.hintAnswer
        stateAccess.updateState {
            val cs = it.cardSession
            if (cs.incorrectAttemptsForCard == attempts && cs.answerText == hintAnswer) it
            else it.copy(cardSession = cs.copy(incorrectAttemptsForCard = attempts, answerText = hintAnswer))
        }
    }

    fun onVoicePromptStarted() {
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(voicePromptStartMs = SystemClock.elapsedRealtime()))
        }
    }

    fun setInputMode(mode: InputMode) {
        val previousMode = stateAccess.uiState.value.cardSession.inputMode
        // Preserve the legacy WORD_BANK guard: no voice launch or audit on this early return.
        if (mode == InputMode.WORD_BANK && stateAccess.uiState.value.cardSession.hintLevel != HintLevel.EASY) {
            stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(inputMode = InputMode.VOICE)) }
            return
        }
        if (stateMachine.hintAnswer != null) {
            stateMachine.onInputChanged("x")
            stateMachine.reset()
        }
        val shouldTriggerVoice = mode == InputMode.VOICE &&
            stateAccess.uiState.value.cardSession.sessionState == SessionState.ACTIVE
        if (shouldTriggerVoice) stateMachine.triggerVoice()
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(
                inputMode = mode,
                incorrectAttemptsForCard = stateMachine.incorrectAttempts,
                answerText = stateMachine.hintAnswer,
                voiceTriggerToken = stateMachine.voiceTriggerToken,
                voicePromptStartMs = if (mode == InputMode.VOICE) it.cardSession.voicePromptStartMs else null
            ))
        }
        if (mode == InputMode.WORD_BANK) wordBank.update()
        Log.d("SessionRunner", "Input mode changed: $mode")
        audit?.inputModeChange(previousMode.name, mode.name, currentCard()?.id ?: "")
    }
}
