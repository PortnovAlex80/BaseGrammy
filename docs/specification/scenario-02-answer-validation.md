# Scenario 02: Answer Validation Edge Cases

Traced against code on branch `feature/daily-cursors`, commit `eea9299`.

---

## Normalizer Pipeline (Actual Code)

**File:** `app/src/main/java/com/alexpo/grammermate/data/Normalization.kt`

```
Step 1: trim + collapse \s+ to single space
Step 2: replace \b(\d{1,2}):\d{2}\b with $1  (strip time minutes)
Step 3: lowercase
Step 4: iterate characters — skip .,?!"<>;:()[]{}  — keep everything else including hyphens, apostrophes, accented chars
Step 5: collapse \s+ again + trim
```

**Key properties:**
- Apostrophes are kept (e.g. `l'albero` stays `l'albero`).
- Accented/diacritic characters are kept as-is (no NFD stripping).
- Hyphens are kept.
- Empty/whitespace-only input normalizes to `""`.

---

## Validation Paths

Three distinct code paths perform answer validation:

### Path A: Standard Training (`TrainingViewModel.submitAnswer`)

**File:** `ui/TrainingViewModel.kt:617-841`

```
submitAnswer()
  guard: sessionState != ACTIVE -> return SubmitResult(false, false)
  guard: inputText.isBlank() && !testMode -> return SubmitResult(false, false)
  card = currentCard()
  normalizedInput = Normalizer.normalize(state.inputText)
  accepted = testMode || card.acceptedAnswers.any { Normalizer.normalize(it) == normalizedInput }
  if accepted: playSuccessTone(), recordCardShowForMastery(card), navigate
  else: playErrorTone(), increment incorrect, hint after 3 attempts
  return SubmitResult(accepted, hintShown)
```

### Path B: Verb Drill & Daily Practice (CardSessionContract adapters)

**Files:**
- `ui/VerbDrillCardSessionProvider.kt:185-227` (`submitAnswerWithInput`)
- `feature/daily/DailyPracticeSessionProvider.kt:159-206` (`submitAnswer`)

Both follow the same pattern:
```
isCorrect = Normalizer.normalize(input) == Normalizer.normalize(card.answer)
  -- OR --
isCorrect = card.acceptedAnswers.any { Normalizer.normalize(input) == Normalizer.normalize(it) }
```

Retry/hint flow: wrong < 3 -> show inline error; wrong >= 3 -> auto-show hint; hint blocks further submits.

### Path C: Vocab Drill Voice Match

**File:** `ui/VocabDrillViewModel.kt:370-432` (`handleVoiceResult`)

```
validAnswers = expectedRaw.split("/").map { normalizeForMatch(it) }
normalizedRecognized = normalizeForMatch(recognizedText)
isCorrect = validAnswers.any { valid ->
    valid.isNotBlank() && (normalizedRecognized == valid || normalizedRecognized.contains(valid))
}
```

Uses its own `normalizeForMatch()` (line 447-449): `text.trim().lowercase().replace(Regex("[.!?,;:]$"), "")` — only strips trailing punctuation, does not use `Normalizer.normalize()`.

---

## Test Case Traces

### Test 1: Exact Match — "io mangio" vs "io mangio"

| Aspect | Detail |
|--------|--------|
| **Code path** | Path A: `TrainingViewModel.kt:622-623` |
| **Normalization** | `Normalizer.normalize("io mangio")` -> `"io mangio"` (no changes needed) |
| **Comparison** | `Normalizer.normalize("io mangio") == "io mangio"` -> true |
| **Expected (spec)** | Correct |
| **Actual (code)** | Correct |
| **Discrepancy** | None |

### Test 2: Case Mismatch — "Io Mangio" vs "io mangio"

| Aspect | Detail |
|--------|--------|
| **Code path** | Path A: `TrainingViewModel.kt:622-623` |
| **Normalization** | `Normalizer.normalize("Io Mangio")` -> step 3 lowercase -> `"io mangio"` |
| **Comparison** | `"io mangio" == "io mangio"` -> true |
| **Expected (spec)** | Correct |
| **Actual (code)** | Correct |
| **Discrepancy** | None |

### Test 3: Punctuation — "io mangio!" vs "io mangio"

| Aspect | Detail |
|--------|--------|
| **Code path** | Path A: `TrainingViewModel.kt:622-623` |
| **Normalization** | `Normalizer.normalize("io mangio!")` -> step 4 removes `!` -> `"io mangio"` |
| **Comparison** | `"io mangio" == "io mangio"` -> true |
| **Expected (spec)** | Correct |
| **Actual (code)** | Correct |
| **Discrepancy** | None |

### Test 4: Multiple Accepted Answers — card has "io mangio+io mangio la mela"

Card data uses `+` as separator in CSV. At import time, `CsvParser` splits on `+` into `acceptedAnswers: List<String>` = `["io mangio", "io mangio la mela"]`.

| Aspect | Detail |
|--------|--------|
| **Code path** | Path A: `TrainingViewModel.kt:623` — `card.acceptedAnswers.any { ... }` |
| **Input "io mangio"** | First answer normalizes to `"io mangio"` -> matches -> CORRECT |
| **Input "io mangio la mela"** | Second answer normalizes to `"io mangio la mela"` -> matches -> CORRECT |
| **Input "io mangio una mela"** | Neither matches -> INCORRECT |
| **Expected (spec)** | Any accepted answer matches; 8.4.4 says compare against each |
| **Actual (code)** | `.any {}` iterates all accepted answers; first match wins |
| **Discrepancy** | None |

### Test 5: Partial Match — "mangio" vs "io mangio"

| Aspect | Detail |
|--------|--------|
| **Code path** | Path A: `TrainingViewModel.kt:622-623` |
| **Normalization** | `Normalizer.normalize("mangio")` -> `"mangio"` |
| **Comparison** | `"mangio" == "io mangio"` -> false |
| **Expected (spec)** | Incorrect — normalization uses exact string equality, not substring |
| **Actual (code)** | Incorrect |
| **Discrepancy** | None. The Normalizer compares full strings. Partial matches do not count. |

**Exception:** Vocab Drill voice matching (Path C) uses `contains()` — partial match IS accepted there.

### Test 6: Italian Diacritics — "perche" vs "perche" (e with accent)

| Aspect | Detail |
|--------|--------|
| **Code path** | Path A: `TrainingViewModel.kt:622-623` |
| **Normalization** | `Normalizer.normalize("perche")` -> `"perche"` (accent preserved) |
| **Comparison** | `"perche" != "perche"` (different Unicode codepoints: `e` U+0065 vs `è` U+00E8) |
| **Expected (spec 3.5)** | Spec says "accent marks/diacritics are treated as distinct. This is intentional: correct accent usage is part of language mastery." |
| **Expected (spec 12.3.2)** | Spec says "strip diacritical marks (NFD decomposition, remove combining characters)" — which would make them match |
| **Actual (code)** | INCORRECT — diacritics are NOT stripped |
| **Discrepancy** | **YES — CRITICAL.** Spec 12.3.2 and 12.7.5 claim the normalizer "strips diacritical marks" via NFD decomposition, but the actual `Normalizer.normalize()` does NOT perform this step. This creates a contradiction between spec 3.5 (which correctly documents the code behavior) and spec 12 (which describes a feature that does not exist). Users typing without accent marks will fail on accented answers. |

### Test 7: Hyphenated — "dacci-lo" vs "daccilo"

| Aspect | Detail |
|--------|--------|
| **Code path** | Path A: `TrainingViewModel.kt:622-623` |
| **Normalization** | `Normalizer.normalize("dacci-lo")` -> `"dacci-lo"` (hyphen kept) |
| **Comparison** | `"dacci-lo" != "daccilo"` -> false |
| **Expected (spec)** | Incorrect — hyphens are explicitly preserved by design (spec 3.5) |
| **Actual (code)** | Incorrect |
| **Discrepancy** | None. This is by design. Hyphenated and non-hyphenated forms are different answers. Both must be listed in `acceptedAnswers` for the card if both should be accepted. |

### Test 8: Time Expression — "alle 3:15" vs "alle tre e quindici"

| Aspect | Detail |
|--------|--------|
| **Code path** | Path A: `TrainingViewModel.kt:622-623` |
| **Normalization** | `Normalizer.normalize("alle 3:15")` -> step 2 regex replaces `3:15` -> `"alle 3"` |
| **Comparison** | `"alle 3" != "alle tre e quindici"` -> false |
| **Expected (spec)** | Time normalization only strips minutes, does NOT convert digits to words. Two fundamentally different answer formats. |
| **Actual (code)** | Incorrect |
| **Discrepancy** | None. The time normalization is intentionally limited: `"8:30"` -> `"8"`. It does not perform digit-to-word conversion. Both numeric and textual forms must be listed in `acceptedAnswers` if both should be accepted. |

### Test 9: Empty Answer — "" (empty string)

| Aspect | Detail |
|--------|--------|
| **Code path** | Path A: `TrainingViewModel.kt:620` |
| **Guard check** | `state.inputText.isBlank() && !state.testMode` -> true |
| **Result** | Returns `SubmitResult(false, false)` immediately — no validation performed |
| **Expected (spec)** | Empty answer should be rejected |
| **Actual (code)** | Rejected (early return, no sound played, no state change) |
| **Discrepancy** | None |

For Path B (VerbDrill/DailyPractice adapters): the adapters do not have an explicit empty-input guard. `Normalizer.normalize("")` returns `""`. The comparison `"" == Normalizer.normalize(card.answer)` will be false (unless the card answer is also empty, which should never happen). So empty input is effectively rejected but after normalization, not via an early guard.

### Test 10: Voice Input with Trailing Noise — "io mangio [pause] [noise]"

| Aspect | Detail |
|--------|--------|
| **Voice input source** | Android `RecognizerIntent.EXTRA_RESULTS` returns the top match as a single string. Noise words may be included. |
| **Standard training (GrammarMateApp.kt:2920-2929)** | `spoken = matches?.firstOrNull()` -> `onInputChange(spoken)` -> `onSubmit()`. The raw ASR text goes directly to `submitAnswer()`. No noise filtering. |
| **CardSessionContract path (TrainingCardSession.kt:468-473)** | `spoken = matches?.firstOrNull()` -> `contract.onVoiceInputResult(spoken)` -> delegates to `onInputChanged(spoken)`. Text appears in input field, user taps "Check". |
| **Verb Drill / Daily Practice** | Custom `LaunchedEffect` auto-launches voice, result goes to `provider.submitAnswerWithInput(spoken)`. Immediate validation. |
| **Normalization** | `Normalizer.normalize("io mangio [pause] [noise]")` — the `[`, `]` brackets ARE removed, but the words "pause" and "noise" remain. Result: `"io mangio pause noise"` which will NOT match `"io mangio"`. |
| **Expected (spec)** | No spec for noise handling |
| **Actual (code)** | Incorrect — ASR noise words cause failure |
| **Discrepancy** | No spec/code disagreement, but a practical gap. The system relies on ASR accuracy. Bracket characters ARE stripped, but the words inside them remain. |

### Test 11: Word Bank Mode — Mastery Counting

| Aspect | Detail |
|--------|--------|
| **Code path** | `TrainingViewModel.kt:623` accepts answer -> line 632 `recordCardShowForMastery(card)` -> line 2896-2915 |
| **Guard** | `recordCardShowForMastery` line 2905-2909: `if (isWordBankMode) return` |
| **Result** | WORD_BANK answer accepted (Correct/Incorrect logic same as other modes) but mastery is NOT recorded |
| **Expected (spec 8.7.3)** | WORD_BANK does NOT count for mastery, does NOT grow flowers |
| **Actual (code)** | Correct answer accepted, counters incremented, but no mastery record |
| **Discrepancy** | None |

Additional note for Daily Practice: `DailyPracticeSessionProvider.nextCard()` line 242 checks `_inputMode != InputMode.WORD_BANK` before calling `onCardAdvanced`, and `cancelDailySession` only advances the cursor if all TRANSLATE + VERBS cards were VOICE/KEYBOARD.

### Test 12: Keyboard Mode — Mastery Counting

| Aspect | Detail |
|--------|--------|
| **Code path** | `TrainingViewModel.kt:623` accepts answer -> line 632 `recordCardShowForMastery(card)` |
| **Guard** | `inputMode == KEYBOARD` does NOT trigger the WORD_BANK early return |
| **Result** | Mastery IS recorded: `masteryStore.recordCardShow(lessonId, languageId, card.id)` |
| **Expected (spec 8.7.3)** | KEYBOARD counts for mastery, grows flowers |
| **Actual (code)** | Mastery recorded, flower growth happens on next `refreshFlowerStates()` |
| **Discrepancy** | None |

### Test 13: Verb Conjugation — "mangiato" expected, user types "mangiato"

This traces through the Verb Drill path (Path B).

| Aspect | Detail |
|--------|--------|
| **Code path** | `VerbDrillCardSessionProvider.submitAnswerWithInput("mangiato")` at line 185 |
| **Card** | `VerbDrillCard` with `answer = "mangiato"`, `acceptedAnswers = listOf("mangiato")` |
| **Normalization** | Both sides normalize to `"mangiato"` |
| **Comparison** | `"mangiato" == "mangiato"` -> true |
| **Result** | `pendingCard = card`, `pendingAnswerResult = AnswerResult(correct=true, displayAnswer="mangiato")` |
| **Advancement** | On `nextCard()` (line 243-273): `hadPending = true` -> `viewModel.submitCorrectAnswer()` |
| **Progress update** | `VerbDrillViewModel.submitCorrectAnswer()` records correct count and advances session index |
| **Expected (spec)** | Correct answer -> progress recorded in VerbDrillSessionState |
| **Actual (code)** | Correct |
| **Discrepancy** | None |

For Daily Practice verb block: `DailyPracticeSessionProvider` similarly normalizes and compares. On `nextCard()`, `onCardAdvanced` is called with the task, which triggers `persistDailyVerbProgress(card)` -> `VerbDrillStore.upsertComboProgress()` updating `everShownCardIds` and `todayShownCardIds`.

### Test 14: Same Answer Submitted Twice — Retry/Hint Logic

#### Path A: Standard Training (`TrainingViewModel.submitAnswer`)

| Aspect | Detail |
|--------|--------|
| **First wrong** | `incorrectAttemptsForCard` -> 1, `lastResult = false`, `answerText = null`, auto-trigger voice if VOICE mode |
| **Second wrong** | `incorrectAttemptsForCard` -> 2, same as above |
| **Third wrong** | `incorrectAttemptsForCard` -> 3 -> hint shown: `answerText = card.acceptedAnswers.joinToString(" / ")`, `sessionState = HINT_SHOWN`, timer paused, `incorrectAttemptsForCard` reset to 0 |
| **After hint** | User sees accepted answers. `SessionState.HINT_SHOWN` — no more submissions until `nextCard()` or `togglePause()` |
| **Expected (spec 8.4.3)** | After 3 attempts: show hint, pause timer, reset attempt counter |
| **Actual (code)** | Matches spec |
| **Discrepancy** | None |

#### Path B: Verb Drill / Daily Practice (retry/hint flow)

| Aspect | Detail |
|--------|--------|
| **First wrong** | `incorrectAttempts = 1`, `showIncorrectFeedback = true`, `remainingAttempts = 2` |
| **Second wrong** | `incorrectAttempts = 2`, `showIncorrectFeedback = true`, `remainingAttempts = 1` |
| **Third wrong** | `incorrectAttempts = 3` -> `hintAnswer = card.answer`, `isPaused = true`, `showIncorrectFeedback = false` |
| **After hint** | `hintAnswer != null` blocks further `submitAnswerWithInput()` calls (line 191: returns null) |
| **Recovery** | User can type to clear hint (clears state, resets attempts), or press Play/Next to advance |
| **Expected (spec 12.3.3)** | 3 wrong -> auto-show hint, pause; manual eye button -> same; nextCard advances |
| **Actual (code)** | Matches spec |
| **Discrepancy** | None |

---

## Summary of Discrepancies

### CRITICAL: Spec 12 vs Spec 3.5 vs Code — Diacritic Stripping

| Source | Claim |
|--------|-------|
| **Spec 3.5 (Normalization)** | "Accent marks / diacritics are treated as distinct. This is intentional." |
| **Spec 12.3.2 (Card Lifecycle)** | "strip diacritics" as part of normalization |
| **Spec 12.7.5 (Answer Normalization)** | "Strip diacritical marks (NFD decomposition, remove combining characters)" |
| **Actual code (`Normalization.kt`)** | NO diacritic stripping. Accented characters pass through unchanged. |
| **Test (`NormalizerTest.kt:206-211`)** | Tests explicitly verify `"cafe"` stays `"cafe"` after normalization — confirming diacritics are preserved. |

**Impact:** In Italian, users who type `"perche"` (without accent) will be marked WRONG when the expected answer is `"perche"` (with grave accent). This is a significant usability issue for mobile users who may not have Italian keyboard layouts.

**Resolution needed:** Either:
1. Add NFD stripping to `Normalizer.normalize()` and update spec 3.5 to match, OR
2. Remove the diacritic-stripping claims from spec 12.3.2 and 12.7.5, accepting that accented and unaccented forms are distinct, OR
3. Ensure all lesson content includes both accented and unaccented variants in `acceptedAnswers`.

### MINOR: Vocab Drill uses different normalization

| Aspect | Detail |
|--------|--------|
| **File** | `VocabDrillViewModel.kt:447-449` |
| **Method** | `normalizeForMatch()` — only `trim().lowercase().replace(Regex("[.!?,;:]$"), "")` |
| **Difference** | Only strips trailing punctuation, not leading or embedded. Does not collapse whitespace. Does not strip time colons. |
| **Impact** | Vocab drill voice matching is more lenient than other paths (uses `contains()` for partial match) but also less normalized (punctuation in the middle of text is kept). |
| **Recommendation** | Consider using `Normalizer.normalize()` for consistency, or document the intentional difference. |

### MINOR: Path B adapters lack empty-input guard

| Aspect | Detail |
|--------|--------|
| **Files** | `VerbDrillCardSessionProvider.kt:185`, `DailyPracticeSessionProvider.kt:159` |
| **Issue** | No explicit check for blank input before normalization. `Normalizer.normalize("")` returns `""`, which will not match any real answer, so the result is correct but the code path includes unnecessary processing and increments `incorrectAttempts`. |
| **Impact** | Empty submission counts as a wrong attempt in Verb Drill / Daily Practice, consuming one of the 3 allowed attempts. In standard training, empty input is rejected before reaching the comparison. |
| **Recommendation** | Add `if (input.isBlank()) return null` at the top of `submitAnswerWithInput()` in both adapters for consistency. |
