package com.alexpo.grammermate.v2.core.domain.repository

import com.alexpo.grammermate.v2.core.domain.model.ChapterId
import com.alexpo.grammermate.v2.core.domain.model.ChapterProgress
import com.alexpo.grammermate.v2.core.domain.model.DailyCursor
import com.alexpo.grammermate.v2.core.domain.model.DrillProgress
import com.alexpo.grammermate.v2.core.domain.model.LanguageId
import com.alexpo.grammermate.v2.core.domain.model.PackId
import com.alexpo.grammermate.v2.core.domain.model.PracticeType
import com.alexpo.grammermate.v2.core.domain.model.StreakData
import kotlinx.coroutines.flow.Flow

/**
 * Прогресс пользователя: серия дней (streak), прогресс глав, прогресс drill'ов
 * и курсор дневной нормы.
 *
 * Streak — реактивный ([observeStreak]) для живого «огонька» в UI. Прогресс
 * глав, drill'ов и дневной курсор — point-in-time `suspend`-чтения/запись.
 */
interface ProgressRepository {

    // ── Streak (серия дней / «огоньки») ────────────────────────────────────

    /** Текущая серия по языку или null, если занятий ещё не было. */
    suspend fun getStreak(langId: LanguageId): StreakData?

    /** Реактивная серия по языку — для подписки UI на «огонёк». */
    fun observeStreak(langId: LanguageId): Flow<StreakData?>

    /**
     * Зафиксировать завершение практики типа [type] в момент [nowMs].
     *
     * Обновляет серию и счётчик «огоньков» за день; возвращает свежую серию.
     */
    suspend fun recordPracticeCompletion(langId: LanguageId, type: PracticeType, nowMs: Long): StreakData

    // ── Прогресс глав ───────────────────────────────────────────────────────

    /** Прогресс главы пака или null, если по ней ещё нет данных. */
    suspend fun getChapterProgress(packId: PackId, chapterId: ChapterId): ChapterProgress?

    /**
     * Обновить прогресс главы пака.
     *
     * @param startedDelta   на сколько изменить счётчик начатых уроков (обычно 0 или +1).
     * @param completedDelta на сколько изменить счётчик завершённых уроков (0 или +1).
     * @param nowMs          момент обращения — записывается в [ChapterProgress.lastAccessedMs].
     */
    suspend fun updateChapterProgress(
        packId: PackId,
        chapterId: ChapterId,
        startedDelta: Int = 0,
        completedDelta: Int = 0,
        nowMs: Long,
    )

    // ── Прогресс drill-тренировок ───────────────────────────────────────────

    /** Прогресс drill-типа в паке или null, если его ещё нет. */
    suspend fun getDrillProgress(packId: PackId, drillType: String): DrillProgress?

    /** Сохранить прогресс drill-тренировки (upsert по pack+drillType). */
    suspend fun saveDrillProgress(progress: DrillProgress)

    // ── Курсор дневной нормы ────────────────────────────────────────────────

    /** Курсор дневной нормы пака или null, если дневная норма не начиналась. */
    suspend fun getDailyCursor(packId: PackId): DailyCursor?

    /** Сохранить курсор дневной нормы пака. */
    suspend fun saveDailyCursor(cursor: DailyCursor)
}
