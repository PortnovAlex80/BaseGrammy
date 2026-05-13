# GrammarMate — Specification Index

Source of truth for all project specs. Numbered specs (01–23) are current; scenario traces verify spec-code alignment.

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
| 08 | [TrainingViewModel](08-training-viewmodel.md) | ViewModel business logic, session management, answer validation |
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

### Regression & Roadmap

| # | Document | Description |
|---|----------|-------------|
| 21 | [Product Roadmap](21-product-roadmap.md) | Next sprint features: Card Feel Rating, Difficulty Levels |
| 22 | [Use Case Registry](22-use-case-registry.md) | UC IDs with acceptance criteria — for regression checks and PR review |
| 23 | [Screen Elements](23-screen-elements.md) | Screen element catalog with invariants — for UI regression checks |

### Architecture

| # | Document | Description |
|---|----------|-------------|
| A1 | [Dependency Map](arch-audit-dependencies.md) | Component inventory, dependency graph, state duplication |
| A2 | [Spec vs Code Discrepancies](arch-audit-spec-vs-code.md) | Critical/minor discrepancies found during audit |
| A3 | [Module Decomposition](arch-module-decomposition.md) | Proposed modular architecture with interfaces |
| A4 | [Phase 3 Interfaces](arch-phase3-interfaces.md) | Phase 3 store interface definitions |

---

## Scenario Verification (Code Traces)

Point-in-time verification of spec-code alignment. Line references may be stale after code changes — check git history.

| # | Scenario | Verifies specs |
|---|----------|---------------|
| S1 | [Training flow](scenario-01-training-flow.md) | 08, 12 |
| S2 | [Answer validation](scenario-02-answer-validation.md) | 08, 01 |
| S3 | [Mastery & flowers](scenario-03-mastery-flower.md) | 03, 01 |
| S4 | [Spaced repetition](scenario-04-spaced-repetition.md) | 03 |
| S5 | [Input modes](scenario-05-input-modes.md) | 08, 12 |
| S6 | [Daily practice](scenario-06-daily-practice.md) | 09 |
| S7 | [Verb drill](scenario-07-verb-drill.md) | 10 |
| S8 | [Vocab drill](scenario-08-vocab-drill.md) | 11 |
| S9 | [Boss battle](scenario-09-boss-battle.md) | 08, 03 |
| S10 | [Pack drills](scenario-10-pack-drills.md) | 10, 11, 15 |
| S11 | [Navigation](scenario-11-navigation.md) | 07, 13 |
| S12 | [State persistence](scenario-12-state-persistence.md) | 02, 06 |
| S13 | [Pack import](scenario-13-pack-import.md) | 15, 04 |
| S14 | [Backup & restore](scenario-14-backup-restore.md) | 06 |
| S15 | [Onboarding](scenario-15-onboarding.md) | 13, 07 |

---

## Legacy Documentation (kept with unique content)

These files contain information not yet incorporated into numbered specs:

| File | Why kept |
|------|----------|
| [legacy-project-idea-v1-5.md](legacy-project-idea-v1-5.md) | Original vision, Warm-up concept, Boss medal tiers, hint rate rules |
| [legacy-screen-forms-verification.md](legacy-screen-forms-verification.md) | 32 spec-vs-code discrepancies (not all resolved) |
| [legacy-test-plan.md](legacy-test-plan.md) | 200+ concrete test case definitions with assertions |
| [legacy-tts-review.md](legacy-tts-review.md) | 6 unfixed critical/major TTS code review findings |
| [legacy-word-tap-translation-design.md](legacy-word-tap-translation-design.md) | Unbuilt feature spec with no other coverage |
| [legacy-drill-designs/2026-05-10-per-pack-bad-words-design.md](legacy-drill-designs/2026-05-10-per-pack-bad-words-design.md) | Partially implemented migration plan |
| [legacy-drill-designs/2026-05-12-daily-session-fixes.md](legacy-drill-designs/2026-05-12-daily-session-fixes.md) | 4 in-progress bug fixes |
