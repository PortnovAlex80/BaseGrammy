# GrammarMate — Complete Project Specification

Generated: 2026-05-12
Branch: feature/daily-cursors
Agents: 20 specification agents + 3 architectural auditors

---

## Specification Documents

### Data Layer

| # | Document | Description |
|---|----------|-------------|
| 01 | [Models & State](01-models-and-state.md) | All data classes, enums, sealed classes, contracts, invariants |
| 02 | [Data Stores](02-data-stores.md) | 11 data stores: API, file format, write semantics, cross-store dependencies |
| 03 | [Algorithms & Calculators](03-algorithms-and-calculators.md) | Ebbinghaus curve, flower state machine, scheduling, normalization |
| 04 | [Parsers](04-parsers.md) | 5 parsers, CSV format specifications for all content types |
| 05 | [Audio TTS/ASR](05-audio-tts-asr.md) | Sherpa-ONNX TTS/ASR engines, model management, lifecycle |
| 06 | [Infrastructure](06-infrastructure.md) | AtomicFileWriter, YamlListStore, BackupManager, AppConfigStore |

### UI Layer

| # | Document | Description |
|---|----------|-------------|
| 07 | [App Router](07-app-router.md) | GrammarMateApp screen routing, navigation graph, dialog orchestration |
| 08 | [TrainingViewModel](08-training-viewmodel.md) | 3600-line ViewModel: all business logic, 70+ fields, 60+ methods |
| 09 | [Daily Practice](09-daily-practice.md) | 3-block session: translations, vocab flashcards, verb conjugations |
| 10 | [Verb Drill](10-verb-drill.md) | Conjugation practice system with CardSessionProvider |
| 11 | [Vocab Drill](11-vocab-drill.md) | Anki-style flashcard system with SRS scheduling |
| 12 | [Training Card Session](12-training-card-session.md) | Reusable card presentation component with slots |

### Cross-Cutting

| # | Document | Description |
|---|----------|-------------|
| 13 | [App Entry & Navigation](13-app-entry-and-navigation.md) | MainActivity, AppRoot, initialization sequence, state restoration |
| 14 | [Theme & UI Components](14-theme-and-ui-components.md) | Material 3 theme, shared components, sounds, strings |
| 15 | [Lesson Content & Packs](15-lesson-content-and-packs.md) | ZIP pack format, import flow, CSV specs, pack validator |
| 16 | [Existing Specs Consolidation](16-existing-specs-consolidation.md) | All prior specs consolidated: evolution, gaps, conflicts |

### User-Facing

| # | Document | Description |
|---|----------|-------------|
| 17 | [User Stories & Use Cases](17-user-stories-and-use-cases.md) | 79 user stories, 25 use cases, 25 edge case scenarios |
| 18 | [Learning Methodology](18-learning-methodology.md) | Ebbinghaus curve, flower metaphor, scheduling algorithms, critique |
| 19 | [Screen Catalog](19-screen-catalog.md) | 31 screens/dialogs with wireframes, interactions, business rules |
| 20 | [Non-Functional Requirements](20-non-functional-requirements.md) | Performance, offline, security, compatibility, scalability |

### Architectural Audit

| # | Document | Description |
|---|----------|-------------|
| A1 | [Dependency Map & State Duplication](arch-audit-dependencies.md) | Component inventory, dependency graph, state ownership, duplications |
| A2 | [Spec vs Code Discrepancies](arch-audit-spec-vs-code.md) | Critical/minor discrepancies, behavioral inconsistencies |
| A3 | [Module Decomposition Proposal](arch-module-decomposition.md) | Proposed modular architecture with interfaces and migration plan |

---

## Quick Reference

### Key Metrics
- **Source files**: 51 Kotlin files
- **Data layer**: 39 files
- **UI layer**: 11 files (+ 3 helpers)
- **Tests**: 18 unit + integration tests
- **TrainingViewModel**: 3600+ lines (primary refactoring target)
- **Lesson content**: 2 default packs (EN + IT), 13 Italian lesson CSVs, 6 vocab CSVs

### Architecture Pattern
```
MainActivity -> AppRoot (restore check) -> GrammarMateApp (Compose UI)
                      |
               TrainingViewModel (all business logic + state)
                      |
           data/ layer (stores, parsers, calculators)
```

### Key Business Rules
1. WORD_BANK mode never counts for mastery (only VOICE/KEYBOARD)
2. All file writes must use AtomicFileWriter (temp -> fsync -> rename)
3. Single ViewModel pattern — no second ViewModels (helpers only)
4. Drill visibility is pack-scoped
5. AppScreen.ELITE/VOCAB kept for backward compat (redirect to HOME)
