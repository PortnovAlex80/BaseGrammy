# 028. Resolve one training context before transition effects

- **Status:** Accepted
- **Date:** 2026-09-29
- **Scope:** Runtime language, pack and lesson selection; lesson-pack import

## Context and decision

The ViewModel selected a language, pack, lesson IDs and lesson content in separate steps. Importing a pack replaced the visible lessons while retaining the previous `activePackId` and `activePackLessonIds`. A lesson ID shared by two packs could therefore rebuild schedules or read mastery in the wrong scope. The small `PackSelectionPlan` fixed only one entrypoint and became a second selection projection.

Weights: business correctness 35%, ownership/isolation 30%, migration risk 20%, testability 15%. Scores are 1–5.

| Option | Correctness | Ownership | Risk | Testability | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| Keep selection decisions in each ViewModel method | 2 | 1 | 4 | 2 | 2.15 |
| Move all transitions and effects into a callback-based controller | 4 | 2 | 1 | 3 | 2.70 |
| Resolve immutable target context; leave effects in ViewModel | 5 | 5 | 4 | 5 | 4.80 |

Choose `TrainingContextResolver`. It reads the installed target pack, its language, lesson IDs and content, and returns one immutable context. `TrainingViewModel` publishes that context before schedule rebuild and owns timer, audio, feature reset and persistence effects. Explicit pack identity wins when a lesson ID exists in multiple packs. Import, add-language, refresh and clear paths use the same navigation projection. The one-purpose `PackSelectionPlan` is removed.

## Pre-mortem and challenge

This could fail if a removed pack is selected, a manifest lists a missing lesson, a drill-only pack inherits a stale lesson, or the ViewModel rebuilds schedules before publishing the target scope. The resolver returns no context for an uninstalled pack, selects only a lesson present in both IDs and content, gives drill-only packs no lesson, and exposes one navigation snapshot. Direct resolver tests cover those cases; the pack-click journey and import regression exercise real ViewModel paths. Schedule-cache tests still guard equal lesson IDs with different content.

The Red Team challenged moving effectful operations into the resolver: timer and audio sequencing would then depend on callbacks into the ViewModel, obscuring the only state writer. The resolver stays read-only and owns no mutable flow. `StartupContentResolver` remains a separate cold-start validator because it must compare saved identity against the loaded installed-language snapshot before any content read; it does not write runtime selection.

## Consequences and decision journal

The pack navigation fields now change together. A pack import also scopes word mastery and vocabulary progress to the imported pack before cards rebuild. Extra pack reads on lesson selection are accepted here to keep the target validated against the installed pack; profile or I/O optimization requires evidence before altering correctness order.

Recheck this decision when adding a new pack entrypoint or changing `LessonStore` lookup semantics. A new transition must resolve target identity before feature effects, then prove persisted scope and visible content agree.
