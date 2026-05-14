# 1. Models & State -- Specification

This document exhaustively catalogs every data class, enum, sealed class, interface, and object in the data-model layer of GrammarMate. It is intended as a reference for refactoring: all fields, types, defaults, validation rules, relationships, state transitions, invariants, and business rules are captured here.

Sources:
- `app/src/main/java/com/alexpo/grammermate/data/Models.kt`
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillCard.kt`
- `app/src/main/java/com/alexpo/grammermate/data/VocabWord.kt`
- `app/src/main/java/com/alexpo/grammermate/data/LessonPackManifest.kt`
- `app/src/main/java/com/alexpo/grammermate/data/CardSessionContract.kt`
- `app/src/main/java/com/alexpo/grammermate/data/TrainingConfig.kt`
- `app/src/main/java/com/alexpo/grammermate/data/SpacedRepetitionConfig.kt`
- `app/src/main/java/com/alexpo/grammermate/data/FlowerCalculator.kt`
- `app/src/main/java/com/alexpo/grammermate/data/MixedReviewScheduler.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` (for `TrainingUiState`, `LessonLadderRow`)
- `app/src/main/java/com/alexpo/grammermate/data/TtsEngine.kt` (for `TtsState`)
- `app/src/main/java/com/alexpo/grammermate/data/AsrEngine.kt` (for `AsrState`)
- `app/src/main/java/com/alexpo/grammermate/data/TtsModelManager.kt` (for `DownloadState`)
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` (for `AppScreen`, `LessonTileState`)
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionHelper.kt` (for `TrainingStateAccess`)

---

## 1.1 Data Classes Overview

### 1.1.1 `Language`

**File:** `Models.kt`
**Purpose:** Represents a target language available in the app (e.g., English, Italian).

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `id` | `String` | -- | Unique identifier (e.g., `"en"`, `"it"`). Must not be blank. |
| `displayName` | `String` | -- | Human-readable name (e.g., "English"). Must not be blank. |

**Relationships:** Referenced by `Lesson.languageId`, `LessonPack.languageId`, `VocabEntry.languageId`, `TrainingProgress.languageId`, `StreakData.languageId`, `LessonMasteryState.languageId`, `TrainingUiState.selectedLanguageId`.

---

### 1.1.2 `Lesson`

**File:** `Models.kt`
**Purpose:** A lesson within a language pack. Contains ordered sentence cards for translation training.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `id` | `String` | -- | Unique lesson identifier. |
| `languageId` | `String` | -- | Links to `Language.id`. |
| `title` | `String` | -- | Display title. |
| `cards` | `List<SentenceCard>` | -- | Ordered list of all cards. |
| `drillCards` | `List<SentenceCard>` | `emptyList()` | Additional drill-only cards. |

**Computed properties:**

| Property | Type | Computation |
|----------|------|-------------|
| `mainPoolCards` | `List<SentenceCard>` | `cards.take(MAIN_POOL_SIZE)` -- first 150 cards |
| `reservePoolCards` | `List<SentenceCard>` | `cards.drop(MAIN_POOL_SIZE)` -- cards beyond index 150 |
| `allCards` | `List<SentenceCard>` | `cards` -- alias for the full list |

**Constants:**

| Constant | Value | Source |
|----------|-------|--------|
| `MAIN_POOL_SIZE` | 150 | `SpacedRepetitionConfig.MASTERY_THRESHOLD` |

**Invariants:**
- `cards` is ordered by difficulty/frequency and never reordered at runtime.
- `mainPoolCards.size <= 150`. If the lesson has fewer than 150 cards, mainPool == allCards.
- Reserve pool cards are used only in MIXED and review sessions to prevent memorization of specific phrases.
- Mastery is measured against the main pool (first 150 cards only).

---

### 1.1.3 `SentenceCard`

**File:** `Models.kt`
**Purpose:** A single translation exercise card. The user sees `promptRu` and must produce one of `acceptedAnswers`.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `id` | `String` | -- | Unique card identifier. Implements `SessionCard.id`. |
| `promptRu` | `String` | -- | Russian prompt text. Implements `SessionCard.promptRu`. |
| `acceptedAnswers` | `List<String>` | -- | List of valid target-language answers. Implements `SessionCard.acceptedAnswers`. |
| `tense` | `String?` | `null` | Optional grammatical tense tag. |

**Relationships:** Implements `SessionCard`. Contained in `Lesson.cards`. Referenced in `DailyTask.TranslateSentence`.

---

### 1.1.4 `VocabEntry`

**File:** `Models.kt`
**Purpose:** A vocabulary entry for the legacy vocab sprint mode (not the Anki-like vocab drill -- see `VocabWord` for that).

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `id` | `String` | -- | Unique entry identifier. |
| `lessonId` | `String` | -- | Links to `Lesson.id`. |
| `languageId` | `String` | -- | Links to `Language.id`. |
| `nativeText` | `String` | -- | Russian text. |
| `targetText` | `String` | -- | Target language text. |
| `isHard` | `Boolean` | `false` | Whether the user marked this word as difficult. |

**Note:** This is distinct from `VocabWord`. `VocabEntry` is the legacy per-lesson vocab model; `VocabWord` is the newer pack-scoped Anki-style drill model.

---

### 1.1.5 `LessonPack`

**File:** `Models.kt`
**Purpose:** Represents an imported lesson pack (ZIP archive). Stored in the app's data directory.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `packId` | `String` | -- | Unique pack identifier. Matches `LessonPackManifest.packId`. |
| `packVersion` | `String` | -- | Version string from manifest. |
| `languageId` | `String` | -- | Target language ID. |
| `importedAt` | `Long` | -- | Epoch millis of import timestamp. |
| `displayName` | `String?` | `null` | Optional display name override from manifest. |

**Relationships:** Referenced by `TrainingUiState.installedPacks`, `TrainingUiState.activePackId`.

---

### 1.1.6 `StoryQuestion`

**File:** `Models.kt`
**Purpose:** A single multiple-choice question within a story quiz.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `qId` | `String` | -- | Question identifier. |
| `prompt` | `String` | -- | Question text. |
| `options` | `List<String>` | -- | Answer options. |
| `correctIndex` | `Int` | -- | 0-based index of the correct option in `options`. Must be in `[0, options.size)`. |
| `explain` | `String?` | `null` | Optional explanation shown after answering. |

---

### 1.1.7 `StoryQuiz`

**File:** `Models.kt`
**Purpose:** A story quiz phase (check-in or check-out) for a lesson.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `storyId` | `String` | -- | Story identifier. |
| `lessonId` | `String` | -- | Links to `Lesson.id`. |
| `phase` | `StoryPhase` | -- | `CHECK_IN` (before lesson) or `CHECK_OUT` (after lesson). |
| `text` | `String` | -- | Story narrative text. |
| `questions` | `List<StoryQuestion>` | -- | Ordered list of questions. |

---

### 1.1.8 `TrainingProgress`

**File:** `Models.kt`
**Purpose:** Persisted training session state. Saved/restored via `ProgressStore`. This is the serialized form that survives app restarts.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `languageId` | `String` | `"en"` | Current target language. |
| `mode` | `TrainingMode` | `TrainingMode.LESSON` | Current training mode. |
| `lessonId` | `String?` | `null` | Active lesson ID (null in ALL_SEQUENTIAL/ALL_MIXED modes). |
| `currentIndex` | `Int` | `0` | Index within the current sub-lesson's card list. `>= 0`. |
| `correctCount` | `Int` | `0` | Correct answers in current session. `>= 0`. |
| `incorrectCount` | `Int` | `0` | Incorrect answers in current session. `>= 0`. |
| `incorrectAttemptsForCard` | `Int` | `0` | Consecutive incorrect attempts for the current card. `>= 0`. |
| `activeTimeMs` | `Long` | `0L` | Active training time in milliseconds. `>= 0`. |
| `state` | `SessionState` | `SessionState.PAUSED` | Current session state. |
| `bossLessonRewards` | `Map<String, String>` | `emptyMap()` | Lesson boss reward IDs to reward tier strings. |
| `bossMegaReward` | `String?` | `null` | Legacy mega boss reward. |
| `bossMegaRewards` | `Map<String, String>` | `emptyMap()` | Per-language mega boss rewards. |
| `voiceActiveMs` | `Long` | `0L` | Time spent in voice input mode. |
| `voiceWordCount` | `Int` | `0` | Words recognized via voice. |
| `hintCount` | `Int` | `0` | Hints used in current session. See hint definition below. |
| `eliteStepIndex` | `Int` | `0` | Current step in elite (daily practice) mode. |
| `eliteBestSpeeds` | `List<Double>` | `emptyList()` | Best typing speeds per elite step. |
| `currentScreen` | `String` | `"HOME"` | Screen name for state restoration. |
| `activePackId` | `String?` | `null` | Currently active lesson pack ID. |
| `dailyLevel` | `Int` | `0` | Daily practice difficulty level. |
| `dailyTaskIndex` | `Int` | `0` | Index within daily practice task list. |
| `dailyCursor` | `DailyCursorState` | `DailyCursorState()` | Daily practice cursor state. |

**Note on `bossLessonRewards` / `bossMegaRewards`:** These store `String` representations of `BossReward` enum values. The `TrainingUiState` versions use typed `Map<String, BossReward>`. The `TrainingProgress` version is the serialized form.

**Definition — "Hints":** In this app, "hints" refer specifically to parenthetical target-language insertions in the Russian prompt text (e.g., `(dire)` in `я говорю (dire) правду`). These are NOT the same as UI features like Word Bank, tense labels, or the "Show Answer" eye button. The `hintCount` field tracks usage of these parenthetical hints. The `HintLevel` enum controls whether `promptRu` is displayed with parenthetical content included (EASY) or stripped (MEDIUM/HARD).

---

### 1.1.9 `LessonMasteryState`

**File:** `Models.kt`
**Purpose:** Per-lesson mastery tracking data. Used to calculate flower visual state. Stored via `MasteryStore`.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `lessonId` | `String` | -- | Links to `Lesson.id`. |
| `languageId` | `String` | -- | Links to `Language.id`. |
| `uniqueCardShows` | `Int` | `0` | Number of unique cards from the main pool that have been practiced with VOICE or KEYBOARD. `>= 0`, max 150. |
| `totalCardShows` | `Int` | `0` | Total card show count (including repeats). `>= 0`. |
| `lastShowDateMs` | `Long` | `0L` | Epoch millis of the last card practice. `0` means never practiced. |
| `intervalStepIndex` | `Int` | `0` | Current position in `INTERVAL_LADDER_DAYS` (0-9). |
| `completedAtMs` | `Long?` | `null` | Epoch millis when mastery reached 100% (150 unique cards). `null` if not completed. |
| `shownCardIds` | `Set<String>` | `emptySet()` | IDs of unique cards shown from the main pool. Used to avoid counting duplicates toward mastery. |

**Invariants:**
- `uniqueCardShows == shownCardIds.size` must always hold.
- `uniqueCardShows <= 150` (bounded by `MASTERY_THRESHOLD`).
- `intervalStepIndex` must be in `[0, 9]` (index into `INTERVAL_LADDER_DAYS`).
- Only VOICE and KEYBOARD input modes contribute to `uniqueCardShows` and `shownCardIds`. WORD_BANK does not count.

---

### 1.1.10 `FlowerVisual`

**File:** `Models.kt`
**Purpose:** Computed visual representation of a lesson's flower for UI rendering. This is not persisted; it is derived from `LessonMasteryState` by `FlowerCalculator`.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `state` | `FlowerState` | -- | Visual flower state (see enum). |
| `masteryPercent` | `Float` | -- | `0.0` to `1.0`. Ratio of `uniqueCardShows / 150`. |
| `healthPercent` | `Float` | -- | `0.0` to `1.0`. Ebbinghaus health. Clamped to `[WILTED_THRESHOLD, 1.0]` unless GONE. |
| `scaleMultiplier` | `Float` | -- | `0.5` to `1.0`. Computed as `(masteryPercent * healthPercent).coerceIn(0.5f, 1.0f)`. |

**Computation rules (in `FlowerCalculator`):**
1. If `mastery == null` or `uniqueCardShows == 0`: state = SEED, masteryPercent = 0, healthPercent = 1.0, scale = 0.5.
2. If `daysSinceLastShow > 90`: state = GONE, masteryPercent = 0, healthPercent = 0, scale = 0.5.
3. Otherwise: masteryPercent = `uniqueCardShows / 150`. healthPercent from `SpacedRepetitionConfig.calculateHealthPercent()`. State determined by `determineFlowerState()`.

---

### 1.1.11 `StreakData`

**File:** `Models.kt`
**Purpose:** Per-language daily streak tracking.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `languageId` | `String` | -- | Language ID. |
| `currentStreak` | `Int` | `0` | Consecutive days of practice. `>= 0`. |
| `longestStreak` | `Int` | `0` | All-time longest streak. `>= 0`, `>= currentStreak`. |
| `lastCompletionDateMs` | `Long?` | `null` | Epoch millis of the last day a sub-lesson was completed. `null` if never. |
| `totalSubLessonsCompleted` | `Int` | `0` | Lifetime count of completed sub-lessons. `>= 0`. |

---

### 1.1.12 `DailySessionState`

**File:** `Models.kt`
**Purpose:** Runtime state of an active daily practice session. Part of `TrainingUiState`, not persisted directly.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `active` | `Boolean` | `false` | Whether a daily practice session is currently running. |
| `tasks` | `List<DailyTask>` | `emptyList()` | Ordered list of tasks (translate, vocab, verb cards). |
| `taskIndex` | `Int` | `0` | Current position in `tasks`. `>= 0`, `< tasks.size` when active. |
| `blockIndex` | `Int` | `0` | Current block index (0=translate, 1=vocab, 2=verbs). |
| `level` | `Int` | `0` | Difficulty level for the session. |
| `finishedToken` | `Boolean` | `false` | Set to `true` when the entire session completes. UI observes this to show completion. |

---

### 1.1.13 `DailyCursorState`

**File:** `Models.kt`
**Purpose:** Persisted cursor state for daily practice. Tracks position across sessions so that repeated daily sessions cycle through different content. Part of `TrainingProgress`.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `sentenceOffset` | `Int` | `0` | Number of sentence cards already shown in current lesson. Increments by `SUB_LESSON_SIZE_DEFAULT` (10). |
| `currentLessonIndex` | `Int` | `0` | 0-based index of the current lesson in the pack. Increments when a lesson's cards are exhausted. |
| `lastSessionHash` | `Int` | `0` | Hash of the last completed session. Used as a cache key for the "Repeat" option. |
| `firstSessionDate` | `String` | `""` | ISO date (`yyyy-MM-dd`) of the first session of the current day. Empty if no session today. |
| `firstSessionSentenceCardIds` | `List<String>` | `emptyList()` | Card IDs from the first session's translate block. Used to reproduce cards on "Repeat". |
| `firstSessionVerbCardIds` | `List<String>` | `emptyList()` | Card IDs from the first session's verb block. Used to reproduce cards on "Repeat". |

---

### 1.1.14 `VerbDrillCard`

**File:** `VerbDrillCard.kt`
**Purpose:** A verb conjugation exercise card. The user sees a Russian sentence and must produce the correct conjugated Italian form.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `id` | `String` | -- | Unique card identifier. Implements `SessionCard.id`. |
| `promptRu` | `String` | -- | Russian prompt sentence. Implements `SessionCard.promptRu`. |
| `answer` | `String` | -- | The single correct conjugated form. |
| `verb` | `String?` | `null` | Infinitive form of the verb (e.g., "essere"). |
| `tense` | `String?` | `null` | Grammatical tense (e.g., "Presente", "Passato Prossimo"). |
| `group` | `String?` | `null` | Conjugation group/category. |
| `rank` | `Int?` | `null` | Frequency rank for ordering. Lower = more frequent. |

**Computed properties:**

| Property | Type | Computation |
|----------|------|-------------|
| `acceptedAnswers` | `List<String>` | `listOf(answer)` -- always a single-element list. |

**Relationships:** Implements `SessionCard`. Contained in `VerbDrillSessionState.cards`. Referenced in `DailyTask.ConjugateVerb`.

---

### 1.1.15 `VerbDrillComboProgress`

**File:** `VerbDrillCard.kt`
**Purpose:** Tracks which cards have been shown for a specific verb drill combination (group + tense). Persisted per-pack.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `group` | `String` | -- | Conjugation group. |
| `tense` | `String` | -- | Tense name. |
| `totalCards` | `Int` | -- | Total number of cards available for this combination. |
| `everShownCardIds` | `Set<String>` | `emptySet()` | All card IDs ever shown (cumulative, never resets). |
| `todayShownCardIds` | `Set<String>` | `emptySet()` | Card IDs shown today. Resets each day. |
| `lastDate` | `String` | `""` | ISO date string of the last session for this combination. Used to detect day changes. |

**Persistence path:** `grammarmate/drills/{packId}/verb_drill_progress.yaml`.

---

### 1.1.16 `VerbDrillSessionState`

**File:** `VerbDrillCard.kt`
**Purpose:** Runtime state of an active verb drill session. Not persisted directly.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `cards` | `List<VerbDrillCard>` | -- | Cards for this session. |
| `currentIndex` | `Int` | `0` | Current card index. `>= 0`, `< cards.size` when active. |
| `correctCount` | `Int` | `0` | Correct answers in this session. `>= 0`. |
| `incorrectCount` | `Int` | `0` | Incorrect answers in this session. `>= 0`. |
| `isComplete` | `Boolean` | `false` | `true` when `currentIndex >= cards.size`. |

---

### 1.1.17 `VerbDrillUiState`

**File:** `VerbDrillCard.kt`
**Purpose:** Top-level UI state for the verb drill screen (separate from the main `TrainingUiState`).

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `availableTenses` | `List<String>` | `emptyList()` | Tenses available in the current pack. |
| `availableGroups` | `List<String>` | `emptyList()` | Groups available in the current pack. |
| `selectedTense` | `String?` | `null` | Currently selected tense filter. `null` = all. |
| `selectedGroup` | `String?` | `null` | Currently selected group filter. `null` = all. |
| `totalCards` | `Int` | `0` | Total cards matching current filter. |
| `everShownCount` | `Int` | `0` | Cards ever shown (cumulative). |
| `todayShownCount` | `Int` | `0` | Cards shown today. |
| `session` | `VerbDrillSessionState?` | `null` | Active session state. `null` when not in a session. |
| `allDoneToday` | `Boolean` | `false` | `true` when all cards for the filter have been shown today. |
| `isLoading` | `Boolean` | `true` | Loading state. |
| `loadedLanguageId` | `String?` | `null` | Language ID of loaded data. |
| `badSentenceCount` | `Int` | `0` | Number of flagged bad sentences. |
| `currentCardIsBad` | `Boolean` | `false` | Whether the current card is flagged as bad. |
| `sortByFrequency` | `Boolean` | `false` | Whether to sort cards by frequency rank. |

---

### 1.1.18 `VocabWord`

**File:** `VocabWord.kt`
**Purpose:** A vocabulary word for the Anki-like spaced repetition drill. Parsed from pack CSV files by `ItalianDrillVocabParser`.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `id` | `String` | -- | Composite ID (e.g., `"nouns_casa"`, `"verbs_essere"`). |
| `word` | `String` | -- | Italian word form. |
| `pos` | `String` | -- | Part of speech: `"nouns"`, `"verbs"`, `"adjectives"`, `"adverbs"`. |
| `rank` | `Int` | -- | Frequency rank. Lower = more common. `>= 1`. |
| `meaningRu` | `String?` | `null` | Russian translation. May be null for some entries. |
| `collocations` | `List<String>` | `emptyList()` | Italian example phrases. |
| `forms` | `Map<String, String>` | `emptyMap()` | Morphological forms. Key is form code (e.g., `"msg"` = masculine singular), value is the form. |

---

### 1.1.19 `WordMasteryState`

**File:** `VocabWord.kt`
**Purpose:** Per-word spaced repetition state. Tracks the Anki-like interval ladder position for each vocab word.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `wordId` | `String` | -- | Links to `VocabWord.id`. |
| `intervalStepIndex` | `Int` | `0` | Position in `INTERVAL_LADDER_DAYS` (0-9). |
| `correctCount` | `Int` | `0` | Lifetime correct answers. `>= 0`. |
| `incorrectCount` | `Int` | `0` | Lifetime incorrect answers. `>= 0`. |
| `lastReviewDateMs` | `Long` | `0L` | Epoch millis of last review. `0` = never reviewed. |
| `nextReviewDateMs` | `Long` | `0L` | Computed: `lastReviewDateMs + INTERVAL_LADDER_DAYS[step] * DAY_MS`. `0` = due immediately. |
| `isLearned` | `Boolean` | `false` | `true` when `intervalStepIndex == 9` (reached the last ladder step). |

**Constants:**

| Constant | Value | Purpose |
|----------|-------|---------|
| `DAY_MS` | `86_400_000L` | Milliseconds in a day. |

**Factory method:** `WordMasteryState.new(wordId)` creates a fresh state with all defaults.

**Computation:** `computeNextReview(lastReviewMs, stepIndex)` returns `lastReviewMs + ladder[step] * DAY_MS`.

**Persistence path:** `grammarmate/drills/{packId}/word_mastery.yaml`.

---

### 1.1.20 `VocabDrillCard`

**File:** `VocabWord.kt`
**Purpose:** Combines a `VocabWord` with its `WordMasteryState` for drill presentation.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `word` | `VocabWord` | -- | The vocabulary word. |
| `mastery` | `WordMasteryState` | -- | Current mastery state for this word. |

---

### 1.1.21 `VocabDrillUiState`

**File:** `VocabWord.kt`
**Purpose:** Top-level UI state for the vocab drill screen.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `isLoading` | `Boolean` | `true` | Whether data is loading. |
| `availablePos` | `List<String>` | `emptyList()` | Available parts of speech. |
| `selectedPos` | `String?` | `null` | Currently selected POS filter. `null` = all. |
| `rankMin` | `Int` | `0` | Minimum rank filter. |
| `rankMax` | `Int` | `Int.MAX_VALUE` | Maximum rank filter. |
| `dueCount` | `Int` | `0` | Words due for review. |
| `totalCount` | `Int` | `0` | Total words matching filter. |
| `session` | `VocabDrillSessionState?` | `null` | Active session. `null` when not drilling. |
| `loadedLanguageId` | `String?` | `null` | Language ID of loaded data. |
| `drillDirection` | `VocabDrillDirection` | `VocabDrillDirection.IT_TO_RU` | Default drill direction. |
| `masteredCount` | `Int` | `0` | Number of words that reached `isLearned == true`. |
| `masteredByPos` | `Map<String, Int>` | `emptyMap()` | Mastered count broken down by POS. |

---

### 1.1.22 `VocabDrillSessionState`

**File:** `VocabWord.kt`
**Purpose:** Runtime state of an active vocab drill session (Anki-like flashcard session).

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `cards` | `List<VocabDrillCard>` | -- | Flashcard queue. |
| `currentIndex` | `Int` | `0` | Current card index. |
| `correctCount` | `Int` | `0` | Correct answers this session. |
| `incorrectCount` | `Int` | `0` | Incorrect answers this session. |
| `isComplete` | `Boolean` | `false` | `true` when all cards processed. |
| `isFlipped` | `Boolean` | `false` | Whether the card is flipped to show the answer side. |
| `direction` | `VocabDrillDirection` | `VocabDrillDirection.IT_TO_RU` | Current drill direction. |
| `voiceAttempts` | `Int` | `0` | Voice recognition attempts for current card (0-3). |
| `voiceRecognizedText` | `String?` | `null` | Last recognized text from voice input. |
| `voiceResult` | `VoiceResult?` | `null` | Result of voice recognition attempt. |
| `voiceCompleted` | `Boolean` | `false` | `true` after correct answer, 3 wrong attempts, or skip. |

---

### 1.1.23 `DrillFiles`

**File:** `LessonPackManifest.kt`
**Purpose:** Lists drill content files declared in a lesson pack manifest section.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `files` | `List<String>` | -- | Non-empty list of CSV filenames. Validated to be non-empty during parsing. |

---

### 1.1.24 `LessonPackManifest`

**File:** `LessonPackManifest.kt`
**Purpose:** Parsed representation of a lesson pack's `manifest.json`. Defines the pack's identity, lessons, and optional drill sections.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `schemaVersion` | `Int` | -- | Must be `1`. Parsing fails for any other value. |
| `packId` | `String` | -- | Unique pack identifier. Must not be blank. |
| `packVersion` | `String` | -- | Version string. Must not be blank. |
| `language` | `String` | -- | Target language code. Must not be blank. |
| `lessons` | `List<LessonPackLesson>` | -- | Lesson entries. Can be empty if drill sections exist. |
| `displayName` | `String?` | `null` | Optional display name override. |
| `verbDrill` | `DrillFiles?` | `null` | Optional verb drill files section. |
| `vocabDrill` | `DrillFiles?` | `null` | Optional vocab drill files section. |

**Validation rules (enforced in `fromJson`):**
- `schemaVersion` must be exactly `1`.
- `packId`, `packVersion`, `language` must all be non-blank.
- Each lesson entry must have non-blank `lessonId` and `file`.
- The manifest must have at least one standard lesson OR at least one drill section. A manifest with no lessons and no drills is rejected.

---

### 1.1.25 `LessonPackLesson`

**File:** `LessonPackManifest.kt`
**Purpose:** A single lesson entry within a pack manifest.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `lessonId` | `String` | -- | Unique lesson identifier within the pack. Must not be blank. |
| `order` | `Int` | -- | Display/import order. Defaults to index + 1. |
| `title` | `String?` | `null` | Optional display title. |
| `file` | `String` | -- | CSV filename within the ZIP. Must not be blank. |
| `drillFile` | `String?` | `null` | Optional drill-specific CSV filename. |
| `type` | `String` | `"standard"` | Lesson type. `"standard"` or `"verb_drill"`. `verb_drill` entries are filtered during import and not parsed as regular lessons. |
| `tenses` | `List<String>` | `emptyList()` | Tenses associated with this lesson. |

---

### 1.1.26 `SessionProgress`

**File:** `CardSessionContract.kt`
**Purpose:** Represents the current position within a card session.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `current` | `Int` | -- | Current card index (0-based). `>= 0`. |
| `total` | `Int` | -- | Total number of cards in the session. `>= 0`. |

---

### 1.1.27 `AnswerResult`

**File:** `CardSessionContract.kt`
**Purpose:** Result of checking a user's answer.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `correct` | `Boolean` | -- | Whether the answer was correct. |
| `displayAnswer` | `String` | -- | The answer text to display to the user. |
| `hintShown` | `Boolean` | `false` | Whether a hint was used for this answer. |

---

### 1.1.28 `InputModeConfig`

**File:** `CardSessionContract.kt`
**Purpose:** Configuration for input modes available in a session.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `availableModes` | `Set<InputMode>` | -- | Which input modes are available. Non-empty. |
| `defaultMode` | `InputMode` | -- | Default input mode. Must be in `availableModes`. |
| `showInputModeButtons` | `Boolean` | -- | Whether to show input mode toggle buttons in the UI. |

---

### 1.1.29 `TrainingUiState`

**File:** `TrainingViewModel.kt`
**Purpose:** The single source of truth for all UI state. Exposed via `StateFlow<TrainingUiState>` from `TrainingViewModel`. This is the central state object for the entire application.

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `languages` | `List<Language>` | `emptyList()` | Available languages. |
| `installedPacks` | `List<LessonPack>` | `emptyList()` | All imported packs. |
| `selectedLanguageId` | `String` | `"en"` | Current target language. |
| `activePackId` | `String?` | `null` | Currently active pack. |
| `activePackLessonIds` | `List<String>?` | `null` | Lesson IDs in the active pack. |
| `lessons` | `List<Lesson>` | `emptyList()` | Loaded lessons. |
| `selectedLessonId` | `String?` | `null` | Currently selected lesson. |
| `mode` | `TrainingMode` | `TrainingMode.LESSON` | Current training mode. |
| `sessionState` | `SessionState` | `SessionState.ACTIVE` | Current session state. |
| `currentIndex` | `Int` | `0` | Current card index. |
| `currentCard` | `SentenceCard?` | `null` | Active card. |
| `inputText` | `String` | `""` | User's typed input. |
| `correctCount` | `Int` | `0` | Session correct count. |
| `incorrectCount` | `Int` | `0` | Session incorrect count. |
| `incorrectAttemptsForCard` | `Int` | `0` | Consecutive wrong attempts on current card. |
| `activeTimeMs` | `Long` | `0L` | Active training time. |
| `voiceActiveMs` | `Long` | `0L` | Time in voice mode. |
| `voiceWordCount` | `Int` | `0` | Words recognized via voice. |
| `hintCount` | `Int` | `0` | Hints used (parenthetical hints only, see definition in 1.1.8). |
| `voicePromptStartMs` | `Long?` | `null` | Start time of voice prompt for speed calculation. |
| `answerText` | `String?` | `null` | Correct answer to display after check. |
| `lastResult` | `Boolean?` | `null` | Result of last answer check. |
| `lastRating` | `Double?` | `null` | Speed/quality rating of last answer. |
| `inputMode` | `InputMode` | `InputMode.VOICE` | Current input mode. |
| `voiceTriggerToken` | `Int` | `0` | Incremented to trigger voice input. |
| `subLessonTotal` | `Int` | `0` | Total sub-lessons in the current schedule. |
| `subLessonCount` | `Int` | `0` | Sub-lessons completed so far. |
| `subLessonTypes` | `List<SubLessonType>` | `emptyList()` | Types of sub-lessons in the schedule. |
| `activeSubLessonIndex` | `Int` | `0` | Current sub-lesson index. |
| `completedSubLessonCount` | `Int` | `0` | Total sub-lessons completed. |
| `subLessonFinishedToken` | `Int` | `0` | Incremented when a sub-lesson finishes. |
| `storyCheckInDone` | `Boolean` | `false` | Whether check-in story is done. |
| `storyCheckOutDone` | `Boolean` | `false` | Whether check-out story is done. |
| `activeStory` | `StoryQuiz?` | `null` | Currently active story quiz. |
| `storyErrorMessage` | `String?` | `null` | Story error. |
| `currentVocab` | `VocabEntry?` | `null` | Current vocab entry (legacy). |
| `vocabInputText` | `String` | `""` | Vocab input text. |
| `vocabAttempts` | `Int` | `0` | Vocab attempts. |
| `vocabAnswerText` | `String?` | `null` | Vocab answer display. |
| `vocabIndex` | `Int` | `0` | Vocab card index. |
| `vocabTotal` | `Int` | `0` | Total vocab cards. |
| `vocabWordBankWords` | `List<String>` | `emptyList()` | Word bank for vocab. |
| `vocabFinishedToken` | `Int` | `0` | Incremented when vocab finishes. |
| `vocabErrorMessage` | `String?` | `null` | Vocab error. |
| `vocabInputMode` | `InputMode` | `InputMode.VOICE` | Vocab input mode. |
| `vocabVoiceTriggerToken` | `Int` | `0` | Vocab voice trigger. |
| `bossActive` | `Boolean` | `false` | Whether a boss battle is active. |
| `bossType` | `BossType?` | `null` | Current boss battle type. |
| `bossTotal` | `Int` | `0` | Total boss questions. |
| `bossProgress` | `Int` | `0` | Boss questions answered. |
| `bossReward` | `BossReward?` | `null` | Boss reward earned. |
| `bossRewardMessage` | `String?` | `null` | Reward display message. |
| `bossFinishedToken` | `Int` | `0` | Incremented when boss finishes. |
| `bossLastType` | `BossType?` | `null` | Previous boss type. |
| `bossErrorMessage` | `String?` | `null` | Boss error. |
| `bossLessonRewards` | `Map<String, BossReward>` | `emptyMap()` | Per-lesson boss rewards. |
| `bossMegaRewards` | `Map<String, BossReward>` | `emptyMap()` | Per-language mega boss rewards. |
| `testMode` | `Boolean` | `false` | Test mode flag. |
| `eliteActive` | `Boolean` | `false` | Whether elite (daily) mode is active. |
| `eliteStepIndex` | `Int` | `0` | Current elite step. |
| `eliteBestSpeeds` | `List<Double>` | `emptyList()` | Best speeds per elite step. |
| `eliteFinishedToken` | `Int` | `0` | Incremented when elite finishes. |
| `eliteUnlocked` | `Boolean` | `false` | Whether elite mode is unlocked. |
| `eliteSizeMultiplier` | `Double` | `1.25` | Card count multiplier for elite. |
| `vocabSprintLimit` | `Int` | `20` | Max cards in vocab sprint. |
| `lessonFlowers` | `Map<String, FlowerVisual>` | `emptyMap()` | Per-lesson flower state. Key = lesson ID. |
| `currentLessonFlower` | `FlowerVisual?` | `null` | Flower for the selected lesson. |
| `currentLessonShownCount` | `Int` | `0` | Unique cards shown for selected lesson. |
| `wordBankWords` | `List<String>` | `emptyList()` | Available word bank words. |
| `selectedWords` | `List<String>` | `emptyList()` | User's selected word bank words (in order). |
| `currentStreak` | `Int` | `0` | Current daily streak. |
| `longestStreak` | `Int` | `0` | All-time longest streak. |
| `streakMessage` | `String?` | `null` | Streak status message. |
| `streakCelebrationToken` | `Int` | `0` | Incremented for streak celebration. |
| `userName` | `String` | `"GrammarMateUser"` | User display name. |
| `ladderRows` | `List<LessonLadderRow>` | `emptyList()` | Lesson ladder data. |
| `ttsState` | `TtsState` | `TtsState.IDLE` | Current TTS engine state. |
| `ttsDownloadState` | `DownloadState` | `DownloadState.Idle` | TTS model download state. |
| `ttsModelReady` | `Boolean` | `false` | Whether TTS model is loaded. |
| `ttsMeteredNetwork` | `Boolean` | `false` | Whether on metered network. |
| `bgTtsDownloading` | `Boolean` | `false` | Background TTS download active. |
| `bgTtsDownloadStates` | `Map<String, DownloadState>` | `emptyMap()` | Per-language TTS download states. |
| `ttsModelsReady` | `Map<String, Boolean>` | `emptyMap()` | Per-language TTS readiness. |
| `ttsSpeed` | `Float` | `1.0f` | TTS playback speed multiplier. |
| `ruTextScale` | `Float` | `1.0f` | Russian text display scale. |
| `badSentenceCount` | `Int` | `0` | Flagged bad sentences count. |
| `isDrillMode` | `Boolean` | `false` | Whether in drill mode. |
| `drillCardIndex` | `Int` | `0` | Current drill card index. |
| `drillTotalCards` | `Int` | `0` | Total drill cards. |
| `drillShowStartDialog` | `Boolean` | `false` | Show drill start dialog. |
| `drillHasProgress` | `Boolean` | `false` | Whether drill has existing progress. |
| `useOfflineAsr` | `Boolean` | `false` | Whether to use offline ASR. |
| `asrState` | `AsrState` | `AsrState.IDLE` | Current ASR engine state. |
| `asrModelReady` | `Boolean` | `false` | Whether ASR model is loaded. |
| `asrDownloadState` | `DownloadState` | `DownloadState.Idle` | ASR model download state. |
| `asrMeteredNetwork` | `Boolean` | `false` | Whether on metered network for ASR. |
| `asrErrorMessage` | `String?` | `null` | ASR error message. |
| `audioPermissionDenied` | `Boolean` | `false` | Whether audio permission was denied. |
| `initialScreen` | `String` | `"HOME"` | Screen to restore on launch. |
| `currentScreen` | `String` | `"HOME"` | Current screen name. |
| `vocabMasteredCount` | `Int` | `0` | Global vocab mastered count. |
| `dailySession` | `DailySessionState` | `DailySessionState()` | Active daily practice session. |
| `dailyCursor` | `DailyCursorState` | `DailyCursorState()` | Daily practice cursor state. |

**Size:** This class has 100+ fields. Per CLAUDE.md, if a feature adds 5+ fields they should be grouped into a nested data class. The current flat structure is a known technical debt item.

---

### 1.1.30 `LessonLadderRow`

**File:** `TrainingViewModel.kt`
**Purpose:** A row in the lesson ladder (unlock progression) display.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `index` | `Int` | -- | Position in the ladder (0-based). |
| `lessonId` | `String` | -- | Links to `Lesson.id`. |
| `title` | `String` | -- | Display title. |
| `uniqueCardShows` | `Int?` | -- | Unique cards practiced. `null` if lesson not started. |
| `daysSinceLastShow` | `Int?` | -- | Days since last practice. `null` if lesson not started. |
| `intervalLabel` | `String?` | `null` | Human-readable interval label (e.g., "every 7 days"). |

---

### 1.1.31 `ScheduledSubLesson`

**File:** `MixedReviewScheduler.kt`
**Purpose:** A sub-lesson produced by the scheduler, with a type and a card list.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `type` | `SubLessonType` | -- | `NEW_ONLY` or `MIXED`. |
| `cards` | `List<SentenceCard>` | -- | Cards for this sub-lesson. Size typically equals `SUB_LESSON_SIZE_DEFAULT` (10). |

---

### 1.1.32 `LessonSchedule`

**File:** `MixedReviewScheduler.kt`
**Purpose:** A complete schedule of sub-lessons for a lesson.

| Field | Type | Default | Validation / Notes |
|-------|------|---------|---------------------|
| `lessonId` | `String` | -- | Links to `Lesson.id`. |
| `subLessons` | `List<ScheduledSubLesson>` | -- | Ordered list of sub-lessons. |

---

## 1.2 Enums & Sealed Classes

### 1.2.1 `StoryPhase`

**File:** `Models.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `CHECK_IN` | Story quiz shown before starting a lesson. Assesses prior knowledge. |
| `CHECK_OUT` | Story quiz shown after completing a lesson. Assesses retention. |

**Transition rules:** CHECK_IN always precedes the lesson. CHECK_OUT always follows lesson completion. Both are optional (controlled by `storyCheckInDone` / `storyCheckOutDone` flags).

---

### 1.2.2 `TrainingMode`

**File:** `Models.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `LESSON` | Single lesson training. The user selects a specific lesson. |
| `ALL_SEQUENTIAL` | All lessons in sequence, one after another. |
| `ALL_MIXED` | All lessons mixed together, cards from different lessons interleaved. |

**Transition rules:** Set when a training session starts. Does not change mid-session. Persisted in `TrainingProgress.mode`.

---

### 1.2.3 `BossType`

**File:** `Models.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `LESSON` | End-of-lesson challenge. Tests stability of patterns from a single lesson. |
| `MEGA` | Cross-lesson challenge. Tests across all completed lessons. |
| `ELITE` | Legacy elite mode boss. Kept for backward compatibility. |

---

### 1.2.4 `BossReward`

**File:** `Models.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `BRONZE` | Low performance in boss battle. |
| `SILVER` | Medium performance in boss battle. |
| `GOLD` | High performance in boss battle. |

**Transition rules:** Determined at boss battle completion based on accuracy/speed metrics. Never changes after being assigned.

---

### 1.2.5 `SessionState`

**File:** `Models.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `ACTIVE` | User is actively answering cards. Timer is running. |
| `PAUSED` | Session is paused (between sub-lessons, during dialogs, etc.). |
| `AFTER_CHECK` | Card has been checked -- showing correct/incorrect feedback. |
| `HINT_SHOWN` | A hint is currently displayed for the active card. |

**State transitions:**
```
PAUSED --> ACTIVE    (session starts / resumes)
ACTIVE --> AFTER_CHECK   (answer submitted)
AFTER_CHECK --> ACTIVE   (next card shown)
ACTIVE --> HINT_SHOWN    (hint requested)
HINT_SHOWN --> ACTIVE    (user continues after hint)
ACTIVE --> PAUSED    (session paused)
ANY --> PAUSED       (interruption, dialog, sub-lesson boundary)
```

**Default in `TrainingProgress`:** `PAUSED` (serialized form).
**Default in `TrainingUiState`:** `ACTIVE` (runtime form).

---

### 1.2.6 `InputMode`

**File:** `Models.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `VOICE` | Speech recognition input. Counts toward mastery. |
| `KEYBOARD` | Typing input. Counts toward mastery. |
| `WORD_BANK` | Word selection input. Does NOT count toward mastery. |

**Critical business rule:** Only VOICE and KEYBOARD increment `uniqueCardShows` and contribute to flower growth. WORD_BANK practice is practice-only and does not affect mastery metrics.

---

### 1.2.7 `FlowerState`

**File:** `Models.kt`
**Values:**

| Value | When Used | Visual |
|-------|-----------|--------|
| `LOCKED` | Lesson is locked (prerequisite not met). Assigned in UI only, not by `FlowerCalculator`. | Lock icon |
| `SEED` | Lesson available but no mastery yet (`uniqueCardShows == 0`), or mastery < 33%. | Seedling |
| `SPROUT` | Lesson has some mastery (33% <= mastery < 66%) and health is full. | Sprout |
| `BLOOM` | Lesson has high mastery (>= 66%) and health is full. | Flower |
| `WILTING` | Health has dropped below 100% but above the wilted threshold. | Wilting flower |
| `WILTED` | Health has dropped to the wilted threshold (~50%). | Dead leaves |
| `GONE` | More than 90 days since last practice. | Empty circle |

**State transitions (as computed by `FlowerCalculator.determineFlowerState`):**

```
Priority order (first matching rule wins):
1. healthPercent <= WILTED_THRESHOLD + 0.01  -->  WILTED
2. healthPercent < 1.0                        -->  WILTING
3. masteryPercent < 0.33                      -->  SEED
4. masteryPercent < 0.66                      -->  SPROUT
5. masteryPercent >= 0.66                     -->  BLOOM

Special cases:
- mastery == null OR uniqueCardShows == 0     -->  SEED (with scale=0.5)
- daysSinceLastShow > 90                      -->  GONE (with mastery=0, health=0, scale=0.5)
```

**Note:** `LOCKED` is never produced by `FlowerCalculator`. It is set in the UI layer (`LessonTileState.LOCKED`) based on ladder unlock logic.

---

### 1.2.8 `DailyBlockType`

**File:** `Models.kt`
**Values:**

| Value | When Used | Block Index |
|-------|-----------|-------------|
| `TRANSLATE` | Sentence translation exercises. | 0 |
| `VOCAB` | Anki-style vocabulary flashcards. | 1 |
| `VERBS` | Verb conjugation exercises. | 2 |

**Fixed order in daily practice:** TRANSLATE (10 cards) --> VOCAB (5 cards) --> VERBS (10 cards).

---

### 1.2.9 `VocabDrillDirection`

**File:** `VocabWord.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `IT_TO_RU` | Italian word shown as prompt, user provides Russian translation. |
| `RU_TO_IT` | Russian word shown as prompt, user provides Italian translation. |

---

### 1.2.10 `VoiceResult`

**File:** `VocabWord.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `CORRECT` | Voice recognition matched the expected answer. |
| `WRONG` | Voice recognition returned text that did not match. |
| `SKIPPED` | User skipped voice input for this card. |

---

### 1.2.11 `TtsState`

**File:** `TtsEngine.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `IDLE` | TTS engine not initialized. |
| `INITIALIZING` | TTS engine is loading. |
| `READY` | TTS engine is ready to speak. |
| `SPEAKING` | TTS is currently speaking. |
| `ERROR` | TTS engine encountered an error. |

**Transitions:** `IDLE --> INITIALIZING --> READY --> SPEAKING --> READY` (cycle). `ANY --> ERROR` on failure.

---

### 1.2.12 `AsrState`

**File:** `AsrEngine.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `IDLE` | ASR engine not initialized. |
| `INITIALIZING` | ASR model loading. |
| `READY` | ASR ready for recording. |
| `RECORDING` | ASR is recording audio. |
| `RECOGNIZING` | ASR is processing recorded audio. |
| `ERROR` | ASR encountered an error. |

**Transitions:** `IDLE --> INITIALIZING --> READY --> RECORDING --> RECOGNIZING --> READY` (cycle). `ANY --> ERROR` on failure.

---

### 1.2.13 `SubLessonType`

**File:** `MixedReviewScheduler.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `NEW_ONLY` | Fresh cards only. No review cards. Used for initial learning. |
| `MIXED` | Combination of new cards and spaced review cards from the review pool. |

**Note:** `WARMUP` was removed and must never be re-introduced (per CLAUDE.md).

---

### 1.2.14 `AppScreen` (private)

**File:** `GrammarMateApp.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `HOME` | Main home screen. |
| `LESSON` | Lesson road map / detail. |
| `ELITE` | Legacy -- redirects to HOME if restored from saved state. Kept for backward compat. |
| `VOCAB` | Legacy -- redirects to HOME if restored from saved state. Kept for backward compat. |
| `DAILY_PRACTICE` | Daily practice session screen. |
| `STORY` | Story quiz screen. |
| `TRAINING` | Active training screen. |
| `LADDER` | Lesson ladder (unlock progression) screen. |
| `VERB_DRILL` | Verb drill standalone screen. |
| `VOCAB_DRILL` | Vocab drill standalone screen. |

**Critical note:** `ELITE` and `VOCAB` enum values must not be removed. If a user's saved state contains `currentScreen: "ELITE"` or `"VOCAB"`, removing the enum value would crash the app. `parseScreen()` falls back to `HOME` for unknown values.

---

### 1.2.15 `LessonTileState` (private)

**File:** `GrammarMateApp.kt`
**Values:**

| Value | When Used |
|-------|-----------|
| `SEED` | Lesson started, low mastery. |
| `SPROUT` | Lesson in progress, medium mastery. |
| `FLOWER` | Lesson has high mastery (BLOOM equivalent). |
| `LOCKED` | Lesson is locked (prerequisite not met). |
| `UNLOCKED` | Available but not started (open lock icon). |
| `EMPTY` | No lesson in this slot (pack has fewer lessons than the grid size). |
| `VERB_DRILL` | Special tile for the verb drill entry point. |

---

## 1.3 Sealed Classes

### 1.3.1 `DailyTask`

**File:** `Models.kt`
**Purpose:** Sealed hierarchy representing a single task within a daily practice session. Each variant corresponds to one of the three daily practice blocks.

**Abstract members:**

| Member | Type | Description |
|--------|------|-------------|
| `id` | `String` | Unique task identifier. |
| `blockType` | `DailyBlockType` | Which block this task belongs to. |

**Variants:**

#### `DailyTask.TranslateSentence`
| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | Task ID. |
| `card` | `SentenceCard` | The sentence card to translate. |
| `inputMode` | `InputMode` | Input mode for this card. |
| `blockType` | `DailyBlockType` | Always `TRANSLATE`. |

#### `DailyTask.VocabFlashcard`
| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | Task ID. |
| `word` | `VocabWord` | The vocabulary word to drill. |
| `direction` | `VocabDrillDirection` | Which direction to drill (IT->RU or RU->IT). |
| `blockType` | `DailyBlockType` | Always `VOCAB`. |

#### `DailyTask.ConjugateVerb`
| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | Task ID. |
| `card` | `VerbDrillCard` | The verb conjugation card. |
| `inputMode` | `InputMode` | Input mode for this card. |
| `blockType` | `DailyBlockType` | Always `VERBS`. |

---

### 1.3.2 `DownloadState`

**File:** `TtsModelManager.kt`
**Purpose:** Sealed hierarchy representing the state of a model download (used for both TTS and ASR models).

**Variants:**

| Variant | Fields | When Used |
|---------|--------|-----------|
| `Idle` | (none) | No download in progress. |
| `Downloading` | `percent: Int`, `bytesDownloaded: Long`, `totalBytes: Long` | Actively downloading. |
| `Extracting` | `percent: Int` | Extracting downloaded archive. |
| `Done` | (none) | Download and extraction complete. |
| `Error` | `message: String` | Download failed with an error. |

---

## 1.4 Contracts & Interfaces

### 1.4.1 `SessionCard` (interface)

**File:** `CardSessionContract.kt`
**Purpose:** Common interface for all card types that can be presented in a training session. Provides a uniform API for the UI to render any card type.

| Member | Type | Description |
|--------|------|-------------|
| `id` | `String` | Unique card identifier. |
| `promptRu` | `String` | Russian prompt text to display. |
| `acceptedAnswers` | `List<String>` | All valid answer strings. |

**Implementors:**
- `SentenceCard` -- `acceptedAnswers` is a stored list of multiple valid answers.
- `VerbDrillCard` -- `acceptedAnswers` is a computed single-element list (`listOf(answer)`).

---

### 1.4.2 `CardSessionCapabilities` (interface)

**File:** `CardSessionContract.kt`
**Purpose:** Optional capabilities that a card session may support. All default to `false`.

| Member | Type | Default | Description |
|--------|------|---------|-------------|
| `supportsTts` | `Boolean` | `false` | Can play TTS audio for the card. |
| `supportsVoiceInput` | `Boolean` | `false` | Can accept voice input. |
| `supportsWordBank` | `Boolean` | `false` | Has a word bank selection mode. |
| `supportsFlagging` | `Boolean` | `false` | Can flag/unflag cards as bad. |
| `supportsNavigation` | `Boolean` | `false` | Can navigate to prev/next card. |
| `supportsPause` | `Boolean` | `false` | Can pause the session. |

---

### 1.4.3 `CardSessionContract` (interface)

**File:** `CardSessionContract.kt`
**Purpose:** The main contract that a card session provider must implement. Adapters wrap existing ViewModel/session logic to satisfy this interface. Used to abstract the common training flow across different session types (lesson, drill, daily practice).

**Extends:** `CardSessionCapabilities`

**Required properties:**

| Property | Type | Description |
|----------|------|-------------|
| `currentCard` | `SessionCard?` | The card currently being presented. `null` if session is over. |
| `progress` | `SessionProgress` | Current position (current/total). |
| `isComplete` | `Boolean` | Whether the session is finished. |
| `inputText` | `String` | Current user input text. |
| `inputModeConfig` | `InputModeConfig` | Available input modes and configuration. |
| `lastResult` | `AnswerResult?` | Result of the last answer check. `null` if not checked yet. |
| `sessionActive` | `Boolean` | Whether the session is currently active (not paused/complete). |

**Properties with default implementations:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ttsState` | `TtsState` | `TtsState.IDLE` | Current TTS state. |
| `currentInputMode` | `InputMode` | `inputModeConfig.defaultMode` | Current input mode. |
| `languageId` | `String` | `"en"` | Language for voice recognition locale. |
| `currentSpeedWpm` | `Int` | `0` | Current typing speed in words per minute. |

**Required methods:**

| Method | Returns | Description |
|--------|---------|-------------|
| `onInputChanged(text: String)` | (Unit) | Update the user's input text. |
| `submitAnswer()` | `AnswerResult?` | Check the current input against accepted answers. |
| `showAnswer()` | `String?` | Reveal the correct answer without checking. |
| `nextCard()` | (Unit) | Advance to the next card. |
| `prevCard()` | (Unit) | Go back to the previous card. |

**Optional methods (with default no-op implementations):**

| Method | Default | Description |
|--------|---------|-------------|
| `onVoiceInputResult(text: String)` | Delegates to `onInputChanged(text)` | Handle voice recognition result. |
| `setInputMode(mode: InputMode)` | No-op | Switch input mode. |
| `getSelectedWords()` | `emptyList()` | Get current word bank selection. |
| `getWordBankWords()` | `emptyList()` | Get available word bank words. |
| `selectWordFromBank(word: String)` | No-op | Select a word from the bank. |
| `removeLastSelectedWord()` | No-op | Remove last selected word bank word. |
| `speakTts()` | No-op | Trigger TTS for the current card. |
| `stopTts()` | No-op | Stop TTS playback. |
| `flagCurrentCard()` | No-op | Flag the current card as bad. |
| `unflagCurrentCard()` | No-op | Remove the bad flag from the current card. |
| `isCurrentCardFlagged()` | `false` | Check if the current card is flagged. |
| `hideCurrentCard()` | No-op | Hide the current card from future sessions. |
| `exportFlaggedCards()` | `null` | Export flagged cards as a string. |
| `togglePause()` | No-op | Toggle session pause state. |
| `requestExit()` | No-op | Request to exit the session. |
| `requestNextBatch()` | No-op | Request the next batch of cards. |

---

### 1.4.4 `TrainingStateAccess` (interface)

**File:** `feature/daily/DailySessionHelper.kt`
**Purpose:** Interface provided by `TrainingViewModel` to its helper classes. Allows helpers to read and update UI state without directly referencing the ViewModel.

| Member | Type | Description |
|--------|------|-------------|
| `uiState` | `StateFlow<TrainingUiState>` | Read-only flow of current UI state. |
| `updateState(transform: (TrainingUiState) -> TrainingUiState)` | Function | Atomically update the UI state. |
| `saveProgress()` | Function | Persist the current progress to storage. |

**Implementation:** `TrainingViewModel` creates an anonymous object implementing this interface. All helpers in `feature/` receive this via constructor injection.

**Constraint:** Helpers never call other helpers directly. All coordination flows through `TrainingViewModel`.

---

## 1.5 Configuration Objects

### 1.5.1 `TrainingConfig` (object)

**File:** `TrainingConfig.kt`
**Purpose:** Compile-time constants for training session sizing.

| Constant | Type | Value | Description |
|----------|------|-------|-------------|
| `SUB_LESSON_SIZE_DEFAULT` | `Int` | `10` | Default number of cards per sub-lesson. |
| `SUB_LESSON_SIZE_MIN` | `Int` | `6` | Minimum sub-lesson size. |
| `SUB_LESSON_SIZE_MAX` | `Int` | `12` | Maximum sub-lesson size. |
| `ELITE_STEP_COUNT` | `Int` | `7` | Number of steps in elite (daily) mode. |

---

### 1.5.2 `SpacedRepetitionConfig` (object)

**File:** `SpacedRepetitionConfig.kt`
**Purpose:** Ebbinghaus forgetting curve calculations and interval ladder configuration.

**Constants:**

| Constant | Type | Value | Description |
|----------|------|-------|-------------|
| `INTERVAL_LADDER_DAYS` | `List<Int>` | `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` | Review intervals in days. Each step increases ~1.5-2x. |
| `MASTERY_THRESHOLD` | `Int` | `150` | Unique card shows needed for 100% mastery. |
| `WILTED_THRESHOLD` | `Float` | `0.5f` | Health below which a flower is considered wilted. |
| `GONE_THRESHOLD_DAYS` | `Int` | `90` | Days without review before a flower disappears. |
| `BASE_STABILITY_DAYS` | `Double` | `0.9` (private) | Base memory stability in days for new material. |
| `STABILITY_MULTIPLIER` | `Double` | `2.2` (private) | Stability growth factor per interval step. |

**Functions:**

| Function | Signature | Description |
|----------|-----------|-------------|
| `calculateStability` | `(intervalStepIndex: Int) -> Double` | Memory stability S = S0 * multiplier^step. |
| `calculateRetention` | `(daysSinceLastShow: Int, intervalStepIndex: Int) -> Float` | Retention R = e^(-t/S). Returns 0.0-1.0. |
| `calculateHealthPercent` | `(daysSinceLastShow: Int, intervalStepIndex: Int) -> Float` | Flower health. Returns WILTED_THRESHOLD to 1.0, or 0 if GONE. |
| `nextIntervalStep` | `(currentStepIndex: Int, wasOnTime: Boolean) -> Int` | Advance or hold the interval step. |
| `wasRepetitionOnTime` | `(daysSinceLastShow: Int, intervalStepIndex: Int) -> Boolean` | Check if review happened within the expected interval. |

---

### 1.5.3 `FlowerCalculator` (object)

**File:** `FlowerCalculator.kt`
**Purpose:** Computes `FlowerVisual` from `LessonMasteryState` using Ebbinghaus math from `SpacedRepetitionConfig`.

**Functions:**

| Function | Signature | Description |
|----------|-----------|-------------|
| `calculate` | `(mastery: LessonMasteryState?, totalCardsInLesson: Int) -> FlowerVisual` | Main entry point. Returns visual state. |
| `getEmoji` | `(state: FlowerState) -> String` | Returns emoji character for a flower state. |
| `getEmojiWithScale` | `(flower: FlowerVisual) -> Pair<String, Float>` | Returns emoji + scale multiplier. |

---

## 1.6 Invariants & Business Rules

### 1.6.1 Mastery Invariants

1. **WORD_BANK never counts:** Only `InputMode.VOICE` and `InputMode.KEYBOARD` contribute to `LessonMasteryState.uniqueCardShows` and `LessonMasteryState.shownCardIds`. Violating this inflates progress and is a Level A correctness violation.

2. **uniqueCardShows == shownCardIds.size:** These two fields must always be in sync. The count must exactly equal the set size.

3. **uniqueCardShows <= 150:** Bounded by `MASTERY_THRESHOLD`. Even if a lesson has more than 150 cards, mastery counts only the main pool (first 150).

4. **Mastery is main-pool only:** Only cards from `Lesson.mainPoolCards` (first 150) contribute to mastery. Reserve pool cards are never counted.

5. **intervalStepIndex in [0, 9]:** Must be a valid index into `INTERVAL_LADDER_DAYS`. Out-of-range values are clamped to the last index.

### 1.6.2 Flower State Invariants

1. **LOCKED is UI-only:** `FlowerCalculator` never returns `LOCKED`. This state is assigned in the UI layer based on ladder unlock logic.

2. **GONE resets mastery:** When `daysSinceLastShow > 90`, `FlowerCalculator` returns `masteryPercent = 0` regardless of actual mastery data. The underlying `LessonMasteryState` is not modified -- the visual state is purely computed.

3. **WILTED_THRESHOLD is the floor:** `calculateHealthPercent` never returns a value between 0 and `WILTED_THRESHOLD` (0.5) unless the flower is GONE (returns 0). The range is `[0.5, 1.0]` for non-GONE flowers.

4. **scaleMultiplier in [0.5, 1.0]:** Always clamped to this range.

5. **Health priority over mastery:** The flower state determination checks health first. A flower with high mastery but low health will show WILTING or WILTED, not BLOOM.

### 1.6.3 Session Invariants

1. **Single ViewModel:** `TrainingViewModel` is the sole owner of `TrainingUiState`. All helpers access state through `TrainingStateAccess`. No second ViewModel may be created (Level B constraint).

2. **Atomic state updates:** All state modifications must go through `updateState { }` which performs a single atomic `copy()`. Partial updates or direct field mutation are prohibited.

3. **Atomic file writes:** All persisted state must be written through `AtomicFileWriter` (temp -> fsync -> rename). Direct `File.writeText` is prohibited (Level B constraint).

4. **Session card counts:** Daily practice sessions have fixed sizes: 10 translate cards + 5 vocab flashcards + 10 verb cards = 25 total tasks.

5. **Sub-lesson size bounds:** `SUB_LESSON_SIZE_MIN` (6) <= actual size <= `SUB_LESSON_SIZE_MAX` (12). Default is 10.

### 1.6.4 Pack-Scoping Invariants

1. **Drill visibility is pack-scoped:** `hasVerbDrill` / `hasVocabDrill` check the active pack's manifest, not all installed packs. Switching packs may hide drill tiles.

2. **Drill progress is pack-scoped:** Verb drill progress, word mastery, and drill files are all stored under `grammarmate/drills/{packId}/`. Switching packs loads different progress data.

3. **verb_drill lesson entries are filtered:** During import, `LessonPackLesson` entries with `type: "verb_drill"` are not parsed as standard lessons. Their CSV files are handled separately.

4. **Manifest must have content:** A manifest must have at least one standard lesson OR at least one drill section (`verbDrill` or `vocabDrill`). Completely empty manifests are rejected.

### 1.6.5 Backward Compatibility

1. **ELITE and VOCAB AppScreen values are kept:** Removing them would crash users with saved `currentScreen: "ELITE"` or `"VOCAB"`. They redirect to HOME.

2. **TrainingProgress stores String rewards:** `bossLessonRewards` is `Map<String, String>` in the persisted form but `Map<String, BossReward>` in the UI state. The serialization format must remain stable.

3. **WARMUP SubLessonType was removed:** It must never be re-introduced. Only `NEW_ONLY` and `MIXED` exist.

### 1.6.6 VerbDrillCard Specifics

1. **Single answer:** `VerbDrillCard.acceptedAnswers` always returns exactly one element (`listOf(answer)`). Unlike `SentenceCard`, which may have multiple accepted answers, verb drill cards have a single correct conjugated form.

2. **todayShownCardIds resets daily:** `VerbDrillComboProgress.todayShownCardIds` is compared against `lastDate` to detect day changes. When the date changes, today's set is cleared.

### 1.6.7 Word Mastery Invariants

1. **isLearned at step 9:** `WordMasteryState.isLearned` becomes `true` when `intervalStepIndex` reaches 9 (the last step of the ladder). This is the "graduated" state.

2. **nextReviewDateMs == 0 means due immediately:** A new word with `lastReviewDateMs == 0` has `nextReviewDateMs == 0` and is always considered due.

3. **intervalStepIndex bounded by ladder size:** Must be in `[0, 9]` (10 steps in `INTERVAL_LADDER_DAYS`). Out-of-range values fall back to the last step.

### 1.6.8 Cursor State Invariants

1. **sentenceOffset increments by 10:** Each completed daily session advances `DailyCursorState.sentenceOffset` by `SUB_LESSON_SIZE_DEFAULT` (10).

2. **currentLessonIndex wraps:** When `sentenceOffset` exceeds the lesson's card count, `currentLessonIndex` increments and `sentenceOffset` resets.

3. **firstSessionDate is ISO format:** Always `yyyy-MM-dd`. Empty string means no session today.

4. **firstSession card IDs are populated on first session:** `firstSessionSentenceCardIds` and `firstSessionVerbCardIds` are filled only on the first session of the day. Subsequent sessions on the same day get different cards, but "Repeat" replays the first session's cards.
