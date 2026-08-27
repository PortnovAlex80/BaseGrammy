# GrammarMate: план стабилизации, рефакторинга и регрессионной защиты

**Дата аудита:** 2026-08-26

**Ветка/ревизия:** `dev` / `67c5ac0c6`

**Область:** активное приложение v2 (`MainActivityV2`), модуль `:domain`, Room/data, Compose UX, CI и тестовый контур

**Статус:** BLOCKED (внешний аудит 2026-08-26 отклонил "executed") —
Фазы 0–7 пройдены формально (625 unit-тестов зелёные), НО аудит вскрыл
критические дефекты, не покрытые unit-контуром:

- [x] D1 Clean install падал на bundled ZIP: ZipGuard 5 000 entries <
      8 949 у ITALIAN_EXPRESS_SHORT → лимит 20 000 + регрессия на РЕАЛЬНЫХ
      ассетах (ZipGuardBundledPacksTest).
- [x] D2 Урок обрывался после первого под-урока (10 карт), остаток терялся,
      mastery завершался преждевременно → nextCardOrComplete переходит к
      следующему под-уроку (10+10+5 для 25 карт) + full-lesson регрессия.
- [ ] D3 Drill/Daily/Vocab не подключены к пользовательской навигации
      (входы из PackContent; импорт drill-контента в importer).
- [ ] D4 Room PK cards/vocab не изолированы по packId (коллизии при двух
      паках с одинаковыми lessonId) — schema v5.
- [ ] D5 lintDebug: 248 ошибок / 547 предупреждений — не входит в зелёный
      контур CI.
- [ ] D6 androidTest и активные Maestro/E2E-сценарии отсутствуют;
      TASK-073 (comprehensive test suite) OPEN.

Плановая Status-строка "executed" была преждевременной: unit-зелёность не
доказывает реальный первый запуск (D1 — ровно этот случай).

## 1. Итог аудита

Архитектурный фундамент v2 сохранять: отдельный pure-Kotlin `:domain`, Room, Hilt,
Compose и PK-based `SessionEngine` являются хорошей базой. Новый rewrite не нужен.

Основная проблема находится не в выбранном стеке, а в незавершённой интеграции:
production-путь тренировки обходит уже готовый `SessionEngine`, частично повторяет его
правила во ViewModel и независимо обновляет UI и Room. Поэтому понятия «текущая
карточка», «показана», «отвечена», «завершена» и «освоена» не имеют одного владельца.
Большая часть наблюдаемых UX-багов является следствием этого разрыва.

Главная стратегия: **подключить существующий доменный Engine как единственный путь
изменения сессии, стабилизировать один полный user journey, затем переносить режимы
вертикальными срезами под контрактными и end-to-end тестами**. Физическую Gradle-
модуляризацию и новый общий SessionStore отложить до появления измеримой причины.

## 2. Подтверждённые проблемы

| Приоритет | Наблюдение | Доказательство | Последствие |
|---|---|---|---|
| P0 | Fresh install не имеет рабочего пути получения контента | startup seed отключён в `GrammarMateApplicationV2.kt:9`; `PackImporter` и часть parser'ов содержат runtime `TODO()` | Home пуст, а показанный совет «импортируйте курс» не ведёт к доступному действию |
| P0 | Home передаёт `packId` как `lessonId` | `GrammarMateNavHost.kt:40-47` | выбор пака не определяет реальный урок; карточки могут быть пустыми или чужими |
| P0 | Новая lesson-сессия сохраняется с пустым pool | `TrainingViewModel.kt:83-99` не передаёт `poolCardIds`; `SessionRepositoryImpl.kt:76-89` сохраняет пустой список | первая карта маскируется UI fallback'ом, но Next читает пустой pool и не продвигается |
| P0 | Production обходит проверенный `SessionEngine` | `TrainingViewModel.kt:126-176` напрямую вызывает `load/update/mark/set`; production-вызовов `SessionEngine` нет | дублирование правил, потеря атомарности и возврат card/session bugs |
| P0 | Persistence failure превращается в визуальный успех | `TrainingViewModel.kt:131-145`: результат `runCatching` игнорируется, state обновляется всегда | пользователь видит засчитанный ответ, которого нет в БД |
| P0 | Реализованы только Home и минимальный Training | `GrammarMateNavHost.kt:52-105` использует `PlaceholderScreen` для ChapterLessons, Settings, VerbDrill и DailyPractice | формально кликабельный, но неполный UX journey |
| P1 | Нет терминального состояния сессии | `onNextCard()` зацикливает pool; `TrainingViewState` не содержит Completed | урок не имеет единой точки completion/reward/navigation |
| P1 | UI-state допускает противоречивые комбинации | `TrainingViewState.kt:27-36`: независимые nullable/boolean поля | возможны `error + currentCard`, feedback без карточки, повторные действия во время записи |
| P1 | Два пути MVI-мутации | reducer меняет часть полей, async-код вызывает `updateState` напрямую | reducer не является полным описанием поведения, тесты переходов не доказывают production flow |
| P1 | Снимок Room читается неатомарно | `SessionRepositoryImpl.kt:117-120,199-206` выполняет три SELECT без `@Transaction` | теоретически возможен torn snapshot между session/pool/shown |
| P1 | `currentCardId` и `cursorIndex` обновляются раздельно | `setCurrentCard()` сохраняет старый cursor в `SessionRepositoryImpl.kt:151-157` | два поля состояния расходятся и по-разному восстанавливаются |
| P1 | Семантика режимов раздвоена | `TrainingMode` имеет 3 значения, `TrainingScreenMode` 8; persisted snapshot хранит первый | resume и выбор renderer/pool/completion не описаны одним контрактом |
| P1 | Доменная логика не подключена | `AnswerValidator`, `CardSessionStateMachine`, mastery/SRS не вызываются из v2 Training; `masteryRepository` не используется | UI даёт упрощённую проверку ответа, не применяет бизнес-правила и прогресс |
| P1 | Критический пользовательский путь не покрыт | нет `app/src/androidTest`; `app/src/test` покрывает только audio и partial import | 442 `@Test` создают ложное ощущение защищённости wiring/navigation/UI |
| P2 | Hot path делает лишний I/O | каждый Next снова загружает snapshot и все cards; full `saveSnapshot` переписывает pool/shown | лишние Room queries, write amplification, задержки и больше окон для гонок |
| P2 | Home не реактивен и показывает фиктивный прогресс | `HomeViewModel` делает одноразовый `getPacks`; `HomeScreen.kt:169-174` всегда 0% | progress не соответствует данным после обучения/import |
| P2 | Автоматизация устарела | Maestro flows относятся к legacy English UI; CI не запускается на push в `dev` и не имеет emulator job | зелёный CI не доказывает рабочий v2 journey |
| P2 | Документы завышают готовность | `v2-architecture/ARCHITECTURE.md:154-167` помечает фазы 0-9 done, но `:183-188` перечисляет ключевые TODO | планирование и оценка риска опираются на неверный статус |

## 3. Целевая архитектура

### 3.1. Неподвижные правила

1. `:domain` остаётся pure Kotlin; Android/Room/Sherpa туда не попадают.
2. `SessionEngine` является единственным владельцем переходов durable session state.
3. Presentation не импортирует mutating API `SessionRepository` и не считает
   progress/mastery/completion самостоятельно.
4. UI не выбирает fallback-карточку. Карточка всегда следует из валидного persisted
   session pool; recovery является явным результатом, а не тихой подменой.
5. Сначала успешно фиксируется транзакция, затем публикуется успешный UI-state и
   запускаются TTS/navigation effects.
6. Команды одной сессии сериализованы; повторный Submit/Next идемпотентен.
7. Mode задаёт policy построения pool, порядка, scoring, completion и resume;
   Compose отвечает только за renderer и управление вводом.
8. Недоступный или незавершённый route не показывается пользователю.

### 3.2. Поток команды

```text
Compose action
    -> TrainingIntent
    -> TrainingViewModel (serialization + orchestration only)
    -> SessionEngine / mode policy
    -> SessionSnapshot + domain result
    -> one Room transaction
    -> immutable TrainingUiState projection
    -> Compose render
    -> post-commit ephemeral effect (TTS/snackbar/navigation)
```

Новый глобальный `SessionStore` на первом этапе не вводится. Существующие
`SessionSnapshot + SessionEngine + SessionRepository.saveSession()` уже задают нужную
границу; сначала надо доказать, что её недостаточно. Если позднее появятся несколько
конкурирующих владельцев одной активной сессии, store допускается отдельным ADR.

### 3.3. Явное UI-состояние

Плоский `TrainingViewState` заменить конечным автоматом:

```text
Loading
Empty(reason)
Active.AwaitingAnswer(session, exercise, draft, inputMode)
Active.Checking(session, exercise)
Active.Feedback(session, exercise, result, canAdvance)
Paused(session)
Completing(session)
Completed(summary, nextAction)
RecoverableError(error, retryAction, stableSession)
FatalError(error, exitAction)
```

Невозможные сочетания состояния должны перестать компилироваться. Draft привязывается
к `cardId/sessionRevision` и сохраняется через `SavedStateHandle`, а не живёт только в
локальном `remember`.

### 3.4. Контракт режима и карточки

До миграции любого режима заполняется исполнимая mode matrix:

| Поле контракта | Что фиксируется |
|---|---|
| Identity | `sessionId`, pack/content version, source route, resume key |
| Selection | источник карточек, hidden filter, lesson/sublesson, due/filter rules |
| Ordering | sequential, mixed, random seed, frequency batch |
| Exercise | тип задачи и допустимые input modes/renderer |
| Attempt | число попыток, normalizer, hint/reveal/skip semantics |
| Progress | numerator, denominator, shown/answered/encounter distinction |
| Mastery | какие input modes меняют SRS/flower; влияние hint/skip |
| Completion | терминальное условие, summary, next action |
| Persistence | какие поля пишутся и в какой транзакции |
| Navigation | Back/pause/abandon/return target |

`TrainingMode`, `TrainingScreenMode`, `CardType`, `DailyBlockType` и
`BlockRenderVia` нельзя механически объединять. Сначала зафиксировать их независимые
измерения; затем заменить их типизированным launch/policy mapping с compile-time
проверкой неподдерживаемых комбинаций.

## 4. План выполнения

### Фаза 0. Factual baseline и release gates (3-5 дней, P0)

Работы:

- Пометить текущий v2 как incomplete product slice, исправить статусную документацию.
- Зафиксировать route/mode/input/card matrix на основе legacy specs и актуального кода.
- Создать минимальные deterministic fixtures: один pack, chapter, lesson, 5-15 cards,
  drill cards, mastery и active session.
- Добавить test IDs/semantics для основного UX пути.
- Стабилизировать Gradle toolchain: единый JDK, ограничение worker'ов на dev-машинах,
  CI на `dev`, отчёты domain/app tests.
- Снять baseline startup, session start/resume, Submit->feedback, Next->render,
  Room query count и frame timing на одном референсном устройстве.

Gate:

- Один воспроизводимый скрипт поднимает чистую БД с fixture.
- Известные P0-дефекты имеют падающие regression tests до исправления.
- Тестовая команда завершается стабильно без JVM OOM.

### Фаза 1. Golden journey: fresh install -> lesson -> completion -> resume (1-2 недели, P0)

Работы:

- Реализовать минимальный идемпотентный import bundled pack на IO dispatcher.
- Реализовать путь Pack -> Chapter -> Lesson; убрать `lessonId = packId`.
- Инжектировать и вызывать `SessionEngine.startLessonSession/resumeSession`, передавать
  реальный pool и убрать UI fallback первой картой.
- Использовать `AnswerValidator` и доменную попытку вместо lowercase comparison.
- Ввести явные Loading/Awaiting/Checking/Feedback/Completed/Error состояния.
- Блокировать повторные действия во время commit; не показывать success при ошибке.
- Определить Back: active session сохраняется как PAUSED либо показывается подтверждение
  abandon; system и toolbar Back имеют одинаковую семантику.

Gate:

- Fresh install без ручного вмешательства открывает bundled pack и реальный lesson.
- Полный урок завершается, progress сохраняется, повторный запуск resume'ит тот же PK.
- Rotation и process recreation не меняют карточку и не дублируют ответ.
- Нет прямых mutating-вызовов `SessionRepository` из training presentation.

### Фаза 2. Атомарность и целостность состояния (1-2 недели, P0)

Работы:

- Сделать `loadSession` настоящим `@Transaction` DAO query/result.
- Уравнять recovery semantics fake и Room: никакой молчаливой замены current card.
- Синхронно обновлять `currentCardId` и `cursorIndex` либо удалить второй источник
  истины после migration.
- Ввести session revision/command token для stale result и double-tap защиты.
- Атомарно применять answer + shown + mastery/SRS + progress/streak/completion через
  transaction coordinator над одной `GrammarMateDatabase`.
- Разделить hot updates и полную замену pool: обычный Submit/Next не должен делать
  delete/reinsert всех `session_cards` и `session_shown_cards`.
- Ошибочные enum/status/content версии возвращать typed recovery/failure и логировать.

Gate:

- Repository contract suite проходит одинаково для fake и in-memory Room.
- Failure injection в середине commit оставляет БД в исходном состоянии.
- `currentCardId in pool` или terminal/empty состояние доказаны property tests.
- Double Submit/Next засчитывается ровно один раз.

### Фаза 3. UX shell и навигация (1-2 недели, P0/P1)

Работы:

- Скрыть bottom bar/navigation rail на focused training/story flows.
- Ввести typed route arguments и валидацию; запретить `orEmpty()` для required IDs.
- Реализовать empty, blocked, retry, paused, completed и recovered-session экраны.
- Сделать Retry повтором неуспешной операции, а не кнопкой Back.
- Persist Report/Flag; snackbar/TTS effects перестать оставлять no-op.
- Стабилизировать IME/focus, возврат draft, screen reader labels, touch targets,
  font scale 200%, dark theme и phone/tablet layout.
- Home/roadmap получают реактивный progress и не показывают фиктивный 0%.

Gate:

- Navigation tests покрывают Home -> Chapter -> Lesson -> Training -> Completion -> Next.
- Back/deep-link/process restore не создают дубликаты destination/session.
- Все интерактивные элементы доступны через стабильные semantics, не по тексту локали.

### Фаза 4. Режимы вертикальными срезами (4-8 недель, P1)

Порядок по риску и зависимостям:

1. Normal lesson + sequential/mixed review.
2. Verb drill + aux drill.
3. Vocab drill.
4. Daily translate/vocab/verbs composition.
5. Boss/mega/elite и exactly-once rewards.
6. Story reader/quiz как отдельный feature, не искусственно как обычная карточка.
7. Pomodoro как orchestration wrapper, не вариант card renderer.

Для каждого среза обязательны:

- заполненная mode matrix;
- pure plan/policy tests на real pack fixtures;
- Room resume/crash test;
- ViewModel transition tests;
- clickable Compose journey;
- performance comparison с baseline;
- feature flag до прохождения gate;
- удаление старого execution path после одного стабильного релиза; dual write запрещён.

### Фаза 5. Content, backup и audio parity (может идти параллельно после Фазы 2, P1)

Content/data:

- Завершить manifest v1/v2 validation, parsers, path traversal protection и atomic import.
- Partial import сделать явным user decision, не silent skip.
- Идемпотентный re-import и content-version policy для active sessions.
- Backup -> wipe -> restore round trip с WAL checkpoint и schema migration tests.

Audio:

- Закрыть reachable runtime TODO для TTS/ASR/Bluetooth/model download.
- TTS/ASR effects запускать после session commit; cancellation не меняет domain state.
- Permission/model missing/low-memory оформлять как recoverable UX-state.
- Сохранить существующие бюджеты cache switch <100 ms и first Started <=200 ms,
  подтвердить их benchmark/instrumentation tests.

### Фаза 6. Производительность и наблюдаемость (1-2 недели, P1/P2)

Работы:

- Загружать session cards один раз в `Map<CardId, Card>` и prefetch следующей карты.
- Использовать узкие Room projections/Flow и `distinctUntilChanged` для Home/progress.
- Проверить `EXPLAIN QUERY PLAN` для pack/lesson/cards, due, resume и drill filters.
- Добавить Macrobenchmark + Baseline Profile для cold start, open training, resume,
  Submit->feedback, Next->render и mode switch.
- В debug включить StrictMode и Compose recomposition diagnostics.
- Добавить structured events без текста ответа/PII: session/revision/mode, transition,
  duration, recovery, rejected duplicate, persistence failure.

Начальные guardrails, уточняемые после Фазы 0:

| Метрика | Бюджет |
|---|---|
| Warm session resume | p95 <= 100 ms на reference device |
| Submit commit -> feedback | p95 <= 100 ms; DB portion <= 50 ms |
| Next -> rendered card | p95 <= 100 ms |
| Main-thread disk/network I/O | 0 |
| Janky frames в training journey | < 5% |
| Регресс любой baseline-метрики в PR | не более 10% без отдельного ADR |
| Full pool rewrite на обычный Submit/Next | 0 |

### Фаза 7. Cleanup и обоснованная модульность (1-2 недели, P2)

Работы:

- Удалить granular session writer API, unused MVI intents, placeholders и reachable TODO.
- Удалить один из дублирующих mode contracts после полного mapping/test coverage.
- Архивировать legacy Maestro flows и старые UX reports как historical evidence.
- Синхронизировать `TARGET_ARCHITECTURE`, SRS, route catalog и фактический status.
- Ввести dependency checks: presentation не зависит от data implementation; feature не
  импортирует чужую implementation package.

Физическое выделение Gradle feature-модулей разрешается, если одновременно выполнены
минимум два условия:

- API границы стабильны два релиза;
- feature имеет независимый release/test cadence;
- incremental build profile показывает существенную выгоду;
- package dependency checks регулярно ловят реальные нарушения;
- feature вырос настолько, что ownership в `:app` стал неочевиден.

## 5. Регрессионная стратегия

### 5.1. Пирамида

| Уровень | Назначение | Запуск |
|---|---|---|
| Pure domain/property | session transitions, policy, validation, completion, mastery invariants | каждый commit |
| ViewModel/use-case | сериализация intents, stale async result, retry, effect order | каждый commit |
| Repository contract + Room | transaction, rollback, fake/Room parity, migration | каждый PR |
| Compose component/journey | реальные клики, enabled states, focus, accessibility | каждый PR для изменённого feature |
| Instrumented navigation/E2E | fresh install, resume/process death, full mode journeys | PR smoke + nightly full |
| Performance | startup, DB/query count, frame timing, audio memory/latency | nightly + release |

Общий coverage percentage не является главным gate. Требования:

- 100% критических session invariants и допустимых/недопустимых state transitions;
- не менее 90% branch coverage для session/mode policy;
- не менее 80% branch coverage для ViewModel/use-case/repository mappings;
- каждый видимый route имеет хотя бы один clickable happy path и один failure/retry path;
- flaky rate nightly suite <1% на окне 30 запусков.

### 5.2. Обязательная regression matrix

| Сценарий | Проверки |
|---|---|
| Fresh install | seed/import, Home content, первый lesson без ручной подготовки |
| Start/resume | тот же `sessionId/currentCardId/pool/order`, draft restore |
| Submit | correct/wrong, accents/case, empty, rapid double tap, DB failure |
| Hint/reveal/skip | attempts, shown, mastery, accuracy, completion semantics |
| Navigation | toolbar/system Back, tab click, deep link, rotate, process death |
| Content mutation | hidden current, pack update/delete/re-import во время pause |
| Completion | last card, empty pool, exactly-once progress/streak/reward |
| Input | keyboard, word bank, voice permission/model missing/cancel |
| Modes | selection/order/renderer/progress/resume для каждой строки mode matrix |
| UI variants | RU/EN, dark/light, font 100/200%, phone/tablet, API 26/current |
| Performance | cold/warm start, session open, submit, next, memory after audio cycles |

### 5.3. CI gates

Fast job:

```text
:domain:test
:app:testDebugUnitTest
lint/static analysis
forbidden dependency/import checks
```

PR job:

```text
assembleDebug
Room contract/migration tests
Compose clickable smoke
fresh-install emulator journey
test/coverage reports for both :domain and :app
```

Nightly/release:

```text
full mode matrix on emulator API 26 + current API
process-death/backup-restore suite
Maestro black-box journeys
macrobenchmarks and audio/device tests
```

CI должен включать ветку `dev`, стандартизировать JDK между локальной разработкой и
runner'ом, ограничивать concurrency по доступной памяти и публиковать отчёты даже при
падении тестов.

## 6. Definition of Done для любого feature slice

- Бизнес-контракт и mode matrix обновлены до кода.
- Нет прямой записи presentation -> repository/DAO.
- Success UI публикуется только после успешного commit.
- Ошибки, Empty, Back, retry, rotation и process restore обработаны.
- Domain, ViewModel, Room и clickable journey tests зелёные.
- Для schema change есть forward migration, schema fixture и restore test.
- Performance budgets не ухудшены более чем на 10%.
- Route включён только после end-to-end gate.
- Удалён старый writer/path; dual write и бесконечные compatibility adapters запрещены.
- Документы и traceability соответствуют фактическому коду.

## 7. Метрики результата на 30/60/90 дней

### 30 дней

- Golden journey fresh install -> lesson -> completion -> resume работает и покрыт E2E.
- `TrainingViewModel` не мутирует `SessionRepository` напрямую.
- Room/fake contract suite защищает current-card и atomic-submit invariants.
- CI стабильно запускает domain/app tests на `dev`; reachable route не ведёт в runtime TODO.

### 60 дней

- Lesson, review, verb/aux, vocab и daily имеют mode policy и full journey tests.
- Все session/progress/mastery изменения проходят через единую transaction boundary.
- Back/process death/double tap/persistence failure входят в обязательный PR smoke.
- Home/roadmap progress реактивен; видимых placeholders нет.

### 90 дней

- Все релизные режимы покрыты mode matrix и E2E; undocumented fallback отсутствует.
- Performance budgets автоматизированы; hot path не перечитывает весь lesson/pool.
- Reachable runtime `TODO()` = 0; stale документация либо обновлена, либо архивирована.
- Решение о Gradle-модуляризации принято по build/dependency метрикам, не заранее.

## 8. Риски плана и ограничения

| Риск | Ранний сигнал | Защита |
|---|---|---|
| Рефакторинг снова уйдёт в инфраструктуру без UX-результата | нет полного lesson journey после двух недель | каждый этап заканчивается видимым vertical slice |
| ViewModel станет новым coordinator god object | растёт число repository imports/branching by mode | orchestration only, pure mode policy, dependency rule |
| Старый и новый path останутся навсегда | feature flag живёт больше одного релиза | владелец и removal date у каждого flag; dual write запрещён |
| Неверная legacy-семантика будет закреплена тестами | mode matrix содержит `TBD` в момент реализации | characterization + product acceptance до включения route |
| Большая транзакция ухудшит latency | Submit DB p95 >50 ms | узкие DAO operations, query tracing, benchmark gate |
| Schema migration повредит данные | нет N-1 fixture/restore test | additive migration, backup round trip, destructive migration запрещена |
| Audio tests будут зелёными только на mock | нет physical-device job | отдельный device release gate для TTS/ASR/Bluetooth/memory |

## 9. Что не делать

- Не начинать третий rewrite и не менять Compose/Room/Hilt без отдельного измеримого основания.
- Не создавать общий `SessionStore` до доказанного multi-owner use case.
- Не дробить проект на десятки Gradle-модулей до стабилизации feature contracts.
- Не исправлять UX fallback'ом, если durable state невалиден.
- Не считать количество `@Test` доказательством покрытия пользовательского пути.
- Не публиковать route, parser или audio path, содержащий reachable `TODO()`/placeholder.
- Не оптимизировать без baseline и не принимать performance-регрессию без ADR.

## 10. Проверка, выполненная при аудите

- Проанализированы 124 production Kotlin-файла (16 961 строк) и 31 test-файл
  (6 645 строк), 442 функции `@Test`.
- `:domain:test` завершён успешно с `--no-parallel --max-workers=1`.
- `:app:testDebugUnitTest` завершён успешно с теми же ограничениями, но занял 10 минут;
  Kotlin compile daemon один раз завершился и Gradle продолжил через fallback.
- Первый общий test run упал до задач из-за native JVM OOM; причина зафиксирована в
  `hs_err_pid39228.log` (106 JVM threads, недостаток native memory). Это подтверждает
  необходимость стабильной toolchain/concurrency конфигурации.
- Instrumented/UI тесты не запускались: `app/src/androidTest` отсутствует, `adb` не
  доступен в текущем окружении.

## 11. Архитектурное решение

Выбор и альтернативы зафиксированы в
[`001-incremental-session-engine-integration.md`](decisions/001-incremental-session-engine-integration.md).
