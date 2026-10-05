package com.alexpo.grammermate.data

import android.content.ContextWrapper
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PackImportSafetyRegressionTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun context() = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getFilesDir(): File = temporaryFolder.root
    }

    private fun archive(packId: String, version: String, includeLesson: Boolean): File {
        val file = temporaryFolder.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("manifest.json", """{"schemaVersion":2,"packId":"$packId","packVersion":"$version","language":"it","chapters":[{"chapterId":"chapter_1","order":1,"title":"Review","lessons":["review_lesson"]}]}""")
            if (includeLesson) entry("review_lesson.csv", "Review lesson\nOriginal prompt;Answer\n")
        }
        return file
    }

    @Test
    fun manifestPackIdCannotDeleteDirectoryOutsidePacksRoot() {
        val context = context()
        val base = File(context.filesDir, "grammarmate").apply { mkdirs() }
        val victim = File(base, "review-victim").apply { mkdirs() }
        // Prove the entire affected directory belongs to this isolated test fixture.
        require(victim.canonicalPath.startsWith(temporaryFolder.root.canonicalPath + File.separator))
        val sentinel = File(victim, "user-progress.txt").apply { writeText("keep") }
        val store = LessonStoreImpl(context)
        val zip = archive("../review-victim", "1", true)
        val result = runCatching { store.importPackFromUri(Uri.fromFile(zip), context.contentResolver) }
        assertTrue("Manifest packId must not delete outside packs; accepted=${result.isSuccess}", sentinel.exists())
    }

    @Test
    fun updateMissingRequiredLessonRetainsInstalledPack() {
        val context = context()
        val store = LessonStoreImpl(context)
        val pack = "REVIEW_PACK"
        store.importPackFromUri(Uri.fromFile(archive(pack, "1", true)), context.contentResolver)
        assertEquals(1, store.getLessons(pack, "it").size)
        val invalid = archive(pack, "2", false)
        runCatching { store.importPackFromUri(Uri.fromFile(invalid), context.contentResolver) }
        assertTrue("Rejected/malformed update must keep the existing lesson file", File(context.filesDir, "grammarmate/packs/$pack/review_lesson.csv").exists())
        assertEquals("1", store.getInstalledPacks().single().packVersion)
    }
}
