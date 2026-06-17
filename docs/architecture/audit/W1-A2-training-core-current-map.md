# Training Core Architecture Map (Current State)

**Agent:** Wave 1 Agent A2  
**Scope:** Training Core (ViewModel, SessionRunner, CardProvider, State Machine)  
**Date:** 2025-01-22  
**Purpose:** Factual map of current training core architecture for safe refactoring audit

---

## Active User Flows

### 1. Lesson Flow (LESSON mode)
**Entry:** `TrainingViewModel.selectLesson(lessonId: String)` (line 595)

**Flow:**
1. Pause timer, clear vocab session, clear all cards (line 596-598)
2. Resolve pack for lesson and set as active (line 601-603)
3. Rebuild schedules using `MixedReviewScheduler` (line 605-606)
4. Calculate `activeSubLessonIndex` based on completed sub-lessons (line 608-619)
5. Reset session state, set navigation to LESSON mode (line 621-623)
6. Reset feature-owned state (boss, story, vocab, daily) (line 624-629)
7. Build session cards via `buildSessionCards()` (line 631)
8. Refresh flower states (line 632)
9. Save progress (line 633)

**Card Selection:** `CardProvider.buildSessionCards()` → `buildLessonSessionCards()` (line 202-233)
- Uses pre-built schedules from `lessonSchedules`
- Filters hidden cards
- Returns `CardSetResult` with cards + metadata

**Session Lifecycle:** `SessionRunner.startSession()` (line 174-206)
- Activates timer
- Records first card for mastery
- Resets state machine
- Triggers voice if needed
- Populates word bank

**Completion:** `SessionRunner.submitNormalLastCard()` (line 479-513)
- Pauses timer
- Calculates completed sub-lesson count
- Updates `activeSubLessonIndex` to next incomplete sub-lesson
- Returns events: `MarkSubLessonCardsShown`, `BuildSessionCards`, `CheckAndMarkLessonCompleted`, `RefreshFlowerStates`, `UpdateStreak`, `SaveProgress`

### 2. Review Flow
**Entry:** `TrainingViewModel.startReview(hintLevel: HintLevel)` (line 948)

**Flow:**
1. Get hidden card IDs from `HiddenCardStore` (line 950)
2. Build review cards: `CardProvider.buildReviewCards()` (line 951-955)
   - Returns all lesson cards (shuffled, filtered by hidden)
3. Start review: `SessionRunner.startReview()` (line 957)
   - Sets `isReviewMode = true`
   - Sets `hintLevel` parameter
   - Chooses default input mode based on hint level:
     - `EASY` → `WORD_BANK`
     - `MEDIUM` → `KEYBOARD`
     - `HARD` → `VOICE`

**Mastery:** Works normally (VOICE/KEYBOARD count, WORD_BANK does not)

### 3. Daily Practice Flow
**Entry:** `TrainingViewModel.startDailyPractice(lessonLevel: Int)` (line 968)

**Flow:**
1. Delegate to `DailyPracticeCoordinator.startDailyPractice()` (line 969)
2. Coordinator resolves progress lesson info (line 970-973)
3. Store first session card IDs in cursor state (line 971)
4. Coordinator builds session blocks:
   - Block 1: TRANSLATE (via `SessionRunner.startCardSession()`)
   - Block 2: VOCAB (inline)
   - Block 3: VERBS (via `SessionRunner.startCardSession()`)

**Cursor Management:**
- `PackDailyCursorStore` (line 92) maintains per-pack cursor
- `sentenceOffset`: cards shown in current lesson (0, 10, 20, ...)
- `verbOffset`: verb cards shown in current lesson (0, 10, 20, ...)
- `firstSessionSentenceCardIds`: card IDs from first session's block 1 (for Repeat)
- `firstSessionVerbCardIds`: card IDs from first session's block 3 (for Repeat)

**Completion:** `TrainingViewModel.onDailyBlockComplete()` (line 1020)
- Advances to next block
- On final block completion: advances cursor offsets

### 4. Verb Drill Flow
**Entry:** `TrainingViewModel.startVerbDrillSession(cards: List<VerbDrillCard>)` (line 770)

**Flow:**
1. `SessionRunner.startCardSession(cards, VERB_DRILL)` (line 771-772)
2. Sets `screenMode = VERB_DRILL`
3. Loads cards into `sessionCards`
4. Activates session with timer

**Card Replacement:** `TrainingViewModel.replaceVerbDrillCards(cards)` (line 788)
- `SessionRunner.replaceCards()` (line 789)
- Resets card navigation state (index, counts, input)
- Preserves `screenMode` and session context

**Exit:** `TrainingViewModel.exitVerbDrillSession()` (line 779)
- `SessionRunner.exitCardSession()` (line 780)
- Resets session state, clears cards, sets `screenMode = NORMAL`

### 5. Sequential/Mixed Flow
**Entry:** `TrainingViewModel.selectMode(mode: TrainingMode)` (line 660)

**ALL_SEQUENTIAL:**
- `CardProvider.buildSessionCards()` (line 107-109)
- Flattens all lesson cards into single list
- Filters hidden cards
- Paginates into blocks of `subLessonSize`

**ALL_MIXED:**
- `CardProvider.buildSessionCards()` (line 110-116)
- Flattens all lesson cards (including reserve pool)
- Filters hidden cards
- Shuffles
- Limits to `TrainingConfig.REVIEW_LIMIT`
- Paginates into blocks

---

## Main Classes and Responsibilities

### TrainingViewModel
**File:** `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`  
**Lines:** 105-2579 (~2579 lines)

**State:**
- `_coreState: MutableStateFlow<TrainingUiState>` (line 95) - Main state holder
- `lessonSchedules: Map<LessonId, LessonSchedule>` (line 128) - Cached schedules
- `sessionSize: Int` (line 132) - Current session size
- `subLessonTotal`, `subLessonCount` (line 126-127) - Session metadata

**Key Methods:**

**Navigation:**
- `selectLanguage(languageId: String)` (line 550) - Switch language, rebuild all
- `selectLesson(lessonId: String)` (line 595) - Switch lesson, calculate next sub-lesson
- `selectPack(packId: String)` (line 636) - Switch pack, resolve lesson
- `selectMode(mode: TrainingMode)` (line 660) - Switch training mode

**Session Operations:**
- `submitAnswer(): SubmitResult` (line 674) - Main answer submission entry point
- `nextCard(triggerVoice: Boolean)` (line 718) - Advance to next card
- `prevCard()` (line 741) - Go to previous card
- `navigateNext()` (line 744) - Browse forward (pause-first)
- `navigatePrev()` (line 747) - Browse backward (pause-first)
- `togglePause()` (line 749) - Toggle ACTIVE/PAUSED
- `finishSession()` (line 753) - End current session

**Card Operations:**
- `showAnswer()` (line 762) - Force-show hint
- `startVerbDrillSession(cards)` (line 770) - Start verb drill
- `replaceVerbDrillCards(cards)` (line 788) - Load next batch
- `exitVerbDrillSession()` (line 779) - Exit verb drill
- `startDailyTranslateSession(cards)` (line 802) - Start daily block 1
- `startDailyVerbsSession(cards)` (line 811) - Start daily block 3
- `exitDailySession()` (line 820) - Exit daily practice

**Progress:**
- `saveProgress()` (line 1414) - Persist state to stores
- `buildSessionCards()` (line 1439) - Rebuild card list from provider
- `rebuildSchedules(lessons)` (line 1474) - Rebuild schedules via MixedReviewScheduler

**Orchestration:**
- `handleSessionEvents(events: List<SessionEvent>)` (line 1299) - Process SessionRunner events
- `handleBossCommands(commands: List<BossCommand>)` (line 1479) - Process boss events

**Feature Accessors (delegation):**
- `val audio: AudioCoordinator` (line 293)
- `val training: SessionRunner` (line 295)
- `val boss: BossOrchestrator` (line 297)
- `val daily: DailyPracticeCoordinator` (line 299)
- `val vocab: VocabSprintRunner` (line 301)
- `val story: StoryRunner` (line 303)
- `val reports: BadSentenceHelper` (line 305)
- `val settings: SettingsActionHandler` (line 307)

**Dependencies (constructed in init):**
- `AnswerValidator` (line 115)
- `CardProvider` (line 148-155)
- `SessionRunner` (line 157-177)
- `ProgressTracker` (line 139-146)
- `StreakManager` (line 136)
- `BossBattleRunner` (line 137)
- `BossOrchestrator` (line 202-209)
- `DailyPracticeCoordinator` (line 184-195)
- `AudioCoordinator` (line 230-235)
- `PomodoroHelper` (line 239-256)

### SessionRunner
**File:** `app/src/main/java/com/alexpo/grammermate/feature/training/SessionRunner.kt`  
**Lines:** 51-1416 (~1416 lines)

**Responsibilities:**
- Owns training session lifecycle
- Manages card navigation and answer submission
- Runs timer
- Handles word bank interaction
- Manages elite sub-mode
- Implements `CardSessionStateModel` for unified state queries

**State:**
- `sessionCards: List<SessionCard>` (line 74) - Active card list
- `bossCards: List<SessionCard>` (line 75) - Boss battle cards
- `eliteCards: List<SessionCard>` (line 76) - Elite mode cards
- `stateMachine: CardSessionStateMachine` (line 68) - Retry/hint state machine
- `timerJob: Job?` (line 77) - Active timer coroutine
- `subLessonSize: Int` (line 82) - Current block size
- `eliteSizeMultiplier: Double` (line 84) - Elite block size multiplier

**Key Methods:**

**Session Lifecycle:**
- `startSession(): List<SessionEvent>` (line 174) - Start/resume ACTIVE session
- `finishSession(): Pair<SessionFinishResult, List<SessionEvent>>` (line 212) - End session
- `pauseSession(): List<SessionEvent>` (line 811) - Pause to PAUSED state
- `resumeFromSettings(): List<SessionEvent>` (line 233) - Resume from PAUSED

**Answer Submission:**
- `submitAnswer(): Pair<SubmitResult, List<SessionEvent>>` (line 296) - Main validation and state update
  - Routes to specialized handlers:
    - `submitLinearSessionAnswer()` (line 537) - VERB_DRILL/DAILY sessions
    - `submitBossLastCard()` (line 408) - Boss battle completion
    - `submitBossMidCard()` (line 429) - Boss battle mid-session
    - `submitEliteFinish()` (line 447) - Elite step completion
    - `submitNormalLastCard()` (line 479) - Normal sub-lesson completion
    - `submitNormalMidCard()` (line 518) - Normal mid-sub-lesson

**Card Navigation:**
- `nextCard(triggerVoice: Boolean): List<SessionEvent>` (line 614) - Advance card
- `prevCard(): List<SessionEvent>` (line 702) - Go back
- `navigateNext(): List<SessionEvent>` (line 720) - Browse forward (pause-first)
- `navigatePrev(): List<SessionEvent>` (line 747) - Browse backward (pause-first)
- `selectSubLesson(index: Int): List<SessionEvent>` (line 764) - Jump to sub-lesson

**Card Session Management:**
- `startCardSession(cards: List<SessionCard>, mode: TrainingScreenMode): List<SessionEvent>` (line 931) - Unified start
- `exitCardSession(): List<SessionEvent>` (line 990) - Unified exit
- `replaceCards(cards: List<SessionCard>): List<SessionEvent>` (line 1106) - Replace cards in session

**Input Handling:**
- `onInputChanged(text: String)` (line 249) - Update input text, clear hint if typing
- `setInputMode(mode: InputMode)` (line 262) - Switch VOICE/KEYBOARD/WORD_BANK
- `selectWordFromBank(word: String)` (line 831) - Word bank selection
- `removeLastSelectedWord()` (line 841) - Undo word bank selection

**Hint:**
- `showAnswer(): List<SessionEvent>` (line 819) - Force-show hint

**Word Bank:**
- `updateWordBank()` (line 1249) - Regenerate word bank options
  - Uses `WordBankGenerator.generateForVerb()` for verb sessions
  - Uses `WordBankGenerator.generateForSentence()` for normal sessions

**Timer:**
- `resumeTimer()` (line 1196) - Start active time tracking
- `pauseTimer()` (line 1227) - Stop active time tracking, flush to state

**Elite Mode:**
- `openEliteStep(index: Int): List<SessionEvent>` (line 873) - Start elite step
- `cancelEliteSession(): List<SessionEvent>` (line 887) - Cancel elite mode
- `resolveEliteUnlocked(lessons, testMode): Boolean` (line 897) - Check unlock condition
- `normalizeEliteSpeeds(speeds): List<Double>` (line 901) - Pad/truncate to elite step count
- `calculateSpeedPerMinute(activeMs, words): Double` (line 913) - Calculate words/minute

**Card List Management:**
- `setSessionCards(cards: List<SentenceCard>)` (line 1160) - Load cards from provider
- `setBossCards(cards: List<SentenceCard>)` (line 1168) - Load boss cards
- `setEliteCards(cards: List<SentenceCard>)` (line 1173) - Load elite cards
- `clearAllCards()` (line 1180) - Clear all card lists
- `getSessionCards(): List<SessionCard>` (line 1178) - Get current cards

**Configuration:**
- `setEliteSizeMultiplier(multiplier: Double)` (line 1186)
- `setSubLessonSize(size: Int)` (line 1190)

**CardSessionStateModel Implementation:**
- `val isActive: Boolean` (line 113) - Session is ACTIVE and no hint shown
- `val isPaused: Boolean` (line 117) - Session is PAUSED or has hint
- `val isHintShown: Boolean` (line 121) - Hint answer is non-null
- `val canSubmit: Boolean` (line 124) - From uiState
- `val hasCurrentCard: Boolean` (line 127) - currentCard() != null
- `val isComplete: Boolean` (line 130) - No cards or all answered correctly
- `val progress: SessionProgress` (line 137) - Current/total for UI

**Dependencies:**
- `TrainingStateAccess` (line 51) - State read/write abstraction
- `AnswerValidator` (line 54) - Answer validation logic
- `WordBankGenerator` (line 55) - Word bank generation
- `CardProvider` (line 56) - Card selection logic
- `StreakManager` (line 57) - Streak tracking
- Query callbacks (line 58-60):
  - `getMastery: (String, String) -> LessonMasteryState?`
  - `getSchedule: (String) -> LessonSchedule?`
  - `calculateCompletedSubLessons: (...) -> Int`
- `onTimerSaveProgress: () -> Unit` (line 61) - Progress save callback
- `sessionTimerMsSink: ((Long) -> Unit)?` (line 62) - High-frequency timer update

### CardProvider
**File:** `app/src/main/java/com/alexpo/grammermate/feature/training/CardProvider.kt`  
**Lines:** 28-251 (~220 lines)

**Responsibilities:**
- Pure Kotlin card selection module
- Handles sub-lesson scheduling via `MixedReviewScheduler`
- Card selection for all training modes
- Boss battle card building
- No Android dependencies (suitable for unit testing)

**State:**
- `cachedScheduleKey: String` (line 37) - Cache invalidation key
- `subLessonSize: Int` (line 29) - Current block size
- `eliteSizeMultiplier: Double` (line 32) - Elite block size multiplier

**Key Methods:**

**Schedule Building:**
- `setSubLessonSize(size: Int)` (line 45) - Update size, invalidate cache
- `buildSchedules(lessons, existingSchedules): Map<LessonId, LessonSchedule>` (line 59)
  - Caches by composite key: `lessonKey|blockSize`
  - Delegates to `MixedReviewScheduler(blockSize).build(lessons)`

**Session Cards:**
- `buildSessionCards(...): CardSetResult` (line 91) - Main entry point
  - Routes by `TrainingMode`:
    - `LESSON` → `buildLessonSessionCards()` (line 202)
    - `ALL_SEQUENTIAL` → flatten, paginate (line 108-109)
    - `ALL_MIXED` → flatten, shuffle, limit, paginate (line 110-116)
  - Returns `CardSetResult` with cards + metadata

**Review Cards:**
- `buildReviewCards(lessons, selectedLessonId, hiddenCardIds): List<SentenceCard>` (line 148)
  - Returns all lesson cards (shuffled, filtered by hidden)

**Boss Cards:**
- `buildBossCards(lessons, type, selectedLessonId, selectedIndex): List<SentenceCard>` (line 172)
  - `LESSON`: Lesson cards shuffled, limited to `MAX_BOSS_CARDS`
  - `MEGA`: All cards up to selected lesson, shuffled, limited
  - `ELITE`: All lesson cards shuffled, limited to `eliteSubLessonSize() * eliteStepCount`

**Internal:**
- `buildLessonSessionCards(...)` (line 202) - LESSON mode implementation
  - Gets schedule from map
  - Calculates `completedSubLessonCount` via `ProgressTracker`
  - Returns sub-lesson at `activeSubLessonIndex`
- `eliteSubLessonSize(): Int` (line 235) - Calculate elite block size

**Dependencies:**
- `ProgressTracker?` (line 34) - Optional, for completed sub-lesson calculation
- `MixedReviewScheduler` (line 68) - Schedule construction

### CardSessionStateMachine
**File:** `app/src/main/java/com/alexpo/grammermate/feature/training/CardSessionStateMachine.kt`  
**Lines:** 24-207 (~180 lines)

**Responsibilities:**
- Reusable state holder for retry/hint logic
- Used by `SessionRunner`, `VerbDrill`, `DailyPractice`
- Encapsulates common pattern:
  - Correct → reset, return result
  - Wrong → increment attempts, show feedback or auto-show hint
  - 3 wrong → auto-show answer
  - Manual "Show Answer" → force show hint
  - User types after hint → clear hint, reset attempts
- Uses Kotlin StateFlow for JUnit testability

**State (all StateFlow<T>):**
- `incorrectAttempts: Int` (line 31) - Consecutive wrong attempts
- `hintAnswer: String?` (line 38) - Non-null when showing hint
- `showIncorrectFeedback: Boolean` (line 45) - Show "Incorrect" inline
- `remainingAttempts: Int` (line 52) - Attempts before auto-show
- `isPaused: Boolean` (line 59) - Session paused (answer shown, waiting)
- `voiceTriggerToken: Int` (line 66) - Token to trigger voice recognition

**Key Methods:**

**Answer Submission:**
- `onSubmit(isCorrect, card, inputMode, onCorrect, onWrong): OnSubmitResult` (line 99)
  - Returns sealed result:
    - `Correct(result)` - Answer was correct
    - `Wrong(attemptNumber, remaining)` - Wrong but attempts remain
    - `HintShown(answer)` - Max attempts reached, hint auto-shown
  - Calls `onCorrect()` or `onWrong()` callbacks for side effects
  - Auto-triggers voice in VOICE mode for retry

**Input Handling:**
- `onInputChanged(text: String)` (line 143) - Clear hint if user types after seeing answer

**Hint:**
- `showAnswer(card: SessionCard): String` (line 156) - Force-show hint manually
- `clearIncorrectFeedback()` (line 169) - Clear inline "Incorrect" feedback

**State Management:**
- `reset()` (line 177) - Reset all state to initial values
- `triggerVoice()` (line 188) - Increment voice trigger token
- `pause()` (line 196) - Set pause state without showing hint
- `resume()` (line 204) - Resume from manual pause

**Dependencies:**
- `maxAttempts: Int` (line 25) - Configurable threshold (default 3)
- `answerProvider: (SessionCard) -> String` (line 26) - Extract hint answer from card

---

## Business Rules Found

### Card Selection Rules

**LESSON Mode:**
1. Use pre-built `LessonSchedule` from `MixedReviewScheduler` (line 210)
2. Get sub-lessons at `activeSubLessonIndex` (line 221)
3. Filter out hidden card IDs (line 223)
4. Return cards with metadata (total count, completed count, types)

**ALL_SEQUENTIAL Mode:**
1. Flatten all lesson cards (line 109)
2. Filter hidden cards (line 109)
3. Paginate into blocks of `subLessonSize` (line 120-125)
4. No shuffling (preserve order)

**ALL_MIXED Mode:**
1. Flatten all lesson cards including reserve pool (line 112)
2. Filter hidden cards (line 113)
3. Shuffle (line 114)
4. Limit to `TrainingConfig.REVIEW_LIMIT` (line 115)
5. Paginate into blocks (line 120-125)

**Review Mode:**
1. Get selected lesson's cards (line 153-155)
2. Filter hidden cards (line 157)
3. Shuffle (line 158)

**Boss Battles:**
- `LESSON`: Lesson cards shuffled, limited to `MAX_BOSS_CARDS` (line 181-184)
- `MEGA`: All cards up to selected lesson, shuffled, limited (line 187-191)
- `ELITE`: All lesson cards shuffled, limited to `eliteSubLessonSize() * eliteStepCount` (line 194-195)

### CurrentIndex Management

**Initialization:**
- `currentIndex` is set to 0 on session start (line 944, 1046)
- Or coerced to safe range when building cards (line 1459)

**Navigation:**
- `nextCard()` increments `currentIndex` (line 680, 687)
- `prevCard()` decrements `currentIndex` (line 703, 706)
- `navigateNext()` increments but leaves PAUSED (line 730)
- `navigatePrev()` decrements but leaves PAUSED (line 756)

**Completion:**
- Last card detection: `currentIndex >= sessionCards.lastIndex` (line 328, 624)
- On sub-lesson completion: reset to 0 (line 497, 658)
- On elite step completion: reset to 0 (line 466)

**Bounds Checking:**
- Always coerced to `0..sessionCards.lastIndex` (line 243, 680, 703)

### Session State Transitions

**ACTIVE → PAUSED:**
- Timer pause (line 811, 1227)
- Sub-lesson completion (line 497, 658)
- Elite step completion (line 466)
- Manual pause via toggle (line 781)
- Navigate arrows (line 723, 750)

**PAUSED → ACTIVE:**
- `startSession()` (line 197)
- `resumeFromSettings()` (line 234)
- Manual resume via toggle (line 803)
- Correct answer while PAUSED (line 314-325)

**ACTIVE → HINT_SHOWN:**
- 3 incorrect attempts (line 366, 373)
- Manual "Show Answer" (line 824)

**HINT_SHOWN → ACTIVE:**
- User types after hint (line 143-149)
- Manual toggle (Play button clears hint) (line 789-801)

### Answer Validation

**Normalization:**
- Input normalized via `Normalizer.normalize()` (AnswerValidator line 67)
- Accepted answers split by `+` for alternatives (line 71)
- Each alternative normalized and compared (line 72)

**Test Mode:**
- When `testMode = true`, always accept (AnswerValidator line 69)

**Voice Metrics:**
- Counted only when `inputMode == VOICE` (SessionRunner line 302-305)
- `voiceActiveMs`: accumulated active time (line 415, 437, 455, 487, 526, 546, 553, 569)
- `voiceWordCount`: words with length >= 3 (line 1276-1280)

### Mastery Tracking

**Counted Shows:**
- Only VOICE and KEYBOARD answers count (TrainingViewModel line 1210)
- WORD_BANK never counts for flower growth (line 1212 comment)

**Recording:**
- `recordCardShowForMastery()` called on correct answer (SessionRunner line 327)
- `recordCardEncounter()` increments encounter count (TrainingViewModel line 1228)

**Sub-lesson Completion:**
- All cards in sub-lesson marked shown via `ProgressTracker.markSubLessonCardsShown()` (SessionRunner line 506, 671)
- Lesson completion checked via `ProgressTracker.checkAndMarkLessonCompleted()` (line 508, 673)

### Streak Rules

**Session Completion:**
- Session counts when `correctCount >= (sessionSize - badSentenceCount)` (TrainingViewModel line 1395-1400)
- All non-bad cards must be answered correctly

**Fire Tracking:**
- One fire per unique `PracticeType` per day (Models.kt line 283)
- Types: TRANSLATION, VOCAB, VERB (line 219-222)

### Daily Practice Cursor

**Pack Isolation:**
- Each pack has its own `PackDailyCursorState` (Models.kt line 383-397)
- Cursor includes: `sentenceOffset`, `verbOffset`, `currentLessonIndex`
- `firstSessionSentenceCardIds` and `firstSessionVerbCardIds` for Repeat (line 389-390)

**Cursor Advancement:**
- Only on session completion (TrainingViewModel line 997-1004)
- Advances BOTH `sentenceOffset` AND `verbOffset` (line 999)

**Repeat:**
- Reconstructs exact cards from `firstSessionCardIds` (line 982-988)

---

## Infrastructure Dependencies

### Stores (Data Persistence)

**Lesson Data:**
- `LessonStore` (line 82) - Lesson and card data
- `ProgressStore` (line 83) - Training progress state
- `ConfigStore` (line 84) - App configuration
- `MasteryStore` (line 85) - Flower/mastery data
- `StreakStore` (line 86) - Daily streak data
- `HiddenCardStore` (line 88) - User-hidden cards
- `VocabProgressStore` (line 89) - Vocab drill progress
- `WordMasteryStore` (line 90) - Word mastery state (pack-scoped)
- `PackLessonProgressStore` (line 91) - Lesson progress per pack
- `PackDailyCursorStore` (line 92) - Daily cursor per pack

**File I/O Pattern:**
- All stores use `AtomicFileWriter` pattern (temp → fsync → rename)
- Example: `MasteryStore.flush()` (TrainingViewModel line 1537, 1187)

### Timer System

**Implementation:**
- Coroutine-based with 500ms tick interval (SessionRunner line 1203)
- Accumulates `activeTimeMs` internally (line 1209)
- Flushes to main state every ~10s (20 ticks) (line 1216-1221)
- High-frequency updates via separate `sessionTimerMsSink` flow (line 1212)

**State:**
- `timerJob: Job?` (line 77) - Active coroutine
- `activeStartMs: Long?` (line 78) - Last resume timestamp
- `lastSaveActiveTimeMs: Long` (line 80) - Accumulated time

### Audio Coordination

**AudioCoordinator** (TrainingViewModel line 230-235):
- Manages TTS/ASR model state
- Plays success/error sounds
- Handles voice input mode
- No business logic, pure coordination

### Feature Coordination

**BossOrchestrator** (line 202-209):
- Manages boss battle state
- Coordinates with `SessionRunner` and `CardProvider`
- Handles reward thresholds

**DailyPracticeCoordinator** (line 184-195):
- Manages daily practice session
- Builds 3-block session (TRANSLATE, VOCAB, VERBS)
- Coordinates cursor advancement

**ProgressTracker** (line 139-146):
- Calculates completed sub-lessons
- Marks cards shown for mastery
- Checks lesson completion
- Resolves progress lesson info

**FlowerRefresher** (line 179-182):
- Refreshes flower display states from mastery data

**StreakManager** (line 136):
- Records practice type completion
- Calculates streak celebration messages

### State Flow Architecture

**Combined State:**
`TrainingViewModel.uiState` is a combine flow (line 259-280):
1. `_coreState` (main TrainingUiState)
2. `audioCoordinator.audioState`
3. `storyRunner.stateFlow`
4. `vocabSprintRunner.vocabState`
5. `dailyPracticeCoordinator.dailyState`
6. `flowerRefresher.stateFlow`
7. `bossOrchestrator.stateFlow`

**State Access Abstraction:**
`TrainingStateAccess` interface (line 105-111):
- `val uiState: StateFlow<TrainingUiState>`
- `fun updateState(transform: (TrainingUiState) -> TrainingUiState)`
- `fun saveProgress()`

- Implemented by `TrainingViewModel` (line 105-111)
- Used by all feature modules (`SessionRunner`, `DailyPracticeCoordinator`, etc.)
- Test fake: `FakeTrainingStateAccess` (test-harness)

---

## State Mutations

### CurrentIndex

**Mutations:**
- `nextCardInternal()` increments (SessionRunner line 680)
- `prevCard()` decrements (line 703)
- `navigateNext()` increments (line 730)
- `navigatePrev()` decrements (line 756)
- `selectSubLesson()` resets to 0 (line 768)
- Sub-lesson completion resets to 0 (line 497, 658)
- Elite step completion resets to 0 (line 466)
- Session start resets to 0 or coerces to safe range (line 183, 1459)

**Guards:**
- Always coerced to valid range: `0..sessionCards.lastIndex` (line 243, 680, 703)

### Session State (SessionState enum)

**ACTIVE:**
- Set by `startSession()` (line 197)
- Set by `resumeFromSettings()` (line 234)
- Set by correct answer while PAUSED (line 318)
- Set by resume after hint (line 795)

**PAUSED:**
- Set by `pauseSession()` (line 813)
- Set by manual toggle (line 781)
- Set by sub-lesson completion (line 497, 658)
- Set by elite step completion (line 466)
- Set by navigate arrows (line 732, 758)
- Set by session finish (line 227)

**HINT_SHOWN:**
- Set by 3 incorrect attempts (line 372)
- Set by manual `showAnswer()` (line 824)
- Cleared by typing after hint (via stateMachine, line 143-149)
- Cleared by manual toggle (line 789)

### Incorrect Attempts

**Increment:**
- State machine increments on wrong answer (line 118)
- Reset to 0 on correct answer (line 112)

**Display:**
- `incorrectAttemptsForCard` in uiState updated from stateMachine (line 252, 384, 388)

### Card Counts

**Correct Count:**
- Incremented on correct answer (line 415, 437, 466, 487, 526, 548, 564)
- Used for session completion check (TrainingViewModel line 1397)

**Incorrect Count:**
- Incremented on wrong answer (line 367, 380, 394)
- Not used for completion (only correctCount matters)

### Active Time

**Accumulation:**
- Timer adds 500ms elapsed every tick (line 1209)
- Flushes to `activeTimeMs` every 10s (line 1219)
- Flushes on pause (line 1230)

**Reset:**
- Session start resets to 0 (line 946, 1053)
- Review session resets to 0 (line 1066)

### Voice Metrics

**Voice Active Time:**
- Accumulated only when `inputMode == VOICE` (line 304, 415, 437, 455, 487, 526, 546, 553, 569)
- Reset on session start (line 952, 1066)

**Voice Word Count:**
- Counted only when `inputMode == VOICE` (line 304, 415, 437, 455, 487, 526, 546, 553, 569)
- Words with length >= 3 (line 1276-1280)
- Reset on session start (line 952, 1066)

### Sub-lesson State

**Active Sub-lesson Index:**
- Calculated from completed count (TrainingViewModel line 609-619)
- Preserved on completion (SessionRunner line 494, 654)
- Reset to 0 on session start (line 768, 946, 1046)
- User can manually select via `selectSubLesson()` (line 764)

**Completed Sub-lesson Count:**
- Calculated by `ProgressTracker.calculateCompletedSubLessons()` (line 492, 652)
- Incremented on sub-lesson completion (line 487, 648)
- Reset to 0 on session start (line 952, 1066)

**Sub-lesson Total:**
- Set to `sessionCards.size` on session start (line 183, 944, 1046, 1069)
- Or from `CardSetResult.subLessonTotal` (TrainingViewModel line 1457)

---

## Test Coverage

### Unit Tests

**Answer Validation:**
- `AnswerValidator` - Pure Kotlin, no Android dependencies
- Can test normalization logic, `+` alternatives, test mode bypass

**Card Selection:**
- `CardProvider` - Pure Kotlin, no Android dependencies
- Can test LESSON/SEQUENTIAL/MIXED modes, boss battles
- Requires `MixedReviewScheduler` and `ProgressTracker` doubles

**State Machine:**
- `CardSessionStateMachine` - Uses StateFlow, testable without Robolectric
- Can test retry logic, hint auto-show, manual show, clear on type

**Schedule Building:**
- `MixedReviewSchedulerTest` exists (data/ line 2)
- Tests sub-lesson schedule construction

### Integration Tests

**Verb Drill Regression:**
- `VerbDrillSessionCardRegressionTest` exists (ui/ line 1)
- Tests verb drill session lifecycle
- Uses real ViewModel with fake stores

**Test Harness:**
- `FakeTrainingStateAccess` (test-harness line 1)
- In-memory state for testing
- No file I/O, no Android dependencies

### Gaps

**No Unit Tests For:**
- `SessionRunner` (complex, 1416 lines)
- `TrainingViewModel` (massive, 2579 lines)
- `ProgressTracker` integration
- `StreakManager` logic
- Boss battle orchestration

**Why:**
- SessionRunner has complex state + timer + coroutines
- TrainingViewModel is too coupled (has everything)
- Would require significant test doubles setup

---

## Risks / Unclear Points

### Architectural Concerns

**1. TrainingViewModel Size (2579 lines)**
- **Risk:** God object, hard to understand, change, test
- **Evidence:** Lines 105-2579
- **Impact:** Every feature adds code to ViewModel
- **Example:** Pomodoro alone added ~100 lines (line 313-389)

**2. Circular Dependencies**
- **Risk:** Features depend on each other through ViewModel
- **Evidence:**
  - `BossOrchestrator` uses `SessionRunner` and `CardProvider` (line 202-209)
  - `SessionRunner` needs `ProgressTracker` callback (line 167-173)
  - `DailyPracticeCoordinator` needs `resolveProgressLessonInfo` callback (line 970)
- **Impact:** Hard to extract features independently

**3. Event-Driven Complexity**
- **Risk:** Event lists create implicit control flow
- **Evidence:** `SessionEvent` sealed hierarchy (SessionEvent.kt line 18-39)
- **Example:** `submitNormalLastCard` returns 6 events (line 505-512)
- **Impact:** Hard to trace what happens after an action

**4. State Mutation Scatter**
- **Risk:** Same state mutated in multiple places
- **Evidence:**
  - `currentIndex` mutated in 4+ methods
  - `activeSubLessonIndex` calculated in 2 places (line 494, 654)
  - `completedSubLessonCount` calculated then recalculated (line 487, 492)
- **Impact:** Easy to introduce inconsistencies

**5. Query Callback Pattern**
- **Risk:** Callbacks-in-events make flow hard to follow
- **Evidence:**
  - `CalculateCompletedSubLessons` with callback (line 29-33)
  - `GetMastery` with callback (line 35)
  - `GetSchedule` with callback (line 36)
- **Impact:** Reader must jump between files to understand logic

**6. Timer Side Effects**
- **Risk:** Timer coroutine mutates state independently
- **Evidence:**
  - Timer tick updates `activeTimeMs` every 500ms (line 1209)
  - Separate `sessionTimerMsSink` for high-frequency updates (line 1212)
  - Flush to main state every 10s (line 1216-1221)
- **Impact:** State changes without explicit action, racy tests

**7. Word Bank Timing**
- **Risk:** Word bank not always populated when needed
- **Evidence:**
  - `updateWordBank()` called in 5 places (line 203, 284, 692, 735, 979)
  - Comment: "Without this, sessions start with empty wordBank" (line 200-202)
- **Impact:** Toggle button missing, user confusion

**8. Daily Cursor Contamination**
- **Risk:** Cursor state shared across packs before fix
- **Evidence:** Comment about "cross-pack contamination" (Models.kt line 381)
- **Fixed by:** `PackDailyCursorState` per pack (line 383-397)
- **Impact:** Repeat showing wrong cards after pack switch

**9. Mastery Store Scoping**
- **Risk:** Global mastery file vs pack-scoped files
- **Evidence:**
  - `rebindWordMasteryStore()` called on pack change (line 1244)
  - Comment: "must be called whenever activePackId changes" (line 1242)
- **Impact:** Writing to wrong file, progress lost

**10. Session Classification Complexity**
- **Risk:** `isLinearSession` check scattered in logic
- **Evidence:**
  - `isLinearSession` property (line 92-97)
  - Used in 5+ places to branch behavior
  - `usesVerbWordBank` property (line 104-108)
- **Impact:** Easy to miss edge cases when adding new session types

### Unclear Business Logic

**1. "Shown" vs "Practiced"**
- **Unclear:** When is a card "shown" vs "practiced"?
- **Evidence:**
  - `recordCardShowForMastery()` only for VOICE/KEYBOARD (line 1210)
  - `markSubLessonCardsShown()` marks all cards (line 1332)
  - Daily practice has separate `recordDailyCardPracticed()` (line 1029)
- **Impact:** Flower growth might be incorrect

**2. Sub-lesson Completion Criteria**
- **Unclear:** What makes a sub-lesson "completed"?
- **Evidence:**
  - `calculateCompletedSubLessons()` uses mastery + interval steps (line 167-173)
  - But also checks `shownCardIds` set
  - Comment: "mastery step ≥ 3 = 'learned'" (CLAUDE.md line 19)
- **Impact:** User might see "completed" sub-lessons that aren't actually learned

**3. Repeat vs Continue**
- **Unclear:** What's the difference?
- **Evidence:**
  - `repeatDailyPractice()` uses `firstSessionCardIds` (line 1006)
  - `onDailyBlockComplete()` advances cursor
  - But Repeat semantics not documented
- **Impact:** User confusion about what Repeat does

**4. Elite Unlock Logic**
- **Unclear:** Why `lessons.size >= 12`?
- **Evidence:** `resolveEliteUnlocked()` (line 897)
- **Impact:** Magic number, not configurable

**5. Bad Sentence Handling**
- **Unclear:** How do bad sentences affect completion?
- **Evidence:**
  - `sessionSize - badSentenceCount` in completion check (line 1398)
  - `badSentenceCount` loaded from store (line 438)
  - But bad sentences can be hidden mid-session
- **Impact:** Completion might be impossible if many bad sentences

### Missing Documentation

**1. Card Ordering**
- **Missing:** Why ALL_MIXED shuffles but ALL_SEQUENTIAL doesn't?
- **Impact:** User experience difference not documented

**2. Session Size Limits**
- **Missing:** Why `REVIEW_LIMIT` = 50?
- **Evidence:** `TrainingConfig.REVIEW_LIMIT` (CardProvider line 111)
- **Impact:** Arbitrary limit might not suit all users

**3. Hint Threshold**
- **Missing:** Why 3 attempts before hint?
- **Evidence:** `HINT_THRESHOLD = 3` (AnswerValidator line 44)
- **Impact:** UX tuning parameter not documented

**4. Elite Multiplier**
- **Missing:** Why 1.25x for elite blocks?
- **Evidence:** `eliteSizeMultiplier = 1.25` (TrainingViewModel line 134)
- **Impact:** Arbitrary difficulty multiplier

**5. Word Bank Distractors**
- **Missing:** How are distractors selected?
- **Evidence:** `WordBankGenerator.generateForVerb/Sentence()` (line 1262-1268)
- **Impact:** Not documented how many distractors, selection strategy

---

## Evidence Summary

### Key Files with Line Evidence

**TrainingViewModel (1646 lines)**
- State definition: line 95-111
- Feature construction: line 115-256
- Init block: line 391-525
- Navigation: line 550-672
- Answer submission: line 674-716
- Session operations: line 718-762
- Card operations: line 770-830
- Progress: line 1414-1476
- Event handling: line 1299-1330

**SessionRunner (1289 lines)**
- State machine: line 68-70
- Private state: line 74-84
- Session classification: line 92-108
- State model implementation: line 113-143
- Session lifecycle: line 174-236
- Answer submission: line 296-578
- Card navigation: line 614-762
- Pause/resume: line 774-815
- Card session management: line 931-1147
- Timer: line 1196-1238

**CardProvider (251 lines)**
- Configuration: line 29-35
- Cache: line 37
- Schedule building: line 45-69
- Session cards: line 91-135
- Review cards: line 148-159
- Boss cards: line 172-198
- Internal helpers: line 202-237

**CardSessionStateMachine (207 lines)**
- State flows: line 29-69
- Result type: line 77-86
- Answer submission: line 99-137
- Input handling: line 143-150
- Hint: line 156-164
- State management: line 177-206

**Data Models**
- SessionState enum: line 105-109
- InputMode enum: line 111-115
- TrainingMode enum: line 87-91
- TrainingScreenMode enum: line 140-148
- DailyCursorState: line 367-375
- PackDailyCursorState: line 383-397

**Session Events**
- Event types: line 18-39
- Composite event: line 38

### Method Signatures for Key Operations

**Card Selection:**
```kotlin
// CardProvider.kt line 91
fun buildSessionCards(
    lessons: List<Lesson>,
    mode: TrainingMode,
    selectedLessonId: LessonId?,
    schedules: Map<LessonId, LessonSchedule>,
    activeSubLessonIndex: Int,
    hiddenCardIds: Set<String>,
    mastery: LessonMasteryState? = null
): CardSetResult
```

**Answer Submission:**
```kotlin
// SessionRunner.kt line 296
fun submitAnswer(): Pair<SubmitResult, List<SessionEvent>>
```

**Session Start:**
```kotlin
// SessionRunner.kt line 174
fun startSession(): List<SessionEvent>

// SessionRunner.kt line 931
fun startCardSession(
    cards: List<SessionCard>,
    mode: TrainingScreenMode
): List<SessionEvent>
```

**Card Navigation:**
```kotlin
// SessionRunner.kt line 614
fun nextCard(triggerVoice: Boolean = false): List<SessionEvent>

// SessionRunner.kt line 702
fun prevCard(): List<SessionEvent>

// SessionRunner.kt line 720
fun navigateNext(): List<SessionEvent>

// SessionRunner.kt line 747
fun navigatePrev(): List<SessionEvent>
```

**State Machine:**
```kotlin
// CardSessionStateMachine.kt line 99
fun onSubmit(
    isCorrect: Boolean,
    card: SessionCard,
    inputMode: InputMode = InputMode.KEYBOARD,
    onCorrect: () -> Unit = {},
    onWrong: () -> Unit = {}
): OnSubmitResult
```

---

## Conclusion

The training core is **functional but heavily coupled**. The main issues are:

1. **TrainingViewModel is a god object** (2579 lines) - does everything
2. **Circular dependencies** - features depend on each other through ViewModel
3. **Event-driven complexity** - hard to trace control flow
4. **State mutation scatter** - same state mutated in multiple places
5. **Query callback pattern** - callbacks-in-events make flow hard to follow

**Positive aspects:**
- `CardProvider` is well-isolated (pure Kotlin, testable)
- `CardSessionStateMachine` is reusable and testable
- `AnswerValidator` is clean and testable
- `TrainingStateAccess` abstraction enables testing
- `FakeTrainingStateAccess` exists for unit tests

**Refactoring priorities:**
1. Extract features from TrainingViewModel (Pomodoro, Audio, Settings)
2. Simplify event flow (replace events with direct method calls where possible)
3. Consolidate state mutations (single source of truth for each piece of state)
4. Break circular dependencies (use inversion of control)
5. Add unit tests for SessionRunner and feature coordinators

**Evidence quality:** All claims backed by file:line references from actual code.
