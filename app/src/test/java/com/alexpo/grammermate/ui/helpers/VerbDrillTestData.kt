package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillLastSessionState
import com.alexpo.grammermate.testharness.FakeVerbDrillStore

fun createTestVerbCards(count: Int): List<VerbDrillCard> {
    return (1..count).map { index ->
        VerbDrillCard(
            id = "test_verb_$index",
            promptRu = "Conjugate verb $index",
            answer = "io conjugate $index",
            verb = "verb_test",
            tense = "Presente",
            group = "regular_are",
            rank = index
        )
    }
}

fun setupLastSession(
    store: FakeVerbDrillStore,
    packId: String,
    tense: String,
    group: String,
    cardIds: List<String>,
    shownCardIds: Set<String> = emptySet(),
    currentIndex: Int = 0
) {
    store.saveLastSession(
        VerbDrillLastSessionState(
            selectedTense = tense,
            selectedGroup = group,
            sortByFrequency = false,
            todayShownCardIds = shownCardIds,
            sessionCardIds = cardIds,
            currentIndex = currentIndex,
            packId = packId
        )
    )
}
