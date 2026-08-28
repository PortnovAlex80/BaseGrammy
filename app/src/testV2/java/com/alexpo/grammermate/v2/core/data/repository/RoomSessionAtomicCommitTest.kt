package com.alexpo.grammermate.v2.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.session.SessionEngine
import com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase
import com.alexpo.grammermate.v2.core.data.local.TrainingDbFixture
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Атомарность составного commit'а сессии (Фаза 2 плана стабилизации
 * 2026-08-26, gate: «Failure injection в середине commit оставляет БД в
 * исходном состоянии»; ADR-001 pre-mortem №3 — атомарность не останавливается
 * на снимке сессии).
 *
 * [SessionEngine] работает через [RoomSessionCommitCoordinator]: персистенция
 * снимка (`SessionDao.saveSnapshot`) и hook'и (mastery/completion) применяются
 * ОДНОЙ Room-транзакцией. Падение любого шага откатывает всё:
 *  - `submitAnswer`: mastery-хук упал → счётчики/shown НЕ засчитаны;
 *  - `nextCardOrComplete`: completion-хук упал → статус остался ACTIVE;
 *  - happy path: сессия и mastery зафиксированы вместе.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSessionAtomicCommitTest {

    private lateinit var db: GrammarMateDatabase

    private val packId = PackId(TrainingDbFixture.PACK_ID)
    private val lessonId = LessonId(TrainingDbFixture.LESSON_ID)
    private val sessionId = SessionId.forLesson(packId, lessonId)

    @Before
    fun setUp() = runTest {
        db = TrainingDbFixture.inMemory(ApplicationProvider.getApplicationContext())
        TrainingDbFixture.seedTrainingContent(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun contentRepository(): ContentRepository = mockk(relaxed = true) {
        coEvery { getCards(packId, lessonId) } returns TrainingDbFixture.CARD_IDS.mapIndexed { index, cardId ->
            com.alexpo.grammermate.domain.model.Card(
                id = CardId(cardId),
                packId = packId,
                lessonId = lessonId,
                ord = index,
                type = com.alexpo.grammermate.domain.model.CardType.SENTENCE,
                promptRu = "Промпт $index",
                acceptedAnswers = listOf("answer $index"),
                tense = null, verb = null, verbGroup = null, person = null,
                frequencyRank = null,
            )
        }
    }

    private fun userContentRepository(): com.alexpo.grammermate.domain.repository.UserContentRepository =
        mockk(relaxed = true) {
            coEvery { getHiddenCardIds(any()) } returns emptySet()
        }

    private fun engine(
        content: ContentRepository = contentRepository(),
        onMarkShown: suspend (PackId, LessonId?, CardId, Long) -> Unit = { _, _, _, _ -> },
        onSessionCompleted: suspend (PackId, LessonId?, Long) -> Unit = { _, _, _ -> },
    ): SessionEngine {
        val repository = SessionRepositoryImpl(db.sessionDao())
        val coordinator = RoomSessionCommitCoordinator(db)
        return SessionEngine(
            sessionRepository = repository,
            contentRepository = content,
            userContentRepository = userContentRepository(),
            clock = { 1_700_000_000_000L },
            commitCoordinator = coordinator,
            onMarkShown = onMarkShown,
            onSessionCompleted = onSessionCompleted,
        )
    }

    @Test
    fun `submitAnswer mastery hook failure rolls back session state`() = runTest {
        val engine = engine(
            onMarkShown = { _, _, _, _ -> error("mastery write failed (injected)") },
        )
        val started = engine.startLessonSession(packId, lessonId, sessionSize = 10)
        val revisionBefore = started.revision

        var thrown: IllegalStateException? = null
        try {
            engine.submitAnswer(
                sessionId = started.sessionId,
                cardId = started.currentCardId!!,
                isCorrect = true,
                inputMode = InputMode.KEYBOARD,
            )
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertThat(thrown).isNotNull()
        // БД в исходном состоянии: ни счётчиков, ни shown, ревизия не тронута.
        val persisted = SessionRepositoryImpl(db.sessionDao()).loadSession(sessionId)!!
        assertThat(persisted.correctCount).isEqualTo(0)
        assertThat(persisted.incorrectCount).isEqualTo(0)
        assertThat(persisted.shownCardIds).isEmpty()
        assertThat(persisted.revision).isEqualTo(revisionBefore)
    }

    @Test
    fun `nextCardOrComplete completion hook failure rolls back status`() = runTest {
        // Пул из одной карты: первый Next на последней карте → COMPLETED.
        val engine = engine(
            onSessionCompleted = { _, _, _ -> error("lesson completion failed (injected)") },
        )
        val started = engine.startLessonSession(packId, lessonId, sessionSize = Int.MAX_VALUE)
        repeat(started.poolCardIds.lastIndex) { engine.nextCard(started.sessionId) }

        var thrown: IllegalStateException? = null
        try {
            engine.nextCardOrComplete(started.sessionId)
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertThat(thrown).isNotNull()
        val persisted = SessionRepositoryImpl(db.sessionDao()).loadSession(sessionId)!!
        assertThat(persisted.status).isEqualTo(SessionStatus.ACTIVE)
    }

    @Test
    fun `successful submit persists session and mastery together`() = runTest {
        val masteryRepository = MasteryRepositoryImpl(db.masteryDao())
        val engine = engine(
            onMarkShown = { packId, lessonId, cardId, nowMs ->
                masteryRepository.recordCardShow(packId, lessonId!!, cardId, nowMs)
            },
        )
        val started = engine.startLessonSession(packId, lessonId, sessionSize = 10)

        engine.submitAnswer(
            sessionId = started.sessionId,
            cardId = started.currentCardId!!,
            isCorrect = true,
            inputMode = InputMode.KEYBOARD,
        )

        // Обе половины составного события зафиксированы.
        val persisted = SessionRepositoryImpl(db.sessionDao()).loadSession(sessionId)!!
        assertThat(persisted.correctCount).isEqualTo(1)
        assertThat(persisted.shownCardIds).containsExactly(started.currentCardId)
        val mastery = masteryRepository.getMastery(packId, lessonId)
        assertThat(mastery).isNotNull()
        assertThat(mastery!!.shownCardIds).contains(started.currentCardId)
    }
}
