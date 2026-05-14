# TASK-007: Verb Drill Exit Navigation to HOME

**Status:** DONE
**Created:** 2026-05-14
**Completed:** 2026-05-15
**Branch:** feature/verb-drill-exit-fix (from feature/perf-and-cursor-fixes)
**Spec:** 10-verb-drill.md#10.6.11, 12-training-card-session.md#12.7.1
**UC:** UC-64
**Scenario:** scenario-07-verb-drill.md

---

## Problem

All in-app exit controls in Verb Drill (back arrow, navigation exit button, completion screen Exit button) only clear the session state (`session = null`), switching to the Verb Drill selection screen instead of navigating to HOME. The user must use the system back button to reach HOME. This is inconsistent with user expectations -- pressing an exit/back button should return to the previous screen (HOME).

Root cause: `VerbDrillScreen.kt:114` passes `onExit = viewModel::exitSession` to the session screen. This only clears session state. The `onBack` lambda (which calls `onNavigate(Routes.HOME)`) is available at `VerbDrillScreen` level but is NOT passed through to the session.

## Changes

### Fix 1: Pass onBack to VerbDrillSessionWithCardSession and wire to exit
**UC:** UC-64 | **Spec:** 10-verb-drill.md#10.6.11

1. In `VerbDrillScreen.kt`, pass `onBack` to `VerbDrillSessionWithCardSession` as a new parameter
2. Change `onExit` from `viewModel::exitSession` to `{ viewModel.exitSession(); onBack() }` -- clears session AND navigates HOME
3. Apply the same pattern to completion screen exit button

**Files:**
- `ui/VerbDrillScreen.kt` -- pass onBack to session, combine with exitSession in onExit

**Pseudocode:**
```kotlin
// VerbDrillScreen.kt, in the session branch (line ~109-118):
if (state.session != null) {
    val provider = remember { VerbDrillCardSessionProvider(viewModel) }
    VerbDrillSessionWithCardSession(
        provider = provider,
        viewModel = viewModel,
        onExit = {
            viewModel.exitSession()
            onBack()  // navigates to HOME
        },
        hintLevel = hintLevel,
        textScale = textScale,
        voiceAutoStart = voiceAutoStart
    )
}
```

**Verification:**
- During verb drill session: tap back arrow <-- should navigate to HOME
- During session: tap StopCircle exit -> confirm -> should navigate to HOME
- After completion: tap Exit button -> should navigate to HOME
- System back button still works (unchanged)
- Returning to Verb Drill from HOME -> shows selection screen (session cleared)

---

## Verification Checklist
1. Back arrow <-- in session header navigates to HOME (not selection screen)
2. Nav bar Exit button (StopCircle) + confirmation -> navigates to HOME
3. Completion screen Exit button -> navigates to HOME
4. System back button -> navigates to HOME (existing, unchanged)
5. Re-entering Verb Drill after exit shows selection screen (no stale session)
6. Session state is properly cleaned up on exit (no leaked state)
7. Build: `assembleDebug` passes with no errors

## Scope Boundaries
**Do NOT touch:**
- GrammarMateApp.kt BackHandler (already works correctly)
- VerbDrillViewModel exitSession() logic (correct -- clears session)
- TrainingCardSession navigation controls (shared component, no changes needed)
- Any other screen's exit behavior

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` -- must pass
2. **Cross-task regression:** verify these unaffected features:
   - Regular training exit (TrainingScreen back button)
   - Daily Practice exit
   - Vocab Drill exit (may have same bug -- check)
   - Home screen navigation
3. **UC/AC spot-check:** verify UC-64 ACs hold
4. **Spec sync:** verify code matches updated specs

## Git
One commit. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-15 | Fix 1: Exit navigation to HOME | DONE | Combined exitSession() + onBack() in VerbDrillScreen.kt line 114 |
