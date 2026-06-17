# Verb/Vocab Drill Architecture Map

**Analysis Date:** 2026-05-22
**Scope:** VerbDrill and VocabDrill systems (ViewModels, Stores, Session Cards)
**Purpose:** Factual documentation of current drill architecture for safe refactoring

---

## Active User Flows

### Verb Drill Flow

1. **Entry:** User navigates to VerbDrillScreen → `VerbDrillViewModel.reloadForPack()` or `reloadForLanguage()`
2. **Selection:** User filters by tense/group → `selectTense()` / `selectGroup()` → `updateProgressDisplay()`
3. **Session Start:** User clicks Start → `startSession()` → batch of cards selected → `saveLastSession()` → navigation to TrainingScreen
4. **Answering:** User types answer → TrainingScreen validates → calls `VerbDrillViewModel.submitCorrectAnswer()` or `markCardCompleted()`
5. **Progress Persistence:** Each answer → `persistCardProgress()` → `VerbDrillStore.upsertComboProgress()`
6. **Session Exit:** User exits → `exitSession()` → `saveLastSessionState()` → SessionCard shown on VerbDrillScreen
7. **SessionCard Actions:**
   - **Repeat:** `onRepeatSession()` → replays same card order from `lastSession.sessionCardIds`
   - **Continue:** `onResumeSession()` → loads next cards excluding `lastSession.todayShownCardIds`
   - **Reset:** `onStartFresh()` → deletes last session, keeps progress

**File Evidence:** `VerbDrillViewModel.kt:148-513`, `VerbDrillScreen.kt:101-127`

### Vocab Drill Flow

1. **Entry:** User navigates to VocabDrillScreen → `VocabDrillViewModel.reloadForPack()` or `reloadForLanguage()`
2. **Selection:** User filters by POS/rank → `selectPos()` / `setRankRange()` → `updateCounts()`
3. **Session Start:** User clicks Start → `startSession()` → due cards selected by SRS algorithm
4. **Answering:**
   - User flips card → `flipCard()`
   - User rates answer (AGAIN/HARD/GOOD/EASY) → `answerRating()` → SRS interval updated
5. **Progress Persistence:** Each rating → `WordMasteryStore.upsertMastery()` → interval step changed
6. **Session Exit:** User exits → `exitSession()` → fire streak recorded if any non-AGAIN ratings
7. **No SessionCard:** VocabDrill has no repeat/continue/reset functionality (as of current implementation)

**File Evidence:** `VocabDrillViewModel.kt:69-354`

---

## Main Classes and Responsibilities

### VerbDrillViewModel

**File:** `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt` (1035 lines)

**Key Responsibilities:**
- Card loading from CSV files (via `VerbDrillCsvParser`)
- Session batch management (random vs frequency-sorted)
- Progress tracking per tense/group combo
- Last session persistence for resume/repeat/reset
- TTS integration for verb pronunciation
- Bad sentence flagging
- Speed tracking (WPM calculation)

**State:**
```kotlin
// Line 88-104
private val _uiState = MutableStateFlow(VerbDrillUiState())
private var allCards: List<VerbDrillCard> = emptyList()
private var progressMap: Map<String, VerbDrillComboProgress> = emptyMap()
private var currentPackId: String? = null
private var packIdForCardId: Map<String, String> = emptyMap()
private var sessionSize: Int = 10
private var tenseInfoMap: Map<String, TenseInfo> = emptyMap()
```

**Key Methods:**
- `reloadForLanguage(languageId: String)` - Line 148: Loads cards for language, finds pack with verb drill files
- `reloadForPack(packId: String)` - Line 183: Creates pack-scoped store, loads cards
- `startSession()` - Line 449: Selects batch of cards, saves last session state
- `submitCorrectAnswer()` - Line 524: Advances card, increments correct count, persists progress
- `markCardCompleted()` - Line 566: Marks card as completed (hint/skip), persists progress
- `persistCardProgress(card: VerbDrillCard)` - Line 604: Updates progress in store
- `onRepeatSession()` - Line 759: Replays saved card batch in same order
- `onResumeSession()` - Line 734: Loads next cards excluding shown ones
- `onStartFresh()` - Line 809: Deletes last session, clears filters
- `saveLastSessionState(session: VerbDrillSessionState)` - Line 704: Persists session for resume

**Test Support:**
- `injectTestCards(cards: List<VerbDrillCard>)` - Line 68: Test-only card injection
- Constructor `VerbDrillViewModel(application, testStore)` - Line 59: Test-only store injection

**File Evidence:** `VerbDrillViewModel.kt:44-1035`

---

### VocabDrillViewModel

**File:** `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt` (550 lines)

**Key Responsibilities:**
- Word loading from CSV files (via `ItalianDrillVocabParser`)
- Spaced repetition scheduling (SRS)
- Session management (due word selection)
- Rating-based progress updates
- Voice input integration (via Android RecognizerIntent)
- Bad sentence flagging
- Mastery tracking per word

**State:**
```kotlin
// Line 43-57
private val _uiState = MutableStateFlow(VocabDrillUiState())
private var allWords: List<VocabWord> = emptyList()
private var masteryMap: Map<String, WordMasteryState> = emptyMap()
private var _hasRatedCards = false
private var activePackId: String? = null
private var sessionSize: Int = 10
```

**Key Methods:**
- `reloadForPack(packId: String, languageId: String)` - Line 69: Loads words for pack, scopes mastery store
- `reloadForLanguage(languageId: String)` - Line 91: Loads words for language
- `startSession()` - Line 229: Selects due words by SRS algorithm
- `answerRating(rating: SrsRating)` - Line 273: Updates mastery based on rating
- `flipCard()` - Line 256: Toggles card flip state
- `handleVoiceResult(recognizedText: String)` - Line 400: Processes voice recognition
- `exitSession()` - Line 337: Records fire streak, clears session

**SRS Algorithm:**
```kotlin
// Line 266-271
private val ratingIntervalDelta = mapOf(
    SrsRating.AGAIN to -100,  // reset to step 0
    SrsRating.HARD to 0,      // stay same step
    SrsRating.GOOD to 1,      // advance 1 step
    SrsRating.EASY to 2       // advance 2 steps
)
```

**File Evidence:** `VocabDrillViewModel.kt:30-550`

---

### Drill Stores

#### VerbDrillStore

**File:** `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt` (313 lines)

**Data Structures:**
```kotlin
// Line 15-22 - VerbDrillComboProgress (progress per tense/group combo)
data class VerbDrillComboProgress(
    val group: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIds: Set<String> = emptySet(),
    val todayShownCardIds: Set<String> = emptySet(),
    val lastDate: String = ""
)

// Line 47-55 - VerbDrillLastSessionState (for resume/repeat/reset)
data class VerbDrillLastSessionState(
    val selectedTense: String?,
    val selectedGroup: String?,
    val sortByFrequency: Boolean,
    val todayShownCardIds: Set<String> = emptySet(),
    val sessionCardIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val packId: String? = null
)
```

**Persistence Files:**
- Progress: `grammarmate/drills/{packId}/verb_drill_progress.yaml` (pack-scoped) or `grammarmate/verb_drill_progress_{languageId}.yaml` (legacy)
- Last Session: `grammarmate/drills/{packId}/verb_drill_last_session.yaml` or `grammarmate/verb_drill_last_session_{languageId}.yaml`

**Key Operations:**
- `loadProgress()` - Line 136: Loads combo progress from YAML
- `upsertComboProgress(key, progress)` - Line 194: Updates single combo progress
- `saveLastSession(session)` - Line 334: Persists last session state
- `loadLastSession()` - Line 290: Loads last session for resume
- `deleteLastSession()` - Line 365: Clears saved session

**Cache Strategy:**
- Progress cache in memory (`progressCache`), invalidated on save
- Cards cache keyed by `packId:languageId`, never invalidated (files don't change at runtime)
- Last session cache in memory (`lastSessionCache`)

**File Evidence:** `VerbDrillStore.kt:12-375`

---

#### WordMasteryStore

**File:** `app/src/main/java/com/alexpo/grammermate/data/WordMasteryStore.kt` (199 lines)

**Data Structures:**
```kotlin
// Line 21-29 - WordMasteryState (per-word mastery)
data class WordMasteryState(
    val wordId: String,
    val intervalStepIndex: Int = 0,         // 0-9, maps to INTERVAL_LADDER_DAYS
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val lastReviewDateMs: Long = 0L,
    val nextReviewDateMs: Long = 0L,
    val isLearned: Boolean = false           // reached LEARNED_THRESHOLD (step 3)
)
```

**Persistence Files:**
- Mastery: `grammarmate/drills/{packId}/word_mastery.yaml` (pack-scoped) or `grammarmate/word_mastery.yaml` (legacy)

**Key Operations:**
- `loadAll()` - Line 59: Loads all mastery records
- `upsertMastery(state)` - Line 128: Updates single word mastery
- `getDueWords()` - Line 142: Returns words due for review
- `getMasteredCount(pos)` - Line 154: Counts mastered words by POS

**Cache Strategy:**
- In-memory cache (`cache`), loaded on first access
- Cache invalidation via `invalidateCache()`

**File Evidence:** `WordMasteryStore.kt:9-199`

---

## Business Rules Found

### VerbDrill Session Card Semantics

**Session Card Structure:**
```kotlin
// VerbDrillSessionState - Line 24-30 in VerbDrillCard.kt
data class VerbDrillSessionState(
    val cards: List<VerbDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false
)
```

**Card "Shown" Rules:**
- A card is counted as **shown** only after `submitCorrectAnswer()` (Check) or `markCardCompleted()` (hint/skip)
- Navigation-only cards (prev/next without Check) are **NOT** counted as shown
- Evidence: `VerbDrillViewModel.kt:524-599` (only `persistCardProgress()` marks cards as shown)

**Repeat Semantics:**
- Repeat replays the **full saved batch** in the same order from `lastSession.sessionCardIds`
- Does **NOT** filter out checked cards
- Evidence: `VerbDrillViewModel.kt:759-804` (`onRepeatSession()`)

**Continue Semantics:**
- Continue loads **next cards from the pool**, excluding `lastSession.todayShownCardIds`
- Does **NOT** resume from the same point - starts a fresh batch
- Evidence: `VerbDrillViewModel.kt:734-753` (`onResumeSession()`)

**Reset Semantics:**
- Reset deletes `lastSession` file
- **Does NOT** delete progress (`todayShownCardIds` remain in progress file)
- Allows starting fresh from the beginning
- Evidence: `VerbDrillViewModel.kt:809-824` (`onStartFresh()`)

**Batch Selection Rules:**
- Random mode: `filtered.shuffled().take(sessionSize)`
- Frequency mode: `filtered.sortedBy { it.rank }.take(sessionSize)`
- Excludes already shown cards: `filtered.filter { it.id !in allShownCardIds }`
- Evidence: `VerbDrillViewModel.kt:449-499` (`startSession()`)

---

### VocabDrill Session Card Semantics

**Session Card Structure:**
```kotlin
// VocabDrillSessionState - Line 79-91 in VocabWord.kt
data class VocabDrillSessionState(
    val cards: List<VocabDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false,
    val isFlipped: Boolean = false,
    val direction: VocabDrillDirection = VocabDrillDirection.IT_TO_RU,
    val voiceAttempts: Int = 0,
    val voiceRecognizedText: String? = null,
    val voiceResult: VoiceResult? = null,
    val voiceCompleted: Boolean = false
)
```

**Card "Shown" Rules:**
- No "shown" tracking - uses SRS scheduling instead
- Cards become "due" based on `nextReviewDateMs`
- Evidence: `VocabDrillViewModel.kt:189-192` (due word calculation)

**SRS Algorithm:**
- Interval ladder: `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS` (0, 1, 3, 7, 14, 30, 60, 90, 180, 365)
- Rating mapping:
  - AGAIN → step 0 (reset)
  - HARD → same step
  - GOOD → step + 1
  - EASY → step + 2
- Evidence: `VocabDrillViewModel.kt:266-305` (`answerRating()`)

**No Repeat/Continue/Reset:**
- VocabDrill does not have SessionCard functionality
- No last session persistence
- Evidence: `VocabDrillViewModel.kt` (no `loadLastSession`, `saveLastSession`, etc.)

---

## Infrastructure Dependencies

### Shared Services (Both Drills)

**AppContainer:**
- `lessonStore: LessonStore` - Provides CSV file paths for drill data
- `progressStore: ProgressStore` - Main app progress (not drill-specific)
- `badSentenceStore: BadSentenceStore` - Shared bad sentence tracking
- `ttsEngine: TtsEngine` - Text-to-speech
- `streakStore: StreakStore` - Fire streak recording
- `configStore: ConfigStore` - Session size configuration

**Evidence:** `VerbDrillViewModel.kt:47-86`, `VocabDrillViewModel.kt:33-41`

### Pack Scoping

**VerbDrill:**
- Store scoped to pack: `container.verbDrillStore(packId)`
- File path: `grammarmate/drills/{packId}/verb_drill_progress.yaml`
- Cards loaded from: `lessonStore.getVerbDrillFiles(packId, languageId)`
- Evidence: `VerbDrillViewModel.kt:183-206`, `VerbDrillStore.kt:54-65`

**VocabDrill:**
- Store scoped to pack: `container.wordMasteryStore(packId)`
- File path: `grammarmate/drills/{packId}/word_mastery.yaml`
- Words loaded from: `lessonStore.getVocabDrillFiles(packId, languageId)`
- Evidence: `VocabDrillViewModel.kt:69-84`, `WordMasteryStore.kt:38-48`

### Progress Tracking Integration

**VerbDrill:**
- **Does NOT** integrate with main `ProgressStore`
- Has separate `VerbDrillComboProgress` per tense/group combo
- Fire streak recorded via `streakStore.recordPracticeTypeCompletion(languageId, PracticeType.VERB)`
- Evidence: `VerbDrillViewModel.kt:632-639`

**VocabDrill:**
- **Does NOT** integrate with main `ProgressStore`
- Has separate `WordMasteryState` per word
- Fire streak recorded via `streakStore.recordPracticeTypeCompletion(languageId, PracticeType.VOCAB)`
- Evidence: `VocabDrillViewModel.kt:337-354`

---

## State Mutations

### VerbDrill Session State

**Initialization:**
```kotlin
// Line 489 in VerbDrillViewModel.kt
val session = VerbDrillSessionState(cards = selected)
```

**On Correct Answer:**
```kotlin
// Line 538-547 in VerbDrillViewModel.kt
session = session.copy(
    currentIndex = nextIndex,
    correctCount = updatedCorrect,
    isComplete = isComplete
)
```

**On Card Completed (Hint/Skip):**
```kotlin
// Line 582-589 in VerbDrillViewModel.kt
session = session.copy(
    currentIndex = nextIndex,
    incorrectCount = updatedIncorrect,
    isComplete = isComplete
)
```

**Progress Persistence:**
```kotlin
// Line 604-626 in VerbDrillViewModel.kt
private fun persistCardProgress(card: VerbDrillCard) {
    val comboKey = "${uiState.selectedGroup ?: ""}|${uiState.selectedTense ?: ""}"
    val everShown = (existing?.everShownCardIds ?: emptySet()) + card.id
    val todayShown = (existing?.todayShownCardIds ?: emptySet()) + card.id
    val updatedProgress = VerbDrillComboProgress(
        group = uiState.selectedGroup ?: "",
        tense = uiState.selectedTense ?: "",
        totalCards = totalCards,
        everShownCardIds = everShown,
        todayShownCardIds = todayShown,
        lastDate = java.time.LocalDate.now().toString()
    )
    progressMap = progressMap.toMutableMap().apply { this[comboKey] = updatedProgress }
    verbDrillStore.upsertComboProgress(comboKey, updatedProgress)
}
```

**Last Session State:**
```kotlin
// Line 502-512 in VerbDrillViewModel.kt
val lastSessionState = VerbDrillLastSessionState(
    selectedTense = state.selectedTense,
    selectedGroup = state.selectedGroup,
    sortByFrequency = state.sortByFrequency,
    todayShownCardIds = allShownCardIds,
    sessionCardIds = selected.map { it.id },
    currentIndex = 0,
    packId = currentPackId
)
verbDrillStore.saveLastSession(lastSessionState)
```

---

### VocabDrill Session State

**Initialization:**
```kotlin
// Line 246-250 in VocabDrillViewModel.kt
session = VocabDrillSessionState(
    cards = dueCards,
    direction = it.drillDirection
)
```

**On Rating:**
```kotlin
// Line 292-299 in VocabDrillViewModel.kt
val updatedMastery = card.mastery.copy(
    intervalStepIndex = newStepIndex,
    correctCount = card.mastery.correctCount + (if (rating != SrsRating.AGAIN) 1 else 0),
    incorrectCount = card.mastery.incorrectCount + (if (rating == SrsRating.AGAIN) 1 else 0),
    lastReviewDateMs = now,
    nextReviewDateMs = newNextReview,
    isLearned = isLearned
)
masteryMap = masteryMap.toMutableMap().apply { this[card.word.id] = updatedMastery }
masteryStore.upsertMastery(updatedMastery)
```

**Card Advancement:**
```kotlin
// Line 307-327 in VocabDrillViewModel.kt
session = session.copy(
    currentIndex = nextIndex,
    correctCount = newCorrect,
    incorrectCount = newIncorrect,
    isComplete = isComplete,
    isFlipped = false,
    voiceAttempts = 0,
    voiceRecognizedText = null,
    voiceResult = null,
    voiceCompleted = false
)
```

---

## Test Coverage

### Unit Tests

**VerbDrillSessionCardRegressionTest** (`app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt`)

**Test Requirements (from CLAUDE.md):**
- Use deterministic cards with stable IDs and `rank = index`
- Render `VerbDrillScreen` and `TrainingScreen` or a small harness
- Start through UI using `verb_start_button`
- Complete cards through UI using `input_field` and `check_button`
- Navigate without completion through UI using `next_button` / `prev_button`
- Exercise SessionCard actions through UI using `session_card`, `repeat_button`, `continue_button`, `reset_button`
- Read ViewModel/store state only for answers and assertions

**Current Tests:**
1. `repeat_replays_saved_batch_in_same_order()` - Line 63: Verifies Repeat restores exact card order
2. `continue_random_mode_excludes_checked_cards_but_not_navigation_only_cards` - Line 87: Verifies Continue excludes only checked cards
3. `continue_frequency_mode_resumes_with_next_ranked_cards_in_order` - Line 123: Verifies Continue maintains frequency order
4. `reset_clears_saved_session_keeps_progress_and_allows_ranked_restart_from_beginning` - Line 155: Verifies Reset deletes session but keeps progress

**Test Harness:**
- Uses `FakeVerbDrillStore` for controlled test data
- Creates deterministic test cards with ranks 1-12
- Simulates full UI flow: selection → session → training → exit → SessionCard actions
- Evidence: `VerbDrillSessionCardRegressionTest.kt:40-359`

**No VocabDrill UI regression tests** - VocabDrill has no SessionCard functionality to test

---

## Risks / Unclear Points

### Architectural Concerns

1. **ViewModel Size (VerbDrillViewModel):**
   - **Issue:** 1035 lines, violates CLAUDE.md guidance ("~1500 lines. Decompose helpers to `feature/` when adding logic")
   - **Risk:** Hard to maintain, test, and understand
   - **Evidence:** `VerbDrillViewModel.kt:1-1035`

2. **No Progress Integration:**
   - **Issue:** Both drills have separate progress systems, don't integrate with main `ProgressStore`
   - **Risk:** Inconsistent progress tracking, unclear "mastery" definition
   - **Evidence:** `VerbDrillViewModel.kt:632-639`, `VocabDrillViewModel.kt:337-354`

3. **VocabDrill Lacks SessionCard:**
   - **Issue:** VerbDrill has resume/repeat/reset, VocabDrill doesn't
   - **Risk:** Inconsistent UX, users expect same functionality
   - **Evidence:** `VocabDrillViewModel.kt` (no SessionCard methods)

4. **Pack Scoping Complexity:**
   - **Issue:** Both ViewModels handle pack-scoped vs legacy mode logic
   - **Risk:** Edge cases, migration bugs, stale data
   - **Evidence:** `VerbDrillViewModel.kt:148-206`, `VocabDrillViewModel.kt:69-106`

5. **Test Store Injection Pattern:**
   - **Issue:** VerbDrillViewModel has test-only constructor and `injectTestCards()`
   - **Risk:** Test-only code in production, potential misuse
   - **Evidence:** `VerbDrillViewModel.kt:59-80`

6. **State Mutation Complexity:**
   - **Issue:** VerbDrill has multiple state mutation paths (correct, completed, navigation)
   - **Risk:** Inconsistent state, lost updates
   - **Evidence:** `VerbDrillViewModel.kt:524-599`

7. **Cache Invalidation:**
   - **Issue:** Both stores use in-memory caching with manual invalidation
   - **Risk:** Stale data if external changes occur
   - **Evidence:** `VerbDrillStore.kt:74-84`, `WordMasteryStore.kt:52-54`

8. **Last Session Validation:**
   - **Issue:** VerbDrill validates last session on load (pack mismatch, card IDs)
   - **Risk:** May discard valid sessions in edge cases
   - **Evidence:** `VerbDrillViewModel.kt:328-346`

9. **Session Size Configuration:**
   - **Issue:** Both ViewModels read session size from config on every reload
   - **Risk:** Inconsistent batch sizes if config changes mid-session
   - **Evidence:** `VerbDrillViewModel.kt:138,149,185`, `VocabDrillViewModel.kt:70,92`

10. **Fire Streak Logic:**
    - **Issue:** Different conditions for recording streak (VerbDrill: correct >= sessionSize - badCount; VocabDrill: any non-AGAIN rating)
    - **Risk:** Inconsistent streak tracking, user confusion
    - **Evidence:** `VerbDrillViewModel.kt:632-639`, `VocabDrillViewModel.kt:337-354`

---

## Evidence

### File References

**VerbDrillViewModel:**
- Card loading: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:148-327`
- Session management: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:449-652`
- Progress persistence: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:604-626`
- Last session handling: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:704-833`
- Test support: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:59-80`

**VocabDrillViewModel:**
- Word loading: `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt:69-167`
- Session management: `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt:229-251`
- SRS logic: `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt:266-330`
- Voice input: `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt:376-479`

**VerbDrillStore:**
- Data structures: `app/src/main/java/com/alexpo/grammermate/data/VerbDrillCard.kt:15-55`
- Progress operations: `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt:136-199`
- Last session operations: `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt:290-375`

**WordMasteryStore:**
- Data structures: `app/src/main/java/com/alexpo/grammermate/data/VocabWord.kt:21-48`
- Mastery operations: `app/src/main/java/com/alexpo/grammermate/data/WordMasteryStore.kt:59-179`

**UI Screens:**
- VerbDrillScreen: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt:64-175`
- VocabDrillScreen: `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillScreen.kt`

**Tests:**
- VerbDrillSessionCardRegressionTest: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:40-359`

---

## Summary

**VerbDrill and VocabDrill are fundamentally different systems:**

1. **VerbDrill:** Batch-based drill with last session persistence, repeat/continue/reset, progress tracking per tense/group combo
2. **VocabDrill:** SRS-based drill with rating system, no last session persistence, progress tracking per word

**Key architectural differences:**
- VerbDrill uses "shown card" tracking, VocabDrill uses SRS scheduling
- VerbDrill has SessionCard functionality, VocabDrill doesn't
- VerbDrill tracks progress per combo (tense/group), VocabDrill per word
- VerbDrill has complex session state mutations, VocabDrill has simpler rating-based updates

**Shared patterns:**
- Both use pack-scoped stores
- Both have separate progress from main ProgressStore
- Both record fire streaks to StreakStore
- Both use YAML persistence with AtomicFileWriter
- Both support bad sentence flagging

**Major risks:**
- VerbDrillViewModel size (1035 lines)
- Inconsistent UX (VocabDrill lacks SessionCard)
- No progress integration with main system
- Complex pack scoping logic
- Cache invalidation edge cases
