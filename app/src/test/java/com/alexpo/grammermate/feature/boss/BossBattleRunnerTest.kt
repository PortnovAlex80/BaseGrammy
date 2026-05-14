package com.alexpo.grammermate.feature.boss

import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for BossBattleRunner — pure-logic boss battle lifecycle.
 *
 * Covers:
 * - startBoss: unlock guard, lesson selection, card pool validation
 * - updateBossProgress: progress coercion, reward tier transitions, pause trigger
 * - resolveBossReward: threshold boundaries (30/60/90%), edge cases (total=0, progress>total)
 * - finishBoss: reward map updates per boss type, no-reward case
 * - clearBossRewardMessage: timer/voice resume conditions
 * - bossRewardMessage: human-readable strings
 */
class BossBattleRunnerTest {

    private lateinit var runner: BossBattleRunner

    // ── Shared test fixtures ─────────────────────────────────────────

    private val testCard = SentenceCard(
        id = "card-1",
        promptRu = "test prompt",
        acceptedAnswers = listOf("test answer")
    )

    private val tenCards: List<SentenceCard> = (1..10).map { i ->
        SentenceCard(id = "card-$i", promptRu = "p$i", acceptedAnswers = listOf("a$i"))
    }

    private val fifteenCards: List<SentenceCard> = (1..15).map { i ->
        SentenceCard(id = "card-$i", promptRu = "p$i", acceptedAnswers = listOf("a$i"))
    }

    @Before
    fun setUp() {
        runner = BossBattleRunner()
    }

    // ══════════════════════════════════════════════════════════════════
    // startBoss
    // ══════════════════════════════════════════════════════════════════

    // ── Unlock guard ──────────────────────────────────────────────────

    @Test
    fun startBoss_lessThan15SubLessons_returnsFailureWithUnlockMessage() {
        val result = runner.startBoss(
            type = BossType.LESSON,
            cards = tenCards,
            selectedLessonId = "lesson-1",
            completedSubLessonCount = 14,
            testMode = false
        )
        assertFalse(result.success)
        assertEquals(0, result.cards.size)
        assertEquals(
            "Complete at least ${TrainingConfig.BOSS_UNLOCK_SUB_LESSONS} exercises first",
            result.errorMessage
        )
    }

    @Test
    fun startBoss_exactly15SubLessons_returnsSuccess() {
        val result = runner.startBoss(
            type = BossType.LESSON,
            cards = tenCards,
            selectedLessonId = "lesson-1",
            completedSubLessonCount = 15,
            testMode = false
        )
        assertTrue(result.success)
        assertEquals(10, result.cards.size)
    }

    @Test
    fun startBoss_moreThan15SubLessons_returnsSuccess() {
        val result = runner.startBoss(
            type = BossType.LESSON,
            cards = tenCards,
            selectedLessonId = "lesson-1",
            completedSubLessonCount = 99,
            testMode = false
        )
        assertTrue(result.success)
    }

    @Test
    fun startBoss_unlockGuard_skippedInTestMode() {
        val result = runner.startBoss(
            type = BossType.LESSON,
            cards = tenCards,
            selectedLessonId = "lesson-1",
            completedSubLessonCount = 0,
            testMode = true
        )
        assertTrue(result.success)
    }

    @Test
    fun startBoss_unlockGuard_skippedForEliteType() {
        val result = runner.startBoss(
            type = BossType.ELITE,
            cards = tenCards,
            selectedLessonId = null,
            completedSubLessonCount = 0,
            testMode = false
        )
        assertTrue(result.success)
    }

    // ── Lesson selection guard ────────────────────────────────────────

    @Test
    fun startBoss_nullLessonId_forLessonType_returnsFailure() {
        val result = runner.startBoss(
            type = BossType.LESSON,
            cards = tenCards,
            selectedLessonId = null,
            completedSubLessonCount = 20,
            testMode = false
        )
        assertFalse(result.success)
        assertEquals("Lesson not selected", result.errorMessage)
    }

    @Test
    fun startBoss_nullLessonId_forMegaType_returnsFailure() {
        val result = runner.startBoss(
            type = BossType.MEGA,
            cards = tenCards,
            selectedLessonId = null,
            completedSubLessonCount = 20,
            testMode = false
        )
        assertFalse(result.success)
        assertEquals("Lesson not selected", result.errorMessage)
    }

    @Test
    fun startBoss_nullLessonId_forEliteType_returnsSuccess() {
        val result = runner.startBoss(
            type = BossType.ELITE,
            cards = tenCards,
            selectedLessonId = null,
            completedSubLessonCount = 0,
            testMode = false
        )
        assertTrue(result.success)
    }

    // ── Empty card pool ───────────────────────────────────────────────

    @Test
    fun startBoss_emptyCards_forLessonType_returnsNoCardsMessage() {
        val result = runner.startBoss(
            type = BossType.LESSON,
            cards = emptyList(),
            selectedLessonId = "lesson-1",
            completedSubLessonCount = 20,
            testMode = false
        )
        assertFalse(result.success)
        assertEquals("Boss has no cards", result.errorMessage)
    }

    @Test
    fun startBoss_emptyCards_forMegaType_returnsMegaMessage() {
        val result = runner.startBoss(
            type = BossType.MEGA,
            cards = emptyList(),
            selectedLessonId = "lesson-1",
            completedSubLessonCount = 20,
            testMode = false
        )
        assertFalse(result.success)
        assertEquals("Mega boss is available after the first lesson", result.errorMessage)
    }

    @Test
    fun startBoss_emptyCards_forEliteType_returnsEliteMessage() {
        val result = runner.startBoss(
            type = BossType.ELITE,
            cards = emptyList(),
            selectedLessonId = null,
            completedSubLessonCount = 0,
            testMode = false
        )
        assertFalse(result.success)
        assertEquals("Elite boss has no cards", result.errorMessage)
    }

    // ── Successful start ──────────────────────────────────────────────

    @Test
    fun startBoss_validInput_returnsCardsAndCorrectMetadata() {
        val result = runner.startBoss(
            type = BossType.LESSON,
            cards = fifteenCards,
            selectedLessonId = "lesson-1",
            completedSubLessonCount = 20,
            testMode = false
        )
        assertTrue(result.success)
        assertEquals(fifteenCards, result.cards)
        assertEquals(15, result.subLessonTotal)
        assertEquals(1, result.subLessonCount)
        assertNull(result.errorMessage)
    }

    // ── Guard priority: unlock checked before lesson selection ────────

    @Test
    fun startBoss_unlockFails_beforeLessonSelectionIsChecked() {
        // Both guards would fail, but unlock is checked first
        val result = runner.startBoss(
            type = BossType.LESSON,
            cards = tenCards,
            selectedLessonId = null,
            completedSubLessonCount = 0,
            testMode = false
        )
        assertFalse(result.success)
        // Unlock guard message, not "Lesson not selected"
        assertTrue(result.errorMessage!!.contains("Complete at least"))
    }

    // ══════════════════════════════════════════════════════════════════
    // resolveBossReward — threshold boundaries
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun resolveBossReward_totalZero_returnsNull() {
        assertNull(runner.resolveBossReward(0, 0))
    }

    @Test
    fun resolveBossReward_totalNegative_returnsNull() {
        assertNull(runner.resolveBossReward(5, -1))
    }

    @Test
    fun resolveBossReward_zeroProgress_returnsNull() {
        assertNull(runner.resolveBossReward(0, 100))
    }

    @Test
    fun resolveBossReward_below30Percent_returnsNull() {
        // 29/100 = 29% → below BRONZE threshold
        assertNull(runner.resolveBossReward(29, 100))
    }

    @Test
    fun resolveBossReward_exact30Percent_returnsBronze() {
        assertEquals(BossReward.BRONZE, runner.resolveBossReward(30, 100))
    }

    @Test
    fun resolveBossReward_justAbove30Percent_returnsBronze() {
        assertEquals(BossReward.BRONZE, runner.resolveBossReward(31, 100))
    }

    @Test
    fun resolveBossReward_justBelow60Percent_returnsBronze() {
        // 59/100 = 59% → still BRONZE
        assertEquals(BossReward.BRONZE, runner.resolveBossReward(59, 100))
    }

    @Test
    fun resolveBossReward_exact60Percent_returnsSilver() {
        assertEquals(BossReward.SILVER, runner.resolveBossReward(60, 100))
    }

    @Test
    fun resolveBossReward_justAbove60Percent_returnsSilver() {
        assertEquals(BossReward.SILVER, runner.resolveBossReward(61, 100))
    }

    @Test
    fun resolveBossReward_justBelow90Percent_returnsSilver() {
        // 89/100 = 89% → still SILVER
        assertEquals(BossReward.SILVER, runner.resolveBossReward(89, 100))
    }

    @Test
    fun resolveBossReward_exact90Percent_returnsGold() {
        assertEquals(BossReward.GOLD, runner.resolveBossReward(90, 100))
    }

    @Test
    fun resolveBossReward_100Percent_returnsGold() {
        assertEquals(BossReward.GOLD, runner.resolveBossReward(100, 100))
    }

    @Test
    fun resolveBossReward_progressGreaterThanTotal_returnsGold() {
        // Progress is coerced; 150/100 = 150% → GOLD
        assertEquals(BossReward.GOLD, runner.resolveBossReward(150, 100))
    }

    // ── Small total edge cases ────────────────────────────────────────

    @Test
    fun resolveBossReward_totalOf1_progress0_returnsNull() {
        assertNull(runner.resolveBossReward(0, 1))
    }

    @Test
    fun resolveBossReward_totalOf1_progress1_returnsGold() {
        // 1/1 = 100% → GOLD
        assertEquals(BossReward.GOLD, runner.resolveBossReward(1, 1))
    }

    @Test
    fun resolveBossReward_totalOf10_progress3_returnsBronze() {
        // 3/10 = 30% → BRONZE
        assertEquals(BossReward.BRONZE, runner.resolveBossReward(3, 10))
    }

    @Test
    fun resolveBossReward_totalOf10_progress6_returnsSilver() {
        // 6/10 = 60% → SILVER
        assertEquals(BossReward.SILVER, runner.resolveBossReward(6, 10))
    }

    @Test
    fun resolveBossReward_totalOf10_progress9_returnsGold() {
        // 9/10 = 90% → GOLD
        assertEquals(BossReward.GOLD, runner.resolveBossReward(9, 10))
    }

    // ── Boundary precision: total=7, inexact division ─────────────────

    @Test
    fun resolveBossReward_totalOf7_progress2_returnsBronze() {
        // 2/7 ≈ 28.57% → below 30% → null
        assertNull(runner.resolveBossReward(2, 7))
    }

    @Test
    fun resolveBossReward_totalOf7_progress3_returnsBronze() {
        // 3/7 ≈ 42.86% → >= 30% → BRONZE
        assertEquals(BossReward.BRONZE, runner.resolveBossReward(3, 7))
    }

    @Test
    fun resolveBossReward_totalOf7_progress4_returnsSilver() {
        // 4/7 ≈ 57.14% → >= 30% but < 60% → BRONZE
        assertEquals(BossReward.BRONZE, runner.resolveBossReward(4, 7))
    }

    @Test
    fun resolveBossReward_totalOf7_progress5_returnsSilver() {
        // 5/7 ≈ 71.43% → >= 60% → SILVER
        assertEquals(BossReward.SILVER, runner.resolveBossReward(5, 7))
    }

    @Test
    fun resolveBossReward_totalOf7_progress6_returnsGold() {
        // 6/7 ≈ 85.71% → >= 60% but < 90% → SILVER
        assertEquals(BossReward.SILVER, runner.resolveBossReward(6, 7))
    }

    @Test
    fun resolveBossReward_totalOf7_progress7_returnsGold() {
        // 7/7 = 100% → GOLD
        assertEquals(BossReward.GOLD, runner.resolveBossReward(7, 7))
    }

    @Test
    fun resolveBossReward_totalOf3_progress3_returnsGold() {
        // 3/3 = 100% → GOLD
        assertEquals(BossReward.GOLD, runner.resolveBossReward(3, 3))
    }

    // ══════════════════════════════════════════════════════════════════
    // updateBossProgress
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun updateBossProgress_coercesProgressToTotal() {
        val result = runner.updateBossProgress(
            progress = 150,
            currentTotal = 100,
            currentReward = null
        )
        assertEquals(100, result.nextProgress)
    }

    @Test
    fun updateBossProgress_noRewardTransition_noMessageAndNoPause() {
        val result = runner.updateBossProgress(
            progress = 10,
            currentTotal = 100,
            currentReward = null
        )
        assertEquals(10, result.nextProgress)
        assertNull(result.nextReward)
        assertNull(result.rewardMessage)
        assertFalse(result.isNewReward)
        assertFalse(result.shouldPause)
    }

    @Test
    fun updateBossProgress_crossesBronzeThreshold_returnsBronzeWithMessage() {
        val result = runner.updateBossProgress(
            progress = 30,
            currentTotal = 100,
            currentReward = null
        )
        assertEquals(30, result.nextProgress)
        assertEquals(BossReward.BRONZE, result.nextReward)
        assertEquals("Bronze reached", result.rewardMessage)
        assertTrue(result.isNewReward)
        assertTrue(result.shouldPause)
    }

    @Test
    fun updateBossProgress_crossesSilverThreshold_returnsSilverWithMessage() {
        val result = runner.updateBossProgress(
            progress = 60,
            currentTotal = 100,
            currentReward = BossReward.BRONZE
        )
        assertEquals(60, result.nextProgress)
        assertEquals(BossReward.SILVER, result.nextReward)
        assertEquals("Silver reached", result.rewardMessage)
        assertTrue(result.isNewReward)
        assertTrue(result.shouldPause)
    }

    @Test
    fun updateBossProgress_crossesGoldThreshold_returnsGoldWithMessage() {
        val result = runner.updateBossProgress(
            progress = 90,
            currentTotal = 100,
            currentReward = BossReward.SILVER
        )
        assertEquals(90, result.nextProgress)
        assertEquals(BossReward.GOLD, result.nextReward)
        assertEquals("Gold reached", result.rewardMessage)
        assertTrue(result.isNewReward)
        assertTrue(result.shouldPause)
    }

    @Test
    fun updateBossProgress_sameRewardTier_noMessageAndNoPause() {
        // Already BRONZE at 40%, still BRONZE at 50%
        val result = runner.updateBossProgress(
            progress = 50,
            currentTotal = 100,
            currentReward = BossReward.BRONZE
        )
        assertEquals(50, result.nextProgress)
        assertEquals(BossReward.BRONZE, result.nextReward)
        assertNull(result.rewardMessage)
        assertFalse(result.isNewReward)
        assertFalse(result.shouldPause)
    }

    @Test
    fun updateBossProgress_jumpFromNullToGold_crossesAllThresholds() {
        // Fast-forward from 0 to 95%: reward jumps from null → GOLD
        val result = runner.updateBossProgress(
            progress = 95,
            currentTotal = 100,
            currentReward = null
        )
        assertEquals(BossReward.GOLD, result.nextReward)
        assertEquals("Gold reached", result.rewardMessage)
        assertTrue(result.isNewReward)
    }

    @Test
    fun updateBossProgress_totalZero_returnsNoReward() {
        val result = runner.updateBossProgress(
            progress = 5,
            currentTotal = 0,
            currentReward = null
        )
        assertEquals(0, result.nextProgress)
        assertNull(result.nextReward)
        assertFalse(result.isNewReward)
    }

    // ══════════════════════════════════════════════════════════════════
    // finishBoss
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun finishBoss_lessonType_withReward_updatesLessonRewards() {
        val existing = mapOf("lesson-A" to BossReward.BRONZE)
        val result = runner.finishBoss(
            bossType = BossType.LESSON,
            bossProgress = 90,
            bossTotal = 100,
            selectedLessonId = "lesson-B",
            currentLessonRewards = existing,
            currentMegaRewards = emptyMap()
        )
        assertEquals(BossReward.GOLD, result.reward)
        assertEquals(
            mapOf("lesson-A" to BossReward.BRONZE, "lesson-B" to BossReward.GOLD),
            result.updatedLessonRewards
        )
        assertTrue(result.updatedMegaRewards.isEmpty())
    }

    @Test
    fun finishBoss_lessonType_withNoReward_doesNotUpdateLessonRewards() {
        // 10/100 = 10% → no reward
        val existing = mapOf("lesson-A" to BossReward.BRONZE)
        val result = runner.finishBoss(
            bossType = BossType.LESSON,
            bossProgress = 10,
            bossTotal = 100,
            selectedLessonId = "lesson-B",
            currentLessonRewards = existing,
            currentMegaRewards = emptyMap()
        )
        assertNull(result.reward)
        // No change to lesson rewards when reward is null
        assertEquals(existing, result.updatedLessonRewards)
    }

    @Test
    fun finishBoss_lessonType_nullLessonId_doesNotUpdateLessonRewards() {
        val result = runner.finishBoss(
            bossType = BossType.LESSON,
            bossProgress = 90,
            bossTotal = 100,
            selectedLessonId = null,
            currentLessonRewards = emptyMap(),
            currentMegaRewards = emptyMap()
        )
        assertEquals(BossReward.GOLD, result.reward)
        assertTrue(result.updatedLessonRewards.isEmpty())
    }

    @Test
    fun finishBoss_megaType_withReward_updatesMegaRewards() {
        val result = runner.finishBoss(
            bossType = BossType.MEGA,
            bossProgress = 60,
            bossTotal = 100,
            selectedLessonId = "lesson-A",
            currentLessonRewards = emptyMap(),
            currentMegaRewards = mapOf("lesson-B" to BossReward.BRONZE)
        )
        assertEquals(BossReward.SILVER, result.reward)
        assertTrue(result.updatedLessonRewards.isEmpty())
        assertEquals(
            mapOf("lesson-B" to BossReward.BRONZE, "lesson-A" to BossReward.SILVER),
            result.updatedMegaRewards
        )
    }

    @Test
    fun finishBoss_megaType_nullLessonId_doesNotUpdateMegaRewards() {
        val result = runner.finishBoss(
            bossType = BossType.MEGA,
            bossProgress = 60,
            bossTotal = 100,
            selectedLessonId = null,
            currentLessonRewards = emptyMap(),
            currentMegaRewards = emptyMap()
        )
        assertEquals(BossReward.SILVER, result.reward)
        assertTrue(result.updatedMegaRewards.isEmpty())
    }

    @Test
    fun finishBoss_megaType_noReward_doesNotUpdateMegaRewards() {
        val result = runner.finishBoss(
            bossType = BossType.MEGA,
            bossProgress = 10,
            bossTotal = 100,
            selectedLessonId = "lesson-A",
            currentLessonRewards = emptyMap(),
            currentMegaRewards = emptyMap()
        )
        assertNull(result.reward)
        assertTrue(result.updatedMegaRewards.isEmpty())
    }

    @Test
    fun finishBoss_eliteType_doesNotUpdateEitherRewardMap() {
        val lessonRewards = mapOf("lesson-A" to BossReward.BRONZE)
        val megaRewards = mapOf("lesson-B" to BossReward.SILVER)
        val result = runner.finishBoss(
            bossType = BossType.ELITE,
            bossProgress = 90,
            bossTotal = 100,
            selectedLessonId = "lesson-A",
            currentLessonRewards = lessonRewards,
            currentMegaRewards = megaRewards
        )
        assertEquals(BossReward.GOLD, result.reward)
        assertEquals(lessonRewards, result.updatedLessonRewards)
        assertEquals(megaRewards, result.updatedMegaRewards)
    }

    @Test
    fun finishBoss_nullBossType_returnsRewardButNoMapChanges() {
        val result = runner.finishBoss(
            bossType = null,
            bossProgress = 60,
            bossTotal = 100,
            selectedLessonId = "lesson-A",
            currentLessonRewards = emptyMap(),
            currentMegaRewards = emptyMap()
        )
        assertEquals(BossReward.SILVER, result.reward)
        assertTrue(result.updatedLessonRewards.isEmpty())
        assertTrue(result.updatedMegaRewards.isEmpty())
    }

    @Test
    fun finishBoss_upgradesExistingReward_forSameLesson() {
        val existing = mapOf("lesson-A" to BossReward.BRONZE)
        val result = runner.finishBoss(
            bossType = BossType.LESSON,
            bossProgress = 90,
            bossTotal = 100,
            selectedLessonId = "lesson-A",
            currentLessonRewards = existing,
            currentMegaRewards = emptyMap()
        )
        assertEquals(BossReward.GOLD, result.reward)
        assertEquals(
            mapOf("lesson-A" to BossReward.GOLD),
            result.updatedLessonRewards
        )
    }

    @Test
    fun finishBoss_totalZero_returnsNullRewardAndNoChanges() {
        val result = runner.finishBoss(
            bossType = BossType.LESSON,
            bossProgress = 5,
            bossTotal = 0,
            selectedLessonId = "lesson-A",
            currentLessonRewards = emptyMap(),
            currentMegaRewards = emptyMap()
        )
        assertNull(result.reward)
        assertTrue(result.updatedLessonRewards.isEmpty())
        assertTrue(result.updatedMegaRewards.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // clearBossRewardMessage
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun clearBossRewardMessage_bossActivePausedWithCardAndVoice_resumesTimerAndVoice() {
        val result = runner.clearBossRewardMessage(
            bossActive = true,
            sessionState = SessionState.PAUSED,
            currentCard = testCard,
            inputMode = InputMode.VOICE
        )
        assertTrue(result.shouldResumeTimer)
        assertTrue(result.shouldTriggerVoice)
    }

    @Test
    fun clearBossRewardMessage_bossActivePausedWithCardAndKeyboard_resumesTimerOnly() {
        val result = runner.clearBossRewardMessage(
            bossActive = true,
            sessionState = SessionState.PAUSED,
            currentCard = testCard,
            inputMode = InputMode.KEYBOARD
        )
        assertTrue(result.shouldResumeTimer)
        assertFalse(result.shouldTriggerVoice)
    }

    @Test
    fun clearBossRewardMessage_bossNotActive_doesNotResume() {
        val result = runner.clearBossRewardMessage(
            bossActive = false,
            sessionState = SessionState.PAUSED,
            currentCard = testCard,
            inputMode = InputMode.VOICE
        )
        assertFalse(result.shouldResumeTimer)
        assertFalse(result.shouldTriggerVoice)
    }

    @Test
    fun clearBossRewardMessage_sessionNotPaused_doesNotResume() {
        val result = runner.clearBossRewardMessage(
            bossActive = true,
            sessionState = SessionState.ACTIVE,
            currentCard = testCard,
            inputMode = InputMode.VOICE
        )
        assertFalse(result.shouldResumeTimer)
        assertFalse(result.shouldTriggerVoice)
    }

    @Test
    fun clearBossRewardMessage_nullCard_doesNotResume() {
        val result = runner.clearBossRewardMessage(
            bossActive = true,
            sessionState = SessionState.PAUSED,
            currentCard = null,
            inputMode = InputMode.VOICE
        )
        assertFalse(result.shouldResumeTimer)
        assertFalse(result.shouldTriggerVoice)
    }

    @Test
    fun clearBossRewardMessage_allConditionsMet_exceptVoiceMode_noVoiceTrigger() {
        val result = runner.clearBossRewardMessage(
            bossActive = true,
            sessionState = SessionState.PAUSED,
            currentCard = testCard,
            inputMode = InputMode.WORD_BANK
        )
        assertTrue(result.shouldResumeTimer)
        assertFalse(result.shouldTriggerVoice)
    }

    // ══════════════════════════════════════════════════════════════════
    // bossRewardMessage
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun bossRewardMessage_bronze_returnsBronzeReached() {
        assertEquals("Bronze reached", runner.bossRewardMessage(BossReward.BRONZE))
    }

    @Test
    fun bossRewardMessage_silver_returnsSilverReached() {
        assertEquals("Silver reached", runner.bossRewardMessage(BossReward.SILVER))
    }

    @Test
    fun bossRewardMessage_gold_returnsGoldReached() {
        assertEquals("Gold reached", runner.bossRewardMessage(BossReward.GOLD))
    }
}
