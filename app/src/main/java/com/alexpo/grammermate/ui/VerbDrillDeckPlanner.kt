package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillUiState

/** Pure card selection rules shared by progress display and session creation. */
internal object VerbDrillDeckPlanner {
    data class Selection(val tense: String?, val group: String?, val person: String?) {
        constructor(state: VerbDrillUiState) : this(state.selectedTense, state.selectedGroup, state.selectedPerson)

        val comboKey: String get() = "${group ?: ""}|${tense ?: ""}|${person ?: ""}"
    }

    data class ProgressSummary(val totalCards: Int, val everShownCount: Int, val todayShownCount: Int)

    data class Batch(val cards: List<VerbDrillCard>, val excludedCardIds: Set<String>)

    fun filteredCards(cards: List<VerbDrillCard>, selection: Selection): List<VerbDrillCard> =
        cards.filter { card ->
            (selection.tense == null || card.tense == selection.tense) &&
                (selection.group == null || card.group == selection.group) &&
                (selection.person == null || card.person == selection.person)
        }

    fun progressSummary(
        cards: List<VerbDrillCard>,
        progressMap: Map<String, VerbDrillComboProgress>,
        selection: Selection
    ): ProgressSummary {
        val progress = progressMap[selection.comboKey]
        return ProgressSummary(
            totalCards = filteredCards(cards, selection).size,
            everShownCount = progress?.everShownCardIds?.size ?: 0,
            todayShownCount = progress?.todayShownCardIds?.size ?: 0
        )
    }

    fun planBatch(
        cards: List<VerbDrillCard>,
        progressMap: Map<String, VerbDrillComboProgress>,
        selection: Selection,
        additionalShownCardIds: Set<String>,
        ignoreTodayShown: Boolean,
        sortByFrequency: Boolean,
        sessionSize: Int,
        shuffle: (List<VerbDrillCard>) -> List<VerbDrillCard> = { it.shuffled() }
    ): Batch {
        val todayShown = if (ignoreTodayShown) emptySet() else
            progressMap[selection.comboKey]?.todayShownCardIds ?: emptySet()
        val excludedCardIds = todayShown + additionalShownCardIds
        val remaining = filteredCards(cards, selection).filter { it.id !in excludedCardIds }
        val ordered = if (sortByFrequency) remaining.sortedBy { it.rank ?: Int.MAX_VALUE } else shuffle(remaining)
        return Batch(ordered.take(sessionSize), excludedCardIds)
    }
}
