package com.alexpo.grammermate.data

interface AuxDrillStore {

    fun loadProgress(): Map<String, AuxDrillComboProgress>

    fun saveProgress(progress: Map<String, AuxDrillComboProgress>)

    fun upsertComboProgress(key: String, progress: AuxDrillComboProgress)

    /** Flush pending writes to disk. */
    fun flush()
}
