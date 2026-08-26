package com.alexpo.grammermate.v2.feature.home

import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.Pack
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.PackLessonProgress
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.MasteryRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Реактивный прогресс паков на Home (Фаза 3 slice 2 + ADR-002 слой 1):
 * [HomeViewModel] комбинирует список паков с агрегатом
 * [MasteryRepository.observePackProgress] — завершение урока отражается на
 * карточке пака без перезахода на экран.
 */
class HomeViewModelProgressTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun pack(id: String): Pack = Pack(
        id = PackId(id),
        languageId = LanguageId("it"),
        displayName = "Pack $id",
        version = "1",
        importedAtMs = 0L,
    )

    @Test
    fun `packs combined with progress - fraction from completedAtMs aggregate`() {
        val packsFlow = MutableStateFlow(listOf(pack("P1"), pack("P2")))
        val progressFlow = MutableStateFlow(
            listOf(
                PackLessonProgress(PackId("P1"), totalLessons = 4, completedLessons = 1),
                PackLessonProgress(PackId("P2"), totalLessons = 2, completedLessons = 2),
            )
        )
        val content = mockk<ContentRepository> {
            every { observePacks() } returns packsFlow
        }
        val mastery = mockk<MasteryRepository> {
            every { observePackProgress() } returns progressFlow
        }

        val vm = HomeViewModel(content, mastery)

        val state = vm.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.packs).hasSize(2)
        assertThat(state.packProgress.getValue("P1").fraction).isEqualTo(0.25f)
        assertThat(state.packProgress.getValue("P2").fraction).isEqualTo(1.0f)
    }

    @Test
    fun `progress update emits new state without re-subscribe`() {
        val packsFlow = MutableStateFlow(listOf(pack("P1")))
        val progressFlow = MutableStateFlow(
            listOf(PackLessonProgress(PackId("P1"), totalLessons = 4, completedLessons = 0))
        )
        val vm = HomeViewModel(
            mockk<ContentRepository> { every { observePacks() } returns packsFlow },
            mockk<MasteryRepository> { every { observePackProgress() } returns progressFlow },
        )
        assertThat(vm.state.value.packProgress.getValue("P1").fraction).isEqualTo(0f)

        // Завершили урок в тренировке → Room-инвалидация → новый агрегат.
        progressFlow.value =
            listOf(PackLessonProgress(PackId("P1"), totalLessons = 4, completedLessons = 1))

        assertThat(vm.state.value.packProgress.getValue("P1").fraction).isEqualTo(0.25f)
    }

    @Test
    fun `pack without mastery rows shows zero progress`() {
        val vm = HomeViewModel(
            mockk<ContentRepository> {
                every { observePacks() } returns MutableStateFlow(listOf(pack("P1")))
            },
            mockk<MasteryRepository> {
                every { observePackProgress() } returns MutableStateFlow(emptyList())
            },
        )

        assertThat(vm.state.value.packProgress).isEmpty()
        assertThat(vm.state.value.packs).hasSize(1)
    }
}
