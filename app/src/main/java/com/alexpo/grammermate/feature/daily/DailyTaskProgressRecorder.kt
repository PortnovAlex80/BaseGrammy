package com.alexpo.grammermate.feature.daily

import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.data.WordMasteryStore
import java.time.LocalDate

/** Persists progress for the actual card answered in a daily-practice block. */
internal class DailyTaskProgressRecorder(
    private val masteryStore: MasteryStore,
    private val verbStoreForPack: (String?) -> VerbDrillStore,
    private val wordStoreForPack: (String?) -> WordMasteryStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val today: () -> String = { LocalDate.now().toString() }
) {
    fun recordSentenceShow(packId: String, lessonId: String, cardId: String) {
        masteryStore.recordCardShowForPack(packId, lessonId, cardId)
    }

    fun recordVerbShow(packId: String, card: VerbDrillCard) {
        val store = verbStoreForPack(packId)
        val comboKey = "${card.group ?: ""}|${card.tense ?: ""}"
        val existing = store.loadProgress()[comboKey]
        val updated = VerbDrillComboProgress(
            group = card.group ?: "",
            tense = card.tense ?: "",
            totalCards = existing?.totalCards ?: 0,
            everShownCardIds = (existing?.everShownCardIds ?: emptySet()) + card.id,
            todayShownCardIds = (existing?.todayShownCardIds ?: emptySet()) + card.id,
            lastDate = today()
        )
        store.upsertComboProgress(comboKey, updated)
    }

    fun rateVocab(packId: String, wordId: String, rating: SrsRating): Int {
        val store = wordStoreForPack(packId)
        val current = store.getMastery(wordId) ?: WordMasteryState.new(wordId)
        val now = nowMs()
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val step = when (rating) {
            SrsRating.AGAIN -> 0
            SrsRating.HARD -> current.intervalStepIndex
            SrsRating.GOOD -> (current.intervalStepIndex + 1).coerceIn(0, maxStep)
            SrsRating.EASY -> (current.intervalStepIndex + 2).coerceIn(0, maxStep)
        }
        store.upsertMastery(current.copy(
            correctCount = current.correctCount + (if (rating != SrsRating.AGAIN) 1 else 0),
            incorrectCount = current.incorrectCount + (if (rating == SrsRating.AGAIN) 1 else 0),
            intervalStepIndex = step,
            lastReviewDateMs = now,
            nextReviewDateMs = WordMasteryState.computeNextReview(now, step),
            isLearned = step >= TrainingConfig.LEARNED_THRESHOLD
        ))
        return step
    }
}
