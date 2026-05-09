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
| Elite mode | Infinite mixed review for long-term skill maintenance |
| Vocab sprint | Vocabulary drill mode with 5-option word bank (1 correct + 4 distractors) |
| Lesson pack | ZIP archive containing `manifest.json` + CSV lesson files, imported via Settings |
| Active Set / Reserve Set | Two 150-card pools per lesson that rotate to prevent phrase memorization |

### Critical gotchas

- **Windows Gradle wrapper is broken** — must use `java -cp` with all 3 JARs, never plain `gradlew`
- **WORD_BANK mode never counts for mastery** — only VOICE and KEYBOARD grow flowers. Violating this inflates progress
- **WARMUP sub-lesson type was removed** — never re-introduce it. Only `NEW_ONLY` and `MIXED` exist
- **All file writes must go through `AtomicFileWriter`** — temp → fsync → rename. No direct File.writeText
- **`TrainingViewModel` is 2000+ lines** — the single ViewModel for ALL business logic. Changes here have high blast radius
- **Project path contains Cyrillic** (`Разработка`) — `android.overridePathCheck=true` in gradle.properties is required

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
```

Skip Assessment only for: plain conversation, questions from loaded context, 1–3 tool call tasks, file creation with known path.

---

### Step 1 — Choose mode based on verdict

```
SUBAGENTS                           TEAM
─────────────────────────────       ─────────────────────────────
Goal: WIDTH                         Goal: DEPTH + CONFLICT
Independent parallel solutions.     Role-based argumentation.
Each agent produces a full result.  Agents dispute and self-organize.
Main scores and picks the winner.   Team lead assembles final result.

Best for:                           Best for:
- unknown solution space            - complex execution, known direction
- parallel alternatives needed      - cross-layer changes
- fire-and-forget exploration       - high-stakes features
                                    - when internal conflict = quality
```

**Combined pattern (highest-stakes tasks):**
Round 1 → SUBAGENTS explore → score → pick winner
Round 2 → TEAM implements with full role conflict

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

## MANDATORY: Subagent-based workflow

Every request that requires working with code, files, or system operations must be handled through subagents.
NEVER perform such work directly in the main context.

**It is forbidden to execute 2 or more tool calls in the main context.**
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
| `LessonStore.kt` | Lesson pack import (ZIP), seed data, language management |
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
| `GrammarMateApp.kt` | All Compose screens: training, roadmap, settings, word bank, boss, elite, vocab sprint |
| `TrainingViewModel.kt` | All business logic: session management, answer validation, word bank, mastery, boss/elite modes |
| `AppRoot.kt` | Entry point — checks backup restore status before showing main app |
| `Theme.kt` | Material 3 theme |

### Lesson Content

Content ships as ZIP "lesson packs" imported via Settings. Each pack contains a `manifest.json` + CSV files. Default packs are bundled in `assets/grammarmate/packs/`. CSV format: rows with Russian prompt + accepted target-language answers.

### App Config

`assets/grammarmate/config.yaml` — runtime flags (testMode, eliteSizeMultiplier, vocabSprintLimit).
