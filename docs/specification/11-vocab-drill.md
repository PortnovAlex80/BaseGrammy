# 11. Vocab Drill System — Specification

## 11.1 Overview

The Vocab Drill system is an Anki-style spaced repetition flashcard module that lets users practice vocabulary from their installed lesson pack. It is one of the three drill types available in GrammarMate (alongside verb conjugation drills and sentence translation).

**Purpose:** Build passive and active vocabulary recall for the target language (Italian) through repeated exposure and self-assessment, using the same interval ladder `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` days shared with the rest of the app's spaced repetition engine.

**Supported word categories** (parts of speech):

| POS key      | Display label | Source CSV file            |
|--------------|---------------|---------------------------|
| `nouns`      | Nouns         | `drill_nouns.csv`          |
| `verbs`      | Verbs         | `drill_verbs.csv`          |
| `adjectives` | Adj.          | `drill_adjectives.csv`     |
| `adverbs`    | Adv.          | `drill_adverbs.csv`        |
| `numbers`    | Numbers       | `drill_numbers.csv`        |
| `pronouns`   | Pronouns      | `drill_pronouns.csv`       |

All CSV files are located in the pack's vocab drill directory (e.g. `grammarmate/drills/{packId}/vocab_drill/`) after import, or under `assets/grammarmate/vocab/it/` for default data.

**Two access points:**

1. **Standalone drill** — user navigates to the VocabDrillScreen from HomeScreen, configures filters, and starts a session. Managed by `VocabDrillViewModel`.
2. **Daily Practice block 2** — 5 vocab flashcards automatically selected by SRS priority, interleaved into the daily practice session. Managed by `DailySessionComposer` + `TrainingViewModel`.

---

## 11.2 Data Model

### 11.2.1 VocabWord

Source file: `data/VocabWord.kt`

```kotlin
data class VocabWord(
    val id: String,                              // e.g. "nouns_106_casa", "verbs_1_essere"
    val word: String,                            // Italian word (the canonical form)
    val pos: String,                             // "nouns", "verbs", "adjectives", "adverbs", "numbers", "pronouns"
    val rank: Int,                               // Frequency rank (from CSV, or synthetic for numbers/pronouns)
    val meaningRu: String? = null,               // Russian translation, may contain "/" for synonyms
    val collocations: List<String> = emptyList(), // Italian example phrases (semicolon-separated in CSV)
    val forms: Map<String, String> = emptyMap()  // Gender/number forms for adjectives, numbers, pronouns
)
```

**ID format:** `{pos}_{rank}_{word}` (e.g. `nouns_106_casa`, `adjectives_776_solito`).

**Forms map keys** (present only for adjectives, numbers, and pronouns):

| Key         | Meaning                              | Used for              |
|-------------|--------------------------------------|-----------------------|
| `msg`       | Masculine singular                   | Adjectives            |
| `fsg`       | Feminine singular                    | Adjectives            |
| `mpl`       | Masculine plural                     | Adjectives            |
| `fpl`       | Feminine plural                      | Adjectives            |
| `form_m`    | Masculine form                       | Numbers               |
| `form_f`    | Feminine form                        | Numbers               |
| `form_sg_m` | Singular masculine form              | Pronouns              |
| `form_sg_f` | Singular feminine form               | Pronouns              |
| `form_pl_m` | Plural masculine form                | Pronouns              |
| `form_pl_f` | Plural feminine form                 | Pronouns              |

**Hard word classification:** Words with `rank > 100` are flagged as `isHard` during CSV parsing. This flag is carried by `ItalianDrillRow.isHard` but not currently exposed on `VocabWord` itself.

### 11.2.2 ItalianDrillRow (parser intermediate)

Source file: `data/ItalianDrillVocabParser.kt`

```kotlin
data class ItalianDrillRow(
    val rank: Int,
    val word: String,
    val collocations: List<String>,
    val isHard: Boolean,              // rank > 100
    val meaningRu: String? = null,
    val forms: Map<String, String> = emptyMap()
)
```

This is the raw parsed row before conversion to `VocabWord`. The parser returns `ItalianDrillRow` objects; the ViewModel/composer converts them to `VocabWord` by constructing the compound ID.

### 11.2.3 VocabEntry (legacy asset format)

Source file: `data/Models.kt`

```kotlin
data class VocabEntry(
    val id: String,                  // e.g. "it_drill_drill_nouns.csv_106"
    val lessonId: String,
    val languageId: String,
    val nativeText: String,          // Italian word
    val targetText: String,          // Collocations joined with "+" or the word itself
    val isHard: Boolean = false
)
```

This model is used only by `ItalianDrillVocabParser.loadAllFromAssets()`, a legacy method that loads from bundled asset files. The current pack-scoped loading path does not use `VocabEntry`; it uses `ItalianDrillRow` -> `VocabWord` directly.

### 11.2.4 WordMasteryState

Source file: `data/VocabWord.kt`

```kotlin
data class WordMasteryState(
    val wordId: String,
    val intervalStepIndex: Int = 0,    // 0-9, index into SpacedRepetitionConfig.INTERVAL_LADDER_DAYS
    val correctCount: Int = 0,         // Total lifetime correct answers
    val incorrectCount: Int = 0,       // Total lifetime incorrect answers
    val lastReviewDateMs: Long = 0L,   // Epoch millis of last review
    val nextReviewDateMs: Long = 0L,   // Computed: lastReviewDateMs + ladder[step] * DAY_MS
    val isLearned: Boolean = false     // Reached step >= 3 (LEARNED_THRESHOLD)
)
```

**Key behaviors:**

- **New word:** `intervalStepIndex = 0`, `lastReviewDateMs = 0`, `nextReviewDateMs = 0`. A word is "due immediately" when `lastReviewDateMs == 0`.
- **Learned threshold:** A word is considered "learned" when `intervalStepIndex >= 3` (i.e. it has advanced past the 3rd rung on the interval ladder). The interval at step 3 is 7 days.
- **Day constant:** `DAY_MS = 86_400_000L` (milliseconds in one day).
- **Next review computation:** `lastReviewMs + INTERVAL_LADDER_DAYS[step] * DAY_MS`.

### 11.2.5 VocabDrillCard

```kotlin
data class VocabDrillCard(
    val word: VocabWord,
    val mastery: WordMasteryState
)
```

Combines a vocab word with its current mastery state for use in a drill session.

### 11.2.6 VocabDrillDirection

```kotlin
enum class VocabDrillDirection { IT_TO_RU, RU_TO_IT }
```

| Value       | Prompt language | Expected answer |
|-------------|-----------------|-----------------|
| `IT_TO_RU`  | Italian word shown | User says/writes Russian meaning |
| `RU_TO_IT`  | Russian meaning shown | User says/writes Italian word |

### 11.2.7 VoiceResult

```kotlin
enum class VoiceResult { CORRECT, WRONG, SKIPPED }
```

Outcome of a voice recognition attempt on a flashcard.

### 11.2.8 VocabDrillUiState

```kotlin
data class VocabDrillUiState(
    val isLoading: Boolean = true,
    val availablePos: List<String> = emptyList(),
    val selectedPos: String? = null,              // null = "All"
    val rankMin: Int = 0,
    val rankMax: Int = Int.MAX_VALUE,
    val dueCount: Int = 0,
    val totalCount: Int = 0,
    val session: VocabDrillSessionState? = null,
    val loadedLanguageId: String? = null,
    val drillDirection: VocabDrillDirection = VocabDrillDirection.IT_TO_RU,
    val voiceModeEnabled: Boolean = false,
    val masteredCount: Int = 0,
    val masteredByPos: Map<String, Int> = emptyMap()
)
```

Top-level state for the standalone VocabDrillScreen. `session` is non-null when a drill session is active.

### 11.2.9 VocabDrillSessionState

```kotlin
data class VocabDrillSessionState(
    val cards: List<VocabDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false,
    val isFlipped: Boolean = false,               // Card flipped to reveal answer
    val direction: VocabDrillDirection = VocabDrillDirection.IT_TO_RU,
    val voiceAttempts: Int = 0,                   // 0-3 per card
    val voiceRecognizedText: String? = null,
    val voiceResult: VoiceResult? = null,         // CORRECT, WRONG, SKIPPED
    val voiceCompleted: Boolean = false,          // True after correct, 3 wrongs, or skip
    val voiceModeEnabled: Boolean = false         // Auto-launch voice input on new cards
)
```

### 11.2.10 DailyTask.VocabFlashcard (Daily Practice integration)

Source file: `data/Models.kt`

```kotlin
data class VocabFlashcard(
    override val id: String,          // "voc_{pos}_{rank}_{word}"
    val word: VocabWord,
    val direction: VocabDrillDirection
) : DailyTask() {
    override val blockType = DailyBlockType.VOCAB
}
```

---

## 11.3 Word Mastery Store

Source file: `data/WordMasteryStore.kt`

### 11.3.1 Purpose

Persists per-word spaced repetition state to YAML. Used by both `VocabDrillViewModel` (standalone drill) and `DailySessionComposer` (daily practice block 2).

### 11.3.2 File Location

| Mode         | Path                                                  |
|--------------|-------------------------------------------------------|
| Pack-scoped  | `grammarmate/drills/{packId}/word_mastery.yaml`       |
| Legacy       | `grammarmate/word_mastery.yaml` (when packId is null) |

### 11.3.3 YAML Schema

```yaml
schemaVersion: 1
data:
  nouns_106_casa:
    intervalStepIndex: 3
    correctCount: 7
    incorrectCount: 1
    lastReviewDateMs: 1715500800000
    nextReviewDateMs: 1716105600000
    isLearned: true
  verbs_1_essere:
    intervalStepIndex: 0
    correctCount: 0
    incorrectCount: 0
    lastReviewDateMs: 0
    nextReviewDateMs: 0
    isLearned: false
```

All fields are required for each word entry.

### 11.3.4 Key Methods

| Method                       | Description                                                                 |
|------------------------------|-----------------------------------------------------------------------------|
| `loadAll()`                  | Reads full YAML file, returns `Map<String, WordMasteryState>`               |
| `saveAll(mastery)`           | Writes full map via `AtomicFileWriter`                                      |
| `getMastery(wordId)`         | Returns state for a single word, or null if never reviewed                  |
| `upsertMastery(state)`       | Load-modify-save for a single word                                          |
| `getDueWords()`              | Returns set of word IDs where `nextReviewDateMs <= now` or `lastReviewDateMs == 0` |
| `getMasteredCount(pos?)`     | Count of words where `isLearned == true`, optionally filtered by POS prefix  |
| `getMasteredByPos()`         | Map of POS -> count of mastered words                                       |

### 11.3.5 Due Word Criteria

A word is considered **due** when:
- `nextReviewDateMs <= System.currentTimeMillis()` (scheduled review has elapsed), OR
- `lastReviewDateMs == 0` (never reviewed, due immediately)

### 11.3.6 Learned Criteria

A word is considered **learned** when `isLearned == true`, which is set when `intervalStepIndex >= 3`. The interval at step 3 is 7 days, meaning the word has survived at least three spaced repetitions.

---

## 11.4 VocabProgressStore (Legacy)

Source file: `data/VocabProgressStore.kt`

### 11.4.1 Purpose

Legacy store for per-lesson vocab sprint progress and a separate SRS system. This store uses a different interval ladder `[1, 3, 7, 14, 30]` and a lesson-scoped progress model. It is **not used** by the current `VocabDrillViewModel` or `DailySessionComposer`, which use `WordMasteryStore` instead.

### 11.4.2 File Location

`grammarmate/vocab_progress.yaml`

### 11.4.3 YAML Schema

```yaml
schemaVersion: 1
data:
  {languageId}:
    {lessonId}:
      completedIndices: [0, 1, 5]
      entries:
        {entryId}:
          lastCorrectMs: 1715500800000
          lastIncorrectMs: 0
          intervalStep: 2
```

### 11.4.4 Interval Ladder

`[1, 3, 7, 14, 30]` days (5 steps, indexed 0-4).

### 11.4.5 Key Methods

| Method                        | Description                                             |
|-------------------------------|---------------------------------------------------------|
| `get(lessonId, languageId)`   | Get sprint progress for a lesson                        |
| `saveCompletedIndices(...)`   | Save set of completed entry indices                     |
| `addCompletedIndex(...)`      | Add one completed index                                 |
| `clearSprintProgress(...)`    | Clear all completed indices for a lesson                |
| `recordCorrect(...)`          | Advance interval step, update lastCorrectMs             |
| `recordIncorrect(...)`        | Reset interval step to 0, update lastIncorrectMs        |
| `isDueForReview(...)`         | Check if entry is past its review interval              |
| `sortEntriesForSprint(...)`   | Anki-like ordering: overdue > new > not due             |

**Note:** This store's `sortEntriesForSprint()` orders words as: overdue (shuffled) first, then new words (shuffled), then not-due words (shuffled).

---

## 11.4a ItalianDrillVocabParser Additional Methods

Source file: `data/ItalianDrillVocabParser.kt`

### `parse(inputStream, fileName) -> List<ItalianDrillRow>`

Auto-detects the CSV format from the header row:

| Header contains                       | Parser method                | POS type    |
|---------------------------------------|------------------------------|-------------|
| `rank` + (`verb` or `noun`)           | `parseRankWordCollocations`  | verbs/nouns |
| `rank` + `adjective`                  | `parseAdjectives`            | adjectives  |
| `rank` + `adverb`                     | `parseAdverbs`               | adverbs     |
| `category` + `italian`                | `parseNumbers`               | numbers     |
| `type` + `person`                     | `parsePronouns`              | pronouns    |
| (fallback: unrecognized header)       | `parseRankWordCollocations`  | verbs/nouns |

The `ru` column is auto-detected by scanning for column headers `ru`, `meaning_ru`, or `russian` (case-insensitive).

### `loadAllFromAssets(context, lessonId, languageId) -> List<VocabEntry>`

Legacy method that loads from hardcoded asset paths (`grammarmate/vocab/it/`). Loads all six CSV files in order: adjectives, adverbs, nouns, numbers, pronouns, verbs. Produces `VocabEntry` objects (not `VocabWord`). **Not used by the pack-scoped loading path.**

### `splitCsvLine(line) -> List<String>`

CSV line splitter that respects double-quoted fields. Commas inside quotes are treated as part of the field value.

---

## 11.5 VocabDrillViewModel

Source file: `ui/VocabDrillViewModel.kt`

### 11.5.1 Architecture

`VocabDrillViewModel` is an `AndroidViewModel` that manages the standalone vocab drill screen. It is a **separate ViewModel** from `TrainingViewModel` (exempt from the single-ViewModel rule because it manages its own isolated domain).

Dependencies:
- `LessonStore` -- for accessing vocab drill CSV files from installed packs
- `WordMasteryStore` -- for per-word SRS state (recreated when pack changes)
- `BadSentenceStore` -- for flagging/unflagging bad vocab cards
- `TtsEngine` -- for pronouncing Italian words

### 11.5.2 Initialization Flow

1. `init` block sets `isLoading = false`. Does **not** auto-load data.
2. Caller must invoke either:
   - `reloadForPack(packId, languageId)` — pack-scoped loading (preferred)
   - `reloadForLanguage(languageId)` — loads from all installed packs (legacy fallback)
3. Both methods are idempotent: if the same pack+language is already loaded, they return immediately.

### 11.5.3 reloadForPack(packId, languageId)

1. Checks idempotency (same packId + languageId + words already loaded).
2. Sets `activePackId`.
3. Recreates `WordMasteryStore` scoped to the pack: `WordMasteryStore(application, packId = packId)`.
4. Launches coroutine to call `loadWords(languageId)`.

### 11.5.4 loadWords(languageId)

1. Determines source files:
   - Pack-scoped: `lessonStore.getVocabDrillFiles(pack, lang)`
   - No pack: scans all installed packs with `flatMap`
2. Parses each file via `ItalianDrillVocabParser.parse()`.
3. Derives POS from filename: removes `{lang}_` prefix, `drill_` prefix, `.csv` suffix (e.g. `drill_nouns.csv` -> `"nouns"`).
4. Constructs `VocabWord` for each row with ID `{pos}_{rank}_{word}`.
5. Sorts all words by `rank`.
6. Loads mastery map from `WordMasteryStore.loadAll()`.
7. Extracts distinct POS list for the filter chips.
8. Calls `updateCounts()`.

### 11.5.5 Card Selection Algorithm (startSession)

When the user taps "Start":

1. Filter words by current `selectedPos` and `rankMin`/`rankMax`.
2. For each filtered word, check if it is **due**:
   - `mastery.nextReviewDateMs <= now` OR `mastery.lastReviewDateMs == 0`
3. Create `VocabDrillCard` for each due word.
4. Sort due cards by `word.rank` (ascending — lowest rank first).
5. Take up to **10** cards.
6. If no due cards exist, set `session = null` (no session started).
7. Create `VocabDrillSessionState` with the selected cards, current direction, and voice mode.

### 11.5.6 Filtering

Users can filter the word pool before starting a session:

| Filter         | Values                                              | Default       |
|----------------|-----------------------------------------------------|---------------|
| Part of speech | `null` (All), `nouns`, `verbs`, `adjectives`, `adverbs`, `numbers`, `pronouns` | `null` (All)  |
| Rank range     | Top 100 (0-100), Top 500 (0-500), Top 1000 (0-1000), All (0-MAX) | All           |

**Note:** When "All" POS is selected (`selectedPos == null`), words with `pos == "numbers"` are excluded from the filtered set. Numbers only appear when explicitly selected via the POS filter.

`updateCounts()` recomputes `dueCount`, `totalCount`, `masteredCount`, and `masteredByPos` after any filter change.

### 11.5.7 Flashcard Flip Behavior

- **flipCard()** — toggles `session.isFlipped`.
- When flipped, the card back shows the answer (translation, forms, collocations).
- The front shows the prompt (Italian word for IT_TO_RU, Russian meaning for RU_TO_IT).
- Auto-flip occurs 800ms after voice input completes (correct, wrong after 3 attempts, or skip).

### 11.5.8 Answer Rating System (Anki-style)

After the card is flipped, the user sees four rating buttons:

| Rating | Behavior                                    | Step Delta | Displayed interval |
|--------|---------------------------------------------|------------|--------------------|
| Again  | Resets step to 0                            | -100 (clamped to 0) | "<1m"        |
| Hard   | Stays at current step                       | 0          | `ladder[currentStep]`d |
| Good   | Advances 1 step                             | +1         | `ladder[currentStep+1]`d |
| Easy   | Advances 2 steps                            | +2         | `ladder[currentStep+2]`d |

The interval ladder is: `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` days (from `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS`).

**Rating logic in `answerRating()`:**

1. Gets current card and its mastery state.
2. Computes new step index:
   - Again: always resets to 0.
   - Others: `currentStep + delta`, clamped to `[0, maxStep]` where maxStep = 9.
3. Computes `nextReviewDateMs` via `WordMasteryState.computeNextReview(now, newStepIndex)`.
4. Sets `isLearned = (newStepIndex >= 3)`.
5. Updates `correctCount` / `incorrectCount`:
   - Again: `incorrectCount++`
   - Others: `correctCount++`
6. Writes updated mastery to in-memory map and persists via `masteryStore.upsertMastery()`.
7. Calls `advanceCard()` to move to next card.

### 11.5.9 Voice Input

**Voice mode toggle:** When enabled, the mic launches automatically on each new card (after a 500ms delay for animation).

**Voice matching logic (`handleVoiceResult()`):**

1. Determines expected answer based on direction:
   - `IT_TO_RU`: expected = `meaningRu` (e.g. "быть/являться")
   - `RU_TO_IT`: expected = `word` (Italian, e.g. "essere")
2. Splits expected answer by "/" to get multiple valid answers.
3. Normalizes both recognized text and expected answers: trim, lowercase, strip trailing punctuation `[.!?,;:]`.
4. Match is correct if normalized recognized text equals or contains any valid answer.
5. On correct: sets `voiceResult = CORRECT`, `voiceCompleted = true`, auto-flips after 800ms.
6. On wrong: increments `voiceAttempts`. If attempts >= 3, sets `voiceCompleted = true` with `WRONG`. Otherwise allows retry.
7. Skip: sets `voiceResult = SKIPPED`, `voiceCompleted = true`.

**Normalization method (`normalizeForMatch`):**
```kotlin
fun normalizeForMatch(text: String): String {
    return text.trim().lowercase().replace(Regex("[.!?,;:]$"), "")
}
```
Strips leading/trailing whitespace, converts to lowercase, and removes trailing punctuation characters.

**Language tag for voice recognition:**
- `IT_TO_RU` direction: `ru-RU` (user speaks Russian)
- `RU_TO_IT` direction: `it-IT` (user speaks Italian)

**Auto-voice check (`shouldAutoStartVoice()`):**
Returns `true` when `voiceModeEnabled && !isFlipped && !voiceCompleted`. Used by the UI to decide whether to auto-launch RecognizerIntent on a new card.

### 11.5.10 Session Completion

- When `currentIndex >= cards.size`, `isComplete = true`.
- Completion screen shows: correct count, incorrect count, total words reviewed.
- "Perfect!" displayed if all correct, "Done!" otherwise.
- Options: "Exit" (returns to selection screen) or "Continue" (starts a new session with the next batch of due words).

### 11.5.11 TTS Support

- `speakTts(text, speed = 0.67f)` — pronounces the Italian word via Sherpa-ONNX TTS.
- TTS button is shown on both card front (IT_TO_RU direction only) and card back.
- Auto-initializes TTS engine on first use with the active language ID.

---

## 11.6 VocabDrillScreen (UI)

Source file: `ui/VocabDrillScreen.kt`

### 11.6.1 Screen Structure

The screen has three states, each rendered by a separate composable:

| State                | Composable                    | Condition                 |
|----------------------|-------------------------------|---------------------------|
| Loading              | `CircularProgressIndicator`   | `state.isLoading == true` |
| Selection (no session)| `VocabDrillSelectionScreen`   | `session == null`         |
| Card practice        | `VocabDrillCardScreen`        | `session != null && !isComplete` |
| Completion           | `VocabDrillCompletionScreen`  | `session.isComplete == true` |

### 11.6.2 Selection Screen (VocabDrillSelectionScreen)

Layout (top to bottom):
1. **Header:** Back arrow + "Flashcards" title.
2. **Direction filter chips:** "IT -> RU" and "RU -> IT". Default: IT -> RU.
3. **Voice input toggle:** Mic icon + "Voice input (auto)" label + Switch.
4. **Part of speech filter chips:** "All" + one chip per available POS. Labels: Nouns, Verbs, Adj., Adv., Numbers (with capitalize-first-char fallback for unknown POS like "pronouns").
5. **Word frequency filter chips:** "Top 100", "Top 500", "Top 1000", "All".
6. **Stats card** (shown when `totalCount > 0`):
   - "Due: {dueCount} / {totalCount}" heading.
   - "Mastered: {masteredCount} words" in green (if any mastered).
   - Per-POS mastered breakdown (e.g. "Nouns: 15 | Verbs: 8").
   - Progress bar: `(totalCount - dueCount) / totalCount`.
7. **Start button:** "Start ({dueCount} due)" or disabled "No due words".

### 11.6.3 Card Screen (VocabDrillCardScreen)

Layout:
1. **Header bar:** Back arrow + "Flashcards" title + card counter "{current}/{total}".
2. **Progress bar:** Linear, showing `currentIndex / totalCards`.
3. **Card** (front or back, depending on `isFlipped`).
4. **Bottom action buttons** (below the card):
   - **Not flipped:** Skip button + Flip button.
   - **Flipped:** 4 Anki-style rating buttons (Again, Hard, Good, Easy) with interval previews.

### 11.6.4 Card Front (VocabDrillCardFront)

- POS badge (colored chip): nouns=`primaryContainer` (label "noun"), verbs=`secondaryContainer` (label "verb"), adjectives=`tertiaryContainer` (label "adj."), adverbs=`errorContainer` (label "adv."), numbers=`surfaceVariant` (label "num."), pronouns=`surfaceVariant` (label "pronouns" with capitalize-first-char fallback).
- Rank badge ("#106" style).
- **Main word** (32sp, bold):
  - IT_TO_RU: Italian word (e.g. "casa").
  - RU_TO_IT: Russian meaning (e.g. "дом/жилище/семья").
- **TTS button** (IT_TO_RU only): VolumeUp icon with states (SPEAKING, INITIALIZING, ERROR, IDLE).
- **Voice input area:**
  - If voice not completed: "Tap to speak" label + large 72dp mic button.
  - If voice completed: `VoiceResultFeedback` card showing result.

**Card background color** changes based on voice result:
- Correct: light green tint (`#E8F5E9` at 0.7 alpha).
- Wrong: light red tint (`#FFEBEE` at 0.7 alpha).
- Default: surface variant at 0.7 alpha.

### 11.6.5 Card Back (VocabDrillCardBack)

Background: secondary container at 0.5 alpha.

**IT_TO_RU direction:**
1. POS badge + Italian word (20sp, semibold) + TTS button.
2. Russian translation (18sp, medium, primary color).

**RU_TO_IT direction:**
1. POS badge + Russian meaning (16sp, medium, 0.7 alpha).
2. Italian word (24sp, bold, primary color) + TTS button.

**Both directions:**
3. **Forms card** (if `forms` is non-empty): "Forms" label inside a tertiary-container-tinted card. The grid columns vary by POS:
   - **Adjectives:** 4-column grid: m sg (`forms["msg"]`), f sg (`forms["fsg"]`), m pl (`forms["mpl"]`), f pl (`forms["fpl"]`).
   - **Numbers:** 2-column grid: m (`forms["form_m"]`), f (`forms["form_f"]`).
   - **Pronouns:** 4-column grid: sg m (`forms["form_sg_m"]`), sg f (`forms["form_sg_f"]`), pl m (`forms["form_pl_m"]`), pl f (`forms["form_pl_f"]`).
   - Each form item shows a label and the value (or "-" if null).
4. **Collocations** (if non-empty): "Collocations" label + up to 5 phrases + "+N more" overflow.
5. **Mastery indicator:** "Step {step+1}/9" or "Learned" (when step >= LEARNED_THRESHOLD = 3). Displayed in label-small text at 0.5 alpha.

### 11.6.6 Voice Result Feedback (VoiceResultFeedback)

Shown in the card front area after voice input completes:
- Displayed in a rounded card.
- Shows recognized text in italics with quotes.
- **CORRECT:** Green check icon + "Correct!" in green.
- **WRONG:** Red X icon + "Try again ({attempts}/3)" or "Moving on..." if completed.
- **SKIPPED:** Gray "Skipped" text.

### 11.6.7 Answer Rating Buttons

Shown on the card back after flipping. Four buttons in a 2x2 grid:

| Button | Color     | Style          | Content                           |
|--------|-----------|----------------|-----------------------------------|
| Again  | Error     | Outlined       | "Again" + "<1m"                   |
| Hard   | Orange    | Outlined       | "Hard" + "{current interval}d"    |
| Good   | Primary   | Filled Button  | "Good" + "{next interval}d"       |
| Easy   | Green     | Filled Button  | "Easy" + "{+2 interval}d"         |

### 11.6.8 Completion Screen (VocabDrillCompletionScreen)

- Animated reveal: stats appear after 800ms delay.
- Title: "Perfect!" (all correct) or "Done!".
- Stats card showing correct count (primary color) and wrong count (error color) side by side.
- "{N} words reviewed" subtitle.
- Two buttons: "Exit" (outlined) and "Continue" (filled).

---

## 11.7 Integration with Daily Practice

### 11.7.1 Daily Practice Block 2 Overview

The `DailySessionComposer` builds block 2 of the daily practice session as vocab flashcards. This block produces `DailyTask.VocabFlashcard` tasks, which are rendered in `DailyPracticeScreen.kt` alongside translation and verb drill blocks.

**Block size:** 10 cards (`VOCAB_COUNT = 10`), though the daily practice UI may show only 5 depending on session configuration.

### 11.7.2 Card Selection Algorithm (Daily Practice)

The daily practice vocab selection is a **pure SRS-based** algorithm, independent of lesson level. It does not use the rank range filter from the standalone drill.

**Algorithm (`buildVocabBlock()`):**

1. Load all vocab words from the pack via `loadVocabWords()`.
2. Load all mastery states from `WordMasteryStore` (pack-scoped).
3. Categorize words into three buckets:
   - **Due words:** `nextReviewDateMs <= now` (past their scheduled review). Paired with `overdueMs = now - nextReviewDateMs`.
   - **New words:** No mastery record exists (never reviewed).
   - **Scheduled words:** `nextReviewDateMs > now` (not yet due). Paired with `lastReviewDateMs` for fallback.
4. Sort due words by **most overdue first** (descending `overdueMs`).
5. Select cards in priority order:
   - Take due words, most overdue first (up to 10).
   - If fewer than 10, fill with new words sorted by rank ascending.
   - If still fewer than 10, fill with least-recently-reviewed scheduled words.
6. Assign alternating directions: even index = `IT_TO_RU`, odd index = `RU_TO_IT`.
7. Return `DailyTask.VocabFlashcard` list.

### 11.7.3 Repeat Session Behavior

When the user selects "Repeat" in daily practice:
- Block 1 (translation): repeats the exact same card IDs.
- **Block 2 (vocab): always rebuilds from SRS.** The repeat session calls `buildVocabBlock()` again independently, which may return different cards if SRS state has changed. Vocab flashcards are not repeated from a stored ID list.
- Block 3 (verb drill): repeats the exact same card IDs.

### 11.7.4 Shared vs Separate State

| Aspect                    | Standalone Drill              | Daily Practice Block 2            |
|---------------------------|-------------------------------|-----------------------------------|
| ViewModel                 | `VocabDrillViewModel`         | `TrainingViewModel` + helpers     |
| Mastery store             | `WordMasteryStore` (pack-scoped) | `WordMasteryStore` (pack-scoped) |
| Persistence file          | Same `word_mastery.yaml`      | Same `word_mastery.yaml`          |
| Word loading              | Same `ItalianDrillVocabParser`| Same parser                       |
| Card selection            | Due-by-rank, user filters     | SRS-priority (overdue > new > fallback) |
| Direction assignment      | User-chosen                   | Alternating even/odd              |
| Answer rating             | 4-button Anki (Again/Hard/Good/Easy) | Simple correct/incorrect     |
| Voice input               | Optional, with mic button     | Follows daily practice voice mode |
| Session size              | Up to 10 cards                | 10 cards (may be truncated to 5)  |

**Key point:** Both contexts share the same `WordMasteryStore` persistence file, so progress made in standalone drill affects daily practice card selection and vice versa.

### 11.7.5 Progress Across Both Contexts

Since both the standalone drill and daily practice write to the same `word_mastery.yaml` file via `WordMasteryStore.upsertMastery()`:

- A word answered "Good" in standalone drill will have its `intervalStepIndex` incremented and `nextReviewDateMs` advanced, making it less likely to appear in the next daily practice session.
- A word marked "Again" in either context resets to step 0 and becomes due the next day.
- The `isLearned` flag is shared: a word marked learned in one context appears as learned in the other.

---

## 11.8 CSV Format Specifications

All vocab drill CSV files are located in the pack's vocab drill directory after import. Default files are under `assets/grammarmate/vocab/it/`.

### 11.8.1 Universal Parsing Rules

- **Encoding:** UTF-8.
- **Header row:** Required. The parser auto-detects format from header column names.
- **Quote handling:** Double-quoted fields are supported (comma inside quotes is treated as part of the field value).
- **Blank lines:** Skipped.
- **Collocation separator:** Semicolon (`;`) within the collocations column.
- **Russian meaning:** May contain forward slash (`/`) to separate synonyms (e.g. "быть/являться").
- **Russian column detection:** The parser looks for columns named `ru`, `meaning_ru`, or `russian`.

### 11.8.2 Verbs CSV (`drill_verbs.csv`)

**Header:** `rank,verb,collocations,ru`

| Column        | Type   | Required | Description                                          |
|---------------|--------|----------|------------------------------------------------------|
| `rank`        | Int    | Yes      | Frequency rank                                       |
| `verb`        | String | Yes      | Italian infinitive form (e.g. "essere", "avere")     |
| `collocations`| String | No       | Semicolon-separated Italian phrases                  |
| `ru`          | String | No       | Russian translation, `/`-separated synonyms          |

**Example:**
```csv
rank,verb,collocations,ru
1,essere,eri consapevole;sarai capace;sono stanco,быть/являться/существовать
15,avere,avere caldo;avere fame;avere fretta,иметь/обладать
79,volere,,хотеть/желать
```

### 11.8.3 Nouns CSV (`drill_nouns.csv`)

**Header:** `rank,noun,collocations,ru`

| Column        | Type   | Required | Description                                          |
|---------------|--------|----------|------------------------------------------------------|
| `rank`        | Int    | Yes      | Frequency rank                                       |
| `noun`        | String | Yes      | Italian noun (e.g. "casa", "tempo")                  |
| `collocations`| String | No       | Semicolon-separated Italian phrases                  |
| `ru`          | String | No       | Russian translation, `/`-separated synonyms          |

**Example:**
```csv
rank,noun,collocations,ru
106,casa,bella casa;casa di famiglia;casa di moda,дом/жилище/семья
132,tempo,passare il tempo;perdere tempo;tempo libero,время/погода
```

### 11.8.4 Adjectives CSV (`drill_adjectives.csv`)

**Header:** `rank,adjective,msg,fsg,mpl,fpl,collocations,ru`

| Column        | Type   | Required | Description                              |
|---------------|--------|----------|------------------------------------------|
| `rank`        | Int    | Yes      | Frequency rank                           |
| `adjective`   | String | Yes      | Italian adjective lemma                  |
| `msg`         | String | No       | Masculine singular form                  |
| `fsg`         | String | No       | Feminine singular form                   |
| `mpl`         | String | No       | Masculine plural form                    |
| `fpl`         | String | No       | Feminine plural form                     |
| `collocations`| String | No       | Semicolon-separated Italian phrases      |
| `ru`          | String | No       | Russian translation, `/`-separated       |

**Example:**
```csv
rank,adjective,msg,fsg,mpl,fpl,collocations,ru
776,solito,solito,solita,soliti,solite,,обычный/привычный
874,pazzo,pazzo,pazza,pazzi,pazze,,сумасшедший/безумный
1566,giovane,giovane,giovane,giovani,giovani,,молодой
```

Forms are stored in the `forms` map with keys: `msg`, `fsg`, `mpl`, `fpl`.

### 11.8.5 Adverbs CSV (`drill_adverbs.csv`)

**Header:** `rank,adverb,comparative,superlative,ru,collocations`

| Column        | Type   | Required | Description                              |
|---------------|--------|----------|------------------------------------------|
| `rank`        | Int    | Yes      | Frequency rank                           |
| `adverb`      | String | Yes      | Italian adverb                           |
| `comparative` | String | No       | Comparative form (e.g. "meglio")         |
| `superlative` | String | No       | Superlative form (e.g. "ottimamente")    |
| `ru`          | String | No       | Russian translation, `/`-separated       |
| `collocations`| String | No       | Semicolon-separated Italian phrases      |

**Example:**
```csv
rank,adverb,comparative,superlative,ru,collocations
2,non,,,не,
34,bene,meglio,ottimamente,хорошо,
55,ora,,,сейчас / час,
```

**Note:** The parser currently reads collocations from column index 5 (the last column). Comparative and superlative forms are parsed but not currently stored on `ItalianDrillRow` or `VocabWord`.

### 11.8.6 Numbers CSV (`drill_numbers.csv`)

**Header:** `category,italian,ru,form_m,form_f,notes`

| Column     | Type   | Required | Description                                |
|------------|--------|----------|--------------------------------------------|
| `category` | String | Yes      | Number category (e.g. "cardinal")          |
| `italian`  | String | Yes      | Italian word for the number                |
| `ru`       | String | No       | Russian translation                        |
| `form_m`   | String | No       | Masculine form (e.g. "uno")                |
| `form_f`   | String | No       | Feminine form (e.g. "una")                 |
| `notes`    | String | No       | Usage notes; parsed as collocations if present |

**No rank column.** Synthetic rank is assigned based on row order (1, 2, 3, ...).

**Example:**
```csv
category,italian,ru,form_m,form_f,notes
cardinal,zero,ноль,,,invariable
cardinal,uno,один,uno,una,changes gender; before consonant uno/una
cardinal,due,два,,,invariable
```

Forms are stored with keys `form_m` and `form_f`. Notes are treated as collocations (semicolon-split).

### 11.8.7 Pronouns CSV (`drill_pronouns.csv`)

**Header:** `type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes,ru`

| Column      | Type   | Required | Description                                |
|-------------|--------|----------|--------------------------------------------|
| `type`      | String | Yes      | Pronoun type (e.g. "personale")            |
| `category`  | String | Yes      | Subcategory (e.g. "soggetto", "oggetto_diretto") |
| `person`    | String | Yes      | Person code (e.g. "1s", "2s", "3sm")       |
| `form_sg_m` | String | Yes      | Singular masculine form (used as main word) |
| `form_sg_f` | String | No       | Singular feminine form                      |
| `form_pl_m` | String | No       | Plural masculine form                       |
| `form_pl_f` | String | No       | Plural feminine form                        |
| `notes`     | String | No       | Usage notes; parsed as collocations         |
| `ru`        | String | No       | Russian translation                         |

**No rank column.** Synthetic rank is assigned based on row order.

**Example:**
```csv
type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes,ru
personale,soggetto,1s,io,io,io,io,first person singular subject,я
personale,soggetto,3sf,lei,lei,lei,lei,"feminine singular third person subject; formal 'you' singular",она/Вы
```

The main `word` for a pronoun row is `form_sg_m` (column index 3). Forms are stored with keys `form_sg_m`, `form_sg_f`, `form_pl_m`, `form_pl_f`.

### 11.8.8 Filename-to-POS Derivation

The POS key is derived from the CSV filename by stripping prefixes and suffixes:

```
drill_nouns.csv      -> "nouns"
drill_verbs.csv      -> "verbs"
drill_adjectives.csv -> "adjectives"
drill_adverbs.csv    -> "adverbs"
drill_numbers.csv    -> "numbers"
drill_pronouns.csv   -> "pronouns"
it_drill_nouns.csv   -> "nouns"  (language prefix also stripped)
```

---

## 11.9 User Stories

### US-11.1: Start a Flashcard Session
**As a** language learner,
**I want to** open the vocab drill screen and start a flashcard session,
**So that** I can practice vocabulary words that are due for review.

**Acceptance criteria:**
- The screen shows available POS categories as filter chips.
- It shows the number of due words out of total words for the current filters.
- The "Start" button shows the due count and is disabled when no words are due.
- Tapping "Start" begins a session with up to 10 due words, sorted by frequency rank.

### US-11.2: Filter Words by Part of Speech
**As a** learner who wants to focus,
**I want to** filter flashcards to a specific part of speech,
**So that** I can practice only nouns, only verbs, etc.

**Acceptance criteria:**
- Filter chips show "All" plus one chip per available POS.
- Selecting a chip immediately updates the due count and total count.
- Only one POS can be selected at a time (mutually exclusive).
- Selecting "All" shows words from all categories.

### US-11.3: Filter Words by Frequency Rank
**As a** learner at a specific level,
**I want to** limit flashcards to the most frequent words,
**So that** I focus on the most useful vocabulary first.

**Acceptance criteria:**
- Rank range filter chips: "Top 100", "Top 500", "Top 1000", "All".
- Selecting a range immediately updates the due count.
- Only one range can be active at a time.

### US-11.4: Flip a Flashcard
**As a** learner,
**I want to** tap to flip a flashcard and see the answer,
**So that** I can check if I know the word before rating myself.

**Acceptance criteria:**
- Card front shows the prompt (Italian word or Russian meaning depending on direction).
- Tapping "Flip" reveals the card back with the answer.
- The flip toggles the card state between front and back.

### US-11.5: Rate My Recall (Anki-style)
**As a** learner using spaced repetition,
**I want to** rate my recall as Again, Hard, Good, or Easy after seeing the answer,
**So that** the system adjusts the review interval appropriately.

**Acceptance criteria:**
- Four buttons appear after flipping: Again, Hard, Good, Easy.
- Each button shows the resulting review interval (e.g. "<1m", "4d", "7d").
- "Again" resets the word to step 0 (due <1m).
- "Hard" keeps the current step.
- "Good" advances one step on the interval ladder.
- "Easy" advances two steps.
- The word's mastery is persisted immediately after rating.

### US-11.6: Practice in Both Directions
**As a** learner building bidirectional recall,
**I want to** choose between IT->RU and RU->IT directions,
**So that** I can practice both recognizing Italian words and producing them from Russian.

**Acceptance criteria:**
- Direction filter chips are shown on the selection screen.
- IT->RU shows Italian word on front, Russian meaning on back.
- RU->IT shows Russian meaning on front, Italian word on back.
- Direction can be changed before starting a session.
- In daily practice, directions alternate (even index = IT->RU, odd = RU->IT).

### US-11.7: Use Voice Input
**As a** learner practicing pronunciation,
**I want to** speak my answer using voice recognition,
**So that** I can practice without typing.

**Acceptance criteria:**
- Voice input toggle on the selection screen enables auto-launch of voice recognition.
- When enabled, the mic launches automatically on each new card (after 500ms delay).
- The mic button is also available for manual tap on the card front.
- Correct pronunciation auto-flips the card after 800ms.
- Wrong attempts show "Try again (N/3)" and allow up to 3 attempts.
- After 3 wrong attempts or tapping Skip, the card auto-flips.
- Language tag for voice recognition matches the expected answer language (ru-RU or it-IT).

### US-11.8: Hear Word Pronunciation (TTS)
**As a** learner,
**I want to** tap a button to hear the Italian word pronounced,
**So that** I can learn the correct pronunciation.

**Acceptance criteria:**
- TTS button (volume icon) appears on the card front in IT->RU direction.
- TTS button appears on the card back for both directions.
- TTS plays the Italian word at 0.67x speed for clarity.
- TTS states: IDLE, INITIALIZING (spinner), SPEAKING (highlighted), ERROR (red X).

### US-11.9: See Word Forms
**As a** learner studying adjectives,
**I want to** see all gender/number forms of a word on the card back,
**So that** I learn masculine, feminine, singular, and plural forms.

**Acceptance criteria:**
- For words with forms (adjectives, numbers, pronouns), a "Forms" card is displayed on the back.
- Shows a 4-column grid: m sg, f sg, m pl, f pl (or relevant subset).
- Words without forms do not show the forms card.

### US-11.10: See Collocations
**As a** learner building contextual knowledge,
**I want to** see common phrases using the word on the card back,
**So that** I understand how the word is used in context.

**Acceptance criteria:**
- Up to 5 collocations are shown on the card back.
- If more than 5 exist, "+N more" text is displayed.
- Words without collocations do not show the section.

### US-11.11: View Session Results
**As a** learner,
**I want to** see a summary after completing a flashcard session,
**So that** I know how well I performed.

**Acceptance criteria:**
- Completion screen shows correct count (blue) and wrong count (red).
- "Perfect!" is shown when all answers are correct.
- "Done!" is shown otherwise.
- Total words reviewed is displayed.
- Stats animate in after 800ms delay.
- "Exit" returns to the selection screen.
- "Continue" starts a new session with the next batch of due words.

### US-11.12: Track Mastery Progress
**As a** learner,
**I want to** see how many words I have mastered and my overall progress,
**So that** I stay motivated and understand my learning trajectory.

**Acceptance criteria:**
- Selection screen shows "Mastered: N words" in green when any words are mastered.
- Per-POS breakdown is shown (e.g. "Nouns: 15 | Verbs: 8").
- Progress bar shows the ratio of learned words to total words.
- "Step N/9" or "Learned" is shown on each card back.
- Words at step 3+ are considered learned.

### US-11.13: Practice Vocab in Daily Practice
**As a** daily practice user,
**I want to** have vocab flashcards appear as block 2 of my daily practice,
**So that** vocab review is integrated into my daily routine.

**Acceptance criteria:**
- Daily practice automatically includes a vocab block with up to 10 flashcards.
- Card selection is SRS-based: most overdue first, then new words, then least-recently-reviewed.
- Directions alternate between IT->RU and RU->IT.
- Progress made in daily practice updates the same mastery store as standalone drill.
- When repeating a daily session, vocab cards are re-selected from SRS (not repeated from stored IDs).

### US-11.14: Pack-Scoped Vocab Data
**As a** user with multiple lesson packs,
**I want to** practice vocab from the currently active pack only,
**So that** I focus on relevant vocabulary for my current course.

**Acceptance criteria:**
- Vocab data is scoped to the active pack ID.
- Mastery data is stored per-pack in `drills/{packId}/word_mastery.yaml`.
- Switching packs reloads words and mastery from the new pack.
- The HomeScreen shows the vocab drill tile only when the active pack declares a `vocabDrill` section in its manifest.

### US-11.15: Skip a Card Without Rating
**As a** learner,
**I want to** skip a card without providing a voice answer,
**So that** I can move on if I am unsure or do not want to use voice input.

**Acceptance criteria:**
- "Skip" button is visible on the card front (before flipping).
- If voice is not yet completed, Skip marks voice as SKIPPED and auto-flips the card.
- If voice is already completed, Skip triggers a flip.
- The user can still rate the card after skipping voice input.

### US-11.16: Resume from Saved State
**As a** user who left the app mid-session,
**I want to** find my vocab drill progress preserved,
**So that** I do not lose my spaced repetition history.

**Acceptance criteria:**
- Word mastery state is persisted to YAML after every answer.
- When returning to the drill screen, due counts reflect previously saved mastery.
- The session itself is not restored (user starts a new session), but all per-word progress is intact.

---

## 11.10 Bad Card Reporting

### 11.10.1 Overview

Vocab drill supports full bad card flagging, unflagging, and export. Flagged cards are persisted in `BadSentenceStore` pack-scoped, with `mode = "vocab_drill"`. The report button is available on the card screen during a session.

### 11.10.2 Methods

`VocabDrillViewModel` provides the following bad sentence methods:

| Method | Description |
|--------|-------------|
| `flagBadSentence()` | Adds current vocab card to `BadSentenceStore`. Uses `activePackId` if available, otherwise falls back to `"__vocab_drill__"`. Sets `mode = "vocab_drill"`, `sentence = word.meaningRu ?: word.word`, `translation = word.word`. |
| `unflagBadSentence()` | Removes current vocab card from `BadSentenceStore`. |
| `isBadSentence()` | Checks if the current vocab card is flagged. |
| `exportBadSentences()` | Exports bad sentences for the active pack via `BadSentenceStore.exportToTextFile(packId)`. Returns file path or `null` if no bad sentences exist. |

### 11.10.3 Mode Field

The `mode` field in `BadSentenceEntry` is set to `"vocab_drill"` for all vocab drill flagged cards. This distinguishes vocab drill reports from training (`"training"`), verb drill (`"verb_drill"`), and daily practice reports.

### 11.10.4 Pack Scoping

When `activePackId` is set (via `reloadForPack()`), bad sentences are scoped to that pack. When no pack is active, the fallback pack ID `"__vocab_drill__"` is used. This ensures flagged cards are correctly grouped during export.

### 11.10.5 Report Button Location

The report button is available on the vocab drill card screen header row (right side), rendered as a `ReportProblem` icon. When the current card is already flagged, the icon tint changes to `colorScheme.error`. Tapping it opens a `ModalBottomSheet` with:

- Card word and meaning displayed at the top (e.g. "essere -- быть/являться").
- **"Add to bad sentences list"** (or "Remove from bad sentences list" if already flagged) with `ReportProblem` icon.
- **"Export bad sentences to file"** with `Download` icon -- exports via `BadSentenceStore.exportToTextFile(packId)`, shows result in an `AlertDialog`.
- **"Copy text"** with `ContentCopy` icon -- copies a formatted string to clipboard:
  ```
  ID: {card.word.id}
  Word: {card.word.word}
  Meaning: {card.word.meaningRu}
  ```
