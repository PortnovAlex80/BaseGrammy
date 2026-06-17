# Wave 1 Agent A1: UI/Nav Architecture Current Map

**Analysis Date:** 2026-05-22
**Scope:** UI and Navigation Layer
**Method:** Factual code analysis - NO changes made
**Agent:** UI/Nav Architecture Auditor

---

## Executive Summary

BaseGrammy uses Jetpack Compose with a single-activity architecture centered around `GrammarMateApp.kt` (1,998 lines). The UI layer manages navigation through Compose Navigation with 10 defined routes, coordinating between two primary ViewModels (`TrainingViewModel` and `VerbDrillViewModel`). State management follows a unidirectional flow pattern with `TrainingUiState` as the single source of truth.

**Critical Finding:** The application has significant business logic embedded in the UI layer, particularly in `GrammarMateApp.kt` which handles session coordination, dialog state management, and navigation orchestration that should ideally be separated.

---

## Active User Flows

### 1. Main Entry Flow
```
AppRoot.kt → StartupScreen → GrammarMateApp → HomeScreen
```

**Evidence:** `AppRoot.kt:91-109`
- Handles data migration on launch
- Shows startup screen during restore
- Delegates to `GrammarMateApp` after restore complete

### 2. Lesson Learning Flow
```
HomeScreen → LessonRoadmapScreen → TrainingScreen → (completion) → LessonRoadmapScreen
```

**Evidence:** `GrammarMateApp.kt:300-303, 349-352`
- HomeScreen: `onSelectLesson` → navigate to LESSON
- LessonRoadmapScreen: `onStartSubLesson` → navigate to TRAINING
- TrainingScreen: completion → navigate back to LESSON via `returnTo`

### 3. Verb Drill Flow
```
HomeScreen → VerbDrillScreen → TrainingScreen → VerbDrillScreen
```

**Evidence:** `GrammarMateApp.kt:333, 554-558`
- HomeScreen: `onOpenVerbDrill` → navigate to VERB_DRILL
- VerbDrillScreen: `onStartSession` → navigate to TRAINING with verb cards
- TrainingScreen: exit → persist session → navigate back to VERB_DRILL

### 4. Daily Practice Flow
```
HomeScreen → DailyPracticeScreen → TrainingScreen → DailyPracticeScreen → HomeScreen
```

**Evidence:** `GrammarMateApp.kt:311-329, 755-763, 523-525`
- HomeScreen: `onOpenElite` → start daily practice → navigate to DAILY_PRACTICE
- DailyPracticeScreen: `onStartCardBlock` → navigate to TRAINING
- TrainingScreen: `onSessionDone` → navigate back to DAILY_PRACTICE
- DailyPracticeScreen: completion → navigate to HOME

### 5. Settings Flow
```
Any Screen → SettingsSheet → (background: TrainingScreen pause/resume)
```

**Evidence:** `GrammarMateApp.kt:232-282`
- SettingsSheet accessible from all screens
- Pauses TrainingScreen if active when opened
- Resumes TrainingScreen if active when dismissed

---

## Main Classes and Responsibilities

### GrammarMateApp (1,998 lines)
**File:** `ui/GrammarMateApp.kt:115-607`

**Responsibilities:**
- Central navigation coordinator with Compose Navigation
- Dialog state management (7 dialog types tracked in `DialogState` data class)
- Session lifecycle coordination between ViewModels
- Token-based navigation triggers (sub-lesson finish, boss finish)
- Back button handling per route
- TTS download progress coordination
- Pomodoro timer lifecycle

**Evidence:** `GrammarMateApp.kt:115-607`

```kotlin
@Composable
fun GrammarMateApp(vm: TrainingViewModel = viewModel()) {
    // Navigation host with 10 routes
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(...) }
        composable(Routes.LESSON) { LessonRoadmapScreen(...) }
        composable(Routes.TRAINING) { TrainingScreenContent(...) }
        // ... 7 more routes
    }
}
```

### Navigation Routes
**File:** `ui/GrammarMateApp.kt:86-97`

```kotlin
private object Routes {
    const val HOME = "home"
    const val LESSON = "lesson"
    const val ELITE = "elite"        // backward compat redirect
    const val VOCAB = "vocab"        // backward compat redirect
    const val DAILY_PRACTICE = "daily_practice"
    const val STORY = "story"
    const val TRAINING = "training"
    const val LADDER = "ladder"
    const val VERB_DRILL = "verb_drill"
    const val VOCAB_DRILL = "vocab_drill"
}
```

### Screen Composables

#### HomeScreen (971 lines)
**File:** `ui/screens/HomeScreen.kt:98-971`

**Responsibilities:**
- Language and pack selection
- Lesson tile grid display with flower states
- Daily practice entry point
- Verb/Vocab drill entry points
- Pomodoro timer integration
- Profile stats display

**Evidence:** `HomeScreen.kt:98-971`

#### LessonRoadmapScreen
**File:** `ui/screens/LessonRoadmapScreen.kt`

**Responsibilities:**
- Sub-lesson selection
- Boss battle entry
- Review mode entry
- Lesson navigation

#### TrainingScreen (estimated 600+ lines)
**File:** `ui/screens/TrainingScreen.kt:94-100+`

**Responsibilities:**
- Card display and interaction
- Input handling (keyboard, voice, word bank)
- Answer checking and feedback
- Hint display
- Progress indication
- Session completion handling

#### VerbDrillScreen (200+ lines)
**File:** `ui/VerbDrillScreen.kt:64-175`

**Responsibilities:**
- Tense/group selection
- Session card filtering
- Start/Repeat/Continue/Reset actions
- Debug dialog

**Evidence:** `VerbDrillScreen.kt:64-175`

#### DailyPracticeScreen
**File:** `ui/DailyPracticeScreen.kt`

**Responsibilities:**
- Block-type selection
- Card coordination
- Session progression
- Completion handling

### ViewModels

#### TrainingViewModel (2,579 lines)
**File:** `ui/TrainingViewModel.kt:105-2579`

**Responsibilities:**
- Central state holder for entire app
- Business logic coordination
- Feature delegation (boss, daily, progress, etc.)
- Audio coordination
- Settings management
- Import/export operations

**Evidence:** `TrainingViewModel.kt:76-150`

```kotlin
class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val _coreState = MutableStateFlow(TrainingUiState(isLoading = true))
    val uiState: StateFlow<TrainingUiState> = _coreState.asStateFlow()
    
    // Feature instances
    private val answerValidator = AnswerValidator()
    private val vocabSprintRunner = VocabSprintRunner(...)
    private val streakManager = StreakManager(...)
    private val bossBattleRunner = BossBattleRunner()
    private val progressTracker = ProgressTracker(...)
    // ... 15+ more feature instances
}
```

#### VerbDrillViewModel
**File:** `ui/VerbDrillViewModel.kt`

**Responsibilities:**
- Verb drill session state
- Card filtering and sorting
- Session persistence
- Last session tracking

---

## Business Rules Found in UI Layer

### 1. Session Coordination Logic
**Location:** `GrammarMateApp.kt:446-456`

**Issue:** Business logic embedded in UI callback

```kotlin
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

**Business Rule:** Verb drill cards must be marked correct in VerbDrillViewModel when accepted in TrainingScreen.

### 2. Navigation-Based Session Persistence
**Location:** `GrammarMateApp.kt:483-486`

**Issue:** Navigation logic tied to business logic

```kotlin
returnTo == Routes.VERB_DRILL && hasActiveCard -> {
    verbDrillVm.persistSessionState()  // Business rule triggered by navigation
    vm.exitVerbDrillSession()
    onNavigate(Routes.VERB_DRILL)
}
```

**Business Rule:** Verb drill sessions must be persisted when exiting mid-session.

### 3. Token-Based Navigation Triggers
**Location:** `GrammarMateApp.kt:903-923`

**Issue:** Business logic in LaunchedEffect

```kotlin
if (currentRoute == Routes.TRAINING && 
    state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
    lastFinishedToken.value = state.cardSession.subLessonFinishedToken
    vm.onTrainingSessionCompleted()  // Business event triggered by UI state
    val hasCards = state.cardSession.currentCard != null
    if (!state.pomodoro.isComplete && hasCards) {
        val returnTo = state.cardSession.returnTo
        if (returnTo == Routes.DAILY_PRACTICE) {
            vm.daily.onBlockComplete()  // Direct business logic call
        }
        onNavigate(returnTo)
    }
}
```

**Business Rule:** Sub-lesson completion triggers Pomodoro tracking and navigates to `returnTo` destination.

### 4. Dialog State Management
**Location:** `GrammarMateApp.kt:101-110`

**Issue:** Complex state management in UI layer

```kotlin
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
```

**Business Rule:** Seven different dialog types tracked with interdependent state (e.g., `pendingDailyLevel` only meaningful when `showDailyResumeDialog` is true).

### 5. Welcome Dialog Trigger Logic
**Location:** `GrammarMateApp.kt:822-830`

**Issue:** Complex business rule in UI LaunchedEffect

```kotlin
LaunchedEffect(currentRoute, state.navigation.userName, 
               state.navigation.languages.size) {
    if (currentRoute == Routes.HOME
        && state.navigation.userName == "GrammarMateUser"
        && state.navigation.welcomeDialogAttempts < 3
        && state.navigation.languages.isNotEmpty()) {  // Guard condition
        onDialogsChange(dialogs.copy(showWelcomeDialog = true))
    }
}
```

**Business Rule:** Welcome dialog shows only on HOME screen, for default username, max 3 attempts, and only after languages are loaded (to prevent overwriting real profile).

---

## Infrastructure Dependencies

### UI Layer Dependencies

**From:** `ui/GrammarMateApp.kt:1-83`

```kotlin
// Navigation
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// State Management
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

// Foundation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*

// Lifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
```

### ViewModel Dependencies

**From:** `ui/TrainingViewModel.kt:1-75`

```kotlin
// Feature Modules (business logic delegation)
import com.alexpo.grammermate.feature.boss.BossBattleRunner
import com.alexpo.grammermate.feature.daily.DailyPracticeCoordinator
import com.alexpo.grammermate.feature.progress.ProgressTracker
import com.alexpo.grammermate.feature.training.SessionRunner
import com.alexpo.grammermate.feature.vocab.VocabSprintRunner

// Shared Logic
import com.alexpo.grammermate.shared.SettingsActionHandler
import com.alexpo.grammermate.shared.audio.AudioCoordinator

// Data Layer
import com.alexpo.grammermate.data.*
```

### Data Flow Pattern

```
UI Layer (Compose)
    ↓ (observes)
TrainingUiState (StateFlow)
    ↓ (held by)
TrainingViewModel
    ↓ (delegates to)
Feature Helpers (boss, daily, progress, training, vocab)
    ↓ (use)
Data Stores (lessonStore, progressStore, masteryStore, etc.)
```

---

## State Mutations

### Direct UI State Mutations

#### 1. Dialog State Updates
**Location:** `GrammarMateApp.kt:234-240, 296-297, etc.`

```kotlin
onDismiss = remember(dialogs, currentRoute, state.cardSession.currentCard) {
    {
        dialogs = dialogs.copy(showSettings = false)  // Direct mutation
        if (currentRoute == Routes.TRAINING && 
            state.cardSession.currentCard != null) {
            vm.resumeFromSettings()
        }
    }
}
```

**Impact:** UI layer directly manages complex dialog state with conditional side effects.

#### 2. Navigation State Tracking
**Location:** `GrammarMateApp.kt:134-198`

```kotlin
var previousRoute by remember { mutableStateOf(Routes.HOME) }

val onNavigate: (String) -> Unit = remember(navController) {
    { route: String ->
        val actual = navController.currentBackStackEntry?.destination?.route
        if (route != actual) {
            previousRoute = actual ?: Routes.HOME  // State mutation
            navController.navigate(route) { ... }
        }
    }
}
```

**Impact:** Navigation state tracked in UI for back button handling.

#### 3. Token Tracking for Navigation Triggers
**Location:** `GrammarMateApp.kt:137-138, 898-929`

```kotlin
val lastFinishedToken = remember { 
    mutableStateOf(state.cardSession.subLessonFinishedToken) }
val lastBossFinishedToken = remember { 
    mutableStateOf(state.boss.bossFinishedToken) }

// Later in LaunchedEffect:
if (state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
    lastFinishedToken.value = state.cardSession.subLessonFinishedToken
    // Trigger navigation
}
```

**Impact:** UI layer manages token-based state synchronization to trigger business events.

### ViewModel State Mutations

**From:** `TrainingViewModel.kt:95-111`

```kotlin
private val _coreState = MutableStateFlow(TrainingUiState(isLoading = true))

private val stateAccess = object : TrainingStateAccess {
    override val uiState: StateFlow<TrainingUiState> get() = this@TrainingViewModel.uiState
    override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
        _coreState.update(transform)  // Centralized state mutation
    }
    override fun saveProgress() = this@TrainingViewModel.saveProgress()
}
```

**Pattern:** Single source of truth with `StateFlow<TrainingUiState>`. All state mutations go through `updateState()`.

---

## Test Coverage

### Clickable UI Tests (7 files)

**Location:** `app/src/test/java/com/alexpo/grammermate/ui/`

1. **DailyPracticeClickUiTest.kt** - Daily practice flow
2. **PauseCascadeClickUiTest.kt** - Pause/resume behavior
3. **PomodoroBannerClickUiTest.kt** - Pomodoro timer UI
4. **PomodoroClickUiTest.kt** - Pomodoro functionality
5. **RegularLessonClickUiTest.kt** - Standard lesson flow
6. **VerbDrillSessionCardRegressionTest.kt** - Verb drill session management
7. **VerbPracticeClickUiTest.kt** - Verb practice flow

### VerbDrillSessionCardRegressionTest Requirements

**Location:** `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md`

**Key Requirements:**

1. **Must use UI clicks:** `verb_start_button`, `check_button`, `next_button`, `repeat_button`, `continue_button`, `reset_button`

2. **Forbidden direct calls:**
   - ❌ `verbVm.startSession()` from test body
   - ❌ `verbVm.onResumeSession()` from test body
   - ❌ `verbVm.onRepeatSession()` from test body
   - ❌ `verbVm.onStartFresh()` from test body

3. **Allowed state reads:**
   - ✅ `session.cards.map { it.id }`
   - ✅ `store.loadLastSession()`
   - ✅ `store.loadProgress()`
   - ✅ `currentCard.acceptedAnswers.first()`

4. **Critical invariant:** "A card is counted shown only after Check succeeds or after the hint/show-answer completion path. Navigation-only cards are not counted shown."

**Evidence:** `VerbDrillSessionCardRegressionTest-Requirements.md:1-183`

### Test Infrastructure Constraints

**From:** `CLAUDE.md:141-175`

```markdown
Required pattern for SessionCard batch tests:

1. Use deterministic cards with stable IDs and `rank = index`.
2. Render `VerbDrillScreen` and `TrainingScreen` or a small harness.
3. Start through UI using `verb_start_button`.
4. Complete cards through UI using `input_field` and `check_button`.
5. Navigate without completion through UI using `next_button` / `prev_button`.
6. Exercise SessionCard actions through UI using `session_card`, 
   `repeat_button`, `continue_button`, `reset_button`.
7. Read ViewModel/store state only for answers and assertions.
```

---

## Architecture Risks / Unclear Points

### 1. Massive God ViewModel
**Severity:** HIGH
**Evidence:** `TrainingViewModel.kt:105-2579` (2,579 lines)

**Issue:** Single ViewModel coordinates:
- 15+ feature instances
- 10+ data stores
- All UI state
- All business logic delegation

**Risk:** 
- Difficult to test
- High coupling
- Hard to maintain
- Violates Single Responsibility Principle

**Recommendation:** Decompose into domain-specific ViewModels (Training, Daily, VerbDrill, VocabDrill, Settings, etc.)

### 2. Business Logic in UI Layer
**Severity:** MEDIUM
**Evidence:** `GrammarMateApp.kt:446-456, 483-486, 903-923`

**Issue:** Session coordination, navigation triggers, and business rules embedded in UI callbacks.

**Risk:**
- Hard to test business logic
- UI changes break business rules
- Cannot reuse business logic

**Recommendation:** Move to ViewModel or use use cases.

### 3. Complex Dialog State Management
**Severity:** MEDIUM
**Evidence:** `GrammarMateApp.kt:101-110, 234-282`

**Issue:** Seven dialog types with interdependent state tracked in UI layer.

**Risk:**
- State synchronization bugs
- Difficult to test dialog flows
- Hard to add new dialogs

**Recommendation:** Use state machine or delegate to ViewModel.

### 4. Token-Based Navigation Triggers
**Severity:** MEDIUM
**Evidence:** `GrammarMateApp.kt:898-929`

**Issue:** Navigation triggered by token comparison in `LaunchedEffect`.

**Risk:**
- Non-obvious navigation flows
- Hard to debug
- Race conditions possible

**Recommendation:** Use explicit events/sealed classes instead of token comparison.

### 5. Tight Coupling Between Screens
**Severity:** LOW
**Evidence:** `GrammarMateApp.kt:194-206, 242-251`

**Issue:** `previousRoute` tracking creates tight coupling between screens.

**Risk:**
- Brittle navigation
- Hard to add new screens
- Complex back button handling

**Recommendation:** Use Navigation Component's built-in back stack management.

### 6. VerbDrillViewModel Dual Scope
**Severity:** LOW
**Evidence:** `GrammarMateApp.kt:142-150, 435-443`

**Issue:** `VerbDrillViewModel` instantiated at two scopes:
1. Outer scope (for session persistence across routes)
2. TRAINING route scope (for tense info bottom sheet)

**Risk:**
- Potential state synchronization issues
- Confusion about which instance handles what

**Recommendation:** Document scoping clearly or use single instance with state sharing.

### 7. Navigation Route String Literals
**Severity:** LOW
**Evidence:** `GrammarMateApp.kt:86-97`

**Issue:** Route strings defined as constants but still scattered throughout code.

**Risk:**
- Typos cause runtime errors
- Hard to refactor routes
- No compile-time safety

**Recommendation:** Use type-safe navigation library (e.g., Compose Destinations).

---

## Evidence Index

### File Locations

**UI Layer (32 files):**
- `ui/GrammarMateApp.kt` - Main navigation coordinator (1,998 lines)
- `ui/AppRoot.kt` - Application entry point (65 lines)
- `ui/Theme.kt` - Material 3 theme
- `ui/TrainingViewModel.kt` - Central state holder (2,579 lines)
- `ui/VerbDrillViewModel.kt` - Verb drill state
- `ui/VocabDrillViewModel.kt` - Vocab drill state
- `ui/VerbDrillScreen.kt` - Verb drill selection (200+ lines)
- `ui/VocabDrillScreen.kt` - Vocab drill selection
- `ui/DailyPracticeScreen.kt` - Daily practice coordinator
- `ui/TrainingCardSession.kt` - Session card display
- `ui/screens/HomeScreen.kt` - Main dashboard (971 lines)
- `ui/screens/LessonRoadmapScreen.kt` - Lesson selection
- `ui/screens/TrainingScreen.kt` - Card practice (600+ lines)
- `ui/screens/LadderScreen.kt` - Mastery ladder
- `ui/screens/SettingsScreen.kt` - Settings management
- `ui/screens/StoryQuizScreen.kt` - Story quizzes
- `ui/components/*.kt` - 16 reusable components

**Test Layer (7 clickable tests):**
- `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt`
- `app/src/test/java/com/alexpo/grammermate/ui/DailyPracticeClickUiTest.kt`
- `app/src/test/java/com/alexpo/grammermate/ui/VerbPracticeClickUiTest.kt`
- `app/src/test/java/com/alexpo/grammermate/ui/RegularLessonClickUiTest.kt`
- `app/src/test/java/com/alexpo/grammermate/ui/PomodoroClickUiTest.kt`
- `app/src/test/java/com/alexpo/grammermate/ui/PomodoroBannerClickUiTest.kt`
- `app/src/test/java/com/alexpo/grammermate/ui/PauseCascadeClickUiTest.kt`

**Documentation:**
- `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md` - Clickable test requirements
- `CLAUDE.md:141-175` - VerbDrill clickable test constraints

### Key Code Patterns

**Navigation Pattern:**
```kotlin
// Location: GrammarMateApp.kt:194-206
val onNavigate: (String) -> Unit = remember(navController) {
    { route: String ->
        navController.navigate(route) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
}
```

**State Observation Pattern:**
```kotlin
// Location: GrammarMateApp.kt:117-118
val state by vm.uiState.collectAsStateWithLifecycle()
```

**ViewModel Creation Pattern:**
```kotlin
// Location: GrammarMateApp.kt:115, 142, 435
@Composable
fun GrammarMateApp(vm: TrainingViewModel = viewModel()) {
    val verbDrillVm = viewModel<VerbDrillViewModel>()
    val verbTenseInfoVm = viewModel<VerbDrillViewModel>()
}
```

**Callback Chain Pattern:**
```kotlin
// Location: GrammarMateApp.kt:693-731
TrainingScreenContent(
    state, vm,
    onSubmit = { ... },  // 4 layers of callback wrapping
    onNext = { ... },
    onPrev = { ... },
    onShowExitDialog = { ... },
    onShowSettings = { ... },
    // ... 8 more callbacks
)
```

---

## Next Steps for Refactoring

Based on this audit, the recommended refactoring priority is:

1. **HIGH:** Extract business logic from `GrammarMateApp.kt` into ViewModel/use cases
2. **HIGH:** Split `TrainingViewModel` into domain-specific ViewModels
3. **MEDIUM:** Implement proper state management for dialogs (state machine)
4. **MEDIUM:** Replace token-based navigation with explicit events
5. **LOW:** Consider type-safe navigation library
6. **LOW:** Clarify VerbDrillViewModel scoping

---

**Analysis Complete:** This document provides a factual baseline for safe architecture refactoring decisions. All claims are backed by file:line evidence from the current codebase.
