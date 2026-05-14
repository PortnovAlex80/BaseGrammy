package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.SentenceCard

/**
 * Pure Kotlin object for generating word banks.
 *
 * Consolidates word bank generation logic that was previously duplicated across
 * [TrainingViewModel][com.alexpo.grammermate.ui.TrainingViewModel],
 * [VerbDrillCardSessionProvider][com.alexpo.grammermate.ui.VerbDrillCardSessionProvider], and
 * [DailyPracticeSessionProvider][com.alexpo.grammermate.feature.daily.DailyPracticeSessionProvider].
 *
 * All functions are stateless and pure — no Android dependencies, no side effects.
 */
object WordBankGenerator {

    /**
     * Generate a word bank for sentence translation training.
     *
     * Splits [targetAnswer] into individual words (the correct tokens), then selects
     * distractor words from [allCards] using normalized comparison to avoid including
     * words that match the answer after diacritics/case/punctuation normalization.
     *
     * @param targetAnswer  The correct answer string to split into word bank tokens.
     * @param allCards      All available sentence cards to pull distractors from.
     * @param maxDistractors Maximum number of distractor words to include. Defaults to 3.
     * @return A shuffled list of word bank tokens (answer words + distractors).
     */
    fun generateForSentence(
        targetAnswer: String,
        allCards: List<SentenceCard>,
        maxDistractors: Int = 3
    ): List<String> {
        val answerWords = splitWords(targetAnswer)
        val normalizedCorrect = answerWords.map { Normalizer.normalize(it) }.toSet()

        val distractorPool = allCards
            .flatMap { it.acceptedAnswers }
            .flatMap { splitWords(it) }
            .filter { it.length >= 3 }
            .filter { Normalizer.normalize(it) !in normalizedCorrect }
            .distinct()

        val distractors = distractorPool.shuffled().take(maxDistractors)
        return (answerWords + distractors).shuffled()
    }

    /**
     * Generate a word bank for verb conjugation drill.
     *
     * Splits [answer] into individual tokens (the correct parts), then selects distractor
     * words from [allAnswers] using normalized comparison. This is used by verb drill
     * sessions where the answer is a single conjugated form and distractors come from
     * other cards in the session.
     *
     * @param answer     The correct answer string.
     * @param allAnswers All available answers in the session to pull distractors from.
     * @param maxDistractors Maximum distractor slots. If the answer has N words,
     *                       the distractor budget is [maxDistractors] - N (clamped to 0).
     *                       Defaults to 8 (total word bank size target).
     * @return A shuffled list of word bank tokens (answer words + distractors).
     */
    fun generateForVerb(
        answer: String,
        allAnswers: List<String>,
        maxDistractors: Int = 8
    ): List<String> {
        val answerWords = splitWords(answer)
        val normalizedCorrect = answerWords.map { Normalizer.normalize(it) }.toSet()

        val distractorBudget = maxOf(0, maxDistractors - answerWords.size)

        val distractors = allAnswers
            .flatMap { splitWords(it) }
            .filter { Normalizer.normalize(it) !in normalizedCorrect }
            .distinct()
            .shuffled()
            .take(distractorBudget)

        return (answerWords + distractors).shuffled()
    }

    /**
     * Check whether a candidate word is a distractor (does not match any correct word).
     *
     * Uses [Normalizer.normalize] for comparison so that words differing only in
     * diacritics, case, or trailing punctuation are treated as equal.
     *
     * @param candidate        The word to check.
     * @param normalizedCorrect Set of already-normalized correct words.
     * @return True if [candidate] does NOT match any word in [normalizedCorrect].
     */
    fun isDistractor(candidate: String, normalizedCorrect: Set<String>): Boolean {
        return Normalizer.normalize(candidate) !in normalizedCorrect
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    /** Split a string into non-blank word tokens on whitespace. */
    private fun splitWords(text: String): List<String> {
        return text.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
    }
}
