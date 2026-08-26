# Mode Matrix — исполняемый контракт режимов тренировки

**План:** `REFACTORING_PLAN_2026-08-26.md` §3.4 — «до миграции любого режима заполняется
исполнимая mode matrix». Строка режима считается готовой, только когда каждая клетка
ссылается на проверяемое утверждение (тест/инвариант), а не на «TBD».

Статус строк:

| Режим | Строка | Статус |
|---|---|---|
| Normal lesson (LESSON) | [ниже](#normal-lesson) | **заполнена (Фаза 1)** |
| Sequential/Mixed review (ALL_SEQUENTIAL / ALL_MIXED) | [ниже](#sequentialmixed-review) | **заполнена (Фаза 4, срез 1)** |
| Verb drill + aux drill | [ниже](#verb-drill) | **verb drill заполнен (Фаза 4, срез 2)**; aux — TBD |
| Vocab drill | [ниже](#vocab-drill) | **заполнена (Фаза 4, срез 3; ADR-003 реализован)** |
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
| **Attempt** | Одна submit-попытка закрывает карточку прохода — повторный submit той же карты идемпотентен (Engine-guard по shown-set, Фаза 2); валидация — `AnswerValidator.validate` (Normalizer: trim/lowercase/диакритики/пунктуация/апострофы; альтернативы через `+`); **hint** — эфемерный UI (маска первой буквы); persist `hintCount` — открытый follow-up; **skip** — advance без shown-метки и без счётчиков | `AnswerValidatorTest`; `SessionEnginePropertyTest.double submit counts exactly once`; `submit_…` VM-тесты |
| **Progress** | numerator = `correctCount + incorrectCount` (закрытые карточки прохода), denominator = `poolCardIds.size`; `shown` — только submit с input mode ≠ WORD_BANK (`SessionEngine.submitAnswer` gate) | `SessionEngine` инвариант 4/5; VM-тесты прогресса |
| **Mastery** | Submit KEYBOARD/VOICE → хук `onMarkShown` с pack/lesson-контекстом → `MasteryRepository.recordCardShow` В ТОЙ ЖЕ Room-транзакции, что и снимок (координатор `RoomSessionCommitCoordinator`, Фаза 2 — ADR-001 pre-mortem №3 закрыт); completion → `markLessonCompleted` той же транзакцией; WORD_BANK/hint/skip mastery не меняют | `RoomSessionAtomicCommitTest` (3 теста: rollback ×2 + happy-path «сессия+mastery вместе») |
| **Completion** | Полный проход пула: Next/Skip с последней карты пула → `completeSession` (status=COMPLETED) + completion-хук одной транзакцией; summary = correct/incorrect/pool; next action — возврат к списку уроков | `next_afterLastCard_completesSession`; повторный вход после COMPLETED — свежая сессия с тем же PK |
| **Persistence** | Каждый submit/next/hide — одна `saveSession`: ревизия `revision+1` (stale-защита), hot updates (обычный Submit/Next НЕ переписывает pool/shown — `@Upsert` вместо REPLACE-cascade, diff вставки/удаления); битые enum-значения → typed `SessionCorruptionException` | `SessionRepositoryContractSpec` (fake+Room parity); `SessionHotUpdateTest`; `GrammarMateMigrationTest` (schema v2) |
| **Navigation** | Back (toolbar/system — одна семантика): выход без подтверждения, сессия остаётся ACTIVE (авто-pause), снимок уже durable → повторный вход resume'ит тот же PK с той же картой; abandon-диалог не нужен — потерь ответов нет | `init_resume_returnsSameCardAndPk`; rotation/process-death — gate Фазы 1 (E2E), unit-уровень — draft в SavedStateHandle |

Известные follow-ups строки (не блокируют golden journey, зарегистрированы планом):

- `hintCount`-persist и `SessionState.HINT_SHOWN` — единственный оставшийся пункт
  Attempt-клетки (attempt-семантика «один submit закрывает карту» закрыта Фазой 2).
- `SessionStatus.PAUSED` не используется: Back оставляет ACTIVE; явный PAUSED появится,
  если появится семантика «пауза ≠ выход» (Фаза 3, UX shell).
- ~~`cursorIndex` и `currentCardId` пишутся раздельно~~ — закрыто Фазой 2: `cursorIndex`
  удалён из снимка, в БД пишется производная `pool.indexOf(currentCardId)`.
- Streak/progress-агрегация при completion — Фазы 4/5 (вместе с режимами, которым
  она нужна; mastery/completion уже атомарны — координатор Фазы 2).

---

## Sequential/Mixed review

Фаза 4, срез 1 (2026-08-26). Mixed review = повторение завершённого урока с
чередованием половин пула — ломает механическое запоминание последовательности.
Это НЕ SRS-driven повторение: интервалы «когда повторять» — слой 2 ADR-002
(после гейта FSRS); здесь только детерминированный порядок пула.

| Поле контракта | Решение | Проверяемое утверждение |
|---|---|---|
| **Identity** | Тот же `sessionId = SessionId.forLesson(packId, lessonId)`, `snapshot.mode = ALL_MIXED` (маркер прохода в снимке); источник — Completed-экран тренировки (`training_repeat_mixed_button`) | `repeatMixedFromCompleted_rebuildsInterleavedPool` (persisted mode) |
| **Selection** | Как Normal lesson (все карты урока минус скрытые); различие — только Ordering | `startLessonSession ALL_MIXED…` (SessionEngineRestartTest) |
| **Ordering** | [LessonOrderPolicy]: LESSON/ALL_SEQUENTIAL — порядок `ord`; ALL_MIXED — чередование половин `a₁b₁a₂b₂…`, детерминировано без random/seed; пул фиксируется на старте и персистится в снимке (никакой рантайм-пересборки v1 — источник card_15) | `LessonOrderPolicyTest` (детерминизм, точный порядок, полнота множества, короткие входы) |
| **Exercise** | тот же рендерер/ввод, что Normal lesson | `TrainingScreenTest.completed_repeatMixedButton_startsInterleavedPass` |
| **Attempt** | идентично Normal lesson | Normal lesson Attempt |
| **Progress** | идентично: numerator = correct+incorrect, denominator = pool.size | `repeatMixedFromCompleted_rebuildsInterleavedPool` (answeredCards=0 после рестарта) |
| **Mastery** | Повтор НЕ «разучивает» заново: shown/encounters идут как обычно через `onMarkShown`-хук, `completedAtMs` сохраняется (повтор не сбрасывает завершённость) | `RoomSessionAtomicCommitTest` (тот же координатор); restart не трогает mastery — `SessionEngineRestartTest.restart clears session context…` |
| **Completion** | Полный проход mixed-пула → COMPLETED тем же `nextCardOrComplete`; после — снова доступен mixed-повтор | `next_afterLastCard_completesSession` (режим-агностичен) |
| **Persistence** | `restartLessonSession(mode=ALL_MIXED)`: delete + fresh start тем же PK; ревизии/hot-updates — как Normal lesson | `restart with mode switches pool order…` |
| **Navigation** | Вход только из Completed; выход — как Normal lesson (Back = выход, сессия durable, re-enter resume'ит mixed-пул) | `repeatMixed_onlyAvailableInCompleted` (команда вне фазы — no-op) |

Follow-ups строки:

- `ALL_SEQUENTIAL` сейчас неотличим от `LESSON` (явного входа нет: «повтор по
  порядку» = re-enter COMPLETED-урока) — станет отдельным входом, если
  product-семантика «повтор по порядку» потребуется отдельно.
- SRS-driven review (due-уроки по `dueAtMs`) — слой 2 ADR-002, гейт Фазы 4+.

---

## Verb drill

Фаза 4, срез 2 (2026-08-26). Спряжение: промпт RU → ввод формы глагола.
Combo-фильтры (tense/group/person) персистятся в снимке — resume восстанавливает выбор.

| Поле контракта | Решение | Проверяемое утверждение |
|---|---|---|
| **Identity** | `SessionId.forVerbDrill(packId)`, `mode=VERB_DRILL`, `lessonId=null`; маршрут `verb_drill/{packId}` (typed requiredId) | `SessionEngineVerbDrillTest.pool is ranked…` |
| **Selection** | `ContentRepository.getVerbDrillCards(packId, tense?, group?, person?)` — combo-фильтры, null = всё | `init_buildsRankedPool_andShowsFirstPrompt` |
| **Ordering** | Частотность `rank` (null — в конец), детерминированно; `sessionSize` режет сверху | `sessionSize caps pool…` |
| **Exercise** | Промпт `promptRu` → ввод `answer` (KEYBOARD, AnswerValidator с `listOf(card.answer)`) | `submit_correct/wrong…` |
| **Attempt** | Один submit закрывает карту (идемпотентность — Engine-guard по shown, Фаза 2) | `SessionEnginePropertyTest.double submit…` |
| **Progress** | correct+incorrect / pool.size, как Normal lesson | `fullPass_completesSession` |
| **Mastery** | Drill не пишет lesson-mastery (`onMarkShown`-хук домена не привязан к drill-комбо — вычисляемое следствие combo-выборки; Словесный SRS — срез 3) | follow-up ниже |
| **Completion** | Полный проход пула → COMPLETED (`nextCardOrComplete`) | `fullPass_completesSession` |
| **Persistence** | Все commit'ы через SessionEngine (revision+1, hot-updates) — как Normal lesson | `SessionRepositoryContractSpec` |
| **Navigation** | Back = выход, сессия durable; повторный вход resume'ит combo | режим-агностичные контракты Фазы 1–2 |

Follow-ups строки: вход из PackContent по флагу `hasVerbDrill` манифеста (сейчас
маршрут не публикуется из UI); UI combo-селектора фильтров; контракт
Repeat/Continue/Reset (CLAUDE.md) — `VerbDrillLastSession`-модель есть, входов ещё нет;
aux drill — по образцу (порт `getAuxDrillCards`).

---

## Vocab drill

Фаза 4, срез 3 (2026-08-26). Anki-style карточки слов; режим БЕЗ SessionEngine-
сессий: durable-состояние — сам word-SRS (`recordWordReview` фиксирует каждый
ответ немедленно, ADR-003 pack-scoped). Батч детерминирован: due-слова пака
(самые просроченные) + добор новыми по рангу частотности, [BATCH_SIZE]=10.

| Поле контракта | Решение | Проверяемое утверждение |
|---|---|---|
| **Identity** | Маршрут `vocab_drill/{packId}` (typed requiredId); сессионного PK НЕТ по дизайну — идентичность = `(packId, wordId)` в word_mastery | `blankRoute_showsErrorWithoutLoading` |
| **Selection** | `observeDueWords(packId, 10)` (ADR-003: фильтр по паку) + `getVocabWords(packId)` минус reviewed-и-не-due | `init_batchIsDueFirstThenFreshByRank` |
| **Ordering** | due-первыми (по возрастанию nextReviewDateMs), затем новые по rank; порядок фиксируется при загрузке батча | `init_batchIsDueFirstThenFreshByRank` |
| **Exercise** | Question (слово, перевод скрыт) → Revealed (перевод + Знаю/Не знаю); реколл Meaning, без набора текста | `reveal_showsTranslation_withoutRecording` |
| **Attempt** | Один ответ на слово за батч: `answer()` фиксирует review и двигает позицию; reveal НЕ пишет SRS | `answer_recordsReview_thenAdvances`; co-verify 0 записей на reveal |
| **Progress** | index/total по батчу; Done = reviewed/correct | `fullBatch_endsInDoneWithCounts` |
| **Mastery** | `recordWordReview(packId, wordId, isCorrect)` — лестница (слой 1 ADR-002; фикс off-by-one: первый верный → 1 день) | `GrammarMateMigrationTest` (schema v3) + impl-фикс в a5139838a |
| **Completion** | Конец батча → Done (терминальное для входа); следующий вход соберёт новый батч из due+новых | `fullBatch_endsInDoneWithCounts` |
| **Persistence** | Каждый ответ — отдельный durable-commit до продвижения (§3.1.5: ошибка → Error, позиция не двигается) | `answer_recordsReview_thenAdvances` (advance после verify) |
| **Navigation** | Back = выход в любой момент (данные уже durable пофразово) | контракты Фазы 3 (режим-агностичны) |

Follow-ups строки: вход из PackContent по `hasVocabDrill` (маршрут не публикуется
из UI до флага манифеста); Voice-режим карточки; `bg_vocab_marks`/`bg_vocab_position`
(тёмный v1-контур) — Фаза 5.
