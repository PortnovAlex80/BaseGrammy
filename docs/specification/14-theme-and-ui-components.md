# 14. Theme & UI Components -- Specification

## 14.1 Theme System

### 14.1.1 Material 3 Light-Only Theme

GrammarMate uses a single `GrammarMateTheme` composable wrapping `MaterialTheme` with a hardcoded `lightColorScheme`. There is **no dark theme variant**: no `darkColorScheme`, no `values-night/` resource directory, no `isSystemInDarkTheme()` check.

**File:** `app/src/main/java/com/alexpo/grammermate/ui/Theme.kt`

```kotlin
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D62),      // Deep teal
    onPrimary = Color.White,
    secondary = Color(0xFF5E8B7E),    // Medium sage green
    onSecondary = Color.White,
    background = Color(0xFFF7F4F1),   // Warm off-white
    onBackground = Color(0xFF1F1F1F), // Near-black
    surface = Color.White,
    onSurface = Color(0xFF1F1F1F)     // Near-black
)
```

### 14.1.2 Color Palette

| Role | Hex | Visual | Usage |
|------|-----|--------|-------|
| `primary` | `#2F5D62` | Deep teal | User avatar circle, primary buttons, tense labels, FilledTonalIconButton fills |
| `onPrimary` | `#FFFFFF` | White | Text on primary-colored surfaces |
| `secondary` | `#5E8B7E` | Sage green | Not directly used in GrammarMateApp -- reserved for future use |
| `onSecondary` | `#FFFFFF` | White | Text on secondary-colored surfaces |
| `background` | `#F7F4F1` | Warm off-white | App-wide background (Scaffold, Surface) |
| `onBackground` | `#1F1F1F` | Near-black | Body text on background |
| `surface` | `#FFFFFF` | White | Card containers, dialog backgrounds |
| `onSurface` | `#1F1F1F` | Near-black | Text on cards, labels, secondary text |
| `surfaceVariant` | (M3 default) | Light grey | Empty lesson tiles (50% alpha), NavIconButton backgrounds, disabled DrillTile |
| `primaryContainer` | (M3 default) | Light teal | DailyPracticeEntryTile container |
| `onPrimaryContainer` | (M3 default) | Dark teal | Text/icons on DailyPracticeEntryTile |
| `secondaryContainer` | (M3 default) | Light sage | VocabDrillEntryTile (70% alpha) |
| `outline` | (M3 default) | Medium grey | Disabled DrillTile icon tint |
| `error` | (M3 default) | Red | Error states, ASR error text, TTS error icon |

### 14.1.3 Theme Entry Point

```kotlin
@Composable
fun GrammarMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
```

No custom `typography` or `shapes` are passed -- Material 3 defaults are used throughout.

### 14.1.4 XML Theme

**File:** `app/src/main/res/values/themes.xml`

```xml
<style name="Theme.GrammarMate" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
</style>
```

Key points:
- Parent is `Theme.Material3.DayNight.NoActionBar` (DayNight parent but only light Compose theme is defined)
- Status bar and navigation bar are transparent, letting the Compose Surface color show through
- No ActionBar -- all UI is in Compose

### 14.1.5 XML Colors

**File:** `app/src/main/res/values/colors.xml`

```xml
<color name="ic_launcher_background">#2F3C4A</color>
```

Only one XML color is defined: the launcher icon adaptive icon background (dark blue-grey). All other colors are defined inline in Compose code.

### 14.1.6 Typography

No custom typography is defined. The app relies entirely on Material 3 default typography scale:
- `displayLarge` through `displaySmall`
- `headlineLarge` through `headlineSmall` -- used for dialog titles (`headlineSmall`)
- `titleLarge` -- used for screen titles ("GrammarMate")
- `titleMedium` -- used for section headers in Settings, Ladder, card options
- `bodyLarge` -- used for setting labels
- `bodyMedium` -- used for body text, descriptions
- `bodySmall` -- used for secondary text, helper text
- `labelLarge` -- used for section headers ("CSV format", "Instructions")
- `labelMedium` -- used for input labels, mode labels
- `labelSmall` -- used for mastered count, sub-labels

### 14.1.7 Shape System

No custom shape system. Material 3 defaults are used. Individual shapes are applied at the composable level:
- `RoundedCornerShape(12.dp)` -- NavIconButton, DrillProgressRow progress bar
- `CircleShape` -- User avatar box, ASR recording indicator dot
- Default Card shapes from Material 3

---

## 14.2 Shared Components

All composables in GrammarMateApp.kt are `private` to that file. There is no separate `ui/components/` or `ui/screens/` directory -- the entire UI resides in GrammarMateApp.kt (except DailyPracticeScreen.kt, VerbDrillScreen.kt, VocabDrillScreen.kt). The following is a complete inventory of all composables.

### 14.2.1 Dialog Composables

All dialogs use Material 3 `AlertDialog` or `ModalBottomSheet`.

| Dialog Composable | Type | Trigger | Title | Description |
|---|---|---|---|---|
| `WelcomeDialog` | AlertDialog (non-dismissable) | First launch or visiting non-HOME screen with default username | "Welcome to GrammarMate!" | Asks for user name. OutlinedTextField with Done IME action. "Continue" + "Skip" buttons. |
| Exit Confirmation | AlertDialog (inline) | Back press during Training | "End session?" | "Current session will be completed." -- Exit / Cancel. Boss mode redirects to Lesson; drill mode calls `exitDrillMode()`. |
| Daily Resume | AlertDialog (inline) | Opening Daily Practice with resumable session | "Ежедневная практика" (Russian) | "Повторить -- те же карточки сначала / Продолжить -- новый набор карточек". Two buttons: "Продолжить" / "Повторить". |
| Story Error | AlertDialog (inline) | `state.storyErrorMessage != null` | "Story" | Shows error message from story engine. |
| Boss Error | AlertDialog (inline) | `state.bossErrorMessage != null` | "Boss" | Shows error message from boss engine. |
| Boss Reward | AlertDialog (inline) | `state.bossRewardMessage != null` | "Boss Reward" | Trophy icon with tinted color (Bronze=#CD7F32, Silver=#C0C0C0, Gold=#FFD700). |
| Streak | AlertDialog (inline) | `state.streakMessage != null` | "Streak!" | Shows "??" at 48sp as icon, streak message, and longest streak comparison. |
| Drill Start (`DrillStartDialog`) | AlertDialog | `state.drillShowStartDialog == true` | "Drill Mode" | "Continue where you left off or start over?" if hasProgress, else "Start drill training?". Buttons: Continue/Start Fresh + Cancel. |
| How This Training Works | AlertDialog (inline) | "How This Training Works" button on Home | "How This Training Works" | Explains grammar pattern building methodology. |
| Lesson Locked Hint | AlertDialog (inline) | Tapping locked empty lesson tile | "Lesson locked" | "Please complete the previous lesson first." |
| Early Start (Lesson) | AlertDialog (inline) | Tapping locked lesson tile that has a lesson | "Start early?" | "Start this lesson early? You can always come back..." Yes / No. |
| Early Start (Sub-lesson) | AlertDialog (inline) | Tapping locked sub-lesson in roadmap | "Start early?" | "Start exercise N early?..." Yes / No. |
| Locked Sub-lesson | AlertDialog (inline) | Tapping locked sub-lesson without early start | "Locked" | "Complete at least 15 exercises first." |
| TTS Download (`TtsDownloadDialog`) | AlertDialog | TTS play button pressed without model | "Download pronunciation model?" | Multi-state: Idle (size info), Downloading (progress bar + percentage), Extracting (progress bar), Done ("ready!"), Error (message). |
| Metered Network (`MeteredNetworkDialog`) | AlertDialog | TTS download on cellular | "Metered network detected" | "You appear to be on a cellular or metered connection. The pronunciation model is ~346 MB." |
| ASR Metered Network (`AsrMeteredNetworkDialog`) | AlertDialog | ASR model download on cellular | "Metered network detected" | "...speech recognition model is ~375 MB." |
| Export Result | AlertDialog (inline) | After exporting bad sentences | "Export" | Shows "Exported to [path]" or "No bad sentences to export". |
| Card Options | ModalBottomSheet | Report sentence button | "Card options" | Options: Add/Remove from bad sentences, Hide card, Export bad sentences, Copy text. |

### 14.2.2 Button Styles

| Style | Component | Usage |
|---|---|---|
| `Button` (filled, primary) | Primary action button | "Continue Learning", "Check", "Save Name" |
| `OutlinedButton` | Secondary action | "How This Training Works", "Import lesson pack (ZIP)", "Add language", destructive actions (with red contentColor `#B00020`) |
| `TextButton` | Dialog buttons, inline actions | "OK", "Cancel", "Exit", "Yes", "No", "Undo", "Download anyway" |
| `FilledTonalIconButton` | Mode/input toggles | Voice mode, Keyboard mode, Word Bank mode, Lesson mode, All lessons, Mixed mode |
| `IconButton` | Icon-only actions | Settings, navigation arrows, mic, visibility, report, TTS speaker, delete pack, back |
| `FilterChip` | Word bank words | Each word in the word bank -- selected state when picked |

### 14.2.3 Card Styles

| Card Variant | Container Color | Usage |
|---|---|---|
| Default Card | `surface` (White) | CardPrompt, HomeScreen primary action card, lesson tiles (non-empty, non-drill), BossTile |
| Default Card (empty) | `surfaceVariant` at 50% alpha | Empty lesson tiles (no lesson in pack slot) |
| `primaryContainer` | `primaryContainer` | DailyPracticeEntryTile |
| `secondaryContainer` at 70% | `secondaryContainer.copy(alpha = 0.7f)` | VocabDrillEntryTile |
| `surfaceVariant` (disabled) | `surfaceVariant` | Disabled DrillTile |
| `primaryContainer` (enabled) | `primaryContainer` | Enabled DrillTile |
| Default Card (drill green) | N/A -- background only | TrainingScreen scaffold in drill mode uses `Color(0xFFE8F5E9)` as containerColor |

### 14.2.4 Progress Indicators

| Component | Visual | Usage |
|---|---|---|
| `LinearProgressIndicator` | Material 3, 2dp height, full width | Persistent TTS background download bar at top of all screens |
| `LinearProgressIndicator` (determinate) | Full width, with percentage text | TTS download dialog progress, ASR download progress in Settings |
| `CircularProgressIndicator` | Material 3, default size | Daily practice loading overlay, TTS INITIALIZING state, ASR RECOGNIZING state |
| `CircularProgressIndicator` (small) | 12dp, 2dp stroke | ASR "Processing..." indicator |
| Custom progress bar (`DrillProgressRow`) | Custom Canvas + Box | 70% width green bar with "current/total" text, 30% speedometer circle |

**DrillProgressRow colors:**
| Element | Color | Hex |
|---|---|---|
| Progress bar fill | Green | `#4CAF50` |
| Progress bar track | Light green | `#C8E6C9` |
| Progress text (dark bg) | White | `#FFFFFF` |
| Progress text (light bg, <12%) | Dark green | `#2E7D32` |
| Speedometer track | Light grey | `#E0E0E0` |
| Speed <= 20 wpm | Red | `#E53935` |
| Speed 21-40 wpm | Yellow | `#FDD835` |
| Speed > 40 wpm | Green | `#43A047` |

### 14.2.5 Input Fields

| Component | Configuration | Usage |
|---|---|---|
| `OutlinedTextField` | `fillMaxWidth()`, various labels | User name (WelcomeDialog), translation input, new lesson title, new language name, vocab limit, Settings user name |
| `OutlinedTextField` (translation) | `fillMaxWidth()`, trailing mic IconButton | Main answer input on TrainingScreen. Label: "Your translation". Mic button triggers voice mode. |
| `Slider` | Range 0.5f..1.5f, 3 steps | TTS speed control |
| `Slider` | Range 1.0f..2.0f, 3 steps | Russian text scale control |
| `Switch` | Material 3 | Test Mode toggle, Offline ASR toggle, Voice auto-start toggle |
| `DropdownMenu` + `DropdownMenuItem` | Material 3 | Language selector, Mode selector (Lesson/All/Mixed), Settings language/pack dropdowns |
| `FilterChip` (Word Bank) | FlowRow layout | Word bank word selection; disabled when all instances used |

### 14.2.6 Other Shared Components

| Component | Description |
|---|---|
| `TtsSpeakerButton` | IconButton that switches icon based on `TtsState`: SPEAKING -> StopCircle (error tint), INITIALIZING -> CircularProgressIndicator, ERROR -> ReportProblem (error tint), default -> VolumeUp |
| `NavIconButton` | 44dp box with RoundedCornerShape(12dp), surfaceVariant background, 3dp accent bottom bar. Used for Prev/Next/Pause/Exit navigation. |
| `ModeIconButton` | FilledTonalIconButton when selected, plain IconButton when not. Used in ModeSelector and input mode toggles. |
| `LanguageSelector` | TextButton that opens DropdownMenu with language list. |
| `DropdownSelector` | Column with label + OutlinedButton that opens DropdownMenu. Used for Language and Pack selection. |
| `AsrStatusIndicator` | Shows state of offline ASR: RECORDING -> pulsing red dot + "Listening...", RECOGNIZING -> small spinner + "Processing...", ERROR -> "Recognition error" in error color |
| `DrillProgressRow` | Custom composable with green progress bar (70% width) + Canvas speedometer circle (30% width) |
| `HeaderStats` | Row showing Progress percentage, Time, Speed stats |
| `LessonTile` | Grid tile showing lesson number + flower emoji + mastery percentage. States: SEED, SPROUT, FLOWER, LOCKED, UNLOCKED, EMPTY, VERB_DRILL |
| `BossTile` | Card with label + trophy icon. Trophy tint varies by reward: Bronze/Silver/Gold |
| `DrillTile` | Card with FitnessCenter icon + "Drill" text. Enabled state uses primaryContainer. |
| `DailyPracticeEntryTile` | Card with "Daily Practice" label, "Practice all sub-lessons" subtitle, PlayArrow icon. Uses primaryContainer. |
| `VerbDrillEntryTile` | Card with FitnessCenter icon + "Verb Drill" label. |
| `VocabDrillEntryTile` | Card with MenuBook icon + "Flashcards" label + mastered count (green `#2E7D32`). Uses secondaryContainer. |

### 14.2.7 Bottom Sheets

| Sheet | Description |
|---|---|
| `SettingsSheet` | Full `ModalBottomSheet` with scrollable content. Sections: Service Mode (Test Mode, Ladder, Vocab limit), Pronunciation speed slider, Voice recognition toggle + download, Voice auto-start toggle, Russian text scale slider, Language/Pack selectors, Import options, Pack management, Profile name editor, Backup & Restore. |

---

## 14.3 Sound Design

### 14.3.1 Sound Files

| File | Size | Format | Duration (approx.) |
|---|---|---|---|
| `voicy_correct_answer.mp3` | 32,215 bytes (~31 KB) | MP3, 64 kbps, 44.1 kHz, Stereo, ID3v2.4 | ~4 seconds |
| `voicy_bad_answer.mp3` | 23,019 bytes (~22 KB) | MP3, 64 kbps, 44.1 kHz, Stereo, ID3v2.4 | ~3 seconds |

Both files are in `app/src/main/res/raw/`.

### 14.3.2 Sound Playback Engine

Sounds are played via `android.media.SoundPool` (not MediaPlayer), configured for low-latency playback:

```kotlin
private val soundPool = SoundPool.Builder()
    .setMaxStreams(2)
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    )
    .build()
```

Sounds are loaded at ViewModel creation and tracked in a `loadedSounds` set. Playback uses volume 1.0 on both channels, no loop, normal rate.

### 14.3.3 When Sounds Are Played

There are two pairs of play functions:
1. `playSuccessSound()` / `playErrorSound()` -- used during daily practice and drill answer submission
2. `playSuccessTone()` / `playErrorTone()` -- used during boss mode and main training answer submission

Both pairs play the same underlying sound files. The distinction is naming only (Tone vs Sound).

**`voicy_correct_answer.mp3`** is played when:
- Daily practice sentence answer is correct (line ~1736)
- Daily practice verb answer is correct (line ~1749)
- Boss mode answer is correct (line ~630)
- Main training answer is correct (line ~2093)

**`voicy_bad_answer.mp3`** is played when:
- Daily practice sentence answer is incorrect (line ~1738)
- Daily practice verb answer is incorrect (line ~1751)
- Boss mode answer is incorrect (line ~814)
- Main training answer is incorrect (line ~2101)

### 14.3.4 TTS-Spoken Answers

TTS (Sherpa-ONNX, offline) speaks the target-language answer text in these contexts:

| Context | Trigger | Speed | Text Source |
|---|---|---|---|
| Training screen | User taps TTS speaker button | 0.67x (or user-configured) | `state.answerText` (shown answer) or `currentCard.acceptedAnswers.first()` |
| Daily Practice screen | Card/component calls `onSpeak(text)` | 0.67x | Target-language text of the current card/verb answer |
| Post-download auto-play | TTS model finishes downloading | 0.67x | `state.answerText` or `currentCard.acceptedAnswers.first()` |

TTS speed is configurable via Settings slider: range 0.5x to 1.5x, persisted in `TrainingUiState.ttsSpeed`.

---

## 14.4 Strings & Localization

### 14.4.1 strings.xml

**File:** `app/src/main/res/values/strings.xml`

```xml
<resources>
    <string name="app_name">GrammarMate</string>
</resources>
```

Only one string resource is defined: the application name. All other UI strings are hardcoded in Kotlin source.

### 14.4.2 Language Mix in UI Strings

The app uses a **mix of English and Russian** hardcoded strings, with no localization framework:

**English strings** (majority):
- Screen titles: "GrammarMate", "Grammar Roadmap"
- Button labels: "Continue Learning", "Check", "Start", "Cancel", "Exit", "OK"
- Training UI: "Your translation", "Correct", "Incorrect", "Answer:", "No cards"
- Dialog titles: "End session?", "Story", "Boss", "Boss Reward", "Streak!", "Welcome to GrammarMate!"
- Settings: "Service Mode", "Test Mode", "Voice recognition", "Backup & Restore", "Profile"
- Mode labels: "Voice", "Keyboard", "Word Bank"
- Drill/Daily labels: "Drill Mode", "Daily Practice", "Verb Drill", "Flashcards"
- Flower legend: "seed", "growing", "bloom", "wilting", "wilted", "forgotten"

**Russian strings** (settings and specific features):
- "Показать лестницу" (Show ladder)
- "Размер текста перевода" (Translation text size)
- "Сбросить весь прогресс" (Reset all progress)
- "Лестница интервалов" (Interval ladder -- screen title)
- "Все уроки текущего пакета" (All lessons of current pack -- subtitle)
- "Нет данных по урокам." (No lesson data)
- Ladder column headers: "#", "Урок" (Lesson), "Карты" (Cards), "Дней" (Days), "Интервал" (Interval)
- "Продолжить" / "Повторить" (Continue / Repeat -- daily practice resume dialog)
- "Ежедневная практика" (Daily practice -- dialog title)
- "Повторить -- те же карточки сначала\nПродолжить -- новый набор карточек" (Repeat description)

**No Italian strings** appear in the UI code despite Italian being a target learning language.

### 14.4.3 Localization Strategy

**Current state:** No localization strategy exists. All strings are hardcoded in GrammarMateApp.kt. There are:
- No `values-ru/`, `values-it/`, or any locale-specific resource directories
- No use of `stringResource()` or `Context.getString()`
- No string identifiers beyond `app_name`

This means:
- The app UI is always in the English/Russian mix regardless of device locale
- Learning content (CSV data) can be in any language, but the app chrome is fixed
- Russian strings appear primarily in developer-facing settings and internal tools

---

## 14.5 Icon & Asset Inventory

### 14.5.1 Launcher Icon

The app uses an **Adaptive Icon** (API 26+) with a fallback PNG for older devices.

**Adaptive icon configuration:**
- Background: `@color/ic_launcher_background` = `#2F3C4A` (dark blue-grey)
- Foreground: `@mipmap/ic_launcher_foreground` (PNG per density)

**XML vector fallback foreground** (`drawable/ic_launcher_foreground.xml`):
- 108dp x 108dp viewport
- Three white horizontal bars (simplified book/lines motif)
- `fillColor="#FFFFFF"`

### 14.5.2 Launcher Icon Assets Per Density

| Density | `ic_launcher.png` | `ic_launcher_foreground.png` | `ic_launcher_round.png` |
|---|---|---|---|
| mdpi (48x48 dp) | 3,695 bytes | 13,400 bytes | 3,695 bytes |
| hdpi (72x72 dp) | 7,011 bytes | 27,481 bytes | 7,011 bytes |
| xhdpi (96x96 dp) | 11,085 bytes | 47,163 bytes | 11,085 bytes |
| xxhdpi (144x144 dp) | 22,121 bytes | 105,657 bytes | 184,696 bytes (xxxhdpi) |
| xxxhdpi (192x192 dp) | 37,658 bytes | 184,696 bytes | 37,658 bytes |

The adaptive icon XML files (`mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`) reference the foreground PNG and background color.

### 14.5.3 Material Icons Used

The app uses Material Icons (`androidx.compose.material.icons.Icons.Default`) exclusively. Complete inventory:

| Icon | Usage |
|---|---|
| `ArrowBack` | Back navigation (Ladder, Lesson Roadmap), Previous card, Sub-lesson back |
| `ArrowForward` | Next card navigation |
| `ContentCopy` | Copy card text in Card Options sheet |
| `Delete` | Delete all lessons, Delete pack |
| `Download` | Export bad sentences, Restore from backup |
| `EmojiEvents` | Boss reward trophy, BossTile icon |
| `FitnessCenter` | Verb Drill entry tile, Drill tile, LessonTile for VERB_DRILL state |
| `Insights` | Show ladder button in Settings |
| `Keyboard` | Keyboard input mode toggle |
| `LibraryBooks` | All lessons mode, Word Bank input mode |
| `LocalFlorist` | Not used in current code (imported but unused) |
| `Lock` | Locked lesson/sub-lesson tiles |
| `MenuBook` | Flashcards entry tile, Lesson mode button |
| `Mic` | Voice input mode toggle, Voice input field trailing icon |
| `Pause` | Pause session |
| `PlayArrow` | Play/resume session, Daily Practice entry icon |
| `Refresh` | Reset all progress button |
| `ReportProblem` | Report sentence, TTS error state |
| `Settings` | Settings button on Home and Training screens |
| `StopCircle` | Stop TTS playback, Exit session |
| `SwapHoriz` | Mixed training mode |
| `Upload` | Import lesson pack, Import lesson CSV, Save progress |
| `Visibility` | Show answer button |
| `VisibilityOff` | Hide card from lessons (Card Options) |
| `VolumeUp` | TTS speaker button (default state) |

### 14.5.4 Other Assets

No other drawable assets, custom illustrations, or image resources exist. The only non-code visual assets are:
- Launcher icon PNGs (5 density buckets x 3 variants)
- Two MP3 sound files in `res/raw/`

Emoji characters are used extensively for flower state visualization (see section 14.6.1), but these are Unicode characters rendered by the system font, not image assets.

---

## 14.6 UI Patterns & Conventions

### 14.6.1 Color Coding

**Answer feedback:**

| State | Text Color | Hex |
|---|---|---|
| Correct answer | Dark green | `#2E7D32` |
| Incorrect answer | Dark red | `#C62828` |

**Boss reward tiers:**

| Reward | Color | Hex |
|---|---|---|
| Bronze | Bronze | `#CD7F32` |
| Silver | Silver | `#C0C0C0` |
| Gold | Gold | `#FFD700` |

**Drill mode color scheme:**

| Element | Color | Hex |
|---|---|---|
| Drill background | Light green | `#E8F5E9` |
| Drill prompt tense label | Medium green | `#388E3C` |
| Drill prompt text | Dark green | `#2E7D32` |
| Vocab mastered count | Dark green | `#2E7D32` |

**Destructive action buttons:**
- Red text: `#B00020` via `ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB00020))`
- Used for: "Delete all lessons", "Сбросить весь прогресс" (Reset all progress)

**Speed indicator colors (DrillProgressRow speedometer):**

| Speed Range | Color | Hex | Meaning |
|---|---|---|---|
| 0-20 wpm | Red | `#E53935` | Slow |
| 21-40 wpm | Yellow | `#FDD835` | Medium |
| 41+ wpm | Green | `#43A047` | Fast |

**ASR status colors:**

| State | Color | Description |
|---|---|---|
| RECORDING | `Color.Red` with pulsing alpha (0.4-1.0) | Pulsing red dot |
| RECOGNIZING | `CircularProgressIndicator` (default) | Small spinner |
| ERROR | `MaterialTheme.colorScheme.error` | Red error text |

**Secondary/muted text:**
- `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)` -- helper text, descriptions, sub-labels
- `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)` -- card prompt in Card Options, vocab drill subtitle
- `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)` -- Ladder subtitle
- `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)` -- voice mode hint, locked tile icon
- `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)` -- empty tile content, disabled accent bar

### 14.6.2 Flower State Emoji System

Flower states are visualized using emoji characters defined in `FlowerCalculator.getEmoji()`:

| State | Emoji | Unicode | Visual | Tile State |
|---|---|---|---|---|
| LOCKED | Lock | `🔒` | Closed padlock | `LessonTileState.LOCKED` |
| SEED | Seedling | `🌱` | Seedling | Default when flower has progress |
| SPROUT | Herb | `🌿` | Growing herb | First lesson (always SPROUT) |
| BLOOM | Cherry Blossom | `🌸` | Pink flower | Full mastery |
| WILTING | Wilted Flower | `🥀` | Drooping flower | Neglected, needs review |
| WILTED | Fallen Leaf | `🍂` | Brown leaf | Severely neglected |
| GONE | Medium Black Circle | `⚫` | Black circle | Completely forgotten |

Special tile states (not flower-based):
- `UNLOCKED`: Open lock emoji `🔓`
- `EMPTY`: Gray bullet `●`
- `VERB_DRILL`: FitnessCenter icon (not emoji)

Font size scales with `flower.scaleMultiplier`: `fontSize = (18 * scale).sp`

The legend displayed on HomeScreen:
```
Legend:
seed -- growing -- bloom
wilting -- wilted -- forgotten
```

### 14.6.3 Animation Patterns

| Animation | Type | Duration | Purpose |
|---|---|---|---|
| TTS download bar | `AnimatedVisibility` | Default | Shows/hides persistent progress bar at screen top when background TTS download is active |
| ASR recording pulse | `animateFloat` + `infiniteRepeatable` | 600ms tween, Reverse | Red dot pulses between 0.4 and 1.0 alpha during voice recording |

These are the only two explicit animations in the app. Screen transitions are handled by Compose recomposition (no shared element transitions, no slide/fade). Dialog appearances use Material 3 default fade-in.

### 14.6.4 Screen Navigation Pattern

Navigation is managed via a mutable `screen` state variable of type `AppScreen` enum:

```
HOME
  -> LESSON (select lesson)
  -> DAILY_PRACTICE (start daily)
  -> VERB_DRILL (open verb drill)
  -> VOCAB_DRILL (open vocab drill)

LESSON
  -> HOME (back)
  -> TRAINING (start sub-lesson, boss, boss mega, drill)
  -> STORY (story quiz)

TRAINING
  -> LESSON (sub-lesson finished, boss finished, exit)
  -> LADDER (via Settings)

STORY
  -> LESSON (close/complete)

LADDER
  -> previous screen (back)

DAILY_PRACTICE
  -> HOME (exit/complete)

VERB_DRILL
  -> HOME (back)

VOCAB_DRILL
  -> HOME (back)
```

BackHandler is registered per-screen:
- TRAINING: shows exit confirmation dialog
- LESSON, DAILY_PRACTICE, VERB_DRILL, VOCAB_DRILL: navigates to HOME
- STORY: navigates to LESSON
- LADDER: navigates to `previousScreen`, resumes Training if applicable

`AppScreen.ELITE` and `AppScreen.VOCAB` enum values redirect to HOME immediately (backward compatibility for saved state).

### 14.6.5 Loading States

| State | Visual | Context |
|---|---|---|
| Daily Practice loading | `Dialog` with Card containing `CircularProgressIndicator` + "Loading session..." text | Shown while `isLoadingDaily` is true |
| TTS downloading (background) | `LinearProgressIndicator` at screen top, 2dp height, `AnimatedVisibility` | Persistent across all screens |
| TTS downloading (dialog) | `AlertDialog` with text percentage + `LinearProgressIndicator` | TTS download dialog |
| ASR downloading | `LinearProgressIndicator` in Settings + percentage text | Settings sheet, Voice recognition section |
| ASR initializing | `CircularProgressIndicator` (24dp, 2dp stroke) in TtsSpeakerButton | Replaces VolumeUp icon |
| TTS speaking | `StopCircle` icon (error tint) in TtsSpeakerButton | Replaces VolumeUp icon |
| ASR recognizing | `CircularProgressIndicator` (12dp, 2dp stroke) + "Processing..." text | Below translation input |
| ASR recording | Pulsing red dot + "Listening..." text | Below translation input |
| No cards | "No cards" text in error color + disabled submit | TrainingScreen when session has no cards |

### 14.6.6 Layout Conventions

**Spacing:** `Arrangement.spacedBy(8.dp)` or `Arrangement.spacedBy(12.dp)` for vertical lists; `Arrangement.spacedBy(16.dp)` for TrainingScreen main content.

**Padding:** `16.dp` horizontal padding on all screens; `8.dp` internal padding on tiles; `32.dp` for loading dialog content.

**Grid:** Lesson tiles use `LazyVerticalGrid` with `GridCells.Fixed(4)`, 8dp spacing, tiles at 72dp height.

**Cards:** Standard Material 3 `Card` with `fillMaxWidth()`. Primary action card is clickable. Entry tiles (DailyPractice, VerbDrill, VocabDrill) are 64dp height.

**Input row:** Translation input is always `OutlinedTextField` with `fillMaxWidth()`, trailing mic icon, label "Your translation".

**Word bank:** `FlowRow` with `FilterChip` for each word, 2dp spacing.

### 14.6.7 Text Scaling

Russian prompt text scales with `state.ruTextScale` (configurable 1.0x to 2.0x via Settings slider):
- Card prompt: `fontSize = (20f * state.ruTextScale).sp`
- Drill prompt: `fontSize = (18f * state.ruTextScale).sp`

All other text uses fixed sizes or Material 3 typography defaults and does not scale.

### 14.6.8 Conventions Summary

1. **All UI in GrammarMateApp.kt** -- no separate screen files except DailyPracticeScreen.kt, VerbDrillScreen.kt, VocabDrillScreen.kt.
2. **All dialogs inline** -- dialog state is local `remember { mutableStateOf(...) }` variables, not in ViewModel.
3. **No custom fonts** -- system default via Material 3.
4. **No dark mode** -- light-only theme.
5. **No localization** -- hardcoded English/Russian mix.
6. **SoundPool for feedback** -- not MediaPlayer, for low-latency answer sounds.
7. **Sherpa-ONNX for TTS** -- offline text-to-speech, not Android TTS.
8. **Emoji for flower states** -- not custom drawables or Lottie animations.
9. **No custom animations** -- only two: TTS download bar visibility toggle and ASR recording pulse.
10. **Stateless UI** -- GrammarMateApp is a pure renderer of `TrainingUiState`, all business logic in TrainingViewModel.
