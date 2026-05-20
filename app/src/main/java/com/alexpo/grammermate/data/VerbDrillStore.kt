package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface VerbDrillStore {

    fun loadProgress(): Map<String, VerbDrillComboProgress>

    fun saveProgress(progress: Map<String, VerbDrillComboProgress>)

    fun getComboProgress(key: String): VerbDrillComboProgress?

    fun upsertComboProgress(key: String, progress: VerbDrillComboProgress)

    fun loadAllCardsForPack(targetPackId: String, languageId: String): List<VerbDrillCard>

    fun getCardsForTenses(packId: String, languageId: String, tenses: List<String>): List<VerbDrillCard>

    /** Flush any pending writes to disk immediately. Call at session end, app background, etc. */
    fun flush()

    // ── Last Session Persistence (VD-50) ────────────────────────────────────────

    /**
     * Load the last incomplete verb drill session.
     * Returns null if no session exists.
     * Session is returned regardless of age (no staleness check).
     */
    fun loadLastSession(): VerbDrillLastSessionState?

    /**
     * Save the current session state for potential resume.
     * Called when user exits an incomplete session.
     */
    fun saveLastSession(session: VerbDrillLastSessionState)

    /**
     * Delete the last session state.
     * Called when user completes a session or chooses "Start Fresh".
     */
    fun deleteLastSession()
}

class VerbDrillStoreImpl(
    context: Context,
    private val packId: String? = null
) : VerbDrillStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file: File = if (packId != null) {
        File(baseDir, "drills/$packId/verb_drill_progress.yaml")
    } else {
        File(baseDir, "verb_drill_progress.yaml")
    }
    private val lastSessionFile: File = if (packId != null) {
        File(baseDir, "drills/$packId/verb_drill_last_session.yaml")
    } else {
        File(baseDir, "verb_drill_last_session.yaml")
    }
    private val schemaVersion = 1
    private val mutex = ReentrantLock()

    // In-memory cache for progress data — invalidated on progress save
    private var progressCache: Map<String, VerbDrillComboProgress>? = null

    // In-memory cache for parsed verb drill cards — keyed by "packId:languageId", never invalidated (files don't change at runtime)
    private var cardsCacheKey: String? = null
    private var cardsCache: List<VerbDrillCard>? = null

    override fun loadProgress(): Map<String, VerbDrillComboProgress> {
        progressCache?.let { return it }
        val loaded = loadProgressFromDisk()
        progressCache = loaded
        return loaded
    }

    private fun loadProgressFromDisk(): Map<String, VerbDrillComboProgress> {
        if (!file.exists() || file.length() == 0L) return emptyMap()
        val raw = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null } ?: return emptyMap()
        val data = when (raw) {
            is Map<*, *> -> raw
            else -> return emptyMap()
        }
        val payload = (data["data"] as? Map<*, *>) ?: data
        val today = LocalDate.now().toString()
        val result = mutableMapOf<String, VerbDrillComboProgress>()
        for ((key, value) in payload) {
            val comboKey = key as? String ?: continue
            val entry = value as? Map<*, *> ?: continue
            val group = entry["group"] as? String ?: continue
            val tense = entry["tense"] as? String ?: continue
            val totalCards = (entry["totalCards"] as? Number)?.toInt() ?: 0
            val everShownCardIds = (entry["everShownCardIds"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?.toSet()
                ?: emptySet()
            val lastDate = entry["lastDate"] as? String ?: ""
            val todayShownCardIds = if (lastDate != today) {
                emptySet()
            } else {
                (entry["todayShownCardIds"] as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.toSet()
                    ?: emptySet()
            }
            val updatedLastDate = if (lastDate != today) today else lastDate
            result[comboKey] = VerbDrillComboProgress(
                group = group,
                tense = tense,
                totalCards = totalCards,
                everShownCardIds = everShownCardIds,
                todayShownCardIds = todayShownCardIds,
                lastDate = updatedLastDate
            )
        }
        return result
    }

    override fun saveProgress(progress: Map<String, VerbDrillComboProgress>) {
        progressCache = progress
        persistProgressToDisk()
    }

    override fun getComboProgress(key: String): VerbDrillComboProgress? {
        return loadProgress()[key]
    }

    override fun upsertComboProgress(key: String, progress: VerbDrillComboProgress) = mutex.withLock {
        val all = loadProgress().toMutableMap()
        all[key] = progress
        progressCache = all
        persistProgressToDisk()
    }

    override fun loadAllCardsForPack(targetPackId: String, languageId: String): List<VerbDrillCard> {
        val cacheKey = "$targetPackId:$languageId"
        cardsCacheKey?.let { if (it == cacheKey) return cardsCache ?: emptyList() }
        val cards = loadAllCardsForPackFromDisk(targetPackId, languageId)
        cardsCacheKey = cacheKey
        cardsCache = cards
        return cards
    }

    private fun loadAllCardsForPackFromDisk(targetPackId: String, languageId: String): List<VerbDrillCard> {
        val verbDrillDir = File(baseDir, "drills/$targetPackId/verb_drill")
        if (!verbDrillDir.exists()) return emptyList()
        val files = verbDrillDir.listFiles()
            ?.filter { it.name.startsWith("${languageId}_") && it.extension == "csv" }
            ?: return emptyList()
        val cards = mutableListOf<VerbDrillCard>()
        for (file in files) {
            val (_, parsed) = file.bufferedReader().use { reader ->
                VerbDrillCsvParser.parse(reader)
            }
            cards.addAll(parsed)
        }
        return cards
    }

    override fun getCardsForTenses(packId: String, languageId: String, tenses: List<String>): List<VerbDrillCard> {
        if (tenses.isEmpty()) return emptyList()
        val allCards = loadAllCardsForPack(packId, languageId)
        val tenseSet = tenses.toSet()
        return allCards.filter { it.tense != null && it.tense in tenseSet }
    }

    /**
     * Flush any pending dirty data to disk immediately.
     * No-op: writes are now immediate (no write-behind batching).
     * Kept for API compatibility.
     */
    override fun flush() {
        // No-op: writes are immediate now
    }

    /**
     * Write progress to disk immediately via AtomicFileWriter.
     * Matches WordMasteryStore pattern: immediate write, no batching.
     */
    private fun persistProgressToDisk() {
        val progress = progressCache ?: return
        val comboPayload = linkedMapOf<String, Any>()
        for ((key, value) in progress) {
            comboPayload[key] = linkedMapOf(
                "group" to value.group,
                "tense" to value.tense,
                "totalCards" to value.totalCards,
                "everShownCardIds" to value.everShownCardIds.toList(),
                "todayShownCardIds" to value.todayShownCardIds.toList(),
                "lastDate" to value.lastDate
            )
        }
        val data = linkedMapOf(
            "schemaVersion" to schemaVersion,
            "data" to comboPayload
        )
        AtomicFileWriter.writeText(file, yaml.dump(data))
    }

    /**
     * Invalidate all caches. Called when progress data is externally reset.
     */
    fun invalidateCache() = mutex.withLock {
        progressCache = null
        cardsCacheKey = null
        cardsCache = null
    }

    // ── Last Session Persistence (VD-50) ────────────────────────────────────────

    private var lastSessionCache: VerbDrillLastSessionState? = null

    override fun loadLastSession(): VerbDrillLastSessionState? = mutex.withLock {
        lastSessionCache?.let { return@withLock it }

        if (!lastSessionFile.exists() || lastSessionFile.length() == 0L) return@withLock null

        val loaded = try {
            loadLastSessionFromDisk()
        } catch (e: Exception) {
            return@withLock null
        }

        lastSessionCache = loaded
        return@withLock loaded
    }

    private fun loadLastSessionFromDisk(): VerbDrillLastSessionState? {
        val raw = try { yaml.load<Any>(lastSessionFile.readText()) } catch (_: Exception) { null } ?: return null
        val data = raw as? Map<*, *> ?: return null

        // Parse todayShownCardIds list
        val todayShownCardIds = (data["todayShownCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.toSet()
            ?: emptySet()
        val sessionCardIds = (data["sessionCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()
        val currentIndex = (data["currentIndex"] as? Number)?.toInt() ?: 0

        return VerbDrillLastSessionState(
            selectedTense = data["selectedTense"] as? String,
            selectedGroup = data["selectedGroup"] as? String,
            sortByFrequency = data["sortByFrequency"] as? Boolean ?: false,
            todayShownCardIds = todayShownCardIds,
            sessionCardIds = sessionCardIds,
            currentIndex = currentIndex
        )
    }

    override fun saveLastSession(session: VerbDrillLastSessionState) = mutex.withLock {
        lastSessionCache = session
        persistLastSessionToDisk(session)
    }

    private fun persistLastSessionToDisk(session: VerbDrillLastSessionState) {
        val data = linkedMapOf(
            "schemaVersion" to schemaVersion,
            "selectedTense" to session.selectedTense,
            "selectedGroup" to session.selectedGroup,
            "sortByFrequency" to session.sortByFrequency,
            "todayShownCardIds" to session.todayShownCardIds.toList(),
            "sessionCardIds" to session.sessionCardIds,
            "currentIndex" to session.currentIndex
        )

        AtomicFileWriter.writeText(lastSessionFile, yaml.dump(data))
    }

    override fun deleteLastSession() = mutex.withLock {
        deleteLastSessionInternal()
    }

    private fun deleteLastSessionInternal() {
        lastSessionCache = null
        if (lastSessionFile.exists()) {
            lastSessionFile.delete()
        }
    }
}
