package com.alexpo.grammermate.feature.progress

import android.app.Application
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.PackLessonProgressState
import com.alexpo.grammermate.data.PackLessonProgressStoreImpl
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.SubLessonType
import com.alexpo.grammermate.shared.TrainingStateAccess
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Focused tests of the single lesson-checkpoint resume rule
 * ([ProgressTracker.resolveLessonCheckpointResume]): the stored checkpoint
 * index wins over completed-sub-lesson fallback, the index is clamped into the
 * current schedule, and the returned checkpoint carries the counters/timer that
 * lesson selection applies to the session.
 */
@RunWith(RobolectricTestRunner::class)
class ProgressTrackerCheckpointResumeTest {

    private val lessonId = LessonId("lesson")
    private val subLessons = listOf(
        ScheduledSubLesson(SubLessonType.NEW_ONLY, emptyList()),
        ScheduledSubLesson(SubLessonType.NEW_ONLY, emptyList()),
        ScheduledSubLesson(SubLessonType.NEW_ONLY, emptyList())
    )

    private fun tracker(app: Application = RuntimeEnvironment.getApplication()) = ProgressTracker(
        stateAccess = mockk<TrainingStateAccess>(relaxed = true),
        masteryStore = mockk(relaxed = true),
        progressStore = mockk(relaxed = true),
        lessonStore = mockk(relaxed = true),
        packDailyCursorStore = mockk(relaxed = true),
        packLessonProgressStore = PackLessonProgressStoreImpl(app)
    )

    private fun storeCheckpoint(
        app: Application = RuntimeEnvironment.getApplication(),
        saved: PackLessonProgressState.LessonProgress
    ) {
        PackLessonProgressStoreImpl(app).savePackProgress(
            PackLessonProgressState("resume-pack", mapOf("lesson" to saved))
        )
    }

    @Test
    fun storedCheckpointIndexWinsAndCarriesCountersAndTimer() {
        val app = RuntimeEnvironment.getApplication()
        val saved = PackLessonProgressState.LessonProgress(
            currentIndex = 4, correctCount = 7, incorrectCount = 2,
            activeTimeMs = 90_000L, activeSubLessonIndex = 2
        )
        storeCheckpoint(app, saved)

        val resume = tracker(app).resolveLessonCheckpointResume("resume-pack", lessonId, subLessons, completedSubLessons = 1)

        assertNotNull(resume.checkpoint)
        assertEquals(2, resume.activeSubLessonIndex)
        assertEquals(4, resume.checkpoint!!.currentIndex)
        assertEquals(7, resume.checkpoint!!.correctCount)
        assertEquals(2, resume.checkpoint!!.incorrectCount)
        assertEquals(90_000L, resume.checkpoint!!.activeTimeMs)
    }

    @Test
    fun missingCheckpointFallsBackToFirstIncompleteSubLesson() {
        val app = RuntimeEnvironment.getApplication()

        val resume = tracker(app).resolveLessonCheckpointResume("resume-pack", lessonId, subLessons, completedSubLessons = 1)

        assertNull(resume.checkpoint)
        assertEquals(1, resume.activeSubLessonIndex)
    }

    @Test
    fun checkpointIndexBeyondTheCurrentScheduleClampsToItsLastSubLesson() {
        val app = RuntimeEnvironment.getApplication()
        // The schedule shrank to 2 sub-lessons after the checkpoint stored index 6.
        storeCheckpoint(app, PackLessonProgressState.LessonProgress(activeSubLessonIndex = 6))

        val resume = tracker(app).resolveLessonCheckpointResume(
            "resume-pack", lessonId, subLessons.take(2), completedSubLessons = 0
        )

        assertEquals(1, resume.activeSubLessonIndex)
    }

    @Test
    fun emptyScheduleAndNoPackBothClampToZero() {
        val app = RuntimeEnvironment.getApplication()
        val t = tracker(app)

        assertEquals(0, t.resolveLessonCheckpointResume("resume-pack", lessonId, emptyList(), 3).activeSubLessonIndex)
        // No active pack: nothing persisted to resume from, index stays the clamped fallback.
        val noPack = t.resolveLessonCheckpointResume(null, lessonId, subLessons, completedSubLessons = 5)
        assertNull(noPack.checkpoint)
        assertEquals(2, noPack.activeSubLessonIndex)
    }
}
