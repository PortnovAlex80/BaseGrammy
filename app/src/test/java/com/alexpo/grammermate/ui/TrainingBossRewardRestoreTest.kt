package com.alexpo.grammermate.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.StoreFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TrainingBossRewardRestoreTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun diskReloadReplacesLiveBossRewardsAndPackSwitchSavesTheRestoredMaps() {
        val app: Application = rule.activity.application
        val stores = StoreFactory.getInstance(app)
        val progressStore = stores.getProgressStore()
        val lessonStore = stores.getLessonStore()
        val originalProgress = progressStore.load()
        val vm = TrainingViewModel(app)
        rule.setContent {
            Text(vm.uiState.collectAsState().value.boss.bossLessonRewards.toString())
        }
        rule.waitUntil(240_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            !vm.uiState.value.isLoading
        }

        try {
            val lessonA = mapOf("reward-a" to "BRONZE")
            val megaA = mapOf("mega-a" to "SILVER")
            progressStore.save(progressStore.load().copy(
                bossLessonRewards = lessonA,
                bossMegaRewards = megaA
            ))
            rule.runOnUiThread { vm.reloadFromDisk() }
            rule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.uiState.value.boss.bossLessonRewards == mapOf("reward-a" to BossReward.BRONZE)
            }

            val lessonB = mapOf("reward-b" to "GOLD")
            val megaB = mapOf("mega-b" to "BRONZE")
            progressStore.save(progressStore.load().copy(
                bossLessonRewards = lessonB,
                bossMegaRewards = megaB
            ))
            rule.runOnUiThread { vm.reloadFromDisk() }
            rule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.uiState.value.boss.bossLessonRewards == mapOf("reward-b" to BossReward.GOLD)
            }
            assertEquals(mapOf("mega-b" to BossReward.BRONZE), vm.uiState.value.boss.bossMegaRewards)

            val pack = lessonStore.getInstalledPacks().firstOrNull { installed ->
                lessonStore.getLessonIdsForPack(installed.packId.value).isNotEmpty()
            }
            assertTrue("A seeded lesson pack is required", pack != null)
            // Selecting a lesson calls the normal boss-session reset and saves progress.
            rule.runOnUiThread { vm.selectPack(requireNotNull(pack).packId.value) }
            assertEquals(lessonB, progressStore.load().bossLessonRewards)
            assertEquals(megaB, progressStore.load().bossMegaRewards)
            assertEquals(mapOf("reward-b" to BossReward.GOLD), vm.uiState.value.boss.bossLessonRewards)
        } finally {
            progressStore.save(originalProgress)
        }
    }
}
