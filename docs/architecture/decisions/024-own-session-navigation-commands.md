# 024. Own card navigation as complete commands

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** autonomous-decision skill

## Context

`SessionRunner` remained above the G6 800-line guard and combined answer grading with navigation. Navigation commands have distinct timer, input, audit and event behavior. Manual arrows save once for pause and again for movement; last-card Next completes a normal or linear session without correct-answer credit; boss advancement emits progress before save. ADR-019/023 keep answer effects in the runner. The user also flagged word bank as a questionable learning mode, so this refactor preserves it without expanding its product role.

## Decision drivers

| Driver | Weight | Reason |
|---|---:|---|
| Correctness and effect order | 35% | The named training and boss paths must retain their observable and persisted results. |
| Cohesive, single effect path | 30% | Navigation should have one command owner and no competing state store. |
| G6 decomposition | 20% | A pure one-line-copy extraction alone would leave the large owner effectively intact. |
| Testability and reversibility | 15% | Existing runner behavior needs focused characterization. |

## Considered options and MCDA

Scores are 1–5, higher is better; total is weighted out of 5.

| Option | Correctness 35% | Cohesion 30% | G6 20% | Testability 15% | Total |
|---|---:|---:|---:|---:|---:|
| A: pure navigation snapshots only | 5 | 5 | 2 | 5 | 4.40 |
| B: effectful navigator calling runner word-bank methods | 3 | 2 | 5 | 3 | 3.10 |
| C: whole navigation commands with direct leaf dependencies | 4 | 5 | 5 | 4 | 4.50 |
| D: deck inventory and dedup handoff first | 5 | 5 | 1 | 4 | 4.05 |

A is safest and reversible, but the runner would still coordinate every navigation effect and remain nearly the same size. B is smaller to implement than C but creates a re-entrant write path. D is a coherent mutable boundary but removes little of the mixed command logic.

## Pre-mortem and Red Team

Assume C fails in six months: a navigation callback writes state through the runner, changing word-bank card identity; an active arrow loses one of its two SaveProgress events; a final Next credits a correct answer; or a timer flush is overwritten by a stale snapshot. The Red Team identified the callback cycle as the strongest concrete objection. Mitigation: `SessionNavigator` calls the shared timer, retry machine and `SessionWordBank` directly, never a runner effect method. Its only callback into the runner is the existing read-only completed-sublesson query. State transforms run inside the fresh `TrainingStateAccess.updateState` callback after timer actions. Runner-level tests cover arrow order, skip, normal/linear completion, boss progress and bank refresh.

## Decision and consequences

Choose C. `SessionNavigator` owns complete card-movement commands, their timer/audit/event order, and no independent session state. `SessionNavigationTransitions` owns pure card-cursor snapshots. `SessionWordBank` preserves existing selection/generation effects as a leaf dependency; it does not introduce a new learning policy. `SessionRunner` remains the public façade and answer/lifecycle owner. This removes a substantial command block while keeping one authoritative `TrainingUiState` and one typed-input flow.

The trade-off is that the runner and navigator both write the same state flow for different commands. Ownership is by command, with no duplicate state cache. `SessionRunner` remains above 800 lines, and word bank's educational value is a separate product question outside this architecture packet.

## Decision journal

**Date:** 2026-09-29. **Expectation:** In 30 days, new card-movement behavior lands in `SessionNavigator`, accepted-answer grading remains in `SessionRunner`, and no navigation callback writes through the runner. **Check trigger:** the next navigation or word-bank behavior change. **What changes this decision:** a reproducible event-order regression or a newly required shared transaction that cannot be owned by one command owner.

## References

- [ADR-019](019-isolate-session-answer-state-transitions.md)
- [ADR-023](023-keep-answer-effects-in-session-runner.md)
- [Architecture closure contract](../../plans/2026-09-29-architecture-closure-contract.md)
