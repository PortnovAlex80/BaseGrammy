package com.alexpo.grammermate.feature.progress

import android.util.Log
import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.LessonLadderCalculator
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.feature.daily.TrainingStateAccess

/**
 * Stateless module that recomputes flower visuals and ladder rows for all lessons.
 *
 * Called after any operation that may change mastery state: init, language/lesson
 * selection, boss completion, backup restore, and pack reload. The ViewModel
 * delegates [com.alexpo.grammermate.ui.TrainingViewModel.refreshFlowerStates] here.
 */
class FlowerRefresher(
    private val stateAccess: TrainingStateAccess,
    private val masteryStore: MasteryStore
) {

    private val logTag = "GrammarMate"

    /**
     * Recalculate flower states for every lesson and update UI state.
     *
     * Iterates all lessons, queries [MasteryStore] for each, computes a
     * [FlowerVisual] via [FlowerCalculator], builds ladder rows via
     * [LessonLadderCalculator], and writes the result into
     * [com.alexpo.grammermate.data.FlowerDisplayState] and
     * [com.alexpo.grammermate.data.NavigationState.ladderRows].
     */
    fun refreshFlowerStates() {
        val state = stateAccess.uiState.value
        val languageId = state.navigation.selectedLanguageId
        val lessons = state.navigation.lessons
        val nowMs = System.currentTimeMillis()

        val masteryMap = lessons.associate { lesson ->
            lesson.id to masteryStore.get(lesson.id.value, languageId.value)
        }

        val flowerStates = lessons.associate { lesson ->
            val mastery = masteryMap[lesson.id]
            val flower = FlowerCalculator.calculate(mastery, lesson.cards.size)
            Log.d(logTag, "Flower for lesson ${lesson.id}: mastery=${mastery?.uniqueCardShows ?: 0}, state=${flower.state}, scale=${flower.scaleMultiplier}")
            lesson.id.value to flower
        }

        val currentLessonId = state.navigation.selectedLessonId
        val currentFlower = currentLessonId?.let { flowerStates[it.value] }
        val currentShownCount = currentLessonId?.let { lessonId ->
            masteryMap[lessonId]?.shownCardIds?.size ?: 0
        } ?: 0

        val ladderRows = lessons.mapIndexed { index, lesson ->
            val mastery = masteryMap[lesson.id]
            val metrics = LessonLadderCalculator.calculate(mastery, nowMs)
            com.alexpo.grammermate.data.LessonLadderRow(
                index = index + 1,
                lessonId = lesson.id,
                title = lesson.title,
                uniqueCardShows = metrics.uniqueCardShows,
                daysSinceLastShow = metrics.daysSinceLastShow,
                intervalLabel = metrics.intervalLabel
            )
        }

        stateAccess.updateState {
            it.copy(
                flowerDisplay = it.flowerDisplay.copy(
                    lessonFlowers = flowerStates,
                    currentLessonFlower = currentFlower,
                    currentLessonShownCount = currentShownCount
                ),
                navigation = it.navigation.copy(ladderRows = ladderRows)
            )
        }
    }
}
