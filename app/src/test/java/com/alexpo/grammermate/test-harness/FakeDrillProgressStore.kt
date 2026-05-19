package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.DrillProgressStore

/**
 * In-memory fake implementation of DrillProgressStore for testing.
 * No file I/O, no YAML parsing, no Android dependencies.
 */
class FakeDrillProgressStore : DrillProgressStore {

    private val data = mutableMapOf<String, Int>()

    override fun getDrillProgress(lessonId: String): Int {
        return data[lessonId] ?: -1
    }

    override fun saveDrillProgress(lessonId: String, cardIndex: Int) {
        data[lessonId] = cardIndex
    }

    override fun hasProgress(lessonId: String): Boolean {
        val progress = data[lessonId] ?: return false
        return progress > 0
    }

    override fun clearDrillProgress(lessonId: String) {
        data.remove(lessonId)
    }

    /**
     * Test helper: Get all progress data.
     */
    fun getAllProgress(): Map<String, Int> = data.toMap()

    /**
     * Test helper: Clear all progress data.
     */
    fun clearAll() {
        data.clear()
    }
}
