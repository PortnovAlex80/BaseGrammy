package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.MixedReviewScheduler
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SubLessonType
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingMode

/**
 * Pure Kotlin card selection module extracted from TrainingViewModel.
 *
 * Handles sub-lesson scheduling, card selection for all training modes
 * (LESSON, ALL_SEQUENTIAL, ALL_MIXED), and boss battle card building.
 * Wraps [MixedReviewScheduler] for schedule construction and provides
 * a cache key to avoid redundant rebuilds.
 *
 * No Android dependencies — suitable for unit testing without Robolectric.
 */
class CardProvider(
    private val subLessonSize: Int = TrainingConfig.SUB_LESSON_SIZE_DEFAULT,
    private val subLessonSizeMin: Int = TrainingConfig.SUB_LESSON_SIZE_MIN,
    private val subLessonSizeMax: Int = TrainingConfig.SUB_LESSON_SIZE_MAX,
    private val eliteSizeMultiplier: Double = 1.25,
    private val eliteStepCount: Int = TrainingConfig.ELITE_STEP_COUNT
) {

    private var cachedScheduleKey: String = ""

    // ── Schedules ──────────────────────────────────────────────────────

    /**
     * Build sub-lesson schedules for all lessons using [MixedReviewScheduler].
     * Results are cached by a composite key derived from lesson IDs, card counts,
     * and block size. Calling again with the same inputs is a no-op.
     *
     * @return the updated (or existing) schedule map.
     */
    fun buildSchedules(
        lessons: List<Lesson>,
        existingSchedules: Map<String, LessonSchedule>
    ): Map<String, LessonSchedule> {
        val lessonKey = lessons.joinToString("|") { "${it.id}:${it.cards.size}" }
        val blockSize = subLessonSize.coerceIn(subLessonSizeMin, subLessonSizeMax)
        val key = "${lessonKey}|${blockSize}"
        if (key == cachedScheduleKey) return existingSchedules
        cachedScheduleKey = key
        return MixedReviewScheduler(blockSize).build(lessons)
    }

    // ── Session cards ──────────────────────────────────────────────────

    /**
     * Select cards for a training session based on [mode].
     *
     * For [TrainingMode.LESSON] the method uses pre-built schedules to pick
     * the sub-lesson at [activeSubLessonIndex] and filters out hidden cards.
     * For [TrainingMode.ALL_SEQUENTIAL] and [TrainingMode.ALL_MIXED] cards
     * are collected across all lessons and paginated into blocks.
     *
     * @param lessons            all loaded lessons
     * @param mode               current training mode
     * @param selectedLessonId   the active lesson (used only in LESSON mode)
     * @param schedules          pre-built schedule map from [buildSchedules]
     * @param activeSubLessonIndex  which sub-lesson page to show
     * @param hiddenCardIds      card IDs that should be excluded
     * @param mastery            optional mastery state for the selected lesson
     *                           (used to calculate completed sub-lesson count)
     * @return a [CardSetResult] with the selected cards and metadata
     */
    fun buildSessionCards(
        lessons: List<Lesson>,
        mode: TrainingMode,
        selectedLessonId: String?,
        schedules: Map<String, LessonSchedule>,
        activeSubLessonIndex: Int,
        hiddenCardIds: Set<String>,
        mastery: LessonMasteryState? = null
    ): CardSetResult {
        if (mode == TrainingMode.LESSON) {
            return buildLessonSessionCards(
                lessons, selectedLessonId, schedules,
                activeSubLessonIndex, hiddenCardIds, mastery
            )
        }

        val lessonCards = when (mode) {
            TrainingMode.ALL_SEQUENTIAL ->
                lessons.flatMap { it.cards }.filter { it.id !in hiddenCardIds }
            TrainingMode.ALL_MIXED -> {
                val reviewLimit = 300
                lessons.flatMap { it.allCards }
                    .filter { it.id !in hiddenCardIds }
                    .shuffled()
                    .take(reviewLimit)
            }
            else -> emptyList()
        }

        val blockSize = subLessonSize.coerceIn(subLessonSizeMin, subLessonSizeMax)
        val subCount = if (lessonCards.isEmpty()) 0
        else (lessonCards.size + blockSize - 1) / blockSize
        val activeIdx = activeSubLessonIndex.coerceIn(0, (subCount - 1).coerceAtLeast(0))
        val blockStart = activeIdx * blockSize
        val block = lessonCards.drop(blockStart).take(blockSize)

        return CardSetResult(
            cards = block,
            subLessonTotal = block.size,
            subLessonCount = subCount,
            activeSubLessonIndex = activeIdx,
            completedSubLessonCount = 0,
            subLessonTypes = emptyList()
        )
    }

    // ── Boss cards ─────────────────────────────────────────────────────

    /**
     * Build the card list for a boss battle.
     *
     * @param lessons          all loaded lessons
     * @param type             boss type (LESSON, MEGA, or ELITE)
     * @param selectedLessonId the active lesson ID (required for LESSON and MEGA)
     * @param selectedIndex    index of the selected lesson in the lessons list
     * @return shuffled card list for the boss session, or empty if unavailable
     */
    fun buildBossCards(
        lessons: List<Lesson>,
        type: BossType,
        selectedLessonId: String?,
        selectedIndex: Int
    ): List<SentenceCard> {
        val maxBossCards = 300
        return when (type) {
            BossType.LESSON -> {
                val lessonCards = lessons
                    .firstOrNull { it.id == selectedLessonId }
                    ?.cards ?: emptyList()
                lessonCards.shuffled().take(maxBossCards)
            }
            BossType.MEGA -> {
                if (selectedIndex <= 0) emptyList()
                else lessons.take(selectedIndex)
                    .flatMap { it.cards }
                    .shuffled()
                    .take(maxBossCards)
            }
            BossType.ELITE -> {
                val eliteSize = eliteSubLessonSize() * eliteStepCount
                lessons.flatMap { it.cards }.shuffled().take(eliteSize)
            }
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────

    private fun buildLessonSessionCards(
        lessons: List<Lesson>,
        selectedLessonId: String?,
        schedules: Map<String, LessonSchedule>,
        activeSubLessonIndex: Int,
        hiddenCardIds: Set<String>,
        mastery: LessonMasteryState?
    ): CardSetResult {
        val schedule = schedules[selectedLessonId]
        val subLessons = schedule?.subLessons.orEmpty()
        val subCount = subLessons.size

        val completedCount = calculateCompletedSubLessons(
            subLessons, mastery, selectedLessonId, lessons
        )

        val activeIdx = activeSubLessonIndex.coerceIn(
            0, (subCount - 1).coerceAtLeast(0)
        )
        val subLesson = subLessons.getOrNull(activeIdx)
        val cards = (subLesson?.cards ?: emptyList())
            .filter { it.id !in hiddenCardIds }

        return CardSetResult(
            cards = cards,
            subLessonTotal = cards.size,
            subLessonCount = subCount,
            activeSubLessonIndex = activeIdx,
            completedSubLessonCount = completedCount,
            subLessonTypes = subLessons.map { it.type }
        )
    }

    /**
     * Count how many sub-lessons are fully completed based on shown card IDs.
     * A sub-lesson is considered completed when every card from the main pool
     * in that sub-lesson has been shown at least once.
     */
    private fun calculateCompletedSubLessons(
        subLessons: List<ScheduledSubLesson>,
        mastery: LessonMasteryState?,
        lessonId: String?,
        lessons: List<Lesson>
    ): Int {
        if (lessonId == null || mastery == null || mastery.shownCardIds.isEmpty()) return 0

        val lessonCardIds = lessons
            .firstOrNull { it.id == lessonId }
            ?.cards
            ?.map { it.id }
            ?.toSet()
            ?: return 0

        var completed = 0
        for (subLesson in subLessons) {
            val allCardsShown = subLesson.cards.all { card ->
                !lessonCardIds.contains(card.id) || mastery.shownCardIds.contains(card.id)
            }
            if (allCardsShown) {
                completed++
            } else {
                break
            }
        }
        return completed
    }

    private fun eliteSubLessonSize(): Int {
        return kotlin.math.ceil(subLessonSize * eliteSizeMultiplier).toInt()
    }
}

/**
 * Result of card selection for a training session.
 * Contains the selected cards and sub-lesson metadata needed by the UI.
 */
data class CardSetResult(
    val cards: List<SentenceCard>,
    val subLessonTotal: Int,
    val subLessonCount: Int,
    val activeSubLessonIndex: Int,
    val completedSubLessonCount: Int,
    val subLessonTypes: List<SubLessonType>
)
