package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackId

/** Immutable target-pack snapshot used before rebuilding lesson schedules. */
internal data class PackSelectionPlan(
    val packId: PackId,
    val lessons: List<Lesson>,
    val lessonIds: List<String>
) {
    fun navigationFrom(current: NavigationState): NavigationState = current.copy(
        lessons = lessons,
        activePackId = packId,
        activePackLessonIds = lessonIds
    )

    fun scheduleLessons(): List<Lesson> =
        if (lessonIds.isEmpty()) lessons else lessons.filter { it.id.value in lessonIds }
}
