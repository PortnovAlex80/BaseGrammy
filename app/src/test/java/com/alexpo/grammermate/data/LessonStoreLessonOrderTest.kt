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

        // Initialize language metadata before registering the synthetic pack.
        LessonStoreImpl(context).ensureSeedData()
        File(baseDir, "seed_v1.done").writeText("ok")
    }

    /**
     * Build a registered pack directory with CSVs written in [csvWriteOrder]
     * (deliberately non-canonical to expose filesystem-order dependence), plus a
     * schema-v2 manifest declaring [lessonIds] as the canonical order, and a
     * matching packs.yaml entry.
     *
     * The lesson content is synthetic and does not match the bundled asset pack.
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

    @Test
    fun `language invalidation evicts pack scoped lesson cache`() {
        val packId = "ITALIAN_SHORT"
        val lessonId = "lesson_01_A01"
        seedPack(packId, "it", listOf(lessonId), listOf(lessonId))
        val store = LessonStoreImpl(context)
        val first = store.getLessons(packId, "it").single().title
        File(packsDir, "$packId/$lessonId.csv").writeText("Updated title\nPrompt;Answer\n")

        store.invalidateLessonsCache("it")

        assertEquals("Updated title", store.getLessons(packId, "it").single().title)
        assertTrue(first != store.getLessons(packId, "it").single().title)
    }

    @Test
    fun `chapter and story reads stay scoped to pack and prefer requested language`() {
        val packId = "ITALIAN_SHORT"
        seedPack(packId, "it", listOf("lesson_01_A01"), listOf("lesson_01_A01"))
        val storyDir = File(packsDir, "$packId/stories").apply { mkdirs() }
        File(storyDir, "chapter_1.md").writeText("English story")
        File(storyDir, "chapter_1_original.md").writeText("Russian story")
        val store = LessonStoreImpl(context)

        assertEquals("chapter_1", store.getChapters(packId).single().chapterId)
        assertTrue(store.hasChapters(packId))
        assertEquals("chapter_1_original.md", store.detectStoryLanguage(packId, "chapter_1", "ru"))
        assertEquals("chapter_1.md", store.detectStoryLanguage(packId, "chapter_1", "en"))
        assertEquals("Russian story", store.getChapterStory(packId, "chapter_1_original.md"))
        assertEquals(null, store.detectStoryLanguage("missing-pack", "chapter_1", "en"))
    }

}
