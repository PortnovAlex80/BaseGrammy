# Discovery Brief — REQ-001 Завершение доменного ядра + извлечение модуля :domain

**Эпизод:** E01 (epic_id=85), проект GrammarMate (id=25)
**Волна:** 0 — Фундамент
**Стадия:** Discovery → Formalization
**Дата:** 2026-07-07
**Источник:** product-level discovery (saga note id=4, id=28), решения спонсора

---

## 1. Бизнес-цели

Завершить доменный слой (`v2/core/domain`) до полного покрытия функционала legacy-приложения (217 файлов, ветка `main`) и **извлечь его в отдельный Gradle-модуль `:domain`** как pure-Kotlin модуль без Android-зависимостей.

Контекст: ветка `rewrite/v2-clean-architecture` содержит частично готовую v2-архитектуру (domain/Room/MVI/FSRS-v6/session-engine/migrator, 72 теста зелёные), но ~80% функциональной поверхности legacy отсутствует. Спонсор подтвердил: **v2 = база, достраиваем пробелы, не переписываем готовое** (saga note id=28, Q2).

Цель E01 — подготовить чистое, полное, zero-Android доменное ядро, на которое будут опираться все вертикальные эпики Wave 1-4 (Training/Verb/Vocab/Daily/Story/Gamification/Boss/Pomodoro).

## 2. Классификация

`classification: tech-task` — завершение архитектурного фундамента, не новая пользовательская фича.

## 3. Комплексность

`complexity: M` (t-shirt). Причины:
- затрагивает существующий regression-locked код (72 теста) — риск регрессии при переносе в новый модуль;
- требует аудита 217 legacy-файлов на предмет отсутствующих доменных моделей/портов;
- multi-module extraction (settings.gradle.kts, build.gradle.kts, dependency wiring).

Risk-triggers:
- `regression-risk`: перенос FSRS-v6 / SessionEngine / CardSessionStateMachine в `:domain` может сломать 72 зелёных теста;
- `shared-mutation-risk`: `:domain` будет зависимостью ВСЕХ вертикальных эпиков → контракт портов должен быть стабилен до Wave 1.

## 4. Гипотезы

- H1: существующий `v2/core/domain` уже имеет ZERO Android-импортов (подтверждено note id=2) → механический перенос в `:domain` безопасен.
- H2:legacy-функционал, отсутствующий в домене, конечен и перечислим (DailyTask composers, SubLesson completion, BadSentence port, Gamification entities, Story models, WordScript segments).
- H3: после извлечения `:domain` 72 существующих теста останутся зелёными (тесты переносятся вместе с кодом).

## 5. Quality-gate чек-лист

- [ ] `:domain` Gradle-модуль создан, `settings.gradle.kts` включает `include(":domain")`.
- [ ] `:app` зависит от `:domain` (`implementation(project(":domain"))`).
- [ ] `grep -R "android\." domain/src` возвращает 0 строк (zero Android imports).
- [ ] 72 существующих теста переносятся в `:domain` и проходят (`build.bat test` зелёный).
- [ ] Аудит legacy: список отсутствующих доменных моделей/портов зафиксирован.
- [ ] Отсутствующие модели/порты добавлены в `:domain`.
- [ ] YamlToRoomMigrator помечен `@Deprecated` или удалён (спонсор Q3 = greenfield data).

## 6. Open questions (парковка, non-blocking)

- OQ-1: Выносить ли `TrainingConfig` (object констант) в `:domain` или оставить в `:app`? Решение: в `:domain` (он часть доменного контракта).
- OQ-2: Value-class IDs (`PackId`, `LessonId`, ...) — переносить как есть или реорганизовать? Решение: как есть, они уже pure Kotlin.

## 7. Decision-matrix (product-level, см. note id=3)

Декомпозиция всего продукта выбрана на product-discovery. Для E01 внутри-эпизодная развилка отсутствует — путь однозначный: завершить домен + извлечь модуль. Decision-fork не запускается (менее 3 валидных вариантов).

## 8. Decision

```
decision: go
reasoning: E01 — критический путь для всех вертикальных эпиков Wave 1-4; без
           завершённого домена и стабильных портов невозможно запустить
           Training/Verb/Vocab/Daily. Существующая v2-domain (72 теста) —
           надёжная база; работа = завершение пробелов + механическое
           извлечение модуля, не greenfield.
source: direct sponsor interaction (saga note id=28, VERDICT-CONFIRMED go)
coverage: 1.0
```

## 9. Затронутые проекты/репозитории

- `BaseGrammy` (repository_id=7), ветка `dev` (integration), feature-ветка `feature/req-001-domain-core`.
- Затрагивает: `app/build.gradle.kts`, `settings.gradle.kts`, новый `domain/build.gradle.kts`, перенос `app/src/main/java/com/alexpo/grammermate/v2/core/domain/*` → `domain/src/main/java/...`.

## 10. Верdict-блок

```
VERDICT: go | REASONING: критический путь Wave 0, база 72 теста зелёные, работа = завершение+извлечение | override? (y)
```

Спонсор подтвердил `go` на product-level discovery (note id=28). На эпизодном уровне пере-подтверждения не требуется — E01 прямо следует из одобренного плана Modified D'.

## 11. Failover

- saga-mcp tracker DB доступна (проверено: epic_id=85, episode_status вернул стадию).
- Subagents доступны (Agent tool) — formalization пойдёт через канонические роли saga-product/architect/analyst.
- Source = db.sqlite + direct sponsor interaction. Degraded-режим не активен.

## 12. Shared-mutation-risk

`:domain` становится общей зависимостью всех вертикальных эпиков. Контракт портов (ContentRepository, SessionRepository, MasteryRepository, ProgressRepository, UserContentRepository, VocabDrillRepository, SettingsRepository, AudioRepository, AudioModelRepository) **должен быть зафиксирован в SRS E01** до старта Wave 1. Любое изменение порта после старта Wave 1 = drift.

**Mitigation:** порты финализируются в SRS (saga-architect), AC покрывают стабильность контракта, любая новая модель добавляется в `:domain` через отдельную задачу с trace `derived_from` к SRS.
