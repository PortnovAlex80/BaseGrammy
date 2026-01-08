package com.alexpo.grammermate.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class LessonStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val lessonsDir = File(baseDir, "lessons")
    private val languagesFile = File(lessonsDir, "languages.yaml")
    private val languagesStore = YamlListStore(yaml, languagesFile)
    private val packsDir = File(baseDir, "packs")
    private val packsFile = File(baseDir, "packs.yaml")
    private val packsStore = YamlListStore(yaml, packsFile)
    private val storiesDir = File(baseDir, "stories")
    private val storiesIndexFile = File(storiesDir, "stories.yaml")
    private val storiesStore = YamlListStore(yaml, storiesIndexFile)

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

    fun getInstalledPacks(): List<LessonPack> {
        val entries = packsStore.read()
        return entries.mapNotNull { entry ->
            val packId = entry["packId"] as? String ?: return@mapNotNull null
            val packVersion = entry["packVersion"] as? String ?: return@mapNotNull null
            val languageId = entry["languageId"] as? String ?: return@mapNotNull null
            val importedAt = (entry["importedAt"] as? Number)?.toLong() ?: 0L
            LessonPack(packId, packVersion, languageId, importedAt)
        }
    }

    fun importPackFromUri(uri: Uri, resolver: ContentResolver): LessonPack {
        ensureSeedData()
        packsDir.mkdirs()
        val tempDir = File(packsDir, "tmp_${UUID.randomUUID()}")
        tempDir.mkdirs()
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(tempDir, entry.name)
                    val canonicalParent = tempDir.canonicalPath + File.separator
                    val canonicalTarget = outFile.canonicalPath
                    if (!canonicalTarget.startsWith(canonicalParent)) {
                        error("Invalid zip entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Cannot open zip")

        val manifestFile = File(tempDir, "manifest.json")
        if (!manifestFile.exists()) {
            tempDir.deleteRecursively()
            error("Manifest not found")
        }
        val manifest = LessonPackManifest.fromJson(manifestFile.readText())
        val languageId = manifest.language.lowercase().trim()
        ensureLanguage(languageId)

        val packDir = File(packsDir, manifest.packId)
        if (packDir.exists()) {
            packDir.deleteRecursively()
        }
        tempDir.copyRecursively(packDir, overwrite = true)
        tempDir.deleteRecursively()

        val lessonEntries = manifest.lessons.sortedBy { it.order }
        lessonEntries.forEach { entry ->
            val sourceFile = File(packDir, entry.file)
            if (!sourceFile.exists()) error("Missing lesson file: ${entry.file}")
            importLessonFromFile(languageId, sourceFile, entry.title, entry.lessonId)
        }
        importStoriesFromPack(packDir, languageId)

        val updated = getInstalledPacks()
            .filterNot { it.packId == manifest.packId }
            .map {
                mapOf(
                    "packId" to it.packId,
                    "packVersion" to it.packVersion,
                    "languageId" to it.languageId,
                    "importedAt" to it.importedAt
                )
            }
            .toMutableList()
        updated.add(
            mapOf(
                "packId" to manifest.packId,
                "packVersion" to manifest.packVersion,
                "languageId" to languageId,
                "importedAt" to System.currentTimeMillis()
            )
        )
        packsStore.write(updated)
        return LessonPack(manifest.packId, manifest.packVersion, languageId, System.currentTimeMillis())
    }

    fun getStoryQuizzes(lessonId: String, phase: StoryPhase, languageId: String): List<StoryQuiz> {
        val entries = storiesStore.read()
        return entries.mapNotNull { entry ->
            val entryLesson = entry["lessonId"] as? String ?: return@mapNotNull null
            val entryPhase = entry["phase"] as? String ?: return@mapNotNull null
            val entryLang = entry["languageId"] as? String ?: return@mapNotNull null
            if (!entryLesson.equals(lessonId, ignoreCase = true)) return@mapNotNull null
            if (!entryPhase.equals(phase.name, ignoreCase = true)) return@mapNotNull null
            if (!entryLang.equals(languageId, ignoreCase = true)) return@mapNotNull null
            val fileName = entry["file"] as? String ?: return@mapNotNull null
            val file = File(storiesDir, fileName)
            if (!file.exists()) return@mapNotNull null
            runCatching { StoryQuizParser.parse(file.readText()) }.getOrNull()
        }
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

    private fun ensureLanguage(languageId: String) {
        val normalized = languageId.lowercase().trim()
        if (normalized.isBlank()) return
        val existing = getLanguages()
        if (existing.any { it.id == normalized }) return
        val displayName = when (normalized) {
            "en" -> "English"
            "it" -> "Italian"
            else -> normalized.uppercase()
        }
        val newEntry = mapOf("id" to normalized, "name" to displayName)
        val updated = existing.map { mapOf("id" to it.id, "name" to it.displayName) } + newEntry
        languagesStore.write(updated)
    }

    private fun importLessonFromFile(
        languageId: String,
        sourceFile: File,
        fallbackTitle: String?,
        lessonIdOverride: String? = null
    ): Lesson {
        val normalizedId = lessonIdOverride?.trim().orEmpty()
        val id = if (normalizedId.isNotBlank()) normalizedId else UUID.randomUUID().toString()
        val fileName = "lesson_$id.csv"
        val dir = languageDir(languageId)
        dir.mkdirs()
        val targetFile = File(dir, fileName)
        sourceFile.inputStream().use { input ->
            FileOutputStream(targetFile).use { output -> input.copyTo(output) }
        }
        val (parsedTitle, cards) = CsvParser.parseLesson(targetFile.inputStream())
        val title = parsedTitle ?: fallbackTitle ?: sourceFile.nameWithoutExtension
        if (normalizedId.isNotBlank()) {
            replaceById(languageId, normalizedId)
        }
        replaceByTitle(languageId, title)
        saveIndex(languageId, LessonIndexEntry(id, title, fileName))
        return Lesson(id = id, languageId = languageId, title = title, cards = cards)
    }

    private fun replaceById(languageId: String, lessonId: String) {
        val entries = loadIndex(languageId).toMutableList()
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val existingId = entry["id"] as? String ?: continue
            if (existingId.equals(lessonId, ignoreCase = true)) {
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

    private fun importStoriesFromPack(packDir: File, languageId: String) {
        storiesDir.mkdirs()
        val existing = storiesStore.read().filterNot {
            val entryLang = it["languageId"] as? String
            entryLang?.equals(languageId, ignoreCase = true) == true
        }.toMutableList()
        packDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .filterNot { it.name.equals("manifest.json", ignoreCase = true) }
            .forEach { file ->
                val story = runCatching { StoryQuizParser.parse(file.readText()) }.getOrNull() ?: return@forEach
                val storedName = "${story.storyId}.json"
                val target = File(storiesDir, storedName)
                AtomicFileWriter.writeText(target, file.readText())
                existing.add(
                    mapOf(
                        "storyId" to story.storyId,
                        "lessonId" to story.lessonId,
                        "phase" to story.phase.name,
                        "languageId" to languageId,
                        "file" to storedName
                    )
                )
            }
        storiesStore.write(existing)
    }

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
