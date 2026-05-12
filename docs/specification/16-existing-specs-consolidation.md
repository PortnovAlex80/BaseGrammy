# 16. Existing Specifications -- Consolidated Reference

This document consolidates all design specifications, plans, and requirement documents that have been produced for GrammarMate, providing a single reference point for the project's design history, decisions, and current status.

---

## 16.1 Document Inventory

### Design Specifications (docs/superpowers/specs/)

| Document | Date | Purpose | Status |
|---|---|---|---|
| `2026-05-07-drill-mode-design.md` | 2026-05-07 | Original drill mode with group-based card iteration | Superseded |
| `2026-05-08-seamless-drill-design.md` | 2026-05-08 | Seamless continuous drill flow, green theme, circular progress | Superseded by TrainingCardSession |
| `2026-05-09-verb-drill-design.md` | 2026-05-09 | Standalone Verb Drill mode with separate ViewModel | Implemented |
| `2026-05-09-verb-drill-plan.md` | 2026-05-09 | Step-by-step implementation plan for Verb Drill | Implemented |
| `2026-05-10-card-mixing-reference.md` | 2026-05-10 | MixedReviewScheduler card mixing mechanism | Current reference |
| `2026-05-10-pack-scoped-drills-design.md` | 2026-05-10 | Bind drills to active pack, manifest-level drill sections | Implemented |
| `2026-05-10-per-pack-bad-words-design.md` | 2026-05-10 | Pack-scoped bad/flagged card storage, verb drill bad cards | Partially implemented |
| `2026-05-10-training-card-session-design.md` | 2026-05-10 | Reusable TrainingCardSession composable with slot architecture | Implemented |
| `2026-05-12-daily-session-fixes.md` | 2026-05-12 | Daily Practice bug fixes (mic, card counting, verb info, navigation) | In progress |

### Implementation Plans (docs/superpowers/plans/)

| Document | Date | Purpose | Status |
|---|---|---|---|
| `2026-05-12-daily-practice-3block-pipeline.md` | 2026-05-12 | Restore 3-block Daily Practice pipeline | Implemented |

### Russian-Language Specifications (project root)

| Document | Date | Purpose | Status |
|---|---|---|---|
| `Spec -- State Machine.md` | 2026-05-12 | Navigation state machine, AppStartRule, screen transition guards | Partially implemented |
| `Экранные формы.Спецификация.md` | 2026-05-12 | Per-screen UI element specification (every button, label, condition) | Current reference |
| `Экранные формы.Верификация.md` | 2026-05-12 | Verification report for screen forms spec against code | Current reference |

### Project Documents

| Document | Date | Purpose | Status |
|---|---|---|---|
| `GrammarMate_project_idea_v1_5.md` | 2026-01-08 | Original project concept, methodology, content structure | Partially outdated |
| `DAILY_PRACTICE_PLAN.md` | (undated) | Daily Practice v2 -- sequential sub-lesson pipeline | Superseded by 3-block design |
| `docs/FUNCTIONAL_REQUIREMENTS.md` | 2026-01-16 | Comprehensive functional requirements (~140 items) | Partially outdated |

---

## 16.2 Drill Mode Design Evolution

### Timeline

**Phase 1: Group-Based Drill (2026-05-07)**
The initial drill mode design introduced a `drillFile` field on lesson manifests. When present, a Drill tile appeared on the lesson roadmap. Cards were split into groups of 9 with pauses between groups. All three input modes (VOICE, KEYBOARD, WORD_BANK) were supported, but mastery was not recorded. Progress was tracked per lesson as a group index.

Key decisions:
- Drill was embedded within the existing lesson roadmap (replaced Story tiles)
- Cards grouped by 9 with intermediate screens
- `DrillProgressStore` saved group-based progress
- `isDrillMode` flag gated drill-specific UI branches

**Phase 2: Seamless Continuous Drill (2026-05-08)**
One day later, the design was revised to remove the group-based fragmentation. The new design introduced:
- Continuous sequential card stream with no pauses
- Green theme override during drill mode (mint background, green accents)
- Circular progress indicator replacing the text counter
- Progress tracked by card index instead of group index
- Long-press on Drill tile to reset progress

This design was implemented but later superseded when TrainingCardSession was introduced.

**Phase 3: Standalone Verb Drill (2026-05-09)**
Verb Drill was designed as an isolated feature with its own ViewModel (`VerbDrillViewModel`) and screen (`VerbDrillScreen`), completely separate from TrainingViewModel. This was a deliberate architectural decision to avoid increasing the already-large TrainingViewModel.

Key design choices:
- Separate ViewModel, not a TrainingViewModel extension
- CSV format: `RU;IT;Verb;Tense;Group` with optional columns
- Dropdown filtering by tense and group
- 10-card batches with daily reset (8am check)
- Progress stored per combo: `"{group}\|{tense}"` key in YAML
- Access via special tile on HomeScreen (not within lesson roadmap)

**Phase 4: Pack-Scoped Drills (2026-05-10)**
Both Verb Drill and Vocab Drill were refactored to bind to the active lesson pack. The manifest gained `verbDrill` and `vocabDrill` top-level sections with file lists. Drill visibility became conditional on the active pack declaring drill content.

Changes:
- Removed `type: "verb_drill"` from lesson entries (moved to manifest-level sections)
- Progress scoped per-pack at `drills/{packId}/` paths
- `reloadForPack(packId)` method added to drill ViewModels
- Hardcoded asset vocab CSVs removed; all drill data ships inside packs

**Phase 5: TrainingCardSession Reusable Component (2026-05-10)**
The training card UI was extracted into a reusable composable with customization slots. Both standard training and Verb Drill (and any future modes) use this component. The `CardSessionContract` interface defines capabilities (TTS, voice input, word bank, flagging, navigation, pause), and each mode provides an adapter (`TrainingCardSessionProvider`, `VerbDrillCardSessionProvider`).

Slots: header, cardContent, inputControls, resultContent, navigationControls, completionScreen, progressIndicator -- all optional with sensible defaults.

### What was implemented vs. what changed

| Original plan | What actually shipped |
|---|---|
| Group-based drill with pauses | Continuous card stream |
| Drill embedded in lesson roadmap | Separate HomeScreen tiles for Verb/Vocab Drill |
| `type: "verb_drill"` in lesson entries | Top-level `verbDrill`/`vocabDrill` manifest sections |
| Flat progress files | Pack-scoped progress at `drills/{packId}/` |
| Inline drill UI in TrainingScreen | Reusable TrainingCardSession composable |
| TrainingScreen has its own implementation | TrainingScreen kept custom; TrainingCardSession used by DailyPractice, VerbDrill, VocabDrill |

---

## 16.3 Daily Practice Design History

### Evolution from Elite Mode to Daily Practice

**Elite Mode (original concept, from project idea v1.5)**
The original post-course maintenance mode. 7-step cycle, each step focused on 3 topics. Each lesson split into 7 packets. Composition: 3 warm-up sentences + 21 focus + 6 noise = 30 sentences per step. Weekly Boss on Step 7.

Elite Mode was implemented but later replaced by Daily Practice. The `AppScreen.ELITE` enum value is retained for backward compatibility (redirects to HOME).

**Vocab Sprint (original concept)**
Separate vocabulary exercises between lessons. Two types: RU to Target and Target to RU. Blocks of 6 words, 3 blocks (18 words) between lessons. Spaced repetition through lessons.

Vocab Sprint was implemented and later replaced by the Vocab Drill (Anki-style flashcards) screen.

**Daily Practice v1 -- Three Random Blocks**
The initial Daily Practice had 3 blocks:
1. 5 random SentenceCards from one lesson
2. 5 VocabWords from drill files
3. 5 VerbDrillCards from drill files

This was superseded.

**Daily Practice v2 -- Sequential Sub-Lesson Pipeline (DAILY_PRACTICE_PLAN.md)**
A redesign to use the same MixedReviewScheduler as regular lessons, creating a sequential sub-lesson pipeline. The user would progress through sub-lessons in order, with full mastery tracking. This was designed but superseded before full implementation.

Key concepts from this plan that were carried forward:
- Same mastery rules (VOICE/KEYBOARD count, WORD_BANK does not)
- Position persistence (lessonIndex + subLessonIndex)
- Resume from mastery state

**Daily Practice v3 -- 3-Block Pipeline (2026-05-12, current)**
The current design restored the 3-block structure with 10 cards each:
1. Block 0: "Translations" -- 10 sentence cards from active lesson's sub-lesson
2. Block 1: "Vocabulary" -- 10 vocab flashcards from drill data, filtered by frequency
3. Block 2: "Verbs" -- 10 verb drill cards, filtered by cumulative tenses

Architecture:
- `DailySessionComposer` builds 3 blocks of `List<SentenceCard>` (vocab/verb cards converted to SentenceCard format)
- `DailySessionHelper` tracks block index (0-2)
- `DailyBlocks`/`DailyBlock` data classes hold the composed session
- Blocks are served one at a time to `DailyPracticeScreen` via `getDailyCurrentCards()`
- Screen layer (DailyPracticeScreen, DailyPracticeSessionProvider) is not modified
- Empty blocks are filtered out; packs without vocab/verb data get fewer blocks

### Known Issues and Fixes Applied

**Daily Session Fixes (2026-05-12, branch: feature/daily-cursors)**

Four categories of bugs were identified:

1. **Microphone and Start button on first card** -- TTS does not auto-play on first card (correct behavior), but Start button and mic icon must remain active regardless.

2. **Card counting in blocks 1 and 3** -- Only VOICE/KEYBOARD answers count as practiced. WORD_BANK and simple navigation do not. Cursor between daily sessions advances only when all 10 cards (minus bad words) have received a qualifying answer.

3. **Verb info display in block 3** -- Verb drill cards in the daily practice must show infinitive, tense, and group metadata, matching the standalone Verb Drill screen.

4. **Repeat/Continue navigation** -- "Repeat" shows the same cards from the first session of the day. "Continue" shows the next portion if the cursor has advanced. Card composition stored for 24 hours.

**Known architectural problems (from the fix spec):**
- Two parallel state systems: `DailyPracticeSessionProvider.currentIndex` (Compose) and `DailySessionHelper.taskIndex` (StateFlow) are not synchronized
- Progress is spread across 5 stores: VerbDrillStore, WordMasteryStore, DailyCursorState, MasteryStore, ProgressStore
- TrainingViewModel is 3000+ lines (God Object pattern)

---

## 16.4 State Machine Specifications

### Navigation State Machine (from "Spec -- State Machine.md")

The navigation spec defines a strict state machine for screen transitions. The key rule is the **AppStartRule**: on any app launch (cold start, process recovery, restoration from background), the initial screen is always HOME. `currentScreen` is no longer persisted.

**Valid transitions:**

```
HOME --> LESSON
HOME --> DAILY_PRACTICE
HOME --> VERB_DRILL
HOME --> VOCAB_DRILL
HOME --(Settings)--> LADDER
HOME --(test mode)--> STORY

LESSON --> HOME (back)
LESSON --> TRAINING (start sub-lesson / boss / drill)

TRAINING --> LESSON (exit / sub-lesson complete / boss complete)
TRAINING --(Settings)--> LADDER

DAILY_PRACTICE --> HOME (exit / complete / back)
VERB_DRILL --> HOME (back / exit)
VOCAB_DRILL --> HOME (back / exit)
STORY --> LESSON (complete / close)
LADDER --> {caller} (back)
```

**Prohibited transitions:**
- HOME --> TRAINING (must go through LESSON)
- HOME --> STORY (must go through LESSON, or test mode)
- HOME --> LADDER (must go through Settings)
- App start --> anything other than HOME

**Entry guards:**
- `HOME --> DAILY_PRACTICE`: `startDailyPractice() == true` (cards available)
- `LESSON --> TRAINING`: `currentCard != null` after start
- `any --> LADDER`: no guard (reference screen)

**Session states by mode:**
- Lesson Training: IDLE --> ACTIVE --> COMPLETE --> IDLE
- Daily Practice: IDLE --> ACTIVE --> FINISHED --> COMPLETE_SCREEN
- Verb Drill: LOADING --> SELECTION --> ACTIVE_SESSION --> COMPLETE --> SELECTION
- Vocab Drill: LOADING --> SELECTION --> CARD_SCREEN --> COMPLETE
- Story: IDLE --> ACTIVE --> IDLE
- Ladder: stateless

**What is persisted vs. not:**
- Persisted: languageId, mode, lessonId, currentIndex, sessionState, activePackId, daily lesson/sub-lesson indices
- NOT persisted: currentScreen (removed from progress.yaml), initialScreen (always HOME)

**Migration steps (from the spec):**
1. Remove `currentScreen` from ProgressStore save/load
2. Remove `restoredScreen` logic from TrainingViewModel.init
3. Add transition guards in GrammarMateApp.kt
4. Clean up AppScreen enum (remove ELITE and VOCAB, which redirect to HOME)

Note: ELITE and VOCAB enum values must be retained per CLAUDE.md instruction -- they redirect to HOME if restored from saved state.

### Screen Forms Specification (from "Экранные формы.Спецификация.md")

A comprehensive per-screen element specification covering every UI element across all screens. The document uses a tabular format with columns: Name, Type, Location, Icon, Behavior, Display Condition.

**Screens covered:**
1. StartupScreen (AppRoot) -- 2 elements
2. HomeScreen -- 34 elements across 8 blocks (Header, Primary Action Card, Grammar Roadmap, Drill Tiles, Daily Practice, Legend, Buttons, Dialogs)
3. LessonRoadmapScreen -- 14 elements across 5 blocks (Header, Progress, Tile Grid, Action Button, Dialogs)
4. TrainingScreen -- 70 elements across 18 blocks (TopBar, Session Header, DrillProgressRow, CardPrompt, AnswerBox, ASR Status, Word Bank, Mode Panel, Check, Result, Navigation, Report Sheet, Exit Dialog, TTS Download, Metered Network dialogs, Settings Sheet, TTS Background Progress)
5. TrainingCardSession (shared component) -- slots: DefaultHeader, DefaultProgressIndicator, DefaultCardContent, DefaultResultContent, DefaultNavigationControls, DefaultInputControls, DefaultCompletionScreen
6. DailyPracticeScreen -- 4 sub-screens (Loading, Active Session, CompletionSparkle, CompletionScreen)
7. VerbDrillScreen -- 4 sub-screens (Loading, SelectionScreen, Active Session, CompletionScreen) + VerbReferenceBottomSheet + TenseInfoBottomSheet
8. VocabDrillScreen -- 4 sub-screens (Loading, SelectionScreen, CardScreen with CardFront/CardBack, CompletionScreen)
9. LadderScreen -- single stateless screen
10. Shared Dialogs: WelcomeDialog, Streak Dialog, Boss Reward Dialog, Boss Error Dialog, Story Error Dialog

### Verification Checklist Status (from "Экранные формы.Верификация.md")

A line-by-line audit of the screen forms spec against actual source code, performed 2026-05-12.

**Overall accuracy: ~90% (292/324 elements match)**

| Batch | Screen | Checked | Match | Discrepancies |
|---|---|---|---|---|
| 1 | StartupScreen + HomeScreen | 34 | 34 | 0 |
| 2 | LessonRoadmapScreen | 14 | 13 | 1 |
| 3 | TrainingScreen | 70 | 62 | 8 |
| 4 | TrainingCardSession + DailyPracticeScreen | 75 | 60 | 15 |
| 5 | VerbDrillScreen + VocabDrillScreen | 105 | ~97 | ~8 |
| 6 | LadderScreen + Shared Dialogs | 26 | 26 | 0 |

**Top 5 critical discrepancies (recommended fixes):**

1. **Hint Answer Card, Incorrect, Attempts** -- Described in DefaultInputControls but actually only exist in DailyInputControls and VerbDrillInputControls. Section 4a of the spec incorrectly attributes these to the default component.

2. **Back buttons in VerbDrill/VocabDrill active sessions** -- Spec says they trigger "End session?" dialog. Code calls `exitSession` directly without confirmation.

3. **Enabled conditions for Voice/Keyboard/WordBank buttons** -- Spec says "always" but code sets `enabled = canLaunchVoice` / `canSelectInputMode`. Buttons can be disabled.

4. **VerbDrill CompletionScreen** -- Spec says "uses DefaultCompletionScreen" but code has a fully custom `VerbDrillCompletionScreen`.

5. **Double progress bar in DailyPractice** -- Both a `DailyProgressBar` outside TrainingCardSession and a `DefaultProgressIndicator` inside are shown. Spec does not mention the dual display.

---

## 16.5 Functional Requirements Status

### From FUNCTIONAL_REQUIREMENTS.md (2026-01-16)

The functional requirements document contains ~140 items across 15 sections. Assessment against current implementation:

#### Implemented and Current

| Section | Status | Notes |
|---|---|---|
| 1. Profile (FR-1.1.x) | Implemented | Profile saved in YAML, default name "GrammarMateUser" |
| 2. Data Storage (FR-2.x) | Implemented | AtomicFileWriter, schema versioning, backup/restore |
| 3. Language/Lesson Management (FR-3.x) | Implemented | Multi-language, ZIP import, CSV import, pack management |
| 4. Sub-lessons (FR-4.x) | Implemented | NEW_ONLY and MIXED types, MixedReviewScheduler |
| 5. Training Modes (FR-5.x) | Implemented | LESSON, ALL_SEQUENTIAL, ALL_MIXED modes |
| 6. Answer Normalization (FR-6.x) | Implemented | Whitespace, case, punctuation normalization |
| 7. Progress Persistence (FR-7.x) | Implemented | ProgressStore with all listed fields |
| 8. Mastery/Flowers (FR-8.x) | Implemented | FlowerCalculator with all states, WORD_BANK exclusion |
| 9. Spaced Repetition (FR-9.x) | Implemented | Ebbinghaus curve, interval ladder |
| 13. Streak System (FR-13.x) | Implemented | Daily streak tracking, milestone notifications |
| 14. App Config (FR-14.x) | Implemented | config.yaml with subLessonSize and other settings |
| 15. Manifest/Versioning (FR-15.x) | Implemented | schemaVersion, pack import with updates |

#### Changed from Original Spec

| Section | Original | Current |
|---|---|---|
| 10. Boss Modes (FR-10.x) | ELITE Boss as 7-step cycle | ELITE mode replaced by Daily Practice. `AppScreen.ELITE` retained for compat. Boss LESSON and MEGA remain. |
| 11. Vocab Sprint (FR-11.x) | RU/Target pairs with hard flag | Replaced by Vocab Drill (Anki-style flashcards) with ItalianDrillVocabParser, pack-scoped, SRS-based mastery |
| 12. Story Quiz (FR-12.x) | JSON-based check-in/check-out quizzes | Implemented but not extensively used in current packs. Story tiles replaced by Drill tiles in some contexts. |
| 3.5 Cyclic sub-lesson display | Blocks of 15, cycle restarts | Implementation changed; sub-lesson size defaults to 10, MixedReviewScheduler builds schedules dynamically |

#### New Features Not in Functional Requirements

These features were added after the FR document was written and are not covered:

- **Verb Drill mode** -- Standalone drill with separate ViewModel, CSV parsing, tense/group filtering, batch sessions, daily reset
- **Vocab Drill mode** -- Anki-style flashcards with ItalianDrillVocabParser, SRS mastery steps (Again/Hard/Good/Easy), direction toggle, POS filtering
- **Daily Practice 3-block pipeline** -- Translations + Vocab + Verbs in a single session
- **Pack-scoped drills** -- Manifest-level `verbDrill`/`vocabDrill` sections, per-pack progress storage
- **TrainingCardSession** -- Reusable composable with slot architecture and CardSessionContract interface
- **Per-pack bad words** -- Single pack-keyed YAML for flagged cards, verb drill support
- **Navigation state machine** -- AppStartRule, transition guards, no screen persistence
- **DailyCursorState** -- Offset-based cursor tracking for daily practice sessions
- **TTS/ASR download dialogs** -- Metered network detection, model download progress

---

## 16.6 Design Decisions Log

### Architectural Decisions

| Decision | Date | Rationale | Status |
|---|---|---|---|
| Single ViewModel (TrainingViewModel) for all business logic | Early | Simplicity, centralized state, Compose stateless renderer | Active. Decomposed into helpers in `ui/helpers/` but single ViewModel retained. |
| Verb Drill uses separate ViewModel | 2026-05-09 | TrainingViewModel already 3000+ lines. Isolated feature = zero blast radius | Active |
| TrainingCardSession as reusable composable | 2026-05-10 | Multiple modes (standard training, daily practice, verb drill, vocab drill) need the same card training UI with different data sources | Active |
| Atomic file writes for all persistence | Early | Prevent data corruption on crash during write | Active |
| YAML for all structured storage | Early | Human-readable, versionable, no SQL dependency | Active |
| No server, no database | Early | Offline-first Android app, file-based persistence | Active |
| Packs as ZIP archives | Early | Bundled content with manifest, easy import/export | Active |
| Pack-scoped drills | 2026-05-10 | Different packs may have different drill content. Progress should not leak between packs | Active |

### Design Decisions Later Reversed

| Original Decision | Reversed To | Reason |
|---|---|---|
| Group-based drill (9 cards per group with pauses) | Continuous card stream | Fragmented user experience |
| `type: "verb_drill"` in lesson entries | Top-level `verbDrill`/`vocabDrill` manifest sections | Cleaner separation; lesson entries should only describe lessons |
| Daily Practice as sequential sub-lesson pipeline | 3-block pipeline (translations + vocab + verbs) | Sub-lesson pipeline was too similar to regular lessons; 3 distinct block types provide variety |
| Elite Mode as post-course maintenance | Daily Practice as universal daily session | Elite was overly complex; Daily Practice serves broader audience |
| Vocab Sprint (simple word translation) | Vocab Drill (Anki-style SRS flashcards) | Anki-style is more effective for long-term retention |
| WARMUP sub-lesson type | Removed entirely | Added complexity without proportional benefit |
| `currentScreen` persisted in progress.yaml | Never persisted, always start on HOME | App could start on broken screen after process kill |

### Key Product Decisions

| Decision | Rationale |
|---|---|
| WORD_BANK never counts for mastery | Prevents inflating progress through easy mode |
| 150 unique card shows = 100% mastery | Reasonable threshold for pattern automation |
| Interval ladder in sub-lesson steps, not days | Users practice irregularly; step-based intervals are more predictable |
| Active Set (150) + Reserve Set (150+) per lesson | Prevents phrase memorization while maintaining pattern focus |
| Boss battles optional | Not all users want stress tests; they are a challenge mode, not required |
| Flower metaphor for progress | Visual, intuitive, integrates health/decay (Ebbinghaus) naturally |

---

## 16.7 Gaps and Conflicts

### Where Specs Disagree with Current Implementation

1. **DefaultInputControls in screen forms spec** -- The spec describes Hint Answer Card, Incorrect feedback, and Attempts counter as part of DefaultInputControls. In the code, these elements exist only in DailyInputControls and VerbDrillInputControls. The spec section 4a needs correction.

2. **VerbDrill CompletionScreen** -- The spec states it "uses DefaultCompletionScreen from TrainingCardSession" but the implementation has a fully custom completion screen. The spec is misleading.

3. **Back button behavior in drill sessions** -- The spec says back buttons in VerbDrill/VocabDrill active sessions trigger a confirmation dialog. The code calls `exitSession` directly without confirmation.

4. **Button enabled conditions** -- The screen forms spec lists Voice/Keyboard/WordBank mode selector buttons as "always" enabled, but the code guards them with `canLaunchVoice` and `canSelectInputMode`.

5. **Daily Practice double progress bar** -- The spec does not document that DailyPracticeScreen shows two progress indicators simultaneously (DailyProgressBar outside TrainingCardSession and DefaultProgressIndicator inside).

6. **Sub-lesson display counter** -- The spec describes a global "Exercise X of Y" counter. The code implements a cyclic block counter: `displayIndex = (completed % 15) + 1`, `displayTotal = min(15, total - cycleStart)`. This means at 30 exercises the user sees "Exercise 1 of 15" twice.

7. **Font size of promptRu** -- Spec says "20sp" for training screen prompt, but code scales it: `(20f * state.ruTextScale).sp`. The spec does not mention the text scale slider.

### Missing Specifications for Implemented Features

1. **DailyCursorState** -- The offset-based cursor tracking for daily practice sessions (sentenceOffset, verbOffset) is implemented in the code but not specified in any design document. Only the fix spec (2026-05-12) references it tangentially.

2. **ItalianDrillVocabParser** -- The parser for Italian vocab drill CSVs (nouns, verbs, adjectives with forms, collocations, frequency ranks) is implemented but has no dedicated specification document.

3. **VerbReferenceBottomSheet and TenseInfoBottomSheet** -- These are documented in the screen forms spec but have no dedicated design document explaining the data source, conjugation table structure, or tense info content.

4. **DailyPracticeSessionProvider** -- The adapter that bridges TrainingViewModel state with the TrainingCardSession composable for daily practice. No dedicated design document.

5. **HiddenCardStore** -- Cards can be hidden from lessons. The store and its interaction with session card filtering are not specified.

6. **BackupManager** -- The backup/restore system is mentioned in functional requirements but has no detailed design specification.

7. **Streak system detailed design** -- FR covers requirements but there is no design document for the streak notification logic, milestone messages, or date boundary handling.

### Features Implemented but Never Specified

1. **Speedometer (WPM arc)** -- The circular Canvas arc showing words-per-minute on TrainingScreen. Appears in the screen forms spec as a documented element but has no design document explaining the calculation, color thresholds (red <= 20, yellow <= 40, green > 40), or UX rationale.

2. **Test mode** -- A toggle in settings that enables direct STORY access from HOME. Mentioned in functional requirements and the state machine spec but never formally designed.

3. **Ru text scale slider** -- A settings slider (1.0-2.0x) that scales Russian text size. Not specified in any design document.

4. **TTS speed slider** -- Pronunciation speed control (0.5-1.5x). Not specified.

5. **ASR (speech recognition) integration** -- Offline ASR using Sherpa-ONNX with download dialogs and metered network detection. Mentioned in CLAUDE.md but has no dedicated design specification.

6. **Boss reward system** -- Bronze/Silver/Gold medals are implemented and shown in dialogs, but the reward calculation logic and display rules are only in functional requirements, not a design spec.

7. **Pack import validation** -- LessonStore handles various edge cases during pack import (BOM removal, missing files, version comparison) that are not captured in design documents.
