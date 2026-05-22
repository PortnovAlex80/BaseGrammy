# Wave 2 Agent A1: UI/Nav Architecture Risk Classification

**Analysis Date:** 2026-05-22
**Scope:** UI and Navigation Layer - Architectural Risk Classification
**Method:** Risk analysis based on W1-A1 current architecture map
**Agent:** UI/Nav Architecture Risk Analyst

---

## Executive Summary

Based on the Wave 1 architecture mapping, **9 critical architectural risks** have been identified in the UI/Nav layer. The most severe risks are:

1. **Business logic embedded in Compose callbacks** (HIGH risk)
2. **Token-based navigation triggers** (HIGH risk) 
3. **Complex dialog state management in UI layer** (MEDIUM risk)
4. **Direct ViewModel coupling from UI callbacks** (MEDIUM risk)

These risks create a fragile architecture where business rules are scattered across UI code, making refactoring dangerous and testing difficult.

---

## Risk Categories Analysis

### 1. Business Logic Inside Compose/UI

#### Risk 1.1: Verb Drill Session Coordination in UI Callback

**Title:** Verb drill answer acceptance logic embedded in UI callback

**Current behavior:**
```kotlin
// Location: GrammarMateApp.kt:446-456
onSubmit = {
    val beforeCard = state.cardSession.currentCard
    val result = vm.submitAnswer()
    if (
        state.cardSession.returnTo == Routes.VERB_DRILL &&
        beforeCard is VerbDrillCard &&
        result.accepted
    ) {
        verbDrillVm.submitCorrectAnswer()  // Business rule in UI
    }
    result
}
```

**Why this is a problem:**
- Business rule (VerbDrill cards must be marked correct when accepted) is embedded in UI layer
- Cannot test this rule without UI composition
- Logic is scattered across two ViewModels from UI code
- Changing the rule requires understanding callback chain depth

**Blast radius:**
- **Breaking:** Any change to answer acceptance flow requires modifying UI code
- **Affected:** VerbDrillViewModel, TrainingViewModel, TrainingScreen
- **Test impact:** Cannot unit test this coordination without Compose testing

**Evidence:**
- `GrammarMateApp.kt:446-456` - Verb drill answer coordination
- `GrammarMateApp.kt:459-465` - Verb drill card completion tracking
- `GrammarMateApp.kt:469-475` - Verb drill previous card tracking

**Proposed direction:**
Move session coordination to a use case or ViewModel method:
```kotlin
// In TrainingViewModel
fun submitAnswerForVerbDrill(): AnswerResult {
    val result = submitAnswer()
    if (result.accepted && isVerbDrillSession()) {
        verbDrillCoordinator.submitCorrectAnswer()
    }
    return result
}
```

**Risk level:** HIGH

**Must-have tests before refactor:**
1. Test that VerbDrill cards are marked correct when answer accepted
2. Test that regular lesson cards don't trigger VerbDrillViewModel
3. Test that navigation state (returnTo) determines coordination behavior
4. Regression test for VerbDrillSessionCard flow

---

#### Risk 1.2: Daily Practice Block Completion in UI Navigation Trigger

**Title:** Daily practice progression logic embedded in token-based navigation

**Current behavior:**
```kotlin
// Location: GrammarMateApp.kt:903-923
if (currentRoute == Routes.TRAINING && 
    state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
    lastFinishedToken.value = state.cardSession.subLessonFinishedToken
    vm.onTrainingSessionCompleted()
    val hasCards = state.cardSession.currentCard != null
    if (!state.pomodoro.isComplete && hasCards) {
        val returnTo = state.cardSession.returnTo
        if (returnTo == Routes.DAILY_PRACTICE) {
            vm.daily.onBlockComplete()  // Business logic in UI
        }
        onNavigate(returnTo)
    }
}
```

**Why this is a problem:**
- Business rule (Daily practice block completion) triggered by UI state comparison
- Navigation and business logic are tightly coupled
- Cannot test block completion logic without UI navigation
- Token comparison is fragile - easy to miss edge cases

**Blast radius:**
- **Breaking:** Changing daily practice flow requires modifying LaunchedEffect logic
- **Affected:** DailyPracticeCoordinator, Pomodoro system, navigation flow
- **Test impact:** Requires UI test to verify block completion behavior

**Evidence:**
- `GrammarMateApp.kt:903-923` - Token-based daily block completion
- `GrammarMateApp.kt:777` - DailyPracticeScreen also calls onBlockComplete()
- `GrammarMateApp.kt:524` - Another onBlockComplete() call in navigation handler

**Proposed direction:**
Create explicit session completion events:
```kotlin
// In TrainingViewModel
sealed class SessionEvent {
    data class SubLessonComplete(val returnTo: String) : SessionEvent()
    data class BossComplete(val lessonId: String) : SessionEvent()
}

val sessionEvents: Flow<SessionEvent> = ...

// In UI
LaunchedEffect(sessionEvents) {
    sessionEvents.collect { event ->
        when (event) {
            is SessionEvent.SubLessonComplete -> {
                vm.onTrainingSessionCompleted()
                if (event.returnTo == Routes.DAILY_PRACTICE) {
                    vm.daily.onBlockComplete()
                }
                onNavigate(event.returnTo)
            }
        }
    }
}
```

**Risk level:** HIGH

**Must-have tests before refactor:**
1. Test daily practice block completion increments block counter
2. Test that sub-lesson completion triggers correct navigation
3. Test that Pomodoro state affects navigation behavior
4. Test that token reset doesn't trigger spurious navigation
5. Regression test for DailyPracticeSessionCard flow

---

### 2. Business Logic Inside Android ViewModel

#### Risk 2.1: God ViewModel with 15+ Feature Instances

**Title:** TrainingViewModel violates Single Responsibility Principle

**Current behavior:**
```kotlin
// Location: TrainingViewModel.kt:76-150
class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val answerValidator = AnswerValidator()
    private val vocabSprintRunner = VocabSprintRunner(...)
    private val streakManager = StreakManager(...)
    private val bossBattleRunner = BossBattleRunner()
    private val progressTracker = ProgressTracker(...)
    // ... 15+ more feature instances
}
```

**Why this is a problem:**
- Single ViewModel coordinates entire app's business logic
- Hard to test individual features in isolation
- Changes to one feature risk breaking others
- Cannot easily reuse feature logic outside of TrainingViewModel

**Blast radius:**
- **Breaking:** Any change to TrainingViewModel risks breaking all features
- **Affected:** Every screen in the app (all observe TrainingViewModel)
- **Test impact:** Unit tests require mocking entire TrainingViewModel

**Evidence:**
- `TrainingViewModel.kt:76-150` - Feature instance declarations
- `GrammarMateApp.kt:115` - Single ViewModel instance for entire app
- All screens pass `vm: TrainingViewModel` parameter

**Proposed direction:**
Decompose into domain-specific ViewModels:
```kotlin
// Domain-specific ViewModels
class TrainingViewModel : AndroidViewModel(app) {
    // Core training logic only
}

class DailyPracticeViewModel : AndroidViewModel(app) {
    // Daily practice coordination
}

class VerbDrillViewModel : AndroidViewModel(app) {
    // Verb drill session management
}

// Use case coordination
class SessionCoordinator(
    private val trainingVm: TrainingViewModel,
    private val dailyVm: DailyPracticeViewModel,
    private val verbDrillVm: VerbDrillViewModel
)
```

**Risk level:** HIGH

**Must-have tests before refactor:**
1. Feature integration tests for each feature
2. Cross-feature interaction tests (e.g., Pomodoro + Daily)
3. State persistence tests for each ViewModel
4. Navigation tests that depend on multiple ViewModels

---

### 3. Infrastructure/File IO Decisions Embedded in UI Layer

#### Risk 3.1: Session Persistence Triggered by Navigation

**Title:** File I/O persistence coupled to navigation callbacks

**Current behavior:**
```kotlin
// Location: GrammarMateApp.kt:483-486
returnTo == Routes.VERB_DRILL && hasActiveCard -> {
    verbDrillVm.persistSessionState()  // File I/O in navigation callback
    vm.exitVerbDrillSession()
    onNavigate(Routes.VERB_DRILL)
}
```

**Why this is a problem:**
- File I/O operations triggered by UI navigation
- Cannot test persistence without navigation
- Persistence logic scattered across multiple navigation branches
- Risk of losing data if navigation is interrupted

**Blast radius:**
- **Breaking:** Changing persistence logic requires finding all navigation triggers
- **Affected:** VerbDrillViewModel, navigation system, file I/O
- **Test impact:** Requires integration test for persistence

**Evidence:**
- `GrammarMateApp.kt:484` - persistSessionState() on exit
- `GrammarMateApp.kt:490` - persistSessionState() on completion
- `GrammarMateApp.kt:507` - persistSessionState() on "more cards"
- `GrammarMateApp.kt:536` - persistSessionState() on session done

**Proposed direction:**
Centralize persistence in ViewModel lifecycle:
```kotlin
// In VerbDrillViewModel
override fun onCleared() {
    // Auto-persist on ViewModel cleanup
    if (session.value.isActive) {
        persistSessionState()
    }
}

// In UI - remove all persistSessionState() calls
onShowExitDialog = {
    vm.exitVerbDrillSession()  // ViewModel handles persistence
    onNavigate(Routes.VERB_DRILL)
}
```

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Test that session is persisted on ViewModel cleanup
2. Test that session state survives process death
3. Test that persistence doesn't corrupt data
4. Regression test for VerbDrill session persistence

---

### 4. Duplicated Rules Across UI Screens

#### Risk 4.1: Daily Practice Block Completion Called from Multiple Locations

**Title:** Block completion logic duplicated across UI

**Current behavior:**
```kotlin
// Location 1: GrammarMateApp.kt:524 (in token-based navigation)
if (returnTo == Routes.DAILY_PRACTICE) {
    vm.daily.onBlockComplete()
}

// Location 2: GrammarMateApp.kt:777 (in DailyPracticeScreen)
onComplete = remember { {
    val nextBlock = vm.daily.onBlockComplete()
    if (nextBlock == null) {
        // All blocks done
    }
} }

// Location 3: GrammarMateApp.kt:913 (another token trigger)
if (returnTo == Routes.DAILY_PRACTICE) {
    vm.daily.onBlockComplete()
}
```

**Why this is a problem:**
- Same business rule called from 3 different UI locations
- Risk of inconsistent behavior if one location is missed
- Hard to track when block completion should occur
- Cannot guarantee execution order

**Blast radius:**
- **Breaking:** Adding new block completion trigger requires finding all existing calls
- **Affected:** Daily practice flow, progress tracking, session state
- **Test impact:** Must test all call sites for consistency

**Evidence:**
- `GrammarMateApp.kt:524, 777, 913` - Three locations calling onBlockComplete()
- `DailyPracticeScreen.kt` - Also has completion logic

**Proposed direction:**
Centralize block completion trigger:
```kotlin
// In DailyPracticeCoordinator
fun onTrainingSessionComplete(returnTo: String) {
    if (returnTo == Routes.DAILY_PRACTICE) {
        onBlockComplete()
    }
}

// In UI - single call site
LaunchedEffect(sessionEvents) {
    sessionEvents.collect { event ->
        when (event) {
            is SessionEvent.SubLessonComplete -> {
                vm.daily.onTrainingSessionComplete(event.returnTo)
                onNavigate(event.returnTo)
            }
        }
    }
}
```

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Test that block completion is called exactly once per session
2. Test that all call sites trigger the same behavior
3. Test that block completion advances to correct next block
4. Regression test for daily practice progression

---

### 5. Direct Store Writes Scattered Across UI

#### Risk 5.1: Store Save Operations Called from Multiple Layers

**Title:** Persistence logic not centralized

**Current behavior:**
```kotlin
// Location 1: TrainingViewModel.kt:316 (settings)
pomodoroSettingsStore.save(durationMinutes)

// Location 2: TrainingViewModel.kt:1166 (config)
configStore.save(configStore.load().copy(ruTextScale = scale))

// Location 3: VerbDrillViewModel.kt:511 (session)
verbDrillStore.saveLastSession(lastSessionState)

// Location 4: TrainingViewModel.kt:1536 (mastery)
wordMasteryStore.saveAll(emptyMap())
```

**Why this is a problem:**
- Store writes scattered across ViewModels
- No centralized persistence orchestration
- Risk of inconsistent save operations
- Difficult to coordinate batch saves

**Blast radius:**
- **Breaking:** Adding new persistence requirement requires finding all save calls
- **Affected:** All data stores, state persistence
- **Test impact:** Must verify each save operation independently

**Evidence:**
- `TrainingViewModel.kt:316, 1166, 1536` - Multiple save operations
- `VerbDrillViewModel.kt:511, 719, 787` - Multiple saveLastSession calls
- No centralized persistence coordinator

**Proposed direction:**
Create persistence coordinator:
```kotlin
class PersistenceCoordinator(
    private val stores: List<DataStore<*>>
) {
    suspend fun saveAll() {
        stores.forEach { it.save() }
    }
    
    suspend fun saveWithTransaction(block: () -> Unit) {
        // Atomic save across multiple stores
    }
}
```

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Test that all required data is persisted on app background
2. Test that save operations don't corrupt data
3. Test that failed saves don't leave inconsistent state
4. Regression test for state persistence after process death

---

### 6. Hidden Coupling Through Callbacks/Events

#### Risk 6.1: Deep Callback Chains in TrainingScreen

**Title:** 8+ callback layers create implicit dependencies

**Current behavior:**
```kotlin
// Location: GrammarMateApp.kt:693-731
TrainingScreenContent(
    state, vm,
    onSubmit = { ... },      // Layer 1: UI callback
    onNext = { ... },        // Layer 2: Navigation callback
    onPrev = { ... },        // Layer 3: State mutation callback
    onShowExitDialog = { ... },  // Layer 4: Dialog callback
    onShowSettings = { ... },    // Layer 5: Settings callback
    onTtsSpeak = onTtsSpeak,     // Layer 6: Audio callback
    onVerbDrillMore = { ... },   // Layer 7: VerbDrill callback
    onSessionDone = { ... },     // Layer 8: Session callback
    getTenseInfo = { ... }       // Layer 9: Info callback
)
```

**Why this is a problem:**
- Callbacks create implicit dependencies between UI and business logic
- Hard to track data flow through callback chains
- Risk of callback hell (nested callbacks)
- Difficult to test callback interactions

**Blast radius:**
- **Breaking:** Adding new callback requires updating all callback chains
- **Affected:** TrainingScreen, TrainingScreenContent, GrammarMateApp
- **Test impact:** Must test callback execution order and side effects

**Evidence:**
- `GrammarMateApp.kt:444-531` - 8+ callback definitions
- `GrammarMateApp.kt:693-731` - 8+ callback parameters
- Callbacks wrapped in `remember {}` for multiple layers

**Proposed direction:**
Use sealed events or state reducer pattern:
```kotlin
// Define events
sealed class TrainingEvent {
    data class SubmitAnswer(val answer: String) : TrainingEvent()
    data class NavigateNext(val cardCompleted: Boolean) : TrainingEvent()
    data class ShowExitDialog(val returnTo: String) : TrainingEvent()
    // ... etc
}

// Single event handler
fun onEvent(event: TrainingEvent) {
    when (event) {
        is TrainingEvent.SubmitAnswer -> submitAnswer(event.answer)
        is TrainingEvent.NavigateNext -> navigateNext(event.cardCompleted)
        // ... etc
    }
}
```

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Test that each event triggers correct business logic
2. Test that event execution order is correct
3. Test that events don't create infinite loops
4. Regression test for all TrainingScreen interactions

---

### 7. Ambiguous Ownership of State

#### Risk 7.1: Dialog State Split Between UI and ViewModel

**Title:** Dialog state management responsibility unclear

**Current behavior:**
```kotlin
// Location 1: GrammarMateApp.kt:101-110 (UI-owned state)
private data class DialogState(
    val showSettings: Boolean = false,
    val showExitDialog: Boolean = false,
    val showWelcomeDialog: Boolean = false,
    val showDailyResumeDialog: Boolean = false,
    val showTtsDownloadDialog: Boolean = false,
    val showProfileStats: Boolean = false,
    val pendingDailyLevel: Int = 0,
    val isLoadingDaily: Boolean = false
)

// Location 2: GrammarMateApp.kt:135 (UI mutation)
var dialogs by remember { mutableStateOf(DialogState()) }

// Location 3: VerbDrillViewModel.kt:737 (ViewModel-owned dialog)
_uiState.update { it.copy(showStartFreshResumeDialog = false) }
```

**Why this is a problem:**
- Some dialogs owned by UI layer (DialogState)
- Some dialogs owned by ViewModel (VerbDrillViewModel.uiState)
- Unclear which layer should own dialog state
- Inconsistent state mutation patterns

**Blast radius:**
- **Breaking:** Moving dialog ownership requires updating all dialog logic
- **Affected:** All screens, dialog state management
- **Test impact:** Must test dialog state in both UI and ViewModel tests

**Evidence:**
- `GrammarMateApp.kt:101-110` - UI-owned DialogState
- `VerbDrillViewModel.kt:737, 761, 831` - ViewModel-owned dialog state
- Inconsistent mutation patterns (UI: `dialogs.copy()`, ViewModel: `_uiState.update()`)

**Proposed direction:**
Centralize dialog state ownership:
```kotlin
// Option 1: All dialogs in ViewModel
data class DialogUiState(
    val showSettings: Boolean = false,
    val showExitDialog: Boolean = false,
    // ... etc
)

data class TrainingUiState(
    // ... existing state
    val dialogs: DialogUiState = DialogUiState()
)

// Option 2: Use state machine for complex dialog flows
sealed class DialogState {
    object Hidden : DialogState()
    data class Settings(val previousRoute: String) : DialogState()
    data class DailyResume(val level: Int) : DialogState()
    // ... etc
}
```

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Test that dialog state is consistent across screen rotations
2. Test that dialog state survives process death
3. Test that dialog transitions are correct
4. Regression test for all dialog flows

---

### 8. Test-Only Workarounds Leaking into Production

#### Risk 8.1: VerbDrillViewModel Dual Scope for Test Access

**Title:** ViewModel scoped differently for test convenience

**Current behavior:**
```kotlin
// Location: GrammarMateApp.kt:142-150 (outer scope)
val verbDrillVm = viewModel<VerbDrillViewModel>()

// Location: GrammarMateApp.kt:435-443 (TRAINING route scope)
composable(Routes.TRAINING) {
    val verbTenseInfoVm = viewModel<VerbDrillViewModel>()
    // Used for tense info bottom sheet
}
```

**Why this is a problem:**
- Same ViewModel instantiated at two scopes for test convenience
- Unclear which instance should be used for what purpose
- Risk of state synchronization issues
- Test requirements influencing production architecture

**Blast radius:**
- **Breaking:** Changing scope breaks tests
- **Affected:** VerbDrill tests, tense info bottom sheet
- **Test impact:** Tests may rely on specific scoping behavior

**Evidence:**
- `GrammarMateApp.kt:142-150` - Outer scope VerbDrillViewModel
- `GrammarMateApp.kt:435-443` - TRAINING scope VerbDrillViewModel
- Comment in code: "Hoisted to outer scope so TRAINING composable can call persistSessionState()"

**Proposed direction:**
Use single instance with proper state sharing:
```kotlin
// Single instance at app level
val verbDrillVm = viewModel<VerbDrillViewModel>()

// Pass to TRAINING route as parameter
composable(Routes.TRAINING) {
    TrainingScreenContent(
        // ... other params
        verbDrillVm = verbDrillVm,
        getTenseInfo = verbTenseInfoVm::getTenseInfo
    )
}
```

**Risk level:** LOW

**Must-have tests before refactor:**
1. Test that VerbDrill state is consistent across scopes
2. Test that tense info bottom sheet works correctly
3. Test that session persistence works with single instance
4. Regression test for VerbDrillSessionCard flow

---

### 9. Navigation Complexity That Makes Refactoring Risky

#### Risk 9.1: Token-Based Navigation Triggers

**Title:** Navigation flow controlled by token comparison

**Current behavior:**
```kotlin
// Location: GrammarMateApp.kt:137-138, 898-929
val lastFinishedToken = remember { 
    mutableStateOf(state.cardSession.subLessonFinishedToken) }
val lastBossFinishedToken = remember { 
    mutableStateOf(state.boss.bossFinishedToken) }

// Later in LaunchedEffect:
if (state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
    lastFinishedToken.value = state.cardSession.subLessonFinishedToken
    // Trigger navigation
}

if (state.boss.bossFinishedToken != lastBossFinishedToken.value) {
    lastBossFinishedToken.value = state.boss.bossFinishedToken
    onNavigate(Routes.LESSON)
}
```

**Why this is a problem:**
- Navigation triggered by token comparison, not explicit events
- Hard to understand navigation flow by reading code
- Risk of race conditions if tokens update rapidly
- Difficult to test navigation triggers

**Blast radius:**
- **Breaking:** Changing navigation flow requires updating token logic
- **Affected:** All navigation flows, session completion, boss battles
- **Test impact:** Requires UI test to verify navigation behavior

**Evidence:**
- `GrammarMateApp.kt:137-138` - Token state initialization
- `GrammarMateApp.kt:898-923` - Sub-lesson token comparison
- `GrammarMateApp.kt:926-929` - Boss token comparison
- `GrammarMateApp.kt:898-900` - Token reset handling

**Proposed direction:**
Replace tokens with explicit events (see Risk 1.2 proposal)

**Risk level:** HIGH

**Must-have tests before refactor:**
1. Test that sub-lesson completion triggers navigation
2. Test that boss completion triggers navigation
3. Test that token reset doesn't trigger spurious navigation
4. Test that rapid token updates don't cause race conditions
5. Regression test for all navigation flows

---

#### Risk 9.2: Previous Route Tracking for Back Navigation

**Title:** Manual route tracking creates tight coupling

**Current behavior:**
```kotlin
// Location: GrammarMateApp.kt:134, 194-206
var previousRoute by remember { mutableStateOf(Routes.HOME) }

val onNavigate: (String) -> Unit = remember(navController) {
    { route: String ->
        val actual = navController.currentBackStackEntry?.destination?.route
        if (route != actual) {
            previousRoute = actual ?: Routes.HOME  // Manual tracking
            navController.navigate(route) { ... }
        }
    }
}
```

**Why this is a problem:**
- Manual route tracking duplicates Navigation Component functionality
- Brittle - easy to forget to update previousRoute
- Tight coupling between screens through previousRoute
- Risk of incorrect back navigation

**Blast radius:**
- **Breaking:** Adding new screen requires updating previousRoute logic
- **Affected:** LadderScreen back navigation, all navigation flows
- **Test impact:** Must test back navigation from every screen

**Evidence:**
- `GrammarMateApp.kt:134` - previousRoute declaration
- `GrammarMateApp.kt:194-206` - Manual route tracking
- `GrammarMateApp.kt:297` - previousRoute used for settings back navigation

**Proposed direction:**
Use Navigation Component's back stack:
```kotlin
// Remove previousRoute tracking
// Use NavController's previousBackStackEntry instead
val onNavigate: (String) -> Unit = remember(navController) {
    { route: String ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}
```

**Risk level:** LOW

**Must-have tests before refactor:**
1. Test that back navigation works from every screen
2. Test that LadderScreen back navigation is correct
3. Test that navigation state is restored after process death
4. Regression test for all navigation flows

---

## Risk Summary Matrix

| Risk | Category | Level | Blast Radius | Test Coverage |
|------|----------|-------|--------------|---------------|
| 1.1 Verb drill answer coordination | Business logic in UI | HIGH | VerbDrill flow | Partial |
| 1.2 Daily practice block completion | Business logic in UI | HIGH | Daily practice flow | Partial |
| 2.1 God ViewModel | Business logic in ViewModel | HIGH | Entire app | Partial |
| 3.1 Session persistence | Infrastructure in UI | MEDIUM | VerbDrill session | Good |
| 4.1 Block completion duplication | Duplicated rules | MEDIUM | Daily practice | Partial |
| 5.1 Scattered store writes | Infrastructure | MEDIUM | All persistence | Good |
| 6.1 Deep callback chains | Hidden coupling | MEDIUM | TrainingScreen | Partial |
| 7.1 Dialog state ownership | Ambiguous ownership | MEDIUM | All dialogs | Partial |
| 8.1 VerbDrill dual scope | Test-only workaround | LOW | VerbDrill tests | Good |
| 9.1 Token-based navigation | Navigation complexity | HIGH | All navigation | Partial |
| 9.2 Previous route tracking | Navigation complexity | LOW | Back navigation | Good |

---

## Refactoring Priority

### Phase 1: Critical (HIGH risk, must fix first)

1. **Replace token-based navigation with explicit events** (Risk 9.1)
   - Create sealed class events for session completion
   - Centralize navigation triggers
   - Add navigation tests

2. **Extract business logic from UI callbacks** (Risk 1.1, 1.2)
   - Move VerbDrill coordination to ViewModel/use case
   - Move Daily practice completion to ViewModel/use case
   - Add business logic tests

3. **Decompose TrainingViewModel** (Risk 2.1)
   - Create domain-specific ViewModels
   - Create coordinator pattern
   - Add feature integration tests

### Phase 2: Important (MEDIUM risk)

4. **Centralize dialog state management** (Risk 7.1)
   - Move all dialogs to ViewModel or use state machine
   - Add dialog state tests

5. **Consolidate callback chains** (Risk 6.1)
   - Use event-based pattern
   - Add event handler tests

6. **Eliminate duplicated rules** (Risk 4.1)
   - Centralize block completion logic
   - Add consistency tests

7. **Centralize persistence** (Risk 3.1, 5.1)
   - Create persistence coordinator
   - Add persistence tests

### Phase 3: Nice to have (LOW risk)

8. **Fix VerbDrill dual scope** (Risk 8.1)
   - Use single instance with proper sharing
   - Add scope tests

9. **Remove manual route tracking** (Risk 9.2)
   - Use Navigation Component features
   - Add navigation tests

---

## Testing Strategy Before Refactor

### Required Test Coverage

1. **Business Logic Tests**
   - VerbDrill answer acceptance coordination
   - Daily practice block completion progression
   - Session completion event handling
   - Token-based navigation triggers

2. **State Management Tests**
   - Dialog state consistency
   - Session state persistence
   - Token state synchronization
   - Navigation state tracking

3. **Integration Tests**
   - Cross-feature interactions (Pomodoro + Daily)
   - Multi-ViewModel coordination
   - Navigation flow end-to-end
   - Persistence after process death

4. **Regression Tests**
   - VerbDrillSessionCard flow
   - Daily practice flow
   - Regular lesson flow
   - All navigation flows

### Test Infrastructure Needed

1. **ViewModel test helpers**
   - Fake stores for testing
   - Test coroutine scope
   - State capture utilities

2. **UI test helpers**
   - Compose test rules
   - Navigation test helpers
   - Dialog test helpers

3. **Integration test setup**
   - Fake dependencies
   - Test application class
   - Test data generators

---

## Next Steps

1. **Review this risk classification** with team
2. **Prioritize risks** based on business impact
3. **Create detailed refactor plans** for high-priority risks
4. **Write missing tests** before starting refactor
5. **Execute refactor in phases** with testing at each phase

---

**Analysis Complete:** This document provides a comprehensive risk classification of the UI/Nav architecture, with specific evidence, blast radius analysis, and testing requirements for each identified risk.
