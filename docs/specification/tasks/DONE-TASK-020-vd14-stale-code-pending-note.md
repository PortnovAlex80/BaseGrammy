# TASK-020: VD-14 Stale CODE PENDING Note

**Status:** DONE
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** scenario-07-verb-drill.md
**Spec:** 23-screen-elements.md#VD-14

---

## Problem

Spec VD-14 says: "NOTE: Currently uses plain IconButton with static VolumeUp instead of TtsSpeakerButton. Marked as CODE PENDING."

The code NOW correctly uses TtsSpeakerButton with 4 states (SPEAKING, INITIALIZING, ERROR, IDLE). The "CODE PENDING" note is stale and misleading.

## Solution

### Fix 1: Remove stale note from VD-14
**File:** `docs/specification/23-screen-elements.md`
- Remove "NOTE: Currently uses plain IconButton..." and "CODE PENDING" text
- Update description to say "TtsSpeakerButton with 4 states: SPEAKING=StopCircle red, INITIALIZING=spinner, ERROR=Warning/ReportProblem, IDLE=VolumeUp"

## Verification Checklist
1. VD-14 no longer mentions CODE PENDING
2. VD-14 describes TtsSpeakerButton with 4 states
3. Matches actual VerbDrillScreen.kt code

## Scope Boundaries
**Do NOT touch:**
- Code changes

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (VerbDrill screen, TTS button states)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Remove stale CODE PENDING note from VD-14 | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
