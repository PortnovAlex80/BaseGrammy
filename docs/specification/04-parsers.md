# 4. Parsers -- Specification

All parsers live in `com.alexpo.grammermate.data` and convert raw CSV/JSON files into domain data classes. Every parser is a Kotlin `object` (singleton) with no state.

---

## 4.1 CsvParser

**Source:** `data/CsvParser.kt`

### Purpose

Parses standard lesson CSV files that ship inside lesson pack ZIPs. Each file contains one lesson's worth of translation cards (Russian prompt, target-language answers, optional tense tag). The first non-blank line is treated as the lesson title.

### Input format

Semicolon-delimited text file (`.csv` extension despite `;` delimiter). No header row. Encoding: UTF-8 (with BOM tolerance).

```
[title line]          <- first non-blank line, lesson title (optional)
ru;text;[tense]       <- data rows
ru;answer1+answer2    <- multiple accepted answers
```

**Column definitions:**

| Column | Index | Required | Description |
|--------|-------|----------|-------------|
| `ru` | 0 | Yes | Russian prompt text. Leading/trailing whitespace and double quotes are trimmed. |
| `answers` | 1 | Yes | One or more accepted target-language translations, separated by `+`. Each answer is trimmed and quote-stripped. Blank answers are filtered out. |
| `tense` | 2 | No | Optional grammatical tense/mood label (e.g. "Presente", "Passato Prossimo"). Trimmed and quote-stripped. Blank values become `null`. |

### Output

Returns `Pair<String?, List<SentenceCard>>` where:
- `first` = extracted title string, or `null` if the first line is blank or contains no valid title characters.
- `second` = ordered list of `SentenceCard` instances.

**`SentenceCard`** (defined in `Models.kt`, implements `SessionCard`):

```kotlin
data class SentenceCard(
    override val id: String,           // "card_{lineNumber}" (1-based, counting from file start)
    override val promptRu: String,     // Russian prompt
    override val acceptedAnswers: List<String>,  // parsed from answers column
    val tense: String? = null          // optional tense tag
) : SessionCard
```

**`SessionCard` interface** (defined in `CardSessionContract.kt`):

```kotlin
interface SessionCard {
    val id: String
    val promptRu: String
    val acceptedAnswers: List<String>
}
```

### Parsing rules

1. **Line splitting:** semicolon (`;`) delimiter with double-quote escaping. Inside quoted fields, semicolons are treated as literal characters, not delimiters. Quotes toggle a state machine (`inQuotes` boolean).
2. **Blank line handling:** blank lines are skipped entirely (do not increment card count).
3. **Title extraction:** the first non-blank line is consumed as the title. Characters are collected while they are letters, digits, spaces, hyphens, periods, or commas. Collection stops at the first character outside this set or at 160 characters, whichever comes first. The Unicode BOM (`﻿`) is stripped from the start. If the result is blank, the title is `null`.
4. **Minimum columns:** a data row must have at least 2 columns (ru + answers). Rows with fewer columns are silently skipped.
5. **Empty fields:** if `ru` or the entire `answers` column is blank after trimming, the row is skipped. If all individual answers (after splitting on `+` and trimming) are blank, the row is skipped.
6. **Answer splitting:** the answers column is split on `+`. Each resulting answer is trimmed of whitespace and surrounding double quotes. Blank answers are removed from the list.
7. **Card IDs:** generated as `"card_{lineNumber}"` where `lineNumber` counts from 1 and includes title and blank lines (i.e., it is the raw line number in the file, not the card index).

### Error handling

The parser is lenient: malformed rows (wrong column count, blank required fields) are silently skipped. No exceptions are thrown for bad data. The parser never returns `null` for the card list -- it returns an empty list if no valid rows are found.

### Public API

```kotlin
object CsvParser {
    fun parseLesson(inputStream: InputStream): Pair<String?, List<SentenceCard>>
}
```

- **Input:** an `InputStream` (typically from a ZIP entry or asset file).
- **Output:** title and card list. The stream is fully consumed and closed via `reader.useLines`.

### Data flow

```
Lesson ZIP file
  -> LessonStore.importPack() reads ZIP entries
  -> CsvParser.parseLesson(inputStream) per CSV file
  -> List<SentenceCard> stored in lesson data
  -> Consumed by TrainingViewModel for lesson sessions, sub-lessons, boss battles, daily practice
```

---

## 4.2 VocabCsvParser

**Source:** `data/VocabCsvParser.kt`

### Purpose

Parses generic vocabulary drill CSV files with a simple three-column format (native text, target text, hardness flag). Used as a fallback/general-purpose vocab format, distinct from the language-specific Italian drill vocab parser.

### Input format

Semicolon-delimited text file. No header row. Encoding: UTF-8.

```
nativeText;targetText[;hard]
```

**Column definitions:**

| Column | Index | Required | Description |
|--------|-------|----------|-------------|
| `nativeText` | 0 | Yes | Word/phrase in the learner's native language (Russian). Trimmed and quote-stripped. |
| `targetText` | 1 | Yes | Word/phrase in the target language. Trimmed and quote-stripped. Multiple accepted forms can be joined with `+` (though this parser does not split on `+`; that is handled downstream by the UI). |
| `hard` | 2 | No | Hardness flag. Recognized values (case-insensitive): `"hard"`, `"1"`, `"true"`. Any other value or missing column = `false`. |

### Output

Returns `List<VocabRow>`.

**`VocabRow`** (defined in `VocabCsvParser.kt`):

```kotlin
data class VocabRow(
    val nativeText: String,
    val targetText: String,
    val isHard: Boolean
)
```

### Parsing rules

1. **Line splitting:** identical to `CsvParser` -- semicolon delimiter with double-quote state machine.
2. **Blank lines:** skipped.
3. **Minimum columns:** 2. Rows with fewer columns are skipped.
4. **Empty fields:** if either `nativeText` or `targetText` is blank after trimming/quote-stripping, the row is skipped.
5. **Hardness:** column index 2 is checked. The raw string (trimmed, quote-stripped) is compared case-insensitively to `"hard"`, `"1"`, or `"true"`.

### Error handling

Identical to `CsvParser` -- silent skip of malformed rows, no exceptions.

### Public API

```kotlin
object VocabCsvParser {
    fun parse(inputStream: InputStream): List<VocabRow>
}
```

### Data flow

```
Vocab CSV file (from lesson pack or assets)
  -> VocabCsvParser.parse(inputStream)
  -> List<VocabRow>
  -> Converted to VocabEntry by calling code
  -> Consumed by TrainingViewModel for vocab drill sessions
```

---

## 4.3 VerbDrillCsvParser

**Source:** `data/VerbDrillCsvParser.kt`

### Purpose

Parses verb drill CSV files containing conjugation exercises. Supports a header-driven column mapping, so column order is flexible. Each row is a single conjugation prompt (Russian) with one correct answer (target language).

### Input format

Semicolon-delimited text file with a **mandatory header row**. The first non-blank line is the title, the second non-blank line is the header row. Encoding: UTF-8.

```
[title line]
RU;IT;Verb;Tense;Group;Rank    <- header (column order flexible)
ru_text;it_text;verb;tense;group;rank   <- data rows
```

**Header column definitions (case-insensitive):**

| Header name | Required | Description |
|-------------|----------|-------------|
| `ru` | Yes | Russian prompt text. If missing, the entire file produces no cards. |
| `it` | Yes | Target-language answer. If missing, the entire file produces no cards. |
| `verb` | No | Infinitive verb form. Used for grouping and fallback extraction. |
| `tense` | No | Grammatical tense/mood (e.g., "Presente", "Imperfetto"). |
| `group` | No | Arbitrary grouping label for organizing cards. |
| `rank` | No | Numeric rank/frequency. Parsed as `Int`, non-numeric values become `null`. |

**Column order is flexible** -- the parser reads the header row, records each column's index by name, then uses those indices when reading data rows. This means columns can appear in any order.

### Output

Returns `Pair<String?, List<VerbDrillCard>>`.

**`VerbDrillCard`** (defined in `VerbDrillCard.kt`, implements `SessionCard`):

```kotlin
data class VerbDrillCard(
    override val id: String,           // "{group}_{tense}_{dataRowIndex}"
    override val promptRu: String,     // Russian prompt
    val answer: String,                // Single correct answer
    val verb: String? = null,          // Resolved verb (header or parenthetical extraction)
    val tense: String? = null,         // Tense label
    val group: String? = null,         // Group label
    val rank: Int? = null              // Numeric rank
) : SessionCard {
    override val acceptedAnswers: List<String> get() = listOf(answer)
}
```

### Parsing rules

1. **Title:** first non-blank line, extracted using `extractTitle()` (same logic as `CsvParser` but only allows letters, digits, and spaces -- no hyphens, periods, or commas). BOM stripped.
2. **Header row:** the second non-blank line. Columns are parsed and indices recorded for `ru`, `it`, `verb`, `tense`, `group`, `rank` (all case-insensitive via `.lowercase()`).
3. **Mandatory columns:** if `ru` or `it` headers are not found, no cards are produced (the parser continues but skips all data rows because `ruIndex < 0 || itIndex < 0`).
4. **Data rows:** columns are read by index. If the row has fewer columns than the maximum required index, the row is skipped.
5. **Verb resolution fallback:** if the `verb` column is absent or empty, the parser attempts to extract the verb from a parenthetical hint in the Russian prompt. Pattern: `\(([\\w]+)` captures the first word inside parentheses. For example, `"я устал (essere stanco)"` yields `"essere"`.
6. **Card ID generation:** `"{group}_{tense}_{dataRowIndex}"` where `group` and `tense` may be empty strings if null, and `dataRowIndex` is a 0-based counter of successfully parsed data rows.
7. **Blank/empty:** rows where `ru` or `it` is blank after trimming are skipped. Optional fields that are blank become `null`.

### Error handling

Silent skip of malformed rows. Non-numeric `rank` values become `null` (via `toIntOrNull()`). Missing verb columns trigger the parenthetical fallback. No exceptions thrown.

### Public API

```kotlin
object VerbDrillCsvParser {
    fun parse(content: String): Pair<String?, List<VerbDrillCard>>
}
```

- **Input:** file content as a `String` (not an `InputStream` -- differs from `CsvParser` and `VocabCsvParser`).
- **Output:** title and card list.

### Data flow

```
Verb drill CSV (from lesson pack ZIP, declared in manifest.json verbDrill section)
  -> LessonStore copies file to grammarmate/drills/{packId}/verb_drill/
  -> VerbDrillCsvParser.parse(fileContent)
  -> List<VerbDrillCard>
  -> Consumed by TrainingViewModel via VerbDrillHelper for verb drill sessions
  -> Progress tracked in grammarmate/drills/{packId}/verb_drill_progress.yaml (VerbDrillComboProgress)
```

---

## 4.4 ItalianDrillVocabParser

**Source:** `data/ItalianDrillVocabParser.kt`

### Purpose

Parses Italian-language vocabulary drill CSV files from assets. Handles six distinct CSV sub-formats (verbs, nouns, adjectives, adverbs, numbers, pronouns) with auto-detection based on the header row. Each sub-format has its own column layout but all produce the same `ItalianDrillRow` output type.

This parser uses **comma** as the delimiter (unlike all other parsers which use semicolon).

### Input format

Comma-delimited text files with a **mandatory header row**. Encoding: UTF-8. Files are loaded from assets at `grammarmate/vocab/it/`.

The parser auto-detects the sub-format by inspecting the header row.

#### 4.4.1 Sub-format: Verbs (`drill_verbs.csv`)

```
rank,verb,collocations,ru
1,essere,eri consapevole;sarai capace;...,быть/являться/существовать
```

| Column | Index | Required | Description |
|--------|-------|----------|-------------|
| `rank` | 0 | Yes | Numeric frequency rank. Must parse as `Int`. |
| `verb` | 1 | Yes | Italian verb infinitive. |
| `collocations` | 2 | No | Semicolon-separated Italian phrases/collocations. These are the actual drill items. |
| `ru` | 3 | No | Russian translation/meaning. Detected by header name matching `ru`, `meaning_ru`, or `russian`. |

**Hardness:** `isHard = rank > 100`.

#### 4.4.2 Sub-format: Nouns (`drill_nouns.csv`)

```
rank,noun,collocations,ru
106,casa,bella casa;casa di famiglia;...,дом/жилище/семья
```

| Column | Index | Required | Description |
|--------|-------|----------|-------------|
| `rank` | 0 | Yes | Numeric frequency rank. |
| `noun` | 1 | Yes | Italian noun. |
| `collocations` | 2 | No | Semicolon-separated Italian phrases. |
| `ru` | 3 | No | Russian translation. |

**Hardness:** `isHard = rank > 100`. Structure is identical to verbs (the same `parseRankWordCollocations` method handles both).

#### 4.4.3 Sub-format: Adjectives (`drill_adjectives.csv`)

```
rank,adjective,msg,fsg,mpl,fpl,collocations,ru
776,solito,solito,solita,soliti,solite,,обычный/привычный
```

| Column | Header | Required | Description |
|--------|--------|----------|-------------|
| 0 | `rank` | Yes | Numeric frequency rank. |
| 1 | `adjective` | Yes | Italian adjective (masculine singular base form). |
| 2 | `msg` | No | Masculine singular form. Stored in `forms["msg"]`. |
| 3 | `fsg` | No | Feminine singular form. Stored in `forms["fsg"]`. |
| 4 | `mpl` | No | Masculine plural form. Stored in `forms["mpl"]`. |
| 5 | `fpl` | No | Feminine plural form. Stored in `forms["fpl"]`. |
| 6 | `collocations` | No | Semicolon-separated phrases. Column position resolved by header index, fallback to index 6. |
| 7 | `ru` | No | Russian translation. Detected by header name. |

**Hardness:** `isHard = rank > 100`. Gender forms are stored in the `forms` map.

#### 4.4.4 Sub-format: Adverbs (`drill_adverbs.csv`)

```
rank,adverb,comparative,superlative,ru,collocations
2,non,,,не,
34,bene,meglio,ottimamente,хорошо,
```

| Column | Index | Required | Description |
|--------|-------|----------|-------------|
| 0 | `rank` | Yes | Numeric frequency rank. |
| 1 | `adverb` | Yes | Italian adverb. |
| 2 | `comparative` | No | Comparative form (currently not stored in `ItalianDrillRow`). |
| 3 | `superlative` | No | Superlative form (currently not stored). |
| 4 | `ru` | No | Russian translation. |
| 5 | `collocations` | No | Semicolon-separated phrases. Hard-coded to index 5. |

**Hardness:** `isHard = rank > 100`.

#### 4.4.5 Sub-format: Numbers (`drill_numbers.csv`)

```
category,italian,ru,form_m,form_f,notes
cardinal,zero,ноль,,,invariable
cardinal,uno,один,uno,una,changes gender
```

| Column | Header | Required | Description |
|--------|--------|----------|-------------|
| 0 | `category` | -- | Number category (e.g., "cardinal", "ordinal"). Not stored. |
| 1 | `italian` | Yes | Italian number word. This becomes the `word` field. |
| 2 | `ru` | Yes | Russian translation. |
| 3 | `form_m` | No | Masculine form. Stored in `forms["form_m"]`. |
| 4 | `form_f` | No | Feminine form. Stored in `forms["form_f"]`. |
| 5 | `notes` | No | Treated as collocations (semicolon-separated). |

**Rank:** no rank column. Synthetic rank is assigned sequentially starting from 1.

#### 4.4.6 Sub-format: Pronouns (`drill_pronouns.csv`)

```
type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes,ru
personale,soggetto,1s,io,io,io,io,first person singular subject,я
```

| Column | Header | Required | Description |
|--------|--------|----------|-------------|
| 0 | `type` | -- | Pronoun type (e.g., "personale"). Not stored. |
| 1 | `category` | -- | Category (e.g., "soggetto"). Not stored. |
| 2 | `person` | -- | Person indicator (e.g., "1s", "3sf"). Not stored. |
| 3 | `form_sg_m` | Yes | Singular masculine form. Becomes the main `word` field. |
| 4 | `form_sg_f` | No | Singular feminine form. Stored in `forms["form_sg_f"]`. |
| 5 | `form_pl_m` | No | Plural masculine form. Stored in `forms["form_pl_m"]`. |
| 6 | `form_pl_f` | No | Plural feminine form. Stored in `forms["form_pl_f"]`. |
| 7 | `notes` | No | Treated as collocations (semicolon-separated). |
| 8 | `ru` | No | Russian translation. |

**Minimum columns:** 4 (to include at least `form_sg_m`). **Rank:** synthetic, sequential from 1. The `form_sg_m` value at column index 3 is used as the primary `word`.

### Output

Returns `List<ItalianDrillRow>`.

**`ItalianDrillRow`** (defined in `ItalianDrillVocabParser.kt`):

```kotlin
data class ItalianDrillRow(
    val rank: Int,
    val word: String,
    val collocations: List<String>,
    val isHard: Boolean,
    val meaningRu: String? = null,
    val forms: Map<String, String> = emptyMap()
)
```

- `word`: the primary lexical item (verb, noun, adjective, etc.).
- `collocations`: list of Italian phrases for drilling. Parsed from semicolon-separated strings within a single CSV column.
- `isHard`: always derived from `rank > 100` (except numbers and pronouns, which use synthetic rank).
- `meaningRu`: Russian translation, detected from a `ru`/`meaning_ru`/`russian` header column.
- `forms`: gender/number form variants, keyed by header name (e.g., `"msg"`, `"fsg"`, `"form_m"`, `"form_sg_f"`).

### Parsing rules

1. **Delimiter:** comma (`,`), not semicolon. This is the only parser in the app that uses comma as delimiter.
2. **Header row:** the first line of the file. Split on comma, trimmed, lowercased, quote-stripped.
3. **Format auto-detection:** the header line is checked in order:
   - Contains `"rank"` AND (`"verb"` OR `"noun"`) -> `parseRankWordCollocations`
   - Contains `"rank"` AND `"adjective"` -> `parseAdjectives`
   - Contains `"rank"` AND `"adverb"` -> `parseAdverbs`
   - Contains `"category"` AND `"italian"` -> `parseNumbers`
   - Contains `"type"` AND `"person"` -> `parsePronouns`
   - Otherwise -> fallback to `parseRankWordCollocations` (with a warning log)
4. **RU column detection:** the `ru` column position is found by scanning headers for exact match against `"ru"`, `"meaning_ru"`, or `"russian"`.
5. **Collocations parsing:** collocations are semicolon-separated within a single CSV column. Split on `;`, trimmed, blanks filtered.
6. **Blank line/row handling:** blank lines are skipped. Rows where `rank` (for ranked formats) fails to parse as `Int` are skipped. Rows where the primary `word` is blank are skipped.
7. **Quote handling:** same double-quote state machine as other parsers, but for comma delimiter.

### Error handling

- Individual file failures in `loadAllFromAssets` are caught and logged (`Log.w`), allowing other files to be processed even if one fails.
- Unknown format falls back to `parseRankWordCollocations` with a warning log.
- Malformed rows are silently skipped.

### Public API

```kotlin
object ItalianDrillVocabParser {
    fun parse(inputStream: InputStream, fileName: String): List<ItalianDrillRow>
    fun loadAllFromAssets(context: Context, lessonId: String, languageId: String): List<VocabEntry>
}
```

- `parse()`: parses a single CSV file. `fileName` is used for logging only (not for format detection -- format is detected from header content).
- `loadAllFromAssets()`: iterates over a hardcoded list of six files (`drill_adjectives.csv`, `drill_adverbs.csv`, `drill_nouns.csv`, `drill_numbers.csv`, `drill_pronouns.csv`, `drill_verbs.csv`) in assets at `grammarmate/vocab/it/`. Converts all `ItalianDrillRow` results to `VocabEntry` objects:
  - `id` = `"it_drill_{fileName}_{rank}"`
  - `nativeText` = `row.word` (Italian)
  - `targetText` = collocations joined with `"+"`, or the word itself if no collocations
  - `isHard` = `row.isHard`
  - `lessonId` and `languageId` are passed through from parameters

### Data flow

```
Assets: grammarmate/vocab/it/drill_*.csv
  -> ItalianDrillVocabParser.loadAllFromAssets(context, lessonId, languageId)
  -> Iterates 6 hardcoded file names
  -> ItalianDrillVocabParser.parse(inputStream, fileName) per file
  -> List<ItalianDrillRow> per file
  -> Converted to List<VocabEntry>
  -> Consumed by TrainingViewModel via VocabHelper for vocab drill sessions
  -> Progress tracked in grammarmate/drills/{packId}/word_mastery.yaml
```

---

## 4.5 StoryQuizParser

**Source:** `data/StoryQuizParser.kt`

### Purpose

Parses story quiz definitions from JSON format. Story quizzes are comprehension exercises tied to lessons, presented in two phases (check-in before practice, check-out after practice).

### Input format

JSON object. Not CSV -- this is the only non-CSV parser.

```json
{
  "storyId": "string",
  "lessonId": "string",
  "phase": "CHECK_IN | CHECK_OUT",
  "text": "Story body text",
  "questions": [
    {
      "qId": "string",
      "prompt": "Question text",
      "options": ["option1", "option2", "option3"],
      "correctIndex": 0,
      "explain": "Optional explanation"
    }
  ]
}
```

**Field definitions:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `storyId` | String | Yes | Unique story identifier. Must not be blank. |
| `lessonId` | String | Yes | Associated lesson identifier. Must not be blank. |
| `phase` | String | Yes | Phase enum value: `"CHECK_IN"` or `"CHECK_OUT"`. Parsed via `StoryPhase.valueOf()`. Must not be blank. |
| `text` | String | Yes | Story body text. Must not be blank. |
| `questions` | Array | No | Array of question objects. Missing or null defaults to empty array. |
| `questions[].qId` | String | Yes | Question identifier. Must not be blank. |
| `questions[].prompt` | String | Yes | Question text. Must not be blank. |
| `questions[].options` | Array of String | Yes | Answer options. Must be non-empty after filtering blanks. |
| `questions[].correctIndex` | Int | Yes | 0-based index into `options`. Must be a valid index (`0 <= correctIndex < options.size`). Defaults to `-1` if absent. |
| `questions[].explain` | String | No | Explanation shown after answering. Blank values become `null`. |

### Output

Returns `StoryQuiz`.

**`StoryQuiz`** (defined in `Models.kt`):

```kotlin
data class StoryQuiz(
    val storyId: String,
    val lessonId: String,
    val phase: StoryPhase,
    val text: String,
    val questions: List<StoryQuestion>
)
```

**`StoryQuestion`** (defined in `Models.kt`):

```kotlin
data class StoryQuestion(
    val qId: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explain: String? = null
)
```

**`StoryPhase`** (defined in `Models.kt`):

```kotlin
enum class StoryPhase {
    CHECK_IN,
    CHECK_OUT
}
```

### Parsing rules

1. **Input:** plain text string containing JSON. Parsed via `org.json.JSONObject` and `JSONArray` (Android platform JSON, not Gson or kotlinx.serialization).
2. **Field access:** all top-level fields use `optString()` with default `""`, then `.trim()`. This means missing string fields become blank strings rather than null.
3. **Validation order:**
   - Top-level: `storyId`, `lessonId`, `phase`, and `text` must all be non-blank. If any is blank, `error()` is thrown (throws `IllegalStateException`).
   - `phase` must match a `StoryPhase` enum value exactly (case-sensitive). `valueOf()` throws `IllegalArgumentException` on mismatch.
   - Per-question: `qId` and `prompt` must be non-blank, `options` must be non-empty after filtering blanks, `correctIndex` must be a valid index into the options array. Invalid questions cause `error()`.
4. **Question filtering:** null entries in the `questions` array are skipped (`optJSONObject(i) ?: continue`). Blank option strings are filtered out.
5. **Explain field:** if absent or blank, becomes `null`.

### Error handling

Unlike the CSV parsers, this parser is **strict**:
- Missing required fields -> `error("Missing story fields")` (throws `IllegalStateException`).
- Invalid phase enum -> `IllegalArgumentException` from `valueOf()`.
- Invalid question -> `error("Invalid question at index $i")`.

There is no lenient/silent-skip mode. Callers must handle exceptions.

### Public API

```kotlin
object StoryQuizParser {
    fun parse(text: String): StoryQuiz
}
```

- **Input:** JSON text as a `String`.
- **Output:** fully validated `StoryQuiz` object. Throws on any validation failure.

### Data flow

```
Story JSON file (from lesson pack or embedded content)
  -> StoryQuizParser.parse(jsonText)
  -> StoryQuiz
  -> Consumed by TrainingViewModel for story phase presentation
  -> CHECK_IN phase shown before lesson practice
  -> CHECK_OUT phase shown after lesson completion
```

---

## 4.6 CSV Format Specifications

Complete reference for every CSV format used in the application.

### 4.6.1 Standard Lesson CSV

**Used by:** `CsvParser`
**Delimiter:** semicolon (`;`)
**Header row:** none
**File extension:** `.csv`
**Location in lesson pack:** root-level CSV files listed in `manifest.json` lessons array
**Example:** `it_presente.csv`

```
Italian - Presente
я устал (essere stanco);Io sono stanco;Presente
ты устал (essere stanco);Tu sei stanco;Presente
я хочу есть (avere fame);Io ho fame + Io ho molta fame;Presente
```

| Line type | Format | Notes |
|-----------|--------|-------|
| Title (first non-blank) | Free text | Up to 160 chars, alphanumeric + space + hyphen + period + comma |
| Data | `ru;answers[;tense]` | `answers` split on `+` for multiple accepted translations |

**Quoting:** fields may be enclosed in double quotes. Semicolons inside quotes are literal. Quotes toggle state (no escape mechanism for literal quotes within quoted fields).

**BOM:** UTF-8 BOM (`﻿`) is stripped from the title line only.

### 4.6.2 Drill CSV (non-verb)

**Used by:** `CsvParser` (same parser as standard lessons, for drill-typed lesson files that are NOT verb drills)
**Delimiter:** semicolon (`;`)
**Header row:** none
**Location in lesson pack:** referenced in lesson entries with `type: "verb_drill"` -- but these are filtered during import and parsed separately by `VerbDrillCsvParser`. Standard drill CSVs (if any) follow the same format as 4.6.1.

### 4.6.3 Verb Drill CSV

**Used by:** `VerbDrillCsvParser`
**Delimiter:** semicolon (`;`)
**Header row:** mandatory (second non-blank line after title)
**File extension:** `.csv`
**Location in lesson pack:** declared in `manifest.json` `verbDrill.files` array; copied to `grammarmate/drills/{packId}/verb_drill/` on import
**Example:** `it_presente_drill.csv`

```
Presente (Drill)
RU;IT;Verb;Tense;Group;Rank
я устал (essere stanco);Io sono stanco.;essere;Presente;essere_stanco;1
```

| Row type | Format | Notes |
|----------|--------|-------|
| Title (first non-blank) | Free text | Letters, digits, spaces only |
| Header (second non-blank) | Column names separated by `;` | Case-insensitive. `RU` and `IT` required. |
| Data | Values at mapped column indices | Column order determined by header |

**Header columns:**

| Name | Required | Description |
|------|----------|-------------|
| `RU` | Yes | Russian prompt |
| `IT` | Yes | Italian answer |
| `Verb` | No | Verb infinitive |
| `Tense` | No | Tense/mood label |
| `Group` | No | Grouping label |
| `Rank` | No | Numeric frequency rank |

**Verb fallback:** if the `Verb` column is empty, the parser extracts the verb from parenthetical text in the Russian prompt using regex `\(([\\w]+)`.

### 4.6.4 Vocab Drill CSV -- Verbs

**Used by:** `ItalianDrillVocabParser.parseRankWordCollocations`
**Delimiter:** comma (`,`)
**Header row:** mandatory
**File extension:** `.csv`
**Location:** `assets/grammarmate/vocab/it/drill_verbs.csv`

```
rank,verb,collocations,ru
1,essere,eri consapevole;sarai capace;...,быть/являться/существовать
15,avere,abbi coraggio;avere caldo;...,иметь/обладать
```

| Column | Name | Required | Notes |
|--------|------|----------|-------|
| 0 | `rank` | Yes | Integer frequency rank |
| 1 | `verb` | Yes | Italian verb infinitive |
| 2 | `collocations` | No | Semicolon-separated phrases |
| 3 | `ru` | No | Russian meaning (detected by header name) |

### 4.6.5 Vocab Drill CSV -- Nouns

**Used by:** `ItalianDrillVocabParser.parseRankWordCollocations` (same handler as verbs)
**Delimiter:** comma (`,`)
**Header row:** mandatory
**File extension:** `.csv`
**Location:** `assets/grammarmate/vocab/it/drill_nouns.csv`

```
rank,noun,collocations,ru
106,casa,bella casa;casa di famiglia;...,дом/жилище/семья
132,tempo,col passare del tempo;...,время/погода
```

| Column | Name | Required | Notes |
|--------|------|----------|-------|
| 0 | `rank` | Yes | Integer frequency rank |
| 1 | `noun` | Yes | Italian noun |
| 2 | `collocations` | No | Semicolon-separated phrases |
| 3 | `ru` | No | Russian meaning |

### 4.6.6 Vocab Drill CSV -- Adjectives

**Used by:** `ItalianDrillVocabParser.parseAdjectives`
**Delimiter:** comma (`,`)
**Header row:** mandatory
**File extension:** `.csv`
**Location:** `assets/grammarmate/vocab/it/drill_adjectives.csv`

```
rank,adjective,msg,fsg,mpl,fpl,collocations,ru
776,solito,solito,solita,soliti,solite,,обычный/привычный
874,pazzo,pazzo,pazza,pazzi,pazze,,сумасшедший/безумный
```

| Column | Name | Required | Notes |
|--------|------|----------|-------|
| 0 | `rank` | Yes | Integer frequency rank |
| 1 | `adjective` | Yes | Base form of adjective |
| 2 | `msg` | No | Masculine singular |
| 3 | `fsg` | No | Feminine singular |
| 4 | `mpl` | No | Masculine plural |
| 5 | `fpl` | No | Feminine plural |
| 6 | `collocations` | No | Semicolon-separated phrases. Position resolved by header index, fallback to 6. |
| 7 | `ru` | No | Russian meaning |

### 4.6.7 Vocab Drill CSV -- Adverbs

**Used by:** `ItalianDrillVocabParser.parseAdverbs`
**Delimiter:** comma (`,`)
**Header row:** mandatory
**File extension:** `.csv`
**Location:** `assets/grammarmate/vocab/it/drill_adverbs.csv`

```
rank,adverb,comparative,superlative,ru,collocations
2,non,,,не,
34,bene,meglio,ottimamente,хорошо,
```

| Column | Name | Required | Notes |
|--------|------|----------|-------|
| 0 | `rank` | Yes | Integer frequency rank |
| 1 | `adverb` | Yes | Italian adverb |
| 2 | `comparative` | No | Comparative form (not stored in output) |
| 3 | `superlative` | No | Superlative form (not stored in output) |
| 4 | `ru` | No | Russian meaning |
| 5 | `collocations` | No | Semicolon-separated phrases. Hard-coded to index 5. |

### 4.6.8 Vocab Drill CSV -- Numbers

**Used by:** `ItalianDrillVocabParser.parseNumbers`
**Delimiter:** comma (`,`)
**Header row:** mandatory
**File extension:** `.csv`
**Location:** `assets/grammarmate/vocab/it/drill_numbers.csv`

```
category,italian,ru,form_m,form_f,notes
cardinal,zero,ноль,,,invariable
cardinal,uno,один,uno,una,changes gender
```

| Column | Name | Required | Notes |
|--------|------|----------|-------|
| 0 | `category` | -- | Number category. Not stored. |
| 1 | `italian` | Yes | Italian number word. Becomes `word`. |
| 2 | `ru` | No | Russian translation |
| 3 | `form_m` | No | Masculine form variant |
| 4 | `form_f` | No | Feminine form variant |
| 5 | `notes` | No | Treated as collocations (semicolon-separated) |

**Rank:** synthetic, assigned sequentially starting from 1.

### 4.6.9 Vocab Drill CSV -- Pronouns

**Used by:** `ItalianDrillVocabParser.parsePronouns`
**Delimiter:** comma (`,`)
**Header row:** mandatory
**File extension:** `.csv`
**Location:** `assets/grammarmate/vocab/it/drill_pronouns.csv`

```
type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes,ru
personale,soggetto,1s,io,io,io,io,first person singular subject,я
```

| Column | Name | Required | Notes |
|--------|------|----------|-------|
| 0 | `type` | -- | Pronoun type. Not stored. |
| 1 | `category` | -- | Category. Not stored. |
| 2 | `person` | -- | Person code. Not stored. |
| 3 | `form_sg_m` | Yes | Singular masculine. Becomes `word`. Minimum 4 columns required. |
| 4 | `form_sg_f` | No | Singular feminine form |
| 5 | `form_pl_m` | No | Plural masculine form |
| 6 | `form_pl_f` | No | Plural feminine form |
| 7 | `notes` | No | Treated as collocations (semicolon-separated) |
| 8 | `ru` | No | Russian translation |

**Rank:** synthetic, assigned sequentially starting from 1.

### 4.6.10 Story Quiz JSON

**Used by:** `StoryQuizParser`
**Format:** JSON (not CSV)
**File extension:** `.json`
**Schema:**

```json
{
  "storyId": "string (required)",
  "lessonId": "string (required)",
  "phase": "CHECK_IN | CHECK_OUT (required)",
  "text": "string (required)",
  "questions": [
    {
      "qId": "string (required)",
      "prompt": "string (required)",
      "options": ["string (required, non-empty)"],
      "correctIndex": 0,
      "explain": "string (optional)"
    }
  ]
}
```

See section 4.5 for full field specification.

---

## 4.7 Shared Parsing Infrastructure

### 4.7.1 Semicolon CSV Line Splitter

Used by: `CsvParser`, `VocabCsvParser`, `VerbDrillCsvParser`

All three parsers contain identical private `parseLine(line: String): List<String>` implementations:

```kotlin
private fun parseLine(line: String): List<String> {
    // Semicolon delimiter, double-quote toggling
    // Quotes are NOT escaped -- a quote inside a quoted field toggles state
}
```

Behavior:
- Delimiter: `;`
- Quote character: `"` -- toggles `inQuotes` state. Quotes are included in the output string.
- When `inQuotes == true`, semicolons are treated as literal characters.
- When `inQuotes == false`, semicolons split the line into columns.
- No escape mechanism for literal quotes within quoted fields.
- The final column is added after the loop (no trailing delimiter required).

### 4.7.2 Comma CSV Line Splitter

Used by: `ItalianDrillVocabParser`

```kotlin
private fun splitCsvLine(line: String): List<String>
```

Identical logic to the semicolon splitter, but uses `,` as the delimiter. This is necessary because Italian vocab drill files use comma-separated values.

### 4.7.3 Title Extraction

Used by: `CsvParser`, `VerbDrillCsvParser`

Both parsers contain private `extractTitle(raw: String): String?` methods with slight differences:

**`CsvParser.extractTitle`:**
- Allowed characters: letters, digits, space, hyphen (`-`), period (`.`), comma (`,`)
- BOM stripped: `﻿`
- Max length: 160 characters

**`VerbDrillCsvParser.extractTitle`:**
- Allowed characters: letters, digits, space only
- BOM stripped: `﻿` (as character literal `'﻿'`)
- Max length: 160 characters

Both return `null` if the result is blank after trimming.

### 4.7.4 Common patterns across all parsers

| Pattern | Description |
|---------|-------------|
| `trim().trim('"')` | Every parsed value is whitespace-trimmed then quote-stripped. This is applied consistently across all parsers. |
| Silent skip | All CSV parsers silently skip malformed rows (wrong column count, blank required fields). `StoryQuizParser` is the exception -- it throws. |
| No encoding parameter | `CsvParser` and `VocabCsvParser` use default `InputStreamReader` encoding. `ItalianDrillVocabParser` explicitly uses `Charsets.UTF_8`. `VerbDrillCsvParser` takes a `String` (pre-read). |
| Singleton objects | All parsers are `object` declarations with no mutable state -- safe to call from any thread. |
