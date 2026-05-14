package com.alexpo.grammermate.data

import android.content.Context
import android.os.Environment
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class BadSentenceEntry(
    val cardId: String,
    val languageId: String,
    val sentence: String,
    val translation: String,
    val mode: String = "training",
    val addedAtMs: Long = System.currentTimeMillis()
)

interface BadSentenceStore {

    fun migrateIfNeeded(lessonStore: LessonStore)

    fun addBadSentence(packId: String, entry: BadSentenceEntry)

    fun addBadSentence(packId: String, cardId: String, languageId: String, sentence: String, translation: String, mode: String = "training")

    fun removeBadSentence(packId: String, cardId: String)

    fun getBadSentences(packId: String): List<BadSentenceEntry>

    fun isBadSentence(packId: String, cardId: String): Boolean

    fun isBadSentence(cardId: String): Boolean

    fun getBadSentenceCount(packId: String): Int

    fun getTotalBadSentenceCount(): Int

    fun exportToTextFile(packId: String): File

    fun exportUnified(): File

    fun clearPack(packId: String)

    fun clearAll()
}

class BadSentenceStoreImpl(private val context: Context) : BadSentenceStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "bad_sentences.yaml")
    private val oldDrillFile = File(baseDir, "drill_bad_sentences.yaml")
    private val mutex = ReentrantLock()

    /** packId -> list of bad sentence entries */
    private var packs: MutableMap<String, MutableList<BadSentenceEntry>> = mutableMapOf()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        val previousPacks = packs
        try {
            val raw = yaml.load<Any>(file.readText()) ?: return
            val data = (raw as? Map<*, *>) ?: return
            val schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 1

            if (schemaVersion == 1) {
                // Old flat format — load into a synthetic "__legacy__" pack
                val items = (data["items"] as? List<*>) ?: return
                val entries = items.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    BadSentenceEntry(
                        cardId = map["cardId"] as? String ?: return@mapNotNull null,
                        languageId = map["languageId"] as? String ?: "",
                        sentence = map["sentence"] as? String ?: "",
                        translation = map["translation"] as? String ?: "",
                        mode = map["mode"] as? String ?: "training",
                        addedAtMs = (map["addedAtMs"] as? Number)?.toLong() ?: 0L
                    )
                }.toMutableList()
                if (entries.isNotEmpty()) {
                    packs["__legacy__"] = entries
                }
            } else {
                // Schema v2: packs keyed structure
                val packsData = data["packs"] as? Map<*, *> ?: return
                for ((packIdObj, packContent) in packsData) {
                    val packId = packIdObj as? String ?: continue
                    val packMap = packContent as? Map<*, *> ?: continue
                    val items = packMap["items"] as? List<*> ?: continue
                    val entries = items.mapNotNull { item ->
                        val map = item as? Map<*, *> ?: return@mapNotNull null
                        BadSentenceEntry(
                            cardId = map["cardId"] as? String ?: return@mapNotNull null,
                            languageId = map["languageId"] as? String ?: "",
                            sentence = map["sentence"] as? String ?: "",
                            translation = map["translation"] as? String ?: "",
                            mode = map["mode"] as? String ?: "training",
                            addedAtMs = (map["addedAtMs"] as? Number)?.toLong() ?: 0L
                        )
                    }.toMutableList()
                    if (entries.isNotEmpty()) {
                        packs[packId] = entries
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BadSentenceStore", "Failed to parse ${file.name}", e)
            packs = previousPacks
        }
    }

    /**
     * One-time migration from schema v1 (flat) + old drill file to schema v2 (pack-scoped).
     * Uses LessonStore to resolve packId from lessonId extracted from card IDs.
     * Called during init in TrainingViewModel.
     */
    override fun migrateIfNeeded(lessonStore: LessonStore) = mutex.withLock {
        ensureLoaded()

        // Merge old drill_bad_sentences.yaml entries into the legacy pack
        if (oldDrillFile.exists()) {
            try {
                val raw = yaml.load<Any>(oldDrillFile.readText()) ?: return@withLock
                val data = (raw as? Map<*, *>) ?: return@withLock
                val items = (data["items"] as? List<*>) ?: return@withLock
                val drillEntries = items.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    BadSentenceEntry(
                        cardId = map["cardId"] as? String ?: return@mapNotNull null,
                        languageId = map["languageId"] as? String ?: "",
                        sentence = map["sentence"] as? String ?: "",
                        translation = map["translation"] as? String ?: "",
                        mode = map["mode"] as? String ?: "verb_drill",
                        addedAtMs = (map["addedAtMs"] as? Number)?.toLong() ?: 0L
                    )
                }
                val legacy = packs.getOrPut("__legacy__") { mutableListOf() }
                for (entry in drillEntries) {
                    if (legacy.none { it.cardId == entry.cardId }) {
                        legacy.add(entry)
                    }
                }
            } catch (_: Exception) {
                // Ignore parse errors on old drill file
            }
        }

        val legacyEntries = packs.remove("__legacy__") ?: emptyList()
        if (legacyEntries.isEmpty() && !oldDrillFile.exists()) {
            // No legacy data and no old drill file — check if we already have v2 data
            if (packs.isNotEmpty()) return@withLock
            // Check if file exists but has no data worth migrating
            if (!file.exists()) return@withLock
            // File exists but no legacy entries and no v2 packs — might be empty file
            return@withLock
        }

        // Group legacy entries by packId using lessonId extracted from cardId
        val grouped = mutableMapOf<String, MutableList<BadSentenceEntry>>()
        for (entry in legacyEntries) {
            val packId = resolvePackIdForCard(entry.cardId, lessonStore) ?: "__unknown__"
            grouped.getOrPut(packId) { mutableListOf() }.add(entry)
        }

        // Merge grouped entries into existing packs
        for ((packId, entries) in grouped) {
            val existing = packs.getOrPut(packId) { mutableListOf() }
            for (entry in entries) {
                if (existing.none { it.cardId == entry.cardId }) {
                    existing.add(entry)
                }
            }
        }

        persist()

        // Delete old drill file after successful migration
        if (oldDrillFile.exists()) {
            oldDrillFile.delete()
        }
    }

    /**
     * Extract lessonId from a cardId and resolve to packId.
     * Regular card IDs: "{lessonId}_{index}" (e.g., "en_word_order_a1_42")
     * Verb drill card IDs: "{group}_{tense}_{rowIndex}" (e.g., "_Presente_7") — harder to resolve.
     */
    private fun resolvePackIdForCard(cardId: String, lessonStore: LessonStore): String? {
        // Try extracting lessonId from the card ID.
        // For regular cards, the pattern is lessonId_index, where lessonId may contain underscores.
        // We try progressively shorter prefixes until we find a matching lesson.
        val parts = cardId.split("_")
        for (i in parts.size - 1 downTo 1) {
            val candidateLessonId = parts.take(i).joinToString("_")
            val packId = lessonStore.getPackIdForLesson(candidateLessonId)
            if (packId != null) return packId
        }
        return null
    }

    override fun addBadSentence(packId: String, entry: BadSentenceEntry) = mutex.withLock {
        ensureLoaded()
        val packEntries = packs.getOrPut(packId) { mutableListOf() }
        if (packEntries.any { it.cardId == entry.cardId }) return@withLock
        packEntries.add(entry)
        persist()
    }

    override fun addBadSentence(packId: String, cardId: String, languageId: String, sentence: String, translation: String, mode: String) = mutex.withLock {
        ensureLoaded()
        val entry = BadSentenceEntry(cardId, languageId, sentence, translation, mode)
        val packEntries = packs.getOrPut(packId) { mutableListOf() }
        if (packEntries.any { it.cardId == entry.cardId }) return@withLock
        packEntries.add(entry)
        persist()
    }

    override fun removeBadSentence(packId: String, cardId: String) = mutex.withLock {
        ensureLoaded()
        packs[packId]?.let { entries ->
            entries.removeAll { it.cardId == cardId }
            if (entries.isEmpty()) packs.remove(packId)
        }
        persist()
    }

    override fun getBadSentences(packId: String): List<BadSentenceEntry> {
        ensureLoaded()
        return packs[packId]?.toList() ?: emptyList()
    }

    override fun isBadSentence(packId: String, cardId: String): Boolean {
        ensureLoaded()
        return packs[packId]?.any { it.cardId == cardId } == true
    }

    /**
     * Backward-compatible: checks all packs for the given cardId.
     * Used during migration period.
     */
    override fun isBadSentence(cardId: String): Boolean {
        ensureLoaded()
        return packs.values.any { entries -> entries.any { it.cardId == cardId } }
    }

    override fun getBadSentenceCount(packId: String): Int {
        ensureLoaded()
        return packs[packId]?.size ?: 0
    }

    /**
     * Total bad sentence count across all packs.
     */
    override fun getTotalBadSentenceCount(): Int {
        ensureLoaded()
        return packs.values.sumOf { it.size }
    }

    override fun exportToTextFile(packId: String): File {
        ensureLoaded()
        val entries = packs[packId] ?: emptyList()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportDir = File(downloadsDir, "BaseGrammy")
        exportDir.mkdirs()
        val exportFile = File(exportDir, "bad_sentences_$packId.txt")
        val lines = entries.map { entry ->
            "ID: ${entry.cardId}\nSource: ${entry.sentence}\nTarget: ${entry.translation}\nLanguage: ${entry.languageId}\nMode: ${entry.mode}\n---"
        }
        AtomicFileWriter.writeText(exportFile, lines.joinToString("\n"))
        return exportFile
    }

    /**
     * Unified export: produces one file with all bad sentences grouped by
     * language, then pack, then mode. Returns the exported file.
     */
    override fun exportUnified(): File {
        ensureLoaded()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportDir = File(downloadsDir, "BaseGrammy")
        exportDir.mkdirs()
        val exportFile = File(exportDir, "bad_sentences_all.txt")

        // Collect all entries with their packId
        val allEntries = mutableListOf<Triple<String, BadSentenceEntry, String>>() // packId, entry, languageId
        for ((packId, entries) in packs) {
            for (entry in entries) {
                allEntries.add(Triple(packId, entry, entry.languageId))
            }
        }

        if (allEntries.isEmpty()) {
            AtomicFileWriter.writeText(exportFile, "No bad sentences reported.")
            return exportFile
        }

        val sb = StringBuilder()
        sb.appendLine("=== Bad Sentences Report ===")
        sb.appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(System.currentTimeMillis())}")
        sb.appendLine("Total entries: ${allEntries.size}")
        sb.appendLine()

        // Group by language
        val byLanguage = allEntries.groupBy { it.third }
        for ((languageId, langEntries) in byLanguage.toSortedMap()) {
            sb.appendLine("## Language: ${languageId.ifBlank { "(unknown)" }}")
            sb.appendLine()

            // Group by pack
            val byPack = langEntries.groupBy { it.first }
            for ((packId, packEntries) in byPack.toSortedMap()) {
                sb.appendLine("  ### Pack: $packId")
                sb.appendLine()

                // Group by mode
                val byMode = packEntries.groupBy { it.second.mode }
                for ((mode, modeEntries) in byMode.toSortedMap()) {
                    sb.appendLine("    #### Mode: $mode (${modeEntries.size} entries)")
                    sb.appendLine()
                    for ((_, entry, _) in modeEntries) {
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(entry.addedAtMs)
                        sb.appendLine("    - ID: ${entry.cardId}")
                        sb.appendLine("      Source: ${entry.sentence}")
                        sb.appendLine("      Target: ${entry.translation}")
                        sb.appendLine("      Reported: $date")
                        sb.appendLine()
                    }
                }
            }
            sb.appendLine()
        }

        AtomicFileWriter.writeText(exportFile, sb.toString())
        return exportFile
    }

    override fun clearPack(packId: String) = mutex.withLock {
        packs.remove(packId)
        persist()
    }

    override fun clearAll() = mutex.withLock {
        packs.clear()
        persist()
    }

    private fun persist() {
        val packsData = linkedMapOf<String, Any>()
        for ((packId, entries) in packs.toSortedMap()) {
            val items = entries.map { entry ->
                linkedMapOf(
                    "cardId" to entry.cardId,
                    "languageId" to entry.languageId,
                    "sentence" to entry.sentence,
                    "translation" to entry.translation,
                    "mode" to entry.mode,
                    "addedAtMs" to entry.addedAtMs
                )
            }
            packsData[packId] = linkedMapOf("items" to items)
        }
        val data = linkedMapOf<String, Any>(
            "schemaVersion" to 2,
            "packs" to packsData
        )
        AtomicFileWriter.writeText(file, yaml.dump(data))
    }
}
