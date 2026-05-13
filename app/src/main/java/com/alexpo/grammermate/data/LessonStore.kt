package com.alexpo.grammermate.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
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

    // -- Lesson CRUD --

    fun getLessons(languageId: String): List<Lesson>

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

    private val defaultPacks = listOf(
        LanguageManager.DefaultPack("EN_WORD_ORDER_A1", "grammarmate/packs/EN_WORD_ORDER_A1.zip"),
        LanguageManager.DefaultPack("IT_VERB_GROUPS_ALL", "grammarmate/packs/IT_VERB_GROUPS_ALL.zip")
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

    override fun seedDefaultPacksIfNeeded(): Boolean = languageManager.seedDefaultPacksIfNeeded { path ->
        packImporter.importPackFromAssets(path)
        true
    }

    override fun updateDefaultPacksIfNeeded(): Boolean = languageManager.updateDefaultPacksIfNeeded(
        importFromAssets = { path ->
            packImporter.importPackFromAssets(path)
            true
        },
        readManifestFromAssets = { path -> packImporter.readPackManifestFromAssets(path) }
    )

    override fun forceReloadDefaultPacks(): Boolean = languageManager.forceReloadDefaultPacks(
        removeInstalledPackData = { packId -> removeInstalledPackData(packId) },
        importFromAssets = { path ->
            packImporter.importPackFromAssets(path)
            true
        }
    )

    // ── Language & pack queries ──────────────────────────────────────────

    override fun getLanguages(): List<Language> = languageManager.getLanguages()

    override fun addLanguage(name: String): Language = languageManager.addLanguage(name)

    override fun getInstalledPacks(): List<LessonPack> = languageManager.getInstalledPacks()

    override fun getPackIdForLesson(lessonId: String): String? {
        val packs = getInstalledPacks()
        for (pack in packs) {
            val manifest = languageManager.readInstalledPackManifest(pack.packId) ?: continue
            if (manifest.lessons.any { it.lessonId == lessonId }) {
                return pack.packId
            }
        }
        return null
    }

    override fun getLessonIdsForPack(packId: String): List<String> {
        val manifest = languageManager.readInstalledPackManifest(packId) ?: return emptyList()
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
        return packImporter.importPackFromUri(uri, resolver)
    }

    override fun importPackFromAssets(assetPath: String): LessonPack {
        ensureSeedData()
        return packImporter.importPackFromAssets(assetPath)
    }

    // ── Pack removal ─────────────────────────────────────────────────────

    override fun removeInstalledPackData(packId: String): Boolean {
        val manifest = languageManager.readInstalledPackManifest(packId)
        if (manifest != null) {
            val languageId = manifest.language.lowercase().trim()
            if (languageId.isNotBlank()) {
                manifest.lessons.forEach { lesson ->
                    deleteLesson(languageId, lesson.lessonId)
                }
                languageManager.removePacksForLanguage(packId, languageId)
                languageManager.deletePackDrills(packId)
                return true
            }
        }
        val removedEntry = languageManager.removePackEntry(packId)
        val packDir = File(packsDir, packId)
        val removedDir = if (packDir.exists()) packDir.deleteRecursively() else false
        languageManager.deletePackDrills(packId)
        return removedEntry || removedDir
    }

    // ── CSV lesson import ────────────────────────────────────────────────

    override fun importFromUri(languageId: String, uri: Uri, resolver: ContentResolver): Lesson {
        return packImporter.importLessonFromUri(
            languageId, uri, resolver,
            ensureSeedData = { ensureSeedData() },
            loadIndex = { loadIndex(it) },
            writeIndex = { langId, entries -> writeIndex(langId, entries) }
        )
    }

    // ── Lesson CRUD ──────────────────────────────────────────────────────

    override fun getLessons(languageId: String): List<Lesson> {
        ensureSeedData()
        val entries = loadIndex(languageId)
        return entries.mapNotNull { entry ->
            val id = entry["id"] as? String ?: return@mapNotNull null
            val title = entry["title"] as? String ?: "Lesson"
            val fileName = entry["file"] as? String ?: return@mapNotNull null
            val csvFile = File(languageDir(languageId), fileName)
            if (!csvFile.exists()) return@mapNotNull null
            val (parsedTitle, cards) = CsvParser.parseLesson(csvFile.inputStream())
            val drillFileName = entry["drillFile"] as? String
            val drillCards = if (drillFileName != null) {
                val drillFile = File(languageDir(languageId), drillFileName)
                if (drillFile.exists()) {
                    val (_, parsedDrillCards) = CsvParser.parseLesson(drillFile.inputStream())
                    parsedDrillCards
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
            Lesson(id = id, languageId = languageId, title = parsedTitle ?: title, cards = cards, drillCards = drillCards)
        }
    }

    override fun deleteAllLessons(languageId: String) {
        val dir = languageDir(languageId)
        if (dir.exists()) dir.deleteRecursively()
        val index = indexFileFor(languageId)
        if (index.exists()) index.delete()
        drillFileManager.removeVocabEntries(languageId, null)
        drillFileManager.removeStoriesForLanguage(languageId)
        languageManager.removePacksForLanguage(languageId)
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
        return Lesson(id = id, languageId = languageId, title = normalizedTitle, cards = emptyList())
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
}
