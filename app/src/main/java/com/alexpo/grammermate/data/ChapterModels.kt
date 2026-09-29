package com.alexpo.grammermate.data

data class LessonLadderRow(
    val index: Int,
    val lessonId: LessonId,
    val title: String,
    val uniqueCardShows: Int?,
    val daysSinceLastShow: Int?,
    val intervalLabel: String?
)

/**
 * Represents a chapter within a lesson pack for narrative grammar learning.
 * Chapters group lessons into a story progression.
 *
 * @param chapterId Unique chapter identifier within the pack. Must not be blank.
 * @param order Display order. Lower numbers appear first.
 * @param title Chapter title (e.g., "Before Language").
 * @param subtitle Optional subtitle or description.
 * @param storyFile Markdown filename within the pack (e.g., "chapter0_story.md"). Null if no story.
 * @param lessons List of lesson IDs in this chapter, in order.
 */
data class Chapter(
    val chapterId: String,
    val order: Int,
    val title: String,
    val subtitle: String? = null,
    val storyFile: String? = null,
    val lessons: List<String> = emptyList()
) {
    init {
        require(chapterId.isNotBlank()) { "chapterId must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(lessons.all { it.isNotBlank() }) { "all lesson IDs must be non-blank" }
    }
}

/**
 * Tracks progress through a chapter. Pack-scoped.
 *
 * THE single completion rule (Phase 3, item 3.5): a lesson is completed iff
 * its mastery row has completedAtMs stamped — which happens when
 * uniqueCardShows >= min(effectiveCardCount, 150). intervalStepIndex is an
 * SRS spacing signal and is NOT a completion criterion.
 *
 * @param chapterId Links to Chapter.chapterId.
 * @param lessonsStarted Number of lessons with uniqueCardShows > 0. >= 0.
 * @param lessonsCompleted Number of lessons with completedAtMs != null. >= 0.
 * @param totalLessons The chapter's FULL lesson count (chapter.lessons.size).
 * @param lastAccessedMs Epoch millis of last lesson activity in this chapter. 0 = never accessed.
 */
data class ChapterProgress(
    val chapterId: String,
    val lessonsStarted: Int = 0,
    val lessonsCompleted: Int = 0,
    val totalLessons: Int = 0,
    val lastAccessedMs: Long = 0L
) {
    init {
        require(lessonsStarted >= 0) { "lessonsStarted must be >= 0" }
        require(lessonsCompleted >= 0) { "lessonsCompleted must be >= 0" }
        require(lessonsStarted >= lessonsCompleted) { "lessonsStarted must be >= lessonsCompleted" }
    }

    /** Completed / total lesson count of the chapter (0 for story-only chapters). */
    val progress: Float
        get() = if (totalLessons == 0) 0f else lessonsCompleted.toFloat() / totalLessons

    companion object {
        /**
         * Creates a new ChapterProgress with zero values for a chapter.
         */
        fun forChapter(chapterId: String) = ChapterProgress(chapterId)
    }
}

/** Roadmap row model: a chapter with its live progress and status. */
data class ChapterCardUi(
    val chapter: Chapter,
    val progress: ChapterProgress,
    val status: ChapterStatus
)

/** Status of a chapter in the roadmap. */
enum class ChapterStatus {
    ACTIVE,     // Started but not completed
    DONE        // All lessons completed
}
