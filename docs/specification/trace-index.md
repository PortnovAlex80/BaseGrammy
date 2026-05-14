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

## Phase 3: Sealed Result Types (Callback Removal)

Result type files that replace callback interfaces. Feature classes return these instead of calling callbacks. TrainingViewModel handles each result type via dedicated dispatcher methods.

### SessionEvent.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/training/SessionEvent.kt` (37 lines)

Replaces: `SessionCallbacks` interface. Methods in `SessionRunner` return `List<SessionEvent>` instead of calling callbacks.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `SessionEvent` (sealed class) | 17 | Result type | 08#7 | — | — |
| `SaveProgress` | 18 | Event variant | 08#7.3 | UC-04 | AC1 |
| `RefreshFlowerStates` | 19 | Event variant | 03#3.3, 08#7.4 | UC-05 | AC1 |
| `UpdateStreak` | 20 | Event variant | 08#7.5 | UC-05 | AC2 |
| `BuildSessionCards` | 21 | Event variant | 08#7.1 | UC-01 | AC1 |
| `PlaySuccess` | 22 | Event variant | 08#7.6 | UC-02 | AC5 |
| `PlayError` | 23 | Event variant | 08#7.6 | UC-03 | AC2 |
| `RecordCardShow` | 24 | Event variant | 02#2.2 | UC-05 | AC1 |
| `MarkSubLessonCardsShown` | 25 | Event variant | 02#2.2 | UC-05 | AC1 |
| `CheckAndMarkLessonCompleted` | 26 | Event variant | 08#7.2 | UC-05 | AC1 |
| `CalculateCompletedSubLessons` (callback-in-result) | 27 | Event variant | 03#3.2 | UC-05 | ? |
| `GetMastery` (callback-in-result) | 33 | Event variant | 02#2.2 | UC-05 | ? |
| `GetSchedule` (callback-in-result) | 34 | Event variant | 02#2.3 | UC-05 | ? |
| `RebuildSchedules` | 35 | Event variant | 03#3.4 | UC-01 | AC1 |
| `Composite` | 36 | Event variant | — | — | — |

### StoryResult.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/training/StoryResult.kt` (11 lines)

Replaces: `StoryCallbacks` interface. `StoryRunner` methods return `StoryResult` instead of calling callbacks.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `StoryResult` (sealed class) | 8 | Result type | 08#7.7 | UC-? | ? |
| `SaveAndBackup` | 9 | Result variant | 08#7.3, 06#6.4 | UC-04 | AC1 |
| `None` | 10 | Result variant | — | — | — |

### BossCommand (BossResult.kt)

**File:** `app/src/main/java/com/alexpo/grammermate/feature/boss/BossResult.kt` (24 lines)

Replaces: `BossCallbacks` interface. `BossOrchestrator` methods return `List<BossCommand>` instead of calling callbacks.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `BossCommand` (sealed class) | 8 | Result type | 08#7.8 | UC-07 | ? |
| `PauseTimer` | 9 | Command variant | 08#7.8 | UC-07 | AC2 |
| `ResumeTimer` | 10 | Command variant | 08#7.8 | UC-07 | AC3 |
| `SaveProgress` | 11 | Command variant | 08#7.3 | UC-04 | AC1 |
| `BuildSessionCards` | 12 | Command variant | 08#7.1 | UC-07 | AC4 |
| `RefreshFlowerStates` | 13 | Command variant | 03#3.3 | UC-05 | AC1 |
| `ResetBoss` | 15 | Command variant | 08#7.8 | UC-07 | — |
| `ResetDailySession` | 17 | Command variant | 09#9.9 | UC-21 | — |
| `ResetStory` | 19 | Command variant | 08#7.7 | — | — |
| `ResetVocabSprint` | 21 | Command variant | 08#7.12 | UC-25 | — |
| `Composite` | 22 | Command variant | — | — | — |

### ProgressResult.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/progress/ProgressResult.kt` (25 lines)

Replaces: `ProgressCallbacks` interface. `ProgressRestorer` methods return `List<ProgressResult>` instead of calling callbacks.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `ProgressResult` (sealed class) | 17 | Result type | 08#7.9, 13#13.3 | UC-? | ? |
| `None` | 18 | Result variant | — | — | — |
| `RebuildSchedules` | 19 | Result variant | 03#3.4 | UC-01 | AC1 |
| `BuildSessionCards` | 20 | Result variant | 08#7.1 | UC-01 | AC1 |
| `RefreshFlowerStates` | 21 | Result variant | 03#3.3 | UC-05 | AC1 |
| `NormalizeEliteSpeeds` (callback-in-result) | 22 | Result variant | 08#7.10 | UC-? | ? |
| `ResolveEliteUnlocked` (callback-in-result) | 23 | Result variant | 08#7.10 | UC-? | ? |
| `ParseBossRewards` (callback-in-result) | 24 | Result variant | 08#7.8 | UC-07 | ? |

### BadSentenceResult.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/progress/BadSentenceResult.kt` (12 lines)

Replaces: `BadSentenceCallbacks` interface. `BadSentenceHelper.flagBadSentence()` and `hideCurrentCard()` return `BadSentenceResult` instead of calling callbacks.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `BadSentenceResult` (sealed class) | 8 | Result type | 12#12.8.2 | UC-53 | ? |
| `AdvanceDrillCard` | 9 | Result variant | 08#7.11 | UC-53 | AC7 |
| `SkipToNextCard` | 10 | Result variant | 12#12.8.2 | UC-53 | AC7 |
| `None` | 11 | Result variant | — | — | — |

### VocabResult.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/vocab/VocabResult.kt` (20 lines)

Replaces: `VocabSprintCallbacks` interface. `VocabSprintRunner.submitAnswer()` returns `VocabSubmitResult` (combining `VocabSoundResult` + `VocabResult`) instead of calling callbacks.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `VocabResult` (sealed class) | 8 | Result type | 08#7.12 | UC-25 | ? |
| `VocabResult.SaveAndBackup` | 9 | Result variant | 08#7.3, 06#6.4 | UC-04 | AC1 |
| `VocabResult.ResetBoss` | 11 | Result variant | 08#7.8 | UC-07 | — |
| `VocabResult.None` | 12 | Result variant | — | — | — |
| `VocabSoundResult` (sealed class) | 15 | Result type | 08#7.6 | UC-25 | ? |
| `VocabSoundResult.PlaySuccess` | 16 | Result variant | 08#7.6 | UC-02 | AC5 |
| `VocabSoundResult.PlayError` | 17 | Result variant | 08#7.6 | UC-03 | AC2 |
| `VocabSoundResult.None` | 18 | Result variant | — | — | — |

### SettingsResult.kt

**File:** `app/src/main/java/com/alexpo/grammermate/shared/SettingsResult.kt` (19 lines)

Replaces: `SettingsCallbacks` interface. `SettingsActionHandler` methods return `List<SettingsResult>` instead of calling callbacks.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `SettingsResult` (sealed class) | 8 | Result type | 08#7.13 | UC-30 | ? |
| `None` | 9 | Result variant | — | — | — |
| `RefreshLessons` | 10 | Result variant | 08#7.13 | UC-30 | AC3 |
| `ResetStores` | 11 | Result variant | 06#6.5 | UC-30 | AC4 |
| `ResetStoresForLanguage` | 12 | Result variant | 06#6.5 | UC-30 | AC4 |
| `ResetDrillFiles` | 13 | Result variant | 06#6.5 | UC-30 | AC4 |
| `ResetDrillFilesForPack` | 14 | Result variant | 06#6.5 | UC-30 | AC4 |
| `ClearWordMastery` | 15 | Result variant | 02#2.4 | UC-30 | AC4 |
| `ResetDailyState` | 16 | Result variant | 09#9.9 | UC-30 | AC4 |
| `SetForceBackup` | 17 | Result variant | 06#6.4 | UC-30 | ? |
| `SaveProgress` | 18 | Result variant | 08#7.3 | UC-04 | AC1 |

## Phase 3: Modified Feature Methods

Feature class methods whose return types changed from `Unit` to sealed result types.

### BadSentenceHelper.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/progress/BadSentenceHelper.kt` (128 lines)

| Symbol | Line | Type | Return type | Spec | UC | AC |
|--------|------|------|-------------|------|----|----|
| `flagBadSentence()` | 25 | Method | `BadSentenceResult` | 12#12.8.2 | UC-53 | AC6 |
| `hideCurrentCard()` | 105 | Method | `BadSentenceResult` | 12#12.8.2 | UC-53 | AC7 |

### StoryRunner.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/training/StoryRunner.kt` (87 lines)

| Symbol | Line | Type | Return type | Spec | UC | AC |
|--------|------|------|-------------|------|----|----|
| `openStory(phase)` | 32 | Method | `StoryResult` | 08#7.7 | UC-? | ? |
| `completeStory(phase, allCorrect)` | 59 | Method | `StoryResult` | 08#7.7 | UC-? | ? |
| `clearStoryError()` | 81 | Method | `StoryResult` | 08#7.7 | UC-? | ? |

### VocabSprintRunner.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/vocab/VocabSprintRunner.kt` (360 lines)

| Symbol | Line | Type | Return type | Spec | UC | AC |
|--------|------|------|-------------|------|----|----|
| `VocabSubmitResult` (data class) | 17 | Result type | — | 08#7.12 | UC-25 | ? |
| `openSprint(resume)` | 51 | Method | `VocabResult` | 08#7.12 | UC-25 | AC1 |
| `submitAnswer(inputOverride)` | 184 | Method | `VocabSubmitResult` | 08#7.12 | UC-25 | AC3–AC5 |

### BossOrchestrator.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/boss/BossOrchestrator.kt` (331 lines)

| Symbol | Line | Type | Return type | Spec | UC | AC |
|--------|------|------|-------------|------|----|----|
| `startMixChallenge()` | 49 | Method | `Pair<Boolean, List<BossCommand>>` | 08#7.8 | UC-07 | AC1 |
| `startBossLesson()` | 94 | Method | `List<BossCommand>` | 08#7.8 | UC-07 | AC1 |
| `startBossMega()` | 96 | Method | `List<BossCommand>` | 08#7.8 | UC-07 | AC1 |
| `startBossElite()` | 98 | Method | `List<BossCommand>` | 08#7.8 | UC-07 | AC1 |
| `startBoss(type)` | 102 | Method | `List<BossCommand>` | 08#7.8 | UC-07 | AC1 |
| `finishBoss()` | 159 | Method | `List<BossCommand>` | 08#7.8 | UC-07 | AC5 |
| `clearBossRewardMessage()` | 211 | Method | `List<BossCommand>` | 08#7.8 | UC-07 | AC6 |
| `updateBossProgress(progress)` | 247 | Method | `List<BossCommand>` | 08#7.8 | UC-07 | AC3 |
| `advanceBossProgressOnNextCard(nextIndex, totalCards)` | 292 | Method | `Pair<BossAdvanceResult, List<BossCommand>>` | 08#7.8 | UC-07 | AC3 |

### ProgressRestorer.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/progress/ProgressRestorer.kt` (205 lines)

| Symbol | Line | Type | Return type | Spec | UC | AC |
|--------|------|------|-------------|------|----|----|
| `applyRestoredProgress(...)` | 56 | Method | `List<ProgressResult>` | 08#7.9, 13#13.3 | UC-29 | ? |
| `restoreBackup(backupUri)` | 126 | Method | `List<ProgressResult>` | 06#6.4, 13#13.3 | UC-29 | AC1 |
| `reloadFromDisk()` | 173 | Method | `List<ProgressResult>` | 13#13.3 | UC-29 | ? |

### SettingsActionHandler.kt

**File:** `app/src/main/java/com/alexpo/grammermate/shared/SettingsActionHandler.kt` (164 lines)

| Symbol | Line | Type | Return type | Spec | UC | AC |
|--------|------|------|-------------|------|----|----|
| `toggleTestMode()` | 41 | Method | `List<SettingsResult>` | 08#7.13 | UC-30 | AC1 |
| `saveProgressNow()` | 80 | Method | `List<SettingsResult>` | 08#7.13 | UC-30 | AC2 |
| `resetAllProgress(app)` | 96 | Method | `List<SettingsResult>` | 06#6.5, 08#7.13 | UC-30 | AC4 |
| `resetLanguageProgress(app, languageId, packId)` | 128 | Method | `List<SettingsResult>` | 06#6.5, 08#7.13 | UC-30 | AC4 |

### SessionRunner.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/training/SessionRunner.kt` (751 lines)

| Symbol | Line | Type | Return type | Spec | UC | AC |
|--------|------|------|-------------|------|----|----|
| `SubmitResult` (data class) | 83 | Result type | — | 08#7.1 | UC-02 | ? |
| `startSession()` | 99 | Method | `List<SessionEvent>` | 08#7.1 | UC-01 | AC1 |
| `finishSession()` | 126 | Method | `Pair<SessionFinishResult, List<SessionEvent>>` | 08#7.1 | UC-04 | AC1 |
| `resumeFromSettings()` | 144 | Method | `List<SessionEvent>` | 08#7.1 | UC-01 | AC1 |
| `submitAnswer()` | 196 | Method | `Pair<SubmitResult, List<SessionEvent>>` | 08#7.1 | UC-02 | AC1–AC5 |
| `nextCard(triggerVoice)` | 400 | Method | `List<SessionEvent>` | 08#7.1 | UC-04 | AC1 |
| `prevCard()` | 430 | Method | `List<SessionEvent>` | 08#7.1 | UC-04 | AC1 |
| `selectSubLesson(index)` | 442 | Method | `List<SessionEvent>` | 08#7.1 | UC-05 | AC1 |
| `togglePause()` | 452 | Method | `List<SessionEvent>` | 08#7.1 | UC-06 | AC2 |
| `pauseSession()` | 461 | Method | `List<SessionEvent>` | 08#7.1 | UC-06 | AC2 |
| `showAnswer()` | 469 | Method | `List<SessionEvent>` | 12#12.7.2 | UC-50 | AC1–AC4 |
| `openEliteStep(index)` | 521 | Method | `List<SessionEvent>` | 08#7.10 | UC-? | ? |
| `cancelEliteSession()` | 534 | Method | `List<SessionEvent>` | 08#7.10 | UC-? | ? |
| `startDrill(resume)` | 576 | Method | `List<SessionEvent>` | 08#7.11 | UC-05 | AC1 |
| `finishDrill(lessonId)` | 635 | Method | `List<SessionEvent>` | 08#7.11 | UC-05 | AC1 |
| `exitDrillMode()` | 643 | Method | `List<SessionEvent>` | 08#7.11 | UC-05 | AC1 |
| `SessionFinishResult` (sealed class) | 746 | Result type | — | 08#7.1 | UC-04 | ? |

## Phase 3: TrainingViewModel Handler Methods

Dispatcher methods in `TrainingViewModel.kt` that execute side effects for each sealed result variant. These are the bridge between feature classes (which return results) and the ViewModel infrastructure (which performs the side effects).

**File:** `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`

| Symbol | Line | Type | Delegates to | Spec | UC | AC |
|--------|------|------|--------------|------|----|----|
| `handleSessionEvents(events)` | 1087 | Handler | `saveProgress`, `refreshFlowerStates`, `updateStreak`, `buildSessionCards`, `audioCoordinator`, `recordCardShowForMastery`, `markSubLessonCardsShown`, `checkAndMarkLessonCompleted`, `rebuildSchedules` | 08#7 | UC-01–UC-05 | AC1–AC5 |
| `handleBossCommands(commands)` | 1195 | Handler | `sessionRunner.pauseTimer`, `sessionRunner.resumeTimer`, `saveProgress`, `buildSessionCards`, `refreshFlowerStates` | 08#7.8 | UC-07 | AC1–AC6 |
| `handleSettingsResults(results)` | 1230 | Handler | `refreshLessons`, `resetStores`, `resetStoresForLanguage`, `resetDrillFiles`, `resetDrillFilesForPack`, `clearWordMastery`, `resetDailyState`, `saveProgress` | 08#7.13 | UC-30 | AC1–AC4 |
| `handleProgressResults(results)` | 1248 | Handler | `rebuildSchedules`, `buildSessionCards`, `refreshFlowerStates`, `sessionRunner.normalizeEliteSpeeds`, `sessionRunner.resolveEliteUnlocked`, `bossOrchestrator.parseBossRewards` | 08#7.9, 13#13.3 | UC-29 | ? |

## Phase 4: Feature-Owned StateFlows

Feature classes now own their own `MutableStateFlow<T>` and expose `StateFlow<T>`. The ViewModel's `uiState` is assembled via a 7-flow `combine` chain that merges core state with 6 feature flows. Feature state classes (`StoryState`, `VocabSprintState`, `DailyPracticeState`, `FlowerDisplayState`, `BossState`) are defined in `Models.kt`.

### StoryRunner.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/training/StoryRunner.kt` (96 lines)

Owns `MutableStateFlow<StoryState>`. Story quiz lifecycle: load quiz, mark completion per phase, clear errors.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `_state` / `stateFlow` | 27–28 | StateFlow owner | 08#7.7 | — | — |
| `openStory(phase)` | 39 | Method | 08#7.7 | UC-? | ? |
| `completeStory(phase, allCorrect)` | 62 | Method | 08#7.7 | UC-? | ? |
| `clearStoryError()` | 84 | Method | 08#7.7 | UC-? | ? |
| `resetState()` | 93 | Method | 08#7.7 | — | — |

### VocabSprintRunner.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/vocab/VocabSprintRunner.kt` (354 lines)

Owns `MutableStateFlow<VocabSprintState>`. Vocab sprint session: setup, answer validation, word bank generation, SRS progress, session completion.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `_state` / `vocabState` | 44–45 | StateFlow owner | 08#7.12 | — | — |
| `openSprint(resume)` | 59 | Method | 08#7.12 | UC-25 | AC1 |
| `clearError()` | 121 | Method | 08#7.12 | UC-25 | ? |
| `onInputChanged(text)` | 129 | Method | 08#7.12 | UC-25 | ? |
| `setInputMode(mode)` | 144 | Method | 08#7.12 | UC-25 | ? |
| `requestVoice()` | 154 | Method | 08#7.12 | UC-25 | ? |
| `submitAnswer(inputOverride)` | 170 | Method | 08#7.12 | UC-25 | AC3–AC5 |
| `showAnswer()` | 215 | Method | 08#7.12 | UC-25 | ? |
| `updateMasteredCount(count)` | 343 | Method | 08#7.12 | UC-25 | ? |
| `resetState()` | 351 | Method | 08#7.12 | — | — |

### DailyPracticeCoordinator.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinator.kt` (574 lines)

Owns `MutableStateFlow<DailyPracticeState>`. Orchestrates 3-block daily practice session (Translate, Vocab, Verbs). Absorbs `DailySessionHelper` + `DailySessionComposer` + per-call store creation.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `_state` / `dailyState` | 51–52 | StateFlow owner | 09#9.1 | — | — |
| `prebuiltDailySession` | 57 | Mutable state | 09#9.8 | — | — |
| `lastDailyTasks` | 61 | Mutable state | 09#9.8 | — | — |
| `hasResumableDailySession()` | 230 | Query | 09#9.9 | UC-21 | ? |
| `startDailyPractice(...)` | 245 | Method | 09#9.2 | UC-21 | AC1 |
| `repeatDailyPractice(...)` | 306 | Method | 09#9.2 | UC-21 | ? |
| `advanceDailyTask(...)` | 363 | Method | 09#9.5 | UC-22 | AC2 |
| `recordDailyCardPracticed(...)` | 373 | Method | 09#9.5, 02#2.2 | UC-05 | AC1 |
| `advanceDailyBlock()` | 391 | Method | 09#9.6.3 | UC-22 | AC1–AC2 |
| `persistDailyVerbProgress(card)` | 395 | Method | 10#10.6 | UC-22 | ? |
| `repeatDailyBlock(...)` | 413 | Method | 09#9.6 | UC-22 | ? |
| `cancelDailySession()` | 444 | Method | 09#9.9 | UC-21 | ? |
| `rateVocabCard(rating)` | 466 | Method | 11#11.4 | UC-25 | AC5 |
| `submitDailySentenceAnswer(input)` | 495 | Method | 09#9.5 | UC-02 | AC1–AC5 |
| `submitDailyVerbAnswer(input)` | 501 | Method | 09#9.5 | UC-27 | AC1–AC2 |
| `prebuildSession(...)` | 525 | Method | 09#9.8 | UC-21 | ? |
| `resetState()` | 544 | Method | 09#9.9 | — | — |
| `clearPrebuiltSession()` | 555 | Method | 09#9.8 | — | — |
| `updateCursor(cursor)` | 566 | Method | 09#9.9 | UC-21 | ? |
| `getCursor()` | 573 | Method | 09#9.9 | UC-21 | ? |
| `advanceDailyCursor(...)` | 631 | Method | 09#9.6.4 | UC-61 | AC1–AC5 |
| `dailyCursorAtSessionStart` | 73 | Mutable state | 09#9.2 | UC-21 | AC1 |
| `dailyPracticeAnsweredCounts` | 70 | Mutable state | 09#9.6.4 | UC-24 | AC1 |

### FlowerRefresher.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/progress/FlowerRefresher.kt` (92 lines)

Owns `MutableStateFlow<FlowerDisplayState>`. Recomputes flower visuals and ladder rows for all lessons. Called after mastery-changing operations.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `_state` / `stateFlow` | 31–32 | StateFlow owner | 03#3.3 | — | — |
| `refreshFlowerStates()` | 43 | Method | 03#3.3 | UC-05 | AC1 |

### BossOrchestrator.kt

**File:** `app/src/main/java/com/alexpo/grammermate/feature/boss/BossOrchestrator.kt` (387 lines)

Owns `MutableStateFlow<BossState>`. Boss battle and Mix Challenge orchestration. Coordinates `BossBattleRunner`, `CardProvider`, and `SessionRunner`.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `_state` / `stateFlow` | 43–44 | StateFlow owner | 08#7.8 | — | — |
| `startMixChallenge()` | 57 | Method | 08#7.8 | UC-07 | AC1 |
| `startBossLesson()` | 106 | Method | 08#7.8 | UC-07 | AC1 |
| `startBossMega()` | 108 | Method | 08#7.8 | UC-07 | AC1 |
| `startBossElite()` | 110 | Method | 08#7.8 | UC-07 | AC1 |
| `startBoss(type)` | 114 | Method | 08#7.8 | UC-07 | AC1 |
| `finishBoss()` | 173 | Method | 08#7.8 | UC-07 | AC5 |
| `clearBossRewardMessage()` | 230 | Method | 08#7.8 | UC-07 | AC6 |
| `clearBossError()` | 262 | Method | 08#7.8 | UC-07 | ? |
| `updateBossProgress(progress)` | 266 | Method | 08#7.8 | UC-07 | AC3 |
| `advanceBossProgressOnNextCard(nextIndex, totalCards)` | 313 | Method | 08#7.8 | UC-07 | AC3 |
| `parseBossRewards(rewardMap)` | 345 | Method | 08#7.8 | UC-07 | ? |
| `initRewards(lessonRewards, megaRewards)` | 358 | Method | 08#7.8 | UC-07 | ? |
| `resetState()` | 366 | Method | 08#7.8 | — | — |
| `resetStateKeepRewards()` | 374 | Method | 08#7.8 | — | — |

## Phase 4: TrainingViewModel — 7-Flow Combine Chain

The ViewModel's `uiState: StateFlow<TrainingUiState>` is assembled from 7 flows in a two-stage `combine` chain:

1. **Stage 1** (5 flows): `_coreState`, `audioCoordinator.audioState`, `storyRunner.stateFlow`, `vocabSprintRunner.vocabState`, `dailyPracticeCoordinator.dailyState` — produces `TrainingUiState` with core + audio + story + vocabSprint + daily.
2. **Stage 2** (2 flows): Stage 1 output, `flowerRefresher.stateFlow`, `bossOrchestrator.stateFlow` — produces final `TrainingUiState` with flowerDisplay + boss.

**File:** `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`

| Symbol | Line | Type | Description | Spec |
|--------|------|------|-------------|------|
| `uiState` (combine stage 1) | 225–237 | StateFlow combine | `_coreState` + audio + story + vocabSprint + daily | 08#7 |
| `uiState` (combine stage 2) | 238–246 | StateFlow combine | Stage 1 + flowerDisplay + boss | 08#7 |

## Phase 4: Models.kt — Core Reset Methods

`resetSessionState()` and `resetAllSessionState()` in `TrainingUiState` now reset only core fields (`cardSession`, `drill`, `elite`). Feature-owned state (boss, story, vocabSprint, daily, flowerDisplay) is reset by explicit calls to each feature's `resetState()` method.

**File:** `app/src/main/java/com/alexpo/grammermate/data/Models.kt`

| Symbol | Line | Type | Description | Spec |
|--------|------|------|-------------|------|
| `resetSessionState()` | 419 | Method | Resets `cardSession` and `drill` only. Feature resets are called separately. | 08#7 |
| `resetAllSessionState()` | 431 | Method | Resets `cardSession`, `drill`, and `elite` counters. Feature resets are called separately. | 08#7 |

## Phase 4: TrainingViewModel — Feature Reset Calls

The ViewModel explicitly calls each feature's `resetState()` or `resetStateKeepRewards()` at specific lifecycle points. Feature reset commands (`ResetBoss`, `ResetDailySession`, `ResetStory`, `ResetVocabSprint`) are also dispatched via `BossCommand` in `handleBossCommands`.

**File:** `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`

| Call site (ViewModel method) | Line(s) | Feature resets called | Context |
|------------------------------|---------|----------------------|---------|
| `init` block | 291–292 | `initRewards`, `updateMasteredCount`, `updateCursor` | Initialize feature state from persisted progress |
| `selectLanguage(languageId)` | 414–418 | `resetStateKeepRewards` (boss), `resetState` (story, vocabSprint, daily) | Language change |
| `selectLesson(lessonId)` | 463–466 | `resetState` (boss, story, vocabSprint, daily) | Lesson change |
| `selectSubLesson(index)` | 497–499 | `resetState` (boss, story, vocabSprint) | Sub-lesson change |
| `resetProgress(app)` | 578–581 | `resetStateKeepRewards` (boss), `resetState` (story, vocabSprint, daily) | Full progress reset |
| `importLessonPack(uri)` | 619–622 | `resetStateKeepRewards` (boss), `resetState` (story, vocabSprint, daily) | Pack import |
| `openVocabSprint(resume)` | 853 | `resetState` (boss) via `VocabResult.ResetBoss` | Vocab sprint start |
| `submitVocabAnswer(...)` | 891 | `resetState` (boss) via `VocabResult.ResetBoss` | Vocab answer submission |
| `storeFirstSessionCardIds(...)` | 698–702 | `getCursor`, `updateCursor` | Daily session card ID storage |
| `advanceDailyCursor(sentenceCount)` | 714–718 | `getCursor`, `updateCursor` | Daily cursor advancement |
| `refreshVocabMasteryCount()` | 1069 | `updateMasteredCount` | Drill mastery refresh |
| `handleBossCommands(commands)` | 1259–1264 | `resetState` via `ResetBoss`, `ResetDailySession`, `ResetStory`, `ResetVocabSprint` commands | Boss command dispatch |
| `handleSettingsResults` — `resetAllProgress` | 1281–1284 | `resetState` (boss, story, vocabSprint, daily) | Settings: reset all |
| `resetDailyState()` | 1294 | `resetState` (daily) | Settings: reset daily |
| `startMixChallenge()` | ~101 | `ResetStory`, `ResetVocabSprint`, `ResetDailySession` via `BossCommand` | Mix Challenge start |

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

## Phase 6: TASK-001 through TASK-005 Changes

### TtsEngine.kt (TASK-003, TASK-005)

**File:** `app/src/main/java/com/alexpo/grammermate/data/TtsEngine.kt` (350 lines)

TTS engine with Mutex-serialized native calls, init timeout, and error reason propagation.

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `TtsState` (sealed class) | 31 | Sealed class | 05#5.1.8 | UC-61 | AC4 |
| `TtsState.Idle` | 32 | State variant | 05#5.1.8 | — | — |
| `TtsState.Initializing` | 33 | State variant | 05#5.1.8 | UC-61 | AC2 |
| `TtsState.Ready` | 34 | State variant | 05#5.1.8 | UC-61 | AC1 |
| `TtsState.Speaking` | 35 | State variant | 05#5.1.8 | — | — |
| `TtsState.Error(reason: String?)` | 36 | State variant | 05#5.1.8 | UC-61 | AC4 |
| `mutex` | 61 | Mutex | 05#5.1.8 | UC-61 | — |
| `initFailed` | 64 | Volatile flag | 05#5.1.8 | UC-61 | AC4 |
| `initialize(languageId)` | 71 | Method (mutex-locked) | 05#5.1.8 | UC-61 | AC2 |
| `speak(...)` | 165 | Method (mutex-locked) | 05#5.1.8 | UC-61 | AC4 |
| `withTimeout(30_000L)` in initialize | 107 | Timeout guard | 05#5.1.8 | UC-61 | — |
| OOM catch → `TtsState.Error("Not enough memory")` | 114–118 | Error handler | 05#5.1.8 | UC-61 | — |
| Timeout catch → `TtsState.Error("Timed out")` | 119–123 | Error handler | 05#5.1.8 | UC-61 | — |

### SharedComponents.kt — TtsSpeakerButton (TASK-005)

**File:** `app/src/main/java/com/alexpo/grammermate/ui/components/SharedComponents.kt`

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `TtsSpeakerButton` error branch | 114 | Composable | 05#5.1.8 | UC-61 | AC4 |
| `isOom` check | 116 | Logic | 05#5.1.8 | UC-61 | — |
| `errorIcon` Warning vs ReportProblem | 117 | Logic | 05#5.1.8 | UC-61 | — |
| `TooltipBox` with `PlainTooltip` | 119–129 | Composable | 05#5.1.8 | UC-61 | — |

### LessonStore.kt — Caching (TASK-002)

**File:** `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt`

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `lessonsCache` | 116 | Cache field | 02#2.1 | UC-60 | AC3 |
| `getLessons(languageId)` | 270–278 | Cached method | 02#2.1 | UC-60 | AC3 |
| `invalidateLessonsCache(languageId?)` | 284–286 | Cache invalidation | 02#2.1 | UC-60 | AC3 |

### VerbDrillStore.kt — Caching (TASK-002)

**File:** `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `progressCache` | 37 | Cache field | 02#2.5 | UC-60 | AC4 |
| `cardsCacheKey` / `cardsCache` | 40–41 | Cache fields | 02#2.5 | UC-60 | AC4 |
| `loadProgress()` | 43–48 | Cached method | 02#2.5 | UC-60 | AC4 |
| `loadAllCardsForPack(...)` | 123–130 | Cached method | 02#2.5 | UC-60 | AC4 |
| `invalidateCache()` | 157–161 | Cache invalidation | 02#2.5 | UC-60 | AC4 |

### WordMasteryStore.kt — Caching (TASK-002)

**File:** `app/src/main/java/com/alexpo/grammermate/data/WordMasteryStore.kt`

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `cache` / `cacheLoaded` | 50–51 | Cache fields | 02#2.6 | UC-60 | AC4 |
| `loadAll()` | 56–62 | Cached method | 02#2.6 | UC-60 | AC4 |
| `invalidateCache()` | 181–184 | Cache invalidation | 02#2.6 | UC-60 | AC4 |

### DailySessionComposer.kt — Caching (TASK-002)

**File:** `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionComposer.kt`

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `cachedVocabWords` | 36 | Cache field | 09#9.4.1 | UC-60 | AC3 |
| `cachedVerbDrillCards` | 37 | Cache field | 09#9.5.1 | UC-60 | AC3 |
| `invalidateCache(packId?, languageId?)` | 418–427 | Cache invalidation | 09#9.4.1 | UC-60 | AC3 |

### GrammarMateApp.kt — Welcome Dialog (TASK-004)

**File:** `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt`

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `showWelcomeDialog` in DialogState | 91 | State field | 13#13.4.3 | UC-46 | AC3 |
| `LaunchedEffect(welcomeDialogAttempts)` | 573 | LaunchedEffect | 13#13.4.3 | UC-46 | AC3 |
| `WelcomeDialog` composable | 900 | Composable | 13#13.4.3 | UC-46 | AC3 |
| `vm.settings.incrementWelcomeDialogAttempts()` | 587 | Handler | 13#13.4.3 | UC-46 | AC3 |

### SettingsActionHandler.kt — Welcome Dialog (TASK-004)

**File:** `app/src/main/java/com/alexpo/grammermate/shared/SettingsActionHandler.kt`

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `incrementWelcomeDialogAttempts()` | 78 | Method | 13#13.4.3 | UC-46 | AC3 |

### ProfileStore.kt — Welcome Dialog (TASK-004)

**File:** `app/src/main/java/com/alexpo/grammermate/data/ProfileStore.kt`

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `welcomeDialogAttempts` in load/save | 32, 45 | Field | 13#13.4.3 | UC-46 | AC3 |

### Models.kt — Welcome Dialog (TASK-004)

**File:** `app/src/main/java/com/alexpo/grammermate/data/Models.kt`

| Symbol | Line | Type | Spec | UC | AC |
|--------|------|------|------|----|----|
| `welcomeDialogAttempts` in NavigationState | 275 | Field | 13#13.4.3 | UC-46 | AC3 |

## Trace Index Maintenance Rules

1. **When adding a new composable or handler** to any of the 4 pilot files, add a row to the corresponding table with spec reference and UC mapping.
2. **When a UC's ACs change** in `22-use-case-registry.md`, verify affected rows still map correctly.
3. **When refactoring** (renaming, extracting, moving symbols), update the Symbol column and Line column.
4. **Line numbers** are approximate (guidance only). Use symbol name as the primary identifier.
5. **Expanding beyond pilot files:** add a new section per file following the same table format. Priority candidates: `GrammarMateApp.kt`, `HomeScreen.kt`, `VocabDrillScreen.kt`, `SettingsScreen.kt`.
6. **Phase 3 result types:** when adding a new variant to a sealed result class (e.g. `SessionEvent`, `BossCommand`, `SettingsResult`), add a row to the corresponding result-type table AND add a `when` branch to the handler method table. When adding a new sealed result class, add a new section following the Phase 3 format.
7. **Phase 4 feature StateFlows:** when a feature class gains or loses an owned `StateFlow`, update the corresponding Phase 4 table AND update the combine chain description in the "7-Flow Combine Chain" section. When a new feature class acquires its own StateFlow, add a new section following the Phase 4 format.
8. **Phase 4 reset wiring:** when a ViewModel method adds or removes a feature `resetState()` / `resetStateKeepRewards()` call, update the "Feature Reset Calls" table. When a new `BossCommand` reset variant is added, update both the BossResult.kt table and the handler dispatch table.

## Phase 5: Unit Test Coverage

### SessionRunnerTest

**File:** `app/src/test/java/com/alexpo/grammermate/feature/training/SessionRunnerTest.kt`

Tests `SessionRunner` — answer submission (6 branching paths), card navigation, session lifecycle, pause/resume, input modes, word bank, drill/elite sub-modes.

| Symbol | Type | Spec | UC | AC |
|--------|------|------|----|-----|
| SessionRunnerTest | test | 08#7.1 | UC-01–UC-06 | AC1–AC6 |
| submitAnswer (correct normal mid/last) | test | 08#7.1 | UC-02, UC-05 | AC1–AC5 |
| submitAnswer (boss mid/last) | test | 08#7.1, 08#7.8 | UC-07 | AC1–AC6 |
| submitAnswer (drill mode) | test | 08#7.11 | UC-05 | AC1 |
| submitAnswer (elite mode) | test | 08#7.10 | UC-05, UC-06 | AC1, AC2 |
| submitAnswer (wrong + hint threshold) | test | 08#7.1 | UC-03 | AC2–AC5 |
| submitAnswer (wrong + voice/keyboard) | test | 08#7.1 | UC-03, UC-12 | AC1, AC4 |
| nextCard / prevCard (boundaries) | test | 08#7.1 | UC-04 | AC1–AC3 |
| startSession (with/without cards, boss active) | test | 08#7.1 | UC-01 | AC1, AC3–AC5 |
| finishSession (normal/elite/rating) | test | 08#7.1 | UC-06 | AC2 |
| setInputMode (mode switching) | test | 08#7.1 | UC-15 | AC1 |
| togglePause / pauseSession | test | 08#7.1 | UC-06 | AC2–AC3 |
| showAnswer (hint display) | test | 12#12.7.2 | UC-50 | AC1–AC4 |
| selectWordFromBank / removeLastSelectedWord | test | 08#7.1 | UC-14 | AC1, AC4 |
| skipToNextCard | test | 08#7.1 | UC-04 | AC1 |
| selectSubLesson | test | 08#7.1 | UC-05 | AC1 |
| drill sub-mode (start/finish/exit) | test | 08#7.11 | UC-05 | AC1 |
| elite helpers (open/cancel/normalize/resolve) | test | 08#7.10 | UC-05 | — |
| onInputChanged | test | 08#7.1 | UC-02 | AC1 |
| resumeFromSettings | test | 08#7.1 | UC-01 | AC1 |
| fullFlow integration tests | test | 08#7.1 | UC-02, UC-03 | AC1–AC5 |

### DailyPracticeCoordinatorTest

**File:** `app/src/test/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinatorTest.kt`

Tests `DailyPracticeCoordinator` — 3-block session lifecycle, block boundaries, cursor advancement, cancel/repeat, verb progress persistence, vocab SRS rating, edge cases.

| Symbol | Type | Spec | UC | AC |
|--------|------|------|----|-----|
| DailyPracticeCoordinatorTest | test | 09#9.2 | UC-21–UC-25 | AC1–AC5 |
| startDailySession (empty/with tasks/no pack) | test | 09#9.6.1 | UC-21 | AC1–AC3 |
| advanceDailyTask (block boundaries translate→vocab→verbs) | test | 09#9.6.2, 09#9.6.3 | UC-22 | AC1–AC3 |
| advanceToNextBlock / advanceDailyBlock | test | 09#9.6.2 | UC-22 | AC1–AC3 |
| cancelDailySession (cursor off-by-one verification) | test | 09#9.6.4 | UC-23 | AC4 |
| endSession | test | 09#9.6.4 | UC-22 | AC1 |
| repeatDailyPractice (cached/fallback) | test | 09#9.6.5 | UC-23 | AC1–AC2 |
| getBlockProgress | test | 09#9.8.3 | UC-24 | AC1 |
| replaceCurrentBlock | test | 09#9.8.6 | UC-22 | AC1 |
| persistDailyVerbProgress | test | 09#9.5.6 | UC-22 | — |
| rateVocabCard (AGAIN/HARD/GOOD/EASY SRS) | test | 09#9.4.6, 11#11.4 | UC-25 | AC5 |
| submitDailySentenceAnswer / submitDailyVerbAnswer | test | 09#9.3.4, 09#9.5 | UC-02, UC-27 | AC1–AC5 |
| recordDailyCardPracticed | test | 09#9.3.5 | UC-05 | AC1 |
| hasResumableDailySession | test | 09#9.8.5 | UC-23 | AC1 |
| updateCursor / getCursor | test | 09#9.8.4 | UC-24 | AC1–AC3 |
| resetState / clearPrebuiltSession | test | 09#9.9 | — | — |
| edge cases (single card, interrupted, empty) | test | 09#9.6.2 | UC-22 | AC1–AC3 |
| fullLifecycle integration | test | 09#9.6.1–09#9.6.4 | UC-21–UC-24 | AC1–AC4 |
