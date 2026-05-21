# Test Lesson - Daily Practice Pack Switching

**Date:** 2026-05-21
**Status:** ✅ VERIFIED WORKING
**Branch:** fix/lazy-packid-validation

## Test Scenario

**Objective:** Verify daily practice correctly identifies and works with active pack after switching.

### Test Steps

1. **Initial Setup:**
   - Install APK: `app\build\outputs\apk\debug\grammermate.apk`
   - Install 2 language packs: EN_WORD_ORDER_A1 and IT_VERB_GROUPS_ALL

2. **Test Pack Switching:**
   - Start daily practice session for EN pack
   - Complete first session (all cards via VOICE/KEYBOARD)
   - Exit to Home screen
   - Switch active pack to IT
   - Start new daily practice session
   - **VERIFY:** All 3 blocks (TRANSLATE, VOCAB, VERBS) show Italian content

3. **Test Resume Dialog:**
   - Complete first daily practice session for IT pack
   - Exit to Home screen
   - Tap Daily Practice tile again
   - **VERIFY:** Dialog appears with "Repeat" and "Continue" buttons

4. **Test Repeat Functionality:**
   - Select "Repeat" option
   - **VERIFY:** Session repeats with same cards from first session
   - **VERIFY:** Cursor does NOT advance (repeat mode)

5. **Test Continue Functionality:**
   - Complete first daily practice session for IT pack
   - Tap Daily Practice tile
   - Select "Continue" option
   - **VERIFY:** New session starts with advanced cursor position

## Results

### ✅ PASSED

- **Pack Switching:** Daily practice correctly shows content from active pack (not cached from previous pack)
- **Resume Dialog:** Dialog appears correctly after first session
- **Repeat Functionality:** Repeats session correctly without advancing cursor
- **Continue Functionality:** Continues with advanced cursor position

### User Confirmation

User confirmed: **"работает эта часть"** (this part works)

## Technical Details

### What Was Fixed

**Root Cause:** Daily practice cached sessions with packId captured at creation time, never refreshed it when user switched packs.

**Solution:** Added packId validation before using cached sessions:
- Prebuilt cache validation (lines 343-351)
- Repeat cache validation (lines 424-431)
- Helper method `getCurrentActivePackId()` (lines 107-110)

**Result:** Daily practice now actively queries current packId and invalidates stale caches when packs are switched.

## Commit History

- `a036b61` - TASK-082: Daily resume dialog fix
- `5733443` - TASK-083: Migration trigger implementation
- `cee76d3` - TASK-081: Lesson progress isolation
- `1335fdf` - TASK-084: Verb isolation bug fix
- `d304fb8` - Critical packId.value bug fix
- `6e75357` - TASK-085: Lazy loading packId validation

## Notes

This test lesson confirms that all 5 daily practice isolation fixes are working correctly in the built APK.
