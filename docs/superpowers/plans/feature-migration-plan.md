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

- TrainingViewModel: thin combiner/router (~200 lines)
- 6 per-feature StateFlows (audio, story, vocabSprint, daily, flowerDisplay, boss)
- 4 core fields in ViewModel _coreState (navigation, cardSession, elite, drill)
- 7 callback interfaces replaced with sealed result types
- Build: PASSES

## Problem Statement

Agents struggle because:
1. Files scattered across flat `data/`, `feature/`, `ui/screens/` — no locality
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
| `app/src/main/java/com/alexpo/grammermate/feature/` | Feature directories (training, boss, daily, progress, vocab) |
| `app/src/main/java/com/alexpo/grammermate/shared/` | Shared directories (audio, infra) |
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
- [x] Create `feature/training/`, `feature/boss/`, `feature/daily/`, `feature/progress/`, `feature/vocab/`, `shared/audio/`, `shared/`
- [x] Standard Kotlin package structure — no build.gradle changes needed

### Step 1.2: Move training feature files
- [x] Move SessionRunner.kt → feature/training/
- [x] Move CardProvider.kt → feature/training/
- [x] Move AnswerValidator.kt → feature/training/
- [x] Move WordBankGenerator.kt → feature/training/
- [x] Move StoryRunner.kt → feature/training/
- [x] Update ALL import statements across project
- [x] Build verify

### Step 1.3: Move boss feature files
- [x] Move BossOrchestrator.kt → feature/boss/
- [x] Move BossBattleRunner.kt → feature/boss/
- [x] Update imports
- [x] Build verify

### Step 1.4: Move daily feature files
- [x] Move DailyPracticeCoordinator.kt → feature/daily/
- [x] Move DailySessionComposer.kt → feature/daily/
- [x] Move DailyPracticeSessionProvider.kt → feature/daily/
- [x] Move DailySessionHelper.kt → feature/daily/
- [x] Update imports
- [x] Build verify

### Step 1.5: Move progress feature files
- [x] Move ProgressTracker.kt → feature/progress/
- [x] Move ProgressRestorer.kt → feature/progress/
- [x] Move FlowerRefresher.kt → feature/progress/
- [x] Move StreakManager.kt → feature/progress/
- [x] Move BadSentenceHelper.kt → feature/progress/
- [x] Move SettingsActionHandler.kt → shared/ (cross-cutting)
- [x] Update imports
- [x] Build verify

### Step 1.6: Move remaining files
- [x] Move VocabSprintRunner.kt → feature/vocab/
- [x] Move AudioCoordinator.kt → shared/audio/
- [x] Move CardSessionStateMachine.kt → feature/training/ (session state machine)
- [x] Update imports
- [x] Full build verify — PASS
- [x] Commit

---

## PHASE 2: AudioState Isolation (proof of concept)

### Step 2.1: Extract AudioState to its own StateFlow
- [x] Create `MutableStateFlow<AudioState>` in AudioCoordinator
- [x] Expose as `StateFlow<AudioState>` public
- [x] ViewModel combines: `combine(audioCoordinator.stateFlow, ...) { audio, ... -> ... }`
- [x] Remove audio fields from TrainingUiState (delegate to combined flow)
- [x] Build verify — PASS

### Step 2.2: Validate audio isolation
- [x] Verify AudioCoordinator has zero cross-dependencies
- [x] Verify Compose UI reads audio state correctly
- [x] Run regression check on UC-48..UC-50 (audio/hint UCs)
- [x] Commit

---

## PHASE 3: Per-Feature Result Types (replace callbacks)

### Step 3.1: Define result types
- [x] Create `BossResult` (BossCommand) sealed class in feature/boss/
- [x] Create `SessionEvent` sealed class in feature/training/
- [x] Create `VocabResult` + `VocabSoundResult` sealed classes in feature/vocab/
- [x] Create `ProgressResult` sealed class in feature/progress/
- [x] Create `BadSentenceResult` sealed class in feature/progress/
- [x] Create `StoryResult` sealed class in feature/training/
- [x] Create `SettingsResult` sealed class in shared/

### Step 3.2: Convert SessionRunner
- [x] Replace `SessionCallbacks` (12 methods) with injected function params + `List<SessionEvent>` return types
- [x] Build reducer in ViewModel: `handleSessionEvents()`
- [x] Delete SessionCallbacks interface
- [x] Build verify

### Step 3.3: Convert BossOrchestrator
- [x] Replace `BossCallbacks` with `List<BossCommand>` return types
- [x] Build reducer: `handleBossCommands()`
- [x] Delete BossCallbacks interface
- [x] Build verify

### Step 3.4: Convert DailyCoordinator
- [x] Already uses lambdas — no callback interface to convert

### Step 3.5: Convert remaining helpers
- [x] ProgressRestorer → ProgressResult (query params injected as lambdas)
- [x] VocabSprintRunner → VocabResult + VocabSoundResult
- [x] StoryRunner → StoryResult
- [x] BadSentenceHelper → BadSentenceResult
- [x] SettingsActionHandler → SettingsResult
- [x] Delete all 7 callback interfaces
- [x] Full build verify — PASS

---

## PHASE 4: StateFlow Decomposition (per-feature ownership)

### Step 4.1: Extract BossState StateFlow
- [x] BossOrchestrator owns `MutableStateFlow<BossState>`
- [x] ViewModel combines BossState flow
- [x] Build verify

### Step 4.2: Extract DailyState StateFlow
- [x] DailyPracticeCoordinator owns `MutableStateFlow<DailyPracticeState>`
- [x] Build verify

### Step 4.3: Extract StoryState, VocabSprintState, FlowerDisplayState
- [x] StoryRunner owns `MutableStateFlow<StoryState>`
- [x] VocabSprintRunner owns `MutableStateFlow<VocabSprintState>`
- [x] FlowerRefresher owns `MutableStateFlow<FlowerDisplayState>`
- [x] Build verify

### Step 4.4: ViewModel combine chain
- [x] 7-flow combine: core + audio + story + vocab + daily + flower + boss
- [x] Feature reset methods (resetSessionState no longer touches feature states)
- [x] Cross-feature commands (ResetBoss, ResetDailySession, ResetStory, ResetVocabSprint)
- [x] Build verify — PASS

---

## Progress Summary

| Phase | Steps | Done | Remaining |
|-------|-------|------|-----------|
| Phase 1 | File moves | 6/6 | 0 |
| Phase 2 | AudioState | 2/2 | 0 |
| Phase 3 | Result types | 5/5 | 0 |
| Phase 4 | StateFlow decomposition | 5/5 | 0 |
| **Total** | | **18/18** | **0** |
