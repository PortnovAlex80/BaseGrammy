# TASK-042: Fix VerbDrill Play/Pause Semantics and Auto-Advance Race

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/fix-verb-drill-play-race (from main)
**Spec:** 10-verb-drill.md §5, scenario-07 §3-4, 23-screen-elements.md VD-36
**UC:** UC-25 (AC2, AC4)
**Scenario:** scenario-07-verb-drill.md
**User Journey:** user-journey-models.md Steps 7.1-7.5, Discrepancies BUG-NAV-012, BUG-NAV-013, BUG-NAV-014

---

## Problem

VerbDrill has three issues with play/pause behavior and auto-advance:

1. **Auto-advance race condition (BUG-NAV-012):** VerbDrillScreen.kt has a LaunchedEffect (~line 202-207) that auto-advances after 500ms on correct voice answer. If the user presses Next during that 500ms window, both the LaunchedEffect and the manual Next call `provider.nextCard()`. The second call finds `pendingCard == null` and goes through the else branch calling `viewModel.nextCardManual()`, which may skip a card or advance the index twice.

2. **Play button overloaded semantics (BUG-NAV-013):** In VerbDrill, the Play button has two different behaviors depending on hidden state:
   - When paused WITH hint shown (`sm.hintAnswer != null`): Play advances to NEXT card
   - When paused WITHOUT hint (manual pause): Play resumes CURRENT card

   There is no visual differentiation between these two states. The user cannot predict what Play will do.

3. **Inconsistent exit navigation (BUG-NAV-014):** In-app exit controls (back arrow, completion Exit button) only clear session state and return to the selection screen. Only the system back button navigates to HOME. This is inconsistent — users expect the same exit behavior regardless of which button they use.

Root cause: The VerbDrillCardSessionProvider was adapted from the TrainingCardSession pattern but added mode-specific behavior (auto-advance, hint-on-pause) without updating the UI to match.

## Changes

### Fix 1: Cancel auto-advance LaunchedEffect on manual Next
**Discrepancy:** BUG-NAV-012 | **UC:** UC-25 AC4 | **Spec:** scenario-07 §3

Wrap the auto-advance LaunchedEffect in a coroutine that can be cancelled. When the user manually presses Next, cancel the auto-advance coroutine before calling `provider.nextCard()`.

Implementation: Use a `remember` ref to track whether auto-advance is pending. On manual Next, set the ref to false before calling nextCard. The LaunchedEffect checks the ref before proceeding.

**Files:** `ui/screens/VerbDrillScreen.kt` — LaunchedEffect (~lines 202-207), onNext callback

**Verification:**
- Answer correctly in VOICE mode
- Immediately press Next
- Confirm only ONE card advance happens (no skip)

### Fix 2: Visual differentiation for Play button state
**Discrepancy:** BUG-NAV-013 | **UC:** UC-25 AC2 | **Spec:** 23-screen-elements.md VD-36

Add visual indication when Play will advance (hint shown) vs resume (manual pause). Options:
- **A:** Show "Next" label or forward arrow icon alongside Play when hint is shown
- **B:** Show a different icon (SkipNext instead of PlayArrow) when Play will advance
- **C:** Add a small badge/tooltip "→ next card" when in hint-shown state

Preferred: Option B — change icon from PlayArrow to SkipNext when `sm.hintAnswer != null`.

**Files:** `ui/screens/VerbDrillScreen.kt` or TrainingCardSession DefaultNavigationControls

**Verification:** Get 3 wrong answers (hint shown) → Play icon changes to SkipNext → press it → advances to next card

### Fix 3: Consistent exit navigation
**Discrepancy:** BUG-NAV-014 | **UC:** N/A | **Spec:** scenario-07 §5

Make all exit paths navigate to HOME consistently:
- Back arrow in VerbDrillScreen → navigate HOME
- Completion screen "Exit" button → navigate HOME
- Keep selection screen accessible via a "Back to Selection" button if needed

This aligns VerbDrill exit behavior with VocabDrill (which navigates HOME on all exits).

**Files:** `ui/screens/VerbDrillScreen.kt` — exit/back handlers, completion screen

**Verification:**
- Press back arrow during drill → HOME
- Complete drill, press Exit → HOME
- System back → HOME

---

## Verification Checklist
1. Correct voice answer + immediate manual Next → single card advance, no skip
2. Hint shown state shows different icon (SkipNext) on Play button
3. Manual pause state shows Play arrow on Play button
4. Press Play with hint → advances to next card
5. Press Play without hint → resumes current card
6. All exit paths (back arrow, Exit button, system back) navigate to HOME
7. Drill session runs to completion normally (10 cards)

## Scope Boundaries
**Do NOT touch:**
- SessionRunner (not used by VerbDrill)
- DailyPracticeCoordinator
- TrainingScreen
- VerbDrillViewModel state management

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from Verification Checklist
4. **Cross-task regression:** VerbDrill selection screen, TTS playback, voice recognition, batch loading
5. **UC/AC spot-check:** read UC-25 AC2, AC4 from 22-use-case-registry.md
6. **Spec sync:** update scenario-07 discrepancy status

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Cancel auto-advance on manual Next | | |
| | Fix 2: Visual differentiation for Play state | | |
| | Fix 3: Consistent exit navigation | | |
