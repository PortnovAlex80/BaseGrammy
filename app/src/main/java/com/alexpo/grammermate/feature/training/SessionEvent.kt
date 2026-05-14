package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.SentenceCard

/**
 * Event types for [SessionRunner] methods.
 * Replaces [SessionCallbacks] — each method returns a list of events
 * instead of calling callbacks.
 *
 * Query-style callbacks that need return values use the callback-in-result
 * pattern (e.g. [CalculateCompletedSubLessons] with callback).
 */
sealed class SessionEvent {
    object SaveProgress : SessionEvent()
    object RefreshFlowerStates : SessionEvent()
    object UpdateStreak : SessionEvent()
    object BuildSessionCards : SessionEvent()
    object PlaySuccess : SessionEvent()
    object PlayError : SessionEvent()
    data class RecordCardShow(val card: SentenceCard) : SessionEvent()
    data class MarkSubLessonCardsShown(val cards: List<SentenceCard>) : SessionEvent()
    object CheckAndMarkLessonCompleted : SessionEvent()
    data class CalculateCompletedSubLessons(
        val subLessons: List<ScheduledSubLesson>,
        val mastery: LessonMasteryState?,
        val lessonId: String?,
        val callback: (Int) -> Unit
    ) : SessionEvent()
    data class GetMastery(val lessonId: String, val langId: String, val callback: (LessonMasteryState?) -> Unit) : SessionEvent()
    data class GetSchedule(val lessonId: String, val callback: (LessonSchedule?) -> Unit) : SessionEvent()
    data class RebuildSchedules(val lessons: List<Lesson>) : SessionEvent()
    data class Composite(val events: List<SessionEvent>) : SessionEvent()
}
