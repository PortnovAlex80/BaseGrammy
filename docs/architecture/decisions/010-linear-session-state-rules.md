# 010. Keep linear session effects in SessionRunner and extract state rules

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** `VERB_DRILL`, `DAILY_TRANSLATE`, `DAILY_VERBS` transitions in the shipping runtime

## Context and drivers

`SessionRunner` combines answer validation, mutable retry state, timer control, card navigation, audit, persistence events and three linear-session modes. Moving the whole answer block would create another coupled orchestrator. The linear paths are a smaller seam: their state projections can be pure while the existing runner keeps effect order and public entrypoints. This follows the [large-owner workplan](../../plans/2026-09-29-large-file-decomposition.md) and its behavior-preservation rule.

| Driver | Weight |
|---|---:|
| Preserve observable transitions and effect order | 40% |
| Isolate one business responsibility | 30% |
| Reduce the oversized runner meaningfully | 20% |
| Reversible implementation cost | 10% |

| Option | Correctness | Isolation | Progress | Economy | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| A — pure linear lifecycle/answer transitions | 5 | 4 | 4 | 4 | 4.40 |
| B — general answer-route and state rules | 4 | 4 | 3 | 5 | 3.90 |
| C — larger answer-outcome reducer | 3 | 5 | 5 | 2 | 3.90 |

## Decision

Choose A. `LinearSessionTransitions` computes start, replace, exit, correct-middle, correct-last, advance and explicit-Next-last snapshots. It stores no mutable state and has no Android, timer, audit or persistence effects. `SessionRunner` remains the sole command owner: it still clears input, resets/triggers retry state, pauses/resumes the timer, writes snapshots and returns events in the original order. Existing `TrainingViewModel` entrypoints stay unchanged. This moves one coherent lifecycle rule set and reduces the runner from 1418 to 1315 physical lines, while leaving the larger normal/boss/elite paths for later slices.

## Pre-mortem and challenge

If this failed later, the likely causes would be: collapsing the two state writes on a middle correct answer; incrementing voice recognition too early; counting explicit Next on the final card as a correct answer; or changing the final save/event sequence. The characterization suite records each distinction before and after the move, including state-write order. A red-team review objected that extracting only the two answer methods would be an anemic file split and leave linear policy scattered. The chosen scope therefore includes start/replace/exit and the explicit-Next completion rule as well. Manual arrow navigation remains separate because it has a different pause policy.

The review also found two **unresolved existing behaviors**, not changed here: answer de-duplication keys only on text and 300 ms, and `TrainingViewModel.submitAnswer()` does not consume `needsSaveProgress` even when the final linear answer sets it. These require separate reproduction and business-rule decisions before any fix; this structural move must not silently change them. The elite speed calculation similarly predates this decision and is outside the linear path.

## Consequences and decision journal

The new pure owner is directly called by the shipping runner and stays small. `SessionRunner` is still above the 800-line guard, so G6 remains open. At the next wave review, the seven linear characterization tests should still pass, the runner should still have one effect path for timer/input/save, and no UI caller should depend on the new internal rule object. Revisit this decision if later normal/boss extraction forces duplicated card-state rules or if a real state-emission regression appears.
