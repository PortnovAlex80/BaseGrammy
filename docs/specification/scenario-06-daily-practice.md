# Scenario 06: Complete Daily Practice Session (3 Blocks)

**Date:** 2026-05-12
**Branch:** feature/daily-cursors
**Spec:** docs/specification/09-daily-practice.md

---

## Scenario

User starts daily practice -> block 1 (10 translations) -> block 2 (10 vocab) -> block 3 (10 verbs) -> completion.

---

## Step-by-Step Trace

### Step 1: User taps Daily Practice tile -> startDailyPractice() -> initialization

**Code path:**
- `GrammarMateApp.kt:320-337` -- HomeScreen `onOpenElite` callback
- `GrammarMateApp.kt:325` -- checks `vm.hasResumableDailySession()` for resume dialog
- `GrammarMateApp.kt:330-335` -- launches `vm.startDailyPractice(level)` on IO dispatcher
- `TrainingViewModel.kt:1450-1507` -- `startDailyPractice(lessonLevel)`

**What initializes:**
1. `dailyCursorAtSessionStart` saved for rollback on cancel (`TrainingViewModel.kt:1452`)
2. `dailyPracticeAnsweredCounts` reset to empty map (`TrainingViewModel.kt:1454`)
3. Reads `activePackId`, `selectedLanguageId`, `selectedLessonId` from state (`TrainingViewModel.kt:1457-1459`)
4. Reads current `dailyCursor` from state (`TrainingViewModel.kt:1460`)
5. Determines `isFirstSessionToday` by comparing cursor's `firstSessionDate` to today (`TrainingViewModel.kt:1462`)
6. Tries pre-built session from `prebuiltDailySession` (built at init for first session) (`TrainingViewModel.kt:1465-1479`)
7. Falls back to synchronous build via `DailySessionComposer.buildSession()` (`TrainingViewModel.kt:1482-1488`)
8. If first session today, stores first-session card IDs in cursor (`TrainingViewModel.kt:1493-1502`)
9. Calls `dailySessionHelper.startDailySession(tasks, lessonLevel)` which sets `DailySessionState(active=true, tasks=..., taskIndex=0, blockIndex=0)` (`DailySessionHelper.kt:19-34`)

**Expected vs actual:** MATCH. Spec 9.6.1 describes this exact flow.

**Discrepancy:** None.

---

### Step 2: Block 1 starts -> DailySessionComposer creates 10 translation tasks -> card selection

**Code path:**
- `DailySessionComposer.kt:60-82` -- `buildSession()` calls `buildSentenceBlock()` first
- `DailySessionComposer.kt:145-182` -- `buildSentenceBlock()`

**Card selection algorithm:**
1. Loads lessons via `lessonStore.getLessons(languageId)` (line 152)
2. Uses `cursor.currentLessonIndex` to select lesson, fallback to `lessonId` match (lines 155-159)
3. Takes `cards.drop(cursor.sentenceOffset)` -- starts at offset, sequential order, no shuffle (lines 165-166)
4. Takes up to `SENTENCE_COUNT = 10` cards (line 168)
5. Input mode rotation: index % 3 -> VOICE(0), KEYBOARD(1), WORD_BANK(2) (lines 171-175)
6. Wraps each as `DailyTask.TranslateSentence(id="sent_${card.id}", card, inputMode)` (lines 176-180)

**Expected vs actual:** MATCH. Spec 9.3.1 describes cursor-driven sequential selection with no shuffle.

**Discrepancy:** None.

---

### Step 3: Block 1, card 1 -> mic auto-starts? What's the input mode?

**Code path:**
- `DailyPracticeSessionProvider.kt:54` -- `_inputMode` initialized to `InputMode.VOICE`
- `DailyPracticeSessionProvider.kt:114-127` -- `inputModeConfig` reads from task: card 0 (index % 3 = 0) -> `InputMode.VOICE`
- `DailyPracticeScreen.kt:246-278` -- `CardSessionBlock` creates provider via `remember(blockKey)`
- `DailyPracticeScreen.kt:333-361` -- `DailyTrainingCardSession` auto-voice LaunchedEffect

**Mic auto-start behavior:**
1. Provider initializes `_inputMode` to `InputMode.VOICE` (line 54 of provider).
2. Card 0 has `inputMode = VOICE` (from modulo 3 rotation).
3. Auto-voice LaunchedEffect triggers when `provider.currentInputMode == VOICE && provider.sessionActive && provider.currentCard != null` (`DailyPracticeScreen.kt:341-343`).
4. On first card: 200ms delay (`DailyPracticeScreen.kt:348`), then launches ASR intent.

**Expected vs actual:** MATCH. The mic auto-starts on the first card because it is VOICE mode.

**Discrepancy:** None. However, there is a subtle point: `_inputMode` starts as `VOICE` regardless of the task's designated mode. The `inputModeConfig.defaultMode` returns the task's mode, but the provider's `_inputMode` is not explicitly synced to the task's mode on creation. In practice, for card 0, the task's mode IS VOICE (index % 3 = 0), so the default `_inputMode = VOICE` happens to match. But if a different block type were first and had KEYBOARD as its first task, the provider would still start with `_inputMode = VOICE` until the UI renders and the user interacts. This is not an issue for the current block ordering (TRANSLATE first, card 0 = VOICE).

---

### Step 4: Block 1: last card completed -> block transition

**Code path:**
- `DailyPracticeSessionProvider.kt:226-257` -- `nextCard()` increments `currentIndex`
- `DailyPracticeSessionProvider.kt:254-256` -- when `currentIndex >= blockCards.size`, calls `onBlockComplete()`
- `DailyPracticeScreen.kt:281-288` -- `blockComplete` flag triggers `onAdvanceBlock()`
- `TrainingViewModel.kt:1620-1625` -- `advanceDailyBlock()` calls `dailySessionHelper.advanceToNextBlock()`
- `DailySessionHelper.kt:76-105` -- `advanceToNextBlock()` scans forward past current block type

**Transition flow:**
1. Provider's `nextCard()` increments `currentIndex` (line 247).
2. Before incrementing, calls `onCardAdvanced(blockCards[currentIndex])` if not WORD_BANK (lines 242-244). This fires `onPersistVerbProgress` for verb cards and `onCardPracticed` for cursor tracking.
3. When `currentIndex >= blockCards.size`, calls `onBlockComplete()` (line 255).
4. In `CardSessionBlock`, `blockComplete = true` (line 255 of DailyPracticeScreen.kt).
5. Auto-advance logic calls `onAdvanceBlock()` (line 282).
6. `advanceToNextBlock()` in DailySessionHelper scans forward in task list past all TRANSLATE tasks, lands on first VOCAB task.
7. Updates `taskIndex` to first VOCAB task, increments `blockIndex` to 1.
8. `DailyPracticeScreen.kt:158-177` -- LaunchedEffect detects `currentBlockType` change (TRANSLATE -> VOCAB), sets `showBlockTransition = true`.
9. `BlockSparkleOverlay` shows "Next: Vocabulary" for 800ms (line 1003-1007).

**Expected vs actual:** MATCH. Spec 9.6.2 describes this exact flow.

**Discrepancy:** None.

---

### Step 5: Block 2 starts -> DailySessionComposer creates vocab tasks -> SRS-based selection

**Code path:**
- `DailySessionComposer.kt:73` -- `buildVocabBlock(packId, languageId)`
- `DailySessionComposer.kt:228-293` -- full SRS selection algorithm

**SRS selection algorithm (executed at session build time, not at block transition):**
1. Loads all vocab words from pack's drill files via `lessonStore.getVocabDrillFiles()` + `ItalianDrillVocabParser` (lines 232, 386-418).
2. Loads all mastery states from `WordMasteryStore.loadAll()` (line 235).
3. Categorizes words: due (overdue or never reviewed), new (no mastery), scheduled (not due) (lines 243-253).
4. Sorts due words by most overdue first (descending `now - nextReviewDateMs`) (line 256).
5. Takes up to 10 due words (line 261).
6. Fills with new words sorted by rank if < 10 (lines 264-266).
7. Fallback: least recently reviewed (lines 270-276).
8. Direction alternation: index % 2 -> IT_TO_RU(0), RU_TO_IT(1) (lines 282-286).
9. Wraps as `DailyTask.VocabFlashcard(id="voc_${word.id}", word, direction)` (lines 287-292).

**Expected vs actual:** MATCH. Spec 9.4.2 describes this exact algorithm.

**Important note:** Vocab tasks are built at session composition time (start of session), NOT at block transition. The SRS state at session start determines which 10 words appear. This is correct per spec.

**Discrepancy:** None.

---

### Step 6: Block 2: flashcard flip -> Anki rating buttons -> step deltas

**Code path:**
- `DailyPracticeScreen.kt:835-990` -- `VocabFlashcardBlock` composable
- `TrainingViewModel.kt:1693-1720` -- `rateVocabCard(rating)`

**Flashcard display:**
- Both prompt and answer text are always visible simultaneously (lines 893-915). There is no "flip" mechanism -- the answer is always shown. The `onFlipVocabCard` callback is a no-op (`GrammarMateApp.kt:378`).

**Rating buttons:**
- Four buttons: "Again"(0), "Hard"(1), "Good"(2), "Easy"(3) with distinct colors (lines 967-989).
- On tap: calls `onRate(rating)`, then `onAdvance()`, then checks if more cards remain (lines 976-979).

**Step delta calculation (TrainingViewModel.kt:1702-1707):**
| Rating | Label | Step delta |
|--------|-------|------------|
| 0 | Again | Reset to 0 |
| 1 | Hard | Stay at current step (no change) |
| 2 | Good | +1 (capped at maxStep = 9) |
| 3 | Easy | +2 (capped at maxStep = 9) |

**Expected vs actual:** PARTIAL MATCH.

**Discrepancy:** Spec 9.4.6 says "Hard (1): Step stays the same or moves down." The code has `current.intervalStepIndex` (stays the same, no move down). This is acceptable -- "or moves down" is optional in the spec language, and staying the same is the simplest correct interpretation.

**Additional note:** The `LEARNED_THRESHOLD = 3` constant is defined locally inside `rateVocabCard()` (line 1709). This is not configurable and not in `SpacedRepetitionConfig`. If the new step index >= 3, `isLearned = true`.

---

### Step 7: Block 2: last card -> transition to block 3

**Code path:**
- `DailyPracticeScreen.kt:207-217` -- VocabFlashcardBlock's `onComplete` callback
- `DailyPracticeScreen.kt:209-213` -- calls `onAdvanceBlock()`, if false calls `onComplete()`
- Same transition flow as Step 4 but now VOCAB -> VERBS.

**Transition:**
1. Last vocab card rated -> `onRate(rating)` called, then `onAdvance()` returns false (no more VOCAB cards).
2. `onComplete()` lambda in `VocabFlashcardBlock` calls `onAdvanceBlock()` first (line 209).
3. `advanceToNextBlock()` scans past VOCAB tasks to first VERBS task.
4. `blockIndex` becomes 2.
5. LaunchedEffect detects block type change (VOCAB -> VERBS), shows sparkle "Next: Verbs".

**Expected vs actual:** MATCH.

**Discrepancy:** None.

---

### Step 8: Block 3 starts -> verb conjugation tasks -> weak-first selection algorithm

**Code path:**
- `DailySessionComposer.kt:76-79` -- `buildVerbBlock(packId, languageId, tenses, cursor)`
- `DailySessionComposer.kt:303-358` -- full weak-first selection

**Algorithm:**
1. If `activeTenses` is empty, returns empty (line 309).
2. Loads all verb drill cards from pack files (line 310, 420-435).
3. Filters by active tenses: only cards with `tense` in `activeTenses` (lines 312-314).
4. Loads progress from `VerbDrillStore.loadProgress()` (line 318).
5. Collects all `everShownCardIds` across all combos (lines 321-324).
6. Excludes previously shown cards (line 327).
7. Scores by weakness: `1 - (shownInSameTense / totalInSameTense)` (lines 330-337).
8. Sorts by weakness descending, stable by original index (lines 340-343).
9. Takes top 10 (line 348).
10. Input mode: even index = KEYBOARD, odd = WORD_BANK (line 351). No VOICE for verb cards.

**Expected vs actual:** MATCH. Spec 9.5.1 describes this exact algorithm.

**Discrepancy:** None.

---

### Step 9: Block 3: verb info chips -> infinitive/tense/group display

**Code path:**
- `DailyPracticeScreen.kt:377-472` -- card content in `DailyTrainingCardSession`
- `DailyPracticeScreen.kt:425-471` -- chip rendering logic

**Chip rendering:**
1. `provider.currentVerbDrillCard()` returns the `VerbDrillCard` for current task (line 377, provider line 360-363).
2. Verb chip: shows `"$verbText #$verbRank"` if rank exists, else just `verbText` (lines 430-445). Hidden if `verbText` is null/blank (line 425).
3. Tense chip: shows abbreviated tense via `abbreviateTense()` (lines 446-458). Hidden if null/blank.
4. Group chip: shows group as-is (lines 459-471). Hidden if null/blank.

**Tense abbreviations** (`DailyPracticeScreen.kt:1090-1106`): Full mapping for all 12 Italian tenses. Unrecognized tenses truncated to 8 chars.

**Chip interaction:** Non-interactive -- `onClick = {}` with comment "no bottom sheet for daily practice" (lines 429, 448, 463).

**Expected vs actual:** MATCH. Spec 9.5.4 describes this exact display.

**Discrepancy:** None.

---

### Step 10: Block 3: last card -> session completion screen

**Code path:**
- `DailyPracticeSessionProvider.kt:254-256` -- `onBlockComplete()` when last verb card done
- `DailyPracticeScreen.kt:281-288` -- `blockComplete = true`, calls `onAdvanceBlock()`
- `DailySessionHelper.kt:87-89` -- `advanceToNextBlock()` reaches end of task list, calls `endSession()`
- `DailySessionHelper.kt:144-154` -- `endSession()` sets `active=false, finishedToken=true`
- `DailyPracticeScreen.kt:106-118` -- detects `!state.active && state.finishedToken`

**Completion flow:**
1. Last verb card advanced -> `onBlockComplete()` fires.
2. `onAdvanceBlock()` returns false (no more blocks).
3. `onComplete()` callback fires (`DailyPracticeScreen.kt:284-286`).
4. In GrammarMateApp.kt:396-399, `onComplete` calls `vm.cancelDailySession()` and navigates to HOME.

**BUT WAIT** -- there's a critical interaction:

Looking more carefully at the flow:
- `CardSessionBlock.kt:281-288` -- `blockComplete = true` triggers `onAdvanceBlock()`. If it returns false, calls `onComplete()`.
- `onComplete` in GrammarMateApp.kt:396-399 calls `vm.cancelDailySession()` which calls `dailySessionHelper.endSession()` AND checks cursor advancement.
- But `advanceToNextBlock()` in DailySessionHelper already called `endSession()` (line 88-89), setting `active=false, finishedToken=true`.
- Then `cancelDailySession()` is called again, which calls `endSession()` again (line 1686). This is idempotent (just sets the same state again).
- `cancelDailySession()` also checks `ds.finishedToken` (line 1672) and advances cursor if all blocks were fully practiced.

**Completion sparkle:**
- `DailyPracticeScreen.kt:103-104` -- `showCompletionSparkle` is true when `finishedToken && !hasShownCompletionSparkle`.
- `BlockSparkleOverlay` shows "Daily practice complete!" with "Great job today!" for 800ms (lines 106-114).
- After dismiss, `DailyPracticeCompletionScreen` renders "Session Complete!" with "Back to Home" button (lines 1048-1078).

**Expected vs actual:** MATCH with a nuance.

**Discrepancy (minor):** The completion sparkle shows BEFORE the completion screen, but `onComplete` has already been called (which calls `cancelDailySession()` and navigates to HOME). This means the sparkle overlay and completion screen may flash very briefly or not be visible at all because the screen navigation happens immediately. The sparkle has 800ms delay but the `onComplete` callback fires immediately. This appears to be a race condition -- the session ends and screen navigates away before the user sees the completion sparkle/screen.

**Update:** Looking more carefully at the code flow: `blockComplete = true` in CardSessionBlock (line 281) triggers `onAdvanceBlock()` which returns false, then `onComplete()` is called (line 285). But the composable re-renders: `DailyPracticeScreen` checks `!state.active && state.finishedToken` FIRST (line 106), which is now true because `advanceToNextBlock()` called `endSession()`. So the sparkle and completion screen should render. However, `onComplete` navigates to HOME on the same frame. The question is which state update wins. The `dailySessionHelper.endSession()` updates the StateFlow, which triggers recomposition of DailyPracticeScreen. But `onComplete` also sets `screen = AppScreen.HOME`, which removes DailyPracticeScreen from composition entirely. The navigation likely wins, meaning the user never sees the completion sparkle/screen from within CardSessionBlock.

There IS a separate path: `DailyPracticeScreen.kt:106-118` checks for `finishedToken` and would show the sparkle/completion screen, but only if the screen is still being rendered. Since `onComplete` navigates away, this code never executes visually.

This means the session effectively ends with an abrupt return to Home, not with the completion screen described in the spec.

---

### Step 11: Repeat button -> what exactly resets? What's preserved?

**Code path:**
- `GrammarMateApp.kt:550-586` -- Resume dialog with "Repeat" button
- `GrammarMateApp.kt:569-578` -- "Repeat" calls `vm.repeatDailyPractice(level)`
- `TrainingViewModel.kt:1538-1587` -- `repeatDailyPractice()`

**Repeat session construction:**
1. Tries in-memory cache (`lastDailyTasks`) first (lines 1547-1551).
2. Falls back to stored first-session card IDs from cursor (lines 1554-1571).
3. Last resort: builds fresh with cursor at offset 0 (lines 1574-1585).
4. Calls `dailySessionHelper.startDailySession(tasks, lessonLevel)`.
5. Does NOT advance cursor (line 1569, 1585).

**What resets:**
- `DailySessionState`: completely fresh (active=true, taskIndex=0, blockIndex=0, finishedToken=false).
- `DailyPracticeSessionProvider`: recreated fresh via `remember(blockKey)` (new blockKey = new state).
- `dailyPracticeAnsweredCounts`: NOT explicitly reset in `repeatDailyPractice()`. It is only reset in `startDailyPractice()` (line 1454). So if the user repeats without starting a new session via `startDailyPractice()`, the counts from the previous session persist.

**What's preserved:**
- `DailyCursorState`: NOT advanced (repeat replays same cards).
- `lastDailyTasks`: kept in memory for fast repeat.
- `firstSessionSentenceCardIds` / `firstSessionVerbCardIds`: preserved in cursor.
- Mastery/SRS progress from previous ratings: preserved (WordMasteryStore, VerbDrillStore).
- Flower progress from TRANSLATE block mastery recordings: preserved.

**Discrepancy (minor):** `dailyPracticeAnsweredCounts` is not reset in `repeatDailyPractice()`. This means if the user completes a session (which accumulates counts), then repeats, the counts continue accumulating. On the second completion, `cancelDailySession()` checks if counts >= expected, which they will be (they were already >= from the first session plus the repeat). This is unlikely to cause issues but is technically a counter leak.

---

### Step 12: Continue button -> what happens after completion?

**Code path:**
- `GrammarMateApp.kt:553-565` -- "Continue" button calls `vm.startDailyPractice(level)`
- `TrainingViewModel.kt:1450-1507` -- `startDailyPractice()` for a non-first session

**Continue session construction:**
1. `isFirstSessionToday` = false (cursor's `firstSessionDate` == today, so NOT first session).
2. Skips pre-built session cache (line 1466: only used for first session).
3. Builds fresh via `DailySessionComposer.buildSession()` with CURRENT cursor state (line 1486).
4. If the previous session completed successfully and `cancelDailySession()` advanced the cursor, the new session starts from the next batch of cards.
5. Does NOT store first-session card IDs again (line 1493 condition is false).

**Cursor advancement in cancelDailySession():**
- Only advances if `ds.finishedToken == true` AND all TRANSLATE cards answered via VOICE/KEYBOARD AND all VERB cards answered via VOICE/KEYBOARD (lines 1672-1683).
- `advanceCursor(sentenceCount)` increments `sentenceOffset` by the count of practiced sentences (line 1530-1536).
- Does NOT advance `currentLessonIndex` (the code only updates `sentenceOffset`).

**Expected vs actual:** MATCH per spec 9.6.5. Continue gives new cards if cursor advanced, same behavior as Repeat if not.

**Discrepancy:** The `currentLessonIndex` is never advanced by `advanceCursor()`. When `sentenceOffset` exceeds the lesson's card count, `buildSentenceBlock()` returns empty (line 166-167: `remaining.isEmpty()`). This means Block 1 becomes empty in future sessions once all cards in the current lesson are exhausted. There is no mechanism to advance to the next lesson. This is a potential issue for users who complete many daily sessions within a lesson.

---

### Step 13: DailyCursorState: how does it track position across blocks?

**Code path:**
- `Models.kt:228-235` -- `DailyCursorState` data class
- `TrainingViewModel.kt:1452` -- saved at session start for rollback
- `TrainingViewModel.kt:1530-1536` -- `advanceCursor(sentenceCount)` only updates `sentenceOffset`
- `TrainingViewModel.kt:1513-1525` -- `storeFirstSessionCardIds()` updates cursor with card IDs
- `TrainingViewModel.kt:2479` -- persisted in `TrainingProgress.dailyCursor` via `ProgressStore.save()`

**Tracking mechanism:**
- `sentenceOffset`: tracks how many sentence cards have been completed. Only advances on session completion with full VOICE/KEYBOARD answers.
- `currentLessonIndex`: which lesson to draw from. Set at cursor creation, never advanced by the code.
- `firstSessionDate`: ISO date of first session today.
- `firstSessionSentenceCardIds` / `firstSessionVerbCardIds`: stored for Repeat functionality.
- `lastSessionHash`: defined but never used in the current codebase.

**Dual tracking:**
1. `DailySessionHelper` tracks in-session position via `taskIndex` and `blockIndex` (in-memory StateFlow, not persisted).
2. `DailyCursorState` tracks cross-session position via `sentenceOffset` (persisted in progress.yaml).

**Expected vs actual:** MATCH.

**Discrepancy:**
1. `lastSessionHash` is defined but never written or read -- dead field.
2. `currentLessonIndex` is never advanced -- lesson progression after exhausting cards is unhandled.

---

### Step 14: Mid-session kill -> what state persists? Can user resume?

**What persists across app restart:**

| Data | Persisted? | Where |
|------|-----------|-------|
| `DailyCursorState` | Yes | `progress.yaml` (line 2479) |
| `DailySessionState` | No | In-memory StateFlow only |
| `dailyPracticeAnsweredCounts` | No | In-memory variable |
| `lastDailyTasks` | No | In-memory variable |
| Verb drill progress | Yes | `verb_drill_progress.yaml` (per card advancement) |
| Word mastery (SRS) | Yes | `word_mastery.yaml` (per rating) |
| Sentence mastery | Yes | Per-card mastery recorded on VOICE/KEYBOARD practice |

**Resume behavior:**
- `hasResumableDailySession()` (TrainingViewModel.kt:1441-1448): returns true only if `firstSessionDate == today` AND first-session card IDs are non-empty.
- On app restart, the user lands on HOME (not stuck in daily practice).
- If user taps Daily Practice again, the resume dialog appears.
- "Repeat" reconstructs from stored card IDs. "Continue" builds new session from cursor.

**Can the user resume mid-block?** No. The in-session position (`taskIndex`, `blockIndex`) is NOT persisted. After a kill, the user can only start a fresh session (either Repeat with same cards or Continue with new cards from cursor position).

**Expected vs actual:** MATCH per spec 9.8.4: "Session position: DailySessionState in TrainingUiState (in-memory). Not persisted across app restarts (session is lost)."

**Discrepancy:** None. This is by design.

---

### Step 15: Block sparkle overlay -> when does it show?

**Code path:**
- `DailyPracticeScreen.kt:103-104` -- completion sparkle condition
- `DailyPracticeScreen.kt:158-177` -- block transition sparkle via LaunchedEffect
- `DailyPracticeScreen.kt:993-1046` -- `BlockSparkleOverlay` composable

**When sparkle shows:**

1. **Block transition sparkle** (lines 158-177):
   - LaunchedEffect keyed on `currentBlockType` change.
   - When `previousBlockType != null && previousBlockType != currentBlockType`, shows sparkle.
   - Message: "Next: {BlockLabel}" (e.g., "Next: Vocabulary", "Next: Verbs").
   - `isLastBlock` calculated as: `currentBlockType == VERBS && blockProgress.globalPosition >= blockProgress.totalTasks`.
   - Auto-dismisses after 800ms.

2. **Completion sparkle** (lines 103-114):
   - Condition: `state.finishedToken && !hasShownCompletionSparkle`.
   - Shows "Daily practice complete!" with "Great job today!".
   - Auto-dismisses after 800ms.
   - After dismiss, shows `DailyPracticeCompletionScreen`.

**Expected vs actual:** PARTIAL MATCH.

**Discrepancy (significant):** As noted in Step 10, the completion sparkle may never be visible because `onComplete` navigates away to HOME immediately. The block transition sparkle between blocks 1->2 and 2->3 should work correctly because those transitions happen within the same screen.

The block transition sparkle message uses the NEXT block's label (line 1003: "Next: $blockLabel" where `blockType` is the new block type), which is correct per spec 9.6.3.

---

## Summary of Findings

### Discrepancies Found

| # | Severity | Description | Location |
|---|----------|-------------|----------|
| 1 | **Significant** | Session completion sparkle/completion screen likely never visible. `onComplete` navigates to HOME immediately after `endSession()`, removing the DailyPracticeScreen before the sparkle renders. | `DailyPracticeScreen.kt:281-288`, `GrammarMateApp.kt:396-399` |
| 2 | **Moderate** | `currentLessonIndex` in DailyCursorState is never advanced. When all cards in a lesson are exhausted (sentenceOffset exceeds lesson size), `buildSentenceBlock()` returns empty, resulting in an empty Block 1 for all subsequent sessions. | `TrainingViewModel.kt:1530-1536`, `DailySessionComposer.kt:165-166` |
| 3 | **Minor** | `dailyPracticeAnsweredCounts` not reset in `repeatDailyPractice()`. Counts from previous session accumulate, potentially causing premature cursor advancement on repeat+complete cycles. | `TrainingViewModel.kt:1538-1587` |
| 4 | **Minor** | `lastSessionHash` field in `DailyCursorState` is defined but never written or read -- dead code. | `Models.kt:231` |
| 5 | **Minor** | `_inputMode` in DailyPracticeSessionProvider initializes to `VOICE` unconditionally (line 54), not from the task's designated mode. Works correctly for TRANSLATE block (card 0 = VOICE) but would be incorrect if blocks were reordered. | `DailyPracticeSessionProvider.kt:54` |
| 6 | **Trivial** | Spec says Hard rating may "move down" step, but code only stays the same. Acceptable interpretation but not exactly matching spec wording. | `TrainingViewModel.kt:1703`, Spec 9.4.6 |

### Spec Compliance: Overall Assessment

The implementation closely follows the spec (09-daily-practice.md) for the core flow:
- 3-block session composition: CORRECT
- Card selection algorithms (cursor-driven, SRS, weak-first): CORRECT
- Input mode rotation: CORRECT
- Retry/hint flow (3 attempts, auto-show, manual show): CORRECT
- Mastery counting (VOICE/KEYBOARD only): CORRECT
- Block transitions with sparkle overlays: CORRECT for inter-block transitions
- Repeat/Continue navigation: CORRECT
- State persistence and resume: CORRECT
- Verb info chips display: CORRECT

The main issue is the completion flow (Discrepancy #1), where the user experience is likely an abrupt return to Home instead of the designed completion sparkle + completion screen.
