# TASK-059: Unify Voice Auto-Launch Mechanism

**Status:** OPEN
**Created:** 2026-05-17
**Branch:** feature/unify-voice-auto-launch (from main)
**Spec:** 12-training-card-session.md, 10-verb-drill.md
**UC:** UC-59
**Scenario:** scenario-05-input-modes.md

---

## Problem

Voice auto-launch has 3 different implementations across training modes:
- **TrainingScreen**: Inline `LaunchedEffect` keyed on `voiceTriggerToken` (TrainingScreen.kt:120-144)
- **VerbDrill**: `VoiceAutoLauncher` shared component (VerbDrillScreen.kt:181-191)
- **DailyPractice**: Inline `LaunchedEffect` keyed on `currentCard.id + voiceTriggerToken` (DailyPracticeScreen.kt:351-361)

Per DP-03 (One Shared Component Per UI Element), all modes should use `VoiceAutoLauncher`.

## Changes

### Fix 1: TrainingScreen uses VoiceAutoLauncher
**Discrepancy:** N/A | **UC:** UC-59 | **Spec:** 12-training-card-session.md DP-03

Replace inline `LaunchedEffect` in TrainingScreen.kt (lines 120-144) with `VoiceAutoLauncher` component call. Pass equivalent parameters. Keep offline ASR support.

**Files:** `ui/screens/TrainingScreen.kt` — replace LaunchedEffect with VoiceAutoLauncher

**Verification:** TrainingScreen voice auto-launch uses VoiceAutoLauncher, behavior unchanged.

### Fix 2: DailyPractice uses VoiceAutoLauncher
**Discrepancy:** N/A | **UC:** UC-59 | **Spec:** 12-training-card-session.md DP-03

Replace inline `LaunchedEffect` in DailyPracticeScreen.kt with `VoiceAutoLauncher` component call.

**Files:** `ui/screens/DailyPracticeScreen.kt` — replace LaunchedEffect with VoiceAutoLauncher

**Verification:** DailyPractice voice auto-launch uses VoiceAutoLauncher, behavior unchanged.

### Fix 3: Update spec
**Discrepancy:** N/A | **UC:** UC-59 | **Spec:** 12-training-card-session.md

Add requirement: "All card-based modes MUST use VoiceAutoLauncher for voice auto-launch. No inline LaunchedEffect implementations."

**Files:** `docs/specification/12-training-card-session.md`

**Verification:** Spec mandates shared component.

---

## Verification Checklist
1. TrainingScreen voice auto-launch works identically after migration
2. VerbDrill voice auto-launch unchanged
3. DailyPractice voice auto-launch works identically after migration
4. Offline ASR path in TrainingScreen still functional
5. Wrong-answer voice retry works in all modes
6. `assembleDebug` passes

## Scope Boundaries
**Do NOT touch:**
- VoiceAutoLauncher component itself (already shared)
- VocabDrill voice launch (different paradigm — flip card)
- Auto-advance timing (separate concern)

## Regression Plan
1. Build: `assembleDebug`
2. Test voice auto-launch in TrainingScreen, VerbDrill, DailyPractice
3. UC-59 spot-check

## Git
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
