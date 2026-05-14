package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.StoryPhase
import com.alexpo.grammermate.data.StoryQuiz
import com.alexpo.grammermate.data.StoryState
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Story quiz session logic extracted from TrainingViewModel.
 *
 * Manages the story lifecycle: loading a quiz, marking completion
 * per phase (CHECK_IN / CHECK_OUT), and clearing errors.
 * Owns its [StateFlow] for [StoryState]; reads navigation from [TrainingStateAccess].
 *
 * Returns [StoryResult] instead of calling callbacks.
 */
class StoryRunner(
    private val stateAccess: TrainingStateAccess,
    private val lessonStore: LessonStore,
) {

    // ── Owned state flow ─────────────────────────────────────────────────
    private val _state = MutableStateFlow(StoryState())
    val stateFlow: StateFlow<StoryState> = _state

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Load and open a story quiz for the given [phase].
     *
     * Reads the current lesson and language from state, queries
     * [LessonStore.getStoryQuizzes], and activates the first match.
     * Sets an error message if no story is found.
     */
    fun openStory(phase: StoryPhase): StoryResult {
        val state = stateAccess.uiState.value
        val lessonId = state.navigation.selectedLessonId ?: return StoryResult.None
        val languageId = state.navigation.selectedLanguageId
        val story = lessonStore.getStoryQuizzes(lessonId.value, phase, languageId.value).firstOrNull()
        if (story == null) {
            _state.update { it.copy(storyErrorMessage = "Story not found. Please import the pack again.") }
            return StoryResult.None
        }
        _state.update { it.copy(activeStory = story, storyErrorMessage = null) }
        return StoryResult.None
    }

    /**
     * Complete a story quiz for the given [phase].
     *
     * If [allCorrect] is true (or test mode is active), marks the corresponding
     * phase as done in [StoryState]. Otherwise clears the active story so the
     * user can retry. Persists progress on success.
     *
     * @param phase      which story phase (CHECK_IN or CHECK_OUT)
     * @param allCorrect whether the user answered all questions correctly
     */
    fun completeStory(phase: StoryPhase, allCorrect: Boolean): StoryResult {
        val state = stateAccess.uiState.value
        val shouldPersist = allCorrect || state.cardSession.testMode
        _state.update { current ->
            if (!allCorrect && !state.cardSession.testMode) {
                current.copy(activeStory = null)
            } else {
                when (phase) {
                    StoryPhase.CHECK_IN -> current.copy(storyCheckInDone = true, activeStory = null)
                    StoryPhase.CHECK_OUT -> current.copy(storyCheckOutDone = true, activeStory = null)
                }
            }
        }
        if (shouldPersist) {
            return StoryResult.SaveAndBackup
        }
        return StoryResult.None
    }

    /**
     * Clear the story error message from state.
     */
    fun clearStoryError(): StoryResult {
        _state.update { it.copy(storyErrorMessage = null) }
        return StoryResult.None
    }

    /**
     * Reset story state to defaults.
     * Called during session resets.
     */
    fun resetState() {
        _state.update { StoryState() }
    }
}
