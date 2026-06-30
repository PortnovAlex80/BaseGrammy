package com.alexpo.grammermate.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lists drill content files declared in a lesson pack manifest.
 * Used for optional `verbDrill` and `vocabDrill` sections.
 */
data class DrillFiles(val files: List<String>)

/**
 * Declares a pack-scoped background-vocab deck for the Background Vocab
 * Listener foreground service.
 *
 * Manifest shape (schema v1 and v2):
 * ```json
 * "backgroundVocab": {
 *   "file": "bg_vocab_12000.csv",
 *   "audioDir": "bg_vocab/audio",
 *   "defaultLanguage": "it",
 *   "translationLanguage": "ru"
 * }
 * ```
 *
 * - [file] — CSV filename (relative to the pack root) following the
 *   [BgVocabCsvParser] schema. Required.
 * - [audioDir] — optional directory (relative to the pack root) of pre-rendered
 *   `.wav` clips. Null means TTS-only playback.
 * - [defaultLanguage] — BCP-47-ish tag of the word/collocation/sentence language
 *   (the language being learned). Defaults to `"it"`.
 * - [translationLanguage] — tag of the parallel translation language. Defaults
 *   to `"ru"`.
 */
data class BackgroundVocabSection(
    val file: String,
    val audioDir: String?,
    val defaultLanguage: String,
    val translationLanguage: String
)

data class LessonPackManifest(
    val schemaVersion: Int,
    val packId: String,
    val packVersion: String,
    val language: String,
    val lessons: List<LessonPackLesson>,
    val displayName: String? = null,
    val verbDrill: DrillFiles? = null,
    val vocabDrill: DrillFiles? = null,
    val backgroundVocab: BackgroundVocabSection? = null,
    val chapters: List<Chapter> = emptyList()
) {
    companion object {
        fun fromJson(text: String): LessonPackManifest {
            val json = JSONObject(text)
            val schemaVersion = json.optInt("schemaVersion", -1)
            if (schemaVersion != 1 && schemaVersion != 2) {
                error("Unsupported schemaVersion: $schemaVersion")
            }
            val packId = json.optString("packId").trim()
            val packVersion = json.optString("packVersion").trim()
            val language = json.optString("language").trim()
            if (packId.isBlank() || packVersion.isBlank() || language.isBlank()) {
                error("Missing packId/packVersion/language")
            }
            val lessonsJson = json.optJSONArray("lessons") ?: JSONArray()
            val lessons = mutableListOf<LessonPackLesson>()
            for (i in 0 until lessonsJson.length()) {
                val entry = lessonsJson.optJSONObject(i) ?: continue
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
                    (0 until tensesArray.length()).mapNotNull { tensesArray.optString(it)?.trim()?.ifBlank { null } }
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

            // Parse chapters (schema v2 only)
            val chapters = if (schemaVersion == 2) {
                parseChapters(json.optJSONArray("chapters"))
            } else {
                emptyList()
            }

            // Validation: Manifest must have content
            // v1: at least one standard lesson OR drill sections OR backgroundVocab
            // v2: at least one chapter with non-empty lessons OR drill sections OR backgroundVocab
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
                chapters = chapters
            )
        }

        private fun parseBackgroundVocab(obj: JSONObject?): BackgroundVocabSection? {
            if (obj == null) return null
            val file = obj.optString("file").trim().ifBlank { null } ?: return null
            val audioDir = obj.optString("audioDir").trim().ifBlank { null }
            val defaultLanguage = obj.optString("defaultLanguage").trim().ifBlank { "it" }
            val translationLanguage = obj.optString("translationLanguage").trim().ifBlank { "ru" }
            return BackgroundVocabSection(
                file = file,
                audioDir = audioDir,
                defaultLanguage = defaultLanguage,
                translationLanguage = translationLanguage
            )
        }

        private fun parseDrillFiles(obj: JSONObject?): DrillFiles? {
            if (obj == null) return null
            val arr = obj.optJSONArray("files") ?: return null
            val files = (0 until arr.length()).mapNotNull { arr.optString(it)?.trim()?.ifBlank { null } }
            if (files.isEmpty()) return null
            return DrillFiles(files)
        }

        private fun parseChapters(chaptersJson: JSONArray?): List<Chapter> {
            if (chaptersJson == null) return emptyList()

            val chapters = mutableListOf<Chapter>()
            for (i in 0 until chaptersJson.length()) {
                val entry = chaptersJson.optJSONObject(i) ?: continue
                val chapterId = entry.optString("chapterId").trim()
                val title = entry.optString("title").trim()
                val order = entry.optInt("order", i)
                val subtitle = entry.optString("subtitle").trim().ifBlank { null }
                val storyFile = entry.optString("storyFile").trim().ifBlank { null }

                if (chapterId.isBlank() || title.isBlank()) {
                    error("Invalid chapter entry at index $i: missing chapterId or title")
                }

                val lessonsJson = entry.optJSONArray("lessons") ?: JSONArray()
                val lessons = mutableListOf<String>()
                for (j in 0 until lessonsJson.length()) {
                    val lessonId = lessonsJson.optString(j).trim()
                    if (lessonId.isNotBlank()) {
                        lessons.add(lessonId)
                    }
                }

                chapters.add(Chapter(chapterId, order, title, subtitle, storyFile, lessons))
            }

            return chapters
        }
    }
}

data class LessonPackLesson(
    val lessonId: String,
    val order: Int,
    val title: String?,
    val file: String,
    val type: String = "standard",
    val tenses: List<String> = emptyList(),
    val grammarChip: String? = null
)
