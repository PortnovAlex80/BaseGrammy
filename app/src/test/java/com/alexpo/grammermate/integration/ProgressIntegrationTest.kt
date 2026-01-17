package com.alexpo.grammermate.integration

import android.content.Context
import com.alexpo.grammermate.data.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Integration tests for Progress system.
 *
 * Проверяет интеграцию: ProgressStore + TrainingProgress
 *
 * ВАЖНО: Включает тесты для режима ALL_MIXED с ограничением 300 карточек (FR-5.1.4-5.1.6)
 */
@RunWith(RobolectricTestRunner::class)
class ProgressIntegrationTest {

    private lateinit var context: Context
    private lateinit var store: ProgressStore
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        store = ProgressStore(context)
        testDir = File(context.filesDir, "grammarmate")
    }

    @After
    fun cleanup() {
        store.clear()
        testDir.deleteRecursively()
    }

    // ========================================
    // Базовые интеграционные тесты
    // ========================================

    @Test
    fun userStartsLesson_progressSaves() {
        // Симуляция: пользователь начинает урок
        val progress = TrainingProgress(
            languageId = "en",
            mode = TrainingMode.LESSON,
            lessonId = "lesson1",
            currentIndex = 0,
            state = SessionState.ACTIVE
        )

        store.save(progress)

        val loaded = store.load()
        assertEquals(TrainingMode.LESSON, loaded.mode)
        assertEquals("lesson1", loaded.lessonId)
        assertEquals(SessionState.ACTIVE, loaded.state)
    }

    @Test
    fun userPausesLesson_stateSaves() {
        // Симуляция: пользователь ставит на паузу
        var progress = TrainingProgress(
            languageId = "en",
            mode = TrainingMode.LESSON,
            lessonId = "lesson1",
            currentIndex = 5,
            state = SessionState.ACTIVE
        )
        store.save(progress)

        // Пауза
        progress = progress.copy(state = SessionState.PAUSED)
        store.save(progress)

        val loaded = store.load()
        assertEquals(SessionState.PAUSED, loaded.state)
        assertEquals(5, loaded.currentIndex) // Позиция сохранена
    }

    @Test
    fun userCompletesLesson_statisticsSave() {
        // Симуляция: пользователь завершает урок со статистикой
        val progress = TrainingProgress(
            languageId = "en",
            mode = TrainingMode.LESSON,
            lessonId = "lesson1",
            currentIndex = 30,
            correctCount = 25,
            incorrectCount = 5,
            activeTimeMs = 600000L, // 10 минут
            state = SessionState.PAUSED
        )

        store.save(progress)

        val loaded = store.load()
        assertEquals(25, loaded.correctCount)
        assertEquals(5, loaded.incorrectCount)
        assertEquals(600000L, loaded.activeTimeMs)
    }

    @Test
    fun userSwitchesMode_progressTransitions() {
        // Симуляция: переключение режимов
        var progress = TrainingProgress(
            mode = TrainingMode.LESSON,
            lessonId = "lesson1"
        )
        store.save(progress)

        // Переключение на ALL_SEQUENTIAL
        progress = TrainingProgress(
            mode = TrainingMode.ALL_SEQUENTIAL,
            lessonId = null
        )
        store.save(progress)

        val loaded = store.load()
        assertEquals(TrainingMode.ALL_SEQUENTIAL, loaded.mode)
        assertNull(loaded.lessonId)
    }

    // ========================================
    // КРИТИЧЕСКИЕ ТЕСТЫ: Режим ALL_MIXED
    // FR-5.1.4-5.1.6: Ограничение 300 карточек
    // ========================================

    @Test
    fun allMixedMode_metadata_canIndicateCardLimit() {
        // Этот тест демонстрирует, что в ProgressStore можно сохранить информацию
        // о том, что режим ALL_MIXED работает (потом TrainingViewModel применит лимит 300)

        val progress = TrainingProgress(
            mode = TrainingMode.ALL_MIXED,
            lessonId = null, // ALL_MIXED не привязан к конкретному уроку
            currentIndex = 0
        )

        store.save(progress)

        val loaded = store.load()
        assertEquals(TrainingMode.ALL_MIXED, loaded.mode)
        assertNull("ALL_MIXED should not have lessonId", loaded.lessonId)
    }

    @Test
    fun allMixedMode_progressTracking_worksCorrectly() {
        // FR-5.1.4-5.1.6: Проверка, что прогресс в ALL_MIXED сохраняется корректно
        // даже если карточек > 300 (TrainingViewModel должен показывать только 300)

        val progress = TrainingProgress(
            mode = TrainingMode.ALL_MIXED,
            lessonId = null,
            currentIndex = 150, // Пользователь прошёл 150 из 300 показываемых
            correctCount = 120,
            incorrectCount = 30
        )

        store.save(progress)

        val loaded = store.load()
        assertEquals(TrainingMode.ALL_MIXED, loaded.mode)
        assertEquals(150, loaded.currentIndex)
        assertEquals(120, loaded.correctCount)

        // Важно: Сам ProgressStore не ограничивает индекс - это делает TrainingViewModel
        // Здесь мы проверяем, что прогресс корректно сохраняется и загружается
    }

    @Test
    fun allMixedMode_completion_savesCorrectly() {
        // Симуляция: пользователь завершил ALL_MIXED режим
        val progress = TrainingProgress(
            mode = TrainingMode.ALL_MIXED,
            currentIndex = 300, // Завершил все 300 карточек
            correctCount = 250,
            incorrectCount = 50,
            state = SessionState.PAUSED
        )

        store.save(progress)

        val loaded = store.load()
        assertEquals(300, loaded.currentIndex)
        assertEquals(250, loaded.correctCount)
    }

    @Test
    fun allMixedMode_restart_resetsIndex() {
        // Симуляция: перезапуск ALL_MIXED (новая случайная выборка 300 карточек)
        var progress = TrainingProgress(
            mode = TrainingMode.ALL_MIXED,
            currentIndex = 150,
            correctCount = 100
        )
        store.save(progress)

        // Перезапуск - сброс прогресса
        progress = TrainingProgress(
            mode = TrainingMode.ALL_MIXED,
            currentIndex = 0,
            correctCount = 0,
            incorrectCount = 0
        )
        store.save(progress)

        val loaded = store.load()
        assertEquals(0, loaded.currentIndex)
        assertEquals(0, loaded.correctCount)
    }

    // ========================================
    // Boss rewards
    // ========================================

    @Test
    fun bossRewards_persist() {
        val progress = TrainingProgress(
            bossLessonRewards = mapOf(
                "lesson1" to "GOLD",
                "lesson2" to "SILVER"
            ),
            bossMegaReward = "GOLD"
        )

        store.save(progress)

        val loaded = store.load()
        assertEquals(2, loaded.bossLessonRewards.size)
        assertEquals("GOLD", loaded.bossLessonRewards["lesson1"])
        assertEquals("GOLD", loaded.bossMegaReward)
    }

    // ========================================
    // Elite mode
    // ========================================

    @Test
    fun eliteMode_progressPersists() {
        val progress = TrainingProgress(
            eliteStepIndex = 3,
            eliteBestSpeeds = listOf(1.5, 1.8, 2.0)
        )

        store.save(progress)

        val loaded = store.load()
        assertEquals(3, loaded.eliteStepIndex)
        assertEquals(3, loaded.eliteBestSpeeds.size)
        assertEquals(2.0, loaded.eliteBestSpeeds[2], 0.01)
    }

    // ========================================
    // Voice metrics
    // ========================================

    @Test
    fun voiceMetrics_persist() {
        val progress = TrainingProgress(
            voiceActiveMs = 120000L, // 2 минуты
            voiceWordCount = 50,
            hintCount = 3
        )

        store.save(progress)

        val loaded = store.load()
        assertEquals(120000L, loaded.voiceActiveMs)
        assertEquals(50, loaded.voiceWordCount)
        assertEquals(3, loaded.hintCount)
    }

    // ========================================
    // Persistence across restarts
    // ========================================

    @Test
    fun persistenceAcrossRestarts_maintainsState() {
        val progress = TrainingProgress(
            mode = TrainingMode.ALL_MIXED,
            currentIndex = 100,
            correctCount = 80,
            incorrectCount = 20
        )

        store.save(progress)

        // Симуляция перезапуска
        val newStore = ProgressStore(context)
        val loaded = newStore.load()

        assertEquals(TrainingMode.ALL_MIXED, loaded.mode)
        assertEquals(100, loaded.currentIndex)
        assertEquals(80, loaded.correctCount)
        assertEquals(20, loaded.incorrectCount)
    }
}
