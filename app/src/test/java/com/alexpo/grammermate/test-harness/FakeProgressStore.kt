package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.TrainingProgress

/**
 * In-memory fake implementation of ProgressStore for testing.
 * No file I/O, no YAML parsing, no Android dependencies.
 */
class FakeProgressStore(
    initialProgress: TrainingProgress = TrainingProgress()
) : ProgressStore {

    private var _progress = initialProgress

    override fun load(): TrainingProgress = _progress

    override fun save(progress: TrainingProgress) {
        _progress = progress
    }

    override fun clear() {
        _progress = TrainingProgress()
    }

    /**
     * Test helper: Get the current progress without loading.
     */
    fun getCurrentProgress(): TrainingProgress = _progress

    /**
     * Test helper: Set progress directly without save.
     */
    fun setProgress(progress: TrainingProgress) {
        _progress = progress
    }
}
