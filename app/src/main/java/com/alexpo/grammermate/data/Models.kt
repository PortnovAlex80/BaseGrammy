package com.alexpo.grammermate.data

@JvmInline value class LessonId(val value: String)
@JvmInline value class LanguageId(val value: String)
@JvmInline value class PackId(val value: String)

data class Language(
    val id: LanguageId,
    val displayName: String
)

data class Lesson(
    val id: LessonId,
    val languageId: LanguageId,
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
    override val id: String,
    override val promptRu: String,
    override val acceptedAnswers: List<String>,
    val tense: String? = null
) : SessionCard

data class VocabEntry(
    val id: String,
    val lessonId: LessonId,
    val languageId: LanguageId,
    val nativeText: String,
    val targetText: String,
    val isHard: Boolean = false
)

data class LessonPack(
    val packId: PackId,
    val packVersion: String,
    val languageId: LanguageId,
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
    val lessonId: LessonId,
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
    HINT_SHOWN
}

enum class InputMode {
    VOICE,
    KEYBOARD,
    WORD_BANK
}

enum class SrsRating { AGAIN, HARD, GOOD, EASY }

/** Theme mode controlling light/dark/system appearance. */
enum class ThemeMode {
    /** Always use light theme. */
    LIGHT,
    /** Always use dark theme. */
    DARK,
    /** Follow system dark/light setting (default). */
    SYSTEM
}

/** Difficulty level controlling which hints are visible during practice. */
enum class HintLevel {
    /** All hints visible: verb info, word bank, first-letter hints. Current default. */
    EASY,
    /** Partial hints: infinitive+tense only, no word bank, keyboard/voice only. */
    MEDIUM,
    /** No hints: voice only, user must produce from Russian prompt alone. */
    HARD
}

/** UI mode for TrainingScreen — controls header, chips, completion callback. */
enum class TrainingScreenMode {
    NORMAL,           // Standard sub-lessons
    BOSS,             // Boss battle review
    BOSS_MEGA,        // Mega boss battle
    ELITE,            // Elite/daily step
    VERB_DRILL,       // Verb conjugation (chips: verb, tense)
    DAILY_TRANSLATE,  // Daily Practice block 1 (translation)
    DAILY_VERBS       // Daily Practice block 3 (verb conjugation with chips)
}

data class TrainingProgress(
    val languageId: LanguageId = LanguageId("en"),
    val mode: TrainingMode = TrainingMode.LESSON,
    val bossLessonRewards: Map<String, String> = emptyMap(),
    val bossMegaReward: String? = null,
    val bossMegaRewards: Map<String, String> = emptyMap(),
    val voiceActiveMs: Long = 0L,
    val voiceWordCount: Int = 0,
    val hintCount: Int = 0,
    val eliteStepIndex: Int = 0,
    val eliteBestSpeeds: List<Double> = emptyList(),
    val currentScreen: String = "HOME",
    val activePackId: PackId? = null,
    val dailyLevel: Int = 0,
    val dailyTaskIndex: Int = 0,
    val dailyCursor: DailyCursorState = DailyCursorState(),
    // Legacy fields - kept for backwards compatibility with existing progress files
    // These are no longer used; lesson progress is now in PackLessonProgressStore
    val lessonId: String? = null,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val incorrectAttemptsForCard: Int = 0,
    val activeTimeMs: Long = 0L,
    val state: SessionState = SessionState.PAUSED
)

/**
 * Состояние освоения урока (данные для расчёта "цветка")
 */
data class LessonMasteryState(
    val lessonId: LessonId,
    val languageId: LanguageId,
    val uniqueCardShows: Int = 0,
    val totalCardShows: Int = 0,
    val lastShowDateMs: Long = 0L,
    val intervalStepIndex: Int = 0,
    val completedAtMs: Long? = null,
    val shownCardIds: Set<String> = emptySet(),
    val cardEncounterCounts: Map<String, Int> = emptyMap()
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
/** Practice type for fire streak tracking. Each unique type per day earns one fire. */
enum class PracticeType {
    TRANSLATION,  // training sub-lesson, boss battle, daily block 1
    VOCAB,        // vocab drill standalone, daily block 2
    VERB          // verb drill standalone, daily block 3
}

enum class PomodoroPreset(val minutes: Int, val label: String) {
    QUICK(5, "Quick"),
    FOCUS(15, "Focus"),
    CLASSIC(20, "Classic")
}

enum class CardDifficultyRating {
    AGAIN,
    HARD,
    GOOD,
    EASY
}

data class PomodoroSessionStats(
    val cardsShown: Int = 0,
    val cardsCorrect: Int = 0,
    val cardsIncorrect: Int = 0,
    val difficultyRatings: Map<CardDifficultyRating, Int> = emptyMap(),
    val wordsPerMinute: Double = 0.0,
    val durationMinutes: Int = 0,
    val completedAtMs: Long = 0
)

data class PomodoroHistoryEntry(
    val id: String,
    val languageId: String,
    val packId: String? = null,
    val lessonId: String? = null,
    val completedAtMs: Long,
    val durationMinutes: Int,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val cardsShown: Int,
    val cardsCorrect: Int,
    val cardsIncorrect: Int,
    val wordsPerMinute: Double
)

data class PomodoroState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isComplete: Boolean = false,
    val selectedDurationMinutes: Int = 20,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val stats: PomodoroSessionStats = PomodoroSessionStats(),
    val showRatingPrompt: Boolean = false,
    val showExitConfirm: Boolean = false,
    val baselineCorrect: Int = 0,
    val baselineIncorrect: Int = 0
)

data class StreakData(
    val languageId: LanguageId,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletionDateMs: Long? = null,
    val totalSubLessonsCompleted: Int = 0,
    val completedTypesToday: Set<PracticeType> = emptySet(),
    val todayFireCount: Int = 0,
    val lastFireDateMs: Long? = null
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

/**
 * Represents a single block within a daily practice session.
 * Each block has a type (TRANSLATE, VOCAB, VERBS), a list of tasks,
 * and a completion flag.
 */
data class DailyBlock(
    val type: DailyBlockType,
    val tasks: List<DailyTask>,
    val isComplete: Boolean = false,
    val taskIndex: Int = 0
) {
    /** How the block is rendered in the UI. */
    val renderVia: BlockRenderVia
        get() = when (type) {
            DailyBlockType.TRANSLATE, DailyBlockType.VERBS -> BlockRenderVia.TRAINING_SCREEN
            DailyBlockType.VOCAB -> BlockRenderVia.INLINE
        }
}

/** How a block should be rendered. */
enum class BlockRenderVia {
    /** Navigate to TrainingScreen (TRANSLATE, VERBS). */
    TRAINING_SCREEN,
    /** Render inline within DailyPracticeScreen (VOCAB). */
    INLINE
}

data class DailySessionState(
    val active: Boolean = false,
    val blocks: List<DailyBlock> = emptyList(),
    val blockIndex: Int = 0,
    val level: Int = 0,
    val finishedToken: Boolean = false,
    val packId: String = ""
) {
    /** Get the current block, or null if session is not active or out of range. */
    val currentBlock: DailyBlock?
        get() = blocks.getOrNull(blockIndex)

    /** Get the current block type, or null. */
    val currentBlockType: DailyBlockType?
        get() = currentBlock?.type

    /** Total tasks across all blocks. */
    val totalTasks: Int
        get() = blocks.sumOf { it.tasks.size }
}

data class DailyCursorState(
    val sentenceOffset: Int = 0,        // cards shown in current lesson (0, 10, 20, ...)
    val currentLessonIndex: Int = 0,    // which lesson in the pack (0-based)
    val lastSessionHash: Int = 0,       // hash of last completed session for "repeat" cache
    val firstSessionDate: String = "",  // ISO date (yyyy-MM-dd) of the first session of the day
    val firstSessionSentenceCardIds: List<String> = emptyList(),  // card IDs from first session's block 1
    val firstSessionVerbCardIds: List<String> = emptyList(),      // card IDs from first session's block 3
    val verbOffset: Int = 0             // verb cards shown in current lesson (0, 10, 20, ...)
)

/**
 * Pack-scoped daily cursor state. Each pack maintains its own cursor position
 * to prevent cross-pack contamination when switching between lesson packs.
 *
 * Used by TASK-080: State isolation bug fix.
 */
data class PackDailyCursorState(
    val packId: String,                 // Pack identifier (e.g., "ru-en-v1", "ru-it-v1")
    val sentenceOffset: Int = 0,        // cards shown in current lesson (0, 10, 20, ...)
    val currentLessonIndex: Int = 0,    // which lesson in the pack (0-based)
    val lastSessionHash: Int = 0,       // hash of last completed session for "repeat" cache
    val firstSessionDate: String = "",  // ISO date (yyyy-MM-dd) of the first session of the day
    val firstSessionSentenceCardIds: List<String> = emptyList(),  // card IDs from first session's block 1
    val firstSessionVerbCardIds: List<String> = emptyList(),      // card IDs from first session's block 3
    val verbOffset: Int = 0             // verb cards shown in current lesson (0, 10, 20, ...)
) {
    companion object {
        /** Create default cursor state for a pack */
        fun forPack(packId: String) = PackDailyCursorState(packId = packId)
    }
}

/**
 * Pack-scoped lesson progress state.
 * Persists training progress for a specific lesson pack.
 * File: lesson_progress_{packId}.yaml
 *
 * @param packId Pack identifier (e.g., "en_word_order_a1")
 * @param lessonProgress Map of lessonId → LessonProgress containing:
 *   - currentIndex: Current card position in lesson
 *   - correctCount: Number of correct answers
 *   - incorrectCount: Number of incorrect answers
 *   - incorrectAttemptsForCard: Wrong attempts on current card
 *   - activeTimeMs: Time spent practicing (milliseconds)
 *   - state: SessionState (PAUSED/ACTIVE/COMPLETED)
 */
data class PackLessonProgressState(
    val packId: String,
    val lessonProgress: Map<String, LessonProgress> = emptyMap()
) {
    /**
     * Lesson-specific progress tracking.
     */
    data class LessonProgress(
        val currentIndex: Int = 0,
        val correctCount: Int = 0,
        val incorrectCount: Int = 0,
        val incorrectAttemptsForCard: Int = 0,
        val activeTimeMs: Long = 0L,
        val state: SessionState = SessionState.PAUSED
    )
}

data class SubmitResult(
    val accepted: Boolean,
    val hintShown: Boolean
)

data class NavigationState(
    val languages: List<Language> = emptyList(),
    val installedPacks: List<LessonPack> = emptyList(),
    val selectedLanguageId: LanguageId = LanguageId("en"),
    val activePackId: PackId? = null,
    val activePackLessonIds: List<String>? = null,
    val lessons: List<Lesson> = emptyList(),
    val selectedLessonId: LessonId? = null,
    val mode: TrainingMode = TrainingMode.LESSON,
    val userName: String = "GrammarMateUser",
    val ladderRows: List<LessonLadderRow> = emptyList(),
    val initialScreen: String = "HOME",
    val currentScreen: String = "HOME",
    val appVersion: String = "1.5",
    val hasVerbDrill: Boolean = false,
    val hasVocabDrill: Boolean = false,
    val welcomeDialogAttempts: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

data class CardSessionState(
    val sessionState: SessionState = SessionState.ACTIVE,
    val currentIndex: Int = 0,
    val currentCard: SessionCard? = null,
    val inputText: String = "",
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val incorrectAttemptsForCard: Int = 0,
    val activeTimeMs: Long = 0L,
    val voiceActiveMs: Long = 0L,
    val voiceWordCount: Int = 0,
    val hintCount: Int = 0,
    val voicePromptStartMs: Long? = null,
    val answerText: String? = null,
    val lastResult: Boolean? = null,
    val lastRating: Double? = null,
    val inputMode: InputMode = InputMode.VOICE,
    val voiceTriggerToken: Int = 0,
    val subLessonTotal: Int = 0,
    val subLessonCount: Int = 0,
    val subLessonTypes: List<SubLessonType> = emptyList(),
    val activeSubLessonIndex: Int = 0,
    val completedSubLessonCount: Int = 0,
    val subLessonFinishedToken: Int = 0,
    val wordBankWords: List<String> = emptyList(),
    val selectedWords: List<String> = emptyList(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakMessage: String? = null,
    val streakCelebrationToken: Int = 0,
    val hintLevel: HintLevel = HintLevel.EASY,
    val hintSessionOffset: Int = 0,
    val encounterCount: Int = 0,
    val isReviewMode: Boolean = false,
    val badSentenceCount: Int = 0,
    val testMode: Boolean = false,
    val vocabSprintLimit: Int = 20,
    val todayFireCount: Int = 0,
    val screenMode: TrainingScreenMode = TrainingScreenMode.NORMAL,
    val returnTo: String = "",
    val verbConjugationCards: List<VerbDrillCard> = emptyList()
) {
    /** Whether the session can accept an answer submission. */
    val canSubmit: Boolean
        get() = currentCard != null && (sessionState == SessionState.ACTIVE || sessionState == SessionState.PAUSED)
}

data class BossState(
    val bossActive: Boolean = false,
    val bossType: BossType? = null,
    val bossTotal: Int = 0,
    val bossProgress: Int = 0,
    val bossReward: BossReward? = null,
    val bossRewardMessage: String? = null,
    val bossFinishedToken: Int = 0,
    val bossLastType: BossType? = null,
    val bossErrorMessage: String? = null,
    val bossLessonRewards: Map<String, BossReward> = emptyMap(),
    val bossMegaRewards: Map<String, BossReward> = emptyMap(),
    /** Hint level before boss started; restored on boss exit. */
    val savedHintLevel: HintLevel = HintLevel.EASY
)

data class StoryState(
    val storyCheckInDone: Boolean = false,
    val storyCheckOutDone: Boolean = false,
    val activeStory: StoryQuiz? = null,
    val storyErrorMessage: String? = null
)

data class VocabSprintState(
    val currentVocab: VocabEntry? = null,
    val vocabInputText: String = "",
    val vocabAttempts: Int = 0,
    val vocabAnswerText: String? = null,
    val vocabIndex: Int = 0,
    val vocabTotal: Int = 0,
    val vocabWordBankWords: List<String> = emptyList(),
    val vocabFinishedToken: Int = 0,
    val vocabErrorMessage: String? = null,
    val vocabInputMode: InputMode = InputMode.VOICE,
    val vocabVoiceTriggerToken: Int = 0,
    val vocabMasteredCount: Int = 0
)

data class EliteState(
    val eliteActive: Boolean = false,
    val eliteStepIndex: Int = 0,
    val eliteBestSpeeds: List<Double> = emptyList(),
    val eliteFinishedToken: Int = 0,
    val eliteUnlocked: Boolean = false,
    val eliteSizeMultiplier: Double = 1.25
)

data class FlowerDisplayState(
    val lessonFlowers: Map<String, FlowerVisual> = emptyMap(),
    val currentLessonFlower: FlowerVisual? = null,
    val currentLessonShownCount: Int = 0
)

data class AudioState(
    val ttsState: TtsState = TtsState.Idle,
    val ttsDownloadState: DownloadState = DownloadState.Idle,
    val ttsModelReady: Boolean = false,
    val ttsMeteredNetwork: Boolean = false,
    val bgTtsDownloading: Boolean = false,
    val bgTtsDownloadStates: Map<String, DownloadState> = emptyMap(),
    val ttsModelsReady: Map<String, Boolean> = emptyMap(),
    val ttsSpeed: Float = 1.0f,
    val ruTextScale: Float = 1.0f,
    val voiceAutoStart: Boolean = true,
    val useOfflineAsr: Boolean = false,
    val asrState: AsrState = AsrState.IDLE,
    val asrModelReady: Boolean = false,
    val asrDownloadState: DownloadState = DownloadState.Idle,
    val asrMeteredNetwork: Boolean = false,
    val asrErrorMessage: String? = null,
    val audioPermissionDenied: Boolean = false
)

data class DailyPracticeState(
    val dailySession: DailySessionState = DailySessionState(),
    val dailyCursor: DailyCursorState = DailyCursorState()
)

data class TrainingUiState(
    val navigation: NavigationState = NavigationState(),
    val cardSession: CardSessionState = CardSessionState(),
    val boss: BossState = BossState(),
    val story: StoryState = StoryState(),
    val vocabSprint: VocabSprintState = VocabSprintState(),
    val elite: EliteState = EliteState(),
    val flowerDisplay: FlowerDisplayState = FlowerDisplayState(),
    val audio: AudioState = AudioState(),
    val daily: DailyPracticeState = DailyPracticeState(),
    val pomodoro: PomodoroState = PomodoroState(),
    /** Grammar Story Roadmap: chapters for current pack */
    val chapters: List<Chapter> = emptyList(),
    /** Grammar Story Roadmap: chapter progress for current pack */
    val chapterProgresses: Map<String, ChapterProgress> = emptyMap(),
    /** Grammar Story Roadmap: active chapter ID (null if no chapters) */
    val activeChapterId: String? = null,
    /** True while background init (file I/O) is in progress. UI shows a spinner. */
    val isLoading: Boolean = false,
    /** Parse errors collected during import operations */
    val parseErrors: List<ParseError> = emptyList(),
    /** True if parse errors should be shown to the user via warning dialog */
    val showParseWarning: Boolean = false,
    /** Formatted user message for parse errors */
    val parseUserMessage: String? = null
) {
    /**
     * Reset all session-related state to defaults.
     * Used by selectLanguage, selectLesson, selectMode, importLessonPack,
     * addLanguage, and refreshLessons to clear stale training state.
     *
     * NOTE: After Phase 4 extraction, boss/story/vocabSprint/daily/flowerDisplay
     * are owned by feature flows and merged via combine(). This method only resets
     * core-owned fields (cardSession). Feature resets are called explicitly
     * by the ViewModel.
     */
    fun resetSessionState(): TrainingUiState = copy(
        cardSession = CardSessionState(sessionState = SessionState.PAUSED)
    )

    /**
     * Full session reset including counters and timers.
     * Used when changing language or importing packs where all progress resets.
     *
     * NOTE: After Phase 4 extraction, boss/story/vocabSprint/daily/flowerDisplay
     * are owned by feature flows. Feature resets are called explicitly by the ViewModel.
     */
    fun resetAllSessionState(): TrainingUiState = resetSessionState().copy(
        cardSession = CardSessionState(correctCount = 0, incorrectCount = 0, activeTimeMs = 0L, voiceActiveMs = 0L, voiceWordCount = 0, hintCount = 0, currentCard = null),
        elite = EliteState(eliteActive = false)
    )
}

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
 * Tracks progress through a chapter. Pack-scoped storage.
 *
 * @param chapterId Links to Chapter.chapterId.
 * @param lessonsStarted Number of lessons with mastery > 0. >= 0.
 * @param lessonsCompleted Number of lessons with intervalStepIndex >= 3. >= 0.
 * @param lastAccessedMs Epoch millis of last lesson activity in this chapter. 0 = never accessed.
 */
data class ChapterProgress(
    val chapterId: String,
    val lessonsStarted: Int = 0,
    val lessonsCompleted: Int = 0,
    val lastAccessedMs: Long = 0L
) {
    init {
        require(lessonsStarted >= 0) { "lessonsStarted must be >= 0" }
        require(lessonsCompleted >= 0) { "lessonsCompleted must be >= 0" }
        require(lessonsStarted >= lessonsCompleted) { "lessonsStarted must be >= lessonsCompleted" }
    }

    companion object {
        /**
         * Creates a new ChapterProgress with zero values for a chapter.
         */
        fun forChapter(chapterId: String) = ChapterProgress(chapterId)
    }
}

/**
 * Grammar chip content loaded from markdown files.
 * Provides grammar explanations for lessons.
 *
 * @param key Grammar chip identifier (e.g., "A01", "A02")
 * @param title Full title (e.g., "A01 - Presente Indicativo")
 * @param essence Core explanation in Russian (Суть section)
 * @param formula Formula section if present (Формула)
 * @param base Base verbs/phrases section if present (База)
 * @param examples List of examples with Italian text and Russian translation
 * @param dontConfuse Optional "Не путать" section content
 * @param notes Additional notes sections (title -> content)
 */
data class GrammarChip(
    val key: String,
    val title: String,
    val essence: String,
    val formula: String? = null,
    val base: String? = null,
    val examples: List<GrammarExample> = emptyList(),
    val dontConfuse: String? = null,
    val notes: Map<String, String> = emptyMap()
) {
    init {
        require(key.isNotBlank()) { "key must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(essence.isNotBlank()) { "essence must not be blank" }
    }
}

/**
 * Single grammar example with Italian text and Russian translation.
 *
 * @param it Italian example text
 * @param ru Russian translation
 * @param note Optional additional note
 */
data class GrammarExample(
    val it: String,
    val ru: String,
    val note: String = ""
) {
    init {
        require(it.isNotBlank()) { "Italian text must not be blank" }
        require(ru.isNotBlank()) { "Russian translation must not be blank" }
    }
}
