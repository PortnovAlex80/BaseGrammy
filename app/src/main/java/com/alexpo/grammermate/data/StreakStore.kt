package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface StreakStore {

    fun save(data: StreakData)

    fun load(languageId: String): StreakData

    fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean>

    fun recordPracticeTypeCompletion(languageId: String, type: PracticeType): Pair<StreakData, Boolean>

    fun getCurrentStreak(languageId: String): StreakData

    fun resetAll()

    fun resetForLanguage(languageId: String)
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
        val payload = mutableMapOf<String, Any?>(
            "languageId" to data.languageId.value,
            "currentStreak" to data.currentStreak,
            "longestStreak" to data.longestStreak,
            "lastCompletionDateMs" to data.lastCompletionDateMs,
            "totalSubLessonsCompleted" to data.totalSubLessonsCompleted
        )
        if (data.completedTypesToday.isNotEmpty()) {
            payload["completedTypesToday"] = data.completedTypesToday.map { it.name }
        }
        if (data.todayFireCount > 0) {
            payload["todayFireCount"] = data.todayFireCount
        }
        if (data.lastFireDateMs != null) {
            payload["lastFireDateMs"] = data.lastFireDateMs
        }
        try {
            AtomicFileWriter.writeText(file, yaml.dump(payload))
            Log.i("StreakStore", "Successfully saved streak data for ${data.languageId.value}: ${file.name} (${file.length()} bytes)")
        } catch (e: IOException) {
            Log.e("StreakStore", "Failed to save streak data for ${data.languageId.value}: ${file.name}", e)
            throw e
        } catch (e: Exception) {
            Log.e("StreakStore", "Unexpected error saving streak data for ${data.languageId.value}: ${file.name}", e)
            throw IOException("Failed to save streak data", e)
        }
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

        // Fire streak fields (migration-safe: defaults for old files)
        @Suppress("UNCHECKED_CAST")
        val completedTypesToday = (data["completedTypesToday"] as? List<String>)
            ?.mapNotNull { name -> PracticeType.entries.find { it.name == name } }
            ?.toSet()
            ?: emptySet()
        val todayFireCount = (data["todayFireCount"] as? Number)?.toInt() ?: 0
        val lastFireDateMs = (data["lastFireDateMs"] as? Number)?.toLong()

        return StreakData(
            languageId = LanguageId(languageId),
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastCompletionDateMs = lastCompletionDateMs,
            totalSubLessonsCompleted = totalSubLessonsCompleted,
            completedTypesToday = completedTypesToday,
            todayFireCount = todayFireCount,
            lastFireDateMs = lastFireDateMs
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

    /**
     * Records completion of a specific practice type for fire streak tracking.
     *
     * Fire streak logic:
     * - Day boundary check: reset completedTypesToday if a new day
     * - Duplicate type: no-op (return unchanged)
     * - New type: add to completedTypesToday, increment todayFireCount, update streak
     *
     * @return updated StreakData and flag indicating whether a new fire was earned.
     */
    override fun recordPracticeTypeCompletion(languageId: String, type: PracticeType): Pair<StreakData, Boolean> = mutex.withLock {
        val current = loadInternal(languageId)
        val now = System.currentTimeMillis()

        // Day boundary: reset fire tracking if last fire was on a different calendar day
        var completedTypesToday = current.completedTypesToday
        var todayFireCount = current.todayFireCount

        val lastFireMs = current.lastFireDateMs
        if (lastFireMs != null) {
            val lastFireCal = Calendar.getInstance().apply { timeInMillis = lastFireMs }
            val todayCal = Calendar.getInstance().apply { timeInMillis = now }
            val isSameDay = lastFireCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    lastFireCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
            if (!isSameDay) {
                // New day: reset fire tracking
                completedTypesToday = emptySet()
                todayFireCount = 0
            }
        }

        // Duplicate type check: if this type was already recorded today, no new fire
        if (type in completedTypesToday) {
            // Still update streak if needed (streak logic uses lastCompletionDateMs)
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
            return Pair(updated, false) // no new fire
        }

        // New type: add fire
        val newCompletedTypes = completedTypesToday + type
        val newFireCount = todayFireCount + 1

        // Update streak based on day boundary
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
            totalSubLessonsCompleted = current.totalSubLessonsCompleted + 1,
            completedTypesToday = newCompletedTypes,
            todayFireCount = newFireCount,
            lastFireDateMs = now
        )

        saveInternal(updated)
        return Pair(updated, true) // new fire earned
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

    override fun resetAll() = mutex.withLock {
        val dir = baseDir
        if (dir.exists()) {
            dir.listFiles()?.filter { it.name.startsWith("streak_") && it.name.endsWith(".yaml") }?.forEach { it.delete() }
        }
    }

    override fun resetForLanguage(languageId: String) = mutex.withLock {
        val file = getFile(languageId)
        if (file.exists()) file.delete()
    }
}
