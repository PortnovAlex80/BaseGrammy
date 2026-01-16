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

/**
 * Unit tests for ProgressStore - защита сохранения и загрузки прогресса тренировки.
 *
 * Покрывает требования:
 * - FR-7.1.1-7.1.10: Все поля прогресса
 * - FR-7.2.1-7.2.2: Загрузка прогресса
 * - FR-7.3.1-7.3.2: Автосохранение
 * - FR-5.1.1-5.1.3: Режимы тренировки
 * - FR-5.2.1-5.2.4: Состояния сессии
 */
@RunWith(RobolectricTestRunner::class)
class ProgressStoreTest {

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
    // 4.1 Сохранение всех полей прогресса
    // ========================================

    @Test
    fun saveProgress_languageId_persists() {
        // FR-7.1.1: Сохранение languageId
        val progress = TrainingProgress(languageId = "ru")
        store.save(progress)

        val loaded = store.load()
        assertEquals("ru", loaded.languageId)
    }

    @Test
    fun saveProgress_trainingMode_persists() {
        // FR-7.1.2: Сохранение режима тренировки
        val progress = TrainingProgress(mode = TrainingMode.ALL_MIXED)
        store.save(progress)

        val loaded = store.load()
        assertEquals(TrainingMode.ALL_MIXED, loaded.mode)
    }

    @Test
    fun saveProgress_lessonId_persists() {
        // FR-7.1.3: Сохранение lessonId
        val progress = TrainingProgress(lessonId = "lesson123")
        store.save(progress)

        val loaded = store.load()
        assertEquals("lesson123", loaded.lessonId)
    }

    @Test
    fun saveProgress_currentIndex_persists() {
        // FR-7.1.4: Сохранение текущего индекса
        val progress = TrainingProgress(currentIndex = 42)
        store.save(progress)

        val loaded = store.load()
        assertEquals(42, loaded.currentIndex)
    }

    @Test
    fun saveProgress_correctWrongCounts_persist() {
        // FR-7.1.5: Сохранение счётчиков правильных/неправильных ответов
        val progress = TrainingProgress(
            correctCount = 15,
            incorrectCount = 5,
            incorrectAttemptsForCard = 2
        )
        store.save(progress)

        val loaded = store.load()
        assertEquals(15, loaded.correctCount)
        assertEquals(5, loaded.incorrectCount)
        assertEquals(2, loaded.incorrectAttemptsForCard)
    }

    @Test
    fun saveProgress_activeTimeMs_persists() {
        // FR-7.1.6: Сохранение активного времени
        val progress = TrainingProgress(activeTimeMs = 123456L)
        store.save(progress)

        val loaded = store.load()
        assertEquals(123456L, loaded.activeTimeMs)
    }

    @Test
    fun saveProgress_sessionState_persists() {
        // FR-7.1.7: Сохранение состояния сессии
        val progress = TrainingProgress(state = SessionState.ACTIVE)
        store.save(progress)

        val loaded = store.load()
        assertEquals(SessionState.ACTIVE, loaded.state)
    }

    @Test
    fun saveProgress_bossRewards_persist() {
        // FR-7.1.8: Сохранение наград Boss
        val progress = TrainingProgress(
            bossLessonRewards = mapOf("lesson1" to "GOLD", "lesson2" to "SILVER")
        )
        store.save(progress)

        val loaded = store.load()
        assertEquals(mapOf("lesson1" to "GOLD", "lesson2" to "SILVER"), loaded.bossLessonRewards)
    }

    @Test
    fun saveProgress_voiceMetrics_persist() {
        // FR-7.1.9: Сохранение метрик голоса
        val progress = TrainingProgress(
            voiceActiveMs = 5000L,
            voiceWordCount = 50,
            hintCount = 3
        )
        store.save(progress)

        val loaded = store.load()
        assertEquals(5000L, loaded.voiceActiveMs)
        assertEquals(50, loaded.voiceWordCount)
        assertEquals(3, loaded.hintCount)
    }

    @Test
    fun saveProgress_eliteProgress_persists() {
        // FR-7.1.10: Сохранение прогресса Elite
        val progress = TrainingProgress(
            eliteStepIndex = 3,
            eliteBestSpeeds = listOf(1.5, 2.0, 2.5)
        )
        store.save(progress)

        val loaded = store.load()
        assertEquals(3, loaded.eliteStepIndex)
        assertEquals(listOf(1.5, 2.0, 2.5), loaded.eliteBestSpeeds)
    }

    // ========================================
    // 4.2 Загрузка прогресса
    // ========================================

    @Test
    fun loadProgress_existingFile_returnsCorrectProgress() {
        // FR-7.2.1: Загрузка существующего прогресса
        val progress = TrainingProgress(
            languageId = "en",
            mode = TrainingMode.LESSON,
            lessonId = "lesson1",
            currentIndex = 5,
            correctCount = 10
        )
        store.save(progress)

        val loaded = store.load()
        assertEquals("en", loaded.languageId)
        assertEquals(TrainingMode.LESSON, loaded.mode)
        assertEquals("lesson1", loaded.lessonId)
        assertEquals(5, loaded.currentIndex)
        assertEquals(10, loaded.correctCount)
    }

    @Test
    fun loadProgress_missingFile_returnsDefaultProgress() {
        // FR-7.2.2: Отсутствующий файл → дефолтный прогресс
        store.clear()

        val loaded = store.load()
        assertEquals("en", loaded.languageId)
        assertEquals(TrainingMode.LESSON, loaded.mode)
        assertEquals(0, loaded.currentIndex)
        assertEquals(SessionState.PAUSED, loaded.state)
    }

    @Test
    fun loadProgress_corruptedFile_returnsDefaultProgress() {
        // FR-7.2.2: Повреждённый файл → дефолтный прогресс
        val file = File(testDir, "progress.yaml")
        testDir.mkdirs()
        file.writeText("invalid yaml content")

        val loaded = store.load()
        assertEquals("en", loaded.languageId)
    }

    // ========================================
    // 4.3 Автосохранение
    // ========================================

    @Test
    fun saveProgress_writesImmediately() {
        // FR-7.3.1: Сохранение происходит немедленно
        val progress = TrainingProgress(currentIndex = 10)
        store.save(progress)

        val file = File(testDir, "progress.yaml")
        assertTrue("File should exist immediately after save", file.exists())
    }

    @Test
    fun saveProgress_multipleCallsConcurrent_lastWins() {
        // FR-7.3.2: При множественных сохранениях побеждает последнее
        store.save(TrainingProgress(currentIndex = 1))
        store.save(TrainingProgress(currentIndex = 2))
        store.save(TrainingProgress(currentIndex = 3))

        val loaded = store.load()
        assertEquals(3, loaded.currentIndex)
    }

    // ========================================
    // 4.4 Режимы тренировки
    // ========================================

    @Test
    fun saveProgress_lessonMode_persists() {
        // FR-5.1.1: Режим LESSON
        val progress = TrainingProgress(mode = TrainingMode.LESSON, lessonId = "lesson1")
        store.save(progress)

        val loaded = store.load()
        assertEquals(TrainingMode.LESSON, loaded.mode)
        assertEquals("lesson1", loaded.lessonId)
    }

    @Test
    fun saveProgress_allSequentialMode_persists() {
        // FR-5.1.2: Режим ALL_SEQUENTIAL
        val progress = TrainingProgress(mode = TrainingMode.ALL_SEQUENTIAL, lessonId = null)
        store.save(progress)

        val loaded = store.load()
        assertEquals(TrainingMode.ALL_SEQUENTIAL, loaded.mode)
    }

    @Test
    fun saveProgress_allMixedMode_persists() {
        // FR-5.1.3: Режим ALL_MIXED
        val progress = TrainingProgress(mode = TrainingMode.ALL_MIXED, lessonId = null)
        store.save(progress)

        val loaded = store.load()
        assertEquals(TrainingMode.ALL_MIXED, loaded.mode)
    }

    // ========================================
    // 4.5 Состояния сессии
    // ========================================

    @Test
    fun saveProgress_activeState_persists() {
        // FR-5.2.1: Состояние ACTIVE
        val progress = TrainingProgress(state = SessionState.ACTIVE)
        store.save(progress)

        val loaded = store.load()
        assertEquals(SessionState.ACTIVE, loaded.state)
    }

    @Test
    fun saveProgress_pausedState_persists() {
        // FR-5.2.2: Состояние PAUSED
        val progress = TrainingProgress(state = SessionState.PAUSED)
        store.save(progress)

        val loaded = store.load()
        assertEquals(SessionState.PAUSED, loaded.state)
    }

    @Test
    fun saveProgress_afterCheckState_persists() {
        // FR-5.2.3: Состояние AFTER_CHECK
        val progress = TrainingProgress(state = SessionState.AFTER_CHECK)
        store.save(progress)

        val loaded = store.load()
        assertEquals(SessionState.AFTER_CHECK, loaded.state)
    }

    @Test
    fun saveProgress_hintShownState_persists() {
        // FR-5.2.4: Состояние HINT_SHOWN
        val progress = TrainingProgress(state = SessionState.HINT_SHOWN)
        store.save(progress)

        val loaded = store.load()
        assertEquals(SessionState.HINT_SHOWN, loaded.state)
    }

    // ========================================
    // 4.6 Boss награды
    // ========================================

    @Test
    fun saveProgress_lessonBossRewards_persist() {
        // FR-10.3.2: Награды за Lesson Boss
        val rewards = mapOf(
            "lesson1" to "BRONZE",
            "lesson2" to "SILVER",
            "lesson3" to "GOLD"
        )
        val progress = TrainingProgress(bossLessonRewards = rewards)
        store.save(progress)

        val loaded = store.load()
        assertEquals(rewards, loaded.bossLessonRewards)
    }

    @Test
    fun saveProgress_megaBossReward_persists() {
        // FR-10.4.2: Награда за Mega Boss
        val progress = TrainingProgress(bossMegaReward = "GOLD")
        store.save(progress)

        val loaded = store.load()
        assertEquals("GOLD", loaded.bossMegaReward)
    }

    @Test
    fun saveProgress_multipleLessonRewards_persist() {
        val rewards = mapOf(
            "L1" to "BRONZE",
            "L2" to "GOLD",
            "L3" to "SILVER"
        )
        val progress = TrainingProgress(bossLessonRewards = rewards)
        store.save(progress)

        val loaded = store.load()
        assertEquals(3, loaded.bossLessonRewards.size)
        assertEquals("BRONZE", loaded.bossLessonRewards["L1"])
        assertEquals("GOLD", loaded.bossLessonRewards["L2"])
    }

    // ========================================
    // 4.7 Elite режим
    // ========================================

    @Test
    fun saveProgress_eliteStepIndex_persists() {
        // FR-10.5.5: Индекс шага Elite
        val progress = TrainingProgress(eliteStepIndex = 5)
        store.save(progress)

        val loaded = store.load()
        assertEquals(5, loaded.eliteStepIndex)
    }

    @Test
    fun saveProgress_eliteBestSpeeds_persist() {
        // FR-10.5.5: Лучшие скорости Elite
        val speeds = listOf(1.2, 1.5, 1.8, 2.0)
        val progress = TrainingProgress(eliteBestSpeeds = speeds)
        store.save(progress)

        val loaded = store.load()
        assertEquals(speeds, loaded.eliteBestSpeeds)
    }

    @Test
    fun saveProgress_eliteBestSpeeds_multipleSteps() {
        // FR-10.5.5: Elite 7 этапов
        val speeds = listOf(1.0, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2)
        val progress = TrainingProgress(eliteBestSpeeds = speeds)
        store.save(progress)

        val loaded = store.load()
        assertEquals(7, loaded.eliteBestSpeeds.size)
        assertEquals(1.0, loaded.eliteBestSpeeds[0], 0.01)
        assertEquals(2.2, loaded.eliteBestSpeeds[6], 0.01)
    }

    // ========================================
    // 4.8 Граничные случаи
    // ========================================

    @Test
    fun saveProgress_nullLessonId_handled() {
        val progress = TrainingProgress(lessonId = null)
        store.save(progress)

        val loaded = store.load()
        assertNull(loaded.lessonId)
    }

    @Test
    fun saveProgress_emptyBossRewards_handled() {
        val progress = TrainingProgress(bossLessonRewards = emptyMap())
        store.save(progress)

        val loaded = store.load()
        assertTrue(loaded.bossLessonRewards.isEmpty())
    }

    @Test
    fun saveProgress_emptyEliteSpeeds_handled() {
        val progress = TrainingProgress(eliteBestSpeeds = emptyList())
        store.save(progress)

        val loaded = store.load()
        assertTrue(loaded.eliteBestSpeeds.isEmpty())
    }

    @Test
    fun saveProgress_zeroValues_persist() {
        val progress = TrainingProgress(
            currentIndex = 0,
            correctCount = 0,
            incorrectCount = 0,
            activeTimeMs = 0L
        )
        store.save(progress)

        val loaded = store.load()
        assertEquals(0, loaded.currentIndex)
        assertEquals(0, loaded.correctCount)
        assertEquals(0, loaded.incorrectCount)
        assertEquals(0L, loaded.activeTimeMs)
    }

    @Test
    fun saveProgress_largeNumbers_persist() {
        val progress = TrainingProgress(
            currentIndex = Int.MAX_VALUE,
            activeTimeMs = Long.MAX_VALUE
        )
        store.save(progress)

        val loaded = store.load()
        assertEquals(Int.MAX_VALUE, loaded.currentIndex)
        assertEquals(Long.MAX_VALUE, loaded.activeTimeMs)
    }

    @Test
    fun clear_removesFile() {
        store.save(TrainingProgress(currentIndex = 10))
        val file = File(testDir, "progress.yaml")
        assertTrue("File should exist", file.exists())

        store.clear()
        assertFalse("File should be deleted", file.exists())
    }

    @Test
    fun saveProgress_includesSchemaVersion() {
        // FR-2.2.1: Версия схемы включается в файл
        store.save(TrainingProgress())

        val file = File(testDir, "progress.yaml")
        val content = file.readText()
        assertTrue("File should contain schemaVersion", content.contains("schemaVersion"))
    }
}
