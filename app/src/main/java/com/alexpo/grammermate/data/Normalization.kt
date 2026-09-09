package com.alexpo.grammermate.data

object Normalizer {

    // Pre-compiled regex patterns — avoid re-compilation on every normalize() call
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val DIACRITICAL_MARKS_REGEX = Regex("\\p{M}")
    private val TIME_MINUTES_REGEX = Regex("\\b(\\d{1,2}):\\d{2}\\b")

    fun normalize(input: String): String {
        val trimmed = input.trim().replace(WHITESPACE_REGEX, " ")
        // NFD decomposition + strip combining diacritical marks (e.g. "perche" == "perché")
        val decomposed = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(DIACRITICAL_MARKS_REGEX, "")
        val timeFixed = noDiacritics.replace(TIME_MINUTES_REGEX, "$1")
        val lower = timeFixed.lowercase()
        val builder = StringBuilder()
        for (ch in lower) {
            when (ch) {
                // Spec (legacy-project-idea: "апостроф внутри слова сохраняется",
                // don't ≠ dont): apostrophes are MEANINGFUL — normalize all
                // typographic variants to ASCII and keep them.
                '\'', '`', '´', '’', '‘' -> builder.append('\'')
                '.', ',', '?', '!', ':', ';', '"', '<', '>', '(', ')', '[', ']', '{', '}' -> {
                    // Skip punctuation.
                }
                else -> builder.append(ch)
            }
        }
        return builder.toString().replace(WHITESPACE_REGEX, " ").trim()
    }


    /**
     * Universal normalization for voice input.
     *
     * Voice recognition can ONLY produce letters, digits, and spaces — never
     * apostrophes, hyphens, dashes, or any punctuation. So any special character
     * in the expected answer must become a space before comparison.
     *
     * Algorithm:
     * 1. NFD decomposition + strip diacritical marks (é → e)
     * 2. Lowercase
     * 3. Replace EVERY non-letter/non-digit with a space (universal — handles
     *    apostrophes, hyphens, dashes, quotes, any Unicode punctuation)
     * 4. Collapse multiple spaces, trim
     *
     * Examples:
     *   "un'insegnante" → "un insegnante"
     *   "va' a casa"    → "va  a casa" → "va a casa"
     *   "l'italiano"    → "l italiano"
     *   "perché"        → "perche"
     *   "state-attento" → "state attento"
     */
    fun normalizeForVoice(input: String): String {
        val trimmed = input.trim().replace(WHITESPACE_REGEX, " ")
        // NFD decomposition + strip combining diacritical marks
        val decomposed = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(DIACRITICAL_MARKS_REGEX, "")
        val lower = noDiacritics.lowercase()
        // Replace ALL non-letter/non-digit with space — universal for any language
        val builder = StringBuilder()
        for (ch in lower) {
            if (ch.isLetterOrDigit()) {
                builder.append(ch)
            } else {
                builder.append(' ')
            }
        }
        return builder.toString().replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Check whether [input] is a complete, exact match against any of [acceptedAnswers]
     * after normalization. Used for auto-submit: only returns true when the user has
     * typed the full answer (not just a prefix).
     */
    fun isExactMatch(input: String, acceptedAnswers: List<String>, minLength: Int = 2): Boolean {
        val normalizedInput = normalize(input)
        if (normalizedInput.length < minLength) return false
        return acceptedAnswers.any { ans ->
            normalizedInput == normalize(ans)
        }
    }
}
