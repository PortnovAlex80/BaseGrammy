# SRS-001 — Доменный контракт + извлечение Gradle-модуля `:domain`

**Эпизод:** E01 (epic_id=85), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (SRS)
**Дата:** 2026-07-07
**Status:** Accepted

**Артефакт-источник:** PRD-001 (artifact_id=439, `01-prd.md`, status=accepted).
**Repository:** BaseGrammy (repository_id=7), ветка `rewrite/v2-clean-architecture`.
**Код:** SRS-001

> Источник истины сигнатур — **фактический код** `v2/core/domain/*` в репозитории
> (verified 2026-07-07). PRD фиксирует продуктовые границы; SRS фиксирует технический
> системный контракт (порты, модели, модульная структура). Все сигнатуры ниже — это
> реальные сигнатуры из кода, перенесённые как есть (regression-locked).

---

## 1. Архитектурный обзор

### 1.1. Положение `:domain` в общей архитектуре

`GrammarMate v2` строится по чистой слоистой архитектуре (см.
`docs/v2-architecture/ARCHITECTURE.md`). Слои сверху вниз:

```
┌─────────────────────────────────────────────────────┐
│  UI (app:ui)  — Compose M3 Adaptive экраны           │
├─────────────────────────────────────────────────────┤
│  Presentation MVI  — Intent → Reducer → State        │
├─────────────────────────────────────────────────────┤
│  Domain (:domain — pure Kotlin, НЕТ Android)         │  ← ЭТА SRS
│  UseCase-ы, доменные модели, FSRS scheduler,         │
│  SessionEngine (currentCardId как PK), SrsScheduler  │
├─────────────────────────────────────────────────────┤
│  Data — Room (user-state) | Files (content packs) |  │
│         DataStore (settings) | Repositories impl     │
├─────────────────────────────────────────────────────┤
│  Platform — Room/SQLite | FileSystem | DataStore |   │
│              Sherpa-ONNX                              │
└─────────────────────────────────────────────────────┘
```

Цель E01 — вынести рамку «Domain» на рисунке в **физически отдельный Gradle-модуль**
`:domain` (pure Kotlin/JVM), чтобы:

1. **Зафиксировать контракт портов и моделей** до старта Wave 1–4 (Training, Verb,
   Vocab, Daily, Story, Gamification, Boss, Pomodoro). Без этого любой вертикальный
   эпик столкнётся с drift-контрактом: ему придётся менять домен «на ходу».
2. **Гарантировать zero-Android** на уровне компилятора (Kotlin/JVM plugin не
   резолвит `android.*`). Сегодня это обеспечивается только конвенцией (grep);
   физический модуль делает нарушение невозможным для сборки.
3. **Получить независимую единицу тестирования** — `./gradlew :domain:test`
   запускает 314 `@Test`-функций (17 классов) на чистой JVM, без эмулятора.

### 1.2. Правила слоёв (зафиксированы ARCHITECTURE.md)

- **Domain** — pure Kotlin-модуль, ноль Android-импортов. Тестируется на чистой JVM.
- **Data** — реализует интерфейсы портов из domain. Room-entities — детали
  реализации, домен о них не знает.
- **Presentation** — MVI: `ViewState` immutable, `Intent` sealed, `Reducer` чистая
  функция.
- **UI** — глупые composables, только `state → UI`, `action → intent`.

Зависимость направлена строго внутрь: `:app → :domain`. `:domain` не зависит ни от
`:app`, ни от Android, ни от Room/Hilt/Sherpa.

### 1.3. Контекст бага `card_15` (первопричина SRS-контракта SessionEngine)

В v1 текущая карточка сессии идентифицировалась индексом массива, а пул пересобирался
в рантайме → после resume индекс указывал на чужую карту, а `card_15` терялась.
v2-инвариант: текущая карта — это **первичный ключ** (`SessionSnapshot.currentCardId`),
а пул хранится целиком в снимке; resume/persist выполняются атомарно (одна транзакция
Room). Этот инвариант — ядро контракта FR-3 и должен сохраниться при переносе в
`:domain` без изменений.

---

## 2. Структура модуля `:domain` (целевая)

### 2.1. Каталоги после извлечения

Перенос `app/src/main/java/com/alexpo/grammermate/v2/core/domain/*` →
`domain/src/main/java/com/alexpo/grammermate/domain/*` (пакет упрощён: убран
сегмент `v2.core` — модуль `:domain` сам по себе означает «v2 clean domain»).
Тесты зеркально: `app/src/test/.../v2/core/domain/*` →
`domain/src/test/java/com/alexpo/grammermate/domain/*`.

```
domain/
├── build.gradle.kts                 (FR-11) kotlin("jvm"), NO android plugin
└── src/
    ├── main/java/com/alexpo/grammermate/domain/
    │   ├── TrainingConfig.kt        (object констант)
    │   ├── audio/
    │   │   ├── AudioModelRepository.kt   (порт FR-7)
    │   │   └── AudioRepository.kt        (порт FR-7)
    │   ├── model/
    │   │   ├── AnswerResult.kt
    │   │   ├── Content.kt                (Language, Pack, Chapter, Lesson, Card,
    │   │   │                               GrammarChip, StoryQuiz, StoryQuestion)
    │   │   ├── DailyTask.kt              (sealed: TranslateSentence/VocabFlashcard/ConjugateVerb)
    │   │   ├── Drills.kt                 (VerbDrillCard, AuxDrillCard, VocabWord,
    │   │   │                               WordMasteryState, VocabDrillCard, ...)
    │   │   ├── Enums.kt                  (все enum + sealed: TrainingMode, InputMode,
    │   │   │                               BossType, SpeakSlot, Segment, ParseError, ...)
    │   │   ├── Flower.kt                 (FlowerState, FlowerVisual, PackFlowerVisual)
    │   │   ├── Gamification.kt           (BossState, EliteState, PomodoroState,
    │   │   │                               PomodoroSessionStats, LessonLadderRow)
    │   │   ├── Pack.kt                   (value-class IDs: PackId/LessonId/CardId/
    │   │   │                               LanguageId/ChapterId/SessionId)
    │   │   ├── Progress.kt               (LessonMastery, StreakData, ChapterProgress,
    │   │   │                               DrillProgress, DailyCursor, PomodoroHistoryEntry)
    │   │   ├── SessionSnapshot.kt        (атомарный снимок сессии — фикс card_15)
    │   │   ├── Settings.kt               (AppConfig)
    │   │   ├── UserContent.kt            (HiddenCard, BadSentence, BgVocabWord,
    │   │   │                               BgVocabMarkEntry, UserProfile)
    │   │   └── WordScript.kt             (WordScript, SpeakItem, ScriptPauses, PhrasePair)
    │   ├── progress/
    │   │   ├── CefrCalculator.kt         (object FR-8)
    │   │   ├── FlowerCalculator.kt       (object + SpacedRepetitionFormulas FR-8)
    │   │   ├── LessonCompletionCalculator.kt  (object FR-8, FR-10)
    │   │   ├── LessonLadderCalculator.kt (object FR-8)
    │   │   ├── ProgressCalculator.kt     (ChapterProgressCalculator + PackProgressCalculator FR-8)
    │   │   └── StreakCalculator.kt       (object FR-8)
    │   ├── repository/
    │   │   ├── ContentRepository.kt      (порт FR-6)
    │   │   ├── MasteryRepository.kt      (порт FR-6)
    │   │   ├── ProgressRepository.kt     (порт FR-6)
    │   │   ├── SessionRepository.kt      (порт FR-6, ★ card_15)
    │   │   ├── SettingsRepository.kt     (порт FR-6)
    │   │   ├── UserContentRepository.kt  (порт FR-6)
    │   │   └── VocabDrillRepository.kt   (порт FR-6)
    │   ├── session/
    │   │   ├── SessionEngine.kt          (★ card_15 fix FR-3)
    │   │   └── SubLessonScheduler.kt     (object FR-9)
    │   ├── srs/
    │   │   ├── SrsCardState.kt           (SrsCardState, SrsMemoryState)
    │   │   ├── SrsConstants.kt           (object)
    │   │   ├── SrsMigration.kt           (legacy step → FSRS, только миграция)
    │   │   ├── SrsParams.kt              (FSRS v6, FR-4)
    │   │   ├── SrsRating.kt
    │   │   └── SrsScheduler.kt           (FSRS v6 ядро FR-4)
    │   ├── story/
    │   │   └── MultilingualStoryParser.kt  (NEW — gap #1 FR-12)
    │   ├── training/
    │   │   ├── CardSessionStateMachine.kt (FR-5)
    │   │   ├── HintCalculator.kt          (object)
    │   │   ├── MixedReviewScheduler.kt    (ScheduledSubLesson, LessonSchedule)
    │   │   └── WordBankGenerator.kt       (object)
    │   └── validation/
    │       ├── AnswerValidator.kt
    │       └── Normalizer.kt
    └── test/java/com/alexpo/grammermate/domain/   (17 тест-классов, 314 @Test)
```

### 2.2. Файлы, НЕ переносимые в `:domain`

- `YamlToRoomMigrator.kt` — остаётся в data-слое (`app/.../v2/core/data/migration/`),
  помечается `@Deprecated` (см. FR-13, решение спонсора Q3 = greenfield data).
- Room-entities/DAOs — E02 (data layer).
- `SherpaAudioRepository` / `SherpaAudioModelRepository` — E03; в `:domain` остаются
  только интерфейсы портов `AudioRepository`/`AudioModelRepository`.

---

## 3. Функциональные требования (FR)

Каждый FR индивидуально адресуем (AC будут `derived_from` по коду). Формулировки —
что система должна делать; формулы и сигнатуры — в §5 (API contract).

### FR-1. Pure-Kotlin доменное ядро (zero-Android)

Модуль `:domain` содержит всё доменное ядро GrammarMate: модели контента, прогресса,
сессий, SRS, тренировок, аудио-порты, валидаторы и калькуляторы — как pure Kotlin
(`data class` с `val`, sealed-иерархии, object-калькуляторы). Ноль `import android.*`.
Тестирование — на чистой JVM через in-memory fake-репозитории.

**Проверка:** NFR-1 (grep), NFR-3 (kotlin jvm plugin).

### FR-2. Доменные модели полного покрытия legacy

`:domain` содержит immutable доменные модели, покрывающие все 14 legacy-доменов:
контент (`Language`, `Pack`, `Chapter`, `Lesson`, `Card`, `GrammarChip`,
`StoryQuiz`, `StoryQuestion`), прогресс (`LessonMastery`, `StreakData`,
`ChapterProgress`, `DrillProgress`, `DailyCursor`, `PomodoroHistoryEntry`),
сессии (`SessionSnapshot`), настройки (`AppConfig`), пользовательский контент
(`HiddenCard`, `BadSentence`, `BgVocabWord`, `UserProfile`), drill'ы
(`VerbDrillCard`, `AuxDrillCard`, `VocabWord`, `WordMasteryState`, `VocabDrillCard`),
геймификация (`BossState`, `EliteState`, `PomodoroState`, `PomodoroSessionStats`,
`LessonLadderRow`), bg-vocab (`WordScript`, `SpeakItem`, `PhrasePair`,
`ScriptPauses`), daily (`DailyTask` sealed). Все ID — value classes (`PackId`,
`LessonId`, `CardId`, `LanguageId`, `ChapterId`, `SessionId`) для type-safety.

**Проверка:** SM-3 (аудит-чеклист 14 доменов), см. §6 NFR.

### FR-3. SessionEngine — resume-by-PK (сохранение инвариантов бага card_15)

`SessionEngine` (class, конструктор: `SessionRepository, ContentRepository,
UserContentRepository, clock: () -> Long`) реализует тренировочную сессию с
текущей картой по **первичному ключу** `SessionSnapshot.currentCardId` (не по
индексу). Переносится в `:domain` **без изменения поведения**. Гарантируемые
инварианты (покрыты `SessionEngineResumeRegressionTest`, 314 @Test суммарно):
1. `currentCardId` ВСЕГДА ∈ `poolCardIds` (или null, если пул пуст);
2. `resumeSession` возвращает снимок с тем же `currentCardId`, что был при
   сохранении — идентичность сохранена;
3. скрытие ТЕКУЩЕЙ карты ЯВНО переводит `currentCardId` на следующую доступную
   (не на null, не молча);
4. `submitAnswer` атомарно обновляет прогресс и (для VOICE/KEYBOARD) пометку shown
   одной `saveSession`;
5. WORD_BANK НЕ помечает карту shown (дизайн v1 сохранён).

Публичный API: `startLessonSession`, `resumeSession`, `nextCard`, `previousCard`,
`submitAnswer`, `flagCard`, `hideCard`, `completeSession`.

**Проверка:** `SessionEngineResumeRegressionTest` зелёный после переноса.

### FR-4. SRS FSRS-v6 планировщик (без изменений формул)

`SrsScheduler` (class) + `SrsParams` (data class, 21 вес FSRS v6) реализуют ядро
FSRS v6 (open-spaced-repetition, прямой перенос py-fsrs). Переносятся в `:domain`
**без изменения формул** (regression-locked): power forgetting curve с обучаемым
decay (`w[20]`), DSR-модель (Difficulty 1–10, Stability дни, Retrievability 0–1),
жизненный цикл `NEW → LEARNING → REVIEW → RELEARNING`. Полностью детерминированный:
время `now` всегда передаётся параметром, `System.currentTimeMillis()` не
используется; `enableFuzzing=false` по умолчанию. `SrsConstants` — единый источник
лестницы `INTERVAL_LADDER_DAYS = [1,2,4,7,10,14,20,28,42,56]`, порогов
`MASTERY_THRESHOLD=150`, `WILTED_THRESHOLD=0.5`, `GONE_THRESHOLD_DAYS=90`,
`LEARNED_THRESHOLD=3`.

Публичный API: `schedule(state, rating, now, params)`, `retrievability(state, now, params)`,
`initDifficulty(rating, params)`, `initStability(rating, params)`,
`nextInterval(stability, params)`, `nextDueAtMs(state, now)`.

**Проверка:** `SrsSchedulerTest`, `SrsParamsTest`, `SrsMigrationTest` зелёные.

### FR-5. CardSessionStateMachine (retry/hint)

`CardSessionStateMachine` (class, `maxAttempts = TrainingConfig.HINT_THRESHOLD = 3`,
`answerProvider: (String) -> String`) — переиспользуемый держатель retry/hint для
провайдеров карточных сессий (VerbDrill, Daily). Использует
`kotlinx.coroutines.flow.MutableStateFlow` (не Compose) для JUnit-тестируемости.
Контракт: верный ответ → сброс; неверный → инкремент попыток + inline-фидбек, при
VOICE авто-retry (`voiceTriggerToken++`); `maxAttempts` неверных → авто-подсказка
(`HintShown`); ручной showAnswer → форс-подсказка; ввод после подсказки → сброс.
Возвращает `OnSubmitResult` (sealed): `Correct(answerResult)` /
`Wrong(incorrectAttempts, remainingAttempts)` / `HintShown(answer)`. Переносится
1:1 из v1 (адаптация только `cardId: String` вместо `SessionCard`).

Публичный API: `onSubmit(isCorrect, cardId, inputMode, onCorrect, onWrong)`,
`onInputChanged(text)`, `showAnswer(cardId)`, `clearIncorrectFeedback()`, `reset()`,
`triggerVoice()`, `pause()`, `resume()`.

**Проверка:** `CardSessionStateMachineTest` зелёный.

### FR-6. Доменные порты (9 интерфейсов) — стабильный контракт

`:domain` определяет 9 интерфейсов портов (реализации — E02 data layer). Контракт
**фиксируется в SRS** (см. §5 API contract) до старта Wave 1: любое изменение
после старта Wave 1 = drift (NFR-4). Порты:

1. `ContentRepository` — read-only контент паков (языки, паки, главы, уроки, карты);
   один реактивный метод `observeLessons`.
2. `SessionRepository` — ★ критично, фикс card_15; атомарные `loadSession`/`saveSession`.
3. `MasteryRepository` — освоенность уроков + SRS (`getDueCards`, `observeMastery`).
4. `ProgressRepository` — streak/«огоньки», прогресс глав/drill, дневной курсор.
5. `UserContentRepository` — скрытые/«плохие» карты, bg-vocab пометки, профиль,
   история помодоро.
6. `VocabDrillRepository` — vocab SRS, verb drill, aux drill, boss-награды.
7. `SettingsRepository` — key-value конфиг (DataStore), флаги одноразовых миграций.
8. `AudioRepository` — TTS/ASR/SFX (`speak`, `recognizeSpeech`, `playSoundEffect`).
9. `AudioModelRepository` — жизненный цикл аудио-моделей (download/status/bluetooth).

Все mutating-методы — `suspend`; реактивные — `Flow`. Домен не знает про Room,
Android, Sherpa.

**Проверка:** §5 фиксирует сигнатуры; NFR-4 фиксирует стабильность.

### FR-7. Аудио-доменные порты и доменные типы аудио-событий

`:domain` содержит `AudioRepository`/`AudioModelRepository` (FR-6.8/6.9) и доменные
sealed-типы событий: `AudioEvent` (`Started`/`Progress(fraction)`/`Completed`/`Failed`),
`RecognitionEvent` (`ListeningStarted`/`EndpointDetected`/`Partial(text)`/`Final(text)`/`Failed`),
`DownloadProgress` (`Downloading(percent)`/`Extracting`/`Initializing`/`Done`/`Failed`),
`ModelStatus` enum, `SoundEffect` enum (`CORRECT_ANSWER`/`WRONG_ANSWER`/`LESSON_COMPLETE`).
Адаптер Sherpa-ONNX — E03; домен зависит только от интерфейсов. Пути к аудиофайлам
в `Segment.Audio.file` — `String` (НЕ `java.io.File`), чтобы домен оставался чистым.

**Проверка:** сборка `:domain` без `java.io.File` в public API аудио-портов.

### FR-8. Pure-Kotlin калькуляторы прогресса (regression-locked)

`:domain` содержит object-калькуляторы прогресса, перенесённые 1:1 из v1
(вычислительная часть, без `android.util.Log`, без stateful-обёрток):
- `CefrCalculator.calculate(ranks)` — CEFR по медиане рангов (`A1`..`C2` либо `—`).
- `FlowerCalculator.calculate(mastery, totalCardsInLesson, nowMs)` — визуальный
  «цветок» урока (`FlowerVisual`, состояние `FlowerState`), через
  `SpacedRepetitionFormulas` (кривая Эббингауза `R = e^(-t/S)`).
- `LessonLadderCalculator.calculate(mastery, nowMs, ladder)` — метки лесенки
  интервалов (`LessonLadderMetrics`, `"N-M"` / `"Просрочка+K"`).
- `StreakCalculator.computeNextStreak(current, nowMs)` — серия дней
  (унификация v1-бага: reset на 1, не на 0).
- `LessonCompletionCalculator` (см. FR-10).
- `ChapterProgressCalculator.calculate(chapter, masteryStates)` +
  `PackProgressCalculator.calculatePackFlower(lessonIds, masteryStates, nowMs)`
  (в файле `ProgressCalculator.kt`).

Все `nowMs` инъектируются (детерминизм). Переносятся без изменения формул.

**Проверка:** 6 `*CalculatorTest` классов (Cefr/Flower/LessonCompletion/LessonLadder/Progress/Streak) зелёные.

### FR-9. SubLessonScheduler (детерминированная нарезка по PK)

`object SubLessonScheduler` — pure function нарезки карт урока на под-уроки по
`sessionSize` (последовательная, без перемешивания — это важно для устойчивости
resume). API: `buildSubLessons(cards, sessionSize): List<List<CardId>>`,
`activeSubLesson(subLessons, activeIndex): List<CardId>` (с clamping индекса).
Замена v1-`MixedReviewScheduler`-рантайм-пересчёта, который порождал баг card_15.

Дополнительно в `training/MixedReviewScheduler.kt`: `MixedReviewScheduler`
(class, `subLessonSize`, `intervals`) — сложная new+review нарезка
(`ScheduledSubLesson(type, cardIds)`, `LessonSchedule`, `LessonScheduleInput`).
Переносится как есть.

**Проверка:** `SubLessonSchedulerTest`, `MixedReviewSchedulerTest` зелёные.

### FR-10. SubLesson / Lesson completion logic (gap #3 — закрыть)

`object LessonCompletionCalculator` уже присутствует и покрывает завершение
урока/под-урока: `isLessonComplete(uniqueCardShows, totalCardsInLesson, hiddenCardCount)`
(порог `min(effective, 150)`) и `calculateCompletedSubLessons(subLessons, shownCardIds,
lessonCardIds, hiddenCardIds)` (последовательный подсчёт завершённых под-уроков,
авто-завершение полностью скрытых под-уроков). **Действие E01 по gap #3:**
подтвердить, что контракт завершения покрывает все legacy-кейсы; при выявлении
незакрытого кейса — добавить pure-функцию в `LessonCompletionCalculator` (не в UI).

**Проверка:** `LessonCompletionCalculatorTest` зелёный + аудит-чеклист (SM-3).

### FR-11. Извлечение Gradle-модуля `:domain` (pure Kotlin/JVM)

Создать физический модуль `:domain`:
- `settings.gradle.kts`: добавить `include(":domain")` (сейчас только `:app`).
- `domain/build.gradle.kts`: плагин `org.jetbrains.kotlin.jvm` (НЕ
  `com.android.application/library`); `jvmTarget = "17"` (совпадает с `:app`).
- Зависимости: `kotlin-stdlib`, `kotlinx-coroutines-core` (для `Flow`),
  `kotlinx-serialization-json` (если потребуется для `@Serializable` доменных
  моделей — на текущий момент доменные модели НЕ сериализуемые аннотациями, поэтому
  serialization добавляется опционально/по необходимости).
- `:app/build.gradle.kts`: `implementation(project(":domain"))`.
- Перенос main- и test-контента (§2.1); перенос механический, без изменения логики.

**Проверка:** NFR-3 (kotlin jvm plugin), SM-4 (`:domain` компилируется, `:app`
собирается).

### FR-12. MultilingualStoryParser (gap #1 — доменный pure-Kotlin парсер)

Добавить в `:domain` (`domain/story/MultilingualStoryParser.kt`) pure-Kotlin парсер
многоязычного story-контента, который разворачивает story-разметку в доменные
модели. На сегодня парсер существует **только в data-слое**
(`app/.../data/MultilingualStoryParser.kt`) и заражён `android.util.Log` и
`java.io.File` — это блокирует zero-Android. Действие E01:

- Перенести парсинг-логику в домен как `object MultilingualStoryParser` (pure Kotlin):
  разметка `{it}…{/it}`, `{en}…{/en}`, `{ru}…{/ru}`, `{el}`, `{de}`, `{zh}` →
  доменные `Segment` (уже определён в `model/Enums.kt`: `Segment.Text(text, languageId)`
  / `Segment.Pause(ms)`; `Segment.Audio(file: String, languageId)` — путь как String,
  не `java.io.File`).
- Маркер `{pause:N}` → `Segment.Pause`.
- Возвращает доменные `StoryQuiz`/`StoryQuestion` (`model/Content.kt`) для
  story-check-in/check-out (`StoryPhase.CHECK_IN` / `CHECK_OUT`).
- Доменный парсер НЕ зависит от `android.util.Log` и `java.io.File`; логирование —
  забота data/UI-слоя, путь к файлу — `String`.
- `detectLanguage(text, defaultLanguageId)` переносится как pure-функция.

**Проверка:** переносится вместе с `MultilingualStoryParserPauseTest` (data → domain);
тест зелёный; `grep "^import android" domain/src` = 0; `grep "java.io.File"
domain/story` = 0 в public API.

### FR-13. YamlToRoomMigrator — `@Deprecated` (решение спонсора Q3)

`YamlToRoomMigrator` (`app/.../v2/core/data/migration/YamlToRoomMigrator.kt`, data-слой)
пометить `@Deprecated("Q3 greenfield data — migrator больше не активный путь данных",
level = DeprecationLevel.WARNING)`. Мигратор НЕ переносится в `:domain` (это
data-слой). Продукт-требование (PRD SM-5): мигратор перестаёт быть активным путём
данных. Выбор `@Deprecated` (а не delete) фиксируется здесь: сохраняется
обратная совместимость компиляции на переходный период, но компилятор предупреждает.
`SettingsRepository.isMigrationDone("migrate_yaml_to_room_v1")` остаётся в контракте
порта (FR-6.7) — флаг миграции персистируется, но новый код его не вызывает.

**Проверка:** SM-5 (`@Deprecated` присутствует, ни один активный путь данных не
вызывает migrator).

### FR-14. DailyTask composers (gap #2 — завершить)

`:domain` уже содержит sealed `DailyTask` (`TranslateSentence(card, inputMode)` /
`VocabFlashcard(word, direction)` / `ConjugateVerb(card, inputMode)`) с `blockType:
DailyBlockType { TRANSLATE, VOCAB, VERBS }`. **Действие E01 по gap #2:**
подтвердить/завершить pure-Kotlin композиторы, которые собирают дневную норму из
блоков `DailyBlockType` + `DailyCursor` (`ProgressRepository.getDailyCursor`) в
`List<DailyTask>`. Композиторы живут в `:domain` (например,
`domain/daily/DailyTaskComposer.kt`) как pure-функция от `(cursor, content, settings)`,
без Android. Если композиторы уже частично существуют вне домена — перенести чистую
часть; UI-специфичная сборка остаётся в presentation.

**Проверка:** новая `DailyTaskComposerTest` покрывает сборку из `DailyCursor` +
`DailyBlockType`; `grep "^import android" domain/daily` = 0.

---

## 4. Нефункциональные требования (NFR)

NFR содержат измеримые метрики (не «быстро», а конкретные числа).

### NFR-1. Zero Android-импортов

В `domain/src` **0** строк, матчей `^import android`.
Команда проверки:
```
git grep -c "^import android" -- "domain/src"  → 0 совпадений
grep -rn "^import android" domain/src          → 0 строк
```
(На старте E01 H1 verified: `git grep "^import android" HEAD -- "*/v2/core/domain/*"`
= 0.) Физический Kotlin/JVM-модуль делает нарушение невозможным для сборки.

### NFR-2. Regression-lock: 314 `@Test` / 17 классов зелёные

После переноса в `:domain` все существующие доменные тесты остаются зелёными БЕЗ
изменения утверждений. Метрики (verified 2026-07-07):
- `@Test`-функций в `*/v2/core/domain/*Test.kt`: **314**;
- тестовых классов: **17**
  (CefrCalculatorTest, FlowerCalculatorTest, LessonCompletionCalculatorTest,
  LessonLadderCalculatorTest, ProgressCalculatorTest, StreakCalculatorTest,
  SessionEngineResumeRegressionTest, SubLessonSchedulerTest, SrsMigrationTest,
  SrsParamsTest, SrsSchedulerTest, CardSessionStateMachineTest, HintCalculatorTest,
  MixedReviewSchedulerTest, WordBankGeneratorTest, AnswerValidatorTest,
  NormalizerTest).
Команда: `./gradlew :domain:test` зелёный; exit code 0.

### NFR-3. Pure-Kotlin JVM-модуль

`domain/build.gradle.kts` использует плагин **`org.jetbrains.kotlin.jvm`** (НЕ
`com.android.application`, НЕ `com.android.library`). `jvmTarget = "17"`. Модуль
компилируется без AGP. `:app` успешно собирается с зависимостью
`implementation(project(":domain"))`. Метрика: `./gradlew :domain:build` успешно
(без Android SDK в classpath модуля).

### NFR-4. Стабильность контракта портов (после E01 = drift)

9 доменных портов (FR-6) фиксируются в SRS E01. **После принятия SRS** любое
изменение сигнатуры порта (добавление/удаление метода, изменение типа параметра,
смены `suspend`/`Flow`) — это **drift**, который требует отдельной задачи с
`trace_add(link_type: 'derived_from', target: SRS-001)` и пересмотра AC.
Параллельные dev-задачи Wave 1–4 НЕ имеют права менять `domain/repository/*` и
`domain/audio/*`. Метрика: `git diff` на `domain/repository/` и `domain/audio/`
после старта Wave 1 = пусто (вне задач, явно помеченных как drift).

### NFR-5. Детерминированность (injectable clock)

Все вычисления времени в домене используют инъектированный источник времени, а не
`System.currentTimeMillis()`:
- `SessionEngine` — конструктор `clock: () -> Long` (default
  `{ System.currentTimeMillis() }`, в тестах подменяется);
- `SrsScheduler.schedule(state, rating, now, params)` — `now` параметром;
- калькуляторы — `nowMs` параметром (`FlowerCalculator`, `LessonLadderCalculator`,
  `StreakCalculator`, `PackProgressCalculator`).
Метрика: `git grep "System.currentTimeMillis" domain/src/main` возвращает ровно 1
совпадение — default-аргумент конструктора `SessionEngine` (помечен как
injectable); никаких скрытых вызовов в вычислениях.

### NFR-6. Атомарность resume (одна транзакция)

`SessionRepository.loadSession` и `saveSession` выполняются как одна операция
(один SELECT / одна `@Transaction` в реализации). Контракт порта явно требует, что
курсор + пул + `currentCardId` + `shownCardIds` всегда согласованы. Метрика:
`SessionEngineResumeRegressionTest` покрывает сценарий «resume после скрытия
текущей карты» и «resume с выпавшей из пула картой» — оба зелёные.

### NFR-7. Immutability доменных моделей

Все доменные модели — `data class` с `val`-свойствами (immutable). Единственное
исключение — `ChapterProgress.totalLessons` (`var private set` — вычисляемое поле,
обновляется через `withTotalLessons(total)`), задокументировано. ID — `@JvmInline
value class`. Метрика: ревью моделей подтверждает отсутствие `var` вне
`ChapterProgress.totalLessons`.

---

## 5. Контракт портов (API contract)

> **ОБЯЗАТЕЛЬНАЯ секция** (brief `shared_mutation_risk=true`): `:domain` станет
> общей зависимостью всех вертикальных эпиков Wave 1–4. Сигнатуры ниже —
> канонический контракт; Wave 1–4 опираются на него. Тексты имплементаций (Room,
> Sherpa) — E02/E03.

Все интерфейсы — в пакете `com.alexpo.grammermate.domain.repository` и
`...domain.audio`. ID — value classes (`PackId`, `LessonId`, `CardId`, `LanguageId`,
`ChapterId`, `SessionId` — все `@JvmInline value class(val value: String)`).
`SessionId` имеет companion-фабрики: `forLesson(packId, lessonId)`, `forVerbDrill`,
`forDailyTranslate`, `forDailyVerbs`, `forAuxDrill`, `forPomodoro`.

### 5.1. ContentRepository (read-only контент паков)

```kotlin
interface ContentRepository {
    suspend fun getLanguages(): List<Language>
    suspend fun getPacks(): List<Pack>
    suspend fun getPacksForLanguage(langId: LanguageId): List<Pack>
    suspend fun getPack(packId: PackId): Pack?
    suspend fun getChapters(packId: PackId): List<Chapter>
    suspend fun getLessons(packId: PackId): List<Lesson>
    suspend fun getLessonsForChapter(packId: PackId, chapterId: ChapterId): List<Lesson>
    suspend fun getLesson(lessonId: LessonId): Lesson?           // вместе с lesson.cards
    suspend fun getCards(lessonId: LessonId): List<Card>
    fun observeLessons(packId: PackId): Flow<List<Lesson>>       // единственный Flow
}
```

### 5.2. SessionRepository (★ критично, фикс card_15)

```kotlin
interface SessionRepository {
    suspend fun getOrCreateSession(
        sessionId: SessionId, packId: PackId, lessonId: LessonId?, mode: TrainingMode,
        poolCardIds: List<CardId>? = null,
        selectedTense: String? = null, selectedGroup: String? = null, selectedPerson: String? = null,
    ): SessionSnapshot
    suspend fun loadSession(sessionId: SessionId): SessionSnapshot?    // один SELECT
    suspend fun saveSession(snapshot: SessionSnapshot)                  // одна транзакция
    suspend fun completeSession(sessionId: SessionId)
    suspend fun setCurrentCard(sessionId: SessionId, cardId: CardId)    // по PK, НЕ по индексу
    suspend fun markCardShown(sessionId: SessionId, cardId: CardId)
    suspend fun updateProgress(sessionId: SessionId, correct: Int, incorrect: Int, hint: Int)
    suspend fun deleteSession(sessionId: SessionId)
}
```

### 5.3. MasteryRepository (освоенность + SRS)

```kotlin
interface MasteryRepository {
    suspend fun getMastery(packId: PackId, lessonId: LessonId): LessonMastery?
    fun observeMastery(packId: PackId, lessonId: LessonId): Flow<LessonMastery?>
    suspend fun recordCardShow(packId: PackId, lessonId: LessonId, cardId: CardId, nowMs: Long): LessonMastery
    suspend fun markLessonCompleted(packId: PackId, lessonId: LessonId, nowMs: Long)
    suspend fun getDueCards(nowMs: Long, limit: Int): List<CardId>
    suspend fun updateSrsState(packId: PackId, lessonId: LessonId, srs: SrsCardState)
}
```

### 5.4. ProgressRepository (streak / главы / drill / daily cursor)

```kotlin
interface ProgressRepository {
    // Streak
    suspend fun getStreak(langId: LanguageId): StreakData?
    fun observeStreak(langId: LanguageId): Flow<StreakData?>
    suspend fun recordPracticeCompletion(langId: LanguageId, type: PracticeType, nowMs: Long): StreakData
    // Главы
    suspend fun getChapterProgress(packId: PackId, chapterId: ChapterId): ChapterProgress?
    suspend fun updateChapterProgress(packId: PackId, chapterId: ChapterId,
        startedDelta: Int = 0, completedDelta: Int = 0, nowMs: Long)
    // Drill
    suspend fun getDrillProgress(packId: PackId, drillType: String): DrillProgress?
    suspend fun saveDrillProgress(progress: DrillProgress)
    // Daily cursor
    suspend fun getDailyCursor(packId: PackId): DailyCursor?
    suspend fun saveDailyCursor(cursor: DailyCursor)
}
```

### 5.5. UserContentRepository (скрытые/плохие/bg-vocab/профиль/помодоро)

```kotlin
interface UserContentRepository {
    // Скрытые карты
    suspend fun getHiddenCardIds(): Set<CardId>
    suspend fun hideCard(cardId: CardId, nowMs: Long)
    suspend fun unhideCard(cardId: CardId)
    fun observeHiddenCards(): Flow<Set<CardId>>
    // Плохие предложения
    suspend fun getBadSentences(packId: PackId): List<BadSentence>
    suspend fun flagBadSentence(entry: BadSentence)
    suspend fun unflagBadSentence(packId: PackId, cardId: CardId)
    suspend fun isBadSentence(packId: PackId, cardId: CardId): Boolean
    // bg-vocab пометки
    suspend fun getBgVocabMark(word: String): BgVocabMark
    suspend fun setBgVocabMark(word: String, mark: BgVocabMark, nowMs: Long)
    suspend fun resetGreenMarks()
    // Профиль
    suspend fun getProfile(): UserProfile
    suspend fun saveProfile(profile: UserProfile)
    // Помодоро
    suspend fun addPomodoroSession(entry: PomodoroHistoryEntry)
    suspend fun getPomodoroHistory(): List<PomodoroHistoryEntry>
}
```

### 5.6. VocabDrillRepository (vocab SRS / verb / aux / boss rewards)

```kotlin
interface VocabDrillRepository {
    // Vocab words (контент)
    suspend fun getVocabWords(packId: PackId): List<VocabWord>
    suspend fun getVocabWordsByRankRange(packId: PackId, min: Int, max: Int): List<VocabWord>
    // Word mastery (SRS по словам)
    suspend fun getWordMastery(wordId: String): WordMasteryState?
    fun observeDueWords(limit: Int): Flow<List<Pair<String, WordMasteryState>>>
    suspend fun recordWordReview(wordId: String, isCorrect: Boolean, nowMs: Long): WordMasteryState
    suspend fun getAllWordMastery(): Map<String, WordMasteryState>
    // Verb drill
    suspend fun getVerbDrillCards(packId: PackId, tense: String?): List<VerbDrillCard>
    suspend fun getVerbDrillCardsForCombo(packId: PackId, verb: String, tense: String): List<VerbDrillCard>
    suspend fun getVerbComboProgress(packId: PackId, comboKey: String): VerbDrillComboProgress?
    suspend fun recordVerbCardShown(packId: PackId, comboKey: String, group: String, tense: String,
        cardId: String, totalCards: Int, nowMs: Long)
    suspend fun getVerbLastSession(packId: PackId): VerbDrillLastSession?
    suspend fun saveVerbLastSession(packId: PackId, session: VerbDrillLastSession, nowMs: Long)
    // Aux drill
    suspend fun getAuxDrillCards(packId: PackId, verb: String, tense: String): List<AuxDrillCard>
    suspend fun getAuxComboProgress(packId: PackId, comboKey: String): AuxDrillComboProgress?
    suspend fun recordAuxCardShown(packId: PackId, comboKey: String, verb: String, tense: String,
        cardId: String, totalCards: Int, nowMs: Long)
    // Boss rewards
    suspend fun getBossRewards(packId: PackId): Map<String, BossReward>
    suspend fun saveBossReward(packId: PackId, bossType: BossType, scopeKey: String,
        reward: BossReward, nowMs: Long)
}
```

### 5.7. SettingsRepository (key-value конфиг)

```kotlin
interface SettingsRepository {
    fun observeAppConfig(): Flow<AppConfig>
    suspend fun getAppConfig(): AppConfig
    suspend fun updateAppConfig(transform: (AppConfig) -> AppConfig)   // атомарный read-modify-write
    suspend fun setMigrationFlag(key: String, done: Boolean)          // напр. "migrate_yaml_to_room_v1"
    suspend fun isMigrationDone(key: String): Boolean
}
```

### 5.8. AudioRepository (TTS / ASR / SFX)

```kotlin
interface AudioRepository {
    fun speak(text: String, languageId: LanguageId, speed: Float = 1.0f): Flow<AudioEvent>
    suspend fun stop()
    fun recognizeSpeech(languageId: LanguageId): Flow<RecognitionEvent>
    suspend fun playSoundEffect(effect: SoundEffect)
    suspend fun isTtsAvailable(languageId: LanguageId): Boolean
    suspend fun isAsrAvailable(): Boolean
}
// События: AudioEvent (Started/Progress(fraction)/Completed/Failed(error)),
// RecognitionEvent (ListeningStarted/EndpointDetected/Partial(text)/Final(text)/Failed),
// SoundEffect enum { CORRECT_ANSWER, WRONG_ANSWER, LESSON_COMPLETE }
```

### 5.9. AudioModelRepository (жизненный цикл моделей)

```kotlin
interface AudioModelRepository {
    fun downloadTtsModel(languageId: LanguageId): Flow<DownloadProgress>
    fun downloadAsrModel(): Flow<DownloadProgress>
    suspend fun getTtsModelStatus(languageId: LanguageId): ModelStatus
    suspend fun getAsrModelStatus(): ModelStatus
    fun observeBluetoothMic(): Flow<Boolean>
    suspend fun setBluetoothMic(enabled: Boolean)
}
// DownloadProgress (Downloading(percent)/Extracting/Initializing/Done/Failed),
// ModelStatus enum { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }
```

### 5.10. Extension points (как добавлять новое в `:domain`)

- **Новая доменная модель:** добавить `data class` в `domain/model/`, при необходимости
  — value-class ID в `Pack.kt`. Не трогать порты.
- **Новый метод порта:** ЗАПРЕЩЕНО после старта Wave 1 без drift-задачи (NFR-4).
  До старта Wave 1 — добавить метод в интерфейс `domain/repository/*` или `domain/audio/*`.
- **Новый калькулятор:** добавить `object XxxCalculator` в `domain/progress/`
  (pure, `nowMs` параметром).
- **Новый SRS-параметр:** расширить `SrsParams` (не менять существующие веса FSRS v6 —
  regression-locked).
- **Новый тип DailyTask:** добавить `data class` вариант в sealed `DailyTask`
  (`model/DailyTask.kt`) + соответствующий `DailyBlockType`.

---

## 6. Зависимости и интеграция

### 6.1. Зависимости `:domain`

`:domain` зависит ТОЛЬКО от:
- `kotlin-stdlib` (default);
- `kotlinx-coroutines-core` — для `Flow` (порты, `CardSessionStateMachine`);
- (опционально) `kotlinx-serialization-json` — только если доменная модель получит
  `@Serializable`; на текущий момент модели сериализуются на границе слоёв
  (Room mappings в data-слое), поэтому serialization НЕ обязательна для E01.

`:domain` НЕ зависит от: Android SDK, AGP, Room, Hilt, Sherpa-ONNX, Compose,
DataStore, JUnit (JUnit — только `testImplementation`, в `:domain`).

### 6.2. Зависимость `:app → :domain`

```
:app
├── implementation(project(":domain"))   // модели, порты, калькуляторы, SessionEngine
├── implementation(libs.room.*)          // имплементации портов (E02)
├── implementation(libs.hilt.*)          // DI реализаций портов
└── implementation(libs.sherpa.onnx)     // SherpaAudioRepository (E03)
```

Стрелка зависимости: `:app → :domain`. Обратной стрелки нет. Это проверяется
компилятором: `:domain` не может импортировать ничего из `:app`.

### 6.3. Интеграция с downstream-эпиками

- **E02 (data layer):** имплементации 9 портов (`ContentRepositoryImpl`,
  `SessionRepositoryImpl`, …) —Room-mapping. Стартует после принятия SRS E01
  (контракт портов зафиксирован).
- **E03 (audio adapter):** `SherpaAudioRepository` / `SherpaAudioModelRepository`.
- **E04–E11 (Training/Verb/Vocab/Daily/Gamification/Boss/Pomodoro):** используют
  доменные модели и порты; НЕ меняют `domain/repository/*` (NFR-4).

---

## 7. Ограничения

1. **Не переписывать FSRS-v6** (`SrsScheduler`, `SrsParams`, 21 вес). Поведение
   regression-locked (R1, `SrsSchedulerTest`).
2. **Не переписывать SessionEngine / инварианты card_15.** Перенос как есть;
   инварианты FR-3 сохраняются (R1, `SessionEngineResumeRegressionTest`).
3. **Не переписывать калькуляторы прогресса.** `CefrCalculator`,
   `FlowerCalculator`/`SpacedRepetitionFormulas`, `LessonLadderCalculator`,
   `StreakCalculator`, `LessonCompletionCalculator`, `ChapterProgressCalculator`,
   `PackProgressCalculator` — формулы freeze. Унификации v1-расхождений (reset
   streak на 1, WILTED без epsilon, daysSince без/с +1) УЖЕ сделаны в коде —
   сохраняются как есть.
4. **Не менять контракты портов** после старта Wave 1 (NFR-4). До старта Wave 1 —
   можно (это и есть работа E01).
5. **Не добавлять Android-зависимости** в `:domain` (NFR-1). MultilingualStoryParser
   при переносе лишается `android.util.Log` и `java.io.File` (FR-12).
6. **Не выносить Room-entities/DAOs и миграции БД** в `:domain` — это E02 (data).
7. **Не реализовывать порты** в `:domain` — только интерфейсы. Имплементации — E02/E03.
8. **Перенос механический:** main-код и тесты переносятся как есть, без рефакторинга
   имён/формул. Изменение package declaration (`v2.core.domain` → `domain`) —
   единственное допустимое преобразование, выполняется update import statements.

---

## 8. Риски (перенесены из PRD §8, дополнены технической спецификой)

- **R1 (regression-risk):** перенос 314 `@Test` в `:domain`. Митигация: тесты
  переносятся вместе с кодом без изменения утверждений (NFR-2); механический
  перенос; H1 (zero-Android) уже подтверждён.
- **R2 (shared-mutation-risk):** изменение контракта порта после Wave 1 = drift.
  Митигация: §5 фиксирует сигнатуры; NFR-4; AC покрывают стабильность.
- **R3 (audit-scope-creep):** аудит 14 legacy-доменов может вскрыть пробелы.
  Митигация: gap-пункты (#1 парсер, #2 daily composers, #3 sub-lesson completion)
  перечислены явно (FR-10, FR-12, FR-14); выявленные новые gap'ы выносятся в
  отдельную задачу с trace, без тихого расширения scope.
- **R4 (migrator-removal):** `@Deprecated` вместо delete сохраняет компиляцию
  (FR-13); метрика SM-5.
- **R5 (package-rename regression):** переименование пакета при переносе может
  сломать import'ы в `:app`. Митигация: обновление import statements в `:app`
  выполняется в той же задаче переноса; сборка `:app` — gate (SM-4).

---

## 9. Traceability

| Артефакт | Тип | Source (derived_from) |
|---|---|---|
| **SRS-001** | SRS | PRD-001 (artifact_id=439) |
| FR-1  Pure-Kotlin домен | FR | SRS-001 |
| FR-2  Доменные модели | FR | SRS-001 |
| FR-3  SessionEngine card_15 | FR | SRS-001 |
| FR-4  FSRS-v6 SrsScheduler | FR | SRS-001 |
| FR-5  CardSessionStateMachine | FR | SRS-001 |
| FR-6  9 доменных портов | FR | SRS-001 |
| FR-7  Аудио-порты/события | FR | SRS-001 |
| FR-8  Калькуляторы прогресса | FR | SRS-001 |
| FR-9  SubLessonScheduler | FR | SRS-001 |
| FR-10 SubLesson completion (gap #3) | FR | SRS-001 |
| FR-11 Извлечение :domain модуля | FR | SRS-001 |
| FR-12 MultilingualStoryParser (gap #1) | FR | SRS-001 |
| FR-13 YamlToRoomMigrator @Deprecated | FR | SRS-001 |
| FR-14 DailyTask composers (gap #2) | FR | SRS-001 |
| NFR-1 Zero Android | NFR | SRS-001 |
| NFR-2 Regression-lock 314 @Test | NFR | SRS-001 |
| NFR-3 Pure-Kotlin JVM | NFR | SRS-001 |
| NFR-4 Стабильность контракта портов | NFR | SRS-001 |
| NFR-5 Детерминированность (clock) | NFR | SRS-001 |
| NFR-6 Атомарность resume | NFR | SRS-001 |
| NFR-7 Immutability моделей | NFR | SRS-001 |

AC (saga-analyst) будут `derived_from` FR-кодов выше. Dev-задачи (saga-planner)
будут `implements` FR-коды; порт-имплементации и адаптеры трассируются к FR-6/FR-7.

---

*SRS фиксирует систему (порты, модели, модуль), не пользовательские потоки (UC —
saga-analyst) и не бизнес-intent (PRD-001). Контракт портов §5 — обязательная
секция из-за shared_mutation_risk=true (brief). Все сигнатуры верифицированы по
фактическому коду `v2/core/domain/*` на 2026-07-07.*
