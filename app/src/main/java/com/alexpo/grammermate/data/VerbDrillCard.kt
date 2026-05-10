package com.alexpo.grammermate.data

data class VerbDrillCard(
    override val id: String,
    override val promptRu: String,
    val answer: String,
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null
) : SessionCard {
    override val acceptedAnswers: List<String> get() = listOf(answer)
}

data class VerbDrillComboProgress(
    val group: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIds: Set<String> = emptySet(),
    val todayShownCardIds: Set<String> = emptySet(),
    val lastDate: String = ""
)

data class VerbDrillSessionState(
    val cards: List<VerbDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false
)

data class VerbDrillUiState(
    val availableTenses: List<String> = emptyList(),
    val availableGroups: List<String> = emptyList(),
    val selectedTense: String? = null,
    val selectedGroup: String? = null,
    val totalCards: Int = 0,
    val everShownCount: Int = 0,
    val todayShownCount: Int = 0,
    val session: VerbDrillSessionState? = null,
    val allDoneToday: Boolean = false,
    val isLoading: Boolean = true,
    val loadedLanguageId: String? = null,
    val badSentenceCount: Int = 0,
    val currentCardIsBad: Boolean = false
)
