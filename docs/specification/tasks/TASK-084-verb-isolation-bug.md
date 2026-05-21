# TASK-084: Verb Isolation Bug — Wrong Pack Verbs in Daily Practice Block 3

**Status:** OPEN
**Created:** 2026-05-21
**Branch:** feature/verb-isolation-bug (from main)
**Priority:** HIGH
**Complexity:** MODERATE
**UC:** UC-21, UC-24
**Scenario:** scenario-06-daily-practice.md

---

## Problem Statement

### Bug: Daily Practice Block 3 (VERBS) shows verbs from wrong pack after switch

**User Report:** "глаголы из неправильного пакета после Update" (verbs from wrong pack after Update)

**Current Broken Behavior:**
1. User has 2 packs installed: EN_WORD_ORDER_A1 and IT_VERB_GROUPS_ALL
2. User completes daily practice session for EN pack
3. User switches active pack to IT
4. User starts new daily practice session
5. **BUG:** Block 3 (VERBS) shows English verb conjugations instead of Italian
6. Block 1 (TRANSLATE) and Block 2 (VOCAB) work correctly (show Italian content)

**Expected Behavior:**
- All 3 blocks should show content from the active pack (IT)
- Switching packs should invalidate ALL cached data from previous pack

---

## Root Cause Analysis

### Infrastructure is Already Pack-Scoped ✅

Research confirms the underlying storage is correctly isolated:
- **VerbDrillStore:** Uses pack-scoped storage via StoreFactory
- **Drill files:** Located in `grammarmate/drills/{packId}/verb_drill/`
- **Language filtering:** Files match `{languageId}_*.csv` pattern
- **Progress tracking:** Pack-scoped in `verb_drill_progress_{languageId}.yaml`
- **Cache keys:** Use "$packId:$languageId" format

**The bug is NOT a migration issue.**

### Actual Problem: Cache Invalidation Failure

**Location:** `DailySessionComposer.kt`

**Broken Logic:**
```kotlin
// Cache invalidation is NOT called when pack changes
fun buildVerbBlock(cursor: PackDailyCursorState, packId: String): List<TrainingCard> {
    // This uses cached verb cards from PREVIOUS pack
    val verbCards = loadVerbDrillCards(packId, languageId)
    // ...
}
```

**Cache Invalidation Method Exists but Not Used:**
```kotlin
fun invalidateCache(packId: String? = null, languageId: String? = null) {
    if (packId != null && languageId != null) {
        val key = "$packId:$languageId"
        if (cachedVerbDrillCards?.first == key) cachedVerbDrillCards = null
    } else {
        cachedVerbDrillCards = null
    }
}
```

**Problem:** `invalidateCache()` is called in some places but NOT when:
1. User switches active pack via HomeScreen
2. Daily practice session starts with new pack
3. Session is rebuilt after `SessionInvalidatedException` (TASK-079)

---

## Acceptance Criteria

### AC1: Cache invalidated on pack switch
**Given:** User has completed daily practice for EN_WORD_ORDER_A1
**And:** Verb cards are cached with key "en_word_order_a1:en"
**When:** User switches active pack to IT_VERB_GROUPS_ALL
**And:** User starts new daily practice session
**Then:** `DailySessionComposer.invalidateCache()` is called with packId="it_verb_groups_all"
**And:** Previous EN verb cache is cleared
**And:** Block 3 loads Italian verb cards

### AC2: Cache invalidated on session rebuild
**Given:** User has active daily practice session for EN pack
**When:** User switches active pack mid-session (TASK-079 SessionInvalidatedException)
**And:** Session is rebuilt for IT pack
**Then:** `DailySessionComposer.invalidateCache()` is called
**And:** New Block 3 shows Italian verbs (not cached English verbs)

### AC3: Cache key validation prevents cross-pack contamination
**Given:** Cache contains verb cards with key "en_word_order_a1:en"
**When:** `buildVerbBlock()` is called with packId="it_verb_groups_all"
**Then:** Cache key mismatch detected
**And:** Cache is invalidated before loading new verb cards
**And:** Italian verb cards are loaded

### AC4: All three blocks use same pack after switch
**Given:** User switches from EN to IT pack
**When:** Daily practice session starts
**Then:** Block 1 (TRANSLATE) shows Italian sentences
**And:** Block 2 (VOCAB) shows Italian vocabulary
**And:** Block 3 (VERBS) shows Italian verb conjugations
**And:** No cross-pack contamination in any block

---

## Implementation Plan

### Phase 1: Add Cache Invalidation Triggers

**File:** `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinator.kt`

**Changes:**

1. **Invalidate cache when starting new daily practice:**
```kotlin
fun startDailyPractice() {
    val activePackId = _state.value.activePackId?.value ?: return

    // ← ADD THIS: Clear cache from previous pack
    sessionComposer.invalidateCache()

    // ... existing code ...
}
```

2. **Invalidate cache on session rebuild:**
```kotlin
private fun rebuildSessionAfterPackSwitch(newPackId: String) {
    // ← ADD THIS: Clear cache before rebuild
    sessionComposer.invalidateCache(newPackId, state.value.languageId)

    // ... existing rebuild code ...
}
```

3. **Invalidate cache when repeating session:**
```kotlin
fun repeatDailyPractice() {
    // ← ADD THIS: Clear cache before repeat
    sessionComposer.invalidateCache()

    // ... existing code ...
}
```

### Phase 2: Enhance Cache Validation

**File:** `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionComposer.kt`

**Changes:**

1. **Add cache key validation in buildVerbBlock():**
```kotlin
fun buildVerbBlock(cursor: PackDailyCursorState, packId: String): List<TrainingCard> {
    val languageId = currentState.value.languageId
    val expectedKey = "$packId:$languageId"

    // ← ADD THIS: Validate cache key matches current pack
    if (cachedVerbDrillCards?.first != expectedKey) {
        cachedVerbDrillCards = null  // Clear stale cache
    }

    val verbCards = loadVerbDrillCards(packId, languageId)
    // ... existing code ...
}
```

2. **Add comprehensive invalidation:**
```kotlin
fun invalidateCache(packId: String? = null, languageId: String? = null) {
    if (packId != null && languageId != null) {
        val key = "$packId:$languageId"
        // ← FIX: Clear cache if key doesn't match (not just if it matches)
        if (cachedVerbDrillCards?.first != key) {
            cachedVerbDrillCards = null
        }
    } else {
        cachedVerbDrillCards = null
    }
}
```

### Phase 3: Add Debug Logging

**File:** `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionComposer.kt`

**Changes:**

1. **Log cache hits/misses:**
```kotlin
private fun loadVerbDrillCards(packId: String, languageId: String): List<VerbDrillCard> {
    val key = "$packId:$languageId"

    if (cachedVerbDrillCards?.first == key) {
        Log.d("DailySessionComposer", "Cache HIT for verb cards: $key")
        return cachedVerbDrillCards!!.second
    }

    Log.d("DailySessionComposer", "Cache MISS for verb cards: $key - loading from storage")
    // ... load from storage ...
}
```

2. **Log pack transitions:**
```kotlin
fun invalidateCache(packId: String? = null, languageId: String? = null) {
    Log.d("DailySessionComposer", "Invalidating verb cache for pack: $packId, lang: $languageId")
    // ... existing code ...
}
```

---

## Verification Checklist

Before marking task complete, verify:

- [ ] Cache invalidated in `startDailyPractice()`
- [ ] Cache invalidated in `rebuildSessionAfterPackSwitch()`
- [ ] Cache invalidated in `repeatDailyPractice()`
- [ ] Cache key validation added to `buildVerbBlock()`
- [ ] Enhanced invalidation logic clears stale cache
- [ ] Debug logging added for cache hits/misses
- [ ] Manual test: Switch EN → IT, verify Block 3 shows Italian verbs
- [ ] Manual test: Switch IT → EN, verify Block 3 shows English verbs
- [ ] Manual test: All 3 blocks show same language after switch
- [ ] Existing tests pass (no regression)
- [ ] Integration test: `testVerbBlockCacheInvalidationOnPackSwitch`

---

## Scope Boundaries

**DO NOT touch:**
- VerbDrillStore implementation (already pack-scoped)
- Drill file storage structure (already correct)
- Verb progress tracking (already isolated)
- Block 1 (TRANSLATE) logic (already works correctly)
- Block 2 (VOCAB) logic (already works correctly)

**Focus ONLY on:**
- Cache invalidation triggers in DailyPracticeCoordinator
- Cache validation in DailySessionComposer
- Debug logging for troubleshooting

---

## Regression Plan

After all fixes are implemented, run:

1. **Build:** `assembleDebug` - must pass with no errors
2. **Tests:** `test` - must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that TASK-079 (session invalidation) still works
5. **Pack transition test:** switch between all installed packs, verify all blocks update correctly
6. **UC/AC spot-check:** read UC-21 and UC-24 from `22-use-case-registry.md`, confirm ACs hold
7. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

---

## References

**Related Tasks:**
- **TASK-079:** Daily practice pack switching - introduced SessionInvalidatedException
- **TASK-080:** State isolation bug - migrated cursor to pack-scoped
- **TASK-083:** Migration trigger - ensures pack-scoped data migrated on update

**Source Files:**
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinator.kt` - add cache invalidation triggers
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionComposer.kt` - enhance cache validation
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt` - reference for pack-scoped pattern

**Documentation:**
- `docs/specification/22-use-case-registry.md` - UC-21 (Start daily practice), UC-24 (Cursor tracking)
- `docs/specification/scenario-06-daily-practice.md` - Daily practice flows

---

## Complexity Assessment

**MODERATE** - Cache management refinement, no data migration needed.

**Change Scope:**
- Add 3 cache invalidation calls in DailyPracticeCoordinator (~6 lines)
- Add cache key validation in buildVerbBlock() (~5 lines)
- Enhance invalidateCache() method (~3 lines)
- Add debug logging (~10 lines)
- Total: ~24 lines of code

**Risk Level:** MEDIUM
- Changes affect caching behavior (performance impact if over-invalidated)
- Requires testing of pack transitions (hard to automate)
- No data loss risk (cache is in-memory only)
- Easy to verify manually (switch packs, check Block 3 language)

**Why not SIMPLE:**
- Requires understanding of cache lifecycle
- Needs coordination between DailyPracticeCoordinator and DailySessionComposer
- Debugging requires tracing cache transitions
- Testing requires manual pack switching

---

## Notes

**Why this wasn't caught in TASK-080:**
- TASK-080 focused on data storage migration (cursor → pack-scoped)
- Cache layer was assumed to auto-invalidate based on cache key
- No integration tests for pack transitions with cached data
- Manual testing likely didn't test "complete session → switch pack → new session" flow

**Prevention for future:**
- Add integration test: `testPackSwitchInvalidatesAllCaches`
- Add acceptance criterion to TASK-079: "All caches cleared on session invalidation"
- Document cache invalidation pattern in `08-training-viewmodel.md`
- Add cache monitoring to detect stale cache usage

**Cache Invalidation Pattern:**
This task establishes the pattern for cache invalidation on pack transitions:
1. **Explicit invalidation** on state changes (pack switch, session rebuild)
2. **Validation** before using cached data (key mismatch → clear)
3. **Logging** for debugging (track hits/misses/invalidations)

Future features that use caching should follow this pattern.

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Phase 1: Add cache invalidation triggers | | |
| | Phase 2: Enhance cache validation | | |
| | Phase 3: Add debug logging | | |
