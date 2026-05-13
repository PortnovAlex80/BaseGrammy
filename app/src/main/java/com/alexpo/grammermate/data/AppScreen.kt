package com.alexpo.grammermate.data

/**
 * App navigation screen enum.
 * Extracted from GrammarMateApp.kt for shared access.
 * ELITE and VOCAB are kept for backward compatibility — they redirect to HOME.
 */
enum class AppScreen {
    HOME,
    LESSON,
    ELITE,
    VOCAB,
    DAILY_PRACTICE,
    MIX_CHALLENGE,
    STORY,
    TRAINING,
    LADDER,
    VERB_DRILL,
    VOCAB_DRILL;

    companion object {
        fun parse(name: String): AppScreen =
            try { valueOf(name) } catch (_: Exception) { HOME }
    }
}
