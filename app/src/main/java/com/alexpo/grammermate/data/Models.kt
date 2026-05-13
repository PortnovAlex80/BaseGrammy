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
    ALL_MIXED,
    MIX_CHALLENGE
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

enum class SrsRating { AGAIN, HARD, GOOD, EASY }

/** Difficulty level controlling which hints are visible during practice. */
enum class HintLevel {
    /** All hints visible: verb info, word bank, first-letter hints. Current default. */
    EASY,
    /** Partial hints: infinitive+tense only, no word bank, keyboard/voice only. */
    MEDIUM,
    /** No hints: voice only, user must produce from Russian prompt alone. */
    HARD
}

data class TrainingProgress(
    val languageId: LanguageId = LanguageId("en"),
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
    val activePackId: PackId? = null,
    val dailyLevel: Int = 0,
    val dailyTaskIndex: Int = 0,
    val dailyCursor: DailyCursorState = DailyCursorState()
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
    val languageId: LanguageId,
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
    val lastSessionHash: Int = 0,       // hash of last completed session for "repeat" cache
    val firstSessionDate: String = "",  // ISO date (yyyy-MM-dd) of the first session of the day
    val firstSessionSentenceCardIds: List<String> = emptyList(),  // card IDs from first session's block 1
    val firstSessionVerbCardIds: List<String> = emptyList()       // card IDs from first session's block 3
)

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
    val hasVocabDrill: Boolean = false
)

data class CardSessionState(
    val sessionState: SessionState = SessionState.ACTIVE,
    val currentIndex: Int = 0,
    val currentCard: SentenceCard? = null,
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
    val badSentenceCount: Int = 0,
    val testMode: Boolean = false,
    val vocabSprintLimit: Int = 20
)

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
    val bossMegaRewards: Map<String, BossReward> = emptyMap()
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

data class DrillState(
    val isDrillMode: Boolean = false,
    val drillCardIndex: Int = 0,
    val drillTotalCards: Int = 0,
    val drillShowStartDialog: Boolean = false,
    val drillHasProgress: Boolean = false
)

data class FlowerDisplayState(
    val lessonFlowers: Map<String, FlowerVisual> = emptyMap(),
    val currentLessonFlower: FlowerVisual? = null,
    val currentLessonShownCount: Int = 0
)

data class AudioState(
    val ttsState: TtsState = TtsState.IDLE,
    val ttsDownloadState: DownloadState = DownloadState.Idle,
    val ttsModelReady: Boolean = false,
    val ttsMeteredNetwork: Boolean = false,
    val bgTtsDownloading: Boolean = false,
    val bgTtsDownloadStates: Map<String, DownloadState> = emptyMap(),
    val ttsModelsReady: Map<String, Boolean> = emptyMap(),
    val ttsSpeed: Float = 1.0f,
    val ruTextScale: Float = 1.0f,
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
    val drill: DrillState = DrillState(),
    val flowerDisplay: FlowerDisplayState = FlowerDisplayState(),
    val audio: AudioState = AudioState(),
    val daily: DailyPracticeState = DailyPracticeState()
) {
    /**
     * Reset all session-related state to defaults.
     * Used by selectLanguage, selectLesson, selectMode, importLessonPack,
     * addLanguage, and refreshLessons to clear stale training state.
     */
    fun resetSessionState(): TrainingUiState = copy(
        cardSession = CardSessionState(sessionState = SessionState.PAUSED),
        boss = BossState(),
        story = StoryState(),
        vocabSprint = VocabSprintState(),
        drill = DrillState()
    )

    /**
     * Full session reset including counters and timers.
     * Used when changing language or importing packs where all progress resets.
     */
    fun resetAllSessionState(): TrainingUiState = resetSessionState().copy(
        cardSession = CardSessionState(correctCount = 0, incorrectCount = 0, activeTimeMs = 0L, voiceActiveMs = 0L, voiceWordCount = 0, hintCount = 0, currentCard = null),
        elite = EliteState(eliteActive = false),
        boss = BossState(bossLessonRewards = emptyMap(), bossMegaRewards = emptyMap()),
        flowerDisplay = FlowerDisplayState(lessonFlowers = emptyMap(), currentLessonFlower = null)
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
