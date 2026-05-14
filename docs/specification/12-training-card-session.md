# 12. Training Card Session -- Specification

## 12.1 Overview

TrainingCardSession is a reusable Compose composable that provides the entire card-based training UI for GrammarMate. It abstracts card presentation, answer input, validation, feedback display, navigation, and session completion into a single parameterized component driven by the `CardSessionContract` interface.

The component is used by three distinct training modes:

1. **Standard lesson training** (via `TrainingCardSessionProvider` -- not yet migrated, uses the default slots in GrammarMateApp.kt).
2. **Verb Drill** (via `VerbDrillCardSessionProvider` wrapping `VerbDrillViewModel`).
3. **Daily Practice Blocks 1 and 3** -- translation and verb conjugation (via `DailyPracticeSessionProvider`).

Block 2 (Vocab Flashcard) does **not** use TrainingCardSession -- it renders an Anki-style flashcard UI instead.

The key design principle: the only difference between training modes is **card selection and scoring logic**, not the card training UI. All modes share the same visual layout, input methods, feedback animations, and navigation pattern.

### Files

| File | Purpose |
|------|---------|
| `data/CardSessionContract.kt` | Interface definitions: `SessionCard`, `CardSessionContract`, `CardSessionCapabilities`, `SessionProgress`, `AnswerResult`, `InputModeConfig` |
| `ui/TrainingCardSession.kt` | `TrainingCardSession` composable, `TrainingCardSessionScope`, all default slot implementations |
| `ui/VerbDrillCardSessionProvider.kt` | Adapter wrapping `VerbDrillViewModel` to implement `CardSessionContract` |
| `feature/daily/DailyPracticeSessionProvider.kt` | Adapter for Daily Practice translation and verb blocks |
| `ui/VerbDrillScreen.kt` | Verb Drill screen: selection screen + `TrainingCardSession` with custom slots + reference sheets |
| `ui/DailyPracticeScreen.kt` | Daily Practice screen: 3-block session with `TrainingCardSession` for blocks 1 and 3 |

---

## 12.2 CardSessionContract

### 12.2.1 SessionCard

The polymorphic card interface that both `SentenceCard` and `VerbDrillCard` implement:

```kotlin
interface SessionCard {
    val id: String
    val promptRu: String
    val acceptedAnswers: List<String>
}
```

Implementors:

- **`SentenceCard`** (in `data/Models.kt`): fields `id`, `promptRu`, `acceptedAnswers: List<String>`, optional `tense: String?`. Maps 1:1 with lesson CSV rows.
- **`VerbDrillCard`** (in `data/VerbDrillCard.kt`): fields `id`, `promptRu`, `answer: String`, optional `verb`, `tense`, `group`, `rank`. Implements `acceptedAnswers` as `listOf(answer)`.

### 12.2.2 CardSessionCapabilities

Optional capability flags that adapters declare. All default to `false`:

| Capability | Meaning |
|------------|---------|
| `supportsTts` | Shows TTS speaker button on card and in result feedback |
| `supportsVoiceInput` | Shows mic trailing icon on text field, voice mode selector button |
| `supportsWordBank` | Shows word bank FlowRow and word bank mode button |
| `supportsFlagging` | Shows report/flag button that opens bottom sheet |
| `supportsNavigation` | Shows bottom navigation row (prev/pause/exit/next) |
| `supportsPause` | Shows pause/play toggle button in navigation |

### 12.2.3 Supporting Data Classes

**`SessionProgress(current: Int, total: Int)`** -- 1-based position counter for display.

**`AnswerResult(correct: Boolean, displayAnswer: String, hintShown: Boolean = false)`** -- result of checking an answer. `displayAnswer` is the canonical form shown to the user.

**`InputModeConfig(availableModes: Set<InputMode>, defaultMode: InputMode, showInputModeButtons: Boolean)`** -- declares which input modes are available and which is the default for the current session.

### 12.2.4 CardSessionContract Interface

Full interface definition with all properties and actions:

**Properties (read by the composable):**

| Property | Type | Description |
|----------|------|-------------|
| `currentCard` | `SessionCard?` | The card currently displayed. Null when session is complete or loading. |
| `progress` | `SessionProgress` | 1-based `(current, total)` for progress display |
| `isComplete` | `Boolean` | True when all cards have been processed |
| `inputText` | `String` | Current input text (adapters may return "" if input is composable-managed) |
| `inputModeConfig` | `InputModeConfig` | Available input modes and default |
| `lastResult` | `AnswerResult?` | Non-null when result feedback should be shown instead of input controls |
| `sessionActive` | `Boolean` | False when paused or waiting for hint acknowledgment |
| `ttsState` | `TtsState` | Current TTS playback state (default: `IDLE`) |
| `currentInputMode` | `InputMode` | Currently selected input mode (default: `inputModeConfig.defaultMode`) |
| `languageId` | `String` | Language ID for voice recognition locale resolution (default: `"en"`) |
| `currentSpeedWpm` | `Int` | Current typing speed in words per minute (default: `0`) |
| `textScale` | `Float` | Font size multiplier for prompt text in default slots (default: `1.0f`). Range [1.0, 2.0]. Propagated from `ruTextScale` in Settings. |

**Actions (called by the composable):**

| Method | Description |
|--------|-------------|
| `onInputChanged(text)` | User typed or modified input text |
| `submitAnswer(): AnswerResult?` | Submit current input for validation. Returns null if adapter manages its own result state. |
| `showAnswer(): String?` | Reveal the correct answer (eye button) |
| `nextCard()` | Advance to the next card |
| `prevCard()` | Go back to the previous card |
| `onVoiceInputResult(text)` | Called when voice recognition returns a result (default: delegates to `onInputChanged`) |
| `setInputMode(mode)` | Switch input mode (VOICE/KEYBOARD/WORD_BANK) |
| `getSelectedWords(): List<String>` | Get currently selected word bank words in order |
| `getWordBankWords(): List<String>` | Get shuffled word bank for current card |
| `selectWordFromBank(word)` | Add a word to the word bank selection |
| `removeLastSelectedWord()` | Remove the last selected word bank word (undo) |
| `speakTts()` | Start TTS playback for current card |
| `stopTts()` | Stop TTS playback |
| `flagCurrentCard()` | Flag current card as bad |
| `unflagCurrentCard()` | Remove bad flag from current card |
| `isCurrentCardFlagged(): Boolean` | Check if current card is flagged |
| `hideCurrentCard()` | Hide current card from future lessons |
| `exportFlaggedCards(): String?` | Export all flagged cards to file, return path |
| `togglePause()` | Toggle session pause state |
| `requestExit()` | Exit the session (called after confirmation dialog) |
| `requestNextBatch()` | Request the next batch of cards (used by Verb Drill completion) |

---

## 12.3 Card Lifecycle

### 12.3.1 State Machine

Each card progresses through these states:

```
  PRESENTING --> SUBMITTED (correct) --> RESULT_SHOWN --> NEXT
       |                                               ^
       |           (wrong, attempts < 3)                |
       +--> INCORRECT_FEEDBACK --> PRESENTING (retry) -+
       |
       |           (wrong, attempts >= 3)
       +--> HINT_SHOWN --> NEXT (via play/pause or next)
       |
       |           (manual eye button)
       +--> HINT_SHOWN --> NEXT
```

**State determination:** The composable determines which state to render based on adapter properties:

- `currentCard != null && lastResult == null` --> PRESENTING (show input controls)
- `currentCard != null && lastResult != null` --> RESULT_SHOWN (show result content)
- `currentCard == null && isComplete` --> COMPLETION (show completion screen)

Adapters that implement the retry/hint flow (`VerbDrillCardSessionProvider`, `DailyPracticeSessionProvider`) add an intermediate state where `lastResult` is NOT set but a hint is displayed. This is tracked via adapter-specific fields (`hintAnswer`, `showIncorrectFeedback`) that the custom `inputControls` slot reads.

### 12.3.2 Submit and Validate Flow

The answer validation pipeline:

1. User enters text (keyboard), speaks (voice), or selects words (word bank).
2. `submitAnswer()` or adapter-specific `submitAnswerWithInput(input)` is called.
3. The adapter normalizes both user input and accepted answers using `Normalizer.normalize()`.
4. Normalization: trim, collapse whitespace, remove time colons, lowercase, strip diacritics, strip punctuation.
5. Comparison: normalized input is compared against each normalized accepted answer.
6. Result: `AnswerResult(correct, displayAnswer)` is set, or for wrong answers the retry/hint flow activates.

### 12.3.3 Retry and Hint Flow

Both `VerbDrillCardSessionProvider` and `DailyPracticeSessionProvider` implement an identical retry/hint flow:

1. **Correct answer:** Set `pendingCard` and `pendingAnswerResult`. The card stays visible for feedback. ViewModel index is NOT advanced yet.
2. **Wrong answer (attempt < 3):** Show inline "Incorrect" feedback with remaining attempt count. Input stays visible. If in VOICE mode, auto-trigger voice recognition after a 1200ms delay.
3. **Wrong answer (attempt >= 3):** Auto-show answer as hint in an error-colored card. Set `isPaused = true`. Input controls stay visible below the hint card.
4. **Manual "Show Answer" (eye button):** Same as attempt >= 3: show hint card, pause session.
5. **Advancement:** `nextCard()` clears all pending state and advances the index. If a hint was shown, the card is marked as completed (no credit). If a correct answer was pending, the ViewModel records it.

### 12.3.4 Batch Management

Cards are presented in batches. The adapter manages a fixed-size batch:

- **Verb Drill:** `VerbDrillViewModel` loads 10 cards per session. `nextBatch()` loads 10 more.
- **Daily Practice:** `DailySessionComposer.CARDS_PER_BLOCK = 10`. Translation block = 10 cards, Verb block = 10 cards. When all cards in a block are done, `onBlockComplete()` is called.

### 12.3.5 Word Bank Generation

When input mode is WORD_BANK, the adapter generates a shuffled word bank:

1. Split the correct answer into individual words.
2. Collect distractor words from other cards in the session (words not in the correct answer).
3. Take up to `max(0, 8 - answerWordCount)` distractors.
4. Shuffle answer words + distractors together.
5. Cache per card ID to avoid regeneration on recomposition.

Word bank selection enforces duplicate counts: if the answer contains "io" twice, the user can select "io" up to twice. Fully-used words are disabled in the FlowRow.

---

## 12.4 UI Components

### 12.4.1 TrainingCardSession Composable Signature

```kotlin
@Composable
fun TrainingCardSession(
    contract: CardSessionContract,
    header: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    cardContent: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    inputControls: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    resultContent: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    navigationControls: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    completionScreen: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    progressIndicator: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    onExit: () -> Unit,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier
)
```

All customization slots receive a `TrainingCardSessionScope` that provides access to the contract, current card, result state, input text, and action callbacks.

### 12.4.2 TrainingCardSessionScope

```kotlin
@Stable
class TrainingCardSessionScope(
    val contract: CardSessionContract,
    val currentCard: SessionCard?,
    val isShowingResult: Boolean,
    val lastResult: AnswerResult?,
    val progressText: String,       // "N / Total"
    val inputText: String,
    val onInputChanged: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onExit: () -> Unit
)
```

The scope is recreated when any of its inputs change, ensuring composables always read fresh values.

### 12.4.3 Default Header

When no custom `header` slot is provided:

1. **Tense label** (conditional): shown only when the current card is a `VerbDrillCard` with a non-blank `tense` field. Rendered in `primary` color, 13sp, `SemiBold`. **Always visible regardless of HintLevel.** The tense label is reference information about the verb form, NOT a hint. HintLevel controls parenthetical hints in prompt text, not this label.
2. **Clean prompt text**: `promptRu` with parenthetical hints stripped via regex `\s*\([^)]+\)`. Rendered at 18sp, `Medium` weight.

Custom overrides: Verb Drill uses a custom header with back arrow + "Verb Drill" title (no settings gear). Daily Practice uses its own header with back arrow + "Daily Practice" title + block label chip.

### 12.4.4 Default Progress Indicator

A horizontal row split 70/30:

- **Left (70% weight):** Rounded progress bar. Green fill (`#4CAF50`) on light green track (`#C8E6C9`). Text overlay `"N / Total"` in 12sp bold. Text color switches from dark green to white when the bar passes 12% fill (readability).
- **Right (30% weight):** Circular speedometer arc (44dp). Background arc in light gray. Foreground arc sweep angle = `360 * min(speedWpm, 100) / 100`. Color: red (`#E53935`) for 0-20 wpm, yellow (`#FDD835`) for 21-40 wpm, green (`#43A047`) for 41+ wpm. Center shows numeric wpm value.

### 12.4.5 Default Card Content

Material `Card` with full-width layout:

- Left column (weighted): "RU" label + prompt text (20sp, `SemiBold`) showing `promptRu` — parenthetical hints included on EASY, stripped on MEDIUM/HARD based on `HintLevel`.
- Right: `TtsSpeakerButton` (see 12.4.9).

Custom overrides: Verb Drill and Daily Practice both add SuggestionChip rows below the prompt showing verb, tense, and group info when the card is a `VerbDrillCard`.

#### Font Size Scaling Behavioral Contract

When `textScale` is set on the contract, all default slot implementations apply it to prompt and content text:

| User Action | System Response | User Outcome |
|-------------|----------------|--------------|
| User changes text scale in Settings | `textScale` prop updates on `CardSessionContract` | All prompt text in card resizes proportionally |
| `textScale = 1.5x` | `DefaultCardContent` applies `(20f * 1.5f).sp = 30sp` to `promptRu` | Card prompt text is 50% larger |
| `textScale = 2.0x` | `DefaultHeader` applies `(18f * 2.0f).sp = 36sp` to `cleanPrompt` | Header text is doubled |

**Scaling rules:**
- Prompt text (`promptRu`) in `DefaultCardContent`: `(20f * textScale).sp`
- Clean prompt text in `DefaultHeader`: `(18f * textScale).sp`
- Tense label in `DefaultHeader`: NOT scaled (fixed 13sp)
- "RU" badge, POS badge, attempt counter: NOT scaled
- Navigation buttons, check button, mode labels: NOT scaled

### 12.4.6 Default Input Controls

When no custom `inputControls` slot is provided. Includes:

1. **OutlinedTextField** -- "Your translation" label, single line. Trailing icon: mic button (when `supportsVoiceInput`). Mic button launches system speech recognition intent with the appropriate language tag.
2. **"No cards" error text** -- shown when `currentCard` is null.
3. **Voice mode hint** -- when `currentInputMode == VOICE`, shows "Say translation: {promptRu}" in muted text.
4. **Word bank FlowRow** -- when `currentInputMode == WORD_BANK` and `supportsWordBank`. FilterChips for each word, with duplicate tracking. "Selected: N / M" label and "Undo" TextButton.
5. **Input mode selector row** -- three FilledTonalIconButtons (Mic, Keyboard, LibraryBooks) on the left. Shown when `showInputModeButtons` is true or `availableModes.size > 1`.
6. **Show answer + Flag/Report buttons** -- Eye icon with "Show answer" tooltip on the right. Warning icon with "Report sentence" tooltip (when `supportsFlagging`).
7. **Current mode label** -- "Voice" / "Keyboard" / "Word Bank" text on the right.
8. **Check button** -- full-width, enabled only when input is not blank and card exists.

### 12.4.7 Default Result Content

When `isShowingResult == true`:

- **Correct:** Green "Correct" label (`#2E7D32`, bold). TTS replay button (when `supportsTts`). "Answer: {displayAnswer}" text.
- **Incorrect:** Red "Incorrect" label (`#C62828`, bold). TTS replay button. "Answer: {displayAnswer}" text.

### 12.4.8 Default Navigation Controls

Shown only when `supportsNavigation == true`. A horizontal row:

- **Left:** Prev button (ArrowBack icon).
- **Center group:** Pause/Play button (when `supportsPause`), Exit button (StopCircle icon), Next button (ArrowForward icon).

All navigation buttons use `NavIconButton` styling: 44dp square, `surfaceVariant` background with rounded corners (12dp), 3dp `primary` accent bar at the bottom.

**Exit button** opens an `AlertDialog` confirmation: "End session? Your progress will be saved." with "End" and "Cancel" buttons.

**Pause/Play button** toggles session active state via `togglePause()`.

### 12.4.9 TtsSpeakerButton

A reusable internal composable with 4 visual states based on `TtsState`:

| TtsState | Icon | Tint |
|----------|------|------|
| `SPEAKING` | `StopCircle` | `error` (red) |
| `INITIALIZING` | `CircularProgressIndicator` (24dp, 2dp stroke) | default |
| `ERROR` | `ReportProblem` | `error` (red) |
| `IDLE` / `READY` | `VolumeUp` (24dp) | default |

### 12.4.10 Default Completion Screen

Shown when `isComplete && !isShowingResult`:

- Party popper emoji at 48sp.
- "Well done!" text at 24sp, bold.
- Progress text (e.g. "10 / 10") in muted color.
- "Done" button (full width).

Custom overrides: Verb Drill shows stats (correct/incorrect counts) + "More" button + "Exit" outlined button.

---

## 12.5 Integration Points

### 12.5.1 Verb Drill Integration

**File:** `ui/VerbDrillScreen.kt`

Verb Drill uses `TrainingCardSession` with four custom slots:

| Slot | Custom Behavior |
|------|----------------|
| `header` | Back arrow + "Verb Drill" title (no settings gear) |
| `cardContent` | Default card + SuggestionChip row (verb with rank, tense abbreviated, group). Chips open `VerbReferenceBottomSheet` or `TenseInfoBottomSheet`. |
| `inputControls` | `DefaultVerbDrillInputControls`: adds hint answer card (error-colored), incorrect feedback with attempt count, auto-voice LaunchedEffect, word bank, report sheet, uses `submitAnswerWithInput()` for retry/hint flow. |
| `completionScreen` | Stats (correct/incorrect), "More" button (`requestNextBatch()`), "Exit" outlined button. |

**Auto-voice behavior:** A `LaunchedEffect` watches `voiceTriggerToken`, `currentInputMode`, `sessionActive`, and `currentCard.id`. When in VOICE mode with an active session and a card present, it auto-launches speech recognition after a 200ms delay (1200ms delay if `showIncorrectFeedback` is true -- retry after wrong answer).

**Auto-advance after correct voice answer:** A separate `LaunchedEffect` watches `pendingAnswerResult` and `currentInputMode`. When result is correct and mode is VOICE, it auto-advances to the next card after a 500ms delay.

**Adapter:** `VerbDrillCardSessionProvider` wraps `VerbDrillViewModel`. Capabilities: all true (TTS, voice, word bank, flagging, navigation, pause).

### 12.5.2 Daily Practice Integration

**File:** `ui/DailyPracticeScreen.kt`

Daily Practice uses `TrainingCardSession` for blocks 1 (Translation) and 3 (Verb Conjugation). Block 2 (Vocab Flashcard) uses a completely separate composable (`VocabFlashcardBlock`).

**Session flow:**

1. `DailyPracticeScreen` dispatches to `CardSessionBlock` for TRANSLATE and VERBS blocks.
2. `CardSessionBlock` creates a `DailyPracticeSessionProvider` scoped by `(blockIndex, taskIndex, tasks.size)`.
3. `DailyTrainingCardSession` wraps `TrainingCardSession` with custom `cardContent` (adds verb/tense chips like Verb Drill) and custom `inputControls` (`DailyInputControls`).
4. On block completion, `onAdvanceBlock()` moves to the next block. A `BlockSparkleOverlay` shows the transition animation.

**Customizations:**
- Custom header with back arrow, "Daily Practice" title, and block label chip (primaryContainer card).
- `BlockProgressBar` (separate from TrainingCardSession's progress indicator) shows overall session progress.
- Block transition sparkle overlay between blocks.
- `DailyPracticeCompletionScreen` at the end of all 3 blocks.

**Adapter:** `DailyPracticeSessionProvider`. Capabilities: TTS true, voice true, word bank true, flagging false (no report button in daily practice), navigation true, pause true.

**Progress persistence:** `onCardAdvanced` callback is called for each card advanced (only for non-WORD_BANK modes). For `ConjugateVerb` tasks, it calls `onPersistVerbProgress`. For all tasks, it calls `onCardPracticed` for cursor advancement tracking.

### 12.5.3 Standard Lesson Training

Standard training in `GrammarMateApp.kt` does NOT currently use the `TrainingCardSession` composable. It renders its own inline UI with equivalent functionality. The design spec envisions a `TrainingCardSessionProvider` adapter that would wrap `TrainingViewModel` to satisfy the contract, but this migration has not yet been performed.

### 12.5.4 Boss Battle

Boss Battle does NOT use `TrainingCardSession`. It has its own dedicated UI with timer, pressure mechanics, and scoring that do not fit the card session pattern.

---

## 12.6 State Management

### 12.6.1 Local UI State (Composable-owned)

The `TrainingCardSession` composable manages these local state variables:

| State | Type | Purpose |
|-------|------|---------|
| `localInputText` | `String` | Current text in the input field. Reset to "" on submit and on card advance. |
| `showExitDialog` | `Boolean` | Controls exit confirmation dialog visibility. |
| `showReportSheet` | `Boolean` | Controls report bottom sheet visibility. |
| `exportMessage` | `String?` | Shows export result in an AlertDialog. |

### 12.6.2 Adapter State (Compose-observable)

Adapters store their state in `mutableStateOf` fields for direct Compose recomposition:

**VerbDrillCardSessionProvider state:**

| Field | Type | Purpose |
|-------|------|---------|
| `pendingCard` | `VerbDrillCard?` | Card currently showing feedback |
| `pendingAnswerResult` | `AnswerResult?` | Result of the last correct submission |
| `isPaused` | `Boolean` | Whether session is paused (hint shown) |
| `incorrectAttempts` | `Int` | Consecutive wrong attempts for current card |
| `hintAnswer` | `String?` | Auto-shown answer after 3 wrong attempts or eye button |
| `showIncorrectFeedback` | `Boolean` | Whether inline "Incorrect" text is shown |
| `remainingAttempts` | `Int` | Attempts left before hint (starts at 3) |
| `_inputMode` | `InputMode` | Currently selected input mode |
| `voiceTriggerToken` | `Int` | Incremented to trigger auto-voice |
| `_selectedWords` | `List<String>` | Words selected in word bank |

**DailyPracticeSessionProvider state:**

Identical fields to VerbDrillCardSessionProvider, plus:
| Field | Type | Purpose |
|-------|------|---------|
| `currentIndex` | `Int` | Position within the block's card list |
| `pendingInput` | `String` | Text being composed before submit |

### 12.6.3 ViewModel State (Flow-owned)

Adapters read from their backing ViewModel's `StateFlow` for source-of-truth data:

- `VerbDrillViewModel.uiState` provides `VerbDrillSessionState` (cards list, currentIndex, correctCount, incorrectCount, isComplete).
- `VerbDrillViewModel.ttsState` provides the current TTS playback state.
- Daily Practice reads from `DailySessionState` via callbacks rather than a direct ViewModel reference.

### 12.6.4 Card Counter and Progress

Progress is always 1-based for display: `SessionProgress(current = index + 1, total = cards.size)`.

In the Verb Drill adapter, the index is advanced by `nextCard()` (not by `submitAnswer()`), so the displayed progress always matches the card being shown or answered.

In the Daily Practice adapter, `currentIndex` is incremented directly in `nextCard()`. The provider caps display at `blockCards.size` via `coerceAtMost`.

### 12.6.5 Adapter Lifecycle

Adapters are created via `remember` with a key:

- **Verb Drill:** `remember { VerbDrillCardSessionProvider(viewModel) }` -- persists across recompositions for the entire session.
- **Daily Practice:** `remember(blockKey)` where `blockKey = Triple(blockIndex, taskIndex, tasks.size)` -- recreated when the block changes.

---

## 12.7 User Interactions

### 12.7.1 Touch/Tap Interactions

| Target | Action |
|--------|--------|
| TTS speaker button (card) | Calls `contract.speakTts()`. Starts TTS playback of the card prompt. If already speaking, stops playback. |
| TTS speaker button (result) | Calls `contract.speakTts()`. Speaks the display answer. |
| Input text field | Standard text editing. Updates `localInputText` via `scope.onInputChanged`. |
| Mic trailing icon (text field) | Switches to VOICE mode and launches system speech recognition intent. |
| Word bank FilterChip | Calls `contract.selectWordFromBank(word)`. Adds word to selection if not fully used. |
| "Undo" text button | Calls `contract.removeLastSelectedWord()`. Removes last selected word. |
| Voice mode button (selector) | Sets input mode to VOICE. In Verb Drill and Daily Practice, this also triggers `voiceTriggerToken++` which auto-launches speech recognition. |
| Keyboard mode button | Sets input mode to KEYBOARD. Clears word bank selection. |
| Word bank mode button | Sets input mode to WORD_BANK. Clears any text input. |
| Show answer button (eye) | Calls `contract.showAnswer()`. Reveals the correct answer. Disabled when hint is already shown. |
| Report button (warning) | Opens the report bottom sheet. |
| "Add to bad sentences list" | Calls `contract.flagCurrentCard()`. Closes sheet. |
| "Remove from bad sentences list" | Calls `contract.unflagCurrentCard()`. Closes sheet. |
| "Hide this card from lessons" | Calls `contract.hideCurrentCard()`. Closes sheet. |
| "Export bad sentences to file" | Calls `contract.exportFlaggedCards()`. Shows result in AlertDialog. |
| "Copy text" | Copies card info (ID, source, target) to clipboard. |
| "Check" button | Calls `scope.onSubmit()`. Validates the answer. Resets input text. |
| Prev button (nav) | Calls `scope.onPrev()`. Goes to previous card. Resets input text. |
| Pause button (nav) | Calls `contract.togglePause()`. Toggles session pause. Behavior depends on pause reason: (1) paused with hint shown (`hintAnswer != null`) → Play advances to next card; (2) paused without hint (manual pause, `hintAnswer == null`) → Play resumes the current card without advancing, preserving input text and attempt state. This applies to all adapters using `supportsPause`. |
| Exit button (nav) | Shows exit confirmation dialog. On confirm: calls `contract.requestExit()`. |
| "End" (exit dialog) | Confirms exit. Calls `contract.requestExit()`. |
| "Cancel" (exit dialog) | Dismisses exit dialog. |
| Next button (nav) | Calls `scope.onNext()`. Advances to next card. Resets input text. If session is complete, calls `onComplete()`. |
| "Done" button (completion) | Calls `scope.onExit()`. Returns to the calling screen. |
| "More" button (Verb Drill completion) | Calls `contract.requestNextBatch()`. Loads 10 more cards. |
| SuggestionChip (verb) | Opens Verb Reference Bottom Sheet showing conjugation table. |
| SuggestionChip (tense) | Opens Tense Info Bottom Sheet showing formula, usage, and examples. |
| Verb Reference TTS button | Speaks the verb infinitive via `viewModel.speakVerbInfinitive(verb)`. |

### 12.7.2 Voice Input Flow

The voice input flow differs between the default input controls and the custom adapters:

**Default input controls (TrainingCardSession):**

1. User taps mic trailing icon or voice mode button.
2. System speech recognition intent is launched with the appropriate language tag (`en-US` or `it-IT`).
3. `ActivityResultContracts.StartActivityForResult()` receives the spoken text.
4. `contract.onVoiceInputResult(spoken)` is called, which defaults to `contract.onInputChanged(spoken)`.
5. Text appears in the input field. User must tap "Check" to submit.

**Verb Drill and Daily Practice (custom inputControls):**

1. Switching to VOICE mode increments `voiceTriggerToken`.
2. `LaunchedEffect` detects the token change and auto-launches speech recognition after a 200ms delay (1200ms if retrying after incorrect answer).
3. When speech result returns, `provider.submitAnswerWithInput(spoken)` is called immediately -- no manual "Check" needed.
4. If correct, input is cleared. A `LaunchedEffect` detects the correct result and auto-advances to the next card after 400-500ms.
5. If incorrect, the spoken text is placed in the input field for manual editing.

### 12.7.3 Keyboard Interactions

- **Typing:** Standard Android text input. `onValueChange` callback updates `localInputText`.
- **Clearing incorrect feedback:** In custom adapters, typing after an incorrect answer clears the inline "Incorrect" feedback via `provider.clearIncorrectFeedback()`.
- **Clearing hint:** In custom adapters, typing after a hint is shown clears the hint, unpauses the session, and resets the attempt counter.
- **Submit:** The "Check" button is the primary submit mechanism. There is no IME action (Enter key) submission -- the user must tap the button.

### 12.7.4 Swipe Gestures

TrainingCardSession does not implement any swipe gestures. Card navigation is exclusively via the prev/next navigation buttons.

### 12.7.5 Answer Normalization

All answer comparisons go through `Normalizer.normalize()`:

1. Trim whitespace, collapse multiple spaces to single.
2. Remove time colons (`\b(\d{1,2}):\d{2}\b` to `$1`).
3. Lowercase the entire string.
4. Strip diacritical marks (NFD decomposition, remove combining characters).
5. Strip all remaining punctuation.

This ensures answers are matched regardless of capitalization, accent usage, trailing punctuation, or minor whitespace differences.

### 12.7.6 Card Presentation Order

Cards are presented in the order provided by the adapter. The composable itself does not shuffle or reorder cards. Ordering is determined by:

- **Verb Drill:** `VerbDrillViewModel` selects and orders cards based on selected tense/group filters and frequency sorting preference.
- **Daily Practice:** `DailySessionComposer` builds the task list with a fixed block order (TRANSLATE, VOCAB, VERBS). Within each block, cards are ordered by the composer's selection algorithm.

### 12.7.7 Session Completion

A session is complete when `contract.isComplete == true` and `lastResult == null` (no pending result to display).

The completion behavior varies by mode:

- **Default:** Shows "Well done!" + progress text + "Done" button.
- **Verb Drill:** Shows stats (correct/incorrect counts). If not all cards are done for today, shows "More" button to load the next batch. Always shows "Exit" outlined button.
- **Daily Practice:** Block completion triggers `onAdvanceBlock()`. If there are more blocks, a `BlockSparkleOverlay` appears briefly, then the next block starts. If all blocks are done, a final `DailyPracticeCompletionScreen` shows "Session Complete!" with a "Back to Home" button.

### 12.7.8 Error States

| Condition | UI Response |
|-----------|-------------|
| `currentCard == null` (not complete) | "No cards" error text in red. Input field disabled. Check button disabled. |
| `currentCard == null && isComplete` | Completion screen shown. |
| TTS error | Warning icon in red on speaker button. |
| Speech recognition failure | No explicit error shown. User can retry or switch to keyboard. |
| Export failure | AlertDialog showing "No bad sentences to export". |

---

## 12.8 UI Consistency — Shared Components [UI-CONSISTENCY-2025]

This section documents the shared UI components to be extracted from existing screen-specific implementations for cross-screen consistency. The goal is to unify input mode bars, report sheets, and voice auto-launch behavior across VerbDrill, VocabDrill, TrainingScreen, and DailyPractice.

---

### 12.8.1 Shared InputModeBar Component [UI-CONSISTENCY-2025]

A shared `SharedInputModeBar` composable must be extracted to `ui/components/SharedInputModeBar.kt`, unifying three current implementations.

**Current implementations to unify:**

| Screen | Component | Class path |
|--------|-----------|------------|
| VerbDrill | `VerbDrillInputModeBar` | `ui/screens/VerbDrillScreen.kt:867-955` |
| DailyPractice | `DailyInputModeBar` | `ui/DailyPracticeComponents.kt:104-173` |
| TrainingScreen | Inline in `AnswerBox` | `ui/screens/TrainingScreen.kt:681-748` |

**Shared component specification:**

```kotlin
@Composable
fun SharedInputModeBar(
    currentMode: InputMode,
    onModeChange: (InputMode) -> Unit,
    hintAvailable: Boolean,          // Whether eye button is active
    wordBankAvailable: Boolean,      // Whether word bank mode is offered
    voiceAvailable: Boolean,         // Whether mic mode is offered
    reportAvailable: Boolean,        // Whether report button is shown
    onShowHint: () -> Unit,          // Eye button callback
    onReport: () -> Unit,            // Report button callback
    hintShown: Boolean = false,      // Disable eye when hint already shown
    modifier: Modifier = Modifier
)
```

**Required buttons (left-to-right order):**

| Position | Button | Icon | Condition | Behavior |
|----------|--------|------|-----------|----------|
| 1 | Mic (VOICE) | `Mic` | `voiceAvailable` | Switches to VOICE mode, triggers `onModeChange(VOICE)` |
| 2 | Keyboard | `Keyboard` | Always | Switches to KEYBOARD mode, triggers `onModeChange(KEYBOARD)` |
| 3 | Word Bank | `MenuBook` | `wordBankAvailable` | Switches to WORD_BANK mode, triggers `onModeChange(WORD_BANK)` |
| 4 | Eye (Show Answer) | `Visibility` | `hintAvailable` | Calls `onShowHint()`. Disabled when `hintShown == true` |
| 5 | Report | `Warning` / `ChangeHistory` | `reportAvailable` | Calls `onReport()` |

**Consistency requirements:**
- All screens must position the input mode bar at the **bottom of the card area, above the Check button**.
- Button sizing: `FilledTonalIconButton` with 40dp default size.
- Active mode indication: the current mode's button uses `FilledTonal` style; inactive buttons use standard icon button style.
- Current mode label text ("Voice" / "Keyboard" / "Word Bank") appears below the button row, right-aligned.

**Regression class paths:**
- `ui/components/SharedInputModeBar.kt` — NEW shared component
- `ui/screens/VerbDrillScreen.kt:867-955` — replace `VerbDrillInputModeBar` with shared
- `ui/DailyPracticeComponents.kt:104-173` — replace `DailyInputModeBar` with shared
- `ui/screens/TrainingScreen.kt:681-748` — replace inline bar with shared

---

### 12.8.2 Shared ReportSheet Component [UI-CONSISTENCY-2025]

#### Behavioral Contract

| Action | System Response | User Outcome |
|--------|----------------|--------------|
| User taps "Add to bad sentences list" | `onFlagToggle()` -> `BadSentenceStore.add(packId, cardId, ...)` -> card.isFlagged = true | Sheet closes; next time sheet opens, option shows "Remove from bad sentences list" with red icon tint |
| User taps "Remove from bad sentences list" | `onFlagToggle()` -> `BadSentenceStore.remove(packId, cardId)` -> card.isFlagged = false | Sheet closes; next time sheet opens, option reverts to "Add to bad sentences list" |
| User taps "Hide this card" | `onHideCard()` -> `hideCurrentCard()` removes card from session (except Daily where it is a no-op) | Sheet closes; card is removed from the current session. In VerbDrill a toast says "Feature not yet available for verb drills" |
| User taps "Export bad sentences" | `onExport()` -> `BadSentenceStore.exportUnified()` -> returns file path or null | Sheet closes; AlertDialog shows file path or "No bad sentences to export" |
| User taps "Copy text" | `onCopy()` -> clipboardManager.setClip(cardId + source + target) | Sheet closes; card text is in system clipboard |

A shared `SharedReportSheet` composable must be extracted to `ui/components/SharedReportSheet.kt`, providing a consistent 4-option report bottom sheet across all screens.

**Reference implementation:** TrainingScreen AnswerBox report sheet (`ui/screens/TrainingScreen.kt`).

**4 standard options (in order):**

| # | Option | Icon | Behavior | Notes |
|---|--------|------|----------|-------|
| 1 | Flag/Unflag bad sentence | `ReportProblem` | Toggle. Red tint when card is flagged. | Text changes: "Add to bad sentences list" / "Remove from bad sentences list" |
| 2 | Hide this card from lessons | `VisibilityOff` | Hides card from future lessons. Closes sheet. | May be a no-op for some modes (verb drill); show informational toast |
| 3 | Export bad sentences | `Download` | Exports flagged cards to text file. Shows path in AlertDialog. | Returns file path or null |
| 4 | Copy text | `ContentCopy` | Copies card info to clipboard. Closes sheet. | Format varies by card type |

**Shared component specification:**

```kotlin
@Composable
fun SharedReportSheet(
    showSheet: Boolean,
    onDismiss: () -> Unit,
    isFlagged: Boolean,
    onFlagToggle: () -> Unit,
    onHideCard: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    hideEnabled: Boolean = true,      // When false, hide option shows informational toast
    cardSummaryText: String,           // Card info displayed at top of sheet
    modifier: Modifier = Modifier
)
```

**Screens adopting this component:**

| Screen | Current implementation | Class path |
|--------|----------------------|------------|
| VerbDrill | `VerbDrillReportSheet` | `ui/screens/VerbDrillScreen.kt` |
| VocabDrill | `VocabDrillReportSheet` | `ui/screens/VocabDrillScreen.kt` |
| TrainingScreen | Inline in `AnswerBox` | `ui/screens/TrainingScreen.kt` |
| DailyPractice | N/A (no report button currently) | `ui/DailyPracticeScreen.kt` |

**Regression class paths:**
- `ui/components/SharedReportSheet.kt` — NEW shared component
- `ui/components/SharedInputModeBar.kt` — NEW shared component
- `ui/components/TrainingCardSession.kt` — uses shared components in default slots
- `ui/screens/VerbDrillScreen.kt` — adopter of SharedReportSheet
- `ui/screens/TrainingScreen.kt` — reference implementation, adopter of SharedReportSheet
- `ui/screens/VocabDrillScreen.kt` — adopter of SharedReportSheet
- `ui/DailyPracticeScreen.kt` — adopter of SharedInputModeBar

---

#### Behavioral Contract -- VoiceAutoLauncher

| Action | System Response | User Outcome |
|--------|----------------|--------------|
| New card shown + voiceMode enabled | `onAutoStartVoice` callback fires -> `speechLauncher.launch(intent)` | Speech recognition dialog appears after configurable delay (500ms for new card, 1200ms after incorrect feedback) |
| Voice answer correct | `pendingAnswerResult.correct == true` && `currentInputMode == VOICE` -> auto-advance after 400-500ms delay | Next card shown automatically |
| Voice answer incorrect (attempt < 3) | `showIncorrectFeedback = true` -> `voiceTriggerToken++` -> re-launch speech after 1200ms | Speech recognition re-prompts; user sees "Incorrect" with remaining attempts |
| Callback only switches InputMode (BUG) | `setInputMode(VOICE)` called but `speechLauncher.launch()` NOT called | Speech dialog does NOT appear -- user must manually tap mic. This is the VoiceAutoLauncher bug pattern to guard against |

**CRITICAL:** The `onAutoStartVoice` callback MUST call `speechLauncher.launch(intent)` directly. Switching `InputMode` alone is INSUFFICIENT and causes the voice-not-launching bug.

---

#### Behavioral Contract -- HintAnswerCard

| Action | System Response | User Outcome |
|--------|----------------|--------------|
| Eye button clicked at HintLevel=EASY | `provider.showAnswer()` -> `hintAnswer = card.answer`, `isPaused = true` | Pink card with answer text appears below prompt card |
| Eye button clicked at HintLevel=MEDIUM | `provider.showAnswer()` -> `hintAnswer = card.answer`, `isPaused = true` | Pink card with answer text appears below prompt card (same as EASY) |
| Eye button clicked at HintLevel=HARD | `provider.showAnswer()` -> `hintAnswer = card.answer`, `isPaused = true` | Pink card with answer text appears below prompt card (same as EASY) |
| 3 wrong attempts on same card | Auto-sets `hintAnswer = card.answer`, `incorrectAttempts = 3`, `isPaused = true` | Pink card appears automatically; eye button becomes disabled |
| `hintAnswer != null` but HintLevel gate blocks render (BUG) | Pink card composable checks `hintLevel == EASY` before rendering | Answer NOT shown at MEDIUM/HARD -- this is the HintAnswerCard bug pattern to guard against |

**CRITICAL:** HintAnswerCard MUST render whenever `hintAnswer != null`, regardless of `HintLevel` setting. HintLevel controls parenthetical hints in prompt text, NOT the eye/show-answer button or the hint card.

---

#### Behavioral Contract -- SharedInputModeBar

| Action | System Response | User Outcome |
|--------|----------------|--------------|
| User taps Mic button | `onModeChange(VOICE)` -> switches input mode, triggers `voiceTriggerToken++` in adapters | Mode label shows "Voice"; auto-voice launches if VoiceAutoLauncher is active |
| User taps Keyboard button | `onModeChange(KEYBOARD)` -> switches input mode, clears word bank selection | Mode label shows "Keyboard"; text field becomes active |
| User taps Word Bank button | `onModeChange(WORD_BANK)` -> switches input mode, clears text input | Mode label shows "Word Bank"; word bank chips appear |
| User taps Eye button | `onShowHint()` -> `provider.showAnswer()` -> sets `hintAnswer`, pauses session | Eye button becomes disabled; HintAnswerCard appears |
| User taps Report button | `onReport()` -> sets `showReportSheet = true` | SharedReportSheet bottom sheet opens |
| Eye button already shown (hint active) | `hintShown == true` -> Eye button is `enabled = false` | Tapping eye button has no effect; hint card remains visible |

---

### 12.8.3 Cross-Screen Consistency Matrix [UI-CONSISTENCY-2025]

Summary of which component is the reference and which are adopters:

| UI Element | Reference Screen | Adopters | Shared Component |
|------------|-----------------|----------|------------------|
| Eye / Show Answer hint card | VerbDrill (`VerbDrillScreen.kt:392-425`) | TrainingScreen, DailyPractice | Inline (no shared component; styling guide only) |
| Voice auto mode toggle + launch | VocabDrill (`VocabDrillScreen.kt:218-241`, `:409-417`) | VerbDrill | `ui/components/VoiceAutoLauncher.kt` (NEW) |
| Report sheet (4 options) | TrainingScreen (`TrainingScreen.kt`) | VerbDrill, VocabDrill | `ui/components/SharedReportSheet.kt` (NEW) |
| Input mode bar | VerbDrill (`VerbDrillScreen.kt:867-955`) | TrainingScreen, DailyPractice | `ui/components/SharedInputModeBar.kt` (NEW) |
