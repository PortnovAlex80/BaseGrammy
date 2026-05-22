# VerbDrillSessionCardRegressionTest - требования

## Цель

Тест должен проверять поведение `SessionCard` в Verb Practice после реальных UI-действий пользователя:

- старт практики через `Start`;
- ввод ответа и клик `Check`;
- переходы через `Next`;
- возврат на `VerbDrillScreen`;
- клики `Repeat`, `Continue`, `Reset`.

Главный инвариант: карточка считается показанной только после проверки ответа или раскрытия ответа/ошибки. Простая навигация `Next` без проверки не должна добавлять карточку в `todayShownCardIds`.

## Важное про порядок

В Verb Drill есть два разных режима порядка карточек:

```kotlin
val selected = if (state.sortByFrequency) {
    remaining.sortedBy { it.rank ?: Int.MAX_VALUE }.take(sessionSize)
} else {
    remaining.shuffled().take(sessionSize)
}
```

Поэтому требования к тестам разные:

- `sortByFrequency = true`: порядок детерминированный, можно и нужно проверять точные списки batch ids.
- `sortByFrequency = false`: порядок намеренно случайный, нельзя проверять точный список `[1,2,3,4,5]`; можно проверять только состав, исключения и сохранение/повтор уже выбранного батча.

Если тест утверждает точный порядок `Continue` или нового `Start` после `Reset`, он обязан включить `sortByFrequency` через UI-клик по checkbox или явно подготовить VM до старта сценария.

## Use Case 1: Repeat

Сценарий:

1. Пользователь стартует Verb Practice.
2. Приложение создает первый batch.
3. Пользователь проходит несколько карточек через `Check`.
4. Пользователь возвращается на `VerbDrillScreen`.
5. Пользователь кликает `Repeat`.

Ожидаемое поведение:

```kotlin
repeatBatchIds == firstBatchIds
```

`Repeat` всегда должен воспроизводить сохраненный `lastSession.sessionCardIds` в том же порядке. Это верно и для random-mode, и для frequency-mode: даже если первый batch был случайным, после сохранения он стал конкретным ordered list.

## Use Case 2: Continue в random-mode

Сценарий:

1. Пользователь стартует batch.
2. Делает `Check` на первых двух карточках.
3. На следующей карточке нажимает только `Next`, без `Check`.
4. Возвращается на `VerbDrillScreen`.
5. Кликает `Continue`.

Ожидаемое поведение:

```kotlin
shownToday == checkedCardIds
navigationOnlyCardId !in shownToday
continueBatchIds.intersect(checkedCardIds).isEmpty()
```

В random-mode не проверяем точный порядок `continueBatchIds`, потому что `remaining.shuffled()` является ожидаемой продуктовой логикой.

## Use Case 3: Continue в frequency-mode

Сценарий использует карточки с rank `1..12`, session size `5`, и включает `sortByFrequency`.

Первый batch:

```kotlin
["test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"]
```

Пользователь делает `Check` на `test_verb_1` и `test_verb_2`, затем только `Next` на `test_verb_3`.

Ожидаемое поведение после `Continue`:

```kotlin
continueBatchIds == listOf(
    "test_verb_3",
    "test_verb_4",
    "test_verb_5",
    "test_verb_6",
    "test_verb_7"
)
```

Смысл: `Continue` исключает только checked/shown карточки. Карточка, которую пользователь только пролистал, остается eligible и при frequency-sort должна идти первой среди remaining.

## Use Case 4: Reset

Сценарий:

1. Пользователь стартует batch.
2. Проверяет несколько карточек через `Check`.
3. Возвращается на `VerbDrillScreen`.
4. Кликает `Reset`.

Ожидаемое поведение:

```kotlin
store.loadLastSession() == null
SessionCard скрыт
progressAfterReset == progressBeforeReset
```

`Reset` удаляет только сохраненную сессию, но не стирает прогресс Verb Drill.

После `Reset` новый `Start` начинает проход колоды заново, потому что обычный старт вызывает `startSession(..., ignoreTodayShown = true)`.

Для проверки точного порядка после `Reset` нужен frequency-mode:

```kotlin
afterResetStartBatchIds == listOf(
    "test_verb_1",
    "test_verb_2",
    "test_verb_3",
    "test_verb_4",
    "test_verb_5"
)
```

В random-mode после `Reset` нельзя требовать совпадения с первым batch, потому что новый старт снова делает shuffle.

## Альтернативы

1. Если сохраненной сессии нет, `SessionCard` не показывается.
2. Если сохраненная сессия содержит card ids, которых больше нет в текущем pack, ее нужно удалить/игнорировать.
3. Если при `Continue` все eligible карточки уже shown, новая сессия не создается и состояние переходит в `allDoneToday`.
4. Если `Repeat` вызывается на legacy-сессии без валидных `sessionCardIds`, точный replay невозможен; допустим fallback на fresh start, но этот кейс не должен маскироваться под проверку порядка.
5. Если пользователь открыл ответ/подсказку и нажал `Next`, карточка считается показанной. Если пользователь просто нажал `Next`, карточка не считается показанной.

## Технические требования

- Основной сценарий должен идти через UI clicks: `verb_start_button`, `check_button`, `next_button`, `repeat_button`, `continue_button`, `reset_button`.
- Нельзя заменять старт/continue/repeat/reset прямыми вызовами `verbVm.startSession()`, `verbVm.onResumeSession()`, `verbVm.onRepeatSession()`, `verbVm.onStartFresh()` из тела теста.
- Читать состояние для assertions можно: `session.cards.map { it.id }`, `store.loadLastSession()`, `store.loadProgress()`, `currentCard.acceptedAnswers.first()`.
- Мутировать состояние напрямую из теста нельзя, кроме подготовки fixture до начала UI-сценария.
- Для точных assertions порядка использовать deterministic fixture: `rank = 1..N`, стабильные ids `test_verb_1..N`, `sessionSize = 5`, `sortByFrequency = true`.

## Что должен доказывать итоговый набор тестов

```kotlin
// Repeat: ordered replay of saved batch, works even when original batch was random.
assertThat(repeatBatchIds).isEqualTo(firstBatchIds)

// Continue random-mode: checked cards are excluded, navigation-only card is not marked shown.
assertThat(continueBatchIds.intersect(checkedCardIds)).isEmpty()
assertThat(navigationOnlyCardId).isNotIn(store.loadLastSession()!!.todayShownCardIds)

// Continue frequency-mode: exact next ordered batch.
assertThat(continueBatchIds).isEqualTo(
    listOf("test_verb_3", "test_verb_4", "test_verb_5", "test_verb_6", "test_verb_7")
)

// Reset: session is cleared, progress remains.
assertThat(store.loadLastSession()).isNull()
assertThat(progressAfterReset).isEqualTo(progressBeforeReset)

// Reset frequency-mode: fresh start begins from rank start again.
assertThat(afterResetStartBatchIds).isEqualTo(
    listOf("test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5")
)
```

## Запуск

Для Android unit test task:

```cmd
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"
```

Команда `test --tests ...` может не работать в этом проекте, потому что верхнеуровневая Android task не принимает `--tests`. Использовать `:app:testDebugUnitTest --tests ...`.
