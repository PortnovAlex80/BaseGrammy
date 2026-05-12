# 15. Lesson Content & Pack System — Specification

## 15.1 Lesson Pack Format

### 15.1.1 ZIP Structure

A lesson pack is a standard ZIP archive. Its root directory must contain `manifest.json` and all CSV/JSON data files referenced by the manifest. Subdirectories are not used; all files reside at the archive root.

```
lesson_pack.zip
  manifest.json                          (required)
  lesson_01.csv                          (lesson content, required per manifest entry)
  lesson_01_drill.csv                    (drill content, optional per manifest entry)
  lesson_02.csv
  ...
  it_verb_groups_all.csv                 (verb drill, referenced by verbDrill section)
  it_drill_nouns.csv                     (vocab drill, referenced by vocabDrill section)
  it_drill_verbs.csv
  ...
  story_L01_CHECK_IN.json               (optional story quiz)
  story_L01_CHECK_OUT.json              (optional story quiz)
  vocab_L01_PRESENT_SIMPLE.csv          (optional per-lesson vocabulary)
```

File naming within the ZIP is arbitrary except:
- `manifest.json` is the reserved manifest file name (case-insensitive match excluded from story import).
- Vocab CSV files must be named `vocab_<lessonId>.csv` for automatic association with a lesson.

### 15.1.2 manifest.json Schema

```json
{
  "schemaVersion": 1,
  "packId": "EN_WORD_ORDER_A1",
  "packVersion": "v2",
  "language": "en",
  "displayName": "Word Order (A1)",
  "lessons": [ ... ],
  "verbDrill": { "files": [ ... ] },
  "vocabDrill": { "files": [ ... ] }
}
```

#### Top-level fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `schemaVersion` | int | yes | Must be `1`. Any other value causes import rejection. |
| `packId` | string | yes | Unique identifier for the pack. Trimmed; must not be blank. Used as filesystem directory name and progress scoping key. |
| `packVersion` | string | yes | Version string for update detection. Trimmed; must not be blank. Compared as plain string equality. |
| `language` | string | yes | Target language code (e.g. `"en"`, `"it"`). Lowercased on import. Creates the language in `languages.yaml` if not present. |
| `displayName` | string | no | Human-readable pack name shown in the UI. Falls back to `packId` if absent. |
| `lessons` | array | conditional | Array of lesson entry objects. Required unless `verbDrill` or `vocabDrill` sections are present. A manifest must have at least one lesson or at least one drill section. |
| `verbDrill` | object | no | Section declaring verb drill files. See 15.4. |
| `vocabDrill` | object | no | Section declaring vocab drill files. See 15.4. |

#### Lesson entry fields

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `lessonId` | string | yes | — | Unique lesson identifier within the pack. Trimmed; must not be blank. Becomes the lesson's `id` in the lesson index. |
| `file` | string | yes | — | CSV filename within the ZIP. Trimmed; must not be blank. The file must exist in the archive. |
| `order` | int | no | array index + 1 | Display/ordering position. Used for sorting lessons. |
| `title` | string | no | null | Lesson title override. If omitted or blank, the title is parsed from the first line of the CSV. |
| `drillFile` | string | no | null | Optional drill CSV file associated with this lesson. If specified, the file must exist in the archive. |
| `type` | string | no | `"standard"` | Lesson type. Currently recognized: `"standard"`, `"verb_drill"`. Lessons with `type: "verb_drill"` are excluded from standard lesson import and instead routed to the verb drill subsystem (legacy per-lesson verb drill path). |
| `tenses` | array of string | no | `[]` | Tense tags associated with this lesson. Used by `LessonLadderCalculator` to determine cumulative tenses available at each lesson level. |

#### verbDrill section

```json
"verbDrill": {
  "files": ["it_verb_groups_all.csv"]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `files` | array of string | yes | List of CSV filenames containing verb conjugation drill data. Empty array is treated as absent (the section is ignored). |

#### vocabDrill section

```json
"vocabDrill": {
  "files": [
    "it_drill_nouns.csv",
    "it_drill_verbs.csv",
    "it_drill_adjectives.csv",
    "it_drill_adverbs.csv",
    "it_drill_numbers.csv",
    "it_drill_pronouns.csv"
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `files` | array of string | yes | List of CSV filenames containing vocabulary drill data. Each file may have a different column schema depending on the part of speech. See 15.5.5. |

### 15.1.3 Versioning Strategy

- **`schemaVersion`**: Defines the manifest schema. Currently only version `1` is supported. Future schema changes (e.g., new required fields) would bump this number and the parser would reject unknown versions. Backward-compatible additions (new optional fields) do not require a schema bump.
- **`packVersion`**: A free-form string compared by exact equality. Used by `updateDefaultPacksIfNeeded()` to detect when a bundled default pack has been updated. When the installed pack version differs from the asset version, the asset version is re-imported, replacing the old data.
- **Version update flow**: Re-importing a pack with the same `packId` removes the old pack directory and lesson index entries, then installs the new version. This is an in-place replacement, not a merge.

### 15.1.4 Validation Rules (App-Level)

The app enforces these validation rules during `LessonPackManifest.fromJson()`:

1. `schemaVersion` must equal `1` -- any other value throws an error.
2. `packId`, `packVersion`, `language` must all be non-blank after trimming.
3. Each lesson entry must have non-blank `lessonId` and `file`.
4. If the manifest has no standard lessons (all have `type: "verb_drill"`) and no `verbDrill` or `vocabDrill` sections, import fails with the error "Manifest has no lessons and no drill sections".
5. Referenced CSV files must exist in the ZIP archive (checked during `importPackFromTempDir`).

The Python pack validator (`pack_validator.py`) performs additional checks. See section 15.7.

---

## 15.2 Default Packs

### 15.2.1 EN_WORD_ORDER_A1.zip

**Purpose**: English word order and simple tenses lesson pack for A1 level.

**Manifest**:
```json
{
  "schemaVersion": 1,
  "packId": "EN_WORD_ORDER_A1",
  "packVersion": "v2",
  "language": "en",
  "displayName": "Word Order (A1)",
  "lessons": [
    {
      "lessonId": "L01_PRESENT_SIMPLE",
      "order": 1,
      "title": "Word Order -- Simple Tenses",
      "file": "lesson_01.csv",
      "drillFile": "lesson_01_drill.csv"
    }
  ]
}
```

**Contents**:
- `lesson_01.csv` -- Main lesson: Russian prompts with English translations. Standard 2-column semicolon-delimited format. Contains sentences practicing present simple, past simple, and future simple patterns.
- `lesson_01_drill.csv` -- Drill lesson: Same grammatical patterns with varied sentences for the Reserve pool. Uses the verb "say hello" as a carrier phrase across multiple tenses.
- `story_L01_CHECK_IN.json` -- Story quiz for the CHECK_IN phase.
- `story_L01_CHECK_OUT.json` -- Story quiz for the CHECK_OUT phase.
- `vocab_L01_PRESENT_SIMPLE.csv` -- Vocabulary pairs (Russian;English) for the lesson.
- No `verbDrill` or `vocabDrill` sections (English pack does not include drill subsystem content).

### 15.2.2 IT_VERB_GROUPS_ALL.zip

**Purpose**: Italian verb conjugation drill pack with standard lessons and comprehensive drill subsystem.

**Manifest**:
```json
{
  "schemaVersion": 1,
  "packId": "IT_VERB_GROUPS_ALL",
  "packVersion": "v3",
  "language": "it",
  "displayName": "Verb Drill - All Groups",
  "lessons": [
    { "lessonId": "L01_1FORM", "order": 1, "title": "Prima Forma - Base", "file": "it_lesson_01.csv" },
    { "lessonId": "L02_1FORM", "order": 2, "title": "Prima Forma - Extended", "file": "it_lesson_02.csv" },
    { "lessonId": "L03_1FORM", "order": 3, "title": "Prima Forma - Practice", "file": "it_lesson_03.csv" },
    { "lessonId": "L04_1FORM", "order": 4, "title": "Prima Forma - Review", "file": "it_lesson_04.csv" },
    { "lessonId": "L05_1FORM", "order": 5, "title": "Prima Forma - Consolidation", "file": "it_lesson_05.csv" },
    { "lessonId": "L06_1FORM", "order": 6, "title": "Prima Forma - Depth", "file": "it_lesson_06.csv" }
  ],
  "verbDrill": { "files": ["it_verb_groups_all.csv"] },
  "vocabDrill": {
    "files": [
      "it_drill_nouns.csv",
      "it_drill_verbs.csv",
      "it_drill_adjectives.csv",
      "it_drill_adverbs.csv",
      "it_drill_numbers.csv",
      "it_drill_pronouns.csv"
    ]
  }
}
```

**Contents**:
- 6 standard lesson CSVs (`it_lesson_01.csv` through `it_lesson_06.csv`) -- Italian translation exercises.
- `it_verb_groups_all.csv` -- Verb conjugation drill with columns RU;IT;Verb;Tense;Group;Rank.
- 6 vocab drill CSVs covering different parts of speech (nouns, verbs, adjectives, adverbs, numbers, pronouns). Each has its own column schema parsed by `ItalianDrillVocabParser`.

### 15.2.3 Seeding on First Launch

The `LessonStore` class manages default pack seeding through these mechanisms:

1. **`seedDefaultPacksIfNeeded()`**: Called on app startup. Checks for the seed marker file `grammarmate/seed_v1.done`. If the marker does not exist and no lesson CSVs are found in the lessons directory, all default packs are imported from bundled assets. The marker is then written atomically ("ok" if any packs were seeded, "none" if none succeeded, "skip" if content already existed).

2. **`updateDefaultPacksIfNeeded()`**: Called on app startup after seeding. For each default pack, reads the manifest from assets and compares `packVersion` against the installed version. If the installed version is missing or different, re-imports from assets. This enables seamless content updates via app updates.

3. **`forceReloadDefaultPacks()`**: Called on app reinstall to ensure the latest lesson content is loaded. Removes all installed data for each default pack, then re-imports from assets.

### 15.2.4 DefaultPack Data Structure

```kotlin
private data class DefaultPack(
    val packId: String,       // e.g. "EN_WORD_ORDER_A1"
    val assetPath: String     // e.g. "grammarmate/packs/EN_WORD_ORDER_A1.zip"
)
```

The `defaultPacks` list in `LessonStore` is hardcoded:

```kotlin
private val defaultPacks = listOf(
    DefaultPack("EN_WORD_ORDER_A1", "grammarmate/packs/EN_WORD_ORDER_A1.zip"),
    DefaultPack("IT_VERB_GROUPS_ALL", "grammarmate/packs/IT_VERB_GROUPS_ALL.zip")
)
```

**To add a new default pack**: add a `DefaultPack` entry AND place the ZIP in `assets/grammarmate/packs/`. Both steps are required.

---

## 15.3 Pack Import Flow

### 15.3.1 Import Entry Points

There are three import paths:

| Method | Source | Trigger |
|--------|--------|---------|
| `importPackFromAssets(assetPath)` | Bundled APK asset | Seeding, updates, force reload |
| `importPackFromUri(uri, resolver)` | User-selected file via system picker | Settings screen import button |
| `importPackFromStream(input)` | Internal delegate | Called by both paths above |

### 15.3.2 Step-by-Step Import Process

**Step 1: Ensure directories exist**

```
grammarmate/lessons/     (lesson CSV storage)
grammarmate/packs/       (pack archive storage)
```

`ensureSeedData()` creates the lessons directory and initializes `languages.yaml` with English and Italian defaults if it does not exist.

**Step 2: Extract ZIP to temporary directory**

The ZIP stream is extracted to `grammarmate/packs/tmp_<UUID>/`. Each entry is written to a file in the temp directory. Path traversal is prevented by verifying that the canonical target path starts with the canonical temp directory path. If validation fails, the import aborts with an error.

**Step 3: Parse manifest**

Reads `manifest.json` from the temp directory and parses it via `LessonPackManifest.fromJson()`. If the manifest file does not exist or parsing fails, the temp directory is deleted and an error is thrown.

**Step 4: Ensure language**

The `language` field from the manifest is lowercased and trimmed. If this language ID does not exist in `languages.yaml`, it is added automatically. Known codes (`en`, `it`) get display names ("English", "Italian"); unknown codes get an uppercase display name.

**Step 5: Remove old pack data (incremental update)**

If a pack with the same `packId` is already installed, its entry is removed from `packs.yaml` and its pack directory (`grammarmate/packs/{packId}/`) is deleted. Lesson data for the old version is replaced in the next step.

**Step 6: Move extracted files to permanent pack directory**

The temp directory is moved to `grammarmate/packs/{packId}/`. This preserves the complete archive contents (manifest, CSVs, JSONs) for future reference and re-import.

**Step 7: Import standard lessons**

Each lesson entry in `manifest.lessons` with `type != "verb_drill"` is processed:
1. Verify the source CSV file exists in the pack directory.
2. Call `importLessonFromFile()` which:
   - Copies the CSV to `grammarmate/lessons/{languageId}/lesson_{lessonId}.csv`.
   - Parses the CSV to extract the title and cards via `CsvParser`.
   - Prefixes card IDs with the lesson ID to avoid collisions (`{lessonId}_{index}`).
   - If `drillFile` is specified, copies it to `grammarmate/lessons/{languageId}/lesson_{lessonId}_drill.csv` and parses it.
   - Removes any existing lesson with the same ID (by `replaceById`) or title (by `replaceByTitle`).
   - Appends the lesson to the lesson index at `grammarmate/lessons/{languageId}_index.yaml`.

**Step 8: Import pack-scoped drills**

`importPackDrills()` processes the `verbDrill` and `vocabDrill` manifest sections:
- For each file in `verbDrill.files`: copies from the pack directory to `grammarmate/drills/{packId}/verb_drill/{filename}`.
- For each file in `vocabDrill.files`: copies from the pack directory to `grammarmate/drills/{packId}/vocab_drill/{filename}`.
- Missing files are silently skipped (no error thrown for drill files).

**Step 9: Import story quizzes**

All `.json` files in the pack directory (except `manifest.json`) are parsed as `StoryQuiz` objects. Each is stored as `grammarmate/stories/{storyId}.json` and indexed in `grammarmate/stories/stories.yaml`. Old versions of the same story (matching `storyId` + `lessonId` + `phase`) are replaced.

**Step 10: Import vocabulary files**

All CSV files in the pack directory whose names start with `vocab_` (case-insensitive) are processed. The lesson ID is extracted from the filename by removing the `vocab_` prefix. Files are stored at `grammarmate/vocab/{languageId}/{filename}` and indexed in `grammarmate/vocab/vocab.yaml`. Old entries for the same lesson+language are replaced.

**Step 11: Update pack registry**

The `grammarmate/packs.yaml` file is updated:
- Any existing entry with the same `packId` is removed.
- A new entry is added with `packId`, `packVersion`, `languageId`, `importedAt` (current timestamp), and optionally `displayName`.

### 15.3.3 Error Handling

| Error condition | Behavior |
|----------------|----------|
| ZIP is not a valid archive | `ZipInputStream` throws; import fails |
| `manifest.json` not in ZIP | Temp directory deleted; `error("Manifest not found")` thrown |
| `schemaVersion != 1` | Parsing fails with `"Unsupported schemaVersion"` |
| Missing `packId`/`packVersion`/`language` | Parsing fails with `"Missing packId/packVersion/language"` |
| No lessons and no drill sections | Parsing fails with `"Manifest has no lessons and no drill sections"` |
| Referenced CSV file missing from ZIP | `error("Missing lesson file: {file}")` thrown |
| Path traversal in ZIP entry names | `error("Invalid zip entry: {name}")` thrown |
| Invalid JSON in story files | Silently skipped (`runCatching` + `return@forEach`) |
| Invalid CSV rows | Silently skipped (rows with wrong column count or empty cells) |

### 15.3.4 Filesystem Layout After Import

```
context.filesDir/grammarmate/
  languages.yaml                          # language registry
  packs.yaml                              # installed packs index
  seed_v1.done                            # seeding marker
  packs/
    {packId}/
      manifest.json                       # copy of original manifest
      *.csv                               # original lesson CSVs
      *.json                              # original story JSONs
  lessons/
    {languageId}_index.yaml               # lesson index for this language
    {languageId}/
      lesson_{lessonId}.csv               # imported lesson CSV
      lesson_{lessonId}_drill.csv         # imported drill CSV (if any)
  drills/
    {packId}/
      verb_drill/
        {filename}.csv                    # verb drill CSVs
      vocab_drill/
        {filename}.csv                    # vocab drill CSVs
      verb_drill_progress.yaml            # verb drill progress (per-pack)
      word_mastery.yaml                   # vocab mastery progress (per-pack)
  stories/
    stories.yaml                          # story index
    {storyId}.json                        # individual story files
  vocab/
    vocab.yaml                            # vocab index
    {languageId}/
      vocab_{lessonId}.csv                # vocab files
```

---

## 15.4 Pack-Scoped Drills

### 15.4.1 Overview

Drills are additional practice modes that go beyond standard sentence translation lessons. There are two drill types:

- **Verb Drill**: Conjugation practice. Cards show a Russian prompt and the user must produce the correct Italian verb form. Cards are organized by verb, tense, and group for filtered practice.
- **Vocab Drill**: Anki-style flashcard review. Words are presented with collocations for context. Progress uses spaced repetition intervals.

### 15.4.2 Manifest Declaration

Drills are declared as top-level sections in `manifest.json`:

```json
{
  "verbDrill": { "files": ["it_verb_groups_all.csv"] },
  "vocabDrill": { "files": ["it_drill_nouns.csv", "it_drill_verbs.csv", ...] }
}
```

Both sections are optional. A pack without drill sections simply does not show drill tiles on the Home screen.

### 15.4.3 Per-Pack Drill Isolation

All drill data and progress is scoped by `packId`:

| Data | Path |
|------|------|
| Verb drill CSVs | `grammarmate/drills/{packId}/verb_drill/` |
| Vocab drill CSVs | `grammarmate/drills/{packId}/vocab_drill/` |
| Verb drill progress | `grammarmate/drills/{packId}/verb_drill_progress.yaml` |
| Vocab mastery progress | `grammarmate/drills/{packId}/word_mastery.yaml` |

This isolation means:
- Two packs can each have their own verb drill without conflicting.
- Removing a pack deletes its drill directory and all associated progress.
- Drill ViewModels use `reloadForPack(packId)` to load pack-specific data.

### 15.4.4 Active Pack and Drill Visibility

The concept of "active pack" determines which drills are shown:

- `hasVerbDrill(packId, languageId)` checks whether `grammarmate/drills/{packId}/verb_drill/` contains CSV files starting with `{languageId}_`.
- `hasVocabDrill(packId, languageId)` checks whether `grammarmate/drills/{packId}/vocab_drill/` contains CSV files starting with `{languageId}_`.
- Drill tiles on HomeScreen are visible only when the active pack declares the corresponding drill section in its manifest AND the files exist on disk.

The active pack ID is stored in `TrainingProgress.activePackId`.

### 15.4.5 Drill File Access Methods

`LessonStore` provides several methods for accessing drill files:

| Method | Scope | Returns |
|--------|-------|---------|
| `getVerbDrillFiles(packId, languageId)` | Language-filtered | CSV files matching `{languageId}_*` in verb_drill dir |
| `getVerbDrillFilesForPack(packId)` | All languages | All CSV files in verb_drill dir |
| `getVocabDrillFiles(packId, languageId)` | Language-filtered | CSV files matching `{languageId}_*` in vocab_drill dir |
| `getVocabDrillFilesForPack(packId)` | All languages | All CSV files in vocab_drill dir |
| `hasVerbDrill(packId, languageId)` | Check only | Boolean |
| `hasVocabDrill(packId, languageId)` | Check only | Boolean |

### 15.4.6 Legacy Verb Drill Path

Before pack-scoped drills were introduced, verb drill CSVs were stored at `grammarmate/verb_drill/` and lessons with `type: "verb_drill"` in the manifest were handled via the `importVerbDrillFile()` method, which copies the CSV to `verb_drill/{languageId}_{lessonId}.csv`.

The old methods (`getVerbDrillFiles(languageId)`, `hasVerbDrillLessons(languageId)`) are `@Deprecated` but still present for backward compatibility. They read from the global `grammarmate/verb_drill/` directory.

---

## 15.5 CSV Lesson Format

### 15.5.1 Standard Lesson CSV

**Format**: Semicolon-delimited text. No header row in the column sense. The first non-empty line is the lesson title. Subsequent non-empty lines are cards.

**Encoding**: UTF-8 (with BOM tolerated via `trimStart('﻿')`).

**Structure**:
```
{lesson title}
{Russian prompt};{target answer(s)}
{Russian prompt};{target answer(s)}
...
```

**Columns**: Exactly 2.

| Column | Description |
|--------|-------------|
| `ru` | Russian prompt text (trimmed, quotes stripped) |
| `answers` | Target-language answer(s). Multiple accepted answers separated by `+` (e.g., `"We don't go+We do not go"`). Trimmed per variant. |

**Optional 3rd column**: If a 3rd semicolon-delimited field exists, it is parsed as the `tense` tag for the card (e.g., "Present Simple"). This is non-standard and only used in certain packs.

**Parsing rules**:
- Empty lines are skipped.
- Lines with fewer than 2 columns after parsing are skipped.
- Lines where either column is blank after trimming are skipped.
- Double quotes are respected as field delimiters (semicolons inside quoted fields are not treated as separators).
- The title is extracted from the first non-empty line: only letters, digits, spaces, hyphens, periods, and commas are kept, up to a maximum of 160 characters.

**Card ID generation**: Cards imported from pack lessons get IDs of the form `{lessonId}_{index}` where index is 0-based. This prevents ID collisions across lessons.

**Example**:
```
Word Order Simple Tenses
я получил сообщение.;I got a message.
ты делаешь торты?;Do you make cakes?
она получила подарок?;Did she get a present?
мы не идем на работу;We don't go to work+We do not go to work.
```

### 15.5.2 Drill Lesson CSV (per-lesson drillFile)

Same format as standard lesson CSV (15.5.1). The `drillFile` field in the manifest lesson entry specifies a second CSV with additional practice cards. These cards go into `Lesson.drillCards` and are used as the Reserve pool to prevent memorization of specific phrases.

Drill cards get IDs of the form `{lessonId}_drill_{index}`.

### 15.5.3 Verb Drill CSV

**Format**: Semicolon-delimited. First non-empty line is the title. Second non-empty line is the column header row. Subsequent lines are data rows.

**Header row** (column names are case-insensitive):

| Column | Required | Description |
|--------|----------|-------------|
| `RU` | yes | Russian prompt (e.g., `"я устал (essere stanco)"`) |
| `IT` | yes | Correct Italian answer (single answer, not `+`-separated) |
| `Verb` | no | Infinitive of the verb (e.g., `"essere"`). Used for filtering. |
| `Tense` | no | Tense name (e.g., `"Presente"`, `"Imperfetto"`). Used for filtering. |
| `Group` | no | Verb group name (e.g., `"irregular_unique"`, `"regular_are"`). Used for filtering. |
| `Rank` | no | Numeric rank for ordering/prioritization. |

**Card ID format**: `"{group}_{tense}_{dataRowIndex}"` (e.g., `"irregular_unique_Presente_0"`).

**Verb resolution**: If the `Verb` column is absent but the Russian prompt contains a parenthetical hint (e.g., `"(consapevole)"`), the parser attempts to extract the infinitive from the first word in the parentheses.

**Example**:
```
Verb Conjugation Drill Italian
RU;IT;Verb;Tense;Group;Rank
я осознаю (consapevole);Io sono consapevole.;essere;Presente;irregular_unique;1
ты осознаешь (consapevole);Tu sei consapevole.;essere;Presente;irregular_unique;1
он осознает (consapevole);Lui è consapevole.;essere;Presente;irregular_unique;1
```

### 15.5.4 Vocabulary CSV (per-lesson vocab)

**File naming**: `vocab_{lessonId}.csv` (e.g., `vocab_L01_PRESENT_SIMPLE.csv`).

**Format**: Semicolon-delimited. No header row.

| Column | Required | Description |
|--------|----------|-------------|
| `native` | yes | Native language text (Russian) |
| `target` | yes | Target language text |
| `hard` | no | If `"hard"`, `"1"`, or `"true"` (case-insensitive), marks the word as hard |

**Example**:
```
работа;work
работа;job;hard
дом;home
```

### 15.5.5 Vocab Drill CSV (pack-scoped, Italian)

These CSVs are stored in `grammarmate/drills/{packId}/vocab_drill/` and parsed by `ItalianDrillVocabParser`. The parser auto-detects the format based on the header row column names. All formats use **comma** as delimiter (unlike lesson CSVs which use semicolons). The `ru`/`meaning_ru`/`russian` column is detected by name and used for Russian translations.

#### drill_verbs.csv and drill_nouns.csv

```
rank,verb,collocations,ru
1,essere,eri consapevole;sarai felice;...,быть/являться
106,casa,bella casa;casa di famiglia;...,дом/жилище
```

| Column | Description |
|--------|-------------|
| `rank` | Integer frequency rank |
| `word` | Italian word (verb infinitive or noun) |
| `collocations` | Semicolon-separated Italian phrases |
| `ru` | Russian translation (optional) |

#### drill_adjectives.csv

```
rank,adjective,msg,fsg,mpl,fpl,collocations,ru
776,solito,solito,solita,soliti,solite,,обычный
```

| Column | Description |
|--------|-------------|
| `rank` | Frequency rank |
| `adjective` | Base form (masculine singular) |
| `msg` | Masculine singular form |
| `fsg` | Feminine singular form |
| `mpl` | Masculine plural form |
| `fpl` | Feminine plural form |
| `collocations` | Semicolon-separated phrases |
| `ru` | Russian translation |

Forms are stored as a `Map<String, String>` in `VocabWord.forms` with keys `msg`, `fsg`, `mpl`, `fpl`.

#### drill_adverbs.csv

```
rank,adverb,comparative,superlative,ru,collocations
2,non,,,не,
34,bene,meglio,ottimamente,хорошо,
```

| Column | Description |
|--------|-------------|
| `rank` | Frequency rank |
| `adverb` | Adverb form |
| `comparative` | Comparative form (may be empty) |
| `superlative` | Superlative form (may be empty) |
| `ru` | Russian translation |
| `collocations` | Semicolon-separated phrases |

#### drill_numbers.csv

```
category,italian,ru,form_m,form_f,notes
cardinal,zero,ноль,,,invariable
cardinal,uno,один,uno,una,changes gender
```

| Column | Description |
|--------|-------------|
| `category` | Number category (e.g., "cardinal", "ordinal") |
| `italian` | Italian number word |
| `ru` | Russian translation |
| `form_m` | Masculine form (optional) |
| `form_f` | Feminine form (optional) |
| `notes` | Usage notes (semicolon-separated collocations) |

No natural rank; synthetic rank is assigned by row order.

#### drill_pronouns.csv

```
type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes,ru
personale,soggetto,1s,io,io,io,io,first person singular subject,я
```

| Column | Description |
|--------|-------------|
| `type` | Pronoun type (e.g., "personale") |
| `category` | Subcategory (e.g., "soggetto") |
| `person` | Person/number code (e.g., "1s", "3sm") |
| `form_sg_m` | Singular masculine form |
| `form_sg_f` | Singular feminine form |
| `form_pl_m` | Plural masculine form |
| `form_pl_f` | Plural feminine form |
| `notes` | Usage notes / collocations |
| `ru` | Russian translation |

Forms stored as `Map<String, String>` with keys `form_sg_m`, `form_sg_f`, `form_pl_m`, `form_pl_f`. No natural rank; synthetic rank by row order.

### 15.5.6 Encoding and Special Characters

- **Primary encoding**: UTF-8. The BOM character (`﻿`) is stripped from the first line of CSV files.
- **Fallback encodings** (pack validator only): UTF-8-SIG, UTF-16, UTF-16-LE, UTF-16-BE, CP1251. The validator tries each in order until one succeeds.
- **Semicolons in field values**: Use double-quote delimiters (e.g., `"text with ; semicolon";answer`). The CSV parser respects quoted fields.
- **Plus sign (`+`)**: In standard lesson CSVs only, separates multiple accepted answers within the `answers` column. In all other CSV formats, `+` has no special meaning.
- **Comma (`,`)**: In vocab drill CSVs, comma is the column delimiter. In all lesson/story CSVs, semicolon is the delimiter.

---

## 15.6 Content Lifecycle

### 15.6.1 Adding Content

**Bundled default packs (app update)**:
1. Place ZIP file in `app/src/main/assets/grammarmate/packs/`.
2. Add a `DefaultPack` entry to the `defaultPacks` list in `LessonStore`.
3. On next app launch, `updateDefaultPacksIfNeeded()` detects the version change and re-imports.

**User import (Settings screen)**:
1. User selects a ZIP file via the system file picker.
2. `importPackFromUri()` is called, which delegates to `importPackFromStream()`.
3. The full import flow runs (see 15.3.2).

### 15.6.2 Updating Content

**Re-importing the same pack** (same `packId`):
- The old pack directory is deleted and replaced.
- Old lesson index entries for the same lesson IDs are removed via `replaceById()`.
- Old stories with the same `storyId` are replaced.
- Old vocab entries for the same `lessonId` + `languageId` are replaced.
- Drill files are overwritten (copy with `overwrite = true`).
- The pack registry entry is updated with the new `packVersion` and `importedAt` timestamp.

**Default pack update detection**: `updateDefaultPacksIfNeeded()` compares `packVersion` strings between the installed and asset versions. If they differ, re-import occurs automatically.

**What is NOT reset on re-import**:
- `VerbDrillStore` progress (`verb_drill_progress.yaml`) -- persists across re-imports since it lives in `drills/{packId}/`.
- `WordMasteryStore` progress (`word_mastery.yaml`) -- persists across re-imports.
- `MasteryStore` per-lesson progress (`uniqueCardShows`, `shownCardIds`) -- persists since it is keyed by lesson ID.
- `ProgressStore` session state -- persists.

### 15.6.3 Removing Content

**Removing a single pack** (`removeInstalledPackData(packId)`):
1. Read the installed pack manifest to get lesson IDs and language ID.
2. For each lesson in the manifest, call `deleteLesson(languageId, lessonId)` which removes the CSV file and the lesson index entry.
3. Remove the pack entry from `packs.yaml`.
4. Delete the pack directory `grammarmate/packs/{packId}/`.
5. Delete the drill directory `grammarmate/drills/{packId}/` (removes all drill CSVs and progress files).
6. If no manifest is found, fall back to removing the registry entry and directories by `packId`.

**Removing all content for a language** (`deleteAllLessons(languageId)`):
1. Delete the entire `grammarmate/lessons/{languageId}/` directory.
2. Delete the lesson index file `grammarmate/lessons/{languageId}_index.yaml`.
3. Remove all vocab entries for this language from `vocab.yaml` and delete associated files.
4. Remove all story entries for this language from `stories.yaml` and delete associated files.
5. Remove all pack entries for this language from `packs.yaml` and delete pack directories.

### 15.6.4 Content Versioning

- Each pack carries a `packVersion` string (e.g., `"v1"`, `"v2"`, `"v3"`).
- Version comparison is plain string equality: `existing.packVersion != manifest.packVersion`.
- There is no semantic versioning, no migration path between versions, and no partial updates.
- Updating a pack is a full replacement: old data is removed, new data is installed.

---

## 15.7 Pack Validator

### 15.7.1 Overview

`pack_validator.py` is a Python tool located at `tools/pack_validator/pack_validator.py` that validates lesson pack ZIP archives before import. It is intended for use by content authors during pack development.

**Usage**:
```bash
python tools/pack_validator/pack_validator.py <lesson_pack.zip>
```

**Exit codes**:
- `0` -- Validation passed (prints "OK").
- `1` -- Validation failed (prints "ERROR: {message}").
- `2` -- Usage error (wrong number of arguments).

### 15.7.2 Validation Checks

The validator performs the following checks in order:

#### 1. File existence
- The ZIP file must exist and be a regular file.

#### 2. Manifest validation (`_validate_manifest`)
- `manifest.json` must exist in the ZIP root.
- Must be valid JSON.
- `schemaVersion` must equal `1`.
- `packId`, `packVersion`, `language` must be non-blank strings.
- `lessons` must be a non-empty array.
- Each lesson entry must be a JSON object with non-blank `lessonId` and `file`.
- Each referenced lesson file must exist in the ZIP.
- Each lesson CSV is validated (see check 3).

#### 3. Lesson CSV validation (`_validate_lesson_csv`)
- File must be readable (encoding auto-detected).
- Must have at least one non-empty line (title).
- All data rows (after the title) must have exactly 2 columns.
- Both columns must be non-empty after trimming and quote stripping.
- Must have at least one valid data row.

#### 4. Story quiz validation (`_validate_stories`)
- All `.json` files in the ZIP (except `manifest.json`) are validated as story quizzes.
- Required fields: `storyId`, `lessonId`, `phase`, `text` (all non-blank).
- `questions` must be a list.
- Each question must have non-blank `qId` and `prompt`.
- `options` must be a non-empty list.
- `correctIndex` must be an integer in range `[0, len(options))`.

#### 5. Vocabulary CSV validation (`_validate_vocab`)
- All CSV files whose names start with `vocab_` (case-insensitive) are validated.
- Each row must have at least 2 columns.
- Both the native and target text columns must be non-empty.

### 15.7.3 Encoding Detection

The validator tries the following encodings in order until one succeeds:
1. `utf-8-sig` (UTF-8 with BOM)
2. `utf-16`
3. `utf-16-le`
4. `utf-16-be`
5. `cp1251` (Windows Cyrillic)

If none succeed, the file is reported as having an unsupported encoding.

### 15.7.4 Limitations

The validator does NOT check:
- Drill CSV files (verb drill or vocab drill) referenced in `verbDrill`/`vocabDrill` sections.
- Pack-scoped drill sections at all (the validator predates this feature).
- Whether lesson IDs are unique within the pack.
- Whether `packId` conflicts with other installed packs.
- CSV encoding issues that only manifest at runtime on Android.
- The optional 3rd column (`tense`) in standard lesson CSVs.

---

## 15.8 Adding a New Lesson Pack

### 15.8.1 Step-by-Step Guide

**Step 1: Create the lesson content**

Prepare CSV files for each lesson. Use semicolon as delimiter. First line is the lesson title, subsequent lines are cards with format `Russian prompt;Target answer(s)`.

```
My Lesson Title
я работаю из дома.;I work from home+I work at home
он часто опаздывает.;He is often late
```

If the pack includes drill content, prepare additional CSVs:
- For verb drills: create a CSV with header row `RU;IT;Verb;Tense;Group;Rank`.
- For vocab drills: create comma-delimited CSVs with appropriate headers per part of speech.

**Step 2: Create optional content files**

- Story quiz JSONs: `{storyId}.json` with `storyId`, `lessonId`, `phase`, `text`, and `questions` array.
- Vocabulary CSVs: `vocab_{lessonId}.csv` with `native;target[;hard]` rows.

**Step 3: Create manifest.json**

```json
{
  "schemaVersion": 1,
  "packId": "MY_NEW_PACK",
  "packVersion": "v1",
  "language": "en",
  "displayName": "My New Pack",
  "lessons": [
    {
      "lessonId": "L01_MY_LESSON",
      "order": 1,
      "title": "Lesson 1 Title",
      "file": "lesson_01.csv",
      "drillFile": "lesson_01_drill.csv"
    }
  ]
}
```

If including drill subsystem:
```json
{
  "schemaVersion": 1,
  "packId": "MY_NEW_PACK",
  "packVersion": "v1",
  "language": "en",
  "displayName": "My New Pack",
  "lessons": [ ... ],
  "verbDrill": { "files": ["verb_drill.csv"] },
  "vocabDrill": { "files": ["vocab_nouns.csv", "vocab_verbs.csv"] }
}
```

**Step 4: Validate the pack**

```bash
python tools/pack_validator/pack_validator.py path/to/MY_NEW_PACK.zip
```

Fix any reported errors before proceeding.

**Step 5: Create the ZIP archive**

Create a ZIP file with `manifest.json` and all referenced CSV/JSON files at the root level:

```bash
cd my_pack_directory/
zip -r ../MY_NEW_PACK.zip manifest.json lesson_01.csv lesson_01_drill.csv story_L01_CHECK_IN.json
```

**Step 6: Register as a default pack (if bundling with the app)**

1. Copy the ZIP to `app/src/main/assets/grammarmate/packs/MY_NEW_PACK.zip`.
2. Open `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt`.
3. Add a new entry to the `defaultPacks` list:

```kotlin
private val defaultPacks = listOf(
    DefaultPack("EN_WORD_ORDER_A1", "grammarmate/packs/EN_WORD_ORDER_A1.zip"),
    DefaultPack("IT_VERB_GROUPS_ALL", "grammarmate/packs/IT_VERB_GROUPS_ALL.zip"),
    DefaultPack("MY_NEW_PACK", "grammarmate/packs/MY_NEW_PACK.zip")  // NEW
)
```

Both steps are required. The ZIP in assets alone will not be detected without the `DefaultPack` entry.

**Step 7: Verify the build**

Run the app build to ensure the asset is included:
```bash
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

Install the APK and verify that the new pack is seeded correctly on first launch.

### 15.8.2 Required Files Checklist

| File | Required | Notes |
|------|----------|-------|
| `manifest.json` | yes | Must be at ZIP root |
| Lesson CSVs (one per manifest entry) | yes | Referenced by `lessons[].file` |
| Drill CSVs (per lesson) | no | Referenced by `lessons[].drillFile` |
| Story quiz JSONs | no | Any `.json` except `manifest.json` |
| Vocab CSVs | no | Named `vocab_{lessonId}.csv` |
| Verb drill CSVs | no | Referenced by `verbDrill.files[]` |
| Vocab drill CSVs | no | Referenced by `vocabDrill.files[]` |

### 15.8.3 Registration in Code

For bundled packs, the only code change needed is adding a `DefaultPack` entry in `LessonStore.defaultPacks`. No other registration is required.

For user-imported packs, no code changes are needed. The import flow handles everything automatically.

### 15.8.4 Content Quality Guidelines

- Each lesson should follow the "1 topic = 1 pattern" principle to maintain mono-learning effectiveness.
- Recommended lesson size: 50-150 cards (split into Active Set of first 150 and Reserve of remainder).
- Each sub-lesson should have 8-15 cards (recommended default: 10).
- Multiple accepted answers use `+` separator (e.g., `"We don't go+We do not go"`).
- Russian prompts should be natural, conversational sentences.
- Verb drill cards should include parenthetical hints (e.g., `"(essere stanco)"`) for verb resolution fallback.
- Vocab drill collocations should be semicolon-separated authentic Italian phrases.
