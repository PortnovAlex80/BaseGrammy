# Architecture Refactor Roadmap

**Project:** GrammarMate (BaseGrammy)  
**Date:** 2026-05-22  
**Version:** 1.0  
**Status:** Executive Summary for Team Review

---

## Re-verification Addendum (2026-06-13)

> The quantitative metrics in this audit and the W1/W2/W3 documents were re-verified against the
> current codebase on **2026-06-13** (audit was authored 2026-05-22; the separate
> `architectural-analysis-report.md` was authored 2026-06-02). File sizes had drifted in two waves;
> all stale counts below were normalized to verified current values.

**Canonical current metrics (verified 2026-06-13):**

| Artifact | Audit (2026-05-22) | Report (2026-06-02) | **Actual (2026-06-13)** |
|----------|--------------------|---------------------|-------------------------|
| `TrainingViewModel.kt` | 1,570 | 2,277 | **2,579** (`class` @ line 105) |
| `GrammarMateApp.kt` | 1,321 | 1,920 | **1,998** (`fun GrammarMateApp` @ 138) |
| `SessionRunner.kt` | 1,240 | 1,310 | **1,416** (`class` @ 51) |
| `VerbDrillViewModel.kt` | 1,017 | — | **1,035** (`class` @ 44) |
| Concrete store classes | "15 YAML stores" | — | **15 `*Impl`** (audit figure is accurate; ~19 classes match `*Store` incl. `StoreFactory` + non-`Impl` stores) |
| `@Deprecated` methods | 8 | — | **6** |
| TODO / FIXME | "Zero" | — | **1 TODO** (intentional reserved-pack note, `LessonStore.kt:191`), 0 FIXME |

**Key delta since audit:** `TrainingViewModel` grew **+933 lines (1646 → 2579, +57%)** in ~3 weeks.
The growth is attributable to the **pack-scoped migration** (commits `1eeda60` "full pack-scoped
mastery isolation + v1→v2 migration", `37c3dda` "VocabProgressStore pack-scoped", `ee22a42` pack
tiles UI, plus several lesson-completion fixes) being implemented **directly inside the god-object
ViewModel** rather than via the use-case layer proposed in W3. This is exactly the trajectory W2
Risk #1 warned against — the risk has materialized and intensified.

**W3 (clean architecture: `domain/` + use cases + repositories) status: still NOT started.** No
`domain/`/`application/`/`usecase` packages and no `*UseCase` classes exist. However, substantial
decomposition HAS already happened via the `feature/` layer (27 files: Runner/Coordinator/Calculator
with sealed `Result`/`Event` commands) and pure-logic classes in `data/` (`FlowerCalculator`,
`MixedReviewScheduler`, `CefrCalculator`, `LessonLadderCalculator`, `Normalization`). The current
pattern is best described as **feature-decomposed MVVM with a command/result pattern**, mid-migration
— not a pure god object, and not yet clean architecture.

**What was normalized in this pass:** stale line counts and `file:line` evidence ranges across
`architectural-analysis-report.md` + all `audit/*.md` (W1-A1, W1-A2, W1-A5, W1-A9, W2-A2, W2-A5,
W2-A9, W3, and this roadmap); the "Zero TODO/FIXME" claims in W1-A9/W2-A9. Qualitative risk
conclusions remain valid and, for the god-object risk, have strengthened.

---

## Executive Summary

GrammarMate suffers from accumulated architectural debt after 3+ years of iterative development. The audit revealed **121 architectural risks** across 9 areas, with **32 HIGH-risk issues** requiring immediate attention. The primary problems are god objects (TrainingViewModel: 2,579 lines), business logic embedded in UI callbacks, state mutation scattered across 8+ locations, and zero test coverage for critical business rules.

**We recommend a 5-phase incremental refactoring over 6-8 months** that prioritizes test coverage first, then extracts business logic to pure Kotlin, creates application orchestration layers, and finally consolidates infrastructure. This approach minimizes risk through small, safe PRs while preserving all user-visible behavior.

**Quick wins:** Remove dead routes, extract pure functions, add regression tests can be done immediately. **Foundation work:** Repository pattern and use case extraction require careful coordination but pay dividends in maintainability.

---

## Current Architecture Assessment

### Health Score: D+

| Dimension | Score | Notes |
|-----------|-------|-------|
| **Separation of Concerns** | F | Business logic in UI, god objects, circular dependencies |
| **Test Coverage** | F | ~10% overall, zero unit tests for business logic |
| **Code Organization** | D | Some pack-scoped stores, but dual storage patterns |
| **Maintainability** | D | Large files, state scatter, event-driven complexity |
| **Data Integrity** | C | AtomicFileWriter good, but no transaction support |
| **Performance** | B | Acceptable, but optimization blocked by architecture |

### Top 5 Problems (by Severity)

#### 1. TrainingViewModel God Object (2,579 lines)
**Severity:** CRITICAL  
**Impact:** Violates single responsibility, impossible to test, state mutated in 8+ locations  
**Location:** `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt:1-2579`  
**Blast Radius:** Entire training flow, all features depend on this class  
**Risk Level:** HIGH - Any change risks breaking unrelated features

#### 2. Business Logic in UI Callbacks
**Severity:** CRITICAL  
**Impact:** Cannot unit test business rules, UI decides session completion  
**Location:** `GrammarMateApp.kt:446-456` (verb drill coordination), `TrainingScreen.kt:221` (success rate calc)  
**Blast Radius:** All UI screens, business rule changes require Compose tests  
**Risk Level:** HIGH - Business logic locked behind UI framework

#### 3. State Mutation Scatter
**Severity:** HIGH  
**Impact:** `currentIndex` mutated in 8+ locations, race conditions, unpredictable state  
**Location:** TrainingViewModel, SessionRunner, multiple Compose callbacks  
**Blast Radius:** Session navigation, card progression, completion detection  
**Risk Level:** HIGH - State inconsistencies cause user-visible bugs

#### 4. Zero Test Coverage for Business Logic
**Severity:** CRITICAL  
**Impact:** No safety net for refactoring, regressions undetected  
**Location:** Entire domain logic (mastery calculation, SRS, card selection)  
**Blast Radius:** All business rules are untested  
**Risk Level:** CRITICAL - Refactoring without tests is dangerous

#### 5. Silent Parser Error Handling
**Severity:** HIGH  
**Impact:** Data loss without user notification, corrupted imports  
**Location:** `CsvParser.kt:25-27` (silent skip on malformed CSV)  
**Blast Radius:** All pack imports, lesson content, vocab drills  
**Risk Level:** HIGH - Users lose data silently

### Technical Debt Summary

| Category | Hours to Fix | Risk Level | Priority |
|----------|--------------|------------|----------|
| **God Objects** | 320-400 hrs | HIGH | P0 |
| **Business Logic in UI** | 160-240 hrs | HIGH | P0 |
| **State Mutation** | 120-200 hrs | HIGH | P0 |
| **Test Coverage** | 240-320 hrs | CRITICAL | P0 |
| **Dual Storage** | 80-120 hrs | MEDIUM | P1 |
| **Dead Code** | 40-60 hrs | LOW | P2 |
| **Parser Errors** | 40-80 hrs | HIGH | P1 |
| **Total** | **1,000-1,420 hrs** | | |

**Estimated Timeline:** 6-8 months with 1-2 developers  
**Team Size:** 2-3 developers (1 senior, 1-2 mid-level)  
**Risk Level:** MEDIUM (mitigated by test-first approach)

---

## Target Architecture Vision

### Before/After Diagrams

#### BEFORE (Current State)
```
┌─────────────────────────────────────────────────────────────────────┐
│                           Compose UI Layer                           │
│ GrammarMateApp.kt (1,998 lines)                                     │
│ - Business logic in callbacks                                       │
│ - Session coordination                                              │
│ - Dialog state management                                           │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Direct state mutation
                                    │ Business rules in UI
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Presentation Layer                           │
│ TrainingViewModel (2,579 lines) ──────┐                            │
│ - ALL app state (25+ fields)          │                            │
│ - Business rules (mastery, SRS)       │    VerbDrillViewModel      │
│ - State mutation (currentIndex × 8)   │    (1,035 lines)           │
│ - Direct store access                 │                            │
│ ┌─────────────────────────────────┐  │                            │
│ │ SessionRunner (1,416 lines)     │  │                            │
│ │ - Event callbacks               │  │                            │
│ └─────────────────────────────────┘  │                            │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Direct store access
                                    │ No repository abstraction
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Data Stores (Mixed Scope)                       │
│ Global Stores + Pack-Scoped Stores (dual pattern)                   │
│ - Direct YAML read/write                                             │
│ - No transaction support                                            │
└─────────────────────────────────────────────────────────────────────┘
```

**Problems:** God objects, state scatter, business logic in UI, no testability

#### AFTER (Target State)
```
┌─────────────────────────────────────────────────────────────────────┐
│                           Compose UI Layer                           │
│ Screens (TrainingScreen, VerbDrillScreen, etc.)                     │
│ - Pure Compose functions (stateless)                                │
│ - UI state only (no business logic)                                 │
│                                                                      │
│ ViewModels (thin orchestrators, ~200 lines each)                    │
│ - UI state management only                                          │
│ - Delegate to use cases                                             │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Use Case invocations
                                    │ Request/Response models
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Application Layer                            │
│ Use Cases (interactors)                                             │
│ - StartSessionUseCase                                               │
│ - SubmitAnswerUseCase                                               │
│ - ComposeDailyPracticeUseCase                                       │
│ - HandleBossBattleUseCase                                           │
│ - (15-20 use cases total)                                           │
│                                                                      │
│ Orchestration Only (no business rules)                              │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Repository interfaces
                                    │ Domain model queries
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Domain Layer (Pure Kotlin)                        │
│ Domain Models (immutable data classes)                              │
│ - Session, Card, Answer, Mastery, Progress                          │
│                                                                      │
│ Business Rules (pure functions, testable without Android)           │
│ - calculateNextReview(), isCardLearned()                            │
│ - shouldIncludeInDailyPractice(), composeSessionBlocks()            │
│ - determineBossTrigger(), calculateScoreChange()                    │
│ - (30-40 pure functions)                                            │
│                                                                      │
│ Domain Services (stateless operations)                              │
│ - MasteryCalculator, DailyPracticeComposer                          │
│ - SessionScheduler, BossBattleTrigger, CardSelector                 │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Repository interface contracts
                                    │ Data source abstraction
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       Infrastructure Layer                           │
│ Repository Implementations                                          │
│ - SessionRepository, MasteryRepository, ProgressRepository          │
│                                                                      │
│ Data Sources                                                        │
│ - PackScopedStore<T> (generic pack-isolated storage)                │
│ - AtomicFileWriter (temp → fsync → rename)                          │
│ - YAML parsers (CsvParser, YamlParser)                              │
└─────────────────────────────────────────────────────────────────────┘
```

**Improvements:** Pure Kotlin domain, repository pattern, thin ViewModels, testable

### Key Principles

1. **Behavioral Preservation (NON-NEGOTIABLE)**
   - DO NOT change any user-visible behavior
   - All existing features must work identically after refactoring
   - Use existing behavior as test oracles (regression tests first)

2. **Pure Kotlin Business Logic**
   - Extract all business rules to pure Kotlin functions
   - No Android dependencies in domain layer
   - Testable with JVM unit tests (no Android emulator needed)

3. **Separation of Concerns**
   - **Domain**: Business rules (no Android, no UI)
   - **Application**: Orchestration (use cases, repositories)
   - **Infrastructure**: Data access (YAML, file system)
   - **UI**: Compose screens and state management only

4. **Dependency Inversion**
   - Domain layer defines repository interfaces
   - Infrastructure layer implements them
   - UI depends on application layer, not infrastructure

5. **Test First, Refactor Second**
   - Add regression tests before changing code
   - Unit tests for business logic (pure Kotlin)
   - Integration tests for repositories (real YAML files)
   - UI clickable tests for critical user flows

6. **Incremental Migration**
   - Small PRs (max 500 lines changed)
   - No big bang rewrites
   - Each PR must pass all tests
   - Continuous deployment throughout migration

---

## Refactor Phases

### Phase 0: Safety Tests (2-3 weeks)

**Goal:** Add regression tests before touching code

**Quick Wins:**
- Remove test-only production code (can be done immediately)
- Add missing UI clickable tests for critical paths
- Document existing behavior as test oracles

**Effort:** 120-160 hours (3-4 weeks with 1-2 developers)

**Blockers:** None (tests only, no code changes)

**Deliverables:**
- 10+ new UI clickable tests covering:
  - Daily practice full flow
  - Boss battle full flow
  - Verb drill session card actions
  - Progress persistence
  - Navigation critical paths
- Test-only production code removed from main codebase
- Behavior documentation for all critical user flows
- Regression test suite passing 100%

**Risks:** LOW (tests only, no code changes)

**Success Criteria:**
- [ ] All existing UI tests passing
- [ ] 10+ new UI tests added and passing
- [ ] Test-only production code removed
- [ ] Behavior documentation complete
- [ ] Regression test suite runs in < 2 minutes

**Quick Win Tasks (First Week):**
1. Remove unused ELITE route (15 lines)
2. Remove test-only utilities from main codebase
3. Add parser error handling tests
4. Document current session completion behavior

---

### Phase 1: Low-Risk Extraction (4-6 weeks)

**Goal:** Extract business logic to pure Kotlin

**Quick Wins:**
- Extract pure functions from TrainingViewModel (immediate value)
- Create domain layer structure (can be done in parallel)
- Add unit tests for mastery calculation (high value, low risk)

**Effort:** 320-400 hours (6-8 weeks with 1-2 developers)

**Blockers:** None (pure extraction, tests first)

**Deliverables:**
- Domain layer structure created:
  - `domain/model/` (immutable data classes)
  - `domain/service/` (stateless business logic)
  - `domain/repository/` (interfaces only)
- 15+ pure functions extracted:
  - Mastery calculation (calculateNextReview, isCardLearned, calculateMasteryStep)
  - Progress aggregation (aggregatePackProgress, getLearnedItemCount)
  - Card selection (selectCardsForSession, applyDifficultyFilter)
  - Session scoring (calculateScoreChange, determineBossTrigger)
- 50+ unit tests passing (JVM, no Android dependencies)
- No behavioral changes detected by regression tests

**Risks:** LOW (pure extraction, tests first, no Android dependencies)

**Success Criteria:**
- [ ] Domain layer created with 15+ pure functions
- [ ] 50+ unit tests passing (JVM, no Android)
- [ ] All existing tests still passing
- [ ] No behavioral changes detected
- [ ] Unit test execution time < 10 seconds

**Quick Win Tasks (First 2 Weeks):**
1. Create domain layer package structure
2. Extract `calculateNextReview()` to pure Kotlin
3. Extract `isCardLearned()` to pure Kotlin
4. Add unit tests for mastery calculation
5. Extract `calculateScoreChange()` to pure Kotlin

**Incremental PR Strategy:**
- PR #1: Create domain layer structure (0 behavior change)
- PR #2: Extract mastery calculation (with tests)
- PR #3: Extract progress aggregation (with tests)
- PR #4: Extract card selection (with tests)
- PR #5: Extract session scoring (with tests)

---

### Phase 2: Medium-Risk Orchestration (6-8 weeks)

**Goal:** Create application layer, use cases, repository interfaces

**Quick Wins:**
- Create repository interfaces (pure abstraction, no implementation)
- Implement repository adapters wrapping existing stores
- Add integration tests for repositories

**Effort:** 400-480 hours (8-10 weeks with 1-2 developers)

**Blockers:** 
- Requires completion of Phase 1 (domain layer)
- Requires careful coordination (touches ViewModels)

**Deliverables:**
- Repository interfaces defined in domain:
  - `SessionRepository`
  - `MasteryRepository`
  - `ProgressRepository`
  - `DailyStatsRepository`
- Repository implementations:
  - 4+ repositories wrapping existing stores
  - Integration tests for each repository
  - No behavioral changes (wrapping pattern)
- Application layer with use cases:
  - `StartSessionUseCase`
  - `SubmitAnswerUseCase`
  - `UpdateMasteryUseCase`
  - `NavigateToNextCardUseCase`
  - `ComposeDailyPracticeUseCase`
  - `HandleBossBattleUseCase`
  - (10+ use cases total)
- ViewModel size reduced by 50%+:
  - TrainingViewModel: 2,579 → ~800 lines
  - VerbDrillViewModel: 1,035 → ~500 lines
- 30+ integration tests passing

**Risks:** MEDIUM (touches ViewModels, requires careful testing)

**Success Criteria:**
- [ ] 10+ use cases implemented
- [ ] Repository interfaces defined in domain
- [ ] ViewModel size reduced by 50%+
- [ ] 30+ integration tests passing
- [ ] All regression tests still passing
- [ ] Integration test execution time < 30 seconds

**Quick Win Tasks (First 2 Weeks):**
1. Define repository interfaces in domain
2. Implement `SessionRepository` wrapping existing stores
3. Add integration tests for `SessionRepository`
4. Create `StartSessionUseCase` (orchestration only)
5. Refactor TrainingViewModel to use `StartSessionUseCase`

**Incremental PR Strategy:**
- PR #1: Define repository interfaces (0 behavior change)
- PR #2: Implement SessionRepository adapter (with tests)
- PR #3: Implement MasteryRepository adapter (with tests)
- PR #4: Create StartSessionUseCase (with tests)
- PR #5: Refactor TrainingViewModel to use StartSessionUseCase
- PR #6: Implement SubmitAnswerUseCase (with tests)
- PR #7: Refactor TrainingViewModel to use SubmitAnswerUseCase
- (Continue for all use cases)

---

### Phase 3: Infrastructure Isolation (4-6 weeks)

**Goal:** Repository pattern, clean data layer, remove dual storage

**Quick Wins:**
- Create generic `PackScopedStore<T>` (reduces duplication)
- Implement migration scripts (can be tested independently)
- Remove legacy global stores (cleanup after migration)

**Effort:** 240-320 hours (5-7 weeks with 1 developer)

**Blockers:**
- Requires completion of Phase 2 (repository layer)
- Requires data migration (needs backups and rollback plans)

**Deliverables:**
- Single pack-scoped store implementation:
  - Generic `PackScopedStore<T>` replaces 6+ store implementations
  - AtomicFileWriter pattern (temp → fsync → rename)
  - Integration tests for generic store
- Migration scripts:
  - Global → Pack-scoped data migration
  - Tested on staging data
  - Rollback procedures documented
  - Backup/restore verification
- Dual storage pattern removed:
  - Legacy global stores deleted
  - All repositories use pack-scoped stores
  - No state duplication
- Special mode storage consolidated:
  - Boss, Elite, Story use pack-scoped stores
  - Remove state duplication across features
- 20+ integration tests passing

**Risks:** MEDIUM (data migration, requires backups)

**Success Criteria:**
- [ ] Single PackScopedStore implementation
- [ ] Migration scripts tested and documented
- [ ] Dual storage pattern removed
- [ ] 20+ integration tests passing
- [ ] All regression tests still passing
- [ ] Migration tested on staging data
- [ ] Rollback procedure tested

**Quick Win Tasks (First Week):**
1. Create generic `PackScopedStore<T>` interface
2. Implement `PackScopedStoreImpl` with AtomicFileWriter
3. Add integration tests for generic store
4. Document migration strategy

**Incremental PR Strategy:**
- PR #1: Create generic PackScopedStore (with tests)
- PR #2: Implement migration script (with tests)
- PR #3: Migrate ProgressRepository to use PackScopedStore
- PR #4: Migrate MasteryRepository to use PackScopedStore
- PR #5: Remove legacy global stores
- PR #6: Consolidate special mode storage

**Migration Safety:**
- Full backups before any migration
- Test migration on staging data first
- Run migration in canary deployment (1% of users)
- Monitor for data corruption errors
- Rollback plan documented and tested

---

### Phase 4: Cleanup (2-3 weeks)

**Goal:** Remove dead code, finalize architecture, polish

**Quick Wins:**
- Remove deprecated code (can be done immediately)
- Finalize god object decomposition (satisfying milestone)
- Update documentation (living documentation)

**Effort:** 120-160 hours (3-4 weeks with 1 developer)

**Blockers:** None (cleanup only, all tests passing)

**Deliverables:**
- Deprecated code removed:
  - Legacy store implementations
  - Dead navigation routes
  - Unused helper classes
  - Deprecated migration methods
- God object decomposition complete:
  - TrainingViewModel: 2,579 → ~200 lines
  - VerbDrillViewModel: 1,035 → ~300 lines
  - DailyPracticeCoordinator: deleted (logic in use cases)
  - ProgressTracker: deleted (logic in domain services)
- Documentation updated:
  - Architecture diagrams (current state: target state)
  - API documentation (use cases, repositories)
  - Migration guide (for future developers)
  - Onboarding guide (how to work with new architecture)
- Performance benchmarks:
  - App startup time: < 2 seconds
  - Session load time: < 500ms
  - Persistence time: < 100ms per operation
- Code quality metrics:
  - Max 300 lines per class
  - 80%+ test coverage
  - Zero test-only production code
  - No circular dependencies

**Risks:** LOW (cleanup only, all tests passing)

**Success Criteria:**
- [ ] No god objects (max 300 lines per class)
- [ ] Dead code removed
- [ ] Documentation updated
- [ ] Performance benchmarks met
- [ ] 80%+ test coverage achieved
- [ ] All regression tests passing

**Quick Win Tasks (First Week):**
1. Remove unused ELITE route (15 lines)
2. Remove deprecated migration methods
3. Delete legacy store implementations
4. Update architecture diagrams
5. Document new architecture patterns

---

## Non-Goals

### Out of Scope for This Migration

1. **New Features**
   - No new user-facing features
   - No UI redesigns
   - No new practice modes
   - **Rationale:** Focus on architectural health without scope creep

2. **Performance Optimization**
   - No algorithm changes
   - No data structure changes
   - Only cleanup-level optimizations (remove dead code)
   - **Rationale:** Architecture cleanup will naturally improve performance

3. **Platform Changes**
   - No iOS port
   - No web version
   - No backend API
   - **Rationale:** Platform independence is a benefit of clean architecture, but not a goal

4. **Library Upgrades**
   - No Kotlin version changes
   - No Compose BOM upgrades
   - Only critical security fixes
   - **Rationale:** Minimize variables during architectural refactoring

5. **Behavioral Changes**
   - NO changes to user-visible behavior
   - NO changes to SRS algorithm
   - NO changes to scoring logic
   - **Rationale:** This is a refactor, not a rewrite

---

## Risks & Mitigations

### High Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Data loss during migration** | CRITICAL | - Full backups before Phase 3<br>- Test migration on staging data<br>- Rollback procedures documented<br>- AtomicFileWriter prevents corruption |
| **Test coverage gaps** | HIGH | - Phase 0 adds regression tests<br>- Code review for test coverage<br>- Mutation testing for critical paths<br>- Manual QA after each phase |
| **Behavioral regressions** | HIGH | - Regression tests before refactoring<br>- A/B testing during migration<br>- Canary deployments<br>- Rapid rollback capability |
| **Breaking existing PRs** | MEDIUM | - Coordinate with team<br>- Migration branch strategy<br>- Update PR templates<br>- Communication plan |

### Medium Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Performance degradation** | MEDIUM | - Benchmark before/after<br>- Profile critical paths<br>- Optimize hot spots<br>- Monitor in production |
| **Incomplete migration** | MEDIUM | - Clear phase criteria<br>- Definition of done<br>- Architecture decision records<br>- Regular architecture reviews |
| **Learning curve** | LOW | - Pair programming<br>- Documentation<br>- Training sessions |

### Low Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Git conflicts** | LOW | - Small, frequent PRs<br>- Clear ownership<br>- Conflict resolution procedures |

---

## Success Metrics

### Code Quality Metrics

- Max 300 lines per class
- 80%+ test coverage
- Zero test-only production code
- No circular dependencies
- Cyclomatic complexity < 10 per method

### Architecture Metrics

- Clear layer separation (Domain → Application → Infrastructure → UI)
- Dependency inversion followed (Domain defines interfaces)
- Pure Kotlin domain layer (no Android dependencies)
- Repository pattern implemented (data access abstracted)

### Maintainability Metrics

- Onboarding time < 1 day for new developers
- PR review time < 30 minutes
- Bug fix time < 2 hours
- Feature addition time < 1 day for simple features

### Performance Metrics

- App startup time: < 2 seconds
- Session load time: < 500ms
- Persistence time: < 100ms per operation
- UI recomposition: < 16ms (60 FPS)

### Test Metrics

- Unit tests: 90%+ coverage of domain layer
- Integration tests: 70%+ coverage of repositories
- UI tests: All critical user flows covered
- Test execution time: Unit < 10s, Integration < 30s, UI < 2m

---

## Team Recommendations

### Required Skills

**Must-Have:**
- Kotlin (expert level)
- Android development (3+ years)
- Jetpack Compose (2+ years)
- Clean Architecture (familiar with concepts)
- Unit testing (JUnit, MockK)
- Git (branching, PR workflows)

**Nice-to-Have:**
- Domain-Driven Design experience
- Repository pattern implementation
- Dependency Injection (Hilt)
- Coroutines and Flow
- YAML parsing
- Atomic file operations

### Team Composition

**Option 1: 2 Developers (6-8 months)**
- 1 Senior Android Developer (leads architecture, 80% time)
- 1 Mid-Level Android Developer (implementation, 100% time)

**Option 2: 3 Developers (4-6 months)**
- 1 Senior Android Developer (leads architecture, 60% time)
- 2 Mid-Level Android Developers (implementation, 100% time)

**Recommended:** Option 2 for faster delivery, Option 1 for resource-constrained teams

### Development Practices

**Daily Standup:**
- What tests did you write yesterday?
- What business logic are you extracting today?
- What's blocking your progress?

**Code Review:**
- All PRs must be reviewed by senior developer
- Check test coverage (must increase, not decrease)
- Check for behavioral changes (regression tests must pass)
- Check for architectural violations (layer separation)

**Testing:**
- Write tests BEFORE implementation (TDD)
- Unit tests for all pure functions
- Integration tests for all repositories
- UI tests for critical user flows

**Documentation:**
- Update architecture diagrams after each phase
- Document all architectural decisions (ADRs)
- Keep README files up to date
- Comment complex business logic

---

## Next Steps

### Immediate Actions (This Week)

1. **Review this roadmap with team**
   - Discuss timeline and resource allocation
   - Identify quick wins to start immediately
   - Agree on risk mitigation strategies

2. **Set up infrastructure**
   - Create `architecture` branch for refactoring work
   - Set up continuous integration for test execution
   - Configure test coverage reporting

3. **Start Phase 0 quick wins**
   - Remove unused ELITE route (15 lines, 0 risk)
   - Remove test-only production code
   - Add parser error handling tests
   - Document existing behavior

4. **Schedule architecture review**
   - Weekly architecture reviews (30 minutes)
   - Monthly stakeholder updates (progress report)
   - Quarterly roadmap reassessment

### First Month Goals

- [ ] Complete Phase 0 (Safety Tests)
- [ ] Start Phase 1 (Domain Extraction)
- [ ] Extract first 5 pure functions
- [ ] Achieve 30% test coverage
- [ ] Remove all dead routes and deprecated code

### First Quarter Goals

- [ ] Complete Phase 0 and Phase 1
- [ ] Start Phase 2 (Application Layer)
- [ ] Extract first 3 use cases
- [ ] Achieve 50% test coverage
- [ ] Reduce TrainingViewModel by 30%

### First Half-Year Goals

- [ ] Complete Phase 0, 1, 2
- [ ] Start Phase 3 (Infrastructure)
- [ ] Complete data migration
- [ ] Achieve 70% test coverage
- [ ] Reduce TrainingViewModel by 60%

### Full-Year Goals

- [ ] Complete all 5 phases
- [ ] Achieve 80%+ test coverage
- [ ] No god objects (max 300 lines per class)
- [ ] Clean architecture implemented
- [ ] Documentation complete

---

## Appendices

### Appendix A: All 121 Risks Summary

**By Area:**
- W1-A1: UI/Navigation (9 risks)
  - 4 HIGH, 3 MEDIUM, 2 LOW
- W1-A2: Training Core (18 risks)
  - 9 HIGH, 6 MEDIUM, 3 LOW
- W1-A3: Progress (12 risks)
  - 5 HIGH, 4 MEDIUM, 3 LOW
- W1-A4: Daily Practice (15 risks)
  - 6 HIGH, 5 MEDIUM, 4 LOW
- W1-A5: Verb/Vocab Drill (12 risks)
  - 4 HIGH, 5 MEDIUM, 3 LOW
- W1-A6: Special Modes (12 risks)
  - 5 HIGH, 4 MEDIUM, 3 LOW
- W1-A7: Data Infrastructure (12 risks)
  - 4 HIGH, 6 MEDIUM, 2 LOW
- W1-A8: Tests (12 risks)
  - 8 HIGH, 3 MEDIUM, 1 LOW
- W1-A9: Dependencies/Dead Code (19 risks)
  - 8 HIGH, 6 MEDIUM, 5 LOW

**Total:** 121 risks (32 HIGH, 42 MEDIUM, 47 LOW)

**By Severity:**
- CRITICAL: 5 risks (require immediate attention)
- HIGH: 27 risks (address in Phase 1-2)
- MEDIUM: 42 risks (address in Phase 2-3)
- LOW: 47 risks (address in Phase 4)

### Appendix B: Evidence Index

**File references for all claims in this roadmap:**

- TrainingViewModel size: `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt:1-2579`
- VerbDrillViewModel size: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:1-1035`
- State mutation scatter: `TrainingViewModel.kt` (8+ locations mutate `currentIndex`)
- Business logic in UI: `GrammarMateApp.kt:446-456` (verb drill coordination)
- Silent parser errors: `CsvParser.kt:25-27` (silent skip on malformed CSV)
- God objects: Multiple files > 1000 lines (TrainingViewModel, VerbDrillViewModel, DailyPracticeCoordinator)

**Full evidence available in Wave 1 and Wave 2 audit documents.**

### Appendix C: Effort Estimation Details

**Phase 0: Safety Tests (120-160 hours)**
- Add 10+ UI tests: 80-100 hours
- Remove test-only code: 20-30 hours
- Document behavior: 20-30 hours

**Phase 1: Low-Risk Extraction (320-400 hours)**
- Create domain layer: 40-60 hours
- Extract 15+ pure functions: 160-200 hours
- Write 50+ unit tests: 80-120 hours
- Code review and fixes: 40-60 hours

**Phase 2: Medium-Risk Orchestration (400-480 hours)**
- Define repository interfaces: 40-60 hours
- Implement 4+ repositories: 80-120 hours
- Create 10+ use cases: 160-200 hours
- Refactor ViewModels: 80-120 hours
- Write 30+ integration tests: 40-60 hours

**Phase 3: Infrastructure Isolation (240-320 hours)**
- Create generic store: 60-80 hours
- Implement migration scripts: 80-120 hours
- Remove dual storage: 60-80 hours
- Consolidate special modes: 40-60 hours

**Phase 4: Cleanup (120-160 hours)**
- Remove deprecated code: 40-60 hours
- Finalize decomposition: 40-60 hours
- Update documentation: 20-30 hours
- Performance tuning: 20-30 hours

**Total: 1,200-1,620 hours (6-9 months with 1-2 developers)**

**Assumptions:**
- Developers are familiar with Kotlin and Android
- No major interruptions or context switching
- Code review process is efficient (< 24 hours turnaround)
- Tests are written before implementation (TDD)
- No new features added during refactoring

---

**Document Status:** Ready for Team Review  
**Next Review Date:** [TBD]  
**Owner:** [Lead Architect]  
**Approved By:** [TBD]

---

**This roadmap is a living document.** Update it as we learn more during the refactoring process. The goal is to arrive at a clean, testable architecture while preserving all user-visible behavior.
