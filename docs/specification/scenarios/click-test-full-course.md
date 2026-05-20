# Full Course Click Test Scenario

> **Purpose:** Comprehensive end-to-end test covering the complete learning journey from first lesson through course completion
> **Coverage:** All training modes, card tracking, navigation, statistics, strikes, retries, and state persistence
> **Test Environment:** Android emulator or physical device with APK installed
> **Duration:** ~60-90 minutes for complete manual execution (or split across multiple sessions)

---

## Document Overview

This test specification documents the complete user journey through GrammarMate, verifying:

1. **Card Tracking** - `uniqueCardShows`, `shownCardIds`, mastery counting
2. **Navigation** - Lesson unlock flow, sub-lesson progression, roadmap updates
3. **Strike Calculation** - `StreakManager` behavior, fire counting, streak days
4. **Statistics** - `totalSubLessonsCompleted`, `todayFireCount`, mastery percentages
5. **All Clickable Elements** - Every button, tile, and control that shows cards
6. **Retries** - 3-attempt hint flow, incorrect answer handling

---

## Test Prerequisites

### Device State
| Item | Requirement |
|------|-------------|
| App Installation | Fresh install OR reset progress for test language |
| Storage | At least 500MB free space for models and data |
| Network | Not required after initial model download (offline capable) |
| Device | Android API 24+ (Android 7.0+) |

### Content Requirements
| Item | Minimum | Recommended |
|------|---------|-------------|
| Lesson Pack | 1 pack with 3+ lessons | 1 pack with 5+ lessons |
| Cards Per Lesson | 150+ cards | 150+ cards (main + reserve pools) |
| Verb Drill | `verbDrill` section in manifest | All 12 Italian tenses |
| Vocab Drill | `vocabDrill` section in manifest | All POS types (nouns, verbs, adj, etc.) |
| Drill Sub-lessons | At least 1 lesson with `drillFile` | 2+ lessons with drill cards |

### Account State (Fresh Install Preferred)
| Setting | Value | Notes |
|---------|-------|-------|
| User Name | "TestUser" | Set in profile or accept default |
| Interface Language | English (default) | Or Russian for i18n test |
| Content Language | Italian (IT) | Or English (EN) |
| Voice Auto-Start | ON | Test both ON and OFF states |
| Hint Level | EASY | Test all 3 levels (EASY/MEDIUM/HARD) |
| Font Scale | 1.0x (default) | Test scaling separately |

---

## Test Data Verification

Before starting the full course test, verify the test pack has adequate content:

### Step 0.1: Verify Lesson Pack Content
| Action | Expected Result | Pass/Fail |
|--------|-----------------|-----------|
| Import test lesson pack ZIP | Pack appears in Settings → Lesson Packs | |
| Open Settings → Lesson Packs | Pack shows lesson count, card counts | |
| Verify manifest.json structure | Contains `lessons`, `verbDrill`, `vocabDrill` sections | |
| Check lesson cards count | Each lesson has 150+ cards in `allCards` array | |
| Check drill cards | At least 1 lesson has `drillFile` and `drillCards` array | |

### Step 0.2: Verify Store Files Created
| Action | Expected Result | Pass/Fail |
|--------|-----------------|-----------|
| Navigate to device file system | `grammarmate/` directory exists | |
| Check `mastery.yaml` | File exists, parseable YAML | |
| Check `progress.yaml` | File exists, contains `selectedLessonId`, `currentIndex` | |
| Check `streak_it.yaml` (or `streak_en.yaml`) | File exists, contains `streakData` | |
| Check drill directories | `drills/{packId}/verb_drill/` and `vocab_drill/` exist | |

---

## PHASE 1: First Lesson (NEW_ONLY Sub-lessons)

**Objective:** Verify initial lesson progression with NEW_ONLY sub-lessons, card tracking, and mastery counting

### Section 1.1: Home Screen Entry and First Lesson Selection

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 1.1.1 | Launch app | HomeScreen renders, lesson tiles visible | `grammarmate/seed_v1.done` marker exists | |
| 1.1.2 | Observe lesson tile states | Tile 1: SEED/UNLOCKED, Tiles 2-12: LOCKED | `mastery.yaml` has empty or no entries | |
| 1.1.3 | Tap "Continue Learning" or Tile 1 | Navigate to Lesson Roadmap | `progress.yaml`: `selectedLessonId` = lesson 1 ID | |
| 1.1.4 | Observe roadmap | 15+ sub-lesson circles shown | Tile 1: UNLOCKED (open lock), Tiles 2+: LOCKED | |
| 1.1.5 | Tap first sub-lesson circle | Navigate to TrainingScreen, PAUSED state | `currentIndex` = 0, `subLessonTotal` = 10 (default) | |

### Section 1.2: First Sub-Lesson - Card Display and Input Modes

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 1.2.1 | Verify card elements | Russian prompt, TTS button, input field, Submit button | `currentCard` has `promptRu`, `answers`, `id` | |
| 1.2.2 | Tap Play button | Session state → ACTIVE, timer starts | `activeTimeMs` begins incrementing | |
| 1.2.3 | Observe input mode | VOICE mode active (mic icon highlighted) | `inputMode` = `InputMode.VOICE` | |
| 1.2.4 | Tap TTS speaker button | Audio plays target language pronunciation | `ttsState` transitions IDLE → SPEAKING → IDLE | |
| 1.2.5 | Tap Keyboard icon | Input mode switches to KEYBOARD | `inputMode` = `InputMode.KEYBOARD` | |
| 1.2.6 | Type answer and tap Submit | Validation runs | `lastResult` set, `correctCount` or `incorrectCount` increments | |

### Section 1.3: First Sub-Lesson - Answer Validation and Mastery Tracking

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 1.3.1 | Submit correct answer (VOICE) | Green "Correct", auto-advance after 400ms | `uniqueCardShows` = 1, `shownCardIds` contains card ID | |
| 1.3.2 | Submit correct answer (KEYBOARD) | Green "Correct", manual advance | `uniqueCardShows` = 2, `shownCardIds` has 2 IDs | |
| 1.3.3 | Submit answer via WORD_BANK | Green "Correct", NO mastery increment | `uniqueCardShows` = 2 (unchanged), `totalCardShows` = 3 | |
| 1.3.4 | Complete 10 cards via VOICE/KEYBOARD | Sub-lesson completion screen | `uniqueCardShows` = 10 (all VOICE/KEYBOARD cards) | |
| 1.3.5 | Tap "Done" on completion | Navigate to Lesson Roadmap | `completedSubLessonCount` recalculated from mastery | |

**CRITICAL VERIFICATION:** After completing first 10 cards:
- `uniqueCardShows` MUST equal count of VOICE + KEYBOARD answers
- `shownCardIds` MUST contain exactly those card IDs
- WORD_BANK answers MUST NOT increment `uniqueCardShows`

### Section 1.4: First Sub-Lesson - Roadmap State Update

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 1.4.1 | Observe completed sub-lesson tile | Shows COMPLETED state (checkmark or filled circle) | `activeSubLessonIndex` = 1 (moved forward) | |
| 1.4.2 | Observe next sub-lesson tile | Shows UNLOCKED state (was LOCKED before) | Unlock calculation uses `completedSubLessonCount` | |
| 1.4.3 | Tap Home and return to lesson | Verify flower state | Flower state = SEED (1-49 unique cards / 150) | |
| 1.4.4 | Tap Tile 1 again | Return to roadmap, state preserved | All tile states unchanged | |

### Section 1.5: Second Sub-Lesson - Verify NEW_ONLY Type

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 1.5.1 | Start second sub-lesson | 10 cards from current lesson only | `sessionCards` all have `lessonId` = current lesson | |
| 1.5.2 | Complete 5 cards with VOICE | Progress advances | `uniqueCardShows` = 15 (10 + 5) | |
| 1.5.3 | Tap Exit mid-session | Exit confirmation dialog | Progress saved: `currentIndex`, `correctCount` | |
| 1.5.4 | Confirm exit | Return to roadmap | `progress.yaml` updated atomically | |
| 1.5.5 | Re-enter second sub-lesson | Session resumes at saved position | `currentIndex` restored (not 0) | |

---

## PHASE 2: Subsequent Lessons (MIXED Sub-lessons)

**Objective:** Verify MIXED sub-lesson behavior with review cards from prior lessons

### Section 2.1: Complete First 15 Sub-Lessons

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 2.1.1 | Complete sub-lessons 1-7 | All NEW_ONLY type | `uniqueCardShows` = 70 (7 × 10) | |
| 2.1.2 | Start sub-lesson 8 | MIXED type (first half of lesson done) | Cards include current + review from lesson 1 | |
| 2.1.3 | Observe card distribution | ~5 current lesson cards + ~5 review cards | Review cards have different `lessonId` | |
| 2.1.4 | Complete MIXED sub-lesson | Both current and review mastery recorded | Current lesson `uniqueCardShows` increments, review lesson `totalCardShows` increments | |
| 2.1.5 | Complete sub-lessons 8-15 | Mix of NEW_ONLY and MIXED | `uniqueCardShows` for lesson 1 = 150 (full main pool) | |

### Section 2.2: Boss Battle Unlock

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 2.2.1 | After completing 15th sub-lesson | Boss Battle tile becomes available | `completedSubLessonCount` >= 15 | |
| 2.2.2 | Verify Boss tile on roadmap | Trophy icon, "Boss Lesson" label | `bossUnlocked` = true in state | |
| 2.2.3 | Verify lesson completion state | Lesson marked as completed | `completedAtMs` set in mastery state | |

---

## PHASE 3: Drill Modes (Verb, Vocab, Lesson Drill)

**Objective:** Verify all three drill modes function correctly

### Section 3.1: Verb Drill Entry and Session

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 3.1.1 | Return to Home Screen | HomeScreen renders | | |
| 3.1.2 | Tap Verb Drill tile | Navigate to VerbDrillScreen (Selection) | `verbDrill/verb_drill_progress.yaml` loaded | |
| 3.1.3 | Verify tense dropdown | Shows "All tenses" + individual tenses | Available tenses from manifest | |
| 3.1.4 | Select "Presente" | Progress updates for Presente only | Filtered by `tense == "Presente"` | |
| 3.1.5 | Tap "Start" | 10-card session begins | `todayShownCardIds` excludes already-seen cards | |
| 3.1.6 | Complete 3 verb cards | Progress saved per combo | `everShownCardIds` updated, `todayShownCount` = 3 | |
| 3.1.7 | Tap Exit and confirm | Return to HomeScreen | Progress persisted in `verb_drill_progress.yaml` | |

### Section 3.2: Vocab Drill Entry and Session

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 3.2.1 | Tap Vocab Drill tile | Navigate to VocabDrillScreen (Selection) | `word_mastery.yaml` loaded | |
| 3.2.2 | Verify direction chips | "IT → RU" (selected), "RU → IT" | Direction toggle works | |
| 3.2.3 | Verify POS chips | "All", "Nouns", "Verbs", "Adj.", etc. | Filter by POS type | |
| 3.2.4 | Tap "Start (N due)" | Flashcard session begins | Cards selected by SRS (due words first) | |
| 3.2.5 | Flip card (voice or skip) | Card back shows translation + forms | Mastery step not yet advanced | |
| 3.2.6 | Tap rating "Good" | Step increments by 1, card advances | `intervalStepIndex` = 1 | |
| 3.2.7 | Complete 10 vocab cards | Session completes | Mastery updated in `word_mastery.yaml` | |

### Section 3.3: Lesson Drill (Drill Sub-Mode)

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 3.3.1 | Navigate to Lesson Roadmap | Tiles shown | | |
| 3.3.2 | Tap Drill tile (if present) | DrillStartDialog appears | `lesson.drillCards` array non-empty | |
| 3.3.3 | Tap "Start" | TrainingScreen in drill mode (green accents) | `isDrillMode` = true | |
| 3.3.4 | Complete 3 drill cards | Progress tracked separately | `drillProgressStore` saves per-lesson progress | |
| 3.3.5 | Tap Exit mid-drill | Progress saved | Can resume via "Continue" option | |

---

## PHASE 4: Boss Battles

**Objective:** Verify boss battle unlock, completion, and reward system

### Section 4.1: Boss Battle Entry

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 4.1.1 | On Lesson Roadmap, tap Boss tile | Navigate to TrainingScreen (boss mode) | `bossActive` = true | |
| 4.1.2 | Verify screen title | Shows "Review Session" | Header label updated | |
| 4.1.3 | Verify progress bar | Shows "0% (0/30)" or similar | `bossProgress` = 0, `bossTotal` = N | |
| 4.1.4 | Verify no Word Bank | Only VOICE and KEYBOARD available | Word Bank button hidden/disabled | |

### Section 4.2: Boss Battle Progress Tracking

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 4.2.1 | Submit correct answer | `bossProgress` increments by 1 | Progress bar shows "3% (1/30)" | |
| 4.2.2 | Submit incorrect answer (3 times) | Hint shown, progress does NOT advance | `bossProgress` unchanged | |
| 4.2.3 | Complete 50% of boss cards | Exit and verify BRONZE reward | `bossProgress` / `bossTotal` > 0.5 | |
| 4.2.4 | Complete 100% of boss cards | GOLD reward dialog | Trophy icon GOLD color (#FFD700) | |

### Section 4.3: Boss State Restoration

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 4.3.1 | After boss completion | Return to Lesson Roadmap | Previous session state restored | |
| 4.3.2 | Tap regular lesson tile | Resume normal training | `currentIndex`, `correctCount` preserved | |

---

## PHASE 5: Daily Practice Integration

**Objective:** Verify Daily Practice 3-block session, cursor advancement, and fire recording

### Section 5.1: Daily Practice Entry

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 5.1.1 | On HomeScreen, tap Daily Practice tile | Loading overlay, then DailyPracticeScreen | Session built from `DailyCursorState` | |
| 5.1.2 | Verify block type badge | Shows "Translation" | Block 1 = TRANSLATE | |
| 5.1.3 | Verify progress bar | Shows "1/30" or similar | 3 blocks × 10 cards each = 30 total | |

### Section 5.2: Block 1 - Translation Cards

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 5.2.1 | Complete first card (VOICE) | Auto-advance after 400ms | Input mode rotates: VOICE → KEYBOARD → WORD_BANK | |
| 5.2.2 | Complete 10 translation cards | Block transition sparkle | "Next: Vocabulary" overlay | |
| 5.2.3 | Verify block transition | VOCAB block appears | Block type badge changes | |

### Section 5.3: Block 2 - Vocabulary Flashcards

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 5.3.1 | Verify flashcard | Prompt + translation visible | No flip required (both shown) | |
| 5.3.2 | Tap rating "Good" | Step increments, card advances | SRS step updated in `word_mastery.yaml` | |
| 5.3.3 | Complete 10 vocab cards | Block transition sparkle | "Next: Verbs" overlay | |

### Section 5.4: Block 3 - Verb Conjugations

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 5.4.1 | Verify verb card | Russian prompt + verb/tense chips | Chips clickable for reference sheets | |
| 5.4.2 | Tap verb chip | VerbReferenceBottomSheet opens | Conjugation table shown | |
| 5.4.3 | Complete 10 verb cards | Session completion sparkle | "Daily practice complete!" overlay | |

### Section 5.5: Cursor Advancement

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 5.5.1 | After completing all 3 blocks | Check `DailyCursorState` | `sentenceOffset` incremented by 10 (if all VOICE/KEYBOARD) | |
| 5.5.2 | Tap "Back to Home" | Navigate to HomeScreen | Fire streak indicator updated | |
| 5.5.3 | Tap Daily Practice again | Resume dialog appears | "Repeat" or "Continue" options | |

---

## PHASE 6: Streak and Statistics Tracking

**Objective:** Verify `StreakManager` behavior, fire counting, and statistics display

### Section 6.1: Fire Streak Recording

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 6.1.1 | Complete a training sub-lesson | Check `StreakStore` | `completedTypesToday` includes `TRANSLATION` | |
| 6.1.2 | Observe HomeScreen header | Fire indicator shows 1 fire emoji | `todayFireCount` = 1 | |
| 6.1.3 | Complete Verb Drill session | Check fire indicator | Shows 2 fire emojis (TRANSLATION + VERB) | |
| 6.1.4 | Complete Vocab Drill session | Check fire indicator | Shows 3 fire emojis (max for Italian) | |
| 6.1.5 | Complete same type again | No new fire added | `completedTypesToday` unchanged (deduplication) | |

### Section 6.2: Streak Days Calculation

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 6.2.1 | Record date of first session | `currentStreak` = 1 | `streakData.currentStreak` = 1 | |
| 6.2.2 | Wait until next calendar day | | | |
| 6.2.3 | Complete another session | Streak increments to 2 | `currentStreak` = 2, `lastPracticeDate` updated | |
| 6.2.4 | Observe streak display | Shows "2d" next to fires | Inline indicator updated | |

### Section 6.3: Total Sub-Lessons Completed

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 6.3.1 | Complete 30 sub-lessons across lessons | Check statistics | `totalSubLessonsCompleted` = 30 | |
| 6.3.2 | Open Settings → Statistics | Verify display | Shows correct count | |

---

## PHASE 7: Navigation Through Lesson Tiles

**Objective:** Verify lesson unlock flow and navigation between lessons

### Section 7.1: Lesson Unlock by Mastery

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 7.1.1 | Complete lesson 1 (150+ unique cards) | Flower state = BLOOM | `uniqueCardShows` >= 99 (66% of 150) | |
| 7.1.2 | Check lesson 2 tile state | Changes from LOCKED to UNLOCKED | Unlock threshold met (prior lesson mastery) | |
| 7.1.3 | Tap lesson 2 tile | Navigate to lesson 2 roadmap | `selectedLessonId` = lesson 2 ID | |

### Section 7.2: Multi-Lesson Progression

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 7.2.1 | Complete 5 sub-lessons in lesson 2 | Check lesson 3 tile | May still be LOCKED (depends on threshold) | |
| 7.2.2 | Complete lesson 2 fully | Lesson 3 unlocks | Sequential unlock flow | |
| 7.2.3 | Tap "Continue Learning" | Navigates to current active lesson | Uses `selectedLessonId` from state | |

---

## PHASE 8: Retries for Bad Answers (3-Attempt Flow)

**Objective:** Verify 3-attempt hint flow across all training modes

### Section 8.1: Regular Training Retry Flow

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 8.1.1 | Submit incorrect answer (attempt 1) | Red "Incorrect", "2 attempts left" | `incorrectAttemptsForCard` = 1 | |
| 8.1.2 | Submit incorrect answer (attempt 2) | Red "Incorrect", "1 attempts left" | `incorrectAttemptsForCard` = 2 | |
| 8.1.3 | Submit incorrect answer (attempt 3) | Hint shown, all accepted answers displayed | `incorrectAttemptsForCard` = 0 (reset) | |
| 8.1.4 | Verify session state | `HINT_SHOWN`, timer paused | `sessionState` = `HINT_SHOWN` | |
| 8.1.5 | Tap Next button | Advance to next card | Manual advance required | |

### Section 8.2: Verb Drill Retry Flow

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 8.2.1 | Submit wrong conjugation (3 times) | Auto-hint appears | Same as regular training | |
| 8.2.2 | Verify hint card | Pink background, red answer text | Verb drill uses same `HintAnswerCard` | |

### Section 8.3: Daily Practice Retry Flow

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 8.3.1 | In TRANSLATE block, submit wrong 3 times | Hint card appears | `showIncorrectFeedback` = true | |
| 8.3.2 | In VERBS block, submit wrong 3 times | Same hint behavior | Consistent across blocks | |

---

## PHASE 9: All Clickable Elements Showing Cards

**Objective:** Verify every clickable element that leads to card display

### Section 9.1: HomeScreen Elements

| Element | Action | Expected Result | Pass/Fail |
|---------|--------|-----------------|-----------|
| Lesson Tile 1 | Tap | Navigate to roadmap | |
| "Continue Learning" button | Tap | Navigate to roadmap | |
| Verb Drill tile | Tap | Navigate to VerbDrillScreen | |
| Vocab Drill tile | Tap | Navigate to VocabDrillScreen | |
| Daily Practice tile | Tap | Navigate to DailyPracticeScreen | |
| Avatar circle | Tap | Open ProfileStatsPopup | |
| Settings gear | Tap | Open SettingsSheet | |

### Section 9.2: Lesson Roadmap Elements

| Element | Action | Expected Result | Pass/Fail |
|---------|--------|-----------------|-----------|
| Sub-lesson circle (UNLOCKED) | Tap | Start training session | |
| Drill tile (if present) | Tap | Show DrillStartDialog | |
| Boss Lesson tile | Tap | Start boss battle | |
| Back arrow | Tap | Navigate to HomeScreen | |

### Section 9.3: Training Screen Elements

| Element | Action | Expected Result | Pass/Fail |
|---------|--------|-----------------|-----------|
| Play button | Tap | Start session (ACTIVE state) | |
| Pause button | Tap | Pause session (PAUSED state) | |
| Next button | Tap | Advance to next card | |
| Previous button | Tap | Go to previous card | |
| Exit button | Tap | Show exit confirmation | |
| TTS speaker | Tap | Play pronunciation | |
| Input mode buttons | Tap | Switch mode (VOICE/KEYBOARD/WORD_BANK) | |
| Show answer (eye) | Tap | Show hint immediately | |
| Report button | Tap | Open report bottom sheet | |

### Section 9.4: Daily Practice Screen Elements

| Element | Action | Expected Result | Pass/Fail |
|---------|--------|-----------------|-----------|
| Back arrow (header) | Tap | Show exit confirmation | |
| Verb chip | Tap | Open VerbReferenceBottomSheet | |
| Tense chip | Tap | Open TenseInfoBottomSheet | |
| Vocab rating buttons | Tap | Record rating, advance card | |
| Vocab report button | Tap | Open report sheet | |

---

## PHASE 10: Course Completion

**Objective:** Verify full course completion state and statistics

### Section 10.1: Complete All Lessons in Pack

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 10.1.1 | Complete all lessons (e.g., 5 lessons) | All tiles show BLOOM or COMPLETED | Each lesson has `uniqueCardShows` >= 99 | |
| 10.1.2 | Check total statistics | `totalSubLessonsCompleted` = 75 (5 × 15) | All sub-lessons counted | |
| 10.1.3 | Check overall mastery | Sum of all `uniqueCardShows` | 750+ cards (5 lessons × 150) | |

### Section 10.2: Verify Final State

| Step | Action | Expected Result | Data Verification | Pass/Fail |
|------|--------|-----------------|-------------------|-----------|
| 10.2.1 | Navigate to HomeScreen | All lesson tiles show completed flowers | `FlowerCalculator` returns BLOOM for all | |
| 10.2.2 | Check fire streak | Shows consistent streak | `currentStreak` reflects days practiced | |
| 10.2.3 | Verify all stores | All YAML files parseable | No data corruption | |

---

## Data Verification Summary

### Mastery Tracking (`mastery.yaml`)

| Field | Expected Behavior | Verification Method |
|-------|------------------|---------------------|
| `uniqueCardShows` | Increments only on VOICE/KEYBOARD | Check after each session |
| `totalCardShows` | Increments on all answers (incl. WORD_BANK) | Compare with `uniqueCardShows` |
| `shownCardIds` | Set of unique card IDs practiced | Verify no duplicates |
| `intervalStepIndex` | Advances on correct review | Check after due card reviews |
| `lastShowDateMs` | Updates on each card show | Verify timestamp increases |

### Progress Tracking (`progress.yaml`)

| Field | Expected Behavior | Verification Method |
|-------|------------------|---------------------|
| `selectedLessonId` | Current active lesson | Check after lesson switch |
| `currentIndex` | Position within sub-lesson | Restores after exit/re-enter |
| `correctCount` / `incorrectCount` | Session statistics | Verify after each session |
| `completedSubLessonCount` | Recalculated from mastery | Matches `uniqueCardShows` / 10 |

### Streak Tracking (`streak_{lang}.yaml`)

| Field | Expected Behavior | Verification Method |
|-------|------------------|---------------------|
| `currentStreak` | Consecutive days with practice | Increments daily |
| `lastPracticeDate` | Most recent practice date | Updates after session |
| `completedTypesToday` | Set of PracticeTypes done today | Max 4 types, deduplicates |

### Drill Progress (`verb_drill_progress.yaml`)

| Field | Expected Behavior | Verification Method |
|-------|------------------|---------------------|
| `everShownCardIds` | All cards ever shown for combo | Accumulates, never resets |
| `todayShownCardIds` | Cards shown today | Resets at midnight |
| `lastShownDateMs` | Most recent show for combo | Updates per card |

### Vocab Mastery (`word_mastery.yaml`)

| Field | Expected Behavior | Verification Method |
|-------|------------------|---------------------|
| `intervalStepIndex` | SRS step (0-9) | Advances per rating |
| `isLearned` | True when step >= 3 | Threshold check |
| `nextReviewDateMs` | Calculated from ladder | Check after rating |

---

## Edge Cases and Error Conditions

### Section EC.1: Network Loss During TTS Download

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| EC.1.1 | Start TTS download, then disable network | Download pauses | |
| EC.1.2 | Re-enable network | Download resumes | |
| EC.1.3 | Tap TTS during download | Show error icon or loading state | |

### Section EC.2: Process Death Mid-Session

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| EC.2.1 | Start session, complete 3 cards | Progress saved | |
| EC.2.2 | Kill app process (swipe away) | App terminated | |
| EC.2.3 | Relaunch app | Restores to HomeScreen | |
| EC.2.4 | Navigate to same lesson | Session resumes at card 4 | `currentIndex` restored |

### Section EC.3: Empty Sub-Lesson Handling

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| EC.3.1 | Create sub-lesson with 0 cards (test mode) | Attempt to start | Graceful error or skip | |
| EC.3.2 | Navigate to next sub-lesson | Skips empty sub-lesson | Continues to next with cards |

### Section EC.4: Maximum Font Scale

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| EC.4.1 | Set font scale to 2.0x | Text at 200% size | |
| EC.4.2 | Navigate to training | Prompt text fits on screen | No overflow or clipping |

---

## Automation Notes

### Maestro Flow Structure

```yaml
# Example: Phase 1.1 - Home to First Lesson
appId: com.alexpo.grammermate
---
- launchApp
- assertVisible: "GrammarMate"
- assertVisible: "Continue Learning"
- tapOn: "Continue Learning"
- assertVisible: "Lesson 1"
```

### mobile-mcp Usage Pattern

```python
# Take screenshot after each phase
mobile_take_screenshot("phase-1-complete.png")
# List elements to verify state
mobile_list_elements_on_screen()
```

---

## Success Criteria

A successful full-course test run must:

1. **Complete all 10 phases** without crashes or ANRs
2. **Verify data persistence** across app restarts (process death test)
3. **Confirm mastery counting** - WORD_BANK doesn't inflate `uniqueCardShows`
4. **Validate unlock flow** - Lesson 2 unlocks after Lesson 1 mastery threshold
5. **Check streak behavior** - Fire emojis update, streak days increment
6. **Verify all drill modes** - Verb, Vocab, and Lesson Drill function correctly
7. **Test all retry flows** - 3-attempt hint works in all modes
8. **Confirm boss battle** - Unlocks at 15 sub-lessons, rewards calculate correctly
9. **Validate Daily Practice** - 3-block session, cursor advances, fires recorded
10. **Check statistics** - All counts match actual progress

---

## Related Specifications

- `01-models-and-state.md` - Data structures for mastery, progress, streak
- `02-data-stores.md` - Store implementations (MasteryStore, ProgressStore, StreakStore)
- `03-algorithms-and-calculators.md` - Spaced repetition, flower calculation
- `08-training-viewmodel.md` - Session management, answer validation
- `09-daily-practice.md` - Daily Practice 3-block architecture
- `10-verb-drill.md` - Verb drill standalone mode
- `11-vocab-drill.md` - Vocab drill SRS system
- `22-use-case-registry.md` - UC-01 through UC-73 for detailed acceptance criteria
- `23-screen-elements.md` - UI element invariants per screen

---

## Change History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2026-05-20 | 1.0 | Initial outline created | Claude Opus |
