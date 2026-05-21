# TASK-082: Daily Practice Resume Dialog Bug - Pack-Scoped Session Detection

**Status:** OPEN  
**Created:** 2026-05-21  
**Branch:** feature/daily-practice-resume-dialog-bug (from main)  
**Priority:** HIGH  
**Complexity:** SIMPLE  
**UC:** UC-23  
**Scenario:** scenario-06-daily-practice.md, scenario-11-navigation.md

---

## Problem Statement

### Bug: Daily practice resume dialog not showing after TASK-080 migration

The daily practice "Repeat vs Continue" resume dialog exists in `GrammarMateApp.kt` (lines 1151-1202) but **never appears** because `hasResumableDailySession()` incorrectly returns `false`.

**Root Cause:**
- `hasResumableDailySession()` uses **GLOBAL** `_state.value.dailyCursor` 
- After TASK-080 migration, daily cursor is **pack-scoped** in `PackDailyCursorStore`
- Global `dailyCursor` in `TrainingProgress` is **empty** for active pack
- Function checks empty cursor → returns `false` → dialog never shows

**Current Broken Logic:**
```kotlin
// DailyPracticeCoordinator.kt:283-289
fun hasResumableDailySession(): Boolean {
    val cursor = _state.value.dailyCursor  // ← WRONG: global cursor, empty after TASK-080
    val today = java.time.LocalDate.now().toString()
    return cursor.firstSessionDate == today &&
        (cursor.firstSessionSentenceCardIds.isNotEmpty() ||
            cursor.firstSessionVerbCardIds.isNotEmpty())
}
```

**User Impact:**

**Scenario:** User has completed first daily session today (pack: EN_WORD_ORDER_A1)
1. User completes daily practice session (all cards via VOICE/KEYBOARD)
2. `PackDailyCursorStore` saves cursor with:
   - `firstSessionDate = "2026-05-21"`
   - `firstSessionSentenceCardIds = [id1, id2, ...]`
   - `firstSessionVerbCardIds = [id1, id2, ...]`
3. User returns to Home screen
4. User taps Daily Practice tile
5. **BUG:** `hasResumableDailySession()` checks global `dailyCursor` (empty)
6. Function returns `false` → dialog skipped → new session starts immediately
7. User loses ability to "Repeat" first session or "Continue" with cursor advancement

**Expected Behavior:**
1. User completes first daily session today
2. User taps Daily Practice tile again
3. Dialog appears: "Repeat first session" or "Continue with new cards"
4. User chooses → appropriate action executed

**Expected Logic:**
```kotlin
fun hasResumableDailySession(): Boolean {
    val cursor = getCurrentPackCursor()  // ← CORRECT: pack-scoped cursor from PackDailyCursorStore
    val today = java.time.LocalDate.now().toString()
    return cursor.firstSessionDate == today &&
        (cursor.firstSessionSentenceCardIds.isNotEmpty() ||
            cursor.firstSessionVerbCardIds.isNotEmpty())
}
```

---

## Acceptance Criteria

### AC1: hasResumableDailySession uses pack-scoped cursor
**Given:** Active pack is EN_WORD_ORDER_A1  
**And:** Pack has cursor with `firstSessionDate = today` and card IDs stored  
**When:** `hasResumableDailySession()` is called  
**Then:** Function loads cursor via `getCurrentPackCursor()` from `PackDailyCursorStore`  
**And:** Function returns `true`

### AC2: Dialog appears when session exists for current pack
**Given:** User completed first daily session today for EN_WORD_ORDER_A1  
**When:** User taps Daily Practice tile on Home screen  
**Then:** `hasResumableDailySession()` returns `true`  
**And:** `DailyResumeDialog` is shown with "Repeat" and "Continue" buttons

### AC3: Session detection works after TASK-080 migration
**Given:** App upgraded to version with TASK-080 pack-scoped migration  
**And:** User has daily cursor in `daily_cursor_{packId}.yaml`  
**When:** User taps Daily Practice tile  
**Then:** Dialog appears if cursor has today's session  
**And:** No cross-pack contamination (each pack has independent dialog state)

### AC4: No regression for multi-pack scenarios
**Given:** User has 2 packs: EN_WORD_ORDER_A1 and IT_VERB_GROUPS_ALL  
**And:** EN pack has today's session (cursor has card IDs)  
**And:** IT pack has no session today (cursor date != today)  
**When:** Active pack is EN  
**And:** User taps Daily Practice  
**Then:** Dialog appears (EN pack has resumable session)  
**When:** User switches to IT pack  
**And:** User taps Daily Practice  
**Then:** Dialog does NOT appear (IT pack has no resumable session)  
**And:** New session starts immediately

---

## Implementation Plan

### Phase 1: Update UC-23 acceptance criteria
**File:** `docs/specification/22-use-case-registry.md`

Add new AC to UC-23:
- **AC5:** `hasResumableDailySession()` uses pack-scoped cursor from `PackDailyCursorStore.getCurrentPackCursor()`, NOT global `dailyCursor`.

### Phase 2: Fix hasResumableDailySession() logic
**File:** `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinator.kt`

**Change:**
```kotlin
// Line 283-289 (BEFORE)
fun hasResumableDailySession(): Boolean {
    val cursor = _state.value.dailyCursor  // ← REMOVE
    val today = java.time.LocalDate.now().toString()
    return cursor.firstSessionDate == today &&
        (cursor.firstSessionSentenceCardIds.isNotEmpty() ||
            cursor.firstSessionVerbCardIds.isNotEmpty())
}

// Line 283-289 (AFTER)
fun hasResumableDailySession(): Boolean {
    val cursor = getCurrentPackCursor()  // ← USE pack-scoped cursor
    val today = java.time.LocalDate.now().toString()
    return cursor.firstSessionDate == today &&
        (cursor.firstSessionSentenceCardIds.isNotEmpty() ||
            cursor.firstSessionVerbCardIds.isNotEmpty())
}
```

**Rationale:**
- `getCurrentPackCursor()` already exists (lines 117-121)
- Loads cursor from `PackDailyCursorStore.loadPackCursor(activePackId)`
- Returns `PackDailyCursorState.forPack(packId)` if not found (empty cursor with default values)
- Aligns with TASK-080 migration architecture

### Phase 3: Update scenario documentation
**File:** `docs/specification/scenario-06-daily-practice.md`

Update section on resume dialog to reflect pack-scoped cursor usage.

### Phase 4: Testing
**Test Case 1:** Dialog appears for today's session
1. Complete first daily session for EN pack
2. Tap Daily Practice tile
3. **Verify:** Dialog shown with Repeat/Continue buttons

**Test Case 2:** No dialog for different pack
1. Complete session for EN pack
2. Switch to IT pack (no session today)
3. Tap Daily Practice tile
4. **Verify:** No dialog, new session starts

**Test Case 3:** No dialog for different day
1. Complete session yesterday
2. Change device date to today
3. Tap Daily Practice tile
4. **Verify:** No dialog (cursor date != today)

---

## Verification Checklist

Before marking task complete, verify:

- [ ] UC-23 updated with AC5 in `22-use-case-registry.md`
- [ ] `hasResumableDailySession()` uses `getCurrentPackCursor()` instead of `_state.value.dailyCursor`
- [ ] Dialog appears when returning to daily practice with existing session
- [ ] Dialog does NOT appear for packs without today's session
- [ ] Multi-pack scenarios work correctly (no cross-pack contamination)
- [ ] Existing tests pass (no regression)
- [ ] Manual testing: complete session → return → dialog shown
- [ ] Manual testing: switch packs → independent dialog state

---

## References

**Related Tasks:**
- **TASK-080:** State isolation bug - introduced pack-scoped `PackDailyCursorStore`
- **TASK-079:** Daily practice pack switching and language grouping

**Source Files:**
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinator.kt:283-289`
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt:1151-1202` (dialog implementation)
- `app/src/main/java/com/alexpo/grammermate/data/PackDailyCursorStore.kt`

**Documentation:**
- `docs/specification/22-use-case-registry.md` (UC-23)
- `docs/specification/scenario-06-daily-practice.md`
- `docs/specification/scenario-11-navigation.md`

---

## Complexity Assessment

**SIMPLE** - Single function change, no new architecture required.

**Change Scope:**
- 1 line change: `_state.value.dailyCursor` → `getCurrentPackCursor()`
- Documentation updates: UC-23 + scenarios
- No data migration needed (TASK-080 already migrated)
- No UI changes (dialog exists, just not showing)

**Risk Level:** LOW
- Function `getCurrentPackCursor()` already exists and is tested
- Change is isolated to single method
- No impact on other daily practice flows
- Easy to verify manually

---

## Notes

**Why this bug wasn't caught in TASK-080:**
- TASK-080 focused on cursor state persistence and migration
- Dialog trigger logic (`hasResumableDailySession`) was not updated
- Existing tests did not cover dialog display after pack switching
- Manual testing likely didn't test "complete session → return → dialog" flow

**Prevention for future:**
- Add acceptance criterion to TASK-080: "Dialog appears for resumable sessions after migration"
- Add integration test: `testDailyResumeDialogAppearsAfterFirstSession`
- Update scenario-06 to include dialog trigger verification
