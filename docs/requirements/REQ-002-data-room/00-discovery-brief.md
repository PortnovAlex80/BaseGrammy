# Discovery Brief — REQ-002 Слой данных Room: PackImport + Backup (без мигратора)

**Эпизод:** E02 (epic_id=86), проект GrammarMate (id=25)
**Волна:** 0 — Фундамент
**Стадия:** Discovery → Formalization
**Дата:** 2026-07-07
**Upstream:** SRS-001 (E01, artifact_id=450) — доменные порты зафиксированы.

---

## 1. Бизнес-цели

Завершить слой данных (`v2/core/data`) до полного покрытия функционала legacy-приложения: реализовать **PackImport→Room** (приём контента из ZIP/CSV/YAML в Room-сущности), **Backup/Restore через Room snapshot**, и **удалить YamlToRoomMigrator** (спонсор Q3 = greenfield data).

**Критичность:** без PackImport→Room сетка Home пуста — приложение не сможет загрузить ни один pack/lesson. Это жёсткая блокировка для всех вертикальных эпиков Wave 1-4.

## 2. Классификация

`classification: tech-task` — инфраструктурный слой данных.

## 3. Комплексность

`complexity: L` (t-shirt). Причины:
- 5 форматов контента (Lesson CSV, Verb CSV, Vocab CSV, Story MD+JSON, bg_vocab CSV) → 5 парсеров → Room-сущности;
- manifest schema v1/v2 (миграция формата);
- Backup/Restore через SAF — новая имплементация (legacy был YAML-based);
- частичные импорты (partial-import confirmation), delete/reset/create-empty flows.

Risk-triggers:
- `data-integrity-risk`: атомарность импорта (либо весь pack, либо ничего) — наследие AtomicFileWriter, в v2 = Room transaction;
- `shared-mutation-risk`: ContentRepositoryImpl — источник данных для ВСЕХ вертикальных эпиков.

## 4. Гипотезы

- H1: доменные порты (ContentRepository, MasteryRepository и др.) уже зафиксированы в SRS-001 (E01) → Data layer пишет реализации под известные контракты.
- H2: legacy PackImporter (`data/PackImporter.kt`, `CsvParser`, `VerbDrillCsvParser`, `VocabCsvParser`, `ItalianDrillVocabParser`, `MultilingualStoryParser`, `BgVocabCsvParser`, `StoryQuizParser`) — рабочий референс; логика парсинга переносится, формат хранения меняется с YAML/CSV-файлов на Room-сущности.
- H3: 29 Room-сущностей + 6 DAO уже определены в v2 (saga note id=2) → нужно только наполнить их данными через import-flow.

## 5. Quality-gate чек-лист

- [ ] PackImport→Room: `importPackFromUri` (SAF), `importPackFromAssets`, `importLessonFromUri` работают.
- [ ] manifest schema v1 и v2 оба парсятся.
- [ ] 5 контент-парсеров (Lesson/Verb/Vocab/Story/BgVocab) наполняют соответствующие DAO.
- [ ] Атомарность: `database.withTransaction {}` — сбой на середине = rollback, ничего не сохраняется.
- [ ] Partial-import confirm, deletePack, resetAndReimport, createEmptyLesson работают.
- [ ] Backup/Restore: Room snapshot в SAF, restore-flow на MainActivity.onCreate.
- [ ] YamlToRoomMigrator удалён или `@Deprecated` (согласовано с E01 FR-13).
- [ ] LanguageManager.ensureSeedData (en/it/de/zh/ru/el).
- [ ] Home grid отображает импортированные pack'и (SM-критично).

## 6. Open questions

- OQ-1: Backup формат — Room DB file (WAL checkpoint) или JSON-экспорт сущностей? Решается в SRS.
- OQ-2: lesson_progress (legacy migrator skippped) — реализовать fresh в Room как PackLessonProgressStore-аналог? Да (см. SRS).

## 7. Decision-matrix

Внутри-эпизодная развилка отсутствует — путь однозначный: реализовать import/backup через Room. Decision-fork не запускается.

## 8. Decision

```
decision: go
reasoning: E02 — жёсткая блокировка для всего приложения (Home grid пуст без PackImport);
           ContentRepositoryImpl нужен всем вертикальным эпикам; upstream порты готовы
           (SRS-001 E01). Greenfield data (Q3) упрощает: мигратор не нужен, только свежий импорт.
source: direct sponsor interaction (note id=28) + product-level discovery (note id=4)
coverage: 1.0
```

## 9. Затронутые проекты/репозитории

- `BaseGrammy` (repository_id=7), ветка `dev` / feature `feature/req-002-data-room`.
- Затрагивает: `app/.../v2/core/data/repository/` (новые/завершаемые *Impl), `app/.../v2/core/data/local/` (DAOs наполнение), `app/.../v2/core/data/migration/YamlToRoomMigrator.kt` (удалить/deprecate).

## 10. Verdict-блок

```
VERDICT: go | REASONING: жёсткая блокировка Home grid + downstream; upstream готов; Q3 упрощает | override? (y)
```

Спонсор подтвердил go на product-level discovery. E02 = часть одобренного плана Modified D'.

## 11. Failover

- saga-mcp tracker DB доступна.
- Subagents доступны (saga-product/architect/analyst).
- Source = direct sponsor + product discovery. Degraded не активен.

## 12. Shared-mutation-risk

`ContentRepositoryImpl` и `PackImporter` — источник данных для всех вертикальных эпиков. Любая Room-схема, изменённая после Wave 1 = миграция БД + drift.

**Mitigation:** Room schema зафиксирована в SRS-002 (saga-architect); WAL + exportSchema=true уже включены в v2; schemaLocation = `$projectDir/schemas`. AC покрывают стабильность схемы.
