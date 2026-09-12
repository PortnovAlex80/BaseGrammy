package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Manages drill file queries, story quiz lookups, and vocab entry retrieval.
 * Extracted from LessonStore to keep each file under 500 lines.
 */
internal class DrillFileManager(
    private val context: Context,
    private val baseDir: File,
    private val storiesDir: File,
    private val storiesStore: YamlListStore,
    private val vocabDir: File,
    private val vocabStore: YamlListStore
) {

    // ── Story queries ────────────────────────────────────────────────────

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
            runCatching { StoryQuizParser.parse(file.readText()) }.getOrNull()?.data
        }
    }

    // ── Vocab queries ────────────────────────────────────────────────────

    fun getVocabEntries(lessonId: String, languageId: String): List<VocabEntry> {
        val entries = vocabStore.read()
        val result = entries.flatMap { entry ->
            val entryLesson = entry["lessonId"] as? String ?: return@flatMap emptyList()
            val entryLang = entry["languageId"] as? String ?: return@flatMap emptyList()
            if (!entryLesson.equals(lessonId, ignoreCase = true)) return@flatMap emptyList()
            if (!entryLang.equals(languageId, ignoreCase = true)) return@flatMap emptyList()
            val fileName = entry["file"] as? String ?: return@flatMap emptyList()
            val languageDir = vocabDirForLanguage(entryLang)
            val file = File(languageDir, fileName).takeIf { it.exists() }
                ?: File(vocabDir, fileName).takeIf { it.exists() }
            if (file == null) return@flatMap emptyList()
            val parseResult = runCatching { VocabCsvParser.parse(file.inputStream()) }.getOrNull() ?: return@flatMap emptyList()
            val rows = parseResult.data ?: return@flatMap emptyList()
            rows.mapIndexed { index, row ->
                VocabEntry(
                    id = "${entryLesson}_${index + 1}",
                    lessonId = LessonId(entryLesson),
                    languageId = LanguageId(entryLang),
                    nativeText = row.nativeText,
                    targetText = row.targetText,
                    isHard = row.isHard
                )
            }
        }.toMutableList()

        // Load additional Italian drill vocab from assets
        if (languageId == "it") {
            val drillEntries = ItalianDrillVocabParser.loadAllFromAssets(context, lessonId, languageId)
            result.addAll(drillEntries)
        }

        return result
    }

    fun removeVocabEntries(languageId: String, lessonId: String?) {
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

    fun removeStoriesForLanguage(languageId: String) {
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

    // ── Drill file queries ───────────────────────────────────────────────

    /**
     * Get verb drill CSV files for a specific pack and language.
     *
     * **Drill Detection Logic:**
     * - Directory structure: `grammarmate/drills/{packId}/verb_drill/`
     * - File naming pattern: `{languageId}_*.csv` (e.g., `it_verb_drill.csv`, `en_drills.csv`)
     * - Checks for both language-specific prefix and `.csv` extension
     * - Returns empty list if directory doesn't exist or no matching files found
     *
     * **Usage for conditional button hiding:**
     * ```kotlin
     * val hasVerbDrills = drillFileManager.hasVerbDrill(packId, languageId)
     * // Hide/show verb practice button based on result
     * ```
     *
     * @param packId Pack identifier (e.g., "IT_VERB_GROUPS_ALL")
     * @param languageId Language code (e.g., "it", "en")
     * @return List of verb drill CSV files, empty if none found
     */
    fun getVerbDrillFiles(packId: String, languageId: String): List<File> {
        val drillDir = File(baseDir, "drills/$packId/verb_drill")
        if (drillDir.exists()) {
            val files = drillDir.listFiles()
                ?.filter { it.name.startsWith("${languageId}_") && it.extension == "csv" }
                ?: emptyList()
            if (files.isNotEmpty()) return files
        }
        // Lazy extraction: drill files may not have been extracted if the pack
        // was imported before verbDrill/vocabDrill sections were added to the manifest.
        // Try to extract from the pack directory now.
        extractDrillsFromPack(packId, "verb_drill")
        if (!drillDir.exists()) return emptyList()
        return drillDir.listFiles()
            ?.filter { it.name.startsWith("${languageId}_") && it.extension == "csv" }
            ?: emptyList()
    }

    /**
     * Get all verb drill CSV files for a specific pack (any language prefix).
     *
     * **Pack-Scoped Drill Detection:**
     * - Directory structure: `grammarmate/drills/{packId}/verb_drill/`
     * - Returns ALL `.csv` files regardless of language prefix
     * - Useful for pack-level drill inventory or validation
     *
     * @param packId Pack identifier
     * @return List of all verb drill CSV files in the pack, empty if none found
     */
    fun getVerbDrillFilesForPack(packId: String): List<File> {
        val drillDir = File(baseDir, "drills/$packId/verb_drill")
        if (!drillDir.exists()) return emptyList()
        return drillDir.listFiles()
            ?.filter { it.extension == "csv" }
            ?: emptyList()
    }

    /**
     * Get vocab drill CSV files for a specific pack and language.
     *
     * **Vocab Drill Detection Logic:**
     * - Directory structure: `grammarmate/drills/{packId}/vocab_drill/`
     * - File naming pattern: `{languageId}_*.csv` (e.g., `it_vocab.csv`, `en_words.csv`)
     * - Checks for both language-specific prefix and `.csv` extension
     * - Returns empty list if directory doesn't exist or no matching files found
     *
     * **Usage for conditional button hiding:**
     * ```kotlin
     * val hasVocabDrills = drillFileManager.hasVocabDrill(packId, languageId)
     * // Hide/show vocab practice button based on result
     * ```
     *
     * @param packId Pack identifier (e.g., "IT_VERB_GROUPS_ALL")
     * @param languageId Language code (e.g., "it", "en")
     * @return List of vocab drill CSV files, empty if none found
     */
    fun getVocabDrillFiles(packId: String, languageId: String): List<File> {
        val drillDir = File(baseDir, "drills/$packId/vocab_drill")
        if (drillDir.exists()) {
            val files = drillDir.listFiles()
                ?.filter { it.name.startsWith("${languageId}_") && it.extension == "csv" }
                ?: emptyList()
            if (files.isNotEmpty()) return files
        }
        // Lazy extraction (same as verb drill)
        extractDrillsFromPack(packId, "vocab_drill")
        if (!drillDir.exists()) return emptyList()
        return drillDir.listFiles()
            ?.filter { it.name.startsWith("${languageId}_") && it.extension == "csv" }
            ?: emptyList()
    }

    /**
     * Get all vocab drill CSV files for a specific pack (any language prefix).
     *
     * **Pack-Scoped Vocab Drill Detection:**
     * - Directory structure: `grammarmate/drills/{packId}/vocab_drill/`
     * - Returns ALL `.csv` files regardless of language prefix
     * - Useful for pack-level vocab drill inventory or validation
     *
     * @param packId Pack identifier
     * @return List of all vocab drill CSV files in the pack, empty if none found
     */
    fun getVocabDrillFilesForPack(packId: String): List<File> {
        val drillDir = File(baseDir, "drills/$packId/vocab_drill")
        if (!drillDir.exists()) return emptyList()
        return drillDir.listFiles()
            ?.filter { it.extension == "csv" }
            ?: emptyList()
    }

    /**
     * Fully parsed vocab drill word lists keyed by "packId|languageId".
     * Parsing re-reads every drill CSV (hundreds of KB per pack), so results
     * are cached; rank filtering happens on top of the cached list. Cleared by
     * [invalidateVocabWordsCache] from LessonStore's pack-mutation hooks.
     * Callers must not mutate the returned full-range list.
     */
    private val vocabWordsCache = java.util.concurrent.ConcurrentHashMap<String, List<VocabWord>>()

    fun invalidateVocabWordsCache() {
        vocabWordsCache.clear()
    }

    fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<VocabWord> {
        val allWords = vocabWordsCache.getOrPut("$packId|$languageId") {
            parseAllVocabWords(packId, languageId)
        }
        if (fromRank == 0 && toRank == Int.MAX_VALUE) return allWords
        return allWords.filter { it.rank in fromRank..toRank }
    }

    private fun parseAllVocabWords(packId: String, languageId: String): List<VocabWord> {
        val files = getVocabDrillFiles(packId, languageId)
        val words = mutableListOf<VocabWord>()
        for (file in files) {
            val stream = file.inputStream()
            val fileName = file.name
            val rows = ItalianDrillVocabParser.parse(stream, fileName)
            stream.close()
            val pos = fileName
                .removePrefix("${languageId}_")
                .removePrefix("drill_")
                .removeSuffix(".csv")
            for (row in rows) {
                words.add(VocabWord(
                    id = "${pos}_${row.rank}_${row.word}",
                    word = row.word,
                    pos = pos,
                    rank = row.rank,
                    meaningRu = row.meaningRu,
                    collocations = row.collocations,
                    forms = emptyMap()
                ))
            }
        }
        return words.sortedBy { it.rank }
    }

    /**
     * Check if verb drills exist for a specific pack and language.
     *
     * **Primary Method for Conditional Button Hiding:**
     * - Returns `true` if at least one verb drill file exists for the language
     * - Used to show/hide "Verb Practice" buttons in UI
     * - Pack-scoped: checks only within the specified pack directory
     *
     * **UI Usage Example:**
     * ```kotlin
     * val showVerbButton = lessonStore.hasVerbDrill(packId, languageId)
     * if (showVerbButton) {
     *     VerbPracticeButton(onClick = { /* navigate to verb drill */ })
     * }
     * ```
     *
     * @param packId Pack identifier
     * @param languageId Language code
     * @return true if verb drills exist, false otherwise
     */
    fun hasVerbDrill(packId: String, languageId: String): Boolean {
        return getVerbDrillFiles(packId, languageId).isNotEmpty()
    }

    /**
     * Check if vocab drills exist for a specific pack and language.
     *
     * **Primary Method for Conditional Button Hiding:**
     * - Returns `true` if at least one vocab drill file exists for the language
     * - Used to show/hide "Vocab Practice" buttons in UI
     * - Pack-scoped: checks only within the specified pack directory
     *
     * **UI Usage Example:**
     * ```kotlin
     * val showVocabButton = lessonStore.hasVocabDrill(packId, languageId)
     * if (showVocabButton) {
     *     VocabPracticeButton(onClick = { /* navigate to vocab drill */ })
     * }
     * ```
     *
     * @param packId Pack identifier
     * @param languageId Language code
     * @return true if vocab drills exist, false otherwise
     */
    fun hasVocabDrill(packId: String, languageId: String): Boolean {
        return getVocabDrillFiles(packId, languageId).isNotEmpty()
    }

    /**
     * Legacy: get verb drill files by language only (pre-pack-scoped).
     */
    @Deprecated("Use getVerbDrillFiles(packId, languageId) for pack-scoped drill lookup.")
    fun getVerbDrillFilesLegacy(languageId: String): List<File> {
        val verbDrillDir = File(baseDir, "verb_drill")
        if (!verbDrillDir.exists()) return emptyList()
        return verbDrillDir.listFiles()
            ?.filter { it.name.startsWith("${languageId}_") && it.extension == "csv" }
            ?: emptyList()
    }

    @Deprecated("Use hasVerbDrill(packId, languageId) for pack-scoped drill check.")
    fun hasVerbDrillLessons(languageId: String): Boolean {
        return getVerbDrillFilesLegacy(languageId).isNotEmpty()
    }

    // ── Lazy drill extraction from pack directory ────────────────────────

    /**
     * Extract drill files from the pack directory if the drill target directory is empty.
     *
     * When a pack was imported before `verbDrill`/`vocabDrill` sections were added
     * to the manifest, the drill files were never copied to `drills/{packId}/`.
     * This method reads the manifest from `packs/{packId}/manifest.json`, finds the
     * listed drill files, and copies them to the correct drill directory.
     *
     * @param packId Pack identifier
     * @param drillType "verb_drill" or "vocab_drill"
     */
    private fun extractDrillsFromPack(packId: String, drillType: String) {
        val packDir = File(baseDir, "packs/$packId")
        if (!packDir.exists()) return

        val manifestFile = File(packDir, "manifest.json")
        if (!manifestFile.exists()) return

        try {
            val manifestJson = JSONObject(manifestFile.readText())
            val drillSection = manifestJson.optJSONObject(
                if (drillType == "verb_drill") "verbDrill" else "vocabDrill"
            ) ?: return
            val filesArray = drillSection.optJSONArray("files") ?: return

            val targetDir = File(baseDir, "drills/$packId/$drillType")
            targetDir.mkdirs()

            for (i in 0 until filesArray.length()) {
                val fileName = filesArray.getString(i)
                val source = File(packDir, fileName)
                if (!source.exists()) {
                    Log.w("DrillFileManager", "Lazy extract: source file not found: ${source.absolutePath}")
                    continue
                }
                val target = File(targetDir, source.name)
                if (!target.exists()) {
                    AtomicFileWriter.writeText(target, source.readText())
                    Log.i("DrillFileManager", "Lazy extract: copied $fileName to ${target.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.w("DrillFileManager", "Lazy extract failed for pack=$packId drillType=$drillType", e)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun vocabDirForLanguage(languageId: String): File = File(vocabDir, languageId)
}
