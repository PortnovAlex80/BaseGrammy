# TASK-088: VerbDrill SessionCard State Regression Test

**Status:** OPEN
**Created:** 2026-05-21
**Branch:** feature/verbdrill-sessioncard-regression-test (from main)
**Spec:** 10-verb-drill.md#10.10
**UC:** VD-51
**Scenario:** N/A

---

## Problem

VerbDrill SessionCard displays stale session data after user clicks Repeat/Continue/Reset buttons. The UI state `lastSessionContext` is not refreshed from the YAML store after button actions modify session state.

**Current bug behavior:**
- User completes VerbDrill session → SessionCard shows "0 cards shown" (stale)
- User clicks Repeat → SessionCard still shows previous session data
- User clicks Continue → SessionCard doesn't update card counts/index
- User clicks Reset → SessionCard persists or shows wrong state

**Root cause:** SessionCard button handlers in `VerbDrillViewModel` call session methods (`onRepeatSession`, `onResumeSession`, `onStartFresh`) but don't re-read `lastSessionContext` from `VerbDrillStore` to update UI state. The UI only refreshes on screen entry via `refreshLastSessionContext()`.

This task is to **write a regression test** that FAILS on the current bug, NOT to fix the bug.

---

## Changes

### Fix 1: Write Compose UI Test for SessionCard State Lifecycle
**Discrepancy:** N/A | **UC:** VD-51 | **Spec:** 10-verb-drill.md#10.10

Create a Compose UI test that emulates complete user journey through VerbDrill sessions using only UI clicks (no direct ViewModel method calls). Test must FAIL on current implementation because `lastSessionContext` doesn't update after button actions.

**Test file:** `app/src/androidTest/java/com/alexpo/grammermate/ui/VerbDrillSessionCardTest.kt`

**Test scenario steps:**
1. **Setup:** Launch VerbDrillScreen with test YAML store (mock VerbDrillStore with preset session data)
2. **First session:**
   - Verify filters shown (tense/group dropdowns + frequency checkbox)
   - Start session via UI click
   - Complete 3 cards via mock Voice/Keyboard (check-approve = completion)
   - Complete session via back navigation
3. **Repeat test 1:**
   - Re-enter VerbDrillScreen
   - Verify SessionCard shown with "3 cards shown" (FAILURE POINT: shows 0)
   - Click "Repeat" button
   - Verify session starts with same 3 cards
   - Complete all 3 cards
   - Exit session
4. **Repeat test 2:**
   - Re-enter VerbDrillScreen
   - Click "Repeat" button
   - Verify session starts with same 3 cards (not new batch)
   - Exit without completion
5. **Continue test 1:**
   - Re-enter VerbDrillScreen
   - Click "Continue" button
   - Verify session starts with next batch (cards 4-6 if session size = 3)
   - Exit without completion (partial progress)
6. **Continue test 2 (full completion):**
   - Re-enter VerbDrillScreen
   - Click "Continue" button
   - Complete all cards in batch (Voice/Keyboard mock)
   - Verify batch completed → mastery step advances (check-approve = passed level)
   - Exit session
7. **Reset test:**
   - Re-enter VerbDrillScreen
   - Verify SessionCard shows updated counts (FAILURE POINT: stale)
   - Click "Reset" button
   - Verify filters shown again (no SessionCard)
   - Verify progress reset → can start from card 1

**Mock requirements:**
- `VerbDrillStore`: In-memory YAML store with preset pack/tense data
- `VoiceAssistance` / `KeyboardAssistance`: Mock to auto-approve cards
- `LessonStore`: Mock returning test pack with 9+ verb drill cards

**Verification:** Test FAILS on current code at steps where SessionCard shows stale data. Test PASSES after fixing `lastSessionContext` refresh logic.

---

## Implementation Notes From Review

The test must remain a **clickable scenario**. The current draft is conceptually useful, but it is not valid if it replaces the user journey with direct ViewModel mutation. Keep UI clicks as the only way to advance navigation and card completion.

Allowed:
- Read ViewModel/state/test fixtures to know which card is currently displayed.
- Read `currentCard.acceptedAnswers.first()` to type a correct answer into the UI.
- Read `todayShownCardIds`, `sessionCardIds`, and active card IDs for assertions.

Not allowed:
- Do not call `viewModel.submitCorrectAnswer()` to simulate Check.
- Do not call `viewModel.markCardCompleted()` to simulate Check/hint/next.
- Do not call `viewModel.exitSession()` to simulate Back/Exit.
- Do not assert that Repeat returns only the number of checked cards. Repeat must replay the full saved batch.

### Correct Check Helper Shape

Use UI actions for card completion. The helper can inspect state to get the correct answer, but must submit through the actual input and Check button.

```kotlin
private fun answerCurrentVerbCardCorrectly(
    composeRule: ComposeTestRule,
    trainingVm: TrainingViewModel
) {
    val card = trainingVm.uiState.value.cardSession.currentCard
        ?: error("No current card on TrainingScreen")
    val answer = card.acceptedAnswers.first()

    composeRule.onNodeWithTag("input_field").performTextClearance()
    composeRule.onNodeWithTag("input_field").performTextInput(answer)
    composeRule.onNodeWithTag("check_button").performClick()
    composeRule.waitForIdle()
}
```

If the current tags differ, use the real tags from `TrainingScreen` / `UnifiedInputControlsBar`. Do not bypass the button with direct ViewModel calls.

### Correct Repeat Assertion

Repeat means "same last batch in the same order", not "same count as checked cards".

```kotlin
val firstBatchIds = verbDrillVm.uiState.value.session!!
    .cards
    .map { it.id }

clickRepeatThroughUi()

val repeatedBatchIds = verbDrillVm.uiState.value.session!!
    .cards
    .map { it.id }

assertEquals(
    "Repeat should replay the full saved batch in the same order",
    firstBatchIds,
    repeatedBatchIds
)
```

The SessionCard text `"3 cards shown"` is a separate assertion about checked cards, not the expected Repeat batch size.

### Correct Continue Assertion

Continue should exclude only cards that were actually completed via Check/hint. Cards visited only through navigation must not be treated as shown.

```kotlin
val checkedCardIds = store.loadLastSession()!!.todayShownCardIds

clickContinueThroughUi()

val continueBatchIds = verbDrillVm.uiState.value.session!!
    .cards
    .map { it.id }
    .toSet()

assertTrue(
    "Continue must not include already checked cards",
    continueBatchIds.intersect(checkedCardIds).isEmpty()
)
```

Add a navigation-only subcase:

```kotlin
val beforeShown = store.loadLastSession()!!.todayShownCardIds

// Navigate with UI only: do not press Check.
composeRule.onNodeWithTag("next_button").performClick()
composeRule.waitForIdle()
exitTrainingThroughUi()

val afterShown = store.loadLastSession()!!.todayShownCardIds
assertEquals(
    "Navigation-only cards must not be counted as shown",
    beforeShown,
    afterShown
)
```

### Correct Reset Assertion

Reset only removes the saved session context and returns the user to filters. It must not assert that learning progress is deleted.

```kotlin
val shownBeforeReset = store.loadProgress()
    .values
    .flatMap { it.todayShownCardIds }
    .toSet()

composeRule.onNodeWithText("Reset from beginning").performClick()
composeRule.waitForIdle()

composeRule.onNodeWithText("Previous session").assertDoesNotExist()
composeRule.onNodeWithText("Time").assertIsDisplayed()
composeRule.onNodeWithText("Group").assertIsDisplayed()
assertNull("Last session should be deleted after reset", store.loadLastSession())

val shownAfterReset = store.loadProgress()
    .values
    .flatMap { it.todayShownCardIds }
    .toSet()

assertEquals(
    "Reset should not delete verb drill shown-card progress",
    shownBeforeReset,
    shownAfterReset
)
```

Use localized strings according to the test locale. In the current default resources, filter labels are `"Time"`/`"Group"` only if the app uses English strings; otherwise use tags or resource-based lookup to avoid locale fragility.

### Recommended Test Split

Prefer three clickable scenario tests instead of one large brittle test:

1. `repeat_replays_last_batch_after_checked_cards`
2. `continue_excludes_only_checked_cards_not_navigation_cards`
3. `reset_hides_session_card_but_keeps_progress`

If a single end-to-end scenario is kept, force deterministic card order. Either enable sort-by-frequency through the UI or inject cards/session configuration so `shuffled()` cannot make the assertions flaky.

---

## Verification Checklist

1. Test file created at `app/src/androidTest/java/com/alexpo/grammermate/ui/VerbDrillSessionCardTest.kt`
2. Test uses only Compose UI Testing APIs (`performClick()`, `onNodeWithText()`, etc.)
3. Test makes ZERO direct calls to ViewModel methods (all state changes via UI clicks)
4. Test mocks Voice/Keyboard for card completion (check-approve logic)
5. Test covers Repeat button (2x: full completion + exit)
6. Test covers Continue button (2x: partial + full completion)
7. Test covers Reset button (verify filters reappear)
8. Test FAILS when run against current codebase (SessionCard shows stale data)
9. Test assertions explicitly check `lastSessionContext` fields (shownCount, cardIndex, tense, group)
10. Test documented in README.md with failure explanation

---

## Scope Boundaries

**Do NOT touch:**
- VerbDrillViewModel button handlers (don't fix the bug)
- VerbDrillScreen.kt UI logic
- VerbDrillStore session persistence
- Voice/Keyboard real implementation (mock only for test)

**DO:**
- Write test file only
- Create mock implementations for test dependencies
- Document test failure points in comments
- Add test to README.md test suite section

---

## Regression Plan

After test is written and FAILS correctly:

1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** Run new test — must FAIL with clear assertion message about stale `lastSessionContext`
3. **Documentation:** Verify test comments explain WHERE and WHY it fails
4. **Future fix:** Separate task will fix VerbDrillViewModel to refresh `lastSessionContext` after button actions
5. **Post-fix verification:** Re-run test — must PASS

---

## Git

Single commit after test file creation:

```
Add TASK-088: VerbDrill SessionCard state regression test

- Write Compose UI test for SessionCard lifecycle
- Test FAILS on current stale lastSessionContext bug
- Covers Repeat/Continue/Reset button scenarios
- Uses only UI clicks, no direct ViewModel calls

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-21 | Fix 1: Write Compose UI Test | OPEN | Test file creation |
