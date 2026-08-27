# Test Plan — GrammarMate

Generated: 2026-05-18
Status: ACTIVE
Related: TASK-073, legacy-test-plan.md

---

## Existing Coverage

| Test File | Tests | Lines | Status |
|-----------|-------|-------|--------|
| SessionRunnerTest.kt | ~85 | 1,338 | EXTENSIVE |
| DailyPracticeCoordinatorTest.kt | ~75 | 1,737 | EXTENSIVE |
| CardProviderTest.kt | ~40 | 1,271 | EXTENSIVE |
| ProgressTrackerTest.kt | ~40 | 1,422 | EXTENSIVE |
| AnswerValidatorTest.kt | ~15 | 493 | GOOD |
| FlowerCalculatorTest.kt | ~12 | 362 | GOOD |
| ProgressStoreTest.kt | ~12 | 469 | GOOD |
| StreakStoreTest.kt | ~10 | 305 | GOOD |
| MasteryStoreTest.kt | ~10 | 377 | GOOD |
| NormalizerTest.kt | ~10 | 304 | GOOD |
| SpacedRepetitionConfigTest.kt | ~10 | 304 | GOOD |
| MixedReviewSchedulerTest.kt | ~4 | 127 | MINIMAL |
| BossBattleRunnerTest.kt | ~20 | 754 | GOOD |
| MasteryIntegrationTest.kt | ~8 | 299 | GOOD |
| ProgressIntegrationTest.kt | ~8 | 302 | GOOD |
| **DailySessionComposerTest.kt** | **0** | **0** | **MISSING** |

---

## Gap Analysis against TASK-073

### 1. SessionRunner — Card Orchestration (CRITICAL)

| Test Case | Status | Existing Coverage |
|-----------|--------|-------------------|
| correct answer ACTIVE → advance to next card | COVERED | `submitAnswer_correctNormalMidCard_advancesToNextCard` |
| correct answer PAUSED → resume + advance | COVERED | `onSubmit correct after pause and typed input resumes and returns Correct` |
| correct answer HINT_SHOWN → resume + advance | COVERED (семантика v2) | `onSubmit correct while hint shown without typing stays Wrong` — resume ТОЛЬКО через onInputChanged (typing); voice-путь остаётся Wrong до смены карты (фикс M-7 аудита) |
| wrong answer ACTIVE → stay, increment attempts | COVERED | `submitAnswer_wrongAnswer_incrementsIncorrectAttempts` |
| wrong × 3 → show hint, HINT_SHOWN state | COVERED | `submitAnswer_wrongAnswerAtHintThreshold_showsHint` |
| last card + correct → subLessonFinishedToken++ | COVERED | `submitAnswer_correctNormalLastCard_signalsSubLessonComplete` |
| navigateNext → currentIndex+1, PAUSED | COVERED | `nextCard_midSession_advancesIndex` |
| navigatePrev → currentIndex-1, PAUSED | COVERED | `prevCard_midSession_decrementsIndex` |
| navigateNext on last card → stays | COVERED | `nextCard_lastIndex_clampsToLast` |
| navigatePrev on first card → stays | COVERED | `prevCard_atFirstIndex_staysAtZero` |
| VOICE mode → auto-trigger recognition | COVERED | `nextCard_voiceMode_triggersVoiceToken` |
| KEYBOARD mode → no auto-trigger | COVERED | `nextCard_keyboardMode_doesNotTriggerVoiceToken` |
| startCardSession(cards, DAILY_TRANSLATE) | COVERED | `startSession_normalMode_emitsBuildSessionCards` |
| exitCardSession → resets state | COVERED | `finishSession_*` tests |

**GAP: 0** — закрыто 2026-08-27 (+bonus: VOICE-retry авто-ретриггер тест).

### 2. DailyPracticeCoordinator — Cursor & Session

| Test Case | Status | Existing Coverage |
|-----------|--------|-------------------|
| cancelDailySession all practiced → returns count | COVERED | `cancelDailySession_finishedAllSentenceAndVerbCards_returnsSentenceCount` |
| cancelDailySession partial → returns null | COVERED | `cancelDailySession_partialTranslate_returnsNull` |
| cancelDailySession WORD_BANK only → null | COVERED | `cancelDailySession_finishedButIncomplete_returnsNull` |
| advanceCursor(10) → offset += 10 | COVERED | cursor tests exist |
| advanceCursor past lesson boundary → lessonIndex++ | COVERED | cursor boundary tests |
| advanceCursor past last lesson → wrap to 0 | COVERED | wrap tests |
| startDailyPractice first session → stores cardIds | COVERED | `startDailySession_callsOnStoreFirstSessionCardIds` |
| startDailyPractice NOT first → preserves cardIds | COVERED | repeat tests |
| repeatDailyPractice → uses stored cardIds | COVERED | `repeatDailyPractice_withCachedTasks_reusesTasks` |
| hasResumableDailySession → true/false | COVERED | `hasResumableDailySession_*` |
| resetState() preserves cursor | COVERED | `resetState_preservesCursor` |
| resetAllDailyState() wipes cursor | **MISSING** | New method, no test |
| recordDailyCardPracticed TRANSLATE/VERBS | COVERED | `recordDailyCardPracticed_*` |
| onBlockComplete advances blockIndex | COVERED | `advanceToNextBlock_*` |
| onBlockComplete on last block → endSession | COVERED | `advanceToNextBlock_fromVerbs_atLastBlock_endsSession` |
| endSession records streak per practice type | COVERED | `endSession_*` |

**GAP: 1 test needed** — `resetAllDailyState()` wipes cursor.

### 3. DailySessionComposer — Block Building (NO TESTS EXIST)

| Test Case | Status | Priority |
|-----------|--------|----------|
| TRANSLATE block: builds from cursor offset | **MISSING** | HIGH |
| TRANSLATE block: does NOT cross lesson boundary | **MISSING** | HIGH |
| TRANSLATE block: sessionSize limits output | **MISSING** | HIGH |
| TRANSLATE block: offset >= lesson size → empty | **MISSING** | HIGH |
| TRANSLATE block: cards in sequential order | **MISSING** | HIGH |
| VOCAB block: selects due words first (overdue) | **MISSING** | HIGH |
| VOCAB block: then new words by rank | **MISSING** | MEDIUM |
| VOCAB block: sessionSize limits output | **MISSING** | MEDIUM |
| VOCAB block: excludes numbers | **MISSING** | MEDIUM |
| VERBS block: filters by active tenses | **MISSING** | HIGH |
| VERBS block: excludes previously shown cards | **MISSING** | HIGH |
| VERBS block: cycles when all shown | **MISSING** | MEDIUM |
| VERBS block: weakness-first ordering | **MISSING** | MEDIUM |
| buildBlocks returns 3 blocks in order | **MISSING** | HIGH |
| buildRepeatBlocks uses stored card IDs | **MISSING** | MEDIUM |
| sessionSize=3 produces 3 cards per block | **MISSING** | HIGH |

**GAP: 16 tests needed** — entirely new test file.

### 4. AnswerValidator

| Test Case | Status | Existing Coverage |
|-----------|--------|-------------------|
| exact match → correct | COVERED | |
| case-insensitive → correct | COVERED | |
| synonym match → correct | COVERED | |
| partial match → wrong | COVERED | |
| empty input → wrong | COVERED | |
| testMode → accepts any non-empty | COVERED | |

**GAP: 0 tests needed** — fully covered.

### 5. CardProvider — Sub-lesson Scheduling

| Test Case | Status | Existing Coverage |
|-----------|--------|-------------------|
| NEW_ONLY sub-lessons: only new cards | COVERED | |
| MIXED sub-lessons: new + review | COVERED | |
| subLessonSize controls cards | COVERED | |
| cycles through all before repeat | COVERED | |

**GAP: 0 tests needed** — fully covered.

### 6. FlowerCalculator

| Test Case | Status | Existing Coverage |
|-----------|--------|-------------------|
| mastery=0 → LOCKED | COVERED | |
| mastery>0 → SEED | COVERED | |
| mastery at threshold → SPROUT | COVERED | |
| mastery at bloom → BLOOM | COVERED | |
| decay → WILTING → WILTED → GONE | COVERED | |

**GAP: 0 tests needed** — fully covered.

### 7. Progress Persistence

| Test Case | Status | Existing Coverage |
|-----------|--------|-------------------|
| saveProgress persists to file | COVERED | ProgressStoreTest |
| loadProgress restores state | COVERED | |
| cursor survives restart | COVERED | ProgressIntegrationTest |
| firstSessionDate survives restart | COVERED | |

**GAP: 0 tests needed** — fully covered.

### 8. Settings

| Test Case | Status | Priority |
|-----------|--------|----------|
| setSessionSize updates all consumers | **MISSING** | MEDIUM |
| setSessionSize persists to config | **MISSING** | MEDIUM |
| setSessionSize clamps to valid range | **MISSING** | MEDIUM |

**GAP: 3 tests needed** — no settings tests exist.

### 9. TTS/ASR

| Test Case | Status | Priority |
|-----------|--------|----------|
| TtsEngine.initialize serializes via mutex | **MISSING** | LOW |
| TtsEngine.doRelease frees native resources | **MISSING** | LOW |
| AudioCoordinator ttsMutex prevents concurrent access | **MISSING** | LOW |

**GAP: 3 tests needed** — requires native mocking, LOW priority.

### 10. Per-Screen User Journeys

| Test Case | Status | Priority |
|-----------|--------|----------|
| Full daily session: TRANSLATE→VOCAB→VERBS→completion | COVERED | `fullLifecycle_traverseAllBlocks_cancelReturnsSentenceCount` |
| Exit mid-session → cursor does NOT advance | COVERED | `fullLifecycle_cancelEarly_noCursorAdvancement` |
| Wrong × 3 → hint → correct → advance | COVERED | `fullFlow_threeWrong_showsHintThenUserCanAdvance` |
| Navigate back/forward → PAUSED → correct → advance | **MISSING** | MEDIUM |
| Verb drill: VOICE correct → advance | **MISSING** | LOW (VerbDrillViewModel) |
| Vocab drill: rate cards → session done | COVERED | `rateVocabCard_*` tests |

**GAP: 2 tests needed.**

---

## Implementation Priority

### Priority 1: DailySessionComposerTest (16 new tests)
Entirely untested. Critical for the VERBS loop fix — ensures block building logic is correct.

### Priority 2: SessionRunner PAUSED/HINT_SHOWN+correct (2 new tests)
Tests the recent fix for non-ACTIVE state advancement.

### Priority 3: Settings tests (3 new tests)
setSessionSize validation.

### Priority 4: resetAllDailyState test (1 new test)

### Priority 5: User journey + TTS (5 new tests)
Lower priority, requires more mocking.

---

## Total Gap: ~27 tests across 4 files

| File | New Tests | Action |
|------|-----------|--------|
| DailySessionComposerTest.kt (NEW) | 16 | Create from scratch |
| SessionRunnerTest.kt (EXPAND) | 2 | Add PAUSED/HINT_SHOWN tests |
| DailyPracticeCoordinatorTest.kt (EXPAND) | 1 | Add resetAllDailyState test |
| Settings / integration tests | 8 | Settings validation + journeys |

**Existing coverage: ~300+ tests. Adding ~27 tests fills all gaps.**
