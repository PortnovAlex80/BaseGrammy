package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.MixedReviewScheduler
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SubLessonType
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.feature.progress.ProgressTracker

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
    private var subLessonSize: Int = TrainingConfig.SUB_LESSON_SIZE_DEFAULT,
    private val subLessonSizeMin: Int = TrainingConfig.SUB_LESSON_SIZE_MIN,
    private val subLessonSizeMax: Int = TrainingConfig.SUB_LESSON_SIZE_MAX,
    private var eliteSizeMultiplier: Double = TrainingConfig.ELITE_SIZE_MULTIPLIER,
    private val eliteStepCount: Int = TrainingConfig.ELITE_STEP_COUNT,
    private val progressTracker: ProgressTracker? = null
) {

    private var cachedScheduleKey: String = ""

    // ── Schedules ──────────────────────────────────────────────────────

    /**
     * Update the sub-lesson size at runtime (e.g. from AppConfig).
     * Invalidates the schedule cache so schedules are rebuilt with the new size.
     */
    fun setSubLessonSize(size: Int) {
        if (subLessonSize != size) {
            subLessonSize = size
            cachedScheduleKey = ""
        }
    }

    /**
     * Build sub-lesson schedules for all lessons using [MixedReviewScheduler].
     * Results are cached by a composite key derived from lesson IDs, card counts,
     * and block size. Calling again with the same inputs is a no-op.
     *
     * @return the updated (or existing) schedule map.
     */
    fun buildSchedules(
        lessons: List<Lesson>,
        existingSchedules: Map<LessonId, LessonSchedule>
    ): Map<LessonId, LessonSchedule> {
        val lessonKey = lessons.joinToString("|") { "${it.id}:${it.cards.size}" }
        val blockSize = subLessonSize
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
        selectedLessonId: LessonId?,
        schedules: Map<LessonId, LessonSchedule>,
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
                val reviewLimit = TrainingConfig.REVIEW_LIMIT
                lessons.flatMap { it.allCards }
                    .filter { it.id !in hiddenCardIds }
                    .shuffled()
                    .take(reviewLimit)
            }
            else -> emptyList()
        }

        val blockSize = subLessonSize
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
            // Clamp to total for completed lessons so roadmap shows CompletionCard
            completedSubLessonCount = if (mastery?.completedAtMs != null) subCount else 0,
            subLessonTypes = emptyList()
        )
    }

    // ── Boss cards ─────────────────────────────────────────────────────

    /**
     * Build a review card list for a completed lesson.
     * Returns all lesson cards (shuffled), filtered by hidden card IDs.
     *
     * @param lessons          all loaded lessons
     * @param selectedLessonId the active lesson ID
     * @param hiddenCardIds    card IDs that should be excluded
     * @return shuffled card list for the review session
     */
    fun buildReviewCards(
        lessons: List<Lesson>,
        selectedLessonId: LessonId?,
        hiddenCardIds: Set<String>
    ): List<SentenceCard> {
        val lessonCards = lessons
            .firstOrNull { it.id == selectedLessonId }
            ?.cards ?: emptyList()
        return lessonCards
            .filter { it.id !in hiddenCardIds }
            .shuffled()
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
        selectedLessonId: LessonId?,
        selectedIndex: Int
    ): List<SentenceCard> {
        val maxBossCards = TrainingConfig.MAX_BOSS_CARDS
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
        selectedLessonId: LessonId?,
        schedules: Map<LessonId, LessonSchedule>,
        activeSubLessonIndex: Int,
        hiddenCardIds: Set<String>,
        mastery: LessonMasteryState?
    ): CardSetResult {
        val schedule = schedules[selectedLessonId]
        val subLessons = schedule?.subLessons.orEmpty()
        val subCount = subLessons.size

        val rawCompletedCount = progressTracker?.calculateCompletedSubLessons(
            subLessons, mastery, selectedLessonId, lessons, hiddenCardIds
        ) ?: 0
        // If lesson is mastery-completed, clamp to total so UI shows CompletionCard
        // instead of sub-lesson grid with locks on completed lessons.
        val completedCount = if (mastery?.completedAtMs != null) subCount else rawCompletedCount
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
