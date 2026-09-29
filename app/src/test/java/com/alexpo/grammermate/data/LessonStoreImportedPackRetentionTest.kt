package com.alexpo.grammermate.data

import android.content.ContextWrapper
import android.net.Uri
import org.junit.Assert.assertEquals
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

/** Repeated seed checks and lesson reads must never delete user-imported packs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LessonStoreImportedPackRetentionTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun twoImportedItalianPacksRemainAvailableAfterSeedChecksAndLessonReads() {
        val app = RuntimeEnvironment.getApplication()
        val context = object : ContextWrapper(app) {
            override fun getFilesDir(): File = temporaryFolder.root
        }
        val store = LessonStoreImpl(context)
        val firstId = "USER_PACK_FIRST"
        val secondId = "USER_PACK_SECOND"

        importPack(store, context, firstId, "shared", "First pack prompt")
        importPack(store, context, secondId, "shared", "Second pack prompt")

        assertEquals(setOf(firstId, secondId), store.getInstalledPacks().map { it.packId.value }.toSet())
        store.ensureSeedData()
        assertEquals(setOf(firstId, secondId), store.getInstalledPacks().map { it.packId.value }.toSet())
        assertEquals(
            "First pack prompt",
            (store.getLessons(firstId, "it").single().cards.single() as SentenceCard).promptRu
        )
        assertEquals(
            "Second pack prompt",
            (store.getLessons(secondId, "it").single().cards.single() as SentenceCard).promptRu
        )
        assertEquals(setOf(firstId, secondId), store.getInstalledPacks().map { it.packId.value }.toSet())
    }

    private fun importPack(store: LessonStoreImpl, context: ContextWrapper, packId: String, lessonId: String, prompt: String) {
        val archive = temporaryFolder.newFile("$packId.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            fun entry(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("manifest.json", """{"schemaVersion":2,"packId":"$packId","packVersion":"test-1","language":"it","chapters":[{"chapterId":"chapter_1","order":1,"title":"Test","lessons":["$lessonId"]}]}""")
            entry("$lessonId.csv", "$lessonId title\n$prompt;Answer\n")
        }
        store.importPackFromUri(Uri.fromFile(archive), context.contentResolver)
    }
}
