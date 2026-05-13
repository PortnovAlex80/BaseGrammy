package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.StoryPhase
import com.alexpo.grammermate.data.StoryQuiz
import com.alexpo.grammermate.data.StoryState

/**
 * Callback interface for cross-module orchestration from [StoryRunner].
 */
interface StoryCallbacks {
    fun forceBackup()
    fun saveProgress()
}

/**
 * Story quiz session logic extracted from TrainingViewModel.
 *
 * Manages the story lifecycle: loading a quiz, marking completion
 * per phase (CHECK_IN / CHECK_OUT), and clearing errors.
 * All state changes go through [TrainingStateAccess].
 *
 * Cross-module orchestration uses [StoryCallbacks].
 */
class StoryRunner(
    private val stateAccess: TrainingStateAccess,
    private val callbacks: StoryCallbacks,
    private val lessonStore: LessonStore,
) {

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Load and open a story quiz for the given [phase].
     *
     * Reads the current lesson and language from state, queries
     * [LessonStore.getStoryQuizzes], and activates the first match.
     * Sets an error message if no story is found.
     */
    fun openStory(phase: StoryPhase) {
        val state = stateAccess.uiState.value
        val lessonId = state.navigation.selectedLessonId ?: return
        val languageId = state.navigation.selectedLanguageId
        val story = lessonStore.getStoryQuizzes(lessonId, phase, languageId).firstOrNull()
        if (story == null) {
            stateAccess.updateState {
                it.copy(story = it.story.copy(storyErrorMessage = "Story not found. Please import the pack again."))
            }
            return
        }
        stateAccess.updateState {
            it.copy(story = it.story.copy(activeStory = story, storyErrorMessage = null))
        }
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
    fun completeStory(phase: StoryPhase, allCorrect: Boolean) {
        val state = stateAccess.uiState.value
        val shouldPersist = allCorrect || state.cardSession.testMode
        stateAccess.updateState { current ->
            if (!allCorrect && !current.cardSession.testMode) {
                current.copy(story = current.story.copy(activeStory = null))
            } else {
                when (phase) {
                    StoryPhase.CHECK_IN -> current.copy(story = current.story.copy(storyCheckInDone = true, activeStory = null))
                    StoryPhase.CHECK_OUT -> current.copy(story = current.story.copy(storyCheckOutDone = true, activeStory = null))
                }
            }
        }
        if (shouldPersist) {
            callbacks.forceBackup()
            callbacks.saveProgress()
        }
    }

    /**
     * Clear the story error message from state.
     */
    fun clearStoryError() {
        stateAccess.updateState {
            it.copy(story = it.story.copy(storyErrorMessage = null))
        }
    }
}
