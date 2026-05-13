package com.alexpo.grammermate.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Handles lesson pack ZIP import: extraction, manifest parsing, lesson file copying,
 * drill file import, story/vocab import from packs, and SAF URI imports.
 *
 * All file writes go through [AtomicFileWriter]. Direct [FileOutputStream] and [File.copyTo]
 * are avoided except for the initial ZIP extraction to a temp directory (which is transient).
 */
internal class PackImporter(
    private val context: Context,
    private val baseDir: File,
    private val packsDir: File,
    private val packsStore: YamlListStore,
    private val storiesDir: File,
    private val storiesStore: YamlListStore,
    private val vocabDir: File,
    private val vocabStore: YamlListStore,
    private val languageEnsurer: (String) -> Unit,
    private val languageDir: (String) -> File,
    private val lessonsDir: File,
    private val lessonIndexWriter: (String, String, String, String, String?) -> Unit,
    private val replaceById: (String, String) -> Unit,
    private val replaceByTitle: (String, String) -> Unit,
    private val removePacksForLanguage: (String, String) -> Unit,
    private val getInstalledPacks: () -> List<LessonPack>
) {

    // ── Public entry points ──────────────────────────────────────────────

    fun importPackFromUri(uri: Uri, resolver: ContentResolver): LessonPack {
        val input = resolver.openInputStream(uri) ?: error("Cannot open zip")
        input.use { stream -> return importPackFromStream(stream) }
    }

    fun importPackFromAssets(assetPath: String): LessonPack {
        val input = context.assets.open(assetPath)
        input.use { stream -> return importPackFromStream(stream) }
    }

    fun readPackManifestFromAssets(assetPath: String): LessonPackManifest {
        val input = context.assets.open(assetPath)
        input.use { stream ->
            val tempDir = extractZipToTemp(stream)
            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                tempDir.deleteRecursively()
                error("Manifest not found")
            }
            val manifest = LessonPackManifest.fromJson(manifestFile.readText())
            tempDir.deleteRecursively()
            return manifest
        }
    }

    /**
     * Import a single CSV lesson file from a SAF URI.
     * FIX: Uses [AtomicFileWriter] instead of raw [File.outputStream] + [InputStream.copyTo].
     */
    fun importLessonFromUri(
        languageId: String,
        uri: Uri,
        resolver: ContentResolver,
        ensureSeedData: () -> Unit,
        loadIndex: (String) -> List<Map<String, Any>>,
        writeIndex: (String, List<Map<String, Any>>) -> Unit
    ): Lesson {
        ensureSeedData()
        val input = resolver.openInputStream(uri) ?: error("Cannot open CSV")
        val title = guessFileName(resolver, uri) ?: "Lesson"
        val id = UUID.randomUUID().toString()
        val fileName = "lesson_$id.csv"
        val dir = languageDir(languageId)
        dir.mkdirs()
        val csvFile = File(dir, fileName)
        // AtomicFileWriter fix: read stream fully into string, then write atomically
        val csvContent = input.bufferedReader().use { it.readText() }
        AtomicFileWriter.writeText(csvFile, csvContent)
        val (parsedTitle, cards) = CsvParser.parseLesson(csvFile.inputStream())
        val lessonTitle = parsedTitle ?: title
        replaceByTitle(languageId, lessonTitle)
        saveIndex(languageId, id, lessonTitle, fileName, null)
        return Lesson(id = LessonId(id), languageId = LanguageId(languageId), title = lessonTitle, cards = cards)
    }

    // ── ZIP extraction ───────────────────────────────────────────────────

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

    // ── Pack import from extracted temp dir ──────────────────────────────

    private fun importPackFromTempDir(tempDir: File): LessonPack {
        try {
            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                error("Manifest not found")
            }
            val manifest = LessonPackManifest.fromJson(manifestFile.readText())
            val languageId = manifest.language.lowercase().trim()
            languageEnsurer(languageId)
            // Only remove the old pack directory for this specific packId
            removePacksForLanguage(manifest.packId, languageId)

            val packDir = File(packsDir, manifest.packId)
            if (packDir.exists()) {
                packDir.deleteRecursively()
            }
            tempDir.copyRecursively(packDir, overwrite = true)
            tempDir.deleteRecursively()

            val lessonEntries = manifest.lessons
                .filter { it.type != "verb_drill" }
                .sortedBy { it.order }
            lessonEntries.forEach { entry ->
                val sourceFile = File(packDir, entry.file)
                if (!sourceFile.exists()) error("Missing lesson file: ${entry.file}")
                val drillSourceFile = entry.drillFile?.let { File(packDir, it) }
                importLessonFromFile(languageId, sourceFile, entry.title, entry.lessonId, drillSourceFile)
            }

            // Import pack-scoped drill files
            importPackDrills(packDir, manifest)

            importStoriesFromPack(packDir, languageId)
            importVocabFromPack(packDir, languageId)

            val updated = getInstalledPacks()
                .filterNot { it.packId.value == manifest.packId }
                .map {
                    val map = mutableMapOf(
                        "packId" to it.packId.value,
                        "packVersion" to it.packVersion,
                        "languageId" to it.languageId.value,
                        "importedAt" to it.importedAt
                    )
                    if (it.displayName != null) map["displayName"] = it.displayName
                    map
                }
                .toMutableList()
            val newEntry = mutableMapOf(
                "packId" to manifest.packId,
                "packVersion" to manifest.packVersion,
                "languageId" to languageId,
                "importedAt" to System.currentTimeMillis()
            )
            if (manifest.displayName != null) newEntry["displayName"] = manifest.displayName
            updated.add(newEntry)
            packsStore.write(updated)
            return LessonPack(PackId(manifest.packId), manifest.packVersion, LanguageId(languageId), System.currentTimeMillis(), manifest.displayName)
        } finally {
            // Always clean up temp directory on any error
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
        }
    }

    // ── Single lesson file import ────────────────────────────────────────

    private fun importLessonFromFile(
        languageId: String,
        sourceFile: File,
        fallbackTitle: String?,
        lessonIdOverride: String? = null,
        drillSourceFile: File? = null
    ): Lesson {
        val normalizedId = lessonIdOverride?.trim().orEmpty()
        val id = if (normalizedId.isNotBlank()) normalizedId else UUID.randomUUID().toString()
        val fileName = "lesson_$id.csv"
        val dir = languageDir(languageId)
        dir.mkdirs()
        val targetFile = File(dir, fileName)
        sourceFile.inputStream().use { input ->
            AtomicFileWriter.writeText(targetFile, input.bufferedReader().readText())
        }
        val (parsedTitle, cards) = CsvParser.parseLesson(targetFile.inputStream())
        val title = parsedTitle ?: fallbackTitle ?: sourceFile.nameWithoutExtension
        // Prefix card IDs with lesson ID to avoid collisions across lessons
        val uniqueCards = cards.mapIndexed { index, card ->
            card.copy(id = "${id}_${index}")
        }

        var drillFileName: String? = null
        var drillCards = emptyList<SentenceCard>()
        if (drillSourceFile != null && drillSourceFile.exists()) {
            val drillTargetName = "lesson_${id}_drill.csv"
            val drillTargetFile = File(dir, drillTargetName)
            drillSourceFile.inputStream().use { input ->
                AtomicFileWriter.writeText(drillTargetFile, input.bufferedReader().readText())
            }
            val (_, parsedDrillCards) = CsvParser.parseLesson(drillTargetFile.inputStream())
            drillFileName = drillTargetName
            drillCards = parsedDrillCards
        }

        if (normalizedId.isNotBlank()) {
            replaceById(languageId, normalizedId)
        } else {
            replaceByTitle(languageId, title)
        }
        lessonIndexWriter(languageId, id, title, fileName, drillFileName)
        val uniqueDrillCards = drillCards.mapIndexed { index, card ->
            card.copy(id = "${id}_drill_${index}")
        }
        return Lesson(id = LessonId(id), languageId = LanguageId(languageId), title = title, cards = uniqueCards, drillCards = uniqueDrillCards)
    }

    // ── Pack-scoped drill import ─────────────────────────────────────────

    /**
     * Import pack-scoped drill files declared in the manifest's verbDrill/vocabDrill sections.
     * Copies listed files from the extracted pack directory to grammarmate/drills/{packId}/.
     */
    private fun importPackDrills(packDir: File, manifest: LessonPackManifest) {
        manifest.verbDrill?.files?.forEach { fileName ->
            val source = File(packDir, fileName)
            if (!source.exists()) return@forEach
            val targetDir = File(baseDir, "drills/${manifest.packId}/verb_drill")
            targetDir.mkdirs()
            val target = File(targetDir, source.name)
            AtomicFileWriter.writeText(target, source.readText())
        }
        manifest.vocabDrill?.files?.forEach { fileName ->
            val source = File(packDir, fileName)
            if (!source.exists()) return@forEach
            val targetDir = File(baseDir, "drills/${manifest.packId}/vocab_drill")
            targetDir.mkdirs()
            val target = File(targetDir, source.name)
            AtomicFileWriter.writeText(target, source.readText())
        }
    }

    /**
     * Import a single verb drill CSV file (legacy, pre-pack-scoped).
     * FIX: Uses [AtomicFileWriter] instead of [File.copyTo].
     */
    fun importVerbDrillFile(languageId: String, sourceFile: File, lessonId: String) {
        val verbDrillDir = File(baseDir, "verb_drill")
        verbDrillDir.mkdirs()
        val targetFile = File(verbDrillDir, "${languageId}_${lessonId}.csv")
        // AtomicFileWriter fix: replace sourceFile.copyTo(targetFile, overwrite = true)
        AtomicFileWriter.writeText(targetFile, sourceFile.readText())
    }

    // ── Story import from pack ───────────────────────────────────────────

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
                    entryLessonId == story.lessonId.value &&
                    entryPhase == story.phase.name
                }

                // Add new/updated story
                existing.add(
                    mapOf(
                        "storyId" to story.storyId,
                        "lessonId" to story.lessonId.value,
                        "phase" to story.phase.name,
                        "languageId" to languageId,
                        "file" to storedName
                    )
                )
            }
        storiesStore.write(existing)
    }

    // ── Vocab import from pack ───────────────────────────────────────────

    private fun importVocabFromPack(packDir: File, languageId: String) {
        vocabDir.mkdirs()
        val languageDirectory = File(vocabDir, languageId)
        languageDirectory.mkdirs()

        val existing = vocabStore.read().toMutableList()

        packDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
            .filter { it.nameWithoutExtension.startsWith("vocab_", ignoreCase = true) }
            .forEach { file ->
                val lessonId = file.nameWithoutExtension.removePrefix("vocab_")
                if (lessonId.isBlank()) return@forEach
                val storedName = "${file.nameWithoutExtension}.csv"
                val target = File(languageDirectory, storedName)
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

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun saveIndex(languageId: String, id: String, title: String, fileName: String, drillFileName: String?) {
        val indexFile = File(lessonsDir, "${languageId}_index.yaml")
        val yaml = org.yaml.snakeyaml.Yaml()
        val store = YamlListStore(yaml, indexFile)
        val existing = store.read().toMutableList()
        val indexMap = mutableMapOf("id" to id, "title" to title, "file" to fileName)
        if (drillFileName != null) {
            indexMap["drillFile"] = drillFileName
        }
        existing.add(indexMap)
        store.write(existing)
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

}
