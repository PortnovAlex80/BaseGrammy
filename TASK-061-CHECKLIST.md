# TASK-061: Training Consolidation — Checklist

## Принцип
TrainingScreen — единственный экземпляр экрана для карточных сессий.
Любой элемент принадлежит TrainingScreen и переиспользуется через конфигурацию.

---

## 1. Mode — конфигурация, не логика

### Mode определяет ТОЛЬКО визуал:
- [ ] Subtitle текст (tense label / "Review Session" / "Refresh Session")
- [ ] Фон (белый / зелёный drill)
- [ ] Verb chips (показаны только для VERB_DRILL / DAILY_VERBS)
- [ ] Completion экран (урок завершён / verb drill stats / daily block done)

### Mode ПРОЗРАЧЕН для:
- [ ] SessionRunner.submitAnswer() — единый алгоритм, без when(mode)
- [ ] SessionRunner.updateWordBank() — единая генерация, без when(mode)
- [ ] SessionRunner.nextCard() — единая навигация
- [ ] SessionRunner.startSession() — один метод вместо startXxxSession()
- [ ] Answer validation — единая логика

### Запрещено:
- [ ] when(mode) ветки внутри SessionRunner
- [ ] Отдельные startVerbDrillSession / startDailyTranslateSession / startDailyVerbsSession
- [ ] Разная логика word bank по mode
- [ ] Режим-специфичная навигация внутри TrainingScreen

---

## 2. Навигация — returnTo токен

- [ ] TrainingScreen получает returnTo: String
- [ ] Урок → returnTo = HOME
- [ ] Daily Practice → returnTo = DAILY_PRACTICE
- [ ] Verb Drill → returnTo = VERB_DRILL
- [ ] Mix Challenge → returnTo = HOME
- [ ] Сессия завершена → navigate(returnTo), TrainingScreen не знает контекст

---

## 3. TrainingScreen modes (9 режимов)

### Уже работают:
- [ ] NORMAL — lesson cards from LessonRoadmap
- [ ] BOSS — boss battle review
- [ ] BOSS_MEGA — mega boss battle
- [ ] DRILL — lesson-scoped drill sub-mode
- [ ] ELITE — refresh session
- [ ] MIX_CHALLENGE — mixed challenge from HomeScreen
- [ ] VERB_DRILL — after VerbDrill selection

### Добавлены в TASK-061:
- [ ] DAILY_TRANSLATE — Daily Practice block 1 через TrainingScreen
- [ ] DAILY_VERBS — Daily Practice block 3 через TrainingScreen

---

## 4. Daily Practice — block-config архитектура

### Структура:
- [ ] DailySessionState.blocks: List<DailyBlock> (не плоский List<DailyTask>)
- [ ] DailyBlock: type, tasks, renderVia (TRAINING_SCREEN / INLINE), isComplete
- [ ] BlockRenderVia enum: TRAINING_SCREEN для TRANSLATE/VERBS, INLINE для VOCAB

### Оркестрация:
- [ ] Единый onBlockComplete() → blockIndex++ → startNextBlock
- [ ] TRANSLATE/VERBS: TrainingScreen done → navigate(DAILY_PRACTICE) → coordinator.onBlockComplete()
- [ ] VOCAB: inline → onComplete → coordinator.onBlockComplete()
- [ ] Все блоки готовы → navigate(HOME) → страйк
- [ ] Нет advanceToNextBlock() / advanceDailyBlock() сканирования

### TENSE_LADDER:
- [ ] Логика не тронута — уровень 1-12 → активные времена
- [ ] VERBS блок фильтрует карты по активным временам

---

## 5. Экраны — роли

| Экран | Роль | Карточки |
|-------|------|----------|
| TrainingScreen | Единый рендерер | Всё через один composable |
| VerbDrillScreen | Выбор только | → TrainingScreen(VERB_DRILL) |
| DailyPracticeScreen | Координатор | TRANSLATE/VERBS → TrainingScreen, VOCAB inline |
| VocabDrillScreen | Отдельная парадигма | Flip-card + SRS, НЕ TrainingScreen |
| HomeScreen | Точка входа | Запуск + страйки |

---

## 6. Прогрессы — независимы

- [ ] recordDailyCardPracticed() — mastery + answered count
- [ ] persistDailyVerbProgress() — everShown + todayShown
- [ ] rateVocabCard() — SRS step
- [ ] endSession() → страйк
- [ ] cancelDailySession() → cursor advancement
- [ ] Прогрессы НЕ зависят от returnTo

---

## 7. Верификация на устройстве

### Общие:
- [ ] Build проходит
- [ ] Все режимы TrainingScreen показывают заголовок "GrammarMate"
- [ ] Word bank работает во всех режимах (NORMAL, VERB_DRILL, DAILY_TRANSLATE, DAILY_VERBS)
- [ ] No регрессий в NORMAL, BOSS, DRILL, VERB_DRILL

### Урок:
- [ ] Выбрать урок → TrainingScreen → пройти карточки → HOME → страйк

### Verb Drill:
- [ ] Verb Drill → выбор → TrainingScreen → пройти → VERB_DRILL или HOME

### Daily Practice:
- [ ] Daily Practice → TRANSLATE блок рендерится через TrainingScreen
- [ ] TRANSLATE done → sparkle → VOCAB inline с SRS кнопками
- [ ] VOCAB done → sparkle → VERBS блок через TrainingScreen
- [ ] VERBS done → completion → HOME → страйк
- [ ] Отмена daily → cursor advancement сохранён

### Anti-check:
- [ ] Нигде кроме TrainingScreen нет рендера sentence/verb карточек
- [ ] Нет отдельных TrainingCardSession вне TrainingScreen
- [ ] Нет when(mode) в SessionRunner бизнес-логике

---

## 8. Рефакторинг (TODO после верификации)

- [ ] SessionRunner: единый startSession(cards, mode, returnTo)
- [ ] SessionRunner: убрать when(mode) из submitAnswer, updateWordBank
- [ ] SessionRunner: убрать отдельные startXxxSession методы
- [ ] GrammarMateApp: убрать mode-специфичные token listeners
- [ ] DailyPracticeScreen: убрать мёртвый код (CardSessionBlock, DailyTrainingCardSession)

---

## 9. Verified on Device (PASS)

### Daily Practice:
- [x] Daily Practice TRANSLATE block → goes through TrainingScreen → word bank toggle visible → complete → returns to DAILY_PRACTICE
- [x] Daily Practice VOCAB block → inline flashcards → multiple cards → SRS rating per card → advances to next card → after last card → transitions to VERBS
- [x] Daily Practice VERBS block → goes through TrainingScreen → complete → returns to DAILY_PRACTICE → all blocks done → HOME + streak

### Verb Drill:
- [x] Verb Drill → selection screen → TrainingScreen → word bank visible → complete → returns to VERB_DRILL screen

### Regular Lesson:
- [x] Regular lesson → TrainingScreen → complete → HOME → streak

### Navigation & Controls:
- [x] Stop/exit button → navigates to HOME (cancelDailySession)
- [x] Back button during daily → navigates to HOME

### Pomodoro (from pomodoro branch):
- [x] Pomodoro pause → hides input controls
- [x] Streak indicator in HomeScreen header
- [x] Pomodoro timer + summary + difficulty rating

### NOT VERIFIED / KNOWN ISSUE:
- [ ] Word bank in regular lesson — user reports not visible. Possible cause: HintLevel set to MEDIUM or HARD in app settings. By design, word bank is only available at EASY level. **Action: user to check Settings > Difficulty level.**

---

## 10. Screen Element Logic

### Screen: TrainingScreen (unified card renderer)

| Element | Behavior |
|---------|----------|
| Title | "GrammarMate" for all modes — hardcoded, not mode-dependent |
| Header subtitle | Text varies by TrainingScreenMode: tense label for VERB_DRILL/DAILY_VERBS, "Review Session" for BOSS, etc. |
| Card prompt | Russian sentence or word from current SentenceCard |
| Verb chips | Visible only when mode is VERB_DRILL or DAILY_VERBS. Show conjugation group/tense info. |
| Input controls | UnifiedInputControlsBar: text field (keyboard), voice button, word bank toggle |
| Word bank toggle | Visible only when `hintLevel == EASY` AND `wordBankWords.isNotEmpty()`. Hidden at MEDIUM/HARD difficulty. |
| Word bank chips | Shown when `inputMode == WORD_BANK` AND `supportsWordBank == true`. Chips are clickable answer fragments. |
| Input controls (pomodoro) | Entire input bar hidden when `pomodoro.isPaused == true`. Resumes on unpause. |
| Card counter | "N / total" indicator showing progress through current session |
| Next card button | Advances to next card in session. Disabled until answer submitted or skipped. |
| Exit / Stop button | Opens exit confirmation dialog → navigates to HOME via cancelDailySession / session cleanup |

### Screen: DailyPracticeScreen (coordinator)

| Element | Behavior |
|---------|----------|
| Role | Orchestrator — renders 3 blocks sequentially, does NOT render cards itself (except VOCAB) |
| TRANSLATE block | Detects block type == TRANSLATE → extracts cards → navigates to TrainingScreen with `returnTo = DAILY_PRACTICE` |
| VOCAB block | Inline VocabFlashcardBlock: flip card animation → voice input → SRS difficulty rating (AGAIN/HARD/GOOD/EASY) → next card via taskIndex increment → after last card → onBlockComplete() |
| VERBS block | Detects block type == VERBS → extracts cards → navigates to TrainingScreen with `returnTo = DAILY_PRACTICE` |
| Block transitions | After each block completes → sparkle animation → next block starts automatically |
| All blocks done | Navigate to HOME → record daily streak → show completion indicator |

### Screen: VerbDrillScreen (selection only)

| Element | Behavior |
|---------|----------|
| Role | Tense/group picker only. Does NOT render cards itself. |
| Selection UI | Shows available tenses and verb groups from active pack |
| On selection | Calls `startCardSession(cards, VERB_DRILL)` → navigates to TrainingScreen with `returnTo = VERB_DRILL` |
| After session | TrainingScreen completes → navigates back to VERB_DRILL screen (not HOME) |

### Navigation: returnTo token

| Source | returnTo value | After completion |
|--------|---------------|-----------------|
| Lesson screen (LessonRoadmap) | `LESSON` | Back to lesson / HOME |
| DailyPracticeScreen | `DAILY_PRACTICE` | Back to daily coordinator → next block or HOME |
| VerbDrillScreen | `VERB_DRILL` | Back to drill selection screen |
| HomeScreen (Mix Challenge) | `HOME` | Direct to HOME |

- `setReturnTo()` is called at ALL navigation-to-TrainingScreen sites
- NavDialogs reads returnTo value → navigates to correct destination
- For DAILY_PRACTICE: completion also calls `onBlockComplete()` to advance block cursor

---

## 11. Regression Plan

### Regression Test Matrix

| # | Test Case | Steps | Expected |
|---|-----------|-------|----------|
| 1 | Daily Practice full flow | Start daily → TRANSLATE (10 cards) → VOCAB (5 cards) → VERBS (10 cards) | All 3 blocks complete → HOME → streak recorded |
| 2 | Regular lesson | Select lesson → TrainingScreen → complete all cards | Returns HOME → streak updated |
| 3 | Regular lesson — word bank | Settings: EASY → select lesson → TrainingScreen | Word bank toggle visible, chips work |
| 4 | Regular lesson — no word bank (MEDIUM) | Settings: MEDIUM → select lesson → TrainingScreen | Word bank toggle hidden, keyboard + voice only |
| 5 | Regular lesson — no word bank (HARD) | Settings: HARD → select lesson → TrainingScreen | Word bank toggle hidden, voice only |
| 6 | Verb Drill full flow | Select tense → TrainingScreen → complete cards | Returns to VERB_DRILL selection screen |
| 7 | Verb Drill — word bank | Settings: EASY → Verb Drill → TrainingScreen | Word bank toggle visible |
| 8 | Stop/exit from lesson | During regular lesson → press stop → confirm dialog | Navigates to HOME |
| 9 | Stop/exit from daily | During daily TRANSLATE → press stop → confirm | Navigates to HOME, cancelDailySession called |
| 10 | Stop/exit from verb drill | During verb drill → press stop → confirm | Navigates to HOME or back to selection |
| 11 | Back button from lesson | During regular lesson → press back | Correct destination (HOME or lesson screen) |
| 12 | Back button from daily | During daily practice → press back | Navigates to HOME |
| 13 | Pomodoro pause/resume | Start session → pause → type answer → Check → Play | Input visible during pause, Check validates, Play resumes |
| 14 | Pomodoro complete | Start session → complete all cards in time | Summary shown → difficulty rating → HOME |
| 15 | Boss battle | Start boss → TrainingScreen → complete | Returns HOME, boss result recorded |
| 16 | Mix challenge | Start mix from HOME → TrainingScreen → complete | Returns HOME |
| 17 | Daily VOCAB SRS | Daily → VOCAB block → flip card → voice input → rate AGAIN/HARD/GOOD/EASY | Rating recorded, next card appears, SRS step updated |
| 18 | Pause — input cascade (Variant B) | During lesson → pause → type answer → Check | Check validates answer, Play button activates |
| 19 | Pause — input cascade (hint) | 3 wrong answers → hint shown → type answer → Check | Check validates, Play activates, no auto-advance |
| 20 | Pause — input cascade (pomodoro) | Pomodoro pause → type answer → Check | Check validates, Play activates |
| 21 | Pause — input preserved | Pause → input has text → resume | Input text preserved after resume |

### Priority Order
1. Test cases 1-5 (Daily Practice + regular lesson with all hint levels)
2. Test cases 6-7 (Verb Drill)
3. Test cases 8-12 (Stop/exit/back from all modes)
4. Test cases 13-14, 18-21 (Pomodoro + pause-input cascade)
5. Test cases 15-17 (Boss, Mix, SRS)
