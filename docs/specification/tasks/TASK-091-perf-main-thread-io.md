# TASK-091: Производительность — убрать дисковый I/O и лишние рекомпозиции с главного потока

**Status:** PERF (не баг — поведение корректное, но дорогое)
**Created:** 2026-09-13
**Branch:** создать `feature/perf-main-thread-io` от текущей ветки (НЕ от `main`)
**Источник:** аудит архитектуры, раздел «1. Производительность»
**Spec:** `20-non-functional-requirements.md`, `02-data-stores.md`

---

## Контекст для агента

GrammarMate — Android-приложение (Kotlin 2.0.21, Jetpack Compose, Material 3) для тренировки
перевода RU→целевой язык. Прогресс визуализируется метафорой растущего цветка на кривой
забывания Эббингауза.

Задача **чисто про производительность**. Наблюдаемое поведение приложения — что засчитывается,
как растёт цветок, какие карточки попадают в повторение — меняться **не должно**. Все находки
ниже проверены чтением кода; строки указаны по состоянию на дату создания задачи, при
расхождении ориентируйся на имена функций.

Каждый пункт независим. Делай их **по одному**, с прогоном тестов после каждого.

---

## Границы задачи

### Не трогать (это отдельная задача про бизнес-логику)

- `SpacedRepetitionConfig` — любые формулы, пороги, лестницу интервалов
- `FlowerCalculator` — расчёт mastery/health/scale и пороги состояний цветка
- `ReviewSelector`, `MixedReviewScheduler` — отбор карточек повторения
- `ProgressTracker.checkAndMarkLessonCompleted` — правило завершения урока
- Условия, при которых вызываются `recordCardShowForPack` / `recordSelfProducedForPack` /
  `recordCardEncounterForPack`. Меняем **как** пишем, а не **когда** и **что**.

### Не менять схему данных

YAML-схемы на диске (`mastery.yaml`, `progress.yaml`, pack-scoped файлы) остаются
байт-совместимыми. Никаких миграций. `schemaVersion` не трогать.

### Не терять гарантии сохранности

Любое батчирование записи обязано сохранять инвариант: **после `flush()` данные на диске**.
Паттерн `AtomicFileWriter` (temp → fsync → rename) остаётся обязательным для всех записей.

---

## Пункт 1. Батчинг записи MasteryStore ⭐ главный выигрыш

**Файлы:** `app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt`

### Проблема

`MasteryStoreImpl.persistToFileLocked()` сериализует **весь снимок** mastery через SnakeYAML и
пишет его с `fsync` при **каждой** мутации. Мутаций на одну карточку — три:

| Метод | Откуда вызывается |
|---|---|
| `recordCardShowForPack` | `TrainingViewModel.handleSessionEvents` → `SessionEvent.RecordCardShow` (~строка 2152) |
| `recordCardEncounterForPack` | там же, следующей строкой (~2153) |
| `recordSelfProducedForPack` | `TrainingViewModel.recordSelfProducedIfEarned` (~1336), на каждый принятый ответ |

`handleSessionEvents` вызывается из UI-колбэков, то есть всё это **главный поток**.

Payload растёт неограниченно: в него идут `shownCardIds` (список) и `cardEncounterCounts`
(мапа) по каждому уроку пака. Для `ITALIAN_FULL_COURSE` это 63 урока × медиана 113 карточек
≈ 7100 карточек → YAML на сотни килобайт, дампится и fsync-ается три раза на карточку.

`flush()` при этом — честный no-op (см. комментарий в коде), хотя API под батчинг уже есть.

### Что уже готово

Точки сброса **уже расставлены по коду**, менять места вызова не нужно:

- `TrainingViewModel.onAppBackgrounded()` (~589)
- `TrainingViewModel.onCleared()` (~1994)
- `TrainingViewModel.saveProgress()` (~2336) — срабатывает на границах под-уроков и по таймеру

### Что сделать

1. Ввести dirty-флаг и единственный фоновый писатель (одно-поточный executor либо
   `CoroutineScope(Dispatchers.IO)` с `Mutex`, на твой выбор — но писатель строго один,
   чтобы сохранить детерминированный порядок записи).
2. Мутирующие методы: обновляют **только in-memory кеш** под `mutex`, ставят dirty и
   планируют запись с debounce ~1000–2000 мс. Синхронной записи в них больше нет.
3. `flush()` делает настоящий сброс: **блокирующе** дожидается, пока pending-запись
   окажется на диске. Он вызывается из `onCleared()`, где после возврата процесс может
   умереть — асинхронный flush здесь недопустим.
4. `clear()` и `clearPack()`: отменить/поглотить pending-запись перед тем, как удалять файл
   или писать новый снимок. Иначе отложенный дамп старого кеша воскресит удалённые данные.

### Инварианты, которые обязаны сохраниться

- **Чтение-после-записи.** `ProgressTracker.recordCardShowForMastery` сразу после
  `recordCardShowForPack` читает `getForPack` и логирует результат. Читатели ходят в
  in-memory кеш, поэтому остаются корректными — но проверь, что ни один читатель не
  завязан на файл.
- **Last-write-wins.** Каждая запись — полный снимок, поэтому при коалесцировании
  нескольких мутаций в одну запись результат идентичен. Это уже задокументировано в
  комментарии к классу, сохрани его смысл.
- **Кеш под `mutex`, файл под `fileMutex`,** и `mutex` никогда не удерживается на время
  дампа + записи. Текущий комментарий у класса описывает именно эту дисциплину — обнови
  его под новую схему, не удаляй.

### Риск

Потеря прогресса при аварийном убийстве процесса в окне debounce. Ограничено 1–2 секундами
и одной-двумя карточками; `saveProgress()` на границе каждого под-урока схлопывает окно
до нуля. Это приемлемо — но **не увеличивай debounce сверх 2 секунд**.

---

## Пункт 2. Таймер сессии: 2 fsync каждые 10 секунд на главном потоке

**Файлы:** `feature/training/SessionRunner.kt`, `feature/progress/ProgressTracker.kt`

### Проблема

Таймер тикает раз в 500 мс и каждые 20 тиков зовёт `onTimerSaveProgress()`
(`SessionRunner.resumeTimer`, ~1352-1357). Корутина живёт в `viewModelScope` → Main.

Колбэк уходит в `ProgressTracker.saveProgress`, который делает три файловые операции:
`packLessonProgressStore.loadPackProgress` + `savePackProgress` + `progressStore.save`
(~335-349 и `ProgressStore.kt` ~86). Две из них с `fsync`.

### Что сделать

- Выполнять сохранение по таймеру на `Dispatchers.IO`, не на Main. Снимок состояния
  (`TrainingUiState`) читается на Main, записывается — на IO.
- Убрать `loadPackProgress` из горячего пути: держать прогресс пака в памяти и писать
  из него, читая с диска только при смене активного пака.
- Пропускать запись, если с прошлого сохранения ничего не изменилось (сравнение по
  сохранённому снимку полей `LessonProgress`).

### Что не менять

Периодичность (10 с) и набор сохраняемых полей. Только поток и количество лишних операций.

---

## Пункт 3. Парсинг 760 КБ CSV на главном потоке ⭐ вторая по важности

**Файлы:** `data/DrillFileManager.kt`, `data/LessonStore.kt`, `ui/TrainingViewModel.kt`

### Проблема

`TrainingViewModel.getProfileStats()` (~2539-2566) вызывает
`lessonStore.getVocabWordsByRankRange(packId, languageId, 0, Int.MAX_VALUE)`.

`DrillFileManager.getVocabWordsByRankRange` (~227-252) **не кеширует ничего**: открывает и
парсит все vocab-drill CSV целиком при каждом вызове. Замеренные размеры в
`ITALIAN_FULL_COURSE`:

```
it_drill_nouns.csv       497 KB
it_drill_verbs.csv       229 KB
it_drill_pronouns.csv     14 KB
it_drill_adjectives.csv    9 KB
it_drill_numbers.csv       9 KB
it_drill_adverbs.csv       3 KB
                        ≈ 760 KB на каждый вызов
```

`refreshProfileStats()` вызывается синхронно **на Main** из четырёх мест:
`TrainingViewModel` ~876 и ~958 (обе внутри `withContext(Dispatchers.Main)` при старте),
~1045 и ~2448 (смена пака/языка). Это наиболее вероятный источник «залипания на секунду
при выборе пака».

### Что сделать

1. Добавить кеш в `DrillFileManager`: ключ `(packId, languageId)` → полный распарсенный
   список `VocabWord`; фильтрация по `fromRank..toRank` уже поверх кеша.
   Используй `ConcurrentHashMap`, как сделано для `lessonsCache` в `LessonStore`.
2. Инвалидация — через те же хуки, что и у уроков. В `LessonStore` есть
   `invalidatePackCaches()` (~236) и `invalidateLessonsCache(languageId)` (~464), вызываемые
   при импорте/удалении/перезагрузке паков. Добавь туда сброс drill-кеша; отдельных
   точек инвалидации не изобретай.
3. `refreshProfileStats()` перевести на `viewModelScope.launch(Dispatchers.IO)` с публикацией
   результата в `_profileStats` (это уже `MutableStateFlow`, UI читает его через
   `collectAsStateWithLifecycle` — менять UI не нужно).

### Проверь попутно

`getProfileStats` также зовёт `wordMasteryStore.getMasteredCount()` и
`wordMasteryStore.loadAll()`. Посмотри, кешируются ли они; если нет — тот же приём.
Не утверждай наличие кеша не проверив.

### Память

Полный список для 12k слов — единицы мегабайт. Если после замера окажется дорого,
кешируй распарсенные строки по файлам, а не готовые `VocabWord`.

---

## Пункт 4. Каждое нажатие клавиши перерисовывает весь NavHost

**Файлы:** `data/Models.kt`, `feature/training/SessionRunner.kt`, `ui/GrammarMateApp.kt`,
`ui/components/UnifiedInputControlsBar.kt`

> **Риск средний — делай последним, отдельным коммитом.** Если по времени не влезаешь,
> останови задачу на пунктах 1–3 и 5; они самодостаточны.

### Проблема

`inputText` лежит в глобальном `CardSessionState` (`Models.kt` ~514). Цепочка на **один
введённый символ**:

```
onValueChange (UnifiedInputControlsBar ~210)
  → SessionRunner.onInputChanged (~280; copy на ~284) → _coreState.update
  → пересчёт combine из 7 флоу двумя ступенями (5 + 2) + два copy() монолитного TrainingUiState (TrainingViewModel ~473-494)
  → val state by vm.uiState.collectAsStateWithLifecycle() в КОРНЕ (GrammarMateApp ~129)
  → рекомпозиция всего файла на 1075 строк
```

Дополнительно `TrainingUiState` для Compose **нестабилен** (внутри `List<Lesson>`,
`List<Language>`, `Map<>`), поэтому `TrainingScreen(state = ...)` не может быть пропущен
и перерисовывается целиком.

Прецедент правильного решения в этом же коде: таймер сессии уже вынесен в отдельный
`_sessionTimerMs: StateFlow` именно по этой причине (см. комментарий
«High-frequency timer flows (separate from main state for performance)»). Сделай то же
самое для ввода.

### Что сделать (предпочтительный вариант)

Вынести `inputText` в отдельный `MutableStateFlow<String>` во ViewModel, по образцу
`_sessionTimerMs`. Поле ввода подписывается только на него.

### Чего избегать

Соблазна держать текст в локальном `remember` внутри `UnifiedInputControlsBar`: там есть
автосабмит при точном совпадении (`Normalizer.isExactMatch` в `onValueChange`), и логика
submit/hint/word-bank читает `inputText` из состояния в нескольких местах
(`SessionRunner` ~340, ~450, ~463). Локальное состояние их рассинхронизирует.

### Обязательно проверить

`inputText` очищается при переходах между карточками (`SessionRunner` ~657, ~685, ~777, ~806
— всего 17 мест; полный список: grep `inputText = ""` по SessionRunner.kt)
и переиспользуется при resume (~201). Все эти места должны продолжать работать.
Режим WORD_BANK собирает ответ из выбранных слов — проверь, что он не сломался.

---

## Пункт 5. Мелочи (быстро, низкий риск)

### 5a. Log.d в цикле по урокам

`feature/progress/ChapterProgressCalculator.kt` ~73 и ~76: `Log.d` вызывается **на каждый
урок главы**, то есть до 63 интерполяций строк на каждое обновление глав, и в release-сборке
тоже (строка собирается до вызова `Log.d`).

Оставь один агрегирующий лог после цикла (он там уже есть, ~85). Пер-урочные — убрать
или обернуть в `if (BuildConfig.DEBUG)`.

### 5b. AtomicFileWriter ломает атомарность на Android

`data/AtomicFileWriter.kt`, `writeText` (~53-62) и `copyAtomic` (аналогично): перед
`renameTo` код **безусловно удаляет целевой файл**, с циклом до 10 попыток и
`Thread.sleep(10)` между ними. Комментарий объясняет это Windows-семантикой.

Две проблемы:

1. На Android/Linux `rename()` и так атомарно заменяет назначение. Удаление целевого файла
   **создаёт окно, в котором файла не существует** — то есть ровно та потеря данных, ради
   защиты от которой написан весь этот класс.
2. До 100 мс `Thread.sleep` на вызывающем потоке (сейчас — главном).

Сделать так: пробовать `renameTo` сразу; ветку с удалением целевого файла оставить
**только как fallback**, если `renameTo` вернул `false`. Логику верификации записи
(`verifyWrite`) не трогать.

### 5c. getPackTiles берёт блокировку на каждый урок

`TrainingViewModel.getPackTiles()` (~215-222) в цикле по урокам вызывает
`masteryStore.getForPack`, а каждый такой вызов захватывает `ReentrantLock`.

Заменить на один `masteryStore.loadAll()["pack:$packId"]` и разбирать полученную мапу
в памяти. Результат обязан совпадать.

---

## Порядок работы

1. `git branch --show-current` — если `main`, создать ветку. В `main` не коммитить.
2. Пункты по одному: правка → сборка → тесты → коммит.
3. Порядок: **1 → 3 → 2 → 5 → 4** (по убыванию отношения выигрыша к риску).

## Проверка

Сборка и тесты (Windows-обходной путь обязателен, `gradlew` напрямую не работает):

```bash
./gw.sh . test
./gw.sh . assembleDebug
```

Либо из cmd:

```cmd
build.bat test
```

Эти тесты должны остаться зелёными без правок в самих тестах:

```
com.alexpo.grammermate.data.MasteryStorePackScopedTest
com.alexpo.grammermate.data.MasteryLadderRulesTest
com.alexpo.grammermate.data.AtomicFileWriterTest
com.alexpo.grammermate.data.AtomicFileWriterSkipIfSameTest
com.alexpo.grammermate.data.WriteVerificationTest
com.alexpo.grammermate.data.FlowerCalculatorTest
com.alexpo.grammermate.data.SpacedRepetitionConfigTest
com.alexpo.grammermate.feature.training.ReviewSelectorTest
com.alexpo.grammermate.feature.progress.ChapterProgressCalculatorTest
```

**Если тест краснеет — чинится код, а не тест.** Переименование тестов в `.bak`, удаление
и замена ассертов на TODO-заглушки запрещены.

### Новые тесты

Для пункта 1 добавить тест на батчинг в `MasteryStorePackScopedTest` (или рядом):

- N мутаций подряд → после `flush()` на диске полный корректный снимок;
- `getForPack` возвращает свежее значение **до** `flush()` (чтение-после-записи из кеша);
- `clear()` после мутации не воскрешает данные отложенной записью.

Для пункта 5b — тест, что при существующем целевом файле содержимое заменяется и файл
ни в какой момент не пропадает.

## Отчёт

По завершении выдай:

- какие пункты сделаны, какие пропущены и почему;
- вывод `test` (число тестов, падения) — не пересказ, а факт;
- изменилось ли что-то в наблюдаемом поведении (ожидаемый ответ — нет);
- замеры «до/после», если снимал.

Если пункт оказался заблокирован — доделай все остальные полностью и явно скажи, что
осталось и почему. Решение сократить объём — за владельцем задачи, не за тобой.
