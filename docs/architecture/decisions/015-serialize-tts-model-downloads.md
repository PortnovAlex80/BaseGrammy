# 015. Serialize TTS model downloads outside AudioCoordinator

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** foreground, Settings batch and background TTS model transfers

## Context and drivers

`AudioCoordinator` held three paths for TTS model downloads, two mutable jobs, metered-network confirmation and overlapping progress projections. It also owns the public `AudioState` flow, speech playback and ASR. At startup the background batch and a Russian single-language request can be scheduled together. Manual download cancels a background job without waiting for its blocking file work to finish. The Settings batch for `it` and `ru` also loses its original request when the user confirms a metered-network warning: the old confirmation starts only the currently selected language.

| Driver | Weight |
|---|---:|
| Correct transfer behavior | 35% |
| Cohesive ownership and isolation | 30% |
| Deterministic testability | 15% |
| Progress toward the 800-line guard | 10% |
| Implementation cost | 10% |

| Option | Correctness | Isolation | Testability | Size | Cost | Weighted / 5 |
|---|---:|---:|---:|---:|---:|---:|
| A — one TTS transfer scheduler | 4 | 5 | 4 | 5 | 2 | 4.20 |
| B — offline ASR controller | 3 | 4 | 4 | 3 | 3 | 3.45 |
| C — speech/story playback controller | 4 | 4 | 3 | 3 | 3 | 3.65 |

## Decision

Choose A. `TtsModelDownloadController` owns foreground and background transfer jobs, readiness probes, metered request identity and download progress projection. Its `TtsModelSource` is a narrow adapter over the existing manager, used by production and controlled tests. `AudioCoordinator` keeps the only `AudioState` flow, TTS engine callbacks, speech playback and all existing public entrypoints. The controller updates that flow through one atomic callback; it does not cache another state copy.

A foreground request cancels and joins the background job before starting a new file writer. The job remains a guard until completion, including cancellation cleanup. Confirmation replays the exact pending selected-language or Settings batch request, even if the selected UI language changes. Initialization and engine readiness remain with `AudioCoordinator`.

## Pre-mortem and challenge

Failure modes: a cancelled background task continues writing into the foreground archive; an old metered request launches only one language; startup schedules two writers; the selected language changes while progress arrives; or a new controller becomes another audio-state owner. The single scheduler and `cancelAndJoin` close the overlap in the coordinator. Direct tests use fake flows to hold cancellation cleanup, start both requests before dispatch, change selection during transfer, and confirm a two-language metered request. The controller has no `StateFlow` or engine ownership.

Red-team review found the overlapping file-write race and the lost Settings batch. It also found that `TtsModelManager` caught `CancellationException` in its network and extraction paths. The manager now rethrows cancellation, deletes partial archives and extraction directories, and checks cancellation between extraction chunks. Joining prevents a replacement writer from starting before that cleanup ends. An interrupted blocking network read still waits for the underlying read timeout before it can observe cancellation.

## Consequences and decision journal

`AudioCoordinator` falls below the 800-line inventory guard while retaining its public API. At the next review, there should be one TTS transfer scheduler and one `AudioState` flow, and the metered batch and background-to-foreground ordering tests should remain green. Revisit if the model manager introduces parallel, per-language file paths or a persistent download queue.
