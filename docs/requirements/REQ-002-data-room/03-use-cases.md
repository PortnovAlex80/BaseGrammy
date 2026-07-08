# UC-002 — Use Cases: Слой данных Room + PackImport + Backup

**Эпизод:** E02 (epic_id=86), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (UC — поведенческая перспектива)
**Дата:** 2026-07-07
**Status:** Accepted

**Артефакт-источник:** PRD-002 (artifact_id=475, `01-prd.md`, status=accepted).
**Upstream контракт:** SRS-001 / E01 (artifact_id=450) — 9 доменных портов стабильны.
**Repository:** BaseGrammy (repository_id=7), ветка `rewrite/v2-clean-architecture`.
**Код:** UC-002

> E02 — **инфраструктурный** эпизод. Его «пользователи» — это не только конечный
> юзер приложения, но и downstream-эпики (E04–E07, E13), которые потребляют
> данные и контракты, фиксируемые здесь. Акторы явно помечены: «конечный юзер
> (через E13 Settings UI)», «downstream-эпик», «система», «разработчик».
>
> Settings-UI (кнопки Import/Backup/Restore) находится в E13 — вне scope E02
> (PRD §5.3, §5.5). Здесь фиксируются только поведенческие контракты операций,
> которые UI в E13 будет вызывать. AC-preview в каждом UC — это заготовка для
> `03-acceptance-criteria.md` (saga-analyst, AC-задача) после SRS-002.

---

## 0. Актёры и заинтересованные стороны

| Актёр | Описание | Где «живёт» |
|---|---|---|
| **Конечный юзер (U)** | Пользователь GrammarMate, который импортирует паки, делает backup, переустанавливает приложение. | Внешний; взаимодействует через Settings UI в **E13** (вне scope E02). |
| **Downstream-эпик (D)** | E04 (Training), E05 (Verb), E06 (Vocab), E07 (Daily/Story), E08+ (Gamification/Boss/Pomodoro), E13 (Settings) — потребляют данные и контракты E02. | Внутренний; «вызывает» операции как library API. |
| **Система (S)** | Приложение GrammarMate v2: app-shell, `MainActivity.onCreate`, `LanguageManager.ensureSeedData`-эквивалент, Room-БД. | `:app:v2/*`. |
| **Разработчик (R)** | Член команды, работающий с кодом v2; зависит от `@Deprecated`-маркировки migrator'а и от стабильности схемы. | Внешний; читает предупреждения компилятора. |

---

## UC-1. Импорт pack из ZIP (SAF) → Room

**Code:** UC-1
**Связан с US:** US-1 (PRD §3)
**Actor:** Конечный юзер (через E13 Settings UI) ИЛИ downstream-эпик (через library API `importPackFromUri` / `importPackFromAssets`).

**Precondition:**
- Приложение установлено, Room-БД `grammarmate_v2.db` создана (E01 read-side).
- ZIP-файл пака доступен: либо через системный SAF-диалог (URI + `ContentResolver`), либо в `assets/`.
- ZIP содержит валидный `manifest.json` (schema v1 или v2 — PRD §4.2) и ресурсы уроков/drills/stories.

**Основной поток (Main flow):**
1. Юзер (или downstream-вызов) инициирует импорт, передавая URI пака или asset-path.
2. PackImporter открывает ZIP, читает `manifest.json`, определяет `schemaVersion`.
3. Валидация manifest (UC-бизнес-правило BR-1): v1 — хотя бы один lesson/drill/bg-vocab; v2 — хотя бы одна chapter с контентом.
4. Запускается `database.withTransaction { ... }` (атомарная единица).
5. Внутри транзакции последовательно отрабатывают нужные парсеры: `CsvParser`, `VerbDrillCsvParser`, `VocabCsvParser`, `MultilingualStoryParser` + `StoryQuizParser`, `BgVocabCsvParser` (PRD §4.1).
6. Каждая успешно распарсенная сущность пишется в соответствующий DAO (`ContentDao`, `DrillDao`, и т. д.).
7. По завершении всех парсеров транзакция commit'ится.
8. PackImporter возвращает результат импорта (pack-id, счётчики импортированных сущностей).

**Альтернативные потоки (Alternate flows):**
- **A1 — Manifest невалиден:** BR-1 нарушен → импорт прерывается до транзакции, возврат ошибки «manifest invalid», БД не тронута.
- **A2 — Ошибка парсера/DAO mid-transaction (atomic rollback):** любая ошибка внутри `withTransaction` → rollback (SM-2). Ни одна сущность не сохранена. Возврат ошибки с указанием сбойного файла.
- **A3 — Partial-import (часть карточек повреждена):** некоторые карты не парсятся, остальные валидны → см. **UC-3** (partial-import confirmation).
- **A4 — Pack с таким pack-id уже существует:** (поведение детализируется в SRS-002) — варианты: отказ, replace, error. BR-2 фиксирует политику.

**Postcondition:**
- В Room-БД появились сущности пака: pack, languages-связи, lessons, cards (5 типов), drills, stories — все в одной атомарной записи (SM-1, SM-9).
- `ContentRepositoryImpl.getPacks()` теперь возвращает этот пак (US-3 разблокирован).
- Никаких «полу-паков» в БД (SM-2).

**Business rules:**
- **BR-1 (manifest validation):** v1 требует ≥1 {lesson | verbDrill | vocabDrill | backgroundVocab}; v2 требует ≥1 chapter с контентом. Наследует legacy-контракт (PRD §4.2, SM-4).
- **BR-2 (pack-id collision):** политика повторного импорта того же pack-id фиксируется в SRS-002 (OQ ветвления нет на уровне PRD; legacy-референс — upstream-контракт).
- **BR-3 (atomicity):** вся запись пака = одна Room-`@Transaction`; rollback при любой ошибке (PRD R1, SM-2).

**covers:** [FR TBD — SRS-002 ещё не написан; ожидаемые FR: PackImport-via-URI, PackImport-via-Assets, manifest-v1/v2 parsing, atomic-pack-transaction]
**AC preview (для 03-acceptance-criteria.md):**
- AC: При импорте валидного ZIP-пака через SAF → `getPacks()` возвращает пак с ожидаемым числом lessons/cards (Given/When/Then).
- AC: При mid-transaction сбое (имитация) → `withTransaction` rollback, дельта сущностей в БД = 0 (SM-2).
- AC: Manifest v1 и v2 оба парсятся без ошибок; invalid-manifest → отказ до транзакции (SM-4).

---

## UC-2. Импорт одиночного lesson (CSV)

**Code:** UC-2
**Связан с US:** US-2 (PRD §3)
**Actor:** Конечный юзер (через E13) ИЛИ разработчик (для тестирования формата урока).

**Precondition:**
- Существует целевой pack (либо новый, либо существующий — куда добавляется урок).
- Файл урока (CSV/MD) доступен через SAF-URI.

**Основной поток:**
1. Юзер/разработчик инициирует `importLessonFromUri(uri, resolver, packId?)`.
2. PackImporter определяет тип файла и выбирает парсер (`CsvParser` для предложений, и т. д.).
3. Внутри `withTransaction` парсер разбирает строки файла; каждой карточке присваивается `card_<lineNumber>` ID (legacy-контракт PRD §4.1).
4. Карточки пишутся в `ContentDao` (cards SENTENCE и др.), связываются с указанным pack/lesson.
5. Если packId не указан — создаётся/переиспользуется pack-контейнер (правило в SRS-002).
6. Транзакция commit'ится, возвращается lesson-id.

**Альтернативные потоки:**
- **A1 — Файл пуст / неверный формат:** отказ до транзакции, БД не тронута.
- **A2 — Ошибка разбора конкретной строки:** см. UC-3 (partial-import), если включён confirm-режим; либо atomic rollback, если strict-режим.
- **A3 — Целевой packId не существует:** ошибка «target pack not found», отказ.

**Postcondition:**
- В Room появился урок с привязанными карточками в рамках атомарной транзакции.
- Home grid (US-3) может отобразить урок после ре-запроса `getLessons()`.

**Business rules:**
- **BR-4 (card ID convention):** ID карточек = `card_<lineNumber>` (наследует legacy `CsvParser`, PRD §4.1).
- **BR-5 (lesson container):** урок обязан принадлежать pack'у — либо явно указанному, либо созданному/переиспользованному по правилу SRS-002.

**covers:** [FR TBD — ожидаемые: lesson-import-from-uri, single-lesson-atomic-write]
**AC preview:**
- AC: Импорт валидного CSV-урока в существующий пак → `getLesson(packId, lessonId)` возвращает карточки с ID вида `card_<N>`.
- AC: Попытка импорта в несуществующий packId → ошибка, БД без изменений.

---

## UC-3. Partial-import confirmation (часть файлов повреждена)

**Code:** UC-3
**Связан с US:** US-2 (PRD §3, §4.7)
**Actor:** Конечный юзер (подтверждает); PackImporter (предлагает).

**Precondition:**
- Идёт импорт pack (UC-1) или lesson (UC-2).
- Внутри транзакции часть сущностей распарсилась успешно, часть упала (битый CSV-файл, некорректная разметка story и т. п.).
- Конфигурация импорта допускает partial-режим (не strict-rollback).

**Основной поток:**
1. PackImporter собирает результат: список успешно импортированных сущностей + список сбойных файлов/карт с причиной ошибки.
2. Транзакция **не commit'ится автоматически**; управление передаётся в confirm-флоу.
3. Юзеру (через E13 UI) показывается диалог: «Импортировано N карточек, M файлов с ошибками: <список>. Сохранить корректные?»
4. Юзер подтверждает → транзакция commit'ится только с успешно распарсенными сущностями (сбойные отбрасываются).
5. Результат импорта возвращает partial-статус + детали.

**Альтернативные потоки:**
- **A1 — Юзер отклоняет:** rollback всей транзакции, БД не тронута, возврат «import cancelled».
- **A2 — strict-режим (без partial):** любая ошибка = atomic rollback (UC-1 A2); confirm-диалог не показывается.

**Postcondition:**
- Либо (confirmed) в БД только валидные сущности пака, ни одного «полу-сломанного» pack (SM-3).
- Либо (rejected) БД без изменений.

**Business rules:**
- **BR-6 (partial invariant):** после partial-import в БД нет pack'а с недостающими обязательными частями — либо pack целостен (с урезанным контентом), либо полностью отсутствует (SM-3).
- **BR-7 (confirm ownership):** confirm-диалог рендерится в UI (E13), но решение (commit vs rollback) принимается на data-уровне — UI только передаёт вердикт.

**covers:** [FR TBD — ожидаемые: partial-import-confirm, partial-import-rollback-on-reject]
**AC preview:**
- AC: При импорте пака, где 2 из 10 карточек битые → диалог подтверждения показывает «8 успешно, 2 ошибки»; подтверждение → в БД 8 карточек, 0 битых (SM-3).
- AC: Отказ в confirm-диалоге → дельта сущностей = 0.

---

## UC-4. Delete pack / Reset+reimport / Create empty lesson

**Code:** UC-4
**Связан с US:** US-2 (PRD §4.7)
**Actor:** Конечный юзер (через E13 Settings UI).

**Precondition:**
- В Room-БД существует хотя бы один pack (для delete/reset).
- Для `createEmptyLesson` — существует целевой pack (или создаётся новый).

**Основной поток (три под-операции):**

**4a. `deletePack(packId)`:**
1. Юзер инициирует удаление пака из Settings.
2. PackImporter/Repository вызывает `deletePack(packId)`.
3. Срабатывает FK CASCADE (уже настроено в Room-схеме, PRD §4.7) — удаляются pack, его lessons, cards, drills, stories, прогресс.
4. Возвращается результат удаления.

**4b. `resetAndReimport`:**
1. Юзер инициирует «reset & reimport» (force reload default packs).
2. Все default-packs удаляются (BR-8 — какие именно паки «default»).
3. Заново импортируются default packs из assets (через UC-1 флоу, но из `assets/`).
4. Возвращается результат ре-импорта.

**4c. `createEmptyLesson(packId?)`:**
1. Юзер/разработчик создаёт пустой урок (для редактирования/тестов).
2. PackImporter создаёт lesson-сущность без карточек, привязывает к pack'у (или новому).
3. Возвращается lesson-id.

**Альтернативные потоки:**
- **A1 — `deletePack` для несуществующего packId:** no-op или ошибка (правило в SRS-002).
- **A2 — `resetAndReimport` при пустых assets:** ошибка «no default packs», текущее состояние сохраняется.
- **A3 — `createEmptyLesson` без packId:** создаётся временный pack-контейнер (BR-5).

**Postcondition:**
- (4a) Pack и все его зависимые сущности удалены; Home grid обновляется (US-3).
- (4b) Default packs приведены к каноническому состоянию из assets.
- (4c) В БД появился пустой урок, готовый к наполнению.

**Business rules:**
- **BR-8 (default packs):** список default-pack'ов = канонический набор из assets (наследует legacy `LanguageManager.defaultPacks`, PRD §4.5).
- **BR-9 (CASCADE integrity):** удаление pack'а ≠ «мягкое» удаление; FK CASCADE убирает все зависимые rows (PRD §4.7).

**covers:** [FR TBD — ожидаемые: delete-pack-cascade, reset-and-reimport, create-empty-lesson]
**AC preview:**
- AC: `deletePack(packId)` → `getPacks()` больше не содержит pack; связанные lessons/cards/drills отсутствуют (CASCADE).
- AC: `resetAndReimport` при актуальных assets → число default-packs соответствует каноническому набору.
- AC: `createEmptyLesson(packId)` → `getLesson(...)` возвращает урок с 0 карточек.

---

## UC-5. Backup прогресса (Room → SAF)

**Code:** UC-5
**Связан с US:** US-4 (PRD §3, §4.3)
**Actor:** Конечный юзер (через E13 Settings UI).

**Precondition:**
- У юзера есть прогресс в Room-БД (хотя бы одна завершённая сессия / SRS-карточка / streak).
- SAF tree-URI предоставлен (системный диалог выбора папки) ИЛИ используется внутреннее хранилище.

**Основной поток:**
1. Юзер инициирует «Backup» в Settings.
2. BackupManager делает snapshot пользовательского состояния из единой Room-БД `grammarmate_v2.db` (формат фиксируется в SRS-002, OQ-1 — вероятно DB file с WAL checkpoint).
3. Backup записывается в SAF-папку (или internal storage) с именем, включающим timestamp.
4. Backup metadata (timestamp, размер, schemaVersion) сохраняется рядом (legacy `writeBackupMetadata`).
5. Возвращается результат backup с путём/URI.

**Альтернативные потоки:**
- **A1 — SAF-URI недоступен / нет прав:** ошибка, backup отменён.
- **A2 — БД пуста (нет прогресса):** backup создаётся, но с пометкой «empty state» (или отказ — правило в SRS-002).
- **A3 — Ошибка записи (диск заполнен):** откат, partial-backup не сохраняется.

**Postcondition:**
- В SAF/internal появился файл backup'а + metadata.
- `hasBackup()` возвращает true; `getAvailableBackups()` включает новый snapshot.
- Состояние Room-БД не изменилось (backup = read-only операция над source).

**Business rules:**
- **BR-10 (backup completeness):** backup обязан покрывать все пользовательские таблицы Room (29 entities, разделённые по user-state) — иначе restore потеряет данные (PRD R4, SM-6).
- **BR-11 (backup immutability):** backup-операция не мутирует source-БД (only-for-read на источнике).

**covers:** [FR TBD — ожидаемые: create-backup-to-saf, create-backup-internal, backup-metadata, list-backups]
**AC preview:**
- AC: `createBackup()` в SAF-папку → в папке появляется backup-файл + metadata; `hasBackup()` == true.
- AC: Backup-файл содержит снимок всех пользовательских таблиц (проверяется через restore round-trip, SM-6).

---

## UC-6. Restore при переустановке (SAF → Room, restore-gate на MainActivity)

**Code:** UC-6
**Связан с US:** US-5 (PRD §3, §4.3)
**Actor:** Система (restore-gate на `MainActivity.onCreate`) + Конечный юзер (подтверждает restore, если интерактивно).

**Precondition:**
- Приложение установлено/переустановлено; Room-БД создана, но «свежая» (нет прогресса).
- В SAF/internal существует хотя бы один backup (или несколько).
- `MainActivity.onCreate` — restore-точка (legacy-паттерн; сам MainActivity в E04+, но **контракт restore-вызова** фиксируется здесь — PRD §4.3, §5.3).

**Основной поток:**
1. На `MainActivity.onCreate` restore-gate проверяет: есть ли backup И БД свежая (признак первой установки/переустановки).
2. Если условие выполняется — `getAvailableBackups()` возвращает список.
3. Выбирается последний backup (по timestamp) ИЛИ юзеру предлагается выбрать (если интерактивно — UI в E13/E04+).
4. BackupManager вызывает `restoreFromBackupUri(uri)` (или `restoreFromBackup(path)`).
5. Содержимое backup'а загружается в Room-БД (механика — DB file swap или entity-by-entity — SRS-002, OQ-1).
6. После restore БД содержит идентичное backup'у пользовательское состояние (SM-6).
7. Restore-gate помечается как выполненный (чтобы не повторять на каждом запуске).

**Альтернативные потоки:**
- **A1 — Backup не найден:** restore-gate пропускается, запуск продолжается со свежей БД (UC-7 seed-заполнение).
- **A2 — Backup повреждён / несовместимая schemaVersion:** ошибка, предложение выбрать другой backup или пропустить.
- **A3 — Несколько backup'ов:** выбор последнего по timestamp (default) или интерактивный выбор юзером.

**Postcondition:**
- Восстановлено пользовательское состояние: прогресс, SRS, streak, drill, pomodoro, настройки, флаги миграций (SM-6).
- Diff между backup и текущим состоянием БД = пусто (round-trip идентичность).
- Юзер может продолжить тренировки с того же места (US-5 цель).

**Business rules:**
- **BR-12 (restore-gate trigger):** restore предлагается/выполняется только при наличии backup И свежей БД (legacy-паттерн, PRD §4.3, SM-7).
- **BR-13 (round-trip identity):** backup → wipe → restore даёт **идентичное** состояние; diff = ∅ (PRD SM-6, R4).
- **BR-14 (restore-gate idempotence):** restore-gate не повторяется на каждом запуске — только при выполнении триггера (BR-12).

**covers:** [FR TBD — ожидаемые: restore-from-backup-uri, restore-from-backup-path, get-available-backups, restore-gate-on-launch]
**AC preview:**
- AC: backup → wipe БД → `restoreFromBackupUri(uri)` → diff по всем пользовательским таблицам = ∅ (SM-6).
- AC: На `MainActivity.onCreate` при наличии backup И свежей БД restore-gate срабатывает (SM-7).
- AC: При отсутствии backup'а restore-gate пропускается без ошибок.

---

## UC-7. Multi-language seed (6 языков)

**Code:** UC-7
**Связан с US:** US-6 (PRD §3, §4.5)
**Actor:** Система (`ensureSeedData`-эквивалент в data-слое).

**Precondition:**
- Приложение установлено; Room-БД создана.
- Seed-конфигурация определена (6 языков: en, it, de, zh, ru, el).

**Основной поток:**
1. На старте (или при первом обращении) `ensureSeedData`-эквивалент проверяет наличие seed-языков в БД.
2. Если языков нет / неполный набор — добавляются недостающие из канонического seed (формализация `ContentRepositoryImpl.LANGUAGES_SEED`, PRD §4.5).
3. `cleanupStalePacks`-эквивалент: удаляются паки, более не входящие в `defaultPacks` (если конфигурация assets изменилась).
4. `getLanguages()` возвращает ровно 6 языков.

**Альтернативные потоки:**
- **A1 — Языки уже есть и корректны:** no-op, `getLanguages()` возвращает 6.
- **A2 — В БД посторонние языки (не из seed):** политика — оставить или очистить (правило в SRS-002; BR-15 по умолчанию = cleanup только stale packs, не языки).
- **A3 — defaultPacks изменился:** stale-packs удаляются (BR-16).

**Postcondition:**
- `getLanguages()` возвращает ровно 6 языков: en/it/de/zh/ru/el (SM-8).
- Паки, не входящие в defaultPacks, удалены (cleanup-stale-packs, SM-8).
- Seed-логика полностью в data-слое (`:app:v2/core/data`), НЕ в UI (PRD §4.5).

**Business rules:**
- **BR-15 (language set stability):** 6 языков (en/it/de/zh/ru/el) — стабильный продуктовый набор (PRD A3); новые языки добавляются расширением seed, не UI в E02.
- **BR-16 (stale-packs cleanup):** при смене defaultPacks паки, более не входящие в набор, удаляются (наследует legacy `LanguageManager.cleanupStalePacks`, PRD §4.5).

**covers:** [FR TBD — ожидаемые: ensure-seed-data, get-languages-six, cleanup-stale-packs]
**AC preview:**
- AC: `ensureSeedData()` на свежей БД → `getLanguages()` возвращает ровно 6 языков {en, it, de, zh, ru, el} (SM-8).
- AC: После смены defaultPacks `cleanupStalePacks` удаляет паки, не входящие в новый набор.

---

## UC-8. @Deprecate YamlToRoomMigrator (greenfield Q3)

**Code:** UC-8
**Связан с US:** — (нет прямого US; продуктовое решение Q3, PRD §4.6, §5.6)
**Actor:** Разработчик (читает предупреждение компилятора); Система (ни один активный путь не вызывает migrator).

**Precondition:**
- `YamlToRoomMigrator` существует в кодовой базе v2 (legacy-переходный мигратор).
- Спонсорское решение Q3 (greenfield data) принято: миграция пользовательских данных legacy → v2 не выполняется (PRD §5.6).
- Согласовано с E01 FR-13 (SRS-001).

**Основной поток:**
1. `YamlToRoomMigrator` помечается аннотацией `@Deprecated` с сообщением и `ReplaceWith`/level (PRD §4.6).
2. Проверяется: ни один активный кодовый путь в v2 не вызывает migrator (SM-5).
3. Компиляция `:app` остаётся зелёной.
4. Если обнаружен активный вызов — он удаляется/заменяется (мигратор перестаёт быть путём данных).

**Альтернативные потоки:**
- **A1 — Активный вызов migrator найден:** удаляется или заменяется на fresh-import путь (UC-1/UC-2); компиляция снова зелёная.
- **A2 — `@Suppress` требуется:** разрешён только в самом файле migrator'а (PRD R6, SM-5).

**Postcondition:**
- `YamlToRoomMigrator` помечен `@Deprecated` (SM-5).
- `grep` активных вызовов migrator в v2 = 0 (SM-5).
- Компиляция `:app` зелёная.
- Физическое удаление — отдельная follow-up задача после стабилизации Wave 1 (PRD OQ-3, вне scope E02).

**Business rules:**
- **BR-17 (migrator inactive):** ни один активный кодовый путь v2 не вызывает migrator (SM-5). Migrator остаётся как dead-code с `@Deprecated` для сохранения компиляции переходных сборок.
- **BR-18 (deprecation visibility):** `@Suppress("DEPRECATION")` разрешён только внутри файла migrator'а, не во внешних вызовах (PRD R6).

**covers:** [FR TBD — ожидаемые: deprecate-yaml-to-room-migrator, no-active-migrator-calls, E01 FR-13 contract]
**AC preview:**
- AC: `YamlToRoomMigrator` имеет аннотацию `@Deprecated`; `grep -r "YamlToRoomMigrator" app/src/main` вне самого файла = 0 совпадений (SM-5).
- AC: `./gradlew :app:compileDebugKotlin` зелёный.

---

## UC-9. Home grid отображает импортированный контент (downstream E13)

**Code:** UC-9
**Связан с US:** US-3 (PRD §3)
**Actor:** Конечный юзер (через Home UI в E04+/E13) — потребитель `ContentRepository`.

> **Важно:** E02 НЕ реализует Home UI (PRD §5.3). Этот UC фиксирует
> **downstream-контракт**: после PackImport (UC-1/UC-2/UC-7) read-side
> `ContentRepositoryImpl` обязан вернуть непустые данные, которые E04+/E13
> отрендерят. UC-9 = поведенческий мост «данные готовы к потреблению».

**Precondition:**
- В Room-БД есть импортированные паки (через UC-1/UC-2) ИЛИ seed (через UC-7).
- `ContentRepositoryImpl.getPacks()` / `getLanguages()` / `getLessons()` реализованы (E01 read-side, accepted).

**Основной поток:**
1. Downstream-эпик (E04+ Home или E13) вызывает `ContentRepository.getPacks()` / `getLanguages()`.
2. `ContentRepositoryImpl` читает из Room (через `ContentDao`).
3. Возвращается непустой список паков/языков с метаданными.
4. Downstream отображает сетку (UI — вне scope E02).

**Альтернативные потоки:**
- **A1 — БД пуста (ничего не импортировано, seed не выполнен):** возвращается пустой список; downstream показывает empty-state (UI — E04+).
- **A2 — Ошибка чтения Room:** исключение пробрасывается наверх; downstream обрабатывает (UI — E04+).

**Postcondition:**
- После PackImport `getPacks()` возвращает непустой список (SM-9, US-3).
- После UC-7 `getLanguages()` возвращает ровно 6 языков (SM-8).
- Read-side `ContentRepositoryImpl` не мутируется в E02 (PRD A1) — только обеспечивается его непустость через import-flow.

**Business rules:**
- **BR-19 (read-side immutability):** E02 не переписывает `ContentRepositoryImpl` (read-side); только добавляет write-операции контента (PRD A1).
- **BR-20 (data readiness):** после PackImport `getPacks()` ≠ ∅ — это сигнальный инвариант разблокировки Wave 1 (PRD §2, SM-9).

**covers:** [FR TBD — ожидаемые: content-repository-read-nonempty-after-import, E01 FR-6.1 contract]
**AC preview:**
- AC: После UC-1 (импорт тестового пака) `ContentRepositoryImpl.getPacks()` возвращает список, содержащий импортированный packId (SM-9, SM-1).
- AC: После UC-7 `getLanguages()` возвращает 6 языков (SM-8).

---

## Traceability matrix (UC ↔ US ↔ FR)

| UC | US (PRD §3) | FR (SRS-002, TBD) | SM (PRD §6) |
|---|---|---|---|
| **UC-1** Импорт pack из ZIP | US-1 | PackImport-via-URI/Assets, manifest v1/v2, atomic-pack-transaction | SM-1, SM-2, SM-4, SM-9 |
| **UC-2** Импорт одиночного lesson | US-2 | lesson-import-from-uri, single-lesson-atomic-write | SM-1 |
| **UC-3** Partial-import confirmation | US-2 | partial-import-confirm, partial-import-rollback-on-reject | SM-3 |
| **UC-4** Delete/Reset+reimport/Create empty | US-2 | delete-pack-cascade, reset-and-reimport, create-empty-lesson | SM-1 |
| **UC-5** Backup прогресса | US-4 | create-backup-to-saf/internal, backup-metadata, list-backups | SM-6 |
| **UC-6** Restore при переустановке | US-5 | restore-from-backup-uri/path, get-available-backups, restore-gate | SM-6, SM-7 |
| **UC-7** Multi-language seed (6) | US-6 | ensure-seed-data, get-languages-six, cleanup-stale-packs | SM-8 |
| **UC-8** @Deprecate YamlToRoomMigrator | — (решение Q3) | deprecate-migrator, no-active-calls (E01 FR-13) | SM-5 |
| **UC-9** Home grid потребляет контент | US-3 | content-repository-read-nonempty (E01 FR-6.1) | SM-8, SM-9 |

> FR-коды будут присвоены в SRS-002 (saga-architect). После этого каждый UC
> получит `trace_add(link_type:'covers')` от UC к FR. На момент написания UC
> (E02 Formalization-UC) SRS-002 ещё не создан — поэтому в `covers` указано
> «FR TBD», а artifact-trace проложен `UC derived_from PRD-002` (на уровне
> артефактов). AC-preview в каждом UC — заготовка для `03-acceptance-criteria.md`.

---

## Зависимости UC от эпиков

| UC | Зависит от | Разблокирует |
|---|---|---|
| UC-1, UC-2, UC-3, UC-4 | E01 (read-side готов), legacy PackImporter (референс) | E04 Training, E05 Verb, E06 Vocab, E07 Daily/Story, E13 Home |
| UC-5, UC-6 | E01, legacy BackupManager (референс) | Доверие юзера (US-4/US-5); restore-gate на MainActivity (E04+) |
| UC-7 | E01, legacy LanguageManager (референс) | E13 (переключение языков), все тренировочные эпики |
| UC-8 | E01 FR-13 (migrator contract) | Чистота кодовой базы v2; follow-up delete-migrator после Wave 1 |
| UC-9 | UC-1/UC-2/UC-7 (наполненные таблицы) | E04+ Home grid (downstream consumer) |

---

*UC-002 фиксирует поведенческие контракты E02 для downstream-эпиков и
конечного юзера. Все ссылки на доменные порты идут через SRS-001 §5 (E01,
accepted); технические детали (формат backup'а, Room-схема, механика
restore) — в SRS-002 (saga-architect). AC-preview — заготовка для
`03-acceptance-criteria.md`, который saga-analyst напишет после SRS-002.*
