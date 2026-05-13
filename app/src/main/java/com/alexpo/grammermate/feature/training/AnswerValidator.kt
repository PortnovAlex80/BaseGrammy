package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.TrainingConfig

/**
 * Result of validating a user's answer against accepted answers.
 *
 * @property isCorrect    Whether the normalized input matched any accepted answer.
 * @property normalizedInput The input string after normalization (trimmed, lowercased,
 *                           diacritics stripped, punctuation removed).
 * @property hintShown    Whether the hint threshold was reached (3 failed attempts).
 * @property hintText     The hint text shown to the user (first accepted answer).
 */
data class ValidationResult(
    val isCorrect: Boolean,
    val normalizedInput: String,
    val hintShown: Boolean,
    val hintText: String?
)

/**
 * Pure-Kotlin answer validation logic extracted from the duplicated patterns in
 * [com.alexpo.grammermate.ui.TrainingViewModel],
 * [com.alexpo.grammermate.ui.VerbDrillCardSessionProvider], and
 * [com.alexpo.grammermate.feature.daily.DailyPracticeSessionProvider].
 *
 * Normalizes the user's input via [Normalizer], then compares it against every
 * accepted answer (each answer string may contain alternatives separated by `+`,
 * matching the CSV lesson format).
 *
 * This class has **no Android dependencies** and can be unit-tested in isolation.
 *
 * @param normalizer The [Normalizer] instance used for text normalization.
 *                   Defaults to [Normalizer] (singleton object) but is injectable
 *                   for testing.
 */
class AnswerValidator(
    private val normalizer: Normalizer = Normalizer
) {

    companion object {
        /** Number of incorrect attempts before a hint is automatically shown. */
        const val HINT_THRESHOLD = TrainingConfig.HINT_THRESHOLD
    }

    /**
     * Validate [input] against [acceptedAnswers].
     *
     * Each entry in [acceptedAnswers] may contain multiple alternatives separated
     * by `+` (the CSV lesson format).  Every alternative is normalized and compared
     * to the normalized input.
     *
     * When [testMode] is true the answer is always accepted (correct), matching the
     * existing behaviour in [com.alexpo.grammermate.ui.TrainingViewModel.submitAnswer].
     *
     * @param input           Raw user input.
     * @param acceptedAnswers List of accepted answer strings (may contain `+` separators).
     * @param testMode        When true, skips comparison and always returns correct.
     * @return [ValidationResult] with the outcome.
     */
    fun validate(
        input: String,
        acceptedAnswers: List<String>,
        testMode: Boolean = false
    ): ValidationResult {
        val normalizedInput = normalizer.normalize(input)

        val isCorrect = testMode || acceptedAnswers.any { answer ->
            // Split on `+` to handle the CSV multi-answer format (e.g. "ciao+salve").
            answer.split("+").any { alternative ->
                normalizer.normalize(alternative) == normalizedInput
            }
        }

        return ValidationResult(
            isCorrect = isCorrect,
            normalizedInput = normalizedInput,
            hintShown = false,
            hintText = null
        )
    }

    /**
     * Determine whether a hint should be shown based on the number of consecutive
     * incorrect attempts for the current card.
     *
     * @param incorrectAttempts Number of failed attempts so far.
     * @return true if [incorrectAttempts] >= [HINT_THRESHOLD] (default 3).
     */
    fun shouldShowHint(incorrectAttempts: Int): Boolean {
        return incorrectAttempts >= HINT_THRESHOLD
    }

    /**
     * Build the hint text from the list of accepted answers.
     *
     * Returns the first accepted answer, matching the behaviour of both
     * [com.alexpo.grammermate.ui.VerbDrillCardSessionProvider] and
     * [com.alexpo.grammermate.feature.daily.DailyPracticeSessionProvider]
     * which show `card.acceptedAnswers.first()` or `card.answer` as the hint.
     *
     * @param acceptedAnswers List of accepted answer strings.
     * @return The first answer, or an empty string if the list is empty.
     */
    fun getHintText(acceptedAnswers: List<String>): String {
        return acceptedAnswers.firstOrNull()?.ifBlank { "" } ?: ""
    }
}
