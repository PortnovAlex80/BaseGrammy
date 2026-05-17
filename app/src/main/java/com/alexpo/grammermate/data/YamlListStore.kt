package com.alexpo.grammermate.data

import org.yaml.snakeyaml.Yaml
import java.io.File

class YamlListStore(
    private val yaml: Yaml,
    private val file: File,
    private val schemaVersion: Int = 1
) {
    @Volatile
    private var cachedData: List<Map<String, Any>>? = null
    @Volatile
    private var cacheValid = false

    fun read(): List<Map<String, Any>> {
        if (cacheValid) {
            val cached = cachedData
            if (cached != null) return cached
        }
        val data = if (!file.exists() || file.length() == 0L) {
            emptyList()
        } else {
            val root = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null } ?: emptyList<Any>()
            when (root) {
                is Map<*, *> -> {
                    val items = root["items"] as? List<*>
                    items?.filterIsInstance<Map<String, Any>>() ?: emptyList()
                }
                is List<*> -> root.filterIsInstance<Map<String, Any>>()
                else -> emptyList()
            }
        }
        cachedData = data
        cacheValid = true
        return data
    }

    fun write(items: List<Map<String, Any>>) {
        cacheValid = false
        cachedData = null
        val data = linkedMapOf(
            "schemaVersion" to schemaVersion,
            "items" to items
        )
        AtomicFileWriter.writeText(file, yaml.dump(data))
    }

    fun invalidateCache() {
        cacheValid = false
        cachedData = null
    }
}
