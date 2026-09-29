# 025. Bind target pack scope before rebuilding schedules

- **Status:** Accepted
- **Date:** 2026-09-29
- **Scope:** `TrainingViewModel` pack selection and `CardProvider` schedule cache

## Context

`selectPack` published the new lessons and pack ID before publishing that pack's lesson IDs. Its synchronous call to `selectLesson` rebuilt schedules through the previous pack's IDs. Disjoint packs could therefore produce no schedule. A separate cache fault reused schedules when two packs shared lesson IDs and card counts but had different card content. A failing `CardProviderScheduleCacheTest` reproduced the latter before the change. Existing pack-isolation tests construct state by hand and do not exercise `TrainingViewModel.selectPack`.

## Decision drivers and options

Weights reflect G2/G3/G6: correctness 35%, coherent ownership 25%, migration risk 20%, and useful ViewModel reduction 20%.

| Option | Correctness | Ownership | Risk | Reduction | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| Preserve current selection sequence and extract only a lesson cursor resolver | 2 | 4 | 5 | 2 | 3.10 |
| Publish a target-pack plan atomically, use target IDs/mastery, and cache schedules by content | 5 | 5 | 4 | 3 | 4.35 |
| Move all selection commands into one cross-feature coordinator | 4 | 2 | 1 | 5 | 3.05 |

Choose the target-pack plan. `PackSelectionPlan` is an immutable projection with no store, state flow or callback into the ViewModel. `TrainingViewModel` retains timer, audio, feature resets and persistence in their existing order. `CardProvider` caches the derived schedule against exact lesson content and block size, so changing pack content invalidates the schedule even when IDs and counts match.

## Pre-mortem, challenge and limits

Possible failures: a pack switch publishes target lessons with stale IDs; same-ID packs retain prior cards; an in-place card-list edit evades the cache; a drill-only pack loses its entry; or effect ordering changes during a large command move. The plan writes lessons/ID/pack together, tests disjoint and shared IDs, snapshots card lists for equality, and leaves cross-feature effects in the ViewModel. The Red Team found both stale-ID and same-ID cache paths, and challenged the synthetic pack tests as insufficient integration evidence; this decision accepts that challenge. A fresh production `selectPack` journey with two packs, including disjoint and overlapping lesson IDs, is still required for G3/B3. Direct `selectLesson` with a different pack from the active navigation context is also not counted as verified by this packet.

## Decision journal

Expected within 30 days: schedule tests keep target content for equal-ID/equal-count packs, and no selection change introduces a second mutable source of pack scope. Recheck on the next pack-switch journey and during the final G3/G5 gates.
