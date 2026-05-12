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

---

## Scenario Verification (Code Traces)

| # | Scenario | File |
|---|----------|------|
| S1 | Lesson training flow | [scenario-01-training-flow.md](scenario-01-training-flow.md) |
| S2 | Answer validation | [scenario-02-answer-validation.md](scenario-02-answer-validation.md) |
| S3 | Mastery & flower progression | [scenario-03-mastery-flower.md](scenario-03-mastery-flower.md) |
| S4 | Spaced repetition scheduling | [scenario-04-spaced-repetition.md](scenario-04-spaced-repetition.md) |
| S5 | Input modes (voice/keyboard/wordbank) | [scenario-05-input-modes.md](scenario-05-input-modes.md) |
| S6 | Daily practice session | [scenario-06-daily-practice.md](scenario-06-daily-practice.md) |
| S7 | Verb drill standalone | [scenario-07-verb-drill.md](scenario-07-verb-drill.md) |
| S8 | Vocab drill standalone | [scenario-08-vocab-drill.md](scenario-08-vocab-drill.md) |
| S9 | Boss battle | [scenario-09-boss-battle.md](scenario-09-boss-battle.md) |
| S10 | Pack-scoped drills | [scenario-10-pack-drills.md](scenario-10-pack-drills.md) |
| S11 | Navigation flow | [scenario-11-navigation.md](scenario-11-navigation.md) |
| S12 | State persistence | [scenario-12-state-persistence.md](scenario-12-state-persistence.md) |
| S13 | Lesson pack import | [scenario-13-pack-import.md](scenario-13-pack-import.md) |
| S14 | Backup & restore | [scenario-14-backup-restore.md](scenario-14-backup-restore.md) |
| S15 | First launch & onboarding | [scenario-15-onboarding.md](scenario-15-onboarding.md) |

---

## Discrepancy Report

| Document | Description |
|----------|-------------|
| [CONSOLIDATED_DISCREPANCY_REPORT.md](CONSOLIDATED_DISCREPANCY_REPORT.md) | All spec-vs-code discrepancies found during audit |
| [arch-audit-spec-vs-code.md](arch-audit-spec-vs-code.md) | Detailed discrepancy analysis (24 found) |

---

## Legacy Documentation

All prior spec, design, and planning documents have been consolidated here. These are historical references — the numbered specs (01–20) above are the current source of truth.

### Legacy Specs (Root Level)

| File | Original Name | Description |
|------|---------------|-------------|
| [legacy-screen-forms-spec.md](legacy-screen-forms-spec.md) | Экранные формы.Спецификация | Screen form specifications |
| [legacy-screen-forms-verification.md](legacy-screen-forms-verification.md) | Экранные формы.Верификация | Screen form verification |
| [legacy-state-machine.md](legacy-state-machine.md) | Spec — State Machine | State machine specification |
| [legacy-project-idea-v1-5.md](legacy-project-idea-v1-5.md) | GrammarMate_project_idea_v1_5 | Original project concept document |
| [legacy-daily-practice-plan.md](legacy-daily-practice-plan.md) | DAILY_PRACTICE_PLAN | Daily practice implementation plan |
| [legacy-team.md](legacy-team.md) | TEAM | Team coordination notes |
| [legacy-session-report-2026-05-07.md](legacy-session-report-2026-05-07.md) | SESSION_REPORT_2026-05-07 | Session report |
| [legacy-agents.md](legacy-agents.md) | agents | Agent workflow documentation |

### Legacy Design Docs (from docs/)

| File | Original Name | Description |
|------|---------------|-------------|
| [legacy-functional-requirements.md](legacy-functional-requirements.md) | FUNCTIONAL_REQUIREMENTS | Full functional requirements |
| [legacy-forgetting-curve-analysis.md](legacy-forgetting-curve-analysis.md) | FORGETTING_CURVE_ANALYSIS | Ebbinghaus curve research |
| [legacy-tts-design.md](legacy-tts-design.md) | TTS_DESIGN | TTS system design |
| [legacy-tts-italian-design.md](legacy-tts-italian-design.md) | TTS_ITALIAN_DESIGN | Italian TTS design |
| [legacy-tts-research.md](legacy-tts-research.md) | TTS_RESEARCH | TTS technology research |
| [legacy-tts-italian-research.md](legacy-tts-italian-research.md) | TTS_ITALIAN_RESEARCH | Italian TTS research |
| [legacy-tts-review.md](legacy-tts-review.md) | TTS_REVIEW | TTS implementation review |
| [legacy-word-tap-translation-design.md](legacy-word-tap-translation-design.md) | WORD_TAP_TRANSLATION_DESIGN | Word-tap translation feature design |
| [legacy-test-plan.md](legacy-test-plan.md) | TEST_PLAN | Comprehensive test plan |
| [legacy-testing-summary.md](legacy-testing-summary.md) | TESTING_SUMMARY | Testing results summary |
| [legacy-test-coverage-analysis.md](legacy-test-coverage-analysis.md) | TEST_COVERAGE_ANALYSIS | Test coverage report |
| [legacy-ui-test-plan.md](legacy-ui-test-plan.md) | UI_TEST_PLAN | UI testing plan |
| [legacy-offline-asr-research.md](legacy-offline-asr-research.md) | OFFLINE_ASR_RESEARCH | Offline ASR technology research |

### Legacy Drill Designs (from docs/superpowers/specs/)

| File | Description |
|------|-------------|
| [legacy-drill-designs/2026-05-07-drill-mode-design.md](legacy-drill-designs/2026-05-07-drill-mode-design.md) | Drill mode initial design |
| [legacy-drill-designs/2026-05-08-seamless-drill-design.md](legacy-drill-designs/2026-05-08-seamless-drill-design.md) | Seamless drill integration design |
| [legacy-drill-designs/2026-05-09-verb-drill-design.md](legacy-drill-designs/2026-05-09-verb-drill-design.md) | Verb drill system design |
| [legacy-drill-designs/2026-05-09-verb-drill-plan.md](legacy-drill-designs/2026-05-09-verb-drill-plan.md) | Verb drill implementation plan |
| [legacy-drill-designs/2026-05-10-card-mixing-reference.md](legacy-drill-designs/2026-05-10-card-mixing-reference.md) | Card mixing algorithm reference |
| [legacy-drill-designs/2026-05-10-pack-scoped-drills-design.md](legacy-drill-designs/2026-05-10-pack-scoped-drills-design.md) | Pack-scoped drill design |
| [legacy-drill-designs/2026-05-10-per-pack-bad-words-design.md](legacy-drill-designs/2026-05-10-per-pack-bad-words-design.md) | Per-pack bad words design |
| [legacy-drill-designs/2026-05-10-training-card-session-design.md](legacy-drill-designs/2026-05-10-training-card-session-design.md) | Training card session design |
| [legacy-drill-designs/2026-05-12-daily-session-fixes.md](legacy-drill-designs/2026-05-12-daily-session-fixes.md) | Daily session bug fixes |

### Legacy Plans (from docs/superpowers/plans/)

| File | Description |
|------|-------------|
| [legacy-plans/2026-05-12-daily-practice-3block-pipeline.md](legacy-plans/2026-05-12-daily-practice-3block-pipeline.md) | Daily practice 3-block pipeline plan |
