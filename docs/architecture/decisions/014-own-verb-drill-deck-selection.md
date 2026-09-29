# 014. Keep verb-drill deck selection pure and session effects in the ViewModel

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** filter, progress summary and next-batch selection in `VerbDrillViewModel`

## Context and drivers

The ViewModel repeated the group/tense/person filter and combo key in progress display, answer persistence and session start. An inconsistent edit could count one pool while drawing from another. Continue must combine persisted and saved shown IDs; fresh start ignores persisted today IDs; Repeat replays saved batch IDs in their original order. The ViewModel still owns those commands, state updates and store writes.

| Driver | Weight |
|---|---:|
| Business-rule correctness | 35% |
| Cohesion and isolation | 30% |
| Testability | 15% |
| Progress toward the 800-line guard | 10% |
| Implementation cost | 10% |

| Option | Correctness | Isolation | Testability | Size | Cost | Weighted / 5 |
|---|---:|---:|---:|---:|---:|---:|
| A — pure deck planner first | 5 | 5 | 5 | 2 | 4 | 4.60 |
| B — catalog and reference loader first | 4 | 4 | 4 | 3 | 3 | 3.80 |
| C — saved-session checkpoint policy first | 4 | 4 | 4 | 1 | 4 | 3.70 |

## Decision

Choose A as a narrow first slice. `VerbDrillDeckPlanner` owns the shared filter/key, raw saved-set count summary and next-batch ordering/exclusion. It takes an injected shuffle function for deterministic policy tests. The ViewModel still dispatches fresh/next/Continue/Repeat, handles empty selection, tests bad-card state, writes the last session and resets timing. Repeat does not call the planner when saved IDs are present. Catalog loading remains a separate follow-up needed for the line guard.

## Pre-mortem and challenge

Failure modes: a fresh start accidentally excludes today's cards; Continue omits saved exclusions; rank ties change order; an empty batch overwrites the saved SessionCard; or the small extraction gets mistaken for closure of the 800-line target. Direct tests cover filter dimensions, raw progress counts, Continue exclusions, fresh exclusions, stable rank ties and null ranks, random eligibility and exhausted pools. Existing Compose regressions cover Repeat and Continue through real routes. The ViewModel keeps the original empty-batch return before any save.

Red-team review preferred catalog loading first because it touches less fragile behavior and removes more lines. That is valid for the size target, but leaves duplicate deck rules in the ViewModel while the catalog split proceeds. The planner's restricted scope and focused plus route tests mitigate the behavior risk; catalog loading is still required before declaring this file done.

## Consequences and decision journal

The ViewModel remains over 800 physical lines after this slice. The planner has no store, clock, UI state or I/O ownership. At the next review, filters and combo-key construction should still have one implementation, Repeat should retain saved order, and the original ViewModel must reach the line guard through additional cohesive owners. Revisit if future deck modes need explicit mode types instead of the current ViewModel-level boolean dispatch.
