# VerbDrillSessionCardRegressionTest - Требования

**Цель теста**

Главная задача теста: проверить не просто факт появления кнопок `Repeat / Continue / Reset`, а порядок карточек в батчах после реальных UI-действий пользователя.

Тест должен проходить путь через клики по экранам:

- старт практики;
- проверка ответа через `Check`;
- переходы `Next`;
- выход обратно на `VerbDrillScreen`;
- клики `Repeat`, `Continue`, `Reset`.

**Важно:** карточка считается показанной только после успешной проверки или раскрытия ответа/ошибки. Простая навигация `Next` без проверки не должна записывать карточку как пройденную.

---

## Базовые условия

Для честной проверки порядка тест должен использовать детерминированный порядок карточек.

```kotlin
val cards = createTestVerbCards(12)
// rank: 1..12
// ids: test_verb_1..test_verb_12

verbVm.setSessionSize(5)
verbVm.toggleSortByFrequency() // включить стабильный порядок
```

Если `sortByFrequency = false`, внутри `startSession()` используется `shuffled()`, и тогда нельзя проверять точный порядок батча. В этом случае можно проверять только включение/исключение карточек, но не `batch == [1,2,3,4,5]`.

---

## Текущий статус теста и ограничения

**Что тест проверяет честно сейчас:**

✅ **Repeat** - Проверяет порядок честно. Берет `firstBatchIds`, кликает `Repeat`, сравнивает `repeatedBatchIds == firstBatchIds`. Даже если первый батч был случайный, Repeat обязан повторить именно его. Это покрыто нормально.

✅ **Continue (частично)** - Проверяет что checked-карточки исключены, navigation-only карточка не записалась в `todayShownCardIds`. Но **НЕ проверяет порядок нового батча** и не проверяет что navigation-only карточка реально попала следующей в continue batch.

✅ **Reset (частично)** - Проверяет что `SessionCard` скрыт, `lastSession` удален, прогресс не очищен. Но **НЕ проверяет что после Reset новый Start снова начинает колоду с начала**.

**Главная проблема:**

Тест не включает `sortByFrequency`. В текущей ViewModel при `sortByFrequency = false` порядок батча идет через:

```kotlin
remaining.shuffled().take(sessionSize)
```

这意味着 точный порядок `[1,2,3,4,5]`, потом `[3,4,5,6,7]`, потом после reset снова `[1,2,3,4,5]` тест **сейчас не может доказать**. Тест может пройти зеленым, даже если порядок Continue/Reset будет неправильный, потому что таких assertions нет.

**Что можно сказать сейчас:**

✅ Тест подтверждает базовую механику SessionCard:
- Repeat повторяет сохраненный батч
- Continue не считает navigation-only карточку shown
- Reset удаляет сессию и сохраняет прогресс

❌ Тест **НЕ** полностью гарантирует мобильную логику порядка батчей во всех режимах.

**Что нужно добавить для полной валидации:**

1. Включить стабильный порядок перед стартом:
   - Либо через UI-клик по `sortByFrequency` checkbox
   - Либо явно в VM: `verbVm.toggleSortByFrequency()`

2. Добавить точные assertions на порядок:

```kotlin
// Use Case 1: Repeat
assertEquals(
    listOf("test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"),
    firstBatchIds
)

// Use Case 2: Continue
assertEquals(
    listOf("test_verb_3", "test_verb_4", "test_verb_5", "test_verb_6", "test_verb_7"),
    continueBatchIds
)

// Use Case 3: Reset
assertEquals(
    firstBatchIds,
    batchAfterResetStart
)
```

**Примечание про exitTrainingThroughUi:**

Выход из тренировки сделан через helper `exitTrainingThroughUi()`, который вызывает `persistSessionState()` и `refreshLastSessionContext()`. Это не клик по реальной кнопке выхода.

Для проверки SessionCard-логики это приемлемо, но это **не full mobile E2E**. Полный E2E тест включал бы клик по реальной кнопке "Back" или "Exit" на TrainingScreen.

---

## Use Case 1: Repeat

**Сценарий:**

1. Пользователь входит в Verb Practice.
2. Выбирает фильтры.
3. Нажимает `Start`.
4. Получает первый батч из 5 карточек:

```kotlin
[test_verb_1, test_verb_2, test_verb_3, test_verb_4, test_verb_5]
```

5. Проверяет первые 3 карточки через `Check`.
6. Выходит обратно на `VerbDrillScreen`.
7. Видит `SessionCard`.
8. Нажимает `Repeat`.

**Ожидаемое поведение:**

```kotlin
repeatBatch == [test_verb_1, test_verb_2, test_verb_3, test_verb_4, test_verb_5]
```

`Repeat` должен replay-нуть ровно последний сохраненный батч в том же порядке. Он не должен строить новый батч и не должен исключать уже checked-карточки.

**Assertion:**

```kotlin
assertThat(repeatBatchIds).isEqualTo(firstBatchIds)
```

---

## Use Case 2: Continue

**Сценарий:**

1. Первый батч:

```kotlin
[test_verb_1, test_verb_2, test_verb_3, test_verb_4, test_verb_5]
```

2. Пользователь делает `Check` на карточках 1 и 2.
3. На карточке 3 пользователь только нажимает `Next`, без `Check`.
4. Выходит обратно на `VerbDrillScreen`.
5. Нажимает `Continue`.

**Ожидаемая логика:**

```kotlin
shownToday == [test_verb_1, test_verb_2]
navigationOnlyCard == test_verb_3
```

Карточка `test_verb_3` не считается показанной, потому что по ней не было проверки.

При `Continue` новый батч должен исключить только checked-карточки:

```kotlin
continueBatch == [test_verb_3, test_verb_4, test_verb_5, test_verb_6, test_verb_7]
```

То есть `Continue` продолжает колоду с учетом прогресса, но не выкидывает карточки, которые пользователь просто пролистал.

**Assertion:**

```kotlin
assertThat(continueBatchIds).isEqualTo(
    listOf("test_verb_3", "test_verb_4", "test_verb_5", "test_verb_6", "test_verb_7")
)
```

---

## Use Case 3: Reset

**Сценарий:**

1. Первый батч:

```kotlin
[test_verb_1, test_verb_2, test_verb_3, test_verb_4, test_verb_5]
```

2. Пользователь проверяет карточки 1 и 2.
3. Выходит обратно.
4. Нажимает `Reset`.

**Ожидаемое поведение:**

```kotlin
lastSession == null
SessionCard скрыт
shownToday сохраняется: [test_verb_1, test_verb_2]
```

`Reset` сбрасывает именно текущую сессию Verb Practice, но не стирает факт, что карточки уже были показаны/проверены.

После Reset новый `Start` должен начинать колоду заново.

**Assertion:**

```kotlin
assertThat(afterResetStartBatchIds).isEqualTo(firstBatchIds)
assertThat(progressAfterReset).containsExactly("test_verb_1", "test_verb_2")
```

---

## Альтернативы

1. **Если сохраненной сессии нет** - `SessionCard` не показывается, пользователь видит обычный выбор фильтров и `Start`.

2. **Если сохраненная сессия битая** (например `sessionCardIds` больше нет в текущем паке) - ее нужно игнорировать/удалить и не показывать `SessionCard`.

3. **Если при `Continue` все карточки уже checked** - новый батч не должен создаваться, состояние должно уходить в `allDoneToday`.

4. **Если `Repeat` вызывается на legacy-сессии без валидных `sessionCardIds`** - точный replay невозможен. Тогда допустим fallback на новый старт, но такой кейс отдельно не должен маскироваться под проверку порядка.

5. **Если пользователь открыл ответ/подсказку и потом нажал `Next`** - карточка должна считаться показанной. Если он просто нажал `Next` без проверки/ответа, карточка не считается показанной.

---

## Что тест обязан доказать

Главная проверка должна быть такой:

```kotlin
// Use Case 1: Repeat
assertThat(repeatBatchIds).isEqualTo(firstBatchIds)

// Use Case 2: Continue
assertThat(continueBatchIds).isEqualTo(
    listOf("test_verb_3", "test_verb_4", "test_verb_5", "test_verb_6", "test_verb_7")
)

// Use Case 3: Reset
assertThat(afterResetStartBatchIds).isEqualTo(firstBatchIds)
assertThat(progressAfterReset).containsExactly("test_verb_1", "test_verb_2")
```

Если в тесте не включен стабильный порядок, то он не доказывает главную цель. Тогда он проверяет только "какие карточки исключены", но не "в каком порядке пользователь реально увидит батчи".

---

## Технические требования (эталон реализации)

**TRUE UI Clicks Only:**

Все взаимодействия должны проходить через UI клики:

- ✅ Клик через `composeRule.onNodeWithTag("verb_start_button").performClick()`
- ✅ Клик через `composeRule.onNodeWithTag("check_button").performClick()`
- ✅ Клик через `composeRule.onNodeWithTag("next_button").performClick()`
- ❌ НЕЛЬЗЯ: `verbVm.startSession()` напрямую из теста
- ❌ НЕЛЬЗЯ: `trainingVm.startVerbDrillSession()` напрямую из теста

**Синхронизация состояния:**

- Использовать `waitUntil()` с timeout для ожидания state changes
- Использовать `waitForIdle()` после каждого UI действия
- Ждать `lastSessionContext == null` перед кликом на start button
- Ждать `session?.cards?.isNotEmpty() == true` после клика на start button

**Детерминированный порядок:**

- Использовать `toggleSortByFrequency()` для включения стабильного порядка
- Использовать карточки с детерминированными `rank` (1, 2, 3, ...)
- Использовать предсказуемые `id` (test_verb_1, test_verb_2, ...)

**Чтение состояния для assertions:**

- ✅ МОЖНО читать `currentCard.acceptedAnswers.first()` для ввода правильного ответа
- ✅ МОЖНО читать `session.cards.map { it.id }` для проверки порядка
- ✅ МОЖНО читать `store.loadLastSession()` для проверки persistence
- ❌ НЕЛЬЗЯ мутировать состояние напрямую из теста

---

## Статус реализации

**Текущий статус:** ✅ Ready (fixes applied, committed, pushed)

**Файл:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt`

**Последние изменения:**
- Добавлен wait для session creation после клика кнопки
- Убраны все debug output
- Исправлена синхронизация с lastSessionContext
- Production код использует `collectAsStateWithLifecycle()` (не изменен)

**Команды для запуска:**

```cmd
# Из BUILD.md
build.bat test --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"

# Полностью
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain test --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"
```
