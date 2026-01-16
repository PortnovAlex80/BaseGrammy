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
 * Unit tests for MasteryStore - защита сохранения и загрузки прогресса мастерства.
 *
 * Покрывает требования:
 * - FR-8.2.1-8.2.6: Метрики мастерства
 * - FR-8.4.1-8.4.6: Запись показов карточек
 * - FR-8.5.1-8.5.4: Хранение и кеширование
 */
@RunWith(RobolectricTestRunner::class)
class MasteryStoreTest {

    private lateinit var context: Context
    private lateinit var store: MasteryStore
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        store = MasteryStore(context)
        testDir = File(context.filesDir, "grammarmate")
    }

    @After
    fun cleanup() {
        store.clear()
        testDir.deleteRecursively()
    }

    // ========================================
    // 3.1 Сохранение и загрузка
    // ========================================

    @Test
    fun saveMastery_newState_writesToFile() {
        // FR-8.5.1: Сохранение нового состояния
        val state = LessonMasteryState(
            lessonId = "lesson1",
            languageId = "en",
            uniqueCardShows = 10,
            totalCardShows = 20
        )
        store.save(state)

        val file = File(testDir, "mastery.yaml")
        assertTrue("File should exist", file.exists())
    }

    @Test
    fun loadMastery_existingFile_returnsCorrectState() {
        // FR-8.5.1: Загрузка существующих данных
        val state = LessonMasteryState(
            lessonId = "lesson1",
            languageId = "en",
            uniqueCardShows = 50,
            totalCardShows = 100,
            lastShowDateMs = 123456789L
        )
        store.save(state)

        val loaded = store.get("lesson1", "en")
        assertNotNull(loaded)
        assertEquals(50, loaded!!.uniqueCardShows)
        assertEquals(100, loaded.totalCardShows)
        assertEquals(123456789L, loaded.lastShowDateMs)
    }

    @Test
    fun loadMastery_missingFile_returnsNull() {
        // FR-8.5.4: Отсутствующий файл → null
        store.clear()

        val loaded = store.get("nonexistent", "en")
        assertNull(loaded)
    }

    @Test
    fun loadMastery_corruptedFile_returnsEmptyMap() {
        // FR-8.5.4: Повреждённый файл → пустая карта
        val file = File(testDir, "mastery.yaml")
        testDir.mkdirs()
        file.writeText("invalid yaml: [[[")

        val all = store.loadAll()
        assertTrue("Corrupted file should return empty map", all.isEmpty())
    }

    // ========================================
    // 3.2 Кеширование
    // ========================================

    @Test
    fun loadMastery_calledTwice_usesCache() {
        // FR-8.5.2: Повторный вызов использует кеш
        val state = LessonMasteryState(
            lessonId = "lesson1",
            languageId = "en",
            uniqueCardShows = 50
        )
        store.save(state)

        val first = store.get("lesson1", "en")
        val second = store.get("lesson1", "en")

        assertSame("Should return same instance from cache", first, second)
    }

    @Test
    fun saveMastery_updatesCache() {
        // FR-8.5.2: Сохранение обновляет кеш
        val state1 = LessonMasteryState(
            lessonId = "lesson1",
            languageId = "en",
            uniqueCardShows = 50
        )
        store.save(state1)

        val state2 = state1.copy(uniqueCardShows = 60)
        store.save(state2)

        val loaded = store.get("lesson1", "en")
        assertEquals(60, loaded!!.uniqueCardShows)
    }

    // ========================================
    // 3.3 Запись показов карточек
    // ========================================

    @Test
    fun recordCardShow_firstTime_increasesUniqueShows() {
        // FR-8.4.1: Первый показ → увеличивает uniqueCardShows
        store.recordCardShow("lesson1", "en", "card1")

        val state = store.get("lesson1", "en")
        assertNotNull(state)
        assertEquals(1, state!!.uniqueCardShows)
    }

    @Test
    fun recordCardShow_secondTime_doesNotIncreaseUniqueShows() {
        // FR-8.4.2: Повторный показ → не увеличивает uniqueCardShows
        store.recordCardShow("lesson1", "en", "card1")
        store.recordCardShow("lesson1", "en", "card1")

        val state = store.get("lesson1", "en")
        assertEquals(1, state!!.uniqueCardShows)
    }

    @Test
    fun recordCardShow_alwaysIncreasesTotalShows() {
        // FR-8.4.3: Любой показ увеличивает totalCardShows
        store.recordCardShow("lesson1", "en", "card1")
        store.recordCardShow("lesson1", "en", "card1")
        store.recordCardShow("lesson1", "en", "card2")

        val state = store.get("lesson1", "en")
        assertEquals(2, state!!.uniqueCardShows)
        assertEquals(3, state.totalCardShows)
    }

    @Test
    fun recordCardShow_updatesLastShowDate() {
        // FR-8.4.4: Обновляет lastShowDateMs
        val before = System.currentTimeMillis()
        store.recordCardShow("lesson1", "en", "card1")
        val after = System.currentTimeMillis()

        val state = store.get("lesson1", "en")
        assertTrue("lastShowDateMs should be set", state!!.lastShowDateMs in before..after)
    }

    @Test
    fun recordCardShow_addsCardIdToSet() {
        // FR-8.4.5: Добавляет cardId в shownCardIds
        store.recordCardShow("lesson1", "en", "card1")
        store.recordCardShow("lesson1", "en", "card2")

        val state = store.get("lesson1", "en")
        assertEquals(setOf("card1", "card2"), state!!.shownCardIds)
    }

    @Test
    fun recordCardShow_updatesIntervalStepOnTime() {
        // FR-8.4.6: При своевременном повторении шаг увеличивается
        // Симулируем первый показ
        store.recordCardShow("lesson1", "en", "card1")
        val state1 = store.get("lesson1", "en")!!
        assertEquals(0, state1.intervalStepIndex)

        // Ждём виртуально 1 день (модифицируем вручную)
        val updated = state1.copy(
            lastShowDateMs = System.currentTimeMillis() - (1L * 24 * 60 * 60 * 1000)
        )
        store.save(updated)

        // Второй показ через 1 день (вовремя для step 0)
        store.recordCardShow("lesson1", "en", "card1")
        val state2 = store.get("lesson1", "en")!!

        assertEquals(1, state2.intervalStepIndex)
    }

    @Test
    fun recordCardShow_keepsIntervalStepWhenLate() {
        // FR-8.4.6: При опоздании шаг не увеличивается
        store.recordCardShow("lesson1", "en", "card1")
        val state1 = store.get("lesson1", "en")!!

        // Ждём виртуально 10 дней (опоздали для step 0 = 1 день)
        val updated = state1.copy(
            lastShowDateMs = System.currentTimeMillis() - (10L * 24 * 60 * 60 * 1000)
        )
        store.save(updated)

        store.recordCardShow("lesson1", "en", "card1")
        val state2 = store.get("lesson1", "en")!!

        assertEquals(0, state2.intervalStepIndex) // Шаг не изменился
    }

    // ========================================
    // 3.4 Множественные уроки
    // ========================================

    @Test
    fun saveMastery_multipleLanguages_separatesCorrectly() {
        // FR-8.5.3: Разделение по языкам
        val stateEn = LessonMasteryState(lessonId = "lesson1", languageId = "en", uniqueCardShows = 10)
        val stateRu = LessonMasteryState(lessonId = "lesson1", languageId = "ru", uniqueCardShows = 20)

        store.save(stateEn)
        store.save(stateRu)

        val loadedEn = store.get("lesson1", "en")
        val loadedRu = store.get("lesson1", "ru")

        assertEquals(10, loadedEn!!.uniqueCardShows)
        assertEquals(20, loadedRu!!.uniqueCardShows)
    }

    @Test
    fun loadMastery_specificLesson_returnsOnlyThatLesson() {
        // FR-8.5.3: Загрузка конкретного урока
        store.save(LessonMasteryState(lessonId = "lesson1", languageId = "en", uniqueCardShows = 10))
        store.save(LessonMasteryState(lessonId = "lesson2", languageId = "en", uniqueCardShows = 20))

        val lesson1 = store.get("lesson1", "en")
        assertEquals(10, lesson1!!.uniqueCardShows)

        val lesson2 = store.get("lesson2", "en")
        assertEquals(20, lesson2!!.uniqueCardShows)
    }

    @Test
    fun saveMastery_preservesOtherLessons() {
        // FR-8.5.3: Сохранение одного урока не удаляет другие
        store.save(LessonMasteryState(lessonId = "lesson1", languageId = "en", uniqueCardShows = 10))
        store.save(LessonMasteryState(lessonId = "lesson2", languageId = "en", uniqueCardShows = 20))

        // Обновляем lesson1
        store.save(LessonMasteryState(lessonId = "lesson1", languageId = "en", uniqueCardShows = 15))

        // lesson2 должен остаться нетронутым
        val lesson2 = store.get("lesson2", "en")
        assertEquals(20, lesson2!!.uniqueCardShows)
    }

    // ========================================
    // 3.5 Версионирование схемы
    // ========================================

    @Test
    fun saveMastery_includesSchemaVersion() {
        // FR-2.2.1: Включение версии схемы
        val state = LessonMasteryState(lessonId = "lesson1", languageId = "en")
        store.save(state)

        val file = File(testDir, "mastery.yaml")
        val content = file.readText()
        assertTrue("File should contain schemaVersion", content.contains("schemaVersion"))
    }

    // ========================================
    // 3.6 Дополнительные методы
    // ========================================

    @Test
    fun markCardsShownForProgress_addsCardIds() {
        val cardIds = listOf("card1", "card2", "card3")
        store.markCardsShownForProgress("lesson1", "en", cardIds)

        val state = store.get("lesson1", "en")
        assertNotNull(state)
        assertTrue(state!!.shownCardIds.containsAll(cardIds))
    }

    @Test
    fun markCardsShownForProgress_doesNotAffectMetrics() {
        store.markCardsShownForProgress("lesson1", "en", listOf("card1"))

        val state = store.get("lesson1", "en")
        assertEquals(0, state!!.uniqueCardShows) // Метрики не затронуты
        assertEquals(0, state.totalCardShows)
    }

    @Test
    fun markLessonCompleted_setsCompletedAt() {
        store.recordCardShow("lesson1", "en", "card1")
        store.markLessonCompleted("lesson1", "en")

        val state = store.get("lesson1", "en")
        assertNotNull(state!!.completedAtMs)
    }

    @Test
    fun markLessonCompleted_idempotent() {
        store.recordCardShow("lesson1", "en", "card1")
        store.markLessonCompleted("lesson1", "en")
        val firstTime = store.get("lesson1", "en")!!.completedAtMs

        Thread.sleep(10)
        store.markLessonCompleted("lesson1", "en")
        val secondTime = store.get("lesson1", "en")!!.completedAtMs

        assertEquals(firstTime, secondTime) // Не должно обновляться
    }

    @Test
    fun getOrCreate_existingLesson_returnsExisting() {
        val state = LessonMasteryState(lessonId = "lesson1", languageId = "en", uniqueCardShows = 50)
        store.save(state)

        val loaded = store.getOrCreate("lesson1", "en")
        assertEquals(50, loaded.uniqueCardShows)
    }

    @Test
    fun getOrCreate_newLesson_returnsDefault() {
        val loaded = store.getOrCreate("newLesson", "en")
        assertEquals(0, loaded.uniqueCardShows)
        assertEquals("newLesson", loaded.lessonId)
        assertEquals("en", loaded.languageId)
    }

    @Test
    fun clearLanguage_removesOnlyThatLanguage() {
        store.save(LessonMasteryState(lessonId = "lesson1", languageId = "en", uniqueCardShows = 10))
        store.save(LessonMasteryState(lessonId = "lesson1", languageId = "ru", uniqueCardShows = 20))

        store.clearLanguage("en")

        assertNull(store.get("lesson1", "en"))
        assertNotNull(store.get("lesson1", "ru"))
    }

    @Test
    fun clear_removesAllData() {
        store.save(LessonMasteryState(lessonId = "lesson1", languageId = "en", uniqueCardShows = 10))
        store.save(LessonMasteryState(lessonId = "lesson2", languageId = "en", uniqueCardShows = 20))

        store.clear()

        val all = store.loadAll()
        assertTrue("All data should be cleared", all.isEmpty())
    }
}
