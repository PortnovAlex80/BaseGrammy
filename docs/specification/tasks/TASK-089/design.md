# TASK-089 Design: VerbDrill SessionCard Regression Test v2

## Текущая архитектура

### VerbDrill SessionCard Mechanics

**Repeat:**
```kotlin
// VerbDrillViewModel.kt:759
fun onRepeatSession() {
    val lastSession = loadValidLastSession()
    // Restore filters including sortByFrequency
    _uiState.update { state ->
        state.copy(
            sortByFrequency = lastSession.sortByFrequency,
            // ...
        )
    }
    val repeatCards = lastSession.sessionCardIds.mapNotNull { cardsById[it] }
    // Replay in saved order
}
```

**Continue:**
```kotlin
// VerbDrillViewModel.kt:734
fun onResumeSession() {
    val lastSession = loadValidLastSession()
    val excludedCardIds = lastSession.todayShownCardIds
    startSession(excludedCardIds, ignoreTodayShown = false)
}
```

**Reset:**
```kotlin
// VerbDrillViewModel.kt:809
fun onStartFresh() {
    verbDrillStore.deleteLastSession()
    // Does NOT clear todayShownCardIds (progress preserved)
}
```

### Batch Order Logic

```kotlin
// VerbDrillViewModel.kt:483
val selected = if (state.sortByFrequency) {
    remaining.sortedBy { it.rank ?: Int.MAX_VALUE }.take(sessionSize)
} else {
    remaining.shuffled().take(sessionSize)
}
```

**Ключевой момент:**
- `sortByFrequency = true` → детерминированный порядок (по rank)
- `sortByFrequency = false` → случайный порядок (shuffled)

## Тестовая архитектура v2

### Разделение на режимы

**Random-mode тесты:**
- Проверяют: включения/исключения карточек
- НЕ проверяют: точный порядок (shuffled ожидаемо)
- Assertions: `intersect(checkedCardIds).isEmpty()`

**Frequency-mode тесты:**
- Проверяют: точный порядок батчей
- Используют: `toggleSortByFrequency()`
- Assertions: `assertEquals([3,4,5,6,7], continueBatchIds)`

### Fixture дизайн

**Детерминированные карточки:**
```kotlin
fun createTestVerbCards(count: Int): List<VerbDrillCard> {
    return (1..count).map { i ->
        VerbDrillCard(
            id = "test_verb_$i",
            rank = i, // Детерминированный для frequency-mode
            tense = "Presente",
            group = "regular_are",
            // ...
        )
    }
}
```

**Настройка ViewModel:**
```kotlin
// Random-mode
verbVm.setSessionSize(5)
// sortByFrequency остается false (default)

// Frequency-mode
verbVm.setSessionSize(5)
verbVm.toggleSortByFrequency() // Включаем детерминированный порядок
```

## UI Test паттерны

### TRUE UI Clicks (эталон из v1)

```kotlin
// Start session через UI
composeRule.onNodeWithTag("verb_start_button", useUnmergedTree = true)
    .assertIsDisplayed()
    .performClick()

// Check answer через UI
composeRule.onNodeWithTag("input_field").performTextInput(card.acceptedAnswers.first())
composeRule.onNodeWithTag("check_button").performClick()

// Navigation через UI
composeRule.onNodeWithTag("next_button").performClick()

// SessionCard кнопки через UI
composeRule.onNodeWithTag("repeat_button").performClick()
composeRule.onNodeWithTag("continue_button").performClick()
composeRule.onNodeWithTag("reset_button").performClick()
```

### Синхронизация (эталон из v1)

```kotlin
// Ждем состояния перед кликом
composeRule.waitUntil(timeoutMillis = 10_000) {
    val state = verbVm.uiState.value
    !state.isLoading && state.totalCards > 0 && state.lastSessionContext == null
}

// Ждем создания сессии после клика
composeRule.waitUntil(timeoutMillis = 5_000) {
    verbVm.uiState.value.session?.cards?.isNotEmpty() == true
}
```

## Test структура

### Вариант A: Один класс с 4 тестами

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class VerbDrillSessionCardRegressionTest {

    // Use Case 1: Repeat (любой режим)
    @Test
    fun repeat_replays_saved_batch() {
        val cards = createTestVerbCards(12)
        val verbVm = preparedVerbVm(cards, sessionSize = 5)
        
        startVerbSessionThroughUi(verbVm, trainingVm, route)
        val firstBatchIds = verbVm.uiState.value.session!!.cards.map { it.id }
        
        repeat(3) { answerCurrentCardCorrectly(trainingVm) }
        
        exitTrainingThroughUi(verbVm, trainingVm, route)
        composeRule.onNodeWithTag("repeat_button").performClick()
        
        val repeatedBatchIds = verbVm.uiState.value.session!!.cards.map { it.id }
        assertEquals(firstBatchIds, repeatedBatchIds)
    }
    
    // Use Case 2: Continue random-mode
    @Test
    fun continue_random_mode_excludes_checked_only() {
        val cards = createTestVerbCards(12)
        val verbVm = preparedVerbVm(cards, sessionSize = 5)
        // NOT calling toggleSortByFrequency() → random mode
        
        startVerbSessionThroughUi(verbVm, trainingVm, route)
        
        // Check 2 cards
        repeat(2) { answerCurrentCardCorrectly(trainingVm) }
        
        // Next only on card 3 (navigation-only)
        val navigationOnlyCardId = trainingVm.uiState.value.cardSession.currentCard!!.id
        composeRule.onNodeWithTag("next_button").performClick()
        
        val shownAfterNavigation = store.loadLastSession()!!.todayShownCardIds
        assertEquals(2, shownAfterNavigation.size)
        assertFalse(navigationOnlyCardId in shownAfterNavigation)
        
        exitTrainingThroughUi(verbVm, trainingVm, route)
        composeRule.onNodeWithTag("continue_button").performClick()
        
        val continueBatchIds = verbVm.uiState.value.session!!.cards.map { it.id }
        assertTrue("Continue must exclude checked cards",
            continueBatchIds.intersect(checkedCardIds).isEmpty())
    }
    
    // Use Case 3: Continue frequency-mode
    @Test
    fun continue_frequency_mode_exact_order() {
        val cards = createTestVerbCards(12)
        val verbVm = preparedVerbVm(cards, sessionSize = 5)
        verbVm.toggleSortByFrequency() // ← Enable frequency mode
        
        startVerbSessionThroughUi(verbVm, trainingVm, route)
        
        // Check test_verb_1, test_verb_2
        repeat(2) { answerCurrentCardCorrectly(trainingVm) }
        
        // Next only on test_verb_3
        val navigationOnlyCardId = trainingVm.uiState.value.cardSession.currentCard!!.id
        composeRule.onNodeWithTag("next_button").performClick()
        
        exitTrainingThroughUi(verbVm, trainingVm, route)
        composeRule.onNodeWithTag("continue_button").performClick()
        
        val continueBatchIds = verbVm.uiState.value.session!!.cards.map { it.id }
        val expected = listOf(
            "test_verb_3", "test_verb_4", "test_verb_5",
            "test_verb_6", "test_verb_7"
        )
        assertEquals("Continue should produce exact next ordered batch",
            expected, continueBatchIds)
        assertTrue("Navigation-only card should be first in frequency mode",
            navigationOnlyCardId == continueBatchIds.first())
    }
    
    // Use Case 4: Reset
    @Test
    fun reset_clears_session_keeps_progress() {
        val cards = createTestVerbCards(12)
        val verbVm = preparedVerbVm(cards, sessionSize = 5)
        verbVm.toggleSortByFrequency() // For frequency-mode assertion
        
        startVerbSessionThroughUi(verbVm, trainingVm, route)
        
        repeat(2) { answerCurrentCardCorrectly(trainingVm) }
        val progressBeforeReset = allTodayShownIds()
        
        exitTrainingThroughUi(verbVm, trainingVm, route)
        composeRule.onNodeWithTag("reset_button").performClick()
        
        // Common assertions (all modes)
        assertNull("Reset should delete session", store.loadLastSession())
        composeRule.onNodeWithTag("session_card").assertIsNotDisplayed()
        assertEquals("Reset should keep progress", progressBeforeReset, allTodayShownIds())
        
        // Frequency-mode: new Start begins from rank start
        startVerbSessionThroughUi(verbVm, trainingVm, route)
        val afterResetStartBatchIds = verbVm.uiState.value.session!!.cards.map { it.id }
        val expected = listOf(
            "test_verb_1", "test_verb_2", "test_verb_3",
            "test_verb_4", "test_verb_5"
        )
        assertEquals("After Reset, Start should begin deck from beginning",
            expected, afterResetStartBatchIds)
    }
}
```

## Risk mitigation

**Риск 1: Перепутать random/frequency assertions**
- **Mitigation:** Четко документировать какой режим для какого теста
- **Verification:** Code review с фокусом на assertions

**Риск 2: Flaky тесты из-за shuffle в random-mode**
- **Mitigation:** В random-mode проверять только исключения, не порядок
- **Verification:** Многократный прогон теста

**Риск 3: Сложность синхронизации UI**
- **Mitigation:** Использовать проверенные паттерны из v1 (waitUntil, waitForIdle)
- **Verification:** Проверить что все 4 теста стабильны

## Зависимости

**От v1 теста:**
- Использовать паттерны TRUE UI clicks
- Использовать синхронизацию из v1
- Helper `exitTrainingThroughUi()` допустим

**От требований:**
- `docs/testing/VerbDrillSessionCardRegressionTest-Requirements.md`
- Спецификация VerbDrill (`docs/specification/10-verb-drill.md`)

## Open questions

1. **Один класс или два?**
   - Плюсы одного класса: проще для навигации
   - Плюсы двух классов: четкое разделение режимов
   - **Рекомендация:** Один класс с понятными названиями тестов

2. **Нужно ли тестировать Repeat в frequency-mode отдельно?**
   - Repeat работает для обоих режимов одинаково (ordered replay)
   - Достаточно одного теста
   - **Рекомендация:** Один тест для Repeat (без указания режима)

3. **Как обрабатывать legacy-сессии без sessionCardIds?**
   - Согласно требованиям: допустим fallback на fresh start
   - Но этот кейс не должен маскироваться под проверку порядка
   - **Рекомендация:** Не включать в этот тест, вынести в отдельный (если нужен)
