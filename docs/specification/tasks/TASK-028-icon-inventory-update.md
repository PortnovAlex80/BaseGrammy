# TASK-028: Icon Inventory Update (8 Extra Icons + LocalFlorist)

**Status:** OPEN
**Created:** 2026-05-15
**Branch:** N/A (spec-only fix)
**Scenario:** —
**Spec:** 14-theme-and-ui-components.md#14.5

---

## Problem

The spec icon inventory lists 25 Material Icons. Audit found:

**8 icons in code NOT in spec inventory:**
1. ChevronRight — verb chip navigation
2. Warning — TTS OOM error, various error states
3. Info — tense info section
4. SkipNext — vocab drill skip button
5. Flip — vocab drill flip card button
6. QrCode2 — QR share dialogs
7. Check — correct voice result indicator
8. Close — incorrect voice result indicator

**1 icon in spec NOT in code:**
- LocalFlorist — spec says "imported but unused". Code confirms it's completely absent (not even imported).

Total: 33 icons in use, spec covers only 24 of them (25 listed minus LocalFlorist which doesn't exist).

## Solution

### Fix 1: Add 8 missing icons to spec inventory
**File:** `docs/specification/14-theme-and-ui-components.md`
- Add: ChevronRight, Warning, Info, SkipNext, Flip, QrCode2, Check, Close
- Include usage context for each

### Fix 2: Remove LocalFlorist from inventory
**File:** `docs/specification/14-theme-and-ui-components.md`
- Remove LocalFlorist entry (or mark as "Not used — candidate for removal if ever imported")

## Verification Checklist
1. Spec icon inventory lists all 33 actually-used icons
2. LocalFlorist removed or marked as absent
3. Each icon has usage context documented

## Scope Boundaries
**Do NOT touch:**
- Code changes
- Icon resource files

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (all screens using icons)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Add 8 missing icons to spec inventory | | |
| | Fix 2: Remove LocalFlorist from inventory | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
