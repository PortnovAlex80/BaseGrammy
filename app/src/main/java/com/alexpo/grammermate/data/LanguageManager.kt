package com.alexpo.grammermate.data

import java.io.File

/**
 * Manages languages, installed packs, and default pack seeding.
 * Extracted from LessonStore to keep each file under 500 lines.
 */
internal class LanguageManager(
    private val baseDir: File,
    private val lessonsDir: File,
    private val packsDir: File,
    private val languagesFile: File,
    private val languagesStore: YamlListStore,
    private val packsStore: YamlListStore,
    private val seedMarker: File,
    private val defaultPacks: List<DefaultPack>
) {

    // ── Seed data ────────────────────────────────────────────────────────

    fun ensureSeedData() {
        if (!lessonsDir.exists()) {
            lessonsDir.mkdirs()
        }
        if (!languagesFile.exists()) {
            val defaults = listOf(
                mapOf("id" to "en", "name" to "English"),
                mapOf("id" to "it", "name" to "Italian")
            )
            languagesStore.write(defaults)
        }
    }

    fun hasLessonContent(): Boolean {
        if (!lessonsDir.exists()) return false
        return lessonsDir.walkTopDown().any { it.isFile && it.extension.equals("csv", ignoreCase = true) }
    }

    fun seedDefaultPacksIfNeeded(
        importFromAssets: (String) -> Boolean
    ): Boolean {
        ensureSeedData()
        if (seedMarker.exists()) return false
        if (hasLessonContent()) {
            AtomicFileWriter.writeText(seedMarker, "skip")
            return false
        }
        var seededAny = false
        defaultPacks.forEach { pack ->
            val seeded = runCatching { importFromAssets(pack.assetPath) }.isSuccess
            if (seeded) seededAny = true
        }
        AtomicFileWriter.writeText(seedMarker, if (seededAny) "ok" else "none")
        return seededAny
    }

    fun updateDefaultPacksIfNeeded(
        importFromAssets: (String) -> Boolean,
        readManifestFromAssets: (String) -> LessonPackManifest?
    ): Boolean {
        ensureSeedData()
        val installed = getInstalledPacks()
        var updatedAny = false
        defaultPacks.forEach { pack ->
            val manifest = runCatching { readManifestFromAssets(pack.assetPath) }.getOrNull() ?: return@forEach
            val existing = installed.firstOrNull { it.packId.value == manifest.packId }
            val shouldUpdate = existing == null || existing.packVersion != manifest.packVersion
            if (shouldUpdate) {
                val updated = runCatching { importFromAssets(pack.assetPath) }.isSuccess
                if (updated) updatedAny = true
            }
        }
        return updatedAny
    }

    fun forceReloadDefaultPacks(
        removeInstalledPackData: (String) -> Boolean,
        importFromAssets: (String) -> Boolean
    ): Boolean {
        ensureSeedData()
        var reloadedAny = false
        defaultPacks.forEach { pack ->
            val removed = removeInstalledPackData(pack.packId)
            val reloaded = runCatching { importFromAssets(pack.assetPath) }.isSuccess
            if (removed || reloaded) reloadedAny = true
        }
        return reloadedAny
    }

    // ── Language CRUD ────────────────────────────────────────────────────

    fun getLanguages(): List<Language> {
        ensureSeedData()
        val entries = languagesStore.read()
        return entries.mapNotNull { entry ->
            val id = entry["id"] as? String ?: return@mapNotNull null
            val name = entry["name"] as? String ?: return@mapNotNull null
            Language(LanguageId(id), name)
        }
    }

    fun addLanguage(name: String): Language {
        ensureSeedData()
        val normalized = name.trim()
        if (normalized.isBlank()) error("Language name is empty")
        val existing = getLanguages()
        val baseId = normalized
            .lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_]+"), "")
            .ifBlank { "lang" }
        var candidate = baseId
        var suffix = 2
        while (existing.any { it.id.value == candidate }) {
            candidate = "${baseId}_$suffix"
            suffix += 1
        }
        val newEntry = mapOf("id" to candidate, "name" to normalized)
        val updated = existing.map { mapOf("id" to it.id.value, "name" to it.displayName) } + newEntry
        languagesStore.write(updated)
        return Language(LanguageId(candidate), normalized)
    }

    fun ensureLanguage(languageId: String) {
        val normalized = languageId.lowercase().trim()
        if (normalized.isBlank()) return
        val existing = getLanguages()
        if (existing.any { it.id.value == normalized }) return
        val displayName = when (normalized) {
            "en" -> "English"
            "it" -> "Italian"
            else -> normalized.uppercase()
        }
        val newEntry = mapOf("id" to normalized, "name" to displayName)
        val updated = existing.map { mapOf("id" to it.id.value, "name" to it.displayName) } + newEntry
        languagesStore.write(updated)
    }

    // ── Pack CRUD ────────────────────────────────────────────────────────

    fun getInstalledPacks(): List<LessonPack> {
        val entries = packsStore.read()
        return entries.mapNotNull { entry ->
            val packId = entry["packId"] as? String ?: return@mapNotNull null
            val packVersion = entry["packVersion"] as? String ?: return@mapNotNull null
            val languageId = entry["languageId"] as? String ?: return@mapNotNull null
            val importedAt = (entry["importedAt"] as? Number)?.toLong() ?: 0L
            val displayName = entry["displayName"] as? String
            LessonPack(PackId(packId), packVersion, LanguageId(languageId), importedAt, displayName)
        }
    }

    fun readInstalledPackManifest(packId: String): LessonPackManifest? {
        val manifestFile = File(File(packsDir, packId), "manifest.json")
        if (!manifestFile.exists()) return null
        return runCatching { LessonPackManifest.fromJson(manifestFile.readText()) }.getOrNull()
    }

    fun removePackEntry(packId: String): Boolean {
        val entries = packsStore.read()
        if (entries.isEmpty()) return false
        var removed = false
        val remaining = entries.filterNot { entry ->
            val entryPackId = entry["packId"] as? String
            if (entryPackId == packId) {
                removed = true
                true
            } else {
                false
            }
        }
        if (removed) {
            packsStore.write(remaining)
        }
        return removed
    }

    fun removePacksForLanguage(languageId: String) {
        val entries = packsStore.read()
        val remaining = mutableListOf<Map<String, Any>>()
        entries.forEach { entry ->
            val entryLang = entry["languageId"] as? String ?: return@forEach
            val packId = entry["packId"] as? String
            if (entryLang.equals(languageId, ignoreCase = true)) {
                if (packId != null) {
                    val dir = File(packsDir, packId)
                    if (dir.exists()) dir.deleteRecursively()
                }
            } else {
                remaining.add(entry)
            }
        }
        packsStore.write(remaining)
    }

    fun removePacksForLanguage(packIdToRemove: String, languageId: String) {
        val entries = packsStore.read()
        val remaining = mutableListOf<Map<String, Any>>()
        entries.forEach { entry ->
            val entryPackId = entry["packId"] as? String
            val entryLang = entry["languageId"] as? String
            if (entryPackId == packIdToRemove && entryLang?.equals(languageId, ignoreCase = true) == true) {
                // Remove old version of this pack
                val dir = File(packsDir, packIdToRemove)
                if (dir.exists()) dir.deleteRecursively()
            } else {
                remaining.add(entry)
            }
        }
        packsStore.write(remaining)
    }

    fun deletePackDrills(packId: String) {
        val drillsDir = File(baseDir, "drills/$packId")
        if (drillsDir.exists()) {
            drillsDir.deleteRecursively()
        }
    }

    // ── Internal data classes ────────────────────────────────────────────

    data class DefaultPack(
        val packId: String,
        val assetPath: String
    )
}
