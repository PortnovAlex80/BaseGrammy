# BaseGrammy - AI Agent Navigation Guide

> **Purpose**: Quick reference for AI agents to navigate the codebase efficiently
> **Language**: Kotlin + Jetpack Compose
> **Architecture**: MVVM pattern, single ViewModel architecture

## Project Structure

```
app/src/main/java/com/alexpo/grammermate/
├── data/                  # Data layer
│   ├── Models.kt         # Data classes (Lesson, Card, VocabEntry)
│   ├── MixedReviewScheduler.kt  # Sub-lesson scheduling
│   ├── TrainingConfig.kt        # Constants
│   ├── MasteryStore.kt          # Progress tracking
│   └── FlowerCalculator.kt      # Mastery visualization
├── ui/
│   ├── TrainingViewModel.kt     # Main controller (2000+ lines)
│   └── GrammarMateApp.kt       # UI composables
└── store/                 # Database layer
```

---

## Core Classes & Methods

### TrainingViewModel.kt
**Main training session controller**

#### Session Management
- `startSession()` - Start/resume training session
- `pauseSession()` - Pause current session
- `togglePause()` - Toggle between active/paused
- `buildSessionCards()` - Build card list for current session

#### Card Navigation
- `submitAnswer(input, voiceDuration, voiceWords)` - Submit answer and validate
- `nextCard()` - Move to next card
- `prevCard()` - Move to previous card
- `currentCard()` - Get current card

#### Word Bank
- `updateWordBank()` - Generate word bank for current card
- `generateWordBank(correctAnswer, extraWords)` - Create shuffled word list
- `selectWordFromBank(word)` - Add word to selected list
- `removeLastSelectedWord()` - Undo last selection

#### Vocabulary Sprint
- `openVocabSprint()` - Start vocabulary sprint mode
- `buildVocabWordBank(entry, pool)` - Create vocab word bank (always 5 options)
- `updateVocabWordBank()` - Refresh vocab word bank
- `submitVocabAnswer(input, voiceDuration, voiceWords)` - Submit vocab answer

#### Input Modes
- `setInputMode(mode)` - Switch between VOICE/KEYBOARD/WORD_BANK
- `InputMode` enum: `VOICE`, `KEYBOARD`, `WORD_BANK`

#### Mastery & Progress
- `recordCardShowForMastery(card)` - Record card show (excludes WORD_BANK mode)
- `markSubLessonCardsShown(cards)` - Mark sub-lesson as completed
- `checkAndMarkLessonCompleted()` - Check if lesson is done
- `refreshFlowerStates()` - Update flower/mastery visualization
- `calculateCompletedSubLessons()` - Count completed sub-lessons

#### Lesson Management
- `selectLesson(lessonId)` - Switch to different lesson
- `selectSubLesson(index)` - Jump to specific sub-lesson
- `rebuildSchedules(lessons)` - Rebuild lesson schedule

#### Boss & Elite Modes
- `openBoss(type)` - Start boss battle
- `openElite()` - Start elite mode
- `cancelBoss()` - Exit boss mode
- `cancelElite()` - Exit elite mode

#### Utilities
- `resolveCardLessonId(card)` - Find which lesson owns a card
- `updateStreak()` - Update daily streak
- `saveProgress()` - Persist current progress

---

### MixedReviewScheduler.kt
**Builds sub-lesson schedule with spaced repetition**

#### Main Methods
- `build(lessons)` - Create lesson schedule map
- `dueLessonIds(reviewStartMixedIndex, lessonIndexById, globalMixedIndex)` - Find lessons due for review
- `fillReviewSlots(dueLessons, reviewQueues, reserveQueues, slots, fallbackQueue)` - Fill review card slots

#### Data Classes
- `SubLessonType` enum: `NEW_ONLY`, `MIXED`
- `ScheduledSubLesson(type, cards)` - Sub-lesson with type and cards
- `LessonSchedule(lessonId, subLessons)` - Full lesson schedule

---

### GrammarMateApp.kt
**Main UI Composable**

#### Key Composables
- `TrainingScreen(state, onAction)` - Main training screen
- `HeaderStats(state)` - Progress display at top
- `CardDisplay(card, state)` - Show current card
- `InputSection(state, onAction)` - Input area (voice/keyboard/word bank)
- `WordBankChips(words, selectedWords, onSelect)` - Word bank UI (lines 2148-2167)
- `VocabWordBankChips(words, onSelect, onSubmit)` - Vocab word bank UI

#### Roadmap
- `RoadmapDialog(lessons, state, onAction)` - Lesson selection with flowers
- `RoadmapEntry` - Training/Story/Boss/Elite tiles

---

### Models.kt
**Data structures**

#### Core Models
- `Lesson(id, languageId, title, cards)` - Lesson data
  - `mainPoolCards` - First 150 cards
  - `reservePoolCards` - Cards beyond 150
  - `allCards` - All cards combined
- `SentenceCard(id, nativeText, acceptedAnswers)` - Card with translations
- `VocabEntry(id, lessonId, languageId, nativeText, targetText)` - Vocabulary item

#### State Models
- `TrainingUiState` - Main UI state with:
  - `currentCard`, `currentIndex`
  - `sessionState` (ACTIVE/PAUSED)
  - `inputMode` (VOICE/KEYBOARD/WORD_BANK)
  - `wordBankWords`, `selectedWords`
  - `subLessonTotal`, `subLessonCount`
  - `completedSubLessonCount`

---

### FlowerCalculator.kt
**Calculate mastery visualization**

#### Methods
- `calculate(mastery, lesson)` - Compute flower state from mastery data
- `determineFlowerState(masteryPercent, healthPercent, mastery)` - Determine flower type
- `toEmoji(state)` - Convert state to emoji string

#### Flower States & Icons (FlowerCalculator.kt:105-113)
```kotlin
LOCKED  -> 🔒 "\uD83D\uDD12"  // Lesson not yet available
SEED    -> 🌱 "\uD83C\uDF31"  // 0-33% mastery (0-49 unique shows)
SPROUT  -> 🌿 "\uD83C\uDF3F"  // 33-66% mastery (50-99 unique shows)
BLOOM   -> 🌸 "\uD83C\uDF38"  // 66-100% mastery (100-150 unique shows)
WILTING -> 🥀 "\uD83E\uDD40"  // Health declining (50-100%)
WILTED  -> 🍂 "\uD83C\uDF42"  // Health critical (<50%)
GONE    -> ⚫ "\u26AB"        // Forgotten (>90 days without practice)
```

#### Flower Growth Mechanics (FlowerCalculator.kt:33-59)

**Mastery Calculation:**
```kotlin
masteryPercent = (uniqueCardShows / 150).coerceIn(0f, 1f)
// uniqueCardShows = count of distinct cards practiced with VOICE or KEYBOARD
// Word Bank mode does NOT count (see TrainingViewModel.kt:1710)
```

**Health Calculation:**
```kotlin
healthPercent = e^(-daysSinceLastShow / stabilityMemory)
// Ebbinghaus forgetting curve
// Health decays if not practicing at expected intervals
```

**Icon Scale:**
```kotlin
scaleMultiplier = (masteryPercent × healthPercent).coerceIn(0.5f, 1.0f)
// Icon size = 50-100% based on mastery and health
// Fresh practice at high mastery = 100% size
// Old practice or low mastery = 50% size
```

**State Priorities (FlowerCalculator.kt:77-88):**
1. Health < 50% → WILTED 🍂
2. Health < 100% → WILTING 🥀
3. Mastery < 33% → SEED 🌱
4. Mastery < 66% → SPROUT 🌿
5. Mastery ≥ 66% → BLOOM 🌸

**Growth Example:**
- 0 shows: 🌱 SEED (0%, scale 50%)
- 25 shows: 🌱 SEED (17%, scale ~58%)
- 50 shows: 🌿 SPROUT (33%, scale ~66%)
- 100 shows: 🌸 BLOOM (67%, scale ~83%)
- 150 shows: 🌸 BLOOM (100%, scale 100%)
- 150 shows + 30 days no practice: 🥀 WILTING (100%, scale ~70%)
- 150 shows + 60 days no practice: 🍂 WILTED (100%, scale ~50%)
- Any shows + 91 days no practice: ⚫ GONE (0%, scale 50%)

---

### MasteryStore.kt
**Track learning progress**

#### Methods
- `recordCardShow(lessonId, languageId, cardId)` - Record card practice
- `get(lessonId, languageId)` - Get mastery data
- `markLessonCompleted(lessonId, languageId)` - Mark lesson done

#### Mastery Data
- `uniqueCardShows` - Distinct cards seen (max 150)
- `totalCardShows` - Total practice count
- `shownCardIds` - Set of seen card IDs
- `lastShowDateMs` - Last practice timestamp
- `intervalStepIndex` - Spaced repetition step

---

### BackupManager.kt
**Manages progress backup and restore for app reinstallation**

#### Methods
- `createBackup()` - Save current progress to Downloads/BaseGrammy
- `restoreFromBackup(backupPath)` - Restore progress from backup
- `getAvailableBackups()` - List all backups with timestamps
- `deleteBackup(backupPath)` - Remove old backup
- `hasBackup()` - Check if backups exist

#### Backup Files
Saves to: `Downloads/BaseGrammy/backup_YYYY-MM-DD_HH-mm-ss/`
- `mastery.yaml` - Flower levels & card progress
- `progress.yaml` - Training session state
- `streak.yaml` - Daily streak data
- `metadata.txt` - Backup timestamp & info

---

### TrainingConfig.kt
**Configuration constants**

- `SUB_LESSON_SIZE_DEFAULT = 10`
- `SUB_LESSON_SIZE_MIN = 6`
- `SUB_LESSON_SIZE_MAX = 12`
- `ELITE_STEP_COUNT = 7`

---

## Key Logic Flow

### Word Bank with Repeated Words
```
GrammarMateApp.kt:2148-2167
- Count word occurrences in wordBankWords (availableCount)
- Count times word selected (usedCount)
- Enable chip if usedCount < availableCount
```

### Mastery Recording (Flower Growth)
```
TrainingViewModel.recordCardShowForMastery()
- Skip if inputMode == WORD_BANK (line 1710)
- Only VOICE and KEYBOARD count for mastery
- Still counts for lesson progress (sub-lesson completion)
```

### Vocab Word Bank
```
TrainingViewModel.buildVocabWordBank()
- Get correct answer from entry.targetText
- Collect 4 distractors from vocab pool
- If <4 available, fetch from all lessons (line 1847-1862)
- Always return 5 options minimum
```

### Sub-Lesson Schedule
```
MixedReviewScheduler.build()
- Only NEW_ONLY and MIXED types (no WARMUP)
- First lesson: only NEW_ONLY
- Later lessons: NEW_ONLY + MIXED with spaced repetition
- Review intervals: [1, 2, 4, 7, 10, 14, 20, 28, 42, 56]
```

---

## Grep Search Patterns

### Word Bank Issues
```bash
grep -r "wordBankWords\|selectedWords\|selectWordFromBank\|updateWordBank" --include="*.kt"
```
**Files**: TrainingViewModel.kt:1791-1879, GrammarMateApp.kt:2148-2167

### Mastery & Flower Growth
```bash
grep -r "recordCardShowForMastery\|FlowerCalculator\|MasteryStore\|refreshFlowerStates" --include="*.kt"
```
**Files**: TrainingViewModel.kt:1698-1719, FlowerCalculator.kt, MasteryStore.kt

### Sub-Lesson Logic
```bash
grep -r "SubLessonType\|buildSessionCards\|ScheduledSubLesson" --include="*.kt"
```
**Files**: MixedReviewScheduler.kt, TrainingViewModel.kt:1044-1101

### Vocabulary Sprint
```bash
grep -r "openVocabSprint\|buildVocabWordBank\|submitVocabAnswer" --include="*.kt"
```
**Files**: TrainingViewModel.kt:1186-1237, 1824-1871

### Input Mode Logic
```bash
grep -r "InputMode\|setInputMode\|WORD_BANK" --include="*.kt"
```
**Files**: TrainingViewModel.kt:1542-1581, Models.kt

---

## Recent Changes (2026-01-12)

### Commit: `82ddb7f` - "Remove warmup mode and improve training logic"

1. **Word Bank Repeated Words** (GrammarMateApp.kt:2148-2167)
   - Changed from `selectedWords.contains(word)` to counting occurrences
   - Now allows repeated word selection

2. **Removed WARMUP Mode** (MixedReviewScheduler.kt:5-8)
   - Removed `WARMUP` from `SubLessonType` enum
   - Only `NEW_ONLY` and `MIXED` remain
   - Removed all warmup-related logic across codebase

3. **Separated Mastery from Progress** (TrainingViewModel.kt:1698-1719)
   - Word Bank mode: 0% mastery tracking (was 10%)
   - Only VOICE and KEYBOARD count for flower growth
   - Word Bank still counts for lesson progress

4. **Fixed Vocab Word Bank** (TrainingViewModel.kt:1824-1871)
   - Always shows 5 options (1 correct + 4 distractors)
   - Fetches from all lessons if current vocab pool insufficient

---

## Common Tasks Quick Reference

| Task | Function | File:Line |
|------|----------|-----------|
| Add new input mode | Extend `InputMode` enum | Models.kt |
| Modify word bank generation | `updateWordBank()` | TrainingViewModel.kt:1791 |
| Change mastery tracking | `recordCardShowForMastery()` | TrainingViewModel.kt:1703 |
| Adjust sub-lesson size | `TrainingConfig` constants | TrainingConfig.kt:4-7 |
| Modify spaced repetition | `MixedReviewScheduler.intervals` | MixedReviewScheduler.kt:22 |
| Change flower states | `FlowerState` enum | FlowerCalculator.kt |
| Update UI for word bank | Word bank composables | GrammarMateApp.kt:2143-2167 |

---

## Important Constraints

⚠️ **DO NOT**:
- Re-introduce WARMUP mode (removed in commit 82ddb7f)
- Count Word Bank mode for mastery tracking
- Modify spaced repetition intervals without testing
- Change flower calculation without updating FlowerCalculator
- Push directly to main branch
- Commit to main without user testing approval

✅ **ALWAYS**:
- Use `recordCardShowForMastery()` for mastery tracking
- Check `inputMode` before recording mastery
- Validate word bank has minimum options for vocab sprint
- Test sub-lesson progression after scheduler changes

---

## Development Workflow

### For New Features/Fixes:

1. **Create feature branch** from current branch
   ```bash
   git checkout -b feature/short-description
   # or
   git checkout -b fix/short-description
   ```

2. **Implement changes**
   - Make code changes
   - Add/update tests if needed
   - Follow existing patterns in codebase

3. **Create commits**
   - Use descriptive commit messages
   - Include `Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>` footer

4. **DO NOT PUSH YET** ⚠️
   - Wait for user review
   - Get confirmation that changes work correctly
   - User must test functionality before merge

5. **After User Approval**
   - Push feature branch: `git push origin feature/branch-name`
   - Create Pull Request (if available)
   - Merge to main only after user confirms testing

### Current Branch Strategy:
- Main branch: `main` (stable, production-ready)
- Development: Feature/fix branches (temporary, for testing)
- Naming: `feature/` or `fix/` prefix required

### Push Protocol:
```
❌ WRONG: Create feature → Commit → Push immediately
✅ RIGHT: Create feature → Commit → Wait for user → Test confirmation → Push
```

**User must explicitly approve before:**
- `git push` to any remote
- Merging to `main`
- Releasing to production

---

## Progress Persistence & Backup System

### How It Works

The app automatically backs up your progress data to prevent loss during reinstallation:

**What's saved:**
- 🌱 Flower levels (mastery progress for each lesson)
- 📊 Card statistics (uniqueCardShows, totalCardShows)
- 📅 Streak data (daily practice streaks)
- ⏱️ Training session state (current position, stats)

**Where backups are stored:**
- Location: `Downloads/BaseGrammy/backup_YYYY-MM-DD_HH-mm-ss/`
- Each backup is timestamped with full date and time
- Contains 3 YAML files (mastery, progress, streak) + metadata

### User Guide: Save Progress Before Uninstall

1. **Regular Backups** (Automatic)
   - The app creates backups whenever you save progress
   - Backups are stored in device storage (not deleted with app)

2. **Manual Backup** (Before uninstall)
   - Call `viewModel.createProgressBackup()` (technical feature for future UI)
   - Backups stay in `Downloads/BaseGrammy/` even after uninstall

### User Guide: Restore Progress After Reinstall

1. **Automatic Restoration** (Recommended)
   - On first app launch after reinstall, the app checks for backups
   - If found, it automatically restores the latest backup
   - Shows confirmation toast: "Progress restored from backup (backup_...)"

2. **Manual Restoration** (For advanced users)
   - Check if `Downloads/BaseGrammy/` has backup folders
   - Copy backup folder to internal app storage (technical process)
   - Restart app to load restored data

### Implementation Details

**Files involved:**
- `BackupManager.kt` - Handles backup create/restore
- `MainActivity.kt` - Checks for backups on first launch
- `TrainingViewModel.kt` - Triggers periodic backups via `createProgressBackup()`
- `AndroidManifest.xml` - Permissions for external storage access

**Key Flow:**
```
App Uninstall → Downloads/BaseGrammy/ data stays
              ↓
App Reinstall → MainActivity checks for backup
              ↓
If backup found → Auto-restore latest backup
              ↓
User sees "Progress restored" toast
              ↓
Flower levels & streaks are back ✅
```

### Important Notes

⚠️ **Storage Requirements:**
- Backups use external storage (Downloads folder)
- Requires READ/WRITE_EXTERNAL_STORAGE permissions
- Each backup is ~10-50KB depending on progress

✅ **Best Practices:**
- Keep backups in Downloads folder (safe from app deletion)
- Check file manager if "Downloads/BaseGrammy/" exists before uninstall
- Multiple backups accumulate, delete old ones if needed

❌ **Won't Restore:**
- Clearing app cache/data manually
- If backups are manually deleted from Downloads
- App data from different Android account
