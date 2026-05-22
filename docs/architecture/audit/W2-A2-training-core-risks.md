# Training Core Architecture Risk Analysis

**Agent:** Wave 2 Agent A2  
**Scope:** Training Core (ViewModel, SessionRunner, CardProvider, State Machine)  
**Date:** 2025-01-22  
**Purpose:** Classify architectural problems and assess refactor risk levels

---

## Executive Summary

The Training Core suffers from **9 HIGH-risk**, **6 MEDIUM-risk**, and **3 LOW-risk** architectural issues. The most critical problems are:

1. **TrainingViewModel God Object** (1570 lines) - violates single responsibility
2. **State Mutation Scatter** - same state mutated in 8+ places
3. **Event-Driven Complexity** - implicit control flow through event lists
4. **Timer Side Effects** - independent coroutine mutates state
5. **Circular Dependencies** - features depend on each other through callbacks

**Recommended approach:** Incremental refactoring with test coverage first, then extract features.

---

## Risk Category 1: Business Logic Inside Compose/UI

### Risk 1.1: UI Calculates Business Metrics
**Risk Level:** MEDIUM

**Current behavior:**
- UI calculates `successRate` inline: `successRate = if (totalCards > 0) sessionCorrect * 100 / totalCards else 0` (TrainingScreen.kt:221)
- UI calculates `speedWpm` inline: `speedWpm = if (state.cardSession.voiceActiveMs > 0) (state.cardSession.voiceWordCount / (state.cardSession.voiceActiveMs / 60000.0)).toInt() else 0` (TrainingScreen.kt:299, 307)

**Why this is a problem:**
- Business logic embedded in presentation layer
- Cannot unit test without Compose framework
- Duplicates calculation logic if needed elsewhere
- UI decides when to show zero vs calculated value

**Blast radius:**
- TrainingScreen.kt lines 221, 299, 307
- Any other screen that needs success rate or WPM display
- Performance metrics reporting

**Evidence:**
```kotlin
// TrainingScreen.kt:221
val successRate = if (totalCards > 0) sessionCorrect * 100 / totalCards else 0

// TrainingScreen.kt:299, 307
speedWpm = if (state.cardSession.voiceActiveMs > 0) (state.cardSession.voiceWordCount / (state.cardSession.voiceActiveMs / 60000.0)).toInt() else 0
```

**Proposed direction:**
- Move calculations to ViewModel or domain model
- Expose `successRate: Int` and `speedWpm: Int` in UI state
- UI only displays pre-calculated values

**Must-have tests before refactor:**
- Test success rate calculation with zero total
- Test success rate calculation with partial completion
- Test WPM calculation with zero voice time
- Test WPM calculation with realistic values

---

### Risk 1.2: UI Decides Session Completion Logic
**Risk level:** LOW

**Current behavior:**
- UI checks `if (!hasCards)` to show completion screen (TrainingScreen.kt:171)
- UI checks `if (isVerbDrillComplete)` for verb drill completion (TrainingScreen.kt:142)

**Why this is a problem:**
- Completion logic split between UI and ViewModel
- UI interprets `hasCards` differently than ViewModel's `isComplete`
- Risk of UI showing completion when ViewModel thinks session is active

**Blast radius:**
- TrainingScreen.kt lines 142, 154, 171
- Completion screen rendering
- User journey after completion

**Evidence:**
```kotlin
// TrainingScreen.kt:171
if (!hasCards) {
    // Show completion screen
}

// TrainingScreen.kt:142
if (isVerbDrillComplete) {
    // Show completion screen
}
```

**Proposed direction:**
- ViewModel exposes single `isSessionComplete: Boolean` 
- UI checks only that flag
- ViewModel owns all completion criteria

**Must-have tests before refactor:**
- Test completion detection for normal session
- Test completion detection for verb drill
- Test completion detection for boss battle
- Test incompletion when cards remain but all answered correctly

---

## Risk Category 2: Business Logic Inside Android ViewModel

### Risk 2.1: TrainingViewModel Violates Single Responsibility (GOD OBJECT)
**Risk level:** HIGH

**Current behavior:**
- TrainingViewModel is **1570 lines** (TrainingViewModel.kt:76-1646)
- Handles navigation, session lifecycle, progress, audio, Pomodoro, settings, import/export, boss battles, daily practice, vocab sprint, story, streaks, backups, profile stats
- Constructs 15+ feature coordinators in init block (lines 115-256)
- Has 80+ public methods

**Why this is a problem:**
- Impossible to understand full responsibility
- Every feature adds more code to same class
- Cannot unit test in isolation
- High coupling - changing one feature risks breaking others
- Violates Single Responsibility Principle

**Blast radius:**
- Entire training flow
- All feature coordinators (BossOrchestrator, DailyPracticeCoordinator, etc.)
- All UI screens that consume TrainingViewModel
- Any new feature that touches training

**Evidence:**
```kotlin
// TrainingViewModel.kt:76-1646 (1570 lines)
class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    // 15+ feature coordinators
    private val answerValidator = AnswerValidator()
    private val vocabSprintRunner = VocabSprintRunner(...)
    private val streakManager = StreakManager(...)
    private val bossBattleRunner = BossBattleRunner()
    private val progressTracker = ProgressTracker(...)
    private val cardProvider = CardProvider(...)
    private val sessionRunner = SessionRunner(...)
    private val flowerRefresher = FlowerRefresher(...)
    private val dailyPracticeCoordinator = DailyPracticeCoordinator(...)
    private val storyRunner = StoryRunner(...)
    private val bossOrchestrator = BossOrchestrator(...)
    private val progressRestorer = ProgressRestorer(...)
    private val badSentenceHelper = BadSentenceHelper(...)
    private val audioCoordinator = AudioCoordinator(...)
    private val pomodoroHelper = PomodoroHelper(...)
    private val settingsActionHandler = SettingsActionHandler(...)
    
    // 80+ public methods
    fun selectLanguage(languageId: String)
    fun selectLesson(lessonId: String)
    fun selectPack(packId: String)
    fun selectMode(mode: TrainingMode)
    fun submitAnswer(): SubmitResult
    fun nextCard(triggerVoice: Boolean = false)
    fun prevCard()
    fun navigateNext()
    fun navigatePrev()
    fun togglePause()
    fun pauseSession()
    fun finishSession()
    fun showAnswer()
    fun startVerbDrillSession(cards: List<VerbDrillCard>)
    fun exitVerbDrillSession()
    fun replaceVerbDrillCards(cards: List<VerbDrillCard>)
    fun startDailyTranslateSession(cards: List<SessionCard>)
    fun startDailyVerbsSession(cards: List<SessionCard>)
    fun exitDailySession()
    // ... 60+ more methods
}
```

**Proposed direction:**
- Extract feature-specific ViewModels (PomodoroViewModel, AudioViewModel, SettingsViewModel)
- Keep TrainingViewModel focused on training session lifecycle only
- Use composition/delegation instead of inheritance

**Must-have tests before refactor:**
- Integration tests for every public method
- Regression tests for each feature coordinator interaction
- State transition tests for all session types
- UI binding tests for all exposed state

---

### Risk 2.2: Timer System Embedded in SessionRunner
**Risk level:** MEDIUM

**Current behavior:**
- Timer coroutine launched inside SessionRunner (SessionRunner.kt:1196-1225)
- Independent coroutine mutates `activeTimeMs` every 500ms (line 1209)
- Separate high-frequency flow `sessionTimerMsSink` (line 1212)
- Flushes to main state every 10s via `stateAccess.updateState` (line 1218-1221)
- Calls `onTimerSaveProgress()` every 10s (line 1221)

**Why this is a problem:**
- Side effect: state changes without explicit user action
- Racy tests - timer tick can fire during assertions
- Complex lifecycle - must pause/resume correctly across session transitions
- Hard to test - need to manipulate time or mock coroutines
- Violates single responsibility - SessionRunner shouldn't manage time

**Blast radius:**
- SessionRunner.kt lines 1196-1238 (timer implementation)
- All session operations (start, pause, resume, finish)
- Progress saving logic
- UI time display
- Any test that touches session state

**Evidence:**
```kotlin
// SessionRunner.kt:1196-1225
fun resumeTimer() {
    if (timerJob?.isActive == true) return
    activeStartMs = SystemClock.elapsedRealtime()
    timerTickCounter = 0
    lastSaveActiveTimeMs = stateAccess.uiState.value.cardSession.activeTimeMs
    timerJob = coroutineScope.launch {
        while (true) {
            delay(500)
            val start = activeStartMs ?: continue
            val elapsed = SystemClock.elapsedRealtime() - start
            activeStartMs = SystemClock.elapsedRealtime()

            // Accumulate total active time internally
            lastSaveActiveTimeMs += elapsed

            // Push high-frequency timer value to separate flow (for UI display)
            sessionTimerMsSink?.invoke(lastSaveActiveTimeMs)

            // Push activeTimeMs to main state and save progress only every ~10s (20 ticks)
            timerTickCounter++
            if (timerTickCounter >= 20) {
                timerTickCounter = 0
                stateAccess.updateState {
                    it.copy(cardSession = it.cardSession.copy(activeTimeMs = lastSaveActiveTimeMs))
                }
                onTimerSaveProgress()
            }
        }
    }
}
```

**Proposed direction:**
- Extract TimerCoordinator as separate class
- Use controlled time source for testing (Clock abstraction)
- Decouple timer tick from state mutation
- Make timer pause/resume explicit in session lifecycle

**Must-have tests before refactor:**
- Test timer accumulation across pause/resume cycles
- Test 10s flush to main state
- Test high-frequency flow updates
- Test timer cancellation on session finish
- Test concurrent timer + user actions

---

## Risk Category 3: Infrastructure/File IO Decisions in Training Logic

### Risk 3.1: saveProgress Scattered Across Training Logic
**Risk level:** MEDIUM

**Current behavior:**
- `saveProgress()` called in 15+ places throughout TrainingViewModel (lines 585, 633, 656, 671, 860, 901, 988, 1091, 1107, 1186, 1302, 1484, 1515, 1582)
- SessionRunner returns `SessionEvent.SaveProgress` in 20+ places
- File I/O triggered implicitly from business logic
- Mastery store flush embedded in multiple flows (lines 352, 1187, 1433)

**Why this is a problem:**
- No single source of truth for when to persist
- Easy to miss save on new code path
- Performance impact - excessive file writes
- Hard to test - need to mock file I/O everywhere
- Violates separation of concerns - business logic shouldn't know about files

**Blast radius:**
- All training operations
- Progress persistence
- Backup creation
- Any test that touches training state

**Evidence:**
```kotlin
// TrainingViewModel.kt - saveProgress called in 15+ places
fun selectLanguage(languageId: String) {
    // ...
    saveProgress() // Line 585
}

fun selectLesson(lessonId: String) {
    // ...
    saveProgress() // Line 633
}

fun finishSession() {
    // ...
    saveProgress() // Line 671
}

private fun handleSessionEvents(events: List<SessionEvent>) {
    events.forEach { event ->
        when (event) {
            is SessionEvent.SaveProgress -> saveProgress() // Line 1302
            // ...
        }
    }
}

// SessionRunner.kt - SaveProgress event returned in 20+ places
fun startSession(): List<SessionEvent> {
    val events = mutableListOf<SessionEvent>()
    events.add(SessionEvent.SaveProgress) // Line 188
    return events
}
```

**Proposed direction:**
- Single save point via dirty flag or auto-save timer
- Separate persistence layer from business logic
- Batch saves to reduce file I/O
- Make save scheduling explicit

**Must-have tests before refactor:**
- Test progress persistence after every operation
- Test no data loss on crash
- Test backup creation timing
- Test concurrent saves don't corrupt data

---

### Risk 3.2: Store Access Embedded in Business Logic
**Risk level:** LOW

**Current behavior:**
- Direct store access throughout TrainingViewModel (lessonStore, progressStore, masteryStore, etc.)
- Store reads scattered across 20+ methods
- Hidden card store queried during card building (line 1442)

**Why this is a problem:**
- Business logic tightly coupled to file format
- Cannot swap in-memory stores for testing
- Store schema changes ripple through business logic
- Hard to mock for unit tests

**Blast radius:**
- All training operations
- Card building logic
- Progress tracking
- Test harness setup

**Evidence:**
```kotlin
// TrainingViewModel.kt - Direct store access
private val lessonStore = container.lessonStore
private val progressStore = container.progressStore
private val masteryStore = container.masteryStore
private val hiddenCardStore = container.hiddenCardStore

private fun buildSessionCards() {
    val hiddenIds = hiddenCardStore.getHiddenCardIds() // Line 1442
    // ...
}
```

**Proposed direction:**
- Repository pattern - abstract store access behind interfaces
- Business logic depends on repositories, not stores
- Stores are implementation detail of repositories

**Must-have tests before refactor:**
- Test business logic with fake repositories
- Test store layer independently
- Test repository error handling

---

## Risk Category 4: Duplicated Rules Across Components

### Risk 4.1: Mastery Counting Logic Duplicated
**Risk level:** MEDIUM

**Current behavior:**
- WORD_BANK doesn't count rule enforced in TrainingViewModel (line 693 comment)
- VOICE/KEYBOARD counting enforced in multiple places
- VerbDrillCards have separate tracking (line 1212)
- Rule: "Only VOICE and KEYBOARD answers count" duplicated across codebase

**Why this is a problem:**
- Easy to miss one location when changing rule
- Inconsistent behavior if one location missed
- No single source of truth for mastery counting
- VerbDrill exception proves rule is not universal

**Blast radius:**
- Mastery tracking
- Flower growth logic
- Progress calculation
- Verb drill vs regular session inconsistency

**Evidence:**
```kotlin
// TrainingViewModel.kt:693
// Only VOICE and KEYBOARD answers count — WORD_BANK does NOT (Level B rule).
if (inputMode == InputMode.WORD_BANK) return SubmitResult.Accepted.copy(hintShown = false)

// TrainingViewModel.kt:1212
private fun recordCardShowForMastery(card: SessionCard) {
    // VerbDrillCards have their own progress tracking in VerbDrillViewModel
    if (card is VerbDrillCard) return
    // ...
}

// SessionRunner.kt:1033
// Mastery tracking works normally (VOICE/KEYBOARD count).
```

**Proposed direction:**
- Single `shouldCountForMastery(inputMode, cardType)` function
- Centralized mastery counting policy
- VerbDrill uses same policy with different parameters

**Must-have tests before refactor:**
- Test WORD_BANK doesn't count
- Test VOICE counts
- Test KEYBOARD counts
- Test VerbDrillCard doesn't count in regular tracking
- Test all input modes

---

## Risk Category 5: Direct Store Writes Scattered

### Risk 5.1: masteryStore.flush() Called from Multiple Locations
**Risk level:** LOW

**Current behavior:**
- `masteryStore.flush()` called in 3 places (lines 352, 1187, 1433)
- Implicit flush timing - not clear when data hits disk
- No transactional safety

**Why this is a problem:**
- Easy to miss flush on new code path
- Risk of data loss if app killed
- No atomic multi-store updates
- Hard to reason about persistence guarantees

**Blast radius:**
- Mastery data persistence
- Flower state
- Progress data
- Crash recovery

**Evidence:**
```kotlin
// TrainingViewModel.kt:352
fun onAppBackgrounded() {
    masteryStore.flush()
}

// TrainingViewModel.kt:1187
fun onCleared() {
    masteryStore.flush()
}

// TrainingViewModel.kt:1433
private fun saveProgress() {
    // ...
    masteryStore.flush()
}
```

**Proposed direction:**
- Single flush point via saveProgress
- Explicit transaction boundaries
- Atomic multi-store updates

**Must-have tests before refactor:**
- Test mastery persistence after background
- Test mastery persistence after app kill
- Test no data loss on crash
- Test concurrent flush safety

---

## Risk Category 6: Hidden Coupling Through Callbacks/Events

### Risk 6.1: Query Callback Pattern in Events
**Risk level:** HIGH

**Current behavior:**
- Events contain callbacks: `GetMastery(callback: (LessonMasteryState?) -> Unit)` (SessionEvent.kt:35)
- Events contain callbacks: `GetSchedule(callback: (LessonSchedule?) -> Unit)` (SessionEvent.kt:36)
- Events contain callbacks: `CalculateCompletedSubLessons(callback: (Int) -> Unit)` (SessionEvent.kt:29-33)
- SessionRunner invokes callbacks to query data from ViewModel

**Why this is a problem:**
- Inverted control flow - hard to trace execution
- Reader must jump between files to understand logic
- Callbacks can be null or fail - adds error paths
- Cannot reason about data flow statically
- Tight coupling - SessionRunner knows about ViewModel's data access

**Blast radius:**
- SessionRunner -> ViewModel communication
- All session completion logic
- Sub-lesson calculation
- Elite unlock logic

**Evidence:**
```kotlin
// SessionEvent.kt:29-36
data class CalculateCompletedSubLessons(
    val lessonId: LessonId,
    val callback: (Int) -> Unit
) : SessionEvent()

data class GetMastery(
    val lessonId: String, 
    val langId: String, 
    val callback: (LessonMasteryState?) -> Unit
) : SessionEvent()

data class GetSchedule(
    val lessonId: String, 
    val callback: (LessonSchedule?) -> Unit
) : SessionEvent()

// SessionRunner.kt:58-59
private val getMastery: (String, String) -> LessonMasteryState?,
private val getSchedule: (String) -> LessonSchedule?,
```

**Proposed direction:**
- Pass data needed through constructor, not callbacks
- Use query interfaces that SessionRunner can call directly
- Eliminate callback events from SessionEvent hierarchy

**Must-have tests before refactor:**
- Test all callback paths succeed
- Test all callback paths handle null correctly
- Test callback failure doesn't crash app
- Test data flow without callbacks

---

### Risk 6.2: Circular Dependencies Through ViewModel
**Risk level:** HIGH

**Current behavior:**
- BossOrchestrator uses SessionRunner and CardProvider (TrainingViewModel.kt:202-209)
- SessionRunner needs ProgressTracker callback (line 167-173)
- DailyPracticeCoordinator needs `resolveProgressLessonInfo` callback (line 970)
- All coordinators constructed in TrainingViewModel init block

**Why this is a problem:**
- Cannot extract coordinators independently
- Changing one coordinator requires updating ViewModel
- Hard to test coordinators in isolation
- Risk of initialization order bugs
- Violates dependency inversion - high-level modules depend on low-level details

**Blast radius:**
- All feature coordinators
- SessionRunner
- TrainingViewModel initialization
- Any new feature coordinator

**Evidence:**
```kotlin
// TrainingViewModel.kt:202-209
private val bossOrchestrator = BossOrchestrator(
    stateAccess = stateAccess,
    sessionRunner = sessionRunner, // Circular dependency
    cardProvider = cardProvider,   // Circular dependency
    getMastery = { lessonId, langId -> ... },
    getSchedule = { lessonId -> ... }
)

// TrainingViewModel.kt:167-173
private val sessionRunner = SessionRunner(
    // ...
    getMastery = { lessonId, langId -> ... },
    getSchedule = { lessonId -> ... },
    calculateCompletedSubLessons = { ... } // Callback to ViewModel
)

// TrainingViewModel.kt:970
private val dailyPracticeCoordinator = DailyPracticeCoordinator(
    // ...
    resolveProgressLessonInfo = { resolveProgressLessonInfo() } // Callback to ViewModel
)
```

**Proposed direction:**
- Use dependency injection framework
- Interfaces instead of concrete classes
- Coordinator-specific interfaces, not generic callbacks
- Break cycles by inverting dependencies

**Must-have tests before refactor:**
- Test each coordinator in isolation
- Test coordinator interaction through interfaces
- Test initialization order doesn't matter
- Test no memory leaks from circular references

---

## Risk Category 7: Ambiguous Ownership of Progress/Session/Cursor

### Risk 7.1: Sub-lesson State Calculated in Multiple Places
**Risk level:** HIGH

**Current behavior:**
- `activeSubLessonIndex` calculated in 2 places (TrainingViewModel.kt:609-619, SessionRunner.kt:494, 654)
- `completedSubLessonCount` calculated then recalculated (SessionRunner.kt:487, 492)
- `currentIndex` mutated in 8+ places (SessionRunner.kt:243, 594, 680, 687, 730, 756, 768)
- Same state owned by both ViewModel and SessionRunner

**Why this is a problem:**
- Easy to introduce inconsistencies
- No single source of truth
- Hard to debug which calculation is correct
- Risk of off-by-one errors
- Violates DRY principle

**Blast radius:**
- Sub-lesson navigation
- Progress calculation
- Session completion detection
- Lesson completion detection

**Evidence:**
```kotlin
// TrainingViewModel.kt:609-619
val completedCount = progressTracker.calculateCompletedSubLessons(
    lessonId = selectedLessonId,
    lessons = lessons,
    mastery = mastery,
    schedule = schedule
)
val activeSubLessonIndex = if (completedCount < totalSubLessons) completedCount else (totalSubLessons - 1)

// SessionRunner.kt:492, 494
val actualCompletedCount = calculateCompletedSubLessons(...)
val finalActiveIndex = activeSubLessonIndex.coerceAtMost(actualCompletedCount)

// SessionRunner.kt:487
val nextCompleted = completedSubLessonCount + 1

// SessionRunner.kt:594, 680, 687, 730, 756, 768
currentIndex = 0 // Reset in 4 places
currentIndex = nextIndex // Increment in 2 places
currentIndex = prevIndex // Decrement in 2 places
```

**Proposed direction:**
- Single owner for each piece of state
- Calculated values cached in one place
- State mutations centralized
- Clear ownership through types

**Must-have tests before refactor:**
- Test sub-lesson calculation consistency
- Test currentIndex bounds checking
- Test completed count updates
- Test all state transitions

---

### Risk 7.2: Daily Cursor State Ownership Ambiguous
**Risk level:** MEDIUM

**Current behavior:**
- Cursor stored in PackDailyCursorStore (Models.kt:383-397)
- Cursor cached in DailyPracticeCoordinator's MutableStateFlow
- TrainingViewModel has `getCursor()` method to bypass stale combine flow (TrainingViewModel.kt:1422)
- Comment explains workaround: "combine flow may not have propagated yet" (lines 1416-1421)

**Why this is a problem:**
- Three sources of truth for same data
- Stale data requires workarounds
- Easy to read wrong cursor value
- Breaks Repeat after restart if cursor wrong
- Violates single source of truth principle

**Blast radius:**
- Daily practice Repeat feature
- Cursor advancement
- Progress persistence
- Session reconstruction

**Evidence:**
```kotlin
// TrainingViewModel.kt:1414-1427
private fun saveProgress() {
    val state = uiState.value
    // Read daily cursor directly from coordinator to avoid combine flow staleness.
    // The combine() flow that produces uiState merges _coreState with coordinator's
    // dailyState; after updateCursor() the MutableStateFlow is updated immediately
    // but the downstream combine may not have propagated yet, so uiState.value can
    // contain a stale cursor. This caused storeFirstSessionCardIds() to write a
    // cursor missing firstSessionDate/cardIds to disk, breaking Repeat after restart.
    val actualCursor = dailyPracticeCoordinator.getCursor()
    val stateToSave = if (state.daily.dailyCursor != actualCursor) {
        state.copy(daily = state.daily.copy(dailyCursor = actualCursor))
    } else {
        state
    }
    // ...
}

// Models.kt:383-397
data class PackDailyCursorState(
    val sentenceOffset: Int = 0,
    val verbOffset: Int = 0,
    val currentLessonIndex: Int = 0,
    val firstSessionSentenceCardIds: List<String> = emptyList(),
    val firstSessionVerbCardIds: List<String> = emptyList(),
    val firstSessionDate: LocalDate? = null
)
```

**Proposed direction:**
- Single source of truth - coordinator owns cursor
- Store is persistence layer only
- Eliminate combine flow staleness
- Make cursor updates explicit

**Must-have tests before refactor:**
- Test cursor persistence after restart
- Test Repeat uses correct cursor
- Test cursor advancement
- Test no stale cursor reads

---

## Risk Category 8: Test-Only Workarounds Leaking into Production

### Risk 8.1: testMode Flag in Production Code
**Risk level:** MEDIUM

**Current behavior:**
- `testMode` flag in CardSessionState (Models.kt)
- Passed through multiple layers: ViewModel -> SessionRunner -> AnswerValidator
- Used to bypass validation: `if (testMode) return true` (AnswerValidator.kt:69)
- Used in elite unlock calculation: `resolveEliteUnlocked(lessons, testMode)` (SessionRunner.kt:897)

**Why this is a problem:**
- Test infrastructure in production code
- Risk of shipping with testMode=true
- Different code paths in test vs production
- Hard to guarantee test covers production behavior

**Blast radius:**
- Answer validation
- Elite unlock logic
- All tests that use testMode
- Production behavior if flag mis-set

**Evidence:**
```kotlin
// Models.kt - CardSessionState
data class CardSessionState(
    val testMode: Boolean = false,
    // ...
)

// AnswerValidator.kt:69
fun validateAnswer(input: String, card: SessionCard): Boolean {
    if (testMode) return true
    // ...
}

// SessionRunner.kt:897
fun resolveEliteUnlocked(lessons: List<Lesson>, testMode: Boolean): Boolean {
    // ...
}
```

**Proposed direction:**
- Remove testMode from production code
- Use dependency injection for test doubles
- Test through public APIs only

**Must-have tests before refactor:**
- Test all validation logic without testMode
- Test elite unlock without testMode
- Test production behavior matches test behavior

---

## Risk Category 9: State Mutation Scatter

### Risk 9.1: currentIndex Mutated in 8+ Places
**Risk level:** HIGH

**Current behavior:**
- `currentIndex` mutated in 8+ methods across SessionRunner
- Incremented in `nextCardInternal()` (line 680)
- Decremented in `prevCard()` (line 703)
- Incremented in `navigateNext()` (line 730)
- Decremented in `navigatePrev()` (line 756)
- Reset to 0 in 4 places (lines 497, 658, 768, 944, 1046, 1459)
- Bounds checked in multiple places (lines 243, 680, 703)

**Why this is a problem:**
- No single place to track all mutations
- Easy to miss bounds check
- Hard to reason about current card
- Risk of index out of bounds
- Cannot enforce invariants

**Blast radius:**
- Card navigation
- Current card display
- Session completion detection
- Bounds checking

**Evidence:**
```kotlin
// SessionRunner.kt - currentIndex mutations
line 243:  currentIndex = currentIndex.coerceIn(0, sessionCards.lastIndex)
line 497:  currentIndex = 0
line 594:  currentIndex = 0
line 600:  sessionState = if (nextCard != null) SessionState.ACTIVE else SessionState.PAUSED
line 663:  currentIndex = 0
line 680:  currentIndex = nextIndex
line 687:  currentIndex = nextIndex
line 703:  currentIndex = prevIndex
line 730:  currentIndex = nextIndex
line 756:  currentIndex = prevIndex
line 768:  currentIndex = 0
line 944:  currentIndex = 0
line 1046: currentIndex = 0
line 1459: currentIndex = currentIndex.coerceIn(0, sessionCards.lastIndex)
```

**Proposed direction:**
- Single `setCurrentIndex(index)` method that enforces invariants
- All mutations go through this method
- State machine for valid index transitions

**Must-have tests before refactor:**
- Test all navigation paths
- Test bounds checking
- Test index reset on completion
- Test concurrent navigation

---

### Risk 9.2: SessionState Mutated in 10+ Places
**Risk level:** HIGH

**Current behavior:**
- SessionState enum mutated in 10+ places
- ACTIVE set in 3 places (lines 197, 234, 318)
- PAUSED set in 6 places (lines 227, 497, 658, 732, 758, 813)
- HINT_SHOWN set in 2 places (lines 372, 824)
- State checks scattered throughout code

**Why this is a problem:**
- No single place to see state machine
- Easy to miss state transition
- Hard to enforce valid transitions
- Risk of invalid state combinations
- Cannot visualize state machine

**Blast radius:**
- Session lifecycle
- Timer behavior
- Voice triggering
- Hint display
- UI state

**Evidence:**
```kotlin
// SessionRunner.kt - SessionState mutations
line 187: sessionState = SessionState.PAUSED
line 198: sessionState = SessionState.ACTIVE
line 227: sessionState = SessionState.PAUSED
line 234: if (stateAccess.uiState.value.cardSession.sessionState == SessionState.ACTIVE) return emptyList()
line 314: if (state.cardSession.sessionState != SessionState.ACTIVE) {
line 318: sessionState = SessionState.ACTIVE
line 372: sessionState = SessionState.HINT_SHOWN
line 466: sessionState = SessionState.PAUSED
line 497: sessionState = SessionState.PAUSED
line 556: sessionState = SessionState.PAUSED
line 572: sessionState = SessionState.ACTIVE
line 600: sessionState = if (nextCard != null) SessionState.ACTIVE else SessionState.PAUSED
line 636: sessionState = SessionState.PAUSED
line 662: sessionState = SessionState.PAUSED
line 687: sessionState = SessionState.ACTIVE
line 732: sessionState = SessionState.PAUSED
line 758: sessionState = SessionState.PAUSED
line 768: sessionState = SessionState.PAUSED
line 781: sessionState = SessionState.PAUSED
line 795: sessionState = SessionState.ACTIVE
line 813: sessionState = SessionState.PAUSED
line 824: sessionState = SessionState.HINT_SHOWN
line 892: sessionState = SessionState.PAUSED
```

**Proposed direction:**
- Explicit state machine class
- Valid transitions enforced
- Single method to change state
- State transition logging

**Must-have tests before refactor:**
- Test all valid state transitions
- Test invalid transitions are blocked
- Test state combinations
- Test state restoration from persistence

---

## Risk Category 10: Event-Driven Complexity

### Risk 10.1: Implicit Control Flow Through Event Lists
**Risk level:** HIGH

**Current behavior:**
- SessionRunner returns `List<SessionEvent>` from 20+ methods
- Each method returns 1-6 events
- ViewModel processes events via `handleSessionEvents()` (TrainingViewModel.kt:1299)
- Control flow split between SessionRunner and ViewModel
- Example: `submitNormalLastCard` returns 6 events (SessionRunner.kt:505-512)

**Why this is a problem:**
- Cannot trace execution by reading code
- Must jump between files to understand behavior
- Easy to miss event handling
- Event list can get out of sync
- No compile-time guarantee events are handled

**Blast radius:**
- All session operations
- Event handling
- Progress saving
- Flower refresh
- Streak updates

**Evidence:**
```kotlin
// SessionEvent.kt:18-38
sealed class SessionEvent {
    object SaveProgress : SessionEvent()
    object RefreshFlowerStates : SessionEvent()
    object UpdateStreak : SessionEvent()
    data class UpdateStreakForType(val type: PracticeType) : SessionEvent()
    object BuildSessionCards : SessionEvent()
    object PlaySuccess : SessionEvent()
    object PlayError : SessionEvent()
    data class RecordCardShow(val card: SessionCard) : SessionEvent()
    data class MarkSubLessonCardsShown(val cards: List<SessionCard>) : SessionEvent()
    object CheckAndMarkLessonCompleted : SessionEvent()
    data class CalculateCompletedSubLessons(val lessonId: LessonId, val callback: (Int) -> Unit) : SessionEvent()
    data class GetMastery(val lessonId: String, val langId: String, val callback: (LessonMasteryState?) -> Unit) : SessionEvent()
    data class GetSchedule(val lessonId: String, val callback: (LessonSchedule?) -> Unit) : SessionEvent()
    data class RebuildSchedules(val lessons: List<Lesson>) : SessionEvent()
    data class Composite(val events: List<SessionEvent>) : SessionEvent()
}

// SessionRunner.kt:505-512
return SubmitResult.Accepted.copy(
    needsSubLessonComplete = true
) to listOf(
    SessionEvent.MarkSubLessonCardsShown(sessionCards),
    SessionEvent.BuildSessionCards,
    SessionEvent.CheckAndMarkLessonCompleted,
    SessionEvent.RefreshFlowerStates,
    SessionEvent.UpdateStreak,
    SessionEvent.SaveProgress
)

// TrainingViewModel.kt:1299-1330
private fun handleSessionEvents(events: List<SessionEvent>) {
    events.forEach { event ->
        when (event) {
            is SessionEvent.SaveProgress -> saveProgress()
            is SessionEvent.RefreshFlowerStates -> refreshFlowerStates()
            is SessionEvent.UpdateStreak -> updateStreak()
            // ... 10 more event handlers
        }
    }
}
```

**Proposed direction:**
- Direct method calls instead of events where possible
- Events only for cross-boundary communication
- Use return values for results, not side effects

**Must-have tests before refactor:**
- Test all event paths are handled
- Test event order is correct
- Test no events are dropped
- Test event composition (Composite events)

---

## Summary by Risk Level

### HIGH Risk (9 issues)
1. TrainingViewModel God Object (1570 lines)
2. State Mutation Scatter - currentIndex
3. State Mutation Scatter - SessionState
4. Query Callback Pattern in Events
5. Circular Dependencies Through ViewModel
6. Sub-lesson State Calculated in Multiple Places
7. Event-Driven Complexity - Implicit Control Flow
8. Timer Side Effects - Independent State Mutation
9. Mastery Counting Logic Duplicated

### MEDIUM Risk (6 issues)
1. UI Calculates Business Metrics
2. Timer System Embedded in SessionRunner
3. saveProgress Scattered Across Training Logic
4. Mastery Counting Logic Duplicated
5. Daily Cursor State Ownership Ambiguous
6. testMode Flag in Production Code

### LOW Risk (3 issues)
1. UI Decides Session Completion Logic
2. Store Access Embedded in Business Logic
3. masteryStore.flush() Called from Multiple Locations

---

## Refactoring Priority

### Phase 1: Test Coverage (Pre-req)
1. Add regression tests for all public TrainingViewModel methods
2. Add unit tests for SessionRunner state transitions
3. Add integration tests for event handling
4. Add tests for timer behavior
5. Add tests for state mutation consistency

### Phase 2: Break Circular Dependencies
1. Extract interfaces for coordinators
2. Use dependency injection
3. Eliminate callback events
4. Make data flow unidirectional

### Phase 3: Consolidate State Mutations
1. Single source of truth for each piece of state
2. Centralized state mutation methods
3. Enforce invariants
4. State machine for SessionState

### Phase 4: Extract Features from ViewModel
1. PomodoroViewModel
2. AudioViewModel
3. SettingsViewModel
4. Keep TrainingViewModel focused on session lifecycle

### Phase 5: Simplify Event Flow
1. Direct method calls where possible
2. Events only for cross-boundary communication
3. Return values instead of side effects

### Phase 6: Extract Timer System
1. TimerCoordinator class
2. Clock abstraction for testing
3. Decouple from state mutation

---

## Testing Strategy Before Refactor

### Critical Paths to Test
1. All session types (LESSON, REVIEW, DAILY, VERB_DRILL, BOSS)
2. All navigation actions (next, prev, navigate, select sub-lesson)
3. All answer submissions (correct, wrong, hint)
4. All state transitions (ACTIVE, PAUSED, HINT_SHOWN)
5. All completion scenarios
6. All timer behaviors
7. All progress persistence points
8. All event handling paths

### Test Coverage Goals
- SessionRunner: 80%+ coverage
- TrainingViewModel: 70%+ coverage
- State transitions: 100% coverage
- Event handling: 100% coverage
- Timer behavior: 90%+ coverage

---

## Conclusion

The Training Core has significant architectural debt but is well-understood. The main issues are:

1. **TrainingViewModel is too large** - needs feature extraction
2. **State mutations are scattered** - needs consolidation
3. **Events create implicit flow** - needs simplification
4. **Circular dependencies** - needs inversion of control

**Recommended approach:** Incremental refactoring with test coverage first, then extract features and consolidate state.

**Risk assessment:** HIGH risk if refactored without tests, MEDIUM risk with test coverage, LOW risk with incremental approach.
