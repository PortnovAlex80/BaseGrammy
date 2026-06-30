package com.alexpo.grammermate.v2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.alexpo.grammermate.v2.core.data.local.entity.CardEncounterEntity
import com.alexpo.grammermate.v2.core.data.local.entity.MasteryStateEntity
import com.alexpo.grammermate.v2.core.data.local.entity.ShownCardEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-слой освоения уроков и интервального повторения (SRS).
 *
 * Хранит, какие карточки урока показаны ([shown_cards]) и как часто
 * ([card_encounters]), считает «цветок» освоения ([mastery_states]) и дату
 * следующего повтора ([MasteryStateEntity.dueAtMs]).
 *
 * Ключевая выборка — [dueCards]: по индексу `dueAtMs` отбирает карточки к
 * повторению (режим Review). [recordCardShow] пишет показ атомарно: обновляет
 * mastery + добавляет в shown-set + инкрементит счётчик встреч карточки.
 *
 * `order` — зарезервированное слово SQL, экранируется backticks.
 */
@Dao
interface MasteryDao {

    // ── Mastery state ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MasteryStateEntity)

    @Query("SELECT * FROM mastery_states WHERE packId = :packId AND lessonId = :lessonId")
    suspend fun get(packId: String, lessonId: String): MasteryStateEntity?

    @Query("SELECT * FROM mastery_states WHERE packId = :packId AND lessonId = :lessonId")
    fun observe(packId: String, lessonId: String): Flow<MasteryStateEntity?>

    @Query("UPDATE mastery_states SET completedAtMs = :now WHERE packId = :packId AND lessonId = :lessonId AND completedAtMs IS NULL")
    suspend fun markCompleted(packId: String, lessonId: String, now: Long)

    /**
     * ★ SRS-выборка: состояния, у которых срок повтора наступил к моменту [now]
     * и которые ещё не завершены. Отбираются самые «просроченные» (по возрастанию
     * [MasteryStateEntity.dueAtMs]), не более [limit] штук.
     *
     * Возвращает mastery-строки — карточки для повторения берёт репозиторий
     * по `packId`/`lessonId` (маппинг mastery → CardId делается выше).
     */
    @Query(
        "SELECT * FROM mastery_states WHERE dueAtMs <= :now AND completedAtMs IS NULL ORDER BY dueAtMs LIMIT :limit"
    )
    suspend fun dueCards(now: Long, limit: Int): List<MasteryStateEntity>

    // ── Shown cards (mastery) ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShownCard(entity: ShownCardEntity)

    @Query("INSERT OR IGNORE INTO shown_cards(packId, lessonId, cardId) VALUES (:packId, :lessonId, :cardId)")
    suspend fun markShown(packId: String, lessonId: String, cardId: String)

    @Query("SELECT cardId FROM shown_cards WHERE packId = :packId AND lessonId = :lessonId")
    suspend fun getShownCardIds(packId: String, lessonId: String): List<String>

    @Query("DELETE FROM shown_cards WHERE packId = :packId AND lessonId = :lessonId")
    suspend fun clearShownCards(packId: String, lessonId: String)

    // ── Card encounters ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEncounter(entity: CardEncounterEntity)

    /**
     * Атомарно увеличить счётчик встреч карточки: вставить строку со счётчиком 1,
     * либо при конфликте инкрементить существующий `count`. (REPLACE-upsert сущности
     * затирал бы счётчик, поэтому здесь — чистый SQL `ON CONFLICT DO UPDATE`.)
     */
    @Query(
        "INSERT INTO card_encounters(packId, lessonId, cardId, count) VALUES (:packId, :lessonId, :cardId, 1) " +
            "ON CONFLICT(packId, lessonId, cardId) DO UPDATE SET count = count + 1"
    )
    suspend fun incrementEncounter(packId: String, lessonId: String, cardId: String)

    @Query("SELECT * FROM card_encounters WHERE packId = :packId AND lessonId = :lessonId")
    suspend fun getEncounters(packId: String, lessonId: String): List<CardEncounterEntity>

    /**
     * ★ Атомарно зафиксировать показ карточки урока в момент [now].
     *
     * Обновляет агрегированное состояние освоения ([updatedMastery] с новыми
     * счётчиками/`dueAtMs`), добавляет карточку в shown-set (дедуп по IGNORE) и
     * upsert'ит счётчик встреч карточки. Всё в одной транзакции — чтобы mastery,
     * shown-set и encounters не рассинхронизировались (особенно важно для SRS).
     *
     * @param packId         пак урока.
     * @param lessonId       урок.
     * @param cardId         показанная карточка.
     * @param now            момент показа (epoch-мс).
     * @param updatedMastery свежее агрегированное mastery (уже пересчитанное).
     */
    @Transaction
    suspend fun recordCardShow(
        packId: String,
        lessonId: String,
        cardId: String,
        now: Long,
        updatedMastery: MasteryStateEntity,
    ) {
        upsert(updatedMastery)
        markShown(packId, lessonId, cardId)
        incrementEncounter(packId, lessonId, cardId)
    }
}
