# Feature-Based Migration Plan
# Branch: feature/arch-feature-migration (create from fix/spec-discrepancies)
# Created: 2026-05-13
# Source: Architect debate (3 architects consensus)

---

## INSTRUCTIONS FOR NEW SESSION

1. Read this file first. It is your external memory.
2. Find the first phase with status `[ ]` (not done).
3. Execute that phase.
4. After completing, run `assembleDebug` to verify build.
5. Update this file: change `[ ]` to `[x]`, add commit hash.
6. Commit the plan update together with code changes.
7. Move to next phase.
8. NEVER skip verification. NEVER mark phase done without successful build.

---

## Current State

- TrainingViewModel: ~1133 lines (reduced from ~3400, 67% reduction)
- 20 helpers in flat `ui/helpers/` directory
- TrainingUiState: single StateFlow, 10 nested data classes
- 7 callback interfaces (BossCallbacks, SessionCallbacks, etc.)
- Build: PASSES
- Regression: 199/208 ACs PASS

## Problem Statement

Agents struggle because:
1. Files scattered across flat `data/`, `ui/helpers/`, `ui/screens/` — no locality
2. Single `updateState {}` funnel — 106 mutation sites, any can touch any slice
3. 7 generic callback interfaces — behavioral coupling hidden in ViewModel wiring
4. Specs fragmented by layer, not by feature

## Target Architecture

```
feature/
  training/   — TrainingState, SessionRunner, CardProvider, AnswerValidator, WordBankGenerator
  boss/       — BossState, BossOrchestrator, BossBattleRunner
  daily/      — DailyState, DailyPracticeCoordinator, DailySessionComposer, DailyPracticeSessionProvider
  progress/   — ProgressState, ProgressTracker, FlowerRefresher, ProgressRestorer, StreakManager
  vocab/      — VocabState, VocabSprintRunner
shared/
  audio/      — AudioCoordinator, TTS, ASR
  infra/      — AtomicFileWriter, Backup, YamlListStore
  content/    — LessonStore, PackImporter, LanguageManager, Models.kt
```

### Principles (all 3 architects agreed)

1. Feature directories — primary organization axis
2. Per-feature internal MutableStateFlow — state isolation within features
3. Per-feature typed result callbacks — cross-feature coordination (replaces 7 generic interfaces)
4. Single combined StateFlow for Compose — pragmatic performance
5. Incremental migration: audio → boss → progress → daily → training

### ViewModel becomes thin combiner + reducer

- Owns only NavigationState (cross-cutting)
- combine() from feature StateFlows into TrainingUiState
- Single reduce() function replaces 28 callback overrides
- Target: ~200 lines

---

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` | ViewModel — thin during migration, router at end |
| `app/src/main/java/com/alexpo/grammermate/ui/helpers/` | Current flat helpers dir — migrate FROM here |
| `app/src/main/java/com/alexpo/grammermate/feature/` | Target feature dir — migrate TO here |
| `docs/superpowers/plans/refactoring-execution-plan.md` | Previous refactoring (COMPLETE) |

## Build Command

```bash
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

## Safety Rules

- Build after EACH phase
- NEVER change GrammarMateApp.kt router behavior
- NEVER change data store file formats
- NEVER change CardSessionContract interface
- Features do NOT call other features directly — coordination through ViewModel only
- Preserve single-ViewModel pattern (Level B constraint)
- Commit after each successful phase

---

## PHASE 1: Feature Directory Reorganization (file moves only)

### Step 1.1: Create directory structure
- [ ] Create `feature/training/`, `feature/boss/`, `feature/daily/`, `feature/progress/`, `feature/vocab/`, `shared/audio/`, `shared/infra/`, `shared/content/`
- [ ] Update build.gradle package source sets if needed (or use standard Kotlin package structure)

### Step 1.2: Move training feature files
- [ ] Move SessionRunner.kt → feature/training/
- [ ] Move CardProvider.kt → feature/training/
- [ ] Move AnswerValidator.kt → feature/training/
- [ ] Move WordBankGenerator.kt → feature/training/
- [ ] Move StoryRunner.kt → feature/training/
- [ ] Update ALL import statements across project
- [ ] Build verify

### Step 1.3: Move boss feature files
- [ ] Move BossOrchestrator.kt → feature/boss/
- [ ] Move BossBattleRunner.kt → feature/boss/
- [ ] Update imports
- [ ] Build verify

### Step 1.4: Move daily feature files
- [ ] Move DailyPracticeCoordinator.kt → feature/daily/
- [ ] Move DailySessionComposer.kt → feature/daily/
- [ ] Move DailyPracticeSessionProvider.kt → feature/daily/
- [ ] Move DailySessionHelper.kt → feature/daily/
- [ ] Update imports
- [ ] Build verify

### Step 1.5: Move progress feature files
- [ ] Move ProgressTracker.kt → feature/progress/
- [ ] Move ProgressRestorer.kt → feature/progress/
- [ ] Move FlowerRefresher.kt → feature/progress/
- [ ] Move StreakManager.kt → feature/progress/
- [ ] Move BadSentenceHelper.kt → feature/progress/
- [ ] Move SettingsActionHandler.kt → feature/shared/ (cross-cutting)
- [ ] Update imports
- [ ] Build verify

### Step 1.6: Move remaining files
- [ ] Move VocabSprintRunner.kt → feature/vocab/
- [ ] Move AudioCoordinator.kt → shared/audio/
- [ ] Move CardSessionStateMachine.kt → feature/training/ (session state machine)
- [ ] Update imports
- [ ] Full build verify
- [ ] Commit

---

## PHASE 2: AudioState Isolation (proof of concept)

### Step 2.1: Extract AudioState to its own StateFlow
- [ ] Create `MutableStateFlow<AudioState>` in AudioCoordinator
- [ ] Expose as `StateFlow<AudioState>` public
- [ ] ViewModel combines: `combine(audioCoordinator.stateFlow, ...) { audio, ... -> ... }`
- [ ] Remove audio fields from TrainingUiState (delegate to combined flow)
- [ ] Build verify

### Step 2.2: Validate audio isolation
- [ ] Verify AudioCoordinator has zero cross-dependencies
- [ ] Verify Compose UI reads audio state correctly
- [ ] Run regression check on UC-48..UC-50 (audio/hint UCs)
- [ ] Commit

---

## PHASE 3: Per-Feature Result Types (replace callbacks)

### Step 3.1: Define result types
- [ ] Create `BossResult` sealed class in feature/boss/
- [ ] Create `SessionResult` sealed class in feature/training/
- [ ] Create `DailyResult` sealed class in feature/daily/
- [ ] Create `ProgressResult` sealed class in feature/progress/

### Step 3.2: Convert SessionRunner
- [ ] Replace `SessionCallbacks` (12 methods) with `emit: (List<SessionResult>) -> Unit`
- [ ] Build reducer in ViewModel for SessionResult
- [ ] Delete SessionCallbacks interface
- [ ] Build verify
- [ ] Run regression on UC-01..UC-06

### Step 3.3: Convert BossOrchestrator
- [ ] Replace `BossCallbacks` with `emit: (List<BossResult>) -> Unit`
- [ ] Build reducer for BossResult
- [ ] Delete BossCallbacks interface
- [ ] Build verify
- [ ] Run regression on UC-17..UC-20

### Step 3.4: Convert DailyCoordinator
- [ ] Replace daily callbacks with `emit: (List<DailyResult>) -> Unit`
- [ ] Build reducer for DailyResult
- [ ] Build verify
- [ ] Run regression on UC-21..UC-25

### Step 3.5: Convert remaining helpers
- [ ] ProgressTracker → ProgressResult
- [ ] VocabSprintRunner → VocabResult
- [ ] FlowerRefresher → ProgressResult
- [ ] Delete all remaining callback interfaces
- [ ] Full build verify
- [ ] Full regression check
- [ ] Commit

---

## PHASE 4: StateFlow Decomposition (per-feature ownership)

### Step 4.1: Extract BossState StateFlow
- [ ] BossOrchestrator owns `MutableStateFlow<BossState>`
- [ ] ViewModel combines BossState flow
- [ ] Build verify

### Step 4.2: Extract DailyState StateFlow
- [ ] DailyCoordinator owns `MutableStateFlow<DailyState>`
- [ ] Build verify

### Step 4.3: Extract ProgressState StateFlow
- [ ] ProgressTracker owns `MutableStateFlow<ProgressState>`
- [ ] Build verify

### Step 4.4: Extract TrainingState StateFlow
- [ ] SessionRunner owns `MutableStateFlow<TrainingState>`
- [ ] This is the LARGEST extraction — elite/drill modes stay here
- [ ] Build verify

### Step 4.5: Collapse ViewModel to router
- [ ] ViewModel = combine() + reduce() + NavigationState
- [ ] Target: ~200 lines
- [ ] Full regression check
- [ ] Commit

---

## Progress Summary

| Phase | Steps | Done | Remaining |
|-------|-------|------|-----------|
| Phase 1 | File moves | 0/6 | 6 |
| Phase 2 | AudioState | 0/2 | 2 |
| Phase 3 | Result types | 0/5 | 5 |
| Phase 4 | StateFlow decomposition | 0/5 | 5 |
| **Total** | | **0/18** | **18** |
