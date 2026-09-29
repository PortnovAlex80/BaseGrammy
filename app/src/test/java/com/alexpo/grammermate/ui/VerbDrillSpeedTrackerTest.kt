package com.alexpo.grammermate.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VerbDrillSpeedTrackerTest {
    @Test
    fun records_one_answer_per_shown_card_and_resets_on_new_batch() {
        var now = 1_000L
        val tracker = VerbDrillSpeedTracker { now }

        tracker.startBatch()
        now += 3_000L
        tracker.recordAnswer()
        tracker.recordAnswer()
        assertEquals(20, tracker.wordsPerMinute)

        tracker.markCardShown()
        now += 1_000L
        tracker.recordAnswer()
        assertEquals(30, tracker.wordsPerMinute)

        tracker.startBatch()
        assertEquals(0, tracker.wordsPerMinute)
        now += 2_000L
        tracker.recordAnswer()
        assertEquals(30, tracker.wordsPerMinute)
    }
}
