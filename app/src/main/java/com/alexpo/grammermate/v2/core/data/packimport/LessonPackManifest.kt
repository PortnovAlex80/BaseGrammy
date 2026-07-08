package com.alexpo.grammermate.v2.core.data.packimport

/**
 * JSON-манифест языкового пака (schema v1 / v2).
 *
 * Переносится из legacy (`com.alexpo.grammermate.data.LessonPackManifest`) 1:1
 * по контракту SRS-002 FR-2 / §5.2. Валидация:
 * - [schemaVersion] **только 1 или 2**; иначе `error("Unsupported schemaVersion")`.
 * - [packId]/[packVersion]/[language] — обязательны (blank → error).
 * - v1: хотя бы один standard lesson, либо `verbDrill`/`vocabDrill`/`backgroundVocab`;
 *   иначе `error("Schema v1 manifest has no lessons, no drill sections, and no backgroundVocab")`.
 * - v2: хотя бы одна глава с непустым `lessons`, либо drill-секции, либо backgroundVocab;
 *   иначе `error("Schema v2 manifest has no chapter content, no drill sections, and no backgroundVocab")`.
 *
 * SCAFFOLD: модели данных определены (контракт зафиксирован SRS-002 §5.2);
 * парсер [fromJson] — TODO (AC-4 / AC-5 / AC-6). Тело `packimport/` реализуется
 * в body-задачах E02 внутри этого фиксированного контракта.
 *
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/02-srs.md">SRS-002 §5.2</a>
 */
data class LessonPackManifest(
    val schemaVersion: Int,                  // 1 или 2 (иначе error)
    val packId: String,
    val packVersion: String,
    val language: String,
    val lessons: List<LessonPackLesson>,     // v1 root-level
    val displayName: String? = null,
    val verbDrill: DrillFiles? = null,
    val vocabDrill: DrillFiles? = null,
    val backgroundVocab: BackgroundVocabSection? = null,
    val chapters: List<ManifestChapter> = emptyList(),   // v2 only
) {
    companion object {
        /**
         * Распарсить JSON-текст манифеста с валидацией (regression-locked).
         *
         * SCAFFOLD TODO (AC-4 / AC-5 / AC-6): реализовать парсинг v1/v2 с валидацией.
         * Переносится 1:1 из legacy `LessonPackManifest.fromJson` — без изменения
         * утверждений валидации (NFR-6). Реализация должна:
         * 1. Проверить `schemaVersion` ∈ {1, 2} (иначе `error("Unsupported schemaVersion: $sv")`).
         * 2. Проверить `packId`/`packVersion`/`language` непусты (иначе `error("Missing packId/packVersion/language")`).
         * 3. Распарсить `lessons` (v1), `chapters` (v2), drill-секции, backgroundVocab.
         * 4. Применить content-валидацию (см. KDoc класса / SRS-002 FR-2).
         */
        fun fromJson(text: String): LessonPackManifest {
            TODO("AC-4 / AC-5 / AC-6: реализовать LessonPackManifest.fromJson (v1/v2 валидация, regression-locked)")
        }
    }
}

/**
 * Одна запись урока в root-level массиве `lessons` (manifest v1).
 *
 * @property lessonId стабильный идентификатор урока (PK в Room `lessons`).
 * @property order позиция урока в паке (для канонической сортировки).
 * @property title человекочитаемое имя, либо null (тогда берётся из CSV-заголовка).
 * @property file путь к CSV-файлу урока относительно корня пака.
 * @property type `"standard"` (перевод) либо `"verb_drill"` (спряжение).
 * @property tenses список времён для verb_drill-урока (фильтр карточек).
 * @property grammarChip ключ грамматической справки (chip), либо null.
 */
data class LessonPackLesson(
    val lessonId: String,
    val order: Int,
    val title: String?,
    val file: String,
    val type: String = "standard",
    val tenses: List<String> = emptyList(),
    val grammarChip: String? = null,
)

/**
 * Секция drill-файлов (`verbDrill` / `vocabDrill`) в манифесте.
 *
 * @property files список путей к CSV drill-файлам относительно корня пака.
 */
data class DrillFiles(val files: List<String>)

/**
 * Секция `backgroundVocab` — pack-scoped фоновый вокаб (foreground service E08).
 *
 * @property file путь к BgVocab CSV относительно корня пака.
 * @property audioDir директория предрендеренных `.wav` клипов (null — TTS-only).
 * @property defaultLanguage BCP-47-ish тег целевого языка (по умолчанию `"it"`).
 * @property translationLanguage BCP-47-ish тег языка перевода (по умолчанию `"ru"`).
 */
data class BackgroundVocabSection(
    val file: String,
    val audioDir: String?,
    val defaultLanguage: String = "it",
    val translationLanguage: String = "ru",
)

/**
 * Глава манифеста — нарративный блок уроков (только schema v2).
 *
 * Внимание: это **манифест-модель** (плоская), а не доменная
 * `com.alexpo.grammermate.v2.core.domain.model.Chapter` (с value-class ID и `packId`).
 * Маппинг manifest-chapter → `ChapterEntity` делает [PackImporter].
 *
 * @property chapterId стабильный идентификатор главы.
 * @property order позиция главы в паке (для канонической сортировки).
 * @property title заголовок главы.
 * @property subtitle подзаголовок, либо null.
 * @property storyFile путь к story-файлу главы, либо null.
 * @property lessons идентификаторы уроков главы в порядке прохождения.
 */
data class ManifestChapter(
    val chapterId: String,
    val order: Int,
    val title: String,
    val subtitle: String?,
    val storyFile: String?,
    val lessons: List<String>,
)

/**
 * Доменная сводка импортированного пака (возвращается в [PackImportResult.Success]
 * и [PackImportResult.Partial]).
 *
 * Отдельная модель от `LessonPackManifest`: manifest — это **вход** (что в JSON),
 * а `LessonPack` — **выход** (что физически попало в Room: packId + version +
 * importedAt + displayName). Содержит только метаданные пака, без уроков/карт —
 * они персистятся в Room и доступны через `ContentRepository`.
 *
 * @property packId стабильный ID пака.
 * @property packVersion версия контента.
 * @property languageId язык пака.
 * @property importedAtMs epoch-мс момента импорта.
 * @property displayName человекочитаемое имя, либо null.
 */
data class LessonPack(
    val packId: String,
    val packVersion: String,
    val languageId: String,
    val importedAtMs: Long,
    val displayName: String? = null,
)
