# Boss Battle Click Test Scenario

## Test Overview
**Feature:** Boss Battle Mode
**Purpose:** Validate complete user journey through boss battle from unlock to reward
**Test Type:** Manual click-through test
**Precondition:** 15 sub-lessons completed OR test mode enabled

---

## Preconditions

| Condition | Value | How to Verify |
|-----------|-------|---------------|
| App installed | grammermate.apk | `adb shell pm list packages` |
| Lesson pack imported | At least 1 lesson | Settings → Lesson Packs |
| Sub-lessons completed | >= 15 OR testMode=true | Home screen → Boss tile visible |
| Device/emulator state | Running, unlocked | Screen visible |

---

## Test Data

| Item | Value |
|------|-------|
| Test lesson | Any lesson with >= 150 cards |
| Expected card count | 10-30 cards (bossCards size) |
| Test answers | Prepare correct translations for first 5 cards |

---

## Test Steps

### Phase 1: Entry & Unlock Verification

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1.1 | Launch app | Home screen loads, lesson tiles visible | |
| 1.2 | Locate Boss tile | Boss Battle tile should be visible (if `completedSubLessonCount >= 15` OR `testMode`) | |
| 1.3 | Tap Boss tile | Navigate to Lesson Roadmap with Boss Lesson tile visible | |
| 1.4 | Verify Boss Lesson tile | "Boss Lesson" tile displayed with trophy icon | |
| 1.5 | Verify Boss Mega tile (if applicable) | "Boss Mega" tile visible only for lessons at index > 0 | |

**Verification Point:** Boss tiles unlock at `completedSubLessonCount >= 15` or when `testMode=true`. `GrammarMateApp.kt:1202`

---

### Phase 2: Boss Battle Start

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 2.1 | Tap Boss Lesson tile | Navigate to Training Screen, boss session starts | |
| 2.2 | Verify screen title | Title shows "Review Session" (not lesson info) | |
| 2.3 | Verify progress bar | Shows `bossProgress/bossTotal` (e.g., "0% (0/30)") | |
| 2.4 | Verify stats header | Shows "Review" label instead of "Progress" | |
| 2.5 | Verify session state | Timer paused, Play button visible | |
| 2.6 | Verify card count | `subLessonTotal` equals boss card count (10-30) | |

**Verification Point:** `startBoss()` initializes state: `bossActive=true`, `bossTotal=N`, `bossProgress=0`, `sessionState=PAUSED`. `TrainingViewModel.kt:2193-2267`

---

### Phase 3: No Hints Mode Verification

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 3.1 | Examine first card | Card prompt should NOT show parenthetical hints (e.g., no "(dire)", "(verità)") | |
| 3.2 | Check HintLevel setting | HintLevel should be ignored in boss mode | |
| 3.3 | Attempt to enable hints | No hint controls should be available in boss UI | |

**Verification Point:** Boss mode operates in "no hints" mode. Parenthetical target-language insertions should be stripped from `promptRu`. Note: This is a **known gap** — HintLevel enum exists but is NOT wired to strip parentheticals. `TrainingViewModel.kt`

---

### Phase 4: Word Bank Unavailable

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 4.1 | Look for Word Bank button | Word Bank input mode should NOT be selectable | |
| 4.2 | Verify available input modes | Only VOICE and KEYBOARD modes available | |
| 4.3 | Attempt to switch to Word Bank | If option exists, it should be disabled or have no effect | |

**Verification Point:** Boss battles disable Word Bank mode. Only VOICE and KEYBOARD count toward progress. `GrammarMateApp.kt` input mode controls

---

### Phase 5: Answer Submission & Progress Tracking

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 5.1 | Tap Play button | Timer starts, `activeTimeMs` begins accumulating | |
| 5.2 | Submit correct answer (VOICE or KEYBOARD) | `bossProgress` increments by 1 | |
| 5.3 | Verify progress update | Progress bar shows updated percentage (e.g., "3% (1/30)") | |
| 5.4 | Submit incorrect answer (3 wrong attempts) | Answer revealed, card stays in queue, `bossProgress` does NOT advance | |
| 5.5 | After 3 wrong attempts, tap Next | Move to next card only after correct answer or reveal | |
| 5.6 | Repeat for 5 cards | Track `bossProgress` after each correct answer | |

**Verification Point:** `bossProgress` tracks correctly answered cards. Incorrect answers do NOT advance progress. `TrainingViewModel.kt:629-678`, `submitAnswer()`

---

### Phase 6: Boss Progress Mid-Session

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 6.1 | Complete 50% of cards (e.g., 15/30) | Progress shows "50% (15/30)" | |
| 6.2 | Tap Exit button | Exit dialog appears | |
| 6.3 | Confirm exit | `finishBoss()` called, BRONZE reward awarded (>50% threshold) | |
| 6.4 | Verify reward dialog | Trophy icon shows BRONZE color (#CD7F32), message "Bronze reached" | |
| 6.5 | Tap OK on reward dialog | Dialog closes, navigate to Lesson Roadmap | |

**Verification Point:** Exiting mid-boss awards reward based on `bossProgress/bossTotal` percentage. BRONZE > 50%, SILVER > 75%, GOLD >= 100%. `TrainingViewModel.kt:2382-2390`, `resolveBossReward()`

---

### Phase 7: Complete Boss Battle (GOLD Path)

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 7.1 | From Lesson Roadmap, tap Boss Lesson tile again | Start new boss session | |
| 7.2 | Complete ALL cards correctly (100%) | `bossProgress` advances to `bossTotal` | |
| 7.3 | On last correct answer | `updateBossProgress(bossTotal)` called, then `finishBoss()` | |
| 7.4 | Verify reward dialog | Trophy icon shows GOLD color (#FFD700), message "Gold reached" | |
| 7.5 | Tap OK on reward dialog | Dialog closes, navigate to Lesson Roadmap | |

**Verification Point:** GOLD reward awarded at 100% completion. `resolveBossReward()` returns `BossReward.GOLD` when `percent >= 100.0`. `TrainingViewModel.kt:2382-2390`

---

### Phase 8: State Restoration After Boss

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 8.1 | After returning to Lesson Roadmap | Previous lesson selection should be restored | |
| 8.2 | Tap on regular lesson tile | Resume normal training session | |
| 8.3 | Verify `currentIndex` | Should match pre-boss position (restored from ProgressStore) | |
| 8.4 | Verify `correctCount` / `incorrectCount` | Should match pre-boss values | |
| 8.5 | Verify `bossActive` | Should be `false` | |

**Verification Point:** `finishBoss()` restores pre-boss state from ProgressStore. `selectedLessonId`, `currentIndex`, `correctCount`, `incorrectCount` all restored. `TrainingViewModel.kt:2270-2329`

---

### Phase 9: Boss Reward Persistence

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 9.1 | Completely close app (swipe away) | App terminated | |
| 9.2 | Relaunch app | Home screen loads | |
| 9.3 | Navigate to Lesson Roadmap | Boss Lesson tile still visible | |
| 9.4 | Check `bossLessonRewards` | GOLD reward should be persisted for the lesson | |
| 9.5 | Start boss battle again | Can replay boss, previous reward is overwritten | |

**Verification Point:** Boss rewards stored in `bossLessonRewards` map persist across app restarts via ProgressStore YAML. `TrainingViewModel.kt:2270-2329`, `finishBoss()`

---

### Phase 10: Multiple Boss Battles (Retry Behavior)

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 10.1 | Complete boss with SILVER (>75%) | `bossLessonRewards[lessonId] = SILVER` | |
| 10.2 | Immediately replay boss | Boss starts fresh, `bossProgress=0` | |
| 10.3 | Complete with only BRONZE (>50%) | `bossLessonRewards[lessonId]` overwritten to BRONZE | |
| 10.4 | Verify final reward | Latest result kept, NOT best-of | |

**Verification Point:** Replaying boss overwrites previous reward (latest, not best). This is a **known behavior** — may be unintentional. `TrainingViewModel.kt:2279`

---

## Expected Dialog & UI Elements

| Element | Location | Text/Content | Color |
|---------|----------|--------------|-------|
| Boss tile | Home Screen | "Boss Battle" | Trophy icon |
| Boss Lesson tile | Lesson Roadmap | "Boss Lesson" | Trophy icon |
| Boss Mega tile | Lesson Roadmap | "Boss Mega" | Trophy icon (lessons > 0) |
| Screen title | Training Screen | "Review Session" | - |
| Progress label | Training Screen | "Review" | - |
| Boss Reward dialog | AlertDialog | "Boss Reward" | - |
| Reward message | Boss Reward dialog | "Bronze reached" / "Silver reached" / "Gold reached" | - |
| Trophy icon (BRONZE) | Boss Reward dialog | EmojiEvents icon | #CD7F32 |
| Trophy icon (SILVER) | Boss Reward dialog | EmojiEvents icon | #C0C0C0 |
| Trophy icon (GOLD) | Boss Reward dialog | EmojiEvents icon | #FFD700 |

---

## Reward Calculation Reference

| Reward | Threshold | Code Condition |
|--------|-----------|----------------|
| BRONZE | > 50% | `percent > 50.0` |
| SILVER | > 75% | `percent > 75.0` |
| GOLD | >= 100% | `percent >= 100.0` |
| None | <= 50% | `else -> null` |

**Note:** These thresholds differ from spec 18 (BRONZE >= 30%, SILVER >= 60-70%). Code values take precedence.

---

## Known Issues / Gaps

| Issue | Severity | Description |
|-------|----------|-------------|
| Mastery inflation | CRITICAL | Boss battles record mastery (`uniqueCardShows`, `intervalStepIndex`) via `recordCardShowForMastery()`, contradicting spec 18.8.4. Fix: Add `if (_uiState.value.bossActive) return` guard. |
| HintLevel not wired | MEDIUM | `HintLevel` enum exists but does NOT strip parenthetical hints from `promptRu` in boss mode. Boss should force `HintLevel.HARD`. |
| Reward overwrite | LOW | Replaying boss overwrites previous reward with latest result, not best-of. |
| Threshold mismatch | LOW | Code thresholds (50%/75%) differ from spec 18 (30%/60-70%). |
| No pool-based tiers | LOW | Spec 18 describes pool-aware medal tiers; code has no pool distinction. |

---

## Code References

| Function | File | Lines | Purpose |
|----------|------|-------|---------|
| `startBoss()` | TrainingViewModel.kt | 2193-2267 | Initialize boss session, select cards |
| `finishBoss()` | TrainingViewModel.kt | 2270-2329 | Complete boss, award reward, restore state |
| `resolveBossReward()` | TrainingViewModel.kt | 2382-2390 | Calculate reward from progress |
| `submitAnswer()` | TrainingViewModel.kt | 629-678 | Handle answer, update boss progress |
| `nextCard()` | TrainingViewModel.kt | 843-886 | Advance to next card, update progress |
| Boss reward dialog | GrammarMateApp.kt | 946-970 | Show reward with colored trophy |
| Boss unlock check | GrammarMateApp.kt | 1202 | `bossUnlocked = completedSubLessonCount >= 15 \|\| testMode` |

---

## Test Completion Criteria

- [ ] All 10 phases completed
- [ ] Boss unlock condition verified
- [ ] Reward calculation verified at all 3 tiers (BRONZE, SILVER, GOLD)
- [ ] State restoration confirmed after boss
- [ ] Persistence confirmed across app restart
- [ ] All UI elements match expected design
- [ ] Known issues documented

---

## Test Log

| Date | Tester | Result | Notes |
|------|--------|--------|-------|
| | | | |
| | | | |
