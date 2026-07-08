# UC-001 — Use Cases: Завершение доменного ядра + извлечение модуля :domain

**Эпизод:** E01 (epic_id=85), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (UC)
**Дата:** 2026-07-07
**Status:** Draft

**Производные от:** PRD-001 (artifact_id=439, `01-prd.md`, status=accepted).
**Repository:** BaseGrammy (repository_id=7), ветка `rewrite/v2-clean-architecture`.
**Код:** UC-001

---

## 0. Контекст документа: кто «пользователь» E01

E01 — фундаментный эпизод. Он не добавляет UI-экранов и не приносит
конечному учащемуся непосредственно видимых функций. Его «пользователи» —
это **два класса акторов**, чьи сценарии использования описывает данный
документ:

- **(a) Downstream-эпики** (E04 Training, E05 Verb, E06 Vocab, E07 Daily,
  E09 Story, E10 Gamification, E11 Boss, E12 Pomodoro). Они потребляют
  доменные порты и модели, чтобы построить на них свой пользовательский
  функционал (UI, имплементации репозиториев, бизнес-логику экранов).
  Для них E01 = стабильный, полный, pure-Kotlin доменный контракт.
- **(b) Разработчик** — переносит существующий домен в `:domain`
  Gradle-модуль, дополняет реальные gaps и устраняет мигратор. Для него
  E01 = регрессионно-безопасное извлечение + завершение пробелов.

Поэтому варианты использования ниже описывают **сценарии использования
доменного контракта** (как актор обращается к портам/движкам/моделям и
какой наблюдаемый результат получает), а не UI-клики. UI-сценарии будут
формализованы в соответствующих эпизодах Wave 1-4 (E04+).

Каждый UC связан (`covers`) с FR-кодами; поскольку SRS E01
(параллельная задача saga-architect task 395) ещё не завершена, FR-коды
помечены placeholder-ом `[FR TBD by SRS]` — этап AC (следующий после
UC+SRS) сшиёт конкретные FR-коды в traceability-матрицу. Это допустимо
на UC-этапе: UC выводятся из PRD (product perspective), а не из SRS.

> Соглашение об именовании: UC-ID = `UC-1..UC-9` (внутри эпизода).
> Код артефакта-сводки = `UC-001` (один на документ).

## 0.1. Список акторов

| Актор | Описание | UC |
|-------|----------|----|
| **A1: Downstream Training эпик (E04)** | Потребляет `SessionEngine`, `CardSessionStateMachine`, `ContentRepository`/`SessionRepository` для тренировки предложений. | UC-1, UC-3, UC-6 |
| **A2: Downstream SRS-потребитель (любой из E04/E05/E06/E07/E10)** | Потребляет `SrsScheduler` (FSRS-v6) для планирования повторений карточек/слов. | UC-2 |
| **A3: Downstream Story эпик (E09)** | Потребляет доменные модели `StoryQuiz`/`StoryQuestion` и парсер многоязычных story-файлов для story-чекинов. | UC-4 |
| **A4: Downstream Daily эпик (E07)** | Потребляет `DailyTask` composers и `DailyBlockType` для сборки дневной нормы задач. | UC-5 |
| **A5: Downstream Gamification эпик (E10)** | Потребляет `LessonCompletionCalculator`, streak/Boss/Elite модели для отметки завершения и мотивации. | UC-6 |
| **A6: Downstream Audio-потребитель (E03 adapter / E06 bg-vocab / E09 Story)** | Потребляет порт `AudioRepository` (TTS/ASR/SFX) без знания о Sherpa-ONNX или Android. | UC-7 |
| **A7: Разработчик** | Переносит домен в `:domain` Gradle-модуль, фиксирует regression-lock, устраняет `YamlToRoomMigrator`. | UC-8, UC-9 |

---

## UC-1: Возобновление сессии по `currentCardId` без потери карты

**Связанные ценности (PRD):** US-1 (Training, устойчивый resume).
**Code:** UC-1
**Covers:** `[FR TBD by SRS]` (резюме сессии по PK; инвариант card_15).
**derived_from:** PRD-001 (439).

**Actor:** A1 — Downstream Training эпик (E04).

**Precondition:**
- Доменный движок `SessionEngine` доступен в `:domain` и реализует
  `resumeSession(sessionId)`.
- Для данного `(packId, lessonId)` ранее была сохранена сессия со
  снимком `SessionSnapshot` (`currentCardId`, `poolCardIds`).
- `SessionRepository` (порт) имеет имплементацию, способную атомарно
  загрузить/сохранить снимок одной операцией.

**Основной поток:**
1. Downstream-эпик (например, Training ViewModel в E04) при старте
   экрана вызывает `SessionEngine.resumeSession(sessionId)`.
2. `SessionEngine` атомарно загружает снимок из `SessionRepository`
   одним чтением.
3. Проверяется инвариант: `currentCardId ∈ poolCardIds`.
4. Если инвариант выполнен — снимок возвращается как есть (тот же
   `currentCardId`, что был при сохранении).
5. Downstream-эпик отображает карту с этим `currentCardId`.

**Альтернативные потоки:**
- **A1: `currentCardId` оказался вне пула (сценарий бага `card_15`).**
  Пул пересобран или повреждён, а курсор ссылается на выпавшую карту.
  `SessionEngine` выполняет **явное восстановление**: карта возвращается
  в пул (в конец), снимок пересохраняется через `saveSession`. Карта НЕ
  была ни скрыта, ни отвечена → она должна показатьcя снова. Никогда не
  возвращается снимок, где курсор указывает мимо пула, и никогда карта
  не подменяется молча.
- **A2: Сессии для `sessionId` не существует.** `resumeSession`
  возвращает `null`. Downstream-эпик должен вызвать
  `startLessonSession(...)` для создания новой сессии.

**Postcondition:**
- Возвращён снимок, в котором `currentCardId` всегда ∈ `poolCardIds`
  (или `null`, если пул пуст). Идентичность текущей карты сохранена
  между сохранением и resume.

**Business rules:**
- **BR-1.1 (card_15 invariant):** Текущая карта идентифицируется
  **первичным ключом** (`currentCardId`), а не индексом массива.
  В v1 потеря карты происходила именно из-за идентификации по индексу
  при пересборке массива в рантайме. В E01 идентификация по PK —
  контракт домена.
- **BR-1.2 (atomicity):** Любое изменение пула (`hideCard`) и любая
  фиксация ответа (`submitAnswer`) пересчитывают `currentCardId` по PK и
  сохраняют весь снимок **одной** операцией `SessionRepository.saveSession`.
- **BR-1.3:** Инварианты покрыты доменным тестом
  `SessionEngineResumeRegressionTest` (perеносится в `:domain`, SM-2).

**Acceptance criteria preview (для этапа AC):**
- «После сохранения сессии с `currentCardId=X` и последующего resume,
  возвращённый снимок содержит `currentCardId=X`, причём `X` ∈
  `poolCardIds` — карта не теряется». Детальные Given/When/Then AC →
  этап AC после SRS.

---

## UC-2: Планирование повторений по алгоритму FSRS-v6

**Связанные ценности (PRD):** US-1, US-6 (интервальные тренировки,
SRS-лестница).
**Code:** UC-2
**Covers:** `[FR TBD by SRS]` (FSRS-v6 расчёт интервала/стабильности;
regression-locked формулы).
**derived_from:** PRD-001 (439).

**Actor:** A2 — любой downstream SRS-потребитель (Training E04, Verb E05,
Vocab E06, Daily E07, Gamification E10).

**Precondition:**
- `SrsScheduler` (FSRS-v6) доступен в `:domain` с public-методом
  планирования следующего интервала по рейтингу ответа
  (`SrsRating.Again/Hard/Good/Easy`) и текущему состоянию карты
  (`SrsCardState`).
- `SrsParams`/`SrsConstants` (параметры FSRS-v6) определены в `:domain`
  и зафиксированы.

**Основной поток:**
1. Downstream-потребитель, получив результат ответа (correct/incorrect)
   и рейтинг пользователя, вызывает `SrsScheduler` с текущим
   `SrsCardState` карты.
2. `SrsScheduler` по формулам FSRS-v6 пересчитывает `stability`,
   `difficulty`, `dueAt` (next review timestamp) и возвращает новое
   `SrsCardState`.
3. Потребитель сохраняет новое состояние через соответствующий порт
   (`MasteryRepository` / `SessionRepository`).
4. Карта планируется к показу в новой сессии в соответствии с `dueAt`.

**Альтернативные потоки:**
- **A1: Карта отвечает впервые (no previous repetition).** Расчёт идёт
  от начального состояния (`intervalStepIndex = 0`); step не
  «регрессирует», но и не «продвигается» (first show — нет предыдущей
  репетиции для оценки).
- **A2: Late review (просроченная карта).** Step НЕ меняется (ни
  вперёд, ни назад) — late review penalty без потери прогресса.
- **A3: On-time review.** Step продвигается `+1` по лестнице интервалов
  `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` дней (cap на последнем).

**Postcondition:**
- Новое `SrsCardState` детерминированно выведено из FSRS-v6 формул с
  заданными параметрами; формулы не изменены относительно текущей
  реализации (regression-locked).

**Business rules:**
- **BR-2.1:** Поведение существующего `SrsScheduler` (FSRS-v6) в E01
  **фиксируется (regression-locked)**, не улучшается и не
  переписывается (PRD §5 Scope OUT: «Новые алгоритмы SRS или изменения
  формул» исключены).
- **BR-2.2:** Все параметры FSRS-v6 (`SrsParams`, `SrsConstants`)
  живут в `:domain` как pure Kotlin.
- **BR-2.3:** Существующие тесты SRS (`srs/`) переносятся в `:domain`
  и остаются зелёными (SM-2).

**Acceptance criteria preview:**
- «Расчёт FSRS-v6 для заданного `(state, rating)` возвращает то же
  `dueAt`/`stability`, что и до переноса в `:domain` — детерминизм
  сохранён». Детальные AC → этап AC.

---

## UC-3: Ограничение «WORD_BANK не растит цветы / не помечает карту shown»

**Связанные ценности (PRD):** US-2 (Verb, word bank как способ ввода);
legacy бизнес-правило.
**Code:** UC-3
**Covers:** `[FR TBD by SRS]` (WORD_BANK ≠ mark shown; сохранение
дизайна v1).
**derived_from:** PRD-001 (439).

**Actor:** A1/A2 — Training (E04), Verb (E05), Vocab (E06), любой
провайдер карточной сессии, использующий `CardSessionStateMachine` и
`InputMode.WORD_BANK`.

**Precondition:**
- `InputMode` enum в `:domain` содержит `WORD_BANK` (наряду с
  `VOICE`, `KEYBOARD`).
- `SessionEngine.submitAnswer` и/или `CardSessionStateMachine.onSubmit`
  доступны downstream.
- Карта показана в режиме ввода `WORD_BANK`.

**Основной поток:**
1. Пользователь downstream-эпика собирает ответ из word bank
   (набор слов) и отправляет.
2. Downstream-эпик валидирует ответ (`AnswerValidator`/`Normalizer`) и
   вызывает `SessionEngine.submitAnswer(..., inputMode = WORD_BANK)`.
3. `SessionEngine` обновляет прогресс (correct/incorrect count), но
   **НЕ** добавляет `cardId` в `shownCardIds`.
4. Поскольку `shownCardIds` не изменилось, `FlowerCalculator`/mastery
   не «продвигают» цветок для этой карты.

**Альтернативные потоки:**
- **A1: Режим VOICE или KEYBOARD.** Карта помечается shown (добавляется
  в `shownCardIds`), mastery/flower продвигаются — «самостоятельный»
  ввод считается полноценным показом.
- **A2: Drill-режим (verb/vocab drill).** Также не помечает shown
  (drill — отдельная механика, не основная тренировка урока).

**Postcondition:**
- `shownCardIds` карты в режиме WORD_BANK не содержит данную карту;
  mastery-прогресс и flower-state для неё не выросли от этого ответа.

**Business rules:**
- **BR-3.1 (дизайн v1 — сохранён):** Ввод из word bank не считается
  полноценным показом карты (легче, чем самостоятельный ввод), поэтому
  не растит mastery/flower. Это явно зафиксировано в KDoc
  `SessionEngine.submitAnswer` и инварианте #5.
- **BR-3.2:** Это бизнес-правило принадлежит домену (`:domain`), а не
  UI/data-слою — оно часть доменного контракта `SessionEngine`.

**Acceptance criteria preview:**
- «После `submitAnswer(cardId=X, isCorrect=true, inputMode=WORD_BANK)`
  множество `shownCardIds` не содержит `X`; flower-state для урока не
  вырос. Тот же вызов с `KEYBOARD` → `X` ∈ `shownCardIds`.». Детальные
  AC → этап AC.

---

## UC-4: Парсинг многоязычной story в доменные модели

**Связанные ценности (PRD):** US-5 (Story-чекины с вопросами по истории).
**Code:** UC-4
**Covers:** `[FR TBD by SRS]` (MultilingualStoryParser — NEW gap,
PRD §4.1 п.1).
**derived_from:** PRD-001 (439).

**Actor:** A3 — Downstream Story эпик (E09).

**Precondition:**
- Доменные модели `StoryQuiz`, `StoryQuestion`, `StoryPhase` существуют
  в `:domain` (confirmed в коде: `model/Content.kt`, `model/Enums.kt`).
- В `:domain` добавлен **доменный парсер** многоязычных story-файлов
  (`MultilingualStoryParser` — gap, PRD §4.1 п.1), который разворачивает
  story-контент в эти модели. Pure Kotlin, без Android.
- Источник story-контента (YAML/JSON-файл, multi-language) доступен
  downstream-эпику (через data-слой E02) как доменная структура
  (строки/документ), либо как уже загруженный raw-контент.

**Основной поток:**
1. Downstream Story-эпик загружает multi-language story-контент (через
   data-слой) и передаёт его в `MultilingualStoryParser`.
2. Парсер разворачивает контент в последовательность `StoryPhase`
   (check-in/chapter/quiz/check-out), каждый со своим текстом на нужных
   языках и набором `StoryQuestion`.
3. Каждый `StoryQuestion` содержит формулировку, варианты ответа и
   метаданные правильного ответа (валидируемые `AnswerValidator`).
4. Парсер возвращает собранный `StoryQuiz` (полная story как доменная
   модель).
5. Downstream-эпик использует `StoryQuiz` для проведения story-чекина.

**Альтернативные потоки:**
- **A1: Некорректный/неполный story-контент.** Парсер возвращает
  детерминированную ошибку доменного типа (sealed result), downstream
  решает, как её показать (не падение приложения).
- **A2: Story без quiz-блоков (только narrative).** Парсер возвращает
  `StoryQuiz` с пустым списком вопросов — допустимый кейс (текстовая
  глава).

**Postcondition:**
- Получен полный `StoryQuiz` (immutable доменная модель), готовый к
  использованию downstream Story-эпиком без знания формата исходного
  файла.

**Business rules:**
- **BR-4.1 (NEW gap):** Парсер — pure-Kotlin доменная логика в
  `:domain`. В E01 отсутствует (verified: grep `*StoryParser*`,
  `MultilingualStory` в `:domain` = 0 файлов) и должен быть добавлен.
- **BR-4.2:** Многоязычность — часть доменной модели (`LanguageId`
  привязан к текстам story), не UI.
- **BR-4.3:** Парсер не зависит от Android/Room — тестируется на
  чистом JVM с fake-контентом.

**Acceptance criteria preview:**
- «Передача валидного multi-language story-контента в
  `MultilingualStoryParser` возвращает `StoryQuiz` с корректным числом
  фаз и вопросов; invalid-контент возвращает доменную ошибку, не
  бросая исключение наружу.». Детальные AC → этап AC.

---

## UC-5: Сборка дневной нормы задач из блоков

**Связанные ценности (PRD):** US-4 (дневная норма: перевод/словарь/глаголы).
**Code:** UC-5
**Covers:** `[FR TBD by SRS]` (DailyTask composers — gap,
PRD §4.1 п.2; `DailyBlockType`).
**derived_from:** PRD-001 (439).

**Actor:** A4 — Downstream Daily эпик (E07).

**Precondition:**
- `DailyTask` sealed-иерархия (`TranslateSentence`, `VocabFlashcard`,
  `ConjugateVerb`) существует в `:domain` (confirmed:
  `model/DailyTask.kt`).
- `DailyBlockType` enum (`TRANSLATE`, `VOCAB`, глагольный блок)
  существует в `:domain` (confirmed: `model/Enums.kt`).
- В `:domain` добавлены/подтверждены **композиторы**, которые собирают
  дневную норму из блоков в список `DailyTask` (gap, PRD §4.1 п.2),
  как pure-Kotlin доменная логика.
- Downstream-эпик имеет доступ к источнику карточек/слов/глаголов
  (через порты `ContentRepository`/`VocabDrillRepository`).

**Основной поток:**
1. Downstream Daily-эпик определяет конфигурацию дня: какие
   `DailyBlockType` активны и сколько задач в каждом блоке.
2. Для каждого блока вызывается соответствующий композитор, который
   через порты репозиториев выбирает карточки/слова/глаголы и
   оборачивает их в конкретный подтип `DailyTask`
   (`TranslateSentence`/`VocabFlashcard`/`ConjugateVerb`) с параметрами
   отрисовки (`InputMode`, `VocabDrillDirection`).
3. Композиторы возвращают упорядоченный `List<DailyTask>` — дневную
   норму.
4. Downstream-эпик отображает задачи одну за другой, отслеживая курсор
   по блокам.

**Альтернативные потоки:**
- **A1: Недостаточно материала для блока** (мало слов в словаре /
  глаголов). Композитор возвращает доступное количество либо
  детерминированную ошибку (downstream решает, пропустить блок или
  показать «недостаточно контента»).
- **A2: Пользователь завершил часть блоков и закрыл приложение.**
   Daily-эпик сохраняет курсор дня (за пределами `:domain` — UI/data);
   при resume доменные композиторы переиспользуются для пересборки.

**Postcondition:**
- Получен полный `List<DailyTask>` — дневная норма как immutable
  доменные модели, готовые к отображению downstream-эпиком.

**Business rules:**
- **BR-5.1 (gap):** Композиторы (блок → `List<DailyTask>`) — pure-Kotlin
  доменная логика в `:domain`. В E01 должны быть подтверждены/завершены
  (PRD §4.1 п.2). На сегодня есть только модель `DailyTask`, композиторов
  в `:domain` нет (grep `composeDaily`, `DailyComposer` = 0 файлов).
- **BR-5.2:** Каждый `DailyTask` несёт `blockType` и стабильный `id`
  (для ключей UI/state).
- **BR-5.3:** Подтипы `DailyTask` переиспользуют существующие доменные
  модели (`Card`, `VocabWord`, `InputMode`, `VocabDrillDirection`) —
  новых дублирующих моделей не плодить.

**Acceptance criteria preview:**
- «Для конфигурации дня `{TRANSLATE: 5, VOCAB: 3, глаголы: 2}`
  композиторы возвращают `List<DailyTask>` длиной 10 с корректными
  `blockType` у каждой задачи.». Детальные AC → этап AC.

---

## UC-6: Фиксация завершения под-урока и урока

**Связанные ценности (PRD):** US-1, US-6 (завершение под-урока,
прогресс по лестнице уроков).
**Code:** UC-6
**Covers:** `[FR TBD by SRS]` (SubLesson/Lesson completion logic,
PRD §4.1 п.3; `LessonCompletionCalculator`).
**derived_from:** PRD-001 (439).

**Actor:** A1/A5 — Training (E04), Gamification (E10).

**Precondition:**
- `SubLessonScheduler` (детерминированная нарезка карт по PK) доступен
  в `:domain` (confirmed: `session/SubLessonScheduler.kt`).
- `LessonCompletionCalculator` доступен в `:domain` (confirmed:
  `progress/LessonCompletionCalculator.kt`) и фиксирует контракт
  завершения под-урока/урока в доменных терминах.
- Downstream-эпик имеет текущее состояние прогресса по уроку
  (через `ProgressRepository`/`MasteryRepository`).

**Основной поток:**
1. Downstream Training-эпик, когда ученик отвечает последнюю карту
   под-урока, вызывает `LessonCompletionCalculator` с текущим mastery/
   session-состоянием.
2. Калькулятор определяет: завершён ли текущий под-урок (по числу
   показанных/отвеченных карт в срезе `SubLessonScheduler`).
3. Если под-урок завершён — `activeSubLessonIndex` продвигается
   (`maxOf(current, actual)` — никогда не движется назад).
4. Если завершён урок в целом (достигнут порог под-уроков, напр. 15) —
   урок помечается завершённым, генерируется событие для Gamification
   (flower/streak/ladder).
5. Результат фиксируется downstream через `ProgressRepository`.

**Альтернативные потоки:**
- **A1: Под-урок завершён, но пользователь уже продвинулся дальше
  (activeSubLessonIndex уже > actual).** Калькулятор сохраняет текущий
  индекс (`maxOf`), не откатывая прогресс назад.
- **A2: Скрытые карты уменьшили пул.** `SubLessonScheduler`
  пересчитывает нарезку с учётом `UserContentRepository.getHiddenCardIds()`;
  контракт завершения остаётся консистентным (PRD §4.1 п.3 — «убедиться,
  что контракт завершения покрывает все legacy-кейсы»).

**Postcondition:**
- Состояние завершения под-урока/урока детерминированно выведено из
  mastery/session и зафиксировано в доменных терминах; прогресс не
  движется назад.

**Business rules:**
- **BR-6.1 (gap-аудит):** Логика завершения должна покрывать все
  legacy-кейсы (включая edge-случаи со скрытыми картами и MIXED
  под-уроками). `LessonCompletionCalculator` уже есть — E01
  **убеждается** (аудит), что контракт полон (PRD §4.1 п.3).
- **BR-6.2 (no-regression):** `SubLessonScheduler` детерминирован
  (нарезка по PK), не зависит от рантайма — легко тестировать.
- **BR-6.3:** Завершение урока = триггер для Gamification (streak/
  ladder/flower) — домен только фиксирует факт, интерпретация в
  downstream-эпике E10.

**Acceptance criteria preview:**
- «При ответе последней карты под-урока N, `LessonCompletionCalculator`
  фиксирует завершение под-урока N и продвигает `activeSubLessonIndex`
  до N+1 (или сохраняет большее). При достижении порога под-уроков
  урок помечается завершённым.». Детальные AC → этап AC.

---

## UC-7: Доступ к аудио-портам без утечки Sherpa-ONNX в домен

**Связанные ценности (PRD):** US-2, US-3, US-5 (голосовой ввод,
фоновый словарь, story-озвучка).
**Code:** UC-7
**Covers:** `[FR TBD by SRS]` (`AudioRepository`/`AudioModelRepository`
порты; zero-Sherpa-в-domain).
**derived_from:** PRD-001 (439).

**Actor:** A6 — Downstream Audio-потребитель (E03 audio adapter, E06
bg-vocab, E09 Story) и косвенно Training (E04 — голосовой ввод).

**Precondition:**
- Порт `AudioRepository` (TTS `speak`, ASR `recognizeSpeech`, SFX
  `playSoundEffect`, availability checks) определён в `:domain`
  (confirmed: `audio/AudioRepository.kt`).
- Порт `AudioModelRepository` (статус/загрузка моделей) определён в
  `:domain` (confirmed: `audio/AudioModelRepository.kt`).
- Домен **не** содержит ни одного импорта `com.k2fsa.sherpa.onnx.*`
  или `android.*` (zero-leak invariant — часть SM-1).

**Основной поток:**
1. Downstream-потребитель (напр. bg-vocab E06) озвучивает слово:
   вызывает `AudioRepository.speak(text, languageId, speed)`.
2. Получает `Flow<AudioEvent>` (`Started → Progress? → Completed/Failed`)
   и реагирует на события (показать индикатор «говорит», скрыть по
   завершении).
3. Для распознавания речи (Training голосовой ввод, E04) вызывает
   `AudioRepository.recognizeSpeech(languageId)`, получает
   `Flow<RecognitionEvent>` (`ListeningStarted → Partial →
   EndpointDetected → Final/Failed`).
4. Для SFX (правильный/неправильный ответ) вызывает
   `playSoundEffect(SoundEffect.CORRECT_ANSWER)`.
5. Реализация (Sherpa-ONNX) живёт в data-слое (E03) и подключается
   через DI; домен её не знает.

**Альтернативные потоки:**
- **A1: TTS/ASR-модель не загружена.** `isTtsAvailable`/`isAsrAvailable`
  возвращают `false`; `speak`/`recognizeSpeech` завершают `Flow` с
  `Failed`. Downstream показывает «загрузите модель» (через
  `AudioModelRepository`).
- **A2: Пользователь отменил озвучку.** Вызов `stop()` немедленно
  останавливает; отписка коллектора отменяет корутину-источник.
- **A3: Bluetooth-микрофон.** Реализация маршрутизирует на BT
  (`AudioModelRepository.observeBluetoothMic`); домен только
  декларирует контракт.

**Postcondition:**
- Аудио-операция инициирована через чистый доменный порт; ни одна
  нативная (Sherpa/Android) зависимость не утекла в `:domain`.

**Business rules:**
- **BR-7.1 (zero-leak):** `:domain` зависит только от
  `AudioRepository`/`AudioModelRepository` интерфейсов. Проверка:
  `grep -Rni "sherpa\|com.k2fsa\|android\." domain/src` = 0 (SM-1).
- **BR-7.2 (reactivity):** Все аудио-операции — `Flow` или `suspend`,
  ни один метод не блокирует поток.
- **BR-7.3 (testability):** В unit-тестах `:domain` подставляется
  fake/mock `AudioRepository` — домен тестируется на чистой JVM.

**Acceptance criteria preview:**
- «После переноса в `:domain`, в `domain/src` нет ни одного импорта
  `sherpa`/`android.*`; интерфейс `AudioRepository` компилируется как
  pure Kotlin. Тест с fake-`AudioRepository` собирает `AudioEvent`
  последовательность.». Детальные AC → этап AC.

---

## UC-8: Перенос домена в `:domain` Gradle-модуль без потери тестов

**Связанные ценности (PRD):** SM-1..SM-4 (zero-Android, no-regression,
полнота, модульная изоляция) — все успех-метрики.
**Code:** UC-8
**Covers:** `[FR TBD by SRS]` (механический перенос в `:domain`;
`settings.gradle.kts`/`build.gradle.kts`; regression-lock).
**derived_from:** PRD-001 (439).

**Actor:** A7 — Разработчик.

**Precondition:**
- Существующий домен в
  `app/src/main/java/com/alexpo/grammermate/v2/core/domain/*` имеет
  ZERO Android-импортов (PRD H1, verified: `grep "^import android"` = 0).
- Существующие тесты в `app/src/test/.../v2/core/domain/*` зелёные
  (314 `@Test`-функций / 72 сценария, PRD H3).

**Основной поток:**
1. Разработчик создаёт каталог `domain/` на корне репозитория с
   `build.gradle.kts` (`org.jetbrains.kotlin.jvm`, без Android plugin).
2. В `settings.gradle.kts` добавляет `include(":domain")`.
3. В `:app`/`build.gradle.kts` объявляет
   `implementation(project(":domain"))`.
4. Механически переносит
   `app/src/main/java/.../v2/core/domain/*` →
   `domain/src/main/java/...` (без изменения assertions/логики).
5. Переносит тесты
   `app/src/test/.../v2/core/domain/*` →
   `domain/src/test/...`.
6. Запускает `./gradlew :domain:test` — все 314 `@Test` зелёные (SM-2).
7. Запускает `grep -Rni "android\." domain/src` = 0 (SM-1).
8. `:app` успешно собирается с зависимостью от `:domain` (SM-4).

**Альтернативные потоки:**
- **A1: При переносе выявлен случайный Android-импорт** (нарушение H1).
  Разработчик рефакторит (замена на pure-Kotlin эквивалент) до
  достижения zero-Android, фиксирует в SRS-решении.
- **A2: Тест упал после переноса.** Регрессия — разработчик
  восстанавливает поведение (перенос должен быть механическим, H3);
  если выявлен скрытый баг, он выносится в отдельную задачу с trace
  (PRD §8 R3 mitigation: «без тихого расширения scope»).

**Postcondition:**
- `:domain` — самостоятельный `kotlin.jvm` Gradle-модуль; `:app`
  зависит от него; все доменные тесты зелёные; zero Android-импортов.

**Business rules:**
- **BR-8.1 (regression-lock):** Перенос **механический**, без изменения
  поведения и assertions. Тесты переносятся вместе с кодом (PRD H3).
- **BR-8.2 (zero-Android):** `:domain` — pure Kotlin/JVM, без Android
  plugin и без `android.*` импортов (SM-1, SM-4).
- **BR-8.3 (audit-complete):** После переноса выполняется финальный
  аудит покрытия 14 legacy-доменов (PRD §4.1 п.4, SM-3); любой gap либо
  закрыт, либо вынесен в задачу с trace.

**Acceptance criteria preview:**
- «`./gradlew :domain:test` зелёный (314 функций), `:app` собирается с
  `project(":domain")`, `grep "android\." domain/src` = 0.». Детальные
  AC → этап AC.

---

## UC-9: Удаление/депрекация `YamlToRoomMigrator` (greenfield Q3)

**Связанные ценности (PRD):** SM-5 (migrator устранён); решение спонсора
Q3 (greenfield data).
**Code:** UC-9
**Covers:** `[FR TBD by SRS]` (migrator устранён; no active data path
через мигратор).
**derived_from:** PRD-001 (439).

**Actor:** A7 — Разработчик.

**Precondition:**
- `YamlToRoomMigrator` существует в
  `app/.../v2/core/data/migration/YamlToRoomMigrator.kt` (confirmed).
- Решение спонсора Q3 = greenfield data (PRD §2): данные в Q3 года —
  чистая установка, миграция YAML→Room больше не нужна.
- Конкретный выбор (delete vs `@Deprecated`) фиксируется в SRS; PRD
  требует лишь, чтобы migrator перестал быть **активным путём данных**.

**Основной поток:**
1. Разработчик по решению SRS либо удаляет
   `YamlToRoomMigrator.kt`, либо помечает его `@Deprecated(level =
   ERROR/HIDDEN)` с указанием, что миграция не поддерживается.
2. Убираются все активные вызовы migrator из процесса загрузки данных
   (DI-граф, startup-последовательность).
3. Запускается сборка `:app` — компилируется без активного migrator-пути.
4. Документируется (в SRS/CLAUDE), что миграция YAML→Room не является
   поддерживаемым путём (Q3 greenfield).

**Альтернативные потоки:**
- **A1: Выявлена dev-зависимость от миграции** (напр. dev-build с
  seed-данными через migrator). Разработчик заменяет dev-seed на
  другой механизм (не через migrator) либо помечает migrator
  `@Deprecated` (оставляя compile-time предупреждение) — выбор в SRS
  (PRD §8 R4 mitigation).
- **A2: Спонсор меняет решение (Q3 не greenfield).** Это изменение
  scope вне E01 — выносится в новый эпизод/задачу с trace; E01 тогда
  оставляет migrator как есть.

**Postcondition:**
- В кодовой базе нет активного пути данных через `YamlToRoomMigrator`
  (либо удалён, либо `@Deprecated`-помечен и не вызывается).

**Business rules:**
- **BR-9.1 (Q3 greenfield):** Мигратор перестаёт быть активным путём
  данных. Конкретный механизм (delete vs deprecate) — зона SRS
  (PRD OQ-3).
- **BR-9.2 (no-silent-scope):** Если удаление вскрывает неожиданные
  зависимости, они решаются в рамках E01 (заменой dev-seed) либо
  выносятся в задачу с trace (PRD §8 R4).

**Acceptance criteria preview:**
- «В кодовой базе нет активного вызова `YamlToRoomMigrator` из startup/
  DI-пути; класс либо удалён, либо помечен `@Deprecated`.». Детальные
  AC → этап AC.

---

## Traceability-матрица (UC ↔ User Story ↔ FR placeholder)

| UC | User Story (PRD §3) | Actor | FR (placeholder до SRS) |
|----|--------------------|-------|-------------------------|
| UC-1 | US-1 (Training resume) | A1 | `[FR TBD by SRS]` |
| UC-2 | US-1, US-6 (FSRS-v6) | A2 | `[FR TBD by SRS]` |
| UC-3 | US-2 (Verb/word bank) | A1/A2 | `[FR TBD by SRS]` |
| UC-4 | US-5 (Story) | A3 | `[FR TBD by SRS]` |
| UC-5 | US-4 (Daily) | A4 | `[FR TBD by SRS]` |
| UC-6 | US-1, US-6 (SubLesson/Lesson) | A1/A5 | `[FR TBD by SRS]` |
| UC-7 | US-2, US-3, US-5 (Audio) | A6 | `[FR TBD by SRS]` |
| UC-8 | SM-1..SM-4 (module extraction) | A7 | `[FR TBD by SRS]` |
| UC-9 | SM-5 (migrator) | A7 | `[FR TBD by SRS]` |

> Покрытие user stories US-1..US-8: каждый US из PRD §3 отображается как
> минимум на один UC выше (UC-1/UC-2/UC-6 → US-1; UC-3 → US-2; UC-7 →
> US-3; UC-5 → US-4; UC-4 → US-5; UC-2/UC-6 → US-6; косвенно US-7/US-8
> через UC-2/UC-7, т.к. Boss/Pomodoro потребляют SRS+audio домен).
> FR-коды будут сшиты на этапе AC после завершения SRS (task 395).

## Cross-reference с PRD Scope (§4)

| PRD §4 пункт | UC покрывает |
|--------------|--------------|
| §4.1 п.1 (MultilingualStoryParser gap) | UC-4 |
| §4.1 п.2 (DailyTask composers gap) | UC-5 |
| §4.1 п.3 (SubLesson completion) | UC-6 |
| §4.1 п.4 (финальный аудит покрытия) | UC-8 (BR-8.3) |
| §4.2 (:domain extraction) | UC-8 |
| §4.3 (YamlToRoomMigrator) | UC-9 |
| Существующее ядро (SessionEngine/FSRS/CardSessionStateMachine) | UC-1, UC-2, UC-3 |
| Audio порты (zero-Sherpa) | UC-7 |

---

*Следующий этап formalization: SRS (saga-architect, task 395) фиксирует
FR-коды и технический контракт домена/портов; затем AC (saga-analyst)
сошьёт UC ↔ FR ↔ Given/When/Then. UC выводятся из PRD (product
perspective) и стабильны независимо от того, какая техническая подпись
получит порт в SRS.*
