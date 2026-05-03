# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

All thinking and reasoning must be in English.

# GrammarMate (BaseGrammy) — AI Agent Context

---

## PROJECT CONTEXT

**What:** Android language learning app with spaced repetition, mastery visualization (flowers), vocabulary sprints, and challenge modes
**Stack:** Kotlin 1.9.22, Jetpack Compose, Android SDK 34 (minSdk 24), Gradle 8.x (AGP 8.2.2), SnakeYAML, Compose compiler 1.5.8, Java 17
**Run:** Local Gradle — `./gradlew assembleDebug` produces APK at `app/build/outputs/apk/debug/grammermate.apk`

### Domain glossary

| Term | Meaning |
|------|---------|
| Flower | Mastery visualization state: seed→sprout→bloom→wilting→wilted→gone, driven by Ebbinghaus forgetting curve |
| Sub-lesson | A chunk of ~10 cards within a lesson; types: NEW_ONLY, MIXED |
| Word Bank | Tap-to-build input mode — does NOT count for flower mastery |
| Vocab Sprint | Vocabulary exercise mode with always-exactly-5 multiple-choice options |
| Boss / Elite | Challenge modes with higher difficulty settings |
| Lesson Pack | ZIP archive with `manifest.json` + CSV lesson files + optional vocab CSV and story quiz JSON |
| Mastery | Progress metric: `uniqueCardShows / 150` capped at 1.0 — only VOICE/KEYBOARD input modes contribute |
| Spaced repetition | Review intervals `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` days |

### Critical gotchas

- Word Bank mode does NOT increase mastery — only VOICE and KEYBOARD do. `recordCardShowForMastery()` skips WORD_BANK.
- WARMUP sub-lesson type was removed — do NOT reintroduce it.
- Spaced repetition intervals are fixed constants in `SpacedRepetitionConfig.kt` — do not change without justification.
- CSV uses `;` as delimiter (not `,`). Lesson CSV format: `ru;answers` with multiple answers separated by `+`.
- `TrainingViewModel.kt` is 2000+ lines — single controller for all training logic. Changes ripple wide.
- All persistence uses YAML/CSV on device storage — no Room/SQL database.
- Vocab word bank must always have exactly 5 options. If current lesson has <4 distractors, fetch from other lessons.

---

## RULE HIERARCHY

When rules conflict, priority goes in this order:

**Level A — Safety & correctness**
Never perform destructive actions. If uncertain — stop and explain.
Correctness beats speed. Transparency beats silence.
Any deviation from Level B–D rules must be acknowledged explicitly
in the response to the user, not only in thinking.

**Level B — Architecture integrity**
Never violate layer boundaries (`data/` = pure logic, `ui/` = Compose + ViewModel).
Always use the architecture skill for structural changes.
A working but architecturally wrong solution is not acceptable.

**Level C — Process rules**
Subagent workflow, heavy-output restrictions, runtime restrictions,
git branch rules. Follow unless Level A or B requires deviation —
in that case, explain why in your response to the user.

**Level D — Preferences**
Simplicity over cleverness, minimal diffs, testability.
Apply when all higher levels are satisfied.

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

Skip Assessment only for: plain conversation, questions from loaded
context, 1–3 tool call tasks, file creation with known path.

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
> "Assessment: TEAM (3 agents) — crosses layer boundaries,
>  unknown integration points, high risk of violations"
> "Assessment: SUBAGENTS (2 agents) — single layer, solution space open"

---

## MANDATORY: Subagent-based workflow

Every request that requires working with code, files, or system
operations must be handled through subagents.
NEVER perform such work directly in the main context.

**It is forbidden to execute 4 or more tool calls in the main context.**
**It is forbidden to skip this rule to save time or because the task
seems simple.**

---

## HEAVY-OUTPUT COMMANDS — always via subagent

ALWAYS spawn a subagent. NEVER execute directly.

- `./gradlew assembleDebug` — build
- `./gradlew test` — run all tests
- `./gradlew test --tests "..."` — run specific test
- `./gradlew assembleRelease` — release build
- `python tools/pack_validator/pack_validator.py <path>` — validate lesson pack

For git commit: spawn a subagent to stage and commit.
Relay only the commit hash and summary back to main.

If you accidentally flood main context — acknowledge the mistake,
do not attempt to summarize. Continue.

---

## FAILOVER BEHAVIOR

| Situation | Action |
|---|---|
| Gradle not available | Check `gradlew` exists and is executable. If missing — report and stop. |
| Architecture skill unavailable | Report it. Do not proceed with structural changes. |
| Subagent execution impossible | Do the work in main context. Acknowledge explicitly. **Except heavy-output — always wait for subagent.** |
| Tests failing unrelated to task | Note it. Do not block the task. Report in summary. |
| Command returns incomplete output | Re-run with explicit flags. If still incomplete — report partial result. |
| Build fails after code change | Revert the change. Report the error. Do not attempt cascading fixes. |

---

## GIT WORKFLOW

1. Features in separate branches: `feature/xxx` or `fix/xxx`
2. Main branch: `main`
3. CLAUDE.md changes are code changes — they go on feature branches too
4. **NEVER commit code changes to `main`.**
5. Before any changes: `git branch --show-current`.
   If on `main` — create or switch to a feature branch.
6. Do not push to remote without explicit user confirmation.
7. Run tests before committing.

---

## ARCHITECTURE

### Source layout

All source code is under `app/src/main/java/com/alexpo/grammermate/`:

- **`data/`** — Pure logic layer, no Android UI dependencies. Most classes here have unit tests.
- **`ui/`** — Compose UI + ViewModel. `TrainingViewModel.kt` is the central controller (2000+ lines).
- **`MainActivity.kt`** — Entry point, handles permissions and backup restore on launch.

### Key files

| File | Role |
|------|------|
| `ui/TrainingViewModel.kt` | Session management, card navigation, word bank, vocab sprint, boss/elite modes, mastery recording |
| `ui/GrammarMateApp.kt` | All Compose UI composables (training screen, roadmap, word bank chips) |
| `ui/AppRoot.kt` | Restore-check gate before showing main app |
| `data/Models.kt` | Data classes: Lesson, SentenceCard, VocabEntry, TrainingUiState, InputMode enum |
| `data/MixedReviewScheduler.kt` | Sub-lesson scheduling with spaced repetition intervals |
| `data/FlowerCalculator.kt` | Mastery visualization (seed→sprout→bloom→wilting→wilted→gone) using Ebbinghaus forgetting curve |
| `data/MasteryStore.kt` | Tracks unique card shows and mastery per lesson |
| `data/LessonStore.kt` | Loads lesson packs from CSV in assets/storage |
| `data/BackupManager.kt` | Backup/restore progress to `Downloads/BaseGrammy/` (survives uninstall) |
| `data/Normalization.kt` | Answer validation logic |
| `data/SpacedRepetitionConfig.kt` | Review interval constants |

### Data flow

1. `MainActivity` → checks for backup → launches `AppRoot` → `GrammarMateApp`
2. `GrammarMateApp` renders UI based on `TrainingUiState` from `TrainingViewModel`
3. User actions (answer, next card, mode switch) go to `TrainingViewModel` as method calls
4. ViewModel reads/writes data through store classes (`MasteryStore`, `ProgressStore`, `LessonStore`, etc.)
5. All persistence uses YAML/CSV files on device storage (no Room/SQL)

### Key behaviors

- **Three input modes**: VOICE, KEYBOARD, WORD_BANK. Only VOICE and KEYBOARD count for mastery (flower growth). Word Bank does NOT.
- **Sub-lesson types**: Only `NEW_ONLY` and `MIXED` (WARMUP was removed — do not reintroduce).
- **Flower mastery**: `uniqueCardShows / 150` capped at 1.0. Health decays via Ebbinghaus curve.
- **Spaced repetition intervals**: `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` — do not change without justification.
- **Vocab word bank**: Always generates exactly 5 options (1 correct + 4 distractors), fetching from other lessons if needed.
- **Lesson packs**: ZIP archives with `manifest.json` + CSV lesson files + optional vocab CSV and story quiz JSON. Validated by `tools/pack_validator/pack_validator.py`.
- **Config**: `app/src/main/assets/grammarmate/config.yaml` for app settings (testMode, eliteSizeMultiplier, vocabSprintLimit).

### Testing

Unit tests in `app/src/test/java/com/alexpo/grammermate/` using JUnit 4 + Robolectric. Tests focus on the `data/` layer. Integration tests in `integration/` subdirectory. `docs/` folder has detailed test plans and coverage analysis.

---

## ON-DEMAND SKILLS

| Skill | Trigger |
|---|---|
| `architecture-defense` | Before any structural change in source code (layer boundaries, new classes, refactoring) |
| `project-reference` | `agents.md` — full method reference with line numbers for all core classes |
| `lesson-pack-reference` | `lesson_packs/INSTRUCTIONS.md` — lesson pack ZIP format, CSV structure, manifest schema |
| `test-plans` | `docs/TEST_PLAN.md`, `docs/UI_TEST_PLAN.md` — test coverage goals and P0/P1/P2 priorities |

---

## REFERENCE: Build & Development Commands

Full build setup guide: **`docs/BUILD_INSTRUCTIONS.md`** (prerequisites, Java/SDK versions, troubleshooting).

**Windows note:** `gradlew.bat` is broken with Gradle 8.9 (multi-JAR wrapper). Use instead:
```
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain <task>
```

```bash
# Build debug APK (outputs to app/build/outputs/apk/debug/grammermate.apk)
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.alexpo.grammermate.data.FlowerCalculatorTest"

# Run a single test method
./gradlew test --tests "com.alexpo.grammermate.data.FlowerCalculatorTest.testBloomState"

# Build release
./gradlew assembleRelease

# Validate a lesson pack zip
python tools/pack_validator/pack_validator.py path/to/pack.zip
```
