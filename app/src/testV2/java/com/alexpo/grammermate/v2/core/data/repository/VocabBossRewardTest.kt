package com.alexpo.grammermate.v2.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.domain.model.BossReward
import com.alexpo.grammermate.domain.model.BossType
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase
import com.alexpo.grammermate.v2.core.data.local.TrainingDbFixture
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Наградная инфраструктура boss-битв (срез 5 Фазы 4): ключ карты включает
 * bossType (фикс L-3) и выдача exactly-once/best-of (без понижения уровня).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VocabBossRewardTest {

    private lateinit var db: GrammarMateDatabase
    private lateinit var repository: VocabDrillRepositoryImpl

    private val packId = PackId(TrainingDbFixture.PACK_ID)

    @Before
    fun setUp() {
        db = TrainingDbFixture.inMemory(ApplicationProvider.getApplicationContext())
        repository = VocabDrillRepositoryImpl(db.drillDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** L-3: LESSON и ELITE с одинаковым scopeKey НЕ схлопываются в карте. */
    @Test
    fun getBossRewards_keyIncludesBossType() = runTest {
        repository.saveBossReward(packId, BossType.LESSON, "lesson_01", BossReward.BRONZE, nowMs = 1)
        repository.saveBossReward(packId, BossType.ELITE, "lesson_01", BossReward.GOLD, nowMs = 2)

        val rewards = repository.getBossRewards(packId)

        assertThat(rewards).hasSize(2)
        assertThat(rewards["lesson:lesson_01"]).isEqualTo(BossReward.BRONZE)
        assertThat(rewards["elite:lesson_01"]).isEqualTo(BossReward.GOLD)
    }

    /** Повторная выдача той же награды — no-op (время не сдвигается). */
    @Test
    fun save_sameReward_isNoOp() = runTest {
        repository.saveBossReward(packId, BossType.MEGA, "mega", BossReward.GOLD, nowMs = 100)
        repository.saveBossReward(packId, BossType.MEGA, "mega", BossReward.GOLD, nowMs = 999)

        val row = db.drillDao().getBossReward(packId.value, "MEGA", "mega")!!
        assertThat(row.reward).isEqualTo("GOLD")
        assertThat(row.earnedAtMs).isEqualTo(100)
    }

    /** Понижение (GOLD→BRONZE) невозможно; повышение фиксируется. */
    @Test
    fun save_upgradeOnly_bronzeToGold() = runTest {
        repository.saveBossReward(packId, BossType.LESSON, "lesson_02", BossReward.BRONZE, nowMs = 1)
        repository.saveBossReward(packId, BossType.LESSON, "lesson_02", BossReward.GOLD, nowMs = 2)
        // Попытка понизить после GOLD — игнорируется.
        repository.saveBossReward(packId, BossType.LESSON, "lesson_02", BossReward.BRONZE, nowMs = 3)

        val rewards = repository.getBossRewards(packId)
        assertThat(rewards["lesson:lesson_02"]).isEqualTo(BossReward.GOLD)
    }
}
