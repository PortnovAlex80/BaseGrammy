package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerbDrillDeckPlannerTest {
    private val selection = VerbDrillDeckPlanner.Selection("Presente", "are", "io")
    private val cards = listOf(
        card("first", 2),
        card("second", 1),
        card("third", null),
        card("fourth", 1),
        card("other-person", 0, person = "tu"),
        card("other-group", 0, group = "ere")
    )
    private val progress = mapOf(
        selection.comboKey to VerbDrillComboProgress(
            group = "are",
            tense = "Presente",
            totalCards = 99,
            everShownCardIds = setOf("first", "deleted-card"),
            todayShownCardIds = setOf("first", "deleted-card")
        )
    )

    @Test
    fun filters_all_three_dimensions_and_uses_saved_set_counts() {
        assertEquals("are|Presente|io", selection.comboKey)
        assertEquals(listOf("first", "second", "third", "fourth"),
            VerbDrillDeckPlanner.filteredCards(cards, selection).map { it.id })
        assertEquals(
            VerbDrillDeckPlanner.ProgressSummary(4, 2, 2),
            VerbDrillDeckPlanner.progressSummary(cards, progress, selection)
        )
    }

    @Test
    fun continue_excludes_progress_and_saved_ids_while_preserving_rank_ties() {
        val batch = plan(additional = setOf("second"), ignoreToday = false, frequency = true)

        assertEquals(setOf("first", "deleted-card", "second"), batch.excludedCardIds)
        assertEquals(listOf("fourth", "third"), batch.cards.map { it.id })
    }

    @Test
    fun fresh_start_ignores_today_progress_but_honors_explicit_exclusions() {
        val batch = plan(additional = setOf("second"), ignoreToday = true, frequency = true)

        assertEquals(setOf("second"), batch.excludedCardIds)
        assertEquals(listOf("fourth", "first", "third"), batch.cards.map { it.id })
    }

    @Test
    fun random_batch_only_shuffles_eligible_cards_and_respects_size() {
        var shuffledIds: List<String> = emptyList()
        val batch = VerbDrillDeckPlanner.planBatch(
            cards, progress, selection, emptySet(), false, false, 2,
            shuffle = { eligible ->
                shuffledIds = eligible.map { it.id }
                eligible.reversed()
            }
        )

        assertEquals(listOf("second", "third", "fourth"), shuffledIds)
        assertEquals(listOf("fourth", "third"), batch.cards.map { it.id })
    }

    @Test
    fun exhausted_selection_has_no_cards() {
        val batch = plan(additional = setOf("second", "third", "fourth"), ignoreToday = false, frequency = true)

        assertTrue(batch.cards.isEmpty())
    }

    private fun plan(additional: Set<String>, ignoreToday: Boolean, frequency: Boolean) =
        VerbDrillDeckPlanner.planBatch(cards, progress, selection, additional, ignoreToday, frequency, 5)

    private fun card(id: String, rank: Int?, group: String = "are", person: String = "io") = VerbDrillCard(
        id = id,
        promptRu = id,
        answer = id,
        tense = "Presente",
        group = group,
        person = person,
        rank = rank
    )
}
