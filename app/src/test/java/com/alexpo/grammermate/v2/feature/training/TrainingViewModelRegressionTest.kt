package com.alexpo.grammermate.v2.feature.training

import androidx.lifecycle.SavedStateHandle
import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionSnapshot
import com.alexpo.grammermate.domain.model.SessionState
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.model.TrainingMode
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.SessionRepository
import com.alexpo.grammermate.domain.repository.UserContentRepository
import com.alexpo.grammermate.domain.session.SessionEngine
import com.alexpo.grammermate.domain.validation.AnswerValidator
import com.alexpo.grammermate.v2.core.data.local.TrainingDbFixture
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
 * ViewModel-регрессия основного UX-пути тренировки (Фаза 0→1 плана
 * стабилизации 2026-08-26).
 *
 * Два бывших RED-якоря P0-дефектов (раздел 2 плана) сняты с `@Ignore` тем же
 * changeset'ом, что закрыл дефекты (конвенция Фазы 0):
 *  - «сессия сохраняется с пустым pool» → VM идёт через SessionEngine,
 *    который строит и передаёт реальный пул;
 *  - «persistence failure превращается в визуальный успех» → FSM публикует
 *    Feedback только после успешного commit.
 *
 * Плюс переходы FSM golden journey: submit→feedback, next→active,
 * next-after-last→completed, skip, resume по PK, draft-restore, double-submit.
 */
class TrainingViewModelRegressionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val packId = PackId(TrainingDbFixture.PACK_ID)
    private val lessonId = LessonId(TrainingDbFixture.LESSON_ID)
    private val sessionId = SessionId.forLesson(packId, lessonId)

    private lateinit var sessionRepository: FakeSessionRepository
    private lateinit var userContentRepository: UserContentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = FakeSessionRepository()
        userContentRepository = mockk {
            coEvery { getHiddenCardIds() } returns emptySet()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Снятые RED-якоря Фазы 1 ───────────────────────────────────────────────

    /**
     * P0-дефект «сессия сохраняется с пустым pool» — закрыт Фазой 1 (ADR-001):
     * VM стартует сессию через [SessionEngine.startLessonSession], который
     * строит пул (карты урока минус скрытые, нарезка под-уроков) и передаёт
     * его в персистенцию. Пул непустой и совпадает с контентом урока по порядку.
     */
    @Test
    fun init_lessonWithCards_persistsSessionWithNonEmptyPool() {
        val vm = trainingViewModel()

        val state = vm.state.value
        assertThat(state).isInstanceOf(TrainingViewState.Active::class.java)
        assertThat((state as TrainingViewState.Active).card.id.value)
            .isEqualTo(TrainingDbFixture.CARD_IDS.first())

        val persisted = sessionRepository.saved[sessionId.value]
        assertThat(persisted).isNotNull()
        assertThat(persisted!!.poolCardIds.map { it.value })
            .containsExactlyElementsIn(TrainingDbFixture.CARD_IDS)
            .inOrder()
    }

    /**
     * P0-дефект «persistence failure превращается в визуальный успех» — закрыт
     * Фазой 1 (правило плана §3.1.5): при падении commit состояние — Error,
     * Feedback недостижим, счётчики в персистенции не изменены.
     */
    @Test
    fun submitAnswer_persistenceFailure_doesNotShowSuccess() {
        val vm = trainingViewModel()
        vm.onDraftChange("answer 0")
        sessionRepository.failSaveSession = true

        vm.submitAnswer()

        assertThat(vm.state.value).isInstanceOf(TrainingViewState.Error::class.java)
        val persisted = sessionRepository.saved[sessionId.value]!!
        assertThat(persisted.correctCount).isEqualTo(0)
        assertThat(persisted.shownCardIds).isEmpty()
    }

    // ── Переходы FSM golden journey ───────────────────────────────────────────

    @Test
    fun init_lessonWithCards_showsFirstCardWithoutError() {
        val vm = trainingViewModel()

        assertThat(vm.state.value).isInstanceOf(TrainingViewState.Active::class.java)
    }

    @Test
    fun submitAnswer_correctAnswer_publishesFeedbackAfterCommit() {
        val vm = trainingViewModel()
        vm.onDraftChange("answer 0")

        vm.submitAnswer()

        val state = vm.state.value as TrainingViewState.Feedback
        assertThat(state.result.correct).isTrue()
        assertThat(state.isLastCard).isFalse()
        assertThat(state.correctAnswer).isNull()

        val persisted = sessionRepository.saved[sessionId.value]!!
        assertThat(persisted.correctCount).isEqualTo(1)
        assertThat(persisted.shownCardIds.map { it.value })
            .containsExactly(TrainingDbFixture.CARD_IDS.first())
    }

    @Test
    fun submitAnswer_wrongAnswer_showsCorrectAnswerAndCountsIncorrect() {
        val vm = trainingViewModel()
        vm.onDraftChange("nope")

        vm.submitAnswer()

        val state = vm.state.value as TrainingViewState.Feedback
        assertThat(state.result.correct).isFalse()
        assertThat(state.correctAnswer).isEqualTo("answer 0")

        val persisted = sessionRepository.saved[sessionId.value]!!
        assertThat(persisted.incorrectCount).isEqualTo(1)
    }

    @Test
    fun submitAnswer_normalization_matchesNormalizedAcceptance() {
        val vm = trainingViewModel()
        // "ANSWER 0" нормализуется (trim/lowercase) и принимается.
        vm.onDraftChange("  ANSWER 0  ")

        vm.submitAnswer()

        assertThat((vm.state.value as TrainingViewState.Feedback).result.correct).isTrue()
    }

    @Test
    fun submitAnswer_whileChecking_ignoredExactlyOnce() {
        val vm = trainingViewModel()
        vm.onDraftChange("answer 0")

        vm.submitAnswer()
        // Второй вызов уже не в Active — команда игнорируется.
        vm.submitAnswer()

        val persisted = sessionRepository.saved[sessionId.value]!!
        assertThat(persisted.correctCount).isEqualTo(1)
    }

    @Test
    fun next_afterFeedback_advancesToNextCard() {
        val vm = trainingViewModel()
        vm.onDraftChange("answer 0")
        vm.submitAnswer()

        vm.next()

        val state = vm.state.value as TrainingViewState.Active
        assertThat(state.card.id.value).isEqualTo(TrainingDbFixture.CARD_IDS[1])
        assertThat(state.draft).isEmpty()
    }

    @Test
    fun skip_doesNotMarkShownAndAdvances() {
        val vm = trainingViewModel()

        vm.skip()

        val state = vm.state.value as TrainingViewState.Active
        assertThat(state.card.id.value).isEqualTo(TrainingDbFixture.CARD_IDS[1])
        val persisted = sessionRepository.saved[sessionId.value]!!
        assertThat(persisted.shownCardIds).isEmpty()
        assertThat(persisted.correctCount + persisted.incorrectCount).isEqualTo(0)
    }

    @Test
    fun next_afterLastCard_completesSession() {
        val vm = trainingViewModel()
        // Полный проход: 3 карты, на каждой — ответ и advance.
        TrainingDbFixture.CARD_IDS.forEachIndexed { index, _ ->
            vm.onDraftChange("answer $index")
            vm.submitAnswer()
            vm.next()
        }

        val state = vm.state.value as TrainingViewState.Completed
        assertThat(state.correctCount).isEqualTo(TrainingDbFixture.CARD_IDS.size)
        assertThat(state.totalCards).isEqualTo(TrainingDbFixture.CARD_IDS.size)

        assertThat(sessionRepository.saved[sessionId.value]!!.status)
            .isEqualTo(SessionStatus.COMPLETED)
    }

    @Test
    fun reenter_afterCompletion_startsFreshSessionSamePk() {
        val vm = trainingViewModel()
        TrainingDbFixture.CARD_IDS.forEachIndexed { index, _ ->
            vm.onDraftChange("answer $index")
            vm.submitAnswer()
            vm.next()
        }
        assertThat(vm.state.value).isInstanceOf(TrainingViewState.Completed::class.java)

        // Повторный вход: COMPLETED-сессия перезапускается свежим проходом.
        val reentered = trainingViewModel()
        val state = reentered.state.value as TrainingViewState.Active
        assertThat(state.card.id.value).isEqualTo(TrainingDbFixture.CARD_IDS.first())
        assertThat(state.answeredCards).isEqualTo(0)
    }

    @Test
    fun init_activeSessionExists_resumesSameCardAndPk() {
        val engine = sessionEngine()
        kotlinx.coroutines.runBlocking {
            engine.startLessonSession(packId, lessonId, sessionSize = 10)
            engine.nextCard(sessionId) // курсор на второй карте
        }

        val vm = trainingViewModel(engine = engine)

        val state = vm.state.value as TrainingViewState.Active
        assertThat(state.card.id.value).isEqualTo(TrainingDbFixture.CARD_IDS[1])
        assertThat(sessionRepository.saved[sessionId.value]!!.poolCardIds)
            .containsExactlyElementsIn(TrainingDbFixture.CARD_IDS.map(::CardId))
            .inOrder()
    }

    @Test
    fun draft_restoredFromSavedStateForSameCard() {
        val handle = SavedStateHandle(
            mapOf("packId" to packId.value, "lessonId" to lessonId.value)
        )
        val vm = trainingViewModel(handle = handle)
        vm.onDraftChange("частичный ответ")

        // Process death: новый VM с тем же SavedStateHandle — draft восстановлен.
        val restored = trainingViewModel(handle = handle)
        val state = restored.state.value as TrainingViewState.Active
        assertThat(state.draft).isEqualTo("частичный ответ")
    }

    @Test
    fun allCardsHidden_emptyPoolShowsEmptyStateWithoutFallback() {
        val hiddenRepository = mockk<UserContentRepository> {
            coEvery { getHiddenCardIds() } returns TrainingDbFixture.CARD_IDS.map(::CardId).toSet()
        }
        val engine = SessionEngine(sessionRepository, contentRepository(), hiddenRepository)
        val vm = trainingViewModel(engine = engine)

        assertThat(vm.state.value).isInstanceOf(TrainingViewState.Empty::class.java)
    }

    // ── Хелперы ──────────────────────────────────────────────────────────────

    private fun trainingViewModel(
        handle: SavedStateHandle = SavedStateHandle(
            mapOf("packId" to packId.value, "lessonId" to lessonId.value)
        ),
        engine: SessionEngine = sessionEngine(),
    ): TrainingViewModel = TrainingViewModel(
        savedStateHandle = handle,
        sessionEngine = engine,
        contentRepository = contentRepository(),
        answerValidator = AnswerValidator(),
    )

    private fun sessionEngine(): SessionEngine =
        SessionEngine(sessionRepository, contentRepository(), userContentRepository)

    private fun contentRepository(): ContentRepository = mockk(relaxed = true) {
        coEvery { getCards(lessonId) } returns lessonCards()
    }

    private fun lessonCards(): List<Card> =
        TrainingDbFixture.CARD_IDS.mapIndexed { index, cardId ->
            Card(
                id = CardId(cardId),
                packId = packId,
                lessonId = lessonId,
                ord = index,
                type = CardType.SENTENCE,
                promptRu = "Промпт $index",
                acceptedAnswers = listOf("answer $index"),
                tense = null,
                verb = null,
                verbGroup = null,
                person = null,
                frequencyRank = null,
            )
        }
}

/**
 * Fake доменного порта сессий: stateful in-memory карта снимков.
 * Повторяет контракт Room-реализации для happy-path (пул — только явный).
 *
 * Паритет с Room-impl по getOrCreate (resume только ACTIVE-сессий) закрыт в
 * Фазе 1; расхождение loadSession-vs-getActiveSession recovery остаётся до Фазы 2.
 */
private class FakeSessionRepository : SessionRepository {

    val saved = linkedMapOf<String, SessionSnapshot>()
    var failSaveSession = false

    private fun snapshot(sessionId: SessionId): SessionSnapshot =
        checkNotNull(saved[sessionId.value]) { "session not created: ${sessionId.value}" }

    override suspend fun getOrCreateSession(
        sessionId: SessionId,
        packId: PackId,
        lessonId: LessonId?,
        mode: TrainingMode,
        poolCardIds: List<CardId>,
        selectedTense: String?,
        selectedGroup: String?,
        selectedPerson: String?,
    ): SessionSnapshot {
        // Как Room-impl (getActiveSession): resume только ACTIVE-сессии;
        // COMPLETED → свежая сессия с тем же PK (MODE_MATRIX → Normal lesson).
        saved[sessionId.value]?.takeIf { it.status == SessionStatus.ACTIVE }?.let { return it }
        val now = System.currentTimeMillis()
        val fresh = SessionSnapshot(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = mode,
            currentCardId = poolCardIds.firstOrNull(),
            cursorIndex = 0,
            status = SessionStatus.ACTIVE,
            state = SessionState.ACTIVE,
            poolCardIds = poolCardIds,
            shownCardIds = emptySet(),
            correctCount = 0,
            incorrectCount = 0,
            hintCount = 0,
            completedSubLessonCount = 0,
            selectedTense = selectedTense,
            selectedGroup = selectedGroup,
            selectedPerson = selectedPerson,
            startedAtMs = now,
            updatedAtMs = now,
        )
        saved[sessionId.value] = fresh
        return fresh
    }

    override suspend fun loadSession(sessionId: SessionId): SessionSnapshot? =
        saved[sessionId.value]

    override suspend fun saveSession(snapshot: SessionSnapshot) {
        if (failSaveSession) throw IllegalStateException("simulated Room failure")
        saved[snapshot.sessionId.value] = snapshot
    }

    override suspend fun completeSession(sessionId: SessionId) {
        saved[sessionId.value] = snapshot(sessionId).copy(status = SessionStatus.COMPLETED)
    }

    override suspend fun setCurrentCard(sessionId: SessionId, cardId: CardId) {
        saved[sessionId.value] = snapshot(sessionId).copy(currentCardId = cardId)
    }

    override suspend fun markCardShown(sessionId: SessionId, cardId: CardId) {
        val current = snapshot(sessionId)
        saved[sessionId.value] =
            current.copy(shownCardIds = current.shownCardIds + cardId)
    }

    override suspend fun updateProgress(
        sessionId: SessionId,
        correct: Int,
        incorrect: Int,
        hint: Int,
    ) {
        if (failSaveSession) throw IllegalStateException("simulated Room failure")
        saved[sessionId.value] = snapshot(sessionId).copy(
            correctCount = correct,
            incorrectCount = incorrect,
            hintCount = hint,
        )
    }

    override suspend fun deleteSession(sessionId: SessionId) {
        saved.remove(sessionId.value)
    }
}
