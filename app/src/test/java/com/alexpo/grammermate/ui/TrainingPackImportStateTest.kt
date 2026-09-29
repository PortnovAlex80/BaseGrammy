package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.StoreFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TrainingPackImportStateTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val originalPackId = "IMPORT_CONTEXT_A"
    private val importedPackId = "IMPORT_CONTEXT_B"
    private lateinit var vm: TrainingViewModel

    @Before
    fun setUp() {
        val archive = packArchive(originalPackId, "first", "Original prompt")
        val lessonStore = StoreFactory.getInstance(application()).getLessonStore()
        lessonStore.importPackFromUri(Uri.fromFile(archive), application().contentResolver)
        assertEquals(true, lessonStore.getInstalledPacks().any { it.packId.value == originalPackId })
        vm = TrainingViewModel(application())
        // Keep the combined public state subscribed without mounting app navigation.
        rule.setContent { Text(vm.uiState.collectAsState().value.navigation.activePackId?.value.orEmpty()) }
        rule.waitUntil(240_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            !vm.uiState.value.isLoading
        }
        rule.runOnUiThread {
            vm.selectLanguage("it")
            vm.selectPack(originalPackId)
        }
        // The command-side reader must see the just-published pack before
        // Compose's combined StateFlow emits its next frame.
        assertEquals("Original prompt", (vm.training.currentCard() as SentenceCard).promptRu)
        assertEquals(true, lessonStore.getInstalledPacks().any { it.packId.value == originalPackId })
        rule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.navigation.activePackId?.value == originalPackId
        }
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) rule.runOnUiThread { vm.selectLanguage("en") }
        val store = StoreFactory.getInstance(application()).getLessonStore()
        store.removeInstalledPackData(originalPackId)
        store.removeInstalledPackData(importedPackId)
    }

    @Test
    fun importingAPackReplacesTheOldPackIdentityLessonsAndSavedScope() {
        val archive = packArchive(importedPackId, "second", "Imported prompt")

        rule.runOnUiThread { vm.importLessonPack(Uri.fromFile(archive)) }
        assertEquals(
            true,
            StoreFactory.getInstance(application()).getLessonStore().getInstalledPacks()
                .any { it.packId.value == importedPackId }
        )
        rule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.navigation.activePackId?.value == importedPackId
        }

        val navigation = vm.uiState.value.navigation
        assertEquals(importedPackId, navigation.activePackId?.value)
        assertEquals(listOf("second"), navigation.activePackLessonIds)
        assertEquals("second", navigation.selectedLessonId?.value)
        assertEquals(listOf("second"), navigation.lessons.map { it.id.value })
        assertEquals(
            "Imported prompt",
            (navigation.lessons.single().cards.single() as SentenceCard).promptRu
        )
        assertEquals(
            importedPackId,
            StoreFactory.getInstance(application()).getProgressStore().load().activePackId?.value
        )
    }

    private fun packArchive(packId: String, lessonId: String, prompt: String): File {
        val archive = File(application().cacheDir, "$packId.zip")
        val manifest = """{"schemaVersion":2,"packId":"$packId","packVersion":"test-1","language":"it","displayName":"$packId","chapters":[{"chapterId":"chapter_1","order":1,"title":"Import chapter","subtitle":"Import test","lessons":["$lessonId"]}]}"""
        ZipOutputStream(archive.outputStream()).use { zip ->
            fun entry(path: String, contents: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(contents.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("manifest.json", manifest)
            entry("$lessonId.csv", "$lessonId title\n$prompt;Answer\n")
        }
        return archive
    }

    private fun application(): Application = rule.activity.application
}
