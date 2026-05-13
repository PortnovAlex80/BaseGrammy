package com.alexpo.grammermate.ui.helpers

import android.util.Log
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.VocabEntry
import com.alexpo.grammermate.data.VocabProgressStore
import com.alexpo.grammermate.ui.TrainingUiState

/**
 * Vocab sprint session logic extracted from TrainingViewModel.
 *
 * Manages the vocab sprint lifecycle: session setup, answer validation,
 * word bank generation, SRS progress recording, and session completion.
 * All state changes go through [TrainingStateAccess].
 *
 * Dependencies:
 * - [LessonStore] for loading vocab entries
 * - [VocabProgressStore] for SRS state and sprint progress
 * - [AnswerValidator] for answer checking
 * - [AudioCoordinator] (via callbacks) for sound effects
 *
 * The [vocabSession] field is held externally (in TrainingViewModel) because
 * it is also cleared by navigation methods outside this helper's scope.
 */
class VocabSprintRunner(
    private val stateAccess: TrainingStateAccess,
    private val lessonStore: LessonStore,
    private val vocabProgressStore: VocabProgressStore,
    private val answerValidator: AnswerValidator
) {
    private val logTag = "VocabSprintRunner"

    /** Current vocab session entries. Set by [openSprint], read by [moveToNextVocab] and [updateWordBank]. */
    var vocabSession: List<VocabEntry> = emptyList()

    // ── Callbacks for cross-module orchestration ─────────────────────────

    /** Play success sound (delegates to AudioCoordinator). */
    var onPlaySuccess: (() -> Unit)? = null

    /** Play error sound (delegates to AudioCoordinator). */
    var onPlayError: (() -> Unit)? = null

    /** Save progress to persistent storage (ViewModel orchestration). */
    var onSaveProgress: (() -> Unit)? = null

    /** Signal that backup should be forced on next save. */
    var onForceBackup: (() -> Unit)? = null

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Open a vocab sprint session for the current lesson.
     *
     * Sorts entries by SRS priority (overdue first, then new, then not due),
     * optionally limits to [vocabSprintLimit], and supports resuming a
     * partially-completed sprint.
     */
    fun openSprint(resume: Boolean = false) {
        val state = stateAccess.uiState.value
        val lessonId = state.navigation.selectedLessonId ?: return
        val languageId = state.navigation.selectedLanguageId
        val allEntries = lessonStore.getVocabEntries(lessonId, languageId)

        // Sort using SRS prioritization: overdue first, then new, then not due
        val sorted = vocabProgressStore.sortEntriesForSprint(allEntries, lessonId, languageId)

        val limit = state.cardSession.vocabSprintLimit
        val limited = if (limit <= 0 || limit >= sorted.size) sorted else sorted.take(limit)

        if (limited.isEmpty()) {
            vocabSession = emptyList()
            stateAccess.updateState {
                it.copy(vocabSprint = it.vocabSprint.copy(vocabErrorMessage = "Vocabulary not found. Please import the pack again."))
            }
            return
        }

        val startIndex: Int
        val sessionEntries: List<VocabEntry>

        if (resume) {
            val progress = vocabProgressStore.get(lessonId, languageId)
            // Filter out already-completed entries
            val remaining = limited.filterIndexed { index, _ -> index !in progress.completedIndices }
            if (remaining.isEmpty()) {
                // All completed - start fresh
                vocabProgressStore.clearSprintProgress(lessonId, languageId)
                sessionEntries = limited
                startIndex = 0
            } else {
                sessionEntries = remaining
                startIndex = 0
            }
        } else {
            vocabProgressStore.clearSprintProgress(lessonId, languageId)
            sessionEntries = limited
            startIndex = 0
        }

        vocabSession = sessionEntries
        val firstEntry = sessionEntries.firstOrNull()
        val vocabWordBank = firstEntry?.let { buildWordBank(it, sessionEntries, state) }.orEmpty()
        Log.d(logTag, "openSprint: allEntries=${allEntries.size}, limited=${limited.size}, session=${sessionEntries.size}, resume=$resume, wordBank=${vocabWordBank.size}")
        stateAccess.updateState {
            it.copy(
                vocabSprint = it.vocabSprint.copy(
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
                ),
                boss = it.boss.copy(
                    bossActive = false,
                    bossType = null,
                    bossTotal = 0,
                    bossProgress = 0,
                    bossReward = null,
                    bossRewardMessage = null,
                    bossFinishedToken = 0,
                    bossErrorMessage = null
                )
            )
        }
    }

    /** Clear the vocab sprint error message. */
    fun clearError() {
        stateAccess.updateState {
            it.copy(vocabSprint = it.vocabSprint.copy(vocabErrorMessage = null))
        }
    }

    /**
     * Handle text input changes in the vocab sprint.
     * Resets attempts and answer text if the user starts typing after seeing the answer.
     */
    fun onInputChanged(text: String) {
        stateAccess.updateState {
            val resetAttempts = it.vocabSprint.vocabAnswerText != null || it.vocabSprint.vocabAttempts >= 3
            it.copy(
                vocabSprint = it.vocabSprint.copy(
                    vocabInputText = text,
                    vocabAttempts = if (resetAttempts) 0 else it.vocabSprint.vocabAttempts,
                    vocabAnswerText = if (resetAttempts) null else it.vocabSprint.vocabAnswerText
                )
            )
        }
    }

    /**
     * Switch the vocab sprint input mode.
     * When switching to WORD_BANK, regenerates the word bank options.
     */
    fun setInputMode(mode: InputMode) {
        Log.d(logTag, "setInputMode: $mode")
        stateAccess.updateState {
            it.copy(vocabSprint = it.vocabSprint.copy(vocabInputMode = mode))
        }
        if (mode == InputMode.WORD_BANK) {
            updateWordBank()
            Log.d(logTag, "Word bank updated. Words: ${stateAccess.uiState.value.vocabSprint.vocabWordBankWords.size}")
        }
    }

    /** Trigger voice recognition for the current vocab entry. */
    fun requestVoice() {
        stateAccess.updateState {
            it.copy(
                vocabSprint = it.vocabSprint.copy(
                    vocabInputMode = InputMode.VOICE,
                    vocabVoiceTriggerToken = it.vocabSprint.vocabVoiceTriggerToken + 1
                )
            )
        }
    }

    /**
     * Submit an answer for the current vocab entry.
     *
     * Validates the answer, records SRS progress, plays audio feedback,
     * and advances to the next entry on success. After 3 failed attempts
     * the correct answer is revealed.
     */
    fun submitAnswer(inputOverride: String? = null) {
        val state = stateAccess.uiState.value
        val entry = state.vocabSprint.currentVocab ?: return
        val input = inputOverride ?: state.vocabSprint.vocabInputText
        if (input.isBlank() && !state.cardSession.testMode) return
        val accepted = answerValidator.validate(input, listOf(entry.targetText), state.cardSession.testMode).isCorrect
        if (accepted) {
            onPlaySuccess?.invoke()
            // Save progress: record correct answer and completed index
            val lessonId = state.navigation.selectedLessonId ?: return
            vocabProgressStore.recordCorrect(entry.id, lessonId, state.navigation.selectedLanguageId)
            vocabProgressStore.addCompletedIndex(lessonId, state.navigation.selectedLanguageId, state.vocabSprint.vocabIndex)
            moveToNextVocab()
            return
        }
        onPlayError?.invoke()
        // Record incorrect answer for SRS tracking
        val lessonId = state.navigation.selectedLessonId ?: return
        vocabProgressStore.recordIncorrect(entry.id, lessonId, state.navigation.selectedLanguageId)
        val nextAttempts = state.vocabSprint.vocabAttempts + 1
        if (nextAttempts >= 3) {
            stateAccess.updateState {
                it.copy(
                    vocabSprint = it.vocabSprint.copy(
                        vocabAttempts = nextAttempts,
                        vocabAnswerText = entry.targetText,
                        vocabInputText = ""
                    )
                )
            }
        } else {
            stateAccess.updateState {
                val nextToken = if (state.vocabSprint.vocabInputMode == InputMode.VOICE) {
                    it.vocabSprint.vocabVoiceTriggerToken + 1
                } else {
                    it.vocabSprint.vocabVoiceTriggerToken
                }
                it.copy(
                    vocabSprint = it.vocabSprint.copy(
                        vocabAttempts = nextAttempts,
                        vocabInputText = "",
                        vocabVoiceTriggerToken = nextToken
                    )
                )
            }
        }
    }

    /** Reveal the correct answer for the current vocab entry. */
    fun showAnswer() {
        val entry = stateAccess.uiState.value.vocabSprint.currentVocab ?: return
        stateAccess.updateState {
            it.copy(
                vocabSprint = it.vocabSprint.copy(
                    vocabAnswerText = entry.targetText,
                    vocabInputText = "",
                    vocabAttempts = 3
                )
            )
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────

    /**
     * Advance to the next vocab entry, or finish the sprint if all entries completed.
     */
    private fun moveToNextVocab() {
        val state = stateAccess.uiState.value
        val nextIndex = state.vocabSprint.vocabIndex + 1
        if (nextIndex >= vocabSession.size) {
            // All vocab entries completed - clear sprint progress
            val lessonId = state.navigation.selectedLessonId
            if (lessonId != null) {
                vocabProgressStore.clearSprintProgress(lessonId, state.navigation.selectedLanguageId)
            }
            stateAccess.updateState {
                it.copy(
                    vocabSprint = it.vocabSprint.copy(
                        currentVocab = null,
                        vocabInputText = "",
                        vocabAttempts = 0,
                        vocabAnswerText = null,
                        vocabIndex = nextIndex,
                        vocabTotal = vocabSession.size,
                        vocabWordBankWords = emptyList(),
                        vocabFinishedToken = it.vocabSprint.vocabFinishedToken + 1
                    )
                )
            }
            onForceBackup?.invoke()
            onSaveProgress?.invoke()
            return
        }
        val next = vocabSession[nextIndex]
        val vocabWordBank = buildWordBank(next, vocabSession, state)
        stateAccess.updateState {
            it.copy(
                vocabSprint = it.vocabSprint.copy(
                    currentVocab = next,
                    vocabInputText = "",
                    vocabAttempts = 0,
                    vocabAnswerText = null,
                    vocabIndex = nextIndex,
                    vocabTotal = vocabSession.size,
                    vocabWordBankWords = vocabWordBank
                )
            )
        }
    }

    /**
     * Update the word bank for the current vocab entry.
     * Called when switching to WORD_BANK input mode.
     */
    private fun updateWordBank() {
        val entry = stateAccess.uiState.value.vocabSprint.currentVocab ?: return
        val options = buildWordBank(entry, vocabSession, stateAccess.uiState.value)
        stateAccess.updateState {
            it.copy(vocabSprint = it.vocabSprint.copy(vocabWordBankWords = options))
        }
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
                    lessonStore.getVocabEntries(lesson.id, languageId)
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
}
