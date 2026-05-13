# Refactoring Execution Plan — Phase 3-4
# Branch: fix/spec-discrepancies
# Created: 2026-05-12
# Updated: 2026-05-13 (Step 4.1 complete)

---

## INSTRUCTIONS FOR NEW SESSION

1. Read this file first. It is your external memory.
2. Find the first step with status `[ ]` (not done).
3. Execute that step.
4. After completing, run `assembleDebug` to verify build.
5. Update this file: change `[ ]` to `[x]`, add commit hash.
6. Commit the plan update together with code changes.
7. Move to next step.
8. NEVER skip verification. NEVER mark step done without successful build.

### Pre-requisite: Merge feature/daily-cursors
Before starting Phase 3, merge `feature/daily-cursors` into `fix/spec-discrepancies`:
```bash
git checkout fix/spec-discrepancies
git merge feature/daily-cursors
```

---

## Current State

- TrainingViewModel: ~1900 lines (reduced after data class restructuring)
- TrainingUiState restructured into 10 nested data classes (was 86 flat fields)
- 8 modules wired: AnswerValidator, WordBankGenerator, StreakManager, CardProvider, ProgressTracker, BossBattleRunner, AudioCoordinator, DailyPracticeCoordinator, SessionRunner
- 1 module deferred: FlowerProgressRenderer
- Build: PASSES (Phase 3 complete, Step 4.1 complete)
- 1 unmerged commit remains (earlier daily-cursors work), but Phase 3 is done

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` | ViewModel — extract FROM here |
| `app/src/main/java/com/alexpo/grammermate/ui/helpers/` | Modules dir — extract TO here |
| `docs/specification/08-training-viewmodel.md` | Method inventory (156 methods, 103 fields) |
| `docs/specification/arch-phase3-interfaces.md` | Phase 3 interface definitions (943 lines) |
| `docs/specification/arch-module-decomposition.md` | Original module proposal with diagrams |
| **THIS FILE** | Execution plan with tracking |

## Build Command

```bash
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

## Safety Rules

- Build after EACH module creation + wiring
- NEVER change GrammarMateApp.kt router behavior
- NEVER change data store file formats
- NEVER change CardSessionContract interface
- Modules do NOT call other modules — coordination through ViewModel only
- Preserve single-ViewModel pattern (Level B constraint)
- Commit after each successful step

---

## PHASE 3: Create Stateful Modules

### Step 3.1: ProgressTracker (~300 lines)

- [x] **Status: COMPLETE**
- **Commit:** `3ebcd37`
- **File:** `ui/helpers/ProgressTracker.kt`
- **Interface:** see `arch-phase3-interfaces.md` section 1
- **Wraps:** MasteryStore + ProgressStore
- **Extracts from TrainingViewModel:**
  - `recordCardShowForMastery()`
  - `saveProgress()` (reads 25+ fields from state via TrainingStateAccess)
  - `advanceCursor()`
  - `storeFirstSessionCardIds()`
  - `resetAllProgress()`
  - `markSubLessonCardsShown()`
  - `checkAndMarkLessonCompleted()`
  - `calculateCompletedSubLessons()` — DEDUPLICATE from both TrainingViewModel AND CardProvider
  - `resolveCardLessonId()`
- **Constructor:** `MasteryStore, ProgressStore, TrainingStateAccess`
- **Risk:** LOW
- **Note:** CardProvider still has private calculateCompletedSubLessons — minor dedup gap
- **Verification:**
  - [x] Module file created with interface
  - [x] Methods extracted from TrainingViewModel
  - [x] calculateCompletedSubLessons removed from CardProvider
  - [x] TrainingViewModel delegates to ProgressTracker
  - [x] Build passes
  - [x] Commit created

---

### Step 3.2: BossBattleRunner (~200 lines)

- [x] **Status: COMPLETE**
- **Commit:** `3ebcd37`
- **File:** `ui/helpers/BossBattleRunner.kt`
- **Interface:** see `arch-phase3-interfaces.md` section 2
- **Pure logic** — returns result objects, does NOT own bossCards
- **Extracts from TrainingViewModel:**
  - `startBoss()`, `startBossLesson()`, `startBossMega()`, `startBossElite()`
  - `finishBoss()`
  - `updateBossProgress()`
  - `resolveBossReward()`
  - `bossRewardMessage()`, `clearBossRewardMessage()`, `clearBossError()`
- **Reward thresholds:** 30%=Bronze, 60%=Silver, 90%=Gold
- **Does NOT hold TrainingStateAccess** — ViewModel applies results
- **Risk:** MEDIUM
- **Note:** clearBossError stays in ViewModel as trivial one-liner. nextCard() has inline boss progress (minor gap)
- **Verification:**
  - [x] Module file created with interface
  - [x] Methods extracted from TrainingViewModel
  - [x] Reward thresholds match original (30/60/90)
  - [x] TrainingViewModel delegates to BossBattleRunner
  - [x] Build passes
  - [x] Commit created

---

### Step 3.3: AudioCoordinator (~300 lines)

- [x] **Status: COMPLETE**
- **Commit:** `3ebcd37`
- **File:** `ui/helpers/AudioCoordinator.kt`
- **Interface:** see `arch-phase3-interfaces.md` section 5
- **Owns:** TtsEngine, AsrEngine, SoundPool, download Jobs
- **Needs:** CoroutineScope + Application context
- **Extracts from TrainingViewModel:**
  - All TTS methods: `onTtsSpeak()`, `stopTts()`, `setTtsSpeed()`, `startTtsDownload()`, `confirmTtsDownloadOnMetered()`, `dismissMeteredWarning()`, `dismissTtsDownloadDialog()`, `startTtsDownloadForLanguage()`, `checkTtsModel()`, `checkAllTtsModels()`, `startBackgroundTtsDownload()`
  - All ASR methods: `startOfflineRecognition()`, `stopAsr()`, `setUseOfflineAsr()`, `startAsrDownload()`, `confirmAsrDownloadOnMetered()`, `dismissAsrMeteredWarning()`, `checkAsrModel()`
  - Sound effects: `playSuccessTone()`/`playSuccessSound()` → merge to `playSuccessSound()`, same for error
- **Dedup:** playSuccessTone/playSuccessSound are identical — merge
- **Risk:** MEDIUM (lifecycle, metered network dialogs)
- **Note:** playSuccessTone/playSuccessSound deduplicated
- **Verification:**
  - [x] Module file created with interface
  - [x] All TTS/ASR methods extracted
  - [x] Duplicate sound methods merged
  - [x] TrainingViewModel delegates to AudioCoordinator
  - [x] Build passes
  - [x] Commit created

---

### Step 3.4: DailyPracticeCoordinator (~350 lines)

- [x] **Status: COMPLETE**
- **Commit:** `3ebcd37`
- **File:** `ui/helpers/DailyPracticeCoordinator.kt`
- **Interface:** see `arch-phase3-interfaces.md` section 4
- **Absorbs:** DailySessionHelper + DailySessionComposer + 18 VM daily methods
- **Owns:** prebuiltDailySession, lastDailyTasks, dailyPracticeAnsweredCounts, dailyCursorAtSessionStart
- **Extracts from TrainingViewModel:**
  - `startDailyPractice()`, `repeatDailyPractice()`
  - `advanceDailyTask()`, `advanceDailyBlock()`, `repeatDailyBlock()`
  - `cancelDailySession()`
  - `rateVocabCard()`, `persistDailyVerbProgress()`
  - `hasResumableDailySession()`, `recordDailyCardPracticed()`
  - `getDailyCurrentCards()`, `getDailyLessonTitle()`, `getDailySubLessonLabel()`, `getDailyProgress()`
- **Factory pattern** for VerbDrillStore/WordMasteryStore (currently created per-call)
- **Risk:** HIGH (cursor advancement, cancel rollback)
- **Note:** DailySessionComposer kept separate, used internally. Factory pattern for drill stores implemented
- **Verification:**
  - [x] Module file created with interface
  - [x] DailySessionHelper + DailySessionComposer absorbed or delegated
  - [x] All 18 daily methods extracted
  - [x] Factory pattern for drill stores
  - [x] TrainingViewModel delegates to DailyPracticeCoordinator
  - [x] Build passes
  - [x] Commit created

---

### Step 3.5: SessionRunner (~450 lines)

- [x] **Status: COMPLETE**
- **Commit:** `3ebcd37`
- **File:** `ui/helpers/SessionRunner.kt`
- **Interface:** see `arch-phase3-interfaces.md` section 3
- **THE LARGEST extraction** — 33 methods
- **Owns:** sessionCards, eliteCards, timerJob, activeStartMs
- **Needs:** CoroutineScope for timer
- **Extracts from TrainingViewModel:**
  - `submitAnswer()` (225 lines!) — after BossBattleRunner extraction, only drill/normal remain
  - `nextCard()`, `prevCard()`, `togglePause()`, `finishSession()`
  - `showAnswer()`, `startSession()`, `onInputChanged()`, `setInputMode()`
  - `selectWordFromBank()`, `removeLastSelectedWord()`
  - `onVoicePromptStarted()`, `resumeTimer()`, `pauseTimer()`
- **Risk:** HIGHEST
- **Note:** Callback pattern for cross-module coordination. 1024 lines including drill/elite modes
- **Verification:**
  - [x] Module file created with interface
  - [x] submitAnswer() fully extracted
  - [x] Timer management extracted
  - [x] TrainingViewModel delegates to SessionRunner
  - [x] Build passes
  - [x] Commit created

---

## PHASE 4: Thin Orchestrator

### Step 4.1: Group TrainingUiState into nested data classes

- [x] **Status: COMPLETE**
- **Commit:** `930b8ed`
- **Reference:** `arch-module-decomposition.md` Section 3
- **Created 10 nested data classes:** SessionState, BossState, EliteState, DrillState, FlowerDisplayState, AudioState, DailyState, DailyVerbState, VocabSprintState, UIHintsState
- **WARNING:** Changes ALL composable access patterns:
  - `state.bossActive` → `state.boss.active`
  - `state.currentIndex` → `state.session.currentIndex`
  - etc.
- **Must update ALL screen files** that reference TrainingUiState fields
- **Risk:** HIGH (massive find-replace across UI layer)
- **Note:** 86 flat fields restructured into 10 nested groups. All screen files and helpers updated.
- **Verification:**
  - [x] Nested data classes created in Models.kt
  - [x] TrainingUiState uses nested classes
  - [x] All screen files updated (GrammarMateApp, HomeScreen, TrainingScreen, DailyPracticeScreen, etc.)
  - [x] Build passes
  - [x] Commit created

---

### Step 4.2: Thin TrainingViewModel (~400 lines)

- [ ] **Status: NOT STARTED**
- **Commit:** —
- **Target:** ~400 lines
- **Holds:** module instances
- **Routes:** public methods to correct module
- **Manages:** currentScreen navigation state, pack/language/lesson selection (cross-cutting)
- **Pattern:**
  ```kotlin
  fun submitAnswer() = sessionRunner.submitAnswer(...)
  fun startDailyPractice(level: Int) = dailyCoordinator.startDailySession(...)
  fun startBossLesson() = bossRunner.startBoss(...)
  ```
- **Risk:** MEDIUM (mostly mechanical delegation)
- **Verification:**
  - [ ] TrainingViewModel under 500 lines
  - [ ] All methods delegate to modules
  - [ ] No business logic remains in ViewModel
  - [ ] Build passes
  - [ ] Commit created

---

### Step 4.3: Final verification

- [ ] **Status: NOT STARTED**
- **Commit:** —
- Full assembleDebug build
- Review GrammarMateApp.kt compiles and routes correctly
- Verify all 11 modules instantiated and wired
- Line count comparison: before/after
- Commit final state

---

## OPTIONAL (lower priority)

### VocabSprintRunner (~250 lines)

- [ ] **Status: NOT STARTED**
- **Commit:** —
- **File:** `ui/helpers/VocabSprintRunner.kt`
- **Interface:** see `arch-module-decomposition.md` section 2.11
- **9 methods**, depends on AnswerValidator + WordBankGenerator
- **Can be done at any point after Step 3.1-3.2**
- **Risk:** MEDIUM

### FlowerProgressRenderer retry

- [ ] **Status: NOT STARTED**
- **Commit:** —
- **Needs:** adapter from LessonTileInfo to FlowerVisual, or rewrite to return FlowerVisual
- **Can be done during Phase 4 or after**
- **Risk:** LOW

---

## Progress Summary

| Phase | Steps | Done | Remaining |
|-------|-------|------|-----------|
| Pre-req | Merge daily-cursors | 1/1 | 0 |
| Phase 3 | 3.1–3.5 | 5/5 | 0 |
| Phase 4 | 4.1–4.3 | 1/3 | 2 |
| Optional | VocabSprint, FlowerRenderer | 0/2 | 2 |
| **Total** | | **7/11** | **4** |
