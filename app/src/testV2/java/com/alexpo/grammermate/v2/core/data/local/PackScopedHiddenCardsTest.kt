package com.alexpo.grammermate.v2.core.data.local

import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.v2.core.data.local.entity.HiddenCardEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackScopedHiddenCardsTest {

    private lateinit var db: GrammarMateDatabase

    @Before
    fun setUp() {
        db = TrainingDbFixture.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun sameCardId_canBeHiddenAndUnhiddenIndependentlyPerPack() = runTest {
        val dao = db.userContentDao()
        dao.hideCard(HiddenCardEntity(packId = "PACK_A", cardId = "shared", hiddenAtMs = 1))
        dao.hideCard(HiddenCardEntity(packId = "PACK_B", cardId = "shared", hiddenAtMs = 2))

        assertThat(dao.getHiddenCardIds("PACK_A")).containsExactly("shared")
        assertThat(dao.getHiddenCardIds("PACK_B")).containsExactly("shared")

        dao.unhideCard("PACK_A", "shared")

        assertThat(dao.getHiddenCardIds("PACK_A")).isEmpty()
        assertThat(dao.observeHiddenCardIds("PACK_B").first()).containsExactly("shared")
    }
}
