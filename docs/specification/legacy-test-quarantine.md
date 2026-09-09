# Legacy Test Quarantine (Phase 0)

**Created:** 2026-09-08, Phase 0 item 0.8 of `docs/architecture/ARCHITECTURE_REVIEW_2026-09-08.md`
**Baseline:** `:app:testLegacyDebugUnitTest` on dev @ 7413b4260 — 466 tests, 46 failures, 0 errors.
**CLOSED 2026-09-09:** all 46 quarantined tests repaired (§1 in Phase 3, §2–§3 after the external audit); the suite now runs 501 tests, 0 skipped, 0 failures. This document remains as the repair record.
**Policy (historical):** every quarantined test carried a named `@Ignore("Phase 0 quarantine — <reason> (legacy-test-quarantine.md)")`. CI was blocking from Phase 0 on, so the quarantine was the only sanctioned way to be red-free.

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

## 3. Parser / normalizer drift — REPAIRED 2026-09-09

All 11 quarantined parser tests repaired and un-@Ignore'd. One real spec
violation fixed in code (the rest were stale fixtures):

- **Normalizer stripped apostrophes** — the spec is explicit («апостроф внутри
  слова сохраняется», don't ≠ dont). `normalize()` now keeps apostrophes,
  normalizing typographic variants (’ ‘ ´ `) to ASCII `'`. The voice variant
  (`normalizeForVoice`) is unchanged — voice input can never produce them.
- CsvParserTest: a separator-less line IS reported (MalformedLine → isPartial)
  per the design pinned by `parseLesson_malformedLine_returnsPartial`; the
  test's `isSuccess` expectation was stale.
- VerbDrillCsvParserTest: fixture headers used commas; the real drill CSV
  format (verified in bundled packs) is semicolon-delimited `RU;IT;Verb;Tense;
  Group;Rank`.
- MultilingualStoryParser: unmarked text now uses the caller's
  `defaultLanguageId` verbatim — auto-detection had made the parameter
  meaningless (it only applied to letter-less text).
- WordScriptToMarkupTest: round-trip now pins 12 segments (the sentence's RU
  half is part of the markup, per the passing exact-markup test).
- DeckPlayerTest: assert the cursor BEFORE `stop()` — stop is the documented
  full-reset transport (`stop_resetsToStart`).
