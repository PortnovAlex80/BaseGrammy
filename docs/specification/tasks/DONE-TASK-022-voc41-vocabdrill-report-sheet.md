# TASK-022: VOC-41 VocabDrill Custom Report Sheet (Not SharedReportSheet)

**Status:** DONE
**Created:** 2026-05-15
**Branch:** feature/vocab-report-sheet (from main)
**Scenario:** scenario-08-vocab-drill.md
**Spec:** 23-screen-elements.md#VOC-41, 23-screen-elements.md#SH-01
**UC:** UC-60

---

## Problem

Two issues with the VocabDrill report sheet:

1. **Custom implementation instead of SharedReportSheet**: VerbDrillScreen uses SharedReportSheet, but VocabDrillScreen has its own inline ModalBottomSheet with custom implementation. This violates the UI-CONSISTENCY-2025 rule that all report sheets should use SharedReportSheet.

2. **Missing "Hide card" option**: The VocabDrill report sheet has: flag/unflag, export, copy, share via QR — but is MISSING the "Hide this card from lessons" option that SharedReportSheet provides.

## Solution

### Fix 1: Replace custom report sheet with SharedReportSheet
**File:** `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillScreen.kt`
- Remove the inline ModalBottomSheet (lines 593-679)
- Use SharedReportSheet with the same parameters as VerbDrillScreen
- Include "Hide card" option (can be a no-op for vocab if not applicable)

### Fix 2: Update spec to document the change
**File:** `docs/specification/23-screen-elements.md`
- Update VOC-41 to reference SharedReportSheet (SH-01)
- Add "Hide card" option to VOC-41 behavior

## Verification Checklist
1. VocabDrillScreen uses SharedReportSheet (same as VerbDrillScreen)
2. Report sheet has: flag/unflag, hide card, export, copy, share via QR
3. Custom ModalBottomSheet code removed
4. Spec VOC-41 references SH-01
5. Build passes

## Scope Boundaries
**Do NOT touch:**
- SharedReportSheet component
- VerbDrillScreen report sheet
- TrainingScreen report sheet

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (VocabDrill screen, report sheets on other screens)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Replace custom report sheet with SharedReportSheet | | |
| | Fix 2: Update spec VOC-41 to reference SH-01 | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
