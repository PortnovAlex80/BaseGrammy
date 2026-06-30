package com.alexpo.grammermate.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression test for the "sound pack wiped on restart" bug.
 *
 * Symptom: the separately-installed background-vocab sound pack (a ZIP of
 * pre-rendered `.opus` clips) "disappeared" after every app restart.
 *
 * Root cause: `TrainingViewModel.init` calls `forceReloadDefaultPacks()`
 * unconditionally on every launch (TrainingViewModel.kt:784). That reaches
 * `LanguageManager.deletePackDrills(packId)` (LanguageManager.kt:277-282),
 * which `deleteRecursively()`s the entire `drills/{packId}/` tree — including
 * the `bg_vocab/audio/` subtree where the sound-pack clips live alongside
 * the pack's own drill data. The pack reload that follows only restores the
 * bundle-shipped clips; any user-installed clips (e.g. all `ru_*.opus`
 * translations, extra ranks) are gone for good.
 *
 * Fix contract: `deletePackDrills` must clear the pack's drill data but
 * PRESERVE the `bg_vocab/audio/` subtree, because that subtree holds the
 * out-of-band sound-pack assets that a pack reload must not touch.
 *
 * Pure-JVM test (LanguageManager has no Android dependencies, only java.io.File).
 */
class LanguageManagerDeletePackDrillsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val packId = "ITALIAN_SHORT"

    private fun newLanguageManager(baseDir: File): LanguageManager {
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

    private fun seedDrillsTree(baseDir: File) {
        val audioDir = File(baseDir, "drills/$packId/bg_vocab/audio").apply { mkdirs() }
        // User-installed sound-pack clips (these MUST survive deletePackDrills).
        File(audioDir, "it_r2_f0.opus").writeText("opus-bytes-it-2")
        File(audioDir, "ru_r2_f0.opus").writeText("opus-bytes-ru-2")
        // bg_vocab CSV (pack data; may be cleared + restored by reload).
        File(baseDir, "drills/$packId/bg_vocab/bg_vocab_12000.csv").writeText("csv")
        // Other drill data (pack data; fair to clear).
        File(baseDir, "drills/$packId/verb_drill/data.json").apply { parentFile?.mkdirs() }
            .writeText("drill-data")
    }

    @Test
    fun `deletePackDrills preserves bg_vocab audio clips (sound pack survives reload)`() {
        val baseDir = tmp.newFolder("grammarmate")
        seedDrillsTree(baseDir)
        val audioDir = File(baseDir, "drills/$packId/bg_vocab/audio")
        val itClip = File(audioDir, "it_r2_f0.opus")
        val ruClip = File(audioDir, "ru_r2_f0.opus")
        assertTrue("precondition: it clip exists", itClip.exists())
        assertTrue("precondition: ru clip exists", ruClip.exists())

        newLanguageManager(baseDir).deletePackDrills(packId)

        assertTrue(
            "deletePackDrills must NOT wipe sound-pack audio clips — it_r2_f0.opus survived",
            itClip.exists()
        )
        assertTrue(
            "deletePackDrills must NOT wipe sound-pack audio clips — ru_r2_f0.opus survived",
            ruClip.exists()
        )
    }

    @Test
    fun `deletePackDrills still clears non-audio pack drill data`() {
        // Ensure the fix does not turn deletePackDrills into a no-op: pack drill
        // data OUTSIDE bg_vocab/audio (and the CSV, which a reload restores) must
        // still be cleared, preserving the original intent of the call.
        val baseDir = tmp.newFolder("grammarmate")
        seedDrillsTree(baseDir)
        val otherDrill = File(baseDir, "drills/$packId/verb_drill/data.json")
        assertTrue("precondition: drill data exists", otherDrill.exists())

        newLanguageManager(baseDir).deletePackDrills(packId)

        assertFalse(
            "deletePackDrills must still clear non-audio drill data (verb_drill/data.json)",
            otherDrill.exists()
        )
    }
}
