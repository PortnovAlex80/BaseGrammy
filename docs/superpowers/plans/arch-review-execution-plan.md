# Architecture Review — Execution Plan

## Status: COMPLETE

## Context
Architecture review revealed 4 critical, 6 high, 5 medium issues.
TrainingViewModel decomposition (Phases 1-4) already complete (3400→1509 lines).
GrammarMateApp.kt decomposition complete (3797→799 lines).
This plan addresses remaining issues.

---

## Batch 1: P1 — State & Logic Fixes (3 agents, parallel)

### 1a. CardSessionStateMachine extraction
- **Files to read:**
  - `ui/helpers/SessionRunner.kt` (lines 231-284, 432-453, 493-501 — retry/hint logic)
  - `ui/helpers/DailyPracticeSessionProvider.kt` (lines 46-410 — own copy of state machine)
  - `ui/VerbDrillCardSessionProvider.kt` (lines 46-287 — own copy of state machine)
  - `ui/helpers/WordBankGenerator.kt` (to use instead of inline generation)
- **Create:** `ui/helpers/CardSessionStateMachine.kt`
  - Reusable Compose state holder with: incorrectAttempts, hintAnswer, showIncorrectFeedback, remainingAttempts, isPaused, voiceTriggerToken
  - Methods: onSubmit(), onInputChanged(), showAnswer(), reset()
  - Parameterize: maxAttempts, answerProvider lambda
- **Modify:** VerbDrillCardSessionProvider.kt — delegate to CardSessionStateMachine, remove ~120 lines inline logic, use WordBankGenerator instead of inline generation
- **Modify:** DailyPracticeSessionProvider.kt — same delegation, remove ~120 lines
- **Do NOT modify:** SessionRunner.kt (uses different StateFlow-based pattern, separate refactor)
- **Verify:** Both providers still implement CardSessionContract

### 1b. Store synchronization — WordMasteryStore + VerbDrillStore
- **Files to read:**
  - `data/WordMasteryStore.kt` (144 lines — find all instantiation points)
  - `data/VerbDrillStore.kt` (113 lines — find all instantiation points)
  - `ui/TrainingViewModel.kt` (where stores are created, ~lines 81-91, rebindWordMasteryStore)
  - `ui/VerbDrillViewModel.kt` (creates own instances)
  - `ui/VocabDrillViewModel.kt` (creates own instances)
  - `ui/helpers/DailyPracticeCoordinator.kt` (caches per-packId)
- **Create:** `data/StoreFactory.kt`
  - Factory that caches store instances per packId
  - `getWordMasteryStore(packId: String?): WordMasteryStore` — returns cached instance
  - `getVerbDrillStore(packId: String?): VerbDrillStore` — returns cached instance
- **Modify:** TrainingViewModel.kt — use StoreFactory instead of creating stores directly
- **Modify:** VerbDrillViewModel.kt — use StoreFactory
- **Modify:** VocabDrillViewModel.kt — use StoreFactory
- **Modify:** DailyPracticeCoordinator.kt — use StoreFactory instead of own cache
- **Key:** All consumers of the same packId must share the same store instance

### 1c. Store synchronization — BadSentenceStore + TtsEngine
- **Files to read:**
  - `data/BadSentenceStore.kt` (332 lines — in-memory cache, ensureLoaded pattern)
  - `data/TtsEngine.kt` (318 lines — native Sherpa-ONNX resources)
  - `ui/helpers/AudioCoordinator.kt` (owns TtsEngine instance)
  - `ui/VerbDrillViewModel.kt` (owns TtsEngine instance, no release() in onCleared)
  - `ui/VocabDrillViewModel.kt` (owns TtsEngine instance, no release() in onCleared)
- **Modify:** `data/StoreFactory.kt` (from 1b) — add BadSentenceStore caching
  - `getBadSentenceStore(packId: String?): BadSentenceStore`
- **Modify:** All BadSentenceStore consumers to use StoreFactory
- **Fix TtsEngine:**
  - VerbDrillViewModel and VocabDrillViewModel should NOT own TtsEngine instances
  - They should receive AudioCoordinator (or a TtsProvider interface) and use its TtsEngine
  - Add `release()` calls in VerbDrillViewModel.onCleared() and VocabDrillViewModel.onCleared() if they must own instances
- **Goal:** 3→1 TtsEngine instances, 3→1 BadSentenceStore instances per packId

---

## Batch 2: P2 — File Decomposition (4 agents, parallel)

### 2a. LessonStore decomposition
- **File to read:** `data/LessonStore.kt` (953 lines)
- **Create:** `data/PackImporter.kt` — extract ZIP import logic (importLessonPack, importFromUri, manifest handling, drill file copy)
- **Create:** `data/LanguageManager.kt` — extract language CRUD (selectLanguage, getLanguages, language file management)
- **Create:** `data/DrillFileManager.kt` — extract drill file queries (hasVerbDrill, hasVocabDrill, getVerbDrillFiles, etc.)
- **Modify:** `data/LessonStore.kt` — keep lesson CRUD, index management, default packs. Delegate to extracted classes.
- **Target:** Each file under 500 lines. LessonStore under 300.
- **Fix:** AtomicFileWriter violations in importFromUri (csvFile.outputStream()) and importVerbDrillFile (copyTo())

### 2b. DailyPracticeScreen — DailyInputControls decomposition
- **File to read:** `ui/DailyPracticeScreen.kt` (1420 lines, DailyInputControls at lines 552-945 = 394 lines)
- **Extract from DailyInputControls:**
  - Word bank UI sub-composable (~50 lines)
  - Report bottom sheet sub-composable (~85 lines)
  - Voice launcher + ASR status sub-composable (~40 lines)
- **Target:** DailyPracticeScreen.kt under 1000 lines

### 2c. VerbDrillScreen — DefaultVerbDrillInputControls decomposition
- **File to read:** `ui/VerbDrillScreen.kt` (1227 lines, DefaultVerbDrillInputControls at lines 299-723 = 425 lines)
- **Extract from DefaultVerbDrillInputControls:**
  - Word bank UI sub-composable
  - Report/reference sheet sub-composable
  - Voice launcher sub-composable
- **Target:** VerbDrillScreen.kt under 1000 lines

### 2d. BackupManager decomposition + AtomicFileWriter fix
- **File to read:** `data/BackupManager.kt` (847 lines)
- **Create:** `data/BackupFileCollector.kt` — file enumeration, size calculation
- **Create:** `data/BackupRestorer.kt` — restore logic, metadata handling
- **Modify:** `data/BackupManager.kt` — keep top-level API, delegate to extracted classes
- **Fix:** Replace `writeText()` at lines 558 and 794 with AtomicFileWriter
- **Target:** Each file under 500 lines

---

## Batch 3: P3 — Dead Code & Dedup (3 agents, parallel)

### 3a. Delete dead code + dedup calculateCompletedSubLessons
- **Delete:** `ui/helpers/FlowerProgressRenderer.kt` (141 lines, not imported anywhere)
- **File to read:**
  - `ui/helpers/CardProvider.kt` (private calculateCompletedSubLessons at lines 279-306)
  - `ui/helpers/ProgressTracker.kt` (calculateCompletedSubLessons at lines 121-149)
- **Fix:** Remove duplicate from CardProvider.kt, make CardProvider call ProgressTracker's version (or make it a shared utility)
- **Also:** Update TrainingCardSession.kt to import NavIconButton and TtsSpeakerButton from ui.components.SharedComponents instead of private copies

### 3b. WordBankGenerator dedup in providers
- **Files to read:**
  - `ui/helpers/WordBankGenerator.kt` (canonical implementation)
  - `ui/VerbDrillCardSessionProvider.kt` (inline word bank generation at lines 306-325)
  - `ui/helpers/DailyPracticeSessionProvider.kt` (inline word bank generation at lines 311-329)
- **Fix:** Both providers should use WordBankGenerator instead of inline generation
- **Note:** If Batch 1a (CardSessionStateMachine) already fixed this, skip

### 3c. Store interfaces — first batch
- **Create interfaces for highest-priority stores:**
  - `data/ILessonStore.kt` — interface for LessonStore (used by 4 consumers)
  - `data/IWordMasteryStore.kt` — interface for WordMasteryStore (sync issues)
  - `data/IVerbDrillStore.kt` — interface for VerbDrillStore (sync issues)
- **Modify:** LessonStore, WordMasteryStore, VerbDrillStore to implement their interfaces
- **Modify:** Consumers to depend on interfaces where practical (constructor injection)
- **Do NOT break existing code** — interfaces are additive

---

## Batch 4: P3 — Store Interfaces Continued (3 agents, parallel)

### 4a. Store interfaces — second batch
- **Create interfaces:**
  - `data/IProgressStore.kt`
  - `data/IMasteryStore.kt`
  - `data/IBackupManager.kt`
- **Modify:** Stores to implement interfaces

### 4b. Store interfaces — third batch
- **Create interfaces:**
  - `data/IBadSentenceStore.kt`
  - `data/IAppConfigStore.kt`
  - `data/IStreakStore.kt`
- **Modify:** Stores to implement interfaces

### 4c. TrainingViewModel — final extraction to under 1200 lines
- **File to read:** `ui/TrainingViewModel.kt` (currently ~1509 lines, target <1200)
- **Extract if still needed:**
  - Boss orchestration → BossOrchestrator helper (~130 lines)
  - Bad sentence management → BadSentenceManager helper (~80 lines)
  - Progress restoration → ProgressRestorer helper (~80 lines)
- **Note:** Batch 1b/1c may already reduce TrainingViewModel lines if store factory moves declaration logic
- **Target:** Under 1200 lines

---

## Verification (after each batch)
- `java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain assembleDebug`
- Check all files under line limits
- No duplicate composables

## Completion Criteria
- [ ] All P1 tasks done
- [ ] All P2 tasks done
- [ ] All P3 tasks done
- [ ] Build passes
- [ ] No files exceed line limits
- [ ] No store multi-instance synchronization bugs
- [ ] No duplicated composables
