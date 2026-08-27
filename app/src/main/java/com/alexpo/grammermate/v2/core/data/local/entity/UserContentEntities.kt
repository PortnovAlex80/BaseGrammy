package com.alexpo.grammermate.v2.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entities пользовательского контента — данные, создаваемые самим пользователем:
 * скрытые карточки, «плохие» предложения, фоновый словарь, история помодоро,
 * флаги миграции. Все таблицы read-write, дополняют импортируемый контент.
 *
 * @see <a href="../../../../../../../../docs/v2-architecture/ROOM_SCHEMA.md">ROOM_SCHEMA.md</a>
 */

/**
 * Скрытая пользователем карточка (исключается из выдачи тренировок).
 *
 * @property cardId     скрытая карточка (PK).
 * @property hiddenAtMs epoch-мс момента скрытия.
 */
@Entity(tableName = "hidden_cards", primaryKeys = ["packId", "cardId"], indices = [Index("cardId")])
data class HiddenCardEntity(
    val packId: String,
    val cardId: String,
    val hiddenAtMs: Long,
)

/**
 * «Плохое» предложение — карточка, отмеченная пользователем как проблемная.
 *
 * Составной PK (packId + cardId); индекс [cardId] — для отчётов по карте.
 *
 * @property packId      пак карточки.
 * @property cardId      карточка.
 * @property languageId  язык.
 * @property sentence    целевое предложение.
 * @property translation перевод.
 * @property mode        режим/контекст, в котором отметили (training / verb_drill).
 * @property addedAtMs   epoch-мс добавления в список.
 */
@Entity(
    tableName = "bad_sentences",
    primaryKeys = ["packId", "cardId"],
    indices = [Index("cardId")]
)
data class BadSentenceEntity(
    val packId: String,
    val cardId: String,
    val languageId: String,
    val sentence: String,
    val translation: String,
    val mode: String,
    val addedAtMs: Long,
)

/**
 * Пометка фонового словарного слова пользователем.
 *
 * Уникальный индекс по [word] — одна пометка на слово.
 *
 * @property word       слово как есть из словаря (PK).
 * @property mark       тип пометки (NONE / GREEN / RED).
 * @property updatedAtMs epoch-мс последнего обновления пометки.
 */
@Entity(tableName = "bg_vocab_marks", indices = [Index("word", unique = true)])
data class BgVocabMarkEntity(
    @PrimaryKey val word: String,
    val mark: String,
    val updatedAtMs: Long,
)

/**
 * Позиция в фоновом словаре — курсор последовательного просмотра слов.
 *
 * @property key        ключ позиции (например, "default"), PK.
 * @property word       текущее слово.
 * @property updatedAtMs epoch-мс последнего обновления.
 */
@Entity(tableName = "bg_vocab_position")
data class BgVocabPositionEntity(
    @PrimaryKey val key: String,
    val word: String,
    val updatedAtMs: Long,
)

/**
 * Запись истории помодоро-сессии — для графиков и статистики.
 *
 * Поля повторяют доменную [com.alexpo.grammermate.domain.model.PomodoroHistoryEntry];
 * индекс [completedAtMs] поддерживает временны́е выборки.
 *
 * @property id               идентификатор записи (PK).
 * @property languageId       язык тренировки.
 * @property packId           пак тренировки, либо null.
 * @property lessonId         урок тренировки, либо null.
 * @property completedAtMs    epoch-мс завершения помодоро.
 * @property durationMinutes  плановая длительность в минутах.
 * @property totalSeconds     фактическое полное время в секундах.
 * @property remainingSeconds сколько секунд осталось на таймере.
 * @property cardsShown       показано карточек.
 * @property cardsCorrect     правильных ответов.
 * @property cardsIncorrect   неправильных ответов.
 * @property wordsPerMinute   темп набора (слов/мин).
 */
@Entity(tableName = "pomodoro_history", indices = [Index("completedAtMs")])
data class PomodoroHistoryEntity(
    @PrimaryKey val id: String,
    val languageId: String,
    val packId: String?,
    val lessonId: String?,
    val completedAtMs: Long,
    val durationMinutes: Int,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val cardsShown: Int,
    val cardsCorrect: Int,
    val cardsIncorrect: Int,
    val wordsPerMinute: Double,
)

/**
 * Флаг миграции файла — однократная пометка выполненной миграции.
 *
 * @property key         ключ мигрируемого ресурса (PK).
 * @property done        выполнена ли миграция.
 * @property migratedAtMs epoch-мс выполнения.
 */
@Entity(tableName = "migratable_files")
data class MigrationFlagEntity(
    @PrimaryKey val key: String,
    val done: Boolean,
    val migratedAtMs: Long,
)
