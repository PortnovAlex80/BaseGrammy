# UI Test Plan - BaseGrammy

**Версия документа:** 1.0
**Дата:** 2026-01-16
**Цель:** Защита UI от регрессий - проверка корректности отображения данных

---

## ПРОБЛЕМА

**❌ UI тесты полностью отсутствуют (0% покрытие)**

### Риски без UI тестов:
1. **Crash при отображении** - деление на ноль, null pointer в процентах
2. **Некорректные индикаторы** - показ 150/0 карточек, 0/0 прогресса
3. **Неправильные цветки** - некорректный масштаб, не то emoji
4. **Сломанная навигация** - пустые экраны, зависание на загрузке
5. **Потеря данных пользователя** - не отображается имя, streak, награды

---

## СТРАТЕГИЯ ТЕСТИРОВАНИЯ

### Типы UI тестов:

1. **Unit тесты UI логики** (ViewModel тесты)
   - Проверка вычислений для отображения
   - Граничные значения (0, пустые списки, null)
   - Форматирование данных

2. **Composable Preview тесты**
   - Screenshot тесты для каждого экрана
   - Проверка различных состояний

3. **Instrumented UI тесты** (Android Test)
   - Полная навигация
   - Проверка взаимодействия
   - Интеграция с реальными данными

### Приоритеты:
- **P0 (Критический):** ViewModel тесты + граничные случаи
- **P1 (Важный):** Preview тесты для основных экранов
- **P2 (Желательный):** Полные instrumented тесты

---

## P0: UNIT ТЕСТЫ UI ЛОГИКИ

### 1. TrainingViewModelUITest.kt [НОВЫЙ]
**Приоритет:** P0 - КРИТИЧЕСКИЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/ui/TrainingViewModelUITest.kt`
**Цель:** Защита расчетов для отображения в UI

#### 1.1 Индикаторы прогресса

##### 1.1.1 Прогресс-бар (деление на ноль)
```kotlin
@Test fun progressBar_zeroTotal_doesNotCrash()
@Test fun progressBar_completed0_total0_returns0Progress()
@Test fun progressBar_completed5_total10_returns50Progress()
@Test fun progressBar_completed10_total10_returns100Progress()
@Test fun progressBar_completedExceedsTotal_caps100Progress()
@Test fun progressBar_negativeCompleted_returns0Progress()
```
**Покрываемые требования:** Отображение прогресса подуроков (строки 808-810 GrammarMateApp.kt)

**Критический баг:**
```kotlin
// Строка 808-810: progress = completed.toFloat() / total.toFloat()
// Если total = 0, будет деление на ноль → NaN → UI crash
```

##### 1.1.2 Счетчики карточек
```kotlin
@Test fun cardCounter_noCards_shows0of0()
@Test fun cardCounter_1of10_showsCorrectly()
@Test fun cardCounter_10of10_showsCorrectly()
@Test fun cardCounter_shownExceedsTotal_capsAtTotal()
@Test fun cardCounter_negativeValues_showsZero()
```
**Покрываемые требования:** Строки 817-823 GrammarMateApp.kt

##### 1.1.3 Процент выполнения
```kotlin
@Test fun progressPercent_0of100_returns0()
@Test fun progressPercent_50of100_returns50()
@Test fun progressPercent_100of100_returns100()
@Test fun progressPercent_zeroTotal_returns0()
@Test fun progressPercent_decimalValues_roundsCorrectly()
```
**Покрываемые требования:** Строки 1982-2013 GrammarMateApp.kt

##### 1.1.4 Форматирование времени
```kotlin
@Test fun formatTime_0ms_returns0000()
@Test fun formatTime_59999ms_returns0059()
@Test fun formatTime_60000ms_returns0100()
@Test fun formatTime_3600000ms_returns6000()
@Test fun formatTime_negativeMs_returns0000()
```
**Покрываемые требования:** Функция formatTime (строки 2470-2475 TrainingViewModel.kt)

##### 1.1.5 Скорость (слов в минуту)
```kotlin
@Test fun speedPerMinute_0ms_returns0()
@Test fun speedPerMinute_1word_60000ms_returns1wpm()
@Test fun speedPerMinute_10words_60000ms_returns10wpm()
@Test fun speedPerMinute_divisionByZero_returns0()
@Test fun speedPerMinute_roundsToInteger()
```
**Покрываемые требования:** speedPerMinute (строки 2477-2481 TrainingViewModel.kt)

#### 1.2 Цветки (Flower визуализация)

##### 1.2.1 Масштаб цветка
```kotlin
@Test fun flowerScale_0mastery_0health_returns50PercentMin()
@Test fun flowerScale_100mastery_100health_returns100Percent()
@Test fun flowerScale_50mastery_50health_returns25Percent_coerced50()
@Test fun flowerScale_neverBelow50Percent()
@Test fun flowerScale_neverAbove100Percent()
```
**Покрываемые требования:** FlowerCalculator.calculate (строка 59)

**Критический баг:**
```kotlin
// Строка 1527: fontSize = (18 * scale).sp
// Если scale < 0, будет отрицательный размер → UI crash
// Если scale = NaN, будет NaN fontSize → UI crash
```

##### 1.2.2 Emoji состояний
```kotlin
@Test fun flowerEmoji_locked_returnsLockIcon()
@Test fun flowerEmoji_seed_returnsSeedIcon()
@Test fun flowerEmoji_sprout_returnsSproutIcon()
@Test fun flowerEmoji_bloom_returnsBloomIcon()
@Test fun flowerEmoji_wilting_returnsWiltingIcon()
@Test fun flowerEmoji_wilted_returnsWiltedIcon()
@Test fun flowerEmoji_gone_returnsGoneIcon()
@Test fun flowerEmoji_allStates_neverNull()
```
**Покрываемые требования:** FlowerCalculator.getEmoji (строки 105-115)

##### 1.2.3 Процент мастерства отображение
```kotlin
@Test fun masteryDisplay_0shows_displays0Percent()
@Test fun masteryDisplay_50shows_displays33Percent()
@Test fun masteryDisplay_150shows_displays100Percent()
@Test fun masteryDisplay_200shows_capsAt100Percent()
@Test fun masteryDisplay_hidesWhenLocked()
@Test fun masteryDisplay_hidesWhenUnlocked()
```
**Покрываемые требования:** Строки 1530-1537 GrammarMateApp.kt

#### 1.3 Профиль пользователя

##### 1.3.1 Инициалы
```kotlin
@Test fun userInitials_emptyName_returnsGM()
@Test fun userInitials_singleName_returnsFirstLetter()
@Test fun userInitials_twoNames_returnsTwoLetters()
@Test fun userInitials_threeNames_returnsTwoLetters()
@Test fun userInitials_withSpaces_trimsCorrectly()
@Test fun userInitials_lowercase_convertsToUppercase()
@Test fun userInitials_withNumbers_handles()
```
**Покрываемые требования:** getUserInitials (строки 447-455 GrammarMateApp.kt)

#### 1.4 Streak индикатор

##### 1.4.1 Сообщения о достижениях
```kotlin
@Test fun streakMessage_day1_correctMessage()
@Test fun streakMessage_day3_correctMessage()
@Test fun streakMessage_day7_correctMessage()
@Test fun streakMessage_day14_correctMessage()
@Test fun streakMessage_day30_correctMessage()
@Test fun streakMessage_day100_correctMessage()
@Test fun streakMessage_day50_multipleOf10_correctMessage()
@Test fun streakMessage_day0_noMessage()
```
**Покрываемые требования:** Строки 1967-1978 TrainingViewModel.kt

##### 1.4.2 Отображение streak
```kotlin
@Test fun streakDisplay_0days_shows0()
@Test fun streakDisplay_currentEqualsLongest_showsOne()
@Test fun streakDisplay_currentLessThanLongest_showsBoth()
```
**Покрываемые требования:** Строки 384-416 GrammarMateApp.kt

#### 1.5 Boss награды

##### 1.5.1 Цвета медалей
```kotlin
@Test fun bossReward_bronze_correctColor()
@Test fun bossReward_silver_correctColor()
@Test fun bossReward_gold_correctColor()
@Test fun bossReward_null_defaultColor()
```
**Покрываемые требования:** Строки 1041-1070 GrammarMateApp.kt

##### 1.5.2 Расчет награды
```kotlin
@Test fun calculateReward_0percent_noReward()
@Test fun calculateReward_50percent_bronze()
@Test fun calculateReward_51percent_bronze()
@Test fun calculateReward_75percent_silver()
@Test fun calculateReward_76percent_silver()
@Test fun calculateReward_99percent_silver()
@Test fun calculateReward_100percent_gold()
@Test fun calculateReward_divisionByZero_noReward()
```
**Покрываемые требования:** Логика наград Boss

#### 1.6 Vocab Sprint

##### 1.6.1 Прогресс словаря
```kotlin
@Test fun vocabProgress_0of0_shows00()
@Test fun vocabProgress_1of10_shows110()
@Test fun vocabProgress_10of10_shows1010()
@Test fun vocabProgress_indexExceedsTotal_showsCorrectly()
```
**Покрываемые требования:** Строки 1124-1129 GrammarMateApp.kt

#### 1.7 Режим ALL_MIXED

##### 1.7.1 Ограничение 300 карточек (UI отображение)
```kotlin
@Test fun allMixedDisplay_500cards_shows300()
@Test fun allMixedDisplay_300cards_shows300()
@Test fun allMixedDisplay_200cards_shows200()
@Test fun allMixedProgress_150of300_shows50Percent()
@Test fun allMixedProgress_300of300_shows100Percent()
```
**Покрываемые требования:** FR-5.1.4-5.1.6 + отображение в HeaderStats

#### 1.8 Пустые состояния

##### 1.8.1 Пустой список уроков
```kotlin
@Test fun emptyLessons_homeScreen_showsNoHint()
@Test fun emptyLessons_dropdown_showsNoLessons()
@Test fun emptyLessons_doesNotCrash()
```
**Покрываемые требования:** Строки 562, 2051-2056 GrammarMateApp.kt

##### 1.8.2 Пустой список карточек
```kotlin
@Test fun emptyCards_trainingScreen_showsNoCards()
@Test fun emptyCards_doesNotCrash()
@Test fun emptyCards_pausesTimer()
```
**Покрываемые требования:** Строки 2178-2179, 2252-2257 GrammarMateApp.kt

##### 1.8.3 Пустой словарь
```kotlin
@Test fun emptyVocab_sprintScreen_showsNoWords()
@Test fun emptyVocab_errorMessage_displayed()
```
**Покрываемые требования:** Строки 1288-1290, 1200-1203 GrammarMateApp.kt

---

## P1: COMPOSABLE PREVIEW ТЕСТЫ

### 2. ComposePreviewTest.kt [НОВЫЙ]
**Приоритет:** P1 - ВАЖНЫЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/ui/ComposePreviewTest.kt`
**Цель:** Screenshot тесты для визуальной проверки

#### 2.1 HomeScreen состояния

```kotlin
@Test fun homeScreen_normalState_rendersCorrectly()
@Test fun homeScreen_emptyLessons_rendersCorrectly()
@Test fun homeScreen_allFlowerStates_renderCorrectly()
@Test fun homeScreen_longUserName_rendersCorrectly()
@Test fun homeScreen_0streak_rendersCorrectly()
@Test fun homeScreen_100streak_rendersCorrectly()
```

#### 2.2 LessonRoadmapScreen состояния

```kotlin
@Test fun lessonRoadmap_0progress_rendersCorrectly()
@Test fun lessonRoadmap_50progress_rendersCorrectly()
@Test fun lessonRoadmap_100progress_rendersCorrectly()
@Test fun lessonRoadmap_withRewards_rendersCorrectly()
```

#### 2.3 TrainingScreen состояния

```kotlin
@Test fun trainingScreen_activeState_rendersCorrectly()
@Test fun trainingScreen_pausedState_rendersCorrectly()
@Test fun trainingScreen_afterCheckState_rendersCorrectly()
@Test fun trainingScreen_correctAnswer_rendersCorrectly()
@Test fun trainingScreen_incorrectAnswer_rendersCorrectly()
@Test fun trainingScreen_longText_rendersCorrectly()
```

#### 2.4 Boss режимы

```kotlin
@Test fun bossScreen_bronze_rendersCorrectly()
@Test fun bossScreen_silver_rendersCorrectly()
@Test fun bossScreen_gold_rendersCorrectly()
@Test fun bossScreen_noReward_rendersCorrectly()
```

#### 2.5 Цветки

```kotlin
@Test fun flowerTile_locked_rendersCorrectly()
@Test fun flowerTile_seed_rendersCorrectly()
@Test fun flowerTile_sprout_rendersCorrectly()
@Test fun flowerTile_bloom_rendersCorrectly()
@Test fun flowerTile_wilting_rendersCorrectly()
@Test fun flowerTile_wilted_rendersCorrectly()
@Test fun flowerTile_gone_rendersCorrectly()
@Test fun flowerTile_minScale50_rendersCorrectly()
@Test fun flowerTile_maxScale100_rendersCorrectly()
```

---

## P2: INSTRUMENTED UI ТЕСТЫ

### 3. HomeScreenUITest.kt [НОВЫЙ]
**Приоритет:** P2 - ЖЕЛАТЕЛЬНЫЙ
**Файл:** `app/src/androidTest/java/com/alexpo/grammermate/HomeScreenUITest.kt`
**Цель:** Полные E2E тесты навигации

#### 3.1 Навигация по урокам

```kotlin
@Test fun clickLesson_opensRoadmap()
@Test fun selectLanguage_loadsLessons()
@Test fun clickContinueLearning_resumesTraining()
@Test fun clickElite_opensEliteScreen()
```

#### 3.2 Взаимодействие с профилем

```kotlin
@Test fun changeUserName_updatesDisplay()
@Test fun openSettings_showsProfile()
```

#### 3.3 Отображение прогресса

```kotlin
@Test fun completeLesson_updatesFlower()
@Test fun dailyActivity_incrementsStreak()
```

---

### 4. TrainingScreenUITest.kt [НОВЫЙ]
**Приоритет:** P2 - ЖЕЛАТЕЛЬНЫЙ
**Файл:** `app/src/androidTest/java/com/alexpo/grammermate/TrainingScreenUITest.kt`

#### 4.1 Режимы ввода

```kotlin
@Test fun switchToVoice_activatesMicrophone()
@Test fun switchToKeyboard_showsKeyboard()
@Test fun switchToWordBank_showsWords()
```

#### 4.2 Навигация по карточкам

```kotlin
@Test fun clickNext_showsNextCard()
@Test fun clickPrev_showsPreviousCard()
@Test fun clickCheck_validatesAnswer()
@Test fun pause_savesProgress()
@Test fun stop_returnsToRoadmap()
```

#### 4.3 Проверка ответов

```kotlin
@Test fun correctAnswer_showsGreen()
@Test fun incorrectAnswer_showsRed()
@Test fun emptyAnswer_doesNotSubmit()
```

---

### 5. BossScreenUITest.kt [НОВЫЙ]
**Приоритет:** P2 - ЖЕЛАТЕЛЬНЫЙ
**Файл:** `app/src/androidTest/java/com/alexpo/grammermate/BossScreenUITest.kt`

#### 5.1 Boss завершение

```kotlin
@Test fun complete50Percent_showsBronze()
@Test fun complete76Percent_showsSilver()
@Test fun complete100Percent_showsGold()
@Test fun bossReward_savesCorrectly()
```

---

## КРИТИЧЕСКИЕ БАГИ БЕЗ ТЕСТОВ

### 🔴 Риск 1: Деление на ноль

**Локация:** GrammarMateApp.kt, строка 808-810
```kotlin
LinearProgressIndicator(
    progress = completed.toFloat() / total.toFloat()  // ← total = 0 → NaN → CRASH
)
```

**Тест для защиты:**
```kotlin
@Test fun progressBar_zeroTotal_doesNotCrash() {
    val completed = 0
    val total = 0
    val progress = if (total > 0) completed.toFloat() / total else 0f
    assertEquals(0f, progress)
}
```

**Фикс:**
```kotlin
val progress = if (total > 0) {
    (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
} else {
    0f
}
LinearProgressIndicator(progress = progress)
```

---

### 🔴 Риск 2: Отрицательный масштаб цветка

**Локация:** FlowerCalculator.kt, строка 59
```kotlin
val scale = (masteryPercent * healthPercent).coerceIn(0.5f, 1.0f)
```

**Проблема:** Если masteryPercent или healthPercent = NaN → scale = NaN

**Тест для защиты:**
```kotlin
@Test fun flowerScale_invalidValues_coercesCorrectly() {
    val mastery = LessonMasteryState(lessonId = "test", languageId = "en")
    val flower = FlowerCalculator.calculate(mastery, totalCards = 0)
    assertTrue(flower.scaleMultiplier >= 0.5f)
    assertTrue(flower.scaleMultiplier <= 1.0f)
    assertFalse(flower.scaleMultiplier.isNaN())
}
```

---

### 🔴 Риск 3: Показ "N of 0" карточек

**Локация:** GrammarMateApp.kt, строка 819
```kotlin
Text(text = "Cards: $shownCards of $totalCards")  // Может быть "150 of 0"
```

**Тест для защиты:**
```kotlin
@Test fun cardCounter_zeroTotal_showsZeroOf Zero() {
    val shown = 0
    val total = 0
    val text = "Cards: $shown of $total"
    assertEquals("Cards: 0 of 0", text)
}

@Test fun cardCounter_shownExceedsTotal_capsAtTotal() {
    val shown = 150
    val total = 100
    val cappedShown = shown.coerceAtMost(total)
    val text = "Cards: $cappedShown of $total"
    assertEquals("Cards: 100 of 100", text)
}
```

---

### 🔴 Риск 4: Пустое имя пользователя → пустые инициалы

**Локация:** GrammarMateApp.kt, строки 447-455
```kotlin
private fun getUserInitials(name: String): String {
    return name.trim()
        .split(" ")
        .take(2)
        .map { it.first().uppercase() }  // ← first() на пустой строке → Exception
        .joinToString("")
        .ifEmpty { "GM" }
}
```

**Тест для защиты:**
```kotlin
@Test fun userInitials_emptyName_returnsGM() {
    assertEquals("GM", getUserInitials(""))
}

@Test fun userInitials_onlySpaces_returnsGM() {
    assertEquals("GM", getUserInitials("   "))
}
```

**Фикс:**
```kotlin
private fun getUserInitials(name: String): String {
    return name.trim()
        .split(" ")
        .filter { it.isNotEmpty() }  // ← Добавить фильтр
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }  // ← Использовать firstOrNull
        .joinToString("")
        .ifEmpty { "GM" }
}
```

---

### 🔴 Риск 5: ALL_MIXED показывает > 300 карточек

**Локация:** TrainingViewModel.kt - buildSessionCards для ALL_MIXED

**Тест для защиты:**
```kotlin
@Test fun allMixedMode_500cards_displays300Maximum() {
    // Создать 500 карточек
    val cards = (1..500).map { createTestCard(it) }

    // Запустить ALL_MIXED режим
    viewModel.startTraining(TrainingMode.ALL_MIXED)

    // Проверить, что показывается только 300
    val displayedTotal = viewModel.uiState.value.subLessonTotal
    assertTrue(displayedTotal <= 300, "Displayed $displayedTotal cards, expected ≤ 300")
}
```

---

## СВОДНАЯ ТАБЛИЦА UI ТЕСТОВ

| # | Тест-файл | Приоритет | Тестов | Покрываемые риски |
|---|-----------|-----------|--------|-------------------|
| 1 | TrainingViewModelUITest | P0 | ~80 | Деление на ноль, отрицательные значения, NaN |
| 2 | ComposePreviewTest | P1 | ~30 | Визуальные регрессии |
| 3 | HomeScreenUITest | P2 | ~10 | Навигация, интеграция |
| 4 | TrainingScreenUITest | P2 | ~10 | Взаимодействие пользователя |
| 5 | BossScreenUITest | P2 | ~5 | Boss награды |

**ИТОГО:** ~135 UI тестов для защиты от регрессий

---

## ROADMAP ВЫПОЛНЕНИЯ

### Спринт 1 (P0) - 2 недели
**Week 1:** TrainingViewModelUITest - индикаторы прогресса (25 тестов)
**Week 2:** TrainingViewModelUITest - цветки, профиль, streak (55 тестов)

**Ожидаемый результат:**
- ✅ Защита от деления на ноль
- ✅ Защита от отрицательных значений
- ✅ Защита от NaN

### Спринт 2 (P1) - 1 неделя
**Week 1:** ComposePreviewTest - основные экраны (30 тестов)

**Ожидаемый результат:**
- ✅ Screenshot тесты для визуальной проверки
- ✅ Детекция визуальных регрессий

### Спринт 3 (P2) - 1 неделя
**Week 1:** Instrumented UI тесты (25 тестов)

**Ожидаемый результат:**
- ✅ E2E тесты навигации
- ✅ Полная проверка взаимодействия

---

## ЗАВИСИМОСТИ

### Для Composable Preview тестов:
```gradle
androidTestImplementation 'androidx.compose.ui:ui-test-junit4:1.5.4'
debugImplementation 'androidx.compose.ui:ui-test-manifest:1.5.4'
```

### Для Screenshot тестов:
```gradle
androidTestImplementation 'com.github.sergio-sastre:AndroidUiTestingUtils:2.0.0'
```

### Для ViewModel тестов:
```gradle
testImplementation 'androidx.arch.core:core-testing:2.2.0'
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
```

---

## МЕТРИКИ УСПЕХА

### Количественные:
- ✅ 80+ unit тестов UI логики
- ✅ 30+ preview тестов
- ✅ 25+ instrumented тестов
- ✅ 0 UI crashes при граничных значениях

### Качественные:
- ✅ Все деления на ноль защищены
- ✅ Все пустые состояния обработаны
- ✅ Все масштабы в допустимых пределах
- ✅ Визуальные регрессии детектируются

---

## ПРАВИЛА ДЛЯ АГЕНТОВ

### ⛔ ЗАПРЕЩЕНО:
- Изменять UI без проверки граничных значений
- Использовать деление без проверки на ноль
- Использовать `.first()` без проверки на пустоту
- Показывать отрицательные проценты/счетчики

### ✅ ОБЯЗАТЕЛЬНО:
- Добавлять `.coerceIn()` для всех процентов и масштабов
- Проверять `if (total > 0)` перед делением
- Использовать `.firstOrNull()` вместо `.first()`
- Проверять пустые состояния перед отображением

### ⚠️ ОСОБОЕ ВНИМАНИЕ:
- **Прогресс-бары:** ВСЕГДА проверять total > 0
- **Цветки:** ВСЕГДА coerce scale в [0.5, 1.0]
- **Счетчики:** ВСЕГДА cap значения в допустимых пределах
- **Инициалы:** ВСЕГДА фильтровать пустые строки

---

## ЗАКЛЮЧЕНИЕ

UI тесты критически важны для защиты от регрессий, особенно при граничных значениях (0, null, пустые списки). Без них приложение может крашиться при отображении пустых данных или показывать некорректную информацию пользователю.

**Следующий шаг:** Начать с P0 тестов (TrainingViewModelUITest) для защиты от критических багов.
