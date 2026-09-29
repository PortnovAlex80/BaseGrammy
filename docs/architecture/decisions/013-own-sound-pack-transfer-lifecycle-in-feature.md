# 013. Own sound-pack transfer lifecycle in the background-vocab feature

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** sound-pack import/download operations exposed by `TrainingViewModel`

## Context and drivers

The 2928-line `TrainingViewModel` held a lazy `SoundPackManager`, one mutable download `Job`, four transfer commands, two almost identical flow collectors, and publication into `AudioCoordinator`. This is a coherent lifecycle separate from training-session state. `AudioCoordinator` already owns the authoritative `AudioState`, while `SoundPackManager` owns ZIP/network/file work. The extraction must retain the four UI entrypoints and keep one active writer per pack transfer.

| Driver | Weight |
|---|---:|
| Transfer correctness | 35% |
| Capability ownership and isolation | 30% |
| Bounded implementation cost | 20% |
| Meaningful testability | 15% |

| Option | Correctness | Isolation | Cost | Testability | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| A — feature controller with small source boundary | 5 | 5 | 2 | 5 | 4.40 |
| B — controller composed inside `AudioCoordinator` | 4 | 4 | 3 | 4 | 3.80 |
| C — direct-manager extraction without source boundary | 4 | 4 | 5 | 3 | 4.05 |

## Decision

Choose A. `SoundPackTransferController` in `feature/backgroundvocab` owns the single transfer job, no-pack guard, import/download flow collection, count refresh and cancel behavior. A small `SoundPackTransferSource` interface and production `SoundPackManagerSource` adapter let lifecycle tests use controlled flows instead of a network or a large ZIP. The controller publishes progress and count through callbacks to `AudioCoordinator`; it owns no second UI state. `TrainingViewModel` retains its four public methods as delegates and constructs the controller lazily with the existing manager, application resolver and ViewModel scope.

The transfer job stays authoritative until it **completes**, even after cancellation. A new request during cancellation is ignored so two extractors cannot reuse the same temporary clip path. A transfer exception is converted to `DownloadState.Error`; coroutine cancellation is rethrown and remains an idle cancellation. A test reproduced the previously unhandled unsupported-pack exception before this correction.

## Pre-mortem and challenge

Possible failures: duplicating `AudioState`; starting a second transfer while a cancelled ZIP writer is still finishing; swallowing cancellation as an error; changing the captured pack ID during a transfer; or introducing a test-only gateway unused by production. The feature controller only calls the existing audio state setters, guards on `Job.isCompleted`, rethrows `CancellationException`, captures the pack ID on start, and uses the same source interface in the production adapter and fake tests. Focused tests cover no pack, progress and count publication, import arguments, exclusion during blocked cancellation cleanup, retry and unsupported-pack error.

A red-team review found that the old immediate `Job = null` on cancel could allow two transfers to overlap while `SoundPackManager` was inside a blocking ZIP entry write. The completion guard addresses that race. The manager still checks cancellation only between entries and not inside its blocking 8 KB copy loop; prompt interruption of one large entry remains an independent improvement. Switching the active pack mid-transfer can still show progress or count for the captured pack in Settings; this slice preserves that display behavior, and a pack-change integration test should precede a UI policy change.

## Consequences and decision journal

The ViewModel is 2874 physical lines after this slice, down from 2928, and still exceeds the 800-line inventory guard. The new controller is 84 lines and has a live production caller. At the next wave review, its single-job tests should pass, `AudioCoordinator` should remain the only owner of `AudioState`, and no direct `SoundPackManager` job should return to the ViewModel. Revisit if transfer state needs to be pack-scoped or if pack switching must cancel an active transfer.
