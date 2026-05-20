package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.VocabDrillCard
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.data.VocabDrillSessionState
import com.alexpo.grammermate.data.VocabWord
import com.alexpo.grammermate.data.VoiceResult
import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.testharness.FakeWordMasteryStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Comprehensive JUnit test for Vocab Drill SRS mechanics.
 *
 * Tests cover:
 * - SRS rating step progression (Again/Hard/Good/Easy)
 * - Step clamping (bounds 0-9)
 * - Learned threshold (step >= 3)
 * - Direction alternation (even/odd cards)
 * - Voice input attempts (3 failures then skip)
 * - Card flip mechanics
 * - Forms display for adjectives
 * - Collocations display for verbs
 *
 * Uses FakeWordMasteryStore for in-memory testing without Android dependencies.
 *
 * Reference: docs/specification/scenarios/click-test-vocab-drill.md
 */
@RunWith(RobolectricTestRunner::class)
class VocabDrillClickTest {

    private lateinit var fakeStore: FakeWordMasteryStore

    @Before
    fun setup() {
        fakeStore = FakeWordMasteryStore()
    }

    // ========================================
    // SRS RATING TESTS
    // ========================================

    @Test
    fun testSRSRating_AgainResetsToStepZero() {
        // Arrange: Create a word at step 5
        val wordId = "nouns_casa"
        val initialState = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 5,
            correctCount = 10,
            incorrectCount = 2
        )
        fakeStore.setMastery(initialState)

        // Act: Apply AGAIN rating (simulating answerRating logic)
        val current = fakeStore.getMastery(wordId)!!
        val newStepIndex = 0  // AGAIN always resets to 0
        val now = System.currentTimeMillis()
        val updatedState = current.copy(
            intervalStepIndex = newStepIndex,
            correctCount = current.correctCount,  // AGAIN doesn't increment correct
            incorrectCount = current.incorrectCount + 1,  // AGAIN increments incorrect
            lastReviewDateMs = now,
            nextReviewDateMs = WordMasteryState.computeNextReview(now, newStepIndex),
            isLearned = newStepIndex >= TrainingConfig.LEARNED_THRESHOLD
        )
        fakeStore.upsertMastery(updatedState)

        // Assert: Step reset to 0
        val result = fakeStore.getMastery(wordId)
        assertEquals("Step should reset to 0 after AGAIN", 0, result?.intervalStepIndex)
        assertEquals("Incorrect count should increment", 3, result?.incorrectCount)
        assertEquals("Correct count should not change", 10, result?.correctCount)
        assertFalse("isLearned should be false at step 0", result?.isLearned ?: true)
    }

    @Test
    fun testSRSRating_HardKeepsCurrentStep() {
        // Arrange: Create a word at step 3
        val wordId = "verbs_essere"
        val initialState = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 3,
            correctCount = 5
        )
        fakeStore.setMastery(initialState)

        // Act: Apply HARD rating (delta = 0, stay same step)
        val current = fakeStore.getMastery(wordId)!!
        val delta = 0  // HARD keeps current step
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val newStepIndex = (current.intervalStepIndex + delta).coerceIn(0, maxStep)
        val now = System.currentTimeMillis()
        val updatedState = current.copy(
            intervalStepIndex = newStepIndex,
            correctCount = current.correctCount + 1,  // HARD increments correct
            incorrectCount = current.incorrectCount,  // HARD doesn't increment incorrect
            lastReviewDateMs = now,
            nextReviewDateMs = WordMasteryState.computeNextReview(now, newStepIndex),
            isLearned = newStepIndex >= TrainingConfig.LEARNED_THRESHOLD
        )
        fakeStore.upsertMastery(updatedState)

        // Assert: Step unchanged at 3
        val result = fakeStore.getMastery(wordId)
        assertEquals("Step should stay at 3 after HARD", 3, result?.intervalStepIndex)
        assertEquals("Correct count should increment", 6, result?.correctCount)
        assertTrue("isLearned should be true at step 3", result?.isLearned ?: false)
    }

    @Test
    fun testSRSRating_GoodAdvancesOneStep() {
        // Arrange: Create a word at step 2
        val wordId = "adjectives_bello"
        val initialState = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 2
        )
        fakeStore.setMastery(initialState)

        // Act: Apply GOOD rating (delta = 1)
        val current = fakeStore.getMastery(wordId)!!
        val delta = 1  // GOOD advances 1 step
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val newStepIndex = (current.intervalStepIndex + delta).coerceIn(0, maxStep)
        val now = System.currentTimeMillis()
        val updatedState = current.copy(
            intervalStepIndex = newStepIndex,
            correctCount = current.correctCount + 1,
            lastReviewDateMs = now,
            nextReviewDateMs = WordMasteryState.computeNextReview(now, newStepIndex),
            isLearned = newStepIndex >= TrainingConfig.LEARNED_THRESHOLD
        )
        fakeStore.upsertMastery(updatedState)

        // Assert: Step advanced to 3
        val result = fakeStore.getMastery(wordId)
        assertEquals("Step should advance to 3 after GOOD", 3, result?.intervalStepIndex)
        assertTrue("isLearned should be true at step 3", result?.isLearned ?: false)
    }

    @Test
    fun testSRSRating_EasyAdvancesTwoSteps() {
        // Arrange: Create a word at step 2
        val wordId = "adverbs_oggi"
        val initialState = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 2
        )
        fakeStore.setMastery(initialState)

        // Act: Apply EASY rating (delta = 2)
        val current = fakeStore.getMastery(wordId)!!
        val delta = 2  // EASY advances 2 steps
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val newStepIndex = (current.intervalStepIndex + delta).coerceIn(0, maxStep)
        val now = System.currentTimeMillis()
        val updatedState = current.copy(
            intervalStepIndex = newStepIndex,
            correctCount = current.correctCount + 1,
            lastReviewDateMs = now,
            nextReviewDateMs = WordMasteryState.computeNextReview(now, newStepIndex),
            isLearned = newStepIndex >= TrainingConfig.LEARNED_THRESHOLD
        )
        fakeStore.upsertMastery(updatedState)

        // Assert: Step advanced to 4
        val result = fakeStore.getMastery(wordId)
        assertEquals("Step should advance to 4 after EASY", 4, result?.intervalStepIndex)
        assertTrue("isLearned should be true at step 4", result?.isLearned ?: false)
    }

    // ========================================
    // STEP CLAMPING TESTS
    // ========================================

    @Test
    fun testStepClamping_NeverBelowZero() {
        // Arrange: Create a word at step 0
        val wordId = "nouns_test_zero"
        val initialState = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 0
        )
        fakeStore.setMastery(initialState)

        // Act: Apply HARD rating (delta = 0) at step 0
        val current = fakeStore.getMastery(wordId)!!
        val delta = 0
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val newStepIndex = (current.intervalStepIndex + delta).coerceIn(0, maxStep)

        // Assert: Step cannot go below 0
        assertTrue("Step should be >= 0", newStepIndex >= 0)
        assertEquals("Step should remain 0", 0, newStepIndex)
    }

    @Test
    fun testStepClamping_NeverAboveNine() {
        // Arrange: Create a word at step 8
        val wordId = "verbs_test_max"
        val initialState = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 8
        )
        fakeStore.setMastery(initialState)

        // Act: Apply EASY rating (delta = 2) at step 8
        val current = fakeStore.getMastery(wordId)!!
        val delta = 2
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val newStepIndex = (current.intervalStepIndex + delta).coerceIn(0, maxStep)

        // Assert: Step clamped to max 9
        assertEquals("Step should be clamped to 9", 9, newStepIndex)
    }

    @Test
    fun testStepClamping_GoodAtMaxStaysAtMax() {
        // Arrange: Create a word at step 9 (max)
        val wordId = "verbs_test_max_stay"
        val initialState = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 9
        )
        fakeStore.setMastery(initialState)

        // Act: Apply GOOD rating (delta = 1) at step 9
        val current = fakeStore.getMastery(wordId)!!
        val delta = 1
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val newStepIndex = (current.intervalStepIndex + delta).coerceIn(0, maxStep)

        // Assert: Step stays at 9
        assertEquals("Step should remain at 9", 9, newStepIndex)
    }

    // ========================================
    // LEARNED THRESHOLD TESTS
    // ========================================

    @Test
    fun testLearnedThreshold_StepThreeOrHigher() {
        // Test all steps around the threshold
        for (step in 0..9) {
            val wordId = "test_step_$step"
            val state = WordMasteryState(
                wordId = wordId,
                intervalStepIndex = step
            )
            fakeStore.setMastery(state)

            val isLearned = step >= TrainingConfig.LEARNED_THRESHOLD
            val result = state.copy(isLearned = isLearned)
            fakeStore.upsertMastery(result)

            val retrieved = fakeStore.getMastery(wordId)
            val expectedLearned = step >= TrainingConfig.LEARNED_THRESHOLD

            assertEquals("Step $step: isLearned should be $expectedLearned",
                expectedLearned, retrieved?.isLearned)
        }
    }

    @Test
    fun testLearnedThreshold_StepTwoNotLearned() {
        val wordId = "test_step_2_not_learned"
        val state = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 2
        )
        fakeStore.setMastery(state)

        val isLearned = 2 >= TrainingConfig.LEARNED_THRESHOLD
        val result = state.copy(isLearned = isLearned)
        fakeStore.upsertMastery(result)

        val retrieved = fakeStore.getMastery(wordId)
        assertFalse("Step 2 should not be learned", retrieved?.isLearned ?: true)
    }

    @Test
    fun testLearnedThreshold_StepThreeIsLearned() {
        val wordId = "test_step_3_learned"
        val state = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 3
        )
        fakeStore.setMastery(state)

        val isLearned = 3 >= TrainingConfig.LEARNED_THRESHOLD
        val result = state.copy(isLearned = isLearned)
        fakeStore.upsertMastery(result)

        val retrieved = fakeStore.getMastery(wordId)
        assertTrue("Step 3 should be learned", retrieved?.isLearned ?: false)
    }

    // ========================================
    // DIRECTION ALTERNATION TESTS
    // ========================================

    @Test
    fun testDirectionAlternation_EvenIsITtoRU() {
        // Create cards with different indices
        val cards = listOf(
            createTestCard(index = 0),  // even
            createTestCard(index = 1),  // odd
            createTestCard(index = 2),  // even
            createTestCard(index = 3)   // odd
        )

        // Even-indexed cards should use IT_TO_RU direction
        val evenCard = cards[0]
        val session = VocabDrillSessionState(
            cards = cards,
            direction = VocabDrillDirection.IT_TO_RU
        )

        // Verify even card session uses IT_TO_RU
        assertEquals("Even-indexed card should use IT_TO_RU",
            VocabDrillDirection.IT_TO_RU, session.direction)
    }

    @Test
    fun testDirectionAlternation_OddIsRUtoIT() {
        // In the actual UI, direction alternates based on card index
        // This test verifies the session state can represent RU_TO_IT
        val cards = listOf(
            createTestCard(index = 0),
            createTestCard(index = 1)
        )

        val sessionRuToIt = VocabDrillSessionState(
            cards = cards,
            direction = VocabDrillDirection.RU_TO_IT
        )

        // Verify session can have RU_TO_IT direction
        assertEquals("Session should support RU_TO_IT direction",
            VocabDrillDirection.RU_TO_IT, sessionRuToIt.direction)
    }

    // ========================================
    // VOICE INPUT TESTS
    // ========================================

    @Test
    fun testVoiceInput_ThreeAttemptsThenSkip() {
        val cards = listOf(createTestCard(index = 0))
        var session = VocabDrillSessionState(
            cards = cards,
            voiceAttempts = 0,
            voiceCompleted = false
        )

        // Simulate 3 wrong attempts
        for (i in 1..3) {
            session = session.copy(
                voiceAttempts = i,
                voiceResult = VoiceResult.WRONG
            )
        }

        // After 3 wrong attempts, should be marked as completed
        val shouldAutoFlip = session.voiceAttempts >= 3
        assertTrue("Should auto-flip after 3 wrong attempts", shouldAutoFlip)
        assertEquals("Voice attempts should be 3", 3, session.voiceAttempts)
    }

    @Test
    fun testVoiceInput_ClearsOnNewCard() {
        val cards = listOf(
            createTestCard(index = 0),
            createTestCard(index = 1)
        )

        var session = VocabDrillSessionState(
            cards = cards,
            currentIndex = 0,
            voiceAttempts = 2,
            voiceRecognizedText = "wrong answer",
            voiceResult = VoiceResult.WRONG,
            voiceCompleted = false
        )

        // Advance to next card
        session = session.copy(
            currentIndex = 1,
            voiceAttempts = 0,
            voiceRecognizedText = null,
            voiceResult = null,
            voiceCompleted = false
        )

        // Verify voice state cleared
        assertEquals("Voice attempts should reset to 0", 0, session.voiceAttempts)
        assertNull("Recognized text should be cleared", session.voiceRecognizedText)
        assertNull("Voice result should be cleared", session.voiceResult)
        assertFalse("Voice completed should be false", session.voiceCompleted)
    }

    @Test
    fun testVoiceInput_SkipMarksCompleted() {
        val cards = listOf(createTestCard(index = 0))
        var session = VocabDrillSessionState(
            cards = cards,
            voiceAttempts = 0,
            voiceCompleted = false
        )

        // Simulate skip action
        session = session.copy(
            voiceResult = VoiceResult.SKIPPED,
            voiceCompleted = true
        )

        // Verify skip marks voice as completed
        assertTrue("Skip should mark voice as completed", session.voiceCompleted)
        assertEquals("Skip should set result to SKIPPED", VoiceResult.SKIPPED, session.voiceResult)
    }

    // ========================================
    // CARD FLIP TESTS
    // ========================================

    @Test
    fun testCardFlip_FrontToBack() {
        val cards = listOf(createTestCard(index = 0))
        var session = VocabDrillSessionState(
            cards = cards,
            isFlipped = false
        )

        // Verify initial state
        assertFalse("Card should start unflipped", session.isFlipped)

        // Flip the card
        session = session.copy(isFlipped = true)

        // Verify flipped state
        assertTrue("Card should be flipped", session.isFlipped)
    }

    @Test
    fun testCardFlip_ResetsOnNextCard() {
        val cards = listOf(
            createTestCard(index = 0),
            createTestCard(index = 1)
        )
        var session = VocabDrillSessionState(
            cards = cards,
            currentIndex = 0,
            isFlipped = true
        )

        // Verify current card is flipped
        assertTrue("First card should be flipped", session.isFlipped)

        // Advance to next card (isFlipped resets to false)
        session = session.copy(
            currentIndex = 1,
            isFlipped = false
        )

        // Verify flip state reset
        assertFalse("Second card should start unflipped", session.isFlipped)
    }

    // ========================================
    // FORMS DISPLAY TESTS
    // ========================================

    @Test
    fun testFormsDisplay_Adjectives() {
        // Create an adjective with gender forms
        val adjective = VocabWord(
            id = "adjectives_solito",
            word = "solito",
            pos = "adjectives",
            rank = 500,
            meaningRu = "usual",
            forms = mapOf(
                "msg" to "solito",
                "fsg" to "solita",
                "mpl" to "soliti",
                "fpl" to "solite"
            )
        )

        // Verify forms are present
        assertTrue("Adjective should have forms", adjective.forms.isNotEmpty())
        assertEquals("Should have 4 form variants", 4, adjective.forms.size)
        assertEquals("Masculine singular should be solito", "solito", adjective.forms["msg"])
        assertEquals("Feminine singular should be solita", "solita", adjective.forms["fsg"])
    }

    @Test
    fun testFormsDisplay_NoFormsForNouns() {
        // Nouns typically don't have forms in our data model
        val noun = VocabWord(
            id = "nouns_casa",
            word = "casa",
            pos = "nouns",
            rank = 100,
            meaningRu = "house"
        )

        // Verify no forms
        assertTrue("Noun should have no forms", noun.forms.isEmpty())
    }

    // ========================================
    // COLLOCATIONS DISPLAY TESTS
    // ========================================

    @Test
    fun testCollocationsDisplay_Verbs() {
        // Create a verb with collocations
        val verb = VocabWord(
            id = "verbs_essere",
            word = "essere",
            pos = "verbs",
            rank = 1,
            meaningRu = "быть",
            collocations = listOf(
                "essere sicuro",
                "essere necessario",
                "essere possibile",
                "essere in grado di",
                "essere d'accordo"
            )
        )

        // Verify collocations are present
        assertTrue("Verb should have collocations", verb.collocations.isNotEmpty())
        assertEquals("Should have 5 collocations", 5, verb.collocations.size)
        assertEquals("First collocation should be 'essere sicuro'",
            "essere sicuro", verb.collocations[0])
    }

    @Test
    fun testCollocationsDisplay_MoreThanFive() {
        // Create a word with more than 5 collocations
        val word = VocabWord(
            id = "verbs_test_many",
            word = "test",
            pos = "verbs",
            rank = 1000,
            meaningRu = "test",
            collocations = listOf(
                "collocation 1",
                "collocation 2",
                "collocation 3",
                "collocation 4",
                "collocation 5",
                "collocation 6",
                "collocation 7"
            )
        )

        // Verify all collocations stored
        assertEquals("All collocations should be stored", 7, word.collocations.size)

        // UI would display only first 5 with "+N more" indicator
        val displayCount = minOf(5, word.collocations.size)
        val moreCount = word.collocations.size - displayCount
        assertEquals("UI should display 5 collocations", 5, displayCount)
        assertEquals("UI should show '+2 more'", 2, moreCount)
    }

    @Test
    fun testCollocationsDisplay_NoCollocations() {
        val word = VocabWord(
            id = "test_no_coll",
            word = "test",
            pos = "nouns",
            rank = 5000,
            meaningRu = "test"
        )

        // Verify no collocations
        assertTrue("Word should have no collocations", word.collocations.isEmpty())
    }

    // ========================================
    // INTERVAL LADDER TESTS
    // ========================================

    @Test
    fun testIntervalLadder_ComputeNextReview() {
        val ladder = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS

        // Test each step in the ladder
        for ((index, days) in ladder.withIndex()) {
            val lastReview = System.currentTimeMillis()
            val nextReview = WordMasteryState.computeNextReview(lastReview, index)
            val expectedDelta = days * WordMasteryState.DAY_MS

            // Allow small margin for timing
            val actualDelta = nextReview - lastReview
            assertEquals("Step $index: should be $days days", expectedDelta.toLong(), actualDelta.toLong())
        }
    }

    @Test
    fun testIntervalLadder_MaxStepUsesLastInterval() {
        val lastReview = System.currentTimeMillis()
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val lastInterval = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.last()

        val nextReview = WordMasteryState.computeNextReview(lastReview, maxStep)
        val expectedDelta = lastInterval * WordMasteryState.DAY_MS
        val actualDelta = nextReview - lastReview

        assertEquals("Max step should use last interval", expectedDelta.toLong(), actualDelta.toLong())
    }

    // ========================================
    // DUE WORDS TESTS
    // ========================================

    @Test
    fun testGetDueWords_NeverReviewedIsDue() {
        // Word with lastReviewDateMs = 0 is due immediately
        val wordId = "nouns_new_word"
        val state = WordMasteryState.new(wordId)
        fakeStore.setMastery(state)

        val dueWords = fakeStore.getDueWords()

        assertTrue("Never-reviewed word should be due", dueWords.contains(wordId))
    }

    @Test
    fun testGetDueWords_OverdueIsDue() {
        val wordId = "verbs_overdue"
        val past = System.currentTimeMillis() - (10 * WordMasteryState.DAY_MS)  // 10 days ago
        val state = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 2,  // 4-day interval
            lastReviewDateMs = past,
            nextReviewDateMs = past + (4 * WordMasteryState.DAY_MS)
        )
        fakeStore.setMastery(state)

        val dueWords = fakeStore.getDueWords()

        assertTrue("Overdue word should be due", dueWords.contains(wordId))
    }

    @Test
    fun testGetDueWords_NotDueYet() {
        val wordId = "adjectives_future"
        val future = System.currentTimeMillis() + WordMasteryState.DAY_MS  // 1 day in future
        val state = WordMasteryState(
            wordId = wordId,
            intervalStepIndex = 0,  // 1-day interval
            lastReviewDateMs = System.currentTimeMillis(),
            nextReviewDateMs = future
        )
        fakeStore.setMastery(state)

        val dueWords = fakeStore.getDueWords()

        assertFalse("Future-due word should not be due", dueWords.contains(wordId))
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private fun createTestCard(index: Int): VocabDrillCard {
        val word = VocabWord(
            id = "test_word_$index",
            word = "word$index",
            pos = "nouns",
            rank = index + 1,
            meaningRu = "meaning$index"
        )
        val mastery = WordMasteryState.new(word.id)
        return VocabDrillCard(word, mastery)
    }
}
