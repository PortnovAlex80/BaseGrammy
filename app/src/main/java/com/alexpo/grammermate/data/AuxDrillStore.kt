package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface AuxDrillStore {

    fun loadProgress(): Map<String, AuxDrillComboProgress>

    fun saveProgress(progress: Map<String, AuxDrillComboProgress>)

    fun upsertComboProgress(key: String, progress: AuxDrillComboProgress)

    /** Flush pending writes to disk. */
    fun flush()
}

class AuxDrillStoreImpl(
    context: Context,
    private val packId: String? = null
) : AuxDrillStore {

    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")

    private val languageId: String = packId?.let {
        val parts = it.split("-")
        if (parts.size >= 2) parts[1] else "en"
    } ?: "en"

    // Pack-scoped path mirrors VerbDrillStoreImpl; filename is distinct so the
    // two stores never share a file.
    private val packDir: File? = packId?.let { File(baseDir, "drills/$it") }
    private val file: File = packDir?.let { File(it, "aux_drill_progress.yaml") }
        ?: File(baseDir, "aux_drill_progress_${languageId}.yaml")

    private val schemaVersion = 1
    private val mutex = ReentrantLock()

    private var progressCache: Map<String, AuxDrillComboProgress>? = null

    override fun loadProgress(): Map<String, AuxDrillComboProgress> {
        progressCache?.let { return it }
        val loaded = loadProgressFromDisk()
        progressCache = loaded
        return loaded
    }

    private fun loadProgressFromDisk(): Map<String, AuxDrillComboProgress> {
        if (!file.exists() || file.length() == 0L) return emptyMap()
        val raw = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null }
            ?: return emptyMap()
        val data = raw as? Map<*, *> ?: return emptyMap()
        val payload = (data["data"] as? Map<*, *>) ?: data
        val result = mutableMapOf<String, AuxDrillComboProgress>()
        for ((key, value) in payload) {
            val comboKey = key as? String ?: continue
            val entry = value as? Map<*, *> ?: continue
            val verb = entry["verb"] as? String ?: continue
            val tense = entry["tense"] as? String ?: continue
            result[comboKey] = AuxDrillComboProgress(
                verb = verb,
                tense = tense,
                totalCards = (entry["totalCards"] as? Int) ?: 0,
                everShownCardIds = (entry["everShownCardIds"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                todayShownCardIds = (entry["todayShownCardIds"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                lastDate = entry["lastDate"] as? String ?: ""
            )
        }
        return result
    }

    override fun saveProgress(progress: Map<String, AuxDrillComboProgress>) {
        progressCache = progress
        persistProgressToDisk()
    }

    override fun upsertComboProgress(key: String, progress: AuxDrillComboProgress) = mutex.withLock {
        val all = loadProgress().toMutableMap()
        all[key] = progress
        progressCache = all
        persistProgressToDisk()
    }

    override fun flush() {
        // Writes are immediate.
    }

    private fun persistProgressToDisk() {
        val progress = progressCache ?: return
        val comboPayload = linkedMapOf<String, Any>()
        for ((key, value) in progress) {
            comboPayload[key] = linkedMapOf(
                "verb" to value.verb,
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
        try {
            AtomicFileWriter.writeText(file, yaml.dump(data))
        } catch (e: IOException) {
            Log.e("AuxDrillStore", "Failed to save aux drill progress: ${file.name}", e)
            throw e
        }
    }
}
