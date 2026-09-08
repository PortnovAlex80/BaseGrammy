package com.alexpo.grammermate.feature.vocab

/**
 * Result types for [VocabSprintRunner] methods.
 * Replaces [VocabSprintCallbacks] — each method returns a result
 * instead of calling a callback.
 */
sealed class VocabResult {
    object SaveAndBackup : VocabResult()
    /** Cross-module signal: boss state should be reset to defaults. */
    object ResetBoss : VocabResult()
    object None : VocabResult()
}

sealed class VocabSoundResult {
    object PlaySuccess : VocabSoundResult()
    object PlayError : VocabSoundResult()
    object None : VocabSoundResult()
}
