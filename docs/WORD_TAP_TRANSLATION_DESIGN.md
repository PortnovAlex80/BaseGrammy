# Word Tap Translation — Design Document

## 1. Current Sentence Display Architecture

### How Russian prompts are rendered

The Russian sentence prompt is displayed in the `CardPrompt` composable (`GrammarMateApp.kt:2524`):

```kotlin
@Composable
private fun CardPrompt(state: TrainingUiState, onSpeak: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), ...) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "RU", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.currentCard?.promptRu ?: "No cards",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TtsSpeakerButton(...)
        }
    }
}
```

Key observations:
- The prompt is a plain `Text` composable — not clickable, not annotated.
- The Russian text comes from `SentenceCard.promptRu` (field in `Models.kt:38`).
- The English translations are in `SentenceCard.acceptedAnswers` — a list of valid answer strings.
- `CardPrompt` is called from the main training column at line 2312.
- `TrainingUiState` (defined in `TrainingViewModel.kt:2360`) holds `currentCard: SentenceCard?`.
- The app currently uses `AnnotatedString` only for clipboard operations, not for interactive text.

### Data already available per card

Each `SentenceCard` has:
- `promptRu: String` — the Russian sentence (e.g., "Я не знаю")
- `acceptedAnswers: List<String>` — English translations (e.g., ["I don't know"])

Additionally, `VocabEntry` objects exist per lesson with:
- `nativeText: String` — the word/phrase in the target language (English for EN packs)
- `targetText: String` — the word/phrase in Russian

The vocab CSV files (`vocab_<lessonId>.csv`) use the same `;` delimiter: `nativeText;targetText[;hard]`.

---

## 2. Dictionary Source Options

### Option A: Use the existing frequency vocabulary file (RECOMMENDED — PRIMARY SOURCE)

**The project already contains** `FrequenceyVocabular.txt` at the repository root — a 12,527-entry frequency dictionary.

**File format:** Tab-delimited, 4 columns:
```
N\tRank\tEnglishWord\tRussianTranslation\t[Mark]
```
Example:
```
1	1	the	употребляется перед числительными...
2	2	be	быть; быть живым, жить/ иметься...
```

**Key characteristics:**
- **Direction:** English -> Russian (top English words ranked 1-12527 with Russian translations).
- **Coverage:** 12,527 entries covering the most frequent English words with extensive Russian translations per entry.
- **Translation quality:** Each entry has multiple Russian equivalents separated by `/` and `;`, covering various senses and contexts.
- **File size:** ~1.5 MB text file.

**How to use for Russian->English lookup (reverse index):**
1. Parse the file once at app startup (or on first dictionary access).
2. For each entry, split the Russian translations by `/` and `;` to get individual Russian words/phrases.
3. Build a reverse `Map<String, List<String>>`: Russian word -> English translations.
4. Russian translations in the file are mostly in dictionary (infinitive/nominative) form, so a basic stemmer is needed to match inflected forms appearing in sentences.

**Pros:**
- Already exists in the project — no external dependency, no licensing concerns.
- 12,527 entries is excellent coverage for A1-A2 level content.
- Rich multi-sense translations provide better context.
- Reasonable file size (~1.5 MB) — can be bundled in assets or read from storage.

**Cons:**
- Direction is English->Russian; must build a reverse index at runtime.
- Russian words in the file are in dictionary form — inflected forms in sentences won't match directly without stemming.
- Some entries have very long translation strings that need parsing/cleaning for display.
- Does not cover language-specific packs (Italian) — only Russian-English.

**Size:** ~1.5 MB file; ~3-5 MB in memory after parsing to reverse index.

### Option B: Supplement with lesson data and VocabEntry data

**Approach:** Use the frequency dictionary as the base (Option A), and supplement with word-level translations extracted from lesson content and `VocabEntry` data.

**How it works:**
1. Parse `FrequenceyVocabular.txt` into the reverse Russian->English index.
2. For each loaded lesson, also extract `VocabEntry` pairs (`nativeText`/`targetText`) as additional Russian->English mappings.
3. VocabEntry data takes priority (it's lesson-specific and curated).
4. The frequency dictionary fills gaps for words not in the current lesson's vocab.

**Pros:**
- VocabEntry translations are lesson-aligned and high quality.
- Combines broad coverage (frequency dict) with precise lesson-specific data.
- Still zero external dependencies.

**Cons:**
- Slightly more complex dictionary builder.
- Need to merge/prioritize multiple sources.

**Size:** Same as Option A + negligible overhead from lesson data.

### Option C: Build dictionary from lesson CSV data only

**Approach:** Extract word-level mappings solely from lesson sentence pairs (`promptRu` / `acceptedAnswers`) and `VocabEntry` data.

**Pros:**
- Zero additional file needed.
- Translations guaranteed to match lesson content.

**Cons:**
- Coverage limited to words in loaded lessons.
- Word-level alignment from full sentences is approximate.
- Insufficient for words the learner hasn't encountered yet.

**Size:** ~10-50 KB in memory.

### Option D: Use an online API

**Approach:** Query a translation API on word tap (e.g., Yandex Dictionary API, MyMemory, LibreTranslate).

**Pros:** Zero bundle size, always up-to-date.
**Cons:** Requires internet, latency, rate limits, privacy concerns.
**Size:** 0 KB bundle.

---

## 3. Recommended Approach

**Primary: Option A (frequency dictionary file) + Option B (VocabEntry supplement).**

Rationale:
1. The `FrequenceyVocabular.txt` file already exists in the project with 12,527 entries — the ideal foundation.
2. Building a reverse index at runtime is straightforward — parse once, cache in memory.
3. `VocabEntry` data provides lesson-specific, curated translations that take priority over the general dictionary.
4. Zero external dependencies, instant offline lookups.

**Four issues and their solutions:**

### Issue 1: Wrong direction (EN->RU, need RU->EN)

**Problem:** The file maps English headwords to Russian translations. We need the reverse — look up a Russian word and find its English equivalent.

**Solution:** Build a reverse index at parse time.

During parsing, for each entry:
1. Extract the English headword (column 2, e.g., "be").
2. Split the Russian translations (column 3) by `/` to get individual sense groups.
3. Within each sense group, split by `,` to get individual Russian words/phrases.
4. For each clean Russian token, add a reverse mapping: `russianWord -> englishHeadword`.

Example:
```
Entry: 2\tbe\tбыть; быть живым, жить/ иметься, наличествовать/ ...
Reverse entries:
  "быть"       -> "be"
  "жить"       -> "be"
  "иметься"    -> "be"
  "наличествовать" -> "be"
  ...
```

Ambiguity is expected (one Russian word may map to multiple English words). The reverse index stores `Map<String, List<String>>`. The popup shows the top match, with VocabEntry-sourced translations prioritized.

### Issue 2: Russian morphology (inflected forms don't match dictionary forms)

**Problem:** Russian words change by case, gender, number, tense. A learner tapping "книгу" (accusative) won't find it if the dictionary only has "книга" (nominative).

**Solution:** Lightweight Russian suffix-stripping stemmer.

Build a `RussianStemmer` utility that strips common inflectional suffixes to produce a likely dictionary form. This is NOT a full morphological analyzer — just suffix pattern matching:

```
Noun patterns (declension endings):
  -а -> strip (книга -> книг)     -я -> strip (семья -> семей)
  -у -> strip (книгу -> книг)     -ю -> strip (семью -> семей)
  -ы -> strip (книги -> книг)     -и -> strip (семьи -> семей)
  -е -> strip (книге -> книг)     -ом -> strip (книгом -> книг)
  -ой -> strip                    -ою -> strip
  -ами -> strip                   -ями -> strip
  -ах -> strip                    -ях -> strip

Verb patterns (conjugation endings):
  -ть -> strip (знать -> зна)     -ти -> strip (идти -> ид)
  -ет -> strip (знает -> зна)     -ёт -> strip
  -ут -> strip (знают -> зна)     -ют -> strip
  -ит -> strip                    -ат -> strip
  -ят -> strip                    -л -> strip (знал -> зна)
  -ла -> strip (знала -> зна)     -ло -> strip
  -ли -> strip (знали -> зна)     -ся -> strip + re-strip
  -шь -> strip                    -те -> strip
  -им -> strip                    -ите -> strip

Adjective patterns:
  -ый -> strip                    -ий -> strip
  -ая -> strip                    -яя -> strip
  -ое -> strip                    -ее -> strip
  -ые -> strip                    -ие -> strip
  -ого -> strip                   -его -> strip
  -ому -> strip                   -ему -> strip
  -ым -> strip                    -им -> strip
  -ую -> strip                    -юю -> strip
```

**Lookup strategy (cascading):**
1. Exact match on the tapped word as-is.
2. Exact match after lowercasing.
3. Stem match (strip suffix, compare stem to index keys).
4. If still no match, show "No translation available".

The stemmer is intentionally simple — it will produce false positives (over-stripping). This is acceptable because the popup just shows a hint, not a definitive answer.

### Issue 3: Translations are too verbose for a popup

**Problem:** The raw translation strings are extremely verbose. The worst cases:
- "run" has 8,801 characters of Russian translations
- "set" has 6,190 characters
- "go" has 4,600 characters
- Average for top-100 words: 500-2000 characters

A mobile popup can show at most 2-3 lines of text (~60-80 characters).

**Solution:** Extract only the first sense (first `/`-delimited segment) and truncate to first comma or first 50 characters.

During reverse index construction:
1. Take only the first sense: `translation.split("/").first()`.
2. Within that sense, take only the first alternative: `sense.split(",").first().trim()`.
3. Truncate to 50 characters if still too long.
4. This gives clean, concise popup text like:
   - "быть" instead of the full 400+ char entry for "be"
   - "иметь, обладать" instead of the full entry for "have"

The display translation is computed once during parsing and stored in the reverse index. No runtime truncation needed.

### Issue 4: No Italian coverage

**Problem:** The frequency dictionary only covers Russian-English. Italian lesson packs (`IT_WORD_ORDER_A1`) have no equivalent dictionary.

**Solution:** For Italian lessons, fall back to lesson-derived data only:
- Use `VocabEntry` pairs for the current lesson (already available).
- Use sentence-level hints from `SentenceCard.acceptedAnswers` for the current card.
- This is adequate for MVP — Italian support can be enhanced later with an Italian frequency dictionary if needed.

The `WordDictionary` class should accept an optional dictionary file. When none is available for the current language (e.g., Italian), it operates in lesson-data-only mode.

---

## 4. UI Implementation Plan

### Compose components needed

**4.1 Replace `Text` with `ClickableText`**

The current `CardPrompt` uses a plain `Text` composable. This needs to become `ClickableText` with an `AnnotatedString`:

```kotlin
// Pseudocode — not implementation
@Composable
private fun CardPrompt(state: TrainingUiState, onSpeak: () -> Unit) {
    val text = state.currentCard?.promptRu ?: "No cards"
    val annotatedText = buildAnnotatedString { /* word-level styling */ }

    ClickableText(
        text = annotatedText,
        onClick = { offset -> /* detect which word was tapped */ },
        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    )
}
```

**4.2 Word detection on tap**

Use `TextLayoutResult` to determine which word was tapped:
1. Pass `onTextLayout = { layoutResult = it }` to `ClickableText`.
2. On click, use `layoutResult.getBoundingBox(offset)` to find the character position.
3. Expand the character position to the full word boundaries (find nearest spaces/punctuation).

**4.3 Translation popup**

Use Compose Material 3 `TooltipBox` or a custom `Popup`:
- `TooltipBox` is lightweight and designed for this pattern.
- Shows the English translation above/below the tapped word.
- Dismisses on tap outside or after a short timeout (2-3 seconds).
- Alternative: `DropdownMenu`-style popup positioned at the tapped word.

**4.4 Visual feedback on tappable words**

- Optionally underline or subtly highlight each Russian word to indicate tappability.
- Use `SpanStyle` with a light underline (`textDecoration = TextDecoration.Underline`) for the entire prompt text.
- Or keep it plain and only show feedback on tap — simpler, less visual clutter.

### UI flow

1. User sees Russian sentence: "Я не знаю где мой ключ"
2. User taps "ключ"
3. A small popup appears above/below "ключ" showing: "key"
4. Popup dismisses after 3 seconds or when user taps elsewhere
5. No interruption to the training session — this is a passive aid

---

## 5. Data Flow

### Building the word dictionary

```
1. Parse FrequenceyVocabular.txt from assets (if available for current language)
    -> Read 12,527 tab-delimited lines
    -> Skip header line (line 1)
    -> For each entry: extract English word (col 2) + Russian translations (col 3)
    -> Truncate: take only first "/" segment, then first "," alternative, cap at 50 chars
    -> Split truncated Russian text into individual words by spaces
    -> Build reverse index: Map<String (Russian), List<String> (English)>
    -> Also add stemmed forms of each Russian word to the index

2. Supplement with VocabEntry data (per lesson)
    -> LessonStore.getVocabEntries(lessonId, languageId)
    -> For each VocabEntry: map targetText (Russian) -> nativeText (English)
    -> VocabEntry translations override frequency dictionary entries (higher priority)

3. Cache in memory for app session lifetime
    -> Rebuild only when language changes
    -> If no frequency dictionary available for language (e.g., Italian),
       operate in lesson-data-only mode
```

### Where to build the dictionary

**New class:** `data/WordDictionary.kt` — pure data layer.

Responsibilities:
- Accept an `InputStream` for the frequency dictionary file (or null for lesson-data-only mode).
- Accept a list of `VocabEntry` objects to supplement/override.
- Build and cache a `Map<String, WordTranslation>` (reverse index).
- Provide `lookup(word: String): WordTranslation?` with cascading match (exact -> lowercase -> stemmed).
- Truncate verbose translations to first sense during parsing.

**New class:** `data/RussianStemmer.kt` — pure utility, no dependencies.

Responsibilities:
- Provide `stem(word: String): String` — strip common Russian inflectional suffixes.
- Stateless, pure function. Called by `WordDictionary` during both index building and lookup.

**Data classes:**
```kotlin
data class WordTranslation(
    val word: String,                     // The Russian word looked up
    val translations: List<String>,       // English translations (best first)
    val source: TranslationSource         // VOCAB_ENTRY, FREQUENCY_DICT, STEM_MATCH
)

enum class TranslationSource {
    VOCAB_ENTRY,       // From lesson vocab CSV — highest priority, curated
    FREQUENCY_DICT,    // From FrequenceyVocabular.txt reverse index
    STEM_MATCH         // Matched via Russian suffix stripping (less certain)
}
```

### Integration with TrainingViewModel

- `WordDictionary` is created when the app starts (or on first dictionary access).
- The frequency dictionary file is read from assets via `context.assets.open("grammarmate/FrequenceyVocabular.txt")`.
- `VocabEntry` data is merged when lessons are loaded.
- Stored as a field in `TrainingViewModel`.
- `CardPrompt` receives a `lookupWord: (String) -> WordTranslation?` lambda from the ViewModel.
- The ViewModel does NOT add dictionary state to `TrainingUiState` — the lookup is synchronous and stateless from the UI's perspective.

### Parsing the frequency dictionary

The file `FrequenceyVocabular.txt` uses tab-delimited format:
```
Line 1: Header row — N\tWord\tTranslation\t[Mark]
Lines 2+: lineNumber\tenglishWord\trussianTranslations\t[empty or mark]
```

Parser steps:
1. Skip header line.
2. Split each line by `\t`.
3. Column index 1 = English word, column index 2 = Russian translations.
4. Truncate: take only the first `/`-delimited sense, then first `,`-delimited alternative.
5. Cap display translation at 50 characters.
6. Split truncated Russian text by spaces into individual words.
7. For each Russian word, add to reverse index: cleanedRussianWord -> englishWord.
8. Also add stemmed forms of each Russian word to the index.

---

## 6. Scope and Files Affected

| File | Change |
|------|--------|
| `app/src/main/assets/grammarmate/FrequenceyVocabular.txt` | **NEW** — copy of the frequency dictionary, bundled in APK assets |
| `data/WordDictionary.kt` | **NEW** — frequency dict parser, reverse index builder, lookup logic |
| `data/RussianStemmer.kt` | **NEW** — lightweight Russian suffix stripping for morphological matching |
| `ui/GrammarMateApp.kt` | Modify `CardPrompt` composable to use `ClickableText` + translation popup |
| `ui/TrainingViewModel.kt` | Add `WordDictionary` field, wire lookup lambda, update `CardPrompt` call site |

No changes to:
- Data models (`Models.kt`)
- Lesson loading (`LessonStore.kt`)
- Persistence layer
- Any other UI composables

### Bundling the frequency dictionary in the APK

The file `FrequenceyVocabular.txt` currently exists only at the repository root and is not part of the Android build. It must be placed in the assets directory so it is included in the APK and accessible at runtime via `AssetManager`.

**Step 1: Copy the file into assets**
```
Source:   <repo_root>/FrequenceyVocabular.txt
Target:   app/src/main/assets/grammarmate/FrequenceyVocabular.txt
```

This follows the existing convention — the app already reads assets from `grammarmate/packs/` and `grammarmate/config.yaml` via `context.assets.open(...)`.

**Step 2: Access at runtime**
```kotlin
// In ViewModel or initialization code
val inputStream = context.assets.open("grammarmate/FrequenceyVocabular.txt")
val dictionary = WordDictionary.fromFrequencyDict(inputStream)
```

**Step 3: Verify in build**
After placing the file, `./gradlew assembleDebug` will include it in the APK. Verify with:
```bash
unzip -l app/build/outputs/apk/debug/grammermate.apk | grep FrequenceyVocabular
```

**Size impact on APK:** ~1.5 MB (uncompressed text file). Android APK compression reduces this significantly (estimated ~400-600 KB in the final APK).

**Note:** The original file at `<repo_root>/FrequenceyVocabular.txt` can be kept as the source of truth and added to `.gitignore` for the copy in assets, or the assets copy can become the canonical location. Recommend moving the file (not copying) to avoid drift between versions.

---

## 7. Estimated Effort

| Task | Estimate |
|------|----------|
| Parse `FrequenceyVocabular.txt` + build reverse index + truncation | 2-3 hours |
| `RussianStemmer` suffix-stripping utility | 2-3 hours |
| `CardPrompt` refactor to `ClickableText` | 1-2 hours |
| Translation popup UI component | 2-3 hours |
| Integration + wiring in ViewModel | 1 hour |
| Testing (unit tests for stemmer, parser, lookup; manual UI testing) | 2-3 hours |
| **Total** | **10-15 hours** |

---

## 8. Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Frequency dictionary is English->Russian; reverse index may produce ambiguous mappings (one Russian word maps to many English words) | Show the top 2-3 most likely translations in the popup; prioritize VocabEntry-sourced translations |
| Word segmentation errors for Russian (hyphens, particles like "не", "ли") | Use simple space/punctuation split; Russian words are typically space-delimited |
| No translation found for tapped word (word not in frequency dict and not in vocab) | Show a graceful "No translation available" message; VocabEntry + 12.5K frequency entries cover most beginner words |
| `ClickableText` interferes with scrolling or other gestures | Use `pointerInput` modifier as fallback; test on scrollable training screen |
| Performance of parsing 12,527-entry file at startup | Parse once on background thread; cache in memory; ~1.5 MB file parses in <100ms |
| Russian morphology makes exact match fail | Basic stemming for common suffixes covers most beginner-level word forms |
| Frequency dictionary only covers Russian-English (not Italian) | For Italian lessons, fall back to lesson-derived data only; Italian dictionary can be added later |

---

## 9. Future Enhancements (out of scope for v1)

- Tap-and-hold for detailed word info (part of speech, conjugation table).
- Italian frequency dictionary for IT_WORD_ORDER packs.
- Morphological analyzer for full Russian inflection handling.
- Offline dictionary download as an optional feature.
- Word tap history for spaced repetition of vocabulary.
- Show word frequency rank in the popup (from the dictionary's rank column).
