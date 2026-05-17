# Scenario 16: Drill Sub-mode — Lesson Drill Training Flow

## Overview

Drill sub-mode provides additional practice on lesson-specific sentence cards within TrainingScreen. Unlike standalone VerbDrill, drill sub-mode has no selection screen — the lesson pack author pre-curates `lesson.drillCards` for this lesson's theme and tense, so the user goes directly into practice. The UI and mechanics are identical to normal training: same navigation, same input modes, same feedback. The only differences are visual (green accents on tense labels and prompt text) and scoring (mastery not counted).

Relationship to standalone VerbDrill: Drill sub-mode is a lesson-scoped VerbDrill without the SelectionScreen. Where VerbDrill lets users choose verb/tense/group, drill sub-mode uses the lesson's pre-built card set.

## Pre-conditions

- User is on LessonRoadmapScreen for a lesson that has `drillFile` in manifest
- `lesson.drillCards` is non-empty
- `hasDrill == true` for the lesson

## Step 1: Entry — Tap Drill Tile

1. User taps Drill tile (LR-08) on LessonRoadmapScreen
2. System calls `showDrillStartDialog(lessonId)`
3. System checks `drillProgressStore.hasProgress(lessonId)`
4. If progress exists → show DrillStartDialog (DG-07) with "Continue" (resume) + "Start Fresh" + "Cancel"
5. If no progress → show DrillStartDialog with "Start" + "Cancel"

### Post-condition
- `drillShowStartDialog == true`

## Step 2: Start Drill Session

### Path A: Start Fresh
1. User taps "Start" (or "Start Fresh")
2. System calls `startDrill(resume=false)`
3. System loads ALL `lesson.drillCards` into `sessionCards`
4. Sets `isDrillMode=true`, `drillCardIndex=0`, `drillTotalCards=drillCards.size`
5. Sets `currentIndex=0`, `currentCard=drillCards[0]`, `subLessonTotal=drillCards.size`
6. Sets `sessionState=PAUSED` (user must press Play)
7. Visual: green prompt text, green tense labels

### Path B: Resume
1. User taps "Continue"
2. System calls `startDrill(resume=true)`
3. Reads saved `drillCardIndex` from `drillProgressStore`
4. Same as Path A but starts at saved index

### Post-condition
- TrainingScreen visible in drill mode
- All drill cards loaded in `sessionCards`
- Standard navigation available (Next/Prev/Pause/Play/Exit)

## Step 3: Card Practice Loop

For each card in `sessionCards`:

1. User sees Russian prompt (parenthetical hints stripped)
2. User answers via VOICE, KEYBOARD, or WORD_BANK
3. System validates answer via `AnswerValidator`
4. **Correct:** Shows green "Correct" result, user taps Next (or auto-advance in VOICE mode)
5. **Incorrect (attempt < 3):** Shows "Incorrect" inline feedback, retries allowed
6. **Incorrect (attempt >= 3):** Shows hint answer in pink card, pauses session
7. Navigation: standard `navigateNext()` advances to next card in `sessionCards`
8. `drillProgressStore.saveDrillProgress(lessonId, currentIndex)` on each advance
9. `recordCardShowForMastery()` returns early — mastery NOT counted

### State invariants
- `isDrillMode == true` throughout
- `sessionCards.size == drillTotalCards`
- Navigation via standard `navigateNext()`/`navigatePrev()` — same as normal training

## Step 4: Drill Completion

1. User answers last card and advances
2. `navigateNext()` detects `currentIndex >= sessionCards.lastIndex` (last card boundary)
3. System calls `finishDrill(lessonId)`:
   - Sets `isDrillMode=false`, clears drill state
   - Increments `subLessonFinishedToken`
   - Clears `drillProgressStore` progress
4. System returns to LessonRoadmapScreen (LESSON)

### Post-condition
- Back on LessonRoadmapScreen
- Drill progress cleared (completed)
- Flower states refreshed

## Step 5: Drill Exit (Mid-session)

1. User taps Exit (StopCircle) or back gesture
2. ExitConfirmationDialog appears
3. User confirms exit
4. System calls `exitDrillMode()`:
   - Saves current `drillCardIndex` to `drillProgressStore`
   - Sets `isDrillMode=false`, clears drill state
5. System returns to LessonRoadmapScreen (LESSON)

### Post-condition
- Back on LessonRoadmapScreen
- Drill progress SAVED (can resume later)

## Edge Cases

- **Empty drillCards:** `startDrill` returns early, no session started
- **Single card drill:** Works normally, completion on first advance
- **Voice auto-trigger:** Same as normal training — 200ms after card load when VOICE mode
- **Word bank:** Generated from drill card answers, same as normal training
- **Report/flag:** Available in drill mode, flagged cards advance via `advanceDrillCard` equivalent (skip current card)

## Differences from Normal Training

| Aspect | Normal Training | Drill Sub-mode |
|--------|----------------|----------------|
| Card source | MixedReviewScheduler | lesson.drillCards |
| Cards per session | 10 (sub-lesson size) | All drill cards |
| Mastery counted | Yes (VOICE/KEYBOARD only) | No |
| Visual theme | Default | Default |
| Progress store | progressStore + masteryStore | drillProgressStore |
| Exit destination | LESSON | LESSON |
| Navigation | navigateNext/navigatePrev | SAME (navigateNext/navigatePrev) |
