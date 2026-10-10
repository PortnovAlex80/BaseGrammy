package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.CefrCalculator
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.WordMasteryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Snapshot of pack-scoped achievement counters shown in the profile dialog. */
data class ProfileStats(
    val cardsCompleted: Int,
    val wordsLearned: Int,
    val cefrLevel: String
)

/**
 * Owns the profile-stats projection: pack-scoped completed cards, learned-word
 * count and the CEFR rank derivation over the selected pack's vocabulary.
 * Reads are pack-scoped through the rebound [WordMasteryStore] provider, so the
 * ViewModel's pack rebind protocol needs no callback into this owner.
 *
 * The ViewModel keeps the refresh timing (selection, reset and restore chains);
 * this class only publishes the resulting [ProfileStats] state.
 */
internal class ProfileStatsSource(
    private val scope: CoroutineScope,
    private val lessonStore: LessonStore,
    private val masteryStore: MasteryStore,
    private val wordMasteryStoreProvider: () -> WordMasteryStore
) {
    private val _profileStats = MutableStateFlow(ProfileStats(0, 0, ""))
    val profileStats: StateFlow<ProfileStats> = _profileStats.asStateFlow()

    /**
     * The CEFR scan parses vocab CSVs from disk — refresh only on pack/language
     * changes, not on every mastery update. The scan runs on Dispatchers.IO
     * (the DrillFileManager cache makes repeat calls cheap); only the resulting
     * StateFlow value touches the UI.
     */
    fun refresh(languageId: String?, packId: String?) {
        scope.launch(Dispatchers.IO) {
            _profileStats.value = compute(languageId, packId)
        }
    }

    /** Pack-scoped projection of mastery and word-mastery data for one language/pack. */
    fun compute(languageId: String?, packId: String?): ProfileStats {
        languageId ?: return ProfileStats(0, 0, "")
        val wordMasteryStore = wordMasteryStoreProvider()

        // Cards completed: sum uniqueCardShows across all lessons for current pack
        val cardsCompleted = if (packId != null) {
            masteryStore.loadAll()["pack:$packId"]
                ?.values
                ?.sumOf { it.uniqueCardShows }
                ?: 0
        } else {
            0
        }

        // Words learned: count of mastered words in current pack
        val wordsLearned = wordMasteryStore.getMasteredCount()

        // CEFR level: based on rank of learned vocab words
        val cefrLevel = if (packId != null) {
            val allWords = lessonStore.getVocabWordsByRankRange(packId, languageId, 0, Int.MAX_VALUE)
            val learnedIds = wordMasteryStore.loadAll().filter { (_, state) -> state.isLearned }.keys
            val learnedRanks = allWords.filter { it.id in learnedIds }.map { it.rank }
            CefrCalculator.calculate(learnedRanks)
        } else {
            CefrCalculator.calculate(emptyList())
        }

        return ProfileStats(cardsCompleted, wordsLearned, cefrLevel)
    }
}
