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
    private val subLessonSize: Int = TrainingConfig.SUB_LESSON_SIZE_DEFAULT,
    private val subLessonSizeMin: Int = TrainingConfig.SUB_LESSON_SIZE_MIN,
    private val subLessonSizeMax: Int = TrainingConfig.SUB_LESSON_SIZE_MAX,
    private val eliteSizeMultiplier: Double = TrainingConfig.ELITE_SIZE_MULTIPLIER,
    private val eliteStepCount: Int = TrainingConfig.ELITE_STEP_COUNT,
    private val progressTracker: ProgressTracker? = null
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
        existingSchedules: Map<LessonId, LessonSchedule>
    ): Map<LessonId, LessonSchedule> {
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

    // ── Mix Challenge cards ─────────────────────────────────────────────

    /**
     * Build a card list for the Mix Challenge (interleaved practice) mode.
     *
     * Selects cards from multiple lessons, maximizing tense alternation so
     * consecutive cards come from different tenses whenever possible.
     * Research shows interleaved practice produces ~43% better long-term
     * retention compared to blocked practice.
     *
     * @param lessons         all loaded lessons for the current language
     * @param startedLessonIds  IDs of lessons the user has started (non-locked)
     * @param count           number of cards to return (default 10)
     * @return shuffled & alternation-sorted card list, or empty if fewer than
     *         3 started lessons with cards exist
     */
    fun buildMixChallengeCards(
        lessons: List<Lesson>,
        startedLessonIds: Set<LessonId>,
        count: Int = 10
    ): List<SentenceCard> {
        // Only use lessons the user has started
        val activeLessons = lessons.filter { it.id in startedLessonIds }
        if (activeLessons.size < 2) return emptyList()

        // Build candidate pool: pick up to 5 cards per lesson from main pool
        val candidates = mutableListOf<SentenceCard>()
        for (lesson in activeLessons) {
            val cards = lesson.mainPoolCards.shuffled().take(5)
            candidates.addAll(cards)
        }

        if (candidates.isEmpty()) return emptyList()

        // Apply maximum alternation sort
        val sorted = applyAlternationSort(candidates)

        return sorted.take(count)
    }

    /**
     * Sort cards so no two consecutive cards share the same tense.
     * Uses a greedy round-robin approach grouped by tense.
     */
    private fun applyAlternationSort(cards: List<SentenceCard>): List<SentenceCard> {
        if (cards.size <= 1) return cards

        // Group cards by tense (null tense grouped under a sentinel)
        val byTense = cards.groupBy { it.tense ?: "__none__" }
        if (byTense.size == 1) return cards.shuffled() // all same tense, nothing to alternate

        // Sort groups largest-first for round-robin fairness
        val groups = byTense.values.sortedByDescending { it.size }.map { it.shuffled().toMutableList() }
        val result = mutableListOf<SentenceCard>()
        var lastGroupIndex = -1

        while (result.size < cards.size) {
            // Pick the group with the most remaining cards that isn't the last used group
            val candidate = groups
                .filterIndexed { idx, _ -> idx != lastGroupIndex }
                .maxByOrNull { it.size }

            if (candidate == null || candidate.isEmpty()) {
                // Fallback: just take from the last used group (only option left)
                val fallback = groups.firstOrNull { it.isNotEmpty() }
                if (fallback != null && fallback.isNotEmpty()) {
                    result.add(fallback.removeAt(0))
                }
                continue
            }

            val groupIdx = groups.indexOf(candidate)
            result.add(candidate.removeAt(0))
            lastGroupIndex = groupIdx
        }

        return result
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

        val completedCount = progressTracker?.calculateCompletedSubLessons(
            subLessons, mastery, selectedLessonId, lessons
        ) ?: 0

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
