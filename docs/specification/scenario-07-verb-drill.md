# Scenario 07: Standalone Verb Drill Session

Trace of the complete standalone Verb Drill flow, comparing code implementation against spec `10-verb-drill.md`.

---

## Step 1: User taps verb drill tile -> ViewModel init -> card loading

**Code path:**

1. HomeScreen verb drill tile click: `GrammarMateApp.kt:341` sets `screen = AppScreen.VERB_DRILL`
2. Router renders at `GrammarMateApp.kt:455-466`:
   - Gets or creates `VerbDrillViewModel` via `viewModel<VerbDrillViewModel>()`
   - Calls `verbDrillVm.reloadForPack(activePackId)` if pack exists, otherwise `reloadForLanguage(languageId)`
3. ViewModel `init` block (`VerbDrillViewModel.kt:93-96`): sets `isLoading = true`, launches `loadCards()` coroutine
4. `reloadForPack` (`VerbDrillViewModel.kt:115-121`): if packId unchanged and cards exist, returns early; otherwise creates new `VerbDrillStore(packId)`, sets loading, launches `loadCards()`
5. `loadCards()` (`VerbDrillViewModel.kt:123-179`): resolves language, gets files from `LessonStore.getVerbDrillFiles(packId, lang)`, parses each CSV via `VerbDrillCsvParser`, builds `packIdForCardId` map, extracts tenses/groups, loads progress, loads tense reference YAML

**Expected (spec 10.5.3):** Init sets `isLoading = true`, launches coroutine to call `loadCards()`. Files obtained from `LessonStore.getVerbDrillFiles(packId, languageId)`.

**Actual:** Matches spec. No discrepancy.

**Note:** The `reloadForPack` call from `GrammarMateApp.kt:459` runs on **every recomposition** of the `VERB_DRILL` screen branch (it's not in a `LaunchedEffect`). However, it has an early-return guard (`if (currentPackId == packId && allCards.isNotEmpty())`), so it only loads once per pack. This is correct but fragile -- if the ViewModel is recreated (e.g., configuration change), it would load twice (once in `init`, once in `reloadForPack`).

---

## Step 2: Filter selection -- tense, group, frequency

**Code path:**

1. `VerbDrillSelectionScreen` (`VerbDrillScreen.kt:977-1074`) renders `TenseDropdown` and `GroupDropdown`
2. `selectTense(tense)` (`VerbDrillViewModel.kt:228-231`): updates `selectedTense`, resets `allDoneToday = false`, calls `updateProgressDisplay()`
3. `selectGroup(group)` (`VerbDrillViewModel.kt:233-235`): same pattern for group
4. `toggleSortByFrequency()` (`VerbDrillViewModel.kt:238-239`): toggles `sortByFrequency` boolean
5. `updateProgressDisplay()` (`VerbDrillViewModel.kt:242-262`): filters `allCards` by selected tense/group, builds combo key `"{group}|{tense}"`, reads progress for that combo, updates `totalCards`, `everShownCount`, `todayShownCount`

**Expected (spec 10.6.2):** Dropdowns shown for tenses and groups. Checkbox for frequency sort. Progress display updates per combo.

**Actual:** Matches spec. No discrepancy.

---

## Step 3: Session starts with 10 cards -- selection algorithm

**Code path:**

1. User taps "Start"/"Continue" button -> `viewModel.startSession()`
2. `startSession()` (`VerbDrillViewModel.kt:264-310`):
   - Filters `allCards` by selected tense and group
   - If filtered empty -> sets `allDoneToday = true`, returns
   - Gets `todayShownCardIds` from progress for the combo key
   - Filters out cards already shown today (`remaining = filtered.filter { it.id !in todayShownCardIds }`)
   - If remaining empty -> sets `allDoneToday = true`, returns
   - If `sortByFrequency`: sorts by `rank` ascending, takes first 10
   - Otherwise: shuffles randomly, takes first 10
   - Creates `VerbDrillSessionState(cards = selected)`
   - Resets speed tracking timestamps

**Expected (spec 10.1.1, 10.5.4):** 10-card batches filtered by tense/group. Random or frequency-sorted.

**Actual:** Matches spec. No discrepancy.

---

## Step 4: Card display -- Russian prompt, verb info chips

**Code path:**

1. `VerbDrillSessionWithCardSession` (`VerbDrillScreen.kt:131-287`) renders `TrainingCardSession` with custom `cardContent`
2. Card content (`VerbDrillScreen.kt:172-245`):
   - Shows "RU" label, Russian prompt in 20sp semi-bold
   - TTS volume button speaks `card.promptRu` (via `contract.speakTts()` which calls `VerbDrillCardSessionProvider.speakTts()` -> `viewModel.speakTts(card.answer)`)
   - Verb chip (`SuggestionChip`): shows verb name + rank (e.g., "essere #1"). Tappable -- opens verb reference bottom sheet
   - Tense chip (`SuggestionChip`): shows abbreviated tense name. Tappable -- opens tense info bottom sheet

**Expected (spec 10.6.3):** Russian prompt, volume button, verb chip with rank, tense chip.

**Actual:** Matches spec. No discrepancy.

**Minor observation:** The TTS button in the card content speaks `card.promptRu` (the Russian text) when clicked, but `VerbDrillCardSessionProvider.speakTts()` speaks `card.answer` (the Italian text). The card-level volume button (`VerbDrillScreen.kt:194`) calls `contract.speakTts()` which maps to the provider's `speakTts()` -> speaks `card.answer`. So actually the volume button on the card speaks the **Italian answer**, not the Russian prompt. This is a UI/UX choice that may surprise users since the prompt is Russian.

**Update:** Looking more carefully at `VerbDrillCardSessionProvider.speakTts()` (line 346-351): it speaks `card.answer` (Italian). The card content's volume button at `VerbDrillScreen.kt:194` calls `contract.speakTts()`. So the TTS button on the card speaks the Italian answer. The spec (10.5.12) says TTS speaks text, and spec 10.6.3 says "TTS speaks the prompt text." This is a **minor discrepancy** -- the code speaks the answer (Italian), the spec says it speaks the prompt text.

---

## Step 5: User types conjugation -> validation -> correct/incorrect flow

**Code path:**

1. User types in `OutlinedTextField` and clicks "Check" button (`VerbDrillScreen.kt:692-707`)
2. Calls `provider.submitAnswerWithInput(input)`
3. `submitAnswerWithInput()` (`VerbDrillCardSessionProvider.kt:185-227`):
   - If hint already shown, returns null (ignores)
   - Gets current card from session
   - Normalizes both input and answer via `Normalizer.normalize()`
   - Compares: `Normalizer.normalize(input) == Normalizer.normalize(card.answer)`
   - If correct: sets `pendingCard`, `pendingAnswerResult = AnswerResult(correct = true, ...)`, records answer time
   - If wrong: increments `incorrectAttempts`, decrements `remainingAttempts`
4. `Normalizer.normalize()` (`Normalization.kt:4-21`): trim, collapse spaces, fix time suffixes, lowercase, strip punctuation (except hyphens), collapse/trim

**Expected (spec 10.5.5):** Normalization trims, collapses spaces, removes time suffixes, lowercases, strips punctuation except hyphens. Single accepted answer.

**Actual:** Matches spec. No discrepancy.

---

## Step 6: 3-attempt retry -> hint reveal

**Code path:**

1. On wrong answer, `incorrectAttempts` incremented, `remainingAttempts = 3 - incorrectAttempts`
2. If `incorrectAttempts < 3`:
   - Sets `showIncorrectFeedback = true` (shows red "Incorrect" text with remaining attempts)
   - If VOICE mode: auto-increments `voiceTriggerToken` to re-trigger speech recognition
   - User stays on same card, can retry
3. If `incorrectAttempts >= 3`:
   - Sets `hintAnswer = card.answer` (auto-shows answer)
   - Sets `pendingCard = card`
   - Sets `isPaused = true`
   - Input controls remain visible below the hint card
4. UI renders hint (`VerbDrillScreen.kt:465-498`): red-tinted card showing "Answer: {hintAnswer}" with TTS button
5. UI renders incorrect feedback (`VerbDrillScreen.kt:501-518`): red "Incorrect" with "{remainingAttempts} attempts left"

**Manual "Show Answer" (eye button):**
- `showAnswer()` (`VerbDrillCardSessionProvider.kt:229-241`): sets `hintAnswer = card.answer`, `incorrectAttempts = 3`, `isPaused = true`

**After hint shown:** User presses Play button (pause/play navigation button), which calls `togglePause()`.

**Expected (spec 10.4.3):** 3 attempts, inline incorrect feedback, auto-hint after 3, manual eye button, pause state.

**Actual:** Matches spec. No discrepancy.

---

## Step 7: togglePause() -- what exactly happens?

**Code path:**

1. Navigation bar has a pause/play button (`TrainingCardSession.kt:875-885`): calls `contract.togglePause()`
2. `VerbDrillCardSessionProvider.togglePause()` (`VerbDrillCardSessionProvider.kt:150-157`):
   - If `isPaused` (Play button pressed): calls `nextCard()` to advance
   - If not paused (Pause button pressed): sets `isPaused = true`

**This is the key question from the scenario prompt:** Does togglePause advance the card or just reset?

**In standalone Verb Drill:** `togglePause()` with `isPaused == true` calls `nextCard()`, which:
- Clears all pending state (pendingCard, pendingAnswerResult, hintAnswer, etc.)
- If had hint: calls `viewModel.markCardCompleted()` (increments incorrect, persists)
- If had pending correct: calls `viewModel.submitCorrectAnswer()` (increments correct, persists)
- If neither: calls `viewModel.nextCardManual()` (persists as shown, advances)
- Auto-triggers voice recognition in VOICE mode

**Comparison with daily practice:** The scenario prompt asks whether this differs from daily practice. In daily practice, `DailyPracticeSessionProvider` also uses `TrainingCardSession` which calls the same `togglePause()`. The behavior would depend on the daily provider's implementation.

**Verdict:** In standalone Verb Drill, togglePause when paused calls `nextCard()` which **does advance the card**. This is intentional -- after a hint is shown (3 wrong attempts or manual eye), the session is paused. The user presses Play to continue to the next card. The spec (10.4.3) says "User presses Play/Continue to advance" -- matches code.

**No discrepancy with spec.** This is correct behavior for the hint-to-advance flow.

---

## Step 8: Batch completion -> VerbDrillStore progress update

**Code path:**

1. `nextCard()` -> `viewModel.submitCorrectAnswer()` or `viewModel.markCardCompleted()`
2. Both call `persistCardProgress(card)` (`VerbDrillViewModel.kt:392-414`):
   - Builds combo key `"{group}|{tense}"`
   - Gets existing progress for combo
   - Adds card ID to `everShownCardIds` (union via `+`)
   - Adds card ID to `todayShownCardIds` (union via `+`)
   - Creates updated `VerbDrillComboProgress`
   - Updates in-memory `progressMap`
   - Calls `verbDrillStore.upsertComboProgress(comboKey, updatedProgress)`
3. `VerbDrillStore.upsertComboProgress()` (`VerbDrillStore.kt:86-90`): loads all progress, updates the entry, saves via `AtomicFileWriter`
4. `AtomicFileWriter.writeText()` writes YAML atomically

**When isComplete becomes true:** After the last card in the batch, `nextIndex >= session.cards.size` sets `isComplete = true`. The completion screen is shown.

**Expected (spec 10.5.7):** Progress persisted immediately after each card, not batched. Ever-shown IDs never reset. Today-shown IDs reset on date change.

**Actual:** Matches spec. No discrepancy.

**Performance note:** `upsertComboProgress` calls `loadProgress()` (full file read) then `saveProgress()` (full file write) for every single card. This is I/O-heavy but safe for the small data volumes involved.

---

## Step 9: Continue -> next batch of 10

**Code path:**

1. Completion screen "More" button (`VerbDrillScreen.kt:1189`): calls `viewModel.nextBatch()`
2. `nextBatch()` (`VerbDrillViewModel.kt:416-418`): delegates to `startSession()`
3. `startSession()` runs the same selection algorithm:
   - Filters by tense/group
   - Excludes `todayShownCardIds` (cards already practiced today)
   - Takes 10 new cards (shuffled or frequency-sorted)
   - If no remaining cards -> `allDoneToday = true`

**Expected (spec 10.6.9):** "More" button starts new batch. Hidden when `allDoneToday`.

**Actual:** Matches spec. No discrepancy.

---

## Step 10: Report bad sentence -> BadSentenceStore

**Code path:**

1. User taps flag button -> `showReportSheet = true`
2. Report sheet shows options (`VerbDrillScreen.kt:360-448`):
   - "Add to bad sentences list" -> calls `contract.flagCurrentCard()`
   - "Remove from bad sentences list" (if already flagged) -> calls `contract.unflagCurrentCard()`
   - "Hide this card from lessons" -> calls `contract.hideCurrentCard()` (no-op for VerbDrill)
   - "Export bad sentences to file" -> calls `contract.exportFlaggedCards()`
   - "Copy text" -> copies to clipboard
3. `VerbDrillCardSessionProvider.flagCurrentCard()` -> `viewModel.flagBadSentence()`
4. `flagBadSentence()` (`VerbDrillViewModel.kt:465-485`):
   - Gets current card from session
   - Resolves `packId` from `packIdForCardId[card.id]`
   - Calls `badSentenceStore.addBadSentence(packId, cardId, languageId, sentence=promptRu, translation=answer)`
   - Updates `badSentenceCount` and `currentCardIsBad`

**BadSentenceStore.addBadSentence()** (`BadSentenceStore.kt:166-176`): adds to pack-scoped entries, persists via `AtomicFileWriter`. Schema v2 with packs structure.

**Data saved per entry:**
- `cardId`: the VerbDrillCard ID (e.g., `"regular_are_Presente_0"`)
- `languageId`: current language
- `sentence`: Russian prompt
- `translation`: Italian answer
- `addedAtMs`: timestamp

**Expected (spec 10.5.9, 10.6.8):** Flag stores card data pack-scoped, export to file, report sheet with options.

**Actual:** Matches spec. No discrepancy.

**Note:** The "Hide this card from lessons" option is rendered but `hideCurrentCard()` is a no-op (`VerbDrillCardSessionProvider.kt:372-373` -- "Not yet supported for VerbDrill"). The button is visible but does nothing. This is a minor UX issue -- the button should probably be hidden or disabled for VerbDrill.

---

## Step 11: reloadForPack(packId) -- what resets and what's preserved

**Code path:**

1. `reloadForPack(packId)` (`VerbDrillViewModel.kt:115-121`):
   - If `currentPackId == packId && allCards.isNotEmpty()` -> returns early (no-op)
   - Sets `currentPackId = packId`
   - Creates **new** `VerbDrillStore(application, packId = packId)` -- old store is replaced
   - Sets `isLoading = true`
   - Launches `loadCards()` which:
     - Reads new CSV files for the pack
     - Replaces `allCards`, `packIdForCardId`, `activePackIds`
     - Loads new progress from the new store (pack-scoped file)
     - Extracts new tenses/groups
     - Resets UI state (`availableTenses`, `availableGroups`, `isLoading`, etc.)

**What resets:**
- `allCards` (new pack's cards)
- `progressMap` (new pack's progress)
- `packIdForCardId` (new mappings)
- `activePackIds` (new set)
- `tenseInfoMap` (reloaded from assets -- same data unless language changed)
- UI state: tenses, groups, loading, counts

**What's preserved:**
- `currentPackId` (explicitly set)
- `verbDrillStore` (replaced with new pack-scoped instance)
- Speed tracking state (`totalAnswerTimeMs`, `totalAnswersForSpeed`, `cardShownTimestamp`) -- **NOT reset**
- `tenseInfoMap` (overwritten but same source if language unchanged)

**Expected (spec 10.5.3):** Creates new VerbDrillStore scoped to pack, reloads cards. Skips if unchanged.

**Actual:** Mostly matches spec. One minor discrepancy: speed tracking state (`totalAnswerTimeMs`, `totalAnswersForSpeed`) is **not reset** on `reloadForPack()`. It is only reset in `startSession()` (line 307-308). If a user switches packs mid-session, the speed metrics carry over from the old pack. This is a very minor issue since speed is session-scoped and switching packs exits the session.

---

## Step 12: Speed tracking -- how is speed measured and displayed

**Code path:**

1. `startSession()` (`VerbDrillViewModel.kt:307-309`): resets `totalAnswerTimeMs = 0`, `totalAnswersForSpeed = 0`, sets `cardShownTimestamp`
2. `markCardShown()` (`VerbDrillViewModel.kt:74-76`): records `cardShownTimestamp = System.currentTimeMillis()`
3. `recordAnswerTime()` (`VerbDrillViewModel.kt:79-85`): adds elapsed time to `totalAnswerTimeMs`, increments `totalAnswersForSpeed`, resets timestamp
4. `currentSpeedWpm` (`VerbDrillViewModel.kt:66-71`): computes `(totalAnswersForSpeed / (totalAnswerTimeMs / 60000.0)).toInt()`
5. Called from `submitAnswerWithInput()` on correct answer (`VerbDrillCardSessionProvider.kt:204`)
6. After advancing to next card, `submitCorrectAnswer()` and `markCardCompleted()` set `cardShownTimestamp = System.currentTimeMillis()` for the next card

**Display:** The `currentSpeedWpm` property is exposed via `VerbDrillCardSessionProvider.currentSpeedWpm` (line 140-141) and `TrainingCardSession` renders it in the progress indicator.

**Expected (spec 10.5.8):** Tracks cumulative time, computes words-per-minute.

**Actual:** Matches spec. No discrepancy.

**Minor observation:** Speed is measured as "answers per minute" (each answer counts as 1 unit regardless of answer length), not actual "words per minute." The field name `currentSpeedWpm` is slightly misleading -- it's really "answers per minute."

---

## Step 13: Tense reference bottom sheet -- what data and when shown

**Code path:**

1. User taps tense chip on a card -> `tenseSheetTense = tenseText; showTenseSheet = true`
2. `TenseInfoBottomSheet` composable (`VerbDrillScreen.kt:825-973`):
   - Gets `TenseInfo` from `viewModel.getTenseInfo(tenseName)` (line 830)
   - If `tenseInfo == null`: shows fallback message "Reference info unavailable"
   - If available: shows tense name + short abbreviation, formula card, usage in Russian, examples list
3. `getTenseInfo()` (`VerbDrillViewModel.kt:223-226`): looks up in `tenseInfoMap` by full tense name
4. Data loaded from `assets/grammarmate/tenses/{languageId}_tenses.yaml` during `loadCards()` (`VerbDrillViewModel.kt:181-191`)

**Data shown:**
- Tense name (full) + short abbreviation
- Formation formula (e.g., "avere/essere + participio")
- Usage explanation in Russian ("Когда использовать")
- Example sentences with Italian, Russian translation, and optional notes

**Expected (spec 10.6.7):** Bottom sheet with formula, usage, examples. Fallback if data unavailable.

**Actual:** Matches spec. No discrepancy.

---

## Step 14: Word bank in verb drill -- how are distractors generated

**Code path:**

1. `VerbDrillCardSessionProvider.getWordBankWords()` (`VerbDrillCardSessionProvider.kt:306-325`):
   - Gets current card (pending or from session)
   - Caches by card ID
   - Splits card answer into words: `card.answer.split(Regex("\\s+"))`
   - Collects distractors from **other cards in the session** (not all cards):
     ```kotlin
     s.cards.filter { it.id != card.id }
         .flatMap { it.answer.split(Regex("\\s+")).filter { w -> w.isNotBlank() && w !in answerWords } }
         .distinct()
         .shuffled()
         .take(maxOf(0, 8 - answerWords.size))
     ```
   - Combines answer words + distractors, shuffled

**Expected (spec 10.4.6):** Answer words split by whitespace. Distractors from other session cards. Up to `max(0, 8 - answerWords.size)` distractors. Shuffled.

**Actual:** Matches spec. No discrepancy.

**Potential issue:** With only 10 cards in a session and verb conjugation answers often being short (2-4 words), the distractor pool is small. Many verb answers share common words (io, tu, lui, siamo, etc.), which means after filtering out words present in the current answer, the remaining unique distractors may be very few. This could result in a word bank with very few distractors or even just the answer words alone.

---

## Step 15: Empty card set (all conjugations mastered) -> what happens

**Code path:**

1. User selects filters and taps Start -> `startSession()`
2. `startSession()` (`VerbDrillViewModel.kt:264-310`):
   - Filters all cards by tense/group
   - If filtered list is empty -> sets `allDoneToday = true`, returns without creating session
   - If remaining (not shown today) is empty -> sets `allDoneToday = true`, returns
3. Selection screen (`VerbDrillScreen.kt:1055-1063`):
   - If `allDoneToday == true`: shows "На сегодня всё!" message, Start button is hidden
   - User must wait until tomorrow (daily reset) or select different filters

**Expected (spec US-10.5):** "На сегодня всё!" message when all cards exhausted.

**Actual:** Matches spec. No discrepancy.

**Note:** There is no concept of "mastering" a card permanently. The system only tracks which cards have been shown today. Once all cards for a combo have been shown today, the user sees "На сегодня всё!". The `everShownCardIds` set tracks permanent progress for display purposes but does not exclude cards from future sessions -- only `todayShownCardIds` excludes cards. This means a card can reappear tomorrow even if it was shown before.

---

## Summary of Findings

### Discrepancies Found

| # | Severity | Step | Issue |
|---|----------|------|-------|
| 1 | Low | 4 | **TTS button speaks Italian answer, not Russian prompt.** Spec 10.6.3 says "TTS speaks the prompt text" but `VerbDrillCardSessionProvider.speakTts()` speaks `card.answer` (Italian). The card content's volume button at `VerbDrillScreen.kt:194` calls `contract.speakTts()` which maps to the provider's `speakTts()` -> speaks Italian. This may be intentional UX (let user hear correct pronunciation) but contradicts spec. |
| 2 | Low | 10 | **"Hide this card from lessons" option is a visible no-op.** `hideCurrentCard()` in `VerbDrillCardSessionProvider.kt:372-373` is empty ("Not yet supported for VerbDrill"), but the button is still shown in the report sheet. Should be hidden or disabled for VerbDrill context. |
| 3 | Trivial | 11 | **Speed tracking not reset on `reloadForPack()`.** `totalAnswerTimeMs` and `totalAnswersForSpeed` carry over when switching packs. Only reset in `startSession()`. Impact is minimal since switching packs implies no active session. |
| 4 | Trivial | 12 | **`currentSpeedWpm` measures "answers per minute", not words per minute.** Each correct answer counts as 1 unit regardless of answer length. Name is misleading. |

### Spec Compliance

| Spec Section | Status |
|-------------|--------|
| 10.1 Overview (architecture isolation) | PASS -- separate ViewModel, screen, store, parser |
| 10.2 Data Model (VerbDrillCard, progress, session state) | PASS -- all data classes match |
| 10.2.7 CSV Format (parser) | PASS -- parsing rules match |
| 10.3 VerbDrillStore (persistence, atomic writes, daily reset) | PASS |
| 10.4 CardSessionProvider (retry/hint, word bank, contract) | PASS |
| 10.5 ViewModel (state, loading, advancement, validation) | PASS (with TTS note above) |
| 10.6 UI (selection screen, session screen, bottom sheets) | PASS (with hide button note) |
| 10.6.5 Voice recognition (auto-launch, auto-advance) | PASS |
| 10.6.9 Completion screen | PASS |
| 10.6.10 Tense abbreviation map | PASS |

### No Issues Found (Confirmed Working)

- Card loading and parsing pipeline
- Pack-scoped drill file storage and progress
- Filter-by-tense and filter-by-group mechanisms
- 10-card batch selection (random and frequency-sorted)
- Daily card pool with `todayShownCardIds` exclusion
- `allDoneToday` state and "На сегодня всё!" message
- 3-attempt retry flow with auto-hint
- Manual "Show answer" (eye button)
- `togglePause()` correctly advances card after hint (Play button)
- `Normalizer` answer validation (strip punctuation, lowercase, etc.)
- Progress persistence per card (immediate, atomic)
- Bad sentence flagging with pack-scoped storage
- Export bad sentences to text file
- `reloadForPack()` / `reloadForLanguage()` with early-return guards
- Verb reference bottom sheet (conjugation table from session cards)
- Tense info bottom sheet (formula, usage, examples from YAML)
- Word bank distractor generation from session cards
- Navigation: prev card, next card, exit session
- Voice recognition auto-trigger and auto-advance on correct answer
- Input mode switching (VOICE, KEYBOARD, WORD_BANK)
