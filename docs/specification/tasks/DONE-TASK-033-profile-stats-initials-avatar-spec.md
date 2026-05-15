# TASK-033: ProfileStatsPopup + InitialsAvatar Missing from Spec

**Status:** DONE
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** scenario-11-navigation.md
**Spec:** 23-screen-elements.md, 19-screen-catalog.md, 14-theme-and-ui-components.md
**UC:** UC-66

## Problem

Two components exist in code but have no spec entries:

1. **ProfileStatsPopup** (`ui/components/ProfileStatsPopup.kt`): Dialog showing user stats (cards completed, words learned, CEFR level). Triggered by tapping avatar on HomeScreen.

2. **InitialsAvatar** (`ui/components/InitialsAvatar.kt`): Reusable circle avatar composable showing user initials. Used by HomeScreen and ProfileStatsPopup. Not in SH (shared components) section.

These were added as TASK-009 but the spec was never updated to include them.

## Solution

### Fix 1: Add ProfileStatsPopup to dialog catalog
**File:** `docs/specification/23-screen-elements.md` — add DG-18 (ProfileStatsPopup)
**File:** `docs/specification/19-screen-catalog.md` — add D17 (ProfileStatsPopup)

Element details:
- Dialog with avatar + user name + cards completed + words learned + CEFR badge
- Triggered by avatar tap on HomeScreen
- Dismisses on tap outside

### Fix 2: Add InitialsAvatar to shared components
**File:** `docs/specification/23-screen-elements.md` — add SH-08 (InitialsAvatar)

Element details:
- CircleShape Box with primaryContainer background
- Shows 1-2 char initials from user name
- Parameterized size (40dp for HomeScreen, 56dp for popup)
- onClick callback

## Verification Checklist

1. DG-18 / D17 entries exist with correct behavior description
2. SH-08 entry exists with correct props (size, colors, fallback)
3. HS-01 references SH-08 and DG-18

## Scope

- IN: Spec documentation for ProfileStatsPopup and InitialsAvatar
- OUT: Code changes, other components

## Dependencies

- Parent: TASK-030 (discrepancy registry)
- Related: TASK-032 (HS-01 avatar discrepancies — references these components)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (HomeScreen, ProfileStatsPopup)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Add ProfileStatsPopup to dialog catalog (DG-18/D17) | | |
| | Fix 2: Add InitialsAvatar to shared components (SH-08) | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
