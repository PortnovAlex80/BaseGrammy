# TASK-061: Training Consolidation — Design Document

## Принцип

**TrainingScreen — единственный экземпляр экрана для карточных сессий.**

Любой элемент на TrainingScreen (банк слов, навигация, инпут, результат) принадлежит этому экрану и никакому другому. Экран переиспользуется через конфигурацию — без изменения логики.

---

## 1. Mode — конфигурация рендеринга, НЕ логика

### Mode определяет ТОЛЬКО:
- Какой subtitle показать
- Зелёный фон или белый
- Показывать ли verb chips
- Какой completion экран

### Mode обязан быть прозрачным для:
- SessionRunner — единый `startSession(cards)`, `submitAnswer()`, `nextCard()` для всех режимов
- Word bank — единая генерация из card pool, без веток по mode
- Навигация между картами — единый next/prev/pause
- Answer validation — единый алгоритм
- Завершение сессии — единый механизм

### Запрещено:
- `when(mode)` ветки внутри SessionRunner
- Отдельные методы `startXxxSession()` для каждого режима
- Разная логика word bank для разных режимов
- Режим-специфичная навигация внутри TrainingScreen

---

## 2. Навигация — returnTo токен

TrainingScreen получает `returnTo: String` — маршрут возврата.

```
Урок        → returnTo = HOME
Daily       → returnTo = DAILY_PRACTICE
Verb Drill  → returnTo = VERB_DRILL
Mix         → returnTo = HOME
```

Сессия завершена → `navigate(returnTo)`. TrainingScreen не знает контекст вызова.

---

## 3. Daily Practice — block-config оркестрация

### Структура
```
DailySessionState(
    blocks: List<DailyBlock>,
    blockIndex: Int
)

DailyBlock(
    type: DailyBlockType,      // TRANSLATE, VOCAB, VERBS
    tasks: List<DailyTask>,
    renderVia: BlockRenderVia,  // TRAINING_SCREEN или INLINE
    isComplete: Boolean
)
```

### Флоу
```
DailyPractice (coordinator):
  block 0: TRANSLATE → TrainingScreen → done → onBlockComplete()
  block 1: VOCAB     → inline        → done → onBlockComplete()
  block 2: VERBS     → TrainingScreen → done → onBlockComplete()
  все блоки готовы → HOME + страйк
```

### Единый путь завершения
- ALL блоки сигнализируют завершение через `onBlockComplete()` → `blockIndex++`
- TRANSLATE/VERBS: TrainingScreen завершает → `returnTo = DAILY_PRACTICE` → coordinator.onBlockComplete()
- VOCAB: inline рендер → onComplete callback → coordinator.onBlockComplete()
- Никакого `advanceToNextBlock()` сканирования

### TENSE_LADDER — без изменений
Уровень урока (1-12) определяет активные времена для блока VERBS. Логика в DailySessionComposer не меняется.

---

## 4. Прогрессы — независимы от источника вызова

Система не знает откуда вызывалась тренировка. Страйки и статистика считаются одинаково:

- `recordDailyCardPracticed(blockType)` — подсчёт отвеченных карт + mastery для TRANSLATE
- `persistDailyVerbProgress(card)` — VerbDrillComboProgress (everShown + todayShown)
- `rateVocabCard(rating)` — SRS rating для VOCAB
- `cancelDailySession()` → возвращает количество предложений для cursor advancement
- `endSession()` → финализирует страйк

---

## 5. Экраны — кто чем владеет

| Экран | Роль | Карточки |
|-------|------|----------|
| TrainingScreen | Единый рендерер | Рендерит всё через один composable |
| VerbDrillScreen | Выбор только | Не рендерит карточки → TrainingScreen |
| DailyPracticeScreen | Координатор | Не рендерит TRANSLATE/VERBS → TrainingScreen, VOCAB inline |
| VocabDrillScreen | Отдельная парадигма | Flip-card + SRS, НЕ через TrainingScreen |
| HomeScreen | Точка входа | Страйки, запуск тренировок |

---

## 6. Checklist реализации

### TrainingScreen modes (единый экран)
- [x] NORMAL — lesson cards
- [x] BOSS / BOSS_MEGA — review session
- [x] DRILL — lesson-scoped drill
- [x] ELITE — refresh session
- [x] MIX_CHALLENGE
- [x] VERB_DRILL — after selection screen
- [x] DAILY_TRANSLATE — Daily Practice block 1
- [x] DAILY_VERBS — Daily Practice block 3

### Архитектура
- [x] Block-config: List<DailyBlock> вместо плоского List<DailyTask>
- [x] BlockRenderVia: TRAINING_SCREEN / INLINE
- [x] Единый onBlockComplete() вместо advanceDailyBlock()
- [x] returnTo токен для навигации
- [ ] SessionRunner без when(mode) веток (рефакторинг)
- [ ] Единый startSession() вместо отдельных startXxxSession()

### Верификация
- [ ] Build проходит
- [ ] APK протестирован на устройстве
- [ ] Все режимы показывают "GrammarMate" заголовок
- [ ] Word bank работает во всех режимах
- [ ] Daily Practice: TRANSLATE → VOCAB → VERBS переходы
- [ ] Страйки засчитываются
- [ ] Нет регрессий в NORMAL, BOSS, DRILL, VERB_DRILL
