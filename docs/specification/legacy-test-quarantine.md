# Legacy Test Quarantine (Phase 0)

**Created:** 2026-09-08, Phase 0 item 0.8 of `docs/architecture/ARCHITECTURE_REVIEW_2026-09-08.md`
**Baseline:** `:app:testLegacyDebugUnitTest` on dev @ 7413b4260 — 466 tests, 46 failures, 0 errors.
**Policy:** every quarantined test carries a named `@Ignore("Phase 0 quarantine — <reason> (legacy-test-quarantine.md)")`. This file is the tracking list; when a test is fixed, remove its `@Ignore` and delete its row here. CI is blocking from Phase 0 on, so the quarantine is the only sanctioned way to be red-free.

These failures predate the 2026-09-08 refactor: they are inherited breakage from earlier runtime restoration, not regressions of Phase 0. Nothing below may be deleted — each row is a regression net awaiting repair.

## 1. Chapter progress & completion rule — reopen in Phase 3 (items 3.1/3.3/3.5)

The completion rule stamps `completedAtMs` when `uniqueCardShows >= min(effectiveCardCount, 150)`; these tests assert lessons counted as completed and get 0. Phase 3 unifies the rule and these tests must be repaired (not re-recorded) as part of it.

| Test class | Failing tests | Symptom |
|---|---|---|
| `feature.progress.ChapterProgressCalculatorTest` | `calculateChapterProgress is independent across chapters`; `calculateChapterProgress counts completed lessons correctly`; `calculateChapterProgress_maintainsStartedGeqCompleted_invariant` | expected 1-2 completed, got 0 |
| `feature.progress.ChapterProgressUpdateTest` | `` `BUG REPRO - lesson with uniqueShows but no completedAtMs not counted as completed` `` | documents the live bug: completed lessons report 0 because `completedAtMs` is never stamped |
| `ui.TrainingViewModelChapterIntegrationTest` | `test chapter progress calculation with lesson mastery states`; `test chapter progress with all lessons completed` | expected 1-2, got 0 |

## 2. Compose click-UI journeys — reopen in Phase 2 (navigation/effects rework)

Two failure shapes: (a) `Expected at most 1 node but found 2` — the same text matches both static Text and EditableText nodes, a harness matcher drift; (b) `The component is not displayed!` — UI behavior moved since the tests were recorded. Phase 2 re-anchors navigation and adds a fresh HOME→training→back journey test; these suites are repaired against the post-Phase-2 behaviour.

| Test class | Failing / total | Shape |
|---|---|---|
| `ui.PauseCascadeClickUiTest` | 8 / 8 (class quarantined) | (a) duplicate-node matcher |
| `ui.RegularLessonClickUiTest` | 7 / 10 | (a)+(b) |
| `ui.VerbDrillScreenStartFreshResumeTest` | 7 / 7 (class quarantined) | session persistence lifecycle + (b) |
| `ui.BossBattleClickUiTest` | 2 / 2 (class quarantined) | (b) |
| `ui.DailyPracticeClickUiTest` | 2 / 12 | (a) + missing `show_answer_button` node |
| `ui.PomodoroBannerClickUiTest` | 2 / 6 | (b) summary not displayed |
| `ui.PomodoroClickUiTest` | 1 / 10 | cancel should suppress summary |

## 3. Parser / normalizer drift — not owned by a plan phase; triage separately

Expectations recorded against an older parser/normalizer; code drifted (or tests assert behaviour that was deliberately changed). Requires per-case product decision (e.g. apostrophe handling in user answers: tests expect `it's` preserved, normalizer strips `'`).

| Test class | Failing tests | Symptom |
|---|---|---|
| `data.NormalizerTest` | `normalize_realUserAnswer_matchesExpected`; `normalize_timeTwelveColon30_becomesTwelve`; `normalize_multipleTransformations_appliedCorrectly` | apostrophes stripped: expected `it's`, got `its` |
| `data.CsvParserTest` | `parseLesson_lineWithoutSeparator_ignored` | assertion on malformed-line handling |
| `data.VerbDrillCsvParserTest` | `parse_malformedCsv_returnsPartial`; `parseValidVerbDrill`; `parse_specialCharacters_handlesCorrectly`; `parseLineNumbers_includedInErrors` | 4/6 of the suite — parser contract drift |
| `data.MultilingualStoryParserPauseTest` | `pausesInterleavedWithUnmarkedText_useDefaultLanguageId` | default languageId `en` vs expected `ru` |
| `data.WordScriptToMarkupTest` | `roundTrip_parsesToExpectedItRuAlternation` | expected 10 segments, got 12 |
| `feature.backgroundvocab.DeckPlayerTest` | `nextWord_whilePlaying_relaunchesFromNewWord` | relaunch counter 0 vs 1 |
