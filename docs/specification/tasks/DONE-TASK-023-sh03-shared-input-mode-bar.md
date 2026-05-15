# TASK-023: SH-03 SharedInputModeBar Not Extracted

**Status:** DONE
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** —
**Spec:** 23-screen-elements.md#SH-03

---

## Problem

Spec defines SH-03 SharedInputModeBar as a shared composable for input mode selection (Voice/Keyboard/Word Bank). But no file or composable named SharedInputModeBar exists in the codebase.

The input mode bar is implemented inline in 4 places:
1. TrainingScreen.kt (lines 600-625)
2. VerbDrillScreen.kt (VerbDrillInputModeBar)
3. DailyPracticeScreen.kt (DailyInputModeBar)
4. TrainingCardSession.kt (DefaultInputControls)

Each has slightly different behavior (some launch speech directly, some only set mode).

## Solution

### Option A: Extract shared composable (HIGH effort)
Create `ui/components/SharedInputModeBar.kt` with parameterized behavior.

### Option B: Remove SH-03 from spec (LOW effort)
Acknowledge that the inline implementations are intentionally different per screen.

**Recommendation:** Option B for now — the inline implementations have meaningful behavioral differences (see TASK-021). Revisit extraction if behavior converges.

### Fix 1 (Option B): Update spec
**File:** `docs/specification/23-screen-elements.md`
- Remove SH-03 or mark as "DEFERRED — per-screen inline implementations have intentional behavioral differences"
- Add notes about per-screen variations

## Verification Checklist
1. SH-03 status is clear (either extracted or documented as deferred)
2. Spec no longer claims a shared composable that doesn't exist

## Scope Boundaries
**Do NOT touch:**
- Code extraction (unless Option A chosen)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (input mode bars on all screens)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Update spec SH-03 status (extract or defer) | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
