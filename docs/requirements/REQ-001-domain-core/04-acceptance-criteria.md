# AC-001 — Acceptance Criteria: Завершение доменного ядра + извлечение модуля `:domain`

**Эпизод:** E01 (epic_id=85), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (AC)
**Дата:** 2026-07-07
**Status:** Accepted

**Производные от:** UC-001 (artifact_id=440, `03-use-cases.md`, status=accepted) и
SRS-001 (artifact_id=450, `02-srs.md`, status=accepted, 14 FR + 7 NFR).
**Repository:** BaseGrammy (repository_id=7), ветка `feature/req-wave-0-formalization`.
**Код:** AC-001 (сводный артефакт документа), индивидуальные AC — `AC-1..AC-18`.

> AC — мост к dev-задачам на продуктовой доске. Каждый AC представлен в форме
> **Given/When/Then**, однозначно проверяем и трассируется (`derived_from`) на
> UC-1..UC-9 (artifact_id=441..449), а также покрывает (`covers`) FR/NFR
> (artifact_id=451..471). Полная трассировка приводится в §3.

---

## 0. Дизайн AC-множества

AC-множество построено так, чтобы **каждый из 14 FR и 7 NFR** имел ≥1 покрывающий
AC (см. coverage-матрицу §3.2 — без неё `artifact_coverage` показал бы gap). Особое
внимание уделено:
- инвариантам бага `card_15` (AC-5, AC-6) — критично для resume-by-PK;
- regression-lock FSRS-v6 (AC-9) — без изменений формул;
- GAP C2 (`TrainingStateAccess` successor) — AC-18, blocking для Wave 1;
- трем SRS-объявленным gap'ам (#1 StoryParser → AC-15; #2 DailyTask composers →
  AC-16; #3 LessonCompletion → AC-14);
- стабильности 9 доменных портов после E01 = drift (AC-10, AC-18, NFR-4).

AC написаны как **технически проверяемые** (unit-test name, grep-команда,
`./gradlew` target). «Работает корректно» не используется.

---

## AC-1. Извлечение Gradle-модуля `:domain` (settings + build)

- **Given:** репозиторий BaseGrammy содержит только модуль `:app`
  (`settings.gradle.kts` без `include(":domain")`).
- **When:** разработчик добавляет `include(":domain")` в `settings.gradle.kts`,
  создаёт `domain/build.gradle.kts` с плагином `org.jetbrains.kotlin.jvm`
  (`jvmTarget = "17"`), и объявляет `implementation(project(":domain"))` в
  `:app/build.gradle.kts`.
- **Then:**
  1. `grep 'include(":domain")' settings.gradle.kts` → 1 совпадение;
  2. `grep 'org.jetbrains.kotlin.jvm' domain/build.gradle.kts` → 1 совпадение,
     `grep 'com.android' domain/build.gradle.kts` → 0 совпадений;
  3. `grep 'project(":domain")' app/build.gradle.kts` ≥ 1 совпадение;
  4. `./gradlew :domain:build` завершается с exit code 0 (без Android SDK в
     classpath модуля `:domain`).

- **derived_from:** UC-8 (artifact_id=448).
- **covers:** FR-11 (461), NFR-3 (467).
- **verification:**
  - `./gradlew :domain:build` (exit 0);
  - `grep -E 'include\(":domain"\)|org.jetbrains.kotlin.jvm|project\(":domain"\)' settings.gradle.kts domain/build.gradle.kts app/build.gradle.kts`.

---

## AC-2. Zero Android-импортов в `:domain`

- **Given:** модуль `:domain` собран с переносом доменного кода
  (см. AC-1, AC-3, AC-4).
- **When:** выполняется проверка исходников модуля.
- **Then:** в `domain/src` ровно **0** строк, матчей `^import android` и **0** матчей
  `^import com\.k2fsa\.sherpa`.
- **derived_from:** UC-8 (448).
- **covers:** FR-1 (451), NFR-1 (465).
- **verification:**
  - `grep -rn "^import android" domain/src` → 0 строк;
  - `grep -rn "^import com.k2fsa.sherpa" domain/src` → 0 строк;
  - дополнительно сборка `:domain` (Kotlin/JVM plugin) **не резолвит**
    `android.*` — нарушение невозможно для компиляции (NFR-3).

---

## AC-3. Regression-lock: 314 `@Test` / 17 классов зелёные в `:domain`

- **Given:** тесты перенесены из `app/src/test/.../v2/core/domain/*` →
  `domain/src/test/...` **механически** (без изменения assertions/логики),
  package declaration обновлена `v2.core.domain` → `domain`.
- **When:** запускается `./gradlew :domain:test`.
- **Then:**
  1. Все 17 тест-классов собираются и выполняются;
  2. Число `@Test`-функций в `domain/src/test` = **314**
     (`grep -rc "@Test" domain/src/test | awk ...' | sum` = 314);
  3. Exit code 0 (BUILD SUCCESSFUL).
- **derived_from:** UC-8 (448).
- **covers:** FR-11 (461), NFR-2 (466).
- **verification:**
  - `./gradlew :domain:test` (exit 0);
  - `grep -rh "@Test" domain/src/test | wc -l` = 314.

---

## AC-4. Доменные модели полного покрытия legacy (14 доменов, immutable)

- **Given:** legacy-код содержит 14 доменных групп (контент, прогресс, сессии,
  настройки, пользовательский контент, drill'ы, геймификация, bg-vocab, daily,
  pack IDs).
- **When:** выполняется финальный аудит покрытия (`docs/requirements/legacy-coverage-checklist.md`
  §C/E) после переноса в `domain/model/`.
- **Then:**
  1. Все 14 групп представлены immutable моделями (`data class` с `val`),
     включая `Language`, `Pack`, `Chapter`, `Lesson`, `Card`, `GrammarChip`,
     `StoryQuiz`, `StoryQuestion`, `SessionSnapshot`, `DailyTask` (sealed),
     `BossState`, `EliteState`, `WordScript`, `HiddenCard`, `BadSentence` и т.д.;
  2. Все ID — `@JvmInline value class` (`PackId`, `LessonId`, `CardId`,
     `LanguageId`, `ChapterId`, `SessionId`);
  3. Единственное `var`-свойство вне value-классов — `ChapterProgress.totalLessons`
     (`var private set` + `withTotalLessons(total)`), что задокументировано.
- **derived_from:** UC-8 (448).
- **covers:** FR-2 (452), NFR-7 (471).
- **verification:**
  - ревью-аудит-чеклист (legacy-coverage-checklist.md §C/E) → 100% COVERED;
  - `grep -rn "^[[:space:]]*var " domain/src/main/java/com/alexpo/grammermate/domain/model/`
    → ровно 1 совпадение (`ChapterProgress.totalLessons`).

---

## AC-5. `SessionEngine` card_15 — resume возвращает тот же `currentCardId` ∈ pool

- **Given:** для `(packId, lessonId)` сохранена сессия со снимком
  `SessionSnapshot(currentCardId = X, poolCardIds = [..., X, ...])`, где
  `X ∈ poolCardIds`.
- **When:** downstream-эпик вызывает `SessionEngine.resumeSession(sessionId)`.
- **Then:**
  1. Загрузка снимка выполняется одной операцией
     `SessionRepository.loadSession` (атомарно — NFR-6);
  2. Возвращённый снимок содержит **тот же** `currentCardId = X`
     (идентичность сохранена);
  3. `X ∈ poolCardIds` возвращённого снимка (инвариант card_15).
- **derived_from:** UC-1 (441).
- **covers:** FR-3 (453), NFR-6 (470).
- **verification:**
  - unit-тест `SessionEngineResumeRegressionTest.resume_returnsSameCurrentCardId_inPool` (зелёный в `:domain`);
  - `./gradlew :domain:test --tests "*SessionEngineResumeRegressionTest*"`.

---

## AC-6. `SessionEngine` card_15 — явное восстановление при выпавшей карте

- **Given:** сохранён снимок, в котором `currentCardId = X`, но
  `X ∉ poolCardIds` (пул пересобран/повреждён — сценарий бага `card_15`).
- **When:** `SessionEngine.resumeSession(sessionId)`.
- **Then:**
  1. Карта `X` **возвращается в пул** (в конец `poolCardIds`);
  2. Снимок пересохраняется через `SessionRepository.saveSession` (атомарно);
  3. Возвращённый снимок содержит `currentCardId = X ∈ poolCardIds`
     (не `null`, не молчаливая подмена другой картой);
  4. Никогда не возвращается снимок, где курсор указывает мимо пула.
- **derived_from:** UC-1 (441).
- **covers:** FR-3 (453), NFR-6 (470).
- **verification:**
  - unit-тест `SessionEngineResumeRegressionTest.resume_orphanCurrentCardId_readdedToPoolTail` (зелёный);
  - тест `resume_hiddenCurrentCard_advancesExplicitly_neverNull` покрывает скрытие текущей карты.

---

## AC-7. `WORD_BANK` не помечает карту shown (дизайн v1 сохранён)

- **Given:** карта с `cardId = X` показана в режиме ввода `InputMode.WORD_BANK`.
- **When:** downstream-эпик вызывает
  `SessionEngine.submitAnswer(cardId=X, isCorrect=true, inputMode=WORD_BANK)`.
- **Then:**
  1. Прогресс (correct/incorrect count) обновлён атомарно;
  2. `X` **НЕ** добавлен в `shownCardIds`;
  3. Mastery/flower-state для урока **не выросли** от этого ответа
     (`FlowerCalculator` не вызывается для `X`).
- **derived_from:** UC-3 (443).
- **covers:** FR-3 (453), FR-5 (455).
- **verification:**
  - unit-тест `SessionEngineResumeRegressionTest.submitAnswer_WORD_BANK_notMarkedShown` (или эквивалент в `CardSessionStateMachineTest`);
  - `./gradlew :domain:test --tests "*CardSessionStateMachineTest*"`.

---

## AC-8. `VOICE`/`KEYBOARD` помечают карту shown

- **Given:** карта с `cardId = X` в режиме `InputMode.KEYBOARD` (или `VOICE`).
- **When:** `SessionEngine.submitAnswer(cardId=X, isCorrect=true, inputMode=KEYBOARD)`.
- **Then:**
  1. Прогресс обновлён;
  2. `X` **добавлен** в `shownCardIds` (самостоятельный ввод = полноценный показ);
  3. Mastery/flower продвигаются для `X`.
- **derived_from:** UC-3 (443).
- **covers:** FR-5 (455).
- **verification:**
  - unit-тест `CardSessionStateMachineTest.onSubmit_KEYBOARD_addsToShownCardIds` (зелёный).

---

## AC-9. FSRS-v6 unchanged — `SrsScheduler`/`SrsParams` regression-locked

- **Given:** `SrsScheduler` (FSRS v6, прямой перенос py-fsrs), `SrsParams`
  (21 вес FSRS v6), `SrsConstants` (лестница `[1,2,4,7,10,14,20,28,42,56]`,
  `MASTERY_THRESHOLD=150`, и т.д.) перенесены в `domain/srs/`.
- **When:** запускаются тесты `SrsSchedulerTest` (33 теста), `SrsParamsTest`,
  `SrsMigrationTest`.
- **Then:**
  1. Все тесты зелёные (выходные значения `dueAt`/`stability`/`difficulty` те же,
     что и до переноса — детерминизм сохранён);
  2. Время `now` инъектируется параметром, `System.currentTimeMillis()` в
     вычислениях не используется (NFR-5);
  3. Формулы power-forgetting curve, DSR-модель, жизненный цикл
     `NEW → LEARNING → REVIEW → RELEARNING` — без изменений.
- **derived_from:** UC-2 (442).
- **covers:** FR-4 (454), NFR-5 (469).
- **verification:**
  - `./gradlew :domain:test --tests "*SrsSchedulerTest*" --tests "*SrsParamsTest*" --tests "*SrsMigrationTest*"` (exit 0);
  - `grep -rn "System.currentTimeMillis" domain/src/main/java/com/alexpo/grammermate/domain/srs/` → 0 совпадений.

---

## AC-10. 9 доменных портов стабильны (сигнатуры заморожены)

- **Given:** E01 определил 9 интерфейсов портов в `domain/repository/` и
  `domain/audio/`: `ContentRepository`, `SessionRepository`, `MasteryRepository`,
  `ProgressRepository`, `UserContentRepository`, `VocabDrillRepository`,
  `SettingsRepository`, `AudioRepository`, `AudioModelRepository` (контракт в
  SRS-001 §5).
- **When:** после старта Wave 1 выполняется `git diff` на
  `domain/repository/` и `domain/audio/`.
- **Then:**
  1. Diff пуст (вне задач, явно помеченных как drift с
     `trace_add(link_type:'derived_from', target:SRS-001)`);
  2. Сигнатуры портов соответствуют SRS-001 §5.1–§5.9 (mutating = `suspend`,
     реактивные = `Flow`, ID = value classes).
- **derived_from:** UC-7 (447).
- **covers:** FR-6 (456), NFR-4 (468).
- **verification:**
  - ревью `domain/repository/*.kt` + `domain/audio/*.kt`: 9 интерфейсов присутствуют, сигнатуры совпадают с SRS-001 §5;
  - `git diff origin/main -- domain/repository/ domain/audio/` после Wave 1 → пуст (gate в CI/ревью).

---

## AC-11. Аудио-порты и sealed-типы событий без утечки Sherpa

- **Given:** `:domain` содержит `AudioRepository`, `AudioModelRepository` и
  доменные типы событий `AudioEvent`, `RecognitionEvent`, `DownloadProgress`,
  `ModelStatus`, `SoundEffect` (FR-7).
- **When:** сборка `:domain` и аудит импортов.
- **Then:**
  1. `grep -rn "sherpa\|com.k2fsa" domain/src` → 0 совпадений;
  2. Пути к аудиофайлам в `Segment.Audio.file` — `String` (НЕ `java.io.File`);
  3. `grep -rn "java.io.File" domain/src/main/java/com/alexpo/grammermate/domain/audio/` → 0 совпадений в public API;
  4. Все аудио-методы — `Flow` или `suspend`, ни один не блокирует поток.
- **derived_from:** UC-7 (447).
- **covers:** FR-7 (457), NFR-1 (465).
- **verification:**
  - `grep -rn "sherpa\|com.k2fsa" domain/src` → 0;
  - `grep -rn "java.io.File" domain/src/main/java/com/alexpo/grammermate/domain/audio/` → 0;
  - `./gradlew :domain:build` компилирует аудио-порты как pure Kotlin.

---

## AC-12. Pure-Kotlin калькуляторы прогресса (regression-locked)

- **Given:** 6 object-калькуляторов перенесены 1:1 в `domain/progress/`:
  `CefrCalculator`, `FlowerCalculator`/`SpacedRepetitionFormulas`,
  `LessonLadderCalculator`, `StreakCalculator`,
  `LessonCompletionCalculator`, `ProgressCalculator`
  (`ChapterProgressCalculator` + `PackProgressCalculator`).
- **When:** запускаются 6 `*CalculatorTest` классов.
- **Then:**
  1. Все тесты зелёные (формулы freeze, унификации v1-расхождений сохранены);
  2. Все `nowMs` инъектируются (детерминизм, NFR-5);
  3. Ни один калькулятор не вызывает `android.util.Log` или
     `System.currentTimeMillis()`.
- **derived_from:** UC-6 (446).
- **covers:** FR-8 (458), NFR-5 (469).
- **verification:**
  - `./gradlew :domain:test --tests "*CalculatorTest"` (exit 0): Cefr/Flower/LessonCompletion/LessonLadder/Progress/Streak;
  - `grep -rn "android.util.Log\|System.currentTimeMillis" domain/src/main/java/com/alexpo/grammermate/domain/progress/` → 0.

---

## AC-13. `SubLessonScheduler` — детерминированная нарезка по PK

- **Given:** `cards: List<Card>` урока и `sessionSize: Int`.
- **When:** вызывается `SubLessonScheduler.buildSubLessons(cards, sessionSize)`.
- **Then:**
  1. Возвращается `List<List<CardId>>` — последовательная нарезка без
     перемешивания (важно для устойчивости resume);
  2. `activeSubLesson(subLessons, activeIndex)` возвращает срез с clamping'ом
     индекса;
  3. Результат детерминирован (одинаковые входы → одинаковый выход, без
     зависимости от рантайма).
- **derived_from:** UC-6 (446).
- **covers:** FR-9 (459).
- **verification:**
  - `./gradlew :domain:test --tests "*SubLessonSchedulerTest*" --tests "*MixedReviewSchedulerTest*"` (exit 0).

---

## AC-14. `LessonCompletionCalculator` — контракт завершения покрывает все legacy-кейсы (gap #3)

- **Given:** `LessonCompletionCalculator` присутствует в `domain/progress/` с
  методами `isLessonComplete(uniqueCardShows, totalCardsInLesson, hiddenCardCount)`
  и `calculateCompletedSubLessons(subLessons, shownCardIds, lessonCardIds, hiddenCardIds)`.
- **When:** аудит/тестирование контракта завершения под-урока/урока на legacy-кейсах
  (включая edge-случаи со скрытыми картами и MIXED под-уроками).
- **Then:**
  1. Полностью скрытые под-уроки авто-завершаются;
  2. Порог завершения = `min(effective, 150)`;
  3. `activeSubLessonIndex` **монотонно неубывающий** (`maxOf(current, actual)`)
     — никогда не движется назад;
  4. `LessonCompletionCalculatorTest` зелёный + аудит-чеклист (SM-3) закрыт.
- **derived_from:** UC-6 (446).
- **covers:** FR-10 (460).
- **verification:**
  - `./gradlew :domain:test --tests "*LessonCompletionCalculatorTest*"` (exit 0);
  - аудит-чеклист legacy-coverage-checklist.md §L: gap #3 → CLOSED.

---

## AC-15. `MultilingualStoryParser` перенесён в `:domain` (gap #1)

- **Given:** в data-слое существует парсер многоязычного story-контента,
  заражённый `android.util.Log` и `java.io.File` (блокирует zero-Android).
- **When:** парсинг-логика переносится в `domain/story/MultilingualStoryParser.kt`
  как pure-Kotlin `object`.
- **Then:**
  1. Разметка `{it}…{/it}`, `{en}`, `{ru}`, `{el}`, `{de}`, `{zh}`, `{pause:N}`
     разворачивается в доменные `Segment.Text` / `Segment.Pause` /
     `Segment.Audio(file: String, languageId)`;
  2. Возвращаются доменные `StoryQuiz`/`StoryQuestion` (из `model/Content.kt`);
  3. `detectLanguage(text, defaultLanguageId)` переносится как pure-функция;
  4. `grep "^import android" domain/src/main/java/com/alexpo/grammermate/domain/story/` → 0;
  5. `grep "java.io.File" domain/src/main/java/com/alexpo/grammermate/domain/story/` → 0 в public API;
  6. `MultilingualStoryParserPauseTest` перенесён (data → domain) и зелёный.
- **derived_from:** UC-4 (444).
- **covers:** FR-12 (462), FR-1 (451), NFR-1 (465).
- **verification:**
  - `./gradlew :domain:test --tests "*MultilingualStoryParser*"` (exit 0);
  - grep-проверки импортов выше.

---

## AC-16. `DailyTask` composers — сборка дневной нормы из блоков (gap #2)

- **Given:** `:domain` содержит sealed `DailyTask` (`TranslateSentence` /
  `VocabFlashcard` / `ConjugateVerb`) с `blockType: DailyBlockType` и
  `DailyCursor` из `ProgressRepository.getDailyCursor`.
- **When:** для конфигурации дня (напр. `{TRANSLATE: 5, VOCAB: 3, VERBS: 2}`) и
  текущего `DailyCursor` вызывается композитор (pure-функция от
  `(cursor, content, settings)`).
- **Then:**
  1. Возвращается упорядоченный `List<DailyTask>` длиной 10 с корректными
     `blockType` у каждой задачи;
  2. Каждый `DailyTask` несёт стабильный `id` (для UI-ключей);
  3. Подтипы переиспользуют существующие доменные модели (`Card`, `VocabWord`,
     `InputMode`, `VocabDrillDirection`) — без дублирования;
  4. Композиторы — pure Kotlin, `grep "^import android" domain/daily` → 0.
- **derived_from:** UC-5 (445).
- **covers:** FR-14 (464), NFR-1 (465).
- **verification:**
  - новая `DailyTaskComposerTest` покрывает сборку из `DailyCursor` + `DailyBlockType` (exit 0);
  - `grep -rn "^import android" domain/src/main/java/com/alexpo/grammermate/domain/daily/` → 0.

---

## AC-17. `YamlToRoomMigrator` помечен `@Deprecated` (решение спонсора Q3)

- **Given:** `YamlToRoomMigrator` существует в
  `app/.../v2/core/data/migration/YamlToRoomMigrator.kt` (data-слой, НЕ переносится
  в `:domain`).
- **When:** разработчик применяет решение SRS-001 FR-13.
- **Then:**
  1. Класс помечен `@Deprecated("Q3 greenfield data — migrator больше не активный путь данных", level = DeprecationLevel.WARNING)`;
  2. Ни один активный путь данных (startup/DI-граф, AppContainer/StoreFactory) не
     вызывает migrator: `grep -rn "YamlToRoomMigrator" app/src/main` возвращает
     только декларацию класса и определение `@Deprecated` (0 активных вызовов);
  3. `SettingsRepository.isMigrationDone("migrate_yaml_to_room_v1")` остаётся в
     контракте порта (флаг персистируется, новый код его не вызывает).
- **derived_from:** UC-9 (449).
- **covers:** FR-13 (463).
- **verification:**
  - `grep -A 2 "class YamlToRoomMigrator" app/src/main/java/com/alexpo/grammermate/v2/core/data/migration/YamlToRoomMigrator.kt` показывает `@Deprecated`;
  - `grep -rn "YamlToRoomMigrator(" app/src/main` → только декларация (0 вызовов `()`);
  - `:app` собирается без активного migrator-пути.

---

## AC-18. GAP C2 — `TrainingStateAccess` successor (cross-cutting порт)

- **Given:** legacy определяет `TrainingStateAccess interface`
  (`feature/daily/DailySessionHelper.kt`) как cross-cutting контракт,
  потребляемый E04/E07/E08/E10/E11/E12; checklist `legacy-coverage-checklist.md`
  §C/GAP C2 помечает его как **HIGH — блокирует Wave 1**.
- **When:** feature-модуль Wave 1–4 (Training E04, Daily E07, BackgroundVocab E08,
  Gamification E10, Boss E11, Pomodoro E12) обращается к training-state.
- **Then:**
  1. Доступ осуществляется через **доменный порт `TrainingStateAccess`**
     (defined in `:domain`, pure Kotlin), НЕ через ad-hoc ViewModel-access или
     прямые ссылки на `TrainingViewModel`;
  2. `grep -rn "interface TrainingStateAccess" domain/src` → 1 совпадение;
  3. В feature-модулях **нет** прямых ссылок на `TrainingViewModel` для чтения
     training-state: `grep -rn "TrainingViewModel" feature/ app/src/main/java/.../feature/`
     → 0 (помимо самой декларации ViewModel);
  4. Контракт порта зафиксирован в SRS-001 §5 / AC до старта Wave 1 (NFR-4);
  5. GAP C2 в `legacy-coverage-checklist.md` §C → CLOSED.
- **derived_from:** UC-7 (447).
- **covers:** FR-6 (456), NFR-4 (468).
- **verification:**
  - `grep -rn "interface TrainingStateAccess" domain/src` → 1;
  - `grep -rn "TrainingViewModel" app/src/main/java/com/alexpo/grammermate/ --include="*.kt" | grep -v "class TrainingViewModel"` → 0 (никаких ссылок на TrainingViewModel из feature-модулей для чтения state);
  - ревью: все 6 downstream-эпиков (E04/E07/E08/E10/E11/E12) потребляют `TrainingStateAccess` через DI, а не через ViewModel.

---

## 3. Traceability-матрица

### 3.1. AC ↔ UC ↔ FR/NFR

| AC | Название | derived_from (UC) | covers (FR/NFR) |
|----|----------|-------------------|------------------|
| AC-1 | Извлечение модуля `:domain` | UC-8 (448) | FR-11 (461), NFR-3 (467) |
| AC-2 | Zero Android-импортов | UC-8 (448) | FR-1 (451), NFR-1 (465) |
| AC-3 | Regression-lock 314 `@Test` | UC-8 (448) | FR-11 (461), NFR-2 (466) |
| AC-4 | Доменные модели 14 доменов (immutable) | UC-8 (448) | FR-2 (452), NFR-7 (471) |
| AC-5 | `SessionEngine` resume сохраняет `currentCardId` ∈ pool | UC-1 (441) | FR-3 (453), NFR-6 (470) |
| AC-6 | `SessionEngine` card_15 явное восстановление | UC-1 (441) | FR-3 (453), NFR-6 (470) |
| AC-7 | `WORD_BANK` не помечает shown | UC-3 (443) | FR-3 (453), FR-5 (455) |
| AC-8 | `VOICE`/`KEYBOARD` помечают shown | UC-3 (443) | FR-5 (455) |
| AC-9 | FSRS-v6 unchanged | UC-2 (442) | FR-4 (454), NFR-5 (469) |
| AC-10 | 9 доменных портов стабильны | UC-7 (447) | FR-6 (456), NFR-4 (468) |
| AC-11 | Аудио-порты без утечки Sherpa | UC-7 (447) | FR-7 (457), NFR-1 (465) |
| AC-12 | Калькуляторы прогресса regression-locked | UC-6 (446) | FR-8 (458), NFR-5 (469) |
| AC-13 | `SubLessonScheduler` детерминирован | UC-6 (446) | FR-9 (459) |
| AC-14 | `LessonCompletionCalculator` (gap #3) | UC-6 (446) | FR-10 (460) |
| AC-15 | `MultilingualStoryParser` в `:domain` (gap #1) | UC-4 (444) | FR-12 (462), FR-1 (451), NFR-1 (465) |
| AC-16 | `DailyTask` composers (gap #2) | UC-5 (445) | FR-14 (464), NFR-1 (465) |
| AC-17 | `YamlToRoomMigrator` `@Deprecated` | UC-9 (449) | FR-13 (463) |
| AC-18 | GAP C2 — `TrainingStateAccess` successor | UC-7 (447) | FR-6 (456), NFR-4 (468) |

### 3.2. Покрытие FR/NFR (каждый FR/NFR имеет ≥1 AC — структурный gate)

| FR/NFR | Код | Покрыто AC |
|--------|-----|------------|
| FR-1 (451) | Pure-Kotlin zero-Android | AC-2, AC-15 |
| FR-2 (452) | Доменные модели 14 доменов | AC-4 |
| FR-3 (453) | SessionEngine card_15 | AC-5, AC-6, AC-7 |
| FR-4 (454) | FSRS-v6 SrsScheduler | AC-9 |
| FR-5 (455) | CardSessionStateMachine | AC-7, AC-8 |
| FR-6 (456) | 9 доменных портов | AC-10, AC-18 |
| FR-7 (457) | Аудио-порты/события | AC-11 |
| FR-8 (458) | Калькуляторы прогресса | AC-12 |
| FR-9 (459) | SubLessonScheduler | AC-13 |
| FR-10 (460) | LessonCompletion (gap #3) | AC-14 |
| FR-11 (461) | Извлечение `:domain` | AC-1, AC-3 |
| FR-12 (462) | MultilingualStoryParser (gap #1) | AC-15 |
| FR-13 (463) | YamlToRoomMigrator `@Deprecated` | AC-17 |
| FR-14 (464) | DailyTask composers (gap #2) | AC-16 |
| NFR-1 (465) | Zero Android | AC-2, AC-11, AC-15, AC-16 |
| NFR-2 (466) | Regression-lock 314 `@Test` | AC-3 |
| NFR-3 (467) | Pure-Kotlin JVM | AC-1 |
| NFR-4 (468) | Стабильность портов | AC-10, AC-18 |
| NFR-5 (469) | Детерминированность (clock) | AC-9, AC-12 |
| NFR-6 (470) | Атомарность resume | AC-5, AC-6 |
| NFR-7 (471) | Immutability моделей | AC-4 |

> **Все 14 FR и 7 NFR имеют ≥1 AC** → `artifact_coverage({epic_id:85, type:'AC',
> link_type:'implements'})` покажет AC-множество полностью покрытым на этапе
> dev-задач (saga-planner).

---

## 4. Сводка

- **18 AC** в форме Given/When/Then, каждый однозначно проверяем (unit-test,
  grep-команда, `./gradlew` target).
- **Покрытие:** 14/14 FR + 7/7 NFR (см. §3.2). 0 orphan-AC (каждый
  `derived_from` UC) и 0 uncovered-FR/NFR.
- **Критичные AC:**
  - AC-5 + AC-6 — инварианты бага `card_15` (resume-by-PK);
  - AC-9 — FSRS-v6 regression-locked (33 теста);
  - AC-10 + AC-18 — стабильность 9 портов + GAP C2 (TrainingStateAccess);
  - AC-15, AC-16, AC-14 — три SRS-объявленных gap'а (#1/#2/#3) закрыты в домене;
  - AC-17 — migrator устранён как активный путь данных (Q3 greenfield).
- **GAP C2 (HIGH, blocking Wave 1)** явно адресован AC-18: доменный порт
  `TrainingStateAccess` определён в `:domain`, feature-модули не ссылаются на
  `TrainingViewModel` напрямую.

---

*AC — мост к dev-задачам saga-planner. Каждый AC.path попадёт в `source_ref`
dev-задачи, а `trace_add({source_id:ac_id, target_type:'task',
target_id:dev_task_id, link_type:'implements'})` зафиксирует реализацию.
`artifact_coverage({epic_id:85, type:'AC', link_type:'implements'})` покажет
прогресс реализации E01.*
