package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Phase 1 (plan item 1.1): readInstalledPackManifest must serve from an
 * in-memory cache — it is reached from composition via hasChapters →
 * BackHandler(enabled=…), so a disk read + JSON parse per call is a
 * per-recomposition main-thread I/O defect.
 *
 * Fix contract pinned here:
 * - a parsed manifest is cached per packId until explicitly invalidated;
 * - a MISSING manifest is cached too (absent pack must not re-hit the disk);
 * - pack mutation paths (import/remove) drop the cache so updated pack
 *   content becomes visible without process restart.
 */
@RunWith(RobolectricTestRunner::class)
class LanguageManagerManifestCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val packId = "CACHE_PACK"

    private fun manifestJson(version: String): String = """
        {
          "schemaVersion": 1,
          "packId": "$packId",
          "packVersion": "$version",
          "language": "it",
          "lessons": [
            {"lessonId": "lesson_01_A01", "file": "lessons/lesson_01_A01.csv", "order": 1}
          ]
        }
    """.trimIndent()

    private fun newManager(baseDir: File): LanguageManager {
        val yaml = org.yaml.snakeyaml.Yaml()
        return LanguageManager(
            baseDir = baseDir,
            lessonsDir = File(baseDir, "lessons").apply { mkdirs() },
            packsDir = File(baseDir, "packs").apply { mkdirs() },
            languagesFile = File(baseDir, "languages.yaml"),
            languagesStore = YamlListStore(yaml, File(baseDir, "languages.yaml")),
            packsStore = YamlListStore(yaml, File(baseDir, "packs.yaml")),
            seedMarker = File(baseDir, "seed_v1.done"),
            defaultPacks = listOf(LanguageManager.DefaultPack(packId, "ignored.zip"))
        )
    }

    private fun writeManifest(baseDir: File, version: String) {
        val packDir = File(baseDir, "packs/$packId").apply { mkdirs() }
        File(packDir, "manifest.json").writeText(manifestJson(version))
    }

    @Test
    fun `readInstalledPackManifest caches parsed manifest until invalidated`() {
        val baseDir = tmp.newFolder("grammarmate")
        writeManifest(baseDir, "1.0")
        val manager = newManager(baseDir)

        assertEquals("1.0", manager.readInstalledPackManifest(packId)?.packVersion)

        // Disk changes are invisible while cached — composition must not re-read.
        writeManifest(baseDir, "2.0")
        assertEquals("1.0", manager.readInstalledPackManifest(packId)?.packVersion)

        manager.invalidateManifestCache(packId)
        assertEquals("2.0", manager.readInstalledPackManifest(packId)?.packVersion)
    }

    @Test
    fun `absent pack is cached as null until invalidated`() {
        val baseDir = tmp.newFolder("grammarmate")
        val manager = newManager(baseDir)

        assertNull(manager.readInstalledPackManifest(packId))

        writeManifest(baseDir, "1.0")
        assertNull(
            "an absent-pack result must be cached too, not re-checked on disk every call",
            manager.readInstalledPackManifest(packId)
        )

        manager.invalidateManifestCache(packId)
        assertNotNull(manager.readInstalledPackManifest(packId))
    }

    @Test
    fun `invalidateManifestCache without argument clears every pack`() {
        val baseDir = tmp.newFolder("grammarmate")
        writeManifest(baseDir, "1.0")
        val otherDir = File(baseDir, "packs/OTHER_PACK").apply { mkdirs() }
        File(otherDir, "manifest.json").writeText(
            manifestJson("1.0").replace(packId, "OTHER_PACK")
        )
        val manager = newManager(baseDir)

        assertNotNull(manager.readInstalledPackManifest(packId))
        assertNotNull(manager.readInstalledPackManifest("OTHER_PACK"))

        manager.invalidateManifestCache()

        // Cache is empty again — both re-read from disk.
        assertEquals("1.0", manager.readInstalledPackManifest(packId)?.packVersion)
        assertEquals("1.0", manager.readInstalledPackManifest("OTHER_PACK")?.packVersion)
    }

    @Test
    fun `removePacksForLanguage drops the cached manifest`() {
        val baseDir = tmp.newFolder("grammarmate")
        writeManifest(baseDir, "1.0")
        // Register the pack in packs.yaml so removePacksForLanguage finds its entry.
        val yaml = org.yaml.snakeyaml.Yaml()
        YamlListStore(yaml, File(baseDir, "packs.yaml")).write(
            listOf(
                mapOf(
                    "packId" to packId,
                    "packVersion" to "1.0",
                    "languageId" to "it",
                    "importedAt" to 1L
                )
            )
        )
        val manager = newManager(baseDir)

        assertNotNull(manager.readInstalledPackManifest(packId))

        manager.removePacksForLanguage(packId, "it")

        assertNull(
            "pack removal must invalidate the manifest cache — the pack dir is gone",
            manager.readInstalledPackManifest(packId)
        )
    }
}
