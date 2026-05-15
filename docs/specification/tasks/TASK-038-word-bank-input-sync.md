# TASK-038: Word Bank Selected Words Not Reflected in Input Text Field

**Status:** BUG
**Created:** 2026-05-15
**Spec:** 12-training-card-session.md#12.6.1, 12.7.1

---

## Problem

When using WORD_BANK input mode, tapping word bank chips updates `_selectedWords` in the adapter (VerbDrillCardSessionProvider, DailyPracticeSessionProvider) but the input text field (`OutlinedTextField`) remains empty. The `WordBankSection` counter updates correctly ("Selected: 3 / 8"), but the actual answer form never fills.

Root cause: `TrainingCardSession.kt` manages `localInputText` as its own `mutableStateOf("")` state. The scope's `inputText` property reads from `localInputText`, which is never updated when the adapter's `selectWordFromBank()` changes `_selectedWords`. In regular training, `SessionRunner.selectWordFromBank()` updates both `selectedWords` AND `inputText` in `TrainingUiState` (via `updateState`), so the form fills correctly. But adapters using `CardSessionContract` via `TrainingCardSession` have a gap.

## Fix

In `TrainingCardSession.kt`, derive `effectiveInputText` from `contract.getSelectedWords()` when in WORD_BANK mode:

```kotlin
val effectiveInputText = if (contract.currentInputMode == InputMode.WORD_BANK) {
    contract.getSelectedWords().joinToString(" ")
} else {
    localInputText
}
```

This reads `getSelectedWords()` during composition (tracked by Compose since the adapter uses `mutableStateOf`), and provides the assembled text to the scope.

**Files:**
- `ui/TrainingCardSession.kt` -- add `effectiveInputText` derivation, use in scope creation and submit

## Verification

1. Verb Drill: switch to Word Bank mode → tap words → input field shows assembled text
2. Verb Drill: tap Undo → input field updates (last word removed)
3. Verb Drill: submit word bank answer → processes correctly
4. Daily Practice: same verification for sentence translation block
5. Regular Training: word bank still works (via SessionRunner, unaffected)
6. Keyboard/Voice modes: input field works normally (localInputText unchanged)
7. Mode switch: WORD_BANK → KEYBOARD → WORD_BANK → selections preserved
8. Build: `assembleDebug` passes

## Spec Updates

- `12-training-card-session.md` 12.6.1: added `effectiveInputText` to state table
- `12-training-card-session.md` 12.6.2: added Compose observability invariant
- `12-training-card-session.md` 12.7.1: updated word bank FilterChip and Undo descriptions

## Regression Invariant

**For all shared components in TrainingCardSession:** any contract state that drives UI must be Compose-observable. If a property is read during composition but backed by StateFlow or plain fields, it MUST be bridged via `mutableStateOf` or derived during composition from a `mutableStateOf` source.

## Git

One commit. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
