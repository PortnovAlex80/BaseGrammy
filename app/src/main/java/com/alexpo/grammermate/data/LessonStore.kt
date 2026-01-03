package com.alexpo.grammermate.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.UUID

class LessonStore(private val context: Context) {
    private val yaml = Yaml()
    private val lessonsDir = File(context.filesDir, "lessons")

    fun ensureSeedData() {
        if (!lessonsDir.exists()) {
            lessonsDir.mkdirs()
        }
    }

    fun getLanguages(): List<Language> {
        ensureSeedData()
        return listOf(
            Language("en", "English"),
            Language("it", "Italian")
        )
    }

    fun getLessons(languageId: String): List<Lesson> {
        ensureSeedData()
        val indexFile = indexFileFor(languageId)
        if (!indexFile.exists()) return emptyList()
        val entries = yaml.load<List<Map<String, Any>>>(indexFile.readText()) ?: emptyList()
        return entries.mapNotNull { entry ->
            val id = entry["id"] as? String ?: return@mapNotNull null
            val title = entry["title"] as? String ?: "Lesson"
            val fileName = entry["file"] as? String ?: return@mapNotNull null
            val csvFile = File(languageDir(languageId), fileName)
            if (!csvFile.exists()) return@mapNotNull null
            val (parsedTitle, cards) = CsvParser.parseLesson(csvFile.inputStream())
            Lesson(id = id, languageId = languageId, title = parsedTitle ?: title, cards = cards)
        }
    }

    fun importFromUri(languageId: String, uri: Uri, resolver: ContentResolver): Lesson {
        ensureSeedData()
        val input = resolver.openInputStream(uri) ?: error("Cannot open CSV")
        val title = guessFileName(resolver, uri) ?: "Lesson"
        val id = UUID.randomUUID().toString()
        val fileName = "lesson_$id.csv"
        val dir = languageDir(languageId)
        dir.mkdirs()
        val csvFile = File(dir, fileName)
        input.use { stream ->
            csvFile.outputStream().use { out ->
                stream.copyTo(out)
            }
        }
        val (parsedTitle, cards) = CsvParser.parseLesson(csvFile.inputStream())
        val lessonTitle = parsedTitle ?: title
        saveIndex(languageId, LessonIndexEntry(id, lessonTitle, fileName))
        return Lesson(id = id, languageId = languageId, title = lessonTitle, cards = cards)
    }

    fun deleteAllLessons(languageId: String) {
        val dir = languageDir(languageId)
        if (dir.exists()) dir.deleteRecursively()
        val index = indexFileFor(languageId)
        if (index.exists()) index.delete()
    }



    private fun saveIndex(languageId: String, entry: LessonIndexEntry) {
        val indexFile = indexFileFor(languageId)
        val existing = if (indexFile.exists()) {
            yaml.load<List<Map<String, Any>>>(indexFile.readText()) ?: emptyList()
        } else {
            emptyList()
        }
        val newEntries = existing.toMutableList()
        newEntries.add(mapOf("id" to entry.id, "title" to entry.title, "file" to entry.fileName))
        indexFile.parentFile?.mkdirs()
        indexFile.writeText(yaml.dump(newEntries))
    }

    private fun languageDir(languageId: String): File = File(lessonsDir, languageId)

    private fun indexFileFor(languageId: String): File = File(lessonsDir, "${languageId}_index.yaml")

    private fun guessFileName(resolver: ContentResolver, uri: Uri): String? {
        val cursor = resolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex == -1) return null
            return it.getString(nameIndex)
        }
    }

    private data class LessonIndexEntry(
        val id: String,
        val title: String,
        val fileName: String
    )
}
