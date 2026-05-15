# TASK-031: Dark Theme Color Palette Mismatch

**Status:** DONE
**Created:** 2026-05-15
**Branch:** feature/dark-theme-palette (from main)
**Scenario:** —
**Spec:** 14-theme-and-ui-components.md#14.2
**UC:** UC-66

## Problem

All 8 explicitly-specified dark theme colors in the spec differ from the code. The code was updated to use Material 3 dynamic defaults instead of the spec's original teal-based palette. 0/8 dark colors match.

| Role | Spec Value | Code Value |
|------|-----------|------------|
| primary | #4A8B92 | #80CBC4 |
| onPrimary | #FFFFFF | #003731 |
| secondary | #7EB5A5 | #80B5A9 |
| onSecondary | #FFFFFF | #00332B |
| background | #1A1A1A | #1A1C1E |
| onBackground | #E8E8E8 | #E2E1DF |
| surface | #2A2A2A | #1A1C1E |
| onSurface | #E8E8E8 | #E2E1DF |

Light theme: 8/8 match perfectly. Dark theme: 0/8 match.

## Solution

### Option A: Update spec to match code (RECOMMENDED)
Code uses M3 dynamic defaults which provide better contrast and consistency. Update spec section 14.2 to reflect the actual dark palette.

### Option B: Update code to match spec
Revert dark theme to the spec's teal palette.

**Files:**
- `docs/specification/14-theme-and-ui-components.md` — update dark color table
- OR `app/src/main/java/com/alexpo/grammermate/ui/Theme.kt` — revert dark colors

## Verification Checklist

1. Dark theme colors in spec match Theme.kt exactly
2. Light theme unchanged (8/8 still match)
3. Visual review: dark theme looks correct on device

## Scope

- IN: Dark theme palette alignment (spec OR code)
- OUT: Light theme, semantic color constants, typography

## Dependencies

- Parent: TASK-030 (discrepancy registry)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (all screens in dark mode, light theme unchanged)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Align dark theme palette spec with code | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
