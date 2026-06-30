package com.alexpo.grammermate.data

data class VerbDrillCard(
    override val id: String,
    override val promptRu: String,
    val answer: String,
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null,
    val person: String? = null,
    val rank: Int? = null
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

/**
 * Represents the state of the last incomplete verb drill session, enabling resume functionality.
 *
 * This state is persisted to `verb_drill_last_session.yaml` and restored when the user
 * re-enters the Verb Drill screen. The "Resume" action loads the NEXT cards from the pool,
 * excluding already shown cards (todayShownCardIds), rather than restoring the exact session.
 *
 * @property selectedTense The tense filter selected by the user (e.g., "Presente", "Imperfetto")
 * @property selectedGroup The conjugation group filter (e.g., "regular_are", "irregular")
 * @property sortByFrequency Whether cards were sorted by frequency rank
 * @property todayShownCardIds Cards already shown today that should be excluded when resuming
 * @property sessionCardIds Card IDs from the last batch, in display order, for Repeat
 * @property currentIndex Next card index within [sessionCardIds] for Continue
 * @property packId Pack that produced this session, used to reject stale migrated sessions
 */
data class VerbDrillLastSessionState(
    val selectedTense: String?,
    val selectedGroup: String?,
    val selectedPerson: String? = null,
    val sortByFrequency: Boolean,
    val todayShownCardIds: Set<String> = emptySet(),
    val sessionCardIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val packId: String? = null
)

data class VerbDrillUiState(
    val availableTenses: List<String> = emptyList(),
    val availableGroups: List<String> = emptyList(),
    val availablePersons: List<String> = emptyList(),
    val selectedTense: String? = null,
    val selectedGroup: String? = null,
    val selectedPerson: String? = null,
    val totalCards: Int = 0,
    val everShownCount: Int = 0,
    val todayShownCount: Int = 0,
    val session: VerbDrillSessionState? = null,
    val allDoneToday: Boolean = false,
    val isLoading: Boolean = true,
    val loadedLanguageId: String? = null,
    val badSentenceCount: Int = 0,
    val currentCardIsBad: Boolean = false,
    val sortByFrequency: Boolean = false,
    val showStartFreshResumeDialog: Boolean = false,
    val lastSessionContext: VerbDrillLastSessionState? = null,
    val showDebugInfo: Boolean = false,
    val debugInfo: String = ""
)
