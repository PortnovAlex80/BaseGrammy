package com.alexpo.grammermate.data

import android.content.Context
import android.os.Environment
import org.yaml.snakeyaml.Yaml
import java.io.File

data class BadSentenceEntry(
    val cardId: String,
    val languageId: String,
    val sentence: String,
    val translation: String,
    val addedAtMs: Long = System.currentTimeMillis()
)

class BadSentenceStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "bad_sentences.yaml")

    private var entries: MutableList<BadSentenceEntry> = mutableListOf()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        try {
            val raw = yaml.load<Any>(file.readText()) ?: return
            val data = (raw as? Map<*, *>) ?: return
            val items = (data["items"] as? List<*>) ?: return
            entries = items.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                BadSentenceEntry(
                    cardId = map["cardId"] as? String ?: return@mapNotNull null,
                    languageId = map["languageId"] as? String ?: "",
                    sentence = map["sentence"] as? String ?: "",
                    translation = map["translation"] as? String ?: "",
                    addedAtMs = (map["addedAtMs"] as? Number)?.toLong() ?: 0L
                )
            }.toMutableList()
        } catch (_: Exception) {
            entries = mutableListOf()
        }
    }

    fun addBadSentence(cardId: String, languageId: String, sentence: String, translation: String) {
        ensureLoaded()
        if (entries.any { it.cardId == cardId }) return
        entries.add(BadSentenceEntry(cardId, languageId, sentence, translation))
        persist()
    }

    fun removeBadSentence(cardId: String) {
        ensureLoaded()
        entries.removeAll { it.cardId == cardId }
        persist()
    }

    fun getBadSentences(): List<BadSentenceEntry> {
        ensureLoaded()
        return entries.toList()
    }

    fun isBadSentence(cardId: String): Boolean {
        ensureLoaded()
        return entries.any { it.cardId == cardId }
    }

    fun exportToTextFile(): File {
        ensureLoaded()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportDir = File(downloadsDir, "BaseGrammy")
        exportDir.mkdirs()
        val exportFile = File(exportDir, "bad_sentences.txt")
        val lines = entries.map { entry ->
            "ID: ${entry.cardId}\nSource: ${entry.sentence}\nTarget: ${entry.translation}\nLanguage: ${entry.languageId}\n---"
        }
        AtomicFileWriter.writeText(exportFile, lines.joinToString("\n"))
        return exportFile
    }

    fun clearAll() {
        entries.clear()
        persist()
    }

    private fun persist() {
        val items = entries.map { entry ->
            linkedMapOf(
                "cardId" to entry.cardId,
                "languageId" to entry.languageId,
                "sentence" to entry.sentence,
                "translation" to entry.translation,
                "addedAtMs" to entry.addedAtMs
            )
        }
        val data = linkedMapOf(
            "schemaVersion" to 1,
            "items" to items
        )
        AtomicFileWriter.writeText(file, yaml.dump(data))
    }
}
