# 021. Use one pack-scoped identity for daily start and repeat

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** daily session selection, first-session cursor and block caches

## Context and choice

The first daily session selects a lesson from the pack cursor, but `TrainingViewModel` stores the currently browsed lesson ID with its card IDs. Repeat then compares those IDs with a mastery-progress lesson and may silently rebuild another session after restart. In-memory and background-prebuilt block caches also validate too few inputs. `DailyPracticeCoordinator.kt` is 799 lines, so a patch inside it alone would reopen the large-file target.

| Driver | Weight |
|---|---:|
| Saved-card replay and pack isolation | 40% |
| Clear ownership of identity/cache | 25% |
| Focused testability | 20% |
| Migration cost and reversibility | 15% |

| Option | Correctness | Ownership | Testability | Cost | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| A — pure identity resolver plus atomic plan cache | 5 | 4 | 5 | 3 | 4.45 |
| B — move the whole daily flow from ViewModel now | 5 | 4 | 3 | 2 | 3.90 |
| C — change the callback and equality check only | 3 | 2 | 2 | 5 | 2.85 |

Choose A. The coordinator remains the only owner of daily session state and card composition. A pure resolver chooses the pack lesson and level for start or same-day repeat. A small cache owner stores immutable block snapshots keyed by identity and inputs. The coordinator passes its actual chosen lesson ID to the ViewModel's first-session persistence callback. The ViewModel retains the save and audit order. A later ViewModel decomposition may move the public daily flow once these rules are characterized.

## Pre-mortem and decision journal

The change can fail if today's saved IDs are rejected because progress or browsing moved, if an old cursor's wrong lesson metadata is treated as authoritative, if a same-pack prebuild for another lesson is consumed, or if an in-memory repeat survives midnight. The resolver validates saved IDs against the active pack and prefers the lesson containing the first-session sentence IDs when metadata conflicts. Repeat caches match the persisted ID list and date; first-session prebuilds also match lesson, level, cursor offsets and session size. Stored-ID reconstruction validates restored sentence and verb counts before accepting it. Vocab cards are regenerated after restart because their IDs are not persisted.

The red-team review rejected C because it leaves stale same-pack cache reuse and the progress-based repeat-block path. B would remove more lines from the ViewModel, but moving UI selection restore, audit, persistence and cursor updates at the same time would make failures harder to attribute. Revisit the flow boundary after the identity and replay tests are stable.
