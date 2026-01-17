package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

class StreakStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")

    private fun getFile(languageId: String): File {
        return File(baseDir, "streak_$languageId.yaml")
    }

    /**
     * Сохраняет данные о streak
     */
    fun save(data: StreakData) {
        baseDir.mkdirs()
        val file = getFile(data.languageId)
        val payload = mapOf(
            "languageId" to data.languageId,
            "currentStreak" to data.currentStreak,
            "longestStreak" to data.longestStreak,
            "lastCompletionDateMs" to data.lastCompletionDateMs,
            "totalSubLessonsCompleted" to data.totalSubLessonsCompleted
        )
        AtomicFileWriter.writeText(file, yaml.dump(payload))
    }

    /**
     * Загружает данные о streak для языка
     */
    fun load(languageId: String): StreakData {
        val file = getFile(languageId)
        if (!file.exists()) {
            return StreakData(languageId = languageId)
        }
        val raw = yaml.load<Any>(file.readText()) ?: return StreakData(languageId = languageId)
        val data = raw as? Map<*, *> ?: return StreakData(languageId = languageId)

        val currentStreak = (data["currentStreak"] as? Number)?.toInt() ?: 0
        val longestStreak = (data["longestStreak"] as? Number)?.toInt() ?: 0
        val lastCompletionDateMs = (data["lastCompletionDateMs"] as? Number)?.toLong()
        val totalSubLessonsCompleted = (data["totalSubLessonsCompleted"] as? Number)?.toInt() ?: 0

        return StreakData(
            languageId = languageId,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastCompletionDateMs = lastCompletionDateMs,
            totalSubLessonsCompleted = totalSubLessonsCompleted
        )
    }

    /**
     * Обновляет streak после завершения подурока
     * @return обновлённый StreakData и флаг, является ли это новым достижением
     */
    fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean> {
        val current = load(languageId)
        val now = System.currentTimeMillis()
        val streakStatus = checkAndUpdateStreak(current, now)

        val newCurrentStreak = when {
            streakStatus.isFirstTime -> 1
            streakStatus.isSameDay -> current.currentStreak
            streakStatus.isConsecutive -> current.currentStreak + 1
            else -> 1 // Пропущен день - сброс на 1
        }

        val updated = current.copy(
            currentStreak = newCurrentStreak,
            longestStreak = maxOf(current.longestStreak, newCurrentStreak),
            lastCompletionDateMs = now,
            totalSubLessonsCompleted = current.totalSubLessonsCompleted + 1
        )

        save(updated)
        return Pair(updated, streakStatus.isNewStreak)
    }

    private data class StreakStatus(
        val isFirstTime: Boolean = false,
        val isSameDay: Boolean = false,
        val isConsecutive: Boolean = false,
        val isNewStreak: Boolean = false
    )

    /**
     * Проверяет, нужно ли обновить streak
     * @return информация о статусе streak
     */
    private fun checkAndUpdateStreak(current: StreakData, nowMs: Long): StreakStatus {
        val lastCompletionMs = current.lastCompletionDateMs
        if (lastCompletionMs == null) {
            // Первый раз - начинаем streak
            return StreakStatus(isFirstTime = true, isNewStreak = true)
        }

        val lastDate = Calendar.getInstance().apply { timeInMillis = lastCompletionMs }
        val today = Calendar.getInstance().apply { timeInMillis = nowMs }

        // Сравниваем даты (год, месяц, день)
        val isSameDay = lastDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        lastDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) {
            // Уже занимались сегодня - не увеличиваем streak
            return StreakStatus(isSameDay = true, isNewStreak = false)
        }

        // Проверяем, был ли вчера
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val wasYesterday = lastDate.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                          lastDate.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)

        if (wasYesterday) {
            // Был вчера - продолжаем streak
            return StreakStatus(isConsecutive = true, isNewStreak = true)
        }

        // Пропустили день(дни) - сбрасываем streak на 1
        return StreakStatus(isNewStreak = true)
    }

    /**
     * Получает текущий streak с учётом пропущенных дней
     */
    fun getCurrentStreak(languageId: String): StreakData {
        val current = load(languageId)
        val lastCompletionMs = current.lastCompletionDateMs ?: return current

        val now = System.currentTimeMillis()

        // Проверяем, не пропущен ли день
        val daysSinceLastCompletion = TimeUnit.MILLISECONDS.toDays(now - lastCompletionMs)

        if (daysSinceLastCompletion > 1) {
            // Пропустили больше 1 дня - сбрасываем streak
            val reset = current.copy(currentStreak = 0)
            save(reset)
            return reset
        }

        return current
    }
}
