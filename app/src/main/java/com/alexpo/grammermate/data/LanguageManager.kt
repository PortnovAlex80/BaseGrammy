package com.alexpo.grammermate.data

import android.util.Log
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
                mapOf("id" to "it", "name" to "Italian"),
                mapOf("id" to "de", "name" to "German"),
                mapOf("id" to "zh", "name" to "Chinese"),
                mapOf("id" to "ru", "name" to "Russian"),
                mapOf("id" to "el", "name" to "Greek")
            )
            languagesStore.write(defaults)
        } else {
            // Ensure new languages are present in existing config
            val entries = languagesStore.read().toMutableList()
            val existingIds = entries.map { it["id"] as? String }.toSet()
            val newLangs = listOf(
                mapOf("id" to "de", "name" to "German"),
                mapOf("id" to "zh", "name" to "Chinese"),
                mapOf("id" to "ru", "name" to "Russian"),
                mapOf("id" to "el", "name" to "Greek")
            ).filterNot { it["id"] in existingIds }
            if (newLangs.isNotEmpty()) {
                entries.addAll(newLangs)
                languagesStore.write(entries)
            }
        }
        // Remove packs that are no longer in the default packs list
        cleanupStalePacks()
    }

    /**
     * Remove packs that are no longer in the current defaultPacks list.
     * Only cleans packs for active languages (it, en).
     */
    private fun cleanupStalePacks() {
        val entries = packsStore.read()
        if (entries.isEmpty()) return

        val validPackIds = defaultPacks.map { it.packId }.toSet()
        val activeLanguages = setOf("it", "en", "de", "zh", "ru", "el")

        val remaining = entries.filterNot { entry ->
            val packId = entry["packId"] as? String ?: return@filterNot false
            val languageId = entry["languageId"] as? String ?: return@filterNot false
            // Only remove stale packs for active languages
            if (packId !in validPackIds && languageId in activeLanguages) {
                // Delete pack directory and drills
                val packDir = File(packsDir, packId)
                if (packDir.exists()) packDir.deleteRecursively()
                val drillsDir = File(baseDir, "drills/$packId")
                if (drillsDir.exists()) drillsDir.deleteRecursively()
                true
            } else {
                false
            }
        }

        if (remaining.size != entries.size) {
            packsStore.write(remaining)
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
        Log.i(TAG, "forceReloadDefaultPacks: ENTER, packs=${defaultPacks.map { it.packId }}")
        ensureSeedData()
        var reloadedAny = false
        defaultPacks.forEach { pack ->
            val removed = removeInstalledPackData(pack.packId)
            val reloaded = runCatching { importFromAssets(pack.assetPath) }.isSuccess
            Log.i(TAG, "forceReloadDefaultPacks: pack=${pack.packId} removed=$removed reloaded=$reloaded")
            if (removed || reloaded) reloadedAny = true
        }
        Log.i(TAG, "forceReloadDefaultPacks: EXIT reloadedAny=$reloadedAny")
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

    fun getLanguagesWithPacks(): List<Language> {
        val allLanguages = getLanguages()
        val packLanguageIds = getInstalledPacks().map { it.languageId.value }.toSet()
        return allLanguages.filter { it.id.value in packLanguageIds }
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
            "de" -> "German"
            "zh" -> "Chinese"
            "ru" -> "Russian"
            "el" -> "Greek"
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

    /**
     * Clear the pack's drill data (`drills/{packId}/`) — the lessons/progress/mastery
     * artifacts a pack reload rebuilds — while PRESERVING the `bg_vocab/audio/`
     * subtree.
     *
     * `bg_vocab/audio/` holds the out-of-band background-vocab sound-pack clips
     * (the user-installed `.opus` ZIP). Those clips are NOT part of the pack bundle
     * and are NOT restored by a pack reload, so wiping them here would silently
     * destroy the user's separately-installed sound pack on every restart — which
     * reaches this method via `forceReloadDefaultPacks` on each launch.
     *
     * To preserve the subtree while still clearing everything else (including
     * siblings of `bg_vocab/audio/` and files alongside `bg_vocab/`), the audio
     * dir is moved aside to a temp sibling, the tree is removed, and the audio dir
     * is moved back. The `bg_vocab/` parent is recreated so the restored path is
     * identical to the original (`drills/{packId}/bg_vocab/audio/...`).
     */
    fun deletePackDrills(packId: String) {
        val drillsDir = File(baseDir, "drills/$packId")
        if (!drillsDir.exists()) {
            Log.i(TAG, "deletePackDrills[$packId]: drills dir absent, nothing to do")
            return
        }

        val audioDir = File(drillsDir, "bg_vocab/audio")
        val audioClipCount = audioDir.listFiles { f -> f.isFile && f.extension.equals("opus", ignoreCase = true) }
            ?.size ?: 0
        Log.i(TAG, "deletePackDrills[$packId]: ENTER drills=${drillsDir.exists()} audioDir=${audioDir.exists()} opus=$audioClipCount")

        val shelter = if (audioDir.exists()) {
            val shelterDir = File(drillsDir.parentFile, "${drillsDir.name}.audio_shelter")
            if (shelterDir.exists()) shelterDir.deleteRecursively()
            val moved = audioDir.renameTo(shelterDir)
            Log.i(TAG, "deletePackDrills[$packId]: shelter move audio->${shelterDir.name} moved=$moved shelterExists=${shelterDir.exists()}")
            shelterDir
        } else {
            Log.i(TAG, "deletePackDrills[$packId]: no audio dir to shelter")
            null
        }

        val deleted = drillsDir.deleteRecursively()
        Log.i(TAG, "deletePackDrills[$packId]: drills tree deleted=$deleted drillsStillExists=${drillsDir.exists()}")

        if (shelter != null && shelter.exists()) {
            val restoredBgVocab = File(drillsDir, "bg_vocab").apply { mkdirs() }
            val restoredAudio = File(restoredBgVocab, "audio")
            val movedBack = shelter.renameTo(restoredAudio)
            val restoredCount = restoredAudio.listFiles { f -> f.isFile && f.extension.equals("opus", ignoreCase = true) }?.size ?: 0
            Log.i(TAG, "deletePackDrills[$packId]: restore movedBack=$movedBack audioExists=${restoredAudio.exists()} opus=$restoredCount shelterStillExists=${shelter.exists()}")
        }
    }

    // ── Internal data classes ────────────────────────────────────────────

    data class DefaultPack(
        val packId: String,
        val assetPath: String
    )

    private companion object {
        const val TAG = "LanguageManager"
    }
}
