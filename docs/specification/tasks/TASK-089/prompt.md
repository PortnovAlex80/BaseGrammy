# TASK-089: VerbDrill SessionCard Regression Test v2

## Контекст проекта

GrammarMate — Android приложение для изучения грамматики (Kotlin, Jetpack Compose, Material 3).
Путь к проекту: D:\Development\BaseGrammy
Ветка: main
Build команда (Windows): 
```cmd
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"
```
APK путь: app/build/outputs/apk/debug/grammermate.apk

## Что сделано

**VerbDrillSessionCardRegressionTest v1** (текущая версия):
- ✅ TRUE UI clicks через Compose testing API
- ✅ Repeat: проверяет порядок честно (даже со случайным батчем)
- ⚠️ Continue: только random-mode (проверяет исключения, не порядок)
- ⚠️ Reset: только удаление сессии (без проверки нового старта)

**Требования обновлены:**
- 📄 `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md` - полная спецификация с 4 use cases
- Разделение на random-mode и frequency-mode
- Random-mode: проверяем только включения/исключения (shuffled ожидаемо)
- Frequency-mode: проверяем точный порядок (детерминированный)

**Спецификация проверена на соответствие коду:**
- ✅ VerbDrillViewModel.onRepeatSession() - воспроизводит sessionCardIds в сохраненном порядке
- ✅ VerbDrillViewModel.onResumeSession() - исключает todayShownCardIds
- ✅ VerbDrillViewModel.onStartFresh() - удаляет сессию, сохраняет прогресс
- ✅ SessionState.startSession() - использует sortedBy vs shuffled по sortByFrequency

## Что НЕ доделано (твоя задача)

### 1. Написать VerbDrillSessionCardRegressionTest v2

**Проблема:** Текущий тест (v1) покрывает только часть требований:
- Не включен `sortByFrequency` - все в random-mode
- Нет проверки Continue в frequency-mode с точным порядком
- Нет проверки нового Start после Reset
- Только 3 use case, должно быть 4

**Решение:** Написать новую версию теста с 4 use cases:

**Use Case 1: Repeat** (работает для random и frequency modes)
- Сценарий: Start → Check 3 cards → Exit → Click Repeat
- Ожидание: `repeatBatchIds == firstBatchIds`
- Примечание: Ordered replay сохраненного батча (даже если оригинал был случайным)

**Use Case 2: Continue в random-mode**
- Сценарий: Start → Check cards 1,2 → Next only on card 3 → Exit → Click Continue
- Ожидание: 
  ```kotlin
  shownToday == [1,2] // card 3 NOT included
  navigationOnlyCardId !in shownToday
  continueBatchIds.intersect(checkedCardIds).isEmpty()
  ```
- Примечание: НЕ проверяем точный порядок (shuffled ожидаемо)

**Use Case 3: Continue в frequency-mode** (НОВЫЙ)
- Сценарий: Start с `sortByFrequency=true` → Check test_verb_1,2 → Next on test_verb_3 → Exit → Click Continue
- Ожидание:
  ```kotlin
  continueBatchIds == [test_verb_3, test_verb_4, test_verb_5, test_verb_6, test_verb_7]
  ```
- Примечание: Navigation-only карточка идет первой при frequency-sort

**Use Case 4: Reset**
- Сценарий: Start → Check 2 cards → Exit → Click Reset
- Общее ожидание: `lastSession == null`, `progressAfterReset == progressBeforeReset`
- Frequency-mode ожидание: После Reset новый Start → `[test_verb_1..5]` (колода с начала)

### 2. Структура теста

**Вариант A:** Один класс с 4 тестами (предпочтительно)
```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class VerbDrillSessionCardRegressionTest {
    @Test
    fun repeat_replays_saved_batch() { ... }
    
    @Test
    fun continue_random_mode_excludes_checked_only() { ... }
    
    @Test
    fun continue_frequency_mode_exact_order() { ... }
    
    @Test
    fun reset_clears_session_keeps_progress() { ... }
}
```

**Вариант B:** Два отдельных класса
```kotlin
class VerbDrillSessionCardRandomModeTest { ... }
class VerbDrillSessionCardFrequencyModeTest { ... }
```

### 3. Технические требования

**TRUE UI Clicks (сохранить из v1):**
- ✅ Все взаимодействия через `performClick()` на кнопках
- ✅ Никаких прямых вызовов `verbVm.startSession()` из теста
- ✅ Синхронизация через `waitUntil()` с timeout
- ✅ Чтение state для assertions (не для мутации)

**Детерминированные fixture для frequency-mode:**
```kotlin
val cards = createTestVerbCards(12) // rank: 1..12, ids: test_verb_1..12
verbVm.setSessionSize(5)
verbVm.toggleSortByFrequency() // для frequency-mode тестов
```

**Assertions:**

*Random-mode (Continue):*
```kotlin
val shownAfterNavigation = store.loadLastSession()!!.todayShownCardIds
assertEquals(2, shownAfterNavigation.size)
assertFalse(navigationOnlyCardId in shownAfterNavigation)

val continueBatchIds = verbVm.uiState.value.session!!.cards.map { it.id }
assertTrue("Continue must exclude checked cards",
    continueBatchIds.intersect(checkedCardIds).isEmpty())
```

*Frequency-mode (Continue):*
```kotlin
val expectedContinueBatch = listOf(
    "test_verb_3", "test_verb_4", "test_verb_5", "test_verb_6", "test_verb_7"
)
assertEquals("Continue should produce exact next ordered batch",
    expectedContinueBatch, continueBatchIds)

assertTrue("Navigation-only card should be in continue batch",
    navigationOnlyCardId in continueBatchIds)
```

*Reset (frequency-mode):*
```kotlin
// Общее для всех режимов
assertNull("Reset should delete session", store.loadLastSession())
assertEquals("Reset should keep progress", progressBeforeReset, allTodayShownIds())

// Frequency-mode: новый Start начинает колоду заново
val afterResetStartBatchIds = startNewSession()?.cards?.map { it.id }
val expectedFirstBatch = listOf(
    "test_verb_1", "test_verb_2", "test_verb_3", "test_verb_4", "test_verb_5"
)
assertEquals("After Reset, Start should begin deck from beginning",
    expectedFirstBatch, afterResetStartBatchIds)
```

## Критерии приемки

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

- [ ] **Можно утверждать:**
  - [ ] "Тест полностью покрывает SessionCard логику для обоих режимов"
  - [ ] "Frequency-mode тесты доказывают точный порядок батчей"

## Ссылки

**Требования:** `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md`

**Текущий тест v1:** `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt`

**Инструкция по сборке:** `BUILD.md` (в корне проекта)

**Build команда:**
```cmd
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"
```

## Примечания

**Ключевой инсайт:** Разница между random-mode и frequency-mode assertions

- **Random-mode** (`sortByFrequency = false`): проверяем включения/исключения (shuffled ожидаемо)
- **Frequency-mode** (`sortByFrequency = true`): проверяем точный порядок (детерминированный)

**Важное:** Не перепутать assertions! Проверять порядок в random-mode = тест будет flaky из-за shuffle.

**Estimated Time:** 4-6 hours
**Complexity:** Medium-High
**Risk:** Low (test isolation, никакого production кода)
