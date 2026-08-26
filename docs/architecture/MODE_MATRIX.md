# Mode Matrix — исполняемый контракт режимов тренировки

**План:** `REFACTORING_PLAN_2026-08-26.md` §3.4 — «до миграции любого режима заполняется
исполнимая mode matrix». Строка режима считается готовой, только когда каждая клетка
ссылается на проверяемое утверждение (тест/инвариант), а не на «TBD».

Статус строк:

| Режим | Строка | Статус |
|---|---|---|
| Normal lesson (LESSON) | [ниже](#normal-lesson) | **заполнена (Фаза 1)** |
| Sequential/Mixed review (ALL_SEQUENTIAL / ALL_MIXED) | — | TBD — гейт Фазы 4, срез 1 |
| Verb drill + aux drill | — | TBD — гейт Фазы 4, срез 2 |
| Vocab drill | — | TBD — гейт Фазы 4, срез 3 (требует решения по pack-scoping `word_mastery`) |
| Daily translate/vocab/verbs | — | TBD — гейт Фазы 4, срез 4 |
| Boss/mega/elite | — | TBD — гейт Фазы 4, срез 5 |
| Story reader/quiz | — | TBD — гейт Фазы 4, срез 6 |
| Pomodoro | — | TBD — гейт Фазы 4, срез 7 |

Строки `TrainingMode` (3 значения), `TrainingScreenMode` (8), `CardType`, `DailyBlockType`
и `BlockRenderVia` — независимые измерения; их типизированное объединение (launch/policy
mapping) выполняется по мере заполнения строк, не заранее.

---

## Normal lesson

Единственный production-режок Фазы 1 (golden journey: fresh install → урок →
completion → resume). Владелец мутаций — `SessionEngine`; presentation не
вызывает mutating API `SessionRepository` (правило плана §3.1.2/§3.1.3).

| Поле контракта | Решение | Проверяемое утверждение |
|---|---|---|
| **Identity** | `sessionId = SessionId.forLesson(packId, lessonId)` → `lesson:{packId}:{lessonId}`; контент-версия — `pack.version` (политика реимпорта — Фаза 5); маршрут-источник: Home → PackContent → `training/{packId}/{lessonId}`; resume-ключ — сам `sessionId` | `TrainingViewModelRegressionTest.init_resume_…`; `SessionEngineResumeRegressionTest` |
| **Selection** | Все карты урока по `ord` (`ContentRepository.getCards`) минус скрытые (`UserContentRepository.getHiddenCardIds`), нарезка `SubLessonScheduler.buildSubLessons(sessionSize=TrainingConfig.SUB_LESSON_SIZE_DEFAULT=10)`; активный под-урок #0 для новой сессии | `init_lessonWithCards_persistsSessionWithNonEmptyPool` (пул = срез карт урока, порядок сохранён) |
| **Ordering** | Sequential (ord), без перемешивания, без seed; пул фиксируется при старте и хранится в снимке целиком | инвариант `currentCardId ∈ poolCardIds` (`SessionEngine` KDoc-инварианты 1–3) |
| **Exercise** | Перевод RU → текстовый ответ; input mode: KEYBOARD (VOICE/WORD_BANK — поздние фазы); рендерер — `PromptCard` + `OutlinedTextField` | `TrainingScreenTestTags` semantics |
| **Attempt** | Одна submit-попытка закрывает карточку прохода; валидация — `AnswerValidator.validate` (Normalizer: trim/lowercase/диакритики/пунктуация/апострофы; альтернативы через `+`); **hint** — эфемерный UI (маска первой буквы); persist `hintCount` и attempt-семантика — Фаза 2; **skip** — advance без shown-метки и без счётчиков | `AnswerValidatorTest`; `submit_…` VM-тесты |
| **Progress** | numerator = `correctCount + incorrectCount` (закрытые карточки прохода), denominator = `poolCardIds.size`; `shown` — только submit с input mode ≠ WORD_BANK (`SessionEngine.submitAnswer` gate) | `SessionEngine` инвариант 4/5; VM-тесты прогресса |
| **Mastery** | Submit KEYBOARD/VOICE → хук `onMarkShown` (в Фазе 1 — no-op: wiring `MasteryRepository.recordCardShow` входит в транзакционный координатор Фазы 2 — ADR-001 pre-mortem №3); WORD_BANK/hint/skip mastery не меняют | инвариант 6 `SessionEngine`; wiring — Фаза 2 |
| **Completion** | Полный проход пула: Next/Skip с последней карты пула → `completeSession` (status=COMPLETED); summary = correct/incorrect/pool; next action — возврат к списку уроков | `next_afterLastCard_completesSession`; повторный вход после COMPLETED — свежая сессия с тем же PK |
| **Persistence** | Каждый submit/next/hide — одна `saveSession` (снимок целиком: pool + shown + currentCardId + счётчики, одна транзакция Room `SessionDao.saveSnapshot`); атомарность «answer + shown» — инвариант 4 | `SessionRepositoryContractTest.saveLoadRoundtrip…`; failure-injection — Фаза 2 |
| **Navigation** | Back (toolbar/system — одна семантика): выход без подтверждения, сессия остаётся ACTIVE (авто-pause), снимок уже durable → повторный вход resume'ит тот же PK с той же картой; abandon-диалог не нужен — потерь ответов нет | `init_resume_returnsSameCardAndPk`; rotation/process-death — gate Фазы 1 (E2E), unit-уровень — draft в SavedStateHandle |

ИзвестныеFollow-ups строки (не блокируют Фазу 1, зарегистрированы планом):

- `hintCount`/attempt-семантика и `SessionState.HINT_SHOWN` — Фаза 2 (входит в atomic submit).
- `SessionStatus.PAUSED` не используется: Back оставляет ACTIVE; явный PAUSED появится,
  если появится семантика «пауза ≠ выход» (Фаза 3, UX shell).
- `cursorIndex` и `currentCardId` пишутся раздельно (`setCurrentCard` не двигает курсор) —
  консолидация запланирована Фазой 2.
