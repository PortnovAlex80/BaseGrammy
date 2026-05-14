# Specification Changelog

All changes to specification documents are tracked here. Each entry references the commit hash and version.

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

### Agent-Friendly Architecture Audit
- Created `arch-agent-friendly-audit-2026-05-14.md` with current audit of parallel agent readiness, ownership gaps, hotspot files, boundary enforcement, and recommended roadmap.
- Updated `README.md` architecture index with A5 audit entry.
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
- Rewrote specification/README.md: added specs 21-23, arch-phase3, scenario-to-spec cross-references
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
