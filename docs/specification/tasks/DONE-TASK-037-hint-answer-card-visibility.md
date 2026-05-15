# TASK-037: DP-11 HintAnswerCard Visibility Condition (Spec Error)

**Status:** DONE
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** scenario-06-daily-practice.md
**Spec:** 23-screen-elements.md#DP-11, 09-daily-practice.md#9.11
**UC:** UC-51

## Problem

DP-11 spec says HintAnswerCard is visible when `provider.hintAnswer != null && hintLevel == EASY`.
Code shows it when `provider.hintAnswer != null` (no hintLevel check). Code comment: "available at all hint levels".

The BEHAVIORAL spec (09-daily-practice.md section 9.11 [UI-CONSISTENCY-2025]) explicitly states: "Eye mode works at ALL HintLevel settings" and "Any guard that checks hintLevel == EASY before rendering the HintAnswerCard is a bug."

The code is CORRECT. The element registry (23-screen-elements.md DP-11) has the wrong visibility condition.

## Solution

Fix the spec entry to remove the hintLevel == EASY guard.

### Fix 1: Update DP-11 visibility condition
**File:** `docs/specification/23-screen-elements.md`
- Change DP-11 visibility from `provider.hintAnswer != null && hintLevel == EASY`
- To: `provider.hintAnswer != null`

## Verification Checklist

1. DP-11 visibility condition matches code: `provider.hintAnswer != null`
2. No hintLevel guard mentioned in DP-11
3. Consistent with 09-daily-practice.md#9.11

## Scope

- IN: DP-11 spec entry visibility condition
- OUT: Code changes, other HintAnswerCard references

## Dependencies

- Parent: TASK-030 (discrepancy registry)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (DailyPractice HintAnswerCard, TrainingScreen hint)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Update DP-11 visibility condition to remove hintLevel guard | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
