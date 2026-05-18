# TASK-073: Comprehensive Test Suite — Test Plan + Unit Tests

**Status:** OPEN
**Created:** 2026-05-18
**Branch:** feature/test-suite (from develop)
**Spec:** All spec files
**UC:** All UCs from 22-use-case-registry.md (88+ use cases)
**Related:** legacy-test-plan.md (~200 TODO tests)

---

## Problem

Every code change causes regressions. Manual testing doesn't catch them. The project has zero meaningful unit tests — `legacy-test-plan.md` lists ~200 tests, all TODO. There is no automated safety net.

Recent regressions that tests would have caught:
- `recordDailyCardPracticed` never called → daily cursor stuck
- `resetState()` wiping `dailyCursor` on `selectLesson()`
- `saveProgress()` reading stale combine flow → cursor not persisted
- HINT_SHOWN + correct → no card advance
- TTS shared singleton race → tablet crash

---

## Approach

### Phase 1: Test Plan (write first, implement second)

Create `docs/specification/test-plan.md` covering every user-facing behavior organized by screen/flow. Each test case maps to a UC/AC from `22-use-case-registry.md`.

### Phase 2: Core Logic Tests (highest priority)

Tests for the engine that drives ALL screens — SessionRunner, DailyPracticeCoordinator, DailySessionComposer, CardProvider, AnswerValidator.

### Phase 3: Per-Screen User Journey Tests

Tests for each screen's complete user journey: entry → interaction → exit.

---

## Test Plan Structure

### 1. SessionRunner — Card Orchestration (CRITICAL)

These tests cover the PRIMITIVE logic the user described: correct → advance, wrong → retry/hint, navigation.

```
Card advancement:
  ✅ correct answer in ACTIVE state → advance to next card
  ✅ correct answer in PAUSED state → resume + advance to next card
  ✅ correct answer in HINT_SHOWN state → resume + advance to next card
  ✅ wrong answer in ACTIVE state → increment incorrectAttempts, stay on card
  ✅ wrong answer × 3 → show hint, enter HINT_SHOWN state
  ✅ last card + correct → subLessonFinishedToken increments, currentCard = null

Navigation:
  ✅ navigateNext() → currentIndex + 1, sessionState = PAUSED
  ✅ navigatePrev() → currentIndex - 1, sessionState = PAUSED
  ✅ navigateNext on last card → stays on last card
  ✅ navigatePrev on first card → stays on first card

Input modes:
  ✅ VOICE mode card → auto-trigger voice recognition on advance
  ✅ KEYBOARD mode card → no auto-trigger
  ✅ WORD_BANK mode card → no auto-trigger

Session lifecycle:
  ✅ startCardSession(cards, DAILY_TRANSLATE) → sets screenMode, sessionState=ACTIVE
  ✅ startCardSession(cards, DAILY_VERBS) → sets screenMode, sessionState=ACTIVE
  ✅ exitCardSession() → resets session state
```

### 2. DailyPracticeCoordinator — Cursor & Session

```
Cursor advancement:
  ✅ cancelDailySession with all TRANSLATE+VERBS practiced → returns sentenceCount
  ✅ cancelDailySession with partial practice → returns null (no advance)
  ✅ cancelDailySession with WORD_BANK only → returns null
  ✅ advanceCursor(10) → sentenceOffset += 10
  ✅ advanceCursor past lesson boundary → currentLessonIndex++, sentenceOffset=0
  ✅ advanceCursor past last lesson → wrap to 0

Session lifecycle:
  ✅ startDailyPractice first session today → stores firstSessionCardIds
  ✅ startDailyPractice NOT first session → does NOT overwrite cardIds
  ✅ repeatDailyPractice → uses stored cardIds
  ✅ hasResumableDailySession with today's date + cardIds → true
  ✅ hasResumableDailySession with no cardIds → false

State preservation:
  ✅ resetState() preserves dailyCursor
  ✅ resetState() clears dailySession (active, blocks)
  ✅ resetAllDailyState() wipes dailyCursor
  ✅ recordDailyCardPracticed(TRANSLATE) increments TRANSLATE count
  ✅ recordDailyCardPracticed(VERBS) increments VERBS count

Block completion:
  ✅ onBlockComplete advances blockIndex
  ✅ onBlockComplete on last block → endSession, finishedToken=true
  ✅ endSession records streak for each practice type
```

### 3. DailySessionComposer — Block Building

```
TRANSLATE block:
  ✅ builds from cursor.currentLessonIndex + sentenceOffset
  ✅ does NOT cross lesson boundaries
  ✅ sessionSize=3 → returns 3 cards
  ✅ offset >= lesson size → returns empty list

VOCAB block:
  ✅ selects due words first (overdue first)
  ✅ then new words by rank
  ✅ sessionSize limits output
  ✅ excludes numbers

VERBS block:
  ✅ filters by active tenses for effectiveLevel
  ✅ excludes previously shown cards
  ✅ cycles when all cards shown
  ✅ weakness-first ordering
```

### 4. AnswerValidator

```
  ✅ exact match → correct
  ✅ case-insensitive match → correct
  ✅ synonym match (answer + answer) → correct
  ✅ partial match → wrong
  ✅ empty input → wrong
  ✅ testMode → accepts any non-empty input
```

### 5. CardProvider — Sub-lesson Scheduling

```
  ✅ NEW_ONLY sub-lessons contain only new cards
  ✅ MIXED sub-lessons contain new + review cards
  ✅ subLessonSize controls cards per sub-lesson
  ✅ cycles through all cards before repeating
```

### 6. FlowerCalculator

```
  ✅ mastery=0 → LOCKED
  ✅ mastery>0 → SEED
  ✅ mastery at threshold → SPROUT
  ✅ mastery at bloom threshold → BLOOM
  ✅ decay over time → WILTING → WILTED → GONE
```

### 7. Progress Persistence

```
  ✅ saveProgress persists DailyCursorState to progress.yaml
  ✅ loadProgress restores DailyCursorState from progress.yaml
  ✅ cursor survives app restart
  ✅ firstSessionDate survives app restart
  ✅ firstSessionCardIds survive app restart
  ✅ saveProgress after updateCursor → correct cursor on disk
```

### 8. Settings

```
  ✅ setSessionSize updates all consumers
  ✅ setSessionSize persists to config
  ✅ setSessionSize clamps to valid range
```

### 9. TTS/ASR

```
  ✅ TtsEngine.initialize serializes via mutex
  ✅ TtsEngine.doRelease frees native resources
  ✅ AudioCoordinator ttsMutex prevents concurrent access
  ✅ checkTtsModel checks engine state + file readiness
  ✅ speakTts catches Throwable (not just Exception)
```

### 10. Per-Screen User Journeys (integration-level)

```
Daily Practice:
  ✅ Full session: TRANSLATE(3) → VOCAB(3) → VERBS(3) → completion
  ✅ Repeat after completion → same cards
  ✅ Continue after completion → new cards from advanced cursor
  ✅ Exit mid-session → cursor does NOT advance
  ✅ App restart after session → Continue/Repeat dialog

Regular Training:
  ✅ Start sub-lesson → answer cards → complete → progress saved
  ✅ Wrong answer × 3 → hint shown → correct → advance
  ✅ Navigate back/forward → PAUSED state → correct → advance

Verb Drill:
  ✅ Start drill → answer conjugation → complete
  ✅ VOICE input → correct → advance
  ✅ KEYBOARD input → correct → advance

Vocab Drill:
  ✅ Start drill → rate card (Again/Hard/Good/Easy) → next card
  ✅ Complete all cards → session done
  ✅ Mastery step advances correctly per rating
```

---

## Implementation Order

1. **Phase 1** (this task): Write test plan to `docs/specification/test-plan.md`
2. **Phase 2**: Implement tests for sections 1-5 (core logic) — highest ROI
3. **Phase 3**: Implement tests for sections 6-10 (calculators, persistence, screens)

## Verification

Each test must:
- Have a descriptive name matching the behavior
- Assert specific state values (not just "no exception")
- Cover the happy path AND at least one edge case
- Map to a UC/AC from the use case registry

## Scope

**In scope:**
- Unit tests for SessionRunner, DailyPracticeCoordinator, DailySessionComposer
- Unit tests for AnswerValidator, CardProvider, FlowerCalculator
- Integration tests for persistence (ProgressStore)
- Test plan document

**Out of scope (separate task):**
- UI/composable tests (Compose testing)
- Instrumented/Android tests (need emulator)
- Performance benchmarks

## Files

- **New:** `app/src/test/java/com/alexpo/grammermate/feature/training/SessionRunnerTest.kt`
- **New:** `app/src/test/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinatorTest.kt` (exists, expand)
- **New:** `app/src/test/java/com/alexpo/grammermate/feature/daily/DailySessionComposerTest.kt`
- **New:** `app/src/test/java/com/alexpo/grammermate/data/AnswerValidatorTest.kt`
- **New:** `app/src/test/java/com/alexpo/grammermate/feature/training/CardProviderTest.kt`
- **New:** `app/src/test/java/com/alexpo/grammermate/data/FlowerCalculatorTest.kt`
- **New:** `app/src/test/java/com/alexpo/grammermate/data/ProgressPersistenceTest.kt`
- **New:** `docs/specification/test-plan.md`

## Git
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Section | Status | Tests Written | Tests Passing |
|------|---------|--------|---------------|---------------|
| | | | | |
