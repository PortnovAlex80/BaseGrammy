# 9. Daily Practice System -- Specification

## 9.1 Overview

Daily Practice is GrammarMate's unified daily training session, combining three distinct exercise blocks into a single flow:

1. **Block 1 -- Sentence Translations** (10 cards): Translate sentences from Russian to the target language using voice, keyboard, or word bank input.
2. **Block 2 -- Vocabulary Flashcards** (10 cards, Anki-style): Review vocabulary words with a show-and-rate mechanism integrating spaced repetition scheduling.
3. **Block 3 -- Verb Conjugations** (10 cards): Conjugate verbs in context, with verb metadata (infinitive, tense, group) displayed as hint chips.

Total session size: up to 30 cards across 3 blocks. Blocks are served sequentially; the session ends when all blocks are complete or the user exits.

### Historical Context

Daily Practice replaces two former modes:
- **Elite mode** (high-speed translation drill) -- removed; `AppScreen.ELITE` enum value kept for backward compatibility (redirects to HOME if restored from saved state).
- **Vocab Sprint** (rapid vocabulary quiz) -- removed; `AppScreen.VOCAB` enum value kept for backward compatibility (same redirect behavior).

These enum values MUST NOT be deleted because existing saved state may reference them. Removing them would crash users who have `"ELITE"` or `"VOCAB"` as their persisted `currentScreen`.

---

## 9.2 Session Architecture

### 9.2.1 Component Overview

The Daily Practice system consists of four primary components:

| Component | File | Responsibility |
|-----------|------|----------------|
| `DailySessionComposer` | `ui/helpers/DailySessionComposer.kt` | Pure builder: constructs `List<DailyTask>` for all three blocks from lesson, vocab, and verb drill data using cursor-based card selection. |
| `DailySessionHelper` | `ui/helpers/DailySessionHelper.kt` | State manager: tracks `taskIndex` and `blockIndex` within the task list, provides block-level and session-level progress, handles block transitions and session lifecycle. |
| `DailyPracticeSessionProvider` | `ui/helpers/DailyPracticeSessionProvider.kt` | `CardSessionContract` adapter for Blocks 1 and 3. Manages per-card state (current index, input mode, retry/hint flow, word bank). Block 2 does NOT use this provider. |
| `DailyPracticeScreen` | `ui/screens/DailyPracticeScreen.kt` (currently in `ui/DailyPracticeScreen.kt`) | Composable UI: renders the current block's content, handles block transitions with sparkle overlays, displays session completion screen. |

### 9.2.2 Data Flow

```
TrainingViewModel.startDailyPractice()
  -> DailySessionComposer.buildSession(lessonLevel, packId, languageId, lessonId, tenses, cursor)
     -> buildSentenceBlock() -> List<DailyTask.TranslateSentence>
     -> buildVocabBlock()    -> List<DailyTask.VocabFlashcard>
     -> buildVerbBlock()     -> List<DailyTask.ConjugateVerb>
  -> DailySessionHelper.startDailySession(tasks, lessonLevel)
     -> sets DailySessionState(active=true, tasks=..., taskIndex=0, blockIndex=0)

UI reads: state.dailySession -> DailyPracticeScreen
  -> DailySessionHelper.getCurrentTask() -> current DailyTask
  -> DailySessionHelper.getBlockProgress() -> BlockProgress(position, size, total)
  -> CardSessionBlock or VocabFlashcardBlock rendered based on blockType

On block completion:
  -> DailySessionHelper.advanceToNextBlock()
  -> BlockSparkleOverlay shown for 800ms
  -> Next block's tasks become current

On session completion:
  -> DailySessionHelper.endSession()
  -> DailyPracticeCompletionScreen shown
```

### 9.2.3 DailyTask Sealed Class

All session content is modeled as `DailyTask` subtypes:

```kotlin
sealed class DailyTask {
    abstract val id: String
    abstract val blockType: DailyBlockType

    data class TranslateSentence(...) : DailyTask()  // blockType = TRANSLATE
    data class VocabFlashcard(...)    : DailyTask()  // blockType = VOCAB
    data class ConjugateVerb(...)     : DailyTask()  // blockType = VERBS
}
```

### 9.2.4 DailyBlockType Enum

```kotlin
enum class DailyBlockType { TRANSLATE, VOCAB, VERBS }
```

Tasks are ordered in the task list by block type: all TRANSLATE tasks first, then all VOCAB tasks, then all VERBS tasks. Block transitions are detected by comparing `blockType` of consecutive tasks.

### 9.2.5 DailySessionState

```kotlin
data class DailySessionState(
    val active: Boolean = false,
    val tasks: List<DailyTask> = emptyList(),
    val taskIndex: Int = 0,
    val blockIndex: Int = 0,
    val level: Int = 0,
    val finishedToken: Boolean = false
)
```

| Field | Meaning |
|-------|---------|
| `active` | Session is in progress. |
| `tasks` | Flat list of all tasks across all 3 blocks, in TRANSLATE/VOCAB/VERBS order. |
| `taskIndex` | Index into `tasks` of the current card being practiced. |
| `blockIndex` | Current block number (0, 1, or 2). Incremented when `blockType` changes between consecutive tasks. |
| `level` | Lesson level at session creation time. |
| `finishedToken` | Set to `true` when `endSession()` is called. Triggers completion UI. |

### 9.2.6 DailyCursorState

Cursor-based state for tracking progress between daily sessions (persisted in `progress.yaml`):

```kotlin
data class DailyCursorState(
    val sentenceOffset: Int = 0,        // cards shown in current lesson (0, 10, 20, ...)
    val currentLessonIndex: Int = 0,    // which lesson in the pack (0-based)
    val lastSessionHash: Int = 0,       // hash of last completed session
    val firstSessionDate: String = "",  // ISO date (yyyy-MM-dd) of the first session of the day
    val firstSessionSentenceCardIds: List<String> = emptyList(),  // card IDs from first session's block 1
    val firstSessionVerbCardIds: List<String> = emptyList()       // card IDs from first session's block 3
)
```

| Field | Purpose |
|-------|---------|
| `sentenceOffset` | How many sentence cards have been completed in the current lesson. Advances by up to 10 per session. Used to select the next batch of cards. |
| `currentLessonIndex` | Index into the lesson list for the pack. Advances when all cards in a lesson have been practiced. |
| `lastSessionHash` | Used to detect whether a new session is needed vs. repeating the same cards. |
| `firstSessionDate` | ISO date string (`yyyy-MM-dd`). Used to determine if the "first session of the day" card cache is still valid (24-hour lifetime). |
| `firstSessionSentenceCardIds` | Stores the card IDs from the first session's Block 1, enabling the "Repeat" feature to replay the exact same cards. |
| `firstSessionVerbCardIds` | Stores the card IDs from the first session's Block 3, enabling the "Repeat" feature for verb cards. |

Persistence: `DailyCursorState` is nested inside `TrainingProgress.dailyCursor` in `ProgressStore` and written to `grammarmate/progress.yaml`.

---

## 9.3 Block 1: Sentence Translations (10 cards)

### 9.3.1 Card Selection Algorithm

Block 1 uses **cursor-driven sequential selection** -- no shuffling. The algorithm is in `DailySessionComposer.buildSentenceBlock()`:

1. Load all lessons for the target language via `LessonStore.getLessons(languageId)`.
2. Select the lesson at `cursor.currentLessonIndex`. If the index is out of range, fall back to matching by `lessonId`.
3. Take cards starting at `cursor.sentenceOffset`, in order, up to `SENTENCE_COUNT` (10).
4. If the lesson is exhausted (offset >= cards.size), return an empty list, signaling the caller to advance the lesson index.

Cards per block: `CARDS_PER_BLOCK = 10` (constant in `DailySessionComposer`).

### 9.3.2 Input Mode Rotation

Input modes are assigned deterministically based on card index within the block:

| Index modulo 3 | Input Mode |
|----------------|------------|
| 0 | `InputMode.VOICE` |
| 1 | `InputMode.KEYBOARD` |
| 2 | `InputMode.WORD_BANK` |

This rotation ensures a balanced mix of input types across the 10 cards.

### 9.3.3 Task Construction

Each selected card is wrapped in `DailyTask.TranslateSentence`:

```kotlin
DailyTask.TranslateSentence(
    id = "sent_${card.id}",
    card = card,          // SentenceCard with promptRu and acceptedAnswers
    inputMode = mode      // VOICE, KEYBOARD, or WORD_BANK
)
```

### 9.3.4 Scoring and Progression

Answer validation is handled by `DailyPracticeSessionProvider`:

1. User submits an answer (via voice, keyboard, or word bank).
2. The input is normalized using `Normalizer.normalize()` and compared against each accepted answer (also normalized).
3. **Correct**: `pendingCard` and `pendingResult` are set. In VOICE mode, auto-advance after 400ms delay.
4. **Incorrect (attempt < 3)**: `showIncorrectFeedback` is set to `true`, `incorrectAttempts` increments, `remainingAttempts` decrements. In VOICE mode, voice recognition is auto-re-triggered after 1200ms.
5. **Incorrect (attempt = 3)**: Answer is auto-shown as a hint via `hintAnswer`. Input controls remain visible. `sessionActive` becomes `false` until the user types again (which clears the hint and resets attempts) or calls `nextCard()`.
6. **Manual "Show Answer"**: Sets `hintAnswer`, pauses session, resets attempt counters. Same behavior as 3 wrong attempts.

### 9.3.5 Mastery Counting

Only VOICE and KEYBOARD answers count as "practiced" for the purpose of cursor advancement. The `onCardAdvanced` callback in `DailyPracticeSessionProvider.nextCard()` is invoked only when the current input mode is NOT `WORD_BANK`:

```kotlin
if (currentIndex < blockCards.size && _inputMode != InputMode.WORD_BANK) {
    onCardAdvanced(blockCards[currentIndex])
}
```

WORD_BANK answers and simple forward navigation (no answer submitted) do NOT count.

### 9.3.6 Repeat Session for Block 1

`DailySessionComposer.buildSentenceBlockFromIds()` reconstructs the block from stored card IDs (from `DailyCursorState.firstSessionSentenceCardIds`). Cards are looked up across all lessons, preserving the original ID order. Input mode rotation follows the same index-based pattern.

---

## 9.4 Block 2: Vocabulary Flashcards (10 cards, Anki-style)

### 9.4.1 Flashcard Model

Each vocab flashcard is a `DailyTask.VocabFlashcard`:

```kotlin
DailyTask.VocabFlashcard(
    id = "voc_${word.id}",
    word = word,                    // VocabWord (id, word, pos, rank, meaningRu, collocations, forms)
    direction = direction           // VocabDrillDirection (IT_TO_RU or RU_TO_IT)
)
```

### 9.4.2 Card Selection Algorithm

Block 2 uses **pure SRS (Spaced Repetition System) selection** from the full pack word list. It is NOT tied to lesson level or cursor position. The algorithm is in `DailySessionComposer.buildVocabBlock()`:

1. Load all vocab words from the pack's vocab drill files via `LessonStore.getVocabDrillFiles()` and `ItalianDrillVocabParser`.
2. Load all mastery states from `WordMasteryStore.loadAll()`.
3. Categorize every word into one of three buckets:
   - **Due words**: `mastery.nextReviewDateMs <= now` or `mastery.lastReviewDateMs == 0`.
   - **New words**: No mastery record exists (never reviewed).
   - **Scheduled words**: Not due, already reviewed.
4. Sort due words by **most overdue first** (descending `now - nextReviewDateMs`).
5. Select up to `VOCAB_COUNT` (10) words, priority order:
   - First: due words, most overdue first.
   - Second: new words, sorted by rank (frequency).
   - Third (fallback): least recently reviewed scheduled words.

### 9.4.3 Direction Assignment

Flashcard direction alternates by index:

| Index modulo 2 | Direction |
|----------------|-----------|
| 0 | `VocabDrillDirection.IT_TO_RU` (prompt in Italian, answer in Russian) |
| 1 | `VocabDrillDirection.RU_TO_IT` (prompt in Russian, answer in Italian) |

### 9.4.4 Show/Hide Answer Flow

Unlike Blocks 1 and 3, Block 2 uses a **show-and-rate** model rather than submit-and-check:

1. The flashcard displays the **prompt text** (large, 28sp) and the **answer text** (smaller, 18sp) simultaneously. Both are always visible -- the card is not "flipped."
2. A TTS button allows listening to the prompt word.
3. A microphone button allows voice recognition. The recognized speech is normalized and compared to the answer text. If correct, the card is auto-rated as "Good" (rating 2) and the session auto-advances.
4. If voice recognition returns an incorrect result, the user stays on the card and can retry or tap a rating button.

### 9.4.5 Rating Buttons

Four Anki-style rating buttons are displayed at the bottom:

| Button | Rating | Background | Text Color |
|--------|--------|------------|------------|
| Again | 0 | Light red (#FFEBEE) | Red (#E53935) |
| Hard | 1 | Light orange (#FFF3E0) | Orange (#FF9800) |
| Good | 2 | Light green (#E8F5E9) | Green (#4CAF50) |
| Easy | 3 | Light blue (#E3F2FD) | Blue (#2196F3) |

Tapping any rating button:
1. Calls `onRate(rating)` which updates `WordMasteryStore` (interval step adjustment based on rating).
2. Calls `onAdvance()` which moves to the next card.
3. If no more cards remain, calls `onComplete()` to advance to the next block or finish the session.

### 9.4.6 Spaced Repetition Integration

Word mastery is persisted in `WordMasteryStore` (pack-scoped at `grammarmate/drills/{packId}/word_mastery.yaml`):

```kotlin
data class WordMasteryState(
    val wordId: String,
    val intervalStepIndex: Int = 0,         // 0-9, index into INTERVAL_LADDER_DAYS
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val lastReviewDateMs: Long = 0L,
    val nextReviewDateMs: Long = 0L,        // lastReviewDate + ladder[step] * DAY_MS
    val isLearned: Boolean = false           // reached step 9
)
```

The interval ladder is `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` days (from `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS`).

Rating affects the interval step:
- **Again (0)**: Reset step to 0 (relearn from scratch).
- **Hard (1)**: Step stays the same or moves down.
- **Good (2)**: Step increments by 1.
- **Easy (3)**: Step increments by 2 or more.

The `nextReviewDateMs` is recalculated after each review as `lastReviewDateMs + INTERVAL_LADDER_DAYS[step] * DAY_MS`.

### 9.4.7 Vocab Block Independence

Block 2 vocab selection is completely independent of Blocks 1 and 3:
- It does not use `DailyCursorState`.
- It does not use lesson level for rank filtering.
- It follows its own SRS scheduling across the full pack word list.
- In "Repeat" sessions (`buildRepeatSession`), Block 2 still generates fresh SRS-based cards rather than replaying the first session's vocab cards.

---

## 9.5 Block 3: Verb Conjugations (10 cards)

### 9.5.1 Verb Card Selection

Block 3 uses **weak-first ordering**, excluding previously shown cards. The algorithm is in `DailySessionComposer.buildVerbBlock()`:

1. Load all verb drill cards from the pack's verb drill files via `LessonStore.getVerbDrillFiles()` and `VerbDrillCsvParser`.
2. Filter cards by **active tenses**:
   - If `cumulativeTenses` is provided (from manifest lessons 1..lessonLevel), use those.
   - Otherwise, fall back to `TENSE_LADDER[lessonLevel]`.
   - If no tenses are active, return an empty block.
3. Load progress from `VerbDrillStore.loadProgress()`.
4. Collect IDs of all previously shown cards across all combos (`everShownCardIds`).
5. **Exclude** previously shown cards to produce the `unshown` set.
6. Score unshown cards by **weakness**: for each card, compute `1 - (shownInSameTense / totalInSameTense)`. Higher weakness score means more cards remain unshown in that tense.
7. Sort by weakness descending, stable by original index (preserves CSV order for ties).
8. Take the top `VERB_COUNT` (10) cards.

### 9.5.2 Tense Ladder

The tense ladder maps lesson levels (1-12) to cumulative sets of active tenses:

| Level | Tenses |
|-------|--------|
| 1 | Presente |
| 2 | + Imperfetto |
| 3 | + Passato Prossimo |
| 4 | + Futuro Semplice |
| 5 | + Condizionale Presente |
| 6 | + Passato Remoto |
| 7 | + Congiuntivo Presente |
| 8 | + Trapassato Prossimo |
| 9 | + Futuro Anteriore |
| 10 | + Congiuntivo Imperfetto |
| 11 | + Condizionale Passato |
| 12 | + Congiuntivo Passato |

Each level adds one tense to the previous level's set. This ensures progressive difficulty as the user advances through lessons.

### 9.5.3 Task Construction

Each selected card is wrapped in `DailyTask.ConjugateVerb`:

```kotlin
DailyTask.ConjugateVerb(
    id = "verb_${card.id}",
    card = card,          // VerbDrillCard (promptRu, answer, verb, tense, group, rank)
    inputMode = mode      // KEYBOARD or WORD_BANK (alternating)
)
```

Input mode alternates: even indices get `KEYBOARD`, odd indices get `WORD_BANK`. Note: `VOICE` is NOT used for verb conjugation cards.

### 9.5.4 Verb Info Display (Hint Chips)

When the current task is a `ConjugateVerb`, the card content renders three `SuggestionChip` components:

| Chip | Source Field | Display | Fallback |
|------|-------------|---------|----------|
| Verb infinitive | `VerbDrillCard.verb` | `"{verb} #{rank}"` (or just verb if rank is null) | Hidden if blank |
| Tense | `VerbDrillCard.tense` | Abbreviated (e.g., "Pres.", "Imperf.", "P. Pross.") | Hidden if blank |
| Group | `VerbDrillCard.group` | As-is | Hidden if blank |

Tense abbreviation is handled by `abbreviateTense()` which maps full Italian tense names to short forms (e.g., "Passato Prossimo" -> "P. Pross."). Unrecognized tenses are truncated to 8 characters.

Chips are non-interactive in Daily Practice (no bottom sheet on tap), unlike the standalone Verb Drill screen.

### 9.5.5 Progress Tracking

Verb drill progress is persisted per-combination in `VerbDrillStore` (pack-scoped at `grammarmate/drills/{packId}/verb_drill_progress.yaml`):

```kotlin
data class VerbDrillComboProgress(
    val group: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIds: Set<String> = emptySet(),
    val todayShownCardIds: Set<String> = emptySet(),
    val lastDate: String = ""
)
```

When a verb card is advanced (via `onCardAdvanced`), the card's ID is added to both `everShownCardIds` and `todayShownCardIds`. The `todayShownCardIds` set is cleared on a new day (when `lastDate != today`).

Only VOICE and KEYBOARD modes trigger `onCardAdvanced` (WORD_BANK does not), matching the mastery counting rule from Block 1.

### 9.5.6 Repeat Session for Block 3

`DailySessionComposer.buildVerbBlockFromIds()` reconstructs the block from stored card IDs (from `DailyCursorState.firstSessionVerbCardIds`). Cards are looked up from verb drill files, preserving original order. Input mode rotation follows the same KEYBOARD/WORD_BANK alternation.

---

## 9.6 Session Flow

### 9.6.1 Start Sequence

1. User taps the Daily Practice tile on HomeScreen.
2. `TrainingViewModel.startDailyPractice()` is called.
3. The ViewModel reads `activePackId`, `selectedLanguageId`, `selectedLessonId`, and `lessons` from the current UI state.
4. Lesson level is derived from the lesson index: `(lessons.indexOfFirst { it.id == selectedLessonId } + 1).coerceIn(1, 12)`.
5. `DailySessionComposer.buildSession()` is called with the lesson level, pack ID, language ID, lesson ID, cumulative tenses, and the current `DailyCursorState`.
6. If all blocks return empty, the session start fails (returns `false`).
7. `DailySessionHelper.startDailySession()` initializes the `DailySessionState` with `active=true`, `taskIndex=0`, `blockIndex=0`.
8. Navigation transitions to `AppScreen.DAILY_PRACTICE`.
9. `DailyPracticeScreen` renders the first task of Block 1 (TRANSLATE).

### 9.6.2 Block Transition Sequence

When a block completes (all cards in the block finished):

1. `DailyPracticeSessionProvider` calls its `onBlockComplete` callback.
2. In `CardSessionBlock`, this sets `blockComplete = true`.
3. `onAdvanceBlock()` is called, which invokes `DailySessionHelper.advanceToNextBlock()`.
4. `advanceToNextBlock()` scans forward in the task list, skipping all tasks of the current `blockType`, and sets `taskIndex` to the first task of the next block type.
5. `blockIndex` is incremented.
6. If no more blocks exist (no more task types), `endSession()` is called and `false` is returned.
7. A `BlockSparkleOverlay` is displayed for 800ms, showing "Next: {BlockLabel}" (or "Daily practice complete!" for the last block).
8. After the sparkle dismisses, the new block's content is rendered.

### 9.6.3 Block Labels

| Block Type | Label in Header | Label in Sparkle |
|------------|----------------|------------------|
| `TRANSLATE` | "Translation" | "Next: Vocabulary" |
| `VOCAB` | "Vocabulary" | "Next: Verbs" |
| `VERBS` | "Verbs" | "Daily practice complete!" |

### 9.6.4 Session Completion

After all blocks are complete:

1. `DailySessionHelper.endSession()` sets `active=false`, `finishedToken=true`.
2. A final `BlockSparkleOverlay` is shown for 800ms with the message "Daily practice complete!" and subtitle "Great job today!".
3. After the sparkle, `DailyPracticeCompletionScreen` is displayed with:
   - Title: "Session Complete!"
   - Message: "Great job! You practiced translations, vocabulary, and verb conjugations."
   - Button: "Back to Home" which calls `onExit()`.

### 9.6.5 Navigation Between Blocks: Repeat vs. Continue

The daily practice system supports two navigation modes for subsequent sessions on the same day:

**Repeat (same cards)**:
- Replays the exact same sentence and verb cards from the first session of the day.
- Uses `DailyCursorState.firstSessionSentenceCardIds` and `firstSessionVerbCardIds` to reconstruct blocks.
- Block 2 (Vocab) always generates fresh SRS-based cards regardless.
- Mastery progress is NOT reset -- previously answered cards remain answered.
- First session card IDs are valid for 24 hours (tracked by `firstSessionDate`).

**Continue (new cards)**:
- If the cursor has advanced (all cards in the block practiced with VOICE/KEYBOARD), generates a new batch of cards starting from the new cursor position.
- If the cursor has NOT advanced, behaves identically to Repeat.
- Block 2 always follows its independent SRS process.

### 9.6.6 Mic/Start Behavior on First Card

On the first card of Block 1:
- TTS does NOT auto-play (this is correct behavior for the first card of a new session).
- The Start button (if present in the UI flow) is active and functional.
- The microphone icon is active and launches ASR when tapped.
- These elements are controlled by `inputMode` and `isListening` state, which are NOT blocked by the absence of auto-play.

### 9.6.7 Card Counting and Batch Management

- Each block targets exactly 10 cards (`CARDS_PER_BLOCK` constant).
- Cards within a block are consumed sequentially (no wrap-around within a single session).
- The `DailyPracticeSessionProvider` tracks progress via `currentIndex` (Compose mutable state) which is separate from `DailySessionHelper.taskIndex` (StateFlow).
- When `currentIndex >= blockCards.size`, the provider calls `onBlockComplete()`.
- Block progress is reported via `SessionProgress(current, total)` where `current = (currentIndex + 1).coerceAtMost(blockCards.size)` and `total = blockCards.size`.

---

## 9.7 UI Components (DailyPracticeScreen)

### 9.7.1 Screen Structure

`DailyPracticeScreen` is a top-level composable receiving the following callbacks:

| Parameter | Type | Purpose |
|-----------|------|---------|
| `state` | `DailySessionState` | Current session state (active, tasks, finishedToken) |
| `blockProgress` | `BlockProgress` | Position within current block and total session |
| `currentTask` | `DailyTask?` | The task currently being practiced |
| `onSubmitSentence` | `(String) -> Boolean` | Submit answer for Block 1 card |
| `onSubmitVerb` | `(String) -> Boolean` | Submit answer for Block 3 card |
| `onShowSentenceAnswer` | `() -> String?` | Show answer for Block 1 card |
| `onShowVerbAnswer` | `() -> String?` | Show answer for Block 3 card |
| `onFlipVocabCard` | `() -> Unit` | Flip Block 2 flashcard |
| `onRateVocabCard` | `(Int) -> Unit` | Rate Block 2 flashcard (0-3) |
| `onAdvance` | `() -> Boolean` | Advance to next card within a block |
| `onAdvanceBlock` | `() -> Boolean` | Advance to next block |
| `onRepeatBlock` | `() -> Boolean` | Repeat current block |
| `onSpeak` | `(String) -> Unit` | TTS speak |
| `onStopTts` | `() -> Unit` | Stop TTS playback |
| `ttsState` | `TtsState` | Current TTS state (IDLE, SPEAKING, INITIALIZING, ERROR) |
| `onExit` | `() -> Unit` | Exit session |
| `onComplete` | `() -> Unit` | Session completed |
| `onPersistVerbProgress` | `(VerbDrillCard) -> Unit` | Persist verb drill card progress |
| `onCardPracticed` | `(DailyBlockType) -> Unit` | Track practiced card for cursor advancement |

### 9.7.2 Header (DailyPracticeHeader)

- Back arrow button (triggers exit confirmation dialog).
- Title: "Daily Practice" (semi-bold, 18sp).
- Block label badge (right-aligned): "Translation", "Vocabulary", or "Verbs" in a `Card` with `primaryContainer` background.

Exit confirmation dialog:
- Title: "Exit practice?"
- Body: "Your progress in this session will be lost."
- Buttons: "Exit" (confirms) and "Stay" (dismisses).

### 9.7.3 Progress Bar (BlockProgressBar)

- `LinearProgressIndicator` showing overall session progress: `globalPosition / totalTasks`.
- Text label: `"{globalPosition}/{totalTasks}"` (e.g., "5/30").
- Progress bar is 8dp tall with 4dp rounded corners.

### 9.7.4 Card Session Block (Blocks 1 and 3)

Renders a `TrainingCardSession` composable with custom `cardContent` and `inputControls`:

**Card content**:
- Russian prompt (labeled "RU", 20sp semi-bold).
- TTS speaker button (shows different icons based on `TtsState`):
  - IDLE: VolumeUp icon.
  - SPEAKING: StopCircle icon (red).
  - INITIALIZING: CircularProgressIndicator.
  - ERROR: ReportProblem icon (red).
- Verb info chips (Block 3 only): verb + rank, tense (abbreviated), group.

**Input controls** (`DailyInputControls`):
- Hint answer card: displayed when `hintAnswer` is non-null (after 3 wrong attempts or manual "Show Answer"). Shows the correct answer in an `errorContainer`-colored card with a TTS button.
- Incorrect feedback row: "Incorrect" text (red) + remaining attempts count.
- Text field: "Your translation" label, with a microphone trailing icon.
- Voice mode hint: "Say translation: {promptRu}" when in VOICE mode and session is active.
- Word bank: `FlowRow` of `FilterChip` words. Selected count shown. "Undo" button removes last selected word.
- Input mode selector: three `FilledTonalIconButton` buttons for Mic, Keyboard, and Word Bank. Active mode label displayed as text.
- Show answer button: Eye icon in a `TooltipBox`. Disabled when hint is already shown.
- Check button: Full-width `Button`. Enabled only when input text is not blank, a card exists, and the session is active. Calls `provider.submitAnswerWithInput(input)`.

**Auto-voice behavior**:
- When input mode is VOICE, a card exists, and the session is active:
  - On new card: 200ms delay, then launch ASR intent.
  - After incorrect feedback: 1200ms delay, then launch ASR intent.
- After correct voice answer: 400ms delay, then auto-advance via `nextCard()`.

### 9.7.5 Vocab Flashcard Block (Block 2)

A separate composable (`VocabFlashcardBlock`) that does NOT use `CardSessionContract`:

**Card display**:
- `Card` with `surfaceVariant` background.
- Prompt text: 28sp bold, centered. Language depends on direction:
  - IT_TO_RU: shows `word.word` (Italian).
  - RU_TO_IT: shows `word.meaningRu` (Russian).
- TTS button: speaks the prompt text.
- Answer text: 18sp medium, `primary` color, centered. Always visible.
  - IT_TO_RU: shows `word.meaningRu`.
  - RU_TO_IT: shows `word.word`.

**Voice input**:
- Large (64dp) microphone button.
- ASR language tag matches the answer direction:
  - IT_TO_RU: `ru-RU` (user speaks Russian).
  - RU_TO_IT: `it-IT` (user speaks Italian).
- On correct voice match: auto-rate as "Good" (2) and auto-advance.
- On incorrect: display "You said: ..." text, user stays on card.

**Rating buttons**: Four `OutlinedButton` components in a row, each with distinct colors. Tapping any button calls `onRate(rating)` and `onAdvance()`. If no more cards, calls `onComplete()`.

### 9.7.6 Block Sparkle Overlay (BlockSparkleOverlay)

Displayed during block transitions:
- Semi-transparent black background overlay.
- `primaryContainer`-colored card with sparkle emoji (48sp).
- Message text: "Next: {BlockLabel}" or "Daily practice complete!" (20sp bold).
- Subtitle for last block: "Great job today!".
- Auto-dismisses after 800ms via `LaunchedEffect`.

### 9.7.7 Completion Screen (DailyPracticeCompletionScreen)

Full-screen centered layout:
- Title: "Session Complete!" (28sp bold, primary color).
- Body text: "Great job! You practiced translations, vocabulary, and verb conjugations."
- "Back to Home" button (full-width).

### 9.7.8 Loading State

When the session is active but `currentTask` is null:
- Full-screen centered `CircularProgressIndicator`.
- "Loading session..." text in muted color.

---

## 9.8 State Management

### 9.8.1 TrainingStateAccess Interface

`DailySessionHelper` and other helpers access the ViewModel's state through a narrow interface:

```kotlin
interface TrainingStateAccess {
    val uiState: StateFlow<TrainingUiState>
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}
```

`TrainingViewModel` provides this via an anonymous object implementation. Helpers never reference the ViewModel directly -- they call `updateState { }` and `saveProgress()` through the interface.

### 9.8.2 Dual State Tracking

The Daily Practice system has two parallel state tracking mechanisms:

1. **Compose state** (`DailyPracticeSessionProvider`): Tracks per-card position (`currentIndex`), input mode, pending results, hint state, and attempt counters. This is the authoritative state for UI rendering during a block.

2. **StateFlow state** (`DailySessionHelper` / `DailySessionState`): Tracks `taskIndex` and `blockIndex` for session-level navigation. This is the authoritative state for block transitions and session lifecycle.

These two systems are not directly synchronized. The `DailyPracticeSessionProvider` is created fresh for each block (via `remember(blockKey)`) using the current `state.taskIndex` to determine the block type. Block transitions are triggered by the provider's `onBlockComplete` callback, which calls `DailySessionHelper.advanceToNextBlock()` to update the StateFlow state.

### 9.8.3 BlockProgress Data Class

```kotlin
data class BlockProgress(
    val blockType: DailyBlockType,    // TRANSLATE, VOCAB, or VERBS
    val positionInBlock: Int,         // 1-based position within the current block
    val blockSize: Int,               // total cards in the current block
    val totalTasks: Int,              // total tasks across all blocks
    val globalPosition: Int           // 1-based position across all blocks
)
```

Computed by `DailySessionHelper.getBlockProgress()`:
1. Finds the start and end indices of the current block type in the task list.
2. Calculates `blockSize = blockEnd - blockStart + 1`.
3. Calculates `positionInBlock = taskIndex - blockStart + 1` (clamped to [1, blockSize]).
4. `globalPosition = taskIndex + 1`.

### 9.8.4 Persistence of Daily Progress

| What | Where | Format |
|------|-------|--------|
| Session position | `DailySessionState` in `TrainingUiState` (in-memory) | Not persisted across app restarts (session is lost). |
| Cursor state | `TrainingProgress.dailyCursor` in `ProgressStore` | YAML in `grammarmate/progress.yaml`. |
| Verb drill progress | `VerbDrillStore` | YAML in `grammarmate/drills/{packId}/verb_drill_progress.yaml`. |
| Word mastery | `WordMasteryStore` | YAML in `grammarmate/drills/{packId}/word_mastery.yaml`. |
| General progress | `ProgressStore` | YAML in `grammarmate/progress.yaml`. |

All file writes use `AtomicFileWriter` (temp -> fsync -> rename) to prevent data corruption.

### 9.8.5 Session Resume

`DailySessionHelper.fastForwardTo(taskIndex)` allows jumping to a specific task index when resuming a saved session. It computes the correct `blockIndex` by counting `blockType` transitions from index 1 to `taskIndex`.

### 9.8.6 Block Replacement

`DailySessionHelper.replaceCurrentBlock(newTasks)` replaces the current block's tasks in the task list with new tasks (used for the Repeat feature). It:
1. Finds the start and end indices of the current block type.
2. Replaces the tasks in that range with the new tasks.
3. Resets `taskIndex` to the block start.

---

## 9.9 User Stories

### Session Initiation

- **As a user**, I want to start a daily practice session from the Home screen so that I can practice all three exercise types in one flow.
- **As a user**, I want the session to automatically select appropriate cards based on my current lesson level so that I practice relevant content.
- **As a user**, I want the session to resume where I left off if I start a new session later in the day so that I see new cards.

### Block 1: Sentence Translations

- **As a user**, I want to translate 10 sentences from Russian to my target language using voice, keyboard, or word bank so that I build translation automaticity.
- **As a user**, I want input modes to rotate automatically (voice, keyboard, word bank) so that I practice with varied input methods.
- **As a user**, I want to see "Incorrect" feedback with remaining attempts when I answer wrongly so that I know I can try again.
- **As a user**, I want the correct answer to be shown after 3 wrong attempts so that I can learn from my mistakes.
- **As a user**, I want to manually reveal the answer via the "Show Answer" button so that I can skip difficult cards without guessing.
- **As a user**, I want voice recognition to auto-restart after a wrong answer in voice mode so that I can retry without manual interaction.
- **As a user**, I want voice recognition to auto-advance after a correct answer so that the practice flow is smooth.
- **As a user**, I want to hear the target language pronunciation via TTS so that I improve my listening comprehension.

### Block 2: Vocabulary Flashcards

- **As a user**, I want to review vocabulary words in an Anki-style flashcard format so that I build long-term vocabulary retention.
- **As a user**, I want to see both the prompt and the answer simultaneously so that I can quickly self-assess.
- **As a user**, I want to rate my recall (Again, Hard, Good, Easy) so that the spaced repetition system schedules future reviews appropriately.
- **As a user**, I want the flashcard direction to alternate between target-to-native and native-to-target so that I practice bidirectional recall.
- **As a user**, I want to practice via voice recognition on flashcards so that I can test my pronunciation.
- **As a user**, I want overdue words to be prioritized so that I review what I am most at risk of forgetting.
- **As a user**, I want new words to be introduced when I have completed all due reviews so that my vocabulary grows progressively.

### Block 3: Verb Conjugations

- **As a user**, I want to conjugate verbs in context so that I build grammatical accuracy.
- **As a user**, I want to see the verb infinitive, tense, and group as hint chips so that I know which form is expected.
- **As a user**, I want verb cards to be ordered by weakness (weakest tenses first) so that I focus on my weakest areas.
- **As a user**, I want previously shown verb cards to be excluded from future sessions so that I see new conjugation challenges.
- **As a user**, I want tenses to be introduced progressively (matching my lesson level) so that I am not overwhelmed with advanced conjugations too early.

### Navigation and Flow

- **As a user**, I want to see a sparkle transition between blocks so that I feel a sense of progression.
- **As a user**, I want to see which block I am currently in (Translation, Vocabulary, Verbs) so that I know what I am practicing.
- **As a user**, I want an overall progress bar showing my position across all 30 cards so that I can estimate how much remains.
- **As a user**, I want to exit mid-session with a confirmation dialog so that I do not accidentally lose progress.
- **As a user**, I want to see a completion screen after finishing all three blocks so that I feel a sense of accomplishment.

### Repeat and Continue

- **As a user**, I want a "Repeat" option that replays the same cards from my first daily session so that I can reinforce what I learned today.
- **As a user**, I want a "Continue" option that gives me new cards if I have completed the previous batch so that I can practice more.
- **As a user**, I want "Continue" to behave like "Repeat" if my cursor has not advanced so that I am not stuck with no content.

### Mastery and Progression

- **As a user**, I want only VOICE and KEYBOARD answers to count as "practiced" so that my mastery accurately reflects active recall.
- **As a user**, I want WORD_BANK answers to NOT count toward mastery so that passive recognition does not inflate my progress.
- **As a user**, I want the cursor to advance to the next batch of cards only when I have actively answered all cards so that I do not skip content.
- **As a user**, I want my progress to persist across days via cursor state so that I always see new content each session.

---

## 9.10 Bad Card Reporting in Daily Practice

### 9.10.1 Overview

All three daily practice blocks support bad card reporting. Users can flag cards with errors or unnatural phrasing for later review and export. Flagged cards are stored in `BadSentenceStore` with mode-specific identifiers so they can be traced back to the originating block.

### 9.10.2 Flagging Support by Block

| Block | Block Type | Mode Field | Flag UI Location | Provider |
|-------|-----------|-----------|-----------------|----------|
| Block 1 (Translation) | `TRANSLATE` | `"daily_translate"` | Report button in `DailyInputControls` | `DailyPracticeSessionProvider` |
| Block 2 (Vocab Flashcard) | `VOCAB` | `"daily_vocab"` | Report button in `VocabFlashcardBlock` | Direct `TrainingViewModel` calls |
| Block 3 (Verb Conjugation) | `VERBS` | `"daily_verb"` | Report button in `DailyInputControls` | `DailyPracticeSessionProvider` |

### 9.10.3 DailyPracticeSessionProvider Flagging

`DailyPracticeSessionProvider` declares `supportsFlagging = true` and accepts the following callback parameters:

| Parameter | Type | Purpose |
|-----------|------|---------|
| `onFlagCard` | `((SessionCard, DailyBlockType) -> Unit)?` | Flags the current card with the block type |
| `onUnflagCard` | `((SessionCard, DailyBlockType) -> Unit)?` | Unflags the current card |
| `isCardFlagged` | `((SessionCard) -> Boolean)?` | Checks if the current card is flagged |
| `onExportFlagged` | `(() -> String?)?` | Exports all flagged cards |

The provider maps `blockType` to the correct mode string before calling the flag callback:

```kotlin
val mode = when (blockType) {
    DailyBlockType.TRANSLATE -> "daily_translate"
    DailyBlockType.VERBS -> "daily_verb"
    DailyBlockType.VOCAB -> "daily_vocab"
}
```

### 9.10.4 TrainingViewModel Daily Flagging Methods

The ViewModel maintains an in-memory `dailyBadCardIds` set for fast lookup during the session:

- `flagDailyBadSentence(cardId, languageId, sentence, translation, mode)`: Adds to `BadSentenceStore` with the active pack ID and the given mode. Also adds `cardId` to `dailyBadCardIds`.
- `unflagDailyBadSentence(cardId)`: Removes from `BadSentenceStore` and `dailyBadCardIds`.
- `isDailyBadSentence(cardId)`: Checks `dailyBadCardIds` first (fast path), then falls back to `BadSentenceStore.isBadSentence(packId, cardId)`.
- `exportDailyBadSentences()`: Calls `BadSentenceStore.exportUnified()` which produces a text file with all flagged cards grouped by language, pack, and mode.

### 9.10.5 Vocab Flashcard Block Flagging

Block 2 (Vocab Flashcard) uses a separate composable (`VocabFlashcardBlock`) that does NOT use `CardSessionContract`. It receives flagging callbacks directly:

| Parameter | Purpose |
|-----------|---------|
| `onFlagDailyBadSentence(cardId, languageId, sentence, translation, mode)` | Flags the vocab word. Sentence = `word.meaningRu ?: word.word`, translation = `word.word`, mode = `"daily_vocab"`. |
| `onUnflagDailyBadSentence(cardId)` | Unflags the vocab word. |
| `isDailyBadSentence(cardId)` | Checks if the vocab word is flagged. |
| `onExportDailyBadSentences()` | Exports all flagged cards. |

A flag/report icon button is displayed on the flashcard. When flagged, the icon turns red (`MaterialTheme.colorScheme.error`). Tapping the report icon opens a `ModalBottomSheet` with flag/unflag, export, and copy options.

### 9.10.6 Hidden Cards Behavior

`DailyPracticeSessionProvider.hideCurrentCard()` is a **no-op** -- flagging is the only report action in daily practice. Cards cannot be hidden during daily practice sessions. Flagging a card does NOT remove it from the session or affect progress tracking; it only records the report for later export.

### 9.10.7 Report Sheet UI

Both `DailyInputControls` (Blocks 1 and 3) and `VocabFlashcardBlock` (Block 2) render a `ModalBottomSheet` when the report button is tapped. The sheet contains:

1. **Card prompt text** (displayed at the top for context)
2. **"Add to bad sentences list"** (or "Remove from bad sentences list" if already flagged) with a warning icon
3. **"Export bad sentences to file"** with a download icon
4. **"Copy text"** with a copy icon -- copies `ID: ...\nSource: ...\nTarget: ...` to clipboard

After export, an inline message shows the exported file path or "No bad sentences to export".

### Edge Cases

- **As a user**, I want the session to gracefully handle packs without vocab or verb drill content so that I can still practice translations.
- **As a user**, I want the session to fail gracefully (return to Home) if no cards are available so that I am not stuck on a loading screen.
- **As a user**, I want the app to always start on the Home screen after a restart so that I am not stuck in a daily practice loading state.
