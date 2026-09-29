# 019. Isolate non-linear answer state transitions

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** `SessionRunner` correct-answer paths

## Context and choice

`SessionRunner` still combines answer validation, mode-specific state copies, card navigation, timer, audit, persistence events and word-bank effects. The remaining normal, boss and elite correct-answer branches repeat the same accepted-card fields. The runner must remain the sole writer of authoritative session state and keep the ordering of `RecordCardShow`, boss progress, completion and save events.

| Driver | Weight |
|---|---:|
| Business-rule correctness | 35% |
| Single state/effect owner | 30% |
| Testability | 20% |
| Size and migration cost | 15% |

| Option | Correctness | Ownership | Testability | Cost | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| A — pure answer snapshots, runner performs effects | 5 | 5 | 5 | 3 | 4.70 |
| B — per-mode lifecycle controllers with writes | 3 | 2 | 4 | 2 | 2.75 |
| C — move navigation and word-bank first | 4 | 4 | 3 | 4 | 3.80 |

Choose A. `SessionAnswerTransitions` computes correct-answer snapshots for normal, boss and elite sessions. It owns no flow, timer, store, audit or coroutine. `SessionRunner` continues to validate, update state, navigate cards, publish events and invoke side effects. The public `submitAnswer` contract remains unchanged. This slice does not attempt to move the whole answer workflow into a second orchestrator.

## Pre-mortem and decision journal

The main hazards are losing the boss progress event from `nextCardInternal`, changing the two state writes on a mid-card answer, calculating elite speed with the final answer's voice contribution instead of the pre-answer total, or computing normal lesson completion from a stale state. The runner keeps navigation and event assembly; normal completion facts are queried inside its state update; elite speed uses the incoming pre-answer metrics. Focused transition tests and existing runner/click tests guard these paths.

The red-team review rejected the apparent simplicity of per-mode controllers: they would need callbacks into the runner for state, timer, audit, card queues and persistence, creating several competing effect paths. Navigation-first remains a valid later slice, but leaves the largest business-rule block entangled. Revisit this decision if a transition needs external effects or the public result/event order changes.
