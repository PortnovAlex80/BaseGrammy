# 🏗️ Архитектурный разбор GrammarMate — Итоговый отчёт

**Дата:** 2026-06-02

---

## Корневая причина: почему изменения ломают старое

Архитектурный анализ выявил **6 структурных проблем**, которые объясняют, почему pipeline-ы, критерии приёмки и ручные regression-check-и не помогают:

---

### 🔴 Проблема #1: God Object TrainingViewModel (2579 строк)

**Файл:** `ui/TrainingViewModel.kt`

| Метрика | Значение |
|---------|----------|
| Строк | 2,277 |
| Public методов | 71 |
| Ответственностей | 12 |
| Внешних зависимостей (stores) | 13 |
| Feature-экземпляров | 16 |
| Полей в `TrainingUiState` | 120+ |

Этот объект — **единая точка отказа**. Любое изменение в любом из 12 доменов (daily, boss, vocab, pomodoro, chapters, import/export...) требует правок в TrainingViewModel. Нет способа изменить одну функцию, не рискуя сломать другую.

**12 ответственностей TrainingViewModel:**

| # | Ответственность | Ключевые методы | Строки |
|---|----------------|-----------------|--------|
| 1 | Навигация и выбор языка/урока/пака | `selectLanguage`, `selectLesson`, `selectPack`, `clearActivePack`, `selectMode` | 636-832 |
| 2 | Управление сессией тренировки | `submitAnswer`, `nextCard`, `prevCard`, `finishSession`, `togglePause` | 834-936 |
| 3 | Daily Practice (оркестрация) | `startDailyPractice`, `repeatDailyPractice`, `cancelDailySession`, `submitDailySentenceAnswer`, `submitDailyVerbAnswer` | 1214-1370 |
| 4 | Verb Drill сессии | `startVerbDrillSession`, `exitVerbDrillSession`, `replaceVerbDrillCards` | 913-940 |
| 5 | Boss Battle (оркестрация) | `startBossLesson`, `startBossMega`, `finishBoss`, `clearBossRewardMessage` | 1387-1397 |
| 6 | Vocab Sprint | `openVocabSprint`, `submitVocabAnswer` | 1351-1384 |
| 7 | Story Quiz | `completeStory` | 1359-1367 |
| 8 | Pomodoro Timer | `startPomodoro`, `pausePomodoro`, `resumePomodoro`, `cancelPomodoro`, `rateCardDifficulty` | 328-450 |
| 9 | Audio/TTS/ASR (делегирование) | `speakStoryText`, `speakMultilingualStory`, `stopStoryNarration`, `startOfflineRecognition` | 379-407, 1438-1457 |
| 10 | Импорт/экспорт данных | `importLesson`, `importLessonPack`, `resetAndImportLesson`, `deleteLesson`, `createEmptyLesson`, `addLanguage`, `restoreBackup` | 989-1100, 1546-1556 |
| 11 | Grammar Story Roadmap (главы) | `getChapterCards`, `loadStoryContent`, `getFirstIncompleteLesson`, `loadChapters`, `updateChapterProgress` | 1985-2258 |
| 12 | Сохранение прогресса и reset | `saveProgress`, `resetAllProgress`, `resetLanguageProgress`, `refreshStreakFromStore` | 1724-1910 |

**State-переменные:** TrainingViewModel управляет одним гигантским `TrainingUiState` через `_coreState: MutableStateFlow<TrainingUiState>`. Этот стейт содержит **9 вложенных структур** общей сложностью более **120 полей**:

```
TrainingUiState (Models.kt:596-651)
  +-- NavigationState       -- 15 полей (languages, packs, lessons, activePackId, selectedLessonId...)
  +-- CardSessionState       -- 40+ полей (currentCard, inputText, inputMode, counters, sub-lessons...)
  +-- BossState              -- 10 полей (bossActive, bossType, bossProgress, rewards...)
  +-- StoryState             -- 4 поля
  +-- VocabSprintState       -- 11 полей
  +-- EliteState             -- 6 полей
  +-- FlowerDisplayState     -- 3 поля
  +-- AudioState             -- 18 полей (TTS, ASR, download states...)
  +-- DailyPracticeState     -- 2 поля (но DailySessionState внутри имеет ещё вложения)
  +-- PomodoroState          -- 10 полей
  +-- chapters, chapterProgresses, activeChapterId  -- Grammar Story Roadmap
  +-- isLoading, parseErrors, showParseWarning      -- UI-состояние загрузки
  +-- storyReaderChapterTitle, storyReaderContent    -- Story Reader
```

Плюс **14 приватных переменных** (строки 105-148), включая `wordMasteryStore` (var — мутируется при смене пака).

**Side-effects через 13 store-зависимостей + 16 feature-экземпляров.**

---

### 🔴 Проблема #2: Shotgun Surgery — Reset Cascade

При смене языка/урока/пака нужно вызвать **10+ reset-методов в правильном порядке**. Этот паттерн дублирован **в 7 местах**:

```
selectLanguage(), selectLesson(), selectMode(), 
importLessonPack(), addLanguage(), refreshLessons(), selectPack()
```

Каждый раз:
```
bossOrchestrator.reset() → storyRunner.reset() → vocabSprintRunner.reset() 
→ dailyCoordinator.reset() → refreshDrillVisibility() → rebuildSchedules() 
→ buildSessionCards() → refreshFlowerStates() → loadChapters() → saveProgress()
```

**Если добавить новый модуль и забыть добавить его reset в одно из 7 мест — приложение в inconsistent state.**

---

### 🔴 Проблема #3: 6 Flow → 1 State (TOCTOU races)

`uiState` — это `combine()` из **6 независимых MutableStateFlow**:

```kotlin
combine(_coreState, audioCoordinator.audioState, storyRunner.stateFlow,
        vocabSprintRunner.vocabState, dailyPracticeCoordinator.dailyState, ...)
```

Код прямо документирует баг: *"after updateCursor() the MutableStateFlow is updated immediately but the downstream combine may not have propagated yet, so uiState.value can contain a stale cursor"* (`TrainingViewModel.kt:1726-1731`).

**Мутация state распределена по нескольким потокам выполнения:**
1. `_coreState.update {}` вызывается из главного потока, `Dispatchers.IO`, и feature-хелперов
2. Итоговый `uiState` — результат объединения 6 разных `MutableStateFlow`, каждый из которых обновляется независимо
3. При чтении `uiState.value` в `saveProgress()` можно получить stale snapshot

---

### 🔴 Проблема #4: `wordMasteryStore` — mutable var при переключении пака

`wordMasteryStore` — это `var` (строка 100), который мутируется при смене пака. Между обновлением `activePackId` в NavigationState и вызовом `rebindWordMasteryStore()` есть **окно**, в которое запись может уйти не в тот пак.

**Проблема переключения пака (selectPack, строки 750-788):**
1. Отменяет daily session, если активна
2. Синхронизирует язык с языком пака
3. Обновляет audio (TTS/ASR) для нового языка
4. Вызывает `selectLesson(lessonId, packId)` — который делает ещё 15 операций
5. Если любая операция не выполнится или выполнится в неправильном порядке — inconsistent state

---

### 🔴 Проблема #5: Stringly-typed навигация + Token-based coupling

- `returnTo` — строка (`"lesson"`, `"daily_practice"`, `"verb_drill"`). Опечатка = runtime-баг
- `subLessonFinishedToken` (integer) связывает `SessionRunner` с `GrammarMateApp` — implicit coupling через наблюдение за числом
- 7 `BackHandler`-ов на разных уровнях — порядок регистрации определяет приоритет

**GrammarMateApp.kt — 1998 строк** — compositional god-object: навигация + диалоги + screen-контент + helper-функции.

---

### 🔴 Проблема #6: VerbDrill/VocabDrill ViewModel-и — скрытый coupling

Они **не разделяют state** с TrainingViewModel, но пишут в те же stores. TrainingViewModel должен вручную вызывать `refreshStreakFromStore()`. **Забытый refresh = stale UI**, и это не ловится никакими проверками.

---

## Структура проекта

```
com.alexpo.grammermate/
  +-- data/           (63 файла) -- хранилища, модели, парсеры, инфраструктура
  +-- feature/         (26 файлов) -- бизнес-логика, разделённая по доменам
  |    +-- boss/       (3 файла)   -- BossOrchestrator, BossBattleRunner, BossResult
  |    +-- daily/      (3 файла)   -- DailyPracticeCoordinator, DailySessionHelper, DailySessionComposer
  |    +-- pomodoro/   (1 файл)    -- PomodoroHelper
  |    +-- progress/   (8 файлов)  -- ProgressTracker, StreakManager, FlowerRefresher, ChapterProgressCalculator...
  |    +-- training/   (9 файлов)  -- SessionRunner, CardProvider, AnswerValidator, CardSessionStateMachine...
  |    +-- vocab/      (2 файла)   -- VocabSprintRunner, VocabResult
  +-- shared/          (3 файла)   -- SettingsActionHandler, ScreenLogger, AudioCoordinator
  +-- ui/              (39 файлов) -- ViewModel'и, экраны, компоненты, навигация
  |    +-- components/ (18 файлов) -- переиспользуемые UI-компоненты
  |    +-- screens/    (8 файлов)  -- отдельные экраны
  +-- GrammarMateApplication.kt    -- Application-класс с AppContainer
  +-- AppContainer.kt              -- DI-контейнер
  +-- MainActivity.kt              -- единственная Activity
```

### Карта зависимостей между пакетами

```
ui/ --> data/          (прямое чтение stores, моделей)
ui/ --> feature/       (делегирование бизнес-логике)
ui/ --> shared/        (SettingsActionHandler, AudioCoordinator)

feature/ --> data/     (stores, модели)
feature/ --> feature/  (StreakManager зависит от data/StreakStore)

shared/ --> data/      (AudioCoordinator использует configStore, TTS/ASR)

data/ --> data/        (внутренние зависимости: StoreFactory -> stores)
```

Циклических зависимостей между пакетами нет.

---

## Data Layer

### Все хранилища данных

| Store | Формат | Файл | Потокобезопасность | Кеширование |
|-------|--------|------|-------------------|-------------|
| `ProgressStore` | YAML | `grammarmate/progress.yaml` | `ReentrantLock` | Нет |
| `MasteryStore` | YAML | `grammarmate/mastery.yaml` | `ReentrantLock` | Да (in-memory) |
| `WordMasteryStore` | YAML | `grammarmate/drills/{packId}/word_mastery.yaml` | `ReentrantLock` | Да (in-memory) |
| `AppConfigStore` | YAML | `grammarmate/config.yaml` | Нет | Нет |
| `StreakStore` | YAML | `grammarmate/streak_{langId}.yaml` | Нет | Нет |
| `BadSentenceStore` | YAML | `grammarmate/bad_sentences.yaml` | Нет | Нет |
| `HiddenCardStore` | YAML | `grammarmate/hidden_cards.yaml` | Нет | Нет |
| `VocabProgressStore` | YAML | `grammarmate/vocab_progress_{langId}.yaml` | Нет | Нет |
| `ProfileStore` | YAML | `grammarmate/profile.yaml` | Нет | Нет |
| `VerbDrillStore` | YAML | `grammarmate/drills/{packId}/verb_drill_progress.yaml` | `ReentrantLock` | Да |
| `DrillProgressStore` | YAML | `grammarmate/drill_progress_{langId}.yaml` | Нет | Нет |
| `PackLessonProgressStore` | YAML | `grammarmate/lesson_progress_{packId}.yaml` | Нет | Нет |
| `PackDailyCursorStore` | YAML | `grammarmate/daily_cursor_{packId}.yaml` | Нет | Нет |
| `ChapterProgressStore` | YAML | `grammarmate/chapter_progress_{packId}.yaml` | Нет | Нет |
| `PomodoroHistoryStore` | YAML | `grammarmate/pomodoro_history.yaml` | Нет | Нет |
| `PomodoroSettingsStore` | YAML | `grammarmate/pomodoro_settings.yaml` | Нет | Нет |

### Проблемы Data Layer

- **Нет единого источника истины.** Данные размазаны по 15+ YAML-файлам. Нет единой транзакции.
- **Неатомарные составные операции.** `saveProgress()` последовательно пишет в `PackLessonProgressStore` и `ProgressStore`, но mastery-данные пишутся отдельно. Краш между записями = рассинхрон.
- **Pack-scoped stores.** При переключении пака — risk of writing to wrong pack if timing is off.

---

## 📊 Текущее покрытие тестами

### Цифры

| Слой | Покрытие | Оценка |
|------|----------|--------|
| Data (парсеры, калькуляторы, stores) | ~70% | ✅ Хорошо |
| Feature (бизнес-логика) | ~10% | ⚠️ Слабо |
| ViewModel (TrainingViewModel) | **~5%** | 🔴 Критический пробел |
| UI (Compose screens) | ~20% | ⚠️ Слабо |
| Навигация | **0%** | 🔴 Ноль |
| Onboarding | **0%** | 🔴 Ноль |

**Из ~230 тестов в 34 файлах:**
- **~120** — настоящие unit-тесты высокого качества (data layer)
- **~25** — настоящие UI-тесты (VerbDrill regression, PauseCascade)
- **~50** — тавтологии: установить X, проверить X == X
- **~35** — smoke-тесты (доказывают что Compose+Robolectric работают)

**Реальное покрытие оценивается в 15-20%** значимых пользовательских путей.

### Полный список тестовых файлов

#### УРОВЕНЬ ДАННЫХ — Модульные тесты (высокое качество)

| Файл | Что тестируется | Кейсы |
|------|-----------------|-------|
| `data/AtomicFileWriterTest.kt` | Атомарная запись | 6 |
| `data/SpacedRepetitionConfigTest.kt` | Кривая забывания Эббингауза | 17 |
| `data/FlowerCalculatorTest.kt` | Расчет состояния цветков | 17 |
| `data/NormalizerTest.kt` | Нормализация ответов | 22 |
| `data/LessonLadderCalculatorTest.kt` | Метрики урока | 5 |
| `data/MixedReviewSchedulerTest.kt` | Планирование повторений | 7 |
| `data/CsvParserTest.kt` | Парсинг CSV | 11 |
| `data/ParseResultTest.kt` | Результат парсинга | 16 |
| `data/LessonPackManifestTest.kt` | Манифест пака | 1 |
| `data/StoryQuizParserTest.kt` | Парсинг викторин | 3 |
| `data/VerbDrillCsvParserTest.kt` | Парсинг спряжения глаголов | 6 |
| `data/VocabCsvParserTest.kt` | Парсинг словарного запаса | 4 |
| `data/WriteVerificationTest.kt` | Запись/чтение всех stores | 13 |
| `data/PomodoroHistoryStoreTest.kt` | История Pomodoro | 1 |
| `data/MigrationManagerTest.kt` | Миграции, бэкап, откат | 14 |
| `data/validation/DataValidatorTest.kt` | Валидация данных | 22 |
| `data/ExternalLessonLoaderTest.kt` | Шаблоны файлов | 4 |

#### УРОВЕНЬ ЛОГИКИ

| Файл | Что тестируется | Кейсы |
|------|-----------------|-------|
| `feature/pomodoro/PomodoroHelperTest.kt` | Завершение Pomodoro | 1 |
| `feature/progress/ChapterProgressCalculatorTest.kt` | Прогресс глав | 7 |

#### ИНТЕГРАЦИЯ

| Файл | Что тестируется | Кейсы |
|------|-----------------|-------|
| `ui/TrainingViewModelChapterIntegrationTest.kt` | Главы + ViewModel | 7 |

#### UI — Тесты взаимодействия

| Файл | Что тестируется | Кейсы | Качество |
|------|-----------------|-------|----------|
| `ui/SmokeClickTest.kt` | Клик по кнопке | 1 | Smoke |
| `ui/InputModeSwitchTest.kt` | Переключение режима | 1 | Smoke |
| `ui/SubmitAnswerTest.kt` | Отправка ответа | 1 | Smoke |
| `ui/NavigationClickTest.kt` | Обратная навигация | 1 | Smoke |
| `ui/WordBankToggleTest.kt` | Банк слов | 1 | Smoke |
| `ui/MinimalComposeRobolectricTest.kt` | Compose+Robolectric | 1 | Smoke |
| `ui/RegularLessonClickUiTest.kt` | Обычный урок | 10 | Среднее |
| `ui/BossBattleClickUiTest.kt` | Прогресс битвы | 2 | Низкое-Среднее |
| `ui/DailyPracticeClickUiTest.kt` | Ежедневная практика | 12 | Низкое-Среднее |
| `ui/PauseCascadeClickUiTest.kt` | Поведение при паузе | 8 | Среднее |
| `ui/PomodoroClickUiTest.kt` | Таймер Pomodoro | 10 | Низкое-Среднее |
| `ui/PomodoroBannerClickUiTest.kt` | Баннер Pomodoro | 6 | Среднее |
| `ui/VerbDrillSessionCardRegressionTest.kt` | SessionCard repeat/continue/reset | 4 | Высокое |
| `ui/VerbPracticeClickUiTest.kt` | Выбор глаголов | 13 | Низкое |

#### Instrumented test

| Файл | Что тестируется | Кейсы |
|------|-----------------|-------|
| `androidTest/ui/VerbDrillScreenStartFreshResumeTest.kt` | Начало/возобновление Verb Drill | 7 |

---

### Критические непокрытые user journeys

| User Journey | Тесты? | Приоритет | Тип |
|-------------|--------|-----------|-----|
| Первый запуск → выбор языка → выбор пака | ❌ | P0 | Instrumented/UI |
| Pack Selection → Story Roadmap → Training → Back | ❌ | P0 | Instrumented |
| TrainingViewModel.submitAnswer() правильный/неправильный | ❌ | P0 | Unit |
| StreakStore: инкремент, сброс, same-day | ❌ | P0 | Unit |
| TrainingViewModel.selectMode переходы состояний | ❌ | P0 | Unit |
| WORD_BANK mode → flowers NOT grown (инвариант) | ❌ | P0 | Unit |
| Навигация: все маршруты | ❌ | P1 | UI |
| Vocab Sprint: полный цикл | ❌ | P1 | UI |
| Story Reader: markdown, quiz | ❌ | P1 | UI |
| Daily Practice: генерация блоков, переход | ❌ | P1 | Unit |
| Boss Battle: прогресс, награда | ❌ | P1 | Unit |
| Настройки: конфигурация | ❌ | P2 | UI |
| Импорт пака: загрузка, распаковка, валидация | ❌ | P2 | Integration |

---

## 🛒 Claude Code Marketplace — что есть

### Установленные в проекте

| Skill | Помогает? | Проблема |
|-------|-----------|----------|
| `regression-check` | Частично | Читает код и проверяет AC через AI — но **не запускает автотесты** |
| `verify-user-journey` | Частично | Чеклист для мысленного прохождения — но **не автоматизирован** |
| `swarm` | Косвенно | Мультиагентная оркестрация |
| `add-feature` | Косвенно | Включает regression-check на этапе 5 |

**Ключевое:** `regression-check` сопоставляет `git diff → затронутые экраны → AC из спецификации → проверка кода`. Он зависит от файлов спецификаций (`22-use-case-registry.md`, `23-screen-elements.md`), и эти файлы могут быть неполными. Он **НЕ выполняет автоматические тесты** — он читает код и проверяет, удовлетворяются ли AC. Это ручной/AI-подход, а не автоматическое выполнение тестов.

### Внешние плагины (стоит установить)

| Плагин | Что даёт | Релевантность | Ссылка |
|--------|----------|---------------|--------|
| **maestro-mcp** | E2E тестирование на эмуляторе/устройстве через MCP. Claude буквально кликает по UI | 🔴 **Критический** | [claudepluginhub.com/plugins/slapglif-maestro-mcp](https://www.claudepluginhub.com/plugins/slapglif-maestro-mcp) |
| **MobAI** | Прямое управление мобильным устройством из Claude Code | 🟡 Высокая | [reddit.com/r/ClaudeCode](https://www.reddit.com/r/ClaudeCode/comments/1qnr2zc/) |
| **obra/superpowers: TDD** | RED-GREEN-REFACTOR цикл для агентов | 🟡 Высокая | [github.com/obra/superpowers](https://github.com/obra/superpowers) |
| **obra/superpowers: verification-before-completion** | Обязательная проверка перед завершением | 🟡 Высокая | [github.com/obra/superpowers](https://github.com/obra/superpowers) |
| **Visual Regression Expert** | Сравнение скриншотов через AI | 🟢 Средняя | [mcpmarket.com](https://mcpmarket.com/tools/skills/visual-regression-expert) |
| **Architecture Review Skill** | Структурный анализ и картирование зависимостей | 🟢 Средняя | [mcpmarket.com](https://mcpmarket.com/tools/skills/architecture-review-1) |

---

## 🎯 Рекомендации: как решить проблему

### Решение 1: Maestro MCP — кликабельные E2E тесты (самое важное)

**Maestro** — это фреймворк UI-автоматизации для Android/iOS, который работает через YAML-файлы. С плагином `maestro-mcp` Claude сможет:

1. Запускать тесты на эмуляторе/устройстве
2. Кликать по элементам
3. Проверять видимость текстов
4. Делать скриншоты и сравнивать

Пример Maestro flow:
```yaml
# flow: onboarding_to_first_lesson.yaml
- launchApp
- assertVisible: "Select Language"
- tapOn: "English"
- assertVisible: "Select Pack"
- tapOn: "ru-en-v1"
- assertVisible: "Chapter 1"
- tapOn: "Continue"
- assertVisible: "Type your answer"
```

### Решение 2: State Machine тесты для TrainingUiState

Создать формальную модель состояний и переходов, затем протестировать каждый переход:

```
HOME → (selectPack) → STORY_ROADMAP → (selectLesson) → TRAINING
TRAINING → (submitAnswer) → TRAINING (next card)
TRAINING → (finishSession) → STORY_ROADMAP
TRAINING → (back) → STORY_ROADMAP
STORY_ROADMAP → (back) → HOME
```

### Решение 3: Contract-тесты между слоями

Для каждого store — протестировать контракт:
- `save(X)` → `load()` == X
- `recordCompletion()` → streak incremented
- `WORD_BANK mode` → flowers NOT grown (инвариант)

### Решение 4: Приоритизированный план написания тестов

| Волна | Что | Тип | Количество |
|-------|-----|-----|------------|
| **1** | TrainingViewModel: submitAnswer, navigate, selectMode | Unit | ~15 тестов |
| **2** | StreakStore логика, MasteryStore инварианты | Unit | ~10 тестов |
| **3** | Навигация: Pack → Roadmap → Training → Back | UI/Maestro | ~8 flows |
| **4** | Daily/Boss/Vocab полные циклы | UI/Maestro | ~10 flows |
| **5** | Onboarding: первый запуск | UI/Maestro | ~5 flows |

### Решение 5: Изменить процесс (CLAUDE.md)

Добавить правило: **НИКАКИХ правок без preceded by failing test**:

```markdown
## MANDATORY: Test-First Rule
Before ANY change to TrainingViewModel, SessionRunner, DailyPracticeCoordinator,
BossBattleRunner, or GrammarMateApp:

1. Write a FAILING test that exercises the current behavior
2. Confirm test fails (RED)
3. Make the change
4. Confirm test passes (GREEN)
5. Run ALL existing tests — zero failures allowed
```

---

## Сводка хрупких coupling-точек

### Критические (могут вызывать баги данных)

1. **`wordMasteryStore` — mutable var** (TrainingViewModel:100). Окно между обновлением `activePackId` и `rebindWordMasteryStore()` — запись может уйти не в тот пак.

2. **Stale cursor in `saveProgress()`** (TrainingViewModel:1726-1731). combine() flow может не успеть распространить обновление.

3. **`init`-блок на двух dispatchers** (TrainingViewModel:457-610). Мутация `_coreState` из двух потоков.

4. **VerbDrillViewModel / VocabDrillViewModel не в едином state flow.** Забытый `refreshStreakFromStore()` = stale UI.

### Высокие (увеличивают риск при модификации)

5. **Reset cascade** — 7 мест с идентичным набором из 10+ вызовов reset.

6. **Token-based navigation** в GrammarMateApp (строки 1374-1527).

7. **`returnTo` string protocol** — нет типобезопасности.

8. **`determinePracticeType()` всегда возвращает TRANSLATION** — dead code.

### Умеренные (ухудшают поддерживаемость)

9. **`TrainingUiState` — 120+ полей.** Любое добавление поля требует обновления всех `copy()` вызовов.

10. **`GrammarMateApp.kt` — 1998 строк.** Compositional god-object.

11. **15+ YAML stores без единой транзакции.**

---

## Ключевые файлы

### Ядро (самые хрупкие)
- `ui/TrainingViewModel.kt` — центральный god-object, 2579 строк
- `ui/GrammarMateApp.kt` — навигация + диалоги, 1998 строк
- `data/Models.kt` — все модели данных, 778 строк

### DI и Stores
- `AppContainer.kt` — DI-контейнер
- `data/StoreFactory.kt` — фабрика stores
- `data/AtomicFileWriter.kt` — атомарная запись

### Feature layer
- `feature/training/SessionRunner.kt` — управление сессией, 1416 строк
- `feature/daily/DailyPracticeCoordinator.kt` — daily practice
- `feature/progress/ProgressTracker.kt` — трекинг прогресса

### Отдельные ViewModel-и
- `ui/VerbDrillViewModel.kt`
- `ui/VocabDrillViewModel.kt`

### Тестовая инфраструктура
- `test-harness/FakeMasteryStore.kt`
- `test-harness/FakeStreakStore.kt`
- `test-harness/FakeTrainingStateAccess.kt`
- `test-harness/FakeVerbDrillStore.kt`

### Эталонные тесты (образцы качества)
- `data/SpacedRepetitionConfigTest.kt`
- `data/FlowerCalculatorTest.kt`
- `data/NormalizerTest.kt`
- `ui/VerbDrillSessionCardRegressionTest.kt`

---

## Резюме

**Причина ломания не в отсутствии регрессионных проверок — а в god-object на 2579 строк с 12 ответственностями, 6 конкурирующими flow, и shotgun-surgery reset-каскадом в 7 местах, при нулевом тестовом покрытии ViewModel и навигации.** Pipelines и acceptance criteria не помогают потому, что хрупкость структурная — нет стен между доменами, и любое изменение каскадом идёт через весь god-object.

**Следующий шаг:** установить `maestro-mcp` плагин и написать YAML-флоу для критических user journeys — это даст реальные кликабельные автотесты, которые агент не сможет "пропустить".

---

## Пайплайн языкового пака: анализ и паттерны проектирования

### Текущее состояние: все EXPRESS-паки идентичны

| Компонент | GERMAN | CHINESE | RUSSIAN | GREEK | ITALIAN |
|-----------|--------|---------|---------|-------|---------|
| schemaVersion | 2 | 2 | 2 | 2 | 2 |
| Главы | 3 | 3 | 3 | 3 | 3 |
| Уроков | 24 (12+12) | 24 | 24 | 24 | 24 |
| Verb drill | 1 файл | 1 | 1 | 1 | 1 |
| Vocab drill | 6 файлов | 6 | 6 | 6 | 6 |
| Stories | 3 md | 3 md | 3 md | 3 md | 3 md |

Единственная разница — **содержание CSV файлов** (текст на целевом языке). Конструкт — 100% одинаковый.

### Текущий pipeline загрузки пака

```
ZIP → unpack → manifest.json → validate → copy files → parse CSV → register → activate
```

**3 дыры в стандартизации:**
1. `pack_validator.py` не поддерживает schema v2 — бесполезен для всех EXPRESS-паков
2. Drill CSV не валидируются при импорте — ошибки всплывают только при использовании
3. Нет темплейта/скаффолда — каждый пак создаётся копированием и ручной правкой

### Полная карта pipeline: от "сырые файлы" до "работающий пак"

```
[1] ИСТОЧНИК
    ZIP файл (assets или SAF URI)
         |
[2] РАСПАКОВКА (PackImporter.extractZipToTemp)
    ZIP -> tmp_UUID/ с path traversal защитой
         |
[3] MANIFEST ВАЛИДАЦИЯ (LessonPackManifest.fromJson)
    - schemaVersion: 1|2
    - packId, packVersion, language: непустые
    - lessons/chapters: минимальный контент
    -> error() при невалидном -- загрузка прерывается
         |
[4] ПОДГОТОВКА ОКРУЖЕНИЯ
    - languageEnsurer(languageId) -> создаёт язык если нет
    - removePacksForLanguage(packId, langId) -> удаляет старую версию
    - Очистка packDir если существует
         |
[5] КОПИРОВАНИЕ ФАЙЛОВ
    tempDir -> packs/PACK_ID/ (включая stories/, CSV, manifest.json)
         |
[6] ИМПОРТ УРОКОВ (importLessonFromFile x N)
    Для каждого CSV:
    - AtomicFileWriter -> lessons/LANG/lesson_ID.csv
    - CsvParser.parseLesson() -> SentenceCard list
    - replaceById() или replaceByTitle() -> дедупликация
    - lessonIndexWriter() -> LANG_index.yaml
    - Ошибки логируются, не прерывают импорт
         |
[7] ИМПОРТ DRILLS (importPackDrills)
    verbDrill.files -> drills/PACK_ID/verb_drill/
    vocabDrill.files -> drills/PACK_ID/vocab_drill/
    - Содержимое НЕ валидируется
         |
[8] ИМПОРТ STORIES (importStoriesFromPack)
    JSON файлы (!= manifest.json) -> stories/
    - StoryQuizParser.parse() -> валидация
         |
[9] ИМПОРТ VOCAB (importVocabFromPack)
    vocab_*.csv -> vocab/LANG/
         |
[10] РЕГИСТРАЦИЯ ПАКА
    packs.yaml += { packId, packVersion, languageId, importedAt }
         |
[11] АКТИВАЦИЯ (TrainingViewModel.selectPack)
    - Синхронизация TTS/ASR языка
    - selectLesson() или drill-only path
    - rebindWordMasteryStore(packId)
    - refreshDrillVisibility()
    - loadChapters()
    - saveProgress()
```

### Применение паттернов GoF и TOGAF

#### Паттерн #1: Template Method (GoF) — "Один pipeline, разный контент"

Прямой hit для ситуации "один алгоритм, разный контент". Сейчас pipeline размазан по `PackImporter` с if-ами на `schemaVersion`. Template Method делает шаги явными и незабываемыми.

```
┌─────────────────────────────────┐
│  PackPipeline (abstract)        │
│─────────────────────────────────│
│ + importPack()  ← template      │
│   1. validateManifest()         │
│   2. extractContent()           │
│   3. parseLessons()             │
│   4. parseDrills()              │
│   5. parseStories()             │
│   6. validate()                 │
│   7. register()                 │
│   8. activate()                 │
│─────────────────────────────────│
│ # validateManifest()  ← hook    │
│ # extractContent()   ← hook    │
│ # parseLessons()     ← hook    │
└────────┬────────────────────────┘
         │
    ┌────┴────┬────────────┬───────────┐
    ▼         ▼            ▼           ▼
 SchemaV1  SchemaV2    DrillOnly   FullPack
Pipeline   Pipeline    Pipeline    Pipeline
```

#### Паттерн #2: Mediator (GoF) — "Забыл добавить reset в одно из 7 мест"

Сейчас TrainingViewModel вручную орkestрирует 16 модулей. Mediator централизует рассылку событий. Новый модуль = новый подписчик, не нужно трогать 7 мест.

```
СЕЙЧАС (Shotgun Surgery):
═══════════════════════════
selectPack() → boss.reset()
             → story.reset()
             → vocab.reset()
             → daily.reset()
             → refreshDrillVisibility()
             → rebuildSchedules()
             → buildSessionCards()
             → refreshFlowerStates()
             → loadChapters()
             → saveProgress()
   ↑ этот же блок в 7 местах ↑

С MEDIATOR:
═══════════
selectPack() → PackMediator.onPackChanged(packId)
                   → сам рассылает всем подписчикам
```

#### Паттерн #3: Chain of Responsibility (GoF) — "Валидация — дырявая"

Проверка пака — цепочка независимых валидаторов. Каждый валидатор — один класс, одна ответственность. Легко добавить новый.

```
manifest.json → SchemaValidator → ContentValidator → CSVValidator → DrillValidator → StoryValidator
                      │                  │                 │              │                │
                   skip?             skip?             skip?          skip?            skip?
                      │                  │                 │              │                │
                      ▼                  ▼                 ▼              ▼                ▼
                   errors            errors            errors         errors           errors
                      │                  │                 │              │                │
                      └──────────────────┴─────────────────┴──────────────┴────────────────┘
                                              │
                                         ValidationResult
                                    (pass / warn / fail)
```

#### Паттерн #4: Prototype + Builder (GoF) — "Создание пака — ручное копирование"

```
PackBuilder.create("FRENCH_EXPRESS")
    .fromTemplate(ExpressPackTemplate)     // Prototype — клонирует структуру
    .language("fr")
    .lessons(loadFrom("french_lessons.csv"))
    .verbDrill(loadFrom("fr_verbs.csv"))
    .vocabDrill(loadFrom("fr_nouns.csv"), loadFrom("fr_adjectives.csv"))
    .build()                                // Builder — пошаговая сборка
    .validate(ChainOfValidators)            // Chain of Responsibility
    .exportTo("FRENCH_EXPRESS.zip")         // готовый пак
```

#### Паттерн #5: Pipeline / Gates (TOGAF) — "Активация пака — хрупкая"

Из TOGAF — паттерн Architecture Continuum с validation gates. Каждый Gate — checkpoint. Если валидация не прошла — pipeline останавливается, состояние откатывается.

```
[Phase A]      [Phase B]       [Phase C]       [Phase D]
 CREATION  →   VALIDATION →   IMPORT     →    ACTIVATION
                GATE ▼          GATE ▼          GATE ▼
             schema ok?      CSV parsed?     stores bound?
                │                │                │
              FAIL → стоп     FAIL → стоп     FAIL → откат
```

Сейчас fallback на "пак создаётся даже с ошибками" — это опасно. Gates гарантируют: не прошёл валидацию — не активируем.

### Сводная таблица паттернов

| Боль | Паттерн | Источник | Что даёт |
|------|---------|----------|----------|
| Один pipeline, разный контент | **Template Method** | GoF | Шаги явные, незабываемые |
| Reset cascade в 7 местах | **Mediator** | GoF | Новый модуль = новый подписчик |
| Дырявая валидация пака | **Chain of Responsibility** | GoF | Каждый валидатор — отдельный класс |
| Ручное создание пака | **Prototype + Builder** | GoF | Клонирование шаблона + пошаговая сборка |
| Хрупкая активация | **Pipeline / Gates** | TOGAF | Checkpoint → откат при ошибке |

**Приоритет внедрения:** Template Method + Mediator решают 80% боли. Остальное наращивается постепенно.

### Ключевые файлы pipeline

| Файл | Назначение |
|------|-----------|
| `data/LessonStore.kt` | Фасад, координация делегатов, загрузка уроков |
| `data/PackImporter.kt` | ZIP-распаковка, импорт manifest/lessons/drills/stories/vocab |
| `data/LanguageManager.kt` | Языки, packs.yaml, seed/default packs |
| `data/LessonPackManifest.kt` | Manifest модель + парсинг + валидация |
| `data/CsvParser.kt` | Парсинг lesson CSV (semicolon-delimited) |
| `data/DrillFileManager.kt` | Drill/stories/vocab queries |
| `data/CsvLineParser.kt` | Общий semicolon-delimited line parser |
| `data/ItalianDrillVocabParser.kt` | Парсинг drill CSV (comma-delimited) |
| `data/VocabCsvParser.kt` | Парсинг vocab CSV |
| `data/Models.kt` | Все data модели (Lesson, Pack, Chapter, SentenceCard...) |
| `data/ParseResult.kt` | Generic parse result + ParseError sealed class |
| `tools/pack_validator/pack_validator.py` | Внешний валидатор (устарел для v2) |
