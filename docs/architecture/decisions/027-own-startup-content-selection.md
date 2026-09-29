# 027. Give startup content selection and state projection explicit owners

- **Status:** Accepted
- **Date:** 2026-09-29
- **Scope:** `TrainingViewModel` cold start

## Context

Startup read lessons from a saved pack before checking that the pack was still installed and belonged to the saved language. The ViewModel also mixed seeding, disk reads, pack selection, UI-state projection and feature initialization in one block. The architecture goal is coherent ownership with correct pack isolation, not a physical line target.

## Decision drivers and options

Weights follow the closure contract: business correctness 35%, ownership/isolation 30%, migration risk 20%, and testability 15%. Scores are 1–5.

| Option | Correctness | Ownership | Risk | Testability | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| Move the whole startup block into one controller | 3 | 3 | 2 | 3 | 2.90 |
| Validate startup content; separate disk reads and pure state projection | 4 | 5 | 4 | 5 | 4.45 |
| Move every language/pack/lesson transition into one context controller now | 5 | 4 | 2 | 4 | 3.75 |
| Extract only lesson content editing | 3 | 3 | 4 | 4 | 3.35 |

Choose bounded startup owners. `StartupContentResolver` checks a saved language against installed languages, accepts only a pack belonging to it, then reads lessons and IDs from that pack. A missing or foreign-language pack becomes no active pack. `TrainingStartupLoader` owns startup seeding and disk reads; `TrainingStartupState` is a pure projection of the loaded data. `TrainingViewModel` still applies the state and initializes feature owners in the previous order. The public ViewModel API and progress file format stay the same.

## Pre-mortem and challenge

Assume this failed later: the loader became another god object; a saved pack pointed at lessons from another language; startup resumed a timer before its state was published; or a unit test missed the real startup path. The loader therefore returns one typed read snapshot and owns no mutable feature state; the resolver rejects mismatched packs before any lesson read; the ViewModel retains publication and subsequent effect order. Focused tests cover missing, foreign-language and drill-only packs, saved-state projection and the zero state. Existing real ViewModel and route tests exercise startup after the move.

The Red Team rejected a broad restoration transplant: backup restore and disk reload have separate cache and write-order requirements. A later audit confirmed stale in-memory mastery and drill caches after manual backup restore, plus a cold-start restore race. Those are separate correctness work; this decision does not claim to repair them. The disabled forced bundled-pack reload also keeps its own selection path and requires a separate review before enabling it.

## Consequences and decision journal

Startup I/O remains off Main, while the projection is testable without Android lifecycle setup. A deleted saved pack now clears active pack scope rather than pairing stale IDs with current lessons. The remaining selection commands, forced-reload branch and backup restore still require ownership or correctness review.

Expected at the next G3/G6 review: no cold-start path reads lessons from an unvalidated pack; no new mutable startup-state copy appears; existing route journeys still start normally. Recheck when changing pack selection or startup sequencing.
