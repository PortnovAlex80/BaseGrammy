package com.alexpo.grammermate.data

import org.yaml.snakeyaml.Yaml
import java.security.MessageDigest

/** A completed modern backup declares every file, including an intentionally empty snapshot. */
internal object BackupSnapshotManifest {
    const val FILE_NAME = "snapshot_manifest.yaml"
    private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun encode(snapshot: Map<String, String>): String = Yaml().dump(mapOf(
        "schemaVersion" to 1, "files" to snapshot.mapValues { digest(it.value) }
    ))

    fun accepts(snapshot: Map<String, String>, manifest: String?): Boolean {
        if (manifest == null) return snapshot.isNotEmpty() // Historical directory backups.
        return try {
            val root = Yaml().load<Any>(manifest) as? Map<*, *> ?: return false
            if (root["schemaVersion"] != 1) return false
            val files = root["files"] as? Map<*, *> ?: return false
            files.keys == snapshot.keys && snapshot.all { (path, text) -> files[path] == digest(text) }
        } catch (_: Exception) { false }
    }
}
