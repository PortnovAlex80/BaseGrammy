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

## 10. Screen Element Logic — Verified Audit

### Screen: HomeScreen

| Element | Visibility | Enabled | Behavior | Notes |
|---------|-----------|---------|----------|-------|
| Avatar circle | Always | Always | Calls onProfileClick | |
| User name | Always, `.take(6)` hard-truncates | N/A | Display only | `.take(6)` clips before Ellipsis has effect |
| Language selector | Always | Always | DropdownMenu with languages | |
| Settings gear | Always | Always | Opens settings | |
| Primary action card | Always | Always | "Continue/Start learning" → navigates | |
| Lesson tile grid | Always (4-column) | Per tile: locked shows dialog | Unlocked → selectLesson | |
| Fire streak indicator | Always (inline after username) | N/A | todayFires emojis + "Nd" streak | Always shows at least one dimmed fire even at streak=0 |
| Tomato (Pomodoro) icon | Always | Always | Opens PomodoroSelectorSheet | |
| Verb Drill tile | `hasVerbDrill == true` | Always | Opens verb drill selection | Pack-scoped |
| Vocab Drill tile | `hasVocabDrill == true` | Always | Opens vocab drill | Pack-scoped, shows mastered count |
| Daily Practice tile | Always | Always | Opens daily practice | |
| "How This Training Works" | Always | Always | Opens info dialog | |

### Screen: TrainingScreen (unified card renderer)

| Element | Visibility | Enabled | Behavior | Notes |
|---------|-----------|---------|----------|-------|
| Title "GrammarMate" | Always (TopBar) | N/A | Display only | Same for ALL modes |
| Settings gear (TopBar) | Always | Always | Opens settings | NOT removed — still in TopBar |
| PomodoroTimerBanner | `pomodoro.isActive && !pomodoro.isComplete` | Pause/Resume button | Shows timer + stats | 48dp Card above session |
| Header subtitle | BOSS/BOSS_MEGA → "Review Session"; ELITE → "Refresh Session" | N/A | Display only | NORMAL/DRILL/VERB_DRILL show tense label instead |
| Tense label | `card.tense != null` for non-BOSS/non-ELITE | N/A | Color varies by mode | Green for drill/verb, blue for mix |
| Card prompt (header) | `cleanPrompt.isNotBlank()` | N/A | Strips ALL parentheticals via regex | **Always strips, ignores HintLevel** |
| Card prompt (CardPrompt) | `currentCard != null` | N/A | Uses HintCalculator (respects HintLevel) | **Can differ from header prompt** |
| Verb drill chips | `mode == VERB_DRILL \|\| DAILY_VERBS) && drillCard != null` | N/A | Display only (onClick no-op) | Verb/tense/group info |
| Answer text field | Always (UnifiedInputControlsBar) | `hasCards` | Auto-submit on exact match in KEYBOARD | |
| Mic trailing icon | `supportsVoiceInput` (always true) | `hasCards && canSubmit` | Switches to VOICE + launches speech | |
| Voice mode hint | `inputMode == VOICE && canSubmit` | N/A | "Say translation: {prompt}" | Shows during PAUSED too |
| Word bank toggle | `supportsWordBank && hintLevel == EASY` | `canSelectInputMode` | Switches to WORD_BANK mode | Hidden at MEDIUM/HARD |
| Word bank chips | `inputMode == WORD_BANK && supportsWordBank` | Per chip (used ones disabled) | Appends word to answer | |
| Check button | Always | `hasCards && inputText.isNotBlank() && canSubmit` | Submits answer | canSubmit = ACTIVE or PAUSED (NOT HINT_SHOWN) |
| Show answer (eye) | Always | `hasCards && hintAnswer == null` | Calls showAnswer() | Disabled when hint already shown |
| Report button | `supportsFlagging && hasCards` | `hasCards` | Opens report sheet | |
| HintAnswerCard | `hintAnswer != null` (answerText from state) | N/A | Pink card with answer text | Shows after eye click or 3 wrong |
| Result label | `lastResult != null` | N/A | "Correct" green / "Incorrect" red | |
| Prev button | `supportsNavigation` | `hasCurrentCard` | ArrowBack, pause-first navigation | |
| Pause/Play button | `supportsPause && supportsNavigation` | `hasCurrentCard` | Pause when active, Play otherwise | Play from hint clears hint, resumes ACTIVE |
| Exit button | `supportsNavigation` | `hasCurrentCard` | StopCircle, opens exit dialog | |
| Next button | `supportsNavigation` | `hasCurrentCard` | ArrowForward, pause-first navigation | |
| VerbDrillCompletion | `VERB_DRILL && !hasCards` | More/Exit buttons | Stats + party popper | Only for VERB_DRILL mode |
| PomodoroSummary | `pomodoro.isComplete` | "OK" button | Full screen: ring + stats + streak | Stays until OK pressed |
| Auto-voice LaunchedEffect | `voiceAutoStart && VOICE && ACTIVE && hasCard` | N/A | Launches speech after 200ms delay | Watches card.id changes |

### Screen: DailyPracticeScreen (coordinator)

| Element | Visibility | Enabled | Behavior | Notes |
|---------|-----------|---------|----------|-------|
| Loading spinner | `!active \|\| currentBlock == null` | N/A | "Loading session..." | |
| Back button | Always | Always | Opens exit confirmation | |
| "Daily Practice" title | Always | N/A | 18sp SemiBold | |
| Block type badge | Always | N/A | primaryContainer chip | "Translation"/"Vocabulary"/"Verbs" |
| Block progress bar | `totalTasks > 0` | N/A | LinearProgressIndicator + "N/M" | |
| Block sparkle overlay | `showBlockTransition == true` | N/A | Auto-dismisses after 800ms | Shows "Next: {BlockType}" |
| TRANSLATE/VERBS block | `blockType == TRANSLATE \|\| VERBS` | N/A | Navigates to TrainingScreen | Cards rendered in TrainingScreen, NOT here |
| VOCAB flashcard | `blockType == VOCAB` | N/A | Inline card with prompt + translation | Both prompt and answer always visible |
| Vocab TTS button | VOCAB block active | Always | Speaks prompt text | |
| Vocab mic button | VOCAB block active | `!isVoiceActive` | 64dp button, launches speech | |
| Vocab rating buttons | VOCAB block active | Always (4 buttons) | AGAIN/HARD/GOOD/EASY → auto-advances | Rating recorded, taskIndex++ |
| Completion sparkle | `finishedToken && !hasShownCompletion` | N/A | "Session Complete!" | |
| Exit confirmation | `showExitDialog` | Stay/Exit | "Exit practice?" | |

### Screen: VerbDrillScreen (selection only)

| Element | Visibility | Enabled | Behavior | Notes |
|---------|-----------|---------|----------|-------|
| Tense/group picker | Always | Always | Grid of tenses and groups | Selection screen only |
| On selection | — | — | startCardSession → TrainingScreen(VERB_DRILL) | |

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
- For POMODORO: completion blocks navigation, shows summary until OK pressed

---

## 10.1 Discrepancies Found (Spec vs Code)

### HIGH — Behavioral mismatch

| # | Element | Issue | Status |
|---|---------|-------|--------|
| D1 | Check button (TS-25) | `canSubmit` excludes HINT_SHOWN — user cannot submit during hint shown. Spec says "no session state gate" | **BY DESIGN** — hint is the answer, re-submitting makes no sense |
| D2 | TS-38 Session Completion | Universal completion overlay for NORMAL/DRILL/ELITE. | **FIXED** — SessionCompletionContent with OK button |
| D3 | Verb/Tense chip taps | Bottom sheets wired to chip onClick. | **FIXED** — VerbReferenceBottomSheet + TenseInfoBottomSheet |
| D4 | PM-04 DifficultyRatingRow | Imported but never rendered in TrainingScreen. | **REMOVED** — pomodoro stripped to timer-only |

### MEDIUM — Visual/conditional mismatch

| # | Element | Issue | Status |
|---|---------|-------|--------|
| D5 | Header prompt vs Card prompt | Header strips ALL parentheticals; Card uses HintCalculator. Two different texts visible. | **BY DESIGN** — header is abbreviated |
| D6 | Show answer (eye) | Extra guard `hintAnswer == null` not in spec. Disables when hint already shown. | **CORRECT** — prevents double-click |
| D7 | Voice mode hint | Shows during PAUSED too (via canSubmit), spec says ACTIVE only. | **BY DESIGN** — Variant B cascade |
| D8 | Keyboard button | Always visible, disabled when not available (spec implies conditional visibility). | **LOW PRIORITY** |
| D9 | Fire streak | Always shows at least one dimmed fire emoji even at streak=0. | **LOW PRIORITY** |
| D10 | PomodoroSummary "OK" vs "Done" | Code uses regular Button "OK", spec says FilledTonalButton "Done". | **USER CHOICE** — OK is correct |
| D11 | PomodoroSummary ring duration | Shows selected duration MM:00, not actual active time. | **LOW PRIORITY** |

### LOW — Stale spec notes

| # | Element | Issue |
|---|---------|-------|
| D12 | MixChallengeSurface | Spec says "hardcoded" but already theme-aware |
| D13 | Progress bar colors | Spec lists hardcoded hex, code uses theme colors |
| D14 | PomodoroSummary ring/difficulty colors | Hardcoded, not theme-aware (dark mode contrast risk) |
| D15 | Dead code | HeaderStats(), ModeSelector() deleted | **FIXED** — removed dead composables and imports |

---

## 11. Regression Plan

### Regression Test Matrix

| # | Test Case | Steps | Expected |
|---|-----------|-------|----------|
| 1 | Daily Practice full flow | Start daily → TRANSLATE (10 cards) → VOCAB (5 cards) → VERBS (10 cards) | All 3 blocks complete → HOME → streak recorded. Pause→Check→Play cascade works during TRANSLATE and VERBS blocks. |
| 2 | Regular lesson | Select lesson → TrainingScreen → complete all cards | Returns HOME → streak updated. Pause→Check→Play cascade works: type during pause → Check validates → Play resumes (no auto-advance). |
| 3 | Regular lesson — word bank | Settings: EASY → select lesson → TrainingScreen | Word bank toggle visible, chips work. Cascade: word bank usable during pause state. |
| 4 | Regular lesson — no word bank (MEDIUM) | Settings: MEDIUM → select lesson → TrainingScreen | Word bank toggle hidden, keyboard + voice only |
| 5 | Regular lesson — no word bank (HARD) | Settings: HARD → select lesson → TrainingScreen | Word bank toggle hidden, voice only |
| 6 | Verb Drill full flow | Select tense → TrainingScreen → complete cards | Returns to VERB_DRILL selection screen. Pause→Check→Play cascade works during verb drill. |
| 7 | Verb Drill — word bank | Settings: EASY → Verb Drill → TrainingScreen | Word bank toggle visible. Cascade: input visible during pause, Check validates, Play resumes. |
| 8 | Stop/exit from lesson | During regular lesson → press stop → confirm dialog | Navigates to HOME |
| 9 | Stop/exit from daily | During daily TRANSLATE → press stop → confirm | Navigates to HOME, cancelDailySession called |
| 10 | Stop/exit from verb drill | During verb drill → press stop → confirm | Navigates to HOME or back to selection |
| 11 | Back button from lesson | During regular lesson → press back | Correct destination (HOME or lesson screen) |
| 12 | Back button from daily | During daily practice → press back | Navigates to HOME |
| 13 | Pomodoro pause/resume | Start session → pause → type answer → Check → Play | Input visible during pause, Check validates, Play resumes |
| 14 | Pomodoro complete | Start session → complete all cards in time | Summary shown with OK button → press OK → HOME |
| 14a | Pomodoro summary stays | Complete pomodoro → summary screen | Summary stays until OK pressed, no auto-dismiss |
| 14b | Eye/hint button (fresh card) | Any mode → fresh card → press eye | Answer shown in pink card. Works before any attempt. |
| 14c | Eye/hint button (after wrong) | Any mode → wrong answer → press eye | Answer shown in pink card. |
| 14d | Completion screen (lesson) | Complete regular lesson → all cards done | "🎉" + stats + OK button shown. Press OK → HOME |
| 14e | Completion screen (daily block) | Complete daily TRANSLATE or VERBS block | "🎉" + stats + OK → returns to daily coordinator → next block |
| 14f | Completion screen (boss/mix) | Complete boss battle or mix challenge | "🎉" + stats + OK → HOME |
| 14g | Verb chip bottom sheet | During verb drill → tap verb chip | VerbReferenceBottomSheet opens with conjugation info |
| 14h | Tense chip bottom sheet | During verb drill → tap tense chip | TenseInfoBottomSheet opens (fallback: abbreviated name) |
| 14i | Verb chip bottom sheet (daily) | During daily VERBS block → tap verb chip | VerbReferenceBottomSheet opens |
| 15 | Boss battle | Start boss → TrainingScreen → complete | Returns HOME, boss result recorded |
| 16 | Mix challenge | Start mix from HOME → TrainingScreen → complete | Returns HOME |
| 17 | Daily VOCAB SRS | Daily → VOCAB block → flip card → voice input → rate AGAIN/HARD/GOOD/EASY | Rating recorded, next card appears, SRS step updated |
| 18 | Pause — input cascade (Variant B) | During lesson → pause → type answer → Check | Check validates answer, Play button activates |
| 19 | Pause — input cascade (hint) | 3 wrong answers → hint shown → type answer → Check | Check validates, Play activates, no auto-advance |
| 20 | Pause — input cascade (pomodoro) | Pomodoro pause → type answer → Check | Check validates, Play activates |
| 21 | Pause — input preserved | Pause → input has text → resume | Input text preserved after resume |
| 22 | Pomodoro summary stats | Start pomodoro → complete session | Summary shows cardsCompleted, successRate, easyCards |
| 23 | Pomodoro 7-day chart | Complete session → check summary | Mon-Sun chart visible, current day highlighted |
| 24 | Pomodoro session overwrite | Complete 2 sessions same day | Last session data shown, not sum |

### Priority Order
1. Test cases 1-5 (Daily Practice + regular lesson with all hint levels)
2. Test cases 6-7 (Verb Drill)
3. Test cases 8-12 (Stop/exit/back from all modes)
4. Test cases 13-14, 18-21 (Pomodoro + pause-input cascade)
5. Test cases 15-17 (Boss, Mix, SRS)
