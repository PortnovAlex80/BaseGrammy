package com.alexpo.grammermate.ui

/** Tracks answer timing for the current verb-drill batch. */
internal class VerbDrillSpeedTracker(private val nowMs: () -> Long = System::currentTimeMillis) {
    private var cardShownTimestamp = 0L
    private var totalAnswerTimeMs = 0L
    private var totalAnswers = 0

    val wordsPerMinute: Int
        get() {
            val minutes = totalAnswerTimeMs / 60000.0
            return if (minutes <= 0.0) 0 else (totalAnswers / minutes).toInt()
        }

    fun markCardShown() {
        cardShownTimestamp = nowMs()
    }

    fun recordAnswer() {
        if (cardShownTimestamp > 0) {
            totalAnswerTimeMs += nowMs() - cardShownTimestamp
            totalAnswers++
            cardShownTimestamp = 0L
        }
    }

    fun startBatch() {
        totalAnswerTimeMs = 0L
        totalAnswers = 0
        markCardShown()
    }
}
