package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * ★ Критический регрессионный набор — именно баг `card_15`.
 *
 * В v1 при SESSION_RESUME пул карточек пересобирался, а текущая
 * идентифицировалась по индексу массива → `card_15` бесследно терялась
 * (не скрыта, не отвечена, но больше не показывалась). Урок зависал.
 *
 * В v2 текущая карточка идентифицируется по `currentCardId` (PK), а пул
 * хранится целиком в снимке. Эти тесты доказывают, что идентичность
 * сохраняется после любой пересборки/возобновления — НИКОГДА молчаливой
 * подмены карты.
 */
class SessionEngineResumeRegressionTest {

    private val packId = PackId("pack")
    private val lessonId = LessonId("lesson")

    /** N карт card_0..card_(n-1) для урока. */
    private fun lessonCards(n: Int): List<Card> = (0 until n).map { idx ->
        Card(
            id = CardId("card_$idx"),
            packId = packId,
            lessonId = lessonId,
            ord = idx,
            type = CardType.SENTENCE,
            promptRu = "prompt $idx",
            acceptedAnswers = listOf("answer $idx"),
            tense = null, verb = null, verbGroup = null, person = null,
            frequencyRank = idx,
        )
    }

    /** Фабрика engine на трёх fake-репозиториях с детерминированным clock. */
    private fun buildEngine(
        content: FakeContentRepository,
        userContent: FakeUserContentRepository,
        sessionRepo: FakeSessionRepository = FakeSessionRepository(),
    ): Triple<SessionEngine, FakeSessionRepository, FakeUserContentRepository> =
        Triple(
            SessionEngine(
                sessionRepository = sessionRepo,
                contentRepository = content,
                userContentRepository = userContent,
                clock = { 1_700_000_000_000L },
            ),
            sessionRepo,
            userContent,
        )

    // ───────────────────────────────────────────────────────────────────────
    //  ★ ГЛАВНЫЙ ТЕСТ: card_15 не теряется после пересборки пула при resume
    // ───────────────────────────────────────────────────────────────────────
    @Test
    fun `resume preserves currentCardId after pool rebuild`() = runTest {
        // 1. Урок из 20 карт, новая сессия с пулом = весь урок (sessionSize=20).
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(20)) }
        val userContent = FakeUserContentRepository()
        val (engine, sessionRepo, _) = buildEngine(content, userContent)

        val started = engine.startLessonSession(packId, lessonId, sessionSize = 20)
        assertThat(started.poolCardIds).hasSize(20)
        assertThat(started.currentCardId).isEqualTo(CardId("card_0"))

        // 2. Доводим текущую карту ДО card_15 (как было в баге): nextCard × 15.
        var current = started
        repeat(15) { current = engine.nextCard(current.sessionId) }
        assertThat(current.currentCardId).isEqualTo(CardId("card_15"))
        val sessionId = current.sessionId

        // 3. Скрыть ДРУГУЮ карту (card_7) — пул меняется 20→19, но card_15 жива.
        engine.hideCard(sessionId, CardId("card_7"))

        // 4. Resume: имитируем перезапуск приложения — перечитываем снимок из БД.
        val resumed = engine.resumeSession(sessionId)

        // ★ Доказательство: card_15 на месте, не потеряна, не подменена молча.
        assertThat(resumed).isNotNull()
        assertThat(resumed!!.currentCardId).isEqualTo(CardId("card_15"))
        // card_7 исключена из пула, card_15 осталась.
        assertThat(resumed.poolCardIds).doesNotContain(CardId("card_7"))
        assertThat(resumed.poolCardIds).contains(CardId("card_15"))
        // Инвариант: currentCardId всегда внутри пула.
        assertThat(resumed.poolCardIds).contains(resumed.currentCardId)

        // Снимок в репозитории и resume согласованы (одна транзакция).
        val stored = sessionRepo.loadSession(sessionId)
        assertThat(stored?.currentCardId).isEqualTo(CardId("card_15"))
    }

    // ───────────────────────────────────────────────────────────────────────
    //  Инвариант: currentCardId ВСЕГДА ∈ poolCardIds (или null при пустом пуле)
    // ───────────────────────────────────────────────────────────────────────
    @Test
    fun `currentCardId always within pool invariant`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(10)) }
        val (engine, _, _) = buildEngine(content, FakeUserContentRepository())

        val snapshot = engine.startLessonSession(packId, lessonId, sessionSize = 10)
        val sessionId = snapshot.sessionId

        // После каждой операции проверяем инвариант.
        fun assertInvariant(s: com.alexpo.grammermate.domain.model.SessionSnapshot) {
            assertTrue(s.currentCardId == null || s.currentCardId in s.poolCardIds)
        }

        assertInvariant(snapshot)
        assertInvariant(engine.nextCard(sessionId))
        assertInvariant(engine.nextCard(sessionId))
        assertInvariant(engine.previousCard(sessionId))
        assertInvariant(engine.flagCard(sessionId, CardId("card_3")))
        assertInvariant(engine.hideCard(sessionId, CardId("card_0")))
        assertInvariant(engine.hideCard(sessionId, engine.resumeSession(sessionId)!!.currentCardId!!))
    }

    // ───────────────────────────────────────────────────────────────────────
    //  Скрытие ТЕКУЩЕЙ карты → currentCardId ЯВНО переходит на следующую
    // ───────────────────────────────────────────────────────────────────────
    @Test
    fun `hide current card moves to next explicitly`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(5)) }
        val (engine, _, _) = buildEngine(content, FakeUserContentRepository())

        val snapshot = engine.startLessonSession(packId, lessonId, sessionSize = 5)
        val sessionId = snapshot.sessionId
        // current = card_2.
        engine.nextCard(sessionId)
        engine.nextCard(sessionId)
        assertThat(engine.resumeSession(sessionId)!!.currentCardId).isEqualTo(CardId("card_2"))

        // Скрываем ТЕКУЩУЮ (card_2) → должны ЯВНО перейти на card_3, не на null.
        val afterHide = engine.hideCard(sessionId, CardId("card_2"))

        assertThat(afterHide.poolCardIds).doesNotContain(CardId("card_2"))
        assertThat(afterHide.currentCardId).isEqualTo(CardId("card_3"))
        assertThat(afterHide.currentCardId in afterHide.poolCardIds).isTrue()
    }

    @Test
    fun `hide current card at tail wraps to first available explicitly`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(3)) }
        val (engine, _, _) = buildEngine(content, FakeUserContentRepository())

        val snapshot = engine.startLessonSession(packId, lessonId, sessionSize = 3)
        val sessionId = snapshot.sessionId
        // current = последняя (card_2).
        engine.nextCard(sessionId)
        engine.nextCard(sessionId)
        assertThat(engine.resumeSession(sessionId)!!.currentCardId).isEqualTo(CardId("card_2"))

        // Скрываем хвостовую текущую → переход на первую доступную (card_0), не null.
        val afterHide = engine.hideCard(sessionId, CardId("card_2"))
        assertThat(afterHide.currentCardId).isEqualTo(CardId("card_0"))
        assertThat(afterHide.currentCardId in afterHide.poolCardIds).isTrue()
    }

    // ───────────────────────────────────────────────────────────────────────
    //  flag (плохая) НЕ убирает карту из пула — bad ≠ hide по дизайну v1
    // ───────────────────────────────────────────────────────────────────────
    @Test
    fun `flag card does not remove from pool`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(5)) }
        val userContent = FakeUserContentRepository()
        val (engine, _, _) = buildEngine(content, userContent)

        val snapshot = engine.startLessonSession(packId, lessonId, sessionSize = 5)
        val sessionId = snapshot.sessionId
        val poolBefore = snapshot.poolCardIds
        val currentBefore = snapshot.currentCardId

        val afterFlag = engine.flagCard(sessionId, CardId("card_3"))

        // Пул и текущая карта не изменились — flag только помечает, не фильтрует.
        assertThat(afterFlag.poolCardIds).isEqualTo(poolBefore)
        assertThat(afterFlag.currentCardId).isEqualTo(currentBefore)
        assertThat(afterFlag.poolCardIds).contains(CardId("card_3"))
    }

    // ───────────────────────────────────────────────────────────────────────
    //  WORD_BANK → НЕ mark shown; VOICE/KEYBOARD → mark shown (дизайн v1)
    // ───────────────────────────────────────────────────────────────────────
    @Test
    fun `word bank does not mark shown`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(3)) }
        val (engine, _, _) = buildEngine(content, FakeUserContentRepository())

        val snapshot = engine.startLessonSession(packId, lessonId, sessionSize = 3)
        val sessionId = snapshot.sessionId

        engine.submitAnswer(sessionId, CardId("card_1"), isCorrect = true, inputMode = InputMode.WORD_BANK)

        val after = engine.resumeSession(sessionId)!!
        assertThat(after.shownCardIds).doesNotContain(CardId("card_1"))
        // Прогресс при этом обновился (ответ зачтён).
        assertThat(after.correctCount).isEqualTo(1)
    }

    @Test
    fun `voice input marks shown`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(3)) }
        val (engine, _, _) = buildEngine(content, FakeUserContentRepository())

        val snapshot = engine.startLessonSession(packId, lessonId, sessionSize = 3)
        val sessionId = snapshot.sessionId

        engine.submitAnswer(sessionId, CardId("card_1"), isCorrect = true, inputMode = InputMode.VOICE)

        val after = engine.resumeSession(sessionId)!!
        assertThat(after.shownCardIds).contains(CardId("card_1"))
        assertThat(after.correctCount).isEqualTo(1)
    }

    @Test
    fun `keyboard input marks shown`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(3)) }
        val (engine, _, _) = buildEngine(content, FakeUserContentRepository())

        val snapshot = engine.startLessonSession(packId, lessonId, sessionSize = 3)
        val sessionId = snapshot.sessionId

        engine.submitAnswer(sessionId, CardId("card_2"), isCorrect = false, inputMode = InputMode.KEYBOARD)

        val after = engine.resumeSession(sessionId)!!
        assertThat(after.shownCardIds).contains(CardId("card_2"))
        assertThat(after.incorrectCount).isEqualTo(1)
    }

    // ───────────────────────────────────────────────────────────────────────
    //  submitAnswer атомарен: progress + mark shown в одной saveSession
    // ───────────────────────────────────────────────────────────────────────
    @Test
    fun `submit answer is atomic progress and shown`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(2)) }
        val (engine, _, _) = buildEngine(content, FakeUserContentRepository())

        val snapshot = engine.startLessonSession(packId, lessonId, sessionSize = 2)
        val sessionId = snapshot.sessionId

        val after = engine.submitAnswer(sessionId, CardId("card_0"), isCorrect = true, inputMode = InputMode.VOICE)

        // Одной операцией: и прогресс, и shown согласованы.
        assertThat(after.correctCount).isEqualTo(1)
        assertThat(after.shownCardIds).contains(CardId("card_0"))
    }

    // ───────────────────────────────────────────────────────────────────────
    //  completeSession переводит статус в COMPLETED
    // ───────────────────────────────────────────────────────────────────────
    @Test
    fun `complete session sets completed status`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(2)) }
        val (engine, _, _) = buildEngine(content, FakeUserContentRepository())

        val snapshot = engine.startLessonSession(packId, lessonId, sessionSize = 2)
        engine.completeSession(snapshot.sessionId)

        val after = engine.resumeSession(snapshot.sessionId)!!
        assertThat(after.status).isEqualTo(SessionStatus.COMPLETED)
    }

    // ───────────────────────────────────────────────────────────────────────
    //  resume несуществующей сессии → null (не падает, не создаёт фантом)
    // ───────────────────────────────────────────────────────────────────────
    @Test
    fun `resume unknown session returns null`() = runTest {
        val content = FakeContentRepository()
        val (engine, _, _) = buildEngine(content, FakeUserContentRepository())

        assertThat(engine.resumeSession(com.alexpo.grammermate.domain.model.SessionId("ghost"))).isNull()
    }

    private fun assertTrue(condition: Boolean) {
        assertThat(condition).isTrue()
    }
}
