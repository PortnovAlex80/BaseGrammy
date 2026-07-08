# PRD-001 — Завершение доменного ядра + извлечение модуля :domain

**Эпизод:** E01 (epic_id=85), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (PRD)
**Дата:** 2026-07-07
**Status:** Draft

**Артефакт-источник:** Discovery brief (artifact_id=438, `00-discovery-brief.md`, decision=go).
**Repository:** BaseGrammy (repository_id=7), ветка `rewrite/v2-clean-architecture`.
**Код:** PRD-001

---

## 1. Резюме продукта/эпизода

E01 — это эпизод-фундамент (Wave 0), в котором доменный слой приложения
GrammarMate (`v2/core/domain`) доводится до полного покрытия пользовательского
функционала legacy-приложения и **выделяется в самостоятельный Gradle-модуль
`:domain`** как pure-Kotlin библиотека без Android-зависимостей. Эпизод не
приносит пользователю новых экранов сам по себе — его ценность косвенная:
без завершённого, стабильного и zero-Android домена невозможно запустить ни
один вертикальный продуктовый эпик Wave 1–4 (Training, Verb, Vocab, Daily,
Story, Gamification, Boss, Pomodoro). E01 снимает архитектурный блокер для
всей продуктовой программы.

## 2. Бизнес-контекст

GrammarMate проходит full rewrite с ветки `main` (legacy, 217 файлов, 14 доменов)
на чистую v2-архитектуру в ветке `rewrite/v2-clean-architecture`. На сегодня в
`v2/core/domain` уже существует надёжное ядро: FSRS-v6 планировщик повторений,
`SessionEngine`, `CardSessionStateMachine`, набор pure-Kotlin калькуляторов
прогресса (CEFR, Flower, Streak, LessonCompletion, LessonLadder), валидаторы
ответов и нормализаторы — всё с зелёной тестовой базой.

Спонсор принял решения (saga note id=28):
- **Q2:** v2 — это база; достраиваем пробелы, а не переписываем готовое.
- **Q3:** данные в Q3 года — greenfield (чистая установка), поэтому
  `YamlToRoomMigrator` больше не нужен и должен быть удалён/депрекирован.
- **:domain module NOW:** домен должен стать физически отдельным модулем
  до старта Wave 1.

**Почему критично.** `:domain` станет общей зависимостью всех вертикальных
эпиков. Если порты и модели домена не полны и не зафиксированы до Wave 1,
любой вертикальный эпик (Training/Verb/Vocab/Daily/Gamification/Boss/Pomodoro)
столкнётся с drift-контрактом: ему придётся менять домен «на ходу», что
размывает границу слоёв и порождает регрессии. E01 = замок фундамента перед
строительством стен.

## 3. User stories / ценности

PRD фиксирует ценности с точки зрения конечного пользователя GrammarMate.
E01 разблокирует их **через downstream-эпики**; сам по себе он не добавляет
UI, но без него эти истории нельзя реализовать.

- **US-1 (через Training, Wave 1):** Как учащийся, я хочу проходить
  интервальные тренировки предложений с устойчивым resume, чтобы не терять
  прогресс сессии при перезапуске приложения.
- **US-2 (через Verb, Wave 1):** Как учащийся, я хочу тренироваться в
  спряжении глаголов разными способами ввода (голос/клавиатура/word bank),
  чтобы отрабатывать глагольную парадигму.
- **US-3 (через Vocab, Wave 1):** Как учащийся, я хочу слушать фоновый
  словарь (bg-vocab) по плану озвучки, чтобы пассивно впитывать слова во
  время других занятий.
- **US-4 (через Daily, Wave 2):** Как учащийся, я хочу получать дневную
  норму задач (перевод / словарь / глаголы), чтобы иметь предсказуемый
  учебный ритм.
- **US-5 (через Story, Wave 2):** Как учащийся, я хочу проходить story-чекины
  (check-in/check-out) с вопросами по истории, чтобы проверять понимание
  прочитанного.
- **US-6 (через Gamification, Wave 3):** Как учащийся, я хочу видеть свой
  streak (серию дней) и прогресс по SRS-лестнице уроков, чтобы сохранять
  мотивацию.
- **US-7 (через Boss, Wave 3):** Как учащийся, я хочу проходить boss-битвы
  как контрольные точки, чтобы чувствовать измеримые достижения.
- **US-8 (через Pomodoro, Wave 4):** Как учащийся, я хочу запускать
  фокус-таймер pomodoro с подсчётом статистики за интервал, чтобы
  структурировать время занятий.

Каждая из этих историй требует полных, стабильных доменных моделей и портов.
E01 обеспечивает их наличие.

## 4. Scope IN — что входит в E01

### 4.1. Завершение домена до полного покрытия legacy

**Состояние на старте E01 (verified в репозитории 2026-07-07).** Часть
«отсутствующего» функционала из discovery (note id=2) на самом деле уже
реализована в коде:
- `BadSentence` модель + порт (`UserContentRepository`) — присутствует.
- Геймификация: `BossState`, `EliteState`, `PomodoroState`,
  `PomodoroSessionStats`, `LessonLadderRow/Metrics` — присутствуют.
- `WordScript` playback-план (`WordScript`, `SpeakItem`, `ScriptPauses`,
  `PhrasePair`) — присутствует.
- `DailyTask` sealed-иерархия (`TranslateSentence`, `VocabFlashcard`,
  `ConjugateVerb`) — присутствует.
- `SubLessonScheduler` (детерминированная нарезка по PK) — присутствует.
- `StoryQuiz` / `StoryQuestion` / `StoryPhase` — присутствуют.

**Реальные gap-пункты, требующие завершения в E01:**
1. **MultilingualStoryParser (доменный парсер story).** В `:domain`
   отсутствует парсер многоязычных story-файлов, который разворачивает
   story-контент в доменные модели `StoryQuiz`/`StoryQuestion`. Необходимо
   добавить доменную модель/логику парсинга (pure Kotlin, без Android).
2. **DailyTask composers.** `DailyTask` описывает единичную задачу, но
   композиторы, которые собирают дневную норму из блоков (`DailyBlockType`)
   в список `DailyTask`, должны быть подтверждены/завершены как pure-Kotlin
   доменная логика.
3. **SubLesson completion logic.** `SubLessonScheduler` режет карты на
   под-уроки; логика фиксации завершения под-урока/урока в доменных
   терминах (`LessonCompletionCalculator` уже есть — нужно убедиться, что
   контракт завершения покрывает все legacy-кейсы).
4. **Финальный аудит покрытия.** Поштучно сверить доменные модели/порты с
   14 legacy-доменами (note id=1) и зафиксировать аудит-чеклист: что покрыто,
   что осталось. Любая выявленная отсутствующая модель добавляется в E01.

> Примечание: технические детали моделей/портов (сигнатуры, идентификаторы,
> иммутабельность) — зона SRS (saga-architect). PRD фиксирует только
> продуктовую полноту: «домен должен уметь выразить весь legacy-функционал».

### 4.2. Извлечение :domain как pure-Kotlin Gradle-модуля

- Создать каталог `domain/` на корне репозитория с `build.gradle.kts`
  (Kotlin/JVM, без Android plugin).
- `settings.gradle.kts`: добавить `include(":domain")`.
- `:app` объявляет зависимость `implementation(project(":domain"))`.
- Перенос `app/src/main/java/com/alexpo/grammermate/v2/core/domain/*` →
  `domain/src/main/java/...` (механический перенос; H1 — zero Android-импортов).
- Перенос тестов `app/src/test/.../v2/core/domain/*` →
  `domain/src/test/...` (H3 — 72 теста / 314 `@Test`-функций остаются зелёными).

### 4.3. Удаление/депрекация YamlToRoomMigrator

- `YamlToRoomMigrator` (источник:
  `app/.../v2/core/data/migration/YamlToRoomMigrator.kt`) — пометить
  `@Deprecated` или удалить (решение спонсора Q3 = greenfield data).
- Конкретный выбор (delete vs deprecate) фиксируется в SRS; PRD требует лишь,
  чтобы migrator перестал быть активным путём данных.

## 5. Scope OUT — что НЕ входит в E01

- **Room entities/DAOs и миграции БД** — это E02 (saga-architect, data layer).
  E01 работает только с pure-Kotlin доменными моделями и интерфейсами портов.
- **Адаптер аудио (audio adapter / TTS playback)** — E03. E01 определяет
  только доменную модель `WordScript`/`SpeakItem` и порт `AudioRepository`.
- **UI-экраны** (Training/Verb/Vocab/Daily/Story/Gamification/Boss/Pomodoro) —
  E04 и далее. E01 не добавляет ни одного экрана.
- **Имплементации портов** (`ContentRepositoryImpl`, `SessionRepositoryImpl`
  и т.д.) — E02. E01 фиксирует только интерфейсы портов в домене.
- **FSRS-v6 / SessionEngine / CardSessionStateMachine — переписывание.**
  Они уже реализованы и протестированы; E01 только переносит их в `:domain`
  без изменения поведения.
- **Новые алгоритмы SRS или изменения формул.** Поведение существующих
  калькуляторов фиксируется (regression-locked), не улучшается.

## 6. Success metrics (измеримые)

- **SM-1 (zero-Android):** `grep -Rni "android\." domain/src` возвращает 0
  строк (импортов `android.*`); `grep -rn "^import android" domain/src` = 0.
- **SM-2 (no regression):** все существующие доменные тесты (314 `@Test`
  функций / 72 тестовых сценария) проходят после переноса в `:domain`
  (`build.bat test` / `./gradlew :domain:test` зелёный).
- **SM-3 (полнота покрытия):** аудит-чеклист legacy-доменов (14 доменов)
  показывает 100% покрытие доменными моделями/портами; каждый «gap» из
  раздела 4.1 закрыт или явно вынесен в отдельную задачу с trace.
- **SM-4 (модульная изоляция):** `:domain` компилируется как
  `org.jetbrains.kotlin.jvm` модуль; `:app` успешно собирается с
  зависимостью от `:domain`.
- **SM-5 (migrator устранён):** `YamlToRoomMigrator` помечен `@Deprecated`
  либо удалён; в кодовой базе нет активного пути данных через мигратор.

## 7. Зависимости и блокировки

- **E01 не зависит ни от чего** (foundation). Это первый эпизод Wave 0.
- **E01 блокирует (hard block):**
  - E04 (Training) — нужны `SessionEngine`, `CardSessionStateMachine`,
    `ContentRepository`/`SessionRepository` в стабильном виде.
  - E05 (Verb) — нужны `VerbDrillCard`, `DailyTask.ConjugateVerb`.
  - E06 (Vocab / bg-vocab) — нужны `WordScript`, `VocabDrillRepository`.
  - E07 (Daily) — нужны `DailyTask` composers, `DailyBlockType`.
  - E10 (Gamification) — нужны `BossState`, `EliteState`,
    `LessonLadder*`, streak-модели.
  - E11 (Pomodoro) — нужны `PomodoroState`, `PomodoroSessionStats`.
- **Мягкая связь:** E02 (data layer / Room) стартует параллельно после SRS
  E01, потому что имплементации портов (`*RepositoryImpl`) требуют
  зафиксированных доменных интерфейсов.

## 8. Риски и митигации

- **R1 (regression-risk):** перенос FSRS-v6 / SessionEngine /
  CardSessionStateMachine / калькуляторов в `:domain` может сломать
  существующие 314 `@Test`. **Митигация:** тесты переносятся вместе с кодом
  без изменения; перенос механический (H1 — zero Android-импортов уже
  подтверждён); запуск тестов — обязательный gate (SM-2).
- **R2 (shared-mutation-risk):** `:domain` — общая зависимость всех
  вертикальных эпиков. Изменение контракта порта после старта Wave 1 = drift.
  **Митигация:** порты финализируются в SRS E01 (saga-architect); AC покрывают
  стабильность контракта; любая новая модель добавляется через отдельную
  задачу с trace `derived_from` к SRS.
- **R3 (audit-scope-creep):** аудит legacy может вскрыть больше пробелов,
  чем оценено (complexity M). **Митигация:** выявленные gap-пункты либо
  закрываются в E01, либо явно выносятся в новую задачу с trace — без
  тихого расширения scope.
- **R4 (migrator-removal副作用):** удаление `YamlToRoomMigrator` может
  задеть dev-сборки, зависящие от миграции данных. **Митигация:** решение
  спонсора Q3 = greenfield data; удаление/deprecate согласовано; в SRS
  фиксируется, что миграция не является поддерживаемым путём.

## 9. Допущения

- **H1:** существующий `v2/core/domain` уже имеет ZERO Android-импортов
  (verified в репозитории: `grep -rn "^import android"` = 0 совпадений) →
  механический перенос в `:domain` безопасен.
- **H2:** legacy-функционал, отсутствующий в домене, конечен и перечислим
  (confirmed: основная масса уже реализована; реальные gaps —
  MultilingualStoryParser, DailyTask composers, финальный аудит покрытия).
- **H3:** после извлечения `:domain` 314 `@Test`-функций (72 сценария)
  останутся зелёными — тесты переносятся вместе с кодом без изменения
  утверждений.
- **H4 (новое):** репозиторий уже содержит существенную часть доменных
  моделей, ранее считавшихся «отсутствующими» (note id=2 устарел относительно
  кода); PRD опирается на фактическое состояние кода, а не на note.

## 10. Open questions

- **OQ-1:** Выносить ли `TrainingConfig` (object констант) в `:domain` или
  оставить в `:app`?
  **Решение (зафиксировано):** в `:domain` — он часть доменного контракта.
- **OQ-2:** Value-class IDs (`PackId`, `LessonId`, `CardId`, …) — переносить
  как есть или реорганизовать?
  **Решение (зафиксировано):** как есть — они уже pure Kotlin.
- **OQ-3 (новое):** Удалять `YamlToRoomMigrator` или помечать `@Deprecated`?
  PRD оставляет выбор SRS; product-требование — migrator перестаёт быть
  активным путём данных (Q3 greenfield).

---

*Следующий шаг formalization: SRS (saga-architect) фиксирует технический
контракт домена и портов; UC/AC (saga-analyst) — параллельно после SRS.
PRD устанавливает продуктовые границы и intent; всё downstream производно от него.*
