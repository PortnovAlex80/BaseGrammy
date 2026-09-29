# Architecture closure contract

Date: 2026-09-29. Baseline: `c18bbacbd` on `feature/atomic-design`.
Decisions: [ADR-008](../architecture/decisions/008-bounded-architecture-closure.md), amended by [ADR-009](../architecture/decisions/009-decompose-large-owners.md) and [ADR-026](../architecture/decisions/026-use-ownership-not-line-count-for-decomposition.md).
Status: **closed on 2026-09-30**. This document fixes the finish line; the [final evidence ledger](2026-09-29-architecture-closure-evidence.md) records the passing G1–G6 checks and route dispositions.

## Goal and boundary

Finish a bounded hardening of the **shipping `:app` runtime** so that the active learning flows have explicit owners, enforceable dependency direction and verified business transitions. Completion is determined by the gates below, not by the number of files moved, folders created or lines removed. Keep the existing Kotlin/Compose app module, navigation system and persistence formats.

This contract covers the active paths for lesson/boss training, verb/aux sessions, standalone vocabulary and pack-scoped progress. Daily TRANSLATE/VERBS and the daily vocabulary rules are checked at their coordinator/store boundaries; the Daily entry is currently disabled, so they are not counted as a passing end-to-end user route. Check all user-reachable routes at a shallow smoke level, with deeper assertions for the named learning paths. The [Atomic Design plan](2026-09-26-atomic-design-migration.md) remains a separate UI migration backlog; its 24 P5 dispatch items are not automatically required to close this architecture goal.

## Required deliverables and acceptance gates

| Gate | Finite deliverable | Pass condition |
|---|---|---|
| G1 — dependency direction | A checked-in package-boundary test/task, run by CI, and a short dependency map | Production `data/` and business `feature/` do not import `ui/`, Compose UI or Navigation. `ui/designsystem/foundation`, atoms and molecules do not import business `data/feature`, ViewModels, stores or navigation. `ui/features/<owner>/` does not import another feature's UI. Preview fixtures are outside these production rules. Existing business-to-business dependencies are listed with a reason and direction; no new cycle is accepted. Zero unlisted violations. |
| G2 — state and effect ownership | One owner table for the five rows below, plus a caller/effect audit of the named paths | Each value has one authoritative source; adapters may map it but must not create a competing mutable copy. Voice/BT launch and cleanup, navigation, timers and persistence have an identified integration owner. Duplicate active renderers and compatibility wrappers on these paths are removed or have a live caller plus a removal condition. Move the cross-feature `TrainingStateAccess` contract out of `feature/daily` to a neutral application boundary and update its consumers. |
| G3 — business rules | The fixed scenario matrix below, with executable tests for each transition and a source-to-test map | Tests assert the observable result **and** the persisted/domain result where applicable. UI claims use production composables and actual clicks; a test that only mutates a fake ViewModel or a no-op harness does not satisfy a click gate. All named assertions pass. No grading, persistence or route behavior changes without a failing characterization test and an explicit expected outcome. |
| G4 — known live defects | Disposition of the five frozen rows below | The three reachable user-visible defects are fixed and covered by focused regression evidence. Bluetooth lifecycle is traced and either wired correctly or the dead callback is removed with evidence that cleanup is owned elsewhere. Disabled Daily flip stays explicitly deferred and cannot be counted as verified; enabling that entry makes it a blocker. No new defect row is added to this milestone unless a gate reveals a reproducible high-impact regression. |
| G5 — release evidence | Build/test log, route matrix, residual-risk note and doc update at one final revision | `:app:assembleDebug`, full `:app:testDebugUnitTest --no-parallel --max-workers=1`, and the relevant instrumented smoke/click paths pass after the final code change. Every currently reachable route has a fresh smoke result. The route matrix distinguishes `PASS`, `FAIL`, `UNTESTED`, `BLOCKED_BY_DESIGN` and `UNREACHABLE_LEGACY`; the latter three never count as passes. If an emulator is unavailable, G5 remains open rather than being inferred from compilation or an old run. |
| G6 — large owners | The fixed [14-file decomposition workplan](2026-09-29-large-file-decomposition.md) | Review each tracked owner's responsibilities and move independently changing capabilities to cohesive owners with live callers. State and effects have one authoritative owner; no wrapper-only split or hidden callback cycle is accepted. Verify moved behavior through G3 and G5. Physical line counts are diagnostic, not a pass threshold. G1–G5 still pass. |

G5's route inventory is the 14 constants in `ui/navigation/Routes.kt` at the baseline; classify each against actual entry paths on the final revision. A disabled entry or compatibility redirect is still listed, but it is not counted as an exercised user route.

The minimum smoke assertion for each reachable route is: open it through its production entry, observe one route-specific screen element (rather than only a navigation state), and leave it through its intended Back/Exit control without a crash or wrong destination. A deeper B1–B5 assertion is required only where the scenario matrix names it. The following frozen list prevents a growing “all screens” requirement; baseline reachability comes from [inventory §16.2](../atomic-design/P0-baseline-inventory.md) and must be rechecked, not assumed, at G5.

| Baseline route set | Baseline classification | G5 evidence |
|---|---|---|
| `HOME`, `LESSON`, `CHAPTER_LESSONS`, `TRAINING` | User reachable | Fresh production-entry smoke for each; B1/B2/B3 deepen training/pack behavior |
| `VERB_DRILL`, `VOCAB_DRILL`, `AUX_DRILL` | User reachable | Fresh production-entry smoke for each; B4/B5 deepen verb/vocabulary behavior |
| `STORY_READER`, `BACKGROUND_VOCAB`, `LADDER` | User reachable | Fresh production-entry smoke for each |
| `DAILY_PRACTICE` | Entry disabled by product | `BLOCKED_BY_DESIGN` with disabled-entry source evidence; coordinator/store rules still tested under B5 |
| `STORY` | No visible quiz entry in the baseline walkthrough | `UNTESTED` until a real entry or deterministic state fixture is exercised; never silently count as a legacy redirect |
| `ELITE`, `VOCAB` | Compatibility redirects with no production navigation caller | `UNREACHABLE_LEGACY` after caller check; verify the redirect destination without counting them as a user journey |

Boss battle is a `TRAINING` mode, not a fifteenth route. B1 must include a deterministic boss-mode transition fixture; its absence from the ordinary walkthrough does not turn boss correctness into a route-smoke pass.

The package check is a guard against forbidden imports, not proof of runtime isolation. G2 and G3 supply the state/effect and behavior evidence that an import scan cannot provide. Android-backed stores and orchestrators may remain in `data/`/`feature/`; this contract requires pure, directly testable **rules** and correct dependency direction, not a repository-wide Android-free domain rewrite.

### State ownership table to complete under G2

| Value or effect | Current source to trace | Required recorded outcome |
|---|---|---|
| Card queue, current card, answer input, hint/pause/result | `TrainingViewModel`, `SessionRunner`, `TrainingScreenContent` | Authoritative source, transition writer(s), UI collector and reset/preserve policy per card |
| Session active time and save checkpoint | `SessionActiveTimer`, `SessionRunner`, `TrainingViewModel` | `cardSession.activeTimeMs` is authoritative persisted state; `SessionActiveTimer` alone owns the running clock/tick counter, emits display ticks, flushes on pause and calls the existing progress-save callback after each 20-tick state write. |
| Mastery and chapter/lesson progress | `MasteryStore`, `PackLessonProgressStore`, `feature/progress/` | Pack key, write trigger, restore path, WORD_BANK exclusion |
| Verb saved batch and shown/completed IDs | `VerbDrillViewModel`, `VerbDrillStore` | Repeat/Continue/Reset source and durable write path |
| Standalone vocabulary SRS rating/interval | `VocabDrillViewModel`, `WordMasteryStore` | Rating transition, pack scope, restart result; `VocabProgressStore` is a separate lesson-vocabulary concern |
| Daily block cursor | `DailyPracticeCoordinator`, `PackDailyCursorStore` | Block advancement and persistence; distinguish disabled entry from domain behavior |

### Frozen business scenario matrix for G3

| ID | Required assertion | Minimum evidence anchor |
|---|---|---|
| B1 | Correct/incorrect answer, pause/hint and navigation-only visit produce distinct state transitions; moving to a new card does not submit or display a stale answer | `SessionRunner` rule tests plus a production TrainingScreen click path (`RegularLessonClickUiTest` or equivalent) |
| B2 | WORD_BANK selection/undo works, duplicate words cannot be overused, and WORD_BANK does not record mastery; a submitted normalized keyboard or recognized-voice answer makes one grading transition with the existing mastery outcome | `WordBankSection`/TrainingScreen click path and mastery/progress test (`ChapterProgressRulesTest` or equivalent); full ASR hardware quality is not inferred from this rule test |
| B3 | Switching active packs cannot reuse another pack's lesson progress, mastery, verb batch or vocabulary rating; restart restores the chosen pack's data | `PackScopedLessonIsolationTest`, store tests and an actual pack-switch journey |
| B4 | Verb Repeat restores the full saved batch in order; Continue excludes answered or explicitly completed (hint/skip) cards while navigation-only visited cards remain eligible; Reset removes saved session context but preserves learning progress | `VerbDrillSessionCardRegressionTest` with real `verb_start_button`, `check_button`, navigation and SessionCard clicks |
| B5 | Vocabulary flip/rate updates the correct interval/progress and survives restart; daily block cursor/rating rules remain deterministic even while Daily entry is disabled | `VocabDrillClickUiTest` plus store/coordinator tests; daily unit evidence is not relabeled as an end-to-end Daily UI pass |

The named test files are existing anchors, **not automatic passes**. Before claiming B1–B5, inspect their assertions and replace placeholder/no-op coverage where it stands in for a required production action. The test count alone is not an acceptance metric.

### Frozen known-defect disposition for G4

| ID | Observed baseline issue | Required disposition before closure |
|---|---|---|
| D1 | Classic Home cannot scroll to lower drill/background-vocabulary actions with a pack active ([inventory §16.3–16.5](../atomic-design/P0-baseline-inventory.md)) | Fix reachable-action access and verify on a compact viewport; do not insert an unbounded lazy grid into a scrollable parent. |
| D2 | An earlier walkthrough reported Training input surviving a card change; current `SessionRunner` already clears its shared input flow on several transitions | Reproduce against this revision or close as already fixed with a transition/click test. The test must distinguish card-change clear from pause/hint preservation; any remaining defect is fixed at the state owner. |
| D3 | Standalone vocabulary's first two wrong voice attempts have no visible “Try again” feedback | Make live feedback visible with the correct attempt count, or if voice cannot be exercised in an emulator, retain a focused state/render test and mark runtime voice evidence `UNTESTED`. |
| D4 | `onBluetoothCleanup` is threaded through training/daily/vocabulary but has no production invocation | Trace audio lifecycle and either call cleanup at the correct boundary or remove the unused callback after proving an existing owner performs it; test the chosen lifecycle path. |
| D5 | Daily flashcard `onFlip` is a no-op while Daily entry is disabled | **Deferred by product reachability.** Record as `BLOCKED_BY_DESIGN`, not `PASS`. It becomes a blocker before any Daily entry is enabled; enabling Daily is outside this milestone. |

The D4 implementation now routes offline ASR and all four external recognizers
through one closeable audio lease. Cancellation, late setup, Busy, settings-off,
result and disposal transitions have focused tests; debug assembly and the
631-test unit gate pass. D4's code disposition is **fixed**. Actual headset
routing remains `UNTESTED` until the final device gate (G5), so it is not
counted as runtime hardware evidence.

## Stop rule and exclusions

Work in bounded packets: dependency/owner map and guard (G1–G2), the fixed 14-file decomposition (G6) alongside named business transitions (G3), frozen defect dispositions (G4), then one final evidence run (G5). A packet closes only with its pass evidence. Once all six gates pass, **stop this refactoring** and mark the milestone closed, even if Atomic P5 still has open visual surfaces. A new finding expands this milestone only if it reproducibly breaks B1–B5/G1–G6, or is a regression **introduced by this milestone** that crashes the app, loses/corrupts saved progress, or blocks a currently reachable named route. Pre-existing unrelated findings go to a separate backlog.

Outside this milestone: migrating all 24 P5 UI items, global atom/molecule/template taxonomy, new Gradle modules, moving every Android type out of business orchestrators, broad localization/theme/icon/deprecation sweeps, and activating disabled Daily/legacy routes. Large files **are** in scope through G6; mechanical line-count-only splits are not. Other work may be a separate goal with separate acceptance criteria and does not silently extend this one.

Baseline evidence is deliberately limited: commit `c18bbacbd` built and passed 551 unit tests, and instrumented-test sources compiled. The last device walkthrough and instrumented run were on an earlier revision. Neither establishes G1–G5 for this new milestone.

At this baseline **0 of 6 gates are closed**: G1 has no CI boundary guard; G2 has no completed owner/effect map and the cross-feature contract still lives under Daily; G3 has partial executable coverage mixed with placeholder suites; G4 defects are open or deferred as above; G5 has no final-revision device/route evidence; G6 has 14 tracked oversized files. Existing passing tests and prior walkthrough evidence are inputs to these gates, not replacements for them.

## Final disposition — 2026-09-30

**G1–G6 are closed** on the final shipping-code revision. The [evidence ledger](2026-09-29-architecture-closure-evidence.md) maps B1–B5 to executable tests and D1–D5 to their fixed or explicitly deferred disposition. The package guard passes for 238 production Kotlin files and its three self-tests pass. `:app:assembleDebug` and the full 679-test JVM suite pass. The final `:app:connectedDebugAndroidTest` run passes both instrumented tests on `emulator-5554`; the [route matrix](2026-09-29-route-matrix.md) records ten reachable routes as `PASS` and preserves the four non-pass classifications. The [14-file review](2026-09-29-large-file-decomposition.md) is closed by ownership and caller evidence rather than a line threshold.

This closes the agreed architecture refactor. Atomic Design P5 visual migration, enabling Daily, and hardware microphone/Bluetooth verification remain outside this milestone's pass claim.
