---
name: add-feature
description: Use when adding a new feature or porting an existing UI element to a new screen. Forces spec -> behavioral contract -> AC with behavioral criteria -> implementation -> regression with non-default configs. Use when user asks to "add X", "port Y to screen Z", "make X work like Y on screen Z", "implement feature", or "wire up X".
---

# Add Feature — Full Pipeline

This skill enforces a complete pipeline from idea to regression. It exists because a retro found that specs described STRUCTURE (button exists) but not BEHAVIOR (what happens when clicked), causing regression checks to miss critical bugs. Every phase below is MANDATORY — do not skip or shortcut.

## Core Principle

**Behavioral specs prevent bugs that structural specs miss.**

A spec that says "Show Answer button exists on TrainingScreen" is structural. It tells you the button is there but not what it does. A spec that says "User clicks Show Answer -> provider.showAnswer() is called -> hintAnswer is set -> card background changes to pink -> answer text appears" is behavioral. It tells you the button is WIRED and DOES something.

When porting a UI element from one screen to another, the structural spec passes ("button exists on both screens") while the behavioral spec catches the real bug ("button exists on target but onClick is not wired to any function").

## Activation

User describes a feature to add, port, or wire up. This skill enforces the complete pipeline below. Announce to the user:

```
ADD-FEATURE PIPELINE activated for: [feature description]
Phases: Research -> Spec Update -> User Review -> Implementation -> Regression
This pipeline is mandatory — specs before code, behavior before structure.
```

## Phase 1: Research (read-only)

Spawn ONE Explore agent that:

1. Reads the relevant spec files (check CLAUDE.md "Document map by component" table for which spec applies)
2. Reads the REFERENCE implementation if porting (the screen that already has this feature working)
3. Reads the TARGET implementation (the screen that needs the feature)
4. Searches `22-use-case-registry.md` for any existing UCs related to this feature
5. Searches `23-screen-elements.md` for any existing elements related to this feature

The agent returns a structured report:

```
RESEARCH REPORT
===============
Feature: [name]
Type: NEW | PORT | ENHANCE

SPEC FILES RELEVANT:
- [spec file path]: [what sections apply]

REFERENCE IMPLEMENTATION (if porting):
- Screen: [screen name]
- File: [path]
- UI element: [composable function name]
- Callback chain: [exact sequence: onClick -> ViewModel method -> state update -> UI reaction]
- Dependencies: [speechLauncher, stateFlow, ttsEngine, etc. needed by the callback]
- Configuration states that affect this feature:
  - HintLevel: [EASY/MEDIUM/HARD behavior differences]
  - InputMode: [VOICE/KEYBOARD/WORD_BANK behavior differences]
  - SessionState: [ACTIVE/PAUSED/HINT_SHOWN behavior differences]
  - Other: [drill mode, boss mode, daily practice, etc.]

TARGET IMPLEMENTATION:
- Screen: [screen name]
- File: [path]
- Current state: [what exists now, what is missing]
- Dependencies available: [which of the reference dependencies exist in target]
- Dependencies missing: [what needs to be added or adapted]

EXISTING UCs/ELEMENTS:
- Related UCs: [UC-IDs that touch this feature area]
- Related Elements: [Element IDs that are adjacent or related]

GAPS:
- [what is undefined, ambiguous, or unknown]
```

**This phase is read-only. No file modifications.**

## Phase 2: Spec Update (write specs BEFORE code)

Spawn ONE agent that updates ALL relevant spec files. The agent MUST produce a diff-level summary of every change before writing.

### 2a. For each spec file affected

Add or update the feature description with TWO sections:

**Structure** — what the UI element looks like:
- Icon, color, position, size, shape
- Visibility conditions (when it appears/hides)
- Text content and formatting

**Behavioral Contract** — what happens when the user interacts:

| User Action | System Response | User Outcome |
|-------------|----------------|--------------|
| Clicks Show Answer button | `provider.showAnswer()` called -> `hintAnswer` set to accepted answers | Card background changes to pink, answer text displayed |
| Clicks Show Answer when already shown | No-op (guard: `hintAnswer != null`) | No visible change |
| Clicks Show Answer in WORD_BANK mode | Same as default | Same as default (mode does not affect hint behavior) |

Rules for the behavioral contract table:
- EVERY row must have a concrete User Outcome (what the user SEES or EXPERIENCES)
- Cover ALL configuration states: HintLevel (EASY/MEDIUM/HARD), InputMode (VOICE/KEYBOARD/WORD_BANK), SessionState (ACTIVE/PAUSED/HINT_SHOWN), and any mode flags (boss, daily, drill)
- Define what happens at NON-DEFAULT settings explicitly — do not assume "same as default" unless the contract table says so
- For ported elements: include a "Callback Wiring" row showing the exact function chain from onClick to state change to UI update
- If a configuration state has NO effect on the feature, state that explicitly: "InputMode: no behavioral difference"

### 2b. Update 22-use-case-registry.md

Add a UC with BOTH structural AND behavioral ACs:
- Structural ACs verify the element exists and looks correct (icon, text, position)
- Behavioral ACs verify the element DOES something when interacted with
- Every ported element gets at least 1 `[BEHAVIORAL]` tagged AC
- Every feature gets at least 1 "Non-Default Configuration" AC
- ACs use OBSERVABLE OUTCOMES, not "matches [ScreenName]"

AC format examples:

```
GOOD (behavioral, observable):
AC3 [BEHAVIORAL]: Clicking Show Answer sets hintAnswer and changes card background to pink
AC4 [BEHAVIORAL]: In HARD HintLevel, Show Answer also strips parenthetical hints from prompt
AC5: Non-default config: In WORD_BANK mode, Show Answer does NOT affect word chip state

BAD (structural only, not verifiable):
AC3: Show Answer button matches TrainingScreen implementation
AC4: Button is wired correctly

UGLY (unobservable, not testable):
AC3: The hint system works as expected
AC4: Button behavior is correct
```

### 2c. Update 23-screen-elements.md

Add an element entry with BOTH "Visual" and "Behavior" subsections:
- Visual: icon, color, size, position, visibility conditions (goes in existing columns)
- Behavior: what the element DOES when interacted with, including all config-state variants

Format:
```
| Element name | [PREFIX]-[NN] | [type] | [visible when] | [Behavior / Invariant] | [Related UC] |
```

Fill the "Related UC" column with the UC-ID from 2b (not "?").

### 2d. Spec change summary

The agent must output a structured summary of ALL changes made:

```
SPEC CHANGES
============
[spec-file-1.md]:
  - Section X.Y: Added behavioral contract table for [feature]
  - Section X.Z: Updated structure description for [feature]

[22-use-case-registry.md]:
  - Added UC-[NN]: [title] with [N] ACs ([M] behavioral, [K] non-default-config)

[23-screen-elements.md]:
  - Added [PREFIX]-[NN]: [element name] with behavior description + UC-[NN] link
```

## Phase 3: User Review

Announce spec changes to user and WAIT for approval before implementation.

Present:
1. The spec change summary from Phase 2d
2. The behavioral contract table(s)
3. The new UC with all ACs (highlighted: behavioral vs structural)

```
SPEC REVIEW REQUEST
===================
[Show the full behavioral contract table(s)]
[Show the new UC and ACs]

Awaiting your approval before proceeding to implementation.
You can request changes to the behavioral contracts or ACs.
```

**DO NOT proceed to Phase 4 until the user explicitly approves.**

If the user requests changes, loop back to Phase 2 for spec updates, then re-present.

## Phase 4: Implementation

Follow CLAUDE.md decomposition rules strictly:
- Touches >= 3 files -> decompose into agents
- Touches TrainingViewModel -> decompose (high blast radius)
- Use LAYERS + TEAM for cross-layer work (data -> helpers -> UI)
- Use WAVES + SUBAGENTS for independent work
- Main context is COORDINATION ONLY — no code editing in main

### Critical Rules for Ported UI Elements

When porting a UI element from reference to target, the callback wiring MUST match.

**Before writing ANY code:**

1. Extract the exact callback chain from the reference screen:
   ```
   Reference chain: onClick={onShowAnswer()} -> TrainingViewModel.showAnswer()
     -> updateState { copy(hintAnswer = card.acceptedAnswers.joinToString(" / ")) }
     -> UI reads hintAnswer, changes CardPrompt background to pink
   ```

2. Verify the target screen has access to the same dependencies:
   - Does the target composable receive the callback prop? (e.g., `onShowAnswer: () -> Unit`)
   - Does the target's parent pass the ViewModel method? (e.g., `onShowAnswer = viewModel::showAnswer`)
   - Are all state fields referenced by the callback available? (e.g., `hintAnswer` in UiState)
   - Are external dependencies available? (e.g., `speechLauncher`, `ttsEngine`, `asrLauncher`)

3. If dependencies differ, adapt the chain but preserve the BEHAVIOR:
   - If the target uses a different state shape, map fields correctly
   - If the target lacks an external dependency (e.g., no ASR), substitute with equivalent or no-op with explicit documentation
   - Document every adaptation in a comment: `// Ported from TrainingScreen: adapted X because Y`

4. After implementation, diff the reference vs target callback wiring:
   - Reference: [exact chain]
   - Target: [exact chain]
   - Differences: [none / documented adaptations]

### Implementation Checklist

Before marking implementation complete:
- [ ] Every behavioral contract row from the spec is implemented in code
- [ ] Every UC AC has a code path that satisfies it
- [ ] Non-default configuration states are handled (not just the happy path)
- [ ] Callback wiring matches reference (if porting) with documented adaptations
- [ ] No dead code (callback registered but never triggers, state set but never read)
- [ ] Build succeeds

## Phase 5: Regression

After implementation completes, verify against ALL behavioral ACs. This phase combines two checks:

### 5a. New Feature Regression

Run through each UC's ACs from Phase 2b — both structural AND behavioral:

1. For each structural AC: verify the element exists with correct properties
2. For each behavioral AC: trace the code path from user action to system response to user outcome
3. For each non-default config AC: verify the code handles that specific config state

Report format per AC:
```
[UC-NN] AC[N] "[description]" -> PASS | FAIL
  Evidence: [file.kt:line references or observable outcome]
```

### 5b. Existing Feature Regression

Run the `/regression-check` skill (or follow its pipeline manually):
1. Collect changed files from git diff
2. Find affected UCs and screen elements
3. Verify each affected AC still passes
4. Verify each affected element invariant still holds

### 5c. Non-Default Configuration Testing

For every behavioral AC, explicitly verify at these configuration states:
- HintLevel: EASY (default), MEDIUM, HARD
- InputMode: VOICE (default), KEYBOARD, WORD_BANK
- SessionState: ACTIVE (default), PAUSED, HINT_SHOWN
- Special modes: boss active, daily practice, drill mode (if applicable)

Only test configurations relevant to the feature. If InputMode has no behavioral effect (stated in the contract), skip it.

### 5d. Regression Report

```
REGRESSION RESULTS
==================
New Feature ACs: N checked (PASS: X, FAIL: Y)
Existing Feature ACs: N checked (PASS: X, FAIL: Y)
Non-Default Configs tested: N (PASS: X, FAIL: Y)

FAILURES:
- [UC-NN] AC[N]: [what failed, what was expected, what was found]
- [PREFIX-NN]: [invariant broken]

If no failures: "All behavioral and structural ACs pass at all tested configurations."
```

## Checklist Template

Copy this checklist for each feature. Fill it out as you complete each phase.

### Feature: [name]

**Phase 1 — Research:**
- [ ] Relevant spec files identified and read
- [ ] Reference implementation analyzed (if porting)
- [ ] Target implementation analyzed
- [ ] Configuration states that affect feature documented
- [ ] Existing UCs/elements cross-referenced
- [ ] Gaps identified

**Phase 2 — Spec Update:**
- [ ] Behavioral contract table written (User Action -> System Response -> User Outcome)
- [ ] All config states covered in behavioral contract (or explicitly stated as no effect)
- [ ] UC added to 22-use-case-registry.md with behavioral ACs
- [ ] At least 1 AC tagged [BEHAVIORAL]
- [ ] At least 1 AC covers non-default configuration
- [ ] Element added to 23-screen-elements.md with behavior description
- [ ] Element linked to UC (Related UC column filled, not "?")
- [ ] Spec change summary produced

**Phase 3 — User Review:**
- [ ] Spec changes presented to user
- [ ] User approved specs (explicit confirmation received)

**Phase 4 — Implementation:**
- [ ] Decomposition plan announced (SUBAGENTS or TEAM, with justification)
- [ ] Reference callback chain documented (if porting)
- [ ] Target callback chain matches reference or adaptations documented
- [ ] Every behavioral contract row implemented
- [ ] Every UC AC has a satisfying code path
- [ ] Non-default configurations handled
- [ ] Build succeeds

**Phase 5 — Regression:**
- [ ] All new feature ACs PASS at default config
- [ ] All new feature ACs PASS at non-default configs
- [ ] All existing feature ACs still PASS (regression check)
- [ ] No dead code (callback set but never fires, state set but never read)
- [ ] Regression report produced

## Anti-patterns (DO NOT)

These are the patterns that caused bugs in previous iterations. Every one of them was a real failure mode.

1. **Don't write ACs that say "matches [ScreenName]" without defining the observable outcome.**
   "Matches TrainingScreen" tells you nothing about what the button DOES. It is a structural comparison that passes even when the button is unwired. Every AC must describe a user-visible outcome.

2. **Don't check only that a component file exists — check that it's WIRED correctly.**
   A ShowAnswerButton.kt file existing proves nothing. The button must be called from the screen composable, receive the onClick callback, and that callback must flow through to the ViewModel method that changes state. Trace the FULL chain.

3. **Don't test only at default settings.**
   Default settings (EASY hint, VOICE mode, ACTIVE session) are the happy path. Bugs hide in non-default states: HARD mode stripping parentheticals, WORD_BANK mode not counting mastery, PAUSED state disabling interaction. Test at least one non-default configuration per feature.

4. **Don't port a UI element without porting its callback logic.**
   Copying the composable without copying the onClick handler, or wiring it to a different method that has subtly different behavior, is the most common porting bug. Extract the exact callback chain from reference and verify target matches.

5. **Don't skip Phase 3 (user review of specs before code).**
   Writing specs and immediately proceeding to code defeats the purpose. The user review catches misunderstandings before they become code that needs rewriting. Even if the user says "looks good, proceed," the explicit checkpoint prevents the spec-code gap that caused the original regression failures.

6. **Don't confuse "element renders" with "element works."**
   "The button is visible" is structural. "Clicking the button triggers the expected state change" is behavioral. Both must be verified. A button can render perfectly and do nothing when clicked.

7. **Don't let a behavioral contract row have "See [other screen]" in the outcome column.**
   Every row must describe what happens ON THIS SCREEN. Cross-references are for context, not for defining behavior. If you cannot describe the outcome without referencing another screen, you do not understand the feature well enough to implement it.

8. **Don't assume "it should work the same" without verifying configuration interactions.**
   A feature that works perfectly in a regular training session may break in boss battle, daily practice, or drill mode because those modes override default behavior (skip mastery recording, change input mode rotation, disable timer). Check the behavioral contract at every mode the feature could be accessed from.

## Relationship to Other Skills

- **`/regression-check`**: Used in Phase 5b to verify existing features are not broken by the new feature
- **`/verify-user-journey`**: Use AFTER Phase 4 to trace the complete data flow and button wiring end-to-end
- **`/swarm`**: Use in Phase 4 for implementation decomposition when the feature touches multiple files or layers
