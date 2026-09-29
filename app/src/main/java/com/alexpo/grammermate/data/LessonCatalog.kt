package com.alexpo.grammermate.data

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Owns lesson discovery, manifest ordering, CSV reads and both lesson caches. */
internal class LessonCatalog(
    private val packsDir: File,
    private val lessonsDir: File,
    private val languageManager: LanguageManager,
    private val loadIndex: (String) -> List<Map<String, Any>>
) {
    private val lessonsCache = ConcurrentHashMap<String, List<Lesson>>()
    private val metadataCache = ConcurrentHashMap<String, List<LessonMetadata>>()

    fun invalidate(languageId: String? = null) {
        if (languageId == null) {
            lessonsCache.clear()
        } else {
            lessonsCache.remove(languageId)
            for (key in lessonsCache.keys) {
                if (key.endsWith(":$languageId")) lessonsCache.remove(key)
            }
        }
        metadataCache.clear()
    }

    fun getPackIdForLesson(lessonId: String): String? {
        val packs = languageManager.getInstalledPacks()
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

    fun getLessonIdsForPack(packId: String): List<String> {
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

    fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String> {
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

    fun getLessons(languageId: String): List<Lesson> {
        return lessonsCache.getOrPut(languageId) {
            Log.d("LessonStore", "Cache miss for language: $languageId, loading from disk")
            loadLessonsFromDisk(languageId)
        }
    }

    fun getLessons(packId: String, languageId: String): List<Lesson> {
        val cacheKey = "$packId:$languageId"
        return lessonsCache.getOrPut(cacheKey) {
            Log.d("LessonStore", "Cache miss for pack:language: $cacheKey, loading from disk")
            loadLessonsFromDisk(languageId, packId)
        }
    }

    fun getLessonMetadata(packId: String, languageId: String): List<LessonMetadata> {
        val cacheKey = "meta:$packId:$languageId"
        return metadataCache.getOrPut(cacheKey) {
            Log.d("LessonStore", "Loading lesson metadata for $cacheKey")
            loadLessonMetadataFromDisk(languageId, packId)
        }
    }

    fun getLesson(packId: String, languageId: String, lessonId: String): Lesson? {
        // Check if already in lessons cache
        val cacheKey = "$packId:$languageId"
        lessonsCache[cacheKey]?.find { it.id.value == lessonId }?.let { return it }
        // Load single lesson from disk
        return loadSingleLessonFromDisk(languageId, packId, lessonId)
    }

    fun getLessonCount(packId: String, languageId: String): Int {
        return getOrderedLessonIdsFromManifest(packId).size
    }

    fun getLessonIdAtIndex(packId: String, languageId: String, index: Int): String? {
        return getOrderedLessonIdsFromManifest(packId).getOrNull(index)
    }

    fun getCardsForLesson(packId: String, languageId: String, lessonId: String): List<SentenceCard> {
        return getLesson(packId, languageId, lessonId)?.cards ?: emptyList()
    }

    private fun loadLessonsFromDisk(languageId: String): List<Lesson> {
        // Try loading from packs FIRST (new schema v2 structure)
        val packLessons = mutableListOf<Lesson>()
        val installedPacks = languageManager.getInstalledPacks()

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
            val csvFile = File(File(lessonsDir, languageId), fileName)
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
        val installedPacks = languageManager.getInstalledPacks()

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

    /**
     * Resolve the CSV filename for [lessonId] in [packId].
     *
     * Mirrors the rule in [getManifestLessonFileNames]: an explicit `file` in
     * manifest.lessons wins, otherwise the filename is "<lessonId>.csv". Works for both
     * schema v1 (lessons listed at the root) and v2 (lesson IDs listed in chapters).
     */
    private fun lessonFileNameFor(packId: String, lessonId: String): String {
        val manifest = languageManager.readInstalledPackManifest(packId)
        val explicit = manifest?.lessons?.firstOrNull { it.lessonId == lessonId }?.file
        return explicit ?: "$lessonId.csv"
    }

    private fun findPack(packId: String, languageId: String): LessonPack? {
        return languageManager.getInstalledPacks().find {
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
            val csvFile = File(packDir, lessonFileNameFor(packId, lessonId))
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
        val csvFile = File(packDir, lessonFileNameFor(packId, lessonId))
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
