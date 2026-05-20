# Mix Challenge Click Test Scenario

> **Status:** DORMANT - UI entry point does not exist in HomeScreen
>
> **Last Updated:** 2026-05-20
>
> **Purpose:** This document outlines the step-by-step click test scenario for Mix Challenge mode. The mode is implemented in the business logic layer (`BossOrchestrator.startMixChallenge()`, `CardProvider.buildMixChallengeCards()`) and the UI layer supports it (`TrainingScreen.MIX_CHALLENGE` mode), but there is currently no button or tile on the HomeScreen to launch it.

---

## 1. Mode Overview

**Mix Challenge** is an interleaved practice mode that selects cards from multiple lessons with maximum tense alternation. Research shows interleaved practice produces ~43% better long-term retention compared to blocked practice.

### Key Characteristics

| Property | Value |
|----------|-------|
| **Card Source** | All lessons in current language/pack (up to 5 cards per lesson from main pool) |
| **Session Size** | Fixed at 10 cards |
| **Entry Requirement** | At least 2 started lessons (lessons with `uniqueCardShows > 0`) |
| **Selection Strategy** | Round-robin alternation by tense to maximize variety |
| **Mastery Counting** | Same as regular training (VOICE/KEYBOARD only, not WORD_BANK) |
| **PracticeType** | `TRANSLATION` (same as regular sub-lessons) |
| **UI Mode** | `TrainingScreenMode.MIX_CHALLENGE` |
| **Visual Theme** | Purple/special surface for tense chips |

---

## 2. Preconditions

### Data Requirements
1. **Multiple lessons available** in the active pack (minimum 2, recommended 3+)
2. **At least 2 lessons started** (have `uniqueCardShows > 0` in mastery state)
3. **Cards with tenses** - alternation works best when cards have different tense values
4. **User has progress** in the current language/pack

### App State
- App is launched and logged in
- Active pack has 3+ lessons with content
- User has completed at least one sub-lesson in 2+ different lessons

---

## 3. Test Steps (When UI Entry Point Exists)

> **NOTE:** These steps assume a UI entry point is added to HomeScreen (e.g., a "Mix Challenge" tile or button). Currently, this feature is **DORMANT** because no such entry point exists.

### Step 1: Navigate to Mix Challenge Entry

| Action | Expected Result |
|--------|-----------------|
| Launch app and arrive at HomeScreen | HomeScreen displays lesson tiles, drills, and mode options |
| Locate Mix Challenge tile/button | Tile is visible, shows "Mix Challenge" label |
| Verify tile is enabled (not grayed out) | At least 2 lessons have been started, so tile should be active |

**Verification:**
- If fewer than 2 lessons have been started, the tile should be disabled or show a message
- Tile should have distinct visual styling (purple theme recommended)

### Step 2: Launch Mix Challenge

| Action | Expected Result |
|--------|-----------------|
| Click/tap Mix Challenge tile | App navigates to `Routes.MIX_CHALLENGE` |
| Observe screen transition | TrainingScreen opens with `TrainingScreenMode.MIX_CHALLENGE` |
| Check header | Shows "Mix Challenge" or similar title (not "Lesson X") |
| Verify first card | A card from one of the started lessons is displayed |

**Verification:**
- `state.navigation.mode == TrainingMode.MIX_CHALLENGE`
- `state.cardSession.sessionState == PAUSED` (initial state)
- `state.cardSession.subLessonTotal == 10`
- `state.cardSession.currentCard != null`

### Step 3: Verify Card Diversity

| Action | Expected Result |
|--------|-----------------|
| Observe current card's tense label | Tense chip shown in purple/special theme |
| Navigate to next card (Click Next) | Second card appears |
| Compare tenses | Ideally different from first card |
| Continue to 3rd-5th cards | Each card should alternate tenses when possible |

**Verification:**
- Cards should come from different lessons (check lesson context if visible)
- Tense alternation should be visible in consecutive cards
- Maximum 5 cards from any single lesson

### Step 4: Answer Cards (Input Mode Testing)

| Action | Expected Result |
|--------|-----------------|
| Submit correct answer (VOICE/KEYBOARD) | `correctCount` increments, card advances |
| Submit incorrect answer | `incorrectCount` increments, hint shown |
| Use WORD_BANK mode | Answer accepted but **does NOT count for mastery** |

**Verification Points:**
- Answer validation uses same logic as regular training
- Mastery counting follows standard rules (VOICE/KEYBOARD only)
- WORD_BANK answers do NOT increment `uniqueCardShows`

### Step 5: Complete Session

| Action | Expected Result |
|--------|-----------------|
| Answer all 10 cards correctly | Session completes |
| Observe completion screen | Shows "Challenge Complete!" title |
| Check stats display | Shows correct/incorrect counts, time |
| Press OK button | Navigate back to HomeScreen |

**Verification:**
- `state.cardSession.currentCard == null` (session exhausted)
- `state.navigation.mode == TrainingMode.MIX_CHALLENGE` (preserved)
- `TrainingScreenMode.MIX_CHALLENGE` completion content displayed

### Step 6: Verify Mastery Persistence

| Action | Expected Result |
|--------|-----------------|
| Exit session, go to HomeScreen | HomeScreen displays updated flower states |
| Check flower progress for lessons used | `uniqueCardShows` incremented for correctly answered cards |
| Verify practice type recorded | `PracticeType.TRANSLATION` added to today's completed types |

**Verification:**
- Each correctly answered card increments `uniqueCardShows` for its lesson
- Streak fire count increments if TRANSLATION type wasn't already completed today
- Mastery progress persists across app restarts

---

## 4. Edge Cases & Error Conditions

### Case 1: Fewer Than 2 Started Lessons

| Condition | Expected Behavior |
|-----------|-------------------|
| User has only 1 started lesson | Mix Challenge entry point should be disabled |
| User clicks anyway (if enabled) | `buildMixChallengeCards()` returns empty list, session should not start |

### Case 2: No Cards Available

| Condition | Expected Behavior |
|-----------|-------------------|
| All lessons have 0 cards or are hidden | Session fails to start, show error message |
| Hidden cards filter out all candidates | Fallback to include first lesson even if not started |

### Case 3: All Cards Same Tense

| Condition | Expected Behavior |
|-----------|-------------------|
| All cards in pool have same tense | Alternation sort detects single tense, returns shuffled list |
| Session proceeds normally | No error, just no alternation benefit |

### Case 4: Session Interruption

| Condition | Expected Behavior |
|-----------|-------------------|
| User presses Back during session | Exit confirmation dialog shown |
| User confirms exit | Session ends, progress saved, return to HomeScreen |
| User cancels exit | Resume session from current card |

---

## 5. Data Flow Verification

### Card Source Verification

```
buildMixChallengeCards(lessons, startedLessonIds, count = 10)
├── Filter lessons: only those in startedLessonIds
├── For each lesson: take up to 5 cards from mainPoolCards
├── Shuffle cards within each lesson's selection
├── Apply alternation sort by tense
└── Return first 10 cards
```

**Test assertion:** After starting Mix Challenge, verify that:
- At least 2 different lessons are represented in the card set
- Cards are shuffled (not in lesson order)
- Tense alternation is visible where possible

### Mastery Counting Verification

```
submitAnswer() in MIX_CHALLENGE mode
├── VOICE mode: increments mastery (uniqueCardShows++)
├── KEYBOARD mode: increments mastery (uniqueCardShows++)
└── WORD_BANK mode: does NOT increment mastery
```

**Test assertion:** After completing a Mix Challenge session:
- Check `MasteryStore` for each lesson used
- Verify `uniqueCardShows` count matches correct VOICE/KEYBOARD answers
- Verify WORD_BANK answers did not increment mastery

### PracticeType Mapping Verification

```
Mix Challenge completion → PracticeType.TRANSLATION
├── Used for streak fire counting
├── Same as regular training sub-lessons
└── Different from VOCAB (vocab drill) and VERB (verb drill)
```

**Test assertion:** After completing Mix Challenge:
- Check `StreakData.completedTypesToday`
- Verify `PracticeType.TRANSLATION` is present
- Fire count should increment if this is first TRANSLATION session today

---

## 6. UI Elements to Verify

### TrainingScreen in MIX_CHALLENGE Mode

| Element | Expected Appearance |
|---------|---------------------|
| Header | Shows "Mix Challenge" or distinctive title |
| Tense chip | Purple/special theme (`MixChallengeSurface`, `MixChallengeText`) |
| Progress indicator | Shows "X / 10" (fixed session size) |
| Sub-lesson indicator | Hidden or shows "Mix" (not tied to specific lesson) |
| Completion screen | "Challenge Complete!" title, standard stats display |

### HomeScreen (When Entry Point Added)

| Element | Expected Appearance |
|---------|---------------------|
| Mix Challenge tile | Distinct visual styling (purple/accent color) |
| Tile state | Enabled if 2+ lessons started, disabled otherwise |
| Position | Near other mode options (Daily Practice, drills) |
| Label | "Mix Challenge" or localized equivalent |

---

## 7. Implementation Status

### Completed Components

| Component | Status | Notes |
|-----------|--------|-------|
| `CardProvider.buildMixChallengeCards()` | ✅ IMPLEMENTED | Full card selection with alternation algorithm |
| `BossOrchestrator.startMixChallenge()` | ✅ IMPLEMENTED | Session setup, state management |
| `TrainingMode.MIX_CHALLENGE` enum | ✅ IMPLEMENTED | Defined in Models.kt |
| `TrainingScreenMode.MIX_CHALLENGE` enum | ✅ IMPLEMENTED | Defined in Models.kt |
| `Routes.MIX_CHALLENGE` | ✅ IMPLEMENTED | Route constant defined in GrammarMateApp.kt |
| NavHost composable(Routes.MIX_CHALLENGE) | ✅ IMPLEMENTED | Renders TrainingScreenContent |
| TrainingScreen MIX_CHALLENGE styling | ✅ IMPLEMENTED | Purple theme for tense chips |
| SessionCompletionContent MIX_CHALLENGE | ✅ IMPLEMENTED | "Challenge Complete!" title |

### Missing Components

| Component | Status | Notes |
|-----------|--------|-------|
| **HomeScreen entry point** | ❌ MISSING | No tile/button to launch Mix Challenge |
| **String resources** | ⚠️ PARTIAL | May need localized strings for UI labels |
| **Documentation** | ⚠️ PARTIAL | This doc is the first dedicated spec |

---

## 8. Recommended Implementation Path

To activate Mix Challenge mode, the following steps are needed:

1. **Add HomeScreen tile** (primary blocker)
   - Add Mix Challenge tile to HomeScreen grid
   - Position below lesson tiles or in a "Modes" section
   - Disable tile if fewer than 2 lessons have been started

2. **Add navigation trigger**
   - Wire tile click to `bossOrchestrator.startMixChallenge()`
   - Navigate to `Routes.MIX_CHALLENGE` on success
   - Show error toast if `startMixChallenge()` returns false

3. **Add string resources** (if not already present)
   - `home_mix_challenge` label
   - `mix_challenge_title` for TrainingScreen header
   - `mix_challenge_disabled` message for insufficient lessons

4. **Test the full flow**
   - Use this click test scenario as verification
   - Test with 2, 3, 5+ lessons
   - Verify alternation algorithm with same-tense cards

---

## 9. References

- **Spec:** `docs/specification/08-training-viewmodel.md` (section on TrainingMode.MIX_CHALLENGE)
- **Code:** `app/src/main/java/com/alexpo/grammermate/feature/training/CardProvider.kt` (buildMixChallengeCards)
- **Code:** `app/src/main/java/com/alexpo/grammermate/feature/boss/BossOrchestrator.kt` (startMixChallenge)
- **Code:** `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt` (MIX_CHALLENGE styling)
- **UI Router:** `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` (Routes.MIX_CHALLENGE)
