# Specification Changelog

All changes to specification documents are tracked here. Each entry references the commit hash and version.

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
- ui/helpers/: 20 files, 5366 lines (was 16 files, ~4981 lines)
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
- AnswerValidator (ui/helpers/AnswerValidator.kt)
- WordBankGenerator (ui/helpers/WordBankGenerator.kt)
- CardProvider (ui/helpers/CardProvider.kt)
- StreakManager (ui/helpers/StreakManager.kt)
- FlowerProgressRenderer (ui/helpers/FlowerProgressRenderer.kt)

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
