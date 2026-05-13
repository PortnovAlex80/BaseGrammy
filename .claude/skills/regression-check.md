---
name: regression-check
description: Run regression verification after code changes. Use when user asks to verify changes, run regression, or check for breakage. Reads git diff, finds affected use cases and screen elements, verifies each acceptance criterion against code.
---

# Regression Check — Post-Change Verification

This skill verifies that code changes do not break existing functionality by checking affected use case acceptance criteria and screen element invariants.

## Activation

User invokes `/regression-check` or asks "check for regressions", "verify changes", "what did I break".

## Execution Pipeline

### Step 1: Collect Changes

Spawn ONE Explore agent that runs:
1. `git diff --name-only HEAD` (or `git diff --name-only main...HEAD` for PR checks)
2. For each changed file, determine:
   - Which screen(s) it belongs to (check file path: ui/screens/, ui/helpers/, data/)
   - Which Element IDs are affected (cross-reference with 23-screen-elements.md)
   - Which UC-IDs are affected (cross-reference via Screen and Source columns in 22-use-case-registry.md)

Return a structured list:
```
CHANGED FILES: [list]
AFFECTED SCREENS: [list with screen IDs]
AFFECTED ELEMENTS: [Element IDs from 23-screen-elements.md]
AFFECTED UCs: [UC-IDs from 22-use-case-registry.md]
TOTAL ACs TO CHECK: [N]
```

### Step 2: Verify Acceptance Criteria

Spawn verification agents (max 5 per wave) to check each affected AC:

For each UC -> each AC:
1. Read the AC statement from 22-use-case-registry.md
2. Find the relevant code in the changed files
3. Verify the AC is still satisfied
4. Report: PASS or FAIL with explanation

Format per AC:
```
[UC-ID] AC[N] "description" -> PASS (file.kt:line references the right field)
[UC-ID] AC[N] "description" -> FAIL (expected X but found Y at file.kt:line)
```

### Step 3: Verify Screen Element Invariants

For each affected Element ID:
1. Read the element's "Visible when" condition from 23-screen-elements.md
2. Check the code still implements this condition correctly
3. Read the element's "Behavior / Invariant"
4. Verify the code still satisfies this invariant
5. Report: PASS or FAIL

Format per element:
```
[ELEM-ID] "element name" visible-when -> PASS (condition check present at file.kt:line)
[ELEM-ID] "element name" behavior -> FAIL (invariant broken: details)
```

### Step 4: Summary Report

Present to user:
```
REGRESSION CHECK RESULTS
========================
Files changed: N
Screens affected: N
Elements checked: N (PASS: X, FAIL: Y)
UCs checked: N (ACs PASS: X, FAIL: Y)

FAILURES:
- [UC-ID] AC[N]: description -> what's wrong
- [ELEM-ID]: description -> what's wrong

If no failures: "All affected acceptance criteria and element invariants pass."
```

## Rules

- This is a READ-ONLY skill. Do NOT modify any files.
- Use Explore agents for verification (read-only, no edits)
- Max 5 agents per wave for parallel verification
- Each agent verifies 5-10 ACs/elements to stay focused
- Reference specific file paths and line numbers in all results
- If a file was deleted or renamed, flag as FAIL (element/UC may be orphaned)
- Cross-reference CLAUDE.md tech debt section — flag if a failure matches known tech debt

## File Mapping Reference

Quick reference for which files map to which screens:

| Path pattern | Screen / Domain |
|-------------|----------------|
| ui/screens/TrainingScreen.kt | TrainingScreen (TS-*) |
| ui/screens/HomeScreen.kt | HomeScreen (HS-*) |
| ui/screens/SettingsScreen.kt | SettingsSheet (SS-*) |
| ui/screens/LessonRoadmapScreen.kt | LessonRoadmapScreen (LR-*) |
| ui/screens/LadderScreen.kt | LadderScreen (LS-*) |
| ui/screens/StoryQuizScreen.kt | StoryQuizScreen (SQ-*) |
| ui/TrainingCardSession.kt | TrainingCardSession (TCS-*) |
| ui/DailyPracticeScreen.kt | DailyPracticeScreen (DP-*) |
| ui/VerbDrillScreen.kt | VerbDrillScreen (VD-*) |
| ui/VocabDrillScreen.kt | VocabDrillScreen (VOC-*) |
| ui/GrammarMateApp.kt | Dialogs (DG-*) |
| ui/TrainingViewModel.kt | All screens (state source) |
| ui/helpers/*.kt | All screens (logic source) |
| data/Models.kt | All screens (type definitions) |
| data/*.kt | All screens (data layer) |
