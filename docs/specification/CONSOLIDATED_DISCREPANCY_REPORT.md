# GrammarMate — Consolidated Discrepancy Report

Generated: 2026-05-12
Source: 15 scenario verification agents + 3 architecture audit agents
Total findings: 49 discrepancies across 38 files

---

## CRITICAL (Must Fix — data loss or wrong behavior)

### C1. Boss battles inflate mastery
- **Source**: scenario-03, scenario-09, arch-audit-spec-vs-code
- **Files**: `TrainingViewModel.kt:632,886,2422`
- **Problem**: `recordCardShowForMastery()` called unconditionally during boss battles, inflating uniqueCardShows and advancing SRS intervals. Spec 18.8.4 says boss battles are separate from mastery/flower system.
- **Fix**: Add `if (_uiState.value.bossActive) return` at top of `recordCardShowForMastery()`
- **Impact**: Flowers bloom faster than intended, mastery metrics are wrong

### C2. vocabMasteredCount always shows 0 on HomeScreen
- **Source**: scenario-10, arch-audit-dependencies
- **Files**: `TrainingViewModel.kt:103,256,453,3157-3159`
- **Problem**: `wordMasteryStore` created without packId → reads from `grammarmate/word_mastery.yaml` (global). All writes go to pack-scoped `drills/{packId}/word_mastery.yaml`. Global file never written.
- **Fix**: Pass active packId when constructing WordMasteryStore in TrainingViewModel
- **Impact**: Users see "0 mastered" on home screen despite active practice

### C3. Stale verb drill progress after daily practice
- **Source**: scenario-10
- **Files**: `VerbDrillViewModel.kt`, `TrainingViewModel.kt`
- **Problem**: VerbDrillViewModel caches `progressMap` in memory. TrainingVM writes verb progress via fresh instances. `reloadForPack()` skips reload when same packId + cards loaded.
- **Fix**: Force reload progress map on session start, or share single VerbDrillStore instance
- **Impact**: Verb drill shows outdated progress after daily practice verb block

### C4. Non-atomic file writes during pack import
- **Source**: scenario-13
- **Files**: `LessonStore.kt` — `importLessonFromFile()`, `importPackDrills()`
- **Problem**: Uses raw `FileOutputStream` and `copyTo()` instead of `AtomicFileWriter`. Violates CLAUDE.md Level B rule. Crash during import = truncated files.
- **Fix**: Replace with AtomicFileWriter for all file writes during import
- **Impact**: Corrupt lesson data if app crashes during import

### C5. Backup missing 10 of ~14 data stores
- **Source**: scenario-14
- **Files**: `BackupManager.kt`
- **Problem**: Only backs up mastery.yaml, progress.yaml, profile.yaml, streak_*.yaml. Missing: verb_drill_progress.yaml, word_mastery.yaml, drill_progress_*.yaml, vocab_progress.yaml, hidden_cards.yaml, bad_sentences.yaml.
- **Fix**: Add all missing stores to backup file list
- **Impact**: Users lose all drill/vocab progress on reinstall+restore

### C6. Completion screen never visible in daily practice
- **Source**: scenario-06
- **Files**: `DailyPracticeScreen.kt`, `TrainingViewModel.kt`
- **Problem**: `onAdvanceBlock()` calls `endSession()` then `onComplete()` immediately navigates to HOME. Completion screen removed from composition before rendering.
- **Fix**: Delay HOME navigation until after completion screen animation
- **Impact**: User never sees daily practice results/celebration

### C7. WelcomeDialog not shown on first launch
- **Source**: scenario-15
- **Files**: `GrammarMateApp.kt:302-305`
- **Problem**: Trigger condition is `screen != AppScreen.HOME`, but app starts on HOME. New users see "GrammarMateUser" until they navigate away.
- **Fix**: Remove the `!= HOME` condition or add a first-launch LaunchedEffect
- **Impact**: Poor first-launch experience

---

## MAJOR (Should Fix — incorrect behavior)

### M1. isLearned threshold: code=3, spec=9
- **Source**: scenario-04, scenario-08, arch-audit-spec-vs-code
- **Files**: `TrainingViewModel.kt:1709`, `VocabDrillViewModel.kt:258`, `VocabWord.kt:28`
- **Problem**: Code uses LEARNED_THRESHOLD=3, but VocabWord.kt comment says "step 9" and specs 01/02/18 document 9. Card back UI shows "Learned" at step 9.
- **Fix**: Align code comment and UI threshold to match actual threshold (3). Update specs.
- **Impact**: Confusion between displayed "Learned" label and actual isLearned state

### M2. Daily practice currentLessonIndex never advances
- **Source**: scenario-06
- **Files**: `DailySessionHelper.kt` / `DailySessionComposer.kt`
- **Problem**: `advanceCursor()` only increments `sentenceOffset`, never `currentLessonIndex`. Block 1 becomes permanently empty when a lesson's cards are exhausted.
- **Fix**: Advance `currentLessonIndex` when sentence cards exhausted
- **Impact**: Daily practice block 1 eventually becomes empty

### M3. Boss reward thresholds differ from spec
- **Source**: scenario-09
- **Files**: `TrainingViewModel.kt`
- **Problem**: Code uses BRONZE > 50%, SILVER > 75%. Spec 18 says BRONZE >= 30%, SILVER >= 60-70%.
- **Fix**: Align code thresholds with spec, or update spec to match code
- **Impact**: Users get lower rewards than spec describes

### M4. SAF folder picker forced on fresh install (Android 10+)
- **Source**: scenario-15
- **Files**: `MainActivity.kt:71-80`
- **Problem**: Fresh install on Android 10+ shows "Select backup folder" SAF picker even with no backup data. No "start fresh" option.
- **Fix**: Check if backup data exists before launching SAF picker. Add skip option.
- **Impact**: Confusing first-launch experience on modern Android

### M5. Fragmented verb progress keys
- **Source**: scenario-10
- **Files**: `TrainingViewModel.kt`, `VerbDrillViewModel.kt`
- **Problem**: Daily practice uses `"|{tense}"` (empty group), standalone uses `"group|tense"`. Progress tracked in separate keys.
- **Fix**: Normalize progress key format to always include group
- **Impact**: Same verb+tense combo has separate progress in daily vs standalone

### M6. Daily Practice BackHandler bypasses confirmation
- **Source**: scenario-11
- **Files**: `GrammarMateApp.kt:209-211`
- **Problem**: System back button exits to HOME immediately. In-screen back arrow shows confirmation dialog.
- **Fix**: Add confirmation dialog to BackHandler for DAILY_PRACTICE
- **Impact**: Accidental progress loss with system back gesture

---

## MODERATE (Should Fix — inconsistency or UX issue)

### R1. Normalizer doesn't strip diacritical marks
- **Source**: scenario-02, arch-audit-spec-vs-code
- **Files**: `Normalization.kt`, spec 12-training-card-session.md
- **Problem**: Spec 12 claims diacritics are stripped. Code doesn't do it. "perche" ≠ "perché".
- **Fix**: Either add NFD decomposition to Normalizer, or fix spec 12. Recommended: add to Normalizer for Italian usability.
- **Impact**: Italian users without accent keyboards get wrong answers

### R2. VocabDrill uses different normalization
- **Source**: scenario-02
- **Files**: `VocabDrillViewModel.kt` — `normalizeForMatch()`
- **Problem**: VocabDrill has its own lighter normalization instead of using shared `Normalizer.normalize()`.
- **Fix**: Use shared Normalizer or merge normalization logic
- **Impact**: Inconsistent answer matching between drill types

### R3. Vocab card back forms hardcode adjective keys
- **Source**: scenario-08
- **Files**: `VocabDrillScreen.kt:797-804`
- **Problem**: Hardcodes msg/fsg/mpl/fpl. Numbers and pronouns show dashes.
- **Fix**: Use word-type-aware form key lookup
- **Impact**: Missing form data for numbers and pronouns

### R4. Vocab Drill BackHandler doesn't refresh mastery count
- **Source**: scenario-11
- **Files**: `GrammarMateApp.kt:224-226`
- **Problem**: System back doesn't call `refreshVocabMasteryCount()`. Home screen counter stale.
- **Fix**: Add `vm.refreshVocabMasteryCount()` to VOCAB_DRILL BackHandler
- **Impact**: Stale mastery count on home screen after vocab drill

### R5. Verb drill "Hide this card" button is a visible no-op
- **Source**: scenario-07
- **Files**: `VerbDrillCardSessionProvider.kt:372-373`
- **Problem**: Button shown but `hideCurrentCard()` is empty ("Not yet supported").
- **Fix**: Hide or disable the button for verb drill
- **Impact**: User frustration when feature doesn't work

### R6. AtomicFileWriter has unnecessary delete() before renameTo()
- **Source**: scenario-12
- **Files**: `AtomicFileWriter.kt:29-36`
- **Problem**: `file.delete()` before `tempFile.renameTo(file)` creates window with no valid file. renameTo() atomically replaces on Linux/Android.
- **Fix**: Remove the `file.delete()` call
- **Impact**: Tiny vulnerability window during writes

### R7. Temp directory not cleaned on import JSON parse failure
- **Source**: scenario-13
- **Files**: `LessonStore.kt`
- **Problem**: Only "file missing" case cleans temp dir. Malformed JSON leaves orphaned tmp_* directories.
- **Fix**: Add try/finally around manifest parsing to always clean temp dir
- **Impact**: Orphaned temp directories accumulate over time

### R8. dailyPracticeAnsweredCounts not reset on Repeat
- **Source**: scenario-06
- **Files**: `DailyPracticeSessionProvider.kt` / `DailySessionHelper.kt`
- **Problem**: Per-block answered counters accumulate across repeat sessions.
- **Fix**: Reset answeredCounts in repeat path
- **Impact**: Incorrect progress display on repeated daily sessions

### R9. Streak reset inconsistency
- **Source**: scenario-04
- **Files**: `TrainingViewModel.kt`, `StreakStore.kt`
- **Problem**: `recordSubLessonCompletion()` resets to 1 on gap, `getCurrentStreak()` resets to 0.
- **Fix**: Align reset behavior to consistent value
- **Impact**: Streak display may flicker between 0 and 1

---

## MINOR (Nice to Fix)

| # | Issue | Source | File |
|---|-------|--------|------|
| m1 | FlowerCalculator.calculate() unused totalCardsInLesson param | scenario-03 | FlowerCalculator.kt |
| m2 | Reserve pool cards increment uniqueCardShows | scenario-03 | MasteryStore.kt |
| m3 | Verb drill TTS speaks Italian answer, not Russian prompt | scenario-07 | VerbDrillCardSessionProvider.kt:349 |
| m4 | Speed tracking not reset on reloadForPack | scenario-07 | VerbDrillViewModel.kt |
| m5 | currentSpeedWpm measures answers/min not words/min | scenario-07 | VerbDrillViewModel.kt |
| m6 | lastSessionHash is dead code in DailyCursorState | scenario-06 | Models.kt |
| m7 | Provider _inputMode defaults to VOICE regardless | scenario-06 | DailyPracticeSessionProvider.kt |
| m8 | dailyPracticeAnsweredCounts accumulation | scenario-06 | DailySessionHelper.kt |
| m9 | Empty input counts as wrong attempt in drill adapters | scenario-02 | VerbDrillCardSessionProvider.kt |
| m10 | Distractor filtering uses exact match in adapters | scenario-05 | VerbDrillCardSessionProvider.kt:316 |
| m11 | restoreFromBackup() returns true with zero files | scenario-14 | BackupManager.kt |
| m12 | getAvailableBackups() never wired to UI | scenario-14 | BackupManager.kt |
| m13 | Pack validator rejects valid tense column | scenario-13 | pack_validator.py |
| m14 | currentScreen persisted but never restored | scenario-12 | TrainingViewModel.kt |
| m15 | VerbDrillStore created 4-5x per daily session | scenario-10 | TrainingViewModel.kt |

---

## Architecture Issues (from audit)

| # | Issue | Severity | Fix |
|---|-------|----------|-----|
| A1 | TrainingViewModel 3615 lines (3x limit) | Critical | Decompose into modules per arch-module-decomposition.md |
| A2 | 3 independent VerbDrillStore instances | Critical | Single shared instance |
| A3 | 3 independent WordMasteryStore instances with different paths | Critical | Single shared instance with correct path |
| A4 | 3 TtsEngine instances across ViewModels | Major | Single shared instance |
| A5 | Retry/hint state machine implemented 3x independently | Major | Extract to shared CardSessionContract |
| A6 | All stores are concrete classes, no interfaces | Moderate | Add interfaces for testability |
| A7 | TrainingStateAccess only used by DailySessionHelper | Moderate | Extend to all helpers |

---

## Resolved Discrepancies

### RESOLVED: Bad card reporting not unified across modes
- **Original issue**: Bad card reporting was only documented for regular training mode. Verb drill, vocab drill, and daily practice had no spec coverage for their reporting features.
- **Resolution date**: 2026-05-12
- **Resolution**: All 7 specification documents have been updated to reflect the unified bad card reporting feature:
  - `02-data-stores.md`: Updated `BadSentenceStore` section with `mode` field documentation and all mode values (`"training"`, `"verb_drill"`, `"vocab_drill"`, `"daily_translate"`, `"daily_vocab"`, `"daily_verb"`). Added `exportUnified()` method.
  - `08-training-viewmodel.md`: Added section 8.12 "Bad Card Reporting" covering training mode, daily practice mode, and export format.
  - `09-daily-practice.md`: Added section 9.10 "Bad Card Reporting in Daily Practice" covering all 3 blocks with mode fields and UI locations.
  - `10-verb-drill.md`: Updated section 10.5.9 with full verb drill flag/unflag/export details and mode field `"verb_drill"`. Updated shared vs separate state table.
  - `11-vocab-drill.md`: Added section 11.10 "Bad Card Reporting" with vocab drill flag/unflag/export details and mode field `"vocab_drill"`.
  - `19-screen-catalog.md`: Added report sheet entries (19.28, 19.28a, 19.28b, 19.28c, 19.28d) for each mode.

---

## Fix Priority Order

**Phase 1 — Data correctness (critical fixes, 1-2 line changes each):**
1. C1: Boss mastery guard
2. C2: WordMasteryStore packId
3. C3: VerbDrillStore stale cache
4. C4: Atomic writes in import

**Phase 2 — UX fixes:**
5. C6: Daily completion screen
6. C7: Welcome dialog trigger
7. M4: SAF picker on fresh install
8. M6: Back handler confirmation

**Phase 3 — Data completeness:**
9. C5: Backup all stores
10. M5: Verb progress key normalization
11. R7: Import temp cleanup
12. R6: AtomicFileWriter delete removal

**Phase 4 — Consistency:**
13. M1: isLearned threshold alignment
14. M2: currentLessonIndex advancement
15. M3: Boss reward thresholds
16. R1-R5: Normalization, forms, mastery refresh, hide button

**Phase 5 — Architecture (modular refactoring):**
17. A1-A7: Module decomposition per arch-module-decomposition.md
