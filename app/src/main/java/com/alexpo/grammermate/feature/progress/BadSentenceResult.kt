package com.alexpo.grammermate.feature.progress

/**
 * Result types for [BadSentenceHelper] methods.
 * Replaces [BadSentenceCallbacks] — each method returns a result
 * instead of calling a callback.
 */
sealed class BadSentenceResult {
    object AdvanceDrillCard : BadSentenceResult()
    object SkipToNextCard : BadSentenceResult()
    object None : BadSentenceResult()
}
