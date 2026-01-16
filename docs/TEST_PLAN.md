# Тест-план для полного покрытия функционала BaseGrammy

**Версия документа:** 1.0
**Дата:** 2026-01-16
**Цель:** Защита от регрессий при разработке новых фич агентами

---

## СТРАТЕГИЯ ТЕСТИРОВАНИЯ

### Приоритеты выполнения:
1. **P0 (Критический)** - НЕМЕДЛЕННО (блокирует релиз)
2. **P1 (Важный)** - В течение спринта
3. **P2 (Желательный)** - В следующем спринте

### Типы тестов:
- **Unit тесты** - изолированное тестирование классов
- **Integration тесты** - тестирование связки компонентов
- **Property-based тесты** - тестирование свойств и инвариантов

---

## P0: КРИТИЧЕСКИЙ ФУНКЦИОНАЛ

### 1. SpacedRepetitionConfigTest.kt [НОВЫЙ]
**Приоритет:** P0 - КРИТИЧЕСКИЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/SpacedRepetitionConfigTest.kt`
**Цель:** Защита алгоритма кривой забывания Эббингауза

#### Тесты для написания:

##### 1.1 Расчет стабильности памяти
```kotlin
@Test fun calculateStability_firstStep_returnsBaseStability()
@Test fun calculateStability_negativeIndex_returnsBaseStability()
@Test fun calculateStability_secondStep_returnsMultipliedStability()
@Test fun calculateStability_maxStep_returnsCorrectStability()
```
**Покрываемые требования:** FR-9.1.2, FR-9.1.3, FR-9.1.4

##### 1.2 Расчет retention (удержания)
```kotlin
@Test fun calculateRetention_zeroDay s_returns100Percent()
@Test fun calculateRetention_oneDayFirstStep_returnsExpectedRetention()
@Test fun calculateRetention_longTime_approachesZero()
@Test fun calculateRetention_neverExceedsOne()
```
**Покрываемые требования:** FR-9.1.1

##### 1.3 Расчет здоровья цветка
```kotlin
@Test fun calculateHealthPercent_zeroDays_returns100Percent()
@Test fun calculateHealthPercent_withinInterval_returns100Percent()
@Test fun calculateHealthPercent_overdueDecays_from100to50Percent()
@Test fun calculateHealthPercent_ninetyDays_returnsZero()
@Test fun calculateHealthPercent_neverBelowWiltedThreshold()
@Test fun calculateHealthPercent_exponentialDecayFormula()
```
**Покрываемые требования:** FR-9.3.1, FR-9.3.2, FR-9.3.3, FR-9.3.4, FR-9.3.5

##### 1.4 Лестница интервалов
```kotlin
@Test fun nextIntervalStep_onTime_advancesStep()
@Test fun nextIntervalStep_late_keepsCurrentStep()
@Test fun nextIntervalStep_maxStep_staysAtMax()
@Test fun wasRepetitionOnTime_withinInterval_returnsTrue()
@Test fun wasRepetitionOnTime_overdue_returnsFalse()
@Test fun intervalLadderDays_hasCorrectValues()
```
**Покрываемые требования:** FR-9.2.1, FR-9.2.2, FR-9.2.3, FR-9.2.4

##### 1.5 Константы и пороги
```kotlin
@Test fun constants_masteryThreshold_equals150()
@Test fun constants_wiltedThreshold_equals50Percent()
@Test fun constants_goneThresholdDays_equals90()
@Test fun constants_baseStability_isPositive()
@Test fun constants_stabilityMultiplier_greaterThanOne()
```
**Покрываемые требования:** FR-9.1.2, FR-9.1.3, FR-9.3.4, FR-9.3.5

**Метрики успеха:**
- ✅ Все константы покрыты
- ✅ Все формулы протестированы с граничными значениями
- ✅ Property-based тесты для монотонности затухания

---

### 2. FlowerCalculatorTest.kt [НОВЫЙ]
**Приоритет:** P0 - КРИТИЧЕСКИЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/FlowerCalculatorTest.kt`
**Цель:** Защита расчета состояния цветков (визуализации прогресса)

#### Тесты для написания:

##### 2.1 Базовые состояния
```kotlin
@Test fun calculate_nullMastery_returnsSeedState()
@Test fun calculate_zeroShows_returnsSeedState()
@Test fun calculate_moreThan90Days_returnsGoneState()
```
**Покрываемые требования:** FR-8.3.5, FR-8.1.2, FR-8.1.7

##### 2.2 Определение состояния по проценту мастерства
```kotlin
@Test fun calculate_0to33PercentMastery_returnsSeed()
@Test fun calculate_33to66PercentMastery_returnsSprout()
@Test fun calculate_66to100PercentMastery_returnsBloom()
```
**Покрываемые требования:** FR-8.1.2, FR-8.1.3, FR-8.1.4

##### 2.3 Увядание по здоровью
```kotlin
@Test fun calculate_healthBelow100Percent_returnsWilting()
@Test fun calculate_healthBelowWiltedThreshold_returnsWilted()
@Test fun calculate_wiltingOverridesBloomState()
```
**Покрываемые требования:** FR-8.1.5, FR-8.1.6

##### 2.4 Расчет процента мастерства
```kotlin
@Test fun calculate_50Shows_returns33PercentMastery()
@Test fun calculate_150Shows_returns100PercentMastery()
@Test fun calculate_200Shows_capsAt100PercentMastery()
```
**Покрываемые требования:** FR-8.3.1, FR-8.3.2

##### 2.5 Масштаб цветка
```kotlin
@Test fun calculate_scaleMultiplier_neverBelow50Percent()
@Test fun calculate_scaleMultiplier_maxIs100Percent()
@Test fun calculate_scaleMultiplier_isMasteryTimesHealth()
```
**Покрываемые требования:** FR-8.3.4

##### 2.6 Emoji представления
```kotlin
@Test fun getEmoji_returnsCorrectEmojiForEachState()
@Test fun getEmojiWithScale_returnsPairWithScale()
```
**Покрываемые требования:** (визуализация)

##### 2.7 Граничные случаи
```kotlin
@Test fun calculate_exactly150Shows_returns100PercentMastery()
@Test fun calculate_exactly90Days_beforeGone()
@Test fun calculate_exactly91Days_isGone()
@Test fun calculate_negativeTimestamp_treatedAsZeroDays()
```

**Метрики успеха:**
- ✅ Все 7 состояний цветка протестированы
- ✅ Граничные значения (0, 50, 100, 150, 90 дней)
- ✅ Корректный расчет масштаба

---

### 3. MasteryStoreTest.kt [НОВЫЙ]
**Приоритет:** P0 - КРИТИЧЕСКИЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/MasteryStoreTest.kt`
**Цель:** Защита сохранения и загрузки прогресса мастерства

#### Тесты для написания:

##### 3.1 Сохранение и загрузка
```kotlin
@Test fun saveMastery_newState_writesToFile()
@Test fun loadMastery_existingFile_returnsCorrectState()
@Test fun loadMastery_missingFile_returnsEmptyList()
@Test fun loadMastery_corruptedFile_returnsEmptyList()
```
**Покрываемые требования:** FR-8.5.1, FR-8.5.3, FR-8.5.4

##### 3.2 Кеширование
```kotlin
@Test fun loadMastery_calledTwice_usesCache()
@Test fun saveMastery_updatesCache()
@Test fun invalidateCache_forcesReload()
```
**Покрываемые требования:** FR-8.5.2

##### 3.3 Запись показов карточек
```kotlin
@Test fun recordCardShow_firstTime_increasesUniqueShows()
@Test fun recordCardShow_secondTime_doesNotIncreaseUniqueShows()
@Test fun recordCardShow_alwaysIncreasesTotalShows()
@Test fun recordCardShow_updatesLastShowDate()
@Test fun recordCardShow_addsCardIdToSet()
@Test fun recordCardShow_updatesIntervalStepOnTime()
@Test fun recordCardShow_keepsIntervalStepWhenLate()
```
**Покрываемые требования:** FR-8.4.1, FR-8.4.2, FR-8.4.3, FR-8.4.4, FR-8.4.5, FR-8.4.6

##### 3.4 Множественные уроки
```kotlin
@Test fun saveMastery_multipleLanguages_separatesCorrectly()
@Test fun loadMastery_specificLesson_returnsOnlyThatLesson()
@Test fun saveMastery_preservesOtherLessons()
```
**Покрываемые требования:** FR-8.5.3

##### 3.5 Версионирование схемы
```kotlin
@Test fun saveMastery_includesSchemaVersion()
@Test fun loadMastery_oldSchemaVersion_migrates()
```
**Покрываемые требования:** FR-2.2.1, FR-2.2.2

##### 3.6 Атомарность записи
```kotlin
@Test fun saveMastery_usesAtomicWrite()
@Test fun saveMastery_failureDoesNotCorruptFile()
```
**Покрываемые требования:** FR-2.1.1

**Метрики успеха:**
- ✅ Запись/чтение работает корректно
- ✅ Кеш работает и инвалидируется правильно
- ✅ Все метрики (uniqueCardShows, totalCardShows, lastShowDateMs) обновляются

---

### 4. ProgressStoreTest.kt [НОВЫЙ]
**Приоритет:** P0 - КРИТИЧЕСКИЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/ProgressStoreTest.kt`
**Цель:** Защита сохранения и загрузки прогресса тренировки

#### Тесты для написания:

##### 4.1 Сохранение всех полей прогресса
```kotlin
@Test fun saveProgress_languageId_persists()
@Test fun saveProgress_trainingMode_persists()
@Test fun saveProgress_lessonId_persists()
@Test fun saveProgress_currentIndex_persists()
@Test fun saveProgress_correctWrongCounts_persist()
@Test fun saveProgress_activeTimeMs_persists()
@Test fun saveProgress_sessionState_persists()
@Test fun saveProgress_bossRewards_persist()
@Test fun saveProgress_voiceMetrics_persist()
@Test fun saveProgress_eliteProgress_persists()
```
**Покрываемые требования:** FR-7.1.1 - FR-7.1.10

##### 4.2 Загрузка прогресса
```kotlin
@Test fun loadProgress_existingFile_returnsCorrectProgress()
@Test fun loadProgress_missingFile_returnsDefaultProgress()
@Test fun loadProgress_corruptedFile_returnsDefaultProgress()
```
**Покрываемые требования:** FR-7.2.1, FR-7.2.2

##### 4.3 Автосохранение
```kotlin
@Test fun saveProgress_writesImmediately()
@Test fun saveProgress_multipleCallsConcurrent_lastWins()
```
**Покрываемые требования:** FR-7.3.1, FR-7.3.2

##### 4.4 Режимы тренировки
```kotlin
@Test fun saveProgress_lessonMode_persists()
@Test fun saveProgress_allSequentialMode_persists()
@Test fun saveProgress_allMixedMode_persists()
```
**Покрываемые требования:** FR-5.1.1, FR-5.1.2, FR-5.1.3

##### 4.4.1 Режим ALL_MIXED - ограничение 300 карточек
```kotlin
@Test fun allMixedMode_moreThan300Cards_selects300Random()
@Test fun allMixedMode_exactly300Cards_selectsAll()
@Test fun allMixedMode_lessThan300Cards_selectsAll()
@Test fun allMixedMode_randomSelection_isDifferentEachTime()
@Test fun allMixedMode_neverExceeds300Cards()
```
**Покрываемые требования:** FR-5.1.4, FR-5.1.5, FR-5.1.6

##### 4.5 Состояния сессии
```kotlin
@Test fun saveProgress_activeState_persists()
@Test fun saveProgress_pausedState_persists()
@Test fun saveProgress_afterCheckState_persists()
@Test fun saveProgress_hintShownState_persists()
```
**Покрываемые требования:** FR-5.2.1, FR-5.2.2, FR-5.2.3, FR-5.2.4

##### 4.6 Boss награды
```kotlin
@Test fun saveProgress_lessonBossRewards_persist()
@Test fun saveProgress_megaBossReward_persists()
@Test fun saveProgress_multipleLessonRewards_persist()
```
**Покрываемые требования:** FR-10.3.2, FR-10.4.2

##### 4.7 Elite режим
```kotlin
@Test fun saveProgress_eliteStepIndex_persists()
@Test fun saveProgress_eliteBestSpeeds_persist()
@Test fun saveProgress_eliteBestSpeeds_multipleSteps()
```
**Покрываемые требования:** FR-10.5.5

**Метрики успеха:**
- ✅ Все 10 полей прогресса корректно сохраняются и загружаются
- ✅ Дефолтные значения корректны
- ✅ Обработка ошибок не теряет данные

---

### 5. NormalizerTest.kt [НОВЫЙ]
**Приоритет:** P0 - КРИТИЧЕСКИЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/NormalizerTest.kt`
**Цель:** Защита проверки ответов пользователя

#### Тесты для написания:

##### 5.1 Удаление пробелов
```kotlin
@Test fun normalize_multipleSpaces_becomesOne()
@Test fun normalize_leadingTrailingSpaces_removed()
@Test fun normalize_tabsAndNewlines_becomeSpaces()
```
**Покрываемые требования:** FR-6.1.1, FR-6.1.6

##### 5.2 Регистр
```kotlin
@Test fun normalize_upperCase_becomesLowerCase()
@Test fun normalize_mixedCase_becomesLowerCase()
```
**Покрываемые требования:** FR-6.1.2

##### 5.3 Пунктуация
```kotlin
@Test fun normalize_period_removed()
@Test fun normalize_comma_removed()
@Test fun normalize_questionMark_removed()
@Test fun normalize_exclamationMark_removed()
@Test fun normalize_colon_removed()
@Test fun normalize_semicolon_removed()
@Test fun normalize_quotes_removed()
@Test fun normalize_brackets_removed()
@Test fun normalize_hyphen_preserved()
```
**Покрываемые требования:** FR-6.1.3, FR-6.1.4

##### 5.4 Время
```kotlin
@Test fun normalize_timeThreeColon00_becomesThree()
@Test fun normalize_timeTwelveColon30_becomesTwelve()
@Test fun normalize_timeSingleDigit_preserved()
```
**Покрываемые требования:** FR-6.1.5

##### 5.5 Комплексные случаи
```kotlin
@Test fun normalize_realUserAnswer_matchesExpected()
@Test fun normalize_multipleTransformations_appliedCorrectly()
@Test fun normalize_emptyString_returnsEmpty()
@Test fun normalize_onlyPunctuation_returnsEmpty()
```
**Покрываемые требования:** FR-6.2.1

##### 5.6 Edge cases
```kotlin
@Test fun normalize_unicodeCharacters_preserved()
@Test fun normalize_apostropheInContraction_handled()
@Test fun normalize_multipleDashes_preserved()
```

**Метрики успеха:**
- ✅ Все типы пунктуации протестированы
- ✅ Граничные случаи (пустая строка, только пунктуация)
- ✅ Реальные примеры ответов пользователей

---

### 6. ProfileStoreTest.kt [НОВЫЙ]
**Приоритет:** P0 - КРИТИЧЕСКИЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/ProfileStoreTest.kt`
**Цель:** Защита сохранения профиля пользователя

#### Тесты для написания:

##### 6.1 Сохранение и загрузка
```kotlin
@Test fun saveProfile_userName_persists()
@Test fun loadProfile_existingFile_returnsCorrectName()
@Test fun loadProfile_missingFile_returnsDefaultName()
```
**Покрываемые требования:** FR-1.1.1, FR-1.1.2, FR-1.1.4

##### 6.2 YAML формат
```kotlin
@Test fun saveProfile_createsYamlFile()
@Test fun loadProfile_readsYamlFile()
@Test fun saveProfile_includesSchemaVersion()
```
**Покрываемые требования:** FR-1.1.3, FR-2.2.1

##### 6.3 Граничные случаи
```kotlin
@Test fun saveProfile_emptyName_handled()
@Test fun saveProfile_specialCharacters_handled()
@Test fun saveProfile_unicodeCharacters_handled()
```

**Метрики успеха:**
- ✅ Сохранение/загрузка работает
- ✅ Дефолтное имя корректно
- ✅ Обработка edge cases

---

## P1: ВАЖНЫЙ ФУНКЦИОНАЛ

### 7. LessonStoreTest.kt [НОВЫЙ]
**Приоритет:** P1 - ВАЖНЫЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/LessonStoreTest.kt`
**Цель:** Защита управления уроками

#### Тесты для написания:

##### 7.1 Управление языками
```kotlin
@Test fun getLanguages_returnsAllLanguages()
@Test fun addLanguage_addsToList()
@Test fun removeLanguage_removesFromList()
```
**Покрываемые требования:** FR-3.1.1, FR-3.1.2, FR-3.1.3

##### 7.2 Импорт ZIP пакетов
```kotlin
@Test fun importPackFromUri_validZip_extractsLessons()
@Test fun importPackFromUri_withManifest_readsManifest()
@Test fun importPackFromAssets_defaultPacks_imports()
@Test fun updateDefaultPacksIfNeeded_newVersion_updates()
@Test fun updateDefaultPacksIfNeeded_sameVersion_skips()
```
**Покрываемые требования:** FR-3.2.1.1, FR-3.2.1.2, FR-3.2.1.3, FR-3.2.1.4

##### 7.3 Импорт CSV
```kotlin
@Test fun importFromUri_validCsv_createsLesson()
@Test fun importFromUri_withBom_handlesBom()
@Test fun importFromUri_invalidCsv_handlesError()
```
**Покрываемые требования:** FR-3.2.2.1, FR-3.2.2.2, FR-3.2.2.3, FR-3.2.2.4

##### 7.4 Удаление уроков
```kotlin
@Test fun deleteLesson_removesLesson()
@Test fun deleteLesson_removesRelatedFiles()
@Test fun deleteAllLessons_removesAllForLanguage()
@Test fun deletePack_removesPack()
```
**Покрываемые требования:** FR-3.3.1, FR-3.3.2, FR-3.3.3, FR-3.3.4

##### 7.5 Структура урока
```kotlin
@Test fun lesson_first150Cards_isMainPool()
@Test fun lesson_after150Cards_isReservePool()
@Test fun lesson_mainPoolCards_returns150()
@Test fun lesson_reservePoolCards_returnsRest()
```
**Покрываемые требования:** FR-3.4.1, FR-3.4.2, FR-3.4.3, FR-3.4.4

##### 7.6 Чтение уроков
```kotlin
@Test fun getLessons_returnsAllLessonsForLanguage()
@Test fun getLesson_returnsSpecificLesson()
@Test fun getLesson_missingLesson_returnsNull()
```

**Метрики успеха:**
- ✅ Импорт ZIP и CSV работает
- ✅ Удаление не ломает структуру данных
- ✅ Main/reserve pools корректны

---

### 8. StreakStoreTest.kt [НОВЫЙ]
**Приоритет:** P1 - ВАЖНЫЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/StreakStoreTest.kt`
**Цель:** Защита streak системы

#### Тесты для написания:

##### 8.1 Сохранение и загрузка
```kotlin
@Test fun saveStreak_persists()
@Test fun loadStreak_returnsCorrectData()
@Test fun loadStreak_missingFile_returnsDefault()
```
**Покрываемые требования:** FR-13.4.1, FR-13.4.2

##### 8.2 Обновление streak
```kotlin
@Test fun updateStreak_sameDay_doesNotIncrease()
@Test fun updateStreak_nextDay_increasesStreak()
@Test fun updateStreak_skippedDay_resetsStreak()
@Test fun updateStreak_updatesLongestStreak()
@Test fun updateStreak_incrementsTotalDays()
```
**Покрываемые требования:** FR-13.2.1, FR-13.2.2, FR-13.2.3, FR-13.2.4

##### 8.3 Граничные случаи
```kotlin
@Test fun updateStreak_midnight_handlesCorrectly()
@Test fun updateStreak_timezone_handlesCorrectly()
@Test fun updateStreak_firstEverActivity_setsStreak1()
```

**Метрики успеха:**
- ✅ Логика подсчета серий корректна
- ✅ Обработка граничных случаев (полночь, первый день)
- ✅ Longest streak обновляется правильно

---

### 9. CsvParserTest.kt [УЛУЧШЕНИЕ]
**Приоритет:** P1 - ВАЖНЫЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/CsvParserTest.kt`
**Цель:** Дополнить существующие тесты

#### Тесты для добавления:

```kotlin
@Test fun parseLesson_multipleAcceptedAnswers_splitsByPlus()
@Test fun parseLesson_emptyLines_ignored()
@Test fun parseLesson_lineWithoutSeparator_ignored()
@Test fun parseLesson_extraFields_ignored()
@Test fun parseLesson_missingFields_handlesError()
```
**Покрываемые требования:** FR-3.2.3.3, FR-3.2.3.4

**Метрики успеха:**
- ✅ 90% → 100% покрытие CsvParser
- ✅ Все edge cases обработаны

---

### 10. AtomicFileWriterTest.kt [УЛУЧШЕНИЕ]
**Приоритет:** P1 - ВАЖНЫЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/AtomicFileWriterTest.kt`
**Цель:** Дополнить существующие тесты

#### Тесты для добавления:

```kotlin
@Test fun writeText_createsTempFile()
@Test fun writeText_renamesTempToTarget()
@Test fun writeText_onError_deletesTempFile()
@Test fun writeText_concurrent_handlesCorrectly()
@Test fun writeText_existingFile_replacesAtomically()
```
**Покрываемые требования:** FR-2.1.2, FR-2.1.3, FR-2.1.4

**Метрики успеха:**
- ✅ 40% → 100% покрытие AtomicFileWriter
- ✅ Обработка ошибок протестирована
- ✅ Параллельная запись протестирована

---

## P2: ЖЕЛАТЕЛЬНЫЙ ФУНКЦИОНАЛ

### 11. BackupManagerTest.kt [НОВЫЙ]
**Приоритет:** P2 - ЖЕЛАТЕЛЬНЫЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/BackupManagerTest.kt`
**Цель:** Защита бэкапов

#### Тесты для написания:

```kotlin
@Test fun createBackup_createsTimestampedFolder()
@Test fun createBackup_copiesAllFiles()
@Test fun createBackup_createsMetadata()
@Test fun restoreBackup_restoresAllFiles()
@Test fun restoreBackup_afterReinstall_autoRestores()
@Test fun autoBackup_every30Minutes_triggers()
```
**Покрываемые требования:** FR-2.3.1, FR-2.3.2, FR-2.3.3, FR-2.3.4

---

### 12. AppConfigStoreTest.kt [НОВЫЙ]
**Приоритет:** P2 - ЖЕЛАТЕЛЬНЫЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/AppConfigStoreTest.kt`
**Цель:** Защита настроек приложения

#### Тесты для написания:

```kotlin
@Test fun saveConfig_subLessonSize_persists()
@Test fun loadConfig_returnsCorrectConfig()
@Test fun saveConfig_voiceSettings_persist()
```
**Покрываемые требования:** FR-14.1.1, FR-14.1.2, FR-14.1.3, FR-14.1.4

---

### 13. VocabCsvParserTest.kt [УЛУЧШЕНИЕ]
**Приоритет:** P2 - ЖЕЛАТЕЛЬНЫЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/data/VocabCsvParserTest.kt`
**Цель:** Дополнить существующие тесты

#### Тесты для добавления:

```kotlin
@Test fun parse_emptyLines_ignored()
@Test fun parse_invalidFormat_handlesError()
@Test fun parse_missingHardFlag_defaultsFalse()
```
**Покрываемые требования:** FR-11.3.3

---

## INTEGRATION ТЕСТЫ

### 14. MasteryIntegrationTest.kt [НОВЫЙ]
**Приоритет:** P0 - КРИТИЧЕСКИЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/integration/MasteryIntegrationTest.kt`
**Цель:** Проверить связку MasteryStore + FlowerCalculator + SpacedRepetitionConfig

#### Тесты для написания:

```kotlin
@Test fun userCompletesLesson_masteryGrows_flowerBlooms()
@Test fun userSkipsDays_flowerWilts()
@Test fun userReturnsAfter90Days_flowerGone()
@Test fun userRepeatsOnTime_intervalAdvances()
@Test fun userRepeatsLate_intervalStaysn()
@Test fun userReaches150Shows_achieves100PercentMastery()
```

**Метрики успеха:**
- ✅ Полный цикл жизни цветка протестирован
- ✅ Интервалы работают корректно
- ✅ Сохранение/загрузка не ломает состояние

---

### 15. ProgressIntegrationTest.kt [НОВЫЙ]
**Приоритет:** P1 - ВАЖНЫЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/integration/ProgressIntegrationTest.kt`
**Цель:** Проверить связку ProgressStore + TrainingViewModel

#### Тесты для написания:

```kotlin
@Test fun userStartsLesson_progressSaves()
@Test fun userPausesLesson_stateSaves()
@Test fun userCompletesLesson_statisticsSave()
@Test fun userSwitchesMode_progressTransitions()
```

#### Тесты для режима ALL_MIXED:

```kotlin
@Test fun userStartsAllMixed_with500Cards_shows300Only()
@Test fun userStartsAllMixed_with200Cards_showsAll200()
@Test fun userStartsAllMixed_with300Cards_showsAll300()
@Test fun userRestartsAllMixed_getsNewRandomSelection()
```

**Метрики успеха:**
- ✅ Прогресс сохраняется на всех этапах
- ✅ Переключение режимов не ломает состояние
- ✅ Ограничение 300 карточек работает корректно в реальных сценариях

---

## PROPERTY-BASED ТЕСТЫ

### 16. SpacedRepetitionPropertyTest.kt [НОВЫЙ]
**Приоритет:** P1 - ВАЖНЫЙ
**Файл:** `app/src/test/java/com/alexpo/grammermate/property/SpacedRepetitionPropertyTest.kt`
**Цель:** Проверить инварианты алгоритма

#### Properties для тестирования:

```kotlin
@Test fun property_healthNeverExceedsOne()
@Test fun property_healthNeverBelowWiltedThreshold()
@Test fun property_stabilityAlwaysIncreases()
@Test fun property_retentionMonotonicallyDecreases()
@Test fun property_intervalStepNeverDecreases()
```

**Метрики успеха:**
- ✅ Все инварианты держатся для случайных входных данных
- ✅ Граничные значения не нарушают инварианты

---

## СВОДНАЯ ТАБЛИЦА ТЕСТ-ПЛАНА

| # | Тест-файл | Приоритет | Новый/Улучшение | Требования | Статус |
|---|-----------|-----------|-----------------|------------|--------|
| 1 | SpacedRepetitionConfigTest | P0 | НОВЫЙ | FR-9.* | ❌ TODO |
| 2 | FlowerCalculatorTest | P0 | НОВЫЙ | FR-8.1.*, FR-8.3.* | ❌ TODO |
| 3 | MasteryStoreTest | P0 | НОВЫЙ | FR-8.2.*, FR-8.4.*, FR-8.5.* | ❌ TODO |
| 4 | ProgressStoreTest | P0 | НОВЫЙ | FR-7.* | ❌ TODO |
| 5 | NormalizerTest | P0 | НОВЫЙ | FR-6.1.*, FR-6.2.* | ❌ TODO |
| 6 | ProfileStoreTest | P0 | НОВЫЙ | FR-1.1.* | ❌ TODO |
| 7 | LessonStoreTest | P1 | НОВЫЙ | FR-3.* | ❌ TODO |
| 8 | StreakStoreTest | P1 | НОВЫЙ | FR-13.* | ❌ TODO |
| 9 | CsvParserTest | P1 | УЛУЧШЕНИЕ | FR-3.2.3.* | ⚠️ Частично |
| 10 | AtomicFileWriterTest | P1 | УЛУЧШЕНИЕ | FR-2.1.* | ⚠️ Частично |
| 11 | BackupManagerTest | P2 | НОВЫЙ | FR-2.3.* | ❌ TODO |
| 12 | AppConfigStoreTest | P2 | НОВЫЙ | FR-14.1.* | ❌ TODO |
| 13 | VocabCsvParserTest | P2 | УЛУЧШЕНИЕ | FR-11.3.* | ⚠️ Частично |
| 14 | MasteryIntegrationTest | P0 | НОВЫЙ | Интеграция | ❌ TODO |
| 15 | ProgressIntegrationTest | P1 | НОВЫЙ | Интеграция | ❌ TODO |
| 16 | SpacedRepetitionPropertyTest | P1 | НОВЫЙ | Properties | ❌ TODO |

---

## ROADMAP ВЫПОЛНЕНИЯ

### Спринт 1 (P0 - КРИТИЧЕСКИЙ)
**Цель:** Защитить критический функционал

1. ✅ Week 1: SpacedRepetitionConfigTest + FlowerCalculatorTest
2. ✅ Week 2: MasteryStoreTest + ProgressStoreTest
3. ✅ Week 3: NormalizerTest + ProfileStoreTest
4. ✅ Week 4: MasteryIntegrationTest

**Ожидаемое покрытие после спринта 1:** 50-60%

### Спринт 2 (P1 - ВАЖНЫЙ)
**Цель:** Покрыть важный функционал

1. ✅ Week 1: LessonStoreTest
2. ✅ Week 2: StreakStoreTest
3. ✅ Week 3: Улучшение CsvParserTest, AtomicFileWriterTest
4. ✅ Week 4: ProgressIntegrationTest + SpacedRepetitionPropertyTest

**Ожидаемое покрытие после спринта 2:** 75-80%

### Спринт 3 (P2 - ЖЕЛАТЕЛЬНЫЙ)
**Цель:** Покрыть оставшийся функционал

1. ✅ Week 1: BackupManagerTest
2. ✅ Week 2: AppConfigStoreTest
3. ✅ Week 3: Улучшение VocabCsvParserTest
4. ✅ Week 4: Дополнительные integration тесты

**Ожидаемое покрытие после спринта 3:** 90%+

---

## МЕТРИКИ УСПЕХА

### Количественные метрики:
- ✅ **Покрытие кода:** 80%+ для критических классов
- ✅ **Количество тестов:** 200+ unit тестов
- ✅ **Integration тесты:** 10+ сценариев
- ✅ **Property-based тесты:** 5+ properties

### Качественные метрики:
- ✅ **Нулевые регрессии** в критическом функционале при разработке новых фич
- ✅ **Быстрая обратная связь** - тесты выполняются < 30 секунд
- ✅ **Понятные ошибки** - тесты дают четкое понимание что сломалось
- ✅ **Maintainable тесты** - легко читать и модифицировать

---

## ИНФРАСТРУКТУРА ТЕСТИРОВАНИЯ

### Необходимые зависимости:
```gradle
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.mockito:mockito-core:5.3.1'
testImplementation 'org.yaml:snakeyaml:2.0'
testImplementation 'io.kotest:kotest-property:5.6.2' // для property-based тестов
```

### CI/CD интеграция:
- ✅ Все тесты запускаются на каждом PR
- ✅ P0 тесты блокируют merge при падении
- ✅ Отчеты о покрытии генерируются автоматически

### Правила для агентов:
1. **НИКОГДА** не изменять существующие тесты без согласования
2. **ВСЕГДА** запускать тесты перед коммитом изменений
3. **ОБЯЗАТЕЛЬНО** добавлять тесты для новой функциональности
4. При падении тестов - **СНАЧАЛА** исправить тесты, **ПОТОМ** продолжать

---

## ЗАКЛЮЧЕНИЕ

Этот тест-план обеспечивает:
1. ✅ **Защиту от регрессий** - 90%+ покрытие критического функционала
2. ✅ **Уверенность в изменениях** - агенты могут безопасно добавлять фичи
3. ✅ **Быструю обратную связь** - тесты выявляют проблемы до релиза
4. ✅ **Документацию** - тесты показывают как работает код

**Следующие шаги:**
1. Начать с P0 тестов (SpacedRepetitionConfigTest, FlowerCalculatorTest)
2. Постепенно покрывать остальные компоненты
3. Внедрить тесты в CI/CD пайплайн
4. Обучить агентов запускать тесты перед коммитами

---

**Готово к использованию! 🚀**
