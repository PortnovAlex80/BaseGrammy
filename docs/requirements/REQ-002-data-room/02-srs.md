# SRS-002 — Слой данных Room: PackImport + Backup + Language seed (без мигратора)

**Эпизод:** E02 (epic_id=86), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (SRS)
**Дата:** 2026-07-07
**Status:** Accepted

**Артефакт-источник:** PRD-002 (artifact_id=475, `01-prd.md`, status=accepted).
**Upstream контракт:** SRS-001 / E01 (artifact_id=450, `REQ-001-domain-core/02-srs.md`) — 9 доменных портов зафиксированы как стабильный контракт (§5 SRS-001).
**Repository:** BaseGrammy (repository_id=7), ветка `rewrite/v2-clean-architecture`. Integration branch = `dev`.
**Код:** SRS-002

> Источник истины сигнатур — **фактический код** `app/.../v2/core/data/*` и
> `app/legacy-src/.../data/*` (verified 2026-07-07). PRD фиксирует продуктовые границы
> E02; SRS фиксирует технический системный контракт data-слоя: Room-схему (29 entities,
> 6 DAO), PackImport-поток (5 парсеров → Room), Backup/Restore контракт, Language seed.
> Все сигнатуры ниже — реальные сигнатуры из кода, перенесённые как есть
> (regression-locked). Доменные порты **не дублируются** — см. SRS-001 §5.

---

## 1. Архитектурный обзор data layer

### 1.1. Положение `v2/core/data` в общей архитектуре

`GrammarMate v2` построен по чистой слоистой архитектуре (`docs/v2-architecture/ARCHITECTURE.md`).
Data-слой (`:app:v2/core/data`) — это **адаптер к Room**, реализующий доменные порты из
`:domain` (E01, SRS-001 §5):

```
┌─────────────────────────────────────────────────────┐
│  UI (app:ui)  — Compose M3 Adaptive экраны           │
├─────────────────────────────────────────────────────┤
│  Presentation MVI  — Intent → Reducer → State        │
├─────────────────────────────────────────────────────┤
│  Domain (:domain — pure Kotlin) — 9 портов (E01)     │  ← upstream контракт
├─────────────────────────────────────────────────────┤
│  Data (app:v2/core/data) — ЭТА SRS                    │
│   ├─ local/   — Room: GrammarMateDatabase + DAOs     │
│   ├─ repository/ — *Impl (Room → domain models)      │
│   ├─ packimport/ — NEW: 5 парсеров → Room (FR-1..7)  │
│   ├─ backup/  — NEW: Room snapshot → SAF (FR-9..10)  │
│   ├─ language/ — NEW: ensureSeedData-эквивалент (FR-12)│
│   └─ migration/ — YamlToRoomMigrator @Deprecated      │
├─────────────────────────────────────────────────────┤
│  Platform — Room/SQLite (WAL) | FileSystem | SAF      │
└─────────────────────────────────────────────────────┘
```

### 1.2. Архитектурный фикс бага `card_15` (наследие E01, поддерживается data-слоем)

Единая Room-БД `grammarmate_v2.db` — это и есть архитектурный фикс бага v1: вместо 24
независимых YAML-файлов под 24 `ReentrantLock` все операции пользовательского состояния
выполняются в **одной `@Transaction`** (либо все изменения применены, либо ни одного).
WAL-режим (`JournalMode.WRITE_AHEAD_LOGGING`) включён в `GrammarMateDatabase.build` —
писатель не блокирует читателей.

### 1.3. Принцип data-слоя: read-сторона уже есть, write-сторона контента — это E02

К началу E02 в data-слое уже присутствуют:
- **Room-схема** — `GrammarMateDatabase` (version=1, `exportSchema=true`, WAL),
  29 entities в 6 файлах (`ContentEntities.kt`, `DrillEntities.kt`, `MasteryEntities.kt`,
  `ProgressEntities.kt`, `SessionEntities.kt`, `UserContentEntities.kt`) и 6 DAO
  (`ContentDao`, `DrillDao`, `MasteryDao`, `ProgressDao`, `SessionDao`, `UserContentDao`).
- **7 read-имплементаций портов** (`ContentRepositoryImpl`, `SessionRepositoryImpl`,
  `MasteryRepositoryImpl`, `ProgressRepositoryImpl`, `UserContentRepositoryImpl`,
  `VocabDrillRepositoryImpl`, `SettingsRepositoryImpl`) — это **read-сторона**.

**Что отсутствует и составляет ядро E02:**
- **Write-сторона для контента:** `ContentRepositoryImpl.getLanguages()` возвращает
  захардкоженный seed, `getPacks()`/`getLesson()` читают таблицы — но **ничто эти таблицы
  не наполняет**. Сетка Home пуста. E02 добавляет PackImport-поток (FR-1..FR-7),
  наполняющий `packs`/`chapters`/`lessons`/`cards`/`vocab_words`/`verb_drill_cards`/
  `aux_drill_cards`/story-контент.
- **Backup/Restore:** снимка Room-БД в SAF нет (FR-8..FR-10).
- **lesson_progress fresh:** `PackLessonProgress`-аналога в Room нет (FR-11).
- **Language seed как data-модуль:** seed захардкожен в `ContentRepositoryImpl.LANGUAGES_SEED`
  — нет `ensureSeedData`/`cleanupStalePacks`-эквивалента (FR-12).
- **`YamlToRoomMigrator` не помечен `@Deprecated`** (FR-13).

### 1.4. Правила слоёв (зафиксированы SRS-001 §1.2, подтверждены здесь)

- Data-слой реализует интерфейсы портов из `:domain`; **менять сигнатуры портов запрещено**
  после старта Wave 1 (SRS-001 NFR-4 = drift).
- Room-entities — детали реализации data-слоя; домен о них не знает. Маппинг
  entity ↔ domain выполняется в `*Impl`.
- PackImport-парсеры переносятся из legacy как pure-функции (или object), подключаются к
  Room через DAO в одной `@Transaction`. Доменный pure-парсер `MultilingualStoryParser`
  уже зафиксирован в E01 FR-12 — data-слой только вызывает его.
- `YamlToRoomMigrator` остаётся в data-слое (`migration/`), помечается `@Deprecated`
  (FR-13); в `:domain` не переносится (это data-деталь).

---

## 2. Структура data layer (целевая после E02)

### 2.1. Каталоги `app/.../v2/core/data/` после E02

```
v2/core/data/
├── local/                                 (СУЩЕСТВУЕТ, version=1)
│   ├── GrammarMateDatabase.kt             (29 entities, WAL, exportSchema=true)
│   ├── Converters.kt                      (List/Set/Map<String,Int> ↔ JSON)
│   ├── dao/
│   │   ├── ContentDao.kt                  (packs/chapters/lessons/cards)
│   │   ├── SessionDao.kt                  (★ card_15 fix: saveSnapshot @Transaction)
│   │   ├── MasteryDao.kt                  (mastery/shown/encounters + SRS dueCards)
│   │   ├── ProgressDao.kt                 (streak/chapter/drill/daily)
│   │   ├── DrillDao.kt                    (vocab/verb/aux/combo/boss)
│   │   └── UserContentDao.kt              (hidden/bad/bg-vocab/pomodoro/flags)
│   └── entity/
│       ├── ContentEntities.kt             (4: Pack/Chapter/Lesson/Card)
│       ├── DrillEntities.kt               (8: VocabWord/WordMastery/VerbDrill/AuxDrill/VerbCombo/AuxCombo/VerbLastSession/BossReward)
│       ├── MasteryEntities.kt             (3: MasteryState/ShownCard/CardEncounter)
│       ├── ProgressEntities.kt            (5: Streak/StreakPracticeToday/DrillProgress/ChapterProgress/DailyCursor)
│       ├── SessionEntities.kt             (3: Session/SessionCard/SessionShownCard)
│       └── UserContentEntities.kt         (6: HiddenCard/BadSentence/BgVocabMark/BgVocabPosition/PomodoroHistory/MigrationFlag)
├── repository/                            (СУЩЕСТВУЕТ, read-сторона; E02 добавляет write-контент)
│   ├── ContentRepositoryImpl.kt           (read; getLanguages=seed)
│   ├── SessionRepositoryImpl.kt           (★ saveSnapshot/loadSession atomic)
│   ├── MasteryRepositoryImpl.kt
│   ├── ProgressRepositoryImpl.kt
│   ├── UserContentRepositoryImpl.kt
│   ├── VocabDrillRepositoryImpl.kt
│   └── SettingsRepositoryImpl.kt          (DataStore; isMigrationDone)
├── packimport/                            (NEW — FR-1..FR-7)
│   ├── PackImporter.kt                    (importPackFromUri/Assets, importLessonFromUri)
│   ├── LessonPackManifest.kt              (schema v1/v2; fromJson + validation)
│   ├── CsvParser.kt                       (Lesson CSV → SentenceCard → CardEntity)
│   ├── VerbDrillCsvParser.kt              (Verb CSV → VerbDrillCard → VerbDrillCardEntity)
│   ├── VocabCsvParser.kt                  (Vocab CSV → VocabRow → VocabWordEntity)
│   ├── StoryQuizParser.kt                 (Story JSON → StoryQuiz → story entities)
│   ├── BgVocabCsvParser.kt                (BgVocab CSV → WordScript → word-script entities)
│   └── PackImportResult.kt                (sealed: Success/Partial/Failed + ParseError list)
├── backup/                                (NEW — FR-8..FR-10)
│   ├── BackupManager.kt                   (createBackup/restoreFromBackup/getAvailableBackups)
│   ├── RoomSnapshotWriter.kt              (WAL checkpoint → копия .db в SAF)
│   └── RoomSnapshotRestorer.kt            (SAF → .db, replace на остановленной БД)
├── language/                              (NEW — FR-12)
│   └── LanguageSeed.kt                    (ensureSeedData, cleanupStalePacks, 6 языков)
├── audio/                                 (E03, вне scope; не трогать)
│   ├── SherpaAudioRepository.kt
│   └── SherpaAudioModelRepository.kt
└── migration/                             (FR-13: @Deprecated)
    └── YamlToRoomMigrator.kt              (@Deprecated("Q3 greenfield data"))
```

### 2.2. Файлы, НЕ переносимые/не создаваемые в E02

- **`multilingualstory/` в data** — доменный pure-парсер `MultilingualStoryParser` уже
  зафиксирован в E01 (`:domain/story/`); data-слой только **вызывает** его
  (`MultilingualStoryParser.parseSegments(content, defaultLanguageId)`) при парсинге
  story-контента пака (FR-5). Дублировать парсер в data запрещено (SRS-001 FR-12).
- **Room-entities/DAOs** — остаются в `local/`; E02 не переписывает схему, только
  наполняет её (SM-10). При обнаружении gap → drift-задача с миграцией БД (§7 R3).
- **`SherpaAudioRepository`/`SherpaAudioModelRepository`** — E03 (audio adapter); вне scope.
- **Физическое удаление `YamlToRoomMigrator`** — follow-up после стабилизации Wave 1 (OQ-3).

---

## 3. Функциональные требования (FR)

Каждый FR индивидуально адресуем (AC будут `derived_from` по коду). Формулировки — что
система должна делать; сигнатуры — в §5 (API contract) и §6 (Room-entity contract).

### FR-1. PackImporter — точки входа (importPackFromUri / Assets / Lesson)

Data-слой содержит `PackImporter`, реализующий три legacy-точки входа, перенесённые в v2:
- `suspend fun importPackFromUri(uri: Uri, resolver: ContentResolver): PackImportResult` —
  импорт ZIP-пака через SAF (extractZipToTemp → `manifest.json` → `importPackFromStream`).
- `suspend fun importPackFromAssets(assetPath: String): PackImportResult` — импорт ZIP из
  `assets` (seed default packs при первом запуске).
- `suspend fun importLessonFromUri(languageId: String, uri: Uri, resolver: ContentResolver): PackImportResult` —
  импорт одного CSV-урока (без целого пака); возвращает `(Lesson?, List<ParseError>)`.
- `suspend fun readPackManifestFromAssets(assetPath: String): LessonPackManifest` — чтение
  манифеста без импорта (для `updateDefaultPacksIfNeeded`).

Контракт zip-extraction сохраняется: extraction в temp-каталог с защитой от path-traversal
(`canonicalTarget.startsWith(canonicalParent)`), cleanup temp в `finally`. Отличие от
legacy: вместо копирования CSV-файлов в `packsDir` + `AtomicFileWriter`, контент
**парсится и пишется в Room** через `database.withTransaction` (FR-7).

**Проверка:** SM-1 (после импорта Home отображает пак); SM-9 (`getPacks()` непустой).

### FR-2. Manifest schema v1 и v2 (LessonPackManifest)

`LessonPackManifest.fromJson(text: String): LessonPackManifest` парсит JSON-манифест пака.
Поля (переносятся 1:1 из legacy):
- `schemaVersion: Int` — **только 1 или 2**; иначе `error("Unsupported schemaVersion")`.
- `packId: String`, `packVersion: String`, `language: String` — обязательны (blank → error).
- `lessons: List<LessonPackLesson>` — для v1 (root-level); каждый `LessonPackLesson`
  содержит `lessonId, order, title?, file, type="standard"|"verb_drill", tenses, grammarChip?`.
- `displayName: String?` — опционально.
- `verbDrill: DrillFiles?`, `vocabDrill: DrillFiles?` — секции drill-файлов (`files: List<String>`).
- `backgroundVocab: BackgroundVocabSection?` — `{ file, audioDir?, defaultLanguage="it", translationLanguage="ru" }`.
- `chapters: List<Chapter>` — **только для v2**; `Chapter(chapterId, order, title, subtitle?, storyFile?, lessons: List<String>)`.

**Валидация (regression-locked):**
- v1: хотя бы один standard lesson (`type != "verb_drill"`) ИЛИ `verbDrill` ИЛИ `vocabDrill`
  ИЛИ `backgroundVocab`; иначе `error("Schema v1 manifest has no lessons, no drill sections, and no backgroundVocab")`.
- v2: хотя бы одна глава с непустым `lessons` ИЛИ drill-секции ИЛИ backgroundVocab;
  иначе `error("Schema v2 manifest has no chapter content, no drill sections, and no backgroundVocab")`.

**Lesson-order канонический:** v1 — `lessons.sortedBy { order }`; v2 — главы по `order`,
уроки внутри главы — по manifest-порядку. Дедупликация `lessonEntries.distinctBy { lessonId }`.

**Проверка:** SM-4 (оба варианта парсятся; валидация работает).

### FR-3. CsvParser — Lesson CSV → SentenceCard → CardEntity

`CsvParser.parseLesson(inputStream: InputStream): ParseResult<Pair<String, List<SentenceCard>>, ParseError>`
переносится 1:1 из legacy как `object CsvParser` (pure Kotlin, без `android.util.Log`).
Контракт:
- Строка 1 — заголовок/название урока (`extractTitle`, `.trimStart('\uFEFF')`, `.take(160)`).
- Каждая последующая строка — ровно 2 колонки (`CsvLineParser.parseLine`, semicolon-delimited):
  `RU | answers`; иначе `ParseError.MalformedLine(lineNumber, expected="2 columns", actual=...)`.
- Ответы разделяются `+`: `answersRaw.split("+").map { it.trim().trim('"') }.filter { it.isNotBlank() }`.
- ID карточки: `"card_$lineNumber"` (lineNumber после строки заголовка; **`card_1` НЕ существует** —
  реальные ID начинаются с `card_2`, см. `ContentEntities.kt` KDoc).
- `consecutiveEmptyLines > 3` → `ParseError.MalformedLine`.

Результат маппится в `CardEntity` (`type = CardType.SENTENCE.name`, `acceptedAnswersJson` =
JSON-массив через `kotlinx.serialization`, `ord = index`, `packId`/`lessonId` из контекста
импорта) и пишется через `ContentDao.replaceLessonCards(lessonId, cards)` (атомарно:
delete старых + insert новых в одной `@Transaction`).

`ParseResult` — sealed (`success`/`partial`/`failure`), частичный успех возвращает и данные,
и ошибки (SM-3 partial-import confirm).

**Проверка:** `CsvParserTest` (legacy) перенесён и зелёный.

### FR-4. VerbDrillCsvParser — Verb CSV → VerbDrillCard → VerbDrillCardEntity

`VerbDrillCsvParser.parse(content: String)` / `parse(reader: BufferedReader)` переносятся 1:1.
Контракт:
- Строка 1 — title (`extractTitle`, буквенно-цифровой префикс до 160 символов).
- Строка 2 — header с колонками `ru`, `it` (обязательны), опционально `verb`, `tense`,
  `group`, `rank`. Индексы колонок детектируются по header.
- Каждая data-row: минимум `maxOf(ruIndex, itIndex)+1` колонок; `ru`/`it` непусты.
- `verb` fallback: если `verb` колонка пуста и `ru.contains("(")`, извлекается через
  `Regex("\\(([\\w]+)")` (напр. `"я устал (essere stanco)"` → `"essere"`).
- `person`: извлекается из первого слова answer (`Io/Tu/Lui/Lei/Noi/Voi/Loro` из `PERSON_ORDER`).
- ID: `"${group ?: ""}_${tense ?: ""}_$dataRowIndex"` (совпадает с `VerbDrillCardEntity.id`).

Результат маппится в `VerbDrillCardEntity` (`packId`, `promptRu`, `answer`, `verb`, `tense`,
`group`, `person`, `rank`) и пишется через `DrillDao.insertVerbDrillCards(entities)` (bulk
REPLACE-upsert — идемпотентный реимпорт). `AuxDrillCardEntity` наполняется аналогично из
aux-drill секций.

**Проверка:** `VerbDrillCsvParserTest` (legacy) перенесён и зелёный.

### FR-5. MultilingualStoryParser + StoryQuizParser — Story → entities

Story-импорт использует **доменный** `MultilingualStoryParser` (E01 FR-12, `:domain/story/`,
pure Kotlin) + `StoryQuizParser` (data, JSON → `StoryQuiz`):
- `MultilingualStoryParser.parseSegments(content: String, defaultLanguageId: String): List<Segment>`
  разворачивает разметку `{it}…{/it}`, `{en}`, `{ru}`, `{el}`, `{de}`, `{zh}` в `Segment.Text`
  / `Segment.Pause(ms)` (маркер `{pause:N}`). `detectLanguage(text, defaultLanguageId)` —
  pure-функция (перенесена в домен в E01).
- `StoryQuizParser.parse(text: String): ParseResult<StoryQuiz, ParseError>` парсит story JSON
  (`storyId`, `lessonId`, `phase: StoryPhase.CHECK_IN|CHECK_OUT`, `text`, `questions: List<StoryQuestion>`).
  Каждый `StoryQuestion` = `qId, prompt, options: List<String>, correctIndex, explain?`.
  Валидация: `correctIndex in options.indices`, иначе `ParseError.InvalidFormat`.

При импорте пака: каждый `.json` (кроме `manifest.json`) парсится `StoryQuizParser`;
story-текст дополнительно прогоняется через доменный `MultilingualStoryParser.parseSegments`
для валидации разметки. Story-сущности персистятся через DAO (при отсутствии отдельной
`stories`-таблицы — через `ChapterEntity.storyFile` путь + `LessonEntity.grammarChipKey`
для связи; если выявится gap → drift-задача с миграцией БД, §7 R3). Дедупликация по
`(storyId, lessonId, phase)`.

**Проверка:** `MultilingualStoryParserPauseTest`, `StoryQuizParserTest` (legacy) перенесены
и зелёные; доменный парсер — `:domain:test` зелёный (E01 FR-12).

### FR-6. VocabCsvParser — Vocab CSV → VocabRow → VocabWordEntity

`VocabCsvParser.parse(inputStream: InputStream): ParseResult<List<VocabRow>, ParseError>`
переносится 1:1. Контракт:
- Каждая строка — минимум 2 колонки (`native, target`); опционально 3-я `hard`.
- `isHard` = true, если 3-я колонка `"hard"|"1"|"true"` (case-insensitive).
- ID: `"${pos}_${rank}_${word}"` (совпадает с `VocabWordEntity.id`).

Файлы с префиксом `vocab_` (напр. `vocab_lessonA.csv`) обрабатываются при импорте пака;
`lessonId = file.nameWithoutExtension.removePrefix("vocab_")`. Результат маппится в
`VocabWordEntity` (`packId`, `word`, `pos`, `rank`, `meaningRu`, `collocationsJson`,
`formsJson`) и пишется через `DrillDao.insertVocabWords(entities)` (bulk REPLACE —
идемпотентный). Дедупликация по `(packId, word)` (unique index).

**Проверка:** `VocabCsvParserTest` (legacy) перенесён и зелёный.

### FR-7. BgVocabCsvParser + Atomic import (withTransaction rollback)

`BgVocabCsvParser.parse(input: InputStream): List<WordScript>` переносится 1:1. Контракт:
- Header (обязательный, ровно 15 колонок): `rank, word, ru, collo_it, collo_ru, s1_it, s1_ru, …, s5_it, s5_ru`.
  Несоответствие header → `IllegalArgumentException` (файл считается повреждённым).
- Каждая data-row: минимум 5 полей; `rank` — Int; `word` непуст.
- Sentences: 3–5 `PhrasePair(it, ru)`, первый пустой `s{n}_it` заканчивает список.
- RFC-4180 quoted fields (comma inside quotes, `""` → `"`).

Результат маппится в word-script entities (аналог `WordScript`/`SpeakItem` домена) и
пишется в одной `@Transaction` с остальным контентом пака.

**Atomic import (КРИТИЧНО, SM-2):** весь импорт пака (manifest → lessons → drills →
stories → vocab → bg-vocab) выполняется в **одной `database.withTransaction { … }`**:
```kotlin
database.withTransaction {
    contentDao.insertPack(packEntity)
    contentDao.insertChapters(chapters)
    contentDao.insertLessons(lessons)
    lessons.forEach { (lessonId, cards) -> contentDao.replaceLessonCards(lessonId, cards) }
    drillDao.insertVerbDrillCards(verbCards)
    drillDao.insertAuxDrillCards(auxCards)
    drillDao.insertVocabWords(vocabWords)
    // story-контент, bg-vocab …
}
```
Сбой на середине → rollback, **0** новых сущностей в БД (никаких частичных паков).
Наследует гарантию legacy `AtomicFileWriter` (temp → fsync → rename), но на уровне Room.

**Проверка:** SM-2 (имитированный сбой → rollback → 0 сущностей); SM-3 (partial-import
confirm: `PackImportResult.Partial(errors)` — корректные карты сохранены, упавшие — нет).

### FR-8. Pack lifecycle: deletePack / resetAndReimport / createEmptyLesson / partial-import

Data-слой предоставляет операции жизненного цикла пака (вызываются из E13 Settings UI):
- `suspend fun deletePack(packId: String)` — `ContentDao.deletePack(packId)`; FK CASCADE
  в схеме уже настроен (`ChapterEntity`/`LessonEntity` → `PackEntity` CASCADE; cards
  удаляются через `deleteCardsForPack`). Удаляет также pack-scoped drill-данные.
- `suspend fun resetAndReimport(defaultPacks: List<DefaultPack>)` — для каждого default
  pack: `deletePack(packId)` + `importPackFromAssets(assetPath)`; используется при
  `forceReloadDefaultPacks` (legacy-паттерн).
- `suspend fun createEmptyLesson(languageId: String, title: String): LessonId` — создаёт
  пустой урок (`LessonEntity` без cards) для редактирования/тестов; ID = `UUID.randomUUID()`.
- **Partial-import confirm:** `PackImporter` возвращает `PackImportResult` (sealed):
  `Success(pack)`, `Partial(pack, errors: List<ParseError>)`, `Failed(errors)`. UI (E13)
  показывает confirm-диалог при `Partial`: корректные карты/уроки сохранены, упавшие — нет.

`ParseError` — sealed (`InvalidFormat`, `MalformedLine(lineNumber, expected, actual)`,
`EmptyFile`, `WithFileContext(fileName, cause)`); `toUserMessage()` для UI.

**Проверка:** SM-3 (partial-import confirm); AC покрывают `deletePack`/`createEmptyLesson`.

### FR-9. Backup — Room snapshot → SAF (WAL checkpoint)

Data-слой содержит `BackupManager` (интерфейс перенесён из legacy):
```kotlin
interface BackupManager {
    suspend fun createBackup(): Boolean
    suspend fun restoreFromBackup(backupPath: String): Boolean
    suspend fun restoreFromBackupUri(backupUri: Uri): Boolean
    suspend fun getAvailableBackups(treeUri: Uri): List<BackupInfo>
    suspend fun getAvailableBackups(): List<BackupInfo>
    suspend fun deleteBackup(backupPath: String): Boolean
    suspend fun hasBackup(): Boolean
}
```

**Формат backup (OQ-1, answered):** Room DB file с **WAL checkpoint**. Процедура
`createBackup()`:
1. `database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")` — сброс
   WAL в основной `.db` файл (все изменения физически в `grammarmate_v2.db`).
2. Копирование `grammarmate_v2.db` (внутреннее хранилище приложения) в SAF-папку
   (`backup_latest/grammarmate_v2.db`) или app-private `backupDir`.
3. Запись metadata: `metadata.txt` (`timestamp`, `schemaVersion=1`, размер, `DATABASE_NAME`).

Все 29 entities покрываются одним снимком БД (round-trip integrity, SM-6). Backup metadata
перезаписывает `backup_latest` при каждом `createBackup` (последний снимок = canonical).

**Проверка:** SM-6 (round-trip: backup → wipe → restore = идентичное состояние, diff пуст).

### FR-10. Restore — SAF → Room + restore-gate на MainActivity.onCreate

`restoreFromBackupUri(uri)` / `restoreFromBackup(path)`:
1. Валидация backup-контента (metadata + presence `grammarmate_v2.db`).
2. **Замена .db файла:** резервная копия БД копируется поверх `grammarmate_v2.db` при
   **остановленной** БД (`database.close()` перед заменой; reopen после). На уровне Room
   это означает: процесс должен рестартовать БД или использовать
   `openHelper.writableDatabase` путь для hot-swap (деталь реализации — SRS фиксирует
   контракт: restore даёт идентичное состояние, SM-6).
3. Лог восстановления (`restore_log.txt`) сохраняется рядом с backup (legacy-паттерн).

**Restore-gate (SM-7):** контракт restore-точки фиксируется здесь, реализация — в E04+
(app-shell). На `MainActivity.onCreate` (legacy-паттерн):
```kotlin
// E04+ contract (НЕ реализуется в E02):
// if (backupManager.hasBackup() && database.isFresh()) {
//     предложить/выполнить restore (dialog или auto)
// }
```
E02 предоставляет `hasBackup()` + `getAvailableBackups()` + `restoreFromBackup*` — UI
(app-shell) вызывает их. Restore на свежей БД (`getPacks().isEmpty()` или
`MigrationFlagEntity` отсутствует) предлагает восстановление.

**Проверка:** SM-7 (restore-точка на `MainActivity.onCreate`); SM-6 (round-trip).

### FR-11. lesson_progress — fresh в Room (PackLessonProgress-аналог)

Реализовать fresh (OQ-2, answered) хранение прогресса по парам `(packId, lessonId)` поверх
существующих `MasteryDao`/`ProgressDao` (сущности `MasteryStateEntity`, `ShownCardEntity`,
`CardEncounterEntity` уже определены в E01-коде). Legacy `PackLessonProgressStore`
(соответствующий `lesson_progress_<packId>.yaml`) **не мигрируется** (FR-13/Q3 greenfield),
но v2 вычисляет эквивалент из `MasteryStateEntity` (`uniqueCardShows`, `completedAtMs`,
`intervalStepIndex`, `dueAtMs`) и `ShownCardEntity`. Новая таблица НЕ добавляется —
используются существующие mastery-entities (SM-10: схема стабильна).

`MasteryRepositoryImpl.markLessonCompleted(packId, lessonId, nowMs)` →
`MasteryDao.markCompleted(packId, lessonId, now)` (UPDATE … WHERE `completedAtMs IS NULL`).

**Проверка:** AC покрывают `getMastery(packId, lessonId)` возвращает fresh-значение после
завершения под-урока; `dueCards(now, limit)` возвращает просроченные уроки.

### FR-12. LanguageManager.ensureSeedData-эквивалент (6 языков)

Data-слой содержит `LanguageSeed` (object/class в `language/`), формализующий существующий
`ContentRepositoryImpl.LANGUAGES_SEED` (6 языков: en/it/de/zh/ru/el). Контракт:
- `suspend fun ensureSeedData()` — гарантирует, что 6 языков присутствуют. В v2 нет
  отдельной таблицы `languages` (pack-import пишет код языка прямо в `packs.languageId`),
  поэтому seed остаётся константой (`LANGUAGES_SEED`); при появлении таблицы — `ContentDao`
  запрос. Дополняет отсутствующие языки (legacy-паттерн: `ensureLanguage(languageId)` с
  displayName `English/Italian/German/Chinese/Russian/Greek`).
- `suspend fun cleanupStalePacks(defaultPacks: List<DefaultPack>)` — удаляет паки, более
  не входящие в `defaultPacks`, для активных языков
  (`activeLanguages = setOf("it","en","de","zh","ru","el")`). Использует `ContentDao.deletePack(packId)`.
- `suspend fun seedDefaultPacksIfNeeded(importFromAssets: (String) -> Boolean): Boolean` —
  first-run seed default packs (legacy `seedMarker` → v2 `MigrationFlagEntity` с ключом
  `seed_default_packs`).
- `suspend fun updateDefaultPacksIfNeeded(...)`, `forceReloadDefaultPacks(...)` — переносятся
  из legacy `LanguageManager` (semantics 1:1).

Лежит в data-слое (`:app:v2/core/data/language/`), НЕ в UI.

**Проверка:** SM-8 (`getLanguages()` = ровно 6 языков; cleanup удаляет stale packs).

### FR-13. YamlToRoomMigrator — @Deprecated (Q3 greenfield data)

`YamlToRoomMigrator` (`migration/YamlToRoomMigrator.kt`) помечается
`@Deprecated("Q3 greenfield data — migrator больше не активный путь данных; используйте PackImporter для свежего импорта", level = DeprecationLevel.WARNING)`.
Это согласовано с SRS-001 FR-13 (E01) и спонсорским решением Q3 (greenfield data).

Контракт:
- `@Deprecated` присутствует на классе `YamlToRoomMigrator` (и/или на `migrateIfNeeded()`).
- Ни один активный кодовый путь в v2 (`v2/core/data/packimport/`, `v2/core/data/language/`,
  app-shell) не вызывает migrator. Метрика: `grep` активных вызовов `migrateIfNeeded` /
  `YamlToRoomMigrator` в v2 (вне `migration/YamlToRoomMigrator.kt`) = **0**.
- `SettingsRepository.isMigrationDone("migrate_yaml_to_room_v1")` остаётся в контракте
  порта (SRS-001 FR-6.7) — флаг персистируется, но новый код его не вызывает.
- `@Suppress("DEPRECATION")` разрешён **только** в самом файле migrator'а.
- Компиляция `:app` зелёная (предупреждение, не ошибка).
- Полное физическое удаление — follow-up после стабилизации Wave 1 (OQ-3, вне scope E02).

**Проверка:** SM-5 (`@Deprecated` присутствует; 0 активных вызовов; `:app` компилируется).

---

## 4. Нефункциональные требования (NFR)

NFR содержат измеримые метрики. Каждый NFR связан с FR и SM.

### NFR-1. Atomic integrity (withTransaction rollback)

Все write-операции PackImport выполняются внутри `database.withTransaction { … }`.
Метрика (SM-2): при имитированном сбое на середине pack-import (исключение в блоке
транзакции после N вставок) → rollback, `getPacks()` / `getCards(packId)` возвращают
**0** новых строк. Проверка: unit/integration test с in-memory Room (`Room.inMemoryDatabaseBuilder`)
и принудительным `throw` внутри `withTransaction`.

### NFR-2. Schema stability (exportSchema=true, schemaLocation)

Room-схема (29 entities, version=1) считается стабильной в продуктовом смысле (SM-10).
Метрики:
- `@Database(version = 1, exportSchema = true)` — неизменно.
- `schemaLocation` = `app/schemas/` (KAPT/room schema dir); JSON-экспорт схемы присутствует
  (`app/schemas/com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase/1.json`).
- После E02 `git diff` на `local/entity/*.kt` (без явных drift-задач) = пусто.
- Version не растёт без явной миграции (`addMigrations`); `fallbackToDestructiveMigration`
  **НЕ** используется (данные пользователя критичны, см. `GrammarMateDatabase.build` KDoc).
- При обнаружении gap (legacy-поле, не вошедшее в схему) — явная drift-задача с
  `addMigrations` + version bump, трассируемая к SRS-002 (§7 R3).

Проверка: `git diff --stat app/src/main/java/com/alexpo/grammermate/v2/core/data/local/entity/`
= пусто; AC покрывают стабльность.

### NFR-3. Backup round-trip integrity

Backup → wipe → restore даёт **идентичное** пользовательское состояние по всем 29
entities (SM-6). Метрика: после round-trip `diff` содержимого БД (dump всех таблиц через
`sqlite3 .dump` или поэлементное сравнение через DAO) = пусто. Покрытие: progress, SRS
(`mastery_states`/`word_mastery`), streak, drill (combo-progress), pomodoro, settings
(DataStore — отдельный файл, backup должен покрывать и его; см. NFR-4), флаги миграций.

Backup формат = Room DB file (WAL checkpoint, FR-9), покрывает все таблицы одним снимком.

### NFR-4. SAF permissions handling

Backup/Restore через SAF (`DocumentFile`, `ContentResolver`) корректно обрабатывает
permissions:
- `createBackup()` в SAF-папку требует персистентный URI permission
  (`contentResolver.takePersistableUriPermission(treeUri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)`).
- Restore из URI (`restoreFromBackupUri`) проверяет `DocumentFile.fromTreeUri` /
  `fromSingleUri`; при отсутствии permission возвращает `false` (не падает).
- `getAvailableBackups(treeUri)` фильтрует директории с префиксом `backup_` или
  `backup_latest`; сортировка по `lastModified` desc.
- `deleteBackup(path)` / `hasBackup()` — утилиты; `hasBackup()` = `getAvailableBackups().isNotEmpty()`.

Метрика: AC покрывают сценарий «SAF permission отозван» → `createBackup()` = false,
диагностическое сообщение; restore из невалидного URI = false.

### NFR-5. Idempotent import (re-import same pack = update, not duplicate)

Повторный импорт того же пака (`packId` совпадает) не создаёт дубликатов. Метрика:
после `importPackFromAssets("italian.zip")` дважды → `getPacks()` возвращает **ровно 1**
пак с этим `packId`; `getLessons(packId)` — без дублей уроков; `getCards(lessonId)` — без
дублей карт. Реализация:
- `ContentDao.insertPack` / `insertLessons` / `insertChapters` — `OnConflictStrategy.REPLACE`
  (upsert по PK).
- `ContentDao.replaceLessonCards(lessonId, cards)` — `@Transaction { deleteCardsForLesson; insertCards }`
  (атомарная замена пула карт урока при реимпорте).
- Legacy-паттерн: `removePacksForLanguage(packId, languageId)` перед импортом — в v2
  заменён на `deletePack(packId)` (FK CASCADE) внутри транзакции или на REPLACE-upsert.
- `VocabWordEntity` unique index `(packId, word)`; `verb_drill_combo_progress` unique index
  `(packId, group, tense)` — дедупликация на уровне схемы.

Проверка: AC покрывают «re-import same pack 2x → no duplicates».

### NFR-6. Парсинг-регрессия (5 парсеров regression-locked)

Логика 5 парсеров (`CsvParser`, `VerbDrillCsvParser`, `VocabCsvParser`,
`MultilingualStoryParser`, `StoryQuizParser`, `BgVocabCsvParser`) переносится из legacy
**без изменения поведения** (R5). Метрика: legacy-тесты (`CsvParserTest`,
`CsvParserLazyLoadingTest`, `VerbDrillCsvParserTest`, `VocabCsvParserTest`,
`BgVocabCsvParserTest`, `MultilingualStoryParserPauseTest`, `StoryQuizParserTest`)
переносятся в v2 (`app/src/test/.../v2/core/data/packimport/`) и остаются зелёными БЕЗ
изменения утверждений. Команда: `./gradlew :app:test` зелёный; число `@Test` в
packimport-тестах ≥ legacy-эталона.

`ParseResult<T, ParseError>` (sealed success/partial/failure) сохраняет семантику: partial
возвращает и данные, и ошибки.

### NFR-7. Производительность PackImport (bulk-insert)

Импорт типичного пака (порядка 100–500 карт + drills + stories) завершается за приемлемое
время на эмуляторе/устройстве. Метрика: bulk-insert через `insertCards(List<CardEntity>)` /
`insertVerbDrillCards(List<...>)` (один `@Insert onConflict=REPLACE` на батч, не построчно);
импорт пака ≤ 2000 карт → < 3 секунд на mid-range эмуляторе (порог уточняется в AC; цель —
не блокировать UI поток, импорт на `Dispatchers.IO`). Все `PackImporter`-методы — `suspend`.

### NFR-8. Стабильность контракта портов (наследие SRS-001 NFR-4)

Data-слой (`*Impl`) реализует 9 доменных портов (SRS-001 §5) **без изменения сигнатур**.
Метрика: после старта Wave 1 `git diff` на `:domain/repository/*` и `:domain/audio/*` =
пусто (вне drift-задач). PackImport добавляет **write-операции контента** в
`ContentRepositoryImpl` (private-методы, использующие `ContentDao`), но **не меняет**
публичный интерфейс `ContentRepository` (read-only порт, SRS-001 §5.1). Если для write-side
потребуется новая доменная операция — это drift-задача с `trace_add(derived_from, SRS-001)`.

---

## 5. API contract — packimport / backup / language (ОБЯЗАТЕЛЬНАЯ секция)

> Brief `shared_mutation_risk=true`: `ContentRepositoryImpl` — источник данных для ВСЕХ
> вертикальных эпиков Wave 1–4 (PRD §2). Любое изменение Room-схемы после старта Wave 1 =
> миграция БД + drift. Эта секция фиксирует канонический контракт **до** старта workers.

### 5.1. PackImporter (public surface)

```kotlin
// app/.../v2/core/data/packimport/PackImporter.kt
class PackImporter @Inject constructor(
    private val database: GrammarMateDatabase,
    private val context: Context,
) {
    suspend fun importPackFromUri(uri: Uri, resolver: ContentResolver): PackImportResult
    suspend fun importPackFromAssets(assetPath: String): PackImportResult
    suspend fun importLessonFromUri(
        languageId: String, uri: Uri, resolver: ContentResolver,
    ): PackImportResult
    suspend fun readPackManifestFromAssets(assetPath: String): LessonPackManifest
}

sealed interface PackImportResult {
    data class Success(val pack: LessonPack) : PackImportResult
    data class Partial(val pack: LessonPack, val errors: List<ParseError>) : PackImportResult
    data class Failed(val errors: List<ParseError>) : PackImportResult
}
```

Extension point: новый тип контента (напр. аудио-deck) — добавить private-метод
`importXxxFromPack(packDir, manifest)` и вызвать его внутри `database.withTransaction { … }`.
**НЕ** добавлять новый метод в `ContentRepository` (read-only порт) — write-side живёт в
`PackImporter` (data-деталь).

### 5.2. LessonPackManifest (schema v1/v2)

```kotlin
// app/.../v2/core/data/packimport/LessonPackManifest.kt
data class LessonPackManifest(
    val schemaVersion: Int,           // 1 или 2 (иначе error)
    val packId: String, val packVersion: String, val language: String,
    val lessons: List<LessonPackLesson>,         // v1 root-level
    val displayName: String? = null,
    val verbDrill: DrillFiles? = null,
    val vocabDrill: DrillFiles? = null,
    val backgroundVocab: BackgroundVocabSection? = null,
    val chapters: List<Chapter> = emptyList(),   // v2 only
) {
    companion object { fun fromJson(text: String): LessonPackManifest }
}
data class LessonPackLesson(val lessonId: String, val order: Int, val title: String?,
    val file: String, val type: String = "standard",
    val tenses: List<String> = emptyList(), val grammarChip: String? = null)
data class DrillFiles(val files: List<String>)
data class BackgroundVocabSection(val file: String, val audioDir: String?,
    val defaultLanguage: String = "it", val translationLanguage: String = "ru")
data class Chapter(val chapterId: String, val order: Int, val title: String,
    val subtitle: String?, val storyFile: String?, val lessons: List<String>)
```

### 5.3. BackupManager (public surface)

```kotlin
// app/.../v2/core/data/backup/BackupManager.kt
interface BackupManager {
    suspend fun createBackup(): Boolean
    suspend fun restoreFromBackup(backupPath: String): Boolean
    suspend fun restoreFromBackupUri(backupUri: Uri): Boolean
    suspend fun getAvailableBackups(treeUri: Uri): List<BackupInfo>
    suspend fun getAvailableBackups(): List<BackupInfo>
    suspend fun deleteBackup(backupPath: String): Boolean
    suspend fun hasBackup(): Boolean
}
data class BackupInfo(val name: String, val path: String, val uri: String? = null,
    val timestamp: String, val dataSize: Long, val metadata: String)
```

Extension point: новый формат backup — расширить `BackupInfo.metadata` (schemaVersion,
timestamp, размер); НЕ менять сигнатуры `BackupManager`.

### 5.4. LanguageSeed (public surface)

```kotlin
// app/.../v2/core/data/language/LanguageSeed.kt
class LanguageSeed @Inject constructor(
    private val contentDao: ContentDao,
    private val settingsRepository: SettingsRepository,  // для seed-флага
) {
    suspend fun ensureSeedData()
    suspend fun cleanupStalePacks(defaultPacks: List<DefaultPack>)
    suspend fun seedDefaultPacksIfNeeded(importFromAssets: (String) -> Boolean): Boolean
    suspend fun updateDefaultPacksIfNeeded(
        importFromAssets: (String) -> Boolean,
        readManifestFromAssets: (String) -> LessonPackManifest?,
    ): Boolean
    suspend fun forceReloadDefaultPacks(
        removeInstalledPackData: (String) -> Boolean,
        importFromAssets: (String) -> Boolean,
    ): Boolean
    companion object {
        val LANGUAGES_SEED: List<Language> = listOf(  // идентично ContentRepositoryImpl
            Language(LanguageId("en"), "English"), Language(LanguageId("it"), "Italian"),
            Language(LanguageId("de"), "German"),   Language(LanguageId("zh"), "Chinese"),
            Language(LanguageId("ru"), "Russian"),  Language(LanguageId("el"), "Greek"),
        )
    }
}
```

### 5.5. Маппинг parser-output → Room-entity (контракт преобразования)

| Парсер (output) | Room entity | DAO insert-метод | PK / Unique |
|---|---|---|---|
| `CsvParser.parseLesson` → `SentenceCard(id="card_$ln")` | `CardEntity` (type=SENTENCE) | `ContentDao.replaceLessonCards(lessonId, cards)` | `id` (PK) |
| `VerbDrillCsvParser.parse` → `VerbDrillCard(id="${g}_${t}_${i}")` | `VerbDrillCardEntity` | `DrillDao.insertVerbDrillCards(list)` | `id` (PK) |
| `VocabCsvParser.parse` → `VocabRow` | `VocabWordEntity(id="${pos}_${rank}_${word}")` | `DrillDao.insertVocabWords(list)` | `(packId, word)` unique |
| `StoryQuizParser.parse` → `StoryQuiz` | story-link через `ChapterEntity.storyFile` / `LessonEntity.grammarChipKey` | `ContentDao.insertChapter` / `insertLesson` | drift-задача при gap |
| `BgVocabCsvParser.parse` → `WordScript` | word-script entity (drift при отсутствии) | TBD | drift-задача при gap |
| manifest | `PackEntity`, `ChapterEntity`, `LessonEntity` | `ContentDao.insertPack/insertChapters/insertLessons` | `id` (PK), FK CASCADE |

**ВАЖНО:** при отсутствии подходящей entity для story-text/bg-vocab-word-script (5/6
domaенов покрываются существующими entities) — это **явный gap**, регистрируемый как
drift-задача с миграцией БД (§7 R3). SRS фиксирует намерение, реализация gap'а — отдельная
задача, трассируемая к SRS-002.

---

## 6. Контракт Room-сущностей (29 entities / 6 DAOs)

> Источник истины — `app/.../v2/core/data/local/entity/*.kt` и `dao/*.kt` (verified
> 2026-07-07). Схема стабильна (version=1, NFR-2). Wave 1–4 опираются на этот контракт;
> изменение = drift с миграцией БД.

### 6.1. Entities по доменам (29 шт.)

#### Content (4) — `ContentEntities.kt`, read-mostly, наполняются PackImport
| Entity | Таблица | PK | Назначение |
|---|---|---|---|
| `PackEntity` | `packs` | `id: String` | Корневой импортируемый пак (`languageId`, `displayName?`, `version`, `importedAtMs`) |
| `ChapterEntity` | `chapters` | `id: String` | Глава (FK→Pack CASCADE; `order`, `title`, `subtitle?`, `storyFile?`) |
| `LessonEntity` | `lessons` | `id: String` | Урок (FK→Chapter CASCADE; `packId`, `chapterId?`, `order`, `title`, `cefrLevel?`, `grammarChipKey?`) |
| `CardEntity` | `cards` | `id: String` | Карточка (`packId`, `lessonId`, `ord`, `type`, `promptRu`, `acceptedAnswersJson`, `tense?`, `verb?`, `verbGroup?`, `person?`, `frequencyRank?`); ID = `card_<lineNumber>`, **`card_1` НЕ существует** |

#### Session (3) — `SessionEntities.kt`, ★ фикс card_15
| Entity | Таблица | PK | Назначение |
|---|---|---|---|
| `SessionEntity` | `sessions` | `id: String` | Состояние сессии (`currentCardId: String?` ★ PK текущей карты, не индекс; `mode`, `status`, `state`, счётчики, `subLessonIndex`, `selectedTense/Group/Person?`, временные метрики) |
| `SessionCardEntity` | `session_cards` | `(sessionId, ord)` | Пул сессии (FK→Session CASCADE; `cardId`) |
| `SessionShownCardEntity` | `session_shown_cards` | `(sessionId, cardId)` | Показанные в сессии (FK→Session CASCADE; `shownAtMs`) |

#### Mastery / SRS (3) — `MasteryEntities.kt`
| Entity | Таблица | PK | Назначение |
|---|---|---|---|
| `MasteryStateEntity` | `mastery_states` | `id: String` (`"<packId>:<lessonId>"`) | Освоенность урока (`uniqueCardShows`, `totalCardShows`, `lastShowDateMs`, `intervalStepIndex`, `fsrsStateJson?`, `dueAtMs` ★ SRS-индекс, `completedAtMs?`); unique `(packId, lessonId)`, index `dueAtMs` |
| `ShownCardEntity` | `shown_cards` | `(packId, lessonId, cardId)` | Множество показанных карт урока |
| `CardEncounterEntity` | `card_encounters` | `(packId, lessonId, cardId)` | Счётчик встреч карты (`count`) |

#### Progress (5) — `ProgressEntities.kt`
| Entity | Таблица | PK | Назначение |
|---|---|---|---|
| `StreakEntity` | `streaks` | `languageId: String` | Серия дней по языку (`currentStreak`, `longestStreak`, `lastCompletionDateMs?`, `totalSubLessonsCompleted`, `todayFireCount`, `lastFireDateMs?`) |
| `StreakPracticeTodayEntity` | `streak_practice_today` | `(languageId, practiceType)` | Дедуп типов практики за день |
| `DrillProgressEntity` | `drill_progress` | `id: String` (`"<packId>:<drillType>"`) | Прогресс drill (`drillType`, `comboKey?`, `totalCards`, `everShownCardIdsJson`, `todayShownCardIdsJson`, `lastDate?`, `cursor`, `updatedAtMs`); unique `(packId, drillType)` |
| `ChapterProgressEntity` | `chapter_progress` | `(packId, chapterId)` | Прогресс главы (`lessonsStarted`, `lessonsCompleted`, `lastAccessedMs`) |
| `DailyCursorEntity` | `daily_cursors` | `packId: String` | Курсор дневной нормы (`sentenceOffset`, `currentLessonIndex`, `verbOffset`, `firstSession*Json`, `firstSessionLessonId?`, `updatedAtMs`) |

#### Drill (8) — `DrillEntities.kt`
| Entity | Таблица | PK | Назначение |
|---|---|---|---|
| `VocabWordEntity` | `vocab_words` | `id: String` (`"${pos}_${rank}_${word}"`) | Контент: слово (`packId`, `word`, `pos`, `rank`, `meaningRu?`, `collocationsJson`, `formsJson`); unique `(packId, word)` |
| `WordMasteryEntity` | `word_mastery` | `wordId: String` | SRS слова (`intervalStepIndex`, `correct/incorrectCount`, `last/nextReviewDateMs` ★ SRS-индекс, `isLearned`) |
| `VerbDrillCardEntity` | `verb_drill_cards` | `id: String` (`"${group}_${tense}_${i}"`) | Контент: спряжение (`packId`, `promptRu`, `answer`, `verb?`, `tense?`, `group?`, `person?`, `rank?`); index `(packId, tense)`, `(packId, verb, tense)` |
| `AuxDrillCardEntity` | `aux_drill_cards` | `id: String` | Контент: подводящая (`packId`, `promptRu`, `answer`, `verb?`, `tense?`, `group?`, `person?`, `rank?`); index `(packId, verb, tense)` |
| `VerbDrillComboProgressEntity` | `verb_drill_combo_progress` | `(packId, comboKey)` | Прогресс combo (`group`, `tense`, `totalCards`, `ever/todayShownCardIdsJson`, `lastDate?`, `updatedAtMs`); unique `(packId, group, tense)` |
| `AuxDrillComboProgressEntity` | `aux_drill_combo_progress` | `(packId, comboKey)` | Прогресс aux combo (`verb`, `tense`, `totalCards`, `everShownCardIdsJson`, `lastDate?`, `updatedAtMs`); unique `(packId, verb, tense)` |
| `VerbDrillLastSessionEntity` | `verb_drill_last_session` | `packId: String` | Resume последней verb-сессии (`selectedTense/Group/Person?`, `sortByFrequency`, `todayShown/sessionCardIdsJson`, `currentIndex`, `updatedAtMs`); unique `packId` |
| `BossRewardEntity` | `boss_rewards` | `(packId, bossType, scopeKey)` | Награда за boss (`bossType`=BossType.name, `scopeKey`, `reward`=BossReward.name, `earnedAtMs`) |

#### UserContent (6) — `UserContentEntities.kt`
| Entity | Таблица | PK | Назначение |
|---|---|---|---|
| `HiddenCardEntity` | `hidden_cards` | `cardId: String` | Скрытая карта (`hiddenAtMs`) |
| `BadSentenceEntity` | `bad_sentences` | `(packId, cardId)` | «Плохое» предложение (`languageId`, `sentence`, `translation`, `mode`, `addedAtMs`); index `cardId` |
| `BgVocabMarkEntity` | `bg_vocab_marks` | `word: String` | Пометка слова (`mark`=NONE/GREEN/RED, `updatedAtMs`); unique `word` |
| `BgVocabPositionEntity` | `bg_vocab_position` | `key: String` | Курсор bg-vocab (`word`, `updatedAtMs`) |
| `PomodoroHistoryEntity` | `pomodoro_history` | `id: String` | Запись помодоро (`languageId`, `packId?`, `lessonId?`, `completedAtMs` index, `durationMinutes`, `totalSeconds`, `remainingSeconds`, `cardsShown/Correct/Incorrect`, `wordsPerMinute`) |
| `MigrationFlagEntity` | `migratable_files` | `key: String` | Флаг миграции (`done`, `migratedAtMs`) |

**Итого: 4 + 3 + 3 + 5 + 8 + 6 = 29 entities** (verified `grep "^@Entity" … | wc -l` = 29).

### 6.2. DAOs — ключевые query-сигнатуры

#### ContentDao — контент паков (FR-1..FR-7 наполняют; read-side для UI)
```kotlin
@Dao interface ContentDao {
    // Write (PackImport): REPLACE-upsert (идемпотентный реимпорт)
    @Insert(onConflict = REPLACE) suspend fun insertPack(pack: PackEntity)
    @Insert(onConflict = REPLACE) suspend fun insertPacks(packs: List<PackEntity>)
    @Insert(onConflict = REPLACE) suspend fun insertChapters(chapters: List<ChapterEntity>)
    @Insert(onConflict = REPLACE) suspend fun insertLessons(lessons: List<LessonEntity>)
    @Insert(onConflict = REPLACE) suspend fun insertCards(cards: List<CardEntity>)
    @Transaction suspend fun replaceLessonCards(lessonId: String, cards: List<CardEntity>)  // ★ атомарная замена пула
    @Query("DELETE FROM packs WHERE id = :packId") suspend fun deletePack(packId: String)     // FK CASCADE
    // Read (UI / *Impl)
    @Query("SELECT * FROM packs") suspend fun getPacks(): List<PackEntity>
    @Query("SELECT * FROM packs WHERE languageId = :langId") suspend fun getPacksForLanguage(langId: String): List<PackEntity>
    @Query("SELECT * FROM lessons WHERE packId = :packId ORDER BY \`order\`") suspend fun getLessons(packId: String): List<LessonEntity>
    @Query("SELECT * FROM lessons WHERE packId = :packId ORDER BY \`order\`") fun observeLessons(packId: String): Flow<List<LessonEntity>>  // единственный Flow
    @Query("SELECT * FROM lessons WHERE packId = :packId AND chapterId = :chapterId ORDER BY \`order\`") suspend fun getLessonsForChapter(packId: String, chapterId: String): List<LessonEntity>
    @Query("SELECT * FROM cards WHERE lessonId = :lessonId ORDER BY ord") suspend fun getCards(lessonId: String): List<CardEntity>
    @Query("SELECT * FROM chapters WHERE packId = :packId ORDER BY \`order\`") suspend fun getChapters(packId: String): List<ChapterEntity>
}
```

#### MasteryDao — освоенность + SRS (FR-11 lesson_progress fresh)
```kotlin
@Dao interface MasteryDao {
    @Insert(onConflict = REPLACE) suspend fun upsert(entity: MasteryStateEntity)
    @Query("SELECT * FROM mastery_states WHERE packId = :packId AND lessonId = :lessonId") suspend fun get(packId: String, lessonId: String): MasteryStateEntity?
    @Query("SELECT * FROM mastery_states WHERE packId = :packId AND lessonId = :lessonId") fun observe(packId: String, lessonId: String): Flow<MasteryStateEntity?>
    @Query("UPDATE mastery_states SET completedAtMs = :now WHERE packId = :packId AND lessonId = :lessonId AND completedAtMs IS NULL") suspend fun markCompleted(packId: String, lessonId: String, now: Long)
    @Query("SELECT * FROM mastery_states WHERE dueAtMs <= :now AND completedAtMs IS NULL ORDER BY dueAtMs LIMIT :limit") suspend fun dueCards(now: Long, limit: Int): List<MasteryStateEntity>  // ★ SRS-выборка
    @Transaction suspend fun recordCardShow(packId: String, lessonId: String, cardId: String, now: Long, updatedMastery: MasteryStateEntity)  // ★ атомарно: upsert + markShown + incrementEncounter
    @Query("INSERT INTO card_encounters(...) VALUES (...,1) ON CONFLICT(...) DO UPDATE SET count = count + 1") suspend fun incrementEncounter(packId: String, lessonId: String, cardId: String)
}
```

#### SessionDao — ★ фикс card_15 (atomic snapshot)
```kotlin
@Dao interface SessionDao {
    @Insert(onConflict = REPLACE) suspend fun upsertSession(session: SessionEntity)
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun getSession(id: String): SessionEntity?
    @Query("UPDATE sessions SET currentCardId = :cardId, cursorIndex = :cursor, updatedAtMs = :now WHERE id = :id") suspend fun updateCursor(id: String, cardId: String?, cursor: Int, now: Long)  // ★ PK, не индекс
    @Transaction suspend fun replacePool(sessionId: String, cards: List<SessionCardEntity>)  // атомарный delete+insert
    @Transaction suspend fun saveSnapshot(session: SessionEntity, cards: List<SessionCardEntity>, shown: List<SessionShownCardEntity>)  // ★★ атомарный снимок (card_15 fix)
}
```

#### ProgressDao — streak / chapter / drill / daily
```kotlin
@Dao interface ProgressDao {
    @Insert(onConflict = REPLACE) suspend fun upsertStreak(entity: StreakEntity)
    @Query("SELECT * FROM streaks WHERE languageId = :langId") fun observeStreak(langId: String): Flow<StreakEntity?>
    @Transaction suspend fun recordPracticeCompletion(langId: String, practiceType: String, now: Long, streak: StreakEntity)  // ★ атомарно: upsert + insertPracticeToday(IGNORE, дедуп)
    @Insert(onConflict = REPLACE) suspend fun upsertChapterProgress(entity: ChapterProgressEntity)
    @Query("UPDATE chapter_progress SET lessonsStarted = lessonsStarted + :startedDelta, lessonsCompleted = lessonsCompleted + :completedDelta, lastAccessedMs = :now WHERE packId = :packId AND chapterId = :chapterId") suspend fun incrementChapterProgress(packId: String, chapterId: String, startedDelta: Int, completedDelta: Int, now: Long)
    @Insert(onConflict = REPLACE) suspend fun upsertDrillProgress(entity: DrillProgressEntity)
    @Insert(onConflict = REPLACE) suspend fun upsertDailyCursor(entity: DailyCursorEntity)
}
```

#### DrillDao — vocab/verb/aux/combo/boss (FR-4/FR-6 наполняют; read для Wave 2)
```kotlin
@Dao interface DrillDao {
    // Write (PackImport): bulk REPLACE
    @Insert(onConflict = REPLACE) suspend fun insertVocabWords(entities: List<VocabWordEntity>)
    @Insert(onConflict = REPLACE) suspend fun insertVerbDrillCards(entities: List<VerbDrillCardEntity>)
    @Insert(onConflict = REPLACE) suspend fun insertAuxDrillCards(entities: List<AuxDrillCardEntity>)
    // Read (Wave 2 Verb/Vocab)
    @Query("SELECT * FROM vocab_words WHERE packId = :packId AND rank >= :minRank AND rank <= :maxRank ORDER BY rank") suspend fun getVocabWordsByRankRange(packId: String, minRank: Int, maxRank: Int): List<VocabWordEntity>
    @Query("SELECT * FROM word_mastery WHERE nextReviewDateMs <= :now ORDER BY nextReviewDateMs LIMIT :limit") fun observeDueWords(now: Long, limit: Int): Flow<List<WordMasteryEntity>>  // ★ SRS-выборка слов
    @Query("SELECT * FROM verb_drill_cards WHERE packId = :packId AND verb = :verb AND tense = :tense") suspend fun getVerbDrillCardsForVerbTense(packId: String, verb: String, tense: String): List<VerbDrillCardEntity>
    @Transaction suspend fun markWordReviewed(updatedMastery: WordMasteryEntity)
    @Transaction suspend fun recordVerbCardShown(updatedProgress: VerbDrillComboProgressEntity)
    @Transaction suspend fun recordAuxCardShown(updatedProgress: AuxDrillComboProgressEntity)
    @Insert(onConflict = REPLACE) suspend fun upsertBossReward(entity: BossRewardEntity)
}
```

#### UserContentDao — hidden/bad/bg-vocab/pomodoro/flags
```kotlin
@Dao interface UserContentDao {
    @Insert(onConflict = REPLACE) suspend fun hideCard(entity: HiddenCardEntity)
    @Query("SELECT cardId FROM hidden_cards") fun observeHiddenCardIds(): Flow<List<String>>
    @Insert(onConflict = REPLACE) suspend fun insertBadSentence(entity: BadSentenceEntity)
    @Query("SELECT EXISTS(SELECT 1 FROM bad_sentences WHERE packId = :packId AND cardId = :cardId)") suspend fun isBadSentence(packId: String, cardId: String): Boolean
    @Insert(onConflict = REPLACE) suspend fun upsertBgVocabMark(entity: BgVocabMarkEntity)
    @Query("UPDATE bg_vocab_marks SET mark = 'NONE'") suspend fun resetGreenMarks()
    @Insert(onConflict = REPLACE) suspend fun addPomodoroSession(entity: PomodoroHistoryEntity)
    @Query("SELECT * FROM pomodoro_history ORDER BY completedAtMs DESC") fun observePomodoroHistory(): Flow<List<PomodoroHistoryEntity>>
    @Insert(onConflict = REPLACE) suspend fun setMigrationFlag(entity: MigrationFlagEntity)
    @Query("SELECT done FROM migratable_files WHERE \`key\` = :key") suspend fun getMigrationFlag(key: String): Boolean?
}
```

### 6.3. Converters (TypeConverters)

`Converters.kt` (registered via `@TypeConverters(Converters::class)` на `GrammarMateDatabase`)
покрывает `List<String> ↔ JSON`, `Set<String> ↔ JSON`, `Map<String, Int> ↔ JSON` через
`kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }`.
`null`/пустая коллекция нормализуются к `"[]"` / `"{}"`. **Однако** ряд JSON-колонок
(`CardEntity.acceptedAnswersJson`, `DrillProgressEntity.everShownCardIdsJson`,
`VerbDrillComboProgressEntity.everShownCardIdsJson`, `VerbDrillLastSessionEntity.sessionCardIdsJson`,
`DailyCursorEntity.firstSession*Json`, `MasteryStateEntity.fsrsStateJson`) хранятся как
готовые строки и сериализуются в `*Impl` (а не через TypeConverter) — это осознанное
решение (см. KDoc `DrillEntities.kt`), сохраняется в E02.

---

## 7. Риски (перенесены из PRD §8, дополненные технической спецификой)

- **R1 (data-integrity-risk, atomic import):** частичный pack-import оставляет «полу-пак»,
  ломающий read-сторону downstream. **Митигация:** FR-7 — `database.withTransaction { … }`,
  rollback при любой ошибке; NFR-1; SM-2. Наследует гарантию legacy `AtomicFileWriter`, но
  на уровне Room.
- **R2 (shared-mutation-risk, ContentRepositoryImpl):** источник данных для ВСЕХ
  вертикальных эпиков Wave 1–4; изменение Room-схемы = миграция БД + drift по downstream.
  **Митигация:** §6 фиксирует 29 entities / 6 DAO; NFR-2 (exportSchema, schemaLocation);
  NFR-8 (стабильность портов); SM-10; drift-задачи трассируются к SRS-002.
- **R3 (schema-drift после Wave 1):** PackImport вскроет legacy-поле, не вошедшее в схему
  (напр. story-text, bg-vocab word-script — см. §5.5 gap). **Митигация:** завершить PackImport
  **до** старта Wave 1 (PRD §7.2); gap регистрируется как drift-задача с `addMigrations` +
  version bump (не тихое расширение схемы).
- **R4 (backup-формат round-trip):** если формат не покрывает все 29 entities, restore
  теряет данные. **Митигация:** FR-9 — Room DB file с WAL checkpoint покрывает все таблицы
  одним снимком; NFR-3; SM-6; DataStore (settings) покрывается отдельным file-copy или
  расширенным snapshot (деталь реализации, AC покрывают).
- **R5 (парсинг-регрессия):** перенос 5 парсеров из legacy в v2 может внести расхождения.
  **Митигация:** NFR-6 — legacy-тесты переносятся как regression-lock, зелёные без изменения
  утверждений; `ParseResult` sealed сохраняет success/partial/failure семантику.
- **R6 (migrator-removal backward-compat):** `@Deprecated` вместо delete сохраняет
  компиляцию, но warning должен быть осознанным. **Митигация:** FR-13 / SM-5 — 0 активных
  вызовов migrator в v2; `@Suppress` только в файле migrator'а.
- **R7 (SAF permission revoked):** backup/restore падает при отозванном URI permission.
  **Митигация:** NFR-4 — `BackupManager` возвращает `false` (не падает); AC покрывают.

---

## 8. Зависимости и интеграция

### 8.1. Зависимости `:app:v2/core/data`

Data-слой зависит от:
- `:domain` (pure Kotlin) — 9 портов, доменные модели, `MultilingualStoryParser` (E01 FR-12),
  `SrsMigration`, `SrsCardState`.
- `androidx.room:room-*` — `GrammarMateDatabase`, DAOs, `withTransaction`.
- `androidx.documentfile:documentfile` — SAF (`BackupManager`, `DocumentFile`).
- `kotlinx.serialization-json` — JSON-колонки (`Converters`, маппинг entity↔domain).
- `javax.inject` / Hilt — DI `*Impl` (`@Inject constructor`).
- `org.yaml.snakeyaml` — **только** в `YamlToRoomMigrator` (`@Deprecated`); PackImport не
  использует YAML (контент паков — CSV/JSON/MD).

Data-слой НЕ зависит от: Compose, Sherpa-ONNX (это E03), UI-слоя.

### 8.2. Зависит от (upstream)

- **E01 — SRS-001 (domain core), artifact_id=450, status=accepted.** 9 доменных портов
  зафиксированы в §5 SRS-001 как стабильный контракт. Data layer пишет имплементации **под
  эти контракты**, не меняя их. `MultilingualStoryParser` (доменный pure-парсер) вызывается
  из data — FR-5. Любое изменение сигнатуры порта после старта Wave 1 = drift
  (SRS-001 NFR-4).
- **Реальный код v2** (`v2/core/data/*`) — source of truth. 29 entities, 6 DAO,
  `GrammarMateDatabase`, 7 `*Impl` уже существуют (verified 2026-07-07); E02 наполняет
  write-сторону и завершает read/write-полноту.

### 8.3. Блокирует (downstream)

- **E04 — Training (Wave 1):** `SessionRepositoryImpl` + контент из PackImport нужны для
  тренировочной сессии. Без импортированных уроков `SessionEngine` (FR-3 E01) не имеет карт.
- **E05 — Verb drill, E06 — Vocab drill (Wave 2):** зависят от наполненных
  `VerbDrillCardEntity` / `AuxDrillCardEntity` / `VocabWordEntity` (через FR-4/FR-6 парсеры)
  и `VocabDrillRepositoryImpl` + `DrillDao`.
- **E07 — Daily, Story epics (Wave 3):** Daily-композиторы (FR-14 E01) требуют контент;
  Stories требуют импортированного story-контента (FR-5). `ContentDao`/`MasteryDao` —
  read-сторона для них.
- **E08+ — Gamification, Boss, Pomodoro (Wave 4):** читают прогресс из
  `ProgressRepositoryImpl` / `UserContentRepositoryImpl` / `DrillDao` (boss rewards).
- **E13 — Settings UI:** кнопки Import/Backup/Restore/Delete в Settings вызывают контракты
  E02: `importPackFromUri`, `importPackFromAssets`, `importLessonFromUri`, `createBackup`,
  `restoreFromBackupUri`, `deletePack`, `resetAndReimport`, `createEmptyLesson` (OQ-4).
- **MainActivity.onCreate restore-точка (E04+):** restore-вызов фиксируется здесь (FR-10),
  реализация — в app-shell (E04+).

### 8.4. Параллельно

- **E03 — Audio adapter:** не блокирует E02 (pack импортирует **пути** к аудио как String,
  не проигрывает). `BgVocabCsvParser` читает `audioDir` (путь), Sherpa-воспроизведение — E03.
  Запускается параллельно после E01.

---

## 9. Traceability

| Артефакт | Тип | Source (derived_from) |
|---|---|---|
| **SRS-002** (этот документ) | SRS | PRD-002 (artifact_id=475) |
| FR-1 PackImporter точки входа | FR | SRS-002; PRD §4.1; legacy `PackImporter` |
| FR-2 Manifest schema v1/v2 | FR | SRS-002; PRD §4.2; legacy `LessonPackManifest` |
| FR-3 CsvParser → CardEntity | FR | SRS-002; PRD §4.1 (parser 1); legacy `CsvParser` |
| FR-4 VerbDrillCsvParser → VerbDrillCardEntity | FR | SRS-002; PRD §4.1 (parser 2); legacy `VerbDrillCsvParser` |
| FR-5 MultilingualStoryParser + StoryQuizParser | FR | SRS-002; PRD §4.1 (parser 4); **SRS-001 FR-12** (доменный парсер) |
| FR-6 VocabCsvParser → VocabWordEntity | FR | SRS-002; PRD §4.1 (parser 3); legacy `VocabCsvParser` |
| FR-7 BgVocabCsvParser + atomic import | FR | SRS-002; PRD §4.1 (parser 5) + §4.7; legacy `BgVocabCsvParser` |
| FR-8 Pack lifecycle (delete/reset/createEmpty/partial) | FR | SRS-002; PRD §4.7 |
| FR-9 Backup → SAF (WAL checkpoint) | FR | SRS-002; PRD §4.3; OQ-1 answered |
| FR-10 Restore + restore-gate | FR | SRS-002; PRD §4.3; legacy `BackupRestorer` |
| FR-11 lesson_progress fresh | FR | SRS-002; PRD §4.4; OQ-2 answered |
| FR-12 LanguageManager.ensureSeedData | FR | SRS-002; PRD §4.5; legacy `LanguageManager` |
| FR-13 YamlToRoomMigrator @Deprecated | FR | SRS-002; PRD §4.6; **SRS-001 FR-13** |
| NFR-1 Atomic integrity | NFR | SRS-002; PRD SM-2 |
| NFR-2 Schema stability | NFR | SRS-002; PRD SM-10 |
| NFR-3 Backup round-trip | NFR | SRS-002; PRD SM-6 |
| NFR-4 SAF permissions | NFR | SRS-002; PRD §4.3 |
| NFR-5 Idempotent import | NFR | SRS-002; PRD §4.1 |
| NFR-6 Парсинг-регрессия 5 парсеров | NFR | SRS-002; PRD R5 |
| NFR-7 Производительность PackImport | NFR | SRS-002 |
| NFR-8 Стабильность контракта портов | NFR | SRS-002; **SRS-001 NFR-4** |

UC (saga-analyst, будущие) будут `derived_from` US-кодов PRD-002 (US-1..US-6) и опираться
на FR-коды выше. Dev-задачи (saga-planner) будут `implements` FR-коды; PackImport-парсеры,
Backup-компоненты и LanguageSeed трассируются к FR-1..FR-13.

---

## 10. Допущения

Из PRD §9 (H1–H3 verified) + дополнительные:

- **A1:** Текущая read-имплементация портов (`ContentRepositoryImpl` и др.) функционально
  корректна и **не переписывается** в E02 (только добавляются write-операции контента через
  `PackImporter`). Если read-side содержит баг — отдельная задача.
- **A2:** `MainActivity` и app-shell (DI-модули, Hilt graph) — E04+; E02 предоставляет
  data-контракт (`PackImporter`, `BackupManager`, `LanguageSeed`), а не wire-up.
- **A3:** 6 языков (en/it/de/zh/ru/el) — стабильный продуктовый набор (PRD A3); новые языки
  — расширение `LANGUAGES_SEED`, не UI в E02.
- **A4:** Backup формат = Room DB file (OQ-1 answered); DataStore (settings) покрывается
  backup'ом (отдельный file-copy или расширение snapshot — деталь реализации, AC покрывают).
- **A5:** `lesson_progress` fresh (OQ-2 answered) — БЕЗ новой таблицы, через существующие
  `MasteryStateEntity`/`ShownCardEntity` (SM-10: схема стабильна).
- **A6:** При обнаружении gap (story-text/bg-vocab-word-script без подходящей entity) —
  drift-задача с миграцией БД (R3), не тихое расширение схемы.

---

## 11. Open questions (status из PRD §10 + разрешённые в SRS)

| Код | Вопрос | Статус после SRS-002 |
|---|---|---|
| **OQ-1** | Backup формат: Room DB file (WAL checkpoint) или JSON-экспорт? | **Resolved (FR-9):** Room DB file с `PRAGMA wal_checkpoint(FULL)`, покрывает все 29 entities одним снимком. |
| **OQ-2** | `lesson_progress` fresh в Room? | **Resolved (FR-11):** да, fresh, БЕЗ новой таблицы, через `MasteryStateEntity`/`ShownCardEntity`. |
| **OQ-3** | Полное физическое удаление `YamlToRoomMigrator` — в каком эпизоде? | **Open (follow-up):** после стабилизации Wave 1. E02 делает `@Deprecated` (FR-13). |
| **OQ-4** | Settings UI (E13) вызывает import — какие exactly операции? | **Open (E13):** E02 фиксирует контракты (`importPackFromUri/Assets`, `importLessonFromUri`, `createBackup`, `restoreFromBackupUri`, `deletePack`, `resetAndReimport`, `createEmptyLesson`, FR-1/FR-8/FR-9/FR-10). |
| **OQ-5 (new)** | Story-text и bg-vocab word-script: достаточно ли существующих entities (`ChapterEntity.storyFile` путь) или нужна drift-миграция (новые таблицы)? | **Open (drift-задача при реализации FR-5/FR-7):** SRS фиксирует намерение (story/bg-vocab контент импортируется), gap регистрируется как drift с `addMigrations` (R3). |

---

*SRS фиксирует систему (Room-схема, PackImport-поток, Backup/Restore, Language seed), не
пользовательские потоки (UC — saga-analyst) и не бизнес-intent (PRD-002). Контракт портов
§5 + Room-entity контракт §6 — обязательные секции из-за shared_mutation_risk=true (brief).
Все сигнатуры верифицированы по фактическому коду `v2/core/data/*` и `legacy-src/.../data/*`
на 2026-07-07 (CLAUDE.md: code is source of truth).*
