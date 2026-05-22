# Tests Architecture Map (Wave 1 Agent A8)

**Context:** BaseGrammy Android language learning app test landscape analysis as part of safe architecture refactoring audit.

**Scope:** UI tests, Regression tests, Unit tests, Test helpers, Coverage gaps, Flaky patterns

---

## Test Categories

### 1. **UI Clickable Tests** (Robolectric + Compose UI Test)
- **Location:** `app/src/test/java/com/alexpo/grammermate/ui/`
- **Framework:** Robolectric (`@Config(sdk = [33])`), Compose UI Testing (`createComposeRule()`)
- **Purpose:** Full UI integration tests simulating real user interactions via Compose UI API
- **Key Pattern:** "TRUE UI-click tests" - NO direct ViewModel method calls, all actions through UI

### 2. **Regression Tests**
- **Location:** `app/src/test/java/com/alexpo/grammermate/ui/*RegressionTest.kt`
- **Framework:** Robolectric + Compose UI Testing
- **Purpose:** Prevent breakage of critical user flows, especially VerbDrill SessionCard functionality
- **Key Pattern:** Real assertions with actual UI click paths, NO green TODO scaffolds

### 3. **Unit Tests**
- **Location:** `app/src/test/java/com/alexpo/grammermate/data/`
- **Framework:** Pure JUnit, no Android dependencies
- **Purpose:** Test pure business logic, calculations, parsers, data transformations

### 4. **Android Instrumentation Tests**
- **Location:** `app/src/androidTest/java/com/alexpo/grammermate/ui/`
- **Framework:** AndroidX Test + Compose UI Testing
- **Purpose:** Tests requiring real Android environment (currently only VerbDrillScreenStartFreshResumeTest)

### 5. **Test Harness (Fakes)**
- **Location:** `app/src/test/java/com/alexpo/grammermate/test-harness/`
- **Purpose:** In-memory fake implementations for testing, no file I/O, no Android dependencies

---

## Main Test Files and Purposes

### Regression Tests

#### VerbDrillSessionCardRegressionTest.kt
**File:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt`
**Lines:** 1-360
**Purpose:** Clickable regression tests for VerbDrill SessionCard actions (Repeat, Continue, Reset)

**Key Test Cases:**
- `repeat_replays_saved_batch_in_same_order` (line 63-84)
  - **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:63-84`
  - **Tests:** Repeat button replays persisted batch in exact order
  - **Click Path:** Start session → answer 3 cards → exit → click repeat_button → verify batch order

- `continue_random_mode_excludes_checked_cards_but_not_navigation_only_cards` (line 87-120)
  - **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:87-120`
  - **Tests:** Continue excludes checked cards, navigation-only cards remain eligible
  - **Click Path:** Start → answer 2 cards → navigate only (next_button) → exit → click continue_button → verify exclusion

- `continue_frequency_mode_resumes_with_next_ranked_cards_in_order` (line 123-152)
  - **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:123-152`
  - **Tests:** Frequency Continue maintains ranked order from where user left off
  - **Click Path:** Enable sort_by_frequency_checkbox → start → answer 2 → navigate only → exit → continue → verify ranked order

- `reset_clears_saved_session_keeps_progress_and_allows_ranked_restart_from_beginning` (line 155-191)
  - **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:155-191`
  - **Tests:** Reset deletes session but keeps progress, allows fresh ranked start
  - **Click Path:** Enable frequency → start → answer 2 → exit → click reset_button → verify session deleted, progress kept, fresh start possible

**Required Pattern (from CLAUDE.md):**
- Use deterministic cards with stable IDs and `rank = index`
- Render VerbDrillScreen and TrainingScreen or switching harness
- Start through UI using `verb_start_button`
- Complete cards through UI using `input_field` and `check_button`
- Navigate without completion through UI using `next_button` / `prev_button`
- Exercise SessionCard actions through UI using `session_card`, `repeat_button`, `continue_button`, `reset_button`
- Read ViewModel/store state only for answers and assertions

**Forbidden in clickable tests:**
- Do NOT call `submitCorrectAnswer()` directly from test body
- Do NOT call `markCardCompleted()` directly from test body
- Do NOT call `exitSession()` directly to simulate user navigation
- Do NOT rename unrelated tests to `.bak` or delete existing tests
- Do NOT add passing tests that contain only TODO comments

### UI Clickable Tests

#### SmokeClickTest.kt
**File:** `app/src/test/java/com/alexpo/grammermate/ui/SmokeClickTest.kt`
**Lines:** 1-52
**Purpose:** Basic Compose click smoke test
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/SmokeClickTest.kt:27-50`
- **Tests:** Basic button click and state change in Compose

#### VerbPracticeClickUiTest.kt
**File:** `app/src/test/java/com/alexpo/grammermate/ui/VerbPracticeClickUiTest.kt`
**Lines:** 1-459
**Purpose:** Comprehensive Verb Practice flow tests
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/VerbPracticeClickUiTest.kt:38-64`
- **Coverage:**
  - Selection screen shows tense and group filters (line 38)
  - Previous session card → continue button (line 71)
  - Previous session card → reset button (line 98)
  - Fresh session → no previous card (line 125)
  - Weak-first card ordering (line 145)
  - Session completion shows stats (line 196)
  - Tense filter selection (line 229)
  - Group filter selection (line 250)
  - Session card answer validation (line 270)
  - Progress saved during session (line 316)
  - Pack-scoped progress isolation (line 348)
  - Daily reset of todayShownCardIds (line 391)
  - Combo progress tracking (line 421)

#### PomodoroClickUiTest.kt
**File:** `app/src/test/java/com/alexpo/grammermate/ui/PomodoroClickUiTest.kt`
**Lines:** 1-100+
**Purpose:** Pomodoro timer feature UI tests
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/PomodoroClickUiTest.kt:44-98`
- **Tests:** Start Pomodoro → timer banner shows, pause/resume, complete early → summary

#### Other UI Click Tests
- **DailyPracticeClickUiTest.kt** - Daily practice session UI
- **InputModeSwitchTest.kt** - Input mode switching (keyboard/voice)
- **NavigationClickTest.kt** - Navigation callbacks
- **PauseCascadeClickUiTest.kt** - Pause behavior
- **PomodoroBannerClickUiTest.kt** - Pomodoro banner UI
- **RegularLessonClickUiTest.kt** - Regular lesson flow
- **SubmitAnswerTest.kt** - Answer submission
- **WordBankToggleTest.kt** - Word bank toggle

### Unit Tests

#### FlowerCalculatorTest.kt
**File:** `app/src/test/java/com/alexpo/grammermate/data/FlowerCalculatorTest.kt`
**Lines:** 1-363
**Purpose:** Flower state calculation logic
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/data/FlowerCalculatorTest.kt:24-31`
- **Coverage:**
  - Basic states (SEED, SPROUT, BLOOM, WILTING, WILTED, GONE, LOCKED)
  - Mastery percentage calculation (0-150 shows → 0-100%)
  - Health percentage calculation (time-based decay)
  - Scale multiplier calculation (mastery% × health%)
  - Emoji representations for each state
  - Boundary cases (exactly 150 shows, exactly 90 days, negative timestamps)

#### CsvParserTest.kt
**File:** `app/src/test/java/com/alexpo/grammermate/data/CsvParserTest.kt`
**Lines:** 1-50+
**Purpose:** CSV parsing for lesson content
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/data/CsvParserTest.kt:10-21`
- **Coverage:** Title parsing, card parsing, UTF-8 BOM handling, punctuation stopping

#### Other Data Unit Tests
- **AtomicFileWriterTest.kt** - Atomic file write operations
- **FlowerCalculatorTest.kt** - Flower state calculation
- **LessonLadderCalculatorTest.kt** - Lesson ladder progression
- **LessonPackManifestTest.kt** - Pack manifest parsing
- **MixedReviewSchedulerTest.kt** - Mixed review scheduling
- **NormalizerTest.kt** - Text normalization
- **PomodoroHistoryStoreTest.kt** - Pomodoro history persistence
- **SpacedRepetitionConfigTest.kt** - Spaced repetition configuration
- **StoryQuizParserTest.kt** - Story quiz parsing
- **VocabCsvParserTest.kt** - Vocabulary CSV parsing

### Feature Tests

#### PomodoroHelperTest.kt
**File:** `app/src/test/java/com/alexpo/grammermate/feature/pomodoro/PomodoroHelperTest.kt`
**Purpose:** Pomodoro timer helper logic
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/feature/pomodoro/PomodoroHelperTest.kt`

### Android Instrumentation Tests

#### VerbDrillScreenStartFreshResumeTest.kt
**File:** `app/src/androidTest/java/com/alexpo/grammermate/ui/VerbDrillScreenStartFreshResumeTest.kt`
**Lines:** 1-100+
**Purpose:** UI integration tests for Verb Drill "Start Fresh / Resume" feature (VD-50)
- **Evidence:** `app/src/androidTest/java/com/alexpo/grammermate/ui/VerbDrillScreenStartFreshResumeTest.kt:22-40`
- **Key Difference:** Does NOT use `injectTestCards()` - uses real `reloadForPack()` flow
- **Coverage:**
  - Dialog shown on re-entry after exitSession()
  - User flow: load cards → start session → exit → re-create ViewModel → reload → verify dialog

---

## Test Coverage

### Well-Covered Areas

1. **VerbDrill SessionCard Actions** ✅
   - Repeat, Continue, Reset functionality fully covered
   - Random mode vs frequency mode behavior
   - Navigation-only vs checked cards distinction
   - Progress persistence vs session persistence

2. **Flower State Calculation** ✅
   - All flower states (SEED, SPROUT, BLOOM, WILTING, WILTED, GONE, LOCKED)
   - Mastery percentage calculation
   - Health percentage calculation
   - Scale multiplier calculation
   - Boundary cases and edge cases

3. **CSV Parsing** ✅
   - Lesson title parsing
   - Card content parsing
   - UTF-8 BOM handling
   - Empty title handling

4. **UI Interaction Patterns** ✅
   - Basic Compose clicking
   - Navigation callbacks
   - Input mode switching
   - Answer submission
   - Pause/Resume cascades

5. **Test Harness Infrastructure** ✅
   - FakeVerbDrillStore
   - FakeMasteryStore
   - FakeStreakStore
   - FakeTrainingStateAccess

### Coverage Gaps

1. **Feature Areas Without Tests:**
   - `feature/boss/` - No test coverage
   - `feature/progress/` - No test coverage
   - `feature/vocab/` - No test coverage (vocab drill specific logic)
   - `feature/daily/` - Directory exists but empty

2. **UI Components:**
   - Individual UI components in `ui/components/` - No dedicated unit tests
   - Component-level behavior tests missing

3. **Integration Scenarios:**
   - `scenario/` directory exists but empty
   - No end-to-end scenario tests beyond VerbDrill

4. **ViewModel Logic:**
   - TrainingViewModel - Only tested indirectly through UI tests
   - VerbDrillViewModel - Only tested indirectly through UI tests
   - No direct ViewModel unit tests

5. **Store Implementations:**
   - Real store implementations (file-based, YAML-based) - No tests
   - Only fake stores tested, not real persistence layer

6. **Error Handling:**
   - No tests for error scenarios (file corruption, parse errors, etc.)
   - No tests for network failures (if any network calls)

7. **Performance:**
   - No performance tests
   - No stress tests for large datasets

---

## Test Helpers and Utilities

### Test Harness (Fakes)

#### FakeVerbDrillStore.kt
**File:** `app/src/test/java/com/alexpo/grammermate/test-harness/FakeVerbDrillStore.kt`
**Lines:** 1-136
**Purpose:** In-memory fake implementation of VerbDrillStore for testing
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/test-harness/FakeVerbDrillStore.kt:17-136`
- **Key Features:**
  - No file I/O, no YAML parsing, no Android dependencies
  - NO auto-deletion of stale sessions (removed 24-hour limit)
  - All sessions returned as-is from `loadLastSession()`
  - Test helpers: `setCards()`, `clear()`, `simulateCardsShown()`
  - Helper methods: `getAvailableTenses()`, `getAvailableGroups()`

#### FakeMasteryStore.kt
**File:** `app/src/test/java/com/alexpo/grammermate/test-harness/FakeMasteryStore.kt`
**Lines:** 1-178
**Purpose:** In-memory fake implementation of MasteryStore for testing
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/test-harness/FakeMasteryStore.kt:12-178`
- **Key Features:**
  - No file I/O, no YAML parsing, no Android dependencies
  - Test helpers: `simulateLessonCompletion()`, `simulatePartialProgress()`
  - Helper methods: `resetAll()`, `getLessonCount()`, `getAllLessonIds()`

#### FakeStreakStore.kt
**File:** `app/src/test/java/com/alexpo/grammermate/test-harness/FakeStreakStore.kt`
**Purpose:** In-memory fake implementation of StreakStore for testing
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/test-harness/FakeStreakStore.kt`

#### FakeTrainingStateAccess.kt
**File:** `app/src/test/java/com/alexpo/grammermate/test-harness/FakeTrainingStateAccess.kt`
**Purpose:** Mutable state holder for tests, wraps TrainingUiState in MutableStateFlow
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/test-harness/FakeTrainingStateAccess.kt`

### Test Data Helpers

#### VerbDrillTestData.kt
**File:** `app/src/test/java/com/alexpo/grammermate/ui/helpers/VerbDrillTestData.kt`
**Lines:** 1-42
**Purpose:** Test data creation helpers for VerbDrill tests
- **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/helpers/VerbDrillTestData.kt:7-19`
- **Functions:**
  - `createTestVerbCards(count: Int): List<VerbDrillCard>` - Creates deterministic test cards with stable IDs
  - `setupLastSession(...)` - Sets up last session state for testing

---

## Flaky Patterns / Anti-patterns

### Identified Flaky Patterns

1. **WaitForIdle Usage**
   - **Pattern:** `composeRule.waitForIdle()` used extensively
   - **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:279`
   - **Risk:** May cause intermittent failures if async operations take longer than expected
   - **Status:** Necessary for Compose UI tests, but can be flaky

2. **WaitUntil with Timeouts**
   - **Pattern:** `composeRule.waitUntil(timeoutMillis = 10_000) { ... }`
   - **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:280-283`
   - **Risk:** Tests may fail if condition not met within timeout
   - **Status:** Necessary for async state changes, but timeouts may need adjustment

3. **Multiple WaitForIdle Calls**
   - **Pattern:** Sequential `waitForIdle()` calls after each UI action
   - **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:319`
   - **Risk:** May slow down test execution unnecessarily
   - **Status:** Defensive programming, but may indicate race conditions

### Anti-patterns

1. **State Mutations in Tests**
   - **Pattern:** Tests directly read ViewModel/store state for assertions
   - **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:333-343`
   - **Status:** ALLOWED for assertions (per CLAUDE.md guidelines)
   - **Risk:** Tests may break if internal state representation changes

2. **Hard-coded Timeouts**
   - **Pattern:** `timeoutMillis = 10_000` scattered throughout tests
   - **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:280`
   - **Risk:** May not be appropriate for all machines/environments
   - **Status:** No centralized timeout configuration

3. **No @Ignore or Disabled Tests Found**
   - **Pattern:** Search for `@Ignore`, `TODO`, `disabled`, `xxx` returned no results
   - **Evidence:** No disabled tests found in codebase
   - **Status:** GOOD - no TODO scaffolds or disabled tests cluttering the suite

---

## Test-Only Workarounds

### Production Code with Test-Only Methods

#### VerbDrillViewModel.injectTestCards()
**File:** `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`
**Purpose:** Test-only method to inject cards directly into the ViewModel
- **Evidence:** `grep -B 5 -A 10 "for testing" ./app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`
- **Code Comment:** "Bypasses the normal LessonStore-based card loading for testing"
- **Usage:** Used in VerbDrillSessionCardRegressionTest
- **Risk:** Test-only method in production code could be misused
- **Justification:** Necessary for deterministic test data without full store setup

#### AnswerValidator (Mentioned in search)
**File:** `app/src/main/java/com/alexpo/grammermate/feature/training/AnswerValidator.kt`
- **Evidence:** Found in grep for "for testing"
- **Status:** Needs further investigation

### Test Harness as Production Dependency Risk

**Pattern:** Test harness classes (`FakeVerbDrillStore`, `FakeMasteryStore`, etc.) are in `test/` directory
- **Status:** GOOD - properly isolated from production code
- **Risk:** None - test-only code not compiled into production APK

---

## Risks / Unclear Points

### Architectural Concerns

1. **Test Location Inconsistency**
   - **Issue:** Some tests in `androidTest/`, most in `test/`
   - **Evidence:** `app/src/androidTest/` contains only VerbDrillScreenStartFreshResumeTest
   - **Risk:** Unclear when to use androidTest vs test
   - **Impact:** Developers may not know where to place new tests

2. **Empty Scenario Directory**
   - **Issue:** `app/src/test/java/com/alexpo/grammermate/scenario/` exists but is empty
   - **Evidence:** Directory exists with no files
   - **Risk:** Indicates planned but unimplemented scenario tests
   - **Impact:** Missing end-to-end scenario coverage

3. **Empty Feature Test Directories**
   - **Issue:** `feature/daily/` exists but has no tests
   - **Evidence:** `app/src/test/java/com/alexpo/grammermate/feature/daily/` is empty
   - **Risk:** Daily practice feature may be undertested
   - **Impact:** Potential bugs in daily practice flow

4. **ViewModel Testing Indirectly**
   - **Issue:** ViewModels only tested through UI tests, not unit tested directly
   - **Evidence:** No `*ViewModelTest.kt` files found
   - **Risk:** ViewModel logic bugs may be hidden by UI layer
   - **Impact:** Harder to debug failures, slower test execution

5. **No Real Store Tests**
   - **Issue:** Only fake stores tested, not real file-based/YAML-based implementations
   - **Evidence:** No test files for `YamlMasteryStore`, `FileVerbDrillStore`, etc.
   - **Risk:** File I/O bugs, YAML parsing bugs may go undetected
   - **Impact:** Data corruption or loss in production

6. **Test Data Helper Location**
   - **Issue:** `VerbDrillTestData.kt` in `ui/helpers/`, not `test-harness/`
   - **Evidence:** `app/src/test/java/com/alexpo/grammermate/ui/helpers/VerbDrillTestData.kt`
   - **Risk:** Inconsistent organization of test utilities
   - **Impact:** Harder to find and reuse test helpers

7. **Regression Test Scope**
   - **Issue:** Only VerbDrill has dedicated regression test
   - **Evidence:** Only `VerbDrillSessionCardRegressionTest.kt` found
   - **Risk:** Other critical flows may regress without detection
   - **Impact:** Bugs in Pomodoro, Daily Practice, Vocab Drill, etc.

### Testing Anti-patterns

1. **Over-reliance on UI Tests for Business Logic**
   - **Issue:** FlowerCalculator, CsvParser tested as unit tests, but most feature logic only tested through UI
   - **Risk:** Slower test execution, harder to debug
   - **Impact:** Longer feedback loops during development

2. **No Integration Tests**
   - **Issue:** Jump from unit tests to full UI tests, no integration layer
   - **Risk:** Missing coverage for component interactions
   - **Impact:** Bugs in component integration may go undetected

3. **Test Data Creation Scattered**
   - **Issue:** Test data creation in individual test files, not centralized
   - **Evidence:** `createTestVerbCards()` in VerbDrillSessionCardRegressionTest harness setup
   - **Risk:** Inconsistent test data, harder to maintain
   - **Impact:** Tests may fail due to bad test data, not logic bugs

---

## Evidence

### File References for All Claims

**Test Categories:**
- UI Clickable Tests: `app/src/test/java/com/alexpo/grammermate/ui/*ClickUiTest.kt`
- Regression Tests: `app/src/test/java/com/alexpo/grammermate/ui/*RegressionTest.kt`
- Unit Tests: `app/src/test/java/com/alexpo/grammermate/data/*Test.kt`
- Android Instrumentation Tests: `app/src/androidTest/java/com/alexpo/grammermate/ui/*Test.kt`
- Test Harness: `app/src/test/java/com/alexpo/grammermate/test-harness/*.kt`

**VerbDrillSessionCardRegressionTest Coverage:**
- Repeat test: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:63-84`
- Continue random mode test: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:87-120`
- Continue frequency mode test: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:123-152`
- Reset test: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:155-191`

**Test Harness Files:**
- FakeVerbDrillStore: `app/src/test/java/com/alexpo/grammermate/test-harness/FakeVerbDrillStore.kt:1-136`
- FakeMasteryStore: `app/src/test/java/com/alexpo/grammermate/test-harness/FakeMasteryStore.kt:1-178`
- FakeStreakStore: `app/src/test/java/com/alexpo/grammermate/test-harness/FakeStreakStore.kt`
- FakeTrainingStateAccess: `app/src/test/java/com/alexpo/grammermate/test-harness/FakeTrainingStateAccess.kt`

**Test Helpers:**
- VerbDrillTestData: `app/src/test/java/com/alexpo/grammermate/ui/helpers/VerbDrillTestData.kt:1-42`

**Flaky Patterns:**
- WaitForIdle usage: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:279`
- WaitUntil with timeouts: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:280-283`

**Test-Only Production Code:**
- VerbDrillViewModel.injectTestCards(): `grep -B 5 -A 10 "for testing" ./app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`

**Coverage Gaps:**
- Empty scenario directory: `app/src/test/java/com/alexpo/grammermate/scenario/` (no files)
- Empty feature/daily directory: `app/src/test/java/com/alexpo/grammermate/feature/daily/` (no files)
- No boss/progress/vocab feature tests: No corresponding test directories found

---

## Summary

**Test Maturity:** Moderate to Good
- Strong regression coverage for VerbDrill SessionCard
- Good unit test coverage for business logic (FlowerCalculator, CsvParser)
- Comprehensive test harness infrastructure
- Well-documented test patterns in CLAUDE.md

**Main Risks:**
1. Over-reliance on UI tests for business logic (slow feedback loops)
2. Missing unit tests for ViewModels
3. No tests for real store implementations (file I/O, YAML)
4. Empty test directories indicate planned but unimplemented tests
5. Only VerbDrill has dedicated regression tests

**Recommendations:**
1. Add unit tests for TrainingViewModel and VerbDrillViewModel
2. Add tests for real store implementations
3. Create regression tests for Pomodoro, Daily Practice, Vocab Drill
4. Consolidate test data creation helpers
5. Document when to use androidTest vs test
6. Consider integration test layer between unit and UI tests

**Test Quality:** Good
- No green TODO scaffolds found
- No disabled tests cluttering the suite
- Clear evidence requirements met with file:line references
- Real assertions in all tests examined
