# TASK-089: VerbDrill SessionCard Regression Test v2 — Checklist

## Принцип
Тест должен покрывать SessionCard логику для ОБЕИХ режимов (random и frequency), но с разными assertions для каждого режима.

---

## 1. Тестовая структура

### Файл и класс
- [ ] Тестовый класс: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt`
- [ ] 4 тестовых метода (не 3 как в v1)
- [ ] Названия тестов четко указывают use case

### Use Case покрытие
- [ ] Use Case 1: Repeat (любой режим)
- [ ] Use Case 2: Continue в random-mode
- [ ] Use Case 3: Continue в frequency-mode (НОВЫЙ)
- [ ] Use Case 4: Reset (с frequency-mode проверкой нового старта)

---

## 2. Random-mode тесты

### Use Case 2: Continue random-mode
- [ ] НЕ вызывать `toggleSortByFrequency()` (random режим по умолчанию)
- [ ] Check на cards 1,2 через UI
- [ ] Next only на card 3 (navigation-only)
- [ ] Exit → Click Continue через UI
- [ ] Assertions:
  - [ ] `shownToday.size == 2`
  - [ ] `navigationOnlyCardId !in shownToday`
  - [ ] `continueBatchIds.intersect(checkedCardIds).isEmpty()`
- [ ] НЕ проверять точный порядок `continueBatchIds` (shuffled ожидаемо)

### Use Case 1: Repeat (также работает для random-mode)
- [ ] Start session (без toggleSortByFrequency)
- [ ] Check 3 cards
- [ ] Exit → Click Repeat
- [ ] Assertion: `repeatBatchIds == firstBatchIds`
- [ ] Примечание: Ordered replay работает даже если первый batch был случайным

---

## 3. Frequency-mode тесты

### Подготовка для frequency-mode
- [ ] Вызывать `verbVm.toggleSortByFrequency()` перед стартом
- [ ] Использовать детерминированные cards: `rank = 1..12`, `ids = test_verb_1..12`
- [ ] `sessionSize = 5`

### Use Case 3: Continue frequency-mode (НОВЫЙ)
- [ ] Вызвать `toggleSortByFrequency()` перед стартом
- [ ] Start session
- [ ] Check `test_verb_1`, `test_verb_2`
- [ ] Next only на `test_verb_3` (navigation-only)
- [ ] Exit → Click Continue через UI
- [ ] Assertions:
  - [ ] `continueBatchIds == [test_verb_3, test_verb_4, test_verb_5, test_verb_6, test_verb_7]`
  - [ ] `navigationOnlyCardId in continueBatchIds`
  - [ ] `navigationOnlyCardId == continueBatchIds.first()` (идет первой при frequency-sort)

### Use Case 4: Reset (с frequency-mode проверкой)
- [ ] Вызвать `toggleSortByFrequency()` перед стартом
- [ ] Start session
- [ ] Check 2 cards
- [ ] Exit → Click Reset через UI
- [ ] Common assertions (все режимы):
  - [ ] `store.loadLastSession() == null`
  - [ ] SessionCard скрыт (`assertIsNotDisplayed()`)
  - [ ] `progressBeforeReset == progressAfterReset`
- [ ] Frequency-mode assertions:
  - [ ] Новый Start после Reset
  - [ ] `afterResetStartBatchIds == [test_verb_1..5]`
  - [ ] Колода начинается с начала

---

## 4. TRUE UI Clicks (эталон из v1)

### Обязательные UI клики
- [ ] Start: `verb_start_button.performClick()`
- [ ] Check answer: `check_button.performClick()`
- [ ] Navigation: `next_button.performClick()`
- [ ] Repeat: `repeat_button.performClick()`
- [ ] Continue: `continue_button.performClick()`
- [ ] Reset: `reset_button.performClick()`

### Запрещено (ViewModel bypass)
- [ ] НЕ вызывать `verbVm.startSession()` из теста
- [ ] НЕ вызывать `verbVm.onResumeSession()` из теста
- [ ] НЕ вызывать `verbVm.onRepeatSession()` из теста
- [ ] НЕ вызывать `verbVm.onStartFresh()` из теста
- [ ] НЕ менять `route.value` напрямую (кроме через helper)

### Допустимо
- [ ] Helper `exitTrainingThroughUi()` (как в v1)
- [ ] Чтение state для assertions: `session.cards.map { it.id }`, `store.loadLastSession()`

---

## 5. Синхронизация (эталон из v1)

### Обязательные waitUntil
- [ ] Перед стартом: `!isLoading && totalCards > 0 && lastSessionContext == null`
- [ ] После клика start button: `session?.cards?.isNotEmpty() == true`
- [ ] Перед SessionCard кнопками: кнопки отображены

### Запрещено
- [ ] НЕ использовать `Thread.sleep()` - только `waitUntil()`
- [ ] НЕ hardcoded задержки

---

## 6. Production код

### Проверка что НЕ изменено
- [ ] `VerbDrillScreen.kt` использует `collectAsStateWithLifecycle()`
- [ ] НЕ изменен на `collectAsState()`
- [ ] SessionCard логика в ViewModel не изменена

### Проверка что соответствует спецификации
- [ ] `onRepeatSession()` воспроизводит sessionCardIds (line 759)
- [ ] `onResumeSession()` исключает todayShownCardIds (line 734)
- [ ] `onStartFresh()` удаляет сессию, сохраняет прогресс (line 809)
- [ ] `startSession()` использует sortedBy vs shuffled (line 483)

---

## 7. Запуск и стабильность

### Команда запуска
- [ ] Используется: `:app:testDebugUnitTest --tests "..."` (НЕ просто `test --tests`)
- [ ] Тест запускается и проходит: все 4 green

### Стабильность
- [ ] Повторный запуск (3+ раз) - все проходят
- [ ] Нет flaky из-за shuffle в random-mode
- [ ] Нет timing issues

---

## 8. Документация

### Обновления (если нужно)
- [ ] Требования обновлены: `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md`
- [ ] Задача создана: `docs/specification/tasks/TASK-089/`
- [ ] Спецификация проверена на соответствие коду

### Финальные утверждения
Можно сказать после завершения:
- [ ] "Тест полностью покрывает SessionCard логику для обоих режимов"
- [ ] "Random-mode тесты проверяют исключения (не порядок)"
- [ ] "Frequency-mode тесты доказывают точный порядок батчей"
- [ ] "Repeat работает для обоих режимов (ordered replay)"
- [ ] "Reset удаляет сессию, сохраняет прогресс, перезапускает колоду в frequency-mode"

---

## 9. Критерии приемки (финальная проверка)

- [ ] Все 4 use case реализованы
- [ ] Random/frequency режимы правильно разделены
- [ ] Assertions соответствуют режиму (порядок vs исключения)
- [ ] TRUE UI clicks только (без ViewModel bypass)
- [ ] Production код не изменен
- [ ] Все 4 теста green и стабильны
- [ ] Код закоммичен и запушен
