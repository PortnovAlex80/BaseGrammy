# 002. SRS: единый источник истины (staged)

- **Status:** Accepted
- **Date:** 2026-08-26
- **Supersedes:** none
- **Superseded by:** none
- **Решение принято в рамках:** REFACTORING_PLAN_2026-08-26.md Фаза 3 (снятие блокера «реактивный прогресс Home — после ADR-002»)

## Context

В кодовой базе сосуществуют три модели «памяти» (аудит 2026-08-26):

1. **Legacy-лестница** `intervalStepIndex` (10 шагов, 1..56 дней) — питает
   `FlowerCalculator`/`LessonLadderCalculator`/`PackProgressCalculator`;
   в production с Фазы 2 **заморожена** (`MasteryRepositoryImpl`: «legacy-лестница
   не пересчитывается на показе»).
2. **FSRS v6** (`SrsScheduler`, D/S/R, полная реализация + 43 теста) — **не вызывается
   ни одним production-путём**: `updateSrsState` есть в порте, но вызовов из
   SessionEngine/ViewModel нет; `fsrsStateJson` пишется только мигратором (отключён).
3. **Кривая Эббингауза в цветке** — формула внутри FlowerCalculator поверх
   замороженного шага лестницы.

Что реально гоняет прогресс пользователя сегодня (Фазы 1–2): счётчики
`uniqueCardShows`/`completedAtMs` + атомарный commit через
`RoomSessionCommitCoordinator`. Ни одна SRS-модель не влияет на UI.

Одновременно аудит-2026 зафиксировал дефекты FSRS-ядра, которые нельзя
импортировать в production без исправления: `learningStep()` захардкожен в 0
(GOOD не выпускает из LEARNING), short-term формула `S^−w19` вне экспоненты,
`elapsed` floor до целых дней (R завышен), `SrsMigration` ставит
`dueAt=lastReview=now` (лавина due + S не растёт на первом ответе).

## Decision drivers

| Driver | Weight |
|---|---:|
| Немедленная определимость агрегатов (Home-прогресс Фазы 3) | 3 |
| Не импортировать известные баги FSRS в production | 3 |
| Инкрементальность без big-bang миграции (ADR-001) | 2 |
| Обратимость/отложенность решения | 2 |

## Decision

**Двухслойная истина с гейтом активации FSRS.**

### Слой 1 — истина production (Фазы 3–4, начиная с сейчас)

Прогресс урока = **счётчики и факты**: `uniqueCardShows`, `completedAtMs`,
`correct/incorrectCount`. Это единственные величины, которые читает и пишет
production-путь (SessionEngine + RoomSessionCommitCoordinator).

Следствия:

- **Home/Chapter-прогресс** считается из `completedAtMs`-агрегатов
  (`completedLessons / totalLessons` per pack) — без SRS-математики.
- `intervalStepIndex` — **display-only legacy**: читается, но не пишется;
  помечен к удалению в Фазе 7 после активации слоя 2.
- `FlowerCalculator`/`LessonLadderCalculator` НЕ подключаются к production-UI
  до активации слоя 2 (сегодня их вывод никем не потребляется — фиксируем это
  как контракт, а не случайность).

### Слой 2 — назначение: FSRS v6 — единственный источник планирования

`SrsScheduler` становится источником истины для «когда повторять» и «насколько
прочно» **не раньше**, чем одновременно:

1. Починены дефекты ядра: `learningStep` (GOOD выпускает в REVIEW),
   short-term формула (единая экспонента), дробный `elapsed`,
   `SrsMigration` (due/lastReview из реальных дат);
2. Пересчёт встроен в атомарный commit (тот же `RoomSessionCommitCoordinator`):
   submit → `SrsScheduler.schedule` → `fsrsStateJson` одной транзакцией;
3. Vertical slice одного режима (Phase 4, review-mixed) доказал формулы на
   реальных данных (characterization против legacy-лестницы).

До выполнения всех трёх условий любая проводка FSRS в UI = drift-задача.

### Vocab drill

Словесный SRS (`word_mastery`, лестница) мигрирует на FSRS **в своём вертикальном
срезе Фазы 4** (vocab drill), не раньше и не отдельным big-bang.

## Alternatives considered

- **A. Большая миграция на FSRS сейчас** — отклонено: импортирует 4 известных
  бага ядра в единственный источник истины; требует правки FlowerCalculator,
  миграции данных и mode-matrix одновременно.
- **B. Легаси-лестница как истина навсегда** — отклонено: лестница заморожена и
  не пересчитывается (расчёт расходится с реальностью — аудит, finding #3);
  возрождение требует вернуть её пересчёт и всё равно не решает vocab.

## Consequences

**Positive:** Home-прогресс Фазы 3 разблокирован без SRS-математики; известные
баги FSRS не попадают в production; миграция — вертикальными срезами.

**Negative:** до активации слоя 2 «кривая цветка» остаётся мёртвым кодом с
тестами (сопровождение); два контракта прогресса (сейчас/потом) нужно держать
в голове.

**Neutral / follow-ups:** активация слоя 2 — отдельная задача с gate-тестами;
`intervalStepIndex`-удаление — Фаза 7; вопрос pack-scoping `word_mastery`
(gap #4 аудита) решается в срезе vocab Фазы 4.

## Decision Journal

**Date:** 2026-08-26

**Ex-ante expectations:**
- Фаза 3: Home показывает реактивный `completed/total` без SRS-зависимостей.
- Фаза 4: review-режим подключает FSRS только после пунктов гейта 1–3.
- Фаза 7: legacy-лестница и Ebbinghaus-дубликаты удалены или подключены к FSRS.

**Check trigger:** 2026-11-26, или раньше — если режиму Фазы 4 понадобится
«when-to-review» раньше выполнения гейта.

**What would change the decision:** доказанная невозможность починки FSRS-ядра
(напр., расхождение с py-fsrs эталонами) → возврат к варианту A/B с новым ADR.

## References

- `domain/src/main/java/com/alexpo/grammermate/domain/srs/SrsScheduler.kt`
- `app/src/main/java/com/alexpo/grammermate/v2/core/data/repository/MasteryRepositoryImpl.kt`
- Аудит 2026-08-26 (сессия стабилизации), находки SRS #1–#11
- `001-incremental-session-engine-integration.md`
