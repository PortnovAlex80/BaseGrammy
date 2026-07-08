package com.alexpo.grammermate.v2.core.data.repository

import com.alexpo.grammermate.v2.core.data.local.dao.ProgressDao
import com.alexpo.grammermate.v2.core.data.local.entity.ChapterProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.DailyCursorEntity
import com.alexpo.grammermate.v2.core.data.local.entity.DrillProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.StreakEntity
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.ChapterId
import com.alexpo.grammermate.domain.model.ChapterProgress
import com.alexpo.grammermate.domain.model.DailyCursor
import com.alexpo.grammermate.domain.model.DrillProgress
import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.PracticeType
import com.alexpo.grammermate.domain.model.StreakData
import com.alexpo.grammermate.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Data-слой прогресса пользователя: серия дней (streak), прогресс глав,
 * прогресс drill-тренировок и курсор дневной нормы.
 *
 * Реализация [ProgressRepository] поверх [ProgressDao]. Маппит Room-сущности в
 * доменные модели ([StreakData], [ChapterProgress], [DrillProgress], [DailyCursor])
 * и обратно. Коллекции [CardId] сериализуются в JSON-колонки через
 * kotlinx.serialization (тот же подход, что в
 * [com.alexpo.grammermate.v2.core.data.local.Converters]); [PracticeType]
 * хранится как имя enum'а.
 *
 * ## Логика серии дней (streak)
 * Пересчёт серии делает репозиторий (DAO лишь персистит готовую [StreakEntity]):
 *  - `lastCompletionDateMs == null` → текущая серия = 1 (первое занятие);
 *  - тот же день → серия без изменений;
 *  - прошлый день (разница ровно 1) → серия + 1;
 *  - разрыв ≥ 2 дней → сброс серии до 1.
 * «День» определяется целочисленным делением epoch-мс на 86 400 000 (как в v1).
 * `longestStreak = max(longestStreak, currentStreak)`.
 *
 * ## «Огоньки» за день (todayFireCount)
 * `streak_practice_today` дедуплит типы практик за день (один тип — один «огонёк»).
 * При переходе через полночь (по [StreakEntity.lastFireDateMs]) репозиторий сбрасывает
 * счётчик за день и очищает накопленные `practice_today`-строки, чтобы дедуп и
 * [StreakData.completedTypesToday] отражали именно сегодняшний день.
 *
 * @param progressDao Room-DAO таблиц прогресса.
 */
class ProgressRepositoryImpl @Inject constructor(
    private val progressDao: ProgressDao,
) : ProgressRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val setSerializer = SetSerializer(String.serializer())
    private val listSerializer = ListSerializer(String.serializer())

    // ── Streak ──────────────────────────────────────────────────────────────────

    override suspend fun getStreak(langId: LanguageId): StreakData? {
        val entity = progressDao.getStreak(langId.value) ?: return null
        return toStreakData(entity, langId.value)
    }

    override fun observeStreak(langId: LanguageId): Flow<StreakData?> =
        progressDao.observeStreak(langId.value).map { entity ->
            entity?.let { toStreakData(it, langId.value) }
        }

    /**
     * Зафиксировать завершение практики и вернуть свежую серию.
     *
     * Пересчитывает серию (current/longest) по правилам v1 (см. KDoc класса),
     * обновляет счётчик «огоньков» за день (дедуп по типу практики) и общее число
     * завершённых подуроков, затем атомарно персистит всё через
     * [ProgressDao.recordPracticeCompletion]. При переходе через полночь очищает
     * устаревшие `practice_today`-строки.
     */
    override suspend fun recordPracticeCompletion(
        langId: LanguageId,
        type: PracticeType,
        nowMs: Long,
    ): StreakData {
        val lang = langId.value
        val typeStr = type.name
        val nowDay = epochDay(nowMs)

        val existing = progressDao.getStreak(lang)

        // Дедуп «огоньков»: тип уже засчитан сегодня? Проверяем только если
        // последний «огонёк» был в тот же день — иначе строки устарели.
        val sameFireDay = existing?.lastFireDateMs?.let { epochDay(it) == nowDay } ?: false
        val alreadyDoneToday = sameFireDay &&
            progressDao.getPracticeToday(lang).any { it.practiceType == typeStr }

        // ── Серия дней ──
        val prevCurrent = existing?.currentStreak ?: 0
        val lastCompletion = existing?.lastCompletionDateMs
        val newCurrent = when {
            lastCompletion == null -> 1
            else -> when (val dayDiff = nowDay - epochDay(lastCompletion)) {
                0L -> prevCurrent        // тот же день — без изменений
                1L -> prevCurrent + 1    // подряд — +1
                else -> 1                // разрыв ≥ 2 (или сдвиг часов назад) — сброс
            }
        }
        val newLongest = maxOf(existing?.longestStreak ?: 0, newCurrent)

        // ── «Огоньки» за день ──
        // База счётчика: сохраняем прошлый, если всё ещё тот же «огненный» день,
        // иначе начинаем с нуля (новый день). Новый «огонёк» зажигается, только
        // если тип практики ещё не выполнялся сегодня.
        val fireBase = if (sameFireDay) existing?.todayFireCount ?: 0 else 0
        val newFireCount = if (alreadyDoneToday) fireBase else fireBase + 1
        val newLastFireMs = if (alreadyDoneToday) existing?.lastFireDateMs else nowMs

        // Переход через полночь: устаревшие practice_today-строки больше не
        // актуальны — очищаем (DAO предоставляет только глобальную очистку;
        // переход суток означает устаревание строк для всех языков).
        if (existing != null && !sameFireDay && existing.lastFireDateMs != null) {
            progressDao.clearPracticeToday()
        }

        val entity = StreakEntity(
            languageId = lang,
            currentStreak = newCurrent,
            longestStreak = newLongest,
            lastCompletionDateMs = nowMs,
            totalSubLessonsCompleted = (existing?.totalSubLessonsCompleted ?: 0) + 1,
            todayFireCount = newFireCount,
            lastFireDateMs = newLastFireMs,
        )
        progressDao.recordPracticeCompletion(lang, typeStr, nowMs, entity)

        return toStreakData(entity, lang)
    }

    // ── Прогресс глав ───────────────────────────────────────────────────────────

    override suspend fun getChapterProgress(packId: PackId, chapterId: ChapterId): ChapterProgress? {
        val entity = progressDao.getChapterProgress(packId.value, chapterId.value) ?: return null
        return ChapterProgress(
            packId = PackId(entity.packId),
            chapterId = ChapterId(entity.chapterId),
            lessonsStarted = entity.lessonsStarted,
            lessonsCompleted = entity.lessonsCompleted,
            lastAccessedMs = entity.lastAccessedMs,
        )
    }

    /**
     * Обновить прогресс главы: инкремент через атомарный SQL
     * ([ProgressDao.incrementChapterProgress]). Если строки ещё нет — создаём её
     * через upsert с начальными значениями (first-touch).
     */
    override suspend fun updateChapterProgress(
        packId: PackId,
        chapterId: ChapterId,
        startedDelta: Int,
        completedDelta: Int,
        nowMs: Long,
    ) {
        val pack = packId.value
        val chapter = chapterId.value
        val touched = progressDao.incrementChapterProgress(
            pack, chapter, startedDelta, completedDelta, nowMs,
        )
        if (touched == 0) {
            progressDao.upsertChapterProgress(
                ChapterProgressEntity(
                    packId = pack,
                    chapterId = chapter,
                    lessonsStarted = maxOf(0, startedDelta),
                    lessonsCompleted = maxOf(0, completedDelta),
                    lastAccessedMs = nowMs,
                ),
            )
        }
    }

    // ── Прогресс drill-тренировок ───────────────────────────────────────────────

    override suspend fun getDrillProgress(packId: PackId, drillType: String): DrillProgress? {
        val entity = progressDao.getDrillProgress(packId.value, drillType) ?: return null
        return DrillProgress(
            packId = PackId(entity.packId),
            drillType = entity.drillType,
            comboKey = entity.comboKey,
            totalCards = entity.totalCards,
            everShownCardIds = jsonToCardIdSet(entity.everShownCardIdsJson),
            todayShownCardIds = jsonToCardIdSet(entity.todayShownCardIdsJson),
            lastDate = entity.lastDate,
            cursor = entity.cursor,
        )
    }

    override suspend fun saveDrillProgress(progress: DrillProgress) {
        progressDao.upsertDrillProgress(
            DrillProgressEntity(
                id = "${progress.packId.value}:${progress.drillType}",
                packId = progress.packId.value,
                drillType = progress.drillType,
                comboKey = progress.comboKey,
                totalCards = progress.totalCards,
                everShownCardIdsJson = cardIdSetToJson(progress.everShownCardIds),
                todayShownCardIdsJson = cardIdSetToJson(progress.todayShownCardIds),
                lastDate = progress.lastDate,
                cursor = progress.cursor,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    // ── Курсор дневной нормы ────────────────────────────────────────────────────

    override suspend fun getDailyCursor(packId: PackId): DailyCursor? {
        val entity = progressDao.getDailyCursor(packId.value) ?: return null
        return DailyCursor(
            packId = PackId(entity.packId),
            sentenceOffset = entity.sentenceOffset,
            currentLessonIndex = entity.currentLessonIndex,
            verbOffset = entity.verbOffset,
            firstSessionDate = entity.firstSessionDate,
            firstSessionSentenceCardIds = jsonToCardIdList(entity.firstSessionSentenceCardIdsJson),
            firstSessionVerbCardIds = jsonToCardIdList(entity.firstSessionVerbCardIdsJson),
            firstSessionLessonId = entity.firstSessionLessonId?.let { LessonId(it) },
        )
    }

    override suspend fun saveDailyCursor(cursor: DailyCursor) {
        progressDao.upsertDailyCursor(
            DailyCursorEntity(
                packId = cursor.packId.value,
                sentenceOffset = cursor.sentenceOffset,
                currentLessonIndex = cursor.currentLessonIndex,
                verbOffset = cursor.verbOffset,
                firstSessionDate = cursor.firstSessionDate,
                firstSessionSentenceCardIdsJson = cardIdListToJson(cursor.firstSessionSentenceCardIds),
                firstSessionVerbCardIdsJson = cardIdListToJson(cursor.firstSessionVerbCardIds),
                firstSessionLessonId = cursor.firstSessionLessonId?.value,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    // ── Маппинг и утилиты ───────────────────────────────────────────────────────

    /** Собрать [StreakData] из сущности, подтянув множество типов практик за день. */
    private suspend fun toStreakData(entity: StreakEntity, langId: String): StreakData {
        val typesToday = progressDao.getPracticeToday(langId)
            .mapNotNull { parsePracticeType(it.practiceType) }
            .toSet()
        return StreakData(
            languageId = LanguageId(entity.languageId),
            currentStreak = entity.currentStreak,
            longestStreak = entity.longestStreak,
            lastCompletionDateMs = entity.lastCompletionDateMs,
            totalSubLessonsCompleted = entity.totalSubLessonsCompleted,
            completedTypesToday = typesToday,
            todayFireCount = entity.todayFireCount,
            lastFireDateMs = entity.lastFireDateMs,
        )
    }

    /** Номер календарного дня (epoch-мс / 86 400 000) — для сравнения «тот же день». */
    private fun epochDay(ms: Long): Long = ms / MILLIS_PER_DAY

    /** Безопасный разбор [PracticeType] по имени; неизвестное значение → null. */
    private fun parsePracticeType(raw: String): PracticeType? =
        runCatching { PracticeType.valueOf(raw) }.getOrNull()

    // ── Сериализация коллекций CardId ↔ JSON ────────────────────────────────────

    private fun cardIdSetToJson(set: Set<CardId>): String =
        json.encodeToString(setSerializer, set.map { it.value }.toSet())

    private fun jsonToCardIdSet(raw: String): Set<CardId> =
        raw.takeUnless { it.isBlank() }
            ?.let { json.decodeFromString(setSerializer, it).map { CardId(it) }.toSet() }
            ?: emptySet()

    private fun cardIdListToJson(list: List<CardId>): String =
        json.encodeToString(listSerializer, list.map { it.value })

    private fun jsonToCardIdList(raw: String): List<CardId> =
        raw.takeUnless { it.isBlank() }
            ?.let { json.decodeFromString(listSerializer, it).map { CardId(it) } }
            ?: emptyList()

    private companion object {
        private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
    }
}
