package com.alexpo.grammermate.v2.feature.home

import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.Pack
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.MasteryRepository
import com.alexpo.grammermate.v2.core.seed.BundledSeedState
import com.alexpo.grammermate.v2.core.seed.BundledSeedStatus
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Регрессия HomeViewModel (Фаза 1 плана стабилизации 2026-08-26): Home
 * реактивен — видит bundled-seed сразу после первого импорта (P2 «Home не
 * реактивен» закрыт для списка паков).
 */
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val pack = Pack(
        id = PackId("ITALIAN_SHORT"),
        languageId = LanguageId("it"),
        displayName = "Italian Fast Track",
        version = "v6",
        importedAtMs = 0L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(packsFlow: kotlinx.coroutines.flow.Flow<List<Pack>>): ContentRepository =
        mockk {
            every { observePacks() } returns packsFlow
            coEvery { getPacks() } returns emptyList()
        }

    /** Прогресс-канал для этих кейсов пуст (пак без mastery-строк = 0%). */
    private fun emptyProgress(): MasteryRepository = mockk {
        every { observePackProgress() } returns flowOf(emptyList())
    }

    @Test
    fun init_emitsPacksFromFlow() {
        val vm = HomeViewModel(repository(flowOf(listOf(pack))), emptyProgress())

        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.packs).containsExactly(pack)
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun init_emptyThenSeeded_updatesReactive() {
        val vm = HomeViewModel(
            repository(
                flow {
                    emit(emptyList())
                    emit(listOf(pack))
                }
            ),
            emptyProgress(),
        )

        // Последняя эмиссия канала — seeded-состояние (bundled-import завершён).
        assertThat(vm.state.value.packs).containsExactly(pack)
    }

    @Test
    fun init_flowError_setsErrorState() {
        val vm = HomeViewModel(
            repository(flow { throw IllegalStateException("db closed") }),
            emptyProgress(),
        )

        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.error).isNotNull()
    }

    @Test
    fun emptyPacks_whileBundledSeedRuns_staysLoading() {
        val seedStatus = BundledSeedStatus().apply { publish(BundledSeedState.Running) }
        val vm = HomeViewModel(repository(flowOf(emptyList())), emptyProgress(), seedStatus)

        assertThat(vm.state.value.isLoading).isTrue()
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun emptyPacks_afterBundledSeedFailure_showsError() {
        val seedStatus = BundledSeedStatus().apply { publish(BundledSeedState.Failed("broken pack")) }
        val vm = HomeViewModel(repository(flowOf(emptyList())), emptyProgress(), seedStatus)

        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.error).isEqualTo("broken pack")
    }
}
