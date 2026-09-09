package com.alexpo.grammermate.data

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Контракт лестницы интервалов на реальном [MasteryStoreImpl].
 *
 * Ключевое правило: показ карточки — это экспозиция, а не доказательство
 * воспроизведения. Правильность ответа сигналом быть не может (ASR ошибается
 * слишком часто), поэтому лестницу двигает только самостоятельный ввод.
 *
 * Спека: docs/specification/forgetting-curve-review-scheduling.md
 */
@RunWith(RobolectricTestRunner::class)
class MasteryLadderRulesTest {

    private lateinit var store: MasteryStoreImpl
    private val pack = "IT_EXPRESS"
    private val lesson = "lesson_01_A01"

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "grammarmate").deleteRecursively()
        store = MasteryStoreImpl(context)
        store.clear()
    }

    @Test
    fun recordCardShow_doesNotAdvanceLadder() {
        repeat(30) { store.recordCardShowForPack(pack, lesson, "card_$it") }

        val state = store.getForPack(pack, lesson)
        assertThat(state).isNotNull()
        assertThat(state!!.totalCardShows).isEqualTo(30)
        assertThat(state.intervalStepIndex).isEqualTo(0)
        // Экспозиция засчитана, но урок не считается повторённым.
        assertThat(state.lastReviewMs).isEqualTo(0L)
    }

    @Test
    fun recordCardShow_preservesEncounterCounts() {
        store.recordCardEncounterForPack(pack, lesson, "card_1")
        store.recordCardEncounterForPack(pack, lesson, "card_1")
        store.recordCardShowForPack(pack, lesson, "card_1")

        val state = store.getForPack(pack, lesson)
        assertThat(state!!.cardEncounterCounts["card_1"]).isEqualTo(2)
    }

    @Test
    fun recordSelfProduced_stampsReviewMarkersAndHoldsStepOnFirstCall() {
        store.recordSelfProducedForPack(pack, lesson, totalEffortCards = 42)

        val state = store.getForPack(pack, lesson)!!
        assertThat(state.lastReviewMs).isGreaterThan(0L)
        assertThat(state.effortAtLastReview).isEqualTo(42)
        // Первое воспроизведение задаёт точку отсчёта, но не двигает шаг.
        assertThat(state.intervalStepIndex).isEqualTo(0)
    }

    @Test
    fun recordSelfProduced_doesNotAdvanceTwiceWithinSameDay() {
        store.recordSelfProducedForPack(pack, lesson, totalEffortCards = 10)
        repeat(20) { store.recordSelfProducedForPack(pack, lesson, totalEffortCards = 10 + it) }

        val state = store.getForPack(pack, lesson)!!
        assertThat(state.intervalStepIndex).isEqualTo(0)
    }

    @Test
    fun recordSelfProduced_advancesStepAfterADayHasPassed() {
        // Ставим точку последнего повторения на сутки назад, шаг 0 (интервал 1 день).
        val yesterday = System.currentTimeMillis() - 86_400_000L - 1000L
        store.saveForPack(
            LessonMasteryState(
                lessonId = LessonId(lesson),
                languageId = LanguageId("it"),
                intervalStepIndex = 0,
                lastReviewMs = yesterday
            ),
            pack
        )

        store.recordSelfProducedForPack(pack, lesson, totalEffortCards = 60)

        val state = store.getForPack(pack, lesson)!!
        assertThat(state.intervalStepIndex).isEqualTo(1)
        assertThat(state.effortAtLastReview).isEqualTo(60)
    }

    @Test
    fun newFieldsSurviveAWriteReadCycle() {
        store.recordSelfProducedForPack(pack, lesson, totalEffortCards = 123)
        val before = store.getForPack(pack, lesson)!!

        val reopened = MasteryStoreImpl(RuntimeEnvironment.getApplication())
        val after = reopened.getForPack(pack, lesson)

        assertThat(after).isNotNull()
        assertThat(after!!.effortAtLastReview).isEqualTo(123)
        assertThat(after.lastReviewMs).isEqualTo(before.lastReviewMs)
    }

    @Test
    fun totalEffortCards_sumsAcrossLessonsOfThePack() {
        repeat(5) { store.recordCardShowForPack(pack, "lesson_01", "a_$it") }
        repeat(7) { store.recordCardShowForPack(pack, "lesson_02", "b_$it") }
        repeat(3) { store.recordCardShowForPack("OTHER_PACK", "lesson_01", "c_$it") }

        assertThat(store.totalEffortCardsForPack(pack)).isEqualTo(12)
        assertThat(store.totalEffortCardsForPack("OTHER_PACK")).isEqualTo(3)
    }
}
