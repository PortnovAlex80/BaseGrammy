package com.alexpo.grammermate.v2.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey.Companion.CASCADE

/**
 * Entities контента — read-mostly таблицы, заполняемые при pack-import
 * (CSV/YAML/аудио). Содержимое паков (файлы) здесь не хранится — только
 * индексируемые метаданные: паки → главы → уроки → карточки.
 *
 * Идентификаторы (Card IDs и т. п.) строятся как `card_<lineNumber>`
 * (см. CsvParser), поэтому `card_1` НЕ существует — реальные ID урока
 * начинаются с `card_2`. Учитывается при миграции.
 *
 * Enum-поля домена (CardType и т. д.) хранятся здесь как [String];
 * маппинг enum ↔ String выполняется в repository-реализации.
 *
 * @see <a href="../../../../../../../../docs/v2-architecture/ROOM_SCHEMA.md">ROOM_SCHEMA.md</a>
 */

/**
 * Пак контента — корневой импортируемый набор уроков.
 *
 * @property id           стабильный ID пака (ITALIAN_SHORT, EN_WORD_ORDER_A1, ...).
 * @property languageId   язык пака.
 * @property displayName  человекочитаемое имя, либо null.
 * @property version      версия импортированного пака.
 * @property importedAtMs epoch-мс момента импорта.
 */
@Entity(tableName = "packs")
data class PackEntity(
    @PrimaryKey val id: String,
    val languageId: String,
    val displayName: String?,
    val version: String,
    val importedAtMs: Long,
)

/**
 * Глава пака — объединение уроков в нарративный блок (сторителлинг).
 *
 * @property id        ID главы (chapter_2).
 * @property packId    пак главы (FK → [PackEntity], CASCADE при удалении пака).
 * @property order     позиция главы в паке.
 * @property title     заголовок главы.
 * @property subtitle  подзаголовок, либо null.
 * @property storyFile путь к файлу истории, либо null.
 */
@Entity(
    tableName = "chapters",
    primaryKeys = ["packId", "id"],
    foreignKeys = [ForeignKey(entity = PackEntity::class, parentColumns = ["id"], childColumns = ["packId"], onDelete = CASCADE)]
)
data class ChapterEntity(
    val packId: String,
    val id: String,
    val order: Int,
    val title: String,
    val subtitle: String?,
    val storyFile: String?,
)

/**
 * Урок пака — атомарная обучающая единица из карточек.
 *
 * @property id             ID урока (lesson_23_B07).
 * @property packId         пак урока.
 * @property chapterId      глава урока (FK → [ChapterEntity], CASCADE); null для паков
 *                          без глав (manifest v1).
 * @property order          позиция урока.
 * @property title          заголовок урока.
 * @property cefrLevel      уровень CEFR (A1, A2, ...), либо null.
 * @property grammarChipKey ключ грамматического «чипа», либо null.
 */
@Entity(
    tableName = "lessons",
    primaryKeys = ["packId", "id"],
    indices = [Index("chapterId"), Index(value = ["packId", "chapterId"])],
    foreignKeys = [ForeignKey(
        entity = ChapterEntity::class,
        parentColumns = ["packId", "id"],
        childColumns = ["packId", "chapterId"],
        onDelete = CASCADE,
    )]
)
data class LessonEntity(
    val packId: String,
    val id: String,
    val chapterId: String?,
    val order: Int,
    val title: String,
    val cefrLevel: String?,
    val grammarChipKey: String?,
)

/**
 * Карточка — минимальная единица упражнения (предложение, глагол, aux).
 *
 * Не имеет FK на урок (см. ROOM_SCHEMA.md) — связь урок→карточки обеспечивается
 * индексами для выборки. [acceptedAnswersJson] хранится как готовая JSON-строка
 * массива (серилизуется приложением, не Room TypeConverter).
 *
 * @property id                   ID карточки (card_2 … card_15).
 * @property packId               пак карточки.
 * @property lessonId             урок карточки.
 * @property ord                  позиция в CSV (после строки заголовка).
 * @property type                 тип (SENTENCE / VERB_DRILL / AUX_DRILL).
 * @property promptRu             промпт на русском.
 * @property acceptedAnswersJson  JSON-массив принимаемых ответов.
 * @property tense                время глагола, либо null.
 * @property verb                 инфинитив глагола, либо null.
 * @property verbGroup            группа спряжения, либо null.
 * @property person               лицо (1/2/3), либо null.
 * @property frequencyRank        ранг частотности (для sortByFrequency), либо null.
 */
@Entity(
    tableName = "cards",
    primaryKeys = ["packId", "id"],
    indices = [Index("lessonId"), Index(value = ["lessonId", "ord"])]
)
data class CardEntity(
    val packId: String,
    val id: String,
    val lessonId: String,
    val ord: Int,
    val type: String,
    val promptRu: String,
    val acceptedAnswersJson: String,
    val tense: String?,
    val verb: String?,
    val verbGroup: String?,
    val person: String?,
    val frequencyRank: Int?,
)
