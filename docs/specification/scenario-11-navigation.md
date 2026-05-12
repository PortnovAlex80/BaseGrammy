# Scenario 11: Complete Navigation Flow Verification

Verification of all screen transitions, BackHandlers, and dialog triggers against specs 07, 13, and 19.

---

## Step 1: App Launch -- AppRoot -> GrammarMateApp -> Home

**Code path:**
- `MainActivity.onCreate` -> `setContent { AppRoot() }` (spec 13.1.4)
- `AppRoot()` collects `RestoreNotifier.restoreState` (AppRoot.kt:23)
- If `status == DONE`: renders `GrammarMateApp()` (AppRoot.kt:24)
- `GrammarMateApp()` creates `screen = parseScreen(state.initialScreen)` (GrammarMateApp.kt:145)
- `state.initialScreen` is hardcoded to `"HOME"` (spec 13.3.3)
- `when(screen)` renders `HomeScreen` (GrammarMateApp.kt:309)

**Expected:** StartupScreen while restoring, then GrammarMateApp -> HOME.
**Actual:** MATCHES SPEC. Entry sequence is correct.

---

## Step 2: Home -> Lesson Roadmap -> tap sub-lesson -> Training

**Code path:**
- Home: tap lesson tile -> `onSelectLesson(lessonId)` -> `vm.selectLesson(lessonId)` + `screen = AppScreen.LESSON` (GrammarMateApp.kt:318-320)
- Home: tap "Continue Learning" card/button -> `onPrimaryAction` -> `screen = AppScreen.LESSON` (GrammarMateApp.kt:317)
- LessonRoadmapScreen: tap sub-lesson -> `onStartSubLesson(index)` -> `vm.selectSubLesson(index)` + `screen = AppScreen.TRAINING` (GrammarMateApp.kt:347-349)

**Expected:** HOME -> LESSON -> TRAINING.
**Actual:** MATCHES SPEC.

---

## Step 3: Training -> Back pressed -> returns to Roadmap? Or Home?

**Code path:**
- `BackHandler(enabled = screen == AppScreen.TRAINING && !showSettings)` -> `showExitDialog = true` (GrammarMateApp.kt:203-205)
- Exit dialog shows "End session?" with "Exit" and "Cancel" (GrammarMateApp.kt:518-548)
- User taps "Exit": calls `vm.finishSession()` (or `vm.finishBoss()` / `vm.exitDrillMode()` for boss/drill), then `screen = AppScreen.LESSON` (GrammarMateApp.kt:522-536)

**Expected per spec 07.3.3:** Back press on TRAINING shows exit confirmation dialog. Confirming navigates to LESSON (not HOME).
**Actual:** MATCHES SPEC. Back press does NOT return to HOME directly; it shows exit dialog which goes to LESSON.

---

## Step 4: Training -> sub-lesson complete -> auto-advance or manual?

**Code path:**
- When `state.subLessonFinishedToken != lastFinishedToken.value` AND `screen == TRAINING`:
  - Updates token, sets `screen = AppScreen.LESSON` (GrammarMateApp.kt:509-512)
- This is a token-based auto-transition. No user action required.

**Expected per spec 07.3.4:** Token change triggers auto-navigation to LESSON.
**Actual:** MATCHES SPEC. Auto-advance via token detection.

---

## Step 5: Training -> lesson complete -> where does user land?

**Code path:**
- Boss finish: `state.bossFinishedToken != lastBossFinishedToken.value` -> `screen = AppScreen.LESSON` (GrammarMateApp.kt:513-516)
- Sub-lesson finish: same pattern -> `screen = AppScreen.LESSON` (GrammarMateApp.kt:509-512)

**Expected per spec:** Session completion always returns to LESSON (Lesson Roadmap).
**Actual:** MATCHES SPEC.

---

## Step 6: Home -> Daily Practice tile -> DailyPracticeScreen

**Code path:**
- Home: tap DailyPracticeEntryTile -> `onOpenElite` callback (GrammarMateApp.kt:967)
- `onOpenElite` logic (GrammarMateApp.kt:323-338):
  - Computes `level = lessonIndex + 1`
  - Checks `vm.hasResumableDailySession()`:
    - If resumable: shows `showDailyResumeDialog` (GrammarMateApp.kt:550-586)
    - If not: launches coroutine, calls `vm.startDailyPractice(level)`, sets `screen = AppScreen.DAILY_PRACTICE` (GrammarMateApp.kt:329-336)
- Loading overlay shown while `isLoadingDaily == true` (GrammarMateApp.kt:487-507)

**Expected per spec 07.15.1:** Daily Practice tile -> resume check -> loading -> DAILY_PRACTICE screen.
**Actual:** MATCHES SPEC.

---

## Step 7: Daily Practice -> Back pressed mid-session -> dialog? data loss?

**Code path:**
- `BackHandler(enabled = screen == AppScreen.DAILY_PRACTICE && !showSettings)` -> `screen = AppScreen.HOME` (GrammarMateApp.kt:209-211)
- ALSO: DailyPracticeScreen has its OWN internal exit dialog:
  - `DailyPracticeHeader` has `showExitDialog` state (DailyPracticeScreen.kt:758)
  - Back arrow in header -> `showExitDialog = true` (DailyPracticeScreen.kt:786)
  - Exit dialog: "Exit practice? Your progress in this session will be lost." with "Exit" and "Stay" (DailyPracticeScreen.kt:765-780)
  - Exit -> calls `onExit()` -> `vm.cancelDailySession()` + `screen = HOME` (GrammarMateApp.kt:393-394)

**DISCREPANCY FOUND:**
- The **router-level BackHandler** (GrammarMateApp.kt:209-211) directly sets `screen = HOME` without showing any confirmation dialog. This means pressing the Android system back button **immediately exits** to HOME with no warning, losing all session data.
- The **in-screen back arrow** in the DailyPracticeHeader shows a confirmation dialog, but this only fires when the user taps the UI back arrow, not the system back button.
- **Spec 19.5** states: "Back button -> exit confirmation dialog ('Exit practice? Progress will be lost.')"
- **Spec 07.3.3** table says: "DAILY_PRACTICE -> Navigate to HOME" (no mention of confirmation).
- **Spec 07.3.3 vs Spec 19.5** contradict each other. The code matches spec 07.3.3 but NOT spec 19.5.

**Verdict:** Bug / spec inconsistency. System back press on DAILY_PRACTICE exits immediately without confirmation, potentially losing session progress. The in-screen back arrow correctly shows confirmation.

---

## Step 8: Daily Practice -> complete -> HOME or completion screen?

**Code path:**
- `onComplete` callback: `vm.cancelDailySession()` + `screen = AppScreen.HOME` (GrammarMateApp.kt:397-399)
- Same for `onExit`: `vm.cancelDailySession()` + `screen = AppScreen.HOME` (GrammarMateApp.kt:393-395)

**Expected per spec 19.5:** Completion shows a completion screen ("Session Complete!") with "Back to Home" button. The completion screen is rendered within DailyPracticeScreen itself.
**Actual:** The completion screen IS shown within DailyPracticeScreen (as part of its internal state), and the "Back to Home" button calls `onComplete` which navigates to HOME. The router-level `onComplete` callback navigates to HOME. MATCHES SPEC for the final navigation destination.

---

## Step 9: Home -> Verb Drill tile -> VerbDrillScreen

**Code path:**
- Home: `hasVerbDrill` computed from `lessonStore.hasVerbDrill(packId, languageId)` (GrammarMateApp.kt:168-170)
- If `hasVerbDrill`: `VerbDrillEntryTile` rendered (GrammarMateApp.kt:950-952)
- Tap -> `onOpenVerbDrill` -> `screen = AppScreen.VERB_DRILL` (GrammarMateApp.kt:341)
- Router creates `VerbDrillViewModel` and calls `reloadForPack` or `reloadForLanguage` (GrammarMateApp.kt:456-466)

**Expected per spec 07.2.1, 07.13.1:** Verb Drill tile visible when pack has verbDrill section; navigates to VERB_DRILL screen with its own ViewModel.
**Actual:** MATCHES SPEC.

---

## Step 10: Verb Drill -> Back pressed -> Home

**Code path:**
- `BackHandler(enabled = screen == AppScreen.VERB_DRILL && !showSettings)` -> `screen = AppScreen.HOME` (GrammarMateApp.kt:221-223)
- Also: `VerbDrillScreen` has `onBack` -> `screen = AppScreen.HOME` (GrammarMateApp.kt:465)

**Expected per spec 07.3.3:** Back -> HOME.
**Actual:** MATCHES SPEC.

---

## Step 11: Home -> Vocab Drill tile -> VocabDrillScreen

**Code path:**
- Home: `hasVocabDrill` computed from `lessonStore.hasVocabDrill(packId, languageId)` (GrammarMateApp.kt:171-173)
- If `hasVocabDrill`: `VocabDrillEntryTile` rendered (GrammarMateApp.kt:955-961)
- Tap -> `onOpenVocabDrill` -> `screen = AppScreen.VOCAB_DRILL` (GrammarMateApp.kt:342)
- Router creates `VocabDrillViewModel` and calls `reloadForPack` or `reloadForLanguage` (GrammarMateApp.kt:469-475)

**Expected per spec:** Vocab Drill tile visible when pack has vocabDrill section.
**Actual:** MATCHES SPEC.

---

## Step 12: Home -> Settings sheet -> which settings are available?

**Code path:**
- Home: tap settings gear -> `onOpenSettings` (GrammarMateApp.kt:888-889)
- `onOpenSettings`: saves `previousScreen = screen`, calls `vm.pauseSession()`, sets `showSettings = true` (GrammarMateApp.kt:313-315)
- `SettingsSheet` rendered as overlay (GrammarMateApp.kt:228-260)

**Available settings (per code and spec 19.13):**
- Test Mode toggle
- Show Ladder button
- Vocabulary Sprint limit
- Pronunciation speed slider (TTS)
- Voice recognition toggle (Offline ASR)
- Translation text size slider (Russian text scale)
- Language dropdown
- Pack dropdown
- Add language
- Import lesson pack (ZIP)
- Import lesson (CSV)
- Reset/Reload lesson
- Create empty lesson
- Delete all lessons
- Delete pack
- Reset all progress
- Profile name
- Save progress now
- Restore from backup

**Expected per spec 19.13:** Full settings panel with all above items.
**Actual:** MATCHES SPEC.

---

## Step 13: Settings -> Language switch -> what resets? where does user land?

**Code path:**
- `vm.selectLanguage(languageId)` (TrainingViewModel.kt:389-419):
  - Clears session cards, boss cards, elite cards
  - Loads new lessons for the language
  - Derives new `activePackId` and `activePackLessonIds`
  - Resets: `currentIndex = 0`, `correctCount = 0`, `incorrectCount = 0`, `inputText = ""`, `lastResult = null`, `answerText = null`, `inputMode = VOICE`, `sessionState = PAUSED`
  - Resets boss-related state, elite-related state
- User remains on the current screen (settings sheet is still open)

**Expected per spec 19.13:** "Changing language reloads lessons and resets active pack."
**Actual:** MATCHES SPEC. No screen navigation occurs; user stays where they are. Session state is fully reset.

---

## Step 14: Settings -> Pack import -> what happens during import?

**Code path:**
- `vm.importLessonPack(uri)` called from SettingsSheet
- The import runs on IO, extracts ZIP, reads manifest, copies CSV files
- Progress state updated in `TrainingUiState`

**Expected per spec 19.13:** File picker launches, ZIP is imported, lessons become available.
**Actual:** MATCHES SPEC. No screen navigation during import; user stays in settings.

---

## Step 15: All 18 dialogs -- verify trigger conditions match spec

| # | Dialog | Spec 07.4.1 Trigger | Code Trigger | Match? |
|---|--------|---------------------|--------------|--------|
| 1 | Settings Sheet | Tap gear icon | `onOpenSettings` -> `showSettings = true` | YES |
| 2 | Exit Confirmation | Back on TRAINING or Stop button | `BackHandler` -> `showExitDialog = true`; `onRequestExit` -> same | YES |
| 3 | Welcome (Name) | `userName == "GrammarMateUser"` on non-HOME | `LaunchedEffect(screen, userName)` checks condition (GrammarMateApp.kt:302-306) | YES |
| 4 | TTS Download | Tap speaker when model not ready | `onTtsSpeak` lambda checks `!ttsModelReady` -> `showTtsDownloadDialog = true` (GrammarMateApp.kt:178-184) | YES |
| 5 | TTS Metered Network | TTS download on metered connection | `state.ttsMeteredNetwork` drives dialog (GrammarMateApp.kt:289-294) | YES |
| 6 | ASR Metered Network | ASR download on metered connection | `state.asrMeteredNetwork` drives dialog (GrammarMateApp.kt:296-301) | YES |
| 7 | Daily Resume | Daily Practice tile when resumable session exists | `vm.hasResumableDailySession()` -> `showDailyResumeDialog = true` (GrammarMateApp.kt:326-328, 550-586) | YES |
| 8 | Story Error | `storyErrorMessage != null` | `state.storyErrorMessage != null` (GrammarMateApp.kt:588-599) | YES |
| 9 | Boss Error | `bossErrorMessage != null` | `state.bossErrorMessage != null` (GrammarMateApp.kt:600-611) | YES |
| 10 | Boss Reward | `bossRewardMessage != null && bossReward != null` | `state.bossRewardMessage != null && state.bossReward != null` (GrammarMateApp.kt:612-636) | YES |
| 11 | Streak | `streakMessage != null` | `state.streakMessage != null` (GrammarMateApp.kt:637-669) | YES |
| 12 | Drill Start | Tap drill tile in roadmap | `state.drillShowStartDialog` (GrammarMateApp.kt:670-683) | YES |
| 13 | Method Explanation | Tap "How This Training Works" | `showMethod` local state in HomeScreen (GrammarMateApp.kt:989-1005) | YES |
| 14 | Locked Lesson Hint | Tap empty locked tile | `showLockedLessonHint` in HomeScreen (GrammarMateApp.kt:1006-1017) | YES |
| 15 | Early Start Lesson | Tap locked tile with lessonId | `earlyStartLessonId != null` in HomeScreen (GrammarMateApp.kt:1018-1040) | YES |
| 16 | Early Start Sub-Lesson | Tap locked sub-lesson tile | `earlyStartSubLessonIndex` in LessonRoadmapScreen | YES |
| 17 | Boss Locked | Tap boss when < 15 exercises | `bossLockedMessage` in LessonRoadmapScreen | YES |
| 18 | Export Bad Sentences | After exporting | `exportMessage` in TrainingScreen AnswerBox | YES |

**Verdict:** All 18 dialog triggers match spec.

---

## Step 16: BackHandler per screen -- verify behavior matches spec 07.3.3

| Screen | Spec Behavior | Code (GrammarMateApp.kt) | Match? |
|--------|--------------|--------------------------|--------|
| TRAINING | Show exit dialog | Line 203-205: `showExitDialog = true` | YES |
| LESSON | Navigate to HOME | Line 206-208: `screen = HOME` | YES |
| DAILY_PRACTICE | Navigate to HOME | Line 209-211: `screen = HOME` | PARTIAL -- see Step 7 |
| STORY | Navigate to LESSON | Line 212-214: `screen = LESSON` | YES |
| LADDER | Navigate to `previousScreen`; resume if TRAINING | Line 215-220: `screen = previousScreen` + `vm.resumeFromSettings()` | YES |
| VERB_DRILL | Navigate to HOME | Line 221-223: `screen = HOME` | YES |
| VOCAB_DRILL | Navigate to HOME; also call `refreshVocabMasteryCount()` | Line 224-226: `screen = HOME` **BUT NO refreshVocabMasteryCount()** | **NO** |
| HOME | Not registered (system default) | No BackHandler for HOME | YES |

**DISCREPANCY on VOCAB_DRILL:**
- Spec 07.3.3 says: "Navigate to HOME; also call `vm.refreshVocabMasteryCount()`"
- Code BackHandler (line 224-226): `screen = AppScreen.HOME` -- does NOT call `refreshVocabMasteryCount()`
- The `refreshVocabMasteryCount()` call only exists in the `onBack` callback passed to `VocabDrillScreen` (GrammarMateApp.kt:478-481), which fires when the user taps the in-screen back button, NOT the system back button.
- **Impact:** Pressing system back from VOCAB_DRILL goes to HOME but does NOT refresh the mastered count shown on the home screen tile. The count will be stale until the user revisits VOCAB_DRILL and exits via the in-screen button.

**Verdict:** Bug. BackHandler for VOCAB_DRILL should also call `vm.refreshVocabMasteryCount()`.

---

## Step 17: ELITE/VOCAB enum values -> redirect to HOME

**Code path:**
- `AppScreen.ELITE -> { screen = AppScreen.HOME }` (GrammarMateApp.kt:363)
- `AppScreen.VOCAB -> { screen = AppScreen.HOME }` (GrammarMateApp.kt:364)
- `parseScreen()` fallback: catches `IllegalArgumentException` -> `HOME` (GrammarMateApp.kt:702-704)

**Expected per spec 07.2.2:** ELITE and VOCAB redirect to HOME immediately. Unknown values fall back to HOME.
**Actual:** MATCHES SPEC.

---

## Step 18: Boss reward dialog -> after boss -> medal display -> dismiss -> HOME?

**Code path:**
- Boss finishes -> `bossFinishedToken` changes -> `screen = AppScreen.LESSON` (GrammarMateApp.kt:513-516)
- After returning to LESSON, `state.bossRewardMessage != null && state.bossReward != null` triggers reward dialog (GrammarMateApp.kt:612-636)
- Dialog shows colored trophy icon (bronze/silver/gold) (GrammarMateApp.kt:621-632)
- Dismiss via "OK" or `onDismissRequest` -> `vm.clearBossRewardMessage()` (GrammarMateApp.kt:614, 616)

**Expected per spec:** Boss completes -> returns to LESSON -> reward dialog shown -> dismiss -> stays on LESSON.
**Actual:** MATCHES SPEC. After dismissing the reward dialog, the user stays on LESSON (not HOME).

---

## Summary of Discrepancies

### DISCREPANCY 1: DAILY_PRACTICE BackHandler missing confirmation dialog (MEDIUM severity)

**Location:** GrammarMateApp.kt:209-211
**Spec reference:** Spec 19.5 states "Back button -> exit confirmation dialog ('Exit practice? Progress will be lost.')."
**Actual behavior:** System back press immediately navigates to HOME without confirmation. The in-screen back arrow does show a confirmation dialog, but the system BackHandler bypasses it.
**Impact:** User can lose daily practice progress by accidentally pressing system back.
**Recommendation:** Change the DAILY_PRACTICE BackHandler to NOT navigate directly. Instead, let the DailyPracticeScreen handle back press internally (it already has its own exit dialog). Alternatively, add a confirmation dialog at the router level similar to the TRAINING exit dialog.

### DISCREPANCY 2: VOCAB_DRILL BackHandler missing refreshVocabMasteryCount (LOW severity)

**Location:** GrammarMateApp.kt:224-226
**Spec reference:** Spec 07.3.3 states "Navigate to HOME; also call `vm.refreshVocabMasteryCount()`."
**Actual behavior:** System back press navigates to HOME but does NOT call `vm.refreshVocabMasteryCount()`. The in-screen `onBack` callback does call it, but only for the in-screen back button.
**Impact:** The "X mastered" counter on the home screen VocabDrillEntryTile becomes stale when user exits via system back button.
**Recommendation:** Add `vm.refreshVocabMasteryCount()` to the VOCAB_DRILL BackHandler:
```kotlin
BackHandler(enabled = screen == AppScreen.VOCAB_DRILL && !showSettings) {
    vm.refreshVocabMasteryCount()
    screen = AppScreen.HOME
}
```

### DISCREPANCY 3: Spec inconsistency between 07.3.3 and 19.5 for DAILY_PRACTICE back behavior

**Spec 07.3.3:** "DAILY_PRACTICE -> Navigate to HOME" (implies direct navigation)
**Spec 19.5:** "Back button -> exit confirmation dialog" (implies confirmation before exit)
**Code:** Matches 07.3.3 (direct navigation), contradicts 19.5.
**Recommendation:** Align both specs. Since the DailyPracticeScreen has an internal exit dialog for its back arrow, the intent was likely to also confirm on system back press. Update spec 07.3.3 to match 19.5, and fix the code accordingly.

---

## Items Verified with No Discrepancies

- App launch sequence (AppRoot -> GrammarMateApp -> HOME)
- HOME -> LESSON -> TRAINING navigation
- Training exit dialog (shows confirmation, navigates to LESSON)
- Sub-lesson/boss token-based auto-transitions
- Training session completion returns to LESSON
- Daily Practice start flow (resume check, loading overlay)
- Daily Practice completion -> HOME
- Verb Drill tile visibility and navigation
- Verb Drill back -> HOME
- Vocab Drill tile visibility and navigation
- Vocab Drill in-screen back -> HOME with refreshVocabMasteryCount
- Settings sheet opening and dismissal from any screen
- Language switch resets session state
- All 18 dialog trigger conditions
- All BackHandler registrations except DAILY_PRACTICE and VOCAB_DRILL (see discrepancies)
- ELITE/VOCAB enum backward compatibility redirects
- Boss reward dialog flow (boss finish -> LESSON -> reward dialog -> dismiss -> stay on LESSON)
- LADDER back navigation to previousScreen with session resume
- STORY back navigation to LESSON
