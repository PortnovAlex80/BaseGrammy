# GrammarMate (BaseGrammy) — AI Agent Context

USE ALWAYS SUBAGENTS IF NEED USE TOOLS MORE THAN 1

---

## PROJECT CONTEXT

**What:** Android language learning app for RU→Target translation (English/Italian). Flower-growing metaphor for progress based on Ebbinghaus forgetting curve. Grammar Story Roadmap: chapter-based narrative learning with allegorical stories.

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
| **Chapter progress** | `ChapterProgress` is pack-scoped. Independent between packs and chapters. |
| **UI conditional visibility** | No pack selected = hide lesson tiles, daily practice. Use `activePack != null` check. |
| **Story language fallback** | Story files prefer `<storyFile>`, fallback to `stories/<language>/<storyFile>`. |
| **LOCKED status removed** | ChapterStatus only has ACTIVE, DONE. No LOCKED state anymore. |
| **Settings icon placement** | Settings icon in top bar, not on home screen tiles anymore. |

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

### Prerequisites

| Component | Version | Notes |
|-----------|---------|-------|
| Java JDK | 17 | IntelliJ JBR or standalone. Other versions unsupported. |
| Android SDK | API 34 | Platform, build-tools, platform-tools |
| Gradle | 8.9 | Auto-downloaded via wrapper |

### Quick build (one command)

```cmd
build.bat assembleDebug
```

APK output: `app\build\outputs\apk\debug\grammermate.apk`

### Windows Gradle workaround

Gradle 8.9+ requires all 3 wrapper JARs in classpath (`gradle/wrapper/*` wildcard doesn't work):
```
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper-shared.jar
gradle/wrapper/gradle-cli.jar
```

**IntelliJ JBR (PRIMARY — works even when `java -version` fails):**
```cmd
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

**System Java:**
```cmd
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

### All build commands

```cmd
build.bat assembleDebug          :: Debug APK
build.bat assembleRelease        :: Release APK
build.bat test                   :: Run all tests
build.bat test --tests "...::SomeTest"  :: Run specific test
build.bat clean                  :: Clean build artifacts
```

### Android SDK path

Actual SDK location (from `local.properties`):
```
C:\Users\user\AppData\Local\Android\Sdk
```

### Install APK on phone

**IMPORTANT:** `adb` is NOT in system PATH on this machine. Use full path:
```cmd
set ADB=C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe
```

**USB cable (recommended):**
1. Enable **Developer Options** on phone: Settings → About Phone → tap "Build number" 7 times
2. Enable **USB Debugging** in Developer Options
3. Connect phone via USB cable
4. Check device is visible:
   ```cmd
   %ADB% devices
   ```
5. Install APK:
   ```cmd
   %ADB% install -r app\build\outputs\apk\debug\grammermate.apk
   ```

**Wireless (over Wi-Fi):**
1. Phone and PC must be on the same Wi-Fi network
2. First connect via USB once and pair:
   ```cmd
   %ADB% tcpip 5555
   %ADB% connect <PHONE_IP>:5555
   ```
3. Disconnect USB, then install:
   ```cmd
   %ADB% install -r app\build\outputs\apk\debug\grammermate.apk
   ```

**Direct file transfer (no adb):**
1. Copy `app\build\outputs\apk\debug\grammermate.apk` to phone (USB cable, Google Drive, Telegram, etc.)
2. On phone: open the APK file in Files app
3. Allow "Install from unknown sources" if prompted
4. Tap **Install**

### Run / launch app on connected phone

```cmd
:: Launch app on connected device
%ADB% shell am start -n com.alexpo.grammermate/.MainActivity

:: See logs in real time
%ADB% logcat -s "GrammerMate" "AndroidRuntime"

:: Uninstall app
%ADB% uninstall com.alexpo.grammermate
```

### Troubleshooting

| Problem | Solution |
|---------|----------|
| `java` not found | Use full IntelliJ JBR path (see above) |
| `NoClassDefFoundError: IDownload` | Ensure all 3 JARs in classpath |
| `sdk.dir not found` | Create `local.properties`: `sdk.dir=C\\:\\Users\\user\\AppData\\Local\\Android\\Sdk` |
| `adb` not found | Use full path: `C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| `FileAlreadyExistsException` on rebuild | Delete old APK first: `rm app\build\outputs\apk\debug\grammermate.apk` |
| Device not found | Check USB Debugging is on; run `%ADB% devices` to verify |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Use `%ADB% install -r` or `%ADB% uninstall com.alexpo.grammermate` first |

**Full docs:** `java.txt` (root), `docs/BUILD_INSTRUCTIONS.md`, `BUILD.md`

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
| `acceptance-criteria-grammar-roadmap.md` | Grammar Story Roadmap feature spec |
| `scenarios/*.md` | Code traces for user flows |

**Rule:** Read spec before modifying code. If spec ≠ code, code is source of truth. Update spec after changes.

---

## GRAMMAR STORY ROADMAP

### Overview
Grammar Story Roadmap is a narrative layer that organizes lessons into chapters with allegorical stories. Packs with chapters show GrammarStoryRoadmapScreen, packs without chapters show ClassicHomeScreen.

### Key Features

**Chapter System:**
- 8 chapters with progressive difficulty (Before Language → First Words → Grammar Garden)
- Each chapter has: title, subtitle, story file, ordered lessons
- Chapter progress tracked independently: lessonsStarted, lessonsCompleted, lastAccessedMs
- Progress pack-scoped via ChapterProgressStore

**Story Reader:**
- Markdown rendering with mobile-optimized layout
- Language-dependent content (Russian/English)
- Story files in `stories/<language>/` or pack root
- Fallback: `<storyFile>` → `stories/<language>/<storyFile>`

**UI Changes:**
- Settings icon moved to top bar (removed from home tiles)
- All status icons removed (no locks, no checkmarks)
- Conditional visibility: no pack = hide lesson tiles, daily practice
- Drill buttons: only show if hasVerbDrill/hasVocabDrill
- Back navigation: Grammar Story Roadmap → Pack Selection

**Navigation Flow:**
```
Pack Selection (no active pack)
    ↓ [select pack]
Grammar Story Roadmap (chapters pack)
    ↓ [continue lesson]
Training Screen (LESSON mode)
    ↓ [back]
Grammar Story Roadmap
    ↓ [back button]
Pack Selection
```

**Manifest v2:**
```json
{
  "schemaVersion": 2,
  "chapters": [
    {
      "chapterId": "chapter_0",
      "order": 0,
      "title": "Before Language",
      "subtitle": "The silence before words",
      "storyFile": "chapter_00_original.md",
      "lessons": ["lesson_01_A01", "lesson_02_A02"]
    }
  ]
}
```

**Chapter Status:**
- ACTIVE: Chapter in progress (green highlight)
- DONE: All lessons completed (mastery >= 3)
- No LOCKED state (removed for cleaner UX)

**Progress Calculation:**
- `lessonsStarted`: lessons with mastery > 0
- `lessonsCompleted`: lessons with intervalStepIndex >= 3
- Progress %: (lessonsCompleted / totalLessons) * 100

**Data Stores:**
- `ChapterProgressStore`: pack-scoped chapter progress
- `ChapterProgressCalculator`: computes progress from mastery data
- Atomic writes via AtomicFileWriter pattern
