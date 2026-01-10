package com.alexpo.grammermate.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class LessonStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val lessonsDir = File(baseDir, "lessons")
    private val languagesFile = File(lessonsDir, "languages.yaml")
    private val languagesStore = YamlListStore(yaml, languagesFile)
    private val seedMarker = File(baseDir, "seed_v1.done")
    private val packsDir = File(baseDir, "packs")
    private val packsFile = File(baseDir, "packs.yaml")
    private val packsStore = YamlListStore(yaml, packsFile)
    private val storiesDir = File(baseDir, "stories")
    private val storiesIndexFile = File(storiesDir, "stories.yaml")
    private val storiesStore = YamlListStore(yaml, storiesIndexFile)
    private val vocabDir = File(baseDir, "vocab")
    private val vocabIndexFile = File(vocabDir, "vocab.yaml")
    private val vocabStore = YamlListStore(yaml, vocabIndexFile)

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

    private fun hasLessonContent(): Boolean {
        if (!lessonsDir.exists()) return false
        return lessonsDir.walkTopDown().any { it.isFile && it.extension.equals("csv", ignoreCase = true) }
    }

    fun seedDefaultPacksIfNeeded(): Boolean {
        ensureSeedData()
        if (seedMarker.exists()) return false
        if (hasLessonContent()) {
            AtomicFileWriter.writeText(seedMarker, "skip")
            return false
        }
        val seeds = listOf(
            "grammarmate/packs/EN_WORD_ORDER_A1.zip",
            "grammarmate/packs/IT_WORD_ORDER_A1.zip"
        )
        var seededAny = false
        seeds.forEach { assetPath ->
            val seeded = runCatching { importPackFromAssetsInternal(assetPath) }.isSuccess
            if (seeded) seededAny = true
        }
        AtomicFileWriter.writeText(seedMarker, if (seededAny) "ok" else "none")
        return seededAny
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
        val input = resolver.openInputStream(uri) ?: error("Cannot open zip")
        input.use { stream -> return importPackFromStream(stream) }
    }

    fun importPackFromAssets(assetPath: String): LessonPack {
        ensureSeedData()
        return importPackFromAssetsInternal(assetPath)
    }

    private fun importPackFromAssetsInternal(assetPath: String): LessonPack {
        val input = context.assets.open(assetPath)
        input.use { stream -> return importPackFromStream(stream) }
    }

    private fun importPackFromStream(input: InputStream): LessonPack {
        packsDir.mkdirs()
        val tempDir = extractZipToTemp(input)
        return importPackFromTempDir(tempDir)
    }

    private fun extractZipToTemp(input: InputStream): File {
        val tempDir = File(packsDir, "tmp_${UUID.randomUUID()}")
        tempDir.mkdirs()
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
        return tempDir
    }

    private fun importPackFromTempDir(tempDir: File): LessonPack {
        val manifestFile = File(tempDir, "manifest.json")
        if (!manifestFile.exists()) {
            tempDir.deleteRecursively()
            error("Manifest not found")
        }
        val manifest = LessonPackManifest.fromJson(manifestFile.readText())
        val languageId = manifest.language.lowercase().trim()
        ensureLanguage(languageId)
        // Don't delete all lessons - we'll do incremental update instead
        // Only remove the old pack directory for this specific packId
        removePacksForLanguage(manifest.packId, languageId)

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
        importVocabFromPack(packDir, languageId)

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

    fun getVocabEntries(lessonId: String, languageId: String): List<VocabEntry> {
        val entries = vocabStore.read()
        return entries.flatMap { entry ->
            val entryLesson = entry["lessonId"] as? String ?: return@flatMap emptyList()
            val entryLang = entry["languageId"] as? String ?: return@flatMap emptyList()
            if (!entryLesson.equals(lessonId, ignoreCase = true)) return@flatMap emptyList()
            if (!entryLang.equals(languageId, ignoreCase = true)) return@flatMap emptyList()
            val fileName = entry["file"] as? String ?: return@flatMap emptyList()
            val languageDir = vocabDirForLanguage(entryLang)
            val file = File(languageDir, fileName).takeIf { it.exists() }
                ?: File(vocabDir, fileName).takeIf { it.exists() }
            if (file == null) return@flatMap emptyList()
            val rows = runCatching { VocabCsvParser.parse(file.inputStream()) }.getOrNull() ?: return@flatMap emptyList()
            rows.mapIndexed { index, row ->
                VocabEntry(
                    id = "${entryLesson}_${index + 1}",
                    lessonId = entryLesson,
                    languageId = entryLang,
                    nativeText = row.nativeText,
                    targetText = row.targetText,
                    isHard = row.isHard
                )
            }
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
        removeVocabEntries(languageId, null)
        removeStoriesForLanguage(languageId)
        removePacksForLanguage(languageId)
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
        removeVocabEntries(languageId, lessonId)
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

    private fun vocabDirForLanguage(languageId: String): File = File(vocabDir, languageId)

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
        val existing = storiesStore.read().toMutableList()

        packDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .filterNot { it.name.equals("manifest.json", ignoreCase = true) }
            .forEach { file ->
                val story = runCatching { StoryQuizParser.parse(file.readText()) }.getOrNull() ?: return@forEach
                val storedName = "${story.storyId}.json"
                val target = File(storiesDir, storedName)
                AtomicFileWriter.writeText(target, file.readText())

                // Remove old version of this story if exists
                existing.removeIf { entry ->
                    val entryStoryId = entry["storyId"] as? String
                    val entryLessonId = entry["lessonId"] as? String
                    val entryPhase = entry["phase"] as? String
                    entryStoryId == story.storyId &&
                    entryLessonId == story.lessonId &&
                    entryPhase == story.phase.name
                }

                // Add new/updated story
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

    private fun importVocabFromPack(packDir: File, languageId: String) {
        vocabDir.mkdirs()
        val languageDir = vocabDirForLanguage(languageId)
        languageDir.mkdirs()

        val existing = vocabStore.read().toMutableList()

        packDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
            .filter { it.nameWithoutExtension.startsWith("vocab_", ignoreCase = true) }
            .forEach { file ->
                val lessonId = file.nameWithoutExtension.removePrefix("vocab_")
                if (lessonId.isBlank()) return@forEach
                val storedName = "${file.nameWithoutExtension}.csv"
                val target = File(languageDir, storedName)
                AtomicFileWriter.writeText(target, file.readText())

                // Remove old version of this vocab if exists
                existing.removeIf { entry ->
                    val entryLessonId = entry["lessonId"] as? String
                    val entryLang = entry["languageId"] as? String
                    entryLessonId == lessonId && entryLang?.equals(languageId, ignoreCase = true) == true
                }

                // Add new/updated vocab
                existing.add(
                    mapOf(
                        "lessonId" to lessonId,
                        "languageId" to languageId,
                        "file" to storedName
                    )
                )
            }
        vocabStore.write(existing)
    }

    private fun removeVocabEntries(languageId: String, lessonId: String?) {
        val entries = vocabStore.read()
        val remaining = mutableListOf<Map<String, Any>>()
        entries.forEach { entry ->
            val entryLang = entry["languageId"] as? String ?: return@forEach
            val entryLesson = entry["lessonId"] as? String
            val shouldRemove = entryLang.equals(languageId, ignoreCase = true) &&
                (lessonId == null || entryLesson?.equals(lessonId, ignoreCase = true) == true)
            if (shouldRemove) {
                val fileName = entry["file"] as? String
                if (fileName != null) {
                    File(vocabDir, fileName).delete()
                    File(vocabDirForLanguage(entryLang), fileName).delete()
                }
            } else {
                remaining.add(entry)
            }
        }
        vocabStore.write(remaining)
    }

    private fun removeStoriesForLanguage(languageId: String) {
        val entries = storiesStore.read()
        val remaining = mutableListOf<Map<String, Any>>()
        entries.forEach { entry ->
            val entryLang = entry["languageId"] as? String ?: return@forEach
            if (entryLang.equals(languageId, ignoreCase = true)) {
                val fileName = entry["file"] as? String
                if (fileName != null) {
                    val file = File(storiesDir, fileName)
                    if (file.exists()) file.delete()
                }
            } else {
                remaining.add(entry)
            }
        }
        storiesStore.write(remaining)
    }

    private fun removePacksForLanguage(packIdToRemove: String, languageId: String) {
        val entries = packsStore.read()
        val remaining = mutableListOf<Map<String, Any>>()
        entries.forEach { entry ->
            val entryPackId = entry["packId"] as? String
            val entryLang = entry["languageId"] as? String
            if (entryPackId == packIdToRemove && entryLang?.equals(languageId, ignoreCase = true) == true) {
                // Remove old version of this pack
                val dir = File(packsDir, packIdToRemove)
                if (dir.exists()) dir.deleteRecursively()
            } else {
                remaining.add(entry)
            }
        }
        packsStore.write(remaining)
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
