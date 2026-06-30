package com.alexpo.grammermate.feature.training

/**
 * Result types for [StoryRunner] methods.
 * Replaces [StoryCallbacks] — each method returns a result
 * instead of calling a callback.
 */
sealed class StoryResult {
    object SaveAndBackup : StoryResult()
    object None : StoryResult()
}
