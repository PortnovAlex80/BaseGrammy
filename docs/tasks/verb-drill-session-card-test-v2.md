# Задача: Написать VerbDrillSessionCardRegressionTest v2

**Статус:** 🔄 Ready to Start
**Приоритет:** High
**Назначена:** TBD
**Создана:** 2025-05-22
**Версия:** 2.0 (полная переработка по обновленной спецификации)

---

## Context

Требования к VerbDrill SessionCard тесту были переработаны и уточнены. Новая спецификация разделяет поведение на два режима:

- **Random-mode** (`sortByFrequency = false`): порядок случаен, проверяем только состав/исключения
- **Frequency-mode** (`sortByFrequency = true`): порядок детерминированный, проверяем точный порядок

Текущая версия теста (`VerbDrillSessionCardRegressionTest.kt`) покрывает только часть требований и работает в random-mode.

**Новая спецификация:** `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md`

---

## Current Status (v1)

**Что текущий тест проверяет:**
- ✅ Repeat: проверяет порядок (честно, работает для обоих режимов)
- ⚠️ Continue: только random-mode (проверяет исключения, не порядок)
- ⚠️ Reset: только удаление сессии (без проверки нового старта)

**Проблемы текущей версии:**
- ❌ Не включен `sortByFrequency` - все в random-mode
- ❌ Нет проверки Continue в frequency-mode с точным порядком
- ❌ Нет проверки нового Start после Reset
- ❌ Только 3 use case, должно быть 4

---

## Task Requirements

### Goal

Написать новую версию теста `VerbDrillSessionCardRegressionTest.kt` которая покрывает все 4 use case из обновленной спецификации:

1. **Use Case 1: Repeat** (работает для random и frequency modes)
2. **Use Case 2: Continue в random-mode** (проверка исключений)
3. **Use Case 3: Continue в frequency-mode** (точный порядок)
4. **Use Case 4: Reset** (общий + frequency-mode для нового старта)

### Technical Requirements

**TRUE UI Clicks (сохранить):**
- ✅ Все взаимодействия через `performClick()` на кнопках
- ✅ Никаких прямых вызовов `verbVm.startSession()` из теста
- ✅ Синхронизация через `waitUntil()` с timeout
- ✅ Чтение state для assertions (не для мутации)

**Новая структура теста:**

Вариант A: Два отдельных тестовых класса
```
VerbDrillSessionCardRandomModeTest
VerbDrillSessionCardFrequencyModeTest
```

Вариант B: Один класс с 4 тестами (предпочтительно)
```
VerbDrillSessionCardRegressionTest
  - test_repeat_replays_saved_batch_random_mode
  - test_continue_random_mode_excludes_checked_only
  - test_continue_frequency_mode_exact_order
  - test_reset_clears_session_keeps_progress
```

**Детерминированные fixture для frequency-mode:**
```kotlin
val cards = createTestVerbCards(12) // rank: 1..12, ids: test_verb_1..12
verbVm.setSessionSize(5)
verbVm.toggleSortByFrequency() // для frequency-mode тестов
```

---

## Specific Use Cases

### Use Case 1: Repeat (random или frequency mode)

**Сценарий:**
1. Start session (любой режим)
2. Check 3 cards
3. Exit → Click Repeat

**Ожидание:**
```kotlin
repeatBatchIds == firstBatchIds
```

**Assertion:**
```kotlin
assertEquals("Repeat should replay exact saved batch", firstBatchIds, repeatBatchIds)
```

**Примечание:** Работает для обоих режимов. Даже если первый batch был случайным, после сохранения он стал ordered list.

---

### Use Case 2: Continue в random-mode

**Сценарий:**
1. Start session (random mode, `sortByFrequency = false`)
2. Check cards 1,2
3. Next only on card 3 (navigation-only)
4. Exit → Click Continue

**Ожидание:**
```kotlin
shownToday == [1,2] // card 3 NOT included
navigationOnlyCardId !in shownToday
continueBatchIds.intersect(checkedCardIds).isEmpty()
```

**Assertions:**
```kotlin
val shownAfterNavigation = store.loadLastSession()!!.todayShownCardIds
assertEquals(2, shownAfterNavigation.size)
assertFalse(navigationOnlyCardId in shownAfterNavigation)

val continueBatchIds = verbVm.uiState.value.session!!.cards.map { it.id }
assertTrue("Continue must exclude checked cards",
    continueBatchIds.intersect(checkedCardIds).isEmpty())
```

**Важно:** НЕ проверяем точный порядок `continueBatchIds` потому что `shuffled()` это ожидаемая логика.

---

### Use Case 3: Continue в frequency-mode

**Сценарий:**
1. Start session (frequency mode, `sortByFrequency = true`)
2. Cards: `["test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"]`
3. Check `test_verb_1`, `test_verb_2`
4. Next only on `test_verb_3` (navigation-only)
5. Exit → Click Continue

**Ожидание:**
```kotlin
continueBatchIds == [
    "test_verb_3",  // navigation-only, идет первой
    "test_verb_4",
    "test_verb_5",
    "test_verb_6",
    "test_verb_7"
]
```

**Assertions:**
```kotlin
val expectedContinueBatch = listOf(
    "test_verb_3", "test_verb_4", "test_verb_5", "test_verb_6", "test_verb_7"
)
assertEquals("Continue should produce exact next ordered batch",
    expectedContinueBatch, continueBatchIds)

assertTrue("Navigation-only card should be in continue batch",
    navigationOnlyCardId in continueBatchIds)
```

**Смысл:** Continue исключает только checked карточки. Navigation-only карточка остается eligible и при frequency-sort идет первой.

---

### Use Case 4: Reset

**Сценарий:**
1. Start session
2. Check 2 cards
3. Exit → Click Reset

**Ожидание (общее для всех режимов):**
```kotlin
store.loadLastSession() == null
SessionCard скрыт
progressAfterReset == progressBeforeReset
```

**Ожидание (frequency-mode, новый Start после Reset):**
```kotlin
afterResetStartBatchIds == [
    "test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"
]
```

**Assertions:**
```kotlin
// Общее для всех режимов
assertNull("Reset should delete session", store.loadLastSession())
composeRule.onNodeWithTag("session_card").assertIsNotDisplayed()
assertEquals("Reset should keep progress", progressBeforeReset, allTodayShownIds())

// Frequency-mode: новый Start начинает колоду заново
if (sortByFrequency) {
    val afterResetStartBatchIds = startNewSession()?.cards?.map { it.id }
    val expectedFirstBatch = listOf(
        "test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"
    )
    assertEquals("After Reset, Start should begin deck from beginning",
        expectedFirstBatch, afterResetStartBatchIds)
}
```

**Примечание:** В random-mode после Reset нельзя требовать совпадения с первым batch, потому что новый старт снова делает shuffle.

---

## Acceptance Criteria

**Тест считается выполненным когда:**

1. ✅ Все 4 use case реализованы и проходят
2. ✅ Random-mode тесты проверяют только исключения/включения (не порядок)
3. ✅ Frequency-mode тесты проверяют точный порядок батчей
4. ✅ Repeat работает для обоих режимов (ordered replay)
5. ✅ Reset проверяет удаление сессии + сохранение прогресса
6. ✅ Все взаимодействия через TRUE UI clicks (без ViewModel bypass)
7. ✅ Тест запускается командой:
    ```cmd
    java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"
    ```
8. ✅ Все 4 теста стабильны (не flaky)

---

## Implementation Notes

**Что НЕ менять (эталон):**
- ❌ НЕ менять `collectAsStateWithLifecycle()` в production коде
- ❌ НЕ добавлять fallback на `verbVm.startSession()` в тесте
- ❌ НЕ использовать `Thread.sleep()` - только `waitUntil()`
- ❌ НЕ менять механику TRUE UI clicks

**Что обязательно сделать:**
- ✅ Включить `toggleSortByFrequency()` для frequency-mode тестов
- ✅ Использовать детерминированные cards: `rank = 1..12`, `ids = test_verb_1..12`
- ✅ Разделить random-mode и frequency-mode тесты
- ✅ Добавить точные assertions для frequency-mode

**Структура теста (рекомендация):**

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class VerbDrillSessionCardRegressionTest {

    // Random-mode тесты
    @Test
    fun repeat_replays_saved_batch_random_mode() { ... }

    @Test
    fun continue_random_mode_excludes_checked_only() { ... }

    // Frequency-mode тесты
    @Test
    fun continue_frequency_mode_exact_order() { ... }

    @Test
    fun reset_clears_session_keeps_progress() { ... }

    private fun preparedVerbVmInRandomMode(cards, size) { ... }
    private fun preparedVerbVmInFrequencyMode(cards, size) {
        vm.toggleSortByFrequency()
    }
}
```

**Или два отдельных класса:**

```kotlin
// Random-mode тесты
class VerbDrillSessionCardRandomModeTest { ... }

// Frequency-mode тесты
class VerbDrillSessionCardFrequencyModeTest { ... }
```

---

## References

**Требования:** `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md`

**Текущий тест:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt`

**Инструкция по сборке:** `BUILD.md` (в корне проекта)

**Команда запуска:**
```cmd
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"
```

---

## Definition of Done

- [ ] **4 use case реализованы:**
  - [ ] Repeat (random или frequency mode)
  - [ ] Continue random-mode (исключения только)
  - [ ] Continue frequency-mode (точный порядок)
  - [ ] Reset (общий + frequency-mode новый старт)

- [ ] **Random-mode assertions:**
  - [ ] Continue: проверяет исключения, не порядок
  - [ ] Repeat: проверяет ordered replay

- [ ] **Frequency-mode assertions:**
  - [ ] Continue: точный порядок `[3,4,5,6,7]`
  - [ ] Reset после нового старта: `[1,2,3,4,5]`
  - [ ] Navigation-only карточка в continue batch

- [ ] **TRUE UI clicks только:**
  - [ ] Никакого ViewModel bypass из теста
  - [ ] Все через `performClick()`

- [ ] **Production код не изменен:**
  - [ ] `collectAsStateWithLifecycle()` на месте

- [ ] **Тест запускается и проходит:**
  - [ ] Все 4 теста green
  - [ ] Стабилен при повторных запусках

- [ ] **Код закоммичен и запушен**

- [ ] **Можно утверждать:**
  - [ ] "Тест полностью покрывает SessionCard логику для обоих режимов"
  - [ ] "Frequency-mode тесты доказывают точный порядок батчей"

---

**Estimated Time:** 4-6 hours
**Complexity:** Medium-High (требует понимания Compose testing + VerbDrill domain + разделение режимов)
**Risk:** Low (test isolation, никакого production кода)

**Key Challenge:** Правильно разделить random-mode и frequency-mode тесты, не перепутать assertions (порядок vs исключения).
