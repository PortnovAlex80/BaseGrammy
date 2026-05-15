# TASK-034: HS-15 Legend Text Structure Mismatch

**Status:** DONE
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** —
**Spec:** 23-screen-elements.md#HS-15

## Problem

Spec describes the legend with individual emoji meanings for 6 states: "seed, growing, bloom, wilting, wilted, forgotten". Code combines them into 2 lines:
- Seedling emoji + string "seed/growing/bloom" (one combined string resource)
- Wilted flower emoji + string "wilting/wilted/forgotten" (one combined string resource)

The spec implies more granular emoji breakdown (6 separate entries or 6 named states with distinct emojis).

## Solution

Update spec HS-15 to reflect the actual 2-line structure:
- Line 1: Seedling emoji + combined seed/growing/bloom text
- Line 2: Wilted flower emoji + combined wilting/wilted/forgotten text

**File:** `docs/specification/23-screen-elements.md` — update HS-15 description

## Verification Checklist

1. Spec HS-15 description matches the 2-line code structure
2. String resource keys documented

## Scope

- IN: HS-15 spec entry
- OUT: Code changes, other legend elements

## Dependencies

- Parent: TASK-030 (discrepancy registry)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (HomeScreen legend display)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Update spec HS-15 to match 2-line code structure | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
