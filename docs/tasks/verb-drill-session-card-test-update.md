# Задача: Обновить VerbDrillSessionCardRegressionTest

**Статус:** 🔄 Ready to Start
**Приоритет:** High
**Назначена:** TBD
**Создана:** 2025-05-22

---

## Context

Тест `VerbDrillSessionCardRegressionTest` был создан для проверки функциональности SessionCard кнопок (Repeat/Continue/Reset). Тест использует TRUE UI clicks и правильно моделирует путь пользователя через экраны приложения.

Требования к тесту задокументированы в: `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md`

---

## Current Status

**Что уже работает (эталон):**
- ✅ TRUE UI clicks через Compose testing API
- ✅ Правильная синхронизация с async state updates
- ✅ Детерминированный порядок карточек через `sortByFrequency`
- ✅ Проверка Repeat/Continue/Reset через реальные UI клики
- ✅ Production код не изменен (`collectAsStateWithLifecycle()`)

**Что требует обновления:**
- ❌ Тест не был запущен из-за отсутствия Java в CI среде
- ❌ Требуется верификация что assertions проверяют порядок батчей
- ❌ Требуется проверка что все 3 use case работают корректно

---

## Task Requirements

### Goal

Обновить тест `VerbDrillSessionCardRegressionTest` чтобы он:

1. Проходил через весь путь пользователя: Start → Check/Next → Exit → Repeat/Continue/Reset
2. Проверял **порядок карточек в батчах**, а не только факт их существования
3. Использовал **только TRUE UI clicks** (без ViewModel bypass)
4. Доказывал что поведение соответствует требованиям из спецификации

### Technical Requirements

**Сохранить (эталонные механики):**
- ✅ TRUE UI clicks только через `performClick()`
- ✅ Синхронизация через `waitUntil()` с timeout
- ✅ Детерминированный порядок через `toggleSortByFrequency()`
- ✅ Чтение state только для assertions, не для мутации
- ✅ Production код использует `collectAsStateWithLifecycle()`

**Обновить:**
- 🔄 Добавить assertions на точный порядок батчей
- 🔄 Проверить что Repeat replay-ит последний batch в том же порядке
- 🔄 Проверить что Continue исключает только checked карточки
- 🔄 Проверить что Reset сохраняет прогресс
- 🔄 Убедиться что navigation-only карточки не считаются показанными

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

2. ✅ Assertions проверяют **точный порядок** батчей, а не только включение/исключение

3. ✅ Тест использует **только TRUE UI clicks** (никакого ViewModel bypass)

4. ✅ Production код не изменен (VerbDrillScreen использует `collectAsStateWithLifecycle()`)

5. ✅ Тест запускается и проходит локально:
   ```cmd
   build.bat test --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"
   ```

6. ✅ Тест стабилен (не flaky) при повторных запусках

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

- [ ] Тест запускается и проходит (все 3 test case green)
- [ ] Assertions проверяют порядок батчей (не только включение/исключение)
- [ ] TRUE UI clicks только (без fallback)
- [ ] Production код не изменен
- [ ] Тест стабилен при повторных запусках
- [ ] Код закоммичен и запушен в main
- [ ] APK собирается успешно
- [ ] Документация обновлена (если нужно)

---

**Estimated Time:** 2-4 hours
**Complexity:** Medium (requires understanding of Compose testing + VerbDrill domain logic)
**Risk:** Low (test isolation, no production changes)
