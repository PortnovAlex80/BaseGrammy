package com.alexpo.grammermate.data

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Owns lesson discovery, manifest ordering, CSV reads and both lesson caches. */
internal class LessonCatalog(
    private val packsDir: File,
    private val languageManager: LanguageManager
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

    private fun loadLessonsFromDisk(languageId: String, packId: String): List<Lesson> =
        getOrderedLessonIdsFromManifest(packId).mapNotNull { loadSingleLessonFromDisk(languageId, packId, it) }

    private fun getOrderedLessonIdsFromManifest(packId: String): List<String> {
        val manifest = languageManager.readInstalledPackManifest(packId) ?: return emptyList()
        return if (manifest.schemaVersion == 2) {
            manifest.chapters.sortedBy { it.order }.flatMap { it.lessons }
        } else {
            manifest.lessons.sortedBy { it.order }.map { it.lessonId }
        }
    }

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
            val entry = languageManager.readInstalledPackManifest(packId)?.lessons?.firstOrNull { it.lessonId == lessonId }
            val parsed = parseResult.data
                ?: if (entry?.type == "draft") (entry.title ?: lessonId) to emptyList() else return null
            val (title, parsedCards) = parsed
            val cards = parsedCards.mapIndexed { index, card ->
                card.copy(
                    id = if (entry?.type == "personal") "${lessonId}_$index" else card.id,
                    sourcePackId = packId,
                    sourceLessonId = lessonId
                )
            }
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
