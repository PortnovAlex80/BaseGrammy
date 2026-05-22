# Wave 1 Agent A6: Special Game Modes Architecture Map

**Project:** BaseGrammy (GrammarMate) Android Language Learning App
**Date:** 2026-05-22
**Scope:** Boss battles, Elite challenges, Story mode, Pomodoro timer
**Status:** Current architecture audit (NO CHANGES MADE)

---

## Executive Summary

BaseGrammy implements **four special game modes** that layer on top of the core training system:

1. **Boss Battles** (LESSON, MEGA, ELITE) - Review sessions with reward tiers
2. **Elite Mode** - Daily step-based practice with speed tracking
3. **Story Mode** (CHECK_IN, CHECK_OUT) - Comprehension quizzes per lesson
4. **Pomodoro Timer** - Focused timed training sessions

All modes are coordinated through **TrainingViewModel** with dedicated orchestrator/runner classes. Each mode owns its state and returns command objects for cross-module coordination.

---

## Active User Flows

### Boss Battle Flow

```
1. User triggers boss: startBossLesson() | startBossMega() | startBossElite()
   ↓
2. BossOrchestrator.startBoss() validates unlock conditions:
   - LESSON/MEGA: requires min(15, total) completed sub-lessons
   - ELITE: no unlock requirement
   - Lesson must be selected for LESSON/MEGA types
   - Card pool must not be empty
   ↓
3. CardProvider.buildBossCards() builds card pool:
   - LESSON: cards from selected lesson only (max 300)
   - MEGA: cards from all lessons up to selected (max 300)
   - ELITE: cards from all lessons (eliteSize * 7 steps)
   ↓
4. BossBattleRunner.startBoss() returns BossStartResult
   ↓
5. BossOrchestrator starts session:
   - Saves pre-boss hint level
   - Forces HARD hint level for boss session
   - Pauses timer
   - Resets card session (currentIndex=0, counts=0)
   ↓
6. User answers cards, SessionRunner.advanceBossProgressOnNextCard():
   - Updates boss progress (0 to bossTotal)
   - Checks reward thresholds: 30% → Bronze, 60% → Silver, 90% → Gold
   - Pauses session when reward threshold crossed
   - Shows reward message
   ↓
7. User clears reward → resume training
   ↓
8. Boss completes or user exits:
   - BossOrchestrator.finishBoss() calculates final reward
   - Updates lesson/mega reward maps (keeps best reward)
   - Restores pre-boss hint level
   - Restores lesson progress from ProgressStore
   - Triggers: SaveProgress, BuildSessionCards, RefreshFlowerStates
```

**Evidence:**
- `BossOrchestrator.startBoss():56-122` - Full boss start flow
- `BossBattleRunner.startBoss():84-131` - Unlock validation logic
- `CardProvider.buildBossCards():172-198` - Card pool building
- `BossOrchestrator.advanceBossProgressOnNextCard():275-302` - Progress tracking

### Elite Mode Flow

```
1. User opens elite step: openEliteStep(index)
   ↓
2. SessionRunner.openEliteStep() validates:
   - Elite unlocked (requires >0 lessons in pack)
   - Index in range [0, ELITE_STEP_COUNT=7)
   ↓
3. CardProvider builds elite cards:
   - Size = ceil(subLessonSize * 1.25) * 7 steps
   - Shuffled cards from all lessons
   ↓
4. SessionRunner starts elite session:
   - eliteActive = true
   - eliteStepIndex = user-selected step
   - Resets card session (currentIndex=0, counts=0)
   - subLessonTotal = elite card count
   - subLessonCount = 7 (fixed)
   ↓
5. User completes cards in the step
   ↓
6. On last card of step:
   - SessionRunner.submitEliteFinish() calculates speed
   - Normalizes eliteBestSpeeds (keeps last 7 speeds)
   - Advances to next step (stepIndex + 1) % 7
   - Pauses timer
   ↓
7. User can cancel: cancelEliteSession()
   - Resets eliteActive = false
   - Clears card session
```

**Evidence:**
- `SessionRunner.openEliteStep():873-886` - Elite session start
- `SessionRunner.submitEliteFinish():447-466` - Elite step completion
- `CardProvider.buildBossCards():193-196` - Elite card building (via BossType.ELITE)
- `SessionRunner.resolveEliteUnlocked():897-900` - Unlock validation

### Story Mode Flow

```
1. User opens story: openStory(phase) [CHECK_IN or CHECK_OUT]
   ↓
2. StoryRunner.openStory() loads quiz:
   - Reads current lesson and language from state
   - Queries LessonStore.getStoryQuizzes(lessonId, phase, languageId)
   - Activates first matching quiz
   - Sets error if no story found
   ↓
3. User answers story questions (multiple choice)
   ↓
4. User submits story: completeStory(phase, allCorrect)
   ↓
5. StoryRunner.completeStory():
   - If allCorrect OR testMode: mark phase done
   - If not allCorrect: clear activeStory (user can retry)
   - Persists progress on success
   ↓
6. Completion state tracked in StoryState:
   - storyCheckInDone: boolean
   - storyCheckOutDone: boolean
```

**Evidence:**
- `StoryRunner.openStory():39-50` - Story loading
- `StoryRunner.completeStory():62-79` - Story completion logic
- `StoryQuizParser.parse():7-52` - Story quiz JSON parsing
- `Models.kt:66-85` - StoryPhase enum, StoryQuiz data class

### Pomodoro Flow

```
1. User starts Pomodoro: startPomodoro(durationMinutes)
   ↓
2. PomodoroHelper.startPomodoro():
   - Captures baseline correct/incorrect counts
   - Initializes PomodoroState with duration
   - Starts countdown timer (1s ticks)
   ↓
3. Timer tick (every second):
   - Decrements trueRemainingSeconds
   - Updates main state every 10s (atomicity)
   - Pushes high-frequency updates to separate flow
   ↓
4. Timer reaches 0 OR user completes all cards:
   - PomodoroHelper.completePomodoro():
     - Calculates session stats (cards shown, correct, incorrect)
     - Calculates words per minute
     - Records difficulty ratings
     - Shows completion summary
   ↓
5. User can pause/resume/cancel:
   - pausePomodoro(): stops timer, pauses training
   - resumePomodoro(): restarts timer
   - cancelPomodoro(): clears state
```

**Evidence:**
- `PomodoroHelper.startPomodoro():27-44` - Pomodoro initialization
- `PomodoroHelper.tick():160-178` - Timer tick logic
- `PomodoroHelper.completePomodoro():69-104` - Completion stats
- `Models.kt:263-274` - PomodoroState data class

---

## Main Classes and Responsibilities

### Boss Mode Architecture

**BossOrchestrator** (`feature/boss/BossOrchestrator.kt`)
- **Purpose:** Coordinates boss battle lifecycle across modules
- **Owned State:** `BossState` (bossActive, bossType, progress, rewards, maps)
- **Key Methods:**
  - `startBoss(BossType):56-122` - Validates and starts boss session
  - `finishBoss():124-185` - Completes boss, updates reward maps
  - `updateBossProgress(Int):228-254` - Advances progress, checks rewards
  - `advanceBossProgressOnNextCard():275-302` - Pure helper for progress tracking
  - `clearBossRewardMessage():187-222` - Resumes session after reward
  - `parseBossRewards():307-312` - Deserializes reward maps from ProgressStore
- **State Access:** Uses `TrainingStateAccess` for reading/writing core state
- **Returns:** `List<BossCommand>` for cross-module coordination
- **Dependencies:** BossBattleRunner, CardProvider, SessionRunner, ProgressStore, MasteryStore

**BossBattleRunner** (`feature/boss/BossBattleRunner.kt`)
- **Purpose:** Pure logic module for boss battle calculations
- **No State:** All functions are pure, returns data objects
- **Key Methods:**
  - `startBoss():84-131` - Validates eligibility, returns BossStartResult
  - `updateBossProgress():146-166` - Calculates new reward tier
  - `finishBoss():184-223` - Computes final reward and updated maps
  - `resolveBossReward(Int, Int):263-272` - Pure function: progress % → reward tier
  - `bossRewardMessage(BossReward):277-283` - Human-readable reward messages
- **Reward Thresholds:**
  - 90% → Gold
  - 60% → Silver
  - 30% → Bronze
  - < 30% → No reward
- **Evidence:** `BossBattleRunner:23-284`

**BossCommand** (`feature/boss/BossOrchestrator.kt:8-23`)
- **Purpose:** Command pattern for boss orchestration
- **Command Types:**
  - `PauseTimer` / `ResumeTimer` - Timer control
  - `SaveProgress` - Persist progress to disk
  - `BuildSessionCards` - Rebuild card session
  - `RefreshFlowerStates` - Update flower visuals
  - `ResetBoss` / `ResetDailySession` / `ResetStory` / `ResetVocabSprint` - State resets
  - `Composite` - Batch commands

### Elite Mode Architecture

**SessionRunner Elite Integration** (`feature/training/SessionRunner.kt`)
- **Purpose:** Manages elite step-based practice sessions
- **Elite State:** Part of core `EliteState` in `TrainingUiState`
- **Key Methods:**
  - `openEliteStep(Int):873-886` - Starts elite step session
  - `cancelEliteSession():887-893` - Cancels active elite session
  - `submitEliteFinish():447-466` - Handles last card of elite step
  - `resolveEliteUnlocked():897-900` - Checks if elite is available
  - `normalizeEliteSpeeds():901-906` - Keeps last 7 speeds, pads with 0.0
  - `eliteSubLessonSize():909-910` - Calculates step size
  - `buildEliteCards():1242-1246` - Builds card pool for elite
- **Elite Configuration:**
  - `ELITE_STEP_COUNT = 7` (TrainingConfig:6)
  - `ELITE_SIZE_MULTIPLIER = 1.25` (TrainingConfig:11)
- **Elite Unlock Condition:** `lessons.isNotEmpty()`
- **Evidence:** `SessionRunner:873-906`

### Story Mode Architecture

**StoryRunner** (`feature/training/StoryRunner.kt`)
- **Purpose:** Manages story quiz lifecycle
- **Owned State:** `StoryState` (checkInDone, checkOutDone, activeStory, errorMessage)
- **Key Methods:**
  - `openStory(StoryPhase):39-50` - Loads story quiz from LessonStore
  - `completeStory(StoryPhase, Boolean):62-79` - Marks phase done on success
  - `clearStoryError():84-87` - Clears error message
  - `resetState():93-95` - Resets to defaults
- **Returns:** `StoryResult` (SaveAndBackup, None)
- **Dependencies:** TrainingStateAccess, LessonStore
- **Evidence:** `StoryRunner:21-96`

**StoryQuizParser** (`data/StoryQuizParser.kt`)
- **Purpose:** Parses story quiz JSON from pack files
- **Key Methods:**
  - `parse(String):7-52` - Parses JSON into StoryQuiz object
- **Story Structure:**
  - `storyId`, `lessonId`, `phase` (CHECK_IN/CHECK_OUT)
  - `text` body
  - `questions` array with multiple choice options
- **Evidence:** `StoryQuizParser:6-53`

### Pomodoro Architecture

**PomodoroHelper** (`feature/pomodoro/PomodoroHelper.kt`)
- **Purpose:** Manages Pomodoro timer lifecycle and stats
- **No Owned State:** All state in `PomodoroState` (part of core state)
- **Key Methods:**
  - `startPomodoro(Int):27-44` - Starts timer with duration
  - `pausePomodoro():46-52` - Pauses timer and training
  - `resumePomodoro():54-59` - Resumes timer
  - `cancelPomodoro():61-67` - Cancels timer, clears state
  - `completePomodoro():69-104` - Calculates final stats
  - `recordDifficultyRating(CardDifficultyRating):116-125` - Records user rating
  - `onTrainingSessionCompleted():110-114` - Called when cards finish before timer
- **Timer Management:**
  - Independent `trueRemainingSeconds` counter (updated every second)
  - Main state updated every 10s (atomicity optimization)
  - High-frequency updates pushed to separate flow for UI
- **Stats Tracked:**
  - `cardsShown`, `cardsCorrect`, `cardsIncorrect`
  - `difficultyRatings` map (AGAIN, HARD, GOOD, EASY)
  - `wordsPerMinute` calculation
  - `durationMinutes`, `completedAtMs`
- **Evidence:** `PomodoroHelper:13-189`

---

## Business Rules Found

### Boss Battle Rules

**Unlock Conditions:**
- **LESSON Boss:** Requires `min(15, totalSubLessons)` completed sub-lessons
- **MEGA Boss:** Requires `min(15, totalSubLessons)` completed sub-lessons
- **ELITE Boss:** No unlock requirement (always available)
- **Lesson Selection:** LESSON and MEGA require a selected lesson
- **Card Availability:** Returns error if card pool is empty

**Evidence:**
- `BossBattleRunner.startBoss():93-100` - Unlock validation
- `TrainingConfig.BOSS_UNLOCK_SUB_LESSONS = 7` - Unlock threshold

**Reward Tiers:**
- **Bronze:** 30% progress (cards completed / total cards)
- **Silver:** 60% progress
- **Gold:** 90% progress
- **Best Reward Kept:** If user repeats boss, keeps highest reward achieved

**Evidence:**
- `BossBattleRunner.resolveBossReward():263-272` - Reward calculation
- `BossOrchestrator.finishBoss():194-210` - Best reward logic
- `TrainingConfig.BOSS_BRONZE_PCT = 30.0`
- `TrainingConfig.BOSS_SILVER_PCT = 60.0`
- `TrainingConfig.BOSS_GOLD_PCT = 90.0`

**Card Pool Rules:**
- **LESSON Boss:** Cards from selected lesson only (max 300)
- **MEGA Boss:** Cards from all lessons up to selected (max 300)
- **ELITE Boss:** Cards from all lessons (eliteSize * 7 steps)
- **Shuffling:** All boss card pools are shuffled

**Evidence:**
- `CardProvider.buildBossCards():172-198` - Card pool building
- `TrainingConfig.MAX_BOSS_CARDS = 300`

**Session Rules:**
- **Hint Level Override:** Boss forces HARD hint level (no hints)
- **Hint Level Restoration:** Pre-boss hint level restored on exit
- **Progress Tracking:** Boss progress (0 to bossTotal) tracked separately
- **Timer Paused:** Timer pauses when reward threshold crossed
- **State Restoration:** On boss exit, restores lesson progress from ProgressStore

**Evidence:**
- `BossOrchestrator.startBoss():77-89` - Hint level override
- `BossOrchestrator.finishBoss():136-137` - Hint level restoration
- `BossOrchestrator.startBoss():91-120` - Session reset

### Elite Mode Rules

**Unlock Condition:**
- **Elite Unlocked:** `lessons.isNotEmpty()` (no minimum lesson count)
- **Test Mode Override:** Always unlocked in test mode

**Evidence:**
- `SessionRunner.resolveEliteUnlocked():897-900`

**Step Structure:**
- **7 Steps Fixed:** `ELITE_STEP_COUNT = 7`
- **Step Size:** `ceil(subLessonSize * 1.25)` cards per step
- **All Lessons:** Cards drawn from all lessons in pack
- **Shuffled:** Card pool is shuffled

**Evidence:**
- `TrainingConfig.ELITE_STEP_COUNT = 7`
- `TrainingConfig.ELITE_SIZE_MULTIPLIER = 1.25`
- `SessionRunner.eliteSubLessonSize():909-910`
- `SessionRunner.buildEliteCards():1242-1246`

**Speed Tracking:**
- **Best Speeds:** Keeps last 7 step speeds
- **Normalization:** Pads with 0.0 if fewer than 7 steps completed
- **Step Advancement:** `(stepIndex + 1) % 7` (wraps around)

**Evidence:**
- `SessionRunner.submitEliteFinish():456-466` - Speed tracking
- `SessionRunner.normalizeEliteSpeeds():901-906` - Normalization

**Session Rules:**
- **Independent Session:** Elite has separate card session (counts reset)
- **Timer Pauses:** Timer pauses on step completion
- **Can Cancel:** User can cancel elite session mid-step

**Evidence:**
- `SessionRunner.openEliteStep():882-886` - Session reset
- `SessionRunner.submitEliteFinish():466` - Timer pause
- `SessionRunner.cancelEliteSession():887-893` - Cancel logic

### Story Mode Rules

**Phase Structure:**
- **CHECK_IN:** Story at beginning of lesson
- **CHECK_OUT:** Story at end of lesson
- **Independent:** Each phase tracked separately

**Evidence:**
- `Models.kt:66-69` - StoryPhase enum

**Completion Rules:**
- **All Correct Required:** Must answer all questions correctly
- **Test Mode Override:** In test mode, completion always persists
- **Retry Allowed:** If not all correct, story is cleared (user can retry)
- **Progress Persistence:** Successful completion saves progress

**Evidence:**
- `StoryRunner.completeStory():62-79` - Completion logic

**Story Loading:**
- **Per Lesson:** Stories tied to specific lesson
- **Per Language:** Stories language-specific
- **Per Phase:** CHECK_IN and CHECK_OUT are separate quizzes
- **First Match:** Loads first story found from LessonStore

**Evidence:**
- `StoryRunner.openStory():39-50` - Story loading
- `LessonStore.getStoryQuizzes()` - Story retrieval (interface)

### Pomodoro Rules

**Timer Rules:**
- **Presets:** Quick (5min), Focus (15min), Classic (20min)
- **Tick Frequency:** Updates every 1 second
- **State Update Frequency:** Main state updated every 10s (atomicity)
- **High-Frequency Flow:** Separate flow for UI countdown (every second)

**Evidence:**
- `Models.kt:225-229` - PomodoroPreset enum
- `PomodoroHelper.tick():160-178` - Timer logic

**Completion Rules:**
- **Timer Reaches 0:** Auto-complete
- **Cards Finish First:** Triggers completion via `onTrainingSessionCompleted()`
- **Stats Calculation:** Based on delta from baseline (correct/incorrect counts)

**Evidence:**
- `PomodoroHelper.completePomodoro():69-104` - Completion logic
- `PomodoroHelper.onTrainingSessionCompleted():110-114` - Early completion

**Stats Tracked:**
- **Cards:** Total shown, correct, incorrect (delta from baseline)
- **Difficulty:** AGAIN, HARD, GOOD, EASY rating counts
- **Speed:** Words per minute (voiceWordCount / voiceActiveMs)
- **Duration:** Selected duration, actual remaining time

**Evidence:**
- `Models.kt:238-246` - PomodoroSessionStats
- `PomodoroHelper.completePomodoro():77-90` - Stats calculation

**Lifecycle Rules:**
- **Pause on App Stop:** Timer pauses when app goes to background
- **Resume on App Start:** Timer resumes when app returns to foreground
- **Sync Remaining:** trueRemainingSeconds synced from state on resume

**Evidence:**
- `PomodoroHelper.onLifecycleStop():135-139` - Pause on stop
- `PomodoroHelper.onLifecycleStart():141-148` - Resume on start

---

## Infrastructure Dependencies

### State Management

**Core State:** `TrainingUiState` (Models.kt:570-590)
- **boss:** `BossState` - Boss battle state
- **elite:** `EliteState` - Elite mode state
- **story:** `StoryState` - Story mode state
- **pomodoro:** `PomodoroState` - Pomodoro timer state
- **cardSession:** `CardSessionStateModel` - Shared card session state
- **navigation:** Lesson/lesson selection, mode selection

**Feature-Owned State:**
- **BossOrchestrator:** Owns `StateFlow<BossState>` (separate from core state)
- **StoryRunner:** Owns `StateFlow<StoryState>` (separate from core state)
- **PomodoroHelper:** No owned state (uses PomodoroState in core state)
- **Elite:** Part of core state (no separate owner)

**Evidence:**
- `BossOrchestrator:43-44` - Owned BossState flow
- `StoryRunner:27-28` - Owned StoryState flow
- `TrainingUiState:586` - EliteState in core state
- `TrainingUiState:590` - PomodoroState in core state

### State Access Layer

**TrainingStateAccess** (feature/daily/TrainingStateAccess.kt)
- **Purpose:** Unified interface for reading/writing TrainingUiState
- **Used By:** BossOrchestrator, StoryRunner, SessionRunner
- **Methods:**
  - `uiState: StateFlow<TrainingUiState>` - Read state
  - `updateState((TrainingUiState) -> TrainingUiState)` - Write state

**Evidence:**
- `BossOrchestrator:34` - Constructor parameter
- `StoryRunner:22` - Constructor parameter

### Progress Persistence

**ProgressStore** (data/ProgressStore.kt)
- **Boss Progress:** `bossLessonRewards`, `bossMegaRewards` maps
- **Elite Progress:** `eliteStepIndex`, `eliteBestSpeeds`
- **Story Progress:** Not in ProgressStore (in LessonStore/pack files)

**Evidence:**
- `ProgressStore:64-70` - Boss reward loading
- `ProgressStore:107-109` - Boss reward saving
- `ProgressStore:78-79` - Elite progress loading
- `ProgressStore:113-114` - Elite progress saving

**LessonStore** (data/LessonStore.kt interface)
- **Story Quizzes:** `getStoryQuizzes(lessonId, phase, languageId)`
- **Story Content:** Stories are part of lesson pack YAML/JSON files

**Evidence:**
- `StoryRunner:43` - Story quiz loading
- `StoryQuizParser` - Story content parsing

### Card Providers

**CardProvider** (feature/training/CardProvider.kt)
- **Boss Cards:** `buildBossCards()` - builds card pool for boss sessions
- **Elite Cards:** `buildEliteCards()` - builds card pool for elite mode
- **Normal Cards:** `buildLessonSessionCards()` - builds regular sub-lesson cards

**Evidence:**
- `CardProvider.buildBossCards():172-198` - Boss card building
- `SessionRunner.buildEliteCards():1242-1246` - Elite card building

### Timer Management

**SessionRunner Timer** (feature/training/SessionRunner.kt)
- **Training Timer:** Shared timer for all training modes
- **Boss Control:** Paused/resumed via BossCommand
- **Elite Control:** Paused on step completion
- **Pomodoro Control:** Independent timer in PomodoroHelper

**Evidence:**
- `SessionRunner:76-79` - Timer job management
- `BossOrchestrator.startBoss():121` - Pause timer command
- `SessionRunner.submitEliteFinish():466` - Pause timer
- `PomodoroHelper:150-158` - Independent timer

---

## State Mutations

### Boss Mode State Changes

**Boss Start (BossOrchestrator.startBoss):**
- **BossState:**
  - `bossActive: true`
  - `bossType: LESSON | MEGA | ELITE`
  - `bossTotal: card count`
  - `bossProgress: 0`
  - `bossReward: null`
  - `bossRewardMessage: null`
  - `savedHintLevel: <current hint level>`
- **Core State:**
  - `boss.bossActive: true`
  - `boss.bossType: <type>`
  - `cardSession.hintLevel: HARD` (forced)
  - `cardSession.sessionState: PAUSED`
  - `cardSession.currentIndex: 0`
  - `cardSession.correctCount: 0`
  - `cardSession.incorrectCount: 0`
  - `cardSession.activeSubLessonIndex: 0`
  - `cardSession.completedSubLessonCount: 0`

**Evidence:** `BossOrchestrator.startBoss():79-120`

**Boss Progress Update (BossOrchestrator.updateBossProgress):**
- **BossState:**
  - `bossProgress: <new value>`
  - `bossReward: <new tier if threshold crossed>`
  - `bossRewardMessage: <message if new reward>`
- **Core State:**
  - `cardSession.sessionState: PAUSED` (if new reward)

**Evidence:** `BossOrchestrator.updateBossProgress():240-253`

**Boss Finish (BossOrchestrator.finishBoss):**
- **BossState:**
  - `bossActive: false`
  - `bossType: null`
  - `bossReward: <final reward>`
  - `bossLessonRewards: <updated map>`
  - `bossMegaRewards: <updated map>`
  - `bossFinishedToken: <incremented>`
- **Core State:**
  - `boss.bossActive: false`
  - `boss.bossType: null`
  - `cardSession.hintLevel: <restored from savedHintLevel>`
  - `cardSession.sessionState: PAUSED`
  - `cardSession.currentIndex: <from ProgressStore>`
  - `cardSession.correctCount: <from ProgressStore>`
  - `cardSession.incorrectCount: <from ProgressStore>`
  - `navigation.selectedLessonId: <from ProgressStore>`
  - `navigation.mode: <from ProgressStore>`

**Evidence:** `BossOrchestrator.finishBoss():140-184`

### Elite Mode State Changes

**Elite Start (SessionRunner.openEliteStep):**
- **Core State:**
  - `elite.eliteActive: true`
  - `elite.eliteStepIndex: <user-selected index>`
  - `cardSession.currentIndex: 0`
  - `cardSession.currentCard: <first card>`
  - `cardSession.correctCount: 0`
  - `cardSession.incorrectCount: 0`
  - `cardSession.sessionState: PAUSED`
  - `cardSession.subLessonTotal: <elite card count>`
  - `cardSession.subLessonCount: 7` (fixed)
  - `cardSession.activeSubLessonIndex: <step index>`

**Evidence:** `SessionRunner.openEliteStep():882-886`

**Elite Finish (SessionRunner.submitEliteFinish):**
- **Core State:**
  - `cardSession.correctCount: <incremented>`
  - `elite.eliteActive: false`
  - `elite.eliteStepIndex: <next step (wraps)>`
  - `elite.eliteBestSpeeds: <updated with new speed>`
  - `elite.eliteFinishedToken: <incremented>`
  - `cardSession.sessionState: PAUSED`
  - `cardSession.currentIndex: 0` (reset for next step)

**Evidence:** `SessionRunner.submitEliteFinish():466`

**Elite Cancel (SessionRunner.cancelEliteSession):**
- **Core State:**
  - `elite.eliteActive: false`
  - `cardSession.sessionState: PAUSED`
  - `cardSession.currentIndex: 0`
  - `cardSession.inputText: ""`

**Evidence:** `SessionRunner.cancelEliteSession():888-892`

### Story Mode State Changes

**Story Open (StoryRunner.openStory):**
- **StoryState:**
  - `activeStory: <loaded quiz>`
  - `storyErrorMessage: null`

**Evidence:** `StoryRunner.openStory():48-49`

**Story Complete (StoryRunner.completeStory):**
- **StoryState (success):**
  - `storyCheckInDone: true` OR `storyCheckOutDone: true`
  - `activeStory: null`
- **StoryState (failure):**
  - `activeStory: null` (user can retry)

**Evidence:** `StoryRunner.completeStory():65-74`

### Pomodoro State Changes

**Pomodoro Start (PomodoroHelper.startPomodoro):**
- **PomodoroState:**
  - `isActive: true`
  - `isPaused: false`
  - `isComplete: false`
  - `selectedDurationMinutes: <user input>`
  - `remainingSeconds: <total seconds>`
  - `totalSeconds: <total seconds>`
  - `baselineCorrect: <current correct count>`
  - `baselineIncorrect: <current incorrect count>`

**Evidence:** `PomodoroHelper.startPomodoro():30-40`

**Pomodoro Tick (PomodoroHelper.tick):**
- **PomodoroState (every 10s):**
  - `remainingSeconds: <decremented>`
- **Independent Counter:**
  - `trueRemainingSeconds: <decremented every 1s>`

**Evidence:** `PomodoroHelper.tick():164-177`

**Pomodoro Complete (PomodoroHelper.completePomodoro):**
- **PomodoroState:**
  - `isComplete: true`
  - `isActive: false`
  - `remainingSeconds: <final remaining>`
  - `stats: <calculated stats>`

**Evidence:** `PomodoroHelper.completePomodoro():92-99`

---

## Test Coverage

### Unit Tests

**Story Mode:**
- ✅ `StoryQuizParserTest` - Tests story JSON parsing
  - Location: `app/src/test/java/com/alexpo/grammermate/data/StoryQuizParserTest.kt`

**Pomodoro:**
- ✅ `PomodoroHelperTest` - Tests Pomodoro timer logic
  - Location: `app/src/test/java/com/alexpo/grammermate/feature/pomodoro/PomodoroHelperTest.kt`
- ✅ `PomodoroHistoryStoreTest` - Tests Pomodoro history persistence
  - Location: `app/src/test/java/com/alexpo/grammermate/data/PomodoroHistoryStoreTest.kt`

**Boss Mode:**
- ❌ No unit tests found for BossBattleRunner
- ❌ No unit tests found for BossOrchestrator
- ❌ No unit tests found for boss reward calculation logic

**Elite Mode:**
- ❌ No unit tests found for elite session logic
- ❌ No unit tests found for elite speed calculation
- ❌ No unit tests found for elite unlock logic

### UI Tests

**Pomodoro:**
- ✅ `PomodoroBannerClickUiTest` - Tests Pomodoro banner UI interactions
- ✅ `PomodoroClickUiTest` - Tests Pomodoro UI flow

**Boss Mode:**
- ❌ No UI tests found for boss battle flow

**Elite Mode:**
- ❌ No UI tests found for elite mode flow

**Story Mode:**
- ❌ No UI tests found for story quiz flow

### Test Coverage Gaps

**Critical Missing Tests:**
1. **BossBattleRunner** - Pure logic module with 0% test coverage
2. **BossOrchestrator** - Orchestration logic with 0% test coverage
3. **Elite session flow** - Start, finish, cancel with 0% test coverage
4. **Story completion flow** - Success/failure paths with 0% test coverage
5. **Mode integration** - Cross-module coordination with 0% test coverage

**Recommendation:**
- Add unit tests for BossBattleRunner pure functions
- Add unit tests for Elite session lifecycle
- Add integration tests for boss/elite/story mode coordination
- Add UI tests for boss/elite/story user flows

---

## Risks / Unclear Points

### Architectural Concerns

**1. State Duplication Between Core and Feature-Owned State**
- **Risk:** BossOrchestrator owns `StateFlow<BossState>` but also updates core `TrainingUiState.boss`
- **Evidence:** `BossOrchestrator:43-44` (owned state), `BossOrchestrator:94-97` (core state update)
- **Impact:** Potential for state divergence if not synchronized correctly
- **Severity:** Medium

**2. Pomodoro State Updates at Different Frequencies**
- **Risk:** `trueRemainingSeconds` updated every 1s, main state every 10s
- **Evidence:** `PomodoroHelper.tick():174-177`
- **Impact:** State inconsistency if app is killed between updates
- **Severity:** Low

**3. Boss Hint Level Override and Restoration**
- **Risk:** Boss forces HARD hint level, but restoration may fail if session ends abnormally
- **Evidence:** `BossOrchestrator:77` (save), `BossOrchestrator:136` (restore)
- **Impact:** User may lose their preferred hint level
- **Severity:** Low

**4. Elite Speed Normalization Edge Cases**
- **Risk:** Normalization pads with 0.0, which may skew average speed calculations
- **Evidence:** `SessionRunner.normalizeEliteSpeeds():902-905`
- **Impact:** First few elite steps show artificially low speeds
- **Severity:** Low

**5. Story Retry Logic**
- **Unclear:** If user fails story quiz, they can retry immediately (no cooldown)
- **Evidence:** `StoryRunner.completeStory():66-67` (clears activeStory on failure)
- **Impact:** Potential for exploit (retry until correct)
- **Severity:** Low

**6. Elite Unlock Condition Too Permissive**
- **Risk:** Elite unlocks if `lessons.isNotEmpty()` (no minimum lesson count)
- **Evidence:** `SessionRunner.resolveEliteUnlocked():897-900`
- **Impact:** Elite available in packs with 1 lesson (may not have enough cards)
- **Severity:** Medium

**7. Boss Elite Card Pool Size**
- **Risk:** Elite boss uses `eliteSize * 7` steps, may be very large
- **Evidence:** `CardProvider.buildBossCards():194-196`
- **Impact:** Performance issues if subLessonSize is large
- **Severity:** Medium

**8. Missing Error Handling for Empty Card Pools**
- **Risk:** Boss/elite sessions may start with empty card pools if lessons have no cards
- **Evidence:** `BossBattleRunner.startBoss():112-123` (checks for empty, but only for boss)
- **Impact:** Elite mode may crash if card pool is empty
- **Severity:** High

**9. Pomodoro Completion on Training Finish**
- **Unclear:** If user finishes all cards before timer, Pomodoro completes automatically
- **Evidence:** `PomodoroHelper.onTrainingSessionCompleted():110-114`
- **Impact:** User may expect timer to continue, but it shows completion summary
- **Severity:** Low

**10. No Integration Tests for Mode Coordination**
- **Risk:** Cross-module coordination (BossCommand, SessionEvent) not tested
- **Impact:** Regressions in mode switching logic
- **Severity:** High

### Performance Concerns

**1. Boss Mega Card Pool Generation**
- **Risk:** MEGA boss loads cards from all lessons up to selected
- **Evidence:** `CardProvider.buildBossCards():186-192`
- **Impact:** May be slow if pack has many lessons with many cards
- **Severity:** Medium

**2. Elite Card Pool Generation**
- **Risk:** Elite loads cards from all lessons in pack
- **Evidence:** `SessionRunner.buildEliteCards():1244-1245`
- **Impact:** May be slow if pack has many lessons
- **Severity:** Medium

**3. Pomodoro Timer Job**
- **Risk:** Timer runs every second while active
- **Evidence:** `PomodoroHelper.startTimer():150-158`
- **Impact:** Battery drain if user leaves Pomodoro active in background
- **Severity:** Low (mitigated by lifecycle pause/resume)

### Data Consistency Concerns

**1. Boss Reward Map Persistence**
- **Risk:** Boss rewards stored in ProgressStore as `Map<String, String>`
- **Evidence:** `ProgressStore:64-70` (loading), `ProgressStore:107-109` (saving)
- **Impact:** String parsing may fail if enum names change
- **Severity:** Low

**2. Elite Speed Persistence**
- **Risk:** Elite speeds stored as `List<Double>` in ProgressStore
- **Evidence:** `ProgressStore:78-79` (loading), `ProgressStore:113-114` (saving)
- **Impact:** List length may not match `ELITE_STEP_COUNT` after config changes
- **Severity:** Medium

**3. Story Progress Not in ProgressStore**
- **Risk:** Story completion tracked in pack files (LessonStore), not ProgressStore
- **Evidence:** No story fields in `TrainingProgress` (Models.kt:150-175)
- **Impact:** Story progress may be lost if pack is re-imported
- **Severity:** Medium

---

## Evidence

### File References

**Boss Mode:**
- `feature/boss/BossOrchestrator.kt:1-349` - Boss orchestration logic
- `feature/boss/BossBattleRunner.kt:1-285` - Boss pure logic module
- `feature/boss/BossResult.kt:1-24` - Boss result data classes
- `feature/training/CardProvider.kt:172-198` - Boss card building
- `ui/TrainingViewModel.kt:1115-1121` - Boss entry points

**Elite Mode:**
- `feature/training/SessionRunner.kt:76-76` - Elite cards storage
- `feature/training/SessionRunner.kt:82-84` - Elite configuration
- `feature/training/SessionRunner.kt:873-906` - Elite session methods
- `feature/training/SessionRunner.kt:447-466` - Elite finish logic
- `feature/training/CardProvider.kt:193-196` - Elite card building
- `ui/TrainingViewModel.kt:961-963` - Elite entry points

**Story Mode:**
- `feature/training/StoryRunner.kt:1-96` - Story session logic
- `feature/training/StoryResult.kt:1-11` - Story result types
- `data/StoryQuizParser.kt:1-53` - Story JSON parsing
- `data/Models.kt:66-85` - StoryPhase enum, StoryQuiz data class

**Pomodoro:**
- `feature/pomodoro/PomodoroHelper.kt:1-189` - Pomodoro timer logic
- `data/Models.kt:225-274` - PomodoroState, PomodoroSessionStats
- `ui/TrainingViewModel.kt:315-318` - Pomodoro entry point

**State Models:**
- `data/Models.kt:502-516` - BossState
- `data/Models.kt:518-523` - StoryState
- `data/Models.kt:540-547` - EliteState
- `data/Models.kt:263-274` - PomodoroState
- `data/Models.kt:570-590` - TrainingUiState (aggregates all mode states)

**Configuration:**
- `data/TrainingConfig.kt:6-16` - Boss/Elite constants
  - `ELITE_STEP_COUNT = 7`
  - `BOSS_UNLOCK_SUB_LESSONS = 15`
  - `MAX_BOSS_CARDS = 300`
  - `ELITE_SIZE_MULTIPLIER = 1.25`
  - `BOSS_BRONZE_PCT = 30.0`
  - `BOSS_SILVER_PCT = 60.0`
  - `BOSS_GOLD_PCT = 90.0`

**Progress Persistence:**
- `data/ProgressStore.kt:64-70` - Boss reward loading
- `data/ProgressStore.kt:78-79` - Elite progress loading
- `data/ProgressStore.kt:107-109` - Boss reward saving
- `data/ProgressStore.kt:113-114` - Elite progress saving

**Command/Event Handling:**
- `feature/boss/BossOrchestrator.kt:8-23` - BossCommand sealed class
- `feature/training/SessionEvent.kt` - SessionEvent sealed class
- `ui/TrainingViewModel.kt:1650-1680` - handleBossCommands()
- `ui/TrainingViewModel.kt:1682-1710` - handleSessionEvents()

---

## Summary

**Architecture Style:** Command/Event pattern with feature-owned state

**Key Strengths:**
1. Clear separation of concerns (orchestrators, runners, parsers)
2. Pure logic modules (BossBattleRunner) are testable
3. Command pattern enables cross-module coordination
4. Each mode owns its state and lifecycle

**Key Weaknesses:**
1. **Zero test coverage** for boss/elite mode logic (critical gap)
2. State duplication between core and feature-owned state (risk of divergence)
3. No integration tests for mode coordination
4. Elite unlock condition too permissive (may not have enough cards)
5. Missing error handling for empty elite card pools

**Refactoring Recommendations:**
1. Add unit tests for BossBattleRunner (pure functions, high value)
2. Add unit tests for Elite session lifecycle
3. Consider unifying state ownership (either all core or all feature-owned)
4. Add integration tests for mode switching
5. Tighten elite unlock condition (require minimum lesson/card count)
6. Add error handling for empty card pools in elite mode

**Overall Assessment:** Special modes are well-architected but under-tested. The command/event pattern is sound, but the lack of test coverage for critical paths (boss rewards, elite progression) is a significant risk for regression.
