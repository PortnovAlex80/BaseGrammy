# AC-002 — Acceptance Criteria: Слой данных Room + PackImport + Backup

**Эпизод:** E02 (epic_id=86), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (AC — мост к development kanban)
**Дата:** 2026-07-07
**Status:** draft

**Артефакт-источник:** PRD-002 (artifact_id=475), UC-002 (artifact_id=477), SRS-002 (artifact_id=499).
**Upstream контракт:** SRS-001 / E01 (artifact_id=450) — 9 доменных портов стабильны.
**Repository:** BaseGrammy (repository_id=7), ветка `feature/req-wave-0-formalization`.
**Код:** AC-002

> E02 — инфраструктурный эпизод. Большинство AC верифицируются **автоматизированно**:
> Room instrumentation tests (`Room.inMemoryDatabaseBuilder`, `MigrationTestHelper`),
> unit-тесты парсеров (pure Kotlin, JUnit), backup round-trip test (Room DB file +
> `PRAGMA wal_checkpoint(FULL)`), import integration test, и grep/compile-gate для
> `@Deprecated`-мigrator'а. Каждый AC сформулирован в форме **Given/When/Then** с
> наблюдаемым исходом и конкретной проверкой.

---

## 0. Покрытие (coverage gate)

- **13 FR** (FR-1..FR-13, artifact_id 500..507, 519..523) — каждый покрыт ≥1 AC.
- **8 NFR** (NFR-1..NFR-8, artifact_id 524..531) — каждый покрыт ≥1 AC.
- **9 UC** (UC-1..UC-9, artifact_id 478..486) — каждый UC имеет ≥1 `derived_from` AC.
- **Coverage-gate** `legacy-coverage-checklist.md` §D (stores: LessonStore,
  LanguageManager, DrillFileManager, ProgressStore, PackLessonProgressStore fresh,
  YamlListStore subsumed by Room) и §G (BackupManager + Restorer) — покрыты AC ниже.

Полная матрица трассировки — §3.

---

## 1. Acceptance Criteria (AC-1..AC-30)

### AC-1. PackImporter — импорт ZIP-пака через SAF (happy path)

**Code:** AC-1
**derived_from:** UC-1 (artifact 478)
**covers:** FR-1 (500)

**Given:** В Room-БД `grammarmate_v2.db` нет пака с `packId="italian_1"`; в `assets/` (или
SAF-URI) доступен валидный ZIP-пак `italian.zip` с `manifest.json` (schema v1), 3 уроками
в CSV и `verb_drill` секцией.

**When:** Вызывается `PackImporter.importPackFromAssets("italian.zip")` (или
`importPackFromUri(uri, resolver)` для SAF-варианта).

**Then:**
- Возвращается `PackImportResult.Success(pack)`.
- `ContentDao.getPacks()` содержит ровно 1 пак с `id="italian_1"`.
- `ContentDao.getLessons("italian_1")` возвращает 3 урока, отсортированных по `order`.
- `DrillDao.getVerbDrillCardsForVerbTense(...)` возвращает карты verb-drill.

**Verification:** Room instrumentation test (`Room.inMemoryDatabaseBuilder`,
`androidx.test`), `importPackFromAssets` вызывает `Dispatchers.IO`, assertions через DAO
post-import. Команда: `./gradlew :app:connectedDebugAndroidTest --tests *.PackImporterTest`.

---

### AC-2. PackImporter — zip-extraction path-traversal защита

**Code:** AC-2
**derived_from:** UC-1 (artifact 478)
**covers:** FR-1 (500)

**Given:** ZIP-файл содержит恶意 entry `../../../etc/passwd` (или `..\\secret.txt`).

**When:** Вызывается `importPackFromUri(...)`.

**Then:**
- Entry с path-traversal **отбрасывается** (canonical-target не внутри temp-parent).
- Импорт либо продолжает с валидными entries (Partial), либо отказ (Failed) — но **ни один
  файл вне temp-каталога не записан**.
- Temp-каталог очищается в `finally`.

**Verification:** Unit test с заранее собранным malicious-zip fixture; post-condition:
temp-dir пуст, вне-temp файлов нет.

---

### AC-3. ★ Atomic import rollback (withTransaction) — КРИТИЧНЫЙ

**Code:** AC-3
**derived_from:** UC-1 (artifact 478)
**covers:** FR-7 (506), NFR-1 (524)

**Given:** Pack с 10 валидными CSV-уроками, но 5-й файл содержит повреждённый CSV
(например, строка с 1 колонкой вместо 2). Конфигурация импорта — **strict-режим**
(rollback-on-error).

**When:** Вызывается `importPackFromAssets("broken_pack.zip")` и `CsvParser` падает на
`MalformedLine` внутри `database.withTransaction { ... }` после успешного парсинга уроков
1..4.

**Then:**
- `withTransaction` выполняет **rollback**.
- `ContentDao.getPacks()` возвращает **0** паков с этим `packId`.
- `ContentDao.getCards(...)` для всех уроков пака возвращает 0.
- `DrillDao.insertVerbDrillCards` / `insertVocabWords` — ни одной новой записи.
- Возвращается `PackImportResult.Failed(errors)` с указанием сбойного файла.

**Verification:** Room instrumentation test, in-memory DB. В body `withTransaction` после 4
успешных `replaceLessonCards` принудительный `throw RuntimeException("simulated")`.
Assertions: `contentDao.getPacks().size == 0`, `getCards("lesson_1").isEmpty()`. Метрика
NFR-1 = 0 новых строк после rollback.

---

### AC-4. Manifest schema v1 — парсинг

**Code:** AC-4
**derived_from:** UC-1 (artifact 478)
**covers:** FR-2 (501), NFR-6 (529)

**Given:** JSON-манифест с `"schemaVersion": 1`, одним standard-lesson (`type="standard"`,
`order=1`, `file="lesson1.csv"`) и опциональным `verbDrill: { files: [...] }`.

**When:** Вызывается `LessonPackManifest.fromJson(text)`.

**Then:**
- Возвращается `LessonPackManifest(schemaVersion=1, lessons=[...], verbDrill=...)`.
- `lessons` отсортированы по `order`; `lessonEntries.distinctBy { lessonId }` — без дублей.
- Никаких исключений не выбрасывается.

**Verification:** Unit-test `LessonPackManifestTest` (pure Kotlin, JUnit). Фикстуры:
`manifest_v1_standard.json`, `manifest_v1_verb_drill.json`. Тест перенесён из legacy
(`LessonPackManifestTest`).

---

### AC-5. Manifest schema v2 — парсинг с chapters

**Code:** AC-5
**derived_from:** UC-1 (artifact 478)
**covers:** FR-2 (501), NFR-6 (529)

**Given:** JSON-манифест с `"schemaVersion": 2`, одной главой
`{ chapterId:"ch1", order:1, title:"Глава 1", lessons:["l1","l2"] }`.

**When:** Вызывается `LessonPackManifest.fromJson(text)`.

**Then:**
- Возвращается `LessonPackManifest(schemaVersion=2, chapters=[Chapter(...)])`.
- Главы отсортированы по `order`; уроки внутри — по manifest-порядку.
- `lessonEntries` канонически собраны из chapters.

**Verification:** Unit-test с фикстурой `manifest_v2_chapters.json`. Regression-lock из
legacy.

---

### AC-6. Manifest validation — отказ для невалидного

**Code:** AC-6
**derived_from:** UC-1 (artifact 478)
**covers:** FR-2 (501)

**Given:**
- (a) Манифест с `schemaVersion: 3` (не 1 и не 2).
- (b) v1-манифест без lessons/drills/backgroundVocab.
- (c) v2-манифест с пустыми chapters и без drill-секций.
- (d) Манифест с `packId=""` (blank).

**When:** Вызывается `LessonPackManifest.fromJson(text)` для каждого случая.

**Then:**
- (a) `error("Unsupported schemaVersion")`.
- (b) `error("Schema v1 manifest has no lessons, no drill sections, and no backgroundVocab")`.
- (c) `error("Schema v2 manifest has no chapter content, no drill sections, and no backgroundVocab")`.
- (d) `error` (blank packId/language).
- БД не тронута (валидация до `withTransaction`).

**Verification:** Unit-test, по одному кейсу на каждое `(a)..(d)`.

---

### AC-7. CsvParser — Lesson CSV → CardEntity (card_<lineNumber>)

**Code:** AC-7
**derived_from:** UC-2 (artifact 479)
**covers:** FR-3 (502), NFR-6 (529)

**Given:** CSV-файл урока (заголовок в строке 1, далее data-строки). Каждая строка: 2
колонки `RU | answers`, ответы разделены `+`.

**When:** Вызывается `CsvParser.parseLesson(inputStream)`.

**Then:**
- Строка 1 → `title` (через `extractTitle`, `.trimStart('\uFEFF')`, `.take(160)`).
- Data-строки → `List<SentenceCard>` с `id="card_<lineNumber>"`.
- `card_1` **не существует** (lineNumber после заголовка; реальные ID начинаются с `card_2`).
- Строка с ≠2 колонками → `ParseError.MalformedLine(lineNumber, expected="2 columns", actual=...)`.
- `consecutiveEmptyLines > 3` → `ParseError.MalformedLine`.
- При маппинге в `CardEntity`: `type = CardType.SENTENCE.name`,
  `acceptedAnswersJson` — JSON-массив, `ord = index`, `packId`/`lessonId` из контекста.

**Verification:** Unit-test `CsvParserTest` (pure Kotlin), перенесён 1:1 из legacy.
Assertions не изменены (regression-lock NFR-6).

---

### AC-8. VerbDrillCsvParser — Verb CSV → VerbDrillCardEntity

**Code:** AC-8
**derived_from:** UC-1 (artifact 478)
**covers:** FR-4 (503), NFR-6 (529)

**Given:** Verb CSV: строка 1 = title, строка 2 = header с колонками `ru,it` (обяз.) +
опц. `verb,tense,group,person,rank`. Data-rows вида `я устал (essere stanco)\tIo sono stanco`.

**When:** Вызывается `VerbDrillCsvParser.parse(content)`.

**Then:**
- `verb` fallback: если колонка `verb` пуста и `ru.contains("(")`, извлекается через
  `Regex("\\(([\\w]+)")` → `"essere"`.
- `person` извлекается из первого слова answer (`PERSON_ORDER`: Io/Tu/Lui/Lei/Noi/Voi/Loro).
- ID карты = `"${group ?: ""}_${tense ?: ""}_$dataRowIndex"` — совпадает с
  `VerbDrillCardEntity.id`.
- При импорте: `DrillDao.insertVerbDrillCards(entities)` (bulk REPLACE) идемпотентен.

**Verification:** Unit-test `VerbDrillCsvParserTest`, перенесён 1:1 из legacy. Полный набор
утверждений сохранён.

---

### AC-9. VocabCsvParser — Vocab CSV → VocabWordEntity

**Code:** AC-9
**derived_from:** UC-1 (artifact 478)
**covers:** FR-6 (505), NFR-6 (529)

**Given:** Файл `vocab_lessonA.csv`: строки формата `native,target[,hard]` (минимум 2
колонки; 3-я `"hard"|"1"|"true"` case-insensitive).

**When:** При импорте пака вызывается `VocabCsvParser.parse(inputStream)`.

**Then:**
- Каждая строка → `VocabRow(native, target, isHard)`.
- `isHard=true`, если 3-я колонка `"hard"|"1"|"true"` (case-insensitive).
- `VocabWordEntity.id = "${pos}_${rank}_${word}"`.
- `lessonId = file.nameWithoutExtension.removePrefix("vocab_")` → `"lessonA"`.
- `DrillDao.insertVocabWords(entities)` идемпотентен; unique `(packId, word)` — повторный
  импорт того же слова не создаёт дубль.

**Verification:** Unit-test `VocabCsvParserTest`, regression-lock из legacy.

---

### AC-10. StoryQuizParser + MultilingualStoryParser — Story → entities

**Code:** AC-10
**derived_from:** UC-1 (artifact 478)
**covers:** FR-5 (504), NFR-6 (529)

**Given:** Story JSON-файл (`storyId, lessonId, phase=CHECK_IN|CHECK_OUT, text,
questions: List<StoryQuestion]`) с разметкой `{it}…{/it}`, `{ru}`, `{pause:500}`.

**When:** При импорте пака каждый `.json` (кроме `manifest.json`) парсится
`StoryQuizParser.parse(text)`, story-текст дополнительно прогоняется через доменный
`MultilingualStoryParser.parseSegments(content, defaultLanguageId)`.

**Then:**
- `StoryQuiz` содержит `questions: List<StoryQuestion>`; каждый — `qId, prompt, options,
  correctIndex, explain?`.
- `correctIndex in options.indices` — иначе `ParseError.InvalidFormat`.
- `MultilingualStoryParser.parseSegments(...)` возвращает `List<Segment>`:
  `Segment.Text` / `Segment.Pause(ms)` (для `{pause:N}`).
- Дедупликация по `(storyId, lessonId, phase)`.

**Verification:** Unit-test `StoryQuizParserTest` + доменный `MultilingualStoryParserPauseTest`
(в `:domain:test`, E01). Оба перенесены из legacy и зелёные.

---

### AC-11. BgVocabCsvParser — header contract (15 columns, RFC-4180)

**Code:** AC-11
**derived_from:** UC-1 (artifact 478)
**covers:** FR-7 (506), NFR-6 (529)

**Given:** BgVocab CSV с обязательным header'ом **ровно 15 колонок**:
`rank, word, ru, collo_it, collo_ru, s1_it, s1_ru, …, s5_it, s5_ru`. RFC-4180 quoted fields
(`""` → `"`, comma inside quotes).

**When:** Вызывается `BgVocabCsvParser.parse(input)`.

**Then:**
- Корректный header → `List<WordScript>` с 3..5 `PhrasePair(it, ru)` каждый (первый пустой
  `s{n}_it` заканчивает список).
- Несоответствие header (≠15 колонок) → `IllegalArgumentException` (файл считается
  повреждённым; partial-import или rollback по конфигурации).
- Data-row с `<5` полей или пустым `word` → ошибка.
- `rank` парсится как `Int`.

**Verification:** Unit-test `BgVocabCsvParserTest`, regression-lock из legacy. Fixture с
RFC-4180 quoted-comma case.

---

### AC-12. importLessonFromUri — одиночный CSV-урок

**Code:** AC-12
**derived_from:** UC-2 (artifact 479)
**covers:** FR-1 (500)

**Given:** Существующий пак `packId="italian_1"`; CSV-URI одиночного урока.

**When:** Вызывается
`PackImporter.importLessonFromUri(languageId="it", uri=uri, resolver=resolver)`.

**Then:**
- `CsvParser.parseLesson` разбирает CSV; карты пишутся через
  `ContentDao.replaceLessonCards(lessonId, cards)` (атомарно: delete + insert в одной
  `@Transaction`).
- `ContentDao.getCards(lessonId)` возвращает карты с `id="card_<N>"`.
- Если `packId` целевого не существует → отказ до транзакции, БД не тронута.

**Verification:** Room instrumentation test (in-memory). Fixture: `single_lesson.csv`,
manifest v1 reference.

---

### AC-13. ★ Partial-import confirmation dialog — КРИТИЧНЫЙ

**Code:** AC-13
**derived_from:** UC-3 (artifact 480)
**covers:** FR-8 (507)

**Given:** Pack из 10 CSV-файлов, из которых 3 повреждены (например, `lesson_3.csv`,
`lesson_7.csv`, `vocab_extra.csv` содержат malformed строки); конфигурация импорта —
partial-режим (не strict-rollback).

**When:** Вызывается `importPackFromAssets(...)` и парсеры возвращают `ParseResult.partial`.

**Then:**
- `PackImporter` возвращает `PackImportResult.Partial(pack, errors)` с **ровно 3**
  `ParseError` (по одному на сбойный файл, с `fileName` в `WithFileContext`).
- UI (E13) показывает confirm-диалог со списком из 3 файлов и кнопками
  «continue anyway» / «cancel».
- При «continue anyway»: в БД сохраняются 7 корректных файлов; 3 сбойных отброшены.
- При «cancel»: rollback всей транзакции, дельта сущностей = 0.

**Verification:** Unit-test на `PackImporter` (in-memory DB): partial-result содержит ровно
3 ошибки; post-confirm (true) → `getCards` возвращает карты только из 7 валидных файлов.
Data-layer возвращает sealed-result; UI confirm — отдельный AC в E13.

---

### AC-14. deletePack — FK CASCADE

**Code:** AC-14
**derived_from:** UC-4 (artifact 481)
**covers:** FR-8 (507)

**Given:** В Room-БД существует пак `packId="italian_1"` с lessons, cards, drills, stories,
progress (mastery_states, drill_progress, chapter_progress).

**When:** Вызывается `ContentDao.deletePack("italian_1")` (FK CASCADE в схеме:
`ChapterEntity`/`LessonEntity` → `PackEntity`; cards через `deleteCardsForPack`).

**Then:**
- `ContentDao.getPacks()` больше не содержит `"italian_1"`.
- `ContentDao.getLessons("italian_1")` → `[]`.
- `ContentDao.getCards(...)` для всех уроков пака → `[]`.
- Pack-scoped drill-данные (`verb_drill_cards`, `aux_drill_cards`, `vocab_words`,
  `verb_drill_combo_progress`) для этого `packId` удалены.
- `deletePack` для несуществующего packId — no-op (без ошибки).

**Verification:** Room instrumentation test (in-memory), `MigrationTestHelper` для проверки
CASCADE-constraints.

---

### AC-15. resetAndReimport — force reload default packs

**Code:** AC-15
**derived_from:** UC-4 (artifact 481)
**covers:** FR-8 (507)

**Given:** В БД есть default packs (например, `italian_1`, `german_1`); assets содержат
актуальные версии этих паков.

**When:** Вызывается `resetAndReimport(defaultPacks)`.

**Then:**
- Для каждого default-pack: `deletePack(packId)` + `importPackFromAssets(assetPath)`.
- После завершения: число default-packs = `defaultPacks.size`.
- Каждый пак приведён к каноническому состоянию из assets (карты, lessons, drills совпадают
  с asset-source).
- Если assets пусты (нет default-packs) → ошибка, текущее состояние сохраняется.

**Verification:** Integration test (in-memory + assets fixture).

---

### AC-16. createEmptyLesson — пустой урок с UUID

**Code:** AC-16
**derived_from:** UC-4 (artifact 481)
**covers:** FR-8 (507)

**Given:** Существующий пак `packId="italian_1"` (или отсутствие packId — тогда создаётся
временный контейнер).

**When:** Вызывается `createEmptyLesson(languageId="it", title="My Lesson")`.

**Then:**
- Возвращается `LessonId` (UUID).
- `ContentDao.getLessons("italian_1")` включает новый урок с `title="My Lesson"`.
- `ContentDao.getCards(newLessonId)` → `[]` (0 карт).
- `LessonEntity.id` = `UUID.randomUUID().toString()`.

**Verification:** Room instrumentation test; assertion на пустой `getCards`.

---

### AC-17. lesson_progress fresh — per-pack completion flags persist

**Code:** AC-17
**derived_from:** UC-4 (artifact 481), UC-9 (artifact 486)
**covers:** FR-11 (521)

**Given:** Пак `packId="italian_1"` с уроком `lessonId="l1"`. После завершения под-урока
`MasteryRepositoryImpl.markLessonCompleted("italian_1", "l1", nowMs)` вызывается
`MasteryDao.markCompleted` (UPDATE … WHERE `completedAtMs IS NULL`). БЕЗ новой таблицы —
используются `MasteryStateEntity`/`ShownCardEntity` (SM-10).

**When:** Перезапуск приложения / повторный запрос `MasteryDao.get("italian_1", "l1")`.

**Then:**
- `MasteryStateEntity.completedAtMs` ≠ NULL для `(italian_1, l1)` — флаг сохранён.
- `dueCards(now, limit)` возвращает просроченные уроки (`dueAtMs <= now AND completedAtMs IS NULL`).
- Legacy-файл `lesson_progress_<packId>.yaml` **не мигрируется** (FR-13/Q3 greenfield);
  v2 вычисляет эквивалент из mastery-entities.
- Схема не расширяется (нет `lesson_progress` таблицы) — NFR-2 (SM-10).

**Verification:** Room instrumentation test. Setup: insert `MasteryStateEntity` без
`completedAtMs`; call `markCompleted`; reopen DB; verify persisted. Idempotence:
повторный `markCompleted` не меняет `completedAtMs`.

---

### AC-18. ★ LanguageSeed.ensureSeedData — 6 языков, идемпотентность — КРИТИЧНЫЙ

**Code:** AC-18
**derived_from:** UC-7 (artifact 484)
**covers:** FR-12 (522)

**Given:** Свежая Room-БД (нет ни одного языка в seed-config). `LANGUAGES_SEED` = 6 языков
{en→English, it→Italian, de→German, zh→Chinese, ru→Russian, el→Greek}.

**When:** Вызывается `LanguageSeed.ensureSeedData()`.

**Then:**
- `ContentRepositoryImpl.getLanguages()` возвращает **ровно 6** языков с кодами
  {en, it, de, zh, ru, el} и displayName {English, Italian, German, Chinese, Russian, Greek}.
- Повторный вызов `ensureSeedData()` — no-op, `getLanguages()` снова 6 (идемпотентность).
- Дополняются только недостающие языки (если в БД уже 3 — добавятся оставшиеся 3).

**Verification:** Unit/integration test на `LanguageSeed` (in-memory DB). Setup: пустая БД;
assert 6; повторный вызов; assert 6.

---

### AC-19. cleanupStalePacks — удаление pack'ов вне defaultPacks

**Code:** AC-19
**derived_from:** UC-7 (artifact 484)
**covers:** FR-12 (522)

**Given:** В БД есть паки `italian_1`, `german_1`, `old_french_1` (более не в
defaultPacks). `defaultPacks = [italian_1, german_1]`. `activeLanguages = {it, en, de, zh,
ru, el}`.

**When:** Вызывается `LanguageSeed.cleanupStalePacks(defaultPacks)`.

**Then:**
- `old_french_1` удалён (`ContentDao.deletePack("old_french_1")`).
- `italian_1`, `german_1` остались.
- `ContentDao.getPacks()` содержит только default-packs.

**Verification:** Integration test (in-memory DB), фикстура с stale-pack.

---

### AC-20. Backup — Room snapshot → SAF (WAL checkpoint)

**Code:** AC-20
**derived_from:** UC-5 (artifact 482)
**covers:** FR-9 (519), NFR-3 (526)

**Given:** В Room-БД есть прогресс: ≥1 `SessionEntity`, mastery-состояния, streak, drill
combo, pomodoro, settings (DataStore). SAF tree-URI предоставлен (с персистентным URI
permission).

**When:** Вызывается `BackupManager.createBackup()`.

**Then:**
- `database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")` сбрасывает WAL
  в основной `.db`.
- В SAF-папке `backup_latest/` появляется `grammarmate_v2.db` (копия) + `metadata.txt`
  (`timestamp`, `schemaVersion=1`, размер, `DATABASE_NAME`).
- `hasBackup()` возвращает `true`.
- `getAvailableBackups()` включает новый snapshot (последний = canonical).
- Состояние source-БД **не изменилось** (backup = read-only операция над источником, BR-11).

**Verification:** Instrumentation test на эмуляторе с SAF (Robolectric или
`androidx.test`). Pre/post assertions: счётчики во всех 29 таблицах source-БД неизменны.

---

### AC-21. ★ Backup round-trip integrity — КРИТИЧНЫЙ

**Code:** AC-21
**derived_from:** UC-6 (artifact 483)
**covers:** FR-10 (520), NFR-3 (526)

**Given:** Состояние Room-БД с импортированным pack `P`, прогрессом mastery, streak,
pomodoro-history, settings (DataStore file), `MigrationFlagEntity`. Все 29 таблиц
непустые (или реалистично наполнены).

**When:** Последовательность: `createBackup()` → SAF → wipe DB (delete
`grammarmate_v2.db`, recreate) → `restoreFromBackupUri(uri)`.

**Then:**
- Восстановленное состояние **идентично** оригинальному.
- `diff` по всем 29 таблицам через DAO (или `sqlite3 .dump` hash comparison) = пусто.
- Hash содержимого `.db` после round-trip совпадает с hash исходного (или поэлементно
  эквивалентен).
- Покрытие: progress (`streaks`, `chapter_progress`, `drill_progress`, `daily_cursors`),
  SRS (`mastery_states`, `word_mastery`, `shown_cards`, `card_encounters`), drill
  (`verb_drill_combo_progress`, `aux_drill_combo_progress`, `verb_drill_last_session`),
  `pomodoro_history`, settings (DataStore file), `migratable_files` (флаги миграций).

**Verification:** Backup round-trip test (Room DB file + `wal_checkpoint(FULL)`).
`MessageDigest` SHA-256 по `sqlite3 .dump` before/after. Метрика NFR-3 = diff пуст.

---

### AC-22. Restore gate — restore-точка на свежей БД

**Code:** AC-22
**derived_from:** UC-6 (artifact 483)
**covers:** FR-10 (520)

**Given:** Приложение установлено/переустановлено; Room-БД свежая
(`ContentDao.getPacks().isEmpty()` или `MigrationFlagEntity` для seed-default-packs
отсутствует); в SAF/internal существует ≥1 backup.

**When:** На `MainActivity.onCreate` restore-gate проверяет условие: `hasBackup() &&
database.isFresh()`.

**Then:**
- Условие истинно → restore-gate срабатывает (контракт E02: `hasBackup()` +
  `getAvailableBackups()` + `restoreFromBackup*` доступны; UI вызывает их в E04+).
- После restore БД содержит состояние из backup (AC-21 round-trip).
- Restore-gate **не повторяется** на каждом запуске (BR-14): только при свежей БД.
- При отсутствии backup → restore-gate пропускается без ошибок (UC-6 A1).

**Verification:** E02 — data-контракт проверяется unit-тестом (`hasBackup()` = true при
наличии `backup_latest/`). Wire-up на `MainActivity.onCreate` — AC в E04+ (app-shell).

---

### AC-23. SAF permissions handling — revoked URI → false (не падает)

**Code:** AC-23
**derived_from:** UC-5 (artifact 482), UC-6 (artifact 483)
**covers:** NFR-4 (527), FR-9 (519)

**Given:**
- (a) SAF tree-URI permission отозван (`takePersistableUriPermission` не вызывался или
  permission отозван системой).
- (b) Restore из невалидного URI (файл удалён, нет `grammarmate_v2.db` внутри).
- (c) `getAvailableBackups(treeUri)` — фильтрация `backup_*` директорий, сортировка
  `lastModified` desc.

**When:** Вызываются `createBackup()` / `restoreFromBackupUri(invalidUri)` /
`getAvailableBackups(treeUri)`.

**Then:**
- (a) `createBackup()` возвращает `false` (не падает); `BackupManager` логирует
  диагностическое сообщение.
- (b) `restoreFromBackupUri(...)` возвращает `false` (валидация metadata + presence
  `grammarmate_v2.db`); БД source не тронута.
- (c) `getAvailableBackups(treeUri)` возвращает только `backup_*`-папки, отсортированные по
  `lastModified` desc; `hasBackup()` = `getAvailableBackups().isNotEmpty()`.

**Verification:** Instrumentation test с mocked `DocumentFile`/`ContentResolver`.
Permission-revoked сценарий — `assertFalse(backupManager.createBackup())`.

---

### AC-24. Idempotent re-import — повторный импорт того же packId = update, not duplicate

**Code:** AC-24
**derived_from:** UC-1 (artifact 478)
**covers:** NFR-5 (528), FR-1 (500)

**Given:** Pack `italian_1` уже импортирован (3 урока, 10 карт в каждом, verb-drill).

**When:** Вызывается `importPackFromAssets("italian.zip")` **второй раз** (тот же `packId`).

**Then:**
- `ContentDao.getPacks()` содержит **ровно 1** пак с `id="italian_1"` (не 2).
- `ContentDao.getLessons("italian_1")` — без дублей уроков (3, не 6).
- `ContentDao.getCards(lessonId)` — без дублей карт (`replaceLessonCards` атомарно заменяет
  пул: delete + insert в одной `@Transaction`).
- `DrillDao.insertVerbDrillCards(list)` / `insertVocabWords(list)` — bulk REPLACE-upsert,
  идемпотентно.
- `VocabWordEntity` unique index `(packId, word)`; `verb_drill_combo_progress` unique index
  `(packId, group, tense)` — дедупликация на уровне схемы.

**Verification:** Integration test (in-memory). Двойной `importPackFromAssets`;
assertion `getPacks().size == 1` для этого packId; count cards/lessons стабилен.

---

### AC-25. ★ YamlToRoomMigrator @Deprecated — 0 активных вызовов — КРИТИЧНЫЙ

**Code:** AC-25
**derived_from:** UC-8 (artifact 485)
**covers:** FR-13 (523)

**Given:** Класс `YamlToRoomMigrator` в `app/.../v2/core/data/migration/`. Спонсорское
решение Q3 (greenfield data) принято — миграция legacy→v2 не выполняется.

**When:** Запускается статический анализ:
- `grep -rn "@Deprecated" app/src/main/java/com/alexpo/grammermate/v2/core/data/migration/YamlToRoomMigrator.kt`
- `grep -rn "YamlToRoomMigrator\\|migrateIfNeeded" app/src/main/java/com/alexpo/grammermate/v2/ \
   --exclude="*/migration/YamlToRoomMigrator.kt"`
- `./gradlew :app:compileDebugKotlin`

**Then:**
- `@Deprecated("Q3 greenfield data — migrator больше не активный путь данных; используйте PackImporter для свежего импорта", level = DeprecationLevel.WARNING)`
  присутствует на классе (и/или на `migrateIfNeeded()`).
- `grep` активных вызовов migrator в v2 (вне `migration/YamlToRoomMigrator.kt`) = **0**
  совпадений.
- `@Suppress("DEPRECATION")` разрешён **только** в файле migrator'а.
- `:app:compileDebugKotlin` зелёный (warning, не error).
- `SettingsRepository.isMigrationDone("migrate_yaml_to_room_v1")` остаётся в контракте порта
  (E01 FR-6.7) — флаг персистируется, но новый код migrator не вызывает.

**Verification:** Shell-grep assertions в CI-скрипте + `compileDebugKotlin` exit code = 0.
Метрика SM-5 = `@Deprecated` present + 0 active calls + green compile.

---

### AC-26. Parser regression lock — 5 парсеров, legacy-тесты перенесены

**Code:** AC-26
**derived_from:** UC-1 (artifact 478), UC-2 (artifact 479)
**covers:** NFR-6 (529), FR-3 (502)

**Given:** Legacy-тесты из v1: `CsvParserTest`, `CsvParserLazyLoadingTest`,
`VerbDrillCsvParserTest`, `VocabCsvParserTest`, `BgVocabCsvParserTest`,
`MultilingualStoryParserPauseTest`, `StoryQuizParserTest`.

**When:** Тесты переносятся в `app/src/test/.../v2/core/data/packimport/` **без изменения
утверждений**, запускается `./gradlew :app:test`.

**Then:**
- Все перенесённые тесты **зелёные**.
- Число `@Test`-методов в packimport-тестах ≥ legacy-эталона (new tests allowed, none
  removed).
- `ParseResult<T, ParseError>` (sealed success/partial/failure) сохраняет семантику:
  partial возвращает и данные, и ошибки.

**Verification:** CI — `./gradlew :app:test`; счётчик `@Test` в
`app/src/test/.../packimport/` ≥ legacy-эталона.

---

### AC-27. Schema stability — 29 entities, version=1, exportSchema=true

**Code:** AC-27
**derived_from:** UC-1 (artifact 478)
**covers:** NFR-2 (525), FR-7 (506)

**Given:** Room-схема на старте Wave 1: `GrammarMateDatabase(version=1, exportSchema=true)`,
29 entities в 6 файлах, 6 DAOs.

**When:** После завершения E02 проверяется:
- `@Database(version = 1, exportSchema = true)` — неизменно.
- `git diff --stat app/src/main/java/com/alexpo/grammermate/v2/core/data/local/entity/` =
  пусто (схема не расширена тихо).
- `app/schemas/.../GrammarMateDatabase/1.json` — присутствует (exported schema).
- `fallbackToDestructiveMigration` **НЕ** используется.

**Then:**
- Version = 1 (без явной миграции — не растёт).
- При обнаружении gap (legacy-поле, не вошедшее в схему — напр. story-text,
  bg-vocab word-script) → зарегистрирована **явная drift-задача** с `addMigrations` +
  version bump, трассируемая к SRS-002 §7 R3 (не тихое расширение).

**Verification:** `git diff` gate в CI; `MigrationTestHelper` при изменении версии.

---

### AC-28. PackImport performance — bulk-insert ≤ 2000 cards < 3 s

**Code:** AC-28
**derived_from:** UC-1 (artifact 478)
**covers:** NFR-7 (530), FR-1 (500)

**Given:** Pack большого размера (порядка 2000 карт + verb/vocab drills + stories).

**When:** Вызывается `importPackFromAssets(...)` (на mid-range эмуляторе, `Dispatchers.IO`).

**Then:**
- Bulk-insert через `ContentDao.insertCards(List<CardEntity>)` /
  `DrillDao.insertVerbDrillCards(List<...>)` — один `@Insert(onConflict=REPLACE)` на батч
  (не построчно).
- Все методы `PackImporter` — `suspend` (не блокируют UI thread).
- Время импорта ≤ 3 секунд (порог; цель — не блокировать UI на mid-range эмуляторе).

**Verification:** Instrumentation test с benchmark-pack (2000 cards), измерение
`System.nanoTime()` до/после; `assertThat(elapsedMs < 3000)`.

---

### AC-29. Port contract stability — 9 портов :domain не изменены

**Code:** AC-29
**derived_from:** UC-9 (artifact 486)
**covers:** NFR-8 (531), FR-1 (500)

**Given:** 9 доменных портов из SRS-001 §5 (E01, accepted). Data-слой (`*Impl`) реализует
их. PackImport добавляет write-операции контента как private-методы `ContentRepositoryImpl`,
использующие `ContentDao`.

**When:** После старта Wave 1 проверяется `git diff` на `:domain/repository/*` и
`:domain/audio/*`.

**Then:**
- `git diff` = пусто (вне drift-задач).
- Публичный интерфейс `ContentRepository` (read-only порт, SRS-001 §5.1) — неизменён.
- Если для write-side требуется новая доменная операция → drift-задача с
  `trace_add(derived_from, SRS-001)`.
- `ContentRepositoryImpl.getPacks()` (read-side) **не переписывается** в E02 (PRD A1).

**Verification:** CI-gate `git diff --exit-code` на `domain/repository/` (вне drift-branch).

---

### AC-30. ★ Home grid отображает импортированный pack — КРИТИЧНЫЙ

**Code:** AC-30
**derived_from:** UC-9 (artifact 486)
**covers:** FR-1 (500)

**Given:** Pack `P` с `packId="italian_1"`, `manifest v2`, импортирован через
`importPackFromAssets` (AC-1). Read-side `ContentRepositoryImpl.getPacks()` реализован (E01).

**When:** Downstream-эпик (E04+/E13) открывает Home и вызывает
`ContentRepository.getPacks()` (через `ContentDao.getPacks()`).

**Then:**
- `ContentDao.getPacks()` возвращает список, содержащий packId `"italian_1"` (непустой,
  SM-9).
- `ContentRepositoryImpl.getLanguages()` возвращает 6 языков (UC-7 seed выполнен).
- Home grid (UI в E04+/E13) способен отрендерить пак P — сигнальный инвариант разблокировки
  Wave 1 (BR-20).

**Verification:** Room instrumentation test (in-memory): `importPackFromAssets(...)` →
`contentDao.getPacks().map { it.id }.contains("italian_1")`. UI rendering — AC в E04+/E13.

---

## 2. Verification plan (summary)

| Тип проверки | Покрывает AC | Инструмент |
|---|---|---|
| **Room instrumentation tests** (in-memory DB) | AC-1, AC-2, AC-3, AC-7..AC-19, AC-21, AC-24, AC-28, AC-30 | `Room.inMemoryDatabaseBuilder`, `MigrationTestHelper`, `androidx.test` |
| **Unit tests** (pure Kotlin, JUnit) | AC-4, AC-5, AC-6, AC-7..AC-11, AC-18, AC-26 | `./gradlew :app:test` |
| **Backup round-trip test** | AC-20, AC-21, AC-22 | Room DB file + `PRAGMA wal_checkpoint(FULL)`, SHA-256 `.dump` |
| **Import integration test** | AC-1, AC-12, AC-13, AC-15, AC-24 | `Dispatchers.IO` + asset-fixture |
| **SAF permissions test** (mocked DocumentFile) | AC-23 | `BackupManager` unit-test с mock `ContentResolver` |
| **Compile/grep gate** (CI) | AC-25, AC-27, AC-29 | `grep -rn`, `./gradlew :app:compileDebugKotlin`, `git diff --exit-code` |

---

## 3. Traceability matrix (AC ↔ UC ↔ FR/NFR)

| AC | derived_from UC | covers FR | covers NFR | SM (PRD) |
|---|---|---|---|---|
| **AC-1** PackImport via SAF happy path | UC-1 (478) | FR-1 (500) | — | SM-1, SM-9 |
| **AC-2** Zip path-traversal защита | UC-1 (478) | FR-1 (500) | — | — |
| **AC-3** ★ Atomic rollback | UC-1 (478) | FR-7 (506) | NFR-1 (524) | SM-2 |
| **AC-4** Manifest v1 parse | UC-1 (478) | FR-2 (501) | NFR-6 (529) | SM-4 |
| **AC-5** Manifest v2 parse | UC-1 (478) | FR-2 (501) | NFR-6 (529) | SM-4 |
| **AC-6** Manifest validation reject | UC-1 (478) | FR-2 (501) | — | SM-4 |
| **AC-7** CsvParser → CardEntity | UC-2 (479) | FR-3 (502) | NFR-6 (529) | — |
| **AC-8** VerbDrillCsvParser | UC-1 (478) | FR-4 (503) | NFR-6 (529) | — |
| **AC-9** VocabCsvParser | UC-1 (478) | FR-6 (505) | NFR-6 (529) | — |
| **AC-10** StoryQuizParser + MultilingualStoryParser | UC-1 (478) | FR-5 (504) | NFR-6 (529) | — |
| **AC-11** BgVocabCsvParser header | UC-1 (478) | FR-7 (506) | NFR-6 (529) | — |
| **AC-12** importLessonFromUri | UC-2 (479) | FR-1 (500) | — | SM-1 |
| **AC-13** ★ Partial-import confirm | UC-3 (480) | FR-8 (507) | — | SM-3 |
| **AC-14** deletePack cascade | UC-4 (481) | FR-8 (507) | — | SM-1 |
| **AC-15** resetAndReimport | UC-4 (481) | FR-8 (507) | — | — |
| **AC-16** createEmptyLesson | UC-4 (481) | FR-8 (507) | — | — |
| **AC-17** lesson_progress fresh | UC-4 (481), UC-9 (486) | FR-11 (521) | NFR-2 (525) | SM-10 |
| **AC-18** ★ LanguageSeed ensureSeedData | UC-7 (484) | FR-12 (522) | — | SM-8 |
| **AC-19** cleanupStalePacks | UC-7 (484) | FR-12 (522) | — | SM-8 |
| **AC-20** Backup → SAF (WAL) | UC-5 (482) | FR-9 (519) | NFR-3 (526) | SM-6 |
| **AC-21** ★ Backup round-trip integrity | UC-6 (483) | FR-10 (520) | NFR-3 (526) | SM-6 |
| **AC-22** Restore gate fresh DB | UC-6 (483) | FR-10 (520) | — | SM-7 |
| **AC-23** SAF permissions → false | UC-5 (482), UC-6 (483) | FR-9 (519) | NFR-4 (527) | — |
| **AC-24** Idempotent re-import | UC-1 (478) | FR-1 (500) | NFR-5 (528) | — |
| **AC-25** ★ Migrator @Deprecated | UC-8 (485) | FR-13 (523) | — | SM-5 |
| **AC-26** Parser regression lock | UC-1 (478), UC-2 (479) | FR-3 (502) | NFR-6 (529) | — |
| **AC-27** Schema stability | UC-1 (478) | FR-7 (506) | NFR-2 (525) | SM-10 |
| **AC-28** PackImport performance | UC-1 (478) | FR-1 (500) | NFR-7 (530) | — |
| **AC-29** Port contract stability | UC-9 (486) | FR-1 (500) | NFR-8 (531) | — |
| **AC-30** ★ Home grid pack displayed | UC-9 (486) | FR-1 (500) | — | SM-9, SM-1 |

### FR coverage check (13/13)

| FR | Покрыт AC |
|---|---|
| FR-1 (500) | AC-1, AC-2, AC-12, AC-24, AC-28, AC-29, AC-30 |
| FR-2 (501) | AC-4, AC-5, AC-6 |
| FR-3 (502) | AC-7, AC-26 |
| FR-4 (503) | AC-8 |
| FR-5 (504) | AC-10 |
| FR-6 (505) | AC-9 |
| FR-7 (506) | AC-3, AC-11, AC-27 |
| FR-8 (507) | AC-13, AC-14, AC-15, AC-16 |
| FR-9 (519) | AC-20, AC-23 |
| FR-10 (520) | AC-21, AC-22 |
| FR-11 (521) | AC-17 |
| FR-12 (522) | AC-18, AC-19 |
| FR-13 (523) | AC-25 |

### NFR coverage check (8/8)

| NFR | Покрыт AC |
|---|---|
| NFR-1 (524) | AC-3 |
| NFR-2 (525) | AC-17, AC-27 |
| NFR-3 (526) | AC-20, AC-21 |
| NFR-4 (527) | AC-23 |
| NFR-5 (528) | AC-24 |
| NFR-6 (529) | AC-4, AC-5, AC-7, AC-8, AC-9, AC-10, AC-11, AC-26 |
| NFR-7 (530) | AC-28 |
| NFR-8 (531) | AC-29 |

### UC coverage check (9/9)

| UC | derived_from AC |
|---|---|
| UC-1 (478) | AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-8, AC-9, AC-10, AC-11, AC-24, AC-26, AC-27, AC-28 |
| UC-2 (479) | AC-7, AC-12, AC-26 |
| UC-3 (480) | AC-13 |
| UC-4 (481) | AC-14, AC-15, AC-16, AC-17 |
| UC-5 (482) | AC-20, AC-23 |
| UC-6 (483) | AC-21, AC-22, AC-23 |
| UC-7 (484) | AC-18, AC-19 |
| UC-8 (485) | AC-25 |
| UC-9 (486) | AC-17, AC-29, AC-30 |

### Coverage-gate legacy-coverage-checklist.md

- **§D (stores):** LessonStore + LanguageManager + DrillFileManager (E02) → AC-1, AC-18,
  AC-19; ProgressStore (E02/E10) → AC-17; **PackLessonProgressStore** (GAP C7, fresh) →
  AC-17 (fresh через `MasteryStateEntity`/`ShownCardEntity` без новой таблицы); YamlListStore
  (subsumed by Room migration) → AC-25 (migrator @Deprecated), AC-27 (schema stability).
- **§G (backup):** BackupManager + FileCollector + Restorer + RestoreNotifier (E02 data) →
  AC-20 (create), AC-21 (round-trip), AC-22 (restore-gate), AC-23 (SAF permissions).

---

*AC-002 — мост к development kanban для REQ-002. Каждый AC — наблюдаемая, верифицируемая
спецификация поведения; `Given/When/Then` + конкретная проверка (room instrumentation /
unit / round-trip / grep+compile). 30 AC покрывают все 13 FR и 8 NFR, происходят от 9 UC
(derived_from) и покрывают FR/NFR (covers). Критичные AC (★): atomic rollback (AC-3),
partial-import confirm (AC-13), LanguageSeed 6 langs idempotent (AC-18), backup round-trip
integrity (AC-21), migrator @Deprecated + 0 calls (AC-25), Home grid pack displayed
(AC-30). После принятия AC-002 saga-planner создаёт dev-tasks, каждый с `implements`-trace
к AC.*
