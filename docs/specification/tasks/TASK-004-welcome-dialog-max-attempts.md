# TASK-004: WelcomeDialog Max 3 Attempts

**Status:** OPEN
**Created:** 2026-05-14
**Branch:** feature/welcome-dialog-fix (from feature/arch-feature-migration)
**Spec:** 13-app-entry-and-navigation.md (section 13.4.3)
**UC:** UC-46 (AC update needed)
**Scenario:** scenario-15-onboarding.md (TC1, TC14)

---

## Problem

The WelcomeDialog asking for username appears infinitely on every app launch if the user skips or enters blank input (which keeps `userName = "GrammarMateUser"`). There is no retry counter and no screen filter — the dialog can pop up during active training sessions, interrupting the user's lesson.

## Changes

### Fix 1: Add attempt counter to UserProfile

**Spec:** 13#13.4.3 | **UC:** UC-46 AC3

Add `welcomeDialogAttempts: Int = 0` to `UserProfile` data class. Increment on each dialog show. Persist in `profile.yaml`. When `welcomeDialogAttempts >= 3`, never show the dialog again.

**Files:**
- `data/Models.kt` — `UserProfile` data class (add field)
- `data/ProfileStore.kt` — persist/load new field
- `ui/GrammarMateApp.kt` — trigger logic: `userName == "GrammarMateUser" && welcomeDialogAttempts < 3`

**Verification:** Skip dialog 3 times → on 4th launch, dialog does not appear. Close app between each attempt.

### Fix 2: Only show on HOME screen

**Spec:** 13#13.4.3 | **UC:** UC-46 AC3

Add screen guard to WelcomeDialog trigger: only show when `currentScreen == AppScreen.HOME`. Never show during TRAINING, DAILY_PRACTICE, VERB_DRILL, VOCAB_DRILL, LESSON.

**Files:**
- `ui/GrammarMateApp.kt` — add `currentScreen == AppScreen.HOME` to LaunchedEffect condition

**Verification:** User on TRAINING screen, userName is default → no dialog. Navigate to HOME → dialog appears.

### Fix 3: Skip button increments counter

**Spec:** 13#13.4.3

When user taps "Skip" or enters blank name (resulting in "GrammarMateUser"), increment the counter AND persist it. This ensures the dialog eventually stops even if user always skips.

**Files:**
- `ui/GrammarMateApp.kt` — `onNameSet` callback increments attempt counter on skip/blank
- `shared/SettingsActionHandler.kt` — `updateUserName()` may need to handle attempt counter

**Verification:** Skip 3 times → dialog never appears again. Name remains "GrammarMateUser" but user can change it in Settings.

---

## Verification Checklist

1. Fresh install: WelcomeDialog appears on first HOME screen
2. Skip → relaunch → dialog appears (attempt 2)
3. Skip → relaunch → dialog appears (attempt 3)
4. Skip → relaunch → dialog does NOT appear (attempt 4 blocked)
5. During TRAINING: dialog never appears regardless of userName
6. During DAILY_PRACTICE: dialog never appears
7. Enter name on attempt 2 → dialog never appears again
8. Settings screen: user can still change name after dialog is permanently dismissed
9. `welcomeDialogAttempts` persists across app restarts (in profile.yaml)

## Scope Boundaries

**Do NOT touch:**
- TrainingViewModel or training logic
- Daily Practice flow
- Any other dialog types
- HomeScreen layout

## Git

Commit footer: `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Attempt counter | | |
| | Fix 2: HOME-only guard | | |
| | Fix 3: Skip increments | | |
| | Verification checklist | | |
