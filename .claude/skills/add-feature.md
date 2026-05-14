---
name: add-feature
description: Use when adding a new feature or porting an existing UI element to a new screen. Forces Research -> Behavioral Spec -> User Review -> Implementation -> Verify vs Spec. Spec is the source of truth — code is adjusted to match spec, never the other way around.
---

# Add Feature — Full Pipeline

## Core Axiom

**Spec is the source of truth. Code is adjusted to match spec, not the other way around.**

If after writing code the behavior differs from the spec — that is a bug in code, not a reason to update the spec.
The spec is updated only if the user explicitly changes requirements. Never to "match the implementation."

## Main Loop

```
RESEARCH → BEHAVIORAL SPEC → [ USER REVIEW ] → CODE → VERIFY vs SPEC
                                                        ↑ if fail — fix code, not spec
```

Announce on activation:
```
ADD-FEATURE PIPELINE: [feature description]
Phases: Research → Spec → Review → Code → Verify
Spec is written before code. Code is verified against spec.
```

---

## Phase 1 — Research [agent]

Spawn ONE Explore agent. Read-only — no file modifications.

Agent reads:
- Relevant spec files (per "Document map" table in CLAUDE.md)
- Reference implementation (screen where feature already works — if porting)
- Target implementation (where feature needs to be added)
- 22-use-case-registry.md — existing UCs near this area
- 23-screen-elements.md — existing adjacent elements

Agent returns:
```
RESEARCH REPORT
===============
Type: NEW | PORT | ENHANCE

Relevant specs: [file → which sections]

Reference (if PORT):
  Screen: [name] | File: [path]
  Callback chain: onClick → [method] → [state field] → [UI reaction]
  Config dependencies:
    HintLevel EASY/MEDIUM/HARD: [differences]
    InputMode VOICE/KEYBOARD/WORD_BANK: [differences]
    Special modes (boss, daily, drill): [differences]
  External deps: [speechLauncher, ttsEngine, ...]

Target:
  Screen: [name] | File: [path]
  What exists: [...]
  What is missing: [...]
  Deps available: [...] | missing: [...]

Adjacent UCs/Elements: [IDs]
Unknowns / risks: [...]
```

---

## Phase 2 — Behavioral Spec [agent]

Spawn ONE Spec agent. Writes spec BEFORE code. This is not optional.

> Why behavioral, not structural?
> Structural: "Show Answer button exists on screen" — passes even if button does nothing.
> Behavioral: "Click → provider.showAnswer() → hintAnswer → card background pink" — catches unwired onClick.

### 2a. Update relevant spec files

For each affected file, add/update a section with two blocks:

**Structure** — what it looks like: icon, color, position, visibility conditions

**Behavioral Contract** — what happens on interaction:

| User Action | System Response | User Outcome |
|---|---|---|
| Click Show Answer | provider.showAnswer() → hintAnswer = accepted answers | Card background turns pink, answer text appears |
| Click when already shown | No-op (guard: hintAnswer != null) | Nothing changes |
| In WORD_BANK mode | Same as default | InputMode has no effect — explicitly documented |

Every config state must be covered explicitly. "Same as default" is acceptable only if written explicitly.

### 2b. Add UC to 22-use-case-registry.md

ACs must be [BEHAVIORAL] — observable outcome, not comparison with another screen:
```
GOOD: AC3 [BEHAVIORAL]: Click Show Answer → hintAnswer → card background pink
GOOD: AC4 [BEHAVIORAL]: HintLevel.HARD strips parenthetical hints from prompt
GOOD: AC5 [NON-DEFAULT]: In WORD_BANK mode, Show Answer does not affect word chips

BAD:  AC3: Button matches TrainingScreen implementation
BAD:  AC4: Button is correctly wired
BAD:  AC4: Behavior is correct
```
Minimum: 1 [BEHAVIORAL] AC + 1 [NON-DEFAULT] AC per feature.

### 2c. Add element to 23-screen-elements.md

"Behavior" column is required. "Related UC" column — fill UC-ID, never leave "?".

### 2d. Add Regression Traceability

For each AC, write the expected code path — BEFORE code is written. This is a plan, not a fact:

```
TRACEABILITY MAP (draft — written before code, verified after)
=============================================================
AC3 [BEHAVIORAL]: "Click Show Answer → background pink"
  Expected path: ShowAnswerButton.onClick
    → TrainingViewModel.showAnswer()
    → updateState { copy(hintAnswer = ...) }
    → TrainingScreen reads hintAnswer
    → CardPrompt background = if (hintAnswer != null) Pink else Default
  Verify at: [to be filled after implementation — file:line]

AC4 [NON-DEFAULT]: "HintLevel.HARD strips parentheses"
  Expected path: showAnswer() → stripParentheticals() if hintLevel == HARD
  Verify at: [to be filled after implementation — file:line]
```

### 2e. Spec Summary

```
SPEC CHANGES
============
[spec-file.md]:
  - Added Behavioral Contract for [feature]
  - Config states covered: HintLevel (3), InputMode (3), SessionState (3)

[22-use-case-registry.md]:
  - Added UC-[NN]: [title]
  - ACs: [N] total ([M] behavioral, [K] non-default-config)

[23-screen-elements.md]:
  - Added [PREFIX-NN]: [name], linked to UC-[NN]

Traceability map: [N] AC paths documented, awaiting file:line
```

---

## Phase 3 — User Review [human]

**Do not proceed to code without explicit confirmation.**
This is not a formality. Review here costs a minute. Review after code costs an hour.

Show the user:
1. Behavioral Contract table(s)
2. UC with ACs (highlight [BEHAVIORAL] and [NON-DEFAULT])
3. Traceability map (draft)
4. Spec Summary

```
SPEC REVIEW
===========
[behavioral contract]
[UC + ACs]
[traceability map draft]

Awaiting confirmation before implementation.
If anything is wrong — update spec (not code).
```

If user requests changes → return to Phase 2. Never start code without explicit "ok" / "proceed" / "go".

---

## Phase 4 — Implementation [agent(s)]

### Decomposition (mandatory per CLAUDE.md)

Use /swarm if ANY of:
- Touches >= 3 files
- Touches TrainingViewModel (high blast radius)
- Cross-layer dependencies (data → helpers → UI)

Waves: read-only reconnaissance → data layer → logic layer → UI layer. Max 5 agents per wave.

### For porting UI elements — required

Before writing any code, extract the exact callback chain from reference:
```
Reference chain:
  onClick={onShowAnswer()}
  → TrainingViewModel.showAnswer()
  → updateState { copy(hintAnswer = card.acceptedAnswers.joinToString(" / ")) }
  → UI reads hintAnswer → CardPrompt background = Pink

Target chain: [fill after implementation]
Differences: [none / document each difference with reason]
```

### Implementation checklist

- [ ] Every Behavioral Contract row is implemented in code
- [ ] Every AC has a code path
- [ ] Non-default config states are handled
- [ ] Callback chain matches reference (or differences documented)
- [ ] No dead code (callback registered but never fires; state set but never read)
- [ ] Build passes

---

## Phase 5 — Verify vs Spec [agent]

**If code does not match spec — that is a bug in code.**
Do not update spec to go green. Do not accept "well it works" as sufficient.
Spec is the contract with the user. Code is its execution.

### 5a. Fill Traceability Map

For each AC from the draft — find actual file:line:
```
AC3 [BEHAVIORAL]: "Click Show Answer → background pink"
  Expected path: [from draft]
  Actual path:
    ShowAnswerButton.kt:42 onClick
    → TrainingViewModel.kt:891 showAnswer()
    → TrainingUiState copy at :897
    → CardPrompt.kt:156 background condition
  Status: MATCH ✓ | DIVERGES ✗ | MISSING ✗
```
If DIVERGES or MISSING → bug. Fix code, not spec.

### 5b. Verify each AC

| AC | Type | Config | Status | Evidence (file:line) |
|---|---|---|---|---|
| AC3 | BEHAVIORAL | default | PASS | CardPrompt.kt:156 |
| AC4 | BEHAVIORAL | HintLevel.HARD | PASS | TrainingVM.kt:903 |
| AC5 | NON-DEFAULT | WORD_BANK | FAIL | word chips not reset |

### 5c. Regression of existing features

Run /regression-check on changed files. Every affected UC must pass.

### 5d. Report

```
VERIFY REPORT
=============
Traceability: N paths checked (MATCH: X, DIVERGES: Y, MISSING: Z)
New ACs: N (PASS: X, FAIL: Y)
Regression ACs: N (PASS: X, FAIL: Y)
Config states tested: [list]

FAILURES (fix code, not spec):
- AC[N]: expected [...], got [...]
- [PREFIX-NN]: invariant broken

If no failures:
"All behavioral and structural ACs pass at all tested configurations."
```

---

## Anti-patterns — real bugs from past iterations

| # | Anti-pattern | Why dangerous |
|---|---|---|
| 1 | AC "matches [ScreenName]" without describing the outcome | Passes even if button does nothing |
| 2 | Check that component file exists, not that it is WIRED | ShowAnswerButton.kt can exist and do nothing |
| 3 | Test only at default settings | Bugs hide in HARD mode, WORD_BANK, boss battle |
| 4 | Port composable without porting callback logic | Button renders, onClick goes nowhere |
| 5 | Skip user review phase | Misunderstood requirements → rewrite after code |
| 6 | Update spec to "match the code" | Destroys the meaning of spec as a contract |
| 7 | "Element renders" ≠ "Element works" | Visibility ≠ functionality |
| 8 | Not testing feature in boss/daily/drill modes | These modes override default behavior |

---

## Related skills

- /regression-check — Phase 5c, verify existing features
- /verify-user-journey — after Phase 4, trace data flow end-to-end
- /swarm — Phase 4, decompose implementation into subagent waves
