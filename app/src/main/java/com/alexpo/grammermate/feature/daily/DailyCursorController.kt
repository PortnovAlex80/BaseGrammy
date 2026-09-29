package com.alexpo.grammermate.feature.daily

import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.PackDailyCursorState
import com.alexpo.grammermate.data.PackDailyCursorStore
import com.alexpo.grammermate.data.VerbDrillCsvParser

/** Pack-scoped cursor persistence and lesson/verb offset policy. */
internal class DailyCursorController(
    private val store: PackDailyCursorStore,
    private val lessons: LessonStore
) {
    fun getPackCursor(packId: String?): PackDailyCursorState =
        packId?.let { store.loadPackCursor(it) ?: PackDailyCursorState.forPack(it) }
            ?: PackDailyCursorState.forPack("unknown")

    fun getCursor(packId: String?): DailyCursorState = getPackCursor(packId).toDailyCursor()

    /** Persist legacy data only when the selected pack has no cursor yet. */
    fun initialize(packId: String?, legacy: DailyCursorState): DailyCursorState? {
        if (packId == null) return null
        val stored = store.loadPackCursor(packId)
        return when {
            stored != null -> stored.toDailyCursor()
            legacy.hasMeaningfulData() -> {
                save(packId, legacy)
                legacy
            }
            else -> DailyCursorState()
        }
    }

    /** Returns false without writing when no pack is selected. */
    fun save(packId: String?, cursor: DailyCursorState): Boolean {
        if (packId == null) return false
        store.savePackCursor(cursor.toPackCursor(packId))
        return true
    }

    fun delete(packId: String?) {
        if (packId == null) return
        store.deletePackCursor(packId)
        store.invalidateCache()
    }

    /** Compute the next cursor; the caller decides when to persist it. */
    fun advance(
        cursor: DailyCursorState,
        packId: String?,
        languageId: String,
        sentenceCount: Int,
        sessionSize: Int
    ): DailyCursorState {
        val activePackId = packId ?: return cursor
        val totalLessonCount = lessons.getLessonCount(activePackId, languageId)
        if (totalLessonCount == 0) return cursor

        var sentenceOffset = cursor.sentenceOffset + sentenceCount
        var currentLessonIndex = cursor.currentLessonIndex
        val currentLessonId = lessons.getLessonIdAtIndex(activePackId, languageId, currentLessonIndex)
        if (currentLessonId != null) {
            val cardCount = lessons.getCardsForLesson(activePackId, languageId, currentLessonId).size
            if (sentenceOffset >= cardCount) {
                currentLessonIndex++
                sentenceOffset = 0
                if (currentLessonIndex >= totalLessonCount) currentLessonIndex = 0
            }
        }

        val effectiveLevel = currentLessonIndex + 1
        val cumulativeTenses = lessons.getCumulativeTenses(activePackId, effectiveLevel)
        val totalVerbPoolSize = countVerbPool(activePackId, languageId, cumulativeTenses)
        val incrementedVerbOffset = cursor.verbOffset + sessionSize
        val verbOffset = if (totalVerbPoolSize > 0 && incrementedVerbOffset >= totalVerbPoolSize) {
            0
        } else incrementedVerbOffset

        return cursor.copy(
            currentLessonIndex = currentLessonIndex,
            sentenceOffset = sentenceOffset,
            verbOffset = verbOffset
        )
    }

    private fun countVerbPool(packId: String, languageId: String, activeTenses: List<String>): Int {
        if (activeTenses.isEmpty()) return 0
        var count = 0
        for (file in lessons.getVerbDrillFiles(packId, languageId)) {
            try {
                val parsed = file.bufferedReader().use { VerbDrillCsvParser.parse(it).data } ?: continue
                count += parsed.count { it.tense != null && it.tense in activeTenses }
            } catch (_: Exception) {
                // An unreadable file cannot contribute to the active verb pool.
            }
        }
        return count
    }

    private fun PackDailyCursorState.toDailyCursor() = DailyCursorState(
        sentenceOffset = sentenceOffset,
        currentLessonIndex = currentLessonIndex,
        lastSessionHash = lastSessionHash,
        firstSessionDate = firstSessionDate,
        firstSessionSentenceCardIds = firstSessionSentenceCardIds,
        firstSessionVerbCardIds = firstSessionVerbCardIds,
        firstSessionLessonId = firstSessionLessonId,
        verbOffset = verbOffset
    )

    private fun DailyCursorState.toPackCursor(packId: String) = PackDailyCursorState(
        packId = packId,
        sentenceOffset = sentenceOffset,
        currentLessonIndex = currentLessonIndex,
        lastSessionHash = lastSessionHash,
        firstSessionDate = firstSessionDate,
        firstSessionSentenceCardIds = firstSessionSentenceCardIds,
        firstSessionVerbCardIds = firstSessionVerbCardIds,
        firstSessionLessonId = firstSessionLessonId,
        verbOffset = verbOffset
    )

    private fun DailyCursorState.hasMeaningfulData(): Boolean =
        sentenceOffset > 0 || currentLessonIndex > 0 || lastSessionHash != 0 ||
            firstSessionDate.isNotEmpty() || firstSessionSentenceCardIds.isNotEmpty() ||
            firstSessionVerbCardIds.isNotEmpty() || verbOffset > 0
}
