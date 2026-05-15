# User Journey Models — Step-by-Step Behavioral Trace

> Version: 1.0 | Date: 2026-05-16 | Branch: main
> Purpose: Model every user path from app launch to feature completion, comparing spec expectations vs actual code behavior. Discrepancies are flagged for fix.

---

## Journey 1: First Launch (Cold Start)

### Step 1.1: User taps app icon

| Aspect | Detail |
|--------|--------|
| **User action** | Taps GrammarMate icon on home screen |
| **System response** | MainActivity.onCreate() → setContentView → AppRoot composable |
| **What user sees** | Splash screen / loading indicator |
| **Navigation** | None yet — waiting for initialization |
| **Spec source** | 13-app-entry-and-navigation.md §1 |
| **Discrepancy** | None |

### Step 1.2: App initialization

| Aspect | Detail |
|--------|--------|
| **System response** | AppRoot checks backup restore status. If first launch: no backup exists, proceeds directly. If returning user: checks `restoreState` |
| **What user sees** | Loading spinner on StartupScreen |
| **Navigation** | Remains on StartupScreen until `restoreState.status == DONE` |
| **Spec source** | 13-app-entry-and-navigation.md §2 |
| **Discrepancy** | None |

### Step 1.3: First launch — no lessons

| Aspect | Detail |
|--------|--------|
| **System response** | `forceReloadDefaultPacks()` seeds default lesson packs from assets. HomeScreen loads with lesson data |
| **What user sees** | HomeScreen with: language selector, lesson grid (1+ lessons), "Continue Learning" card, Daily Practice tile, Verb Drill tile (if pack has verb drill), Flashcards tile (if pack has vocab drill) |
| **Navigation** | StartupScreen → HOME |
| **Elements visible** | HS-01 (app title), HS-02 (language selector), HS-03 (lesson grid), HS-04 (Continue Learning card), HS-05 (Daily Practice tile), HS-06 (Verb Drill tile if hasVerbDrill), HS-07 (Flashcards tile if hasVocabDrill), HS-08 (Settings gear), HS-09 (streak counter) |
| **Spec source** | 19-screen-catalog.md §1, 23-screen-elements.md |
| **Discrepancy** | None — first launch flow is well-defined |

### Step 1.3b: Returning user — restore from backup

| Aspect | Detail |
|--------|--------|
| **System response** | If backup file found in Downloads/BaseGrammy/: shows restore prompt |
| **What user sees** | Restore dialog: "Backup found. Restore?" with Yes/No buttons |
| **Navigation** | Remains on StartupScreen until user decides |
| **Spec source** | 13-app-entry-and-navigation.md §2.3 |
| **Discrepancy** | None |

---

## Journey 2: Home Screen — Navigation Hub

### Step 2.1: User sees Home Screen

| Aspect | Detail |
|--------|--------|
| **What user sees** | Full HomeScreen layout: title bar with language name, streak counter, settings gear, lesson grid, daily practice tile, drill tiles |
| **Available actions** | Tap lesson card → go to Lesson Roadmap (Journey 3) |
| | Tap "Daily Practice" → start daily session (Journey 6) |
| | Tap "Verb Drill" → go to Verb Drill (Journey 7) |
| | Tap "Flashcards" → go to Vocab Drill (Journey 8) |
| | Tap settings gear → open Settings sheet |
| | Tap language selector → change active language/pack |
| **Elements** | HS-01 through HS-14 (14 elements total) |
| **Spec source** | 19-screen-catalog.md §1 |
| **Discrepancy** | None |

### Step 2.2: User taps lesson card

| Aspect | Detail |
|--------|--------|
| **User action** | Taps a lesson card in the grid |
| **System response** | `vm.selectLesson(lessonId)` loads lesson data, computes sub-lesson states, flower states |
| **Navigation** | HOME → LESSON (LessonRoadmapScreen) |
| **What user sees next** | LessonRoadmapScreen with sub-lesson grid, flower states, boss tile (if unlocked) |
| **Spec source** | 07-app-router.md §3.2 |
| **Discrepancy** | None |

### Step 2.3: User taps "Continue Learning" (primary action)

| Aspect | Detail |
|--------|--------|
| **User action** | Taps primary action card on HomeScreen |
| **System response** | Same as tapping the next uncompleted lesson card |
| **Navigation** | HOME → LESSON |
| **Spec source** | 19-screen-catalog.md §1.4 |
| **Discrepancy** | None |

### Step 2.4: User opens Settings

| Aspect | Detail |
|--------|--------|
| **User action** | Taps settings gear icon |
| **System response** | ModalBottomSheet opens with settings content |
| **What user sees** | Settings sheet: language management, lesson pack import, TTS download, ladder access, theme toggle, about section |
| **Navigation** | No navigation — modal overlay on current screen |
| **Back behavior** | Back press closes settings sheet. On TRAINING: also calls `vm.resumeFromSettings()` if card exists |
| **Spec source** | 19-screen-catalog.md §11 |
| **Discrepancy** | None |

---

## Journey 3: Lesson Roadmap — Select Sub-Lesson

### Step 3.1: User sees LessonRoadmapScreen

| Aspect | Detail |
|--------|--------|
| **What user sees** | Lesson title, back arrow, grid of sub-lesson tiles with flower states (LOCKED/SEED/SPROUT/BLOOM), boss tile (if unlocked: completedSubLessonCount >= 15) |
| **Available actions** | Tap sub-lesson tile → start training (Journey 4) |
| | Tap boss tile → start boss battle (Journey 5) |
| | Tap back arrow → return to HOME |
| **Elements** | LR-01 through LR-12 |
| **Spec source** | 19-screen-catalog.md §2 |
| **Discrepancy** | None |

### Step 3.2: User taps sub-lesson tile

| Aspect | Detail |
|--------|--------|
| **User action** | Taps a sub-lesson tile |
| **System response** | `vm.selectSubLesson(index)` builds session cards, initializes state |
| **Navigation** | LESSON → TRAINING |
| **What user sees next** | TrainingScreen with first card, PAUSED state (Play button visible) |
| **Spec source** | 07-app-router.md §3.3, scenario-01 §2 |
| **Discrepancy** | None |

### Step 3.3: User taps boss tile

| Aspect | Detail |
|--------|--------|
| **User action** | Taps boss tile (only visible when unlocked) |
| **System response** | `vm.startBossLesson()` or `vm.startBossMega()` — shuffles boss cards, resets counters |
| **Navigation** | LESSON → TRAINING (boss mode) |
| **What user sees next** | TrainingScreen with "Boss Battle" header, PAUSED state |
| **Spec source** | scenario-09 §2 |
| **Discrepancy** | **BUG-NAV-001**: Boss battle records mastery via `recordCardShowForMastery()` at 3 code sites WITHOUT checking `bossActive`. Spec 18.8.4 says boss battles should be separate from mastery/flower system. This inflates `uniqueCardShows` and advances SRS intervals during boss battles. |

---

## Journey 4: Training Session — Card-by-Card

### Step 4.1: User sees first card (PAUSED state)

| Aspect | Detail |
|--------|--------|
| **What user sees** | Card with Russian prompt, input area, Play button, input mode buttons (Mic/Keyboard/Book), Next arrow, Show Answer eye icon |
| **Session state** | PAUSED — timer not running, Check button disabled |
| **Available actions** | Press Play → start session (Step 4.2) |
| | Switch input mode → change to VOICE/KEYBOARD/WORD_BANK |
| | Press Next → advance to next card without starting timer |
| | Press Show Answer → reveal answer |
| | Press exit (StopCircle) → show exit dialog |
| **Elements** | TS-01 through TS-37 |
| **Spec source** | 19-screen-catalog.md §3, scenario-01 §3 |
| **Discrepancy** | None |

### Step 4.2: User presses Play (PAUSED → ACTIVE)

| Aspect | Detail |
|--------|--------|
| **User action** | Taps Play button |
| **System response** | `togglePause()` → `startSession()`: sets sessionState=ACTIVE, starts timer, clears inputText |
| **What changes visually** | Play icon → Pause icon. Timer starts ticking. Check button becomes enabled (when input entered). If VOICE mode: speech recognition auto-launches after 200ms |
| **State transition** | `PAUSED → ACTIVE` |
| **Spec source** | scenario-01 §3.1 |
| **Discrepancy** | None |

### Step 4.3: User types answer and presses Check

| Aspect | Detail |
|--------|--------|
| **User action** | Types answer in KEYBOARD mode, taps Check button |
| **Preconditions** | `sessionState == ACTIVE && inputText.isNotBlank() && currentCard != null` |
| **System response** | `submitAnswer()` → normalizes input → compares against accepted answers |

#### 4.3a: Correct answer

| Aspect | Detail |
|--------|--------|
| **What user sees** | Green "Correct" label (TS-26). Success sound plays |
| **State change** | `correctCount++`, `lastResult = true` |
| **If mid-card**: | Auto-advances to next card via `nextCardInternal()`: clears input, resets attempt counter, new card loads, timer continues |
| **If last card**: | Timer pauses, `subLessonFinishedToken` incremented, auto-navigates to LESSON |
| **Spec source** | scenario-02 §3 |
| **Discrepancy** | **BUG-NAV-002**: After correct answer on mid-card, session stays ACTIVE. There is no visual cue (pulsing Next, auto-advance) telling the user the answer was accepted and they're on the next card. The card content changes but the transition is instant and easy to miss, especially in VOICE mode where auto-submit fires. |

#### 4.3b: Incorrect answer (attempts 1-2)

| Aspect | Detail |
|--------|--------|
| **What user sees** | Red "Incorrect" label. Error sound plays. Input field clears (VOICE mode) or stays (KEYBOARD mode) |
| **State change** | `incorrectCount++`, `incorrectAttemptsForCard++` |
| **User can** | Type new answer and retry. In VOICE mode: speech recognition auto-re-triggers |
| **Spec source** | scenario-02 §4 |
| **Discrepancy** | None |

#### 4.3c: Incorrect answer (attempt 3)

| Aspect | Detail |
|--------|--------|
| **What user sees** | Answer hint card appears showing all accepted answers joined with " / " |
| **State change** | `sessionState = HINT_SHOWN`, timer pauses, `incorrectAttemptsForCard` resets to 0 |
| **User can** | Press Next to advance (→ Step 4.5) or Press Play (→ Step 4.2b) |
| **Spec source** | scenario-02 §5 |
| **Discrepancy** | **BUG-NAV-003**: Play button from HINT_SHOWN calls `startSession()` which sets `sessionState = ACTIVE` and clears `inputText`, but does NOT clear `answerText` (hint text). The hint card stays visible while the session is ACTIVE and the Check button re-enables. The user can submit again with the hint text still showing. |

### Step 4.4: User presses Pause (ACTIVE → PAUSED)

| Aspect | Detail |
|--------|--------|
| **User action** | Taps Pause button (icon is Pause when ACTIVE) |
| **System response** | `togglePause()`: timer pauses, `sessionState = PAUSED` |
| **What changes visually** | Pause icon → Play icon. Timer stops |
| **State transition** | `ACTIVE → PAUSED` |
| **Spec source** | scenario-01 §3.2 |
| **Discrepancy** | None |

### Step 4.5: User presses Next (any state)

| Aspect | Detail |
|--------|--------|
| **User action** | Taps right arrow (Next) button |
| **System response** | `nextCard()`: advances index, loads new card, clears all card-local state, sets `sessionState = ACTIVE` |
| **Enabled when** | `hasCards` (currentCard != null) — **always enabled, no sessionState guard** |
| **If was HINT_SHOWN**: | Resumes timer |
| **If last card**: | `coerceAtMost(lastIndex)` — stays on last card, no completion signal |
| **Spec source** | scenario-01 §3.3 |
| **Discrepancy** | **BUG-NAV-004**: Next button is always enabled, even during ACTIVE session. After auto-advance from correct answer, user can press Next and skip the next card without answering. **BUG-NAV-005**: Pressing Next on the last card stays on last card with no visual feedback that the session is complete. Only the Stop/Exit button provides the exit path. |

### Step 4.6: User presses Show Answer (eye icon)

| Aspect | Detail |
|--------|--------|
| **User action** | Taps eye icon |
| **System response** | `showAnswer()`: pauses timer, reveals all accepted answers, sets `sessionState = HINT_SHOWN` |
| **What user sees** | Hint card with correct answer(s). Check button disabled |
| **State transition** | `any → HINT_SHOWN` |
| **Spec source** | scenario-05 §4 |
| **Discrepancy** | None — behavior matches spec |

### Step 4.7: User presses Exit (StopCircle)

| Aspect | Detail |
|--------|--------|
| **User action** | Taps exit/stop button |
| **System response** | Exit confirmation dialog appears |
| **Dialog options** | "End session? Your progress will be saved." with "End" / "Cancel" |
| **On confirm**: | If boss active → `finishBoss()` → LESSON. If drill mode → `exitDrillMode()` → LESSON. If normal → `finishSession()` → LESSON |
| **On cancel**: | Dialog dismissed, session resumes |
| **Spec source** | 23-screen-elements.md TCS-24 |
| **Discrepancy** | None |

### Step 4.8: Sub-lesson finishes (auto-navigation)

| Aspect | Detail |
|--------|--------|
| **Trigger** | Last card answered correctly. `subLessonFinishedToken` incremented |
| **System response** | GrammarMateApp.kt detects token change via `LaunchedEffect`, calls `onNavigate(LESSON)` |
| **What user sees** | Brief flash of TrainingScreen → LessonRoadmapScreen |
| **Navigation** | TRAINING → LESSON (automatic) |
| **Spec source** | 07-app-router.md §3.5 |
| **Discrepancy** | None — token-based auto-navigation works as spec'd |

---

## Journey 5: Boss Battle

### Step 5.1: Boss session starts (PAUSED)

| Aspect | Detail |
|--------|--------|
| **What user sees** | TrainingScreen with "Boss Battle" / "Review Session" header, first card loaded, PAUSED state |
| **Available actions** | Press Play to begin |
| **Differences from normal training** | No time limit (measures elapsed time only). Progress bar shows boss completion %. Unlimited retries |
| **Spec source** | scenario-09 §2 |
| **Discrepancy** | **BUG-NAV-001** (same as Step 3.3) |

### Step 5.2: Boss card-by-card

| Aspect | Detail |
|--------|--------|
| **Same as Journey 4** | Play/Pause/Check/Retry work identically to normal training |
| **Additional**: | Boss progress bar advances with each card. Reward thresholds: BRONZE >50%, SILVER >75%, GOLD 100% |

### Step 5.3: Boss reward pause

| Aspect | Detail |
|--------|--------|
| **Trigger** | Boss progress crosses reward threshold (33/66/100% for BRONZE/SILVER/GOLD) |
| **System response** | Session pauses, reward message displayed |
| **What user sees** | Reward overlay with tier name |
| **User action** | Dismiss reward → session resumes |
| **Spec source** | scenario-09 §4 |
| **Discrepancy** | **BUG-NAV-006**: `clearBossRewardMessage()` has complex conditional for `shouldResumeTimer` reading `bossActive`, `sessionState`, `currentCard`, `inputMode` at different points. If state changes between read and write, resume decision may be stale. |

### Step 5.4: Boss finishes

| Aspect | Detail |
|--------|--------|
| **Trigger** | All boss cards answered (last card correct) |
| **System response** | `finishBoss()`: calculates reward, records in ProgressStore, restores previous lesson state, rebuilds cards |
| **Navigation** | TRAINING → LESSON (via `bossFinishedToken`) |
| **What user sees** | Brief flash → LessonRoadmapScreen → Reward dialog with trophy |
| **Spec source** | scenario-09 §5 |
| **Discrepancy** | **BUG-NAV-007**: Reward overwrite — replaying boss overwrites previous reward with latest result (not best). A GOLD can be replaced by BRONZE on replay. |

---

## Journey 6: Daily Practice

### Step 6.1: User taps Daily Practice tile

| Aspect | Detail |
|--------|--------|
| **User action** | Taps "Daily Practice" tile on HomeScreen |
| **System response** | Checks `hasResumableDailySession()`. If resumable → shows DailyResumeDialog. If not → starts new session |
| **What user sees** | Loading dialog (non-cancelable) while session builds on IO dispatcher |
| **Navigation** | HOME → DAILY_PRACTICE (after loading completes) |
| **Spec source** | 19-screen-catalog.md §1.5 |
| **Discrepancy** | **BUG-NAV-008**: If coroutine fails silently, user stays on HOME with loading cleared but no navigation and no error message. |

### Step 6.2: Block 1 — Translate (10 cards)

| Aspect | Detail |
|--------|--------|
| **What user sees** | DailyPracticeScreen with sentence card, input controls (VOICE/KEYBOARD/WORD_BANK rotating), progress indicator "1/10" |
| **Session behavior** | Same Check/Retry as normal training but with DailyPracticeSessionProvider |
| **Hint behavior** | 3 wrong attempts → shows answer. Press Play → advances to next card (same as TrainingScreen bug BUG-NAV-003) |
| **Auto-advance** | Correct voice answer → 400ms delay → auto-advance to next card |
| **Spec source** | scenario-06 §3 |
| **Discrepancy** | **BUG-NAV-009**: Daily practice has NO retry mechanism after wrong answer display. Single wrong answer cycle → hint shown → must advance. Inconsistent with regular training which allows 3 retries per card. |

### Step 6.3: Block 2 — Vocab Flashcards (5 cards)

| Aspect | Detail |
|--------|--------|
| **Transition** | BlockSparkleOverlay shows "Next: Vocabulary" for ~800ms |
| **What user sees** | Flashcard with word, translation, forms, collocations. Rating buttons: Again/Hard/Good/Easy |
| **User action** | Read card → tap rating button → auto-advance to next |
| **Spec source** | scenario-06 §4 |
| **Discrepancy** | None |

### Step 6.4: Block 3 — Verbs (10 cards)

| Aspect | Detail |
|--------|--------|
| **Transition** | BlockSparkleOverlay shows "Next: Verbs" for ~800ms |
| **What user sees** | Sentence card with verb conjugation prompt, KEYBOARD/WORD_BANK input |
| **Session behavior** | Same as Block 1 but no VOICE mode for verbs |
| **Spec source** | scenario-06 §5 |
| **Discrepancy** | None |

### Step 6.5: Daily session completes

| Aspect | Detail |
|--------|--------|
| **Trigger** | Last task in Block 3 completed |
| **System response** | `endSession()`: sets `active=false`, `finishedToken=true` |
| **What user sees** | CompletionScreen with "Daily practice complete!" message |
| **User action** | Tap "Exit" → `cancelDailySession()` → navigate HOME |
| **Navigation** | DAILY_PRACTICE → HOME |
| **Spec source** | scenario-06 §6 |
| **Discrepancy** | **BUG-NAV-010**: Completion sparkle may not be visible. `onComplete` callback calls `cancelDailySession()` which may trigger navigation before the sparkle animation renders. |

### Step 6.6: User presses back during Daily Practice

| Aspect | Detail |
|--------|--------|
| **In-screen back arrow** | Shows exit confirmation dialog: "Exit practice? Progress lost." |
| **System back button** | **Navigates directly to HOME without confirmation** |
| **Spec source** | 19-screen-catalog.md §5, 23-screen-elements.md DP-30 |
| **Discrepancy** | **BUG-NAV-011**: System BackHandler on DAILY_PRACTICE navigates HOME directly (GrammarMateApp.kt line 427-429). Spec says exit confirmation dialog should appear for system back too. In-screen back arrow works correctly. |

---

## Journey 7: Verb Drill

### Step 7.1: User taps Verb Drill tile

| Aspect | Detail |
|--------|--------|
| **User action** | Taps "Verb Drill" tile on HomeScreen |
| **Navigation** | HOME → VERB_DRILL |
| **What user sees** | VerbDrillScreen: Selection sub-screen with Tense dropdown, Group dropdown, Start button |
| **Spec source** | scenario-07 §1 |
| **Discrepancy** | None |

### Step 7.2: User starts drill session

| Aspect | Detail |
|--------|--------|
| **User action** | Selects tense/group, taps Start |
| **System response** | VerbDrillViewModel filters cards, loads 10-card batch |
| **What user sees** | Card session with prompt, input field, Check button |
| **Spec source** | scenario-07 §2 |
| **Discrepancy** | None |

### Step 7.3: Check answer (VerbDrill)

| Aspect | Detail |
|--------|--------|
| **Correct**: | Sets pendingCard, records answer time |
| **Wrong (attempts < 3)**: | Shows "Incorrect" feedback, auto-triggers voice if VOICE mode |
| **Wrong (attempts >= 3)**: | Shows hint (pink card), pauses session |
| **Spec source** | scenario-07 §3 |
| **Discrepancy** | **BUG-NAV-012**: Auto-advance race condition. `LaunchedEffect` auto-advances after 500ms on correct voice answer. Manual Next press during 500ms window causes double `nextCard()` call, potentially skipping a card. |

### Step 7.4: Play/Pause in VerbDrill

| Aspect | Detail |
|--------|--------|
| **If paused with hint shown**: | Play advances to NEXT card (calls `nextCard()`) |
| **If paused without hint**: | Play resumes CURRENT card (calls `sm.resume()`) |
| **Spec source** | 23-screen-elements.md VD-36 |
| **Discrepancy** | **BUG-NAV-013**: Play button has overloaded semantics — "advance" vs "resume" with no visual differentiation. User cannot tell which behavior will occur. |

### Step 7.5: VerbDrill completion and exit

| Aspect | Detail |
|--------|--------|
| **Completion**: | Stats screen with correct/incorrect counts. "More" button or "Exit" button |
| **Exit**: | Returns to selection screen (NOT HomeScreen) |
| **System back**: | Navigates to HOME |
| **Spec source** | scenario-07 §5 |
| **Discrepancy** | **BUG-NAV-014**: In-app exit controls (back arrow, completion Exit) return to selection screen, not HOME. Only system back goes HOME. Inconsistent navigation. |

---

## Journey 8: Vocab Drill

### Step 8.1: User taps Flashcards tile

| Aspect | Detail |
|--------|--------|
| **User action** | Taps "Flashcards" tile on HomeScreen |
| **Navigation** | HOME → VOCAB_DRILL |
| **What user sees** | VocabDrillScreen: Selection with direction chips, POS chips, frequency chips, Start button |
| **Spec source** | scenario-08 §1 |
| **Discrepancy** | None |

### Step 8.2: Vocab card flow

| Aspect | Detail |
|--------|--------|
| **Card front**: | POS badge, rank badge, word (32sp bold). TTS button (IT→RU only). Mic button for voice input. Skip/Flip buttons |
| **Voice input**: | Up to 3 attempts. Match against synonyms (split by "/"). Auto-flip on correct or max attempts |
| **Card back**: | Word + translation + forms + collocations. 4 rating buttons: Again/Hard/Good/Easy |
| **Rating → advance**: | Auto-advance to next card |
| **Spec source** | scenario-08 §3-4 |
| **Discrepancy** | **BUG-NAV-015**: `isLearned` threshold mismatch. Code uses `LEARNED_THRESHOLD = 3` (step >= 3). Card back mastery indicator shows "Learned" at step >= 9. Data says learned at step 3, UI says step 9. |

### Step 8.3: Vocab drill exit

| Aspect | Detail |
|--------|--------|
| **System back**: | Calls `vm.refreshVocabMasteryCount()` then navigates HOME |
| **In-screen back**: | Same |
| **Spec source** | scenario-08 §6 |
| **Discrepancy** | **BUG-NAV-016**: `refreshVocabMasteryCount()` is called on EVERY back press, even if no cards were practiced. Unnecessary I/O but not harmful. |

---

## Discrepancy Summary

| ID | Severity | Location | Description |
|----|----------|----------|-------------|
| BUG-NAV-001 | HIGH | BossOrchestrator, SessionRunner | Boss records mastery despite spec saying it shouldn't. 3 code sites call `recordCardShowForMastery()` without `bossActive` guard |
| BUG-NAV-002 | MEDIUM | TrainingScreen | No visual cue after correct answer auto-advance. Card changes instantly with no transition feedback |
| BUG-NAV-003 | HIGH | SessionRunner.togglePause() | Play from HINT_SHOWN doesn't clear `answerText`. Hint persists while session is ACTIVE |
| BUG-NAV-004 | MEDIUM | TrainingScreen NavigationRow | Next button always enabled — can skip cards during ACTIVE session |
| BUG-NAV-005 | LOW | SessionRunner.nextCardInternal() | Next on last card stays on last card with no completion feedback |
| BUG-NAV-006 | MEDIUM | BossOrchestrator.clearBossRewardMessage() | Stale state read in shouldResumeTimer conditional |
| BUG-NAV-007 | LOW | BossOrchestrator | Reward overwrite on replay (latest, not best) |
| BUG-NAV-008 | LOW | GrammarMateApp.kt Daily start | Silent failure if coroutine fails — no error feedback |
| BUG-NAV-009 | MEDIUM | DailyPracticeCoordinator | No retry after wrong answer display — inconsistent with training |
| BUG-NAV-010 | LOW | DailyPracticeScreen | Completion sparkle may be skipped by navigation race |
| BUG-NAV-011 | HIGH | GrammarMateApp.kt BackHandler | System back on DAILY_PRACTICE exits without confirmation (spec requires dialog) |
| BUG-NAV-012 | HIGH | VerbDrillScreen LaunchedEffect | Auto-advance race with manual Next — card skip possible |
| BUG-NAV-013 | MEDIUM | VerbDrillCardSessionProvider | Play button overloaded: "advance" vs "resume" with no visual difference |
| BUG-NAV-014 | LOW | VerbDrillScreen exit | In-app exit → selection screen, system back → HOME. Inconsistent |
| BUG-NAV-015 | MEDIUM | VocabDrill mastery indicator | isLearned threshold: data says step>=3, UI shows "Learned" at step>=9 |
| BUG-NAV-016 | LOW | GrammarMateApp.kt VOCAB_DRILL back | Unnecessary refreshVocabMasteryCount on every exit |

---

## Structural Issues (Cross-Cutting)

| ID | Severity | Description |
|----|----------|-------------|
| STRUCT-001 | HIGH | Three different state mechanisms: TrainingScreen uses `SessionState` enum, VerbDrill uses `CardSessionStateMachine` with `isPaused` + `hintAnswer`, DailyPractice uses its own `DailyPracticeSessionProvider`. No unified model. |
| STRUCT-002 | MEDIUM | `AFTER_CHECK` SessionState defined but never used — dead code adding confusion about valid transitions |
| STRUCT-003 | MEDIUM | No single source of truth for button enabled state. Check button enabled condition is computed inline in composable rather than derived from a shared state property |
| STRUCT-004 | LOW | `inputText` clearing strategy inconsistent: VOICE mode clears on wrong, KEYBOARD keeps text on wrong. Not a bug but inconsistent UX |
