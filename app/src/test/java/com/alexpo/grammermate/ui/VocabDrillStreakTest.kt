package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.Looper
import com.alexpo.grammermate.data.PracticeType
import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.StreakStoreImpl
import com.alexpo.grammermate.data.StoreFactory
import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.data.WordMasteryStoreImpl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The VOCAB fire belongs to ratings made in this session, not old card mastery. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VocabDrillStreakTest {
    private val packId = "VOCAB_STREAK_${System.nanoTime()}"
    private val wordId = "nouns_1_casa"
    private lateinit var application: Application
    private var previousStreak: StreakData? = null

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        val stores = StoreFactory.getInstance(application)
        previousStreak = stores.getStreakStore().load("it")
        stores.getStreakStore().resetForLanguage("it")
        importVocabPack()
        stores.getWordMasteryStore(packId).saveAll(emptyMap())
    }

    @After
    fun tearDown() {
        if (::application.isInitialized) {
            val stores = StoreFactory.getInstance(application)
            stores.getWordMasteryStore(packId).saveAll(emptyMap())
            stores.getLessonStore().removeInstalledPackData(packId)
            previousStreak?.let(stores.getStreakStore()::save)
        }
    }

    @Test
    fun newWordRatedGoodEarnsVocabFireAndSurvivesRestart() {
        val vm = loadedViewModel()
        vm.startSession()
        assertEquals(0L, vm.uiState.value.session!!.cards.single().mastery.lastReviewDateMs)

        vm.flipCard()
        assertTrue(vm.uiState.value.session!!.isFlipped)
        vm.answerRating(SrsRating.GOOD)
        vm.exitSession()

        // A fresh ViewModel must rehydrate from disk, not a factory-cached store.
        StoreFactory.getInstance(application).evict(packId)
        val restarted = loadedViewModel()
        assertEquals(0, restarted.uiState.value.dueCount)
        val persistedStreak = StreakStoreImpl(application).load("it")
        assertTrue(PracticeType.VOCAB in persistedStreak.completedTypesToday)
        assertEquals(1, persistedStreak.todayFireCount)
        val persistedMastery = WordMasteryStoreImpl(application, packId).loadAll()[wordId]
        assertNotNull(persistedMastery)
        assertEquals(1, persistedMastery!!.intervalStepIndex)
        assertEquals(1, persistedMastery!!.correctCount)
        assertTrue(persistedMastery.lastReviewDateMs > 0L)

        assertTrue(PracticeType.VOCAB in StreakStoreImpl(application).load("it").completedTypesToday)
        vm.exitSession()
        assertEquals(persistedStreak, StreakStoreImpl(application).load("it"))
    }

    @Test
    fun previouslyReviewedWordRatedAgainDoesNotEarnVocabFire() {
        StoreFactory.getInstance(application).getWordMasteryStore(packId).saveAll(
            mapOf(wordId to WordMasteryState(
                wordId = wordId,
                correctCount = 1,
                lastReviewDateMs = System.currentTimeMillis() - 2 * WordMasteryState.DAY_MS,
                nextReviewDateMs = 0L
            ))
        )
        val vm = loadedViewModel()
        vm.startSession()
        assertTrue(vm.uiState.value.session!!.cards.single().mastery.lastReviewDateMs > 0L)

        vm.flipCard()
        assertTrue(vm.uiState.value.session!!.isFlipped)
        vm.answerRating(SrsRating.AGAIN)
        vm.exitSession()

        val persistedStreak = StreakStoreImpl(application).load("it")
        assertFalse(PracticeType.VOCAB in persistedStreak.completedTypesToday)
        assertEquals(0, persistedStreak.todayFireCount)
        val persistedMastery = WordMasteryStoreImpl(application, packId).loadAll()[wordId]
        assertEquals(1, persistedMastery?.incorrectCount)
    }

    private fun loadedViewModel(): VocabDrillViewModel {
        val vm = VocabDrillViewModel(application)
        vm.reloadForPack(packId, "it")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (!vm.uiState.value.isLoading && vm.uiState.value.totalCount == 1) return vm
            Thread.sleep(10)
        }
        error("Vocab fixture did not load: ${vm.uiState.value}")
    }

    private fun importVocabPack() {
        val archive = File(application.cacheDir, "$packId.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            fun entry(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("manifest.json", """{"schemaVersion":2,"packId":"$packId","packVersion":"test-1","language":"it","vocabDrill":{"files":["it_drill_nouns.csv"]}}""")
            entry("it_drill_nouns.csv", "rank,noun,collocations,ru\n1,casa,di casa,дом\n")
        }
        StoreFactory.getInstance(application).getLessonStore()
            .importPackFromUri(Uri.fromFile(archive), application.contentResolver)
    }
}
