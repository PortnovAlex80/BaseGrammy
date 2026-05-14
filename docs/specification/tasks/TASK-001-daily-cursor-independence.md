# TASK-001: Daily Practice Cursor Independence

**Status:** DONE
**Created:** 2026-05-14
**Branch:** feature/daily-cursor-independence (from feature/arch-feature-migration)
**Spec:** 09-daily-practice.md (sections 9.1, 9.5.1, 9.6.1, 9.6.4, 9.8.7)
**UC:** UC-21 (AC5-AC6), UC-24 (AC2 reversed), UC-60, UC-61
**Scenario:** scenario-06-daily-practice.md (discrepancies #2, #7, #8)

---

## Problem

Daily Practice uses `resolveProgressLessonInfo()` (mastery-based level) to determine which tenses to show in Block 3. This is independent of `DailyCursorState`, creating desync:

- User on lesson 1 sees Imperfetto verbs (level 2 tenses) in Block 3
- `currentLessonIndex` never advances when cards are exhausted — Block 1 empties permanently
- Block 3 returns empty when all verbs for active tenses have been shown

## Changes (3 fixes)

### Fix 1: Cursor-based level resolution

**Discrepancy:** #7 | **UC:** UC-21 AC5-AC6 | **Spec:** 09#9.5.1, 09#9.6.1

Replace `resolveProgressLessonInfo()` with cursor-based level:

```
effectiveLevel = DailyCursorState.currentLessonIndex + 1
lessonId = lesson at cursor.currentLessonIndex
```

**Files:** `feature/daily/DailyPracticeCoordinator.kt` — `startDailyPractice()`, `prebuildSession()`

**Rules:**
- Prebuilt cache must validate cursor level matches; discard if mismatch
- `resolveProgressLessonInfo()` is NOT called for daily practice anymore

### Fix 2: Lesson transition in advanceCursor()

**Discrepancy:** #2 | **UC:** UC-24 AC2, UC-61 | **Spec:** 09#9.6.4

Advance `currentLessonIndex` when `sentenceOffset` exceeds lesson card count:

```
sentenceOffset += sentenceCount
if (sentenceOffset >= currentLesson.cards.size) {
    currentLessonIndex++
    sentenceOffset = 0
    if (currentLessonIndex >= packLessons.size) {
        currentLessonIndex = 0  // pack wrap
    }
}
```

**Files:** `feature/daily/DailyPracticeCoordinator.kt` — `advanceDailyCursor()`

### Fix 3: Verb cycling in buildVerbBlock()

**Discrepancy:** #8 | **UC:** UC-60 | **Spec:** 09#9.5.1

When all verb cards for active tenses have been shown, cycle instead of returning empty:

```
if (unshown.isEmpty()) {
    candidateCards = filtered  // all cards matching active tenses
} else {
    candidateCards = unshown
}
```

Weakness scores still apply — least-practiced forms appear first.

**Files:** `feature/daily/DailySessionComposer.kt` — `buildVerbBlock()`

---

## Verification Checklist (from 09#9.8.7)

1. `currentLessonIndex=0` (level 1) → Block 3 contains ONLY Presente verbs
2. `currentLessonIndex=1` (level 2) → Block 3 contains Presente + Imperfetto
3. `currentLessonIndex` matches for Block 1 and Block 3 within a session
4. Block 3 never returns empty when active tenses have cards (cycling kicks in)
5. Browsing a different lesson on roadmap does NOT change daily cursor

## Scope Boundaries

**Do NOT touch:**
- `DailyCursorState` fields (no new fields)
- Block 2 (VOCAB) — independent of cursor
- Regular training / `selectedLessonId` — separate path
- Mastery/Flower logic — shared, unchanged

## Git

One commit per fix or one combined commit. Footer:
```
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-14 | Fix 1: Cursor-based level | DONE | DailyPracticeCoordinator.startDailyPractice() derives effectiveLevel from cursor.currentLessonIndex+1 |
| 2026-05-14 | Fix 2: Lesson transition | DONE | DailyPracticeCoordinator.advanceDailyCursor() wraps lessons within pack |
| 2026-05-14 | Fix 3: Verb cycling | DONE | DailySessionComposer.buildVerbBlock() cycles when all cards shown |
| 2026-05-14 | Verification checklist | DONE | UC-60 PASS, UC-21 AC5-AC6 PASS, UC-24 PASS, UC-61 PASS. Build clean. |
