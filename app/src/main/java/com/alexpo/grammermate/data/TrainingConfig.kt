package com.alexpo.grammermate.data

object TrainingConfig {
    const val SUB_LESSON_SIZE_DEFAULT = 10
    const val SUB_LESSON_SIZE_MIN = 6
    const val SUB_LESSON_SIZE_MAX = 12
    const val ELITE_STEP_COUNT = 7

    /**
     * Fallback words for vocab word bank when insufficient distractors available.
     * Common words that work across most contexts.
     */
    val FALLBACK_WORDS_EN = listOf(
        "time", "person", "way", "day", "thing", "world", "life", "hand",
        "part", "child", "eye", "place", "work", "week", "case", "point"
    )

    val FALLBACK_WORDS_IT = listOf(
        "tempo", "persona", "modo", "giorno", "cosa", "mondo", "vita", "mano",
        "parte", "bambino", "occhio", "posto", "lavoro", "settimana", "caso", "punto"
    )
}
