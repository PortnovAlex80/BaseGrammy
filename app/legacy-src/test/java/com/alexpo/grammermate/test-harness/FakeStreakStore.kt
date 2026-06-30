package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.PracticeType
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.StreakStore
import java.util.Calendar

/**
 * In-memory fake implementation of StreakStore for testing.
 * No file I/O, no YAML parsing, no Android dependencies.
 */
class FakeStreakStore : StreakStore {

    private val data = mutableMapOf<String, StreakData>()

    override fun save(data: StreakData) {
        val langId = data.languageId.value
        this.data[langId] = data
    }

    override fun load(languageId: String): StreakData {
        return data[languageId] ?: StreakData(languageId = LanguageId(languageId))
    }

    override fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean> {
        val existing = load(languageId)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lastDate = existing.lastCompletionDateMs ?: 0L
        val lastDay = if (lastDate > 0L) {
            Calendar.getInstance().apply {
                timeInMillis = lastDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else 0L

        val isSameDay = lastDay == today
        val isNextDay = lastDay > 0L && (today - lastDay) == (24 * 60 * 60 * 1000L)

        val newStreak = when {
            isSameDay -> existing.currentStreak
            isNextDay -> existing.currentStreak + 1
            lastDay > 0L && today > lastDay -> 1 // Gap of 2+ days, reset to 1
            else -> 1 // First time
        }

        val updated = existing.copy(
            currentStreak = newStreak,
            longestStreak = maxOf(existing.longestStreak, newStreak),
            lastCompletionDateMs = System.currentTimeMillis(),
            totalSubLessonsCompleted = existing.totalSubLessonsCompleted + 1
        )

        save(updated)
        return updated to (!isSameDay && newStreak > existing.currentStreak)
    }

    override fun recordPracticeTypeCompletion(languageId: String, type: PracticeType): Pair<StreakData, Boolean> {
        val existing = load(languageId)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lastFireDate = existing.lastFireDateMs ?: 0
        val lastFireDay = if (lastFireDate > 0) {
            Calendar.getInstance().apply {
                timeInMillis = lastFireDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else 0

        val isNewDay = lastFireDay != today

        val newCompletedTypes = if (isNewDay) {
            setOf(type)
        } else {
            existing.completedTypesToday + type
        }

        val newFireCount = newCompletedTypes.size
        val newFireEarned = isNewDay || type !in existing.completedTypesToday

        val updated = existing.copy(
            completedTypesToday = newCompletedTypes,
            todayFireCount = newFireCount,
            lastFireDateMs = if (newFireEarned) today else existing.lastFireDateMs
        )

        save(updated)
        return updated to newFireEarned
    }

    override fun getCurrentStreak(languageId: String): StreakData {
        return load(languageId)
    }

    override fun resetAll() {
        data.clear()
    }

    override fun resetForLanguage(languageId: String) {
        data.remove(languageId)
    }

    /**
     * Test helper: Set streak data directly.
     */
    fun setStreakData(streakData: StreakData) {
        data[streakData.languageId.value] = streakData
    }
}
