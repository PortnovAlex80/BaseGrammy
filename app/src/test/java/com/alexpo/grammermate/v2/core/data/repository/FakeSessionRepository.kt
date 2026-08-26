package com.alexpo.grammermate.v2.core.data.repository

import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionSnapshot
import com.alexpo.grammermate.domain.model.SessionState
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.model.TrainingMode
import com.alexpo.grammermate.domain.repository.SessionRepository
import com.alexpo.grammermate.domain.session.StaleSessionRevisionException

/**
 * Fake [SessionRepository] для app-тестов (Фаза 2 плана стабилизации
 * 2026-08-26: contract suite «проходит одинаково для fake и in-memory Room»).
 *
 * Семантика ПОЛНОСТЬЮ повторяет контракт Room-реализации:
 *  - resume только ACTIVE-сессий (getOrCreateSession);
 *  - loadSession возвращает сохранённое КАК ЕСТЬ (recovery — зона Engine);
 *  - saveSession — optimistic-concurrency: ревизия снимка обязана быть
 *    `stored + 1`, иначе [StaleSessionRevisionException];
 *  - гранулярные мутаторы инкрементируют ревизию.
 *
 * Тестовые рычаги: [failSaveSession] — имитация падения персистенции;
 * [store] — прямая инспекция состояния.
 */
class FakeSessionRepository(
    private val clock: () -> Long = { System.currentTimeMillis() },
) : SessionRepository {

    val store = linkedMapOf<String, SessionSnapshot>()

    /** Рычаг failure-injection: saveSession бросает вместо записи. */
    var failSaveSession = false

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
        store[sessionId.value]?.takeIf { it.status == SessionStatus.ACTIVE }?.let { return it }
        // Не-ACTIVE (COMPLETED) строка удаляется перед свежим проходом —
        // как Room-impl (delete + свежая строка с ревизией 0).
        store.remove(sessionId.value)
        val now = clock()
        val pool = poolCardIds.filter { it.value.isNotBlank() }
        val fresh = SessionSnapshot(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = mode,
            currentCardId = pool.firstOrNull(),
            status = SessionStatus.ACTIVE,
            state = SessionState.ACTIVE,
            revision = 0L,
            poolCardIds = pool,
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
        store[sessionId.value] = fresh
        return fresh
    }

    override suspend fun loadSession(sessionId: SessionId): SessionSnapshot? = store[sessionId.value]

    override suspend fun saveSession(snapshot: SessionSnapshot) {
        if (failSaveSession) throw IllegalStateException("simulated Room failure")
        val stored = store[snapshot.sessionId.value]
        if (stored != null && snapshot.revision != stored.revision + 1) {
            throw StaleSessionRevisionException(
                snapshot.sessionId,
                snapshot.revision,
                stored.revision,
            )
        }
        store[snapshot.sessionId.value] = snapshot
    }

    override suspend fun completeSession(sessionId: SessionId) {
        store[sessionId.value]?.let {
            store[sessionId.value] = it.copy(
                status = SessionStatus.COMPLETED,
                updatedAtMs = clock(),
                revision = it.revision + 1,
            )
        }
    }

    override suspend fun setCurrentCard(sessionId: SessionId, cardId: CardId) {
        store[sessionId.value]?.let {
            store[sessionId.value] = it.copy(
                currentCardId = cardId,
                updatedAtMs = clock(),
                revision = it.revision + 1,
            )
        }
    }

    override suspend fun markCardShown(sessionId: SessionId, cardId: CardId) {
        store[sessionId.value]?.let {
            store[sessionId.value] = it.copy(
                shownCardIds = it.shownCardIds + cardId,
                updatedAtMs = clock(),
                revision = it.revision + 1,
            )
        }
    }

    override suspend fun updateProgress(sessionId: SessionId, correct: Int, incorrect: Int, hint: Int) {
        store[sessionId.value]?.let {
            store[sessionId.value] = it.copy(
                correctCount = correct,
                incorrectCount = incorrect,
                hintCount = hint,
                updatedAtMs = clock(),
                revision = it.revision + 1,
            )
        }
    }

    override suspend fun deleteSession(sessionId: SessionId) {
        store.remove(sessionId.value)
    }
}
