# TASK-089: Staircase Navigation After Training Completion

**Status:** OPEN
**Created:** 2026-05-29
**Branch:** feature/staircase-navigation (from feature/multilingual-story-tts)
**Spec:** 13-app-entry-and-navigation#13.3.6, 24-grammar-story-roadmap#navigation-flow
**UC:** UC-101 AC1-6
**Scenario:** scenario-11-navigation#step-3,4,5

---

## Problem

Текущее поведение: после завершения тренировки пользователь попадает сразу на HOME, пропуская LESSON и CHAPTER_LESSONS. Это нарушает навигационный контекст - пользователь не видит путь назад к главам и урокам.

**Ожидаемое поведение:**
- TRAINING → LESSON → CHAPTER_LESSONS → GRAMMAR_STORY_ROADMAP → HOME

**Фактическое поведение:**
- TRAINING → HOME (перескакивает LESSON, CHAPTER_LESSONS, GRAMMAR_STORY_ROADMAP)

**Проблема:** Код в [GrammarMateApp.kt:622-629](d:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\ui\GrammarMateApp.kt#L622-L629) устанавливает `returnTo = Routes.HOME` или использует дефолтный LESSON, но не учитывает контекст глав.

---

## Changes

### Fix 1: Implement returnTo architecture decision
**Discrepancy:** N/A | **UC:** UC-101 AC1-6 | **Spec:** 13#13.3.6

**Description:** Implement intelligent `returnTo` logic based on pack context (has chapters vs classic). The code-architect agent will determine the exact logic, but the approach should:

1. When entering TRAINING from CHAPTER_LESSONS: set `returnTo = Routes.CHAPTER_LESSONS`
2. When entering CHAPTER_LESSONS from GRAMMAR_STORY_ROADMAP: maintain navigation context
3. Sub-lesson/boss completion: navigate to `returnTo` which cascades through the full path
4. For non-chapter packs: automatically shorten path (TRAINING → LESSON → HOME)

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` — `setReturnTo()` logic
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt:450-453` — `onStartSubLesson` callback
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt:1177-1186` — sub-lesson completion navigation

**Verification:**
- Start training from CHAPTER_LESSONS → complete sub-lesson → lands on LESSON
- Back from LESSON → lands on CHAPTER_LESSONS (not HOME)
- Back from CHAPTER_LESSONS → lands on GRAMMAR_STORY_ROADMAP
- Back from GRAMMAR_STORY_ROADMAP → lands on HOME with cleared pack

### Fix 2: Update onSessionDone to respect returnTo chain
**Discrepancy:** N/A | **UC:** UC-101 AC1 | **Spec:** 13#13.3.6

**Description:** Modify `onSessionDone` callback in [GrammarMateApp.kt:620-631](d:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\ui\GrammarMateApp.kt#L620-L631) to navigate to `returnTo` instead of hardcoded HOME.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt:620-631` — `onSessionDone` callback

**Verification:**
- Complete training session → "Done" button → lands on LESSON (not HOME)
- Verify `returnTo` is correctly set based on entry context

### Fix 3: Ensure universal scope (all packs)
**Discrepancy:** N/A | **UC:** UC-101 AC5-6 | **Spec:** 13#13.3.6

**Description:** Apply staircase navigation universally to ALL packs. The path should automatically shorten for non-chapter packs without conditional branching in the navigation code.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` — back handlers
- `app/src/main/java/com/alexpo/grammermate/ui/screens/ChapterLessonsScreen.kt` — back callback

**Verification:**
- Non-chapter pack: TRAINING → LESSON → HOME (correctly shortened)
- Chapter pack: TRAINING → LESSON → CHAPTER_LESSONS → GRAMMAR_STORY_ROADMAP → HOME

---

## Verification Checklist

1. **Main user journey:** Select chapter → select lesson → complete sub-lesson → verify lands on LESSON
2. **LESSON back:** Verify back button from LESSON goes to CHAPTER_LESSONS (if has chapters)
3. **CHAPTER_LESSONS back:** Verify back goes to GRAMMAR_STORY_ROADMAP
4. **GRAMMAR_STORY_ROADMAP back:** Verify back goes to HOME and clears active pack
5. **Non-chapter packs:** Verify path shortens correctly (TRAINING → LESSON → HOME)
6. **Boss completion:** Verify boss finish follows same staircase pattern
7. **Mid-session exit:** Verify exit dialog also respects staircase pattern

## Scope Boundaries

**Do NOT touch:**
- Daily practice navigation flow (separate concern)
- Verb drill navigation flow (separate concern)
- Vocab drill navigation flow (separate concern)
- Story reader navigation (separate concern)
- Settings sheet and ladder navigation (separate concern)
- TTS/ASR or audio-related code (unrelated)
- Mastery/flower state calculation (unrelated)
- Chapter progress tracking (unrelated)
- Pack switching logic (unrelated)

**DO NOT refactor:**
- Do NOT change the overall navigation architecture (AppScreen enum, state-based navigation)
- Do NOT introduce new screens or routes
- Do NOT modify the token-based navigation mechanism (`subLessonFinishedToken`, `bossFinishedToken`)
- Do NOT change how `returnTo` is stored or passed (only what value is set)

## Regression Plan

After all fixes are implemented, run:

1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** 
   - Manual test: Complete full staircase path from chapter to home and back
   - Manual test: Verify non-chapter pack navigation still works correctly
   - Manual test: Verify boss completion follows staircase pattern
   - Manual test: Verify mid-session exit respects staircase pattern
4. **Cross-task regression:**
   - Verify daily practice navigation unaffected
   - Verify verb drill navigation unaffected
   - Verify vocab drill navigation unaffected
   - Verify classic pack navigation (no chapters) unaffected
5. **UC/AC spot-check:** Read UC-101 from `22-use-case-registry.md`, confirm all ACs hold
6. **Spec sync:** If code diverges from spec intentionally, update spec + CHANGELOG + trace-index

## Git

One commit per fix or one combined. Commit message:
```
Implement staircase navigation after training completion

- Add intelligent returnTo logic based on chapter context
- Update onSessionDone to respect returnTo chain
- Apply universal scope for all packs with automatic path shortening
- Verify UC-101 acceptance criteria

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-29 | Fix 1: Implement returnTo architecture decision | OPEN | Awaiting code-architect agent decision |
| 2026-05-29 | Fix 2: Update onSessionDone to respect returnTo chain | OPEN | |
| 2026-05-29 | Fix 3: Ensure universal scope (all packs) | OPEN | |
