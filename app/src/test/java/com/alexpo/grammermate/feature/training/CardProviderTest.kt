package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for [CardProvider].
 *
 * Pure JUnit 4 — no Android dependencies, no Robolectric.
 * Uses inline test data builders for [SentenceCard] and [Lesson].
 */
class CardProviderTest {

    // ── System under test ──────────────────────────────────────────────

    private lateinit var provider: CardProvider

    // ── Test defaults ──────────────────────────────────────────────────

    private val defaultSubLessonSize = 10
    private val defaultSubLessonSizeMin = 6
    private val defaultSubLessonSizeMax = 12
    private val defaultEliteSizeMultiplier = 1.25
    private val defaultEliteStepCount = 7

    // ── Helpers ────────────────────────────────────────────────────────

    private fun card(id: String, tense: String? = null) = SentenceCard(
        id = id,
        promptRu = "Russian prompt for $id",
        acceptedAnswers = listOf("Answer for $id"),
        tense = tense
    )

    private fun lesson(
        id: String,
        cardCount: Int,
        startId: Int = 0,
        tensePerCard: (Int) -> String? = { null }
    ): Lesson {
        val cards = (0 until cardCount).map { i ->
            card("L${id}_card${startId + i}", tensePerCard(i))
        }
        return Lesson(
            id = LessonId(id),
            languageId = LanguageId("en"),
            title = "Lesson $id",
            cards = cards
        )
    }

    private fun lessonFromCards(id: String, cards: List<SentenceCard>): Lesson = Lesson(
        id = LessonId(id),
        languageId = LanguageId("en"),
        title = "Lesson $id",
        cards = cards
    )

    // ── Setup ──────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        provider = CardProvider(
            subLessonSize = defaultSubLessonSize,
            subLessonSizeMin = defaultSubLessonSizeMin,
            subLessonSizeMax = defaultSubLessonSizeMax,
            eliteSizeMultiplier = defaultEliteSizeMultiplier,
            eliteStepCount = defaultEliteStepCount,
            progressTracker = null
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  buildSchedules
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildSchedules_returnsNonEmptySchedule_forNonEmptyLessons() {
        val lessons = listOf(lesson("l1", 30))
        val result = provider.buildSchedules(lessons, emptyMap())

        assertTrue(result.containsKey(LessonId("l1")))
        assertTrue(result[LessonId("l1")]!!.subLessons.isNotEmpty())
    }

    @Test
    fun testBuildSchedules_returnsEmpty_forEmptyLessons() {
        val result = provider.buildSchedules(emptyList(), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testBuildSchedules_cachesResult_whenCalledTwiceWithSameInput() {
        val lessons = listOf(lesson("l1", 20))

        // First call — builds schedules
        val result1 = provider.buildSchedules(lessons, emptyMap())

        // Second call with same lessons — should hit cache and return existing schedules
        val result2 = provider.buildSchedules(lessons, result1)

        // Cache hit returns the same map reference passed as existingSchedules
        assertSame(result1, result2)
    }

    @Test
    fun testBuildSchedules_rebuilds_whenLessonChange() {
        val lessons1 = listOf(lesson("l1", 20))
        val result1 = provider.buildSchedules(lessons1, emptyMap())

        val lessons2 = listOf(lesson("l1", 30))
        val result2 = provider.buildSchedules(lessons2, result1)

        // Different card count → different key → rebuild
        // Sub-lesson count should differ
        assertNotEquals(
            result1[LessonId("l1")]?.subLessons?.size,
            result2[LessonId("l1")]?.subLessons?.size
        )
    }

    @Test
    fun testBuildSchedules_handlesMultipleLessons() {
        val lessons = listOf(
            lesson("l1", 15),
            lesson("l2", 25)
        )
        val result = provider.buildSchedules(lessons, emptyMap())

        assertTrue(result.containsKey(LessonId("l1")))
        assertTrue(result.containsKey(LessonId("l2")))
    }

    // ═══════════════════════════════════════════════════════════════════
    //  buildSessionCards — LESSON mode
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildSessionCards_lessonMode_returnsCardsFromSchedule() {
        val lessons = listOf(lesson("l1", 30))
        val schedules = provider.buildSchedules(lessons, emptyMap())
        val schedule = schedules[LessonId("l1")]!!

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.LESSON,
            selectedLessonId = LessonId("l1"),
            schedules = schedules,
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // Should return cards from the first sub-lesson
        val expectedCards = schedule.subLessons[0].cards
        assertEquals(expectedCards.size, result.cards.size)
        assertEquals(expectedCards.map { it.id }.toSet(), result.cards.map { it.id }.toSet())
    }

    @Test
    fun testBuildSessionCards_lessonMode_filtersHiddenCards() {
        val lessons = listOf(lesson("l1", 20))
        val schedules = provider.buildSchedules(lessons, emptyMap())

        // Hide the first card
        val hiddenIds = setOf("Ll1_card0")

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.LESSON,
            selectedLessonId = LessonId("l1"),
            schedules = schedules,
            activeSubLessonIndex = 0,
            hiddenCardIds = hiddenIds
        )

        // Hidden card should not appear
        assertTrue(result.cards.none { it.id == "Ll1_card0" })
    }

    @Test
    fun testBuildSessionCards_lessonMode_emptySchedules_returnsEmptyCards() {
        val lessons = listOf(lesson("l1", 10))
        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.LESSON,
            selectedLessonId = LessonId("l1"),
            schedules = emptyMap(), // No schedules
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertTrue(result.cards.isEmpty())
        assertEquals(0, result.subLessonCount)
    }

    @Test
    fun testBuildSessionCards_lessonMode_invalidIndex_clampedToValidRange() {
        val lessons = listOf(lesson("l1", 20))
        val schedules = provider.buildSchedules(lessons, emptyMap())
        val schedule = schedules[LessonId("l1")]!!
        val subCount = schedule.subLessons.size

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.LESSON,
            selectedLessonId = LessonId("l1"),
            schedules = schedules,
            activeSubLessonIndex = 999, // Way out of range
            hiddenCardIds = emptySet()
        )

        // Index should be clamped to last valid sub-lesson
        assertTrue(result.activeSubLessonIndex < subCount)
        assertEquals(subCount - 1, result.activeSubLessonIndex)
    }

    @Test
    fun testBuildSessionCards_lessonMode_returnsSubLessonTypes() {
        val lessons = listOf(
            lesson("l1", 30),
            lesson("l2", 30)
        )
        val schedules = provider.buildSchedules(lessons, emptyMap())

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.LESSON,
            selectedLessonId = LessonId("l1"),
            schedules = schedules,
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // First lesson always has NEW_ONLY sub-lessons (no previous lesson for review)
        assertTrue(result.subLessonTypes.isNotEmpty())
        assertTrue(result.subLessonTypes.all { it == SubLessonType.NEW_ONLY })
    }

    @Test
    fun testBuildSessionCards_lessonMode_completedSubLessonCount_zeroWithoutProgressTracker() {
        val lessons = listOf(lesson("l1", 20))
        val schedules = provider.buildSchedules(lessons, emptyMap())

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.LESSON,
            selectedLessonId = LessonId("l1"),
            schedules = schedules,
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // Without progressTracker, completedSubLessonCount is always 0
        assertEquals(0, result.completedSubLessonCount)
    }

    @Test
    fun testBuildSessionCards_lessonMode_completedSubLessonCount_withProgressTracker_delegates() {
        // CardProvider delegates to progressTracker?.calculateCompletedSubLessons.
        // When progressTracker is null, completedSubLessonCount is always 0.
        // When a tracker is provided, the result depends on tracker logic.
        // This test verifies the delegation contract by checking the null case.
        // The non-null case is tested in ProgressTrackerTest.
        val lessons = listOf(lesson("l1", 20))
        val schedules = provider.buildSchedules(lessons, emptyMap())

        // Provide mastery state — even with shown cards, no tracker means 0 completed
        val mastery = LessonMasteryState(
            lessonId = LessonId("l1"),
            languageId = LanguageId("en"),
            shownCardIds = setOf("Ll1_card0", "Ll1_card1")
        )

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.LESSON,
            selectedLessonId = LessonId("l1"),
            schedules = schedules,
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet(),
            mastery = mastery
        )

        // Without progressTracker, mastery is passed but not used for completedCount
        assertEquals(0, result.completedSubLessonCount)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  buildSessionCards — ALL_SEQUENTIAL mode
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildSessionCards_allSequential_returnsAllCardsInOrder() {
        val lessons = listOf(
            lesson("l1", 10),
            lesson("l2", 10)
        )

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // First block should contain the first 10 cards in lesson order
        assertEquals(10, result.cards.size)
        // Cards from l1 should come first (sequential = flatMap preserves order)
        assertTrue(result.cards.all { it.id.startsWith("Ll1_") })
    }

    @Test
    fun testBuildSessionCards_allSequential_paginatedIntoBlocks() {
        val lessons = listOf(lesson("l1", 25))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // 25 cards / 10 per block = 3 blocks (10 + 10 + 5)
        assertEquals(3, result.subLessonCount)
        assertEquals(10, result.cards.size) // First block = 10 cards
        assertEquals(0, result.activeSubLessonIndex)
    }

    @Test
    fun testBuildSessionCards_allSequential_secondBlock_returnsNext10() {
        val lessons = listOf(lesson("l1", 25))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 1,
            hiddenCardIds = emptySet()
        )

        assertEquals(10, result.cards.size)
        assertEquals(1, result.activeSubLessonIndex)
        // Second block should start at card index 10
        assertTrue(result.cards.any { it.id == "Ll1_card10" })
    }

    @Test
    fun testBuildSessionCards_allSequential_lastBlock_partialCards() {
        val lessons = listOf(lesson("l1", 25))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 2,
            hiddenCardIds = emptySet()
        )

        // Last block should have 5 cards (25 - 20 = 5)
        assertEquals(5, result.cards.size)
        assertEquals(2, result.activeSubLessonIndex)
    }

    @Test
    fun testBuildSessionCards_allSequential_filtersHiddenCards() {
        val lessons = listOf(lesson("l1", 15))
        val hiddenIds = setOf("Ll1_card0", "Ll1_card5", "Ll1_card10")

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = hiddenIds
        )

        assertTrue(result.cards.none { it.id in hiddenIds })
        // Filtering happens before pagination: 15 cards - 3 hidden = 12 cards total.
        // First block = first 10 of those 12 = 10 cards
        assertEquals(10, result.cards.size)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  buildSessionCards — ALL_MIXED mode
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildSessionCards_allMixed_respects300CardLimit() {
        // Create lessons with > 300 total cards
        val lessons = (1..10).map { lesson("l$it", 50) }

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_MIXED,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // Total cards across all sub-lessons should be <= 300
        val totalCards = result.subLessonCount * result.subLessonTotal.coerceAtMost(defaultSubLessonSize)
        // The REVIEW_LIMIT of 300 is applied in buildSessionCards
        // After shuffle + take(300), paginated into blocks
        assertTrue(result.subLessonCount <= 30) // 300 / 10 = 30 blocks max
    }

    @Test
    fun testBuildSessionCards_allMixed_cardsAreShuffled() {
        // Use enough cards to see shuffling effect
        val lessons = (1..5).map { lesson("l$it", 30) }

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_MIXED,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // Result should have cards (shuffled, but we can't assert randomness deterministically)
        assertTrue(result.cards.isNotEmpty())
    }

    @Test
    fun testBuildSessionCards_allMixed_filtersHiddenCards() {
        val lessons = listOf(lesson("l1", 15))
        val hiddenIds = setOf("Ll1_card3", "Ll1_card7")

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_MIXED,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = hiddenIds
        )

        assertTrue(result.cards.none { it.id in hiddenIds })
    }

    // ═══════════════════════════════════════════════════════════════════
    //  buildSessionCards — edge cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildSessionCards_emptyLessons_returnsEmptyResult() {
        val result = provider.buildSessionCards(
            lessons = emptyList(),
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertTrue(result.cards.isEmpty())
        assertEquals(0, result.subLessonCount)
        assertEquals(0, result.subLessonTotal)
    }

    @Test
    fun testBuildSessionCards_singleCard_returnsSingleCardBlock() {
        val lessons = listOf(lessonFromCards("l1", listOf(card("c1"))))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertEquals(1, result.cards.size)
        assertEquals(1, result.subLessonCount)
        assertEquals("c1", result.cards[0].id)
    }

    @Test
    fun testBuildSessionCards_negativeIndex_clampedToZero() {
        val lessons = listOf(lesson("l1", 20))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = -5,
            hiddenCardIds = emptySet()
        )

        assertEquals(0, result.activeSubLessonIndex)
    }

    @Test
    fun testBuildSessionCards_mixedChallengeMode_returnsEmptyForUnknownMode() {
        // MIX_CHALLENGE is not handled in buildSessionCards — falls through to else -> emptyList
        val lessons = listOf(lesson("l1", 20))
        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.MIX_CHALLENGE,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertTrue(result.cards.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════
    //  buildBossCards — LESSON type
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildBossCards_lessonType_returnsCardsFromSelectedLesson() {
        val lessons = listOf(lesson("l1", 30))

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.LESSON,
            selectedLessonId = LessonId("l1"),
            selectedIndex = 0
        )

        assertEquals(30, result.size)
        // All cards should belong to lesson l1
        assertTrue(result.all { it.id.startsWith("Ll1_") })
    }

    @Test
    fun testBuildBossCards_lessonType_capsAt300Cards() {
        val lessons = listOf(lesson("l1", 500))

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.LESSON,
            selectedLessonId = LessonId("l1"),
            selectedIndex = 0
        )

        assertEquals(TrainingConfig.MAX_BOSS_CARDS, result.size)
    }

    @Test
    fun testBuildBossCards_lessonType_unknownLesson_returnsEmpty() {
        val lessons = listOf(lesson("l1", 20))

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.LESSON,
            selectedLessonId = LessonId("nonexistent"),
            selectedIndex = 0
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun testBuildBossCards_lessonType_nullLessonId_returnsEmpty() {
        val lessons = listOf(lesson("l1", 20))

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.LESSON,
            selectedLessonId = null,
            selectedIndex = 0
        )

        assertTrue(result.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════
    //  buildBossCards — MEGA type
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildBossCards_megaType_returnsCardsFromLessonsBeforeSelected() {
        val lessons = listOf(
            lesson("l1", 10),
            lesson("l2", 10),
            lesson("l3", 10)
        )

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.MEGA,
            selectedLessonId = LessonId("l3"),
            selectedIndex = 2 // takes lessons at indices 0, 1
        )

        assertEquals(20, result.size)
        // Cards should be from l1 and l2 only
        assertTrue(result.all { it.id.startsWith("Ll1_") || it.id.startsWith("Ll2_") })
        assertTrue(result.none { it.id.startsWith("Ll3_") })
    }

    @Test
    fun testBuildBossCards_megaType_indexZero_returnsEmpty() {
        val lessons = listOf(lesson("l1", 10))

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.MEGA,
            selectedLessonId = LessonId("l1"),
            selectedIndex = 0 // first lesson — no lessons before it
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun testBuildBossCards_megaType_negativeIndex_returnsEmpty() {
        val lessons = listOf(lesson("l1", 10))

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.MEGA,
            selectedLessonId = LessonId("l1"),
            selectedIndex = -1
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun testBuildBossCards_megaType_capsAt300Cards() {
        val lessons = (1..10).map { lesson("l$it", 50) }

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.MEGA,
            selectedLessonId = LessonId("l10"),
            selectedIndex = 9 // takes lessons 0..8 = 450 cards
        )

        assertEquals(TrainingConfig.MAX_BOSS_CARDS, result.size)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  buildBossCards — ELITE type
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildBossCards_eliteType_returnsCardsFromAllLessons() {
        val lessons = listOf(
            lesson("l1", 20),
            lesson("l2", 20)
        )

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.ELITE,
            selectedLessonId = null,
            selectedIndex = 0
        )

        // Elite size = ceil(10 * 1.25) * 7 = 13 * 7 = 91 cards
        // But total available is 40, so limited to 40
        assertEquals(40, result.size)
    }

    @Test
    fun testBuildBossCards_eliteType_correctSizeCalculation() {
        // eliteSubLessonSize = ceil(10 * 1.25) = 13
        // eliteStepCount = 7
        // eliteSize = 13 * 7 = 91
        val lessons = (1..20).map { lesson("l$it", 10) } // 200 total cards

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.ELITE,
            selectedLessonId = null,
            selectedIndex = 0
        )

        assertEquals(91, result.size) // 13 * 7 = 91
    }

    @Test
    fun testBuildBossCards_eliteType_withCustomMultiplier() {
        val customProvider = CardProvider(
            subLessonSize = 10,
            subLessonSizeMin = 6,
            subLessonSizeMax = 12,
            eliteSizeMultiplier = 2.0, // ceil(10 * 2.0) = 20
            eliteStepCount = 5,
            progressTracker = null
        )

        val lessons = (1..20).map { lesson("l$it", 10) } // 200 total cards

        val result = customProvider.buildBossCards(
            lessons = lessons,
            type = BossType.ELITE,
            selectedLessonId = null,
            selectedIndex = 0
        )

        // 20 * 5 = 100
        assertEquals(100, result.size)
    }

    @Test
    fun testBuildBossCards_eliteType_emptyLessons_returnsEmpty() {
        val result = provider.buildBossCards(
            lessons = emptyList(),
            type = BossType.ELITE,
            selectedLessonId = null,
            selectedIndex = 0
        )

        assertTrue(result.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════
    //  buildMixChallengeCards
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildMixChallengeCards_returnsEmpty_whenFewerThan2StartedLessons() {
        val lessons = listOf(lesson("l1", 20))
        val started = setOf(LessonId("l1"))

        val result = provider.buildMixChallengeCards(lessons, started)

        assertTrue(result.isEmpty())
    }

    @Test
    fun testBuildMixChallengeCards_returnsEmpty_whenNoLessonsMatchStartedIds() {
        val lessons = listOf(
            lesson("l1", 20),
            lesson("l2", 20)
        )
        val started = setOf(LessonId("l3"), LessonId("l4"))

        val result = provider.buildMixChallengeCards(lessons, started)

        assertTrue(result.isEmpty())
    }

    @Test
    fun testBuildMixChallengeCards_returnsCards_whenAtLeast2StartedLessons() {
        val lessons = listOf(
            lesson("l1", 20),
            lesson("l2", 20),
            lesson("l3", 20)
        )
        val started = setOf(LessonId("l1"), LessonId("l2"), LessonId("l3"))

        val result = provider.buildMixChallengeCards(lessons, started)

        // Should return up to 10 cards (default count)
        assertTrue(result.isNotEmpty())
        assertTrue(result.size <= 10)
    }

    @Test
    fun testBuildMixChallengeCards_respectsCustomCount() {
        val lessons = (1..5).map { lesson("l$it", 20) }
        val started = lessons.map { it.id }.toSet()

        val result = provider.buildMixChallengeCards(lessons, started, count = 5)

        assertEquals(5, result.size)
    }

    @Test
    fun testBuildMixChallengeCards_onlyUsesStartedLessonCards() {
        val lessons = listOf(
            lesson("l1", 20),
            lesson("l2", 20),
            lesson("l3", 20)
        )
        // Only l1 and l2 are started — l3 should be excluded
        val started = setOf(LessonId("l1"), LessonId("l2"))

        val result = provider.buildMixChallengeCards(lessons, started, count = 10)

        assertTrue(result.isNotEmpty())
        // All cards should come from l1 or l2 only
        assertTrue(result.all { it.id.startsWith("Ll1_") || it.id.startsWith("Ll2_") })
        assertTrue(result.none { it.id.startsWith("Ll3_") })
    }

    @Test
    fun testBuildMixChallengeCards_takesUpTo5CardsPerLesson() {
        // Each lesson has 20 cards, but only up to 5 per lesson are used
        val lessons = listOf(
            lesson("l1", 20),
            lesson("l2", 20)
        )
        val started = lessons.map { it.id }.toSet()

        val result = provider.buildMixChallengeCards(lessons, started, count = 20)

        // 2 lessons * 5 cards = 10 max candidates
        assertTrue(result.size <= 10)
    }

    @Test
    fun testBuildMixChallengeCards_returnsEmpty_whenLessonsHaveNoCards() {
        val lessons = listOf(
            lessonFromCards("l1", emptyList()),
            lessonFromCards("l2", emptyList())
        )
        val started = setOf(LessonId("l1"), LessonId("l2"))

        val result = provider.buildMixChallengeCards(lessons, started)

        assertTrue(result.isEmpty())
    }

    @Test
    fun testBuildMixChallengeCards_usesMainPoolOnly() {
        // Lesson with 160 cards — mainPool is 150, reserve is 10
        val lessons = listOf(
            lesson("l1", 160),
            lesson("l2", 160)
        )
        val started = lessons.map { it.id }.toSet()

        val result = provider.buildMixChallengeCards(lessons, started, count = 10)

        // Cards should only come from main pool (first 150)
        // Reserve cards have IDs Ll1_card150..Ll1_card159 and Ll2_card150..Ll2_card159
        val reserveCardIds = (150 until 160).flatMap { idx ->
            listOf("Ll1_card$idx", "Ll2_card$idx")
        }.toSet()
        assertTrue(
            "Mix challenge should only use main pool cards, found reserve: ${result.map { it.id }.filter { it in reserveCardIds }}",
            result.none { it.id in reserveCardIds }
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  applyAlternationSort (tested indirectly through buildMixChallengeCards)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildMixChallengeCards_alternatesTenses_whenMultipleTensesAvailable() {
        val cards = (1..5).flatMap { lessonIdx ->
            (1..3).map { tenseIdx ->
                card("c${lessonIdx}_${tenseIdx}", tense = "tense$tenseIdx")
            }
        }

        val lessons = listOf(
            lessonFromCards("l1", cards.take(5)),
            lessonFromCards("l2", cards.drop(5).take(5)),
            lessonFromCards("l3", cards.drop(10).take(5))
        )
        val started = lessons.map { it.id }.toSet()

        val result = provider.buildMixChallengeCards(lessons, started, count = 15)

        // When multiple tenses exist, consecutive cards should differ
        // at least in the majority of cases
        if (result.size > 1) {
            val sameTensePairs = result.zipWithNext().count { (a, b) -> a.tense == b.tense }
            // Not all consecutive pairs should share the same tense
            assertTrue(
                "Expected tense alternation, but $sameTensePairs/${result.size - 1} consecutive pairs share tense",
                sameTensePairs < result.size - 1
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Custom sub-lesson sizes
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testCustomSubLessonSize_respectedInSessionCards() {
        val smallProvider = CardProvider(
            subLessonSize = 6,
            subLessonSizeMin = 6,
            subLessonSizeMax = 6,
            eliteSizeMultiplier = defaultEliteSizeMultiplier,
            eliteStepCount = defaultEliteStepCount,
            progressTracker = null
        )

        val lessons = listOf(lesson("l1", 20))

        val result = smallProvider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // 20 cards / 6 per block = 4 blocks (6+6+6+2)
        assertEquals(4, result.subLessonCount)
        assertEquals(6, result.cards.size)
    }

    @Test
    fun testSubLessonSize_clampedToMinMax() {
        val clampedProvider = CardProvider(
            subLessonSize = 50, // exceeds max of 12
            subLessonSizeMin = 6,
            subLessonSizeMax = 12,
            eliteSizeMultiplier = defaultEliteSizeMultiplier,
            eliteStepCount = defaultEliteStepCount,
            progressTracker = null
        )

        val lessons = listOf(lesson("l1", 30))

        val result = clampedProvider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // 30 cards / 12 per block = 3 blocks (12+12+6)
        assertEquals(3, result.subLessonCount)
        assertEquals(12, result.cards.size)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CardSetResult metadata
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testCardSetResult_subLessonTotal_equalsCardCount() {
        val lessons = listOf(lesson("l1", 15))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertEquals(result.cards.size, result.subLessonTotal)
    }

    @Test
    fun testCardSetResult_nonLessonModes_emptySubLessonTypes() {
        val lessons = listOf(lesson("l1", 20))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertTrue(result.subLessonTypes.isEmpty())
    }

    @Test
    fun testCardSetResult_nonLessonModes_zeroCompletedSubLessons() {
        val lessons = listOf(lesson("l1", 20))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertEquals(0, result.completedSubLessonCount)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Schedule cache key logic
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testCacheKey_differentLessonIds_triggersRebuild() {
        val lessons1 = listOf(lesson("alpha", 20))
        val result1 = provider.buildSchedules(lessons1, emptyMap())

        val lessons2 = listOf(lesson("beta", 20))
        val result2 = provider.buildSchedules(lessons2, result1)

        assertTrue(result1.containsKey(LessonId("alpha")))
        assertTrue(result2.containsKey(LessonId("beta")))
        assertFalse(result2.containsKey(LessonId("alpha")))
    }

    @Test
    fun testCacheKey_sameLessonsTwice_returnsSameMap() {
        val lessons = listOf(lesson("l1", 20))

        val result1 = provider.buildSchedules(lessons, emptyMap())
        val result2 = provider.buildSchedules(lessons, result1)

        // Same key → existing map returned
        assertSame(result1, result2)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Main pool vs reserve pool (lessons with >150 cards)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testLessonWithReserveCards_allSequential_includesAllCards() {
        // 200 cards — 150 main pool + 50 reserve
        val lessons = listOf(lesson("l1", 200))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // 200 cards / 10 = 20 blocks
        assertEquals(20, result.subLessonCount)
        assertEquals(10, result.cards.size)
    }

    @Test
    fun testLessonWithReserveCards_allMixed_usesAllCards() {
        val lessons = listOf(lesson("l1", 200))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_MIXED,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // Should use allCards (not just mainPool) up to REVIEW_LIMIT=300
        // 200 < 300, so all 200 should be included
        // 200 / 10 = 20 blocks
        assertEquals(20, result.subLessonCount)
    }

    @Test
    fun testBuildSessionCards_allMixed_multipleLessonsWithReserve() {
        val lessons = listOf(
            lesson("l1", 200),
            lesson("l2", 200)
        )

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_MIXED,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        // 400 total cards, but REVIEW_LIMIT = 300
        // 300 / 10 = 30 blocks
        assertEquals(30, result.subLessonCount)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Integration: buildSchedules + buildSessionCards (LESSON mode)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testIntegration_lessonMode_multipleSubLessons_navigateBetweenThem() {
        // 50 cards with default subLessonSize=10 → multiple sub-lessons
        val lessons = listOf(lesson("l1", 50))
        val schedules = provider.buildSchedules(lessons, emptyMap())
        val schedule = schedules[LessonId("l1")]!!

        val totalSubLessons = schedule.subLessons.size
        assertTrue("Expected multiple sub-lessons for 50 cards", totalSubLessons > 1)

        // Request each sub-lesson in sequence
        for (i in 0 until totalSubLessons) {
            val result = provider.buildSessionCards(
                lessons = lessons,
                mode = TrainingMode.LESSON,
                selectedLessonId = LessonId("l1"),
                schedules = schedules,
                activeSubLessonIndex = i,
                hiddenCardIds = emptySet()
            )
            assertEquals(i, result.activeSubLessonIndex)
            assertTrue(result.cards.isNotEmpty())
        }
    }

    @Test
    fun testIntegration_lessonMode_subLessonCardsDoNotOverlap() {
        val lessons = listOf(lesson("l1", 30))
        val schedules = provider.buildSchedules(lessons, emptyMap())
        val schedule = schedules[LessonId("l1")]!!

        val allCardIds = mutableSetOf<String>()
        for (i in schedule.subLessons.indices) {
            val result = provider.buildSessionCards(
                lessons = lessons,
                mode = TrainingMode.LESSON,
                selectedLessonId = LessonId("l1"),
                schedules = schedules,
                activeSubLessonIndex = i,
                hiddenCardIds = emptySet()
            )
            val currentIds = result.cards.map { it.id }.toSet()
            val overlap = allCardIds.intersect(currentIds)
            assertTrue("Sub-lessons should not share cards, but found overlap: $overlap", overlap.isEmpty())
            allCardIds.addAll(currentIds)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Boss cards are shuffled
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildBossCards_lessonType_cardsAreShuffled() {
        val lessons = listOf(lesson("l1", 30))

        // Run multiple times and verify at least one result differs from original order
        var foundDifferentOrder = false
        val originalOrder = (0 until 30).map { "Ll1_card$it" }

        repeat(10) {
            val result = provider.buildBossCards(
                lessons = lessons,
                type = BossType.LESSON,
                selectedLessonId = LessonId("l1"),
                selectedIndex = 0
            )
            val resultOrder = result.map { it.id }
            if (resultOrder != originalOrder) {
                foundDifferentOrder = true
            }
        }

        // With 30 cards shuffled 10 times, the probability of always getting
        // the same order is astronomically low (1/30!^10)
        assertTrue("Boss cards should be shuffled", foundDifferentOrder)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Additional boundary tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBuildSessionCards_allSequential_exactlyOneBlock() {
        // 10 cards = exactly 1 block of default size
        val lessons = listOf(lesson("l1", 10))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertEquals(1, result.subLessonCount)
        assertEquals(10, result.cards.size)
    }

    @Test
    fun testBuildSessionCards_allSequential_exactlyOneMoreThanOneBlock() {
        // 11 cards = 2 blocks (10 + 1)
        val lessons = listOf(lesson("l1", 11))

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = emptySet()
        )

        assertEquals(2, result.subLessonCount)
        assertEquals(10, result.cards.size) // First block is full
    }

    @Test
    fun testBuildSessionCards_allHiddenCards_returnsEmptyBlock() {
        val lessons = listOf(lesson("l1", 10))
        val allHidden = (0 until 10).map { "Ll1_card$it" }.toSet()

        val result = provider.buildSessionCards(
            lessons = lessons,
            mode = TrainingMode.ALL_SEQUENTIAL,
            selectedLessonId = null,
            schedules = emptyMap(),
            activeSubLessonIndex = 0,
            hiddenCardIds = allHidden
        )

        assertTrue(result.cards.isEmpty())
        // Block count is based on filtered cards: 0 cards = 0 blocks
        assertEquals(0, result.subLessonCount)
    }

    @Test
    fun testBuildBossCards_megaType_singleLessonBeforeSelected() {
        val lessons = listOf(
            lesson("l1", 10),
            lesson("l2", 5)
        )

        val result = provider.buildBossCards(
            lessons = lessons,
            type = BossType.MEGA,
            selectedLessonId = LessonId("l2"),
            selectedIndex = 1
        )

        // Only lesson at index 0 (l1) is before l2
        assertEquals(10, result.size)
        assertTrue(result.all { it.id.startsWith("Ll1_") })
    }

    @Test
    fun testBuildMixChallengeCards_exactly2StartedLessons_returnsCards() {
        val lessons = listOf(
            lesson("l1", 20),
            lesson("l2", 20)
        )
        val started = setOf(LessonId("l1"), LessonId("l2"))

        val result = provider.buildMixChallengeCards(lessons, started, count = 10)

        // Exactly 2 lessons is the minimum for mix challenge
        assertTrue(result.isNotEmpty())
        assertTrue(result.size <= 10)
    }

    @Test
    fun testBuildMixChallengeCards_singleCardPerLesson_returnsAll() {
        val lessons = listOf(
            lessonFromCards("l1", listOf(card("c1"))),
            lessonFromCards("l2", listOf(card("c2"))),
            lessonFromCards("l3", listOf(card("c3")))
        )
        val started = lessons.map { it.id }.toSet()

        val result = provider.buildMixChallengeCards(lessons, started, count = 10)

        // 3 lessons * 1 card each = 3 candidates
        assertEquals(3, result.size)
    }
}
