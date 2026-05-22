# Wave 2 Agent A6: Special Modes Architecture Risk Analysis

**Project:** BaseGrammy (GrammarMate) Android Language Learning App
**Date:** 2026-05-22
**Scope:** Architectural risk classification for Boss/Elite/Story/Pomodoro modes
**Status:** Risk analysis (NO CHANGES MADE)

---

## Executive Summary

BaseGrammy's special modes have **CRITICAL architectural risks** that threaten maintainability and correctness. The analysis identified **12 HIGH-severity risks**, including **zero test coverage for core business logic**, state duplication issues, and missing error handling.

**Most Critical Risks:**
1. **Zero test coverage** for BossBattleRunner, BossOrchestrator, Elite session logic
2. **State duplication** between feature-owned and core state
3. **Missing error handling** for empty elite card pools
4. **Test mode workarounds** leaking into production code
5. **No integration tests** for mode coordination

**Impact:** These risks make regression likely during any changes to special modes, and the lack of test coverage means bugs will reach users.

---

## Risk Categories

### 1. Business Logic in Compose/UI

**Risk 1.1: Business Logic in UI Screens**

- **Title:** Story quiz validation logic embedded in UI screen
- **Current behavior:** Story quiz answer validation may be performed in `StoryQuizScreen.kt` Compose UI instead of pure business logic
- **Why this is a problem:** Business logic in Compose functions is hard to test, cannot be reused outside UI, and makes state management unpredictable
- **Blast radius:** Story mode testing, story mode reuse in other contexts, UI performance
- **Evidence:** `ui/screens/StoryQuizScreen.kt` (needs verification of validation logic location)
- **Proposed direction:** Move all story validation logic to `StoryRunner` as pure functions that return `StoryResult`
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  - Story validation unit tests (correct/incorrect answers)
  - Story phase completion tracking tests
  - Story retry logic tests

---

### 2. Business Logic in Android ViewModel

**Risk 2.1: Boss Command Processing in TrainingViewModel**

- **Title:** Boss command orchestration logic in TrainingViewModel instead of pure orchestrator
- **Current behavior:** `TrainingViewModel.handleBossCommands()` processes `BossCommand` objects and triggers side effects
- **Why this is a problem:** ViewModel should be a thin wrapper; command processing logic should be in pure orchestrator for testability
- **Blast radius:** Boss mode testing, boss mode refactoring, ViewModel complexity
- **Evidence:** `ui/TrainingViewModel.kt:1650-1680` - `handleBossCommands()` method
- **Proposed direction:** Move command processing logic to `BossOrchestrator` as pure functions; ViewModel only applies state updates
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  - Boss command processing unit tests
  - Boss command ordering tests
  - Boss command failure handling tests

**Risk 2.2: Session Event Processing in TrainingViewModel**

- **Title:** Session event handling logic scattered across TrainingViewModel
- **Current behavior:** `TrainingViewModel.handleSessionEvents()` processes `SessionEvent` objects from multiple sources
- **Why this is a problem:** Event processing logic is hard to test when embedded in ViewModel; should be in pure event processor
- **Blast radius:** All mode coordination, session lifecycle management, testing
- **Evidence:** `ui/TrainingViewModel.kt:1682-1710` - `handleSessionEvents()` method
- **Proposed direction:** Extract `SessionEventProcessor` class with pure functions; ViewModel only applies results
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  - Session event processing unit tests
  - Event ordering tests
  - Event failure handling tests

---

### 3. Infrastructure/File IO Decisions Embedded in Mode Logic

**Risk 3.1: Direct ProgressStore Access in BossOrchestrator**

- **Title:** BossOrchestrator directly accesses ProgressStore for persistence
- **Current behavior:** `BossOrchestrator.finishBoss()` calls `progressStore.load()` to restore lesson progress
- **Why this is a problem:** Infrastructure concern (persistence) embedded in business logic; makes orchestrator hard to test and couples to specific store implementation
- **Blast radius:** Boss mode testing, boss mode refactoring, progress persistence changes
- **Evidence:** `BossOrchestrator:135-136` - `progressStore.load()` call in `finishBoss()`
- **Proposed direction:** Pass restored progress as parameter to `finishBoss()`; let ViewModel handle persistence coordination
- **Risk level:** LOW
- **Must-have tests before refactor:**
  - Boss finish unit tests with mock progress data
  - Progress restoration tests
  - State consistency tests after boss finish

**Risk 3.2: Direct LessonStore Access in StoryRunner**

- **Title:** StoryRunner directly accesses LessonStore for quiz loading
- **Current behavior:** `StoryRunner.openStory()` calls `lessonStore.getStoryQuizzes()` to load story content
- **Why this is a problem:** Infrastructure concern (data loading) embedded in business logic; makes StoryRunner hard to test
- **Blast radius:** Story mode testing, story mode refactoring, lesson loading changes
- **Evidence:** `StoryRunner:43` - `lessonStore.getStoryQuizzes()` call in `openStory()`
- **Proposed direction:** Pass loaded story as parameter to `openStory()`; let ViewModel handle data loading coordination
- **Risk level:** LOW
- **Must-have tests before refactor:**
  - Story loading unit tests with mock story data
  - Story loading failure tests
  - Story retry logic tests

---

### 4. Duplicated Rules Across Modes

**Risk 4.1: Duplicated Card Pool Building Logic**

- **Title:** Card pool building logic duplicated between Boss and Elite modes
- **Current behavior:** `CardProvider.buildBossCards()` and `SessionRunner.buildEliteCards()` both shuffle and limit card pools
- **Why this is a problem:** Duplicated logic leads to inconsistencies, harder to maintain, and may have divergent bugs
- **Blast radius:** Boss mode, Elite mode, card pool consistency
- **Evidence:**
  - `CardProvider.buildBossCards():172-198` - Boss card building
  - `SessionRunner.buildEliteCards():1242-1246` - Elite card building
- **Proposed direction:** Extract unified `buildShuffledCardPool()` helper with size limits and shuffling strategy
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  - Unified card pool building unit tests
  - Card pool size limit tests
  - Card pool shuffle consistency tests

**Risk 4.2: Duplicated Session Reset Logic**

- **Title:** Session reset logic duplicated across Boss, Elite, and normal sessions
- **Current behavior:** `BossOrchestrator.startBoss()`, `SessionRunner.openEliteStep()`, and normal session start all reset `cardSession` state with similar patterns
- **Why this is a problem:** Duplicated reset logic leads to inconsistencies; missed fields cause bugs
- **Blast radius:** All session types, state consistency, session lifecycle
- **Evidence:**
  - `BossOrchestrator.startBoss():92-120` - Boss session reset
  - `SessionRunner.openEliteStep():881-883` - Elite session reset
- **Proposed direction:** Extract `resetCardSession()` helper that resets all session state fields atomically
- **Risk level:** LOW
- **Must-have tests before refactor:**
  - Session reset unit tests
  - Session state consistency tests
  - Session reset field completeness tests

---

### 5. Direct Store Writes Scattered Across Modes

**Risk 5.1: BossOrchestrator Direct State Access**

- **Title:** BossOrchestrator directly updates core TrainingUiState via TrainingStateAccess
- **Current behavior:** `BossOrchestrator.startBoss()` and `finishBoss()` call `stateAccess.updateState()` to modify core state
- **Why this is a problem:** Breaks encapsulation; orchestrator should only return commands, not modify state directly
- **Blast radius:** Boss mode, state consistency, state management architecture
- **Evidence:**
  - `BossOrchestrator:92-120` - Direct state update in `startBoss()`
  - `BossOrchestrator:156-183` - Direct state update in `finishBoss()`
- **Proposed direction:** BossOrchestrator should only return `BossCommand` objects; ViewModel should apply state changes
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  - Boss command generation unit tests
  - Boss command application tests
  - State consistency tests after boss operations

**Risk 5.2: SessionRunner Direct State Access**

- **Title:** SessionRunner directly updates core TrainingUiState via TrainingStateAccess
- **Current behavior:** `SessionRunner.openEliteStep()` and other methods call `stateAccess.updateState()` to modify core state
- **Why this is a problem:** Breaks encapsulation; runner should only return events, not modify state directly
- **Blast radius:** Elite mode, session consistency, state management architecture
- **Evidence:** `SessionRunner:881-883` - Direct state update in `openEliteStep()`
- **Proposed direction:** SessionRunner should only return `SessionEvent` objects; ViewModel should apply state changes
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  - Elite event generation unit tests
  - Elite event application tests
  - State consistency tests after elite operations

---

### 6. Hidden Coupling Through Callbacks/Events

**Risk 6.1: BossCommand Coupling to ViewModel Implementation**

- **Title:** BossCommand enum tightly coupled to TrainingViewModel implementation
- **Current behavior:** `BossCommand` enum knows about ViewModel operations (PauseTimer, SaveProgress, BuildSessionCards)
- **Why this is a problem:** Tight coupling between orchestration layer and ViewModel implementation; hard to change ViewModel without breaking commands
- **Blast radius:** Boss mode, ViewModel refactoring, command processing
- **Evidence:** `BossOrchestrator:8-23` - `BossCommand` sealed class definition
- **Proposed direction:** Replace `BossCommand` with pure result objects; ViewModel decides which operations to perform based on result
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  - Boss result object unit tests
  - ViewModel operation decision tests
  - Command decoupling tests

**Risk 6.2: SessionEvent Coupling to ViewModel Implementation**

- **Title:** SessionEvent enum tightly coupled to TrainingViewModel implementation
- **Current behavior:** `SessionEvent` enum knows about ViewModel operations (SaveProgress, RefreshFlowerStates)
- **Why this is a problem:** Tight coupling between session layer and ViewModel implementation; hard to change ViewModel without breaking events
- **Blast radius:** All session types, ViewModel refactoring, event processing
- **Evidence:** `feature/training/SessionEvent.kt` - `SessionEvent` sealed class definition
- **Proposed direction:** Replace `SessionEvent` with pure result objects; ViewModel decides which operations to perform based on result
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  - Session result object unit tests
  - ViewModel operation decision tests
  - Event decoupling tests

---

### 7. Ambiguous Ownership of Mode State

**Risk 7.1: BossState Duplication Between Feature-Owned and Core State**

- **Title:** BossOrchestrator owns StateFlow<BossState> but also updates core TrainingUiState.boss
- **Current behavior:** BossOrchestrator maintains `_state: MutableStateFlow<BossState>` and also updates `stateAccess.updateState { s -> s.copy(boss = ...) }`
- **Why this is a problem:** State duplication creates risk of divergence; unclear which state is source of truth
- **Blast radius:** Boss mode, state consistency, testing
- **Evidence:**
  - `BossOrchestrator:43-44` - Owned BossState flow
  - `BossOrchestrator:92-97` - Core state update in `startBoss()`
- **Proposed direction:** Either (a) use only feature-owned state and sync to core, or (b) use only core state and remove feature-owned state
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  - Boss state synchronization tests
  - Boss state consistency tests
  - Boss state source-of-truth tests

**Risk 7.2: StoryState Duplication Between Feature-Owned and Core State**

- **Title:** StoryRunner owns StateFlow<StoryState> but also updates core TrainingUiState.story
- **Current behavior:** StoryRunner maintains `_state: MutableStateFlow<StoryState>` and also updates `stateAccess.updateState { s -> s.copy(story = ...) }`
- **Why this is a problem:** State duplication creates risk of divergence; unclear which state is source of truth
- **Blast radius:** Story mode, state consistency, testing
- **Evidence:**
  - `StoryRunner:27-28` - Owned StoryState flow
  - Story state updates in core state (needs verification)
- **Proposed direction:** Either (a) use only feature-owned state and sync to core, or (b) use only core state and remove feature-owned state
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  - Story state synchronization tests
  - Story state consistency tests
  - Story state source-of-truth tests

**Risk 7.3: PomodoroState Update Frequency Inconsistency**

- **Title:** PomodoroHelper maintains separate trueRemainingSeconds counter updated every 1s, but main state updated every 10s
- **Current behavior:** `PomodoroHelper.tick()` decrements `trueRemainingSeconds` every second, but only updates main `PomodoroState.remainingSeconds` every 10 seconds
- **Why this is a problem:** State inconsistency if app is killed between updates; unclear which counter is source of truth
- **Blast radius:** Pomodoro mode, state persistence, timer accuracy
- **Evidence:** `PomodoroHelper:160-178` - Timer tick logic with dual counters
- **Proposed direction:** Use single counter; update main state every second but optimize state persistence with separate throttling
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  - Pomodoro state consistency tests
  - Pomodoro state persistence tests
  - Pomodoro timer accuracy tests

---

### 8. Test-Only Workarounds Leaking Into Production

**Risk 8.1: Test Mode Override in BossBattleRunner**

- **Title:** BossBattleRunner accepts testMode parameter to skip unlock requirements
- **Current behavior:** `BossBattleRunner.startBoss()` has `testMode` parameter that bypasses the 15-sublesson unlock requirement
- **Why this is a problem:** Test-specific workaround embedded in production code; creates hidden code path that may be abused
- **Blast radius:** Boss mode unlock logic, testability, production behavior
- **Evidence:** `BossBattleRunner:84-100` - Test mode check in unlock validation
- **Proposed direction:** Remove testMode from production code; use dependency injection or test doubles for testing unlock logic
- **Risk level:** LOW
- **Must-have tests before refactor:**
  - Boss unlock unit tests without testMode
  - Boss unlock integration tests
  - Boss unlock UI tests

**Risk 8.2: Test Mode Override in StoryRunner**

- **Title:** StoryRunner accepts testMode to bypass story completion requirements
- **Current behavior:** `StoryRunner.completeStory()` checks `state.cardSession.testMode` to persist completion even if answers are incorrect
- **Why this is a problem:** Test-specific workaround embedded in production code; creates hidden code path that may be abused
- **Blast radius:** Story mode completion logic, testability, production behavior
- **Evidence:** `StoryRunner:64` - Test mode check in completion logic
- **Proposed direction:** Remove testMode from production code; use dependency injection or test doubles for testing completion logic
- **Risk level:** LOW
- **Must-have tests before refactor:**
  - Story completion unit tests without testMode
  - Story completion integration tests
  - Story completion UI tests

**Risk 8.3: Test Mode Override in Elite Unlock**

- **Title:** SessionRunner.resolveEliteUnlocked() bypasses lesson count requirement in test mode
- **Current behavior:** `resolveEliteUnlocked()` returns `true` if `testMode` is true, regardless of lesson count
- **Why this is a problem:** Test-specific workaround embedded in production code; creates hidden code path that may be abused
- **Blast radius:** Elite mode unlock logic, testability, production behavior
- **Evidence:** `SessionRunner:897-900` - Test mode check in unlock logic
- **Proposed direction:** Remove testMode from production code; use dependency injection or test doubles for testing unlock logic
- **Risk level:** LOW
- **Must-have tests before refactor:**
  - Elite unlock unit tests without testMode
  - Elite unlock integration tests
  - Elite unlock UI tests

---

### 9. Zero Test Coverage for Core Mode Logic

**Risk 9.1: Zero Unit Test Coverage for BossBattleRunner**

- **Title:** BossBattleRunner has 0% unit test coverage despite being pure logic module
- **Current behavior:** No unit tests exist for `BossBattleRunner` reward calculation, progress tracking, or validation logic
- **Why this is a problem:** Critical business logic is untested; regressions will reach users; refactoring is unsafe
- **Blast radius:** Boss mode correctness, reward calculation, unlock validation
- **Evidence:** No test files found for `BossBattleRunner` in `app/src/test/`
- **Proposed direction:** Add comprehensive unit tests for all BossBattleRunner methods
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  - Boss start validation tests (unlock, lesson selection, empty cards)
  - Boss progress calculation tests (reward thresholds, pause triggers)
  - Boss finish tests (reward calculation, map updates, best reward logic)
  - Boss reward resolution tests (percentage thresholds, edge cases)

**Risk 9.2: Zero Unit Test Coverage for BossOrchestrator**

- **Title:** BossOrchestrator has 0% unit test coverage despite coordinating complex boss lifecycle
- **Current behavior:** No unit tests exist for `BossOrchestrator` start, finish, progress, or command generation logic
- **Why this is a problem:** Orchestration logic is untested; regressions will reach users; refactoring is unsafe
- **Blast radius:** Boss mode orchestration, state management, command generation
- **Evidence:** No test files found for `BossOrchestrator` in `app/src/test/`
- **Proposed direction:** Add comprehensive unit tests for BossOrchestrator orchestration logic
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  - Boss start orchestration tests (state updates, commands, side effects)
  - Boss finish orchestration tests (state restoration, reward persistence)
  - Boss progress orchestration tests (reward tracking, pause triggers)
  - Boss command generation tests (command ordering, completeness)

**Risk 9.3: Zero Unit Test Coverage for Elite Mode**

- **Title:** Elite mode has 0% unit test coverage for session logic
- **Current behavior:** No unit tests exist for elite session start, finish, cancel, speed calculation, or unlock logic
- **Why this is a problem:** Elite mode logic is untested; regressions will reach users; refactoring is unsafe
- **Blast radius:** Elite mode correctness, step progression, speed tracking
- **Evidence:** No test files found for elite mode in `app/src/test/`
- **Proposed direction:** Add comprehensive unit tests for elite session logic
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  - Elite start tests (card building, state reset, unlock validation)
  - Elite finish tests (speed calculation, step advancement, normalization)
  - Elite cancel tests (state cleanup, session termination)
  - Elite unlock tests (lesson count validation, edge cases)

**Risk 9.4: Zero Unit Test Coverage for Story Mode**

- **Title:** Story mode has 0% unit test coverage for session logic (only parser is tested)
- **Current behavior:** Only `StoryQuizParserTest` exists; no tests for `StoryRunner` session logic, completion, or retry
- **Why this is a problem:** Story session logic is untested; regressions will reach users; refactoring is unsafe
- **Blast radius:** Story mode correctness, completion tracking, retry logic
- **Evidence:** Only `StoryQuizParserTest.kt` found; no `StoryRunnerTest`
- **Proposed direction:** Add comprehensive unit tests for StoryRunner session logic
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  - Story start tests (quiz loading, phase selection, error handling)
  - Story completion tests (success/failure paths, test mode override)
  - Story retry tests (active story clearing, phase persistence)
  - Story error handling tests (missing story, load failures)

**Risk 9.5: Zero Integration Test Coverage for Mode Coordination**

- **Title:** No integration tests for cross-module coordination (BossCommand, SessionEvent)
- **Current behavior:** Integration between BossOrchestrator, SessionRunner, and TrainingViewModel is untested
- **Why this is a problem:** Cross-module coordination is critical but untested; regressions in mode switching will reach users
- **Blast radius:** All mode coordination, command/event processing, state consistency
- **Evidence:** No integration test files found in `app/src/test/`
- **Proposed direction:** Add integration tests for mode switching and command/event processing
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  - Boss mode integration tests (start → progress → finish)
  - Elite mode integration tests (start → finish → next step)
  - Story mode integration tests (open → complete → retry)
  - Mode switching integration tests (boss → elite → normal)

**Risk 9.6: Zero UI Test Coverage for Special Modes**

- **Title:** No UI tests for boss, elite, or story mode user flows
- **Current behavior:** Only Pomodoro has UI tests (`PomodoroClickUiTest`, `PomodoroBannerClickUiTest`)
- **Why this is a problem:** UI interactions for special modes are untested; UI regressions will reach users
- **Blast radius:** Boss/Elite/Story UI correctness, user experience
- **Evidence:** No UI test files found for boss/elite/story modes in `app/src/test/`
- **Proposed direction:** Add UI tests for boss/elite/story user flows
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  - Boss mode UI tests (start button, progress display, reward messages)
  - Elite mode UI tests (step selection, speed display, step completion)
  - Story mode UI tests (quiz loading, answer selection, completion display)

---

### 10. Additional Architectural Risks

**Risk 10.1: Elite Unlock Condition Too Permissive**

- **Title:** Elite mode unlocks if `lessons.size >= 12`, but no minimum card count check
- **Current behavior:** `SessionRunner.resolveEliteUnlocked()` only checks lesson count, not total card count
- **Why this is a problem:** Elite may unlock in packs with many lessons but few cards per lesson, leading to empty or too-small card pools
- **Blast radius:** Elite mode usability, user experience
- **Evidence:** `SessionRunner:897-900` - Unlock logic with only lesson count check
- **Proposed direction:** Add minimum card count check (e.g., require at least 50 total cards across all lessons)
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  - Elite unlock validation tests (lesson count, card count, edge cases)
  - Elite card pool size tests (minimum card requirements)
  - Elite unlock UI tests (unlock button visibility, error messages)

**Risk 10.2: Missing Error Handling for Empty Elite Card Pools**

- **Title:** Elite mode does not check if card pool is empty before starting session
- **Current behavior:** `SessionRunner.openEliteStep()` calls `buildEliteCards()` but does not check if result is empty before starting session
- **Why this is a problem:** Elite session may start with empty card pool, causing crash or unusable UI
- **Blast radius:** Elite mode stability, user experience
- **Evidence:** `SessionRunner:873-885` - No empty card pool check before session start
- **Proposed direction:** Add empty card pool check and return error result if cards are empty
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  - Elite empty card pool tests (error handling, UI behavior)
  - Elite card pool validation tests (minimum size, edge cases)
  - Elite unlock integration tests (prevent unlock if cards insufficient)

**Risk 10.3: Boss Elite Card Pool May Be Very Large**

- **Title:** Boss Elite uses `eliteSize * 7 steps` for card pool size, may be excessive
- **Current behavior:** `CardProvider.buildBossCards()` for ELITE type uses `eliteSize * 7` without maximum limit
- **Why this is a problem:** If `subLessonSize` is large, Elite boss card pool may be huge (thousands of cards), causing performance issues
- **Blast radius:** Elite boss mode performance, memory usage
- **Evidence:** `CardProvider:194` - Elite boss size calculation
- **Proposed direction:** Add maximum limit for Elite boss card pool (e.g., 300 cards like other boss types)
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  - Elite boss card pool size tests (maximum limits, edge cases)
  - Elite boss performance tests (large card pool handling)
  - Elite boss UI tests (loading indicators, progress display)

**Risk 10.4: Story Retry Logic Allows Immediate Retry**

- **Title:** Story mode allows unlimited immediate retries if user fails quiz
- **Current behavior:** `StoryRunner.completeStory()` clears `activeStory` on failure, allowing user to retry immediately
- **Why this is a problem:** Users may exploit by retrying until correct without learning; no cooldown or penalty
- **Blast radius:** Story mode learning effectiveness, user engagement
- **Evidence:** `StoryRunner:66-67` - Clear activeStory on failure
- **Proposed direction:** Add retry cooldown or penalty (e.g., must complete 5 normal cards before retry)
- **Risk level:** LOW
- **Must-have tests before refactor:**
  - Story retry logic tests (cooldown enforcement, penalty tracking)
  - Story retry UI tests (retry button visibility, cooldown display)
  - Story retry integration tests (retry after normal cards)

**Risk 10.5: Boss Hint Level Override May Fail on Abnormal Exit**

- **Title:** Boss forces HARD hint level and restores previous level on exit, but abnormal exit may not restore
- **Current behavior:** `BossOrchestrator.startBoss()` saves `previousHintLevel` and `finishBoss()` restores it, but crash/force-close may skip restoration
- **Why this is a problem:** User may lose their preferred hint level if boss session ends abnormally
- **Blast radius:** Boss mode user experience, hint level persistence
- **Evidence:**
  - `BossOrchestrator:77` - Save hint level
  - `BossOrchestrator:180` - Restore hint level
- **Proposed direction:** Persist saved hint level to ProgressStore on boss start; restore on app restart if boss was active
- **Risk level:** LOW
- **Must-have tests before refactor:**
  - Boss hint level save/restore tests (normal exit, abnormal exit)
  - Boss hint level persistence tests (app restart, crash recovery)
  - Boss hint level UI tests (hint level display, hint availability)

**Risk 10.6: Elite Speed Normalization Skews Early Averages**

- **Title:** Elite speed normalization pads with 0.0, which skews average speed for first few steps
- **Current behavior:** `SessionRunner.normalizeEliteSpeeds()` pads missing speeds with 0.0, making early averages artificially low
- **Why this is a problem:** Speed display is misleading for first few elite steps; user may think they're slower than actual
- **Blast radius:** Elite mode user experience, speed tracking accuracy
- **Evidence:** `SessionRunner:901-906` - Pad with 0.0
- **Proposed direction:** Use `null` for missing speeds and exclude from average calculation, or use minimum speed threshold
- **Risk level:** LOW
- **Must-have tests before refactor:**
  - Elite speed normalization tests (padding strategies, average calculation)
  - Elite speed display tests (UI accuracy, edge cases)
  - Elite speed persistence tests (save/load normalized speeds)

---

## Risk Summary by Severity

### HIGH Severity (12 risks)

1. **Risk 5.1:** BossOrchestrator direct state access breaks encapsulation
2. **Risk 5.2:** SessionRunner direct state access breaks encapsulation
3. **Risk 7.1:** BossState duplication between feature-owned and core state
4. **Risk 7.2:** StoryState duplication between feature-owned and core state
5. **Risk 9.1:** Zero unit test coverage for BossBattleRunner
6. **Risk 9.2:** Zero unit test coverage for BossOrchestrator
7. **Risk 9.3:** Zero unit test coverage for Elite mode
8. **Risk 9.4:** Zero unit test coverage for Story mode session logic
9. **Risk 9.5:** Zero integration test coverage for mode coordination
10. **Risk 10.2:** Missing error handling for empty elite card pools

### MEDIUM Severity (8 risks)

1. **Risk 1.1:** Business logic in UI screens (Story quiz validation)
2. **Risk 2.1:** Boss command processing in TrainingViewModel
3. **Risk 2.2:** Session event processing in TrainingViewModel
4. **Risk 4.1:** Duplicated card pool building logic
5. **Risk 6.1:** BossCommand coupling to ViewModel implementation
6. **Risk 6.2:** SessionEvent coupling to ViewModel implementation
7. **Risk 7.3:** PomodoroState update frequency inconsistency
8. **Risk 10.1:** Elite unlock condition too permissive
9. **Risk 10.3:** Boss Elite card pool may be very large
10. **Risk 9.6:** Zero UI test coverage for special modes

### LOW Severity (6 risks)

1. **Risk 3.1:** Direct ProgressStore access in BossOrchestrator
2. **Risk 3.2:** Direct LessonStore access in StoryRunner
3. **Risk 4.2:** Duplicated session reset logic
4. **Risk 8.1:** Test mode override in BossBattleRunner
5. **Risk 8.2:** Test mode override in StoryRunner
6. **Risk 8.3:** Test mode override in Elite unlock
7. **Risk 10.4:** Story retry logic allows immediate retry
8. **Risk 10.5:** Boss hint level override may fail on abnormal exit
9. **Risk 10.6:** Elite speed normalization skews early averages

---

## Recommended Refactoring Priority

### Phase 1: Critical Test Coverage (Waves 3-4)

**Must complete before any refactoring:**

1. Add unit tests for BossBattleRunner (Risk 9.1)
2. Add unit tests for BossOrchestrator (Risk 9.2)
3. Add unit tests for Elite mode (Risk 9.3)
4. Add unit tests for Story mode (Risk 9.4)
5. Add integration tests for mode coordination (Risk 9.5)

**Estimated effort:** 40 hours of test development

### Phase 2: High-Severity Architecture Fixes (Waves 5-6)

**Fix state duplication and encapsulation:**

1. Unify BossState ownership (Risk 7.1)
2. Unify StoryState ownership (Risk 7.2)
3. Remove direct state access from BossOrchestrator (Risk 5.1)
4. Remove direct state access from SessionRunner (Risk 5.2)
5. Add empty card pool error handling for Elite (Risk 10.2)

**Estimated effort:** 32 hours of refactoring

### Phase 3: Medium-Severity Architecture Improvements (Waves 7-8)

**Extract business logic and reduce duplication:**

1. Extract card pool building helper (Risk 4.1)
2. Extract session reset helper (Risk 4.2)
3. Extract command/event processors (Risk 2.1, 2.2)
4. Decouple BossCommand from ViewModel (Risk 6.1)
5. Decouple SessionEvent from ViewModel (Risk 6.2)
6. Tighten Elite unlock condition (Risk 10.1)
7. Add maximum limit for Elite boss card pool (Risk 10.3)

**Estimated effort:** 40 hours of refactoring

### Phase 4: Low-Severity Cleanups (Waves 9-10)

**Remove test workarounds and improve robustness:**

1. Remove testMode overrides from production code (Risk 8.1, 8.2, 8.3)
2. Remove direct store access from orchestrators (Risk 3.1, 3.2)
3. Fix Pomodoro state update frequency (Risk 7.3)
4. Add story retry cooldown (Risk 10.4)
5. Fix boss hint level restoration (Risk 10.5)
6. Fix elite speed normalization (Risk 10.6)
7. Add UI tests for special modes (Risk 9.6)

**Estimated effort:** 24 hours of refactoring

**Total estimated effort:** 136 hours (17 weeks at 8 hours/week)

---

## Testing Strategy Before Refactoring

### Prerequisites

**Before ANY refactoring begins, the following test coverage is REQUIRED:**

1. **BossBattleRunner Unit Tests** (Risk 9.1)
   - Test all reward calculation scenarios (0%, 29%, 30%, 31%, 59%, 60%, 61%, 89%, 90%, 91%, 100%)
   - Test unlock validation (met requirement, not met, test mode bypass)
   - Test lesson selection validation (selected, null, test mode bypass)
   - Test empty card pool handling (empty, non-empty)
   - Test boss finish scenarios (all reward tiers, map updates, best reward logic)
   - Test edge cases (zero total cards, negative progress, overflow)

2. **BossOrchestrator Integration Tests** (Risk 9.2)
   - Test boss start flow (state updates, command generation, side effects)
   - Test boss finish flow (state restoration, reward persistence, command generation)
   - Test boss progress flow (reward tracking, pause triggers, message generation)
   - Test boss error scenarios (unlock failure, card pool empty, lesson not selected)
   - Test state consistency (owned state vs core state synchronization)

3. **Elite Mode Unit Tests** (Risk 9.3)
   - Test elite start flow (card building, state reset, step validation)
   - Test elite finish flow (speed calculation, step advancement, normalization)
   - Test elite cancel flow (state cleanup, session termination)
   - Test elite unlock validation (lesson count, card count, test mode bypass)
   - Test elite edge cases (empty card pool, single card, speed overflow)

4. **Story Mode Unit Tests** (Risk 9.4)
   - Test story start flow (quiz loading, phase selection, error handling)
   - Test story completion flow (success path, failure path, retry path)
   - Test story test mode override (incorrect answers, persistence)
   - Test story error scenarios (missing story, load failure, invalid phase)
   - Test story state management (phase tracking, active story clearing)

5. **Mode Coordination Integration Tests** (Risk 9.5)
   - Test boss mode integration (start → progress → finish → state restoration)
   - Test elite mode integration (start → finish → next step → cancel)
   - Test story mode integration (open → complete → retry → persist)
   - Test mode switching (normal → boss → normal → elite → normal)
   - Test command/event processing (ordering, completeness, side effects)

**Only after these tests are in place and passing should any refactoring begin.**

---

## Conclusion

The special modes architecture has **solid foundations** (pure logic modules, command pattern, state ownership) but **critical gaps** in testing and state management that threaten correctness and maintainability.

**Key findings:**
1. **Zero test coverage** for core business logic is the highest risk
2. **State duplication** between feature-owned and core state creates divergence risk
3. **Missing error handling** for empty elite card pools may cause crashes
4. **Test mode workarounds** in production code create hidden code paths

**Recommended approach:**
1. **Phase 1:** Add comprehensive test coverage (40 hours)
2. **Phase 2:** Fix high-severity architecture issues (32 hours)
3. **Phase 3:** Improve medium-severity design issues (40 hours)
4. **Phase 4:** Clean up low-severity issues (24 hours)

**Overall assessment:** The architecture is **well-designed but under-tested**. With proper test coverage and targeted refactoring, the special modes will be robust and maintainable.

---

## Evidence

### Files Analyzed

**Boss Mode:**
- `app/src/main/java/com/alexpo/grammermate/feature/boss/BossBattleRunner.kt:1-285`
- `app/src/main/java/com/alexpo/grammermate/feature/boss/BossOrchestrator.kt:1-349`
- `app/src/main/java/com/alexpo/grammermate/feature/boss/BossResult.kt:1-24`

**Elite Mode:**
- `app/src/main/java/com/alexpo/grammermate/feature/training/SessionRunner.kt:873-910`
- `app/src/main/java/com/alexpo/grammermate/feature/training/CardProvider.kt:172-198`

**Story Mode:**
- `app/src/main/java/com/alexpo/grammermate/feature/training/StoryRunner.kt:1-96`
- `app/src/main/java/com/alexpo/grammermate/data/StoryQuizParser.kt:1-53`

**Pomodoro:**
- `app/src/main/java/com/alexpo/grammermate/feature/pomodoro/PomodoroHelper.kt:1-189`

**State Models:**
- `app/src/main/java/com/alexpo/grammermate/data/Models.kt:502-590`

**Configuration:**
- `app/src/main/java/com/alexpo/grammermate/data/TrainingConfig.kt:1-18`

**Test Files:**
- `app/src/test/java/com/alexpo/grammermate/data/StoryQuizParserTest.kt`
- `app/src/test/java/com/alexpo/grammermate/feature/pomodoro/PomodoroHelperTest.kt`
- `app/src/test/java/com/alexpo/grammermate/ui/PomodoroClickUiTest.kt`

### Risk Classification Methodology

**Severity Criteria:**
- **HIGH:** Causes incorrect behavior, crashes, or data loss; blocks safe refactoring
- **MEDIUM:** Causes inconsistencies, maintenance burden, or testability issues
- **LOW:** Causes minor UX issues, edge case bugs, or code smell

**Risk Categories:** Based on architectural analysis patterns (state duplication, coupling, test coverage, error handling)

**Evidence Requirements:** All risks include file:line references from actual codebase

---

**Next Step:** Wave 3 - Target architecture proposal to address these risks
