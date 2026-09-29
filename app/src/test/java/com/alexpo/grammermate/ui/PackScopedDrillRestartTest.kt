package com.alexpo.grammermate.ui

import android.app.Application
import android.os.Looper
import com.alexpo.grammermate.AppContainer
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.VerbDrillStoreImpl
import com.alexpo.grammermate.data.WordMasteryStoreImpl
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Real CSV, pack-scoped YAML stores and production ViewModels across an A/B switch and restart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PackScopedDrillRestartTest {
    private lateinit var application: Application
    private lateinit var container: AppContainer
    private lateinit var baseDir: File
    private lateinit var previousProgress: TrainingProgress
    private val suffix = java.lang.Long.toHexString(System.nanoTime())
    private val packA = "ru-it-b3-a-$suffix"
    private val packB = "ru-it-b3-b-$suffix"

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        container = AppContainer(application)
        previousProgress = container.progressStore.load()
        container.progressStore.save(previousProgress.copy(languageId = LanguageId("it")))

        // Resolve the directory through the same factory-backed store the VMs use.
        // This remains correct when another Robolectric test first bound the factory.
        val packDir = requireNotNull(File(
            (container.verbDrillStore(packA) as VerbDrillStoreImpl).lastSessionFilePath()
        ).parentFile)
        baseDir = requireNotNull(requireNotNull(packDir.parentFile).parentFile)
    }

    @After
    fun tearDown() {
        if (::container.isInitialized) {
            container.evict(packA)
            container.evict(packB)
            if (::previousProgress.isInitialized) container.progressStore.save(previousProgress)
        }
        if (::baseDir.isInitialized) {
            val drillsRoot = File(baseDir, "drills").canonicalFile
            val expectedRoot = File(application.filesDir, "grammarmate/drills").canonicalFile
            if (drillsRoot == expectedRoot) {
                for (packId in listOf(packA, packB)) {
                    val target = File(drillsRoot, packId).canonicalFile
                    if (target.parentFile == drillsRoot && target.name == packId) {
                        target.deleteRecursively()
                    }
                }
            }
        }
    }

    @Test
    fun savedVerbBatchAndProgressStayInTheirPackAfterSwitchAndViewModelRestart() {
        seedVerbCsv(packA, "A")
        seedVerbCsv(packB, "B")
        assertEquals("it", container.progressStore.load().languageId.value)
        assertEquals(4, container.verbDrillStore(packA).loadAllCardsForPack(packA, "it").size)

        val first = VerbDrillViewModel(application)
        first.reloadForPack(packA)
        awaitVerbCards(first)
        first.setSessionSize(3)
        first.toggleSortByFrequency()
        first.startSession()
        val aBatch = requireNotNull(first.uiState.value.session).cards
        assertEquals(3, aBatch.size)
        assertTrue(aBatch.all { it.promptRu.startsWith("A prompt") && it.answer.startsWith("Io A") })
        val savedIds = aBatch.map { it.id }

        first.submitCorrectAnswer()
        first.exitSession()
        val aOnDisk = VerbDrillStoreImpl(application, packA)
        assertEquals(packA, aOnDisk.loadLastSession()?.packId)
        assertEquals(savedIds, aOnDisk.loadLastSession()?.sessionCardIds)
        assertEquals(1, aOnDisk.loadLastSession()?.currentIndex)
        assertEquals(1, aOnDisk.loadProgress().values.sumOf { it.everShownCardIds.size })

        // Deliberately reuse the same generated card IDs in B. The visible
        // prompts and checkpoint must still come from B's own CSV and YAML.
        first.reloadForPack(packB)
        awaitVerbCards(first)
        assertNull(first.uiState.value.lastSessionContext)
        assertEquals(0, first.uiState.value.everShownCount)
        first.setSessionSize(3)
        first.startSession()
        val bBatch = requireNotNull(first.uiState.value.session).cards
        assertEquals(savedIds.toSet(), bBatch.map { it.id }.toSet())
        assertTrue(bBatch.all { it.promptRu.startsWith("B prompt") && it.answer.startsWith("Io B") })
        first.exitSession()
        assertEquals(packB, VerbDrillStoreImpl(application, packB).loadLastSession()?.packId)

        // A new VM must read A's saved batch from disk and replay A's cards.
        val restarted = VerbDrillViewModel(application)
        restarted.reloadForPack(packA)
        awaitVerbCards(restarted)
        assertEquals(savedIds, restarted.uiState.value.lastSessionContext?.sessionCardIds)
        assertEquals(1, restarted.uiState.value.lastSessionContext?.currentIndex)
        assertEquals(1, restarted.uiState.value.everShownCount)
        restarted.onRepeatSession()
        val repeated = requireNotNull(restarted.uiState.value.session)
        assertEquals(0, repeated.currentIndex)
        assertEquals(savedIds, repeated.cards.map { it.id })
        assertTrue(repeated.cards.all { it.promptRu.startsWith("A prompt") && it.answer.startsWith("Io A") })
        assertEquals(0, VerbDrillStoreImpl(application, packB).loadProgress().size)
    }

    @Test
    fun vocabRatingWithCollidingWordIdSurvivesRestartWithoutChangingOtherPack() {
        seedVocabCsv(packA, "A home")
        seedVocabCsv(packB, "B home")
        val wordId = "nouns_1_casa"

        val first = VocabDrillViewModel(application)
        first.reloadForPack(packA, "it")
        awaitVocabCards(first)
        first.startSession()
        val aCard = requireNotNull(first.uiState.value.session).cards.single()
        assertEquals(wordId, aCard.word.id)
        assertEquals("A home", aCard.word.meaningRu)
        first.flipCard()
        first.answerRating(SrsRating.GOOD)

        val aOnDisk = requireNotNull(WordMasteryStoreImpl(application, packA).getMastery(wordId))
        assertEquals(1, aOnDisk.intervalStepIndex)
        assertEquals(1, aOnDisk.correctCount)
        assertNull(WordMasteryStoreImpl(application, packB).getMastery(wordId))

        first.reloadForPack(packB, "it")
        awaitVocabCards(first)
        assertEquals(1, first.uiState.value.dueCount)
        first.startSession()
        val bCard = requireNotNull(first.uiState.value.session).cards.single()
        assertEquals(wordId, bCard.word.id)
        assertEquals("B home", bCard.word.meaningRu)
        assertEquals(0, bCard.mastery.intervalStepIndex)
        first.flipCard()
        first.answerRating(SrsRating.AGAIN)

        val restarted = VocabDrillViewModel(application)
        restarted.reloadForPack(packA, "it")
        awaitVocabCards(restarted)
        assertEquals(0, restarted.uiState.value.dueCount)
        restarted.startSession()
        assertNull(restarted.uiState.value.session)

        restarted.reloadForPack(packB, "it")
        awaitVocabCards(restarted)
        val bOnDisk = requireNotNull(WordMasteryStoreImpl(application, packB).getMastery(wordId))
        assertEquals(0, bOnDisk.intervalStepIndex)
        assertEquals(0, bOnDisk.correctCount)
        assertEquals(1, bOnDisk.incorrectCount)
        assertEquals(1, WordMasteryStoreImpl(application, packA).getMastery(wordId)?.correctCount)
    }

    private fun seedVerbCsv(packId: String, marker: String) {
        val file = File(baseDir, "drills/$packId/verb_drill/it_scope.csv")
        file.parentFile!!.mkdirs()
        file.writeText(buildString {
            appendLine("Pack scope test")
            appendLine("ru;it;verb;tense;group;rank")
            for (rank in 1..4) {
                appendLine("$marker prompt $rank;Io $marker answer $rank;studiare;Presente;group;$rank")
            }
        })
    }

    private fun seedVocabCsv(packId: String, meaning: String) {
        val file = File(baseDir, "drills/$packId/vocab_drill/it_drill_nouns.csv")
        file.parentFile!!.mkdirs()
        file.writeText("rank,noun,collocations,ru\n1,casa,di casa,$meaning\n")
    }

    private fun awaitVerbCards(vm: VerbDrillViewModel) {
        awaitLoaded({ "verb state=${vm.uiState.value}" }) {
            val state = vm.uiState.value
            !state.isLoading && state.loadedLanguageId == "it" && state.totalCards == 4
        }
        assertEquals(4, vm.uiState.value.totalCards)
    }

    private fun awaitVocabCards(vm: VocabDrillViewModel) {
        awaitLoaded({ "vocab state=${vm.uiState.value}" }) {
            val state = vm.uiState.value
            !state.isLoading && state.loadedLanguageId == "it" && state.totalCount == 1
        }
        assertEquals(1, vm.uiState.value.totalCount)
    }

    private fun awaitLoaded(detail: () -> String, ready: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!ready() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Pack-scoped drill did not load: ${detail()}", ready())
    }
}
