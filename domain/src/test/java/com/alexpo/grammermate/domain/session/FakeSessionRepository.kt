package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionSnapshot
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.model.TrainingMode
import com.alexpo.grammermate.domain.repository.SessionRepository

/**
 * In-memory реализация [SessionRepository] для чистых JVM-тестов.
 *
 * Полностью повторяет контракт data-слоя (Room), но без SQLite/Android:
 * состояние хранится в единой мутабельной [Map]. Это позволяет тестировать
 * доменную логику [SessionEngine] (включая критический resume-сценарий
 * бага `card_15`) детерминированно и мгновенно.
 *
 * `getOrCreateSession` создаёт свежий снимок из переданного [poolCardIds]
 * с `currentCardId` = первая карта пула — ровно так, как должен делать
 * реальный data-слой. Пул обязателен (ADR-001: пул строит SessionEngine,
 * data-слой только персистит переданное).
 *
 * Контракт ревизий (Фаза 2, паритет с Room): [saveSession] отвергает снимок,
 * чья ревизия ≠ `stored + 1` ([StaleSessionRevisionException]); гранулярные
 * мутаторы инкрементируют ревизию сами.
 */
class FakeSessionRepository(
    private val clock: () -> Long = { System.currentTimeMillis() },
) : SessionRepository {

    /** Единое хранилище снимков: имитация «одной транзакции» Room. */
    private val store = mutableMapOf<SessionId, SessionSnapshot>()

    override suspend fun getOrCreateSession(
        sessionId: SessionId,
        packId: PackId,
        lessonId: LessonId?,
        mode: TrainingMode,
        poolCardIds: List<CardId>,
        pendingCardIds: List<CardId>,
        sessionSize: Int,
        selectedTense: String?,
        selectedGroup: String?,
        selectedPerson: String?,
    ): SessionSnapshot {
        // Как Room-impl (getActiveSession): resume только ACTIVE-сессий;
        // COMPLETED → свежий снимок с тем же PK (MODE_MATRIX → Normal lesson).
        store[sessionId]?.takeIf { it.status == SessionStatus.ACTIVE }?.let { return it }
        val now = clock()
        val pool = poolCardIds.filter { it.value.isNotBlank() }
        val snapshot = SessionSnapshot(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = mode,
            currentCardId = pool.firstOrNull(),
            status = SessionStatus.ACTIVE,
            state = com.alexpo.grammermate.domain.model.SessionState.ACTIVE,
            revision = 0L,
            poolCardIds = pool,
            pendingCardIds = pendingCardIds,
            sessionSize = sessionSize,
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
        store[sessionId] = snapshot
        return snapshot
    }

    /** Как Room: сохранённое значение возвращается как есть (recovery — Engine). */
    override suspend fun loadSession(sessionId: SessionId): SessionSnapshot? = store[sessionId]

    override suspend fun saveSession(snapshot: SessionSnapshot) {
        val stored = store[snapshot.sessionId]
        if (stored != null && snapshot.revision != stored.revision + 1) {
            throw StaleSessionRevisionException(snapshot.sessionId, snapshot.revision, stored.revision)
        }
        store[snapshot.sessionId] = snapshot
    }

    override suspend fun completeSession(sessionId: SessionId) {
        store[sessionId]?.let { cur ->
            store[sessionId] = cur.copy(
                status = SessionStatus.COMPLETED,
                updatedAtMs = clock(),
                revision = cur.revision + 1,
            )
        }
    }




    override suspend fun deleteSession(sessionId: SessionId) {
        store.remove(sessionId)
    }
}
