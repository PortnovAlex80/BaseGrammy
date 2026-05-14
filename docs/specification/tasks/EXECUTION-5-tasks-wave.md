# EXECUTION PROMPT: 5-Task Wave Implementation

## Context

This project (GrammarMate — Android language learning app, Kotlin + Compose) has 5 spec'd and tracked tasks ready for implementation. All requirements are finalized, specs are updated, and task files exist in `docs/specification/tasks/`.

**Read ALL task files FIRST** before writing any code. Each task is self-contained with problem description, per-fix details, affected files, verification criteria, and scope boundaries.

## Task Registry

| Task | File | Title | Spec | Depends on |
|------|------|-------|------|-----------|
| TASK-001 | `docs/specification/tasks/TASK-001-daily-cursor-independence.md` | Daily Practice Cursor Independence [DONE] | 09-daily-practice.md | — |
| TASK-002 | `docs/specification/tasks/TASK-002-performance-caching.md` | In-Memory Data Caching [DONE] | 20-NFR, 02-data-stores | — |
| TASK-003 | `docs/specification/tasks/TASK-003-tts-thread-safety-error-ux.md` | TTS Thread Safety and Error UX [DONE] | 05-audio-tts-asr.md | — |
| TASK-004 | `docs/specification/tasks/TASK-004-welcome-dialog-max-attempts.md` | WelcomeDialog Max 3 Attempts [DONE] | 13-app-entry-and-navigation.md | — |
| TASK-005 | `docs/specification/tasks/TASK-005-tts-error-icon-memory.md` | TTS Error Icon for Memory Failures [DONE] | 05-audio-tts-asr.md | TASK-003 |
| TASK-009 | `docs/specification/tasks/DONE-TASK-009-profile-stats-popup.md` | Profile Stats Popup with CEFR Level [DONE] | 20-NFR | — |

## Build Commands

```bash
# Gradle on Windows (use this exact form, never plain gradlew):
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain

# Build debug APK:
... assembleDebug

# Run all unit tests:
... test
```

## Wave Plan

```
Wave 1 (RESEARCH — 1 agent, read-only):
  Read all 5 task files + their referenced spec sections.
  Return: file map, risk assessment, dependency confirmation.

Wave 2 (IMPLEMENT — 3 agents, parallel, INDEPENDENT tasks):
  Agent 2.1: TASK-001 (Daily Practice cursor independence — 3 fixes)
  Agent 2.2: TASK-004 (WelcomeDialog max 3 attempts — 3 fixes)
  Agent 2.3: TASK-002 (Performance caching — read task file for scope)

Wave 3 (IMPLEMENT — 1 agent):
  Agent 3.1: TASK-003 (TTS thread safety — 3 fixes: mutex, timeout, error reason)

Wave 4 (IMPLEMENT — 1 agent, depends on Wave 3):
  Agent 4.1: TASK-005 (TTS error icon — 2 fixes: error reason in state, tooltip)

Wave 5 (REGRESSION — 1 agent, read-only):
  Run full regression: build + tests + per-task verification checklists.
  Update task completion logs.
  Update specs if code diverged from spec (trace-index, CHANGELOG).
```

## Per-Task Details

### TASK-001: Daily Practice Cursor Independence
**3 fixes, files in feature/daily/**

| Fix | What | Files |
|-----|------|-------|
| 1 | Replace `resolveProgressLessonInfo()` with `cursor.currentLessonIndex + 1` in `DailyPracticeCoordinator.startDailyPractice()` and `prebuildSession()`. Prebuilt cache: validate cursor level matches, discard if mismatch. | `DailyPracticeCoordinator.kt` |
| 2 | In `advanceDailyCursor()`: when `sentenceOffset >= lesson.cards.size` → `currentLessonIndex++`, `sentenceOffset = 0`. If `currentLessonIndex >= packLessons.size` → wrap to 0. | `DailyPracticeCoordinator.kt` |
| 3 | In `buildVerbBlock()`: when `unshown.isEmpty()` → use full filtered set as candidates (cycling). | `DailySessionComposer.kt` |

**Verification:** cursor=0 → only Presente verbs. cursor=1 → Presente + Imperfetto. Block 3 never empty.

### TASK-002: In-Memory Data Caching
**Read task file** — added externally, scope defined there.

### TASK-003: TTS Thread Safety and Error UX
**3 fixes, files in data/ and shared/**

| Fix | What | Files |
|-----|------|-------|
| 1 | Add `Mutex()` to `TtsEngine`. Wrap `initialize()`, `speak()`, `stop()`, `release()` with `mutex.withLock`. | `data/TtsEngine.kt` |
| 2 | Add `withTimeout(30_000)` around native `OfflineTts` instantiation. On timeout → ERROR state. | `data/TtsEngine.kt` |
| 3 | Add error reason to `TtsState`: change from flat ERROR enum to sealed class or add `ttsErrorReason: String?`. Populate on OOM, file missing, timeout, native error. | `data/TtsEngine.kt`, propagate to UI state |

**Verification:** Samsung Tab: download + auto-init → no crash. Init hang → 30s → ERROR. speak() in ERROR → silent skip.

### TASK-004: WelcomeDialog Max 3 Attempts
**3 fixes, files in data/ and ui/**

| Fix | What | Files |
|-----|------|-------|
| 1 | Add `welcomeDialogAttempts: Int = 0` to `UserProfile`. Persist in `profile.yaml`. Guard: `attempts < 3`. | `data/Models.kt`, `data/ProfileStore.kt`, `ui/GrammarMateApp.kt` |
| 2 | Only show dialog when `currentScreen == HOME`. Never during TRAINING/DAILY_PRACTICE/LESSON/VERB_DRILL/VOCAB_DRILL. | `ui/GrammarMateApp.kt` |
| 3 | Skip/blank → increment counter + persist. After 3 skips → dialog permanently dismissed. | `ui/GrammarMateApp.kt` |

**Verification:** Skip 3x → no dialog on 4th launch. During training → never appears. Settings still allows name change.

### TASK-005: TTS Error Icon for Memory/Loading Failures
**2 fixes, depends on TASK-003 (error reason field)**

| Fix | What | Files |
|-----|------|-------|
| 1 | Use error reason from TASK-003 to populate UI state field `ttsErrorReason: String?`. Map to human-readable strings. | `ui/TrainingViewModel.kt` or state collection |
| 2 | Add `TooltipBox` to TTS icon when ERROR. Show: "Model not loaded", "Not enough memory", "Initialization failed", "Timed out". Warning triangle for OOM, ReportProblem for others. | `ui/components/TtsSpeakerButton.kt`, `ui/screens/DailyPracticeScreen.kt` |

**Verification:** Kill model file → ERROR → tooltip "Model not loaded". OOM → red triangle → "Not enough memory".

## Spec Tracking Rules

After each wave, the agent MUST:
1. **Check if code matches spec** — if code intentionally diverges from spec, update the spec to match code (with note in CHANGELOG).
2. **Update trace-index** — add new symbols introduced by the changes.
3. **Update task completion log** — mark each fix DONE in the task file.
4. **Update scenario discrepancies** — if a discrepancy is now fully resolved in code (not just spec), change status from `SPEC RESOLVED, CODE PENDING` to `RESOLVED`.

Files to update after implementation:
- `docs/specification/trace-index.md` — new symbols
- `docs/specification/CHANGELOG.md` — spec changes
- `docs/specification/tasks/TASK-NNN-*.md` — completion log
- `docs/specification/scenario-06-daily-practice.md` — discrepancy statuses (for TASK-001)

## Regression Plan (Wave 5)

After ALL waves complete, run full regression:

### Build check
```bash
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

### Unit tests
```bash
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain test
```

### Per-task verification

**TASK-001 checklist (from 09#9.8.7):**
1. `currentLessonIndex=0` → Block 3 contains ONLY Presente
2. `currentLessonIndex=1` → Block 3 contains Presente + Imperfetto
3. Block 1 and Block 3 use same lesson index
4. Block 3 never empty when active tenses have cards
5. Browsing different lesson on roadmap does NOT change daily cursor

**TASK-003 checklist:**
1. All TtsEngine native calls wrapped in Mutex
2. Init timeout fires after 30s on hang
3. Error reason propagated to UI state
4. speak() in ERROR state → silent skip, no crash

**TASK-004 checklist:**
1. Fresh install → dialog on HOME (attempt 1)
2. Skip 3 times → dialog never appears again
3. Dialog never appears during TRAINING/DAILY_PRACTICE
4. Settings still allows name change after dialog dismissed
5. `welcomeDialogAttempts` persists in profile.yaml

**TASK-005 checklist:**
1. ERROR icon shows tooltip with cause
2. "Out of memory" shows red triangle (Warning), not ReportProblem
3. Normal TTS flow unaffected (READY/SPEAKING — no tooltip)

### Cross-task regression
- Daily Practice still starts correctly (TASK-001 didn't break session flow)
- TTS still works on non-Samsung devices (TASK-003 didn't regress normal flow)
- WelcomeDialog doesn't interfere with Daily Practice resume dialog
- Regular training unaffected by cursor changes

### UC/AC spot-check
Read `docs/specification/22-use-case-registry.md` — verify UC-21 AC5-AC6, UC-24 AC2, UC-60, UC-61 AC1-AC4 still hold after code changes.

## Git

Branch: work in the CURRENT branch ). Do NOT create a new branch.
One commit per task, or one combined commit per wave.
NEVER push without explicit user approval.
