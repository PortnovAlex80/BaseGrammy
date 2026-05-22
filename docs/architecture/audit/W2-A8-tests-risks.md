# Tests Architecture Risk Analysis (Wave 2 Agent A8)

**Context:** Classification of architectural problems in BaseGrammy test suite based on Wave 1 mapping.

**Analysis Date:** 2026-05-22

**Scope:** Business logic gaps, infrastructure gaps, flaky patterns, test-only production code, coverage gaps, missing feature tests

---

## Risk Categories

### 1. Business Logic Testing Gaps (ViewModels Not Tested)

#### Risk 1.1: TrainingViewModel Has No Unit Tests
- **Title:** TrainingViewModel logic tested only indirectly through UI tests
- **Current behavior:** TrainingViewModel (~1500 lines) is only tested via UI click tests. No direct unit tests.
- **Evidence:**
  - No file: `app/src/test/java/com/alexpo/grammermate/ui/*TrainingViewModelTest.kt`
  - UI tests reference: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:306-310`
  - Test reads state: `harness.trainingVm.uiState.value.cardSession.currentCard`
- **Why this is a problem:**
  - ViewModel contains business logic for session management, card progression, answer validation
  - UI tests are slow (Robolectric + Compose) and test multiple layers simultaneously
  - Hard to isolate bugs - is it UI logic or ViewModel logic?
  - Cannot test edge cases without full UI setup
- **Blast radius:** Any change to TrainingViewModel logic requires full UI test suite. Refactoring ViewModel internals breaks UI tests even if behavior is identical.
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  1. Unit tests for session state transitions
  2. Unit tests for card progression logic
  3. Unit tests for answer validation
  4. Unit tests for combo tracking
  5. Unit tests for pause/resume state machine

#### Risk 1.2: VerbDrillViewModel Has No Unit Tests
- **Title:** VerbDrillViewModel logic tested only through UI tests
- **Current behavior:** VerbDrillViewModel business logic is only validated via UI click paths
- **Evidence:**
  - No file: `app/src/test/java/com/alexpo/grammermate/ui/*VerbDrillViewModelTest.kt`
  - UI tests at: `app/src/test/java/com/alexpo/grammermate/ui/VerbPracticeClickUiTest.kt:38-421`
  - Test reads state: `verbVm.uiState.value.session`
- **Why this is a problem:**
  - ViewModel contains tense/group filtering, session card logic, frequency sorting
  - Cannot test filter logic without full Compose UI
  - Slow feedback loop for filter logic changes
- **Blast radius:** Changes to filtering, sorting, or session management require full UI test suite.
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  1. Unit tests for tense filtering logic
  2. Unit tests for group filtering logic
  3. Unit tests for frequency sorting
  4. Unit tests for session card management
  5. Unit tests for weak-first ordering

---

### 2. Infrastructure Testing Gaps (Real Stores Not Tested)

#### Risk 2.1: File-Based Stores Have No Tests
- **Title:** Real store implementations (file I/O, YAML parsing) are completely untested
- **Current behavior:** Only fake stores have tests. Real stores are never tested in isolation.
- **Evidence:**
  - Real stores (untouched): `MasteryStore.kt`, `LessonStore.kt`, `DrillProgressStore.kt`, `PackDailyCursorStore.kt`
  - Only test found: `app/src/test/java/com/alexpo/grammermate/data/PomodoroHistoryStoreTest.kt`
  - Fake stores tested: `app/src/test/java/com/alexpo/grammermate/test-harness/FakeVerbDrillStore.kt`
  - Fake stores tested: `app/src/test/java/com/alexpo/grammermate/test-harness/FakeMasteryStore.kt`
- **Why this is a problem:**
  - File I/O errors (permissions, disk full, corrupted files) are not tested
  - YAML parsing errors (malformed files, encoding issues) are not tested
  - Atomic file write patterns are not verified
  - Data migration between versions is not tested
  - Fake stores may diverge from real store behavior
- **Blast radius:** Data corruption, user progress loss, crashes on file I/O errors, YAML parsing failures in production.
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  1. Integration tests for MasteryStore file I/O
  2. Integration tests for VerbDrillStore YAML parsing
  3. Integration tests for LessonStore CSV parsing
  4. Error handling tests (file not found, parse errors, disk full)
  5. Atomic file write verification tests
  6. Data migration tests between versions

---

### 3. Flaky Test Patterns

#### Risk 3.1: Hard-Coded Timeouts
- **Title:** Tests use hard-coded timeouts that may fail on slow machines
- **Current behavior:** Multiple `waitUntil(timeoutMillis = X)` calls with fixed timeouts
- **Evidence:**
  - `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:280` - `timeoutMillis = 10_000`
  - `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:291` - `timeoutMillis = 5_000`
  - `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:305` - `timeoutMillis = 5_000`
  - `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:324` - `timeoutMillis = 5_000`
- **Why this is a problem:**
  - Tests may fail on CI machines under load
  - No centralized timeout configuration
  - Different timeouts for similar operations (5s vs 10s)
  - No exponential backoff or retry logic
- **Blast radius:** Intermittent test failures, false positives in CI, delayed feedback.
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  1. Centralized timeout configuration constants
  2. Timeout values based on measured operation durations
  3. Documented rationale for each timeout value

#### Risk 3.2: Sequential WaitForIdle Calls
- **Title:** Multiple `waitForIdle()` calls suggest potential race conditions
- **Current behavior:** Tests call `waitForIdle()` multiple times in sequence
- **Evidence:**
  - `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:279` - `waitForIdle()`
  - `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:304` - `waitForIdle()` then `waitUntil()`
  - `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:319` - `waitForIdle()`
  - `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:330` - `waitForIdle()`
- **Why this is a problem:**
  - Suggests async operations may complete at unpredictable times
  - Defensive programming that may hide race conditions
  - Slows down test execution unnecessarily
- **Blast radius:** Tests may pass locally but fail on CI due to timing differences.
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  1. Document why each `waitForIdle()` is necessary
  2. Consider using `waitUntil()` with specific conditions instead
  3. Measure actual async operation durations

---

### 4. Test-Only Production Code

#### Risk 4.1: VerbDrillViewModel.injectTestCards()
- **Title:** Production ViewModel contains test-only method that bypasses normal loading
- **Current behavior:** `VerbDrillViewModel` has `injectTestCards()` method for testing
- **Evidence:**
  - `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:68` - `fun injectTestCards(cards: List<VerbDrillCard>)`
  - Comment: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:66-67` - "Bypasses the normal LessonStore-based card loading for testing"
- **Why this is a problem:**
  - Test-only method compiled into production APK
  - Could be misused in production code
  - Tight coupling between tests and ViewModel internals
  - Tests don't exercise real card loading flow
- **Blast radius:** Production APK contains unused test code, tests don't verify real card loading behavior.
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  1. Tests for real card loading flow through LessonStore
  2. Remove `injectTestCards()` after real loading is tested
  3. Use test doubles (fakes) instead of test-only methods

#### Risk 4.2: DailySessionComposer.injectVerbDrillCardsForTest()
- **Title:** Production composer has test-only injection method
- **Current behavior:** `DailySessionComposer` has `injectVerbDrillCardsForTest()` method
- **Evidence:**
  - `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionComposer.kt:568` - `fun injectVerbDrillCardsForTest(packId: String, languageId: String, cards: List<VerbDrillCard>)`
  - Comment: `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionComposer.kt:565-567` - "This bypasses file loading for unit tests"
- **Why this is a problem:**
  - Same issues as Risk 4.1
  - Multiple test-only methods suggest pattern
- **Blast radius:** Same as Risk 4.1, plus indicates systemic issue.
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  1. Tests for real file loading in DailySessionComposer
  2. Remove `injectVerbDrillCardsForTest()` after real loading is tested

---

### 5. Coverage Gaps

#### Risk 5.1: Empty Feature Test Directories
- **Title:** Feature areas have no test coverage
- **Current behavior:** Multiple feature directories exist but are empty or have minimal tests
- **Evidence:**
  - Empty: `app/src/test/java/com/alexpo/grammermate/feature/daily/` (0 files)
  - Minimal: `app/src/test/java/com/alexpo/grammermate/feature/pomodoro/PomodoroHelperTest.kt` (1 file)
  - Missing: `app/src/test/java/com/alexpo/grammermate/feature/boss/` (does not exist)
  - Missing: `app/src/test/java/com/alexpo/grammermate/feature/progress/` (does not exist)
  - Missing: `app/src/test/java/com/alexpo/grammermate/feature/vocab/` (does not exist)
- **Why this is a problem:**
  - Boss battle feature logic is untested
  - Progress tracking logic is untested
  - Vocab drill feature logic is untested
  - Daily practice feature logic is untested
  - Bugs in these features will only be caught manually
- **Blast radius:** Undetected bugs in major features, regression risk when refactoring.
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  1. Unit tests for boss battle logic
  2. Unit tests for progress calculation
  3. Unit tests for vocab drill session management
  4. Unit tests for daily practice session generation

#### Risk 5.2: Empty Scenario Test Directory
- **Title:** Scenario tests directory exists but is empty
- **Current behavior:** `app/src/test/java/com/alexpo/grammermate/scenario/` directory exists with 0 files
- **Evidence:**
  - Directory exists: `app/src/test/java/com/alexpo/grammermate/scenario/`
  - File count: 0 files
- **Why this is a problem:**
  - No end-to-end scenario tests
  - No multi-step user journey tests
  - No integration tests between features
  - Indicates planned but unimplemented test strategy
- **Blast radius:** Integration bugs between features, incomplete user journey validation.
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  1. Scenario test for complete learning session (start → practice → finish)
  2. Scenario test for progress persistence across app restarts
  3. Scenario test for daily practice flow

---

### 6. Missing Feature Tests

#### Risk 6.1: Only VerbDrill Has Regression Tests
- **Title:** Other critical flows lack dedicated regression tests
- **Current behavior:** Only `VerbDrillSessionCardRegressionTest.kt` exists
- **Evidence:**
  - Only file: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt`
  - No files: `PomodoroRegressionTest.kt`, `DailyPracticeRegressionTest.kt`, `VocabDrillRegressionTest.kt`
- **Why this is a problem:**
  - Pomodoro timer state could regress
  - Daily practice card selection could regress
  - Vocab drill session management could regress
  - No safety net for refactoring these features
- **Blast radius:** Regressions in Pomodoro, Daily Practice, Vocab Drill features.
- **Risk level:** HIGH
- **Must-have tests before refactor:**
  1. Regression tests for Pomodoro timer state (pause/resume/complete)
  2. Regression tests for Daily Practice card selection
  3. Regression tests for Vocab Drill session management
  4. Regression tests for Progress tracking accuracy

---

### 7. Direct State Mutations in Tests

#### Risk 7.1: FakeTrainingStateAccess Mutates State Directly
- **Title:** Test harness allows direct state mutation, bypassing ViewModel logic
- **Current behavior:** `FakeTrainingStateAccess` has `modifyState()` that directly sets state
- **Evidence:**
  - `app/src/test/java/com/alexpo/grammermate/test-harness/FakeTrainingStateAccess.kt:20` - `_uiState.value = transform(_uiState.value)`
  - `app/src/test/java/com/alexpo/grammermate/test-harness/FakeTrainingStateAccess.kt:28` - `_uiState.value = state`
- **Why this is a problem:**
  - Tests can create invalid state combinations
  - Bypasses ViewModel validation logic
  - Tests may pass with impossible states
  - Doesn't test real state transition paths
- **Blast radius:** Tests may pass with invalid states, giving false confidence.
- **Risk level:** LOW (tests read state for assertions, which is allowed per CLAUDE.md)
- **Must-have tests before refactor:**
  1. Document valid state invariants
  2. Add validation to `modifyState()` to enforce invariants
  3. Prefer state transitions through ViewModel methods

---

### 8. Coverage Quality Issues

#### Risk 8.1: Over-Reliance on UI Tests for Business Logic
- **Title:** Business logic tested primarily through slow UI tests instead of unit tests
- **Current behavior:** Most feature logic is only tested through UI click tests
- **Evidence:**
  - UI tests: 10+ `*ClickUiTest.kt` files
  - ViewModel unit tests: 0 files
  - Feature unit tests: 1 file (`PomodoroHelperTest.kt`)
- **Why this is a problem:**
  - Slow test execution (Robolectric + Compose)
  - Hard to debug failures (is it UI or logic?)
  - Cannot test edge cases easily
  - Longer feedback loops during development
- **Blast radius:** Slow development velocity, difficult debugging, hidden bugs.
- **Risk level:** MEDIUM
- **Must-have tests before refactor:**
  1. Unit tests for all feature helpers
  2. Unit tests for all ViewModels
  3. UI tests reserved for integration scenarios

---

## Risk Summary by Category

| Category | HIGH Risks | MEDIUM Risks | LOW Risks |
|----------|-----------|--------------|-----------|
| Business Logic | 2 (ViewModels) | 0 | 0 |
| Infrastructure | 1 (File I/O) | 0 | 0 |
| Flaky Patterns | 0 | 2 (Timeouts, WaitForIdle) | 0 |
| Test-Only Code | 0 | 2 (inject methods) | 0 |
| Coverage Gaps | 2 (Features, Scenarios) | 0 | 0 |
| Missing Tests | 1 (Only VerbDrill) | 0 | 0 |
| State Mutations | 0 | 0 | 1 (FakeTrainingStateAccess) |
| Coverage Quality | 0 | 1 (UI tests for logic) | 0 |

**Total:** 6 HIGH risks, 5 MEDIUM risks, 1 LOW risk

---

## Prioritized Remediation Plan

### Phase 1: Critical Safety Net (Before Major Refactoring)
**Goal:** Prevent regressions when refactoring TrainingViewModel

1. **Add ViewModel unit tests** (addresses Risk 1.1, 1.2)
   - TrainingViewModel unit tests for session management
   - VerbDrillViewModel unit tests for filtering/sorting
   - Estimated: 2-3 days

2. **Add store integration tests** (addresses Risk 2.1)
   - MasteryStore file I/O tests
   - VerbDrillStore YAML parsing tests
   - Error handling tests
   - Estimated: 2-3 days

3. **Add missing regression tests** (addresses Risk 6.1)
   - Pomodoro regression tests
   - Daily Practice regression tests
   - Vocab Drill regression tests
   - Estimated: 2-3 days

**Total Phase 1:** 6-9 days

### Phase 2: Reduce Flakiness (Improve CI Reliability)
**Goal:** Eliminate intermittent test failures

1. **Centralize timeout configuration** (addresses Risk 3.1)
   - Create test configuration constants
   - Document timeout rationale
   - Estimated: 1 day

2. **Review and reduce WaitForIdle usage** (addresses Risk 3.2)
   - Replace with specific condition checks where possible
   - Document necessary idle waits
   - Estimated: 1 day

**Total Phase 2:** 2 days

### Phase 3: Remove Test-Only Production Code (Clean Architecture)
**Goal:** Eliminate test-only methods from production code

1. **Add real loading flow tests** (addresses Risk 4.1, 4.2)
   - Tests for LessonStore-based card loading
   - Tests for file-based card loading
   - Estimated: 2 days

2. **Remove injectTestCards methods** (addresses Risk 4.1, 4.2)
   - Remove `VerbDrillViewModel.injectTestCards()`
   - Remove `DailySessionComposer.injectVerbDrillCardsForTest()`
   - Estimated: 1 day

**Total Phase 3:** 3 days

### Phase 4: Fill Coverage Gaps (Complete Safety Net)
**Goal:** Test all major features

1. **Add feature unit tests** (addresses Risk 5.1)
   - Boss battle logic tests
   - Progress tracking tests
   - Vocab drill tests
   - Daily practice tests
   - Estimated: 3-4 days

2. **Add scenario tests** (addresses Risk 5.2)
   - End-to-end learning session scenario
   - Progress persistence scenario
   - Daily practice flow scenario
   - Estimated: 2-3 days

3. **Rebalance test pyramid** (addresses Risk 8.1)
   - Move business logic from UI tests to unit tests
   - Keep UI tests for integration scenarios
   - Estimated: 3-4 days

**Total Phase 4:** 8-11 days

---

## Must-Have Tests Before Any Refactoring

Before refactoring TrainingViewModel, VerbDrillViewModel, or store implementations:

### Minimum Viable Test Suite
1. ✅ VerbDrillSessionCardRegressionTest (already exists)
2. ❌ TrainingViewModel unit tests (session state, card progression, answer validation)
3. ❌ VerbDrillViewModel unit tests (filtering, sorting, session management)
4. ❌ MasteryStore integration tests (file I/O, YAML parsing, error handling)
5. ❌ VerbDrillStore integration tests (YAML parsing, file I/O, error handling)
6. ❌ Pomodoro regression tests (timer state, pause/resume/complete)
7. ❌ Daily Practice regression tests (card selection, session management)
8. ❌ Vocab Drill regression tests (session management, progress tracking)

**Estimated effort:** 8-12 days

---

## Evidence File References

All claims in this document are backed by file:line evidence from:
- `app/src/test/java/com/alexpo/grammermate/ui/*RegressionTest.kt`
- `app/src/test/java/com/alexpo/grammermate/ui/*ClickUiTest.kt`
- `app/src/test/java/com/alexpo/grammermate/test-harness/*.kt`
- `app/src/test/java/com/alexpo/grammermate/feature/*/`
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionComposer.kt`
- `app/src/main/java/com/alexpo/grammermate/data/*Store.kt`

See W1-A8-tests-current-map.md for complete evidence mapping.
