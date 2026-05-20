# GrammarMate (BaseGrammy) — AI Agent Context

---

## PROJECT CONTEXT

**What:** Android language learning app for RU→Target translation (English/Italian). Flower-growing metaphor for progress based on Ebbinghaus forgetting curve.

**Stack:** Kotlin 1.9.22, Jetpack Compose (BOM 2024.02.00), Material 3, Android SDK 24–34, Java 17, SnakeYAML 2.2, Sherpa-ONNX (TTS/ASR)

**Run:** Local Gradle build via `java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain` (Windows wrapper workaround required). APK installed on device/emulator.

---

## PROJECT STRUCTURE

```
BaseGrammy/
├── app/src/main/
│   ├── java/com/alexpo/grammermate/
│   │   ├── data/                          # Data layer: stores, parsers, models
│   │   │   ├── Models.kt                  # Core data classes (Lesson, Card, enums)
│   │   │   ├── VocabWord.kt               # Vocab drill data classes
│   │   │   ├── LessonStore.kt             # Pack import, language management
│   │   │   ├── MasteryStore.kt            # Card mastery tracking
│   │   │   ├── ProgressStore.kt           # Session state persistence
│   │   │   ├── FlowerCalculator.kt        # Mastery → flower state mapping
│   │   │   ├── MixedReviewScheduler.kt    # Sub-lesson scheduling
│   │   │   ├── SpacedRepetitionConfig.kt  # Ebbinghaus curve math
│   │   │   ├── BackupManager.kt           # Backup/restore logic
│   │   │   ├── TtsEngine.kt / AsrEngine.kt    # Audio (TTS/ASR)
│   │   │   └── YamlListStore.kt           # Generic YAML storage
│   │   │
│   │   ├── feature/                       # Domain helpers (plain classes, NOT ViewModels)
│   │   │   ├── boss/                      # Boss battle logic
│   │   │   ├── daily/                     # Daily Practice 3-block session
│   │   │   ├── progress/                  # Progress calculation helpers
│   │   │   ├── training/                  # Training session helpers
│   │   │   └── vocab/                     # Vocab drill helpers
│   │   │
│   │   ├── shared/
│   │   │   └── audio/                     # Audio playback utilities
│   │   │
│   │   └── ui/                            # UI layer
│   │       ├── GrammarMateApp.kt          # Screen router, dialog orchestration
│   │       ├── TrainingViewModel.kt       # PRIMARY ViewModel (all business logic)
│   │       ├── DailyPracticeScreen.kt     # Daily Practice UI
│   │       ├── AppRoot.kt                 # Entry point (backup restore check)
│   │       ├── Theme.kt                   # Material 3 theme
│   │       ├── screens/*.kt               # Per-screen composables
│   │       └── components/*.kt            # Shared UI components
│   │
│   ├── res/
│   │   ├── values/strings-*.xml           # UI strings (English)
│   │   ├── values-ru/strings-*.xml        # UI strings (Russian)
│   │   └── xml/                           # XML configs
│   │
│   └── assets/grammarmate/
│       ├── packs/                         # Default lesson pack ZIPs
│       ├── vocab/it/                      # Italian vocab drill CSVs
│       └── config.yaml                    # Runtime flags
│
├── docs/specification/                    # Project specs & scenarios
│   ├── 01-models-and-state.md
│   ├── 02-data-stores.md
│   ├── 08-training-viewmodel.md
│   ├── scenarios/*.md                     # Code traces for user flows
│   └── tasks/                             # Implementation task files
│
├── tools/pack_validator/                  # Lesson pack validation
└── CLAUDE.md                              # This file
```

---

## CRITICAL GOTCHAS

| Gotcha | Details |
|--------|---------|
| **Windows Gradle** | Must use `java -cp "gradle/wrapper/*"` workaround, not `gradlew` |
| **WORD_BANK ≠ mastery** | Only VOICE and KEYBOARD grow flowers. WORD_BANK never counts. |
| **AtomicFileWriter** | All file writes must use temp → fsync → rename pattern |
| **Single ViewModel** | `TrainingViewModel` is ~1500 lines. Decompose helpers to `feature/` when adding logic. |
| **Pack-scoped drills** | `hasVerbDrill`/`hasVocabDrill` check active pack manifest only |
| **Learned threshold** | Mastery step ≥ 3 = "learned", not step 9 (full mastery) |

---

## AGENT DELEGATION (MANDATORY)

### When to decompose

**MUST spawn subagents when ANY is true:**
- Task touches ≥ 3 files
- Task adds ≥ 1 new class or ≥ 3 new functions
- Task requires reading spec doc AND modifying code
- Task involves TrainingViewModel
- Estimated > 30 lines of new code
- Task has sequential dependencies

### Step 0: Assessment first

Before any non-trivial task, spawn **one Assessment subagent**:

```
Assessment output:
VERDICT: SUBAGENTS | TEAM
AGENTS: N
COMPLEXITY: simple | moderate | complex
LAYERS AFFECTED: [list]
RISKS: [list]

DECOMPOSITION PLAN:
Wave 1:
  - Step 1.1: [atomic action]
  - Step 1.2: [atomic action]
Wave 2:
  - Step 2.1: [depends on Wave 1]
```

**Assessment MUST NOT spawn agents.** Main context spawns after verdict.

### Step 1: Choose mode

| Mode | Best for |
|------|----------|
| **SUBAGENTS** | Independent parallel solutions, read-only analysis, new features |
| **TEAM** | 3+ layers with dependencies, layer coordination (data→helpers→ui) |

**Default: SUBAGENTS.** Use TEAM when layer coordination is critical.

### Step 2: Execute in waves

**NEVER spawn all at once. Max 5 agents per wave.**

```
1. Spawn Wave N (max 5)
2. WAIT for all to complete
3. Collect results
4. Checkpoint (build check)
5. Spawn Wave N+1
```

### Layer-based decomposition (TEAM mode)

When work cascades across layers:

```
DATA-AGENT:    data/ → Models.kt, stores, parsers
HELPERS-AGENT: feature/ + shared/ → helpers (waits for DATA summary)
UI-AGENT:      ui/ → TrainingViewModel, screens (waits for HELPERS summary)
```

- Sequential with SendMessage coordination
- Build checkpoint after each layer
- Zero file conflicts

### Main context rules

**Main context NEVER implements.** Main context ONLY:
- Reads Assessment output
- Announces decomposition plan
- Spawns agents
- Collects results
- Makes go/no-go decisions between waves

**ONE tool call per action in main.** Chaining = violation.

### Heavy-output commands (always via subagent)

- `assembleDebug` / `assembleRelease`
- `test` (Gradle test suite)
- `git log` with large ranges
- `pack_validator.py` output

---

## BUILD COMMANDS

```bash
# Windows Gradle workaround
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain

# Build debug APK
... assembleDebug

# Run tests
... test

# Validate lesson pack
python tools/pack_validator/pack_validator.py path/to/pack.zip
```

---

## GIT WORKFLOW

1. Features in `feature/xxx` branches
2. Main branch: `main`
3. **NEVER commit code to `main`**
4. Before changes: check `git branch --show-current` — if `main`, create branch
5. Commit footer: `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`
6. **NEVER push without user approval**

---

## SKILLS TRIGGERS

| Skill | When to use |
|-------|-------------|
| `/swarm` | Any non-trivial task (≥3 files, TrainingViewModel, >30 lines) |
| `/regression-check` | After touching ≥2 files, before commit |
| `/verify-user-journey` | Before committing UI/data changes |
| `/add-feature` | Adding new feature or porting UI element |

---

## SPEC REFERENCE

Full specs in `docs/specification/`:

| File | Covers |
|------|--------|
| `01-models-and-state.md` | Data classes, enums, state |
| `02-data-stores.md` | All data stores |
| `08-training-viewmodel.md` | TrainingViewModel logic |
| `scenarios/*.md` | Code traces for user flows |

**Rule:** Read spec before modifying code. If spec ≠ code, code is source of truth. Update spec after changes.
