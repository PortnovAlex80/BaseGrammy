# Module Decomposition Proposal

## 1. Current Architecture Problems

### 1.1 God Object: TrainingViewModel (3615 lines)

TrainingViewModel violates every reasonable size limit. It contains:
- **65+ public methods** spanning 7 distinct feature domains
- **100+ fields** in `TrainingUiState` (the state data class alone is 115 lines)
- **15 private var fields** for transient session state (`sessionCards`, `bossCards`, `eliteCards`, `vocabSession`, etc.)
- **13 data store instantiations** inside the constructor
- **TTS/ASR engine lifecycle management** mixed with business logic

### 1.2 State Ownership Scattered

No single place owns any given piece of state. A "boss progress" update touches:
- `_uiState` (via `update { }`)
- `bossCards` (private var)
- `progressStore` (via `saveProgress()`)
- `masteryStore` (via `recordCardShowForMastery`)
- `streakStore` (via `updateStreak`)
- `soundPool` (via `playSuccessTone`)

Six different state containers touched in one method. No module boundary exists.

### 1.3 Massive Copy-Paste State Reset

The `selectLanguage()`, `selectLesson()`, `selectMode()`, `refreshLessons()`, `importLessonPack()`, and `addLanguage()` methods each contain a ~40-field `it.copy(...)` block that resets all session state. These blocks are nearly identical but have subtle differences. Any new field added to `TrainingUiState` must be added to 6+ places or state leaks between features.

### 1.4 Untestable Business Logic

Business logic lives inside an `AndroidViewModel` that requires:
- An Android `Application` context
- A running `viewModelScope`
- Access to `Dispatchers.Main` for state updates
- Real file I/O via stores

Unit testing `submitAnswer()` or `startBoss()` requires instrumented tests or heavy mocking of 13 stores.

### 1.5 No Module Boundaries

VerbDrillViewModel (575 lines) and VocabDrillViewModel (456 lines) are separate ViewModels that duplicate patterns from TrainingViewModel (TTS management, answer validation, word bank generation). DailySessionComposer, DailySessionHelper, and DailyPracticeSessionProvider are in `feature/daily/` but their relationship to TrainingViewModel is ad-hoc rather than through defined interfaces.

---

## 2. Proposed Module Architecture

### Module Relationship Diagram

```
                         GrammarMateApp.kt (router)
                                  |
                                  | uiState: StateFlow<TrainingUiState>
                                  v
                    +-----------------------------+
                    |    TrainingViewModel        |
                    |    (thin orchestrator)      |
                    |    ~400 lines               |
                    +-----------------------------+
                       |      |       |       |
          +------------+      |       |       +------------+
          |                    |       |                     |
          v                    v       v                     v
  +---------------+   +--------------+  +------------+  +------------+
  | SessionRunner |   | DailyCoord.  |  | AudioCoord.|  | StreakMgr  |
  | (lesson flow) |   | (3 blocks)   |  | (TTS+ASR)  |  | (streaks)  |
  +---------------+   +--------------+  +------------+  +------------+
       |        |          |     |
       v        v          v     v
  +----------+ +--------+ +------+ +----------+
  | CardProv.| | Answer | |Verb  | | Vocab    |
  | (select) | | Valid. | |Drill | | Flash    |
  +----------+ +--------+ |Coord | | Coord    |
       |                   +------+ +----------+
       v                      |          |
  +-----------+               v          v
  | Progress  |         +--------+ +---------+
  | Tracker   |         | Verb   | | Vocab   |
  | (mastery) |         | Drill  | | SRS     |
  +-----------+         | Store  | | Store   |
       |                +--------+ +---------+
       v                    ^          ^
  +-----------+             |          |
  | Flower    |    +--------+----------+--------+
  | Renderer  |    |       Data Stores          |
  +-----------+    | (LessonStore, MasteryStore, |
                   |  ProgressStore, etc.)       |
                   +-----------------------------+
```

**Dependency rule:** Modules only depend downward. Data stores have no knowledge of modules. Modules depend on interfaces, not implementations.

---

### 2.1 SessionRunner

- **Responsibility**: Manages training session lifecycle -- start, advance card, complete sub-lesson, finish session. Handles all non-boss, non-elite, non-daily card flow.
- **Owns**: Current card index, session timer, sub-lesson boundaries, session state transitions.
- **Interface**:
```kotlin
interface SessionRunner {
    val sessionState: StateFlow<SessionRunnerState>

    fun startSession(cards: List<SentenceCard>, inputMode: InputMode)
    fun submitAnswer(input: String, inputMode: InputMode): SubmitResult
    fun nextCard(triggerVoice: Boolean)
    fun prevCard()
    fun togglePause()
    fun finishSession(): SessionSummary
    fun showAnswer(): String

    // Word bank
    fun getWordBankWords(): List<String>
    fun selectWordFromBank(word: String)
    fun removeLastSelectedWord()

    data class SessionRunnerState(
        val active: Boolean,
        val currentCard: SentenceCard?,
        val currentIndex: Int,
        val totalCards: Int,
        val correctCount: Int,
        val incorrectCount: Int,
        val incorrectAttempts: Int,
        val sessionState: SessionState,
        val inputMode: InputMode,
        val answerText: String?,
        val inputText: String,
        val lastResult: Boolean?,
        val wordBankWords: List<String>,
        val selectedWords: List<String>,
        val voiceTriggerToken: Int,
        val activeTimeMs: Long
    )

    data class SessionSummary(
        val correctCount: Int,
        val incorrectCount: Int,
        val activeTimeMs: Long,
        val voiceActiveMs: Long,
        val voiceWordCount: Int
    )
}
```
- **Dependencies**: `AnswerValidator`, `ProgressTracker`, `WordBankGenerator` (via constructor injection).
- **Extracted from**: TrainingViewModel methods `submitAnswer()`, `nextCard()`, `prevCard()`, `togglePause()`, `finishSession()`, `showAnswer()`, `startSession()`, `updateWordBank()`, `selectWordFromBank()`, `removeLastSelectedWord()`, `onInputChanged()`, `setInputMode()`, `onVoicePromptStarted()`, plus private methods `resumeTimer()`, `pauseTimer()`, `buildSessionCards()`, `currentCard()`.
- **Testability**: Pure Kotlin class. Inject mock `ProgressTracker` and `AnswerValidator`. Test session lifecycle without Android.
- **Estimated size**: ~450 lines

---

### 2.2 ProgressTracker

- **Responsibility**: Single source of truth for all mastery, progress, and persistence. Wraps MasteryStore, ProgressStore, and coordinates persistence calls.
- **Owns**: Mastery state per lesson, training progress persistence, card show recording, lesson completion tracking, cursor state.
- **Interface**:
```kotlin
interface ProgressTracker {
    // Card show tracking (VOICE/KEYBOARD only)
    fun recordCardShow(lessonId: String, languageId: String, cardId: String, inputMode: InputMode)
    fun markCardsShownForProgress(lessonId: String, languageId: String, cardIds: Collection<String>)
    fun markSubLessonCardsShown(lessonId: String, languageId: String, cards: List<SentenceCard>, inputMode: InputMode)

    // Lesson completion
    fun markLessonCompleted(lessonId: String, languageId: String)
    fun getCompletedSubLessonCount(subLessons: List<ScheduledSubLesson>, lessonId: String, languageId: String, allLessons: List<Lesson>): Int
    fun checkAndMarkLessonCompleted(lessonId: String, languageId: String, completedSubLessonCount: Int)

    // Mastery queries
    fun getMastery(lessonId: String, languageId: String): LessonMasteryState?
    fun getMasteryOrCreate(lessonId: String, languageId: String): LessonMasteryState
    fun getShownCardCount(lessonId: String, languageId: String): Int

    // Progress persistence
    fun saveTrainingProgress(progress: TrainingProgress)
    fun loadTrainingProgress(): TrainingProgress
    fun clearAllProgress(packIds: List<String>, lessonStore: LessonStore)

    // Cursor
    fun advanceCursor(currentCursor: DailyCursorState, sentenceCount: Int): DailyCursorState
    fun storeFirstSessionCardIds(cursor: DailyCursorState, sentenceIds: List<String>, verbIds: List<String>, today: String): DailyCursorState
}
```
- **Dependencies**: `MasteryStore`, `ProgressStore` (data layer, already exist).
- **Extracted from**: TrainingViewModel methods `recordCardShowForMastery()`, `markSubLessonCardsShown()`, `checkAndMarkLessonCompleted()`, `calculateCompletedSubLessons()`, `resolveCardLessonId()`, `saveProgress()`, `advanceCursor()`, `storeFirstSessionCardIds()`, `resetAllProgress()`.
- **Testability**: Wrap real stores with in-memory fakes. Test mastery tracking without file I/O.
- **Estimated size**: ~300 lines

---

### 2.3 CardProvider

- **Responsibility**: Card selection algorithms -- builds sub-lesson card sets from lesson data, schedules review cards, manages MIXED/NEW_ONLY sub-lessons.
- **Owns**: Sub-lesson schedule building, card filtering (hidden cards), sub-lesson size calculation.
- **Interface**:
```kotlin
interface CardProvider {
    fun buildSchedules(lessons: List<Lesson>): Map<String, LessonSchedule>
    fun buildSessionCards(
        lessons: List<Lesson>,
        mode: TrainingMode,
        selectedLessonId: String?,
        schedules: Map<String, LessonSchedule>,
        activeSubLessonIndex: Int,
        hiddenCardIds: Set<String>
    ): CardSetResult

    fun buildEliteCards(lessons: List<Lesson>, eliteSizeMultiplier: Double): List<SentenceCard>
    fun buildBossCards(lessons: List<Lesson>, type: BossType, selectedLessonId: String?, selectedIndex: Int): List<SentenceCard>

    data class CardSetResult(
        val cards: List<SentenceCard>,
        val subLessonTotal: Int,
        val subLessonCount: Int,
        val activeSubLessonIndex: Int,
        val completedSubLessonCount: Int,
        val subLessonTypes: List<SubLessonType>
    )
}
```
- **Dependencies**: `MixedReviewScheduler` (already a pure class in data/).
- **Extracted from**: TrainingViewModel methods `rebuildSchedules()`, `buildSessionCards()`, `buildEliteCards()`, and the card-building portions of `startBoss()`.
- **Testability**: Pure functions. Feed lesson data, verify card selection. No Android dependencies.
- **Estimated size**: ~250 lines

---

### 2.4 AnswerValidator

- **Responsibility**: Answer normalization, comparison against accepted answers, hint logic.
- **Owns**: Normalization rules, attempt counting, hint threshold (3 attempts).
- **Interface**:
```kotlin
interface AnswerValidator {
    fun validate(input: String, acceptedAnswers: List<String>, testMode: Boolean): ValidationResult
    fun shouldShowHint(incorrectAttempts: Int): Boolean
    fun getHintText(acceptedAnswers: List<String>): String

    data class ValidationResult(
        val isCorrect: Boolean,
        val normalizedInput: String,
        val hintShown: Boolean,
        val hintText: String?
    )
}
```
- **Dependencies**: `Normalizer` (already exists in data/).
- **Extracted from**: Core logic in `submitAnswer()`, `submitDailySentenceAnswer()`, `submitDailyVerbAnswer()`, `submitVocabAnswer()`. Currently duplicated across TrainingViewModel, VerbDrillCardSessionProvider, DailyPracticeSessionProvider.
- **Testability**: Pure functions. Zero dependencies on Android. Highest-value extraction for testability.
- **Estimated size**: ~80 lines

---

### 2.5 WordBankGenerator

- **Responsibility**: Generates word bank (answer words + distractors) for any card type.
- **Owns**: Distractor selection algorithm, word count targets.
- **Interface**:
```kotlin
interface WordBankGenerator {
    fun generateSentenceBank(card: SentenceCard, allCards: List<SentenceCard>, maxDistractors: Int = 3): List<String>
    fun generateVocabBank(entry: VocabEntry, pool: List<VocabEntry>, fallbackPool: List<VocabEntry>): List<String>
    fun generateVerbBank(card: VerbDrillCard, sessionCards: List<VerbDrillCard>): List<String>
}
```
- **Dependencies**: `Normalizer` (data layer).
- **Extracted from**: TrainingViewModel methods `generateWordBank()`, `updateWordBank()`, `buildVocabWordBank()`, `updateVocabWordBank()`, plus identical logic in `VerbDrillCardSessionProvider.getWordBankWords()` and `DailyPracticeSessionProvider.getWordBankWords()`.
- **Testability**: Pure functions. Test distractor selection, correctness of answer inclusion.
- **Estimated size**: ~120 lines

---

### 2.6 DailyPracticeCoordinator

- **Responsibility**: Orchestrates the 3-block daily practice session (Translate, Vocab, Verbs). Manages block transitions, cursor advancement, first-session caching, repeat logic.
- **Owns**: Daily session lifecycle, block progress, cursor state transitions, first-session card ID storage.
- **Interface**:
```kotlin
interface DailyPracticeCoordinator {
    val dailyState: StateFlow<DailySessionState>

    fun startDailySession(
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cursor: DailyCursorState,
        isFirstSessionToday: Boolean
    ): Boolean

    fun repeatDailySession(lessonLevel: Int, packId: String, languageId: String, lessonId: String, cursor: DailyCursorState): Boolean
    fun advanceTask(): Boolean
    fun advanceBlock(): Boolean
    fun repeatBlock(): Boolean
    fun cancelSession()
    fun getCurrentTask(): DailyTask?
    fun getBlockProgress(): BlockProgress

    fun recordCardPracticed(blockType: DailyBlockType)
    fun persistVerbProgress(card: VerbDrillCard, packId: String)
    fun rateVocabCard(rating: Int, packId: String)

    fun hasResumableSession(cursor: DailyCursorState): Boolean
}
```
- **Dependencies**: `DailySessionComposer` (already exists), `DailySessionHelper` (already exists), `VerbDrillStore`, `WordMasteryStore`, `ProgressTracker`.
- **Extracted from**: TrainingViewModel methods `startDailyPractice()`, `repeatDailyPractice()`, `advanceDailyTask()`, `advanceDailyBlock()`, `repeatDailyBlock()`, `cancelDailySession()`, `recordDailyCardPracticed()`, `persistDailyVerbProgress()`, `rateVocabCard()`, `hasResumableDailySession()`, plus `DailySessionHelper` and `DailySessionComposer` merged in.
- **Testability**: Inject mock stores. Test block transitions, cursor advancement, repeat logic without Android.
- **Estimated size**: ~350 lines

---

### 2.7 BossBattleRunner

- **Responsibility**: Boss battle session lifecycle -- card selection, progress tracking, reward calculation, unlock guards.
- **Owns**: Boss state (active, type, progress, reward), boss card pool, unlock eligibility.
- **Interface**:
```kotlin
interface BossBattleRunner {
    val bossState: StateFlow<BossRunnerState>

    fun startBoss(type: BossType, lessons: List<Lesson>, selectedLessonId: String?, selectedIndex: Int, completedSubLessons: Int, testMode: Boolean): StartBossResult
    fun recordCorrectAnswer(isLastCard: Boolean): BossProgressUpdate
    fun finishBoss(selectedLessonId: String?, currentRewards: Map<String, BossReward>, megaRewards: Map<String, BossReward>): BossResult
    fun clearRewardMessage()

    data class BossRunnerState(
        val active: Boolean,
        val type: BossType?,
        val total: Int,
        val progress: Int,
        val reward: BossReward?,
        val rewardMessage: String?,
        val errorMessage: String?
    )

    data class StartBossResult(val success: Boolean, val error: String? = null, val cards: List<SentenceCard> = emptyList())
    data class BossProgressUpdate(val isComplete: Boolean, val newReward: BossReward?, val shouldPause: Boolean)
    data class BossResult(val reward: BossReward?, val lessonRewards: Map<String, BossReward>, val megaRewards: Map<String, BossReward>)
}
```
- **Dependencies**: None beyond data classes.
- **Extracted from**: TrainingViewModel methods `startBoss()`, `startBossLesson()`, `startBossMega()`, `startBossElite()`, `finishBoss()`, `updateBossProgress()`, `resolveBossReward()`, `bossRewardMessage()`, `clearBossRewardMessage()`, `clearBossError()`.
- **Testability**: Pure logic. Test reward thresholds, unlock guards, progress tracking.
- **Estimated size**: ~200 lines

---

### 2.8 FlowerProgressRenderer

- **Responsibility**: Calculates flower visual states from mastery data. Pure computation.
- **Owns**: Nothing (pure function module). Delegates to existing `FlowerCalculator` and `LessonLadderCalculator`.
- **Interface**:
```kotlin
interface FlowerProgressRenderer {
    fun calculateFlowerStates(
        lessons: List<Lesson>,
        languageId: String,
        selectedLessonId: String?
    ): FlowerStateResult

    data class FlowerStateResult(
        val lessonFlowers: Map<String, FlowerVisual>,
        val currentLessonFlower: FlowerVisual?,
        val currentLessonShownCount: Int,
        val ladderRows: List<LessonLadderRow>
    )
}
```
- **Dependencies**: `FlowerCalculator` (already pure), `LessonLadderCalculator` (already pure), `MasteryStore`.
- **Extracted from**: TrainingViewModel method `refreshFlowerStates()`.
- **Testability**: Inject mock MasteryStore. Test that flower states reflect mastery correctly.
- **Estimated size**: ~60 lines

---

### 2.9 AudioCoordinator

- **Responsibility**: TTS playback, ASR recording, model download management, sound effects.
- **Owns**: TTS engine lifecycle, ASR engine lifecycle, sound pool, download state.
- **Interface**:
```kotlin
interface AudioCoordinator {
    val ttsState: StateFlow<TtsState>
    val asrState: StateFlow<AsrState>
    val ttsDownloadState: StateFlow<Map<String, DownloadState>>
    val ttsModelsReady: StateFlow<Map<String, Boolean>>

    fun speak(text: String, languageId: String, speed: Float = 1.0f)
    fun stopTts()
    fun setTtsSpeed(speed: Float)

    fun startAsrDownload()
    fun startOfflineRecognition(languageId: String): String // returns recognized text
    fun stopAsr()
    fun setUseOfflineAsr(enabled: Boolean)
    fun checkAsrModel()

    fun playSuccessSound()
    fun playErrorSound()

    fun release()
}
```
- **Dependencies**: `TtsEngine`, `TtsModelManager`, `AsrEngine`, `AsrModelManager`, `SoundPool` (all in data/ already).
- **Extracted from**: TrainingViewModel methods `onTtsSpeak()`, `stopTts()`, `setTtsSpeed()`, `startTtsDownload()`, `confirmTtsDownloadOnMetered()`, `dismissMeteredWarning()`, `dismissTtsDownloadDialog()`, `startTtsDownloadForLanguage()`, `checkTtsModel()`, `checkAllTtsModels()`, `startBackgroundTtsDownload()`, `startOfflineRecognition()`, `stopAsr()`, `setUseOfflineAsr()`, `startAsrDownload()`, `confirmAsrDownloadOnMetered()`, `dismissAsrMeteredWarning()`, `checkAsrModel()`, `playSuccessTone()`, `playErrorTone()`, plus all download state management.
- **Testability**: Interface mockable. Real implementation requires Android context (inevitable for audio).
- **Estimated size**: ~300 lines

---

### 2.10 StreakManager

- **Responsibility**: Daily streak tracking, streak celebration messages, streak persistence.
- **Owns**: Streak state, celebration message generation.
- **Interface**:
```kotlin
interface StreakManager {
    fun recordSubLessonCompletion(languageId: String): StreakUpdate
    fun getCurrentStreak(languageId: String): StreakData
    fun dismissMessage()

    data class StreakUpdate(
        val streakData: StreakData,
        val isNewStreak: Boolean,
        val celebrationMessage: String?
    )
}
```
- **Dependencies**: `StreakStore` (data layer).
- **Extracted from**: TrainingViewModel methods `updateStreak()`, `dismissStreakMessage()`.
- **Testability**: Inject mock StreakStore. Test milestone detection and message generation.
- **Estimated size**: ~80 lines

---

### 2.11 VocabSprintRunner

- **Responsibility**: Vocab sprint (flashcard) session lifecycle -- card selection via SRS, answer validation, progress tracking.
- **Owns**: Vocab session state, SRS ordering.
- **Interface**:
```kotlin
interface VocabSprintRunner {
    val vocabState: StateFlow<VocabSprintState>

    fun openSession(lessonId: String, languageId: String, resume: Boolean, limit: Int)
    fun submitAnswer(input: String, testMode: Boolean): Boolean
    fun showAnswer(): String
    fun setInputMode(mode: InputMode)
    fun requestVoice()
    fun onInputChanged(text: String)
    fun getWordBankWords(): List<String>

    data class VocabSprintState(
        val currentEntry: VocabEntry?,
        val inputText: String,
        val attempts: Int,
        val answerText: String?,
        val index: Int,
        val total: Int,
        val finishedToken: Int,
        val errorMessage: String?,
        val inputMode: InputMode,
        val voiceTriggerToken: Int,
        val wordBankWords: List<String>
    )
}
```
- **Dependencies**: `AnswerValidator`, `WordBankGenerator`, `VocabProgressStore`, `LessonStore`.
- **Extracted from**: TrainingViewModel methods `openVocabSprint()`, `submitVocabAnswer()`, `showVocabAnswer()`, `moveToNextVocab()`, `onVocabInputChanged()`, `setVocabInputMode()`, `requestVocabVoice()`, `hasVocabProgress()`, `clearVocabError()`.
- **Testability**: Inject mock stores and validators. Test SRS ordering and answer flow.
- **Estimated size**: ~250 lines

---

## 3. Proposed TrainingUiState Decomposition

The current flat `TrainingUiState` (100+ fields) should be grouped into nested data classes. Each module owns its sub-state:

```kotlin
data class TrainingUiState(
    // Navigation & selection (owned by ViewModel)
    val languages: List<Language>,
    val installedPacks: List<LessonPack>,
    val selectedLanguageId: String,
    val activePackId: String?,
    val activePackLessonIds: List<String>?,
    val lessons: List<Lesson>,
    val selectedLessonId: String?,
    val mode: TrainingMode,
    val currentScreen: String,
    val initialScreen: String,

    // Session state (owned by SessionRunner)
    val session: SessionState = SessionState(),

    // Daily practice (owned by DailyPracticeCoordinator)
    val dailySession: DailySessionState = DailySessionState(),
    val dailyCursor: DailyCursorState = DailyCursorState(),

    // Boss battle (owned by BossBattleRunner)
    val boss: BossState = BossState(),

    // Elite mode (owned by SessionRunner, sub-mode)
    val elite: EliteState = EliteState(),

    // Vocab sprint (owned by VocabSprintRunner)
    val vocabSprint: VocabSprintState = VocabSprintState(),

    // Drill mode (owned by SessionRunner, sub-mode)
    val drill: DrillState = DrillState(),

    // Progress visualization (owned by FlowerProgressRenderer)
    val flowers: FlowerDisplayState = FlowerDisplayState(),

    // Audio (owned by AudioCoordinator)
    val audio: AudioState = AudioState(),

    // Profile (owned by ViewModel)
    val userName: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val streakMessage: String?,
    val streakCelebrationToken: Int,
    val testMode: Boolean,
    val eliteSizeMultiplier: Double,
    val vocabSprintLimit: Int,
    val vocabMasteredCount: Int,
    val badSentenceCount: Int
)

data class SessionState(
    val sessionState: SessionState = SessionState.PAUSED,
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
    val storyCheckInDone: Boolean = false,
    val storyCheckOutDone: Boolean = false,
    val activeStory: StoryQuiz? = null,
    val storyErrorMessage: String? = null
)

data class BossState(
    val active: Boolean = false,
    val type: BossType? = null,
    val total: Int = 0,
    val progress: Int = 0,
    val reward: BossReward? = null,
    val rewardMessage: String? = null,
    val finishedToken: Int = 0,
    val lastType: BossType? = null,
    val errorMessage: String? = null,
    val lessonRewards: Map<String, BossReward> = emptyMap(),
    val megaRewards: Map<String, BossReward> = emptyMap()
)

data class EliteState(
    val active: Boolean = false,
    val stepIndex: Int = 0,
    val bestSpeeds: List<Double> = emptyList(),
    val finishedToken: Int = 0,
    val unlocked: Boolean = false
)

data class DrillState(
    val isDrillMode: Boolean = false,
    val cardIndex: Int = 0,
    val totalCards: Int = 0,
    val showStartDialog: Boolean = false,
    val hasProgress: Boolean = false
)

data class FlowerDisplayState(
    val lessonFlowers: Map<String, FlowerVisual> = emptyMap(),
    val currentLessonFlower: FlowerVisual? = null,
    val currentLessonShownCount: Int = 0,
    val ladderRows: List<LessonLadderRow> = emptyList()
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
```

This allows targeted updates: `it.copy(session = it.session.copy(correctCount = ...))` instead of touching 40+ fields at once.

---

## 4. Migration Strategy

### Phase 1: Extract Pure Functions (Zero Risk)

**What moves where:**
1. Create `AnswerValidator` in `feature/training/AnswerValidator.kt` -- extract normalization + comparison from `submitAnswer()`.
2. Create `WordBankGenerator` in `feature/training/WordBankGenerator.kt` -- extract from `generateWordBank()`, `buildVocabWordBank()`.
3. Create `FlowerProgressRenderer` in `feature/training/FlowerProgressRenderer.kt` -- extract from `refreshFlowerStates()`.

**Tests to add first:**
- `AnswerValidatorTest`: correct/incorrect answers, testMode bypass, hint after 3 attempts.
- `WordBankGeneratorTest`: answer words present, distractor count, no duplicates.
- `FlowerProgressRendererTest`: verify FlowerCalculator output mapping.

**What could break:** Nothing -- these are pure extraction. TrainingViewModel delegates to the new classes but behavior is identical.

**Rollback:** Delete helper files, inline the calls back into TrainingViewModel.

**Estimated effort:** 1 day.

---

### Phase 2: Extract Stateless Helpers (Low Risk)

**What moves where:**
1. Create `BossBattleRunner` in `feature/boss/BossBattleRunner.kt` -- extract boss card selection, reward calculation, unlock guards.
2. Create `CardProvider` in `feature/training/CardProvider.kt` -- extract `rebuildSchedules()`, `buildSessionCards()`, `buildEliteCards()`.
3. Create `StreakManager` in `feature/progress/StreakManager.kt` -- extract `updateStreak()`, `dismissStreakMessage()`.

**Tests to add first:**
- `BossBattleRunnerTest`: reward thresholds (50%=Bronze, 75%=Silver, 100%=Gold), unlock guard (15 sub-lessons), empty cards error.
- `CardProviderTest`: NEW_ONLY vs MIXED sub-lessons, hidden card filtering, correct sub-lesson count.
- `StreakManagerTest`: milestone messages (1, 3, 7, 14, 30, 100 days), no message for consecutive completions.

**What could break:**
- Boss battle card selection might differ if extraction introduces subtle ordering changes.
- Schedule building depends on `MixedReviewScheduler` which is already pure -- low risk.

**Rollback:** Revert to TrainingViewModel methods. Keep tests as regression tests.

**Estimated effort:** 2 days.

---

### Phase 3: Extract Stateful Modules with Interfaces (Medium Risk)

**What moves where:**
1. Create `SessionRunner` in `feature/training/SessionRunner.kt` -- the largest extraction. Moves card navigation, timer, answer submission, word bank interaction.
2. Create `DailyPracticeCoordinator` in `feature/daily/DailyPracticeCoordinator.kt` -- merge DailySessionHelper + DailySessionComposer + TrainingViewModel daily methods.
3. Create `VocabSprintRunner` in `feature/vocab/VocabSprintRunner.kt` -- extract vocab sprint session.
4. Create `ProgressTracker` in `feature/progress/ProgressTracker.kt` -- wrap MasteryStore + ProgressStore access patterns.
5. Create `AudioCoordinator` in `shared/audio/AudioCoordinator.kt` -- extract TTS/ASR/SoundPool management.

**Tests to add first:**
- `SessionRunnerTest`: full session lifecycle (start -> answer all -> finish), hint flow (3 wrong -> show answer), word bank mode does not record mastery.
- `DailyPracticeCoordinatorTest`: block transitions, cursor advancement only on full completion, repeat uses same card IDs.
- `ProgressTrackerTest`: WORD_BANK mode does not count, MIXED mode resolves correct lesson ID.
- `AudioCoordinatorTest`: mock engines, verify lifecycle calls.

**What could break:**
- Timer precision: `SystemClock.elapsedRealtime()` usage must transfer correctly.
- State synchronization: multiple modules updating `_uiState` concurrently could race. Solution: all state updates go through the ViewModel's `updateState` lambda (same as current `TrainingStateAccess` pattern).
- Daily cursor rollback on cancel: must preserve exact logic.

**Rollback:** Each module is behind an interface. Swap implementation with a "passthrough" that delegates back to TrainingViewModel methods.

**Estimated effort:** 5 days.

---

### Phase 4: Break ViewModel into Thin Orchestrator (High Risk)

**What moves where:**
1. Group `TrainingUiState` fields into nested data classes (Section 3 above).
2. TrainingViewModel becomes a thin orchestrator:
   - Holds module instances
   - Routes public methods to the correct module
   - Manages `currentScreen` navigation state
   - Handles pack/language/lesson selection (cross-cutting concerns)
3. Target: ~400 lines

**The thin ViewModel looks like:**
```kotlin
class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionRunner: SessionRunner
    private val dailyCoordinator: DailyPracticeCoordinator
    private val bossRunner: BossBattleRunner
    private val audioCoordinator: AudioCoordinator
    private val progressTracker: ProgressTracker
    private val streakManager: StreakManager
    private val flowerRenderer: FlowerProgressRenderer
    private val cardProvider: CardProvider
    private val answerValidator: AnswerValidator
    private val wordBankGenerator: WordBankGenerator

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState

    // Navigation (stays in ViewModel)
    fun selectLanguage(languageId: String) { ... }
    fun selectLesson(lessonId: String) { ... }
    fun selectPack(packId: String) { ... }

    // Delegates to modules
    fun submitAnswer() = sessionRunner.submitAnswer(...)
    fun startDailyPractice(level: Int) = dailyCoordinator.startDailySession(...)
    fun startBossLesson() = bossRunner.startBoss(...)

    // ... etc
}
```

**Tests to add first:**
- Integration test: full user flow (select lesson -> train -> finish -> flower updates).
- Navigation test: language/lesson switch resets all module state.
- State consistency test: after every action, `TrainingUiState` is self-consistent.

**What could break:**
- GrammarMateApp.kt depends on specific field names in `TrainingUiState`. The nested data class restructuring changes access patterns (e.g., `state.bossActive` becomes `state.boss.active`). This requires updating all composable references.
- Saved state restoration: `ProgressStore` saves flat field names. Nested restructuring requires migration.

**Rollback:** Keep the flat `TrainingUiState` as a "view" over the nested structure using computed properties. Revert the nesting without reverting the module extraction.

**Estimated effort:** 5 days.

---

## 5. Interface Contracts

### Module-to-Module Boundaries

| From | To | Interface | Data Flow |
|------|----|-----------|-----------|
| SessionRunner | AnswerValidator | `AnswerValidator.validate()` | Input -> ValidationResult |
| SessionRunner | ProgressTracker | `ProgressTracker.recordCardShow()` | Card show event |
| SessionRunner | AudioCoordinator | `AudioCoordinator.playSuccessSound()` | Sound trigger |
| SessionRunner | CardProvider | `CardProvider.buildSessionCards()` | Lesson data -> Card set |
| SessionRunner | WordBankGenerator | `WordBankGenerator.generateSentenceBank()` | Card -> Word list |
| DailyCoordinator | DailySessionComposer | Direct (already exists) | Builds task list |
| DailyCoordinator | ProgressTracker | `ProgressTracker.advanceCursor()` | Cursor update |
| DailyCoordinator | AudioCoordinator | `AudioCoordinator.playSuccessSound()` | Sound trigger |
| BossRunner | CardProvider | `CardProvider.buildBossCards()` | Boss card set |
| ViewModel | All modules | Constructor injection | Owns lifecycle |
| ViewModel | FlowerRenderer | `FlowerProgressRenderer.calculateFlowerStates()` | Mastery -> Visuals |
| ViewModel | StreakManager | `StreakManager.recordSubLessonCompletion()` | Streak update |
| ViewModel | AudioCoordinator | `AudioCoordinator` full interface | Audio lifecycle |

### State Access Pattern

All modules receive `TrainingStateAccess` (already defined in `DailySessionHelper.kt`) to update the shared `StateFlow<TrainingUiState>`. No module holds its own `MutableStateFlow` -- all state lives in the single `TrainingUiState`:

```kotlin
interface TrainingStateAccess {
    val uiState: StateFlow<TrainingUiState>
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}
```

Each module updates only its own nested state sub-tree:
```kotlin
// SessionRunner updating only session state
stateAccess.updateState { it.copy(session = it.session.copy(correctCount = it.session.correctCount + 1)) }
```

---

## 6. State Ownership Rules

After refactoring, each piece of state has EXACTLY ONE owner:

| State | Owner | Access Pattern |
|-------|-------|---------------|
| `session.*` fields | SessionRunner | Direct write, others read via `uiState` |
| `boss.*` fields | BossBattleRunner | Direct write, others read |
| `elite.*` fields | SessionRunner (sub-mode) | Direct write, others read |
| `drill.*` fields | SessionRunner (sub-mode) | Direct write, others read |
| `dailySession` | DailyPracticeCoordinator | Direct write, others read |
| `dailyCursor` | DailyPracticeCoordinator | Direct write, others read |
| `vocabSprint.*` fields | VocabSprintRunner | Direct write, others read |
| `flowers.*` fields | FlowerProgressRenderer | Direct write, others read |
| `audio.*` fields | AudioCoordinator | Direct write, others read |
| `selectedLanguageId`, `selectedLessonId`, `activePackId` | TrainingViewModel | Direct write, modules never write |
| `currentStreak`, `streakMessage` | StreakManager via ViewModel | StreakManager computes, ViewModel writes |
| `testMode`, `userName`, `vocabSprintLimit` | TrainingViewModel | Settings state, modules read-only |

**Cross-module state access:** Read-only via `uiState.value`. Write only through `updateState` on owned sub-tree. No module reads private vars from another module.

**No shared mutable state:** All mutable state is either:
1. In `MutableStateFlow<TrainingUiState>` (single owner per sub-tree)
2. In data stores (data layer, accessed through ProgressTracker facade)
3. In Android framework objects (AudioCoordinator owns TtsEngine/AsrEngine)

---

## 7. Naming Conventions

### Package Structure

```
com.alexpo.grammermate/
  data/                           # Data layer (unchanged)
    Models.kt                     # Data classes
    LessonStore.kt                # Lesson persistence
    MasteryStore.kt               # Mastery persistence
    ProgressStore.kt              # Progress persistence
    VerbDrillStore.kt             # Verb drill persistence
    WordMasteryStore.kt           # Vocab SRS persistence
    FlowerCalculator.kt           # Pure computation
    SpacedRepetitionConfig.kt     # Pure computation
    MixedReviewScheduler.kt       # Pure computation
    Normalizer.kt                 # Pure computation
    CardSessionContract.kt        # Interface for session providers
    ...

  ui/                             # UI layer
    GrammarMateApp.kt             # Router (unchanged)
    TrainingViewModel.kt          # Thin orchestrator (~400 lines)
    TrainingUiState.kt            # State data class (moved from TrainingViewModel.kt)
    VerbDrillViewModel.kt         # Separate ViewModel (unchanged)
    VocabDrillViewModel.kt        # Separate ViewModel (unchanged)
    VerbDrillCardSessionProvider.kt  # CardSessionContract adapter (unchanged)

    helpers/                      # Domain modules
      TrainingStateAccess.kt      # Shared interface (already exists)
      AnswerValidator.kt          # Pure function module
      WordBankGenerator.kt        # Pure function module
      FlowerProgressRenderer.kt   # Flower state computation
      CardProvider.kt             # Card selection algorithms
      BossBattleRunner.kt         # Boss battle logic
      StreakManager.kt            # Streak tracking
      SessionRunner.kt            # Training session lifecycle
      DailyPracticeCoordinator.kt # Daily practice orchestration
      VocabSprintRunner.kt        # Vocab sprint session
      ProgressTracker.kt          # Mastery/progress facade
      AudioCoordinator.kt         # TTS/ASR management
      DailySessionComposer.kt     # Session building (already exists)
      DailyPracticeSessionProvider.kt  # CardSessionContract adapter (already exists)
```

### File Naming Rules

| Type | Pattern | Example |
|------|---------|---------|
| Domain module | `PascalCase.kt` -- noun describing responsibility | `SessionRunner.kt`, `BossBattleRunner.kt` |
| Interface | Defined inside the module file, not separate | `interface SessionRunner { ... }` inside `SessionRunner.kt` |
| Implementation | `ClassNameImpl` or just `ClassName` if only one impl | `class SessionRunnerImpl(...) : SessionRunner` |
| Data class group | `PascalCase.kt` -- one file per domain concept | `TrainingUiState.kt` for all UI state classes |
| Adapter (CardSessionContract) | `XxxSessionProvider.kt` | `DailyPracticeSessionProvider.kt` |
| Pure function module | `PascalCase.kt` -- can be `object` or class | `object AnswerValidator { ... }` |

### Class Naming Patterns

- **Modules**: Noun + role suffix: `SessionRunner`, `CardProvider`, `ProgressTracker`, `AudioCoordinator`, `StreakManager`, `BossBattleRunner`
- **Interfaces**: Same name as module. Implementation suffixes with `Impl` only if there are multiple implementations.
- **State classes**: `XxxState` suffix: `SessionState`, `BossState`, `EliteState`, `AudioState`
- **Results**: `XxxResult` suffix: `SubmitResult`, `ValidationResult`, `BossResult`
- **Events**: `XxxEvent` if using event-based communication (not planned for this architecture)

---

## 8. Constraints and Non-Goals

### Must Maintain

1. **Single ViewModel pattern**: TrainingViewModel remains the ONLY ViewModel for the main app flow. VerbDrillViewModel and VocabDrillViewModel are standalone features with their own screens.
2. **GrammarMateApp.kt compatibility**: The router's `val state by vm.uiState.collectAsState()` pattern and all `vm.someMethod()` calls must continue to work. This is why we keep TrainingViewModel as a thin public API facade.
3. **CardSessionContract**: VerbDrillCardSessionProvider and DailyPracticeSessionProvider already implement this interface. They remain unchanged.
4. **TrainingStateAccess pattern**: All helpers receive state access through this interface (already established in `DailySessionHelper.kt`).

### Non-Goals

1. **Breaking GrammarMateApp.kt into separate screen files** -- that is a separate UI decomposition task.
2. **Merging VerbDrillViewModel/VocabDrillViewModel into TrainingViewModel** -- they have separate UI flows and should stay independent.
3. **Switching to MVI or other architecture patterns** -- this proposal keeps MVVM with a single ViewModel.
4. **Adding dependency injection framework** -- modules are instantiated in TrainingViewModel's `init` block. DI is a future enhancement, not required for decomposition.

---

## 9. Estimated Sizes After Refactoring

| File | Current Lines | Target Lines |
|------|--------------|-------------|
| TrainingViewModel.kt | 3615 | ~400 |
| TrainingUiState.kt (new) | -- | ~200 |
| SessionRunner.kt | -- | ~450 |
| DailyPracticeCoordinator.kt | -- | ~350 |
| BossBattleRunner.kt | -- | ~200 |
| CardProvider.kt | -- | ~250 |
| AnswerValidator.kt | -- | ~80 |
| WordBankGenerator.kt | -- | ~120 |
| ProgressTracker.kt | -- | ~300 |
| AudioCoordinator.kt | -- | ~300 |
| StreakManager.kt | -- | ~80 |
| VocabSprintRunner.kt | -- | ~250 |
| FlowerProgressRenderer.kt | -- | ~60 |
| **Total** | **3615** | **~3040** |

Net reduction: ~575 lines (from removing duplicated logic -- word bank generation, answer validation, state reset blocks). More importantly, the maximum file size drops from 3615 to 450 lines, and every module is independently testable.
