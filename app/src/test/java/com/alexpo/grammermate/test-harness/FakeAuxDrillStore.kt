package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.AuxDrillComboProgress
import com.alexpo.grammermate.data.AuxDrillStore

/** In-memory fake for tests. No file I/O, no Android deps. */
class FakeAuxDrillStore : AuxDrillStore {

    private val progressData = mutableMapOf<String, AuxDrillComboProgress>()

    override fun loadProgress(): Map<String, AuxDrillComboProgress> = progressData

    override fun saveProgress(progress: Map<String, AuxDrillComboProgress>) {
        progressData.clear()
        progressData.putAll(progress)
    }

    override fun upsertComboProgress(key: String, progress: AuxDrillComboProgress) {
        progressData[key] = progress
    }

    override fun flush() {
        // No-op
    }

    fun clear() {
        progressData.clear()
    }
}
