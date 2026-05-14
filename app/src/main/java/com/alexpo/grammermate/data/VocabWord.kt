package com.alexpo.grammermate.data

/**
 * A vocabulary word for the Anki-like drill feature.
 * Constructed from parsed CSV data (via ItalianDrillVocabParser).
 */
data class VocabWord(
    val id: String,                         // e.g. "nouns_casa", "verbs_essere"
    val word: String,                        // Italian word
    val pos: String,                         // "nouns", "verbs", "adjectives", "adverbs"
    val rank: Int,                           // frequency rank
    val meaningRu: String? = null,           // Russian translation (optional)
    val collocations: List<String> = emptyList(),  // Italian phrases
    val forms: Map<String, String> = emptyMap()    // e.g. {"msg": "pazzo", "fsg": "pazza"} for adjectives
)

/**
 * Per-word spaced repetition mastery state.
 * Interval step index maps into SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.
 */
data class WordMasteryState(
    val wordId: String,
    val intervalStepIndex: Int = 0,         // 0-9, index into INTERVAL_LADDER_DAYS
    val correctCount: Int = 0,              // total correct answers
    val incorrectCount: Int = 0,            // total wrong answers
    val lastReviewDateMs: Long = 0L,        // epoch millis of last review
    val nextReviewDateMs: Long = 0L,        // computed: lastReviewDate + ladder[step] * DAY_MS
    val isLearned: Boolean = false           // reached LEARNED_THRESHOLD (step 3)
) {
    companion object {
        const val DAY_MS = 86_400_000L

        /**
         * Create a fresh mastery state for a word that has never been reviewed.
         * nextReviewDateMs = 0 means "due immediately".
         */
        fun new(wordId: String): WordMasteryState = WordMasteryState(wordId = wordId)

        /**
         * Compute next review date from last review and the current interval step.
         */
        fun computeNextReview(lastReviewMs: Long, stepIndex: Int): Long {
            val ladder = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS
            val days = if (stepIndex in ladder.indices) ladder[stepIndex] else ladder.last()
            return lastReviewMs + days * DAY_MS
        }
    }
}

/** A drill card combining a vocab word with its mastery state. */
data class VocabDrillCard(
    val word: VocabWord,
    val mastery: WordMasteryState
)

/** Direction for vocab drill: which language is prompted, which is the answer. */
enum class VocabDrillDirection { IT_TO_RU, RU_TO_IT }

/** Result of a voice recognition attempt. */
enum class VoiceResult { CORRECT, WRONG, SKIPPED }

/** Top-level UI state for the vocab drill screen. */
data class VocabDrillUiState(
    val isLoading: Boolean = true,
    val availablePos: List<String> = emptyList(),  // e.g. ["nouns", "verbs", "adjectives", "adverbs"]
    val selectedPos: String? = null,
    val rankMin: Int = 0,
    val rankMax: Int = Int.MAX_VALUE,
    val dueCount: Int = 0,
    val totalCount: Int = 0,
    val session: VocabDrillSessionState? = null,
    val loadedLanguageId: String? = null,
    val drillDirection: VocabDrillDirection = VocabDrillDirection.IT_TO_RU,
    val masteredCount: Int = 0,
    val masteredByPos: Map<String, Int> = emptyMap()
)

/** State of an active drill session. */
data class VocabDrillSessionState(
    val cards: List<VocabDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false,
    val isFlipped: Boolean = false,       // card flipped to show answer
    val direction: VocabDrillDirection = VocabDrillDirection.IT_TO_RU,
    val voiceAttempts: Int = 0,           // 0-3
    val voiceRecognizedText: String? = null,
    val voiceResult: VoiceResult? = null, // CORRECT, WRONG, SKIPPED
    val voiceCompleted: Boolean = false   // true after correct or 3 wrong or skip
)
