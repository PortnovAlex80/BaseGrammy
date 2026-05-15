# TASK-035: TS-09 TTS Warning Icon for OOM Undocumented

**Status:** DONE
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** scenario-01-training-flow.md
**Spec:** 23-screen-elements.md#TS-09, 14-theme-and-ui-components.md
**UC:** UC-61

## Problem

Spec says TTS button ERROR state always uses `ReportProblem` icon. Code uses two variants:
- `ReportProblem` icon for general errors
- `Warning` icon for OOM (out of memory) errors

The Warning variant is undocumented in spec. Found in SharedComponents.kt TtsSpeakerButton.

## Solution

Update spec to document both ERROR icon variants:
- ReportProblem: general TTS errors (file not found, decode error)
- Warning: OOM errors (model too large for memory)

**File:** `docs/specification/23-screen-elements.md` — update TS-09 to mention Warning icon variant
**File:** `docs/specification/14-theme-and-ui-components.md` — add Warning to icon inventory

## Verification Checklist

1. TS-09 spec mentions both ReportProblem and Warning icons for ERROR state
2. Icon inventory includes Warning

## Scope

- IN: TS-09 spec entry, icon inventory
- OUT: Code changes, other TTS button references

## Dependencies

- Parent: TASK-030 (discrepancy registry)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (TTS button on all screens)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Update TS-09 to document Warning icon variant | | |
| | Fix 2: Add Warning to icon inventory | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
