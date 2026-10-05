package com.alexpo.grammermate.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
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

    // -- Story language detection --

    fun detectStoryLanguage(packId: String, chapterId: String, uiLanguage: String): String?

}

/**
 * Primary data store for lessons, packs, languages, drills, stories, and vocab.
 *
 * Delegates to:
 * - [LanguageManager] for language/pack CRUD and seed data
 * - [LessonCatalog] for lesson discovery, loading and cache ownership
 * - [PackImporter] for ZIP import, SAF URI import, manifest handling
 * - [DrillFileManager] for drill file queries, story/vocab lookups
 * - [PackChapterStoryRepository] for pack chapter and story reads
 *
 * All existing public method signatures are preserved — consumers import LessonStore only.
 */
class LessonStoreImpl(private val context: Context) : LessonStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    init { PackInstallTransaction.recover(baseDir) }
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

    private val defaultPacks = listOf(
        LanguageManager.DefaultPack("EN_WORD_ORDER_A1", "grammarmate/packs/EN_WORD_ORDER_A1.zip"),
        LanguageManager.DefaultPack("EN_WORD_ORDER_A1_DRILLS", "grammarmate/packs/EN_WORD_ORDER_A1_DRILLS.zip"),
        // TODO: re-enable when Italian verb groups pack is needed
        // LanguageManager.DefaultPack("IT_VERB_GROUPS_ALL", "grammarmate/packs/IT_VERB_GROUPS_ALL.zip"),
        LanguageManager.DefaultPack("ITALIAN_SHORT", "grammarmate/packs/ITALIAN_EXPRESS_SHORT.zip"),
        LanguageManager.DefaultPack("ITALIAN_FULL_COURSE", "grammarmate/packs/ITALIAN_FULL_COURSE.zip"),
        LanguageManager.DefaultPack("ITALIAN_IMPERIAL", "grammarmate/packs/ITALIAN_IMPERIAL_COURSE.zip"),
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

    private val lessonCatalog = LessonCatalog(packsDir, languageManager)

    private val chapterStories = PackChapterStoryRepository(context, packsDir, languageManager::readInstalledPackManifest)

    private val drillFileManager = DrillFileManager(
        context, baseDir, storiesDir, storiesStore, vocabDir, vocabStore
    )

    private val packImporter = PackImporter(
        context, baseDir, packsDir, packsStore, languagesStore, storiesStore, vocabStore
    )

    // ── Seed & defaults ──────────────────────────────────────────────────

    private var checkedLegacyPersonalLessons = false

    @Synchronized
    override fun ensureSeedData() {
        languageManager.ensureSeedData()
        if (!checkedLegacyPersonalLessons) {
            if (packImporter.migrateLegacyPersonalLessons()) invalidatePackCaches()
            checkedLegacyPersonalLessons = true
        }
    }

    /** Drop every pack-derived cache (lessons + manifests) after pack mutations. */
    private fun invalidatePackCaches() {
        invalidateLessonsCache()
        languageManager.invalidateManifestCache()
        GrammarChipStore.reloadIfInitialized(packsDir)
    }

    override fun seedDefaultPacksIfNeeded(): Boolean = run {
        val seeded = languageManager.seedDefaultPacksIfNeeded { path ->
            packImporter.importPackFromAssets(path)
            true
        }
        if (seeded) invalidatePackCaches()
        seeded
    }

    override fun updateDefaultPacksIfNeeded(): Boolean = run {
        val updated = languageManager.updateDefaultPacksIfNeeded(
            importFromAssets = { path ->
                packImporter.importPackFromAssets(path)
                true
            },
            readManifestFromAssets = { path -> packImporter.readPackManifestFromAssets(path) }
        )
        if (updated) invalidatePackCaches()
        updated
    }

    override fun forceReloadDefaultPacks(): Boolean = run {
        val result = languageManager.forceReloadDefaultPacks(
            importFromAssets = { path ->
                packImporter.importPackFromAssets(path)
                true
            }
        )
        if (result) invalidatePackCaches()
        result
    }

    // ── Language & pack queries ──────────────────────────────────────────

    override fun getLanguages(): List<Language> = languageManager.getLanguages()

    override fun getLanguagesWithPacks(): List<Language> = languageManager.getLanguagesWithPacks()

    override fun addLanguage(name: String): Language = languageManager.addLanguage(name)

    override fun getInstalledPacks(): List<LessonPack> = languageManager.getInstalledPacks()

    override fun getPackIdForLesson(lessonId: String): String? = lessonCatalog.getPackIdForLesson(lessonId)

    override fun getLessonIdsForPack(packId: String): List<String> = lessonCatalog.getLessonIdsForPack(packId)

    override fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String> =
        lessonCatalog.getCumulativeTenses(packId, lessonLevel)

    // ── Pack import ──────────────────────────────────────────────────────

    override fun importPackFromUri(uri: Uri, resolver: ContentResolver): LessonPack = run {
        ensureSeedData()
        try { packImporter.importPackFromUri(uri, resolver) }
        finally { invalidatePackCaches() }
    }

    override fun importPackFromAssets(assetPath: String): LessonPack = run {
        ensureSeedData()
        try { packImporter.importPackFromAssets(assetPath) }
        finally { invalidatePackCaches() }
    }

    // ── Pack removal ─────────────────────────────────────────────────────

    override fun removeInstalledPackData(packId: String): Boolean {
        val manifest = languageManager.readInstalledPackManifest(packId)
        val result = if (manifest != null) {
            val languageId = manifest.language.lowercase().trim()
            if (languageId.isNotBlank()) {
                manifest.lessons.forEach { lesson ->
                    deleteLegacyLesson(languageId, lesson.lessonId)
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
        invalidatePackCaches()
        return result
    }

    // ── CSV lesson import ────────────────────────────────────────────────

    override fun importFromUri(languageId: String, uri: Uri, resolver: ContentResolver): Lesson {
        ensureSeedData()
        val (lesson, errors) = packImporter.importLessonFromUri(languageId, uri, resolver)

        // Log any errors that occurred during import
        errors.forEach { error ->
            android.util.Log.w("LessonStore", error.toUserMessage())
        }

        invalidatePackCaches()
        return lesson
    }

    override fun importFromUriWithErrors(languageId: String, uri: Uri, resolver: ContentResolver): Pair<Lesson, List<ParseError>> {
        ensureSeedData()
        val result = packImporter.importLessonFromUri(languageId, uri, resolver)
        invalidatePackCaches()
        return result
    }

    // ── Lesson CRUD ──────────────────────────────────────────────────────

    override fun getLessons(packId: String, languageId: String): List<Lesson> {
        ensureSeedData()
        return lessonCatalog.getLessons(packId, languageId)
    }

    override fun getLessonMetadata(packId: String, languageId: String): List<LessonMetadata> {
        ensureSeedData()
        return lessonCatalog.getLessonMetadata(packId, languageId)
    }

    override fun getLesson(packId: String, languageId: String, lessonId: String): Lesson? {
        ensureSeedData()
        return lessonCatalog.getLesson(packId, languageId, lessonId)
    }

    override fun getLessonCount(packId: String, languageId: String): Int {
        ensureSeedData()
        return lessonCatalog.getLessonCount(packId, languageId)
    }

    override fun getLessonIdAtIndex(packId: String, languageId: String, index: Int): String? {
        ensureSeedData()
        return lessonCatalog.getLessonIdAtIndex(packId, languageId, index)
    }

    override fun getCardsForLesson(packId: String, languageId: String, lessonId: String): List<SentenceCard> {
        ensureSeedData()
        return lessonCatalog.getCardsForLesson(packId, languageId, lessonId)
    }

    /** Evict lesson reads and drill-word reads after a store mutation. */
    fun invalidateLessonsCache(languageId: String? = null) {
        lessonCatalog.invalidate(languageId)
        drillFileManager.invalidateVocabWordsCache()
    }

    override fun deleteAllLessons(languageId: String) {
        val dir = languageDir(languageId)
        if (dir.exists()) dir.deleteRecursively()
        val index = indexFileFor(languageId)
        if (index.exists()) index.delete()
        drillFileManager.removeVocabEntries(languageId, null)
        drillFileManager.removeStoriesForLanguage(languageId)
        languageManager.removePacksForLanguage(languageId)
        invalidatePackCaches()
    }

    override fun deleteLesson(languageId: String, lessonId: String) {
        ensureSeedData()
        val userPack = getInstalledPacks().firstOrNull { pack ->
            pack.packId.value.startsWith("USER_") && pack.languageId.value == languageId &&
                languageManager.readInstalledPackManifest(pack.packId.value)?.lessons?.singleOrNull()?.let {
                    it.lessonId == lessonId && it.type in setOf("personal", "draft")
                } == true
        }
        if (userPack != null) removeInstalledPackData(userPack.packId.value)
        else deleteLegacyLesson(languageId, lessonId)
    }

    private fun deleteLegacyLesson(languageId: String, lessonId: String) {
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
        val lesson = packImporter.createEmptyLesson(languageId, title)
        invalidatePackCaches()
        return lesson
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


    // ── Chapter queries (Grammar Story Roadmap) ───────────────────────────

    override fun getChapters(packId: String): List<Chapter> = chapterStories.getChapters(packId)

    override fun getChapterStory(packId: String, storyFile: String): String? =
        chapterStories.getChapterStory(packId, storyFile)

    fun getChapterStoryWithLanguageDetection(packId: String, chapterId: String, uiLanguage: String): String? =
        chapterStories.getChapterStoryWithLanguageDetection(packId, chapterId, uiLanguage)

    override fun hasChapters(packId: String): Boolean = chapterStories.hasChapters(packId)

    // ── External lesson loading ─────────────────────────────────────────────

    override fun detectStoryLanguage(packId: String, chapterId: String, uiLanguage: String): String? =
        chapterStories.detectStoryLanguage(packId, chapterId, uiLanguage)

    // ── Private helpers ──────────────────────────────────────────────────

    private fun loadIndex(languageId: String): List<Map<String, Any>> = readIndexFromDisk(languageId)

    private fun writeIndex(languageId: String, entries: List<Map<String, Any>>) = writeIndexToDisk(languageId, entries)

    private fun readIndexFromDisk(languageId: String): List<Map<String, Any>> {
        val indexFile = indexFileFor(languageId)
        if (!indexFile.exists()) return emptyList()
        val store = YamlListStore(yaml, indexFile)
        return store.read()
    }

    private fun writeIndexToDisk(languageId: String, entries: List<Map<String, Any>>) {
        val indexFile = indexFileFor(languageId)
        val store = YamlListStore(yaml, indexFile)
        store.write(entries)
    }

    private fun languageDir(languageId: String): File = File(lessonsDir, languageId)

    private fun indexFileFor(languageId: String): File = File(lessonsDir, "${languageId}_index.yaml")


    // ── Lazy loading private helpers ───────────────────────────────────


}
