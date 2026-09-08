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
    STORY,
    TRAINING,
    LADDER,
    VERB_DRILL,
    VOCAB_DRILL,
    CHAPTER_LESSONS,
    AUX_DRILL;

    companion object {
        fun parse(name: String): AppScreen =
            try { valueOf(name) } catch (_: Exception) { HOME }
    }
}
