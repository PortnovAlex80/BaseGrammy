package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class HiddenCardStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "hidden_cards.yaml")
    private val mutex = ReentrantLock()

    private var hiddenIds: MutableSet<String> = mutableSetOf()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        val previousIds = hiddenIds
        try {
            val raw = yaml.load<Any>(file.readText()) ?: return
            val data = (raw as? Map<*, *>) ?: return
            val items = (data["hiddenCardIds"] as? List<*>) ?: return
            hiddenIds = items.mapNotNull { it as? String }.toMutableSet()
        } catch (e: Exception) {
            Log.e("HiddenCardStore", "Failed to parse ${file.name}", e)
            hiddenIds = previousIds
        }
    }

    fun hideCard(cardId: String) = mutex.withLock {
        ensureLoaded()
        hiddenIds.add(cardId)
        persist()
    }

    fun unhideCard(cardId: String) = mutex.withLock {
        ensureLoaded()
        hiddenIds.remove(cardId)
        persist()
    }

    fun isHidden(cardId: String): Boolean {
        ensureLoaded()
        return hiddenIds.contains(cardId)
    }

    fun getHiddenCardIds(): Set<String> {
        ensureLoaded()
        return hiddenIds.toSet()
    }

    fun clearAll() = mutex.withLock {
        hiddenIds.clear()
        persist()
    }

    private fun persist() {
        val data = linkedMapOf(
            "schemaVersion" to 1,
            "hiddenCardIds" to hiddenIds.toList()
        )
        AtomicFileWriter.writeText(file, yaml.dump(data))
    }
}
