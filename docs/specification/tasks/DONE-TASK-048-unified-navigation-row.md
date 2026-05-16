# TASK-048: Unified NavigationRow Component for All Card Modes

**Status:** DONE
**Created:** 2026-05-16
**Branch:** feature/unified-navigation-row (from main)
**Spec:** 12-training-card-session.md, 08-training-viewmodel.md §5, 10-verb-drill.md §5
**UC:** UC-06, UC-20, UC-25
**Scenario:** scenario-01, scenario-06, scenario-07
**User Journey:** user-journey-models.md Design Principle DP-01
**Depends on:** TASK-045 (Phase 5 — CardSessionStateModel UI integration), TASK-047 (pause-mode navigation)

---

## Problem

Currently, the navigation bar (← Pause Stop →) has different implementations across modes:

1. **TrainingScreen** has `NavigationRow` composable — back arrow, Play/Pause, Stop, Next arrow
2. **VerbDrillScreen** has `VerbDrillNavigationControls` — custom implementation with SkipNext icon for hint state
3. **DailyPracticeScreen** uses `DefaultNavigationControls` from TrainingCardSession — yet another variant

Each implementation has slightly different:
- Button enabled/disabled logic
- Icon choices (Play vs SkipNext)
- Pause/resume semantics
- Event handling

Per Design Principle DP-01: "All card-based drill modes MUST use identical play/pause/submit/retry/navigation behavior." The navigation bar must be ONE shared component.

## Changes

### Fix 1: Create UnifiedNavigationRow composable
**Discrepancy:** N/A (new component) | **UC:** UC-06, UC-20, UC-25 | **Spec:** 12-training-card-session.md

Create a single `UnifiedNavigationRow` composable in `ui/components/` (or as part of `TrainingCardSession.kt`) that:
- Accepts `CardSessionStateModel` as its state source (from TASK-045)
- Renders: [← Prev] [Play/Pause] [Stop/Exit] [Next →]
- Play/Pause icon: Pause when `isActive`, Play when `!isActive`
- Prev: always enabled when has cards and index > 0
- Next: always enabled when has cards (per TASK-047, triggers PAUSE if ACTIVE)
- Stop: always enabled, shows exit confirmation
- All icon choices and enabled logic are IDENTICAL for all modes

**Files:** New file `ui/components/UnifiedNavigationRow.kt` or extend `ui/TrainingCardSession.kt`

**Verification:** TrainingScreen, VerbDrillScreen, and DailyPracticeScreen all render identical navigation bars

### Fix 2: Replace TrainingScreen NavigationRow
**Discrepancy:** N/A | **UC:** UC-06 | **Spec:** 08-training-viewmodel.md §5

Replace the existing `NavigationRow` composable in TrainingScreen with `UnifiedNavigationRow`. Pass SessionRunner (which implements CardSessionStateModel) as the state source.

**Files:** `ui/screens/TrainingScreen.kt` — remove old NavigationRow, use UnifiedNavigationRow

**Verification:** Training mode navigation looks and behaves exactly as before

### Fix 3: Replace VerbDrill navigation controls
**Discrepancy:** N/A | **UC:** UC-25 | **Spec:** 10-verb-drill.md §5

Replace `VerbDrillNavigationControls` (custom implementation added in TASK-042) with `UnifiedNavigationRow`. Pass VerbDrillCardSessionProvider (which implements CardSessionStateModel) as the state source.

The SkipNext icon behavior from TASK-042 should be preserved through the CardSessionStateModel.isHintShown property — when hint is shown, the Play icon can optionally change to indicate "advance" (this is a visual property of the shared component, not mode-specific logic).

**Files:** `ui/screens/VerbDrillScreen.kt` — remove custom navigation controls, use UnifiedNavigationRow

**Verification:** VerbDrill navigation matches Training navigation exactly

### Fix 4: Replace DailyPractice navigation controls
**Discrepancy:** N/A | **UC:** UC-20 | **Spec:** 09-daily-practice.md §5

Replace `DefaultNavigationControls` usage in DailyPracticeScreen with `UnifiedNavigationRow`. Pass DailyPracticeSessionProvider (which implements CardSessionStateModel) as the state source.

**Files:** `ui/screens/DailyPracticeScreen.kt`, `feature/daily/DailyPracticeSessionProvider.kt`

**Verification:** DailyPractice card blocks navigation matches Training navigation exactly

### Fix 5: Delete old navigation implementations
**Discrepancy:** N/A | **UC:** N/A | **Spec:** N/A

After all 3 modes use UnifiedNavigationRow:
- Delete old `NavigationRow` from TrainingScreen
- Delete `VerbDrillNavigationControls` from VerbDrillScreen
- Delete `DefaultNavigationControls` from TrainingCardSession (if no longer used)
- Clean up any dead code

**Files:** TrainingScreen.kt, VerbDrillScreen.kt, TrainingCardSession.kt

**Verification:** Grep for old navigation component names → zero results

---

## Verification Checklist
1. UnifiedNavigationRow renders identically in Training, VerbDrill, DailyPractice
2. Play/Pause toggles work the same in all modes
3. Next arrow triggers PAUSE + advance in all modes (TASK-047)
4. Prev arrow triggers PAUSE + retreat in all modes
5. Stop/Exit shows confirmation dialog in all modes
6. Hint-shown state: Play icon changes consistently across modes
7. No mode-specific conditionals in UnifiedNavigationRow code
8. Old navigation implementations fully removed
9. `assembleDebug` passes
10. All existing tests pass

## Scope Boundaries
**Do NOT touch:**
- VocabDrillScreen (flashcard flip paradigm — no navigation row)
- GrammarMateApp.kt (route-level navigation)
- SessionRunner business logic
- Mastery/flower/streak calculations

## Regression Plan
1. **Build:** `assembleDebug` after each fix
2. **Tests:** `test` after each fix
3. **Per-task verification:** each item above
4. **Cross-task regression:** full session flow in Training, Boss, DailyPractice, VerbDrill
5. **UC/AC spot-check:** UC-06 AC2-4, UC-20, UC-25

## Git
One commit per fix. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Create UnifiedNavigationRow | | |
| | Fix 2: Replace Training NavigationRow | | |
| | Fix 3: Replace VerbDrill controls | | |
| | Fix 4: Replace DailyPractice controls | | |
| | Fix 5: Delete old implementations | | |
