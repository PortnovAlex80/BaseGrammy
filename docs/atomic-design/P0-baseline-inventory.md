# P0 Baseline Inventory — GrammarMate Atomic Design migration

- Date: 2026-09-27
- Baseline revision: `3569a9fe946574d4b67ef1221eb6bf358d5c9595`
- Branch: `feature/atomic-design` (working tree clean at inspection time)
- Scope: **P0 only** — source/verification baseline. No production code changed, no refactoring, no new tests. Evidence below is grep/read-verified on this exact tree; where the plan (`docs/plans/2026-09-26-atomic-design-migration.md`, "dated snapshot") and reality disagree, the correction is recorded here.
- Path convention: paths below are relative to `app/src/main/java/com/alexpo/grammermate/` unless prefixed otherwise. All line numbers refer to revision `3569a9fe9`.

---

## 1. Route map (NavHost in `ui/GrammarMateApp.kt`, lines 387–1029)

Composition root: `MainActivity` (`MainActivity.kt:116` `setContent { AppRoot() }`) → `ui/AppRoot.kt:26 AppRoot` → `GrammarMateTheme` (`AppRoot.kt:35`) → `GrammarMateApp(vm)` (`AppRoot.kt:38`; defined `ui/GrammarMateApp.kt:127`). Route constants: `ui/navigation/Routes.kt:84–99` (`internal object Routes`, 14 constants).

| Route constant (`Routes.kt`) | Screen composable rendered | Source file:line | Notes |
|---|---|---|---|
| `HOME` = `"home"` (start destination) | Conditional: `GrammarStoryRoadmapScreen` (when `activePackHasChapters`) or `HomeScreen` (classic v1 packs) | `ui/screens/GrammarStoryRoadmapScreen.kt:65` / `ui/screens/HomeScreen.kt:110`; dispatch at `GrammarMateApp.kt:403/480` | Two home variants under one route. Roadmap has no separate route (comment `ui/navigation/NavCommon.kt:108–112`). |
| `LESSON` = `"lesson"` | `LessonRoadmapScreen` | `ui/screens/LessonRoadmapScreen.kt` (called `GrammarMateApp.kt:523`) | Inner `BackHandler` `GrammarMateApp.kt:582`. |
| `ELITE` = `"elite"` | none — redirect | `GrammarMateApp.kt:600–602` | Backward compat: `LaunchedEffect` navigates to HOME, pops ELITE. |
| `VOCAB` = `"vocab"` | none — redirect | `GrammarMateApp.kt:605–607` | Backward compat: navigates to HOME, pops VOCAB. |
| `DAILY_PRACTICE` = `"daily_practice"` | `DailyPracticeScreenContent` → `DailyPracticeScreen` | `ui/screens/DailyPracticeScreenContent.kt:89` → `ui/DailyPracticeScreen.kt:76` (called `GrammarMateApp.kt:611`) | |
| `STORY` = `"story"` | `StoryQuizScreen` | `ui/screens/StoryQuizScreen.kt` (called `GrammarMateApp.kt:615`) | |
| `TRAINING` = `"training"` | **`TrainingScreenContent` → `TrainingScreen`** | `ui/screens/TrainingScreenContent.kt:89` → `ui/screens/TrainingScreen.kt:102` (called `GrammarMateApp.kt:662`) | **Verified active training path.** Also hosts 3 `BackHandler`s (`GrammarMateApp.kt:779, 788, 799`) for drill-return / daily-cancel / lesson-exit-dialog. |
| `LADDER` = `"ladder"` | `LadderScreen` | `ui/screens/LadderScreen.kt` (called `GrammarMateApp.kt:638`) | Pushed from Settings; resumes paused session if opener was TRAINING (`GrammarMateApp.kt:642–653`). |
| `VERB_DRILL` = `"verb_drill"` | `VerbDrillScreen` | `ui/VerbDrillScreen.kt:65` (called `GrammarMateApp.kt:830`) | Pack-reload `LaunchedEffect` `GrammarMateApp.kt:811–817`; `BackHandler` `:825`. |
| `VOCAB_DRILL` = `"vocab_drill"` | `VocabDrillScreen` | `ui/VocabDrillScreen.kt:426` card screen inside (`VocabDrillScreen` entry `:65`; called `GrammarMateApp.kt:865`) | Own `VocabDrillViewModel` per route entry (`GrammarMateApp.kt:842`). |
| `AUX_DRILL` = `"aux_drill"` | `AuxDrillScreen` | `ui/AuxDrillScreen.kt` (called `GrammarMateApp.kt:894`) | Hands deck to shared TRAINING session via `vm.startVerbDrillSession` (`GrammarMateApp.kt:897–907`). |
| `STORY_READER` = `"story_reader"` | `StoryReaderScreen` | `ui/screens/StoryReaderScreen.kt` (called `GrammarMateApp.kt:917`) | |
| `CHAPTER_LESSONS` = `"chapter_lessons"` | `ChapterLessonsScreen` (fallback loader when no chapter selected) | `ui/screens/ChapterLessonsScreen.kt` (called `GrammarMateApp.kt:983`) | |
| `BACKGROUND_VOCAB` = `"background_vocab"` | `BackgroundVocabScreen` | `ui/screens/BackgroundVocabScreen.kt` (called `GrammarMateApp.kt:1016`) | |

Overlays outside the NavHost (same `Box`, `GrammarMateApp.kt:300–1061`): `SettingsSheet` (`:312`), `NavDialogs` (`:1049`, all dialogs in `ui/dialogs/AppDialogs.kt`), `NavBackHandlers` (`:1040`), persistent TTS banner/progress bar (`:290–298`).

**Legacy enum**: `data/AppScreen.kt:8` (12 values incl. legacy `ELITE`, `VOCAB`). Mapped from routes by `routeToScreen` (`ui/navigation/NavCommon.kt:88–104`); non-route screens fold: `STORY_READER → AppScreen.STORY`, `BACKGROUND_VOCAB → AppScreen.HOME`. Used only for `vm.settings.onScreenChanged(...)` tracking (`GrammarMateApp.kt:175–179`).

**Verified active training path**: `GrammarMateApp` (TRAINING route, `:657–805`) → `TrainingScreenContent` (`ui/screens/TrainingScreenContent.kt:89`) → `TrainingScreen` (`ui/screens/TrainingScreen.kt:102`). `TrainingCardSession` is NOT on this path (see §3, dead code).

---

## 2. Component inventory (P0 baseline — all rows `characterized` or `unassessed`; nothing extracted/adopted yet)

Callers are **production callers** (grep over `app/src/main/java`, excluding the definition itself). Test sources reference none of these components directly (grep over `app/src/test` returned no component names).

| Symbol | Source (file:line) | Production callers (count + files) | Atomic level (proposal) | Feature owner | Authoritative state owner | Effects owned | Semantics / test tags | Proposed destination | Validation notes | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| `NavIconButton` | `ui/components/SharedComponents.kt:59` | 4 — all in `ui/components/UnifiedNavigationRow.kt:89,101,116,123` | Atom | Shared (navigation row) | none (stateless) | none | 44dp target, accent bottom bar; carries tags via callers (`prev/pause/exit/next_button`) | `designsystem/atoms` | Used only by UnifiedNavigationRow today; doc comment says "Used by GrammarMateApp and TrainingCardSession" — stale | characterized |
| `TtsSpeakerButton` | `ui/components/SharedComponents.kt:97` | 1 live — `ui/screens/TrainingScreen.kt:495` (CardPrompt); 2 in dead file `ui/TrainingCardSession.kt:343,442` | Atom | Shared (training) | TTS state from `TrainingUiState.audio.ttsState` | none (pure render of 4 `TtsState`s) | stop/spinner/error-tooltip/idle icons; a11y strings `content_desc_*`; OOM error icon heuristic (`:118`) | `designsystem/atoms` | Preserve 4-state parity incl. tooltip-on-error | characterized |
| `InitialsAvatar` | `ui/components/InitialsAvatar.kt:19` | 2 — `ui/components/ProfileStatsPopup.kt:51`, `ui/screens/HomeScreen.kt:206` (fully-qualified) | Atom | Shared (profile/home) | none | clickable (profile open) | 40dp circle; duplicate initials logic: dead `HomeScreen.kt:99 getUserInitials` vs private `buildInitials` (`InitialsAvatar.kt:43`) — consolidate | `designsystem/atoms` | Two call sites with identical meaning — good P1/P2 pilot candidate | characterized |
| `AppBackground` (+ `Modifier.glassSurface`/`cardOutline`/`emeraldGlow`) | `ui/components/Glass.kt:50` (modifiers `:138,:178,:187`) | 1 — `ui/Theme.kt:309` (inside `GrammarMateTheme`); modifiers: HomeScreen, LessonTile, drill tiles | Foundation-boundary case (drawing + inset wrapper) | App shell | none | Applies `WindowInsets.safeDrawing` padding (`Glass.kt:66`) — must stay with app facade, not in foundations | Gradient + radial glow colors hardcoded here (`:79–121`) | Foundation tokens → `designsystem/foundation`; inset wrapper stays in theme facade (plan §6 rule 1) | Do not let foundation import this file; ADR-007 mitigation 6 | characterized |
| `CardPrompt` | `ui/screens/TrainingScreen.kt:457` | 1 — `TrainingScreen.kt:368` (non-drill modes only) | Organism (P2 pilot target) | Training | `TrainingUiState.cardSession` (hint calc inputs) | TTS via `onSpeak` callback (effect stays at boundary); audit log inside (`:499`) | tag `card_prompt_text` (`:468`); scaled `20f * ruTextScale`; `contextRu` visible independently of hint ladder (`:485`) | `features/training/components` | P2 pilot per plan/ADR-007; keep hint calc + audit at integration boundary | characterized |
| `HintAnswerCard` | `ui/components/HintAnswerCard.kt:26` | 1 live — `ui/components/UnifiedInputControlsBar.kt:180`; 1 in dead `ui/TrainingCardSession.kt:450` | Molecule | Shared (input bar) | none | none | red `errorContainer` card, `hint_answer_prefix` string | `designsystem/molecules` (or fold into input-bar organism) | Doc comment claims 4 callers — stale (see §3.9) | characterized |
| `ClickableHintAnswerCard` | `ui/components/ClickableHintAnswerCard.kt:22` | 1 — `ui/components/UnifiedInputControlsBar.kt:170` | Molecule | Shared (input bar) | none (reads `WordInfoCache` map passed in) | none (click callback out; popup owned by bar) | rank-colored words (raw hex `:65–67`); `WORD_TAG` annotations | `designsystem/molecules` | Depends on `WordHint` model — keep generic version data-driven or feature-owned | characterized |
| `SessionProgressIndicator` | `ui/components/SessionProgressIndicator.kt:45` | 2 live — `ui/screens/TrainingScreen.kt:352,360`; 1 in dead `ui/TrainingCardSession.kt:211` | Molecule | Shared (training) | none | none | uses 8 token getters; speed arc thresholds 20/40 wpm | `designsystem/molecules` | P3a target (plan §5); pure display | characterized |
| `DifficultyRatingRow` | `ui/components/DifficultyRatingRow.kt:24` | **0 — dead** | (would-be Molecule) | none | local `hasSelected` | `LaunchedEffect(Unit)` auto-rates GOOD after 3s (`:29–35`) — dangerous if revived | raw hex chip colors (`:17–20`), hardcoded "How was it?" | **retire-candidate** | Superseded by screen-local SRS rows (§3.8) | characterized (dead) |
| `UnifiedInputControlsBar` | `ui/components/UnifiedInputControlsBar.kt:90` | 1 live — `ui/screens/TrainingScreen.kt:707` (via `AnswerBox` contract adapter `:649–705`); 1 in dead `ui/TrainingCardSession.kt:402` | Organism (display) + effect boundary | Shared (training/daily/verb per DP-01) | `CardSessionContract` (interface alive; sole production implementer = adapter `TrainingScreen.kt:650`) | speechLauncher `:136`; `launchVoice` + BT setup `:148–163`; word-info cache load `:114–125`; auto-submit on exact match `:210–227`; WordInfoPopup state `:128–130,408–421` | tags `input_field`, `check_button`, `show_answer_button`; word-bank tags via `WordBankSection` | P3b: pure display beneath stable adapter; effects stay in integration layer (ledger §5) | Plan §2 flags exactly this file; ledger required before movement | characterized |
| `UnifiedNavigationRow` | `ui/components/UnifiedNavigationRow.kt:47` | 1 live — `ui/screens/TrainingScreen.kt:397` (stateModel = anonymous `CardSessionStateModel` built at `:398–416`); 1 in dead `ui/TrainingCardSession.kt:244` | Molecule/Organism | Shared (session navigation) | `CardSessionStateModel` (implemented by `SessionRunner` `feature/training/SessionRunner.kt:73`) | owns exit-confirm dialog local state (`:58–82`) | tags `prev_button`,`pause_button`,`exit_button`,`next_button`,`exit_confirm_button` | P3b target; keep dialog ownership + tag semantics | characterized |
| `VoiceAutoLauncher` | `ui/components/VoiceAutoLauncher.kt:19` | **0 — dead** (doc claims "Shared between VocabDrillScreen and TrainingScreen" — false) | (would-be Molecule) | none | none | 500ms-delay auto-launch logic — duplicated inline at `ui/VocabDrillScreen.kt:470–478`; training uses a different 200ms variant (`TrainingScreen.kt:567–601`) | **retire-candidate** (or revive as the single implementation in P3b — decide explicitly) | Ledger §5 rows 1–3 are the three live/dead variants | characterized (dead) |
| `PackTileCard` / `PackTileGrid` | `ui/screens/HomeScreen.kt:561 / :639` | 1 each — internal (`HomeScreen.kt:648`, `:275`) | Molecule | Home | `PackTileUi` from `vm.packTiles` flow (`GrammarMateApp.kt:398`) | none | `cardOutline`, progress indicator | `features/home/components` (P5) | VM-sliced state already in place | characterized |
| Drill entry tiles `VerbDrillEntryTile` / `AuxDrillEntryTile` / `VocabDrillEntryTile` / `DailyPracticeEntryTile` | `ui/screens/HomeScreen.kt:657 / :706 / :737 / :794` | 1 each — `HomeScreen.kt:337,347,353,367` | Molecule | Home | none (callbacks) | none | tags `verb_drill_entry_tile`, `aux_drill_entry_tile`, `vocab_drill_entry_tile`; DailyPractice tile **hard-disabled** (`enabled=false` `:372`, toast "Временно не доступно") | `features/home/components` (P5) | Roadmap variant uses plain `OutlinedButton`s instead (`GrammarStoryRoadmapScreen.kt:168–212`) — two intentional-looking variants, do not merge blindly | characterized |
| Lesson tiles `LessonTile` + `buildLessonTiles` | `ui/screens/HomeScreen.kt:852 / :1038` | 1 — `HomeScreen.kt:309` (inside `LazyVerticalGrid` `:300`) | Molecule + pure mapping fn | Home | `FlowerVisual` map from `state.flowerDisplay.lessonFlowers` | none | `LessonTileState` enum (incl. `LOCKED` — classic HomeScreen only; chapter roadmap uses `ChapterStatus` ACTIVE/DONE per ADR); emerald glow on active/bloomed | `features/home/components` (P5) | `buildLessonTiles` contains debug `Log.d` per lesson (`:1059,1064`) — unrelated defect, record only | characterized |
| Verb `SessionCard` (start/repeat/continue/reset) | `ui/VerbDrillScreen.kt:487` | 1 — `VerbDrillScreen.kt:297` (in `VerbDrillSelectionScreen`) | Organism | Verb drill | `VerbDrillViewModel` (saved batch) | actions at `:116–136` call `onRepeatSession/onResumeSession/onStartFresh` + audit | tags `session_card`,`repeat_button`,`continue_button`,`reset_button`; `verb_start_button` at `:319`; `StartFreshResumeDialog` `:365` | `features/verbdrill/components` (P4) | Click-path tests in CLAUDE.md govern; never reconnect via TrainingCardSession | characterized |
| Completion screens `SessionCompletionContent` / `VerbDrillCompletionContent` | `ui/screens/TrainingScreen.kt:907 / :856` | 1 each — `TrainingScreen.kt:206 / :172` | Organism | Training (all modes) / verb drill | counts from `state.cardSession` | none (callbacks out) | mode-dependent title (`:914–919`); guarded by `hasAnyAnswer` (`:159,203`) | `features/training/components` (P3a/P4) | Pomodoro completion takes priority (`:183–197`) | characterized |
| Report/info sheets `SharedReportSheet`, `QrShareDialog` | `ui/components/SharedReportSheet.kt` (def), `ui/components/QrShareDialog.kt` | SharedReportSheet: 3 live — `TrainingScreen.kt:604`, `ui/VocabDrillScreen.kt:670`, `ui/DailyPracticeScreen.kt:378`; 1 dead `TrainingCardSession.kt:373`. QrShareDialog: same 3 live + 1 dead (`:394`) | Organism | Shared (reporting) | bad-sentence store via callbacks | none of its own | flag/hide/export/copy/share-QR actions | `patterns/` candidate (3 live callers share meaning) — verify evolution compatibility first | Export-result message dialog is caller-local (`TrainingScreen.kt:635–646`) | characterized |
| Download dialogs `TtsDownloadDialog`, `MeteredNetworkDialog`, `AsrMeteredNetworkDialog`, `TtsDownloadStatusBanner` | `ui/components/SharedComponents.kt:154,:242,:263`; `ui/components/TtsDownloadStatusBanner.kt:101` | First three: 1 each — `ui/dialogs/AppDialogs.kt:183,:197,:207`. Banner: 1 — `GrammarMateApp.kt:297` | Molecule/organism | Audio/TTS | `DownloadState` from `state.audio` | TTS-done auto-speak effect lives in NavDialogs (`AppDialogs.kt:174–179`) | banner is `internal`; `calcBgDownloadProgress` helper (`GrammarMateApp.kt:292`) | Keep with audio feature surface (P5) | | characterized |
| `ProfileStatsPopup`, `PomodoroSelectorSheet`, `PomodoroSummaryScreen`, `PomodoroTimerBanner`, `WordInfoPopup`, `WordBankSection`, `AsrStatusIndicator`, `GrammarInfoBottomSheet` + verb/tense sheets, `SettingsSheet`, `WelcomeDialog` | see files: `ProfileStatsPopup.kt:23`, `PomodoroSelectorSheet.kt`, `PomodoroSummaryScreen.kt`, `PomodoroTimerBanner.kt`, `WordInfoPopup.kt:35`, `DailyPracticeComponents.kt:30/45`, `SharedComponents.kt:286`, `VerbDrillSheets.kt:113/:234`, `screens/SettingsScreen.kt:158`, `AppDialogs.kt` | each exactly 1: `AppDialogs.kt:160`, `HomeScreen.kt:476`, `TrainingScreen.kt:188`, `TrainingScreen.kt:256`, `UnifiedInputControlsBar.kt:412`, `UnifiedInputControlsBar.kt:285`, `TrainingScreen.kt:725`, `TrainingScreen.kt:449/431/442`, `GrammarMateApp.kt:312`, `AppDialogs.kt:143` | mixed (mostly Molecule/Organism) | per feature | see ledger §5 | see ledger §5 | word-bank tags `word_bank_container/chip_*/undo` (`DailyPracticeComponents.kt:60–90`) | feature-owned destinations per plan §3 | | characterized |
| `TrainingCardSession` (+ `TrainingCardSessionScope`, 6 `Default*` privates) | `ui/TrainingCardSession.kt:106` | **0 — dead.** No production or test caller (grep-verified). Plan §2 confirmed; still true on this tree. | (would-be Template) | none | local `localInputText` `:126` (would duplicate authoritative input state) | wires `UnifiedInputControlsBar`, `UnifiedNavigationRow`, `SessionProgressIndicator`, `HintAnswerCard`, `TtsSpeakerButton`, `SharedReportSheet`, `QrShareDialog` | **retire-candidate.** Do NOT connect to production (ADR-007). Its 8 sub-component call sites are the only thing keeping some "dead" counts above 0 | Removal is a P6 consolidation item; requires owning integrator decision | characterized (dead) |

### 2.1 Dead / retire-candidate summary (P0 verdict)

| Item | Evidence | Note |
|---|---|---|
| `TrainingCardSession` + scope + 6 default slots | 0 call sites (main + test) | CardSessionContract **interface itself is alive** — sole production implementer is the adapter in `TrainingScreen.kt:649–705`; consumed by `UnifiedInputControlsBar`, `WordBankSection` |
| `DifficultyRatingRow` | 0 call sites | hardcodes raw hex (`:17–20`); 3s auto-GOOD effect; superseded by DailyPractice/VocabDrill SRS rows |
| `VoiceAutoLauncher` | 0 call sites | logic duplicated inline in VocabDrillScreen with identical 500ms pattern |
| `HomeScreen.getUserInitials` | 0 call sites | duplicates `InitialsAvatar.buildInitials` |
| `DailyWordBankSection` | 0 call sites | backward-compat alias (`DailyPracticeComponents.kt:99`) |
| `VerbDrillSheets` ViewModel overloads (`VerbReferenceBottomSheet:58`, `TenseInfoBottomSheet:85`) | 0 external callers (only self-delegation) | parameter-based overloads (`:113,:234`) are the live ones |
| Dead theme tokens `BossBronze`, `BossSilver`, `BossGold`, `DestructiveRed` | 0 usages outside `Theme.kt` | `GrammarMateColors` fields + getters — retire or adopt in P5 (Boss rewards are currently plain M3 containers, `GrammarStoryRoadmapScreen.kt:268–271`) |

---

## 3. Duplication / dispersion evidence (baseline numbers for P1+)

Each prior observation re-verified on `3569a9fe9`; corrections marked **[CORRECTED]**.

1. **TrainingCardSession dead / contract alive** — confirmed. 0 call sites for `TrainingCardSession`; `CardSessionContract` used by `TrainingScreen.kt:650` (adapter), `UnifiedInputControlsBar.kt:91`, `DailyPracticeComponents.kt:30,99`; `CardSessionStateModel` implemented by `feature/training/SessionRunner.kt:73` and an anonymous object in `TrainingScreen.kt:404`.
2. **DifficultyRatingRow dead with raw hex** — confirmed (see §2.1).
3. **`LocalGrammarMateColors` / token access** — **[CORRECTED nuance]**. Direct `LocalGrammarMateColors` references outside `Theme.kt`: **0** (prior claim holds). BUT the "legacy" top-level getters (`CorrectGreen`, `ProgressGreen`, `SrsAgainBackground`, … all 25) are NOT hardcoded: each delegates to `LocalGrammarMateColors.current.*` (`Theme.kt:141–202`), so every screen using them is theme-aware (light+dark correct). Files using each getter (excluding Theme.kt): `CorrectGreen`/`IncorrectRed` → TrainingScreen, TrainingCardSession(dead); `MasteryGreen` → VocabDrillScreen, SessionProgressIndicator, ChapterLessonsScreen, HomeScreen, GrammarStoryRoadmapScreen; progress/speed getters → SessionProgressIndicator only; all 8 `Srs*` getters → DailyPracticeScreen only; `VocabIntervalOrange`/`VocabCorrectBackground`/`VocabIncorrectBackground` → VocabDrillScreen; `Boss*`/`DestructiveRed` → nobody. Remaining true bypass: `DifficultyRatingRow` (raw hex, dead) and `ClickableHintAnswerCard` rank colors (raw hex, live, `:65–67`), plus hardcoded gradient hexes in `Glass.kt:79–121` and `HomeScreen.kt:231` (`0xFFFF8F00` streak color).
4. **@Preview count** — confirmed: **0** `@Preview` in all of `app/src` (main + test).
5. **Inline font sizes** — **[CORRECTED]** 62 literal `fontSize = N.sp` occurrences (prior: ~87) **plus 20** scaled expressions `fontSize = (Nf * textScale).sp` = **82 total**. Distinct literal values: **14** (prior: 14 — confirmed). By frequency: `20.sp`×10, `14.sp`×9, `18.sp`×7, `48.sp`×6, `12.sp`×6, `11.sp`×5, `16.sp`×4, `13.sp`×4, `24.sp`×3, `32.sp`×2, `28.sp`×2, `10.sp`×2, `40.sp`×1, `22.sp`×1.
6. **RoundedCornerShape inline** — **[CORRECTED]** **37 occurrences** across **16 files** (prior: ~47 / ~15). Of these, 5 are the legitimate owner `GrammarMateShapes` (`Theme.kt:258–264`); **32 inline across 15 screen/component files**. Values: `12.dp`×16, `8.dp`×11, `16.dp`×3, `20.dp`×2, `10.dp`×2, `4.dp`×1, `28.dp`×1, `14.dp`×1. Examples: `SharedComponents.kt:70` (NavIconButton 12dp), `SessionProgressIndicator.kt:68,76` (12dp), `HomeScreen.kt:684,765,837` (10/10/12dp), `DifficultyRatingRow.kt:67` (8dp, dead).
7. **Full-screen loading re-implementations** — confirmed and raised: **≥5 → 10 inline variants** (Box + centered `CircularProgressIndicator`, optionally + message):
   1. `GrammarMateApp.kt:132–147` — app `isLoading` (spinner + "preparing")
   2. `GrammarMateApp.kt:996–1001` — CHAPTER_LESSONS fallback (spinner only)
   3. `ui/AppRoot.kt:45–66` — `StartupScreen` restore states (spinner + message)
   4. `ui/VocabDrillScreen.kt:140–152` — `state.isLoading` (spinner + label)
   5. `ui/VerbDrillScreen.kt:75–92` — `state.isLoading` (spinner + label)
   6. `ui/DailyPracticeScreen.kt:117–127` — session inactive (spinner + label)
   7. `ui/DailyPracticeScreen.kt:176–180` — during card-block navigation (spinner only)
   8. `ui/screens/BackgroundVocabScreen.kt:188–196` — audio service binding (spinner only)
   9. `ui/screens/BackgroundVocabScreen.kt:242–256` — deck loading (spinner + hardcoded RU string "Загрузка слов…" — unlocalized)
   10. `ui/dialogs/AppDialogs.kt:471–485` — `DialogState.DailyLoading` overlay card (spinner + label)
   (Component-embedded spinners — `TtsSpeakerButton` Initializing, `TtsDownloadStatusBanner:139`, `AsrStatusIndicator`, `VerbDrillSheets:153`, `VocabDrillScreen:798` — are separate, non-full-screen cases.)
8. **Back-arrow header icons inline vs `NavIconButton`** — confirmed: **11 inline header back arrows** (NavIconButton is used only inside UnifiedNavigationRow for the prev-card control, not headers): `DailyPracticeScreen.kt:220`, `AuxDrillScreen.kt:55`, `VocabDrillScreen.kt:261`, `VocabDrillScreen.kt:493`, `VerbDrillScreen.kt:215`, `GrammarStoryRoadmapScreen.kt:97`, `LadderScreen.kt:50`, `StoryReaderScreen.kt:90`, `LessonRoadmapScreen.kt:148`, `BackgroundVocabScreen.kt:181` (only site using the `AutoMirrored` variant), `ChapterLessonsScreen.kt:71`. Note: deprecation-wise all use deprecated `Icons.Default.ArrowBack` except BackgroundVocabScreen — an API-hygiene defect to record separately. (`PomodoroSelectorSheet.kt:63` uses ArrowBack as a pane-toggle icon, not a header back.)
9. **SRS rating row variants (Again/Hard/Good/Easy)** — 3 variants:
   - `ui/DailyPracticeScreen.kt:341–362` — one Row of 4 `OutlinedButton`s colored by `Srs*Background/Srs*Text` tokens; flat labels; one-shot `isRated` guard.
   - `ui/VocabDrillScreen.kt:559–624` — 2×2 grid (Again|Hard / Good|Easy) with interval-ladder subtitles (`1,2,4,7,…56` days; the Again subtitle is the localized `<1m` string, not days), different color sources (`colorScheme.error`, `VocabIntervalOrange`, `primary`, `MasteryGreen`); GOOD/Easy preview next-step math (`:605,:619`).
   - `ui/components/DifficultyRatingRow.kt` — dead FilterChip variant (§2.1).
   Semantics differ (daily one-shot rating vs ladder-preview rating) — per plan §6.4 these are not automatic merge candidates.
10. **Answer/hint card variants** — `HintAnswerCard` (1 live caller), `ClickableHintAnswerCard` (1 live caller), both selected by `clickableWordHints && wordInfoCache != null` inside `UnifiedInputControlsBar.kt:167–182`; plus dead inline use in `TrainingCardSession.kt:450`. Localized prefix vs hardcoded `"Ответ: "` (`ClickableHintAnswerCard.kt:36`) — localization defect to record.

---

## 4. Effect / state ownership ledger for P3/P4 (plan §5)

Format: owner (file:line) — trigger — keys — lifecycle/cancellation. "Ambiguous/duplicated" rows are flagged. All paths relative to `com/alexpo/grammermate/`.

| # | Effect | Current owner | Event trigger / keys | Lifecycle / cancellation notes |
|---|---|---|---|---|
| 1 | Voice recognition launcher (training path) | `ui/screens/TrainingScreen.kt:554–566` (`AnswerBox.speechLauncher`) + `launchVoiceRecognition` helper `:756–778` | `LaunchedEffect` `:567–601`, keys: `currentCard?.id`, `inputMode`, `sessionState`, `voiceTriggerToken`; 200ms delay; branches offline ASR (`:584`) vs platform recognizer | Auto-start variant A (**200ms**). Cancelled on key change; audit `voiceStart` inside effect. Voice result → `onInputChange` + immediate `onSubmit` (`:560–564`) — different from bar behavior (below). |
| 2 | Voice recognition launcher (input bar) | `ui/components/UnifiedInputControlsBar.kt:136–145` + `launchVoice` `:148–163` | Manual (mic buttons `:237–247`, `:298–316`); `scope.launch { onBluetoothSetup() }` then `speechLauncher.launch(intent)` | Result → `contract.onVoiceInputResult(spoken)` only (no auto-submit). **Ambiguous/duplicated with row 1**: two launchers with different result handling and prompt sources coexist on the same screen. |
| 3 | Voice auto-launch (vocab drill) | `ui/VocabDrillScreen.kt:470–478` | `LaunchedEffect(session.currentIndex, voiceAutoStart)`; conditions `!isFlipped && !voiceCompleted && !isVoiceActive`; **500ms** delay + re-check | Auto-start variant B; dead shared component `VoiceAutoLauncher` encodes the same logic (`VoiceAutoLauncher.kt:27–34`). **Duplicated logic, single live instance.** |
| 4 | Auto-flip after voice result (vocab) | `ui/VocabDrillScreen.kt:462–467` | `LaunchedEffect(session.voiceCompleted, session.voiceResult)`; 800ms delay → `onFlip()` | Distinct timing (800ms); cancellation on key change. |
| 5 | Bluetooth SCO setup/cleanup | Training: `vm.audio::startBluetoothMicIfNeeded` / `stopBluetoothMicIfNeeded` wired at `TrainingScreenContent.kt:168–169`; Vocab: `GrammarMateApp.kt:871–872`; Daily: `DailyPracticeScreenContent.kt:158–159`; invoked inside rows 1/2 and `DailyPracticeScreen.kt:328` | Fire-and-forget suspend; cleanup passed but invoked only via `onBluetoothCleanup` params (grep shows no live caller in TrainingScreen — passed down, currently unused there: `TrainingScreen.kt:146,538,720`) | **Ambiguous**: cleanup parameter threaded but not invoked at UI layer; actual stop lives in AudioCoordinator (`shared/audio/AudioCoordinator.kt`). Verify before P3b. |
| 6 | Bluetooth permission launcher (API 31+) | `ui/GrammarMateApp.kt:302–310` (`btPermissionLauncher`) + settings toggle `:351–362` | Manual (SettingsSheet toggle) | Grants → `vm.audio.setUseBluetoothMic(true/false)`. |
| 7 | TTS speak/stop toggle | `ui/GrammarMateApp.kt:207–225` (`onTtsSpeak` remember, keys: ttsState, ttsModelReady, bgTtsDownloadStates, languageId, answerText, currentCard, dialogs) | TtsSpeakerButton click (CardPrompt `TrainingScreen.kt:495–505`, audit inside) | Stops when Speaking; opens `DialogState.TtsDownload` when model missing; speaks answerText/first accepted answer. |
| 8 | TTS download-done auto-speak | `ui/dialogs/AppDialogs.kt:174–179` | `LaunchedEffect(dialogs==TtsDownload, ttsDownloadState)` | Dismisses dialog + speaks once. |
| 9 | Background TTS download progress | `ui/GrammarMateApp.kt:290–298` (progress bar `calcBgDownloadProgress`), banner `TtsDownloadStatusBanner` | Level-triggered render, no effect | Hidden during story playback. |
| 10 | Voice trigger token (boss re-trigger) | Produced by `feature/boss/BossOrchestrator.kt:217–224`; consumed by row 1 keys | Token increment on new boss card | Only consumer is TrainingScreen effect — keep token semantics when extracting. |
| 11 | Automatic advance (training) | Not a UI effect: state machine pauses awaiting explicit Next after hint/correct (`UnifiedNavigationRow` doc `:109–112`); advance via `vm.navigateNext` wired `GrammarMateApp.kt:676–685` (+ `verbDrillVm.markCardCompleted()` for drill-like returns) | Explicit clicks; VM one-shot `navigationEvents` stream `GrammarMateApp.kt:276–284` routes completion | Auto-advance-adjacent UI effects are rows 3/4 only (vocab). **P3a verification (cbb2f9b24):** the onNext boundary reads `state.cardSession.lastResult == false || answerText != null` to decide `markCardCompleted()` (`GrammarMateApp.kt:675–685`) — i.e. the same `lastResult` state the result display renders is ALSO an advance-decision input. The display is a pure downstream render (no effect); P3a moves only rendering, so this interplay is untouched. Proposed owner: unchanged. Regression evidence: RegularLessonClickUiTest + VerbDrillSessionCardRegressionTest in the P3a full gate. |
| 12 | Input text state (training) | Dedicated flow: `vm.inputText` collected at `TrainingScreenContent.kt:108`; changed via `vm.training.onInputChanged` | Every keystroke | Plan invariant: do NOT fold into combined uiState. Dead rival: `TrainingCardSession.kt:126` local state. **Auto-submit on exact match** lives in `UnifiedInputControlsBar.kt:210–227` (bar-side, `Normalizer.isExactMatch`). |
| 13 | Word-info cache (clickable hints) | `ui/components/UnifiedInputControlsBar.kt:114–125` — `remember(clickableWordHints, baseDir) { WordInfoCache.getInstance(baseDir) }` + `LaunchedEffect(wordInfoCache) { loadDrillData() }` | Feature toggle / baseDir change | Filesystem access inside UI layer (`ui/components/WordInfoCache.kt:25` singleton). **Flagged**: file I/O in component; move behind adapter in P3b. Popup state `:128–130,408–421`. |
| 14 | Grammar chip lookup (training) | `ui/screens/TrainingScreenContent.kt:121–124` — `GrammarChipStore.getChipForLesson(lessonId, packId)` | Every recomposition of content (not an effect — a synchronous store read) | Pack-scoped. **Flagged**: composition-time store lookup; candidate for VM slice. |
| 15 | Pomodoro timer | VM: `startPomodoro:564`, `pausePomodoro:575`, `resumePomodoro:582`, `cancelPomodoro:589`, tick sink `:491` (`ui/TrainingViewModel.kt`); UI collects `pomodoroRemainingSeconds` `TrainingScreenContent.kt:104`; banner `TrainingScreen.kt:251–266`; completion summary `TrainingScreen.kt:183–197`; selector sheet `HomeScreen.kt:476–485`; start → `GrammarMateApp.kt:509–513` | App lifecycle: `DisposableEffect` lifecycle observer `GrammarMateApp.kt:195–205` → `vm.onAppBackgrounded()/onAppForegrounded()` | Session active-time timer separately: `SessionRunner.resumeTimer` (`feature/training/SessionRunner.kt:1358–1373`, 500ms tick, saves every ~10s). **P3a verification (cbb2f9b24):** the P3a progress display consumes NO Pomodoro tick — `pomodoroRemainingSeconds` is collected at `TrainingScreenContent.kt:104,163` and feeds only `PomodoroTimerBanner` (out of P3a scope). The progress display's speed input is computed at the TrainingScreen boundary (`:355`/`:363`: `voiceWordCount / (voiceActiveMs / 60000.0)`) from `cardSession.voiceActiveMs/voiceWordCount`, owned by SessionRunner (accumulated on submits, `SessionRunner.kt:523,550`). Formula stays at the boundary; display receives a plain `Int`. Proposed owner: unchanged. Regression: full gate incl. Pomodoro suites. |
| 16 | Completion callback (training) | `TrainingScreen.kt:203–216` renders `SessionCompletionContent(onDone = onSessionDone)`; routing `GrammarMateApp.kt:737–774` (switch on `returnTo`: DAILY_PRACTICE → `vm.daily.onBlockComplete()`+popTo; CHAPTER_LESSONS/LESSON/drill-return → popTo; else resetStack HOME) | OK button | Verb-drill variant: `VerbDrillCompletionContent` More → `GrammarMateApp.kt:724–735` (`persistSessionState`+`nextBatch`+`replaceVerbDrillCards`); unattempted-exit guard `TrainingScreen.kt:159,170`. |
| 17 | Daily practice session wiring | `DailyPracticeScreenContent.kt:89–162`: block start `:109–118` (sets `returnTo`, navigates TRAINING), `onComplete` → `vm.daily.onBlockComplete()` `:133–142`, exit → `cancelDailySession`+HOME `:126–132`; VocabFlashcardBlock speech launcher `DailyPracticeScreen.kt:280–294` (auto-rates GOOD on correct voice) | Block transitions; `remember(task.id)` local card state `:261–267` | Overlaps rows 3/11 — keep distinct. |
| 18 | Verb drill session persistence on exit | `GrammarMateApp.kt:695–721` (`onShowExitDialog`: persist+`exitVerbDrillSession`+popTo/resetStack), `:724–735` (More), `:779–786` (BackHandler drill return) | Exit/stop/system back | Three parallel paths — must remain behaviorally identical; covered by click tests. |
| 19 | Pack-scoped VM reloads | `GrammarMateApp.kt:166–172` (verbDrillVm on pack/lang), `:811–817` (redundant reload on VERB_DRILL entry — deliberate), `:844–850` (vocabDrillVm), `:881–885` (auxVm) | `LaunchedEffect(packId, languageId)` | **Flagged**: VERB_DRILL entry reload duplicates outer effect by design (comment `:808–809`). |
| 20 | Recognizer/permission inventory (misc) | `DailyPracticeScreen.kt:280`; `ui/screens/BackgroundVocabScreen.kt` service binding (player null → spinner `:188`); `VerbDrillScreen.kt:73–75` `refreshLastSessionContext` on entry | Screen entry / manual | |
| 21 | Training result feedback display (P3a, added at cbb2f9b24 pre-movement) | Display: `ResultBlock` `ui/screens/TrainingScreen.kt:718` — render-only (no `remember`, no `animate*`, no test tags — verified by read). State owner: `CardSessionState.lastResult: Boolean?` (`data/Models.kt:534`) mutated by `feature/training/SessionRunner.kt` — set `true` on correct submit `:550,:644`; `false` on incorrect `:479,:492,:503`; reset `null` on next `:804`, prev `:833`, paused-nav `:861,:891`, sub-lesson switch `:906`, repeat `:1014`, lesson start `:265` | Recomposition on uiState change; no LaunchedEffect, no keys | No effects owned — nothing to cancel. Proposed owner: **unchanged** — boundary adapter `ResultBlock(state)` keeps its signature and call site `TrainingScreen.kt:396`; rendering moves to a stateless organism `ui/features/training/components/TrainingResultBlock(isCorrect, modifier)`. Regression evidence: `RegularLessonClickUiTest` incorrect-result case asserts `"Incorrect"` displayed (`:687`); progress case asserts `"1 / 2"` (`:744`); full unit gate |
| 22 | Progress fraction display math (P3a) | `ui/components/SessionProgressIndicator.kt:50` — `if (total > 0) current.toFloat() / total else 0f` lives INSIDE the display; `current`/`total` are derived at the call sites `TrainingScreen.kt:350–364` from `cardSession.currentIndex/subLessonTotal` + boss fields (owners: SessionRunner/VM) | Recomposition only — not an effect | Display math, not a timer/formula source — moves WITH the display verbatim (total>0 guard preserved). Call-site derivation unchanged, stays at the boundary. Proposed owner: unchanged. Regression: `RegularLessonClickUiTest` `"1 / 2"` assertion (`:744`) + full gate |
| 23 | Speed-arc thresholds + low-fraction label branch (P3a) | `ui/components/SessionProgressIndicator.kt:53–57` (20/40 wpm → red/yellow/green arc), `:82` (fraction < 0.12 → MasteryGreen label) | Render-only | Pure display policy — moves with the display verbatim; thresholds/colors unchanged (no retune). Proposed owner: unchanged. Regression: P3a previews cover all three speed colors + low-fraction branch; full gate |

---

## 5. Baseline verification (exact commands and results)

Environment: Windows/Git Bash; all Gradle execution via repo helper `./gw.sh . <task> --no-parallel --max-workers=1` (sets JAVA_HOME=Corretto 21.0.8 and the 3-jar wrapper classpath; no direct gradle/gradlew calls). One Gradle process at a time; no concurrent builds.

1. **`:app:assembleDebug`** — `./gw.sh . :app:assembleDebug --no-parallel --max-workers=1`
   Result: **BUILD SUCCESSFUL in 4m 30s** (39 tasks: 1 executed, 38 up-to-date — incremental over existing build dir). APK present at `app/build/outputs/apk/debug/grammermate.apk`.
2. **`:app:testDebugUnitTest`** — `./gw.sh . :app:testDebugUnitTest --no-parallel --max-workers=1`
   First invocation returned UP-TO-DATE in 10s with **stale** results (XML dated 2026-09-13) — not acceptable as baseline evidence. Re-ran with forced execution:
   `./gw.sh . :app:testDebugUnitTest --rerun --no-parallel --max-workers=1`
   Result: **BUILD SUCCESSFUL in 2m 14s** (28 tasks: 1 executed = the test task itself; Robolectric caches warm from prior runs).
   **Totals (parsed from 67 fresh `TEST-*.xml` suite files, timestamped 2026-09-27 10:40): 546 run / 546 passed / 0 failed / 0 skipped.** No re-run for flake needed (no failures, no OOM).
3. **`:app:connectedDebugAndroidTest`** — **UNVERIFIED GATE, not passed.** No local emulator/adb device exists in this environment; no androidTest execution attempted. CI (.github/workflows/ci.yml) remains the enforcement point for this gate.
4. Post-test hygiene: Robolectric sandbox temp dirs cleaned (`rm -rf "$TEMP"/robolectric-*`) after confirming no java test process remained.
5. Visual/screenshot baseline: **explicitly unavailable** — no emulator/device; previews count in repo is 0. Recorded as a P1 gap (plan P0 row requires "screenshot or explicitly unavailable visual baseline"; this is the latter).

---

## 6. Open questions (for owning integrator)

1. **Dead-code disposal order**: `TrainingCardSession`, `DifficultyRatingRow`, `VoiceAutoLauncher`, `DailyWordBankSection`, `VerbDrillSheets` VM-overloads, `getUserInitials`, and 4 dead theme tokens all sit on the shared `feature/atomic-design` branch. Retire in P1/P6, or keep `VoiceAutoLauncher` as the single P3b implementation replacing the two inline variants (its logic matches the live VocabDrill inline copy exactly)? Decision affects P3b scope.
2. **Two voice launchers on one screen** (ledger rows 1–2): TrainingScreen/AnswerBox auto-launch (200ms, result→submit) vs UnifiedInputControlsBar manual launch (result→input only). Unifying changes observable behavior; current plan says preserve. Confirm the duplication is intentional before P3b display extraction.
3. **Bluetooth cleanup is threaded but never invoked at the UI layer** for the training path (`onBluetoothCleanup` param unused at `TrainingScreen.kt:146,538,720`). Unrelated pre-existing defect — record as separate work, do not "fix" during extraction.
4. **`UnifiedInputControlsBar` performs filesystem I/O** (`WordInfoCache.loadDrillData`) and composition-time store reads exist (`GrammarChipStore` in `TrainingScreenContent.kt:123`). Where does the P3b boundary sit — adapter above the bar, or VM slice first?
5. **Stale doc comments** in `SharedComponents.kt:56,93` ("Used by GrammarMateApp and TrainingCardSession") and `VoiceAutoLauncher.kt:9` ("Shared between VocabDrillScreen and TrainingScreen") misdescribe reality; also `docs/specification/14-theme-and-ui-components.md` per plan §2. Update when each area migrates.
6. **Plan §2 vs reality**: plan's §2 table row for `ui/TrainingCardSession.kt` says "no production invocation found in the baseline search" — still true. No other §2 disagreements found beyond the token-getter nuance (§3.3 above) and count corrections (§3.5–3.7).

## 7. Recommended next slice (P1 — Foundations), file-anchored

Scope per plan §5 P1 row: `Theme.kt`, `Glass.kt`, values required by the P2 pilot. Concretely:

1. Split `ui/Theme.kt` so foundational declarations (`GrammarMateColors` + light/dark instances + `LocalGrammarMateColors`, `GrammarMateShapes`, `GrammarMateTypography`) have no import of `ui/components/Glass.kt`; keep `GrammarMateTheme` as the app facade that continues wrapping content in `AppBackground` (preserve `Glass.kt:66` safeDrawing inset behavior — current single caller `Theme.kt:309`).
2. Promote the values the pilot needs first: prompt font scale policy (`20f/13f/18f * ruTextScale` — 20 live scaled-fontSize sites — 17 in live screens + 3 inside dead TrainingCardSession.kt — already follow this pattern), the `12.dp/8.dp` corner radii (27 inline occurrences) only where a token has a real owner; do not tokenize all 8 values mechanically.
3. Keep the 25 token getters compiling (compatibility imports) — they are the only consumption path; migrating call sites off them is not P1.
4. Add the first pure preview fixtures (currently 0 previews repo-wide): a `designsystem/preview` helper + light/dark fixture for tokens and for `SessionProgressIndicator`/`TtsSpeakerButton` (pure, no VM) to establish the P2 preview pattern. No `@Preview` exists today, so this is additive.
5. Do NOT touch `TrainingCardSession.kt` or other dead files in P1 — schedule retirement as its own reviewed change (P6 or an explicit cleanup slice) so the P2 diff stays reviewable.
6. Verification for P1: `./gw.sh . :app:assembleDebug --no-parallel --max-workers=1`, full unit gate with `--rerun` (note: plain invocation is UP-TO-DATE-prone on an unchanged tree — force re-run or change sources), plus rendered light/dark screenshots if a device/emulator can be provisioned; otherwise record the gap as done here.

---

## 8. P1 status update (foundations slice)

- Date: 2026-09-27. Slice baseline revision: `24f4d50ee259abce38a4dc6a913d07c976696da6` (branch `feature/atomic-design`). Sections 1–7 above are unchanged evidence for revision `3569a9fe9`; their Theme.kt/Glass.kt line numbers do not apply to the post-P1 tree (line references below are for `24f4d50ee` + P1 diff).
- Production code changed: `ui/Theme.kt` only (315 → 137 lines), plus new files under `ui/designsystem/`. **No screen/component file touched; zero call-site changes.** `ui/components/Glass.kt` untouched.
- Foundation extraction (statuses move to `extracted`):
  - `ui/designsystem/foundation/GrammarMateColors.kt` — `GrammarMateColors` data class (`:21`), `LightGrammarMateColors` (`:59`), `DarkGrammarMateColors` (`:89`), `LocalGrammarMateColors` (`:119`), Material schemes `LightGrammarMateColorScheme`/`DarkGrammarMateColorScheme` (`:129/:154`, were private `LightColors`/`DarkColors`; `internal`, values verbatim).
  - `ui/designsystem/foundation/Type.kt` — `GrammarMateTypography` (`:15`, was private in Theme.kt; now `internal`).
  - `ui/designsystem/foundation/Shapes.kt` — `GrammarMateShapes` (`:33`, public as before) built from new `GrammarMateRadii` tokens (`:21`).
  - `ui/designsystem/foundation/TextScale.kt` — new `PromptTextScale` (`:21`: `prompt=20f`, `context=13f`, `drillPrompt=18f`) + `scaledSp` (`:38`).
  - Facade `ui/Theme.kt` keeps: `GrammarMateTheme` (`:108`, still wraps `AppBackground` → gradient + `safeDrawing` insets, behavior unchanged) and all 25 compat color getters (`:44–105`, same package `com.alexpo.grammermate.ui`, so both explicit imports and same-package unqualified resolution are preserved).
- Dependency-rule audit (rule 1): the four foundation files import only `androidx.compose.*` — zero `com.alexpo.*` imports; no foundation→components/features/navigation/stores edge exists. Facade imports foundation + `ui.components.AppBackground` only. Status of §2 `AppBackground` row: boundary verified, wrapper stays app-level via facade; Glass.kt not modified.
- §2.1 dead theme tokens `BossBronze`/`BossSilver`/`BossGold`/`DestructiveRed`: moved with the palette as-is (still present, still 0 non-facade callers); retirement remains P6 as recorded.
- §3.3 token access: unchanged consumption path; getters now documented as facade-owned compat surface, removal = P5+ after callers migrate to `LocalGrammarMateColors.current.*`. Raw-hex bypasses (`ClickableHintAnswerCard.kt:65–67`, `Glass.kt` gradients, `HomeScreen.kt:231`) intentionally untouched this slice.
- §3.5 text-scale pattern: policy now has a foundation owner (`PromptTextScale`/`scaledSp`); the 17 live scaled-fontSize call sites are unchanged (adoption starts with the P2 prompt pilot).
- §3.6 corner radii: `12.dp`/`8.dp` now have tokens (`GrammarMateRadii.small`/`.extraSmall`) consumed by `GrammarMateShapes`; the 27 inline occurrences are unchanged (adoption in later slices). Medium/large/extraLarge radii intentionally left inline in `Shapes.kt` until a token has a second consumer.
- §3.4 preview count: 0 → **6** in `ui/designsystem/preview/FoundationPreviews.kt` (palette light+dark with name+hex swatches, typography scale, shapes, prompt text-scale at 1.0x/1.5x; the 7th `@Preview` grep hit is a prose comment, not a function). All pure fixtures; `ui-tooling-preview` was already an `implementation` dep (app/build.gradle.kts:130), `ui-tooling` debug-only (`:153`) — no dependency changes.
- Validation on this tree: `:app:assembleDebug` BUILD SUCCESSFUL (39s); `:app:testDebugUnitTest --rerun` BUILD SUCCESSFUL (2m 12s), parsed 67 fresh `TEST-*.xml`: **546 run / 546 passed / 0 failed / 0 skipped** (equal to the P0 baseline). `connectedDebugAndroidTest` remains an unverified gate (no emulator/adb); rendered-preview/device screenshots remain unavailable locally (previews compile but were not rendered here).

---

## 9. P2 status update (prompt pilot)

- Date: 2026-09-27. Slice baseline revision: `bb3773ab8` (branch `feature/atomic-design`, tree clean; §8's P1 foundation state). Sections 1–8 above are prior-slice evidence; line references below are for the post-P2 tree.
- Production code changed: 2 files modified, 2 files added. Screens/drills/bar/navigation/dead files/build files untouched; no dependency changes; no visual value changes.

### 9.1 What was extracted

| Change | Location (post-P2) | Detail |
|---|---|---|
| **NEW** `TrainingPromptCard` organism + 9 previews | `ui/features/training/components/TrainingPromptCard.kt` (component `:63`) | Display-only feature organism: `label, displayPrompt, situationContext: String?, textScale: Float, audioState: TtsButtonState, audioEnabled: Boolean, onAudioClick, modifier`. Renders `card_prompt_text` tag (`:83`), context line, TTS atom. Zero `com.alexpo.*` imports except `ui.designsystem.*` and preview-only uses of `ui.GrammarMateTheme` + `data.ThemeMode` (P1 preview convention). No ViewModel/session-contract/data-layer imports in the component itself. |
| **NEW** `TtsSpeakerButton` atom + `TtsButtonState` | `ui/designsystem/atoms/TtsSpeakerButton.kt` (state `:34`, atom `:62`) | Rendering moved verbatim from `SharedComponents.kt:97`, re-typed from `data.TtsState` to UI-owned sealed `TtsButtonState { Idle, Initializing, Speaking, Error(reason) }` (added `modifier` param per plan §4 API rule). §2 `TtsSpeakerButton` row: destination reached as proposed (`designsystem/atoms`); status → `extracted`+`adopted`. |
| Legacy `TtsState`-typed overload (compat wrapper) | `ui/components/SharedComponents.kt:90` | Kept ONLY so the dead `ui/TrainingCardSession.kt:343,442` call sites compile (untouchable per slice constraints). Delegates to the single atom implementation; 12 lines. **Removal condition: dies with TrainingCardSession retirement (P6).** Owner: integrating integrator. |
| `CardPrompt` → boundary adapter | `ui/screens/TrainingScreen.kt:466` | Same public signature `(state, onSpeak)`; production call site `:368` unchanged (`if (!isDrillStyle)` guard untouched → no duplicate prompt in VERB_DRILL/DAILY_VERBS, which render the drill line at `:331`). Adapter owns: `HintCalculator.calculateEffectiveHints` + no-cards fallback, `SentenceCard.contextRu` cast, `TtsState → TtsButtonState` mapping (`else → Idle` covers Idle/Ready/Paused exactly as the old button's `else` branch did), `audioEnabled = currentCard != null`, and the TTS audit log inside `onAudioClick` (click-time reads of `currentCard.id`/`promptRu` preserved verbatim). No `LaunchedEffect`/`remember` moved; no effect keys/callback order changed. |

### 9.2 Parity evidence (old inline CardPrompt → TrainingPromptCard)

| Invariant | Old (TrainingScreen.kt @ bb3773ab8) | New | Backing evidence |
|---|---|---|---|
| `card_prompt_text` tag | `Modifier.testTag("card_prompt_text")` on prompt Text | Identical, same node position/modifier order | `RegularLessonClickUiTest` 10/10 + `PackSwitchClickUiTest` 4/4 pass — both assert `assertIsDisplayed()` + `assertTextContains` hint-calculated content through the extracted path |
| contextRu visibility | rendered whenever non-null, independent of hint ladder | `situationContext: String?` rendered whenever non-null (blank-safe, same semantics) | Code-identical rendering; not exercised by a dedicated test (pre-existing gap, unchanged) |
| Prompt scale | `(20f * ruTextScale).sp` | `scaledSp(PromptTextScale.prompt /*20f*/, textScale)` — same math | Compile + full gate; token unit `TextScale.kt:38` is `(baseSp * textScale).sp` |
| Context scale | `(13f * ruTextScale).sp` | `scaledSp(PromptTextScale.context /*13f*/, textScale)` | same |
| Drill prompt scale | `(18f * ruTextScale).sp` at `:331` | `scaledSp(PromptTextScale.drillPrompt /*18f*/, ...)` — same math (token adoption, this slice) | VerbDrill-related suites in full gate green |
| TTS button states/enabled | `TtsSpeakerButton(ttsState, enabled = currentCard != null, onClick)`; else-branch = idle visual for Idle/Ready/Paused; Error tooltip + OOM heuristic (`reason.contains("memory", true)`) | Atom renders byte-identical `when`; boundary maps `Speaking/Initializing/Error(reason)` and `else → Idle` | `:app:assembleDebug` + full unit gate; rendering ported verbatim (only type renamed) |
| A11y labels | `content_desc_stop` / `content_desc_tts_error` / `content_desc_listen` | Same string resources, same icons/tints | strings-components.xml:4–6 unchanged |
| Localized labels | `training_ru`, `training_no_cards` resolved in screen | Resolved in boundary adapter, passed as `String` | strings-screens.xml:156–157 unchanged |
| Drill suppression | `if (!isDrillStyle) CardPrompt(...)` guard at call site | Guard untouched; adapter only delegates rendering | full gate incl. VerbDrillSessionCardRegressionTest, VerbPracticeClickUiTest, BossBattleClickUiTest green (546/546) |

### 9.3 Token adoption (first production adopters, per P1 §8 forecast)

`PromptTextScale.prompt`/`.context`/`.drillPrompt` + `scaledSp` now own all three prompt-surface anchors (organism `:85,:95`; drill line `TrainingScreen.kt:331`). §3.5's remaining live inline scaled sites (VocabDrillScreen, DailyPracticeScreen, SimpleMarkdownParser, etc.) adopt in their own slices, unchanged here.

### 9.4 Previews

`@Preview` count: 6 → **15** (+9 in `TrainingPromptCard.kt`, beside the component): Light, Dark, long-RU-prompt, long-IT-prompt+contextRu, font-scale 1.5x, audio idle/initializing/speaking/error(reason tooltip). All pure fixtures (no ViewModel/filesystem/pack); rendered previews remain unverified locally (no device — see §8).

### 9.5 Validation (exact)

1. Focused (after code complete): `./gw.sh . :app:testDebugUnitTest --no-parallel --max-workers=1 --rerun --tests "com.alexpo.grammermate.ui.RegularLessonClickUiTest" --tests "com.alexpo.grammermate.ui.PackSwitchClickUiTest"` → BUILD SUCCESSFUL in 30s; XML: RegularLessonClickUiTest **10/10**, PackSwitchClickUiTest **4/4**, 0 failures.
2. Phase gate: `:app:assembleDebug` → BUILD SUCCESSFUL in 17s; full `:app:testDebugUnitTest --rerun` → BUILD SUCCESSFUL in 2m 11s, parsed 67 fresh `TEST-*.xml`: **546 run / 546 passed / 0 failed / 0 skipped** (= P0/P1 baseline).
3. Unverified gates: `connectedDebugAndroidTest` (no emulator/adb — unchanged since P0); rendered preview/device screenshots unavailable locally. Robolectric temp dirs cleaned post-run.

### 9.6 Ownership evaluation (honest; plan demands this)

- **Real ownership gain, not only forwarding:** the boundary adapter `CardPrompt` is not a pass-through — it holds the hint calculation, `SentenceCard` interpretation, TTS-state mapping, enablement and audit logging that plan §4 assigns to the integration layer; the organism holds layout/tag/typography policy and is now previewable in isolation (all 4 audio states, dark, 1.5x scale) for the first time. The audio rendering also gained a single implementation without a data-layer dependency (`data.TtsState` no longer enters UI display components).
- **But reuse is still potential, not proven:** one production caller. If P4 does not produce a second consumer with compatible semantics (e.g. a completion/report card or an answer panel following this contract), the honest simplification per plan §5 is to fold the organism back beside TrainingScreen and keep only the boundary function — the wrapper cost today is real but small (1 adapter + 1 legacy overload).
- **Costs to track:** legacy `TtsState` overload in SharedComponents.kt must not outlive the dead file (P6); drill prompt line stays screen-local intentionally (single site, distinct semantics — not merged into the organism).
- §2 status updates: `CardPrompt` → `extracted`+`adopted`; `TtsSpeakerButton` → `extracted`+`adopted` (atom), legacy overload = compat wrapper with removal condition.

### 9.7 Rollback boundary and next slice

- Rollback = revert the 2 modified files + delete `ui/designsystem/atoms/TtsSpeakerButton.kt` + `ui/features/training/components/TrainingPromptCard.kt` (self-contained on top of `bb3773ab8`; no data/build/dependency surface).
- Recommended next: **P3a** per plan §5 — training result display (`ResultBlock`) and `SessionProgressIndicator` display extraction; effect ledger §4 rows 11/15 stay at the boundary. Reuse decision for `TrainingPromptCard` (second consumer vs fold-back) is due at P4.

---

## 10. P3a status update (feedback/progress display)

- Date: 2026-09-27. Slice baseline revision: `cbb2f9b24` (branch `feature/atomic-design`, tree clean at start; P2 state per §9). Sections 1–9 are prior-slice evidence; file:line references below are for the post-P3a tree unless marked otherwise.

### 10.1 Ledger-first (completed BEFORE any code movement — plan §5 mandate)

§4 effect ledger updated first, in the same change set but authored before the code:

- **Row 11 (automatic advance) extended**: verified on cbb2f9b24 that the onNext advance boundary reads `state.cardSession.lastResult == false || answerText != null` to decide `verbDrillVm.markCardCompleted()` (`ui/GrammarMateApp.kt:675–685`) — the same `lastResult` state the result display renders is an advance-decision input. Display is a pure downstream render; no effect moved.
- **Row 15 (Pomodoro timer) extended**: verified the progress display consumes no Pomodoro tick — `pomodoroRemainingSeconds` is collected at `ui/screens/TrainingScreenContent.kt:104,163` and feeds only `PomodoroTimerBanner` (out of P3a scope). The progress display's `speedWpm` is computed at the TrainingScreen boundary (`:355`/`:363`) from `cardSession.voiceActiveMs/voiceWordCount` (owner: SessionRunner, accumulated on submits `SessionRunner.kt:523,550`); formula stays at the boundary, display receives a plain Int.
- **Rows 21–23 added**: (21) result feedback display state — `CardSessionState.lastResult: Boolean?` (`data/Models.kt:534`), key SessionRunner set/reset sites enumerated (true/false sets + principal null resets; further `lastResult = null` resets exist along session-reset paths — ownership conclusion unaffected); display block verified to contain no `LaunchedEffect`/`remember`/`animate*`/test tags; proposed owner unchanged. (22) progress-fraction display math (`SessionProgressIndicator` `:50` pre-move) — display math, moves with the display, call-site derivation unchanged. (23) speed-arc thresholds (20/40 wpm) + low-fraction label branch — display policy, unchanged, no retune.

### 10.2 What was extracted / moved

| Change | Location (post-P3a) | Detail |
|---|---|---|
| **NEW** `TrainingResultBlock` organism + 6 previews | `ui/features/training/components/TrainingResultBlock.kt` (component `:54`) | Display-only: `isCorrect: Boolean?, modifier: Modifier = Modifier`. The three variants are parameterized by the source data type itself (`Boolean?`: true→localized "Correct"/[CorrectGreen], false→"Incorrect"/[IncorrectRed], null→blank placeholder line), not booleans hiding distinct semantics. Zero `com.alexpo.*` component imports except facade getters + preview-only `GrammarMateTheme`/`ThemeMode` (P1/P2 convention). No ViewModel/session-contract/data-state imports. |
| `ResultBlock` → boundary adapter | `ui/screens/TrainingScreen.kt:729` | Same public signature `(state: TrainingUiState)`; production call site `:397` unchanged (`ResultBlock(state)` byte-identical). Adapter owns the only state interpretation: reading `state.cardSession.lastResult`. No effect keys/callbacks touched (the block had none). |
| **RE-HOMED** `SessionProgressIndicator` + 4 previews | `ui/features/training/components/SessionProgressIndicator.kt` (was `ui/components/SessionProgressIndicator.kt`) | Body verbatim: fraction formula (`total > 0` guard), 20/40 wpm arc thresholds, `<0.12` MasteryGreen label branch, 70/30 weight layout, all 8 facade color getters — unchanged. Signature extended with `modifier: Modifier = Modifier` applied to the root Row (P2 precedent: TtsSpeakerButton; default keeps both call sites render-identical). Doc comment corrected: training-feature-owned, not "shared across screens". |
| Import updates | `ui/screens/TrainingScreen.kt:84`; `ui/TrainingCardSession.kt:50` (dead file, import line ONLY — no wrapper created) | Old file deleted. No compat wrapper: only two import sites existed, so a delegating wrapper would have been a pure forwarding layer (plan: don't add forwarding layers). |

**Scope honesty vs assignment wording:** the assignment described the result block as "correct/incorrect label, expected answer, TTS replay". On the actual tree (verified before acting) `ResultBlock` contained ONLY the correctness label. The accepted answer is rendered by the input bar's hint card (`UnifiedInputControlsBar` — P3b scope), and answer replay lives in the prompt card's TTS button (P2 boundary, ledger row 7). Neither was added to the result display — that would be a visual/feature change, prohibited in a parity slice. `TtsSpeakerButton` reuse therefore did not apply here (it is already reused by the P2 prompt organism).

### 10.3 SessionProgressIndicator destination decision + evidence

Caller verification (grep over `app/src/main/java`, cbb2f9b24): live callers = `ui/screens/TrainingScreen.kt:352,360` only; third reference is the dead `ui/TrainingCardSession.kt:211` (0 callers itself). No non-training screen uses it. Per the assignment rule ("if only training uses it meaningfully, feature-owned under ui/features/training/components/") and plan §3 dependency rule 4 (two similar-looking callers are insufficient for a shared pattern — here there is only ONE live caller file), the shared-molecule destination (`designsystem/molecules`, P0's provisional proposal under a then-assumed "Shared (training)" ownership) is rejected; the component is re-homed to the training feature package. It was already display-only (`Int` inputs, no state reach-in), so the work was re-home + previews + doc correction, per the "don't force moves" honesty clause — placement, not appearance, was the defect.

### 10.4 Parity evidence

| Invariant | Old (cbb2f9b24) | New | Backing evidence |
|---|---|---|---|
| Result labels/strings | `stringResource(R.string.training_correct / training_incorrect)` (`TrainingScreen.kt:722–723` old) | Identical resources resolved inside the organism | strings-screens.xml:170–171 (en) / values-ru:149,154 (ru) unchanged |
| Result colors | `CorrectGreen` / `IncorrectRed` facade getters | Same getters, no retune | `ui/Theme.kt:44,46` facade unchanged |
| Result weight/layout | Bold, Column(6.dp spacing) > Row(CenterVertically), null→empty Text placeholder | Verbatim (placeholder preserved so no-result spacing is identical) | Code-identical |
| Result test tag / animation | none existed | none added | Read-verified pre-move; ledger row 21 |
| Progress text | `"$current / $total"` 12sp Bold, `<0.12` fraction → MasteryGreen else ProgressLabelWhite | Verbatim | `RegularLessonClickUiTest` asserts `"1 / 2"` displayed (`:744`) through the re-homed component — 10/10 green |
| Progress fraction math | `if (total > 0) current.toFloat() / total else 0f` inside display | Verbatim (moves with display; call-site current/total derivation untouched at `TrainingScreen.kt:349–365`) | same test |
| Speed arc | 20/40 wpm thresholds; 360° sweep × (wpm≤100)/100; 44dp canvas, 4dp stroke | Verbatim | compile + full gate; previews cover all three speed colors |
| Animation timing/keys | none in either block | none | N/A — no animate* existed |

### 10.5 Token/component adoption

- Facade color getters remain the consumption path (per assignment: do not retune); migration to `LocalGrammarMateColors.current.*` stays P5+ as recorded in §8.
- `modifier: Modifier = Modifier` API rule (plan §4) now satisfied on both P3a components. No new tokens justified: the 12.dp corner radii here predate `GrammarMateRadii.small` but adoption would be a token-sweep with no behavioral anchor — deferred to a consolidation slice (2 sites, no repetition gain).
- `TtsSpeakerButton` atom: not applicable to the result block (no audio control on this surface — see 10.2 scope note).

### 10.6 Previews

`@Preview` count 15 → **26** (+11, all pure fixtures — no ViewModel/filesystem/pack/engine):
- `TrainingResultBlock.kt` (6): correct/incorrect/no-result × light/dark. **Deviation recorded:** the assignment's suggested "long IT answer with hints, audio states" variants do not exist for this component — it has no answer text or audio input (those live in the P3b input bar and P2 prompt card). Previews cover the component's real state space instead of inventing parameters.
- `SessionProgressIndicator.kt` (5): start 1/10 (exercises the `<0.12` low-fraction MasteryGreen label branch + zero-sweep slow-red arc), mid 5/10 (30 wpm, yellow), near-complete 9/10 (65 wpm, fast green), dark mid (25 wpm), mid at system font scale 1.5x (review follow-up R3: the 12sp Bold "x / y" text in the 24dp-high bar is the large-font clipping-risk surface; behavior itself unchanged — verbatim source).

Rendered-preview verification remains unavailable locally (no device/emulator — unchanged since P0).

### 10.7 Validation (exact)

1. Focused (after code complete): `./gw.sh . :app:testDebugUnitTest --no-parallel --max-workers=1 --rerun --tests "com.alexpo.grammermate.ui.RegularLessonClickUiTest" --tests "com.alexpo.grammermate.ui.SmokeClickTest"` → BUILD SUCCESSFUL in 26s; XML: RegularLessonClickUiTest **10/10** (incl. the `"Incorrect"` result-display and `"1 / 2"` progress assertions — both through the extracted path), SmokeClickTest **1/1**. Suite selection: grep over `app/src/test` — beyond the two suites run, `BossBattleClickUiTest` also asserts progress text (`"1 / 10"`, `"2 / 10"`, `"3 / 10"` at `:143,:158,:170,:243`; the boss branch renders `SessionProgressIndicator` via `TrainingScreen.kt:359–365`); it was not run focused but is inside the full gate and passed 2/2 (reviewer-caught coverage fact — the original "no other suite" claim here was wrong). SubmitAnswerTest is a generic harness smoke, not training; it was not needed and not run focused — it is inside the full gate.
2. Phase gate: `:app:assembleDebug` → BUILD SUCCESSFUL in 16s; full `:app:testDebugUnitTest --rerun` → BUILD SUCCESSFUL in 2m 15s; parsed 67 fresh `TEST-*.xml`: **546 run / 546 passed / 0 failed / 0 skipped** (= P0/P1/P2 baseline). Robolectric temp dirs cleaned post-run.
3. Unverified gates: `connectedDebugAndroidTest` (no emulator/adb — unchanged since P0); rendered previews/screenshots unavailable locally.

### 10.8 Inventory status updates + honest ownership evaluation

- Inventory statuses (recorded here; §2 remains the dated P0 snapshot): `SessionProgressIndicator` → `extracted`+`adopted` (destination corrected to feature-owned; P0's `designsystem/molecules` proposal overruled by caller evidence — 10.3). New row concept: `TrainingResultBlock` → `extracted`+`adopted` (single production caller via the `ResultBlock` adapter). `ResultBlock` (screen-local) → boundary adapter, `characterized`.
- **Forwarding-layer check (plan §5 / master prompt):** the `ResultBlock` adapter is 1 state read + 1 delegation — borderline forwarding. It is kept (rather than inlining `TrainingResultBlock(...)` at `:397`) for the P2-established symmetry: both training organisms have identically-shaped boundaries (`CardPrompt`, `ResultBlock`) that document the state source at one owned place; cost is 11 lines, and the P4 verb/daily drill modes may need per-mode interpretation here. If P4 produces no second interpretation need, inlining is the honest simplification. The SessionProgressIndicator re-home is NOT a forwarding layer: one implementation, two live call sites, zero wrappers.
- **Real gains:** both training feedback/progress surfaces are now previewable in isolation for the first time (10 new state fixtures), the result display no longer reads `TrainingUiState` directly, and the progress indicator's ownership matches its actual callers.
- **P4 candidates noted (out of scope here):** `SessionCompletionContent`/`VerbDrillCompletionContent` (`TrainingScreen.kt`) are separate result displays (completion stats) — P4 per §2; vocab/daily SRS result rows already screen-owned; `PomodoroTimerBanner` stays with the Pomodoro surface (P5).
- Wrapper ledger: none added this slice; the P2 legacy `TtsState` overload in `SharedComponents.kt` remains the only live compat wrapper (removal = P6, unchanged).

### 10.9 Compat/rollback boundary and next slice

- Compat wrappers added: **none**. Dead-file touch: one import line in `ui/TrainingCardSession.kt` (compilation only; file remains dead and untouched otherwise).
- Rollback = revert `TrainingScreen.kt` + `TrainingCardSession.kt` + the deleted `ui/components/SessionProgressIndicator.kt`, delete `ui/features/training/components/{TrainingResultBlock,SessionProgressIndicator}.kt`, revert the docs file. Self-contained on top of `cbb2f9b24`; no data/build/dependency surface.
- Recommended next: **P3b** per plan §5 — `AnswerBox` display, `UnifiedInputControlsBar`, `UnifiedNavigationRow`, word-bank/hint display. Ledger prerequisites: rows 1–3 (voice launchers), 5 (Bluetooth cleanup threaded-but-uninvoked defect), 12 (input state + bar-side auto-submit), 13 (WordInfoCache file I/O in component) must be resolved/owned before movement; §6 open questions 2/4 need integrator decisions.
