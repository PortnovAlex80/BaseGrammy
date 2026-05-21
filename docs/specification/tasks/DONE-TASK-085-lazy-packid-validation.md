# TASK-085: Daily Practice Lazy Loading PackId Validation

**Status:** DONE
**Created:** 2026-05-21
**Branch:** fix/lazy-packid-validation (from main)
**Priority:** CRITICAL
**Complexity:** SIMPLE
**UC:** UC-21, UC-24
**Scenario:** scenario-06-daily-practice.md

---

## Problem Statement

### Bug: Daily practice doesn't actively ask which pack it's working with

**User Report:** "не сработало, нужно чтобы дейли практика сама спрашивала какой пакет, а не пассивно запрашивала. может там ленивая загрузка?"

**Current Broken Behavior:**
1. User starts daily practice session for EN_WORD_ORDER_A1 pack
2. User switches active pack to IT_VERB_GROUPS_ALL
3. User starts new daily practice session
4. **BUG:** Daily practice shows content from EN pack (cached session with stale packId)
5. **ROOT CAUSE:** Daily practice caches sessions with packId captured at creation time, never refreshes it

**Expected Behavior:**
1. User starts daily practice session for EN_WORD_ORDER_A1 pack
2. User switches active pack to IT_VERB_GROUPS_ALL
3. User starts new daily practice session
4. **FIXED:** Daily practice validates packId, clears stale cache, rebuilds session with IT pack content

---

## Root Cause Analysis

### Lazy Loading + Caching Issue

**Location:** `DailyPracticeCoordinator.kt`

**Problem 1: Prebuilt Session Cache (lines 336-359)**
```kotlin
val cached = prebuiltDailyBlocks
if (isFirstSessionToday && cached != null && cached.isNotEmpty()) {
    // ❌ Uses cached session without validating packId
    lastDailyBlocks = cached
    startDailySession(cached, effectiveLevel, packId.value)  // packId captured at cache creation
}
```

**Problem 2: In-Memory Repeat Cache (lines 407-410)**
```kotlin
val cached = lastDailyBlocks
if (cached != null && cached.isNotEmpty()) {
    // ❌ Uses cached session without validating packId
    startDailySession(cached, lessonLevel, packId.value)  // packId captured at session creation
}
```

**Problem 3: No Dynamic PackId Query**
- Daily practice reads packId only at session creation (line 311)
- Never refreshes packId from current UI state
- No validation that cached session matches current active pack

---

## Acceptance Criteria

### AC1: Prebuilt cache validates packId before use
**Given:** User has cached daily practice session for EN pack
**When:** User switches to IT pack and starts new daily practice
**Then:** Prebuilt cache validation detects packId mismatch
**And:** Cache is cleared and session rebuilt with IT pack content

### AC2: Repeat cache validates packId before use
**Given:** User has completed daily practice session for EN pack
**When:** User switches to IT pack and repeats daily practice
**Then:** Repeat cache validation detects packId mismatch
**And:** Cache is cleared and session rebuilt with IT pack content

### AC3: Daily practice actively queries current packId
**Given:** User switches packs during active daily practice session
**When:** Daily practice needs to load content
**Then:** Coordinator queries `stateAccess.uiState.value.navigation.activePackId?.value` for current packId
**And:** Does not rely on cached/stale packId values

### AC4: Cache invalidation logged
**Given:** PackId mismatch detected in cached session
**When:** Cache is invalidated
**Then:** Log message indicates which packId was cached vs current
**And:** Log indicates cache was cleared and session rebuilt

---

## Implementation Plan

### Phase 1: Add packId validation to prebuilt cache

**File:** `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinator.kt`

**Changes (lines 343-351):**
```kotlin
val cached = prebuiltDailyBlocks
if (isFirstSessionToday && cached != null && cached.isNotEmpty()) {
    // NEW: Validate that cached session matches current pack
    val currentPackId = stateAccess.uiState.value.navigation.activePackId?.value
    val cachedPackId = packId.value
    
    if (cachedPackId != currentPackId) {
        Log.i(logTag, "Prebuilt cache packId mismatch: cached=$cachedPackId, current=$currentPackId. Clearing cache.")
        prebuiltDailyBlocks = null
        prebuiltSessionLevel = 0
        // Fall through to rebuild session
    } else {
        val levelMismatch = levelFromCursor && prebuiltSessionLevel != effectiveLevel
        if (levelMismatch) {
            prebuiltDailyBlocks = null
            prebuiltSessionLevel = 0
        } else {
            lastDailyBlocks = cached
            startDailySession(cached, effectiveLevel, packId.value)
            return true
        }
    }
}
```

### Phase 2: Add packId validation to repeat cache

**Changes (lines 424-431):**
```kotlin
val cached = lastDailyBlocks
if (cached != null && cached.isNotEmpty()) {
    // NEW: Validate that cached session matches current pack
    val currentPackId = stateAccess.uiState.value.navigation.activePackId?.value
    val cachedPackId = packId.value
    
    if (cachedPackId != currentPackId) {
        Log.i(logTag, "Repeat cache packId mismatch: cached=$cachedPackId, current=$currentPackId. Clearing cache.")
        lastDailyBlocks = null
        // Fall through to rebuild session
    } else {
        startDailySession(cached, lessonLevel, packId.value)
        return true
    }
}
```

### Phase 3: Add helper method for dynamic packId query

**Changes (lines 107-110):**
```kotlin
/** Get the current active pack ID dynamically (not cached). */
private fun getCurrentActivePackId(): String? {
    return stateAccess.uiState.value.navigation.activePackId?.value
}
```

---

## Verification Checklist

Before marking task complete, verify:

- [ ] Prebuilt cache validation added (lines 343-351)
- [ ] Repeat cache validation added (lines 424-431)
- [ ] Helper method getCurrentActivePackId() added (lines 107-110)
- [ ] Log messages added for cache invalidation events
- [ ] Manual test: Switch EN → IT packs, verify daily practice shows IT content
- [ ] Manual test: Repeat after pack switch, verify correct pack content
- [ ] Manual test: All 3 blocks show content from current pack
- [ ] Existing tests pass (no regression)

---

## Scope Boundaries

**DO NOT touch:**
- Session state creation logic (packId capture at creation is correct)
- Cache invalidation in getCurrentBlock() (already working correctly)
- Daily practice business logic (unrelated to caching)

**Focus ONLY on:**
- Adding packId validation before using cached sessions
- Ensuring cached sessions match current active pack
- Adding logging for debugging cache invalidation

---

## Regression Plan

After all fixes are implemented, run:

1. **Build:** `assembleDebug` - must pass with no errors
2. **Tests:** `test` - must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that TASK-082/083/081/084 still work
5. **Pack transition test:** switch between all installed packs, verify correct content
6. **UC/AC spot-check:** read UC-21 and UC-24 from `22-use-case-registry.md`, confirm ACs hold

---

## References

**Related Tasks:**
- **TASK-080:** State isolation bug - established pack-scoped pattern
- **TASK-082:** Daily resume dialog - uses pack-scoped cursor
- **TASK-083:** Migration trigger - ensures pack-scoped data migrated
- **TASK-084:** Verb isolation bug - cache invalidation pattern

**Source Files:**
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinator.kt`

**Documentation:**
- `docs/specification/22-use-case-registry.md` - UC-21 (Start daily practice), UC-24 (Cursor tracking)
- `docs/specification/scenario-06-daily-practice.md` - Daily practice flows

---

## Complexity Assessment

**SIMPLE** - Cache validation only, no architecture changes.

**Change Scope:**
- Add packId validation in 2 locations (~16 lines)
- Add helper method (~4 lines)
- Add logging (~4 lines)
- Total: ~24 lines of code

**Risk Level:** LOW
- Changes are additive (validation only)
- No breaking changes to existing behavior
- Easy to verify manually (switch packs, check content)
- Graceful fallback (cache miss → rebuild session)

---

## Notes

**Why this wasn't caught earlier:**
- Tests don't simulate pack switching during active sessions
- Tests use fixed packId values, never change them
- No integration test for "cache session → switch pack → validate cache"

**User feedback was critical:**
- User correctly identified lazy loading as root cause
- User emphasized "actively ask" vs "passively query"
- This led directly to the solution: validate cached packId vs current packId

**Pattern established:**
This task establishes the pattern for cache validation in daily practice:
1. **Always validate** cached data matches current state before use
2. **Invalidate stale cache** when mismatch detected
3. **Log invalidation events** for debugging
4. **Rebuild with correct data** after invalidation

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-21 | Phase 1: Prebuilt cache validation | DONE | Added packId validation before using prebuilt cached sessions |
| 2026-05-21 | Phase 2: Repeat cache validation | DONE | Added packId validation before using repeat cached sessions |
| 2026-05-21 | Phase 3: Helper method | DONE | Added getCurrentActivePackId() for dynamic packId query |
| 2026-05-21 | Full implementation | DONE | All phases complete, commit 6e75357 |
| 2026-05-21 | User confirmation | DONE | User confirmed "работает эта часть" |
