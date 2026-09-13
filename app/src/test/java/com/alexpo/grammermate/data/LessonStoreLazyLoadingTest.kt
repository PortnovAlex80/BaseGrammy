package com.alexpo.grammermate.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

class LessonStoreLazyLoadingTest {

    // ========================================
    // LessonMetadata data class contract tests
    // These PASS immediately (pure data class, no Android needed)
    // ========================================

    @Test
    fun lessonMetadata_holdsIdLanguageIdTitle() {
        val meta = LessonMetadata(
            id = LessonId("lesson_01_A01"),
            languageId = LanguageId("it"),
            title = "A01 - Presente Indicativo"
        )
        assertEquals("lesson_01_A01", meta.id.value)
        assertEquals("it", meta.languageId.value)
        assertEquals("A01 - Presente Indicativo", meta.title)
    }

    @Test
    fun lessonMetadata_dataClassEquality() {
        val m1 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title")
        val m2 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title")
        assertEquals(m1, m2)
    }

    @Test
    fun lessonMetadata_differentId_notEqual() {
        val m1 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title")
        val m2 = LessonMetadata(LessonId("l2"), LanguageId("it"), "Title")
        assertNotEquals(m1, m2)
    }

    @Test
    fun lessonMetadata_differentTitle_notEqual() {
        val m1 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title A")
        val m2 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title B")
        assertNotEquals(m1, m2)
    }

    @Test
    fun lessonMetadata_differentLanguageId_notEqual() {
        val m1 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title")
        val m2 = LessonMetadata(LessonId("l1"), LanguageId("en"), "Title")
        assertNotEquals(m1, m2)
    }

    @Test
    fun lessonMetadata_copyWorks() {
        val original = LessonMetadata(LessonId("l1"), LanguageId("it"), "Original")
        val modified = original.copy(title = "Modified")
        assertEquals("Original", original.title)
        assertEquals("Modified", modified.title)
        assertEquals(original.id, modified.id)
        assertEquals(original.languageId, modified.languageId)
    }

    @Test
    fun lessonMetadata_destructuring() {
        val meta = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title")
        val (id, languageId, title) = meta
        assertEquals("l1", id.value)
        assertEquals("it", languageId.value)
        assertEquals("Title", title)
    }

    // ========================================
    // LessonStore lazy API stub contract tests
    // These test default implementations on the interface.
    // Stubs return empty/null/0 — positive tests will be added
    // after implementation with proper Android test setup.
    // ========================================

    @Test
    fun stub_getLesson_unknown_returnsNull() {
        val store = object : LessonStore by stubLessonStore() {}
        val result = store.getLesson("UNKNOWN_PACK", "xx", "lesson_XX_nonexistent")
        assertNull("Stub getLesson should return null", result)
    }

    @Test
    fun stub_getLessonMetadata_returnsEmptyList() {
        val store = object : LessonStore by stubLessonStore() {}
        val result = store.getLessonMetadata("UNKNOWN_PACK", "xx")
        assertTrue("Stub getLessonMetadata should return empty list", result.isEmpty())
    }

    @Test
    fun stub_getLessonCount_returnsZero() {
        val store = object : LessonStore by stubLessonStore() {}
        val result = store.getLessonCount("UNKNOWN_PACK", "xx")
        assertEquals("Stub getLessonCount should return 0", 0, result)
    }

    @Test
    fun stub_getLessonIdAtIndex_returnsNull() {
        val store = object : LessonStore by stubLessonStore() {}
        val result = store.getLessonIdAtIndex("UNKNOWN_PACK", "xx", 0)
        assertNull("Stub getLessonIdAtIndex should return null", result)
    }

    @Test
    fun stub_getCardsForLesson_returnsEmptyList() {
        val store = object : LessonStore by stubLessonStore() {}
        val result = store.getCardsForLesson("UNKNOWN_PACK", "xx", "lesson_XX_nonexistent")
        assertTrue("Stub getCardsForLesson should return empty list", result.isEmpty())
    }

    /**
     * Provides a minimal stub implementation of LessonStore interface
     * so we can test the default method stubs without Android context.
     * Only the methods needed for lazy loading tests are implemented here.
     */
    private fun stubLessonStore(): LessonStore = object : LessonStore {
        override fun ensureSeedData() {}
        override fun seedDefaultPacksIfNeeded(): Boolean = false
        override fun updateDefaultPacksIfNeeded(): Boolean = false
        override fun forceReloadDefaultPacks(): Boolean = false
        override fun getLanguages(): List<Language> = emptyList()
        override fun getLanguagesWithPacks(): List<Language> = emptyList()
        override fun addLanguage(name: String): Language = throw UnsupportedOperationException()
        override fun getInstalledPacks(): List<LessonPack> = emptyList()
        override fun getPackIdForLesson(lessonId: String): String? = null
        override fun getLessonIdsForPack(packId: String): List<String> = emptyList()
        override fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String> = emptyList()
        override fun importPackFromUri(uri: android.net.Uri, resolver: android.content.ContentResolver): LessonPack =
            throw UnsupportedOperationException()
        override fun importPackFromAssets(assetPath: String): LessonPack =
            throw UnsupportedOperationException()
        override fun removeInstalledPackData(packId: String): Boolean = false
        override fun importFromUri(languageId: String, uri: android.net.Uri, resolver: android.content.ContentResolver): Lesson =
            throw UnsupportedOperationException()
        override fun importFromUriWithErrors(languageId: String, uri: android.net.Uri, resolver: android.content.ContentResolver): Pair<Lesson, List<ParseError>> =
            throw UnsupportedOperationException()
        override fun getLessons(languageId: String): List<Lesson> = emptyList()
        override fun getLessons(packId: String, languageId: String): List<Lesson> = emptyList()
        override fun deleteAllLessons(languageId: String) {}
        override fun deleteLesson(languageId: String, lessonId: String) {}
        override fun createEmptyLesson(languageId: String, title: String): Lesson =
            throw UnsupportedOperationException()
        override fun getStoryQuizzes(lessonId: String, phase: StoryPhase, languageId: String): List<StoryQuiz> = emptyList()
        override fun getVocabEntries(lessonId: String, languageId: String): List<VocabEntry> = emptyList()
        override fun getVerbDrillFiles(packId: String, languageId: String): List<java.io.File> = emptyList()
        override fun getVerbDrillFilesForPack(packId: String): List<java.io.File> = emptyList()
        override fun getVocabDrillFiles(packId: String, languageId: String): List<java.io.File> = emptyList()
        override fun getVocabDrillFilesForPack(packId: String): List<java.io.File> = emptyList()
        override fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<VocabWord> = emptyList()
        override fun hasVerbDrill(packId: String, languageId: String): Boolean = false
        override fun hasVocabDrill(packId: String, languageId: String): Boolean = false
        override fun getChapters(packId: String): List<Chapter> = emptyList()
        override fun getChapterStory(packId: String, storyFile: String): String? = null
        override fun hasChapters(packId: String): Boolean = false
        override fun loadExternalLessons(languageId: String, externalDirPath: String): Int = 0
        override fun detectStoryLanguage(packId: String, chapterId: String, uiLanguage: String): String? = null
        override fun getVerbDrillFiles(languageId: String): List<java.io.File> = emptyList()
        override fun hasVerbDrillLessons(languageId: String): Boolean = false
    }
}

/**
 * Disk-backed tests for the lazy lesson-loading API against the real
 * [LessonStoreImpl] (TASK-092). Setup pattern copied from
 * [LessonStoreLessonOrderTest]: Robolectric filesDir, a seeded store, and a
 * synthetic pack registered under a default pack id (required —
 * cleanupStalePacks deletes unknown ids).
 *
 * packId must be one of the hardcoded defaultPacks; lesson content is fully
 * synthetic and does not match the real asset pack.
 */
@RunWith(RobolectricTestRunner::class)
class LessonStoreLazyLoadingDiskTest {

    private val packId = "ITALIAN_SHORT"
    private val langId = "it"

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

        // Seed first so the marker is present; otherwise ensureSeedData() (run
        // inside every getLessons/getLesson call) would clean up our pack.
        LessonStoreImpl(context).ensureSeedData()
        File(baseDir, "seed_v1.done").writeText("ok")
    }

    /** v2 pack: chapters declare the course order; filenames are "<lessonId>.csv". */
    private fun seedV2Pack(lessonIds: List<String>) {
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
        lessonIds.forEach { writeLessonCsv(packDir, "$it.csv", it) }
        registerPack()
    }

    /**
     * v1 pack with an EXPLICIT file mapping: lessons[] declares lessonId -> file,
     * and at least one file name differs from "<lessonId>.csv". This is the
     * TASK-092 bug fixture: before the fix the lazy path guesses the filename
     * and misses the lesson.
     */
    private fun seedV1MappedPack(entries: List<Pair<String, String>>) {
        val packDir = File(packsDir, packId).apply { mkdirs() }
        val lessonsJson = entries.mapIndexed { i, (lessonId, file) ->
            "    { \"lessonId\": \"$lessonId\", \"file\": \"$file\", \"order\": ${i + 1} }"
        }.joinToString(",\n")
        val manifest = buildString {
            append("{\n")
            append("  \"schemaVersion\": 1,\n")
            append("  \"packId\": \"$packId\",\n")
            append("  \"packVersion\": \"v1\",\n")
            append("  \"language\": \"$langId\",\n")
            append("  \"lessons\": [\n")
            append(lessonsJson)
            append("\n  ]\n")
            append("}")
        }
        File(packDir, "manifest.json").writeText(manifest)
        entries.forEach { (lessonId, file) -> writeLessonCsv(packDir, file, lessonId) }
        registerPack()
    }

    private fun writeLessonCsv(packDir: File, fileName: String, lessonId: String) {
        File(packDir, fileName).writeText(
            "$lessonId title\nРус ${lessonId}_1;Ita ${lessonId}_1\nРус ${lessonId}_2;Ita ${lessonId}_2\nРус ${lessonId}_3;Ita ${lessonId}_3\n"
        )
    }

    private fun registerPack() {
        val yaml = org.yaml.snakeyaml.Yaml()
        YamlListStore(yaml, File(baseDir, "packs.yaml")).write(listOf(
            mapOf(
                "packId" to packId,
                "packVersion" to "v1",
                "languageId" to langId,
                "importedAt" to 0L
            )
        ))
    }

    // ── getLessonCount / getLessonIdAtIndex ──────────────────────────────

    @Test
    fun getLessonCount_returnsManifestLessonCount() {
        seedV2Pack(listOf("lesson_10_C01", "lesson_01_A01", "lesson_05_B01"))
        val store = LessonStoreImpl(context)
        assertEquals(3, store.getLessonCount(packId, langId))
    }

    @Test
    fun getLessonCount_returnsZero_forUnknownPack() {
        LessonStoreImpl(context).also {
            assertEquals(0, it.getLessonCount("NO_SUCH_PACK", langId))
        }
    }

    @Test
    fun getLessonIdAtIndex_returnsFirstLessonInCourseOrder() {
        val order = listOf("lesson_10_C01", "lesson_01_A01", "lesson_05_B01")
        seedV2Pack(order)
        val store = LessonStoreImpl(context)
        assertEquals("lesson_10_C01", store.getLessonIdAtIndex(packId, langId, 0))
    }

    @Test
    fun getLessonIdAtIndex_returnsNull_pastEnd() {
        seedV2Pack(listOf("lesson_01_A01", "lesson_05_B01"))
        assertNull(LessonStoreImpl(context).getLessonIdAtIndex(packId, langId, 2))
    }

    @Test
    fun getLessonIdAtIndex_returnsNull_forNegativeIndex() {
        seedV2Pack(listOf("lesson_01_A01"))
        assertNull(LessonStoreImpl(context).getLessonIdAtIndex(packId, langId, -1))
    }

    // ── getLessonMetadata / getLesson / getCardsForLesson ────────────────

    @Test
    fun getLessonMetadata_oneEntryPerLesson_inCourseOrder() {
        val order = listOf("lesson_10_C01", "lesson_01_A01", "lesson_05_B01")
        seedV2Pack(order)
        val metadata = LessonStoreImpl(context).getLessonMetadata(packId, langId)
        assertEquals(order, metadata.map { it.id.value })
        assertEquals("lesson_10_C01 title", metadata.first { it.id.value == "lesson_10_C01" }.title)
    }

    @Test
    fun getLesson_returnsLessonWithNonEmptyCards() {
        seedV2Pack(listOf("lesson_01_A01", "lesson_05_B01"))
        val lesson = LessonStoreImpl(context).getLesson(packId, langId, "lesson_01_A01")
        assertNotNull(lesson)
        assertEquals(3, lesson!!.cards.size)
    }

    @Test
    fun getLesson_returnsNull_forUnknownLessonId() {
        seedV2Pack(listOf("lesson_01_A01"))
        assertNull(LessonStoreImpl(context).getLesson(packId, langId, "lesson_XX_nonexistent"))
    }

    @Test
    fun getCardsForLesson_sameAsGetLessonCards() {
        seedV2Pack(listOf("lesson_01_A01", "lesson_05_B01"))
        val store = LessonStoreImpl(context)
        assertEquals(
            store.getLesson(packId, langId, "lesson_05_B01")!!.cards,
            store.getCardsForLesson(packId, langId, "lesson_05_B01")
        )
    }

    // ── TASK-092 regression: explicit manifest file mapping ──────────────

    @Test
    fun mappedFileName_lazyPathFindsRenamedLesson_andMetadataListsIt() {
        seedV1MappedPack(
            listOf(
                "lesson_01_A01" to "renamed_01.csv",
                "lesson_02_A02" to "lesson_02_A02.csv"
            )
        )
        val store = LessonStoreImpl(context)

        val lesson = store.getLesson(packId, langId, "lesson_01_A01")
        assertNotNull("lazy path must resolve the manifest-declared file, not <lessonId>.csv", lesson)
        assertEquals(3, lesson!!.cards.size)

        val metadataIds = store.getLessonMetadata(packId, langId).map { it.id.value }
        assertTrue("metadata must list the renamed lesson", "lesson_01_A01" in metadataIds)
        assertEquals(listOf("lesson_01_A01", "lesson_02_A02"), metadataIds)
    }

    // ── Parity: lazy and full paths must agree on card IDs ───────────────

    @Test
    fun cardIdParity_lazyVsFull_mappedV1Pack() {
        seedV1MappedPack(
            listOf(
                "lesson_01_A01" to "renamed_01.csv",
                "lesson_02_A02" to "lesson_02_A02.csv"
            )
        )
        val store = LessonStoreImpl(context)
        val lazyIds = store.getLesson(packId, langId, "lesson_01_A01")!!.cards.map { it.id }
        val fullIds = store.getLessons(packId, langId)
            .first { it.id.value == "lesson_01_A01" }
            .cards.map { it.id }
        assertEquals(lazyIds, fullIds)
    }

    @Test
    fun cardIdParity_lazyVsFull_defaultFileNamePack() {
        seedV2Pack(listOf("lesson_10_C01", "lesson_01_A01", "lesson_05_B01"))
        val store = LessonStoreImpl(context)
        val lazyIds = store.getLesson(packId, langId, "lesson_10_C01")!!.cards.map { it.id }
        val fullIds = store.getLessons(packId, langId)
            .first { it.id.value == "lesson_10_C01" }
            .cards.map { it.id }
        assertEquals(lazyIds, fullIds)
    }
}
