# 031. Read command state synchronously from its owners

- **Status:** Accepted
- **Date:** 2026-09-30
- **Scope:** `TrainingStateAccess` reads by training, daily, boss, vocabulary, progress, settings and audio commands

## Context and decision

`TrainingStateAccess.updateState` wrote `_coreState` synchronously, but its `uiState` read returned the ViewModel's `combine(...).stateIn(WhileSubscribed)` display projection. A command immediately following its own write could read the previous card, input mode, pack or session state. The production-click verb regression exposed this as a first-card read after Next; without a Compose subscriber, the projection might not update at all. A read bridge used by business commands must not depend on display collection timing.

Weights: transition correctness 40%, clear ownership 25%, migration risk 20%, hot-path cost 15%. Scores are 1–5.

| Option | Correctness | Ownership | Risk | Cost | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| Start the combined display flow eagerly | 2 | 2 | 4 | 2 | 2.40 |
| Read only `_coreState` | 3 | 3 | 3 | 5 | 3.30 |
| Expose a synchronous owner snapshot for commands | 5 | 5 | 4 | 4 | 4.65 |

Replace the read side of `TrainingStateAccess` with `currentState: TrainingUiState`. The ViewModel constructs it synchronously from `_coreState.value` and the current audio, story, vocabulary, daily, flower and boss owner flows. `updateState` still writes **only** `_coreState`; feature coordinators write their own flows. Compose continues collecting the combined `TrainingViewModel.uiState` projection. Every production bridge read now uses `currentState`, so no command waits for or trusts a UI emission. A constructor-time guard returns core state until all runtime owners are initialized.

## Pre-mortem and challenge

The snapshot could recurse if it called `TrainingProgressSnapshotSource.capture()`: that source resolves the daily cursor through a callback that itself reads `TrainingStateAccess`. The command getter therefore reads only in-memory owner flow values and never performs cursor reconciliation or disk I/O. Owners may be uninitialized while the bridge is constructed; the guard is enabled only when the ViewModel reaches its `init` block. Several owner values can change between shallow-copy reads, so this is a fresh command snapshot rather than an atomic transaction across all features. Pack selection and grading still publish their own ordered state/effect transitions.

The Red Team rejected eager collection because `combine` still emits asynchronously after `_coreState.update` and would retain a timing-dependent business read. Reading only core state would lose boss and daily owner values that deliberately live outside it. A single `currentState` contract also removes the unused `StateFlow` from the business boundary; the former interface flow was never collected by a production consumer.

## Consequences and decision journal

The command-side test checks a newly selected pack's current card before waiting for a Compose frame. The verb click path, word-bank transitions and existing runner/daily fakes cover call-order compatibility; full unit and device gates remain required. New business owners should expose their current in-memory value through this bridge only when a command truly needs a cross-feature snapshot. Persistence snapshots stay in `TrainingProgressSnapshotSource`, which has its own cursor and write-order contract.
