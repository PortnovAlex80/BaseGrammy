# 7. App Router (GrammarMateApp) -- Specification

GrammarMateApp.kt is the central UI routing hub and the only composable entry point for all application screens, dialogs, overlays, and settings. Despite its name, it is not a screen itself -- it is a **stateless screen dispatcher** that collects a single `StateFlow<TrainingUiState>` from `TrainingViewModel` and delegates rendering to per-screen composable functions. It owns no business logic.

---

## 7.1 Architecture Overview

### 7.1.1 Role

GrammarMateApp serves as:

1. **Screen router** -- holds a `screen: AppScreen` mutable state and switches between screen composables via a `when(screen)` block.
2. **Dialog orchestrator** -- manages 10+ dialog visibility flags (`showSettings`, `showExitDialog`, `showWelcomeDialog`, etc.) and renders the corresponding `AlertDialog` or `ModalBottomSheet` composables.
3. **State collector** -- obtains `TrainingViewModel` via `viewModel()`, collects `uiState` via `collectAsState()`, and passes the state object (or derived values) down to every screen composable.
4. **Event dispatcher** -- wires user interactions (button clicks, swipes, voice launches) to ViewModel method calls (`vm::submitAnswer`, `vm::selectLesson`, etc.).
5. **Lifecycle bridge** -- notifies the ViewModel of screen changes via `LaunchedEffect(screen) { vm.onScreenChanged(screen.name) }`.

### 7.1.2 Connection to TrainingViewModel

```
GrammarMateApp composable
    |
    +-- vm: TrainingViewModel = viewModel()
    +-- state: TrainingUiState = vm.uiState.collectAsState()
    |
    +-- Passes state as read-only to child composables
    +-- Passes ViewModel method references as callbacks
    +-- Never mutates state directly
```

The router does **not** create any other ViewModel except the two drill ViewModels (`VerbDrillViewModel`, `VocabDrillViewModel`) which are created lazily when their respective screens are displayed.

### 7.1.3 Stateless Rendering Pattern

GrammarMateApp holds only UI-local state:

| Variable | Type | Purpose |
|----------|------|---------|
| `screen` | `AppScreen` | Current active screen |
| `previousScreen` | `AppScreen` | Screen to return to from LADDER/Settings |
| `showSettings` | `Boolean` | Settings bottom sheet visibility |
| `showExitDialog` | `Boolean` | Training exit confirmation |
| `showWelcomeDialog` | `Boolean` | First-launch name prompt |
| `showDailyResumeDialog` | `Boolean` | Resume vs. repeat daily practice |
| `showTtsDownloadDialog` | `Boolean` | TTS model download prompt |
| `pendingDailyLevel` | `Int` | Daily practice level awaiting start |
| `isLoadingDaily` | `Boolean` | Loading overlay for daily practice |
| `lastFinishedToken` | `Any?` | Token to detect sub-lesson completion |
| `lastBossFinishedToken` | `Any?` | Token to detect boss completion |

All domain state lives in `TrainingUiState` owned by `TrainingViewModel`. GrammarMateApp never writes to domain state -- it only reads and dispatches.

---

## 7.2 Screen Enum (AppScreen)

```kotlin
private enum class AppScreen {
    HOME,
    LESSON,
    ELITE,
    VOCAB,
    DAILY_PRACTICE,
    STORY,
    TRAINING,
    LADDER,
    VERB_DRILL,
    VOCAB_DRILL
}
```

### 7.2.1 Screen Values

| Value | Rendering Composable | Purpose |
|-------|---------------------|---------|
| `HOME` | `HomeScreen()` | Main dashboard. Shows user avatar, language selector, lesson grid, drill tiles, daily practice entry. |
| `LESSON` | `LessonRoadmapScreen()` | Sub-lesson selector for a specific lesson. Shows exercise tiles, boss tiles, drill tile, progress bar. |
| `TRAINING` | `TrainingScreen()` | Active card-based practice session. Displays prompt, answer box, result block, navigation row. |
| `DAILY_PRACTICE` | `DailyPracticeScreen()` | Daily practice session with 3 blocks: sentence translation, vocab flashcard, verb conjugation. |
| `STORY` | `StoryQuizScreen()` | Story quiz with multiple-choice questions (check-in / check-out phases). |
| `LADDER` | `LadderScreen()` | Interval ladder overview -- shows all lessons with their spaced repetition status. |
| `VERB_DRILL` | `VerbDrillScreen()` | Standalone verb conjugation drill with its own `VerbDrillViewModel`. |
| `VOCAB_DRILL` | `VocabDrillScreen()` | Standalone vocab flashcard drill with its own `VocabDrillViewModel`. |
| `ELITE` | *(redirects to HOME)* | **Deprecated.** Kept for backward compatibility with saved state. Redirects to `HOME` immediately. |
| `VOCAB` | *(redirects to HOME)* | **Deprecated.** Kept for backward compatibility with saved state. Redirects to `HOME` immediately. |

### 7.2.2 Backward Compatibility Notes

`ELITE` and `VOCAB` enum values are retained because users may have persisted `currentScreen: "ELITE"` or `currentScreen: "VOCAB"` in their saved state. The `parseScreen()` function handles unknown values by falling back to `HOME`, and the `when` block for `ELITE` / `VOCAB` immediately redirects to `HOME`:

```kotlin
AppScreen.ELITE -> { screen = AppScreen.HOME }
AppScreen.VOCAB -> { screen = AppScreen.HOME }
```

Removing these enum values would cause `IllegalArgumentException` on state restoration for users who last used these screens. They must not be removed.

### 7.2.3 Screen Parsing

```kotlin
private fun parseScreen(name: String): AppScreen {
    return try { AppScreen.valueOf(name) } catch (_: IllegalArgumentException) { AppScreen.HOME }
}
```

Initial screen is derived from `state.initialScreen` (a string persisted by the ViewModel). If parsing fails, defaults to `HOME`.

---

## 7.3 Navigation Flow

### 7.3.1 State Machine

```
HOME <--> LESSON <--> TRAINING
  |                   |
  |        +----------+----------+
  |        |          |          |
  +------> |    STORY |   (boss | sub-lesson finish
  |        |          |  tokens)|
  |        +----------+----------+
  |
  +------> DAILY_PRACTICE --> HOME
  |
  +------> VERB_DRILL --> HOME
  |
  +------> VOCAB_DRILL --> HOME
  |
  +------> LADDER --> previousScreen
```

### 7.3.2 Forward Transitions

| From | To | Trigger |
|------|----|---------|
| HOME | LESSON | User taps a lesson tile or "Continue Learning" button |
| HOME | LESSON | User taps primary action card |
| HOME | DAILY_PRACTICE | User taps "Daily Practice" tile; starts via `vm.startDailyPractice(level)` |
| HOME | VERB_DRILL | User taps "Verb Drill" tile (only if `hasVerbDrill == true`) |
| HOME | VOCAB_DRILL | User taps "Flashcards" tile (only if `hasVocabDrill == true`) |
| LESSON | TRAINING | User taps sub-lesson tile, boss tile, or drill tile |
| TRAINING | LESSON | Sub-lesson finish token changes (`subLessonFinishedToken`) |
| TRAINING | LESSON | Boss finish token changes (`bossFinishedToken`) |
| TRAINING | LESSON | User confirms exit via exit dialog |
| LESSON | STORY | *(currently unused -- StoryCheckIn/StoryCheckOut are rendered as no-ops in the roadmap)* |
| STORY | LESSON | Story completed or closed |
| LADDER | previousScreen | Back button pressed |
| DAILY_PRACTICE | HOME | Exit or complete daily session |
| VERB_DRILL | HOME | Back button |
| VOCAB_DRILL | HOME | Back button |

### 7.3.3 BackHandler Behavior

Back handling is registered per-screen. When `showSettings == true`, all BackHandlers are disabled (the settings sheet captures back):

| Screen | BackHandler Enabled | Back Action |
|--------|---------------------|-------------|
| TRAINING | `screen == TRAINING && !showSettings` | Shows exit confirmation dialog (`showExitDialog = true`) |
| LESSON | `screen == LESSON && !showSettings` | Navigate to HOME |
| DAILY_PRACTICE | `screen == DAILY_PRACTICE && !showSettings` | Navigate to HOME |
| STORY | `screen == STORY && !showSettings` | Navigate to LESSON |
| LADDER | `screen == LADDER && !showSettings` | Navigate to `previousScreen`; if `previousScreen == TRAINING`, also call `vm.resumeFromSettings()` |
| VERB_DRILL | `screen == VERB_DRILL && !showSettings` | Navigate to HOME |
| VOCAB_DRILL | `screen == VOCAB_DRILL && !showSettings` | Navigate to HOME; also call `vm.refreshVocabMasteryCount()` |
| HOME | Not registered | System default (minimize / exit app) |

### 7.3.4 Token-Based Screen Transitions

Two token-based transitions occur when `screen == TRAINING`:

1. **Sub-lesson finish** -- When `state.subLessonFinishedToken` differs from `lastFinishedToken.value`, the router sets `screen = LESSON`.
2. **Boss finish** -- When `state.bossFinishedToken` differs from `lastBossFinishedToken.value`, the router sets `screen = LESSON`.

These tokens are ViewModel-generated monotonically increasing values that signal session completion without requiring the ViewModel to know about the router's screen state.

### 7.3.5 State Restoration

- `state.initialScreen` is parsed at compose entry via `parseScreen(state.initialScreen)`.
- The ViewModel persists the current screen name via `vm.onScreenChanged(screen.name)` (called in `LaunchedEffect(screen)`).
- ELITE and VOCAB values are redirected to HOME on restoration (see 7.2.2).
- Invalid or unknown screen names fall back to HOME.

### 7.3.6 Settings Sheet and previousScreen

When the user opens Settings from any screen, `previousScreen` is saved. When Settings is dismissed:

- If `previousScreen == TRAINING && state.currentCard != null`, calls `vm.resumeFromSettings()`.
- The Ladder screen uses `previousScreen` for its back navigation.

---

## 7.4 Dialog Orchestration

GrammarMateApp manages the following dialogs, all controlled by local boolean flags:

### 7.4.1 Dialog Inventory

| Dialog | Flag | Type | Trigger Condition |
|--------|------|------|-------------------|
| Settings Sheet | `showSettings` | `ModalBottomSheet` | User taps gear icon on HOME or TRAINING |
| Exit Confirmation | `showExitDialog` | `AlertDialog` | Back press during TRAINING or "Stop" button |
| Welcome (Name Prompt) | `showWelcomeDialog` | `AlertDialog` | `userName == "GrammarMateUser"` on non-HOME screen |
| TTS Download | `showTtsDownloadDialog` | `AlertDialog` | User taps speaker button when TTS model not ready |
| TTS Metered Network | `state.ttsMeteredNetwork` | `AlertDialog` | TTS download attempted on metered connection |
| ASR Metered Network | `state.asrMeteredNetwork` | `AlertDialog` | ASR download attempted on metered connection |
| Daily Resume | `showDailyResumeDialog` | `AlertDialog` | User taps Daily Practice when resumable session exists |
| Story Error | `state.storyErrorMessage != null` | `AlertDialog` | Story loading or parsing error |
| Boss Error | `state.bossErrorMessage != null` | `AlertDialog` | Boss session error |
| Boss Reward | `state.bossRewardMessage != null` | `AlertDialog` | Boss completed; shows BRONZE/SILVER/GOLD reward |
| Streak Message | `state.streakMessage != null` | `AlertDialog` | Daily streak milestone reached |
| Drill Start | `state.drillShowStartDialog` | `AlertDialog` (DrillStartDialog) | User taps drill tile in LessonRoadmap |
| Method Explanation | `showMethod` (inside HomeScreen) | `AlertDialog` | User taps "How This Training Works" button |
| Locked Lesson Hint | `showLockedLessonHint` (inside HomeScreen) | `AlertDialog` | User taps a locked lesson tile with no lesson ID |
| Early Start Lesson | `earlyStartLessonId` (inside HomeScreen) | `AlertDialog` | User taps a locked lesson tile that has a lesson ID |
| Early Start Sub-Lesson | `earlyStartSubLessonIndex` (inside LessonRoadmapScreen) | `AlertDialog` | User taps a locked sub-lesson tile |
| Boss Locked | `bossLockedMessage` (inside LessonRoadmapScreen) | `AlertDialog` | User taps boss tile when < 15 exercises completed |
| Export Bad Sentences | `exportMessage` (inside TrainingScreen AnswerBox) | `AlertDialog` | After exporting bad sentences file |
| Daily Loading Overlay | `isLoadingDaily` | `Dialog` (non-cancelable) | Daily practice session loading in progress |

### 7.4.2 Dialog Dismissal Behavior

| Dialog | Dismiss Actions |
|--------|-----------------|
| Settings Sheet | Back press, swipe down, tap outside. Calls `vm.resumeFromSettings()` if returning to TRAINING. |
| Exit Confirmation | "Cancel" button dismisses. "Exit" button calls `vm.finishSession()` / `vm.finishBoss()` / `vm.exitDrillMode()` depending on session type. |
| Welcome | "Continue" sets name via `vm.updateUserName()`. "Skip" sets default "GrammarMateUser". Not dismissible by tapping outside. |
| TTS Download | "Cancel" dismisses. "Download" starts download. During download, shows "Continue in background" which dismisses but keeps download active. Auto-closes when download completes and auto-plays TTS. |
| TTS Metered Network | "Download anyway" confirms. "Cancel" dismisses both metered warning and TTS download dialog. |
| ASR Metered Network | "Download anyway" confirms. "Cancel" dismisses both metered warning and ASR download dialog. |
| Daily Resume | "Continue" starts new daily session with same level. "Repeat" restarts from scratch. |
| Story/Boss Error | "OK" dismisses, calls `vm.clearStoryError()` or `vm.clearBossError()`. |
| Boss Reward | "OK" dismisses, calls `vm.clearBossRewardMessage()`. |
| Streak | "Continue" dismisses, calls `vm.dismissStreakMessage()`. |
| Drill Start | "Start" or "Continue" (depending on existing progress). "Cancel" dismisses. "Start Fresh" resets progress. |
| Method Explanation | "OK" dismisses. |
| Locked Lesson Hint | "OK" dismisses. |
| Early Start (Lesson) | "Yes" navigates to lesson. "No" dismisses. |
| Early Start (Sub-Lesson) | "Yes" starts sub-lesson early. "No" dismisses. |
| Boss Locked | "OK" dismisses. |
| Export Bad Sentences | "OK" dismisses. |
| Daily Loading | Non-cancelable. Dismisses when `isLoadingDaily` becomes false after session starts. |

### 7.4.3 Dialog Priority and Stacking

Dialogs are rendered in code order within the composable tree. Multiple dialogs can technically be visible simultaneously (e.g., Welcome + TTS Download). However, in practice:

- `showWelcomeDialog` is triggered by a `LaunchedEffect` that checks `userName == "GrammarMateUser"` when not on HOME, so it fires on first non-HOME navigation.
- State-driven dialogs (error messages, rewards, streak) are mutually exclusive in practice because the ViewModel clears one before showing the next.
- The Settings sheet (`ModalBottomSheet`) overlays the entire content when visible, preventing interaction with underlying screen elements.

---

## 7.5 State Rendering

### 7.5.1 State Collection

```kotlin
val vm: TrainingViewModel = viewModel()
val state by vm.uiState.collectAsState()
```

The router obtains a single `TrainingViewModel` instance and subscribes to its `StateFlow<TrainingUiState>`. Every recomposition reads the latest state.

### 7.5.2 State Distribution

The `state` object is passed **whole** to most screen composables:

- `HomeScreen(state = state, ...)`
- `LessonRoadmapScreen(state = state, ...)`
- `TrainingScreen(state = state, ...)`
- `LadderScreen(state = state, ...)`

For `DailyPracticeScreen`, the router extracts derived values:

```kotlin
val dailyState = state.dailySession
val dailyTask = vm.getDailyCurrentTask()
val dailyProgress = vm.getDailyBlockProgress()
DailyPracticeScreen(state = dailyState, blockProgress = dailyProgress, ...)
```

For drill screens, the router creates separate ViewModels:

```kotlin
// VerbDrillScreen
val verbDrillVm = viewModel<VerbDrillViewModel>()
verbDrillVm.reloadForPack(activePackId)  // or reloadForLanguage(...)

// VocabDrillScreen
val vocabDrillVm = viewModel<VocabDrillViewModel>()
vocabDrillVm.reloadForPack(packId, state.selectedLanguageId)  // or reloadForLanguage(...)
```

### 7.5.3 Derived Values Computed in Router

The router computes several values from state before passing them down:

| Derived Value | Computation | Passed To |
|---------------|-------------|-----------|
| `hasVerbDrill` | `lessonStore.hasVerbDrill(activePackId, languageId)` | HomeScreen |
| `hasVocabDrill` | `lessonStore.hasVocabDrill(activePackId, languageId)` | HomeScreen |
| `onTtsSpeak` lambda | Checks TTS state, model readiness, triggers download or playback | TrainingScreen, DailyPracticeScreen |
| `calcBgDownloadProgress()` | Aggregates background TTS download states into 0-1 float | Persistent progress bar |

### 7.5.4 Persistent UI Elements

Two UI elements render on all screens regardless of the active screen:

1. **Background TTS download progress bar** -- An `AnimatedVisibility`-wrapped `LinearProgressIndicator` at the top of the `Column`. Visible when `state.bgTtsDownloading == true`. Shows aggregated download progress across all languages.

2. **Settings Sheet** -- A `ModalBottomSheet` that overlays everything when `showSettings == true`.

### 7.5.5 LaunchedEffects

| Effect | Trigger | Action |
|--------|---------|--------|
| Screen change notification | `screen` changes | `vm.onScreenChanged(screen.name)` |
| Welcome dialog trigger | `screen` or `userName` changes | Shows welcome dialog if not HOME and user has default name |
| Sub-lesson finish detection | `screen == TRAINING && token changed` | Navigates to LESSON |
| Boss finish detection | `screen == TRAINING && token changed` | Navigates to LESSON |
| Voice recognition auto-launch | `currentCard.id`, `inputMode`, `sessionState`, `voiceTriggerToken` change | Launches voice recognition activity when in VOICE mode |

---

## 7.6 User Interactions

### 7.6.1 HomeScreen Interactions

| Interaction | Callback | ViewModel Method |
|-------------|----------|------------------|
| Tap lesson tile | `onSelectLesson(lessonId)` | `vm.selectLesson(lessonId)` |
| Tap primary action card | `onPrimaryAction` | Sets `screen = LESSON` |
| Tap "Continue Learning" button | `onPrimaryAction` | Sets `screen = LESSON` |
| Tap language selector | `onSelectLanguage(languageId)` | `vm.selectLanguage(languageId)` |
| Tap Settings gear | `onOpenSettings` | `vm.pauseSession()`, sets `showSettings = true` |
| Tap "Daily Practice" tile | `onOpenElite` | Checks resumable session, starts daily practice via `vm.startDailyPractice(level)` |
| Tap "Verb Drill" tile | `onOpenVerbDrill` | Sets `screen = VERB_DRILL` |
| Tap "Flashcards" tile | `onOpenVocabDrill` | Sets `screen = VOCAB_DRILL` |
| Tap "How This Training Works" | Opens method dialog | Local dialog, no ViewModel call |
| Tap locked lesson tile | Shows early start dialog | `onSelectLesson` on confirm |
| Tap empty locked slot | Shows locked hint dialog | No ViewModel call |

### 7.6.2 LessonRoadmapScreen Interactions

| Interaction | Callback | ViewModel Method |
|-------------|----------|------------------|
| Tap sub-lesson tile | `onStartSubLesson(index)` | `vm.selectSubLesson(index)` |
| Tap "Start/Continue Lesson" button | `onStartSubLesson(currentIndex)` | `vm.selectSubLesson(currentIndex)` |
| Tap boss "Review" tile | `onStartBossLesson` | `vm.startBossLesson()` |
| Tap boss "Mega" tile | `onStartBossMega` | `vm.startBossMega()` |
| Tap drill tile | `onDrillStart` | `vm.showDrillStartDialog(lessonId)` |
| Tap locked sub-lesson tile | Shows early start dialog | `onStartSubLesson` on confirm |
| Tap locked boss tile | Shows boss locked dialog | No ViewModel call |
| Back button | `onBack` | Sets `screen = HOME` |

### 7.6.3 TrainingScreen Interactions

| Interaction | Callback | ViewModel Method |
|-------------|----------|------------------|
| Type in answer field | `onInputChange(text)` | `vm.onInputChanged(text)` |
| Tap "Check" button | `onSubmit()` | `vm.submitAnswer()` |
| Tap "Prev" | `onPrev()` | `vm.prevCard()` |
| Tap "Next" | `onNext(false)` | `vm.nextCard(false)` |
| Tap Pause/Play | `onTogglePause()` | `vm.togglePause()` |
| Tap Stop/Exit | `onRequestExit()` | Shows exit dialog |
| Tap Settings | `onShowSettings()` | Sets `showSettings = true` |
| Tap mode button (Lesson/All/Mixed) | `onSelectMode(mode)` | `vm.selectMode(mode)` |
| Select lesson from dropdown | `onSelectLesson(id)` | `vm.selectLesson(id)` |
| Tap Voice input mode | `onSetInputMode(VOICE)` | `vm.setInputMode(VOICE)` |
| Tap Keyboard input mode | `onSetInputMode(KEYBOARD)` | `vm.setInputMode(KEYBOARD)` |
| Tap Word Bank input mode | `onSetInputMode(WORD_BANK)` | `vm.setInputMode(WORD_BANK)` |
| Tap "Show answer" | `onShowAnswer()` | `vm.showAnswer()` |
| Tap word in word bank | `onSelectWordFromBank(word)` | `vm.selectWordFromBank(word)` |
| Tap "Undo" in word bank | `onRemoveLastWord()` | `vm.removeLastSelectedWord()` |
| Tap TTS speaker | `onTtsSpeak` | `vm.onTtsSpeak(text, speed)` or `vm.stopTts()` |
| Tap Report/Flag | Opens report bottom sheet | Sheet-local actions |
| Flag bad sentence | `onFlagBadSentence()` | `vm.flagBadSentence()` |
| Unflag bad sentence | `onUnflagBadSentence()` | `vm.unflagBadSentence()` |
| Hide card | `onHideCard()` | `vm.hideCurrentCard()` |
| Export bad sentences | `onExportBadSentences()` | `vm.exportBadSentences()` |
| Copy card text | Clipboard copy | Local clipboard action |
| Voice recognition result | `speechLauncher` callback | `onInputChange(spoken)` then `onSubmit()` |

### 7.6.4 DailyPracticeScreen Interactions

| Interaction | Callback | ViewModel Method |
|-------------|----------|------------------|
| Submit sentence answer | `onSubmitSentence` | `vm.submitDailySentenceAnswer()` |
| Submit verb answer | `onSubmitVerb` | `vm.submitDailyVerbAnswer()` |
| Show sentence answer | `onShowSentenceAnswer` | `vm.getDailySentenceAnswer()` |
| Show verb answer | `onShowVerbAnswer` | `vm.getDailyVerbAnswer()` |
| Flip vocab card | `onFlipVocabCard` | No-op (tracked locally) |
| Rate vocab card | `onRateVocabCard(rating)` | `vm.rateVocabCard(rating)` |
| Persist verb card | `onPersistVerbProgress(card)` | `vm.persistDailyVerbProgress(card)` |
| Card practiced | `onCardPracticed(blockType)` | `vm.recordDailyCardPracticed(blockType)` |
| Advance task | `onAdvance` | `vm.advanceDailyTask()` |
| Advance block | `onAdvanceBlock` | `vm.advanceDailyBlock()` |
| Repeat block | `onRepeatBlock` | `vm.repeatDailyBlock()` |
| Speak text | `onSpeak(text)` | `vm.onTtsSpeak(text, speed)` |
| Stop TTS | `onStopTts` | `vm.stopTts()` |
| Exit daily | `onExit` | `vm.cancelDailySession()` |
| Complete daily | `onComplete` | `vm.cancelDailySession()` |

### 7.6.5 StoryQuizScreen Interactions

| Interaction | Callback | Action |
|-------------|----------|--------|
| Select answer option | Updates `selections` map | Local state |
| Tap "Check" | Validates selection, records result | Local state |
| Tap "Next" / "Finish" | Advances question or completes quiz | `vm.completeStory(phase, allCorrect)` |
| Tap "Prev" | Goes to previous question | Local state |
| Close story | `onClose` | `vm.completeStory(phase, false)` |

### 7.6.6 LadderScreen Interactions

| Interaction | Callback | Action |
|-------------|----------|--------|
| Back button | `onBack` | Returns to `previousScreen` |
| Scroll list | Built-in `LazyColumn` | Local scroll state |

### 7.6.7 VerbDrillScreen Interactions

| Interaction | Callback | Action |
|-------------|----------|--------|
| Back button | `onBack` | Sets `screen = HOME` |
| All drill interactions | Handled by `VerbDrillViewModel` | Separate ViewModel |

### 7.6.8 VocabDrillScreen Interactions

| Interaction | Callback | Action |
|-------------|----------|--------|
| Back button | `onBack` | `vm.refreshVocabMasteryCount()`, sets `screen = HOME` |
| All drill interactions | Handled by `VocabDrillViewModel` | Separate ViewModel |

### 7.6.9 Settings Sheet Interactions

| Interaction | ViewModel Method |
|-------------|------------------|
| Toggle test mode | `vm.toggleTestMode()` |
| Change TTS speed | `vm.setTtsSpeed(float)` |
| Change Russian text scale | `vm.setRuTextScale(float)` |
| Toggle offline ASR | `vm.setUseOfflineAsr(boolean)` |
| Start ASR download | `vm.startAsrDownload()` |
| Select language | `vm.selectLanguage(id)` |
| Select pack | `vm.selectPack(id)` |
| Add language | `vm.addLanguage(name)` |
| Import lesson pack (ZIP) | `vm.importLessonPack(uri)` |
| Import lesson (CSV) | `vm.importLesson(uri)` |
| Reset/Reload lesson | `vm.resetAndImportLesson(uri)` |
| Create empty lesson | `vm.createEmptyLesson(title)` |
| Delete all lessons | `vm.deleteAllLessons()` |
| Delete pack | `vm.deletePack(packId)` |
| Update vocab limit | `vm.updateVocabSprintLimit(int)` |
| Update user name | `vm.updateUserName(name)` |
| Save progress now | `vm.saveProgressNow()` |
| Restore from backup | `vm.restoreBackup(uri)` |
| Reset all progress | `vm.resetAllProgress()` |
| Open ladder | Sets `showSettings = false`, `screen = LADDER` |

---

## 7.7 Internal UI Components

GrammarMateApp defines the following private composables that are used exclusively within this file (not extracted to separate screen/component files):

### 7.7.1 Screen Composables

| Composable | Lines (approx.) | Description |
|------------|-----------------|-------------|
| `HomeScreen()` | ~250 | Home dashboard with lesson grid, drill tiles, daily practice entry |
| `LessonRoadmapScreen()` | ~220 | Sub-lesson grid with boss tiles, drill tile, progress indicators |
| `TrainingScreen()` | ~150 | Scaffold wrapping card prompt, answer box, result block, navigation |
| `StoryQuizScreen()` | ~150 | Multiple-choice story quiz with question navigation |
| `LadderScreen()` | ~60 | Lesson ladder table with interval status |

### 7.7.2 Tile and Card Composables

| Composable | Description |
|------------|-------------|
| `LessonTile()` | Single lesson grid tile with flower emoji, mastery %, lock state |
| `LessonRoadmapScreen` tiles | Sub-lesson tiles within LessonRoadmap showing type (NEW/MIX) and completion |
| `BossTile()` | Boss battle tile with reward badge (bronze/silver/gold) |
| `DrillTile()` | Drill entry tile in lesson roadmap |
| `VerbDrillEntryTile()` | Verb drill entry tile on home screen |
| `VocabDrillEntryTile()` | Vocab flashcard entry tile on home screen with mastery count |
| `DailyPracticeEntryTile()` | Daily practice entry tile on home screen |

### 7.7.3 Training UI Components

| Composable | Description |
|------------|-------------|
| `CardPrompt()` | Card displaying the Russian prompt with TTS speaker button |
| `AnswerBox()` | Input field, word bank UI, input mode selector, report button |
| `ResultBlock()` | Correct/Incorrect indicator with answer text and TTS button |
| `NavigationRow()` | Prev, Pause/Play, Stop, Next navigation buttons |
| `ModeSelector()` | Training mode selector (Lesson/All Sequential/All Mixed) |
| `DrillProgressRow()` | Progress bar with speedometer circle for drill sessions |
| `HeaderStats()` | Session statistics (progress %, time, speed) |
| `TtsSpeakerButton()` | TTS button with state-dependent icon (playing/initializing/error/idle) |
| `AsrStatusIndicator()` | Pulsing red dot (recording), spinner (recognizing), error text |
| `NavIconButton()` | Styled navigation button with bottom accent bar |

### 7.7.4 Settings and Selector Components

| Composable | Description |
|------------|-------------|
| `SettingsSheet()` | Full settings panel as ModalBottomSheet |
| `LanguageSelector()` | Language dropdown in home screen header |
| `LanguageLessonColumn()` | Language + pack selector in settings |
| `DropdownSelector()` | Generic dropdown with label |
| `ModeIconButton()` | Filled vs. outlined icon button for mode selection |

### 7.7.5 Dialog Composables

| Composable | Description |
|------------|-------------|
| `WelcomeDialog()` | First-launch name input dialog |
| `TtsDownloadDialog()` | TTS model download with progress |
| `MeteredNetworkDialog()` | TTS metered network warning |
| `AsrMeteredNetworkDialog()` | ASR metered network warning |
| `DrillStartDialog()` | Drill mode start/resume choice |

### 7.7.6 Helper Enums and Classes

| Type | Description |
|------|-------------|
| `AppScreen` | Screen routing enum (10 values) |
| `LessonTileState` | Tile visual state: SEED, SPROUT, FLOWER, LOCKED, UNLOCKED, EMPTY, VERB_DRILL |
| `LessonTileUi` | Data class for grid tile: index, lessonId, state |
| `RoadmapEntry` | Sealed class for lesson roadmap entries: Training, Drill, StoryCheckIn, StoryCheckOut, BossLesson, BossMega |
| `calcBgDownloadProgress()` | Aggregates multiple download states into 0-1 float |
| `getUserInitials()` | Extracts 2-letter initials from user name |
| `parseScreen()` | Converts string to AppScreen with fallback |
| `buildLessonTiles()` | Generates 12 lesson tiles with lock/unlock/flower logic |
| `buildRoadmapEntries()` | Generates roadmap entry list for current lesson |
| `formatTime()` | Milliseconds to MM:SS format |
| `speedPerMinute()` | Words per minute calculation |
| `launchVoiceRecognition()` | Creates and launches system speech recognition intent |

---

## 7.8 Data Flow Diagram

```
User Action
    |
    v
GrammarMateApp (composable)
    |
    +-- Local state update (screen, dialog flags)
    |       |
    |       v
    |   Recomposition triggered
    |
    +-- ViewModel method call (vm::something)
            |
            v
    TrainingViewModel
            |
            +-- updateState { ... }  -->  StateFlow emission
            +-- saveProgress()
            |
            v
    TrainingUiState updated
            |
            v
    GrammarMateApp recomposes (collectAsState)
            |
            v
    Screen composable receives new state
```

The cycle is:

1. User interacts with a composable (tap, type, voice).
2. GrammarMateApp updates local state OR calls a ViewModel method.
3. ViewModel processes the action, updates `TrainingUiState` via `updateState { }`.
4. `StateFlow` emits new state.
5. `collectAsState()` triggers recomposition of GrammarMateApp.
6. Current screen composable re-renders with new state.
7. Token-based effects (subLessonFinishedToken, bossFinishedToken) may trigger screen transitions.

---

## 7.9 Voice Recognition Integration

### 7.9.1 Activity Result Launchers

Three activity result launchers are registered in GrammarMateApp:

1. **`speechLauncher`** (StartActivityForResult) -- Launches `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`. On success, feeds spoken text to `onInputChange` and auto-submits via `onSubmit`.
2. **`audioPermissionLauncher`** (RequestPermission) -- Requests audio recording permission. Result handled by system.
3. **`importLauncher`** (OpenDocument) -- Opens file picker for CSV import. Calls `vm.importLesson(uri)`.
4. **`packImportLauncher`** (OpenDocument) -- Opens file picker for ZIP import. Calls `vm.importLessonPack(uri)`.
5. **`resetLauncher`** (OpenDocument) -- Opens file picker for reset/reload. Calls `vm.resetAndImportLesson(uri)`.
6. **`restoreBackupLauncher`** (OpenDocumentTree) -- Opens directory picker for backup restore. Calls `vm.restoreBackup(uri)`.

### 7.9.2 Voice Recognition Trigger

Voice recognition auto-launches via a `LaunchedEffect` that watches:
- `state.currentCard?.id` -- new card
- `state.inputMode` -- mode changes to VOICE
- `state.sessionState` -- session becomes active
- `state.voiceTriggerToken` -- explicit re-trigger from ViewModel

When all conditions are met (`inputMode == VOICE`, `sessionState == ACTIVE`, `currentCard != null`), the router:
1. Delays 200ms.
2. Calls `onVoicePromptStarted()`.
3. If offline ASR is enabled and model ready, calls `onStartOfflineRecognition()`.
4. Otherwise, calls `launchVoiceRecognition()` which launches the system `RecognizerIntent`.

### 7.9.3 Language Tag Mapping

```kotlin
val languageTag = when (languageId) {
    "it" -> "it-IT"
    else -> "en-US"
}
```

Only Italian and English are supported for system speech recognition. The prompt text is set to the card's Russian text.

---

## 7.10 TTS Integration at Router Level

### 7.10.1 TTS Speak Lambda

The router creates a shared `onTtsSpeak` lambda used by both TrainingScreen and DailyPracticeScreen:

```kotlin
val onTtsSpeak: () -> Unit = {
    if (state.ttsState == TtsState.SPEAKING) {
        vm.stopTts()                           // Stop if already speaking
    } else if (!state.ttsModelReady) {
        // Check background download state
        vm.setTtsDownloadStateFromBackground(bgState)
        showTtsDownloadDialog = true            // Show download dialog
    } else {
        val text = state.answerText ?: state.currentCard?.acceptedAnswers?.firstOrNull()
        if (text != null) vm.onTtsSpeak(text, speed = 0.67f)
    }
}
```

Behavior:
1. If TTS is currently speaking, stops it.
2. If the TTS model is not downloaded, checks for background download progress and shows the download dialog.
3. If the model is ready, speaks the answer text (or first accepted answer) at 0.67x speed.

### 7.10.2 TTS Download Auto-Play

When the TTS download dialog is visible and `state.ttsDownloadState` transitions to `DownloadState.Done`:
1. The dialog auto-closes.
2. `vm.dismissTtsDownloadDialog()` is called.
3. TTS auto-plays the answer text.

### 7.10.3 TTS Speed

The hardcoded speed in the router-level lambda is `0.67f`. The user-configurable TTS speed (`state.ttsSpeed`, range 0.5-1.5) is used in the Settings sheet and by DailyPracticeScreen's `onSpeak` callback. TrainingScreen uses the hardcoded value via the shared `onTtsSpeak` lambda.

---

## 7.11 Lesson Tile Logic

### 7.11.1 Tile State Determination

The `buildLessonTiles()` function generates exactly 12 tiles for the home screen grid. Each tile's state is determined by:

1. **EMPTY** -- No lesson exists at this index in the active pack.
2. **LOCKED** (closed lock) -- Lesson exists but all of the following are true: no progress on this lesson, previous lesson has no progress, and this is not the next lesson after the last one with progress.
3. **UNLOCKED** (open lock) -- This is the lesson immediately after the last lesson with any progress (`masteryPercent > 0`), or any lesson before that point whose previous lesson has progress.
4. **SEED / SPROUT / FLOWER** -- The lesson has progress (`masteryPercent > 0`); the actual emoji comes from `FlowerCalculator.getEmoji(flower.state)`.
5. **Test mode** -- All tiles show SEED state regardless of progress.

### 7.11.2 Early Start Feature

Locked tiles with a valid `lessonId` show an "early start" confirmation dialog. The user can choose to bypass the lock and proceed to the lesson. Locked tiles without a `lessonId` (empty slots beyond the pack's lesson count) show a "lesson locked" hint that cannot be bypassed.

---

## 7.12 Roadmap Entry Construction

The `buildRoadmapEntries()` function generates the list of entries shown in `LessonRoadmapScreen`:

1. **Drill** (optional) -- Added first if the current lesson has drill cards (`currentLesson.drillCards.isNotEmpty()`).
2. **Training entries** -- One per sub-lesson type in the current 15-item cycle window.
3. **BossLesson** -- Always added.
4. **BossMega** -- Added only if `lessonIndex > 0` (lessons after the first have a mega boss).
5. **StoryCheckIn / StoryCheckOut** -- Kept in the sealed class for backward compatibility but rendered as no-ops.

Sub-lessons are displayed in cycles of 15. The current cycle is calculated as `completed / 15`, and only the current cycle's sub-lessons are shown.

---

## 7.13 Drill ViewModels

### 7.13.1 VerbDrillViewModel

Created via `viewModel<VerbDrillViewModel>()` when `screen == VERB_DRILL`. The router calls:
- `reloadForPack(activePackId)` if an active pack exists.
- `reloadForLanguage(selectedLanguageId)` as fallback.

This ViewModel is separate from `TrainingViewModel` and manages its own state for verb conjugation drills.

### 7.13.2 VocabDrillViewModel

Created via `viewModel<VocabDrillViewModel>()` when `screen == VOCAB_DRILL`. The router calls:
- `reloadForPack(packId, selectedLanguageId)` if an active pack exists.
- `reloadForLanguage(selectedLanguageId)` as fallback.

On back navigation, the router calls `vm.refreshVocabMasteryCount()` to update the mastery count shown on the home screen tile.

These are the **only two ViewModels** besides `TrainingViewModel` in the application.

---

## 7.14 Drill Start Flow

When the user taps the drill tile in the lesson roadmap:

1. Router calls `vm.showDrillStartDialog(lessonId)`.
2. ViewModel sets `state.drillShowStartDialog = true` and `state.drillHasProgress` accordingly.
3. Router renders `DrillStartDialog` with two or three options:
   - "Start" (no progress) or "Continue" (has progress) -- calls `vm.startDrill(resume = true/false)`.
   - "Start Fresh" (only if has progress) -- calls `vm.startDrill(resume = false)`.
   - "Cancel" -- calls `vm.dismissDrillDialog()`.
4. On start/continue, the router sets `screen = TRAINING` and the drill renders within the TrainingScreen using `state.isDrillMode` flag.

---

## 7.15 Daily Practice Flow

### 7.15.1 Start Flow

1. User taps "Daily Practice" tile on HOME.
2. Router computes `level = lessonIndex + 1` for the selected lesson.
3. Checks `vm.hasResumableDailySession()`:
   - If resumable: shows `showDailyResumeDialog` with "Continue" (new cards) or "Repeat" (same cards).
   - If not resumable: launches directly.
4. Both paths set `isLoadingDaily = true`, show a non-cancelable loading dialog, and call `vm.startDailyPractice(level)` or `vm.repeatDailyPractice(level)` on `Dispatchers.IO`.
5. On success, sets `screen = DAILY_PRACTICE` and clears loading state.

### 7.15.2 Daily Practice Session

The `DailyPracticeScreen` composable receives:
- `state` from `state.dailySession`
- `blockProgress` from `vm.getDailyBlockProgress()`
- `currentTask` from `vm.getDailyCurrentTask()`
- `ttsState` from main state for TTS button rendering

All interactions are forwarded to `TrainingViewModel` methods with `daily` prefix.

### 7.15.3 Exit/Complete

Both exit and complete call `vm.cancelDailySession()` and navigate to HOME.

---

## 7.16 Word Bank UI

The word bank is rendered within `AnswerBox()` when `inputMode == WORD_BANK && wordBankWords.isNotEmpty()`:

1. Displays all words as `FilterChip`s in a `FlowRow`.
2. Tracks availability by counting word instances -- a word is "fully used" when all its occurrences in the bank have been selected.
3. Shows "Selected: N / M" counter with an "Undo" button that removes the last selected word.
4. Word selection calls `vm.selectWordFromBank(word)`, undo calls `vm.removeLastSelectedWord()`.

---

## 7.17 Report/Flag Bottom Sheet

Within `AnswerBox()`, a "Report sentence" button opens a `ModalBottomSheet` with these actions:

1. **Flag/Unflag bad sentence** -- Toggles bad sentence status via `vm.flagBadSentence()` / `vm.unflagBadSentence()`.
2. **Hide card** -- Removes card from future lessons via `vm.hideCurrentCard()`.
3. **Export bad sentences** -- Exports flagged sentences to file via `vm.exportBadSentences()`, shows result in alert.
4. **Copy text** -- Copies card ID, source, and target to clipboard using `ClipboardManager`.

The bottom sheet shows the current card's Russian prompt at the top for context.

---

## 7.18 File Size and Decomposition Status

GrammarMateApp.kt is approximately 3,550 lines, significantly exceeding the 1,000-line limit for screen files specified in CLAUDE.md. The following internal composables are candidates for extraction to `ui/screens/` and `ui/components/`:

**Screen composables to extract to `ui/screens/`:**
- `HomeScreen()` (~250 lines)
- `LessonRoadmapScreen()` (~220 lines)
- `TrainingScreen()` (~150 lines)
- `StoryQuizScreen()` (~150 lines)
- `LadderScreen()` (~60 lines)

**Shared components to extract to `ui/components/`:**
- `SettingsSheet()` (~430 lines)
- `WelcomeDialog()`
- `TtsDownloadDialog()`, `MeteredNetworkDialog()`, `AsrMeteredNetworkDialog()`
- `DrillStartDialog()`
- `CardPrompt()`, `AnswerBox()`, `ResultBlock()`, `NavigationRow()`
- `TtsSpeakerButton()`, `AsrStatusIndicator()`

Extraction must maintain the rule that GrammarMateApp.kt contains only the `GrammarMateApp()` composable (routing + dialog orchestration), the `AppScreen` enum, and dialog state management.
