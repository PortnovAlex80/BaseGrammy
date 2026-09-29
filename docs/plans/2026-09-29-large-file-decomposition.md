# Large-file decomposition workplan

Date: 2026-09-29. Baseline: `d8d228d67` on `feature/atomic-design`.
Decision: [ADR-009](../architecture/decisions/009-decompose-large-owners.md).
This is part of the [architecture closure contract](2026-09-29-architecture-closure-contract.md), not a separate open-ended cleanup.

## Target

The user explicitly includes large files and god objects in the modularity goal. Audit the **14 production Kotlin files over 800 physical lines** at this baseline and split each by a named responsibility. The 800-line threshold is an inventory and regression guard, not permission to move code into arbitrary wrappers. Each extracted owner must have a coherent reason to change, own its relevant mutable state/effects, and have a live caller. No new extracted owner may exceed 800 physical lines. Do not use extension functions or partial classes solely to make the counter green.

| Wave | Baseline file (physical lines) | Responsibility boundary to establish |
|---|---|---|
| 1 — session/application state | `ui/TrainingViewModel.kt` (2918) | Keep app-state composition/public façade; move Pomodoro, sound-pack, pack/startup and other independent decisions to feature owners |
| 1 — session/application state | `feature/training/SessionRunner.kt` (1449) | Separate active-time timer, card/answer transitions and word-bank mechanics without changing the authoritative session state |
| 1 — session/application state | `ui/VerbDrillViewModel.kt` (1065) | Separate selection/deck preparation from saved-session and progress rules |
| 1 — session/application state | `feature/daily/DailyPracticeCoordinator.kt` (1019) | Separate cursor persistence, block construction and rating transitions |
| 2 — data/audio | `data/LessonStore.kt` (1212) | Separate pack/manifest/file access from lesson lookup and cache policy |
| 2 — data/audio | `shared/audio/AudioCoordinator.kt` (939) | Separate playback lifecycle from download/model orchestration |
| 2 — data/audio | `data/validation/DataValidator.kt` (899) | Separate validators by imported content type while retaining one import-result contract |
| 2 — data/audio | `data/Models.kt` (853) | Move related model groups to owning files; this is declaration organization, not a new business service |
| 2 — data/audio | `data/TtsEngine.kt` (836) | Separate model initialization/resource ownership from synthesis/playback mechanics |
| 3 — UI orchestration | `ui/screens/SettingsScreen.kt` (1233) | Feature-owned settings sections receive values/callbacks; no direct store access from display code |
| 3 — UI orchestration | `ui/screens/HomeScreen.kt` (1105) | Extract pack/lesson/entry sections with clear input contracts; retain one scroll owner so lower actions remain reachable |
| 3 — UI orchestration | `ui/GrammarMateApp.kt` (1075) | Keep route graph; move independent dialog/permission/route effect ownership to named integration objects |
| 3 — UI orchestration | `ui/VocabDrillScreen.kt` (1015) | Separate selection, active card and result presentation while retaining one voice/flip lifecycle owner |
| 3 — UI orchestration | `ui/screens/TrainingScreen.kt` (924) | Separate mode-specific display and sheets from screen-level input/voice adapters |

Counts are from `System.IO.File.ReadAllLines` at the stated baseline. Later counts must use the same method. A file may need multiple small slices; each slice must compile and preserve the previous public entrypoint until its callers migrate. Sequence within a wave can change when a dependency requires it, but the fixed 14-file target cannot grow without a separately recorded decision.

## Slice contract

For each file: record its current responsibilities and callers, name the responsibility being moved and its new owner, identify authoritative state and effects, add or select a meaningful transition/click test, perform one extraction, then build and run focused tests. Do not combine a structural move with a behavior fix unless a characterization test exposes a defect in the moved path. Update the owner map and physical-line table after each slice. At wave closure run the full unit gate; at final closure run the device/route gate from the architecture contract.

The first slice extracts the active-time timer from `SessionRunner` into a small training-owned class with an injected monotonic clock and callback sinks. `SessionRunner` keeps `cardSession.activeTimeMs` as authoritative state and its existing `resumeTimer`/`pauseTimer` entrypoints. Verify 500 ms display ticks, 20-tick checkpoint/save order, idempotent resume and pause flush with virtual time. This is a genuine state/effect owner, not a file-only move.

## Execution ledger

| Slice | Owner and state/effect boundary | Physical lines after slice | Evidence |
|---|---|---:|---|
| 1A — active-time timer | `SessionActiveTimer` owns running-job, clock and tick count; `SessionRunner` retains the persisted session value and public resume/pause calls. | `SessionRunner.kt` 1418; `SessionActiveTimer.kt` 56 | `SessionActiveTimerTest` verifies tick, idempotent resume, pause flush, checkpoint order and restart from saved value. Focused test and final 553-test unit suite pass. |
| 1B — linear session rules | `LinearSessionTransitions` computes pure start/replace/exit, answer and explicit-Next snapshots for verb/daily modes. `SessionRunner` keeps retry state, card queue, input, timer, writes, audit and event order. | `SessionRunner.kt` 1315; `LinearSessionTransitions.kt` 143 | Seven `SessionRunnerLinearTransitionsTest` scenarios passed before/after the lifecycle move; boss progress test also passes. Debug assembly and all 560 unit tests pass. |
| 1C — submit de-duplication | `SubmissionDedupGuard` owns duplicate-attempt identity, clock and deck epoch. `SessionRunner` still owns answer validation, card progression and audit. | `SessionRunner.kt` 1317; `SubmissionDedupGuard.kt` 39 | A runner test first reproduced a dropped valid keyboard answer on adjacent cards. Four runner cases now cover repeated keyboard text, same-card duplicates, voice races and deck replacement; focused tests and full gate pass. [ADR-011](../architecture/decisions/011-scope-submit-deduplication.md). |
| 2A — chapter models | `ChapterModels.kt` groups ladder row, chapter definition, pack-scoped chapter progress and roadmap status without changing package or public type names. | `Models.kt` 766; `ChapterModels.kt` 88 | Existing chapter progress, calculator and ViewModel integration tests exercise the relocated public declarations; final debug assembly and 553-test unit suite pass. |
| 2B — mastery validation | `MasteryStateValidator` owns persisted mastery parsing and default/warning rules. `MasteryStore` calls it directly; the old `DataValidator` method stays as a compatibility entrypoint for existing tests/callers. | `DataValidator.kt` 734; `MasteryStateValidator.kt` 183 | Existing `DataValidatorTest` mastery cases and `MasteryStore` tests cover the same result contract; debug assembly and 553-test unit suite pass. |
| 2C — resident TTS cache | `ResidentTtsCache` already owned model eviction and native free effects; its declaration now lives in a separate file while `TtsEngine` remains the live caller. | `TtsEngine.kt` 735; `ResidentTtsCache.kt` 105 | Existing `ResidentTtsCacheTest` covers eviction, replacement and free behavior; debug assembly and 553-test unit suite pass. |
| 3A — training mode display | `TrainingModeSections.kt` owns verb chips and both completion displays. These stateless composables receive card/stats and callbacks; `TrainingScreen` keeps input/voice and route orchestration. | `TrainingScreen.kt` 751; `TrainingModeSections.kt` 200 | Existing training/verb click tests cover production composables; debug assembly and 553-test unit suite pass. |

This ledger records incremental progress. Slices 1A–1C do not close the `SessionRunner` target. Slices 2A–3A bring `Models.kt`, `DataValidator.kt`, `TtsEngine.kt` and `TrainingScreen.kt` below the line guard. G6 remains open for the other 10 oversized files **and** the cohesion/ownership review of these four; in particular, the remaining validator categories and TTS initialization/playback paths must be checked for mixed responsibilities.

Next packet: characterize `SessionRunner`'s remaining normal, boss and elite answer transitions, then extract one branch at a time behind an explicit state/effect contract. Keep the public `submitAnswer` entrypoint until production callers migrate. After that, move Pomodoro history/settings and sound-pack download lifecycle out of `TrainingViewModel` into feature owners, preserving the existing UI-facing flows and callback order.

## Stop rule

G6 closes when all 14 baseline files are at or below 800 physical lines, the extracted classes are at or below that guard, and a review shows each has one named capability with no duplicate authoritative state or effect path. G1–G5 of the architecture contract must still pass. Remaining small files, visual Atomic P5 items and unrelated defects do not extend this list. A new oversized file created by these slices fails G6; an unrelated pre-existing file below the threshold is not automatically added.
