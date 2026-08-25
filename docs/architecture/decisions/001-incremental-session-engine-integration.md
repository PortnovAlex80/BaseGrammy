# 001. Incremental SessionEngine integration

- **Status:** Accepted
- **Date:** 2026-08-26
- **Supersedes:** none
- **Superseded by:** none
- **Decision-maker:** autonomous-decision skill

## Context

GrammarMate v2 has a sound foundation (`:domain`, Room, Hilt, Compose MVI), but its
production training path bypasses the tested `SessionEngine`. `TrainingViewModel`
creates a session without a pool, duplicates answer/advance logic and independently
updates UI and persistence. The result is split ownership of current card, counters,
shown/mastery and completion.

The architecture fork is whether to stabilize the existing boundary, introduce a new
global SessionStore/aggregate, or immediately split the codebase into feature modules.
The situation is **Complex**: current lesson defects are knowable, while the correct
semantics of incomplete legacy modes will only emerge through characterization and
vertical probes. The first normal-lesson slice is the probe.

Existing constraints:

- `:domain` remains pure Kotlin and Android-free.
- v2 is the accepted base (`TARGET_ARCHITECTURE.md`).
- Room remains the user-state transaction boundary.
- `currentCardId` is a stable PK and must be in the persisted pool.
- Regression safety and user-visible correctness outrank structural churn.

## Decision drivers

| Driver | Weight | Why it matters here |
|---|---:|---|
| Correctness and state integrity | 3 | Current defects can lose or falsely report learning progress |
| Testability and regression safety | 3 | The user explicitly requires regression coverage before broad refactoring |
| Time to user-visible UX value | 2 | Active v2 does not yet have one complete fresh-install journey |
| Alignment with existing architecture | 2 | The tested domain foundation should be reused rather than duplicated |
| Reversibility | 2 | Migration must proceed mode by mode without destructive data changes |
| Performance and observability | 1 | Session hot paths should remove extra queries and expose failures |

Scale: 1 = poor, 5 = excellent. The weighted maximum is 65.

## Considered options

### Option A - Conservative vertical stabilization

Keep `:app + :domain`. Connect the existing `SessionEngine`, make it the only mutating
training path, add explicit UI phases and atomic Room operations, then migrate modes as
tested vertical slices. Use package boundaries and dependency checks; defer physical
module extraction.

Pros: smallest structural change, fastest fix for confirmed defects, reuses 442 tests,
high rollback ability. Cons: `:app` remains a broad module and compile-time feature
isolation is deferred.

### Option B - New session-centric Store and aggregate

Add a global serialized `SessionStore`, a new `SessionAggregate`, commands, revisions and
mode policies. ViewModels dispatch commands and observe one store. Migrate one mode at a
time behind flags.

Pros: explicit single writer and strong concurrency model. Cons: duplicates the current
`SessionSnapshot + SessionEngine + saveSession` boundary, risks a new god object and adds
a third state model before mode semantics are known.

### Option C - Deep feature/Gradle modularization

Create `core:*`, `training:*` and `feature:*` modules, typed navigation contracts and a
shared training runtime, then migrate features into the new graph.

Pros: strongest long-term compile-time isolation and local build/test ownership. Cons:
highest churn, weakest reversibility and slowest path to a working user journey while
the feature contracts are still incomplete.

## MCDA matrix

The initial scoring preferred B, but Red Team evidence showed that its alignment score
was overstated because the proposed store duplicates an existing engine/snapshot
boundary. The corrected matrix is:

| Option | Correctness (3) | Testability (3) | UX value (2) | Alignment (2) | Reversibility (2) | Perf/obs (1) | Weighted total |
|---|---:|---:|---:|---:|---:|---:|---:|
| A. Conservative stabilization | 4 | 4 | 5 | 5 | 5 | 4 | **58** |
| B. New SessionStore | 4 | 5 | 3 | 2 | 3 | 4 | **47** |
| C. Deep modularization | 4 | 5 | 2 | 3 | 2 | 5 | **46** |

Sanity check: A wins because it directly repairs the confirmed production bypass and is
the easiest to undo. No criterion alone determines the result. B and C remain possible
future decisions after the session/mode contracts become stable.

## Pre-mortem

Assumption: Option A was implemented and failed six months later.

1. **TrainingViewModel became another coordinator god object** - likelihood: medium;
   detectable by file size, repository imports and mode branches; mitigation: ViewModel
   orchestration only, pure policies/use cases and forbidden-import checks.
2. **Mode behavior was ported ad hoc and diverged** - likelihood: high without a gate;
   detectable by missing/TBD mode-matrix rows; mitigation: characterization and journey
   tests before each route is enabled.
3. **Atomicity stopped at session snapshot and omitted mastery/reward** - likelihood:
   medium; detectable by failure-injection tests; mitigation: a Room transaction
   coordinator for each business event before the mode ships.
4. **Deferred modularization became permanent accidental coupling** - likelihood:
   medium; detectable through dependency graph/build profile; mitigation: explicit
   extraction triggers in the roadmap and a 90-day review.
5. **Old and new execution paths coexisted indefinitely** - likelihood: medium;
   detectable by feature flags older than one release; mitigation: no dual writes and a
   mandatory removal date/owner for every adapter or flag.

**Net effect:** A survives with the guardrails above. It is a constrained vertical
stabilization, not permission to keep business logic in ViewModel.

## Red Team

**Strongest argument against the original leading option B:** the proposed Store creates
a third source of truth. The repository already contains `SessionSnapshot`, a tested pure
`SessionEngine`, `SessionRepository.saveSession()` and transactional
`SessionDao.saveSnapshot()`. The defect is the production bypass, not a missing aggregate.

**Source in repo:** `TrainingViewModel.kt:83-99` creates a pool-less session and masks it
with a UI fallback; `TrainingViewModel.kt:157-162` then cannot advance the empty pool.
`SessionEngine.kt:95-108` already builds and persists the correct pool, and
`SessionEngineResumeRegressionTest` protects its invariants.

**Response:** accepted. The decision switched from B to A. A must add serialization,
transaction and dependency guardrails where evidence requires them, without introducing
a new global state model.

## Decision

Chose: **Option A - Conservative vertical stabilization.**

Connect the existing `SessionEngine` to production, prevent presentation from mutating
session repositories directly, and migrate user journeys one at a time under executable
mode contracts and regression gates. New SessionStore and physical feature modules are
deferred until measurements show the existing boundary is insufficient.

## Consequences

**Positive:**

- Confirmed session bugs can be fixed with minimal structural churn.
- Existing domain tests and Room schema remain useful.
- Each mode can be released and rolled back independently.
- Architectural work is tied to visible UX outcomes.

**Negative:**

- `:app` remains broad in the near term.
- Package/dependency rules initially provide weaker isolation than Gradle modules.
- Cross-repository atomic completion still requires a transaction coordinator.

**Neutral / follow-ups:**

- Add the full test pyramid described in the roadmap.
- Reconcile mode types only after the executable mapping exists.
- Revisit SessionStore or module extraction when the recorded triggers fire.

## Decision Journal

**Date:** 2026-08-26

**Decision:** integrate the current SessionEngine incrementally instead of adding a new
global store or starting immediate deep modularization.

**Ex-ante expectations - if this decision is right:**

- In 30 days: a fresh-install lesson journey passes through `SessionEngine`, presentation
  has zero direct session writes, and process-resume/double-submit tests are green.
- In 90 days: every released training mode has an executable contract and E2E test; no
  compatibility path or feature flag is older than one stable release.
- In 6 months: session/state bug fixes touch policy/use-case code rather than multiple
  ViewModels/Compose callbacks, and performance budgets have not regressed over 10%.

**Check trigger:** 2026-11-26, or earlier if two ViewModels need concurrent write access to
one session, `:app` dependency violations repeat, or incremental build time becomes a
measured bottleneck.

**What would change the decision:** evidence that a single Engine plus serialized
ViewModel/use-case orchestration cannot prevent concurrent writers, or build/dependency
metrics show package boundaries are insufficient. That evidence would trigger a new ADR
for SessionStore or physical feature modules.

## References

- `docs/architecture/TARGET_ARCHITECTURE.md`
- `docs/v2-architecture/ARCHITECTURE.md`
- `domain/src/main/java/com/alexpo/grammermate/domain/session/SessionEngine.kt`
- `domain/src/test/java/com/alexpo/grammermate/domain/session/SessionEngineResumeRegressionTest.kt`
- `docs/architecture/REFACTORING_PLAN_2026-08-26.md`
