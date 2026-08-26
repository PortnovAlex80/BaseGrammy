package com.alexpo.grammermate.v2.feature.training

import androidx.lifecycle.SavedStateHandle
import com.alexpo.grammermate.domain.model.BadSentence
import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.Pack
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

    /** Записанные флаги «плохое предложение» (Фаза 3: persist Flag). */
    private val flaggedSentences = mutableListOf<BadSentence>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = FakeSessionRepository()
        flaggedSentences.clear()
        userContentRepository = mockk {
            coEvery { getHiddenCardIds() } returns emptySet()
            coEvery { flagBadSentence(any()) } answers {
                val entry: BadSentence = firstArg()
                flaggedSentences += entry
            }
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

        val persisted = sessionRepository.store[sessionId.value]
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
        val persisted = sessionRepository.store[sessionId.value]!!
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

        val persisted = sessionRepository.store[sessionId.value]!!
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

        val persisted = sessionRepository.store[sessionId.value]!!
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

        val persisted = sessionRepository.store[sessionId.value]!!
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
        val persisted = sessionRepository.store[sessionId.value]!!
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

        assertThat(sessionRepository.store[sessionId.value]!!.status)
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
        assertThat(sessionRepository.store[sessionId.value]!!.poolCardIds)
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

    /**
     * Фаза 3: «Persist Report/Flag» — flagCard пишет BadSentence в
     * UserContentRepository с контекстом карточки и НЕ меняет сессию.
     */
    @Test
    fun flagCard_persistsBadSentence_withoutTouchingSession() {
        val vm = trainingViewModel()
        vm.onDraftChange("answer 0")
        vm.submitAnswer() // теперь Feedback
        val persistedBefore = sessionRepository.store[sessionId.value]!!

        vm.flagCard()

        assertThat(flaggedSentences).hasSize(1)
        val flag = flaggedSentences.single()
        assertThat(flag.cardId.value).isEqualTo(TrainingDbFixture.CARD_IDS.first())
        assertThat(flag.packId).isEqualTo(packId)
        assertThat(flag.languageId.value).isEqualTo("it")
        assertThat(flag.mode).isEqualTo("LESSON")
        // Сессия не тронута: ни ревизия, ни счётчики (bad ≠ hide).
        val persistedAfter = sessionRepository.store[sessionId.value]!!
        assertThat(persistedAfter).isEqualTo(persistedBefore)
    }

    /** Фаза 3: невалидный маршрут (пустые required ID) — явная Error, не пустая сессия. */
    @Test
    fun init_blankRouteArgs_showsErrorWithoutSession() {
        val vm = trainingViewModel(
            handle = SavedStateHandle(mapOf("packId" to "", "lessonId" to "")),
        )

        assertThat(vm.state.value).isInstanceOf(TrainingViewState.Error::class.java)
        assertThat(sessionRepository.store).isEmpty()
        // Retry на невалидном маршруте — no-op (маршрута нет, повторять нечего).
        vm.reload()
        assertThat(vm.state.value).isInstanceOf(TrainingViewState.Error::class.java)
    }

    // ── Фаза 3 slice 2: ResumeGate (recovered-session экран) ─────────────────

    /**
     * Повторный вход в незавершённый урок (есть ответы) — ЯВНЫЙ выбор
     * (ResumeGate), а не молчаливый resume (план §3.1.4: recovery — явный
     * результат).
     */
    @Test
    fun reenter_midLesson_showsResumeGate() {
        val engine = sessionEngine()
        kotlinx.coroutines.runBlocking {
            engine.startLessonSession(packId, lessonId, sessionSize = 10)
            engine.submitAnswer(
                sessionId, CardId(TrainingDbFixture.CARD_IDS.first()),
                isCorrect = true, inputMode = InputMode.KEYBOARD,
            )
        }

        val vm = trainingViewModel(engine = engine)

        val state = vm.state.value as TrainingViewState.ResumeGate
        assertThat(state.answeredCards).isEqualTo(1)
        assertThat(state.totalCards).isEqualTo(TrainingDbFixture.CARD_IDS.size)
        assertThat(state.correctCount).isEqualTo(1)
        assertThat(state.incorrectCount).isEqualTo(0)
    }

    /** «Продолжить» из гейта — с сохранённой карточки и счётчиков. */
    @Test
    fun resumeFromGate_continuesFromSavedCard() {
        val engine = sessionEngine()
        kotlinx.coroutines.runBlocking {
            engine.startLessonSession(packId, lessonId, sessionSize = 10)
            engine.submitAnswer(
                sessionId, CardId(TrainingDbFixture.CARD_IDS.first()),
                isCorrect = true, inputMode = InputMode.KEYBOARD,
            )
            engine.nextCard(sessionId)
        }
        val vm = trainingViewModel(engine = engine)
        assertThat(vm.state.value).isInstanceOf(TrainingViewState.ResumeGate::class.java)

        vm.resumeFromGate()

        val state = vm.state.value as TrainingViewState.Active
        assertThat(state.card.id.value).isEqualTo(TrainingDbFixture.CARD_IDS[1])
        assertThat(state.answeredCards).isEqualTo(1)
    }

    /**
     * «Начать заново» из гейта: сбрасывается ТОЛЬКО контекст сессии — счётчики
     * ответов/shown обнулены, пул тот же; mastery не участвует (зона
     * MasteryRepository, см. SessionEngineRestartTest).
     */
    @Test
    fun restartFromGate_resetsSessionContextOnly() {
        val engine = sessionEngine()
        kotlinx.coroutines.runBlocking {
            engine.startLessonSession(packId, lessonId, sessionSize = 10)
            engine.submitAnswer(
                sessionId, CardId(TrainingDbFixture.CARD_IDS.first()),
                isCorrect = true, inputMode = InputMode.KEYBOARD,
            )
        }
        val vm = trainingViewModel(engine = engine)
        assertThat(vm.state.value).isInstanceOf(TrainingViewState.ResumeGate::class.java)

        vm.restartFromGate()

        val state = vm.state.value as TrainingViewState.Active
        assertThat(state.card.id.value).isEqualTo(TrainingDbFixture.CARD_IDS.first())
        assertThat(state.answeredCards).isEqualTo(0)
        val persisted = sessionRepository.store[sessionId.value]!!
        assertThat(persisted.correctCount).isEqualTo(0)
        assertThat(persisted.shownCardIds).isEmpty()
        assertThat(persisted.poolCardIds.map { it.value })
            .containsExactlyElementsIn(TrainingDbFixture.CARD_IDS)
            .inOrder()
    }

    /** Гейт НЕ срабатывает без ответов: навигационный re-enter = тихий resume. */
    @Test
    fun reenter_lessonWithoutAnswers_resumesSilently() {
        val engine = sessionEngine()
        kotlinx.coroutines.runBlocking {
            engine.startLessonSession(packId, lessonId, sessionSize = 10)
            engine.nextCard(sessionId) // переход без ответа
        }

        val vm = trainingViewModel(engine = engine)

        val state = vm.state.value as TrainingViewState.Active
        assertThat(state.card.id.value).isEqualTo(TrainingDbFixture.CARD_IDS[1])
        assertThat(state.answeredCards).isEqualTo(0)
    }

    // ── Фаза 4 срез 1: mixed review из Completed ─────────────────────────────

    /**
     * «Повторить вперемешку»: пул пересобран чередованием половин
     * ([c0, c2, c1] для трёх карт фикстуры), счётчики прохода обнулены,
     * persisted mode = ALL_MIXED.
     */
    @Test
    fun repeatMixedFromCompleted_rebuildsInterleavedPool() {
        val vm = trainingViewModel()
        TrainingDbFixture.CARD_IDS.forEachIndexed { index, _ ->
            vm.onDraftChange("answer $index")
            vm.submitAnswer()
            vm.next()
        }
        assertThat(vm.state.value).isInstanceOf(TrainingViewState.Completed::class.java)

        vm.repeatMixedFromCompleted()

        val state = vm.state.value as TrainingViewState.Active
        assertThat(state.card.id.value).isEqualTo(TrainingDbFixture.CARD_IDS.first())
        assertThat(state.answeredCards).isEqualTo(0)
        val persisted = sessionRepository.store[sessionId.value]!!
        assertThat(persisted.mode).isEqualTo(TrainingMode.ALL_MIXED)
        assertThat(persisted.poolCardIds.map { it.value })
            .containsExactly(
                TrainingDbFixture.CARD_IDS[0],
                TrainingDbFixture.CARD_IDS[2],
                TrainingDbFixture.CARD_IDS[1],
            )
            .inOrder()
    }

    @Test
    fun repeatMixed_onlyAvailableInCompleted() {
        val vm = trainingViewModel() // Active

        vm.repeatMixedFromCompleted()

        // Команда вне фазы Completed — no-op (сессия не тронута).
        assertThat(vm.state.value).isInstanceOf(TrainingViewState.Active::class.java)
        assertThat(sessionRepository.store[sessionId.value]!!.mode)
            .isEqualTo(TrainingMode.LESSON)
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
        userContentRepository = userContentRepository,
        answerValidator = AnswerValidator(),
    )

    private fun sessionEngine(): SessionEngine =
        SessionEngine(sessionRepository, contentRepository(), userContentRepository)

    private fun contentRepository(): ContentRepository = mockk(relaxed = true) {
        coEvery { getCards(lessonId) } returns lessonCards()
        coEvery { getPack(packId) } returns Pack(
            id = packId,
            languageId = LanguageId("it"),
            displayName = "Fixture Pack",
            version = "1",
            importedAtMs = 0L,
        )
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
