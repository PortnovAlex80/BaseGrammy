package com.alexpo.grammermate.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
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
    companion object {
        private const val TAG = "PackImporter"
    }

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
     * Returns Pair<Lesson?, List<ParseError>> to handle parse errors gracefully.
     */
    fun importLessonFromUri(
        languageId: String,
        uri: Uri,
        resolver: ContentResolver,
        ensureSeedData: () -> Unit,
        loadIndex: (String) -> List<Map<String, Any>>,
        writeIndex: (String, List<Map<String, Any>>) -> Unit
    ): Pair<Lesson?, List<ParseError>> {
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

        val parseResult = CsvParser.parseLesson(csvFile.inputStream())
        val errors = parseResult.errors.map { error ->
            // Add file context to errors
            ParseError.WithFileContext(fileName, error)
        }

        val parsedData = parseResult.data
        if (parsedData == null) {
            // Parsing failed completely
            return Pair(null, errors)
        }

        val (parsedTitle, cards) = parsedData
        val lessonTitle = parsedData.first
        replaceByTitle(languageId, lessonTitle)
        saveIndex(languageId, id, lessonTitle, fileName, null)

        val uniqueCards = cards.mapIndexed { index, card ->
            card.copy(id = "${id}_${index}")
        }

        val lesson = Lesson(id = LessonId(id), languageId = LanguageId(languageId), title = lessonTitle, cards = uniqueCards)

        return if (errors.isEmpty()) {
            Pair(lesson, emptyList())
        } else {
            Pair(lesson, errors)
        }
    }

    // ── ZIP extraction ───────────────────────────────────────────────────

    private fun importPackFromStream(input: InputStream): LessonPack {
        packsDir.mkdirs()
        val tempDir = extractZipToTemp(input)
        val (pack, errors) = importPackFromTempDir(tempDir)

        // Log any errors that occurred during import
        errors.forEach { error ->
            android.util.Log.w("PackImporter", error.toUserMessage())
        }

        return pack ?: error("Pack import failed")
    }

    private fun extractZipToTemp(input: InputStream): File {
        val tempDir = File(packsDir, "tmp_${UUID.randomUUID()}")
        tempDir.mkdirs()
        Log.d(TAG, "Extracting ZIP to temp dir: ${tempDir.absolutePath}")
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
                    Log.d(TAG, "Created directory: ${entry.name}")
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                    if (entry.name.endsWith(".csv")) {
                        Log.d(TAG, "Extracted CSV: ${entry.name} (${outFile.length()} bytes)")
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        Log.d(TAG, "ZIP extraction complete. Listing CSV files in temp dir:")
        tempDir.listFiles()?.filter { it.name.endsWith(".csv") }?.forEach {
            Log.d(TAG, "  - ${it.name} (${it.length()} bytes)")
        }
        return tempDir
    }

    // ── Pack import from extracted temp dir ──────────────────────────────

    private fun importPackFromTempDir(tempDir: File): Pair<LessonPack?, List<ParseError>> {
        val allErrors = mutableListOf<ParseError>()
        try {
            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                return Pair(null, listOf(
                    ParseError.InvalidFormat(
                        lineNumber = 0,
                        reason = "Manifest file not found in pack"
                    )
                ))
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

            // Debug: List temp dir contents before copying
            Log.d(TAG, "Temp dir contents before copy:")
            tempDir.walkTopDown().maxDepth(2).forEach { file ->
                Log.d(TAG, "  ${file.absolutePath}")
            }

            // Check if stories directory exists in temp
            val storiesTempDir = File(tempDir, "stories")
            Log.d(TAG, "Stories dir in temp: ${storiesTempDir.exists()}, files: ${storiesTempDir.listFiles()?.size ?: 0}")

            tempDir.copyRecursively(packDir, overwrite = true)

            // Debug: List pack dir contents after copying
            Log.d(TAG, "Pack dir contents after copy:")
            packDir.walkTopDown().maxDepth(2).forEach { file ->
                Log.d(TAG, "  ${file.absolutePath}")
            }

            // Check if stories directory exists in pack
            val storiesPackDir = File(packDir, "stories")
            Log.d(TAG, "Stories dir in pack: ${storiesPackDir.exists()}, files: ${storiesPackDir.listFiles()?.size ?: 0}")

            tempDir.deleteRecursively()

            val lessonEntries = manifest.lessons
                .filter { it.type != "verb_drill" }
                .sortedBy { it.order }

            var successCount = 0
            var failureCount = 0
            lessonEntries.forEach { entry ->
                val sourceFile = File(packDir, entry.file)
                if (!sourceFile.exists()) {
                    allErrors.add(
                        ParseError.InvalidFormat(
                            lineNumber = 0,
                            reason = "Missing lesson file: ${entry.file}"
                        )
                    )
                    failureCount++
                    return@forEach
                }
                val (lesson, errors) = importLessonFromFile(languageId, sourceFile, entry.title, entry.lessonId, null)
                if (lesson != null) {
                    successCount++
                } else {
                    failureCount++
                }
                allErrors.addAll(errors)
            }

            // Import pack-scoped drill files
            importPackDrills(packDir, manifest)

            val storyErrors = importStoriesFromPack(packDir, languageId)
            allErrors.addAll(storyErrors)

            val vocabErrors = importVocabFromPack(packDir, languageId)
            allErrors.addAll(vocabErrors)

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

            val lessonPack = LessonPack(PackId(manifest.packId), manifest.packVersion, LanguageId(languageId), System.currentTimeMillis(), manifest.displayName)

            // Log summary
            if (allErrors.isNotEmpty()) {
                android.util.Log.w("PackImporter", "Imported pack ${manifest.packId} with ${allErrors.size} error(s). Lessons: $successCount succeeded, $failureCount failed")
                allErrors.forEach { error ->
                    android.util.Log.w("PackImporter", error.toUserMessage())
                }
            }

            return Pair(lessonPack, allErrors)
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
    ): Pair<Lesson?, List<ParseError>> {
        val normalizedId = lessonIdOverride?.trim().orEmpty()
        val id = if (normalizedId.isNotBlank()) normalizedId else UUID.randomUUID().toString()
        val fileName = "lesson_$id.csv"
        val dir = languageDir(languageId)
        dir.mkdirs()
        val targetFile = File(dir, fileName)
        sourceFile.inputStream().use { input ->
            AtomicFileWriter.writeText(targetFile, input.bufferedReader().readText())
        }

        val parseResult = CsvParser.parseLesson(targetFile.inputStream())
        val errors = parseResult.errors.map { error ->
            // Add file context to errors
            ParseError.WithFileContext(sourceFile.name, error)
        }

        val parsedData = parseResult.data
        if (parsedData == null) {
            // Parsing failed completely
            return Pair(null, errors)
        }

        val (parsedTitle, cards) = parsedData
        val title = fallbackTitle ?: parsedTitle
        // Prefix card IDs with lesson ID to avoid collisions across lessons
        val uniqueCards = cards.mapIndexed { index, card ->
            card.copy(id = "${id}_${index}")
        }

        if (normalizedId.isNotBlank()) {
            replaceById(languageId, normalizedId)
        } else {
            replaceByTitle(languageId, title)
        }
        lessonIndexWriter(languageId, id, title, fileName, null)
        val lesson = Lesson(id = LessonId(id), languageId = LanguageId(languageId), title = title, cards = uniqueCards)

        return if (errors.isEmpty()) {
            Pair(lesson, emptyList())
        } else {
            Pair(lesson, errors)
        }
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
            if (!source.exists()) {
                Log.w(TAG, "Vocab drill file not found in pack: $fileName (full path: ${source.absolutePath})")
                return@forEach
            }
            val targetDir = File(baseDir, "drills/${manifest.packId}/vocab_drill")
            targetDir.mkdirs()
            val target = File(targetDir, source.name)
            AtomicFileWriter.writeText(target, source.readText())
            Log.d(TAG, "Imported vocab drill file: $fileName to ${target.absolutePath}")
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

    private fun importStoriesFromPack(packDir: File, languageId: String): List<ParseError> {
        val errors = mutableListOf<ParseError>()
        storiesDir.mkdirs()
        val existing = storiesStore.read().toMutableList()

        packDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .filterNot { it.name.equals("manifest.json", ignoreCase = true) }
            .forEach { file ->
                val parseResult = StoryQuizParser.parse(file.readText())
                if (parseResult.data == null) {
                    // Add file context to errors
                    errors.addAll(parseResult.errors.map { error ->
                        ParseError.WithFileContext(file.name, error)
                    })
                    return@forEach
                }

                val story = parseResult.data
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

                // Collect any errors from successful parse
                if (parseResult.errors.isNotEmpty()) {
                    errors.addAll(parseResult.errors.map { error ->
                        ParseError.WithFileContext(file.name, error)
                    })
                }
            }
        storiesStore.write(existing)
        return errors
    }

    // ── Vocab import from pack ───────────────────────────────────────────

    private fun importVocabFromPack(packDir: File, languageId: String): List<ParseError> {
        val errors = mutableListOf<ParseError>()
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

                // Parse vocab file to check for errors
                val parseResult = VocabCsvParser.parse(file.inputStream())

                // Add file context to errors
                val fileErrors = parseResult.errors.map { error ->
                    ParseError.WithFileContext(file.name, error)
                }
                errors.addAll(fileErrors)

                // Only import if parsing succeeded (even partially)
                if (parseResult.data != null) {
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
            }
        vocabStore.write(existing)
        return errors
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
