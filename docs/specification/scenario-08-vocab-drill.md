# Scenario 08: Standalone Vocab Drill Session Trace

Trace of the standalone Vocab Drill flow: user opens vocab drill, selects filters, practices flashcards, completes session.

---

## Step 1: User taps vocab drill tile -> VocabDrillViewModel init -> word loading

**Code path:**

1. `GrammarMateApp.kt:469` -- `val vocabDrillVm = viewModel<VocabDrillViewModel>()` creates/retrieves the ViewModel.
2. `GrammarMateApp.kt:470-475` -- Calls `reloadForPack(packId, languageId)` or `reloadForLanguage(languageId)` depending on whether an active pack exists.
3. `VocabDrillViewModel.kt:41-44` -- `init` block sets `isLoading = false`. Does NOT auto-load. Waits for explicit reload call.
4. `VocabDrillViewModel.kt:50-59` -- `reloadForPack(packId, languageId)`:
   - Idempotency check: returns immediately if same pack + language + words already loaded.
   - Sets `activePackId = packId`.
   - Recreates `WordMasteryStore(getApplication(), packId = packId)`.
   - Sets `isLoading = true`.
   - Launches coroutine to `loadWords(languageId)`.
5. `VocabDrillViewModel.kt:76-135` -- `loadWords()`:
   - Gets vocab drill CSV files from `lessonStore.getVocabDrillFiles(pack, lang)`.
   - Parses each file via `ItalianDrillVocabParser.parse(stream, fileName)`.
   - Derives POS from filename: removes `{lang}_` prefix, `drill_` prefix, `.csv` suffix.
   - Constructs `VocabWord` with ID `{pos}_{rank}_{word}`.
   - Sorts all words by `rank`.
   - Loads mastery map from `masteryStore.loadAll()`.
   - Extracts distinct POS list.
   - Calls `updateCounts()`.

**LessonStore.getVocabDrillFiles** (`LessonStore.kt:854-860`): Looks in `grammarmate/drills/{packId}/vocab_drill/`, filters by `{languageId}_` prefix and `.csv` extension.

**Spec match:** Matches spec 11 Section 11.5.2-11.5.4 exactly.

---

## Step 2: Filter selection -- direction, POS, frequency

**Code path:**

- `VocabDrillScreen.kt:189-199` -- Direction FilterChips: "IT -> RU" and "RU -> IT". Calls `onSetDirection(VocabDrillDirection.IT_TO_RU)` or `RU_TO_IT`.
- `VocabDrillViewModel.kt:335-342` -- `setDirection()` updates `drillDirection` in state AND in the active session if one exists.
- `VocabDrillScreen.kt:203-226` -- Voice input toggle: Switch calls `onSetVoiceMode(enabled)`.
- `VocabDrillViewModel.kt:345-352` -- `setVoiceMode()` updates `voiceModeEnabled` in state AND active session.
- `VocabDrillScreen.kt:229-251` -- POS FilterChips: "All" (null) + one per `availablePos`. Labels: Nouns, Verbs, Adj., Adv.
- `VocabDrillViewModel.kt:140-143` -- `selectPos(pos)` updates `selectedPos`, calls `updateCounts()`.
- `VocabDrillScreen.kt:253-271` -- Frequency chips: Top 100, Top 500, Top 1000, All.
- `VocabDrillViewModel.kt:148-151` -- `setRankRange(min, max)` updates `rankMin`/`rankMax`, calls `updateCounts()`.

**updateCounts** (`VocabDrillViewModel.kt:153-181`):
- Filters `allWords` by `selectedPos` and `rankMin`/`rankMax`.
- Counts "due" words: `mastery == null || nextReviewDateMs <= now || lastReviewDateMs == 0`.
- Counts mastered: iterates `masteryMap` for `isLearned == true`, extracts POS from word ID prefix.

**Spec match:** Matches spec 11 Section 11.6.2 and 11.5.6. Default direction is IT_TO_RU. Default rank range is 0 to Int.MAX_VALUE ("All"). No discrepancy.

---

## Step 3: Card selection algorithm

**Code path:** `VocabDrillViewModel.kt:195-218` -- `startSession()`:

1. Gets `now = System.currentTimeMillis()`.
2. Gets `filteredWords()` -- filtered by POS and rank range.
3. Maps each filtered word to a `VocabDrillCard` if due: `mastery == null -> new WordMasteryState(word.id)`, then checks `mastery.nextReviewDateMs <= now || mastery.lastReviewDateMs == 0L`.
4. **Sorts by `word.rank` ascending** (lowest rank = most frequent first).
5. Takes up to **10** cards.
6. If no due cards: sets `session = null` and returns.
7. Creates `VocabDrillSessionState` with cards, direction, and voice mode.

**Spec match:** Spec 11 Section 11.5.5 describes the same algorithm: filter, check due, sort by rank, take 10. No discrepancy.

**Priority logic observation:** The standalone drill does NOT use the three-bucket "overdue > new > fallback" priority system used by daily practice (spec 11 Section 11.7.2). Instead it simply filters all due words and sorts by rank. This is by design -- the spec documents two different algorithms for the two contexts.

---

## Step 4: Card front display

**Code path:** `VocabDrillScreen.kt:564-680` -- `VocabDrillCardFront()`:

- **POS badge** (`VocabDrillScreen.kt:952-971`): Colored chip based on POS. Nouns=primaryContainer, Verbs=secondaryContainer, Adj.=tertiaryContainer, Adv.=errorContainer.
- **Rank badge** (`VocabDrillScreen.kt:974-988`): "#N" chip.
- **Main word** (lines 606-615):
  - `IT_TO_RU`: `card.word.word` (Italian word, e.g. "casa")
  - `RU_TO_IT`: `card.word.meaningRu ?: "?"` (Russian meaning, e.g. "дом/жилище/семья")
  - Displayed at 32sp, bold, centered.
- **TTS button** (lines 618-645): Only shown for `IT_TO_RU` direction. States: SPEAKING (blue), INITIALIZING (spinner), ERROR (red X), else IDLE (standard icon).
- **Voice input area** (lines 650-677):
  - If `!voiceCompleted`: "Tap to speak" + large 72dp mic button.
  - If `voiceCompleted`: `VoiceResultFeedback` card.

**Card background color** (lines 579-587):
- Voice CORRECT: light green tint (`#E8F5E9` at 0.7 alpha).
- Voice WRONG: light red tint (`#FFEBEE` at 0.7 alpha).
- Default: surfaceVariant at 0.7 alpha.

**Spec match:** Matches spec 11 Section 11.6.4. No discrepancy.

---

## Step 5: Card flip -> back side

**Code path:** `VocabDrillViewModel.kt:223-228` -- `flipCard()` toggles `session.isFlipped`.

**Back side** (`VocabDrillScreen.kt:684-850`):

**IT_TO_RU direction (lines 704-739):**
1. POS badge + Italian word (20sp, semibold) + TTS button.
2. Russian translation (`meaningRu`) at 18sp, medium, primary color.

**RU_TO_IT direction (lines 741-777):**
1. POS badge + Russian meaning (16sp, 0.7 alpha).
2. Italian word (24sp, bold, primary color) + TTS button.

**Both directions (lines 780-848):**
3. **Forms card** (if `forms` non-empty): "Forms" label + 4-column grid (m sg, f sg, m pl, f pl).
4. **Collocations** (if non-empty): "Collocations" label + up to 5 phrases + "+N more" overflow.
5. **Mastery indicator**: `"Step {step+1}/9"` or `"Learned"` when `step >= 9`.

**Auto-flip** (`VocabDrillScreen.kt:369-374`): `LaunchedEffect` triggers `onFlip()` after 800ms delay when `voiceCompleted` becomes true.

**Spec match:** Matches spec 11 Section 11.6.5. No discrepancy.

---

## Step 6: Anki rating buttons (4 buttons) -> step delta

**Code path:** `VocabDrillViewModel.kt:233-274`:

| Rating  | Delta | New Step                        | Button Label Interval      |
|---------|-------|---------------------------------|----------------------------|
| AGAIN   | -100  | Always 0 (hardcoded reset)     | "<1m"                      |
| HARD    | 0     | `(current + 0) clamped [0..9]` | `ladder[currentStep]`d     |
| GOOD    | +1    | `(current + 1) clamped [0..9]` | `ladder[currentStep+1]`d   |
| EASY    | +2    | `(current + 2) clamped [0..9]` | `ladder[currentStep+2]`d   |

**Button display intervals** (`VocabDrillScreen.kt:449-515`):
- Again: hardcoded "<1m" (line 466)
- Hard: `ladder[currentStep]` days (line 479)
- Good: `ladder[(currentStep + 1).coerceAtMost(9)]` days (line 498)
- Easy: `ladder[(currentStep + 2).coerceAtMost(9)]` days (line 512)

The interval ladder is `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` days (indices 0-9).

**Spec match:** Matches spec 11 Section 11.5.8 and 11.6.7. No discrepancy.

---

## Step 7: Voice input -> handleVoiceResult -> normalizeForMatch -> matching

**Code path:**

1. **Voice launcher** (`VocabDrillScreen.kt:92-107`):
   - Language tag: `IT_TO_RU -> "ru-RU"`, `RU_TO_IT -> "it-IT"`.
   - Uses `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`.
   - On result: calls `viewModel.handleVoiceResult(spoken)`.

2. **Auto-launch** (`VocabDrillScreen.kt:377-385`):
   - `LaunchedEffect(session.currentIndex, voiceModeEnabled)`: if voice mode on + not flipped + not completed + not active, launches after 500ms delay.

3. **handleVoiceResult** (`VocabDrillViewModel.kt:370-432`):
   - Gets expected answer based on direction:
     - `IT_TO_RU`: `card.word.meaningRu` (e.g. "быть/являться")
     - `RU_TO_IT`: `card.word.word` (e.g. "essere")
   - Splits expected by "/" to get valid answers list.
   - Normalizes both recognized and expected via `normalizeForMatch()`.

4. **normalizeForMatch** (`VocabDrillViewModel.kt:447-449`):
   ```kotlin
   text.trim().lowercase().replace(Regex("[.!?,;:]$"), "")
   ```
   Strips trailing punctuation only.

5. **Match logic** (lines 388-393):
   ```kotlin
   val isCorrect = validAnswers.any { valid ->
       valid.isNotBlank() && (
           normalizedRecognized == valid ||
           normalizedRecognized.contains(valid)
       )
   }
   ```
   Match if recognized text equals OR contains any valid answer.

6. **Result handling** (lines 395-432):
   - Correct: sets `CORRECT`, `voiceCompleted = true`.
   - Wrong: increments `voiceAttempts`. If >= 3: sets `WRONG`, `voiceCompleted = true`. Else: sets `WRONG`, `voiceCompleted = false` (allows retry).
   - Max attempts: 3.

**Spec match:** Matches spec 11 Section 11.5.9. No discrepancy.

---

## Step 8: TTS playback

**Code path:** `VocabDrillViewModel.kt:308-330`:

- `speakTts(text, speed = 0.67f)`:
  - If TTS not ready or wrong language, initializes with `ttsEngine.initialize(langId)`.
  - Calls `ttsEngine.speak(text, languageId = langId, speed = speed)`.
- `stopTts()` calls `ttsEngine.stop()`.
- Released in `onCleared()`.

**TTS button locations:**
- Card front: only `IT_TO_RU` direction (`VocabDrillScreen.kt:618`).
- Card back: both directions (lines 722, 770).

**Trigger:** Manual tap only. No auto-playback on card display.

**Spec match:** Matches spec 11 Section 11.5.11 and US-11.8. Speed is 0.67f. No discrepancy.

---

## Step 9: isLearned threshold -- LEARNED_THRESHOLD = 3 vs spec 01 says 9

**Code (VocabDrillViewModel.kt:258-259):**
```kotlin
val LEARNED_THRESHOLD = 3  // words at step 3+ are considered learned
val isLearned = newStepIndex >= LEARNED_THRESHOLD
```

**Same in TrainingViewModel (line 1709-1710):**
```kotlin
val LEARNED_THRESHOLD = 3
val isLearned = newStepIndex >= LEARNED_THRESHOLD
```

**VocabWord.kt comment (line 28):**
```kotlin
val isLearned: Boolean = false  // reached the last interval step (9)
```

**Spec documents disagree:**
- `01-models-and-state.md` line 392: "true when intervalStepIndex == 9 (reached the last ladder step)."
- `02-data-stores.md` line 341: "isLearned is set to true when the word reaches the last interval step (step 9)."
- `11-vocab-drill.md` line 95: "reached step >= 3 (LEARNED_THRESHOLD)."
- `18-learning-methodology.md` line 451: "isLearned is true when intervalStepIndex reaches 9."
- `arch-audit-spec-vs-code.md`: Already documented this discrepancy as CRITICAL-2.

**Verdict:** The actual code uses threshold 3 in both ViewModels. The `VocabWord.kt` comment is stale (says step 9). Specs 01, 02, and 18 are incorrect. Only spec 11 correctly documents the threshold. The mastery indicator on the card back shows "Learned" when `step >= 9`, which contradicts the actual `isLearned` check at step >= 3 -- see Step 10 for details.

---

## Step 10: Completion screen -> stats shown -> mastery percentage

**Code path:** `VocabDrillScreen.kt:1009-1103` -- `VocabDrillCompletionScreen()`:

- Title: "Perfect!" if `correctCount == cards.size`, else "Done!".
- Stats animate in after 800ms delay (`LaunchedEffect(Unit) { delay(800L); showStats = true }`).
- Shows `correctCount` (primary color) and `incorrectCount` (error color) side by side.
- Shows "{N} words reviewed" subtitle.
- Buttons: "Exit" (returns to selection screen) and "Continue" (calls `startSession()` for next batch).

**Mastery progress bar on selection screen** (`VocabDrillScreen.kt:318-324`):
```kotlin
val learned = state.totalCount - state.dueCount
learned.toFloat() / state.totalCount.toFloat()
```
This calculates progress as `(totalCount - dueCount) / totalCount`. This is NOT the same as `masteredCount / totalCount`. It counts non-due words as "progress", which includes words whose next review is in the future but haven't reached the learned threshold.

**Card back mastery indicator** (`VocabDrillScreen.kt:840-847`):
```kotlin
val step = card.mastery.intervalStepIndex
val maxStep = 9
val stepLabel = if (step >= maxStep) "Learned" else "Step ${step + 1}/$maxStep"
```
Shows "Learned" only when `step >= 9`, but `isLearned` is set at `step >= 3`. So a word at step 5 would be flagged `isLearned = true` in the data model but the card back would show "Step 6/9" rather than "Learned".

**DISCREPANCY:** The mastery indicator label uses a different threshold (step >= 9) than the actual `isLearned` field (step >= 3). The spec 11 Section 11.6.5 line 509 says "Step {step+1}/9 or 'Learned' (when step >= 9)", which matches the UI code but not the `isLearned` logic. The "Learned" label on the card back and the `isLearned` flag in the data model represent different concepts.

---

## Step 11: reloadForPack(packId) -> pack switching -> what resets

**Code path:** `VocabDrillViewModel.kt:50-59`:

1. Idempotency: if same pack + language + non-empty words, returns immediately (no reload).
2. Sets `activePackId = packId`.
3. **Recreates `WordMasteryStore`**: `masteryStore = WordMasteryStore(getApplication(), packId = packId)` -- new store instance pointing to the new pack's YAML file.
4. Sets `isLoading = true`.
5. Launches coroutine to `loadWords(languageId)` which:
   - Clears and rebuilds `allWords` from the new pack's CSV files.
   - Loads new `masteryMap` from the new pack's mastery file.
   - Updates `availablePos`, `dueCount`, `totalCount`, `masteredCount`.

**What resets on pack switch:**
- `allWords` -- rebuilt from new pack.
- `masteryMap` -- loaded from new pack's `word_mastery.yaml`.
- `availablePos` -- recalculated.
- `dueCount`, `totalCount`, `masteredCount` -- recalculated.
- `activePackId` -- updated.

**What does NOT reset:**
- `selectedPos` -- user's filter selection persists.
- `rankMin`/`rankMax` -- user's filter selection persists.
- `drillDirection` -- persists.
- `voiceModeEnabled` -- persists.
- Active `session` -- NOT explicitly cleared on reload. However, the idempotency check means reload is skipped if same pack+language, so this is safe for normal navigation.

**Spec match:** Matches spec 11 Section 11.5.3 and US-11.14. No discrepancy.

---

## Step 12: Empty word set (all learned) -> what happens

**Code paths:**

1. **All words learned but not all due:** `startSession()` finds due cards and creates a session. No issue.

2. **No due words at all:** `startSession()` (`VocabDrillViewModel.kt:206-208`):
   ```kotlin
   if (dueCards.isEmpty()) {
       _uiState.update { it.copy(session = null) }
       return
   }
   ```
   Sets `session = null`. The selection screen remains visible.

3. **Selection screen behavior when no due words:** `VocabDrillScreen.kt:342-344`:
   - Start button shows "No due words" and is disabled (`enabled = state.dueCount > 0`).
   - The dueCount displays "Due: 0 / {totalCount}".
   - Progress bar shows `(totalCount - 0) / totalCount = 1.0` (100%), which is correct.

4. **No words loaded at all (empty pack):** `VocabDrillScreen.kt:327-334`:
   - Shows "No words loaded" text instead of the stats card.
   - Start button disabled.

**Spec match:** Matches spec 11 US-11.1 acceptance criteria: "Start button shows due count and is disabled when no words are due." No discrepancy.

---

## Step 13: Words with multiple forms (noun: singular/plural, adjective: masc/fem)

**Code path:**

Forms are stored in `VocabWord.forms: Map<String, String>`.

**Nouns:** The parser (`ItalianDrillVocabParser.parseRankWordCollocations`) does NOT extract gender forms from noun CSV. Noun CSV format is `rank,noun,collocations,ru` -- no form columns. Nouns have `forms = emptyMap()`.

**Adjectives** (`ItalianDrillVocabParser.kt:127-173`): Parses `msg`, `fsg`, `mpl`, `fpl` columns into the forms map.

**Numbers** (`ItalianDrillVocabParser.kt:218-261`): Parses `form_m`, `form_f` into forms.

**Pronouns** (`ItalianDrillVocabParser.kt:268-317`): Parses `form_sg_m`, `form_sg_f`, `form_pl_m`, `form_pl_f` into forms.

**Display on card back** (`VocabDrillScreen.kt:779-808`):
- Forms section is shown when `forms.isNotEmpty()`.
- Displays a 4-column grid: `FormItem("m sg", forms["msg"])`, `FormItem("f sg", forms["fsg"])`, `FormItem("m pl", forms["mpl"])`, `FormItem("f pl", forms["fpl"])`.
- Note: For numbers and pronouns, the form keys are different (`form_m`, `form_f`, `form_sg_m`, etc.) but the display always renders msg/fsg/mpl/fpl. This means **numbers and pronouns would show dashes for all form values** because the keys don't match.

**DISCREPANCY:** The card back `FormItem` display hardcodes the adjective form keys (`msg`, `fsg`, `mpl`, `fpl`). For numbers (keys `form_m`, `form_f`) and pronouns (keys `form_sg_m`, `form_sg_f`, `form_pl_m`, `form_pl_f`), the forms exist in the map but the display will show "-" for all values because it looks up the wrong keys. This is a UI bug.

---

## Step 14: Skip button -> what happens to skipped word's SRS state

**Code path:**

1. **Skip button** (`VocabDrillScreen.kt:523-541`):
   ```kotlin
   onClick = {
       if (!session.voiceCompleted) {
           onSkipVoice()
       } else {
           onFlip()
       }
   }
   ```
   - If voice not completed: calls `skipVoice()`.
   - If voice already completed: calls `flipCard()`.

2. **skipVoice** (`VocabDrillViewModel.kt:435-445`):
   - Sets `voiceResult = SKIPPED`, `voiceCompleted = true`.
   - Does NOT modify SRS state. Does NOT call `advanceCard()`.
   - After skip, the auto-flip `LaunchedEffect` fires (800ms delay), flipping the card to show the back.

3. **After skip + flip:** The user sees the 4 rating buttons (Again/Hard/Good/Easy) and must rate the card. The SRS state is only updated when the user taps a rating button.

4. **Key point:** "Skip" skips the voice input only, NOT the card. The user still has to flip and rate the card. The SRS state is only affected by the rating choice, not by the skip action itself.

**Spec match:** Matches spec 11 US-11.15: "If voice is not yet completed, Skip marks voice as SKIPPED and auto-flips the card. The user can still rate the card after skipping voice input." No discrepancy.

---

## Step 15: Bidirectional testing -> IT_TO_RU vs RU_TO_IT

**Code path:**

**Card front (VocabDrillScreen.kt:606-615):**
- `IT_TO_RU`: shows Italian word (`card.word.word`), expects Russian answer.
- `RU_TO_IT`: shows Russian meaning (`card.word.meaningRu ?: "?"`), expects Italian answer.

**Card back (VocabDrillScreen.kt:704-777):**
- `IT_TO_RU`: shows Italian word (small) + Russian translation (prominent).
- `RU_TO_IT`: shows Russian meaning (small) + Italian word (prominent).

**Voice recognition language tag (VocabDrillScreen.kt:96-99):**
- `IT_TO_RU`: `ru-RU` (user speaks Russian).
- `RU_TO_IT`: `it-IT` (user speaks Italian).

**Voice matching expected answer (VocabDrillViewModel.kt:379-382):**
- `IT_TO_RU`: expected = `card.word.meaningRu` (Russian).
- `RU_TO_IT`: expected = `card.word.word` (Italian).

**TTS button on front (VocabDrillScreen.kt:618):** Only shown for `IT_TO_RU` direction. In `RU_TO_IT`, the front shows the Russian word, so TTS of Italian would not make sense before the flip.

**TTS on back:** Available for both directions (always speaks the Italian word).

**Potential issue:** In `RU_TO_IT` direction, if `meaningRu` is null, the card front shows "?" and voice matching would compare against an empty string. The `normalizeForMatch` check includes `valid.isNotBlank()` which guards against empty matches, but the front display would be unhelpful.

**Spec match:** Matches spec 11 Section 11.2.5 and US-11.6. No discrepancy.

---

## Summary of Findings

### Discrepancies Found

| # | Step | Severity | Description |
|---|------|----------|-------------|
| D1 | 9 | **High** | `isLearned` threshold: Code uses `LEARNED_THRESHOLD = 3` in both ViewModels. `VocabWord.kt` comment says "reached the last interval step (9)". Specs 01, 02, 18 say step 9. Only spec 11 correctly documents threshold 3. Already identified in `arch-audit-spec-vs-code.md` as CRITICAL-2. |
| D2 | 10 | **Medium** | Card back mastery indicator shows "Learned" only at `step >= 9`, but `isLearned` is `true` at `step >= 3`. The label and the data field represent different thresholds. A word at step 5 is `isLearned = true` in the data model but displays "Step 6/9" on the card back. |
| D3 | 13 | **Medium** | Form display hardcodes adjective keys (`msg`, `fsg`, `mpl`, `fpl`). Numbers (keys `form_m`, `form_f`) and pronouns (keys `form_sg_m`, `form_sg_f`, `form_pl_m`, `form_pl_f`) will show dashes instead of actual form values. |

### Confirmed Correct (No Discrepancies)

| Step | Aspect |
|------|--------|
| 1 | Initialization flow: no auto-load, explicit reload via pack/language |
| 2 | Filter selection: direction, POS, frequency -- all wired correctly |
| 3 | Card selection: due-by-rank, up to 10 cards |
| 4 | Card front display: badges, word, TTS, voice input |
| 5 | Card flip: auto-flip after voice, back side layout |
| 6 | Anki ratings: AGAIN/HARD/GOOD/EASY deltas and interval display |
| 7 | Voice matching: normalize, split-by-slash, contains check, 3 attempts |
| 8 | TTS: manual trigger, 0.67x speed, correct language |
| 11 | Pack switching: full reload of words and mastery |
| 12 | Empty word set: disabled start button, "No due words" |
| 14 | Skip button: skips voice only, does not affect SRS state |
| 15 | Bidirectional: correct front/back, language tags, voice matching |

### Files Traced

| File | Lines | Role |
|------|-------|------|
| `ui/VocabDrillViewModel.kt` | 455 | State management, SRS logic, voice matching |
| `ui/VocabDrillScreen.kt` | 1104 | UI: selection, card front/back, completion |
| `data/VocabWord.kt` | 94 | Data models: VocabWord, WordMasteryState, session state |
| `data/WordMasteryStore.kt` | 145 | YAML persistence of per-word mastery |
| `data/ItalianDrillVocabParser.kt` | 397 | CSV parsing for all vocab drill formats |
| `data/LessonStore.kt` | 948 | Pack-scoped vocab file loading |
| `data/SpacedRepetitionConfig.kt` | 172 | Interval ladder [1,2,4,7,10,14,20,28,42,56] |
| `ui/GrammarMateApp.kt` | -- | ViewModel creation and reload dispatch |
