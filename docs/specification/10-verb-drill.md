# 10. Verb Drill System -- Specification

This section covers the Verb Drill system: a dedicated conjugation practice mode that drills Italian verb forms by group and tense, independent from the standard lesson/sub-lesson training flow.

---

## 10.1 Overview

### 10.1.1 What Verb Drill Is

Verb Drill is a focused conjugation practice mode that presents the user with Russian prompts and expects Italian verb conjugation answers. Unlike regular lesson training, which practices full sentence translations across mixed grammar patterns, Verb Drill isolates verb morphology: each card targets a specific verb, tense, and conjugation group.

The system supports:

- **Filtering by tense** (Presente, Imperfetto, Passato Prossimo, etc.)
- **Filtering by conjugation group** (regular -are, irregular unique, etc.)
- **Three input modes**: Voice recognition, keyboard typing, and word bank
- **Retry/hint flow**: 3 attempts before auto-showing the answer
- **Verb reference sheets**: conjugation tables available per card via bottom sheets
- **Tense reference info**: formula, usage description, and examples via bottom sheets
- **Bad sentence flagging**: mark problematic cards for review/export

### 10.1.2 How It Differs from Regular Lesson Training

| Aspect | Regular Lesson Training | Verb Drill |
|--------|------------------------|------------|
| ViewModel | `TrainingViewModel` (single, all business logic) | `VerbDrillViewModel` (separate, isolated) |
| Card source | Lesson CSV files (2-column: `ru;answers`) | Verb drill CSV files (5+ column: `RU;IT;Verb;Tense;Group;Rank`) |
| Session composition | Sub-lessons with `NEW_ONLY` / `MIXED` types | 10-card batches filtered by tense/group |
| Progress tracking | Per-lesson mastery (unique card shows), flower state | Per-combo (group+tense) card IDs shown, ever and today |
| Screen | `TrainingScreen` via `GrammarMateApp` routing | `VerbDrillScreen` (own selection + session screens) |
| Navigation | Roadmap lesson tiles | Dedicated `VERB_DRILL` tile on roadmap + HomeScreen drill tile |
| Daily integration | Block 1 of Daily Practice | Block 3 of Daily Practice (via `DailySessionComposer`) |

### 10.1.3 Architectural Isolation

Verb Drill was designed as an **isolated feature** (Approach B from the design spec): it has its own ViewModel, screen, data classes, store, and parser. It does not modify `TrainingViewModel` at all. Integration with the rest of the app occurs at two points:

1. **Roadmap/HomeScreen**: a dedicated tile that navigates to `VerbDrillScreen`
2. **Daily Practice**: Block 3 reuses `VerbDrillCard` data and the retry/hint flow pattern via `DailyPracticeSessionProvider`

---

## 10.2 Verb Drill Data Model

### 10.2.1 VerbDrillCard

**File:** `app/src/main/java/com/alexpo/grammermate/data/VerbDrillCard.kt`

```kotlin
data class VerbDrillCard(
    override val id: String,
    override val promptRu: String,
    val answer: String,
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null,
    val rank: Int? = null
) : SessionCard {
    override val acceptedAnswers: List<String> get() = listOf(answer)
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `String` | Yes | Deterministic identifier: `"{group}_{tense}_{rowIndex}"` (empty strings substituted for null group/tense). Example: `"regular_are_Presente_0"`, `"irregular_unique_Imperfetto_5"` |
| `promptRu` | `String` | Yes | Russian-language prompt shown to the user. May contain parenthetical verb hints like `"я готов (essere stanco)"` |
| `answer` | `String` | Yes | The expected Italian target answer. Treated as a single accepted answer (no `+` separator like regular lesson CSVs) |
| `verb` | `String?` | No | The infinitive form of the verb being tested (e.g., `"essere"`, `"avere"`). Falls back to extraction from parenthetical hints in `promptRu` when the CSV column is absent |
| `tense` | `String?` | No | Grammatical tense name (e.g., `"Presente"`, `"Imperfetto"`, `"Passato Prossimo"`) |
| `group` | `String?` | No | Conjugation group classification (e.g., `"regular_are"`, `"irregular_unique"`, `"regular_isc"`) |
| `rank` | `Int?` | No | Frequency rank for sorting by frequency. Lower rank = more common verb |

**SessionCard interface implementation:**

`VerbDrillCard` implements `SessionCard`, making it usable in both `VerbDrillCardSessionProvider` (standalone drill) and `DailyPracticeSessionProvider` (daily practice block 3). The `acceptedAnswers` property returns a single-element list containing `answer`.

### 10.2.2 Verb, Tense, Group Classification

**Verb Groups** (Italian conjugation classification):

| Group ID | Name | Type | Endings | Approximate Card Count |
|----------|------|------|---------|----------------------|
| `regular_are` | Правильные -are | regular | -o, -i, -a, -iamo, -ate, -ano | 412 |
| `regular_ere` | Правильные -ere | regular | -o, -i, -e, -iamo, -ete, -ono | 163 |
| `regular_ire` | Правильные -ire | regular | -o, -i, -e, -iamo, -ite, -ono | 32 |
| `regular_isc` | Правильные -isc (ire) | regular | -isco, -isci, -isce, -iamo, -ite, -iscono | 57 |
| `regular_ciare_giare` | -ciare/-giare (i-drop) | regular | -o, -i, -a... (no doubling) | 69 |
| `regular_care_gare` | -care/-gare (h-insert) | regular | -co to -chi, -go to -ghi | 64 |
| `irregular_porre` | Неправильные (porre) | irregular | pon- stem | 9 |
| `irregular_tenere` | Неправильные (tenere) | irregular | ten-/tin- stem | 9 |
| `irregular_venire` | Неправильные (venire) | irregular | ven-/venn- stem | 7 |
| `irregular_cogliere` | Неправильные (cogliere) | irregular | cogl-/colg- stem | 6 |
| `irregular_dire` | Неправильные (dire) | irregular | dic- stem | 5 |
| `irregular_trarre` | Неправильные (trarre) | irregular | tragg- stem | 4 |
| `irregular_unique` | Уникальные/мелкие группы | irregular | mixed | 34 |

**Tenses** (Italian grammatical tenses used in the drill):

Presente, Imperfetto, Passato Prossimo, Passato Remoto, Trapassato Prossimo, Futuro Semplice, Futuro Anteriore, Condizionale Presente, Condizionale Passato, Congiuntivo Presente, Congiuntivo Imperfetto, Congiuntivo Passato.

The tense ladder maps lesson levels (1-12) to cumulative tenses unlocked:

| Lesson Level | Tenses Unlocked |
|-------------|-----------------|
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

### 10.2.3 VerbDrillComboProgress

Tracks cumulative progress for a specific group+tense combination.

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

| Field | Description |
|-------|-------------|
| `group` | Group filter (empty string means "all groups") |
| `tense` | Tense filter (empty string means "all tenses") |
| `totalCards` | Total number of cards matching this combo |
| `everShownCardIds` | Set of card IDs that have ever been shown. Permanent -- never resets |
| `todayShownCardIds` | Set of card IDs shown today. Resets when `lastDate` differs from current date |
| `lastDate` | ISO date string (e.g., `"2026-05-09"`) for daily reset detection |

### 10.2.4 VerbDrillSessionState

Represents an active drill session.

```kotlin
data class VerbDrillSessionState(
    val cards: List<VerbDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false
)
```

| Field | Description |
|-------|-------------|
| `cards` | The 10 cards selected for this session batch |
| `currentIndex` | Index of the current card being shown (0-based) |
| `correctCount` | Number of cards answered correctly |
| `incorrectCount` | Number of cards where hint was shown (3 wrong attempts or manual eye) |
| `isComplete` | `true` when `currentIndex` reaches `cards.size` |

### 10.2.5 VerbDrillUiState

The full UI state exposed by `VerbDrillViewModel` as a `StateFlow`.

```kotlin
data class VerbDrillUiState(
    val availableTenses: List<String> = emptyList(),
    val availableGroups: List<String> = emptyList(),
    val selectedTense: String? = null,
    val selectedGroup: String? = null,
    val totalCards: Int = 0,
    val everShownCount: Int = 0,
    val todayShownCount: Int = 0,
    val session: VerbDrillSessionState? = null,
    val allDoneToday: Boolean = false,
    val isLoading: Boolean = true,
    val loadedLanguageId: String? = null,
    val badSentenceCount: Int = 0,
    val currentCardIsBad: Boolean = false,
    val sortByFrequency: Boolean = false
)
```

### 10.2.6 VerbDrillCsvParser

**File:** `app/src/main/java/com/alexpo/grammermate/data/VerbDrillCsvParser.kt`

**Object:** `VerbDrillCsvParser` (singleton)

**Public method:** `fun parse(content: String): Pair<String?, List<VerbDrillCard>>`

Returns a pair of (optional title string, list of parsed cards).

### 10.2.7 CSV Format

Verb drill CSV files use a semicolon-delimited format with an optional title line:

```
Verb Conjugation Drill B1 Italian
RU;IT;Verb;Tense;Group;Rank
я готов;Io sono pronto.;essere;Presente;irregular_unique;1
ты был готов;Tu eri pronto.;essere;Imperfetto;irregular_unique;2
```

**Parsing rules:**

1. **Title line**: The first non-blank line is treated as a title and skipped for card parsing. A title consists of letters, digits, and spaces only, up to 160 characters. If the first line contains semicolons, it is treated as a header instead.
2. **Header line**: The first non-blank, non-title line. Column indices are mapped by matching lowercase column names: `ru`, `it`, `verb`, `tense`, `group`, `rank`.
3. **Required columns**: `RU` and `IT` are mandatory. If either column index is not found in the header, all data rows are skipped.
4. **Optional columns**: `Verb`, `Tense`, `Group`, `Rank`. If absent, the corresponding `VerbDrillCard` fields are `null`.
5. **Data rows**: Each subsequent non-blank line is parsed as a data row. Semicolons inside quoted fields are preserved (not treated as delimiters). Fields are trimmed of whitespace and surrounding quotes.
6. **Blank fields**: Rows where `RU` or `IT` is blank after trimming are skipped.
7. **Verb fallback**: If the `Verb` column is absent or empty, the parser attempts to extract the verb from a parenthetical hint in `promptRu`. Pattern: `\((\w+)` -- captures the first word inside parentheses. Example: `"я устал (essere stanco)"` yields verb `"essere"`.
8. **Deterministic IDs**: Generated as `"{group}_{tense}_{rowIndex}"` where null values are replaced with empty strings. Example: `"regular_are_Presente_0"`, `"_Presente_3"` (no group), `"__5"` (no group or tense).
9. **Rank parsing**: The `Rank` column is parsed as an integer. If parsing fails, `rank` is `null`.

**Semicolon parsing algorithm:**

The parser implements its own semicolon-splitting logic (not reusing `CsvParser.parseLine`):
- Iterates character by character
- Tracks quote state (toggled by `"`)
- Semicolons inside quotes are treated as literal characters
- All other semicolons are delimiters

### 10.2.8 SessionCard Interface

**File:** `app/src/main/java/com/alexpo/grammermate/data/CardSessionContract.kt`

Both `VerbDrillCard` and `SentenceCard` implement the `SessionCard` interface:

```kotlin
interface SessionCard {
    val id: String
    val promptRu: String
    val acceptedAnswers: List<String>
}
```

This allows both card types to be used interchangeably in `CardSessionContract` implementations, enabling code reuse between Verb Drill and Daily Practice.

---

## 10.3 Verb Drill Store

### 10.3.1 Purpose

`VerbDrillStore` persists verb drill progress to a YAML file, tracking which cards have been shown (ever and today) for each group+tense combination.

**File:** `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`

### 10.3.2 File Location

Progress files are **pack-scoped**:

| Scenario | File Path |
|----------|-----------|
| Pack-scoped (preferred) | `context.filesDir/grammarmate/drills/{packId}/verb_drill_progress.yaml` |
| Legacy global (no packId) | `context.filesDir/grammarmate/verb_drill_progress.yaml` |

The constructor takes an optional `packId` parameter. When provided, all progress is scoped to that pack. When `null`, a global file is used for backward compatibility.

### 10.3.3 YAML Schema

```yaml
schemaVersion: 1
data:
  regular_are|Presente:
    group: regular_are
    tense: Presente
    totalCards: 412
    everShownCardIds:
      - regular_are_Presente_0
      - regular_are_Presente_1
      - regular_are_Presente_15
    todayShownCardIds:
      - regular_are_Presente_0
      - regular_are_Presente_1
    lastDate: "2026-05-09"
  "|Presente":
    group: ""
    tense: Presente
    totalCards: 247
    everShownCardIds: []
    todayShownCardIds: []
    lastDate: "2026-05-09"
```

**Schema details:**

- `schemaVersion`: integer, currently `1`
- `data`: map keyed by `"{group}|{tense}"` combo string
  - Empty group or tense is represented by empty string (e.g., `"|Presente"`, `"|"`)
  - `everShownCardIds`: list of card ID strings (permanent progress)
  - `todayShownCardIds`: list of card ID strings (daily pool)
  - `lastDate`: ISO date string for daily reset detection

### 10.3.4 Daily Reset

On `loadProgress()`, the store compares each entry's `lastDate` with the current date (`java.time.LocalDate.now().toString()`):

- If `lastDate != today`: clears `todayShownCardIds` to `emptySet()` and updates `lastDate` to today
- If `lastDate == today`: preserves `todayShownCardIds` as-is

This provides a **lazy daily reset** without any alarm, timer, or background process.

### 10.3.5 Public API

| Method | Signature | Description |
|--------|-----------|-------------|
| `loadProgress` | `fun loadProgress(): Map<String, VerbDrillComboProgress>` | Reads the YAML file and returns all combo progress entries. Performs daily reset on load. Returns empty map if file does not exist. |
| `saveProgress` | `fun saveProgress(progress: Map<String, VerbDrillComboProgress>)` | Serializes the full progress map to YAML and writes via `AtomicFileWriter`. |
| `getComboProgress` | `fun getComboProgress(key: String): VerbDrillComboProgress?` | Convenience getter for a single combo. Delegates to `loadProgress()`. |
| `upsertComboProgress` | `fun upsertComboProgress(key: String, progress: VerbDrillComboProgress)` | Updates a single combo entry. Loads all progress, updates the entry, saves via `AtomicFileWriter`. |
| `loadAllCardsForPack` | `fun loadAllCardsForPack(targetPackId: String, languageId: String): List<VerbDrillCard>` | Reads all verb drill CSV files for a given pack and language from `grammarmate/drills/{packId}/verb_drill/`. Filters files by `{languageId}_` prefix and `.csv` extension. Returns parsed cards. |
| `getCardsForTenses` | `fun getCardsForTenses(packId: String, languageId: String, tenses: List<String>): List<VerbDrillCard>` | Returns all cards from a pack filtered by the given tense list. Returns empty if tenses is empty. |

### 10.3.6 Atomic Writes

All file writes go through `AtomicFileWriter.writeText(file, yaml.dump(data))`, following the project-wide rule that no store may write files directly.

### 10.3.7 Pack-Scoped Drill File Storage

After lesson pack import, verb drill CSV files are stored at:

```
context.filesDir/grammarmate/drills/{packId}/verb_drill/{languageId}_{lessonId}.csv
```

Files are filtered by `{languageId}_` prefix and `.csv` extension when loading.

---

## 10.4 VerbDrillCardSessionProvider

### 10.4.1 Purpose

`VerbDrillCardSessionProvider` is an adapter that wraps `VerbDrillViewModel` to implement the `CardSessionContract` interface. This allows the Verb Drill session to use the shared `TrainingCardSession` composable, which expects a `CardSessionContract` provider.

**File:** `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillCardSessionProvider.kt`

### 10.4.2 Design Rationale

The contract expects `submitAnswer()` to return a result while keeping the card visible for feedback, then `nextCard()` to advance to the next card. This adapter tracks the submitted card locally in Compose `mutableStateOf` fields so the UI recomposes when the result changes.

A fresh instance is created each time a session starts (via Compose `remember`).

### 10.4.3 Retry/Hint Flow

The provider implements a 3-attempt retry mechanism:

```
Card displayed
    |
    v
User submits answer
    |
    +---> Correct? --> Set pendingCard + pendingAnswerResult = CORRECT
    |                      (auto-advance in VOICE mode after 500ms delay)
    |
    +---> Wrong? --> Attempt counter++
                      |
                      +---> Attempts < 3?
                      |       |
                      |       +---> Yes: show "Incorrect" inline feedback
                      |              (auto-re-trigger voice recognition in VOICE mode)
                      |              User can retry
                      |
                      +---> No: auto-show answer as hint
                              (hintAnswer set, input controls stay visible, isPaused = true)
                              User presses Play/Continue to advance
```

**Manual "Show Answer" (eye button):** Immediately shows the answer as a hint, sets `incorrectAttempts = 3`, and pauses the session. The input controls remain visible.

**After hint is shown:** The user presses the Play button, which calls `togglePause()`. Since `isPaused == true`, this calls `nextCard()` to advance to the next card.

### 10.4.4 State Fields (Compose Observable)

| Field | Type | Description |
|-------|------|-------------|
| `pendingCard` | `VerbDrillCard?` | The card that was submitted and is showing feedback. `null` when no feedback is displayed. |
| `pendingAnswerResult` | `AnswerResult?` | The result from the last correct submission. `null` until a correct answer or hint. |
| `hintAnswer` | `String?` | When non-null, the answer is being shown as a hint. Input controls remain visible below the hint text. |
| `showIncorrectFeedback` | `Boolean` | When `true`, shows red "Incorrect" text with remaining attempts. |
| `remainingAttempts` | `Int` | Number of attempts remaining before auto-hint (starts at 3, decrements on wrong answers). |
| `incorrectAttempts` | `Int` | Consecutive wrong attempts for the current card. |
| `voiceTriggerToken` | `Int` | Incremented to trigger automatic voice recognition. Observed by `LaunchedEffect` in the UI. |

### 10.4.5 Card Session Contract Implementation

**Capabilities:**

| Capability | Value | Notes |
|------------|-------|-------|
| `supportsTts` | `true` | Uses Sherpa-ONNX TTS engine |
| `supportsVoiceInput` | `true` | Android speech recognition |
| `supportsWordBank` | `true` | Generates word bank from answer + distractors |
| `supportsFlagging` | `true` | Bad sentence flagging per card |
| `supportsNavigation` | `true` | Previous/next card navigation |
| `supportsPause` | `true` | Pause/play for hint-to-advance flow |

**Key contract properties:**

- `currentCard`: Returns `pendingCard` if feedback is showing, otherwise `session.cards[currentIndex]`, or `null` if session is complete.
- `progress`: Returns `SessionProgress(current = currentIndex + 1, total = cards.size)`.
- `isComplete`: `true` when `session.isComplete && pendingCard == null`.
- `sessionActive`: `false` when paused, hint is showing, or session is complete.
- `inputModeConfig`: All three modes available (VOICE, KEYBOARD, WORD_BANK), default VOICE, no input mode buttons shown in default config.

### 10.4.6 Word Bank Generation

Word bank words are generated per card and cached:

1. Split the card's answer into words by whitespace
2. Collect distractor words from other cards in the session (words not present in the current answer)
3. Take up to `max(0, 8 - answerWords.size)` distractors
4. Shuffle the combined list (answer words + distractors)
5. Cache by card ID to avoid regeneration on recomposition

### 10.4.7 Key Methods

| Method | Description |
|--------|-------------|
| `submitAnswerWithInput(input: String)` | Primary submission method. Normalizes input via `Normalizer.normalize()`, compares against `card.answer`. Handles retry/hint flow. |
| `nextCard()` | Clears all pending state, advances ViewModel index. Delegates to `viewModel.submitCorrectAnswer()` (correct), `viewModel.markCardCompleted()` (hint), or `viewModel.nextCardManual()` (skip). Auto-triggers voice recognition in VOICE mode. |
| `prevCard()` | Navigates to previous card, clears all pending state. |
| `showAnswer()` | Shows answer as hint (eye button). Sets `isPaused = true`. |
| `togglePause()` | If paused: calls `nextCard()` to advance. If active: pauses the session. |
| `clearIncorrectFeedback()` | Called when user starts typing a new attempt after incorrect feedback. |
| `setInputMode(mode)` | Switches input mode, clears selected words, auto-triggers voice in VOICE mode. |

---

## 10.5 VerbDrillViewModel

### 10.5.1 Purpose

`VerbDrillViewModel` is an `AndroidViewModel` that owns all verb drill business logic. It is completely separate from `TrainingViewModel` (architectural isolation -- does not modify the single-ViewModel pattern because it is a different ViewModel).

**File:** `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`

### 10.5.2 State Management

The ViewModel exposes a single `StateFlow<VerbDrillUiState>`:

```kotlin
private val _uiState = MutableStateFlow(VerbDrillUiState())
val uiState: StateFlow<VerbDrillUiState> = _uiState
```

Internal state:

| Field | Type | Description |
|-------|------|-------------|
| `allCards` | `List<VerbDrillCard>` | All parsed cards from all verb drill CSV files (loaded once, kept in memory) |
| `progressMap` | `Map<String, VerbDrillComboProgress>` | In-memory copy of progress, keyed by combo string |
| `currentPackId` | `String?` | Active pack ID for pack-scoped loading. `null` for legacy global mode. |
| `packIdForCardId` | `Map<String, String>` | Maps each card ID to its source pack ID (for bad sentence scoping) |
| `activePackIds` | `Set<String>` | All pack IDs that have verb drill cards loaded |
| `tenseInfoMap` | `Map<String, TenseInfo>` | Tense reference information loaded from YAML assets |

### 10.5.3 Initialization and Loading

**`init` block:**

Sets `isLoading = true` and launches a coroutine to call `loadCards()`.

**`loadCards(languageId?)` flow:**

1. Resolve language ID from parameter or `ProgressStore.load().languageId`
2. Get verb drill CSV files from `LessonStore.getVerbDrillFiles(packId, languageId)` or legacy `getVerbDrillFiles(languageId)`
3. For each file: read content, parse with `VerbDrillCsvParser.parse()`, map each card to its pack ID
4. Accumulate all cards, build `packIdForCardId` mapping, collect `activePackIds`
5. Extract distinct `tense` values (sorted) and `group` values (sorted) for dropdown population
6. Load progress from `VerbDrillStore.loadProgress()`
7. Sum bad sentence counts across all active packs
8. Update UI state with available tenses/groups, loading complete
9. Load tense reference info from `grammarmate/tenses/{languageId}_tenses.yaml`

**`reloadForLanguage(languageId)`**: Skips reload if language unchanged and cards already loaded.

**`reloadForPack(packId)`**: Creates a new `VerbDrillStore` scoped to the pack, then reloads cards. Skips if pack unchanged and cards already loaded.

### 10.5.4 Card Advancement

**`submitCorrectAnswer()`**:

1. Validates session is active and index is in bounds
2. Gets current card at `session.currentIndex`
3. Increments `correctCount`, advances `currentIndex` by 1
4. Sets `isComplete = true` if next index exceeds cards size
5. Updates UI state with new session, checks next card's bad sentence status
6. Persists card progress via `persistCardProgress(card)`
7. Updates speed tracking timestamp for next card
8. Updates progress display

**`markCardCompleted()`**:

Same flow as `submitCorrectAnswer()` but increments `incorrectCount` instead of `correctCount`. Used when a card is "done" but not answered correctly (hint shown or skip).

**`nextCardManual()`**:

Advances to the next card without marking correct or incorrect. Persists the current card as shown (adds to today shown IDs). If at the last card, persists it and starts a new session batch.

**`prevCard()`**:

Moves to the previous card, sets `isComplete = false`. Does not persist progress.

### 10.5.5 Answer Validation

Answer checking is performed in `VerbDrillCardSessionProvider.submitAnswerWithInput()`, not in the ViewModel directly:

```kotlin
val isCorrect = Normalizer.normalize(input) == Normalizer.normalize(card.answer)
```

**Normalization algorithm** (from `Normalizer.normalize()`):

1. Trim whitespace, collapse multiple spaces to single
2. Remove time suffixes (`HH:MM` becomes `H`)
3. Lowercase
4. Strip all punctuation except hyphens
5. Collapse whitespace again, trim

Each `VerbDrillCard` has exactly one accepted answer (`acceptedAnswers` returns `listOf(answer)`), unlike regular lesson cards which may have multiple accepted answers separated by `+`.

### 10.5.6 Input Mode Handling

Three input modes are supported:

| Mode | Behavior |
|------|----------|
| `VOICE` | Default mode. Auto-launches speech recognition when card appears. On correct answer, auto-advances after 500ms delay. On wrong answer, auto-re-triggers speech recognition. |
| `KEYBOARD` | Manual text input with "Check" button. No auto-advance. |
| `WORD_BANK` | Tap words in correct order. Selected words shown as chips. "Check" button validates the joined word string. |

### 10.5.7 Progress Persistence

**`persistCardProgress(card)`**:

1. Constructs combo key from selected group and tense: `"{group}|{tense}"`
2. Gets existing progress for this combo
3. Adds card ID to `everShownCardIds` (union)
4. Adds card ID to `todayShownCardIds` (union)
5. Calculates total cards for the current filter
6. Creates updated `VerbDrillComboProgress`
7. Updates in-memory `progressMap`
8. Calls `verbDrillStore.upsertComboProgress(comboKey, updatedProgress)`

Progress is persisted **immediately** after each card is completed, not batched at end of session.

### 10.5.8 Speed Tracking

The ViewModel tracks typing/speaking speed:

| Field | Description |
|-------|-------------|
| `cardShownTimestamp` | `System.currentTimeMillis()` when card is displayed |
| `totalAnswerTimeMs` | Cumulative milliseconds spent answering |
| `totalAnswersForSpeed` | Number of answers timed |
| `currentSpeedWpm` | Computed: `(totalAnswersForSpeed / (totalAnswerTimeMs / 60000.0)).toInt()` |

**`markCardShown()`**: Records `cardShownTimestamp`.

**`recordAnswerTime()`**: Adds elapsed time since `cardShownTimestamp` to `totalAnswerTimeMs`, increments `totalAnswersForSpeed`, resets timestamp.

### 10.5.9 Bad Sentence Support

Verb drill has full flag/unflag/export support for bad cards. Flagged cards are persisted in `BadSentenceStore` pack-scoped, with `mode = "verb_drill"`.

**Methods:**

| Method | Description |
|--------|-------------|
| `flagBadSentence()` | Adds current card to `BadSentenceStore` for its pack (resolved via `packIdForCardId` map). Sets `mode = "verb_drill"`, `sentence = card.promptRu`, `translation = card.answer`. Updates `badSentenceCount` and `currentCardIsBad` in UI state. |
| `unflagBadSentence()` | Removes current card from `BadSentenceStore`. Updates `badSentenceCount` and sets `currentCardIsBad = false`. |
| `isBadSentence()` | Returns `currentCardIsBad` from UI state (tracked per card advancement). |
| `exportBadSentences()` | Calls `BadSentenceStore.exportUnified()` which exports all flagged cards across all packs. Returns file path or `null` if no bad sentences exist. |
| `isCardBad(card)` | Private. Checks `BadSentenceStore.isBadSentence(packId, cardId)` for the card's pack-scoped entry. Called on card advancement to update `currentCardIsBad`. |

**Mode field:** `"verb_drill"` -- stored in `BadSentenceEntry.mode` to identify verb drill as the source.

**Report sheet options (from VerbDrillSession UI):**

- **"Add to bad sentences list"** (or "Remove from bad sentences list" if already flagged)
- **"Export bad sentences to file"** -- triggers `exportBadSentences()` and shows the exported file path
- **"Copy text"** -- copies card ID, source, and target to clipboard

The "Hide this card from lessons" option is visible but is a no-op for verb drill (not supported -- see discrepancy report R5).

### 10.5.10 Tense Reference Information

**Data classes:**

```kotlin
data class TenseInfo(
    val name: String,       // Full tense name: "Passato Prossimo"
    val short: String,      // Abbreviation: "P. Pross."
    val formula: String,    // Formation formula
    val usageRu: String,    // Usage explanation in Russian
    val examples: List<TenseExample>
)

data class TenseExample(
    val it: String,   // Italian example sentence
    val ru: String,   // Russian translation
    val note: String  // Usage note
)
```

**Source**: `assets/grammarmate/tenses/{languageId}_tenses.yaml`

**`loadTenseInfo(languageId)`**: Reads YAML from assets, parses into `Map<String, TenseInfo>` keyed by full tense name. Falls back to empty map on error.

**`getTenseInfo(tenseName)`**: Returns `TenseInfo?` for the given tense name.

### 10.5.11 Verb Reference

**`getConjugationForVerb(verb, tense)`**: Returns all cards from the current session matching the given verb+tense pair, sorted by original order in the session. Used to populate the verb reference bottom sheet with a conjugation table.

**`speakVerbInfinitive(verb)`**: Speaks the verb infinitive via TTS at 0.8x speed (slower than normal for clarity).

### 10.5.12 TTS Support

| Method | Description |
|--------|-------------|
| `speakTts(text, speed)` | Speaks text via Sherpa-ONNX TTS. Initializes engine if needed. Default speed 0.67. |
| `stopTts()` | Stops current TTS playback. |
| `ttsState` | `StateFlow<TtsState>` from the TTS engine. |

TTS is initialized lazily on first use, checking if the active language matches the current `languageId`. Language mapping: `"it"` -> `"it-IT"`.

---

## 10.6 VerbDrillScreen (UI)

### 10.6.1 Screen Architecture

**File:** `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt`

The `VerbDrillScreen` composable serves as the entry point and switches between two sub-screens:

1. **Selection screen** (`VerbDrillSelectionScreen`): shown when `state.session == null`
2. **Session screen** (`VerbDrillSessionWithCardSession`): shown when `state.session != null`

```kotlin
@Composable
fun VerbDrillScreen(
    viewModel: VerbDrillViewModel,
    onBack: () -> Unit
)
```

**Loading state**: When `state.isLoading == true`, shows a centered `CircularProgressIndicator` with "Loading..." text.

### 10.6.2 Selection Screen (VerbDrillSelectionScreen)

**Layout:**

```
+----------------------------------+
|  [<-]  Verb Drill                |
|                                  |
|  Время:                          |
|  [Presente            ]          |  <-- TenseDropdown (only if tenses exist)
|                                  |
|  Группа:                         |
|  [regular_are         ]          |  <-- GroupDropdown (only if groups exist)
|                                  |
|  [x] По частотности             |  <-- Checkbox toggle
|                                  |
|  Прогресс: 247 / 412            |  <-- everShownCount / totalCards
|  Сегодня: 30                     |  <-- todayShownCount
|  ████████░░░░░░░░░ 60%          |  <-- LinearProgressIndicator
|                                  |
|  [       Старт / Продолжить  ]   |  <-- Start or Continue button
|                                  |
+----------------------------------+
```

**Components:**

- **Back arrow**: navigates to previous screen
- **TenseDropdown**: shown only when `state.availableTenses` is non-empty. Options: "Все времена" (all tenses, value `null`) + each available tense. Selected value stored in `state.selectedTense`.
- **GroupDropdown**: shown only when `state.availableGroups` is non-empty. Options: "Все группы" (all groups, value `null`) + each available group. Selected value stored in `state.selectedGroup`.
- **Sort by frequency checkbox**: toggles `state.sortByFrequency`. When enabled, session cards are sorted by `rank` (ascending) instead of shuffled randomly.
- **Progress display**: shows `everShownCount / totalCards` and today count for the selected combo.
- **Start/Continue button**: text is "Продолжить" if `todayShownCount > 0`, otherwise "Старт". Calls `viewModel.startSession()`.
- **All done message**: when `state.allDoneToday == true`, shows "На сегодня всё!" instead of the start button.

### 10.6.3 Session Screen (VerbDrillSessionWithCardSession)

The session screen uses the shared `TrainingCardSession` composable with VerbDrill-specific customizations.

**Card display (cardContent lambda):**

```
+----------------------------------+
|  RU                    [Volume] |
|  я готов                         |
|                                  |
|  [essere #1 >]  [Pres.]         |  <-- Verb chip + Tense chip (if present)
+----------------------------------+
```

- **Russian prompt**: shown in 20sp semi-bold
- **Volume button**: TTS speaks the prompt text

#### Font Size Scaling Behavioral Contract

VerbDrillScreen applies `ruTextScale` (from `AudioState` / Settings) to the card prompt text:

| User Action | System Response | User Outcome |
|-------------|----------------|--------------|
| User changes text scale in Settings | VerbDrillScreen re-reads `ruTextScale` from ViewModel state | Prompt text resizes proportionally |
| `textScale = 1.5x` | `promptRu` renders at `(20f * 1.5f).sp = 30sp` | Card prompt is 50% larger |
| `textScale = 2.0x` | `promptRu` renders at `(20f * 2.0f).sp = 40sp` | Card prompt is doubled in size |

**Scaling rules:**
- Card prompt text (`promptRu`): `(20f * ruTextScale).sp`
- Hint chips (verb, tense, group): NOT scaled
- Verb group badges: NOT scaled
- Conjugation table text in reference sheets: NOT scaled
- Navigation buttons, check button: NOT scaled
- **Verb chip** (`SuggestionChip`): shows verb name (with rank if present, e.g., "essere #1"). Tappable -- opens `VerbReferenceBottomSheet`.
- **Tense chip** (`SuggestionChip`): shows abbreviated tense name. Tappable -- opens `TenseInfoBottomSheet`.

### 10.6.4 Input Controls (DefaultVerbDrillInputControls)

The input controls mirror the `AnswerBox` component used in regular training:

**Layout:**

```
+----------------------------------+
| Hint answer (if shown):          |
|  Answer: Io sono pronto.  [Vol] |
+----------------------------------+
| Incorrect  2 attempts left       |  <-- Shown on wrong answer < 3
+----------------------------------+
| Your translation            [Mic]|
| [________________________________]|
+----------------------------------+
| Say translation: я готов         |  <-- Voice mode hint
+----------------------------------+
| Tap words in correct order:      |  <-- Word bank mode
| [io] [sono] [pronto] [loro]     |
| Selected: 2 / 4      [Undo]     |
+----------------------------------+
| [Mic] [Kbd] [Book]   [Eye][Flag]|
|                       Voice      |
+----------------------------------+
| [          Check            ]    |
+----------------------------------+
```

**Components:**

- **Hint answer card**: appears when `provider.hintAnswer != null` (after 3 wrong attempts or manual eye). Shows the correct answer in red on an error-tinted card with a TTS button.
- **Incorrect feedback**: red "Incorrect" text with remaining attempts count. Shown when `provider.showIncorrectFeedback == true`.
- **Text field**: `OutlinedTextField` with "Your translation" label. Trailing icon is a microphone that switches to VOICE mode.
- **Voice mode hint**: shown when in VOICE mode, displays "Say translation: {promptRu}".
- **Word bank**: `FlowRow` of `FilterChip` words. Selected words tracked with count. "Undo" button removes last selected word.
- **Input mode buttons**: Mic (VOICE), Keyboard (KEYBOARD), Book (WORD_BANK).
- **Show answer button** (eye icon): disabled when hint already shown.
- **Flag button** (report icon): opens report bottom sheet.
- **Check button**: submits the input via `provider.submitAnswerWithInput()`. Enabled only when input is non-blank and session is active.

### 10.6.5 Voice Recognition

Voice recognition follows the same pattern as `AnswerBox`:

1. `LaunchedEffect` observes `currentCard.id`, `currentInputMode`, `sessionActive`, and `voiceTriggerToken`
2. When conditions are met (VOICE mode, session active, card exists), launches Android `RecognizerIntent` with a 200ms delay
3. Language tag mapping: `"it"` -> `"it-IT"`, others -> `"en-US"`
4. On result: sets input text, calls `submitAnswerWithInput()`, clears input
5. Auto-advance after correct voice answer: `LaunchedEffect` waits 500ms then calls `provider.nextCard()`

### 10.6.6 Verb Reference Bottom Sheet

**Triggered by**: tapping the verb chip on a card.

**Content:**

```
+----------------------------------+
|  essere                 [Volume] |
|  Группа: irregular_unique        |
|  Время: Presente                 |
|  ─────────────────────           |
|  Спряжение:                      |
|  Io sono pronto.                 |
|  Tu sei pronto.                  |
|  Lui/Lei è pronto.              |
|  ...                             |
+----------------------------------+
```

- **Verb name** with TTS button (speaks infinitive at 0.8x speed)
- **Group label** (if present)
- **Tense label** (if present)
- **Conjugation table**: all cards from the current session matching the verb+tense, showing each card's answer

### 10.6.7 Tense Info Bottom Sheet

**Triggered by**: tapping the tense chip on a card.

**Content:**

```
+----------------------------------+
|  [i] Passato Prossimo (P.Pross.) |
|                                  |
|  +-- Формула --+                 |
|  | avere/essere + participio     |
|  +--------------+                 |
|                                  |
|  Когда использовать              |
|  Действие, завершенное в прошлом |
|                                  |
|  ─────────────────────           |
|  Примеры                         |
|  +-- Card --+                    |
|  | Ho mangiato la pasta.        |
|  | Я съел пасту.                |
|  | С обычными глаголами         |
|  +-----------+                   |
+----------------------------------+
```

- **Tense name header** with Info icon and short abbreviation
- **Formula card** in a primary-tinted card
- **Usage explanation** in Russian
- **Examples**: list of cards with Italian text, Russian translation, and optional note

When `TenseInfo` is unavailable for a tense, shows a fallback with just the tense name and "Справочная информация для этого времени недоступна."

### 10.6.8 Report Bottom Sheet

**Triggered by**: tapping the flag/report button.

**Options:**

- **Add to bad sentences list** (or "Remove from bad sentences list" if already flagged)
- **Hide this card from lessons** (not yet supported for VerbDrill)
- **Export bad sentences to file**
- **Copy text** (copies card ID, source, and target to clipboard)

### 10.6.9 Completion Screen (VerbDrillCompletionScreen)

Shown when the session is complete (all 10 cards done).

```
+----------------------------------+
|                                  |
|            🎉                    |
|                                  |
|          Отлично!                |
|                                  |
|  Правильных: 7  |  Ошибок: 3    |
|                                  |
|  [         Ещё            ]      |  <-- "More" (hidden if allDoneToday)
|  [         Выход         ]       |  <-- "Exit"
|                                  |
+----------------------------------+
```

- **Stats**: shows `correctCount` and `incorrectCount`
- **"Ещё" button**: calls `viewModel.nextBatch()` to start a new 10-card batch (hidden when `allDoneToday == true`)
- **"Выход" button**: exits to selection screen

### 10.6.10 Tense Abbreviation Map

Used in tense chips to keep them compact:

| Full Name | Abbreviation |
|-----------|-------------|
| Presente | Pres. |
| Imperfetto | Imperf. |
| Passato Prossimo | P. Pross. |
| Passato Remoto | P. Rem. |
| Trapassato Prossimo | Trap. P. |
| Futuro Semplice | Fut. Sempl. |
| Futuro Anteriore | Fut. Ant. |
| Condizionale Presente | Cond. Pres. |
| Condizionale Passato | Cond. Pass. |
| Congiuntivo Presente | Cong. Pres. |
| Congiuntivo Imperfetto | Cong. Imp. |
| Congiuntivo Passato | Cong. Pass. |

Unknown tenses are truncated to 8 characters.

---

## 10.7 Integration with Daily Practice

### 10.7.1 Overview

Verb drill cards appear as **Block 3** (VERBS) of the Daily Practice session, alongside Block 1 (Translation) and Block 2 (Vocab Flashcards). The Daily Practice system is managed by `DailySessionComposer` and rendered in `DailyPracticeScreen`.

### 10.7.2 DailySessionComposer -- Verb Block Construction

**File:** `app/src/main/java/com/alexpo/grammermate/ui/helpers/DailySessionComposer.kt`

The `buildVerbBlock()` method constructs Block 3:

1. **Load cards**: reads all verb drill CSV files for the pack via `LessonStore.getVerbDrillFiles()`
2. **Filter by tenses**: only cards matching `activeTenses` (derived from the tense ladder based on lesson level) are included
3. **Exclude previously shown**: collects all `everShownCardIds` across all combos from `VerbDrillStore` progress, filters them out
4. **Weak-first scoring**: each remaining card is scored by weakness:
   - For each card's tense: `weakness = 1 - (shownInCombo / comboTotal)`
   - Higher weakness = more unshown cards in that tense = that tense needs more practice
5. **Sort**: weak-first (descending by weakness), stable within same weakness (by original index)
6. **Take 10**: selects the top `VERB_COUNT` (10) cards
7. **Assign input modes**: alternating KEYBOARD and WORD_BANK (no VOICE for daily verb block)

### 10.7.3 DailyTask.ConjugateVerb

```kotlin
class ConjugateVerb(
    override val id: String,
    val card: VerbDrillCard,
    val inputMode: InputMode
) : DailyTask() {
    override val blockType = DailyBlockType.VERBS
}
```

Each verb drill task wraps a `VerbDrillCard` with a predetermined input mode and a `DailyTask` ID prefixed with `"verb_"`.

### 10.7.4 DailyPracticeSessionProvider -- Verb Block Handling

**File:** `app/src/main/java/com/alexpo/grammermate/ui/helpers/DailyPracticeSessionProvider.kt`

`DailyPracticeSessionProvider` implements `CardSessionContract` and handles all three block types. For the VERBS block:

- **`currentCard`**: converts `DailyTask.ConjugateVerb` to `SessionCard` via `taskToSessionCard()` (returns the wrapped `VerbDrillCard`)
- **`currentVerbDrillCard()`**: returns the raw `VerbDrillCard` for verb/tense chip display in the UI
- **`submitAnswer()`**: same normalization and retry/hint flow as `VerbDrillCardSessionProvider`
- **`nextCard()`**: calls `onCardAdvanced(blockCards[currentIndex])` for progress persistence, then advances index. Only VOICE and KEYBOARD modes count as "practiced" (WORD_BANK does not trigger the callback).

### 10.7.5 Shared vs Separate State

| Aspect | Standalone Verb Drill | Daily Practice Block 3 |
|--------|----------------------|----------------------|
| ViewModel | `VerbDrillViewModel` | `TrainingViewModel` (delegates to `DailySessionComposer`) |
| Session provider | `VerbDrillCardSessionProvider` | `DailyPracticeSessionProvider` |
| Card selection | Random/shuffled (or frequency-sorted), filtered by user-selected tense/group | Weak-first ordering, filtered by tense ladder |
| Progress tracking | `VerbDrillStore` (combo-scoped) | `VerbDrillStore` (via `persistDailyVerbProgress` in `DailySessionHelper`) |
| Input modes | VOICE (default), KEYBOARD, WORD_BANK | KEYBOARD, WORD_BANK (no VOICE default) |
| Verb/tense chips | Yes (with bottom sheets) | Yes (mirrors VerbDrillScreen) |
| Bad sentence flagging | Supported (`mode = "verb_drill"`) | Supported (`mode = "daily_verb"`) via `DailyPracticeSessionProvider` |

### 10.7.6 Progress Tracking Across Both Contexts

Both standalone Verb Drill and Daily Practice Block 3 write to the same `VerbDrillStore` progress file (pack-scoped). This means:

- Cards shown in Daily Practice are marked as shown and will be excluded from future standalone Verb Drill sessions
- Cards shown in standalone Verb Drill are excluded from future Daily Practice verb blocks
- The `everShownCardIds` set grows cumulatively across both contexts
- The `todayShownCardIds` set tracks daily progress across both contexts

### 10.7.7 Repeat Session Support

The `DailySessionComposer.buildRepeatSession()` method reconstructs Block 3 from stored card IDs:

- `buildVerbBlockFromIds()` looks up cards by ID from verb drill files
- Preserves original ID order
- Uses same input mode assignment (alternating KEYBOARD/WORD_BANK)

This allows the daily practice "Repeat" function to replay the exact same verb cards from the first session of the day.

---

## 10.8 User Stories

### US-10.1: Practice Verb Conjugations

**As a user**, I want to practice Italian verb conjugations grouped by tense and conjugation type, so that I can build automatic recall of verb forms separately from sentence translation practice.

### US-10.2: Filter by Tense

**As a user**, I want to select a specific tense (e.g., Passato Prossimo) from a dropdown and practice only cards for that tense, so that I can focus on tenses I find difficult.

### US-10.3: Filter by Conjugation Group

**As a user**, I want to select a specific conjugation group (e.g., irregular verbs, -are verbs) from a dropdown, so that I can target practice at specific verb patterns.

### US-10.4: Track Progress Per Combo

**As a user**, I want to see how many cards I have practiced for a given tense+group combination (both ever and today), so that I can measure my coverage of conjugation forms.

### US-10.5: Daily Card Pool

**As a user**, I want the system to track which cards I have already practiced today and exclude them from new sessions, so that each session gives me fresh cards. When I have exhausted all cards for a combo, I want to see "На сегодня всё!" and come back tomorrow.

### US-10.6: Retry Wrong Answers

**As a user**, when I answer incorrectly, I want to see "Incorrect" feedback with remaining attempts, and have up to 3 attempts before the answer is shown, so that I can learn from my mistakes without getting stuck.

### US-10.7: Show Answer on Demand

**As a user**, I want to press an eye button to reveal the answer immediately when I am stuck, so that I can learn the correct form without wasting time.

### US-10.8: Voice Input

**As a user**, I want to practice verb conjugations by speaking the answer aloud, so that I build production skills (not just recognition). I want voice recognition to auto-start when a new card appears and auto-retry when my answer is wrong.

### US-10.9: Keyboard Input

**As a user**, I want to type conjugation answers using the keyboard, so that I can practice in quiet environments where voice input is not practical.

### US-10.10: Word Bank Mode

**As a user**, I want to compose answers by tapping word tiles in the correct order, so that I can practice when I am unsure of exact spelling but know the word order.

### US-10.11: Verb Reference Sheet

**As a user**, when practicing a card for the verb "essere", I want to tap the verb chip and see the full conjugation table for that verb in the current tense, so that I can review the pattern while practicing.

### US-10.12: Tense Reference Info

**As a user**, when practicing a card in Passato Prossimo, I want to tap the tense chip and see the formation formula, usage rules in Russian, and example sentences, so that I can understand when and how to use that tense.

### US-10.13: Sort by Frequency

**As a user**, I want to toggle a "По частотности" option that sorts cards by verb frequency (most common first), so that I can prioritize learning the most useful verbs.

### US-10.14: Flag Bad Sentences

**As a user**, when I encounter a card with an error or unnatural phrasing, I want to flag it as a "bad sentence" and later export all flagged cards to a text file, so that I can report issues to content creators.

### US-10.15: Session Completion

**As a user**, after completing a 10-card batch, I want to see my score (correct vs. incorrect) and choose to practice more cards or exit, so that I have a sense of accomplishment and control over my practice session.

### US-10.16: Verb Drill in Daily Practice

**As a user**, I want verb conjugation cards to appear as the third block of my Daily Practice session, selected automatically based on my lesson level and weak areas, so that conjugation practice is integrated into my daily routine without requiring separate navigation.

### US-10.17: Pack-Scoped Progress

**As a user**, I want my verb drill progress to be tracked per lesson pack, so that switching between different language packs (e.g., Italian B1, Italian B2) keeps progress independent.

### US-10.18: TTS for Verb Drill

**As a user**, I want to hear the prompt spoken aloud via TTS, and also hear the correct answer after revealing it, so that I can associate sounds with written forms. I also want to hear the verb infinitive pronounced slowly when viewing the verb reference sheet.

### US-10.19: Navigate Between Cards

**As a user**, I want to go back to previous cards in the current session, so that I can review cards I already answered or check something I missed.

---

## 10.9 UI Consistency Requirements [UI-CONSISTENCY-2025]

This section documents the UI consistency requirements for VerbDrill, established by cross-screen audit. VerbDrill is the REFERENCE implementation for eye/show-answer mode and the ADOPTER of voice auto mode (from VocabDrill) and the 4-option report sheet (from TrainingScreen).

---

### 10.9.1 Voice Input (Auto) Mode [UI-CONSISTENCY-2025]

VerbDrill must adopt the "Voice input (auto)" toggle pattern from VocabDrillScreen. This replaces the current VOICE input mode selection with an explicit toggle on the selection screen.

**Reference implementation:** VocabDrillScreen.kt lines 218-241 (Switch toggle) and lines 409-417 (auto-launch LaunchedEffect).

**Requirements:**

1. **Selection screen toggle:** Add a "Voice input (auto)" Switch toggle with Mic icon to `VerbDrillSelectionScreen`, positioned below the filter dropdowns and above the start button.
   - Class path: `ui/screens/VerbDrillScreen.kt` — `VerbDrillSelectionScreen` composable

2. **Auto-launch on new card:** When `voiceModeEnabled == true`, mic auto-launches on each new card with a **500ms delay** (matching VocabDrill timing).
   - Class path: `ui/VerbDrillCardSessionProvider.kt` — `voiceTriggerToken` field and auto-launch logic

3. **Auto-advance on correct voice answer:** After a correct voice answer, auto-advance to the next card after **800ms delay** (matching VocabDrill timing, not the current 500ms).
   - Class path: `ui/VerbDrillCardSessionProvider.kt` — `pendingAnswerResult` observer

4. **Voice mode state field:** Add `voiceModeEnabled: Boolean = false` to `VerbDrillUiState`.
   - Class path: `data/VerbDrillCard.kt` — `VerbDrillUiState` data class

5. **Shared component:** Extract the voice auto-launch logic into a shared composable `ui/components/VoiceAutoLauncher.kt` used by both VerbDrill and VocabDrill.
   - Class path: `ui/components/VoiceAutoLauncher.kt` — NEW shared component

**Regression class paths:**
- `ui/screens/VerbDrillScreen.kt` — selection screen toggle + session voice behavior
- `ui/VerbDrillCardSessionProvider.kt` — auto-launch token and auto-advance timing
- `ui/components/VoiceAutoLauncher.kt` — shared auto-launch composable (NEW)
- `ui/screens/VocabDrillScreen.kt:218-241` — reference toggle implementation
- `ui/screens/VocabDrillScreen.kt:409-417` — reference auto-launch implementation

---

### 10.9.2 Eye / Show Answer Mode — Reference Implementation [UI-CONSISTENCY-2025]

VerbDrill's eye/show-answer mode is the **REFERENCE implementation** that all other screens (TrainingScreen, DailyPractice) must match.

**IMPORTANT:** Eye mode works at ALL HintLevel settings. The hint card renders whenever `provider.hintAnswer != null`, regardless of whether hintLevel is EASY, MEDIUM, or HARD. HintLevel controls parenthetical hints in prompt text (e.g., stripping `(dire)` and `(verità)` from the card body), NOT the eye/show-answer button or the hint answer card visibility. Any guard that checks `hintLevel == EASY` before rendering the HintAnswerCard is a bug.

**Reference implementation:** VerbDrillScreen.kt lines 392-425 (hint answer card with errorContainer tint, red answer text, inline TTS replay button).

**Visual specification:**

1. **Hint answer card:** When `provider.hintAnswer != null`, the answer is shown in a pink `Card` with `MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)` background tint.
   - Class path: `ui/screens/VerbDrillScreen.kt:392-400` — hint card composable

2. **Red answer text:** The answer text is rendered with `MaterialTheme.colorScheme.error` color in the format "Answer: {answer}".
   - Class path: `ui/screens/VerbDrillScreen.kt:401-405` — answer text styling

3. **Inline TTS replay button:** A red-tinted `VolumeUp` icon button is placed inline next to the answer text for TTS replay. The icon uses `MaterialTheme.colorScheme.error` tint.
   - Class path: `ui/screens/VerbDrillScreen.kt:406-415` — TTS replay button

4. **Eye button disabled guard:** The eye/show-answer button is disabled while a hint is already shown, guarded by `provider.hintAnswer == null`.
   - Class path: `ui/screens/VerbDrillScreen.kt` — eye button enabled state

**Other screens adopting this pattern:**
- `ui/screens/TrainingScreen.kt` — AnswerBox hint card
- `ui/DailyPracticeScreen.kt` — DailyInputControls hint card

**Regression class paths:**
- `ui/screens/VerbDrillScreen.kt:392-425` — reference implementation (DO NOT DEVIATE)
- `ui/screens/TrainingScreen.kt:681-748` — adopter (must match)
- `ui/DailyPracticeScreen.kt` — adopter (must match)

---

### 10.9.3 Report Sheet — 4-Option Standard [UI-CONSISTENCY-2025]

The VerbDrill report sheet must adopt the 4-option standard from TrainingScreen, adding the missing "Hide this card from lessons" option.

**Current state:** `VerbDrillReportSheet` has 3 options (flag/unflag, export, copy).
**Required state:** 4 options matching TrainingScreen (flag/unflag, HIDE CARD, export, copy).

**4 standard options (in order):**

| # | Option | Icon | Behavior |
|---|--------|------|----------|
| 1 | Flag/Unflag bad sentence | `ReportProblem` (toggles) | Calls `flagBadSentence()` or `unflagBadSentence()`. Red tint when flagged. |
| 2 | **Hide this card from lessons** | `VisibilityOff` | Calls `hideCurrentCard()`. Closes sheet. |
| 3 | Export bad sentences | `Download` | Calls `exportBadSentences()`. Shows file path in AlertDialog. |
| 4 | Copy text | `ContentCopy` | Copies card ID, source, target to clipboard. Closes sheet. |

**Shared component:** Extract into `ui/components/SharedReportSheet.kt` used by VerbDrill, TrainingScreen, and VocabDrill.

**Implementation notes:**
- Option 2 (Hide) is currently a no-op for VerbDrill (no hidden-card infrastructure). The option must still appear in the sheet for UI consistency. A toast or snackbar should indicate "Feature not yet available for verb drills."
- The shared component must accept a `HideOptionConfig` parameter to control whether hide is functional or informational.

**Regression class paths:**
- `ui/screens/VerbDrillScreen.kt` — `VerbDrillReportSheet` composable (update to 4 options)
- `ui/components/SharedReportSheet.kt` — shared report sheet (NEW)
- `ui/screens/TrainingScreen.kt` — `AnswerBox` report sheet (reference for 4-option layout)
- `ui/screens/VocabDrillScreen.kt` — `VocabDrillReportSheet` (adopter)
