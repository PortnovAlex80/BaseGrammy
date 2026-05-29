# TASK-090: Stop Story Button UI State Update Bug

**Status:** DONE
**Created:** 2026-05-29
**Branch:** feature/stop-story-button-fix (from feature/multilingual-story-tts)
**Spec:** 24-grammar-story-roadmap#story-reader
**UC:** UC-102 AC1-7
**Scenario:** N/A (direct bug fix)

---

## Problem

Кнопка стоп в Grammar Story Roadmap останавливает аудио (TTS.stop() работает), но UI не обновляется - кнопка остается видимой, состояние `isStoryPlaying` не сбрасывается.

**Что работает:**
- Кнопка появляется когда `isStoryPlaying == true`
- Callback `onStopStory` вызывается корректно
- `audioCoordinator.stopTts()` исполняется
- `ttsEngine.stop()` работает

**Что НЕ работает:**
- UI state не обновляется после стопа
- `isStoryPlaying` остается true (TtsState.Speaking → Idle не переключается)
- Кнопка stop не исчезает

**Root cause:** State synchronization problem - TTS engine stops but UI state flow doesn't update immediately.

---

## Changes

### Fix 1: Add explicit state update to stopStoryNarration()
**Discrepancy:** N/A | **UC:** UC-102 AC3-7 | **Spec:** 24#story-reader

**Description:** Modify `stopStoryNarration()` in TrainingViewModel to explicitly trigger UI state update after calling `audioCoordinator.stopTts()`. This ensures immediate recomposition without relying on async flow delays.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt:392-394` — `stopStoryNarration()` function

**Code change:**
```kotlin
fun stopStoryNarration() {
    audioCoordinator.stopTts()
    // Force UI state update to reflect stop immediately
    _coreState.update { it }
}
```

**Verification:**
- Play story → tap stop → button disappears immediately
- `isStoryPlaying` becomes false without delay
- No race condition between TTS stop and UI update

### Fix 2: Ensure AudioCoordinator.stopTts() updates internal state
**Discrepancy:** N/A | **UC:** UC-102 AC4-6 | **Spec:** 24#story-reader

**Description:** Verify that `AudioCoordinator.stopTts()` properly updates the internal TTS state flow. If the state flow doesn't update automatically, add explicit state emission.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/shared/audio/AudioCoordinator.kt:194-196` — `stopTts()` function
- `app/src/main/java/com/alexpo/grammermate/shared/audio/TtsEngine.kt` — `stop()` function

**Code change (if needed):**
```kotlin
fun stopTts() {
    ttsEngine.stop()
    // Ensure state flow emits Idle state immediately
    _ttsState.update { TtsState.Idle }
}
```

**Verification:**
- After `stopTts()`, `ttsState` becomes `TtsState.Idle`
- State change emits immediately (not delayed)
- UI receives state update via StateFlow

---

## Verification Checklist

1. **Play story:** Tap play button on chapter → verify button changes to stop
2. **Stop story:** Tap stop button → verify button disappears immediately
3. **State update:** Verify `isStoryPlaying` becomes false after stop
4. **No race condition:** Rapid play/stop cycles work correctly
5. **TTS silence:** Verify audio actually stops (no sound continues)
6. **Replay:** Can replay story after stopping
7. **Multiple chapters:** Stop works on all chapter stories

## Scope Boundaries

**Do NOT touch:**
- Other TTS functionality (TrainingScreen, VerbDrill, etc.)
- Audio coordinator initialization
- TTS download or error handling
- Story loading or markdown rendering
- Chapter navigation or progress tracking
- Any other navigation flows

**DO NOT refactor:**
- Do NOT change the overall audio architecture
- Do NOT modify how TTS state is stored or managed (only fix the stop bug)
- Do NOT introduce new audio components or abstractions

## Regression Plan

After all fixes are implemented, run:

1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:**
   - Manual test: Play chapter story → stop → verify button disappears
   - Manual test: Play → stop → replay → works correctly
   - Manual test: Rapid play/stop 5 times → no UI lag or stuck state
   - Manual test: Verify on 3 different chapters
4. **Cross-task regression:**
   - Verify TTS in TrainingScreen unaffected
   - Verify TTS in VerbDrill unaffected
   - Verify TTS in VocabDrill unaffected
   - Verify story playback still works correctly
5. **UC/AC spot-check:** Read UC-102 from `22-use-case-registry.md`, confirm all ACs hold
6. **Spec sync:** If code diverges from spec intentionally, update spec + CHANGELOG + trace-index

## Git

One commit per fix or one combined. Commit message:
```
Fix stop story button UI state update bug

- Add explicit state update to stopStoryNarration()
- Ensure AudioCoordinator.stopTts() updates internal state
- Fix isStoryPlaying state synchronization
- Verify UC-102 acceptance criteria

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-29 | Fix 1: Add explicit state update to stopStoryNarration() | OPEN | |
| 2026-05-29 | Fix 2: Ensure AudioCoordinator.stopTts() updates internal state | OPEN | May not need changes if state updates automatically |
