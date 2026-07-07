# PRD-002 — Слой данных Room: PackImport + Backup (без мигратора)

**Эпизод:** E02 (epic_id=86), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (PRD — продуктовая перспектива)
**Дата:** 2026-07-07
**Status:** Accepted

**Артефакт-источник:** Discovery Brief REQ-002 (artifact_id=472, `00-discovery-brief.md`, decision=go, status=accepted).
**Upstream контракт:** SRS-001 / E01 (artifact_id=450, `REQ-001-domain-core/02-srs.md`) — 9 доменных портов зафиксированы как стабильный контракт (§5 SRS-001).
**Repository:** BaseGrammy (repository_id=7), ветка `rewrite/v2-clean-architecture`. Integration branch = `dev`.
**Код:** PRD-002

> PRD фиксирует продуктовые границы (intent, scope, ценность, метрики), **не
> технический контракт**. Технические сигнатуры, формат backup'а и схема Room —
> забота SRS-002 (saga-architect). Все ссылки на доменные порты — через код
> SRS-001, здесь они не дублируются. Источник истины по текущему состоянию
> кода — `app/.../v2/core/data/*` (verified 2026-07-07, см. CLAUDE.md).

---

## 1. Резюме эпизода

E02 завершает **слой данных v2** (`v2/core/data`) до полного покрытия
функционала legacy-приложения в трёх продуктовых направлениях:

1. **PackImport → Room** — приём контента паков из ZIP/CSV/YAML/JSON (5 парсеров)
   и наполнение Room-сущностей, чтобы сетка Home отображала реальные паки/уроки.
   Парсинг-логика переносится из legacy `PackImporter`, а формат хранения меняется
   с разрозненных YAML/CSV-файлов под `ReentrantLock` на **атомарные
   `@Transaction`** в единой Room-БД.
2. **Backup / Restore** — экспорт и восстановление снимка пользовательского
   состояния (прогресс, SRS, настройки) через SAF; restore-точка на запуске
   приложения, чтобы переустановка не теряла прогресс.
3. **Удаление / `@Deprecated` YamlToRoomMigrator** — переходный мигратор
   перестаёт быть активным путём данных (решение спонсора Q3 = greenfield data).

Дополнительно: реализация `lesson_progress` fresh в Room (аналог legacy
`PackLessonProgressStore`) и `LanguageManager.ensureSeedData`-эквивалент
для seed-языков en/it/de/zh/ru/el.

**Бизнес-решение:** go (Brief §8) — критическая блокировка Home grid и всех
вертикальных эпиков Wave 1–4; upstream порты (E01) готовы; greenfield Q3
упрощает работу (мигратор не нужен, только свежий импорт).

---

## 2. Бизнес-контекст (почему критично)

GrammarMate v2 построен по чистой слоистой архитектуре: домен (`:domain`,
pure Kotlin, E01) определяет **порты**, а `:app:v2/core/data` предоставляет
**имплементации** (Room-mapping). Сегодня:

- **Room-схема уже существует** — `GrammarMateDatabase` (version=1,
  `exportSchema=true`, WAL), 29 entities в 6 файлах (`ContentEntities.kt`,
  `SessionEntities.kt`, `MasteryEntities.kt`, `ProgressEntities.kt`,
  `DrillEntities.kt`, `UserContentEntities.kt`) и 6 DAO (`ContentDao`,
  `SessionDao`, `MasteryDao`, `ProgressDao`, `DrillDao`, `UserContentDao`).
- **7 имплементаций портов уже написаны** (`ContentRepositoryImpl`,
  `SessionRepositoryImpl`, `MasteryRepositoryImpl`, `ProgressRepositoryImpl`,
  `UserContentRepositoryImpl`, `VocabDrillRepositoryImpl`,
  `SettingsRepositoryImpl`) — но это **read-сторона**, она читает из Room.
- **Нет write-стороны для контента:** `ContentRepositoryImpl.getLanguages()`
  возвращает захардкоженный seed, `getPacks()`/`getLesson()` читают таблицы —
  но **ничто эти таблицы не наполняет**. Сетка Home пуста.

Критичность двух уровней:

1. **Home grid пуст без PackImport.** Пока нет пути записи контента в Room,
   вертикальные эпики Wave 1 (Training E04), Wave 2 (Verb E05, Vocab E06),
   Wave 3 (Daily E07, Story), Wave 4 (Gamification, Boss, Pomodoro) **не имеют
   данных для отображения и тренировки**. Это жёсткая блокировка эпизода.
2. **`ContentRepositoryImpl` — источник данных для ВСЕХ вертикальных эпиков**
   (Brief §12, `shared_mutation_risk=true`). Любое изменение Room-схемы после
   старта Wave 1 = миграция БД + drift по всем downstream. Поэтому PackImport
   должен быть закончен и стабилизирован **до** Wave 1.

Backup/Restore критично для **доверия пользователя**: SRS-001 FR-3 сделал
завершение под-урока атомарным (фикс бага `card_15`), но если при переустановке
теряется весь прогресс — атомарность в процессе бесполезна. Restore-точка на
`MainActivity.onCreate` закрывает этот пробел.

Удаление migrator'а — продукт-решение Q3 (greenfield data): свежий импорт
заменяет миграцию legacy-данных; мигратор остаётся как dead-code с
`@Deprecated`-предупреждением, чтобы не ломать компиляцию переходных сборок
(согласовано с E01 FR-13).

---

## 3. User stories (через downstream-эпики)

Пользователь здесь — одновременно и end-user приложения, и downstream-команда
(Wave 1–4), потребляющая данные. Каждая US связана с эпиком, который она
разблокирует.

### US-1. Импорт pack из ZIP (через SAF или assets)

**Как** пользователь, **я хочу** выбрать ZIP-файл пака (Lesson Pack) в
системном диалоге SAF или автоматически из assets, **чтобы** в моей базе
появился новый пак с уроками, карточками, drill'ами и stories.

- Разблокирует: отображение контента на Home (US-3), все тренировочные эпики Wave 1–4.
- Источник-референс: legacy `PackImporter.importPackFromUri(uri, resolver)`,
  `importPackFromAssets(assetPath)`.
- Критичный инвариант: **атомарность** — либо весь pack импортирован, либо ни
  одна сущность не сохранена (room `withTransaction`, rollback при ошибке).

### US-2. Импорт одного урока из файла

**Как** пользователь/разработчик, **я хочу** импортировать отдельный урок
(CSV/MD) без целого пака, **чтобы** тестировать формат урока или добавлять
уроки в существующий пак.

- Источник-референс: legacy `PackImporter.importLessonFromUri(...)`.
- Связано: `createEmptyLesson`, `deletePack`, `resetAndReimport` (partial-import
  confirmation).

### US-3. Просмотр контента на Home

**Как** пользователь, **я хочу** видеть на Home сетку паков/языков с реальными
данными, **чтобы** выбрать, что тренировать.

- Зависит от: US-1 (наполненные таблицы `packs`/`lessons`/`cards`).
- Покрытие: `ContentRepositoryImpl.getPacks()`/`getLanguages()`/`getLessons()`
  уже реализованы (read); E02 обеспечивает их непустость через import-flow.
- SM-критично: Home grid **отображает** импортированные паки (см. §6 SM-1).

### US-4. Backup прогресса

**Как** пользователь, **я хочу** создать резервную копию своего прогресса в
SAF-папку (или внутреннее хранилище), **чтобы** не потерять дни тренировок при
сбое/смене устройства.

- Источник-референс: legacy `BackupManager.createBackup()` /
  `BackupManagerImpl.createBackupToInternal()`.
- Снимок пользовательского состояния из единой Room-БД
  (`grammarmate_v2.db`): прогресс, SRS, streak, drill, pomodoro, настройки,
  флаги миграций.
- Формат backup'а (DB-file с WAL checkpoint vs JSON-экспорт) — OQ-1,
  решается в SRS-002.

### US-5. Restore при переустановке

**Как** пользователь, **я хочу** при первом запуске после переустановки
автоматически (или одним тапом) восстановить прогресс из найденного backup'а,
**чтобы** продолжить с того же места.

- Restore-точка: `MainActivity.onCreate` (legacy-паттерн).
- Источник-референс: `BackupManager.restoreFromBackup(backupPath)` /
  `restoreFromBackupUri(uri)` / `getAvailableBackups()`.
- Round-trip: backup → restore должно давать **идентичное** пользовательское
  состояние (SM-6).

### US-6. Multi-language (en/it/de/zh/ru/el)

**Как** пользователь, изучающий несколько языков, **я хочу** иметь доступ к
6 языкам обучения (English/Italian/German/Chinese/Russian/Greek), **чтобы**
переключаться между ними.

- Источник-референс: legacy `LanguageManager.ensureSeedData()` /
  `getLanguages()` / `defaultPacks` / `cleanupStalePacks()`.
- В v2 seed уже захардкожен в `ContentRepositoryImpl.LANGUAGES_SEED`
  (6 языков). E02 формализует этот seed как `ensureSeedData`-эквивалент (напр.,
  модуль данных, не UI), включая cleanup-логику устаревших паков при смене
  конфигурации assets.

---

## 4. Scope IN

Все элементы берутся из Brief §4–5 и верифицируются по фактическому коду.

### 4.1. PackImport → Room (5 парсеров)

- Перенос парсинг-логики из legacy `app/legacy-src/.../data/` в v2
  `app/.../v2/core/data/`:
  1. `CsvParser` — предложения (cards SENTENCE, `card_<lineNumber>` ID);
  2. `VerbDrillCsvParser` — спряжения глаголов (cards VERB_DRILL);
  3. `VocabCsvParser` — словарные карточки (cards / vocab words);
  4. `MultilingualStoryParser` + `StoryQuizParser` — истории с разметкой
     `{it}…{/it}`/`{en}`/`{ru}`/`{el}`/`{de}`/`{zh}` и `{pause:N}`
     (многоязычные сегменты, check-in/check-out quiz); доменный pure-парсер
     уже зафиксирован в E01 FR-12, E02 подключает его к data-flow;
  5. `BgVocabCsvParser` — фоновая лексика (background vocab deck).
- Точки входа (legacy-контракт, переносится в v2):
  `importPackFromUri(uri, resolver)`, `importPackFromAssets(assetPath)`,
  `readPackManifestFromAssets`, `importLessonFromUri(...)`.
- Все 5 парсеров наполняют соответствующие DAO (`ContentDao`, `DrillDao` и т. д.).
- Atomic import: `database.withTransaction { ... }` — сбой на середине = rollback.

### 4.2. Manifest schema v1 и v2

- Парсинг обоих вариантов через эквивалент `LessonPackManifest`
  (`schemaVersion: Int`, поле `lessons`, опциональные `chapters` для v2,
  секции `verbDrill`/`vocabDrill`/`backgroundVocab`).
- Валидация: v1 — хотя бы один урок/drill/bg-vocab; v2 — хотя бы одна глава
  с контентом (legacy-контракт сохраняется).
- Lesson-order из manifest (v2: chapter order; v1: lesson order) — канонический
  порядок отображения.

### 4.3. Backup / Restore через Room snapshot

- `createBackup()` — снимок пользовательского состояния единой Room-БД в SAF.
- `restoreFromBackup(path)` / `restoreFromBackupUri(uri)` — восстановление.
- `getAvailableBackups(treeUri)` / `getAvailableBackups()` — список backup'ов.
- `deleteBackup(path)`, `hasBackup()` — утилиты.
- Restore-точка на `MainActivity.onCreate` (legacy-паттерн; сам `MainActivity`
  — вне scope E02, в E04+, но **контракт restore-вызова** фиксируется здесь).
- Backup metadata (timestamp, размер, schemaVersion) — сохраняется (legacy
  `BackupManagerImpl.writeBackupMetadata`).

### 4.4. lesson_progress — fresh в Room

- Аналог legacy `PackLessonProgressStore`: прогресс по парам (packId, lessonId)
  в Room, реализуемый fresh (OQ-2 = answered=yes, Brief §6).
- Лежит поверх `MasteryDao` / `ProgressDao` (сущности уже определены в E01-коде).

### 4.5. LanguageManager.ensureSeedData-эквивалент

- Seed 6 языков (en/it/de/zh/ru/el) — формализация существующего seed в
  `ContentRepositoryImpl.LANGUAGES_SEED`.
- `cleanupStalePacks`-эквивалент: удаление паков, более не входящих в defaultPacks,
  при смене конфигурации assets.
- Лежит в data-слое (`:app:v2/core/data`), НЕ в UI.

### 4.6. Удаление / `@Deprecated` YamlToRoomMigrator

- `YamlToRoomMigrator` помечается `@Deprecated` (согласовано с E01 FR-13; brief §4
  формулирует как «удалить», §5 quality-gate допускает `@Deprecated` как переходный
  путь). **Спонсорский выбор** (Q3 greenfield data): мигратор перестаёт быть
  активным путём данных.
- Ни один активный кодовый путь в v2 не вызывает migrator (SM-5).
- Полное физическое удаление — отдельная задача после стабилизации Wave 1
  (out of scope для E02; фиксируется как явный follow-up).

### 4.7. Partial-import / delete / reset / create-empty flows

- Partial-import confirmation: если импорт частично не удался (некоторые
  карточки/парсеры упали), пользователь получает confirm-диалог.
- `deletePack(packId)` — удаление пака (FK CASCADE в схеме уже настроено).
- `resetAndReimport` — сброс и повторный импорт (force reload default packs).
- `createEmptyLesson` — создание пустого урока (для редактирования/тестов).

---

## 5. Scope OUT (явные non-goals)

### 5.1. Доменные порты — E01 (upstream, НЕ E02)

- Контракты `ContentRepository`, `SessionRepository`, `MasteryRepository`,
  `ProgressRepository`, `UserContentRepository`, `VocabDrillRepository`,
  `SettingsRepository`, `AudioRepository`, `AudioModelRepository` —
  **зафиксированы** в SRS-001 §5. E02 пишет имплементации под них, **не меняя**
  сигнатуры портов. Любое изменение сигнатуры = drift-задача (SRS-001 NFR-4).

### 5.2. Аудио-адаптеры — E03

- `SherpaAudioRepository`, `SherpaAudioModelRepository`, TTS/ASR/SFX — вне scope.
- PackImport копирует **пути** к аудиофайлам (как String), но не проигрывает
  аудио (это E03 + E08+).

### 5.3. UI-экраны — E04 и далее

- Home, Training, Verb, Vocab, Daily, Settings и др. — вне scope.
- В частности: **Settings-экран с кнопками Import/Backup/Restore** — это E13
  (Settings epic). E02 предоставляет только **data-контракт** (исходящие методы),
  которые UI в E13 будет вызывать.

### 5.4. Переписывание Room-схемы — если уже есть

- Текущая Room-схема (29 entities, version=1) считается **стабильной** в
  продуктовом смысле. E02 **не переписывает** схему с нуля, только наполняет
  её данными. Если в ходе PackImport обнаружится, что какая-то сущность не
  покрывает legacy-поле — это drift-задача с миграцией БД (`addMigrations`,
  version bump), а не переписывание.

### 5.5. Settings import UI (E13)

- UI кнопок «Import pack», «Backup», «Restore», «Delete pack» в Settings —
  E13. E02 только определяет, **какие операции** доступны (US-1/4/5/2), но не
  их визуальное оформление.

### 5.6. Миграция пользовательских данных legacy → v2

- Перенос прогресса старых пользователей из legacy YAML-хранилища в v2 Room —
  НЕ делается (Q3 greenfield data). `YamlToRoomMigrator` помечается
  `@Deprecated`, но не выполняет реальной миграции на пользовательских данных.

---

## 6. Success metrics (SM, измеримые)

| Код | Метрика | Измеримость |
|---|---|---|
| **SM-1** | PackImport работает | После импорта тестового пака Home grid отображает пак/уроки (US-1, US-3). |
| **SM-2** | Atomic import | Сбой на середине pack-import (имитированный) → rollback через `withTransaction`, **0** новых сущностей в БД (никаких частичных паков). |
| **SM-3** | Partial-import confirm | При частичном падении (некоторые карты) пользователь получает confirm-диалог, корректные карты сохраняются, упавшие — нет; ни один pack не «наполовину сломан». |
| **SM-4** | Manifest v1/v2 | Оба варианта `LessonPackManifest` парсятся без ошибок; валидация (минимум один урок/chapter/drill/bg-vocab) работает. |
| **SM-5** | Migrator устранён | `YamlToRoomMigrator` помечен `@Deprecated` (E01 FR-13); `grep` активных вызовов migrator в v2 = **0**; компиляция `:app` зелёная. |
| **SM-6** | Backup/Restore round-trip | backup → wipe → restore даёт **идентичное** пользовательское состояние (прогресс/SRS/streak/drill/pomodoro/настройки); diff = пусто. |
| **SM-7** | Restore-точка | На `MainActivity.onCreate` при наличии backup'а и свежей БД restore предлагается/выполняется (legacy-паттерн). |
| **SM-8** | LanguageManager.ensureSeedData | `getLanguages()` возвращает ровно 6 языков (en/it/de/zh/ru/el); cleanup-stale-packs удаляет паки, не входящие в defaultPacks. |
| **SM-9** | Home отображает паки | End-to-end: после PackImport → `ContentRepositoryImpl.getPacks()` возвращает непустой список (SM-критично, см. §2). |
| **SM-10** | Room-схема стабильна | После E02 `git diff` на Room-entities (без явных drift-задач) = пусто; schema export в `app/schemas/` обновлён; version не растёт без миграции. |

---

## 7. Зависимости

### 7.1. Depends on (upstream)

- **E01 — SRS-001 (domain core), artifact_id=450, status=accepted.**
  9 доменных портов зафиксированы в §5 SRS как стабильный контракт. Data layer
  пишет имплементации **под эти контракты**, не меняя их. Любое изменение
  сигнатуры после старта Wave 1 = drift (SRS-001 NFR-4).
- **Реальный код v2** (`v2/core/data/*`) — source of truth. Room-схема,
  6 DAO, `GrammarMateDatabase` и 7 `*Impl` уже существуют; E02 наполняет
  write-сторону и завершает read/write-полноту.

### 7.2. Блокирует (downstream)

- **E04 — Training (Wave 1):** `SessionRepositoryImpl` + контент из PackImport
  нужны для тренировочной сессии. Без импортированных уроков `SessionEngine`
  (FR-3 E01) не имеет карт для сессии.
- **E05 — Verb drill, E06 — Vocab drill (Wave 2):** зависят от наполненных
  `VerbDrillCardEntity` / `VocabWordEntity` (через Verb/Vocab парсеры PackImport)
  и `VocabDrillRepositoryImpl`.
- **E07 — Daily, Story epics (Wave 3):** Daily-композиторы (FR-14 E01) требуют
  контент; Stories требуют импортированного story-контента (через
  `MultilingualStoryParser`/`StoryQuizParser`).
- **E08+ — Gamification, Boss, Pomodoro (Wave 4):** читают прогресс из
  `ProgressRepositoryImpl`/`UserContentRepositoryImpl`.
- **E13 — Settings UI:** кнопки Import/Backup/Restore в Settings вызывают
  операции, контракты которых фиксируются здесь (US-1/4/5/2).
- **MainActivity.onCreate restore-точка:** restore-вызов фиксируется здесь,
  реализация — в E04+ (app-shell).

### 7.3. Параллельно

- **E03 — Audio adapter:** не блокирует E02 (pack импортирует **пути** к аудио,
  не проигрывает). Запускается параллельно после E01.

---

## 8. Риски

| Код | Риск | Митигация |
|---|---|---|
| **R1** | **data-integrity-risk (atomic import):** частичный pack-import оставляет «полу-пак», ломающий read-сторону downstream. | `database.withTransaction { ... }` — rollback при любой ошибке (SM-2); наследует гарантию `AtomicFileWriter` из v1, но на уровне Room. |
| **R2** | **shared-mutation-risk (ContentRepositoryImpl):** это источник данных для ВСЕХ вертикальных эпиков; любое изменение Room-схемы после Wave 1 = миграция БД + drift по всем downstream. | Room-схема фиксируется в SRS-002 (saga-architect); `exportSchema=true` + `schemaLocation=app/schemas/` уже включены; AC покрывают стабильность схемы (SM-10); drift-задачи трассируются к SRS-002. |
| **R3** | **schema-drift после Wave 1:** если PackImport вскроет legacy-поле, не вошедшее в текущую схему, потребуется миграция БД (version bump) под всеми downstream. | Завершить PackImport **до** старта Wave 1 (см. §7.2); при обнаружении gap — явная drift-задача с `addMigrations`, не тихое расширение схемы. |
| **R4** | **Backup-формат round-trip:** если формат не покрывает все 29 entities, restore теряет данные (SM-6 fail). | Формат фиксируется в SRS-002 (OQ-1); AC покрывают полный round-trip по всем пользовательским таблицам. |
| **R5** | **Парсинг-регрессия (5 парсеров):** перенос логики из legacy в v2 может внести расхождения в формат CSV/YAML/JSON. | Тесты legacy-парсеров (`CsvParserTest`, `VerbDrillCsvParserTest`, `VocabCsvParserTest`, `BgVocabCsvParserTest`, `MultilingualStoryParserPauseTest`, `StoryQuizParserTest`) переносятся как regression-lock; SRS-002 фиксирует поведение. |
| **R6** | **migrator-removal backward-compat:** `@Deprecated` вместо delete сохраняет компиляцию, но компилятор-предупреждение должно быть осознанным (не заглушено). | SM-5: 0 активных вызовов migrator в v2; `@Suppress` разрешён только в самом файле migrator'а. |

---

## 9. Допущения

Из Brief §4 (гипотезы H1–H3):

- **H1 (verified):** доменные порты (`ContentRepository`, `MasteryRepository` и
  др.) зафиксированы в SRS-001 (E01, accepted) → data layer пишет имплементации
  под известные контракты, не определяя их.
- **H2 (verified):** legacy `PackImporter` (`app/legacy-src/.../data/PackImporter.kt`,
  `CsvParser`, `VerbDrillCsvParser`, `VocabCsvParser`, `MultilingualStoryParser`,
  `StoryQuizParser`, `BgVocabCsvParser`) — рабочий референс; логика парсинга
  переносится, формат хранения меняется с YAML/CSV-файлов под `ReentrantLock`
  на Room-сущности в одной `@Transaction`.
- **H3 (verified):** 29 Room-сущностей + 6 DAO уже определены в v2
  (`v2/core/data/local/`, verified 2026-07-07) → нужно только наполнить их данными
  через import-flow.

Дополнительные допущения PRD:

- **A1:** Текущая read-имплементация портов (`ContentRepositoryImpl` и др.)
  функционально корректна и **не переписывается** в E02 (только добавляются
  write-операции контента). Если read-side содержит баг — отдельная задача.
- **A2:** `MainActivity` и app-shell (DI-модули, Hilt graph) находятся в
  E04+ / app-shell; E02 предоставляет data-контракт, а не wire-up.
- **A3:** 6 языков (en/it/de/zh/ru/el) — стабильный продуктовый набор; новые
  языки добавляются через расширение seed (не через UI в E02).

---

## 10. Open questions

| Код | Вопрос | Статус | Где решается |
|---|---|---|---|
| **OQ-1** | Backup формат: Room DB file (WAL checkpoint) или JSON-экспорт сущностей? | **Answered** (Brief §6): вероятно Room DB file с WAL checkpoint. Окончательная фиксация формата + покрытие всех 29 entities — в **SRS-002** (saga-architect). | SRS-002 |
| **OQ-2** | `lesson_progress` (legacy migrator skipped) — реализовать fresh в Room как `PackLessonProgressStore`-аналог? | **Answered** (Brief §6): да, fresh в Room, поверх `MasteryDao`/`ProgressDao`. | SRS-002 (имплементация) + §4.4 (PRD scope) |
| **OQ-3** | Полное физическое удаление `YamlToRoomMigrator` — в каком эпизоде? | **New.** После стабилизации Wave 1 (smoke end-to-end через E04+). E02 делает `@Deprecated`; delete — отдельная follow-up задача (вне scope E02). | Follow-up task |
| **OQ-4** | Settings UI (E13) вызывает import — какие exactly операции? | Уточняется в E13 (UC); E02 фиксирует контракты: `importPackFromUri`, `importPackFromAssets`, `importLessonFromUri`, `createBackup`, `restoreFromBackupUri`, `deletePack`, `resetAndReimport`, `createEmptyLesson`. | E13 (downstream) |

---

## 11. Traceability

| Артефакт | Тип | Source (derived_from) |
|---|---|---|
| **PRD-002** (этот документ) | PRD | Discovery Brief REQ-002 (artifact_id=472) |
| Scope §4.1 PackImport | — | Brief §4 H2; SRS-001 §5.1 (ContentRepository); legacy `PackImporter` |
| Scope §4.3 Backup/Restore | — | Brief §4–5; legacy `BackupManager` |
| Scope §4.6 migrator deprecate | — | Brief §4; **SRS-001 FR-13** (E01, артефакт 450) |
| Scope §4.5 ensureSeedData | — | Brief §5; SRS-001 §5.1 (`getLanguages`); legacy `LanguageManager` |
| US-3 / SM-9 Home grid | — | SRS-001 FR-6.1 ContentRepository (read-only контент) |
| R1 atomic import | — | Brief §3 risk-trigger; SRS-001 FR-3 (атомарность SessionEngine, аналог) |
| R2 shared-mutation | — | Brief §12; SRS-001 NFR-4 (стабильность контракта портов) |

UC (saga-analyst, будущие) будут `derived_from` US-кодов (US-1..US-6) выше.
FR/NFR (SRS-002, saga-architect) будут `derived_from` scope §4 и метрик §6.
Dev-задачи (saga-planner) будут `implements` FR-коды SRS-002.

---

*PRD фиксирует продуктовые границы E02 (intent, scope, ценность, метрики),
не технический контракт. Все ссылки на доменные порты идут через SRS-001 §5
(E01, accepted) — здесь сигнатуры не дублируются. Реальное состояние кода
верифицировано по `app/.../v2/core/data/*` на 2026-07-07 (CLAUDE.md:
code is source of truth).*
