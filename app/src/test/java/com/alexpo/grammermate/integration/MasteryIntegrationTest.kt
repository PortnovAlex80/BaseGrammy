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
 * Integration tests for Mastery system - проверка связки компонентов.
 *
 * Проверяет интеграцию: MasteryStore + FlowerCalculator + SpacedRepetitionConfig
 *
 * Покрывает полный цикл жизни цветка от SEED до GONE.
 */
@RunWith(RobolectricTestRunner::class)
class MasteryIntegrationTest {

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
    // Полный цикл жизни цветка
    // ========================================

    @Test
    fun userCompletesLesson_masteryGrows_flowerBlooms() {
        // Симуляция прохождения урока: пользователь показывает карточки
        val lessonId = "testLesson"
        val languageId = "en"

        // Начальное состояние: нет данных
        var mastery = store.get(lessonId, languageId)
        assertNull("Initially no mastery data", mastery)

        var flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("Initial state is SEED", FlowerState.SEED, flower.state)
        assertEquals(0f, flower.masteryPercent, 0.01f)

        // Пользователь показывает 40 карточек (26.6% мастерства < 33% → SEED)
        for (i in 1..40) {
            store.recordCardShow(lessonId, languageId, "card$i")
        }

        mastery = store.get(lessonId, languageId)
        assertNotNull(mastery)
        assertEquals(40, mastery!!.uniqueCardShows)
        assertEquals(40, mastery.totalCardShows)

        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("At 40 shows: SEED", FlowerState.SEED, flower.state)
        assertEquals(40f / 150f, flower.masteryPercent, 0.01f)

        // Пользователь показывает ещё 35 карточек (75/150 = 50% мастерства → SPROUT)
        for (i in 41..75) {
            store.recordCardShow(lessonId, languageId, "card$i")
        }

        mastery = store.get(lessonId, languageId)
        assertEquals(75, mastery!!.uniqueCardShows)

        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("At 75 shows: SPROUT", FlowerState.SPROUT, flower.state)
        assertTrue("Mastery ~50%", flower.masteryPercent in 0.33f..0.66f)

        // Пользователь показывает ещё 75 карточек (150/150 = 100% мастерства → BLOOM)
        for (i in 76..150) {
            store.recordCardShow(lessonId, languageId, "card$i")
        }

        mastery = store.get(lessonId, languageId)
        assertEquals(150, mastery!!.uniqueCardShows)

        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("At 150 shows: BLOOM", FlowerState.BLOOM, flower.state)
        assertEquals(1.0f, flower.masteryPercent, 0.01f)
    }

    @Test
    fun userSkipsDays_flowerWilts() {
        // Симуляция: пользователь начал урок, но пропустил несколько дней
        val lessonId = "testLesson"
        val languageId = "en"

        // Показываем 75 карточек (75/150 = 50% → SPROUT)
        for (i in 1..75) {
            store.recordCardShow(lessonId, languageId, "card$i")
        }

        var mastery = store.get(lessonId, languageId)!!
        var flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("Fresh state: SPROUT", FlowerState.SPROUT, flower.state)
        assertEquals(1.0f, flower.healthPercent, 0.01f)

        // Симулируем прошествие 3 дней (здоровье упадёт)
        val threeDaysAgo = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)
        mastery = mastery.copy(lastShowDateMs = threeDaysAgo)
        store.save(mastery)

        mastery = store.get(lessonId, languageId)!!
        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("After 3 days: WILTING", FlowerState.WILTING, flower.state)
        assertTrue("Health should drop", flower.healthPercent < 1.0f)
        assertTrue("Health above wilted threshold", flower.healthPercent > SpacedRepetitionConfig.WILTED_THRESHOLD)

        // Симулируем прошествие 60 дней (критическое увядание)
        val sixtyDaysAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)
        mastery = mastery.copy(lastShowDateMs = sixtyDaysAgo)
        store.save(mastery)

        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("After 60 days: WILTED", FlowerState.WILTED, flower.state)
        assertTrue("Health at minimum", flower.healthPercent <= SpacedRepetitionConfig.WILTED_THRESHOLD + 0.01f)
    }

    @Test
    fun userReturnsAfter90Days_flowerGone() {
        // Симуляция: пользователь забросил урок на 91+ дней
        val lessonId = "testLesson"
        val languageId = "en"

        // Показываем карточки
        for (i in 1..50) {
            store.recordCardShow(lessonId, languageId, "card$i")
        }

        var mastery = store.get(lessonId, languageId)!!

        // Симулируем прошествие 91 дня
        val ninetyOneDaysAgo = System.currentTimeMillis() - (91L * 24 * 60 * 60 * 1000)
        mastery = mastery.copy(lastShowDateMs = ninetyOneDaysAgo)
        store.save(mastery)

        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("After 91 days: GONE", FlowerState.GONE, flower.state)
        assertEquals(0f, flower.healthPercent, 0.01f)
    }

    @Test
    fun userRepeatsOnTime_intervalAdvances() {
        // Симуляция: пользователь повторяет карточки вовремя
        val lessonId = "testLesson"
        val languageId = "en"

        // Первый показ
        store.recordCardShow(lessonId, languageId, "card1")
        var mastery = store.get(lessonId, languageId)!!
        assertEquals("Initial step: 0", 0, mastery.intervalStepIndex)

        // Ждём 1 день (вовремя для step 0)
        val oneDayAgo = System.currentTimeMillis() - (1L * 24 * 60 * 60 * 1000)
        mastery = mastery.copy(lastShowDateMs = oneDayAgo)
        store.save(mastery)

        // Второй показ (вовремя)
        store.recordCardShow(lessonId, languageId, "card1")
        mastery = store.get(lessonId, languageId)!!
        assertEquals("Step advanced to 1", 1, mastery.intervalStepIndex)

        // Ждём 2 дня (вовремя для step 1)
        val twoDaysAgo = System.currentTimeMillis() - (2L * 24 * 60 * 60 * 1000)
        mastery = mastery.copy(lastShowDateMs = twoDaysAgo)
        store.save(mastery)

        // Третий показ (вовремя)
        store.recordCardShow(lessonId, languageId, "card1")
        mastery = store.get(lessonId, languageId)!!
        assertEquals("Step advanced to 2", 2, mastery.intervalStepIndex)
    }

    @Test
    fun userRepeatsLate_intervalStays() {
        // Симуляция: пользователь повторяет карточки с опозданием
        val lessonId = "testLesson"
        val languageId = "en"

        // Первый показ
        store.recordCardShow(lessonId, languageId, "card1")
        var mastery = store.get(lessonId, languageId)!!
        assertEquals("Initial step: 0", 0, mastery.intervalStepIndex)

        // Ждём 5 дней (опоздали для step 0 = 1 день)
        val fiveDaysAgo = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000)
        mastery = mastery.copy(lastShowDateMs = fiveDaysAgo)
        store.save(mastery)

        // Второй показ (с опозданием)
        store.recordCardShow(lessonId, languageId, "card1")
        mastery = store.get(lessonId, languageId)!!
        assertEquals("Step stays at 0 (late)", 0, mastery.intervalStepIndex)
    }

    @Test
    fun userReaches150Shows_achieves100PercentMastery() {
        // Симуляция: пользователь достигает 100% мастерства
        val lessonId = "testLesson"
        val languageId = "en"

        // Показываем все 150 карточек
        for (i in 1..150) {
            store.recordCardShow(lessonId, languageId, "card$i")
        }

        val mastery = store.get(lessonId, languageId)!!
        assertEquals(150, mastery.uniqueCardShows)
        assertEquals(150, mastery.totalCardShows)

        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("At 150 shows: BLOOM", FlowerState.BLOOM, flower.state)
        assertEquals(1.0f, flower.masteryPercent, 0.01f)
        assertEquals(1.0f, flower.scaleMultiplier, 0.01f)

        // Дополнительные показы не увеличивают mastery выше 100%
        for (i in 1..50) {
            store.recordCardShow(lessonId, languageId, "card$i") // Повторы
        }

        val updatedMastery = store.get(lessonId, languageId)!!
        assertEquals("Unique shows stays at 150", 150, updatedMastery.uniqueCardShows)
        assertEquals("Total shows increases", 200, updatedMastery.totalCardShows)

        val updatedFlower = FlowerCalculator.calculate(updatedMastery, totalCardsInLesson = 150)
        assertEquals("Still 100% mastery", 1.0f, updatedFlower.masteryPercent, 0.01f)
    }

    @Test
    fun persistenceAcrossRestarts_maintainsState() {
        // Симуляция: проверка сохранения состояния между перезапусками
        val lessonId = "testLesson"
        val languageId = "en"

        // Показываем 75 карточек
        for (i in 1..75) {
            store.recordCardShow(lessonId, languageId, "card$i")
        }

        var mastery = store.get(lessonId, languageId)!!
        val firstStepIndex = mastery.intervalStepIndex
        val firstLastShow = mastery.lastShowDateMs

        // Симуляция перезапуска: создаём новый store
        val newStore = MasteryStore(context)
        val loadedMastery = newStore.get(lessonId, languageId)!!

        assertEquals("Unique shows persisted", 75, loadedMastery.uniqueCardShows)
        assertEquals("Interval step persisted", firstStepIndex, loadedMastery.intervalStepIndex)
        assertEquals("Last show date persisted", firstLastShow, loadedMastery.lastShowDateMs)

        val flower = FlowerCalculator.calculate(loadedMastery, totalCardsInLesson = 150)
        assertEquals("State persisted: SPROUT", FlowerState.SPROUT, flower.state)
    }

    @Test
    fun multipleLanguages_independentProgress() {
        // Симуляция: независимый прогресс по разным языкам
        val lessonId = "lesson1"

        // Английский: 40 карточек (40/150 = 26.6% < 33% → SEED)
        for (i in 1..40) {
            store.recordCardShow(lessonId, "en", "card$i")
        }

        // Русский: 75 карточек (75/150 = 50% → SPROUT)
        for (i in 1..75) {
            store.recordCardShow(lessonId, "ru", "card$i")
        }

        val masteryEn = store.get(lessonId, "en")!!
        val masteryRu = store.get(lessonId, "ru")!!

        assertEquals(40, masteryEn.uniqueCardShows)
        assertEquals(75, masteryRu.uniqueCardShows)

        val flowerEn = FlowerCalculator.calculate(masteryEn, totalCardsInLesson = 150)
        val flowerRu = FlowerCalculator.calculate(masteryRu, totalCardsInLesson = 150)

        assertEquals("English: SEED", FlowerState.SEED, flowerEn.state)
        assertEquals("Russian: SPROUT", FlowerState.SPROUT, flowerRu.state)
    }
}
