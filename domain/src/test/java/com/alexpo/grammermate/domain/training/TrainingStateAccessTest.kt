package com.alexpo.grammermate.domain.training

import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionState
import com.alexpo.grammermate.domain.model.TrainingMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * AC-18 / GAP C2 — contract test for the [TrainingStateAccess] domain port.
 *
 * Фиксирует поведение successor-порта (мигрированного из legacy
 * `feature/daily/DailySessionHelper.kt`) до старта Wave 1: три операции
 * (reactive read, atomic update, suspend persist) ведут себя как канонический
 * контракт, на который опираются E04/E07/E08/E10/E11/E12.
 */
class TrainingStateAccessTest {

    // Минимальная корректная реализация порта — ровно та семантика, которую
    // должен обеспечить корневой composition (AppContainer/ViewModel-слой в E01).
    // compareAndSet-loop применяет transform атомарно → конкурирующие обновления
    // не теряются (та же семантика, что и MutableStateFlow.update).
    private class FakeTrainingStateAccess(
        initial: TrainingState = TrainingState.Empty,
    ) : TrainingStateAccess {
        private val backing = MutableStateFlow(initial)
        override val trainingState: StateFlow<TrainingState> = backing.asStateFlow()
        var saveProgressCalls = 0
            private set

        override fun updateState(transform: (TrainingState) -> TrainingState) {
            while (true) {
                val current = backing.value
                val updated = transform(current)
                if (backing.compareAndSet(current, updated)) return
            }
        }

        override suspend fun saveProgress() {
            saveProgressCalls++
        }
    }

    @Test
    fun `port exposes reactive trainingState as non-null initial StateFlow`() {
        val access = FakeTrainingStateAccess()

        // Гарантия для подписчиков из фич: всегда есть текущее значение.
        assertThat(access.trainingState.value).isEqualTo(TrainingState.Empty)
    }

    @Test
    fun `updateState applies pure transform to current navigation context`() {
        val access = FakeTrainingStateAccess()
        val pack = PackId("pack-es")
        val language = LanguageId("es")
        val lesson = LessonId("lesson-03")

        access.updateState { it.copy(navigation = it.navigation.copy(activePackId = pack)) }
        access.updateState { it.copy(navigation = it.navigation.copy(selectedLanguageId = language)) }
        access.updateState { it.copy(navigation = it.navigation.copy(selectedLessonId = lesson)) }

        val nav = access.trainingState.value.navigation
        assertThat(nav.activePackId).isEqualTo(pack)
        assertThat(nav.selectedLanguageId).isEqualTo(language)
        assertThat(nav.selectedLessonId).isEqualTo(lesson)
    }

    @Test
    fun `updateState updates session counters atomically`() {
        val access = FakeTrainingStateAccess()

        // Имитируем ответ пользователя: correct +1, индекс +1.
        access.updateState { state ->
            state.copy(
                session = state.session.copy(
                    correctCount = state.session.correctCount + 1,
                    currentIndex = state.session.currentIndex + 1,
                )
            )
        }

        assertThat(access.trainingState.value.session.correctCount).isEqualTo(1)
        assertThat(access.trainingState.value.session.currentIndex).isEqualTo(1)
    }

    @Test
    fun `concurrent updateState transforms do not lose updates`() {
        val access = FakeTrainingStateAccess()
        val iterations = 200

        // Многопоточный increment счётчика через CAS-цикл реализации:
        // если updateState атомарна, итог = 8*iterations (race-free).
        val threads = List(8) {
            Thread {
                repeat(iterations) {
                    access.updateState { s ->
                        s.copy(session = s.session.copy(correctCount = s.session.correctCount + 1))
                    }
                }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach { it.join() }

        assertThat(access.trainingState.value.session.correctCount)
            .isEqualTo(8 * iterations)
    }

    @Test
    fun `saveProgress is suspend and callable from coroutine context`() = runTest {
        val access = FakeTrainingStateAccess()

        access.saveProgress()
        access.saveProgress()

        assertThat(access.saveProgressCalls).isEqualTo(2)
    }

    @Test
    fun `trainingState reflects the latest value after successive updates`() {
        val access = FakeTrainingStateAccess()

        access.updateState { it.copy(session = it.session.copy(correctCount = 5)) }
        access.updateState { it.copy(session = it.session.copy(incorrectCount = 2)) }

        // StateFlow.value всегда отражает последнее значение (reactive contract
        // для подписчиков: collect получит то же, что value).
        val latest = access.trainingState.value
        assertThat(latest.session.correctCount).isEqualTo(5)
        assertThat(latest.session.incorrectCount).isEqualTo(2)
    }

    @Test
    fun `TrainingState Empty defaults match cross-cutting contract`() {
        val empty = TrainingState.Empty

        // Контракт: начальное состояние — ничего не выбрано, счётчики нулевые,
        // режим LESSON (как legacy NavigationState.mode default).
        assertThat(empty.navigation.activePackId).isNull()
        assertThat(empty.navigation.selectedLanguageId).isNull()
        assertThat(empty.navigation.selectedLessonId).isNull()
        assertThat(empty.navigation.mode).isEqualTo(TrainingMode.LESSON)
        assertThat(empty.navigation.currentScreen).isEqualTo("HOME")
        assertThat(empty.session.sessionState).isEqualTo(SessionState.ACTIVE)
        assertThat(empty.session.correctCount).isEqualTo(0)
        assertThat(empty.session.currentIndex).isEqualTo(0)
        assertThat(empty.session.activeTimeMs).isEqualTo(0L)
    }
}
