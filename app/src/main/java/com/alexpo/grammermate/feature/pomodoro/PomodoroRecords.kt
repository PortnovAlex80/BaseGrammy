package com.alexpo.grammermate.feature.pomodoro

import com.alexpo.grammermate.data.PomodoroHistoryEntry
import com.alexpo.grammermate.data.PomodoroHistoryStore
import com.alexpo.grammermate.data.PomodoroSessionStats
import com.alexpo.grammermate.data.PomodoroSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns Pomodoro settings/history persistence and the values shown on Home. */
internal class PomodoroRecords(
    private val settingsStore: PomodoroSettingsStore,
    private val historyStore: PomodoroHistoryStore
) {
    private val _lastDuration = MutableStateFlow(20)
    val lastDuration: StateFlow<Int> = _lastDuration.asStateFlow()

    private val _history = MutableStateFlow<List<PomodoroHistoryEntry>>(emptyList())
    val history: StateFlow<List<PomodoroHistoryEntry>> = _history.asStateFlow()

    /** Called on the startup IO path; subsequent reads of the flow stay in memory. */
    fun loadDuration() {
        _lastDuration.value = settingsStore.load()
    }

    fun saveDuration(durationMinutes: Int) {
        settingsStore.save(durationMinutes)
        _lastDuration.value = durationMinutes
    }

    fun loadHistory(languageId: String?): List<PomodoroHistoryEntry> =
        languageId?.let(historyStore::loadAll) ?: emptyList()

    fun refreshHistory(languageId: String?) {
        val entries = loadHistory(languageId)
        if (_history.value != entries) _history.value = entries
    }

    fun appendCompleted(
        stats: PomodoroSessionStats,
        remainingSeconds: Int,
        totalSeconds: Int,
        languageId: String?,
        packId: String?,
        lessonId: String?
    ) {
        if (languageId == null) return
        historyStore.append(
            PomodoroHistoryEntry(
                id = "${languageId}_${stats.completedAtMs}",
                languageId = languageId,
                packId = packId,
                lessonId = lessonId,
                completedAtMs = stats.completedAtMs,
                durationMinutes = stats.durationMinutes,
                totalSeconds = totalSeconds,
                remainingSeconds = remainingSeconds,
                cardsShown = stats.cardsShown,
                cardsCorrect = stats.cardsCorrect,
                cardsIncorrect = stats.cardsIncorrect,
                wordsPerMinute = stats.wordsPerMinute
            )
        )
        refreshHistory(languageId)
    }
}
