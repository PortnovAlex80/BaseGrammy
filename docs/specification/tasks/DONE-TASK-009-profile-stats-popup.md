# TASK-009: Profile Stats Popup with CEFR Level

**Status:** DONE
**Created:** 2026-05-15
**Branch:** feature/profile-stats-popup
**Spec:** `docs/superpowers/specs/2026-05-15-profile-stats-design.md`

## Problem

Users have no visibility into their learning progress. There's no aggregate stats display — only per-lesson flowers and per-drill progress. Users want to know "what level am I at?".

## Solution

Add an InitialsAvatar on HomeScreen. Tap it → show a micro-popup with:
- Total cards completed (auto-submit / successful shows)
- Total words learned (isLearned count)
- Estimated CEFR level (A1-C2) based on rank center of gravity of learned words

## Implementation Steps

### Fix 1: CefrCalculator
**File:** `app/src/main/java/com/alexpo/grammermate/data/CefrCalculator.kt` (NEW)
- Pure function: takes list of learned VocabWord ranks → returns CEFR level string
- Median-based mapping: <500→A1, <1000→A2, <2000→B1, <4000→B2, <8000→C1, >=8000→C2
- Edge case: <10 learned words → "A1", 0 words → "—"

### Fix 2: InitialsAvatar composable
**File:** `app/src/main/java/com/alexpo/grammermate/ui/components/InitialsAvatar.kt` (NEW)
- Colored circle with 1-2 letter initials from user name
- Parameterized size (40dp for HomeScreen, 56dp for popup)
- onClick callback

### Fix 3: ProfileStatsPopup composable
**File:** `app/src/main/java/com/alexpo/grammermate/ui/components/ProfileStatsPopup.kt` (NEW)
- Small card popup showing: avatar + name, cards count, words learned, CEFR badge
- Loads stats on-demand from MasteryStore, WordMasteryStore, CefrCalculator
- Dismiss on tap outside

### Fix 4: Wire into HomeScreen + GrammarMateApp
**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt`, `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt`
- Add InitialsAvatar to HomeScreen top bar
- Add `showProfileStats: Boolean = false` to DialogState
- Add ProfileStatsPopup rendering in NavDialogs section

### Fix 5: Spec tracking
**Files:** `docs/specification/trace-index.md`, `docs/specification/CHANGELOG.md`
- Add new symbols: CefrCalculator, InitialsAvatar, ProfileStatsPopup
- Add CHANGELOG entry

## Verification Checklist
1. Tap avatar on HomeScreen → popup appears with stats
2. Cards count matches sum of uniqueCardShows across all lessons
3. Words count matches WordMasteryStore.getMasteredCount()
4. CEFR level changes as more words are learned (test with mock data)
5. Popup dismisses on tap outside
6. Avatar shows correct initials from user name
7. Works with default name "GrammarMateUser" → shows "G"
8. Settings name change updates avatar initials

## Scope
- IN: Stats popup, CEFR calculation, InitialsAvatar, DialogState flag
- OUT: Avatar upload, activity history, per-lesson breakdown, profile editing from popup

## Dependencies
- None — all data sources already exist
