package com.alexpo.grammermate.data

data class Language(
    val id: String,
    val displayName: String
)

data class Lesson(
    val id: String,
    val languageId: String,
    val title: String,
    val cards: List<SentenceCard>
) {
    companion object {
        const val MAIN_POOL_SIZE = SpacedRepetitionConfig.MASTERY_THRESHOLD // 150 cards
    }

    /**
     * Основной пул карточек для достижения мастери (первые 150 карточек).
     */
    val mainPoolCards: List<SentenceCard>
        get() = cards.take(MAIN_POOL_SIZE)

    /**
     * Резервный пул карточек (карточки после первых 150).
     * Используется в Review и Mix-уроках для предотвращения заученности.
     */
    val reservePoolCards: List<SentenceCard>
        get() = cards.drop(MAIN_POOL_SIZE)

    /**
     * Все карточки (основной пул + резерв).
     */
    val allCards: List<SentenceCard>
        get() = cards
}

data class SentenceCard(
    val id: String,
    val promptRu: String,
    val acceptedAnswers: List<String>
)

data class VocabEntry(
    val id: String,
    val lessonId: String,
    val languageId: String,
    val nativeText: String,
    val targetText: String,
    val isHard: Boolean = false
)

data class LessonPack(
    val packId: String,
    val packVersion: String,
    val languageId: String,
    val importedAt: Long
)

enum class StoryPhase {
    CHECK_IN,
    CHECK_OUT
}

data class StoryQuestion(
    val qId: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explain: String? = null
)

data class StoryQuiz(
    val storyId: String,
    val lessonId: String,
    val phase: StoryPhase,
    val text: String,
    val questions: List<StoryQuestion>
)

enum class TrainingMode {
    LESSON,
    ALL_SEQUENTIAL,
    ALL_MIXED
}

enum class BossType {
    LESSON,
    MEGA,
    ELITE
}

enum class BossReward {
    BRONZE,
    SILVER,
    GOLD
}

enum class SessionState {
    ACTIVE,
    PAUSED,
    AFTER_CHECK,
    HINT_SHOWN
}

enum class InputMode {
    VOICE,
    KEYBOARD,
    WORD_BANK
}

data class TrainingProgress(
    val languageId: String = "en",
    val mode: TrainingMode = TrainingMode.LESSON,
    val lessonId: String? = null,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val incorrectAttemptsForCard: Int = 0,
    val activeTimeMs: Long = 0L,
    val state: SessionState = SessionState.PAUSED,
    val bossLessonRewards: Map<String, String> = emptyMap(),
    val bossMegaReward: String? = null,
    val voiceActiveMs: Long = 0L,
    val voiceWordCount: Int = 0,
    val hintCount: Int = 0,
    val eliteStepIndex: Int = 0,
    val eliteBestSpeeds: List<Double> = emptyList()
)

/**
 * Состояние освоения урока (данные для расчёта "цветка")
 */
data class LessonMasteryState(
    val lessonId: String,
    val languageId: String,
    val uniqueCardShows: Int = 0,
    val totalCardShows: Int = 0,
    val lastShowDateMs: Long = 0L,
    val intervalStepIndex: Int = 0,
    val completedAtMs: Long? = null,
    val shownCardIds: Set<String> = emptySet()
)

/**
 * Состояние цветка для отображения в UI
 */
enum class FlowerState {
    LOCKED,
    SEED,
    SPROUT,
    BLOOM,
    WILTING,
    WILTED,
    GONE
}

/**
 * Визуальное представление цветка
 */
data class FlowerVisual(
    val state: FlowerState,
    val masteryPercent: Float,
    val healthPercent: Float,
    val scaleMultiplier: Float
)
