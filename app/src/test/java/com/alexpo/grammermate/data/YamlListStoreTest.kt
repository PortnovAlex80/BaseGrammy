package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.io.path.createTempDirectory

class YamlListStoreTest {
    @Test
    fun readMigratesListFormat() {
        val dir = createTempDirectory().toFile()
        try {
            val file = File(dir, "list.yaml")
            file.writeText("- id: en\n  name: English\n- id: it\n  name: Italian\n")
            val store = YamlListStore(Yaml(), file)
            val items = store.read()
            assertEquals(2, items.size)
            assertEquals("en", items[0]["id"])
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeUsesSchemaWrapper() {
        val dir = createTempDirectory().toFile()
        try {
            val file = File(dir, "list.yaml")
            val store = YamlListStore(Yaml(), file)
            store.write(listOf(mapOf("id" to "en", "name" to "English")))
            val raw = Yaml().load<Map<String, Any>>(file.readText())
            assertEquals(1, raw["schemaVersion"])
        } finally {
            dir.deleteRecursively()
        }
    }
}
