package com.alexpo.grammermate.v2.feature.daily

import androidx.lifecycle.SavedStateHandle
import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.DailyCursor
import com.alexpo.grammermate.domain.model.DailyTask
import com.alexpo.grammermate.domain.model.Lesson
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.VerbDrillCard
import com.alexpo.grammermate.domain.model.VocabWord
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.ProgressRepository
import com.alexpo.grammermate.domain.repository.VocabDrillRepository
import com.alexpo.grammermate.domain.validation.AnswerValidator
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
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
 * ViewModel дневной нормы (срез 4 Фазы 4): лента {5T+3V+2Verbs} по курсору;
 * vocab-ответы пишут word-SRS ДО продвижения; конец дня двигает все три
 * смещения курсора ровно на число потреблённых задач.
 */
class DailyPracticeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val packId = PackId("FIXTURE_PACK")
    private val lessonId = LessonId("lesson_fix_01")

    private val sentences = (0 until 6).map { i ->
        Card(
            id = CardId("s_$i"), packId = packId, lessonId = lessonId, ord = i,
            type = CardType.SENTENCE, promptRu = "p_$i",
            acceptedAnswers = listOf("a_$i"),
            tense = null, verb = null, verbGroup = null, person = null, frequencyRank = null,
        )
    }
    private val verbs = (0 until 3).map { VerbDrillCard("v_$it", "спрягай $it", "va_$it") }
    private val vocab = (0 until 4).map {
        VocabWord(id = "w_$it", word = "word_$it", pos = "noun", rank = it, meaningRu = "м_$it")
    }

    private val savedCursor = mutableListOf<DailyCursor>()

    private fun progress(cursor: DailyCursor? = null) = mockk<ProgressRepository>(relaxed = true) {
        coEvery { getDailyCursor(packId) } returns cursor
        coEvery { saveDailyCursor(any()) } answers { savedCursor += firstArg<DailyCursor>() }
    }

    private fun content() = mockk<ContentRepository>(relaxed = true) {
        coEvery { getLessons(packId) } returns listOf(
            Lesson(lessonId, packId, null, 0, "L", null, null, emptyList()),
        )
        coEvery { getCards(packId, lessonId) } returns sentences
    }

    private fun vocabRepo() = mockk<VocabDrillRepository>(relaxed = true) {
        coEvery { getVerbDrillCards(packId, null) } returns verbs
        coEvery { getVocabWords(packId) } returns vocab
    }

    private fun viewModel(
        progressRepo: ProgressRepository = progress(),
        vocabRepo: VocabDrillRepository = vocabRepo(),
    ) = DailyPracticeViewModel(
        savedStateHandle = SavedStateHandle(mapOf("packId" to packId.value)),
        progressRepository = progressRepo,
        contentRepository = content(),
        vocabDrillRepository = vocabRepo,
        answerValidator = AnswerValidator(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        savedCursor.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_composesTenTasks_firstIsTranslate() {
        val vm = viewModel()

        val state = vm.state.value as DailyViewState.Question
        assertThat(state.total).isEqualTo(10)
        assertThat(state.task).isInstanceOf(DailyTask.TranslateSentence::class.java)
    }

    @Test
    fun vocabFlashcard_knowPath_recordsWordSrs() {
        val vocabRepo = vocabRepo()
        val vm = viewModel(vocabRepo = vocabRepo)
        repeat(5) { i -> vm.submitAnswer("a_$i"); vm.next() }
        val q = vm.state.value as DailyViewState.Question
        assertThat(q.task).isInstanceOf(DailyTask.VocabFlashcard::class.java)

        vm.submitAnswer("x") // «Знаю» — текст не важен, тип задачи решает ветку

        val state = vm.state.value as DailyViewState.Answered
        assertThat(state.correct).isTrue()
        coVerify(exactly = 1) { vocabRepo.recordWordReview(packId, "w_0", true, any()) }
    }

    @Test
    fun vocabReveal_showsTranslationWithoutRecording() {
        val vocabRepo = vocabRepo()
        val vm = viewModel(vocabRepo = vocabRepo)
        repeat(5) { i -> vm.submitAnswer("a_$i"); vm.next() }

        vm.revealVocab()

        val state = vm.state.value as DailyViewState.Answered
        assertThat(state.correctAnswer).isEqualTo("м_0")
        coVerify(exactly = 0) { vocabRepo.recordWordReview(any(), any(), any(), any()) }
    }

    @Test
    fun vocabDontKnow_recordsIncorrectReview() {
        val vocabRepo = vocabRepo()
        val vm = viewModel(vocabRepo = vocabRepo)
        repeat(5) { i -> vm.submitAnswer("a_$i"); vm.next() }

        vm.vocabDontKnow()

        val state = vm.state.value as DailyViewState.Answered
        assertThat(state.correct).isFalse()
        assertThat(state.correctAnswer).isEqualTo("м_0")
        coVerify(exactly = 1) { vocabRepo.recordWordReview(packId, "w_0", false, any()) }
    }

    @Test
    fun fullDay_advancesCursorByConsumedCounts() {
        // Курсор по умолчанию (null → нулевые смещения): 5+3+2 задач.
        val vm = viewModel(progressRepo = progress(cursor = null))
        repeat(10) {
            val q = vm.state.value as DailyViewState.Question
            when (q.task) {
                is DailyTask.TranslateSentence -> vm.submitAnswer("a_$it")
                is DailyTask.ConjugateVerb -> vm.submitAnswer("va_0")
                is DailyTask.VocabFlashcard -> vm.revealVocab()
            }
            vm.next()
        }

        val done = vm.state.value as DailyViewState.Done
        assertThat(done.reviewed).isEqualTo(10)
        val saved = savedCursor.single()
        assertThat(saved.sentenceOffset).isEqualTo(5)
        assertThat(saved.verbOffset).isEqualTo(2)
        assertThat(saved.vocabOffset).isEqualTo(3)
    }

    @Test
    fun blankRoute_showsError() {
        val vm = DailyPracticeViewModel(
            savedStateHandle = SavedStateHandle(mapOf("packId" to "")),
            progressRepository = mockk(relaxed = true),
            contentRepository = mockk(relaxed = true),
            vocabDrillRepository = mockk(relaxed = true),
            answerValidator = AnswerValidator(),
        )

        assertThat(vm.state.value).isInstanceOf(DailyViewState.Error::class.java)
    }
}
