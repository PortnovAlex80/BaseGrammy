package com.alexpo.grammermate.v2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.alexpo.grammermate.domain.session.StaleSessionRevisionException
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.v2.core.data.local.entity.SessionCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionPendingCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionShownCardEntity

/**
 * Data-слой сессий — ★ КРИТИЧНО, фикс бага card_15.
 *
 * Сессия хранится и возобновляется как единый целостный снимок: упорядоченный
 * пул ([session_cards]) + множество показанных ([session_shown_cards]) +
 * текущая карточка по первичному ключу ([SessionEntity.currentCardId], а НЕ
 * по индексу массива).
 *
 * Фаза 2 плана стабилизации 2026-08-26:
 *  - [loadSnapshotParts] читает сессию, пул и shown-set ОДНОЙ транзакцией —
 *    torn snapshot между тремя SELECT невозможен;
 *  - [saveSnapshot] — hot updates: обычный Submit/Next НЕ переписывает пул и
 *    shown целиком, применяются только изменившиеся части (guardrail плана:
 *    «full pool rewrite на обычный Submit/Next = 0»); shown-строки сохраняют
 *    исходный `shownAtMs`;
 *  - ревизии: запись отвергается [StaleSessionRevisionException], если ревизия
 *    снимка ≠ stored + 1 (optimistic concurrency; БД не изменяется).
 *
 * `currentCardId` обновляется точечно через [updateCursor] как PK, не индекс.
 * Все mutating-методы — `suspend`. UI не подписывается на сессию реактивно,
 * поэтому `Flow` здесь не нужен.
 */
@Dao
interface SessionDao {

    // ── Session row ──────────────────────────────────────────────────────────

    /**
     * Upsert строки сессии. ★ НЕ `@Insert(REPLACE)` (Фаза 2): REPLACE в SQLite =
     * DELETE + INSERT, что через FK `ON DELETE CASCADE` сносил бы pool/shown
     * детей на КАЖДОМ сохранении и убивал hot updates. `@Upsert` на конфликте
     * делает UPDATE — cascade не запускается.
     */
    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id AND status = 'ACTIVE'")
    suspend fun getActiveSession(id: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("UPDATE sessions SET currentCardId = :cardId, cursorIndex = :cursor, revision = revision + 1, updatedAtMs = :now WHERE id = :id")
    suspend fun updateCursor(id: String, cardId: String?, cursor: Int, now: Long)

    @Query("UPDATE sessions SET status = :status, revision = revision + 1, updatedAtMs = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    @Query("UPDATE sessions SET correctCount = :correct, incorrectCount = :incorrect, hintCount = :hint, revision = revision + 1, updatedAtMs = :now WHERE id = :id")
    suspend fun updateCounts(id: String, correct: Int, incorrect: Int, hint: Int, now: Long)

    @Query("UPDATE sessions SET revision = revision + 1, updatedAtMs = :now WHERE id = :id")
    suspend fun bumpRevision(id: String, now: Long)

    // ── Session pool (session_cards) ─────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionCards(cards: List<SessionCardEntity>)

    @Query("SELECT * FROM session_cards WHERE sessionId = :id ORDER BY ord")
    suspend fun getSessionCards(id: String): List<SessionCardEntity>

    @Query("DELETE FROM session_cards WHERE sessionId = :id")
    suspend fun deleteSessionCards(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingCards(cards: List<SessionPendingCardEntity>)

    @Query("SELECT * FROM session_pending_cards WHERE sessionId = :id ORDER BY ord")
    suspend fun getPendingCards(id: String): List<SessionPendingCardEntity>

    @Query("DELETE FROM session_pending_cards WHERE sessionId = :id")
    suspend fun deletePendingCards(id: String)

    @Transaction
    suspend fun replacePending(sessionId: String, cards: List<SessionPendingCardEntity>) {
        deletePendingCards(sessionId)
        insertPendingCards(cards)
    }

    /**
     * Атомарно пересобрать пул карточек сессии: удалить старый и вставить [cards].
     * Вызывается ТОЛЬКО когда пул реально изменился (см. [saveSnapshot]).
     */
    @Transaction
    suspend fun replacePool(sessionId: String, cards: List<SessionCardEntity>) {
        deleteSessionCards(sessionId)
        insertSessionCards(cards)
    }

    // ── Shown set (session_shown_cards) ──────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markShown(entity: SessionShownCardEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markShownAll(entities: List<SessionShownCardEntity>)

    @Query("SELECT * FROM session_shown_cards WHERE sessionId = :id")
    suspend fun getShownCards(id: String): List<SessionShownCardEntity>

    @Query("DELETE FROM session_shown_cards WHERE sessionId = :id")
    suspend fun deleteShownCards(id: String)

    @Query("DELETE FROM session_shown_cards WHERE sessionId = :id AND cardId IN (:cardIds)")
    suspend fun deleteShownCards(id: String, cardIds: List<String>)

    // ── Составные транзакционные операции ────────────────────────────────────

    /**
     * Пометить карточку shown И поднять ревизию строки сессии — одной
     * транзакцией (паритет с fake: снимок изменился → ревизия выросла).
     */
    @Transaction
    suspend fun markShownAndBumpRevision(entity: SessionShownCardEntity, now: Long) {
        markShown(entity)
        bumpRevision(entity.sessionId, now)
    }

    /**
     * Атомарно прочитать все части снимка одной транзакцией (Фаза 2: load —
     * настоящий `@Transaction`, а не три независимых SELECT).
     *
     * @return части снимка либо null, если строки сессии нет.
     */
    @Transaction
    suspend fun loadSnapshotParts(id: String): SessionSnapshotParts? {
        val session = getSession(id) ?: return null
        return SessionSnapshotParts(
            session = session,
            cards = getSessionCards(id),
            pending = getPendingCards(id),
            shown = getShownCards(id),
        )
    }

    /**
     * ★ Атомарно сохранить снимок сессии в одной транзакции — с проверкой
     * ревизии и hot updates.
     *
     * Ревизии: запись принимается iff строка новая ИЛИ
     * `session.revision == stored.revision + 1`; иначе —
     * [StaleSessionRevisionException], БД не изменена. Ревизию в снимке
     * проставляет домен (`SessionEngine` делает copy с `revision + 1`).
     *
     * Hot updates: пул переписывается только при фактическом изменении
     * (сравнение списков); shown — вставка только НОВЫХ строк и удаление
     * только удалённых; исходные `shownAtMs` сохраняются.
     *
     * Гарантирует целостность resume: пул и currentCardId всегда согласованы —
     * это и есть фикс бага card_15.
     */
    @Transaction
    suspend fun saveSnapshot(
        session: SessionEntity,
        cards: List<SessionCardEntity>,
        pending: List<SessionPendingCardEntity>,
        shown: List<SessionShownCardEntity>,
    ) {
        val existing = getSession(session.id)
        if (existing != null && session.revision != existing.revision + 1L) {
            throw StaleSessionRevisionException(
                sessionId = SessionId(session.id),
                snapshotRevision = session.revision,
                storedRevision = existing.revision,
            )
        }
        upsertSession(session)
        if (existing == null || getSessionCards(session.id) != cards) {
            replacePool(session.id, cards)
        }
        if (existing == null || getPendingCards(session.id) != pending) {
            replacePending(session.id, pending)
        }
        val existingShownIds = getShownCards(session.id).map { it.cardId }.toSet()
        val targetShownIds = shown.map { it.cardId }.toSet()
        val toInsert = shown.filter { it.cardId !in existingShownIds }
        if (toInsert.isNotEmpty()) {
            markShownAll(toInsert)
        }
        if (existing != null && targetShownIds != existingShownIds) {
            val removed = (existingShownIds - targetShownIds).toList()
            if (removed.isNotEmpty()) {
                deleteShownCards(session.id, removed)
            }
        }
    }
}

/**
 * Части снимка, прочитанные одной транзакцией [SessionDao.loadSnapshotParts].
 *
 * @property session строка сессии (курсор + currentCardId + счётчики + ревизия).
 * @property cards   упорядоченный пул карточек.
 * @property shown   множество показанных карточек (с исходными shownAtMs).
 */
data class SessionSnapshotParts(
    val session: SessionEntity,
    val cards: List<SessionCardEntity>,
    val pending: List<SessionPendingCardEntity>,
    val shown: List<SessionShownCardEntity>,
)
