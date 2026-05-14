package com.alexpo.grammermate.data

import android.content.Context
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
            runCatching { StoryQuizParser.parse(file.readText()) }.getOrNull()
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
            val rows = runCatching { VocabCsvParser.parse(file.inputStream()) }.getOrNull() ?: return@flatMap emptyList()
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
     * Looks in grammarmate/drills/{packId}/verb_drill/.
     */
    fun getVerbDrillFiles(packId: String, languageId: String): List<File> {
        val drillDir = File(baseDir, "drills/$packId/verb_drill")
        if (!drillDir.exists()) return emptyList()
        return drillDir.listFiles()
            ?.filter { it.name.startsWith("${languageId}_") && it.extension == "csv" }
            ?: emptyList()
    }

    /**
     * Get all verb drill CSV files for a specific pack (any language prefix).
     * Looks in grammarmate/drills/{packId}/verb_drill/.
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
     * Looks in grammarmate/drills/{packId}/vocab_drill/.
     */
    fun getVocabDrillFiles(packId: String, languageId: String): List<File> {
        val drillDir = File(baseDir, "drills/$packId/vocab_drill")
        if (!drillDir.exists()) return emptyList()
        return drillDir.listFiles()
            ?.filter { it.name.startsWith("${languageId}_") && it.extension == "csv" }
            ?: emptyList()
    }

    /**
     * Get all vocab drill CSV files for a specific pack (any language prefix).
     * Looks in grammarmate/drills/{packId}/vocab_drill/.
     */
    fun getVocabDrillFilesForPack(packId: String): List<File> {
        val drillDir = File(baseDir, "drills/$packId/vocab_drill")
        if (!drillDir.exists()) return emptyList()
        return drillDir.listFiles()
            ?.filter { it.extension == "csv" }
            ?: emptyList()
    }

    fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<VocabWord> {
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
                if (row.rank in fromRank..toRank) {
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
        }
        return words.sortedBy { it.rank }
    }

    fun hasVerbDrill(packId: String, languageId: String): Boolean {
        return getVerbDrillFiles(packId, languageId).isNotEmpty()
    }

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

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun vocabDirForLanguage(languageId: String): File = File(vocabDir, languageId)
}
