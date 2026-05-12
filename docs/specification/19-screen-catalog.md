# 19. Complete Screen Catalog -- Specification

This document catalogs every screen and dialog in GrammarMate. Each entry covers purpose, entry/exit conditions, layout wireframe, UI components, displayed state, user interactions, business rules, and edge cases.

The app uses a single-activity, single-ViewModel architecture. `GrammarMateApp()` is the root composable that routes between screens via an `AppScreen` enum. All screens are composables defined in `GrammarMateApp.kt` or dedicated screen files (`DailyPracticeScreen.kt`, `VerbDrillScreen.kt`, `VocabDrillScreen.kt`, `TrainingCardSession.kt`, `AppRoot.kt`).

---

## 19.1 App Startup Screen

- **Purpose**: Displayed during app initialization while checking for backup restore status. Shows a loading spinner and status message.
- **Entry conditions**: App launch. Shown by `AppRoot()` when `RestoreNotifier.restoreState.status` is not `DONE`.
- **Exit conditions**: Automatically dismissed when restore status becomes `DONE`, at which point `GrammarMateApp()` is rendered.
- **Layout**:
```
+----------------------------------+
|                                  |
|                                  |
|          [Spinner]               |
|       "Restoring backup..."      |
|       "Waiting for backup..."    |
|       "Preparing..."             |
|                                  |
|                                  |
+----------------------------------+
```
- **Components**:
  - `CircularProgressIndicator` (centered)
  - `Text` (status message, bodyMedium)
- **State displayed**: `RestoreStatus` (IN_PROGRESS, NEEDS_USER, or other)
- **User interactions**: None. This is a blocking wait screen.
- **Business rules**:
  - Messages are localized to English, keyed by `RestoreStatus` enum.
  - Cannot be dismissed by the user.
- **Edge cases**: If restore hangs indefinitely, the user sees the spinner forever. No timeout is implemented at this level.

---

## 19.2 Home Screen

- **Purpose**: Main hub. Shows user profile, language selector, lesson grid (flower progress), daily practice tile, drill tiles, and navigation to all features.
- **Entry conditions**: Default screen on app start (`AppScreen.HOME`). Also returned to from Lesson Roadmap (back button), Training (session end), Daily Practice (exit/complete), Verb Drill (back), Vocab Drill (back).
- **Exit conditions**: Tap a lesson tile -> `AppScreen.LESSON`. Tap Daily Practice tile -> `AppScreen.DAILY_PRACTICE`. Tap Verb Drill tile -> `AppScreen.VERB_DRILL`. Tap Vocab Drill tile -> `AppScreen.VOCAB_DRILL`. Tap settings gear -> SettingsSheet overlay. Tap "Continue Learning" card -> `AppScreen.LESSON`.
- **Layout**:
```
+-------------------------------------------+
| [Avatar] UserName        [EN v] [Settings] |
+-------------------------------------------+
| +---------------------------------------+ |
| | Pack Name / Continue Learning         | |
| | Lesson 3. Exercise 2/10              | |
| +---------------------------------------+ |
|                                           |
| Grammar Roadmap                           |
| +------+------+------+------+            |
| | 1    | 2    | 3    | 4    |            |
| | 🌱   | 🔓   | 🔒   | 🔒   |            |
| | 45%  |      |      |      |            |
| +------+------+------+------+            |
| | 5    | 6    | 7    | 8    |            |
| | ●    | ●    | ●    | ●    |            |
| +------+------+------+------+            |
| | 9    | 10   | 11   | 12   |            |
| | ●    | ●    | ●    | ●    |            |
| +------+------+------+------+            |
|                                           |
| +-----------------+ +------------------+  |
| | Verb Drill      | | Flashcards       |  |
| +-----------------+ | 3 mastered       |  |
|                     +------------------+  |
| +---------------------------------------+ |
| | Daily Practice           [Play icon]  | |
| | Practice all sub-lessons              | |
| +---------------------------------------+ |
|                                           |
| Legend:                                   |
|  seed  growing  bloom                    |
|  wilting  wilted  forgotten              |
|                                           |
| [How This Training Works]                |
| [Continue Learning]                       |
+-------------------------------------------+
```
- **Components**:
  - **Profile avatar**: Circular box with user initials (first two words, max 2 chars), primary color background
  - **User name text**: Semi-bold weight
  - **LanguageSelector dropdown**: Shows current language code (e.g., "EN"), opens menu of installed languages
  - **Settings icon button**: Gear icon, opens `SettingsSheet`
  - **Primary action Card**: Clickable card showing active pack display name and lesson progress hint
  - **Lesson grid**: 4-column `LazyVerticalGrid` with `LessonTile` cards. Each tile shows: lesson number, flower emoji (from `FlowerCalculator`), mastery percentage. Tile states: SEED, SPROUT, FLOWER, LOCKED, UNLOCKED, EMPTY, VERB_DRILL
  - **VerbDrillEntryTile**: Card with FitnessCenter icon and "Verb Drill" label. Only visible when `hasVerbDrill` is true (active pack has verbDrill section)
  - **VocabDrillEntryTile**: Card with MenuBook icon, "Flashcards" label, mastered count. Only visible when `hasVocabDrill` is true
  - **DailyPracticeEntryTile**: primaryContainer-colored card with "Daily Practice" title, "Practice all sub-lessons" subtitle, PlayArrow icon
  - **Legend text**: Flower state emoji legend
  - **"How This Training Works" OutlinedButton**: Opens explanation dialog
  - **"Continue Learning" Button**: Navigates to Lesson Roadmap
- **State displayed**:
  - `userName`, `selectedLanguageId`, `languages`, `lessons`, `lessonFlowers`, `activePackId`, `activePackLessonIds`, `activePackDisplayName`, `selectedLessonId`, `completedSubLessonCount`, `subLessonCount`, `sessionState`, `correctCount`, `incorrectCount`, `activeTimeMs`, `testMode`, `installedPacks`, `vocabMasteredCount`
- **User interactions**:
  - Tap avatar/settings: Open settings sheet
  - Tap language selector: Switch language
  - Tap lesson tile: Select lesson and navigate to Lesson Roadmap
  - Tap locked tile: Show "Start early?" dialog (offers to unlock the lesson)
  - Tap empty tile: Show "Lesson locked" dialog
  - Tap Daily Practice tile: Start/resume daily practice (with resume dialog if session exists)
  - Tap Verb Drill tile: Navigate to Verb Drill screen
  - Tap Flashcards tile: Navigate to Vocab Drill screen
  - Tap "How This Training Works": Show explanation AlertDialog
  - Tap "Continue Learning": Navigate to Lesson Roadmap
- **Business rules**:
  - Lesson grid always shows exactly 12 tiles (padded with EMPTY if pack has fewer)
  - Lesson unlock logic: first lesson always SPROUT; subsequent lessons UNLOCKED only after the previous lesson has masteryPercent > 0; all others LOCKED
  - Drill tiles are pack-scoped: visibility depends on `hasVerbDrill`/`hasVocabDrill` checking the active pack manifest
  - Daily Practice resume dialog offers "Continue" (new cards) or "Repeat" (same cards from start)
  - When `sessionState == ACTIVE`, primary card shows "Continue Learning"
- **Edge cases**:
  - No installed packs: tiles show as EMPTY, drill tiles hidden
  - Test mode: all lessons shown as SEED (unlocked)
  - User name is "GrammarMateUser": triggers WelcomeDialog if not on HOME screen (but auto-triggered on first launch via LaunchedEffect)

---

## 19.3 Lesson Roadmap Screen

- **Purpose**: Shows sub-lesson exercises for the currently selected lesson with progress visualization. Entry point for starting training sessions, boss battles, and drill mode.
- **Entry conditions**: From Home Screen (tap lesson tile or "Continue Learning"). From Training Screen (session auto-completes via `subLessonFinishedToken`/`bossFinishedToken`).
- **Exit conditions**: Back button -> Home Screen. Tap exercise tile or "Start Lesson"/"Continue Lesson" -> Training Screen. Tap Boss tile -> Training Screen (boss mode). Tap Drill tile -> DrillStartDialog -> Training Screen (drill mode).
- **Layout**:
```
+-------------------------------------------+
| [<-] Lesson Title                    [  ] |
+-------------------------------------------+
| [=========                     ] 60%      |
|        Exercise 4 of 15                   |
|        Cards: 45 of 150                   |
|                                           |
| +------+------+------+------+            |
| | 1    | 2    | 3    | 4    |            |
| | NEW  | NEW  | NEW  | MIX  |            |
| +------+------+------+------+            |
| | 5    | 6    | ...  | 15   |            |
| | MIX  | MIX  |      | MIX  |            |
| +------+------+------+------+            |
|                                           |
| [Start Lesson] / [Continue Lesson]        |
+-------------------------------------------+
```
- **Components**:
  - **Header row**: Back IconButton, lesson title (Semi-bold), spacer for alignment
  - **Progress bar**: `LinearProgressIndicator` showing completed/total ratio
  - **Exercise counter**: "Exercise X of Y" centered text
  - **Card counter**: "Cards: X of Y" centered text (smaller, alpha 0.7)
  - **Sub-lesson grid**: 4-column `LazyVerticalGrid`. Each tile is a Card (72dp height) with: exercise number, flower emoji or lock icon, type label ("NEW" or "MIX")
  - **BossTile**: Card with "Review"/"Mega" label, trophy icon colored by reward (bronze/silver/gold), or lock icon if locked
  - **DrillTile**: Card with FitnessCenter icon and "Drill" label (if lesson has drill cards)
  - **Action button**: "Start Lesson" (if completed==0) or "Continue Lesson"
  - **Early start dialog**: AlertDialog asking "Start exercise N early?"
  - **Boss locked dialog**: AlertDialog "Complete at least 15 exercises first."
- **State displayed**:
  - `selectedLessonId`, `lessons`, `subLessonCount`, `completedSubLessonCount`, `subLessonTypes`, `currentLessonShownCount`, `currentLessonFlower`, `bossLessonRewards`, `bossMegaRewards`, `testMode`
- **User interactions**:
  - Tap completed/active exercise tile: Start that sub-lesson
  - Tap locked exercise tile: Show "Start early?" dialog
  - Tap Boss tile (unlocked): Start boss battle
  - Tap Boss tile (locked): Show locked message
  - Tap Drill tile: Show DrillStartDialog
  - Tap action button: Start current exercise
- **Business rules**:
  - Sub-lessons display in cycles of 15 (pagination). Shows current cycle based on completed count.
  - Boss battles unlock when `completedSubLessonCount >= 15` or `testMode` is on
  - "Mega Boss" only appears for lessons at index > 0 (not the first lesson)
  - Story tiles (`StoryCheckIn`, `StoryCheckOut`) exist in the `RoadmapEntry` sealed class for backward compat but are not rendered
  - Exercise tiles show the lesson's flower state (same flower for all exercises of a lesson)
- **Edge cases**:
  - No sub-lesson types loaded: falls back to default pattern (first 3 NEW_ONLY, rest MIXED)
  - All exercises completed: visible list becomes empty, cycle calculation shows no tiles
  - Test mode: all tiles are accessible regardless of completion order

---

## 19.4 Training Screen

- **Purpose**: Core card-by-card training session. Presents a Russian prompt, accepts user translation via keyboard/voice/word bank, shows correct/incorrect feedback, and tracks progress.
- **Entry conditions**: From Lesson Roadmap (start sub-lesson, start boss, start drill). Uses `AppScreen.TRAINING`.
- **Exit conditions**: Back button -> exit confirmation dialog. Session auto-completes -> returns to Lesson Roadmap. Stop button -> exit dialog.
- **Layout** (normal lesson mode):
```
+-------------------------------------------+
| GrammarMate                      [Settings]|
+-------------------------------------------+
| Presente Indicativo                       |
| Он работает в офисе                       |
| [===========        ] 5/10    [Speed 42]  |
|                                           |
| +---------------------------------------+ |
| | RU                                    | |
| | Он работает в офисе        [Speaker]  | |
| +---------------------------------------+ |
|                                           |
| [Your translation              ] [Mic]    |
| [Mic] [Keyboard] [Book]  [Eye] [Flag] KB |
|           [Check]                         |
|                                           |
| Correct                                   |
| Answer: Lui lavora in ufficio  [Speaker]  |
|                                           |
| [<]   [Pause] [Stop] [>]                 |
+-------------------------------------------+
```
- **Layout** (drill mode): Same structure but with green background (`Color(0xFFE8F5E9)`), green prompt text, and no session title.
- **Layout** (boss mode): Title shows "Review Session" instead of lesson info.
- **Components**:
  - **Scaffold**: Top bar with "GrammarMate" title and settings gear. Background color changes to green in drill mode.
  - **Tense label**: Optional, shown when card has tense info (13sp, primary/green, semi-bold)
  - **Clean prompt**: Russian prompt with parenthetical hints stripped (18sp * ruTextScale, medium weight)
  - **DrillProgressRow**: Progress bar (70% width, green fill, white "X/Y" overlay) + circular speedometer (30% width, color-coded by WPM)
  - **CardPrompt Card**: "RU" label + Russian prompt text (20sp * ruTextScale) + TTS speaker button
  - **TtsSpeakerButton**: 4 states -- idle (VolumeUp), speaking (StopCircle, red), initializing (spinner), error (ReportProblem, red)
  - **AnswerBox**:
    - `OutlinedTextField` with "Your translation" label + Mic trailing icon
    - Voice mode hint text ("Say translation: ...")
    - ASR status indicator (offline mode): pulsing red dot (recording), spinner (processing), error text
    - Word bank `FlowRow` with `FilterChip` words + "Undo" button + selection counter
    - Input mode selector: Mic, Keyboard, Book `FilledTonalIconButton`s
    - Show answer `IconButton` with tooltip (eye icon)
    - Report `IconButton` with tooltip (warning icon)
    - Current mode label text ("Voice"/"Keyboard"/"Word Bank")
    - "Check" `Button` (full width)
  - **ResultBlock**: "Correct" (green) or "Incorrect" (red) text + TTS replay + "Answer: ..." text
  - **NavigationRow**: Prev (ArrowBack), Pause/Play, Exit (StopCircle), Next (ArrowForward) -- all as styled `NavIconButton` (44dp, surfaceVariant background, 3dp primary accent bar)
  - **Report ModalBottomSheet**: Shows card prompt, options to flag/unflag as bad sentence, hide card, export bad sentences, copy text
- **State displayed**:
  - `currentCard`, `inputText`, `inputMode`, `sessionState`, `lastResult`, `answerText`, `ttsState`, `currentIndex`, `subLessonTotal`, `bossActive`, `bossProgress`, `bossTotal`, `isDrillMode`, `drillCardIndex`, `drillTotalCards`, `voiceActiveMs`, `voiceWordCount`, `wordBankWords`, `selectedWords`, `ruTextScale`, `useOfflineAsr`, `asrState`
- **User interactions**:
  - Type in text field and tap "Check" or press Enter
  - Tap Mic button: Switch to voice mode, launch speech recognition
  - Tap Keyboard button: Switch to keyboard input mode
  - Tap Book button: Switch to word bank mode
  - Tap word bank FilterChips to build answer
  - Tap Undo to remove last selected word
  - Tap Show Answer (eye icon): Reveal the correct answer
  - Tap Report (flag icon): Open report bottom sheet
  - Tap TTS speaker: Play/stop pronunciation
  - Tap Prev/Next: Navigate between cards
  - Tap Pause/Play: Toggle session pause
  - Tap Stop: Open exit confirmation dialog
  - Back gesture: Open exit confirmation dialog
- **Business rules**:
  - WORD_BANK input mode never counts for mastery (only VOICE and KEYBOARD)
  - Voice mode auto-triggers speech recognition when a new card appears (200ms delay)
  - Voice mode auto-submits when speech result is received
  - In offline ASR mode, `startOfflineRecognition()` is called instead of `RecognizerIntent`
  - Check button disabled when input is blank, no cards, or session is paused
  - "Show answer" triggers answer display but does not submit
  - Navigation prev/next wrap around or stop at bounds depending on ViewModel logic
  - Boss mode uses `bossProgress`/`bossTotal` instead of `currentIndex`/`subLessonTotal`
  - Drill mode uses `drillCardIndex`/`drillTotalCards`
  - Report sheet options: flag/unflag card, hide card (removes from future lessons), export all flagged cards to file, copy card text to clipboard
  - TTS requires model download (346 MB). If not downloaded, tapping speaker opens TtsDownloadDialog
  - TTS speed controlled by `ttsSpeed` setting (0.5x-1.5x)
- **Edge cases**:
  - No cards loaded: "No cards" error text shown, Check button disabled
  - Session paused: Voice mode and Check button disabled
  - TTS model not downloaded: Download dialog shown instead of playing audio
  - Metered network detected during download: Warning dialog before proceeding
  - `AppScreen.ELITE` and `AppScreen.VOCAB` redirect to HOME immediately (backward compat)

---

## 19.5 Daily Practice Screen

- **Purpose**: Unified daily session with 3 sequential blocks: 10 sentence translations, 5 vocabulary flashcards (Anki-style), 10 verb conjugations. Provides varied practice in a single session.
- **Entry conditions**: From Home Screen (tap Daily Practice tile). If a resumable session exists, a resume dialog offers "Continue" (new cards) or "Repeat" (restart same cards).
- **Exit conditions**: Back button -> exit confirmation dialog ("Exit practice? Progress will be lost."). Session completion -> completion screen -> Home Screen.
- **Layout** (card session -- TRANSLATE/VERBS block):
```
+-------------------------------------------+
| [<-] Daily Practice        [Translation]  |
+-------------------------------------------+
| [========                        ] 5/25   |
|                                           |
| +---------------------------------------+ |
| | RU                                    | |
| | Он работает в офисе        [Speaker]  | |
| |                                       | |
| | lavorare #5 [>] Presente [>] 2nd [>] | |
| +---------------------------------------+ |
|                                           |
| [Your translation              ] [Mic]    |
| [Mic] [Keyboard] [Book]    [Eye] KB      |
|           [Check]                         |
|                                           |
| [<]   [Pause] [Stop] [>]                 |
+-------------------------------------------+
```
- **Layout** (vocab flashcard block):
```
+-------------------------------------------+
| [<-] Daily Practice        [Vocabulary]   |
+-------------------------------------------+
| [========                        ] 15/25  |
|                                           |
| +---------------------------------------+ |
| |        lavorare                        | |
| |            [Speaker]                   | |
| |         to work                        | |
| +---------------------------------------+ |
|                                           |
|        "You said: lavorare"               |
|              [Mic 64dp]                   |
|                                           |
| [Again] [Hard] [Good] [Easy]             |
+-------------------------------------------+
```
- **Layout** (block transition sparkle overlay):
```
+-------------------------------------------+
|  (dimmed background)                      |
|                                           |
|    +---------------------------------+    |
|    |          sparkle                |    |
|    |    "Next: Vocabulary"           |    |
|    +---------------------------------+    |
|                                           |
+-------------------------------------------+
```
- **Layout** (completion screen):
```
+-------------------------------------------+
|                                           |
|       "Session Complete!"                 |
|                                           |
|  Great job! You practiced translations,   |
|  vocabulary, and verb conjugations.       |
|                                           |
|         [Back to Home]                    |
+-------------------------------------------+
```
- **Components**:
  - **DailyPracticeHeader**: Back button + "Daily Practice" title + block type badge (Translation/Vocabulary/Verbs in primaryContainer Card)
  - **BlockProgressBar**: Linear progress bar + "X/Y" label
  - **BlockSparkleOverlay**: Full-screen semi-transparent overlay with sparkle emoji, "Next: [BlockType]" or "Daily practice complete!" message. Auto-dismisses after 800ms.
  - **CardSessionBlock** (TRANSLATE/VERBS): Wraps `TrainingCardSession` with `DailyPracticeSessionProvider`. Shows Russian prompt card with verb/tense/group hint chips (SuggestionChip) for verb cards, input controls (hint card, incorrect feedback, text field, word bank, input mode selector, show answer, check button), and navigation.
  - **VocabFlashcardBlock**: Shows word in prompt language (large, 28sp, bold), TTS button, translation text (18sp, primary), microphone button (64dp), voice recognition feedback, and 4 rating buttons (Again=red, Hard=orange, Good=green, Easy=blue).
  - **DailyPracticeCompletionScreen**: "Session Complete!" heading, description text, "Back to Home" button
  - **Exit dialog**: AlertDialog "Exit practice?" with "Stay" and "Exit" buttons
  - **Loading dialog**: CircularProgressIndicator + "Loading session..." shown during session initialization
- **State displayed**:
  - `dailySession` (DailySessionState), `blockProgress` (BlockProgress), `currentTask` (DailyTask), `ttsState`, `selectedLanguageId`
- **User interactions**:
  - TRANSLATE/VERBS block: Same as Training Screen -- type/speak answer, check, navigate cards
  - VOCAB block: Tap mic to speak translation, tap rating button (Again/Hard/Good/Easy)
  - Tap back: Exit confirmation dialog
  - Rating buttons auto-advance to next card
  - Correct voice answer in VOCAB block auto-advances with "Good" rating
- **Business rules**:
  - 3-block structure: TRANSLATE (10 cards) -> VOCAB (5 cards) -> VERBS (10 cards)
  - Block transitions show sparkle overlay with next block name for 800ms
  - Final block completion shows "Daily practice complete!" sparkle
  - Verb cards show hint chips: verb name + rank, tense (abbreviated), conjugation group
  - Verb hint chips do NOT open bottom sheets in daily practice (unlike standalone Verb Drill)
  - Voice auto-trigger: When voice mode is active and new card appears, speech recognition launches after 200ms (1200ms after incorrect feedback)
  - Correct voice answer auto-advances after 400ms
  - Vocab flashcard block: both prompt and answer text are always visible (no flip mechanic)
  - Vocab voice recognition: compares normalized spoken text against answer, auto-rates "Good" on match
  - Exit dialog warns "progress in this session will be lost"
  - Session completion calls `cancelDailySession()` and returns to HOME
- **Edge cases**:
  - Loading state: If session not yet active or no current task, shows spinner with "Loading session..."
  - Resume dialog: If resumable daily session exists, offers Continue (new cards) or Repeat (restart)
  - Session loading overlay: Dialog with spinner during `startDailyPractice` / `repeatDailyPractice` IO operations

---

## 19.6 Verb Drill Selection Screen

- **Purpose**: Pre-session configuration screen for verb conjugation drill. Allows filtering by tense, conjugation group, and frequency sorting before starting.
- **Entry conditions**: From Home Screen (tap Verb Drill tile) -> `AppScreen.VERB_DRILL`. Shown when `VerbDrillViewModel.uiState.session == null`.
- **Exit conditions**: Back button -> Home Screen. "Start"/"Continue" button -> Verb Drill Session.
- **Layout**:
```
+-------------------------------------------+
| [<-] Verb Drill                           |
+-------------------------------------------+
|                                           |
| Time:                                     |
| [All tenses v]                            |
|                                           |
| Group:                                    |
| [All groups v]                            |
|                                           |
| [x] Po chastotnosti                      |
|                                           |
| Progress: 45 / 120                        |
| Today: 10                                 |
| [=========                     ]          |
|                                           |
|           [Start] / [Continue]            |
+-------------------------------------------+
```
- **Components**:
  - **Header row**: Back IconButton + "Verb Drill" title
  - **TenseDropdown**: Label "Time:" + TextButton dropdown with "All tenses" + available tenses
  - **GroupDropdown**: Label "Group:" + TextButton dropdown with "All groups" + available groups
  - **Checkbox**: "Po chastotnosti" (sort by frequency)
  - **Progress section**: "Progress: X/Y" + "Today: N" + LinearProgressIndicator
  - **Start/Continue Button**: Full-width. Text changes based on `todayShownCount > 0`
- **State displayed**: `VerbDrillUiState` fields: `selectedTense`, `availableTenses`, `selectedGroup`, `availableGroups`, `sortByFrequency`, `everShownCount`, `totalCards`, `todayShownCount`, `allDoneToday`
- **User interactions**:
  - Tap tense dropdown: Select a tense filter
  - Tap group dropdown: Select a conjugation group filter
  - Toggle frequency checkbox: Sort cards by frequency
  - Tap Start/Continue: Begin or resume session
- **Business rules**:
  - If `allDoneToday` is true, shows "Na segodnya vsyo!" message instead of start button
  - Progress shows cumulative cards ever shown vs total, plus today's count
  - Filters narrow the card pool for the session
- **Edge cases**:
  - No verb drill data loaded: Loading spinner shown at screen level
  - All cards done today: Disable start, show completion message

---

## 19.7 Verb Drill Session Screen

- **Purpose**: Card-by-card verb conjugation drill session. Shows Russian prompt, accepts Italian conjugated form, provides verb/tense hints, and tracks correct/incorrect with retry logic.
- **Entry conditions**: From Verb Drill Selection Screen (tap Start/Continue). Also `VerbDrillCardSessionProvider` manages state.
- **Exit conditions**: Back button -> exits session. Session completion -> Verb Drill Completion Screen.
- **Layout**:
```
+-------------------------------------------+
| [<-] Verb Drill                           |
+-------------------------------------------+
| +---------------------------------------+ |
| | RU                                    | |
| | Я работаю в офисе          [Speaker]  | |
| |                                       | |
| | lavorare #5 [>]  Presente [>]        | |
| +---------------------------------------+ |
|                                           |
| [=========                     ] 3/10     |
|                                           |
| + Answer: lui lavora in ufficio [Speaker]+|
|                                           |
| Incorrect    2 attempts left              |
| [Your translation              ] [Mic]    |
| [Mic] [Keyboard] [Book]  [Eye] [Flag] KB |
|           [Check]                         |
|                                           |
| [<]   [Pause] [Stop] [>]                 |
+-------------------------------------------+
```
- **Components**:
  - **Custom header**: Back IconButton + "Verb Drill" title
  - **Card content**: Material Card with "RU" label + Russian prompt (20sp) + TTS speaker button. Below prompt: verb name SuggestionChip (with rank, chevron icon) + tense SuggestionChip (abbreviated)
  - **ProgressIndicator**: Default `DrillProgressRow`-style bar + speedometer from `TrainingCardSession`
  - **Hint answer card**: Red-tinted Card showing "Answer: [text]" + error-colored TTS button. Shown when eye button pressed or 3 wrong attempts.
  - **Incorrect feedback row**: "Incorrect" red text + "N attempts left" text
  - **Input controls**: OutlinedTextField (clears incorrect feedback on type change), voice/keyboard/word bank mode buttons, show answer button (disabled when hint shown), report button, mode label, Check button
  - **Navigation**: Prev/Pause/Exit/Next NavIconButtons from TrainingCardSession
  - **VerbReferenceBottomSheet**: Modal bottom sheet showing verb infinitive, group, tense, full conjugation table, TTS for infinitive
  - **TenseInfoBottomSheet**: Modal bottom sheet showing tense name (full + abbreviated), formula card, usage explanation (Russian), example cards (Italian + Russian + optional note)
- **State displayed**: `VerbDrillUiState.session`, `VerbDrillCardSessionProvider` state (current card, hint answer, incorrect feedback, remaining attempts, voice trigger)
- **User interactions**:
  - Same input modes as Training Screen (keyboard, voice, word bank)
  - Tap verb chip: Open verb reference bottom sheet
  - Tap tense chip: Open tense info bottom sheet
  - Tap show answer: Reveal hint answer (disables further show-answer taps)
  - Tap report: Open report bottom sheet (flag/unflag, hide, export, copy)
  - Auto-advance: After correct voice answer, auto-advances after 500ms
- **Business rules**:
  - 3 attempts per card. After 3 wrong attempts, hint answer is shown.
  - Show answer button reveals hint immediately (no attempts consumed)
  - Hint answer card is red-tinted (errorContainer at 0.3 alpha)
  - Incorrect feedback clears when user starts typing again
  - Verb chip shows verb infinitive + rank number (e.g., "lavorare #5")
  - Tense names abbreviated: "Presente" -> "Pres.", "Passato Prossimo" -> "P. Pross.", etc.
  - Tense info includes: formula, usage explanation in Russian, Italian-Russian example pairs
  - Session is batch-based: 10 cards per batch. After completion, "Eshche" button starts next batch.
- **Edge cases**:
  - Loading state: Full-screen spinner with "Loading..." text
  - All cards done today: Completion screen shows only "Vykhod" button, no "Eshche"

---

## 19.8 Verb Drill Completion Screen

- **Purpose**: Post-session summary showing results and option to continue with another batch.
- **Entry conditions**: After all cards in a verb drill batch are completed.
- **Exit conditions**: "Vykhod" (Exit) button -> back to Verb Drill Selection. "Eshche" (More) button -> new batch of cards.
- **Layout**:
```
+-------------------------------------------+
|                                           |
|              sparkle                      |
|           "Otlichno!"                     |
|                                           |
|  Pravilnykh: 8  |  Oshibok: 2            |
|                                           |
|           [Eshche]                        |
|           [Vykhod]                        |
+-------------------------------------------+
```
- **Components**:
  - Sparkle emoji (48sp)
  - "Otlichno!" text (bold, 24sp)
  - Stats: "Pravilnykh: N | Oshibok: N" (semi-transparent)
  - "Eshche" (More) Button: Full-width. Starts next batch.
  - "Vykhod" (Exit) OutlinedButton: Full-width. Returns to selection screen.
- **State displayed**: `session.correctCount`, `session.incorrectCount`, `state.allDoneToday`
- **User interactions**:
  - Tap "Eshche": Start new batch of 10 cards
  - Tap "Vykhod": Exit to selection screen
- **Business rules**:
  - "Eshche" button hidden when `allDoneToday` is true (all available cards exhausted for today)
- **Edge cases**: None -- this screen is straightforward.

---

## 19.9 Vocab Drill Selection Screen

- **Purpose**: Pre-session configuration for vocabulary flashcard drill (Anki-style). Allows filtering by direction, part of speech, word frequency, and voice input mode.
- **Entry conditions**: From Home Screen (tap Flashcards tile) -> `AppScreen.VOCAB_DRILL`. Shown when `VocabDrillViewModel.uiState.session == null`.
- **Exit conditions**: Back button -> Home Screen (triggers `refreshVocabMasteryCount`). "Start (N due)" button -> Vocab Drill Card Screen.
- **Layout**:
```
+-------------------------------------------+
| [<-] Flashcards                           |
+-------------------------------------------+
|                                           |
| Direction                                 |
| [IT -> RU] [RU -> IT]                    |
|                                           |
| [Mic icon] Voice input (auto)    [Switch] |
|                                           |
| Part of speech                            |
| [All] [Nouns] [Verbs] [Adj.] [Adv.]      |
|                                           |
| Word frequency                            |
| [Top 100] [Top 500] [Top 1000] [All]     |
|                                           |
| +---------------------------------------+ |
| | Due: 45 / 200                         | |
| | Mastered: 15 words                    | |
| | Nouns: 8 | Verbs: 5 | Adj.: 2        | |
| | [===========                ]          | |
| +---------------------------------------+ |
|                                           |
|      [Start (45 due)]                     |
+-------------------------------------------+
```
- **Components**:
  - **Header row**: Back IconButton + "Flashcards" title
  - **Direction FilterChips**: "IT -> RU" and "RU -> IT" options
  - **Voice input toggle**: Mic icon + "Voice input (auto)" label + Switch
  - **POS FilterChips**: "All" + dynamic list (Nouns, Verbs, Adj., Adv.) based on available data
  - **Frequency FilterChips**: "Top 100" (0-100), "Top 500" (0-500), "Top 1000" (0-1000), "All" (0-MAX)
  - **Stats Card**: primaryContainer background. Shows due count, mastered count, per-POS breakdown, progress bar (learned/total)
  - **Start Button**: Full-width. Disabled when `dueCount == 0`. Shows "Start (N due)" or "No due words"
- **State displayed**: `VocabDrillUiState`: `drillDirection`, `voiceModeEnabled`, `selectedPos`, `availablePos`, `rankMin`, `rankMax`, `dueCount`, `totalCount`, `masteredCount`, `masteredByPos`
- **User interactions**:
  - Tap direction chips: Switch drill direction
  - Toggle voice switch: Enable/disable automatic voice input mode
  - Tap POS chips: Filter by part of speech
  - Tap frequency chips: Filter by rank range
  - Tap Start: Begin session with due words
- **Business rules**:
  - Due words are calculated based on Spaced Repetition Config (interval ladder)
  - Start button disabled when no words are due
  - Stats card updates in real-time as filters change
- **Edge cases**:
  - No words loaded: "No words loaded" text instead of stats card
  - No due words: Button shows "No due words" and is disabled

---

## 19.10 Vocab Drill Card Screen

- **Purpose**: Anki-style flashcard review. Shows word on front, user can attempt voice translation, then flip to see full details and rate recall.
- **Entry conditions**: From Vocab Drill Selection (tap Start).
- **Exit conditions**: Back button -> exits session. Session completion -> Vocab Drill Completion Screen. All cards reviewed -> completion.
- **Layout** (front/unflipped):
```
+-------------------------------------------+
| [<-] Flashcards                   3/15    |
| [=========                ]               |
|                                           |
| +---------------------------------------+ |
| | [noun] [#42]                          | |
| |                                       | |
| |           lavorare                    | |
| |            [Speaker]                  | |
| |                                       | |
| |         "Tap to speak"                | |
| |           [Mic 72dp]                  | |
| +---------------------------------------+ |
|                                           |
|     [Skip]            [Flip]              |
+-------------------------------------------+
```
- **Layout** (front, voice completed correct):
```
|           lavorare                        |
|            [Speaker]                      |
|                                           |
| +---------------------------------------+ |
| | "lavorare" (recognized text)          | |
| | [check] Correct!                      | |
| +---------------------------------------+ |
+-------------------------------------------+
```
- **Layout** (back/flipped):
```
+-------------------------------------------+
| [<-] Flashcards                   3/15    |
| [=========                ]               |
|                                           |
| +---------------------------------------+ |
| | [noun] lavorare          [Speaker]    | |
| | to work                                | |
| |                                       | |
| | Forms                                 | |
| | m sg    f sg    m pl    f pl          | |
| | -       -       -       -             | |
| |                                       | |
| | Collocations                          | |
| | lavorare bene                         | |
| | lavorare sodo                         | |
| |                                       | |
| | Step 3/9                              | |
| +---------------------------------------+ |
|                                           |
| [Again]  [Hard]                           |
| [<1m]    [2d]                             |
| [Good]   [Easy]                           |
| [4d]     [7d]                             |
+-------------------------------------------+
```
- **Components**:
  - **Header row**: Back IconButton + "Flashcards" title + "X/Y" progress counter (primary color)
  - **LinearProgressIndicator**: Full-width progress bar
  - **Card front**: RoundedCornerShape(16dp) Card with dynamic background color (green tint on correct, red tint on wrong, default surfaceVariant). Contains: POS badge, rank badge, main word (32sp, bold), TTS button (IT_TO_RU only), microphone button (72dp FilledTonalIconButton) or voice result feedback
  - **VoiceResultFeedback**: Card showing recognized text (italic), result (Correct with green check / Wrong with red X / Skipped)
  - **Card back**: secondaryContainer Card with: word + TTS button, Russian translation, forms table (adjectives: msg/fsg/mpl/fpl), collocations list (max 5 shown), mastery step indicator
  - **POS badge**: Small colored Card with part of speech label (noun=primaryContainer, verb=secondaryContainer, adj.=tertiaryContainer, adv.=errorContainer)
  - **Rank badge**: Small surfaceVariant Card with "#N" rank
  - **Rating buttons** (flipped): 2x2 grid:
    - "Again" (OutlinedButton, red) with "<1m" interval
    - "Hard" (OutlinedButton, orange) with current step interval
    - "Good" (Button, primary) with next step interval
    - "Easy" (Button, green) with step+2 interval
  - **Skip/Flip buttons** (unflipped): "Skip" (OutlinedButton with SkipNext icon) + "Flip" (Button with Flip icon)
- **State displayed**: `VocabDrillSessionState`: cards, currentIndex, isFlipped, direction, voiceCompleted, voiceResult, voiceRecognizedText, voiceAttempts
- **User interactions**:
  - Tap mic: Start voice recognition for translation
  - Tap TTS button: Play word pronunciation
  - Tap Skip: Skip voice input and flip card (or auto-flip if voice already completed)
  - Tap Flip: Show card back with answer details
  - Tap rating button (Again/Hard/Good/Easy): Rate recall quality, advance interval, move to next card
- **Business rules**:
  - Voice auto-starts when voice mode is enabled (500ms delay on new card)
  - Voice auto-flips card on correct answer (800ms delay after voice result)
  - Voice attempts: max 3. After 3 wrong, shows "Moving on..." and auto-flips
  - Voice skips: counted as SKIPPED result
  - Interval ladder: [1, 2, 4, 7, 10, 14, 20, 28, 42, 56] days
  - "Again" resets interval to step 0 (<1m = 1 day)
  - "Hard" keeps current interval step
  - "Good" advances 1 step
  - "Easy" advances 2 steps
  - Forms section only shown for words with non-empty `forms` map
  - Collocations show max 5, with "+N more" overflow
  - Mastery step indicator shows "Step X/9" or "Learned" when max
- **Edge cases**:
  - Card index out of bounds: `getOrElse` returns early, nothing rendered
  - Voice already completed: Mic button replaced by result feedback
  - No voice completed and user taps Skip: Calls `skipVoice()`, then flips

---

## 19.11 Vocab Drill Completion Screen

- **Purpose**: Post-session summary for vocabulary flashcard drill.
- **Entry conditions**: After all cards in a vocab drill session are reviewed.
- **Exit conditions**: "Exit" button -> returns to selection screen. "Continue" button -> starts a new session.
- **Layout**:
```
+-------------------------------------------+
|                                           |
|           "Perfect!" / "Done!"            |
|                                           |
| +---------------------------------------+ |
| |      12          3                     | |
| |   Correct      Wrong                  | |
| |      15 words reviewed                | |
| +---------------------------------------+ |
|                                           |
|    [Exit]            [Continue]           |
+-------------------------------------------+
```
- **Components**:
  - Title text: "Perfect!" (if all correct) or "Done!" (bold, 28sp, primary)
  - Stats Card: Correct count (32sp, primary, bold) + Wrong count (32sp, error, bold) + "N words reviewed" text
  - "Exit" OutlinedButton + "Continue" Button (full-width row)
  - Fade-in animation: Stats appear after 800ms delay
- **State displayed**: `session.correctCount`, `session.incorrectCount`, `session.cards.size`
- **User interactions**:
  - Tap Exit: Return to selection screen
  - Tap Continue: Start new session
- **Business rules**:
  - Stats card hidden for first 800ms (animated reveal)
  - "Perfect!" shown only when `correctCount == cards.size`
- **Edge cases**: None.

---

## 19.12 Boss Battle Screen

- **Purpose**: End-of-lesson review challenge that tests pattern stability under pressure. Reuses the Training Screen in boss mode.
- **Entry conditions**: From Lesson Roadmap (tap Review or Mega boss tile). Sets `bossActive = true` in TrainingUiState.
- **Exit conditions**: Same as Training Screen. Completion auto-returns to Lesson Roadmap via `bossFinishedToken`.
- **Layout**: Identical to Training Screen layout, with "Review Session" title instead of lesson info.
- **Components**: Same as Training Screen components.
- **State displayed**: `bossActive`, `bossProgress`, `bossTotal`, plus all Training Screen state.
- **User interactions**: Same as Training Screen.
- **Business rules**:
  - Boss battles unlock after completing 15 sub-lessons
  - "Review" boss uses lesson cards; "Mega" boss uses cross-lesson cards
  - Boss reward shown after completion: bronze/silver/gold trophy (BossReward dialog)
  - Exit dialog during boss: calls `finishBoss()` instead of `finishSession()`
- **Edge cases**:
  - Boss error: Shows error AlertDialog with boss error message
  - Boss reward: Shows reward AlertDialog with colored trophy icon

---

## 19.13 Settings Sheet

- **Purpose**: Full configuration panel for the app. Opened as a `ModalBottomSheet` from any screen.
- **Entry conditions**: Tap settings gear icon on any screen.
- **Exit conditions**: Tap outside sheet, swipe down, or tap any navigation action within the sheet.
- **Layout** (scrollable):
```
+-------------------------------------------+
| Service Mode                              |
+-------------------------------------------+
| Test Mode                    [Switch]     |
| Enables all lessons, accepts all answers  |
|                                           |
| [Show Ladder]                             |
| [Vocabulary Sprint limit     ]            |
| Set how many words to show (0 = all)      |
|                                           |
| Pronunciation speed                       |
| 0.5x [====Slider====] 1.5x               |
|           0.67x                           |
|                                           |
| Voice recognition                         |
| Offline speech recognition     [Switch]   |
| Using Google speech recognition           |
|                                           |
| Translation text size                     |
| 1.0x [====Slider====] 2.0x               |
|           1.0x                            |
|                                           |
| Language: [English v]                     |
| Pack: [Italian Basics v]                  |
|                                           |
| [New language          ]                  |
| [Add language]                            |
| [Import lesson pack (ZIP)]                |
| [Import lesson (CSV)]                     |
| [Reset/Reload (clear + import)]           |
| [Empty lesson (title)  ]                  |
| [Create empty lesson]                     |
| [Delete all lessons]        (red)         |
| [Reset all progress]        (red)         |
|                                           |
| CSV format                                |
| UTF-8, delimiter ';'...                   |
|                                           |
| Instructions                              |
| Play - start/continue...                  |
|                                           |
| Packs                                     |
| Italian Basics (1.0)              [Delete]|
|                                           |
| Profile                                   |
| [Your Name          ]                     |
| [Save Name]                               |
|                                           |
| Backup & Restore                          |
| [Save progress now]                       |
| [Restore from backup]                     |
+-------------------------------------------+
```
- **Components**:
  - **Test Mode toggle**: Switch + description
  - **Ladder button**: OutlinedButton with Insights icon -> navigates to LadderScreen
  - **Vocab limit field**: OutlinedTextField (digits only)
  - **TTS speed slider**: Slider (0.5-1.5, 3 steps) + current value display
  - **Offline ASR toggle**: Switch + download state indicator (downloading/extracting/error/ready status text + progress bar)
  - **Russian text scale slider**: Slider (1.0-2.0, 3 steps) + current value display
  - **Language dropdown**: DropdownSelector with installed languages
  - **Pack dropdown**: DropdownSelector with packs for current language
  - **Add language**: OutlinedTextField + "Add language" button
  - **Import lesson pack**: OutlinedButton with Upload icon, launches file picker for ZIP
  - **Import lesson CSV**: OutlinedButton with Upload icon, launches file picker for CSV
  - **Reset/Reload**: OutlinedButton with Upload icon, clears + re-imports CSV
  - **Create empty lesson**: OutlinedTextField + "Create empty lesson" button
  - **Delete all lessons**: Red OutlinedButton with Delete icon
  - **Reset all progress**: Red OutlinedButton with Refresh icon
  - **CSV format info**: Static text explaining CSV format
  - **Instructions**: Static text about play/pause/stop
  - **Installed packs list**: Pack name + Delete IconButton per pack
  - **Profile section**: Name text field + "Save Name" button
  - **Backup section**: "Save progress now" + "Restore from backup" buttons
- **State displayed**: `testMode`, `vocabSprintLimit`, `ttsSpeed`, `useOfflineAsr`, `asrModelReady`, `asrDownloadState`, `ruTextScale`, `languages`, `selectedLanguageId`, `installedPacks`, `activePackId`, `userName`
- **User interactions**:
  - Toggle test mode: Enable/disable test mode
  - Tap ladder: Navigate to LadderScreen
  - Change vocab limit: Update sprint limit
  - Adjust TTS speed slider: Set pronunciation speed
  - Toggle offline ASR: Enable/disable offline speech recognition; triggers download if model not ready
  - Adjust text scale slider: Scale Russian text size
  - Select language: Switch active language
  - Select pack: Switch active lesson pack
  - Add language: Create new language entry
  - Import pack: Launch file picker, import ZIP lesson pack
  - Import CSV: Launch file picker, import single CSV lesson
  - Reset/Reload: Launch file picker, clear existing lessons and import new CSV
  - Create empty lesson: Create a new lesson with no cards
  - Delete all lessons: Remove all lessons (destructive, red button)
  - Reset all progress: Clear all progress data (destructive, red button)
  - Delete pack: Remove an installed pack
  - Save name: Update user profile name
  - Save/restore backup: Export/import progress data
- **Business rules**:
  - Changing language reloads lessons and resets active pack
  - Changing pack updates active pack and refreshes lesson list
  - TTS download continues in background (persistent progress bar on all screens)
  - ASR download triggered when offline ASR toggled on and model not ready
  - File pickers use `ActivityResultContracts.OpenDocument` (CSV/ZIP) and `OpenDocumentTree` (backup restore)
- **Edge cases**:
  - No installed packs: Shows "No installed packs" text
  - ASR download error: Shows error message in red
  - Name field empty or unchanged: Save button disabled

---

## 19.14 Ladder Screen

- **Purpose**: Displays the interval ladder for all lessons in the current pack. Shows each lesson's card count, days since last review, and current interval status.
- **Entry conditions**: From Settings Sheet (tap "Show Ladder" button).
- **Exit conditions**: Back button -> returns to previous screen (Settings or Training).
- **Layout**:
```
+-------------------------------------------+
| [<-] Lestnitsa intervalov                 |
|      Vse uroki tekushchego paketa         |
+-------------------------------------------+
| #   Urok        Karty Dney Interval       |
|                                           |
| [1  Lesson 1     45    3    4d          ] |
| [2  Lesson 2     30    12   Prosrochka! ] |
| [3  Lesson 3     0     -    -           ] |
+-------------------------------------------+
```
- **Components**:
  - **Header**: Back IconButton + "Lestnitsa intervalov" title + "Vse uroki tekushchego paketa" subtitle
  - **LadderHeaderRow**: Column headers (#, Urok, Karty, Dney, Interval) in labelMedium, alpha 0.7
  - **LadderRowCard**: Card per lesson showing: index, title (ellipsized), unique card shows, days since last show, interval label
  - Overdue rows use `errorContainer` background
- **State displayed**: `ladderRows` (List of `LessonLadderRow`)
- **User interactions**: Scroll the list. No interactive elements beyond back navigation.
- **Business rules**:
  - Overdue lessons (interval label starts with "Prosrochka") highlighted with red background
  - No data: Shows "Net dannykh po urokam" placeholder text
- **Edge cases**:
  - Empty `ladderRows`: Shows placeholder text and returns early

---

## 19.15 Story Quiz Screen

- **Purpose**: Reading comprehension quiz shown during story check-in/check-out phases. Presents a story text followed by multiple-choice questions.
- **Entry conditions**: From Lesson Roadmap (story phase triggers). Currently story tiles are not rendered in the roadmap, so this screen is effectively unused but maintained for backward compatibility.
- **Exit conditions**: Back/close button -> Lesson Roadmap. Complete all questions -> Lesson Roadmap.
- **Layout**:
```
+-------------------------------------------+
| Story Check-in / Story Check-out          |
+-------------------------------------------+
| [Story text body]                         |
|                                           |
| Question 1 / 3                            |
| The question prompt text                  |
|                                           |
|  > Option A                               |
|    Option B                               |
|    Option C                               |
|                                           |
| Correct: Option A                         |
|                                           |
| [Prev]  [Check]  [Next/Finish]            |
+-------------------------------------------+
```
- **Components**:
  - **Phase title**: "Story Check-in" or "Story Check-out"
  - **Story text**: Body medium text
  - **Question counter**: "Question X / Y"
  - **Question prompt**: Semi-bold text
  - **Option rows**: Clickable rows with ">" indicator for selected option. Result annotations: "(correct)", "(your choice)"
  - **Correct answer text**: Primary color, shown after checking
  - **Error message**: Red text for "Select an answer" or "Incorrect"
  - **Navigation**: Prev (outlined), Check (filled), Next/Finish (filled) buttons
  - **Scroll hint**: "Scroll to continue" shown when content exceeds viewport
- **State displayed**: `activeStory` (StoryQuiz), `testMode`, local selection/result state
- **User interactions**:
  - Tap option row: Select answer (can re-select before checking)
  - Tap Check: Validate selected answer, show result
  - Tap Next: Move to next question (validates first)
  - Tap Finish: Complete story (last question)
  - Tap Prev: Go to previous question
- **Business rules**:
  - Must select an option before checking
  - "Check" shows correct/incorrect result inline
  - "Next" validates then advances; on last question, completes the story
  - In test mode, all stories are marked as completed regardless of correctness
  - Empty questions list: Auto-completes with `onComplete(true)`
  - Null story: Auto-closes via `onClose()`
- **Edge cases**:
  - Null story: Immediately closes
  - Empty questions: Immediately completes as correct
  - Story error: Shows error AlertDialog

---

## 19.16 Welcome/Onboarding Dialog

- **Purpose**: First-launch dialog asking for user's name to personalize the experience.
- **Entry conditions**: Triggered by `LaunchedEffect` when user navigates away from HOME and `userName == "GrammarMateUser"` (default). Also shown on first launch.
- **Exit conditions**: Tap "Continue" (with name entered) or "Skip" (keeps default name).
- **Layout**:
```
+-------------------------------------------+
|        Welcome to GrammarMate!            |
+-------------------------------------------+
|                                           |
| What's your name?                         |
| [Enter your name        ]                 |
|                                           |
|           [Skip]  [Continue]              |
+-------------------------------------------+
```
- **Components**:
  - **Title**: "Welcome to GrammarMate!" (headlineSmall)
  - **Body text**: "What's your name?"
  - **OutlinedTextField**: Name input with "Enter your name" placeholder, max 50 chars, single line, Done IME action
  - **"Skip" TextButton**: Sets name to "GrammarMateUser"
  - **"Continue" TextButton**: Sets name to trimmed input (or "GrammarMateUser" if blank)
- **State displayed**: None (local input state only)
- **User interactions**:
  - Type name in text field
  - Press Done on keyboard -> same as Continue
  - Tap Skip -> dismiss with default name
  - Tap Continue -> save name
- **Business rules**:
  - Cannot be dismissed by tapping outside (empty `onDismissRequest`)
  - Name truncated to 50 characters
  - Blank input treated as "GrammarMateUser"
- **Edge cases**: None -- straightforward dialog.

---

## 19.17 TTS Download Dialog

- **Purpose**: Modal dialog for downloading text-to-speech pronunciation models. Shows download progress and handles completion/errors.
- **Entry conditions**: Tap TTS speaker button when TTS model is not downloaded (`ttsModelReady == false`).
- **Exit conditions**: Dismiss, download complete (auto-closes and plays), or error.
- **Layout** (varies by state):
```
+-------------------------------------------+
| Download pronunciation model?             |
+-------------------------------------------+
| This will download ~346 MB (Italian       |
| pronunciation). Uses internal storage.    |
|                                           |
|              [Cancel]  [Download]         |
+-------------------------------------------+

+-------------------------------------------+
| Download pronunciation model?             |
+-------------------------------------------+
| Downloading... 67%                        |
| [===========                  ]           |
|                                           |
|    [Continue in background]               |
+-------------------------------------------+
```
- **Components**:
  - **AlertDialog** with dynamic content based on `DownloadState`:
    - Idle: Size info + "Download" button
    - Downloading: Percentage + progress bar + "Continue in background" button
    - Extracting: Same as downloading
    - Done: "Pronunciation model ready!" + "OK" button
    - Error: "Download failed: [message]" + "OK" button
- **State displayed**: `ttsDownloadState`, `selectedLanguageId`
- **User interactions**:
  - Tap Download: Start model download
  - Tap Cancel: Dismiss dialog
  - Tap Continue in background: Dismiss dialog, download continues
  - Tap OK: Dismiss after completion/error
- **Business rules**:
  - Auto-closes when download completes and auto-plays TTS for the current card
  - Background download progress shown as persistent bar on all screens
  - Metered network warning shown separately before download begins
- **Edge cases**:
  - Background download state synced from `bgTtsDownloadStates` when dialog opens

---

## 19.18 Metered Network Warning Dialog (TTS)

- **Purpose**: Warns user before downloading TTS model on cellular/metered connection.
- **Entry conditions**: TTS download initiated on metered network (`ttsMeteredNetwork == true`).
- **Exit conditions**: "Download anyway" -> proceed with download. "Cancel" -> abort.
- **Layout**:
```
+-------------------------------------------+
| Metered network detected                  |
+-------------------------------------------+
| You appear to be on a cellular or         |
| metered connection. The pronunciation     |
| model is ~346 MB. Continue downloading?   |
|                                           |
|              [Cancel]  [Download anyway]  |
+-------------------------------------------+
```
- **Components**: Standard AlertDialog with warning text and two action buttons.
- **Business rules**: Must confirm before download proceeds on metered connection.

---

## 19.19 ASR Metered Network Warning Dialog

- **Purpose**: Same as TTS metered warning but for offline speech recognition model (~375 MB).
- **Entry conditions**: ASR download initiated on metered network (`asrMeteredNetwork == true`).
- **Exit conditions**: Same as TTS metered warning.
- **Layout**: Identical to TTS metered warning with model size updated to ~375 MB.

---

## 19.20 Exit Confirmation Dialog

- **Purpose**: Confirms user wants to end the current training/daily practice session.
- **Entry conditions**: Back gesture/button during training, or tap Stop button.
- **Exit conditions**: "Exit" -> end session. "Cancel" -> dismiss.
- **Layout**:
```
+-------------------------------------------+
| End session?                              |
+-------------------------------------------+
| Current session will be completed.        |
|                                           |
|              [Cancel]  [Exit]             |
+-------------------------------------------+
```
- **Components**: Standard AlertDialog.
- **Business rules**:
  - During boss mode: calls `finishBoss()` instead of `finishSession()`
  - During drill mode: calls `exitDrillMode()` instead of `finishSession()`
  - After exit, navigates to Lesson Roadmap

---

## 19.21 Daily Practice Resume Dialog

- **Purpose**: Offers user choice between continuing a resumable daily session or starting fresh.
- **Entry conditions**: Tapping Daily Practice tile when `hasResumableDailySession()` returns true.
- **Exit conditions**: "Continue" -> resume with new cards. "Repeat" -> restart with same cards.
- **Layout**:
```
+-------------------------------------------+
| Ezhednevnaya praktika                     |
+-------------------------------------------+
| Povtorit - te zhe kartochki snachala      |
| Prodolzhit - novyy nabor kartochek        |
|                                           |
|              [Repeat]  [Continue]         |
+-------------------------------------------+
```
- **Components**: Standard AlertDialog with title "Ezhednevnaya praktika" and descriptive text.
- **Business rules**:
  - "Continue" calls `startDailyPractice(level)` with new card selection
  - "Repeat" calls `repeatDailyPractice(level)` with same cards from beginning
  - Both show loading overlay during session initialization

---

## 19.22 Drill Start Dialog

- **Purpose**: Confirms starting a drill session from the lesson roadmap. Offers resume if previous progress exists.
- **Entry conditions**: Tap Drill tile in Lesson Roadmap.
- **Exit conditions**: "Start"/"Continue" -> begin drill. "Start Fresh" -> restart from beginning. "Cancel" -> dismiss.
- **Layout**:
```
+-------------------------------------------+
| Drill Mode                                |
+-------------------------------------------+
| Continue where you left off or start over?|
|                                           |
| [Cancel] [Start Fresh] [Continue]         |
+-------------------------------------------+
```
- **Components**: Standard AlertDialog.
- **Business rules**:
  - If `hasProgress`: Shows "Continue" (confirm) + "Start Fresh" (dismiss) + "Cancel"
  - If no progress: Shows "Start" (confirm) + "Cancel"

---

## 19.23 Boss Reward Dialog

- **Purpose**: Displays boss battle completion reward (bronze/silver/gold trophy).
- **Entry conditions**: After boss battle completion when `bossRewardMessage != null` and `bossReward != null`.
- **Exit conditions**: "OK" button -> dismiss.
- **Layout**:
```
+-------------------------------------------+
|          [Trophy icon]                    |
| Boss Reward                               |
+-------------------------------------------+
| You earned a gold trophy!                 |
|                                           |
|                    [OK]                   |
+-------------------------------------------+
```
- **Components**:
  - **Trophy icon**: `EmojiEvents` icon colored by reward: bronze (#CD7F32), silver (#C0C0C0), gold (#FFD700)
  - **Title**: "Boss Reward"
  - **Message text**: `bossRewardMessage`
  - **OK button**: Dismisses reward
- **State displayed**: `bossReward` (BRONZE/SILVER/GOLD), `bossRewardMessage`

---

## 19.24 Streak Dialog

- **Purpose**: Displays streak information (consecutive days of practice).
- **Entry conditions**: When `streakMessage != null` in TrainingUiState.
- **Exit conditions**: "Continue" button -> dismiss.
- **Layout**:
```
+-------------------------------------------+
|          "??"                              |
| Streak!                                   |
+-------------------------------------------+
| 5 days in a row!                          |
| Longest streak: 12 days                   |
|                                           |
|               [Continue]                  |
+-------------------------------------------+
```
- **Components**:
  - **Icon**: "??" text (48sp) as icon
  - **Title**: "Streak!"
  - **Message**: `streakMessage` (titleMedium, centered)
  - **Longest streak**: Shown only when `longestStreak > currentStreak`
  - **Continue button**: Dismisses
- **State displayed**: `streakMessage`, `currentStreak`, `longestStreak`

---

## 19.25 Locked Lesson Hint Dialog

- **Purpose**: Informative dialog when user taps an empty lesson slot (no lesson content in pack).
- **Entry conditions**: Tap an EMPTY lesson tile on Home Screen.
- **Exit conditions**: "OK" button -> dismiss.
- **Layout**:
```
+-------------------------------------------+
| Lesson locked                             |
+-------------------------------------------+
| Please complete the previous lesson first.|
|                                           |
|                    [OK]                   |
+-------------------------------------------+
```

---

## 19.26 Early Start Dialog

- **Purpose**: Confirmation dialog when user tries to start a locked lesson or locked sub-lesson early.
- **Entry conditions**: Tap a locked lesson tile on Home Screen, or tap a locked sub-lesson tile on Lesson Roadmap.
- **Exit conditions**: "Yes" -> unlock and proceed. "No" -> dismiss.
- **Layout**:
```
+-------------------------------------------+
| Start early?                              |
+-------------------------------------------+
| Start this lesson early? You can always   |
| come back to review previous lessons.     |
|                                           |
|              [No]  [Yes]                  |
+-------------------------------------------+
```
- **Business rules**: Allows accessing locked content with a warning. Does not affect unlock state of other lessons.

---

## 19.27 How This Training Works Dialog

- **Purpose**: Explains the GrammarMate training methodology.
- **Entry conditions**: Tap "How This Training Works" button on Home Screen.
- **Exit conditions**: "OK" button -> dismiss.
- **Layout**: Simple AlertDialog with explanatory text: "GrammarMate builds automatic grammar patterns with repeated retrieval. States show how stable each pattern is and when it needs refresh."

---

## 19.28 Report Bottom Sheet (Training Screen)

- **Purpose**: Allows users to flag problematic cards, hide cards, export flagged cards, or copy card text during training sessions.
- **Entry conditions**: Tap the flag/report button (ReportProblem icon) in the Training Screen `AnswerBox`.
- **Exit conditions**: Tap outside sheet, swipe down, or complete any action (sheet auto-dismisses).
- **Layout**:
```
+-------------------------------------------+
| Report                                    |
+-------------------------------------------+
| Он работает в офисе                       |
|                                           |
| [!] Remove from bad sentences list        |  <- shown if flagged
|   OR                                      |
| [!] Add to bad sentences list             |  <- shown if not flagged
|                                           |
| [Download] Export bad sentences to file   |
|                                           |
| [Copy] Copy text                          |
+-------------------------------------------+
```
- **Components**:
  - **Card prompt text**: Shows `reportCard.promptRu` for context (bodyMedium, 0.7 alpha)
  - **Flag/unflag TextButton**: Icon + "Add to bad sentences list" or "Remove from bad sentences list". Flag icon is red (error color) when card is already flagged.
  - **Export TextButton**: Download icon + "Export bad sentences to file". Calls `contract.exportFlaggedCards()`.
  - **Copy TextButton**: Copy icon + "Copy text". Copies card ID, source, and target to clipboard.
- **State displayed**: `currentCard` (for prompt text and flag status)
- **User interactions**:
  - Tap flag/unflag: Adds or removes the card from `BadSentenceStore` with `mode = "training"`. Sheet dismisses.
  - Tap export: Calls `exportBadSentences()` and shows exported file path or "No bad sentences to export" message. Sheet dismisses.
  - Tap copy: Copies `ID: {id}\nSource: {promptRu}\nTarget: {acceptedAnswers}` to clipboard.
- **Business rules**:
  - Export uses `BadSentenceStore.exportUnified()` producing `Downloads/BaseGrammy/bad_sentences_all.txt`
  - Flagged card status is persisted immediately
  - In drill mode, flagging also advances to the next card via `advanceDrillCard()`
- **Edge cases**:
  - No card loaded: Sheet content is empty, no actions available

---

## 19.28a Report Bottom Sheet (Daily Practice -- Blocks 1 and 3)

- **Purpose**: Same as Training Screen report sheet but for daily practice sentence translation (Block 1) and verb conjugation (Block 3) cards. Uses `CardSessionContract` methods.
- **Entry conditions**: Tap the flag/report button in `DailyInputControls` during a daily practice session.
- **Exit conditions**: Same as Training Screen report sheet.
- **Layout**: Identical to Training Screen report sheet (19.28).
- **Components**: Same as Training Screen report sheet, but uses `contract.flagCurrentCard()`, `contract.unflagCurrentCard()`, and `contract.exportFlaggedCards()` from the `CardSessionContract` interface.
- **Business rules**:
  - Mode field is `"daily_translate"` (Block 1) or `"daily_verb"` (Block 3) depending on the current block type
  - `hideCurrentCard()` is a no-op in daily practice -- no card hiding
  - Flagged cards do NOT affect session progress or cursor advancement
  - Uses `TrainingViewModel.flagDailyBadSentence()` / `unflagDailyBadSentence()` / `isDailyBadSentence()` / `exportDailyBadSentences()`
- **Edge cases**: Same as Training Screen report sheet

---

## 19.28b Report Bottom Sheet (Daily Practice -- Block 2 Vocab)

- **Purpose**: Report sheet for daily practice vocabulary flashcards. Similar to Training Screen report sheet but uses direct ViewModel callbacks instead of `CardSessionContract`.
- **Entry conditions**: Tap the flag/report button in `VocabFlashcardBlock` during daily practice.
- **Exit conditions**: Same as Training Screen report sheet.
- **Layout**: Identical to Training Screen report sheet (19.28), but prompt shows the vocab word instead of a sentence.
- **Components**: Same options (flag/unflag, export, copy). Uses direct callback parameters:
  - `onFlagDailyBadSentence(word.id, languageId, word.meaningRu ?: word.word, word.word, "daily_vocab")`
  - `onUnflagDailyBadSentence(word.id)`
  - `isDailyBadSentence(word.id)`
  - `onExportDailyBadSentences()`
- **Business rules**:
  - Mode field is always `"daily_vocab"`
  - Flagged icon turns red when card is flagged
  - Export uses `BadSentenceStore.exportUnified()`
- **Edge cases**: Same as Training Screen report sheet

---

## 19.28c Report Bottom Sheet (Verb Drill)

- **Purpose**: Report sheet for standalone verb drill sessions. Same structure as Training Screen report sheet.
- **Entry conditions**: Tap the flag/report button in `DefaultVerbDrillInputControls` during a verb drill session.
- **Exit conditions**: Same as Training Screen report sheet.
- **Layout**: Identical to Training Screen report sheet (19.28).
- **Components**: Same options (flag/unflag, export, copy). Uses `VerbDrillViewModel` methods:
  - `flagBadSentence()` -- mode = `"verb_drill"`, sentence = `card.promptRu`, translation = `card.answer`
  - `unflagBadSentence()`
  - `isBadSentence()` -- checks `currentCardIsBad` from UI state
  - `exportBadSentences()` -- uses `BadSentenceStore.exportUnified()`
- **Business rules**:
  - Mode field is `"verb_drill"`
  - "Hide this card" button is visible but non-functional (known issue R5 in discrepancy report)
  - Flag status updates `currentCardIsBad` in `VerbDrillUiState`, which is checked on card advancement
- **Edge cases**: Same as Training Screen report sheet

---

## 19.28d Report Bottom Sheet (Vocab Drill)

- **Purpose**: Report sheet for standalone vocab drill flashcards. Similar to Vocab Drill Card Screen but with report options.
- **Entry conditions**: Tap the flag/report button on the Vocab Drill card screen.
- **Exit conditions**: Same as Training Screen report sheet.
- **Layout**: Identical to Training Screen report sheet (19.28), but prompt shows the vocab word and its meaning.
- **Components**: Same options (flag/unflag, export, copy). Uses `VocabDrillViewModel` methods:
  - `flagBadSentence()` -- mode = `"vocab_drill"`, sentence = `word.meaningRu ?: word.word`, translation = `word.word`
  - `unflagBadSentence()`
  - `isBadSentence()`
  - `exportBadSentences()` -- uses `BadSentenceStore.exportToTextFile(packId)`
- **Business rules**:
  - Mode field is `"vocab_drill"`
  - Pack ID is `activePackId` if set, otherwise falls back to `"__vocab_drill__"`
  - Flagged icon turns red when card is flagged
- **Edge cases**: Same as Training Screen report sheet

- **Purpose**: Confirmation dialog after exporting flagged bad sentences to a file.
- **Entry conditions**: Tap "Export bad sentences to file" in any report bottom sheet.
- **Exit conditions**: "OK" button -> dismiss.
- **Layout**: AlertDialog with "Export" title and message showing the export file path or "No bad sentences to export".

---

## 19.29 Daily Practice Loading Overlay

- **Purpose**: Blocking dialog shown while daily practice session initializes on IO thread.
- **Entry conditions**: Triggered when starting or resuming daily practice (`isLoadingDaily == true`).
- **Exit conditions**: Automatically dismissed when session initialization completes.
- **Layout**:
```
+-------------------------------------------+
|  +------------------------------------+  |
|  |        [Spinner]                   |  |
|  |     Loading session...             |  |
|  +------------------------------------+  |
+-------------------------------------------+
```
- **Components**: `Dialog` with Card containing `CircularProgressIndicator` + "Loading session..." text.

---

## 19.30 Persistent TTS Download Progress Bar

- **Purpose**: Thin progress bar shown at the top of every screen while a TTS model download is in progress.
- **Entry conditions**: `bgTtsDownloading == true` (any background download active).
- **Exit conditions**: Automatically hidden when all downloads complete.
- **Layout**: 2dp tall `LinearProgressIndicator` spanning full width, shown above all screen content.
- **Business rules**: Aggregates progress from all language downloads (downloading = 90% weighted, extracting = remaining 10%).

---

## 19.31 AppScreen Enum Reference

The `AppScreen` enum defines all possible screen destinations:

| Value | Screen | Notes |
|-------|--------|-------|
| `HOME` | Home Screen | Default/landing screen |
| `LESSON` | Lesson Roadmap | Sub-lesson grid for selected lesson |
| `TRAINING` | Training Screen | Card presentation + answer input |
| `ELITE` | (redirects to HOME) | Kept for backward compat |
| `VOCAB` | (redirects to HOME) | Kept for backward compat |
| `DAILY_PRACTICE` | Daily Practice Screen | 3-block daily session |
| `STORY` | Story Quiz Screen | Reading comprehension quiz |
| `LADDER` | Ladder Screen | Interval ladder overview |
| `VERB_DRILL` | Verb Drill Screen | Verb conjugation practice |
| `VOCAB_DRILL` | Vocab Drill Screen | Flashcard vocabulary review |

**Navigation flow**:
```
HOME -----> LESSON -----> TRAINING
  |              |-----> STORY
  |-----> DAILY_PRACTICE
  |-----> VERB_DRILL
  |-----> VOCAB_DRILL
  |-----> LADDER (via Settings)

Settings: ModalBottomSheet overlay on any screen
BackHandler: Context-sensitive per screen
```
