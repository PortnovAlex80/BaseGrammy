package com.alexpo.grammermate.data

/**
 * Card in the aux (lead-in) drill pool. Mapped on-the-fly from parsed
 * VerbDrillCsvParser output — the underlying pool file is shared with Verb
 * Drill, but this model is independent to keep the aux layer isolated.
 */
data class AuxDrillCard(
    val id: String,
    val promptRu: String,
    val answer: String,
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null,
    val person: String? = null,
    val rank: Int? = null
)

/** Per-(verb|tense) progress for aux drill. Stored under its own yaml file. */
data class AuxDrillComboProgress(
    val verb: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIds: Set<String> = emptySet(),
    val lastDate: String = ""
)

data class AuxDrillSessionState(
    val cards: List<AuxDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false
)

data class AuxDrillUiState(
    val availablePairs: List<AuxDrillPair> = emptyList(),
    val selectedPair: AuxDrillPair? = null,
    val totalCards: Int = 0,
    val everShownCount: Int = 0,
    val session: AuxDrillSessionState? = null,
    val isLoading: Boolean = true,
    val loadedLanguageId: String? = null
)
