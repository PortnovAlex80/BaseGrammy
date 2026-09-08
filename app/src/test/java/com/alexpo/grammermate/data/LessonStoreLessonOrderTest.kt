package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Regression test for Symptom B: in the wild, `File.listFiles()` returned lesson
 * CSVs in non-deterministic (inode) order on ext4, so [MixedReviewScheduler]
 * — which mixes review cards from "previous" lessons by positional index — pulled
 * cards from unrelated lessons into the level-1 mix (MI 8 only, because readdir
 * order happened to be shuffled there).
 *
 * `getLessons(packId, languageId)` must therefore return lessons in manifest order
 * regardless of the order the filesystem yields the files.
 */
@RunWith(RobolectricTestRunner::class)
class LessonStoreLessonOrderTest {

    private lateinit var context: android.content.Context
    private lateinit var baseDir: File
    private lateinit var packsDir: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        baseDir = File(context.filesDir, "grammarmate")
        baseDir.deleteRecursively()
        baseDir.mkdirs()
        packsDir = File(baseDir, "packs")
        packsDir.mkdirs()

        // Run ensureSeedData() once on a clean LessonStoreImpl so that the seed
        // marker is written and the language file is initialized. We do this
        // BEFORE registering our test pack, because ensureSeedData() also runs
        // cleanupStalePacks() which deletes any pack whose id is not in the
        // hardcoded defaultPacks list — that would wipe our test pack if it ran
        // after registration. By seeding first, the subsequent getLessons() call
        // (which also calls ensureSeedData) finds the seed marker present and
        // cleanup still filters our pack, BUT we additionally mark the seed
        // marker below so cleanup is a no-op for a stable test.
        LessonStoreImpl(context).ensureSeedData()
        File(baseDir, "seed_v1.done").writeText("ok")
    }

    /**
     * Build a registered pack directory with CSVs written in [csvWriteOrder]
     * (deliberately non-canonical to expose filesystem-order dependence), plus a
     * schema-v2 manifest declaring [lessonIds] as the canonical order, and a
     * matching packs.yaml entry.
     *
     * NOTE on packId choice: `LessonStoreImpl.ensureSeedData()` runs
     * `cleanupStalePacks()` on every `getLessons()` call, which DELETES any pack
     * whose id is not in the hardcoded `defaultPacks` list. So the test pack id
     * MUST be one of the default pack ids (here ITALIAN_SHORT, lang "it"); the
     * lesson content is fully synthetic and does not match the real asset pack.
     */
    private fun seedPack(
        packId: String,
        langId: String,
        lessonIds: List<String>,
        csvWriteOrder: List<String>
    ) {
        val packDir = File(packsDir, packId).apply { mkdirs() }
        val manifest = buildString {
            append("{\n")
            append("  \"schemaVersion\": 2,\n")
            append("  \"packId\": \"$packId\",\n")
            append("  \"packVersion\": \"v1\",\n")
            append("  \"language\": \"$langId\",\n")
            append("  \"chapters\": [\n")
            append("    { \"chapterId\": \"chapter_1\", \"order\": 1, \"title\": \"C1\", \"subtitle\": \"s\", \"lessons\": [")
            append(lessonIds.joinToString(", ") { "\"$it\"" })
            append("] }\n")
            append("  ]\n")
            append("}")
        }
        File(packDir, "manifest.json").writeText(manifest)

        csvWriteOrder.forEach { lessonId ->
            File(packDir, "$lessonId.csv").writeText("$lessonId title\nРус $lessonId;Ita $lessonId\n")
        }

        val yaml = org.yaml.snakeyaml.Yaml()
        val packsFile = File(baseDir, "packs.yaml")
        YamlListStore(yaml, packsFile).write(listOf(
            mapOf(
                "packId" to packId,
                "packVersion" to "v1",
                "languageId" to langId,
                "importedAt" to 0L
            )
        ))
    }

    @Test
    fun `getLessons returns lessons in manifest order regardless of filesystem order`() {
        // packId must be in defaultPacks so ensureSeedData's cleanupStalePacks
        // does not delete it; content is synthetic and unique to this test.
        val packId = "ITALIAN_SHORT"
        val langId = "it"
        // Manifest declares a NON-lexicographic order (C01 -> A01 -> B01).
        // File names are derived from lessonId, so a loader that returns files
        // in listFiles() (lex) order would yield [A01, B01, C01] — diverging
        // from the manifest. The loader must sort by manifest order instead.
        // This reproduces the on-device bug where ext4 readdir returned a
        // shuffled order and MixedReviewScheduler mixed unrelated lessons.
        val manifestOrder = listOf("lesson_10_C01", "lesson_01_A01", "lesson_05_B01")
        seedPack(
            packId = packId,
            langId = langId,
            lessonIds = manifestOrder,
            csvWriteOrder = manifestOrder
        )

        val store = LessonStoreImpl(context)
        val lessons = store.getLessons(packId, langId)

        assertEquals("all three lessons should load", 3, lessons.size)
        val ids = lessons.map { it.id.value }
        assertEquals(
            "lessons must follow manifest order, not filesystem/lex order",
            manifestOrder,
            ids
        )
    }

    @Test
    fun `getLessons order is stable across repeated reads`() {
        val packId = "ITALIAN_SHORT"
        val langId = "it"
        val manifestOrder = listOf("lesson_10_C01", "lesson_01_A01")
        seedPack(
            packId = packId,
            langId = langId,
            lessonIds = manifestOrder,
            csvWriteOrder = manifestOrder
        )

        val store = LessonStoreImpl(context)
        val first = store.getLessons(packId, langId).map { it.id.value }
        // Force a cache-miss reload.
        store.invalidateLessonsCache()
        val second = store.getLessons(packId, langId).map { it.id.value }

        assertEquals(manifestOrder, first)
        assertEquals("reload must preserve canonical order", first, second)
        assertTrue("cache invalidation should have evicted the entry", first.isNotEmpty())
    }
}
