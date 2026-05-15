# TASK-044: Fix Training Next Button State Guard and Button Enabled Source

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/fix-training-next-button (from main)
**Spec:** 08-training-viewmodel.md §5, scenario-01 §3, 23-screen-elements.md TS-25
**UC:** UC-06 (AC2, AC4)
**Scenario:** scenario-01-training-flow.md
**User Journey:** user-journey-models.md Step 4.5, Discrepancies BUG-NAV-004, STRUCT-003

---

## Problem

Two related issues with TrainingScreen button state management:

1. **Next button always enabled (BUG-NAV-004):** The Next button in NavigationRow is enabled whenever `hasCards` (currentCard != null). There is no sessionState guard. This means:
   - User can skip cards during ACTIVE session without answering
   - After auto-advance from correct answer, pressing Next skips the subsequent card
   - The button is clickable even in HINT_SHOWN state (where pressing it is valid for advancing, but confusing during ACTIVE)

2. **No single source of truth for button enabled state (STRUCT-003):** The Check button enabled condition is computed inline in the AnswerBox composable (`hasCards && inputText.isNotBlank() && sessionState == ACTIVE && currentCard != null`). This logic should be derived from a shared state property to ensure consistency between the Check button state and the actual ability to submit.

Root cause: Button enabled conditions were written as inline expressions during initial implementation rather than derived from a unified state model.

## Changes

### Fix 1: Add session state guard to Next button
**Discrepancy:** BUG-NAV-004 | **UC:** UC-06 AC4 | **Spec:** scenario-01 §3.3

The Next button should be enabled when:
- `hasCards` (currentCard != null)
- `sessionState != ACTIVE` — only allow manual Next when PAUSED or HINT_SHOWN

During ACTIVE session, the only way to advance should be via correct answer submission (auto-advance). This prevents card skipping.

Exception: In boss battle mode, Next may need to remain always-enabled since boss battles allow skipping. Check boss spec before applying this guard to boss mode.

**Files:** `ui/screens/TrainingScreen.kt` — NavigationRow composable, Next button (~line 725-727)

**Verification:**
- During ACTIVE session: Next button is disabled
- After wrong answer (PAUSED or HINT_SHOWN): Next button is enabled
- Correct answer auto-advance still works (Next not involved)

### Fix 2: Extract Check button enabled condition to shared property
**Discrepancy:** STRUCT-003 | **UC:** UC-06 AC2 | **Spec:** 23-screen-elements.md TS-25

Add a computed property to CardSessionState (or TrainingUiState) that derives `canSubmit` from the existing fields:
```kotlin
val canSubmit: Boolean
    get() = sessionState == ACTIVE && currentCard != null
```

Then use this property in the AnswerBox composable instead of the inline expression. The `inputText.isNotBlank()` check remains inline since it's a UI-level concern.

**Files:** `data/Models.kt` — CardSessionState data class, `ui/screens/TrainingScreen.kt` — AnswerBox

**Verification:** Check button behavior unchanged — this is a refactoring, not a behavior change.

---

## Verification Checklist
1. Next button disabled during ACTIVE session
2. Next button enabled during PAUSED session
3. Next button enabled during HINT_SHOWN session
4. Correct answer auto-advance still works (doesn't use Next button)
5. Check button enabled behavior unchanged after refactoring
6. Boss battle mode: verify Next still works if boss needs it
7. Normal training flow: start → answer all cards → finish, still works

## Scope Boundaries
**Do NOT touch:**
- VerbDrillScreen (uses TrainingCardSession, different button setup)
- DailyPracticeScreen (separate UI)
- SessionRunner (no UI changes)
- GrammarMateApp.kt navigation

## Regression Plan
1. **Build:** `assembleDebug` — must pass
2. **Tests:** `test` — must pass
3. **Per-task verification:** check each item above
4. **Cross-task regression:** full training session flow, boss battle flow, all input modes
5. **UC/AC spot-check:** read UC-06 AC2, AC4

## Git
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Session state guard on Next button | | |
| | Fix 2: Extract canSubmit to shared property | | |
