package com.alexpo.grammermate.data

import android.content.ContextWrapper
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class GrammarChipImportRefreshTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun declaredNestedEnglishChipPathLoadsAndImportRefreshesTheWarmedChip() = runBlocking {
        val app = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = temporary.root
        }
        GrammarChipStore.clearCache()
        try {
            val asset = "grammarmate/packs/EN_ADV_BASE_GRAMMAR.zip"
            val store = LessonStoreImpl(app)
            val pack = store.importPackFromAssets(asset)
            val root = File(app.filesDir, "grammarmate/packs")
            val manifest = LessonPackManifest.fromJson(File(root, "${pack.packId.value}/manifest.json").readText())
            val lesson = manifest.lessons.first()
            assertTrue(lesson.grammarChip!!.contains('/'))
            val chipJson = JSONObject(File(root, "${pack.packId.value}/${lesson.grammarChip}").readText())
            GrammarChipStore.initialize(app, root)
            assertEquals(chipJson.getString("title"), GrammarChipStore.getChipForLesson(lesson.lessonId, pack.packId.value)!!.title)
            val update = temporary.newFile("updated.zip")
            ZipOutputStream(update.outputStream()).use { output ->
                ZipInputStream(app.assets.open(asset)).use { input ->
                    var entry = input.nextEntry
                    while (entry != null) {
                        output.putNextEntry(ZipEntry(entry.name))
                        val bytes = input.readBytes()
                        val replacement = when (entry.name) {
                            "manifest.json" -> JSONObject(bytes.toString(Charsets.UTF_8)).put("packVersion", "review-2").toString().toByteArray()
                            lesson.grammarChip -> JSONObject(bytes.toString(Charsets.UTF_8)).put("title", "Updated declared chip").toString().toByteArray()
                            else -> bytes
                        }
                        output.write(replacement)
                        output.closeEntry(); input.closeEntry()
                        entry = input.nextEntry
                    }
                }
            }
            store.importPackFromUri(Uri.fromFile(update), app.contentResolver)
            assertEquals("Updated declared chip", GrammarChipStore.getChipForLesson(lesson.lessonId, pack.packId.value)!!.title)
        } finally { GrammarChipStore.clearCache() }
    }
}
