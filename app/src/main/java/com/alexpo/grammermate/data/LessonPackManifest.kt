package com.alexpo.grammermate.data

import org.json.JSONArray
import org.json.JSONObject

data class LessonPackManifest(
    val schemaVersion: Int,
    val packId: String,
    val packVersion: String,
    val language: String,
    val lessons: List<LessonPackLesson>
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
                if (lessonId.isBlank() || file.isBlank()) {
                    error("Invalid lesson entry at index $i")
                }
                lessons.add(LessonPackLesson(lessonId, order, title, file))
            }
            if (lessons.isEmpty()) error("Manifest has no lessons")
            return LessonPackManifest(schemaVersion, packId, packVersion, language, lessons)
        }
    }
}

data class LessonPackLesson(
    val lessonId: String,
    val order: Int,
    val title: String?,
    val file: String
)
