# 23. Screen Element Registry

## Summary

| Screen | Prefix | Element Count |
|--------|--------|---------------|
| HomeScreen | HS | 22 |
| TrainingScreen | TS | 37 |
| TrainingCardSession | TCS | 29 |
| DailyPracticeScreen | DP | 30 |
| VerbDrillScreen | VD | 41 |
| VocabDrillScreen | VOC | 48 |
| LessonRoadmapScreen | LR | 13 |
| LadderScreen | LS | 11 |
| SettingsScreen (SettingsSheet) | SS | 46 |
| StoryQuizScreen | SQ | 13 |
| GrammarMateApp Dialogs | DG | 17 |
| [UI-CONSISTENCY-2025] Shared Components | SH | 6 |
| **Total** | | **313** |

---

## 1. HomeScreen (ui/screens/HomeScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Avatar circle | HS-01 | image | Always | 40dp CircleShape, primary background, shows user initials (max 2 chars, fallback "GM"). Clickable has no action (decorative). | ? |
| User name text | HS-02 | text | Always | SemiBold weight. Displays `state.navigation.userName`. | ? |
| Language selector | HS-03 | button | Always | TextButton showing uppercase language code (e.g. "IT"). Opens DropdownMenu with all available languages. Selecting a different language calls `onSelectLanguage(id)`. | ? |
| Settings gear icon | HS-04 | button | Always | IconButton with Settings icon. Calls `onOpenSettings()`. | ? |
| Primary action card | HS-05 | card | Always | Clickable Card showing active pack display name ("Continue Learning" / "Start learning") and lesson progress hint ("Lesson N. Exercise X/Y"). Calls `onPrimaryAction()`. | ? |
| "Grammar Roadmap" header | HS-06 | text | Always | SemiBold section header above the lesson tile grid. | ? |
| Lesson tile grid | HS-07 | card | Always (12 tiles) | 4-column LazyVerticalGrid of LessonTile cards (72dp height). Always shows 12 tiles; empty slots for packs with fewer lessons. | ? |
| Lesson tile index number | HS-08 | text | Per tile | SemiBold tile number (1-12). | ? |
| Lesson tile emoji | HS-09 | text | Per tile | Shows flower emoji based on state: LOCKED = lock, UNLOCKED = open lock, SEED/SPROUT/BLOOM = FlowerCalculator emoji, EMPTY = gray dot. | ? |
| Lesson tile mastery percent | HS-10 | text | `masteryPercent > 0` and not LOCKED/UNLOCKED/EMPTY | Shows "N%" in 10sp, 60% alpha. | ? |
| Verb Drill entry tile | HS-11 | card | `hasVerbDrill == true` | Card with FitnessCenter icon + "Verb Drill" label. 64dp height. Calls `onOpenVerbDrill()`. | ? |
| Vocab Drill entry tile | HS-12 | card | `hasVocabDrill == true` | Card with MenuBook icon + "Flashcards" label + mastered count badge ("N mastered" in green). Calls `onOpenVocabDrill()`. | ? |
| Daily Practice entry tile | HS-13 | card | Always | primaryContainer Card with "Daily Practice" title + "Practice all sub-lessons" subtitle + PlayArrow icon. Calls `onOpenElite()`. | ? |
| Mix Challenge entry tile | HS-14 | card | **HIDDEN** [UI-CONSISTENCY-2025] | DORMANT: tile is no longer rendered on HomeScreen. Blue-tinted Card (0xFFE3F2FD) with "Mix Challenge" title + "Interleaved practice across tenses" subtitle + SwapHoriz icon. Retained in registry for backward compat. | ? |
| Legend text | HS-15 | text | Always | Shows "Legend:" header + emoji meanings: seed, growing, bloom, wilting, wilted, forgotten. | ? |
| "How This Training Works" button | HS-16 | button | Always | OutlinedButton, full-width. Shows HowThisTrainingWorksDialog on tap. | ? |
| "Continue Learning" button | HS-17 | button | Always | Filled Button, full-width. Calls `onPrimaryAction()`. | ? |
| HowThisTrainingWorksDialog | HS-18 | dialog | `showMethod == true` | AlertDialog with title "How This Training Works", explanation text, "OK" button. | ? |
| LessonLockedDialog | HS-19 | dialog | `showLockedLessonHint == true` (tap EMPTY tile) | AlertDialog: "Lesson locked" title, "Please complete the previous lesson first.", "OK". | ? |
| EarlyStartDialog (lesson) | HS-20 | dialog | `earlyStartLessonId != null` (tap LOCKED tile with lessonId) | AlertDialog: "Start early?" title, "Yes"/"No" buttons. "Yes" calls `onSelectLesson(lessonId)`. | ? |
| Drill tiles row container | HS-21 | card | `hasVerbDrill \|\| hasVocabDrill` | Row containing VerbDrillEntryTile and VocabDrillEntryTile side by side (each weighted 1f). | ? |
| Locked tile clickable | HS-22 | button | `tile.state == LOCKED` and `tile.lessonId != null` | Opens EarlyStartDialog (HS-20). If `lessonId == null`, opens LessonLockedDialog (HS-19). | ? |

---

## 2. TrainingScreen (ui/screens/TrainingScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Scaffold TopBar title | TS-01 | text | Always | "GrammarMate" in titleLarge, Bold. | ? |
| Settings gear (top bar) | TS-02 | button | Always | IconButton with Settings icon. Calls `onOpenSettings()` + `onShowSettings()`. | ? |
| Session header label | TS-03 | text | Conditionally | "Review Session" when `bossActive`, "Refresh Session" when `eliteActive`, green-tinted text in drill mode. | ? |
| Tense label | TS-04 | text | `card.tense` is not null/blank | 13sp SemiBold, primary color (or blue Surface for Mix Challenge). In drill mode: green (0xFF388E3C). | ? |
| Prompt text (header) | TS-05 | text | `currentCard != null` | Stripped prompt (parenthetical hints removed via regex), `(18f * ruTextScale).sp`, Medium weight. Green tint in drill mode. | UC-56 |
| DrillProgressRow (progress bar) | TS-06 | progress-bar | Always | Rounded green bar (70% width, #4CAF50 on #C8E6C9 track). "N / Total" text overlay. Text color flips dark-green-to-white at 12% fill. | ? |
| Speedometer (progress arc) | TS-07 | progress-bar | Always | Canvas arc (30% width, 44dp). Color: red (<=20 wpm), yellow (<=40 wpm), green (>40 wpm). Center shows numeric wpm. | ? |
| CardPrompt card | TS-08 | card | `currentCard != null` | Material Card with "RU" label + prompt text (`(20f * ruTextScale).sp`, SemiBold) + TtsSpeakerButton. | UC-56 |
| CardPrompt TTS button | TS-09 | button | `currentCard != null` | TtsSpeakerButton: 4 states (SPEAKING=StopCircle red, INITIALIZING=spinner, ERROR=ReportProblem red, IDLE=VolumeUp). Calls `onTtsSpeak()`. | ? |
| Answer text field | TS-10 | input-field | `hasCards == true` | OutlinedTextField "Your translation". Enabled only when `hasCards`. Auto-submits on exact match in KEYBOARD mode via Normalizer.isExactMatch. | ? |
| Mic trailing icon (text field) | TS-11 | button | `canLaunchVoice` | IconButton inside trailingIcon. Switches to VOICE mode + launches speech recognition. | ? |
| "No cards" error text | TS-12 | text | `hasCards == false` | Red error text. | ? |
| Voice mode hint | TS-13 | text | `inputMode == VOICE` and `sessionState == ACTIVE` | Muted text "Say translation: {promptRu}". | ? |
| ASR status indicator | TS-14 | text | `useOfflineAsr == true` | AsrStatusIndicator showing offline ASR state. | ? |
| Word bank instruction | TS-15 | text | `inputMode == WORD_BANK` and `wordBankWords.isNotEmpty()` | "Tap words in correct order:" label text. | ? |
| Word bank chips | TS-16 | chip | `inputMode == WORD_BANK` and `wordBankWords.isNotEmpty()` | FlowRow of FilterChips. Tracks duplicate counts per word. Fully-used words disabled. Calls `onSelectWordFromBank(word)`. | ? |
| Word bank "Selected" counter | TS-17 | text | `inputMode == WORD_BANK` and `selectedWords.isNotEmpty()` | "Selected: N / M" in primary color. | ? |
| Word bank "Undo" button | TS-18 | button | `inputMode == WORD_BANK` and `selectedWords.isNotEmpty()` | TextButton "Undo". Calls `onRemoveLastWord()`. | ? |
| Voice mode selector | TS-19 | button | `canLaunchVoice` | FilledTonalIconButton with Mic icon. Switches to VOICE mode. | ? |
| Keyboard mode selector | TS-20 | button | `canSelectInputMode` | FilledTonalIconButton with Keyboard icon. Switches to KEYBOARD mode. | ? |
| Word bank mode selector | TS-21 | button | `canSelectInputMode` | FilledTonalIconButton with LibraryBooks icon. Switches to WORD_BANK mode. | ? |
| Show answer button | TS-22 | button | `hasCards` | IconButton with Visibility icon + "Show answer" tooltip. Calls `onShowAnswer()`. | ? |
| Report button | TS-23 | button | `hasCards` | IconButton with ReportProblem icon + "Report sentence" tooltip. Opens report ModalBottomSheet. | ? |
| Current mode label | TS-24 | text | Always | "Voice" / "Keyboard" / "Word Bank" label text. | ? |
| Check button | TS-25 | button | `hasCards && inputText.isNotBlank() && sessionState == ACTIVE && currentCard != null` | Full-width Button "Check". Calls `onSubmit()`. | ? |
| Result label | TS-26 | text | `lastResult != null` | "Correct" (green #2E7D32) or "Incorrect" (red #C62828), Bold. | ? |
| Result TTS replay | TS-27 | button | `lastResult != null` and `answerText` not blank | TtsSpeakerButton. Replays answer TTS. | ? |
| Answer text | TS-28 | text | `answerText` not blank | "Answer: {answerText}" text. | ? |
| Navigation Prev button | TS-29 | button | `hasCards` | NavIconButton with ArrowBack. Calls `onPrev()`. | ? |
| Navigation Pause/Play | TS-30 | button | `hasCards` | NavIconButton: Pause icon when ACTIVE, Play icon otherwise. Calls `onTogglePause()`. | ? |
| Navigation Exit button | TS-31 | button | `hasCards` | NavIconButton with StopCircle icon. Calls `onRequestExit()` (triggers exit dialog). | ? |
| Navigation Next button | TS-32 | button | `hasCards` | NavIconButton with ArrowForward. Calls `onNext(false)`. | ? |
| Report bottom sheet | TS-33 | bottom-sheet | `showReportSheet == true` | ModalBottomSheet: card prompt text + flag/unflag bad sentence + hide card + export bad sentences + copy text. | ? |
| Export result dialog | TS-34 | dialog | `exportMessage != null` | AlertDialog showing export file path or "No bad sentences to export". | ? |
| Auto-voice LaunchedEffect | TS-35 | (system) | `inputMode == VOICE && sessionState == ACTIVE && currentCard != null` | Auto-launches speech recognition 200ms after card/mode change. | ? |
| Drill mode background | TS-36 | (visual) | `isDrillMode` | Scaffold containerColor set to green (0xFFE8F5E9). | ? |
| Mix Challenge tense chip | TS-37 | card | `isMixChallenge && card.tense` not blank | Blue Surface (0xFFE3F2FD) with bold tense text (14sp, #01565C0). | ? |

---

## 3. TrainingCardSession (ui/TrainingCardSession.kt) -- Reusable Component

These elements are the default slot implementations. Screens that use TrainingCardSession with custom slots override specific elements.

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Tense label (header) | TCS-01 | text | `currentCard` is VerbDrillCard with non-blank tense | 13sp SemiBold, primary color. Always visible regardless of HintLevel. | ? |
| Clean prompt text (header) | TCS-02 | text | `currentCard != null` | Stripped prompt (parentheticals removed), `(18f * textScale).sp`, Medium weight. | UC-56 |
| Progress bar | TCS-03 | progress-bar | Always | Rounded green bar (70% width). "N / Total" overlay. Text color flips at 12% fill. | ? |
| Speedometer arc | TCS-04 | progress-bar | Always | Canvas arc (30% width, 44dp). Red/yellow/green by wpm. Center shows numeric value. | ? |
| Card content | TCS-05 | card | `currentCard != null` | Material Card: "RU" label + prompt text (`(20f * textScale).sp` SemiBold) + TtsSpeakerButton. | UC-56 |
| Card TTS button | TCS-06 | button | `currentCard != null` | TtsSpeakerButton. Calls `contract.speakTts()`. | ? |
| Answer text field | TCS-07 | input-field | `currentCard != null` and not showing result | OutlinedTextField "Your translation". Auto-submits on exact match in KEYBOARD mode. | ? |
| Mic trailing icon | TCS-08 | button | `contract.supportsVoiceInput && hasCards` | IconButton. Switches to VOICE mode + launches speech recognition. | ? |
| "No cards" error | TCS-09 | text | `currentCard == null` and not complete | Red error text "No cards". | ? |
| Voice mode hint | TCS-10 | text | `currentInputMode == VOICE` | "Say translation: {prompt}" in muted text. | ? |
| Word bank chips | TCS-11 | chip | `currentInputMode == WORD_BANK && supportsWordBank` | FlowRow of FilterChips with duplicate tracking. Fully-used words disabled. | ? |
| Word bank counter | TCS-12 | text | Word bank has selected words | "Selected: N / M" in primary color. | ? |
| Word bank Undo | TCS-13 | button | Word bank has selected words | TextButton "Undo". Calls `contract.removeLastSelectedWord()`. | ? |
| Voice mode button | TCS-14 | button | `supportsVoiceInput && hasCards` | FilledTonalIconButton Mic. Switches to VOICE mode + launches recognition. | ? |
| Keyboard mode button | TCS-15 | button | `InputMode.KEYBOARD in availableModes && hasCards` | FilledTonalIconButton Keyboard. Switches to KEYBOARD mode. ALWAYS visible regardless of HintLevel. | ? |
| Word bank mode button | TCS-16 | button | `supportsWordBank && hasCards` | FilledTonalIconButton LibraryBooks. Switches to WORD_BANK mode. | ? |
| Show answer button | TCS-17 | button | `hasCards` | IconButton Visibility + "Show answer" tooltip. Calls `contract.showAnswer()`. | ? |
| Report button | TCS-18 | button | `supportsFlagging && hasCards` | IconButton ReportProblem + "Report sentence" tooltip. Opens report sheet. | ? |
| Current mode label | TCS-19 | text | Always | "Voice" / "Keyboard" / "Word Bank" label. | ? |
| Check button | TCS-20 | button | `inputText.isNotBlank() && hasCards` | Full-width "Check" button. Calls `scope.onSubmit()`. | ? |
| Correct/Incorrect result | TCS-21 | text | `isShowingResult` | "Correct" (green) or "Incorrect" (red) Bold text + TTS replay + "Answer: {displayAnswer}". | ? |
| Report bottom sheet | TCS-22 | bottom-sheet | `showReportSheet == true` | ModalBottomSheet: card prompt + flag/unflag + hide card + export + copy text. | ? |
| Navigation row | TCS-23 | button | `supportsNavigation` | Prev + Pause/Play (if `supportsPause`) + Exit + Next NavIconButtons. | ? |
| Exit confirmation dialog | TCS-24 | dialog | Exit button tapped | "End session? Your progress will be saved." with "End"/"Cancel". | ? |
| Completion screen | TCS-25 | card | `isComplete && !isShowingResult` | Party popper emoji (48sp) + "Well done!" (24sp Bold) + progress text + "Done" button. | ? |
| Progress text overlay | TCS-26 | text | Inside progress bar | "N / Total" in 12sp Bold. Color switches at 12% fill. | ? |
| Speed value text | TCS-27 | text | Inside speedometer | Numeric wpm value in 13sp Bold, colored by speed range. | ? |
| Result TTS replay button | TCS-28 | button | `isShowingResult && supportsTts` | TtsSpeakerButton in result section. Speaks `displayAnswer`. | ? |
| Export result dialog | TCS-29 | dialog | `exportMessage != null` | AlertDialog with export path or "No bad sentences to export". | ? |

---

## 4. DailyPracticeScreen (ui/DailyPracticeScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Loading spinner | DP-01 | card | `!state.active \|\| currentTask == null` | CircularProgressIndicator + "Loading session..." text. | ? |
| Back button (header) | DP-02 | button | Always | IconButton ArrowBack. Opens exit confirmation dialog. | ? |
| "Daily Practice" title | DP-03 | text | Always | SemiBold, 18sp. | ? |
| Block type badge chip | DP-04 | card | Always | primaryContainer Card showing "Translation" / "Vocabulary" / "Verbs". | ? |
| Block progress bar | DP-05 | progress-bar | `totalTasks > 0` | LinearProgressIndicator (8dp height, rounded) + "N/M" label. Shows overall session position. | ? |
| Block sparkle overlay | DP-06 | card | `showBlockTransition == true` or session complete | Semi-transparent black overlay + sparkle emoji + "Next: {BlockType}" or "Daily practice complete!". Auto-dismisses after 800ms. | ? |
| TRANSLATE/VERBS card session | DP-07 | card | `currentTask.blockType == TRANSLATE \|\| VERBS` | Wraps TrainingCardSession via DailyPracticeSessionProvider. Includes card content, input controls, navigation. | ? |
| Card prompt (translate/verbs) | DP-08 | card | Card session active | Card with "RU" label + prompt (`(20f * ruTextScale).sp` SemiBold) + TTS button. | UC-56 |
| Verb/tense/group chips (verbs) | DP-09 | chip | `verbText` not blank | SuggestionChips for verb (with rank), tense (abbreviated), group. All chips are ALWAYS visible regardless of HintLevel -- they are reference data, not hints. Verb chip tap opens VerbReferenceBottomSheet with conjugation table. Tense chip tap opens TenseInfoBottomSheet with formula/usage/examples. Group chip is non-interactive (display only). | UC-58 |
| Card TTS button (translate/verbs) | DP-10 | button | Card session active | Inline TTS button (not TtsSpeakerButton): 4 states (SPEAKING/INITIALIZING/ERROR/IDLE). | ? |
| Hint answer card (translate/verbs) | DP-11 | card | `provider.hintAnswer != null && hintLevel == EASY` | Error-tinted Card showing "Answer: {hint}". Includes TTS replay button. | ? |
| Incorrect feedback (translate/verbs) | DP-12 | text | `provider.showIncorrectFeedback` | Red "Incorrect" + "N attempts left" text. | ? |
| Answer text field (translate/verbs) | DP-13 | input-field | Card session active | OutlinedTextField "Your translation". Auto-submits on exact match. | ? |
| Mic trailing icon (translate/verbs) | DP-14 | button | `canLaunchVoice` | Switches to VOICE mode. | ? |
| Word bank section (translate/verbs) | DP-15 | chip | `WORD_BANK mode` | DailyWordBankSection: chips + counter + Undo. | ? |
| Input mode bar (translate/verbs) | DP-16 | button | Card session active | DailyInputModeBar: Voice/Keyboard/WordBank buttons + Show answer + Report. Keyboard button is ALWAYS visible (not gated by HintLevel). | ? |
| Check button (translate/verbs) | DP-17 | button | `hasCards && inputText.isNotBlank() && sessionActive` | "Check" button. Submits via `provider.submitAnswerWithInput()`. | ? |
| Report sheet (translate/verbs) | DP-18 | bottom-sheet | `showReportSheet == true` | DailyReportSheet: flag/unflag + export + copy. | ? |
| VOCAB flashcard card | DP-19 | card | `currentTask.blockType == VOCAB` | surfaceVariant Card with prompt word (28sp Bold) + translation (18sp Medium, primary) + TTS button + report button. | ? |
| Vocab prompt text | DP-20 | text | VOCAB block active | Word text in `(28f * ruTextScale).sp` Bold, centered. Direction-dependent: IT_TO_RU shows Italian word, RU_TO_IT shows Russian meaning. | UC-56 |
| Vocab TTS button | DP-21 | button | VOCAB block active | IconButton VolumeUp. Calls `onSpeak(promptText)`. | ? |
| Vocab report button | DP-22 | button | VOCAB block active | IconButton ReportProblem. Opens DailyReportSheet. Tinted red if word is flagged. | ? |
| Vocab translation text | DP-23 | text | VOCAB block active | Translation text in `(18f * ruTextScale).sp` Medium, primary color. Always visible regardless of HintLevel. | UC-56 |
| Vocab "You said" text | DP-24 | text | `voiceRecognizedText != null` | "You said: \"{text}\"" in muted style. | ? |
| Vocab mic button | DP-25 | button | VOCAB block active | 64dp FilledTonalIconButton. Launches voice recognition with direction-appropriate language tag. | ? |
| Vocab rating buttons | DP-26 | button | VOCAB block active | 4 OutlinedButtons: Again (red), Hard (orange), Good (primary), Easy (green). Auto-advances on tap. | ? |
| Auto-voice effect (translate/verbs) | DP-27 | (system) | `inputMode == VOICE && sessionActive && currentCard != null` | LaunchedEffect triggers speech recognition after 200ms (1200ms after incorrect feedback). | ? |
| Auto-advance effect | DP-28 | (system) | `pendingAnswerResult.correct && inputMode == VOICE` | Auto-advances to next card after 400ms on correct voice answer. | ? |
| Completion screen | DP-29 | card | `state.finishedToken && !hasShownCompletionSparkle` | "Session Complete!" heading + description text + "Back to Home" button. | ? |
| Exit confirmation dialog | DP-30 | dialog | Back button tapped | "Exit practice?" + "Your progress in this session will be lost." + "Stay"/"Exit" buttons. | ? |

---

## 5. VerbDrillScreen (ui/VerbDrillScreen.kt)

### 5a. Selection Screen

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button (selection) | VD-01 | button | Always | IconButton ArrowBack. Calls `onBack()`. | ? |
| "Verb Drill" title (selection) | VD-02 | text | Always | SemiBold weight. | ? |
| Loading spinner | VD-03 | card | `state.isLoading` | CircularProgressIndicator + "Loading..." text. | ? |
| Tense dropdown | VD-04 | button | `availableTenses.isNotEmpty()` | TextButton showing selected tense or "All tenses". Opens DropdownMenu with "All" + individual tenses. Calls `onSelectTense(value)`. | ? |
| Group dropdown | VD-05 | button | `availableGroups.isNotEmpty()` | TextButton showing selected group or "All groups". Opens DropdownMenu with "All" + individual groups. Calls `onSelectGroup(value)`. | ? |
| "Sort by frequency" checkbox | VD-06 | toggle | Always | Checkbox + "Sort by frequency" text. Toggles `sortByFrequency` state. | ? |
| Progress stats | VD-07 | text | `totalCards > 0` | "Progress: X / Y" + "Today: N" (muted). | ? |
| Progress bar (selection) | VD-08 | progress-bar | `totalCards > 0` | LinearProgressIndicator showing everShownCount / totalCards. | ? |
| "All done today" text | VD-09 | text | `allDoneToday` | SemiBold 18sp centered text. | ? |
| Start/Continue button | VD-10 | button | `!allDoneToday && (totalCards > 0 \|\| tenses/groups available)` | "Start" if todayShownCount == 0, "Continue" otherwise. Calls `onStart()`. | ? |

### 5b. Active Session

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button (session) | VD-11 | button | Session active | IconButton ArrowBack. Calls `onExit()`. | ? |
| "Verb Drill" title (session) | VD-12 | text | Session active | SemiBold weight. | ? |
| Progress bar + speedometer | VD-13 | progress-bar | Session active | Reuses DefaultProgressIndicator from TrainingCardSession. | ? |
| Card prompt | VD-14 | card | `currentCard != null` | Card: "RU" label + prompt text (`(20f * ruTextScale).sp` SemiBold) + TTS VolumeUp button. | UC-56 |
| Verb SuggestionChip | VD-15 | chip | `verbText` not blank | Shows verb infinitive + "#rank". ChevronRight icon. Tap opens VerbReferenceBottomSheet. Always visible regardless of HintLevel. | UC-58 |
| Tense SuggestionChip | VD-16 | chip | `tense` not blank | Shows abbreviated tense name (e.g. "Pres."). Tap opens TenseInfoBottomSheet. Always visible regardless of HintLevel. | UC-58 |
| Hint answer card | VD-17 | card | `provider.hintAnswer != null && hintLevel == EASY` | Error-tinted Card "Answer: {hint}" + TTS replay (red-tinted). | ? |
| Incorrect feedback | VD-18 | text | `provider.showIncorrectFeedback` | Red "Incorrect" + "N attempts left" text. | ? |
| Answer text field | VD-19 | input-field | `hasCards` | OutlinedTextField "Your translation". Auto-submits on exact match. Typing clears incorrect feedback. | ? |
| Mic trailing icon | VD-20 | button | `canLaunchVoice` | Switches to VOICE mode. | ? |
| Voice mode hint | VD-21 | text | `inputMode == VOICE && sessionActive` | "Say translation: {prompt}" muted text. | ? |
| Word bank section | VD-22 | chip | `WORD_BANK mode` | VerbDrillWordBankSection: chips + counter + Undo. | ? |
| Voice mode button | VD-23 | button | `canLaunchVoice` | Sets input mode to VOICE (does NOT directly launch speech). | ? |
| Keyboard mode button | VD-24 | button | `canSelectInputMode` | Sets input mode to KEYBOARD. | ? |
| Word bank mode button | VD-25 | button | `canSelectInputMode` | Sets input mode to WORD_BANK. | ? |
| Show answer button | VD-26 | button | `hasCards && hintAnswer == null` | Visibility icon. Calls `contract.showAnswer()`. Disabled when hint already shown. Always visible regardless of HintLevel. | ? |
| Report button | VD-27 | button | `supportsFlagging && hasCards` | ReportProblem icon. Opens VerbDrillReportSheet. | ? |
| Mode label | VD-28 | text | Always | "Voice" / "Keyboard" / "Word Bank". | ? |
| Check button | VD-29 | button | `hasCards && inputText.isNotBlank() && sessionActive` | "Check". Uses `provider.submitAnswerWithInput()` for retry/hint flow. | ? |
| Auto-voice effect | VD-30 | (system) | `inputMode == VOICE && sessionActive && currentCard != null` | LaunchedEffect triggers speech recognition after 200ms. | ? |
| Auto-advance after voice correct | VD-31 | (system) | `pendingAnswerResult.correct && inputMode == VOICE` | Auto-advances after 500ms. | ? |
| Report bottom sheet | VD-32 | bottom-sheet | `showReportSheet == true` | VerbDrillReportSheet: flag/unflag + export + copy (no hide card option). | ? |
| VerbReferenceBottomSheet | VD-33 | bottom-sheet | `showVerbSheet == true` | Shows verb infinitive + TTS button + group + tense + conjugation table. | ? |
| TenseInfoBottomSheet | VD-34 | bottom-sheet | `showTenseSheet == true` | Shows tense name + formula Card + usage explanation + example cards. | ? |
| Export result dialog | VD-35 | dialog | `exportMessage != null` | AlertDialog with export path or "No bad sentences to export". | ? |
| Navigation row | VD-36 | button | Session active | DefaultNavigationControls from TrainingCardSession: Prev + Pause/Play + Exit + Next. | ? |

### 5c. Completion Screen

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Sparkle emoji | VD-37 | text | Session complete | 48sp party popper emoji. | ? |
| "Otlichno!" title | VD-38 | text | Session complete | Bold 24sp. | ? |
| Stats text | VD-39 | text | Session complete | "Pravilnykh: X \| Oshibok: Y" in muted color. | ? |
| "More" button | VD-40 | button | `!allDoneToday` | "Eshche" Button. Calls `viewModel.nextBatch()` to load 10 more cards. | ? |
| "Exit" button | VD-41 | button | Session complete | OutlinedButton "Vykhod". Calls `onExit()`. | ? |

---

## 6. VocabDrillScreen (ui/VocabDrillScreen.kt)

### 6a. Selection Screen

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button (selection) | VOC-01 | button | Always | IconButton ArrowBack. Calls `onBack()`. | ? |
| "Flashcards" title | VOC-02 | text | Always | SemiBold weight. | ? |
| Loading spinner | VOC-03 | card | `state.isLoading` | CircularProgressIndicator + "Loading..." text. | ? |
| "Direction" label | VOC-04 | text | Always | labelMedium "Direction". | ? |
| Direction filter "IT -> RU" | VOC-05 | chip | Always | FilterChip. Selected when `drillDirection == IT_TO_RU`. | ? |
| Direction filter "RU -> IT" | VOC-06 | chip | Always | FilterChip. Selected when `drillDirection == RU_TO_IT`. | ? |
| ~~Voice input toggle~~ | VOC-07 | (removed) | N/A | REMOVED. Voice auto-start is now controlled by the global Settings toggle (`AudioState.voiceAutoStart`). No per-drill toggle exists. | UC-57 |
| "Part of speech" label | VOC-08 | text | Always | labelMedium "Part of speech". | ? |
| POS "All" chip | VOC-09 | chip | Always | FilterChip. Selected when `selectedPos == null`. | ? |
| POS chips (per category) | VOC-10 | chip | Always (one per `availablePos`) | FilterChips: Nouns, Verbs, Adj., Adv., Numbers, etc. Selected when matching `selectedPos`. | ? |
| "Word frequency" label | VOC-11 | text | Always | labelMedium "Word frequency". | ? |
| Frequency chips | VOC-12 | chip | Always | FilterChips: Top 100, Top 500, Top 1000, All. Each maps to a rank range. | ? |
| Stats card | VOC-13 | card | `totalCount > 0` | primaryContainer Card: "Due: X / Y" + "Mastered: N words" + per-POS breakdown + progress bar. | ? |
| "No words loaded" text | VOC-14 | text | `totalCount == 0` | Muted centered text. | ? |
| Start button | VOC-15 | button | `dueCount > 0` | "Start (N due)". Disabled when no due words. | ? |

### 6b. Card Screen

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button (card) | VOC-16 | button | Session active | IconButton ArrowBack. Calls `onExit()`. | ? |
| "Flashcards" title (card) | VOC-17 | text | Session active | SemiBold weight. | ? |
| Card progress counter | VOC-18 | text | Session active | "N/M" in labelLarge, primary color. | ? |
| Report button (card header) | VOC-19 | button | Session active | IconButton ReportProblem. Tinted red if word is flagged. Opens report sheet. | ? |
| Card progress bar | VOC-20 | progress-bar | Session active | LinearProgressIndicator showing current/total. | ? |
| Card front container | VOC-21 | card | `!session.isFlipped` | RoundedCornerShape(16dp) Card. Background tint changes: green on correct voice, red on wrong, surfaceVariant default. | ? |
| POS badge | VOC-22 | card | Always (when card has POS) | Small rounded Card showing "noun"/"verb"/"adj." etc. Color-coded by POS. Always visible regardless of HintLevel. | ? |
| Rank badge | VOC-23 | card | Always (when card has rank) | Small rounded Card showing "#N" rank. Always visible regardless of HintLevel. | ? |
| Word text (front) | VOC-24 | text | `!session.isFlipped` | `(32f * ruTextScale).sp` Bold centered. Direction-dependent: IT_TO_RU shows Italian, RU_TO_IT shows Russian meaning. | UC-56 |
| TTS button (front) | VOC-25 | button | `direction == IT_TO_RU` | 4-state icon (SPEAKING/INITIALIZING/ERROR/IDLE). Calls `onSpeak(word)`. | ? |
| "Tap to speak" text | VOC-26 | text | `!voiceCompleted` | labelMedium muted text. | ? |
| Mic button (front, 72dp) | VOC-27 | button | `!voiceCompleted` | 72dp FilledTonalIconButton with 36dp Mic icon. Launches voice recognition. | ? |
| Voice result feedback card | VOC-28 | card | `voiceCompleted` | Shows recognized text (italic) + result: "Correct!" (green check), "Moving on..." (red, after max attempts), "Try again (N/3)" (red), "Skipped" (muted). | ? |
| Card back container | VOC-29 | card | `session.isFlipped` | secondaryContainer Card with POS badge + word + meaning + forms + collocations + mastery step. | ? |
| Word + TTS (back) | VOC-30 | button | `session.isFlipped` | Shows word text + TTS VolumeUp button. Layout differs by direction. | ? |
| Translation text (back) | VOC-31 | text | `session.isFlipped` | Direction-dependent: shows the "answer" side of the card. | ? |
| Forms table | VOC-32 | card | `session.isFlipped && forms.isNotEmpty()` | tertiaryContainer Card: "Forms" label + form items (m sg, f sg, m pl, f pl). POS-dependent form keys. Always visible regardless of HintLevel. | ? |
| Collocations list | VOC-33 | text | `session.isFlipped && collocations.isNotEmpty()` | Max 5 collocations shown. "+N more" if overflow. Always visible regardless of HintLevel. | ? |
| Mastery step indicator | VOC-34 | text | `session.isFlipped` | "Step X/9" or "Learned" (when step >= 3). Muted labelSmall. | ? |
| Skip button | VOC-35 | button | `!session.isFlipped` | OutlinedButton with SkipNext icon. Skips voice or flips card. | ? |
| Flip button | VOC-36 | button | `!session.isFlipped` | Filled Button with Flip icon. Calls `onFlip()`. | ? |
| "Again" rating button | VOC-37 | button | `session.isFlipped` | Error-colored OutlinedButton. Shows "Again" + "<1m" interval. Resets to step 0. | ? |
| "Hard" rating button | VOC-38 | button | `session.isFlipped` | Orange-colored OutlinedButton. Shows "Hard" + current step interval. Stays at same step. | ? |
| "Good" rating button | VOC-39 | button | `session.isFlipped` | Primary-colored Filled Button. Shows "Good" + next step interval. Advances +1 step. | ? |
| "Easy" rating button | VOC-40 | button | `session.isFlipped` | Green-colored Filled Button. Shows "Easy" + +2 step interval. Advances +2 steps. | ? |
| Report bottom sheet | VOC-41 | bottom-sheet | `showReportSheet == true` | ModalBottomSheet: "Word options" title + word text + flag/unflag + export + copy. | ? |
| Export result dialog | VOC-42 | dialog | `exportMessage != null` | AlertDialog with export result. | ? |
| Auto-flip on voice correct | VOC-43 | (system) | `voiceCompleted && voiceResult == CORRECT && !isFlipped` | Auto-flips card after 800ms delay. | ? |
| Auto-launch voice | VOC-44 | (system) | `voiceAutoStart (global) && !isFlipped && !voiceCompleted && !isVoiceActive` | Auto-launches voice recognition after 500ms delay. Uses global `voiceAutoStart` from Settings, NOT per-drill toggle. Mic button still works on manual click when voiceAutoStart is OFF. | UC-57 |

### 6c. Completion Screen

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| "Perfect!" / "Done!" title | VOC-45 | text | Session complete | Bold 28sp, primary color. "Perfect!" when all correct, "Done!" otherwise. | ? |
| Stats card | VOC-46 | card | `showStats == true` (800ms fade-in) | Correct count (primary, 32sp Bold) + Wrong count (error, 32sp Bold) + "N words reviewed". | ? |
| Exit button (completion) | VOC-47 | button | `showStats == true` | OutlinedButton "Exit". Returns to selection. | ? |
| Continue button (completion) | VOC-48 | button | `showStats == true` | Filled Button "Continue". Starts new session. | ? |

---

## 7. LessonRoadmapScreen (ui/screens/LessonRoadmapScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button | LR-01 | button | Always | IconButton ArrowBack. Calls `onBack()`. | ? |
| Lesson title | LR-02 | text | Always | SemiBold weight. Shows selected lesson title or "Lesson" fallback. | ? |
| Progress bar | LR-03 | progress-bar | Always | LinearProgressIndicator showing completed/total. | ? |
| "Exercise X of Y" text | LR-04 | text | Always | Centered text showing current cycle position. Paginated in blocks of 15. | ? |
| "Cards: X of Y" text | LR-05 | text | Always | 12sp, 70% alpha. Shows shownCards/totalCards for current lesson. | ? |
| Sub-lesson grid | LR-06 | card | Always | 4-column LazyVerticalGrid with entries from `buildRoadmapEntries()`. userScrollEnabled=false. | ? |
| Training tile (exercise) | LR-07 | card | Per entry | Card (72dp) showing: index number, flower emoji (LOCKED/UNLOCKED/completed flower), type label ("NEW"/"MIX"). Clickable when `canEnter`. | ? |
| Drill tile | LR-08 | card | `hasDrill == true` | Card with FitnessCenter icon + "Drill" label (12sp). primaryContainer when enabled. Calls `onDrillStart()`. | ? |
| Boss "Review" tile | LR-09 | card | Always (per cycle) | Card showing "Review" label + trophy icon (colored by reward) or lock icon. Clickable when `bossUnlocked`. | ? |
| Boss "Mega" tile | LR-10 | card | `lessonIndex > 0` (per cycle) | Card showing "Mega" label + trophy icon (colored by reward) or lock icon. Clickable when `bossUnlocked`. | ? |
| "Start Lesson" / "Continue Lesson" button | LR-11 | button | Always | Full-width Button. "Start Lesson" when completed==0, "Continue Lesson" otherwise. Calls `onStartSubLesson(currentIndex)`. | ? |
| Early start dialog (sub-lesson) | LR-12 | dialog | `earlyStartSubLessonIndex != null` | "Start exercise N early?" with "Yes"/"No". | ? |
| Boss locked dialog | LR-13 | dialog | `bossLockedMessage != null` | "Locked" title + "Complete at least 15 exercises first." + "OK". | ? |

---

## 8. LadderScreen (ui/screens/LadderScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button | LS-01 | button | Always | IconButton ArrowBack. Calls `onBack()`. Returns to caller screen. | ? |
| Title "Lestnitsa intervalov" | LS-02 | text | Always | titleLarge SemiBold. | ? |
| Subtitle "Vse uroki tekushchego paketa" | LS-03 | text | Always | bodySmall, 65% alpha. | ? |
| Empty state text | LS-04 | text | `ladderRows.isEmpty()` | "Net dannykh po urokam" muted text. Early return. | ? |
| Header row | LS-05 | text | `ladderRows.isNotEmpty()` | Column headers: #, Urok, Karty, Dney, Interval in labelMedium. | ? |
| Ladder row card | LS-06 | card | Per row in `ladderRows` | RoundedCornerShape(14dp) Card. Shows: index, title (ellipsized), uniqueCardShows, daysSinceLastShow, intervalLabel. Overdue rows use errorContainer background. | ? |
| Row index text | LS-07 | text | Per row | titleSmall weight. | ? |
| Row title text | LS-08 | text | Per row | bodyMedium, single line, ellipsized. | ? |
| Row cards count | LS-09 | text | Per row | "-" if null, otherwise numeric. | ? |
| Row days count | LS-10 | text | Per row | "-" if null, otherwise numeric. | ? |
| Row interval label | LS-11 | text | Per row | "-" if null. Overdue starts with "Prosrochka". | ? |

---

## 9. SettingsScreen / SettingsSheet (ui/screens/SettingsScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| ModalBottomSheet container | SS-01 | bottom-sheet | `show == true` | ModalBottomSheet with dismiss callback. On dismiss from TRAINING: calls `resumeFromSettings()` if card is active. | ? |
| "Service Mode" section header | SS-02 | text | Always | titleMedium SemiBold. | ? |
| Test Mode switch | SS-03 | toggle | Always | Switch. Toggles test mode. | ? |
| Test Mode description | SS-04 | text | Always | "Enables all lessons, accepts all answers, unlocks Elite mode" in bodySmall, 60% alpha. | ? |
| "Show Ladder" button | SS-05 | button | Always | OutlinedButton with Insights icon. Closes sheet + navigates to LADDER. | ? |
| "Difficulty" section header | SS-06 | text | Always | titleMedium SemiBold. | ? |
| Hint level chips (Easy/Medium/Hard) | SS-07 | chip | Always | 3 FilterChips. EASY: "All hints visible", MEDIUM: "Partial hints", HARD: "No hints". Each weighted 1f. | ? |
| Hint level description | SS-08 | text | Always | Dynamic description based on current hintLevel. bodySmall, 60% alpha. | ? |
| Vocab Sprint limit field | SS-09 | input-field | Always | OutlinedTextField "Vocabulary Sprint limit". Digits only. "0 = all words". | ? |
| Vocab limit description | SS-10 | text | Always | "Set how many words to show (0 = all words)" bodySmall, 60% alpha. | ? |
| "Pronunciation speed" header | SS-11 | text | Always | titleMedium SemiBold. | ? |
| TTS speed slider | SS-12 | toggle | Always | Slider 0.5x-1.5x, 3 steps. Calls `onSetTtsSpeed`. | ? |
| TTS speed value display | SS-13 | text | Always | "0.XXx" formatted, centered. | ? |
| "Voice recognition" header | SS-14 | text | Always | titleMedium SemiBold. | ? |
| Offline ASR switch | SS-15 | toggle | Always | Switch. When enabled without model, triggers ASR download. | ? |
| ASR download progress bar | SS-16 | progress-bar | `asrDownloadState == Downloading \|\| Extracting` | LinearProgressIndicator + percentage text. | ? |
| ASR download error text | SS-17 | text | `asrDownloadState == Error` | Error message in error color. | ? |
| ASR status text | SS-18 | text | `asrDownloadState == Idle/Ready/Done` | "Using on-device recognition" / "Model not downloaded" / "Using Google speech recognition". | ? |
| "Translation text size" header | SS-19 | text | Always | titleMedium SemiBold. | ? |
| Text scale slider | SS-20 | toggle | Always | Slider 1.0x-2.0x, 3 steps. Calls `onSetRuTextScale`. | ? |
| Text scale value display | SS-21 | text | Always | "0.0x" formatted, centered. | ? |
| Language dropdown | SS-22 | button | Always | DropdownSelector "Language". Shows current language. Changing reloads lessons and resets active pack. | ? |
| Pack dropdown | SS-23 | button | Always | DropdownSelector "Pack". Shows current pack. Filters packs by selected language. | ? |
| "New language" text field | SS-24 | input-field | Always | OutlinedTextField "New language". | ? |
| "Add language" button | SS-25 | button | Always | OutlinedButton. Adds language if non-empty. Clears field. | ? |
| "Import lesson pack (ZIP)" button | SS-26 | button | Always | OutlinedButton with Upload icon. Opens file picker for ZIP files. | ? |
| "Import lesson (CSV)" button | SS-27 | button | Always | OutlinedButton with Upload icon. Opens file picker for CSV files. | ? |
| "Reset/Reload" button | SS-28 | button | Always | OutlinedButton with Upload icon. Opens file picker, clears + imports. | ? |
| "Empty lesson" text field | SS-29 | input-field | Always | OutlinedTextField "Empty lesson (title)". | ? |
| "Create empty lesson" button | SS-30 | button | Always | OutlinedButton. Creates lesson if title non-empty. Clears field. | ? |
| "Delete all lessons" button | SS-31 | button | Always | Red OutlinedButton with Delete icon. Calls `onDeleteAllLessons()`. | ? |
| "Reset progress" button | SS-32 | button | Always | Red OutlinedButton with Refresh icon. Label shows current language name: "Сбросить прогресс ({languageName})". Tapping opens confirmation dialog. | UC-62 |
| Reset progress confirmation dialog | SS-32a | dialog | User tapped SS-32 | AlertDialog with title "Сбросить прогресс", body listing what will be cleared, "Сбросить" confirm button (red), "Отмена" dismiss button. Calls `onResetAllProgress()` on confirm. | UC-62 |
| CSV format text | SS-33 | text | Always | "CSV format" header + format explanation (UTF-8, semicolon delimiter, example). | ? |
| Instructions text | SS-34 | text | Always | "Instructions" header + usage instructions. | ? |
| "Packs" header | SS-35 | text | Always | labelLarge "Packs". | ? |
| Pack list row | SS-36 | card | Per installed pack | Row: pack name (weighted) + Delete IconButton. | ? |
| "No installed packs" text | SS-37 | text | `installedPacks.isEmpty()` | bodySmall placeholder. | ? |
| "Profile" header | SS-38 | text | Always | titleMedium SemiBold. | ? |
| User name field | SS-39 | input-field | Always | OutlinedTextField "Your Name", 50 char limit, single line. | ? |
| "Save Name" button | SS-40 | button | `trimmed name != current name && trimmed.isNotEmpty()` | Filled Button. Calls `onUpdateUserName()`. | ? |
| "Backup & Restore" header | SS-41 | text | Always | titleMedium SemiBold. | ? |
| Restore description | SS-42 | text | Always | "Restore progress from backup folder (Downloads/BaseGrammy)". | ? |
| "Save progress now" button | SS-43 | button | Always | OutlinedButton with Upload icon. Calls `onSaveProgress()`. | ? |
| "Restore from backup" button | SS-44 | button | Always | OutlinedButton with Download icon. Opens folder picker. | ? |
| App info footer | SS-45 | text | Always | "GrammarMate" + version + tagline + description. Centered. | ? |

---

## 10. StoryQuizScreen (ui/screens/StoryQuizScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Phase title | SQ-01 | text | `story != null` | "Story Check-in" or "Story Check-out" (SemiBold). | ? |
| Story text | SQ-02 | text | `story != null` | bodyMedium. Shows `story.text`. | ? |
| Question counter | SQ-03 | text | `story != null` | "Question X / Y". | ? |
| Question prompt | SQ-04 | text | `story != null` | SemiBold. Shows `question.prompt`. | ? |
| Option row | SQ-05 | button | Per option | Clickable Row with ">" indicator when selected. Shows option text + "(correct)" / "(your choice)" suffixes when result shown. | ? |
| Correct answer text | SQ-06 | text | `showResult` | Primary color. "Correct: {optionText}". | ? |
| Error message | SQ-07 | text | `errorMessage != null` | Red text. "Select an answer" or "Incorrect". | ? |
| Prev button | SQ-08 | button | `questionIndex > 0` | OutlinedButton "Prev". Goes to previous question. | ? |
| Check button | SQ-09 | button | Always | Filled Button "Check". Validates selection. Shows error if none selected. Records correct/incorrect. | ? |
| Next/Finish button | SQ-10 | button | Always | Filled Button. Shows "Finish" on last question, "Next" otherwise. Validates first. On last: calls `onComplete()`. | ? |
| "Scroll to continue" hint | SQ-11 | text | `scrollState.maxValue > 0` | bodySmall muted centered text. | ? |
| Auto-close (null story) | SQ-12 | (system) | `story == null` | Immediately calls `onClose()`. | ? |
| Auto-complete (empty questions) | SQ-13 | (system) | `story.questions.isEmpty()` | Immediately calls `onComplete(true)`. | ? |

---

## 11. GrammarMateApp Dialogs (ui/GrammarMateApp.kt + ui/components/)

These dialogs are rendered as persistent overlays and can appear on any screen.

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| TTS background progress bar | DG-01 | progress-bar | `bgTtsDownloading == true` | 2dp LinearProgressIndicator at top of all content. Aggregates progress from all language downloads. Persists across screen changes. | ? |
| WelcomeDialog | DG-02 | dialog | `userName == "GrammarMateUser"` (first launch) | "Welcome to GrammarMate!" + "What's your name?" + OutlinedTextField (50 char, single line, Done IME) + "Skip" + "Continue". Cannot dismiss by tapping outside. Blank input treated as "GrammarMateUser". | ? |
| StreakDialog | DG-03 | dialog | `streakMessage != null` | "??" icon text (48sp) + "Streak!" title + streakMessage (titleMedium, centered) + "Longest streak: N days" (only when longestStreak > currentStreak) + "Continue" button. | ? |
| BossRewardDialog | DG-04 | dialog | `bossRewardMessage != null && bossReward != null` | Trophy icon (EmojiEvents) colored by reward type (bronze #CD7F32, silver #C0C0C0, gold #FFD700) + "Boss Reward" title + rewardMessage + "OK" button. | ? |
| BossErrorDialog | DG-05 | dialog | `bossErrorMessage != null` | "Boss" title + error message text + "OK" button. | ? |
| StoryErrorDialog | DG-06 | dialog | `storyErrorMessage != null` | "Story" title + error message text + "OK" button. | ? |
| DrillStartDialog | DG-07 | dialog | `drillShowStartDialog == true` (tap DrillTile) | "Drill Mode" title + resume/fresh message. If progress exists: "Continue" (resume) + "Start Fresh". If no progress: "Start" only. Always has "Cancel". | ? |
| ExitConfirmationDialog | DG-08 | dialog | Back gesture or Stop during TRAINING/DAILY_PRACTICE | Title "End session?" / "Exit practice?". DAILY_PRACTICE: calls `cancelDailySession()`, navigates HOME. Boss: calls `finishBoss()`, navigates LESSON. Drill: calls `exitDrillMode()`, navigates LESSON. Normal: calls `finishSession()`, navigates LESSON. | ? |
| TtsDownloadDialog | DG-09 | dialog | `showTtsDownloadDialog == true` (TTS tap without model) | "Download pronunciation model?" title. Dynamic content: Idle shows size, Downloading/Extracting shows progress bar + %, Done shows "ready!", Error shows failure. "Download"/"OK"/"Cancel" buttons. Auto-closes on completion and auto-plays TTS. | ? |
| MeteredNetworkDialog (TTS) | DG-10 | dialog | `ttsMeteredNetwork == true` (TTS download on metered connection) | "Metered network detected" title + "~346 MB" warning + "Download anyway" + "Cancel". | ? |
| AsrMeteredNetworkDialog | DG-11 | dialog | `asrMeteredNetwork == true` (ASR download on metered connection) | "Metered network detected" title + "~375 MB" warning + "Download anyway" + "Cancel". | ? |
| DailyResumeDialog | DG-12 | dialog | Tap Daily Practice with resumable session | "Ezhednevnaya praktika" title + "Repeat = same cards, Continue = new cards" + "Repeat" (dismiss) + "Continue" (confirm). | ? |
| DailyPracticeLoadingOverlay | DG-13 | dialog | `isLoadingDaily == true` | Card with CircularProgressIndicator + "Loading session..." text. Blocking, auto-dismissed when initialization completes. | ? |
| HowThisTrainingWorksDialog | DG-14 | dialog | Tap "How This Training Works" on HomeScreen | Title + "GrammarMate builds automatic grammar patterns with repeated retrieval..." + "OK". | ? |
| LessonLockedDialog | DG-15 | dialog | Tap EMPTY lesson tile on HomeScreen | "Lesson locked" + "Please complete the previous lesson first." + "OK". | ? |
| EarlyStartDialog (Home/Lesson) | DG-16 | dialog | Tap locked lesson or sub-lesson tile | "Start early?" + "Start this lesson/exercise early? You can always come back..." + "Yes" + "No". | ? |
| ExportBadSentencesResultDialog | DG-17 | dialog | After exporting bad sentences from report sheet | "Export" title + file path or "No bad sentences to export" + "OK". | ? |

---

## 12. [UI-CONSISTENCY-2025] Shared Components (ui/components/)

These shared composables enforce cross-screen UI consistency. Each is used by 2+ screens.

| Element | ID | Type | Used by | Behavior / Invariant | Related UC |
|---------|----|------|---------|----------------------|------------|
| SharedReportSheet | SH-01 | bottom-sheet | TrainingScreen, VerbDrillScreen, DailyPracticeScreen | ModalBottomSheet with exactly 4 options: Flag/Unflag, Hide card, Export bad sentences, Copy text. Card prompt text shown at top for context. | UC-53 |

**Behavior:** Each option triggers its corresponding callback. Flag toggles card.isFlagged (adds/removes from BadSentenceStore). Hide removes card from session (except Daily Practice where it is a documented no-op). Export returns formatted string via BadSentenceStore.exportUnified() -- non-null when at least one card is flagged. Copy writes card text (ID, source, target) to system clipboard.

| VoiceAutoLauncher | SH-02 | (system) | VerbDrillScreen, VocabDrillScreen | LaunchedEffect composable that auto-launches voice recognition after configurable delay (200ms for new card, 1200ms after incorrect feedback). | UC-52 |

**Behavior:** When enabled and card changes, fires onAutoStartVoice after delay. Callback MUST call speechLauncher.launch(intent) directly -- switching InputMode alone is insufficient and causes the voice-not-launching bug. On correct voice answer, auto-advance triggers after 400-500ms.

| SharedInputModeBar | SH-03 | button | TrainingScreen, VerbDrillScreen, DailyPracticeScreen | Row of FilledTonalIconButtons: Mic, Keyboard, WordBank + Eye (show answer) + Report. Active mode highlighted. Mode label displayed below. | UC-51, UC-53 |

**Behavior:** Mode buttons switch input method via onModeChange callback. Eye button calls onShowHint() which sets hintAnswer on the provider and pauses the session. Report button opens SharedReportSheet. Eye button is disabled when hintShown == true.

| HintAnswerCard | SH-04 | card | TrainingScreen, VerbDrillScreen, DailyPracticeScreen | Pink Card with `errorContainer.copy(alpha = 0.3f)` background, red error-colored answer text, inline TTS replay button. Reference: VerbDrillScreen.kt:392-425. | UC-51 |

**Behavior:** Renders whenever hintAnswer != null. NOT gated by HintLevel -- eye mode shows answer at ALL difficulty levels (EASY, MEDIUM, HARD). HintLevel only controls parenthetical hints in prompt text, not the show-answer mechanism.

| TextScaleProvider | SH-05 | (system) | TrainingScreen, VerbDrillScreen, VocabDrillScreen, DailyPracticeScreen, TrainingCardSession | N/A (no visual element -- scales existing text). Multiplies base font sizes by textScale value (1.0-2.0). Applied to: prompt text, word displays, answer text. NOT applied to: navigation, badges, buttons, small labels. | UC-56 |

**Behavior:** Propagated via `CardSessionContract.textScale` or composable parameter. Reads `ruTextScale` from `AudioState` / `AppConfigStore`. Applied as `fontSize = (baseSize * textScale).sp`. Elements excluded from scaling: RU badge, POS badge, hint chips, attempt counter, tense labels, navigation buttons, progress bar text, rating button text, block label badges.

| VoiceAutoStartToggle | SH-06 | switch | SettingsScreen | Switch with label "Auto-start voice input". When ON, shows description "Voice recognition starts automatically when a new card appears". When OFF, shows "Voice recognition starts only when you tap the microphone". Bound to `state.audio.voiceAutoStart` via `onSetVoiceAutoStart` callback. | UC-57 |
