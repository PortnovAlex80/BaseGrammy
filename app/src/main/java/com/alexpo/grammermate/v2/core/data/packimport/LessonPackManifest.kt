package com.alexpo.grammermate.v2.core.data.packimport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * JSON-манифест языкового пака (schema v1 / v2).
 *
 * Перенесён 1:1 из legacy (`com.alexpo.grammermate.data.LessonPackManifest`) по
 * контракту SRS-002 FR-2 / §5.2 (semantics `org.json.opt*` воспроизведены через
 * kotlinx.serialization — pure Kotlin, тестируется на чистом JVM). Валидация:
 * - [schemaVersion] **только 1 или 2**; иначе `error("Unsupported schemaVersion")`.
 * - [packId]/[packVersion]/[language] — обязательны (blank → error).
 * - v1: хотя бы один standard lesson, либо `verbDrill`/`vocabDrill`/`backgroundVocab`;
 *   иначе `error("Schema v1 manifest has no lessons, no drill sections, and no backgroundVocab")`.
 * - v2: хотя бы одна глава с непустым `lessons`, либо drill-секции, либо backgroundVocab;
 *   иначе `error("Schema v2 manifest has no chapter content, no drill sections, and no backgroundVocab")`.
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
         * Распарсить JSON-текст манифеста с валидацией (regression-locked, legacy 1:1).
         *
         * 1. `schemaVersion` ∈ {1, 2} (иначе null — фикс аудита M-2: typed
         *    отказ вместо IllegalStateException, ронявшего весь import-путь).
         * 2. `packId`/`packVersion`/`language` непусты (иначе null).
         * 3. `lessons` (v1), `chapters` (v2), drill-секции, backgroundVocab.
         * 4. Content-валидация: у манифеста должен быть контент (см. KDoc класса).
         */
        fun fromJson(text: String): LessonPackManifest? {
            // Фикс M-2: битый JSON и невалидные поля — null, а не исключение.
            val json = runCatching { Json.parseToJsonElement(text).jsonObject }
                .getOrNull() ?: return null

            val schemaVersion = json.optInt("schemaVersion", -1)
            if (schemaVersion != 1 && schemaVersion != 2) {
                return null
            }
            val packId = json.optString("packId").trim()
            val packVersion = json.optString("packVersion").trim()
            val language = json.optString("language").trim()
            if (packId.isBlank() || packVersion.isBlank() || language.isBlank()) {
                return null
            }

            val lessonsJson = json.optJSONArray("lessons") ?: JsonArray(emptyList())
            val lessons = mutableListOf<LessonPackLesson>()
            for (i in 0 until lessonsJson.size) {
                val entry = lessonsJson[i] as? JsonObject ?: continue
                val lessonId = entry.optString("lessonId").trim()
                val file = entry.optString("file").trim()
                val order = entry.optInt("order", i + 1)
                val title = entry.optString("title").trim().ifBlank { null }
                val type = entry.optString("type", "standard").trim()
                if (lessonId.isBlank() || file.isBlank()) {
                    error("Invalid lesson entry at index $i")
                }
                val tensesArray = entry.optJSONArray("tenses")
                val tenses = if (tensesArray != null) {
                    tensesArray.mapNotNull {
                        (it as? JsonElement)?.strOrNull()?.trim()?.ifBlank { null }
                    }
                } else {
                    emptyList()
                }
                val grammarChip = entry.optString("grammarChip").trim().ifBlank { null }
                lessons.add(LessonPackLesson(lessonId, order, title, file, type, tenses, grammarChip))
            }

            val displayName = json.optString("displayName").trim().ifBlank { null }
            val verbDrill = parseDrillFiles(json.optJSONObject("verbDrill"))
            val vocabDrill = parseDrillFiles(json.optJSONObject("vocabDrill"))
            val backgroundVocab = parseBackgroundVocab(json.optJSONObject("backgroundVocab"))

            // Chapters — только schema v2.
            val chapters = if (schemaVersion == 2) {
                parseChapters(json.optJSONArray("chapters"))
            } else {
                emptyList()
            }

            when (schemaVersion) {
                1 -> {
                    val hasStandardLessons = lessons.any { it.type != "verb_drill" }
                    if (!hasStandardLessons && verbDrill == null && vocabDrill == null && backgroundVocab == null) {
                        error("Schema v1 manifest has no lessons, no drill sections, and no backgroundVocab")
                    }
                }
                2 -> {
                    val hasChapterContent = chapters.any { it.lessons.isNotEmpty() }
                    if (!hasChapterContent && verbDrill == null && vocabDrill == null && backgroundVocab == null) {
                        error("Schema v2 manifest has no chapter content, no drill sections, and no backgroundVocab")
                    }
                }
            }

            return LessonPackManifest(
                schemaVersion = schemaVersion,
                packId = packId,
                packVersion = packVersion,
                language = language,
                lessons = lessons,
                displayName = displayName,
                verbDrill = verbDrill,
                vocabDrill = vocabDrill,
                backgroundVocab = backgroundVocab,
                chapters = chapters,
            )
        }

        // ── opt*-хелперы в семантике org.json (missing → default) ─────────────

        private fun JsonObject.optString(key: String, default: String = ""): String =
            this[key]?.strOrNull() ?: default

        private fun JsonObject.optInt(key: String, default: Int): Int =
            (this[key] as? JsonPrimitive)?.intOrNull ?: default

        private fun JsonElement.strOrNull(): String? =
            (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

        private fun JsonObject.optJSONArray(key: String): JsonArray? =
            this[key] as? JsonArray

        private fun JsonObject.optJSONObject(key: String): JsonObject? =
            this[key] as? JsonObject

        // ── Секции манифеста ──────────────────────────────────────────────────

        private fun parseBackgroundVocab(obj: JsonObject?): BackgroundVocabSection? {
            if (obj == null) return null
            val file = obj.optString("file").trim().ifBlank { null } ?: return null
            val audioDir = obj.optString("audioDir").trim().ifBlank { null }
            val defaultLanguage = obj.optString("defaultLanguage").trim().ifBlank { "it" }
            val translationLanguage = obj.optString("translationLanguage").trim().ifBlank { "ru" }
            return BackgroundVocabSection(file, audioDir, defaultLanguage, translationLanguage)
        }

        private fun parseDrillFiles(obj: JsonObject?): DrillFiles? {
            if (obj == null) return null
            val arr = obj.optJSONArray("files") ?: return null
            val files = arr.mapNotNull { it.strOrNull()?.trim()?.ifBlank { null } }
            if (files.isEmpty()) return null
            return DrillFiles(files)
        }

        private fun parseChapters(chaptersJson: JsonArray?): List<ManifestChapter> {
            if (chaptersJson == null) return emptyList()
            val chapters = mutableListOf<ManifestChapter>()
            for (i in 0 until chaptersJson.size) {
                val entry = chaptersJson[i] as? JsonObject ?: continue
                val chapterId = entry.optString("chapterId").trim()
                val title = entry.optString("title").trim()
                val order = entry.optInt("order", i)
                val subtitle = entry.optString("subtitle").trim().ifBlank { null }
                val storyFile = entry.optString("storyFile").trim().ifBlank { null }
                if (chapterId.isBlank() || title.isBlank()) {
                    error("Invalid chapter entry at index $i: missing chapterId or title")
                }
                val lessonsArr = entry.optJSONArray("lessons") ?: JsonArray(emptyList())
                val lessons = lessonsArr.mapNotNull { it.strOrNull()?.trim()?.ifBlank { null } }
                chapters.add(ManifestChapter(chapterId, order, title, subtitle, storyFile, lessons))
            }
            return chapters
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
