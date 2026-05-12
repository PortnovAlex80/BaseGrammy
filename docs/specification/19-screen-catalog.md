# 19. Screen Catalog

## Overview

GrammarMate contains **10 distinct screens** (7 full screens with `AppScreen` enum values, 3 sub-screens), **1 modal bottom sheet** (SettingsSheet), and **16 dialogs**. Navigation is managed via a private `AppScreen` enum inside `GrammarMateApp.kt` -- there is no Jetpack Navigation component. Screen state is held in `remember { mutableStateOf(parseScreen(state.initialScreen)) }` and transitions occur by reassigning this variable.

**Total screen count**: 10 screens + 1 modal sheet + 16 dialogs = 27 UI surfaces.

**Navigation pattern**: Single-activity, no Navigation Component. `GrammarMateApp()` is the root composable that routes between screens via `when (screen)` on `AppScreen`. Dialogs and sheets are conditionally rendered overlays. Back navigation is handled per-screen via `BackHandler` composables.

**Source files**:
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` -- AppScreen enum, HomeScreen, LessonRoadmapScreen, TrainingScreen, StoryQuizScreen, LadderScreen, SettingsSheet, all dialogs
- `app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt` -- StartupScreen
- `app/src/main/java/com/alexpo/grammermate/ui/DailyPracticeScreen.kt` -- DailyPracticeScreen
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt` -- VerbDrillScreen (Selection, Session, Completion)
- `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillScreen.kt` -- VocabDrillScreen (Selection, CardScreen, Completion)
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingCardSession.kt` -- Reusable card session component used by VerbDrill and DailyPractice

---

## Navigation Graph

```
[Startup] --(restore done)--> [HOME]
                                  |
                                  +-- Primary Action Card / "Continue Learning" / LessonTile --> [LESSON]
                                  |       |
                                  |       +-- LessonTile (exercise) --> [TRAINING] --(exit)--> [LESSON]
                                  |       +-- DrillTile --> DrillStartDialog --> [TRAINING] (drill mode)
                                  |       +-- BossTile "Review" --> [TRAINING] (boss mode)
                                  |       +-- BossTile "Mega" --> [TRAINING] (boss mega mode)
                                  |
                                  +-- DailyPracticeEntryTile --> [DAILY_PRACTICE] --(exit)--> [HOME]
                                  |
                                  +-- VerbDrillEntryTile --> [VERB_DRILL] --(exit/back)--> [HOME]
                                  |       (Selection --> Session --> Completion)
                                  |
                                  +-- VocabDrillEntryTile --> [VOCAB_DRILL] --(exit/back)--> [HOME]
                                  |       (Selection --> CardScreen --> Completion)
                                  |
                                  +-- Settings gear --> SettingsSheet (ModalBottomSheet)
                                          +-- "Show Ladder" --> [LADDER] --(back)--> caller

[TRAINING] SettingsSheet --> "Show Ladder" --> [LADDER] --(back)--> [TRAINING]
[LESSON] Story trigger --> [STORY] --(close/complete)--> [LESSON]

AppScreen.ELITE --> redirects to HOME (backward compat, enum kept)
AppScreen.VOCAB --> redirects to HOME (backward compat, enum kept)

Global dialogs (overlay on any screen):
  WelcomeDialog, StreakDialog, BossRewardDialog, BossErrorDialog,
  StoryErrorDialog, TtsDownloadDialog, MeteredNetworkDialog (TTS),
  AsrMeteredNetworkDialog
```

---

## Screen Inventory

| # | Screen | AppScreen | Source File | Purpose | Routes In | Routes Out |
|---|--------|-----------|-------------|---------|-----------|------------|
| 1 | StartupScreen | N/A (AppRoot) | AppRoot.kt | Loading state during backup restore | App launch | HOME (restore done) |
| 2 | HomeScreen | HOME | GrammarMateApp.kt:819 | Main hub with lesson grid, drill tiles, daily practice | Start, back from LESSON/TRAINING/DAILY/VERB/VOCAB | LESSON, DAILY_PRACTICE, VERB_DRILL, VOCAB_DRILL |
| 3 | LessonRoadmapScreen | LESSON | GrammarMateApp.kt:1178 | Sub-lesson grid with boss/drill tiles | HOME (tap lesson), TRAINING (session end) | HOME (back), TRAINING (start sub-lesson/boss/drill) |
| 4 | TrainingScreen | TRAINING | GrammarMateApp.kt:2554 | Card-by-card translation practice | LESSON (start sub-lesson/boss/drill) | LESSON (exit/session end) |
| 5 | DailyPracticeScreen | DAILY_PRACTICE | DailyPracticeScreen.kt | 3-block daily session (translate, vocab, verbs) | HOME (tap Daily Practice) | HOME (exit/complete) |
| 6 | VerbDrillScreen | VERB_DRILL | VerbDrillScreen.kt | Verb conjugation drill (selection + session + completion) | HOME (tap Verb Drill) | HOME (back/exit) |
| 7 | VocabDrillScreen | VOCAB_DRILL | VocabDrillScreen.kt | Flashcard vocab drill (selection + cards + completion) | HOME (tap Flashcards) | HOME (back/exit) |
| 8 | LadderScreen | LADDER | GrammarMateApp.kt:2388 | Interval ladder overview | SettingsSheet (Show Ladder) | caller (back) |
| 9 | StoryQuizScreen | STORY | GrammarMateApp.kt:1599 | Reading comprehension quiz | LESSON (story phase) | LESSON (close/complete) |
| 10 | SettingsSheet | N/A (ModalBottomSheet) | GrammarMateApp.kt:1951 | Full app configuration | Any screen (gear icon) | Dismiss (returns to caller) |

### Dialog Inventory

| # | Dialog | Trigger | Source Location |
|---|--------|---------|-----------------|
| D1 | WelcomeDialog | First launch (userName == "GrammarMateUser") | GrammarMateApp.kt:759 |
| D2 | StreakDialog | After session (streakMessage != null) | GrammarMateApp.kt:659 |
| D3 | BossRewardDialog | Boss completion (bossRewardMessage + bossReward) | GrammarMateApp.kt:634 |
| D4 | BossErrorDialog | Boss error (bossErrorMessage != null) | GrammarMateApp.kt:622 |
| D5 | StoryErrorDialog | Story error (storyErrorMessage != null) | GrammarMateApp.kt:610 |
| D6 | DrillStartDialog | Tap DrillTile in LESSON | GrammarMateApp.kt:1495 |
| D7 | ExitConfirmationDialog | Back/Stop during TRAINING or DAILY_PRACTICE | GrammarMateApp.kt:535 |
| D8 | TtsDownloadDialog | Tap TTS button without model | GrammarMateApp.kt:3414 |
| D9 | MeteredNetworkDialog (TTS) | TTS download on metered connection | GrammarMateApp.kt:3478 |
| D10 | AsrMeteredNetworkDialog | ASR download on metered connection | GrammarMateApp.kt:3496 |
| D11 | DailyResumeDialog | Tap Daily Practice with resumable session | GrammarMateApp.kt:572 |
| D12 | LessonLockedDialog | Tap EMPTY lesson tile | GrammarMateApp.kt:1028 |
| D13 | EarlyStartDialog | Tap locked lesson or sub-lesson | GrammarMateApp.kt:1040, 1358 |
| D14 | HowThisTrainingWorksDialog | Tap "How This Training Works" button | GrammarMateApp.kt:1011 |
| D15 | DailyPracticeLoadingOverlay | During session initialization | GrammarMateApp.kt:504 |
| D16 | ExportBadSentencesResultDialog | After exporting bad sentences | GrammarMateApp.kt:3062 |

---

## Per-Screen Details

### 1. StartupScreen

- **AppScreen enum**: N/A (not a routed screen -- renders in `AppRoot()` before `GrammarMateApp()`)
- **Source file**: `app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt` (lines 21-52)
- **Parent**: App launch entry point
- **Key UI elements**:
  - `CircularProgressIndicator` (centered)
  - `Text` status message: "Restoring backup..." (IN_PROGRESS), "Waiting for backup folder..." (NEEDS_USER), "Preparing..." (other)
- **State dependencies**: `RestoreNotifier.restoreState` (StateFlow of RestoreStatus)
- **User interactions**: None (blocking wait)
- **Business rules**:
  - Cannot be dismissed by user
  - Transitions to HOME when `restoreState.status == DONE`
  - `GrammarMateTheme` wraps the entire startup flow
- **Cross-reference**: Matches Russian spec section 1 exactly. No discrepancies.

---

### 2. HomeScreen

- **AppScreen enum**: `HOME`
- **Source file**: `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` (composable at line 819)
- **Parent**: Default landing screen after startup
- **Key UI elements**:
  - **Profile header**: Avatar circle (40dp, primary background, initials) + userName (SemiBold) + LanguageSelector dropdown + Settings gear IconButton
  - **Primary action Card**: Clickable card showing activePackDisplayName ("Continue Learning" / "Start learning") + lesson progress hint ("Lesson N. Exercise X/Y")
  - **Grammar Roadmap**: "Grammar Roadmap" header + 4-column `LazyVerticalGrid` of LessonTile cards (72dp). Each shows: lesson number, flower emoji (LOCKED/UNLOCKED/SEED/SPROUT/FLOWER/EMPTY), mastery percentage
  - **Drill tiles row**: VerbDrillEntryTile (FitnessCenter icon) + VocabDrillEntryTile (MenuBook icon + mastered count). Row visible when `hasVerbDrill || hasVocabDrill`
  - **DailyPracticeEntryTile**: primaryContainer card with "Daily Practice" title, "Practice all sub-lessons" subtitle, PlayArrow icon
  - **Legend**: Flower state emoji legend text
  - **Action buttons**: "How This Training Works" OutlinedButton + "Continue Learning" Button
- **State dependencies**: `userName`, `selectedLanguageId`, `languages`, `lessons`, `lessonFlowers`, `activePackId`, `activePackLessonIds`, `sessionState`, `correctCount`, `incorrectCount`, `activeTimeMs`, `testMode`, `completedSubLessonCount`, `subLessonCount`, `selectedLessonId`, `installedPacks`, `vocabMasteredCount`
- **User interactions**:
  - Tap avatar/settings gear -> SettingsSheet
  - Tap language selector -> language dropdown menu
  - Tap lesson tile -> selectLesson + navigate to LESSON
  - Tap locked tile -> EarlyStartDialog or LessonLockedDialog
  - Tap Verb Drill tile -> navigate to VERB_DRILL
  - Tap Flashcards tile -> navigate to VOCAB_DRILL
  - Tap Daily Practice tile -> start/resume daily practice
  - Tap "Continue Learning" / Primary card -> navigate to LESSON
  - Tap "How This Training Works" -> explanation dialog
- **Business rules**:
  - Grid always shows 12 tiles (EMPTY padding for packs with fewer lessons)
  - First lesson always SPROUT state. Subsequent lessons UNLOCKED only after previous lesson has masteryPercent > 0
  - Drill tiles visibility is pack-scoped (`hasVerbDrill`/`hasVocabDrill` check active pack manifest)
  - Daily Practice entry: if resumable session exists, shows DailyResumeDialog
- **Cross-reference**: Russian spec section 2 matches. One addition: Russian spec mentions `activePackDisplayName` in Primary Action Card which matches code. The Russian spec's "LanguageSelector" label at line 62 matches code. No discrepancies found.

---

### 3. LessonRoadmapScreen

- **AppScreen enum**: `LESSON`
- **Source file**: `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` (composable at line 1178)
- **Parent**: HomeScreen (tap lesson tile or "Continue Learning")
- **Key UI elements**:
  - **Header row**: Back IconButton + lesson title (SemiBold) + spacer
  - **Progress section**: LinearProgressIndicator + "Exercise X of Y" + "Cards: X of Y"
  - **Sub-lesson grid**: 4-column `LazyVerticalGrid` with entries from `buildRoadmapEntries()`:
    - `RoadmapEntry.Training`: exercise tile with number, flower emoji, "NEW"/"MIX" label
    - `RoadmapEntry.Drill`: FitnessCenter icon + "Drill" label (if lesson has drill cards)
    - `RoadmapEntry.BossLesson`: "Review" label + trophy/lock icon (colored by reward)
    - `RoadmapEntry.BossMega`: "Mega" label + trophy/lock icon (only for lessonIndex > 0)
    - `RoadmapEntry.StoryCheckIn`/`StoryCheckOut`: kept for backward compat but NOT rendered
  - **Action button**: "Start Lesson" (completed==0) or "Continue Lesson"
- **State dependencies**: `selectedLessonId`, `lessons`, `subLessonCount`, `completedSubLessonCount`, `subLessonTypes`, `currentLessonShownCount`, `currentLessonFlower`, `bossLessonRewards`, `bossMegaRewards`, `testMode`
- **User interactions**:
  - Tap back -> HOME
  - Tap exercise tile (canEnter) -> startSubLesson + TRAINING
  - Tap locked exercise tile -> EarlyStartDialog
  - Tap Boss tile (unlocked) -> startBossLesson/startBossMega + TRAINING
  - Tap Boss tile (locked) -> BossLockedDialog
  - Tap Drill tile -> DrillStartDialog -> TRAINING (drill mode)
  - Tap action button -> startSubLesson(currentIndex) + TRAINING
- **Business rules**:
  - Sub-lessons paginated in cycles of 15
  - Boss unlocked when `completedSubLessonCount >= 15` or `testMode`
  - Mega Boss only for `lessonIndex > 0`
  - Story entries in RoadmapEntry sealed class are NOT rendered (backward compat only)
  - Default fallback sub-lesson types: first 3 NEW_ONLY, rest MIXED
- **Cross-reference**: Russian spec section 3 matches. No discrepancies. Note: Russian spec mentions `DrillTile` and `BossTile` inline which matches the code.

---

### 4. TrainingScreen

- **AppScreen enum**: `TRAINING`
- **Source file**: `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` (composable at line 2554)
- **Parent**: LessonRoadmapScreen (start sub-lesson, boss, or drill)
- **Key UI elements**:
  - **Scaffold TopBar**: "GrammarMate" title + Settings gear IconButton
  - **Session header**: "Review Session" (boss), "Refresh Session" (elite), or drill-specific green header
  - **Tense label**: Optional (13sp, SemiBold) when card has tense
  - **Prompt text**: Russian prompt with parenthetical hints stripped (18sp * ruTextScale)
  - **DrillProgressRow**: Progress bar (70% width, green fill, "X/Y" overlay) + Speedometer (30% width, Canvas arc, color by WPM: red <=20, yellow <=40, green >40)
  - **CardPrompt Card**: "RU" label + prompt text (20sp * ruTextScale) + TtsSpeakerButton
  - **AnswerBox**: OutlinedTextField "Your translation" + Mic trailing icon + ASR status indicator (offline) + Word bank FlowRow (FilterChips + Undo + counter) + Input mode buttons (Mic/Keyboard/Book) + Show answer (eye) + Report (flag) + Mode label + "Check" Button
  - **ResultBlock**: "Correct" (green) / "Incorrect" (red) + TTS replay + "Answer: ..."
  - **NavigationRow**: Prev + Pause/Play + Exit (StopCircle) + Next (all 44dp NavIconButton with accent bar)
  - **Report ModalBottomSheet**: Card prompt + flag/unflag bad sentence + hide card + export bad sentences + copy text
  - **Export result dialog**: Shows export file path after exporting bad sentences
- **State dependencies**: `currentCard`, `inputText`, `inputMode`, `sessionState`, `lastResult`, `answerText`, `ttsState`, `currentIndex`, `subLessonTotal`, `bossActive`, `bossProgress`, `bossTotal`, `isDrillMode`, `drillCardIndex`, `drillTotalCards`, `voiceActiveMs`, `voiceWordCount`, `wordBankWords`, `selectedWords`, `ruTextScale`, `useOfflineAsr`, `asrState`
- **User interactions**:
  - Type answer + Check / Enter
  - Tap Mic -> voice input mode, auto-launches recognition
  - Tap Keyboard/Book -> switch input mode
  - Tap word bank chips -> build answer; Undo -> remove last
  - Show answer (eye) -> reveal correct answer
  - Report (flag) -> open report bottom sheet
  - TTS speaker -> play/stop pronunciation
  - Prev/Next -> navigate cards; Pause/Play -> toggle; Exit/Stop -> exit dialog
  - Back gesture -> exit dialog
- **Business rules**:
  - WORD_BANK input mode does NOT count for mastery (only VOICE and KEYBOARD)
  - Voice mode auto-triggers on new card (200ms delay), auto-submits on result
  - Check disabled when input blank, no cards, or session paused
  - Boss mode uses `bossProgress`/`bossTotal`; Drill mode uses `drillCardIndex`/`drillTotalCards`
  - Drill mode: green background (Color(0xFFE8F5E9))
  - TTS requires model download (~346 MB)
  - Report sheet: flag/unflag persisted immediately; export to `Downloads/BaseGrammy/bad_sentences_all.txt`
- **Cross-reference**: Russian spec section 4 matches closely. Note: Russian spec says TrainingScreen "does NOT use TrainingCardSession" which matches code (it has its own inline implementation). The report sheet in Training is a `ModalBottomSheet`, not a Dialog, matching both specs. No discrepancies.

---

### 5. DailyPracticeScreen

- **AppScreen enum**: `DAILY_PRACTICE`
- **Source file**: `app/src/main/java/com/alexpo/grammermate/ui/DailyPracticeScreen.kt`
- **Parent**: HomeScreen (tap Daily Practice tile)
- **Key UI elements**:
  - **DailyPracticeHeader**: Back button + "Daily Practice" title + block type badge (Translation/Vocabulary/Verbs in colored Card)
  - **BlockProgressBar**: LinearProgressIndicator + "X/Y" label
  - **BlockSparkleOverlay**: Semi-transparent overlay with sparkle + "Next: [BlockType]" or "Daily practice complete!". Auto-dismisses after ~800ms
  - **TRANSLATE/VERBS block**: Wraps `TrainingCardSession` via `DailyPracticeSessionProvider`. Shows Russian prompt card with optional verb/tense hint SuggestionChips, input controls (text field, word bank, voice), navigation, result display
  - **VOCAB block (VocabFlashcardBlock)**: Word display (28sp, bold) + TTS button + translation + Mic button (64dp) + voice recognition feedback + 4 rating buttons (Again=red, Hard=orange, Good=primary, Easy=green)
  - **CompletionScreen**: "Session Complete!" heading + description + "Back to Home" button
  - **Exit dialog**: "Exit practice?" with "Stay"/"Exit"
  - **Loading state**: Spinner + "Loading session..." when `!state.active || currentTask == null`
- **State dependencies**: `dailySession` (DailySessionState), `blockProgress` (BlockProgress), `currentTask` (DailyTask), `ttsState`, `selectedLanguageId`
- **User interactions**:
  - TRANSLATE/VERBS: Same as TrainingScreen -- type/speak answer, check, navigate
  - VOCAB: Tap mic for voice recognition, tap rating button (Again/Hard/Good/Easy)
  - Back -> exit confirmation
  - Rating auto-advances to next card
- **Business rules**:
  - 3-block structure: TRANSLATE (10 cards) -> VOCAB (5 cards) -> VERBS (10 cards)
  - Block transitions show sparkle overlay (~800ms)
  - Verb hint chips in daily practice do NOT open bottom sheets (unlike standalone Verb Drill)
  - Voice auto-trigger on new card (200ms delay; 1200ms after incorrect feedback)
  - Correct voice answer auto-advances after 400ms
  - Vocab flashcards: both prompt and answer always visible (no flip mechanic)
  - Exit: "progress in this session will be lost"
- **Cross-reference**: Russian spec section 5 matches. Russian spec describes TrainingCardSession slots (DefaultProgressIndicator, DefaultCardContent, etc.) reused by DailyPractice which matches the code architecture. No discrepancies.

---

### 6. VerbDrillScreen

- **AppScreen enum**: `VERB_DRILL`
- **Source file**: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt`
- **Parent**: HomeScreen (tap Verb Drill tile)
- **Key UI elements**:
  - **SelectionScreen**: Back button + "Verb Drill" title + Tense dropdown + Group dropdown + "Sort by frequency" Checkbox + Progress stats ("Progress: X/Y", "Today: N") + LinearProgressIndicator + "All done today!" text or Start/Continue button
  - **Active Session (VerbDrillSessionWithCardSession)**: Custom header (back + "Verb Drill") + progress bar/speedometer + Card with "RU" label + prompt + TTS button + verb SuggestionChip (infinitive + #rank) + tense SuggestionChip (abbreviated) + input controls (text field, word bank, voice, show answer, report) + result display + navigation
  - **VerbReferenceBottomSheet**: Verb infinitive + TTS button + group + tense + conjugation table (triggered by verb chip tap)
  - **TenseInfoBottomSheet**: Tense name + formula Card + usage explanation (Russian) + example cards (Italian + Russian + notes) (triggered by tense chip tap)
  - **Report ModalBottomSheet**: Same structure as Training report sheet (flag/unflag, hide, export, copy)
  - **CompletionScreen**: Sparkle emoji + "Otlichno!" text + "Pravilnykh: X | Oshibok: Y" + "Eshche" (more) Button + "Vykhod" (exit) OutlinedButton
- **State dependencies**: `VerbDrillUiState` (isLoading, session, selectedTense, availableTenses, selectedGroup, availableGroups, sortByFrequency, everShownCount, totalCards, todayShownCount, allDoneToday, correctCount, incorrectCount)
- **User interactions**:
  - Selection: filter by tense/group, toggle frequency sort, start session
  - Session: same input modes as Training (keyboard/voice/word bank), tap verb chip -> reference sheet, tap tense chip -> tense info, report
  - Completion: "Eshche" for next batch, "Vykhod" to exit
- **Business rules**:
  - 3 attempts per card in session. After 3 wrong, hint answer shown.
  - Show answer reveals hint immediately (no attempts consumed)
  - Session is batch-based (10 cards per batch)
  - "Eshche" hidden when `allDoneToday`
  - Tense names abbreviated in chips (Presente -> Pres., etc.)
  - Uses `VerbDrillViewModel` (separate ViewModel scoped to pack via `reloadForPack()`)
- **Cross-reference**: Russian spec section 6 matches. Russian spec describes TrainingCardSession slots reused by VerbDrill which matches code. VerbReferenceBottomSheet and TenseInfoBottomSheet are described accurately. No discrepancies.

---

### 7. VocabDrillScreen

- **AppScreen enum**: `VOCAB_DRILL`
- **Source file**: `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillScreen.kt`
- **Parent**: HomeScreen (tap Flashcards tile)
- **Key UI elements**:
  - **SelectionScreen**: Back button + "Flashcards" title + Direction FilterChips ("IT -> RU" / "RU -> IT") + Voice input Switch + POS FilterChips (All/Nouns/Verbs/etc.) + Frequency FilterChips (Top 100/500/1000/All) + Stats Card (due count, mastered, POS breakdown, progress bar) + Start button
  - **CardScreen (VocabDrillCardScreen)**:
    - *Front (unflipped)*: POS badge + rank badge + word (32sp, bold) + TTS button + "Tap to speak" text + Mic button (72dp) + voice result feedback + Skip/Flip buttons
    - *Back (flipped)*: POS badge + word + TTS button + translation + Forms table (m sg, f sg, m pl, f pl) + Collocations list (max 5) + mastery step ("Step X/9" / "Learned") + 4 rating buttons (Again/Hard/Good/Easy with interval labels)
  - **CompletionScreen**: "Perfect!"/"Done!" title + Stats Card (correct/wrong/reviewed) + Exit + Continue buttons (fade-in after 800ms)
  - **Report ModalBottomSheet**: Same structure (flag/unflag, export, copy)
- **State dependencies**: `VocabDrillUiState` (drillDirection, voiceModeEnabled, selectedPos, availablePos, rankMin, rankMax, dueCount, totalCount, masteredCount, masteredByPos, session with cards/currentIndex/isFlipped/voiceCompleted/voiceResult)
- **User interactions**:
  - Selection: filter direction, POS, frequency; toggle voice; start session
  - Card front: tap mic for voice, tap Skip to skip voice, tap Flip to reveal
  - Card back: tap rating button (Again/Hard/Good/Easy) to advance
  - Completion: Exit to selection, Continue for new session
- **Business rules**:
  - Due words calculated by Spaced Repetition Config (interval ladder)
  - Voice auto-starts when voice mode enabled (500ms delay)
  - Voice auto-flips on correct answer (800ms)
  - Max 3 voice attempts, then auto-flip with "Moving on..."
  - Interval ladder: [1, 2, 4, 7, 10, 14, 20, 28, 42, 56] days
  - Rating: Again = reset to step 0, Hard = same step, Good = +1 step, Easy = +2 steps
  - Uses `VocabDrillViewModel` (separate ViewModel, `reloadForPack()`)
- **Cross-reference**: Russian spec section 7 matches. Direction chips, POS chips, frequency chips, front/back card layouts all match. No discrepancies.

---

### 8. LadderScreen

- **AppScreen enum**: `LADDER`
- **Source file**: `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` (composable at line 2388)
- **Parent**: SettingsSheet (tap "Show Ladder")
- **Key UI elements**:
  - **Header**: Back IconButton + "Lestnitsa intervalov" title (titleLarge, SemiBold) + "Vse uroki tekushchego paketa" subtitle (bodySmall, alpha 0.65)
  - **LadderHeaderRow**: Column headers (#, Urok, Karty, Dney, Interval) in labelMedium
  - **LadderRowCard**: Per-lesson Card (RoundedCornerShape 14dp) with index + title (ellipsized) + uniqueCardShows + daysSinceLastShow + intervalLabel. Overdue rows use `errorContainer` background
  - **Empty state**: "Net dannykh po urokam" placeholder text
- **State dependencies**: `ladderRows` (List of `LessonLadderRow`)
- **User interactions**: Scroll list. Back -> returns to caller screen (HOME or TRAINING, with resumeFromSettings if from TRAINING)
- **Business rules**:
  - Overdue lessons (interval label starts with "Prosrochka") highlighted in red
  - No data: shows placeholder and returns early
- **Cross-reference**: Russian spec section 8 matches exactly. No discrepancies.

---

### 9. StoryQuizScreen

- **AppScreen enum**: `STORY`
- **Source file**: `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` (composable at line 1599)
- **Parent**: LessonRoadmapScreen (story phase trigger). Currently unused (story tiles not rendered).
- **Key UI elements**:
  - **Phase title**: "Story Check-in" or "Story Check-out"
  - **Story text**: bodyMedium
  - **Question counter**: "Question X / Y"
  - **Question prompt**: SemiBold
  - **Option rows**: Clickable rows with ">" indicator for selection. Result annotations: "(correct)", "(your choice)"
  - **Correct answer text**: Primary color (after checking)
  - **Error message**: Red text
  - **Navigation**: Prev (outlined) + Check (filled) + Next/Finish (filled) buttons
  - **Scroll hint**: "Scroll to continue" when content exceeds viewport
- **State dependencies**: `activeStory` (StoryQuiz), `testMode`, local selection/result state
- **User interactions**: Select option, Check answer, Next/Finish, Prev
- **Business rules**:
  - Must select before checking
  - Next validates first; on last question, completes story
  - Test mode: all stories marked completed regardless of correctness
  - Empty questions: auto-completes as correct
  - Null story: auto-closes
- **Cross-reference**: Not covered in Russian spec (absent from that document). This screen is maintained for backward compatibility but effectively unused since story tiles are not rendered.

---

### 10. SettingsSheet

- **AppScreen enum**: N/A (ModalBottomSheet overlay)
- **Source file**: `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` (composable at line 1951)
- **Parent**: Any screen (gear icon)
- **Key UI elements**:
  - **Service Mode section**: Test Mode Switch + description + "Show Ladder" OutlinedButton (Insights icon) + Vocabulary Sprint limit OutlinedTextField
  - **Pronunciation speed**: Slider (0.5-1.5x, 3 steps) + current value display
  - **Voice recognition**: Offline ASR Switch + download state indicator (downloading/extracting/error/ready with progress bar)
  - **Translation text size**: Slider (1.0-2.0x, 3 steps) + current value display
  - **Language/Pack selectors**: Language DropdownSelector + Pack DropdownSelector
  - **Content management**: "New language" TextField + Add button + Import lesson pack (ZIP) + Import lesson (CSV) + Reset/Reload + Create empty lesson + Delete all lessons (red) + Reset all progress (red)
  - **Info sections**: CSV format explanation + Instructions
  - **Installed packs list**: Pack name + Delete IconButton per pack
  - **Profile section**: Name TextField + "Save Name" Button
  - **Backup & Restore**: "Save progress now" + "Restore from backup" buttons
- **State dependencies**: `testMode`, `vocabSprintLimit`, `ttsSpeed`, `useOfflineAsr`, `asrModelReady`, `asrDownloadState`, `ruTextScale`, `languages`, `selectedLanguageId`, `installedPacks`, `activePackId`, `userName`
- **User interactions**: Toggle test mode, open ladder, change vocab limit, adjust TTS speed, toggle offline ASR, adjust text scale, select language/pack, add language, import pack/CSV, reset/reload, create/delete lessons, delete packs, save name, backup/restore
- **Business rules**:
  - Changing language reloads lessons and resets active pack
  - TTS download persists in background (progress bar on all screens)
  - ASR download triggered when toggled on without model
  - File pickers: `ActivityResultContracts.OpenDocument` (CSV/ZIP), `OpenDocumentTree` (backup restore)
  - On dismiss from TRAINING: calls `resumeFromSettings()` if card is active
- **Cross-reference**: Russian spec section 17 (SettingsSheet) matches. All controls listed in the Russian spec are present in code. No discrepancies.

---

## Dialog Details

### D1. WelcomeDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 759)
- **Trigger**: First launch when `userName == "GrammarMateUser"` (via LaunchedEffect)
- **Key UI elements**: Title "Welcome to GrammarMate!" (headlineSmall) + "What's your name?" text + OutlinedTextField (50 char limit, single line, Done IME) + "Skip" TextButton + "Continue" TextButton
- **Business rules**: Cannot dismiss by tapping outside (empty onDismissRequest). Blank input treated as "GrammarMateUser".
- **Cross-reference**: Russian spec section 9 (WelcomeDialog) matches exactly.

### D2. StreakDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 659)
- **Trigger**: `streakMessage != null` in TrainingUiState
- **Key UI elements**: "??" icon text (48sp) + "Streak!" title + streakMessage (titleMedium, centered) + "Longest streak: N days" (when longestStreak > currentStreak) + "Continue" button
- **Business rules**: Longest streak only shown when it exceeds current streak.
- **Cross-reference**: Russian spec section 9 (Streak Dialog) matches. No discrepancies.

### D3. BossRewardDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 634)
- **Trigger**: `bossRewardMessage != null && bossReward != null`
- **Key UI elements**: Trophy icon (EmojiEvents, colored: bronze=#CD7F32, silver=#C0C0C0, gold=#FFD700) + "Boss Reward" title + rewardMessage text + "OK" button
- **Cross-reference**: Russian spec section 9 (Boss Reward Dialog) matches. No discrepancies.

### D4. BossErrorDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 622)
- **Trigger**: `bossErrorMessage != null`
- **Key UI elements**: "Boss" title + error message text + "OK" button
- **Cross-reference**: Russian spec section 9 (Boss Error Dialog) matches. No discrepancies.

### D5. StoryErrorDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 610)
- **Trigger**: `storyErrorMessage != null`
- **Key UI elements**: "Story" title + error message text + "OK" button
- **Cross-reference**: Russian spec section 9 (Story Error Dialog) matches. No discrepancies.

### D6. DrillStartDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 1495)
- **Trigger**: Tap DrillTile in Lesson Roadmap (`drillShowStartDialog`)
- **Key UI elements**: "Drill Mode" title + message (resume or fresh) + "Start"/"Continue" (confirm) + "Start Fresh" (dismiss, only if hasProgress) + "Cancel" buttons
- **Business rules**: If progress exists: "Continue" (resume) + "Start Fresh". If no progress: "Start" only.
- **Cross-reference**: Russian spec section 5-7 (DrillStartDialog in Lesson Roadmap) matches. No discrepancies.

### D7. ExitConfirmationDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 535)
- **Trigger**: Back gesture or Stop button during TRAINING or DAILY_PRACTICE
- **Key UI elements**: Title "End session?" or "Exit practice?" + message + "Cancel" + "Exit" buttons
- **Business rules**:
  - DAILY_PRACTICE: calls `cancelDailySession()`, navigates HOME
  - Boss mode: calls `finishBoss()`, navigates LESSON
  - Drill mode: calls `exitDrillMode()`, navigates LESSON
  - Normal training: calls `finishSession()`, navigates LESSON
- **Cross-reference**: Russian spec mentions exit dialogs in TrainingScreen (section 4, block 13) and DailyPracticeScreen (section 5, block 6). Both match. No discrepancies.

### D8. TtsDownloadDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 3414)
- **Trigger**: Tap TTS speaker when model not downloaded (`ttsModelReady == false`)
- **Key UI elements**: "Download pronunciation model?" title + dynamic content (Idle: size info; Downloading/Extracting: progress bar + percentage; Done: "ready!"; Error: failure message) + Download/OK/Cancel buttons
- **Business rules**: Auto-closes on completion and auto-plays TTS for current card.
- **Cross-reference**: Russian spec section 4 (block 14, TtsDownloadDialog) matches. No discrepancies.

### D9. MeteredNetworkDialog (TTS)

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 3478)
- **Trigger**: TTS download on metered connection (`ttsMeteredNetwork == true`)
- **Key UI elements**: "Metered network detected" title + "~346 MB" warning + "Download anyway" + "Cancel"
- **Cross-reference**: Russian spec section 4 (block 15, MeteredNetworkDialog) matches. No discrepancies.

### D10. AsrMeteredNetworkDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 3496)
- **Trigger**: ASR download on metered connection (`asrMeteredNetwork == true`)
- **Key UI elements**: "Metered network detected" title + "~375 MB" warning + "Download anyway" + "Cancel"
- **Cross-reference**: Russian spec section 4 (block 16, AsrMeteredNetworkDialog) matches. No discrepancies.

### D11. DailyResumeDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 572)
- **Trigger**: Tap Daily Practice when `hasResumableDailySession()` returns true
- **Key UI elements**: "Ezhednevnaya praktika" title + "Repeat = same cards, Continue = new cards" text + "Repeat" (dismissButton) + "Continue" (confirmButton)
- **Business rules**: "Continue" calls `startDailyPractice(level)`, "Repeat" calls `repeatDailyPractice(level)`.
- **Cross-reference**: Not explicitly described in Russian spec (resume dialog is mentioned in DailyPracticeScreen sub-screen section but not as a separate dialog). The code implementation matches the described behavior.

### D12. LessonLockedDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 1028, inside HomeScreen)
- **Trigger**: Tap EMPTY lesson tile on Home Screen
- **Key UI elements**: "Lesson locked" title + "Please complete the previous lesson first." text + "OK" button
- **Cross-reference**: Russian spec section 2 (block 8, "Lesson locked" dialog) matches. No discrepancies.

### D13. EarlyStartDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 1040 in HomeScreen, line 1358 in LessonRoadmapScreen)
- **Trigger**: Tap locked lesson tile (HomeScreen) or locked sub-lesson tile (LessonRoadmapScreen)
- **Key UI elements**: "Start early?" title + "Start this lesson early? You can always come back..." text + "No" + "Yes" buttons
- **Business rules**: "Yes" unlocks and proceeds. Does not affect other lessons' unlock state.
- **Cross-reference**: Russian spec section 2 (block 8, "Start early?" dialog) and section 3 (block 5) both match. No discrepancies.

### D14. HowThisTrainingWorksDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 1011, inside HomeScreen)
- **Trigger**: Tap "How This Training Works" button on Home Screen
- **Key UI elements**: Title + "GrammarMate builds automatic grammar patterns with repeated retrieval..." text + "OK" button
- **Cross-reference**: Russian spec section 2 (block 8, "How This Training Works" dialog) matches. No discrepancies.

### D15. DailyPracticeLoadingOverlay

- **AppScreen enum**: N/A (Dialog)
- **Source file**: `GrammarMateApp.kt` (line 504)
- **Trigger**: Starting/resuming daily practice (`isLoadingDaily == true`)
- **Key UI elements**: Card with CircularProgressIndicator + "Loading session..." text
- **Business rules**: Blocking dialog, auto-dismissed when initialization completes.

### D16. ExportBadSentencesResultDialog

- **AppScreen enum**: N/A (AlertDialog)
- **Source file**: `GrammarMateApp.kt` (line 3062, inside AnswerBox)
- **Trigger**: After exporting bad sentences from report sheet
- **Key UI elements**: "Export" title + file path or "No bad sentences to export" + "OK" button

---

## Report Bottom Sheets (Cross-Cutting)

The report bottom sheet (ModalBottomSheet with flag/unflag/export/copy options) appears in multiple contexts. The core UI is identical across all contexts:

| Context | Trigger Location | Mode String | ViewModel Source |
|---------|-----------------|-------------|------------------|
| Training Screen | AnswerBox flag button | "training" | TrainingViewModel |
| Daily Practice Block 1/3 | DailyInputControls | "daily_translate" / "daily_verb" | TrainingViewModel (daily methods) |
| Daily Practice Block 2 | VocabFlashcardBlock | "daily_vocab" | TrainingViewModel (daily methods) |
| Verb Drill | DefaultVerbDrillInputControls | "verb_drill" | VerbDrillViewModel |
| Vocab Drill | Card screen report button | "vocab_drill" | VocabDrillViewModel |

---

## Persistent UI Elements (All Screens)

These elements render on every screen regardless of current AppScreen:

| Element | Condition | Description |
|---------|-----------|-------------|
| TTS Background Progress Bar | `bgTtsDownloading == true` | 2dp LinearProgressIndicator at top of all content. Aggregates progress from all language downloads. |
| WelcomeDialog | `userName == "GrammarMateUser"` | LaunchedEffect triggers on any screen transition |
| TtsDownloadDialog | `showTtsDownloadDialog == true` | Triggered by TTS tap without model |
| MeteredNetworkDialog (TTS) | `ttsMeteredNetwork == true` | Metered connection warning |
| AsrMeteredNetworkDialog | `asrMeteredNetwork == true` | ASR metered connection warning |
| StreakDialog | `streakMessage != null` | Post-session streak display |
| BossRewardDialog | `bossRewardMessage + bossReward != null` | Post-boss reward |
| BossErrorDialog | `bossErrorMessage != null` | Boss session error |
| StoryErrorDialog | `storyErrorMessage != null` | Story session error |
| DrillStartDialog | `drillShowStartDialog == true` | Drill start confirmation |
| SettingsSheet | `showSettings == true` | Settings modal overlay |

---

## Cross-Reference with Russian Spec

The Russian specification file ("Ekstrannye formy. Spetsifikatsiya") covers sections 1-9 with detailed element tables. Comparison results:

| Russian Spec Section | Code Match | Discrepancies |
|----------------------|------------|---------------|
| 1. StartupScreen | Exact match | None |
| 2. HomeScreen | Exact match | None |
| 3. LessonRoadmapScreen | Exact match | None |
| 4. TrainingScreen | Exact match | Note: Russian spec says "TrainingScreen has its own implementation and does NOT use TrainingCardSession" which matches code |
| 4a. TrainingCardSession | Exact match | Component is defined in `TrainingCardSession.kt`, used by VerbDrill and DailyPractice |
| 5. DailyPracticeScreen | Exact match | None |
| 6. VerbDrillScreen | Exact match | None |
| 7. VocabDrillScreen | Exact match | None |
| 8. LadderScreen | Exact match | None |
| 9. Dialogs | Exact match | None |

**Screens present in code but absent from Russian spec**: StoryQuizScreen (section 9.15 in this catalog). This screen exists in code but story tiles are not rendered in the roadmap, making it effectively unused.

**Elements in Russian spec matching code exactly**: All TtsDownloadDialog, MeteredNetworkDialog, AsrMeteredNetworkDialog, WelcomeDialog, StreakDialog, BossRewardDialog, BossErrorDialog, StoryErrorDialog, DrillStartDialog, Report Sheet variants.
