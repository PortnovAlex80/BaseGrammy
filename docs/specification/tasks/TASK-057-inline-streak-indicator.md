# TASK-057: Inline Streak Indicator in HomeScreen Header

**Status:** OPEN
**Created:** 2026-05-17
**Branch:** feature/inline-streak-indicator (from feature/pomodoro-timer)
**Spec:** 23-screen-elements.md#HS-02, 23-screen-elements.md#HS-23, 19-screen-catalog.md#HomeScreen, 22-use-case-registry.md#UC-72
**UC:** UC-72 AC1-AC10
**Scenario:** scenario-11-navigation.md

---

## Problem

The FireStreakIndicator currently renders as a separate full-width card (Row 2) below the header row on HomeScreen. This takes up vertical space and feels disconnected from the user's identity. The motivational text labels ("Perfect day!", "Great variety!", "Keep practicing!") add visual noise without proportional value. The username field has no length limit, creating layout pressure when a streak indicator is added inline.

The streak data model and calculation logic (StreakStore, StreakManager) are correct and should NOT change. Only the UI presentation needs to move from a card to an inline element.

## Changes

### Fix 1: Truncate username to 6 characters
**Discrepancy:** N/A | **UC:** UC-72 AC10 | **Spec:** 23-screen-elements.md#HS-02

In `HomeScreen.kt`, the username Text composable (HS-02) currently displays `state.navigation.userName` without length limits. Add truncation: if name length > 6, display `name.take(6) + "…"`. Use `TextOverflow.Ellipsis` with `maxLines = 1` and a `widthIn(max = 48.dp)` constraint.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt` — username Text composable (HS-02)

**Verification:** Username longer than 6 chars shows truncated with ellipsis. Username 6 chars or less shows unchanged.

### Fix 2: Create inline streak indicator and replace FireStreakIndicator card
**Discrepancy:** N/A | **UC:** UC-72 AC1-AC9 | **Spec:** 23-screen-elements.md#HS-23

Replace the current `FireStreakIndicator` composable (full-width Card) with an inline `Row` placed in the header Row 1, immediately after the username Text. The inline indicator consists of:

1. A `Row` containing:
   - Fire emoji text: `"🔥".repeat(todayFireCount.coerceIn(1, 3))` at ~14sp
   - 4dp spacer
   - Streak days text: `"${currentStreak}d"` at ~14sp, Bold
2. Styling:
   - When `currentStreak == 0 && todayFireCount == 0`: grey color (`MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)`) and show single `"🔥"` dimmed
   - When `currentStreak > 0 || todayFireCount > 0`: colored (orange `0xFFFF8F00` or `MaterialTheme.colorScheme.primary`)
   - When `currentStreak >= 7`: gold/orange highlight
3. Always visible (no conditional hiding)

Remove the old `FireStreakIndicator` card composable entirely. Remove the conditional block in HomeScreen that shows it as a separate Row 2.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt` — header Row, FireStreakIndicator composable

**Verification:**
- Streak 0, fires 0: grey `🔥 0d` visible
- Streak 3, fires 1: colored `🔥 3d`
- Streak 7, fires 2: gold `🔥🔥 7d`
- Streak 1, fires 3: colored `🔥🔥🔥 1d`
- No separate card between header and Primary Action Card

### Fix 3: Remove motivational text strings (optional cleanup)
**Discrepancy:** N/A | **UC:** N/A | **Spec:** N/A

The strings `home_perfect_day`, `home_great_variety`, `home_keep_practicing` in string resources are no longer referenced by any UI element. Mark them as unused or remove them. This is low priority and can be deferred.

**Files:** `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ru/strings.xml`

**Verification:** No compile errors referencing removed strings. Grep confirms zero usages.

---

## Verification Checklist
1. Username displays truncated to 6 chars with ellipsis when longer
2. Inline streak indicator always visible in header Row 1 after username
3. 0 streak + 0 fires shows grey dimmed `🔥 0d`
4. Active streak shows 1-3 fire emojis matching todayFireCount (max 3)
5. Streak days displayed as "Nd" suffix
6. Streak >= 7 shows gold/orange highlight styling
7. Indicator updates immediately after session completion (navigate back to HomeScreen)
8. Old FireStreakIndicator card no longer renders
9. Header layout does not overflow or clip on narrow screens (320dp width)
10. Dark theme: grey state visible against dark background

## Scope Boundaries
**Do NOT touch:**
- `data/StreakStore.kt` — no data model changes
- `data/Models.kt` — StreakData, PracticeType unchanged
- `feature/progress/StreakManager.kt` — no logic changes
- `TrainingViewModel.kt` — no streak calculation changes
- StreakDialog (DG-03) — celebration dialog stays as-is
- `CardSessionState` streak fields — unchanged

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work:
   - HomeScreen lesson grid renders correctly
   - Language selector, pomodoro selector, settings still accessible
   - Drill tiles (verb/vocab) still visible when pack has drills
   - Daily practice entry tile still functional
   - StreakDialog still shows correctly on milestone
5. **UC/AC spot-check:** read UC-72 from `22-use-case-registry.md`, confirm AC1-AC10 hold
6. **Spec sync:** specs already updated in this task creation

## Git
One combined commit. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Username truncation | | |
| | Fix 2: Inline streak indicator | | |
| | Fix 3: Remove unused strings | | |
