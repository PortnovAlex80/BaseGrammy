# Agent-Friendly Refactoring Plan

**Created:** 2026-05-14  
**Source:** `docs/specification/arch-agent-friendly-audit-2026-05-14.md`  
**Goal:** make GrammarMate easier for parallel agent work by enforcing ownership, reducing hotspot files, shortening dependency chains, and updating agent process docs (`CLAUDE.md`, skills, task workflow).

---

## Current State

- Feature migration is mostly complete: `feature/training`, `feature/daily`, `feature/boss`, `feature/progress`, `feature/vocab`, `shared/audio`.
- `TrainingViewModel.kt` is reduced to a composition/dispatch hub, but still a frequent coordination point.
- `TrainingUiState` is grouped into domain state classes.
- Feature READMEs exist and help with local context.
- Specs and trace-index exist, but maintenance is manual.
- `CLAUDE.md` contains valuable process rules, but also stale facts, vendor-specific instructions, and references to repo-local skills that are not currently present in the repository.

## Target Outcome

Agent-friendly means:

1. Agents can find the right files from one index.
2. Agents can work in parallel without touching the same hotspot files.
3. Architecture boundaries are checked by scripts, not only described in prose.
4. Task prompts include owned files, forbidden files, specs, UCs, tests, and trace updates.
5. `CLAUDE.md` becomes a thin adapter; stable repo rules move into model-neutral `AGENTS.md`.
6. Skills/process docs become explicit, current, and executable enough for future task waves.

## Non-Goals

- No product behavior changes in Phase 1.
- No data format migrations unless a task explicitly requires it.
- No change to the single-ViewModel product pattern.
- No Gradle multi-module split in this plan; package-level enforcement is enough for the next step.

---

## Parallelization Rules For This Plan

| Area | Parallel-safe? | Notes |
|---|---|---|
| Docs/process files | Mostly no | `CLAUDE.md`, `AGENTS.md`, plan docs should have one owner agent per wave. |
| Architecture guard scripts | Yes | Can run in parallel with UI extraction if file sets do not overlap. |
| UI screen extraction | Yes, one screen per agent | Do not let two agents edit `GrammarMateApp.kt` or the same screen. |
| Result-flow cleanup | No | Sequential because it touches command types, producers, and `TrainingViewModel` dispatchers. |
| Test splitting | Yes, one test class per agent | Avoid shared fixture files unless explicitly owned. |

Hotspot files that require a single owner per wave:

- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt`
- `app/src/main/java/com/alexpo/grammermate/data/Models.kt`
- `docs/specification/trace-index.md`
- `docs/specification/CHANGELOG.md`
- `CLAUDE.md`
- future `AGENTS.md`

---

## Phase 1 - Agent Process Baseline

**Purpose:** make the repo self-describing for any agent before more code refactoring.

### Step 1.1 - Create model-neutral `AGENTS.md`

- [ ] **Status:** pending
- **Files:** `AGENTS.md`, `CLAUDE.md`
- **Work:**
  - Create `AGENTS.md` as the canonical repo guide.
  - Move stable rules from `CLAUDE.md` into `AGENTS.md`: architecture, build commands, spec workflow, file ownership, task packet format.
  - Keep `CLAUDE.md` as Claude-specific adapter: tool quirks, old workflow compatibility, link to `AGENTS.md`.
  - Update stale metrics: current `TrainingViewModel.kt` line count, current feature package layout, current test locations.
  - Fix mojibake in copied Russian text and arrows where possible.
- **Acceptance criteria:**
  - A new agent can read `AGENTS.md` and know build/test commands, architecture boundaries, high-risk files, and spec workflow.
  - `CLAUDE.md` no longer duplicates large stable sections.
  - No app code changes.

### Step 1.2 - Define skills/process registry

- [ ] **Status:** pending
- **Files:** `AGENTS.md`, `CLAUDE.md`, optional `.claude/skills/README.md`
- **Work:**
  - Verify whether repo-local `.claude/skills/` should exist. Current search found no repo-local skill files, while `CLAUDE.md` references skill names.
  - Add a "Skills and Processes" section that distinguishes:
    - repo-local skills that exist;
    - external agent commands that may exist only in a particular runtime;
    - fallback manual workflow when a skill is unavailable.
  - Define minimal process equivalents for:
    - `add-feature`
    - `swarm`
    - `regression-check`
    - `verify-user-journey`
    - `create-task`
  - Add a rule: skills must not require editing the same hotspot file from multiple agents in one wave.
- **Acceptance criteria:**
  - Missing skills are no longer a blocker; the fallback workflow is explicit.
  - Process docs are runtime-neutral.

### Step 1.3 - Add task packet template

- [ ] **Status:** pending
- **Files:** `docs/specification/tasks/README.md`, `AGENTS.md`
- **Work:**
  - Add the task packet template from the audit.
  - Required fields: Goal, Read First, Owned Files, Do Not Touch, State Owner, Store Owner, UC/AC, Verification, Trace Updates.
  - Add rule: every implementation task must declare whether it touches `TrainingViewModel`, `GrammarMateApp`, `Models`, `trace-index`, or `CHANGELOG`.
- **Acceptance criteria:**
  - Future tasks can be split into agent-safe packets before coding starts.

---

## Phase 2 - Architecture Ownership And Guards

**Purpose:** turn architecture prose into enforceable checks.

### Step 2.1 - Add ownership registry

- [ ] **Status:** pending
- **Files:** `docs/specification/architecture-ownership.md`, feature READMEs
- **Work:**
  - Document package-level ownership.
  - Document state subtree ownership:
    - `navigation` -> `TrainingViewModel`
    - `cardSession` -> `SessionRunner`
    - `boss` -> `BossOrchestrator`
    - `daily` -> `DailyPracticeCoordinator`
    - `audio` -> `AudioCoordinator`
    - `flowerDisplay` -> `FlowerRefresher`
    - `story` -> `StoryRunner`
    - `vocabSprint` -> `VocabSprintRunner`
    - `drill` / `elite` -> `SessionRunner`
  - Document allowed cross-feature imports and exceptions.
  - Update feature READMEs with links to the ownership registry.
- **Acceptance criteria:**
  - Every state subtree has one writer.
  - Every allowed cross-feature dependency is explicit.

### Step 2.2 - Move `TrainingStateAccess` to neutral package

- [ ] **Status:** pending
- **Files:** new `shared/state/TrainingStateAccess.kt`; imports in feature/shared/ui files
- **Work:**
  - Move interface out of `feature/daily/DailySessionHelper.kt`.
  - Use package such as `com.alexpo.grammermate.shared.state`.
  - Update all imports.
  - Update `feature/daily/README.md`, `feature/training/README.md`, `shared/audio/README.md`.
- **Risk:** medium; import-only but broad.
- **Verification:**
  - `assembleDebug`
  - focused grep: no imports from `feature.daily.TrainingStateAccess`
- **Acceptance criteria:**
  - `feature.daily` no longer looks like a pseudo-core package.

### Step 2.3 - Add architecture import guard

- [ ] **Status:** pending
- **Files:** `tools/architecture_guard.ps1` or `tools/architecture_guard.py`, `AGENTS.md`
- **Work:**
  - Add a script that checks forbidden imports.
  - Initial rules:
    - `data` must not import `ui`, `feature`, or `shared`.
    - `shared/audio` may import `data` and `shared/state`, not feature packages.
    - `feature/*` may import `data` and `shared/state`; cross-feature imports must match allowlist.
    - `ui` may import feature/shared/data.
  - Keep an allowlist file or allowlist section in the script.
- **Verification:**
  - Run guard locally.
  - Add command to `AGENTS.md` and future regression checklists.
- **Acceptance criteria:**
  - Boundary violations become visible before review.

### Step 2.4 - Generate architecture reports

- [ ] **Status:** pending
- **Files:** `tools/generate_arch_reports.ps1` or `.py`, `docs/specification/generated/*`
- **Work:**
  - Generate file-size report.
  - Generate import graph report.
  - Optionally generate symbol index for classes/functions.
  - Make generated files clearly marked as generated.
- **Acceptance criteria:**
  - Agents can inspect current hotspots without running ad hoc commands.

---

## Phase 3 - Hotspot File Reduction

**Purpose:** reduce merge conflicts and local context size.

### Step 3.1 - Split `VocabDrillScreen.kt`

- [ ] **Status:** pending
- **Files:** `ui/VocabDrillScreen.kt`, new `ui/components/vocab/*` or `ui/screens/vocab/*`
- **Work:**
  - Extract filter panel.
  - Extract flashcard content.
  - Extract rating controls.
  - Extract voice/TTS controls if reusable.
- **Target:** `VocabDrillScreen.kt` below 700 lines.
- **Verification:**
  - `assembleDebug`
  - relevant vocab drill test/manual checklist from specs.
- **Agent note:** one screen owner only.

### Step 3.2 - Split `GrammarMateApp.kt` dialogs

- [ ] **Status:** pending
- **Files:** `ui/GrammarMateApp.kt`, `ui/components/dialogs/*`
- **Work:**
  - Extract TTS/ASR download dialogs.
  - Extract welcome dialog host logic if it remains router-scoped.
  - Extract boss reward/streak/error dialogs.
  - Keep routing decisions in `GrammarMateApp.kt`.
- **Target:** `GrammarMateApp.kt` below 650 lines.
- **Verification:**
  - `assembleDebug`
  - navigation scenario spot-check.

### Step 3.3 - Reduce `TrainingViewModel` compatibility wrappers

- [ ] **Status:** pending
- **Files:** `TrainingViewModel.kt`, `GrammarMateApp.kt`, affected screens
- **Work:**
  - Keep wrappers only where they coordinate multiple features.
  - Prefer direct domain accessors: `vm.training`, `vm.daily`, `vm.boss`, `vm.audio`, `vm.settings`, `vm.reports`, `vm.story`.
  - Document remaining wrappers with why they exist.
- **Target:** `TrainingViewModel.kt` below 900 lines.
- **Verification:**
  - `assembleDebug`
  - no behavior change.

### Step 3.4 - Split large tests by behavior

- [ ] **Status:** pending
- **Files:** large test classes only
- **Work:**
  - Split `DailyPracticeCoordinatorTest.kt` by cursor/block/SRS behavior.
  - Split `SessionRunnerTest.kt` by submit/navigation/elite-drill behavior.
  - Split `ProgressTrackerTest.kt` by mastery/cursor/reset behavior.
  - Split `CardProviderTest.kt` by schedule/boss/mix behavior.
- **Acceptance criteria:**
  - Test files become easier to target from task prompts.
  - Test names map to specs/UCs.

---

## Phase 4 - Command Flow Cleanup

**Purpose:** remove hidden callback control flow and make command dispatch easy to inspect.

### Step 4.1 - Replace callback-in-result with query interfaces

- [ ] **Status:** pending
- **Files:** `SessionEvent.kt`, `ProgressResult.kt`, `SessionRunner.kt`, `ProgressRestorer.kt`, `TrainingViewModel.kt`
- **Work:**
  - Create explicit query interfaces, for example `SessionQueries`, `ProgressRestoreQueries`.
  - Inject query interfaces into helpers.
  - Remove callback-bearing sealed variants:
    - `CalculateCompletedSubLessons`
    - `GetMastery`
    - `GetSchedule`
    - `NormalizeEliteSpeeds`
    - `ResolveEliteUnlocked`
    - `ParseBossRewards`
  - Keep sealed events/results for side effects only.
- **Risk:** high; sequential owner only.
- **Verification:**
  - `SessionRunnerTest`
  - `ProgressTrackerTest`
  - `assembleDebug`
- **Acceptance criteria:**
  - Event/result types are simple side-effect commands.
  - No sealed result variant carries a callback.

### Step 4.2 - Document reducers/dispatchers

- [ ] **Status:** pending
- **Files:** `feature/*/README.md`, `trace-index.md`
- **Work:**
  - Add a small reducer/dispatcher map:
    - command type;
    - producer;
    - dispatcher in `TrainingViewModel`;
    - side effects.
  - Update trace-index for command type changes.
- **Acceptance criteria:**
  - A new agent can trace side effects without reading every feature file.

---

## Phase 5 - Spec-Driven Workflow Automation

**Purpose:** make spec-driven design operational for agents.

### Step 5.1 - Add trace update checker

- [ ] **Status:** pending
- **Files:** `tools/spec_trace_check.*`, `AGENTS.md`
- **Work:**
  - Detect changed pilot files:
    - `TrainingScreen.kt`
    - `VerbDrillScreen.kt`
    - `DailyPracticeScreen.kt`
    - `TrainingCardSession.kt`
    - `TrainingViewModel.kt`
  - Warn if `trace-index.md` was not updated in same change set.
  - Keep it warning-only at first.
- **Acceptance criteria:**
  - Agents get an explicit reminder before finalizing a behavior change.

### Step 5.2 - Add per-task verification matrix

- [ ] **Status:** pending
- **Files:** `docs/specification/tasks/README.md`, task files
- **Work:**
  - Standardize verification blocks:
    - build command;
    - focused tests;
    - affected UCs;
    - affected screen elements;
    - manual code checks.
  - Update `EXECUTION-5-tasks-wave.md` to use the new task packet format.
- **Acceptance criteria:**
  - A task can be handed to an agent without extra oral context.

### Step 5.3 - Add "wave manifest" for parallel work

- [ ] **Status:** pending
- **Files:** `docs/specification/tasks/WAVE-TEMPLATE.md`
- **Work:**
  - Define wave-level ownership:
    - agent name;
    - owned files;
    - forbidden files;
    - dependency order;
    - verification command;
    - merge checkpoint.
  - Add rule: only one agent per hotspot file per wave.
- **Acceptance criteria:**
  - Multi-agent waves become reviewable before coding starts.

---

## Recommended Execution Order

1. Phase 1 first: process docs and skill fallback. This prevents future agents from following stale instructions.
2. Phase 2 next: ownership registry and import guard. This makes future refactors safer.
3. Phase 3 screen/test splitting can run in parallel by file ownership.
4. Phase 4 must be sequential because it touches event/result contracts and ViewModel dispatch.
5. Phase 5 can run alongside Phase 3 if it has a separate docs/tools owner.

## First Practical Wave

Suggested first wave with low code risk:

| Agent | Task | Owned files |
|---|---|---|
| A | Create `AGENTS.md`, slim `CLAUDE.md` | `AGENTS.md`, `CLAUDE.md` |
| B | Create architecture ownership doc | `docs/specification/architecture-ownership.md`, feature README links |
| C | Add task packet template | `docs/specification/tasks/README.md`, `docs/specification/tasks/WAVE-TEMPLATE.md` |

Do not run these in parallel if one agent must edit both `AGENTS.md` and task README. If uncertain, execute A first, then B/C.

## Done Definition For The Whole Plan

- `AGENTS.md` exists and is current.
- `CLAUDE.md` is short, current, and model-specific only.
- Skills/process fallback is explicit and does not assume unavailable repo-local commands.
- `TrainingStateAccess` lives in a neutral package.
- Architecture guard runs and documents allowlisted dependencies.
- Generated architecture reports exist.
- `VocabDrillScreen.kt`, `GrammarMateApp.kt`, and `TrainingViewModel.kt` are below target sizes or have documented exceptions.
- No sealed command/result variant carries callbacks.
- Task files use a standard agent-safe packet format.
- Trace/spec checks are part of the documented workflow.
