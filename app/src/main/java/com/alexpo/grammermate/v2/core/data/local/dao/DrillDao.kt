package com.alexpo.grammermate.v2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.alexpo.grammermate.v2.core.data.local.entity.AuxDrillCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.AuxDrillComboProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BossRewardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VerbDrillCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VerbDrillComboProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VerbDrillLastSessionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VocabWordEntity
import com.alexpo.grammermate.v2.core.data.local.entity.WordMasteryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-слой drill-тренировок: vocab (SRS по словам), verb (спряжение) и aux
 * (подводящие), а также прогресс combos и награды за boss-битвы.
 *
 * Контент (слова/карточки) — read-mostly: `REPLACE`-upsert'ы при pack-import
 * (включая bulk-insert). Прогресс combos и SRS-состояния — изменяемое во времени
 * пользовательское состояние.
 *
 * Ключевые выборки: [observeDueWords] — реактивная SRS-выборка слов к повторению
 * (по индексу `nextReviewDateMs`); `getVocabWordsByRankRange` — срез по рангу;
 * `*ForVerbTense` — фильтр combo verb+время.
 *
 * [markWordReviewed], [recordVerbCardShown] и [recordAuxCardShown] — атомарные
 * `@Transaction`-операции: обновляют SRS-состояние/progress + персист в одной
 * транзакции, чтобы прогресс не рассинхронизировался. Перерасчёт самой лестницы
 * интервалов делает репозиторий и передаёт уже готовое состояние сюда.
 */
@Dao
interface DrillDao {

    // ── Vocab words (контент) ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabWord(entity: VocabWordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVocabWord(entity: VocabWordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabWords(entities: List<VocabWordEntity>)

    @Query("SELECT * FROM vocab_words WHERE packId = :packId ORDER BY rank")
    suspend fun getVocabWordsForPack(packId: String): List<VocabWordEntity>

    @Query("SELECT * FROM vocab_words WHERE packId = :packId AND rank >= :minRank AND rank <= :maxRank ORDER BY rank")
    suspend fun getVocabWordsByRankRange(packId: String, minRank: Int, maxRank: Int): List<VocabWordEntity>

    @Query("SELECT * FROM vocab_words WHERE id = :id")
    suspend fun getVocabWord(id: String): VocabWordEntity?

    // ── Word mastery (SRS по словам) ──────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordMastery(entity: WordMasteryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWordMastery(entity: WordMasteryEntity)

    @Query("SELECT * FROM word_mastery WHERE packId = :packId AND wordId = :wordId")
    suspend fun getWordMastery(packId: String, wordId: String): WordMasteryEntity?

    /**
     * ★ SRS-выборка: слова ПАКА, у которых срок повтора наступил к моменту
     * [now], отсортированные по возрастанию [WordMasteryEntity.nextReviewDateMs]
     * (самые «просроченные»), не более [limit] штук. Реактивна — для живого
     * Review-списка. ADR-003: фильтр по паку обязателен.
     */
    @Query(
        "SELECT * FROM word_mastery WHERE packId = :packId AND nextReviewDateMs <= :now " +
            "ORDER BY nextReviewDateMs LIMIT :limit"
    )
    fun observeDueWords(now: Long, packId: String, limit: Int): Flow<List<WordMasteryEntity>>

    @Query("SELECT * FROM word_mastery WHERE packId = :packId")
    suspend fun getAllWordMastery(packId: String): List<WordMasteryEntity>

    /**
     * ★ Атомарно зафиксировать повторение слова: upsert пересчитанного SRS-состояния
     * ([updatedMastery] с новыми счётчиками/`nextReviewDateMs`). Перерасчёт самой
     * лестницы интервалов (intervalStepIndex / nextReviewDateMs / isLearned) делает
     * репозиторий и передаёт готовое состояние — здесь только персист в одной транзакции.
     *
     * @param updatedMastery свежее SRS-состояние слова (уже пересчитанное).
     */
    @Transaction
    suspend fun markWordReviewed(updatedMastery: WordMasteryEntity) {
        upsertWordMastery(updatedMastery)
    }

    // ── Verb drill cards (контент) ───────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerbDrillCard(entity: VerbDrillCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerbDrillCards(entities: List<VerbDrillCardEntity>)

    @Query("SELECT * FROM verb_drill_cards WHERE packId = :packId")
    suspend fun getVerbDrillCardsForPack(packId: String): List<VerbDrillCardEntity>

    @Query("SELECT * FROM verb_drill_cards WHERE packId = :packId AND tense = :tense")
    suspend fun getVerbDrillCardsForTense(packId: String, tense: String): List<VerbDrillCardEntity>

    @Query("SELECT * FROM verb_drill_cards WHERE packId = :packId AND verb = :verb AND tense = :tense")
    suspend fun getVerbDrillCardsForVerbTense(packId: String, verb: String, tense: String): List<VerbDrillCardEntity>

    // ── Aux drill cards (контент) ────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuxDrillCard(entity: AuxDrillCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuxDrillCards(entities: List<AuxDrillCardEntity>)

    @Query("SELECT * FROM aux_drill_cards WHERE packId = :packId AND verb = :verb AND tense = :tense")
    suspend fun getAuxDrillCardsForVerbTense(packId: String, verb: String, tense: String): List<AuxDrillCardEntity>

    // ── Verb drill combo progress ────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVerbComboProgress(entity: VerbDrillComboProgressEntity)

    @Query("SELECT * FROM verb_drill_combo_progress WHERE packId = :packId AND comboKey = :comboKey")
    suspend fun getVerbComboProgress(packId: String, comboKey: String): VerbDrillComboProgressEntity?

    @Query("SELECT * FROM verb_drill_combo_progress WHERE packId = :packId")
    suspend fun getAllVerbComboProgress(packId: String): List<VerbDrillComboProgressEntity>

    /**
     * ★ Атомарно зафиксировать показ verb drill карточки: upsert пересчитанный
     * progress combo ([updatedProgress] с обновлёнными ever/todayShown-множествами
     * и `lastDate`). Пересчёт множеств показанных карточек делает репозиторий
     * (десериализует JSON-Set, добавляет [cardId], сериализует обратно) и передаёт
     * готовое состояние — здесь только персист в одной транзакции.
     *
     * @param updatedProgress свежий progress combo (уже с добавленным [cardId]).
     */
    @Transaction
    suspend fun recordVerbCardShown(updatedProgress: VerbDrillComboProgressEntity) {
        upsertVerbComboProgress(updatedProgress)
    }

    // ── Aux drill combo progress ─────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuxComboProgress(entity: AuxDrillComboProgressEntity)

    @Query("SELECT * FROM aux_drill_combo_progress WHERE packId = :packId AND comboKey = :comboKey")
    suspend fun getAuxComboProgress(packId: String, comboKey: String): AuxDrillComboProgressEntity?

    /**
     * ★ Атомарно зафиксировать показ aux drill карточки: upsert пересчитанный
     * progress combo. Пересчёт множества показанных делает репозиторий.
     *
     * @param updatedProgress свежий progress combo (уже с добавленной карточкой).
     */
    @Transaction
    suspend fun recordAuxCardShown(updatedProgress: AuxDrillComboProgressEntity) {
        upsertAuxComboProgress(updatedProgress)
    }

    // ── Verb drill last session (resume) ─────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVerbLastSession(entity: VerbDrillLastSessionEntity)

    @Query("SELECT * FROM verb_drill_last_session WHERE packId = :packId")
    suspend fun getVerbLastSession(packId: String): VerbDrillLastSessionEntity?

    // ── Boss rewards ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBossReward(entity: BossRewardEntity)

    @Query("SELECT * FROM boss_rewards WHERE packId = :packId")
    suspend fun getBossRewards(packId: String): List<BossRewardEntity>

    @Query("SELECT * FROM boss_rewards WHERE packId = :packId AND bossType = :bossType AND scopeKey = :scopeKey")
    suspend fun getBossReward(packId: String, bossType: String, scopeKey: String): BossRewardEntity?
}
