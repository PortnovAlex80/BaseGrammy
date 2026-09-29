# 012. Save linear completion after answer-side effects

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** `SessionRunner.submitAnswer()` result contract and `TrainingViewModel` persistence dispatch

## Context and decision drivers

A correct final answer in `VERB_DRILL`, `DAILY_TRANSLATE` or `DAILY_VERBS` sets `needsSaveProgress=true`, but `TrainingViewModel.submitAnswer()` ignored that field. The runner emitted no `SaveProgress` event on this path. The completion flag set `forceBackupOnSave`, yet a real UI regression test showed no backup after the final verb-drill answer. Saving must happen after `recordSelfProducedIfEarned()` and daily answer bookkeeping. The existing `saveProgress()` also read `uiState.value`, a combined flow that can lag its source flows immediately after a state change.

| Driver | Weight |
|---|---:|
| Persist the completed state and backup request | 40% |
| Preserve effect order | 30% |
| Keep one owner for each decision | 20% |
| Keep the correction bounded | 10% |

| Option | Correctness | Order | Isolation | Cost | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| A — conditional ViewModel save after hooks | 4 | 5 | 3 | 5 | 4.20 |
| B — emit `SaveProgress` from the runner | 2 | 2 | 4 | 4 | 2.60 |
| C — narrow result flag plus fresh persistence snapshot | 5 | 5 | 4 | 3 | 4.60 |

## Decision

Choose C. `needsSaveProgress` now defaults to `false` and is explicitly `true` for the final linear answer. The boss-final path no longer requests it because `finishBoss()` already saves through its own command. `TrainingViewModel` consumes the flag at the end of `submitAnswer()`, after setting `forceBackupOnSave`, recording mastery and accounting for daily cards. Existing event-driven saves remain in place for normal, elite and wrong-answer paths.

`dispatchSaveProgress()` snapshots `_coreState` and each feature-owned state flow directly. This keeps the persisted card/lesson state current even when the combined UI flow has not emitted yet. It still reads the daily cursor directly from its coordinator because cursor updates can precede `dailyState` emission. The public result of `TrainingViewModel.submitAnswer()` is unchanged.

## Pre-mortem and challenge

Failure modes considered: a second boss save; saving before the final mastery/daily effects; saving an older UI projection; a code path returning before the final save; or a backup task never completing. The boss flag is now false, the new save runs after those hooks, and the source-flow snapshot addresses projection lag. `isDailySession()` only admits the two screen modes handled in its `when`, so the unrelated fallback return does not bypass this path. The regression test first failed because no backup appeared, then passed with this change and checks the new backup's metadata. The global `progress.yaml` is conditional in this verb-drill fixture, so its presence is not asserted.

A red-team review argued against a bare runner event because `handleSessionEvents()` runs before `forceBackupOnSave` and daily/mastery hooks. It also identified the stale combined-flow snapshot. Both objections shaped the chosen option. The existing normal sub-lesson event saves before its ViewModel mastery hook and sets its backup flag afterward; that is an older, separate ordering concern and is not changed by this linear-session fix.

## Consequences and decision journal

One final linear answer produces one late save request and a forced backup. Ordinary linear answers continue to rely on their existing session/exit checkpoints. At the next architecture wave review, the final-answer UI regression and the runner transition tests should pass, and no additional path should set `needsSaveProgress` without owning a post-answer save order. Revisit the design if another result type needs late persistence; then a named post-answer effect contract may be preferable to more boolean flags.
