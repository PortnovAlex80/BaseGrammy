# Задача: Обновить VerbDrillSessionCardRegressionTest

**Статус:** 🔄 Ready to Start
**Приоритет:** High
**Назначена:** TBD
**Создана:** 2025-05-22

---

## Context

Тест `VerbDrillSessionCardRegressionTest` был создан для проверки функциональности SessionCard кнопок (Repeat/Continue/Reset). Тест использует TRUE UI clicks и правильно моделирует путь пользователя через экраны приложения.

**Важно:** Тест полезный и частично честный, но **НЕ полностью доказывает главное требование про порядок карточек во всех режимах**. Нельзя сказать "на мобиле точно все ок с логикой порядка" на основе текущей версии.

Требования к тесту задокументированы в: `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md`

---

## Current Status

**Что тест проверяет честно сейчас:**

✅ **Repeat** - Проверяет порядок честно. Берет `firstBatchIds`, кликает `Repeat`, сравнивает `repeatedBatchIds == firstBatchIds`. Даже если первый батч был случайный, Repeat обязан повторить именно его.

✅ **Continue (частично)** - Проверяет что checked-карточки исключены, navigation-only карточка не записалась в `todayShownCardIds`. Но **НЕ проверяет порядок нового батча** и не проверяет что navigation-only карточка реально попала следующей в continue batch.

✅ **Reset (частично)** - Проверяет что `SessionCard` скрыт, `lastSession` удален, прогресс не очищен. Но **НЕ проверяет что после Reset новый Start снова начинает колоду с начала**.

**Главная проблема:**

Тест не включает `sortByFrequency`. В текущей ViewModel при `sortByFrequency = false` порядок батча идет через `shuffled()`. Это значит точный порядок батчей тест **сейчас не может доказать**.

**Что можно сказать сейчас:**
- ✅ Тест подтверждает базовую механику SessionCard
- ❌ Тест **НЕ** полностью гарантирует мобильную логику порядка батчей

**Эталонные механики (сохранить):**
- ✅ TRUE UI clicks через Compose testing API
- ✅ Правильная синхронизация с async state updates
- ✅ Production код не изменен (`collectAsStateWithLifecycle()`)
- ✅ Чтение state только для assertions, не для мутации

**Примечание про exitTrainingThroughUi:**

Выход из тренировки сделан через helper `exitTrainingThroughUi()`, который вызывает `persistSessionState()` и `refreshLastSessionContext()`. Это не клик по реальной кнопке выхода.

Для проверки SessionCard-логики это приемлемо, но это **не full mobile E2E**.

---

## Task Requirements

### Goal

Обновить тест `VerbDrillSessionCardRegressionTest` чтобы он:

1. **Включал детерминированный порядок** через `sortByFrequency = true`
2. Проверял **точный порядок карточек в батчах** (не только включение/исключение)
3. Доказывал что поведение соответствует требованиям для всех 3 use cases
4. Оставался TRUE UI clicks тестом (без ViewModel bypass)

### Что именно нужно сделать

**Шаг 1: Включить стабильный порядок**

Добавить в `preparedVerbVm()` или в начало каждого теста:

```kotlin
verbVm.toggleSortByFrequency() // включить детерминированный порядок
```

Или через UI-клик если есть checkbox с testTag.

**Шаг 2: Добавить точные assertions на порядок**

```kotlin
// Use Case 1: Repeat
val expectedFirstBatch = listOf("test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5")
assertEquals(expectedFirstBatch, firstBatchIds)
assertEquals(expectedFirstBatch, repeatedBatchIds)

// Use Case 2: Continue
val expectedContinueBatch = listOf("test_verb_3", "test_verb_4", "test_verb_5", "test_verb_6", "test_verb_7")
assertEquals(expectedContinueBatch, continueBatchIds)

// Use Case 3: Reset
assertEquals(expectedFirstBatch, batchAfterResetStart)
```

**Шаг 3: Убедиться что navigation-only карточка попадает в continue batch**

Добавить assertion:

```kotlin
assertTrue("Navigation-only card should be in continue batch",
    navigationOnlyCardId in continueBatchIds)
```

**Сохранить (эталонные механики):**
- ✅ TRUE UI clicks только через `performClick()`
- ✅ Синхронизация через `waitUntil()` с timeout
- ✅ Чтение state только для assertions, не для мутации
- ✅ Production код использует `collectAsStateWithLifecycle()`
- ✅ Helper `exitTrainingThroughUi()` допустим (не full E2E, но OK для SessionCard логики)

### Specific Use Cases

#### Use Case 1: Repeat
```kotlin
// Scenario:
Start → Check 3 cards → Exit → Click Repeat

// Expected:
repeatBatch == [1,2,3,4,5] // same as first batch

// Required assertion:
assertThat(repeatBatchIds).isEqualTo(firstBatchIds)
```

#### Use Case 2: Continue
```kotlin
// Scenario:
Start → Check cards 1,2 → Next on card 3 → Exit → Click Continue

// Expected:
shownToday == [1,2] // card 3 NOT included
continueBatch == [3,4,5,6,7] // excludes 1,2 but includes 3

// Required assertion:
assertThat(continueBatchIds).isEqualTo(listOf("3","4","5","6","7"))
assertThat(navigationOnlyCard in shownToday).isFalse()
```

#### Use Case 3: Reset
```kotlin
// Scenario:
Start → Check cards 1,2 → Exit → Click Reset → Start again

// Expected:
SessionCard hidden after Reset
lastSession == null
progress preserved: [1,2]
newBatch == [1,2,3,4,5] // starts deck from beginning

// Required assertion:
assertThat(afterResetStartBatchIds).isEqualTo(firstBatchIds)
assertThat(progressAfterReset).containsExactly("1","2")
```

---

## Acceptance Criteria

**Тест считается выполненным когда:**

1. ✅ Все 3 теста проходят:
   - `repeat_replays_last_batch_after_checked_cards`
   - `continue_excludes_checked_cards_but_not_navigation_only_cards`
   - `reset_hides_session_card_but_keeps_progress`

2. ✅ **Детерминированный порядок включен:**
   - `verbVm.toggleSortByFrequency()` вызван перед стартом
   - ИЛИ через UI-клик если есть checkbox с testTag
   - Порядок батчей предсказуем: [1,2,3,4,5], [3,4,5,6,7], etc.

3. ✅ **Assertions проверяют точный порядок батчей:**
   ```kotlin
   // Use Case 1: Repeat
   assertEquals(listOf("test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"), firstBatchIds)
   assertEquals(firstBatchIds, repeatedBatchIds)

   // Use Case 2: Continue
   assertEquals(listOf("test_verb_3", "test_verb_4", "test_verb_5", "test_verb_6", "test_verb_7"), continueBatchIds)
   assertTrue(navigationOnlyCardId in continueBatchIds) // navigation-only card included

   // Use Case 3: Reset
   assertEquals(firstBatchIds, batchAfterResetStart)
   assertEquals(shownBeforeReset, allTodayShownIds()) // progress preserved
   ```

4. ✅ Тест использует **только TRUE UI clicks** (никакого ViewModel bypass из теста)

5. ✅ Production код не изменен (VerbDrillScreen использует `collectAsStateWithLifecycle()`)

6. ✅ Тест запускается и проходит локально:
   ```cmd
   build.bat test --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"
   ```

7. ✅ Тест стабилен (не flaky) при повторных запусках

8. ✅ Можно сказать: "Тест полностью гарантирует мобильную логику порядка батчей во всех режимах"

---

## Implementation Notes

**Что НЕ менять:**
- ❌ НЕ менять `collectAsStateWithLifecycle()` на `collectAsState()` в production коде
- ❌ НЕ добавлять fallback на `verbVm.startSession()` в тесте
- ❌ НЕ использовать `Thread.sleep()` - только `waitUntil()`
- ❌ НЕ менять механику TRUE UI clicks - это наш эталон

**Что проверить перед стартом:**
- ✅ Прочитать `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md`
- ✅ Понять разницу между checked cards (через Check) и navigation-only cards (через Next)
- ✅ Убедиться что `sortByFrequency = true` для детерминированного порядка

**Отладка:**
- Если тест падает на кнопке - проверить что `lastSessionContext == null`
- Если тест падает на session - проверить что wait для session creation есть
- Если порядок не совпадает - проверить что `toggleSortByFrequency()` вызван

---

## References

**Требования:** `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md`

**Текущий тест:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt`

**Инструкция по сборке:** `BUILD.md` (в корне проекта)

**Build команды:**
```cmd
# Запуск теста
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain test --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"

# Сборка APK
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

---

## Definition of Done

- [ ] **Детерминированный порядок включен** (`sortByFrequency = true`)
- [ ] **Тест запускается и проходит** (все 3 test case green)
- [ ] **Assertions проверяют точный порядок батчей:**
  - [ ] Repeat: `firstBatchIds == repeatedBatchIds`
  - [ ] Continue: `continueBatchIds == [3,4,5,6,7]`
  - [ ] Reset: `batchAfterReset == firstBatchIds`
  - [ ] Navigation-only card в continue batch
- [ ] **TRUE UI clicks только** (без fallback на ViewModel)
- [ ] **Production код не изменен** (`collectAsStateWithLifecycle()`)
- [ ] **Тест стабилен** при повторных запусках (не flaky)
- [ ] **Код закоммичен и запушен** в main
- [ ] **APK собирается успешно**
- [ ] **Можно утверждать:** "Тест полностью гарантирует мобильную логику порядка батчей"

---

**Estimated Time:** 2-4 hours
**Complexity:** Medium (requires understanding of Compose testing + VerbDrill domain logic + deterministic ordering)
**Risk:** Low (test isolation, no production changes)

**Key Challenge:** Понять разницу между "тест зеленый" и "тест доказывает порядок батчей". Текущий тест зеленый, но не доказывает порядок. Нужно добавить assertions на точный порядок.
