package com.alexpo.grammermate.v2.core.data.repository

import com.alexpo.grammermate.v2.core.data.local.dao.MasteryDao
import com.alexpo.grammermate.v2.core.data.local.entity.MasteryStateEntity
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.LessonMastery
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.PackLessonProgress
import com.alexpo.grammermate.domain.repository.MasteryRepository
import com.alexpo.grammermate.domain.srs.SrsCardState
import com.alexpo.grammermate.domain.srs.SrsMemoryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * Data-слой освоения уроков и интервального повторения (SRS).
 *
 * Реализация [MasteryRepository] поверх [MasteryDao]. Отвечает за маппинг
 * Room-сущностей в доменные модели ([LessonMastery]) и обратно, а также за
 * сериализацию агрегированного SRS-состояния ([SrsCardState]) в JSON-колонку
 * [MasteryStateEntity.fsrsStateJson].
 *
 * ## Сериализация SrsCardState
 * [SrsCardState] — чистый доменный класс без `@Serializable` (по дизайну:
 * domain остаётся свободным от wire-формата). Поэтому JSON строится и читается
 * вручную через [JsonObject]. При этом сознательно используется **имя** enum'а
 * [SrsMemoryState] (а не ordinal) — как требует KDoc домена: это стабильный
 * wire-формат, переживший бы переупорядочивание значений.
 *
 * ## Уровень детализации mastery
 * В схеме v2 mastery агрегируется **по уроку** ([MasteryStateEntity] индексируется
 * по `packId`+`lessonId`), а детализация до карточки живёт в `shown_cards` и
 * `card_encounters`. Поэтому [getDueCards] отдаёт один элемент на просроченный
 * урок (см. KDoc метода).
 *
 * @param masteryDao Room-DAO mastery/SRS-таблиц.
 */
class MasteryRepositoryImpl @Inject constructor(
    private val masteryDao: MasteryDao,
) : MasteryRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun getMastery(packId: PackId, lessonId: LessonId): LessonMastery? =
        masteryDao.get(packId.value, lessonId.value)?.let { toDomain(it) }

    override fun observeMastery(packId: PackId, lessonId: LessonId): Flow<LessonMastery?> =
        masteryDao.observe(packId.value, lessonId.value).map { entity ->
            entity?.let { toDomain(it) }
        }

    /**
     * Зафиксировать показ карточки и вернуть свежее состояние освоения.
     *
     * Пересчитывает денормализованные счётчики ([MasteryStateEntity.uniqueCardShows] /
     * [MasteryStateEntity.totalCardShows]) и фиксирует показ в одной атомарной
     * транзакции [MasteryDao.recordCardShow] (mastery + shown-set + encounters).
     * `dueAtMs` берётся из актуального SRS-расписания, либо при его отсутствии
     * приравнивается к моменту показа (урок доступен «сейчас»).
     */
    override suspend fun recordCardShow(
        packId: PackId,
        lessonId: LessonId,
        cardId: CardId,
        nowMs: Long,
    ): LessonMastery {
        val pack = packId.value
        val lesson = lessonId.value
        val card = cardId.value

        val existing = masteryDao.get(pack, lesson)
        val isNewCard = if (existing != null) {
            card !in masteryDao.getShownCardIds(pack, lesson).toSet()
        } else {
            true
        }

        val prevSrs = existing?.let { srsFromJson(it.fsrsStateJson) }
        val dueAt = prevSrs?.dueAtMs?.takeIf { it > 0L } ?: nowMs

        val updated = MasteryStateEntity(
            id = "$pack:$lesson",
            packId = pack,
            lessonId = lesson,
            uniqueCardShows = (existing?.uniqueCardShows ?: 0) + if (isNewCard) 1 else 0,
            totalCardShows = (existing?.totalCardShows ?: 0) + 1,
            lastShowDateMs = nowMs,
            // legacy-лестница не пересчитывается на показе: FSRS-состояние
            // (srsState) стало источником истины; шаг хранится для миграции.
            intervalStepIndex = existing?.intervalStepIndex ?: 0,
            fsrsStateJson = existing?.fsrsStateJson,
            dueAtMs = dueAt,
            completedAtMs = existing?.completedAtMs,
        )

        masteryDao.recordCardShow(pack, lesson, card, nowMs, updated)
        return toDomain(updated)
    }

    override suspend fun markLessonCompleted(packId: PackId, lessonId: LessonId, nowMs: Long) {
        masteryDao.markCompleted(packId.value, lessonId.value, nowMs)
    }

    /**
     * Карточки к повторению: один элемент на просроченный урок.
     *
     * Mastery агрегировано по уроку, поэтому [MasteryDao.dueCards] отбирает
     * lesson-строки. Каждый урок кодируется в `CardId` составным ключом
     * `"<packId>:<lessonId>"`, по которому вышестоящий слой (UseCase/UI)
     * восстанавливает пакет и урок для запуска режима Review.
     */
    override suspend fun getDueCards(nowMs: Long, limit: Int): List<CardId> =
        masteryDao.dueCards(nowMs, limit).map { entity ->
            CardId("${entity.packId}:${entity.lessonId}")
        }

    override suspend fun updateSrsState(packId: PackId, lessonId: LessonId, srs: SrsCardState) {
        val existing = masteryDao.get(packId.value, lessonId.value) ?: return
        masteryDao.upsert(
            existing.copy(
                fsrsStateJson = srsToJson(srs),
                dueAtMs = srs.dueAtMs,
            ),
        )
    }

    /** ADR-002 слой 1: агрегат из `completedAtMs`, без SRS-математики. */
    override fun observePackProgress(): Flow<List<PackLessonProgress>> =
        masteryDao.observePackProgress().map { rows ->
            rows.map { row ->
                PackLessonProgress(
                    packId = PackId(row.packId),
                    totalLessons = row.totalLessons,
                    completedLessons = row.completedLessons,
                )
            }
        }

    // ── Маппинг сущность → домен ──────────────────────────────────────────────

    /**
     * Собрать [LessonMastery] из [entity], подтянув множество показанных
     * карточек (`shown_cards`) и счётчики встреч (`card_encounters`).
     */
    private suspend fun toDomain(entity: MasteryStateEntity): LessonMastery {
        val pack = entity.packId
        val lesson = entity.lessonId
        val shownIds = masteryDao.getShownCardIds(pack, lesson).map { CardId(it) }.toSet()
        val encounters = masteryDao.getEncounters(pack, lesson)
            .associate { CardId(it.cardId) to it.count }
        return LessonMastery(
            packId = PackId(pack),
            lessonId = LessonId(lesson),
            uniqueCardShows = entity.uniqueCardShows,
            totalCardShows = entity.totalCardShows,
            lastShowDateMs = entity.lastShowDateMs,
            intervalStepIndex = entity.intervalStepIndex,
            srsState = srsFromJson(entity.fsrsStateJson),
            dueAtMs = entity.dueAtMs,
            completedAtMs = entity.completedAtMs,
            shownCardIds = shownIds,
            cardEncounterCounts = encounters,
        )
    }

    // ── Сериализация SrsCardState ↔ JSON ──────────────────────────────────────

    /**
     * Упаковать [SrsCardState] в JSON-строку колонки `fsrsStateJson`.
     *
     * Формат — плоский объект с числовыми полями и `state` как **именем** enum'а
     * (не ordinal) — стабильный wire-формат согласно KDoc [SrsMemoryState].
     */
    private fun srsToJson(state: SrsCardState): String {
        val obj: JsonObject = buildJsonObject {
            put("stability", state.stability)
            put("difficulty", state.difficulty)
            put("lastReviewMs", state.lastReviewMs)
            put("reps", state.reps)
            put("lapses", state.lapses)
            put("state", state.state.name)
            put("dueAtMs", state.dueAtMs)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    /**
     * Разобрать [raw] в [SrsCardState]. Толерантен к отсутствующим/лишним полям
     * и повреждённым данным: возвращает `null`, чтобы слой выше трактовал урок
     * как «без SRS-истории» (эквивалент NEW).
     */
    private fun srsFromJson(raw: String?): SrsCardState? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = json.decodeFromString(JsonObject.serializer(), raw)
            SrsCardState(
                stability = obj["stability"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                difficulty = obj["difficulty"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                lastReviewMs = obj["lastReviewMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                reps = obj["reps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                lapses = obj["lapses"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                state = obj["state"]?.jsonPrimitive?.content
                    ?.let { name -> runCatching { SrsMemoryState.valueOf(name) }.getOrNull() }
                    ?: SrsMemoryState.NEW,
                dueAtMs = obj["dueAtMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
        }.getOrNull()
    }
}
