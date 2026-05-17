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
