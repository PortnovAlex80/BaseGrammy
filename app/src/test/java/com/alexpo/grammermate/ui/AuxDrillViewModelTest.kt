package com.alexpo.grammermate.ui

import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.data.AuxDrillCatalog
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.testharness.FakeAuxDrillStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric is required because [AuxDrillViewModel] extends AndroidViewModel
 * and needs an Application context. Same pattern as VerbDrillViewModel tests.
 */
@RunWith(RobolectricTestRunner::class)
class AuxDrillViewModelTest {

    private val application = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun makeVm(store: FakeAuxDrillStore = FakeAuxDrillStore()) =
        AuxDrillViewModel(application, store)

    private fun makeCards(): List<VerbDrillCard> {
        // 3 avere/Presente + 3 essere/Presente + 2 other
        return listOf(
            VerbDrillCard("a1", "ru1", "Io ho x", "avere", "Presente", "irregular_unique", "Io", 1),
            VerbDrillCard("a2", "ru2", "Tu hai x", "avere", "Presente", "irregular_unique", "Tu", 2),
            VerbDrillCard("a3", "ru3", "Lui ha x", "avere", "Presente", "irregular_unique", "Lui", 3),
            VerbDrillCard("e1", "ru4", "Io sono x", "essere", "Presente", "irregular_unique", "Io", 1),
            VerbDrillCard("e2", "ru5", "Tu sei x", "essere", "Presente", "irregular_unique", "Tu", 2),
            VerbDrillCard("e3", "ru6", "Lui è x", "essere", "Presente", "irregular_unique", "Lui", 3),
            VerbDrillCard("o1", "ru7", "Io compro", "comprare", "Presente", "regular_are", "Io", 1),
            VerbDrillCard("o2", "ru8", "Io compravo", "comprare", "Imperfetto", "regular_are", "Io", 1)
        )
    }

    @Test
    fun injectCards_buildsAuxCardsFromPool() {
        val vm = makeVm()
        vm.injectPoolForTest(makeCards())

        assertEquals(8, vm.allPoolCardsForTest().size)
    }

    @Test
    fun selectPair_filtersToAuxVerbAndTense() {
        val vm = makeVm()
        vm.injectPoolForTest(makeCards())

        val pair = AuxDrillCatalog.ALL.first { it.verb == "avere" && it.tense == "Presente" }
        vm.selectPair(pair)

        val filtered = vm.currentFilteredCardsForTest()
        assertEquals(3, filtered.size)
        assertTrue(filtered.all { it.verb == "avere" && it.tense == "Presente" })
    }

    @Test
    fun selectPair_reportsTotalCardsCount() {
        val vm = makeVm()
        vm.injectPoolForTest(makeCards())

        val pair = AuxDrillCatalog.ALL.first { it.verb == "essere" && it.tense == "Presente" }
        vm.selectPair(pair)

        assertEquals(3, vm.uiState.value.totalCards)
    }

    @Test
    fun startSession_buildsSessionFromFilteredCards() {
        val vm = makeVm()
        vm.injectPoolForTest(makeCards())

        val pair = AuxDrillCatalog.ALL.first { it.verb == "avere" && it.tense == "Presente" }
        vm.selectPair(pair)
        vm.setSessionSizeForTest(2)
        vm.startSession()

        val session = vm.uiState.value.session
        assertTrue(session != null)
        assertEquals(2, session?.cards?.size)
        assertTrue(session?.cards?.all { it.verb == "avere" } == true)
    }

    @Test
    fun submitCorrectAnswer_advancesAndPersists() {
        val vm = makeVm()
        vm.injectPoolForTest(makeCards())
        val pair = AuxDrillCatalog.ALL.first { it.verb == "avere" && it.tense == "Presente" }
        vm.selectPair(pair)
        vm.setSessionSizeForTest(2)
        vm.startSession()

        val firstCardId = vm.uiState.value.session!!.cards.first().id
        vm.submitCorrectAnswer()

        assertEquals(1, vm.uiState.value.session?.correctCount)
        val key = "aux|avere|Presente"
        val progress = (vm.auxStoreForTest() as com.alexpo.grammermate.testharness.FakeAuxDrillStore).loadProgress()[key]
        assertTrue(progress?.everShownCardIds?.contains(firstCardId) == true)
    }

    @Test
    fun startSession_marksAllDoneWhenNoCards() {
        val vm = makeVm()
        vm.injectPoolForTest(makeCards())
        // Futuro Semplice: no cards in test pool
        val pair = AuxDrillCatalog.ALL.first { it.verb == "avere" && it.tense == "Futuro Semplice" }
        vm.selectPair(pair)
        vm.startSession()

        assertTrue(vm.uiState.value.allDoneToday)
        assertEquals(null, vm.uiState.value.session)
    }

    @Test
    fun exitSession_clearsSession() {
        val vm = makeVm()
        vm.injectPoolForTest(makeCards())
        val pair = AuxDrillCatalog.ALL.first { it.verb == "avere" && it.tense == "Presente" }
        vm.selectPair(pair)
        vm.setSessionSizeForTest(2)
        vm.startSession()

        vm.exitSession()

        assertEquals(null, vm.uiState.value.session)
    }
}
