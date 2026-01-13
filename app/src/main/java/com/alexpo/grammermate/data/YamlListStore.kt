package com.alexpo.grammermate.data

import org.yaml.snakeyaml.Yaml
import java.io.File

class YamlListStore(
    private val yaml: Yaml,
    private val file: File,
    private val schemaVersion: Int = 1
) {
    fun read(): List<Map<String, Any>> {
        if (!file.exists()) return emptyList()
        val root = yaml.load<Any>(file.readText()) ?: return emptyList()
        return when (root) {
            is Map<*, *> -> {
                val items = root["items"] as? List<*>
                items?.filterIsInstance<Map<String, Any>>() ?: emptyList()
            }
            is List<*> -> root.filterIsInstance<Map<String, Any>>()
            else -> emptyList()
        }
    }

    fun write(items: List<Map<String, Any>>) {
        val data = linkedMapOf(
            "schemaVersion" to schemaVersion,
            "items" to items
        )
        AtomicFileWriter.writeText(file, yaml.dump(data))
    }
}
