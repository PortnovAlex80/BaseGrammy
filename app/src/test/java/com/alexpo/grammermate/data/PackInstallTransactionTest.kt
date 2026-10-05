package com.alexpo.grammermate.data

import android.content.ContextWrapper
import android.net.Uri
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class PackInstallTransactionTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun context(filesDir: File = temporary.root) = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getFilesDir(): File = filesDir
    }
    private fun archive(version: String): File = temporary.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            fun write(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            write("manifest.json", """{"schemaVersion":1,"packId":"pack","packVersion":"$version","language":"en","lessons":[{"lessonId":"lesson","order":1,"file":"nested/lesson.csv"}],"backgroundVocab":{"file":"bg.csv","audioDir":"audio"}}""")
            write("nested/lesson.csv", "Title $version\nPrompt;Answer $version\n")
            write("bg.csv", BgVocabCsvParser.EXPECTED_HEADER.joinToString(",") + "\n1,casa,house,casa mia,my house,La casa,The house,,,,,,,,\n")
            write("audio/word.opus", "clip-$version")
        }
    }
    private fun liveFiles(base: File): Map<String, List<Byte>> = base.walkTopDown()
        .filter { it.isFile && !it.relativeTo(base).invariantSeparatorsPath.split('/').any { p -> p.startsWith("tmp_") || p.startsWith(".pack-") } }
        .associate { it.relativeTo(base).invariantSeparatorsPath to it.readBytes().toList() }

    @Test fun lexicalFilesDirectoryImportsAndRollsBackAgainstCanonicalTargets() {
        val alias = temporary.newFolder("alias")
        val app = context(File(alias, ".."))
        val store = LessonStoreImpl(app)
        store.importPackFromUri(Uri.fromFile(archive("1")), app.contentResolver)
        val base = File(temporary.root, "grammarmate").canonicalFile
        assertTrue(File(base, "packs.yaml").isFile)
        assertEquals("Title 1", store.getLessons("pack", "en").single().title)
        val before = liveFiles(base)
        PackInstallTransaction.afterWriteForTest = { error("injected after canonical write") }
        try {
            assertTrue(runCatching { store.importPackFromUri(Uri.fromFile(archive("2")), app.contentResolver) }.isFailure)
        } finally { PackInstallTransaction.afterWriteForTest = null }
        assertEquals(before, liveFiles(base))
        assertEquals("1", LessonStoreImpl(app).getInstalledPacks().single().packVersion)
        assertEquals("Title 1", LessonStoreImpl(app).getLessons("pack", "en").single().title)
    }

    @Test fun distinctCanonicalRootIsUsedBeforeConstructingJournalAndStillRejectsOutsideTargets() {
        val root = temporary.newFolder("canonical-root").canonicalFile
        File(root, "packs").mkdirs()
        // Model Android's filesDir/canonical root alias without requiring host symlink privileges.
        val aliasPath = File(temporary.root, "visible-root-alias")
        val alias = object : File(aliasPath.path) {
            override fun getCanonicalFile(): File = root
            override fun getCanonicalPath(): String = root.path
        }
        val staged = temporary.newFolder("prepared-pack").apply { File(this, "manifest.json").writeText("prepared") }
        val source = temporary.newFile().apply { writeText("lesson data") }
        val target = File(root, "lessons/en/lesson.csv")
        PackInstallTransaction.install(alias, staged, "pack", mapOf(target to source), "registry data")
        assertEquals("lesson data", target.readText())
        assertEquals("registry data", File(root, "packs.yaml").readText())
        assertEquals("prepared", File(root, "packs/pack/manifest.json").readText())
        assertFalse("All publication paths must use the canonical root", aliasPath.exists())

        val before = liveFiles(root)
        val outside = temporary.newFile().apply { writeText("keep outside") }
        val rejectedStage = temporary.newFolder("rejected-pack")
        assertTrue(runCatching {
            PackInstallTransaction.install(alias, rejectedStage, "pack", mapOf(outside to source), "replacement")
        }.isFailure)
        assertEquals("keep outside", outside.readText())
        assertEquals(before, liveFiles(root))
        assertTrue(rejectedStage.isDirectory)
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith(".pack-install-") })
    }

    @Test fun injectedWriteFailureRestoresEveryDerivedFileRegistryAndOriginalLesson() {
        val app = context()
        val store = LessonStoreImpl(app)
        store.importPackFromUri(Uri.fromFile(archive("1")), app.contentResolver)
        val before = liveFiles(File(app.filesDir, "grammarmate"))
        assertEquals("Title 1", store.getLessons("pack", "en").single().title)
        PackInstallTransaction.afterWriteForTest = { index -> if (index == 3) error("injected") }
        try {
            val result = runCatching { store.importPackFromUri(Uri.fromFile(archive("2")), app.contentResolver) }
            assertTrue(result.isFailure)
        } finally { PackInstallTransaction.afterWriteForTest = null }
        assertEquals(before, liveFiles(File(app.filesDir, "grammarmate")))
        assertEquals("1", store.getInstalledPacks().single().packVersion)
        assertEquals("Title 1", store.getLessons("pack", "en").single().title)
    }

    @Test fun freshAudioDirectoryIsMovedAndFailedNewInstallLeavesNoPublishedContent() {
        val app = context()
        val store = LessonStoreImpl(app)
        store.ensureSeedData()
        val base = File(app.filesDir, "grammarmate")
        val before = liveFiles(base)
        // Last hook runs after the staged audio directory has been moved.
        PackInstallTransaction.afterWriteForTest = { if (File(base, "drills/pack/bg_vocab/audio/word.opus").exists()) error("after audio") }
        try { assertTrue(runCatching { store.importPackFromUri(Uri.fromFile(archive("1")), app.contentResolver) }.isFailure) }
        finally { PackInstallTransaction.afterWriteForTest = null }
        assertEquals(before, liveFiles(base))
        store.importPackFromUri(Uri.fromFile(archive("1")), app.contentResolver)
        assertEquals("clip-1", File(base, "drills/pack/bg_vocab/audio/word.opus").readText())
    }

    @Test fun startupRecoversInterruptedPublicationBeforeCachedReadersOpen() {
        val app = context()
        val store = LessonStoreImpl(app)
        store.importPackFromUri(Uri.fromFile(archive("1")), app.contentResolver)
        val base = File(app.filesDir, "grammarmate")
        val before = liveFiles(base)
        val journal = File(base, ".pack-install-interrupted").apply { mkdirs() }
        val registry = File(base, "packs.yaml")
        AtomicFileWriter.copyAtomic(registry, File(journal, "before/0"))
        AtomicFileWriter.writeText(File(journal, "plan.yaml"), Yaml().dump(mapOf(
            "pack" to "packs/pack", "packExisted" to true,
            "files" to listOf(mapOf("path" to "packs.yaml", "existed" to true, "backup" to "before/0")),
            "directories" to emptyList<String>()
        )))
        assertTrue(File(base, "packs/pack").renameTo(File(journal, "previous-pack")))
        File(base, "packs/pack").mkdirs()
        File(base, "packs/pack/manifest.json").writeText("incomplete")
        registry.writeText("schemaVersion: 1\nitems: []\n")
        val reopened = LessonStoreImpl(app)
        assertEquals("1", reopened.getInstalledPacks().single().packVersion)
        assertEquals(before, liveFiles(base))
        assertFalse(journal.exists())
    }

    @Test fun personalCsvReplacementHasStableIdentityAndDeleteRemovesItsPack() {
        val app = context()
        val store = LessonStoreImpl(app)
        val csv = temporary.newFile().apply { writeText("Personal\nPrompt;Answer\n") }
        val first = store.importFromUri("en", Uri.fromFile(csv), app.contentResolver)
        val firstPack = store.getPackIdForLesson(first.id.value)!!
        assertTrue(firstPack.startsWith("USER_"))
        assertEquals(first.cards, store.getLessons(firstPack, "en").single().cards)
        assertEquals(firstPack, first.cards.single().sourcePackId)
        assertEquals(first.id.value, first.cards.single().sourceLessonId)
        csv.writeText("Personal\nNew prompt;New answer\n")
        val second = store.importFromUri("en", Uri.fromFile(csv), app.contentResolver)
        assertEquals(first.id, second.id)
        assertEquals(firstPack, store.getPackIdForLesson(second.id.value))
        assertEquals(1, store.getInstalledPacks().size)
        assertEquals("New prompt", store.getLessons(firstPack, "en").single().cards.single().promptRu)
        store.deleteLesson("en", first.id.value)
        assertTrue(store.getInstalledPacks().isEmpty())
        assertTrue(store.getLessons(firstPack, "en").isEmpty())
        val draft = store.createEmptyLesson("en", "Draft")
        val draftPack = store.getPackIdForLesson(draft.id.value)!!
        assertTrue(store.getLessons(draftPack, "en").single().cards.isEmpty())
    }

    @Test fun oldStandaloneIndexMigratesOnceWithoutChangingLessonOrCardIdentity() {
        val app = context()
        val id = "1a9ee5b2-8052-4e5c-9eca-aa8cb6a944a2"
        val base = File(app.filesDir, "grammarmate")
        val lessons = File(base, "lessons/en").apply { mkdirs() }
        File(lessons, "lesson_$id.csv").writeText("Personal\nPrompt;Answer\n")
        File(base, "lessons/en_index.yaml").writeText(Yaml().dump(mapOf("schemaVersion" to 1,
            "items" to listOf(mapOf("id" to id, "title" to "Personal", "file" to "lesson_$id.csv")))))
        val store = LessonStoreImpl(app)
        store.ensureSeedData()
        val pack = store.getPackIdForLesson(id)!!
        assertEquals("USER_$id", pack)
        assertEquals("${id}_0", store.getLessons(pack, "en").single().cards.single().id)
        LessonStoreImpl(app).ensureSeedData()
        assertEquals(1, LessonStoreImpl(app).getInstalledPacks().size)
    }

    @Test fun malformedRequiredVocabAndBackgroundFilesRetainInstalledUpdateAndRemovedDrillsDisappear() {
        val app = context()
        val store = LessonStoreImpl(app)
        fun writeZip(version: String, kind: String, content: String): File = temporary.newFile().also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                val section = if (kind == "vocabDrill") "\"vocabDrill\":{\"files\":[\"en_drill_nouns.csv\"]}"
                    else "\"backgroundVocab\":{\"file\":\"bg.csv\"}"
                val name = if (kind == "vocabDrill") "en_drill_nouns.csv" else "bg.csv"
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("{\"schemaVersion\":1,\"packId\":\"pack\",\"packVersion\":\"$version\",\"language\":\"en\",$section}".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
        }
        store.importPackFromUri(Uri.fromFile(writeZip("1", "vocabDrill", "rank,noun,collocations\n1,casa,casa mia\n")), app.contentResolver)
        val before = liveFiles(File(app.filesDir, "grammarmate"))
        for (kind in listOf("vocabDrill", "backgroundVocab")) {
            assertTrue(runCatching { store.importPackFromUri(Uri.fromFile(writeZip("2", kind, "garbage")), app.contentResolver) }.isFailure)
            assertEquals(before, liveFiles(File(app.filesDir, "grammarmate")))
        }
        store.importPackFromUri(Uri.fromFile(archive("3")), app.contentResolver)
        assertTrue(store.getVocabDrillFiles("pack", "en").isEmpty())
        assertEquals("3", store.getInstalledPacks().single().packVersion)
    }

    @Test fun allRegisteredBundledPacksPassTheSameMandatoryContentValidation() {
        val app = context()
        val store = LessonStoreImpl(app)
        val assets = listOf("EN_WORD_ORDER_A1.zip", "EN_WORD_ORDER_A1_DRILLS.zip", "ITALIAN_EXPRESS_SHORT.zip",
            "ITALIAN_FULL_COURSE.zip", "ITALIAN_IMPERIAL_COURSE.zip", "EN_ADV_BASE_GRAMMAR.zip", "EN_ENGLISH_FULL_COURSE.zip")
        assets.forEach { asset ->
            val pack = store.importPackFromAssets("grammarmate/packs/$asset")
            assertTrue("Registered pack must be readable: $asset", store.getLessonCount(pack.packId.value, pack.languageId.value) > 0)
        }
        assertEquals(assets.size, store.getInstalledPacks().size)
    }
}
