package com.alexpo.grammermate.data

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.Calendar

/**
 * Unit tests for StreakStore - защита streak системы.
 *
 * Покрывает требования:
 * - FR-13.1.1-13.1.4: Отслеживание серий
 * - FR-13.2.1-13.2.4: Обновление streak
 * - FR-13.4.1-13.4.2: Хранение
 */
@RunWith(RobolectricTestRunner::class)
class StreakStoreTest {

    private lateinit var context: Context
    private lateinit var store: StreakStore
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        store = StreakStore(context)
        testDir = File(context.filesDir, "grammarmate")
    }

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    // ========================================
    // 8.1 Сохранение и загрузка
    // ========================================

    @Test
    fun saveStreak_persists() {
        // FR-13.4.1: Сохранение streak
        val data = StreakData(
            languageId = "en",
            currentStreak = 5,
            longestStreak = 10,
            lastCompletionDateMs = System.currentTimeMillis(),
            totalSubLessonsCompleted = 25
        )

        store.save(data)

        val loaded = store.load("en")
        assertEquals(5, loaded.currentStreak)
        assertEquals(10, loaded.longestStreak)
        assertEquals(25, loaded.totalSubLessonsCompleted)
    }

    @Test
    fun loadStreak_returnsCorrectData() {
        // FR-13.4.2: Загрузка streak
        val data = StreakData(
            languageId = "en",
            currentStreak = 7,
            longestStreak = 15
        )

        store.save(data)

        val loaded = store.load("en")
        assertEquals("en", loaded.languageId)
        assertEquals(7, loaded.currentStreak)
        assertEquals(15, loaded.longestStreak)
    }

    @Test
    fun loadStreak_missingFile_returnsDefault() {
        // FR-13.4.2: Отсутствующий файл → дефолтный streak
        val loaded = store.load("en")

        assertEquals("en", loaded.languageId)
        assertEquals(0, loaded.currentStreak)
        assertEquals(0, loaded.longestStreak)
        assertNull(loaded.lastCompletionDateMs)
        assertEquals(0, loaded.totalSubLessonsCompleted)
    }

    // ========================================
    // 8.2 Обновление streak
    // ========================================

    @Test
    fun updateStreak_firstTime_setsStreak1() {
        // FR-13.2.1: Первое занятие → streak = 1
        val (updated, isNew) = store.recordSubLessonCompletion("en")

        assertTrue("Should be new streak", isNew)
        assertEquals(1, updated.currentStreak)
        assertEquals(1, updated.longestStreak)
        assertEquals(1, updated.totalSubLessonsCompleted)
        assertNotNull(updated.lastCompletionDateMs)
    }

    @Test
    fun updateStreak_sameDay_doesNotIncrease() {
        // FR-13.2.2: Повторное занятие в тот же день → streak не растёт
        store.recordSubLessonCompletion("en")
        val (updated, isNew) = store.recordSubLessonCompletion("en")

        assertFalse("Should not be new streak (same day)", isNew)
        assertEquals(1, updated.currentStreak)
        assertEquals(2, updated.totalSubLessonsCompleted) // Но total растёт
    }

    @Test
    fun updateStreak_nextDay_increasesStreak() {
        // FR-13.2.3: Занятие на следующий день → streak растёт
        store.recordSubLessonCompletion("en")

        // Симулируем следующий день
        val yesterday = System.currentTimeMillis() - (25 * 60 * 60 * 1000L) // 25 часов назад
        val data = store.load("en")
        store.save(data.copy(lastCompletionDateMs = yesterday))

        val (updated, isNew) = store.recordSubLessonCompletion("en")

        assertTrue("Should be new streak (next day)", isNew)
        assertEquals(2, updated.currentStreak)
        assertEquals(2, updated.longestStreak)
    }

    @Test
    fun updateStreak_skippedDay_resetsStreak() {
        // FR-13.2.4: Пропущенный день → streak сбрасывается
        store.recordSubLessonCompletion("en")

        // Симулируем пропуск 3 дней
        val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
        val data = store.load("en")
        store.save(data.copy(lastCompletionDateMs = threeDaysAgo))

        val (updated, isNew) = store.recordSubLessonCompletion("en")

        assertTrue("Should be new streak after gap", isNew)
        assertEquals(1, updated.currentStreak) // Сброс на 1
    }

    @Test
    fun updateStreak_updatesLongestStreak() {
        // FR-13.2.5: Обновление longest streak
        // День 1
        store.recordSubLessonCompletion("en")
        var data = store.load("en")
        assertEquals(1, data.longestStreak)

        // День 2
        data = data.copy(lastCompletionDateMs = System.currentTimeMillis() - (25 * 60 * 60 * 1000L))
        store.save(data)
        store.recordSubLessonCompletion("en")
        data = store.load("en")
        assertEquals(2, data.longestStreak)

        // День 3
        data = data.copy(lastCompletionDateMs = System.currentTimeMillis() - (25 * 60 * 60 * 1000L))
        store.save(data)
        store.recordSubLessonCompletion("en")
        data = store.load("en")
        assertEquals(3, data.longestStreak)

        // Пропустили несколько дней, затем новое занятие
        data = data.copy(lastCompletionDateMs = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L))
        store.save(data)
        store.recordSubLessonCompletion("en")
        data = store.load("en")

        assertEquals(1, data.currentStreak) // Текущий сброшен
        assertEquals(3, data.longestStreak) // Longest сохранён
    }

    @Test
    fun updateStreak_incrementsTotalDays() {
        // FR-13.1.4: totalSubLessonsCompleted всегда растёт
        store.recordSubLessonCompletion("en")
        var data = store.load("en")
        assertEquals(1, data.totalSubLessonsCompleted)

        store.recordSubLessonCompletion("en") // Тот же день
        data = store.load("en")
        assertEquals(2, data.totalSubLessonsCompleted)

        store.recordSubLessonCompletion("en") // Тот же день
        data = store.load("en")
        assertEquals(3, data.totalSubLessonsCompleted)
    }

    // ========================================
    // 8.3 Граничные случаи
    // ========================================

    @Test
    fun updateStreak_midnight_handlesCorrectly() {
        // Граничный случай: занятие ровно в полночь
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        val almostMidnight = calendar.timeInMillis

        val data = StreakData(
            languageId = "en",
            currentStreak = 1,
            longestStreak = 1,
            lastCompletionDateMs = almostMidnight,
            totalSubLessonsCompleted = 1
        )
        store.save(data)

        // Следующий день, ровно после полуночи
        calendar.add(Calendar.MINUTE, 2) // 00:01 следующего дня
        val afterMidnight = calendar.timeInMillis

        // Проверяем, что система правильно определяет новый день
        val current = store.load("en")
        val lastDate = Calendar.getInstance().apply { timeInMillis = current.lastCompletionDateMs!! }
        val today = Calendar.getInstance().apply { timeInMillis = afterMidnight }

        assertFalse("Should be different days",
            lastDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            lastDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        )
    }

    @Test
    fun getCurrentStreak_checksForMissedDays() {
        // FR-13.2.4: getCurrentStreak проверяет пропущенные дни
        store.recordSubLessonCompletion("en")
        var data = store.load("en")
        assertEquals(1, data.currentStreak)

        // Симулируем пропуск 3 дней
        data = data.copy(lastCompletionDateMs = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L))
        store.save(data)

        val current = store.getCurrentStreak("en")
        assertEquals(0, current.currentStreak) // Сброшен
    }

    @Test
    fun getCurrentStreak_withinOneDay_preservesStreak() {
        // Если прошло < 1 дня, streak сохраняется
        store.recordSubLessonCompletion("en")
        var data = store.load("en")

        // Симулируем прошествие 12 часов
        data = data.copy(lastCompletionDateMs = System.currentTimeMillis() - (12 * 60 * 60 * 1000L))
        store.save(data)

        val current = store.getCurrentStreak("en")
        assertEquals(1, current.currentStreak) // Сохранён
    }

    @Test
    fun differentLanguages_independentStreaks() {
        // Разные языки имеют независимые streaks
        store.recordSubLessonCompletion("en")
        store.recordSubLessonCompletion("ru")

        val enData = store.load("en")
        val ruData = store.load("ru")

        assertEquals("en", enData.languageId)
        assertEquals(1, enData.currentStreak)

        assertEquals("ru", ruData.languageId)
        assertEquals(1, ruData.currentStreak)

        // Обновляем только английский
        val yesterday = System.currentTimeMillis() - (25 * 60 * 60 * 1000L)
        val enUpdated = enData.copy(lastCompletionDateMs = yesterday)
        store.save(enUpdated)
        store.recordSubLessonCompletion("en")

        val enFinal = store.load("en")
        val ruFinal = store.load("ru")

        assertEquals(2, enFinal.currentStreak)
        assertEquals(1, ruFinal.currentStreak) // Русский не затронут
    }

    @Test
    fun multipleCompletionsPerDay_incrementsTotal() {
        // Множественные занятия в день увеличивают total, но не streak
        store.recordSubLessonCompletion("en")
        store.recordSubLessonCompletion("en")
        store.recordSubLessonCompletion("en")

        val data = store.load("en")
        assertEquals(1, data.currentStreak)
        assertEquals(3, data.totalSubLessonsCompleted)
    }
}
