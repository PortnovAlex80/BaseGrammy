package com.alexpo.grammermate.v2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alexpo.grammermate.v2.core.data.local.entity.BadSentenceEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BgVocabMarkEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BgVocabPositionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.HiddenCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.MigrationFlagEntity
import com.alexpo.grammermate.v2.core.data.local.entity.PomodoroHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-слой пользовательского контента: скрытые карточки, «плохие» предложения,
 * пометки фонового словаря, история помодоро и флаги миграции.
 *
 * Скрытые карточки реактивны ([observeHiddenCardIds]) — UI фильтрует выдачу на
 * лету. История помодоро реактивна ([observePomodoroHistory]) для графиков.
 * Остальное — point-in-time `suspend`-операции.
 *
 * Флаги миграции (`migratable_files`) — одноразовые маркеры «файл уже перенесён»,
 * используются при импорте legacy-данных v1 → v2.
 */
@Dao
interface UserContentDao {

    // ── Hidden cards ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideCard(entity: HiddenCardEntity)

    @Query("DELETE FROM hidden_cards WHERE cardId = :cardId")
    suspend fun unhideCard(cardId: String)

    @Query("SELECT cardId FROM hidden_cards")
    suspend fun getHiddenCardIds(): List<String>

    @Query("SELECT cardId FROM hidden_cards")
    fun observeHiddenCardIds(): Flow<List<String>>

    @Query("DELETE FROM hidden_cards")
    suspend fun clearHiddenCards()

    // ── Bad sentences (жалобы на контент) ────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadSentence(entity: BadSentenceEntity)

    @Query("SELECT * FROM bad_sentences WHERE packId = :packId")
    suspend fun getBadSentences(packId: String): List<BadSentenceEntity>

    @Query("DELETE FROM bad_sentences WHERE packId = :packId AND cardId = :cardId")
    suspend fun deleteBadSentence(packId: String, cardId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bad_sentences WHERE packId = :packId AND cardId = :cardId)")
    suspend fun isBadSentence(packId: String, cardId: String): Boolean

    // ── Background vocab marks ───────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBgVocabMark(entity: BgVocabMarkEntity)

    @Query("SELECT * FROM bg_vocab_marks WHERE word = :word")
    suspend fun getBgVocabMark(word: String): BgVocabMarkEntity?

    @Query("DELETE FROM bg_vocab_marks WHERE word = :word")
    suspend fun deleteBgVocabMark(word: String)

    @Query("UPDATE bg_vocab_marks SET mark = 'NONE'")
    suspend fun resetGreenMarks()

    @Query("DELETE FROM bg_vocab_marks WHERE mark = 'GREEN'")
    suspend fun deleteGreenMarks()

    // ── Background vocab position ────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBgVocabPosition(entity: BgVocabPositionEntity)

    @Query("SELECT * FROM bg_vocab_position WHERE `key` = :key")
    suspend fun getBgVocabPosition(key: String): BgVocabPositionEntity?

    // ── Pomodoro history ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPomodoroSession(entity: PomodoroHistoryEntity)

    @Query("SELECT * FROM pomodoro_history ORDER BY completedAtMs DESC")
    suspend fun getPomodoroHistory(): List<PomodoroHistoryEntity>

    @Query("SELECT * FROM pomodoro_history ORDER BY completedAtMs DESC")
    fun observePomodoroHistory(): Flow<List<PomodoroHistoryEntity>>

    @Query("DELETE FROM pomodoro_history")
    suspend fun clearPomodoroHistory()

    // ── Migration flags (migratable_files) ───────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMigrationFlag(entity: MigrationFlagEntity)

    @Query("SELECT done FROM migratable_files WHERE `key` = :key")
    suspend fun getMigrationFlag(key: String): Boolean?

    @Query("DELETE FROM migratable_files WHERE `key` = :key")
    suspend fun deleteMigrationFlag(key: String)
}
