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
import com.alexpo.grammermate.domain.repository.MasteryRepository
import com.alexpo.grammermate.domain.repository.SessionRepository
import com.alexpo.grammermate.v2.core.data.local.TrainingDbFixture
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * ViewModel-регрессия основного UX-пути тренировки (Фаза 0 плана
 * стабилизации 2026-08-26). Два RED-якоря известных P0-дефектов плана
 * (раздел 2): production-путь обходит SessionEngine и создаёт сессию без
 * пула; ошибка персистенции превращается в визуальный успех.
 *
 * RED-тесты помечены [Ignore] до фикса в Фазе 1; зелёный guard фиксирует
 * текущее корректное поведение загрузки.
 */
class TrainingViewModelRegressionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val packId = PackId(TrainingDbFixture.PACK_ID)
    private val lessonId = LessonId(TrainingDbFixture.LESSON_ID)
    private val sessionId = SessionId.forLesson(packId, lessonId)

    private lateinit var sessionRepository: FakeSessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = FakeSessionRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * P0-дефект «сессия сохраняется с пустым pool» (план, раздел 2): VM
     * вызывает `getOrCreateSession` без пула; UI маскирует пустой пул
     * fallback'ом на первую карточку контента. Целевое поведение (Фаза 1,
     * ADR-001): пул строится SessionEngine и персистится непустым для урока
     * с карточками.
     *
     * RED до Фазы 1.
     */
    @Ignore("RED-якорь P0 «пустой пул» — REFACTORING_PLAN_2026-08-26.md Фаза 1")
    @Test
    fun init_lessonWithCards_persistsSessionWithNonEmptyPool() {
        val vm = trainingViewModel()

        // Контент урока загружен и показан (текущее поведение — корректно).
        assertThat(vm.state.value.currentCard).isNotNull()
        assertThat(vm.state.value.totalCards).isEqualTo(TrainingDbFixture.CARD_IDS.size)

        // Целевое поведение: сессия в персистенции имеет непустой пул.
        val persisted = sessionRepository.saved[sessionId.value]
        assertThat(persisted).isNotNull()
        assertThat(persisted!!.poolCardIds.map { it.value })
            .containsExactlyElementsIn(TrainingDbFixture.CARD_IDS)
            .inOrder()
    }

    /**
     * P0-дефект «persistence failure превращается в визуальный успех» (план,
     * раздел 2): `runCatching` без `onFailure` проглатывает исключение, state
     * получает `lastResult` и `answeredCards+1`. Целевое поведение (правило
     * 3.1.5 плана): success-UI публикуется только после успешного commit.
     *
     * RED до Фазы 1.
     */
    @Ignore("RED-якорь P0 «успех при упавшем persist» — план Фаза 1")
    @Test
    fun submitAnswer_persistenceFailure_doesNotShowSuccess() {
        val vm = trainingViewModel()
        sessionRepository.failUpdateProgress = true

        vm.onSubmitAnswer("answer 0")

        assertThat(vm.state.value.lastResult).isNull()
        assertThat(vm.state.value.error).isNotNull()
    }

    /**
     * Зелёный guard: happy-path загрузки — карточки урока отображаются,
     * ошибка отсутствует.
     */
    @Test
    fun init_lessonWithCards_showsFirstCardWithoutError() {
        val vm = trainingViewModel()

        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.currentCard?.id?.value)
            .isEqualTo(TrainingDbFixture.CARD_IDS.first())
    }

    // ── Хелперы ──────────────────────────────────────────────────────────────

    private fun trainingViewModel(): TrainingViewModel = TrainingViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf("packId" to packId.value, "lessonId" to lessonId.value)
        ),
        sessionRepository = sessionRepository,
        contentRepository = contentRepositoryWithLessonCards(),
        masteryRepository = mockk<MasteryRepository>(relaxed = true),
    )

    private fun contentRepositoryWithLessonCards(): ContentRepository {
        val cards = TrainingDbFixture.CARD_IDS.mapIndexed { index, cardId ->
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
        return mockk(relaxed = true) {
            io.mockk.coEvery { getCards(lessonId) } returns cards
        }
    }
}

/**
 * Fake доменного порта сессий: stateful in-memory карта снимков.
 * Повторяет контракт Room-реализации для happy-path (пул — только явный).
 */
private class FakeSessionRepository : SessionRepository {

    val saved = linkedMapOf<String, SessionSnapshot>()
    var failUpdateProgress = false

    private fun snapshot(sessionId: SessionId): SessionSnapshot =
        checkNotNull(saved[sessionId.value]) { "session not created: ${sessionId.value}" }

    override suspend fun getOrCreateSession(
        sessionId: SessionId,
        packId: PackId,
        lessonId: LessonId?,
        mode: TrainingMode,
        poolCardIds: List<CardId>?,
        selectedTense: String?,
        selectedGroup: String?,
        selectedPerson: String?,
    ): SessionSnapshot {
        // ЗАМЕТКА О РАСХОЖДЕНИИ: Room-impl резюмит только ACTIVE-сессии
        // (getActiveSession); этот fake возвращает любую сохранённую. При
        // снятии @Ignore в Фазе 2 («уравнять recovery-семантики fake/Room»)
        // свести к контрактy Room.
        saved[sessionId.value]?.let { return it }
        val now = System.currentTimeMillis()
        val fresh = SessionSnapshot(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = mode,
            currentCardId = poolCardIds?.firstOrNull(),
            cursorIndex = 0,
            status = SessionStatus.ACTIVE,
            state = SessionState.ACTIVE,
            poolCardIds = poolCardIds ?: emptyList(),
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
        if (failUpdateProgress) throw IllegalStateException("simulated Room failure")
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
