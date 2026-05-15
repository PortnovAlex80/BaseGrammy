# TASK-027: DG-14/15/16/17 Dialog Location References Stale

**Status:** OPEN
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** scenario-11-navigation.md
**Spec:** 19-screen-catalog.md, 23-screen-elements.md#DG

---

## Problem

Several dialogs were extracted from GrammarMateApp.kt to screen-specific files, but spec still references GrammarMateApp.kt as their location:

- DG-14 (HowThisTrainingWorksDialog): Now in HomeScreen.kt
- DG-15 (LessonLockedDialog): Now in HomeScreen.kt
- DG-16 (EarlyStartDialog): Now in HomeScreen.kt and LessonRoadmapScreen.kt
- DG-17 (ExportBadSentencesResultDialog): Now in SharedReportSheet.kt

## Solution

### Fix 1: Update dialog source file references
**File:** `docs/specification/19-screen-catalog.md`
- Update source file for DG-14, DG-15, DG-16 to HomeScreen.kt
- Update source file for DG-16 also to LessonRoadmapScreen.kt
- Update source file for DG-17 to SharedReportSheet.kt

**File:** `docs/specification/23-screen-elements.md`
- Update DG element source references if they specify file locations

## Verification Checklist
1. All DG-14..DG-17 source file references match actual code locations
2. GrammarMateApp.kt no longer listed as source for extracted dialogs

## Scope Boundaries
**Do NOT touch:**
- Code changes
- Other dialog references

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (dialog navigation, HomeScreen dialogs)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Update DG-14..DG-17 source file references | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
