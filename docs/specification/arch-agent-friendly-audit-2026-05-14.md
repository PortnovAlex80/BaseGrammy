# Agent-Friendly Architecture Audit

**Date:** 2026-05-14  
**Scope:** static architecture audit of current app code, specs, trace docs, and agent workflow docs.  
**Goal:** make GrammarMate easier and safer for parallel agent work: low context, clear ownership, short dependency chains, explicit spec links, low merge-conflict risk.

## Terminology

The useful term here is **agent-friendly architecture** or **agentic-friendly codebase**. In practical engineering terms this means:

- **low-context locality:** an agent can understand one feature by reading a small README, one spec section, one code package, and one focused test file;
- **clear ownership:** every state field, side effect, store, screen, and spec section has one primary owner;
- **bounded blast radius:** changing a feature does not force edits in central files unless the public contract changes;
- **parallel-safe work packets:** unrelated agents can work in disjoint file sets with deterministic verification;
- **machine-checkable boundaries:** architecture rules are enforced by tests/scripts, not only by prose.

"Harmless" is not the usual architecture term. The closer terms are **agent-safe**, **low-risk**, **side-effect-safe**, and **parallelizable**.

## Executive Summary

The app is much more agent-friendly than the older 2026-05-12 audit describes. The previous `TrainingViewModel` god object was reduced from 3615 lines to 1094 lines, state was grouped into domain state classes in `TrainingUiState`, feature helpers now own `StateFlow`s, stores have interfaces and centralized caching, and feature packages have READMEs.

The main remaining problem is not "no architecture". It is **architecture that is documented but not yet enforced**. The repo relies on humans/agents remembering boundaries. A parallel wave can still collide in `TrainingViewModel.kt`, `GrammarMateApp.kt`, `Models.kt`, `trace-index.md`, and `CHANGELOG.md`.

Current agent-friendliness score: **6.5 / 10**.

- Good: specs, trace index, feature packages, helper extraction, store factory, strong tests around extracted logic.
- Weak: single Gradle module, large UI/router files, mixed old/new ViewModel access, callback-in-result command patterns, stale/duplicated agent instructions, manually maintained trace docs.
- Highest leverage next step: add an **architecture ownership registry + import guard + task packet template** before further large feature waves.

## What Improved Since The Older Audit

### 1. ViewModel decomposition is real

Current largest files:

| File | Lines | Notes |
|---|---:|---|
| `ui/TrainingViewModel.kt` | 1094 | Down from 3615. Still a central action/dispatch hub. |
| `feature/training/SessionRunner.kt` | 660 | Owns session lifecycle. Good extraction, but now near helper size limit. |
| `feature/daily/DailyPracticeCoordinator.kt` | 567 | Daily orchestration is outside the ViewModel. |
| `shared/audio/AudioCoordinator.kt` | 453 | Audio lifecycle mostly centralized. |
| `feature/progress/ProgressTracker.kt` | 376 | Mastery/progress facade exists. |

Evidence:

- `TrainingViewModel.kt:64-260` wires feature instances and exposes accessors like `vm.audio`, `vm.training`, `vm.daily`, `vm.settings`.
- `TrainingViewModel.kt:212-233` combines core state with feature-owned flows.
- `Models.kt:398-408` defines nested domain state in `TrainingUiState`.

### 2. Store duplication was mostly addressed

The old audit flagged multiple independent store instances. Now:

- `AppContainer.kt:12-35` centralizes dependency access.
- `StoreFactory.kt:12-102` caches singleton stores and pack-scoped stores.
- `WordMasteryStore`, `VerbDrillStore`, `MasteryStore`, `ProgressStore`, etc. have interfaces plus `Impl` classes.
- `TtsProvider` gives a singleton `TtsEngine`, used by `AppContainer` and `AudioCoordinator`.

This materially reduces stale caches and file write races.

### 3. Feature-local README files help agents

Good examples:

- `feature/training/README.md`
- `feature/daily/README.md`
- `feature/progress/README.md`
- `feature/boss/README.md`
- `shared/audio/README.md`

These describe API surface, state ownership, dependencies, and edit scope warnings. This is exactly the right direction.

### 4. Tests now cover extracted modules

Current test files are not tiny, but they exist around the right seams:

- `SessionRunnerTest.kt`
- `CardProviderTest.kt`
- `DailyPracticeCoordinatorTest.kt`
- `ProgressTrackerTest.kt`
- `BossBattleRunnerTest.kt`
- `AnswerValidatorTest.kt`

This makes feature-level agent work safer than when everything lived inside `TrainingViewModel`.

## Remaining Architecture Risks

### P0 - Boundaries are documented, not enforced

The project is still a single Gradle module:

```text
settings.gradle.kts -> include(":app")
```

Package boundaries are social rules only. Current import graph shows cross-feature links:

| Edge | Import count |
|---|---:|
| `feature/progress -> data` | 46 |
| `feature/daily -> data` | 43 |
| `feature/training -> data` | 41 |
| `ui -> feature/training` | 9 |
| `ui -> feature/progress` | 7 |
| `feature/progress -> feature/daily` | 4 |
| `feature/daily -> feature/training` | 3 |
| `feature/training -> feature/daily` | 2 |
| `feature/boss -> feature/training` | 2 |

Some of this is expected, but it is not machine-checked. `TrainingStateAccess` lives in `feature/daily/DailySessionHelper.kt`, yet it is imported by training, boss, progress, vocab, shared, and audio. That makes `feature.daily` look like a core module, which is misleading for agents.

Recommended fix:

1. Move `TrainingStateAccess` to a neutral package such as `shared/state` or `core`.
2. Add `docs/specification/architecture-ownership.md` with allowed dependency rules.
3. Add a lightweight import guard script, for example `tools/architecture_guard.ps1`, and run it in the regression checklist.

Target rules:

```text
data -> no app feature/ui imports
feature/* -> data + shared/state only, except explicitly allowed edges
shared/audio -> data + shared/state only
ui -> feature + shared + data state models
TrainingViewModel -> composition/dispatch only
```

### P0 - Hotspot files still block parallel agents

Largest current hotspots:

| File | Lines | Why it blocks parallelism |
|---|---:|---|
| `ui/VocabDrillScreen.kt` | 1190 | Over the 1000-line screen guideline. |
| `ui/TrainingViewModel.kt` | 1094 | Under 1200, but still central for many tasks. |
| `ui/GrammarMateApp.kt` | 929 | Router + dialog orchestration + feature wiring. |
| `ui/VerbDrillScreen.kt` | 843 | Large screen with several workflows. |
| `ui/DailyPracticeScreen.kt` | 829 | Large screen with block-specific behavior. |
| `ui/screens/TrainingScreen.kt` | 729 | Training UI still broad. |

For agent work, file size matters because one large file becomes a merge-conflict magnet even if the logic is conceptually separated.

Recommended split order:

1. `VocabDrillScreen.kt`: extract filter panel, flashcard body, rating controls, voice/TTS controls.
2. `GrammarMateApp.kt`: move dialogs into `ui/components/dialogs` or route-specific functions.
3. `TrainingViewModel.kt`: finish moving legacy facade methods to domain accessors or delete unused wrappers.

### P1 - `TrainingViewModel` is still the global action broker

The ViewModel has many public methods, including selection, training, daily, boss, drill, vocab, story, settings, restore, reports, and audio compatibility methods. It also owns four command dispatchers:

- `handleSessionEvents`
- `handleBossCommands`
- `handleSettingsResults`
- `handleProgressResults`

The accessors at `TrainingViewModel.kt:244-260` are good, but UI still mixes new and old calls. Example from `GrammarMateApp.kt`: some calls go through `vm.audio` / `vm.daily`, while others call wrappers like `vm.pauseSession()`, `vm.startDailyPractice()`, `vm.finishBoss()`, `vm.clearBossRewardMessage()`.

Recommended rule:

- Keep `TrainingViewModel` as composition root + state combiner + navigation owner.
- Route feature actions through `vm.training`, `vm.daily`, `vm.boss`, `vm.audio`, `vm.settings`, `vm.reports`, `vm.story`.
- Keep wrapper methods only when they coordinate multiple features.

This will reduce future conflict frequency in `TrainingViewModel.kt`.

### P1 - Command/result objects still contain callbacks

`SessionEvent.kt` and `ProgressResult.kt` include "callback-in-result" variants:

- `SessionEvent.CalculateCompletedSubLessons(... callback: (Int) -> Unit)`
- `SessionEvent.GetMastery(... callback: (...) -> Unit)`
- `SessionEvent.GetSchedule(... callback: (...) -> Unit)`
- `ProgressResult.NormalizeEliteSpeeds(... callback: (...) -> Unit)`
- `ProgressResult.ResolveEliteUnlocked(... callback: (...) -> Unit)`
- `ProgressResult.ParseBossRewards(... callback: (...) -> Unit)`

This is better than direct ViewModel callbacks, but it still creates hidden control flow. Agents must inspect both the event producer and the dispatcher to understand behavior.

Recommended fix:

- Replace query-style command callbacks with injected query interfaces or explicit method dependencies.
- Keep sealed commands for side effects only.

Good target:

```kotlin
interface SessionQueries {
    fun getMastery(lessonId: String, langId: String): LessonMasteryState?
    fun getSchedule(lessonId: String): LessonSchedule?
    fun calculateCompletedSubLessons(...): Int
}
```

Then `SessionEvent` stays a pure side-effect list: save, play sound, refresh flowers, rebuild cards.

### P1 - State ownership is split between feature flows and core writes

The state model is much improved:

```kotlin
TrainingUiState(
  navigation,
  cardSession,
  boss,
  story,
  vocabSprint,
  elite,
  drill,
  flowerDisplay,
  audio,
  daily
)
```

But ownership is not yet strict enough. Several modules own a local `StateFlow` and also write some core state through `TrainingStateAccess`.

Examples:

- `BossOrchestrator` owns `BossState`, but also writes `cardSession` and `navigation`.
- `FlowerRefresher` owns `FlowerDisplayState`, but its README says it also writes ladder rows into core state.
- `SessionRunner` owns private `sessionCards`, `bossCards`, `eliteCards`, and writes `cardSession`, `drill`, `elite`, sometimes boss-adjacent state.
- `DailyPracticeCoordinator` owns `DailyPracticeState`, but also carries private cache/cursor fields.

Recommended fix:

Create an ownership table that is treated as a contract:

| State subtree | Only writer | Other modules may |
|---|---|---|
| `navigation` | `TrainingViewModel` | read only |
| `cardSession` | `SessionRunner` | request via commands |
| `boss` | `BossOrchestrator` | read only |
| `daily` | `DailyPracticeCoordinator` | read only |
| `audio` | `AudioCoordinator` | read only |
| `flowerDisplay` | `FlowerRefresher` | read only |
| `story` | `StoryRunner` | read only |
| `vocabSprint` | `VocabSprintRunner` | read only |
| `drill` / `elite` | `SessionRunner` | read only |

If a module must update another subtree, it should return a command instead of writing directly.

### P1 - Agent instructions are stale and tool-specific

`CLAUDE.md` is very useful, but it contains stale facts and vendor-specific workflow rules:

- says `TrainingViewModel` is around 1500 lines, current is 1094;
- describes older `ui/helpers` structure while current code uses `feature/*` and `shared/audio`;
- contains mandatory subagent/team rules that may conflict with other agent runtimes;
- has mojibake in many Russian strings and arrows, which increases cognitive noise.

Recommended fix:

1. Create a model-neutral `AGENTS.md` as the primary repo instruction.
2. Keep `CLAUDE.md` as a Claude-specific adapter that points to `AGENTS.md`.
3. Add a short "Current Architecture Snapshot" section generated or reviewed after each architecture wave.
4. Fix file encoding to UTF-8 without mojibake.

### P2 - Specs are strong, but trace maintenance is manual

`docs/specification/trace-index.md` is valuable. It maps symbols to specs and UCs. But line references are approximate and the file must be manually updated.

Agent risk:

- a code agent can implement a behavior but forget the trace row;
- another agent can edit the same trace index and create conflicts;
- stale line numbers look authoritative.

Recommended fix:

- Keep `trace-index.md` as human-readable.
- Add generated support files:
  - `docs/specification/generated/file-size-report.md`
  - `docs/specification/generated/import-graph.md`
  - `docs/specification/generated/symbol-index.md`
- Add a small checker that fails when a changed pilot screen has no trace-index update.

### P2 - Root README is not a developer onboarding entry

`readme.md` currently reads like lesson/content notes, not project onboarding. For agents, the root README should answer:

- what app this is;
- how to build/test on Windows;
- where specs live;
- where architecture map lives;
- what files are high-risk;
- how to choose the smallest context for a task.

Recommended fix:

- Move current lesson content into `docs/content/` or `lesson_packs/`.
- Make `README.md` a 1-page developer/agent entrypoint.

### P2 - Tests are broad but not yet agent-sized

The extracted modules have tests, but some test files exceed 1000 lines:

- `DailyPracticeCoordinatorTest.kt`: 1384
- `ProgressTrackerTest.kt`: 1182
- `SessionRunnerTest.kt`: 1141
- `CardProviderTest.kt`: 1051

This is acceptable for coverage, but not ideal for fast agent comprehension.

Recommended fix:

- Split by behavior:
  - `SessionRunnerSubmitTest`
  - `SessionRunnerNavigationTest`
  - `SessionRunnerEliteDrillTest`
  - `DailyPracticeCursorTest`
  - `DailyPracticeBlockTransitionTest`
- Add task-specific Gradle commands to each task file.

## Parallel Work Safety Map

### Safer parallel lanes

These are good candidates for concurrent agents if each task has a clear file set:

| Lane | Main files | Notes |
|---|---|---|
| Audio/TTS | `data/TtsEngine.kt`, `shared/audio/*`, TTS button components | Avoid mixing with unrelated UI router changes. |
| Daily practice logic | `feature/daily/*`, `DailyPracticeCoordinatorTest.kt`, `09-daily-practice.md` | Keep `TrainingViewModel` edits minimal. |
| Training engine | `feature/training/*`, `SessionRunnerTest.kt`, `CardProviderTest.kt` | Avoid simultaneous boss/session changes. |
| Store/cache work | `data/*Store.kt`, `StoreFactory.kt`, focused store tests | Good isolated backend lane. |
| Single-screen UI split | one screen file + extracted component files | Good if no ViewModel contract changes. |
| Spec-only task creation | `docs/specification/tasks/*` | Good parallel read-only/write-light lane, but avoid same index files. |

### Avoid parallel edits unless coordinated

| File | Why |
|---|---|
| `ui/TrainingViewModel.kt` | central dispatcher and compatibility facade |
| `ui/GrammarMateApp.kt` | router, dialogs, feature wiring |
| `data/Models.kt` | shared state/data model hub |
| `docs/specification/trace-index.md` | single global trace file, frequent conflicts |
| `docs/specification/CHANGELOG.md` | single global log |
| `CLAUDE.md` / future `AGENTS.md` | global agent behavior |

For these files, use sequential waves or one owner agent.

## Recommended Agent-Friendly Roadmap

### Phase 1 - Enforce boundaries, no feature behavior changes

1. Add `docs/specification/architecture-ownership.md`.
2. Move `TrainingStateAccess` to neutral package `shared/state` or `core`.
3. Add architecture import guard script.
4. Add generated file-size and import-graph reports.
5. Update `CLAUDE.md` or create `AGENTS.md` with current facts.

Expected impact: agents stop guessing architecture rules.

### Phase 2 - Reduce conflict hotspots

1. Split `VocabDrillScreen.kt` below 700 lines.
2. Split `GrammarMateApp.kt` dialogs and route helpers.
3. Convert remaining `vm.someWrapper()` calls to domain accessors where no cross-feature coordination is needed.
4. Split large test classes by behavior.

Expected impact: more agents can work in parallel without touching the same files.

### Phase 3 - Clean command flow

1. Remove callback-in-result variants from sealed event/result types.
2. Add explicit query interfaces for session/progress queries.
3. Make feature commands side-effect-only.
4. Update trace-index section for result types.

Expected impact: agents can reason about feature behavior by reading one producer, one command type, and one dispatcher.

### Phase 4 - Make specs executable enough

1. Add `tools/spec_trace_check` or equivalent simple checker.
2. Add per-task verification commands to every task file.
3. Generate symbol/file maps before each wave.
4. Keep `trace-index.md` high-level and avoid line-number dependence where possible.

Expected impact: spec-driven design becomes operational, not just documentary.

## Suggested Task Packet Template

Use this for future agent work:

```md
# TASK-NNN: Title

## Goal
One observable behavior or one refactor boundary.

## Read First
- Spec: docs/specification/XX-file.md#section
- Trace: docs/specification/trace-index.md#symbol
- Feature README: app/src/main/java/.../README.md

## Owned Files
- path/to/file.kt
- path/to/test.kt

## Do Not Touch
- TrainingViewModel.kt unless explicitly listed
- GrammarMateApp.kt unless explicitly listed
- Models.kt unless explicitly listed

## Contracts
- State owner:
- Store owner:
- Public methods allowed:
- UC/AC:

## Verification
- Gradle command:
- Focused test:
- Manual code check:

## Trace Updates
- trace-index row:
- changelog row:
```

## Short Verdict

The app is already halfway to being agent-friendly. The important shift now is from **refactoring into helpers** to **enforcing and indexing ownership**.

Best next architecture investment:

1. machine-check package boundaries;
2. make `AGENTS.md` current and model-neutral;
3. split remaining hotspot files;
4. remove callback-in-result control flow;
5. generate architecture maps instead of relying only on manual trace docs.

After that, parallel agent work can be planned around stable feature lanes instead of always routing through central files.
