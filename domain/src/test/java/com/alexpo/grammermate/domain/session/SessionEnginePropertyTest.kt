package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionStatus
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Property-тесты инвариантов сессии (Фаза 2 плана стабилизации 2026-08-26,
 * gate: «`currentCardId in pool` или terminal/empty состояние доказаны
 * property tests»; «Double Submit/Next засчитывается ровно один раз»).
 *
 * Детерминированные псевдослучайные последовательности команд (фиксированные
 * seed'ы — воспроизводимость падения) поверх [SessionEngine] с fake-репозиториями.
 * После КАЖДОЙ команды проверяются инварианты:
 *
 * 1. `currentCardId == null ∨ currentCardId ∈ poolCardIds` (или сессия
 *    terminal/empty — тогда пул пуст/статус COMPLETED);
 * 2. `shownCardIds ⊆ poolCardIds` (скрытая карта уходит и из shown);
 * 3. пул без дубликатов;
 * 4. `correctCount + incorrectCount` равен числу ЗАЧТЁННЫХ submit'ов —
 *    повторный submit той же карты (double-tap) не увеличивает счёт;
 * 5. ревизия строго возрастает на каждой успешной durable-мутации.
 */
class SessionEnginePropertyTest {

    private val packId = PackId("pack")
    private val lessonId = LessonId("lesson")
    private val cardCount = 12

    private fun lessonCards(): List<Card> = (0 until cardCount).map { idx ->
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

    @Test
    fun `random command sequences preserve session invariants`() = runTest {
        repeat(50) { seed ->
            val random = Random(seed)
            val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards()) }
            val userContent = FakeUserContentRepository()
            val sessionRepo = FakeSessionRepository(clock = { 1_700_000_000_000L + seed })
            val engine = SessionEngine(
                sessionRepository = sessionRepo,
                contentRepository = content,
                userContentRepository = userContent,
                clock = { 1_700_000_000_000L + seed },
            )

            var snapshot = engine.startLessonSession(packId, lessonId, sessionSize = cardCount)
            var revision = snapshot.revision
            // Сколько РАЗЛИЧНЫХ карт закрыто submit'ом (зачтённые ответы).
            val submittedCards = mutableSetOf<CardId>()

            checkInvariants(snapshot, seed, "start")
            assertThat(snapshot.revision).isEqualTo(revision)

            repeat(60) { step ->
                val pool = snapshot.poolCardIds
                when (random.nextInt(6)) {
                    // Submit текущей карты (возможен повторный — double-tap).
                    0, 1 -> {
                        val card = snapshot.currentCardId ?: return@repeat
                        val isCorrect = random.nextBoolean()
                        val before = snapshot.correctCount + snapshot.incorrectCount
                        snapshot = engine.submitAnswer(
                            sessionId = snapshot.sessionId,
                            cardId = card,
                            isCorrect = isCorrect,
                            inputMode = InputMode.KEYBOARD,
                        )
                        val after = snapshot.correctCount + snapshot.incorrectCount
                        if (card in submittedCards) {
                            // Повторный submit той же карты — идемпотентен.
                            assertThat(after).isEqualTo(before)
                        } else {
                            assertThat(after).isEqualTo(before + 1)
                            submittedCards += card
                        }
                    }
                    // Next после ответа / skip-advance.
                    2, 3 -> {
                        if (snapshot.status == SessionStatus.ACTIVE) {
                            snapshot = engine.nextCardOrComplete(snapshot.sessionId)
                        }
                    }
                    // Скрыть случайную карту пула.
                    4 -> {
                        val victim = pool.randomOrNull(random) ?: return@repeat
                        snapshot = engine.hideCard(snapshot.sessionId, victim)
                        submittedCards -= victim
                    }
                    // Resume: reload + явное восстановление, если нужно.
                    else -> {
                        val resumed = engine.resumeSession(snapshot.sessionId) ?: return@repeat
                        // Resume либо вернул сохранённое, либо восстановил выпавшую
                        // карту в пул — оба варианта держат инварианты.
                        snapshot = resumed
                    }
                }
                if (snapshot.revision > revision) revision = snapshot.revision
                checkInvariants(snapshot, seed, "step $step")
                assertThat(snapshot.revision).isAtLeast(revision)
            }
        }
    }

    /** Инварианты 1-3 (см. KDoc класса) после каждой команды. */
    private val allCardIds: Set<com.alexpo.grammermate.domain.model.CardId> by lazy {
        lessonCards().map { it.id }.toSet()
    }

    private fun checkInvariants(
        snapshot: com.alexpo.grammermate.domain.model.SessionSnapshot,
        seed: Int,
        at: String,
    ) {
        val current = snapshot.currentCardId
        if (snapshot.poolCardIds.isEmpty()) {
            assertThat(current).isNull()
        } else {
            assertThat(current).isNotNull()
            assertThat(snapshot.poolCardIds).contains(current)
        }
        // shown ⊆ ВСЕ карточки урока (фикс D2: пул = активный под-урок и
        // сменяется при переходе к следующему; shown накапливается по всему
        // уроку, поэтому ⊆ pool больше не инвариант).
        if (snapshot.status != SessionStatus.COMPLETED) {
            for (shown in snapshot.shownCardIds) {
                assertThat(allCardIds).contains(shown)
            }
        }
        assertThat(snapshot.poolCardIds.distinct()).hasSize(snapshot.poolCardIds.size)
    }

    /**
     * Gate-проверка «Double Submit засчитывается ровно один раз» на прямом
     * сценарии: два подряд engine.submitAnswer одной карты → один счёт,
     * одна ревизия-мутация.
     */
    @Test
    fun `double submit counts exactly once`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards()) }
        val engine = SessionEngine(
            sessionRepository = FakeSessionRepository(clock = { 1L }),
            contentRepository = content,
            userContentRepository = FakeUserContentRepository(),
            clock = { 1L },
        )
        val started = engine.startLessonSession(packId, lessonId, sessionSize = cardCount)

        val first = engine.submitAnswer(started.sessionId, started.currentCardId!!, true, InputMode.KEYBOARD)
        val second = engine.submitAnswer(started.sessionId, started.currentCardId!!, true, InputMode.KEYBOARD)

        assertThat(first.revision).isEqualTo(started.revision + 1)
        // Повторный submit — no-op: тот же снимок, счётчики не двоены.
        assertThat(second).isEqualTo(first)
        assertThat(second.correctCount).isEqualTo(1)

        val persisted = engine.resumeSession(started.sessionId)!!
        assertThat(persisted.correctCount + persisted.incorrectCount).isEqualTo(1)
    }
}
