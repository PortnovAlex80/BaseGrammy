package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.StoreFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exercises the production Home pack tiles and the ViewModel they actually call. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class PackSelectionJourneyClickUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val packA = "UI_CLICK_SHARED_A"
    private val packB = "UI_CLICK_SHARED_B"
    private val uniquePack = "UI_CLICK_UNIQUE"
    private val testPackIds get() = listOf(packA, packB, uniquePack)
    private lateinit var vm: TrainingViewModel

    @Before
    fun setUp() {
        // Import before VM startup so its installed-pack snapshot contains all
        // three fixtures, exactly as it would after restarting the app.
        importPack(packA, "A Click shared", "shared", "Shared card from A")
        importPack(packB, "B Click shared", "shared", "Shared card from B")
        importPack(uniquePack, "C Click unique", "unique", "Unique card from C")

        vm = TrainingViewModel(application())
        rule.setContent { GrammarMateTheme { GrammarMateApp(vm) } }
        rule.waitUntil(240_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            !vm.uiState.value.isLoading
        }
        // The persisted language belongs to other journeys in this Robolectric
        // process. Establish the language only; every pack change below is a click.
        rule.runOnUiThread { vm.selectLanguage("it") }
        rule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.navigation.activePackId == null &&
                vm.packTiles.value.map { it.packId }.containsAll(testPackIds)
        }
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) rule.runOnUiThread { vm.selectLanguage("en") }
        val lessonStore = StoreFactory.getInstance(application()).getLessonStore()
        testPackIds.forEach { lessonStore.removeInstalledPackData(it) }
    }

    @Test
    fun clickingPacksWithSharedAndDisjointLessonIdsLoadsOnlySelectedPackCard() {
        clickPack("A Click shared", packA, "shared", "Shared card from A")
        returnToPackTiles()

        clickPack("C Click unique", uniquePack, "unique", "Unique card from C")
        assertFalse(vm.uiState.value.navigation.lessons.any { it.id.value == "shared" })
        returnToPackTiles()

        clickPack("B Click shared", packB, "shared", "Shared card from B")
        assertFalse(vm.uiState.value.navigation.lessons.any { lesson ->
            lesson.cards.any { (it as? SentenceCard)?.promptRu == "Shared card from A" }
        })
        returnToPackTiles()

        // Returning to A catches caches keyed only by lessonId or language.
        clickPack("A Click shared", packA, "shared", "Shared card from A")
    }

    private fun clickPack(label: String, packId: String, lessonId: String, prompt: String) {
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(label).performClick()
        rule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val state = vm.uiState.value
            state.navigation.activePackId?.value == packId &&
                state.navigation.selectedLessonId?.value == lessonId &&
                (state.cardSession.currentCard as? SentenceCard)?.promptRu == prompt
        }
        val state = vm.uiState.value
        assertEquals(packId, state.navigation.activePackId?.value)
        assertEquals(listOf(lessonId), state.navigation.activePackLessonIds)
        assertEquals(listOf(lessonId), state.navigation.lessons.map { it.id.value })
        assertEquals(prompt, (state.navigation.lessons.single().cards.single() as SentenceCard).promptRu)
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    private fun returnToPackTiles() {
        // The chapter roadmap's production Back control clears the active pack.
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.navigation.activePackId == null
        }
    }

    private fun importPack(packId: String, name: String, lessonId: String, prompt: String) {
        val archive = File(application().cacheDir, "$packId.zip")
        val manifest = """{"schemaVersion":2,"packId":"$packId","packVersion":"test-1","language":"it","displayName":"$name","chapters":[{"chapterId":"chapter_1","order":1,"title":"Click test chapter","subtitle":"Click test","lessons":["$lessonId"]}]}"""
        ZipOutputStream(archive.outputStream()).use { zip ->
            fun entry(path: String, contents: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(contents.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("manifest.json", manifest)
            entry("$lessonId.csv", "$lessonId title\n$prompt;Answer\n")
        }
        StoreFactory.getInstance(application()).getLessonStore()
            .importPackFromUri(Uri.fromFile(archive), application().contentResolver)
    }

    private fun application(): Application = RuntimeEnvironment.getApplication() as Application
}
