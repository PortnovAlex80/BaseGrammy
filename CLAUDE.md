# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**BaseGrammy** (formerly GrammarMate) is an Android language learning app that builds grammar automaticism through RU→Target retrieval practice. Uses spaced repetition without calendar constraints — intervals are measured in completed sub-lessons, not days.

**Tech Stack**: Kotlin + Jetpack Compose, Gradle 8.2, targetSdk 34, no database (YAML file persistence)

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.alexpo.grammermate.data.FlowerCalculatorTest"

# Run tests with coverage
./gradlew testDebugUnitTest

# Clean build
./gradlew clean

# Install debug APK to connected device
./gradlew installDebug
```

**Output**: Debug APK renamed to `grammermate.apk` in `app/build/outputs/apk/debug/`

## Architecture

### Single-ViewModel MVVM

The app uses a **centralized ViewModel architecture**:
- `TrainingViewModel` (~2350 lines) — main controller for all training session logic
- `GrammarMateApp.kt` — Compose UI (stateless renderer, displays ViewModel state)
- Data layer in `data/` package — stores, parsers, calculators, domain models

**Why this pattern**: Training flow is tightly coupled; a single ViewModel simplifies state management across card navigation, word bank, mastery tracking, and sub-lesson progression.

### Data Persistence (No Database)

Progress stored as YAML files with atomic write pattern (temp → fsync → rename):
- `MasteryStore.kt` — flower levels, card shows, spaced repetition state
- `ProgressStore.kt` — current session position
- `StreakStore.kt` — daily streak data
- `ProfileStore.kt` — user settings and language selection

All stores extend `YamlListStore<T>` and write to app's internal storage.

### Lesson Packs System

Content loaded via ZIP files in `assets/grammarmate/packs/`:
- `EN_WORD_ORDER_A1.zip` — English A1 word order pack
- `IT_WORD_ORDER_A1.zip` — Italian A1 word order pack
- Each pack contains CSV lessons + manifest + optional story quizzes

Parsed by `CsvParser.kt` (sentences) and `StoryQuizParser.kt` (stories).

### Mastery & Flower System

`FlowerCalculator.kt` computes flower state from mastery data:
- **LOCKED** 🔒 — lesson not available
- **SEED** 🌱 — 0-33% mastery (0-49 unique card shows)
- **SPROUT** 🌿 — 33-66% mastery (50-99 shows)
- **BLOOM** 🌸 — 66-100% mastery (100-150 shows)
- **WILTING** 🥀 — health declining (not practicing)
- **WILTED** 🍂 — health critical (<50%)
- **GONE** ⚫ — forgotten (>90 days without practice)

**Important**: Only VOICE and KEYBOARD input modes count for mastery. WORD_BANK mode does NOT increase mastery (see `TrainingViewModel.recordCardShowForMastery()`).

### Sub-Lesson Scheduling

`MixedReviewScheduler.kt` builds lesson schedules with two sub-lesson types:
- **NEW_ONLY** — current lesson only (first 3-4 sub-lessons per topic)
- **MIXED** — current topic + spaced repetition of past topics

WARMUP mode was removed (commit 82ddb7f) — do not re-introduce.

Spaced repetition intervals (in sub-lesson steps): `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]`

---

## EXECUTION MODE

### Step 0 — Assessment First

Before any non-trivial task (feature implementation, refactor, bug fix requiring analysis), spawn **one Assessment subagent**.

The Assessment subagent:
- Reads the task and relevant files
- Identifies: affected layers, unknowns, risks, inter-dependencies
- Estimates complexity: simple / moderate / complex
- Returns a structured verdict to main

**Assessment MUST NOT spawn additional agents. All spawning happens in main context after verdict.**

**Assessment output format:**
```
VERDICT: DIRECT | SUBAGENTS | TEAM
COMPLEXITY: simple | moderate | complex
LAYERS AFFECTED: [UI | ViewModel | Data | Stores | Assets]
UNKNOWNS: [list]
RISKS: [list]
REASONING: [brief explanation]
```

Skip Assessment only for:
- Plain conversation / questions
- Tasks from loaded context
- 1–3 tool call operations (file reads, simple edits)

### Step 1 — Choose Mode

```
DIRECT                SUBAGENTS                           TEAM
─────────────         ─────────────────────────────       ─────────────────────────────
Simple, isolated      Goal: WIDTH                         Goal: DEPTH + CONFLICT
tasks.                Independent parallel solutions.     Role-based argumentation.
                       Each agent produces full result.    Agents dispute and self-organize.
                       Main scores and picks winner.      Team lead assembles final.

Best for:             Best for:                           Best for:
- File reads          - Unknown solution space            - Complex execution, known direction
- Simple edits        - Parallel alternatives needed      - Cross-layer changes
- Questions           - Fire-and-forget exploration       - High-stakes features
                                                            - Internal conflict = quality
```

**Combined pattern (highest-stakes tasks):**
Round 1 → SUBAGENTS explore → score → pick winner
Round 2 → TEAM implements with full role conflict

### Step 2 — Spawn with Shared Context

Assessment analysis goes into EVERY agent prompt. Each prompt reframes it from a different angle.

**Subagents — same analysis, different angle:**
```
agent-1: "[assessment] — Find risks, edge cases, failure modes."
agent-2: "[assessment] — Design the solution. Architecture first."
agent-3: "[assessment] — Simplify ruthlessly. Minimal correct solution."
```

**Team — same analysis, different role:**
```
agent-1: "[assessment] — ARCHITECT. Propose structure. Others challenge you."
agent-2: "[assessment] — IMPLEMENTER. Build it. Push back on anything that
          doesn't work in practice. Use SendMessage."
agent-3: "[assessment] — REVIEWER. Attack both. Find what they missed.
          Use SendMessage to challenge their decisions."
```

### Step 3 — Evaluate and Choose

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

## ON-DEMAND SKILLS

Skills are loaded only when trigger matches. Use `Skill` tool to invoke.

| Skill | Trigger | Purpose |
|---|---|---|
| `superpowers:brainstorming` | Creative work (features, components) | Explore user intent, requirements, design before implementation |
| `superpowers:systematic-debugging` | Bug, test failure, unexpected behavior | Diagnose root cause before proposing fixes |
| `superpowers:test-driven-development` | Implementing feature/bugfix | Write tests before implementation code |
| `superpowers:verification-before-completion` | Before claiming work complete | Run verification commands, confirm output |
| `feature-dev:feature-dev` | Feature development | Guided development with codebase understanding |
| `superpowers:writing-plans` | Have spec for multi-step task | Create implementation plan before coding |
| `superpowers:executing-plans` | Have written implementation plan | Execute plan with review checkpoints |

**Critical**: If ANY skill might apply (even 1% chance), invoke it BEFORE responding.

---

## Key Business Rules

1. **Word Bank with repeated words**: Words can appear multiple times; chips enable/disable based on occurrence count (GrammarMateApp.kt:2596-2601)

2. **Vocabulary word bank**: Always shows 5 options (1 correct + 4 distractors). Falls back to all lessons if current vocab pool insufficient (TrainingViewModel.kt:1865-1904)

3. **Sub-lesson size**: Default 10 cards (TrainingConfig.kt), range 6-12

4. **Hint rate gating**: `hintUsageRate > 40%` blocks FLOWER transition; `> 60%` blocks FLOWER entirely until reduced

5. **Boss/Elite modes**: Optional challenges after lesson completion; use Active+Reserve pools

## File Locations

| Component | Path |
|-----------|------|
| Main ViewModel | `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` |
| UI Composables | `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` |
| Domain Models | `app/src/main/java/com/alexpo/grammermate/data/Models.kt` |
| Mastery Logic | `app/src/main/java/com/alexpo/grammermate/data/FlowerCalculator.kt` |
| Progress Stores | `app/src/main/java/com/alexpo/grammermate/data/*Store.kt` |
| Unit Tests | `app/src/test/java/com/alexpo/grammermate/` |
| Lesson Assets | `app/src/main/assets/grammarmate/` |
| Backup Location | `Downloads/BaseGrammy/backup_YYYY-MM-DD_HH-mm-ss/` |

## Constraints

- DO NOT re-introduce WARMUP mode (removed in commit 82ddb7f)
- DO NOT count WORD_BANK mode for mastery tracking
- DO NOT modify spaced repetition intervals without testing
- DO NOT push directly to main branch without user testing approval
- YAML files require `schemaVersion` field for future migrations

---

## Domain Glossary

| Term | Meaning |
|------|---------|
| Sub-lesson | A training block of 6-12 cards. 10-12 sub-lessons per lesson topic. |
| Active Set | First 150 sentences of a lesson — used for primary learning. |
| Reserve Set | Hidden 150+ sentences — used for spaced repetition after lesson completion. |
| Flower mastery | Visual progress indicator: SEED → SPROUT → BLOOM (or decays to WILTING/WILTED/GONE). |
| Mixed review | Sub-lessons combining current topic + spaced repetition of past topics. |
| Hint rate | Portion of cards where user viewed answer. Blocks BLOOM if >40%. |
| Word Bank | Input mode where user taps words instead of typing/speaking. Does NOT count for mastery. |

## Critical Gotchas

- **Word Bank ≠ mastery**: `recordCardShowForMastery()` skips WORD_BANK mode (TrainingViewModel.kt:1738)
- **Vocab word bank size**: Always 5 options (1 correct + 4 distractors). Falls back to all lessons if needed.
- **Flower health decay**: Based on Ebbinghaus forgetting curve with stabilityMemory factor.
- **Atomic file writes**: All stores use temp → fsync → rename pattern via `AtomicFileWriter`.
- **Sub-lesson completion**: Marked via `markSubLessonCardsShown()` — affects ladder progress, not flower.
- **Backup restores**: Auto-check on app launch via `MainActivity.kt` for `Downloads/BaseGrammy/` backups.

---

## Rule Hierarchy

When rules conflict, priority goes in this order:

**Level A — Safety & correctness**
Never perform destructive actions. If uncertain — stop and explain.
Correctness beats speed. Transparency beats silence.
Any deviation from Level B–D rules must be acknowledged explicitly.

**Level B — Architecture integrity**
Respect the Single-ViewModel MVVM pattern. Data flows: Stores → ViewModel → UI.
UI is stateless; all logic in ViewModel or data layer.
A working but architecturally wrong solution is not acceptable.

**Level C — Process rules**
- Assessment-first for non-trivial tasks
- Subagent workflow for 4+ tool calls
- Feature branches: `feature/xxx` or `fix/xxx`
- User must test before pushing to remote
- Main branch is stable — no direct commits

**Level D — Preferences**
Simplicity over cleverness. Minimal diffs. Testability.
Apply when all higher levels are satisfied.

---

## Heavy-Output Commands (Always via subagent)

ALWAYS spawn a subagent. NEVER execute directly in main context:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew testDebugUnitTest
./gradlew clean
git log (with large ranges)
```

For git commits: spawn a subagent to stage and commit. Relay only commit hash and summary.

**MANDATORY**: It is forbidden to execute 4 or more tool calls in main context.

---

## Failover Behavior

| Situation | Action |
|---|---|
| ADB device not connected | Report and stop. Cannot test APK without device. |
| Gradle build fails | Run `./gradlew clean`, retry. Report error output. |
| Tests failing unrelated to task | Note it. Do not block the task. Report in summary. |
| YAML file corrupted | Check `AtomicFileWriter` pattern. Report backup location. |
| Asset ZIP missing | Verify `assets/grammarmate/packs/` directory. |
| Skill unavailable | Proceed without it. Acknowledge explicitly in response. |
| Subagent execution impossible | Do work in main context. Acknowledge explicitly. Except heavy-output — always wait for subagent. |
| Git merge conflicts | Stop and explain. Do not auto-resolve without user approval. |

---

## Git Workflow

1. **Main branch**: `main` (stable, production-ready)
2. **Feature branches**: `feature/xxx` or `fix/xxx`
3. **NEVER commit code directly to `main`**
4. Before changes: check current branch with `git branch --show-current`
5. If on `main` — create or switch to a feature branch first

**Push protocol**:
```
❌ WRONG: Create feature → Commit → Push immediately
✅ RIGHT: Create feature → Commit → Wait for user → Test confirmation → Push
```

User must explicitly approve before:
- `git push` to any remote
- Merging to `main`
- Releasing to production

---

## Mandatory Workflow

Every non-trivial code change requires:
1. Check current branch
2. Create feature branch if on main
3. **Assessment** (spawn subagent for analysis)
4. Choose mode (DIRECT / SUBAGENTS / TEAM)
5. Implement changes (via agents if applicable)
6. Run relevant tests
7. Wait for user testing approval
8. Only then push

Simple tasks (file reads, questions, 1-2 line edits) can proceed directly.

---

## Multi-Agent Workflow Summary

```
Task Request
     ↓
Assessment (subagent)
     ↓
Verdict: DIRECT | SUBAGENTS | TEAM
     ↓
Execute (main or agents)
     ↓
Verify & Report
```

**Key principle**: Assessment first, then mode choice, then execution. Heavy-output always via subagent.
