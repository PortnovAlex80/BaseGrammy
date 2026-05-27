# GrammarMate (BaseGrammy) — AI Agent Context

USE ALWAYS SUBAGENTS IF NEED USE TOOLS MORE THAN 1

---

## PROJECT CONTEXT

**What:** Android language learning app for RU→Target translation (English/Italian). Flower-growing metaphor for progress based on Ebbinghaus forgetting curve.

**Stack:** Kotlin 1.9.22, Jetpack Compose (BOM 2024.02.00), Material 3, Android SDK 24–34, Java 17, SnakeYAML 2.2, Sherpa-ONNX (TTS/ASR)

---

## CRITICAL GOTCHAS

| Gotcha | Details |
|--------|---------|
| **Windows Gradle** | Must use `java -cp "gradle/wrapper/*"` workaround, not `gradlew`. See `java.txt` for complete build commands and setup instructions. |
| **WORD_BANK ≠ mastery** | Only VOICE and KEYBOARD grow flowers. WORD_BANK never counts. |
| **AtomicFileWriter** | All file writes must use temp → fsync → rename pattern |
| **Single ViewModel** | `TrainingViewModel` is ~1500 lines. Decompose helpers to `feature/` when adding logic. |
| **Pack-scoped drills** | `hasVerbDrill`/`hasVocabDrill` check active pack manifest only |
| **Learned threshold** | Mastery step ≥ 3 = "learned", not step 9 (full mastery) |

---

## VerbDrill Clickable Regression Tests

When writing Verb Practice / VerbDrill regression tests, do not submit green TODO scaffolds. A test is only valid if it has real assertions and exercises the relevant UI click path.

Required pattern for SessionCard batch tests:

1. Use deterministic cards with stable IDs and `rank = index`.
2. Render `VerbDrillScreen` and `TrainingScreen` or a small harness switching between them.
3. Start through UI using `verb_start_button`.
4. Complete cards through UI using `input_field` and `check_button`.
5. Navigate without completion through UI using `next_button` / `prev_button`.
6. Exercise SessionCard actions through UI using `session_card`, `repeat_button`, `continue_button`, `reset_button`.
7. Read ViewModel/store state only for answers and assertions. Do not mutate state directly from the test body.

Allowed:
- Read `currentCard.acceptedAnswers.first()` to type the correct answer.
- Read `session.cards.map { it.id }` to assert batch identity/order.
- Read `store.loadLastSession()` and `store.loadProgress()` to assert persistence.

Forbidden in clickable tests:
- Do not call `submitCorrectAnswer()` directly from the test body instead of clicking `check_button`.
- Do not call `markCardCompleted()` directly from the test body instead of the UI hint/next flow.
- Do not call `exitSession()` directly to simulate user navigation.
- Do not rename unrelated tests to `.bak` or delete existing tests.
- Do not add passing tests that contain only TODO comments.
- Do not assert that Repeat returns only checked cards. Repeat replays the full saved batch in order.
- Do not assert that Reset clears learning progress. Reset clears saved session context; progress remains.

Expected semantics:
- A card is counted shown only after Check succeeds or after the hint/show-answer completion path.
- Navigation-only cards are not counted shown.
- Repeat replays `lastSession.sessionCardIds` in the same order.
- Continue excludes checked/shown cards, not merely visited cards.
- Reset hides SessionCard and deletes last session, but keeps VerbDrill progress.

Run the focused test with the Windows wrapper workaround:

```cmd
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain test --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"
```

If Java is only available through IntelliJ JBR, use the same classpath with the full `java.exe` path from `docs/BUILD_INSTRUCTIONS.md` or `java.txt`.

---

## BUILD APK

**Java NOT available in this environment.** Build APK on your local machine.

### Complete build documentation

**The file `java.txt` in the project root contains complete build instructions, including:**
- Java 17 installation (IntelliJ JBR or standalone)
- Android SDK setup without Android Studio
- Windows Gradle wrapper workaround (3-JAR classpath)
- All build commands with examples
- Troubleshooting guide

### Quick reference

Also see `docs/BUILD_INSTRUCTIONS.md` for additional setup details.

### Prerequisites

| Component | Version |
|-----------|---------|
| Java JDK | 17 |
| Android SDK | API 34 |

### Quick commands

**IMPORTANT:** If `java` command is not found, use full IntelliJ JBR path (see note below).

```cmd
:: Debug APK (output: app\build\outputs\apk\debug\grammermate.apk)
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug

:: Release APK
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleRelease

:: Run tests
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain test
```

**Windows workaround:** Gradle 8.9+ requires all 3 wrapper JARs in classpath (wildcard `gradle/wrapper/*` doesn't work):
```
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper-shared.jar
gradle/wrapper/gradle-cli.jar
```

**IntelliJ JBR full path (PRIMARY SOLUTION when `java` command fails):**
```cmd
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

The full IntelliJ JBR path works even when `java -version` fails or Java is not in PATH.
See `java.txt` or `docs/BUILD_INSTRUCTIONS.md` for complete troubleshooting guide.

Or create `build.bat` (see BUILD_INSTRUCTIONS.md or java.txt for full instructions).

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
| `/build-apk` | Build debug or release APK locally |

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
