# TASK-024: SH-05 TextScaleProvider Not Implemented

**Status:** DONE
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** —
**Spec:** 23-screen-elements.md#SH-05

---

## Problem

Spec defines SH-05 TextScaleProvider as a centralized composable for text scaling. No such file or composable exists in the codebase. Text scaling is applied inline at each usage site via `(baseSize * textScale).sp`.

## Solution

### Option A: Extract TextScaleProvider (MEDIUM effort)
Create a CompositionLocal provider for text scale, eliminating repeated `(baseSize * textScale).sp` calculations.

### Option B: Remove SH-05 from spec (LOW effort)
Acknowledge the inline approach is used.

**Recommendation:** Option B — the inline approach is simple and explicit. A CompositionLocal would add indirection without clear benefit at this scale.

### Fix 1 (Option B): Update spec
**File:** `docs/specification/23-screen-elements.md`
- Remove SH-05 or mark as "DEFERRED — inline scaling via (baseSize * textScale).sp is used"
- Document the inline pattern as the standard approach

## Verification Checklist
1. SH-05 status is clear in spec
2. No phantom shared component claimed

## Scope Boundaries
**Do NOT touch:**
- Code changes (unless Option A chosen)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (text scaling on all screens)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Update spec SH-05 status (extract or defer) | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
