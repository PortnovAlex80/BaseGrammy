# Wave 2 Agent A3: Progress Architecture Risk Analysis

**Generated:** 2026-05-22
**Scope:** Progress Tracking Architecture Risk Classification
**Status:** RISK ANALYSIS - NO CODE CHANGES

---

## Executive Summary

**Total Risks Identified:** 12
- HIGH: 4
- MEDIUM: 6
- LOW: 2

**Critical Risk Areas:**
1. ProgressTracker complexity (560 lines, multiple concerns)
2. Dual storage pattern (legacy + pack-scoped)
3. WORD_BANK mode ambiguity
4. No incremental updates (performance/scalability)

**Architectural Debt:** Significant technical debt in progress tracking layer requires systematic refactoring.

---

## Risk Classification

### RISK-1: ProgressTracker God Object

**Title:** ProgressTracker violates Single Responsibility Principle

**Current behavior:**
- ProgressTracker.kt is 560 lines handling mastery tracking, cursor management, lesson completion, progress persistence, and reset operations
- Acts as facade/wrapper around 4 different stores (MasteryStore, ProgressStore, PackLessonProgressStore, PackDailyCursorStore)
- Contains business logic for mode filtering (WORD_BANK, boss battles), SRS coordination, and lesson completion detection

**Why this is a problem:**
- Violates Single Responsibility Principle - handles too many concerns
- Difficult to test due to multiple dependencies and side effects
- High blast radius - any change affects multiple subsystems
- Cognitive overload for developers - must understand entire 560-line file to make safe changes

**Blast radius:**
- Mastery tracking (recordCardShowForMastery, markSubLessonCardsShown)
- Cursor advancement (advanceCursor, storeFirstSessionCardIds)
- Lesson completion (checkAndMarkLessonCompleted, calculateCompletedSubLessons)
- Progress persistence (saveProgress with dual storage logic)
- Reset operations (resetStores)

**Evidence:**
```kotlin
// ProgressTracker.kt:25-42
/**
 * Single source of truth for all mastery tracking and progress persistence.
 * Wrapper around MasteryStore + ProgressStore + PackLessonProgressStore + PackDailyCursorStore.
 * Does NOT directly update TrainingUiState.
 */
class ProgressTracker(
    private val masteryStore: MasteryStore,
    private val progressStore: ProgressStore,
    private val lessonStore: LessonStore,
    private val packDailyCursorStore: PackDailyCursorStore,
    private val packLessonProgressStore: PackLessonProgressStore
) {
    // 560 lines of mixed concerns
}
```

**Proposed direction:**
- Split into focused modules:
  - MasteryTracker (card shows, SRS)
  - LessonProgressTracker (lesson completion, sub-lessons)
  - CursorManager (daily cursor advancement)
  - ProgressPersistenceCoordinator (save/load orchestration)
- Each module should have < 200 lines
- Clear interfaces between modules

**Risk level:** HIGH

**Must-have tests before refactor:**
1. Integration test for full card show → mastery update flow
2. Integration test for lesson completion detection
3. Integration test for cursor advancement across lesson boundaries
4. Integration test for save/load with dual storage
5. Regression test for WORD_BANK mode not counting toward mastery

---

### RISK-2: WORD_BANK Mode Semantic Ambiguity

**Title:** WORD_BANK mode has confusing "tracked but not counted" semantics

**Current behavior:**
- WORD_BANK mode cards are tracked in `shownCardIds` but NOT counted toward mastery (`uniqueCardShows`/`totalCardShows`)
- Two separate methods: `recordCardShowForMastery()` skips WORD_BANK, `markSubLessonCardsShown()` only processes WORD_BANK
- Cards marked in `shownCardIds` for progress tracking but excluded from mastery metrics

**Why this is a problem:**
- Confusing semantics - "shown" doesn't mean "counted for mastery"
- Two separate code paths for different modes - easy to introduce bugs
- Unclear why WORD_BANK shows are tracked at all if they don't count
- Business rationale not documented in code

**Blast radius:**
- MasteryStore.recordCardShow() - increments uniqueCardShows/totalCardShows
- MasteryStore.markCardsShownForProgress() - only updates shownCardIds
- ProgressTracker.recordCardShowForMastery() - skips WORD_BANK (line 69-72)
- ProgressTracker.markSubLessonCardsShown() - only processes WORD_BANK (line 92)
- FlowerCalculator.calculate() - uses uniqueCardShows for flower growth
- Sub-lesson completion detection - uses shownCardIds (line 146)

**Evidence:**
```kotlin
// ProgressTracker.kt:67-72
// Word Bank mode: does NOT count for mastery (flower growth)
// Only voice and keyboard input count for skill formation
if (inputMode == InputMode.WORD_BANK) {
    Log.d(logTag, "Skipping card show record for Word Bank mode - does not count for mastery")
    return
}

// ProgressTracker.kt:91-92
if (inputMode != InputMode.WORD_BANK || cards.isEmpty()) return
// Only processes WORD_BANK mode
```

**Proposed direction:**
1. Document business rationale: Why track WORD_BANK shows if they don't count?
2. If tracking is needed for sub-lesson completion: Make this explicit in method names
3. If tracking is not needed: Remove WORD_BANK from markSubLessonCardsShown entirely
4. Consider unified method with explicit `countsForMastery: Boolean` parameter
5. Add integration test verifying WORD_BANK doesn't grow flowers

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Test that WORD_BANK cards don't increment uniqueCardShows/totalCardShows
2. Test that WORD_BANK cards ARE added to shownCardIds
3. Test that WORD_BANK cards don't grow flowers
4. Test that WORD_BANK cards count toward sub-lesson completion
5. Test that VOICE/KEYBOARD cards DO count toward mastery

---

### RISK-3: Dual Storage Pattern Complexity

**Title:** Lesson progress split between legacy ProgressStore and new PackLessonProgressStore

**Current behavior:**
- Lesson progress fields (currentIndex, correctCount, incorrectCount, etc.) saved to PackLessonProgressStore
- Global fields (mode, elite, daily, etc.) remain in ProgressStore
- ProgressTracker.saveProgress() orchestrates dual writes (lines 260-334)
- Legacy fields in TrainingProgress set to 0/null as placeholders (lines 306-310)

**Why this is a problem:**
- Confusing save logic - must remember which fields go where
- Potential for data inconsistency if one write succeeds and other fails
- No transactional guarantee across two stores
- Legacy fields clutter codebase with dead data
- Migration complexity - users may have data in both locations

**Blast radius:**
- ProgressTracker.saveProgress() - dual write orchestration
- ProgressStore - contains legacy lesson progress fields
- PackLessonProgressStore - contains new lesson progress
- TrainingViewModel - reads from both stores via ProgressTracker
- Migration logic in ProgressStore.migrateGlobalLessonProgressToPackScoped()

**Evidence:**
```kotlin
// ProgressTracker.kt:260-298
/**
 * Phase 5, Wave 3.1: Lesson progress fields are saved to PackLessonProgressStore
 * instead of ProgressStore. Other fields (mode, elite, daily, etc.) remain in
 * the global ProgressStore.
 */
fun saveProgress(state: TrainingUiState, forceBackup: Boolean, normalizedEliteSpeeds: List<Double>): Boolean {
    // Save lesson progress to pack-scoped store (Phase 5, Wave 3.1)
    val updatedPackProgress = packProgress.copy(
        lessonProgress = packProgress.lessonProgress + (selectedLessonId to lessonProgress)
    )
    packLessonProgressStore.savePackProgress(updatedPackProgress)

    // Save other progress fields to global store (NOT lesson progress)
    progressStore.save(
        TrainingProgress(
            currentIndex = 0,  // Legacy fields - no longer used
            correctCount = 0,  // Legacy fields - no longer used
            // ...
        )
    )
}
```

**Proposed direction:**
1. Complete migration: Remove legacy lesson progress fields from TrainingProgress
2. Split TrainingProgress into focused data classes:
   - GlobalSettings (mode, elite, daily)
   - PerPackLessonProgress (already exists)
3. Add migration verification: Check for orphaned data in legacy fields
4. Consider atomic write: If dual writes needed, use transaction pattern

**Risk level:** HIGH

**Must-have tests before refactor:**
1. Integration test for dual write consistency
2. Migration test: Verify data moves from legacy to new store correctly
3. Test for orphaned data detection
4. Test that legacy fields can be safely removed
5. Regression test for cross-pack contamination (TASK-081)

---

### RISK-4: Business Logic Scattered Across ProgressTracker and Stores

**Title:** Mastery calculation logic embedded in stores, not isolated

**Current behavior:**
- SRS interval calculation in SpacedRepetitionConfig (pure logic)
- But SRS step advancement logic in MasteryStore.recordCardShow() (lines 172-176)
- Mastery threshold constants scattered: SpacedRepetitionConfig (150), TrainingConfig (3, 15)
- Flower state calculation in FlowerCalculator (pure logic)
- Mode filtering (WORD_BANK, boss) in ProgressTracker (business logic)

**Why this is a problem:**
- Business rules scattered across stores, ProgressTracker, and config classes
- Difficult to find all rules affecting mastery/flower state
- Stores should be persistence-only, not contain business logic
- Hard to test business rules in isolation

**Blast radius:**
- MasteryStore.recordCardShow() - contains SRS step advancement logic
- ProgressTracker - contains mode filtering logic
- SpacedRepetitionConfig - contains interval calculation
- FlowerCalculator - contains flower state logic
- TrainingConfig - contains threshold constants

**Evidence:**
```kotlin
// MasteryStore.kt:172-176
// Business logic embedded in store
val wasOnTime = SpacedRepetitionConfig.wasRepetitionOnTime(
    daysSinceLastShow = daysSince,
    intervalDays = currentInterval
)
val newStep = if (wasOnTime) {
    SpacedRepetitionConfig.nextIntervalStep(intervalStepIndex)
} else {
    intervalStepIndex  // Stay on same step if late
}

// ProgressTracker.kt:61-72
// Business logic in ProgressTracker
if (bossActive) return  // Boss battles don't count
if (inputMode == InputMode.WORD_BANK) return  // Word bank doesn't count
```

**Proposed direction:**
1. Extract business logic into pure Kotlin functions in feature/progress/domain/
2. Create MasteryEngine or ProgressService for business logic coordination
3. Stores should only serialize/deserialize data
4. ProgressTracker should delegate to domain logic, not contain it
5. Consolidate threshold constants in single config class

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Unit tests for SRS step advancement logic
2. Unit tests for mode filtering rules
3. Unit tests for mastery threshold calculations
4. Integration test for full card show → mastery update flow
5. Regression test for all flower states

---

### RISK-5: No Incremental Updates - Full File Rewrite on Every Save

**Title:** YAML files completely rewritten on every save, no incremental updates

**Current behavior:**
- MasteryStore.save() overwrites entire mastery.yaml file (lines 136-145)
- ProgressStore.save() overwrites entire progress.yaml file (lines 102-145)
- PackLessonProgressStore.savePackProgress() overwrites entire lesson_progress_{packId}.yaml
- All stores load full YAML file on startup, modify in-memory cache, write full cache back

**Why this is a problem:**
- Poor scalability as lesson count grows
- Slow I/O for large datasets
- Potential data corruption if write fails mid-file
- Cannot update single lesson without rewriting all lessons
- No append-only logging for audit trail

**Blast radius:**
- MasteryStore.persistToFile() - writes full mastery map
- ProgressStore.persistToFile() - writes full progress struct
- PackLessonProgressStore.persistToFile() - writes full pack progress
- All save operations throughout codebase

**Evidence:**
```kotlin
// MasteryStore.kt:311-338
private fun persistToFile() {
    val yamlString = yaml.dumpAsMap(
        mapOf(
            "schemaVersion" to SCHEMA_VERSION,
            "languages" to cache
        )
    )
    AtomicFileWriter.writeAtomically(file, yamlString)
}
```

**Proposed direction:**
1. Short-term: Optimize YAML serialization (only if performance becomes issue)
2. Long-term: Consider SQLite database for better scalability
3. Alternative: Incremental file format (append-only log + periodic compaction)
4. Add performance metrics before/after optimization

**Risk level:** LOW (current performance acceptable, but scalability concern)

**Must-have tests before refactor:**
1. Performance benchmark for save operations with 100+ lessons
2. Performance benchmark for load operations with 100+ lessons
3. Integration test for data integrity after failed write
4. Migration test from YAML to SQLite (if chosen)

---

### RISK-6: Ambiguous Mastery Thresholds - "Learned" vs "Mastered"

**Title:** LEARNED_THRESHOLD = 3 is unclear and potentially dead code

**Current behavior:**
- LEARNED_THRESHOLD = 3 in TrainingConfig.kt:13
- MASTERY_THRESHOLD = 150 in SpacedRepetitionConfig.kt:31
- Comment says LEARNED_THRESHOLD is "for lesson completion detection"
- But lesson completion uses BOSS_UNLOCK_SUB_LESSONS = 15
- Unclear what LEARNED_THRESHOLD = 3 actually means

**Why this is a problem:**
- Ambiguous terminology - "learned" vs "mastered" vs "completed"
- LEARNED_THRESHOLD may be dead code
- Unclear business rule - what does 3 represent?
- Potential for confusion when modifying mastery logic

**Blast radius:**
- TrainingConfig.LEARNED_THRESHOLD
- SpacedRepetitionConfig.MASTERY_THRESHOLD
- TrainingConfig.BOSS_UNLOCK_SUB_LESSONS
- Any code referencing these thresholds

**Evidence:**
```kotlin
// TrainingConfig.kt:13
const val LEARNED_THRESHOLD = 3

// SpacedRepetitionConfig.kt:31
const val MASTERY_THRESHOLD = 150  // Unique card shows for 100% flower

// TrainingConfig.kt:8
const val BOSS_UNLOCK_SUB_LESSONS = 15  // Sub-lessons for boss unlock
```

**Proposed direction:**
1. Search codebase for all usages of LEARNED_THRESHOLD
2. If unused: Remove dead code
3. If used: Clarify what "3" represents (3 SRS steps? 3 card shows? 3 lessons?)
4. Standardize terminology: "learned" vs "mastered" vs "completed"
5. Document thresholds in central location with business rationale

**Risk level:** LOW (unclear, but likely not critical)

**Must-have tests before refactor:**
1. Search codebase for LEARNED_THRESHOLD usages
2. Test what happens when 3 unique card shows reached
3. Test what happens when 150 unique card shows reached
4. Test lesson completion at 15 sub-lessons
5. Document expected behavior at each threshold

---

### RISK-7: Boss Battle Progress Exception Inconsistency

**Title:** ELITE boss saves progress but other bosses don't - inconsistent behavior

**Current behavior:**
- Boss battles skipped in recordCardShowForMastery() (line 62)
- Exception: ELITE boss saves progress (line 274-276)
- No documentation why ELITE boss is special
- Unclear if this is intentional or bug

**Why this is a problem:**
- Inconsistent behavior across boss types
- User confusion: Why does ELITE boss count but others don't?
- Potential bug: Should ELITE boss really count for mastery?
- No test coverage for this exception

**Blast radius:**
- ProgressTracker.saveProgress() - checks boss type (lines 274-276)
- Mastery tracking for boss battles
- User expectations for boss battle progression

**Evidence:**
```kotlin
// ProgressTracker.kt:61-62
// Boss battles do not count toward mastery/flower/SRS progress
if (bossActive) return

// ProgressTracker.kt:274-276
if (state.boss.bossActive && state.boss.bossType != com.alexpo.grammermate.data.BossType.ELITE) {
    return false  // Don't save progress for non-ELITE bosses
}
```

**Proposed direction:**
1. Clarify business requirement: Should ELITE boss count for mastery?
2. If yes: Document why ELITE boss is special
3. If no: Remove exception, treat all bosses equally
4. Add test coverage for all boss types
5. Consider adding boss-specific progress tracking (separate from lesson mastery)

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Test that regular boss battles don't count toward mastery
2. Test that ELITE boss DOES count toward mastery (if intentional)
3. Test that ELITE boss progress is saved correctly
4. Document business rationale for ELITE boss exception

---

### RISK-8: Direct Store Writes Scattered Across Progress

**Title:** Multiple direct write paths to stores, no single coordination point

**Current behavior:**
- ProgressTracker writes to 4 different stores
- TrainingViewModel also writes to stores via ProgressTracker
- Some stores written directly from other components (VerbDrillStore, WordMasteryStore)
- No transaction coordinator for multi-store writes
- No rollback if one write fails

**Why this is a problem:**
- Potential for data inconsistency if writes fail partially
- Difficult to track all write paths
- No atomicity across stores
- Difficult to debug data corruption issues

**Blast radius:**
- ProgressTracker.saveProgress() - writes to 2 stores
- TrainingViewModel.saveProgress() - calls ProgressTracker
- VerbDrillStore - written directly from VerbDrillScreen
- WordMasteryStore - written directly from VocabSprintRunner
- No transaction manager

**Evidence:**
```kotlin
// ProgressTracker.kt:269-334
fun saveProgress(...): Boolean {
    // Write to packLessonProgressStore
    packLessonProgressStore.savePackProgress(updatedPackProgress)

    // Write to progressStore
    progressStore.save(
        TrainingProgress(...)
    )
    // No transaction, no rollback if first succeeds and second fails
}
```

**Proposed direction:**
1. Create ProgressTransactionCoordinator for multi-store writes
2. Implement transaction pattern: Begin → Write All → Commit / Rollback
3. Add write-ahead logging for crash recovery
4. Consider event sourcing: Append events to log, replay to build state

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Integration test for multi-store write consistency
2. Test for rollback on partial write failure
3. Test for crash recovery during write
4. Test for data corruption detection

---

### RISK-9: Hidden Coupling Through Callbacks and Events

**Title:** Progress updates triggered through implicit callbacks, not explicit flows

**Current behavior:**
- TrainingViewModel calls ProgressTracker.recordCardShowForMastery() from multiple locations
- No clear entry point for progress updates
- Callbacks scattered: onCardAdvanced, onAnswerSubmit, onBlockComplete
- Difficult to trace when/why progress updates happen

**Why this is a problem:**
- Difficult to reason about progress update flow
- Easy to introduce bugs by missing callback
- No clear contract for when progress should update
- Hidden dependencies between UI events and progress updates

**Blast radius:**
- TrainingViewModel - multiple call sites for progress updates
- ProgressTracker - called from multiple locations
- SessionRunner - triggers progress updates
- DailyPracticeCoordinator - triggers progress updates

**Evidence:**
```kotlin
// TrainingViewModel.kt:1210-1217
private fun recordCardShowForMastery(...) {
    progressTracker.recordCardShowForMastery(
        card = currentCard,
        bossActive = bossActive,
        inputMode = inputMode,
        selectedLanguageId = selectedLanguageId,
        lessons = lessons,
        selectedLessonId = selectedLessonId
    )
}
// Called from: line 452, 1416, and potentially other locations
```

**Proposed direction:**
1. Create explicit ProgressUpdateUseCase or ProgressService
2. Define clear entry points: onCardShown, onSessionComplete, onLessonCompleted
3. Use event bus or explicit method calls instead of scattered callbacks
4. Document progress update flow in architecture diagram

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Integration test for all progress update triggers
2. Test that progress updates correctly on card navigation
3. Test that progress updates correctly on session completion
4. Test that progress updates correctly on lesson completion
5. Document all progress update triggers

---

### RISK-10: Migration Complexity - No Rollback Mechanism

**Title:** Migration from global to pack-scoped storage has no rollback

**Current behavior:**
- ProgressStore.migrateGlobalDailyCursorToPackScoped() (lines 151-206)
- ProgressStore.migrateGlobalLessonProgressToPackScoped() (lines 208-307)
- Migration checks for existing data before overwriting (lines 160-170, 243-251)
- No rollback if migration fails mid-way
- No backup created before migration

**Why this is a problem:**
- Potential data loss if migration fails
- No way to undo migration if bugs found
- User data at risk during upgrade
- Difficult to test migration edge cases

**Blast radius:**
- All user progress data
- Daily cursor data
- Lesson progress data
- App upgrade process

**Evidence:**
```kotlin
// ProgressStore.kt:151-206
fun migrateGlobalDailyCursorToPackScoped(...): Boolean {
    val progress = load()
    if (progress.dailyCursor.currentLessonIndex == 0 &&
        progress.dailyCursor.sentenceOffset == 0) {
        return false  // No data to migrate
    }
    // Migration happens here, no rollback
}
```

**Proposed direction:**
1. Create automatic backup before migration
2. Implement rollback mechanism if migration fails
3. Add migration status flag (in-progress, completed, failed)
4. Add migration verification: Check data integrity after migration
5. Test migration with various data states

**Risk level:** HIGH

**Must-have tests before refactor:**
1. Migration test with empty global data
2. Migration test with full global data
3. Migration test with corrupted global data
4. Rollback test if migration fails
5. Integration test for app upgrade path

---

### RISK-11: SRS Complexity Spread Across Multiple Files

**Title:** Spaced Repetition System logic scattered across config, store, and calculator

**Current behavior:**
- SRS interval calculation in SpacedRepetitionConfig (pure math)
- SRS step advancement in MasteryStore.recordCardShow()
- SRS health calculation in SpacedRepetitionConfig
- Flower state calculation in FlowerCalculator
- No single SRS engine or service

**Why this is a problem:**
- Difficult to verify SRS correctness
- Hard to modify SRS algorithm
- No clear ownership of SRS logic
- Scientific documentation scattered

**Blast radius:**
- SpacedRepetitionConfig - interval calculation, health calculation
- MasteryStore - SRS step advancement
- FlowerCalculator - flower state based on SRS
- All mastery tracking logic

**Evidence:**
```kotlin
// MasteryStore.kt:164-176
// SRS step advancement logic embedded in store
val daysSince = calculateDaysSince(lastShowDateMs)
val currentInterval = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS[intervalStepIndex]
val wasOnTime = SpacedRepetitionConfig.wasRepetitionOnTime(...)
val newStep = if (wasOnTime) {
    SpacedRepetitionConfig.nextIntervalStep(intervalStepIndex)
} else {
    intervalStepIndex
}
```

**Proposed direction:**
1. Create SrsEngine or SpacedRepetitionService
2. Consolidate all SRS logic in one place
3. Keep stores as pure persistence layers
4. Add comprehensive unit tests for SRS algorithm
5. Document scientific sources in single location

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. Unit tests for SRS interval calculation
2. Unit tests for SRS step advancement
3. Unit tests for SRS health calculation
4. Integration test for full SRS flow
5. Regression test for all flower states

---

### RISK-12: Test-Only Workarounds Leaking into Production

**Title:** Test doubles and test harness patterns may influence production code

**Current behavior:**
- FakeMasteryStore in test-harness
- Test harness supports progress tracking simulation
- No evidence of test-only code in production (good)
- But unclear if production code is testable enough

**Why this is a problem:**
- If production code not testable, tests may be incomplete
- Risk of bugs in untestable code paths
- Difficult to add regression tests

**Blast radius:**
- All progress tracking logic
- Test coverage
- Regression prevention

**Evidence:**
```kotlin
// test-harness/FakeMasteryStore.kt
// Test double for MasteryStore - good pattern
// But need to verify production code is testable
```

**Proposed direction:**
1. Verify all progress tracking code is testable
2. Add integration tests for critical flows
3. Ensure test doubles match production interfaces
4. Add regression tests for edge cases

**Risk level:** LOW (current state is acceptable)

**Must-have tests before refactor:**
1. Audit test coverage for progress tracking
2. Add integration tests for all critical flows
3. Add regression tests for known bugs (TASK-080, TASK-081)

---

## Risk Summary Matrix

| Risk ID | Title | Level | Blast Radius | Complexity |
|---------|-------|-------|--------------|------------|
| RISK-1 | ProgressTracker God Object | HIGH | Entire progress system | High |
| RISK-2 | WORD_BANK Mode Ambiguity | MEDIUM | Mastery tracking, flower growth | Medium |
| RISK-3 | Dual Storage Pattern | HIGH | Lesson progress persistence | High |
| RISK-4 | Business Logic Scattered | MEDIUM | Mastery calculation, mode filtering | Medium |
| RISK-5 | No Incremental Updates | LOW | All save operations | Low |
| RISK-6 | Ambiguous Thresholds | LOW | Mastery constants | Low |
| RISK-7 | Boss Battle Inconsistency | MEDIUM | Boss battle progress | Low |
| RISK-8 | Direct Store Writes | MEDIUM | All progress persistence | Medium |
| RISK-9 | Hidden Coupling | MEDIUM | Progress update triggers | Medium |
| RISK-10 | No Migration Rollback | HIGH | All user data | High |
| RISK-11 | SRS Complexity | MEDIUM | SRS algorithm | Medium |
| RISK-12 | Test Coverage | LOW | Test quality | Low |

---

## Prioritized Refactoring Recommendations

### Phase 1: Critical Risks (Do First)
1. **RISK-10:** Add migration rollback mechanism - protects user data
2. **RISK-3:** Complete dual storage migration - removes technical debt
3. **RISK-1:** Split ProgressTracker into focused modules - improves maintainability

### Phase 2: High-Value Improvements
4. **RISK-2:** Clarify WORD_BANK semantics - reduces confusion
5. **RISK-4:** Extract business logic from stores - improves testability
6. **RISK-8:** Add transaction coordinator - improves data consistency

### Phase 3: Technical Debt
7. **RISK-9:** Explicit progress update flows - improves debugging
8. **RISK-11:** Consolidate SRS logic - improves maintainability
9. **RISK-7:** Clarify boss battle rules - improves consistency

### Phase 4: Performance & Scalability
10. **RISK-5:** Consider incremental updates or SQLite - future-proofing
11. **RISK-6:** Resolve ambiguous thresholds - code cleanup
12. **RISK-12:** Improve test coverage - quality assurance

---

## Testing Strategy Before Refactoring

### Critical Tests (Must Have Before Any Refactor)
1. **Integration test:** Full card show → mastery update → flower growth flow
2. **Integration test:** Lesson completion detection at 15 sub-lessons
3. **Integration test:** Daily cursor advancement across lesson boundaries
4. **Integration test:** Save/load with dual storage consistency
5. **Migration test:** Global to pack-scoped migration with rollback

### Regression Tests (Protect Against Bugs)
6. **Regression test:** WORD_BANK mode doesn't grow flowers
7. **Regression test:** Boss battles don't count toward mastery (except ELITE)
8. **Regression test:** Cross-pack contamination prevention (TASK-080, TASK-081)
9. **Regression test:** All flower states (SEED, SPROUT, BLOOM, WILTING, WILTED, GONE)

### Performance Tests (Validate Optimizations)
10. **Performance test:** Save/load with 100+ lessons
11. **Performance test:** YAML parsing overhead
12. **Performance test:** Incremental update vs full rewrite

---

## Migration Safety Checklist

Before starting any refactoring:

- [ ] All critical integration tests pass
- [ ] All regression tests pass
- [ ] Migration test passes with rollback verification
- [ ] Performance baseline established
- [ ] Backup mechanism in place
- [ ] Rollback plan documented
- [ ] Code coverage for affected modules > 80%
- [ ] Architecture review approved

---

**END OF RISK ANALYSIS**
