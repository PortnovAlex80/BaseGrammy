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
                '\'', '`', '´', '’', '‘', '.', ',', '?', '!', ':', ';', '"', '<', '>', '(', ')', '[', ']', '{', '}' -> {
                    // Skip punctuation and apostrophe variants.
                }
                '-' -> {
                    builder.append(ch)
                }
                else -> builder.append(ch)
            }
        }
        return builder.toString().replace(WHITESPACE_REGEX, " ").trim()
    }


    /**
     * Aggressive normalization for voice input.
     * Strips ALL special characters (punctuation, apostrophes, hyphens, quotes, etc.)
     * keeping only letters, digits, and spaces. Voice input cannot produce special
     * characters, so this ensures "va a casa" matches "va' a casa", "perche" matches
     * "perché", etc.
     */
    fun normalizeForVoice(input: String): String {
        val trimmed = input.trim().replace(WHITESPACE_REGEX, " ")
        // NFD decomposition + strip combining diacritical marks
        val decomposed = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(DIACRITICAL_MARKS_REGEX, "")
        val lower = noDiacritics.lowercase()
        // Keep ONLY letters, digits, and spaces — strip everything else
        val builder = StringBuilder()
        for (ch in lower) {
            if (ch.isLetterOrDigit() || ch == ' ') {
                builder.append(ch)
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