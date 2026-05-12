# Specification Changelog

All changes to specification documents are tracked here. Each entry references the commit hash and version.

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
