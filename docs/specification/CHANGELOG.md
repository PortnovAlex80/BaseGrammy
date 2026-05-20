# Specification Changelog

All changes to specification documents are tracked here. Each entry references the commit hash and version.

## [TASK-076: Verb Drill Session Persistence Lifecycle] - 2026-05-20

### Changed
- `docs/specification/scenario-07-verb-drill.md`: Updated Step 1 to include `refreshLastSessionContext()` on screen entry and VD-51 inline SessionCard flow. Updated Step 9 to reflect in-place batch loading via `replaceVerbDrillCards()` instead of navigation to selection screen. Updated Exit Navigation section with `persistSessionState()` call on all exit paths. Added discrepancy #5 documenting VD-50 dialog disabled / VD-51 inline SessionCard active.
- `docs/specification/22-use-case-registry.md`: Updated UC-74 from "Start Fresh / Resume dialog" to "inline SessionCard and session persistence". Added ACs for: `refreshLastSessionContext()` on screen entry, in-place "Ещё" batch loading, `persistSessionState()` on all exit paths, proper `lastSessionContext` null clearing, "Repeat" button replaying same cards.
- `docs/specification/23-screen-elements.md`: Added VD-51 (Inline SessionCard) element. Updated VD-40 (More button) behavior to call `replaceVerbDrillCards()` in-place. Updated VD-41 (Exit button) to document `persistSessionState()` call. Updated element count to 42 / total 339.
- `docs/specification/10-verb-drill.md`: No changes in this update (spec section 10.6.2 still documents VD-50 dialog -- discrepancy noted in scenario-07 discrepancy #5).

### Key Changes
- VD-50 modal dialog is disabled (`showStartFreshResumeDialog` never set to true)
- VD-51 inline SessionCard shown on VerbDrillSelectionScreen when `lastSessionContext != null`
- `persistSessionState()` called on all exit paths from GrammarMateApp
- "Ещё" button loads next batch in-place via `replaceVerbDrillCards()` without leaving TrainingScreen
- `refreshLastSessionContext()` called on every VerbDrillScreen entry via LaunchedEffect
- `lastSessionContext` properly cleared to null when no YAML file exists

## [Remove Mix Challenge Feature (DORMANT)] - 2026-05-20

### Changed
- `docs/specification/scenarios/click-test-mix-challenge.md`: Deleted (Mix Challenge has no UI entry point, feature is dormant).
- `docs/specification/22-use-case-registry.md`: Removed UC-54 (Mix Challenge tile not visible). Updated Domain 12 title to remove "Mix Challenge" reference.

### Key Changes
- Mix Challenge feature was never fully implemented (no UI entry point on HomeScreen)
- AppScreen.MIX_CHALLENGE enum value kept for backward compatibility
- Documentation cleanup to reflect current feature state

## [Verb Drill Start Fresh / Resume Dialog - Remove Staleness Limit] - 2026-05-20

### Changed
- `10-verb-drill.md`: Updated section 10.2.5 (VerbDrillLastSessionState) - removed staleness language, timestamp now used only for session age display. Updated section 10.6.2 (Start Fresh / Resume Dialog VD-50) - removed 24-hour staleness condition from dialog trigger, updated dialog layout to show session context (age, tense, group, progress, score), removed staleness condition from behavior table, updated "When to clear last session" section. Updated dialog text localization table to include tense/group/progress/score labels.
- `22-use-case-registry.md`: Updated UC-74 - removed "within 24 hours" from preconditions and AC1, removed AC6 (staleness), added AC8-AC12 for session context display (tense, group, progress, score labels).

### Key Changes
- Dialog now appears whenever a previous incomplete session exists (no time limit)
- Dialog displays full session context: tense filter, group filter, progress (e.g., "5/10 cards"), score (correct/incorrect counts)
- Labels show "All tenses" / "All groups" when filters are null
- Last session is never auto-cleared due to age (only on completion, Start Fresh, or pack switch)

## [Daily Practice Session Size and Verb Offset Fixes] - 2026-05-20

### Changed
- `09-daily-practice.md`: Updated section 9.6.8 to document that sessionSize from AppConfig now controls block sizes for all three blocks (TRANSLATE, VOCAB, VERBS), replacing the hardcoded CARDS_PER_BLOCK constant. Updated section 9.5.1 to describe cursor-based verb block advancement using verbOffset, which increments by sessionSize after each session and cycles at the pool end. Updated section 9.8.7 to add verbOffset to the DailyCursorState documentation and cursor advancement rules.
- `01-models-and-state.md`: Added verbOffset field to DailyCursorState documentation with description of its purpose and increment behavior.
- `22-use-case-registry.md`: Updated UC-21 AC2 to specify that sessionSize from AppConfig affects all three daily practice blocks uniformly. Updated UC-24 to add AC7 documenting verbOffset tracking through the verb drill pool.

### Key Changes
- sessionSize config parameter now applies to ALL daily practice modes (no coerceIn restrictions)
- Block 3 verb cards use cursor-based advancement via verbOffset (not everShownCardIds exclusion)
- verbOffset increments by sessionSize after session completion, cycles at pool end

## [Test Infrastructure Cleanup] - 2026-05-19

### Changed
- `build.gradle.kts`: Robolectric 4.13 already configured for unit tests
- `SessionRunner.kt`: Added auto-set of `currentCard` from `sessionCards` in `startSession()` for test compatibility
- `app/src/test/java/com/alexpo/grammermate/scenario/*`: Added `@RunWith(RobolectricTestRunner::class)` to all scenario tests to mock Android APIs (`SystemClock`, `Log`)
- `app/src/test/java/com/alexpo/grammermate/test-harness/`: Created in-memory fake implementations for all stores (no Mockito)

### Added
- `docs/TEST_INFRASTRUCTURE.md`: Comprehensive documentation of test infrastructure, including Windows Gradle wrapper workaround, Java location, test types (unit/scenario/click), and troubleshooting guide

### Test Status
- Total tests: 175
- Passing: 165
- Failing: 10 (AssertionError - test logic issues, not infrastructure)
- MixedSessionScenarioTest: 24/24 passing ✅
- MasteryProgressionScenarioTest: 30/34 passing ✅
- EliteModeScenarioTest: 18/19 passing ✅
- NewOnlySessionScenarioTest: 5/5 passing ✅

### Infrastructure Fixes
- Fixed "Method elapsedRealtime in android.os.SystemClock not mocked" by adding Robolectric runner
- Fixed "Method d in android.util.Log not mocked" by same fix
- Fixed Windows Gradle wrapper path: `java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain`
- Java location: `C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe`

## [TASK-070: Daily Practice Cursor Fix] - 2026-05-18

### Changed
- `09-daily-practice.md`: Updated section 9.2.1 to note `DailyPracticeSessionProvider` is dead code. Updated section 9.3.5 (Mastery Counting) to reflect actual counting path through `TrainingViewModel.submitAnswer()` instead of `DailyPracticeSessionProvider.onCardAdvanced`. Updated section 9.5.6 to note actual verb progress persistence path. Updated section 9.6.2 (Block Transition) to correct the card counting trace. Updated section 9.6.4 to document how `dailyPracticeAnsweredCounts` is populated. Updated section 9.8.7 (Cursor Invariant) to document soft reset (`resetState()` preserves cursor) vs full wipe (`resetAllDailyState()` wipes cursor), and added cursor survival guarantees for lesson selection, language change, and pack import. Updated DailyPracticeCoordinator API to include `resetState()` and `resetAllDailyState()` signatures.
- `scenario-06-daily-practice.md`: Updated Step 4 card counting trace from `onCardAdvanced` to `submitAnswer()` hook. Updated Step 10 provider reference. Added discrepancy #9 documenting the dead code finding and TASK-070 resolution. Added discrepancy #10 documenting the `resetState()` cursor wipe bug and Fix 3 resolution.
- `trace-index.md`: Added TASK-070 section with entries for `isDailySession()`, daily tracking block in `submitAnswer()`, `resetState()` (soft reset), and `resetAllDailyState()` (full wipe).
- `tasks/TASK-070-daily-cursor-stuck.md`: Added Fix 3 section documenting `resetState()` cursor preservation. Updated verification checklist with items 10-13 for cursor survival checks. Updated completion log with Fix 3 status.

## [Spec Update] - 2026-05-17

### Added
- `20-non-functional-requirements.md`: Added section 20.1.8 (Compose Rendering Performance) with state flow consumption requirements (PERF-01 through PERF-06), compose recomposition requirements (PERF-07 through PERF-12), performance budget table with current measurements vs targets, and architectural constraint note preserving single-ViewModel/single-state patterns.

## [Performance Tasks Completion] - 2026-05-17

### Changed
- Task files renamed to DONE- prefix: TASK-063, TASK-064, TASK-065, TASK-069
- `20-non-functional-requirements.md` §20.1.8: Updated PERF-01 through PERF-07 status from "Pending" to "Done"
- `tasks/README.md`: Updated tasks 063, 064, 065, 069 status to DONE

### Completed tasks
- DONE-TASK-063: PERF-001 Flow Deduplication & Lifecycle (distinctUntilChanged, collectAsStateWithLifecycle, WhileSubscribed)
- DONE-TASK-064: PERF-002 Timer Isolation (dedicated timer StateFlows, saveProgress debounce, PomodoroHelper atomic update)
- DONE-TASK-065: PERF-003 Lambda Stabilization (remember-wrapped callbacks, specific field keys)
- DONE-TASK-069: PERF-007 Background ViewModel Init (Dispatchers.IO init, loading state, no main-thread I/O)
- TTS/ASR crash fix applied (preventive null-safety for engine lifecycle)

## [Bug Fix: No auto-advance after 3 incorrect retries] - 2026-05-17

### Changed
- `12-training-card-session.md`: Updated section 12.3.3 (Retry and Hint Flow) to clarify that after 3 incorrect attempts the system shows the answer but does NOT auto-advance. Updated section 12.4.8 (Default Navigation Controls) to specify that Play/Pause button shows PlayArrow (not SkipNext) when hint is shown, and pressing it clears the hint without advancing.
- `22-use-case-registry.md`: Updated UC-03 AC6 to explicitly state no auto-advance after hint shown. Added AC7 specifying PlayArrow icon behavior on hint shown.
- `UnifiedNavigationRow.kt`: Removed misleading SkipNext icon when hint is shown. Play/Pause button now always shows PlayArrow when paused (including during hint), making it clear the button clears the hint and retries the same card rather than advancing.

## [TASK-061: DailyPractice Block-Config Coordinator] - 2026-05-17

### Changed
- `12-training-card-session.md`: Updated section 12.5.2 (Daily Practice Integration) to document the new block-config orchestration model. DailySessionState now uses `List<DailyBlock>` instead of flat `List<DailyTask>`. Single completion path via `coordinator.onBlockComplete()`.
- `Models.kt`: Added `DailyBlock` data class with type/tasks/isComplete/renderVia. Added `BlockRenderVia` enum. Updated `DailySessionState` to use blocks+blockIndex instead of tasks+taskIndex+blockIndex.
- `DailyPracticeCoordinator.kt`: Rewritten to block-config model. Removed advanceToNextBlock(), advanceDailyBlock(), replaceCurrentBlock() scanning logic. Added onBlockComplete() single completion path.
- `DailySessionComposer.kt`: Added buildBlocks(), buildRepeatBlocks(), rebuildBlockAsBlock(), tasksToBlocks() methods.
- `DailyPracticeScreen.kt`: Simplified to use currentBlock+currentTask instead of taskIndex-based navigation. VOCAB block signals completion via onComplete callback.
- `GrammarMateApp.kt`: Updated DailyPracticeScreenContent to use new coordinator API. Updated daily block completion detection in NavDialogs.
- `TrainingViewModel.kt`: Replaced advanceDailyTask() with onDailyBlockComplete(). Removed redundant delegation methods.

## [TASK-054: TrainingScreen TCS Migration] - 2026-05-16

### Changed
- `23-screen-elements.md`: Added migration note to TrainingScreen section (TS-* elements now provided by TrainingCardSession slots)
- `trace-index.md`: Updated TrainingScreen pilot section to reflect new slot composables (TrainingHeaderSlot, TrainingCardContentSlot, TrainingInputControlsSlot, TrainingResultContentSlot). Added TrainingCardSessionProvider adapter section.

## [Spec Update] - 2026-05-16

### Added
- Fire streak system: per-day tracking of unique practice types (TRANSLATION, VOCAB, VERB, SUB_DRILL)
- "Засчитанная учебная единица" definition in glossary
- PracticeType enum for activity classification
- UC-71: Record fire streak on session completion (was UC-62 in draft; UC-62 already used by progress reset)
- UC-72: Display fire streak on HomeScreen (was UC-63 in draft; UC-63 already used by VerbDrill play button)
- HS-23: Fire streak indicator element
- Fire streak algorithm in 03-algorithms
- Fire streak integration in daily practice, verb drill, vocab drill specs

### Changed
- Streak counting rule: from binary (completed/not) to fire-based (unique types per day)
- StreakData model: added completedTypesToday, todayFireCount, lastFireDateMs
- Fixed HS-09 labeling error in user journey models (it is flower emoji, not streak counter)
- Updated motivation section (18-learning-methodology) with fire streak design

## [Spec Update] - 2026-05-16

### Changed
- Unified session size parameter: introduced "учебная единица" (Learning Unit) with configurable `SESSION_SIZE` (default: 10)
- Removed stale "5 cards" references from daily practice vocab block across 8 spec files
- Added `sessionSize` to AppConfig parameter table (06-infrastructure.md)
- Added task TASK-050: Unify Session Size Parameter

## v3.5 (2026-05-15) -- TASK-031: Dark Theme Palette Spec Update

- `14-theme-and-ui-components.md`: Updated DarkColors in section 14.1.1 to match actual Material 3 dynamic defaults from code. All 8 explicitly-specified dark theme colors corrected: primary (#4A8B92 -> #80CBC4), onPrimary (#FFFFFF -> #003731), secondary (#7EB5A5 -> #80B5A9), onSecondary (#FFFFFF -> #00332B), background (#1A1A1A -> #1A1C1E), onBackground (#E8E8E8 -> #E2E1DF), surface (#2A2A2A -> #1A1C1E), onSurface (#E8E8E8 -> #E2E1DF). Added note that dark theme follows M3 dynamic defaults. Light theme unchanged. No code changes.
- Commit: (pending)

## v3.4 (2026-05-15) -- Re-Audit 11 CRITICAL Fixes

### Re-audit CRITICAL resolution (11/11 fixed)

- `tasks/README.md`: Updated with 11 CRITICAL fix entries (DATA-1, DATA-2, W-DATA-1, W-DATA-2, UI-1, UI-2, XC-2, TEST-1, XC-3, TEST-2, TEST-3, ARCH-1, XC-1).
- `CLAUDE.md`: Updated project version context.
- Code changes (22 files, +352 / -358 lines):
  - Wave 1 (`ff45fd6`): Add ReentrantLock mutex to 7 data stores (MasteryStore, ProgressStore, StreakStore, VerbDrillStore, WordMasteryStore, DrillProgressStore, VocabProgressStore). Prevents concurrent write corruption (DATA-1, DATA-2, W-DATA-1, W-DATA-2).
  - Wave 2 (`f7eaf38`): UI crash fixes — VocabDrillScreen null-safety, StoryQuizScreen lifecycle fix (UI-1, UI-2). Signing config in build.gradle.kts (XC-2). CI pipeline via .github/workflows/ci.yml (TEST-1). Backup validation in BackupRestorer + backup_rules.xml (XC-3). CardSessionStateMachine state guards.
  - Wave 3 (`35fea87`): Testability improvements — AudioCoordinator testability, StreakStore test-friendly constructor (TEST-2, TEST-3).
  - Wave 4 (`75dbc8f`): Implement research findings — architecture improvements, cross-cutting concerns (ARCH-1, XC-1). BackupFileCollector/BackupManager refactored.
- Commit: `2065557`

## v3.3 (2026-05-15) -- Dark-Mode Color Values + Compliance Spec

- `14-theme-and-ui-components.md`: Added dark-mode color values for all hardcoded semantic colors (section 14.7.5 expanded with exact hex values). Added Dark-Mode Color Adaptation Strategy (section 14.7.6). Added Screen-by-Screen Dark-Mode Status (section 14.7.7).
- `22-use-case-registry.md`: Added Domain 21 with UC-68 (Dark-Mode Color Compliance, 9 ACs). Updated summary counts (68 UCs, 348 ACs, 21 domains). Added cross-reference entry for TASK-012, TASK-013, TASK-014.
- `23-screen-elements.md`: Added dark-mode compliance notes to TS-06 (progress bar track), TS-26 (result label), TS-36 (drill background), TS-37 (mix challenge surface), TCS-21 (correct/incorrect result), HS-12 (vocab mastered count), VOC-21 (card front container), VOC-37 through VOC-40 (rating buttons), DP-26 (daily vocab rating buttons). Each note specifies exact dark-mode color values and references UC-68 ACs.
- Commit: (pending)

## v3.2 (2026-05-15) -- Theme Switching + Interface Language Specs

### TASK-010: Theme Mode Switching (Light/Dark/System)
### TASK-011: Interface Language Switching (English/Russian)

- `14-theme-and-ui-components.md`: Updated section 14.1.1 from "light-only" to dual theme with LightColors + DarkColors. Updated theme entry point to accept ThemeMode parameter. Updated section 14.4 (Strings & Localization) to reflect existing string resources. Updated conventions summary. Added section 14.7 (Theme Mode Selection) with ThemeMode enum, persistence, settings UI, theme application, hardcoded color audit. Added section 14.8 (Interface Language) with supported languages, persistence, settings UI, locale switching, independence from learning content.
- `22-use-case-registry.md`: Added Domain 19 with UC-66 (Theme mode switching, 7 ACs). Added Domain 20 with UC-67 (Interface language switching, 7 ACs). Updated summary counts (67 UCs, 339 ACs, 20 domains). Added cross-reference entries for TASK-010 and TASK-011.
- `23-screen-elements.md`: Added SS-46 (Appearance section header), SS-47 (theme mode selector), SS-48 (interface language selector). Updated counts (48 SS elements, 316 total).
- `tasks/TASK-010-theme-mode-switching.md`: Created self-contained task file.
- `tasks/TASK-011-interface-language-switching.md`: Created self-contained task file.
- `tasks/README.md`: Added TASK-010 and TASK-011 entries.
- Commit: `pending`

## v3.1 (2026-05-15) -- QR Share Translation Feature

### TASK-008: Share Translation via QR Code

- `12-training-card-session.md`: Updated section 12.8.2 SharedReportSheet from 4 to 5 options. Added 5th option "Share translation via QR" with QrCode2 icon. Updated behavioral contract with QR share row. Updated SharedReportSheet function signature with `shareText` and `onShareQr` parameters. Added TASK-008 link after regression paths. Updated cross-screen consistency matrix.
- `23-screen-elements.md`: Updated SH-01 (5 options, UC-65 ref). Added SH-07 (QrShareDialog element). Updated TS-33, TCS-22, VD-32, DP-18, VOC-41 with 5th option note. Updated summary counts (7 shared components, 314 total).
- `22-use-case-registry.md`: Added UC-65 (Share translation via QR code) with 5 behavioral ACs. Updated UC-53 to reference 5 options. Updated Domain 12 counts (6 UCs, 35 ACs). Updated summary counts (65 UCs, 325 ACs). Updated cross-reference table.
- `tasks/TASK-008-qr-share-translation.md`: Created self-contained task file with 4 fixes.
- `tasks/README.md`: Added TASK-008 entry.
- Commit: `pending`

## v3.0 (2026-05-14) -- Architecture Refactoring & Quality Fixes

### TASK-007: Verb Drill Exit Navigation to HOME

- `10-verb-drill.md`: Added section 10.6.11 (Exit Navigation Rule) specifying all exit paths must navigate to HOME. Added TASK-007 link after TASK-006 link in section 10.4.3.
- `23-screen-elements.md`: Updated VD-11 (back button navigates HOME), VD-36 (exit button navigates HOME), VD-41 (completion exit navigates HOME). Added UC-64 cross-references.
- `22-use-case-registry.md`: Added UC-64 (Verb Drill exit navigates to HOME) with 6 behavioral ACs. Updated Domain 6 counts (7 UCs, 36 ACs). Updated summary counts (64 UCs, 320 ACs). Updated cross-reference table and source-to-UC mapping.
- `scenario-07-verb-drill.md`: Added exit navigation issue section documenting the bug, root cause, and fix reference.
- `tasks/TASK-007-verb-drill-exit-navigation.md`: Created self-contained task file.
- `tasks/README.md`: Added TASK-007 entry.
- Commit: `pending`

### TASK-006: Verb Drill Play Button Fix

- `10-verb-drill.md`: Clarified pause/resume vs advance behavior in togglePause() (section 10.4.3). Added card advancement rules and pause reason disambiguation. Updated togglePause() method description in 10.4.7.
- `12-training-card-session.md`: Updated Pause button behavior in section 12.7.1 to distinguish manual-pause-resume from hint-pause-advance.
- `23-screen-elements.md`: Updated VD-14 (TTS button discrepancy note -- plain IconButton vs TtsSpeakerButton). Updated VD-36 (navigation row Pause/Play behavior with hint/no-hint distinction). Added UC-63 cross-reference.
- `22-use-case-registry.md`: Added UC-63 (VerbDrill Play button distinguishes pause reason) with 5 behavioral ACs. Updated Domain 6 counts (6 UCs, 30 ACs). Updated cross-reference table. Updated summary counts (63 UCs, 314 ACs).
- `tasks/TASK-006-verb-drill-play-button-fix.md`: Created self-contained task file.
- `tasks/README.md`: Added TASK-006 entry.
- Commit: `pending`

### TASK-001 through TASK-005: Performance, Cursor, TTS, Welcome Dialog

- `trace-index.md`: Added Phase 6 section with symbols from TASK-001 through TASK-005: TtsState sealed class variants, Mutex, init timeout, error tooltip (TASK-003/005); welcomeDialogAttempts fields (TASK-004); caching fields in LessonStore, VerbDrillStore, WordMasteryStore, DailySessionComposer (TASK-002); advanceDailyCursor, dailyCursorAtSessionStart (TASK-001).
- `02-data-stores.md`: Caching added to LessonStore.getLessons(), VerbDrillStore.loadProgress()/loadAllCardsForPack(), WordMasteryStore.loadAll() (TASK-002).
- `05-audio-tts-asr.md`: TtsState changed from enum to sealed class with Error(reason). Mutex serialization. 30s init timeout. Error tooltip with OOM differentiation (TASK-003/005).
- `09-daily-practice.md`: Cursor-based level resolution in startDailyPractice(). Lesson transition in advanceDailyCursor(). Verb block cycling (TASK-001).
- `13-app-entry-and-navigation.md`: welcomeDialogAttempts counter in profile.yaml. HOME-only guard. Skip increments counter (TASK-004).
- `22-use-case-registry.md`: UC-60 AC3-AC4 updated (caching now implemented). UC-61 expanded (lesson transition + cursor advancement).
- Commit: `pending`

### ViewModel Thinning
- Public methods reduced 108 -> 54 (-50%)
- 8 feature coordinator properties exposed
- UI call sites migrated to vm.coordinator.method() pattern
- Commit: `5ff1198`

### Unit Tests: BossBattleRunner, AnswerValidator, CardProvider, ProgressTracker
- +233 @Test methods added (231 -> 464)
- New test files: BossBattleRunnerTest, AnswerValidatorTest, CardProviderTest, ProgressTrackerTest
- Commit: `2391496`

### Manual DI via AppContainer
- AppContainer with constructor injection for ViewModels and helpers
- GrammarMateApplication class added
- 4 new store interfaces: DrillProgressStore, HiddenCardStore, VocabProgressStore, ProfileStore
- Zero hardcoded *Impl(application) in ViewModels
- Commit: `cf88e3b`

### UI Deduplication
- 3 report sheets -> 1 SharedReportSheet
- 3 word bank implementations -> 1 shared component
- 2 progress indicators -> 1 SessionProgressIndicator
- Commit: `c8cbc2c`

### Agent-Friendly Architecture
- Per-feature README.md in 7 packages
- Module dependency map (docs/module-map.md)
- Commit: `5fb6209`

### Unit Tests: SessionRunner, DailyPracticeCoordinator
- +231 @Test methods added (464 -> 695)
- New test files: SessionRunnerTest, DailyPracticeCoordinatorTest
- Commit: `be0f6d2`

### Vendor Code Audit Status Update
- Updated audit spec with status for completed improvements
- Commit: `e88242a`

### Trace-Index: Test Coverage
- Updated trace-index with Phase 5 unit test coverage for SessionRunner and DailyPracticeCoordinator
- Commit: `2094e57`

### Metrics
- Total @Test methods: 695 (was 231, +464)
- ViewModel public methods: 54 (was 108, -50%)
- Manual DI: 0 hardcoded *Impl(application) in ViewModels
- UI deduplication: 8 component files -> 3 shared components

### Branch: feature/arch-feature-migration (not merged)

## v2.4 (2026-05-13) -- Lesson Rename

### Lesson Display Names Updated to Tense-Based Names
- `15-lesson-content-and-packs.md`: Updated Italian lesson titles in manifest example from generic "Prima Forma" names to tense-based names: Presente, Imperfetto, Passato Prossimo, Futuro + Remoto, Condizionale + Mix, Tutti i Tempi.
- `assets/grammarmate/packs/IT_VERB_GROUPS_ALL.zip`: Updated manifest.json lesson titles to match.

## v2.3 (2026-05-13) -- Progress Reset Fix

### Progress Reset Scoped to Current Language
- `02-data-stores.md`: Added section 2.16 documenting reset behavior: scoped to current language/pack, confirmation dialog, store-by-store cleanup table, invariants.
- `22-use-case-registry.md`: Added Domain 18 with UC-62 (Reset progress for current language with confirmation). 8 acceptance criteria. Updated cross-reference table.
- `23-screen-elements.md`: Updated SS-32 element description (language name in button label, confirmation dialog). Added SS-32a (confirmation dialog element).

## v2.2 (2026-05-13) -- TTS Icon Auto-Recovery Fix

### TTS Icon Bug Fix
- `05-audio-tts-asr.md`: Updated section 5.1.7 with auto-initialization behavior after download. Updated TtsSpeakerButton states table with auto-recovery note.
- `22-use-case-registry.md`: Added Domain 17 with UC-61 (TTS speaker icon auto-recovery). Updated summary counts (61 UCs, 267 ACs, 17 domains).

## v2.1 (2026-05-13) -- Documentation Cleanup & Skills Pipeline

### Documentation Cleanup
- Deleted 27 superseded files: 24 legacy docs fully covered by specs 01-23, broken docs/README.md, stale CONSOLIDATED_DISCREPANCY_REPORT.md, completed arch-review-execution-plan.md
- Removed empty legacy-plans/ directory
- Kept 7 legacy files with unique content (test plan, TTS review bugs, unbuilt features)

### Navigation Overhaul
- Rewrote specification/README.md: added specs 21-23, scenario-to-spec cross-references
- Added SKILLS PIPELINE section to CLAUDE.md with trigger table and workflow diagram
- Updated CLAUDE.md roadmap section: removed deleted arch-review plan, added active plans

### Files Changed
- Deleted: 27 files (see commit 0162395)
- Updated: CLAUDE.md (skills pipeline + roadmap), docs/specification/README.md (full rewrite)

## v2.0 (2026-05-13) -- Architecture Review & Full Refactoring

### UI Layer Decomposition
- GrammarMateApp.kt: 3797 → 799 lines (79% reduction). Now a pure router.
- Extracted 6 screens to `ui/screens/`: HomeScreen, LessonRoadmapScreen, StoryQuizScreen, TrainingScreen, SettingsScreen, LadderScreen
- Created `ui/components/`: SharedComponents, DailyPracticeComponents, VerbDrillSheets
- Deduplicated TtsSpeakerButton, NavIconButton, AsrStatusIndicator into SharedComponents.kt
- DailyPracticeScreen: 1421 → 723, VerbDrillScreen: 1228 → 972

### ViewModel Decomposition
- TrainingViewModel: 3400 → 1197 lines (65% reduction over 5 phases)
- Phase 4 additions: BossOrchestrator (351), BadSentenceHelper (132), SettingsActionHandler (152)
- CardSessionStateMachine extracted to unify retry/hint logic across providers

### Data Layer Decomposition
- LessonStore: 953 → 448 lines. Split into PackImporter, LanguageManager, DrillFileManager
- BackupManager: 847 → 365 lines. Split into BackupFileCollector, BackupRestorer
- AtomicFileWriter violations fixed: importFromUri, importVerbDrillFile, BackupRestorer copy operations
- AtomicFileWriter.copyAtomic() added for binary file operations

### Store Synchronization
- StoreFactory singleton created: caches WordMasteryStore, VerbDrillStore, BadSentenceStore per packId
- All consumers (TrainingVM, VerbDrillVM, VocabDrillVM, DailyPracticeCoordinator) use factory
- TtsEngine: native memory leak fixed (release() added), shared instance via AudioCoordinator

### Store Interfaces
- 9 interfaces created in Kotlin style (interface = StoreName, class = StoreNameImpl)
- All stores implement their interfaces. Consumers updated to use Impl constructors.

### Dead Code Removed
- FlowerProgressRenderer.kt deleted (141 lines, not imported anywhere)
- calculateCompletedSubLessons deduplicated (CardProvider → ProgressTracker delegation)
- WordBankGenerator dedup: both providers now use canonical implementation
- 3 backup/worktree branches identified for cleanup

### Metrics
- Total .kt files: 86 (was 69)
- feature/ + shared/: 20 files, 5366 lines (was 16 files in ui/helpers/, ~4981 lines)
- data/: 45 files, 7635 lines (was 38 files, ~7058 lines)
- 0 files exceed line limits
- Build: SUCCESS, 0 compilation errors

### Documentation Added
- Created `22-use-case-registry.md`: 50 use cases, 201 acceptance criteria across 11 domains, extracted from 15 scenario traces and user stories document
- Summary table with per-domain UC/AC counts and cross-reference mapping from source files

### Branch: fix/spec-discrepancies (not merged)

## v1.5 (2026-05-12) -- Full Specification + Bug Fixes + Feature Updates

### Specification Created
- 20 specification documents created from scratch (01-20)
- 3 architecture audit documents
- 15 scenario verification traces
- Consolidated discrepancy report (49 findings)
- README.md index

### Bug Fixes (code -> spec alignment)
- C1: Boss battles no longer inflate mastery
- C2: WordMasteryStore now uses pack-scoped path
- C3: VerbDrillStore stale cache fixed
- C4: AtomicFileWriter used in pack import
- C5: Backup now covers all 14 data stores
- C6: Daily practice completion screen now visible
- C7: WelcomeDialog shows on first launch
- M1-M6: isLearned threshold, lessonIndex, boss rewards, SAF picker, verb progress keys, back handler
- R1-R5: Diacritics normalization, vocab forms, mastery refresh, hide button, import cleanup

### Features Added
- Bad card reporting in ALL training modes (training, verb_drill, vocab_drill, daily_translate, daily_vocab, daily_verb)
- Unified export grouped by language/pack/mode
- Numbers excluded from general vocab pool
- Daily practice cursor isolated from lesson preview browsing
- Auto-submit on correct keyboard input

### Architecture Modules Created
- AnswerValidator (feature/training/AnswerValidator.kt)
- WordBankGenerator (feature/training/WordBankGenerator.kt)
- CardProvider (feature/training/CardProvider.kt)
- StreakManager (feature/progress/StreakManager.kt)
- FlowerProgressRenderer (feature/training/FlowerProgressRenderer.kt)

### Documentation Reorganized
- All legacy specs, design docs, plans moved into docs/specification/
- CLAUDE.md updated with SPECIFICATION & DOCUMENTATION section
- Document map by component added

### Commits (fix/spec-discrepancies)

| Hash | Message |
|------|---------|
| `dcbfe19` | Fix critical discrepancies: boss mastery, vocab count, verb progress stale cache, atomic imports |
| `ad0c79f` | Fix UX discrepancies: daily completion screen, welcome dialog, SAF picker, back handler |
| `7b86e4b` | Fix data completeness: backup all stores, verb progress keys, atomic writer, import cleanup |
| `063b5ad` | Fix consistency: isLearned threshold, lesson index advance, boss rewards, diacritics, forms, mastery refresh |
| `7ae9417` | Extract StreakManager module from TrainingViewModel streak logic |
| `a34f288` | Extract AnswerValidator module from duplicated validation logic |
| `76acb9f` | Extract WordBankGenerator module from duplicated word bank logic |
| `6dd37c5` | Extract CardProvider module from TrainingViewModel card selection logic |
| `584aa36` | Extract FlowerProgressRenderer module from TrainingViewModel flower logic |
| `7b07ab6` | Bump version to 1.5 |
| `4c35612` | Enable bad card reporting in all training modes with unified export |
| `ffc9b1c` | Exclude numbers from general vocab pool in drill and daily practice |
| `d91e174` | Update specification: bad card reporting in all training modes |
| `741c5d3` | Isolate daily practice cursor from lesson preview browsing |
| `1424487` | Add auto-submit when correct answer typed in keyboard mode |
| `37dfd49` | Add Normalizer.isExactMatch and fix BackupManager build |
| `914efa3` | Add comprehensive specification docs for modular refactoring |
| `8f789c9` | Wire 4 pure modules into TrainingViewModel: AnswerValidator, WordBankGenerator, StreakManager, CardProvider |
| `28d4660` | Reorganize all documentation into docs/specification/ and update CLAUDE.md |

## v1.4.1 (previous) -- Daily Practice Pipeline

Daily practice pipeline with 3-block session composition (sentence translation, vocab flashcard, verb conjugation). Cursor-based session state. Fix branch: `feature/daily-cursors`.
