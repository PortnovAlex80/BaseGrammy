package com.alexpo.grammermate.v2.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entities освоенности уроков (mastery / SRS-прогресс).
 *
 * Хранит агрегированное состояние интервального повторения по урокам:
 * счётчики показов, SRS/FSRS-состояние, дату следующего показа ([dueAtMs]).
 * Уникальный индекс (packId, lessonId) обеспечивает точечный lookup;
 * индекс [dueAtMs] — эффективную SRS-выборку «что повторить сейчас».
 *
 * @see <a href="../../../../../../../../docs/v2-architecture/ROOM_SCHEMA.md">ROOM_SCHEMA.md</a>
 */

/**
 * Освоенность одного урока — SRS-состояние и счётчики.
 *
 * @property id                 составной ключ "<packId>:<lessonId>".
 * @property packId             пак урока.
 * @property lessonId           урок.
 * @property uniqueCardShows    сколько уникальных карточек показано.
 * @property totalCardShows     всего показов (с повторами).
 * @property lastShowDateMs     epoch-мс последнего показа любой карточки урока.
 * @property intervalStepIndex  индекс шага интервала в INTERVAL_LADDER_DAYS (legacy).
 * @property fsrsStateJson      FSRS Card state (JSON), либо null.
 * @property dueAtMs            ★ epoch-мс, когда урок снова доступен (0 — доступен); индекс SRS.
 * @property completedAtMs      epoch-мс завершения урока, либо null.
 */
@Entity(
    tableName = "mastery_states",
    indices = [Index(value = ["packId", "lessonId"], unique = true), Index("dueAtMs")]
)
data class MasteryStateEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val lessonId: String,
    val uniqueCardShows: Int,
    val totalCardShows: Int,
    val lastShowDateMs: Long,
    val intervalStepIndex: Int,
    val fsrsStateJson: String?,
    val dueAtMs: Long,
    val completedAtMs: Long?,
)

/**
 * Показанная карточка урока — множество уникально показанных.
 *
 * Составной PK (packId + lessonId + cardId) делает запись уникальной по уроку
 * и карточке. Дополняет [MasteryStateEntity] детализацией до карточки.
 *
 * @property packId   пак.
 * @property lessonId урок.
 * @property cardId   показанная карточка.
 */
@Entity(
    tableName = "shown_cards",
    primaryKeys = ["packId", "lessonId", "cardId"],
    indices = [Index("cardId")]
)
data class ShownCardEntity(
    val packId: String,
    val lessonId: String,
    val cardId: String,
)

/**
 * Счётчик встреч карточки — сколько раз карточка показана в уроке.
 *
 * Составной PK (packId + lessonId + cardId). [count] отражает повторные показы
 * (включая неверные ответы), дополняя множество [ShownCardEntity].
 *
 * @property packId   пак.
 * @property lessonId урок.
 * @property cardId   карточка.
 * @property count    число встреч карточки.
 */
@Entity(
    tableName = "card_encounters",
    primaryKeys = ["packId", "lessonId", "cardId"]
)
data class CardEncounterEntity(
    val packId: String,
    val lessonId: String,
    val cardId: String,
    val count: Int,
)
