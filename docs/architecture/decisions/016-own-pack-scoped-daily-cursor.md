# 016. Own pack-scoped daily cursor rules outside session coordination

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** pack cursor migration, storage and offset advancement

## Context and drivers

`DailyPracticeCoordinator` mixed active block transitions with pack cursor migration, store writes, lesson wrapping and verb-pool CSV counting. The cursor is pack-scoped and must not be written for an absent pack. `advanceDailyCursor` computes a value without persisting it; `updateCursor` later saves to the pack store before publishing the value into daily UI state. Those effects and their order are part of the existing contract.

| Driver | Weight |
|---|---:|
| Cursor and session correctness | 35% |
| Cohesive ownership | 30% |
| Testability | 15% |
| Progress toward the 800-line guard | 10% |
| Implementation cost | 10% |

| Option | Correctness | Isolation | Testability | Size | Cost | Weighted / 5 |
|---|---:|---:|---:|---:|---:|---:|
| A — pack cursor controller | 4 | 5 | 4 | 4 | 3 | 4.20 |
| B — session plan/cache owner | 3 | 3 | 3 | 4 | 2 | 3.00 |
| C — broad progress tracker | 3 | 3 | 4 | 4 | 3 | 3.25 |

## Decision

Choose A. `DailyCursorController` takes an explicit pack ID for every operation, owns pack cursor conversion, legacy initialization, persistence, deletion and offset/verb-pool advancement. `DailyPracticeCoordinator` retains its public methods and the only `DailyPracticeState` flow. Its wrappers capture the current pack, call the controller and preserve the existing save-before-UI order. Advancement remains a calculation that the caller must explicitly persist.

The controller does not own block caches, session state or the `TrainingStateAccess` interface. It keeps the existing single-lesson boundary behavior: when the sentence offset reaches the current lesson size, it advances one lesson, drops overflow and wraps the last lesson to index zero. Verb offset uses the new lesson's active tense pool and wraps when the increment reaches its size.

## Pre-mortem and challenge

Possible failures: migrating unscoped legacy data into the wrong pack; writing an `unknown` pack; publishing UI cursor state after a failed store write; persisting a computed offset prematurely; or changing the exact lesson/verb wrap boundary. Explicit pack arguments and no-write handling for a missing pack reduce the first two risks. The coordinator publishes only after `save` returns, and `advance` never writes. Tests cover migration precedence, pack isolation, no-pack behavior, saved first-session lesson identity and both wrap boundaries; daily and pack-switch UI regressions pass.

Red-team review warned that the original legacy migration itself can seed whichever selected pack lacks a cursor. This extraction preserves that policy because no source-pack marker exists. It also correctly predicted that this one owner would leave the coordinator above the line guard: it is 865 physical lines after this slice. A second cohesive daily-progress owner is required before G6 can close for this file; the cursor owner should not absorb rating or session effects just to meet a count.

## Consequences and decision journal

At the next review, cursor writes should still be pack-scoped, `advanceDailyCursor` should remain non-persisting, and no second `DailyPracticeState` owner should appear. Revisit the legacy migration policy if the persisted format gains a reliable origin pack ID. Separate first-session lesson identity and per-card mastery defects discovered during review require characterized fixes in their own daily-progress/session slices.
