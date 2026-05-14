# Phase 3: Stateful Module Interfaces

Detailed interface contracts for the 5 stateful modules that own `TrainingStateAccess` and manage session state. These are extracted from the actual method signatures and behavior in `TrainingViewModel.kt` (lines referenced from `08-training-viewmodel.md`).

---

## 1. ProgressTracker

### Responsibility

Single source of truth for all mastery tracking, progress persistence, and lesson completion detection. Wraps MasteryStore + ProgressStore behind a coherent API.

### Interface

```kotlin
interface ProgressTracker {

    // ── Card show tracking (VOICE/KEYBOARD only, WORD_BANK excluded) ──

    fun recordCardShowForMastery(
        card: SentenceCard,
        bossActive: Boolean,
        isDrillMode: Boolean,
        inputMode: InputMode,
        selectedLanguageId: String,
        lessons: List<Lesson>,
        selectedLessonId: String?
    )
    // Lines 2989-3010. Skips if bossActive, isDrillMode, or inputMode == WORD_BANK.

    fun markSubLessonCardsShown(
        cards: List<SentenceCard>,
        inputMode: InputMode,
        selectedLessonId: String?,
        selectedLanguageId: String,
        lessons: List<Lesson>
    )
    // Lines 3012-3023. Only operates when inputMode == WORD_BANK.

    // ── Lesson completion ──

    fun checkAndMarkLessonCompleted(
        completedSubLessonCount: Int,
        selectedLessonId: String?,
        selectedLanguageId: String
    )
    // Lines 3028-3035. Marks lesson completed when completedSubLessonCount >= 15.

    fun calculateCompletedSubLessons(
        subLessons: List<ScheduledSubLesson>,
        mastery: LessonMasteryState?,
        lessonId: String?,
        lessons: List<Lesson>
    ): Int
    // Lines 3040-3067. Returns count of consecutive completed sub-lessons from start.
    // NOTE: CardProvider has a private copy of this logic used in buildSessionCards().
    // During Phase 3, ProgressTracker should own this; CardProvider should delegate to it
    // rather than duplicating.

    fun resolveCardLessonId(
        card: SentenceCard,
        selectedLessonId: String?,
        lessons: List<Lesson>
    ): String
    // Lines 2968-2982. Resolves which lesson a card belongs to.

    // ── Progress-based lesson info ──

    fun resolveProgressLessonInfo(
        activePackId: String?,
        selectedLanguageId: String,
        activePackLessonIds: List<String>?,
        lessons: List<Lesson>,
        dailyCursor: DailyCursorState
    ): Pair<String, Int>?
    // Lines 2533-2572. Returns (lessonId, level) for the user's actual progress position.

    fun getProgressLessonLevel(
        activePackId: String?,
        selectedLanguageId: String,
        activePackLessonIds: List<String>?,
        lessons: List<Lesson>,
        dailyCursor: DailyCursorState
    ): Int
    // Lines 2578-2580. Delegates to resolveProgressLessonInfo, returns level or 1.

    // ── Persistence ──

    fun saveProgress(state: TrainingUiState, forceBackup: Boolean): Boolean
    // Lines 2488-2522. Serializes state to ProgressStore. Skips if bossActive && !ELITE.
    // Returns true if backup was triggered (forceBackup = true).

    // ── Cursor ──

    fun advanceCursor(
        currentCursor: DailyCursorState,
        sentenceCount: Int,
        selectedLanguageId: String
    ): DailyCursorState
    // Lines 1546-1564. Advances sentence offset, auto-advances lesson index when exhausted.

    fun storeFirstSessionCardIds(
        sentenceIds: List<String>,
        verbIds: List<String>
    ): DailyCursorState
    // Lines 1527-1539. Returns updated cursor with first-session card IDs stored.

    // ── Reset ──

    fun resetAllProgress()
    // Line 1155-1200 (partial). Clears mastery + progress stores for active language.
}
```

### Owned State (TrainingUiState fields this module reads/writes)

This module is primarily a **reader** of state (takes values as parameters) and a **writer** to data stores. It does NOT directly update TrainingUiState. The ViewModel calls it and applies results.

Reads: `bossActive`, `isDrillMode`, `inputMode`, `selectedLanguageId`, `selectedLessonId`, `lessons`, `activePackId`, `activePackLessonIds`, `dailyCursor`, `completedSubLessonCount`, `currentIndex`, `correctCount`, `incorrectCount`, `incorrectAttemptsForCard`, `activeTimeMs`, `sessionState`, `bossLessonRewards`, `bossMegaRewards`, `voiceActiveMs`, `voiceWordCount`, `hintCount`, `eliteStepIndex`, `eliteBestSpeeds`, `currentScreen`, `activePackId`, `dailySession`

### Owned Stores

| Store | Usage |
|-------|-------|
| `MasteryStore` | `recordCardShow()`, `markCardsShownForProgress()`, `markLessonCompleted()`, `get()` |
| `ProgressStore` | `save()`, `load()` |

### Dependencies

None (pure data layer wrapper). Other modules depend on ProgressTracker.

### Migration Source

| Method | Lines | Notes |
|--------|-------|-------|
| `saveProgress()` | 2488-2522 | Core persistence |
| `recordCardShowForMastery()` | 2989-3010 | Mastery tracking with WORD_BANK guard |
| `markSubLessonCardsShown()` | 3012-3023 | WORD_BANK batch marking |
| `checkAndMarkLessonCompleted()` | 3028-3035 | 15-sublesson completion check |
| `calculateCompletedSubLessons()` | 3040-3067 | Sequential sub-lesson count |
| `resolveCardLessonId()` | 2968-2982 | Card-to-lesson mapping |
| `resolveProgressLessonInfo()` | 2533-2572 | Progress-based lesson resolution |
| `getProgressLessonLevel()` | 2578-2580 | Public helper |
| `advanceCursor()` | 1546-1564 | Daily cursor advancement |
| `storeFirstSessionCardIds()` | 1527-1539 | First-session card ID storage |

### Risks

1. **`saveProgress()` reads 25+ fields from state** -- extracting to a parameter object or passing the whole `TrainingUiState` is necessary. If the state shape changes, this method must be updated.
2. **`forceBackupOnSave` is a private var** in TrainingViewModel -- this side-channel flag must either be passed as a parameter or ProgressTracker must own it.
3. **`resolveProgressLessonInfo` reads `dailyCursor`** from state but is used by DailyPracticeCoordinator -- this creates a dependency. Solution: pass cursor as parameter (already designed this way).
4. **Thread safety**: All store operations are synchronous file I/O. No coroutine scope needed. Safe to call from any thread.

### Estimated Lines

~300

---

## 2. BossBattleRunner

### Responsibility

Manages boss battle session lifecycle -- card pool creation, progress tracking, reward thresholds, and session teardown. Pure logic that returns results for the ViewModel to apply.

### Interface

```kotlin
interface BossBattleRunner {

    // ── Session start ──

    fun startBoss(
        type: BossType,
        lessons: List<Lesson>,
        selectedLessonId: String?,
        selectedIndex: Int,
        completedSubLessonCount: Int,
        testMode: Boolean,
        bossCards: List<SentenceCard>  // pre-built by CardProvider.buildBossCards()
    ): BossStartResult
    // Lines 2228-2303. Validates eligibility, applies pre-built card pool, returns state.
    // startBossLesson/startBossMega/startBossElite are thin wrappers that call this.
    // NOTE: Card building is delegated to CardProvider.buildBossCards() by the ViewModel.
    // BossBattleRunner receives the cards, validates eligibility, and returns the result.

    // ── Progress tracking ──

    fun updateBossProgress(
        progress: Int,
        currentTotal: Int,
        currentReward: BossReward?
    ): BossProgressUpdate
    // Lines 2394-2415. Calculates reward tier, detects new reward threshold.
    // Returns updated state for ViewModel to apply.

    // ── Session finish ──

    fun finishBoss(
        bossType: BossType?,
        bossProgress: Int,
        bossTotal: Int,
        selectedLessonId: String?,
        currentLessonRewards: Map<String, BossReward>,
        currentMegaRewards: Map<String, BossReward>
    ): BossFinishResult
    // Lines 2305-2365. Calculates final reward, updates reward maps.

    // ── Reward message ──

    fun clearBossRewardMessage(
        bossActive: Boolean,
        sessionState: SessionState,
        currentCard: SentenceCard?,
        inputMode: InputMode
    ): BossRewardClearResult
    // Lines 2367-2388. Determines if timer should resume and voice should trigger.

    // ── Pure functions ──

    fun resolveBossReward(progress: Int, total: Int): BossReward?
    // Lines 2417-2426. 30%=Bronze, 60%=Silver, 90%=Gold.

    fun bossRewardMessage(reward: BossReward): String
    // Lines 2428-2434. "Bronze reached" / "Silver reached" / "Gold reached".

    // ── Result data classes ──

    data class BossStartResult(
        val success: Boolean,
        val cards: List<SentenceCard>,
        val errorMessage: String? = null,
        val subLessonTotal: Int = 0,
        val subLessonCount: Int = 1
    )

    data class BossProgressUpdate(
        val nextProgress: Int,
        val nextReward: BossReward?,
        val rewardMessage: String?,
        val isNewReward: Boolean,
        val shouldPause: Boolean
    )

    data class BossFinishResult(
        val reward: BossReward?,
        val updatedLessonRewards: Map<String, BossReward>,
        val updatedMegaRewards: Map<String, BossReward>
    )

    data class BossRewardClearResult(
        val shouldResumeTimer: Boolean,
        val shouldTriggerVoice: Boolean
    )
}
```

### Owned State (TrainingUiState fields)

**Writes** (via ViewModel applying results): `bossActive`, `bossType`, `bossTotal`, `bossProgress`, `bossReward`, `bossRewardMessage`, `bossFinishedToken`, `bossLastType`, `bossErrorMessage`, `bossLessonRewards`, `bossMegaRewards`

**Reads**: `completedSubLessonCount`, `testMode`, `selectedLessonId`, `lessons`, `sessionState`, `currentCard`, `inputMode`

### Owned Stores

None (pure logic module). Uses ProgressTracker and CardProvider indirectly through the ViewModel.

### Dependencies

- `CardProvider.buildBossCards(lessons, type, selectedLessonId, selectedIndex)` for card pool construction. NOTE: Elite card building is handled by `buildBossCards(type=ELITE)` -- there is no separate `buildEliteCards()` method in CardProvider. The `eliteStepCount` and `eliteSizeMultiplier` are CardProvider constructor params, so BossBattleRunner delegates to CardProvider rather than computing elite size itself.
- No direct store access.

### Migration Source

| Method | Lines | Notes |
|--------|-------|-------|
| `startBossLesson()` | 2216-2218 | Thin wrapper |
| `startBossMega()` | 2220-2222 | Thin wrapper |
| `startBossElite()` | 2224-2226 | Thin wrapper |
| `startBoss(type)` | 2228-2303 | Main start logic. Should delegate to CardProvider.buildBossCards() instead of building cards inline. |
| `finishBoss()` | 2305-2365 | Session teardown + reward update |
| `clearBossRewardMessage()` | 2367-2388 | Resume logic |
| `clearBossError()` | 2390-2392 | Simple state clear |
| `updateBossProgress()` | 2394-2415 | Progress + reward calculation |
| `resolveBossReward()` | 2417-2426 | Pure function |
| `bossRewardMessage()` | 2428-2434 | Pure function |

### Risks

1. **`finishBoss()` calls `buildSessionCards()`, `saveProgress()`, `refreshFlowerStates()`** -- these cross-module calls mean BossBattleRunner must either return a result and let the ViewModel orchestrate, or take callbacks. Recommendation: return `BossFinishResult` and let ViewModel call the other modules.
2. **`startBoss()` writes to `sessionCards` and `bossCards` private vars** -- these must remain in the ViewModel or SessionRunner. BossBattleRunner returns the card list; ViewModel/SessionRunner assigns it.
3. **Reward thresholds (30/60/90%)** are business-critical. Must be unit-tested during extraction.

### Estimated Lines

~200

---

## 3. SessionRunner

### Responsibility

Manages training session lifecycle for lesson, elite, and drill modes -- card navigation, answer submission orchestration, timer, word bank interaction, and sub-lesson transitions.

### Interface

```kotlin
interface SessionRunner {

    // ── Session lifecycle ──

    fun startSession()
    // Lines 2436-2459. Builds cards if needed, activates timer, records first card mastery.

    fun finishSession(): SessionFinishResult
    // Lines 940-971. Pauses timer, returns summary metrics.

    fun resumeFromSettings()
    // Lines 1289-1292. Delegates to startSession.

    // ── Input ──

    fun onInputChanged(text: String)
    // Lines 353-362. Updates inputText, clears answer text after 1 incorrect attempt.

    fun onVoicePromptStarted()
    // Lines 364-368. Records voice prompt start timestamp.

    fun setInputMode(mode: InputMode)
    // Lines 370-391. Switches input mode, resets voice state.

    // ── Answer submission ──

    fun submitAnswer(
        sessionCards: List<SentenceCard>,
        lessonSchedules: Map<String, LessonSchedule>,
        eliteStepCount: Int
    ): SubmitResult
    // Lines 624-849. Complex branching method. Orchestrates across modules:
    //   - boss branch: delegates to BossBattleRunner
    //   - elite branch: handles elite step completion
    //   - drill branch: delegates to advanceDrillCard
    //   - last card: triggers sub-lesson completion, flower refresh, streak update
    //   - normal: advances to next card

    // ── Card navigation ──

    fun nextCard(triggerVoice: Boolean)
    // Lines 851-904. Advances index, updates boss progress if boss active.

    fun prevCard()
    // Lines 906-922. Decrements index.

    fun selectSubLesson(index: Int)
    // Lines 1377-1391. Switches active sub-lesson, rebuilds cards.

    // ── Pause/resume ──

    fun togglePause()
    // Lines 924-932.

    fun pauseSession()
    // Lines 934-938.

    // ── Hint ──

    fun showAnswer()
    // Lines 973-986. Shows correct answer, pauses timer.

    // ── Word bank interaction ──

    fun selectWordFromBank(word: String)
    // Lines 3174-3185. Appends word to selection.

    fun removeLastSelectedWord()
    // Lines 3190-3203. Removes last word from selection.

    // ── Skip ──

    fun skipToNextCard(sessionCards: List<SentenceCard>)
    // Lines 3598-3623. Skips current card, advances to next available.

    // ── Elite sub-mode ──

    fun openEliteStep(index: Int, eliteStepCount: Int)
    // Lines 1393-1425. Starts elite step with fresh card set.

    fun cancelEliteSession()
    // Lines 1427-1444. Exits elite mode, resets state.

    fun resolveEliteUnlocked(lessons: List<Lesson>, testMode: Boolean): Boolean
    // Lines 2582-2584. testMode || lessons.size >= 12

    fun normalizeEliteSpeeds(speeds: List<Double>): List<Double>
    // Lines 2586-2592. Trims to eliteStepCount size.

    fun eliteSubLessonSize(): Int
    // Lines 2594-2596. Calculates from eliteSizeMultiplier.

    fun calculateSpeedPerMinute(activeMs: Long, words: Int): Double
    // Lines 2598-2602. Words per minute calculation.

    // ── Drill sub-mode ──

    fun showDrillStartDialog(lessonId: String)
    // Lines 1817-1827. Checks drill cards exist, shows dialog.

    fun startDrill(resume: Boolean)
    // Lines 1829-1880. Initializes drill mode, loads first card.

    fun dismissDrillDialog()
    // Lines 1882-1884.

    fun loadDrillCard(cardIndex: Int, activate: Boolean)
    // Lines 1886-1914. Loads a specific drill card.

    fun advanceDrillCard()
    // Lines 1916-1929. Moves to next drill card or finishes.

    fun finishDrill(lessonId: String)
    // Lines 1931-1947. Completes drill, clears drill state.

    fun exitDrillMode()
    // Lines 1949-1975. Exits drill, saves progress, resets state.

    // ── Timer (private in implementation) ──

    // resumeTimer()  - Lines 2467-2480
    // pauseTimer()   - Lines 2482-2486
    // currentCard()  - Lines 2461-2465

    // ── Result types ──

    data class SessionFinishResult(
        val active: Boolean,
        val correctCount: Int,
        val incorrectCount: Int,
        val activeTimeMs: Long,
        val voiceActiveMs: Long,
        val voiceWordCount: Int
    )
}
```

### Owned State (TrainingUiState fields)

**Writes**: `sessionState`, `currentIndex`, `currentCard`, `inputText`, `correctCount`, `incorrectCount`, `incorrectAttemptsForCard`, `activeTimeMs`, `voiceActiveMs`, `voiceWordCount`, `hintCount`, `voicePromptStartMs`, `answerText`, `lastResult`, `lastRating`, `inputMode`, `voiceTriggerToken`, `subLessonTotal`, `subLessonCount`, `subLessonTypes`, `activeSubLessonIndex`, `completedSubLessonCount`, `subLessonFinishedToken`, `wordBankWords`, `selectedWords`, `eliteActive`, `eliteStepIndex`, `eliteBestSpeeds`, `eliteFinishedToken`, `isDrillMode`, `drillCardIndex`, `drillTotalCards`, `drillShowStartDialog`, `drillHasProgress`

**Reads**: All of the above plus `bossActive`, `selectedLessonId`, `selectedLanguageId`, `lessons`, `mode`, `testMode`

### Owned Stores

| Store | Usage |
|-------|-------|
| `DrillProgressStore` | Drill mode progress: `hasProgress()`, `getDrillProgress()`, `saveDrillProgress()`, `clearDrillProgress()` |
| `BadSentenceStore` | Drill mode bad sentence count on exit |

### Dependencies

| Module | Usage |
|--------|-------|
| `TrainingStateAccess` | State reads and writes |
| `ProgressTracker` | `recordCardShowForMastery()`, `markSubLessonCardsShown()`, `checkAndMarkLessonCompleted()`, `saveProgress()` |
| `BossBattleRunner` | `updateBossProgress()`, `resolveBossReward()`, `bossRewardMessage()` (via ViewModel delegation) |
| `CardProvider` | `buildSchedules(lessons, existingSchedules)`, `buildSessionCards(lessons, mode, lessonId, schedules, index, hiddenIds, mastery)`, `buildBossCards(lessons, type, lessonId, selectedIndex)` |
| `AnswerValidator` | `validate(input, acceptedAnswers, testMode)` for answer checking; `shouldShowHint(attempts)` for hint threshold; `getHintText(acceptedAnswers)` for hint text |
| `WordBankGenerator` | `generateForSentence(targetAnswer, allCards, maxDistractors)` for sentence word banks; `generateForVerb(answer, allAnswers, maxDistractors)` for verb drill word banks. NOTE: `updateWordBank()` remains in SessionRunner/ViewModel -- it reads state and calls WordBankGenerator |
| `FlowerProgressRenderer` | NOTE: `refreshFlowerStates()` is NOT a method on FlowerProgressRenderer. It is a ViewModel-level orchestration method that reads MasteryStore and calls `FlowerProgressRenderer.buildLessonTiles()` + `computeFlowerState()`. SessionRunner triggers it via ViewModel callback, not directly. |
| `StreakManager` | `recordSubLessonCompletion(languageId): Pair<StreakData, Boolean>` + `getCelebrationMessage(streakCount): String?`. NOTE: `updateStreak()` is a ViewModel-level method that calls StreakStore + StreakManager. SessionRunner triggers it via ViewModel callback. |
| `AudioCoordinator` | `playSuccessSound()`, `playErrorSound()` |

### Migration Source

33 methods total (see Section 6 in `08-training-viewmodel.md`, SessionRunner checklist).

Key methods:
| Method | Lines | Notes |
|--------|-------|-------|
| `submitAnswer()` | 624-849 | Most complex method. Branches across 5+ modules. |
| `nextCard()` | 851-904 | Boss progress + word bank update. |
| `startSession()` | 2436-2459 | Timer + card building. |
| `startDrill()` | 1829-1880 | Drill mode initialization. |
| `openEliteStep()` | 1393-1425 | Elite sub-mode initialization. |

### Risks

1. **`submitAnswer()` is a 225-line mega-method** spanning 5 module domains. Decomposition is high-risk. Recommendation: extract in stages -- first move boss/elite/drill branches to their respective handlers, then collapse the remaining flow.
2. **Timer uses `viewModelScope`** -- SessionRunner needs a CoroutineScope. Can receive it from ViewModel.
3. **`sessionCards`, `bossCards`, `eliteCards` are private vars** -- these must live in SessionRunner. BossBattleRunner returns card lists; SessionRunner assigns them to its internal lists.
4. **`lessonSchedules` is shared with CardProvider** -- CardProvider owns schedule building; SessionRunner reads via a getter.
5. **Cross-module calls in `submitAnswer()`** (after last card: `buildSessionCards()`, `markSubLessonCardsShown()`, `checkAndMarkLessonCompleted()`, `refreshFlowerStates()`, `updateStreak()`) -- these must be coordinated through callbacks or return types. The ViewModel acts as orchestrator.

### Estimated Lines

~450

---

## 4. DailyPracticeCoordinator

### Responsibility

Orchestrates the 3-block daily practice session (Translate, Vocab, Verbs). Merges `DailySessionHelper` + `DailySessionComposer` + TrainingViewModel daily methods into a single coherent module.

### Interface

```kotlin
interface DailyPracticeCoordinator {

    // ── Session start/resume ──

    fun hasResumableDailySession(): Boolean
    // Lines 1448-1455. Checks if cursor has today's first-session card data.

    fun startDailyPractice(lessonLevel: Int): Boolean
    // Lines 1457-1521. Full start flow: uses pre-built or builds session,
    // stores first-session card IDs, starts DailySessionHelper session.

    fun repeatDailyPractice(lessonLevel: Int): Boolean
    // Lines 1566-1619. Rebuilds session from cached card IDs or builds fresh.

    // ── Task/block navigation ──

    fun advanceDailyTask(): Boolean
    // Lines 1621-1628. Persists verb progress, advances task index.

    fun advanceDailyBlock(): Boolean
    // Lines 1652-1657. Delegates to DailySessionHelper.

    fun repeatDailyBlock(): Boolean
    // Lines 1677-1699. Rebuilds current block with new cards via DailySessionComposer.

    fun cancelDailySession()
    // Lines 1701-1722. Conditionally advances cursor, ends session.

    // ── Current state queries ──

    fun getDailyCurrentTask(): DailyTask?
    // Lines 1757-1759. Delegates to DailySessionHelper.

    fun getDailyBlockProgress(): BlockProgress
    // Lines 1761-1763. Delegates to DailySessionHelper.

    // ── Answer submission ──

    fun submitDailySentenceAnswer(input: String): Boolean
    // Lines 1765-1776. Validates, plays sound.

    fun submitDailyVerbAnswer(input: String): Boolean
    // Lines 1778-1789. Validates, plays sound.

    // ── Answer retrieval ──

    fun getDailySentenceAnswer(): String?
    // Lines 1791-1794.

    fun getDailyVerbAnswer(): String?
    // Lines 1796-1799.

    // ── Card practiced tracking ──

    fun recordDailyCardPracticed(blockType: DailyBlockType)
    // Lines 1635-1650. Tracks per-block VOICE/KEYBOARD answers,
    // records mastery for TRANSLATE block cards.

    // ── Verb progress ──

    fun persistDailyVerbProgress(card: VerbDrillCard)
    // Lines 1659-1675. Updates VerbDrillStore with shown card IDs.

    // ── Vocab SRS ──

    fun rateVocabCard(rating: Int)
    // Lines 1728-1755. Updates WordMasteryStore with SRS rating.

    // ── Internal: cursor management ──

    // advanceCursor() -> delegated to ProgressTracker
    // storeFirstSessionCardIds() -> delegated to ProgressTracker

    // ── Internal: session helper absorption ──

    // startDailySession() -> from DailySessionHelper
    // getCurrentTask() -> from DailySessionHelper
    // nextTask() -> from DailySessionHelper
    // advanceToNextBlock() -> from DailySessionHelper
    // replaceCurrentBlock() -> from DailySessionHelper
    // endSession() -> from DailySessionHelper
    // getBlockProgress() -> from DailySessionHelper
    // fastForwardTo() -> from DailySessionHelper
}
```

### Owned State (TrainingUiState fields)

**Writes**: `dailySession`, `dailyCursor`

**Reads**: `activePackId`, `selectedLanguageId`, `dailyCursor`, `dailySession`

### Owned Private Mutable State

| Variable | Type | Purpose |
|----------|------|---------|
| `prebuiltDailySession` | `List<DailyTask>?` | Pre-computed daily session from init |
| `lastDailyTasks` | `List<DailyTask>?` | In-memory cache for repeat |
| `dailyPracticeAnsweredCounts` | `MutableMap<DailyBlockType, Int>` | Per-block VOICE/KEYBOARD tracking |
| `dailyCursorAtSessionStart` | `DailyCursorState` | Snapshot for cancel rollback |

### Owned Stores

| Store | Usage |
|-------|-------|
| `VerbDrillStore` | `loadProgress()`, `upsertComboProgress()` |
| `WordMasteryStore` | `getMastery()`, `upsertMastery()` |
| `LessonStore` | `getLessons()`, `getCumulativeTenses()` |

### Dependencies

| Module | Usage |
|--------|-------|
| `TrainingStateAccess` | State reads and writes |
| `DailySessionComposer` | `buildSession()`, `buildRepeatSession()`, `rebuildBlock()` |
| `ProgressTracker` | `advanceCursor()`, `storeFirstSessionCardIds()`, `resolveProgressLessonInfo()`, `resolveCardLessonId()`, `saveProgress()` |
| `AnswerValidator` | **Phase 3 replacement**: currently `submitDailySentenceAnswer()` and `submitDailyVerbAnswer()` use `Normalizer.normalize()` directly. During Phase 3, replace with `AnswerValidator.validate(input, acceptedAnswers)`. |
| `AudioCoordinator` | `playSuccessSound()`, `playErrorSound()` (via ViewModel delegation) |
| `MasteryStore` | `recordCardShow()` for TRANSLATE block mastery |

Note: `DailySessionHelper` is absorbed into this module, not a dependency. The helper's logic becomes internal implementation.

### Migration Source

18 methods from TrainingViewModel (see Section 6 in `08-training-viewmodel.md`, DailyCoordinator checklist) + all methods from `DailySessionHelper` + `DailySessionComposer` remains a separate utility.

Key methods:
| Method | Lines | Notes |
|--------|-------|-------|
| `startDailyPractice()` | 1457-1521 | Most complex: multi-path start (cached vs built) |
| `cancelDailySession()` | 1701-1722 | Conditional cursor advance |
| `repeatDailyPractice()` | 1566-1619 | Multi-path repeat (cached vs rebuilt vs fresh) |
| `rateVocabCard()` | 1728-1755 | SRS rating logic |
| `recordDailyCardPracticed()` | 1635-1650 | Mastery tracking per block |

### Risks

1. **VerbDrillStore and WordMasteryStore are instantiated per-call** (`VerbDrillStore(getApplication(), packId = packId)`) -- these should be constructor-injected or provided via factory.
2. **`cancelDailySession()` conditionally advances cursor** based on answered counts -- this is tricky state logic that must be preserved exactly.
3. **`dailyPracticeAnsweredCounts` is mutable private state** that persists across method calls within a session. Must be owned by DailyCoordinator, not TrainingViewModel.
4. **`DailySessionHelper` is absorbed** -- any code that currently calls `dailySessionHelper` directly must be updated to go through the coordinator.
5. **`startDailyPractice()` creates `DailySessionComposer` each call** -- should be injected or provided via factory.

### Estimated Lines

~350

---

## 5. AudioCoordinator

### Responsibility

TTS playback, ASR recording, model download management, and sound effects. Owns all audio engine lifecycle and download state.

### Interface

```kotlin
interface AudioCoordinator {

    // ── TTS playback ──

    fun onTtsSpeak(text: String, speed: Float? = null)
    // Lines 2616-2631. Initializes engine if needed, speaks text.

    fun stopTts()
    // Lines 2743-2745. Stops current TTS playback.

    fun setTtsSpeed(speed: Float)
    // Lines 2633-2635. Updates TTS speed in state.

    fun setRuTextScale(scale: Float)
    // Lines 2637-2639. Updates RU text scale in state.

    // ── TTS downloads ──

    fun startTtsDownload()
    // Lines 2641-2648. Checks metered network, starts download.

    fun confirmTtsDownloadOnMetered()
    // Lines 2650-2653. Dismisses metered warning, proceeds with download.

    fun dismissMeteredWarning()
    // Lines 2655-2657. Dismisses metered network warning.

    fun dismissTtsDownloadDialog()
    // Lines 2663-2669. Resets download state to Idle if terminal.

    fun startTtsDownloadForLanguage(languageId: String)
    // Lines 2706-2741. Downloads TTS model for a specific language.

    fun setTtsDownloadStateFromBackground(bgState: DownloadState)
    // Lines 2939-2941. Mirrors background download state.

    // ── TTS model checks ──

    fun checkTtsModel()
    // Lines 2693-2697. Checks if current language TTS model is ready.

    fun checkAllTtsModels()
    // Lines 2699-2704. Checks all language TTS models.

    // ── ASR ──

    fun startOfflineRecognition(onResult: (String) -> Unit)
    // Lines 2760-2772. Starts ASR recording, calls onResult with recognized text.

    fun stopAsr()
    // Lines 2774-2776. Stops ASR recording.

    fun setUseOfflineAsr(enabled: Boolean)
    // Lines 2778-2788. Toggles offline ASR, persists to config.

    fun checkAsrModel()
    // Lines 2751-2754. Checks if ASR model is ready.

    // ── ASR downloads ──

    fun startAsrDownload()
    // Lines 2790-2796. Checks metered network, starts download.

    fun confirmAsrDownloadOnMetered()
    // Lines 2798-2801.

    fun dismissAsrMeteredWarning()
    // Lines 2803-2805.

    fun dismissAsrDownloadDialog()
    // Lines 2756-2758.

    // ── Sound effects ──

    fun playSuccessSound()
    // Lines 2952-2956 (playSuccessTone) + 1801-1805 (playSuccessSound).
    // UNIFIED: merge both into single method.

    fun playErrorSound()
    // Lines 2958-2962 (playErrorTone) + 1807-1811 (playErrorSound).
    // UNIFIED: merge both into single method.

    // ── Lifecycle ──

    fun release()
    // From onCleared() lines 2943-2950. Releases TTS engine, ASR engine, sound pool.

    // ── Background downloads ──

    fun startBackgroundTtsDownload()
    // Lines 2888-2937. Downloads TTS models for all missing languages.
}
```

### Owned State (TrainingUiState fields)

**Writes**: `ttsState` (via engine state collection), `ttsDownloadState`, `ttsModelReady`, `ttsMeteredNetwork`, `bgTtsDownloading`, `bgTtsDownloadStates`, `ttsModelsReady`, `ttsSpeed`, `ruTextScale`, `useOfflineAsr`, `asrState`, `asrModelReady`, `asrDownloadState`, `asrMeteredNetwork`, `asrErrorMessage`, `audioPermissionDenied`

**Reads**: `selectedLanguageId`, `ttsSpeed`, `ttsModelReady`, `ttsDownloadState`, `asrState`, `asrModelReady`, `asrErrorMessage`

### Owned Private Mutable State

| Variable | Type | Purpose |
|----------|------|---------|
| `soundPool` | `SoundPool` | Sound effect playback |
| `successSoundId` | `Int` | Loaded success sound ID |
| `errorSoundId` | `Int` | Loaded error sound ID |
| `loadedSounds` | `MutableSet<Int>` | Set of loaded sound IDs |
| `ttsDownloadJob` | `Job?` | Active TTS download coroutine |
| `asrDownloadJob` | `Job?` | Active ASR download coroutine |
| `bgDownloadJob` | `Job?` | Background TTS download coroutine |

### Owned Stores/Engines

| Instance | Type | Usage |
|----------|------|-------|
| `ttsEngine` | `TtsEngine` | `speak()`, `stop()`, `release()`, `initialize()`, state collection |
| `ttsModelManager` | `TtsModelManager` | `download()`, `downloadMultiple()`, `isModelReady()`, `isNetworkMetered()` |
| `asrEngine` | `AsrEngine?` | `recordAndTranscribe()`, `stopRecording()`, `release()`, `initialize()`, state collection |
| `asrModelManager` | `AsrModelManager` | `downloadVad()`, `downloadAsr()`, `isReady()`, `isVadReady()`, `isAsrReady()`, `isNetworkMetered()` |
| `configStore` | `AppConfigStore` | `load()`, `save()` for `useOfflineAsr` setting |

### Dependencies

| Dependency | Usage |
|------------|-------|
| `TrainingStateAccess` | State reads and writes |
| CoroutineScope | For `viewModelScope` (download jobs, ASR recording) |
| `Application` context | For engine initialization |

### Migration Source

28 methods (see Section 6 in `08-training-viewmodel.md`, AudioCoordinator checklist).

Plus init block audio initialization (lines 150-351 partial) and `onCleared()` cleanup (lines 2943-2950).

Key methods:
| Method | Lines | Notes |
|--------|-------|-------|
| `startTtsDownloadForLanguage()` | 2706-2741 | Complex: updates multiple state fields simultaneously |
| `startBackgroundTtsDownload()` | 2888-2937 | Multi-language download with state merging |
| `transcribeWithOfflineAsr()` | 2832-2882 | ASR lifecycle with error handling |
| `onTtsSpeak()` | 2616-2631 | Engine init + speak |

### Risks

1. **`viewModelScope` usage** -- download coroutines and ASR recording use `viewModelScope.launch`. AudioCoordinator must receive a CoroutineScope from the ViewModel.
2. **Engine state collection** -- `ttsEngine.state` and `asrEngine.state` are StateFlows collected in coroutines. These subscriptions must be moved to AudioCoordinator.
3. **`startOfflineRecognition` calls `onInputChanged()`** -- this cross-module call must be handled via callback parameter (`onResult: (String) -> Unit`) rather than direct coupling.
4. **Sound method deduplication** -- `playSuccessTone()`/`playSuccessSound()` and `playErrorTone()`/`playErrorSound()` are identical implementations. Must unify during extraction.
5. **`configStore` dependency** -- only `setUseOfflineAsr()` uses it. Could pass a simple lambda instead of the full store.
6. **Android context requirement** -- TtsEngine, AsrEngine, SoundPool all need Application context. AudioCoordinator must receive it.

### Estimated Lines

~300

---

## Appendix: Cross-Module Call Graph for Phase 3

```
                    ┌─────────────────┐
                    │  TrainingViewModel │
                    │  (thin orchestrator) │
                    └────────┬────────┘
                             │ owns all modules, routes calls
          ┌──────────┬───────┼───────┬──────────┐
          v          v       v       v          v
   ┌────────────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────────┐
   │ Session    │ │ Daily│ │ Boss │ │Audio │ │ Progress │
   │ Runner     │ │ Coord│ │Battle│ │Coord │ │ Tracker  │
   └─────┬──────┘ └──┬───┘ └──┬───┘ └──────┘ └──────────┘
         │            │        │
         │ uses       │ uses   │ uses
         v            v        v
   ┌──────────┐ ┌──────────┐ ┌──────────┐
   │CardProv  │ │Progress  │ │CardProv  │
   │AnswerVal │ │Tracker   │ │(via VM)  │
   │WordBank  │ │AudioCoord│ │          │
   │BossBattle│ │          │ │          │
   │Progress  │ │          │ │          │
   │StreakMgr │ │          │ │          │
   │AudioCoord│ │          │ │          │
   └──────────┘ └──────────┘ └──────────┘
```

**Key constraint from CLAUDE.md**: "Helpers never call other helpers directly; all coordination flows through TrainingViewModel."

This means:
- SessionRunner does NOT hold a reference to BossBattleRunner or ProgressTracker.
- SessionRunner returns results to the ViewModel, which calls the appropriate module.
- DailyPracticeCoordinator calls ProgressTracker through the ViewModel.
- AudioCoordinator is called by all modules but only through the ViewModel.

**Exception**: The existing `TrainingStateAccess` pattern allows helpers to read/write shared state directly. This is acceptable for reads but writes should respect module ownership.

---

## Appendix B: Phase 1-2 API Cross-Reference

Cross-reference between Phase 3 interface dependencies and actual Phase 1-2 module APIs. Verified against source code.

### AnswerValidator (actual API)

| Method | Signature |
|--------|-----------|
| `validate()` | `(input: String, acceptedAnswers: List<String>, testMode: Boolean = false): ValidationResult` |
| `shouldShowHint()` | `(incorrectAttempts: Int): Boolean` |
| `getHintText()` | `(acceptedAnswers: List<String>): String` |

**Phase 3 consumers**: SessionRunner (via ViewModel in `submitAnswer()`), DailyPracticeCoordinator (`submitDailySentenceAnswer()`, `submitDailyVerbAnswer()`).

**Key mismatch found**: `submitDailySentenceAnswer()` and `submitDailyVerbAnswer()` currently use `Normalizer.normalize()` directly, not AnswerValidator. Phase 3 should replace these with `AnswerValidator.validate()` calls.

### WordBankGenerator (actual API)

| Method | Signature |
|--------|-----------|
| `generateForSentence()` | `(targetAnswer: String, allCards: List<SentenceCard>, maxDistractors: Int = 3): List<String>` |
| `generateForVerb()` | `(answer: String, allAnswers: List<String>, maxDistractors: Int = 8): List<String>` |
| `isDistractor()` | `(candidate: String, normalizedCorrect: Set<String>): Boolean` |

**Phase 3 consumers**: SessionRunner (in `updateWordBank()` logic).

**Key mismatch found**: Phase 3 interface originally referenced `generateSentenceBank()` and `updateWordBank()`. Actual method names are `generateForSentence()` and `generateForVerb()`. `updateWordBank()` is NOT a WordBankGenerator method -- it's TrainingViewModel logic that reads state and calls WordBankGenerator. This stays in SessionRunner/ViewModel.

### FlowerProgressRenderer (actual API)

| Method | Signature |
|--------|-----------|
| `computeFlowerState()` | `(mastery: Int, totalCards: Int, lastPracticeDate: Long?, intervalStep: Int): FlowerVisual` |
| `computeHealth()` | `(lastPracticeDate: Long?, intervalStep: Int): Double` |
| `buildLessonTileState()` | `(lesson: Lesson, masteryState: LessonMasteryState?): LessonTileInfo` |
| `buildLessonTiles()` | `(lessons: List<Lesson>, masteryMap: Map<String, LessonMasteryState>): List<LessonTileInfo>` |
| `flowerEmoji()` | `(state: FlowerState): String` |

**Phase 3 consumers**: ViewModel orchestrates `refreshFlowerStates()` which reads MasteryStore and calls `buildLessonTiles()`.

**Key mismatch found**: `refreshFlowerStates()` is NOT a FlowerProgressRenderer method. It's a ViewModel-level orchestration method. SessionRunner triggers flower refresh via ViewModel callback after sub-lesson completion, not by calling FlowerProgressRenderer directly.

### StreakManager (actual API)

| Method | Signature |
|--------|-----------|
| `recordSubLessonCompletion()` | `(languageId: String): Pair<StreakData, Boolean>` |
| `getCelebrationMessage()` | `(streakCount: Int): String?` |

**Phase 3 consumers**: ViewModel orchestrates `updateStreak()` which calls `StreakStore.recordSubLessonCompletion()` then `StreakManager.getCelebrationMessage()`.

**Key mismatch found**: `updateStreak()` is NOT a StreakManager method. It's a ViewModel-level method that combines StreakStore + StreakManager + state writes. SessionRunner triggers it via ViewModel callback.

### CardProvider (actual API)

| Method | Signature |
|--------|-----------|
| `buildSchedules()` | `(lessons: List<Lesson>, existingSchedules: Map<String, LessonSchedule>): Map<String, LessonSchedule>` |
| `buildSessionCards()` | `(lessons, mode, selectedLessonId, schedules, activeSubLessonIndex, hiddenCardIds, mastery?): CardSetResult` |
| `buildBossCards()` | `(lessons: List<Lesson>, type: BossType, selectedLessonId: String?, selectedIndex: Int): List<SentenceCard>` |

**Phase 3 consumers**: SessionRunner (session cards, elite cards), BossBattleRunner (boss cards via ViewModel).

**Key mismatch found**: No `buildEliteCards()` method exists. Elite cards are built by `buildBossCards(type=ELITE)`. No `rebuildSchedules()` method -- it's `buildSchedules(lessons, existingSchedules)` with caching built in. Also `calculateCompletedSubLessons()` is duplicated between CardProvider (private) and ProgressTracker -- must be deduplicated in Phase 3.

### Summary of Corrections Applied

| Phase 3 Module | Original Reference | Corrected To |
|-----------------|-------------------|--------------|
| SessionRunner | `CardProvider.buildEliteCards()` | `CardProvider.buildBossCards(type=ELITE)` |
| SessionRunner | `CardProvider.rebuildSchedules()` | `CardProvider.buildSchedules(lessons, existingSchedules)` |
| SessionRunner | `WordBankGenerator.generateSentenceBank()` | `WordBankGenerator.generateForSentence()` |
| SessionRunner | `WordBankGenerator.updateWordBank()` | Stays in SessionRunner -- it reads state and calls WordBankGenerator |
| SessionRunner | `FlowerProgressRenderer.refreshFlowerStates()` | ViewModel callback -- not a direct call |
| SessionRunner | `StreakManager.updateStreak()` | `StreakManager.recordSubLessonCompletion()` + ViewModel orchestration |
| BossBattleRunner | Takes `eliteStepCount`, `eliteSizeMultiplier` params | Receives pre-built `bossCards` from CardProvider |
| DailyCoordinator | `AnswerValidator` as current dependency | Phase 3 replacement -- currently uses `Normalizer.normalize()` directly |
| ProgressTracker | `calculateCompletedSubLessons()` | Duplicated with CardProvider -- needs deduplication |
