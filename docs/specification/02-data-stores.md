# 2. Data Stores — Specification

All persistent stores in GrammarMate live under `context.filesDir/grammarmate/` and use YAML as the serialization format (via SnakeYAML). Every store that writes files uses `AtomicFileWriter` (temp -> fsync -> rename) to prevent data corruption on crash or power loss.

---

## 2.1 LessonStore

- **Purpose**: Manages lesson packs (ZIP import/export), lesson content (CSV), languages, stories, vocab entries, and pack-scoped drill files. This is the primary content management store and the largest in the data layer.
- **File location**:
  - Languages: `grammarmate/languages.yaml`
  - Lesson index per language: `grammarmate/lessons/{languageId}_index.yaml`
  - Lesson CSV files: `grammarmate/lessons/{languageId}/lesson_{id}.csv`
  - Drill CSV per lesson: `grammarmate/lessons/{languageId}/lesson_{id}_drill.csv`
  - Pack registry: `grammarmate/packs.yaml`
  - Pack extracted data: `grammarmate/packs/{packId}/` (full ZIP contents including `manifest.json`)
  - Stories index: `grammarmate/stories/stories.yaml`
  - Story JSON files: `grammarmate/stories/{storyId}.json`
  - Vocab index: `grammarmate/vocab/vocab.yaml`
  - Vocab CSV files: `grammarmate/vocab/{languageId}/vocab_{lessonId}.csv`
  - Pack-scoped verb drill CSVs: `grammarmate/drills/{packId}/verb_drill/{languageId}_*.csv`
  - Pack-scoped vocab drill CSVs: `grammarmate/drills/{packId}/vocab_drill/{languageId}_*.csv`
  - Seed marker: `grammarmate/seed_v1.done`

- **Data format**:
  - `languages.yaml` — YamlListStore schema: `{ schemaVersion: 1, items: [{ id: "en", name: "English" }, ...] }`
  - `{languageId}_index.yaml` — YamlListStore schema: `{ schemaVersion: 1, items: [{ id: "...", title: "...", file: "lesson_xxx.csv", drillFile: "lesson_xxx_drill.csv" (optional) }] }`
  - `packs.yaml` — YamlListStore schema: `{ schemaVersion: 1, items: [{ packId: "...", packVersion: "...", languageId: "...", importedAt: 123456, displayName: "..." (optional) }] }`
  - `stories.yaml` — YamlListStore schema: `{ schemaVersion: 1, items: [{ storyId: "...", lessonId: "...", phase: "CHECK_IN|CHECK_OUT", languageId: "...", file: "{storyId}.json" }] }`
  - `vocab.yaml` — YamlListStore schema: `{ schemaVersion: 1, items: [{ lessonId: "...", languageId: "...", file: "vocab_{lessonId}.csv" }] }`
  - Lesson CSV: 2-column semicolon-delimited (`ru;answers`), no header. Answers separated by `+`.
  - Drill CSV: same format as lesson CSV, used for drill practice.

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `ensureSeedData` | `fun ensureSeedData()` | Unit | Creates `lessons/` directory and `languages.yaml` with default English/Italian entries if they do not exist. |
  | `seedDefaultPacksIfNeeded` | `fun seedDefaultPacksIfNeeded(): Boolean` | Boolean | On first run (no `seed_v1.done` marker and no lesson content), imports default packs from assets. Returns true if any pack was seeded. Writes seed marker. |
  | `updateDefaultPacksIfNeeded` | `fun updateDefaultPacksIfNeeded(): Boolean` | Boolean | Checks installed packs against bundled defaults; re-imports any whose `packVersion` differs. Returns true if any pack was updated. |
  | `forceReloadDefaultPacks` | `fun forceReloadDefaultPacks(): Boolean` | Boolean | Removes existing default pack data and re-imports from assets. Used after app reinstall. |
  | `getLanguages` | `fun getLanguages(): List<Language>` | List of `Language(id, displayName)` | Reads `languages.yaml`, parses entries. |
  | `addLanguage` | `fun addLanguage(name: String): Language` | `Language` | Generates a slug-based ID from the name, ensures uniqueness, appends to `languages.yaml`. |
  | `getInstalledPacks` | `fun getInstalledPacks(): List<LessonPack>` | List of `LessonPack` | Reads `packs.yaml` registry. |
  | `getPackIdForLesson` | `fun getPackIdForLesson(lessonId: String): String?` | String? | Iterates all installed pack manifests to find which pack contains the given lesson ID. |
  | `getLessonIdsForPack` | `fun getLessonIdsForPack(packId: String): List<String>` | List of String | Reads the pack manifest and returns lesson IDs sorted by order. |
  | `getCumulativeTenses` | `fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String>` | List of String | Returns deduplicated tenses from lessons 1..lessonLevel in the pack, excluding `verb_drill` type lessons. |
  | `importPackFromUri` | `fun importPackFromUri(uri: Uri, resolver: ContentResolver): LessonPack` | `LessonPack` | Imports a pack from a content URI (user-selected ZIP). Delegates to `importPackFromStream`. |
  | `importPackFromAssets` | `fun importPackFromAssets(assetPath: String): LessonPack` | `LessonPack` | Imports a pack from the app's assets directory. |
  | `removeInstalledPackData` | `fun removeInstalledPackData(packId: String): Boolean` | Boolean | Deletes all lesson files, pack directory, pack registry entry, and pack-scoped drill directory. |
  | `getLessons` | `fun getLessons(languageId: String): List<Lesson>` | List of `Lesson` | Reads the language index, parses each CSV file, returns fully hydrated Lesson objects with cards and drill cards. Card IDs are prefixed with lesson ID. |
  | `importFromUri` | `fun importFromUri(languageId: String, uri: Uri, resolver: ContentResolver): Lesson` | `Lesson` | Imports a single CSV lesson from a URI. Replaces by title if duplicate. |
  | `createEmptyLesson` | `fun createEmptyLesson(languageId: String, title: String): Lesson` | `Lesson` | Creates a CSV file with just the title as content. Replaces by title if duplicate. |
  | `deleteAllLessons` | `fun deleteAllLessons(languageId: String)` | Unit | Deletes the entire language directory, its index, associated vocab, stories, and pack entries. |
  | `deleteLesson` | `fun deleteLesson(languageId: String, lessonId: String)` | Unit | Removes a specific lesson from the index and deletes its CSV files and vocab entries. |
  | `getStoryQuizzes` | `fun getStoryQuizzes(lessonId: String, phase: StoryPhase, languageId: String): List<StoryQuiz>` | List of `StoryQuiz` | Queries the stories index by lesson/phase/language, parses JSON files. |
  | `getVocabEntries` | `fun getVocabEntries(lessonId: String, languageId: String): List<VocabEntry>` | List of `VocabEntry` | Reads vocab CSV from index, optionally merges Italian drill vocab from assets. |
  | `getVerbDrillFiles` | `fun getVerbDrillFiles(packId: String, languageId: String): List<File>` | List of File | Lists CSV files in `drills/{packId}/verb_drill/` matching the language prefix. |
  | `getVerbDrillFilesForPack` | `fun getVerbDrillFilesForPack(packId: String): List<File>` | List of File | Lists all CSV files in `drills/{packId}/verb_drill/`. |
  | `getVocabDrillFiles` | `fun getVocabDrillFiles(packId: String, languageId: String): List<File>` | List of File | Lists CSV files in `drills/{packId}/vocab_drill/` matching the language prefix. |
  | `getVocabDrillFilesForPack` | `fun getVocabDrillFilesForPack(packId: String): List<File>` | List of File | Lists all CSV files in `drills/{packId}/vocab_drill/`. |
  | `getVocabWordsByRankRange` | `fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<VocabWord>` | List of `VocabWord` | Parses vocab drill CSVs, filters by rank range, returns sorted by rank. |
  | `hasVerbDrill` | `fun hasVerbDrill(packId: String, languageId: String): Boolean` | Boolean | Returns true if `drills/{packId}/verb_drill/` contains any CSV for the given language. |
  | `hasVocabDrill` | `fun hasVocabDrill(packId: String, languageId: String): Boolean` | Boolean | Returns true if `drills/{packId}/vocab_drill/` contains any CSV for the given language. |

  Deprecated methods (kept for backward compat):
  - `getVerbDrillFiles(languageId: String)` — legacy, reads from global `verb_drill/` directory
  - `hasVerbDrillLessons(languageId: String)` — legacy counterpart

- **Write semantics**: All index and registry files written via `YamlListStore.write()` which uses `AtomicFileWriter`. Story JSON files and vocab CSV files also use `AtomicFileWriter`. Lesson CSVs during import use stream copy (not atomic, but sourced from immutable asset/ZIP content).

- **Read semantics**: No caching. Every call to `getLessons()`, `getLanguages()`, `getInstalledPacks()`, etc. reads and parses from disk. This is acceptable because these are called infrequently (screen transitions, import events) and the data sizes are small.

- **Error handling**:
  - Missing `manifest.json` in ZIP: throws `error("Manifest not found")`, cleans up temp directory.
  - Missing lesson file referenced in manifest: throws `error("Missing lesson file: ...")`.
  - Invalid `schemaVersion` in manifest JSON: throws `error("Unsupported schemaVersion: ...")`.
  - ZIP extraction uses path traversal protection (canonical path check).
  - Failed imports are wrapped in `runCatching` during seed/update operations to avoid blocking other packs.

- **Invariants**:
  - A language ID is always lowercase and trimmed.
  - Card IDs are always prefixed with lesson ID (`{lessonId}_{index}`) to prevent cross-lesson collisions.
  - `packs.yaml` contains at most one entry per `packId`.
  - Lesson index entries have unique `id` fields within a language.
  - `replaceByTitle` and `replaceById` ensure no duplicates before saving a new entry.
  - Seed marker ensures default pack import only happens once (or on explicit force reload).
  - Lessons with `type: "verb_drill"` in the manifest are excluded from standard lesson import; their CSVs are imported as drill content instead.

- **Dependencies**:
  - `YamlListStore` (for all YAML index/registry files)
  - `AtomicFileWriter` (for all file writes)
  - `CsvParser` (lesson CSV parsing)
  - `VocabCsvParser` (vocab CSV parsing)
  - `ItalianDrillVocabParser` (Italian-specific drill vocab from assets)
  - `StoryQuizParser` (story JSON parsing)
  - `VerbDrillCsvParser` (verb drill CSV parsing, used indirectly via `VerbDrillStore`)
  - `LessonPackManifest.fromJson` (manifest parsing)

---

## 2.2 MasteryStore

- **Purpose**: Tracks per-lesson card mastery — how many unique cards have been practiced, total shows, which specific card IDs have been seen, and interval-based spaced repetition progress. This is the core store for flower state computation.
- **File location**: `grammarmate/mastery.yaml`
- **Data format**: YAML, schema version 1:
  ```yaml
  schemaVersion: 1
  data:
    {languageId}:
      {lessonId}:
        uniqueCardShows: 42
        totalCardShows: 87
        lastShowDateMs: 1715500800000
        intervalStepIndex: 3
        completedAtMs: 1715500800000   # or 0 if not completed
        shownCardIds:
          - "lesson1_0"
          - "lesson1_1"
  ```

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `loadAll` | `fun loadAll(): Map<String, Map<String, LessonMasteryState>>` | Nested map: languageId -> lessonId -> state | Loads entire file into in-memory cache on first call; returns cache on subsequent calls. |
  | `get` | `fun get(lessonId: String, languageId: String): LessonMasteryState?` | `LessonMasteryState?` | Returns state for a specific lesson, or null if never tracked. |
  | `save` | `fun save(state: LessonMasteryState)` | Unit | Upserts the state into cache and persists full file. |
  | `recordCardShow` | `fun recordCardShow(lessonId: String, languageId: String, cardId: String)` | Unit | Records that a card was shown. Increments `uniqueCardShows` if the card is new. Updates `totalCardShows`. Computes new `intervalStepIndex` based on `SpacedRepetitionConfig`. Updates `lastShowDateMs`. Does NOT affect `completedAtMs`. |
  | `markCardsShownForProgress` | `fun markCardsShownForProgress(lessonId: String, languageId: String, cardIds: Collection<String>)` | Unit | Adds card IDs to `shownCardIds` without incrementing mastery counters. Used for progress tracking only (e.g., marking cards as "seen" for daily practice without awarding mastery). |
  | `markLessonCompleted` | `fun markLessonCompleted(lessonId: String, languageId: String)` | Unit | Sets `completedAtMs` to current time. No-op if already completed. |
  | `getOrCreate` | `fun getOrCreate(lessonId: String, languageId: String): LessonMasteryState` | `LessonMasteryState` | Returns existing state or creates a fresh zero-valued state. |
  | `clear` | `fun clear()` | Unit | Wipes cache and deletes the file. |
  | `clearLanguage` | `fun clearLanguage(languageId: String)` | Unit | Removes all mastery data for a given language from cache and persists. |

- **Write semantics**: Full rewrite via `AtomicFileWriter` on every `save()` call. The entire cache is serialized to YAML.

- **Read semantics**: Lazy-cached. First call to `loadAll()` reads the file and populates `cache`. Subsequent calls return the cache directly. Cache is never invalidated (assumes single-process access).

- **Error handling**: On parse error, cache is reset to empty. The file is not deleted on error — next restart will attempt to read it again.

- **Invariants**:
  - `uniqueCardShows` <= `totalCardShows` (unique is a subset of total).
  - `uniqueCardShows` equals `shownCardIds.size` (incremented only when card ID is new).
  - `intervalStepIndex` is always within `[0, INTERVAL_LADDER_DAYS.size - 1]` (clamped by `SpacedRepetitionConfig`).
  - `completedAtMs` is set at most once (no-op on subsequent calls).
  - A `LessonMasteryState` always has a non-null `lessonId` and `languageId`.

- **Dependencies**:
  - `SpacedRepetitionConfig` (for interval step calculation in `recordCardShow`)
  - `AtomicFileWriter`

---

## 2.3 ProgressStore

- **Purpose**: Persists the current training session state so the user can resume exactly where they left off after app restart or process death.
- **File location**: `grammarmate/progress.yaml`
- **Data format**: YAML, schema version 1:
  ```yaml
  schemaVersion: 1
  data:
    languageId: "en"
    mode: "LESSON"
    lessonId: "lesson_abc123"
    currentIndex: 5
    correctCount: 3
    incorrectCount: 1
    incorrectAttemptsForCard: 0
    activeTimeMs: 120000
    state: "PAUSED"
    bossLessonRewards: { lesson1: "BRONZE" }
    bossMegaReward: "SILVER"
    bossMegaRewards: { lesson2: "GOLD" }
    voiceActiveMs: 45000
    voiceWordCount: 23
    hintCount: 2
    eliteStepIndex: 0
    eliteBestSpeeds: [1.5, 2.1]
    currentScreen: "HOME"
    activePackId: "IT_VERB_GROUPS_ALL"
    dailyLevel: 3
    dailyTaskIndex: 7
    dailyCursor:
      sentenceOffset: 20
      currentLessonIndex: 1
      lastSessionHash: 12345
      firstSessionDate: "2025-05-12"
      firstSessionSentenceCardIds: ["lesson1_0", "lesson1_1"]
      firstSessionVerbCardIds: ["verb_0", "verb_1"]
  ```

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `load` | `fun load(): TrainingProgress` | `TrainingProgress` | Reads and parses the file. Returns default `TrainingProgress()` if file missing or unparseable. Includes migration logic: if old single `bossMegaReward` exists but `bossMegaRewards` is empty, migrates using `lessonId` as key. |
  | `save` | `fun save(progress: TrainingProgress)` | Unit | Serializes the full `TrainingProgress` to YAML via `AtomicFileWriter`. |
  | `clear` | `fun clear()` | Unit | Deletes the file. |

- **Write semantics**: Full rewrite via `AtomicFileWriter` on every `save()`.

- **Read semantics**: No caching. Every `load()` reads from disk. This is intentional because the store is called infrequently (app startup, session pause/resume).

- **Error handling**: Missing file returns defaults. Unparseable YAML returns defaults. Enum parsing failures fall back to default values (`LESSON` for mode, `PAUSED` for state).

- **Invariants**:
  - `currentIndex` >= 0.
  - `correctCount` >= 0, `incorrectCount` >= 0.
  - `state` is always a valid `SessionState` enum name.
  - `mode` is always a valid `TrainingMode` enum name.
  - `dailyCursor` is always non-null (defaults to `DailyCursorState()`).
  - `bossMegaRewards` migration: if `bossMegaReward` is non-null and `bossMegaRewards` is empty, the old value is migrated into the map using `lessonId` as key.

- **Dependencies**: `AtomicFileWriter` only.

---

## 2.4 DrillProgressStore

- **Purpose**: Stores the card index position for in-progress drill sessions, allowing users to resume a drill from where they left off.
- **File location**: `grammarmate/drill_progress_{lessonId}.yaml` (one file per lesson)
- **Data format**: YAML, no schema version:
  ```yaml
  lessonId: "lesson_abc123"
  cardIndex: 15
  ```

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `getDrillProgress` | `fun getDrillProgress(lessonId: String): Int` | Int | Returns saved card index, or -1 if no progress exists or file is corrupt. |
  | `saveDrillProgress` | `fun saveDrillProgress(lessonId: String, cardIndex: Int)` | Unit | Writes the card index to the lesson-specific file via `AtomicFileWriter`. Creates `baseDir` if needed. |
  | `hasProgress` | `fun hasProgress(lessonId: String): Boolean` | Boolean | Returns `getDrillProgress(lessonId) > 0`. |
  | `clearDrillProgress` | `fun clearDrillProgress(lessonId: String)` | Unit | Deletes the lesson-specific file. |

- **Write semantics**: Full rewrite via `AtomicFileWriter`.

- **Read semantics**: No caching. Reads from disk every time. Returns -1 on any error.

- **Error handling**: Missing file returns -1. Parse error returns -1. No crash on corrupt data.

- **Invariants**:
  - A progress file exists only if the user has started and not completed a drill.
  - `cardIndex` is positive when stored (store returns -1 for "no progress").
  - Files are per-lesson, so different lessons have independent drill progress.

- **Dependencies**: `AtomicFileWriter`.

---

## 2.5 VerbDrillStore

- **Purpose**: Tracks verb conjugation drill progress per group+tense combination within a pack. Records which cards have been shown (ever and today) to ensure variety across sessions.
- **File location**:
  - Pack-scoped: `grammarmate/drills/{packId}/verb_drill_progress.yaml`
  - Legacy (packId == null): `grammarmate/verb_drill_progress.yaml`
- **Data format**: YAML, schema version 1:
  ```yaml
  schemaVersion: 1
  data:
    "{group}_{tense}":
      group: "are"
      tense: "Presente"
      totalCards: 42
      everShownCardIds: ["are_Presente_0", "are_Presente_1"]
      todayShownCardIds: ["are_Presente_0"]
      lastDate: "2025-05-12"
  ```

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `loadProgress` | `fun loadProgress(): Map<String, VerbDrillComboProgress>` | Map of comboKey -> progress | Reads from file. On load, if `lastDate` is not today, `todayShownCardIds` is cleared (stale data). Returns empty map if file missing. |
  | `saveProgress` | `fun saveProgress(progress: Map<String, VerbDrillComboProgress>)` | Unit | Writes the full map via `AtomicFileWriter`. |
  | `getComboProgress` | `fun getComboProgress(key: String): VerbDrillComboProgress?` | `VerbDrillComboProgress?` | Returns progress for a specific group+tense combo key. Delegates to `loadProgress()`. |
  | `upsertComboProgress` | `fun upsertComboProgress(key: String, progress: VerbDrillComboProgress)` | Unit | Load-modify-save: loads all, upserts the entry, writes all back. |
  | `loadAllCardsForPack` | `fun loadAllCardsForPack(targetPackId: String, languageId: String): List<VerbDrillCard>` | List of `VerbDrillCard` | Reads CSV files from `drills/{targetPackId}/verb_drill/`, parses them via `VerbDrillCsvParser`, returns all cards. |
  | `getCardsForTenses` | `fun getCardsForTenses(packId: String, languageId: String, tenses: List<String>): List<VerbDrillCard>` | List of `VerbDrillCard` | Filters `loadAllCardsForPack` results to only cards whose tense is in the provided list. Returns empty if tenses list is empty. |

- **Write semantics**: Full rewrite via `AtomicFileWriter`.

- **Read semantics**: No caching. Every `loadProgress()` reads and parses the file. `todayShownCardIds` is auto-cleared if `lastDate` is not today (date rollover logic).

- **Error handling**: Missing file returns empty map. Parse errors result in empty map return.

- **Invariants**:
  - Combo key format: `"{group}_{tense}"`.
  - `todayShownCardIds` is always a subset of `everShownCardIds`.
  - `todayShownCardIds` is automatically cleared when `lastDate` does not match today's date.
  - `totalCards` is the total number of available cards for the combo (set externally, not computed from shown IDs).
  - File path is scoped by `packId` — different packs have completely independent verb drill progress.

- **Dependencies**:
  - `VerbDrillCsvParser` (for CSV parsing in `loadAllCardsForPack`)
  - `AtomicFileWriter`

---

## 2.6 WordMasteryStore

- **Purpose**: Tracks per-word mastery state for the Anki-like vocab drill. Records spaced repetition progress (interval step, correct/incorrect counts, review dates, learned status) for individual vocabulary words.
- **File location**:
  - Pack-scoped: `grammarmate/drills/{packId}/word_mastery.yaml`
  - Legacy (packId == null): `grammarmate/word_mastery.yaml`
- **Data format**: YAML, schema version 1:
  ```yaml
  schemaVersion: 1
  data:
    "{wordId}":
      intervalStepIndex: 3
      correctCount: 5
      incorrectCount: 1
      lastReviewDateMs: 1715500800000
      nextReviewDateMs: 1716105600000
      isLearned: false
  ```

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `loadAll` | `fun loadAll(): Map<String, WordMasteryState>` | Map of wordId -> state | Reads entire file. Returns empty map if missing or corrupt. |
  | `saveAll` | `fun saveAll(mastery: Map<String, WordMasteryState>)` | Unit | Writes full map via `AtomicFileWriter`. |
  | `getMastery` | `fun getMastery(wordId: String): WordMasteryState?` | `WordMasteryState?` | Returns state for a single word, or null if never reviewed. |
  | `upsertMastery` | `fun upsertMastery(state: WordMasteryState)` | Unit | Load-modify-save: inserts or replaces a single word's mastery state. |
  | `getDueWords` | `fun getDueWords(): Set<String>` | Set of String | Returns word IDs where `nextReviewDateMs <= now` OR `lastReviewDateMs == 0` (never reviewed). |
  | `getMasteredCount` | `fun getMasteredCount(pos: String? = null): Int` | Int | Counts words with `isLearned == true`. Optionally filters by POS prefix extracted from word ID (e.g., `"nouns"` matches `"nouns_casa"`). |
  | `getMasteredByPos` | `fun getMasteredByPos(): Map<String, Int>` | Map of POS -> count | Groups mastered words by POS prefix and counts them. |

- **Write semantics**: Full rewrite via `AtomicFileWriter`.

- **Read semantics**: No caching. Every method reads from disk. This means `upsertMastery` does a full load + full save on every call.

- **Error handling**: Missing file returns empty map. Parse errors return empty map.

- **Invariants**:
  - `intervalStepIndex` is in range [0, 9] (index into `INTERVAL_LADDER_DAYS` which has 10 entries).
  - `isLearned` is set to true when the word reaches the last interval step (step 9).
  - Word IDs follow the pattern `{pos}_{word}` (e.g., `nouns_casa`, `verbs_essere`) for POS extraction to work.
  - `nextReviewDateMs` is computed externally as `lastReviewDateMs + INTERVAL_LADDER_DAYS[step] * DAY_MS`.
  - File path is scoped by `packId`.

- **Dependencies**: `AtomicFileWriter`.

---

## 2.7 ProfileStore

- **Purpose**: Stores user profile information (currently only the user name).
- **File location**: `grammarmate/profile.yaml`
- **Data format**: YAML, no schema version:
  ```yaml
  userName: "GrammarMateUser"
  ```

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `load` | `fun load(): UserProfile` | `UserProfile` | Returns stored profile or default `UserProfile(userName = "GrammarMateUser")` if missing/error. |
  | `save` | `fun save(profile: UserProfile)` | Unit | Writes profile via `AtomicFileWriter`. Creates `baseDir` if needed. |
  | `clear` | `fun clear()` | Unit | Deletes the file. |

- **Write semantics**: Full rewrite via `AtomicFileWriter`.

- **Read semantics**: No caching. Reads from disk every time.

- **Error handling**: Missing file returns default. Parse error returns default and prints stack trace.

- **Invariants**:
  - `userName` is always non-null and non-blank (defaults to "GrammarMateUser").

- **Dependencies**: `AtomicFileWriter`.

---

## 2.8 StreakStore

- **Purpose**: Tracks daily practice streaks per language — consecutive days of practice, longest streak, total sub-lessons completed.
- **File location**: `grammarmate/streak_{languageId}.yaml` (one file per language)
- **Data format**: YAML, no schema version:
  ```yaml
  languageId: "en"
  currentStreak: 5
  longestStreak: 12
  lastCompletionDateMs: 1715500800000
  totalSubLessonsCompleted: 47
  ```

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `save` | `fun save(data: StreakData)` | Unit | Writes streak data to the language-specific file via `AtomicFileWriter`. Creates `baseDir` if needed. |
  | `load` | `fun load(languageId: String): StreakData` | `StreakData` | Reads from file. Returns default `StreakData(languageId)` if missing or corrupt. |
  | `recordSubLessonCompletion` | `fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean>` | Pair of (updated data, isNewStreak flag) | Core streak logic. Loads current data, checks if this is same-day (no change), consecutive day (increment), or gap (reset to 1). Updates `longestStreak` if new record. Increments `totalSubLessonsCompleted`. Saves and returns. |
  | `getCurrentStreak` | `fun getCurrentStreak(languageId: String): StreakData` | `StreakData` | Loads current data and checks if more than 1 day has passed since last completion. If so, resets `currentStreak` to 0 and saves. Returns updated data. |

- **Write semantics**: Full rewrite via `AtomicFileWriter`.

- **Read semantics**: No caching. Reads from disk every time.

- **Error handling**: Missing file returns default `StreakData`. Parse error returns default.

- **Invariants**:
  - `currentStreak` >= 0.
  - `longestStreak` >= `currentStreak`.
  - `totalSubLessonsCompleted` >= 0.
  - Same-day completions do not increment the streak counter but do increment `totalSubLessonsCompleted`.
  - A gap of more than 1 day resets `currentStreak` to 0 (via `getCurrentStreak`) or 1 (via `recordSubLessonCompletion`).
  - Date comparison uses `Calendar.get(YEAR)` and `Calendar.get(DAY_OF_YEAR)` for same-day and consecutive-day checks.

- **Dependencies**: `AtomicFileWriter`.

---

## 2.9 HiddenCardStore

- **Purpose**: Maintains a set of card IDs that the user has hidden (e.g., flagged as unwanted or inappropriate). Hidden cards are excluded from training sessions.
- **File location**: `grammarmate/hidden_cards.yaml`
- **Data format**: YAML, schema version 1:
  ```yaml
  schemaVersion: 1
  hiddenCardIds:
    - "lesson1_42"
    - "lesson2_7"
  ```

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `hideCard` | `fun hideCard(cardId: String)` | Unit | Adds card ID to the hidden set and persists. |
  | `unhideCard` | `fun unhideCard(cardId: String)` | Unit | Removes card ID from the hidden set and persists. |
  | `isHidden` | `fun isHidden(cardId: String): Boolean` | Boolean | Returns true if the card ID is in the hidden set. |
  | `getHiddenCardIds` | `fun getHiddenCardIds(): Set<String>` | Set of String | Returns a copy of all hidden card IDs. |
  | `clearAll` | `fun clearAll()` | Unit | Clears the hidden set and persists. |

- **Write semantics**: Full rewrite via `AtomicFileWriter` on every mutation.

- **Read semantics**: Lazy-cached. First call to `ensureLoaded()` reads the file. Cache persists for the lifetime of the store instance.

- **Error handling**: Missing file returns empty set. Parse error resets to empty set.

- **Invariants**:
  - All card IDs in the set are non-null, non-blank strings.
  - The set contains no duplicates (enforced by `MutableSet`).

- **Dependencies**: `AtomicFileWriter`.

---

## 2.10 BadSentenceStore

- **Purpose**: Stores user-reported bad sentences (incorrect translations, errors in content) organized by pack. Supports migration from legacy flat format to pack-scoped format. Provides export functionality for content review. Each entry includes a `mode` field identifying the training context where the card was flagged.
- **File location**: `grammarmate/bad_sentences.yaml`
- **Data format**: YAML, schema version 2 (current):
  ```yaml
  schemaVersion: 2
  packs:
    "{packId}":
      items:
        - cardId: "lesson1_42"
          languageId: "en"
          sentence: "The cat sits on mat"
          translation: "Кот сидит на коврике"
          mode: "training"
          addedAtMs: 1715500800000
  ```

  Legacy schema version 1 (migrated on load):
  ```yaml
  schemaVersion: 1
  items:
    - cardId: "..."
      languageId: "..."
      sentence: "..."
      translation: "..."
      mode: "..."
      addedAtMs: ...
  ```

  Old drill file (also migrated): `grammarmate/drill_bad_sentences.yaml` (same schema v1 format)

- **BadSentenceEntry data class**:

  | Field | Type | Description |
  |-------|------|-------------|
  | `cardId` | `String` | Unique card identifier |
  | `languageId` | `String` | Language ID (e.g., "it", "en") |
  | `sentence` | `String` | Source text (Russian prompt for translation/verb cards, meaning or word for vocab) |
  | `translation` | `String` | Target text (accepted answer(s) for translation, Italian word for vocab) |
  | `mode` | `String` | Training context where the card was flagged. Default: `"training"` |
  | `addedAtMs` | `Long` | Timestamp when the entry was created |

- **Mode field values**:

  | Mode | Source | Description |
  |------|--------|-------------|
  | `"training"` | Regular lesson training | Sentence translation cards in LESSON/BOSS/DRILL modes |
  | `"verb_drill"` | Standalone verb drill | Verb conjugation cards in `VerbDrillViewModel` |
  | `"vocab_drill"` | Standalone vocab drill | Vocabulary flashcard words in `VocabDrillViewModel` |
  | `"daily_translate"` | Daily practice Block 1 | Sentence translation cards in daily practice |
  | `"daily_vocab"` | Daily practice Block 2 | Vocabulary flashcard words in daily practice |
  | `"daily_verb"` | Daily practice Block 3 | Verb conjugation cards in daily practice |

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `migrateIfNeeded` | `fun migrateIfNeeded(lessonStore: LessonStore)` | Unit | One-time migration from schema v1 (flat) + old drill file to schema v2 (pack-scoped). Groups legacy entries by resolving `packId` from `lessonId` via `LessonStore.getPackIdForLesson()`. Legacy drill entries default to `mode = "verb_drill"`. Deletes old drill file after migration. |
  | `addBadSentence` | `fun addBadSentence(packId: String, entry: BadSentenceEntry)` | Unit | Adds a bad sentence entry to the specified pack. No-op if `cardId` already exists in that pack. |
  | `addBadSentence` | `fun addBadSentence(packId: String, cardId: String, languageId: String, sentence: String, translation: String, mode: String = "training")` | Unit | Convenience overload that wraps parameters in a `BadSentenceEntry`. Mode defaults to `"training"`. |
  | `removeBadSentence` | `fun removeBadSentence(packId: String, cardId: String)` | Unit | Removes the entry with matching `cardId` from the specified pack. Removes the pack key if it becomes empty. |
  | `getBadSentences` | `fun getBadSentences(packId: String): List<BadSentenceEntry>` | List of entries | Returns all bad sentences for a pack. |
  | `isBadSentence` | `fun isBadSentence(packId: String, cardId: String): Boolean` | Boolean | Checks if a specific card is marked as bad within a pack. |
  | `isBadSentence` | `fun isBadSentence(cardId: String): Boolean` | Boolean | Backward-compatible: checks across all packs for the given cardId. Used during migration period. |
  | `getBadSentenceCount` | `fun getBadSentenceCount(packId: String): Int` | Int | Returns count of bad sentences for a pack. |
  | `getTotalBadSentenceCount` | `fun getTotalBadSentenceCount(): Int` | Int | Returns total count across all packs. |
  | `exportToTextFile` | `fun exportToTextFile(packId: String): File` | File | Per-pack export to `Downloads/BaseGrammy/bad_sentences_{packId}.txt`. Each entry: `ID: ...\nSource: ...\nTarget: ...\nLanguage: ...\nMode: ...\n---`. Uses `AtomicFileWriter`. |
  | `exportUnified` | `fun exportUnified(): File` | File | Unified export to `Downloads/BaseGrammy/bad_sentences_all.txt`. All entries grouped by language, then pack, then mode. Includes generation timestamp and total count header. Uses `AtomicFileWriter`. |
  | `clearPack` | `fun clearPack(packId: String)` | Unit | Removes all bad sentences for a pack. |
  | `clearAll` | `fun clearAll()` | Unit | Removes all bad sentences for all packs. |

- **Write semantics**: Full rewrite via `AtomicFileWriter`. Packs are sorted by key for deterministic output. Entries within each pack are persisted in insertion order.

- **Read semantics**: Lazy-cached via `ensureLoaded()`. Cache persists for the lifetime of the store instance.

- **Error handling**: Missing file returns empty. Parse error resets to empty. Old drill file parse errors are silently ignored.

- **Invariants**:
  - Schema v2 is always written on persist (even if loaded from v1).
  - Within a pack, `cardId` values are unique (duplicate `addBadSentence` calls are no-ops).
  - `addedAtMs` defaults to `System.currentTimeMillis()` if not provided.
  - `mode` defaults to `"training"` if not provided (backward compat with legacy entries).
  - `__legacy__` pack key is used transiently during v1->v2 migration and is removed before persist.
  - `__unknown__` pack key is used for legacy entries whose pack cannot be resolved.
  - `__vocab_drill__` pack key is used by `VocabDrillViewModel` when no active pack ID is available.

- **Dependencies**:
  - `LessonStore` (only for `migrateIfNeeded` — resolves `packId` from `lessonId`)
  - `AtomicFileWriter`

---

## 2.11 VocabProgressStore

- **Purpose**: Tracks vocab sprint progress (which entry indices have been completed in the current sprint) and per-entry spaced repetition state (correct/incorrect history, interval step) for vocabulary flashcard review.
- **File location**: `grammarmate/vocab_progress.yaml`
- **Data format**: YAML, schema version 1:
  ```yaml
  schemaVersion: 1
  data:
    {languageId}:
      {lessonId}:
        completedIndices: [0, 1, 5]
        entries:
          "{entryId}":
            lastCorrectMs: 1715500800000
            lastIncorrectMs: 0
            intervalStep: 2
  ```

  Spaced repetition intervals (in days): `[1, 3, 7, 14, 30]`

- **Public API**:

  | Method | Signature | Return | Behavior |
  |--------|-----------|--------|----------|
  | `loadAll` | `fun loadAll(): Map<String, Map<String, LessonVocabProgress>>` | Nested map: languageId -> lessonId -> progress | Loads entire file into cache on first call. Returns cache on subsequent calls. |
  | `get` | `fun get(lessonId: String, languageId: String): LessonVocabProgress` | `LessonVocabProgress` | Returns progress for a specific lesson, or default (empty indices and entries) if not tracked. |
  | `saveCompletedIndices` | `fun saveCompletedIndices(lessonId: String, languageId: String, indices: Set<Int>)` | Unit | Replaces the completed indices set for a lesson. |
  | `addCompletedIndex` | `fun addCompletedIndex(lessonId: String, languageId: String, index: Int)` | Unit | Adds a single index to the completed set. |
  | `clearSprintProgress` | `fun clearSprintProgress(lessonId: String, languageId: String)` | Unit | Resets completed indices to empty set for a lesson. Retains entry SRS states. |
  | `recordCorrect` | `fun recordCorrect(entryId: String, lessonId: String, languageId: String)` | Unit | Records a correct answer. Advances `intervalStep` by 1 (clamped to max index of `INTERVALS_DAYS`). Updates `lastCorrectMs`. |
  | `recordIncorrect` | `fun recordIncorrect(entryId: String, lessonId: String, languageId: String)` | Unit | Records an incorrect answer. Resets `intervalStep` to 0. Updates `lastIncorrectMs`. |
  | `isDueForReview` | `fun isDueForReview(entryId: String, lessonId: String, languageId: String): Boolean` | Boolean | Returns true if the entry has been reviewed before and is past its review interval. Returns false for never-reviewed entries. |
  | `sortEntriesForSprint` | `fun sortEntriesForSprint(entries: List<VocabEntry>, lessonId: String, languageId: String): List<VocabEntry>` | Sorted list | Anki-like prioritization: overdue words first, then new words (never answered correctly), then not-yet-due words. Each group is shuffled internally for variety. |
  | `clear` | `fun clear()` | Unit | Wipes cache and deletes the file. |

- **Write semantics**: Full rewrite via `AtomicFileWriter` on every mutation.

- **Read semantics**: Lazy-cached. First call to `loadAll()` reads and caches. Subsequent calls return cache.

- **Error handling**: Missing file returns empty cache. Parse error resets cache to empty.

- **Invariants**:
  - `completedIndices` contains only non-negative integers.
  - `intervalStep` is in range `[0, INTERVALS_DAYS.lastIndex]` (0-4 for the 5-element ladder `[1, 3, 7, 14, 30]`).
  - Incorrect answers always reset `intervalStep` to 0.
  - Correct answers advance `intervalStep` by 1, clamped at max.
  - `isDueForReview` returns false for entries that have never been answered correctly (`lastCorrectMs <= 0`).

- **Dependencies**: `AtomicFileWriter`.

---

## 2.12 Supporting Infrastructure

### YamlListStore

- **Purpose**: Generic YAML-backed list storage used by `LessonStore` for all index/registry files.
- **File location**: Configurable (passed via constructor).
- **Data format**:
  ```yaml
  schemaVersion: 1
  items:
    - key1: value1
      key2: value2
  ```
- **Public API**:
  - `read(): List<Map<String, Any>>` — Reads and returns items list. Supports both wrapped (`{schemaVersion, items}`) and bare list formats.
  - `write(items: List<Map<String, Any>>)` — Writes items wrapped in schema container via `AtomicFileWriter`.

### AtomicFileWriter

- **Purpose**: Ensures atomic file writes across all stores. Prevents data corruption from crashes during write.
- **Mechanism**: Write to `.tmp` file -> `fd.sync()` -> delete original -> rename `.tmp` to target. On Windows, retries temp file deletion up to 10 times with 10ms sleep between attempts (handles file locking).

---

## 2.13 Cross-Store Dependencies

### Dependency Graph

```
LessonStore
  ├── uses YamlListStore (languages, packs, stories, vocab indexes)
  ├── uses AtomicFileWriter (all file writes)
  ├── uses CsvParser (lesson CSV parsing)
  ├── uses VocabCsvParser (vocab CSV parsing)
  ├── uses ItalianDrillVocabParser (Italian drill vocab from assets)
  ├── uses StoryQuizParser (story JSON parsing)
  └── uses LessonPackManifest (manifest parsing)

MasteryStore
  ├── uses SpacedRepetitionConfig (interval step calculation)
  └── uses AtomicFileWriter

ProgressStore
  └── uses AtomicFileWriter

DrillProgressStore
  └── uses AtomicFileWriter

VerbDrillStore
  ├── uses VerbDrillCsvParser (CSV parsing)
  └── uses AtomicFileWriter

WordMasteryStore
  └── uses AtomicFileWriter

ProfileStore
  └── uses AtomicFileWriter

StreakStore
  └── uses AtomicFileWriter

HiddenCardStore
  └── uses AtomicFileWriter

BadSentenceStore
  ├── uses LessonStore (only during migration: getPackIdForLesson)
  └── uses AtomicFileWriter

VocabProgressStore
  └── uses AtomicFileWriter
```

### Data Flow

```
User imports pack ZIP
    │
    ▼
LessonStore.importPackFromStream()
    ├── Creates pack directory in grammarmate/packs/{packId}/
    ├── Imports lesson CSVs -> grammarmate/lessons/{lang}/
    ├── Imports drill CSVs -> grammarmate/drills/{packId}/verb_drill/ and vocab_drill/
    ├── Imports stories -> grammarmate/stories/
    ├── Imports vocab -> grammarmate/vocab/{lang}/
    └── Updates packs.yaml registry
         │
         ▼
    VerbDrillStore loads CSVs from grammarmate/drills/{packId}/verb_drill/
    WordMasteryStore tracks progress in grammarmate/drills/{packId}/word_mastery.yaml
    LessonStore.hasVerbDrill() / hasVocabDrill() checks determine UI tile visibility

User completes training session
    │
    ▼
MasteryStore.recordCardShow() -> updates mastery.yaml
StreakStore.recordSubLessonCompletion() -> updates streak_{lang}.yaml
ProgressStore.save() -> persists session state to progress.yaml

User reports bad sentence
    │
    ▼
BadSentenceStore.addBadSentence(packId, ...) -> updates bad_sentences.yaml
(BadSentenceStore.migrateIfNeeded called at init, depends on LessonStore)

User hides a card
    │
    ▼
HiddenCardStore.hideCard() -> updates hidden_cards.yaml
(TrainingViewModel filters cards using HiddenCardStore.isHidden())
```

### Potential Consistency Issues

1. **Pack removal vs. orphaned progress**: When `LessonStore.removeInstalledPackData()` is called, it deletes the pack directory and drill CSVs, but does NOT clean up `VerbDrillStore`, `WordMasteryStore`, or `DrillProgressStore` progress files for that pack. The progress files in `drills/{packId}/` ARE deleted by `deletePackDrills()`, but any `VerbDrillStore` or `WordMasteryStore` instances holding that `packId` will reference a now-deleted file. These stores should be reconstructed with the new pack context.

2. **MasteryStore.cardId vs. LessonStore.lessonId coupling**: Card IDs are generated as `{lessonId}_{index}` during import. If a lesson is re-imported with different content, old `MasteryStore` entries reference stale card IDs. There is no cascade delete from lesson removal to mastery cleanup.

3. **ProgressStore.activePackId and VerbDrillStore.packId**: The `ProgressStore` saves `activePackId`, but `VerbDrillStore` and `WordMasteryStore` take `packId` as a constructor parameter. If the user switches packs, new store instances must be created. Stale instances will read/write to the wrong pack's progress file.

4. **StreakStore date boundary**: `getCurrentStreak()` and `recordSubLessonCompletion()` use `Calendar` with the device's local timezone. If the device timezone changes between sessions, streak calculations could be affected.

5. **VocabProgressStore interval ladder vs. SpacedRepetitionConfig**: `VocabProgressStore` uses its own `INTERVALS_DAYS = [1, 3, 7, 14, 30]` ladder, which is different from `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS = [1, 2, 4, 7, 10, 14, 20, 28, 42, 56]`. These are intentionally separate (vocab has a shorter 5-step ladder; lesson mastery uses a 10-step ladder), but developers must be aware of this divergence.

6. **No transactional writes across stores**: When a training session ends, `MasteryStore`, `StreakStore`, `ProgressStore`, and possibly `VerbDrillStore`/`WordMasteryStore`/`VocabProgressStore` are all updated independently. A crash mid-sequence can leave the stores in an inconsistent state (e.g., streak incremented but mastery not updated). This is mitigated by `AtomicFileWriter` ensuring each individual store's file is never corrupt, but cross-store atomicity is not guaranteed.

7. **BadSentenceStore migration idempotency**: `migrateIfNeeded()` is designed to be called repeatedly without harm (legacy entries are consumed and removed), but if the app crashes between removing legacy data and persisting the v2 data, legacy entries could be lost. The old drill file is deleted only after successful persist.
