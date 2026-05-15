# TASK-032: HS-01 Avatar Discrepancies (3 Sub-issues)

**Status:** OPEN
**Created:** 2026-05-15
**Branch:** feature/avatar-fixes (from main)
**Scenario:** scenario-11-navigation.md
**Spec:** 23-screen-elements.md#HS-01
**UC:** UC-66

## Problem

The avatar (HS-01) has 3 discrepancies between spec and code:

1. **Background color**: Spec says "primary background"; code uses `primaryContainer`.
   - Spec: `MaterialTheme.colorScheme.primary`
   - Code (InitialsAvatar.kt): `MaterialTheme.colorScheme.primaryContainer`

2. **Fallback text**: Spec says fallback is "GM"; code uses "?" for blank names.
   - Spec: "fallback 'GM'"
   - Code (InitialsAvatar.kt buildInitials()): returns "?" when name is blank
   - HomeScreen.kt getUserInitials() returns "GM" but is NOT called by InitialsAvatar

3. **Click action**: Spec says "Clickable has no action (decorative)"; code opens ProfileStatsPopup.
   - Spec: "Clickable has no action (decorative)"
   - Code: `onProfileClick` opens ProfileStatsPopup dialog

## Solution

### Decision needed: align spec to code OR code to spec?

Recommendation: Update spec to match code for issues #1 and #3 (code behavior is better).
For #2: Standardize on "GM" fallback (update InitialsAvatar to use "GM" instead of "?").

### Fix 1: Update spec HS-01
**File:** `docs/specification/23-screen-elements.md`
- Change background to "primaryContainer"
- Change fallback to "GM"
- Change click action to "Opens ProfileStatsPopup (see DG-EXTRA-1)"

### Fix 2: Fix InitialsAvatar fallback
**File:** `app/src/main/java/com/alexpo/grammermate/ui/components/InitialsAvatar.kt`
- Change buildInitials() fallback from "?" to "GM"

## Verification Checklist

1. Spec HS-01 matches code: primaryContainer bg, GM fallback, ProfileStatsPopup on click
2. InitialsAvatar shows "GM" for empty/blank names
3. ProfileStatsPopup opens on avatar tap
4. Avatar text color contrast is acceptable on primaryContainer

## Scope

- IN: HS-01 spec entry, InitialsAvatar fallback fix
- OUT: ProfileStatsPopup content, other avatar sizes

## Dependencies

- Parent: TASK-030 (discrepancy registry)
- Related: TASK-033 (ProfileStatsPopup + InitialsAvatar spec entries)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (HomeScreen avatar, ProfileStatsPopup)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Update spec HS-01 to match code | | |
| | Fix 2: Fix InitialsAvatar fallback to "GM" | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
