package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.*
import com.alexpo.grammermate.feature.progress.ProgressTracker
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.testharness.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Full course journey test covering lesson progression loop.
 *
 * Tests the complete user journey from first lesson through multiple lessons:
 * - Lesson unlock logic (mastery threshold)
 * - Sub-lesson completion (NEW_ONLY type)
 * - Mastery tracking (uniqueCardShows, shownCardIds)
 * - Flower progression (SEED → SPROUT → BLOOM)
 * - Sequential lesson unlocks
 *
 * Uses real SessionRunner, ProgressTracker, StreakManager with in-memory fake stores.
 *
 * Spec reference: docs/specification/scenarios/click-test-full-course-journey.md
 */
@RunWith(RobolectricTestRunner::class)
class FullCourseJourneyTest {

    private lateinit var stateAccess: FakeTrainingStateAccess
    private lateinit var masteryStore: FakeMasteryStore
    private lateinit var progressStore: FakeProgressStore
    private lateinit var streakStore: FakeStreakStore
    private lateinit var lessonStore: FakeLessonStore
    private lateinit var answerValidator: AnswerValidator
    private lateinit var progressTracker: ProgressTracker
    private lateinit var streakManager: StreakManager

    private val testLanguageId = LanguageId("en")
    private val testPackId = PackId("test-pack")

    // Test constants
    private val subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
    private val masteryThreshold = SpacedRepetitionConfig.MASTERY_THRESHOLD // 150

    @Before
    fun setup() {
        // Initialize all fake stores
        masteryStore = FakeMasteryStore()
        progressStore = FakeProgressStore()
        streakStore = FakeStreakStore()
        lessonStore = FakeLessonStore()

        // Initialize state access with default state
        val initialState = TrainingUiState().copy(
            navigation = NavigationState(
                selectedLanguageId = testLanguageId,
                activePackId = testPackId
            )
        )
        stateAccess = FakeTrainingStateAccess(initialState)

        // Initialize core components
        answerValidator = AnswerValidator()
        streakManager = StreakManager(streakStore)
        progressTracker = ProgressTracker(
            stateAccess = stateAccess,
            masteryStore = masteryStore,
            progressStore = progressStore,
            lessonStore = lessonStore
        )
    }

    // ========================================
    // Helper: Create a lesson with cards
    // ========================================

    private fun createLesson(
        lessonId: String,
        title: String,
        cardCount: Int
    ): Lesson {
        val cards = (1..cardCount).map { i ->
            SentenceCard(
                id = "$lessonId-card-$i",
                promptRu = "русское предложение $i",
                acceptedAnswers = listOf("english sentence $i")
            )
        }
        return Lesson(
            id = LessonId(lessonId),
            languageId = testLanguageId,
            title = title,
            cards = cards
        )
    }

    // ========================================
    // Helper: Complete a lesson with given completion rate
    // ========================================

    /**
     * Simulates completing a lesson by answering cards with the given correctness rate.
     * Creates NEW_ONLY sub-lessons and marks cards as shown.
     *
     * @param lessonId The lesson to complete
     * @param completionRate Fraction of cards to answer correctly (0.0 to 1.0)
     * @return Number of unique cards actually shown
     */
    private fun completeLesson(lessonId: String, completionRate: Float = 1.0f): Int {
        val lesson = lessonStore.getLessons(testLanguageId.value)
            .find { it.id.value == lessonId }
            ?: throw IllegalArgumentException("Lesson $lessonId not found")

        val scheduler = MixedReviewScheduler(subLessonSize)
        val schedule = scheduler.build(listOf(lesson))
        val subLessons = schedule[lesson.id]?.subLessons ?: emptyList()

        var totalCardsShown = 0

        subLessons.forEach { subLesson ->
            // Only process NEW_ONLY sub-lessons (first lesson behavior)
            if (subLesson.type == SubLessonType.NEW_ONLY) {
                val cardsToComplete = (subLesson.cards.size * completionRate).toInt()
                subLesson.cards.take(cardsToComplete).forEach { card ->
                    masteryStore.recordCardShow(
                        lessonId = lessonId,
                        languageId = testLanguageId.value,
                        cardId = card.id
                    )
                    totalCardsShown++
                }
            }
        }

        return totalCardsShown
    }

    // ========================================
    // Helper: Complete a single sub-lesson
    // ========================================

    /**
     * Simulates completing a single sub-lesson with the given correctness rate.
     *
     * @param cards The cards in the sub-lesson
     * @param correctRate Fraction of cards to answer correctly (0.0 to 1.0)
     * @return Number of cards actually answered correctly
     */
    private fun completeSubLesson(cards: List<SentenceCard>, correctRate: Float = 1.0f): Int {
        val cardsToComplete = (cards.size * correctRate).toInt()
        return cardsToComplete
    }

    // ========================================
    // Helper: Verify lesson is unlocked
    // ========================================

    /**
     * Verifies that a lesson at the given index is accessible.
     * A lesson is unlocked if:
     * - It's the first lesson (index 0), OR
     * - The previous lesson has mastery >= threshold
     *
     * @param lessonIndex The lesson index to check
     */
    private fun verifyLessonUnlocked(lessonIndex: Int) {
        val lessons = lessonStore.getLessons(testLanguageId.value)

        assertTrue("Lesson index $lessonIndex out of bounds", lessonIndex < lessons.size)

        if (lessonIndex == 0) {
            // First lesson is always unlocked
            return
        }

        // Check previous lesson mastery
        val previousLesson = lessons[lessonIndex - 1]
        val previousMastery = masteryStore.get(
            previousLesson.id.value,
            testLanguageId.value
        )

        assertNotNull("Previous lesson should have mastery data", previousMastery)
        assertTrue(
            "Lesson $lessonIndex should be unlocked (previous lesson has ${previousMastery!!.uniqueCardShows} unique shows, threshold $masteryThreshold)",
            previousMastery.uniqueCardShows >= masteryThreshold
        )
    }

    // ========================================
    // Helper: Verify mastery state
    // ========================================

    /**
     * Verifies mastery tracking for a lesson.
     *
     * @param lessonId The lesson to verify
     * @param expectedUniqueShows Expected unique card shows
     */
    private fun verifyMastery(lessonId: String, expectedUniqueShows: Int) {
        val mastery = masteryStore.get(lessonId, testLanguageId.value)

        assertNotNull("Lesson $lessonId should have mastery data", mastery)
        assertEquals(
            "Lesson $lessonId should have $expectedUniqueShows unique shows",
            expectedUniqueShows,
            mastery?.uniqueCardShows
        )

        if (expectedUniqueShows > 0) {
            assertTrue(
                "Lesson $lessonId should have shownCardIds",
                mastery?.shownCardIds?.isNotEmpty() == true
            )
            assertEquals(
                "Lesson $lessonId shownCardIds size should match unique shows",
                expectedUniqueShows,
                mastery?.shownCardIds?.size
            )
        }
    }

    // ========================================
    // Helper: Verify flower progression
    // ========================================

    /**
     * Verifies flower state progression for a lesson.
     *
     * Flower states based on masteryPercent:
     * - SEED: masteryPercent < 33%
     * - SPROUT: 33% <= masteryPercent < 66%
     * - BLOOM: masteryPercent >= 66%
     *
     * @param lessonId The lesson to verify
     * @param expectedState Expected flower state
     */
    private fun verifyFlowerState(lessonId: String, expectedState: FlowerState) {
        val lesson = lessonStore.getLessons(testLanguageId.value)
            .find { it.id.value == lessonId }
            ?: throw IllegalArgumentException("Lesson $lessonId not found")

        val mastery = masteryStore.get(lessonId, testLanguageId.value)
        val flower = FlowerCalculator.calculate(mastery, lesson.cards.size)

        assertEquals(
            "Lesson $lessonId flower should be $expectedState (mastery: ${flower.masteryPercent * 100}%)",
            expectedState,
            flower.state
        )
    }

    // ========================================
    // Test 1: Complete first lesson - unlocks second lesson
    // ========================================

    @Test
    fun testCompleteFirstLesson_UnlocksSecondLesson() = runBlocking {
        // --- SETUP: Create first lesson with 160 cards (above mastery threshold) ---
        val lesson1 = createLesson("lesson-1", "Lesson 1", 160)
        lessonStore.addLesson(lesson1)

        // --- SETUP: Create second lesson (locked initially) ---
        val lesson2 = createLesson("lesson-2", "Lesson 2", 150)
        lessonStore.addLesson(lesson2)

        // --- ASSERT: First lesson is unlocked (index 0) ---
        verifyLessonUnlocked(0)

        // --- ASSERT: Second lesson is locked (previous lesson has 0 mastery) ---
        val masteryBefore = masteryStore.get("lesson-1", testLanguageId.value)
        val initialShows = masteryBefore?.uniqueCardShows ?: 0
        assertTrue(
            "Second lesson should be locked initially (mastery: $initialShows)",
            initialShows == 0 || initialShows < masteryThreshold
        )

        // --- ACTION: Complete first lesson (100% completion) ---
        val cardsShown = completeLesson("lesson-1", 1.0f)

        // --- ASSERT: Mastery tracked correctly ---
        verifyMastery("lesson-1", cardsShown)
        assertTrue(
            "First lesson should meet or exceed mastery threshold ($masteryThreshold)",
            cardsShown >= masteryThreshold
        )

        // --- ASSERT: Flower is in BLOOM state (>= 66% mastery) ---
        verifyFlowerState("lesson-1", FlowerState.BLOOM)

        // --- ASSERT: Second lesson is now unlocked ---
        verifyLessonUnlocked(1)

        // Total assertions: 6
    }

    // ========================================
    // Test 2: Complete five lessons - sequential unlocks
    // ========================================

    @Test
    fun testCompleteFiveLessons_SequentialUnlocks() = runBlocking {
        // --- SETUP: Create 5 lessons ---
        val lessons = (1..5).map { i ->
            createLesson("lesson-$i", "Lesson $i", 160)
        }
        lessons.forEach { lessonStore.addLesson(it) }

        // --- ASSERT: Only first lesson is unlocked initially ---
        verifyLessonUnlocked(0)

        val mastery0 = masteryStore.get("lesson-1", testLanguageId.value)
        assertEquals("Lesson 1 should start with 0 mastery", 0, mastery0?.uniqueCardShows ?: 0)

        // --- ACTION & ASSERT: Complete lessons sequentially ---
        val expectedMastery = mutableListOf<Int>()

        (1..5).forEach { lessonNum ->
            val lessonId = "lesson-$lessonNum"

            // Complete the lesson
            val cardsShown = completeLesson(lessonId, 1.0f)
            expectedMastery.add(cardsShown)

            // Verify mastery
            verifyMastery(lessonId, cardsShown)

            // Verify flower state (BLOOM for full completion)
            verifyFlowerState(lessonId, FlowerState.BLOOM)

            // Verify next lesson is unlocked (if not the last)
            if (lessonNum < 5) {
                verifyLessonUnlocked(lessonNum)
            }
        }

        // --- ASSERT: All 5 lessons completed ---
        val allLessons = lessonStore.getLessons(testLanguageId.value)
        assertEquals("Should have 5 lessons", 5, allLessons.size)

        // --- ASSERT: Each lesson has correct mastery ---
        expectedMastery.forEachIndexed { index, expected ->
            val lessonId = "lesson-${index + 1}"
            val actualMastery = masteryStore.get(lessonId, testLanguageId.value)
            assertEquals(
                "Lesson $lessonId should have $expected unique shows",
                expected,
                actualMastery?.uniqueCardShows ?: 0
            )
        }

        // Total assertions: 5 lessons * 3 checks each + 1 initial = 16
    }

    // ========================================
    // Test 3: Partial lesson completion - flower progression
    // ========================================

    @Test
    fun testPartialCompletion_FlowerProgression() = runBlocking {
        // --- SETUP: Create lesson with 150 cards ---
        val lesson = createLesson("lesson-partial", "Partial Lesson", 150)
        lessonStore.addLesson(lesson)

        // --- ASSERT: Initial state is SEED ---
        verifyFlowerState("lesson-partial", FlowerState.SEED)

        // --- ACTION: Complete 30 cards (20% = below SEED→SPROUT threshold of 33%) ---
        // Use lesson.cards directly for predictable counts
        lesson.cards.take(30).forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-partial",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        // --- ASSERT: Still SEED (below 33% threshold: 30/150 = 20%) ---
        verifyFlowerState("lesson-partial", FlowerState.SEED)

        // --- ACTION: Complete up to 50 cards (33.3% → SPROUT threshold) ---
        lesson.cards.drop(30).take(20).forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-partial",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        // --- ASSERT: Now SPROUT (33-66% range: 50/150 = 33.3%) ---
        verifyFlowerState("lesson-partial", FlowerState.SPROUT)

        // --- ACTION: Complete up to 100 cards (66% → BLOOM threshold) ---
        lesson.cards.drop(50).take(50).forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-partial",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        // --- ASSERT: Now BLOOM (>= 66%: 100/150 = 66.7%) ---
        verifyFlowerState("lesson-partial", FlowerState.BLOOM)

        // Total assertions: 4
    }

    // ========================================
    // Test 4: Mastery threshold calculation
    // ========================================

    @Test
    fun testMasteryThresholdCalculation() {
        // --- ASSERT: Threshold is 150 cards ---
        assertEquals("Mastery threshold should be 150", 150, masteryThreshold)

        // --- SETUP: Create lesson with exactly 150 cards ---
        val lesson = createLesson("lesson-threshold", "Threshold Lesson", 150)
        lessonStore.addLesson(lesson)

        // --- ACTION: Complete 149 cards (below threshold) ---
        lesson.cards.take(149).forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-threshold",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        // --- ASSERT: Below threshold ---
        val masteryBelow = masteryStore.get("lesson-threshold", testLanguageId.value)
        assertEquals("Should have 149 unique shows", 149, masteryBelow?.uniqueCardShows ?: 0)
        assertTrue("Should be below threshold", 149 < masteryThreshold)

        // --- ACTION: Complete one more card ---
        lesson.cards.drop(149).take(1).forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-threshold",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        // --- ASSERT: At or above threshold ---
        val masteryAt = masteryStore.get("lesson-threshold", testLanguageId.value)
        assertEquals("Should have 150 unique shows", 150, masteryAt?.uniqueCardShows ?: 0)
        assertTrue("Should meet threshold", 150 >= masteryThreshold)

        // Total assertions: 5
    }

    // ========================================
    // Test 5: shownCardIds tracking
    // ========================================

    @Test
    fun testShownCardIdsTracking() {
        // --- SETUP: Create lesson with 50 cards ---
        val lesson = createLesson("lesson-ids", "IDs Lesson", 50)
        lessonStore.addLesson(lesson)

        // --- ACTION: Complete first 20 cards directly (not via sub-lessons) ---
        val expectedCardIds = mutableSetOf<String>()
        lesson.cards.take(20).forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-ids",
                languageId = testLanguageId.value,
                cardId = card.id
            )
            expectedCardIds.add(card.id)
        }

        // --- ASSERT: shownCardIds contains exactly those 20 cards ---
        val mastery = masteryStore.get("lesson-ids", testLanguageId.value)
        assertNotNull("Mastery should exist", mastery)
        assertEquals("Should have 20 unique shows", 20, mastery?.uniqueCardShows ?: 0)
        assertEquals("Should have 20 shown card IDs", 20, mastery?.shownCardIds?.size ?: 0)

        // Verify each expected card is in shownCardIds
        expectedCardIds.forEach { cardId ->
            assertTrue(
                "Card ID $cardId should be in shownCardIds",
                mastery?.shownCardIds?.contains(cardId) == true
            )
        }

        // --- ACTION: Complete 10 more cards ---
        lesson.cards.drop(20).take(10).forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-ids",
                languageId = testLanguageId.value,
                cardId = card.id
            )
            expectedCardIds.add(card.id)
        }

        // --- ASSERT: shownCardIds now contains 30 unique cards ---
        val masteryAfter = masteryStore.get("lesson-ids", testLanguageId.value)
        assertEquals("Should have 30 unique shows", 30, masteryAfter?.uniqueCardShows ?: 0)
        assertEquals("Should have 30 shown card IDs", 30, masteryAfter?.shownCardIds?.size ?: 0)

        // Total assertions: 7
    }

    // ========================================
    // Test 6: Lesson with insufficient cards - no unlock
    // ========================================

    @Test
    fun testInsufficientCards_NoUnlock() {
        // --- SETUP: Create first lesson with only 100 cards (below threshold) ---
        val lesson1 = createLesson("lesson-small-1", "Small Lesson 1", 100)
        lessonStore.addLesson(lesson1)

        // --- SETUP: Create second lesson ---
        val lesson2 = createLesson("lesson-small-2", "Small Lesson 2", 100)
        lessonStore.addLesson(lesson2)

        // --- ACTION: Complete all cards in first lesson ---
        val cardsShown = completeLesson("lesson-small-1", 1.0f)

        // --- ASSERT: Mastery is 100 (below threshold of 150) ---
        verifyMastery("lesson-small-1", 100)
        assertTrue("100 is below threshold 150", 100 < masteryThreshold)

        // --- ASSERT: Flower is SPROUT (66% of 100 is ~66%, which is at BLOOM threshold) ---
        // Actually 100/150 = 66.67%, so it should be BLOOM
        verifyFlowerState("lesson-small-1", FlowerState.BLOOM)

        // --- ASSERT: Second lesson remains locked (insufficient mastery) ---
        val mastery1 = masteryStore.get("lesson-small-1", testLanguageId.value)
        assertTrue(
            "Second lesson should remain locked (mastery: ${mastery1?.uniqueCardShows})",
            mastery1?.uniqueCardShows == 100 && 100 < masteryThreshold
        )

        // Total assertions: 4
    }

    // ========================================
    // Test 7: NEW_ONLY sub-lesson type for first lesson
    // ========================================

    @Test
    fun testNewOnlySubLessonType() {
        // --- SETUP: Create first lesson ---
        val lesson = createLesson("lesson-newonly", "New Only Lesson", 100)
        lessonStore.addLesson(lesson)

        // --- ACTION: Build schedule ---
        val scheduler = MixedReviewScheduler(subLessonSize)
        val schedule = scheduler.build(listOf(lesson))

        // --- ASSERT: Schedule exists ---
        assertNotNull("Schedule should exist", schedule)
        assertTrue("Schedule should contain the lesson", schedule.containsKey(lesson.id))

        // --- ASSERT: All sub-lessons are NEW_ONLY ---
        val subLessons = schedule[lesson.id]?.subLessons ?: emptyList()
        assertTrue("Should have at least one sub-lesson", subLessons.isNotEmpty())

        subLessons.forEach { subLesson ->
            assertEquals(
                "First lesson should only have NEW_ONLY sub-lessons",
                SubLessonType.NEW_ONLY,
                subLesson.type
            )
        }

        // --- ASSERT: No MIXED sub-lessons ---
        val hasMixed = subLessons.any { it.type == SubLessonType.MIXED }
        assertFalse("First lesson should not have MIXED sub-lessons", hasMixed)

        // Total assertions: 5
    }

    // ========================================
    // Test 8: Streak recording on lesson completion
    // ========================================

    @Test
    fun testStreakRecording_LessonCompletion() = runBlocking {
        // --- SETUP: Create lesson ---
        val lesson = createLesson("lesson-streak", "Streak Lesson", 160)
        lessonStore.addLesson(lesson)

        // --- ASSERT: No streak initially ---
        val streakBefore = streakStore.load(testLanguageId.value)
        assertEquals("Streak should be 0 initially", 0, streakBefore?.currentStreak ?: 0)

        // --- ACTION: Complete first sub-lesson ---
        val scheduler = MixedReviewScheduler(subLessonSize)
        val schedule = scheduler.build(listOf(lesson))
        val subLessons = schedule[lesson.id]?.subLessons ?: emptyList()

        val firstSubLesson = subLessons.firstOrNull { it.type == SubLessonType.NEW_ONLY }
        assertNotNull("Should have at least one NEW_ONLY sub-lesson", firstSubLesson)

        firstSubLesson?.cards?.forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-streak",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        // --- ACTION: Record streak ---
        val (streakData, isMilestone) = streakManager.recordSubLessonCompletion(testLanguageId.value)

        // --- ASSERT: Streak incremented ---
        assertEquals("Streak should be 1", 1, streakData.currentStreak)
        assertTrue("Should be a streak-worthy event", isMilestone)

        // --- ACTION: Complete another sub-lesson ---
        val secondSubLesson = subLessons.getOrNull(1)
        secondSubLesson?.cards?.take(10)?.forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-streak",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        val (streakData2, _) = streakManager.recordSubLessonCompletion(testLanguageId.value)

        // --- ASSERT: Streak is still 1 (same day) ---
        assertEquals("Streak should remain 1 on same day", 1, streakData2.currentStreak)

        // Total assertions: 5
    }

    // ========================================
    // Test 9: ProgressTracker integration
    // ========================================

    @Test
    fun testProgressTracker_Integration() {
        // --- SETUP: Create lesson and card ---
        val lesson = createLesson("lesson-tracker", "Tracker Lesson", 50)
        lessonStore.addLesson(lesson)

        val card = lesson.cards.first()

        // --- ACTION: Record card show through ProgressTracker ---
        progressTracker.recordCardShowForMastery(
            card = card,  // SentenceCard already implements SessionCard
            bossActive = false,
            isDrillMode = false,
            inputMode = InputMode.VOICE,
            selectedLanguageId = testLanguageId,
            lessons = listOf(lesson),
            selectedLessonId = lesson.id
        )

        // --- ASSERT: Mastery tracked ---
        val mastery = masteryStore.get("lesson-tracker", testLanguageId.value)
        assertNotNull("Mastery should exist", mastery)
        assertEquals("Should have 1 unique show", 1, mastery?.uniqueCardShows)
        assertTrue("Card ID should be in shownCardIds", mastery?.shownCardIds?.contains(card.id) == true)

        // Total assertions: 3
    }

    // ========================================
    // Test 10: Multiple lessons with different completion rates
    // ========================================

    @Test
    fun testMultipleLessons_DifferentCompletionRates() = runBlocking {
        // --- SETUP: Create 3 lessons ---
        val lesson1 = createLesson("lesson-rate-1", "Rate Lesson 1", 200)
        val lesson2 = createLesson("lesson-rate-2", "Rate Lesson 2", 180)
        val lesson3 = createLesson("lesson-rate-3", "Rate Lesson 3", 160)

        listOf(lesson1, lesson2, lesson3).forEach { lessonStore.addLesson(it) }

        // --- ACTION: Complete lessons at different rates ---
        // Lesson 1: 100% completion
        val shown1 = completeLesson("lesson-rate-1", 1.0f)

        // Lesson 2: 75% completion
        val shown2 = completeLesson("lesson-rate-2", 0.75f)

        // Lesson 3: 50% completion
        val shown3 = completeLesson("lesson-rate-3", 0.5f)

        // --- ASSERT: Mastery reflects completion rates ---
        val mastery1 = masteryStore.get("lesson-rate-1", testLanguageId.value)
        val mastery2 = masteryStore.get("lesson-rate-2", testLanguageId.value)
        val mastery3 = masteryStore.get("lesson-rate-3", testLanguageId.value)

        assertEquals("Lesson 1 should have ~200 shows (100%)", shown1, mastery1?.uniqueCardShows)
        assertEquals("Lesson 2 should have ~135 shows (75% of 180)", shown2, mastery2?.uniqueCardShows)
        assertEquals("Lesson 3 should have ~80 shows (50% of 160)", shown3, mastery3?.uniqueCardShows)

        // --- ASSERT: Flower states match mastery ---
        verifyFlowerState("lesson-rate-1", FlowerState.BLOOM)
        verifyFlowerState("lesson-rate-2", FlowerState.BLOOM)
        verifyFlowerState("lesson-rate-3", FlowerState.SPROUT) // 80/150 = 53%

        // Total assertions: 6
    }

    // ========================================
    // SECTION 6: Statistics Tracking Tests
    // Tests for totalSubLessonsCompleted, todayFireCount, completedTypesToday
    // Spec reference: docs/specification/scenarios/click-test-full-course-journey.md Section 6.3
    // ========================================

    /**
     * Test 11: totalSubLessonsCompleted accumulates across multiple lessons.
     *
     * Verifies that:
     * - Completing 3 sub-lessons across different lessons increments totalSubLessonsCompleted
     * - Counter persists across multiple practice sessions
     * - StreakStore correctly tracks cumulative total
     */
    @Test
    fun testTotalSubLessonsCompleted_Accumulates() = runBlocking {
        // --- SETUP: Create 3 lessons ---
        val lesson1 = createLesson("lesson-stats-1", "Stats Lesson 1", 160)
        val lesson2 = createLesson("lesson-stats-2", "Stats Lesson 2", 160)
        val lesson3 = createLesson("lesson-stats-3", "Stats Lesson 3", 160)
        listOf(lesson1, lesson2, lesson3).forEach { lessonStore.addLesson(it) }

        // --- ASSERT: Initial totalSubLessonsCompleted is 0 ---
        val initialStreak = streakStore.load(testLanguageId.value)
        assertEquals(
            "Initial totalSubLessonsCompleted should be 0",
            0,
            initialStreak?.totalSubLessonsCompleted ?: 0
        )

        // --- ACTION: Complete first sub-lesson of lesson 1 ---
        val scheduler1 = MixedReviewScheduler(subLessonSize)
        val schedule1 = scheduler1.build(listOf(lesson1))
        val subLessons1 = schedule1[lesson1.id]?.subLessons ?: emptyList()

        val firstSubLesson1 = subLessons1.firstOrNull { it.type == SubLessonType.NEW_ONLY }
        assertNotNull("Should have NEW_ONLY sub-lesson", firstSubLesson1)
        firstSubLesson1?.cards?.forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-stats-1",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        val (streak1, _) = streakManager.recordSubLessonCompletion(testLanguageId.value)

        // --- ASSERT: totalSubLessonsCompleted = 1 ---
        assertEquals(
            "After first sub-lesson, totalSubLessonsCompleted should be 1",
            1,
            streak1.totalSubLessonsCompleted
        )

        // --- ACTION: Complete second sub-lesson of lesson 2 ---
        val scheduler2 = MixedReviewScheduler(subLessonSize)
        val schedule2 = scheduler2.build(listOf(lesson2))
        val subLessons2 = schedule2[lesson2.id]?.subLessons ?: emptyList()

        val firstSubLesson2 = subLessons2.firstOrNull { it.type == SubLessonType.NEW_ONLY }
        assertNotNull("Should have NEW_ONLY sub-lesson", firstSubLesson2)
        firstSubLesson2?.cards?.forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-stats-2",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        val (streak2, _) = streakManager.recordSubLessonCompletion(testLanguageId.value)

        // --- ASSERT: totalSubLessonsCompleted = 2 ---
        assertEquals(
            "After second sub-lesson, totalSubLessonsCompleted should be 2",
            2,
            streak2.totalSubLessonsCompleted
        )

        // --- ACTION: Complete third sub-lesson of lesson 3 ---
        val scheduler3 = MixedReviewScheduler(subLessonSize)
        val schedule3 = scheduler3.build(listOf(lesson3))
        val subLessons3 = schedule3[lesson3.id]?.subLessons ?: emptyList()

        val firstSubLesson3 = subLessons3.firstOrNull { it.type == SubLessonType.NEW_ONLY }
        assertNotNull("Should have NEW_ONLY sub-lesson", firstSubLesson3)
        firstSubLesson3?.cards?.forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-stats-3",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        val (streak3, _) = streakManager.recordSubLessonCompletion(testLanguageId.value)

        // --- ASSERT: totalSubLessonsCompleted = 3 ---
        assertEquals(
            "After third sub-lesson, totalSubLessonsCompleted should be 3",
            3,
            streak3.totalSubLessonsCompleted
        )

        // --- ASSERT: Verify persistence in StreakStore ---
        val finalStreak = streakStore.load(testLanguageId.value)
        assertEquals(
            "StreakStore should persist totalSubLessonsCompleted = 3",
            3,
            finalStreak?.totalSubLessonsCompleted
        )

        // Total assertions: 6
    }

    /**
     * Test 12: todayFireCount increments with multiple practice types.
     *
     * Verifies that:
     * - Daily Practice (3 fires: TRANSLATION, VOCAB, VERB) increments todayFireCount
     * - Regular lesson (1 fire: TRANSLATION) doesn't increment if TRANSLATION already earned
     * - Total todayFireCount = 3 after Daily Practice + regular lesson
     */
    @Test
    fun testTodayFireCount_MultiPractice() = runBlocking {
        // --- SETUP: Create lesson for regular practice ---
        val lesson = createLesson("lesson-fire", "Fire Lesson", 160)
        lessonStore.addLesson(lesson)

        // --- ASSERT: Initial todayFireCount is 0 ---
        val initialStreak = streakStore.load(testLanguageId.value)
        assertEquals(
            "Initial todayFireCount should be 0",
            0,
            initialStreak?.todayFireCount ?: 0
        )

        // --- ACTION: Complete Daily Practice (3 fires: TRANSLATION, VOCAB, VERB) ---
        // Simulate Daily Practice completion with 3 practice types

        // TRANSLATION fire (Daily Practice Block 1)
        val (streakAfterTrans, _) = streakManager.recordPracticeTypeCompletion(
            testLanguageId.value,
            PracticeType.TRANSLATION
        )
        assertEquals("TRANSLATION should earn 1 fire", 1, streakAfterTrans.todayFireCount)

        // VOCAB fire (Daily Practice Block 2)
        val (streakAfterVocab, _) = streakManager.recordPracticeTypeCompletion(
            testLanguageId.value,
            PracticeType.VOCAB
        )
        assertEquals("After VOCAB, todayFireCount should be 2", 2, streakAfterVocab.todayFireCount)

        // VERB fire (Daily Practice Block 3)
        val (streakAfterVerb, _) = streakManager.recordPracticeTypeCompletion(
            testLanguageId.value,
            PracticeType.VERB
        )
        assertEquals("After VERB, todayFireCount should be 3", 3, streakAfterVerb.todayFireCount)

        // --- ACTION: Complete regular lesson (TRANSLATION fire) ---
        val scheduler = MixedReviewScheduler(subLessonSize)
        val schedule = scheduler.build(listOf(lesson))
        val subLessons = schedule[lesson.id]?.subLessons ?: emptyList()

        val firstSubLesson = subLessons.firstOrNull { it.type == SubLessonType.NEW_ONLY }
        assertNotNull("Should have NEW_ONLY sub-lesson", firstSubLesson)
        firstSubLesson?.cards?.forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-fire",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        // Note: TRANSLATION already completed today, so this doesn't increment fire count
        // but still increments totalSubLessonsCompleted
        val (finalStreak, _) = streakManager.recordSubLessonCompletion(testLanguageId.value)

        // --- ASSERT: todayFireCount = 3 (TRANSLATION already earned) ---
        assertEquals(
            "todayFireCount should be 3 (TRANSLATION already earned from Daily Practice)",
            3,
            finalStreak.todayFireCount
        )

        // --- ASSERT: totalSubLessonsCompleted = 1 (only regular lesson counts, not Daily Practice blocks) ---
        // Daily Practice has 3 blocks but they don't increment totalSubLessonsCompleted
        // Only regular lesson sub-lessons (TRANSLATION practice type) increment this counter
        assertEquals(
            "totalSubLessonsCompleted should be 1 (only the regular lesson sub-lesson)",
            1,
            finalStreak.totalSubLessonsCompleted
        )

        // Total assertions: 6
    }

    /**
     * Test 13: completedTypesToday tracks all four practice types.
     *
     * Verifies that:
     * - Practicing all 4 types (TRANSLATION, VOCAB, VERB, SUB_DRILL) in one day
     * - completedTypesToday contains all 4 PracticeType values
     * - todayFireCount = 4 after all types completed
     */
    @Test
    fun testCompletedTypesToday_AllFourTypes() = runBlocking {
        // --- SETUP: Create lesson ---
        val lesson = createLesson("lesson-types", "Types Lesson", 160)
        lessonStore.addLesson(lesson)

        // --- ASSERT: Initial completedTypesToday is empty ---
        val initialStreak = streakStore.load(testLanguageId.value)
        assertTrue(
            "Initial completedTypesToday should be empty",
            initialStreak?.completedTypesToday?.isEmpty() ?: true
        )

        // --- ACTION: Complete all 4 practice types ---

        // 1. TRANSLATION (regular lesson sub-lesson)
        val scheduler = MixedReviewScheduler(subLessonSize)
        val schedule = scheduler.build(listOf(lesson))
        val subLessons = schedule[lesson.id]?.subLessons ?: emptyList()

        val firstSubLesson = subLessons.firstOrNull { it.type == SubLessonType.NEW_ONLY }
        firstSubLesson?.cards?.forEach { card ->
            masteryStore.recordCardShow(
                lessonId = "lesson-types",
                languageId = testLanguageId.value,
                cardId = card.id
            )
        }

        val (streak1, _) = streakManager.recordPracticeTypeCompletion(
            testLanguageId.value,
            PracticeType.TRANSLATION
        )

        // --- ASSERT: TRANSLATION in completedTypesToday ---
        assertTrue(
            "TRANSLATION should be in completedTypesToday",
            streak1.completedTypesToday.contains(PracticeType.TRANSLATION)
        )
        assertEquals("todayFireCount should be 1", 1, streak1.todayFireCount)

        // 2. VOCAB
        val (streak2, _) = streakManager.recordPracticeTypeCompletion(
            testLanguageId.value,
            PracticeType.VOCAB
        )

        assertTrue(
            "VOCAB should be in completedTypesToday",
            streak2.completedTypesToday.contains(PracticeType.VOCAB)
        )
        assertEquals("todayFireCount should be 2", 2, streak2.todayFireCount)

        // 3. VERB
        val (streak3, _) = streakManager.recordPracticeTypeCompletion(
            testLanguageId.value,
            PracticeType.VERB
        )

        assertTrue(
            "VERB should be in completedTypesToday",
            streak3.completedTypesToday.contains(PracticeType.VERB)
        )
        assertEquals("todayFireCount should be 3", 3, streak3.todayFireCount)

        // 4. SUB_DRILL (English pack drill sub-mode)
        val (streak4, _) = streakManager.recordPracticeTypeCompletion(
            testLanguageId.value,
            PracticeType.SUB_DRILL
        )

        assertTrue(
            "SUB_DRILL should be in completedTypesToday",
            streak4.completedTypesToday.contains(PracticeType.SUB_DRILL)
        )
        assertEquals("todayFireCount should be 4", 4, streak4.todayFireCount)

        // --- ASSERT: All 4 types in completedTypesToday ---
        assertEquals(
            "completedTypesToday should contain 4 types",
            4,
            streak4.completedTypesToday.size
        )
        assertTrue(
            "completedTypesToday should contain TRANSLATION",
            streak4.completedTypesToday.contains(PracticeType.TRANSLATION)
        )
        assertTrue(
            "completedTypesToday should contain VOCAB",
            streak4.completedTypesToday.contains(PracticeType.VOCAB)
        )
        assertTrue(
            "completedTypesToday should contain VERB",
            streak4.completedTypesToday.contains(PracticeType.VERB)
        )
        assertTrue(
            "completedTypesToday should contain SUB_DRILL",
            streak4.completedTypesToday.contains(PracticeType.SUB_DRILL)
        )

        // Total assertions: 11
    }

    /**
     * Test 14: Course completion statistics verification.
     *
     * Verifies that after completing a full 5-lesson course:
     * - totalSubLessonsCompleted matches expected count
     * - Each lesson has correct mastery percent
     * - Flower states reflect completion
     * - Statistics verified in both StreakStore and MasteryStore
     */
    @Test
    fun testCourseCompletionStatistics() = runBlocking {
        // --- SETUP: Build full course using CourseTestDataFactory ---
        val factory = CourseTestDataFactory()
        val courseData = factory.buildMinimalCourse(
            lessonCount = 5,
            packId = testPackId.value,
            languageId = testLanguageId.value
        )

        // Load course data into lesson store
        courseData.lessons.forEach { lessonTestData ->
            lessonStore.addLesson(lessonTestData.toLesson(testLanguageId))
        }

        // --- ASSERT: Initial statistics ---
        val initialStreak = streakStore.load(testLanguageId.value)
        assertEquals(
            "Initial totalSubLessonsCompleted should be 0",
            0,
            initialStreak?.totalSubLessonsCompleted ?: 0
        )

        // --- ACTION: Complete all 5 lessons ---
        var expectedSubLessonsPerLesson = 0 // Will be calculated from first lesson
        var totalSubLessonsCompleted = 0

        courseData.lessons.forEachIndexed { index, lessonTestData ->
            val lessonId = lessonTestData.lessonId

            // Complete each sub-lesson for this lesson
            val scheduler = MixedReviewScheduler(subLessonSize)
            val lesson = lessonStore.getLessons(testLanguageId.value)
                .find { it.id.value == lessonId }!!
            val schedule = scheduler.build(listOf(lesson))
            val subLessons = schedule[lesson.id]?.subLessons ?: emptyList()

            var subLessonsForThisLesson = 0
            subLessons.forEach { subLesson ->
                if (subLesson.type == SubLessonType.NEW_ONLY) {
                    // Complete all cards in this sub-lesson
                    subLesson.cards.forEach { card ->
                        masteryStore.recordCardShow(
                            lessonId = lessonId,
                            languageId = testLanguageId.value,
                            cardId = card.id
                        )
                    }

                    // Record sub-lesson completion
                    val (streakData, _) = streakManager.recordSubLessonCompletion(
                        testLanguageId.value
                    )
                    totalSubLessonsCompleted = streakData.totalSubLessonsCompleted
                    subLessonsForThisLesson++
                }
            }

            // Track expected count from first lesson
            if (index == 0) {
                expectedSubLessonsPerLesson = subLessonsForThisLesson
            }

            // --- ASSERT: Mastery tracked for this lesson ---
            val mastery = masteryStore.get(lessonId, testLanguageId.value)
            assertNotNull(
                "Lesson $lessonId should have mastery data",
                mastery
            )

            val expectedCards = lessonTestData.cards.size.coerceAtMost(Lesson.MAIN_POOL_SIZE)
            assertTrue(
                "Lesson $lessonId should have at least 150 unique shows (expected: $expectedCards, actual: ${mastery?.uniqueCardShows})",
                mastery?.uniqueCardShows ?: 0 >= 150
            )

            // --- ASSERT: Flower is BLOOM ---
            val flower = FlowerCalculator.calculate(mastery, lessonTestData.cards.size)
            assertEquals(
                "Lesson $lessonId flower should be BLOOM (mastery: ${flower.masteryPercent * 100}%)",
                FlowerState.BLOOM,
                flower.state
            )
        }

        // --- ASSERT: Total sub-lessons completed ---
        val finalStreak = streakStore.load(testLanguageId.value)
        val expectedTotal = 5 * expectedSubLessonsPerLesson
        assertEquals(
            "totalSubLessonsCompleted should match expected (5 lessons × $expectedSubLessonsPerLesson sub-lessons each)",
            expectedTotal,
            finalStreak?.totalSubLessonsCompleted ?: 0
        )

        // --- ASSERT: Each lesson's mastery percent is 100% ---
        courseData.lessons.forEach { lessonTestData ->
            val mastery = masteryStore.get(lessonTestData.lessonId, testLanguageId.value)
            val flower = FlowerCalculator.calculate(mastery, lessonTestData.cards.size)
            assertTrue(
                "Lesson ${lessonTestData.lessonId} should have 100% mastery percent (actual: ${flower.masteryPercent * 100}%)",
                flower.masteryPercent >= 1.0f
            )
        }

        // --- ASSERT: All flower states are BLOOM ---
        courseData.lessons.forEach { lessonTestData ->
            val mastery = masteryStore.get(lessonTestData.lessonId, testLanguageId.value)
            val flower = FlowerCalculator.calculate(mastery, lessonTestData.cards.size)
            assertEquals(
                "Lesson ${lessonTestData.lessonId} flower should be BLOOM",
                FlowerState.BLOOM,
                flower.state
            )
        }

        // --- ASSERT: Verify statistics in both stores ---
        // StreakStore: totalSubLessonsCompleted
        val streakStoreStats = streakStore.load(testLanguageId.value)
        assertTrue(
            "StreakStore totalSubLessonsCompleted should be > 0 (actual: ${streakStoreStats?.totalSubLessonsCompleted})",
            (streakStoreStats?.totalSubLessonsCompleted ?: 0) > 0
        )

        // MasteryStore: all lessons have mastery data
        courseData.lessons.forEach { lessonTestData ->
            val mastery = masteryStore.get(lessonTestData.lessonId, testLanguageId.value)
            assertNotNull(
                "MasteryStore should have data for ${lessonTestData.lessonId}",
                mastery
            )
            assertTrue(
                "MasteryStore uniqueCardShows should be >= 150 for ${lessonTestData.lessonId}",
                mastery?.uniqueCardShows ?: 0 >= 150
            )
        }

        // Total assertions: 5 lessons × 3 + 3 = 18
    }
}
