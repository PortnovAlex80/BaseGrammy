# TASK-029: Minor Spec Corrections (TS-37, SS-12, SS-20, SS-31)

**Status:** OPEN
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** —
**Spec:** 23-screen-elements.md

---

## Problem

Four minor spec typos/inaccuracies found during audit:

1. **TS-37 (Mix Challenge tense chip color)**: Spec says `#01565C0` (7-char hex, invalid). Code uses `0xFF1565C0` = `#1565C0` (Material Blue 800). Spec has a typo — extra leading `0`.

2. **SS-12 (TTS speed slider)**: Spec says "3 steps". Code has `steps = 3` which creates 4 segments (0.5, 0.75, 1.0, 1.25, 1.5). "3 steps" is ambiguous — does it mean 3 positions or 3 intermediate stops?

3. **SS-20 (Text scale slider)**: Same ambiguity as SS-12. Spec says "3 steps", code creates 4 positions.

4. **SS-31 (Delete all lessons button color)**: Spec says hardcoded `#B00020`. Code uses `MaterialTheme.colorScheme.error` (theme-aware). Code is more correct.

## Solution

### Fix 1: Correct TS-37 color
**File:** `docs/specification/23-screen-elements.md`
- Change `#01565C0` to `#1565C0`

### Fix 2: Clarify SS-12 and SS-20 slider steps
**File:** `docs/specification/23-screen-elements.md`
- Change "3 steps" to "4 discrete positions (steps=3 in Slider API)"
- Or: document actual values: 0.5/0.75/1.0/1.25/1.5 for SS-12, 1.0/1.25/1.5/1.75/2.0 for SS-20

### Fix 3: Update SS-31 color reference
**File:** `docs/specification/23-screen-elements.md`
- Change `#B00020` to `MaterialTheme.colorScheme.error`

## Verification Checklist
1. TS-37 color is valid 6-char hex `#1565C0`
2. SS-12 and SS-20 clearly state 4 discrete positions
3. SS-31 references theme error color, not hardcoded value

## Scope Boundaries
**Do NOT touch:**
- Code changes

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (TrainingScreen, SettingsScreen)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Correct TS-37 color | | |
| | Fix 2: Clarify SS-12/SS-20 slider steps | | |
| | Fix 3: Update SS-31 color reference | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
