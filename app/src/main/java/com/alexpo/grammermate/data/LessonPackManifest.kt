package com.alexpo.grammermate.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lists drill content files declared in a lesson pack manifest.
 * Used for optional `verbDrill` and `vocabDrill` sections.
 */
data class DrillFiles(val files: List<String>)

data class LessonPackManifest(
    val schemaVersion: Int,
    val packId: String,
    val packVersion: String,
    val language: String,
    val lessons: List<LessonPackLesson>,
    val displayName: String? = null,
    val verbDrill: DrillFiles? = null,
    val vocabDrill: DrillFiles? = null
) {
    companion object {
        fun fromJson(text: String): LessonPackManifest {
            val json = JSONObject(text)
            val schemaVersion = json.optInt("schemaVersion", -1)
            if (schemaVersion != 1) error("Unsupported schemaVersion: $schemaVersion")
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
                val drillFile = entry.optString("drillFile").trim().ifBlank { null }
                val type = entry.optString("type", "standard").trim()
                if (lessonId.isBlank() || file.isBlank()) {
                    error("Invalid lesson entry at index $i")
                }
                lessons.add(LessonPackLesson(lessonId, order, title, file, drillFile, type))

            }
            val displayName = json.optString("displayName").trim().ifBlank { null }

            val verbDrill = parseDrillFiles(json.optJSONObject("verbDrill"))
            val vocabDrill = parseDrillFiles(json.optJSONObject("vocabDrill"))

            // Manifest must have at least one lesson, or drill/vocab sections
            val hasStandardLessons = lessons.any { it.type != "verb_drill" }
            if (!hasStandardLessons && verbDrill == null && vocabDrill == null) {
                error("Manifest has no lessons and no drill sections")
            }

            return LessonPackManifest(schemaVersion, packId, packVersion, language, lessons, displayName, verbDrill, vocabDrill)
        }

        private fun parseDrillFiles(obj: JSONObject?): DrillFiles? {
            if (obj == null) return null
            val arr = obj.optJSONArray("files") ?: return null
            val files = (0 until arr.length()).mapNotNull { arr.optString(it)?.trim()?.ifBlank { null } }
            if (files.isEmpty()) return null
            return DrillFiles(files)
        }
    }
}

data class LessonPackLesson(
    val lessonId: String,
    val order: Int,
    val title: String?,
    val file: String,
    val drillFile: String? = null,
    val type: String = "standard"

)
