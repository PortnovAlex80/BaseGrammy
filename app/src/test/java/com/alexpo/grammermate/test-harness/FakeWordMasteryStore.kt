package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.data.WordMasteryStore

/**
 * In-memory fake implementation of WordMasteryStore for testing.
 * No file I/O, no YAML parsing, no Android dependencies.
 */
class FakeWordMasteryStore : WordMasteryStore {

    private val masteryData = mutableMapOf<String, WordMasteryState>()

    override fun loadAll(): Map<String, WordMasteryState> = masteryData

    override fun saveAll(mastery: Map<String, WordMasteryState>) {
        masteryData.clear()
        masteryData.putAll(mastery)
    }

    override fun getMastery(wordId: String): WordMasteryState? = masteryData[wordId]

    override fun upsertMastery(state: WordMasteryState) {
        masteryData[state.wordId] = state
    }

    override fun getDueWords(): Set<String> {
        val now = System.currentTimeMillis()
        return masteryData.filter { (_, state) ->
            state.nextReviewDateMs <= now || state.lastReviewDateMs == 0L
        }.keys
    }

    override fun getMasteredCount(pos: String?): Int {
        val learned = masteryData.filter { (_, state) -> state.isLearned }
        return if (pos != null) {
            learned.count { (wordId, _) -> wordId.startsWith("${pos}_") }
        } else {
            learned.size
        }
    }

    override fun getMasteredByPos(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for ((wordId, state) in masteryData) {
            if (!state.isLearned) continue
            val pos = wordId.indexOf('_').let { idx ->
                if (idx > 0) wordId.substring(0, idx) else "unknown"
            }
            result[pos] = (result[pos] ?: 0) + 1
        }
        return result
    }

    /**
     * Test helper: Clear all data.
     */
    fun clear() {
        masteryData.clear()
    }

    /**
     * Test helper: Set mastery for a word directly.
     */
    fun setMastery(state: WordMasteryState) {
        masteryData[state.wordId] = state
    }

    /**
     * Load vocab drill test data from CourseTestDataFactory.
     *
     * @param vocabDrillData VocabDrillCourseData from CourseTestDataFactory
     * @param markAsLearned If true, mark all words as learned (step >= 3)
     */
    fun loadVocabDrillData(
        vocabDrillData: com.alexpo.grammermate.testharness.CourseTestDataFactory.VocabDrillCourseData,
        markAsLearned: Boolean = false
    ) {
        val targetStep = if (markAsLearned) 3 else 0

        for (word in vocabDrillData.allWords) {
            val mastery = WordMasteryState(
                wordId = word.id,
                intervalStepIndex = targetStep,
                correctCount = if (markAsLearned) 5 else 0,
                incorrectCount = 0,
                lastReviewDateMs = if (markAsLearned) System.currentTimeMillis() else 0L,
                nextReviewDateMs = if (markAsLearned) {
                    WordMasteryState.computeNextReview(System.currentTimeMillis(), targetStep)
                } else 0L,
                isLearned = markAsLearned
            )
            masteryData[word.id] = mastery
        }
    }

    /**
     * Simulate a review session for a specific word.
     *
     * @param wordId The word ID
     * @param correct Whether the answer was correct
     * @param rating The SRS rating (affects step advancement)
     */
    fun simulateReview(
        wordId: String,
        correct: Boolean,
        rating: SrsRating = SrsRating.GOOD
    ) {
        val existing = masteryData[wordId] ?: WordMasteryState.new(wordId)
        val now = System.currentTimeMillis()

        val newStep = when (rating) {
            SrsRating.AGAIN -> 0
            SrsRating.HARD -> existing.intervalStepIndex
            SrsRating.GOOD -> (existing.intervalStepIndex + 1).coerceAtMost(9)
            SrsRating.EASY -> (existing.intervalStepIndex + 2).coerceAtMost(9)
        }

        val newCorrectCount = if (correct) existing.correctCount + 1 else existing.correctCount
        val newIncorrectCount = if (!correct) existing.incorrectCount + 1 else existing.incorrectCount

        val updated = existing.copy(
            intervalStepIndex = newStep,
            correctCount = newCorrectCount,
            incorrectCount = newIncorrectCount,
            lastReviewDateMs = now,
            nextReviewDateMs = WordMasteryState.computeNextReview(now, newStep),
            isLearned = newStep >= 3
        )
        masteryData[wordId] = updated
    }

    /**
     * Mark a specific word as learned.
     *
     * @param wordId The word ID to mark as learned
     */
    fun markAsLearned(wordId: String) {
        val existing = masteryData[wordId] ?: WordMasteryState.new(wordId)
        val now = System.currentTimeMillis()
        val learnedStep = 3

        val updated = existing.copy(
            intervalStepIndex = learnedStep,
            correctCount = existing.correctCount + 3,
            lastReviewDateMs = now,
            nextReviewDateMs = WordMasteryState.computeNextReview(now, learnedStep),
            isLearned = true
        )
        masteryData[wordId] = updated
    }

    /**
     * Get mastery states for all words of a specific part of speech.
     */
    fun getMasteryForPos(pos: String): List<WordMasteryState> {
        return masteryData.filter { (wordId, _) -> wordId.startsWith("${pos}_") }
            .values.toList()
    }

    /**
     * Count words at a specific interval step.
     */
    fun countAtStep(step: Int): Int {
        return masteryData.count { (_, state) -> state.intervalStepIndex == step }
    }
}
