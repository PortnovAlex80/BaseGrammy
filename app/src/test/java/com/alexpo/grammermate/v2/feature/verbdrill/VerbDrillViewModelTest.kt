package com.alexpo.grammermate.v2.feature.verbdrill

import androidx.lifecycle.SavedStateHandle
import com.alexpo.grammermate.domain.model.AppConfig
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.VerbDrillCard
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.SettingsRepository
import com.alexpo.grammermate.domain.repository.UserContentRepository
import com.alexpo.grammermate.domain.session.SessionEngine
import com.alexpo.grammermate.domain.validation.AnswerValidator
import com.alexpo.grammermate.v2.core.data.repository.FakeSessionRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * ViewModel verb drill (Фаза 4 срез 2): пул строится движком, submit публикует
 * Feedback только после commit, полный проход → Completed.
 */
class VerbDrillViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val packId = PackId("FIXTURE_PACK")

    private lateinit var sessionRepository: FakeSessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = FakeSessionRepository(clock = { 1L })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun cards() = listOf(
        VerbDrillCard("v1", "я говорю", "parlo", verb = "parlare", tense = "present", group = "are", person = "Io", rank = 1),
        VerbDrillCard("v2", "ты говоришь", "parli", verb = "parlare", tense = "present", group = "are", person = "Tu", rank = 2),
        VerbDrillCard("v3", "он говорит", "parla", verb = "parlare", tense = "present", group = "are", person = "Lui", rank = 3),
    )

    private fun viewModel(sessionSize: Int = 10): VerbDrillViewModel {
        val configuredSessionSize = sessionSize
        val content = mockk<ContentRepository>(relaxed = true) {
            coEvery { getVerbDrillCards(packId, null, null, null) } returns cards()
        }
        val userContent = mockk<UserContentRepository>(relaxed = true) {
            coEvery { getHiddenCardIds(any()) } returns emptySet()
        }
        return VerbDrillViewModel(
            savedStateHandle = SavedStateHandle(mapOf("packId" to packId.value)),
            sessionEngine = SessionEngine(sessionRepository, content, userContent, clock = { 1L }),
            contentRepository = content,
            settingsRepository = mockk<SettingsRepository>(relaxed = true) {
                coEvery { getAppConfig() } returns AppConfig(sessionSize = configuredSessionSize)
            },
            answerValidator = AnswerValidator(),
        )
    }

    @Test
    fun init_buildsRankedPool_andShowsFirstPrompt() {
        val vm = viewModel()

        val state = vm.state.value as VerbDrillViewState.Active
        assertThat(state.card).isEqualTo("v1")
        assertThat(state.promptRu).isEqualTo("я говорю")
        assertThat(state.totalCards).isEqualTo(3)
    }

    @Test
    fun init_usesConfiguredSessionSizeForNewSession() {
        viewModel(sessionSize = 3)

        val session = sessionRepository.store.getValue(SessionId.forVerbDrill(packId).value)
        assertThat(session.sessionSize).isEqualTo(3)
    }

    @Test
    fun submit_correctAnswer_publishesFeedbackAfterCommit() {
        val vm = viewModel()

        vm.submitAnswer("parlo")

        val state = vm.state.value as VerbDrillViewState.Feedback
        assertThat(state.correct).isTrue()
        assertThat(state.correctAnswer).isNull()
        assertThat(state.answeredCards).isEqualTo(1)
    }

    @Test
    fun submit_wrongAnswer_showsCorrectForm() {
        val vm = viewModel()

        vm.submitAnswer("parliamo")

        val state = vm.state.value as VerbDrillViewState.Feedback
        assertThat(state.correct).isFalse()
        assertThat(state.correctAnswer).isEqualTo("parlo")
    }

    @Test
    fun fullPass_completesSession() {
        val vm = viewModel()
        listOf("parlo", "parli", "parla").forEach {
            vm.submitAnswer(it)
            vm.next()
        }

        val state = vm.state.value as VerbDrillViewState.Completed
        assertThat(state.correctCount).isEqualTo(3)
        assertThat(state.totalCards).isEqualTo(3)
    }

    @Test
    fun blankRoute_showsErrorWithoutSession() {
        val content = mockk<ContentRepository>(relaxed = true)
        val vm = VerbDrillViewModel(
            savedStateHandle = SavedStateHandle(mapOf("packId" to "")),
            sessionEngine = SessionEngine(sessionRepository, content, mockk(relaxed = true)),
            contentRepository = content,
            settingsRepository = mockk(relaxed = true),
            answerValidator = AnswerValidator(),
        )

        assertThat(vm.state.value).isInstanceOf(VerbDrillViewState.Error::class.java)
        assertThat(sessionRepository.store).isEmpty()
    }
}
