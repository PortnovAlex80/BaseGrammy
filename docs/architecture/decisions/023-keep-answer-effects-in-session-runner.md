# 023. Keep answer effects in SessionRunner

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** autonomous-decision skill

## Context

`SessionRunner.submitAnswer` mixes retry/hint state copies with timer, audit, navigation, events and persistence requests. ADR-019 already assigned accepted-answer snapshots to the pure `SessionAnswerTransitions` module and left all writes and effects in the runner. The G2/G3/G6 contract requires one authoritative writer, stable business transitions and coherent reductions of large files.

## Decision drivers

| Driver | Weight | Reason |
|---|---:|---|
| Correctness and effect order | 35% | Boss progress and late linear-session persistence have known regressions. |
| Single state/effect owner | 30% | A second writer or event dispatcher would obscure G2. |
| Testability | 20% | Retry/hint rules should be tested without Android effects. |
| Decomposition progress/cost | 15% | G6 requires meaningful boundaries, not wrappers. |

## Considered options

| Option | Approach | Pros | Cons |
|---|---|---|---|
| A: extend pure retry transitions | Move wrong/hint state projection into `SessionAnswerTransitions`; runner retains all effects. | Small, reversible, no second effect contract. | Only a modest reduction in runner size. |
| B: accepted-answer planner | Return branch, result, local events and action flags from a new pure planner. | Explicit mode matrix and larger potential reduction. | Event merging and action interpretation duplicate existing navigation and save contracts. |
| C: minimal incorrect-only helper | Move just three copy branches into a helper without a broader retry rule boundary. | Lowest immediate edit cost. | Similar to A but less clearly owned and tested. |

All three are reversible. The main unknown was whether an accepted-answer planner could preserve event and timer order without becoming a second orchestrator.

## MCDA matrix

Scores are 1–5, higher is better. Weighted total is out of 5.

| Option | Correctness 35% | Ownership 30% | Testability 20% | Progress/cost 15% | Total |
|---|---:|---:|---:|---:|---:|
| A | 5 | 5 | 4 | 2 | 4.35 |
| B | 3 | 3 | 5 | 4 | 3.55 |
| C | 5 | 4 | 3 | 2 | 3.85 |

## Pre-mortem and Red Team

Assume A fails in six months: retry UI and persisted fields drift apart; the defensive `Correct` branch changes silently; or small extractions leave `SessionRunner` oversized. Mitigations: keep the retry machine's decision in the runner, test wrong/third-wrong state and event order, preserve defensive branch fields, and continue G6 with separately coherent session owners.

The Red Team's strongest objection to B was concrete: `nextCardInternal` generates boss-progress and save events, and its returned list was once dropped, freezing the boss bar. `TrainingViewModel` also intentionally saves linear completion after progress effects. A planner that returns executable events would introduce a second ordering contract; precomputing state before `pauseTimer` could discard flushed active time or change elite scoring. These objections favor A for this packet. A future planner is acceptable only if it proves one effect owner and preserves these orders.

## Decision and consequences

Choose A. `SessionAnswerTransitions` projects the retry-machine's wrong or hint result to `CardSessionState`. `SessionRunner` alone calls the machine, audits, writes, pauses the timer and returns events. As a separate clear extension, `ReviewSessionTransitions` owns only the pure review reset snapshot; the runner still owns cards, timer, audit and the write.

The runner becomes smaller and the rules directly testable, but G6 remains open. The next decomposition must remove another coherent capability rather than split methods by length.

## Decision journal

**Date:** 2026-09-29. **Expectation:** In 30 days, wrong/hint and review changes should remain inside their transition owners, with `SessionRunner` still the only session-state/effect writer. **Check trigger:** the next change to retry, review or answer event order. **What changes this decision:** an accepted-answer contract that removes duplication while preserving boss progress, elite timer semantics and late linear persistence under tests.

## References

- [ADR-019](019-isolate-session-answer-state-transitions.md)
- [Architecture closure contract](../../plans/2026-09-29-architecture-closure-contract.md)
