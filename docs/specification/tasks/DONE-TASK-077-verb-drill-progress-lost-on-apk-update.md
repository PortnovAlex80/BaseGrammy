# TASK-077: Verb Drill Progress Lost on APK Update

**Status:** DONE
**Created:** 2026-05-20
**Branch:** feature/verb-drill-progress-persistence (from main)
**Spec:** `10-verb-drill.md#10.3, 10-verb-drill.md#10.5.7`
**UC:** US-10.4, US-10.5, US-10.20
**Scenario:** `scenario-07-verb-drill.md`

---

## Problem

When the APK is updated (new version installed over old version), verb drill progress is lost:
- `everShownCardIds` resets (all cards shown again)
- `lastSessionState` is lost (no "Повторить"/"Продолжить" options)
- User must start practice from scratch

**Expected behavior:** Files in `context.filesDir/grammarmate/` should persist across APK updates. This is standard Android behavior — `filesDir` is not cleared on app update.

**Actual behavior:** Progress is lost after APK update.

---

## Changes

### Investigation 1: Verify VerbDrillStore file paths and write behavior
**Discrepancy:** Unknown | **UC:** US-10.4, US-10.5 | **Spec:** `10-verb-drill.md#10.3`

Determine root cause by investigating:
1. **File path verification**: Confirm `VerbDrillStore` uses correct `filesDir` path
2. **Write verification**: Confirm `AtomicFileWriter.writeText()` actually flushes to disk
3. **packId consistency**: Verify `packId` doesn't change between versions
4. **File existence**: Check if files exist before vs after update

**Files:**
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt` — lines 49-64 (file paths), 185-203 (persistProgressToDisk)
- `app/src/main/java/com/alexpo/grammermate/data/AtomicFileWriter.kt`

**Verification:**
- Add logging to confirm file paths and write operations
- Test APK update scenario (install old version, practice, install new version, check progress)

### Fix 1: Apply root cause fix (TBD after investigation)
**Discrepancy:** TBD | **UC:** US-10.4, US-10.5 | **Spec:** `10-verb-drill.md#10.3`

Apply fix based on Investigation 1 findings.

**Files:** TBD

**Verification:** After APK update, verb drill progress persists:
- `everShownCardIds` preserved
- `lastSessionState` preserved (Повторить/Продолжить available)

---

## Verification Checklist
1. After APK update, `verb_drill_progress.yaml` exists in `filesDir/grammarmate/drills/{packId}/`
2. After APK update, `verb_drill_last_session.yaml` exists in `filesDir/grammarmate/drills/{packId}/`
3. `everShownCardIds` in progress file matches pre-update state
4. `lastSessionContext` is non-null after update if session existed before
5. "Повторить" and "Продолжить" buttons work correctly after update
6. New cards are excluded based on `todayShownCardIds`/`everShownCardIds` after update
7. Other stores (MasteryStore, ProgressStore) also persist correctly (cross-check)

## Scope Boundaries
**Do NOT touch:**
- Verb drill UI components (VerbDrillScreen, sheets)
- Verb drill CSV parsing or loading
- Other features (vocab drill, daily practice)
- APK build/install process

**Focus ONLY on:**
- VerbDrillStore file persistence
- AtomicFileWriter behavior
- File path resolution

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** Check each item from Verification Checklist above
4. **Cross-task regression:** Verify regular lesson training still works, mastery persists
5. **UC/AC spot-check:** Confirm US-10.4 (track progress), US-10.5 (daily card pool), US-10.20 (resume session) work
6. **Spec sync:** Update `10-verb-drill.md` if code diverges from spec

## Git
One commit for investigation findings, one for fix.
Commit footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-20 | Investigation 1: Verify VerbDrillStore file paths and write behavior | DONE | Root cause: reloadForLanguage() not updating store |
| 2026-05-20 | Fix 1: Apply root cause fix | DONE | Updated reloadForLanguage() to use currentPackId |
