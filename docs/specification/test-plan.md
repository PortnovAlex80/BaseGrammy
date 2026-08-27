# Regression Test Plan - GrammarMate v2

Updated: 2026-08-27  
Status: ACTIVE, TASK-073 closure baseline  
Scope: `MainActivityV2`, `:domain`, Room/DataStore, Compose navigation and critical Android journeys

The previous version of this document described the retired legacy runtime
(`SessionRunner`, `DailyPracticeCoordinator`, `DailySessionComposer`). Its detailed
matrix remains available in `legacy-archive/legacy-test-plan.md`. The production v2
runtime uses `SessionEngine`, `DailyTaskComposer`, pack-scoped Room state and feature FSMs.

## Quality Gate

Every change must pass:

```text
./gradlew :domain:test :app:testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

CI executes the unit/lint/build gate and an API 36 emulator job. A release is not
considered regression-safe when the connected suite is skipped.

## Active Coverage Matrix

| Risk area | Owner/source of truth | Required regression level | Current anchors | Status |
|---|---|---|---|---|
| Lesson queue, advance, resume, completion | `SessionEngine` + `SessionSnapshot` | Domain unit + repository contract + VM | `SessionEngineTest`, `SessionRepositoryContractTest`, `TrainingViewModelRegressionTest` | COVERED |
| Frozen sub-lessons and pending queue after restart | persisted `pendingCardIds`, `sessionSize` | Engine + fake/Room parity | pending-queue round-trip and full-lesson tests | COVERED |
| Atomic Room snapshots and schema migration | Room transaction, migrations v1..v6 | In-memory Room + migration tests | `SessionRepositoryRoomContractTest`, `GrammarMateDatabaseMigrationTest` | COVERED |
| Pack isolation for cards, drills, hidden and mastery state | composite `(packId, id)` keys | Two-pack collision tests | pack importer, scoped hidden-card and DAO tests | COVERED |
| Pack import and bundled first run | `PackImporter`, bundled seed state | Parser/import unit + device journey | bundled ZIP regressions, Home device smoke | COVERED |
| Training FSM and persistence failures | `TrainingViewModel`/`SessionEngine` | Reducer/VM + Compose | submit/next/skip/resume/restart/error tests | COVERED |
| Daily state and Next serialization | `DailyPracticeViewModel`, `DailyTaskComposer` | Domain + VM race regression | composer matrix and double-next regression | COVERED |
| Verb and vocabulary drills | drill VMs + scoped repositories | Domain/VM + device entry | full-pass/rating/filter tests and device smoke | COVERED |
| Story resolution and rendering | imported basename path + reader state | Import unit + device entry | story path regression and markdown smoke | COVERED |
| Settings persistence and consumers | `SettingsRepository`/DataStore | Repository + VM + consumer VM | session-size persist/clamp and lesson/verb wiring | COVERED |
| Audio resource lifecycle | audio repositories/coordinators | JVM contract tests; device smoke where native model exists | ASR manifest/memory, TTS registry/cache, recognition tests | COVERED WITH DEVICE LIMITATION |
| Critical navigation | Navigation Compose routes | Connected device suite | Home -> Pack, Lesson, Story, Verb, Vocab, Daily, Pomodoro | COVERED |

## Critical Device Journeys

`GoldenJourneySmokeTest` must run against a clean install and verifies:

1. Home seeds and opens the bundled pack.
2. The first lesson opens a real Training card.
3. Chapter story opens imported markdown.
4. Verb drill opens an answer field.
5. Vocabulary drill opens a rating/reveal flow.
6. Daily practice opens a task.
7. Pomodoro opens its timer controls.
8. Settings opens the persisted lesson-size controls.

These are deliberately shallow end-to-end tests. Detailed transitions and failure
branches stay in deterministic JVM tests to keep the emulator suite stable.

## Required Regression Rules

- Any new state field must have fake and Room save/load parity tests.
- Any new session command must test duplicate taps and persistence failure.
- Any pack-owned entity must include a two-pack/same-local-id collision test.
- Any new route must have a device entry smoke and a ViewModel invalid-route test.
- Any settings field must test persistence, validation/clamp and every production consumer.
- Any Room schema change must include explicit migration and destructive-migration rejection checks.
- Flaky retries do not count as coverage; fix synchronization with semantic state/tags.

## Residual Risks

- Native ASR/TTS model execution depends on device ABI and installed model files; JVM tests
  cannot prove vendor/native cleanup. Exercise this during release testing on phone and tablet.
- Performance is guarded structurally (bounded queues, no repeated content reload on Next),
  but macrobenchmarks and startup/frame timing are not yet a release gate.
- Foldable/tablet visual layout is covered by Compose density variants, not screenshot baselines.

These are explicit platform/performance follow-ups, not unimplemented TASK-073 unit-test gaps.
