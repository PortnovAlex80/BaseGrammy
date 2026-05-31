package com.alexpo.grammermate.feature.progress

import android.util.Log
import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.ChapterProgress
import com.alexpo.grammermate.data.LessonMasteryState

/**
 * Calculator for chapter progress metrics.
 *
 * Computes progress tracking data for a chapter based on the mastery states
 * of its lessons. Progress is independent across chapters - a lesson in
 * Chapter 1 does not affect Chapter 2's progress.
 *
 * Computation rules:
 * - lessonsStarted: Count of lessons where uniqueCardShows > 0
 * - lessonsCompleted: Count of lessons where EITHER:
 *   - intervalStepIndex >= LEARNED_THRESHOLD (3) — advanced via Daily Review SRS
 *   - OR uniqueCardShows >= MASTERY_THRESHOLD (150) — completed all cards
 * - lastAccessedMs: Maximum lastShowDateMs from all lessons in the chapter
 *
 * Edge cases:
 * - Empty chapter (no lessons): Returns zero progress with lastAccessedMs = 0
 * - Single lesson chapter: Works correctly, counts that one lesson
 * - Lessons without mastery data: Treated as not started (uniqueCardShows = 0)
 */
object ChapterProgressCalculator {

    private const val TAG = "ChapterProgressCalc"

    /**
     * Calculate progress for a chapter based on lesson mastery states.
     *
     * @param chapter The chapter to calculate progress for
     * @param masteryStates Map of lessonId to LessonMasteryState for all lessons in the pack
     * @return ChapterProgress with computed metrics
     */
    fun calculateChapterProgress(
        chapter: Chapter,
        masteryStates: Map<String, LessonMasteryState>
    ): ChapterProgress {
        if (chapter.lessons.isEmpty()) {
            return ChapterProgress(
                chapterId = chapter.chapterId,
                lessonsStarted = 0,
                lessonsCompleted = 0,
                lastAccessedMs = 0L
            )
        }

        var startedCount = 0
        var completedCount = 0
        var lastAccessedMs = 0L

        for (lessonId in chapter.lessons) {
            val mastery = masteryStates[lessonId]

            // Count as started if any cards have been shown (uniqueCardShows > 0)
            if (mastery != null && mastery.uniqueCardShows > 0) {
                startedCount++
            }

            // Count as completed if the lesson was explicitly marked completed
            // (via markLessonCompleted after user finishes all sub-lessons).
            // This is separate from mastery/flower which tracks card repetition.
            val isCompleted = mastery?.completedAtMs != null
            if (isCompleted) {
                completedCount++
            }

            // Track the most recent activity across all lessons in the chapter
            if (mastery != null && mastery.lastShowDateMs > lastAccessedMs) {
                lastAccessedMs = mastery.lastShowDateMs
            }
        }

        Log.d(TAG, "calculate: chapter=${chapter.chapterId} started=$startedCount completed=$completedCount total=${chapter.lessons.size}")

        return ChapterProgress(
            chapterId = chapter.chapterId,
            lessonsStarted = startedCount,
            lessonsCompleted = completedCount,
            lastAccessedMs = lastAccessedMs
        )
    }
}
