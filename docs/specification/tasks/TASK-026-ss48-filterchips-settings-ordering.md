# TASK-026: SS-48 FilterChips + Settings Section Ordering

**Status:** OPEN
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** —
**Spec:** 23-screen-elements.md#SS-48, 19-screen-catalog.md

---

## Problem

Two issues:

1. **SS-48 component type**: Spec says "DropdownSelector" for Interface Language. Code uses 3 FilterChips (System / English / Русский), matching the same pattern as SS-47 Theme Mode selector. The FilterChip approach is consistent and arguably better UX.

2. **Section ordering**: Code places Interface Language at the TOP of Settings (before Service Mode). Spec implies it should be at the bottom (SS-48 is the last element). Also, Appearance (SS-46/SS-47) is after Service Mode in code, not near the end as implied by spec numbering.

## Solution

### Fix 1: Update spec SS-48 to FilterChips
**File:** `docs/specification/23-screen-elements.md`
- Change SS-48 from "DropdownSelector" to "3 FilterChips (System / English / Русский)"

### Fix 2: Update spec section ordering
**File:** `docs/specification/23-screen-elements.md` — update SS element ordering to match code:
- Interface Language (top)
- Service Mode
- Appearance
- Difficulty
- Vocab Sprint limit
- TTS Speed
- Voice Recognition
- Voice Auto-Start
- Text Scale
- Language/Pack
- Content Management
- Packs
- Profile
- Backup & Restore
- Footer

## Verification Checklist
1. SS-48 describes FilterChips, not DropdownSelector
2. Element ordering in spec matches code layout
3. Both Appearance and Interface Language in correct positions

## Scope Boundaries
**Do NOT touch:**
- Code changes

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (Settings screen layout)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Update spec SS-48 to FilterChips | | |
| | Fix 2: Update spec section ordering | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
