# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# GrammarMate (BaseGrammy) — AI Agent Context

---

## PROJECT CONTEXT

**What:** Android language learning app that builds automatic grammar pattern skills for RU→Target translation (English/Italian). Flower-growing metaphor for progress visualization based on Ebbinghaus forgetting curve.
**Stack:** Kotlin 1.9.22, Jetpack Compose (BOM 2024.02.00), Material 3, Android SDK 24–34, Java 17, SnakeYAML 2.2, Sherpa-ONNX (TTS/ASR)
**Run:** Local Gradle build via `java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain` (Windows wrapper workaround required). No Docker, no server. APK installed on device/emulator.

### Domain glossary

| Term | Meaning |
|------|---------|
| Sub-lesson | Chunk of ~10 cards within a lesson. Types: `NEW_ONLY` (fresh), `MIXED` (new + spaced review) |
| Main pool | First 150 cards of a lesson — used for mastery measurement |
| Reserve pool | Cards beyond 150 — used in review/mix to prevent memorization of specific phrases |
| Mastery | Number of unique cards practiced with VOICE or KEYBOARD. WORD_BANK does NOT count |
| Flower state | Visual progress indicator: LOCKED → SEED → SPROUT → BLOOM → (WILTING → WILTED → GONE if neglected) |
| Interval ladder | Spaced repetition schedule in days: [1, 2, 4, 7, 10, 14, 20, 28, 42, 56] |
| Boss battle | End-of-lesson challenge testing pattern stability under pressure |
| Daily Practice | Unified session with 3 blocks: 10 translations, 5 vocab flashcards (Anki-style), 10 verb conjugations. Replaces former Elite mode and Vocab Sprint. |
| Lesson pack | ZIP archive containing `manifest.json` + CSV lesson files, imported via Settings |
| Active Set / Reserve Set | Two 150-card pools per lesson that rotate to prevent phrase memorization |

### Critical gotchas

- **Windows Gradle wrapper is broken** — must use `java -cp` with all 3 JARs, never plain `gradlew`
- **WORD_BANK mode never counts for mastery** — only VOICE and KEYBOARD grow flowers. Violating this inflates progress
- **WARMUP sub-lesson type was removed** — never re-introduce it. Only `NEW_ONLY` and `MIXED` exist
- **All file writes must go through `AtomicFileWriter`** — temp → fsync → rename. No direct File.writeText
- **`TrainingViewModel` is 3000+ lines** — the single ViewModel for ALL business logic. Changes here have high blast radius. Decompose into helpers in `ui/helpers/` when adding new domain logic.
- **Project path contains Cyrillic** (`Разработка`) — `android.overridePathCheck=true` in gradle.properties is required
- **Drill visibility is pack-scoped** — `hasVerbDrill`/`hasVocabDrill` check the active pack's manifest, not all installed packs. A pack without `verbDrill`/`vocabDrill` sections shows no drill tiles.
- **`AppScreen.ELITE` and `AppScreen.VOCAB` enum values are kept for backward compat** — they redirect to HOME if restored from saved state. Do not remove them; removing would crash users with saved `currentScreen: "ELITE"` or `"VOCAB"` values.

---

## RULE HIERARCHY

When rules conflict, priority goes in this order:

**Level A — Safety & correctness**
Never perform destructive actions (force push, reset --hard, delete branches). If uncertain — stop and explain.
Correctness beats speed. Transparency beats silence.
Any deviation from Level B–D rules must be acknowledged explicitly in the response to the user, not only in thinking.

**Level B — Architecture integrity**
Never violate the single-ViewModel pattern. UI (`GrammarMateApp.kt`) must remain a stateless renderer.
Data layer stores must use atomic file writes. A working but architecturally wrong solution is not acceptable.

**Level C — Process rules**
Subagent workflow for non-trivial tasks, heavy-output restrictions, git branch rules.
Follow unless Level A or B requires deviation — in that case, explain why in your response.

**Level D — Preferences**
Simplicity over cleverness, minimal diffs, testability.
Kotlin idioms, Compose best practices. Apply when all higher levels are satisfied.

---

## EXECUTION MODE

### Mandatory decomposition triggers

Decompose into subagents when ANY of the following is true:
- Task touches ≥ 3 files
- Task adds ≥ 1 new class or ≥ 3 new functions
- Task requires reading a spec doc AND modifying code
- Task involves TrainingViewModel (high blast radius)
- Estimated implementation > 30 lines of new code
- Task has sequential dependencies (step B needs step A's output)

Do NOT attempt these in a single agent or main context.
The cost of decomposition is low. The cost of a failed large agent is high.

### Step 0 — Always run Assessment first

Before any non-trivial task, spawn **one Assessment subagent**.

The Assessment subagent:
- reads the task and relevant files
- identifies: affected layers, unknowns, risks, inter-part dependencies
- estimates complexity: simple / moderate / complex
- returns a structured verdict to main

**Assessment MUST NOT call TeamCreate or spawn any agents.**
**All spawning happens in main context after verdict is received.**

**Assessment output format:**
```
VERDICT: SUBAGENTS | TEAM
AGENTS: N (min 2)
COMPLEXITY: simple | moderate | complex
LAYERS AFFECTED: [list]
UNKNOWNS: [list]
RISKS: [list]
REASONING: [brief explanation]

DECOMPOSITION PLAN:
Wave 1 (N agents, max 5):
  - Step 1.1: [atomic action — 1 file / 1 function / 1 concept]
  - Step 1.2: [atomic action]
  - Step 1.3: [atomic action]
Wave 2 (N agents, max 5):
  - Step 2.1: [depends on Wave 1 results]
  ...
DEPENDENCIES: [what Wave N+1 needs from Wave N]
```

Skip Assessment only for: plain conversation, questions from loaded context, 1–3 tool call tasks, file creation with known path.

**Atomic step definition:** a task executable by one agent without needing results from other parallel agents in the same wave. Criteria: modifies ≤ 2 files, adds ≤ 1 new function or class, contains ≤ 50 lines of new code, has a single clear success check.

---

### Step 1 — Choose mode based on verdict

**Layer split matters more than agent mode.** The key decision is HOW to decompose (by layers or by independent tasks). Agent mode (TEAM vs SUBAGENTS) is secondary.

```
SUBAGENTS (simple, low overhead)       TEAM (structured, medium overhead)
─────────────────────────────          ─────────────────────────────
Goal: WIDTH + simplicity               Goal: DEPTH + task dependencies
Independent or sequential.             Named agents + TaskList + blockedBy.
Main spawns, waits, collects.          TeamCreate + TaskCreate + SendMessage.

Best for:                              Best for:
- read-only analysis / audits          - 3+ layers with real dependencies
- new features (all new files)         - parallel agents at same layer
- single-layer refactors               - tasks needing enforced ordering
- sequential layer work (data→ui)      - when you want named accountability

Overhead: 1 action per agent           Overhead: ~10 actions per session
```

**Default: SUBAGENTS.** Use TEAM only when TaskList dependencies provide real value (agent B cannot start until agent A finishes).

**TEAM experience note:** In practice, SendMessage coordination was rarely used — agents read files directly. TEAM's main value was TaskList with `blockedBy` enforcement, not inter-agent messaging. If you can enforce ordering in main context (spawn agent A → wait → spawn agent B), SUBAGENTS is simpler and equally effective.

**Combined pattern (highest-stakes tasks):**
Round 1 → SUBAGENTS explore → score → pick winner
Round 2 → TEAM implements with layer coordination

---

### Step 2 — Spawn with shared context, different kick-starts

Assessment analysis goes into EVERY agent prompt.
Each prompt reframes it from a different angle.

**Subagents — same analysis, different angle:**
```
agent-1: "[assessment] — Find risks, edge cases, failure modes."
agent-2: "[assessment] — Design the solution. Architecture first."
agent-3: "[assessment] — Simplify ruthlessly. Minimal correct solution."
```

**Team — same analysis, different role:**
```
agent-1: "[assessment] — ARCHITECT. Propose structure. Others challenge you."
agent-2: "[assessment] — IMPLEMENTER. Build it. Push back on anything
          that doesn't work in practice. Use SendMessage."
agent-3: "[assessment] — REVIEWER. Attack both. Find what they missed.
          Use SendMessage to challenge their decisions."
```

Framing IS the role. Do not assign roles top-down.

**Role conflict (team only):** first SendMessage wins the role.
The other picks a different role or goes idle.

### Decomposition strategy: LAYERS + TEAM for refactoring (MANDATORY)

**The key insight: layer-based decomposition pairs naturally with TEAM mode.**
When work cascades across layers (data → helpers → UI), agents need to know what the previous layer changed. TEAM mode gives them SendMessage for coordination. SUBAGENTS leaves them blind.

**LAYER-TEAM** (default for any task touching ≥2 layers):
```
DATA-AGENT: data/    → Models.kt, stores, parsers, calculators
HELPERS-AGENT: ui/helpers/ → all helper files (waits for DATA-AGENT summary)
UI-AGENT: ui/        → TrainingViewModel, GrammarMateApp, screens (waits for HELPERS-AGENT summary)
```
- Sequential execution with SendMessage coordination between layers
- DATA-AGENT finishes → sends summary of changed APIs → HELPERS-AGENT adapts
- HELPERS-AGENT finishes → sends summary → UI-AGENT adapts
- Real build checkpoints after each agent (data compiles alone → data+helpers → full build)
- Zero file conflicts by design

**WAVE-SUBAGENTS** (only for independent tasks with zero file overlap):
- Parallel agents, no inter-agent communication
- Only valid when no two agents touch the same file
- Good for: read-only analysis, adding independent features, separate bug fixes

**When to use which:**

| Scenario | Strategy | Mode |
|----------|----------|------|
| Type migration (20+ files) | LAYERS | TEAM |
| Refactoring shared interfaces | LAYERS | TEAM |
| Adding new feature (new files) | WAVES | SUBAGENTS |
| Architecture audit | WAVES | SUBAGENTS |
| Bug fix in one layer | Single agent | N/A |
| Mixed (some layers change, some don't) | LAYERS for affected only | TEAM |
| Bug fix in one layer | Single agent |

**Hard rules:**
1. Never assign the same file to two agents
2. Never use worktrees for write operations (changes can be silently lost on auto-cleanup)
3. Max 3 implementation agents; 5 for read-only analysis
4. After each layer agent: commit → build check → SendMessage summary to next layer → next agent
5. Trivial tasks (< 3 files, < 30 lines) → single agent, no decomposition
6. TEAM mode is for coordination, not just "quality through conflict" — layer coordination is an equally valid TEAM use case
7. Be flexible: Assessment decides strategy, not dogma. Mix approaches if the task demands it.

**Checkpoint (required after each layer agent):**
1. List result: ✓ / ✗
2. Commit changes
3. Run build check (via subagent)
4. SendMessage summary of changed APIs to next layer's agent
5. Report to user
6. Fix failures BEFORE spawning next agent

---

### Step 3 — Evaluate and choose

**Subagents:** score each proposal, present winner with brief comparison.

| Criterion | 0 | 1 | 2 |
|---|---|---|---|
| Minimal changes | Large diff, many files | Moderate diff | Targeted, minimal diff |
| Architecture consistency | Violates layer rules | Minor deviation | Clean, fits layers |
| Simplicity | Over-engineered | Acceptable | Simple and clear |
| Testability | Hard to test | Testable with effort | Easy to test |
| Correctness | Wrong or incomplete | Partially correct | Fully correct |

**Max score: 10. On tie — pick simpler.**

**Team:** results are combined. Team lead assembles pieces.

---

### Tools by mode

**Subagents** (Agent tool only):
- `Agent(prompt)` — spawn, run, return result, die
- No inter-agent communication. Main collects all results.

**Team** (full tool set):
- `TeamCreate(name)` — create team infrastructure
- `Agent(team_name, name, prompt)` — spawn named persistent agent
- `SendMessage(agent_name, message)` — peer-to-peer during execution

---

### Report your mode choice

Before spawning, announce verdict and mode. User can override.
> "Assessment: TEAM (3 agents) — crosses layer boundaries, unknown integration points, high risk of violations"
> "Assessment: SUBAGENTS (2 agents) — single layer, solution space open"

---

## MANDATORY: Main context is coordination only

Main context NEVER performs implementation work.
Main context ONLY:
- Reads Assessment output
- Announces decomposition plan to user
- Spawns agents
- Collects and summarizes agent results
- Makes go/no-go decisions between waves

If you catch yourself writing code, editing files, or running build commands in main context — STOP. Spawn a subagent.

**ONE tool call per main context action (spawn or read). Chaining tool calls in main = implementation leak = violation.**
**It is forbidden to skip this rule to save time or because the task seems simple.**

---

## HEAVY-OUTPUT COMMANDS — always via subagent

ALWAYS spawn a subagent. NEVER execute directly.

- `assembleDebug` / `assembleRelease` (Gradle builds)
- `test` (Gradle test suite)
- `git log` with large ranges
- `pack_validator.py` output

For git commit: spawn a subagent to stage and commit.
Relay only the commit hash and summary back to main.

If you accidentally flood main context — acknowledge the mistake, do not attempt to summarize. Continue.

---

## FAILOVER BEHAVIOR

| Situation | Action |
|---|---|
| Java/Gradle not available | Report it. Suggest checking JAVA_HOME and Android SDK path. Do not proceed with build tasks. |
| Architecture skill unavailable | Report it. Do not proceed with structural changes. |
| Subagent execution impossible | Do the work in main context. Acknowledge explicitly. **Except heavy-output — always wait for subagent.** |
| Tests failing unrelated to task | Note it. Do not block the task. Report in summary. |
| Command returns incomplete output | Re-run with explicit flags. If still incomplete — report partial result. |
| `gradlew` fails with NoClassDefFoundError | Use `java -cp` workaround with all 3 wrapper JARs (see Build Commands) |

---

## GIT WORKFLOW

1. Features in separate branches: `feature/xxx`
2. Main branch: `main`
3. CLAUDE.md changes are code changes — they go on feature branches too
4. **NEVER commit code changes to `main`.**
5. Before any changes: `git branch --show-current`. If on `main` — create or switch to a feature branch.
6. Commit footer: `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`
7. **NEVER push without explicit user approval.** Wait for user to test before any remote operations.
8. Before starting new feature work: `git status` — if dirty, commit or stash first. Then create a fresh branch from main: `git checkout main && git pull && git checkout -b feature/xxx`

---

## Build & Test Commands

Gradle wrapper on Windows requires a multi-JAR classpath workaround:

```bash
# Alias for all Gradle commands (Windows):
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain

# Build debug APK → app/build/outputs/apk/debug/grammermate.apk
... assembleDebug

# Run all unit tests
... test

# Single test class
... test --tests "com.alexpo.grammermate.data.MixedReviewSchedulerTest"

# Single test method
... test --tests "com.alexpo.grammermate.data.FlowerCalculatorTest.testBloomState"

# Validate lesson pack ZIPs
python tools/pack_validator/pack_validator.py path/to/pack.zip
```

---

## Architecture

### MVVM — Single ViewModel

`TrainingViewModel` (AndroidViewModel) is the sole ViewModel. It holds all training state in a `StateFlow<TrainingUiState>` and handles every domain action. `GrammarMateApp.kt` is a pure stateless renderer that collects the state flow and dispatches actions.

```
MainActivity → AppRoot (restore check) → GrammarMateApp (Compose UI)
                   ↕
            TrainingViewModel (all business logic + state)
                   ↕
    data/ layer (stores, parsers, calculators)
```

### Data Layer (`data/`)

All stores read/write YAML/CSV files in `context.filesDir/grammarmate/`. Key files:

| File | Purpose |
|------|---------|
| `Models.kt` | Data classes: `Lesson`, `SentenceCard`, `VocabEntry`, `TrainingUiState`, enums |
| `LessonStore.kt` | Lesson pack import (ZIP), seed data, language management, pack-scoped drill import/query |
| `LessonPackManifest.kt` | Pack manifest data classes including `DrillFiles`, `verbDrill`/`vocabDrill` optional sections |
| `VerbDrillStore.kt` | Verb drill progress, scoped per-pack at `drills/{packId}/verb_drill_progress.yaml` |
| `WordMasteryStore.kt` | Vocab drill word mastery, scoped per-pack at `drills/{packId}/word_mastery.yaml` |
| `ItalianDrillVocabParser.kt` | Parses Italian vocab drill CSVs (nouns, verbs, adjectives, etc.) |
| `MasteryStore.kt` | Per-lesson card show tracking (uniqueCardShows, shownCardIds) |
| `ProgressStore.kt` | Training session position/state |
| `SpacedRepetitionConfig.kt` | Ebbinghaus forgetting curve math, interval ladder [1,2,4,7,10,14,20,28,42,56 days] |
| `FlowerCalculator.kt` | Maps mastery+health → flower state (LOCKED/SEED/SPROUT/BLOOM/WILTING/WILTED/GONE) |
| `MixedReviewScheduler.kt` | Builds sub-lesson schedules with NEW_ONLY and MIXED types |
| `LessonLadderCalculator.kt` | Determines lesson unlock order |
| `BackupManager.kt` | Backup/restore to Downloads/BaseGrammy/ |
| `TtsEngine.kt` / `TtsModelManager.kt` | Sherpa-ONNX TTS (offline, AAR in `libs/`) |
| `AsrEngine.kt` / `AsrModelManager.kt` | Speech recognition |
| `CsvParser.kt` / `VocabCsvParser.kt` | Parse lesson CSV content |
| `YamlListStore.kt` | Generic YAML-backed list storage with atomic writes |

### UI Layer (`ui/`)

| File | Purpose |
|------|---------|
| `GrammarMateApp.kt` | Screen router: `GrammarMateApp()` composable, `AppScreen` enum, dialog orchestration, BackHandlers |
| `TrainingViewModel.kt` | All business logic: session management, answer validation, word bank, mastery, boss/daily practice modes |
| `DailyPracticeScreen.kt` | Daily Practice UI: 3-block session renderer (sentence translation, vocab flashcard, verb conjugation) |
| `AppRoot.kt` | Entry point — checks backup restore status before showing main app |
| `Theme.kt` | Material 3 theme |
| `screens/*.kt` | Per-screen Composable files (HomeScreen, TrainingScreen, LessonRoadmapScreen, etc.) |
| `components/*.kt` | Shared Composable components (dialogs, reusable UI) |
| `helpers/*.kt` | ViewModel domain helpers (BossHelper, VocabHelper, DailySessionHelper, DailySessionComposer, DailyPracticeSessionProvider, etc.) — plain classes, NOT ViewModels |

### Lesson Content

Content ships as ZIP "lesson packs" imported via Settings. Each pack contains a `manifest.json` + CSV files. Default packs are bundled in `assets/grammarmate/packs/`. CSV format: rows with Russian prompt + accepted target-language answers.

**Adding a new lesson pack to the build:**
1. Place the ZIP in `assets/grammarmate/packs/`
2. Add a `DefaultPack` entry to the `defaultPacks` list in `LessonStore.kt`
3. Both steps are required — the ZIP in assets alone is not enough

**Manifest format:**
```json
{
  "packId": "...",
  "schemaVersion": 1,
  "language": "it",
  "lessons": [...],
  "verbDrill": { "files": ["verb_drill.csv"] },
  "vocabDrill": { "files": ["drill_nouns.csv", "drill_verbs.csv"] }
}
```
- `verbDrill` and `vocabDrill` are optional top-level sections. Omit them for packs without drill content.
- Lesson entries with `type: "verb_drill"` are filtered during import — the CSV is NOT parsed as a standard lesson.
- On import, drill files are copied to `grammarmate/drills/{packId}/verb_drill/` and `grammarmate/drills/{packId}/vocab_drill/`.
- Progress is scoped per-pack: `grammarmate/drills/{packId}/verb_drill_progress.yaml` and `word_mastery.yaml`.
- Drill tiles on HomeScreen are visible only when the active pack declares the corresponding drill section.
- Drill ViewModels accept `packId` via `reloadForPack(packId)` to load pack-scoped data.
- Standard lesson CSV: 2 columns `ru;answers` (answers separated by `+`), no header row.
- Verb drill CSV: header row `RU;IT;Verb;Tense;Group` (RU and IT required; Verb, Tense, Group optional).
- Vocab drill CSV: language-specific format parsed by `ItalianDrillVocabParser`.

### File Size & Decomposition Guidelines

**Hard limits — decompose when exceeded:**

| Layer | Max lines | Action when exceeded |
|-------|-----------|---------------------|
| Screen file (Compose) | 1000 | Extract sub-composables to component files in `ui/components/` |
| ViewModel | 1200 | Extract domain helpers to `ui/helpers/` |
| Data store | 500 | Extract parsers or calculators to separate files in `data/` |
| Data class (single class) | 30 fields | Group related fields into nested data classes |

**Decomposition rules:**

1. **Screen files go in `ui/screens/`** — one file per major screen (Home, Lesson, Training, DailyPractice, Story, Ladder, Settings). Helper composables used only by that screen stay in the same file. Dialog composables triggered from navigation stay in GrammarMateApp.kt.
2. **Shared components go in `ui/components/`** — composables used by 2+ screens (TTS/ASR download dialogs, welcome dialog).
3. **ViewModel helpers go in `ui/helpers/`** — plain Kotlin classes that implement domain logic (boss, vocab, drill, elite, TTS/ASR, word bank). They receive a `TrainingStateAccess` interface (NOT ViewModels — no lifecycle). Helpers never call other helpers directly; all coordination flows through TrainingViewModel.
4. **NEVER create a second ViewModel.** Helpers are owned by TrainingViewModel. The single-ViewModel pattern is a Level B constraint.
5. **GrammarMateApp.kt is a router.** It contains only `GrammarMateApp()` (screen routing, dialog state, BackHandlers), `AppScreen` enum, and dialog orchestration. All screen rendering is delegated to `ui/screens/`.
6. **Helper dependency pattern:** helpers take `TrainingStateAccess` as a constructor parameter (defined in `DailySessionHelper.kt`):
   ```kotlin
   interface TrainingStateAccess {
       val uiState: StateFlow<TrainingUiState>
       fun updateState(transform: (TrainingUiState) -> TrainingUiState)
       fun saveProgress()
   }
   ```
   TrainingViewModel provides this via an anonymous object. Helpers call `updateState { }` and `saveProgress()` through it.
7. **Screen-scoped reset functions:** the ViewModel should have private `resetBossState()`, `resetVocabState()`, etc. functions that zero out the relevant fields in one `copy()` call. This gives encapsulation without nested data classes.
8. **New feature fields:** if a feature adds 5+ fields to TrainingUiState, group them into a nested data class from the start. Do not retrofit the existing flat structure (deferred until ViewModel is under 1500 lines).
9. **Extract in phases:** bug fixes first → UI file extraction (low risk) → ViewModel helper extraction (medium risk). Verify build after each extraction.

### App Config

`assets/grammarmate/config.yaml` — runtime flags (testMode, eliteSizeMultiplier, vocabSprintLimit).

---

## Decomposition Example

**BAD** (single large agent):
```
Agent: "Add boss battle scoring to TrainingViewModel"
→ 200+ line diff, high blast radius, hard to review
```

**GOOD** (wave decomposition):
```
Wave 1 (research):
  Agent 1.1: "Read 08-training-viewmodel.md + scenario-09-boss-battle.md.
              Return: list of existing boss state fields in TrainingUiState"
  Agent 1.2: "Read TrainingViewModel.kt lines 1–500.
              Return: current boss battle entry point functions"
  Agent 1.3: "Read BossHelper.kt. Return: current scoring logic gaps"

Wave 2 (implementation, after Wave 1 collected):
  Agent 2.1: "Add BossScoreState data class to Models.kt. ≤ 10 fields."
  Agent 2.2: "Add score calculation to BossHelper.kt. No ViewModel changes."

Wave 3 (wiring):
  Agent 3.1: "Wire BossScoreState into TrainingViewModel via updateState{}.
              Change only the boss-related section."
  Agent 3.2: "Update TrainingScreen.kt to display score."
```

---

## SPECIFICATION & DOCUMENTATION

All project documentation is in `docs/specification/`. When working on a specific component, read the corresponding spec section first.

### Document map by component

| Component | Spec file | When to read |
|-----------|-----------|-------------|
| Data models, enums, state | 01-models-and-state.md | Any change to Models.kt, VocabWord.kt, VerbDrillCard.kt |
| Any data store | 02-data-stores.md | Changes to MasteryStore, ProgressStore, VerbDrillStore, WordMasteryStore, etc. |
| Spaced repetition, flowers | 03-algorithms-and-calculators.md | SRS logic, FlowerCalculator, MixedReviewScheduler, Normalization |
| CSV parsing | 04-parsers.md | Lesson content import, CSV format changes |
| TTS / ASR | 05-audio-tts-asr.md | Audio playback, speech recognition, model downloads |
| AtomicFileWriter, BackupManager | 06-infrastructure.md | File I/O, backup/restore, AppConfigStore |
| GrammarMateApp navigation | 07-app-router.md | Screen routing, dialog orchestration, BackHandlers |
| TrainingViewModel | 08-training-viewmodel.md | Session management, answer validation, word bank, boss battles |
| Daily Practice | 09-daily-practice.md | 3-block session, cursor, block transitions |
| Verb Drill | 10-verb-drill.md | Conjugation practice, CardSessionProvider |
| Vocab Drill | 11-vocab-drill.md | Flashcard SRS, Anki-style rating, word mastery |
| TrainingCardSession | 12-training-card-session.md | Reusable card UI, slots, input controls |
| App entry, state restoration | 13-app-entry-and-navigation.md | MainActivity, AppRoot, init sequence |
| Theme, sounds, strings | 14-theme-and-ui-components.md | Material 3 theme, shared composables |
| Lesson packs, CSV formats | 15-lesson-content-and-packs.md | Pack import/export, manifest format |
| All prior specs history | 16-existing-specs-consolidation.md | Design evolution, gaps, conflicts |
| User stories & use cases | 17-user-stories-and-use-cases.md | Feature requirements, edge cases |
| Learning methodology | 18-learning-methodology.md | Ebbinghaus curve, flower metaphor, scheduling |
| All screens & dialogs | 19-screen-catalog.md | Screen layouts, wireframes, interactions |
| Performance, offline, security | 20-non-functional-requirements.md | NFRs, scalability, compatibility |
| Use cases & acceptance criteria | 22-use-case-registry.md | Any feature verification, regression testing, PR review |
| Screen elements & invariants | 23-screen-elements.md | UI changes, element behavior verification, regression testing |

### Architecture docs

| Document | Purpose |
|----------|---------|
| arch-audit-dependencies.md | Component dependency map, state duplication analysis |
| arch-audit-spec-vs-code.md | Spec vs code discrepancies (24 found) |
| arch-module-decomposition.md | Proposed modular architecture (11 modules) |

### Scenario verification (code traces)

| Scenario | File |
|----------|------|
| Lesson training flow | scenario-01-training-flow.md |
| Answer validation | scenario-02-answer-validation.md |
| Mastery & flower progression | scenario-03-mastery-flower.md |
| Spaced repetition scheduling | scenario-04-spaced-repetition.md |
| Input modes (voice/keyboard/wordbank) | scenario-05-input-modes.md |
| Daily practice session | scenario-06-daily-practice.md |
| Verb drill standalone | scenario-07-verb-drill.md |
| Vocab drill standalone | scenario-08-vocab-drill.md |
| Boss battle | scenario-09-boss-battle.md |
| Pack-scoped drills | scenario-10-pack-drills.md |
| Navigation flow | scenario-11-navigation.md |
| State persistence | scenario-12-state-persistence.md |
| Lesson pack import | scenario-13-pack-import.md |
| Backup & restore | scenario-14-backup-restore.md |
| First launch & onboarding | scenario-15-onboarding.md |

### Tech Debt

- **Difficulty mode (HintLevel) not wired to parenthetical hints:** `HintLevel` enum exists (EASY/MEDIUM/HARD) and controls Word Bank / tense label visibility, but does NOT strip parenthetical target-language insertions from `promptRu` in the card body. Example: `я говорю (dire) правду (verità)` — the `(dire)` and `(verità)` are always visible regardless of difficulty. Spec updated in `21-product-roadmap.md` section 2. Implementation needed: strip `\s*\([^)]+\)` from card body `promptRu` when `hintLevel != EASY`. Boss Battle should force `HintLevel.HARD`.
- **UC/Element cross-references incomplete:** `23-screen-elements.md` has "Related UC" column mostly as "?". Needs linking to UC-IDs from `22-use-case-registry.md`.

### Roadmap docs

- `docs/specification/21-product-roadmap.md` — next sprint features (Card Feel Rating, Difficulty Levels)
- `docs/superpowers/plans/arch-review-execution-plan.md` — architecture fixes (4 batches, not started)
- `docs/specification/legacy-test-plan.md` — unit test backlog (~200 tests, all TODO)
- `.claude/skills/regression-check.md` — regression verification skill (diff → UC/element check → PASS/FAIL)

### Rule: Read spec before modifying code

Before modifying any component listed above, read the corresponding spec file. If the spec and code disagree, the CODE is the source of truth (specs may be outdated). Update the spec after code changes.

### Rule: Update changelog on spec changes

Whenever a specification file is created or modified, add an entry to `docs/specification/CHANGELOG.md` with:
- Date
- Version (matches app versionName)
- Which file(s) changed
- What changed (created / updated section X / fixed discrepancy Y)
- Commit hash

### Rule: Run regression check after code changes

After any non-trivial code change (touches ≥ 2 files or modifies ViewModel/helpers), run regression verification:
1. Use `/regression-check` skill, OR
2. Manually: read `22-use-case-registry.md` → find affected UCs → verify ACs against code
3. Read `23-screen-elements.md` → find affected elements → verify invariants

Report results as PASS/FAIL per AC and element before marking task complete.

### Rule: Behavioral parity for ported UI elements

When porting a UI element from a reference screen to an adopter screen:
1. Spec MUST contain a behavioral contract table (Action | System Response | User Outcome) for every configuration state
2. AC MUST include at least 1 BEHAVIORAL criterion per ported element (not just "button visible")
3. Regression MUST diff the adopter's callback wiring against the reference implementation
4. AC MUST NOT use "matches [ScreenName] pattern" without specifying the observable outcome
5. Checklist MUST include a "Non-Default Configuration" row testing behavior at MEDIUM, HARD, non-default input modes
