# User Journey Models — Step-by-Step Behavior Tracing

> Version: 1.1 | Date: 2026-05-16 | Branch: main
> Purpose: Model each user journey from app launch to feature completion. Compare spec expectations with actual code behavior. Discrepancies are flagged for correction.

---

## Common Behavior Patterns

Many screens share the same foundational mechanics. Patterns are extracted to eliminate duplication and ensure consistent bug fixes.

### Pattern A: Sentence Training

**Base flow:** card with prompt → input mode selection → Play (timer start) → answer input → Check → normalization → comparison → result → next card.

**Detailed steps of the base flow:**

| Step | Action | System Response |
|------|--------|-----------------|
| A.1 | User sees a card | PAUSED: prompt, input area, Play / Check / Next / Show Answer / Exit buttons |
| A.2 | Presses Play | `togglePause()` → `startSession()`: sessionState=ACTIVE, timer started, inputText cleared |
| A.3 | Enters answer, presses Check | `submitAnswer()` → normalization → comparison against accepted answers |
| A.4a | Answer correct | correctCount++, green badge, auto-calls `nextCardInternal()` or pauses if last card |
| A.4b | Answer incorrect (attempts 1-2) | incorrectCount++, red badge, retry (VOICE: auto-restart) |
| A.4c | Answer incorrect (attempt 3) | sessionState=HINT_SHOWN, timer paused, all answers shown |
| A.5 | Presses Next | `nextCard()`: index++, new card, sessionState=ACTIVE |
| A.6 | Presses Show Answer | `showAnswer()`: pause, show answers, sessionState=HINT_SHOWN |
| A.7 | Presses Exit | Confirmation dialog → `finishSession()` → navigate back |

**Used in:**

| Journey | Implementation | Differences from base flow |
|---------|---------------|---------------------------|
| Journey 4: Sub-lesson | SessionState enum | Base implementation. All 3 input modes (VOICE/KEYBOARD/WORD_BANK). Mastery is recorded |
| Journey 5: Boss | SessionState enum | No time limit (timing only). BRONZE/SILVER/GOLD rewards. Unlimited attempts. BUG: records mastery |
| Journey 6 Block 1: Daily Translate | DailyPracticeSessionProvider | 10 cards. Auto-advance 400ms. VOICE/KEYBOARD/WORD_BANK modes rotate |
| Journey 6 Block 3: Daily Verbs | DailyPracticeSessionProvider | 10 cards. No VOICE (KEYBOARD/WORD_BANK only). Otherwise same as Block 1 |
| Journey 7: Verb Drill | CardSessionStateMachine | No VOICE. Shows verb infinitive, rank, tense on card. Auto-advance 500ms (race BUG-NAV-012) |

### Pattern B: Flashcard Rating

**Base flow:** front side (word) → optional voice input → flip → back side (translation + forms + collocations) → rating (Again/Hard/Good/Easy) → SRS step updated → next card.

**Detailed steps of the base flow:**

| Step | Action | System Response |
|------|--------|-----------------|
| B.1 | User sees front side | POS badge, rank, word (32sp bold). Buttons: TTS, Mic, Skip, Flip |
| B.2 | (Optional) Voice input | Up to 3 attempts. Comparison with synonyms ("/" separator). Auto-flip on correct or max attempts |
| B.3 | Card flip | Back side: word + translation + forms + collocations |
| B.4 | Rating selection | AGAIN → step=0, HARD → step unchanged, GOOD → step+1, EASY → step+2 |
| B.5 | Auto-advance to next card | Index++, load next word |

**Used in:**

| Journey | Differences from base flow |
|---------|---------------------------|
| Journey 6 Block 2: Daily Vocab | 5 cards. No filters (auto-selected). No voice input on front side |
| Journey 8: Vocab Drill | Full version. Filters: direction (IT→RU / RU→IT), POS, frequency. TTS button. Skip button. Voice input |

### Pattern C: Async Operation

**Base flow:** user action → background task launched → progress indicator → completion → state update.

**Used in:**
- Journey 9: Pack import (synchronous on main thread — BUG)
- Journey 10: TTS download (async with progress)
- Journey 11: Backup/Restore (synchronous without progress — BUG)

---

## Journey 1: First Launch (Cold Start)

### Step 1.1: User taps app icon

| Aspect | Details |
|--------|---------|
| **User action** | Taps GrammarMate icon on home screen |
| **System response** | MainActivity.onCreate() → setContentView → AppRoot composable |
| **What user sees** | Splash screen / loading indicator |
| **Navigation** | None yet — waiting for initialization |
| **Spec source** | 13-app-entry-and-navigation.md §1 |
| **Discrepancy** | None |

### Step 1.2: App initialization

| Aspect | Details |
|--------|---------|
| **System response** | AppRoot checks backup restore status. First launch: no backup, continues immediately. Returning user: checks `restoreState` |
| **What user sees** | Loading spinner on StartupScreen |
| **Navigation** | Stays on StartupScreen until `restoreState.status == DONE` |
| **Spec source** | 13-app-entry-and-navigation.md §2 |
| **Discrepancy** | None |

### Step 1.3: First launch — no lessons

| Aspect | Details |
|--------|---------|
| **System response** | `forceReloadDefaultPacks()` loads default lesson packs from assets. HomeScreen loads with lesson data |
| **What user sees** | HomeScreen: language selector, lesson grid (1+), "Continue learning" card, Daily Practice tile, Verb Drill tile (if pack contains verbs), Flashcards tile (if pack contains vocabulary) |
| **Navigation** | StartupScreen → HOME |
| **Visible elements** | HS-01 (title), HS-02 (language selector), HS-03 (lesson grid), HS-04 ("Continue" card), HS-05 (Daily Practice), HS-06 (Verb Drill if hasVerbDrill), HS-07 (Flashcards if hasVocabDrill), HS-08 (settings gear), HS-09 (streak counter) |
| **Spec source** | 19-screen-catalog.md §1, 23-screen-elements.md |
| **Discrepancy** | None — first launch is well-defined |

### Step 1.3b: Returning user — backup restore

| Aspect | Details |
|--------|---------|
| **System response** | If backup file found in Downloads/BaseGrammy/: shows restore prompt |
| **What user sees** | Restore dialog: "Backup found. Restore?" with Yes/No buttons |
| **Navigation** | Stays on StartupScreen until user decides |
| **Spec source** | 13-app-entry-and-navigation.md §2.3 |
| **Discrepancy** | None |

---

## Journey 2: Home Screen — Navigation Hub

### Step 2.1: User sees Home Screen

| Aspect | Details |
|--------|---------|
| **What user sees** | Full HomeScreen layout: title with language name, streak counter, settings gear, lesson grid, Daily Practice tile, drill tiles |
| **Available actions** | Tap lesson card → go to Lesson Roadmap (Journey 3) |
| | Tap "Daily Practice" → start daily session (Journey 6) |
| | Tap "Verb Drill" → go to verbs (Journey 7) |
| | Tap "Flashcards" → go to vocabulary (Journey 8) |
| | Tap settings gear → open settings panel (Journey 9) |
| | Tap language selector → switch active language/pack |
| **Elements** | HS-01 through HS-14 (14 elements) |
| **Spec source** | 19-screen-catalog.md §1 |
| **Discrepancy** | None |

### Step 2.2: User taps lesson card

| Aspect | Details |
|--------|---------|
| **User action** | Taps lesson card in the grid |
| **System response** | `vm.selectLesson(lessonId)` loads lesson data, computes sub-lesson states and flowers |
| **Navigation** | HOME → LESSON (LessonRoadmapScreen) |
| **What user sees next** | LessonRoadmapScreen: sub-lesson grid, flowers, boss tile (if unlocked) |
| **Spec source** | 07-app-router.md §3.2 |
| **Discrepancy** | None |

### Step 2.3: User taps "Continue learning" (primary action)

| Aspect | Details |
|--------|---------|
| **User action** | Taps primary action card on HomeScreen |
| **System response** | Same as tapping the next incomplete lesson |
| **Navigation** | HOME → LESSON |
| **Spec source** | 19-screen-catalog.md §1.4 |
| **Discrepancy** | None |

### Step 2.4: User opens Settings

| Aspect | Details |
|--------|---------|
| **User action** | Taps gear icon |
| **System response** | `vm.pauseSession()` + ModalBottomSheet with settings content |
| **What user sees** | Settings panel: language management, pack import, ASR download, ladder access, theme toggle, backup/restore, "About" section |
| **Navigation** | No navigation — modal overlay on current screen |
| **Back button behavior** | Pressing Back closes the panel. On TRAINING: also calls `vm.resumeFromSettings()` if a card exists |
| **Spec source** | 19-screen-catalog.md §11 |
| **Discrepancy** | None |

---

## Journey 3: Lesson Roadmap — Sub-lesson Selection

### Step 3.1: User sees LessonRoadmapScreen

| Aspect | Details |
|--------|---------|
| **What user sees** | Lesson name, back arrow, grid of sub-lesson tiles with flowers (LOCKED/SEED/SPROUT/BLOOM), boss tile (if unlocked: completedSubLessonCount >= 15) |
| **Available actions** | Tap sub-lesson → start training (Journey 4) |
| | Tap boss tile → start boss battle (Journey 5) |
| | Tap back arrow → return to HOME |
| **Elements** | LR-01 through LR-12 |
| **Spec source** | 19-screen-catalog.md §2 |
| **Discrepancy** | None |

### Step 3.2: User taps sub-lesson

| Aspect | Details |
|--------|---------|
| **User action** | Taps sub-lesson tile |
| **System response** | `vm.selectSubLesson(index)` creates session cards, initializes state |
| **Navigation** | LESSON → TRAINING |
| **What user sees next** | TrainingScreen with first card, PAUSED state (Play button visible) |
| **Spec source** | 07-app-router.md §3.3, scenario-01 §2 |
| **Discrepancy** | None |

### Step 3.3: User taps boss tile

| Aspect | Details |
|--------|---------|
| **User action** | Taps boss tile (visible only when unlocked) |
| **System response** | `vm.startBossLesson()` or `vm.startBossMega()` — shuffles boss cards, resets counters |
| **Navigation** | LESSON → TRAINING (boss mode) |
| **What user sees next** | TrainingScreen with "Boss Battle" header, PAUSED state |
| **Spec source** | scenario-09 §2 |
| **Discrepancy** | **BUG-NAV-001**: Boss battle records mastery via `recordCardShowForMastery()` in 3 code locations WITHOUT checking `bossActive`. Spec states that boss battles should be isolated from the mastery/flower system. This inflates `uniqueCardShows` and advances SRS intervals. |

---

## Journey 4: Sentence Training (Sub-lesson)

> **Based on: Pattern A — Sentence Training.** Base implementation. All 3 input modes. Mastery is recorded. See Pattern A for full base flow description.

### Differences from base flow

- Full Pattern A implementation — all steps A.1–A.7 work as described
- All 3 input modes: VOICE, KEYBOARD, WORD_BANK
- Mastery: `recordCardShowForMastery()` called when each card is shown
- Completion: `subLessonFinishedToken` → auto-navigate to LESSON

### Identified discrepancies

| ID | Step | Description |
|----|------|-------------|
| BUG-NAV-002 | A.4a | No visual feedback after auto-advance on correct answer. Card changes instantly without animation, easy to miss |
| BUG-NAV-003 | A.4c | Play from HINT_SHOWN does not clear `answerText`. Hint persists while session is ACTIVE, Check is active |
| BUG-NAV-004 | A.5 | Next button always active — can skip cards during ACTIVE session |
| BUG-NAV-005 | A.5 | Next on last card stays on it with no completion feedback |

---

## Journey 5: Boss Battle

> **Based on: Pattern A — Sentence Training.** Variation: rewards, no time limit, unlimited attempts.

### Differences from base flow

- No time limit — only elapsed time measurement
- Progress bar shows boss completion %
- Reward thresholds: BRONZE >50%, SILVER >75%, GOLD 100%
- Unlimited attempts (no penalty for errors)
- On reward threshold crossing: pause, reward overlay, resume after dismiss
- Completion: `finishBoss()` → computes reward → writes to ProgressStore → `bossFinishedToken` → LESSON

### Identified discrepancies

| ID | Step | Description |
|----|------|-------------|
| BUG-NAV-001 | Entire boss | Records mastery despite spec. 3 calls to `recordCardShowForMastery()` without `bossActive` check |
| BUG-NAV-006 | Reward pause | `clearBossRewardMessage()` — stale state read in `shouldResumeTimer` condition |
| BUG-NAV-007 | Completion | Reward overwrite on repeat — GOLD can be replaced by BRONZE |

---

## Journey 6: Daily Practice

> **Blocks 1 and 3: Pattern A — Sentence Training.** Block 2: Pattern B — Flashcard Rating.

### Step 6.1: User taps Daily Practice tile

| Aspect | Details |
|--------|---------|
| **User action** | Taps "Daily Practice" tile on HomeScreen |
| **System response** | Checks `hasResumableDailySession()`. If resumable → DailyResumeDialog. If not → new session |
| **What user sees** | Loading dialog (non-dismissible) while session is assembled on IO dispatcher |
| **Navigation** | HOME → DAILY_PRACTICE (after loading completes) |
| **Spec source** | 19-screen-catalog.md §1.5 |
| **Discrepancy** | **BUG-NAV-008**: Silent failure on coroutine error — user stays on HOME with no navigation and no error message |

### Step 6.2: Block 1 — Translation (10 cards)

> Pattern A with variation: DailyPracticeSessionProvider, auto-selected input mode (VOICE/KEYBOARD/WORD_BANK rotation), auto-advance 400ms.

| Difference from base Pattern A | Details |
|-------------------------------|---------|
| Card source | DailyPracticeSessionProvider |
| Input modes | Rotation between VOICE/KEYBOARD/WORD_BANK |
| Auto-advance on correct voice | 400ms delay |
| Hint behavior | 3 incorrect → shows answer. Play → advance to next (BUG-NAV-003) |
| **Discrepancy** | **BUG-NAV-009**: No retry after showing wrong answer. One cycle → hint → must advance. Inconsistent with regular training (3 attempts) |

### Step 6.3: Block 2 — Vocabulary Cards (5 cards)

> Pattern B with variation: 5 cards, no filters, no voice input.

| Difference from base Pattern B | Details |
|-------------------------------|---------|
| Card count | 5 |
| Block transition | BlockSparkleOverlay "Next: Vocabulary" ~800ms |
| Filters | None (cards auto-selected) |
| Voice input | None |
| **Discrepancy** | None |

### Step 6.4: Block 3 — Verbs (10 cards)

> Pattern A with variation: no VOICE (KEYBOARD/WORD_BANK only), otherwise same as Block 1.

| Difference from base Pattern A | Details |
|-------------------------------|---------|
| Input modes | KEYBOARD + WORD_BANK only (no VOICE) |
| Block transition | BlockSparkleOverlay "Next: Verbs" ~800ms |
| **Discrepancy** | None |

### Step 6.5: Daily session completes

| Aspect | Details |
|--------|---------|
| **Trigger** | Last task in Block 3 completed |
| **System response** | `endSession()`: `active=false`, `finishedToken=true` |
| **What user sees** | CompletionScreen with "Daily practice complete!" message |
| **User action** | Tap "Exit" → `cancelDailySession()` → HOME |
| **Discrepancy** | **BUG-NAV-010**: Completion sparkle may be skipped — `onComplete` calls `cancelDailySession()` before animation renders |

### Step 6.6: User presses Back during Daily Practice

| Aspect | Details |
|--------|---------|
| **Internal back arrow** | Shows dialog: "Exit practice? Progress will be lost." |
| **System Back button** | **Navigates directly to HOME without confirmation** |
| **Discrepancy** | **BUG-NAV-011**: BackHandler on DAILY_PRACTICE navigates to HOME directly (GrammarMateApp.kt). Spec requires confirmation dialog |

---

## Journey 7: Verb Drill

> **Based on: Pattern A — Sentence Training.** Variation: no VOICE, verb infinitive/rank/tense display, CardSessionStateMachine instead of SessionState.

### Differences from base Pattern A

- **No VOICE mode** — KEYBOARD and WORD_BANK only
- **Extra card info:** verb infinitive, rank, tense
- **State machine:** `CardSessionStateMachine` with `isPaused` + `hintAnswer` instead of `SessionState` enum
- **Auto-advance:** 500ms on correct voice (but VOICE unavailable — bug or dead code?)
- **Exit:** returns to selection screen (not HOME), system Back → HOME

### Step 7.1: User taps Verb Drill tile

| Aspect | Details |
|--------|---------|
| **User action** | Taps "Verb Drill" tile on HomeScreen |
| **Navigation** | HOME → VERB_DRILL |
| **What user sees** | VerbDrillScreen: Tense/Group dropdowns, "Start" button |
| **Spec source** | scenario-07 §1 |
| **Discrepancy** | None |

### Step 7.2: User starts drill

| Aspect | Details |
|--------|---------|
| **User action** | Selects tense/group, taps "Start" |
| **System response** | VerbDrillViewModel filters cards, loads batch of 10 |
| **What user sees** | Session with prompt, input field, Check button |
| **Discrepancy** | None |

### Identified discrepancies

| ID | Step | Description |
|----|------|-------------|
| BUG-NAV-012 | Auto-advance | Race condition: `LaunchedEffect` auto-advance after 500ms + manual Next = double `nextCard()`, card skipped |
| BUG-NAV-013 | Play from HINT_SHOWN | Overloaded semantics: "advance" vs "resume" with no visual distinction |
| BUG-NAV-014 | Exit | Internal exit → selection screen, system Back → HOME. Inconsistent |

---

## Journey 8: Vocab Drill

> **Based on: Pattern B — Flashcard Rating.** Full version with filters, TTS, voice input.

### Differences from base Pattern B

- **Full selection UI:** direction chips (IT→RU / RU→IT), POS, frequency
- **TTS button** (IT→RU direction only)
- **Voice input** on front side (up to 3 attempts, comparison with synonyms)
- **Skip button** to skip a card
- **Mastery:** stored in `drills/{packId}/word_mastery.yaml`, scoped per pack

### Step 8.1: User taps Flashcards tile

| Aspect | Details |
|--------|---------|
| **User action** | Taps "Flashcards" tile on HomeScreen |
| **Navigation** | HOME → VOCAB_DRILL |
| **What user sees** | VocabDrillScreen: direction chips, POS, frequency, "Start" button |
| **Discrepancy** | None |

### Identified discrepancies

| ID | Step | Description |
|----|------|-------------|
| BUG-NAV-015 | Card back side | isLearned threshold: data uses step>=3, UI shows "Learned" at step>=9 |
| BUG-NAV-016 | Exit | Redundant `refreshVocabMasteryCount()` on every Back press |

---

## Journey 9: Settings — Lesson Pack Import

### Step 9.1: Opening Settings

| Aspect | Details |
|--------|---------|
| **User action** | Taps gear icon on any screen |
| **System response** | `vm.pauseSession()`, ModalBottomSheet with settings |
| **What user sees** | Settings panel with content management section |
| **Discrepancy** | None |

### Step 9.2: Tapping "Import Lesson Pack"

| Aspect | Details |
|--------|---------|
| **User action** | Taps "Import lesson pack" button (Upload icon) |
| **System response** | `packImportLauncher.launch(arrayOf("application/zip", ...))` — system file picker |
| **What user sees** | System file selection dialog, filtered to ZIP files |
| **Discrepancy** | None |

### Step 9.3: Selecting ZIP file

| Aspect | Details |
|--------|---------|
| **User action** | Selects lesson pack ZIP file |
| **System response** | URI → `vm.importLessonPack(uri)` → `PackImporter.importPackFromUri()` |
| **Processing** | 1) Extract ZIP to temp directory (with path traversal protection) |
| | 2) Parse `manifest.json` (packId, language, lessons, verbDrill, vocabDrill) |
| | 3) Register language via `languageEnsurer()` |
| | 4) Delete old pack on re-import |
| | 5) Copy temp → `packs/{packId}/` |
| | 6) Import lesson CSV files via `AtomicFileWriter` |
| | 7) Copy drill files to `drills/{packId}/verb_drill/` and `vocab_drill/` |
| | 8) Import story/vocab files |
| | 9) Register pack in `packs.yaml` |
| | 10) Reset session state, update UI |
| **What user sees** | Settings panel stays open. No progress indicator |
| **Discrepancy** | **BUG-SET-001**: Import runs synchronously on the main thread. UI may freeze on large packs. **BUG-SET-002**: On error (missing manifest, invalid JSON, no CSV) — no user feedback. Error is logged, but Settings stays open with no message. **BUG-SET-003**: Between deleting the old pack and writing the new one — data loss window on crash. |

### Step 9.4: Import completion

| Aspect | Details |
|--------|---------|
| **System response** | ViewModel resets boss/story/vocab/daily state, updates `selectedLanguageId`, `installedPacks`, `lessons` |
| **What user sees** | On closing settings — HomeScreen with new lesson pack. Language selector updated |
| **Discrepancy** | None (if import succeeds) |

---

## Journey 10: Settings — TTS Model Download

> **Note:** TTS download is NOT triggered from the settings panel, but by tapping the speaker icon on TrainingScreen. Settings contains an ASR toggle, but no TTS button.

### Step 10.1: Tapping speaker icon without model

| Aspect | Details |
|--------|---------|
| **User action** | Taps speaker icon (TtsSpeakerButton) on TrainingScreen when `ttsModelReady == false` |
| **System response** | Checks background download. If already downloading — copies state. Shows `TtsDownloadDialog` |
| **What user sees** | Speaker icon is red (ReportProblem). After tapping — "Download pronunciation model?" dialog |
| **Discrepancy** | None |

### Step 10.2: TTS download dialog

| Aspect | Details |
|--------|---------|
| **Dialog states** | Idle: "Will download ~350 MB" + "Download" button |
| | Downloading: progress bar + % + "Background" button |
| | Extracting: progress bar + % |
| | Done: "Model ready!" (auto-dismiss) |
| | Error: "Download error: {message}" + "OK" |
| **Discrepancy** | None |

### Step 10.3: Download confirmation

| Aspect | Details |
|--------|---------|
| **User action** | Taps "Download" |
| **System response** | `AudioCoordinator.startTtsDownload()`: checks `isNetworkMetered()`. If mobile → MeteredNetworkDialog. If WiFi → `beginTtsDownload()` |
| **What user sees** | Either download starts, or "Mobile network detected. ~346 MB. Download?" dialog |
| **Discrepancy** | None |

### Step 10.4: Download and extraction

| Aspect | Details |
|--------|---------|
| **System response** | `TtsModelManager.download()`: HTTP download with 3 retries, 60s connect / 120s read timeout. Up to 5 redirects (GitHub URLs). Progress via Flow. Then `extractTarBz2()` with path traversal check |
| **Models** | English (Kokoro): ~350 MB download, ~700 MB disk. Italian (VITS Piper): ~65 MB download, ~150 MB disk |
| **What user sees** | Progress bar updates in real-time. Can press "Background" — thin progress bar at top of all screens |
| **Discrepancy** | **BUG-SET-004**: No way to cancel an active download. Only "Background" (hide dialog). **BUG-SET-005**: Background download for all languages (`downloadMultiple`) — sequential, not parallel. If 2 languages, second waits for first. |

### Step 10.5: Download completion

| Aspect | Details |
|--------|---------|
| **System response** | `Done` → `ttsModelReady = true` → `ttsEngine.initialize(languageId)` → Sherpa-ONNX `OfflineTts` created |
| **What user sees** | Dialog: "Model ready!" → auto-dismiss. Speaker icon: red → green over ~2s |
| **Discrepancy** | None |

### Step 10.6: Background download (automatic)

| Aspect | Details |
|--------|---------|
| **Trigger** | App launch / ViewModel creation |
| **System response** | `AudioCoordinator.startBackgroundTtsDownload()`: finds languages without model → `downloadMultiple()` sequentially |
| **What user sees** | Thin 2dp progress bar at top of all screens. Disappears on completion |
| **Discrepancy** | **BUG-SET-005** (same): sequential download, not parallel |

---

## Journey 11: Settings — Backup and Restore

### Creating a backup

#### Step 11.B1: Tapping "Save progress now"

| Aspect | Details |
|--------|---------|
| **User action** | Taps "Save progress now" button (Upload icon) in Backup & Restore section |
| **System response** | `vm.saveProgressNow()` → `BackupManager.createBackup()` → `createBackupToInternal()` |
| **Processing** | Creates `grammarmate/backups/backup_latest/`. Copies via `AtomicFileWriter.copyAtomic()`: mastery.yaml, progress.yaml, profile.yaml, hidden_cards.yaml, bad_sentences.yaml, vocab_progress.yaml, streak_*.yaml, drill_progress_*.yaml, drills/{packId}/verb_drill_progress.yaml, drills/{packId}/word_mastery.yaml, metadata.txt |
| **What user sees** | Nothing. Settings panel stays open without confirmation |
| **Discrepancy** | **BUG-SET-006**: No user feedback on backup success or failure. `createBackup()` returns false on error, but ViewModel shows no toast/snackbar. **BUG-SET-007**: Backup overwrites previous one (always "backup_latest"). No backup history. **BUG-SET-008**: NOT backed up: config.yaml, languages.yaml, packs.yaml, lesson CSVs, TTS/ASR models, stories JSON. On app reinstall, custom packs are lost. |

### Restoring a backup

#### Step 11.R1: Tapping "Restore from backup"

| Aspect | Details |
|--------|---------|
| **User action** | Taps "Restore from backup" button (Download icon) |
| **System response** | `ActivityResultContracts.OpenDocumentTree` — system folder picker |
| **What user sees** | System directory selection dialog |
| **Discrepancy** | None |

#### Step 11.R2: Selecting backup folder

| Aspect | Details |
|--------|---------|
| **User action** | Selects folder with backup data |
| **System response** | `vm.restoreBackup(uri)` → `BackupRestorer.restoreFromUri()`: looks for `backup_latest` inside selected folder, then: 1) Validate each file (YAML parsing) 2) Copy via `AtomicFileWriter` 3) Migrate old streak.yaml → streak_{languageId}.yaml format 4) Write restore_log.txt |
| **What user sees** | Settings panel stays open. No progress indicator |
| **Discrepancy** | **BUG-SET-009**: No feedback on restore completion. User does not know if it succeeded. **BUG-SET-010**: After restore, app does NOT reload automatically. Data takes effect on next store read from disk. User may see stale data until restart. |

#### Step 11.R3: App launch restore path (AppRoot)

| Aspect | Details |
|--------|---------|
| **Trigger** | Detecting incomplete restore on launch |
| **System response** | AppRoot checks `RestoreNotifier.restoreState`. If not DONE → StartupScreen with blocking |
| **What user sees** | White screen with spinner and status text. Blocks all interaction |
| **Discrepancy** | **BUG-SET-011**: No Cancel/Skip/Timeout button on StartupScreen during restore. If RestoreNotifier hangs, user cannot use the app. |

---

## Discrepancy Summary

### Navigation Bugs (Training Flow)

| ID | Severity | Location | Description |
|----|----------|----------|-------------|
| BUG-NAV-001 | HIGH | BossOrchestrator, SessionRunner | Boss records mastery without `bossActive` check. 3 locations in code |
| BUG-NAV-002 | MEDIUM | TrainingScreen | No visual feedback after auto-advance on correct answer |
| BUG-NAV-003 | HIGH | SessionRunner.togglePause() | Play from HINT_SHOWN does not clear `answerText`. Hint visible during ACTIVE |
| BUG-NAV-004 | MEDIUM | TrainingScreen NavigationRow | Next button always active — card skipping |
| BUG-NAV-005 | LOW | SessionRunner.nextCardInternal() | Next on last card — no completion feedback |
| BUG-NAV-006 | MEDIUM | BossOrchestrator.clearBossRewardMessage() | Stale state read in shouldResumeTimer |
| BUG-NAV-007 | LOW | BossOrchestrator | Reward overwrite on repeat (last, not best) |
| BUG-NAV-008 | LOW | GrammarMateApp.kt Daily launch | Silent coroutine failure |
| BUG-NAV-009 | MEDIUM | DailyPracticeCoordinator | No retry after hint — inconsistent with training |
| BUG-NAV-010 | LOW | DailyPracticeScreen | Completion sparkle — navigation race |
| BUG-NAV-011 | HIGH | GrammarMateApp.kt BackHandler | System Back on DAILY_PRACTICE without dialog |
| BUG-NAV-012 | HIGH | VerbDrillScreen LaunchedEffect | Auto-advance race + manual Next — card skipped |
| BUG-NAV-013 | MEDIUM | VerbDrillCardSessionProvider | Play overloaded: "advance" vs "resume" |
| BUG-NAV-014 | LOW | VerbDrillScreen exit | Inconsistent navigation (selection vs HOME) |
| BUG-NAV-015 | MEDIUM | VocabDrill mastery indicator | isLearned: data step>=3, UI step>=9 |
| BUG-NAV-016 | LOW | GrammarMateApp.kt VOCAB_DRILL Back | Redundant refreshVocabMasteryCount |

### Settings and Infrastructure Bugs

| ID | Severity | Location | Description |
|----|----------|----------|-------------|
| BUG-SET-001 | MEDIUM | PackImporter | Pack import synchronous on main thread — UI freezes |
| BUG-SET-002 | MEDIUM | PackImporter / ViewModel | No user feedback on import error |
| BUG-SET-003 | LOW | PackImporter | Data loss window between old pack deletion and new pack write |
| BUG-SET-004 | LOW | TtsModelManager | No TTS download cancellation (only "Background") |
| BUG-SET-005 | LOW | AudioCoordinator | Background language download sequential, not parallel |
| BUG-SET-006 | MEDIUM | BackupManager / ViewModel | No feedback on backup success/failure |
| BUG-SET-007 | LOW | BackupManager | Backup overwrites previous — no history |
| BUG-SET-008 | MEDIUM | BackupManager | Custom lesson packs NOT included in backup — lost on reinstall |
| BUG-SET-009 | MEDIUM | BackupRestorer / ViewModel | No feedback on restore completion |
| BUG-SET-010 | MEDIUM | ViewModel | After restore data not reloaded — stale state |
| BUG-SET-011 | LOW | AppRoot StartupScreen | No Cancel/Skip/Timeout on restore hang |

---

## Structural Issues (Cross-cutting)

| ID | Severity | Description |
|----|----------|-------------|
| STRUCT-001 | HIGH | Three different state mechanisms for Pattern A: TrainingScreen → `SessionState` enum, VerbDrill → `CardSessionStateMachine` (`isPaused` + `hintAnswer`), DailyPractice → `DailyPracticeSessionProvider`. No unified model. Each has its own bugs (BUG-NAV-003, 012, 013) |
| STRUCT-002 | MEDIUM | `AFTER_CHECK` SessionState defined but unused — dead code |
| STRUCT-003 | MEDIUM | No single source of truth for button enabled-state. Check condition computed inline in composable |
| STRUCT-004 | LOW | `inputText` clearing: VOICE clears on error, KEYBOARD keeps it. Inconsistent UX |
| STRUCT-005 | MEDIUM | No unified feedback mechanism (toast/snackbar) for Settings operations. Each operation (import, backup, restore) is silent on success and error (BUG-SET-002, 006, 009) |
| STRUCT-006 | MEDIUM | BUG-SET-008 is not a code bug but an architectural gap: backup does not include custom packs. On app reinstall, user loses imported content |
