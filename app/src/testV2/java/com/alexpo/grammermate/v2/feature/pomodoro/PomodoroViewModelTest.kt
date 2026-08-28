package com.alexpo.grammermate.v2.feature.pomodoro

import androidx.lifecycle.SavedStateHandle
import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.Pack
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.PomodoroHistoryEntry
import com.alexpo.grammermate.domain.model.PomodoroPreset
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.UserContentRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * PomodoroViewModel (срез 7 Фазы 4): виртуальное время — отсчёт, пауза/резюм,
 * запись истории durable при завершении (обратном и ручном).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val packId = PackId("TEST_PACK")
    private val saved = mutableListOf<PomodoroHistoryEntry>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        saved.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): PomodoroViewModel {
        val userContent = mockk<UserContentRepository> {
            coEvery { addPomodoroSession(any()) } answers { saved += firstArg<PomodoroHistoryEntry>() }
        }
        val content = mockk<ContentRepository>(relaxed = true) {
            coEvery { getPack(packId) } returns Pack(
                id = packId, languageId = LanguageId("it"),
                displayName = null, version = "1", importedAtMs = 0,
            )
        }
        return PomodoroViewModel(
            savedStateHandle = SavedStateHandle(mapOf("packId" to packId.value)),
            userContentRepository = userContent,
            contentRepository = content,
        )
    }

    @Test
    fun countdownCompletes_andRecordsSession() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(PomodoroPreset.QUICK) // 5 минут
        runCurrent()

        advanceTimeBy(5 * 60_000L + 1_000)
        runCurrent()

        val state = vm.state.value as PomodoroViewState.Finished
        assertThat(state.entry.remainingSeconds).isEqualTo(0)
        assertThat(state.entry.totalSeconds).isEqualTo(5 * 60)
        assertThat(state.entry.languageId.value).isEqualTo("it")
        assertThat(saved).hasSize(1)
        assertThat(saved.single().packId).isEqualTo(packId)
    }

    @Test
    fun pauseFreezesTimer_resumeContinues() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(PomodoroPreset.QUICK)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        val beforePause = (vm.state.value as PomodoroViewState.Running).remainingMs
        assertThat(beforePause).isEqualTo(4 * 60_000L)

        vm.pause()
        advanceTimeBy(120_000) // на паузе время стоит
        runCurrent()
        assertThat(vm.state.value).isInstanceOf(PomodoroViewState.Paused::class.java)

        vm.resume()
        advanceTimeBy(60_000)
        runCurrent()
        val after = (vm.state.value as PomodoroViewState.Running).remainingMs
        assertThat(after).isEqualTo(3 * 60_000L)
        assertThat(saved).isEmpty() // ничего не записано — сессия идёт
    }

    @Test
    fun manualFinishRecordsElapsedWithRemainder() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(PomodoroPreset.FOCUS) // 15 минут
        runCurrent()
        advanceTimeBy(5 * 60_000)
        runCurrent()
        vm.finish()
        runCurrent()

        val entry = saved.single()
        assertThat(entry.remainingSeconds).isEqualTo(10 * 60)
        assertThat(entry.totalSeconds).isEqualTo(15 * 60)
        assertThat(vm.state.value).isInstanceOf(PomodoroViewState.Finished::class.java)
    }

    @Test
    fun startOnlyFromIdle_andDismissResets() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(PomodoroPreset.QUICK)
        runCurrent()
        vm.start(PomodoroPreset.CLASSIC) // игнор: не Idle
        runCurrent()
        assertThat((vm.state.value as PomodoroViewState.Running).preset)
            .isEqualTo(PomodoroPreset.QUICK)

        vm.pause()
        vm.dismiss()
        assertThat(vm.state.value).isEqualTo(PomodoroViewState.Idle)
    }
}
