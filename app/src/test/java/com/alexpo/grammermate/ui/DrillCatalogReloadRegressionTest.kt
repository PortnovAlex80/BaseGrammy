package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.ItalianDrillVocabParser
import com.alexpo.grammermate.data.StoreFactory
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.VerbDrillCard
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class DrillCatalogReloadRegressionTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var app: Application
    private lateinit var originalProgress: TrainingProgress
    private val packId = "CATALOG_UPDATE_REVIEW"

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        val progress = StoreFactory.getInstance(app).getProgressStore()
        originalProgress = progress.load()
        progress.save(originalProgress.copy(languageId = LanguageId("it")))
    }

    @After fun cleanup() {
        unmockkConstructor(VerbDrillCardCatalogLoader::class)
        unmockkObject(ItalianDrillVocabParser)
        val stores = StoreFactory.getInstance(app)
        stores.getLessonStore().removeInstalledPackData(packId)
        stores.getProgressStore().save(originalProgress)
    }

    @Test fun samePackVerbUpdateIsUsedByTheStartButtonAndNoPackClearsCatalog() {
        install("old", 3)
        val vm = VerbDrillViewModel(app)
        var started = emptyList<VerbDrillCard>()
        rule.setContent { MaterialTheme { VerbDrillScreen(vm, onBack = {}, onStartSession = { started = it }) } }
        rule.runOnUiThread { vm.reloadForPack(packId) }
        await { !vm.uiState.value.isLoading && vm.uiState.value.totalCards == 3 }
        rule.onNodeWithTag("verb_start_button").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(started.all { it.promptRu.startsWith("old") })
        install("new", 4)
        rule.runOnUiThread { vm.reloadForPack(packId) }
        await { !vm.uiState.value.isLoading && vm.uiState.value.totalCards == 4 }
        assertEquals(null, vm.uiState.value.session)
        rule.onNodeWithTag("repeat_button").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(started.isNotEmpty())
        assertTrue(started.all { it.promptRu.startsWith("new") })

        val saved = StoreFactory.getInstance(app).getVerbDrillStore(packId).loadLastSession()
        rule.runOnUiThread { vm.reloadForLanguage("it"); vm.refreshLastSessionContext() }
        rule.waitForIdle()
        assertEquals(0, vm.uiState.value.totalCards)
        assertEquals(null, vm.uiState.value.session)
        assertEquals(saved, StoreFactory.getInstance(app).getVerbDrillStore(packId).loadLastSession())
    }

    @Test fun samePackVocabUpdateIsUsedByStartAndNoPackDoesNotScanOtherPacks() {
        install("old", 3)
        val vm = VocabDrillViewModel(app)
        rule.setContent { MaterialTheme { VocabDrillScreen(vm, onBack = {}, voiceAutoStart = false) } }
        rule.runOnUiThread { vm.reloadForPack(packId, "it") }
        await { !vm.uiState.value.isLoading && vm.uiState.value.totalCount == 3 }
        rule.onNodeWithText(app.getString(R.string.vocab_start, 3)).performClick()
        rule.waitForIdle()
        assertTrue(vm.uiState.value.session!!.cards.all { it.word.word.startsWith("old") })
        install("new", 4)
        rule.runOnUiThread { vm.reloadForPack(packId, "it") }
        await { !vm.uiState.value.isLoading && vm.uiState.value.totalCount == 4 }
        assertEquals(null, vm.uiState.value.session)
        rule.onNodeWithText(app.getString(R.string.vocab_start, 4)).performClick()
        rule.waitForIdle()
        assertTrue(vm.uiState.value.session!!.cards.all { it.word.word.startsWith("new") })
        rule.onNodeWithText("newword1").assertIsDisplayed()
        rule.runOnUiThread { vm.reloadForLanguage("it") }
        rule.waitForIdle()
        assertEquals(0, vm.uiState.value.totalCount)
        assertEquals(null, vm.uiState.value.session)
    }

    @Test fun delayedVerbCatalogCannotReplaceNewerPackSelection() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        mockkConstructor(VerbDrillCardCatalogLoader::class)
        every { anyConstructed<VerbDrillCardCatalogLoader>().load(any(), "old-pack", any()) } answers {
            entered.countDown()
            try {
                check(release.await(10, TimeUnit.SECONDS))
                snapshot("old")
            } finally { finished.countDown() }
        }
        every { anyConstructed<VerbDrillCardCatalogLoader>().load(any(), "new-pack", any()) } returns snapshot("new")
        val vm = VerbDrillViewModel(app)
        rule.setContent { MaterialTheme { VerbDrillScreen(vm, onBack = {}, onStartSession = {}) } }
        try {
            rule.runOnUiThread { vm.reloadForPack("old-pack") }
            await { entered.count == 0L }
            rule.runOnUiThread { vm.reloadForPack("new-pack") }
            await { vm.uiState.value.availableGroups == listOf("new") }
            release.countDown()
            assertTrue(finished.await(10, TimeUnit.SECONDS))
            rule.waitForIdle()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("new"), vm.uiState.value.availableGroups)
        } finally { release.countDown() }
    }

    @Test fun clearingPackDuringVocabLoadCannotRepublishItsCatalog() {
        install("old", 3)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        mockkObject(ItalianDrillVocabParser)
        every { ItalianDrillVocabParser.parse(any(), any()) } answers {
            entered.countDown()
            try {
                check(release.await(10, TimeUnit.SECONDS))
                callOriginal()
            } finally { finished.countDown() }
        }
        val vm = VocabDrillViewModel(app)
        rule.setContent { MaterialTheme { VocabDrillScreen(vm, onBack = {}, voiceAutoStart = false) } }
        try {
            rule.runOnUiThread { vm.reloadForPack(packId, "it") }
            await { entered.count == 0L }
            rule.runOnUiThread { vm.reloadForLanguage("it") }
            release.countDown()
            assertTrue(finished.await(10, TimeUnit.SECONDS))
            rule.waitForIdle()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, vm.uiState.value.totalCount)
            assertTrue(vm.uiState.value.availablePos.isEmpty())
        } finally { release.countDown() }
    }

    private fun snapshot(label: String) = VerbDrillCardCatalogLoader.Snapshot(
        cards = listOf(VerbDrillCard(label, label, label, group = label)),
        cardToPack = mapOf(label to "$label-pack"), packIds = setOf("$label-pack"),
        tenses = emptyList(), groups = listOf(label), persons = emptyList(), progress = emptyMap(),
        badCount = 0, lang = "it", tenseInfo = emptyMap()
    )

    private fun install(label: String, count: Int) {
        val zip = File(app.cacheDir, "$packId.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            fun entry(name: String, text: String) {
                output.putNextEntry(ZipEntry(name))
                output.write(text.toByteArray())
                output.closeEntry()
            }
            entry("manifest.json", """{"schemaVersion":1,"packId":"$packId","packVersion":"$label","language":"it","verbDrill":{"files":["it_verbs.csv"]},"vocabDrill":{"files":["it_drill_nouns.csv"]}}""")
            entry("it_verbs.csv", "Verb Drill\nRU;IT;Verb;Tense;Group;Rank\n" +
                (1..count).joinToString("\n") { "$label prompt $it;Io parlo $it;parlare;Presente;are;$it" })
            entry("it_drill_nouns.csv", "rank,noun,collocations,ru\n" +
                (1..count).joinToString("\n") { "$it,${label}word$it,phrase,meaning" })
        }
        StoreFactory.getInstance(app).getLessonStore().importPackFromUri(Uri.fromFile(zip), app.contentResolver)
    }

    private fun await(condition: () -> Boolean) {
        rule.waitUntil(30_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
