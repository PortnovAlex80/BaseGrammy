package com.alexpo.grammermate.data

/**
 * App navigation screen enum.
 * Extracted from GrammarMateApp.kt for shared access.
 * Historical ELITE and VOCAB names resolve to HOME through parse().
 */
enum class AppScreen {
    HOME,
    LESSON,
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
