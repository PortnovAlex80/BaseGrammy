# TASK-036: TS-27 + TCS-21 Duplicate TTS Buttons in Result Area

**Status:** DONE
**Created:** 2026-05-15
**Branch:** feature/tts-button-dedup (from main)
**Scenario:** scenario-01-training-flow.md
**Spec:** 23-screen-elements.md#TS-27, 23-screen-elements.md#TCS-21
**UC:** UC-51

## Problem

After answering a card, TWO TTS buttons are visible:
1. TtsSpeakerButton (4-state) in the result row (TCS-28) — plays answer audio
2. Plain VolumeUp IconButton inside HintAnswerCard (TS-27) — also plays answer audio

Spec describes TS-27 as "TtsSpeakerButton. Replays answer TTS" but code uses a plain VolumeUp IconButton inside HintAnswerCard, not the 4-state TtsSpeakerButton.

The result is two nearly-identical TTS buttons visible simultaneously, which is confusing.

## Solution

### Option A: Remove TTS from HintAnswerCard (RECOMMENDED)
The TtsSpeakerButton in the result row (TCS-28) already handles TTS. Remove the redundant VolumeUp button from HintAnswerCard.

### Option B: Update spec to document both buttons
Document the dual-button pattern if intentional.

**Files (Option A):**
- `app/src/main/java/com/alexpo/grammermate/ui/components/HintAnswerCard.kt` — remove TTS button parameter and rendering
- `docs/specification/23-screen-elements.md` — update TS-27 to remove TTS mention, note it uses result row TTS

**Files (Option B):**
- `docs/specification/23-screen-elements.md` — update TS-27 to describe plain VolumeUp in HintAnswerCard

## Verification Checklist

1. Only ONE TTS button visible in result area after answering
2. TTS replay works correctly for correct and incorrect answers
3. HintAnswerCard still shows answer text
4. Spec accurately describes the TTS button placement

## Scope

- IN: HintAnswerCard TTS button, TS-27 spec entry
- OUT: TCS-28 (result row TTS), TrainingScreen TTS, DailyPractice TTS

## Dependencies

- Parent: TASK-030 (discrepancy registry)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (TTS on all training screens, HintAnswerCard)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Remove duplicate TTS button from HintAnswerCard or update spec | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
