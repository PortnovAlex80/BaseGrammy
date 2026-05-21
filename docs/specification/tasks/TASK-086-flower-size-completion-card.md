# TASK-086: Flower Size Inconsistency — Completion Card Too Large

**Status:** OPEN
**Created:** 2026-05-21
**Branch:** fix/flower-size-completion-card (from main)
**Priority:** LOW
**Complexity:** SIMPLE
**UC:** UC-02, UC-06
**Scenario:** scenario-01-training-flow.md

---

## Problem Statement

### Bug: Flower displayed too large on completion card compared to home screen

**User Report:** "когда захожу на главный экран. у нас цветок большой, а потом когда заходим в сами уроки - он становится правильным"

Translation: When going to main screen, the flower is large, but when going to lessons, it becomes correct size.

**Current Broken Behavior:**
1. User is on Home Screen - flower shown at correct size (18.sp base)
2. User completes a lesson - Completion Card shows flower
3. **BUG:** Completion Card flower is 2.67x larger than home screen flower
4. User returns to Home Screen - flower size is correct again

**Expected Behavior:**
- Flowers should be consistent size across all screens (home, lessons, completion)

---

## Root Cause Analysis

### Size Comparison Across Screens

| Screen | Component | Base Font Size | Scale | Result |
|--------|-----------|----------------|-------|--------|
| **Home Screen** | LessonTile | 18.sp | scaleMultiplier | ✅ Correct |
| **Lesson Roadmap** | Exercise Tile | 18.sp | scaleMultiplier | ✅ Correct |
| **Lesson Roadmap** | Completion Card | **48.sp** | scaleMultiplier | ❌ **Too Large** |

**Location:** `app/src/main/java/com/alexpo/grammermate/ui/screens/LessonRoadmapScreen.kt:446`

**Broken Code:**
```kotlin
Text(text = emoji, fontSize = (48 * scale).sp)  // ❌ 48.sp is 2.67x larger
```

**Correct Code (should be):**
```kotlin
Text(text = emoji, fontSize = (18 * scale).sp)  // ✅ Consistent with other screens
```

---

## Acceptance Criteria

### AC1: Completion card flower uses consistent base size
**Given:** User completes a lesson
**When:** Completion Card is displayed
**Then:** Flower uses 18.sp base size (same as home screen)

### AC2: All flowers consistent across screens
**Given:** User views home screen, lesson tiles, completion card
**When:** Comparing flower sizes
**Then:** All flowers use 18.sp base size with scaleMultiplier

---

## Implementation Plan

### Phase 1: Fix completion card flower size

**File:** `app/src/main/java/com/alexpo/grammermate/ui/screens/LessonRoadmapScreen.kt`

**Change (line 446):**
```kotlin
// BEFORE:
Text(text = emoji, fontSize = (48 * scale).sp)

// AFTER:
Text(text = emoji, fontSize = (18 * scale).sp)
```

---

## Verification Checklist

Before marking task complete, verify:

- [ ] Completion card flower size changed from 48.sp to 18.sp
- [ ] Manual test: Complete lesson, verify flower size matches home screen
- [ ] Manual test: Compare flower sizes across all screens
- [ ] Existing tests pass (no regression)

---

## Scope Boundaries

**DO NOT touch:**
- Home screen flower sizing (already correct)
- Lesson tile flower sizing (already correct)
- FlowerCalculator or scaleMultiplier logic

**Focus ONLY on:**
- Completion card flower base font size

---

## Regression Plan

After fix is implemented, run:

1. **Build:** `assembleDebug` - must pass with no errors
2. **Tests:** `test` - must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above

---

## References

**Related Tasks:**
- None (standalone UI fix)

**Source Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/screens/LessonRoadmapScreen.kt`

**Documentation:**
- `docs/specification/22-use-case-registry.md` - UC-02 (Start lesson), UC-06 (Complete lesson)

---

## Complexity Assessment

**SIMPLE** - Single line change.

**Change Scope:**
- Change 1 number: 48 → 18
- Total: 1 character change

**Risk Level:** LOW
- Purely visual change
- No functional impact
- Easy to verify visually
- No data migration needed

---

## Notes

**Why this wasn't caught earlier:**
- Likely intentional design choice (make completion card flower prominent)
- User feedback indicates this was incorrect - should be consistent
- No automated tests for visual consistency

**User Impact:**
- Flowers will now be consistent size across all screens
- Completion card flower will match home screen and lesson tile flowers

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Phase 1: Fix completion card flower size | | |
