package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.MixedReviewScheduler
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.ui.TrainingUiState
import kotlinx.coroutines.flow.StateFlow

interface TrainingStateAccess {
    val uiState: StateFlow<TrainingUiState>
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}

class DailySessionHelper(
    private val stateAccess: TrainingStateAccess
) {
    fun startDailyPipeline(
        lessonIndex: Int,
        subLessonIndex: Int,
        totalSubLessons: Int,
        globalPosition: Int
    ) {
        stateAccess.updateState { state ->
            state.copy(
                dailySession = state.dailySession.copy(
                    active = true,
                    lessonIndex = lessonIndex,
                    subLessonIndex = subLessonIndex,
                    totalSubLessons = totalSubLessons,
                    globalSubLessonPosition = globalPosition,
                    finishedToken = false
                )
            )
        }
        stateAccess.saveProgress()
    }

    fun getCurrentLessonIndex(): Int {
        return stateAccess.uiState.value.dailySession.lessonIndex
    }

    fun getCurrentSubLessonIndex(): Int {
        return stateAccess.uiState.value.dailySession.subLessonIndex
    }

    fun advanceToNextSubLesson(
        lessons: List<Lesson>,
        schedules: Map<String, LessonSchedule>
    ): Boolean {
        val ds = stateAccess.uiState.value.dailySession
        if (!ds.active) return false

        // Move to next sub-lesson
        var newLessonIndex = ds.lessonIndex
        var newSubLessonIndex = ds.subLessonIndex + 1
        val lesson = lessons.getOrNull(newLessonIndex)
        val schedule = lesson?.let { schedules[it.id] }
        val subLessons = schedule?.subLessons.orEmpty()

        if (newSubLessonIndex >= subLessons.size) {
            // Move to next lesson
            newLessonIndex++
            newSubLessonIndex = 0

            if (newLessonIndex >= lessons.size) {
                endSession()
                return false
            }
        }

        // Calculate global position
        var globalPos = 0
        for (i in 0 until newLessonIndex) {
            globalPos += schedules[lessons.getOrNull(i)?.id]?.subLessons?.size ?: 0
        }
        globalPos += newSubLessonIndex + 1

        stateAccess.updateState {
            it.copy(
                dailySession = it.dailySession.copy(
                    lessonIndex = newLessonIndex,
                    subLessonIndex = newSubLessonIndex,
                    globalSubLessonPosition = globalPos
                )
            )
        }
        stateAccess.saveProgress()
        return true
    }

    fun endSession() {
        stateAccess.updateState { state ->
            state.copy(
                dailySession = state.dailySession.copy(
                    active = false,
                    finishedToken = true
                )
            )
        }
        stateAccess.saveProgress()
    }

    fun isSessionComplete(): Boolean {
        val ds = stateAccess.uiState.value.dailySession
        return !ds.active || ds.finishedToken
    }

    fun getProgress(): DailyPipelineProgress {
        val ds = stateAccess.uiState.value.dailySession
        return DailyPipelineProgress(
            lessonIndex = ds.lessonIndex,
            subLessonIndex = ds.subLessonIndex,
            totalSubLessons = ds.totalSubLessons,
            globalPosition = ds.globalSubLessonPosition
        )
    }
}

data class DailyPipelineProgress(
    val lessonIndex: Int,
    val subLessonIndex: Int,
    val totalSubLessons: Int,
    val globalPosition: Int
)
