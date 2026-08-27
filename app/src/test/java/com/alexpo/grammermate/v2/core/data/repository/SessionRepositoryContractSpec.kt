package com.alexpo.grammermate.v2.core.data.repository

import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.model.TrainingMode
import com.alexpo.grammermate.domain.repository.SessionRepository
import com.alexpo.grammermate.domain.session.StaleSessionRevisionException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Общая contract-спецификация доменного порта [SessionRepository]
 * (Фаза 2 плана стабилизации 2026-08-26, gate: «Repository contract suite
 * проходит одинаково для fake и in-memory Room»).
 *
 * Наследники ([RoomSessionRepositoryContractTest] — in-memory Room;
 * [FakeSessionRepositoryContractTest] — fake) обязаны проходить ВСЕ кейсы
 * идентично: любое расхождение fake/Room — regression.
 *
 * Фиксируемый контракт:
 *  - обязательный пул персистится в порядке передачи, первая карта — current;
 *  - resume только ACTIVE; сохранённый снимок возвращается КАК ЕСТЬ
 *    (включая `currentCardId` вне пула — recovery в домене, не молчаливая
 *    подмена, план Фаза 2);
 *  - ревизии: успешный save пишет `stored + 1`; save с чужой ревизией —
 *    [StaleSessionRevisionException], состояние не меняется;
 *  - гранулярные мутаторы инкрементируют ревизию и не теряют пул.
 */
abstract class SessionRepositoryContractSpec {

    /** Создать тестируемый репозиторий (fresh, пустое состояние). */
    protected abstract fun repository(): SessionRepository

    private val packId = PackId("FIXTURE_PACK")
    private val lessonId = LessonId("lesson_fix_01")
    private val sessionId = SessionId.forLesson(packId, lessonId)
    private val pool = listOf("card_fix_2", "card_fix_3", "card_fix_4").map(::CardId)

    private suspend fun createSession(
        repository: SessionRepository = repository(),
        mode: TrainingMode = TrainingMode.LESSON,
    ): com.alexpo.grammermate.domain.model.SessionSnapshot =
        repository.getOrCreateSession(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = mode,
            poolCardIds = pool,
        )

    @Test
    fun `getOrCreateSession persists required pool in order with first card current`() = runTest {
        val repository = repository()

        val snapshot = createSession(repository)

        assertThat(snapshot.poolCardIds).containsExactlyElementsIn(pool).inOrder()
        assertThat(snapshot.currentCardId).isEqualTo(pool.first())
        assertThat(snapshot.shownCardIds).isEmpty()
        assertThat(snapshot.status).isEqualTo(SessionStatus.ACTIVE)
        assertThat(snapshot.revision).isEqualTo(0L)
    }

    @Test
    fun `getOrCreateSession resumes only ACTIVE session without overwriting pool`() = runTest {
        val repository = repository()
        val first = createSession(repository)
        // Мутируем сессию (ревизия 1), затем «повторный вход» с другим пулом.
        val advanced = first.copy(
            currentCardId = pool[1],
            revision = first.revision + 1,
        )
        repository.saveSession(advanced)

        val resumed = repository.getOrCreateSession(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = TrainingMode.LESSON,
            poolCardIds = listOf(CardId("OTHER_CARD")),
        )

        // ACTIVE-сессия resume'ится как есть; переданный пул игнорируется.
        assertThat(resumed.currentCardId).isEqualTo(pool[1])
        assertThat(resumed.poolCardIds).containsExactlyElementsIn(pool).inOrder()
        assertThat(resumed.revision).isEqualTo(1L)
    }

    @Test
    fun `getOrCreateSession after COMPLETED starts fresh session same PK`() = runTest {
        val repository = repository()
        val first = createSession(repository)
        repository.completeSession(sessionId)

        val second = createSession(repository)

        assertThat(second.status).isEqualTo(SessionStatus.ACTIVE)
        assertThat(second.revision).isEqualTo(0L)
        assertThat(second.shownCardIds).isEmpty()
        // Тот же PK сессии.
        assertThat(second.sessionId).isEqualTo(first.sessionId)
    }

    @Test
    fun `saveLoad roundtrip preserves pool current shown and increments revision`() = runTest {
        val repository = repository()
        val first = createSession(repository)
        val updated = first.copy(
            currentCardId = pool[2],
            shownCardIds = setOf(pool[0], pool[1]),
            correctCount = 2,
            revision = first.revision + 1,
        )
        repository.saveSession(updated)

        val loaded = repository.loadSession(sessionId)!!

        assertThat(loaded.poolCardIds).containsExactlyElementsIn(pool).inOrder()
        assertThat(loaded.currentCardId).isEqualTo(pool[2])
        assertThat(loaded.shownCardIds).containsExactly(pool[0], pool[1])
        assertThat(loaded.correctCount).isEqualTo(2)
        assertThat(loaded.revision).isEqualTo(1L)
    }

    @Test
    fun `saveLoad roundtrip preserves pending queue order and session size`() = runTest {
        val repository = repository()
        val pending = listOf("future_1", "future_2", "future_3").map(::CardId)
        val created = repository.getOrCreateSession(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = TrainingMode.LESSON,
            poolCardIds = pool.take(2),
            pendingCardIds = pending,
            sessionSize = 2,
        )

        assertThat(created.pendingCardIds).containsExactlyElementsIn(pending).inOrder()
        assertThat(created.sessionSize).isEqualTo(2)

        val updatedPending = listOf(pending[1], pending[2])
        repository.saveSession(
            created.copy(
                pendingCardIds = updatedPending,
                revision = created.revision + 1,
            ),
        )
        val loaded = repository.loadSession(sessionId)!!

        assertThat(loaded.pendingCardIds).containsExactlyElementsIn(updatedPending).inOrder()
        assertThat(loaded.sessionSize).isEqualTo(2)
    }

    @Test
    fun `saveSession with stale revision is rejected and leaves state unchanged`() = runTest {
        val repository = repository()
        val first = createSession(repository)
        val advanced = first.copy(correctCount = 1, revision = first.revision + 1)
        repository.saveSession(advanced)

        // Stale-писатель: снимок с ревизией, которая НЕ следует за stored (=1).
        val stale = advanced.copy(correctCount = 5, revision = first.revision)
        var thrown: StaleSessionRevisionException? = null
        try {
            repository.saveSession(stale)
        } catch (e: StaleSessionRevisionException) {
            thrown = e
        }

        assertThat(thrown).isNotNull()
        val loaded = repository.loadSession(sessionId)!!
        assertThat(loaded.correctCount).isEqualTo(1)
        assertThat(loaded.revision).isEqualTo(1L)
    }

    /**
     * Фаза 2: «никакой молчаливой замены current card» — data-слой возвращает
     * сохранённый PK как есть, даже если он выпал из пула (контент мутировал).
     * Явное восстановление выполняет SessionEngine (`restoreCurrentCardIfNeeded`).
     */
    @Test
    fun `loadSession returns orphan currentCardId as is`() = runTest {
        val repository = repository()
        val first = createSession(repository)
        val orphaned = first.copy(
            poolCardIds = listOf(pool[0], pool[2]),
            currentCardId = CardId("DELETED_CARD"),
            revision = first.revision + 1,
        )
        repository.saveSession(orphaned)

        val loaded = repository.loadSession(sessionId)!!

        assertThat(loaded.currentCardId?.value).isEqualTo("DELETED_CARD")
    }

    @Test
    fun `empty pool snapshot keeps null current and roundtrips`() = runTest {
        val repository = repository()
        val snapshot = repository.getOrCreateSession(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = TrainingMode.LESSON,
            poolCardIds = emptyList(),
        )

        assertThat(snapshot.currentCardId).isNull()
        assertThat(snapshot.poolCardIds).isEmpty()

        val loaded = repository.loadSession(sessionId)!!
        assertThat(loaded.currentCardId).isNull()
        assertThat(loaded.poolCardIds).isEmpty()
    }

    @Test
    fun `deleteSession removes snapshot entirely`() = runTest {
        val repository = repository()
        createSession(repository)

        repository.deleteSession(sessionId)

        assertThat(repository.loadSession(sessionId)).isNull()
    }
}
