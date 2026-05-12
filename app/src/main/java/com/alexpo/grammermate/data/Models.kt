package com.alexpo.grammermate.data

data class Language(
    val id: String,
    val displayName: String
)

data class Lesson(
    val id: String,
    val languageId: String,
    val title: String,
    val cards: List<SentenceCard>,
    val drillCards: List<SentenceCard> = emptyList()
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
    override val id: String,
    override val promptRu: String,
    override val acceptedAnswers: List<String>,
    val tense: String? = null
) : SessionCard

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
    val importedAt: Long,
    val displayName: String? = null
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
    val bossMegaRewards: Map<String, String> = emptyMap(),
    val voiceActiveMs: Long = 0L,
    val voiceWordCount: Int = 0,
    val hintCount: Int = 0,
    val eliteStepIndex: Int = 0,
    val eliteBestSpeeds: List<Double> = emptyList(),
    val currentScreen: String = "HOME",
    val activePackId: String? = null,
    val dailyLevel: Int = 0,
    val dailyTaskIndex: Int = 0,
    val dailyCursor: DailyCursorState = DailyCursorState()
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

/**
 * Данные о streak (ежедневных занятиях)
 */
data class StreakData(
    val languageId: String,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletionDateMs: Long? = null,
    val totalSubLessonsCompleted: Int = 0
)

enum class DailyBlockType { TRANSLATE, VOCAB, VERBS }

sealed class DailyTask {
    abstract val id: String
    abstract val blockType: DailyBlockType

    data class TranslateSentence(
        override val id: String,
        val card: SentenceCard,
        val inputMode: InputMode
    ) : DailyTask() {
        override val blockType = DailyBlockType.TRANSLATE
    }

    data class VocabFlashcard(
        override val id: String,
        val word: VocabWord,
        val direction: VocabDrillDirection
    ) : DailyTask() {
        override val blockType = DailyBlockType.VOCAB
    }

    data class ConjugateVerb(
        override val id: String,
        val card: VerbDrillCard,
        val inputMode: InputMode
    ) : DailyTask() {
        override val blockType = DailyBlockType.VERBS
    }
}

data class DailySessionState(
    val active: Boolean = false,
    val tasks: List<DailyTask> = emptyList(),
    val taskIndex: Int = 0,
    val blockIndex: Int = 0,
    val level: Int = 0,
    val finishedToken: Boolean = false
)

data class DailyCursorState(
    val sentenceOffset: Int = 0,        // cards shown in current lesson (0, 10, 20, ...)
    val currentLessonIndex: Int = 0,    // which lesson in the pack (0-based)
    val verbOffset: Int = 0,            // verb cards shown for current tenses (0, 10, 20, ...)
    val lastSessionHash: Int = 0,       // hash of last completed session for "repeat" cache
    val firstSessionDate: String = "",  // ISO date (yyyy-MM-dd) of the first session of the day
    val firstSessionSentenceCardIds: List<String> = emptyList(),  // card IDs from first session's block 1
    val firstSessionVerbCardIds: List<String> = emptyList()       // card IDs from first session's block 3
)
