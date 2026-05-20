# TASK-075: Verb Drill "Resume" Loads Next Cards, Not Same Session

**Status:** OPEN
**Created:** 2026-05-20
**Branch:** feature/verb-drill-resume-next-cards (from develop)
**Spec:** 10-verb-drill.md#10.6.2 (VD-50), 22-use-case-registry.md#US-10.20
**UC:** US-10.20 AC2
**Scenario:** scenario-07-verb-drill.md

---

## Problem

Current VerbDrillViewModel "Resume" behavior restores the EXACT same session state (same cards, same position). User requirement is different: "Resume" should keep filters but load NEXT cards from the pool, excluding already shown cards (todayShownCardIds).

Additionally, 24h staleness check auto-deletes sessions older than 24 hours. This prevents users from resuming after longer breaks.

## Changes

### Fix 1: Simplify VerbDrillLastSessionState data structure
**Discrepancy:** 10-verb-drill.md#169-207 | **UC:** US-10.20 AC2 | **Spec:** 10-verb-drill.md#169-207

Remove fields that are no longer needed since we're not restoring exact session state:
- Remove: `cards: List<VerbDrillCard>`
- Remove: `currentIndex: Int`
- Remove: `correctCount: Int`
- Remove: `incorrectCount: Int`
- Remove: `timestamp: Long`
- Add: `todayShownCardIds: Set<String>` (track shown cards across sessions)

**Files:**
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillCard.kt` — VerbDrillLastSessionState data class

**Verification:** Data class only has selectedTense, selectedGroup, sortByFrequency, todayShownCardIds

### Fix 2: Remove 24h staleness check
**Discrepancy:** 10-verb-drill.md#726-740 | **UC:** US-10.20 AC2 | **Spec:** 10-verb-drill.md#VD-50

Remove `isSessionFresh()` method and auto-deletion of stale sessions. Dialog should appear whenever last session exists, regardless of age.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt` — remove isSessionFresh(), modify loadLastSession()

**Verification:** loadLastSession() returns session even if older than 24h

### Fix 3: Change onResumeSession() to load next cards
**Discrepancy:** 10-verb-drill.md#753 | **UC:** US-10.20 AC2 | **Spec:** 10-verb-drill.md#10.6.2

Change from restoring exact session to loading new cards with same filters:

Current (WRONG):
```kotlin
fun onResumeSession() {
    val lastSession = verbDrillStore.loadLastSession() ?: return
    _uiState.update {
        it.copy(
            selectedTense = lastSession.selectedTense,
            selectedGroup = lastSession.selectedGroup,
            session = VerbDrillSessionState(
                cards = lastSession.cards,        // SAME CARDS
                currentIndex = lastSession.currentIndex  // SAME POSITION
            )
        )
    }
}
```

New (CORRECT):
```kotlin
fun onResumeSession() {
    val lastSession = verbDrillStore.loadLastSession() ?: return

    // Restore filters
    _uiState.update {
        it.copy(
            selectedTense = lastSession.selectedTense,
            selectedGroup = lastSession.selectedGroup,
            sortByFrequency = lastSession.sortByFrequency
        )
    }

    // Load next cards (excluding todayShownCardIds)
    startVerbDrillSession()  // This already uses todayShownCardIds for filtering
}
```

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt` — onResumeSession() method

**Verification:** onResumeSession() calls startVerbDrillSession() instead of restoring saved session

### Fix 4: Update dialog to show filter context only
**Discrepancy:** 10-verb-drill.md#726-747 | **UC:** US-10.20 | **Spec:** 10-verb-drill.md#VD-50

Remove progress and score from dialog. Only show tense and group filters.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/screens/VerbDrillScreen.kt` — StartFreshResumeDialog composable

**Verification:** Dialog shows only tense and group, no progress/score

### Fix 5: Update startVerbDrillSession() to use todayShownCardIds from last session
**Discrepancy:** NEW | **UC:** US-10.20 AC2 | **Spec:** 10-verb-drill.md#VD-50

When loading cards, merge todayShownCardIds from last session with current progress.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt` — startVerbDrillSession() method

**Verification:** Cards loaded exclude both progress.todayShownCardIds AND lastSession.todayShownCardIds

---

## Verification Checklist
1. VerbDrillLastSessionState only has: selectedTense, selectedGroup, sortByFrequency, todayShownCardIds
2. loadLastSession() does NOT auto-delete sessions older than 24h
3. onResumeSession() keeps filters but calls startVerbDrillSession() for new cards
4. StartFreshResumeDialog shows only tense and group (no progress/score)
5. "Resume" loads next 10 cards excluding already shown today
6. "Start Fresh" clears filters and shows selection screen
7. Dialog appears for sessions of any age (not just <24h)

## Scope Boundaries
**Do NOT touch:**
- Daily Practice logic (different mechanism)
- VerbDrillSelectionScreen (no changes needed)
- TrainingScreen VERB_DRILL sub-mode (no changes needed)
- VerbReferenceBottomSheet, TenseInfoBottomSheet (no changes needed)
- Other drill types (VocabDrill, etc.)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test --tests "*VerbDrill*"` — must pass
3. **Manual verification:**
   - Start Verb Drill, select "Presente" + "regular_are", complete 5 cards, exit
   - Resume → should see same filters, next 10 cards (not same 5)
   - Start Fresh → should see selection screen with cleared filters
4. **Cross-task regression:** Verify Home → Verb Drill → Training → Home flow still works
5. **UC/AC spot-check:** US-10.20 all ACs pass
6. **Spec sync:** Update CHANGELOG.md with spec version

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: VerbDrillLastSessionState | | |
| | Fix 2: Remove 24h staleness | | |
| | Fix 3: onResumeSession() behavior | | |
| | Fix 4: Dialog content | | |
| | Fix 5: todayShownCardIds merge | | |
