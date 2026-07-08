package com.alexpo.grammermate.v2.core.data.packimport

/**
 * Лёгкие промежуточные модели — **вывод парсеров** до маппинга в Room-entity.
 *
 * Это НЕ полные доменные модели (`domain.model.Card` требует `packId`/`lessonId`/
 * `ord`/`type`, которых у парсера на момент чтения CSV ещё нет), и НЕ Room-entities
 * (`CardEntity` требует `packId`/`lessonId`/`acceptedAnswersJson`). Это намеренно
 * отдельный слой: парсер читает CSV → отдаёт `SentenceCard`/`VerbDrillCard`/`VocabRow`,
 * а [PackImporter] в `withTransaction` обогащает их контекстом импорта (packId, lessonId,
 * ord) и маппит в entity.
 *
 * SCAFFOLD: модели зафиксированы как contract для body-задач парсеров (AC-7..AC-11);
 * сами парсеры (`CsvParser`, `VerbDrillCsvParser`, `VocabCsvParser`, `StoryQuizParser`,
 * `BgVocabCsvParser`) — TODO, реализуются в соответствующих AC-задачах E02.
 */

/**
 * Карточка перевода — вывод [CsvParser] (Lesson CSV → SentenceCard → CardEntity).
 *
 * Идентификатор: `"card_$lineNumber"` (lineNumber после строки заголовка;
 * `card_1` НЕ существует — реальные ID начинаются с `card_2`, см. ContentEntities KDoc).
 *
 * @property id `"card_$lineNumber"`.
 * @property promptRu промпт на русском (что перевести).
 * @property acceptedAnswers нормализованные принимаемые ответы.
 * @property tense время глагола, либо null (не для всех уроков).
 */
data class SentenceCard(
    val id: String,
    val promptRu: String,
    val acceptedAnswers: List<String>,
    val tense: String? = null,
)

/**
 * Карточка спряжения — вывод [VerbDrillCsvParser] (Verb CSV → VerbDrillCard → VerbDrillCardEntity).
 *
 * Идентификатор: `"${group ?: ""}_${tense ?: ""}_$dataRowIndex"`.
 *
 * @property id `"${group ?: ""}_${tense ?: ""}_$dataRowIndex"`.
 * @property promptRu промпт на русском.
 * @property answer правильный ответ на целевом языке.
 * @property verb инфинитив глагола, либо null.
 * @property tense время, либо null.
 * @property group группа спряжения, либо null.
 * @property person лицо/число (Io/Tu/...), либо null.
 * @property rank ранг частотности, либо null.
 */
data class VerbDrillCard(
    val id: String,
    val promptRu: String,
    val answer: String,
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null,
    val person: String? = null,
    val rank: Int? = null,
)

/**
 * Строка вокаба — вывод [VocabCsvParser] (Vocab CSV → VocabRow → VocabWordEntity).
 *
 * Идентификатор: `"${pos}_${rank}_${word}"`. PK word-entity; уникальность по `(packId, word)`.
 *
 * @property id `"${pos}_${rank}_${word}"`.
 * @property pos часть речи.
 * @property rank ранг частотности.
 * @property word слово на целевом языке.
 * @property meaningRu перевод на русский.
 * @property collocations коллокации (опционально).
 * @property forms формы слова (опционально).
 * @property isHard пометка «сложное» (3-я колонка CSV = hard|1|true).
 */
data class VocabRow(
    val id: String,
    val pos: String,
    val rank: Int,
    val word: String,
    val meaningRu: String? = null,
    val collocations: List<String> = emptyList(),
    val forms: List<String> = emptyList(),
    val isHard: Boolean = false,
)
