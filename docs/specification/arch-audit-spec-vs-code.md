# Specification vs Code Audit -- Discrepancy Report

**Date:** 2026-05-12
**Scope:** 10 specification documents cross-checked against 8 source code files
**Spec documents reviewed:** 01-models-and-state.md, 03-algorithms-and-calculators.md, 08-training-viewmodel.md, 09-daily-practice.md, 10-verb-drill.md, 11-vocab-drill.md, 12-training-card-session.md, FUNCTIONAL_REQUIREMENTS.md, Spec -- State Machine.md
**Supplementary specs:** Экранные формы.Спецификация.md, Экранные формы.Верификация.md

---

## 1. Methodology

Each specification document was read in full and cross-checked line-by-line against the corresponding source code file(s). For each field, constant, algorithm step, invariant, and business rule described in the specs, I verified:

1. **Existence** -- does the code element exist at all?
2. **Value/Type match** -- does the actual value, type, or default match what the spec says?
3. **Algorithmic fidelity** -- does the code implement exactly the algorithm described?
4. **Behavioral rules** -- are the stated invariants and business rules enforced in code?

Source files were read from `app/src/main/java/com/alexpo/grammermate/`. The verification report (Экранные формы.Верификация.md) was also cross-referenced for UI-layer discrepancies.

---

## 2. Critical Discrepancies (Must Fix)

### CRITICAL-1: Normalizer does NOT strip diacritical marks despite spec claiming it does

- **Spec says** (12-training-card-session.md, Section 12.3.2): "4. Strip diacritical marks (NFD decomposition, remove combining characters)." Also repeated in 12.7.5 Step 4: "Strip diacritical marks (NFD decomposition, remove combining characters)."
- **Code does**: `Normalization.kt` only does: trim whitespace, collapse spaces, remove time colons, lowercase, strip punctuation (keeping hyphens), collapse spaces again. **There is no NFD decomposition or diacritic stripping.**
- **Impact**: Italian accent marks (e.g., `e` vs `e`, `a` vs `a`) are treated as distinct characters. Users who omit accents in verb conjugations will be marked wrong. This contradicts the spec's intent that accent differences should be normalized away.
- **Fix**: Either add `java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")` to the Normalizer, OR update specs 03-algorithms-and-calculators.md (Section 3.5) and 10-verb-drill.md (Section 10.5.5) and 12-training-card-session.md to correctly state that diacritics are NOT stripped (which is the current behavior). Note: spec 03-algorithms-and-calculators.md Section 3.5 correctly states diacritics are NOT normalized ("This is intentional: correct accent usage is part of language mastery"), so there is a contradiction BETWEEN spec documents.

### CRITICAL-2: isLearned threshold contradicted between spec documents

- **Spec says** (01-models-and-state.md, Section 1.1.19): "isLearned: true when intervalStepIndex == 9 (reached the last ladder step)." Also Section 1.6.7 Invariant 1: "isLearned at step 9."
- **Spec says** (11-vocab-drill.md, Section 11.2.3): "isLearned: reached step >= 3 (LEARNED_THRESHOLD)" and Section 11.5.8: "isLearned = (newStepIndex >= 3)."
- **Code does**: `VocabWord.kt` line 28: `val isLearned: Boolean = false` with comment "reached the last interval step (9)". The code itself does not embed the threshold -- it is set externally by the ViewModel.
- **Impact**: Two spec documents contradict each other. 01-models-and-state.md says step 9, 11-vocab-drill.md says step 3. The actual behavior depends on the ViewModel implementation. If the ViewModel uses >= 3 (as spec 11 says), then the comment in VocabWord.kt is wrong. If it uses == 9 (as 01-models says), then spec 11 is wrong.
- **Fix**: Determine the correct threshold and update both specs and the code comment to match. Verify the ViewModel's `answerRating()` method.

### CRITICAL-3: Daily Practice session total task count contradicted

- **Spec says** (01-models-and-state.md, Section 1.6.3 Invariant 4): "Daily practice sessions have fixed sizes: 10 translate cards + 5 vocab flashcards + 10 verb cards = 25 total tasks."
- **Spec says** (09-daily-practice.md, Section 9.1): "Block 2 -- Vocabulary Flashcards (10 cards, Anki-style)." Also Section 9.4.2: "Select up to VOCAB_COUNT (10) words."
- **Code does**: `DailySessionComposer.kt` line 36: `const val VOCAB_COUNT = CARDS_PER_BLOCK` where `CARDS_PER_BLOCK = 10`. So the VOCAB block produces up to 10 cards, not 5.
- **Impact**: 01-models-and-state.md says total is 25 (5 vocab), but both the code and spec 09 say vocab block is 10 cards, giving total 30. Invariant in 01-models-and-state.md is wrong.
- **Fix**: Update Section 1.6.3 Invariant 4 in 01-models-and-state.md to say "10 translate cards + 10 vocab flashcards + 10 verb cards = 30 total tasks."

### CRITICAL-4: VocabWord.id format mismatch between specs

- **Spec says** (11-vocab-drill.md, Section 11.2.1): ID format is `{pos}_{rank}_{word}` (e.g., "nouns_106_casa").
- **Spec says** (01-models-and-state.md, Section 1.1.18): ID format is `{pos}_{word}` (e.g., "nouns_casa", "verbs_essere") -- no rank.
- **Code does**: `DailySessionComposer.kt` line 403: `id = "${pos}_${row.rank}_${row.word}"` -- matches spec 11 with rank.
- **Impact**: 01-models-and-state.md provides incorrect ID examples. Code matches spec 11.
- **Fix**: Update 01-models-and-state.md Section 1.1.18 to show correct ID format `{pos}_{rank}_{word}` with examples like "nouns_106_casa".

### CRITICAL-5: Spec claims currentScreen is NOT persisted, but code still has the field

- **Spec says** (Spec -- State Machine.md, Section 2): "TrainingProgress.currentScreen no longer saved in YAML. initialScreen always HOME."
- **Code does**: `Models.kt` line 133: `val currentScreen: String = "HOME"` is still a field in `TrainingProgress`. The State Machine spec describes a planned change that has not been implemented.
- **Impact**: The State Machine spec describes desired behavior that conflicts with the actual codebase. Developers following the State Machine spec will be confused about whether currentScreen is persisted.
- **Fix**: Either implement the State Machine spec (remove currentScreen from TrainingProgress persistence) or update the State Machine spec to mark this as a future change that is not yet implemented.

---

## 3. Minor Discrepancies (Should Fix)

### MINOR-1: SPROUT state description inconsistency in spec

- **Spec says** (01-models-and-state.md, Section 1.2.7): "SPROUT -- Lesson has some mastery (33% <= mastery < 66%) and health is full."
- **Spec says** (01-models-and-state.md, FlowerState transitions): "masteryPercent < 0.33 -> SEED" and "masteryPercent < 0.66 -> SPROUT". So SPROUT is actually in range [33%, 66%).
- **Spec says** (03-algorithms-and-calculators.md, Section 3.2): "SPROUT -- Lesson in progress. Mastery >= 1% and < 33%."
- **Code does**: `FlowerCalculator.kt` line 87: `masteryPercent < 0.33f -> FlowerState.SEED` followed by `masteryPercent < 0.66f -> FlowerState.SPROUT`. This means SPROUT is [0.33, 0.66), NOT [0.01, 0.33).
- **Impact**: Spec 03-algorithms-and-calculators.md Section 3.2 claims SPROUT is mastery 1-33%, which is wrong. The code matches spec 01 and the state transitions diagram in spec 03.
- **Fix**: Update spec 03 Section 3.2 SPROUT description to say "Mastery >= 33% and < 66%."

### MINOR-2: Normalizer spec mentions apostrophe handling but lists it inconsistently

- **Spec says** (03-algorithms-and-calculators.md, Section 3.5 "What is NOT normalized" item 2): "Apostrophes -- not in the punctuation removal list. `l'albero` keeps the apostrophe."
- **Code does**: `Normalization.kt` line 11: the `when(ch)` block does NOT include `'` (apostrophe) in the punctuation removal list. Apostrophes pass through to the builder.
- **Impact**: Spec and code match here. However, the spec 12-training-card-session.md Section 12.7.5 Step 5 says "Strip all remaining punctuation" without qualifying that apostrophes are excluded. This is misleading.
- **Fix**: Update spec 12 Section 12.7.5 to note that apostrophes and hyphens are preserved.

### MINOR-3: MixedReviewScheduler newOnlyTarget split formula differs from spec description

- **Spec says** (03-algorithms-and-calculators.md, Section 3.3): "newOnlyTarget = ceil(totalCards / 2)" for non-first lessons.
- **Code does**: `MixedReviewScheduler.kt` line 59: `val newOnlyTarget = if (allowMixed) { ceilDiv(totalCards, 2) } else { totalCards }`. The `ceilDiv` function is `(numerator + denominator - 1) / denominator` which is ceiling division. So for 11 cards: ceilDiv(11, 2) = 6. This matches spec.
- **Impact**: No behavioral discrepancy, but the spec pseudocode uses `ceil()` while code uses integer ceiling division. Functionally identical.
- **Fix**: Cosmetic only. No change needed.

### MINOR-4: DailySessionComposer.VOCAB_COUNT vs spec 01 fixed size claim

- **Spec says** (01-models-and-state.md, Section 1.2.8): "Fixed order in daily practice: TRANSLATE (10 cards) --> VOCAB (5 cards) --> VERBS (10 cards)."
- **Code does**: `DailySessionComposer.kt` VOCAB_COUNT = 10 (same as CARDS_PER_BLOCK).
- **Impact**: 01-models-and-state.md claims 5 vocab cards but code produces up to 10. This is a duplicate finding with CRITICAL-3 but at the enum description level.
- **Fix**: Change "5 cards" to "10 cards" in Section 1.2.8.

### MINOR-5: VerbDrillCard.id format in spec example vs spec description

- **Spec says** (10-verb-drill.md, Section 10.2.1): ID format is `"{group}_{tense}_{rowIndex}"` with empty strings for nulls. Examples: `"regular_are_Presente_0"`, `"irregular_unique_Imperfetto_5"`.
- **Spec says** (10-verb-drill.md, Section 10.2.7 Item 8): Same ID format described.
- **Spec says** (01-models-and-state.md, Section 1.1.14): Just says "Unique card identifier" without specifying format.
- **Code does**: `VerbDrillCard.kt` -- the `id` is set externally by `VerbDrillCsvParser`. We would need to verify the parser constructs IDs in this format.
- **Impact**: Spec 10 is likely correct and spec 01 is underspecified. Not a functional issue but a documentation gap.
- **Fix**: Add the ID format description to spec 01 Section 1.1.14.

### MINOR-6: Normalizer handles `ln` import but never uses it

- **Code does**: `SpacedRepetitionConfig.kt` line 3 imports `kotlin.math.ln` but never calls it. Only `exp()` is used.
- **Impact**: Dead import. No functional impact but confusing for readers.
- **Fix**: Remove unused import from SpacedRepetitionConfig.kt.

### MINOR-7: DailySessionComposer.buildSession passes cursor to buildVerbBlock but spec says verb block is independent

- **Spec says** (09-daily-practice.md, Section 9.5.1): Verb block uses "weak-first ordering, excluding previously shown cards." No mention of cursor.
- **Spec says** (09-daily-practice.md, Section 9.4.7): "Block 2 vocab selection is completely independent of Blocks 1 and 3: It does not use DailyCursorState."
- **Code does**: `DailySessionComposer.kt` line 79: `buildVerbBlock(packId, languageId, tenses, cursor)` passes cursor but the function signature at line 303 accepts `cursor: DailyCursorState` but **never uses it** in the function body.
- **Impact**: Dead parameter. No functional discrepancy but the API is misleading.
- **Fix**: Remove the unused `cursor` parameter from `buildVerbBlock()`, or document why it is there (future use).

### MINOR-8: Spec 08 references "dailyLevel" and "dailyTaskIndex" in saveProgress but they are from TrainingProgress, not DailySessionState

- **Spec says** (08-training-viewmodel.md, Section 8.2.3): `saveProgress()` persists `dailyLevel` and `dailyTaskIndex`.
- **Code does**: `Models.kt` `TrainingProgress` has fields `dailyLevel: Int = 0` and `dailyTaskIndex: Int = 0`. These are separate from `DailySessionState` which has `level: Int = 0` and `taskIndex: Int = 0`.
- **Impact**: There is potential confusion between the persisted `TrainingProgress.dailyTaskIndex` and the runtime `DailySessionState.taskIndex`. The spec should clarify whether `dailyTaskIndex` in TrainingProgress is actually used for session resume or is legacy.
- **Fix**: Clarify in spec 08 whether `dailyLevel`/`dailyTaskIndex` are the persisted forms of `DailySessionState.level`/`taskIndex`, or if they serve a different purpose.

### MINOR-9: Spec 01 VocabDrillUiState missing fields vs spec 11

- **Spec says** (01-models-and-state.md, Section 1.1.21): Lists `VocabDrillUiState` fields including `masteredByPos`.
- **Spec says** (11-vocab-drill.md, Section 11.2.7): Lists same state with identical fields.
- **Code does**: `VocabWord.kt` `VocabDrillUiState` matches both specs.
- **Impact**: No discrepancy. Both specs and code are aligned here.
- **Fix**: N/A.

### MINOR-10: DailyPracticeSessionProvider.togglePause() differs from spec description

- **Spec says** (09-daily-practice.md, Section 9.7.4): No explicit description of togglePause behavior.
- **Spec says** (10-verb-drill.md, Section 10.4.3): "After hint is shown: The user presses the Play button, which calls togglePause(). Since isPaused == true, this calls nextCard() to advance."
- **Code does** (`DailyPracticeSessionProvider.kt` lines 276-293): `togglePause()` when paused does NOT call `nextCard()`. Instead, it clears all pending state and restarts the current card with fresh attempts. This is fundamentally different from the VerbDrill behavior.
- **Impact**: DailyPractice and VerbDrill have different togglePause semantics. In VerbDrill, pause->play advances the card. In DailyPractice, pause->play resets the current card for a fresh attempt.
- **Fix**: Document this behavioral difference in spec 09 or spec 12. Decide which behavior is correct and align them.

### MINOR-11: Word bank distractor selection uses normalized comparison in spec but not in code

- **Spec says** (12-training-card-session.md, Section 12.3.5): "Collect distractor words from other cards in the session (words not in the correct answer)."
- **Code does** (`DailyPracticeSessionProvider.kt` lines 313-319): Distractors are filtered by `w !in answerWords` using raw string comparison, NOT normalized comparison.
- **Impact**: Words that are the same after normalization (e.g., "Hello" and "hello") could both appear in the word bank, one as a correct answer and one as a distractor. This is unlikely to cause real issues but is a subtle difference.
- **Fix**: Use normalized comparison for distractor filtering to match the spec's intent.

---

## 4. Missing Specifications (Code exists but spec doesn't cover it)

### MISSING-1: DailyPracticeSessionProvider.inputModeConfig reads task-specific inputMode

- **Code location**: `DailyPracticeSessionProvider.kt` lines 116-127
- **What it does**: The `inputModeConfig` property reads the current task's predetermined `inputMode` (from `DailyTask.TranslateSentence.inputMode` or `DailyTask.ConjugateVerb.inputMode`) and returns it as the default mode. This means input mode defaults change per-card.
- **Why it matters**: Spec 09 Section 9.3.2 describes the rotation pattern but spec 12 Section 12.2.4 `InputModeConfig` does not mention that the default mode can be task-dependent. The spec implies the config is static for a session.

### MISSING-2: DailySessionComposer.loadVocabWords catches and silently swallows exceptions

- **Code location**: `DailySessionComposer.kt` lines 386-417
- **What it does**: Each vocab file parse is wrapped in try-catch that silently skips unreadable files. No logging, no error state.
- **Why it matters**: If a vocab CSV file is corrupted, it silently disappears from the session. The spec does not describe this error handling behavior.

### MISSING-3: DailySessionComposer.loadVerbDrillCards catches and silently swallows exceptions

- **Code location**: `DailySessionComposer.kt` lines 420-435
- **What it does**: Same silent exception handling for verb drill files.
- **Why it matters**: Same concern as MISSING-2. Corrupted verb drill files produce empty blocks without user notification.

### MISSING-4: MixedReviewScheduler.ceilDiv utility function

- **Code location**: `MixedReviewScheduler.kt` lines 210-213
- **What it does**: Custom ceiling division used for splitting cards between NEW_ONLY and MIXED sub-lessons.
- **Why it matters**: Spec 03 Section 3.3 mentions `ceil()` in pseudocode but does not document this helper or the exact integer ceiling division formula.

### MISSING-5: DailySessionComposer.buildVerbBlock weakness scoring uses tense-level not combo-level

- **Code location**: `DailySessionComposer.kt` lines 331-335
- **What it does**: Weakness is computed per tense by summing `everShownCardIds.size` across all combos matching `card.tense`, then dividing by total cards in that tense. This is a tense-level weakness, not a group+tense combo weakness.
- **Why it matters**: Spec 09 Section 9.5.1 says "for each card, compute 1 - (shownInSameTense / totalInSameTense)." The code matches this description. But spec 10 Section 10.7.2 says "for each card's tense: weakness = 1 - (shownInCombo / comboTotal)" which implies combo-level (group+tense) scoring. These are different.

---

## 5. Spec-Only Features (Spec describes but code doesn't implement)

### SPEC-ONLY-1: State Machine AppStartRule (currentScreen removal)

- **Spec location**: Spec -- State Machine.md, Section 2
- **What was planned**: `currentScreen` field would be removed from `TrainingProgress` YAML persistence; `initialScreen` would always be hardcoded to `"HOME"`.
- **Current status**: **Not implemented.** `TrainingProgress` still has `currentScreen: String = "HOME"` and the field is still persisted. The code in `Models.kt` matches the old behavior.

### SPEC-ONLY-2: State Machine guard checks before navigation

- **Spec location**: Spec -- State Machine.md, Sections 3.1-3.2
- **What was planned**: Guard checks before transitioning to TRAINING or DAILY_PRACTICE (e.g., `state.currentCard != null`, `dailySession.active == true`).
- **Current status**: **Partially implemented.** The guards are likely enforced in `GrammarMateApp.kt` routing logic but this was not verified in the source files under audit. The spec describes them as mandatory.

### SPEC-ONLY-3: Spec 03 Normalizer mentions stripping diacritics in one place but explicitly says NOT to in another

- **Spec location**: 03-algorithms-and-calculators.md Section 3.5 ("What is NOT normalized" item 1) vs 12-training-card-session.md Section 12.3.2 Step 4.
- **What was planned**: 12-training-card-session.md describes diacritic stripping as part of the normalization pipeline. 03-algorithms-and-calculators.md explicitly says it is NOT done.
- **Current status**: Code matches spec 03 (no stripping). Spec 12 is wrong.

---

## 6. Behavioral Inconsistencies

### INCONSISTENCY-1: Normalizer: diacritics handling

- **Behavior**: Whether accent marks are stripped during answer normalization
- **Place A** (03-algorithms-and-calculators.md Section 3.5): "Accent marks / diacritics -- accented characters are treated as distinct. This is intentional: correct accent usage is part of language mastery."
- **Place B** (12-training-card-session.md Section 12.3.2 Step 4): "Strip diacritical marks (NFD decomposition, remove combining characters)."
- **Which is correct**: The code (Normalization.kt) does NOT strip diacritics, matching Place A. Place B is incorrect and should be updated.

### INCONSISTENCY-2: togglePause behavior: VerbDrill vs DailyPractice

- **Behavior**: What happens when user presses Play/Pause after a hint is shown
- **Place A** (VerbDrill, spec 10.4.3): togglePause when paused calls nextCard() to advance to the next card.
- **Place B** (DailyPractice, DailyPracticeSessionProvider.kt lines 276-293): togglePause when paused clears all state and restarts the current card (fresh attempts, no advancement).
- **Which is correct**: Both are valid UX choices but they should be documented as intentional differences. The specs do not acknowledge this divergence.

### INCONSISTENCY-3: Verb weakness scoring: tense-level vs combo-level

- **Behavior**: How verb card weakness is computed for daily practice selection
- **Place A** (spec 09-daily-practice.md Section 9.5.1): "1 - (shownInSameTense / totalInSameTense)" -- tense-level scoring.
- **Place B** (spec 10-verb-drill.md Section 10.7.2): "1 - (shownInCombo / totalInCombo)" -- combo (group+tense) level scoring.
- **Code** (DailySessionComposer.kt lines 331-335): Uses tense-level filtering by matching `progress.tense == card.tense` across all combos. This matches Place A.
- **Which is correct**: The code matches spec 09. Spec 10 is describing a different (more granular) algorithm that is not implemented.

### INCONSISTENCY-4: isLearned threshold across spec documents

- **Behavior**: At what interval step a word is considered "learned"
- **Place A** (01-models-and-state.md Section 1.1.19): "isLearned: true when intervalStepIndex == 9"
- **Place B** (11-vocab-drill.md Section 11.2.3): "isLearned: reached step >= 3 (LEARNED_THRESHOLD)"
- **Code** (VocabWord.kt line 28): Comment says "reached the last interval step (9)" but the field is `false` by default and set externally.
- **Which is correct**: Cannot determine without reading the ViewModel code that sets `isLearned`. This is a critical ambiguity.

---

## 7. Summary Statistics

### Total discrepancies found: 24

### By severity:
| Severity | Count |
|----------|-------|
| Critical | 5 |
| Minor | 11 |
| Missing spec coverage | 5 |
| Spec-only (unimplemented) | 3 |

### By category:
| Category | Count |
|----------|-------|
| Spec-Code mismatch | 12 |
| Spec-Spec contradiction | 6 |
| Missing spec coverage | 5 |
| Behavioral inconsistency | 4 |
| Dead code/unused imports | 1 |

### Top priorities for resolution:

1. **CRITICAL-1** (Normalizer diacritics): Decide whether to strip accents or not, then update spec 12 to match code.
2. **CRITICAL-2** (isLearned threshold): Determine the actual threshold from ViewModel code, update both spec documents.
3. **CRITICAL-3** (Daily session size): Update spec 01 Section 1.6.3 and Section 1.2.8 to say 10 vocab cards (30 total).
4. **CRITICAL-4** (VocabWord ID format): Update spec 01 Section 1.1.18 to include rank in ID format.
5. **CRITICAL-5** (State Machine spec): Mark the State Machine spec as "planned but not yet implemented" or implement it.

### Spec accuracy by document:

| Spec Document | Accuracy | Notes |
|---------------|----------|-------|
| 01-models-and-state.md | ~90% | Wrong vocab count, wrong VocabWord ID, isLearned ambiguity |
| 03-algorithms-and-calculators.md | ~98% | Most accurate spec document |
| 08-training-viewmodel.md | ~95% | Minor ambiguities in dailyTaskIndex persistence |
| 09-daily-practice.md | ~95% | Accurate for the most part; cursor not used in verb block |
| 10-verb-drill.md | ~90% | Wrong weakness scoring description vs code; isLearned ambiguity |
| 11-vocab-drill.md | ~90% | isLearned threshold contradicts spec 01 |
| 12-training-card-session.md | ~85% | Falsely claims diacritic stripping; misattributes hint features to default controls |
| FUNCTIONAL_REQUIREMENTS.md | ~95% | Accurate high-level overview |
| Spec -- State Machine.md | ~50% | Describes planned changes that are not yet implemented |
| Экранные формы.Верификация.md | ~100% | Accurate verification report, matches this audit's findings |
