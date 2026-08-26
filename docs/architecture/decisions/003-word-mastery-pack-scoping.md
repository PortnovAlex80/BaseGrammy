# 003. word_mastery: pack-scoping (gap #4 аудита 2026-08-26)

- **Status:** Accepted
- **Date:** 2026-08-26
- **Supersedes:** none
- **Контекст плана:** Фаза 4, срез 3 (vocab drill) — «требует решения по
  pack-scoping word_mastery» (MODE_MATRIX, статус-таблица)

## Context

`word_mastery` — единственная user-state таблица схемы БЕЗ packId: PK `wordId`
глобальный (`DrillEntities.kt`). Контент слов при этом pack-scoped
(`vocab_words` unique(packId, word)). Следствия (аудит, H-2/M-1):

- одно слово в двух паках делит одну SRS-запись: `recordWordReview` по паку A
  перезаписывает интервалы пака B;
- `observeDueWords` — `WHERE nextReviewDateMs <= :now` без фильтра по паку:
  Review-список смешивает все паки, включая неактивные;
- `wordId` строится как `pos_rank_word` — коллизии между паками уже на уровне
  контента (аудит, C-1/H-4).

Окно для смены ключа закрывается первым реальным пользователем: БД schema v2,
реальных установок нет — сейчас смена PK стоит дёшево (additive-миграция с
пересборкой таблицы), позже — только с потерей/дедупликацией данных.

## Decision

1. **PK `word_mastery` становится составным: `(packId, wordId)`.** Семантика
   SRS-состояния слова — «внутри пака», как у всей остальной user-state схемы
   (`mastery_states`, `drill_progress`, `chapter_progress`).
2. **Schema v3, миграция v2→v3**: `ALTER TABLE` добавить `packId TEXT NOT NULL
   DEFAULT ''` нельзя с новым PK — миграция через пересборку (CREATE new →
   INSERT SELECT с packId из `vocab_words` по слову; строки без матча — сироты
   несуществующего контента, удаляются; зелёная field-БД это не затрагивает
   (fixture-тест миграции).
3. **`observeDueWords(now, packId, limit)`** — фильтр по паку обязателен;
   сигнатура порта `VocabDrillRepository` меняется вместе со срезом 3.
4. **`recordWordReview(packId, wordId, …)`** — пишет в scoped-строку.
5. Vocab drill (срез 3) migrating на FSRS НЕ начинается в этом срезе — слой 2
   ADR-002; лестница остаётся до гейта (гэп off-by-one нового слова из аудита
   чинится в срезе 3 как behavior-fix лестницы).

## Alternatives considered

- **Глобальный wordId с префиксом пака (`pack:word`) без смены PK** — отклонено:
  лечит симптом, ломает совместимость ID с контентом (`vocab_words.id`),
  оставляет двойной источник правды в кодировке ключа.
- **Оставить как есть до Фазы 5** — отклонено: срез 3 (vocab drill UI) лёг бы
  поверх дырявого контракта; окно дешёвой миграции закрывается.

## Consequences

- Положительные: SRS слов не течёт между паками; Review-выборка честная;
  единая pack-scoped семантика user-state.
- Отрицательные: table-rebuild миграция (осторожность + fixture-тест);
  `bg_vocab_marks` (PK word) — тот же вопрос, решается тем же срезом.
- Follow-ups: миграция v2→v3 + `GrammarMateMigrationTest`-кейс; смена
  `VocabDrillRepository` сигнатур; строка «Vocab drill» mode matrix.

## Decision Journal

**Check trigger:** срез 3 Фазы 4 (немедленная реализация); контрольная точка
2026-11-26. **What would change:** появление межпаковых «общих слов» как
продуктовой фичи (тогда глобальный SRS — отдельный ADR поверх).
