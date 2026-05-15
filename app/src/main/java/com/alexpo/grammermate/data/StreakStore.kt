package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface StreakStore {

    fun save(data: StreakData)

    fun load(languageId: String): StreakData

    fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean>

    fun getCurrentStreak(languageId: String): StreakData
}

class StreakStoreImpl(private val context: Context) : StreakStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val mutex = ReentrantLock()

    private fun getFile(languageId: String): File {
        return File(baseDir, "streak_$languageId.yaml")
    }

    private fun saveInternal(data: StreakData) {
        baseDir.mkdirs()
        val file = getFile(data.languageId.value)
        val payload = mapOf(
            "languageId" to data.languageId.value,
            "currentStreak" to data.currentStreak,
            "longestStreak" to data.longestStreak,
            "lastCompletionDateMs" to data.lastCompletionDateMs,
            "totalSubLessonsCompleted" to data.totalSubLessonsCompleted
        )
        AtomicFileWriter.writeText(file, yaml.dump(payload))
    }

    private fun loadInternal(languageId: String): StreakData {
        val file = getFile(languageId)
        if (!file.exists() || file.length() == 0L) {
            return StreakData(languageId = LanguageId(languageId))
        }
        val raw = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null } ?: return StreakData(languageId = LanguageId(languageId))
        val data = raw as? Map<*, *> ?: return StreakData(languageId = LanguageId(languageId))

        val currentStreak = (data["currentStreak"] as? Number)?.toInt() ?: 0
        val longestStreak = (data["longestStreak"] as? Number)?.toInt() ?: 0
        val lastCompletionDateMs = (data["lastCompletionDateMs"] as? Number)?.toLong()
        val totalSubLessonsCompleted = (data["totalSubLessonsCompleted"] as? Number)?.toInt() ?: 0

        return StreakData(
            languageId = LanguageId(languageId),
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastCompletionDateMs = lastCompletionDateMs,
            totalSubLessonsCompleted = totalSubLessonsCompleted
        )
    }

    override fun save(data: StreakData) = mutex.withLock {
        saveInternal(data)
    }

    override fun load(languageId: String): StreakData = mutex.withLock {
        loadInternal(languageId)
    }

    /**
     * Обновляет streak после завершения подурока
     * @return обновлённый StreakData и флаг, является ли это новым достижением
     */
    override fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean> = mutex.withLock {
        val current = loadInternal(languageId)
        val now = System.currentTimeMillis()
        val streakStatus = checkAndUpdateStreak(current, now)

        val newCurrentStreak = when {
            streakStatus.isFirstTime -> 1
            streakStatus.isSameDay -> current.currentStreak
            streakStatus.isConsecutive -> current.currentStreak + 1
            else -> 1
        }

        val updated = current.copy(
            currentStreak = newCurrentStreak,
            longestStreak = maxOf(current.longestStreak, newCurrentStreak),
            lastCompletionDateMs = now,
            totalSubLessonsCompleted = current.totalSubLessonsCompleted + 1
        )

        saveInternal(updated)
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
    override fun getCurrentStreak(languageId: String): StreakData = mutex.withLock {
        val current = loadInternal(languageId)
        val lastCompletionMs = current.lastCompletionDateMs ?: return current

        val now = System.currentTimeMillis()

        val daysSinceLastCompletion = TimeUnit.MILLISECONDS.toDays(now - lastCompletionMs)

        if (daysSinceLastCompletion > 1) {
            val reset = current.copy(currentStreak = 0)
            saveInternal(reset)
            return reset
        }

        return current
    }
}
