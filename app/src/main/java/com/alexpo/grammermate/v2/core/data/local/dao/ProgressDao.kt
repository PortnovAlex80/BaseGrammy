package com.alexpo.grammermate.v2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.alexpo.grammermate.v2.core.data.local.entity.ChapterProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.DailyCursorEntity
import com.alexpo.grammermate.v2.core.data.local.entity.DrillProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.StreakEntity
import com.alexpo.grammermate.v2.core.data.local.entity.StreakPracticeTodayEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-слой прогресса пользователя: серия дней (streak), прогресс глав,
 * прогресс drill-тренировок и курсор дневной нормы.
 *
 * Streak — реактивный ([observeStreak]) для живого «огонька» в UI. Прогресс
 * глав, drill'ов и дневной курсор — point-in-time `suspend`-чтения/запись.
 *
 * [recordPracticeCompletion] обновляет streak и счётчик «огоньков» за день
 * атомарно: upsert streak + insert записи о выполненной сегодня практике
 * (дедуп по `languageId`+`practiceType`, чтобы один и тот же тип практики не
 * засчитывался дважды за день).
 */
@Dao
interface ProgressDao {

    // ── Streak ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreak(entity: StreakEntity)

    @Query("SELECT * FROM streaks WHERE languageId = :langId")
    suspend fun getStreak(langId: String): StreakEntity?

    @Query("SELECT * FROM streaks WHERE languageId = :langId")
    fun observeStreak(langId: String): Flow<StreakEntity?>

    @Query("DELETE FROM streaks WHERE languageId = :langId")
    suspend fun deleteStreak(langId: String)

    // ── Practice today (дедуп типов практики за день) ────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPracticeToday(entity: StreakPracticeTodayEntity): Long

    @Query("SELECT * FROM streak_practice_today WHERE languageId = :langId")
    suspend fun getPracticeToday(langId: String): List<StreakPracticeTodayEntity>

    @Query("DELETE FROM streak_practice_today")
    suspend fun clearPracticeToday()

    /**
     * ★ Атомарно зафиксировать завершение практики типа [practiceType] в момент [now].
     *
     * Обновляет серию (`streak`) и помечает тип практики как выполненный сегодня
     * (`streak_practice_today`, дедуп по IGNORE). Перерасчёт самой серии
     * (currentStreak / longestStreak / todayFireCount / lastFireDateMs) делает
     * репозиторий и передаёт уже готовый [streak]; здесь — только персист.
     *
     * `insertPracticeToday` возвращает -1 при конфликте (тип уже выполнен сегодня),
     * что репозиторий использует, чтобы решить, нужно ли зажигать новый «огонёк».
     *
     * @param langId        язык практики.
     * @param practiceType  тип практики (TRANSLATION / VOCAB / VERB).
     * @param now           момент завершения (epoch-мс).
     * @param streak        свежее состояние серии (после пересчёта).
     */
    @Transaction
    suspend fun recordPracticeCompletion(
        langId: String,
        practiceType: String,
        now: Long,
        streak: StreakEntity,
    ) {
        upsertStreak(streak)
        insertPracticeToday(StreakPracticeTodayEntity(languageId = langId, practiceType = practiceType))
    }

    // ── Chapter progress ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapterProgress(entity: ChapterProgressEntity)

    @Query("SELECT * FROM chapter_progress WHERE packId = :packId AND chapterId = :chapterId")
    suspend fun getChapterProgress(packId: String, chapterId: String): ChapterProgressEntity?

    @Query(
        "UPDATE chapter_progress SET lessonsStarted = lessonsStarted + :startedDelta, " +
            "lessonsCompleted = lessonsCompleted + :completedDelta, lastAccessedMs = :now " +
            "WHERE packId = :packId AND chapterId = :chapterId"
    )
    suspend fun incrementChapterProgress(
        packId: String,
        chapterId: String,
        startedDelta: Int,
        completedDelta: Int,
        now: Long,
    ): Int

    // ── Drill progress ───────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrillProgress(entity: DrillProgressEntity)

    @Query("SELECT * FROM drill_progress WHERE packId = :packId AND drillType = :drillType")
    suspend fun getDrillProgress(packId: String, drillType: String): DrillProgressEntity?

    @Query("DELETE FROM drill_progress WHERE packId = :packId AND drillType = :drillType")
    suspend fun deleteDrillProgress(packId: String, drillType: String)

    // ── Daily cursor ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyCursor(entity: DailyCursorEntity)

    @Query("SELECT * FROM daily_cursors WHERE packId = :packId")
    suspend fun getDailyCursor(packId: String): DailyCursorEntity?

    @Query("DELETE FROM daily_cursors WHERE packId = :packId")
    suspend fun deleteDailyCursor(packId: String)
}
