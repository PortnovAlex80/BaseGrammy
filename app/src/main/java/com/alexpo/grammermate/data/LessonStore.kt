package com.alexpo.grammermate.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.alexpo.grammermate.BuildConfig
import org.yaml.snakeyaml.Yaml
import java.io.File

interface LessonStore {

    // -- Seed & defaults --

    fun ensureSeedData()

    fun seedDefaultPacksIfNeeded(): Boolean

    fun updateDefaultPacksIfNeeded(): Boolean

    fun forceReloadDefaultPacks(): Boolean

    // -- Language & pack queries --

    fun getLanguages(): List<Language>

    fun getLanguagesWithPacks(): List<Language>

    fun addLanguage(name: String): Language

    fun getInstalledPacks(): List<LessonPack>

    fun getPackIdForLesson(lessonId: String): String?

    fun getLessonIdsForPack(packId: String): List<String>

    fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String>

    // -- Pack import --

    fun importPackFromUri(uri: Uri, resolver: ContentResolver): LessonPack

    fun importPackFromAssets(assetPath: String): LessonPack

    // -- Pack removal --

    fun removeInstalledPackData(packId: String): Boolean

    // -- CSV lesson import --

    fun importFromUri(languageId: String, uri: Uri, resolver: ContentResolver): Lesson

    fun importFromUriWithErrors(languageId: String, uri: Uri, resolver: ContentResolver): Pair<Lesson, List<ParseError>>

    // -- Lesson CRUD --

    fun getLessons(languageId: String): List<Lesson>

    /** Pack-scoped lesson loading. Only loads lessons from the specified pack. */
    fun getLessons(packId: String, languageId: String): List<Lesson>

    /**
     * Get lightweight lesson metadata (ID + title only) for UI display.
     * Does NOT load cards - use getLesson() for full lesson data.
     */
    fun getLessonMetadata(packId: String, languageId: String): List<LessonMetadata> {
        // STUB - will be implemented
        return emptyList()
    }

    /**
     * Load full lesson data (including cards) for a single lesson.
     * Called when user opens a lesson.
     */
    fun getLesson(packId: String, languageId: String, lessonId: String): Lesson? {
        // STUB - will be implemented
        return null
    }

    /**
     * Get total lesson count for pack without loading lesson data.
     * Uses manifest chapters to count.
     */
    fun getLessonCount(packId: String, languageId: String): Int {
        // STUB - will be implemented
        return 0
    }

    /**
     * Get lesson ID at specific index in pack (for daily practice cursor).
     * Uses manifest chapter order, no lesson loading.
     */
    fun getLessonIdAtIndex(packId: String, languageId: String, index: Int): String? {
        // STUB - will be implemented
        return null
    }

    /**
     * Get cards for a single lesson (for daily practice).
     * Loads ONLY the requested lesson, not all lessons.
     */
    fun getCardsForLesson(packId: String, languageId: String, lessonId: String): List<SentenceCard> {
        // STUB - will be implemented
        return emptyList()
    }

    fun deleteAllLessons(languageId: String)

    fun deleteLesson(languageId: String, lessonId: String)

    fun createEmptyLesson(languageId: String, title: String): Lesson

    // -- Story & vocab queries --

    fun getStoryQuizzes(lessonId: String, phase: StoryPhase, languageId: String): List<StoryQuiz>

    fun getVocabEntries(lessonId: String, languageId: String): List<VocabEntry>

    // -- Drill file queries --

    fun getVerbDrillFiles(packId: String, languageId: String): List<File>

    fun getVerbDrillFilesForPack(packId: String): List<File>

    fun getVocabDrillFiles(packId: String, languageId: String): List<File>

    fun getVocabDrillFilesForPack(packId: String): List<File>

    fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<VocabWord>

    fun hasVerbDrill(packId: String, languageId: String): Boolean

    fun hasVocabDrill(packId: String, languageId: String): Boolean

    // -- Chapter queries (Grammar Story Roadmap) --

    fun getChapters(packId: String): List<Chapter>

    fun getChapterStory(packId: String, storyFile: String): String?

    fun hasChapters(packId: String): Boolean

    // -- External lesson loading --

    fun loadExternalLessons(languageId: String, externalDirPath: String): Int

    // -- Story language detection --

    fun detectStoryLanguage(packId: String, chapterId: String, uiLanguage: String): String?

    @Deprecated("Use getVerbDrillFiles(packId, languageId) for pack-scoped drill lookup.")
    fun getVerbDrillFiles(languageId: String): List<File>

    @Deprecated("Use hasVerbDrill(packId, languageId) for pack-scoped drill check.")
    fun hasVerbDrillLessons(languageId: String): Boolean
}

/**
 * Primary data store for lessons, packs, languages, drills, stories, and vocab.
 *
 * Delegates to:
 * - [LanguageManager] for language/pack CRUD and seed data
 * - [PackImporter] for ZIP import, SAF URI import, manifest handling
 * - [DrillFileManager] for drill file queries, story/vocab lookups
 *
 * All existing public method signatures are preserved — consumers import LessonStore only.
 */
class LessonStoreImpl(private val context: Context) : LessonStore {
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

    // In-memory cache for getLessons() — invalidated on pack import/delete/reload
    private val lessonsCache = java.util.concurrent.ConcurrentHashMap<String, List<Lesson>>()
    private val metadataCache = java.util.concurrent.ConcurrentHashMap<String, List<LessonMetadata>>()

    private val defaultPacks = listOf(
        LanguageManager.DefaultPack("EN_WORD_ORDER_A1", "grammarmate/packs/EN_WORD_ORDER_A1.zip"),
        LanguageManager.DefaultPack("EN_WORD_ORDER_A1_DRILLS", "grammarmate/packs/EN_WORD_ORDER_A1_DRILLS.zip"),
        // TODO: re-enable when Italian verb groups pack is needed
        // LanguageManager.DefaultPack("IT_VERB_GROUPS_ALL", "grammarmate/packs/IT_VERB_GROUPS_ALL.zip"),
        LanguageManager.DefaultPack("ITALIAN_SHORT", "grammarmate/packs/ITALIAN_EXPRESS_SHORT.zip"),
        LanguageManager.DefaultPack("ITALIAN_FULL_COURSE", "grammarmate/packs/ITALIAN_FULL_COURSE.zip"),
        // Reserved: Greek, German, Chinese, Russian packs (backup commit 7ca026a)
        // LanguageManager.DefaultPack("GREEK_EXPRESS", "grammarmate/packs/GREEK_EXPRESS.zip"),
        // LanguageManager.DefaultPack("GREEK_EXPRESS_IT", "grammarmate/packs/GREEK_EXPRESS_IT.zip"),
        // LanguageManager.DefaultPack("GERMAN_EXPRESS", "grammarmate/packs/GERMAN_EXPRESS.zip"),
        // LanguageManager.DefaultPack("CHINESE_EXPRESS", "grammarmate/packs/CHINESE_EXPRESS.zip"),
        // LanguageManager.DefaultPack("RUSSIAN_EXPRESS", "grammarmate/packs/RUSSIAN_EXPRESS.zip"),
        LanguageManager.DefaultPack("EN_ADV_BASE_GRAMMAR", "grammarmate/packs/EN_ADV_BASE_GRAMMAR.zip"),
        LanguageManager.DefaultPack("EN_ENGLISH_FULL_COURSE", "grammarmate/packs/EN_ENGLISH_FULL_COURSE.zip")
    )

    private val languageManager = LanguageManager(
        baseDir, lessonsDir, packsDir, languagesFile, languagesStore,
        packsStore, seedMarker, defaultPacks
    )

    private val drillFileManager = DrillFileManager(
        context, baseDir, storiesDir, storiesStore, vocabDir, vocabStore
    )

    private val packImporter = PackImporter(
        context, baseDir, packsDir, packsStore,
        storiesDir, storiesStore, vocabDir, vocabStore,
        languageEnsurer = { languageManager.ensureLanguage(it) },
        languageDir = { languageDir(it) },
        lessonsDir = lessonsDir,
        lessonIndexWriter = { langId, id, title, fileName, drillFileName ->
            saveIndex(langId, LessonIndexEntry(id, title, fileName, drillFileName))
        },
        replaceById = { langId, id -> replaceById(langId, id) },
        replaceByTitle = { langId, title -> replaceByTitle(langId, title) },
        removePacksForLanguage = { packId, langId -> languageManager.removePacksForLanguage(packId, langId) },
        getInstalledPacks = { languageManager.getInstalledPacks() }
    )

    // ── Seed & defaults ──────────────────────────────────────────────────

    override fun ensureSeedData() = languageManager.ensureSeedData()

    /** Drop every pack-derived cache (lessons + manifests) after pack mutations. */
    private fun invalidatePackCaches() {
        invalidateLessonsCache()
        languageManager.invalidateManifestCache()
    }

    override fun seedDefaultPacksIfNeeded(): Boolean {
        val seeded = languageManager.seedDefaultPacksIfNeeded { path ->
            packImporter.importPackFromAssets(path)
            true
        }
        if (seeded) invalidatePackCaches()
        return seeded
    }

    override fun updateDefaultPacksIfNeeded(): Boolean {
        val updated = languageManager.updateDefaultPacksIfNeeded(
            importFromAssets = { path ->
                packImporter.importPackFromAssets(path)
                true
            },
            readManifestFromAssets = { path -> packImporter.readPackManifestFromAssets(path) }
        )
        if (updated) invalidatePackCaches()
        return updated
    }

    override fun forceReloadDefaultPacks(): Boolean {
        val result = languageManager.forceReloadDefaultPacks(
            removeInstalledPackData = { packId -> removeInstalledPackData(packId) },
            importFromAssets = { path ->
                packImporter.importPackFromAssets(path)
                true
            }
        )
        if (result) invalidatePackCaches()
        return result
    }

    // ── Language & pack queries ──────────────────────────────────────────

    override fun getLanguages(): List<Language> = languageManager.getLanguages()

    override fun getLanguagesWithPacks(): List<Language> = languageManager.getLanguagesWithPacks()

    override fun addLanguage(name: String): Language = languageManager.addLanguage(name)

    override fun getInstalledPacks(): List<LessonPack> = languageManager.getInstalledPacks()

    override fun getPackIdForLesson(lessonId: String): String? {
        val packs = getInstalledPacks()
        for (pack in packs) {
            val manifest = languageManager.readInstalledPackManifest(pack.packId.value) ?: continue

            // Check root-level lessons (schema v1)
            if (manifest.lessons.any { it.lessonId == lessonId }) {
                return pack.packId.value
            }

            // Check chapter-level lessons (schema v2)
            for (chapter in manifest.chapters) {
                val chapterLessonIds = chapter.lessons
                if (chapterLessonIds.contains(lessonId)) {
                    return pack.packId.value
                }
            }
        }
        return null
    }

    override fun getLessonIdsForPack(packId: String): List<String> {
        val manifest = languageManager.readInstalledPackManifest(packId) ?: return emptyList()

        // For schema v2, collect lessons from chapters in order
        if (manifest.schemaVersion == 2) {
            return manifest.chapters
                .sortedBy { it.order }
                .flatMap { chapter -> chapter.lessons }
        }

        // For schema v1, use root-level lessons
        return manifest.lessons.sortedBy { it.order }.map { it.lessonId }
    }

    override fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String> {
        val manifest = languageManager.readInstalledPackManifest(packId) ?: return emptyList()
        val sortedLessons = manifest.lessons
            .filter { it.type != "verb_drill" }
            .sortedBy { it.order }
        if (lessonLevel < 1 || lessonLevel > sortedLessons.size) return emptyList()
        return sortedLessons
            .take(lessonLevel)
            .flatMap { it.tenses }
            .distinct()
    }

    // ── Pack import ──────────────────────────────────────────────────────

    override fun importPackFromUri(uri: Uri, resolver: ContentResolver): LessonPack {
        ensureSeedData()
        val pack = packImporter.importPackFromUri(uri, resolver)
        invalidateLessonsCache()
        languageManager.invalidateManifestCache(pack.packId.value)
        return pack
    }

    override fun importPackFromAssets(assetPath: String): LessonPack {
        ensureSeedData()
        val pack = packImporter.importPackFromAssets(assetPath)
        invalidateLessonsCache()
        languageManager.invalidateManifestCache(pack.packId.value)
        return pack
    }

    // ── Pack removal ─────────────────────────────────────────────────────

    override fun removeInstalledPackData(packId: String): Boolean {
        val manifest = languageManager.readInstalledPackManifest(packId)
        val result = if (manifest != null) {
            val languageId = manifest.language.lowercase().trim()
            if (languageId.isNotBlank()) {
                manifest.lessons.forEach { lesson ->
                    deleteLesson(languageId, lesson.lessonId)
                }
                languageManager.removePacksForLanguage(packId, languageId)
                languageManager.deletePackDrills(packId)
                true
            } else {
                false
            }
        } else {
            val removedEntry = languageManager.removePackEntry(packId)
            val packDir = File(packsDir, packId)
            val removedDir = if (packDir.exists()) packDir.deleteRecursively() else false
            languageManager.deletePackDrills(packId)
            removedEntry || removedDir
        }
        invalidateLessonsCache()
        languageManager.invalidateManifestCache(packId)
        return result
    }

    // ── CSV lesson import ────────────────────────────────────────────────

    override fun importFromUri(languageId: String, uri: Uri, resolver: ContentResolver): Lesson {
        val (lesson, errors) = packImporter.importLessonFromUri(
            languageId, uri, resolver,
            ensureSeedData = { ensureSeedData() },
            loadIndex = { loadIndex(it) },
            writeIndex = { langId, entries -> writeIndex(langId, entries) }
        )

        // Log any errors that occurred during import
        errors.forEach { error ->
            android.util.Log.w("LessonStore", error.toUserMessage())
        }

        invalidateLessonsCache(languageId)
        return lesson ?: error("Lesson import failed")
    }

    override fun importFromUriWithErrors(languageId: String, uri: Uri, resolver: ContentResolver): Pair<Lesson, List<ParseError>> {
        val (lesson, errors) = packImporter.importLessonFromUri(
            languageId, uri, resolver,
            ensureSeedData = { ensureSeedData() },
            loadIndex = { loadIndex(it) },
            writeIndex = { langId, entries -> writeIndex(langId, entries) }
        )

        invalidateLessonsCache(languageId)
        val actualLesson = lesson ?: error("Lesson import failed")
        return Pair(actualLesson, errors)
    }

    // ── Lesson CRUD ──────────────────────────────────────────────────────

    override fun getLessons(languageId: String): List<Lesson> {
        ensureSeedData()
        return lessonsCache.getOrPut(languageId) {
            Log.d("LessonStore", "Cache miss for language: $languageId, loading from disk")
            loadLessonsFromDisk(languageId)
        }
    }

    override fun getLessons(packId: String, languageId: String): List<Lesson> {
        ensureSeedData()
        val cacheKey = "$packId:$languageId"
        return lessonsCache.getOrPut(cacheKey) {
            Log.d("LessonStore", "Cache miss for pack:language: $cacheKey, loading from disk")
            loadLessonsFromDisk(languageId, packId)
        }
    }

    override fun getLessonMetadata(packId: String, languageId: String): List<LessonMetadata> {
        ensureSeedData()
        val cacheKey = "meta:$packId:$languageId"
        return metadataCache.getOrPut(cacheKey) {
            Log.d("LessonStore", "Loading lesson metadata for $cacheKey")
            loadLessonMetadataFromDisk(languageId, packId)
        }
    }

    override fun getLesson(packId: String, languageId: String, lessonId: String): Lesson? {
        ensureSeedData()
        // Check if already in lessons cache
        val cacheKey = "$packId:$languageId"
        lessonsCache[cacheKey]?.find { it.id.value == lessonId }?.let { return it }
        // Load single lesson from disk
        return loadSingleLessonFromDisk(languageId, packId, lessonId)
    }

    override fun getLessonCount(packId: String, languageId: String): Int {
        ensureSeedData()
        return getOrderedLessonIdsFromManifest(packId).size
    }

    override fun getLessonIdAtIndex(packId: String, languageId: String, index: Int): String? {
        ensureSeedData()
        return getOrderedLessonIdsFromManifest(packId).getOrNull(index)
    }

    override fun getCardsForLesson(packId: String, languageId: String, lessonId: String): List<SentenceCard> {
        return getLesson(packId, languageId, lessonId)?.cards ?: emptyList()
    }

    /**
     * Invalidate the lessons cache. Call with a specific languageId to evict one entry,
     * or null to clear the entire cache.
     */
    fun invalidateLessonsCache(languageId: String? = null) {
        if (languageId != null) {
            lessonsCache.remove(languageId)
        } else {
            lessonsCache.clear()
        }
        metadataCache.clear()
    }

    private fun loadLessonsFromDisk(languageId: String): List<Lesson> {
        // Try loading from packs FIRST (new schema v2 structure)
        val packLessons = mutableListOf<Lesson>()
        val installedPacks = getInstalledPacks()

        for (pack in installedPacks) {
            // Only load packs matching the languageId
            if (pack.languageId.value != languageId) continue

            val packDir = File(packsDir, pack.packId.value)
            if (!packDir.exists()) {
                Log.w("LessonStore", "Pack directory does not exist: ${packDir.absolutePath}")
                continue
            }

            // Read manifest to determine which files are actual lessons
            val manifest = languageManager.readInstalledPackManifest(pack.packId.value)
            val manifestLessonFiles = getManifestLessonFileNames(manifest)

            // Load ONLY files declared in manifest — exclude drill/vocab CSVs
            val lessonFiles = packDir.listFiles()?.filter {
                it.isFile && it.name.endsWith(".csv") && it.name in manifestLessonFiles
            } ?: continue

            Log.d("LessonStore", "Loading ${lessonFiles.size} lesson files from pack: ${pack.packId.value} (filtered from ${packDir.listFiles()?.count { it.isFile && it.name.endsWith(".csv") } ?: 0} CSVs), schema: ${manifest?.schemaVersion}")

            for (lessonFile in lessonFiles) {
                try {
                    val parseResult = CsvParser.parseLesson(lessonFile.inputStream())
                    val (parsedTitle, cards) = parseResult.data ?: continue

                    // For schema v1, use lessonId from manifest; for schema v2, use filename
                    val lessonId = if (manifest?.schemaVersion == 1) {
                        // Find lesson in manifest by filename
                        val fileName = lessonFile.name
                        val lessonEntry = manifest.lessons.find { it.file == fileName }
                        if (lessonEntry != null) {
                            Log.d("LessonStore", "Mapped file '$fileName' to lessonId '${lessonEntry.lessonId}'")
                            lessonEntry.lessonId
                        } else {
                            Log.w("LessonStore", "No manifest entry for file: $fileName, using filename as ID")
                            fileName.removeSuffix(".csv")
                        }
                    } else {
                        // Schema v2 or no manifest - use filename
                        lessonFile.name.removeSuffix(".csv")
                    }

                    packLessons.add(Lesson(
                        id = LessonId(lessonId),
                        languageId = LanguageId(languageId),
                        title = parsedTitle ?: lessonId,
                        cards = cards
                    ))
                } catch (e: Exception) {
                    Log.e("LessonStore", "Failed to parse lesson file: ${lessonFile.name}", e)
                }
            }
        }

        // If pack lessons were found, return them (prioritize pack lessons over legacy)
        if (packLessons.isNotEmpty()) {
            Log.d("LessonStore", "Loaded ${packLessons.size} lessons from pack directories for language: $languageId (PACK LESSONS PRIORITY)")
            return packLessons
        }

        // Otherwise, fall back to legacy structure (schema v1)
        Log.d("LessonStore", "No pack lessons found for language: $languageId, falling back to legacy structure")
        val entries = loadIndex(languageId)
        val legacyLessons = entries.mapNotNull { entry ->
            val id = entry["id"] as? String ?: return@mapNotNull null
            val title = entry["title"] as? String ?: "Lesson"
            val fileName = entry["file"] as? String ?: return@mapNotNull null
            val csvFile = File(languageDir(languageId), fileName)
            if (!csvFile.exists()) return@mapNotNull null
            val parseResult = CsvParser.parseLesson(csvFile.inputStream())
            val (parsedTitle, cards) = parseResult.data ?: return@mapNotNull null
            Lesson(id = LessonId(id), languageId = LanguageId(languageId), title = parsedTitle ?: title, cards = cards)
        }

        Log.d("LessonStore", "Loaded ${legacyLessons.size} lessons from legacy structure for language: $languageId")
        return legacyLessons
    }

    /**
     * Load lessons from disk filtered to a single pack.
     * Only loads lessons from the pack directory matching [packId].
     */
    private fun loadLessonsFromDisk(languageId: String, packId: String): List<Lesson> {
        val installedPacks = getInstalledPacks()

        // Find the specific pack
        val pack = installedPacks.find {
            it.packId.value == packId && it.languageId.value == languageId
        } ?: run {
            Log.w("LessonStore", "Pack not found or language mismatch: packId=$packId, languageId=$languageId")
            return emptyList()
        }

        val packDir = File(packsDir, pack.packId.value)
        if (!packDir.exists()) {
            Log.w("LessonStore", "Pack directory does not exist: ${packDir.absolutePath}")
            return emptyList()
        }

        val manifest = languageManager.readInstalledPackManifest(pack.packId.value)
        val manifestLessonFiles = getManifestLessonFileNames(manifest)

        val lessonFiles = packDir.listFiles()?.filter {
            it.isFile && it.name.endsWith(".csv") && it.name in manifestLessonFiles
        } ?: return emptyList()

        val lessons = mutableListOf<Lesson>()

        Log.d("LessonStore", "Loading ${lessonFiles.size} lesson files from pack: ${pack.packId.value} (filtered from ${packDir.listFiles()?.count { it.isFile && it.name.endsWith(".csv") } ?: 0} CSVs), schema: ${manifest?.schemaVersion}")

        // Canonical lesson order from the manifest (v2: chapter order; v1: lesson
        // order). listFiles() returns files in FS/readdir order which is
        // non-deterministic on ext4 — sorting the result here keeps
        // MixedReviewScheduler (which mixes review cards by positional lesson
        // index) stable and prevents unrelated lessons from leaking into a
        // level's mix (Symptom B regression).
        val orderedLessonIds = getOrderedLessonIdsFromManifest(pack.packId.value)

        for (lessonFile in lessonFiles) {
            try {
                val parseResult = CsvParser.parseLesson(lessonFile.inputStream())
                val (parsedTitle, cards) = parseResult.data ?: continue

                val lessonId = if (manifest?.schemaVersion == 1) {
                    val fileName = lessonFile.name
                    val lessonEntry = manifest.lessons.find { it.file == fileName }
                    if (lessonEntry != null) {
                        Log.d("LessonStore", "Mapped file '$fileName' to lessonId '${lessonEntry.lessonId}'")
                        lessonEntry.lessonId
                    } else {
                        Log.w("LessonStore", "No manifest entry for file: $fileName, using filename as ID")
                        fileName.removeSuffix(".csv")
                    }
                } else {
                    lessonFile.name.removeSuffix(".csv")
                }

                lessons.add(Lesson(
                    id = LessonId(lessonId),
                    languageId = LanguageId(languageId),
                    title = parsedTitle ?: lessonId,
                    cards = cards
                ))
            } catch (e: Exception) {
                Log.e("LessonStore", "Failed to parse lesson file: ${lessonFile.name}", e)
            }
        }

        Log.d("LessonStore", "Loaded ${lessons.size} lessons from pack: $packId for language: $languageId")
        return sortByManifestOrder(lessons, orderedLessonIds)
    }

    override fun deleteAllLessons(languageId: String) {
        val dir = languageDir(languageId)
        if (dir.exists()) dir.deleteRecursively()
        val index = indexFileFor(languageId)
        if (index.exists()) index.delete()
        drillFileManager.removeVocabEntries(languageId, null)
        drillFileManager.removeStoriesForLanguage(languageId)
        languageManager.removePacksForLanguage(languageId)
        invalidateLessonsCache(languageId)
    }

    override fun deleteLesson(languageId: String, lessonId: String) {
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
                val drillFileName = entry["drillFile"] as? String
                if (drillFileName != null) {
                    val drillFile = File(languageDir(languageId), drillFileName)
                    if (drillFile.exists()) drillFile.delete()
                }
                iterator.remove()
                break
            }
        }
        writeIndex(languageId, entries)
        drillFileManager.removeVocabEntries(languageId, lessonId)
        invalidateLessonsCache(languageId)
    }

    override fun createEmptyLesson(languageId: String, title: String): Lesson {
        ensureSeedData()
        val normalizedTitle = title.trim().ifBlank { "Lesson" }
        replaceByTitle(languageId, normalizedTitle)
        val id = java.util.UUID.randomUUID().toString()
        val fileName = "lesson_$id.csv"
        val dir = languageDir(languageId)
        dir.mkdirs()
        val csvFile = File(dir, fileName)
        AtomicFileWriter.writeText(csvFile, normalizedTitle)
        saveIndex(languageId, LessonIndexEntry(id, normalizedTitle, fileName))
        invalidateLessonsCache(languageId)
        return Lesson(id = LessonId(id), languageId = LanguageId(languageId), title = normalizedTitle, cards = emptyList())
    }

    // ── Story & vocab queries (delegated) ────────────────────────────────

    override fun getStoryQuizzes(lessonId: String, phase: StoryPhase, languageId: String): List<StoryQuiz> =
        drillFileManager.getStoryQuizzes(lessonId, phase, languageId)

    override fun getVocabEntries(lessonId: String, languageId: String): List<VocabEntry> =
        drillFileManager.getVocabEntries(lessonId, languageId)

    // ── Drill file queries (delegated) ───────────────────────────────────

    override fun getVerbDrillFiles(packId: String, languageId: String): List<File> =
        drillFileManager.getVerbDrillFiles(packId, languageId)

    override fun getVerbDrillFilesForPack(packId: String): List<File> =
        drillFileManager.getVerbDrillFilesForPack(packId)

    override fun getVocabDrillFiles(packId: String, languageId: String): List<File> =
        drillFileManager.getVocabDrillFiles(packId, languageId)

    override fun getVocabDrillFilesForPack(packId: String): List<File> =
        drillFileManager.getVocabDrillFilesForPack(packId)

    override fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<VocabWord> =
        drillFileManager.getVocabWordsByRankRange(packId, languageId, fromRank, toRank)

    override fun hasVerbDrill(packId: String, languageId: String): Boolean =
        drillFileManager.hasVerbDrill(packId, languageId)

    override fun hasVocabDrill(packId: String, languageId: String): Boolean =
        drillFileManager.hasVocabDrill(packId, languageId)

    @Deprecated("Use getVerbDrillFiles(packId, languageId) for pack-scoped drill lookup.")
    override fun getVerbDrillFiles(languageId: String): List<File> =
        drillFileManager.getVerbDrillFilesLegacy(languageId)

    @Deprecated("Use hasVerbDrill(packId, languageId) for pack-scoped drill check.")
    override fun hasVerbDrillLessons(languageId: String): Boolean =
        drillFileManager.hasVerbDrillLessons(languageId)

    // ── Chapter queries (Grammar Story Roadmap) ───────────────────────────

    override fun getChapters(packId: String): List<Chapter> {
        val manifest = languageManager.readInstalledPackManifest(packId) ?: return emptyList()
        val chapters = manifest.chapters.sortedBy { it.order }
        if (BuildConfig.DEBUG) {
            Log.d("LessonStore", "getChapters($packId): returning ${chapters.size} chapters")
            chapters.forEach { Log.d("LessonStore", "  - ${it.chapterId}: ${it.title}") }
        }
        return chapters
    }

    override fun getChapterStory(packId: String, storyFile: String): String? {
        if (storyFile.isBlank()) return null

        val packDir = File(packsDir, packId)
        val storiesDir = File(packDir, "stories")
        val storyFileObj = File(storiesDir, storyFile)

        Log.d("LessonStore", "Loading story: packId=$packId, storyFile=$storyFile")
        Log.d("LessonStore", "Pack dir exists: ${packDir.exists()}, Stories dir exists: ${storiesDir.exists()}")

        return try {
            // First, try to read from internal storage
            if (storyFileObj.exists()) {
                Log.d("LessonStore", "Story found in internal storage: ${storyFileObj.absolutePath}")
                return storyFileObj.readText()
            }

            Log.d("LessonStore", "Story NOT found in internal storage, trying assets...")
            Log.d("LessonStore", "Story file path: ${storyFileObj.absolutePath}")

            // If not in internal storage, try to read from assets and copy it
            val assetPath = "grammarmate/packs/$packId/stories/$storyFile"
            try {
                context.assets.open(assetPath).use { input ->
                    // Ensure the stories directory exists
                    if (!storiesDir.exists()) {
                        storiesDir.mkdirs()
                        Log.d("LessonStore", "Created stories directory: ${storiesDir.absolutePath}")
                    }

                    // Read from assets and write to internal storage
                    val content = input.bufferedReader().readText()
                    storyFileObj.writeText(content)

                    Log.d("LessonStore", "Copied story from assets: $assetPath -> $storyFileObj")
                    return content
                }
            } catch (e: Exception) {
                Log.e("LessonStore", "Story file not found in assets: $assetPath", e)
                Log.d("LessonStore", "Available stories in pack:")
                storiesDir.listFiles()?.forEach { file ->
                    Log.d("LessonStore", "  - ${file.name}")
                }
                null
            }
        } catch (e: Exception) {
            Log.e("LessonStore", "Failed to load story content: $storyFile", e)
            null
        }
    }

    /**
     * Get chapter story with automatic language detection based on UI language preference.
     * This is the preferred method for loading stories in the Grammar Story Roadmap.
     *
     * @param packId Pack identifier
     * @param chapterId Chapter identifier (e.g., "chapter_01")
     * @param uiLanguage UI language setting ("ru", "en", "system")
     * @return Story content if found, null otherwise
     */
    fun getChapterStoryWithLanguageDetection(packId: String, chapterId: String, uiLanguage: String): String? {
        val storyFile = detectStoryLanguage(packId, chapterId, uiLanguage) ?: return null
        return getChapterStory(packId, storyFile)
    }

    override fun hasChapters(packId: String): Boolean {
        val manifest = languageManager.readInstalledPackManifest(packId) ?: return false
        return manifest.chapters.isNotEmpty()
    }

    // ── External lesson loading ─────────────────────────────────────────────

    /**
     * Load lessons from an external directory with Russian naming pattern.
     * Pattern: урок_{number}_{code}.csv (e.g., урок_01_A01.csv, урок_02_A02.csv)
     *
     * @param languageId Target language ID (e.g., "it" for Italian)
     * @param externalDirPath Absolute path to external lesson directory
     * @return Number of lessons successfully loaded
     */
    override fun loadExternalLessons(languageId: String, externalDirPath: String): Int {
        ensureSeedData()
        val externalDir = File(externalDirPath)
        if (!externalDir.exists() || !externalDir.isDirectory) {
            android.util.Log.w("LessonStore", "External directory does not exist: $externalDirPath")
            return 0
        }

        // Russian pattern: урок_{number}_{code}.csv
        val lessonPattern = Regex("""урок_(\d+)_[A-Z]\d+\.csv""")
        val lessonFiles = externalDir.listFiles()
            ?.filter { it.isFile && it.extension == "csv" }
            ?.filter { lessonPattern.matches(it.name) }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (lessonFiles.isEmpty()) {
            android.util.Log.w("LessonStore", "No lessons found matching pattern in: $externalDirPath")
            return 0
        }

        var loadedCount = 0
        lessonFiles.forEach { file ->
            try {
                val match = lessonPattern.find(file.name) ?: return@forEach
                val lessonNumber = match.groupValues[1]
                val codeMatch = Regex("""[A-Z]\d+""").find(file.name)
                val code = codeMatch?.value ?: "UNKNOWN"

                // Parse the CSV file
                val parseResult = CsvParser.parseLesson(file.inputStream())
                val (title, cards) = parseResult.data ?: return@forEach

                // Create lesson ID from code
                val lessonId = "${languageId}_lesson_${code}"

                // Check if lesson already exists and replace it
                replaceById(languageId, lessonId)

                // Copy file to language directory
                val fileName = "lesson_${code}.csv"
                val dir = languageDir(languageId)
                dir.mkdirs()
                val csvFile = File(dir, fileName)
                AtomicFileWriter.writeText(csvFile, file.readText())

                // Update index
                saveIndex(languageId, LessonIndexEntry(lessonId, title ?: "Lesson $lessonNumber", fileName))

                loadedCount++
                android.util.Log.i("LessonStore", "Loaded external lesson: ${file.name} -> $lessonId")
            } catch (e: Exception) {
                android.util.Log.e("LessonStore", "Failed to load lesson: ${file.name}", e)
            }
        }

        if (loadedCount > 0) {
            invalidateLessonsCache(languageId)
            android.util.Log.i("LessonStore", "Successfully loaded $loadedCount lessons from $externalDirPath")
        }

        return loadedCount
    }

    // ── Story language detection ─────────────────────────────────────────────

    /**
     * Detect which story file to use based on UI language preference.
     *
     * Logic:
     * - If uiLanguage is "ru", try Russian story file first (chapter_XX_original.md)
     * - If uiLanguage is "en" or "system", try English story file first (chapter_XX.md)
     * - Fall back to available file if preferred language doesn't exist
     *
     * @param packId Pack identifier
     * @param chapterId Chapter identifier (e.g., "chapter_01")
     * @param uiLanguage UI language setting ("ru", "en", "system")
     * @return Story filename if found, null otherwise
     */
    override fun detectStoryLanguage(packId: String, chapterId: String, uiLanguage: String): String? {
        val packDir = File(packsDir, packId)
        val storiesDir = File(packDir, "stories")
        if (!storiesDir.exists()) {
            android.util.Log.w("LessonStore", "Stories directory does not exist for pack: $packId")
            return null
        }

        // Normalize chapter ID for filename
        val chapterBase = chapterId.removePrefix("chapter_").trim()
        if (chapterBase.isEmpty()) {
            android.util.Log.w("LessonStore", "Invalid chapter ID: $chapterId")
            return null
        }

        // Determine preferred language
        val preferRussian = when (uiLanguage.lowercase()) {
            "ru" -> true
            "system" -> {
                // Check system locale
                val systemLang = java.util.Locale.getDefault().language
                systemLang == "ru"
            }
            else -> false
        }

        android.util.Log.d("LessonStore", "Detecting story language for chapterId=$chapterId, preferRussian=$preferRussian")

        // List all available story files for this chapter
        val availableFiles = storiesDir.listFiles()?.filter { file ->
            file.name.startsWith("chapter_$chapterBase") && file.name.endsWith(".md")
        } ?: emptyList()

        android.util.Log.d("LessonStore", "Available story files for chapter_$chapterBase: ${availableFiles.map { it.name }}")

        if (availableFiles.isEmpty()) {
            android.util.Log.w("LessonStore", "No story files found for chapter_$chapterBase")
            return null
        }

        // Prioritize based on language preference
        // _original.md files are Russian, others are English translations
        val prioritizedFiles = if (preferRussian) {
            // Prefer _original.md (Russian), then any other variant
            availableFiles.sortedByDescending { it.name.endsWith("_original.md") }
        } else {
            // Prefer non-_original.md files (English variants), then _original.md
            availableFiles.sortedBy { it.name.endsWith("_original.md") }
        }

        android.util.Log.d("LessonStore", "Prioritized story files: ${prioritizedFiles.map { it.name }}")

        for (file in prioritizedFiles) {
            android.util.Log.d("LessonStore", "Trying story file: ${file.name}")
            if (file.exists()) {
                android.util.Log.d("LessonStore", "Found story file for $chapterId: ${file.name} (uiLanguage=$uiLanguage, preferRussian=$preferRussian)")
                return file.name
            }
        }

        android.util.Log.w("LessonStore", "No suitable story file found for chapter: $chapterId")
        return null
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun saveIndex(languageId: String, entry: LessonIndexEntry) {
        val existing = loadIndex(languageId).toMutableList()
        val indexMap = mutableMapOf("id" to entry.id, "title" to entry.title, "file" to entry.fileName)
        if (entry.drillFileName != null) {
            indexMap["drillFile"] = entry.drillFileName
        }
        existing.add(indexMap)
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
                val drillFileName = entry["drillFile"] as? String
                if (drillFileName != null) {
                    val drillFile = File(languageDir(languageId), drillFileName)
                    if (drillFile.exists()) drillFile.delete()
                }
                iterator.remove()
            }
        }
        writeIndex(languageId, entries)
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
                val drillFileName = entry["drillFile"] as? String
                if (drillFileName != null) {
                    val drillFile = File(languageDir(languageId), drillFileName)
                    if (drillFile.exists()) drillFile.delete()
                }
                iterator.remove()
            }
        }
        writeIndex(languageId, entries)
    }

    private fun languageDir(languageId: String): File = File(lessonsDir, languageId)

    private fun indexFileFor(languageId: String): File = File(lessonsDir, "${languageId}_index.yaml")

    private data class LessonIndexEntry(
        val id: String,
        val title: String,
        val fileName: String,
        val drillFileName: String? = null
    )

    // ── Lazy loading private helpers ───────────────────────────────────

    /**
     * Extract the set of CSV filenames declared as lessons in the manifest.
     * v1: lessons[].file  |  v2: chapter lessonIds → "{lessonId}.csv"
     * Only these files are loaded as lessons — drill/vocab CSVs are excluded.
     */
    private fun getManifestLessonFileNames(manifest: LessonPackManifest?): Set<String> {
        if (manifest == null) return emptySet()
        return if (manifest.schemaVersion == 2) {
            // v2: chapters contain lessonIds; filename = "{lessonId}.csv"
            // If manifest.lessons has explicit file mappings, use those
            val lessonIdToFile = manifest.lessons.associate { it.lessonId to it.file }
            manifest.chapters.flatMap { it.lessons }.map { lessonId ->
                lessonIdToFile[lessonId] ?: "$lessonId.csv"
            }.toSet()
        } else {
            // v1: lessons directly list file names
            manifest.lessons.map { it.file }.toSet()
        }
    }

    private fun getOrderedLessonIdsFromManifest(packId: String): List<String> {
        val manifest = languageManager.readInstalledPackManifest(packId) ?: return emptyList()
        return if (manifest.schemaVersion == 2) {
            manifest.chapters.sortedBy { it.order }.flatMap { it.lessons }
        } else {
            manifest.lessons.sortedBy { it.order }.map { it.lessonId }
        }
    }

    /**
     * Sort [lessons] to match the canonical [orderedLessonIds] from the manifest.
     * Lessons not present in the manifest (e.g. stray CSVs) keep their relative
     * order and are placed after the known ones — stable, so the result is
     * deterministic regardless of listFiles()/readdir order on the device FS.
     */
    private fun sortByManifestOrder(
        lessons: List<Lesson>,
        orderedLessonIds: List<String>
    ): List<Lesson> {
        if (orderedLessonIds.isEmpty()) return lessons
        val orderIndex = orderedLessonIds.withIndex().associate { (i, id) -> id to i }
        val knownCount = orderedLessonIds.size
        // Kotlin's sortedBy is stable: unknown lessons (all keyed at knownCount)
        // keep their input relative order and land after every known lesson.
        return lessons.sortedBy { lesson -> orderIndex[lesson.id.value] ?: knownCount }
    }

    private fun findPack(packId: String, languageId: String): LessonPack? {
        return getInstalledPacks().find {
            it.packId.value == packId && it.languageId.value == languageId
        }
    }

    private fun loadLessonMetadataFromDisk(languageId: String, packId: String): List<LessonMetadata> {
        val pack = findPack(packId, languageId) ?: return emptyList()
        val packDir = File(packsDir, pack.packId.value)
        if (!packDir.exists()) return emptyList()

        val lessonIds = getOrderedLessonIdsFromManifest(packId)
        if (lessonIds.isEmpty()) return emptyList()

        val result = mutableListOf<LessonMetadata>()
        for (lessonId in lessonIds) {
            val csvFile = File(packDir, "$lessonId.csv")
            if (!csvFile.exists()) continue
            try {
                val title = CsvParser.parseLessonTitle(csvFile.inputStream()) ?: lessonId
                result.add(LessonMetadata(
                    id = LessonId(lessonId),
                    languageId = LanguageId(languageId),
                    title = title
                ))
            } catch (e: Exception) {
                Log.e("LessonStore", "Failed to parse title for $lessonId", e)
                result.add(LessonMetadata(
                    id = LessonId(lessonId),
                    languageId = LanguageId(languageId),
                    title = lessonId
                ))
            }
        }
        Log.d("LessonStore", "Loaded ${result.size} lesson metadata entries for $packId")
        return result
    }

    private fun loadSingleLessonFromDisk(languageId: String, packId: String, lessonId: String): Lesson? {
        val pack = findPack(packId, languageId) ?: return null
        val packDir = File(packsDir, pack.packId.value)
        val csvFile = File(packDir, "$lessonId.csv")
        if (!csvFile.exists()) {
            Log.w("LessonStore", "Lesson file not found: ${csvFile.absolutePath}")
            return null
        }
        try {
            val parseResult = CsvParser.parseLesson(csvFile.inputStream())
            val (title, cards) = parseResult.data ?: return null
            return Lesson(
                id = LessonId(lessonId),
                languageId = LanguageId(languageId),
                title = title ?: lessonId,
                cards = cards
            )
        } catch (e: Exception) {
            Log.e("LessonStore", "Failed to load lesson $lessonId", e)
            return null
        }
    }
}
