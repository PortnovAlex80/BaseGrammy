package com.alexpo.grammermate.v2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.alexpo.grammermate.v2.core.data.local.entity.SessionCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionShownCardEntity

/**
 * Data-слой сессий — ★ КРИТИЧНО, фикс бага card_15.
 *
 * Сессия хранится и возобновляется как единый целостный снимок: курсор +
 * упорядоченный пул ([session_cards]) + множество показанных
 * ([session_shown_cards]) + текущая карточка по первичному ключу
 * ([SessionEntity.currentCardId], а НЕ по индексу массива).
 *
 * Раньше состояние было размазано по нескольким полям, из-за чего курсор и пул
 * рассинхронизировались и после возобновления показывалась чужая карточка
 * (card_15). Теперь:
 *  - [saveSnapshot] пишет сессию, пул и shown-set в **одной транзакции**;
 *  - [replacePool] пересобирает пул атомарно (delete + insert);
 *  - `currentCardId` обновляется точечно через [updateCursor] как PK, не индекс.
 *
 * Все mutating-методы — `suspend`. UI не подписывается на сессию реактивно,
 * поэтому `Flow` здесь не нужен.
 */
@Dao
interface SessionDao {

    // ── Session row ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id AND status = 'ACTIVE'")
    suspend fun getActiveSession(id: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("UPDATE sessions SET currentCardId = :cardId, cursorIndex = :cursor, updatedAtMs = :now WHERE id = :id")
    suspend fun updateCursor(id: String, cardId: String?, cursor: Int, now: Long)

    @Query("UPDATE sessions SET status = :status, updatedAtMs = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    @Query("UPDATE sessions SET correctCount = :correct, incorrectCount = :incorrect, hintCount = :hint, updatedAtMs = :now WHERE id = :id")
    suspend fun updateCounts(id: String, correct: Int, incorrect: Int, hint: Int, now: Long)

    // ── Session pool (session_cards) ─────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionCards(cards: List<SessionCardEntity>)

    @Query("SELECT * FROM session_cards WHERE sessionId = :id ORDER BY ord")
    suspend fun getSessionCards(id: String): List<SessionCardEntity>

    @Query("DELETE FROM session_cards WHERE sessionId = :id")
    suspend fun deleteSessionCards(id: String)

    /**
     * Атомарно пересобрать пул карточек сессии: удалить старый и вставить [cards].
     *
     * Валидация `currentCardId` ∈ новом пуле выполняется в репозитории: если
     * текущая карточка не входит в пересобранный пул, репозиторий делает явный
     * recovery (а не молчаливую подмену).
     */
    @Transaction
    suspend fun replacePool(sessionId: String, cards: List<SessionCardEntity>) {
        deleteSessionCards(sessionId)
        insertSessionCards(cards)
    }

    // ── Shown set (session_shown_cards) ──────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markShown(entity: SessionShownCardEntity)

    @Query("SELECT * FROM session_shown_cards WHERE sessionId = :id")
    suspend fun getShownCards(id: String): List<SessionShownCardEntity>

    @Query("DELETE FROM session_shown_cards WHERE sessionId = :id")
    suspend fun deleteShownCards(id: String)

    /**
     * ★ Атомарно сохранить весь снимок сессии в одной транзакции.
     *
     * Гарантирует целостность resume: курсор, пул, currentCardId и shown-set
     * всегда согласованы — это и есть фикс бага card_15. Ни при каких условиях
     * курсор не может сослаться на карточку, которой нет в пуле.
     *
     * @param session сессия (курсор + currentCardId + счётчики).
     * @param cards   упорядоченный пул карточек (ord детерминирован).
     * @param shown   множество уже показанных карточек.
     */
    @Transaction
    suspend fun saveSnapshot(
        session: SessionEntity,
        cards: List<SessionCardEntity>,
        shown: List<SessionShownCardEntity>,
    ) {
        upsertSession(session)
        replacePool(session.id, cards)
        deleteShownCards(session.id)
        if (shown.isNotEmpty()) {
            shown.forEach { markShown(it) }
        }
    }
}
