# Pomodoro Timer — Feature Design Spec

**Date:** 2026-05-16
**Status:** Draft
**Scope:** Focused training sessions with countdown timer, session stats, and Anki-style difficulty ratings

---

## 1. Overview

**Feature:** Pomodoro-style focused training sessions. User picks a time (5/15/20/custom min), practices cards with a visible countdown, rates difficulty after each card (Anki-style), and receives a session summary when the timer expires.

**Purpose:** Structured, time-boxed practice that helps learners maintain focus and build habits. The timer creates urgency, the difficulty ratings build metacognition, and the summary reinforces the reward loop. Integrates with the existing fire streak system.

**Why Pomodoro fits GrammarMate:** The app teaches automatic grammar pattern skills through repetition. Focused, timed sessions match the "deliberate practice" methodology — short bursts of high-concentration effort with measurable outcomes.

### Existing HomeScreen elements (no changes needed)

The HomeScreen already has all header elements the user mentioned:
- **Avatar** (HS-01): 40dp circle with initials, opens ProfileStatsPopup
- **Language selector** (HS-03): TextButton with language code, opens DropdownMenu
- **Settings** (HS-04): Gear icon, opens Settings dialog

**Only new element:** Tomato icon button (inserted between Language selector and Settings).

### MVP Scope

| In scope | Out of scope |
|----------|-------------|
| Tomato icon on HomeScreen header | Pomodoro for Daily Practice / Verb Drill / Vocab Drill |
| Timer selection (5/15/20/custom min) | Break timer (work phase only) |
| Countdown timer overlay during training | Cross-session Pomodoro history |
| Post-card difficulty rating (Anki-style) | Background timer service / notifications |
| Session summary screen | Pomodoro-specific streak (reuses existing fire streak) |
| Fire streak integration | Custom alarm sounds |

---

## 2. User Journey

### Happy Path

```
HomeScreen → Tap 🍅 → Bottom sheet with time options → Select 20 min → Start
→ Training with timer banner → Answer cards → Rate difficulty → Next card
→ Timer hits 0:00 → Chime → Summary overlay → Done → HomeScreen
```

### Step-by-step

**Step 1 — HomeScreen**: User sees tomato icon in header row between language selector and settings gear.

**Step 2 — Tap tomato**: ModalBottomSheet slides up with timer selection UI. Three preset time cards: Quick (5 min), Focus (15 min), Classic (20 min — default selected). Custom time stepper below. Last selection remembered.

**Step 3 — Select time, tap Start**: App navigates to training screen. The next unfinished sub-lesson starts. PomodoroState becomes active with selected duration.

**Step 4 — During training**:
- Timer banner shows at top of training screen: countdown MM:SS + cards shown + success rate + pause button
- Training proceeds normally (voice/keyboard/word bank)
- After each card result (correct or incorrect), difficulty rating chips appear
- User taps Again/Hard/Good/Easy, which advances to next card
- If no rating after 3 seconds, "Good" auto-selected

**Step 5 — Timer expires**:
- System notification sound via `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)`
- Current card result is finalized
- Training session pauses
- Full-screen summary overlay appears

**Step 6 — Summary screen**:
- Circular progress ring showing time completed
- Stats: cards shown, correct count, incorrect count, success rate, WPM
- Difficulty breakdown as horizontal bars
- Fire streak earned (if session qualifies)
- "Done" button → HomeScreen

### Edge cases

| Situation | Behavior |
|-----------|----------|
| User exits mid-Pomodoro | Confirmation dialog "End Pomodoro session?" — stats discarded on confirm |
| All sub-lessons done before timer | Summary shows early completion, remaining time not counted |
| App minimized / goes to background | Timer pauses. Resumes on foreground. |
| User pauses training | Timer pauses too. Resume continues both. |
| No unfinished lessons | Show message "All lessons complete! Great work." + "Start" navigates to review mode |
| Phone call / interruption | Timer pauses on lifecycle stop event |
| Pomodoro active, user navigates away | Confirmation dialog. On confirm: Pomodoro cancelled, return to HomeScreen |
| Timer hits 0 mid-card | Card result is counted, then summary shows |

---

## 3. Data Model

### New types (in `Models.kt`)

```kotlin
enum class PomodoroPreset(val minutes: Int, val label: String) {
    QUICK(5, "Quick"),
    FOCUS(15, "Focus"),
    CLASSIC(20, "Classic")
}

enum class CardDifficultyRating {
    AGAIN,   // Didn't know it, need to repeat
    HARD,    // Struggled but got it right
    GOOD,    // Knew it comfortably
    EASY     // Knew it instantly, too easy
}

data class PomodoroSessionStats(
    val cardsShown: Int = 0,
    val cardsCorrect: Int = 0,
    val cardsIncorrect: Int = 0,
    val difficultyRatings: Map<CardDifficultyRating, Int> = emptyMap(),
    val wordsPerMinute: Double = 0.0,
    val durationMinutes: Int = 0,
    val completedAtMs: Long = 0
)

data class PomodoroState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isComplete: Boolean = false,
    val selectedDurationMinutes: Int = 20,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val stats: PomodoroSessionStats = PomodoroSessionStats(),
    val showRatingPrompt: Boolean = false,
    val showExitConfirm: Boolean = false
)
```

### Integration into existing state

Add `pomodoro: PomodoroState = PomodoroState()` to `TrainingUiState`.

### Persistence

| Data | Storage | Format |
|------|---------|--------|
| Last selected duration | `grammarmate/pomodoro_settings.yaml` | `lastDurationMinutes: 20` |
| Session stats | Ephemeral — only live during session | Not persisted |
| Fire/streak | Existing `StreakStore` via `recordPracticeTypeCompletion()` | Existing YAML |

---

## 4. UI Design

### 4.1 Tomato Icon (HomeScreen Header)

**Position**: Right side of header Row, between LanguageSelector (HS-03) and Settings gear (HS-04).

Layout change: `[Avatar + Name]  ...  [Language] [🍅 Tomato] [⚙️ Settings]`

- Custom vector drawable: CC0 tomato SVG from SVG Repo, converted to Android Vector Drawable
- 24dp icon, `IconButton` wrapper
- Tint: `MaterialTheme.colorScheme.primary`
- Content description: "Pomodoro Timer"
- onClick: toggles `showPomodoroSheet = true`

### 4.2 Pomodoro Selector Bottom Sheet

```
┌─────────────────────────────────────┐
│              🍅                      │
│       Pomodoro Training             │
│    Focus. Practice. Grow.           │
│                                     │
│   ┌───────┐ ┌───────┐ ┌────────┐   │
│   │ 5 min │ │15 min │ │ 20 min │   │
│   │ Quick │ │ Focus │ │Classic │   │
│   └───────┘ └───────┘ └────────┘   │
│                                     │
│    Custom:  [ − ]  25  [ + ] min   │
│                                     │
│   ┌──────────────────────────────┐  │
│   │          🍅 Start            │  │
│   └──────────────────────────────┘  │
└─────────────────────────────────────┘
```

- `ModalBottomSheet` with `sheetMaxWidth = 400.dp`
- Tomato icon 48sp at top (same vector drawable)
- Three preset cards in a Row: selectable, selected = primary container fill + border
- Custom stepper: `OutlinedIconButton` with +/- , value in center, range 1-60 min
- "Start" `FilledButton` with tomato icon, full width
- Duration defaults to last selection (loaded from settings)

### 4.3 Timer Banner (Training Screen Top)

```
┌──────────────────────────────────────────────┐
│  🍅  18:42    📊 12 cards    ✅ 83%    ⏸   │
└──────────────────────────────────────────────┘
```

- `Card` at top of TrainingScreen, above TrainingCardSession
- 48dp height, warm tomato-tinted background `Color(0xFFFFEBEE)` (light red)
- Left section: tomato icon 16dp + countdown "MM:SS" in `fontFeatureSettings = "tnum"` (monospace digits)
- Center section: "N cards" text
- Right section: success rate percentage + pause/play icon button
- Visible only when `pomodoro.isActive && !pomodoro.isComplete`

### 4.4 Difficulty Rating Row

Shown after each card result during Pomodoro sessions. Positioned below the answer result area.

```
        How was it?
   [🔴 Again] [🟡 Hard] [🟢 Good] [⚡ Easy]
```

- Row of 4 `FilterChip` composables
- Colors:
  - AGAIN: `Color(0xFFFFCDD2)` (red tint)
  - HARD: `Color(0xFFFFE0B2)` (amber tint)
  - GOOD: `Color(0xFFC8E6C9)` (green tint)
  - EASY: `Color(0xFFBBDEFB)` (blue tint)
- Selected chip gets a border + darker fill
- Auto-selects GOOD after 3 seconds via `LaunchedEffect`
- On selection: records rating, advances to next card

### 4.5 Session Summary Screen

Full-screen overlay replacing the training content when `pomodoro.isComplete`.

```
┌──────────────────────────────────┐
│                                  │
│            🎉                    │
│      Session Complete!           │
│                                  │
│     ┌──────────────────┐        │
│     │   ╭──────────╮   │        │
│     │   │  20:00   │   │        │
│     │   │   100%   │   │        │
│     │   ╰──────────╯   │        │
│     └──────────────────┘        │
│                                  │
│    ┌──────────┐ ┌──────────┐    │
│    │ 📊 23    │ │ ✅ 87%   │    │
│    │ cards    │ │ correct  │    │
│    └──────────┘ └──────────┘    │
│    ┌──────────┐ ┌──────────┐    │
│    │ 🗣️ 45    │ │ 🔥 3     │    │
│    │ WPM      │ │ fires    │    │
│    └──────────┘ └──────────┘    │
│                                  │
│    Difficulty breakdown:         │
│    Again  ████          2        │
│    Hard   ████████      5        │
│    Good   █████████████ 12       │
│    Easy   ████          4        │
│                                  │
│    🔥 7 day streak!             │
│                                  │
│   ┌──────────────────────────┐   │
│   │          Done            │   │
│   └──────────────────────────┘   │
└──────────────────────────────────┘
```

Components:
- **Circular progress ring** (Canvas): 120dp, shows time completed vs selected duration. Center: "MM:SS" + "% complete"
- **Stats grid** (2x2): cards shown, correct rate, WPM, fires earned
- **Difficulty bars**: horizontal `LinearProgressIndicator` per rating, proportional to total ratings
- **Streak indicator**: reuse `FireStreakIndicator` from HomeScreen
- **"Done" button**: full-width `FilledButton`, navigates to HomeScreen

---

## 5. Architecture

### 5.1 PomodoroHelper (new file: `feature/pomodoro/PomodoroHelper.kt`)

Follows the helper pattern: plain class implementing domain logic, takes `TrainingStateAccess` interface.

```kotlin
class PomodoroHelper(
    private val stateAccess: TrainingStateAccess,
    private val settingsStore: PomodoroSettingsStore
) {
    fun startPomodoro(durationMinutes: Int)
    fun pausePomodoro()
    fun resumePomodoro()
    fun cancelPomodoro()
    fun completePomodoro()
    fun recordDifficultyRating(rating: CardDifficultyRating)
    fun tick() // called every 1 second
    fun getLastSelectedDuration(): Int
}
```

- Timer coroutine runs in TrainingViewModel scope, ticks every 1 second
- `tick()` decrements `remainingSeconds`, checks for completion
- `completePomodoro()` calculates final stats from `CardSessionState`, sets `pomodoro.isComplete = true`
- `recordDifficultyRating()` updates `PomodoroSessionStats.difficultyRatings`

### 5.2 PomodoroSettingsStore (new file: `data/PomodoroSettingsStore.kt`)

Simple YAML store for last selected duration. Uses `AtomicFileWriter`.

```kotlin
class PomodoroSettingsStore(context: Context) {
    fun load(): Int // returns last duration, default 20
    fun save(durationMinutes: Int)
}
```

File: `grammarmate/pomodoro_settings.yaml`

### 5.3 Integration with TrainingViewModel

TrainingViewModel gets a `PomodoroHelper` instance (created in init block alongside other helpers).

New methods on TrainingViewModel:
- `startPomodoro(durationMinutes: Int)` — delegates to PomodoroHelper, starts training session
- `pausePomodoro()` / `resumePomodoro()` — delegates to PomodoroHelper
- `cancelPomodoro()` — delegates to PomodoroHelper, resets state
- `rateCardDifficulty(rating: CardDifficultyRating)` — delegates to PomodoroHelper
- `completePomodoroSession()` — called from PomodoroHelper when timer expires

The existing `activeTimeMs` timer tick (every 500ms) continues as-is. The Pomodoro countdown is a separate 1-second tick that only runs when `pomodoro.isActive`.

### 5.4 Screen rendering integration

**HomeScreen** (`HomeScreen.kt`):
- Add `showPomodoroSheet` local state
- Add tomato `IconButton` in header Row
- Render `PomodoroSelectorSheet` composable when `showPomodoroSheet`
- Callback: `onStartPomodoro: (Int) -> Unit` — added to HomeScreen parameters

**GrammarMateApp** (`GrammarMateApp.kt`):
- Wire `onStartPomodoro` callback to `vm.startPomodoro(durationMinutes)` + navigate to training

**TrainingScreen** (`TrainingScreen.kt` or wherever TrainingCardSession is rendered):
- When `state.pomodoro.isActive && !state.pomodoro.isComplete`: render `PomodoroTimerBanner` above `TrainingCardSession`
- When `state.pomodoro.isComplete`: render `PomodoroSummaryScreen` instead of `TrainingCardSession`
- Difficulty rating row rendered inside TrainingCardSession result area when `state.pomodoro.isActive && state.pomodoro.showRatingPrompt`

### 5.5 Fire streak integration

Pomodoro does NOT introduce a new PracticeType. The fire streak records through the existing mechanism:
- When Pomodoro wraps a regular training session → TRANSLATION fire on completion
- The session qualification check (`isSessionCompleted()`) still applies
- Pomodoro summary shows the fire if earned, or "Keep going!" if not

This keeps the fire system simple: 3 fires for Italian (TRANSLATION, VOCAB, VERB), unchanged.

---

## 6. Icon Asset

**Source:** CC0 tomato SVG from SVG Repo (public domain, no attribution required)
- URL: https://www.svgrepo.com/svg/489691/tomato
- Convert to Android Vector Drawable XML via Android Studio
- Place in `res/drawable/ic_tomato.xml`
- Usage: `Icon(painter = painterResource(R.drawable.ic_tomato), contentDescription = "Pomodoro")`

**Fallback:** If SVG conversion is problematic, use Material `Icons.Default.Timer` with a red tint as a temporary icon.

---

## 7. Acceptance Criteria

### UC-P1: Start Pomodoro from HomeScreen
- **Given** user is on HomeScreen
- **When** user taps tomato icon
- **Then** Pomodoro selector bottom sheet appears with 3 presets + custom option
- **And** last selected duration is pre-selected
- **When** user selects a duration and taps "Start"
- **Then** app navigates to training screen with timer banner visible and countdown running

### UC-P2: Timer Counts Down During Training
- **Given** Pomodoro session is active and not paused
- **When** 1 second elapses
- **Then** remaining time decreases by 1 second and banner updates
- **And** cards shown count updates after each card

### UC-P3: Rate Card Difficulty (Anki-style)
- **Given** Pomodoro is active and user just answered a card
- **When** answer result is shown
- **Then** difficulty rating chips appear (Again/Hard/Good/Easy)
- **When** user taps a rating OR 3 seconds pass
- **Then** rating is recorded and next card appears
- **When** 3 seconds pass without user input
- **Then** "Good" is auto-selected

### UC-P4: Timer Expires — Summary Screen
- **Given** Pomodoro timer reaches 0:00
- **When** timer expires
- **Then** training pauses, chime plays, summary screen appears
- **And** summary shows: time completed ring, cards shown, correct rate, WPM, difficulty breakdown, fire streak

### UC-P5: Fire Streak Earned
- **Given** Pomodoro session qualifies (correctCount >= sessionSize - badSentences)
- **When** summary screen is shown
- **Then** fire streak is recorded per existing TRANSLATION rules
- **And** summary shows fire earned

### UC-P6: Pause/Resume Pomodoro
- **Given** Pomodoro is active
- **When** user taps pause on timer banner
- **Then** timer and training both pause
- **When** user taps resume
- **Then** timer and training both resume

### UC-P7: Exit Pomodoro Early
- **Given** Pomodoro is active
- **When** user presses system back or navigates away
- **Then** confirmation dialog "End Pomodoro session?" appears
- **When** confirmed
- **Then** Pomodoro cancelled, stats discarded, navigate to HomeScreen

### UC-P8: App Backgrounded During Pomodoro
- **Given** Pomodoro is active and counting down
- **When** app goes to background (onStop)
- **Then** timer pauses
- **When** app returns to foreground (onStart)
- **Then** timer resumes from where it left off

### UC-P9: Session Completes Before Timer
- **Given** Pomodoro is active and user finishes all sub-lesson cards
- **When** session completes before timer expires
- **Then** summary screen appears with "Early completion!" message
- **And** remaining time is shown as unused

---

## 8. Files to Create/Modify

### New files
| File | Purpose |
|------|---------|
| `res/drawable/ic_tomato.xml` | Tomato vector drawable (CC0 SVG conversion) |
| `data/PomodoroSettingsStore.kt` | Last selected duration persistence |
| `feature/pomodoro/PomodoroHelper.kt` | Timer logic, stats, difficulty tracking |
| `ui/components/PomodoroTimerBanner.kt` | Countdown + stats banner during training |
| `ui/components/PomodoroSelectorSheet.kt` | Time selection bottom sheet |
| `ui/components/PomodoroSummaryScreen.kt` | Post-session summary overlay |
| `ui/components/DifficultyRatingRow.kt` | Anki-style rating chips |

### Modified files
| File | Change |
|------|--------|
| `data/Models.kt` | Add PomodoroState, PomodoroSessionStats, CardDifficultyRating, PomodoroPreset |
| `ui/screens/HomeScreen.kt` | Add tomato icon button + PomodoroSelectorSheet |
| `ui/TrainingViewModel.kt` | Add pomodoro state field, PomodoroHelper, timer coroutine, integration methods |
| `ui/screens/TrainingScreen.kt` | Render timer banner and summary overlay based on PomodoroState |
| `ui/GrammarMateApp.kt` | Wire onStartPomodoro callback from HomeScreen to ViewModel |

---

## 9. Design Decisions Log

| Decision | Chosen | Alternative | Rationale |
|----------|--------|-------------|-----------|
| Pomodoro mode vs overlay | Overlay on existing training | New PomodoroScreen | Reuses all existing training infrastructure, minimal blast radius |
| New PracticeType | No — reuse existing TRANSLATION | POMODORO PracticeType | Keeps fire system at 3 fires, no complexity increase |
| Difficulty rating scope | Only during Pomodoro sessions | All training sessions | Reduces friction during normal training, Pomodoro is the "focused" mode |
| Timer persistence | Pauses on background, doesn't persist | Foreground service with notification | Simpler, no permissions needed, matches app's offline-first approach |
| Auto-rating | Good after 3 seconds | No auto-select | Prevents blocking the user flow |
| Stats storage | Ephemeral, shown once | Persisted history | MVP simplicity, can add history later |
