package com.alexpo.grammermate.v2.feature.vocabdrill

import androidx.lifecycle.SavedStateHandle
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.VocabWord
import com.alexpo.grammermate.domain.model.WordMasteryState
import com.alexpo.grammermate.domain.repository.VocabDrillRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * ViewModel vocab drill (Фаза 4 срез 3): батч = due-слова + добор новыми по
 * рангу; ответ фиксируется recordWordReview (ADR-003 pack-scoped) до
 * продвижения; полный батч → Done.
 */
class VocabDrillViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val packId = PackId("FIXTURE_PACK")

    private fun word(id: String, rank: Int) = VocabWord(
        id = id,
        word = "w-$id",
        pos = "noun",
        rank = rank,
        meaningRu = "перевод-$id",
    )

    private val dueWord = word("due_1", 1)
    private val fresh1 = word("new_1", 2)
    private val fresh2 = word("new_2", 3)
    private val reviewed = word("old_1", 4)

    private fun repository(): VocabDrillRepository = mockk(relaxed = true) {
        every { observeDueWords(packId, any()) } returns flowOf(
            listOf(dueWord.id to WordMasteryState(wordId = dueWord.id, nextReviewDateMs = 1)),
        )
        coEvery { getAllWordMastery(packId) } returns mapOf(
            // reviewed уже повторялся и не due → в батч не попадает.
            reviewed.id to WordMasteryState(wordId = reviewed.id, nextReviewDateMs = Long.MAX_VALUE),
        )
        coEvery { getVocabWords(packId) } returns listOf(dueWord, reviewed, fresh1, fresh2)
    }

    private fun viewModel(repo: VocabDrillRepository = repository()) = VocabDrillViewModel(
        savedStateHandle = SavedStateHandle(mapOf("packId" to packId.value)),
        vocabDrillRepository = repo,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_batchIsDueFirstThenFreshByRank() {
        val vm = viewModel()

        val state = vm.state.value as VocabDrillViewState.Question
        assertThat(state.word.id).isEqualTo("due_1")
        assertThat(state.total).isEqualTo(3) // due + 2 новых; reviewed исключён
    }

    @Test
    fun reveal_showsTranslation_withoutRecording() {
        val repo = repository()
        val vm = viewModel(repo)

        vm.reveal()

        val state = vm.state.value as VocabDrillViewState.Revealed
        assertThat(state.word.meaningRu).isEqualTo("перевод-due_1")
        io.mockk.coVerify(exactly = 0) { repo.recordWordReview(any(), any(), any(), any()) }
    }

    @Test
    fun answer_recordsReview_thenAdvances() {
        val repo = repository()
        val vm = viewModel(repo)
        vm.reveal()

        vm.answer(true)

        val state = vm.state.value as VocabDrillViewState.Question
        assertThat(state.word.id).isEqualTo("new_1") // следующий по рангу
        io.mockk.coVerify(exactly = 1) {
            repo.recordWordReview(packId, "due_1", true, any())
        }
    }

    @Test
    fun fullBatch_endsInDoneWithCounts() {
        val vm = viewModel()
        repeat(3) {
            vm.reveal()
            vm.answer(true)
        }

        val state = vm.state.value as VocabDrillViewState.Done
        assertThat(state.reviewed).isEqualTo(3)
        assertThat(state.correct).isEqualTo(3)
    }

    @Test
    fun emptyPack_showsEmptyState() {
        val repo = mockk<VocabDrillRepository>(relaxed = true) {
            every { observeDueWords(packId, any()) } returns flowOf(emptyList())
            coEvery { getAllWordMastery(packId) } returns emptyMap()
            coEvery { getVocabWords(packId) } returns emptyList()
        }

        val vm = viewModel(repo)

        assertThat(vm.state.value).isInstanceOf(VocabDrillViewState.Empty::class.java)
    }

    @Test
    fun blankRoute_showsErrorWithoutLoading() {
        val repo = mockk<VocabDrillRepository>(relaxed = true)

        val vm = VocabDrillViewModel(
            savedStateHandle = SavedStateHandle(mapOf("packId" to "")),
            vocabDrillRepository = repo,
        )

        assertThat(vm.state.value).isInstanceOf(VocabDrillViewState.Error::class.java)
    }
}
