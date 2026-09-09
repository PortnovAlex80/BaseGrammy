package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.ChapterProgress
import com.alexpo.grammermate.data.ChapterStatus
import com.alexpo.grammermate.data.ChapterCardUi
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.MasteryStore

/**
 * THE home of chapter/roadmap reads (Phase 4, item 4.5 — extracted from
 * TrainingViewModel): computes chapter cards, chapter progress maps, status,
 * completion sets and the first-incomplete cursor from pack-scoped mastery.
 * All functions are pure over their inputs — the ViewModel supplies the
 * active pack/language and forwards the results into state.
 *
 * THE completion rule (Phase 3): a lesson is completed iff its mastery row
 * has completedAtMs stamped (uniqueCardShows >= min(effectiveCardCount, 150)).
 */
class ChapterRepository(
    private val lessonStore: LessonStore,
    private val masteryStore: MasteryStore
) {

    /** Mastery snapshot for every lesson of the pack (memory-only reads). */
    fun buildPackMasteryStates(packId: String, languageId: String): Map<String, LessonMasteryState> {
        val states = mutableMapOf<String, LessonMasteryState>()
        for (lid in lessonStore.getLessonIdsForPack(packId)) {
            states[lid] = masteryStore.getForPack(packId, lid)
                ?: LessonMasteryState(LessonId(lid), LanguageId(languageId))
        }
        return states
    }

    /** Live chapter cards (roadmap rows) — the same computation every consumer sees. */
    fun getChapterCards(
        chapters: List<Chapter>,
        packId: String,
        languageId: String
    ): List<ChapterCardUi> {
        if (chapters.isEmpty()) return emptyList()
        val masteryStates = buildPackMasteryStates(packId, languageId)
        return chapters.mapIndexed { index, chapter ->
            val progress = ChapterProgressCalculator.calculateChapterProgress(chapter, masteryStates)
            ChapterCardUi(chapter, progress, calculateChapterStatus(chapter, progress, index))
        }
    }

    /** Live progress for every chapter of the pack, keyed by chapterId. */
    fun computeAllChapterProgress(
        chapters: List<Chapter>,
        packId: String,
        languageId: String
    ): Map<String, ChapterProgress> {
        val masteryStates = buildPackMasteryStates(packId, languageId)
        return chapters.associate { ch ->
            ch.chapterId to ChapterProgressCalculator.calculateChapterProgress(ch, masteryStates)
        }
    }

    /** DONE only when every lesson of the chapter is stamped completed. */
    fun calculateChapterStatus(
        chapter: Chapter,
        progress: ChapterProgress,
        chapterIndex: Int
    ): ChapterStatus = when {
        chapter.lessons.isEmpty() -> ChapterStatus.ACTIVE // story-only chapter is never DONE
        progress.lessonsCompleted >= chapter.lessons.size -> ChapterStatus.DONE
        else -> ChapterStatus.ACTIVE // all chapters are accessible
    }

    /** Lesson IDs of the chapter whose mastery has completedAtMs stamped. */
    fun getCompletedLessonIds(chapter: Chapter, packId: String?): Set<String> =
        chapter.lessons.filter { lessonId ->
            val mastery = packId?.let { masteryStore.getForPack(it, lessonId) }
            mastery?.completedAtMs != null
        }.toSet()

    /**
     * First lesson of the chapter without completedAtMs (the continue-cursor).
     * Null when the whole chapter is complete.
     */
    fun getFirstIncompleteLesson(
        chapter: Chapter,
        packId: String?,
        languageId: String
    ): String? {
        for (lessonId in chapter.lessons) {
            val masteryState = packId?.let { masteryStore.getForPack(it, lessonId) }
                ?: LessonMasteryState(LessonId(lessonId), LanguageId(languageId))
            if (masteryState.completedAtMs == null) {
                return lessonId
            }
        }
        return null
    }
}
