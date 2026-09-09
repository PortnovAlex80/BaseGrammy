# Legacy Test Quarantine (Phase 0)

**Created:** 2026-09-08, Phase 0 item 0.8 of `docs/architecture/ARCHITECTURE_REVIEW_2026-09-08.md`
**Baseline:** `:app:testLegacyDebugUnitTest` on dev @ 7413b4260 — 466 tests, 46 failures, 0 errors.
**Policy:** every quarantined test carries a named `@Ignore("Phase 0 quarantine — <reason> (legacy-test-quarantine.md)")`. This file is the tracking list; when a test is fixed, remove its `@Ignore` and delete its row here. CI is blocking from Phase 0 on, so the quarantine is the only sanctioned way to be red-free.

These failures predate the 2026-09-08 refactor: they are inherited breakage from earlier runtime restoration, not regressions of Phase 0. Nothing below may be deleted — each row is a regression net awaiting repair.

## 1. Chapter progress & completion rule — REPAIRED in Phase 3 (items 3.1/3.3/3.5)

All six quarantined tests were repaired and un-@Ignore'd in Phase 3:
fixtures re-anchored to the unified rule (completion = `completedAtMs`
stamped at `uniqueCardShows >= min(effectiveCardCount, 150)`), and the BUG
REPRO was rewritten to pin the repaired chain (restore-path
`recalculateCompletionsExcludingHidden` stamps → live calculator counts).
New rule pins live in `feature/progress/ChapterProgressRulesTest.kt`.

## 2. Compose click-UI journeys — REPAIRED 2026-09-09

All 29 quarantined click-UI tests were repaired and un-@Ignore'd. The repairs
surfaced TWO real production bugs (fixed with the repairs):

1. **Duplicate prompt rendering in TrainingScreen** — the card prompt rendered
   twice (untagged header text + `card_prompt_text` card) on every non-drill
   card, which was also the root of the duplicate-node matcher failures. Drill
   modes now render the header prompt; other modes render the RU-labeled card.
2. **Premature verb-drill session discard** — a fresh `VerbDrillViewModel`
   deleted the saved resume session during startup validation because card
   availability was checked before `allCards` loaded. Validation now runs only
   when cards are loaded. Also: the fake-store test path reads injected cards
   via `loadAllCardsForPack` (with a pack-scoped language fallback) instead of
   waiting for the never-called `injectTestCards`.

Harness-level repairs (behaviour-preserving): remembered reactive state so
controlled fields/buttons enable (`mutableStateOf` without `remember` resets
every recomposition), `performScrollTo()` before below-the-fold clicks,
`performTextSelection` semantics on controlled fields, VD-51 dialog→SessionCard
re-anchoring in VerbDrill tests, BOSS screenMode + "N / M" progress format,
Pomodoro `stats` field + split value/label StatCards, and the DailyPractice
vocab flow re-pointed at its real surface (DailyPracticeScreen flip + SRS row).

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
