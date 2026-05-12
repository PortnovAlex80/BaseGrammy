# Scenario 05: Input Mode Handling (VOICE, KEYBOARD, WORD_BANK)

Verification of input mode behavior across all training contexts.

---

## TC-01: WORD_BANK mode -- mastery counting

**Scenario:** User taps words to assemble "io mangio", submits, answer is correct.

**Code path:**
1. User taps `selectWordFromBank(word)` in `TrainingCardSession.kt:657` (or via `VerbDrillCardSessionProvider.selectWordFromBank()` at line 329, or `DailyPracticeSessionProvider.selectWordFromBank()` at line 329)
2. Words are appended to `_selectedWords`, joined into `inputText`
3. `submitAnswer()` called at `TrainingViewModel.kt:617`
4. Answer validated via `Normalizer.normalize()` at line 622-623
5. If correct, `recordCardShowForMastery(card)` called at line 632
6. Inside `recordCardShowForMastery()` at line 2896-2915:
   - Line 2905: `val isWordBankMode = _uiState.value.inputMode == InputMode.WORD_BANK`
   - Line 2906-2909: **Early return** if WORD_BANK -- "Skipping card show record for Word Bank mode - does not count for mastery"

**Expected (spec, 08-training-viewmodel.md section 8.7.3):** WORD_BANK does NOT count for mastery. Should return early.

**Actual:** Correct. The code returns early at `TrainingViewModel.kt:2906-2909` before calling `masteryStore.recordCardShow()`.

**Discrepancy:** None.

---

## TC-02: KEYBOARD mode -- mastery counting

**Scenario:** User types "io mangio", submits, answer is correct.

**Code path:**
1. `submitAnswer()` at `TrainingViewModel.kt:617`
2. Line 622-623: `Normalizer.normalize(state.inputText)` compared against `card.acceptedAnswers`
3. If correct, line 632: `recordCardShowForMastery(card)`
4. Inside `recordCardShowForMastery()`: `inputMode == KEYBOARD`, so WORD_BANK guard at line 2905 does NOT trigger
5. Line 2912: `masteryStore.recordCardShow(lessonId, languageId, card.id)` is called

**Expected:** KEYBOARD counts for mastery. YES.

**Actual:** Correct. Mastery is recorded at `masteryStore.recordCardShow()`.

**Discrepancy:** None.

---

## TC-03: VOICE mode -- mastery counting

**Scenario:** User speaks "io mangio", ASR recognizes, answer is correct.

**Code path:**
1. Voice result arrives via `onVoiceInputResult()` (default implementation in `CardSessionContract` delegates to `onInputChanged(spoken)`)
2. For regular training in `GrammarMateApp.kt`, ASR result populates `inputText`
3. `submitAnswer()` at `TrainingViewModel.kt:617`
4. Same path as KEYBOARD -- `recordCardShowForMastery(card)` at line 632
5. `inputMode == VOICE`, WORD_BANK guard does NOT trigger
6. `masteryStore.recordCardShow()` is called

**Expected:** VOICE counts for mastery. YES.

**Actual:** Correct. Same code path as KEYBOARD -- mastery is recorded.

**Discrepancy:** None.

---

## TC-04: Switch from WORD_BANK to KEYBOARD mid-card

**Scenario:** User starts in WORD_BANK mode, then switches to KEYBOARD before submitting.

**Code path:**
1. `setInputMode(InputMode.KEYBOARD)` at `TrainingViewModel.kt:366`
2. Line 367-378: Updates `_uiState` with new `inputMode = KEYBOARD`
3. Line 368: Resets attempts/answer if previous answer was shown
4. Line 381-384: Does NOT call `updateWordBank()` since mode is KEYBOARD, not WORD_BANK
5. `selectedWords` list remains populated but `wordBankWords` are preserved in state
6. User types new text into `inputText` field
7. On submit, `recordCardShowForMastery()` checks current `_uiState.value.inputMode` which is now `KEYBOARD`
8. Mastery IS recorded

**Expected:** Switching to KEYBOARD mid-card should allow mastery counting, since the mode at submit time determines whether mastery is recorded.

**Actual:** Correct. The mastery check reads `inputMode` at the time of submit, so switching from WORD_BANK to KEYBOARD means mastery IS recorded. However, `inputText` from word bank selection is NOT cleared when switching away from WORD_BANK (line 381-384 only regenerates word bank when switching TO WORD_BANK). The user can type new text over it.

**Discrepancy:** None. Behavior is correct -- mode at submission time determines mastery.

---

## TC-05: Word bank generation -- target words and distractor selection

**Scenario:** How are target words and distractors selected for word bank?

**Code path (regular training -- `TrainingViewModel.kt`):**

`updateWordBank()` at line 2985-3018:
1. Line 2997: `correctAnswer = card.acceptedAnswers.firstOrNull()` -- uses FIRST accepted answer only
2. Line 2998: Split into individual words, filter blanks
3. Line 2999: Normalize correct words for comparison
4. Line 3000-3008: **Distractor pool** = all lesson cards' accepted answers, split into words:
   - Filter out current card (`it.id != card.id`)
   - Filter: length >= 3 characters
   - Filter: not matching any normalized correct word
   - Take distinct values
5. Line 3009: Take 3 random distractors from shuffled pool
6. Line 3010: `generateWordBank(correctAnswer, extraWords)` at line 2976-2980:
   - Split correct answer into words
   - Add extras (filtering blanks and duplicates against correct words)
   - Shuffle the combined list

**Code path (VerbDrill -- `VerbDrillCardSessionProvider.kt`):**

`getWordBankWords()` at line 306-325:
1. Line 313: Split card's single `answer` into words by whitespace
2. Line 314-318: Distractors from OTHER cards in the SESSION (not all cards)
3. Line 317: Take `max(0, 8 - answerWords.size)` distractors
4. Line 321: Shuffle combined

**Code path (Daily Practice -- `DailyPracticeSessionProvider.kt`):**

`getWordBankWords()` at line 306-325 (same pattern):
1. Distractors from OTHER cards in the BLOCK (via `blockCards`)
2. Same formula: `max(0, 8 - answerWords.size)` distractors

**Expected (spec 08-training-viewmodel.md section 8.6.1):**
- Sentence training: 3 distractors from all lesson cards' answers
- Verb drill/daily: up to 8-answerWords distractors from session cards

**Actual:** Matches spec.

**Discrepancy:** None.

---

## TC-06: Word bank -- are distractors ever the same as target words?

**Code path:**

Regular training (`TrainingViewModel.kt:3000-3008`):
- Line 3007: `.filter { Normalizer.normalize(it) !in normalizedCorrect }` -- explicitly excludes words matching correct answer (after normalization)

VerbDrill (`VerbDrillCardSessionProvider.kt:316`):
- `.filter { w -> w.isNotBlank() && w !in answerWords }` -- filters by exact string match, NOT normalized

DailyPractice (`DailyPracticeSessionProvider.kt:316`):
- `.filter { w -> w.isNotBlank() && w !in answerWords }` -- same as VerbDrill

**Expected:** Distractors should never be the same as target words.

**Actual:** Regular training properly normalizes before comparison. However, VerbDrill and DailyPractice providers use exact string match (`w !in answerWords`) without normalization. If a distractor differs only by case or accent from a correct word, it could appear as a distractor.

**Discrepancy:** Minor. VerbDrill and DailyPractice word bank distractor filtering uses exact match instead of normalized match. A distractor like "Io" could appear alongside correct answer word "io" in these contexts. The risk is low since most verb drill answers are lowercase, but it is technically possible.

---

## TC-07: Voice input fails (no mic permission)

**Scenario:** User tries voice input but microphone permission is denied.

**Code path:**

The app uses two voice input mechanisms:

**A) Android system RecognizerIntent (TrainingCardSession.kt, VerbDrillScreen.kt, DailyPracticeScreen.kt):**
- Line 599-604 in `TrainingCardSession.kt`: Creates `Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)` and launches via `speechLauncher`
- If permission denied or no speech recognizer installed, the activity result returns with `resultCode != RESULT_OK`
- Line 468-474: If result is not OK, nothing happens -- `spoken` is null, `onVoiceInputResult` is never called
- No error toast, no fallback. User is left with empty input field.

**B) Offline ASR (AsrEngine.kt):**
- Line 184: `recordAndTranscribe()` -- if recognizer is null, sets ERROR state and returns empty string
- Line 208-209: `AudioRecord` creation can throw if permission denied
- Line 281-290: Exception caught, sets `AsrState.ERROR`, returns empty string
- The ViewModel checks `audioPermissionDenied` flag in UI state

**Expected:** No automatic fallback to keyboard. User must manually switch modes.

**Actual:** Correct. The system RecognizerIntent simply fails silently (no result callback). The offline ASR sets error state. Neither mechanism auto-falls back to keyboard. The user can still see the keyboard button and manually switch.

**Discrepancy:** None. Matches spec section 12.7.8: "Speech recognition failure -- No explicit error shown. User can retry or switch to keyboard."

---

## TC-08: Voice input returns empty string

**Scenario:** ASR recognizes speech but returns empty string.

**Code path:**

**System RecognizerIntent:**
- `TrainingCardSession.kt:470`: `val spoken = matches?.firstOrNull()` -- if matches is empty, spoken is null
- Line 471: `if (!spoken.isNullOrBlank())` -- null/blank check prevents calling `onVoiceInputResult`
- Nothing happens. Input field stays empty.

**Offline ASR (`AsrEngine.kt`):**
- Line 261-266: `if (speechSamples.isEmpty())` -- returns empty string and logs "No speech detected"
- Line 275: `val text = result.text.trim()` -- could be empty string if recognizer returns whitespace
- Empty string returned to caller

**VerbDrillScreen (custom voice LaunchedEffect):**
- Voice result text set as input text
- `submitAnswerWithInput(input)` called -- if input is empty/blank, the validation will fail (wrong answer)
- Alternatively, if the caller checks `isNotBlank()`, submit is skipped

**Expected:** Empty ASR result is treated as no input. No crash, no incorrect submission.

**Actual:** Correct. The RecognizerIntent path filters null/blank before calling back. The offline ASR returns empty string which is handled by callers. VerbDrillScreen and DailyPracticeScreen set the text into the input field but the Check button requires non-blank input (`TrainingCardSession.kt:784: enabled = scope.inputText.isNotBlank()`).

**Discrepancy:** None.

---

## TC-09: Voice input in verb drill vs. regular training

**Scenario:** Is voice input processed differently in verb drill?

**Code path:**

**Regular training (GrammarMateApp.kt + TrainingViewModel.kt):**
1. Voice text set via `onInputChanged(text)` into `inputText` state
2. User must tap "Check" to submit manually
3. `submitAnswer()` at `TrainingViewModel.kt:617` processes the answer
4. Multiple accepted answers checked (`card.acceptedAnswers.any { ... }`)

**Verb drill (VerbDrillScreen.kt + VerbDrillCardSessionProvider.kt):**
1. `LaunchedEffect` at `VerbDrillScreen.kt:140-150` watches for voice token changes
2. Voice recognition auto-launches when conditions met (VOICE mode, active session)
3. On result: `submitAnswerWithInput(spoken)` called IMMEDIATELY -- no manual "Check"
4. `VerbDrillCardSessionProvider.submitAnswerWithInput()` at line 185:
   - `Normalizer.normalize(input) == Normalizer.normalize(card.answer)` -- compares against SINGLE answer
5. If correct: `LaunchedEffect` at line 144-150 auto-advances after 500ms delay

**Key differences:**
| Aspect | Regular Training | Verb Drill |
|--------|-----------------|------------|
| Submit trigger | Manual "Check" | Automatic on voice result |
| Answer check | `acceptedAnswers.any { ... }` | Single `card.answer` comparison |
| Auto-advance | No (except daily practice) | Yes, 500ms after correct |
| Retry voice | Only on wrong + VOICE mode | Auto-re-triggers on wrong |

**Expected (spec 12-training-card-session.md section 12.7.2):** Verb drill auto-submits on voice result; regular training requires manual submit.

**Actual:** Matches spec. Different processing paths.

**Discrepancy:** None.

---

## TC-10: Voice input in vocab drill -- answer matching

**Scenario:** How is voice answer matched in vocab drill?

**Code path:**

`VocabDrillViewModel.handleVoiceResult()` at `VocabDrillViewModel.kt:370-432`:

1. Line 379-382: Determine expected answer based on direction:
   - `IT_TO_RU`: expected = `card.word.meaningRu` (e.g. "быть/являться")
   - `RU_TO_IT`: expected = `card.word.word` (e.g. "essere")
2. Line 385: `val validAnswers = expectedRaw.split("/").map { normalizeForMatch(it) }`
3. Line 386: `val normalizedRecognized = normalizeForMatch(recognizedText)`
4. Line 388-393: Match logic:
   ```kotlin
   val isCorrect = validAnswers.any { valid ->
       valid.isNotBlank() && (
           normalizedRecognized == valid ||
           normalizedRecognized.contains(valid)
       )
   }
   ```
5. Line 447-449: `normalizeForMatch()`:
   ```kotlin
   private fun normalizeForMatch(text: String): String {
       return text.trim().lowercase().replace(Regex("[.!?,;:]$"), "")
   }
   ```

**Expected (spec 11-vocab-drill.md section 11.5.9):**
- Split by "/" for synonyms
- Normalize: trim, lowercase, strip trailing punctuation
- Match: exact OR contains

**Actual:** Correct. The code splits by "/", normalizes with lighter normalization than `Normalizer.normalize()` (only strips trailing punctuation, not all punctuation), and uses both exact match and contains.

**Discrepancy:** None.

**Note:** The vocab drill uses a different, lighter normalization (`normalizeForMatch`) than the rest of the app (`Normalizer.normalize`). This is intentional -- voice recognition may return text with varying punctuation that should not affect the match. However, `contains` matching could produce false positives if the recognized text is long and the expected answer is a short substring.

---

## TC-11: Voice auto-advance after correct answer

**Scenario:** After correct voice answer, does the app auto-advance to next card?

**Code path:**

**Regular training (TrainingViewModel.kt):**
- `submitAnswer()` at line 629-677: On correct answer, `nextCard(triggerVoice = state.inputMode == InputMode.VOICE)` at line 677
- `nextCard()` does advance the card, but the UI in GrammarMateApp.kt does NOT auto-advance -- user must tap "Next" or the result triggers the next sub-lesson
- No auto-advance timer for regular training

**Verb drill (VerbDrillScreen.kt):**
- `LaunchedEffect` at line 144-150:
  ```kotlin
  LaunchedEffect(provider.pendingAnswerResult, provider.currentInputMode) {
      val result = provider.pendingAnswerResult
      if (result != null && result.correct && provider.currentInputMode == InputMode.VOICE) {
          delay(500)
          provider.nextCard()
      }
  }
  ```
- **YES**, auto-advances after 500ms delay when correct in VOICE mode

**Daily practice (DailyPracticeScreen.kt):**
- Daily practice uses `DailyPracticeSessionProvider` which mirrors VerbDrill behavior
- The `DailyInputControls` composable applies voice input text to the field
- Auto-advance behavior depends on the custom `inputControls` slot implementation in DailyPracticeScreen

**Expected (spec 10-verb-drill.md section 10.4.3):** Verb drill auto-advances after correct voice answer. Regular training does not.

**Actual:** Verb drill: YES, 500ms delay. Regular training: NO auto-advance.

**Discrepancy:** None.

---

## TC-12: WORD_BANK -- all words used, no distractors possible

**Scenario:** The answer is a single word. No distractors available because all other cards' answers share the same words.

**Code path (regular training -- `TrainingViewModel.kt`):**

`updateWordBank()` at line 2985-3018:
1. `correctAnswer = card.acceptedAnswers.firstOrNull()` = e.g., "casa"
2. `correctWords = ["casa"]`
3. `distractorPool` at line 3000-3008: filters all words from other cards' answers that don't match "casa" and have length >= 3
4. If distractor pool is empty (all other cards share the same words), `extraWords = emptyList()`
5. Line 3010: `generateWordBank("casa", emptyList())` at line 2976:
   - `words = ["casa"]`
   - `extras = []`
   - Result: `["casa"]` (shuffled, which is just one element)

**Verb drill (`VerbDrillCardSessionProvider.kt`):**
`getWordBankWords()` at line 306-325:
- Same pattern: answer words + distractors
- If no distractors available, bank = just answer words
- For a single-word answer: bank = `["word"]`

**Expected:** Word bank with only the target word(s) and no distractors. User must still select the word(s) in order.

**Actual:** Correct. When no distractors are available, the word bank contains only the answer words. The user still taps them in order. The UI shows all chips as available.

**Discrepancy:** None. This is technically less useful (obvious answer) but not a bug.

---

## TC-13: Input mode persistence between sessions

**Scenario:** Does the app remember the preferred input mode?

**Code path:**

`TrainingProgress` data class in `Models.kt:115-138` does NOT include `inputMode`.

`saveProgress()` in `TrainingViewModel.kt:2453-2487` serializes `TrainingProgress` -- no `inputMode` field.

`ProgressStore.kt` reads/writes `TrainingProgress` -- no `inputMode` field.

When sessions are started or reset, `inputMode` is ALWAYS reset to `InputMode.VOICE`:
- `selectLanguage()` at line 418: `inputMode = InputMode.VOICE`
- `selectLesson()` at line 503: `inputMode = InputMode.VOICE`
- `selectMode()` at line 577: `inputMode = InputMode.VOICE`
- `startBossLesson()` at line 1008: `inputMode = InputMode.VOICE`
- `startBossMega()` at line 1094: `inputMode = InputMode.VOICE`
- `openVocabSprint()` at line 2015: `vocabInputMode = InputMode.VOICE`

VerbDrillCardSessionProvider at line 62: `_inputMode = InputMode.VOICE` (default)

DailyPracticeSessionProvider at line 54: `_inputMode = InputMode.VOICE` (default)

However, `DailyPracticeSessionProvider.inputModeConfig` at line 114-127 reads the task's predetermined `inputMode`:
- `DailyTask.TranslateSentence.inputMode` is assigned by `DailySessionComposer` (rotates VOICE, KEYBOARD, WORD_BANK)
- `DailyTask.ConjugateVerb.inputMode` is assigned by `DailySessionComposer` (alternates KEYBOARD, WORD_BANK)

**Expected:** Input mode is NOT persisted between sessions. Each session starts with default mode (VOICE for regular/verb drill, predetermined for daily practice).

**Actual:** Correct. Input mode is not saved in `TrainingProgress` and always resets to VOICE (or task-specific default).

**Discrepancy:** None.

---

## TC-14: Training mode LESSON vs. BOSS -- different input mode behavior?

**Scenario:** Do LESSON and BOSS modes handle input differently?

**Code path:**

Both LESSON and BOSS modes use the same `submitAnswer()` in `TrainingViewModel.kt:617`.

**Boss-specific behavior:**
- Line 634-678: Boss mode has a separate branch for correct answers
- Line 634: `if (state.bossActive)` -- special handling for last card
- Line 648-653: Voice metrics tracking is identical to non-boss
- Line 655: `finishBoss()` called on last card
- Line 677: `nextCard(triggerVoice = state.inputMode == InputMode.VOICE)` for non-last cards

**Key difference:**
- Boss battles DO use input mode for voice trigger after card advancement
- Boss battles DO record mastery (same `recordCardShowForMastery` call at line 632)
- Boss battles DO NOT save progress mid-battle (line 2455: `if (state.bossActive && state.bossType != BossType.ELITE) return`)
- Boss battles use the same WORD_BANK exclusion for mastery

**Input mode switching:**
- Both modes use the same `setInputMode()` at line 366
- Both generate word banks the same way

**Expected:** Same input mode handling for LESSON and BOSS modes.

**Actual:** Correct. The input mode logic is identical. The only difference is session-level behavior (no mid-battle saves for non-ELITE bosses).

**Discrepancy:** None.

---

## TC-15: Daily practice blocks -- same input mode logic?

**Scenario:** Do all three daily practice blocks use the same input mode logic?

**Code path:**

**Block 1 (TRANSLATE):** Uses `DailyPracticeSessionProvider`
- `inputModeConfig` at line 116-127: reads `task.inputMode` from `DailyTask.TranslateSentence`
- `DailySessionComposer` assigns rotating modes: VOICE, KEYBOARD, WORD_BANK (repeating)
- Supports all three modes
- `submitAnswer()` at line 159-206: normalizes and compares against `acceptedAnswers`
- `nextCard()` at line 226-257: calls `onCardAdvanced()` only for non-WORD_BANK modes

**Block 2 (VOCAB):** Uses `VocabFlashcardBlock` in DailyPracticeScreen.kt -- NOT `DailyPracticeSessionProvider`
- This block does NOT use `TrainingCardSession` at all
- It uses `VocabDrillViewModel.handleVoiceResult()` for voice matching
- Voice is optional (toggled by user)
- No word bank mode -- only voice + flip + rate (Anki-style)
- Direction alternates IT_TO_RU / RU_TO_IT

**Block 3 (VERBS):** Uses `DailyPracticeSessionProvider`
- `inputModeConfig` reads `task.inputMode` from `DailyTask.ConjugateVerb`
- `DailySessionComposer` assigns alternating KEYBOARD, WORD_BANK (no VOICE default for verb block)
- `submitAnswer()` same normalization as block 1
- `nextCard()` same WORD_BANK exclusion for `onCardAdvanced`

**Key differences:**

| Aspect | Block 1 (Translate) | Block 2 (Vocab) | Block 3 (Verbs) |
|--------|--------------------|----------------|-----------------|
| Provider | DailyPracticeSessionProvider | VocabDrillViewModel | DailyPracticeSessionProvider |
| Voice input | Yes (part of rotation) | Optional (toggle) | Not in default rotation |
| Keyboard | Yes (part of rotation) | No (flip + rate) | Yes (alternating) |
| Word bank | Yes (part of rotation) | No | Yes (alternating) |
| Mastery counting | Yes (TRANSLATE) | No (VOCAB) | No (VERBS) |
| Word bank mastery exclusion | Yes | N/A | Yes |

**Expected (spec 08-training-viewmodel.md section 8.5.6):**
- Block 1: rotates VOICE, KEYBOARD, WORD_BANK
- Block 2: Anki-style flashcard (no word bank)
- Block 3: alternates KEYBOARD, WORD_BANK

**Actual:** Matches spec.

**Discrepancy:** None.

---

## Summary

| TC | Description | Expected | Actual | Discrepancy |
|----|-------------|----------|--------|-------------|
| 01 | WORD_BANK mastery | NO | NO | None |
| 02 | KEYBOARD mastery | YES | YES | None |
| 03 | VOICE mastery | YES | YES | None |
| 04 | Mid-card mode switch | Mastery by current mode | Correct | None |
| 05 | Word bank generation | Spec-defined algorithm | Matches | None |
| 06 | Distractor = target? | Never | Regular: never. VerbDrill/Daily: minor edge case | Minor |
| 07 | No mic permission | Silent failure, no auto-fallback | Correct | None |
| 08 | Empty voice result | No action taken | Correct | None |
| 09 | Verb drill vs regular voice | Auto-submit in drill | Correct | None |
| 10 | Vocab voice matching | Exact or contains, lighter normalization | Correct | None |
| 11 | Voice auto-advance | Verb drill: yes. Regular: no | Correct | None |
| 12 | No distractors possible | Only target words shown | Correct | None |
| 13 | Input mode persistence | Not saved, resets to VOICE | Correct | None |
| 14 | LESSON vs BOSS input | Same logic | Correct | None |
| 15 | Daily blocks input logic | Different per block | Correct | None |

### Issues Found

**TC-06 (Minor):** VerbDrillCardSessionProvider and DailyPracticeSessionProvider use exact string match (`w !in answerWords`) when filtering distractors, while TrainingViewModel uses normalized comparison (`Normalizer.normalize(it) !in normalizedCorrect`). This could allow case-variant duplicates in the word bank (e.g., "Io" appearing alongside "io") in verb drill and daily practice contexts. The risk is low but the inconsistency is real.

No other discrepancies found. The input mode handling code faithfully implements the specification.
