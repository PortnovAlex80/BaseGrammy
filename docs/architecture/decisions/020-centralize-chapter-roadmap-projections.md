# 020. Centralize chapter roadmap projections

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** `TrainingViewModel` chapter reads and progress projection

## Context and choice

`ChapterRepository` already computes pack-scoped mastery snapshots, chapter cards, completion sets and first-incomplete lesson. `TrainingViewModel` repeats several of those rules and also computes the active chapter. Duplicate reads can diverge on the `completedAtMs` completion rule. The ViewModel remains the owner of `_coreState`, `chapterCards` and refresh timing.

| Driver | Weight |
|---|---:|
| Correctness of pack-scoped progress | 35% |
| Coherent ownership | 25% |
| Migration risk and reversibility | 20% |
| Reduction of ViewModel responsibilities | 20% |

| Option | Correctness | Ownership | Risk | Reduction | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| A — delegate chapter projections to existing repository | 4 | 5 | 5 | 3 | 4.25 |
| B — extract a new daily-practice flow and fix replay identity | 5 | 3 | 2 | 4 | 3.70 |
| C — extract import/reset orchestration now | 2 | 2 | 2 | 3 | 2.20 |

Choose A for this slice. `ChapterRepository` becomes the sole owner of chapter read/projection rules, including the active-chapter cursor and affected-chapter progress. `TrainingViewModel` supplies active pack/language, writes `_coreState`, and decides when to refresh the observable card list. It keeps the public methods used by screens and app routes. Pack-tile projection and daily-practice flow are separate capabilities.

## Pre-mortem and decision journal

The main hazards are reading mastery from the wrong pack, changing the `completedAtMs` rule, reordering lessons, changing the timing of multi-chapter updates, or keeping stale cards when an active pack is cleared. Direct repository tests must cover identical lesson IDs in different packs and empty/story-only chapters; click tests cover pack switching. Preserve the existing update order for affected chapters and avoid changing `activeChapterId` on a normal lesson progress refresh.

The red-team review challenged a broad new `ChapterController`: it would need both repository reads and mutable UI state, and might become a second owner of the same chapter state. Extending the existing repository and keeping state writes in the ViewModel avoids that boundary confusion. The daily replay identity defect is confirmed, but a safe repair also needs restart, cursor and prebuilt-cache tests; record it as a later bounded slice rather than blending it into this one.
