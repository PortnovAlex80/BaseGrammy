# Wave 1 Agent A4: Daily Practice Architecture Map

## Active User Flows

### Daily Practice Session Flow

**1. Session Initialization (startDailyPractice)**
- User clicks "Start Daily Practice" from HomeScreen
- `TrainingViewModel.startDailyPractice()` → `DailyPracticeCoordinator.startDailyPractice()`
- Coordinator loads pack-scoped cursor via `PackDailyCursorStore.loadPackCursor()` (line 124)
- Level derived from `cursor.currentLessonIndex` (not mastery-based) (line 370)
- Session built via `DailySessionComposer.buildBlocks()` (line 426)
- First-session card IDs stored in cursor for repeat functionality (line 447)
- Session started with `startDailySession()` (line 437)

**2. Block Execution Flow**

**Block 1: TRANSLATE (navigate to TrainingScreen)**
- `DailyPracticeScreenContent` detects `TRANSLATE` block (GrammarMateApp.kt:736)
- Calls `onStartCardBlock(DailyBlockType.TRANSLATE, cards)` (line 84)
- Routes to `TrainingViewModel.startDailyTranslateSession()` → `TrainingScreen`
- User completes 10 sentence cards via VOICE/KEYBOARD/WORD_BANK
- Progress tracked via `recordDailyCardPracticed()` for cursor advancement (line 544)
- Block completion → `GrammarMateApp` detects daily mode → `coordinator.onBlockComplete()` (line 253)

**Block 2: VOCAB (inline in DailyPracticeScreen)**
- `DailyPracticeScreen` renders `VocabFlashcard` tasks inline (line 62)
- User practices SRS flashcards with Show Answer → Rate flow
- Each rating advances task index within VOCAB block (line 674)
- All cards rated → `onBlockComplete()` → advances to Block 3

**Block 3: VERBS (navigate to TrainingScreen)**
- Same flow as TRANSLATE block
- Uses weak-first ordering based on verb drill progress (line 445)
- Completion → `onBlockComplete()` → session ends

**3. Session Completion**
- All blocks done → `coordinator.endSession()` (line 194)
- Streaks updated via `StreakStore.recordPracticeTypeCompletion()` (line 207)
- Daily answered counts checked for cursor advancement (line 632)
- `finishedToken` set → completion sparkle shown (line 235)

**4. Repeat Flow (repeatDailyPractice)**
- User clicks "Repeat" → `TrainingViewModel.repeatDailyPractice()` (line 1006)
- Coordinator tries in-memory cache first (`lastDailyBlocks`) (line 468)
- Falls back to stored first-session card IDs from cursor (line 487)
- `DailySessionComposer.buildRepeatBlocks()` reconstructs session (line 491)
- Same cards replayed in original order

**5. Cursor Advancement**
- Only on session completion with full practice (line 632)
- `advanceDailyCursor()` increments `sentenceOffset` and `verbOffset` (line 871)
- Lesson wraps to next when `sentenceOffset` exceeds lesson size (line 886)
- Pack wraps to lesson 0 when last lesson exhausted (line 890)
- Updated cursor saved via `PackDailyCursorStore.savePackCursor()` (line 842)

## Main Classes and Responsibilities

### DailyPracticeCoordinator
**File:** `feature/daily/DailyPracticeCoordinator.kt`

**Core Responsibilities:**
- Orchestrates 3-block daily practice session (line 68)
- Manages pack-scoped cursor state (line 124)
- Provides block completion signaling (line 253)
- Tracks per-block answered counts for cursor advancement (line 104)
- Handles repeat/rebuild flows (line 452, 581)

**Key Methods:**
- `startDailyPractice()`: Line 350 - Initialize session from cursor
- `repeatDailyPractice()`: Line 452 - Rebuild from first-session card IDs
- `onBlockComplete()`: Line 253 - Single completion path for all blocks
- `getCurrentBlock()`: Line 291 - Get current block with pack change validation
- `recordDailyCardPracticed()`: Line 544 - Track VOICE/KEYBOARD completion
- `advanceDailyCursor()`: Line 871 - Advance cursor with lesson/pack wrapping
- `cancelDailySession()`: Line 628 - Conditional cursor advancement
- `repeatDailyBlock()`: Line 581 - Rebuild single block

**State Management:**
- `_state: MutableStateFlow<DailyPracticeState>` (line 85)
- `prebuiltDailyBlocks: List<DailyBlock>?` (line 91) - Background init cache
- `lastDailyBlocks: List<DailyBlock>?` (line 99) - In-memory repeat cache
- `dailyPracticeAnsweredCounts: MutableMap<DailyBlockType, Int>` (line 104)
- `dailyCursorAtSessionStart: DailyCursorState` (line 107)

### DailySessionComposer
**File:** `feature/daily/DailySessionComposer.kt`

**Core Responsibilities:**
- Pure builder for daily practice sessions (line 32)
- Block 1: Cursor-driven sentence selection (line 273)
- Block 2: SRS-based vocab selection (line 356)
- Block 3: Weak-first verb drill selection (line 445)
- Parallel block building with coroutineScope (line 88)

**Key Methods:**
- `buildBlocks()`: Line 106 - Build 3 blocks in parallel
- `buildRepeatBlocks()`: Line 211 - Rebuild from stored card IDs
- `buildSentenceBlock()`: Line 273 - Cursor-driven, no shuffle
- `buildVocabBlock()`: Line 356 - SRS selection (due → new → fallback)
- `buildVerbBlock()`: Line 445 - Weak-first with collocation grouping
- `rebuildBlockAsBlock()`: Line 143 - Rebuild single block

**Session Composition Rules:**
- Block 1: Next 10 cards from `cursor.sentenceOffset` in order (line 293)
- Block 2: 10 most-overdue SRS words, fill with new, fallback to least-recent (line 389)
- Block 3: 10 cards from weak-first ordered pool starting at `cursor.verbOffset` (line 501)

### PackDailyCursorStore
**File:** `data/PackDailyCursorStore.kt`

**Core Responsibilities:**
- Pack-scoped cursor persistence (line 18)
- Prevents cross-pack contamination (line 13)
- File pattern: `daily_cursor_{packId}.yaml` (line 47)

**Key Methods:**
- `loadPackCursor()`: Line 50 - Load cursor for specific pack
- `savePackCursor()`: Line 80 - Save cursor with atomic write
- `deletePackCursor()`: Line 112 - Remove cursor for pack
- `loadAllPackCursors()`: Line 124 - Scan directory for all cursors

**Cursor State:**
- `PackDailyCursorState` (line 383): Pack-scoped cursor
- Fields: `packId`, `sentenceOffset`, `currentLessonIndex`, `firstSessionDate`, `firstSessionSentenceCardIds`, `firstSessionVerbCardIds`, `verbOffset`

### DailySessionHelper
**File:** `feature/daily/DailySessionHelper.kt`

**Core Responsibilities:**
- `TrainingStateAccess` interface (line 12) - Bridge to ViewModel
- `BlockProgress` data class (line 22) - Progress tracking

## Business Rules Found

### Session Composition Rules
1. **Level from cursor, not mastery**: `effectiveLevel = cursor.currentLessonIndex + 1` (line 372)
2. **First session detection**: `isFirstSessionToday = cursor.firstSessionDate != today` (line 382)
3. **Prebuilt cache validation**: Match packId and level (line 387, 395)
4. **Repeat session**: Rebuild from `firstSessionSentenceCardIds` and `firstSessionVerbCardIds` (line 493)

### Cursor Advancement Rules
1. **Only VOICE/KEYBOARD count**: WORD_BANK does not advance cursor (line 632)
2. **Full completion required**: All TRANSLATE and VERBS cards must be practiced (line 639)
3. **Lesson wrapping**: `sentenceOffset >= lesson.cards.size` → increment lesson index (line 886)
4. **Pack wrapping**: `currentLessonIndex >= lessons.size` → wrap to 0 (line 890)
5. **Verb cycling**: `verbOffset + sessionSize >= totalVerbPoolSize` → wrap to 0 (line 901)

### Repeat/Continue/Reset Semantics
1. **Repeat**: Replays exact same cards from first session (line 491)
2. **Continue**: Not implemented - only Repeat exists
3. **Reset**: `resetState()` clears caches but preserves cursor (line 793)
4. **Full reset**: `resetAllDailyState()` clears everything including cursor (line 807)

### Blocking Conditions
**No explicit blocking logic found.** Daily practice can always start given:
- Active pack is selected
- Lessons exist for the pack
- Verb drill files exist (for VERBS block)

### Vocab SRS Rules
1. **Due words first**: Sorted by most overdue (line 384)
2. **Fill with new**: Never-reviewed words by rank (line 392)
3. **Fallback**: Least recently reviewed (line 398)
4. **Numbers excluded**: POS filter removes "numbers" (line 609)

## Infrastructure Dependencies

### Stores
- `LessonStore`: Load lessons, vocab/verb drill files (line 73)
- `MasteryStore`: Record card shows for TRANSLATE block (line 73)
- `VerbDrillStore`: Load verb drill progress (line 74)
- `WordMasteryStore`: Load vocab mastery states (line 75)
- `StreakStore`: Record practice type completions (line 76)
- `PackDailyCursorStore`: Pack-scoped cursor persistence (line 78)

### Progress Tracking
- **Streaks**: Per-practice-type (TRANSLATION, VOCAB, VERB) (line 199)
- **Daily answered counts**: Per-block VOICE/KEYBOARD completion (line 104)
- **Verb drill progress**: Per-combo (group+tense) everShown/todayShown (line 565)
- **Vocab mastery**: SRS interval ladder with step index (line 663)

### Caching Strategy
- **Prebuilt session**: Background init, validated on start (line 384)
- **In-memory repeat cache**: `lastDailyBlocks` for fast repeat (line 468)
- **Parsed data cache**: Vocab words and verb drill cards keyed by `packId:languageId` (line 40)

## State Mutations

### DailyPracticeState
**File:** `data/Models.kt:575`

```kotlin
data class DailyPracticeState(
    val dailySession: DailySessionState = DailySessionState(),
    val dailyCursor: DailyCursorState = DailyCursorState()
)
```

### DailySessionState
**File:** `data/Models.kt:346`

```kotlin
data class DailySessionState(
    val active: Boolean = false,
    val blocks: List<DailyBlock> = emptyList(),
    val blockIndex: Int = 0,
    val level: Int = 0,
    val finishedToken: Boolean = false,
    val packId: String = ""
)
```

**Mutations:**
- `startDailySession()`: Initialize with blocks, level, packId (line 181)
- `onBlockComplete()`: Mark current block complete, increment blockIndex (line 258)
- `endSession()`: Set active=false, finishedToken=true (line 234)

### PackDailyCursorState
**File:** `data/Models.kt:383`

**Mutations:**
- `initializeCursor()`: Load from pack store or use legacy (line 163)
- `updateCursor()`: Save to pack store and update in-memory (line 829)
- `advanceDailyCursor()`: Increment offsets, handle lesson/pack wrapping (line 871)
- First-session card IDs stored on `startDailyPractice()` (line 439)

### DailyBlock
**File:** `data/Models.kt:324`

```kotlin
data class DailyBlock(
    val type: DailyBlockType,
    val tasks: List<DailyTask>,
    val isComplete: Boolean = false,
    val taskIndex: Int = 0
)
```

**Mutations:**
- Block completion: `isComplete = true` in `onBlockComplete()` (line 258)
- VOCAB task index: Advanced in `rateVocabCard()` (line 675)

## Test Coverage

### Unit Tests
- **No dedicated unit tests found** for DailyPracticeCoordinator or DailySessionComposer
- Tests exist in `ui/DailyPracticeClickUiTest.kt` but are UI integration tests

### Integration Tests
- **DailyPracticeClickUiTest.kt**: Tests full 3-block flow via UI clicks
  - Test: `dailyPractice_fullThreeBlockFlow()` (line 43)
  - Tests TRANSLATE → VOCAB → VERBS → Completion flow
  - Uses Compose UI testing, no direct coordinator calls

### Test Gaps
- No unit tests for cursor advancement logic
- No unit tests for repeat session reconstruction
- No unit tests for pack switching behavior
- No tests for `SessionInvalidatedException` handling
- No tests for first-session card ID storage/retrieval

## Risks / Unclear Points

### Architectural Concerns

1. **No explicit blocking logic**: Daily practice can always start, may fail gracefully with empty blocks
2. **Complex cursor management**: Multiple cursor types (PackDailyCursorState vs DailyCursorState) create conversion overhead
3. **Tight coupling to TrainingViewModel**: Coordinator requires TrainingStateAccess interface
4. **State mutation complexity**: DailyPracticeState mixes session and cursor state
5. **Repeat flow fragility**: Depends on first-session card IDs, falls back to reset if corrupted
6. **No continue semantics**: Only repeat exists, no "continue where left off" flow
7. **Pack validation**: Session invalidated if pack changes, but no proactive prevention

### State Isolation
- **Risk**: Cross-pack contamination was a bug (TASK-080), now fixed with PackDailyCursorStore
- **Mitigation**: Each pack has its own cursor file (line 47)

### Performance Concerns
- **Verb drill loading**: Parses CSV files on every cache miss (line 624)
- **Weakness calculation**: Scores all filtered verbs on every build (line 471)
- **No cursor persistence during session**: Crash loses progress, only cursor survives

### Data Flow Complexity
- **Triple caching**: Prebuilt session, in-memory cache, parsed data cache
- **Cache invalidation**: Manual cache clearing on pack change (line 546)
- **Cursor conversion**: PackDailyCursorState ↔ DailyCursorState conversions (line 137, 832)

### Error Handling
- **SessionInvalidatedException**: Thrown on pack change mid-session (line 299)
- **Graceful degradation**: Repeat falls back to reset if card IDs mismatch (line 511)
- **No explicit validation**: Blocks can be empty, session can fail silently

## Evidence

### File References

**Core Architecture:**
- `feature/daily/DailyPracticeCoordinator.kt`:68 - Class definition and documentation
- `feature/daily/DailySessionComposer.kt`:32 - Pure builder pattern
- `feature/daily/DailySessionHelper.kt`:12 - TrainingStateAccess interface
- `data/PackDailyCursorStore.kt`:18 - Pack-scoped store interface
- `data/Models.kt`:324 - DailyBlock data class
- `data/Models.kt`:346 - DailySessionState data class
- `data/Models.kt`:367 - DailyCursorState data class
- `data/Models.kt`:383 - PackDailyCursorState data class
- `data/Models.kt`:575 - DailyPracticeState data class

**Session Composition:**
- `DailySessionComposer.kt`:106 - `buildBlocks()` method
- `DailySessionComposer.kt`:273 - `buildSentenceBlock()` cursor-driven
- `DailySessionComposer.kt`:356 - `buildVocabBlock()` SRS selection
- `DailySessionComposer.kt`:445 - `buildVerbBlock()` weak-first ordering
- `DailySessionComposer.kt`:211 - `buildRepeatBlocks()` from IDs

**Coordinator Methods:**
- `DailyPracticeCoordinator.kt`:350 - `startDailyPractice()`
- `DailyPracticeCoordinator.kt`:452 - `repeatDailyPractice()`
- `DailyPracticeCoordinator.kt`:253 - `onBlockComplete()`
- `DailyPracticeCoordinator.kt`:291 - `getCurrentBlock()`
- `DailyPracticeCoordinator.kt`:544 - `recordDailyCardPracticed()`
- `DailyPracticeCoordinator.kt`:871 - `advanceDailyCursor()`
- `DailyPracticeCoordinator.kt`:628 - `cancelDailySession()`
- `DailyPracticeCoordinator.kt`:581 - `repeatDailyBlock()`

**Cursor Management:**
- `DailyPracticeCoordinator.kt`:124 - `getCurrentPackCursor()`
- `DailyPracticeCoordinator.kt`:133 - `saveCurrentPackCursor()`
- `DailyPracticeCoordinator.kt`:163 - `initializeCursor()`
- `DailyPracticeCoordinator.kt`:829 - `updateCursor()`
- `PackDailyCursorStore.kt`:50 - `loadPackCursor()`
- `PackDailyCursorStore.kt`:80 - `savePackCursor()`

**State Management:**
- `DailyPracticeCoordinator.kt`:85 - `_state` MutableStateFlow
- `DailyPracticeCoordinator.kt`:91 - `prebuiltDailyBlocks` cache
- `DailyPracticeCoordinator.kt`:99 - `lastDailyBlocks` cache
- `DailyPracticeCoordinator.kt`:104 - `dailyPracticeAnsweredCounts`
- `DailyPracticeCoordinator.kt`:181 - `startDailySession()`
- `DailyPracticeCoordinator.kt`:194 - `endSession()`

**Business Rules:**
- `DailyPracticeCoordinator.kt`:370 - Level from cursor
- `DailyPracticeCoordinator.kt`:382 - First session detection
- `DailyPracticeCoordinator.kt`:632 - VOICE/KEYBOARD only
- `DailyPracticeCoordinator.kt`:886 - Lesson wrapping
- `DailyPracticeCoordinator.kt`:890 - Pack wrapping
- `DailySessionComposer.kt`:384 - Vocab SRS due sorting
- `DailySessionComposer.kt`:609 - Numbers exclusion

**UI Integration:**
- `GrammarMateApp.kt`:736 - `DailyPracticeScreenContent`
- `GrammarMateApp.kt`:743 - `vm.daily.getCurrentBlock()`
- `GrammarMateApp.kt`:747 - `DailyPracticeScreen` render
- `DailyPracticeScreen.kt`:70 - Screen component definition
- `TrainingViewModel.kt`:968 - `startDailyPractice()`
- `TrainingViewModel.kt`:1006 - `repeatDailyPractice()`
- `TrainingViewModel.kt`:1020 - `onDailyBlockComplete()`

**Test Evidence:**
- `ui/DailyPracticeClickUiTest.kt`:43 - Full 3-block flow test
- `ui/DailyPracticeClickUiTest.kt`:86 - Block creation pattern

---

**Generated:** 2026-05-22
**Agent:** Wave 1 Agent A4
**Scope:** Daily Practice Architecture (Safe Refactoring Audit)