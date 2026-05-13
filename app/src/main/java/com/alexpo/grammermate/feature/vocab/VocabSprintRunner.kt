package com.alexpo.grammermate.feature.vocab

import android.util.Log
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.VocabEntry
import com.alexpo.grammermate.data.VocabProgressStore
import com.alexpo.grammermate.data.VocabSprintState
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import com.alexpo.grammermate.feature.training.AnswerValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Result of submitting a vocab sprint answer.
 * Combines sound feedback and persistence action.
 */
data class VocabSubmitResult(
    val sound: VocabSoundResult,
    val action: VocabResult
)

/**
 * Vocab sprint session logic extracted from TrainingViewModel.
 *
 * Manages the vocab sprint lifecycle: session setup, answer validation,
 * word bank generation, SRS progress recording, and session completion.
 * All state changes go through [TrainingStateAccess].
 *
 * Returns typed results instead of calling callbacks.
 */
class VocabSprintRunner(
    private val stateAccess: TrainingStateAccess,
    private val lessonStore: LessonStore,
    private val vocabProgressStore: VocabProgressStore,
    private val answerValidator: AnswerValidator
) {
    private val logTag = "VocabSprintRunner"

    // ── Owned state flow ─────────────────────────────────────────────────
    private val _state = MutableStateFlow(VocabSprintState())
    val vocabState: StateFlow<VocabSprintState> = _state

    /** Current vocab session entries. Set by [openSprint], read by [moveToNextVocab] and [updateWordBank]. */
    var vocabSession: List<VocabEntry> = emptyList()

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Open a vocab sprint session for the current lesson.
     *
     * Sorts entries by SRS priority (overdue first, then new, then not due),
     * optionally limits to [vocabSprintLimit], and supports resuming a
     * partially-completed sprint.
     */
    fun openSprint(resume: Boolean = false): VocabResult {
        val state = stateAccess.uiState.value
        val lessonId = state.navigation.selectedLessonId ?: return VocabResult.None
        val languageId = state.navigation.selectedLanguageId
        val allEntries = lessonStore.getVocabEntries(lessonId.value, languageId.value)

        // Sort using SRS prioritization: overdue first, then new, then not due
        val sorted = vocabProgressStore.sortEntriesForSprint(allEntries, lessonId.value, languageId.value)

        val limit = state.cardSession.vocabSprintLimit
        val limited = if (limit <= 0 || limit >= sorted.size) sorted else sorted.take(limit)

        if (limited.isEmpty()) {
            vocabSession = emptyList()
            _state.update { it.copy(vocabErrorMessage = "Vocabulary not found. Please import the pack again.") }
            return VocabResult.None
        }

        val startIndex: Int
        val sessionEntries: List<VocabEntry>

        if (resume) {
            val progress = vocabProgressStore.get(lessonId.value, languageId.value)
            // Filter out already-completed entries
            val remaining = limited.filterIndexed { index, _ -> index !in progress.completedIndices }
            if (remaining.isEmpty()) {
                // All completed - start fresh
                vocabProgressStore.clearSprintProgress(lessonId.value, languageId.value)
                sessionEntries = limited
                startIndex = 0
            } else {
                sessionEntries = remaining
                startIndex = 0
            }
        } else {
            vocabProgressStore.clearSprintProgress(lessonId.value, languageId.value)
            sessionEntries = limited
            startIndex = 0
        }

        vocabSession = sessionEntries
        val firstEntry = sessionEntries.firstOrNull()
        val vocabWordBank = firstEntry?.let { buildWordBank(it, sessionEntries, state) }.orEmpty()
        Log.d(logTag, "openSprint: allEntries=${allEntries.size}, limited=${limited.size}, session=${sessionEntries.size}, resume=$resume, wordBank=${vocabWordBank.size}")
        _state.update {
            it.copy(
                currentVocab = firstEntry,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = startIndex,
                vocabTotal = sessionEntries.size,
                vocabWordBankWords = vocabWordBank,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0
            )
        }
        return VocabResult.ResetBoss
    }

    /** Clear the vocab sprint error message. */
    fun clearError() {
        _state.update { it.copy(vocabErrorMessage = null) }
    }

    /**
     * Handle text input changes in the vocab sprint.
     * Resets attempts and answer text if the user starts typing after seeing the answer.
     */
    fun onInputChanged(text: String) {
        _state.update {
            val resetAttempts = it.vocabAnswerText != null || it.vocabAttempts >= 3
            it.copy(
                vocabInputText = text,
                vocabAttempts = if (resetAttempts) 0 else it.vocabAttempts,
                vocabAnswerText = if (resetAttempts) null else it.vocabAnswerText
            )
        }
    }

    /**
     * Switch the vocab sprint input mode.
     * When switching to WORD_BANK, regenerates the word bank options.
     */
    fun setInputMode(mode: InputMode) {
        Log.d(logTag, "setInputMode: $mode")
        _state.update { it.copy(vocabInputMode = mode) }
        if (mode == InputMode.WORD_BANK) {
            updateWordBank()
            Log.d(logTag, "Word bank updated. Words: ${_state.value.vocabWordBankWords.size}")
        }
    }

    /** Trigger voice recognition for the current vocab entry. */
    fun requestVoice() {
        _state.update {
            it.copy(
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = it.vocabVoiceTriggerToken + 1
            )
        }
    }

    /**
     * Submit an answer for the current vocab entry.
     *
     * Validates the answer, records SRS progress, signals sound feedback,
     * and advances to the next entry on success. After 3 failed attempts
     * the correct answer is revealed.
     */
    fun submitAnswer(inputOverride: String? = null): VocabSubmitResult {
        val state = stateAccess.uiState.value
        val vocabState = _state.value
        val entry = vocabState.currentVocab ?: return VocabSubmitResult(VocabSoundResult.None, VocabResult.None)
        val input = inputOverride ?: vocabState.vocabInputText
        if (input.isBlank() && !state.cardSession.testMode) return VocabSubmitResult(VocabSoundResult.None, VocabResult.None)
        val accepted = answerValidator.validate(input, listOf(entry.targetText), state.cardSession.testMode).isCorrect
        if (accepted) {
            // Save progress: record correct answer and completed index
            val lessonId = state.navigation.selectedLessonId ?: return VocabSubmitResult(VocabSoundResult.PlaySuccess, VocabResult.None)
            vocabProgressStore.recordCorrect(entry.id, lessonId.value, state.navigation.selectedLanguageId.value)
            vocabProgressStore.addCompletedIndex(lessonId.value, state.navigation.selectedLanguageId.value, vocabState.vocabIndex)
            val nextResult = moveToNextVocab()
            return VocabSubmitResult(VocabSoundResult.PlaySuccess, nextResult)
        }
        // Record incorrect answer for SRS tracking
        val lessonId = state.navigation.selectedLessonId ?: return VocabSubmitResult(VocabSoundResult.PlayError, VocabResult.None)
        vocabProgressStore.recordIncorrect(entry.id, lessonId.value, state.navigation.selectedLanguageId.value)
        val nextAttempts = vocabState.vocabAttempts + 1
        if (nextAttempts >= 3) {
            _state.update {
                it.copy(
                    vocabAttempts = nextAttempts,
                    vocabAnswerText = entry.targetText,
                    vocabInputText = ""
                )
            }
        } else {
            _state.update {
                val nextToken = if (vocabState.vocabInputMode == InputMode.VOICE) {
                    it.vocabVoiceTriggerToken + 1
                } else {
                    it.vocabVoiceTriggerToken
                }
                it.copy(
                    vocabAttempts = nextAttempts,
                    vocabInputText = "",
                    vocabVoiceTriggerToken = nextToken
                )
            }
        }
        return VocabSubmitResult(VocabSoundResult.PlayError, VocabResult.None)
    }

    /** Reveal the correct answer for the current vocab entry. */
    fun showAnswer() {
        val entry = _state.value.currentVocab ?: return
        _state.update {
            it.copy(
                vocabAnswerText = entry.targetText,
                vocabInputText = "",
                vocabAttempts = 3
            )
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────

    /**
     * Advance to the next vocab entry, or finish the sprint if all entries completed.
     */
    private fun moveToNextVocab(): VocabResult {
        val state = stateAccess.uiState.value
        val vocabState = _state.value
        val nextIndex = vocabState.vocabIndex + 1
        if (nextIndex >= vocabSession.size) {
            // All vocab entries completed - clear sprint progress
            val lessonId = state.navigation.selectedLessonId
            if (lessonId != null) {
                vocabProgressStore.clearSprintProgress(lessonId.value, state.navigation.selectedLanguageId.value)
            }
            _state.update {
                it.copy(
                    currentVocab = null,
                    vocabInputText = "",
                    vocabAttempts = 0,
                    vocabAnswerText = null,
                    vocabIndex = nextIndex,
                    vocabTotal = vocabSession.size,
                    vocabWordBankWords = emptyList(),
                    vocabFinishedToken = it.vocabFinishedToken + 1
                )
            }
            return VocabResult.SaveAndBackup
        }
        val next = vocabSession[nextIndex]
        val vocabWordBank = buildWordBank(next, vocabSession, state)
        _state.update {
            it.copy(
                currentVocab = next,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = nextIndex,
                vocabTotal = vocabSession.size,
                vocabWordBankWords = vocabWordBank
            )
        }
        return VocabResult.None
    }

    /**
     * Update the word bank for the current vocab entry.
     * Called when switching to WORD_BANK input mode.
     */
    private fun updateWordBank() {
        val entry = _state.value.currentVocab ?: return
        val options = buildWordBank(entry, vocabSession, stateAccess.uiState.value)
        _state.update { it.copy(vocabWordBankWords = options) }
    }

    /**
     * Build a word bank for a vocab entry.
     *
     * Takes the correct answer as the first option, then selects up to 4 distractors
     * from the session pool. If not enough distractors are available in the pool,
     * falls back to pulling from all lessons' vocab entries.
     */
    private fun buildWordBank(entry: VocabEntry, pool: List<VocabEntry>, state: TrainingUiState): List<String> {
        val correctOption = entry.targetText.split("+")
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: entry.targetText
        val normalizedCorrect = Normalizer.normalize(correctOption)

        // Collect all options from the vocab pool
        val poolOptions = pool
            .flatMap { it.targetText.split("+") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Remove the correct answer from the distractor pool
        val poolDistractors = poolOptions
            .filter { Normalizer.normalize(it) != normalizedCorrect }
            .shuffled()

        // Start with distractors from the vocab pool
        val distractors = poolDistractors.take(4).toMutableList()

        // If not enough distractors, supplement from all lessons
        if (distractors.size < 4) {
            val languageId = state.navigation.selectedLanguageId
            val allVocabFromLessons = state.navigation.lessons
                .flatMap { lesson ->
                    lessonStore.getVocabEntries(lesson.id.value, languageId.value)
                }
                .flatMap { it.targetText.split("+") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .filter { Normalizer.normalize(it) != normalizedCorrect }
                .filter { !distractors.contains(it) }
                .shuffled()

            val additionalNeeded = 4 - distractors.size
            distractors.addAll(allVocabFromLessons.take(additionalNeeded))
        }

        // Up to 4 distractors + correct answer = up to 5 options
        val selectedDistractors = distractors.take(4)
        val result = (listOf(correctOption) + selectedDistractors).shuffled()

        Log.d(logTag, "buildWordBank: entry=${entry.nativeText}, correct=$correctOption, pool=${pool.size}, poolDistractors=${poolDistractors.size}, finalDistractors=${selectedDistractors.size}, result=${result.size}, words=$result")
        return result
    }

    // ── State management helpers ────────────────────────────────────────

    /**
     * Update the mastered count from the store.
     * Called when returning from VocabDrill to reflect updated mastery.
     */
    fun updateMasteredCount(count: Int) {
        _state.update { it.copy(vocabMasteredCount = count) }
    }

    /**
     * Reset vocab sprint state to defaults.
     * Called during session resets.
     */
    fun resetState() {
        _state.update { VocabSprintState() }
    }
}
