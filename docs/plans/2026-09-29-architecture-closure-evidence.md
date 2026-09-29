# Architecture closure evidence

Milestone: [architecture closure contract](2026-09-29-architecture-closure-contract.md) on `feature/atomic-design`. Closed on 2026-09-30 after the last production change, full JVM/build gate and final instrumented route gate.

## Gates

| Gate | Evidence | Current result |
|---|---|---|
| G1 | [Package boundary map](../architecture/package-boundaries.md), `scripts/check_package_boundaries.py`, its unit tests and `.github/workflows/ci.yml` | PASS: 238 production Kotlin files checked; 3 guard self-tests pass |
| G2 | [State/effect owner trace](../architecture/state-effect-ownership.md), synchronous command snapshot [ADR-031](../architecture/decisions/031-read-command-state-synchronously.md), live-caller audit and boss restore regression | PASS: one command-time owner snapshot, named effect owners and no observed competing production reader/writer |
| G3 | B1–B5 source-to-test matrix below | PASS: full 679-test JVM gate, including all named transition/click regressions |
| G4 | D1–D5 disposition below | PASS: D1–D4 code regressions pass in full suite; D5 remains `BLOCKED_BY_DESIGN` by contract. Headset/microphone hardware remains untested. |
| G5 | Build/unit/instrumented log and [route matrix](2026-09-29-route-matrix.md) below | PASS: build, 679 JVM tests and 2 device tests; all 10 reachable routes passed production-entry smoke |
| G6 | [Fixed 14-file responsibility review](2026-09-29-large-file-decomposition.md), with [ADR-026](../architecture/decisions/026-use-ownership-not-line-count-for-decomposition.md) | PASS: all 14 baseline owners reviewed for responsibility, live callers and effect/state authority; G1–G5 pass |

## Business transitions: source to executable evidence

| Scenario | Production owner/path | Regression evidence |
|---|---|---|
| B1 | `SessionRunner`, `SessionNavigator`, `TrainingScreenContent` and boss mode | `SessionRunnerNavigationTest`, `SessionRunnerBossProgressTest`, `RegularLessonClickUiTest`: correct/incorrect, pause/hint, navigation-only and next-card input/result handling through production clicks. |
| B2 | `SessionWordBank`, `SessionRunner.submitAnswer`, `TrainingScreenContent` and mastery writer | `SessionWordBankTest`, `WordBankTrainingClickUiTest`, `ChapterProgressRulesTest`, `NormalizerTest`: duplicate chips, undo, word-bank mastery exclusion and one normalized keyboard grade. Recognizer hardware quality is outside this assertion. |
| B3 | `TrainingContextResolver`, `TrainingViewModel`, pack-keyed lesson/mastery/verb/vocabulary stores | `PackSelectionJourneyClickUiTest`, `TrainingPackImportStateTest`, `MasteryStorePackScopedTest`, `PackScopedDrillRestartTest`, `VerbDrillStateValidatorTest`: active-pack content, colliding verb/word IDs and durable A→B→restart isolation, including unfiltered verb progress. The simulation-only `PackScopedLessonIsolationTest` is not counted as a gate anchor. |
| B4 | `VerbDrillViewModel`, `VerbDrillStore`, TRAINING route bridge | `VerbDrillSessionCardRegressionTest`: Repeat order, Continue answered/hint/skip exclusion versus navigation-only eligibility, and Reset preserving learning progress via actual UI controls. |
| B5 | `VocabDrillViewModel`, `WordMasteryStore`, `DailyPracticeCoordinator` and pack cursor | `VocabDrillClickUiTest`, `VocabDrillStreakTest`, `DailyPracticeCoordinatorReplayTest`, `DailyCursorControllerTest`, `DailyStreakCompletionTest`: flip/rating/interval/restart and deterministic daily rules. Daily UI entry remains disabled. |

The refactor also exposed progress-loss paths outside the frozen B1–B5 matrix. `BackupRestoreConsistencyTest` checks a managed snapshot with live cached stores and legacy filenames; `TrainingProgressWriterTest` guards pending timer writes across restore; `SessionRunnerInputTest` guards transient answer clearing. `TrainingBossRewardRestoreTest` checks that a disk reload hydrates the boss state owner and that ordinary pack changes do not erase rewards on the next save. These are high-impact regressions within G2/G5, not an expansion to unrelated screens.

## Frozen defect dispositions

| ID | Disposition and evidence |
|---|---|
| D1 | Fixed Home scroll ownership; `HomeCompactViewportTest` asserts lower actions on compact viewport and passes in the final full suite. |
| D2 | Same-card pause/hint preserves draft; card navigation and new card clear it. `SessionRunnerNavigationTest`, `SessionWordBankTest`, `RegularLessonClickUiTest` pass in the final full suite. |
| D3 | Wrong voice attempts publish visible retry feedback. `VocabDrillClickUiTest` covers rendered feedback; actual microphone recognition remains `UNTESTED` without hardware evidence. |
| D4 | `SpeechRouteOwner` owns closeable Bluetooth/audio leases; `SpeechRecognitionAttemptTest` and `SpeechRouteOwnerTest` cover launch/cancel/result/disposal lifecycle. Headset routing on hardware remains `UNTESTED`. |
| D5 | `BLOCKED_BY_DESIGN`: Daily entry is disabled; `onFlip` is deferred and not counted as a pass. |

## Final-revision run log

| Check | Command or environment | Result |
|---|---|---|
| Package guard | `python scripts/check_package_boundaries.py` | PASS: 238 production Kotlin files |
| Guard self-tests | `python -m unittest discover -s scripts -p test_check_package_boundaries.py` | PASS: 3 tests |
| Debug APK | `:app:assembleDebug` | PASS |
| Full JVM suite | `:app:testDebugUnitTest --no-parallel --max-workers=1` | PASS: 117 XML suites, 679 tests, 0 skipped/failures/errors; combined assemble/unit Gradle run finished in 13m 1s |
| Android instrumented | `:app:connectedDebugAndroidTest --no-parallel --max-workers=1` on `Medium_Phone_API_36.1` / `emulator-5554` | PASS: 2 tests, 0 skipped/failures/errors; final run 2m 47s |
| Production routes | [14-route matrix](2026-09-29-route-matrix.md) | 10 reachable `PASS`; Daily `BLOCKED_BY_DESIGN`, Story `UNTESTED` without entry, Elite/Vocab `UNREACHABLE_LEGACY` with static redirect check |

## Residual risk

The disabled Daily UI and legacy redirects are not shipping user journeys. Story quiz has no visible entry in the reviewed route graph. Physical microphone recognition and Bluetooth headset routing are not proven by the emulator's UI smoke; the lease and feedback state machines have focused tests. Word Bank remains a compatibility mode; its teaching value is a product decision outside this architecture milestone.
