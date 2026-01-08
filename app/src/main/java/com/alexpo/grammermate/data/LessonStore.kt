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
    private val baseDir = File(context.filesDir, "grammarmate")
    private val lessonsDir = File(baseDir, "lessons")
    private val languagesFile = File(lessonsDir, "languages.yaml")
    private val languagesStore = YamlListStore(yaml, languagesFile)

    fun ensureSeedData() {
        if (!lessonsDir.exists()) {
            lessonsDir.mkdirs()
        }
        if (!languagesFile.exists()) {
            val defaults = listOf(
                mapOf("id" to "en", "name" to "English"),
                mapOf("id" to "it", "name" to "Italian")
            )
            languagesStore.write(defaults)
        }
    }

    fun getLanguages(): List<Language> {
        ensureSeedData()
        val entries = languagesStore.read()
        return entries.mapNotNull { entry ->
            val id = entry["id"] as? String ?: return@mapNotNull null
            val name = entry["name"] as? String ?: return@mapNotNull null
            Language(id, name)
        }
    }

    fun addLanguage(name: String): Language {
        ensureSeedData()
        val normalized = name.trim()
        if (normalized.isBlank()) error("Language name is empty")
        val existing = getLanguages()
        val baseId = normalized
            .lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_]+"), "")
            .ifBlank { "lang" }
        var candidate = baseId
        var suffix = 2
        while (existing.any { it.id == candidate }) {
            candidate = "${baseId}_$suffix"
            suffix += 1
        }
        val newEntry = mapOf("id" to candidate, "name" to normalized)
        val updated = existing.map { mapOf("id" to it.id, "name" to it.displayName) } + newEntry
        languagesStore.write(updated)
        return Language(candidate, normalized)
    }

    fun getLessons(languageId: String): List<Lesson> {
        ensureSeedData()
        val entries = loadIndex(languageId)
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
        replaceByTitle(languageId, lessonTitle)
        saveIndex(languageId, LessonIndexEntry(id, lessonTitle, fileName))
        return Lesson(id = id, languageId = languageId, title = lessonTitle, cards = cards)
    }

    fun deleteAllLessons(languageId: String) {
        val dir = languageDir(languageId)
        if (dir.exists()) dir.deleteRecursively()
        val index = indexFileFor(languageId)
        if (index.exists()) index.delete()
    }

    fun deleteLesson(languageId: String, lessonId: String) {
        ensureSeedData()
        val entries = loadIndex(languageId).toMutableList()
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val id = entry["id"] as? String ?: continue
            if (id == lessonId) {
                val fileName = entry["file"] as? String
                if (fileName != null) {
                    val csvFile = File(languageDir(languageId), fileName)
                    if (csvFile.exists()) csvFile.delete()
                }
                iterator.remove()
                break
            }
        }
        writeIndex(languageId, entries)
    }

    fun createEmptyLesson(languageId: String, title: String): Lesson {
        ensureSeedData()
        val normalizedTitle = title.trim().ifBlank { "Lesson" }
        replaceByTitle(languageId, normalizedTitle)
        val id = UUID.randomUUID().toString()
        val fileName = "lesson_$id.csv"
        val dir = languageDir(languageId)
        dir.mkdirs()
        val csvFile = File(dir, fileName)
        AtomicFileWriter.writeText(csvFile, normalizedTitle)
        saveIndex(languageId, LessonIndexEntry(id, normalizedTitle, fileName))
        return Lesson(id = id, languageId = languageId, title = normalizedTitle, cards = emptyList())
    }



    private fun saveIndex(languageId: String, entry: LessonIndexEntry) {
        val existing = loadIndex(languageId).toMutableList()
        existing.add(mapOf("id" to entry.id, "title" to entry.title, "file" to entry.fileName))
        writeIndex(languageId, existing)
    }

    private fun loadIndex(languageId: String): List<Map<String, Any>> {
        val indexFile = indexFileFor(languageId)
        if (!indexFile.exists()) return emptyList()
        val store = YamlListStore(yaml, indexFile)
        return store.read()
    }

    private fun writeIndex(languageId: String, entries: List<Map<String, Any>>) {
        val indexFile = indexFileFor(languageId)
        val store = YamlListStore(yaml, indexFile)
        store.write(entries)
    }

    private fun replaceByTitle(languageId: String, title: String) {
        val entries = loadIndex(languageId).toMutableList()
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val existingTitle = entry["title"] as? String ?: continue
            if (existingTitle.equals(title, ignoreCase = true)) {
                val fileName = entry["file"] as? String
                if (fileName != null) {
                    val csvFile = File(languageDir(languageId), fileName)
                    if (csvFile.exists()) csvFile.delete()
                }
                iterator.remove()
            }
        }
        writeIndex(languageId, entries)
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
