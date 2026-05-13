# Code → Spec Trace Index

Maps code symbols (composable functions, handlers, LaunchedEffects) to specification sections, Use Case IDs, and Acceptance Criteria numbers. Reference format: `08#7.6` = file `08-training-viewmodel.md` section 7.6; `UC-57` from `22-use-case-registry.md`.

## Pilot: TrainingScreen.kt

**File:** `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt` (866 lines)

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `TrainingScreen` | 96 | Composable | 08#2.1, 12#12.5 | UC-01 | AC1–AC5 |
| `HeaderStats` | 264 | Composable | 08#2.3 | UC-05 | AC1 |
| `ModeSelector` | 313 | Composable | 08#2.1 | UC-01 | AC3 |
| `CardPrompt` | 388 | Composable | 12#12.4.5, 08#2.1 | UC-56 | AC3 |
| `AnswerBox` | 415 | Composable | 08#2.2, 12#12.4.6 | UC-12, UC-13, UC-14, UC-15, UC-50 | AC1–AC4, AC1–AC4, AC1–AC5, AC1–AC4, AC1–AC4 |
| `speechLauncher` (voice result handler) | 445 | Handler | 08#7.6, 12#12.7.1 | UC-12 | AC2–AC4 |
| `LaunchedEffect(voiceAutoStart)` | 458 | LaunchedEffect | 08#7.6, 12#12.7.1 | UC-57 | AC2, AC4 |
| `SharedReportSheet` | 478 | Composable | 12#12.8.2 | UC-53 | AC1–AC5 |
| `OutlinedTextField` + auto-submit `isExactMatch` | 516 | Handler | 08#2.2 | UC-13 | AC1–AC4 |
| `FlowRow` (word bank) | 573 | Composable | 08#2.2, 12#12.4.6 | UC-14 | AC1–AC5 |
| `onRemoveLastWord` (Undo) | 610 | Handler | 08#2.2 | UC-14 | AC4 |
| Input mode buttons (Mic/Keyboard/Book) | 622 | Composable | 08#2.4 | UC-15 | AC1–AC4 |
| Show answer (eye) `IconButton` | 662 | Handler | 08#2.2, 12#12.7.2 | UC-50 | AC1–AC4 |
| Report (triangle) `IconButton` | 674 | Handler | 12#12.8.2 | UC-53 | AC6–AC9 |
| `ResultBlock` | 704 | Composable | 08#2.2, 12#12.4.7 | UC-02, UC-03 | AC5, AC2 |
| `HintAnswerCard` in ResultBlock | 714 | Composable | 12#12.8.1 | UC-51 | AC1–AC7 |
| `NavigationRow` | 724 | Composable | 08#2.3, 12#12.4.9 | UC-06 | AC1–AC4 |
| `DrillProgressRow` | 759 | Composable | 08#2.3, 12#12.4.8 | UC-05 | AC1 |
| `launchVoiceRecognition` | 850 | Helper fn | 08#7.6 | UC-12 | AC1 |
| Prompt `fontSize = (18f * state.audio.ruTextScale).sp` | 179, 224 | Scaling | 14#13 | UC-56 | AC3 |

## Pilot: VerbDrillScreen.kt

**File:** `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt` (942 lines)

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `VerbDrillScreen` | 81 | Composable | 10#10.6.1 | UC-26 | AC1–AC2 |
| `VerbDrillSessionWithCardSession` | 132 | Composable | 10#10.6.2, 12#12.5 | UC-26 | AC4–AC5 |
| `autoSpeechLauncher` (voice result) | 153 | Handler | 10#10.6.3 | UC-27 | AC3 |
| `onAutoStartVoice` helper | 166 | Helper fn | 10#10.6.3 | UC-59 | AC1–AC5 |
| `VoiceAutoLauncher` | 184 | Composable | 10#10.9.1, 12#12.8.3 | UC-59 | AC1–AC6 |
| `LaunchedEffect(auto-advance after correct voice)` | 197 | LaunchedEffect | 10#10.6.3 | UC-27 | AC4 |
| `cardContent` slot (prompt + verb/tense chips) | 225 | Slot lambda | 10#10.6.2 | UC-58 | AC1, AC4 |
| `SuggestionChip` verb (opens VerbReferenceBottomSheet) | 259 | Handler | 10#10.9.2 | UC-58 | AC2, AC4 |
| `SuggestionChip` tense (opens TenseInfoBottomSheet) | 282 | Handler | 10#10.9.2 | UC-58 | AC3, AC5 |
| `DefaultVerbDrillInputControls` | 351 | Composable | 10#10.6.3, 12#12.4.6 | UC-27, UC-49 | AC1–AC6, AC1–AC6 |
| `LaunchedEffect(auto-voice)` in DefaultVerbDrillInputControls | 392 | LaunchedEffect | 10#10.9.1 | UC-59 | AC1–AC5 |
| `SharedReportSheet` in DefaultVerbDrillInputControls | 419 | Composable | 12#12.8.2 | UC-53 | AC1–AC5 |
| `HintAnswerCard` in DefaultVerbDrillInputControls | 454 | Composable | 12#12.8.1 | UC-51 | AC1–AC7 |
| Incorrect feedback with remaining attempts | 462 | Composable | 10#10.6.3 | UC-49 | AC2, AC5 |
| `OutlinedTextField` + auto-submit `isExactMatch` | 482 | Handler | 10#10.6.3 | UC-27 | AC1–AC2 |
| `VerbDrillWordBankSection` | 547 | Composable | 10#10.6.3 | UC-14 | AC1, AC4 |
| `VerbDrillInputModeBar` | 552 | Composable | 10#10.6.3 | UC-57 | AC6 |
| Check button (`submitAnswerWithInput`) | 576 | Handler | 10#10.6.3 | UC-27 | AC1–AC5 |
| `VerbDrillSelectionScreen` | 596 | Composable | 10#10.6.1 | UC-26 | AC1, AC4–AC5 |
| `VerbDrillDropdown` tense | 622 | Composable | 10#10.6.1 | UC-26 | AC1 |
| `VerbDrillDropdown` group | 632 | Composable | 10#10.6.1 | UC-26 | AC1 |
| Frequency checkbox | 647 | Composable | 10#10.6.1 | UC-26 | AC5 |
| Progress display (everShown/today) | 657 | Composable | 10#10.6.1 | UC-26 | AC4 |
| Start/Continue button | 687 | Handler | 10#10.6.1 | UC-26 | AC4 |
| `VerbDrillCompletionScreen` | 732 | Composable | 10#10.6.4 | UC-28 | AC1–AC4 |
| "More" button (`nextBatch`) | 764 | Handler | 10#10.6.4 | UC-26 | AC4 |
| `VerbDrillWordBankSection` (extracted) | 784 | Composable | 10#10.6.3 | UC-14 | AC1, AC4 |
| `VerbDrillInputModeBar` (extracted) | 840 | Composable | 10#10.9.1 | UC-57 | AC6 |
| Show answer button in VerbDrillInputModeBar | 894 | Handler | 10#10.6.3 | UC-50 | AC3 |
| `VerbReferenceBottomSheet` invocation | 320 | Composable | 10#10.9.2 | UC-58 | AC4 |
| `TenseInfoBottomSheet` invocation | 334 | Composable | 10#10.9.2 | UC-58 | AC5 |

## Pilot: DailyPracticeScreen.kt

**File:** `app/src/main/java/com/alexpo/grammermate/ui/DailyPracticeScreen.kt` (878 lines)

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `DailyPracticeScreen` | 83 | Composable | 09#9.1, 09#9.6 | UC-21 | AC1–AC4 |
| `BlockSparkleOverlay` (completion) | 117 | Composable | 09#9.6.4 | UC-22 | AC1–AC2 |
| `DailyPracticeCompletionScreen` | 123 | Composable | 09#9.6.4 | UC-22 | AC1–AC4 |
| `BlockProgressBar` | 145 | Composable | 09#9.7.2 | UC-24 | AC1 |
| `LaunchedEffect(blockType change)` | 153 | LaunchedEffect | 09#9.6.3 | UC-22 | AC1–AC2 |
| `BlockSparkleOverlay` (block transition) | 161 | Composable | 09#9.6.3 | UC-22 | AC1–AC2 |
| `CardSessionBlock` | 211 | Composable | 09#9.4, 12#12.5 | UC-22 | AC1–AC4 |
| `DailyPracticeSessionProvider` creation | 244 | Factory | 09#9.4, 09#9.8 | UC-22 | AC4 |
| `DailyTrainingCardSession` | 280, 293 | Composable | 09#9.5, 12#12.5 | UC-22 | AC4 |
| `speechLauncher` (voice result) | 306 | Handler | 09#9.5 | UC-12 | AC2–AC4 |
| `LaunchedEffect(auto-voice)` | 333 | LaunchedEffect | 09#9.5, 12#12.7.1 | UC-57 | AC3, AC5 |
| `LaunchedEffect(auto-advance after correct voice)` | 346 | LaunchedEffect | 09#9.5 | UC-27 | AC4 |
| Verb chip `SuggestionChip` | 391 | Handler | 09#9.7.4 | UC-58 | AC2 |
| Tense chip `SuggestionChip` | 401 | Handler | 09#9.7.4 | UC-58 | AC3 |
| Group chip `SuggestionChip` | 409 | Composable | 09#9.7.4 | — | — |
| `DailyInputControls` | 467 | Composable | 09#9.5, 12#12.4.6 | UC-16, UC-49 | AC1–AC4, AC1–AC6 |
| `HintAnswerCard` in DailyInputControls | 494 | Composable | 12#12.8.1 | UC-51 | AC1–AC7 |
| Incorrect feedback | 503 | Composable | 09#9.5 | UC-49 | AC2, AC5 |
| `OutlinedTextField` + auto-submit | 511 | Handler | 09#9.5 | UC-13 | AC1–AC4 |
| `DailyWordBankSection` | 553 | Composable | 09#9.5 | UC-14 | AC1, AC4 |
| `DailyInputModeBar` | 557 | Composable | 09#9.5, 12#12.8.4 | UC-57 | AC3, AC5 |
| Check button | 573 | Handler | 09#9.5 | UC-02 | AC1–AC5 |
| `SharedReportSheet` in DailyInputControls | 586 | Composable | 12#12.8.2 | UC-53 | AC1–AC9 |
| `DailyPracticeHeader` | 616 | Composable | 09#9.7.1 | UC-06 | AC1 |
| Exit dialog in header | 623 | Handler | 09#9.7.1 | UC-06 | AC1 |
| `BlockProgressBar` | 644 | Composable | 09#9.7.2 | UC-24 | AC1 |
| `VocabFlashcardBlock` | 656 | Composable | 09#9.3, 11#11.4 | UC-25 | AC1–AC5 |
| Voice recognition in VocabFlashcardBlock | 687 | Handler | 09#9.3 | UC-25 | AC4 |
| Voice auto-match + auto-rate | 694 | Handler | 09#9.3 | UC-25 | AC4 |
| Anki rating buttons (Again/Hard/Good/Easy) | 745 | Composable | 11#11.4, 09#9.3 | UC-25 | AC5 |
| `BlockSparkleOverlay` | 792 | Composable | 09#9.6.3 | UC-22 | AC1–AC2 |
| `DailyPracticeCompletionScreen` | 815 | Composable | 09#9.6.4 | UC-22 | AC1–AC4 |
| `loadTenseInfoFromAssets` | 841 | Helper fn | 09#9.7.4 | UC-58 | AC5 |
| `VerbReferenceBottomSheet` invocation | 430 | Composable | 09#9.7.4 | UC-58 | AC4 |
| `TenseInfoBottomSheet` invocation | 451 | Composable | 09#9.7.4 | UC-58 | AC5 |

## Pilot: TrainingCardSession.kt

**File:** `app/src/main/java/com/alexpo/grammermate/ui/TrainingCardSession.kt` (921 lines)

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `TrainingCardSessionScope` | 92 | @Stable class | 12#12.4.2 | UC-55 | AC1–AC5 |
| `TrainingCardSession` | 131 | Composable | 12#12.4.1, 12#12.5 | UC-55 | AC1–AC5 |
| `localInputText` state | 146 | State | 12#12.6 | — | — |
| `scope` creation | 155 | Factory | 12#12.4.2 | UC-55 | AC1–AC3 |
| Completion screen dispatch (`contract.isComplete`) | 190 | Branch | 12#12.3 | UC-05 | AC1 |
| Slot dispatch (header/cardContent/inputControls/resultContent/navigationControls) | 207–248 | Branch | 12#12.4.1 | UC-55 | AC1–AC5 |
| `DefaultHeader` | 258 | Composable | 12#12.4.3 | UC-56 | AC3, AC7 |
| Tense label in DefaultHeader | 265 | Composable | 12#12.4.3 | — | — |
| Parenthetical stripping (`Regex("\\s*\\([^)]+\\)")`) | 276 | Logic | 12#12.4.3, 21#2 | UC-48 | — |
| `DefaultProgressIndicator` | 292 | Composable | 12#12.4.8 | UC-05 | AC1 |
| Progress bar (70/30 split) | 310 | Composable | 12#12.4.8 | — | — |
| Speedometer arc | 344 | Composable | 12#12.4.8 | — | — |
| `DefaultCardContent` | 378 | Composable | 12#12.4.5 | UC-56 | AC3, AC7 |
| `TtsSpeakerButton` | 395 | Composable | 12#12.4.5 | UC-61 | AC1–AC4 |
| `DefaultInputControls` | 416 | Composable | 12#12.4.6 | UC-12, UC-13, UC-14, UC-15 | AC1–AC4, AC1–AC4, AC1–AC5, AC1–AC4 |
| `speechLauncher` (voice result) | 432 | Handler | 12#12.7.1 | UC-12 | AC2–AC4 |
| Report sheet (ModalBottomSheet) | 445 | Composable | 12#12.8.2 | UC-53 | AC1–AC9 |
| Flag/Unflag handler | 472–494 | Handler | 12#12.8.2 | UC-53 | AC6 |
| Hide card handler | 496 | Handler | 12#12.8.2 | UC-53 | AC7 |
| Export handler | 507 | Handler | 12#12.8.2 | UC-53 | AC8 |
| Copy text handler | 519 | Handler | 12#12.8.2 | UC-53 | AC9 |
| `OutlinedTextField` + auto-submit `isExactMatch` | 548 | Handler | 12#12.4.6 | UC-13 | AC1–AC4 |
| Word bank `FlowRow` | 622 | Composable | 12#12.4.6 | UC-14 | AC1, AC4 |
| Undo button | 656 | Handler | 12#12.4.6 | UC-14 | AC4 |
| Input mode selector (Mic/Keyboard/Book) | 673 | Composable | 12#12.4.6 | UC-15 | AC1–AC4 |
| Show answer button (eye) | 721 | Handler | 12#12.7.2 | UC-50 | AC1–AC4 |
| Flag/Report button | 734 | Handler | 12#12.8.2 | UC-53 | AC1–AC5 |
| Check button | 761 | Handler | 12#12.7.3 | UC-02 | AC1–AC5 |
| `DefaultResultContent` | 777 | Composable | 12#12.4.7 | UC-02, UC-03 | AC5, AC2 |
| `HintAnswerCard` in DefaultResultContent | 804 | Composable | 12#12.8.1 | UC-51 | AC1–AC7 |
| `DefaultNavigationControls` | 819 | Composable | 12#12.4.9 | UC-06 | AC1–AC4 |
| Exit confirmation dialog | 824 | Handler | 12#12.4.9 | UC-06 | AC1 |
| Pause/Play toggle | 860 | Handler | 12#12.4.9 | UC-06 | AC2 |
| `DefaultCompletionScreen` | 889 | Composable | 12#12.4.10 | UC-05 | AC1 |
| "Done" button | 913 | Handler | 12#12.4.10 | UC-05 | AC1 |

## Cross-Reference: UC → Symbol Coverage

| UC | TrainingScreen | VerbDrillScreen | DailyPracticeScreen | TrainingCardSession |
|----|---------------|----------------|--------------------|--------------------|
| UC-01 | TrainingScreen, CardPrompt, AnswerBox | — | — | DefaultHeader, DefaultInputControls |
| UC-02 | ResultBlock, AnswerBox (onSubmit) | — | DailyInputControls (Check) | DefaultResultContent, Check button |
| UC-03 | ResultBlock | — | DailyInputControls (incorrect feedback) | DefaultResultContent |
| UC-04 | NavigationRow (Next) | — | — | DefaultNavigationControls (Next) |
| UC-05 | DrillProgressRow, HeaderStats | — | — | DefaultCompletionScreen, DefaultProgressIndicator |
| UC-06 | NavigationRow, exit dialog | — | DailyPracticeHeader (exit dialog) | DefaultNavigationControls (exit dialog) |
| UC-12 | AnswerBox (voice), LaunchedEffect | autoSpeechLauncher | speechLauncher | DefaultInputControls (speechLauncher) |
| UC-13 | AnswerBox (keyboard auto-submit) | DefaultVerbDrillInputControls (keyboard) | DailyInputControls (keyboard) | DefaultInputControls (keyboard auto-submit) |
| UC-14 | FlowRow (word bank) | VerbDrillWordBankSection | DailyWordBankSection | DefaultInputControls (FlowRow) |
| UC-15 | Input mode buttons | VerbDrillInputModeBar | DailyInputModeBar | DefaultInputControls (mode buttons) |
| UC-16 | — | — | CardSessionBlock, DailyInputControls | — |
| UC-21 | — | — | DailyPracticeScreen | — |
| UC-22 | — | — | CardSessionBlock, BlockSparkleOverlay | — |
| UC-25 | — | — | VocabFlashcardBlock | — |
| UC-26 | — | VerbDrillScreen, VerbDrillSelectionScreen | — | — |
| UC-27 | — | DefaultVerbDrillInputControls, auto-advance LaunchedEffect | LaunchedEffect(auto-advance) | — |
| UC-28 | — | VerbDrillCompletionScreen | — | — |
| UC-49 | — | DefaultVerbDrillInputControls (3-attempt) | DailyInputControls (3-attempt) | — |
| UC-50 | AnswerBox (eye button) | VerbDrillInputModeBar (eye button) | DailyInputModeBar (eye button) | DefaultInputControls (eye button) |
| UC-51 | HintAnswerCard in ResultBlock | HintAnswerCard in DefaultVerbDrillInputControls | HintAnswerCard in DailyInputControls | HintAnswerCard in DefaultResultContent |
| UC-53 | SharedReportSheet | SharedReportSheet | SharedReportSheet | Report sheet (ModalBottomSheet) |
| UC-55 | — | — | — | TrainingCardSession + all slots |
| UC-56 | prompt fontSize scaling | prompt fontSize scaling | textScale on all blocks | DefaultHeader, DefaultCardContent (textScale) |
| UC-57 | LaunchedEffect(voiceAutoStart) | VoiceAutoLauncher, LaunchedEffect(auto-voice) | LaunchedEffect(auto-voice) | — |
| UC-58 | — | SuggestionChip (verb/tense), VerbReferenceBottomSheet, TenseInfoBottomSheet | SuggestionChip (verb/tense), VerbReferenceBottomSheet, TenseInfoBottomSheet | — |
| UC-59 | — | VoiceAutoLauncher, LaunchedEffect(auto-voice) | — | — |
| UC-61 | TtsSpeakerButton | TTS icon in cardContent | TTS icon in cardContent | TtsSpeakerButton |

## Trace Index Maintenance Rules

1. **When adding a new composable or handler** to any of the 4 pilot files, add a row to the corresponding table with spec reference and UC mapping.
2. **When a UC's ACs change** in `22-use-case-registry.md`, verify affected rows still map correctly.
3. **When refactoring** (renaming, extracting, moving symbols), update the Symbol column and Line column.
4. **Line numbers** are approximate (guidance only). Use symbol name as the primary identifier.
5. **Expanding beyond pilot files:** add a new section per file following the same table format. Priority candidates: `GrammarMateApp.kt`, `HomeScreen.kt`, `VocabDrillScreen.kt`, `SettingsScreen.kt`.
